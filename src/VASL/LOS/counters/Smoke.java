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
package VASL.LOS.counters;

import VASL.LOS.Map.Location;
import VASL.LOS.counters.Counter;

/**
 * Title:        Smoke.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public	class Smoke extends Counter {

	// smoke attributes
	private	int 		height;
    private	int     	hindrance;
	private Location location;

    public Smoke(String name, Location location, int height, int hindrance ) {

        super(name);
        this.location = location;
        this.height = height;
        this.hindrance = hindrance;
    }

	// basic getters
	public int 		getHeight()		{return height;}
	public int 		getHindrance()	{return hindrance;}
	public Location	getLocation()	{return location;}
}
