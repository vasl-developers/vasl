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
package VASL.build.module.map;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;

/**
 * Geometry of the local map view rotation: the view can be turned clockwise by a number of quarter turns
 * (0 = no rotation, 1 = 90 degrees, 2 = 180 degrees, 3 = 270 degrees).
 *
 * The rotation affects only how the map is shown on screen: map coordinates are never rotated, so piece
 * positions, saved games and the commands sent to the other players stay the same whatever rotation is used.
 *
 * All the methods convert between the "unrotated" view (the view VASSAL would show without rotation) and the
 * "rotated" view (the one actually on screen). The size passed to them is always the size of the unrotated view.
 */
public final class MapViewRotation {

    private MapViewRotation() {
    }

    /** @return the number of quarter turns reduced to the range 0..3 */
    public static int normalize(int quarterTurns) {
        return ((quarterTurns % 4) + 4) % 4;
    }

    /** @return true if the rotation swaps width and height */
    public static boolean swapsAxes(int quarterTurns) {
        return normalize(quarterTurns) % 2 == 1;
    }

    /** @return the size of the rotated view */
    public static Dimension rotate(Dimension size, int quarterTurns) {
        return swapsAxes(quarterTurns) ? new Dimension(size.height, size.width) : new Dimension(size);
    }

    /** Converts a point of the unrotated view into the rotated view */
    public static Point rotate(Point p, int quarterTurns, Dimension size) {
        switch (normalize(quarterTurns)) {
            case 1:
                return new Point(size.height - p.y, p.x);
            case 2:
                return new Point(size.width - p.x, size.height - p.y);
            case 3:
                return new Point(p.y, size.width - p.x);
            default:
                return new Point(p);
        }
    }

    /** Converts a point of the rotated view back into the unrotated view */
    public static Point unrotate(Point p, int quarterTurns, Dimension size) {
        return rotate(p, -quarterTurns, rotate(size, quarterTurns));
    }

    /** Converts a rectangle of the unrotated view into the rotated view */
    public static Rectangle rotate(Rectangle r, int quarterTurns, Dimension size) {
        switch (normalize(quarterTurns)) {
            case 1:
                return new Rectangle(size.height - r.y - r.height, r.x, r.height, r.width);
            case 2:
                return new Rectangle(size.width - r.x - r.width, size.height - r.y - r.height, r.width, r.height);
            case 3:
                return new Rectangle(r.y, size.width - r.x - r.width, r.height, r.width);
            default:
                return new Rectangle(r);
        }
    }

    /** Converts a rectangle of the rotated view back into the unrotated view */
    public static Rectangle unrotate(Rectangle r, int quarterTurns, Dimension size) {
        return rotate(r, -quarterTurns, rotate(size, quarterTurns));
    }

    /**
     * @return the transform that maps the unrotated view onto the rotated one, for a view of the given size
     * (in the same units used for drawing, i.e. including the OS scaling)
     */
    public static AffineTransform getTransform(int quarterTurns, double width, double height) {
        switch (normalize(quarterTurns)) {
            case 1:
                return new AffineTransform(0, 1, -1, 0, height, 0);
            case 2:
                return new AffineTransform(-1, 0, 0, -1, width, height);
            case 3:
                return new AffineTransform(0, -1, 1, 0, 0, width);
            default:
                return new AffineTransform();
        }
    }

    /** Turns the graphics clockwise by the given quarter turns around a point */
    public static void rotate(Graphics2D g, int quarterTurns, double x, double y) {
        g.rotate(normalize(quarterTurns) * Math.PI / 2, x, y);
    }

    /**
     * Facing (index of the clockwise facings of a piece, 0 = default) that makes a piece shown on a view rotated by
     * toQuarterTurns look on the screen as with the given facing on a view rotated by fromQuarterTurns.
     * When no facing looks exactly the same, e.g. with 6 facings and a quarter turn, on the rotated view the piece is
     * turned counterclockwise by half a facing: so the covered arc of the vehicles, bottom right by default in the OB
     * window, stays bottom right. Moving the piece back gives the facing it had before.
     */
    public static int facingSeenAlike(int facing, int facings, int fromQuarterTurns, int toQuarterTurns) {
        return Math.floorMod(facing - facingOffset(facings, fromQuarterTurns) + facingOffset(facings, toQuarterTurns), facings);
    }

    // facings to add on a view rotated by the quarter turns, to look like on the unrotated view
    private static int facingOffset(int facings, int quarterTurns) {
        // a quarter turn is facings / 4 facings, rounded (half down) to the facing turned counterclockwise
        return (int) Math.ceil(-normalize(quarterTurns) * facings / 4.0 - 0.5);
    }

    // keys of the numeric keypad that move a piece to the hexes around it, clockwise from the top: the direction of
    // the i-th key is 60 * i degrees when the hexes have a side at the top (unrotated view and half turn) and
    // 30 + 60 * i degrees when they have a vertex at the top (quarter turns)
    private static final int[] SIDE_UP_KEYS = {
        KeyEvent.VK_NUMPAD8, KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD2, KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD7
    };
    private static final int[] VERTEX_UP_KEYS = {
        KeyEvent.VK_NUMPAD9, KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD1, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD7
    };

    /**
     * The keys of the numeric keypad move the pieces toward the direction they have on the screen: on a view rotated
     * by the given quarter turns, this is the key with the same direction on the unrotated view. For a key without
     * a hex in its direction on the rotated view (4 and 6 when the hexes have a side at the top, 8 and 2 when they
     * have a vertex at the top) it is {@link KeyEvent#VK_UNDEFINED}; any other key is returned as it is.
     */
    public static int keypadKeyOnUnrotatedView(int keyCode, int quarterTurns) {
        if (keyCode < KeyEvent.VK_NUMPAD1 || keyCode > KeyEvent.VK_NUMPAD9 || keyCode == KeyEvent.VK_NUMPAD5) {
            return keyCode;
        }
        final int q = normalize(quarterTurns);
        final int[] keysOnView = q % 2 == 0 ? SIDE_UP_KEYS : VERTEX_UP_KEYS;
        for (int i = 0; i < keysOnView.length; i++) {
            if (keysOnView[i] == keyCode) {
                final int direction = (q % 2 == 0 ? 60 * i : 30 + 60 * i) - 90 * q;
                return SIDE_UP_KEYS[Math.floorMod(direction, 360) / 60];
            }
        }
        return KeyEvent.VK_UNDEFINED;
    }
}
