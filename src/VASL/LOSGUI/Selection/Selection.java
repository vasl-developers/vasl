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

import VASL.LOS.Map.Terrain;

import java.awt.*;

/**
 * Title:        Selection.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public abstract class Selection {

    // private variables
    protected final static Color paintColor = Color.red;

    public abstract void paint(Graphics2D g);

    public abstract Shape getUpdateShape();

    public String getTerrainXMLSnippet(Terrain terrain) {

        return "";
    }

    public String getElevationXMLSnippet(int elevation) {

        return "";
    }
}
