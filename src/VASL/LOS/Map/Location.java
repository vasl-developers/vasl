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
	private int			baseHeight;
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

    public Location(
            String n,
            int hgt,
            Point LOSpt,
            Point auxLOSpt,
            Point edgept,
            Hex hex,
            Terrain terr) {

        name			= n;
        baseHeight		= hgt;
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

	public	int	 	getBaseHeight() {return baseHeight;}
	public	void	setBaseHeight(int newBaseHeight) {baseHeight = newBaseHeight;}
	public	int	 	getAbsoluteHeight() {return baseHeight + hex.getBaseHeight();}

	public Terrain  getTerrain() {return terrain;}
	public	void 	setTerrain(Terrain newTerrain) {terrain = newTerrain;}

	public Hex getHex() {return hex;}

	public	Point	getLOSPoint() {return LOSPoint;}
	public	Point	getAuxLOSPoint() {return auxLOSPoint;}
	public	Point	getEdgeCenterPoint() {return edgeCenterPoint;}

	public Terrain  getDepressionTerrain(){ return depressionTerrain;}
	public	void	setDepressionTerrain(Terrain newDepressionTerrain){

		// removing depression terrain?
		if (newDepressionTerrain == null){

			// ensure the location base elevation is the same as the center
			if(hex.getCenterLocation().isDepressionTerrain()){
				baseHeight = 1;
			}
			else {
				baseHeight = 0;
			}
		}

		// adding depression terrain?
		else if (depressionTerrain == null) {

			// set the location height same as center
			baseHeight = 0;

		}

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

	public void copyLocation(Location l) {

		// copy the flags
		baseHeight 		= l.getBaseHeight();

		// copy name, terrain values
		name				= l.getName();
		baseHeight			= l.getBaseHeight();
		terrain 			= l.getTerrain();
		depressionTerrain 	= l.getDepressionTerrain();
	}

    public boolean auxLOSPointIsCloser(int x, int y) {

        return Point.distance(x, y, LOSPoint.x, LOSPoint.y) >
               Point.distance(x, y, auxLOSPoint.x, auxLOSPoint.y);

        }

}

