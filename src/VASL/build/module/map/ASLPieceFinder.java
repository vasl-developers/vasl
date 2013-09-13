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

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;

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
    final private static int CIRCLE_SIZE = 100;
    final private static int CIRCLE_DURATION = 2000;
    private Point clickPoint;
    private Boolean active = false;
    private Timer timer;

    // this component is not configurable
    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[] {String.class};
    }

    @Override
    public String[] getAttributeNames() {
        return new String[] {""};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[] {""};
    }

    @Override
    public String getAttributeValueString(String key) {

        return "";
    }

    @Override
    public void setAttribute(String key, Object value) {
    }

    @Override
    public void addTo(Buildable parent) {

        // add this component to the game and register a mouse listener
        if (parent instanceof Map) {
            this.map = (Map) parent;
            GameModule.getGameModule().addCommandEncoder(this);
            map.addDrawComponent(this);
            map.addLocalMouseListener(this);
            timer = new Timer (CIRCLE_DURATION, this);

        }
    }

    public void startAnimation(boolean isLocal) {

        // do not adjust the view of the player who initiated the command
        if (!isLocal) {
            map.centerAt(clickPoint);
        }

        active = true;
        timer.restart();
    }

    @Override
    public void draw(Graphics g, Map map) {

        if (active && clickPoint != null) {

            // translate the piece center for current zoom
            Point p = new Point(clickPoint);
            p.x *= map.getZoom();
            p.y *= map.getZoom();

            // draw a circle around the selected point
            Graphics2D gg = (Graphics2D) g;
            g.setColor(Color.RED);
            gg.setStroke(new BasicStroke(3));
            gg.drawOval(p.x - CIRCLE_SIZE/2, p.y - CIRCLE_SIZE/2, CIRCLE_SIZE, CIRCLE_SIZE);
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
            return COMMAND_PREFIX + clickPoint.x + "," + clickPoint.y;
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

        if(e.isControlDown()){
            clickPoint = new Point(e.getX(), e.getY());
            GameModule mod = GameModule.getGameModule();
            Command c = new FindPieceCommand(this);
            mod.sendAndLog(c);
            startAnimation(true);
        }
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
}

class FindPieceCommand extends Command {

    private ASLPieceFinder finder;

    public FindPieceCommand(ASLPieceFinder finder) {

        this.finder = finder;
    }

    protected void executeCommand() {

        finder.startAnimation(false);
    }

    protected Command myUndoCommand() {
        return null;
    }

    public int getValue() {
        return 0;
    }
}
