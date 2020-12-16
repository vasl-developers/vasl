/*
 * $Id: ASLPieceFinder.java 0000 2009-03-09 03:22:10Z davidsullivan1 $
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

import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_HEIGHT;
import static VASSAL.build.GameModule.getGameModule;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.tools.swing.SwingUtils;
import VASSAL.configure.BooleanConfigurer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * This component highlights a spot on the board.
 * It's handy when you need to draw your opponent's attention to a piece you are rallying, moving, etc.
 */
public class ASLPieceFinder extends AbstractConfigurable implements CommandEncoder, GameComponent, Drawable, ActionListener, MouseListener {

    public static final String COMMAND_PREFIX = "PIECE_FINDER:";
    private Map map;

    // animation control - for drawing the red circle
    final private static int CIRCLE_DURATION = 2000;
    private Point clickPoint;
    private Boolean active = false;
    private Timer timer;
    private static final String preferenceTabName = "VASL";
    // this component is not configurable
    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[] {String.class};
    }

    @Override
    public String[] getAttributeNames() {
        return new String[] {"Name"};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[] {"Name"};
    }

    @Override
    public String getAttributeValueString(String key) {

        return "ASL Piece Finder";
    }

    @Override
    public void setAttribute(String key, Object value) {
    }

    @Override
    public void addTo(Buildable parent) {

        // add this component to the game and register a mouse listener
        if (parent instanceof Map) {
            this.map = (Map) parent;
            getGameModule().addCommandEncoder(this);
            map.addDrawComponent(this);
            map.addLocalMouseListener(this);
            timer = new Timer (CIRCLE_DURATION, this);
            final BooleanConfigurer Highlightcenter = new BooleanConfigurer("HighlightCentered", "Opponent's Highlight Centered on Your Map", true);
            getGameModule().getPrefs().addOption(preferenceTabName, Highlightcenter);
            final BooleanConfigurer Highlightcircle = new BooleanConfigurer("HighlightShowCircle", "Highlight Shows Red Circle on Map", true);
            getGameModule().getPrefs().addOption(preferenceTabName, Highlightcircle);
        }
    }

    public void startAnimation(boolean isLocal) {

        // do not adjust the view of the player who initiated the command
        if (!isLocal && isHighlightCentered()) {
            map.centerAt(clickPoint);
        }

        active = true;
        timer.restart();
        map.getView().repaint();
    }

    @Override
    public void draw(Graphics g, Map map) {
        if (!active || clickPoint == null) {
          return;
        }

        final Graphics2D g2d = (Graphics2D) g;
        final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();

        final int diameter = (int)(map.getZoom() * os_scale * DEFAULT_HEX_HEIGHT * 2);

        // translate the piece center for current zoom
        final Point p = map.mapToDrawing(clickPoint, os_scale);

        // draw a circle around the selected point
        if (isHighlightShowCircle()) {
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke((float) (3 * os_scale)));
            g2d.drawOval(p.x - diameter / 2, p.y - diameter / 2, diameter, diameter);
        }
    }

    @Override
    public boolean drawAboveCounters() {
        return true;
    }

    @Override
    public void removeFrom(Buildable parent) {

    }

    /**
     * Command string is of the form PIECE_FINDER:x,y where x and y are the coordinates of the mouse click
     * @param c the command
     * @return the command string
     */
    public String encode(Command c) {
        if (c instanceof FindPieceCommand) {
            return COMMAND_PREFIX + (
                    (FindPieceCommand) c).getClickPoint().x +
                    "," +
                    ((FindPieceCommand) c).getClickPoint().y;
        }
        else {
            return null;
        }
    }

    public Command decode(String s) {
        if (s.startsWith(COMMAND_PREFIX)) {

            // decode the piece location
            int x = Integer.parseInt(s.substring(s.indexOf(":") + 1, s.indexOf(",")));
            int y = Integer.parseInt(s.substring(s.indexOf(",") + 1));

            clickPoint = new Point(x,y);
            return new FindPieceCommand(this);
        }
        else {
            return null;
        }
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
    /**
     * Called when the animation timer expires
     */
    public void actionPerformed(ActionEvent e) {

        active = false;
        timer.stop();
        map.repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    /**
     * Initiate the command with the mouse press
     */
    public void mousePressed(MouseEvent e) {
        if (SwingUtils.isSelectionToggle(e)){ //BR// Vassal 3.3 mouse interface adjustment
            clickPoint = new Point(e.getX(), e.getY());
            GameModule mod = getGameModule();
            Command c = new FindPieceCommand(this);
            mod.sendAndLog(c);
            startAnimation(true);
        }
    }

    private static boolean isHighlightCentered() {
        return Boolean.TRUE.equals(getGameModule().getPrefs().getValue("HighlightCentered"));
    }
    private static boolean isHighlightShowCircle() {
        return Boolean.TRUE.equals(getGameModule().getPrefs().getValue("HighlightShowCircle"));
    }
    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void setup(boolean gameStarting) {
    }

    @Override
    public Command getRestoreCommand() {
        return null;
    }

    public void setClickPoint(Point p) {

        clickPoint = p;
    }

    public Point getClickPoint(){
        return clickPoint;
    }
}

class FindPieceCommand extends Command {

    private ASLPieceFinder finder;

    private Point clickPoint;

    public FindPieceCommand(ASLPieceFinder finder) {
        clickPoint = new Point(finder.getClickPoint());
        this.finder = finder;
    }

    protected void executeCommand() {
        finder.setClickPoint(clickPoint);
        finder.startAnimation(false);
    }

    protected Command myUndoCommand() {
        return null;
    }

    public int getValue() {
        return 0;
    }

    public Point getClickPoint(){
        return clickPoint;
    }
}
