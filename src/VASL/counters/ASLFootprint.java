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
package VASL.counters;

import VASL.build.module.ASLMap;
import VASL.build.module.map.MapViewRotation;
import VASSAL.counters.Footprint;
import VASSAL.counters.GamePiece;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;

/**
 * Movement trail which turns with the rotated map view also when its piece is shown upright. The upright piece is
 * drawn through a graphics turned back around it, but the trail is drawn in map coordinates, so it has to be drawn
 * through the graphics turned like the map.
 */
public class ASLFootprint extends Footprint {

    public ASLFootprint() {
        super();
    }

    public ASLFootprint(String type, GamePiece inner) {
        super(type, inner);
    }

    @Override
    public void draw(Graphics g, int x, int y, Component obs, double zoom) {
        final ASLMap map = getMap() instanceof ASLMap ? (ASLMap) getMap() : null;
        final Shape clip = g.getClip();
        if (map == null || clip == null || !isTrailVisible() || !map.isDrawnUpright(getOutermost(this))) {
            super.draw(g, x, y, obs, zoom);
            return;
        }
        // the piece upright, then the trail drawn by Footprint through the graphics turned like the map, where Footprint
        // draws the piece again: that one is clipped out
        piece.draw(g, x, y, obs, zoom);
        final Graphics2D g2d = (Graphics2D) g;
        final AffineTransform t = g2d.getTransform();
        MapViewRotation.rotate(g2d, map.getViewRotation(), x, y);
        final Rectangle r = piece.boundingBox();
        final Area outsidePiece = new Area(g2d.getClip());
        outsidePiece.subtract(new Area(new Rectangle(
            x + (int) Math.floor(r.x * zoom) - 2, y + (int) Math.floor(r.y * zoom) - 2,
            (int) Math.ceil(r.width * zoom) + 4, (int) Math.ceil(r.height * zoom) + 4)));
        g2d.setClip(outsidePiece);
        super.draw(g, x, y, obs, zoom);
        g2d.setTransform(t);
        g2d.setClip(clip);
    }
}
