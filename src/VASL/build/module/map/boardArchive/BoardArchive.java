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

import VASL.LOS.Map.Map;
import VASL.LOS.Map.Terrain;
import VASSAL.tools.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static VASSAL.tools.io.IOUtils.closeQuietly;

/**
 * This class is used to read and write files in the board archive
 */
public class BoardArchive {

    private static final Logger logger = LoggerFactory.getLogger(BoardArchive.class);

    // constants for standard geomorphic boards, which compensate for VASL "fuzzy" geometry. Hexes are slightly too wide.
    public static final double GEO_WIDTH = 33;
    public static final double GEO_HEIGHT = 10;
    public static final double GEO_IMAGE_WIDTH = 1800;
    public static final double GEO_IMAGE_HEIGHT = 645;
    public static final double GEO_HEX_WIDTH = GEO_IMAGE_WIDTH/(GEO_WIDTH - 1.0);
    public static final double GEO_HEX_HEIGHT = GEO_IMAGE_HEIGHT/GEO_HEIGHT;
    public static final Point2D.Double GEO_A1_Center = new Point2D.Double(0, GEO_HEX_HEIGHT/2.0);

    private String archiveName;
    private String qualifiedBoardArchive;
    private SharedBoardMetadata sharedBoardMetadata;

    private static final String LOSDataFileName = "LOSData";    // name of the LOS data file in archive
    private static final String boardMetadataFileName = "BoardMetadata.xml"; // name of the board metadata file

    private BufferedImage boardImage;
    private Map map;

    private BoardMetadata metadata;

    // legacy board (i.e. V5) stuff
    private static final String dataFileName = "data"; // name of the legacy data file
    private static final String overlaySSRFileName = "overlaySSR"; // name of the legacy overlay SSR file
    private static final String colorsFileName = "colors"; // name of the legacy colors file
    private static final String colorSSRFileName = "colorSSR"; // name of the legacy colorSSR file

    protected boolean legacyBoard = true;
    private DataFile dataFile;
    private OverlaySSRFile overlaySSRFile;
    private ColorsFile colorsFile;
    private ColorSSRFile colorSSRFile;

    /**
     * Defines the interface to a VASL board archive in the VASL boards directory
     *
     * @param archiveName the unqualified VASL board archive name
     * @param boardDirectory the boards directory
     * @exception java.io.IOException if the archive cannot be opened
     */
    public BoardArchive(String archiveName, String boardDirectory, SharedBoardMetadata sharedBoardMetadata) throws IOException {

        // set the archive name, etc.
        this.archiveName = archiveName;
		qualifiedBoardArchive = boardDirectory +
                System.getProperty("file.separator", "\\") +
                archiveName;
        this.sharedBoardMetadata = sharedBoardMetadata;

        metadata = new BoardMetadata(sharedBoardMetadata);
        InputStream file = null;
        InputStream dataFileStream = null;
        InputStream overlaySSRFileStream = null;
        InputStream colorsFileStream = null;
        InputStream colorSSRFileStream = null;

        // open the archive
        ZipFile archive = new ZipFile(qualifiedBoardArchive);

        // read the board metadata file
        try {
            file = getInputStreamForArchiveFile(archive, boardMetadataFileName);
            metadata.parseBoardMetadataFile(file);
            legacyBoard = false;


        } catch (Exception e) {

            // no metadata file so legacy archive
            logger.info("Unable to read the board metadata in board archive " + archiveName);
            legacyBoard = true;

        }
        finally {
            closeQuietly(file);
        }

        // read the legacy data file
        try {
            dataFileStream = getInputStreamForArchiveFile(archive, dataFileName);
            dataFile = new DataFile(dataFileStream);
        }
        catch (IOException e) {

            // required for legacy boards
            if(!legacyBoard) {
                throw new IOException("Cannot read the data file in board " + archiveName, e);
            }
        }
        finally {
            closeQuietly(dataFileStream);
        }

        // read the legacy overlay SSR file
        try {
            overlaySSRFileStream = getInputStreamForArchiveFile(archive, overlaySSRFileName);
            overlaySSRFile = new OverlaySSRFile(overlaySSRFileStream, archiveName);
        } catch (Exception ignore) {
            // bury
        }
        finally {
            closeQuietly(overlaySSRFileStream);
        }

        // read the legacy colors file
        try {
            colorsFileStream = getInputStreamForArchiveFile(archive, colorsFileName);
            colorsFile = new ColorsFile(colorsFileStream, archiveName);
        }
        catch (Exception ignore) {
            // bury
        }
        finally {
            closeQuietly(colorsFileStream);
        }

        // read the legacy color SSR file
        try {
            colorSSRFileStream = getInputStreamForArchiveFile(archive, colorSSRFileName);
            colorSSRFile = new ColorSSRFile(colorSSRFileStream, archiveName);
        }
        catch (Exception ignore) {
            // bury
        }
        finally {
            closeQuietly(colorSSRFileStream);
        }

        closeQuietly(archive);
    }

    /**
     * @return the list of all board colors
     */
    public LinkedHashMap<String, BoardColor> getBoardColors(){

        if(legacyBoard) {

            // board colors replace the shared metadata colors
            LinkedHashMap<String, BoardColor> boardColors = new LinkedHashMap<String, BoardColor>(sharedBoardMetadata.getBoardColors().size());
            boardColors.putAll(sharedBoardMetadata.getBoardColors());
            if(colorsFile != null) {
                boardColors.putAll(colorsFile.getColorRules());
            }
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

        if(legacyBoard){

            // board color SSR replace the shared metadata color SSR
            LinkedHashMap<String, ColorSSRule> colorSSRules = new LinkedHashMap<String, ColorSSRule>();
            colorSSRules.putAll(sharedBoardMetadata.getColorSSRules());
            if(colorSSRFile != null) {
                colorSSRules.putAll(colorSSRFile.getColorSSRules());
            }
            return colorSSRules;
        }
        else {
            return metadata.getColorSSRules();
        }
    }

    /**
     * @return the set of overlay rules
     */
    public LinkedHashMap<String, OverlaySSRule> getOverlaySSRules() {

        if(legacyBoard){

            // board overlay rules replace the shared metadata overlay rules
            LinkedHashMap<String, OverlaySSRule> overlaySSRules = new LinkedHashMap<String, OverlaySSRule>();
            overlaySSRules.putAll(sharedBoardMetadata.getOverlaySSRules());
            if(overlaySSRFile != null) {
                overlaySSRules.putAll(overlaySSRFile.getOverlaySSRules());
            }
            return overlaySSRules;
        }
        else {
            return metadata.getOverlaySSRules();
        }

    }

    /**
     * @return the set of underlay rules
     */
    public LinkedHashMap<String, UnderlaySSRule> getUnderlaySSRules() {

        if(legacyBoard){

            // board underlay rules replace the shared metadata underlay rules
            LinkedHashMap<String, UnderlaySSRule> underlaySSRules = new LinkedHashMap<String, UnderlaySSRule>();
            underlaySSRules.putAll(sharedBoardMetadata.getUnderlaySSRules());
            if(overlaySSRFile != null) {
                underlaySSRules.putAll(overlaySSRFile.getUnderlaySSRules());
            }
            return underlaySSRules;
        } else {
            return metadata.getUnderlaySSRules();
        }
    }

    /**
     * Reads the map from disk using terrain types read from the board archive
     * @return <code>Map</code> object. Null if the LOS data does not exist or an error occurred.
     */
    public Map getLOSData(){

        return getLOSData(sharedBoardMetadata.getTerrainTypes());
    }

    /**
     * Reads the map from disk using the provide terrain types.
     * @return <code>Map</code> object. Null if the LOS data does not exist or an error occurred.
     */
    public Map getLOSData(HashMap<String, Terrain> terrainTypes){

        // read the map if necessary
        if (map == null){

            ObjectInputStream infile = null;
            ZipFile archive = null;
            try {
                archive = new ZipFile(qualifiedBoardArchive);
                infile =
                        new ObjectInputStream(
                                new BufferedInputStream(
                                        new GZIPInputStream(
                                                getInputStreamForArchiveFile(archive, LOSDataFileName))));
                // read the map-level data
                final int width = infile.readInt();
				final int height = infile.readInt();
				final int gridWidth = infile.readInt();
				final int gridHeight = infile.readInt();

                if(isGEO()) {

                    map  = new Map(width, height, terrainTypes);
                }
                else {
                    map = new Map(width, height, getA1CenterX(), getA1CenterY(), gridWidth, gridHeight, terrainTypes);

                }

                // read the terrain and elevations grids
                for(int x = 0; x < gridWidth; x++) {
                    for(int y = 0; y < gridHeight; y++) {
                        map.setGridElevation((int) infile.readByte(), x, y);
                        map.setGridTerrainCode((int) infile.readByte() & (0xff), x, y);

                    }
                }

                map.resetHexTerrain();

                // read the hex information
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {
                        final byte stairway = infile.readByte();
                        if((int) stairway == 1) {
                            map.getHex(col, row).setStairway(true);
                        }
                        else {
                            map.getHex(col,row).setStairway(false);
                        }
                    }
                }

                // set the slopes
                map.setSlopes(metadata.getSlopes());

            } catch(Exception e) {
                System.err.println("Could not read the LOS data in board " + qualifiedBoardArchive);
                e.printStackTrace(System.err);
                return null;
            }
            finally {

				org.apache.commons.io.IOUtils.closeQuietly(infile);
                IOUtils.closeQuietly(archive);
            }
        }
        return map;
    }

    /**
     * Write the LOS data to the board archive
     * @param map the LOS data
     */
    public void writeLOSData(Map map){

        ObjectOutputStream outfile = null;
        try {

            // write the LOS data to a local temp file
            final File tempFile = new File("VASL.temp.LOSData");
            outfile =
                    new ObjectOutputStream(
                            new BufferedOutputStream(
                                      new GZIPOutputStream(
                                            new FileOutputStream(tempFile))));

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
            for (int col = 0; col < map.getWidth(); col++) {
                for (int row = 0; row < map.getHeight() + (col % 2); row++) {
                    outfile.writeByte( map.getHex(col, row).hasStairway() ? 1: 0);
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
        finally {
			org.apache.commons.io.IOUtils.closeQuietly(outfile);
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
            InputStream file = null;
            ZipFile archive = null;
            try {

                // open the archive and get the file entries
                archive = new ZipFile(qualifiedBoardArchive);
                file = getInputStreamForArchiveFile(archive, imageFileName);
                boardImage = ImageIO.read(file);
            }
            catch (IOException e) {

                System.err.println("Could not open the board image: " + imageFileName);
				System.err.println(e.toString());
                return null;
            }
            finally {
                closeQuietly(file);
                closeQuietly(archive);
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
    private InputStream getInputStreamForArchiveFile(ZipFile archive, String fileName) throws IOException {

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
     * @return true if upper left hex is A0, B1 is higher, etc.
     */
	@SuppressWarnings("unused")
    public boolean isAltHexGrain() {
        return metadata.isAltHexGrain();
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

            // default image naming for legacy boards
            return "bd" + getBoardName() + ".gif";
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
}


