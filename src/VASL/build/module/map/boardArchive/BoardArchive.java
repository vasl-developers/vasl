package VASL.build.module.map.boardArchive;

import java.awt.Color;
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
import org.jdom2.JDOMException;

import static VASSAL.tools.io.IOUtils.closeQuietly;

/**
 * This class is used to read and write files in the board archive
 */
public class BoardArchive {

	private String qualifiedBoardArchive;
    private SharedBoardMetadata sharedBoardMetadata;
    private static final String LOSDataFileName = "LOSData";    // name of the LOS data file in archive
    private static final String boardMetadataFile = "BoardMetadata.xml"; // name of the board metadata file
    private static final String sharedBoardMetadataFile = "SharedBoardMetadata.xml"; // name of the shared board metadata file
    private BufferedImage boardImage;
    private Map map;

    private BoardMetadata metadata;

    /**
     * Defines the interface to a VASL board archive in the VASL boards directory
     *
     * @param archiveName the unqualified VASL board archive name
     * @param boardDirectory the boards directory
     * @exception java.io.IOException if the archive cannot be opened
     */
    public BoardArchive(String archiveName, String boardDirectory, SharedBoardMetadata sharedBoardMetadata) throws IOException {

        // set the archive name, etc.
		qualifiedBoardArchive = boardDirectory +
                System.getProperty("file.separator", "\\") +
                archiveName;
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

                map  = new Map(width, height, terrainTypes);

                // read the terrain and elevations grids
                for(int x = 0; x < gridWidth; x++) {
                    for(int y = 0; y < gridHeight; y++) {
                        map.setGridElevation((int) infile.readByte(), x, y);
                        map.setGridTerrainCode((int) infile.readByte(), x, y);

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

            } catch(Exception e) {
                System.err.println("Could not read the LOS data in board " + qualifiedBoardArchive);
				System.err.println(e.toString());
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
    public final InputStream getInputStreamForArchiveFile(ZipFile archive, String fileName) throws IOException {

        try {

            final Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()){

                final ZipEntry entry = entries.nextElement();

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

        return metadata.getName();
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

        // return LOSColorMapFile.NO_ELEVATION;
        return BoardMetadata.NO_ELEVATION;
    }

    /**
     * @return the code indicating a color has no terrain type
     */
    public static int getNoTerrainColorCode(){

        // return  LOSColorMapFile.NO_TERRAIN;
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
	@SuppressWarnings("unused")
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

    /**
     * @return the board image file name
     */
    public String getBoardImageFileName() {
        return metadata.getBoardImageFileName();
    }

    /**
     * @return int code for a value not read from the archive
     */
    public static int missingValue() {
        return BoardMetadata.MISSING;
    }

    /**
     * @return the set of color SSR rules
     */
    public HashMap<String, ColorSSRule> getColorSSRules() {

        return metadata.getColorSSRules();
    }
}



