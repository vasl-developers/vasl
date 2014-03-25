/*
 * $Id: VASLThread 11/25/13 davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
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

import VASL.LOS.Map.VASLGameInterface;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASSAL.build.Buildable;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.command.Command;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.ColorConfigurer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;

import static VASSAL.build.GameModule.getGameModule;

public class VASLThread extends ASLThread implements KeyListener, GameComponent {

    public static final String ENABLED = "LosCheckEnabled";
    public static final String HINDRANCE_THREAD_COLOR = "hindranceThreadColor";
    public static final String BLOCKED_THREAD_COLOR = "blockedThreadColor";
    private boolean legacyMode = false;
    private final static String preferenceTabName = "LOS";
    private VASL.LOS.Map.Map LOSMap;

    // LOS stuff
    protected VASL.LOS.Map.LOSResult result;
    private VASL.LOS.Map.Location source;
    private VASL.LOS.Map.Location target;
    private VASLGameInterface VASLGameInterface;
    private ASLBoard upperLeftBoard;
    private boolean useAuxSourceLOSPoint;
    private boolean useAuxTargetLOSPoint;
    private String resultsString = "";

    // LOS colors
    private Color LOSColor;
    private Color hindranceColor;
    private Color blockedColor;

    /**
     * Called when the LOS check is started
     */
    protected void launch() {

        super.launch();

        // make sure we have a map otherwise disable LOS
        VASL.build.module.ASLMap theMap = (VASL.build.module.ASLMap) map;
        if(theMap == null || theMap.isLegacyMode()) {
            legacyMode = true;
        }
        else {
            legacyMode = false;
            LOSMap = theMap.getVASLMap();

            // initialize LOS
            result = new VASL.LOS.Map.LOSResult();
            VASLGameInterface = new VASLGameInterface(theMap, LOSMap);
            VASLGameInterface.updatePieces();

            // setting these to null prevents the last LOS from being shown when launched
            source = null;
            target = null;

        }

        // grab the user preferences for the thread colors
        super.threadColor = (Color) getGameModule().getPrefs().getValue(LOS_COLOR);
        LOSColor = (Color) getGameModule().getPrefs().getValue(LOS_COLOR);
        if (LOSPrefActive()) {

            LOSColor = (Color) getGameModule().getPrefs().getValue(LOS_COLOR);
            hindranceColor = (Color) getGameModule().getPrefs().getValue(HINDRANCE_THREAD_COLOR);
            blockedColor = (Color) getGameModule().getPrefs().getValue(BLOCKED_THREAD_COLOR);
            map.getView().requestFocus();
        }
    }

    public void addTo(Buildable buildable) {

        super.addTo(buildable);

        // add the key listener
        map.getView().addKeyListener(this);

        // add additional thread colors
        final BooleanConfigurer enable = new BooleanConfigurer(ENABLED, "Enable LOS checking", Boolean.TRUE);
        final JCheckBox enableBox = findBox(enable.getControls());

        final ColorConfigurer threadColor = new ColorConfigurer(LOS_COLOR, "Thread Color", Color.red);
        final ColorConfigurer hindrance = new ColorConfigurer(HINDRANCE_THREAD_COLOR, "Hindrance Thread Color", Color.red);
        final ColorConfigurer blocked = new ColorConfigurer(BLOCKED_THREAD_COLOR, "Blocked Thread Color", Color.blue);
        final BooleanConfigurer verbose = new BooleanConfigurer("verboseLOS", "Verbose LOS mode");
        getGameModule().getPrefs().addOption(preferenceTabName, threadColor);
        getGameModule().getPrefs().addOption(preferenceTabName, enable);
        getGameModule().getPrefs().addOption(preferenceTabName, hindrance);
        getGameModule().getPrefs().addOption(preferenceTabName, blocked);
        getGameModule().getPrefs().addOption(preferenceTabName, verbose);
        java.awt.event.ItemListener l = new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                enableAll(hindrance.getControls(), enableBox.isSelected());
                enableAll(blocked.getControls(), enableBox.isSelected());
                enableAll(verbose.getControls(), enableBox.isSelected());
            }
        };
        enableBox.addItemListener(l);
        enableAll(hindrance.getControls(), Boolean.TRUE.equals(enable.getValue()));
        enableAll(blocked.getControls(), Boolean.TRUE.equals(enable.getValue()));
        enableAll(verbose.getControls(), Boolean.TRUE.equals(enable.getValue()));

        // hook for game opening/closing
        getGameModule().getGameState().addGameComponent(this);
    }

    protected JCheckBox findBox(Component c) {
        JCheckBox val = null;
        if (c instanceof JCheckBox) {
            val = (JCheckBox) c;
        }
        for (int i = 0; i < ((Container) c).getComponentCount(); ++i) {
            val = findBox(((Container) c).getComponent(i));
            if (val != null) {
                break;
            }
        }
        return val;
    }

    private void enableAll(Component c, boolean enable) {
        c.setEnabled(enable);
        if (c instanceof Container) {
            for (int i = 0; i < ((Container) c).getComponentCount(); ++i) {
                enableAll(((Container) c).getComponent(i), enable);
            }
        }
    }

    public void mousePressed(MouseEvent e) {

        super.mousePressed(e);
        if (!isEnabled() || legacyMode) {
            return;
        }
        result.reset();

        // get the map point
        Point p = mapMouseToMapCoordinates(e.getPoint());
        if (p == null || !LOSMap.onMap(p.x, p.y)) return;

        // get the nearest location
        source = LOSMap.gridToHex(p.x, p.y).getNearestLocation(p.x, p.y);
        useAuxSourceLOSPoint = useAuxLOSPoint(source, p.x, p.y);

        // if Ctrl click, use upper location
        if (e.isControlDown()) {
            while (source.getUpLocation() != null) {
                source = source.getUpLocation();
            }
        }

        // make the source and the target the same
        target = source;
        useAuxTargetLOSPoint = useAuxSourceLOSPoint;
    }

    public void mouseReleased(MouseEvent e) {

        super.mouseReleased(e);
    }

    public void mouseDragged(MouseEvent e) {

        if (!legacyMode && source != null && isEnabled()) {

            // get the map point, ensure the point is on the CASL map
            Point p = mapMouseToMapCoordinates(map.mapCoordinates(e.getPoint()));
            if (p == null || !LOSMap.onMap(p.x, p.y)) return;
            VASL.LOS.Map.Location newLocation = LOSMap.gridToHex(p.x, p.y).getNearestLocation(p.x, p.y);
            boolean useAuxNewLOSPoint = useAuxLOSPoint(newLocation, p.x, p.y);

            // are we really in a new location?
            if (target == newLocation && useAuxTargetLOSPoint == useAuxNewLOSPoint) {
                return;
            }
            target = newLocation;
            useAuxTargetLOSPoint = useAuxNewLOSPoint;

            // if Ctrl click, use upper location
            if (e.isControlDown()) {
                while (target.getUpLocation() != null) {
                    target = target.getUpLocation();
                }
            }
            doLOS();
        }
        super.mouseDragged(e);
        map.repaint();
    }

    private boolean isEnabled() {
        return visible && LOSPrefActive();
    }

    private boolean LOSPrefActive() {
        return Boolean.TRUE.equals(getGameModule().getPrefs().getValue(ENABLED));
    }

    public void draw(Graphics g, VASSAL.build.module.Map m) {

        if (!LOSPrefActive() || legacyMode) {
            super.draw(g, m);
        }
        else if (visible) {

            lastAnchor = map.componentCoordinates(anchor);
            lastArrow = map.componentCoordinates(arrow);

            if (source != null && target != null) {
                // source LOS point
                Point sourceLOSPoint;
                if (useAuxSourceLOSPoint) {
                    sourceLOSPoint = new Point(source.getAuxLOSPoint());
                }
                else {
                    sourceLOSPoint = new Point(source.getLOSPoint());
                }
                sourceLOSPoint = mapPointToScreen(sourceLOSPoint);

                // target LOS point
                Point targetLOSPoint;
                if (useAuxTargetLOSPoint) {
                    targetLOSPoint = new Point(target.getAuxLOSPoint());
                }
                else {
                    targetLOSPoint = new Point(target.getLOSPoint());
                }
                targetLOSPoint = mapPointToScreen(targetLOSPoint);

                // transform the blocked-at point
                Point b = null;
                if (result.isBlocked()) {
                    b = new Point(result.getBlockedAtPoint());
                    b = mapPointToScreen(b);
                }
                // transform the hindrance point
                Point h = null;
                if (result.hasHindrance()) {
                    h = new Point(result.firstHindranceAt());
                    h = mapPointToScreen(h);
                }
                // draw the LOS thread
                if (result.isBlocked()) {
                    if (result.hasHindrance()) {
                        g.setColor(LOSColor);
                        g.drawLine(
                                sourceLOSPoint.x,
                                sourceLOSPoint.y,
                                h.x,
                                h.y);
                        g.setColor(hindranceColor);
                        g.drawLine(
                                h.x,
                                h.y,
                                b.x,
                                b.y);
                        g.setColor(blockedColor);
                        g.drawLine(
                                b.x,
                                b.y,
                                targetLOSPoint.x,
                                targetLOSPoint.y);
                    }
                    else {
                        g.setColor(LOSColor);
                        g.drawLine(
                                sourceLOSPoint.x,
                                sourceLOSPoint.y,
                                b.x,
                                b.y);
                        g.setColor(blockedColor);
                        g.drawLine(
                                b.x,
                                b.y,
                                targetLOSPoint.x,
                                targetLOSPoint.y);
                    }
                }
                else if (result.hasHindrance()) {
                    g.setColor(LOSColor);
                    g.drawLine(
                            sourceLOSPoint.x,
                            sourceLOSPoint.y,
                            h.x,
                            h.y);
                    g.setColor(hindranceColor);
                    g.drawLine(
                            h.x,
                            h.y,
                            targetLOSPoint.x,
                            targetLOSPoint.y);
                }
                else {
                    g.setColor(LOSColor);
                    g.drawLine(
                            sourceLOSPoint.x,
                            sourceLOSPoint.y,
                            targetLOSPoint.x,
                            targetLOSPoint.y);
                }

                // use the draw range property to turn all text on/off
                if (drawRange) {
                    // determine if the text should be above or below the location
                    boolean shiftSourceText = sourceLOSPoint.y > targetLOSPoint.y;
                    int shift = g.getFontMetrics().getHeight();

                    // draw the source elevation
                    switch (source.getBaseHeight() + source.getHex().getBaseHeight()) {
                        case -1:
                        case -2:
                            g.setColor(Color.red);
                            break;
                        case 0:
                            g.setColor(Color.gray);
                            break;
                        case 1:
                            g.setColor(Color.darkGray);
                            break;
                        case 2:
                            g.setColor(Color.black);
                            break;
                        default:
                            g.setColor(Color.white);
                    }
                    g.setFont(RANGE_FONT);
                    if (isVerbose()) {
                        lastRangeRect = drawText(g,
                                sourceLOSPoint.x - 20,
                                sourceLOSPoint.y + (shiftSourceText ? shift : 0) - g.getFontMetrics().getDescent(),
                                source.getName() + "  (Level " + (source.getBaseHeight() + source.getHex().getBaseHeight() + ")"));
                    }
                    else if (source.getBaseHeight() != 0) {
                        lastRangeRect = drawText(g,
                                sourceLOSPoint.x - 20,
                                sourceLOSPoint.y + (shiftSourceText ? shift : 0) - g.getFontMetrics().getDescent(),
                                "Level " + (source.getBaseHeight() + source.getHex().getBaseHeight()));
                    }

                    // draw the target elevation
                    switch (target.getBaseHeight() + target.getHex().getBaseHeight()) {
                        case -1:
                        case -2:
                            g.setColor(Color.red);
                            break;
                        case 0:
                            g.setColor(Color.gray);
                            break;
                        case 1:
                            g.setColor(Color.darkGray);
                            break;
                        case 2:
                            g.setColor(Color.black);
                            break;
                        default:
                            g.setColor(Color.white);
                    }
                    if (isVerbose()) {
                        lastRangeRect.add(drawText(g,
                                targetLOSPoint.x - 20,
                                targetLOSPoint.y + (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                                target.getName() + "  (Level " + (target.getBaseHeight() + target.getHex().getBaseHeight() + ")")));
                    }
                    else if (target.getBaseHeight() != 0) {
                        lastRangeRect.add(drawText(g,
                                targetLOSPoint.x - 20,
                                targetLOSPoint.y + (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                                "Level " + (target.getBaseHeight() + target.getHex().getBaseHeight())));
                    }

                    // draw the verbose text
                    g.setColor(Color.black);
                    if (shiftSourceText) {
                        lastRangeRect.add(drawText(g, targetLOSPoint.x - 20, targetLOSPoint.y - shift, resultsString));
                    }
                    else {
                        lastRangeRect.add(drawText(g, targetLOSPoint.x - 20, targetLOSPoint.y + shift * 2 - 2, resultsString));
                    }
                }
            }
        }
        else {
            super.draw(g, m);
        }
    }

    private boolean isVerbose() {
        return Boolean.TRUE.equals(getGameModule().getPrefs().getValue("verboseLOS"));
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {

        if (!isEnabled() && legacyMode) {
            return;
        }
        int code = e.getKeyCode();
        // move up
        if (code == KeyEvent.VK_KP_UP || code == KeyEvent.VK_UP) {

            e.consume(); // prevents the map from scrolling when trying to move end point

            // move the source up
            if (e.isControlDown() && source != null) {
                if (source.getUpLocation() != null) {
                    source = source.getUpLocation();
                    doLOS();
                    map.repaint();
                }
            }
            // move the target up
            else if (target != null) {
                if (target.getUpLocation() != null) {
                    target = target.getUpLocation();
                    doLOS();
                    map.repaint();
                }
            }
        }

        // move down
        else if (code == KeyEvent.VK_KP_DOWN || code == KeyEvent.VK_DOWN) {

            e.consume();

            // move the source down
            if (e.isControlDown() && source != null) {
                if (source.getDownLocation() != null) {
                    source = source.getDownLocation();
                    doLOS();
                    map.repaint();
                }
            }
            // move the target down
            else if (target != null) {
                if (target.getDownLocation() != null) {
                    target = target.getDownLocation();
                    doLOS();
                    map.repaint();

                }
            }
        }
    }

    private boolean useAuxLOSPoint(VASL.LOS.Map.Location l, int x, int y) {

        Point LOSPoint = l.getLOSPoint();
        Point AuxLOSPoint = l.getAuxLOSPoint();

        // use the closest LOS point
        if (Point.distance(x, y, LOSPoint.x, LOSPoint.y) > Point.distance(x, y, AuxLOSPoint.x, AuxLOSPoint.y)) {
            return true;
        }
        return false;
    }

    public Command getRestoreCommand() {
        return null;
    }

    private Point mapPointToScreen(Point p) {

        Point temp = map.componentCoordinates(new Point(p));
        double scale = upperLeftBoard == null ? 1.0 : upperLeftBoard.getMagnification() * ((HexGrid)upperLeftBoard.getGrid()).getHexSize()/ASLBoard.DEFAULT_HEX_HEIGHT;
        if (upperLeftBoard != null) {
            temp.x = (int)Math.round(temp.x*scale);
            temp.y = (int)Math.round(temp.y*scale);
        }
        temp.translate((int) (map.getEdgeBuffer().width * map.getZoom()), (int) (map.getEdgeBuffer().height * map.getZoom()));

        return temp;
    }

    private Point mapMouseToMapCoordinates(Point p) {

        // just remove edge buffer
        Point temp = new Point(p);
        temp.translate(-map.getEdgeBuffer().width, -map.getEdgeBuffer().height);
        return temp;
    }

    /**
     * Draws some text on the map
     * @param g the map graphics
     * @param x upper left point
     * @param y upper left point
     * @param s the text
     * @return a bounding region of the text drawn
     */
    private Rectangle drawText(Graphics g, int x, int y, String s) {

        // paint the background
        int border = 1;
        g.setColor(Color.black);
        Rectangle region = new Rectangle(
                x - border,
                y - border - g.getFontMetrics().getHeight() + g.getFontMetrics().getDescent(),
                g.getFontMetrics().stringWidth(s) + border * 2,
                g.getFontMetrics().getHeight() + border * 2);
        g.fillRect(region.x, region.y, region.width, region.height);

        // draw the text
        g.setColor(Color.white);
        g.drawString(s, x, y);
        return region;
    }

    /**
     * Execute the LOS
     */
    private void doLOS() {

        // silently ignore invalid LOS checks
        if(source == null || target == null || result == null || VASLGameInterface == null)
        {
            return;
        }

        // do the LOS
        LOSMap.LOS(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, VASLGameInterface);

        // set the result string
        resultsString =
                "Range: " + result.getRange();
        lastRange = String.valueOf(result.getRange());
        if (isVerbose()) {
            if (result.isBlocked()) {
                resultsString += "  Blocked in " + LOSMap.gridToHex(result.getBlockedAtPoint().x, result.getBlockedAtPoint().y).getName() +
                        " ( " + result.getReason() + ")";
            }
            else {
                resultsString += (result.getHindrance() > 0 ? ("  Hindrances: " + result.getHindrance()) : "");
            }
        }
    }

}
