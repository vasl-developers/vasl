package VASL.build.module.map.boardArchive;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Map;
import VASL.LOS.Map.Terrain;
import VASL.build.module.ASLMap;
import VASL.build.module.map.boardPicker.BoardException;
import VASL.build.module.map.boardPicker.VASLBoard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

/**
 * This class is used to read and write files in the board archive
 */
public class BoardArchive {

    private static final Logger logger = LoggerFactory.getLogger(BoardArchive.class);

    // constants for standard geomorphic boards, which compensate for VASL "fuzzy" geometry. Hexes are slightly too wide.
    //ToDo Review and determine if all of these should be removed as code handles a range of board size including a/b and deluxe
    public static final double GEO_WIDTH_IN_HEXES = 33;
    public static final double GEO_HEIGHT_IN_HEXES = 10;
    public static final double GEO_IMAGE_WIDTH = 1800;
    public static final double GEO_IMAGE_HEIGHT = 645;
    public static final double GEO_HEX_WIDTH = GEO_IMAGE_WIDTH/(GEO_WIDTH_IN_HEXES - 1.0);
    public static final double GEO_HEX_HEIGHT = GEO_IMAGE_HEIGHT/ GEO_HEIGHT_IN_HEXES;
    public static final Point2D.Double GEO_A1_Center = new Point2D.Double(0, GEO_HEX_HEIGHT/2.0);

    public static final String ALT_HEX_GRID_KEY = "alternate"; // key used in the hex grid for setting alt hexgrid

    private String archiveName;
    private String qualifiedBoardArchive;
    private SharedBoardMetadata sharedBoardMetadata;

    private static final String LOSDataFileName = "LOSData";    // name of the LOS data file in archive
    private static final String boardMetadataFileName = "BoardMetadata.xml"; // name of the board metadata file

    private BufferedImage boardImage;
    private Map map;

    private BoardMetadata metadata;

    // legacy board (i.e. V5) file names
    private static final String dataFileName = "data"; // name of the legacy data file
    private static final String SSRControlsFileName = "SSRControls";

    protected boolean legacyBoard = true;
    // for legacy files null means they do not exist
    private DataFile dataFile;
    private SSRControlsFile SSRControlsFile;

    /**
     * Defines the interface to a VASL board archive in the VASL boards directory
     *
     * @param archiveName the unqualified VASL board archive name
     * @param boardDirectory the boards directory
     * @exception IOException if the archive cannot be opened
     */
    @SuppressWarnings("AssignmentToNull")
    public BoardArchive(String archiveName, String boardDirectory, SharedBoardMetadata sharedBoardMetadata) throws IOException {

        // set the archive name, etc.
        this.archiveName = archiveName;
		qualifiedBoardArchive = boardDirectory +
                System.getProperty("file.separator", "\\") +
                archiveName;
        this.sharedBoardMetadata = sharedBoardMetadata;

        // parse the board metadata
        metadata = new BoardMetadata(sharedBoardMetadata);

        // open the archive
        ZipFile archive = new ZipFile(qualifiedBoardArchive);

        try (archive) {
            // read the board metadata file
            try (InputStream metadataFileStream = getInputStreamForArchiveFile(archive, boardMetadataFileName)) {
                metadata.parseBoardMetadataFile(metadataFileStream);
                legacyBoard = false;
            }
            catch (Exception e) {
                // no metadata file so legacy archive
                logger.info("Unable to read the board metadata in board archive " + archiveName);
                legacyBoard = true;
            }

            // read the legacy data file
            try (InputStream dataFileStream = getInputStreamForArchiveFile(archive, dataFileName)) {
                dataFile = new DataFile(dataFileStream);
            } catch (IOException e)
            {
                // required for legacy boards
                if (!legacyBoard) {
                    dataFile = null;
                }
            }

            // read the SSR controls file
            try (InputStream SSRControlsFileStream = getInputStreamForArchiveFile(archive, SSRControlsFileName)) {
                SSRControlsFile = new SSRControlsFile(SSRControlsFileStream, archiveName);
            } catch (Exception ignore) {
                // bury
            }
        }
    }

    /**
     * @return the list of all board colors
     */
    public LinkedHashMap<String, BoardColor> getBoardColors(){
        // get colors from legacy file if they exist and none in metadata
        if(legacyBoard ||  !metadata.hasBoardSpecificColors()) {
            // board colors replace the shared metadata colors
            LinkedHashMap<String, BoardColor> boardColors = new LinkedHashMap<String, BoardColor>(sharedBoardMetadata.getBoardColors().size());
            // Use the metadata version rather than the sharedBoardMetadata version as the metadata version can have additional entries from the BMD.xml file
            //boardColors.putAll(sharedBoardMetadata.getBoardColors());
            boardColors.putAll(metadata.getBoardColors());
            return boardColors;
        }
        else {
            return metadata.getBoardColors();
        }
    }

    /**
     * @return the set of color SSR rules
     */
    public LinkedHashMap<String, ColorSSRule> getColorSSRules() {
            LinkedHashMap<String, ColorSSRule> colorSSRules = new LinkedHashMap<String, ColorSSRule>();
            // Use the metadata version rather than the sharedBoardMetadata version as the metadata version can have additional entries from the BMD.xml file
            //colorSSRules.putAll(sharedBoardMetadata.getColorSSRules());
            colorSSRules.putAll(metadata.getColorSSRules());
            return colorSSRules;
    }

    /**
     * @return the set of overlay rules
     */
    public LinkedHashMap<String, OverlaySSRule> getOverlaySSRules() {

        if(metadata.hasBoardSpecificOverlayRules()){
            return metadata.getOverlaySSRules();
        }
        return null;
    }

    /**
     * @return the set of underlay rules
     */
    public LinkedHashMap<String, UnderlaySSRule> getUnderlaySSRules() {

       return metadata.getUnderlaySSRules();
    }

    /**
     * Reads the map from disk using terrain types read from the board archive
     * Use only for losgui
     * @return <code>Map</code> object. Null if the LOS data does not exist or an error occurred.
     *//*
    public Map readLOSData(String offset, boolean isCropping){
        //ToDo fix this
        return getLOSData(sharedBoardMetadata.getTerrainTypes(), offset, isCropping, 0);
    }

    *//**
     * Reads the map from disk using the provide terrain types.
     * @return <code>Map</code> object. Null if the LOS data does not exist or an error occurred.
     *//*
    //ToDo delete this method
    // Use only for LOSGUI
    @Deprecated(since="6.7.1", forRemoval=true)
    public Map getLOSData(HashMap<String, Terrain> terrainTypes, String offset, boolean isCropping, double gridadj){

        try (ZipFile archive = new ZipFile(qualifiedBoardArchive)) {  //open the LOSData file of the board
            try (ObjectInputStream infile = new ObjectInputStream(
                    new BufferedInputStream(
                            new GZIPInputStream(
                                    getInputStreamForArchiveFile(archive, LOSDataFileName))))) {

                // read the board-level data
                final int widthInHexes = infile.readInt();
                final int heightInHexes = infile.readInt();
                final int gridWidth = infile.readInt();
                final int gridHeight = infile.readInt();
                if (isGEO()) {
                    map = new Map(widthInHexes, heightInHexes, terrainTypes, "", "", isCropping); // passboardgridconfig, passcropgridconfig, isCropping);
                } else {
                    //map = new Map(getHexWidth(), getHexHeight(), widthInHexes, heightInHexes, getA1CenterX(), getA1CenterY(), gridWidth, gridHeight, terrainTypes, "", "", isCropping); //passboardgridconfig, passcropgridconfig, isCropping);
                }

                // read the terrain and elevations grids
                for (int x = 0; x < gridWidth; x++) {
                    for (int y = 0; y < gridHeight; y++) {
                        map.setGridElevation((int) infile.readByte(), x, y);
                        map.setGridTerrainCode((int) infile.readByte() & (0xff), x, y);
                    }
                }

                // code added to enable rr embankments in RB and Partial Orchards
                // set the rr embankments
                map.setRBrrembankments(metadata.getRBrrembankments());
                map.setPartialOrchards(metadata.getPartialOrchards());

                // read the hex information
                if(map.getMapConfiguration().contains("EqualRowCount") || map.getA1CenterY()==65) {
                    for (int col = 0; col < map.getWidth(); col++) {
                        for (int row = 0; row < map.getHeight(); row++) { // no extra hex for boards where each col has same number of rows (eg RO/DaE)
                            final byte stairway = infile.readByte();
                            if ((int) stairway == 1) {
                                map.getHex(col, row).setStairway(true);
                            } else {
                                map.getHex(col, row).setStairway(false);
                            }
                        }
                    }
                } else {
                    for (int col = 0; col < map.getWidth(); col++) {
                        for (int row = 0; row < map.getHeight() + (col % 2); row++) {
                            final byte stairway = infile.readByte();
                            if ((int) stairway == 1) {
                                map.getHex(col, row).setStairway(true);
                            } else {
                                map.getHex(col, row).setStairway(false);
                            }
                        }
                    }
                }

                // code moved from before stairway loop to enable factory quasi-levels in stairway hexes
                map.resetHexTerrain(gridadj);

                // set the slopes
                map.setSlopes(metadata.getSlopes());
            }
        } catch(Exception e) {
            logger.warn("Could not read the LOS data in board " + qualifiedBoardArchive);
            return null;
        }

        return map;
    }*/
    /*
    * this is the new method of adding los data to a VASL map
    * need to read each element from the LOSData file and discard where not needed; or will assign incorrect values to variables
     */
    //ToDo check that all params are required
    public Map  addLOSDatatoVASLMap (HashMap<String, Terrain> terrainTypes, VASLBoard board, Map VASLMap, String fliphexconfig, ASLMap aslmap, boolean persistevelation) throws BoardException {
        // open LOSData file and read data
        try (ZipFile archive = new ZipFile(qualifiedBoardArchive)) {
            try (ObjectInputStream infile = new ObjectInputStream(
                    new BufferedInputStream(
                            new GZIPInputStream(
                                    getInputStreamForArchiveFile(archive, LOSDataFileName))))) {
                int startcropx = 0; int startcropy = 0; int startcol =0; int startrow =0; int endcol =0; int endrow =0; int cropWidth =0; int cropHeight = 0; int dwadj = 0;
                int boardposx = 0; int boardposy = 0; // position of top left corner of board in multi-board map
                // these values MUST be read from infile before creating terrainGrid and elevationGrid or data will be incorrectly read from infile
                int width = infile.readInt();
                int height = infile.readInt();
                int gridWidth = infile.readInt();
                int gridHeight = infile.readInt();
                cropWidth = gridWidth; // default values when no cropping
                cropHeight = gridHeight;
                boolean isbboard = this.getA1CenterX() == -901 ? true : false;
                boolean isdwboard = this.getA1CenterY() == -612.75 ? true : false;  // "DW" board test
                boardposx = (int) (board.bounds().getX() - board.getMap().getEdgeBuffer().getWidth());
                boardposy = (int) (board.bounds().getY() - board.getMap().getEdgeBuffer().getHeight());
                Hex boardstarthex = VASLMap.gridToHex(boardposx, boardposy);
                int mapcol = boardstarthex.getColumnNumber();
                int maprow = boardstarthex.getRowNumber();

                if (board.isCropped()){
                    // uses the board getters to retrieve data set in ASLMap.buildVASLMap()
                    startcropx = (int) board.getCropBounds().getX();
                    startcropy = (int) board.getCropBounds().getY();
                    startcol = board.getRow1().length() == 2 ? board.getstartcropcol().get(board.getRow1()) +26 : board.getstartcropcol().get(board.getRow1());
                    startrow = board.getstartcroprow().get(board.getCoord1());
                    // handle double height geo boards that have row numbers 11 - 20 mainly the BFPDW a/b boards
                    if (isdwboard){
                        startrow += GEO_HEIGHT_IN_HEXES;
                        dwadj += GEO_HEIGHT_IN_HEXES;
                    }
                    endcol = board.getendcropcol().get(board.getRow2());
                    endrow = board.getendcroprow().get(board.getCoord2());
                    int cropcoladj = (endcol == board.getWidth()) ? 0 : 1;
                    if (board.getCropBounds().getWidth() != -1) {width = endcol - startcol + cropcoladj;}
                    if (board.getCropBounds().getHeight() != -1) {height = endrow - startrow;}  // this formula only works if rows actually cropped
                    cropWidth = (int) board.getCropBounds().getWidth() == -1 ? gridWidth : (int) board.getCropBounds().getWidth();
                    cropHeight = (int) board.getCropBounds().getHeight() == -1 ? gridHeight : (int) board.getCropBounds().getHeight();
                }

                // read the terrain and elevations grids
                // using boardpos x and y to handle multiboard maps
                for (int x = 0; x < (gridWidth); x++) { //startx + gridWidth); x++) {
                    for (int y = 0; y < (gridHeight); y++) { //starty + gridHeight); y++) {
                        if (x >= startcropx && x < (startcropx + cropWidth) && y >= startcropy && y < (startcropy + cropHeight)) {
                            VASLMap.setGridElevation((int) infile.readByte(), x -startcropx + boardposx, y - startcropy + boardposy);
                            int terrainint = infile.readByte() & 255;
                            //ToDo turn the tests below into a HalfHexCheck method
                            // and ensure it works for cropped / flipped boards
                            // it is not fully working - test and fix
                            if (boardposx > 0 && x == 0){  //new board to the right
                                Terrain firsthalfhex = VASLMap.getGridTerrain(boardposx -1 , y - startcropy + boardposy);
                                Terrain secondhalfhex = VASLMap.getTerrain(terrainint);
                                if (!firsthalfhex.isOpen() && secondhalfhex.isOpen()) {
                                    terrainint = VASLMap.getGridTerrainCode(boardposx - 1, y - startcropy + boardposy);
                                }
                            }
                            if (boardposy > 0 && y == 0){ //new board below
                                Terrain firsthalfhex = VASLMap.getGridTerrain(x -startcropx + boardposx, y - startcropy + boardposy - 1);
                                Terrain secondhalfhex = VASLMap.getTerrain(terrainint);
                                if (!firsthalfhex.isOpen() && secondhalfhex.isOpen()) {
                                    terrainint = firsthalfhex.getType();
                                }
                                else if(firsthalfhex.isOpen() && !secondhalfhex.isOpen()){
                                    terrainint = secondhalfhex.getType();
                                }
                            }
                            VASLMap.setGridTerrainCode(terrainint, x - startcropx + boardposx, y - startcropy + boardposy);
                        }
                        else {
                            //need to do this twice to properly step through infile
                            infile.readByte();
                            infile.readByte();
                        }
                    }
                }

                // terrain transformations - need to make all changes to Terrain and Elevation Grids then flip if necessary
                board.applyColorSSRulestoTerrainElevationGrids(VASLMap, sharedBoardMetadata.getLOSSSRules());

                //add overlays to LOS
                VASLMap = aslmap.adjustLOSForOverlays(board, VASLMap, false, persistevelation);

                // AT THIS POINT ALL CHANGES HAVE BEEN MADE TO TERRAIN AND ELEVATION GRIDS
                if (board.isReversed()) {
                    VASLMap.flipTerrainAndElevationGrids(board);
                }

                // now create and populate the hexGrid
                Hex[][] flippedgrid = new Hex[0][];
                if (board.isReversed()) {  // board portion to be flipped is not symmetrical    (width % 2 == 0 && board.isReversed())
                    // the underlying VASLMap.hexgrid will have the correct column heights for the after flipping configuration
                    // can't add the non-symmetrical crop first then flip, need to copy into a new hex grid then flip-copy into VASLMap.hexgrid
                    flippedgrid = new Hex [width][];
                    // read-in the hex information
                    //determine if even/odd col has extra row - depends on crop
                    int heightadj = 0;
                    Point2D.Double flipcenterpoint = new Point2D.Double();
                    boolean isLongerCol = startcol % 2 == 1 ? true : false;
                    //if (startrow != 0) {startrow -= 1;} //adjustment required to align with zero-based row arrays
                    for (int col = 0; col < (startcol + width); col++) {
                        if(col >= startcol){
                            heightadj = isLongerCol ? 1 : 0;
                            isLongerCol = !isLongerCol;
                            flippedgrid[col - startcol] = new Hex[height + heightadj]; // add 1 if odd
                        }
                        for (int row = 0 + dwadj; row < (startrow + height + heightadj); row++) {
                            if (col >= startcol && row >= startrow) {
                                flipcenterpoint.y = heightadj == 1 ? (getHexHeight() * row) : ((getHexHeight() * row) + (getHexHeight()/2)) ;
                                // ToDo need to handle fullhex width crop
                                flipcenterpoint.x = col == 0 ? 0 : (col * getHexWidth() - 1);
                                if (board.nearestFullRow && !fliphexconfig.contains("LeftHalf")) {flipcenterpoint.x += 28.25;}
                                flippedgrid[col - startcol][row - startrow] = new Hex(col - startcol, row - startrow, VASLMap.getGEOHexName(col, row, false, isdwboard),
                                        flipcenterpoint, board.getHexHeight(), board.getHexWidth(), VASLMap, 0, terrainTypes.get(0));
                                final byte stairway = infile.readByte();
                                if ((int) stairway == 1) {
                                    flippedgrid [col - startcol][row - startrow].setStairway(true);
                                }
                                else {
                                    flippedgrid [col - startcol][row - startrow].setStairway(false);
                                }
                                flippedgrid [col - startcol][row - startrow].resetHexAndLocationNames(VASLMap.getGEOHexName(col, row, isbboard, isdwboard));
                            } else {
                                infile.readByte();
                            }
                        }
                    }

                    /*for (int col = 0; col < flippedgrid.length; col++) {
                        for (int row = 0; row < flippedgrid[col].length; row++) {
                            flippedgrid[col][row].resetTerrain(boardposx - startcropx, boardposy - startcropy);
                        }
                    }*/

                    // now copy into hexGrid
                    for (int col = 0; col < width; col++) {
                        for (int row = 0 + dwadj; row < flippedgrid[width - col -1].length; row++) {
                            Hex flippedHex = flippedgrid[width - col - 1][flippedgrid[width - col -1].length - row -1];
                            VASLMap.flipthehex(flippedHex, true, VASLMap.getHexGrid()[mapcol + col][maprow + row]);
                            VASLMap.getHexGrid()[mapcol + col][maprow + row].copy(flippedHex);
                            VASLMap.getHexGrid()[mapcol + col][maprow + row].resetTerrain(); // boardposx - startcropx, boardposy - startcropy);
                        }
                    }
                }
                else {
                    // read-in the hex information
                    //determine if even/odd col has extra row - depends on crop
                    if (startrow != 0) {
                        startrow -= 1;
                    } //adjustment required to align with zero-based row arrays
                    for (int col = 0; col < (startcol + width); col++) {
                        int heightadj = col % 2 == 1 ? 1 : 0;
                        for (int row = 0 + dwadj; row < (startrow + height + heightadj); row++) {
                            if (col >= startcol && row >= startrow) {
                                final byte stairway = infile.readByte();
                                if ((int) stairway == 1) {
                                    VASLMap.getHex(mapcol + col - startcol, maprow + row - startrow).setStairway(true);
                                } else {
                                    VASLMap.getHex(mapcol + col - startcol, maprow + row - startrow).setStairway(false);
                                }
                                VASLMap.getHex(mapcol + col - startcol, maprow + row - startrow).resetHexAndLocationNames(VASLMap.getGEOHexName(col, row, isbboard, isdwboard));

                            } else {
                                infile.readByte();
                            }
                        }
                    }
                }

                // terrain transforms that require hexGrid changes

                board.applyColorSSRulestoHexGrid(VASLMap, sharedBoardMetadata.getLOSSSRules());

                // update hexGrid to include overlays changes
                VASLMap = aslmap.adjustLOSForOverlays(board, VASLMap, true, persistevelation);
                //ToDo check if either of this needs to be done for geo boards
                // code added to enable rr embankments in RB and Partial Orchards
                // set the rr embankments
                // should this be after hexgrid loop?
                VASLMap.setRBrrembankments(metadata.getRBrrembankments());
                VASLMap.setPartialOrchards(metadata.getPartialOrchards());
                // set the slopes
                VASLMap.setSlopes(metadata.getSlopes());

                // reset the hexside locations to map grid
                for (int col = 0; col < VASLMap.getHexGrid().length; col++) {
                    for (int row = 0; row < VASLMap.getHexGrid()[col].length; row++) {
                        VASLMap.getHexGrid()[col][row].resetHexsideLocationNames();
                    }
                }
                // code moved from before stairway loop to enable factory quasi-levels in stairway hexes
                VASLMap.resetHexTerrain(0); //gridadj);
            }
        } catch(Exception e) {
            logger.warn("Could not read the LOS data in board " + qualifiedBoardArchive);
            return null;
        }
        return VASLMap;
    }

    /*
     * this is the new method of adding los data to a HASL VASL map
     * need to read each element from the LOSData file and discard where not needed; or will assign incorrect values to variables
     */
    //ToDo check that all params are required
    public Map  addHASLLOSDatatoVASLMap (HashMap<String, Terrain> terrainTypes, VASLBoard board, Map VASLMap, String fliphexconfig, ASLMap aslmap, boolean persistelevation) throws BoardException {
        // open LOSData file and read data
        try (ZipFile archive = new ZipFile(qualifiedBoardArchive)) {
            try (ObjectInputStream infile = new ObjectInputStream(
                    new BufferedInputStream(
                            new GZIPInputStream(
                                    getInputStreamForArchiveFile(archive, LOSDataFileName))))) {
                int startcropx = 0; int startcropy = 0; int startcol =0; int startrow =0; int endcol =0; int endrow =0; int cropWidth =0; int cropHeight = 0; int dwadj = 0;
                int boardposx = 0; int boardposy = 0; // position of top left corner of board in multi-board map
                // these values MUST be read from infile before creating terrainGrid and elevationGrid or data will be incorrectly read from infile
                int width = infile.readInt();
                int height = infile.readInt();
                int gridWidth = infile.readInt();
                int gridHeight = infile.readInt();
                cropWidth = gridWidth; // default values when no cropping
                cropHeight = gridHeight;
                boardposx = (int) (board.bounds().getX() - board.getMap().getEdgeBuffer().getWidth());
                boardposy = (int) (board.bounds().getY() - board.getMap().getEdgeBuffer().getHeight());
                Hex boardstarthex = VASLMap.gridToHex(boardposx, boardposy);
                int mapcol = boardstarthex.getColumnNumber();
                int maprow = boardstarthex.getRowNumber();
                // add hack to fix RB/RO join
                if (maprow > 0 && board.getName().contains("RO")) { maprow += 1;}
                if (board.isCropped()){
                    // uses the board getters to retrieve data set in ASLMap.buildVASLMap()
                    startcropx = (int) board.getCropBounds().getX();
                    startcropy = (int) board.getCropBounds().getY();
                    startcol = board.getRow1().length() == 2 ? board.getstartcropcol().get(board.getRow1()) +26 : board.getstartcropcol().get(board.getRow1());
                    startrow = board.getstartcroprow().get(board.getCoord1());
                    endcol = board.getendcropcol().get(board.getRow2());
                    endrow = board.getendcroprow().get(board.getCoord2());
                    int cropcoladj = (endcol == board.getWidth()) ? 0 : 1;
                    if (board.getCropBounds().getWidth() != -1) {width = endcol - startcol + cropcoladj;}
                    // handle funky RBv3/RO crop to merge boards
                    if (board.getName().contains("RO") && boardposy > 0) { // adding RO below RBv3
                        height = endrow;
                    } else if (board.getCropBounds().getHeight() != -1) { // this formula only works if rows actually cropped
                        height = endrow - startrow;
                    }
                    cropWidth = (int) board.getCropBounds().getWidth() == -1 ? gridWidth : (int) board.getCropBounds().getWidth();
                    cropHeight = (int) board.getCropBounds().getHeight() == -1 ? gridHeight : (int) board.getCropBounds().getHeight();
                }

                // read the terrain and elevations grids
                // using boardpos x and y to handle multiboard maps
                for (int x = 0; x < (gridWidth); x++) { //startx + gridWidth); x++) {
                    for (int y = 0; y < (gridHeight); y++) { //starty + gridHeight); y++) {
                        if (x >= startcropx && x < (startcropx + cropWidth) && y >= startcropy && y < (startcropy + cropHeight)) {
                            VASLMap.setGridElevation((int) infile.readByte(), x -startcropx + boardposx, y - startcropy + boardposy);
                            int terrainint = infile.readByte() & 255;
                            //ToDo turn the tests below into a HalfHexCheck method
                            // and ensure it works for cropped / flipped boards
                            // it is not fully working - test and fix
                            if (boardposx > 0 && x == 0){  //new board to the right
                                Terrain firsthalfhex = VASLMap.getGridTerrain(boardposx -1 , y - startcropy + boardposy);
                                Terrain secondhalfhex = VASLMap.getTerrain(terrainint);
                                if (!firsthalfhex.isOpen() && secondhalfhex.isOpen()) {
                                    terrainint = VASLMap.getGridTerrainCode(boardposx - 1, y - startcropy + boardposy);
                                }
                            }
                            if (boardposy > 0 && y == 0){ //new board below
                                Terrain firsthalfhex = VASLMap.getGridTerrain(x -startcropx + boardposx, y - startcropy + boardposy - 1);
                                Terrain secondhalfhex = VASLMap.getTerrain(terrainint);
                                if (!firsthalfhex.isOpen() && secondhalfhex.isOpen()) {
                                    terrainint = firsthalfhex.getType();
                                }
                                else if(firsthalfhex.isOpen() && !secondhalfhex.isOpen()){
                                    terrainint = secondhalfhex.getType();
                                }
                            }
                            VASLMap.setGridTerrainCode(terrainint, x - startcropx + boardposx, y - startcropy + boardposy);
                        }
                        else {
                            //need to do this twice to properly step through infile
                            infile.readByte();
                            infile.readByte();
                        }
                    }
                }

                // terrain transformations - need to make all changes to Terrain and Elevation Grids then flip if necessary
                board.applyColorSSRulestoTerrainElevationGrids(VASLMap, sharedBoardMetadata.getLOSSSRules());

                //add overlays to LOS
                VASLMap = aslmap.adjustLOSForOverlays(board, VASLMap, false, persistelevation);

                // AT THIS POINT ALL CHANGES HAVE BEEN MADE TO TERRAIN AND ELEVATION GRIDS
                if (board.isReversed()) {
                    VASLMap.flipTerrainAndElevationGrids(board);
                }

                // now create and populate the hexGrid
                // read-in the hex information
                //determine if even/odd col has extra row - depends on crop
                if (startrow != 0) {startrow -= 1;} //adjustment required to align with zero-based row arrays
                int heightadj = 0;
                for (int col = 0; col < (startcol + width); col++) {
                    if (board.getName().contains("RBv3") || board.getName().contains("SG")) {
                        heightadj = col % 2 == 1 ? 1 : 0;
                    }  // DaE has equal rows so no heightadj required
                    // if adding RO to RBv3 then heightadj required
                    else if (board.getName().contains("RO") && maprow > 0){
                        heightadj = col % 2 == 1 ? 1 : 0;
                    }
                    for (int row = 0 + dwadj; row < (startrow + height + heightadj); row++) {
                        if (col >= startcol && row >= startrow) {
                            final byte stairway = infile.readByte();
                            if ((int) stairway == 1) {
                                VASLMap.getHex(mapcol + col - startcol, maprow + row - startrow).setStairway(true);
                            } else {
                                VASLMap.getHex(mapcol + col - startcol, maprow + row - startrow).setStairway(false);
                            }
                            VASLMap.getHex(mapcol + col - startcol, maprow + row - startrow).resetHexAndLocationNames(VASLMap.getHASLHexName(col, row, board));

                        } else {
                            infile.readByte();
                        }
                    }
                }

                // terrain transforms that require hexGrid changes
                board.applyColorSSRulestoHexGrid(VASLMap, sharedBoardMetadata.getLOSSSRules());

                // update hexGrid to include overlays changes
                VASLMap = aslmap.adjustLOSForOverlays(board, VASLMap, true, persistelevation);
                //ToDo check if either of this needs to be done
                // code added to enable rr embankments in RB and Partial Orchards
                // set the rr embankments
                // should this be after hexgrid loop?
                VASLMap.setRBrrembankments(metadata.getRBrrembankments());
                VASLMap.setPartialOrchards(metadata.getPartialOrchards());
                // set the slopes
                VASLMap.setSlopes(metadata.getSlopes());

                // reset the hexside locations to map grid
                for (int col = 0; col < VASLMap.getHexGrid().length; col++) {
                    for (int row = 0; row < VASLMap.getHexGrid()[col].length; row++) {
                        VASLMap.getHexGrid()[col][row].resetHexsideLocationNames();
                    }
                }
                // code moved from before stairway loop to enable factory quasi-levels in stairway hexes
                VASLMap.resetHexTerrain(0); //gridadj);
            }
        } catch(Exception e) {
            logger.warn("Could not read the LOS data in board " + qualifiedBoardArchive);
            return null;
        }
        return VASLMap;
    }
        /**
     * Write the LOS data to the board archive
     * @param map the LOS data
     */
    @SuppressWarnings("unused")  // want to keep write method with read for simplicity
    public void writeLOSData(Map map){

        // write the LOS data to a local temp file
        final File tempFile = new File("VASL.temp.LOSData");
        try (ObjectOutputStream outfile =
                     new ObjectOutputStream(
                             new BufferedOutputStream(
                                     new GZIPOutputStream(
                                             new FileOutputStream(tempFile))))){

            // write map-level information
            outfile.writeInt(map.getWidth());
            outfile.writeInt(map.getHeight());
            outfile.writeInt(map.getGridWidth());
            outfile.writeInt(map.getGridHeight());

            // write the elevation and terrain grids
            for(int x = 0; x < map.getGridWidth(); x++) {
                for(int y = 0; y < map.getGridHeight(); y++) {
                    outfile.writeByte(map.getGridElevation(x, y));
                    outfile.writeByte(map.getGridTerrainCode(x, y));
                }
            }

            // write the hex information
            if(map.getMapConfiguration().contains("EqualRowCount")){
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight(); row++) { // no extra hex for boards where each col has same number of rows (eg RO/DaE)
                        outfile.writeByte( map.getHex(col, row).hasStairway() ? 1: 0);
                    }
                }
            } else {
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {
                        outfile.writeByte( map.getHex(col, row).hasStairway() ? 1: 0);
                    }
                     }
            }

            outfile.close();

            // add the LOS data to the archive and clean up
            addLOSDataToArchive(tempFile);

            // clean up
            if (!tempFile.delete()) {
				//noinspection ThrowCaughtLocally
				throw new IOException("Cannot delete the temporary file: " + tempFile.getAbsolutePath());
            }

        } catch(Exception e) {

            System.err.println("Cannot save the LOS data to board " + metadata.getName());
            System.err.println("Make sure the archive is not locked by another process/application");
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    /**
     * @return true if the board is a stand GEO board (10 x 33)
     */
    public boolean isGEO(){
        return
                metadata.getHexWidth() == BoardMetadata.MISSING &&
                metadata.getHexHeight() == BoardMetadata.MISSING &&
                metadata.getA1CenterX() == BoardMetadata.MISSING &&
                metadata.getA1CenterY() == BoardMetadata.MISSING;
    }

    /**
     * Get the board image from the archive
     */
    public BufferedImage getBoardImage () {

        // read and cache the board image if necessary
        if (boardImage == null) {

            // String imageFileName = losMetadataFile.getBoardImageFileName();
            final String imageFileName = metadata.getBoardImageFileName();
            // read the image
            // open the archive and get the file entries
            try (ZipFile archive = new ZipFile(qualifiedBoardArchive);
                InputStream file = getInputStreamForArchiveFile(archive, imageFileName)){
                boardImage = ImageIO.read(file);
            }
            catch (IOException e) {

                logger.warn("Could not open the board image: " + imageFileName);
                logger.warn(e.toString());
                return null;
            }
        }
        return boardImage;
    }

    /**
     * Adds the losData file to an existing board archive
     * @param losData the losData to add
     * @throws IOException
     */
    private void addLOSDataToArchive(File losData) throws IOException {

        // get a temp losData and delete as we just want the name
        final File tempFile = File.createTempFile("board" + getBoardName(), null);
        if (!tempFile.delete()) {
            throw new IOException("Cannot delete the temporary file: " + tempFile.getAbsolutePath());
        }

        // rename exist archive to the temp file
        final File boardArchive = new File(qualifiedBoardArchive);
        if (!boardArchive.renameTo(tempFile)) {

            throw new RuntimeException("could not rename the losData "+
                    boardArchive.getAbsolutePath() +
                    " to "+tempFile.getAbsolutePath());
        }

        // copy the archive skipping the losData if it exists
        final byte[] buf = new byte[1024];
        final ZipInputStream currentArchive = new ZipInputStream(new FileInputStream(tempFile));
        final ZipOutputStream newArchive = new ZipOutputStream(new FileOutputStream(boardArchive));
        ZipEntry entry = currentArchive.getNextEntry();
        while (entry != null) {
            final String entryName = entry.getName();
            if (!entryName.equals(LOSDataFileName)) {

                // Add ZIP entry to output stream.
                newArchive.putNextEntry(new ZipEntry(entryName));

                // Transfer bytes from the ZIP losData to the output losData
                int len;
				//noinspection NestedAssignment
				while ((len = currentArchive.read(buf)) > 0) {
                    newArchive.write(buf, 0, len);
                }
            }
            entry = currentArchive.getNextEntry();
        }

        // Close the archive input stream
        currentArchive.close();

        // add the losData
        final InputStream in = new FileInputStream(losData);
        newArchive.putNextEntry(new ZipEntry(LOSDataFileName));
        int len;
		//noinspection NestedAssignment
		while ((len = in.read(buf)) > 0) {
            newArchive.write(buf, 0, len);
        }
        // Complete the entry
        newArchive.closeEntry();
        newArchive.close();
        in.close();

        // Complete the ZIP losData
        if (!tempFile.delete()) {
            throw new IOException("Cannot delete the temporary file: " + tempFile.getAbsolutePath());
        }
    }

    /**
     * Open the archive and gets an <code>InputStream</code> for the given file
     * @param fileName the file to open in the archive
     * @return InputStream to the desired file
     */
    public InputStream getInputStreamForArchiveFile(ZipFile archive, String fileName) throws IOException {

        final Enumeration<? extends ZipEntry> entries = archive.entries();
        while (entries.hasMoreElements()){

            final ZipEntry entry = entries.nextElement();

            // if found return an InputStream
            if(entry.getName().equals(fileName)){

                return archive.getInputStream(entry);

            }
        }

        // file not found
        throw new IOException("Could not open the file '" + fileName + "' in archive " + qualifiedBoardArchive);
    }

    /**
     * @return the name of the board
     */
    public String getBoardName() {

        if(legacyBoard){
            return archiveName.substring(2);
        }
        else {
            return metadata.getName();
        }
    }

    /**
     * Gets the elevation for the given color
     * @param color the board color
     * @return the elevation
     */
    public int getElevationForColor(Color color) {

        if (metadata.getVASLColorName(color) == null) {
            return BoardMetadata.NO_ELEVATION;
        }
        return getElevationForVASLColor(metadata.getVASLColorName(color));
    }
    public String getVASLColorName(Color color){
        if (metadata.getVASLColorName(color) == null) {
            return "";
        }
        return metadata.getVASLColorName(color);
    }
    /**
     * Gets the terrain for the given color
     * @param color the board color
     * @return the terrain code
     */
    public int getTerrainForColor(Color color) {

        if (metadata.getVASLColorName(color) == null) {
            return BoardMetadata.NO_TERRAIN;
        }
        return getTerrainForVASLColor(metadata.getVASLColorName(color));
    }

    /**
     * @param color a color
     * @return  true if the color is a stairway color
     */
    public boolean isStairwayColor(Color color) {

        if(color == null || metadata.getVASLColorName(color) == null) {
            return false;
        }
        final String colorName = metadata.getVASLColorName(color);
        return "WoodStairwell".equals(colorName) || "StoneStairwell".equals(colorName);
    }

    /**
     * Get the elevation for the given VASL color name
     * @param VASLColorName - the color name from the colors file in the board archive
     * @return the elevation
     */
    public int getElevationForVASLColor(String VASLColorName) {

        final String elevation = metadata.getBoardColors().get(VASLColorName).getElevation();
        if(elevation.equals(BoardMetadata.UNKNOWN)) {
            return BoardMetadata.NO_ELEVATION;
        }
        else {
            return Integer.parseInt(metadata.getBoardColors().get(VASLColorName).getElevation());
        }
    }

    /**
     * Get the terrain code for the given VASL color name
     * @param VASLColorName - the color name from the colors file in the board archive
     * @return the terrain code
     */
    public int getTerrainForVASLColor(String VASLColorName) {

        final String terrainName = metadata.getBoardColors().get(VASLColorName).getTerrainName();
        if(terrainName.equals(BoardMetadata.UNKNOWN)) {
            return BoardMetadata.NO_TERRAIN;
        }
        else {
            return sharedBoardMetadata.getTerrainTypes().get(terrainName).getType();
        }
    }

    /**
     * @return the code indicating a color has no elevation
     */
    public static int getNoElevationColorCode(){

        return BoardMetadata.NO_ELEVATION;
    }

    /**
     * @return the code indicating a color has no terrain type
     */
    public static int getNoTerrainColorCode(){

        return BoardMetadata.NO_TERRAIN;
    }

    /**
     * Get the building types
     * @return list of hex names mapped to building types
     */
    public HashMap<String, String> getBuildingTypes(){

        // return losMetadataFile.getBuildingTypes();
        return metadata.getBuildingTypes();
    }

    /**
     * Get board width in hexes
     * @return width in hexes
     */
    public int getBoardWidth() {

        return metadata.getBoardWidth();
    }

    /**
     * Get the board height in hexes
     * @return height in hexes
     */
    public int getBoardHeight() {

        return metadata.getBoardHeight();
    }

    /**
     * Board has hills? Used when creating LOS data from the board image
     * @return true if the board has hills
     */
    public boolean boardHasElevations() {
        return metadata.hasHills();
    }

    /**
     * @return x location of the A1 center hex dot
     */
    public double getA1CenterX() {

        if(legacyBoard){
            return dataFile.getX0() == null ? GEO_A1_Center.x : Double.parseDouble(dataFile.getX0());
        }
        else {
            return metadata.getA1CenterX() == missingValue() ? GEO_A1_Center.x : metadata.getA1CenterX();
        }
    }

    /**
     * @return y location of the A1 center hex dot
     */
    public double getA1CenterY() {

        if(legacyBoard){
            return dataFile.getY0() == null ? GEO_A1_Center.y : Double.parseDouble(dataFile.getY0());
        }
        else {
            return metadata.getA1CenterY() == missingValue() ? GEO_A1_Center.y : metadata.getA1CenterY();
        }
    }

    /**
     * @return hex width in pixels
     */
    public double getHexWidth() {

        if(legacyBoard){
            return dataFile.getDX() == null ? GEO_HEX_WIDTH : Double.parseDouble(dataFile.getDX());
        }
        else {
            return metadata.getHexWidth() == missingValue() ? GEO_HEX_WIDTH : metadata.getHexWidth();
        }
    }

    /**
     * @return hex height in pixels
     */
    public double getHexHeight() {

        if(legacyBoard){
            return dataFile.getDY() == null ? GEO_HEX_HEIGHT: Double.parseDouble(dataFile.getDY());
        }
        else {
            return metadata.getHexHeight() == missingValue() ? GEO_HEX_HEIGHT : metadata.getHexHeight();
        }
    }

    /**
     * @return the hex snap scale
     */
    public int getSnapScale() {

        if(legacyBoard){
            return dataFile.getSnapScale() == null ? 1 : Integer.parseInt(dataFile.getSnapScale());
        }
        else {
            return metadata.getSnapScale() == BoardMetadata.MISSING ? 1 : metadata.getSnapScale();
        }
    }

    /**
     * @return true if upper left hex is A0, B1 is higher, etc.
     */
    public boolean isAltHexGrain() {

        if(isLegacyBoard()){
            return dataFile.getAltHexGrid() != null && Boolean.parseBoolean(dataFile.getAltHexGrid());
        }
        else {
            return metadata.isAltHexGrain();

        }
    }

    /**
     * @return the board version string
     */
    public String getVersion(){

        if(legacyBoard){
            return dataFile.getVersion();
        }
        else {
            return  metadata.getVersion();
        }
    }

    /**
     * @return the board image file name
     */
    public String getBoardImageFileName() {

        if(legacyBoard) {
            if (dataFile.getBoardImageFileName() != null) {
                return dataFile.getBoardImageFileName();
            }
            else {
                // default image naming for legacy boards
                return "bd" + getBoardName() + ".gif";
            }
        }
        else {
            return metadata.getBoardImageFileName();
        }
    }

    /**
     * @return int code for a value not read from the archive
     */
    public static int missingValue() {
        return BoardMetadata.MISSING;
    }

    /**
     * @return the set of hexes with slopes
     */
    public Slopes getSlopes() {
        return metadata.getSlopes();
    }

    /**
     * @return true if board archive is legacy (i.e. V5) format
     */
    public boolean isLegacyBoard() {
        return legacyBoard;
    }

    /**
     * @return  a list of the "basic" nodes in the SSR control file; null if file does not exist
     */
    public NodeList getBasicNodes() {

        if(SSRControlsFile == null) {
            return null;
        }
        else {
            return SSRControlsFile.getBasicNodes();
        }
    }

    /**
     * @return  a list of the "option" nodes in the SSR control file; null if file does not exist
     */
    public NodeList getOptionNodes() {

        if(SSRControlsFile  == null) {
            return null;
        }
        else {
            return SSRControlsFile.getOptionNodes();
        }
    }

    public static String getSharedBoardMetadataFileName() {
        return "SharedBoardMetadata.xml";
    }

    // added to handle boards with original configuration where top left hex is half height and half width (ie BRT)
    public String getHexGridConfig(){
        return metadata.getHexGridConfig();
    }

    public String getQualifiedBoardArchive(){
        return qualifiedBoardArchive;
    }
    public BoardMetadata getMetadata(){
        return metadata;
    }
}


