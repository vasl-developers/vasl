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
package VASL.LOSGUI.Selection;

import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Terrain;

import java.awt.*;

/**
 * Title:        HexSelection.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class HexSelection
        extends Selection {

    // private variables
    private Shape paintShape;
    @SuppressWarnings("unused")
    private Shape updateShape;
    private Hex hex;

    // getters and setters
    public Shape getUpdateShape() {
        return paintShape;
    }

    public Hex getHex() {
        return hex;
    }

    // constructor
    public HexSelection(Shape paintShape, Hex h) {

        this.paintShape = paintShape;
        hex = h;
    }

    //paint
    public void paint(Graphics2D g) {

        g.setColor(paintColor);
        g.fill(paintShape);
    }
    public String getTerrainXMLSnippet(Terrain terrain) {

        return
                "<terrainEdit type=\"Hex\" " +
                        "hexName=\"" + hex.getName() + "\" " +
                        "terrainType=\"" + terrain.getName() + "\" " +
                        "/>";
    }

    public String getElevationXMLSnippet(int elevation) {

        return
                "<elevationEdit type=\"Hex\" " +
                        "hexName=\"" + hex.getName() + "\" " +
                        "elevation=\"" + elevation + "\" " +
                        "/>";
    }
}

