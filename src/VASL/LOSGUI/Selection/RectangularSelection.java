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
import java.awt.geom.Ellipse2D;

/**
 * Title:        RectangularSelection.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class RectangularSelection
        extends Selection {

    // private variables
    protected Shape paintShape;
    protected boolean round;

    // getters
    public Shape getUpdateShape() {

        if (round) {
            return new Ellipse2D.Float(
                    (float) ((Rectangle) paintShape).getX(),
                    (float) ((Rectangle) paintShape).getY(),
                    (float) ((Rectangle) paintShape).getWidth(),
                    (float) ((Rectangle) paintShape).getHeight());
        } else {
            return paintShape;
        }
    }

    // constructor
    public RectangularSelection(Shape paintShape, boolean isRound) {

        this.paintShape = paintShape;
        this.round = isRound;
    }

    public RectangularSelection(){}

    //paint
    public void paint(Graphics2D g) {

        g.setColor(paintColor);
        if (round) {
            g.fillArc(
                    (int) ((Rectangle) paintShape).getX(),
                    (int) ((Rectangle) paintShape).getY(),
                    (int) ((Rectangle) paintShape).getWidth(),
                    (int) ((Rectangle) paintShape).getHeight(),
                    0,
                    360);
        } else {
            g.fill(paintShape);
        }
    }

    public String getTerrainXMLSnippet(Terrain terrain) {

        Rectangle rectangle = (Rectangle) paintShape;
        String rectangleValues =
                "x=\"" + (int) rectangle.getX() + "\" " +
                "y=\"" + (int) rectangle.getY() + "\" " +
                "width=\"" + (int) rectangle.getWidth() + "\" " +
                "height=\"" + (int) rectangle.getHeight() + "\" " +
                "terrainType=\"" + terrain.getName() + "\" " +
                "/>";
        if (round) {
            return  "<terrainEdit type=\"Circle\" " + rectangleValues;
        }
        else {
            return  "<terrainEdit type=\"Rectangle\" " + rectangleValues;
        }
    }

    public String getElevationXMLSnippet(int elevation) {

        Rectangle rectangle = (Rectangle) paintShape;
        String rectangleValues =
                "x=\"" + (int) rectangle.getX() + "\" " +
                        "y=\"" + (int) rectangle.getY() + "\" " +
                        "width=\"" + (int) rectangle.getWidth() + "\" " +
                        "height=\"" + (int) rectangle.getHeight() + "\" " +
                        "elevation=\"" + elevation + "\" " +
                        "/>";
        if (round) {
            return  "<elevationEdit type=\"Circle\" " + rectangleValues;
        }
        else {
            return  "<elevationEdit type=\"Rectangle\" " + rectangleValues;
        }
    }
}
