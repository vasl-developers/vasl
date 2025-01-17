/*
 * Copyright (c) 2000-2003 by David Sullivan
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

/**
 * Title:        Location.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public class Location {

	// property variables
	private String  	name;
	private int levelInHex;
	private Point		LOSPoint;
	private Point		auxLOSPoint;	//auxiliary LOS point for bypass locations
	private Point		edgeCenterPoint;
	private Hex hex;

	private Terrain terrain;
	private Terrain depressionTerrain;
	private Location upLocation;
	private Location downLocation;

	// property methods
	public	String  getName(){return name;}
	public	void 	setName(String newName) {name = newName;}

    public Location(String n, int locationlevel, Point LOSpt, Point auxLOSpt, Point edgept, Hex hex, Terrain terr) {
        name			= n;
        levelInHex = locationlevel;
        LOSPoint		= LOSpt;
        auxLOSPoint		= auxLOSpt;
        edgeCenterPoint	= edgept;
        this.hex		= hex;
        terrain			= terr;
    }

    public Location(Location l) {
        // use the same points
        LOSPoint		= (Point) l.getLOSPoint().clone();
        auxLOSPoint		= (Point) l.getAuxLOSPoint().clone();
        edgeCenterPoint	= (Point) l.getEdgeCenterPoint().clone();
        hex				= l.getHex();
        copyLocation(l);
    }

    public Location(){}

	public	int getLevelInHex() {return levelInHex;}
	public	void setLevelInHex(int newLevelInHex) {levelInHex = newLevelInHex;}
	public	int	 	getAbsoluteHeight() {return levelInHex + hex.getBaseLevelofHex();}

	public Terrain  getTerrain() {return terrain;}
	public	void 	setTerrain(Terrain newTerrain) {terrain = newTerrain;}

	public Hex getHex() {return hex;}

	public	Point	getLOSPoint() {return LOSPoint;}
	public	Point	getAuxLOSPoint() {return auxLOSPoint;}
	public	Point	getEdgeCenterPoint() {return edgeCenterPoint;}

	public Terrain  getDepressionTerrain(){ return depressionTerrain;}
	public	void	setDepressionTerrain(Terrain newDepressionTerrain){
		depressionTerrain = newDepressionTerrain;
	}

	public	boolean	isDepressionTerrain() {
        return (depressionTerrain != null);
	}

	public	boolean	isCenterLocation() {
			return hex.isCenterLocation(this);
	}

	public Location getUpLocation() {return upLocation;}
	public	void	setUpLocation(Location newUpLocation) {upLocation = newUpLocation;}

	public Location getDownLocation() {return downLocation;}
	public	void	setDownLocation(Location newDownLocation) {downLocation = newDownLocation;}

	public Location getNextLevelLocation(String upOrDown) {
		if (upOrDown.equals("up")) {
			return getUpLocation();
		}
		else if (upOrDown.equals("down")) {
			return getDownLocation();
		}
		return null;
	}
	public void setNextLevelLocation (Location nextLocation, String upOrDown) {
		if (upOrDown.equals("up")) {
			setUpLocation(nextLocation);
		} else if (upOrDown.equals("down")) {
			setDownLocation(nextLocation);
		}
	}

	public void copyLocation(Location l) {

		// copy name, terrain values
		name				= l.getName();
		levelInHex 			= l.getLevelInHex();
		terrain 			= l.getTerrain();
		depressionTerrain 	= l.getDepressionTerrain();
	}

    public boolean auxLOSPointIsCloser(int x, int y) {
        return Point.distance(x, y, LOSPoint.x, LOSPoint.y) >
               Point.distance(x, y, auxLOSPoint.x, auxLOSPoint.y);
    }
}

