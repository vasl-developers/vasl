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

import VASL.LOS.counters.OBA;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Title:        LOSResult.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public class LOSResult {

	// some useful constants
	public static final int UNKNOWN = -1;

	// private variables
	protected Location sourceLocation;
    private Location targetLocation;

	private boolean blocked;
	private Point   blockedAtPoint;
	protected Point firstHindranceAt;
	private int	 	range;
	protected int	sourceExitHexspine	= UNKNOWN;
	private int	 	targetEnterHexspine	= UNKNOWN;
	private String	reason				= "";
	private boolean continuousSlope;
	protected boolean LOSis60Degree;
	private boolean LOSisHorizontal;

    // the <Integer> is the range to the hindrance hex
	protected HashMap<Integer, Integer>  mapHindrances = new HashMap<Integer, Integer>();

    // <Integer, Integer> = <range, hindrance>
	private	HashMap<Integer, Integer>	smokeHindrances	= new HashMap<Integer, Integer>();
    private	HashMap<Integer, Integer>	vehicleHindrances	= new HashMap<Integer, Integer>();
    private HashSet<OBA> obaHindrances = new HashSet<OBA>();

	// hindrance/blocked methods
	public boolean  isBlocked()				{return blocked;}
	public Point	getBlockedAtPoint()		{return blockedAtPoint;}
	public Point	firstHindranceAt()		{return firstHindranceAt;}
	public String	getReason()				{return reason;}

    public boolean isLOSisHorizontal() { return LOSisHorizontal;}
    public boolean isLOSis60Degree()   {return LOSis60Degree;}
	public boolean hasHindrance(){

        return mapHindrances.size() + smokeHindrances.size() + vehicleHindrances.size() + obaHindrances.size() > 0;
	}

    /**
     * @return total hindrances in results
     */
    public int getHindrance() {
		int hindrance=0;
		for(Integer range : mapHindrances.keySet()) {
			hindrance += mapHindrances.get(range);
		}

		// add the smoke hindrances
        for(Integer range : smokeHindrances.keySet()) {

            hindrance += smokeHindrances.get(range);
        }
        // add the vehicle hindrances
        for(Integer range: vehicleHindrances.keySet()) {
            hindrance += vehicleHindrances.get(range);
        }
        // add the OBA hindrances
        hindrance += obaHindrances.size();

		return hindrance;
	}

    /**
     * add a map hindrance hex
     * @param h the hindrance hex
     * @param x current x of LOS
     * @param y current y of LOS
     */
    public void addMapHindrance(Hex h, int hindrance, int x, int y){

        setFirstHindrance(x, y);

        // if there's already a terrain hindrance at this range replace if hindrance is greater
		Integer range = sourceLocation.getHex().getMap().range(sourceLocation.getHex(), h, sourceLocation.getHex().getMap().getMapConfiguration());
		if(!mapHindrances.containsKey(range) ||
				(mapHindrances.containsKey(range) && hindrance > mapHindrances.get(range))) {
			mapHindrances.put(range, hindrance);
		}

        setBlockedByHindrance(x, y);
	}

    /**
     * Sets the first hindrance point if needed
     * @param x current x of LOS
     * @param y current y of LOS
     */
    private void setFirstHindrance(int x, int y) {
        if(firstHindranceAt == null) {
            firstHindranceAt = new Point(x, y);
        }
    }

    /**
     * Sets the result as blocked when max hindrance reached
     * @param x current x of LOS
     * @param y current y of LOS
     */
    private void setBlockedByHindrance(int x, int y) {

        // blocked if hindrances >= 6
        if (getHindrance() >= 6) {
            setBlocked(x, y, "Hindrance total of six or more (B.10)");
        }
    }

    /**
     * Add a smoke hindrance hex
     * @param h the smoke hindrance hex
     * @param hindrance the amount of hindrance (e.g. +2 or +3)
     * @param x current x of LOS
     * @param y current y of LOS
     */
    public void addSmokeHindrance(Hex h, int hindrance, int x, int y){

        setFirstHindrance(x, y);

        // if there's already smoke at this range replace if hindrance is greater
        Integer range = sourceLocation.getHex().getMap().range(sourceLocation.getHex(), h, sourceLocation.getHex().getMap().getMapConfiguration());
        if(!smokeHindrances.containsKey(range) ||
           (smokeHindrances.containsKey(range) && hindrance > smokeHindrances.get(range))) {
            smokeHindrances.put(range, hindrance);
        }

        setBlockedByHindrance(x, y);
	}

    /**
     * Add a vehicle hindrance hex
     * @param h the vehicle hindrance hex
     * @param hindrance the amount of hindrance (e.g. the number of vehicles)
     * @param x current x of LOS
     * @param y current y of LOS
     */
    public void addVehicleHindrance(Hex h, int hindrance, int x, int y){

        setFirstHindrance(x, y);

        // if there's already a vehicle hindrance at this range replace if hindrance is greater
        Integer range = sourceLocation.getHex().getMap().range(sourceLocation.getHex(), h, sourceLocation.getHex().getMap().getMapConfiguration());
        if(!vehicleHindrances.containsKey(range) ||
                (vehicleHindrances.containsKey(range) && hindrance > vehicleHindrances.get(range))) {
            vehicleHindrances.put(range, hindrance);
        }

        setBlockedByHindrance(x, y);
    }

    /**
     * Add an OBA hindrance hex
     * @param oba the OBA counter
     * @param x current x of LOS
     * @param y current y of LOS
     */
    public void addOBAHindrance(OBA oba, int x, int y){

        setFirstHindrance(x, y);
        obaHindrances.add(oba);
        setBlockedByHindrance(x, y);
    }

    // location methods
	public void setSourceLocation(Location l){sourceLocation = l;}
	public void	setTargetLocation(Location l){targetLocation = l;}

	// hexside methods
	public void		setSourceExitHexspine(int h){sourceExitHexspine = h;}
	public void		setTargetEnterHexspine(int h){targetEnterHexspine = h;}
	public int		getSourceExitHexspine()		{return sourceExitHexspine;}
	public int		getTargetEnterHexspine()	{return targetEnterHexspine;}

	// continuous slope
	public boolean	isContinuousSlope()			{return continuousSlope;}
	public void		setContinuousSlope(boolean newContinuousSlope){
		continuousSlope = newContinuousSlope;
	}

	// LOS slope
	public void	setLOSis60Degree(boolean newLOSis60Degree){
		LOSis60Degree = newLOSis60Degree;
	}
    public void setLOSisHorizontal(boolean loSisHorizontal) {
        LOSisHorizontal = loSisHorizontal;
    }

	// range methods
	public void setRange(int r){range = r;}
	public int  getRange()	 {return range;}

	public void setBlocked(int x, int y, String	r) {
		blocked				= true;
		blockedAtPoint		= new Point(x,y);
		reason				= r;
	}

	public void reset() {

		blocked				= false;
		blockedAtPoint		= null;
		firstHindranceAt 	= null;
		range				= 0;
		reason				= "";
		continuousSlope 	= false;
		LOSis60Degree		= false;
		mapHindrances.clear();
		smokeHindrances.clear();
        vehicleHindrances.clear();
        obaHindrances.clear();
		sourceLocation		= null;
		targetLocation		= null;
		sourceExitHexspine	= UNKNOWN;
		targetEnterHexspine	= UNKNOWN;
	}

	public void resetreportingonly() {
		blocked				= false;
		blockedAtPoint		= null;
		reason				= "";
	}
}
