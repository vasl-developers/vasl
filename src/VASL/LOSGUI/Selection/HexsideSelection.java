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

import VASL.LOS.Map.Location;

import java.awt.*;

/**
 * Title:        HexsideSelection.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class HexsideSelection
        extends Selection {

    // private variables
    private Shape paintShape;
    private Shape updateShape;
    private Location location;

    // getters and setters
    public Shape getUpdateShape() {
        return updateShape;
    }

    public Shape getPaintShape() {
        return paintShape;
    }

    public Location getLocation() {
        return location;
    }

    public void setPaintShape(Rectangle s) {
        paintShape = s;
    }

    // constructor
    public HexsideSelection(Shape newPaintShape, Shape newUpdateShape, Location newLocation) {

        paintShape = newPaintShape;
        updateShape = newUpdateShape;
        location = newLocation;
    }

    //paint
    public void paint(Graphics2D g) {

        g.setColor(paintColor);
        g.fill(paintShape);
    }


}

