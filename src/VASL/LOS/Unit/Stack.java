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
package VASL.LOS.Unit;

import java.io.*;
import java.util.*;

/**
 * Title:        Stack.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public class Stack
	implements Serializable {

	// variables
	HashSet	units = new HashSet(4);
	Iterator 	iter;

	// constructors
	public Stack(Unit unit){

		units.add(unit);
	}

	// add a unit
	public void addUnit(Unit unit){

		units.add(unit);
	}

	// get the first unit in the stack
	public Unit getFirstUnit(){

		iter = units.iterator();

		if (iter.hasNext()){

			return (Unit) iter.next();
		}

		return null;
	}

	// get the next unit in the stack
	public Unit getNextUnit(){

		if (iter != null && iter.hasNext()){

			return (Unit) iter.next();
		}
		return null;
	}

}
