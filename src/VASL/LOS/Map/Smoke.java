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

import java.io.Serializable;

/**
 * Title:        Smoke.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public	class Smoke
		implements Serializable {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// types of smoke
	public static final int SMOKE							= 1;
	public static final int WHITE_PHOSPHORUS				= 2;
	public static final int SMOKE_GRENADES					= 3;
	public static final int WHITE_PHOSPHORUS_SMOKE_GRENADES	= 4;

	// smoke attributes
	private	int 		height;
    private	int     	hindrance;
	private	boolean		dispersed;
	private	String		name;
	private	Location	location;

	private	int type;

	public Smoke(int type, Location loc, boolean dispersed) {

		initialize(type, loc, dispersed);
	}

	public Smoke(int type, Location loc) {

		initialize(type, loc, false);
	}

	private void initialize(int type, Location loc, boolean dispersed){

		this.type 		= type;
		this.location 	= loc;

		switch (type) {

			case SMOKE:
				if (dispersed) {

					hindrance = 2;
				}
				else {

					hindrance = 3;
				}
				this.dispersed = dispersed;
				height = 2;
				name = "Smoke";
				break;

			case WHITE_PHOSPHORUS:
				if (dispersed) {

					hindrance = 1;
				}
				else {

					hindrance = 2;
				}
				this.dispersed = dispersed;
				height = 4;
				name = "White Phosphorus";
				break;

			case SMOKE_GRENADES:

				dispersed 	= false;
				hindrance 	= 2;
				height 		= 2;
				name = "Smoke Grenades";
				break;

			case WHITE_PHOSPHORUS_SMOKE_GRENADES:

				dispersed 	= false;
				hindrance 	= 1;
				height 		= 4;
				name = "White Phosphorus Smoke Grenades";
		}
	}

	// basic getters
	public int 		getType()		{return type;}
	public int 		getHeight()		{return height;}
	public int 		getHindrance()	{return hindrance;}
	public String	getName()		{return name;}
	public Location	getLocation()	{return location;}
	public boolean	isDispersed()	{return dispersed;}

	// is white phosphorus?
	public boolean isWhitePhosphorus(){

		if (type == WHITE_PHOSPHORUS || type == WHITE_PHOSPHORUS_SMOKE_GRENADES){

			return true;
		}
		else {
			return false;
		}
	}

	// is smoke grenade?
	public boolean isSmokeGrenade(){

		if (type == SMOKE_GRENADES || type == WHITE_PHOSPHORUS_SMOKE_GRENADES){

			return true;
		}
		else {
			return false;
		}
	}
}
