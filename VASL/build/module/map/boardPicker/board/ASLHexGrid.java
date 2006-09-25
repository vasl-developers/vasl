/*
 * $Id$
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
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
package VASL.build.module.map.boardPicker.board;

import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid;

import java.awt.*;

public class ASLHexGrid extends HexGrid {
  protected static final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private static final int UNSPECIFIED = -117;

  public ASLHexGrid() {
    super();
  }

  public ASLHexGrid(double height, boolean alt) {
    super(height, alt);
  }

  public String locationName(Point p) {
    p.translate(-origin.x, (int) (dy / 2. - origin.y));

    int hexX, hexY;
    int hexX2 = UNSPECIFIED;
    int hexY2 = UNSPECIFIED;

    hexX = (int) Math.floor(p.x / dx + 0.5);
    hexY = (hexX % 2 == 0 ?
        (int) Math.floor(p.y / dy) + 1 :
        (int) Math.floor(p.y / dy + 0.5));

    if ((int) Math.floor(2 * p.x / dx + .5) % 2 != 0) {       /* Bypass at 1/2-hex in X */
      hexX = (int) Math.floor(p.x / dx);
      hexY = (hexX % 2 == 0 ?
          (int) Math.floor(p.y / dy) + 1 :
          (int) Math.floor(p.y / dy + 0.5));

      hexX2 = hexX + 1;
      if (hexX % 2 == 0)
        hexY2 = p.y > (hexY - 0.5) * dy ? hexY : hexY - 1;
      else
        hexY2 = p.y > hexY * dy ? hexY + 1 : hexY;
    }
    else if (hexX % 2 == 0) {
      if ((int) Math.floor(2 * p.y / dy + 0.5) % 2 == 0) {    /* Bypass at 1/2-hex in Y */
        hexX2 = hexX;
        hexY2 = p.y > (hexY - 0.5) * dy ? hexY + 1 : hexY - 1;
      }
    }
    else {
      if ((int) Math.floor(2 * p.y / dy + 0.5) % 2 != 0) {    /* Bypass at 1/2-hex in Y */
        hexX2 = hexX;
        hexY2 = p.y > hexY * dy ? hexY + 1 : hexY - 1;
      }
    }

    if (alternate && hexX % 2 != 0) {
      hexY++;
      hexY2++;
    }

    String hex = hexrow(hexX) + hexY;
    if (hexX2 != UNSPECIFIED)
      hex += "/" + hexrow(hexX2) + hexY2;
    return (hex);
  }

  public Point getLocation(String hex) throws MapGrid.BadCoords {
    hex = hex.toLowerCase();
    try {
      int hexX, hexY, sign = 1;

      if (hex.startsWith("-")) {
        sign = -1;
        hex = hex.substring(1);
      }
      Point p = hexPosition(hex);
      hexX = (int) (dx * p.x + .5);
      if (p.x % 2 == 0)
        hexY = (int) (dy * (p.y - 1) + .5);
      else
        hexY = alternate ?
            (int) (dy * (p.y - 1.5) + .5)
            : (int) (dy * (p.y - .5) + .5);

      return new Point(sign * hexX + origin.x, hexY + origin.y);
    }
    catch (Exception e) {
      throw new MapGrid.BadCoords("Bad Hex Coordinates");
    }
  }

  /**
   * @return the integer hexrow and coordinate of a hex
   * in number of hexes, e.g. hex A1 returns (0,1), B3 returns (1,3)
   */
  public static Point hexPosition(String hex) throws MapGrid.BadCoords {
    int nx = 0, ny = 0, n = 0;
    nx = hex.charAt(0) - 'a';
    if (hex.charAt(1) <= 'z'
        && hex.charAt(1) >= 'a') {
      if (hex.charAt(1) == hex.charAt(0))
        nx += 26;
      else {
        throw new MapGrid.BadCoords("Bad Hex Coordinates");
      }
      n = 1;
    }
    try {
      ny = Integer.parseInt(hex.substring(n + 1));
    }
    catch (NumberFormatException ex) {
      throw new MapGrid.BadCoords("Bad Coordinates");
    }

    return new Point(nx, ny);
  }

  public static String hexrow(int n) {
    String s = "";
    if (n < 0) {
      s = "-";
      n = -n;
    }
    while (n >= 0) {
      s += alphabet.substring(n % 26, n % 26 + 1);
      n -= 26;
    }
    return s;
  }
}
