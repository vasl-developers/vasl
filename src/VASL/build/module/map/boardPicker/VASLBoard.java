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
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import VASL.LOS.LOSDataEditor;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Map;
import VASL.LOS.Map.Terrain;
import VASL.build.module.ASLMap;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.LOSSSRule;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.i18n.Translatable;

/**
 * Extends ASLBoard to add support for version 6+ boards
 */
public class VASLBoard extends ASLBoard {

    private boolean legacyBoard = true;        // is the board legacy (i.e. V5) format?
    private BoardArchive VASLBoardArchive;

    public VASLBoard(){

        super();
    }

    /**
     * Sets the board archive and all archive variables
     * @param s the image file name - not used
     * @param f the archive file
     */
    //TODO: refactor the board picker so there's a better way to initialize the board and instantiate the archive interface
    @Override
	public void setBaseImageFileName(String s, File f) {

        //TODO: eliminate the need to call the super class method
        super.setBaseImageFileName(s,f);

        // set the board archive
        try {
            VASLBoardArchive = new BoardArchive(f.getName(), f.getParent(), ASLMap.getSharedBoardMetadata());
            legacyBoard = false;

        } catch (IOException e) {

            // must be a legacy board
            legacyBoard = true;
        }
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
     * @return true if this board is legacy format (pre 6.0)
     */
    public boolean isLegacyBoard() {
        return legacyBoard;
    }

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

			final Rectangle bounds = new Rectangle(getCropBounds());
            if(bounds.width == -1) {
                bounds.width = getUncroppedSize().width;
            }
            if(bounds.height == -1) {
                bounds.height = getUncroppedSize().height;
            }
            return losData.crop(new Point(bounds.x, bounds.y), new Point(bounds.x + bounds.width, bounds.y + bounds.height));
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
    public Map getLOSData(HashMap<String, Terrain> terrainTypes){
        return VASLBoardArchive.getLOSData(terrainTypes);
    }

    /**
     * Set the information formerly in the data file
     */
    //TODO: deprecate when pre-6.0 boards are no longer supported
    @Override
    public void readData(){

        if (isLegacyBoard()) {
            super.readData();
        }
        else {

            version = VASLBoardArchive.getVersion();
            if(VASLBoardArchive.getA1CenterX() != BoardArchive.missingValue()){
                ((Translatable)getGrid()).setAttribute(HexGrid.X0, (int) VASLBoardArchive.getA1CenterX());
            }
            if(VASLBoardArchive.getA1CenterY() != BoardArchive.missingValue()){
                ((Translatable)getGrid()).setAttribute(HexGrid.Y0, (int) VASLBoardArchive.getA1CenterY());
            }
            if((int) VASLBoardArchive.getHexWidth() != BoardArchive.missingValue()){
                ((Translatable)getGrid()).setAttribute(HexGrid.DX, VASLBoardArchive.getHexWidth());
            }
            if((int) VASLBoardArchive.getHexHeight() != BoardArchive.missingValue()){
                ((Translatable)getGrid()).setAttribute(HexGrid.DY, VASLBoardArchive.getHexHeight());
            }
        }

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
    public void applyColorSSRules(Map LOSData, HashMap<String, LOSSSRule> losssRules) throws BoardException {

        if(!legacyBoard && !terrainChanges.isEmpty()) {

            boolean changed = false; // changes made?

            // PTO status so we only do this once
            boolean PTO = false;
            boolean PTOWithDenseJungle = false;

            // step through each SSR token
            final StringTokenizer st = new StringTokenizer(terrainChanges, "\t");
            while (st.hasMoreTokens()) {

				final String s = st.nextToken();

				final LOSSSRule rule = losssRules.get(s);
                if(rule == null) {
                    throw new BoardException("Unsupported scenario-specific rule: " + s + ". LOS disabled");
                }

                // these are rules that have to be handled in the code
                if("customCode".equals(rule.getType())) {

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
                    else if("NoBridge".equals(s)) {

                        // OK if board has no bridges otherwise unsupported
						final Hex[][] hexGrid = LOSData.getHexGrid();
                        for (int x = 0; x < hexGrid.length; x++) {
                            for (int y = 0; y < hexGrid[x].length; y++) {
                                if(LOSData.getHex(x, y).hasBridge()){
                                 throw new BoardException("Board " + name + " has a bridge so it does not support NoBridge SSR.");
                                }
                            }
                        }
                    }
                    else if("SwampToSwampPattern".equals(s) ||
                            "PalmTrees".equals(s) ||
                            "DenseJungle".equals(s) ||
                            "Bamboo".equals(s)) {

                        PTO = true;
                        if("DenseJungle".equals(s)) {
                            PTOWithDenseJungle = true;
                        }
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
            }

            // apply PTO changes
            if(PTO) {
                applyPTOTransformations(PTOWithDenseJungle, LOSData);
                changed = true;
            }

            // update the hex grid
            if(changed){
                LOSData.resetHexTerrain();
            }
        }
    }

    /**
     * Apply PTO terrain transformations
     * @param denseJungle Dense Jungle in effect?
     */
    private void applyPTOTransformations(boolean denseJungle, Map LOSData) {

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

        // All woods are Jungle; Dense Jungle is inherent terrain;
        if(denseJungle) {
            for (int col = 0; col < losDataEditor.getMap().getWidth(); col++) {
                for (int row = 0; row < losDataEditor.getMap().getHeight() + (col % 2); row++) {

                    Hex hex = LOSData.getHex(col, row);
                    if("Woods".equals(hex.getCenterLocation().getTerrain().getName())){
                        losDataEditor.setGridTerrain(hex.getHexBorder(), LOSData.getTerrain("Dense Jungle"));
                    }
                }
            }
            changeGridTerrain(LOSData.getTerrain("Woods"), LOSData.getTerrain("Dense Jungle"), LOSData);
        }
        else {
            changeGridTerrain(LOSData.getTerrain("Woods"), LOSData.getTerrain("Light Jungle"), LOSData);
        }

        // All brush is Bamboo
        changeGridTerrain(LOSData.getTerrain("Brush"), LOSData.getTerrain("Bamboo"), LOSData);
        for (int col = 0; col < losDataEditor.getMap().getWidth(); col++) {
            for (int row = 0; row < losDataEditor.getMap().getHeight() + (col % 2); row++) {

                Hex hex = LOSData.getHex(col, row);
                if("Bamboo".equals(hex.getCenterLocation().getTerrain().getName())){
                    losDataEditor.setGridTerrain(hex.getHexBorder(), LOSData.getTerrain("Bamboo"));
                }
            }
        }

        // All orchards are Palm Trees
        changeGridTerrain(LOSData.getTerrain("Orchard"), LOSData.getTerrain("Palm Trees"), LOSData);
        changeGridTerrain(LOSData.getTerrain("Orchard, Out of Season"), LOSData.getTerrain("Palm Trees"), LOSData);

        // All grain is Kunai
        changeGridTerrain(LOSData.getTerrain("Grain"), LOSData.getTerrain("Kunai"), LOSData);

        // Each marsh hex adjacent to ≥ one Jungle hex is a Swamp hex;
        for (int col = 0; col < losDataEditor.getMap().getWidth(); col++) {
            for (int row = 0; row < losDataEditor.getMap().getHeight() + (col % 2); row++) {

                Hex currentHex = LOSData.getHex(col,row);
                for(int x = 0; x < 6; x++) {

                    Hex adjacentHex = LOSData.getAdjacentHex(currentHex, x);
                    if(adjacentHex != null && "Marsh".equals(adjacentHex.getCenterLocation().getTerrain().getName())) {
                        losDataEditor.setGridTerrain(adjacentHex.getHexBorder(), LOSData.getTerrain("Swamp"));
                    }
                }
            }
        }

        // All bridges are Fords
        bridgesToFord(LOSData);
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
