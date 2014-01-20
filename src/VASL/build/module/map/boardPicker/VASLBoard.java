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

import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Terrain;
import VASL.build.module.ASLMap;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.LOSSSRule;
import VASSAL.build.module.map.boardPicker.board.HexGrid;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Extends ASLBoard to add support for version 6+ boards
 */
public class VASLBoard extends ASLBoard {

    private boolean legacyBoard = true; // is the board legacy (i.e. V5) format?
    private BoardArchive VASLBoardArchive;
    private boolean flipped = false; // prevents the same board from being flipped multiple times

    /**
     * @return true if the board is already set
     */
    public boolean isFlipped() {
        return flipped;
    }

    /**
     * Mark board as having been flipped. This is a hack to prevent boards from being flipped multiple time
     * @param flipped
     */
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }

    public VASLBoard(){

        super();
    }

    /**
     * Sets the board archive and all archive variables
     * @param s the image file name - not used
     * @param f the archive file
     */
    //TODO: refactor the board picker so there's a better way to initialize the board and instantiate the archive interface
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
     * @return true if this board is legacy format (pre 6.0)
     */
    public boolean isLegacyBoard() {
        return legacyBoard;
    }

    /**
     * Is the board cropped?
     */
    public boolean isCropped() {
        Rectangle croppedBounds = getCropBounds();
        return !(croppedBounds.x == 0 && croppedBounds.y == 0 && croppedBounds.width == -1 && croppedBounds.height == -1);
    }

    /**
     * Crops the LOS data
     * @param losData
     */
    public VASL.LOS.Map.Map cropLOSData(VASL.LOS.Map.Map losData) {
        if(!isCropped()) {
            return null;
        }
        else {

            Rectangle bounds = new Rectangle(getCropBounds());
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
        Rectangle rectangle = new Rectangle(this.boundaries);
        rectangle.translate(-1 * map.getEdgeBuffer().width, -1 * map.getEdgeBuffer().height);
        return rectangle;
    }

    /**
     * @return the LOS data
     * @param terrainTypes the terrain types
     */
    public VASL.LOS.Map.Map getLOSData(HashMap<String, Terrain> terrainTypes){
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
            if(VASLBoardArchive.getA1CenterX() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.X0, VASLBoardArchive.getA1CenterX());
            }
            if(VASLBoardArchive.getA1CenterY() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.Y0, VASLBoardArchive.getA1CenterY());
            }
            if(VASLBoardArchive.getHexWidth() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.DX, VASLBoardArchive.getHexWidth());
            }
            if(VASLBoardArchive.getHexHeight() != VASLBoardArchive.missingValue()){
                ((HexGrid) getGrid()).setAttribute(HexGrid.DY, VASLBoardArchive.getHexHeight());
            }
        }

    }

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
    public void applyColorSSRules(VASL.LOS.Map.Map LOSData, HashMap<String, LOSSSRule> losssRules) throws BoardException {

        if(!this.legacyBoard && terrainChanges.length() > 0) {

            System.out.println(terrainChanges);

            boolean changed = false; // changes made?

            // step through each SSR token
            StringTokenizer st = new StringTokenizer(terrainChanges, "\t");
            while (st.hasMoreTokens()) {

                String s = st.nextToken();

                LOSSSRule rule = losssRules.get(s);
                if(rule == null) {
                    throw new BoardException("Unsupported scenario-specific rule: " + s);
                }

                // these are rules that have to be handled in the code
                if(rule.getType().equals("customCode")) {

                    if(s.equals("NoStairwells")) {

                        Hex[][] hexGrid = LOSData.getHexGrid();
                        for (int x = 0; x < hexGrid.length; x++) {
                            for (int y = 0; y < hexGrid[x].length; y++) {
                                LOSData.getHex(x, y).setStairway(false);
                            }
                        }
                        changed = true;

                    }
                    else if(s.equals("RowhouseBarsToBuildings")) {

                        // for simplicity assume stone building as type will not impact LOD
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall"), LOSData.getTerrain("Stone Building"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 1 Level"), LOSData.getTerrain("Stone Building, 1 Level"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 2 Level"), LOSData.getTerrain("Stone Building, 2 Level"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 3 Level"), LOSData.getTerrain("Stone Building, 3 Level"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 4 Level"), LOSData.getTerrain("Stone Building, 4 Level"), LOSData);
                        changed = true;
                    }
                    else if(s.equals("RowhouseBarsToOpenGround")) {

                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 1 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 2 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 3 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changeGridTerrain(LOSData.getTerrain("Rowhouse Wall, 4 Level"), LOSData.getTerrain("Open Ground"), LOSData);
                        changed = true;
                    }
                    else if(s.equals("FloodedRivers")) {

                    }
                    else if(s.equals("FordableRivers")) {

                    }
                    else if(s.equals("Winter")) {

                    }
                    else if(s.equals("Mud")) {

                    }
                    else if(s.equals("NoHills")) {

                    }
                    else {
                        throw new BoardException("Unsupported custom code SSR: " + s);
                    }
                }
                else if(rule.getType().equals("terrainMap")) {

                    applyTerrainMapRule(rule, LOSData);
                    changed = true;

                }
                else if(rule.getType().equals("elevationMap")) {

                    applyElevationMapRule(rule, LOSData);
                    changed = true;

                }
                else if(rule.getType().equals("terrainToElevationMap")) {

                    applyTerrainToElevationMapRule(rule, LOSData);
                    changed = true;
                }
                else if(rule.getType().equals("elevationToTerrainMap")) {

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
    private void applyElevationMapRule(LOSSSRule rule, VASL.LOS.Map.Map LOSData) throws BoardException {

        int fromElevation;
        int toElevation;
        try {
            fromElevation = Integer.parseInt(rule.getFromValue());
            toElevation = Integer.parseInt(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to value in SSR elevation map " + rule.getName());
        }
        changeGridElevation(fromElevation, toElevation, LOSData);
    }

    /**
     * Apply terrain rule to the LOS data
     * @param rule the terrain map rule
     * @param LOSData the LOS data
     * @throws BoardException
     */
    private void applyTerrainMapRule(LOSSSRule rule, VASL.LOS.Map.Map LOSData) throws BoardException {

        Terrain fromTerrain;
        Terrain toTerrain;
        try {
            fromTerrain = LOSData.getTerrain(rule.getFromValue());
            toTerrain = LOSData.getTerrain(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to terrain in SSR terrain map " + rule.getName());
        }
        changeGridTerrain(fromTerrain, toTerrain, LOSData);
    }

    /**
     * Apply elevation to terrain rule to the LOS data
     * @param rule the elevation to terrain map rule
     * @param LOSData the LOS data
     * @throws BoardException
     */
    private void applyElevationToTerrainMapRule(LOSSSRule rule, VASL.LOS.Map.Map LOSData) throws BoardException {

        int fromElevation;
        Terrain toTerrain;

        try {
            fromElevation = Integer.parseInt(rule.getFromValue());
            toTerrain = LOSData.getTerrain(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to value in SSR elevation to terrain map " + rule.getName());
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
    private void applyTerrainToElevationMapRule(LOSSSRule rule, VASL.LOS.Map.Map LOSData) throws BoardException {

        Terrain fromTerrain;
        int toElevation;
        try {
            fromTerrain = LOSData.getTerrain(rule.getFromValue());
            toElevation = Integer.parseInt(rule.getToValue());
        }
        catch (Exception e) {
            throw new BoardException("Invalid from or to value in SSR terrain to elevation map " + rule.getName());
        }

        // adjust the terrain and elevation
        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                if(LOSData.getGridTerrain(x, y) == fromTerrain){
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
    private void changeGridTerrain(
            VASL.LOS.Map.Terrain fromTerrain,
            VASL.LOS.Map.Terrain toTerrain,
            VASL.LOS.Map.Map LOSData
    ){

        for(int x = 0; x < LOSData.getGridWidth(); x++) {
            for(int y = 0; y < LOSData.getGridHeight(); y++ ) {

                if(LOSData.getGridTerrain(x, y) == fromTerrain){
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
    private void changeGridElevation(
            int fromElevation,
            int toElevation,
            VASL.LOS.Map.Map LOSData
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
