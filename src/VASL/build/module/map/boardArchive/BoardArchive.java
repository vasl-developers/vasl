package VASL.build.module.map.boardArchive;

import VASL.LOS.Map.Map;
import VASL.LOS.Map.Terrain;
import org.jdom2.JDOMException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.*;

import static VASSAL.tools.io.IOUtils.closeQuietly;

/**
 * This class is used to read and write files in the board archive
 */
public class BoardArchive {

    // the board archive details
    private String boardName;
    private String boardDirectory;
    private String qualifiedBoardArchive;
    SharedBoardMetadata sharedBoardMetadata;
    private final static String LOSDataFileName = "LOSData";    // name of the LOS data file in archive
    private final static String boardMetadataFile = "BoardMetadata.xml"; // name of the board metadata file

    private final static String sharedBoardMetadataFile = "SharedBoardMetadata.xml"; // name of the shared board metadata file
    private BufferedImage boardImage;
    private Map map;

    BoardMetadata metadata;

    /**
     * Defines the interface to a VASL board archive in the VASL boards directory
     *
     * @param boardName the unqualified VASL board archive name
     * @param boardDirectory the boards directory
     * @exception java.io.IOException if the archive cannot be opened
     */
    public BoardArchive(String boardName, String boardDirectory, SharedBoardMetadata sharedBoardMetadata) throws IOException {

        // set the archive name, etc.
        this.boardName = boardName;
        this.boardDirectory = boardDirectory;
        qualifiedBoardArchive = this.boardDirectory +
                System.getProperty("file.separator", "\\") +
                boardName;
        this.sharedBoardMetadata = sharedBoardMetadata;

        // parse the board metadata
        metadata = new BoardMetadata(sharedBoardMetadata);
        InputStream file = null;
        ZipFile archive = null;
        try {
            archive = new ZipFile(qualifiedBoardArchive);
            file = getInputStreamForArchiveFile(archive, boardMetadataFile);
            metadata.parseBoardMetadataFile(file);

        } catch (JDOMException e) {

            throw new IOException("Unable to read the board metadata", e);
        }
        finally {
            closeQuietly(file);
            closeQuietly(archive);
        }
    }

    /**
     * Reads the map from disk using terrain types read from the board archive
     * @return <code>Map</code> object. Null if the LOS data does not exist or an error occurred.
     */
    public VASL.LOS.Map.Map getLOSData(){

        return this.getLOSData(sharedBoardMetadata.getTerrainTypes());
    }

    /**
     * Reads the map from disk using the provide terrain types.
     * @return <code>Map</code> object. Null if the LOS data does not exist or an error occurred.
     */
    public VASL.LOS.Map.Map getLOSData(HashMap<String, Terrain> terrainTypes){

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
                int width = infile.readInt();
                int height = infile.readInt();
                int gridWidth = infile.readInt();
                int gridHeight = infile.readInt();

                map  = new Map(width, height, terrainTypes);

                // read the terrain and elevations grids
                for(int x = 0; x < gridWidth; x++) {
                    for(int y = 0; y < gridHeight; y++) {
                        map.setGridElevation(infile.readByte(), x, y);
                        map.setGridTerrainCode(infile.readByte(), x, y);

                    }
                }

                map.resetHexTerrain();

                // read the hex information
                for (int col = 0; col < map.getWidth(); col++) {
                    for (int row = 0; row < map.getHeight() + (col % 2); row++) {
                        byte stairway = infile.readByte();
                        if(stairway == 1) {
                            map.getHex(col, row).setStairway(true);
                        }
                        else {
                            map.getHex(col,row).setStairway(false);
                        }
                    }
                }

            } catch(Exception e) {
                System.err.println("Could not read the LOS data in board " + qualifiedBoardArchive);
                return null;
            }
            finally {

                VASSAL.tools.io.IOUtils.closeQuietly((InputStream) infile);
                VASSAL.tools.io.IOUtils.closeQuietly(archive);
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
            File tempFile = new File("VASL.temp.LOSData");
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
                    outfile.writeByte((byte) map.getGridElevation(x, y));
                    outfile.writeByte((byte) map.getGridTerrainCode(x, y));
                }
            }

            // write the hex information
            for (int col = 0; col < map.getWidth(); col++) {
                for (int row = 0; row < map.getHeight() + (col % 2); row++) {
                    outfile.writeByte( map.getHex(col, row).hasStairway() ? (byte) 1: (byte) 0);
                }
            }

            outfile.close();

            // add the LOS data to the archive and clean up
            addLOSDataToArchive(tempFile);

            // clean up
            if (!tempFile.delete()) {
                throw new IOException("Cannot delete the temporary file: " + tempFile.getAbsolutePath());
            }

        } catch(Exception e) {

            System.err.println("Cannot save the LOS data to board " + boardName);
            System.err.println("Make sure the archive is not locked by another process/application");
            e.printStackTrace(System.err);
            System.exit(-1);
        }
        finally {
            VASSAL.tools.io.IOUtils.closeQuietly((OutputStream) outfile);
        }

    }

    /**
     * Get the board image from the archive
     */
    public BufferedImage getBoardImage () {

        // read and cache the board image if necessary
        if (boardImage == null) {

            // String imageFileName = losMetadataFile.getBoardImageFileName();
            String imageFileName = metadata.getBoardImageFileName();

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
        File tempFile = File.createTempFile(this.getBoardName(), null);
        if (!tempFile.delete()) {
            throw new IOException("Cannot delete the temporary file: " + tempFile.getAbsolutePath());
        }

        // rename exist archive to the temp file
        File boardArchive = new File(qualifiedBoardArchive);
        if (!boardArchive.renameTo(tempFile)) {

            throw new RuntimeException("could not rename the losData "+
                    boardArchive.getAbsolutePath() +
                    " to "+tempFile.getAbsolutePath());
        }

        // copy the archive skipping the losData if it exists
        byte[] buf = new byte[1024];
        ZipInputStream currentArchive = new ZipInputStream(new FileInputStream(tempFile));
        ZipOutputStream newArchive = new ZipOutputStream(new FileOutputStream(boardArchive));
        ZipEntry entry = currentArchive.getNextEntry();
        while (entry != null) {
            String entryName = entry.getName();
            if (!entryName.equals(LOSDataFileName)) {

                // Add ZIP entry to output stream.
                newArchive.putNextEntry(new ZipEntry(entryName));

                // Transfer bytes from the ZIP losData to the output losData
                int len;
                while ((len = currentArchive.read(buf)) > 0) {
                    newArchive.write(buf, 0, len);
                }
            }
            entry = currentArchive.getNextEntry();
        }

        // Close the archive input stream
        currentArchive.close();

        // add the losData
        InputStream in = new FileInputStream(losData);
        newArchive.putNextEntry(new ZipEntry(LOSDataFileName));
        int len;
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

        try {

            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()){

                ZipEntry entry = entries.nextElement();

                // if found return an InputStream
                if(entry.getName().equals(fileName)){

                    return archive.getInputStream(entry);

                }
            }
        }
        catch (IOException e){

            System.err.println("Could not read the file '" + fileName + "' in archive " + qualifiedBoardArchive);
            throw e;
        }

        // file not found
        throw new IOException("Could not open the file '" + fileName + "' in archive " + qualifiedBoardArchive);
    }

    /**
     * @return the name of the board
     */
    public String getBoardName() {

        //TODO: this should be read from the metadata at some point
        return boardName;
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
     * Get the elevation for the given VASL color name
     * @param VASLColorName - the color name from the colors file in the board archive
     * @return the elevation
     */
    public int getElevationForVASLColor(String VASLColorName) {

        String elevation = metadata.getBoardColors().get(VASLColorName).getElevation();
        if(elevation.equals(metadata.UNKNOWN)) {
            return metadata.NO_ELEVATION;
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

        String terrainName = metadata.getBoardColors().get(VASLColorName).getTerrainName();
        if(terrainName.equals(metadata.UNKNOWN)) {
            return metadata.NO_TERRAIN;
        }
        else {
            return sharedBoardMetadata.getTerrainTypes().get(terrainName).getType();
        }
    }

    /**
     * @return the code indicating a color has no elevation
     */
    public int getNoElevationColorCode(){

        // return LOSColorMapFile.NO_ELEVATION;
        return metadata.NO_ELEVATION;
    }

    /**
     * @return the code indicating a color has no terrain type
     */
    public int getNoTerrainColorCode(){

        // return  LOSColorMapFile.NO_TERRAIN;
        return metadata.NO_TERRAIN;
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
     * Get the terrain types
     * @return a list of terrain names mapped to terrain objects
     */
    public HashMap<String, Terrain> getTerrainTypes(){

        // return losMetadataFile.getTerrainTypes();
        return sharedBoardMetadata.getTerrainTypes();
    }

    /**
     * Get board width in hexes
     * @return width in hexes
     */
    public int getBoardWidth() {
        // return losMetadataFile.getBoardWidth();
        return metadata.getBoardWidth();
    }

    /**
     * Get the board height in hexes
     * @return height in hexes
     */
    public int getBoardHeight() {
        // return losMetadataFile.getBoardHeight();
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
    public int getA1CenterX() {
        return metadata.getA1CenterX();
    }

    /**
     * @return y location of the A1 center hex dot
     */
    public int getA1CenterY() {
        return metadata.getA1CenterY();
    }

    /**
     * @return hex width in pixels
     */
    public float getHexWidth() {
        return metadata.getHexWidth();
    }

    /**
     * @return hex height in pixels
     */
    public float getHexHeight() {
        return metadata.getHexHeight();
    }

    /**
     * @return true if upper left hex is A0, B1 is higher, etc.
     */
    public boolean isAltHexGrain() {
        return metadata.isAltHexGrain();
    }

    public String getVersion(){
        return  metadata.getVersion();
    }

    /**
     * @return the name of the shared metadata file
     */
    public static String getSharedBoardMetadataFileName() {
        return sharedBoardMetadataFile;
    }

    public String getBoardImageFileName() {
        return metadata.getBoardImageFileName();
    }

    /**
     * @return int code for a value not read from the archive
     */
    public int missingValue() {
        return metadata.MISSING;
    }
}



