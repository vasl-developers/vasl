package VASL.build.module.map;

/**
 * Copyright (c) 2016 by David Sullivan
 * <p>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 * <p>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.counters.*;

import java.awt.*;
import java.util.ArrayList;

import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_HEIGHT;

/**
 * This component highlights the sniper counters.
 */
public class ASLSniperFinder extends AbstractConfigurable implements GameComponent, Drawable {
    private Map map;

    private Boolean visible = false;
    private final ArrayList<Point> pointList = new ArrayList<Point>();

    // this component is not configurable
    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{String.class};
    }

    @Override
    public String[] getAttributeNames() {
        return new String[]{"Name"};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{"Name"};
    }

    @Override
    public String getAttributeValueString(String key) {
        return "ASL Sniper Finder";
    }

    @Override
    public void setAttribute(String key, Object value) {
    }

    public void addTo(Buildable parent) {

        // add this component to the game
        if (parent instanceof Map) {
            this.map = (Map) parent;
            map.addDrawComponent(this);
        }
    }

    @Override
    public void draw(Graphics g, Map map) {
        if (visible) {
            LoadSniperPosition();

            if (pointList.size() > 0) {
                g.setColor(Color.RED);

                Graphics2D g2d = (Graphics2D) g;

                Stroke oldStroke = g2d.getStroke();
                g2d.setStroke(new BasicStroke(4));

                final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();

                final int circleSize = (int) (os_scale * map.getZoom() * DEFAULT_HEX_HEIGHT * 1.5);

                for (Point aPointList : pointList) {
                    final Point point = map.mapToDrawing(aPointList, os_scale);
                    g2d.drawOval(
                      point.x - circleSize / 2,
                      point.y - circleSize / 2,
                      circleSize,
                      circleSize
                    );
                }

                g2d.setStroke(oldStroke);
            }
        }
    }

    private void LoadSniperPosition() {

        pointList.clear();

        final PieceIterator pi = new PieceIterator(GameModule.getGameModule().getGameState().getAllPieces().iterator());

        while (pi.hasMoreElements()) {
            final GamePiece piece = pi.nextPiece();

            if(piece instanceof Stack) {
                for (PieceIterator pi2 = new PieceIterator(((Stack) piece).getPiecesIterator()); pi2.hasMoreElements(); ) {
                    GamePiece piece2 = pi2.nextPiece();
                    if (piece2.getName().contains("Sniper")) {
                        Point pos = piece2.getPosition();

                        if (!pointList.contains(pos))
                            pointList.add(pos);
                    }
                }
            }
            else if (piece.getName().contains("Sniper")) {
                Point pos = piece.getPosition();

                if (!pointList.contains(pos))
                    pointList.add(pos);
            }
        }
    }

    public void findSniper(boolean visible) {
        this.visible = visible;

        map.getView().repaint();
    }

    @Override
    public boolean drawAboveCounters() {
        return true;
    }

    @Override
    public void removeFrom(Buildable parent) {

    }

    @Override
    public HelpFile getHelpFile() {
        return null;
    }

    @Override
    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }

    @Override
    public void setup(boolean gameStarting) {
    }

    @Override
    public Command getRestoreCommand() {
        return null;
    }
}
