/*
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
package org.vasl.tests;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import VASL.build.module.map.MapViewRotation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the geometry of the local map view rotation
 */
public class MapViewRotationTest {

    private static final Dimension SIZE = new Dimension(1000, 600);

    @Test
    public void normalizeWrapsQuarterTurns() {
        assertEquals(0, MapViewRotation.normalize(4));
        assertEquals(3, MapViewRotation.normalize(-1));
        assertEquals(1, MapViewRotation.normalize(-7));
    }

    @Test
    public void quarterTurnsSwapWidthAndHeight() {
        assertEquals(new Dimension(600, 1000), MapViewRotation.rotate(SIZE, 1));
        assertEquals(SIZE, MapViewRotation.rotate(SIZE, 2));
        assertEquals(new Dimension(600, 1000), MapViewRotation.rotate(SIZE, 3));
    }

    @Test
    public void clockwiseTurnMovesCornersClockwise() {
        // top left -> top right, top right -> bottom right, bottom left -> top left
        assertEquals(new Point(600, 0), MapViewRotation.rotate(new Point(0, 0), 1, SIZE));
        assertEquals(new Point(600, 1000), MapViewRotation.rotate(new Point(1000, 0), 1, SIZE));
        assertEquals(new Point(0, 0), MapViewRotation.rotate(new Point(0, 600), 1, SIZE));
    }

    @Test
    public void counterclockwiseTurnMovesCornersCounterclockwise() {
        // top left -> bottom left, top right -> top left
        assertEquals(new Point(0, 1000), MapViewRotation.rotate(new Point(0, 0), 3, SIZE));
        assertEquals(new Point(0, 0), MapViewRotation.rotate(new Point(1000, 0), 3, SIZE));
    }

    @Test
    public void unrotateIsTheInverseOfRotate() {
        for (int q = 0; q < 4; q++) {
            for (int x = 0; x <= SIZE.width; x += 125) {
                for (int y = 0; y <= SIZE.height; y += 75) {
                    final Point p = new Point(x, y);
                    assertEquals(p, MapViewRotation.unrotate(MapViewRotation.rotate(p, q, SIZE), q, SIZE));

                    final Rectangle r = new Rectangle(x, y, 40, 70);
                    assertEquals(r, MapViewRotation.unrotate(MapViewRotation.rotate(r, q, SIZE), q, SIZE));
                }
            }
        }
    }

    @Test
    public void rectanglesRotateLikeTheirCorners() {
        final Rectangle r = new Rectangle(100, 200, 40, 70);
        for (int q = 0; q < 4; q++) {
            final Rectangle expected = new Rectangle(MapViewRotation.rotate(r.getLocation(), q, SIZE));
            expected.add(MapViewRotation.rotate(new Point(r.x + r.width, r.y + r.height), q, SIZE));
            assertEquals(expected, MapViewRotation.rotate(r, q, SIZE));
        }
    }

    @Test
    public void graphicsTurnClockwiseAroundThePoint() {
        final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        MapViewRotation.rotate(g, 1, 100, 50);
        // what is drawn right of the point appears below it, and the point itself does not move
        final Point2D right = g.getTransform().transform(new Point2D.Double(110, 50), null);
        assertEquals(100, right.getX(), 1e-9);
        assertEquals(60, right.getY(), 1e-9);
        final Point2D center = g.getTransform().transform(new Point2D.Double(100, 50), null);
        assertEquals(100, center.getX(), 1e-9);
        assertEquals(50, center.getY(), 1e-9);
        // turning back gives the original graphics
        MapViewRotation.rotate(g, -1, 100, 50);
        assertEquals(new Point2D.Double(110, 50), g.getTransform().transform(new Point2D.Double(110, 50), null));
        g.dispose();
    }

    @Test
    public void facingsTurnAgainstTheView() {
        // 6 facings (hexsides): a quarter turn has no exact facing, the piece looks turned 30 degrees counterclockwise
        for (int q = 1; q < 4; q += 2) {
            for (int f = 0; f < 6; f++) {
                final int onView = MapViewRotation.facingSeenAlike(f, 6, 0, q);
                assertEquals(330, Math.floorMod(onView * 60 + q * 90 - f * 60, 360));
            }
        }
        // the default facing of the vehicles, on the view turned counterclockwise and clockwise
        assertEquals(1, MapViewRotation.facingSeenAlike(0, 6, 0, 3));
        assertEquals(4, MapViewRotation.facingSeenAlike(0, 6, 0, 1));
        // half turn and 12 facings (hexsides and vertices): exactly the same look
        assertEquals(3, MapViewRotation.facingSeenAlike(0, 6, 0, 2));
        for (int q = 0; q < 4; q++) {
            for (int f = 0; f < 12; f++) {
                final int onView = MapViewRotation.facingSeenAlike(f, 12, 0, q);
                assertEquals(0, Math.floorMod(onView * 30 + q * 90 - f * 30, 360));
            }
        }
    }

    @Test
    public void facingComesBackWhenThePieceMovesBack() {
        for (int q = 0; q < 4; q++) {
            for (int f = 0; f < 6; f++) {
                final int onView = MapViewRotation.facingSeenAlike(f, 6, 0, q);
                assertEquals(f, MapViewRotation.facingSeenAlike(onView, 6, q, 0));
            }
        }
    }

    @Test
    public void keypadKeysMoveTowardTheScreenDirection() {
        final int[] hexKeys = {KeyEvent.VK_NUMPAD7, KeyEvent.VK_NUMPAD8, KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD3};
        // unrotated: the keys stay the same
        for (int key : hexKeys) {
            assertEquals(key, MapViewRotation.keypadKeyOnUnrotatedView(key, 0));
        }
        // half turn: the opposite direction, 4 and 6 have no hex
        assertEquals(KeyEvent.VK_NUMPAD2, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD8, 2));
        assertEquals(KeyEvent.VK_NUMPAD1, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD9, 2));
        assertEquals(KeyEvent.VK_NUMPAD7, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD3, 2));
        assertEquals(KeyEvent.VK_UNDEFINED, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD4, 2));
        assertEquals(KeyEvent.VK_UNDEFINED, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD6, 2));
        // quarter turn clockwise: the screen right is the map up, 8 and 2 have no hex
        assertEquals(KeyEvent.VK_NUMPAD8, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD6, 1));
        assertEquals(KeyEvent.VK_NUMPAD2, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD4, 1));
        assertEquals(KeyEvent.VK_NUMPAD7, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD9, 1));
        assertEquals(KeyEvent.VK_NUMPAD9, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD3, 1));
        assertEquals(KeyEvent.VK_NUMPAD3, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD1, 1));
        assertEquals(KeyEvent.VK_NUMPAD1, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD7, 1));
        assertEquals(KeyEvent.VK_UNDEFINED, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD8, 1));
        assertEquals(KeyEvent.VK_UNDEFINED, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD2, 1));
        // quarter turn counterclockwise: the screen left is the map up
        assertEquals(KeyEvent.VK_NUMPAD8, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD4, 3));
        assertEquals(KeyEvent.VK_NUMPAD2, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD6, 3));
        assertEquals(KeyEvent.VK_NUMPAD9, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD7, 3));
        assertEquals(KeyEvent.VK_NUMPAD3, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD9, 3));
        // the other keys are not changed
        assertEquals(KeyEvent.VK_NUMPAD5, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_NUMPAD5, 1));
        assertEquals(KeyEvent.VK_UP, MapViewRotation.keypadKeyOnUnrotatedView(KeyEvent.VK_UP, 1));
    }

    @Test
    public void transformMatchesPointRotation() {
        final double scale = 1.5; // e.g. OS scaling of the drawing
        for (int q = 0; q < 4; q++) {
            for (int x = 0; x <= SIZE.width; x += 125) {
                for (int y = 0; y <= SIZE.height; y += 75) {
                    final Point expected = MapViewRotation.rotate(new Point(x, y), q, SIZE);
                    final Point2D actual = MapViewRotation.getTransform(q, SIZE.width * scale, SIZE.height * scale)
                        .transform(new Point2D.Double(x * scale, y * scale), null);
                    assertEquals(expected.x * scale, actual.getX(), 1e-9);
                    assertEquals(expected.y * scale, actual.getY(), 1e-9);
                }
            }
        }
    }
}
