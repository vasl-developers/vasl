/*
 * $Id: ASLMap.java 8530 2012-12-26 04:37:04Z uckelman $
 *
 * Copyright (c) 2013 by Brent Easton
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

package VASL.build.module;

import java.awt.Point;

import VASSAL.build.module.Map;

public class ASLMap extends Map {
  
  public ASLMap() {
    super();
  }
  
  /*
   *  Work-around for VASL board being 1 pixel too large causing double stacks to form along board edges.
   *  Any snap to a board top or left edge half hex, bump it 1 pixel up or left on to the next board.
   *  
   *  */
  public Point snapTo(Point p) {
    final Point p1 = super.snapTo(p);
    final Point p2 = new Point (p1);
    final String loc = locationName(p1);
    
    // Ignore Edge and Corner locations
    if (! loc.contains("/")) {
      // Zero row hexes are all top edge half hexes, bump the snap up 1 pixel to the board above.
      if (loc.endsWith("0") && ! loc.endsWith("10")) {
        p2.y -= 1;
      }
      // Column A hexes are all left edge half gexes, bump the snap left 1 pixel to the board to the left 
      else if (loc.contains("A") && ! loc.contains("AA")) {
        p2.x -=1;
      }
    }
    // If the snap has been bumped offmap (must be top or right edge of map), use the original snap.
    if (findBoard(p2) == null) {
      return p1;
    }
    return p2;
  }
  
}