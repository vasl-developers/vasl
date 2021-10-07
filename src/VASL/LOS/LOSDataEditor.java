package VASL.LOS;

import VASL.LOS.Map.*;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;


/**
 * LOSDataEditor is used to edit the LOS data.
 * It's used by the LOS GUI and dynamically by VASL to perform SSR terrain changes
 */
public class LOSDataEditor {

    // the LOS data
    protected Map map;

    // the board archive and shared board metadata
    protected BoardArchive boardArchive;
    protected SharedBoardMetadata sharedBoardMetadata;

    // for terrain and elevation encoding while creating LOS data
    private static final int UNKNOWN_TERRAIN = 255;
    private static final int TERRAIN_OFFSET = 500;  //changed by DR to permit terrain types with types values > 128
    private static final int UNKNOWN_ELEVATION = -10;
    private static final int ELEVATION_OFFSET = 50;

    /**
     * Creates an LOS data editor for a VASL archive
     *
     * @param boardName      the name of the VASL board archive file
     * @param boardDirectory the VASL board archive directory
     * @throws java.io.IOException if the archive cannot be opened
     */
    public LOSDataEditor(String boardName, String boardDirectory, SharedBoardMetadata sharedBoardMetadata) throws IOException {

        boardArchive = new BoardArchive(boardName, boardDirectory, sharedBoardMetadata);
        this.sharedBoardMetadata = sharedBoardMetadata;

        // create an empty map
        map = createNewLOSData();
    }

    /**
     * Creates an LOS data editor for an existing map
     * IMPORTANT use only for changing the terrain on an existing map
     *
     * @param map the map
     */
    public LOSDataEditor(Map map) {
        this.map = map;
    }

    public Map createNewLOSData() {

        Map m;
        String passgridconfig= boardArchive.getHexGridConfig();
        if(passgridconfig==null){passgridconfig="Normal";}
        boolean isCropping=false;
        if (boardArchive.isGEO()) {

            m = new Map(boardArchive.getHexWidth(),
                    boardArchive.getHexHeight(),
                    boardArchive.getBoardWidth(),
                    boardArchive.getBoardHeight(),
                    boardArchive.getA1CenterX(),
                    boardArchive.getA1CenterY(),
                    boardArchive.getBoardImage().getWidth(),
                    boardArchive.getBoardImage().getHeight(),
                    sharedBoardMetadata.getTerrainTypes(), passgridconfig, isCropping);
            m.setSlopes(boardArchive.getSlopes());
        } else {
            m = new Map(
                    // DR added two new parameters
                    boardArchive.getHexWidth(),
                    boardArchive.getHexHeight(),
                    boardArchive.getBoardWidth(),
                    boardArchive.getBoardHeight(),
                    boardArchive.getA1CenterX(),
                    boardArchive.getA1CenterY(),
                    boardArchive.getBoardImage().getWidth(),
                    boardArchive.getBoardImage().getHeight(),
                    sharedBoardMetadata.getTerrainTypes(), passgridconfig, isCropping);
            m.setSlopes(boardArchive.getSlopes());
        }
        return m;
    }

    //TODO: removed redundancy in these private methods

    /**
     * Find the nearest terrain to a pixel of unknown terrain. E.g. the hex center dot will have unknown terrain
     *
     * @param x x value of pixel
     * @param y y value of pixel
     * @return
     */
    private Terrain getNearestTerrain(int x, int y) {

        int min = 0;
        int max = 1;
        Terrain terrainType;

        // search at most 5 pixels out
        while (max <= 5) {
            for (int i = x - max; i <= x + max; i++) {
                for (int j = y - max; j <= y + max; j++) {

                    // this logic make the search radius circular
                    if (map.onMap(i, j) && Point.distance((double) x, (double) y, (double) i, (double) j) > min &&
                            Point.distance((double) x, (double) y, (double) i, (double) j) <= max &&
                            map.getGridTerrainCode(i, j) != UNKNOWN_TERRAIN &&
                            map.getGridTerrainCode(i, j) < TERRAIN_OFFSET) {

                        terrainType = map.getGridTerrain(i, j);

                        // ignore inherent terrain
                        if (!terrainType.isInherentTerrain()) {
                            return terrainType;
                        }
                    }
                }
            }

            min++;
            max++;
        }

        // if no terrain within 5 pixels then punt and use open ground
        return map.getTerrain("Open Ground");
    }

    /**
     * Find the nearest elevation to a pixel of unknown elevation. E.g. the hex center dot will have unknown elevation
     *
     * @param x x value of pixel
     * @param y y value of pixel
     * @return
     */
    private int getNearestElevation(int x, int y) {

        int min = 0;
        int max = 1;

        // search at most 50 pixels out
        while (max <= 50) {
            for (int i = x - max; i <= x + max; i++) {
                for (int j = y - max; j <= y + max; j++) {

                    // this logic makes the search radius circular
                    if (map.onMap(i, j) && Point.distance((double) x, (double) y, (double) i, (double) j) > min &&
                            Point.distance((double) x, (double) y, (double) i, (double) j) <= max &&
                            map.getGridElevation(i, j) != UNKNOWN_ELEVATION &&
                            map.getGridElevation(i, j) < ELEVATION_OFFSET - 5) { // need a buffer for negative elevations

                        return map.getGridElevation(i, j);
                    }
                }
            }

            min++;
            max++;
        }

        // if no elevation within 5 pixels then punt and use level 0
        return 0;
    }

    /**
     * Add the elevation and terrain code for each pixel in the map.
     * If the terrain/elevation is unknown mark it as such.
     */
    private void setAllTerrain() {

        // read the board image
        BufferedImage boardImage = boardArchive.getBoardImage();

        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {

                Color color = getRGBColor(boardImage, x, y);

                int elevation = boardArchive.getElevationForColor(color);
                int terrain = boardArchive.getTerrainForColor(color);

                // set the elevation in the map grid
                if (boardArchive.boardHasElevations()) { // ignore boards without elevation

                    if (elevation == BoardArchive.getNoElevationColorCode()) {
                        map.setGridElevation(UNKNOWN_ELEVATION, x, y);
                    } else {
                        map.setGridElevation(elevation, x, y);
                    }
                }

                // set the terrain type in the map grid
                if (terrain == BoardArchive.getNoTerrainColorCode()) {
                    map.setGridTerrainCode(UNKNOWN_TERRAIN, x, y);
                } else {
                    map.setGridTerrainCode(terrain, x, y);
                }
            }
        }
    }

    /**
     * find nearest terrains for unknown terrain or elevation
     * The "offset" is a temporary encoding that records the correct terrain/elevation
     * but allows us to ignore the pixel when finding the nearest terrain/elevation of its neighbors.
     * Otherwise the "nearest terrain" will bleed to pixels it shouldn't
     */
    private void setAllUnknownTerrain() {

        // find the nearest terrain encoding as we go
        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrainCode(x, y) == UNKNOWN_TERRAIN) {
                    map.setGridTerrainCode(getNearestTerrain(x, y).getType() + TERRAIN_OFFSET, x, y);
                }

                if (map.getGridElevation(x, y) == UNKNOWN_ELEVATION) {
                    map.setGridElevation(getNearestElevation(x, y) + ELEVATION_OFFSET, x, y);
                }
            }
        }

        // remove the terrain encoding
        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {
                if (map.getGridTerrainCode(x, y) >= TERRAIN_OFFSET) {
                    map.setGridTerrainCode(map.getGridTerrainCode(x, y) - TERRAIN_OFFSET, x, y);
                }
                if (map.getGridElevation(x, y) >= ELEVATION_OFFSET / 2) {
                    map.setGridElevation(map.getGridElevation(x, y) - ELEVATION_OFFSET, x, y);
                }
            }
        }
    }

    /**
     * Create the LOS data from the board image in the VASL archive
     * Assumes the file helpers have been set
     */
    public void createLOSData() {

        // create an empty map
        map = createNewLOSData();

        // spin through the terrain and elevation grids and add the terrain codes
        setAllTerrain();

        // fix pixels that have no terrain or elevation code (E.g. the hex center dot)
        setAllUnknownTerrain();

        // we need to occasionally update the hex grid as the following processes need updated hex information
        map.resetHexTerrain(0);

        // fix cliff elevation pixels - set them to the lower of the two hex elevations
        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrain(x, y) == map.getTerrain("Cliff")) {

                    Hex hex = map.gridToHex(x, y);
                    // DR code added to trap hex=null
                    if(!(hex==null)) {
                        Hex oppositeHex = map.getAdjacentHex(hex, hex.getLocationHexside(hex.getNearestLocation(x, y)));

                        if (oppositeHex == null) {
                            map.setGridElevation(hex.getBaseHeight(), x, y);
                        } else {
                            map.setGridElevation(Math.min(hex.getBaseHeight(), oppositeHex.getBaseHeight()), x, y);
                        }
                    }
                }
            }
        }

        // apply building-type transformations
        HashMap<String, String> buildingTypes = boardArchive.getBuildingTypes();
        for (String hex : buildingTypes.keySet()) {

            Hex h = map.getHex(hex);
            Terrain toTerrain = map.getTerrain(buildingTypes.get(hex));
            changeAllTerrain(h.getCenterLocation().getTerrain(), toTerrain, h.getHexBorder());
        }
        setExteriorFactoryWalls();

        map.resetHexTerrain(0);

        // set depression elevations
        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrain(x, y).isDepression()) {

                    map.setGridElevation(map.getGridElevation(x, y) - 1, x, y);
                }
            }
        }

        map.resetHexTerrain(0);

        fixElevatedSunkenRoads();

        map.resetHexTerrain(0);

        addStairways();
    }

    /**
     * Spins through the map and adds stairways
     */
    private void addStairways() {

        BufferedImage boardImage = boardArchive.getBoardImage();

        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {

                if (boardArchive.isStairwayColor(getRGBColor(boardImage, x, y))) {
                    map.gridToHex(x, y).setStairway(true);
                }
            }
        }
    }

    /**
     * This is a kludge to fix elevated and sunken roads as they use the same colors
     */
    private void fixElevatedSunkenRoads() {

        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrain(x, y).getName().equals("Sunken Road")) {

                    // fix elevated roads
                    if (map.gridToHex(x, y).getBaseHeight() == 1) {
                        map.setGridElevation(1, x, y);
                        map.setGridTerrainCode(map.getTerrain("Elevated Road").getType(), x, y);
                    }

                    // fix sunken roads
                    else {
                        map.setGridElevation(-1, x, y);
                    }
                }
            }
        }
    }

    /*
    Get a <code>Color</code> object for a given pixel
     */
    private Color getRGBColor(BufferedImage image, int x, int y) {

        int c = image.getRGB(x, y);
        int red = (c & 0x00ff0000) >> 16;
        int green = (c & 0x0000ff00) >> 8;
        int blue = c & 0x000000ff;
        return new Color(red, green, blue);

    }

    public Map getMap() {
        return map;
    }

    /**
     * Determine where exterior factory wall exist
     * For factories, we need to set where the "boundary" of the factory is,
     * replacing it with the appropriate factory wall terrain.
     */
    private void setExteriorFactoryWalls() {

        // set the walls
        for (int x = 0; x < map.getGridWidth(); x++) {
            for (int y = 0; y < map.getGridHeight(); y++) {

                Terrain terr = map.getGridTerrain(x, y);
                int newTerr = terr.getType();
                if (terr.getName().equals("Wooden Factory, 1.5 Level")) {
                    newTerr = map.getTerrain("Wooden Factory Wall, 1.5 Level").getType();
                } else if (terr.getName().equals("Wooden Factory, 2.5 Level")) {
                    newTerr = map.getTerrain("Wooden Factory Wall, 2.5 Level").getType();
                } else if (terr.getName().equals("Stone Factory, 1.5 Level")) {
                    newTerr = map.getTerrain("Stone Factory Wall, 1.5 Level").getType();
                } else if (terr.getName().equals("Stone Factory, 2.5 Level")) {
                    newTerr = map.getTerrain("Stone Factory Wall, 2.5 Level").getType();
                } else if (terr.getName().equals("Roofless Stone Factory, 1.5 Level")) {
                    newTerr = map.getTerrain("Stone Factory Wall, 1.5 Level").getType();
                } else if (terr.getName().equals("Roofless Stone Factory, 2.5 Level")) {
                    newTerr = map.getTerrain("Stone Factory Wall, 2.5 Level").getType();
                } else if (terr.getName().equals("Wooden Factory, 1 Level")) {
                    newTerr = map.getTerrain("Wooden Factory Wall, 1 Level").getType();
                }

                if (map.getGridTerrain(x, y).isFactoryTerrain() &&
                        (!map.getGridTerrain(Math.max(x - 1, 0), y).isFactoryTerrain() ||
                                !map.getGridTerrain(Math.min(x + 1, map.getGridWidth()), y).isFactoryTerrain() ||
                                !map.getGridTerrain(x, Math.max(y - 1, 0)).isFactoryTerrain() ||
                                !map.getGridTerrain(x, Math.min(y + 1, map.getGridHeight())).isFactoryTerrain())
                        ) {
                    map.setGridTerrainCode(newTerr, x, y);
                }
            }
        }
    }

    /**
     * Determine where factory wall exist for the given terrain type within a rectangular area.
     * For factories, we need to set where the "boundry" of the factory is,
     * replacing it with the appropriate factory wall terrian.
     *
     * @param rect map area to update
     * @param terr building terrain type
     */
    public void setFactoryWalls(Rectangle rect, Terrain terr) {

        int startX = (int) rect.getX();
        int startY = (int) rect.getY();

        // map the terrain
        int newTerr = terr.getType();
        if (terr.getName().equals("Wooden Factory, 1.5 Level")) {
            newTerr = map.getTerrain("Wooden Factory Wall, 1.5 Level").getType();
        } else if (terr.getName().equals("Wooden Factory, 2.5 Level")) {
            newTerr = map.getTerrain("Wooden Factory Wall, 2.5 Level").getType();
        }
        if (terr.getName().equals("Stone Factory, 1.5 Level")) {
            newTerr = map.getTerrain("Stone Factory Wall, 1.5 Level").getType();
        }
        if (terr.getName().equals("Stone Factory, 2.5 Level")) {
            newTerr = map.getTerrain("Stone Factory Wall, 2.5 Level").getType();
        }

        // set the walls
        for (int x = Math.max(startX, 0);
             x < Math.min(startX + rect.getWidth(), map.getGridWidth());
             x++) {
            for (int y = Math.max(startY, 0);
                 y < Math.min(startY + rect.getHeight(), map.getGridHeight());
                 y++) {

                if (map.getGridTerrain(x, y).isFactoryTerrain() &&
                        (!map.getGridTerrain(Math.max(x - 1, 0), y).isFactoryTerrain() ||
                                !map.getGridTerrain(Math.min(x + 1, map.getGridWidth()), y).isFactoryTerrain() ||
                                !map.getGridTerrain(x, Math.max(y - 1, 0)).isFactoryTerrain() ||
                                !map.getGridTerrain(x, Math.min(y + 1, map.getGridHeight())).isFactoryTerrain())
                        ) {
                    map.setGridTerrainCode(newTerr, x, y);
                }
            }
        }
    }

    /**
     * Sets all pixels within the given shape to the new terrain type.
     *
     * @param s    map area to update
     * @param terr new terrain type
     */
    // set the grid terrain for an arbitrary shape
    public void setGridTerrain(Shape s, Terrain terr) {

        Rectangle rect = s.getBounds();
        int startX = (int) rect.getX();
        int startY = (int) rect.getY();
        int terrType = terr.getType();

        // set the terrain in the map grid
        for (int x = Math.max(startX, 0);
             x < Math.min(startX + rect.getWidth(), map.getGridWidth());
             x++) {
            for (int y = Math.max(startY, 0);
                 y < Math.min(startY + rect.getHeight(), map.getGridHeight());
                 y++) {

                if (s.contains(x, y)) {

                    // only apply rowhouse/factory walls to buildings
                    if (terr.isRowhouseFactoryWall()) {

                        Terrain currentTerrain = map.getGridTerrain(x, y);

                        //map rowhouse height to current building height
                        if (currentTerrain.getName().equals("Stone Building") || currentTerrain.getName().equals("Wooden Building")) {
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall").getType(), x, y);
                        } else if (currentTerrain.getName().equals("Stone Building, 1 Level") ||
                                currentTerrain.getName().equals("Wooden Building, 1 Level") ||
                                currentTerrain.getName().equals("Stone Factory, 1.5 Level") ||
                                currentTerrain.getName().equals("Wooden Factory, 1.5 Level")) {
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 1 Level").getType(), x, y);
                        } else if (currentTerrain.getName().equals("Stone Building, 2 Level") ||
                                currentTerrain.getName().equals("Wooden Building, 2 Level") ||
                                currentTerrain.getName().equals("Stone Factory, 2.5 Level") ||
                                currentTerrain.getName().equals("Wooden Factory, 2.5 Level")) {
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 2 Level").getType(), x, y);
                        } else if (currentTerrain.getName().equals("Stone Building, 3 Level") || currentTerrain.getName().equals("Wooden Building, 3 Level")) {
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 3 Level").getType(), x, y);
                        } else if (currentTerrain.getName().equals("Stone Building, 4 Level") || currentTerrain.getName().equals("Wooden Building, 4 Level")) {
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 4 Level").getType(), x, y);
                        }
                        // code changed by DR to implement Interior Factory walls
                        else if(terr.getName().equals("Stone Factory, 1.5 Level")) {
                            map.setGridTerrainCode(map.getTerrain("Stone Factory Wall, 1.5 Level").getType(), x, y);
                        } else if (terr.getName().equals("Wooden Factory, 1.5 Level")){
                            map.setGridTerrainCode(map.getTerrain("Wooden Factory Wall, 1.5 Level").getType(), x, y);
                        }
                        else if(terr.getName().equals("Stone Factory, 2.5 Level")) {
                            map.setGridTerrainCode(map.getTerrain("Stone Factory Wall, 2.5 Level").getType(), x, y);
                        } else if(terr.getName().equals("Wooden Factory, 2.5 Level")){
                            map.setGridTerrainCode(map.getTerrain("Wooden Factory Wall, 2.5 Level").getType(), x, y);
                        }
                    }

                    // special rule for Heavy Jungle - don't replace water
                    else if ("Dense Jungle".equals(terr.getName())) {

                        if (!(map.getGridTerrain(x, y).getLOSCategory() == Terrain.LOSCategories.WATER)) {
                            map.setGridTerrainCode(terrType, x, y);
                        }
                    } else {
                        map.setGridTerrainCode(terrType, x, y);
                    }
                }
            }
        }
    }


    /**
     * Sets the ground level of all pixels within the given shape to the new terrain height.
     *
     * @param s     map area to update
     * @param terr  applicable depression terrain
     * @param level new ground level
     */
    public void setGridGroundLevel(Shape s, Terrain terr, int level) {

        setGridGroundLevel(s, s.getBounds(), terr, level);
    }

    /**
     * Returns a set of hexes that intersect ("touch") the given rectangle.
     *
     * @param rect map area
     * @return a Vector containing the intersecting hexes
     */
    public Vector intersectedHexes(Map map, Rectangle rect) {

        Vector<Hex> hexes = new Vector<Hex>(5, 5);
        Hex currentHex;

        // find the hexes in the corner of the rectangle, clip to map boundry
        Hex upperLeft = map.gridToHex(
                Math.max((int) rect.getX(), 0),
                Math.max((int) rect.getY(), 0));
        Hex lowerRight = map.gridToHex(
                Math.min((int) (rect.getX() + rect.getWidth()), map.getGridWidth() - 1),
                Math.min((int) (rect.getY() + rect.getHeight()), map.getGridHeight() - 1));

        // Rectangle completely in a single hex? Add the hex and quit
        if (upperLeft == lowerRight) {

            hexes.addElement(upperLeft);
            return hexes;
        }

        // our desired bounds
        int minX = Math.max(upperLeft.getColumnNumber() - 1, 0);
        int minY = Math.max(upperLeft.getRowNumber() - 1, 0);
        int maxX = Math.min(lowerRight.getColumnNumber() + 1, map.getWidth() - 1);
        int maxY = Math.min(lowerRight.getRowNumber() + 1, map.getHeight());

        // check all hexes bound by the corners to the vector
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY;
                 y <= Math.min(maxY, map.getHexGrid()[x].length - 1);
                 y++) {
                currentHex = map.getHex(x, y);

                // add hexes that touch
                if (currentHex.isTouchedBy(rect)) {
                    hexes.addElement(map.getHex(x, y));
                }
            }
        }
        return hexes;
    }

    //TODO: does area need to be a parameter?

    /**
     * Sets the grid map ground level for a section of map
     *
     * @param s            the area to set
     * @param area         area of the map to update
     * @param terr         applicable depression terrain
     * @param newElevation the new elevation
     */
    private void setGridGroundLevel(Shape s, Rectangle area, Terrain terr, int newElevation) {

        // get the rectangle variables once
        int locX = (int) area.getX();
        int locY = (int) area.getY();
        int width = (int) area.getWidth();
        int height = (int) area.getHeight();

        Hex currentHex;

        // step through each pixel in the rectangle
        for (int x = Math.max(locX, 0); x <= Math.min(locX + width, map.getGridWidth() - 1); x++) {
            for (int y = Math.max(locY, 0); y <= Math.min(locY + height, map.getGridHeight() - 1); y++) {

                // point in brush?
                if (s == null || s.contains(x, y)) {

                    // setting to depression hex?
                    if (terr != null) {

                        // set the current hex
                        currentHex = map.gridToHex(x, y);

                        // if we're already a depression, use the current elevation
                        if (currentHex.isDepressionTerrain()) {
                            map.setGridElevation(currentHex.getBaseHeight(), x, y);
                        } else {
                            map.setGridElevation(currentHex.getBaseHeight() - 1, x, y);
                        }
                    } else {
                        map.setGridElevation(newElevation, x, y);
                    }
                }
            }
        }
    }

    /**
     * Maps all terrain from one type to another within a given shape.
     *
     * @parameter fromTerrain original terrain to replace
     * @parameter toTerrain new terrain type
     * @parameter s area of the map to change
     */
    public boolean changeAllTerrain(Terrain fromTerrain, Terrain toTerrain, Shape s) {

        char fromTerrainType = (char) fromTerrain.getType();
        char toTerrainType = (char) toTerrain.getType();

        boolean changed = false;

        // change the map grid
        for (int i = 0; i < map.getGridWidth(); i++) {
            for (int j = 0; j < map.getGridHeight(); j++) {
                if (map.getGridTerrain(i, j).getType() == fromTerrainType && s.contains((double) i, (double) j)) {

                    map.setGridTerrainCode(toTerrainType, i, j);
                    changed = true;
                }
            }
        }

        // change the hex grid
        if(map.getMapConfiguration().equals("TopLeftHalfHeightEqualRowCount")){
            for (int col = 0; col < map.getWidth(); col++) {
                for (int row = 0; row < map.getHeight(); row++) { // no extra hex for boards where each col has same number of rows (eg RO)

                    Hex[][] hexGrid = map.getHexGrid();

                    if (hexGrid[col][row].getHexBorder().intersects(s.getBounds())) {

                        hexGrid[col][row].changeAllTerrain(fromTerrain, toTerrain, s);
                        changed = true;
                    }
                }
            }
        } else {
            for (int col = 0; col < map.getWidth(); col++) {
                for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                    Hex[][] hexGrid = map.getHexGrid();

                    if (hexGrid[col][row].getHexBorder().intersects(s.getBounds())) {

                        hexGrid[col][row].changeAllTerrain(fromTerrain, toTerrain, s);
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Maps all ground level elevation from one level to another within a given shape.
     *
     * @parameter fromElevation original ground level elevation to replace
     * @parameter toTerrain new ground level elevation
     */
    public boolean changeAllGroundLevel(int fromElevation, int toElevation, Shape s) {

        boolean changed = false;

        // change the map grid
        for (int i = 0; i < map.getGridWidth(); i++) {
            for (int j = 0; j < map.getGridHeight(); j++) {
                if (map.getGridElevation(i, j) == fromElevation && s.contains((double) i, (double) j)) {

                    map.setGridElevation(toElevation, i, j);
                    changed = true;
                }
            }
        }

        // change the base height of the hex grid
        if(map.getMapConfiguration().equals("TopLeftHalfHeightEqualRowCount")){
            for (int col = 0; col < map.getWidth(); col++) {
                for (int row = 0; row < map.getHeight(); row++) { // no extra hex for boards where each col has same number of rows (eg RO)
                    Hex[][] hexGrid = map.getHexGrid();

                    if (hexGrid[col][row].getBaseHeight() == fromElevation &&
                        hexGrid[col][row].getHexBorder().intersects(s.getBounds())) {

                        hexGrid[col][row].setBaseHeight(toElevation);
                        changed = true;
                    }
                }
            }
        } else {
            for (int col = 0; col < map.getWidth(); col++) {
                for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                    Hex[][] hexGrid = map.getHexGrid();

                    if (hexGrid[col][row].getBaseHeight() == fromElevation &&
                        hexGrid[col][row].getHexBorder().intersects(s.getBounds())) {

                        hexGrid[col][row].setBaseHeight(toElevation);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Rotates the map 180 degrees. Should only be used for geomorphic map boards
     */
    public void flip() {

        map.flip();
    }

    /**
     * Read the LOS data or create it if it doesn't exist
     */
    public void readLOSData() {

        // code added by DR to enable unlimited cropping
        String offset=boardArchive.getHexGridConfig();
        if (offset==null){offset="Normal";}
        map = boardArchive.getLOSData(offset, false);
        if (map == null) {

            // convert the image
            createLOSData();
        }
    }

    public BufferedImage getBoardImage() {

        return boardArchive.getBoardImage();
    }

}
