package VASL.build.module.map.boardArchive;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Copyright (c) 2014 by David Sullivan
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

/**
 * The set of all slope hexside on a board
 */
public class Slopes {

    private HashMap<String, boolean[]> slopes = new HashMap<String, boolean[]>(10);

    public void addSlope(String hex, boolean[] hexsides) {
        slopes.put(hex, hexsides);
    }

    public boolean[] getSlopes(String hex) {
        return slopes.get(hex);
    }

    public boolean hasSlope(String hex, int hexside) {
        try {
            return slopes.get(hex)[hexside];
        }
        catch (Exception e) {
            return false;
        }
    }

    public HashMap<String, boolean[]> getAllSlopes(){
        return slopes;
    }
}
