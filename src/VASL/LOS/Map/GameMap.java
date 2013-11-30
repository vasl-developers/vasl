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
import java.util.HashMap;

/**
 * The <code>GameMap</code> class is the map API.
 * The map uses two data structures to represent the logical map: a terrain grid and a hex
 * grid.
 * <p>
 * The terrain grid contains a physical representation of the image terrain where one point
 * in the grid corresponds to one pixel in the map image. The upper-left pixel is (0,0).
 * <p>
 * The hex grid contains the information that is specific to each hex and uses the
 * following coordinate system: the upper-left most hex (A1) is (0,0), A2 would be (0,1),
 * B0 would be (1,0), and so on. Note that the number of hexes in each column will
 * depend upon whether the column is odd or even.

 * @version 1.00 10/01/00
 * @author David Sullivan
 */
public class GameMap
    extends Map
    implements Serializable {

    /**
     * Constructs a new <code>Map</code> object. A standard geomorphic map board
     * is 10 x 33 hexes.
     *
     * @param w the width of the map in hexes
     * @param h the height of the map in hexes
     */
    public GameMap(int w, int h, HashMap<String, Terrain> terrainNameMap) {
        super(w, h, terrainNameMap);
    }

}

