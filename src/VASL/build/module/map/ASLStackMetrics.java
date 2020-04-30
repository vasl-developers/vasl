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
package VASL.build.module.map;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.BitSet;

import VASL.build.module.ASLMap;
import VASL.counters.ASLProperties;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.StackMetrics;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Highlighter;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;

public class ASLStackMetrics extends StackMetrics {

    private boolean disableFullColorStacks = false;

    @Override
    public void addTo(Buildable buildable) {

        Prefs gameModulePrefs = GameModule.getGameModule().getPrefs();
        String generalTabKey = Resources.getString("Prefs.general_tab");
        String prefKey = "DisableFullColorStacks";

        BooleanConfigurer configurer = (BooleanConfigurer)gameModulePrefs.getOption(prefKey);
        if (configurer == null) {
            configurer = new BooleanConfigurer(prefKey, "Disable full color stacks (requires restart)", Boolean.FALSE);
            gameModulePrefs.addOption(generalTabKey, configurer);
        }
        disableFullColorStacks = (Boolean)(gameModulePrefs.getValue(prefKey));

        super.addTo(buildable);
    }

    @Override
    protected void drawUnexpanded(GamePiece p, Graphics g,
                                  int x, int y, Component obs, double zoom) {
        if (p.getProperty(ASLProperties.LOCATION) != null) {
            p.draw(g, x, y, obs, zoom);
        } else {
            super.drawUnexpanded(p, g, x, y, obs, zoom);
        }
    }

    @Override
    public int getContents(Stack parent, Point[] positions, Shape[] shapes, Rectangle[] boundingBoxes, int x, int y) {
        int val = super.getContents(parent, positions, shapes, boundingBoxes, x, y);
        if (!parent.isExpanded()) {
            int count = parent.getPieceCount();
            BitSet visibleLocations = new BitSet(count);
            BitSet visibleOther = new BitSet(count);
            for (int i = 0; i < count; ++i) {
                GamePiece p = parent.getPieceAt(i);
                boolean visibleToMe = !Boolean.TRUE.equals(p.getProperty(Properties.INVISIBLE_TO_ME));
                boolean isLocation = p.getProperty((ASLProperties.LOCATION)) != null;
                visibleLocations.set(i, isLocation && visibleToMe);
                visibleOther.set(i, !isLocation && visibleToMe);
            }
            if (visibleLocations.cardinality() > 0 && visibleOther.cardinality() > 0) {
                for (int i = 0; i < count; ++i) {
                    if (visibleLocations.get(i)) {
                        if (positions != null) {
                            positions[i].translate(-15, 0);
                        }
                        if (boundingBoxes != null) {
                            boundingBoxes[i].translate(-15, 0);
                        }
                        if (shapes != null) {
                            shapes[i] = AffineTransform.getTranslateInstance(-15, 0).createTransformedShape(shapes[i]);
                        }
                    }
                }
            }
        }
        return val;
    }

    @Override
    public void draw(Stack stack, Graphics g, int x, int y, Component obs, double zoom) {

        Highlighter highlighter = stack.getMap() == null ? BasicPiece.getHighlighter() : stack.getMap().getHighlighter();
        Point[] positions = new Point[stack.getPieceCount()];
        getContents(stack, positions, null, null, x, y);

        for (PieceIterator e = new PieceIterator(stack.getPiecesIterator(), unselectedVisible); e.hasMoreElements(); ) {
            GamePiece next = e.nextPiece();
            int index = stack.indexOf(next);
            int nextX = x + (int) (zoom * (positions[index].x - x));
            int nextY = y + (int) (zoom * (positions[index].y - y));
//if (stack.isExpanded() || !e.hasMoreElements()) {
            next.draw(g, nextX, nextY, obs, zoom);
//}
//else {
//    drawUnexpanded(next, g, nextX, nextY, obs, zoom);
//}
        }

        for (PieceIterator e = new PieceIterator(stack.getPiecesIterator(), selectedVisible); e.hasMoreElements(); ) {
            GamePiece next = e.nextPiece();
            int index = stack.indexOf(next);
            int nextX = x + (int) (zoom * (positions[index].x - x));
            int nextY = y + (int) (zoom * (positions[index].y - y));
            next.draw(g, nextX, nextY, obs, zoom);
            highlighter.draw(next, g, nextX, nextY, obs, zoom);
        }
    }

    private boolean isVisible(Rectangle region, Rectangle bounds) {
        boolean visible = true;
        if (region != null) {
            visible = region.intersects(bounds);
        }
        return visible;
    }

    @Override
    public void draw(Stack stack, Point location, Graphics g, Map map, double zoom, Rectangle visibleRect) {

        if (disableFullColorStacks) {
            super.draw(stack, location, g, map, zoom, visibleRect);
        } else {
            final Graphics2D g2d = (Graphics2D) g;
            final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();

            final Component view = map.getView();

            Highlighter highlighter = map.getHighlighter();
            Point mapLocation = map.drawingToMap(location, os_scale);
            Rectangle region = visibleRect == null ? null : map.drawingToMap(visibleRect, os_scale);
            Point[] positions = new Point[stack.getPieceCount()];
            Rectangle[] bounds = region == null ? null : new Rectangle[stack.getPieceCount()];
            getContents(stack, positions, null, bounds, mapLocation.x, mapLocation.y);

            for (PieceIterator e = new PieceIterator(stack.getPiecesIterator(), unselectedVisible); e.hasMoreElements(); ) {
                GamePiece next = e.nextPiece();
                int index = stack.indexOf(next);
                Point pt = map.mapToDrawing(positions[index], os_scale);
                if (bounds == null || isVisible(region, bounds[index])) {
//if (stack.isExpanded() || !e.hasMoreElements()) {
                    next.draw(g, pt.x, pt.y, view, zoom);
//} else {
//    drawUnexpanded(next, g, pt.x, pt.y, map.getView(), zoom);
//}
                }
            }

            for (PieceIterator e = new PieceIterator(stack.getPiecesIterator(), selectedVisible); e.hasMoreElements(); ) {
                GamePiece next = e.nextPiece();
                int index = stack.indexOf(next);
                if (bounds == null || isVisible(region, bounds[index])) {
                    Point pt = map.mapToDrawing(positions[index], os_scale);
                    next.draw(g, pt.x, pt.y, view, zoom);
                    highlighter.draw(next, g, pt.x, pt.y, view, zoom);
                }
            }
        }
    }
}
