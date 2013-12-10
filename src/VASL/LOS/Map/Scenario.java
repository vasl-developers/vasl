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

import java.util.Date;
import java.util.HashSet;

public class Scenario {

	// initialization variables
	private int		theater;
	private boolean	changed;	// changed since the last time saved?
	private int		EC;
	private Date		date;
	private int		axisSAN;
	private int		alliedSAN;
	private int		axisDefaultELR;
	private int		alliedDefaultELR;
	private boolean	rooftops;
	private int		turns;
	private boolean	halfTurn;

	// constructors
	public Scenario() {

	}


	public HashSet getVehicles(Hex h){

		return getVehicles(h, -1);
	}

	public HashSet getVehicles(Hex h,int level){

		HashSet temp = new HashSet();
		return temp;
	}

	public HashSet getVehicles(){

		HashSet temp = new HashSet();
		return temp;
	}

	public HashSet getUnits(Hex h){

		HashSet temp = new HashSet();
		return temp;
	}

}
