package VASL.LOS;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import VASL.LOS.Map.Bridge;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Map;
import VASL.LOS.Map.Terrain;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.BoardMetadata;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;


/**
 * LOSDataEditor is used to edit the LOS data.
 * It's used by the LOS GUI and dynamically by VASL to perform SSR terrain changes
 */
//TODO: this class is a mess and needs a complete refactoring
// Need to move the methods used solely by the LOS editor to a subclass in the los-gui project
public class LOSDataEditor {

    // the LOS data
    Map map;

    // the board archive and shared board metadata
    BoardArchive boardArchive;
   SharedBoardMetadata sharedBoardMetadata;

    // standard elevation colors
    public final static Color VALLEY2_COLOR		= new Color(88, 110, 50);
    public final static Color VALLEY1_COLOR		= new Color(119, 146, 74);
    public final static Color LEVEL1_COLOR		= new Color(176, 147, 70);
    public final static Color LEVEL2_COLOR		= new Color(147, 111, 31);
    public final static Color LEVEL3_COLOR		= new Color(118, 80, 0);
    public final static Color LEVEL4_COLOR		= new Color(94, 57, 0);
    public final static Color LEVEL5_COLOR		= new Color(74, 45, 0);
    public final static Color LEVEL6_COLOR		= new Color(56, 34, 0);
    public final static Color LEVEL7_COLOR		= new Color(60, 25, 0);
    public final static Color LEVEL8_COLOR		= new Color(50, 20, 0);
    public final static Color LEVEL9_COLOR		= new Color(40, 15, 0);
    public final static Color LEVEL10_COLOR		= new Color(30, 10, 0);
    public final static Color WATER_EDGE_COLOR	= new Color(253, 255, 255);
    public final static Color GULLY_INTERIOR_COLOR	= new Color(148, 95, 35);

    // for terrain and elevation encoding while creating LOS data
    private static final int UNKNOWN_TERRAIN = 255;
    private static final int TERRAIN_OFFSET = 128;
    private static final int UNKNOWN_ELEVATION = -10;
    private static final int ELEVATION_OFFSET = 50;

    /**
     * Creates an LOS data editor for a VASL archive
     * @param boardName the name of the VASL board archive file
     * @param boardDirectory the VASL board archive directory
     * @exception java.io.IOException if the archive cannot be opened
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
     * @param map the map
     */
    public LOSDataEditor(Map map) {
        this.map = map;
    }

    public Map createNewLOSData(){

        Map m;
        if(boardArchive.isGEO()) {

            m = new Map(
                    boardArchive.getBoardWidth(),
                    boardArchive.getBoardHeight(),
                    sharedBoardMetadata.getTerrainTypes());
            m.setSlopes(boardArchive.getSlopes());
        }
        else {
            m = new Map(
                    boardArchive.getBoardWidth(),
                    boardArchive.getBoardHeight(),
                    boardArchive.getA1CenterX(),
                    boardArchive.getA1CenterY(),
                    boardArchive.getBoardImage().getWidth(),
                    boardArchive.getBoardImage().getHeight(),
                    sharedBoardMetadata.getTerrainTypes());
            m.setSlopes(boardArchive.getSlopes());
        }
        return m;
    }

    //TODO: removed redundancy in these private methods
    /**
     * Find the nearest terrain to a pixel of unknown terrain. E.g. the hex center dot will have unknown terrain
     * @param x x value of pixel
     * @param y y value of pixel
     * @return
     */
    private Terrain getNearestTerrain(int x, int y){

        int min = 0;
        int max = 1;
        Terrain terrainType;

        // search at most 5 pixels out
        while (max <= 5 ) {
            for (int i = x - max; i <= x + max; i++){
                for (int j = y - max; j <= y + max; j++){

                    // this logic make the search radius circular
                    if(	map.onMap(i, j) && Point.distance((double)x, (double)y, (double)i, (double)j) > min &&
                            Point.distance((double)x, (double)y, (double)i, (double)j) <= max &&
                            map.getGridTerrainCode(i, j) != UNKNOWN_TERRAIN &&
                            map.getGridTerrainCode(i, j) < TERRAIN_OFFSET){

                        terrainType = map.getGridTerrain(i, j);

                        // ignore inherent terrain
                        if (!terrainType.isInherentTerrain()){
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
     * @param x x value of pixel
     * @param y y value of pixel
     * @return
     */
    private int getNearestElevation(int x, int y){

        int min = 0;
        int max = 1;

        // search at most 50 pixels out
        while (max <= 50 ) {
            for (int i = x - max; i <= x + max; i++){
                for (int j = y - max; j <= y + max; j++){

                    // this logic makes the search radius circular
                    if(	map.onMap(i, j) && Point.distance((double)x, (double)y, (double)i, (double)j) > min &&
                            Point.distance((double)x, (double)y, (double)i, (double)j) <= max &&
                            map.getGridElevation(i, j) != UNKNOWN_ELEVATION &&
                            map.getGridElevation(i, j) < ELEVATION_OFFSET - 5){ // need a buffer for negative elevations

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

        for(int x = 0; x < map.getGridWidth(); x++) {
            for(int y = 0; y < map.getGridHeight(); y++) {

                Color color = getRGBColor(boardImage, x, y);

                int elevation = boardArchive.getElevationForColor(color);
                int terrain = boardArchive.getTerrainForColor(color);

                // set the elevation in the map grid
                if(boardArchive.boardHasElevations()) { // ignore boards without elevation

                    if(elevation == BoardArchive.getNoElevationColorCode()){
                        map.setGridElevation(UNKNOWN_ELEVATION, x, y);
                    }
                    else {
                        map.setGridElevation(elevation, x, y);
                    }
                }

                // set the terrain type in the map grid
                if(terrain == BoardArchive.getNoTerrainColorCode()){
                    map.setGridTerrainCode(UNKNOWN_TERRAIN, x, y);
                }
                else {
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
    private void setAllUnknownTerrain(){

        // find the nearest terrain encoding as we go
        for(int x = 0; x < map.getGridWidth(); x++) {
            for(int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrainCode(x, y) == UNKNOWN_TERRAIN) {
                    map.setGridTerrainCode(getNearestTerrain(x,y).getType() + TERRAIN_OFFSET, x, y);
                }

                if (map.getGridElevation(x, y) == UNKNOWN_ELEVATION){
                    map.setGridElevation(getNearestElevation(x,y) + ELEVATION_OFFSET, x, y);
                }
            }
        }

        // remove the terrain encoding
        for (int x = 0; x < map.getGridWidth(); x++){
            for (int y = 0; y < map.getGridHeight(); y++){
                if (map.getGridTerrainCode(x, y) >= TERRAIN_OFFSET) {
                    map.setGridTerrainCode(map.getGridTerrainCode(x, y) - TERRAIN_OFFSET, x, y);
                }
                if (map.getGridElevation(x, y) >= ELEVATION_OFFSET/2){
                    map.setGridElevation(map.getGridElevation(x, y) - ELEVATION_OFFSET, x, y);
                }
            }
        }
    }

    /**
     * Create the LOS data from the board image in the VASL archive
     * Assumes the file helpers have been set
     */
    public void createLOSData(){

        // create an empty map
        map = createNewLOSData();

        // spin through the terrain and elevation grids and add the terrain codes
        setAllTerrain();

        // fix pixels that have no terrain or elevation code (E.g. the hex center dot)
        setAllUnknownTerrain();

        // we need to occasionally update the hex grid as the following processes need updated hex information
        map.resetHexTerrain();

        // fix cliff elevation pixels - set them to the lower of the two hex elevations
        for(int x = 0; x < map.getGridWidth(); x++) {
            for(int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrain(x, y) == map.getTerrain("Cliff")) {

                    Hex hex = map.gridToHex(x, y);
                    Hex oppositeHex = map.getAdjacentHex(hex, hex.getLocationHexside(hex.getNearestLocation(x, y)));

                    if(oppositeHex == null){
                        map.setGridElevation(hex.getBaseHeight(), x, y);
                    }
                    else {
                        map.setGridElevation(Math.min(hex.getBaseHeight(), oppositeHex.getBaseHeight()), x, y);
                    }
                }
            }
        }

        // apply building-type transformations
        HashMap<String,String> buildingTypes = boardArchive.getBuildingTypes();
        for(String hex : buildingTypes.keySet()){

            Hex h = map.getHex(hex);
            Terrain toTerrain = map.getTerrain(buildingTypes.get(hex));
            changeAllTerrain(h.getCenterLocation().getTerrain(), toTerrain, h.getHexBorder());
        }
        setExteriorFactoryWalls();

        map.resetHexTerrain();

        // set depression elevations
        for(int x = 0; x < map.getGridWidth(); x++) {
            for(int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrain(x, y).isDepression()) {

                    map.setGridElevation(map.getGridElevation(x,y) - 1, x, y);
                }
            }
        }

        map.resetHexTerrain();

        fixElevatedSunkenRoads();

        map.resetHexTerrain();

        addStairways();
    }

    /**
     * Spins through the map and adds stairways
     */
    private void addStairways() {

        BufferedImage boardImage = boardArchive.getBoardImage();

        for(int x = 0; x < map.getGridWidth(); x++) {
            for(int y = 0; y < map.getGridHeight(); y++){

                if(boardArchive.isStairwayColor(getRGBColor(boardImage, x, y))) {
                    map.gridToHex(x, y).setStairway(true);
                }
            }
        }
    }

    /**
     * This is a kludge to fix elevated and sunken roads as they use the same colors
     */
    private void fixElevatedSunkenRoads(){

        for(int x = 0; x < map.getGridWidth(); x++) {
            for(int y = 0; y < map.getGridHeight(); y++) {

                if (map.getGridTerrain(x, y).getName().equals("Sunken Road") ) {

                    // fix elevated roads
                    if( map.gridToHex(x,y).getBaseHeight() == 1)
                    {
                        map.setGridElevation(1, x, y);
                        map.setGridTerrainCode(map.getTerrain("Elevated Road").getType(), x, y);
                    }

                    // fix sunken roads
                    else
                    {
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

        int c = image.getRGB(x,y);
        int  red = (c & 0x00ff0000) >> 16;
        int  green = (c & 0x0000ff00) >> 8;
        int  blue = c & 0x000000ff;
        return new Color(red,green,blue);

    }

    /**
     * Repaints an area of the map image. If a bridge or prefab building touches the paint area,
     * the entire bridge/building is repainted as well. This should be the first paint routine
     * called when recreating an area of the map image.
     * @param x left-most pixel column
     * @param y right-most pixel column
     * @param width width of the paint area
     * @param height height of the paint area
     * @param mapImage the map image
     * @param imageList list of the terrain images to use
     */
    @SuppressWarnings("UnusedDeclaration")
    public void paintMapArea(int x, int y, int width, int height,
                             BufferedImage mapImage,
                             BufferedImage[] imageList,
                             Image singleHexWoodenBridgeImage,
                             Image singleHexStoneBridgeImage
    ) {

        Hex currentHex;
        Terrain depressionTerrain;
        int terrType;
        Rectangle paintArea = new Rectangle(x, y, width, height);


//        try {

            // step through each pixel
            for (int i = Math.max(x, 0); i < Math.min(x + width + 1, map.getGridWidth() - 1); i++) {
                for (int j = Math.max(y, 0); j < Math.min(y + height + 1, map.getGridHeight() - 1); j++) {

                    // should we use the depression terrain? (ignore switch for sunken roads,
                    // non open ground terrain)
                    currentHex = map.gridToHex(i, j);
                    depressionTerrain = currentHex.getCenterLocation().getDepressionTerrain();

                    if (depressionTerrain != null &&
                            map.getGridTerrain(i,j).isOpen() &&
                            !depressionTerrain.getName().equals("Sunken Road") &&
                            map.getGridElevation(i,j) == currentHex.getBaseHeight()) {

                        terrType = depressionTerrain.getType();
                    }
                    else {

                        terrType = map.getGridTerrain(i,j).getType();
                    }

                    // get color for non-ground level open ground
                    Color c = null;
                    switch (map.getGridElevation(i,j)) {

                        case -2:
                            c = VALLEY2_COLOR;
                            break;
                        case -1:
                            c = VALLEY1_COLOR;
                            break;
                        case 1:
                            c = LEVEL1_COLOR;
                            break;
                        case 2:
                            c = LEVEL2_COLOR;
                            break;
                        case 3:
                            c = LEVEL3_COLOR;
                            break;
                        case 4:
                            c = LEVEL4_COLOR;
                            break;
                        case 5:
                            c = LEVEL5_COLOR;
                            break;
                        case 6:
                            c = LEVEL6_COLOR;
                            break;
                        case 7:
                            c = LEVEL7_COLOR;
                            break;
                        case 8:
                            c = LEVEL8_COLOR;
                            break;
                        case 9:
                            c = LEVEL9_COLOR;
                            break;
                        case 10:
                            c = LEVEL10_COLOR;
                            break;
                    }

                    // create the two-tone colors for depression terrain
                    boolean overridePixelColor = false;
//                    if (map.getTerrain(terrType).isWaterTerrain() || map.getTerrain(terrType).getName().equals("Gully")) {
                    if (map.getTerrain(terrType).getName().equals("Gully")) {

                        // get the distance to the nearest non-water terrain
                        double dist = 10.0;
                        double currDist;
                        Point p = new Point(0, 0);
                        for (int a = -6; a <= 6; a++) {
                            for (int b = -6; b <= 6; b++) {

                                if ( map.onMap(i + a, j + b) &&
                                        (((map.getTerrain(terrType).getName().equals("Gully") ||
                                                map.getTerrain(terrType).getName().equals("Shallow Stream") ||
                                                map.getTerrain(terrType).getName().equals("Deep Stream")) &&
                                                map.getGridElevation(i,j) != map.getGridElevation(i + a,j + b) ||
                                                (!(map.getTerrain(terrType).getName().equals("Gully") ||
                                                        map.getTerrain(terrType).getName().equals("Shallow Stream") ||
                                                        map.getTerrain(terrType).getName().equals("Deep Stream") &&
                                                                map.getGridTerrain(i + a,j + b).isWaterTerrain()))))) {

                                    currDist = p.distance((double) a, (double) b);
                                    if (currDist < dist) {

                                        dist = currDist;
                                    }
                                }
                            }
                        }

                        // set the outer-color pixel
                        if (map.getTerrain(terrType).getName().equals("Gully")) {

                            // brown middle for Gullies
                            if (5.0 < dist) {

                                mapImage.setRGB(i, j, GULLY_INTERIOR_COLOR.getRGB());
                                overridePixelColor = true;
                            }
                        }
                        else if (map.getTerrain(terrType).isStream()) {

                            if (0.0 < dist && dist <= 5.0) {

                                mapImage.setRGB(i, j, VALLEY1_COLOR.getRGB());
                                overridePixelColor = true;
                            }
                            else if (5.0 < dist && dist < 10.0) {

                                mapImage.setRGB(i, j, WATER_EDGE_COLOR.getRGB());
                                overridePixelColor = true;
                            }
                        }
                        else {

                            if (0.0 < dist && dist <= 5.0) {

                                mapImage.setRGB(i, j, WATER_EDGE_COLOR.getRGB());
                                overridePixelColor = true;
                            }

                        }
                    }

                    // image exist for this terrain?
                    if (!overridePixelColor && imageList[terrType] == null) {

                        // open ground color on an elevation?
                        if (map.getTerrain(terrType).getMapColor().equals(map.getTerrain("Open Ground").getMapColor())
                                && map.getGridElevation(i,j)  != 0) {

                            mapImage.setRGB(i, j, c.getRGB());

                        }
                        else {
                            mapImage.setRGB(i, j, map.getTerrain(terrType).getMapColor().getRGB());
                        }
                    }
                    else if (!overridePixelColor) {

                        // open ground color on an elevation?
                        if (imageList[terrType].getRGB(i % imageList[terrType].getWidth(), j % imageList[terrType].getHeight()) ==
                                map.getTerrain("Open Ground").getMapColor().getRGB() &&
                                map.getGridElevation(i,j)  != 0) {

                            mapImage.setRGB(i, j, c.getRGB());
                        }
                        else {
                            mapImage.setRGB(i, j, imageList[terrType].getRGB(i % imageList[terrType].getWidth(), j % imageList[terrType].getHeight()));
                        }
                    }
                }
            }


        // paint the bridges...
        // create a temp image for translated bridge image
        Bridge bridge = null;
        Image bridgeImage = null;

        Hex[][] hexGrid = map.getHexGrid();
        for (int col = 0; col < hexGrid.length; col++) {
            for (int row = 0; row < hexGrid[col].length; row++) {

                currentHex = hexGrid[col][row];

                // has a bridge?
                if (currentHex.hasBridge() && currentHex.getExtendedHexBorder().getBounds().intersects(paintArea)) {

                    // set the bridge, etc.
                    bridge = currentHex.getBridge();

                    // set the image
                    if (bridge.getTerrain().getName().equals("Single Hex Stone Bridge")) {

                        bridgeImage = singleHexStoneBridgeImage;
                    }
                    else if (bridge.getTerrain().getName().equals("Single Hex Wooden Bridge")) {

                        bridgeImage = singleHexWoodenBridgeImage;
                    }
                    else {

                        bridgeImage = imageList[bridge.getTerrain().getType()];
                    }

                    // has image?
                    if (bridgeImage == null) {

                        System.err.println("No image found for bridge " + bridge.getTerrain().getName() + " in hex " + currentHex.getName());

                    }
                    else {

                        Graphics2D g = (Graphics2D) mapImage.getGraphics();

                        // need to translate?
                        if (bridge.getRotation() != 0) {
                            g.setTransform(AffineTransform.getRotateInstance(
                                    Math.toRadians(bridge.getRotation()),
                                    (int) bridge.getCenter().getX(),
                                    (int) bridge.getCenter().getY()
                            ));
                        }

                        g.drawImage(
                                bridgeImage,
                                (int) bridge.getCenter().getX() - bridgeImage.getWidth(null) / 2,
                                (int) bridge.getCenter().getY() - bridgeImage.getHeight(null) / 2,
                                null);

                        // free resources
                        g.dispose();
                    }
                }
            }
        }

    }

    public Map getMap() {
        return map;
    }

    /**
     * Paints the contour lines into an area of the map image.
     * This should be called after paintMapArea when recreating the map image.
     * @param x left-most pixel column
     * @param y right-most pixel column
     * @param width width of the paint area
     * @param height height of the paint area
     * @param img the map image
     */
    public void paintMapContours(int x, int y, int width, int height, BufferedImage img) {

        // create map in image
        int gridWidth = map.getGridWidth();
        int gridHeight = map.getGridHeight();
        for (int col = Math.max(x, 0); col < Math.min(x + width, gridWidth); col++) {
            for (int row = Math.max(y, 0); row < Math.min(y + height, gridHeight); row++) {

                // grid adjacent to lower ground level?
                if (((col > 0 && map.getGridElevation(col, row) > map.getGridElevation(col - 1,row)) ||
                        (row > 0 && map.getGridElevation(col, row) > map.getGridElevation(col, row - 1)) ||
                        (col < gridWidth - 1 && map.getGridElevation(col, row) > map.getGridElevation(col + 1,row)) ||
                        (row < gridHeight - 1 && map.getGridElevation(col, row) > map.getGridElevation(col,row + 1)))
                        ) {
                    img.setRGB(col, row, 0xFFFF0F0F);
                }
            }
        }
    }

    /**
     * Paints the shadows into an area of the map image.
     * This should be called after paintMapArea when recreating the map image.
     * @param x left-most pixel column
     * @param y right-most pixel column
     * @param width width of the paint area
     * @param height height of the paint area
     * @param img the map image
     */
    public void paintMapShadows(int x, int y, int width, int height, BufferedImage img) {

        int currentHeight;
        int currentTerrainHeight;
        int groundLevel;
        Terrain currentTerrain;
        Hex currentHex = null;
        Hex tempHex;

        // Bridge stuff
        Bridge bridge = null;

        // number of pixels to shadow per level
        int pixelsPerLevel = 5;
        int pixelsPerHalfLevel = 2;

        // paint the map shadows in the image
        for (int col = Math.max(x, 0); col < Math.min(x + width - 1, map.getGridWidth()); col++) {

            // set the height of the first location in the grid column
            currentTerrain = map.getGridTerrain(col, Math.max(y - 1, 0));

            currentHeight = pixelsPerLevel * (currentTerrain.getHeight() + map.getGridElevation(col, Math.max(y - 1, 0)));

            // add half level height
            if (currentTerrain.isHalfLevelHeight()) {
                currentHeight += pixelsPerHalfLevel;

            }

            for (int row = Math.max(y - 1, 0); row < Math.min(y + height + pixelsPerLevel * 3, map.getGridHeight()); row++) {

                // set the current hex
                tempHex = map.gridToHex(col, row);
                if (tempHex != currentHex) {

                    // set the bridge
                    currentHex = tempHex;
                    bridge = currentHex.getBridge();
                }

                if (bridge != null && bridge.getShape().contains(col, row)) {

                    groundLevel = bridge.getRoadLevel() * pixelsPerLevel;
                    currentTerrainHeight = pixelsPerHalfLevel;

                }
                else {

                    currentTerrain = map.getGridTerrain(col, row);

                    currentTerrainHeight = currentTerrain.getHeight() * pixelsPerLevel;

                    if (currentTerrain.isHalfLevelHeight()) {

                        currentTerrainHeight += pixelsPerHalfLevel;
                    }
                    groundLevel = map.getGridElevation(col, row) * pixelsPerLevel;
                }

                // darken pixels in shadow
                if (currentTerrainHeight + groundLevel < currentHeight) {

                    // parse the pixel
                    int pixel = img.getRGB(col, row);
                    int alpha = pixel & 0xFF000000;
                    int red = pixel & 0x000000FF;
                    int green = (pixel & 0x0000FF00) >> 8;
                    int blue = (pixel & 0x00FF0000) >> 16;

                    // apply shadow
                    red = (int) ((float) red * 0.7);
                    green = (int) ((float) green * 0.7);
                    blue = (int) ((float) blue * 0.7);

                    // re-assemble and paint
                    pixel = alpha | red | (green << 8) | (blue << 16);
                    img.setRGB(col, row, pixel);

                    currentHeight -= 1;
                }
                else if (currentTerrainHeight + groundLevel > currentHeight) {

                    // parse the pixel
                    int pixel = img.getRGB(col, row);
                    int alpha = pixel & 0xFF000000;
                    int red = pixel & 0x000000FF;
                    int green = (pixel & 0x0000FF00) >> 8;
                    int blue = (pixel & 0x00FF0000) >> 16;

                    // apply highlight
                    red = (int) Math.min(255, (float) (red + 50));
                    green = (int) Math.min(255, (float) (green + 50));
                    blue = (int) Math.min(255, (float) (blue + 50));

                    // need to use custom color for woods
                    if(currentTerrain.getName().equals("Woods")) {
                        green = 250;
                    }
                    // re-assemble and paint
                    pixel = alpha | red | (green << 8) | (blue << 16);
                    img.setRGB(col, row, pixel);

                    // set the current height
                    currentHeight = currentTerrainHeight + groundLevel;
                }
            }
        }
    }

    /**
     * Paints hex terrain that is not in a location - e.g. slopes and railroads
     */
    public void paintAncillaryHexTerrain(BufferedImage img) {

        // get graphics handle
        Graphics2D workSpace = (Graphics2D) img.getGraphics();
        workSpace.setColor(Color.RED);
        workSpace.setFont(new Font("Arial", Font.BOLD, 12));

        Hex currentHex = null;
        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight() + (x % 2); y++) { // add 1 hex if odd
                currentHex = map.getHex(x, y);
                for (int z = 0; z < 6; z++){
                    if(currentHex.hasSlope(z)) {

                        String s = "S";
                        workSpace.drawString(
                                s,
                                currentHex.getHexsideLocation(z).getEdgeCenterPoint().x - workSpace.getFontMetrics().stringWidth(s) / 2,
                                currentHex.getHexsideLocation(z).getEdgeCenterPoint().y
//                                        h.getCenterLocation().getLOSPoint().x -
//                                                workSpace.getFontMetrics().stringWidth(h.getName()) / 2 +
//                                                (h.getColumnNumber() == 0 ? 6 : 0) +
//                                                (h.getColumnNumber() == map.getHexGrid().length - 1 ? -7 : 0),
//                                        h.getHexsideLocation(0).getLOSPoint().y + 10
                        );
                    }
                }
            }
        }
    }

    /**
     * Sets all pixels within the given rectangle to the new terrain type.
     * @param rect map area to update
     * @param terr new terrain type
     */
    public void setGridTerrain(Rectangle rect, Terrain terr) {

        int startX = (int) rect.getX();
        int startY = (int) rect.getY();

        // set the terrain in the map grid
        for (int x = Math.max(startX, 0);
             x < Math.min(startX + rect.getWidth(), map.getGridWidth());
             x++) {
            for (int y = Math.max(startY, 0);
                 y < Math.min(startY + rect.getHeight(), map.getGridHeight());
                 y++) {

                map.setGridTerrainCode(terr.getType(), x, y);

            }
        }

        // set the factory walls, if necessary
        if (terr.isFactoryTerrain()) {
            setFactoryWalls(rect, terr);
        }
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
                if(terr.getName().equals("Wooden Factory, 1.5 Level")) {
                    newTerr = map.getTerrain("Wooden Factory Wall, 1.5 Level").getType();
                }
                else if(terr.getName().equals("Wooden Factory, 2.5 Level")) {
                    newTerr = map.getTerrain("Wooden Factory Wall, 2.5 Level").getType();
                }
                else if(terr.getName().equals("Stone Factory, 1.5 Level")) {
                    newTerr = map.getTerrain("Stone Factory Wall, 1.5 Level").getType();
                }
                else if(terr.getName().equals("Stone Factory, 2.5 Level")) {
                    newTerr = map.getTerrain("Stone Factory Wall, 2.5 Level").getType();
                }

                if (map.getGridTerrain(x,y).isFactoryTerrain() &&
                        (!map.getGridTerrain(Math.max(x - 1, 0), y).isFactoryTerrain() ||
                         !map.getGridTerrain(Math.min(x + 1, map.getGridWidth()), y).isFactoryTerrain()  ||
                         !map.getGridTerrain(x, Math.max(y - 1, 0)).isFactoryTerrain()  ||
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
     * @param rect map area to update
     * @param terr building terrain type
     */
    private void setFactoryWalls(Rectangle rect, Terrain terr) {

        int startX = (int) rect.getX();
        int startY = (int) rect.getY();

        // map the terrain
        int newTerr = terr.getType();
        if(terr.getName().equals("Wooden Factory, 1.5 Level")) {
            newTerr = map.getTerrain("Wooden Factory Wall, 1.5 Level").getType();
        }
        else if(terr.getName().equals("Wooden Factory, 2.5 Level")) {
            newTerr = map.getTerrain("Wooden Factory Wall, 2.5 Level").getType();
        }
        if(terr.getName().equals("Stone Factory, 1.5 Level")) {
            newTerr = map.getTerrain("Stone Factory Wall, 1.5 Level").getType();
        }
        if(terr.getName().equals("Stone Factory, 2.5 Level")) {
            newTerr = map.getTerrain("Stone Factory Wall, 2.5 Level").getType();
        }

        // set the walls
        for (int x = Math.max(startX, 0);
             x < Math.min(startX + rect.getWidth(), map.getGridWidth());
             x++) {
            for (int y = Math.max(startY, 0);
                 y < Math.min(startY + rect.getHeight(), map.getGridHeight());
                 y++) {

                if (map.getGridTerrain(x,y).isFactoryTerrain() &&
                        (!map.getGridTerrain(Math.max(x - 1, 0), y).isFactoryTerrain() ||
                                !map.getGridTerrain(Math.min(x + 1, map.getGridWidth()), y).isFactoryTerrain()  ||
                                !map.getGridTerrain(x, Math.max(y - 1, 0)).isFactoryTerrain()  ||
                                !map.getGridTerrain(x, Math.min(y + 1, map.getGridHeight())).isFactoryTerrain())
                        ) {
                    map.setGridTerrainCode(newTerr, x, y);
                }
            }
        }
    }

    /**
     * Paints the hex grid into the map image. Also paints the hex centers mark (including
     * tunnel/sewer, stairway symbols). Shows if smoke and entrenchments exist in hex (for now).
     * This should be called after all other map painting routines when recreating the map image.
     * @param img the map image
     */
    // create hex outlines in image
    public void paintMapHexes(Image img) {

        paintMapHexes(img, Color.black);

    }

    // create hex outlines in image
    private void paintMapHexes(Image img, Color c) {

        // paint each hex
        Hex[][] hexGrid = map.getHexGrid();
        for (int col = 0; col < hexGrid.length; col++) {
            for (int row = 0; row < hexGrid[col].length; row++) {

                paintMapHex(img, hexGrid[col][row], true, c);
            }
        }
    }

    // paint a single hex
    private void paintMapHex(Image img, Hex h, boolean paintName, Color c) {

        // get graphics handle
        Graphics2D workSpace = (Graphics2D) img.getGraphics();
        workSpace.setColor(c);

        // draw hex border
        workSpace.drawPolygon(h.getHexBorder());

        // draw hex name
        if (paintName) {
            workSpace.setFont(new Font("Arial", Font.PLAIN, 10));

            workSpace.drawString(
                    h.getName(),
                    h.getCenterLocation().getLOSPoint().x -
                            workSpace.getFontMetrics().stringWidth(h.getName()) / 2 +
                            (h.getColumnNumber() == 0 ? 6 : 0) +
                            (h.getColumnNumber() == map.getHexGrid().length - 1 ? -7 : 0),
                    h.getHexsideLocation(0).getLOSPoint().y + 10
            );

            // draw hex center
            workSpace.setColor(Color.white);
            if (h.hasStairway() &&
                    !(h.getCenterLocation().getTerrain().getName().equals("Stone Building, 1 Level")||
                            h.getCenterLocation().getTerrain().getName().equals("Wooden Building, 1 Level"))) {

                workSpace.fillRect(h.getHexCenter().x - 3, h.getHexCenter().y - 3, 6, 6);
            }
            else {
                workSpace.fillRect(h.getHexCenter().x - 1, h.getHexCenter().y - 1, 2, 2);
            }
        }
    }

    /**
     * Sets all pixels within the given shape to the new terrain type.
     * @param s map area to update
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
                    if (terr.isRowhouseWall()) {

                        Terrain currentTerrain = map.getGridTerrain(x, y);

                        //map rowhouse height to current building height
                        if(currentTerrain.getName().equals("Stone Building") || currentTerrain.getName().equals("Wooden Building")){
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall").getType(), x, y);
                        }
                        else if(currentTerrain.getName().equals("Stone Building, 1 Level") ||
                                currentTerrain.getName().equals("Wooden Building, 1 Level") ||
                                currentTerrain.getName().equals("Stone Factory, 1.5 Level") ||
                                currentTerrain.getName().equals("Wooden Factory, 1.5 Level")){
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 1 Level").getType(), x, y);
                        }
                        else if(currentTerrain.getName().equals("Stone Building, 2 Level") ||
                                currentTerrain.getName().equals("Wooden Building, 2 Level") ||
                                currentTerrain.getName().equals("Stone Factory, 2.5 Level") ||
                                currentTerrain.getName().equals("Wooden Factory, 2.5 Level")){
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 2 Level").getType(), x, y);
                        }
                        else if(currentTerrain.getName().equals("Stone Building, 3 Level") || currentTerrain.getName().equals("Wooden Building, 3 Level")){
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 3 Level").getType(), x, y);
                        }
                        else if(currentTerrain.getName().equals("Stone Building, 4 Level") || currentTerrain.getName().equals("Wooden Building, 4 Level")){
                            map.setGridTerrainCode(map.getTerrain("Rowhouse Wall, 4 Level").getType(), x, y);
                        }
                    }

                    // special rule for Heavy Jungle - don't replace water
                    else if ("Dense Jungle".equals(terr.getName())){

                        if(!(map.getGridTerrain(x, y).getLOSCategory() == Terrain.LOSCategories.WATER)) {
                            map.setGridTerrainCode(terrType, x, y);
                        }
                    }
                    else {
                        map.setGridTerrainCode(terrType, x, y);
                    }
                }
            }
        }
    }


    /**
     * Sets the hex terrain for an area of the map. All locations within the given shape are changed.
     * @param s map area to change
     * @param terr new terrain type
     */
    public void setHexTerrain(Shape s, Terrain terr) {

        // get the affected hexes
        Vector<Hex> v = intersectedHexes(map, s.getBounds());
        Hex currentHex = null;
        for (Hex aHex : v) {

            // set the center location
            if (s.contains(aHex.getCenterLocation().getLOSPoint())) {

                aHex.getCenterLocation().setTerrain(terr);

            }

            // set the terrain on the hexsides
            for (int x = 0; x < 6; x++) {
                if (s.contains(aHex.getHexsideLocation(x).getEdgeCenterPoint())) {

                    aHex.setHexsideLocationTerrain(x, terr);
                }
            }
        }
    }

    /**
     * Sets the ground level of all pixels within the given shape to the new terrain height.
     * @param s map area to update
     * @param terr applicable depression terrain
     * @param level new ground level
     */
    public void setGridGroundLevel(Shape s, Terrain terr, int level) {

        setGridGroundLevel(s, s.getBounds(), terr, level);
    }

    /**
     * Returns a set of hexes that intersect ("touch") the given rectangle.
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
     * @param s the area to set
     * @param area area of the map to update
     * @param terr applicable depression terrain
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
                        }
                        else {
                            map.setGridElevation(currentHex.getBaseHeight() - 1, x, y);
                        }
                    }
                    else {
                        map.setGridElevation(newElevation, x, y);
                    }
                }
            }
        }
    }

    /**
     * Sets the hex ground level/depression terrain for a section of map.
     * Should be called after setGridGroundLevel.
     * @param s map area to update
     * @param terr applicable depression terrain
     * @param newLevel new ground level
     */
    public void setHexGroundLevel(Shape s, Terrain terr, int newLevel) {

        // set the hex base elevation and depression terrain
        Vector v = intersectedHexes(map, s.getBounds());
        Iterator iter = v.iterator();
        Hex currentHex = null;
        Location center = null;
        while (iter.hasNext()) {

            currentHex = (Hex) iter.next();
            center = currentHex.getCenterLocation();

            // set the center location
            if (s.contains(center.getLOSPoint())) {

                if (terr != null) {

                    // hex being set to depression terrain?
                    if (!center.isDepressionTerrain()) {

                        currentHex.setBaseHeight(map.getGridElevation(
                                (int) center.getLOSPoint().getX(),
                                (int) center.getLOSPoint().getY()));

                        //set the depression terrain, base level of the six hexsides
                        for (int x = 0; x < 6; x++) {

                            // on map?
                            if ((x == 0 && currentHex.isNorthOnMap()) ||
                                    (x == 1 && currentHex.isNorthEastOnMap()) ||
                                    (x == 2 && currentHex.isSouthEastOnMap()) ||
                                    (x == 3 && currentHex.isSouthOnMap()) ||
                                    (x == 4 && currentHex.isSouthWestOnMap()) ||
                                    (x == 5 && currentHex.isNorthWestOnMap())

                                    ) {

                                // if the hexside location has the same elevation as the center
                                // (on the grid), then make it a depression location
                                if (map.getGridElevation(
                                        (int) center.getLOSPoint().getX(),
                                        (int) center.getLOSPoint().getY())
                                        ==
                                        map.getGridElevation(
                                                (int) currentHex.getHexsideLocation(x).getEdgeCenterPoint().getX(),
                                                (int) currentHex.getHexsideLocation(x).getEdgeCenterPoint().getY())
                                        ) {
                                    currentHex.getHexsideLocation(x).setBaseHeight(0);
                                    currentHex.getHexsideLocation(x).setDepressionTerrain(terr);
                                }
                                else {
                                    // non-depression hexside locations are one level higher
                                    currentHex.getHexsideLocation(x).setBaseHeight(1);
                                }
                            }
                        }
                    }
                }
                else {
                    currentHex.setBaseHeight(newLevel);
                }

                // update the depression terrain for the hex
                currentHex.setDepressionTerrain(terr);
            }

            // set the depression terrain on the hexsides
            if (terr != null) {
                for (int x = 0; x < 6; x++) {
                    if (s.contains(currentHex.getHexsideLocation(x).getEdgeCenterPoint())) {

                        currentHex.setHexsideDepressionTerrain(x);

                        // if center is depression, ensure base level is reset
                        if (center.isDepressionTerrain()) {
                            currentHex.getHexsideLocation(x).setBaseHeight(0);
                        }
                    }
                }
            }
        }
    }

    /**
     * Maps all terrain from one type to another in the whole map.
     * @parameter fromTerrain original terrain to replace
     * @parameter toTerrain new terrain type
     */
    public boolean changeAllTerrain(Terrain fromTerrain, Terrain toTerrain) {

        return changeAllTerrain(fromTerrain, toTerrain, new Rectangle(0, 0, map.getGridWidth(), map.getGridHeight()));
    }

    /**
     * Maps all terrain from one type to another within a given shape.
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
        for (int col = 0; col < map.getWidth(); col++) {
            for (int row = 0; row < map.getHeight() + (col % 2); row++) {

                Hex[][] hexGrid = map.getHexGrid();

                if (hexGrid[col][row].getHexBorder().intersects(s.getBounds())) {

                    hexGrid[col][row].changeAllTerrain(fromTerrain, toTerrain, s);
                    changed = true;
                }
            }
        }

        return changed;
    }

    /**
     * Maps all ground level elevation from one level to another in the whole map.
     * @parameter fromElevation original ground level elevation to replace
     * @parameter toTerrain new ground level elevation
     */
    public boolean changeAllGroundLevel(int fromElevation, int toElevation) {

        return changeAllGroundLevel(fromElevation, toElevation, new Rectangle(0, 0, map.getGridWidth(), map.getGridHeight()));
    }

    /**
     * Maps all ground level elevation from one level to another within a given shape.
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
        return changed;
    }

    /**
     * Rotates the map 180 degrees. Should only be used for geomorphic map boards
     */
    public void flip() {

        map.flip();
    }

    /**
     *	This method is intended to be used only to copy geomorphic maps into
     *	a larger map "grid" for VASL. As such, 1) it is assumed the half hex along board
     *	edges are compatible, and 2) the hex/location names from the map that is being
     *	inserted should be used. Other uses will produce unexpected results.
     */
    public boolean insertMap(Map insertMap, Hex upperLeft) {

        return map.insertMap(insertMap, upperLeft);
    }

    /**
     * Write the LOS data to the board archive
     */
    public void saveLOSData() {

        boardArchive.writeLOSData(map);
    }

    /**
     * Read the LOS data or create it if it doesn't exist
     */
    public void readLOSData(){

        map = boardArchive.getLOSData();

        if (map == null) {

            // convert the image
            createLOSData();
        }
    }

    public String getArchiveName() {
        return boardArchive.getBoardName();
    }

    public BufferedImage getBoardImage(){

        return boardArchive.getBoardImage();
    }

    /**
     * Return a map of all terrain names and types
     * @return
     */
    public HashMap<String,String> getTerrainNames(){

        HashMap<String, String> terrainNames = new HashMap<String, String>(boardArchive.getTerrainTypes().size());

        for (String key: boardArchive.getTerrainTypes().keySet()){

            terrainNames.put(key, boardArchive.getTerrainTypes().get(key).getLOSCategory().toString());
        }

        return terrainNames;

    }

    /**
     * @return the shared board metadata
     */
    public SharedBoardMetadata getSharedBoardMetadata() {
        return sharedBoardMetadata;
    }
}
