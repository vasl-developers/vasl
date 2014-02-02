/*
 * Copyright (c) 2000-2013 by David Sullivan
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
package VASL.LOS.Map;

import java.awt.*;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;
/**
 * The <code>Map</code> class is the map API.
 * The map uses two data structures to represent the logical map: a terrain grid and a hex
 * grid.
 * <p>
 * The terrain grid contains a physical representation of the image terrain where one point
 * in the grid corresponds to one pixel in the map image. The upper-left pixel is (0,0).
 * <p>
 * The hex grid contains the information that is specific to each hex and uses the
 * following coordinate system: the upper-left most hex (A1) is (0,0), A2 would be (0,1),
 * B0 would be (1,0), and so on. Note that the number of hexes in each column will
 * depend upon whether the column is odd or even.
 */
public class Map
	implements Serializable {

    // width and height of the map in hexes
	protected int		 width;
	protected int		 height;

    // width and height of the terrain grid
	protected int		 gridWidth;
	protected int		 gridHeight;

    // map information
	protected char		 terrainGrid[][];  		// terrain for each pixel
	protected byte		 elevationGrid[][];  	// height for each pixel
	protected Hex		 hexGrid[][];			// hex array

    // terrain type codes
	protected Terrain terrainList[] = new Terrain[256];

    // map terrain names to the terrain objects
    private static HashMap<String, Terrain> terrainNameMap;

    // smoke list
    protected HashSet smokeList	= new HashSet(20);

    // LOS error messages
    protected static String LOS_err_A6_3_1 = "Exits depression before range/elevation restictions are satisfied (A6.3)";
    protected static String LOS_err_A6_3_2 = "Does not enter depression while range/elevation restictions are satisfied (A6.3)";
    protected static String LOS_err_A6_8 = "LOS must leave the building before leaving the source hex to see a location with a different elevation (A6.8 Example 2)";
    protected static String LOS_err_B23_71 = "Cannot see through rowhouse wall (B23.71)";
    protected static String LOS_err_B27_2_1 = "Unit in entrenchment cannot see over hexside terrain to non-adjacent lower target (B27.2)";
    protected static String LOS_err_B27_2_2 = "Cannot see non-adjacent unit in higher elevation entrenchment over hexside terrain (B27.2)";
    protected static String LOS_err_B9_52_1 = "Cannot see through/over bocage (B9.52)";
    protected static String LOS_err_B9_52_2 = "Source or Target location is in a blind hex (B9.52)";
    protected static String LOS_err_B9_2 = "Intervening hexside terrain (B9.2)";
    protected static String LOS_err_A6_2_1 = "Ground level is higher than both the source and target (A6.2)";
    protected static String LOS_err_A6_2_2 = "Half level terrain is higher than both the source and target (A6.2)";
    protected static String LOS_err_A6_2_3 = "Terrain is higher than both the source and target (A6.2)";
    protected static String LOS_err_A6_2_4 = "Must have a height advantage to see over this terrain (A6.2)";
    protected static String LOS_err_A6_4_1 = "Source or Target location is in a blind hex (A6.4)";
    protected static String LOS_err_B10_23 = "Source or Target location is in a blind hex (B10.23)";


    /**
     * Constructs a new <code>Map</code> object. A standard geomorphic map board
     * is 10 x 33 hexes.
     * @param w the width of the map in hexes
     * @param h the height of the map in hexes
     */
    public Map(int w, int h, HashMap<String, Terrain> terrainNameMap) {

        width = w;
        height = h;
        gridWidth = (int) ((width - 1) * Hex.WIDTH);
        gridHeight = (int) (height * Hex.HEIGHT);

        // get the terrain
        setTerrain(terrainNameMap);

        // create the grids
        // default values are zero, so level zero and open ground
        terrainGrid = new char[gridWidth][gridHeight];
        elevationGrid = new byte[gridWidth][gridHeight];

        // create the hex grid
        hexGrid = new Hex[width][];
        for (int col = 0; col < width; col++) {

            hexGrid[col] = new Hex[height + (col % 2)]; // add 1 if odd
            for (int row = 0; row < height + (col % 2); row++) {
                hexGrid[col][row] = new Hex(col, row, this, 0, terrainList[0]);
            }
        }

        // create the hex locations
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height + (col % 2); row++) {
                hexGrid[col][row].resetHexsideLocationNames();
            }
        }
    }

    /**
     * Returns the <code>Terrain</code> type code for the pixel at row, col of
     * the map image.
     * @param row the row coordinate of the map image pixel
     * @param col the col coordinate of the map image pixel
     * @return  the terrain type code at (row, col)
     */
    public int getGridTerrainCode(int row, int col) {

        return (int) terrainGrid[row][col];
    }

    /**
     * Returns the <code>Terrain</code> for the pixel at row, col of
     * the map image.
     * @param row the row coordinate of the map image pixel
     * @param col the col coordinate of the map image pixel
     * @return <code>Terrain</code> the terrain type at (row, col) if on board, otherwise null
     */
    public Terrain getGridTerrain(int row, int col) {

        return terrainList[(int) terrainGrid[row][col]];
    }

    /**
     * Sets the terrain code for the pixel at row, col of the map image.
     * @param terrainCode the <code>Terrain </code> code
     * @param row the row coordinate of the map image pixel
     * @param col the col coordinate of the map image pixel
     */
    public void setGridTerrainCode(int terrainCode, int row, int col) {

        terrainGrid[row][col] = (char) terrainCode;
    }

    /**
     * Returns the ground level for the pixel at row, col of
     * the map image.
     * @param row the row coordinate of the map image pixel
     * @param col the col coordinate of the map image pixel
     * @return the ground level at (row, col)
     */
    public int getGridElevation(int row, int col) {

        // ground level
        return (int) elevationGrid[row][col];
    }

    /**
     * Set the elevation of the pixel at row, col
     * @param elevation the new elevation
     * @param row the row coordinate of the map image pixel
     * @param col the col coordinate of the map image pixel
     */
    public void setGridElevation(int elevation, int row, int col) {

        elevationGrid[row][col] = (byte) elevation;

    }

    /**
     * Sets the hex grid to conform with the terrain and elevation grids
     */
    //TODO: should this be here?
    public void resetHexTerrain(){

        // step through each hex and reset the terrain.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height + (x % 2); y++) { // add 1 hex if odd
                getHex(x,y).resetTerrain();
            }
        }
    }

    /**
     * List of all smoke on the map
     * @return smoke list
     */
    public HashSet getSmokeList() {
        return smokeList;
    }


    /**
     * Returns the width of the map in hexes.
     * @return the map width in hexes
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of the map in hexes for the even hex columns.
     * @return the map height in hexes
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the width of the map grid.
     * @return the width of the map grid
     */
    public int getGridWidth() {
        return gridWidth;
    }

    /**
     * Returns the height of the map grid.
     * @return the height of the map grid.
     */
    public int getGridHeight() {
        return gridHeight;
    }

    /**
     * Return the <code>Terrain</code> type for the given terrain code.
     * @param t terrain code defined in the <code>Terrain</code> class
     * @return <code>Terrain</code> type.
     */
    public Terrain getTerrain(int t) {

        return terrainList[t];
    }

    /**
     * Return the <code>Terrain</code> type for the given terrain name.
     * @param terrainName the terrain name
     * @return <code>Terrain</code> type.
     */
    public Terrain getTerrain(String terrainName) {

        return terrainNameMap.get(terrainName);
    }

    /**
     * Get the <code>Hex</code> at hex grid location (col, row)
     * @param col hex column ('A' is 0)
     * @param row offset of hex in column (first hex in column is 0)
     * @return the <code>Hex</code> at (col, row)
     */
    public Hex getHex(int col, int row) {

        return hexGrid[col][row];
    }

    /**
     * Get a <code>Hex</code> by name
     * @param name hex name
     * @return the <code>Hex</code> at (col, row)
     */
    public Hex getHex(String name) {

        Hex h;
        for (int col = 0; col < hexGrid.length; col++) {
            for (int row = 0; row < hexGrid[col].length; row++) {

                h = getHex(col, row);

                if (h.getName().equalsIgnoreCase(name)) {

                    return h;
                }
            }
        }

        return null;
    }

    /**
     * Get the hex grid. Needed when iterating through all hexes
     */
    public Hex[][] getHexGrid(){

        return hexGrid;
    }

    /**
     * Determines if the hex at (col, row) in the hex grid is on the map
     * @param col hex column ('A' is 0)
     * @param row offset of hex in column (first hex in column is 0)
     * @return true if on map, otherwise false
     */
    public boolean hexOnMap(int col, int row) {

        try {
            @SuppressWarnings("unused")
            Hex temp = hexGrid[col][row];
            return true;
        }
        catch (Exception e) {

            return false;
        }
    }

    /**
     * Find an adjacent hex.
     * @param h starting hex
     * @param hexside hexside shared with the adjacent hex. 0-5 where 0 is top of hex, continuing clockwise through 5
     * @return adjacent hex, null if hex is not on the map
     */
    public Hex getAdjacentHex(Hex h, int hexside) {

        int col = h.getColumnNumber();
        int row = h.getRowNumber();
        boolean colIsEven = (col % 2 == 0);

        switch (hexside) {
            case 0:
                row -= 1;
                break;
            case 1:
                col += 1;
                row += colIsEven ? 0 : -1;
                break;
            case 2:
                col += 1;
                row += colIsEven ? 1 : 0;
                break;
            case 3:
                row += 1;
                break;
            case 4:
                col -= 1;
                row += colIsEven ? 1 : 0;
                break;
            case 5:
                col -= 1;
                row += colIsEven ? 0 : -1;
                break;
        }

        if (hexOnMap(col, row)) {
            return getHex(col, row);
        }
        else {
            return null;
        }
    }

    /**
     * Finds the hex that contains an image pixel.
     * @param x the x coordinate of the map image pixel
     * @param y the y coordinate of the map image pixel
     * @return the Hex containing the pixel
     */
    public Hex gridToHex(int x, int y) {

        // enure the point is on the map
        x = Math.max(x, 0);
        x = Math.min(x, gridWidth - 1);
        y = Math.max(y, 0);
        y = Math.min(y, gridHeight - 1);

        try {

            int z = (int) (x / (Hex.WIDTH / 3));
            int row;
            int col;

            // in "grey area" between columns?
            if ((z - 1) % 3 == 0) {

                col = (int) Math.ceil(((double) z - 1) / 3);
                row = (int) ((col % 2 == 0) ? y / Hex.HEIGHT : (y + Hex.HEIGHT / 2) / Hex.HEIGHT);

                if (hexGrid[col][row].contains(x, y)) {

                    return hexGrid[col][row];
                }
                else if (col % 2 == 0) {

                    if (hexGrid[col + 1][row + 1].contains(x, y)) {

                        return hexGrid[col + 1][row + 1];
                    }
                    else {
                        return hexGrid[col + 1][row];
                    }
                }
                else {
                    if ((row - 1 >= 0 && hexGrid[col + 1][row - 1].contains(x, y)) ||
                            (row == height)) {

                        return hexGrid[col + 1][row - 1];
                    }
                    else {
                        return hexGrid[col + 1][row];
                    }
                }
            }
            else {

                col = (int) Math.ceil((double) z / 3);
                row = (int) ((col % 2 == 0) ? y / Hex.HEIGHT : (y + Hex.HEIGHT / 2) / Hex.HEIGHT);
                return hexGrid[col][row];
            }
        }
        catch (Exception e) {

            System.err.println("gridToHex error at X: " + x + " Y: " + y);
            return null;
        }
    }

    /**
     * Returns the range between two hexes.
     * @param source source hex
     * @param target "target" hex
     * @return the range
     */
    public int range(Hex source, Hex target) {

        int dirX = target.getColumnNumber() > source.getColumnNumber() ? 1 : -1;
        int dirY = target.getRowNumber() > source.getRowNumber() ? 1 : -1;

        int rng = 0;

        int currentRow = source.getRowNumber();
        int currentCol = source.getColumnNumber();

        // step through each row, adjusting the current row as necessary
        while (currentCol != target.getColumnNumber()) {

            // adjust the row as we step through the columns
            if ((currentRow != target.getRowNumber()) &&
                    ((currentCol % 2 == 0 && dirY == 1) ||
                            (currentCol % 2 == 1 && dirY == -1))) {

                currentRow += dirY;
            }

            currentCol += dirX;
            rng += 1;
        }

        // we're in the target col: if not in target hex, compute distance
        if (currentRow != target.getRowNumber()) {

            rng += Math.abs(target.getRowNumber() - currentRow);
        }

        return rng;
    }

    /**
     * Determines if a line-of-sight exists between two locations. The auxiliary LOS points are used for
     * bypass locations. Standard LOS is drawn to the counterclockwise-most hexspine on the bypassed hexside. The
     * auxillary LOS point is the clockwise-most hexspine on the bypassed hexside.
     * @param source source hex
     * @param useAuxSourceLOSPoint use auxiliary bypass aiming point for source location
     * @param target "target" hex
     * @param useAuxTargetLOSPoint use auxiliary bypass aiming point for target location
     * @param result <code>LOSResult</code> that will contain all of the LOS information
     * @param scenario <code>Scenario</code> that contains all scenario-dependent LOS information
     */
    public void LOS(Location source, boolean useAuxSourceLOSPoint, Location target, boolean useAuxTargetLOSPoint, LOSResult result, Scenario scenario) {

        // reset the results
        result.setClear();
        result.setSourceLocation(source);
        result.setTargetLocation(target);
        result.setUseAuxSourceLOSPoint(useAuxSourceLOSPoint);
        result.setUseAuxTargetLOSPoint(useAuxTargetLOSPoint);

        // add the first hex
        result.addHex(source.getHex());

        // in the same location?
        if (source == target) {

            result.setRange(0);
            return;
        }

        // check for smoke in source hex here
        //TODO: move smoke to hex
        HashSet hexSmoke = getAllSmoke(source.getHex());
        if (hexSmoke != null && hexSmoke.size() > 0) {

            Smoke s = null;

            // step through all smoke counters in the source hex
            Iterator iter = hexSmoke.iterator();
            while (iter.hasNext()) {

                s = (Smoke) iter.next();

                if ((source.getAbsoluteHeight() >= s.getLocation().getAbsoluteHeight() &&
                        source.getAbsoluteHeight() < s.getLocation().getAbsoluteHeight() + s.getHeight()) ||
                        // shooting down through smoke
                        (source.getAbsoluteHeight() == s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                                target.getAbsoluteHeight() < source.getAbsoluteHeight()) ||
                        // shooting down through smoke in the same hex
                        (source.getHex() == target.getHex() &&
                                source.getAbsoluteHeight() >= s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                                target.getAbsoluteHeight() < source.getAbsoluteHeight() + s.getHeight()) ||
                        // source below and target above
                        (source.getAbsoluteHeight() < s.getLocation().getAbsoluteHeight() &&
                                target.getAbsoluteHeight() > s.getLocation().getAbsoluteHeight() + s.getHeight())
                        ) {

                    // add hindrance
                    result.addSmokeHindrance(s, (int) source.getLOSPoint().getX(), (int) source.getLOSPoint().getY());
                    if (result.isBlocked()) return;
                }
            }
        }

        // in same hex?
        if (source.getHex() == target.getHex()) {

            // Set the range
            result.setRange(0);

            // either unit in the hex center?
            if (source.isCenterLocation() ||
                    target.isCenterLocation()) {

                // if both locations are building, ensure difference in levels is <= 1 and stairway
                if (source.getTerrain().isBuildingTerrain() && target.getTerrain().isBuildingTerrain()) {
                    if (Math.abs(source.getBaseHeight() - target.getBaseHeight()) > 1 ||
                            !source.getHex().hasStairway()) {

                        result.setBlocked(
                                (int) source.getLOSPoint().getX(),
                                (int) source.getLOSPoint().getY(),
                                "Crosses building level or no stairway");
                        return;
                    }
                }

                // source on a bridge and target under bridge, etc?
                if ((source.getTerrain().isBridge() && target.isCenterLocation()) ||
                        (target.getTerrain().isBridge() && source.isCenterLocation())) {

                    result.setBlocked(
                            (int) source.getLOSPoint().getX(),
                            (int) source.getLOSPoint().getY(),
                            "Cannot see location under the bridge");
                    return;
                }

                // Out of bunker cover arc?

                // otherwise clear
                return;
            }
        }

        // Otherwise, standard LOS check
        Pt2PtLOS(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, scenario);
    }

    // point to point LOS
    protected void Pt2PtLOS(Location source, boolean useAuxSourceLOSPoint, Location target, boolean useAuxTargetLOSPoint, LOSResult result, Scenario scenario) {

        // location variables
        int sourceX = useAuxSourceLOSPoint ? (int) source.getAuxLOSPoint().getX() : (int) source.getLOSPoint().getX();
        int sourceY = useAuxSourceLOSPoint ? (int) source.getAuxLOSPoint().getY() : (int) source.getLOSPoint().getY();
        int targetX = useAuxTargetLOSPoint ? (int) target.getAuxLOSPoint().getX() : (int) target.getLOSPoint().getX();
        int targetY = useAuxTargetLOSPoint ? (int) target.getAuxLOSPoint().getY() : (int) target.getLOSPoint().getY();

        // direction variables
        int colDir = targetX - sourceX < 0 ? -1 : 1;
        int rowDir = targetY - sourceY < 0 ? -1 : 1;
        int numCols = Math.abs(targetX - sourceX) + 1;
        double deltaY;

        // hindrance, etc. variables
        boolean blocked = false;
        String reason = "";

        Terrain currentTerrain = null;
        int currentTerrainHgt = 0;
        int groundLevel = -9999;

        // hex data variables
        Hex sourceHex = source.getHex();
        Hex targetHex = target.getHex();
        Hex currentHex = sourceHex;
        Hex vehicleHex = sourceHex;
        Hex hindranceHex = sourceHex;
        Hex tempHex = null;
        int sourceElevation = sourceHex.getBaseHeight() + source.getBaseHeight();
        int targetElevation = targetHex.getBaseHeight() + target.getBaseHeight();
        int range = range(sourceHex, targetHex);
        int rangeToSource = 0;
        int rangeToTarget = range;

        // bridge stuff
        Shape bridgeArea = null;
        Shape bridgeRoadArea = null;
        Bridge bridge = null;

        boolean continuousSlope = true;
        boolean LOSLeavesBuilding = !sourceHex.getTerrain().isBuildingTerrain();

        // "rise" per grid column
        deltaY = ((double) targetY - (double) sourceY) / (double) numCols;

        // Exiting depression restriction placed when looking out of a depression
        // to a higher elevation where the "elevation difference <= range" restriction has not
        // been satisfied. Must be satisfied before leaving the depression.
        boolean exitsSourceDepression =
                source.isDepressionTerrain() &&
                        (targetElevation < sourceElevation ||
                                (targetElevation - sourceElevation > 0 &&
                                        targetElevation - sourceElevation < range) ||
                                (source.isDepressionTerrain() &&
                                        target.isDepressionTerrain() &&
                                        targetElevation == sourceElevation));
        Hex ignoreGroundLevelHex = null;

        // Entering depression restriction placed when looking into a depression
        // reverse of above
        boolean entersTargetDepression =
                target.isDepressionTerrain() &&
                        sourceElevation - targetElevation > 0 &&
                        sourceElevation - targetElevation < range;

        // grid column entry/exit points
        double enter = sourceY;
        double exit = enter + deltaY;

        // initialize some result variables
        result.setRange(rangeToTarget);
        result.setSourceExitHexside(LOSResult.UNKNOWN);
        result.setTargetEnterHexside(LOSResult.UNKNOWN);

        // LOS slope variables
        boolean LOSisHorizontal = (sourceY == targetY);
        double doubleSourceX = useAuxSourceLOSPoint ? source.getAuxLOSPoint().getX() : source.getLOSPoint().getX();
        double doubleSourceY = useAuxSourceLOSPoint ? source.getAuxLOSPoint().getY() : source.getLOSPoint().getY();
        double doubleTargetX = useAuxTargetLOSPoint ? target.getAuxLOSPoint().getX() : target.getLOSPoint().getX();
        double doubleTargetY = useAuxTargetLOSPoint ? target.getAuxLOSPoint().getY() : target.getLOSPoint().getY();
        double slope = Math.abs((doubleSourceY - doubleTargetY) / (doubleSourceX - doubleTargetX));

        // set the tolerance to compensate for "fuzzy" geometry of VASL boards
        double tolerance = 0.05;
        if (range == 3 || range == 4) {

            tolerance = 0.028;
        }
        else if (5 <= range && range <= 10) {

            tolerance = 0.02;
        }
        else if (range > 10) {

            tolerance = 0.015;
        }

        boolean LOSis60Degree = Math.abs(slope - Math.tan(Math.toRadians(60))) < tolerance;

        // set the result with the slope information
        result.setLOSis60Degree(LOSis60Degree);
        if (LOSis60Degree) {
            if (colDir == 1) {
                if (rowDir == 1) {
                    result.setSourceExitHexspine(3);
                    result.setTargetEnterHexspine(0);
                }
                else {
                    result.setSourceExitHexspine(1);
                    result.setTargetEnterHexspine(4);
                }
            }
            else {
                if (rowDir == 1) {
                    result.setSourceExitHexspine(4);
                    result.setTargetEnterHexspine(1);
                }
                else {
                    result.setSourceExitHexspine(0);
                    result.setTargetEnterHexspine(3);
                }
            }
        }
        else if (slope == 0.0) {
            if (colDir == 1) {
                result.setSourceExitHexspine(2);
                result.setTargetEnterHexspine(5);
            }
            else {
                result.setSourceExitHexspine(5);
                result.setTargetEnterHexspine(2);
            }
        }

        // step through each pixel column
        int currentCol = sourceX;
        for (int col = 0; col < numCols; col++) {

            // set row variables
            int currentRow = (int) enter;
            int numRows = Math.abs((int) exit - (int) enter) + 1;

            // step through each pixel in the current row
            for (int row = 0; row < numRows; row++) {

                // adjust our variables for the new spot
                try {
                    currentTerrain = getGridTerrain(currentCol, currentRow);
                }
                catch (Exception e) {
                    System.err.println("LOS is off the map at " + currentCol + ", " + currentRow);
                    if (useAuxTargetLOSPoint) {
                        System.err.println("Target is " + target.getName() + " at " + target.getLOSPoint().getX() + ", " + target.getLOSPoint().getY());
                    }
                    else {
                        System.err.println("Target is " + target.getName() + " at " + target.getAuxLOSPoint().getX() + ", " + target.getAuxLOSPoint().getY());
                    }
                    return;
                }

                // set the temp hex
                if (sourceHex.getExtendedHexBorder().contains(currentCol, currentRow)) {

                    tempHex = sourceHex;
                }
                else if (targetHex.getExtendedHexBorder().contains(currentCol, currentRow)) {

                    tempHex = targetHex;
                }
                else {

                    tempHex = gridToHex(currentCol, currentRow);
                }

                // set the other "temp hexes"
                vehicleHex = tempHex;
                hindranceHex = tempHex;

                // need to do some 'tweaking' to properly handle inherent terrain hindrances, vehicles, and smoke
                // 1) skip inherent terrain that 'spills' into adjacent hex
                // 2) LOS along a hexside misses inherent terrain in adjacent hex
                if (LOSis60Degree || LOSisHorizontal) {

                    if (LOSisHorizontal) {

                        if (currentTerrain.isInherentTerrain() && tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {

                        }
                        else if (currentRow != 0 &&
                                gridToHex(currentCol, currentRow - 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                            hindranceHex = gridToHex(currentCol, currentRow - 1);
                            currentTerrain = terrainList[hindranceHex.getCenterLocation().getTerrain().getType()];
                        }
                        else if (currentRow != gridHeight &&
                                gridToHex(currentCol, currentRow + 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                            hindranceHex = gridToHex(currentCol, currentRow + 1);
                            currentTerrain = terrainList[hindranceHex.getCenterLocation().getTerrain().getType()];
                        }
                        else if (currentTerrain.isInherentTerrain() && !tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {
                            if (tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {

                                currentTerrain = terrainList[tempHex.getCenterLocation().getTerrain().getType()];
                            }
                            else {
                                currentTerrain = terrainList[terrainNameMap.get("Open Ground").getType()];
                            }
                        }
                    }
                    else if (LOSis60Degree) {

                        if (currentTerrain.isInherentTerrain() && tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {

                        }
                        else if (currentCol != 0 && currentRow != 0 &&
                                gridToHex(currentCol - 1, currentRow - 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                            hindranceHex = gridToHex(currentCol - 1, currentRow - 1);
                            currentTerrain = terrainList[hindranceHex.getCenterLocation().getTerrain().getType()];
                        }
                        else if (currentCol != gridWidth && currentRow != gridHeight &&
                                gridToHex(currentCol + 1, currentRow + 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                            hindranceHex = gridToHex(currentCol + 1, currentRow + 1);
                            currentTerrain = terrainList[hindranceHex.getCenterLocation().getTerrain().getType()];
                        }
                        else if (currentCol != 0 && currentRow != gridHeight &&
                                gridToHex(currentCol - 1, currentRow + 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                            hindranceHex = gridToHex(currentCol - 1, currentRow + 1);
                            currentTerrain = terrainList[hindranceHex.getCenterLocation().getTerrain().getType()];
                        }
                        else if (currentCol != gridWidth && currentRow != 0 &&
                                gridToHex(currentCol + 1, currentRow - 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                            hindranceHex = gridToHex(currentCol + 1, currentRow - 1);
                            currentTerrain = terrainList[hindranceHex.getCenterLocation().getTerrain().getType()];
                        }
                        else if (currentTerrain.isInherentTerrain() && !tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {
                            if (tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {

                                currentTerrain = terrainList[tempHex.getCenterLocation().getTerrain().getType()];
                            }
                            else {
                                currentTerrain = getTerrain("Open Ground");
                            }
                        }
                    }
                }
                else if (currentTerrain.isInherentTerrain() &&
                        !tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {
                    if (tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {

                        currentTerrain = terrainList[tempHex.getCenterLocation().getTerrain().getType()];
                    }
                    else {

                        currentTerrain = getTerrain("Open Ground");
                    }
                }


                currentTerrainHgt = currentTerrain.getHeight();
                groundLevel = (int) elevationGrid[currentCol][currentRow];

                /******************************
                 Set the current hex
                 ******************************/
                if (tempHex != currentHex) {

                    currentHex = tempHex;
                    rangeToSource = range(currentHex, sourceHex);
                    rangeToTarget = range(currentHex, targetHex);

                    // add the current hex
                    result.addHex(currentHex);

                    // set the bridge variables
                    bridge = currentHex.getBridge();

                    // get the vehicle hindrances
                    //if LOS on hex side, use the hex that has the most vehicles
                    //TODO: move vehicles to map
                    if ((LOSisHorizontal || LOSis60Degree) && sourceElevation == targetElevation) {
                        int numVeh = scenario.getVehicles(vehicleHex, sourceElevation).size();
                        if (currentRow != 0 && scenario.getVehicles(gridToHex(currentCol, currentRow - 1), sourceElevation).size() > numVeh) {

                            vehicleHex = gridToHex(currentCol, currentRow - 1);
                            numVeh = scenario.getVehicles(vehicleHex).size();
                        }
                        if (currentRow != gridHeight && scenario.getVehicles(gridToHex(currentCol, currentRow + 1), sourceElevation).size() > numVeh) {

                            vehicleHex = gridToHex(currentCol, currentRow + 1);
                            numVeh = scenario.getVehicles(vehicleHex).size();
                        }
                        if (LOSis60Degree && currentCol != 0 && currentRow != gridHeight && scenario.getVehicles(gridToHex(currentCol - 1, currentRow + 1), sourceElevation).size() > numVeh) {

                            vehicleHex = gridToHex(currentCol - 1, currentRow + 1);
                            numVeh = scenario.getVehicles(vehicleHex).size();
                        }
                        if (LOSis60Degree && currentCol != gridWidth && currentRow != 0 && scenario.getVehicles(gridToHex(currentCol + 1, currentRow - 1), sourceElevation).size() > numVeh) {

                            vehicleHex = gridToHex(currentCol + 1, currentRow - 1);
                        }
                    }

                    // vehicle hindrance?
                    if (vehicleHex != sourceHex && vehicleHex != targetHex &&
                            source.getAbsoluteHeight() == target.getAbsoluteHeight()) {

                        Iterator vehicles = scenario.getVehicles(vehicleHex).iterator();
                        Vehicle v;
                        while (vehicles.hasNext()) {

                            v = (Vehicle) vehicles.next();

                            // see if a LOS exists to the vehicle
                            LOSResult res1 = new LOSResult();
                            LOSResult res2 = new LOSResult();
                            LOS(source, useAuxSourceLOSPoint, v.getLocation(), false, res1, scenario);
                            LOS(target, useAuxTargetLOSPoint, v.getLocation(), false, res2, scenario);

                            if (!res1.isBlocked() && !res2.isBlocked()) {

                                // add vehicle hindrance
                                result.addVehicleHindrance(v, currentCol, currentRow, scenario);
                                if (result.isBlocked()) return;
                            }
                        }
                    }

                    if (bridge != null) {

                        // set bridge area
                        bridgeArea = bridge.getShape();
                        bridgeRoadArea = bridge.getRoadShape();
                    }

                    // still continuous slope?
                    if (Math.abs(sourceElevation - currentHex.getBaseHeight()) != rangeToSource) {

                        continuousSlope = false;
                    }

                    // lift the depression exit restriction?
                    if (exitsSourceDepression) {

                        if ((currentHex.isDepressionTerrain() &&
                                targetElevation - currentHex.getBaseHeight() >= rangeToTarget) ||
                                // LOS leaves gully because hex elevation is <= the elevation of the gully
                                (!currentHex.isDepressionTerrain() &&
                                        currentHex.getBaseHeight() <= sourceElevation)) {

                            ignoreGroundLevelHex = currentHex;
                            exitsSourceDepression = false;
                        }
                    }

                    // hex has smoke, or LOS on hexside and adjacent hex has smoke?
                    HashSet hexSmoke = getAllSmoke(currentHex);
                    if (hexSmoke.size() == 0) {

                        if (LOSisHorizontal) {

                            if (currentRow != 0) {

                                hexSmoke = getAllSmoke(gridToHex(currentCol, currentRow - 1));
                            }
                            if (hexSmoke.size() == 0 && currentRow != gridHeight) {

                                hexSmoke = getAllSmoke(gridToHex(currentCol, currentRow + 1));
                            }
                        }

                        else if (LOSis60Degree) {

                            if (currentCol != 0 && currentRow != 0) {

                                hexSmoke = getAllSmoke(gridToHex(currentCol - 1, currentRow - 1));
                            }
                            if (hexSmoke.size() == 0 && currentCol != gridWidth && currentRow != gridHeight) {

                                hexSmoke = getAllSmoke(gridToHex(currentCol + 1, currentRow + 1));
                            }
                            if (hexSmoke.size() == 0 && currentCol != 0 && currentRow != gridHeight && gridToHex(currentCol - 1, currentRow + 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                                hexSmoke = getAllSmoke(gridToHex(currentCol - 1, currentRow + 1));
                            }
                            if (hexSmoke.size() == 0 && currentCol != gridWidth && currentRow != 0 && gridToHex(currentCol + 1, currentRow - 1).getCenterLocation().getTerrain().isInherentTerrain()) {

                                hexSmoke = getAllSmoke(gridToHex(currentCol + 1, currentRow - 1));
                            }
                        }
                    }

                    if (hexSmoke != null && hexSmoke.size() > 0) {

                        Smoke s = null;

                        // step through all smoke counters in the hex
                        Iterator iter = hexSmoke.iterator();
                        while (iter.hasNext()) {

                            s = (Smoke) iter.next();

                            // in target hex
                            if ((currentHex == targetHex &&
                                    target.getAbsoluteHeight() >= s.getLocation().getAbsoluteHeight() &&
                                    target.getAbsoluteHeight() < s.getLocation().getAbsoluteHeight() + s.getHeight()) ||
                                    (target.getAbsoluteHeight() == s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                                            source.getAbsoluteHeight() < target.getAbsoluteHeight())
                                    ) {

                                // add hindrance
                                result.addSmokeHindrance(s, currentCol, currentRow);
                                if (result.isBlocked()) return;
                            }
                            // between source and target
                            else if (
                                    Math.max(source.getAbsoluteHeight(), target.getAbsoluteHeight()) <= s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                                            Math.min(source.getAbsoluteHeight(), target.getAbsoluteHeight()) >= s.getLocation().getAbsoluteHeight()
                                    ) {

                                if (// source and target under the smoke? Ignore
//									!(target.getAbsoluteHeight() < s.getLocation().getAbsoluteHeight() && source.getAbsoluteHeight() < s.getLocation().getAbsoluteHeight()) &&
                                    // source and target above smoke? Ignore
                                        !(source.getAbsoluteHeight() == s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                                                target.getAbsoluteHeight() == s.getLocation().getAbsoluteHeight() + s.getHeight())
                                        ) {
                                    // add hindrance
                                    result.addSmokeHindrance(s, currentCol, currentRow);
                                    if (result.isBlocked()) return;
                                }
                            }
                            // creates "blind hex"
                            else if (isBlindHex(
                                    sourceElevation,
                                    targetElevation,
                                    rangeToSource,
                                    rangeToTarget,
                                    groundLevel,
                                    s.getHeight()
                            )
                                    ) {

                                // add hindrance
                                result.addSmokeHindrance(s, currentCol, currentRow);
                                if (result.isBlocked()) return;
                            }
                        }
                    }
                }

                // LOS leaves the source building?
                if (!LOSLeavesBuilding) {
                    if (!currentTerrain.isBuilding()) {
                        LOSLeavesBuilding = true;
                    }
                }

                /******************************
                 Depression terrain
                 ******************************/
                // restricted by exiting a depression? (checked in all hexes)
                if (exitsSourceDepression) {

                    // LOS still in the depression?
                    if (groundLevel > currentHex.getBaseHeight()) {

                        blocked = true;
                        reason = LOS_err_A6_3_1;
                    }
                }

                // LOS must enter a depression?
                // range must be <= elevation difference or be in the depression
                if (entersTargetDepression) {
                    if (rangeToSource > (sourceElevation - targetElevation) &&
                            !(currentHex.isDepressionTerrain() && groundLevel == currentHex.getBaseHeight())) {

                        blocked = true;
                        reason = LOS_err_A6_3_2;
                    }
                }

                /******************************
                 Leaving buildings
                 ******************************/
                // blocked LOS leaving a building?
                if (!LOSLeavesBuilding &&
                        currentHex != sourceHex &&
                        currentTerrain.isBuilding() &&
                        target.getTerrain().isBuildingTerrain() &&
                        sourceElevation != targetElevation &&
                        groundLevel + currentTerrainHgt >= sourceElevation
                        ) {
                    reason = LOS_err_A6_8;
                    blocked = true;
                }

                /******************************
                 Edge terrain
                 ******************************/
                if (currentTerrain.isHexside() && !currentTerrain.getName().equals("Cliff")) {

                    // rowhouse wall?
                    if (currentTerrain.isRowhouseWall()) {

                        // always blocks if...
                        if (//higher than both source/target
                                (groundLevel + currentTerrainHgt > sourceElevation &&
                                        groundLevel + currentTerrainHgt > targetElevation) ||
                                        //same height as both source/target, but 1/2 level
                                        (groundLevel + currentTerrainHgt == sourceElevation &&
                                                groundLevel + currentTerrainHgt == targetElevation &&
                                                currentTerrain.isHalfLevelHeight()) ||
                                        //same height as higher source/target, but other is lower
                                        (groundLevel + currentTerrainHgt == Math.max(sourceElevation, targetElevation) &&
                                                groundLevel + currentTerrainHgt > Math.min(sourceElevation, targetElevation))
                                ) {

                            reason = LOS_err_B23_71;
                            blocked = true;
                        }

                        // otherwise check for blind hexes
                        else if (isBlindHex(
                                sourceElevation,
                                targetElevation,
                                rangeToSource,
                                rangeToTarget,
                                groundLevel,
                                currentTerrainHgt
                        )) {

                            reason = "Source or Target location is in a blind hex";
                            blocked = true;
                        }
                    }

                    else {

                        // target elevation must > source if in entrenchment
                        if (source.getTerrain().isEntrenchmentTerrain()) {

                            if (range > 1 && targetElevation <= sourceElevation) {

                                blocked = true;
                                reason = LOS_err_B27_2_1;
                            }
                        }
                        else if (target.getTerrain().isEntrenchmentTerrain()) {

                            if (range > 1 && targetElevation >= sourceElevation) {

                                blocked = true;
                                reason = LOS_err_B27_2_2;
                            }
                        }
                        else {

                            // should we ignore the edge terrain?
                            boolean ignore = isIgnorableHexsideTerrain(sourceHex, currentHex.nearestLocation(currentCol, currentRow), result.getSourceExitHexspine()) ||
                                    isIgnorableHexsideTerrain(targetHex, currentHex.nearestLocation(currentCol, currentRow), result.getTargetEnterHexspine());

                            if (!ignore) {

                                // check bocage
                                if (currentTerrain.getName().equals("Bocage")) {

                                    // always blocks if...
                                    if (//higher than both source/target
                                            (groundLevel + currentTerrainHgt > sourceElevation &&
                                                    groundLevel + currentTerrainHgt > targetElevation) ||
                                                    //same height as both source/target, but 1/2 level
                                                    (groundLevel + currentTerrainHgt == sourceElevation &&
                                                            groundLevel + currentTerrainHgt == targetElevation &&
                                                            currentTerrain.isHalfLevelHeight()) ||
                                                    //same height as higher source/target, but other is lower
                                                    (groundLevel + currentTerrainHgt == Math.max(sourceElevation, targetElevation) &&
                                                            groundLevel + currentTerrainHgt > Math.min(sourceElevation, targetElevation))
                                            ) {

                                        reason = LOS_err_B9_52_1;
                                        blocked = true;
                                    }

                                    // otherwise check for blind hexes
                                    else if (isBlindHex(
                                            sourceElevation,
                                            targetElevation,
                                            rangeToSource,
                                            rangeToTarget,
                                            groundLevel,
                                            currentTerrainHgt
                                    )) {

                                        reason = LOS_err_B9_52_2;
                                        blocked = true;
                                    }
                                }

                                // on the same level?
                                else if (groundLevel == sourceElevation && groundLevel == targetElevation) {

                                    blocked = true;
                                    reason = LOS_err_B9_2;
                                }
                            }
                        }
                    }
                }

                // Can we ignore the current hex?
                else if ((currentHex != sourceHex && currentHex != targetHex) ||
                        (currentHex == sourceHex && !currentTerrain.isOpen() && !source.isCenterLocation()) ||
                        (currentHex == targetHex && !currentTerrain.isOpen() && !target.isCenterLocation())
                        ) {

                    /******************************
                     Bridge causes hindrance?
                     ******************************/
                    if (currentHex.hasBridge()) {

                        if (sourceElevation == targetElevation && sourceElevation == bridge.getRoadLevel()) {

                            // on bridge but not on road?
                            if (bridgeArea.contains(currentCol, currentRow) && !bridgeRoadArea.contains(currentCol, currentRow)) {

                                // add hindrance
                                if (addHindranceHex(currentHex, sourceHex, targetHex, currentCol, currentRow, result)) return;
                            }
                        }
                    }

                    /******************************
                     Ground level higher than both source and target?
                     ******************************/
                    if (groundLevel > sourceElevation && groundLevel > targetElevation) {

                        reason = LOS_err_A6_2_1;
                        blocked = true;
                    }

                    /******************************
                     Lower level of split terrain
                     ******************************/
                    else if (currentTerrain.hasSplit() &&
                            groundLevel == sourceElevation &&
                            groundLevel == targetElevation) {

                        if (currentTerrain.isLowerLOSObstacle()) {

                            reason = "This terrain blocks LOS to same same elevation Source and Target";
                            blocked = true;
                        }
                        else if (currentTerrain.isLowerLOSHindrance()) {

                            // add hindrance
                            if (addHindranceHex(hindranceHex, sourceHex, targetHex, currentCol, currentRow, result)) return;
                        }
                    }

                    /******************************
                     Half level terrain on same elevation
                     ******************************/
                    else if (currentTerrain.isHalfLevelHeight() &&
                            groundLevel + currentTerrainHgt == sourceElevation &&
                            groundLevel + currentTerrainHgt == targetElevation) {

                        // terrain blocks LOS?
                        if (currentTerrain.isLOSObstacle()) {
                            reason = LOS_err_A6_2_2;
                            blocked = true;
                        }
                        // must be hindrance
                        else {

                            // add hindrance
                            if (addHindranceHex(hindranceHex, sourceHex, targetHex, currentCol, currentRow, result)) return;
                        }
                    }

                    /******************************
                     Higher than both source and target
                     ******************************/
                    else if (groundLevel + currentTerrainHgt > sourceElevation &&
                            groundLevel + currentTerrainHgt > targetElevation) {

                        // terrain blocks LOS?
                        if (currentTerrain.isLOSObstacle()) {
                            reason = LOS_err_A6_2_3;
                            blocked = true;
                        }
                        // must be hindrance
                        else {

                            // add hindrance
                            if (addHindranceHex(hindranceHex, sourceHex, targetHex, currentCol, currentRow, result)) return;
                        }
                    }

                    /******************************
                     Blocked if equal to the higher of either location when...
                     ******************************/
                    else if (groundLevel + currentTerrainHgt == Math.max(sourceElevation, targetElevation) &&
                            groundLevel + currentTerrainHgt > Math.min(sourceElevation, targetElevation) &&
                            // are exiting gully restrictions satisfied?
                            !(ignoreGroundLevelHex != null && ignoreGroundLevelHex.containsExtended(currentCol, currentRow)) &&
                            // are entering gully restrictions satisfied?
                            !(entersTargetDepression && currentHex.isDepressionTerrain()) &&
                            !(exitsSourceDepression && currentHex.isDepressionTerrain())
                            ) {

                        // Need to handle special case where source unit is adjacent to a water obstacle looking
                        // at a target in the water obstacle. We can ignore the bit of open ground that extends into
                        // the first water hex.
                        if (!(currentHex.getCenterLocation().getTerrain().isWaterTerrain() &&
                                currentTerrain.getHeight() < 1 &&
                                ((rangeToSource == 1 && sourceElevation > targetElevation && target.getHex().getCenterLocation().getTerrain().isWaterTerrain()) ||
                                        (rangeToTarget == 1 && targetElevation > sourceElevation && source.getHex().getCenterLocation().getTerrain().isWaterTerrain())))) {

                            // if orchard, then hindrance
                            if (currentTerrain.getName().equals("Orchard, Out of Season")) {

                                if (addHindranceHex(hindranceHex, sourceHex, targetHex, currentCol, currentRow, result)) return;

                            }
                            else {
                                reason = LOS_err_A6_2_4;
                                blocked = true;
                            }

                        }
                    }

                    /******************************
                     Check for blind hexes
                     ******************************/
                    else if (
                            groundLevel + currentTerrainHgt > Math.min(sourceElevation, targetElevation) &&
                                    groundLevel + currentTerrainHgt < Math.max(sourceElevation, targetElevation)
                            ) {

                        if (isBlindHex(
                                sourceElevation,
                                targetElevation,
                                rangeToSource,
                                rangeToTarget,
                                groundLevel,
                                currentTerrainHgt,
                                nearestHexsideIsCliff(currentCol, currentRow)
                        )) {
                            // blocked if terrain is obstacle
                            if (currentTerrain.isLOSObstacle()) {
                                reason = LOS_err_A6_4_1;
                                blocked = true;
                            }

                            // see if ground level alone creates blind hex
                            else if (groundLevel > Math.min(sourceElevation, targetElevation) &&
                                    groundLevel < Math.max(sourceElevation, targetElevation) &&
                                    isBlindHex(
                                            sourceElevation,
                                            targetElevation,
                                            rangeToSource,
                                            rangeToTarget,
                                            groundLevel,
                                            0,
                                            nearestHexsideIsCliff(currentCol, currentRow)
                                    )
                                    ) {
                                reason = LOS_err_B10_23;
                                blocked = true;
                            }

                            // hindrance creates "blind hex", if not target/source hex
                            else if (currentHex != targetHex && currentHex != sourceHex) {

                                // only one hindrance for out-of-season orchard
                                if (currentTerrain.getName().equals("Orchard, Out of Season")) {

                                    if (rangeToTarget == 1) {

                                        if (addHindranceHex(hindranceHex, sourceHex, targetHex, currentCol, currentRow, result)) return;
                                    }
                                }
                                else {
                                    // add hindrance
                                    if (addHindranceHex(hindranceHex, sourceHex, targetHex, currentCol, currentRow, result)) return;
                                }
                            }
                        }
                    }
                }

                // set results if blocked
                if (blocked) {
                    result.setBlocked(currentCol, currentRow, reason);
                    return;
                }

                // next row
                currentRow += rowDir;
            }

            // adjust variables for next column
            enter = exit;
            currentCol += colDir;

            // adjust variables for last column
            if (col + 1 == numCols) {
                exit = targetY;
            }
            else {
                exit += deltaY;
            }
        }

        // set continuous slope result
        result.setContinuousSlope(continuousSlope);
    }

    /**
     * Determines if a location is on one of the hexsides for a hex.
     * @param  h the hex
     * @param  l the hexside location
     */
    public boolean isAdjacentHexside(Hex h, Location l) {

        // ignore center locations
        if (l.isCenterLocation()) {

            return false;
        }
        // same hex?
        else if (l.getHex() == h) {

            return true;
        }

        // opposite hexside?
        for (int x = 0; x < 6; x++) {

            // get the adjacent hex for this hexside
            Hex h2 = getAdjacentHex(h, x);
            if (h2 != null) {

                // adjacent to this hexside?
                if (h2.getHexsideLocation(h2.getOppositeHexside(x)) == l) {

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determines if a location is one of the hexspines for a hex.
     * @param  h the hex
     * @param  l the hexside location
     */
    public boolean isHexspine(Hex h, Location l) {

        // check each hexspine
        for (int x = 0; x < 6; x++) {

            // get the adjacent hex for this hexside
            Hex h2 = getAdjacentHex(h, x);
            if (h2 != null) {

                // hexspine location?
                if (x == 0 && (h2.getHexsideLocation(2) == l || h2.getHexsideLocation(4) == l)) return true;
                if (x == 1 && (h2.getHexsideLocation(3) == l || h2.getHexsideLocation(5) == l)) return true;
                if (x == 2 && (h2.getHexsideLocation(4) == l || h2.getHexsideLocation(0) == l)) return true;
                if (x == 3 && (h2.getHexsideLocation(5) == l || h2.getHexsideLocation(1) == l)) return true;
                if (x == 4 && (h2.getHexsideLocation(0) == l || h2.getHexsideLocation(2) == l)) return true;
                if (x == 5 && (h2.getHexsideLocation(1) == l || h2.getHexsideLocation(3) == l)) return true;
            }
        }

        return false;
    }

    /**
     * Determines if a hexside terrain can be ignored for a hex.
     * @param  h the hex. LOS assumed to start or end in this hex
     * @param  l the hexside location
     * @param  LOSHexspine hexspine the LOS enters/leaves hex h. -1 if LOS not on hexspine.
     */
    public boolean isIgnorableHexsideTerrain(Hex h, Location l, int LOSHexspine) {

        // some useful variables
        Hex locationHex = l.getHex();
        int locationHexside = locationHex.getLocationHexside(l);
        Terrain locationHexsideTerrain = locationHex.getEdgeTerrain(locationHexside);

        // too far away?
        if (range(h, locationHex) > 2) {

            return false;
        }
        // always ignore if adjacent
        if (isAdjacentHexside(h, l)) {

            return true;
        }
        // ignore hexspines if not bocage
        if (isHexspine(h, l) && locationHexsideTerrain != null && !locationHexsideTerrain.getName().equals("Bocage")) {

            return true;
        }
        // ignore any hexside terrain that spilled into an adjacent hex
        if (locationHexsideTerrain == null) {

            return true;
        }

        // for LOS along a hexspine, check hexside terrain at the far end of the hexspine
        if (LOSHexspine >= 0) {

            // for locations that are 2 hexes away, let's use the corresponding
            // location in the adjacent hex
            if (range(h, locationHex) == 2) {

                // find the hex across the location hexside
                Hex oppositeHex = getAdjacentHex(locationHex, locationHexside);

                if (oppositeHex != null && range(h, oppositeHex) > 1) {

                    return false;
                }

                // change the location values
                locationHex = oppositeHex;
                locationHexside = oppositeHex.getOppositeHexside(locationHexside);
                l = locationHex.getHexsideLocation(locationHexside);
            }


            // The following code check the hex/hexsides listing in this table:
            //
            //	LOS Hexside Hexspine Hexside 1  Hexside 2
            //	----------- -------- ---------  ---------
            //	0           0, 4     5, 0       0, 5
            //	1           1, 5     0, 1       1, 0
            //	2           2, 0     1, 2       2, 1
            //	3           3, 1     2, 3       3, 2
            //	4           4, 2     3, 4       4, 3
            //	5           5, 3     4, 5       5, 4
            //
            //	where x is the adjacent hex, y is the hexside

            int hexside = LOSHexspine == 0 ? 5 : LOSHexspine - 1;
            int hexspine = LOSHexspine < 2 ? LOSHexspine + 4 : LOSHexspine - 2;
            Hex hex1 = getAdjacentHex(h, hexside);
            Hex hex2 = getAdjacentHex(h, LOSHexspine);
//            Location l1 = hex2.getHexsideLocation(hexspine);
            Location l2 = hex1.getHexsideLocation(LOSHexspine);
            Location l3 = hex2.getHexsideLocation(hexside);
            Terrain t1 = hex2.getEdgeTerrain(hexspine);
            Terrain t2 = hex1.getEdgeTerrain(LOSHexspine);
            Terrain t3 = hex2.getEdgeTerrain(hexside);

            if (t1 != null && (l == l2 || l == l3) && (t2 == null || t3 == null)) {

                return true;
            }
        }
        return false;
    }

    /**
     * Returns the set of all smoke objects in a hex.
     * @return HashSet containing all smoke objects
     */
    public HashSet getAllSmoke(Hex h) {

        // step through all smoke locations
        HashSet<Smoke> allSmoke = new HashSet(smokeList.size());
        Iterator iter = smokeList.iterator();
        Smoke sl;
        while (iter.hasNext()) {

            sl = (Smoke) iter.next();

            if (sl.getLocation().getHex() == h) {
                allSmoke.add(sl);
            }
        }

        return allSmoke;
    }

    // nearest location is a cliff?
    private boolean nearestHexsideIsCliff(int x, int y) {

        Hex h = gridToHex(x, y);
        Location l = gridToHex(x, y).nearestLocation(x, y);

        //TODO: simplify
        if (l.isCenterLocation()) {

            return false;
        }
        else {

            return h.hasCliff(h.getLocationHexside(l));
        }
    }

    // add hindrance and return true if LOS blocked
    private boolean addHindranceHex(
            Hex currentHex,
            Hex sourceHex,
            Hex targetHex,
            int currentCol,
            int currentRow,
            LOSResult result) {

        // add hex if necessary
        if (currentHex != sourceHex && currentHex != targetHex) {

            result.addMapHindrance(currentHex, currentCol, currentRow);

            // see if hindrance caused LOS to be blocked
            return result.isBlocked();
        }
        return false;
    }

    private boolean isBlindHex(
            int sourceElevation,
            int targetElevation,
            int rangeToSource,
            int rangeToTarget,
            int groundLevel,
            int currentTerrainHgt
    ) {

        return isBlindHex(
                sourceElevation,
                targetElevation,
                rangeToSource,
                rangeToTarget,
                groundLevel,
                currentTerrainHgt,
                false);
    }

    protected boolean isBlindHex(
            int sourceElevation,
            int targetElevation,
            int rangeToSource,
            int rangeToTarget,
            int groundLevel,
            int currentTerrainHgt,
            boolean isCliffHexside
    ) {

        int temp;

        // blind hex NA for same-level LOS
        if(sourceElevation == targetElevation){
            return false;
        }

        // if LOS raising, swap source/target and use the same logic as LOS falling
        if (sourceElevation < targetElevation) {

            // swap elevations
            temp = sourceElevation;
            sourceElevation = targetElevation;
            targetElevation = temp;

            // swap range
            temp = rangeToSource;
            rangeToSource = rangeToTarget;
            rangeToTarget = temp;
        }


        // is the obstacle a non-cliff crestline?
        if (currentTerrainHgt == 0 && !isCliffHexside) {

            return rangeToTarget <= Math.max(2 * (groundLevel + currentTerrainHgt) + ((int) rangeToSource / 5) - sourceElevation - targetElevation, 0);
        }
        else {

            return rangeToTarget <= Math.max(2 * (groundLevel + currentTerrainHgt) + ((int) rangeToSource / 5) - sourceElevation - targetElevation + 1, 1);
        }
    }

    /**
     * Sets the terrain name map and populate the terrain list
     */
    public void setTerrain(HashMap<String, Terrain> nameMap) {

        terrainNameMap = nameMap;
        for(String name : nameMap.keySet()) {

            terrainList[nameMap.get(name).getType()] = nameMap.get(name);
        }
    }

    /**
     * Determines if a pixel is within the map image.
     * @param c the x coordinate of the map image pixel
     * @param r the y coordinate of the map image pixel
     * @return returns true if within the image, else false
     */
    public boolean onMap(int c, int r) {

        if (r < 0 || c < 0 || c >= gridWidth || r >= gridHeight)
            return false;
        else
            return true;
    }

    /**
     * Returns a set of hexes that intersect ("touch") the given rectangle.
     * @param rect map area
     * @return a Vector containing the intersecting hexes
     */
    //TODO: move to map editor
    public Vector intersectedHexes(Rectangle rect) {

        Vector<Hex> hexes = new Vector<Hex>(5, 5);
        Hex currentHex;

        // find the hexes in the corner of the rectangle, clip to map boundry
        Hex upperLeft = gridToHex(
                Math.max((int) rect.getX(), 0),
                Math.max((int) rect.getY(), 0));
        Hex lowerRight = gridToHex(
                Math.min((int) (rect.getX() + rect.getWidth()), gridWidth - 1),
                Math.min((int) (rect.getY() + rect.getHeight()), gridHeight - 1));

        // Rectangle completely in a single hex? Add the hex and quit
        if (upperLeft == lowerRight) {

            hexes.addElement(upperLeft);
            return hexes;
        }

        // our desired bounds
        int minX = Math.max(upperLeft.getColumnNumber() - 1, 0);
        int minY = Math.max(upperLeft.getRowNumber() - 1, 0);
        int maxX = Math.min(lowerRight.getColumnNumber() + 1, width - 1);
        int maxY = Math.min(lowerRight.getRowNumber() + 1, height);

        // check all hexes bound by the corners to the vector
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY;
                 y <= Math.min(maxY, hexGrid[x].length - 1);
                 y++) {
                currentHex = getHex(x, y);

                // add hexes that touch
                if (currentHex.isTouchedBy(rect)) {
                    hexes.addElement(getHex(x, y));
                }
            }
        }
        return hexes;
    }

    // ignore edge terrain in adjacent hex?
    protected boolean ignoreEdgeTerrain(int referenceHexside, int adjacentHexside) {

        switch (referenceHexside) {
            case 0:
                if (adjacentHexside == 2 ||
                        adjacentHexside == 3 ||
                        adjacentHexside == 4) {

                    return true;
                }
                break;
            case 1:
                if (adjacentHexside == 3 ||
                        adjacentHexside == 4 ||
                        adjacentHexside == 5) {

                    return true;
                }
                break;
            case 2:
                if (adjacentHexside == 4 ||
                        adjacentHexside == 5 ||
                        adjacentHexside == 0) {

                    return true;
                }
                break;
            case 3:
                if (adjacentHexside == 5 ||
                        adjacentHexside == 0 ||
                        adjacentHexside == 1) {

                    return true;
                }
                break;
            case 4:
                if (adjacentHexside == 0 ||
                        adjacentHexside == 1 ||
                        adjacentHexside == 2) {

                    return true;
                }
                break;
            case 5:
                if (adjacentHexside == 1 ||
                        adjacentHexside == 2 ||
                        adjacentHexside == 3) {

                    return true;
                }
                break;
        }

        return false;
    }

    /**
     * Rotates the map 180 degrees. Should only be used for geomorphic map boards
     */
    //TODO: move to editor?
    public void flip() {

        char terrain;
        byte elevation;
        Hex h1, h2;

        // flip the terrain and elevation grids
        for (int x = 0; x < (gridWidth+1) / 2; x++) {
            for (int y = 0; y < gridHeight; y++) {

                terrain = terrainGrid[x][y];
                terrainGrid[x][y] = terrainGrid[gridWidth - x - 1][gridHeight - y - 1];
                terrainGrid[gridWidth - x - 1][gridHeight - y - 1] = terrain;

                elevation = elevationGrid[x][y];
                elevationGrid[x][y] = elevationGrid[gridWidth - x - 1][gridHeight - y - 1];
                elevationGrid[gridWidth - x - 1][gridHeight - y - 1] = elevation;
            }
        }

        // flip the hex grid
        for (int x = 0; x < hexGrid.length / 2 + 1; x++) {
            for (int y = 0; y < (x == hexGrid.length / 2 ? (hexGrid[x].length - 1) / 2 + 1 : hexGrid[x].length); y++) {

                // get the next two hexes
                h1 = hexGrid[x][y];
                h2 = hexGrid[width - x - 1][hexGrid[width - x - 1].length - y - 1];

                // swap the hexes in the grid
                hexGrid[x][y] = h2;
                hexGrid[width - x - 1][hexGrid[width - x - 1].length - y - 1] = h1;

                // flip the hexes themselves
                h1.flip();
                h2.flip();

                // swap the column/row numbers
                int temp = h1.getColumnNumber();
                h1.setColumnNumber(h2.getColumnNumber());
                h2.setColumnNumber(temp);

                temp = h1.getRowNumber();
                h1.setRowNumber(h2.getRowNumber());
                h2.setRowNumber(temp);

                // swap the hex polygons
                Polygon poly = h1.getHexBorder();
                h1.setHexBorder(h2.getHexBorder());
                h2.setHexBorder(poly);

                poly = h1.getExtendedHexBorder();
                h1.setExtendedHexBorder(h2.getExtendedHexBorder());
                h2.setExtendedHexBorder(poly);
            }
        }
    }

    /**
     *	This method is intended to be used only to copy geomorphic maps into
     *	a larger map "grid" for VASL. As such, 1) it is assumed the half hex along board
     *	edges are compatible, and 2) the hex/location names from the map that is being
     *	inserted should be used. Other uses will produce unexpected results.
     * @param map the map to insert
     * @param upperLeft the upper left corner of the inserted map should align with this hex
     * @return true if the map was successfully inserted
     */
    //TODO: move to editor?
    public boolean insertMap(Map map, Hex upperLeft) {

        // determine where the upper-left pixel of the inserted map will be
        int left = upperLeft.getCenterLocation().getLOSPoint().x;
        int upper = upperLeft.getCenterLocation().getLOSPoint().y - (int) Hex.HEIGHT / 2;

        // ensure the map will fit
        if (!onMap(left, upper)) {

            return false;
        }

        // ensure the map will fit
        if (left + map.getGridWidth() > this.gridWidth || upper + map.getGridHeight() > this.gridHeight) {

            return false;
        }

        // copy the terrain and elevation grids
        for (int x = 0; x < map.gridWidth; x++) {
            for (int y = 0; y < map.gridHeight; y++) {

                terrainGrid[left + x][upper + y] = (char) map.getGridTerrain(x, y).getType();
                elevationGrid[left + x][upper + y] = (byte) map.getGridElevation(x, y);
            }
        }

        // copy the hex grid
        int hexRow = upperLeft.getRowNumber();
        int hexCol = upperLeft.getColumnNumber();
        for (int x = 0; x < map.hexGrid.length; x++) {
            for (int y = 0; y < map.hexGrid[x].length; y++) {

                hexGrid[x + hexCol][y + hexRow].copy(map.getHex(x, y));

            }
        }

        return true;
    }

    /**
     * Crops the board to the points in the map grid. Note that the "corners" of the cropped map must create
     * a map where the left and right board edges are half hexes and both corner hexes are fully on the map
     * @param upperLeft upper left corner of the map grid
     * @param lowerRight the lower-right corner of the map grid
     * @return the cropped map or null if invalid
     */
    //TODO: extend to handle non-symmetrical geometries and alternate hex sizes
    public Map crop(Point upperLeft, Point lowerRight){

        int gridWidth = lowerRight.x - upperLeft.x;
        int gridHeight = lowerRight.y - upperLeft.y;
        int hexWidth = (int) Math.round(gridWidth/Hex.WIDTH) + 1;
        int hexHeight = (int) Math.round(gridHeight/Hex.HEIGHT);

        // the hex width must be odd - if not extend to include the next half hex
        if (hexWidth%2 != 1) {
            hexWidth++;
        }

        Map newMap = new Map(hexWidth, hexHeight, terrainNameMap);

        // copy the map grid
        for(int x = 0; x < newMap.gridWidth; x++) {
            for(int y = 0; y < newMap.gridHeight; y++){

                newMap.terrainGrid[x][y] = terrainGrid[x + upperLeft.x][y + upperLeft.y];
                newMap.elevationGrid[x][y] = elevationGrid[x + upperLeft.x][y + upperLeft.y];
            }
        }

        //copy the hex grid
        Hex upperLeftHex = gridToHex(upperLeft.x, upperLeft.y);
        for (int x = 0; x < newMap.hexGrid.length; x++) {
            for (int y = 0; y < newMap.hexGrid[x].length; y++) {

                try {
                    newMap.hexGrid[x][y] = (hexGrid[x + upperLeftHex.getColumnNumber()][y + upperLeftHex.getRowNumber()]);
                }
                catch (Exception e) {
                    System.out.println();
                }
            }
        }

        return newMap;
    }

}

