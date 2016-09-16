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

import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.RBrrembankments;
import VASL.build.module.map.boardArchive.Slopes;

import VASL.LOS.VASLGameInterface;
import VASL.LOS.counters.OBA;
import VASL.LOS.counters.Smoke;
import VASL.LOS.counters.Vehicle;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.*;

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
public class Map  {

    // default hex geometry
    private double hexHeight = BoardArchive.GEO_HEX_HEIGHT;
    private double hexWidth = BoardArchive.GEO_HEX_WIDTH;

    // terrain names which is used a number of times
    private final static String HILLOCK = "Hillock";
    private final static String WALL = "Wall";
    private final static String HEDGE = "Hedge";
    private final static String BOCAGE = "Bocage";
    private final static String STONE_RUBBLE = "Stone Rubble";
    private final static String WOODEN_RUBBLE = "Wooden Rubble";
    private final static String GRAIN = "Grain";
    private final static String BRUSH = "Brush";

    // the "upper left" hex - even if it's not A1 (e.g. boards 1b-6b) 
    private double A1CenterX;
	private double A1CenterY;

	// width and height of the map in hexes
	private int width;
	private int	height;

    // width and height of the terrain grid
	private int	gridWidth;
	private int	gridHeight;

    // map information
	private char[][] terrainGrid;  		// terrain for each pixel
	private byte[][] elevationGrid;  	// height for each pixel
	private Hex[][]	 hexGrid;			// hex array [column][row]

    // terrain type codes
	private Terrain[] terrainList = new Terrain[256];

    // map terrain names to the terrain objects
    private static HashMap<String, Terrain> terrainNameMap;

    // the hillocks
    private HashSet<Hillock> hillocks = new HashSet<Hillock>();

	/**
	 * Constructs a new <code>Map</code> object using custom hex size and explicit image size.
	 * A standard geomorphic map board is 10 x 33 hexes.
	 * @param width the width of the map in hexes
	 * @param height the height of the map in hexes
	 * @param A1CenterX x-offset of the A1 hex center dot
     * @param A1CenterY y-offset of the A1 hex center dot
	 * @param imageWidth width of the board image in pixels
	 * @param imageHeight height of the board image in pixels
	 * @param terrainNameMap mapping of terrain names to terrain objects
	 */

    //DR new constructor - hexWidth and hexHeight now passed in as parameters
    public Map(double hexWidth, double hexHeight, int width, int height, double A1CenterX, double A1CenterY, int imageWidth, int imageHeight, HashMap<String, Terrain> terrainNameMap){
        this.width = width;
        this.height = height;

        //Set the hex geometry
        //DR set hexHeight, hexWidth values using parameters
        this.hexHeight=hexHeight;
        this.hexWidth=hexWidth;

        this.A1CenterX = (A1CenterX == BoardArchive.missingValue() ? BoardArchive.GEO_A1_Center.x : A1CenterX);
		this.A1CenterY = (A1CenterY == BoardArchive.missingValue() ? BoardArchive.GEO_A1_Center.y : A1CenterY);

		// initialize
		setTerrain(terrainNameMap);
		gridWidth = imageWidth;
		gridHeight = imageHeight;
		terrainGrid = new char[gridWidth][gridHeight];
		elevationGrid = new byte[gridWidth][gridHeight];

		// create the hex grid
		hexGrid = new Hex[this.width][];
		for (int col = 0; col < this.width; col++) {

			hexGrid[col] = new Hex[this.height + (col % 2)]; // add 1 if odd
			for (int row = 0; row < this.height + (col % 2); row++) {
				hexGrid[col][row] = new Hex(col, row, getGEOHexName(col, row), getHexCenterPoint(col, row), hexHeight, hexWidth, this, 0, terrainList[0]);
			}
		}

		// reset the hex locations to map grid
		for (int col = 0; col < this.width; col++) {
			for (int row = 0; row < this.height + (col % 2); row++) {
				hexGrid[col][row].resetHexsideLocationNames();
			}
		}
	}

    /**
     * Finds the center point of a hex in the hexgrid of a geometric board
     * NOTE - does NOT handle arbitrary relationships between the hex grid and the image
     *        Assumes [0][0] is the upper left hex on the map
     * @param col the hex column (0 = the first column in the grid)
     * @param row the hex row (0 = the first row in the column)
     * @return the hex center point
     */
    private Point2D.Double getHexCenterPoint(int col, int row) {

        // odd columns will be offset half a hex toward the top of the map
        // if the A1 x offset is negative (e.g. boards 1b-6b), assume it's zero
        return new Point2D.Double(
                (A1CenterX < 0.0 ? 0.0 : A1CenterX) + hexWidth * (double) col,
                (A1CenterY < 0.0 ? hexHeight/2.0 : A1CenterY) + hexHeight * (double) row - hexHeight/2.0 * (double) (col%2)
        );

    }

    /**
     * Find the hex name for a hex on a board
     * By convention the columns are A-Z, then AA-ZZ, then AAA-ZZZ for max of 26*3 columns
     * Row number can be any integer
     * NOTE - does NOT handle arbitrary negative offsets for A1 - assumes offsets < 0 are for boards 1b - 6b, etc.
     * @param col the hex column (0 = the first column in the grid)
     * @param row the hex row (0 = the first row in the column)
     * @return the hex name. If invalid the empty string is returned
     */
    private String getGEOHexName(int col, int row) {

        char c;
        String name = "";

        // negative A1 x offset implies boards 1b-6b
        if(A1CenterX < 0.0) {

            if (col < 10) {
                c = (char) ((int) 'Q' + col);
                name += c;
            }
            else {
                c = (char) ((int) 'A' + (col - 10)%26);
                name += c;
                name += c;
            }
        }
        else {
            c = (char) ((int) 'A' + col%26);
            name += c;

            for (int x = 0; x < col/26; x++) {
                name += c;
            }
        }

        // add row as suffix - even cols (e.g. A = 0) will start with 1; odd cols will start with zero
        // negative A1 y offset implies boards BFP DW 2b
        int rowOffset = A1CenterY < 0.0 ? (int) (-A1CenterY/hexHeight) + 1: 0;
        return name + (row + rowOffset + (col%2 == 0 ? 1 : 0));
    }

	/**
     * Constructs a new <code>Map</code> as a standard geomorphic map board (10 x 33 hexes)
     * @param w the width of the map in hexes
     * @param h the height of the map in hexes
     */
    public Map(int w, int h, HashMap<String, Terrain> terrainNameMap) {
        //DR added two variables to pass in hexWidth and hexHeight
        this(BoardArchive.GEO_HEX_WIDTH, BoardArchive.GEO_HEX_HEIGHT, w, h, BoardArchive.GEO_A1_Center.x, BoardArchive.GEO_A1_Center.y, (int) BoardArchive.GEO_IMAGE_WIDTH, (int) BoardArchive.GEO_IMAGE_HEIGHT, terrainNameMap);

    }

	/**
	 * @return the hex width
	 */
	public double getHexWidth() {
		return hexWidth;
	}

    /**
     * @return the hex height
     */
    public double getHexHeight() {
        return hexHeight;
    }

    /**
     * Returns the <code>Terrain</code> type code for the pixel at row, col of
     * the map image.
     * @param row the row coordinate
     * @param col the col coordinate
     * @return  the terrain type code at (row, col)
     */
    public int getGridTerrainCode(int row, int col) {

        return (int) terrainGrid[row][col];
    }

    /**
     * Returns the <code>Terrain</code> for the pixel at row, col of the map image.
     * @param row the row coordinate
     * @param col the col coordinate
     * @return <code>Terrain</code> the terrain type at (row, col) if on board, otherwise null
     */
    public Terrain getGridTerrain(int row, int col) {

        if (onMap(row, col)) {
            return terrainList[(int) terrainGrid[row][col]];
        }
        return null;
    }

    /**
     * Sets the terrain code for the pixel at row, col of the map image.
     * @param terrainCode the <code>Terrain </code> code
     * @param row the row coordinate
     * @param col the col coordinate
     */
    public void setGridTerrainCode(int terrainCode, int row, int col) {

        terrainGrid[row][col] = (char) terrainCode;
    }

    /**
     * Returns the ground level for the pixel at row, col of the map image.
     * @param row the row coordinate
     * @param col the col coordinate
     * @return the ground level at (row, col) or 0 if not on map
     */
    public int getGridElevation(int row, int col) {

        if (onMap(row, col)) {
            return (int) elevationGrid[row][col];
        }
        return 0;
    }

    /**
     * Set the elevation of the point at row, col
     * @param elevation the new elevation
     * @param row the row coordinate
     * @param col the col coordinate
     */
    public void setGridElevation(int elevation, int row, int col) {

        elevationGrid[row][col] = (byte) elevation;

    }

    /**
     * Sets the hex grid to conform with the terrain and elevation grids
     */
    public void resetHexTerrain(){

        // step through each hex and reset the terrain.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height + (x % 2); y++) { // add 1 hex if odd
                getHex(x,y).resetTerrain();
            }
        }

        // recreate the hillocks
        buildHillocks();
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

		for (int col = 0; col < hexGrid.length; col++) {
            for (int row = 0; row < hexGrid[col].length; row++) {

				Hex hex = getHex(col, row);

				if (hex.getName().equalsIgnoreCase(name)) {

                    return hex;
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
            @SuppressWarnings("unused") Hex temp = hexGrid[col][row];
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

		if(hexside > 5) {
			return null;
		}

        int col = h.getColumnNumber();
        int row = h.getRowNumber();
        boolean colIsEven = (col % 2 == 0);

		//noinspection SwitchStatementWithoutDefaultBranch
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
     * Finds the hex that contains a point
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the Hex containing the point
     */
    public Hex gridToHex(int x, int y) {

        // ensure the point is on the map
        if(!onMap(x, y)) {
            return null;
        }
        try {
            // DR added -A1CenterX to handle x offset on HASL maps
            // also trapped all errors in finding a column and row; return null rather than break
            int z = (int) ((double) (x - (A1CenterX < 0.0 ? 0 : A1CenterX)) / (hexWidth / 3.0));
            int row;
            int col;

            // in "grey area" between columns?
            if ((z - 1) % 3 == 0) {

                col = (int) Math.ceil(((double) z - 1.0) / 3.0);
                row = (int) ((col % 2 == 0) ? (double) y / hexHeight
                        : ((double) y + hexHeight / 2.0) / hexHeight);

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

                col = (int) Math.ceil((double) z / 3.0);
                row = (int) ((col % 2 == 0) ? (double) y / hexHeight
                        : ((double) y + hexHeight / 2.0) / hexHeight);
                return hexGrid[col][row];
            }
         // return null on any error
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the range between two hexes.
     * @param source source hex
     * @param target "target" hex
     * @return the range
     */
    public static int range(Hex source, Hex target) {

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
     * @param source source location
     * @param useAuxSourceLOSPoint use auxiliary bypass aiming point for source location
     * @param target "target" location
     * @param useAuxTargetLOSPoint use auxiliary bypass aiming point for target location
     * @param result <code>LOSResult</code> that will contain all of the LOS information
     * @param VASLGameInterface <code>Scenario</code> that contains all scenario-dependent LOS information
     */
    public void LOS(Location source, boolean useAuxSourceLOSPoint, Location target, boolean useAuxTargetLOSPoint, LOSResult result, VASLGameInterface VASLGameInterface) {

        // initialize the results
        result.reset();
        result.setSourceLocation(source);
        result.setTargetLocation(target);

        // in the same location?
        if (source.equals(target)) {

            result.setRange(0);
            return;
        }

        LOSStatus status = new LOSStatus(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, VASLGameInterface);

        // check same hex rules
        if(checkSameHexSmokeRule(status, result)) {
            return;
        }
        if (checkSameHexRule(status, result)) {
            return;
        }

        // step through each column
        status.currentCol = status.sourceX;
        for (int col = 0; col < status.numCols; col++) {

            // set row variables
            status.currentRow = (int) status.enter;
            int numRows = Math.abs((int) status.exit - (int) status.enter) + 1;

            // step through the current row
            for (int row = 0; row < numRows; row++) {

                // code added by DR to enable Roofless factory hexes

                status.currentTerrain = getGridTerrain(status.currentCol, status.currentRow);
                status.groundLevel = getGridElevation(status.currentCol, status.currentRow); //int) elevationGrid[status.currentCol][status.currentRow];

                // temp hex is the hex the LOS point is in
                if (status.sourceHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow)) {

                    status.tempHex = status.sourceHex;
                }
                else if (status.targetHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow)) {

                    status.tempHex = status.targetHex;
                }
                else {

                    status.tempHex = gridToHex(status.currentCol, status.currentRow);
                }

                // LOS leaves the source building?
                if (!status.LOSLeavesBuilding) {
                    if (!status.currentTerrain.isBuilding()) {
                        status.LOSLeavesBuilding = true;
                    }
                }

                // If LOS is on a hexside fetch the two adjacent hexes
                AdjacentHexes adjacentHexes = null;
                if(status.LOSisHorizontal || status.LOSis60Degree) {

                    adjacentHexes = getAdjacentHexes(status, status.tempHex);
                }

                // check the LOS rules for this point and return if blocked
                if(adjacentHexes == null){

                    if (applyLOSRules(status, result)) {
                        return;
                    }
                }
                else {

                    // for LOS on hexside need to check both hexes
                    if(adjacentHexes.getTop() != null && adjacentHexes.getTop().getExtendedHexBorder().contains(status.currentCol, status.currentRow)) {
                        if(checkLOSOnHexsideRule(result, status, adjacentHexes.getTop())) {
                            return;
                        }
                    }
                    if(adjacentHexes.getBottom() != null && adjacentHexes.getBottom().getExtendedHexBorder().contains(status.currentCol, status.currentRow)) {
                        if(checkLOSOnHexsideRule(result, status, adjacentHexes.getBottom())) {
                            return;
                        }
                    }
                }

                // next row
                status.currentRow += status.rowDir;
            }

            // adjust variables for next column
            status.enter = status.exit;
            status.currentCol += status.colDir;

            // adjust variables for last column
            if (col + 1 == status.numCols) {
                status.exit = (double)status.targetY;
            }
            else {
                status.exit += status.deltaY;
            }
        }

        // set continuous slope result
        result.setContinuousSlope(status.continuousSlope);
    }

    /**
     * Check one of the hexes when LOS is on a hexside
     * @param result the LOS result
     * @param status the LOS status
     * @param hex the hex to check
     * @return true if LOS is blocked
     */
    protected boolean checkLOSOnHexsideRule (LOSResult result, LOSStatus status, Hex hex) {

        status.tempHex = hex;

        // we can ignore source/target hex for this rule
        if(hex.equals(status.sourceHex) || hex.equals(status.targetHex)) {
            return false;
        }

        // code added by DR to deal with cliff hexsides and higher level hex
        // can ignore hex and hexside terrain in higher level hex when LOS along cliff hexside
        if(status.currentTerrain.isCliff() && (hex.getBaseHeight()> status.targetElevation || hex.getBaseHeight() > status.sourceElevation)) {
            return false;
        }
        // force check of hexside terrain for hexes with inherent terrain
        // (e.g. for case where orchard hex has wall)
        if(status.currentTerrain.isHexsideTerrain() && hex.getCenterLocation().getTerrain().isInherentTerrain()) {
            if (applyLOSRules(status, result)) {
                return true;
            }
        }

        // ensure inherent terrain is not missed
        if(status.tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {

            // special case for PTO and water along hexside
            if(!(("Dense Jungle".equals(status.tempHex.getCenterLocation().getTerrain().getName()) ||
                  "Bamboo".equals(status.tempHex.getCenterLocation().getTerrain().getName())) &&
                   status.currentTerrain.getLOSCategory() == Terrain.LOSCategories.WATER))
            {
                status.currentTerrain = status.tempHex.getCenterLocation().getTerrain();
            }
        }

        return applyLOSRules(status, result);
    }

    /**
     * A simple class that encapsulates two adjacent hexes.
     */
    protected static class AdjacentHexes {
        private Hex top;
        private Hex bottom = null;

        public AdjacentHexes(Hex top, Hex bottom) {
            this.top = top;
            this.bottom = bottom;
        }

        /**
         * @return the top-most hex (i.e. has the lower row number)
         */
        public Hex getTop(){
            return top;
        }

        /**
         * @return the bottom-most hex (i.e. has the higher row number)
         */
        public Hex getBottom(){
            return bottom;
        }
    }

    /**
     * Finds the two hexes at a given range touched by a LOS traced along a hexside
     * @param status the LOS status
     * @param rangeHex the range from the source hex
     * @return the two adjacent hexes - null if LOS not on a hexside
     */
    protected AdjacentHexes getAdjacentHexes(LOSStatus status, Hex rangeHex) {

        Hex sourceHex = status.source.getHex();

        // ensure we're truly on a hexside
        if(status.sourceHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow) ||
           status.targetHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow) ||
          (status.source.getHex().getCenterLocation().equals(status.source) &&  range(sourceHex, rangeHex)%2 == 0)) {
            return null;
        }

        for(int x = 0; x < 6; x++){
            Hex temp = getAdjacentHex(rangeHex, x);
            if(temp != null && temp.getExtendedHexBorder().contains(status.currentCol, status.currentRow)) {

                // ignore any pairs that include source/target hex
                if(!temp.equals(status.sourceHex) && !temp.equals(status.targetHex)){
                    return new AdjacentHexes(rangeHex, temp);
                }
            }
        }

        return null;
    }

    /**
     * A class that allows the LOS status to be passed to various methods and classes
     * The constructor initializes the LOS status
     * Note that all properties are public to eliminate getter/setter clutter
     */
    private class LOSStatus {

        public Location source;
        public boolean useAuxSourceLOSPoint;
        public Location target;
        public boolean useAuxTargetLOSPoint;
        public LOSResult result;
        public VASL.LOS.VASLGameInterface VASLGameInterface;

        // location variables
        public int sourceX;
        public int sourceY;
        public int targetX;
        public int targetY;

        // direction variables
        public int colDir;
        public int rowDir;
        public int numCols;
        public double deltaY;

        // hindrance, etc. variables
        public boolean blocked = false;
        public String reason = "";

        public Terrain currentTerrain = null;
        public int currentTerrainHgt = 0;
        public int groundLevel = -9999;

        // hex data variables
        public Hex sourceHex;
        public Hex targetHex;
        public Hex currentHex;
        public Hex tempHex = null;
        public int sourceElevation;
        public int targetElevation;
        public int range;
        public int rangeToSource = 0;
        public int rangeToTarget;

        // bridge stuff
        public Shape bridgeArea = null;
        public Shape bridgeRoadArea = null;
        public Bridge bridge = null;

        public boolean continuousSlope = true;
        public boolean LOSLeavesBuilding;
        public boolean LOSis60Degree;
        public boolean LOSisHorizontal;

        private final static int NONE = -1;
        private int[] sourceExitHexsides = {NONE, NONE};
        private int[] targetEnterHexsides = {NONE, NONE};


        // need lots of variables to track state of LOS across hillocks
        public boolean startsOnHillock;
        public boolean endsOnHillock;
        public Hillock crossingHillock = null; // LOS is currently crossing this hillock
        public HashSet<Hillock> crossedHillocks = new HashSet<Hillock>();  // hillocks that have been crossed (starting hillock inclusive)
        public Hillock sourceAdjacentHillock = null;
        public Hillock targetAdjacentHillock = null;
        public Location firstWallCrossed = null;    // the first wall/hedge point/pixel touched by LOS
        public Point firstWallPoint = null;
        public Hex firstRubbleCrossed = null;       // ditto for rubble
        public Hex firstHalfLevelHindrance = null;  // ditto for the first half-level hindrance

        // LOS across slope hexside
        boolean slopes;

        // Exiting depression restriction placed when looking out of a depression
        // to a higher elevation where the "elevation difference <= range" restriction has not
        // been satisfied. Must be satisfied before leaving the depression.
        public boolean exitsSourceDepression;

        public Hex ignoreGroundLevelHex = null;

        // Entering depression restriction placed when looking into a depression
        // reverse of above
        public boolean entersTargetDepression;
        // added by DR to enable LOS along a depression
        public boolean depressionhexsidesreducerange = false;
        public Hex previousHex;

        // grid column entry/exit points
        public double enter;
        public double exit;
        public int currentCol;
        public int currentRow;

        public double slope;

        private LOSStatus(Location source, boolean useAuxSourceLOSPoint, Location target, boolean useAuxTargetLOSPoint, LOSResult result, VASLGameInterface VASLGameInterface) {

            this.source = source;
            this.useAuxSourceLOSPoint = useAuxSourceLOSPoint;
            this.target = target;
            this.useAuxTargetLOSPoint = useAuxTargetLOSPoint;
            this.result = result;
            this.VASLGameInterface = VASLGameInterface;

            // start and end points
            sourceX = useAuxSourceLOSPoint ? (int) source.getAuxLOSPoint().getX() : (int) source.getLOSPoint().getX();
            sourceY = useAuxSourceLOSPoint ? (int) source.getAuxLOSPoint().getY() : (int) source.getLOSPoint().getY();
            targetX = useAuxTargetLOSPoint ? (int) target.getAuxLOSPoint().getX() : (int) target.getLOSPoint().getX();
            targetY = useAuxTargetLOSPoint ? (int) target.getAuxLOSPoint().getY() : (int) target.getLOSPoint().getY();

            // direction variables
            colDir = targetX - sourceX < 0 ? -1 : 1;
            rowDir = targetY - sourceY < 0 ? -1 : 1;
            numCols = Math.abs(targetX - sourceX) + 1;

            // hex data variables
            sourceHex = source.getHex();
            targetHex = target.getHex();
            currentHex = sourceHex;
            sourceElevation = sourceHex.getBaseHeight() + source.getBaseHeight();
            targetElevation = targetHex.getBaseHeight() + target.getBaseHeight();
            range = range(sourceHex, targetHex);
            rangeToTarget = range;

            //code change DR to redo in-factory LOS
            LOSLeavesBuilding = !sourceHex.getCenterLocation().getTerrain().isBuilding();

            // "rise" per grid column
            deltaY = ((double) targetY - (double) sourceY) / (double) numCols;

            // grid column entry/exit points
            enter = (double)sourceY;
            exit = enter + deltaY;

            // initialize some result variables
            result.setRange(rangeToTarget);

            // LOS slope variables
            LOSisHorizontal = (sourceY == targetY);
            result.setLOSisHorizontal(LOSisHorizontal);
            slope = Math.abs((double) (sourceY - targetY) / (double) (sourceX - targetX));


            // set the tolerance to compensate for "fuzzy" geometry of VASL boards
            // DR added >5 test as need larger tolerance at short range on RB board
            //TODO - this is a kludge and fails at very long ranges - replace with algorithm
            double tolerance = 0.05;
            if (range >= 5 && range <= 15) {

                tolerance = 0.03;
            }
            else if (range > 15) {

                tolerance = 0.015;
            }
            LOSis60Degree = Math.abs(slope - StrictMath.tan(Math.toRadians(60.0))) < tolerance;
            result.setLOSis60Degree(LOSis60Degree);

            // set the result with the slope information
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
            setEnterExitHexsides();

            // DR  revised the depression restiction code below and moved it from above to make use LOSisHorizontal, etc

            // Exiting depression restriction placed when looking out of a depression
            // to a higher elevation where the "elevation difference <= range" restriction has not
            // been satisfied. Must be satisfied before leaving the depression.
            double sourceadj=0;
            double targetadj=0;

            if(source.getTerrain().isRooftop() ) {
                sourceadj=-0.5;
            }
            if(target.getTerrain().isRooftop() ) {
                targetadj=-0.5;
            }
            exitsSourceDepression =
                    source.isDepressionTerrain() &&
                            (((targetElevation+ targetadj) - (sourceElevation+sourceadj) > 0 &&
                            (targetElevation+ targetadj) - (sourceElevation+sourceadj) < range) ||
                    ((LOSis60Degree || LOSisHorizontal) && exitsDepressionTerrainHexside() ) ||
                    (targetElevation+targetadj == sourceElevation+sourceadj) ||
                    (sourceElevation+ sourceadj > targetElevation+targetadj));

            // Entering depression restriction placed when looking into a depression
            // reverse of above
            entersTargetDepression =
                    target.isDepressionTerrain() &&
                            (((sourceElevation+ sourceadj) - (targetElevation+targetadj) > 0 &&
                            (sourceElevation+sourceadj) - (targetElevation+targetadj) < range) ||
                    ((LOSis60Degree || LOSisHorizontal) && entersDepressionTerrainHexside()) ||
                    (targetElevation+targetadj == sourceElevation+sourceadj) ||
                    (targetElevation+targetadj > sourceElevation+sourceadj));

            // slope rules are in effect if the higher location is up-slope
            slopes = (exitsSlopeHexside() && sourceElevation >= targetElevation) ||
                     (entersSlopeHexside() && targetElevation >= sourceElevation);

            // initialize hillock status
            startsOnHillock = HILLOCK.equals(source.getTerrain().getName()) || slopes;
            endsOnHillock = HILLOCK.equals(target.getTerrain().getName());
            if(startsOnHillock) {
                crossingHillock = getHillock(sourceHex);
            }
            setAdjacentToHillock();
        }

        /**
         * @return true if the LOS exits the source hex via a slope hexside
         */
        public boolean exitsSlopeHexside() {

            if(sourceExitHexsides[0] != NONE && sourceHex.hasSlope(sourceExitHexsides[0])) {
                return true;
            }
            else if(sourceExitHexsides[1] != NONE && sourceHex.hasSlope(sourceExitHexsides[1])) {
                return true;
            }
            return false;
        }

        /**
         * @return true if the LOS enters the target hex via a slope hexside
         */
        public boolean entersSlopeHexside() {

            if(targetEnterHexsides[0] != NONE && targetHex.hasSlope(targetEnterHexsides[0])) {
                return true;
            }
            else if(targetEnterHexsides[1] != NONE && targetHex.hasSlope(targetEnterHexsides[1])) {
                return true;
            }
            return false;
        }
        /**
         * @return true if the LOS exits the source hex via a depression terrain hexside; added by DR
         */
        public boolean exitsDepressionTerrainHexside() {
            if (LOSis60Degree || LOSisHorizontal) {
                return false;
            }
            if(sourceExitHexsides[0] != NONE && sourceHex.getHexsideLocation(sourceExitHexsides[0]).isDepressionTerrain()) {
                return true;
            }
            else if(sourceExitHexsides[1] != NONE && sourceHex.getHexsideLocation(sourceExitHexsides[1]).isDepressionTerrain()) {
                return true;
            }
            return false;
        }

        /**
         * @return true if the LOS enters the target hex via a depression terrain hexside; added by DR
         */
        public boolean entersDepressionTerrainHexside() {
            if (LOSis60Degree || LOSisHorizontal) {
                return false;
            }
            if(targetEnterHexsides[0] != NONE && targetHex.getHexsideLocation(targetEnterHexsides[0]).isDepressionTerrain()) {
                return true;
            }
            else if(targetEnterHexsides[1] != NONE && targetHex.getHexsideLocation(targetEnterHexsides[1]).isDepressionTerrain()) {
                return true;
            }
            return false;
        }

        /**
         * Adjusts the hillock status variables for a new hex
         */
        private void setHillockStatus() {

            final Hillock hillock = getHillock(currentHex);

            // add hillocks crossed as appropriate
            if(crossingHillock != null && hillock == null){

                crossedHillocks.add(crossingHillock);
                crossingHillock = null;
            }
            else if(crossingHillock == null && hillock != null) {

                crossingHillock = hillock;
            }

            // because rubble is inherent terrain we can put this check here
            if(STONE_RUBBLE.equals(currentTerrain.getName())  && STONE_RUBBLE.equals(currentHex.getCenterLocation().getTerrain().getName())||
               WOODEN_RUBBLE.equals(currentTerrain.getName()) && WOODEN_RUBBLE.equals(currentHex.getCenterLocation().getTerrain().getName())) {
                if(firstRubbleCrossed == null) {
                    firstRubbleCrossed = currentHex;
                }
            }
        }

        /**
         * Set the adjacent hillocks variables
         */
        private void setAdjacentToHillock() {

            if(!startsOnHillock) {
                if(sourceExitHexsides[0] != NONE &&
                        getAdjacentHex(sourceHex, sourceExitHexsides[0]) != null &&
                        HILLOCK.equals(getAdjacentHex(sourceHex, sourceExitHexsides[0]).getCenterLocation().getTerrain().getName())) {
                    sourceAdjacentHillock = getHillock(getAdjacentHex(sourceHex, sourceExitHexsides[0]));
                }
                if(sourceExitHexsides[1] != NONE &&
                        getAdjacentHex(sourceHex, sourceExitHexsides[0]) != null &&
                        HILLOCK.equals(getAdjacentHex(sourceHex, sourceExitHexsides[1]).getCenterLocation().getTerrain().getName())) {
                    sourceAdjacentHillock = getHillock(getAdjacentHex(sourceHex, sourceExitHexsides[1]));
                }
            }

            if(!endsOnHillock) {
                if(targetEnterHexsides[0] != NONE &&
                        getAdjacentHex(targetHex, targetEnterHexsides[0]) != null &&
                        HILLOCK.equals(getAdjacentHex(targetHex, targetEnterHexsides[0]).getCenterLocation().getTerrain().getName())) {
                    targetAdjacentHillock = getHillock(getAdjacentHex(targetHex, targetEnterHexsides[0]));
                }
                if(targetEnterHexsides[1] != NONE &&
                        getAdjacentHex(targetHex, targetEnterHexsides[1]) != null &&
                        HILLOCK.equals(getAdjacentHex(targetHex, targetEnterHexsides[1]).getCenterLocation().getTerrain().getName())) {
                    targetAdjacentHillock = getHillock(getAdjacentHex(targetHex, targetEnterHexsides[1]));
                }
            }
        }

        /**
         * Sets which hexsides the LOS enters/exits the source/target hexes
         * Will be two values if exiting via hexspine, otherwise one
         */
        private void setEnterExitHexsides() {


            boolean sourceSet = false;
            boolean targetSet = false;
            if(source.isCenterLocation()) {
                
                sourceExitHexsides = getExitFromCenterHexsides();
                sourceSet = true;
            }

            if(target.isCenterLocation()) {

                // will be the opposite of the enter logic
                targetEnterHexsides = getExitFromCenterHexsides();
                targetEnterHexsides[0] = Hex.getOppositeHexside(targetEnterHexsides[0]);
                targetEnterHexsides[1] = Hex.getOppositeHexside(targetEnterHexsides[1]);
                targetSet = true;

            }
            if(!sourceSet) {

                // if more than two hexsides are touched and the LOS start/ends on a vertex then the vertex hexsides can be thrown out
                HashSet<Integer> hexsides = getHexsideCrossed(sourceHex);
                if(hexsides.size() > 2) {
                    removeVertexHexsides(sourceHex, hexsides);
                }

                Iterator<Integer> i = hexsides.iterator();
                if(i.hasNext()) {
                    sourceExitHexsides[0] = i.next();
                    if(i.hasNext()) {
                        sourceExitHexsides[1] = i.next();
                    }
                }
            }
            if(!targetSet){

                // if more than two hexsides are touched and the LOS start/ends on a vertex then the vertex hexsides can be thrown out
                HashSet<Integer> hexsides = getHexsideCrossed(targetHex);
                if(hexsides.size() > 2) {
                    removeVertexHexsides(targetHex, hexsides);
                }

                Iterator<Integer> i = hexsides.iterator();
                if(i.hasNext()) {
                    targetEnterHexsides[0] = i.next();
                    if(i.hasNext()) {
                        targetEnterHexsides[1] = i.next();
                    }
                }
            }
        }

        /**
         * @return the set of hexsides in the source hex touched by the LOS if source is the center location
         */
        private int[] getExitFromCenterHexsides() {

            final double slope = (double) (sourceY - targetY) / (double) (sourceX - targetX);

            int[] exitHexsides = {NONE, NONE};
            
            if(slope == Double.POSITIVE_INFINITY) {
                exitHexsides[0] = 0;
            }
            else if(slope == Double.NEGATIVE_INFINITY) {
                exitHexsides[0] = 3;
            }
            else if(LOSisHorizontal) {
                if(colDir == 1) {
                    exitHexsides[0] = 1;
                    exitHexsides[1] = 2;
                }
                else {
                    exitHexsides[0] = 4;
                    exitHexsides[1] = 5;
                }
            }
            else if (LOSis60Degree) {
                if(colDir == 1) {
                    if (slope > 0) {
                        exitHexsides[0] = 2;
                        exitHexsides[1] = 3;
                    }
                    else {
                        exitHexsides[0] = 0;
                        exitHexsides[1] = 1;
                    }
                }
                else  {
                    if (slope > 0) {
                        exitHexsides[0] = 5;
                        exitHexsides[1] = 0;
                    }
                    else {
                        exitHexsides[0] = 3;
                        exitHexsides[1] = 4;
                    }
                }
            }
            else {

                HashSet<Integer> hexsides = getHexsideCrossed(sourceHex);
                Iterator<Integer> i = hexsides.iterator();
                if(i.hasNext()) {
                    exitHexsides[0] = i.next();
                }
            }

            return exitHexsides;
        }

        /**
         * @param hex the hex
         * @return returns the set of hexsides touched by the LOS
         */
        private HashSet<Integer> getHexsideCrossed(Hex hex) {

            // convert hexsides to lines and check for intersection
            Line2D.Double losLine = new Line2D.Double(sourceX, sourceY, targetX, targetY);

            // This algorithm courtesy of http://stackoverflow.com/questions/5184815/java-intersection-point-of-a-polygon-and-line
            final PathIterator polyIt = hex.getExtendedHexBorder().getPathIterator(null); //Getting an iterator along the polygon path
            final double[] coords = new double[6];      // Double array with length 6 needed by iterator
            final double[] firstCoords = new double[2]; // First point (needed for closing polygon path)
            final double[] lastCoords = new double[2];  // Previously visited point
            polyIt.currentSegment(firstCoords);         // Getting the first coordinate pair
            lastCoords[0] = firstCoords[0];             // Priming the previous coordinate pair
            lastCoords[1] = firstCoords[1];
            polyIt.next();

            HashSet<Integer> hexsides = new HashSet<Integer>();
            int hexside = 0;
            while(!polyIt.isDone()) {
                final int type = polyIt.currentSegment(coords);
                switch(type) {
                    case PathIterator.SEG_LINETO : {
                        final Line2D.Double currentLine = new Line2D.Double(lastCoords[0], lastCoords[1], coords[0], coords[1]);
                        if(currentLine.intersectsLine(losLine)) {
                            hexsides.add(hexside);
                        }
                        lastCoords[0] = coords[0];
                        lastCoords[1] = coords[1];
                        break;
                    }
                    case PathIterator.SEG_CLOSE : {
                        final Line2D.Double currentLine = new Line2D.Double(coords[0], coords[1], firstCoords[0], firstCoords[1]);
                        if(currentLine.intersectsLine(losLine)) {
                            hexsides.add(hexside);
                        }
                        break;
                    }
                    default : {
                        return null;
                    }
                }
                polyIt.next();
                hexside++;
            }
            return hexsides;
        }

        /**
         * Removes hexsides whose vertex starts/ends on the LOS end point
         * @param hexsides
         */
        private void removeVertexHexsides(Hex hex, HashSet<Integer> hexsides) {

            Iterator iterator = hexsides.iterator();
            while(iterator.hasNext()){
                Integer i = (Integer) iterator.next();
                if((hex.getHexsideLocation(i).getLOSPoint().x    == sourceX && hex.getHexsideLocation(i).getLOSPoint().y == sourceY) ||
                   (hex.getHexsideLocation(i).getAuxLOSPoint().x == sourceX && hex.getHexsideLocation(i).getAuxLOSPoint().y == sourceY) ||
                   (hex.getHexsideLocation(i).getLOSPoint().x    == targetX && hex.getHexsideLocation(i).getLOSPoint().y == targetY) ||
                   (hex.getHexsideLocation(i).getAuxLOSPoint().x == targetX && hex.getHexsideLocation(i).getAuxLOSPoint().y == targetY)
                 ) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Applies the LOS rules to a single point; adjusts the status if the LOS enters a new hex
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean applyLOSRules(LOSStatus status, LOSResult result) {


        // if there's a terrain counter in the hex use that terrain instead
        if(status.VASLGameInterface != null && status.VASLGameInterface.getTerrain(status.tempHex) != null) {
            status.currentTerrain = status.VASLGameInterface.getTerrain(status.tempHex);
        }

        status.currentTerrainHgt = status.currentTerrain.getHeight();
        HashSet<Integer> hexsides;
        // code added by  DR to enable RB rr embankments
        hexsides = status.getHexsideCrossed(status.tempHex);
        // code added by DR to enable roofless factory hexes
        Terrain previousTerrain = null;
        // are we in a new hex?
        if (!status.tempHex.equals(status.currentHex)) {
            //store terrain in previous hex (needed when checking roofless hexes and depression hexsides; added by DR)
            boolean reggie=false;
            previousTerrain = status.currentHex.getCenterLocation().getTerrain();
            if(status.previousHex ==null) {
                status.previousHex = status.currentHex;
            }
            else if(!status.previousHex.equals(status.tempHex)){
                status.previousHex = status.currentHex;
            }
            else {
                reggie = true;
            }
            status.currentHex = status.tempHex;
            status.rangeToSource = range(status.currentHex, status.sourceHex);
            status.rangeToTarget = range(status.currentHex, status.targetHex);

            // set the bridge variables
            status.bridge = status.currentHex.getBridge();

            if (status.bridge != null) {

                // set bridge area
                status.bridgeArea = status.bridge.getShape();
                status.bridgeRoadArea = status.bridge.getRoadShape();
            }

            // still continuous slope?
            if (Math.abs(status.sourceElevation - status.currentHex.getBaseHeight()) != status.rangeToSource) {

                status.continuousSlope = false;
            }
            int rangeadj = 0;
            double sourceadj=0;
            double targetadj=0;

            if(status.source.getTerrain().isRooftop() ) {
                sourceadj=-0.5;
            }
            if(status.target.getTerrain().isRooftop() ) {
                targetadj = -0.5;
            }
            boolean followsdepression = true;
            // lift the depression exit restriction?
            if (status.exitsSourceDepression && !reggie) {

                // LOS cannot follow along a depression if it is along hexside
                if (!(status.LOSisHorizontal) && !(status.LOSis60Degree)) {
                    Location testlocation=status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
                    if (status.targetElevation + targetadj > status.sourceElevation+ sourceadj) {
                        HashSet<Integer> testhexsides;
                        testhexsides = status.getHexsideCrossed(status.currentHex);
                        if (testhexsides != null && !testhexsides.isEmpty()) {
                            for (Integer hexside : testhexsides) {
                                if (status.currentHex.getHexsideLocation(hexside).equals(testlocation)) {
                                    if (status.currentHex.getHexsideLocation(hexside).isDepressionTerrain()) {
                                        rangeadj = status.rangeToSource - 1;
                                        if (!((status.targetElevation +targetadj) - (status.sourceElevation+ sourceadj) >= status.rangeToSource - rangeadj)) {
                                            followsdepression = false;
                                        }
                                        else {
                                            status.ignoreGroundLevelHex = status.currentHex;
                                        }
                                    }
                                    else {
                                        int depressionadj = 0;
                                        if (status.currentHex.isDepressionTerrain()) {
                                            depressionadj = 1;
                                        }
                                        else {
                                            if (status.rangeToSource==1 && status.currentHex.getBaseHeight()<= status.sourceElevation) {
                                                depressionadj=1;
                                            }
                                        }
                                        // equal range adjustment required
                                        int equalrangeadj=0;
                                        if (followsdepression && status.rangeToSource==range(status.sourceHex, status.previousHex)) {
                                            equalrangeadj=1;
                                        }
                                        if (!status.targetHex.equals(status.currentHex) &&
                                                ((status.currentHex.getBaseHeight() + depressionadj + status.currentTerrainHgt >= status.targetElevation + targetadj) || (status.currentHex.getBaseHeight() + depressionadj + status.currentTerrainHgt > status.sourceElevation + sourceadj)) &&
                                                (!((status.targetElevation + targetadj) - (status.sourceElevation + sourceadj) >= status.range -(status.rangeToSource-1+ equalrangeadj)))) {
                                            followsdepression = false;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else {
                    followsdepression=false;
                }
            }
            if (status.entersTargetDepression && !reggie) {
                Location testlocation=status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
                if (!(status.LOSisHorizontal) && !(status.LOSis60Degree)) {
                    if ((status.sourceElevation + sourceadj > status.targetElevation + targetadj) && !(status.currentHex.equals(status.targetHex))) {
                        HashSet<Integer> testhexsides;
                        testhexsides = status.getHexsideCrossed(status.currentHex);
                        if (testhexsides != null && !testhexsides.isEmpty()) {
                            for (Integer hexside : testhexsides) {
                                if (!status.currentHex.getHexsideLocation(hexside).equals(testlocation)) {
                                    if (status.currentHex.getHexsideLocation(hexside).isDepressionTerrain()) {
                                        rangeadj = status.rangeToSource - 1;
                                        if (!((status.sourceElevation + sourceadj) - (status.targetElevation + targetadj) >= status.rangeToSource - rangeadj)) {
                                            followsdepression = false;
                                        }
                                        else {
                                            status.ignoreGroundLevelHex = status.currentHex;
                                        }
                                    } else {
                                        int depressionadj = 0;
                                        if (status.currentHex.isDepressionTerrain()) {
                                            depressionadj = 1;
                                        }
                                        // equal range adjustment required
                                        int equalrangeadj=0;
                                        int nexthexside=0;
                                        for (Integer loophexside : testhexsides ) {
                                            if ((hexside==loophexside)) {
                                                nexthexside=loophexside;
                                            }
                                        }
                                        if (followsdepression && status.rangeToSource==range(status.sourceHex, getAdjacentHex(status.currentHex, nexthexside))) {
                                            equalrangeadj=1;
                                        }
                                        if (!status.sourceHex.equals(status.currentHex) &&
                                                ((status.currentHex.getBaseHeight() + depressionadj + status.currentTerrainHgt >= status.targetElevation + targetadj) || (status.currentHex.getBaseHeight() + depressionadj + status.currentTerrainHgt > status.sourceElevation+ sourceadj)) &&
                                                (!((status.sourceElevation + sourceadj) - (status.targetElevation + targetadj) >= status.range -(status.rangeToTarget-1+ equalrangeadj)))) {
                                            followsdepression = false;
                                        }
                                        else {
                                            followsdepression=true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    followsdepression = false;
                }
            }
            if (followsdepression) {
                status.depressionhexsidesreducerange = true;
            } else {
                status.depressionhexsidesreducerange = false;

            }

            // set the hillocks status
            status.setHillockStatus();
        }

        // check the LOS rules
        if (checkDepressionRule(status, result)) {
            return true;
        }
        if (checkBuildingRestrictionRule(status, result, previousTerrain)) {
            return true;
        }
        if (status.currentTerrain.isHexsideTerrain() && !status.currentTerrain.isCliff()) {
            if (checkHexsideTerrainRule(status, result)) {
                return true;
            }
        }
        // code added by DR to handle RB rrembankments
        else {
            if (checkRBrrembankments(status, result, hexsides)) {
                return true;
            }
        }

        if(checkHexSmokeRule(status, result)) {
            return true;
        }
        if(checkVehicleHindranceRule(status, result)) {
            return true;
        }
        if(checkOBAHindranceRule(status, result)) {
            return true;
        }

        // We can ignore the current hex if we're in source/target and LOS is from center location
        // (non-center location implies bypass and LOS may be blocked)
        if ((!status.currentHex.equals(status.sourceHex) && !status.currentHex.equals(status.targetHex)) ||
             (status.currentHex.equals(status.sourceHex) && !status.currentTerrain.isOpen() && !status.source.isCenterLocation()) ||
             (status.currentHex.equals(status.targetHex) && !status.currentTerrain.isOpen() && !status.target.isCenterLocation())
                ) {

            // ignore inherent terrain that "spills" into adjacent hex
            if(status.currentTerrain.isInherentTerrain() && !status.currentHex.getCenterLocation().getTerrain().equals(status.currentTerrain)) {

                // ignore this check when terrain counter is present
                if(status.VASLGameInterface == null  || (status.VASLGameInterface != null && status.VASLGameInterface.getTerrain(status.currentHex) == null)){
                    return false;
                }
            }

            // Check LOS rules and return if blocked
            if (checkBridgeHindranceRule(status, result)) {
                return true;
            }
            if (checkGroundLevelRule(status, result)) {
                return true;
            }
            if (checkSplitTerrainRule(status, result)) {
                return true;
            }
            if (checkHalfLevelTerrainRule(status, result)) {
                return true;
            }
            if (checkTerrainIsHigherRule(status, result)) {
                return true;
            }
            if (checkTerrainHeightRule(status, result)) {
                return true;
            }
            if (checkBlindHexRule(status, result)) {
                return true;
            }
            if(checkHillockRule(status, result)) {
                return true;
            }
        }

        // set results if blocked
        if (status.blocked) {
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
        }
        return false;
    }

    /**
     * Applies the LOS rules to LOS within the same hex
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkSameHexRule(LOSStatus status, LOSResult result) {
        // in same hex?
        if (status.source.getHex().equals(status.target.getHex())) {

            // Set the range
            result.setRange(0);

            // either unit in the hex center?
            if (status.source.isCenterLocation() ||
                    status.target.isCenterLocation()) {

                // if both locations are building, ensure difference in levels is <= 1 and stairway
                if (status.source.getTerrain().isBuildingTerrain() && status.target.getTerrain().isBuildingTerrain()) {
                    if (Math.abs(status.source.getBaseHeight() - status.target.getBaseHeight()) > 1 ||
                            !status.source.getHex().hasStairway()) {

                        result.setBlocked(
                                (int) status.source.getLOSPoint().getX(),
                                (int) status.source.getLOSPoint().getY(),
                                "Crosses building level or no stairway");
                        return true;
                    }
                }

                // source on a bridge and target under bridge, etc?
                if ((status.source.getTerrain().isBridge() && status.target.isCenterLocation()) ||
                        (status.target.getTerrain().isBridge() && status.source.isCenterLocation())) {

                    result.setBlocked(
                            (int) status.source.getLOSPoint().getX(),
                            (int) status.source.getLOSPoint().getY(),
                            "Cannot see location under the bridge");
                    return true;
                }

                // otherwise clear
                return true;
            }
        }
        return false;
    }

    /**
     * Checks smoke hindrances for in-hex LOS
     * @param status the LOS status
     * @param result the LOS results
     * @return true if the LOS is blocked
     */
    protected boolean checkSameHexSmokeRule(LOSStatus status, LOSResult result) {

        if (status.source.getHex().equals(status.target.getHex())) {
            if(status.VASLGameInterface != null) {

                HashSet<Smoke> hexSmoke = status.VASLGameInterface.getSmoke(status.source.getHex());
                if (hexSmoke != null && !hexSmoke.isEmpty()) {

                    int hindrance = 0;
                    for (Smoke s: hexSmoke) {

                        if(locationInSmoke(status.source, s)) {
                            hindrance += s.getHindrance() + 1;
                        }
                        if(locationInSmoke(status.target, s)) {
                            hindrance += s.getHindrance();
                        }

                    }

                    if(hindrance >= 6) {
                        result.setBlocked(status.sourceX, status.sourceY, "Hindrance total of six or more (B.10)");
                    }
                    else {
                        result.addSmokeHindrance(status.sourceHex, hindrance, status.sourceX, status.sourceY);
                    }
                }
            }
        }
        return result.isBlocked();
    }

    /**
     * @param location a location
     * @param smoke a smoke counter
     * @return true if location is in the smoke
     */
    protected static boolean locationInSmoke(Location location, Smoke smoke) {
        return location.getBaseHeight() >= smoke.getLocation().getBaseHeight() &&
               location.getBaseHeight() < smoke.getLocation().getBaseHeight() + smoke.getHeight();
    }

    /**
     * Applies hindrances to the LOS for any smoke in the given hex
     *
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkHexSmokeRule(LOSStatus status, LOSResult result) {

        // check for smoke in source hex here
        if(status.VASLGameInterface != null) {

            Hex hex = status.currentHex;

            HashSet<Smoke> hexSmoke = status.VASLGameInterface.getSmoke(hex);
            if (hexSmoke != null && !hexSmoke.isEmpty()) {

                int hindrance = 0;
                boolean sourceInSmoke = false;
                for (Smoke s: hexSmoke) {

                    // in source hex?
                    if(hex.equals(status.sourceHex)) {
                        if(locationInSmoke(status.source, s)){
                            hindrance += s.getHindrance() + 1;
                            sourceInSmoke = true;
                        }
                        else {
                            // shooting down through smoke?
                            if(status.source.getAbsoluteHeight() == s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                               status.target.getAbsoluteHeight() < status.source.getAbsoluteHeight() ){
                                hindrance += s.getHindrance();
                            }
                        }
                    }
                    // in target hex?
                    else if(hex.equals(status.targetHex)) {
                        if(locationInSmoke(status.source, s)){
                            hindrance += s.getHindrance();
                        }
                        // shooting down through smoke?
                        else if(status.target.getAbsoluteHeight() == s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                           status.source.getAbsoluteHeight() < status.target.getAbsoluteHeight() ){
                            hindrance += s.getHindrance();
                        }
                    }
                    else {
                        // source and target level must be within the smoke elevation
                        if (status.source.getAbsoluteHeight() >= s.getLocation().getAbsoluteHeight() &&
                            status.source.getAbsoluteHeight() < s.getLocation().getAbsoluteHeight() + s.getHeight() &&
                            status.target.getAbsoluteHeight() >= s.getLocation().getAbsoluteHeight() &&
                            status.target.getAbsoluteHeight() < s.getLocation().getAbsoluteHeight() + s.getHeight()){

                            hindrance += s.getHindrance();
                        }
                        // smoke creates "blind hex"
                        else if (isBlindHex(status, s.getHeight())){

                            hindrance += s.getHindrance();
                        }
                    }
                }
                if(hindrance > 0) {

                    // the max hindrance per location is 3 unless the source location is in the smoke
                    if(sourceInSmoke) {
                        hindrance = hindrance > 4 ? 4 : hindrance;
                    }
                    else {
                        hindrance = hindrance > 3 ? 3 : hindrance;
                    }

                    result.addSmokeHindrance(hex, hindrance, status.currentCol, status.currentRow);
                    if(result.isBlocked()){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Applies hindrances to the LOS for any vehicle in the given hex
     *
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkVehicleHindranceRule(LOSStatus status, LOSResult result) {

        // check for vehicles in source hex here
        if(status.VASLGameInterface != null) {

            Hex hex = status.currentHex;

            HashSet<Vehicle> vehicles = status.VASLGameInterface.getVehicles(hex);
            if (vehicles != null && !vehicles.isEmpty()) {

                int hindrance = 0;
                for (Vehicle v: vehicles) {

                    // skip source/target hex
                    if(!hex.equals(status.sourceHex) && !hex.equals(status.targetHex)) {

                        // vehicle must be same elevation as both source and target
                        if(status.source.getAbsoluteHeight() == status.target.getAbsoluteHeight() &&
                           status.source.getAbsoluteHeight() == v.getLocation().getAbsoluteHeight()){

                            // no hindrance if up-slope or both units on hillocks (unless, in the later case, vehicle is also on hillock)
                            if(status.slopes || (status.startsOnHillock && status.endsOnHillock && status.crossingHillock == null)) {
                                return false;
                            }

                            // if vehicle in bypass the LOS must cross the bypassed hexside
                            if(v.getLocation().isCenterLocation() || v.getLocation().equals(hex.getNearestLocation(status.currentCol, status.currentRow))) {


                                // both source and target must have an LOS to the vehicle
                                LOSResult result1 = new LOSResult();
                                LOSResult result2 = new LOSResult();
                                LOS(status.source, status.useAuxSourceLOSPoint, v.getLocation(), false, result1, status.VASLGameInterface);
                                LOS(status.target, status.useAuxTargetLOSPoint, v.getLocation(), false, result2, status.VASLGameInterface);
                                if(!result1.isBlocked() && !result2.isBlocked())
                                {
                                    hindrance++;
                                }
                            }
                        }
                    }
                }
                if(hindrance > 0) {
                    result.addVehicleHindrance(hex, hindrance, status.currentCol, status.currentRow);
                    if(result.isBlocked()){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Applies hindrances to the LOS for OBA in the given hex
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkOBAHindranceRule(LOSStatus status, LOSResult result) {

        // check for OBA in current hex
        if(status.VASLGameInterface != null) {

            HashSet<OBA> obaList = status.VASLGameInterface.getOBA();
            if (obaList != null && !obaList.isEmpty()) {

                for (OBA oba: obaList) {

                    // must be within the blast area
                    if(range(status.currentHex, oba.getHex()) <= oba.getBlastAreaRadius()){

                        // source or target are within the OBA
                        if(locationInOBA(status.source, oba) || locationInOBA(status.target, oba)) {

                            result.addOBAHindrance(oba, status.currentCol, status.currentRow);
                            if(result.isBlocked()){
                                return true;
                            }
                        }
                        // source and target are within the OBA elevation
                        else if(status.source.getAbsoluteHeight() >= oba.getHex().getCenterLocation().getAbsoluteHeight() &&
                                status.source.getAbsoluteHeight() < oba.getHex().getCenterLocation().getAbsoluteHeight() + oba.getBlastHeight() &&
                                status.target.getAbsoluteHeight() >= oba.getHex().getCenterLocation().getAbsoluteHeight() &&
                                status.target.getAbsoluteHeight() < oba.getHex().getCenterLocation().getAbsoluteHeight() + oba.getBlastHeight()){


                            result.addOBAHindrance(oba, status.currentCol, status.currentRow);
                            if(result.isBlocked()){
                                return true;
                            }
                        }
                        // OBA creates "blind hex"
                        else if (isBlindHex(status, oba.getBlastHeight())){

                            result.addOBAHindrance(oba, status.currentCol, status.currentRow);
                            if(result.isBlocked()){
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param location the location
     * @param oba the OBA counter
     * @return true if location is within the OBA
     */
    private boolean locationInOBA(Location location, OBA oba) {

        return  range(oba.getHex(), location.getHex()) <= oba.getBlastAreaRadius() &&
                location.getBaseHeight() >= oba.getHex().getCenterLocation().getBaseHeight() &&
                location.getBaseHeight() < oba.getHex().getCenterLocation().getBaseHeight() + oba.getBlastHeight();
    }

    /**
     * Ensures LOS leaves a building before unit in the same building at different elevations can see each other
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkBuildingRestrictionRule(LOSStatus status, LOSResult result, Terrain previousTerrain) {

        // blocked LOS leaving a building?
        // code added by DR for Rooftops, which are created at full level and need to be at lower level for LOS purposes
        int rooftopadj=0;
        if(status.source.getTerrain().isRooftop() || status.target.getTerrain().isRooftop() ) {
            rooftopadj=-1;
        }

        if (!status.LOSLeavesBuilding && !status.currentHex.equals(status.sourceHex)) {
            if (status.currentTerrain.isBuildingTerrain()) {  // LOS is within a non-factory building; not same hex
                if (status.sourceElevation != status.targetElevation) {
                    status.reason = "LOS must leave the building before leaving the source hex to see a location with a different elevation (A6.8 Example 2)";
                    status.blocked = true;
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }
            }
            else if (!status.LOSLeavesBuilding && (status.target.getTerrain().isFactoryTerrain() | status.source.getTerrain().isFactoryTerrain())  ) {  //LOS is within a factory
                // no rooftop LOS if source/target are not in same hex (unless current hex is roofless or first roofed hex)
                if(!status.sourceHex.equals(status.targetHex)) {  //same-hex LOS is ok in factory
                    if(status.LOSis60Degree || status.LOSisHorizontal) {  //need one of the adjacent hexes to be roofless
                        if(status.rangeToSource % 2 ==0){ // at even range do normal hex change
                            return isRooftopLOSBlocked(status.currentHex, status.currentTerrain, status.currentTerrainHgt, status, result, rooftopadj);
                        }
                        else {  // range is odd so need one of the adjacent hexes to be roofless
                            if (!isRooftopLOSBlocked(status.currentHex, status.currentTerrain, status.currentTerrainHgt, status, result, rooftopadj)) {
                                return false;  // if first hex is roofless then LOS not blocked
                            }
                            else {  //need to check if second hex is roofless
                                status.reason="";  //reset variables
                                status.blocked=false;
                                result.resetreportingonly();
                                int testhexside = status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentCol, status.currentRow));
                                Hex testhex = getAdjacentHex(status.currentHex, testhexside);
                                return isRooftopLOSBlocked(testhex, testhex.getCenterLocation().getTerrain(), testhex.getCenterLocation().getTerrain().getHeight(), status, result, rooftopadj);
                            }
                        }
                   }
                    else {
                        return isRooftopLOSBlocked(status.currentHex, status.currentTerrain, status.currentTerrainHgt, status, result, rooftopadj);
                    }
                }
            }
        }
        return false;
    }

    private boolean isRooftopLOSBlocked(Hex passcurrentHex, Terrain passcurrentTerrain, int passcurrentTerrainHgt, LOSStatus status, LOSResult result, int rooftopadj) {
        if ((status.source.getTerrain().isRooftop() && !status.target.getTerrain().isRooftop()) &&  //LOS is from rooftop down
                (!passcurrentHex.getCenterLocation().getTerrain().isRoofless() && !passcurrentHex.equals(status.targetHex))) {
            //each hex (except source and target) along LOS must be roofless; if not LOS blocked
            status.reason = "LOS from/to Factory Rooftop within Factory only exists in same hex ground level location (B23.87)";
            status.blocked = true;
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
        } else if ((status.target.getTerrain().isRooftop() && !status.source.getTerrain().isRooftop()) &&  // LOS is up to rooftop
                ((!passcurrentHex.getCenterLocation().getTerrain().isRoofless() && status.rangeToSource==1 && !status.sourceHex.getCenterLocation().getTerrain().isRoofless()) ||
                        (!passcurrentHex.getCenterLocation().getTerrain().isRoofless() &&  !passcurrentHex.equals(status.targetHex) && (passcurrentTerrainHgt+ passcurrentTerrain.getHeight() >= status.targetElevation + rooftopadj)))) { // status.rangeToTarget == 1)
            //each hex (except source and target) along LOS must be roofless; if not LOS blocked
            //unless target is higher than factory rooftop; first hex must still be roofless - this will handle some LOS that leaves factory - NOT GOOD BUT . . .
            if (status.rangeToSource==1) {
                status.reason = "LOS must leave the building before leaving the source hex to see a location with a different elevation (A6.8 Example 2)";
            }
            else {
                status.reason = "LOS from/to Factory Rooftop within Factory only exists in same hex ground level location (B23.87)";
            }

            status.blocked = true;
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
        }
        return false;
    }
    /**
     * Ensures the elevation/range restriction for source/target IN a depression are met
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkDepressionRule(LOSStatus status, LOSResult result) {

        // this method revised entirely by DR
        double sourceadj=0;
        double targetadj=0;

        if(status.source.getTerrain().isRooftop() ) {
            sourceadj=-0.5;
        }
        if(status.target.getTerrain().isRooftop() ) {
            targetadj=-0.5;
        }
        // LOS must leave a depression?
        // restricted by exiting a depression? (checked in all hexes)
        if (status.exitsSourceDepression) {

            // LOS still in the depression?
            if (status.groundLevel > status.currentHex.getBaseHeight() && !(status.currentHex.equals(status.targetHex)) &&
                    status.groundLevel > status.sourceElevation + sourceadj && !(status.range==1 && status.exitsDepressionTerrainHexside()) && (status.currentHex.isDepressionTerrain() && status.currentHex.equals(status.sourceHex) && status.targetElevation+ targetadj<= status.sourceElevation+ sourceadj) ||
                    (status.range==1 && !(status.exitsDepressionTerrainHexside()) && status.targetHex.isDepressionTerrain()) ||
                    (!status.depressionhexsidesreducerange && !status.currentHex.equals(status.sourceHex) && status.targetElevation+ targetadj - status.sourceElevation+ sourceadj > 0 && status.targetElevation+ targetadj - status.sourceElevation+ sourceadj < status.range)) {  // && (!status.exitsDepressionTerrainHexside())) {
                status.blocked = true;
                status.reason = "Exits depression before range/elevation restrictions are satisfied (A6.3)";
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
        }

        // LOS must enter a depression?
        if (status.entersTargetDepression) {
            if (status.groundLevel > status.currentHex.getBaseHeight() && !(status.currentHex.equals(status.sourceHex)) &&
                    status.groundLevel > status.targetElevation+ targetadj &&!(status.range==1 && status.entersDepressionTerrainHexside()) &&
                    (!status.currentHex.isDepressionTerrain() || status.currentHex.equals(status.targetHex)&& status.sourceElevation+ sourceadj<= status.targetElevation+ targetadj) ||
                    (status.range==1 && !(status.exitsDepressionTerrainHexside()) && status.sourceHex.isDepressionTerrain()) ||
                    (!status.depressionhexsidesreducerange && !status.currentHex.equals(status.sourceHex) && status.sourceElevation+ sourceadj - status.targetElevation+ targetadj > 0 && status.sourceElevation+ sourceadj - status.targetElevation+ targetadj < status.range)) {  // && (!status.entersDepressionTerrainHexside()) ) {

                status.blocked = true;
                status.reason = "Does not enter depression while range/elevation restrictions are satisfied (A6.3)";
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
        }
        return false;
    }

    /**
     * Checks nuances for hexside terrain
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkHexsideTerrainRule(LOSStatus status, LOSResult result) {

        // do not apply when starting/ending on hillock - in hillock rule
        if(status.startsOnHillock || status.endsOnHillock) {
            return false;
        }
        // code added by DR to deal with cellars
        int sourceadj=0;
        int targetadj=0;
        if(status.source.getTerrain().isCellar()) {
            sourceadj=+1;
        }
        if(status.target.getTerrain().isCellar()) {
            targetadj=+1;
        }
        // rowhouse/factory wall?
        if (status.currentTerrain.isRowhouseFactoryWall()) {
            // code added by DR to deal with factory fire along/across Factory wall hexside
            if (checkRowhouseFactoryWall(status, result)) {
                status.reason = "Cannot see through rowhouse/factory wall (B23.71/O5.31)";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
            else {
                return false;
            }
         }

        else {

            // target elevation must > source if in entrenchment
            if (status.source.getTerrain().isEntrenchmentTerrain()) {

                if (status.range > 1 && status.targetElevation <= status.sourceElevation) {

                    status.blocked = true;
                    status.reason = "Unit in entrenchment cannot see over hexside terrain to non-adjacent lower target (B27.2)";
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }
            }
            else if (status.target.getTerrain().isEntrenchmentTerrain()) {

                if (status.range > 1 && status.targetElevation >= status.sourceElevation) {

                    status.blocked = true;
                    status.reason = "Cannot see non-adjacent unit in higher elevation entrenchment over hexside terrain (B27.2)";
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }
            }
            // code added by DR to handle cellars
            else if (status.source.getTerrain().isCellar()) {
                if ((status.target.getTerrain().isCellar()) | (status.range !=1 && status.rangeToSource == 1 && status.targetElevation + targetadj <= status.sourceElevation+sourceadj)) {

                    status.blocked = true;
                    status.reason = "Unit in cellar cannot see over hexside terrain to non-adjacent target (O6.3)";
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }
            }
            else if (status.target.getTerrain().isCellar()) {
                if (status.range != 1 && status.rangeToTarget == 1 && status.targetElevation+targetadj >= status.sourceElevation + sourceadj) {

                    status.blocked = true;
                    status.reason = "Unit in cellar cannot be seen over hexside terrain by non-adjacent target (O6.3)";
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }
            }
            else {

                // should we ignore the hexside terrain?
                boolean ignore =
                        isIgnorableHexsideTerrain(status.sourceHex, status.currentHex.getNearestLocation(status.currentCol, status.currentRow), status.result.getSourceExitHexspine()) ||
                        isIgnorableHexsideTerrain(status.targetHex, status.currentHex.getNearestLocation(status.currentCol, status.currentRow), status.result.getTargetEnterHexspine());

                if (!ignore) {

                    // check bocage
                    if (BOCAGE.equals(status.currentTerrain.getName())) {

                        // always blocks if...
                        if (//higher than both source/target
                                (status.groundLevel + status.currentTerrainHgt > status.sourceElevation &&
                                 status.groundLevel + status.currentTerrainHgt > status.targetElevation) ||
                                 //same height as both source/target, but 1/2 level
                                 (status.groundLevel + status.currentTerrainHgt == status.sourceElevation &&
                                  status.groundLevel + status.currentTerrainHgt == status.targetElevation &&
                                  status.currentTerrain.isHalfLevelHeight()) ||
                                  //same height as higher source/target, but other is lower
                                  (status.groundLevel + status.currentTerrainHgt == Math.max(status.sourceElevation, status.targetElevation) &&
                                   status.groundLevel + status.currentTerrainHgt > Math.min(status.sourceElevation, status.targetElevation)) &&
                                   !status.slopes
                                ) {

                            status.reason = "Cannot see through/over bocage (B9.52)";
                            status.blocked = true;
                            result.setBlocked(status.currentCol, status.currentRow, status.reason);
                            return true;
                        }

                        // otherwise check for blind hexes
                        else if (isBlindHex(status, status.currentTerrainHgt)) {

                            status.reason = "Source or Target location is in a blind hex (B9.52)";
                            status.blocked = true;
                            result.setBlocked(status.currentCol, status.currentRow, status.reason);
                            return true;
                        }
                    }

                    // on the same level?
                    else if (status.groundLevel == status.sourceElevation && status.groundLevel == status.targetElevation && !status.slopes) {

                        status.blocked = true;
                        status.reason = "Intervening hexside terrain (B9.2)";
                        result.setBlocked(status.currentCol, status.currentRow, status.reason);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Applies blind-hex restrictions; also adds hindrances for out-of-season orchards that would create a "blind hex"
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkBlindHexRule(LOSStatus status, LOSResult result) {
        // code added by DR to deal with cellars
        int sourceadj=0;
        int targetadj=0;

        if(status.source.getTerrain().isCellar()) {
            sourceadj=+1;
        }
        if(status.target.getTerrain().isCellar()) {
            targetadj=+1;
        }
        if(status.source.getTerrain().isRooftop()) {
            sourceadj=-1;
        }
        if(status.target.getTerrain().isRooftop()) {
            targetadj=-1;
        }
        // special case when LOS is up-slope or on hillock
        if (status.groundLevel + status.currentTerrainHgt == Math.max(status.sourceElevation + sourceadj, status.targetElevation+ targetadj) &&
                status.groundLevel + status.currentTerrainHgt >  Math.min(status.sourceElevation + sourceadj, status.targetElevation + targetadj)) {

            // can ignore this rule for slopes/hillocks unless blind hex
            if((status.startsOnHillock || status.slopes) && isBlindHex(status, status.currentTerrainHgt)) {
                status.reason = "Source or Target location is in a blind hex from an up-slope location (F2.3)";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
        }

        // special case for bocage - blind hexes checked in hexside rule
        if(BOCAGE.equals(status.currentTerrain.getName())){
            return false;
        }
        // special case for cliffs - need to trigger blind hex check DR
        int cliffadj=0;
        if(status.currentTerrain.isCliff()){
            cliffadj=1;
        }
        if (status.groundLevel + status.currentTerrainHgt + cliffadj > Math.min(status.sourceElevation + sourceadj, status.targetElevation + targetadj) &&
            status.groundLevel + status.currentTerrainHgt + cliffadj < Math.max(status.sourceElevation + sourceadj, status.targetElevation + targetadj)
                ) {

            if (isBlindHex(status, status.currentTerrainHgt, nearestHexsideIsCliff(status.currentCol, status.currentRow, status, result))) {

                // blocked if terrain is obstacle and not hexsides such as Rowhouse or Factory Walls (handled in checkHexsideTerrainRule) DR
                if (status.currentTerrain.isLOSObstacle() && !status.currentTerrain.isHexsideTerrain()) {

                    // ignore inherent terrain that is not the same as center location
                    if(!status.currentTerrain.isInherentTerrain() || (status.currentHex.getCenterLocation().getTerrain().equals(status.currentTerrain))) {

                        if (!status.currentHex.getCenterLocation().getTerrain().isRoofless() || status.currentTerrain.isOutsideFactoryWall()) {

                            status.reason = "Source or Target location is in a blind hex (A6.4)";
                            status.blocked = true;
                            result.setBlocked(status.currentCol, status.currentRow, status.reason);
                            return true;
                        }
                    }
                }
                // code added by DR to handle fire through a factory hex from/to another level outside the factory
                else if ((status.currentTerrain.getLOSCategory() == Terrain.LOSCategories.FACTORY) && (status.LOSLeavesBuilding || status.targetElevation> status.currentTerrainHgt + status.currentTerrain.getHeight()) &&
                        (!status.currentHex.getCenterLocation().getTerrain().isRoofless()) && (!status.currentTerrain.getName().contains("Interior Factory Wall"))) {
                    status.reason = "Source or Target location is in a blind hex (B10.23)";
                    status.blocked = true;
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }
                // see if ground level alone creates blind hex
                else if (status.groundLevel + cliffadj > Math.min(status.sourceElevation + sourceadj, status.targetElevation + targetadj) &&
                        status.groundLevel +cliffadj < Math.max(status.sourceElevation + sourceadj, status.targetElevation + targetadj) &&
                        //(!status.sourceHex.isDepressionTerrain() && !status.targetHex.isDepressionTerrain()) &&
                        isBlindHex(status, 0, nearestHexsideIsCliff(status.currentCol, status.currentRow, status, result))) {

                    status.reason = "Source or Target location is in a blind hex (B10.23)";
                    status.blocked = true;
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }

                // hindrance creates "blind hex", if not target/source hex
                else if (!status.currentHex.equals(status.targetHex) && !status.currentHex.equals(status.sourceHex)) {

                    // only one hindrance for out-of-season orchard
                    if ("Orchard, Out of Season".equals(status.currentTerrain.getName())) {

                        if (status.rangeToTarget == 1) {

                            if (addHindranceHex(status, result))
                                return true;
                        }
                    }
                    // code added by DR to handle LOS over roofless factory; no hindrance from higher elevation
                    else if (status.currentTerrain.getLOSCategory() == Terrain.LOSCategories.FACTORY)
                        return false;
                    else {
                        // add hindrance
                        if (addHindranceHex(status, result))
                            return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Applies the LOS rules to a single point; adjusts the status if the LOS enters a new hex
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkTerrainHeightRule(LOSStatus status, LOSResult result) {
// code added by DR to deal with rooftop 1/2 level and cellars
        double sourceadj=0;
        double targetadj=0;
        double obstacleadj=0;
        if(status.source.getTerrain().isRooftop() ) {
            sourceadj=-0.5;
        }
        if(status.target.getTerrain().isRooftop() ) {
            targetadj=-0.5;
        }
        if(status.currentTerrain.isHalfLevelHeight() && status.currentTerrain.isBuilding()) {
            obstacleadj=+0.5;
        }
        if(status.source.getTerrain().isCellar()) {
            sourceadj=+1;
        }
        if(status.target.getTerrain().isCellar()) {
            targetadj=+1;
        }

        if ( (status.groundLevel + status.currentTerrainHgt + obstacleadj== Math.max(status.sourceElevation + sourceadj, status.targetElevation + targetadj)) &&
                (status.groundLevel + status.currentTerrainHgt+obstacleadj > Math.min(status.sourceElevation+ sourceadj, status.targetElevation + targetadj))) {

            // can ignore this rule for slopes/hillocks - handled by blind hex rule
            if(status.slopes || status.startsOnHillock || status.endsOnHillock) {
                return false;
            }

            // can also ignore for bocage
            if(BOCAGE.equals(status.currentTerrain.getName())){
                return false;
            }

            // are exiting gully restrictions satisfied?
            if (!(status.ignoreGroundLevelHex != null &&
                    status.ignoreGroundLevelHex.containsExtended(status.currentCol, status.currentRow)) &&
                    // are entering gully restrictions satisfied?
                    !(status.entersTargetDepression && (status.currentHex.isDepressionTerrain() && status.currentTerrainHgt==0) && (!(status.LOSis60Degree) && !(status.LOSisHorizontal))) &&
                    !(status.exitsSourceDepression && (status.currentHex.isDepressionTerrain() && status.currentTerrainHgt==0) && (!(status.LOSis60Degree) && !(status.LOSisHorizontal)))
                    ) {

                // Need to handle special case where source unit is adjacent to a water obstacle looking
                // at a target in the water obstacle. We can ignore the bit of open ground that extends into
                // the first water hex.
                if (!(status.currentHex.getCenterLocation().getTerrain().isWaterTerrain() &&
                        status.currentTerrain.getHeight() < 1 &&
                        ((status.rangeToSource == 1 && status.sourceElevation > status.targetElevation &&
                                status.target.getHex().getCenterLocation().getTerrain().isWaterTerrain()) ||
                                (status.rangeToTarget == 1 && status.targetElevation > status.sourceElevation &&
                                        status.source.getHex().getCenterLocation().getTerrain().isWaterTerrain())))) {

                    // if orchard, then hindrance
                    if ("Orchard, Out of Season".equals(status.currentTerrain.getName())) {

                        if (addHindranceHex(status, result))
                            return true;

                    } else {
                         status.reason = "Must have a height advantage to see over this terrain (A6.2)";
                        status.blocked = true;
                        result.setBlocked(status.currentCol, status.currentRow, status.reason);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if terrain is higher than both the source and target
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkTerrainIsHigherRule(LOSStatus status, LOSResult result) {
        // code added by DR to deal with rooftop 1/2 level and cellars
        double sourceadj=0;
        double targetadj=0;
        double obstacleadj=0;
        if(status.source.getTerrain().isRooftop()) {
            sourceadj=-0.5; //1;
        }
        if(status.target.getTerrain().isRooftop()) {
            targetadj=-0.5; //1;
        }
        if(status.currentTerrain.isHalfLevelHeight() && !status.currentTerrain.isHexsideTerrain()) {
            obstacleadj=+0.5;
        }
        if(status.source.getTerrain().isCellar()) {
            sourceadj=+1;
        }
        if(status.target.getTerrain().isCellar()) {
            targetadj=+1;
        }

        // ignore special case for split terrain - different rule
        if(status.currentTerrain.hasSplit() &&
                status.groundLevel == status.sourceElevation + sourceadj &&
                status.groundLevel == status.targetElevation + targetadj){
            return false;
        }

        // ignore bocage - different rule
        if(BOCAGE.equals(status.currentTerrain.getName())){
            return false;
        }

        if (status.groundLevel + status.currentTerrainHgt + obstacleadj > status.sourceElevation + sourceadj &&
                status.groundLevel + status.currentTerrainHgt + obstacleadj > status.targetElevation + targetadj) {

            // terrain blocks LOS?
            if (status .currentTerrain.isLOSObstacle()) {
                status.reason = "Terrain is higher than both the source and target (A6.2)";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
            // must be hindrance
            else {
                // code added by DR to handle factory LOS (leaves building and within building)
                if (!status.LOSLeavesBuilding && status.currentTerrain.getLOSCategory() == Terrain.LOSCategories.FACTORY) {
                    return false;
                } else if (status.LOSLeavesBuilding && status.currentTerrain.getLOSCategory() == Terrain.LOSCategories.FACTORY) {
                    status.reason = "Terrain is higher than both the source and target (A6.2)";
                    status.blocked = true;
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                } else {
                    // add hindrance
                    if (addHindranceHex(status, result)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Applies hillocks rules
     * Note that his rule supersedes the "check half-level terrain" rule
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkHillockRule(LOSStatus status, LOSResult result) {

        // only applicable to same elevations
        if(hillockRuleApplicable(status)){

            // both units on a hillock - always clear
            if(status.startsOnHillock && status.endsOnHillock) {

                return false;

            }

            // one unit on a hillock
            else if(status.startsOnHillock || status.endsOnHillock) {

                // check intervening hillocks
                if(status.startsOnHillock && status.crossedHillocks.size()
                        - (status.targetAdjacentHillock != null ? 1 : 0) // ignore hillock adjacent to target
                        + (status.exitsSlopeHexside()           ? 1 : 0) // adjust if starting on slope
                        > 2) {

                    return blockByHillock(status, result);

                } else if(status.endsOnHillock && status.crossedHillocks.size()
                        - (status.sourceAdjacentHillock != null ? 1 : 0) // ignore hillock adjacent to source
                        + (status.entersSlopeHexside()          ? 1 : 0) // adjust if ending on slope
                        > 1) {

                    return blockByHillock(status, result);
                }

                // check intervening wall/hedge
                else if (WALL.equals(status.currentTerrain.getName()) || HEDGE.equals(status.currentTerrain.getName()) ){

                    if(status.firstWallCrossed == null) {

                        status.firstWallCrossed = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
                        status.firstWallPoint = new Point(status.currentCol, status.currentRow);
                    }

                    else  {
                        // find the wall location and the location in the opposite hex
                        Location nearestLocation = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
                        Hex locationHex = nearestLocation.getHex();
                        int locationHexside = locationHex.getLocationHexside(nearestLocation);
                        Hex oppositeHex = getAdjacentHex(locationHex, locationHexside);
                        Location oppositeLocation = oppositeHex.getHexsideLocation(Hex.getOppositeHexside(locationHexside));

                        if(!nearestLocation.isCenterLocation() &&
                           (nearestLocation == status.firstWallCrossed || oppositeLocation == status.firstWallCrossed)) {

                            return false;
                        }
                        else {

                            // pretend we're not on a hillock and use check hexside rule to see if wall/hedge blocks
                            boolean startsOnHillock = status.startsOnHillock;
                            boolean endsOnHillock = status.endsOnHillock;
                            status.startsOnHillock = false;
                            status.endsOnHillock = false;
                            if (checkHexsideTerrainRule(status, new LOSResult()) && status.firstWallPoint.distance(status.currentCol, status.currentRow) > 15) {

                                status.reason = "More than one intervening wall/hedge (F6.4)";
                                status.blocked = true;
                                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                                return true;
                            }
                            else {
                                status.startsOnHillock = startsOnHillock;
                                status.endsOnHillock = endsOnHillock;
                                status.blocked = false;
                                status.reason = "";
                            }
                        }
                    }
                }

                // check intervening rubble
                else if (STONE_RUBBLE.equals(status.currentTerrain.getName()) || WOODEN_RUBBLE.equals(status.currentTerrain.getName())){

                    if(status.currentHex != status.firstRubbleCrossed) {

                        status.reason = "More than one intervening rubble hex (F6.4)";
                        status.blocked = true;
                        result.setBlocked(status.currentCol, status.currentRow, status.reason);
                        return true;
                    }
                }
            }

            // ignore "adjacent" hillocks if not entrenched
            else if((status.sourceAdjacentHillock != null && status.sourceAdjacentHillock.contains(status.currentHex) && !status.source.getTerrain().isEntrenchmentTerrain()) ||
                    (status.targetAdjacentHillock != null && status.targetAdjacentHillock.contains(status.currentHex) && !status.target.getTerrain().isEntrenchmentTerrain())) {
                return false;
            }

            //check other 1/2 level terrain
            else if(status.currentTerrain.isHalfLevelHeight() && !status.currentTerrain.isHexsideTerrain()) {
                return applyHalfLevelTerrain(status, result);
            }
        }
        return false;
    }

    /**
     * Set status and result when LOS blocked by hillock
     * @param status the LOS status
     * @param result the LOS result
     * @return always true
     */
    private boolean blockByHillock(LOSStatus status, LOSResult result) {
        status.reason = "Intervening hillock (F6.4)";
        status.blocked = true;
        result.setBlocked(status.currentCol, status.currentRow, status.reason);
        return true;
    }

    /**
     * @param status the LOS status
     * @return true if hillock rules are applicable to the LOS status
     */
    private boolean hillockRuleApplicable(LOSStatus status) {

        return status.groundLevel + status.currentTerrainHgt == status.sourceElevation &&
                status.groundLevel + status.currentTerrainHgt == status.targetElevation &&
                (status.crossingHillock != null ||
                        status.startsOnHillock ||
                        status.endsOnHillock ||
                        status.sourceAdjacentHillock != null ||
                        status.targetAdjacentHillock != null);
    }

    /**
     * This class represents the set of hexes that make up a Hillock
     */
    private class Hillock {

        HashSet<Hex> hexes = new HashSet<Hex>(10);

        /**
         * Add a hex
         * @param hex the hex
         */
        public void addHex(Hex hex) {
            hexes.add(hex);

        }

        /**
         * @param hex the hex
         * @return true if the given hex is part of the hillock
         */
        public boolean contains(Hex hex) {
            return hexes.contains(hex);
        }
        /**
         * @return the number of hexes in the hillock
         */
        public int size() {
            return hexes.size();
        }

        /**
         * @param hex the hex
         * @return true if the given hex is not part of the hillock but adjacent to one of the hexes
         */
        @SuppressWarnings("unused")
        public boolean isAdjacent(Hex hex) {

            if(hexes.contains(hex)) {
                return false;
            }

            for(Hex h : hexes) {
                if(range(h, hex) == 1) {
                    return true;
                }

            }

            return false;
        }

        /**
         * @return true if any hex in given hillock is adjacent to this hillock
         */
        public boolean isAdjacent(Hillock hillock) {

            for(Hex outer : hillock.getHexes()){
                for(Hex inner : hexes){
                    if (range(inner, outer) == 1){
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Copies all hexes from the given hillock into this hillock
         * @param hillock the hillock
         */
        public void merge(Hillock hillock){

            // can't merge with oneself
            if(this == hillock){
                return;
            }

            hexes.addAll(hillock.getHexes());

/* 		for(Hex h : hillock.getHexes()){
			hexes.add(h);
		}
 */
        }

        /**
         * @return the set of hexes in this hillock
         */
        public HashSet<Hex> getHexes() {
            return hexes;
        }

    }

    /**
     * @param hex the hex
     * @return the hillock containing the given hex; null if none
     */
    private Hillock getHillock(Hex hex) {

        for(Hillock h : hillocks) {
            if(h.contains(hex)) {
                return h;
            }
        }
        return null;
    }

    /**
     * Build the hillocks
     * Assumes the hexgrid is current
     */
    public void buildHillocks() {

        // remove existing hillocks
        hillocks = new HashSet<Hillock>();

        // create one hillock for each hillock hex
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height + (x % 2); y++) { // add 1 hex if odd
                if (HILLOCK.equals(getHex(x, y).getCenterLocation().getTerrain().getName())) {

                    Hillock h = new Hillock();
                    h.addHex(getHex(x, y));
                    hillocks.add(h);
                }
            }
        }

        // consolidate hillocks into a hew hash set
        HashSet<Hillock> newHillocks = new HashSet<Hillock>();
        while (hillocks.size() > 0) {

            // cycle through hillocks repeatedly - creating one hillock each time through
            boolean consolidation = true;
            while (consolidation && hillocks.size() > 1) {

                // get the first hillock
                Iterator<Hillock> i = hillocks.iterator();
                Hillock hillock = i.next();

                // is there an adjacent hillock?
                consolidation = false;
                while (i.hasNext()) {

                    // consolidate one adjacent hillock
                    Hillock h = i.next();
                    if (hillock.isAdjacent(h)) {
                        hillock.merge(h);
                        i.remove();
                        consolidation = true;
                    }
                }
            }

            // move the created hillock
            Iterator<Hillock> i = hillocks.iterator();
            Hillock h = i.next();
            newHillocks.add(h);
            i.remove();
        }

        hillocks = newHillocks;
    }

    /**
     * Checks rules for half-level terrain
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkHalfLevelTerrainRule(LOSStatus status, LOSResult result) {

        // special rules for hillocks
        if ((hillockRuleApplicable(status) && !status.slopes) || hillockHindranceToLowerElevation(status)) {

            // apply max one hindrance for grain/brush
            if (status.firstHalfLevelHindrance == null &&
                    (BRUSH.equals(status.currentTerrain.getName()) || GRAIN.equals(status.currentTerrain.getName())) &&
                    !(status.startsOnHillock && status.endsOnHillock)) {

                status.firstHalfLevelHindrance = status.currentHex;
                if (addHindranceHex(status, result)) {
                    return true;
                }
            }
            return false;
        }

        // code added by DR to deal with rooftop 1/2 level
        double sourceadj=0;
        double targetadj=0;
        if(status.source.getTerrain().isRooftop() && !(status.target.getTerrain().isRooftop())) {
            sourceadj=-0.5;
        }
        if(status.target.getTerrain().isRooftop() && !(status.source.getTerrain().isRooftop())) {
            targetadj=-0.5;
        }

        return status.currentTerrain.isHalfLevelHeight() &&
                !status.currentTerrain.isHexsideTerrain() &&
                status.groundLevel + status.currentTerrainHgt == status.sourceElevation+sourceadj &&
                status.groundLevel + status.currentTerrainHgt == status.targetElevation+targetadj &&
                !status.slopes && applyHalfLevelTerrain(status, result);
    }

    /**
     * Logic for when to apply grain/brush hindrance from hillock to lower elevation
     * @param status the LOS status
     * @return true in hindrance applicable
     */
    private boolean hillockHindranceToLowerElevation(LOSStatus status) {
        return (status.startsOnHillock &&
                       status.groundLevel + status.currentTerrainHgt == status.sourceElevation &&
                       status.groundLevel + status.currentTerrainHgt >  status.targetElevation)
                         ||
               (status.endsOnHillock &&
                       status.groundLevel + status.currentTerrainHgt == status.targetElevation &&
                       status.groundLevel + status.currentTerrainHgt >  status.sourceElevation);
    }

    /**
     * Set status and result for LOS impacted by 1/2-level terrain
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    private boolean applyHalfLevelTerrain(LOSStatus status, LOSResult result) {

        if (status.currentTerrain.isLOSObstacle()) {

            status.reason = "Half level terrain is higher than both the source and/or the target (A6.2)";
            status.blocked = true;
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
        }
        else {

            // must be hindrance
            if (addHindranceHex(status, result)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies the LOS rules for terrain having two terrain types (e.g. orchard)
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkSplitTerrainRule(LOSStatus status, LOSResult result) {
        double sourceadj=0;
        double targetadj=0;

        //if(status.source.getTerrain().isRooftop()) {
        //    sourceadj=-0.5; //1;
        //}
        //if(status.target.getTerrain().isRooftop()) {
        //    targetadj=-0.5; //1;
        //}

        if(status.source.getTerrain().isCellar()) {
            sourceadj=+1;
        }
        if(status.target.getTerrain().isCellar()) {
            targetadj=+1;
        }
        if (status.currentTerrain.hasSplit() &&
                status.groundLevel == status.sourceElevation + sourceadj &&
                status.groundLevel == status.targetElevation + targetadj) {

            // special case for slopes
            if(status.slopes) {

                if (status.currentTerrain.isLOSObstacle()) {

                    status.reason = "This terrain blocks LOS to up-slope location";
                    status.blocked = true;
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
                }

                // must be hindrance
                if (addHindranceHex(status, result))
                    return true;
            }
            else if ((status.currentTerrain.isLowerLOSObstacle()) | (!status.LOSLeavesBuilding && status.currentTerrain.isOutsideFactoryWall())) {  //getLOSCategory() == Terrain.LOSCategories.FACTORY)) {

                status.reason = "This terrain blocks LOS to same same elevation Source and Target";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
            else if (status.currentTerrain.isLowerLOSHindrance()) {

                // add hindrance
                if (addHindranceHex(status, result))
                    return true;
            }
        }
        return false;
    }

    /**
     * Checks if ground level is higher than source and target
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkGroundLevelRule(LOSStatus status, LOSResult result) {
        // code added by DR to deal with cellars
        int sourceadj=0;
        int targetadj=0;
        if(status.source.getTerrain().isCellar()) {
            sourceadj=+1;
        }
        if(status.target.getTerrain().isCellar()) {
            targetadj=+1;
        }
        if (status.groundLevel > status.sourceElevation + sourceadj && status.groundLevel > status.targetElevation+ targetadj) {

            status.reason = "Ground level is higher than both the source and target (A6.2)";
            status.blocked = true;
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
        }
        return false;
    }

    /**
     * Check if a bridge creates a hindrance
     *
     * @param status the LOS status
     * @param result the LOS result
     * @return true if LOS is blocked
     */
    protected boolean checkBridgeHindranceRule(LOSStatus status, LOSResult result) {
        if (status.currentHex.hasBridge()) {

            if (status.sourceElevation == status.targetElevation && status.sourceElevation == status.bridge.getRoadLevel()) {

                // on bridge but not on road?
                if (status.bridgeArea.contains((double)status.currentCol, (double)status.currentRow) && !status.bridgeRoadArea.contains((double)status.currentCol, (double)status.currentRow)) {

                    // add hindrance
                    if (addHindranceHex(status, result))
                        return true;
                }
            }
        }
        return false;
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
        if (l.getHex().equals(h)) {

            return true;
        }

        // opposite hexside?
        for (int x = 0; x < 6; x++) {

            // get the adjacent hex for this hexside
            Hex h2 = getAdjacentHex(h, x);
            if (h2 != null) {

                // adjacent to this hexside?
                if (h2.getHexsideLocation(Hex.getOppositeHexside(x)).equals(l)) {

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
                if (x == 0 && (h2.getHexsideLocation(2).equals(l) || h2.getHexsideLocation(4).equals(l))) return true;
                if (x == 1 && (h2.getHexsideLocation(3).equals(l) || h2.getHexsideLocation(5).equals(l))) return true;
                if (x == 2 && (h2.getHexsideLocation(4).equals(l) || h2.getHexsideLocation(0).equals(l))) return true;
                if (x == 3 && (h2.getHexsideLocation(5).equals(l) || h2.getHexsideLocation(1).equals(l))) return true;
                if (x == 4 && (h2.getHexsideLocation(0).equals(l) || h2.getHexsideLocation(2).equals(l))) return true;
                if (x == 5 && (h2.getHexsideLocation(1).equals(l) || h2.getHexsideLocation(3).equals(l))) return true;
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
        if (locationHexside ==-1) {return true;} // ignore if location is not a hexside
        Terrain locationHexsideTerrain = locationHex.getHexsideTerrain(locationHexside);

        // too far away?
        if (range(h, locationHex) > 2) {

            return false;
        }
        // always ignore if adjacent
        if (isAdjacentHexside(h, l)) {

            return true;
        }
        // ignore hexspines if not bocage
        if (isHexspine(h, l) && !(locationHexsideTerrain != null && BOCAGE.equals(locationHexsideTerrain.getName()))) {

            return true;
        }

        // ignore hexside terrain in adjacent hex that spills into adjacent location
        if (range(h, locationHex) == 1 && !l.getTerrain().isHexsideTerrain()){
            return true;
        }

        // for LOS along a hexspine, check hexside terrain at the far end of the hexspine
       if (LOSHexspine >= 0) {

            // for locations that are 2 hexes away, let's use the corresponding
            // location in the adjacent hex
            if (range(h, locationHex) == 2) {

                // find the hex across the location hexside
                Hex oppositeHex = getAdjacentHex(locationHex, locationHexside);

                if (oppositeHex == null) {
                    return true;
                }
                if (range(h, oppositeHex) > 1) {
                    return false;
                }

                // change the location values
                locationHex = oppositeHex;
                locationHexside = Hex.getOppositeHexside(locationHexside);
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

            if(hex1 == null || hex2 == null) return false;

            Location l2 = hex1.getHexsideLocation(LOSHexspine);
            Location l3 = hex2.getHexsideLocation(hexside);

            Terrain t1 = hex2.getHexsideTerrain(hexspine);
            Terrain t2 = hex1.getHexsideTerrain(LOSHexspine);
            Terrain t3 = hex2.getHexsideTerrain(hexside);

           return t1 != null && (l.equals(l2) || l.equals(l3)) && (t2 == null || t3 == null);
       }
        return false;
    }

    /**
     * @param x x coordinate
     * @param y y coordinate
     * @return true if the nearest hexside is a cliff
     */
    protected boolean  nearestHexsideIsCliff(int x, int y, LOSStatus status, LOSResult result) {

        if (!(result.isLOSis60Degree()) &&  !(result.isLOSisHorizontal()) ) {
            Hex hex = gridToHex(x, y);
            Location l = gridToHex(x, y).getNearestLocation(x, y);

            return !l.isCenterLocation() && hex.hasCliff(hex.getLocationHexside(l));
        }
        else {
            // special case of LOS along hexside; need to check for depression cliff hexsides in source/target hex - code above does not work all the time
            if (status.sourceHex.isDepressionTerrain()) {
                int firsthexside = result.getSourceExitHexspine();
                int secondhexside = firsthexside + 1;
                if (secondhexside >= 6) {
                    secondhexside = secondhexside - 6;
                }

                // need to check now for cliffs connected to LOS hexside; if cliff these will need to be tested for blind hexes
                if (status.sourceHex.hasCliff(firsthexside) || status.sourceHex.hasCliff(secondhexside)) {
                    return true;
                }
            }
            if (status.targetHex.isDepressionTerrain()) {
                int firsthexside = result.getTargetEnterHexspine();
                int secondhexside = firsthexside + 1;
                if (secondhexside >= 6) {
                    secondhexside = secondhexside - 6;
                }

                // need to check now for cliffs connected to LOS hexside; if cliff these will need to be tested for blind hexes
                if (status.targetHex.hasCliff(firsthexside) || status.targetHex.hasCliff(secondhexside)) {
                    return true;
                }
            }
            return false;
        }

    }

    /**
     * Add a hindrance hex to the result
     * @param status the LOS status
     * @param result the LOS result
     * @return true if LOS is blocked
     */
    protected boolean addHindranceHex(LOSStatus status,LOSResult result) {

        // add hex if necessary
        if (!status.currentHex.equals(status.sourceHex) && !status.currentHex.equals(status.targetHex)) {

            // hindrance must be between the source and target
            if(range(status.sourceHex, status.currentHex) < range(status.sourceHex, status.targetHex) &&
               range(status.targetHex, status.currentHex) < range(status.sourceHex, status.targetHex)){
                int hindrancevalue =1;
                // handle special cases where terrain hindrance is not 1
                // roofless factory debris
                if (status.currentTerrain.isRoofless()) {hindrancevalue=2;}
                result.addMapHindrance(status.currentHex, hindrancevalue, status.currentCol, status.currentRow);

                // see if hindrance caused LOS to be blocked
                return result.isBlocked();
            }
        }
        return false;
    }

    /**
     * @param status the LOS status
     * @param terrainHeight the terrain height
     * @return true if target is in a blind hex
     */
    protected static boolean isBlindHex(LOSStatus status, int terrainHeight) {

        return isBlindHex(status, terrainHeight, false);
    }

    /**
     * @param status the LOS status
     * @param terrainHeight the terrain height
     * @param isCliffHexside is a cliff hexside?
     * @return true if target is in a blind hex
     */
    protected static boolean isBlindHex(LOSStatus status, int terrainHeight, boolean isCliffHexside) {
        int sourceadj=0;
        int targetadj=0;
        if(status.source.getTerrain().isRooftop()) {
            sourceadj=-1;
        }
        if(status.target.getTerrain().isRooftop()) {
            targetadj=-1;
        }
        int sourceElevation = status.sourceElevation;
        int targetElevation = status.targetElevation;
        int rangeToSource = status.rangeToSource;
        int rangeToTarget = status.rangeToTarget;
        int groundLevel = status.groundLevel;

		// blind hex NA for same-level LOS
        if(sourceElevation == targetElevation){
            return false;
        }

        // now do rooftop adjustment - not before or will mess up previous test
        sourceElevation=sourceElevation + sourceadj;
        targetElevation=targetElevation + targetadj;

        Hex starthex=status.sourceHex;
        Hex finishhex=status.targetHex;
        // if LOS raising, swap source/target and use the same logic as LOS falling
        if (sourceElevation < targetElevation) {

            // swap elevations
			int temp = sourceElevation;
			sourceElevation = targetElevation;
            targetElevation = temp;
            starthex=status.targetHex;
            finishhex=status.sourceHex;
            // swap range
            temp = rangeToSource;
            rangeToSource = rangeToTarget;
            rangeToTarget = temp;
        }


        // increment source elevation for slopes/hillocks in special case where terrain is same height as upper location
        if(status.slopes || status.startsOnHillock) {
            if (status.groundLevel + status.currentTerrainHgt == Math.max(status.sourceElevation, status.targetElevation)) {

                sourceElevation++;
            }
        }

        if(isCliffHexside &&!status.LOSis60Degree && !status.LOSisHorizontal) {
            Location testlocation = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
            int testhexside =status.currentHex.getLocationHexside(testlocation);
            int newhexside=testhexside+3;
            if(newhexside>= 6) {newhexside=newhexside-6;}

            if (rangeToSource==1 && !(rangeToTarget==1) && sourceElevation >= terrainHeight && testlocation.getTerrain().isCliff() && !starthex.getHexsideLocation(newhexside).getTerrain().isCliff()) {
                isCliffHexside= false;
            }
            if (rangeToTarget==1 && !(rangeToSource==1) && targetElevation >= terrainHeight && testlocation.getTerrain().isCliff() && !finishhex.getHexsideLocation(newhexside).getTerrain().isCliff()) {
                isCliffHexside= false;
            }
            else if(testlocation.getTerrain().isCliff()) {
                isCliffHexside=true;
            }
        }
        if(status.currentTerrain.isRowhouseFactoryWall()) {
            // hex containing IFW hexside is "first" blind hex; use range adjustment to handle
            rangeToTarget=rangeToTarget+1;
            // range from Source to IFW is to hex with exit IFW not hex with entry IFW

        }

        // is the obstacle a non-cliff crest line?
        if (terrainHeight == 0 && !isCliffHexside) {
            int depressionadj=0;
            HashSet<Integer> sidecrossed= status.getHexsideCrossed(status.currentHex);
            Iterator<Integer> i = sidecrossed.iterator();
            if(i.hasNext()) {
                int testforDepressionhexside = i.next();
                if (status.currentHex.getHexsideLocation(testforDepressionhexside).isDepressionTerrain()) {
                   depressionadj=-1;
                }
            }
            if( rangeToTarget <= Math.max(2 * (groundLevel + depressionadj + terrainHeight) + (rangeToSource / 5) - sourceElevation - targetElevation, 0)) {
                return true;
            }
            else {
                return false;
            }
        }
        else {

            return rangeToTarget <= Math.max(2 * (groundLevel + terrainHeight) + (rangeToSource / 5) - sourceElevation - targetElevation + 1, 1);
        }
    }

    /**
     * Sets the terrain name map and populate the terrain list
     */
	public final void setTerrain(HashMap<String, Terrain> nameMap) {

		terrainNameMap = nameMap;
        for(String name : nameMap.keySet()) {

            terrainList[nameMap.get(name).getType()] = nameMap.get(name);
        }
    }

    /**
     * Determines if a point is within the map image.
     * @param c the x coordinate
     * @param r the y coordinate
     * @return true if the point is on the map
     */
    public boolean onMap(int c, int r) {

        return !(r < 0 || c < 0 || c >= gridWidth || r >= gridHeight);
    }

    /**
     * Rotates the map 180 degrees. Should only be used for geomorphic map boards
     */
    public void flip() {

		// flip the terrain and elevation grids
        for (int x = 0; x < (gridWidth+1) / 2; x++) {
            for (int y = 0; y < gridHeight; y++) {

				char terrain = terrainGrid[x][y];
				terrainGrid[x][y] = terrainGrid[gridWidth - x - 1][gridHeight - y - 1];
                terrainGrid[gridWidth - x - 1][gridHeight - y - 1] = terrain;

				byte elevation = elevationGrid[x][y];
				elevationGrid[x][y] = elevationGrid[gridWidth - x - 1][gridHeight - y - 1];
                elevationGrid[gridWidth - x - 1][gridHeight - y - 1] = elevation;
            }
        }

        // flip the hex grid
        for (int x = 0; x < hexGrid.length / 2 + 1; x++) {
            for (int y = 0; y < (x == hexGrid.length / 2 ? (hexGrid[x].length - 1) / 2 + 1 : hexGrid[x].length); y++) {

                // get the next two hexes
				Hex h1 = hexGrid[x][y];
				Hex h2 = hexGrid[width - x - 1][hexGrid[width - x - 1].length - y - 1];

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
    public boolean insertMap(Map map, Hex upperLeft) {

        // determine where the upper-left point of the inserted map will be
        int left = upperLeft.getCenterLocation().getLOSPoint().x;
        int upper = upperLeft.getCenterLocation().getLOSPoint().y - (int) hexHeight / 2;

        // ensure the map will fit
        if (!onMap(left, upper)) {

            return false;
        }

        // copy the terrain and elevation grids
        for (int x = 0; x < map.gridWidth && x < this.gridWidth; x++) {
            for (int y = 0; y < map.gridHeight && y < this.gridHeight; y++) {

                terrainGrid[left + x][upper + y] = (char) map.getGridTerrain(x, y).getType();
                elevationGrid[left + x][upper + y] = (byte) map.getGridElevation(x, y);
            }
        }

        // copy the hex grid
        int hexRow = upperLeft.getRowNumber();
        int hexCol = upperLeft.getColumnNumber();
        for (int x = 0; x < map.hexGrid.length && x < this.hexGrid.length; x++) {
            for (int y = 0; y < map.hexGrid[x].length && y < this.hexGrid[x].length; y++) {

                hexGrid[x + hexCol][y + hexRow].copy(map.getHex(x, y));

            }
        }

        // need to rebuild the hillocks
        buildHillocks();

        return true;
    }

    // DR added to handle HASL maps
    public boolean insertOneMap(Map map) {

        // copy the terrain and elevation grids
        for (int x = 0; x < map.gridWidth && x < this.gridWidth; x++) {
            for (int y = 0; y < map.gridHeight && y < this.gridHeight; y++) {

                terrainGrid[x][y] = (char) map.getGridTerrain(x, y).getType();
                elevationGrid[x][y] = (byte) map.getGridElevation(x, y);
            }
        }

        // copy the hex grid
        for (int x = 0; x < map.hexGrid.length && x < this.hexGrid.length; x++) {
            for (int y = 0; y < map.hexGrid[x].length && y < this.hexGrid[x].length; y++) {

                hexGrid[x][y].copy(map.getHex(x, y));

            }
        }

        // need to rebuild the hillocks
        buildHillocks();

        return true;
    }

    /**
     * Crops the board to the points in the map grid. Note that the "corners" of the cropped map must create
     * a map where the left and right board edges are half hexes and both corner hexes are fully on the map
     * @param upperLeft upper left corner of the map grid
     * @param lowerRight the lower-right corner of the map grid
     * @return the cropped map or null if invalid
     */
    public Map crop(Point upperLeft, Point lowerRight){

        int localGridWidth = lowerRight.x - upperLeft.x;
        int localGridHeight = lowerRight.y - upperLeft.y;
        int localHexWidth = (int) Math.round((double)localGridWidth / hexWidth) + 1;
        int localHexHeight = (int) Math.round((double)localGridHeight / hexHeight);

        // the hex width must be odd - if not extend to include the next half hex
        if (localHexWidth%2 != 1) {
            localHexWidth++;
        }

        //DR amended code to use width, height
        Map newMap = new Map(hexWidth, hexHeight, width, height, A1CenterX, A1CenterY, localGridWidth, localGridHeight, terrainNameMap);

        // copy the map grid
        for(int x = 0; x < newMap.gridWidth && x + upperLeft.x < gridWidth; x++) {
            for(int y = 0; y < newMap.gridHeight && y + upperLeft.y < gridHeight; y++){

                newMap.terrainGrid[x][y] = terrainGrid[x + upperLeft.x][y + upperLeft.y];
                newMap.elevationGrid[x][y] = elevationGrid[x + upperLeft.x][y + upperLeft.y];
            }
        }

        //copy the hex grid
        Hex upperLeftHex = gridToHex(upperLeft.x, upperLeft.y);
        for (int x = 0; x < newMap.hexGrid.length; x++) {
            for (int y = 0; y < newMap.hexGrid[x].length; y++) {

                newMap.hexGrid[x][y] = (hexGrid[x + upperLeftHex.getColumnNumber()][y + upperLeftHex.getRowNumber()]);
            }
        }

        return newMap;
    }

    /**
     * Set the hexes with slopes
     * @param slopes the slopes
     */
    public void setSlopes(Slopes slopes) {
        for(java.util.Map.Entry<String, boolean[]> hex : slopes.getAllSlopes().entrySet()){
            if(getHex(hex.getKey()) != null){
                getHex(hex.getKey()).setSlopes(hex.getValue());
            }
        }
    }

    // code added by DR to enable RB rr embankments
    /**
     * Set the hexes with rr embankments
     *
     */
    public void setRBrrembankments(RBrrembankments RBrrembankments) {
        for(java.util.Map.Entry<String, boolean[]> hex : RBrrembankments.getAllRBrrembankments().entrySet()){
            if(getHex(hex.getKey()) != null){
                getHex(hex.getKey()).setRBrrembankments(hex.getValue());
            }
        }
    }
    // method added by DR to handle LOS across RB rrembankments
    public boolean checkRBrrembankments (LOSStatus status,LOSResult result, HashSet<Integer> hexsides) {
        Terrain checkhexside;
        for (Integer hexside : hexsides) {
            // get Terrain for hexside crossed by LOS
            if (hexside==status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentCol, status.currentRow))) {

                checkhexside = status.currentHex.getHexsideTerrain(hexside);
                if (checkhexside != null) {
                    // if Terrain is RB rrembankment then check if blocks LOS
                    if (checkhexside.isHexsideTerrain() && checkhexside.getName().contains("Rrembankment")) {
                        // target elevation must > source if in entrenchment
                        if (status.source.getTerrain().isEntrenchmentTerrain()) {
                            if (status.range > 1 && status.targetElevation <= status.sourceElevation) {
                                status.blocked = true;
                                status.reason = "Unit in entrenchment cannot see over hexside terrain to non-adjacent lower target (B27.2)";
                                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                                return true;
                            }
                        } else if (status.target.getTerrain().isEntrenchmentTerrain()) {
                            if (status.range > 1 && status.targetElevation >= status.sourceElevation) {
                                status.blocked = true;
                                status.reason = "Cannot see non-adjacent unit in higher elevation entrenchment over hexside terrain (B27.2)";
                                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                                return true;
                            }
                        }
                        // handle cellars
                        else if (status.source.getTerrain().isCellar()) {
                            if (status.range != 1 && status.rangeToSource == 1 && status.targetElevation <= status.sourceElevation + 1) {
                                status.blocked = true;
                                status.reason = "Unit in cellar cannot see over hexside terrain to non-adjacent target (O6.3)";
                                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                                return true;
                            }
                        } else if (status.target.getTerrain().isCellar()) {
                            if (status.range != 1 && status.rangeToTarget == 1 && status.targetElevation + 1 >= status.sourceElevation) {
                                status.blocked = true;
                                status.reason = "Unit in cellar cannot be seen over hexside terrain by non-adjacent target (O6.3)";
                                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                                return true;
                            }
                        } else {
                            // should we ignore the hexside terrain?
                            if (!status.currentHex.equals(status.sourceHex)) {
                                boolean ignore =
                                        isIgnorableHexsideTerrain(status.sourceHex, status.currentHex.getNearestLocation(status.currentCol, status.currentRow), status.result.getSourceExitHexspine()) ||
                                                isIgnorableHexsideTerrain(status.targetHex, status.currentHex.getNearestLocation(status.currentCol, status.currentRow), status.result.getTargetEnterHexspine());

                                if (!ignore) {

                                    // RB rrembankment is not part of firer or target hex
                                    if (!status.currentHex.equals(status.targetHex)) {
                                        // RB rrembankment must be on opposite side of adjacent hex to block LOS
                                        Hex oppositeHex = getAdjacentHex(status.currentHex, hexside);
                                        if (!oppositeHex.equals(status.sourceHex) && !oppositeHex.equals((status.targetHex))) {
                                            if (status.groundLevel == status.sourceElevation && status.groundLevel == status.targetElevation && !status.slopes) {
                                                status.blocked = true;
                                                status.reason = "Intervening hexside terrain (B9.2)";
                                                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // no hexsides are blocking LOS
        return false;
    }

    // method added by DR to handle LOS along/across Rowhouse and Interior Factory Walls
    public boolean checkRowhouseFactoryWall (LOSStatus status,LOSResult result) {
        // must be rowhouse wall or IFW to get here
        double sourceadj=0;
        double targetadj=0;
        if (status.source.getTerrain().isRooftop()) {
            sourceadj = -1;
        }
        if (status.target.getTerrain().isRooftop()) {
            targetadj=-1;
        }
        if (status.currentTerrain.getName().contains("Interior Factory Wall") && (result.isLOSis60Degree() | result.isLOSisHorizontal())   ) {
            // special case of LOS along IFW
            int firsthexside = result.getSourceExitHexspine() + 1;
            if (firsthexside >= 6) {
                firsthexside = firsthexside - 6;
            }
            int secondhexside = firsthexside + 3;
            if (secondhexside >= 6) {
                secondhexside = secondhexside - 6;
            }

            if (!status.currentHex.equals(status.sourceHex) && !status.currentHex.equals(status.targetHex)) {

                if(status.currentTerrain.equals(status.currentHex.getHexsideTerrain(firsthexside)) || status.currentTerrain.equals(status.currentHex.getHexsideTerrain(secondhexside))){
                    Hex altHex;
                    int firstvertexside;
                    int secondvertexside;
                    int thirdvertexside;
                    int fourthvertexside;
                    if (status.currentTerrain.equals(status.currentHex.getHexsideTerrain(firsthexside))) {
                        altHex = getAdjacentHex(status.currentHex, firsthexside);
                        firstvertexside=firsthexside-1;
                        secondvertexside=firsthexside+1;
                        thirdvertexside= secondhexside-1;
                        fourthvertexside=secondhexside+1;
                    } else {
                        altHex = getAdjacentHex(status.currentHex, secondhexside);
                        firstvertexside=secondhexside-1;
                        secondvertexside=secondhexside+1;
                        thirdvertexside= firsthexside-1;
                        fourthvertexside=firsthexside+1;
                    }
                    if( firstvertexside==-1) { firstvertexside=5;}
                    if( firstvertexside==6) { firstvertexside=0;}
                    if( secondvertexside==-1) { secondvertexside=5;}
                    if( secondvertexside==6) { secondvertexside=0;}
                    if( thirdvertexside==-1) { thirdvertexside=5;}
                    if( thirdvertexside==6) { thirdvertexside=0;}
                    if( fourthvertexside==-1) { fourthvertexside=5;}
                    if( fourthvertexside==6) { fourthvertexside=0;}

                    // LOS is blocked along an Interior Factory Wall UNLESS from Rooftop or higher and one of the IFW hexes is roofless or both on rooftop or higher elevation
                    if ((status.groundLevel + status.currentTerrainHgt <= status.sourceElevation + sourceadj ||
                            status.groundLevel + status.currentTerrainHgt <= status.targetElevation + targetadj) && (status.currentHex.getCenterLocation().getTerrain().getName().contains("Roofless") || altHex.getCenterLocation().getTerrain().getName().contains("Roofless"))) {
                        // need to check now for IFW connected to IFW along hexside; these will block LOS
                        Terrain ter1 = status.currentHex.getHexsideTerrain(firstvertexside);
                        Terrain ter2 = status.currentHex.getHexsideTerrain(secondvertexside);
                        Terrain ter3 = status.currentHex.getHexsideTerrain(thirdvertexside);
                        Terrain ter4 = status.currentHex.getHexsideTerrain(fourthvertexside);

                        if(( ter1 != null && status.currentHex.getHexsideTerrain(firstvertexside).isRowhouseFactoryWall() && !status.sourceHex.equals(getAdjacentHex(status.currentHex, firstvertexside))) ||
                                ( ter2 !=null && status.currentHex.getHexsideTerrain(secondvertexside).isRowhouseFactoryWall() && !status.sourceHex.equals(getAdjacentHex(status.currentHex, secondvertexside))) ||
                                ( ter3 !=null && status.currentHex.getHexsideTerrain(thirdvertexside).isRowhouseFactoryWall() && !status.sourceHex.equals(getAdjacentHex(altHex, thirdvertexside))) ||
                                ( ter4 !=null && status.currentHex.getHexsideTerrain(fourthvertexside).isRowhouseFactoryWall() && !status.sourceHex.equals(getAdjacentHex(altHex, fourthvertexside)))) {
                            if(isBlindHex(status, status.currentTerrainHgt)) {
                                return true;
                            }
                            else {
                                return false;
                            }
                        }
                        return false;
                    } else if (status.groundLevel + status.currentTerrainHgt <= status.sourceElevation + sourceadj && status.groundLevel + status.currentTerrainHgt <= status.targetElevation + targetadj) {
                        return false;
                    } else {
                        return true;
                    }
                    // }
                }
                else {
                    // have to handle IFW here because can't call getAdjacentHex from within isBlindHex
                    // deal with rooftop los across IFW DR
                    boolean sourcehexsidetest= (status.currentHex.equals(getAdjacentHex(status.sourceHex, status.sourceExitHexsides[0])) || status.currentHex.equals(getAdjacentHex(status.sourceHex, status.sourceExitHexsides[1])));
                    boolean targethexsidetest= (status.currentHex.equals(getAdjacentHex(status.targetHex, status.targetEnterHexsides[0])) || status.currentHex.equals(getAdjacentHex(status.targetHex, status.targetEnterHexsides[1])));
                    if(status.source.getTerrain().isRooftop()  && (status.rangeToSource==0 | (status.rangeToSource==1 && sourcehexsidetest ))) {

                        return false;
                    }
                    else if(status.target.getTerrain().isRooftop() && (status.rangeToTarget==0 | (status.rangeToTarget==1 && targethexsidetest))) {
                        return false;
                    }
                    // moved code below from checkHexsideTerrainRule to keep all rowhouse/IF walls in one method
                    else if (  //now deal with all non-special situations
                        //higher than both source/target
                            (status.groundLevel + status.currentTerrainHgt > status.sourceElevation + sourceadj &&
                                    status.groundLevel + status.currentTerrainHgt > status.targetElevation + targetadj) ||
                                    //same height as both source/target, but 1/2 level
                                    (status.groundLevel + status.currentTerrainHgt == status.sourceElevation + sourceadj &&
                                            status.groundLevel + status.currentTerrainHgt == status.targetElevation + targetadj &&
                                            !(status.source.getTerrain().isRooftop() && status.target.getTerrain().isRooftop()) &&
                                            status.currentTerrain.isHalfLevelHeight()) ||
                                    //same height as higher source/target, but other is lower
                                    (status.groundLevel + status.currentTerrainHgt == Math.max(status.sourceElevation + sourceadj, status.targetElevation + targetadj) &&
                                            status.groundLevel + status.currentTerrainHgt > Math.min(status.sourceElevation + sourceadj, status.targetElevation + targetadj))
                            ) {
                        return true;
                    }
                    // otherwise check for blind hexes
                    else if (isBlindHex(status, status.currentTerrainHgt)) {
                        return true;
                    }
                }
            }
            else {
                return false;
            }
        }
        else {
                // have to handle IFW here because can't call getAdjacentHex from within isBlindHex
                // deal with rooftop los across IFW DR
                boolean sourcehexsidetest= status.currentHex.equals(getAdjacentHex(status.sourceHex, status.sourceExitHexsides[0]));
                boolean targethexsidetest= status.currentHex.equals(getAdjacentHex(status.targetHex, status.targetEnterHexsides[0]));
                if(status.source.getTerrain().isRooftop()  && (status.rangeToSource==0 || (status.rangeToSource==1 && sourcehexsidetest ))) {
                    return false;
                }
                else if(status.target.getTerrain().isRooftop() && (status.rangeToTarget==0 || (status.rangeToTarget==1 && targethexsidetest))) {
                    return false;
                }
                // moved code below from checkHexsideTerrainRule to keep all rowhouse/IF walls in one method
                else if (  //now deal with all non-special situations
                    //higher than both source/target
                    (status.groundLevel + status.currentTerrainHgt > status.sourceElevation + sourceadj &&
                            status.groundLevel + status.currentTerrainHgt > status.targetElevation + targetadj) ||
                            //same height as both source/target, but 1/2 level
                            (status.groundLevel + status.currentTerrainHgt == status.sourceElevation + sourceadj &&
                                    status.groundLevel + status.currentTerrainHgt == status.targetElevation + targetadj &&
                                    !(status.source.getTerrain().isRooftop() && status.target.getTerrain().isRooftop()) &&
                                    status.currentTerrain.isHalfLevelHeight()) ||
                            //same height as higher source/target, but other is lower
                            (status.groundLevel + status.currentTerrainHgt == Math.max(status.sourceElevation + sourceadj, status.targetElevation + targetadj) &&
                                    status.groundLevel + status.currentTerrainHgt > Math.min(status.sourceElevation + sourceadj, status.targetElevation + targetadj))
                    ) {
                    return true;
                }
                // otherwise check for blind hexes
                else {
                    if (status.sourceElevation > status.targetElevation) {
                        int testhexside = status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentCol, status.currentRow));
                        Hex testhex = getAdjacentHex(status.currentHex, testhexside);
                        int testrange = range(status.sourceHex, testhex);
                        int oldrange = status.rangeToSource;
                        if (status.rangeToSource > testrange) {
                            status.rangeToSource = testrange;
                        }
                        if (isBlindHex(status, status.currentTerrainHgt)) {
                            status.rangeToSource = oldrange;
                            return true;
                        } else {
                            status.rangeToSource = oldrange;
                        }
                    } else if (status.targetElevation > status.sourceElevation) {
                        int testhexside = status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentCol, status.currentRow));
                        Hex testhex = getAdjacentHex(status.currentHex, testhexside);
                        int testrange = range(status.sourceHex, testhex);
                        int oldrange = status.rangeToTarget;
                        if (status.rangeToTarget > testrange) {
                            status.rangeToTarget = testrange;
                        }
                        if (isBlindHex(status, status.currentTerrainHgt)) {
                            status.rangeToTarget = oldrange;
                            return true;
                        } else {
                            status.rangeToTarget = oldrange;
                        }

                    }
                }
        }
        // LOS is not blocked
        return false;
    }
}


