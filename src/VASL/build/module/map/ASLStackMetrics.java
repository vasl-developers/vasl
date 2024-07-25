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

import java.awt.*;
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
        //String generalTabKey = Resources.getString("Prefs.general_tab");
        String generalTabKey = "VASL";
        String prefKey = "DisableFullColorStacks";

        BooleanConfigurer configurer = (BooleanConfigurer)gameModulePrefs.getOption(prefKey);
        if (configurer == null) {
            configurer = new BooleanConfigurer(prefKey, "Disable full color stacks (requires restart)", Boolean.FALSE);
            gameModulePrefs.addOption(generalTabKey, configurer);
        }
        disableFullColorStacks = (Boolean)(gameModulePrefs.getValue(prefKey));
        if (disableFullColorStacks) {super.blankColor = Color.WHITE;}
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
        //JY
        //Duplicate VASSAL StackMetrics.getContents(), with changes to apparent sizes to account for board zoom level
        //int val = super.getContents(parent, positions, shapes, boundingBoxes, x, y);
        double pZoom = ((ASLMap)map).PieceScalerBoardZoom(parent);
        int val = parent.getMaximumVisiblePieceCount();
        if (positions != null) {
            val = Math.min(val, positions.length);
        }
        if (boundingBoxes != null) {
            val = Math.min(val, boundingBoxes.length);
        }
        if (shapes != null) {
            val = Math.min(val, shapes.length);
        }
        int esx = Math.max((int) (this.exSepX * pZoom), 5);
        int esy = Math.max((int) (this.exSepY * pZoom), 10);
        int usx = Math.max((int) (this.unexSepX * pZoom), 1);
        int usy = Math.max((int) (this.unexSepY * pZoom), 2);
        int dx = parent.isExpanded() ? esx : usx;
        int dy = parent.isExpanded() ? esy : usy;
        Point currentPos = null;
        Rectangle currentSelBounds = null;

        for(int index = 0; index < val; ++index) {
            GamePiece child = parent.getPieceAt(index);
            Rectangle bbox;
            if (Boolean.TRUE.equals(child.getProperty("Invisible"))) {
                bbox = new Rectangle(x, y, 0, 0);
                if (positions != null) {
                    positions[index] = bbox.getLocation();
                }
                if (boundingBoxes != null) {
                    boundingBoxes[index] = scalePiece(bbox, pZoom);
                }
                if (shapes != null) {
                    shapes[index] = scalePiece(bbox, pZoom);
                }
            } else {
                child.setProperty("useUnrotatedShape", Boolean.TRUE);
                Rectangle nextSelBounds = scalePiece(child.getShape().getBounds(), pZoom);
                child.setProperty("useUnrotatedShape", Boolean.FALSE);
                Point nextPos = new Point(0, 0);
                if (currentPos == null) {
                    nextSelBounds.translate(x, y);
                    currentPos = new Point(x, y);
                    nextPos = currentPos;
                } else {
                    this.nextPosition(currentPos, currentSelBounds, nextPos, nextSelBounds, dx, dy);
                }
                if (positions != null) {
                    positions[index] = nextPos;
                }
                if (boundingBoxes != null) {
                    bbox = scalePiece(child.boundingBox(), pZoom);
                    bbox.translate(nextPos.x, nextPos.y);
                    boundingBoxes[index] = bbox;
                }
                if (shapes != null) {
                    Shape s = scalePiece(child.getShape(), pZoom);
                    s = AffineTransform.getTranslateInstance((double)nextPos.x, (double)nextPos.y).createTransformedShape(s);
                    shapes[index] = s;
                }
                currentPos = nextPos;
                currentSelBounds = nextSelBounds;
            }
        }
        //JY

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
                //JY
                int dxz = (int) (15/((ASLMap)map).getbZoom());
                //JY
                for (int i = 0; i < count; ++i) {
                    if (visibleLocations.get(i)) {
                        if (positions != null) {
                            //JY
                            //positions[i].translate(-15, 0);
                            positions[i].translate(-dxz, 0);
                            //JY
                        }
                        if (boundingBoxes != null) {
                            //JY
                            //boundingBoxes[i].translate(-15, 0);
                            boundingBoxes[i].translate(-dxz, 0);
                            //JY
                        }
                        if (shapes != null) {
                            //JY
                            //shapes[i] = AffineTransform.getTranslateInstance(-15, 0).createTransformedShape(shapes[i]);
                            shapes[i] = AffineTransform.getTranslateInstance(-dxz, 0).createTransformedShape(shapes[i]);
                            //JY
                        }
                    }
                }
            }
        }
        return val;
    }

    @Override
    public void draw(Stack stack, Graphics g, int x, int y, Component obs, double zoom) {
        if (disableFullColorStacks) {
            super.draw(stack, g, x, y, obs, zoom);
        } else {
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

    //JY
    public Rectangle scalePiece(Rectangle r, double f) {
        Rectangle o = new Rectangle();
        o.width = (int) (r.width * f);
        o.height = (int) (r.height * f);
        o.x = (int) (r.getCenterX() - 0.5*r.width*f);
        o.y = (int) (r.getCenterY() - 0.5*r.height*f);
        return o;
    }
    public Shape scalePiece(Shape s, double f) {
        double cx = s.getBounds2D().getCenterX();
        double cy = s.getBounds2D().getCenterY();
        final AffineTransform t = AffineTransform.getTranslateInstance(-cx, -cy);
        t.scale(f, f);
        t.translate(cx, cy);
        return t.createTransformedShape(s);
    }
    //JY
}
