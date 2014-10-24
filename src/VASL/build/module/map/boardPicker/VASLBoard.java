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
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.StringTokenizer;

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

// 	public Map(int width, int height, double A1CenterX, double A1CenterY, int imageWidth, int imageHeight, HashMap<String, Terrain> terrainNameMap){

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

                        // for simplicity assume stone building as type will not impact LOD
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

            // update the hex grid
            if(changed){
                LOSData.resetHexTerrain();
            }
        }
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
