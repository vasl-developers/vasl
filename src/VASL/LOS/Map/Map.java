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

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.HashMap;
import java.util.HashSet;

import VASL.build.module.map.boardPicker.board.ASLHexGrid;

import static java.lang.StrictMath.cos;

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
@SuppressWarnings("ALL")
public class Map  {

	// hex geometry
	public static final double DEFAULT_HEX_HEIGHT = 64.5;
	public static final double DEFAULT_HEX_WIDTH = 56.265;
	private double hexHeight = DEFAULT_HEX_HEIGHT;
	private double hexWidth = DEFAULT_HEX_WIDTH;
	private double A1CenterX;
	private double A1CenterY;

	// todo: used only by hex - move to hex object
	private double h;

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


	/**
	 * Constructs a new <code>Map</code> object using custom hex size and/or offset.
	 * A standard geomorphic map board is 10 x 33 hexes.
	 * @param width the width of the map in hexes
	 * @param height the height of the map in hexes
	 * @param xOffset x-offset of the A1 hex center dot
	 * @param yOffset y-offset of the A1 hex center dot
	 * @param hexHeight the height of a hex in board pixels
	 * @param terrainNameMap mapping of terrain names to terrain objects
	 */
	public Map(int width, int height, int xOffset, int yOffset, double hexHeight, HashMap<String, Terrain> terrainNameMap){

		this.width = width;
		this.height = height;

		//Set the hex geometry
		this.hexHeight = hexHeight;
		A1CenterY = hexHeight /2.0;
		h = A1CenterY /cos(Math.toRadians(30.0)) * 1.00727476217123670956911024062675; // cludge for standard geo board
		A1CenterX = h /2.0;
		hexWidth = h + A1CenterX;

		// get the terrain
		setTerrain(terrainNameMap);

		// create a VASL hex grid
		ASLHexGrid aslHexGrid = new ASLHexGrid(hexHeight, false);
		Hex[][] tempHexGrid = new Hex[width + 2][height + 2];

		// default values are zero, so level zero and open ground
		gridWidth = (int) (((double) width - 1.0) * hexWidth);
		gridHeight = (int) ((double) height * hexHeight);

		// use the image size if possible

		terrainGrid = new char[gridWidth][gridHeight];
		elevationGrid = new byte[gridWidth][gridHeight];

		// create the hex grid
		hexGrid = new Hex[this.width][];
		for (int col = 0; col < this.width; col++) {

			hexGrid[col] = new Hex[this.height + (col % 2)]; // add 1 if odd
			for (int row = 0; row < this.height + (col % 2); row++) {
				hexGrid[col][row] = new Hex(col, row, this, 0, terrainList[0]);
			}
		}

		// create the hex locations
		for (int col = 0; col < this.width; col++) {
			for (int row = 0; row < this.height + (col % 2); row++) {
				hexGrid[col][row].resetHexsideLocationNames();
			}
		}
	}

	/**
	 * Constructs a new <code>Map</code> object using custom hex size and explicite image size.
	 * A standard geomorphic map board is 10 x 33 hexes.
	 * @param width the width of the map in hexes
	 * @param height the height of the map in hexes
	 * @param xOffset x-offset of the A1 hex center dot
	 * @param imageWidth width of the board image in pixels
	 * @param imageHeight height of the board image in pixels
	 * @param yOffset y-offset of the A1 hex center dot
	 * @param hexHeight the height of a hex in board pixels
	 * @param terrainNameMap mapping of terrain names to terrain objects
	 */
	public Map(int width, int height, int xOffset, int yOffset, int imageWidth, int imageHeight, double hexHeight, HashMap<String, Terrain> terrainNameMap){

		this.width = width;
		this.height = height;

		//Set the hex geometry
		this.hexHeight = hexHeight;
		A1CenterY = hexHeight /2.0;
		h = A1CenterY /cos(Math.toRadians(30.0)) * 1.00727476217123670956911024062675; // cludge for standard geo board
		A1CenterX = h /2.0;
		hexWidth = h + A1CenterX;

		// get the terrain
		setTerrain(terrainNameMap);

		// default values are zero, so level zero and open ground
		gridWidth = imageWidth;
		gridHeight = imageHeight;
		terrainGrid = new char[gridWidth][gridHeight];
		elevationGrid = new byte[gridWidth][gridHeight];

		// create the hex grid
		hexGrid = new Hex[this.width][];
		for (int col = 0; col < this.width; col++) {

			hexGrid[col] = new Hex[this.height + (col % 2)]; // add 1 if odd
			for (int row = 0; row < this.height + (col % 2); row++) {
				hexGrid[col][row] = new Hex(col, row, this, 0, terrainList[0]);
			}
		}

		// create the hex locations
		for (int col = 0; col < this.width; col++) {
			for (int row = 0; row < this.height + (col % 2); row++) {
				hexGrid[col][row].resetHexsideLocationNames();
			}
		}
	}

	/**
     * Constructs a new <code>Map</code> object using the default hex size.
	 * A standard geomorphic map board is 10 x 33 hexes.
     * @param w the width of the map in hexes
     * @param h the height of the map in hexes
     */
    public Map(int w, int h, HashMap<String, Terrain> terrainNameMap) {

		this(w, h, 0, 0, DEFAULT_HEX_HEIGHT, terrainNameMap);

    }


	/**
	 * @return the hex height
	 */
	public double getHexHeight() {
		return hexHeight;
	}


	/**
	 * @return the hex width
	 */
	public double getHexWidth() {
		return hexWidth;
	}


	/**
	 * @return the y component of the hex A1 center dot
	 */
	public double getA1CenterY() {
		return A1CenterY;
	}


	/**
	 * @return the distance from the hex center dot to the vertex
	 */
	public double getH() {
		return h;
	}

	/**
	 * @return the x component of the hex A1 center dot
	 */
	public double getA1CenterX() {
		return A1CenterX;
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
     * @return the ground level at (row, col)
     */
    public int getGridElevation(int row, int col) {

        // ground level
        return (int) elevationGrid[row][col];
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

				final Hex hex = getHex(col, row);

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
            @SuppressWarnings("unused")
            final Hex temp = hexGrid[col][row];
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
        final boolean colIsEven = (col % 2 == 0);

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

		final int z = (int) ((double) x / (hexWidth / 3.0));
		final int row;
		final int col;

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
    }

    /**
     * Returns the range between two hexes.
     * @param source source hex
     * @param target "target" hex
     * @return the range
     */
    public static int range(Hex source, Hex target) {

        final int dirX = target.getColumnNumber() > source.getColumnNumber() ? 1 : -1;
        final int dirY = target.getRowNumber() > source.getRowNumber() ? 1 : -1;

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

        final LOSStatus status = new LOSStatus(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, VASLGameInterface);

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
            final int numRows = Math.abs((int) status.exit - (int) status.enter) + 1;

            // step through the current row
            for (int row = 0; row < numRows; row++) {

                status.currentTerrain = getGridTerrain(status.currentCol, status.currentRow);
                status.groundLevel = (int) elevationGrid[status.currentCol][status.currentRow];

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

                // If LOS on a hexside fetch the two adjacent hexes
                AdjacentHexes adjacentHexes = null;
                if(status.LOSisHorizontal || status.LOSis60Degree) {

                    adjacentHexes = getAdjacentHexes(status, status.tempHex);
                }

                // check the LOS rules for this point and return if blocked
                if(adjacentHexes == null){

                    if (checkPointLOSRules(status, result)) {
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

        // force check of hexside terrain for hexes with inherent terrain
        // (e.g. for case where orchard hex has wall)
        if(status.currentTerrain.isHexsideTerrain() && hex.getCenterLocation().getTerrain().isInherentTerrain()) {
            if (checkPointLOSRules(status, result)) {
                return true;
            }
        }

        // ensure inherent terrain is not missed
        if(status.tempHex.getCenterLocation().getTerrain().isInherentTerrain()) {
            status.currentTerrain = status.tempHex.getCenterLocation().getTerrain();
        }

        return checkPointLOSRules(status, result);
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

        final Hex sourceHex = status.source.getHex();

        // ensure we're truly on a hexside
        if(status.sourceHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow) ||
           status.targetHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow) ||
          (status.source.getHex().getCenterLocation().equals(status.source) &&  range(sourceHex, rangeHex)%2 == 0)) {
            return null;
        }

        for(int x = 0; x < 6; x++){
            final Hex temp = getAdjacentHex(rangeHex, x);
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
        public VASL.LOS.Map.VASLGameInterface VASLGameInterface;

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

        // Exiting depression restriction placed when looking out of a depression
        // to a higher elevation where the "elevation difference <= range" restriction has not
        // been satisfied. Must be satisfied before leaving the depression.
        public boolean exitsSourceDepression;

        public Hex ignoreGroundLevelHex = null;

        // Entering depression restriction placed when looking into a depression
        // reverse of above
        public boolean entersTargetDepression;

        // grid column entry/exit points
        public double enter;
        public double exit;
        public int currentCol;
        public int currentRow;

        private LOSStatus(Location source, boolean useAuxSourceLOSPoint, Location target, boolean useAuxTargetLOSPoint, LOSResult result, VASLGameInterface VASLGameInterface) {

            this.source = source;
            this.useAuxSourceLOSPoint = useAuxSourceLOSPoint;
            this.target = target;
            this.useAuxTargetLOSPoint = useAuxTargetLOSPoint;
            this.result = result;
            this.VASLGameInterface = VASLGameInterface;

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

            LOSLeavesBuilding = !sourceHex.getCenterLocation().getTerrain().isBuildingTerrain();

            // "rise" per grid column
            deltaY = ((double) targetY - (double) sourceY) / (double) numCols;

            // Exiting depression restriction placed when looking out of a depression
            // to a higher elevation where the "elevation difference <= range" restriction has not
            // been satisfied. Must be satisfied before leaving the depression.
            exitsSourceDepression =
                    source.isDepressionTerrain() &&
                            (targetElevation < sourceElevation ||
                                    (targetElevation - sourceElevation > 0 &&
                                            targetElevation - sourceElevation < range) ||
                                    (source.isDepressionTerrain() &&
                                            target.isDepressionTerrain() &&
                                            targetElevation == sourceElevation));

            // Entering depression restriction placed when looking into a depression
            // reverse of above
            entersTargetDepression =
                    target.isDepressionTerrain() &&
                            sourceElevation - targetElevation > 0 &&
                            sourceElevation - targetElevation < range;

            // grid column entry/exit points
            enter = (double)sourceY;
            exit = enter + deltaY;

            // initialize some result variables
            result.setRange(rangeToTarget);
            result.setSourceExitHexside(LOSResult.UNKNOWN);
            result.setTargetEnterHexside(LOSResult.UNKNOWN);

            // LOS slope variables
            LOSisHorizontal = (sourceY == targetY);
            result.setLOSisHorizontal(LOSisHorizontal);
            final double doubleSourceX = useAuxSourceLOSPoint ? source.getAuxLOSPoint().getX() : source.getLOSPoint().getX();
			final double doubleSourceY = useAuxSourceLOSPoint ? source.getAuxLOSPoint().getY() : source.getLOSPoint().getY();
			final double doubleTargetX = useAuxTargetLOSPoint ? target.getAuxLOSPoint().getX() : target.getLOSPoint().getX();
			final double doubleTargetY = useAuxTargetLOSPoint ? target.getAuxLOSPoint().getY() : target.getLOSPoint().getY();
			final double slope = Math.abs((doubleSourceY - doubleTargetY) / (doubleSourceX - doubleTargetX));

            // set the tolerance to compensate for "fuzzy" geometry of VASL boards
            //TODO - this is a cludge and fails at very long ranges - replace with algorithm
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
        }
    }

    /**
     * Applies the LOS rules to a single point; adjusts the status if the LOS enters a new hex
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkPointLOSRules(LOSStatus status, LOSResult result) {


        // if there's a terrain counter in the hex use that terrain instead
        if(status.VASLGameInterface != null && status.VASLGameInterface.getTerrain(status.tempHex) != null) {
            status.currentTerrain = status.VASLGameInterface.getTerrain(status.tempHex);
        }

        status.currentTerrainHgt = status.currentTerrain.getHeight();

        // are we in a new hex?
        if (!status.tempHex.equals(status.currentHex)) {

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

            // lift the depression exit restriction?
            if (status.exitsSourceDepression) {

                if ((status.currentHex.isDepressionTerrain() &&
                        status.targetElevation - status.currentHex.getBaseHeight() >= status.rangeToTarget) ||
                        // LOS leaves gully because hex elevation is <= the elevation of the gully
                        (!status.currentHex.isDepressionTerrain() &&
                                status.currentHex.getBaseHeight() <= status.sourceElevation)) {

                    status.ignoreGroundLevelHex = status.currentHex;
                    status.exitsSourceDepression = false;
                }
            }
        }

        // check the LOS rules
        if (checkDepressionRule(status, result)) {
            return true;
        }
        if (checkBuildingRestrictionRule(status, result)) {
            return true;
        }
        if (status.currentTerrain.isHexsideTerrain() && !"Cliff".equals(status.currentTerrain.getName())) {
            if (checkHexsideTerrainRule(status, result)) {
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
        }

        // set results if blocked
        if (status.blocked) {
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
        }
        return false;
    }

    /**
     * Applies the LOS rules to a single point
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

                final HashSet<Smoke> hexSmoke = status.VASLGameInterface.getSmoke(status.source.getHex());
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

            final Hex hex = status.currentHex;

            final HashSet<Smoke> hexSmoke = status.VASLGameInterface.getSmoke(hex);
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
                        else if (isBlindHex(
                                status.sourceElevation,
                                status.targetElevation,
                                status.rangeToSource,
                                status.rangeToTarget,
                                status.groundLevel,
                                s.getHeight())){

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

            final Hex hex = status.currentHex;

            final HashSet<Vehicle> vehicles = status.VASLGameInterface.getVehicles(hex);
            if (vehicles != null && !vehicles.isEmpty()) {

                int hindrance = 0;
                for (Vehicle v: vehicles) {

                    // skip source/target hex
                    if(!hex.equals(status.sourceHex) && !hex.equals(status.targetHex)) {

                        // vehicle must be same elevation as both source and target
                        if(status.source.getAbsoluteHeight() == status.target.getAbsoluteHeight() &&
                           status.source.getAbsoluteHeight() == v.getLocation().getAbsoluteHeight()){

                            // if vehicle in bypass the LOS must cross the bypassed hexside
                            if(v.getLocation().isCenterLocation() ||
                                    (!v.getLocation().isCenterLocation() &&
										v.getLocation().equals(hex.getNearestLocation(status.currentCol, status.currentRow)))) {


                                // both source and target must have an LOS to the vehicle
                                final LOSResult result1 = new LOSResult();
                                final LOSResult result2 = new LOSResult();
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

            final HashSet<OBA> obaList = status.VASLGameInterface.getOBA();
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
                        else if (isBlindHex(
                                status.sourceElevation,
                                status.targetElevation,
                                status.rangeToSource,
                                status.rangeToTarget,
                                status.groundLevel,
                                oba.getBlastHeight())){

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
    protected boolean checkBuildingRestrictionRule(LOSStatus status, LOSResult result) {

        // blocked LOS leaving a building?
        if (!status.LOSLeavesBuilding &&
			!status.currentHex.equals(status.sourceHex) &&
                status.currentTerrain.isBuilding() &&
                status.target.getTerrain().isBuildingTerrain() &&
                status.sourceElevation != status.targetElevation &&
                status.groundLevel + status.currentTerrainHgt >= status.sourceElevation
                ) {
            status.reason = "LOS must leave the building before leaving the source hex to see a location with a different elevation (A6.8 Example 2)";
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

        // restricted by exiting a depression? (checked in all hexes)
        if (status.exitsSourceDepression) {

            // LOS still in the depression?
            if (status.groundLevel > status.currentHex.getBaseHeight()) {

                status.blocked = true;
                status.reason = "Exits depression before range/elevation restrictions are satisfied (A6.3)";
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
        }

        // LOS must enter a depression?
        // range must be <= elevation difference or be in the depression
        if (status.entersTargetDepression) {
            if (status.rangeToSource > (status.sourceElevation - status.targetElevation) &&
                    !(status.currentHex.isDepressionTerrain() && status.groundLevel == status.currentHex.getBaseHeight())) {

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
        // rowhouse wall?
        if (status.currentTerrain.isRowhouseWall()) {

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
                                    status.groundLevel + status.currentTerrainHgt > Math.min(status.sourceElevation, status.targetElevation))
                    ) {

                status.reason = "Cannot see through rowhouse wall (B23.71)";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }

            // otherwise check for blind hexes
            else if (isBlindHex(
                    status.sourceElevation,
                    status.targetElevation,
                    status.rangeToSource,
                    status.rangeToTarget,
                    status.groundLevel,
                    status.currentTerrainHgt
            )) {

                status.reason = "Source or Target location is in a blind hex";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
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
            else {

                // should we ignore the hexside terrain?
                final boolean ignore =
                        isIgnorableHexsideTerrain(status.sourceHex, status.currentHex.getNearestLocation(status.currentCol, status.currentRow), status.result.getSourceExitHexspine()) ||
                        isIgnorableHexsideTerrain(status.targetHex, status.currentHex.getNearestLocation(status.currentCol, status.currentRow), status.result.getTargetEnterHexspine());

                if (!ignore) {

                    // check bocage
                    if ("Bocage".equals(status.currentTerrain.getName())) {

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
                                                status.groundLevel + status.currentTerrainHgt > Math.min(status.sourceElevation, status.targetElevation))
                                ) {

                            status.reason = "Cannot see through/over bocage (B9.52)";
                            status.blocked = true;
                            result.setBlocked(status.currentCol, status.currentRow, status.reason);
                            return true;
                        }

                        // otherwise check for blind hexes
                        else if (isBlindHex(
                                status.sourceElevation,
                                status.targetElevation,
                                status.rangeToSource,
                                status.rangeToTarget,
                                status.groundLevel,
                                status.currentTerrainHgt
                        )) {

                            status.reason = "Source or Target location is in a blind hex (B9.52)";
                            status.blocked = true;
                            result.setBlocked(status.currentCol, status.currentRow, status.reason);
                            return true;
                        }
                    }

                    // on the same level?
                    else if (status.groundLevel == status.sourceElevation && status.groundLevel == status.targetElevation) {

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
        if (status.groundLevel + status.currentTerrainHgt > Math.min(status.sourceElevation, status.targetElevation) &&
            status.groundLevel + status.currentTerrainHgt < Math.max(status.sourceElevation, status.targetElevation)
                ) {

            if (isBlindHex(
				status.sourceElevation,
				status.targetElevation,
				status.rangeToSource,
				status.rangeToTarget,
				status.groundLevel,
				status.currentTerrainHgt,
				nearestHexsideIsCliff(status.currentCol, status.currentRow)
			)) {

                // blocked if terrain is obstacle
                if (status.currentTerrain.isLOSObstacle()) {

                    // ignore inherent terrain that is not the same as center location
                    if(!status.currentTerrain.isInherentTerrain() ||
                      ( status.currentTerrain.isInherentTerrain() && status.currentHex.getCenterLocation().getTerrain().equals(status.currentTerrain))) {

                        status.reason = "Source or Target location is in a blind hex (A6.4)";
                        status.blocked = true;
                        result.setBlocked(status.currentCol, status.currentRow, status.reason);
                        return true;
                    }
                }

                // see if ground level alone creates blind hex
                else if (status.groundLevel > Math.min(status.sourceElevation, status.targetElevation) &&
                        status.groundLevel < Math.max(status.sourceElevation, status.targetElevation) &&
                        isBlindHex(
                                status.sourceElevation,
                                status.targetElevation,
                                status.rangeToSource,
                                status.rangeToTarget,
                                status.groundLevel,
                                0,
                                nearestHexsideIsCliff(status.currentCol, status.currentRow)
                        )
                        ) {
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

        if (status.groundLevel + status.currentTerrainHgt == Math.max(status.sourceElevation, status.targetElevation) &&
            status.groundLevel + status.currentTerrainHgt > Math.min(status.sourceElevation, status.targetElevation) &&
                // are exiting gully restrictions satisfied?
                !(status.ignoreGroundLevelHex != null &&
                  status.ignoreGroundLevelHex.containsExtended(status.currentCol, status.currentRow)) &&
                // are entering gully restrictions satisfied?
                !(status.entersTargetDepression && status.currentHex.isDepressionTerrain()) &&
                !(status.exitsSourceDepression && status.currentHex.isDepressionTerrain())
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

                }
                else {
                    status.reason = "Must have a height advantage to see over this terrain (A6.2)";
                    status.blocked = true;
                    result.setBlocked(status.currentCol, status.currentRow, status.reason);
                    return true;
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

        // ignore special case for split terrain - different rule
        if(status.currentTerrain.hasSplit() &&
                status.groundLevel == status.sourceElevation &&
                status.groundLevel == status.targetElevation){
            return false;
        }

        if (status.groundLevel + status.currentTerrainHgt > status.sourceElevation &&
                status.groundLevel + status.currentTerrainHgt > status.targetElevation) {

            // terrain blocks LOS?
            if (status.currentTerrain.isLOSObstacle()) {
                status.reason = "Terrain is higher than both the source and target (A6.2)";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
            // must be hindrance
            else {

                // add hindrance
                if (addHindranceHex(status, result))
                    return true;
            }
        }
        return false;
    }

    /**
     * Checks rules for half-level terrain
     * @param status the LOS status
     * @param result the LOS result
     * @return true if the LOS is blocked
     */
    protected boolean checkHalfLevelTerrainRule(LOSStatus status, LOSResult result) {

        if (status.currentTerrain.isHalfLevelHeight() &&
                !status.currentTerrain.isHexsideTerrain() &&
                status.groundLevel + status.currentTerrainHgt == status.sourceElevation &&
                status.groundLevel + status.currentTerrainHgt == status.targetElevation) {

            // terrain blocks LOS?
            if (status.currentTerrain.isLOSObstacle()) {
                status.reason = "Half level terrain is higher than both the source and target (A6.2)";
                status.blocked = true;
                result.setBlocked(status.currentCol, status.currentRow, status.reason);
                return true;
            }
            // must be hindrance
            else {

                // add hindrance
                if (addHindranceHex(status, result))
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

        if (status.currentTerrain.hasSplit() &&
                status.groundLevel == status.sourceElevation &&
                status.groundLevel == status.targetElevation) {

            if (status.currentTerrain.isLowerLOSObstacle()) {

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
        if (status.groundLevel > status.sourceElevation && status.groundLevel > status.targetElevation) {

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
            final Hex h2 = getAdjacentHex(h, x);
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
            final Hex h2 = getAdjacentHex(h, x);
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
        final Terrain locationHexsideTerrain = locationHex.getHexsideTerrain(locationHexside);

        // too far away?
        if (range(h, locationHex) > 2) {

            return false;
        }
        // always ignore if adjacent
        if (isAdjacentHexside(h, l)) {

            return true;
        }
        // ignore hexspines if not bocage
        if (isHexspine(h, l) && !(locationHexsideTerrain != null && "Bocage".equals(locationHexsideTerrain.getName()))) {

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
                final Hex oppositeHex = getAdjacentHex(locationHex, locationHexside);

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

            final int hexside = LOSHexspine == 0 ? 5 : LOSHexspine - 1;
            final int hexspine = LOSHexspine < 2 ? LOSHexspine + 4 : LOSHexspine - 2;

            final Hex hex1 = getAdjacentHex(h, hexside);
            final Hex hex2 = getAdjacentHex(h, LOSHexspine);

            if(hex1 == null || hex2 == null) return false;

            final Location l2 = hex1.getHexsideLocation(LOSHexspine);
            final Location l3 = hex2.getHexsideLocation(hexside);

            final Terrain t1 = hex2.getHexsideTerrain(hexspine);
            final Terrain t2 = hex1.getHexsideTerrain(LOSHexspine);
            final Terrain t3 = hex2.getHexsideTerrain(hexside);

           return t1 != null && (l.equals(l2) || l.equals(l3)) && (t2 == null || t3 == null);
       }
        return false;
    }

    /**
     * @param x x coordinate
     * @param y y coordinate
     * @return true if the nearest hexside is a cliff
     */
    protected boolean nearestHexsideIsCliff(int x, int y) {

        final Hex hex = gridToHex(x, y);
        final Location l = gridToHex(x, y).getNearestLocation(x, y);

        return !l.isCenterLocation() && hex.hasCliff(hex.getLocationHexside(l));
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

                result.addMapHindrance(status.currentHex, status.currentCol, status.currentRow);

                // see if hindrance caused LOS to be blocked
                return result.isBlocked();
            }
        }
        return false;
    }

    protected static boolean isBlindHex(
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

    protected static boolean isBlindHex(
		int sourceElevation,
		int targetElevation,
		int rangeToSource,
		int rangeToTarget,
		int groundLevel,
		int currentTerrainHgt,
		boolean isCliffHexside
	) {

		// blind hex NA for same-level LOS
        if(sourceElevation == targetElevation){
            return false;
        }

        // if LOS raising, swap source/target and use the same logic as LOS falling
        if (sourceElevation < targetElevation) {

            // swap elevations
			int temp = sourceElevation;
			sourceElevation = targetElevation;
            targetElevation = temp;

            // swap range
            temp = rangeToSource;
            rangeToSource = rangeToTarget;
            rangeToTarget = temp;
        }


        // is the obstacle a non-cliff crestline?
        if (currentTerrainHgt == 0 && !isCliffHexside) {

            return rangeToTarget <= Math.max(2 * (groundLevel + currentTerrainHgt) + (rangeToSource / 5) - sourceElevation - targetElevation, 0);
        }
        else {

            return rangeToTarget <= Math.max(2 * (groundLevel + currentTerrainHgt) + (rangeToSource / 5) - sourceElevation - targetElevation + 1, 1);
        }
    }

    /**
     * Sets the terrain name map and populate the terrain list
     */
	public final void setTerrain(HashMap<String, Terrain> nameMap) {

		//noinspection AssignmentToStaticFieldFromInstanceMethod
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

				final char terrain = terrainGrid[x][y];
				terrainGrid[x][y] = terrainGrid[gridWidth - x - 1][gridHeight - y - 1];
                terrainGrid[gridWidth - x - 1][gridHeight - y - 1] = terrain;

				final byte elevation = elevationGrid[x][y];
				elevationGrid[x][y] = elevationGrid[gridWidth - x - 1][gridHeight - y - 1];
                elevationGrid[gridWidth - x - 1][gridHeight - y - 1] = elevation;
            }
        }

        // flip the hex grid
        for (int x = 0; x < hexGrid.length / 2 + 1; x++) {
            for (int y = 0; y < (x == hexGrid.length / 2 ? (hexGrid[x].length - 1) / 2 + 1 : hexGrid[x].length); y++) {

                // get the next two hexes
				final Hex h1 = hexGrid[x][y];
				final Hex h2 = hexGrid[width - x - 1][hexGrid[width - x - 1].length - y - 1];

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
        final int left = upperLeft.getCenterLocation().getLOSPoint().x;
        final int upper = upperLeft.getCenterLocation().getLOSPoint().y - (int) hexHeight / 2;

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
        final int hexRow = upperLeft.getRowNumber();
        final int hexCol = upperLeft.getColumnNumber();
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
    public Map crop(Point upperLeft, Point lowerRight){

        final int localGridWidth = lowerRight.x - upperLeft.x;
        final int localGridHeight = lowerRight.y - upperLeft.y;
        int localHexWidth = (int) Math.round((double)localGridWidth / getHexWidth()) + 1;
        final int localHexHeight = (int) Math.round((double)localGridHeight / hexHeight);

        // the hex width must be odd - if not extend to include the next half hex
        if (localHexWidth%2 != 1) {
            localHexWidth++;
        }

        final Map newMap = new Map(localHexWidth, localHexHeight, terrainNameMap);

        // copy the map grid
        for(int x = 0; x < newMap.gridWidth; x++) {
            for(int y = 0; y < newMap.gridHeight; y++){

                newMap.terrainGrid[x][y] = terrainGrid[x + upperLeft.x][y + upperLeft.y];
                newMap.elevationGrid[x][y] = elevationGrid[x + upperLeft.x][y + upperLeft.y];
            }
        }

        //copy the hex grid
        final Hex upperLeftHex = gridToHex(upperLeft.x, upperLeft.y);
        for (int x = 0; x < newMap.hexGrid.length; x++) {
            for (int y = 0; y < newMap.hexGrid[x].length; y++) {

                newMap.hexGrid[x][y] = (hexGrid[x + upperLeftHex.getColumnNumber()][y + upperLeftHex.getRowNumber()]);
            }
        }

        return newMap;
    }
}

