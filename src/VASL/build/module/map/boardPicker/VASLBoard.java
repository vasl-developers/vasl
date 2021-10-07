/*
 * $Id: VASLBoard.java 8947 2013-11-21 15:49:18Z davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

package VASL.build.module.map.boardPicker;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

import VASL.LOS.LOSDataEditor;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Map;
import VASL.LOS.Map.Terrain;
import VASL.build.module.map.boardArchive.LOSSSRule;

/**
 * Extends ASLBoard to add support for version 6+ boards
 */
public class VASLBoard extends ASLBoard {

    public VASLBoard(){

        super();
    }

    /**
     * @return the width of the board in hexes
     */
    public int getWidth() {return VASLBoardArchive.getBoardWidth();}

    /**
     * @return the height of the board in hexes
     */
    public int getHeight() {return VASLBoardArchive.getBoardHeight();}

    /**
	 * @return the height of the map hexes in pixels
	 */
	public double getHexHeight() {
		return VASLBoardArchive.getHexHeight();
	}

    /**
     * @return the width of the map hexes in pixels
     */
    public double getHexWidth(){ return VASLBoardArchive.getHexWidth();}

    /**
     * @return x location of the A1 center hex dot
     */
    public double getA1CenterX() { return VASLBoardArchive.getA1CenterX();}

    /**
     * @return y location of the A1 center hex dot
     */
    public double getA1CenterY() { return VASLBoardArchive.getA1CenterY();}

    /**
     * Is the board cropped?
     */
    public boolean isCropped() {
        final Rectangle croppedBounds = getCropBounds();
        return !(croppedBounds.x == 0 && croppedBounds.y == 0 && croppedBounds.width == -1 && croppedBounds.height == -1);
    }

    /**
     * Crops the LOS data
     * @param losData the map LOS data
     */
    public Map cropLOSData(Map losData) {
        if(!isCropped()) {
            return null;
        }
        else {
            int offset=0;
			final Rectangle bounds = new Rectangle(getCropBounds());
            if(!(getA1CenterX()==0) && !(getA1CenterX()==-999) && !(getA1CenterX()==-901)) { offset = (int) (getA1CenterX());}
             if(bounds.width == -1) {
                bounds.width = getUncroppedSize().width;
            }
            if(bounds.height == -1) {
                bounds.height = getUncroppedSize().height;
            }
            String cropconfig="";
            if(this.nearestFullRow) {
                cropconfig="FullHex";
                if (this.getCropBounds().getX() == 0) {cropconfig = "FullHexLeftHalf";}
                if (this.getCropBounds().getMaxX() == this.getUncroppedSize().getWidth()) {cropconfig = "FullHexRightHalf";}
            }
            return losData.crop(new Point(bounds.x , bounds.y), new Point(bounds.x  + bounds.width, bounds.y + bounds.height), offset, cropconfig);
        }
    }

    /**
     * @return a rectangle defining the board's location within the map
     */
    public Rectangle getBoardLocation() {

        // the easiest way to do this is to use the boundary rectangle and remove the edge buffer
		final Rectangle rectangle = new Rectangle(boundaries);
        rectangle.translate(-1 * map.getEdgeBuffer().width, -1 * map.getEdgeBuffer().height);
        return rectangle;
    }

    /**
     * @return the LOS data
     * @param terrainTypes the terrain types
     */
    public Map getLOSData(HashMap<String, Terrain> terrainTypes, String offset, boolean isCropping, double gridadj){
        return VASLBoardArchive.getLOSData(terrainTypes, offset, isCropping, gridadj);
    }

    @Override
	public String getName() {

        if(isLegacyBoard()) {
            return super.getName();
        }
        else {
            return VASLBoardArchive.getBoardName();
        }
    }

    /**
     * Applies the color scenario-specific rules to the LOS data
     * @param LOSData the LOS data to modify
     */
    public void applyColorSSRules(Map LOSData, HashMap<String, LOSSSRule> losssRules, double gridadj) throws BoardException {

        if(!isLegacyBoard() && !terrainChanges.isEmpty()) {

            boolean changed = false; // changes made?

            // There is no explicit PTO rule so it has to be inferred so we don't miss Light Jungle
            boolean PTO = false;

            // step through each SSR token
            final StringTokenizer st = new StringTokenizer(terrainChanges, "\t");
            while (st.hasMoreTokens()) {

				final String s = st.nextToken();

				final LOSSSRule rule = losssRules.get(s);
                if(rule == null) {
                    /* this fix allows LOS checking across BSO on the RB/RO boards
                       an effort was made to expand this to all BSO on all LOS-enabled boards
                       but too many problems/exceptions were encounted. Since the RB code seemed to work, I have left it in. Doug Rimmer September 2018 */
                    if(this.name.equals("RBv3") || this.name.equals("RO")){
                       applyRBROrule(s, LOSData);
                       changed = true;
                    }else {
                        // deleting the BoardException so that BSO no longer disable los but are treated as per regular overlays
                       //throw new BoardException("Unsupported scenario-specific rule: " + s + ". LOS disabled");
                    }
                }

                // these are rules that have to be handled in the code
                else if("customCode".equals(rule.getType())) {

                    if("NoStairwells".equals(s)) {

						final Hex[][] hexGrid = LOSData.getHexGrid();
                        for (int x = 0; x < hexGrid.length; x++) {
                            for (int y = 0; y < hexGrid[x].length; y++) {
                                LOSData.getHex(x, y).setStairway(false);
                            }
                        }
                        changed = true;

                    }
                    else if("RowhouseBarsToBuildings".equals(s)) {

                        // for simplicity assume stone building as type will not impact LOS
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall"), LOSData.getTerrain("Stone Building"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 1 Level"), LOSData.getTerrain("Stone Building, 1 Level"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 2 Level"), LOSData.getTerrain("Stone Building, 2 Level"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 3 Level"), LOSData.getTerrain("Stone Building, 3 Level"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 4 Level"), LOSData.getTerrain("Stone Building, 4 Level"), LOSData);
                        changed = true;
                    }
                    else if("RowhouseBarsToOpenGround".equals(s)) {

                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 1 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 2 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 3 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 4 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changed = true;
                    }
                    else if("NoBridge".equals(s) || "BridgeToFord".equals(s)) {

                        bridgesToFord(LOSData);
                        changed = true;
                    }
                    else if("RoadsToPaths".equals(s) || "NoWoodsRoads".equals(s) || "NoWoodsRoad".equals(s)){

                        fillWoodsRoadHexes(LOSData);
                        changed = true;

                    }
                    else if("Bamboo".equals(s)) {

                        // All brush is Bamboo
                        LOSDataEditor losDataEditor = new LOSDataEditor(LOSData);
                        changeGridTerrain(LOSData.getTerrain("Brush"), LOSData.getTerrain("Bamboo"), LOSData);
                        for (int col = 0; col < losDataEditor.getMap().getWidth(); col++) {
                            for (int row = 0; row < losDataEditor.getMap().getHeight() + (col % 2); row++) {

                                Hex hex = LOSData.getHex(col, row);
                                if("Bamboo".equals(hex.getCenterLocation().getTerrain().getName())){
                                    losDataEditor.setGridTerrain(hex.getHexBorder(), LOSData.getTerrain("Bamboo"));
                                }
                            }
                        }
                        changed = true;
                        PTO = true;
                    }
                    else if("PalmTrees".equals(s)) {

                        changeGridTerrain(LOSData.getTerrain("Orchard"), LOSData.getTerrain("Palm Trees"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Orchard, Out of Season"), LOSData.getTerrain("Palm Trees"), LOSData);
                        changed = true;
                        PTO = true;
                    }
                    else if("SwampToSwampPattern".equals(s)) {

                        // Each marsh hex adjacent to ≥ one Jungle hex is a Swamp hex;
                        LOSDataEditor losDataEditor = new LOSDataEditor(LOSData);
                        for (int col = 0; col < losDataEditor.getMap().getWidth(); col++) {
                            for (int row = 0; row < losDataEditor.getMap().getHeight() + (col % 2); row++) {

                                Hex currentHex = LOSData.getHex(col,row);
                                if( "Marsh".equals(currentHex.getCenterLocation().getTerrain().getName())){

                                    boolean apply = false;
                                    for(int x = 0; x < 6; x++) {

                                        Hex adjacentHex = LOSData.getAdjacentHex(currentHex, x);
                                        if(adjacentHex != null &&
                                                ("Woods".equals(adjacentHex.getCenterLocation().getTerrain().getName()) ||
                                                        "Light Jungle".equals(adjacentHex.getCenterLocation().getTerrain().getName()) ||
                                                        "Dense Jungle".equals(adjacentHex.getCenterLocation().getTerrain().getName()))) {

                                            apply = true;
                                        }
                                    }
                                    if(apply) {
                                        losDataEditor.changeAllTerrain(losDataEditor.getMap().getTerrain("Marsh"),
                                                losDataEditor.getMap().getTerrain("Swamp"),
                                                currentHex.getHexBorder());
                                    }
                                }
                            }
                        }
                        changed = true;
                        PTO = true;
                    }
                    else if("DenseJungle".equals(s)) {

                        LOSDataEditor losDataEditor = new LOSDataEditor(LOSData);
                        for (int col = 0; col < losDataEditor.getMap().getWidth(); col++) {
                            for (int row = 0; row < losDataEditor.getMap().getHeight() + (col % 2); row++) {

                                Hex hex = LOSData.getHex(col, row);
                                if("Woods".equals(hex.getCenterLocation().getTerrain().getName())){
                                    losDataEditor.setGridTerrain(hex.getHexBorder(), LOSData.getTerrain("Dense Jungle"));
                                }
                            }
                        }
                        changeGridTerrain(LOSData.getTerrain("Woods"), LOSData.getTerrain("Dense Jungle"), LOSData);
                        changed = true;
                        PTO = true;

                    }
                    else {
                        throw new BoardException("Unsupported custom code SSR: " + s);
                    }
                }
                else if("terrainMap".equals(rule.getType())) {

                    applyTerrainMapRule(rule, LOSData);
                    changed = true;

                }
                else if("elevationMap".equals(rule.getType())) {

                    applyElevationMapRule(rule, LOSData);
                    changed = true;

                }
                else if("terrainToElevationMap".equals(rule.getType())) {

                    applyTerrainToElevationMapRule(rule, LOSData);
                    changed = true;
                }
                else if("elevationToTerrainMap".equals(rule.getType())) {

                    applyElevationToTerrainMapRule(rule, LOSData);
                    changed = true;
                }
                else if("terrainToSelectElevationMap".equals(rule.getType())) {

                    applyTerrainToSelectElevationMapRule(rule, LOSData);
                    changed = true;
                }
            }

            // transform woods to Light Jungle and buildings to huts if PTO changes
            if(PTO) {
                changeGridTerrain(LOSData.getTerrain("Woods"), LOSData.getTerrain("Light Jungle"), LOSData);
                buildingsToHuts(LOSData);
                changed = true;
            }

            // update the hex grid
            if(changed){
                LOSData.resetHexTerrain(gridadj);
            }
        }
    }

    /**
     * Fills the center of woods-road hexes with woods
     */
    private void fillWoodsRoadHexes(Map LOSData) {

        LOSDataEditor losDataEditor = new LOSDataEditor(LOSData);

        // No roads exist (all woods-roads are Paths, with no Open Ground in the woods-road portion of those hexes)
        for (int col = 0; col < losDataEditor.getMap().getWidth(); col++) {
            for (int row = 0; row < losDataEditor.getMap().getHeight() + (col % 2); row++) {

                // Add some woods to center of forest-road hexes to block LOS
                Hex hex = losDataEditor.getMap().getHex(col, row);
                if(isForestRoadHex(hex)) {

                    losDataEditor.setGridTerrain(
                            new Ellipse2D.Double(
                                    hex.getCenterLocation().getLOSPoint().x - LOSData.getHexHeight()/3,
                                    hex.getCenterLocation().getLOSPoint().y - LOSData.getHexHeight()/3,
                                    LOSData.getHexHeight()*2/3,
                                    LOSData.getHexHeight()*2/3),
                            LOSData.getTerrain("Woods"));
                }
            }
        }
    }

    /**
     * Change all bridges to fords by setting roads and bridge terrain to gully
     */
    private void bridgesToFord(Map LOSData) {

        HashSet<Hex> bridgeHexes = new HashSet<Hex>();

        // find bridge hexes and change bridge terrain to open ground
        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                Hex currentHex = LOSData.gridToHex(x, y);

                if(LOSData.getGridTerrain(x,y).getLOSCategory() == Terrain.LOSCategories.BRIDGE) {

                    LOSData.setGridTerrainCode(LOSData.getTerrain("Gully").getType(), x, y);
                    bridgeHexes.add(currentHex);
                }
            }
        }

        // set the center of the bridge hexes to gully to remove road
        LOSDataEditor editor = new LOSDataEditor(LOSData);
        for (Hex h : bridgeHexes) {

            editor.setGridGroundLevel(
                    new Ellipse2D.Double(
                            h.getCenterLocation().getLOSPoint().x - LOSData.getHexHeight()/3,
                            h.getCenterLocation().getLOSPoint().y - LOSData.getHexHeight()/3,
                            LOSData.getHexHeight()*2/3,
                            LOSData.getHexHeight()*2/3),
                    LOSData.getTerrain("Gully"),
                    0);
        }
    }

    /**
     * @param hex the hex
     * @return true if hex is a forest-road hex
     */
    private boolean isForestRoadHex(Hex hex) {

        return hex.getCenterLocation().getTerrain().getLOSCategory() == Terrain.LOSCategories.ROAD &&
               ("Woods".equals(hex.getHexsideLocation(0).getTerrain().getName()) ||
                "Woods".equals(hex.getHexsideLocation(1).getTerrain().getName()) ||
                "Woods".equals(hex.getHexsideLocation(2).getTerrain().getName()) ||
                "Woods".equals(hex.getHexsideLocation(3).getTerrain().getName()) ||
                "Woods".equals(hex.getHexsideLocation(4).getTerrain().getName()) ||
                "Woods".equals(hex.getHexsideLocation(5).getTerrain().getName()));
    }

    /**
     * Apply elevation rule to the LOS data
     * @param rule the elevation map rule
     * @param LOSData the LOS data
     * @throws BoardException
     */
    private static void applyElevationMapRule(LOSSSRule rule, Map LOSData) throws BoardException {

		final int fromElevation;
		final int toElevation;
        try {
            fromElevation = Integer.parseInt(rule.getFromValue());
            toElevation = Integer.parseInt(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to value in SSR elevation map " + rule.getName(), e);
        }
        changeGridElevation(fromElevation, toElevation, LOSData);
    }

    /**
     * Apply terrain rule to the LOS data
     * @param rule the terrain map rule
     * @param LOSData the LOS data
     * @throws BoardException
     */
    private static void applyTerrainMapRule(LOSSSRule rule, Map LOSData) throws BoardException {

        final Terrain fromTerrain;
        final Terrain toTerrain;
        try {
            fromTerrain = LOSData.getTerrain(rule.getFromValue());
            toTerrain = LOSData.getTerrain(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to terrain in SSR terrain map " + rule.getName(), e);
        }
        changeGridTerrain(fromTerrain, toTerrain, LOSData);
    }

    /**
     * Apply elevation to terrain rule to the LOS data
     * @param rule the elevation to terrain map rule
     * @param LOSData the LOS data
     * @throws BoardException
     */
    private static void applyElevationToTerrainMapRule(LOSSSRule rule, Map LOSData) throws BoardException {

        final int fromElevation;
        final Terrain toTerrain;

        try {
            fromElevation = Integer.parseInt(rule.getFromValue());
            toTerrain = LOSData.getTerrain(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to value in SSR elevation to terrain map " + rule.getName(), e);
        }

        // adjust the terrain and elevation
        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                if(LOSData.getGridElevation(x, y) == fromElevation){
                    LOSData.setGridElevation(0, x, y);
                    LOSData.setGridTerrainCode(toTerrain.getType(), x, y);
                }
            }
        }
    }

    /**
     * Apply terrain to elevation rule to the LOS data
     * @param rule the terrain to elevation map rule
     * @param LOSData the LOS data
     * @throws BoardException
     */
    private static void applyTerrainToElevationMapRule(LOSSSRule rule, Map LOSData) throws BoardException {

        final Terrain fromTerrain;
        final int toElevation;
        try {
            fromTerrain = LOSData.getTerrain(rule.getFromValue());
            toElevation = Integer.parseInt(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to value in SSR terrain to elevation map " + rule.getName(), e);
        }

        // adjust the terrain and elevation
        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                if(LOSData.getGridTerrain(x, y).equals(fromTerrain)){
                    LOSData.setGridElevation(toElevation, x, y);
                    LOSData.setGridTerrainCode(LOSData.getTerrain("Open Ground").getType(), x, y);
                }
            }
        }
    }

    /**
     * Apply terrain to select elevation rule to the LOS data; where terrain exists at more than 1 level on board and want to change independently
     * @param rule the terrain to elevation map rule
     * @param LOSData the LOS data
     * @throws BoardException
     */
    private static void applyTerrainToSelectElevationMapRule(LOSSSRule rule, Map LOSData) throws BoardException {

        final Terrain fromTerrain;
        final int toElevation;
        try {
            fromTerrain = LOSData.getTerrain(rule.getFromValue());
            toElevation = Integer.parseInt(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to value in SSR terrain to elevation map " + rule.getName(), e);
        }

        // adjust the terrain and elevation
        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                if(LOSData.getGridTerrain(x, y).equals(fromTerrain) && LOSData.getGridElevation(x, y) == toElevation){
                    //LOSData.setGridElevation(toElevation, x, y);
                    LOSData.setGridTerrainCode(LOSData.getTerrain("Open Ground").getType(), x, y);
                }
            }
        }
    }

    /**
     * Apply RB SSR changes to the LOS data
     * @param s the terrain to be added
     * @param LOSData the LOS data
     * @throws BoardException
     */

    /* this fix allows LOS checking across BSO on the RB/RO boards
       an effort was made to expand this to all BSO on all LOS-enabled boards
       but too many problems/exceptions were encounted. Since the RB code seemed to work, I have left it in. Doug Rimmer September 2018 */
    private void applyRBROrule(String s, Map LOSData) throws BoardException {

        for (Overlay o: super.overlays) {
            //if (o.name.contains(s)){ // the overlay has been applied to the RB map, need to update losdata
                for(int x = o.boundaries.x; x< (o.boundaries.x + o.boundaries.width +1); x++) {
                    for(int y = o.boundaries.y; y < (o.boundaries.y + o.boundaries.height +1); y++ ) {
                        // rb ssr overlays convert stone buildings to gutted; identify type of stone building then convert losdata
                        if(LOSData.getGridTerrain(x, y).equals(LOSData.getTerrain("Stone Factory, 1.5 Level"))){
                            LOSData.setGridTerrainCode(LOSData.getTerrain("Gutted Stone Factory, 1.5 Level").getType(), x, y);
                            LOSData.gridToHex(x, y).getCenterLocation().setTerrain(LOSData.getTerrain("Gutted Stone Factory, 1.5 Level"));
                        } else if (LOSData.getGridTerrain(x, y).equals(LOSData.getTerrain("Stone Factory, 2.5 Level"))){
                            LOSData.setGridTerrainCode(LOSData.getTerrain("Gutted Stone Factory, 2.5 Level").getType(), x, y);
                            LOSData.gridToHex(x, y).getCenterLocation().setTerrain(LOSData.getTerrain("Gutted Stone Factory, 2.5 Level"));
                        } else if (LOSData.getGridTerrain(x, y).equals(LOSData.getTerrain("Stone Building"))){
                            LOSData.setGridTerrainCode(LOSData.getTerrain("Gutted Stone Building").getType(), x, y);
                            LOSData.gridToHex(x, y).getCenterLocation().setTerrain(LOSData.getTerrain("Gutted Stone Building"));
                        } else if (LOSData.getGridTerrain(x, y).equals(LOSData.getTerrain("Stone Building, 1 Level"))){
                            LOSData.setGridTerrainCode(LOSData.getTerrain("Gutted Stone Building, 1 Level").getType(), x, y);
                            LOSData.gridToHex(x, y).getCenterLocation().setTerrain(LOSData.getTerrain("Gutted Stone Building, 1 Level"));
                        } else if (LOSData.getGridTerrain(x, y).equals(LOSData.getTerrain("Stone Building, 2 Level"))){
                            LOSData.setGridTerrainCode(LOSData.getTerrain("Gutted Stone Building, 2 Level").getType(), x, y);
                            LOSData.gridToHex(x, y).getCenterLocation().setTerrain(LOSData.getTerrain("Gutted Stone Building, 2 Level"));
                        } else if (LOSData.getGridTerrain(x, y).equals(LOSData.getTerrain("Stone Building, 3 Level"))){
                            LOSData.setGridTerrainCode(LOSData.getTerrain("Gutted Stone Building, 3 Level").getType(), x, y);
                            LOSData.gridToHex(x, y).getCenterLocation().setTerrain(LOSData.getTerrain("Gutted Stone Building, 3 Level"));
                        } else if (LOSData.getGridTerrain(x, y).equals(LOSData.getTerrain("Stone Building, 4 Level"))) {
                            LOSData.setGridTerrainCode(LOSData.getTerrain("Gutted Stone Building, 4 Level").getType(), x, y);
                            LOSData.gridToHex(x, y).getCenterLocation().setTerrain(LOSData.getTerrain("Gutted Stone Building, 4 Level"));
                        }
                    }
                }
            //}
        }
    }

    /**
     * Converts all single-story houses with multiple buildings to huts
     * @param LOSData the LOS data
     */
    //TODO find a better way
    private void buildingsToHuts(Map LOSData) {

/*
        LOSDataEditor losDataEditor = new LOSDataEditor(LOSData);
        Hex[][] hexGrid = LOSData.getHexGrid();
        for (int x = 0; x < hexGrid.length; x++) {
            for (int y = 0; y < hexGrid[x].length; y++) {

                Hex hex = LOSData.getHex(x, y);
                if(isHut(hex, LOSData)) {

                    System.out.println(hex.getName() + ": true" );
                }
            }
        }
*/
    }

    /**
     * @param hex the hex
     * @return true if hex qualifies for conversion to huts in PTO
     */
    //TODO find a better way
    private boolean isHut(Hex hex, Map LOSData) {

        return false;

/*
        HashSet<Point> buildingPoints  = new HashSet<Point>();

        // collect all building points
        final Rectangle rectangle = hex.getHexBorder().getBounds();
        for(int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
            for(int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {

                if(hex.getHexBorder().contains(x,y) &&
                        LOSData.onMap(x,y) &&
                        ("Wooden Building".equals(LOSData.getGridTerrain(x,y).getName()) ||
                         "Stone Building".equals(LOSData.getGridTerrain(x,y).getName()))) {

                    buildingPoints.add(new Point(x,y));
                }
            }
        }

        // remove adjacent points to count the buildings
        int max = buildingPoints.size();
        for (int x = 0; x < max && buildingPoints.size() > 1; x++) {

            Point p = buildingPoints.iterator().next();
            Iterator<Point> iterator = buildingPoints.iterator();
            boolean adjacent = false;
            while (iterator.hasNext() && !adjacent) {
                Point p2 = iterator.next();
                if((p2.x != p.x || p2.y != p.y) && p2.distance(p) <= 1.5){
                    adjacent = true;
                }
            }
            if(adjacent) {
                buildingPoints.remove(p);
            }
        }

        return buildingPoints.size() > 1;

*/
    }

    /**
     * Changes all terrain in the terrain grid of the LOS data from one type to another
     * IMPORTANT - the hex grid is not updated
     * @param fromTerrain the from terrain
     * @param toTerrain the to terrain
     * @param LOSData the LOS data
     */
    private static void changeGridTerrain(
		Terrain fromTerrain,
		Terrain toTerrain,
		Map LOSData
	){

        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                if(LOSData.getGridTerrain(x, y).equals(fromTerrain)){
                    LOSData.setGridTerrainCode(toTerrain.getType(), x, y);
                }
            }
        }
    }

    /**
     * Maps all elevations in the elevation grid of the LOS data to a new elevation
     * IMPORTANT - the hex grid is not updated
     * @param fromElevation the from elevation
     * @param toElevation the to elevation
     * @param LOSData the LOS data
     */
    private static void changeGridElevation(
		int fromElevation,
		int toElevation,
		Map LOSData
	){

        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                if(LOSData.getGridElevation(x,y) == fromElevation){
                    LOSData.setGridElevation(toElevation, x, y);
                }
            }
        }
    }
}
