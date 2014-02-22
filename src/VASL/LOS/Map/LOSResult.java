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
import java.util.HashSet;
import java.util.Iterator;

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
	@SuppressWarnings("unused")
  private Location targetLocation;
	private boolean useAuxSourceLOSPoint;
	private boolean useAuxTargetLOSPoint;

	private boolean blocked;
	private Point   blockedAtPoint;
	protected Point firstHindranceAt;
	private int	 	range;
	private int	 	sourceExitHexside;
	private int	 	targetEnterHexside;
	protected int	sourceExitHexspine	= UNKNOWN;
	private int	 	targetEnterHexspine	= UNKNOWN;
	private String	reason				= "";
	private boolean continuousSlope;
	protected boolean LOSis60Degree;
	private boolean LOSisHorizontal;

	private	HashSet	hexes				= new HashSet();
	protected HashSet mapHindranceHexes	= new HashSet();
	private	HashSet	smokeHindrances		= new HashSet();
	private	HashSet	vehicleHindrances	= new HashSet();

	// hindrance/blocked methods
	public boolean  isBlocked()				{return blocked;}
	public Point	getBlockedAtPoint()		{return blockedAtPoint;}
	public Point	firstHindranceAt()		{return firstHindranceAt;}
	public String	getReason()				{return reason;}

	public boolean  hasHindrance(){

		if (mapHindranceHexes.size() + smokeHindrances.size() + vehicleHindrances.size() > 0){

			return true;
		}
		else {
			return false;
		}
	}

	public int getHindrance() {

		int hindrance = Math.min(mapHindranceHexes.size(), range) + vehicleHindrances.size();

		// compute smoke
		Iterator iter 	= smokeHindrances.iterator();
		Smoke 	 s;
		while (iter.hasNext()){

			s = (Smoke) iter.next();
			hindrance += s.getHindrance();

			// add one for being in the smoke
			if (sourceLocation.getHex() == s.getLocation().getHex() &&
				sourceLocation.getAbsoluteHeight() >= s.getLocation().getAbsoluteHeight() &&
				sourceLocation.getAbsoluteHeight() <  s.getLocation().getAbsoluteHeight() + s.getHeight()
			){

				hindrance += 1;
			}
		}

		return hindrance;
	}

	// add a map hindrance hex
	public void addMapHindrance(Hex h, int x, int y){

		// point already in the extended border of a hindrance hex?
		Iterator 	iter = mapHindranceHexes.iterator();
		boolean	found = false;
		Hex temp = null;
		while(iter.hasNext() && !found){

			temp = (Hex) iter.next();
			if(temp.getExtendedHexBorder().contains(x, y)){
				found = true;
			}
			// don't add if LOSis60Degree and the appropriate adjacent hex already has been added
			if (LOSis60Degree) {

				if (sourceExitHexspine == 0 || sourceExitHexspine == 3){

					if (h.getMap().getAdjacentHex(temp, 1) == h || h.getMap().getAdjacentHex(temp, 4) == h){

						found = true;
					}
				}
				else if (sourceExitHexspine == 1 || sourceExitHexspine == 4){

					if (h.getMap().getAdjacentHex(temp, 2) == h || h.getMap().getAdjacentHex(temp, 5) == h){

						found = true;
					}
				}
			}

			// don't add another hex having the same range to the target is already present
			if(sourceLocation.getHex().getMap().range(sourceLocation.getHex(), h) ==
			   sourceLocation.getHex().getMap().range(sourceLocation.getHex(), temp)){

			    found = true;
			}
		}

		// add hex if necessary
		if(!found){
			mapHindranceHexes.add(h);

			// set first hindrance point, if necesary
			if (firstHindranceAt == null){

				firstHindranceAt = new Point(x, y);
			}

			// blocked if hindrances >= 6
			if (getHindrance() >= 6) {

				setBlocked(x, y, "Hindrance total of six or more (B.10)");
			}
		}
	}

	// add a smoke hindrance
	public void addSmokeHindrance(Smoke s, int x, int y){

		if (!smokePresent(s)){

			boolean found = false;
			Hex smokeHex = s.getLocation().getHex();

			// don't add if LOS along hexside and smoke from adjacent hex already has been added
			if (LOSisHorizontal){

				if (smokePresent(smokeHex.getMap().getAdjacentHex(smokeHex, 0)) ||
					smokePresent(smokeHex.getMap().getAdjacentHex(smokeHex, 3))
				){
					found = true;
				}
			}
			else if (LOSis60Degree) {

				if (sourceExitHexspine == 0 || sourceExitHexspine == 3){

					if (smokePresent(smokeHex.getMap().getAdjacentHex(smokeHex, 1)) ||
						smokePresent(smokeHex.getMap().getAdjacentHex(smokeHex, 4))){

						found = true;
					}
				}
				else if (sourceExitHexspine == 1 || sourceExitHexspine == 4){

					if (smokePresent(smokeHex.getMap().getAdjacentHex(smokeHex, 2)) ||
						smokePresent(smokeHex.getMap().getAdjacentHex(smokeHex, 5))){

						found = true;
					}
				}
			}

			if (!found){

				//add smoke
				smokeHindrances.add(s);

				// set first hindrance point, if necessary
				if (firstHindranceAt == null){

					firstHindranceAt = new Point(x, y);
				}

				// blocked if hindrances >= 6
				if (getHindrance() >= 6) {

					setBlocked(x, y, "Hindrance total of six or more (B.10)");
				}
			}
		}
	}

	// add a vehicle hindrance
	public void addVehicleHindrance(Vehicle v, int x, int y, VASLGameInterface VASLGameInterface){

		// vehicle already added?
		Iterator 	iter = vehicleHindrances.iterator();
		boolean	found = false;
		while(iter.hasNext() && !found){

			Vehicle veh	= (Vehicle) iter.next();
			Hex vehHex 	= veh.getLocation().getHex();

			if(vehHex == v.getLocation().getHex()){
				found = true;

			}
			// don't add if LOSis60Degree and the appropriate adjacent hex already has been added
			else if (LOSis60Degree) {

				if (sourceExitHexspine == 0 || sourceExitHexspine == 3){

					if (vehHex.getMap().getAdjacentHex(vehHex, 1) == v.getLocation().getHex() ||
						vehHex.getMap().getAdjacentHex(vehHex, 4) == v.getLocation().getHex()){

						found = true;
					}
				}
				else if (sourceExitHexspine == 1 || sourceExitHexspine == 4){

					if (vehHex.getMap().getAdjacentHex(vehHex, 2) == v.getLocation().getHex() ||
						vehHex.getMap().getAdjacentHex(vehHex, 5) == v.getLocation().getHex()){

						found = true;
					}
				}
			}
		}

		// add hex if necessary
		if(!found){

			vehicleHindrances.add(v);

			// set first hindrance point, if necesary
			if (firstHindranceAt == null){

				firstHindranceAt = new Point(x, y);
			}

			// blocked if hindrances >= 6
			if (getHindrance() >= 6) {

				setBlocked(x, y, "Hindrance total of six or more (B.10)");
			}
		}
	}

	// hexes
	public void 	addHex(Hex h)	{hexes.add(h);}
	public HashSet	getHexes()		{return hexes;}

	// location methods
	public void setSourceLocation(Location l){sourceLocation = l;}
	public void	setTargetLocation(Location l){targetLocation = l;}

	// hexside methods
	public void		setSourceExitHexside(int h)	{sourceExitHexside  = h;}
	public void		setSourceExitHexspine(int h){sourceExitHexspine = h;}
	public void		setTargetEnterHexside(int h){targetEnterHexside = h;}
	public void		setTargetEnterHexspine(int h){targetEnterHexspine = h;}
	public void		setUseAuxSourceLOSPoint(boolean b){useAuxSourceLOSPoint = b;}
	public void		setUseAuxTargetLOSPoint(boolean b){useAuxTargetLOSPoint = b;}
	public int		getSourceExitHexside()		{return sourceExitHexside;}
	public int		getSourceExitHexspine()		{return sourceExitHexspine;}
	public int		getTargetEnterHexside()		{return targetEnterHexside;}
	public int		getTargetEnterHexspine()	{return targetEnterHexspine;}

	// continuous slope
	public boolean	isContinuousSlope()			{return continuousSlope;}
	public void		setContinuousSlope(boolean newContinuousSlope){
		continuousSlope = newContinuousSlope;
	}

	// LOS slope
	public void		setLOSis60Degree(boolean newLOSis60Degree){
		LOSis60Degree = newLOSis60Degree;
	}

	// range methods
	public void setRange(int r){range = r;}
	public int  getRange()	 {return range;}

	// LOS blocked
	public void setBlocked(int x, int y, String	reas) {

		blocked				= true;
		blockedAtPoint		= new Point(x,y);
		reason				= reas;
	}

	// clear LOS
	public void reset() {

		blocked				= false;
		blockedAtPoint		= null;
		firstHindranceAt 	= null;
		range				= 0;
		sourceExitHexside	= UNKNOWN;
		targetEnterHexside	= UNKNOWN;
		useAuxSourceLOSPoint	= false;
		useAuxTargetLOSPoint	= false;
		reason				= "";
		continuousSlope 	= false;
		LOSis60Degree		= false;
		mapHindranceHexes.clear();
		smokeHindrances.clear();
 		vehicleHindrances.clear();
		hexes.clear();
		sourceLocation		= null;
		targetLocation		= null;
		sourceExitHexspine	= UNKNOWN;
		targetEnterHexspine	= UNKNOWN;
	}

	// this smoke already added?
	private boolean smokePresent(Smoke s){

		Iterator 	iter = smokeHindrances.iterator();
		while(iter.hasNext()){
			if((Smoke) iter.next() == s){
				return true;
			}
		}
		return false;
	}

	// any smoke from this hex already added?
	private boolean smokePresent(Hex h){

		HashSet<Smoke> smoke = h.getMap().getAllSmoke(h);

		if (smoke != null){

			Iterator smokeIter = smoke.iterator();
			while (smokeIter.hasNext()){

				if (smokePresent((Smoke) smokeIter.next())){

					return true;
				}
			}
		}
		return false;
	}
}
