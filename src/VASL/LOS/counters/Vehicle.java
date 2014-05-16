/*
 * $Id: OBA 2/15/14 davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
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

/**
 * A simple class for a vehicle counter
 */
public class Vehicle extends Counter {

    Location location;

	public Vehicle(String name, Location location){

        super(name);
        this.location = location;
	}

    public Location getLocation() {
        return location;
    }
}

