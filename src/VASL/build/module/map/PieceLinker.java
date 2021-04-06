package VASL.build.module.map;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.GameState;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.configure.ColorConfigurer;
import VASSAL.configure.NamedHotKeyConfigurer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Properties;
import VASSAL.tools.NamedKeyStroke;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Copyright (c) 2017 by David Sullivan
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
 *
 * This class links two pieces and draws a line between them when with piece is selected
 * Links to you opponents concealed or HIP pieces are not shown
 */
public class PieceLinker extends AbstractConfigurable implements KeyListener, CommandEncoder, GameComponent, Drawable {

    private static final String LINK_COMMAND_PREFIX = "LINK_PIECE:";
    private static final String UNLINK_COMMAND_PREFIX = "UNLINK_PIECE:";
    private Map map;

    private static final String NAME = "Name";
    private static final String THREAD_COLOR = "Color";
    private static final String THREAD_WIDTH = "ThreadWidth";
    private static final String LINK_KEY = "LinkKey";
    private static final String UNLINK_KEY = "UnlinkKey";

    private Color threadColor = Color.RED;
    private int threadWidth = 2;
    private NamedKeyStroke linkKey = new NamedKeyStroke("71dd9846"); // CTL+ALT+L
    private NamedKeyStroke unlinkKey = new NamedKeyStroke("b385611"); // CTL+ALT+U

    // links are one to many and two-way
    private HashMap<String, ArrayList<String>> links = new HashMap<String, ArrayList<String>>(10);

    // not used for now but set to false to toggle all links off
    private boolean visible = true;

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{
                String.class,
                Color.class,
                Integer.class,
                NamedKeyStroke.class,
                NamedKeyStroke.class
        };
    }

    @Override
    public String[] getAttributeNames() {
        return new String[]{
                NAME,
                THREAD_COLOR,
                THREAD_WIDTH,
                LINK_KEY,
                UNLINK_KEY};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{
                "Piece Linker",
                "Thread Color",
                "Thread width",
                "Link Key",
                "Unlink Key",
        };
    }

    @Override
    public String getAttributeValueString(String key) {

        if (NAME.equals(key)) {
            return getConfigureName();
        } else if (THREAD_COLOR.equals(key)) {
            return ColorConfigurer.colorToString(threadColor);
        } else if (THREAD_WIDTH.equals(key)) {
            return String.valueOf(threadWidth);
        } else if (LINK_KEY.equals(key)) {
            return NamedHotKeyConfigurer.encode(linkKey);
        } else if (UNLINK_KEY.equals(key)) {
            return NamedHotKeyConfigurer.encode(unlinkKey);
        } else {
            return null;
        }
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (NAME.equals(key)) {
            setConfigureName((String) value);
        } else if (THREAD_COLOR.equals(key)) {
            if (value instanceof String) {
                value = ColorConfigurer.stringToColor((String) value);
            }
            threadColor = (Color) value;
        } else if (THREAD_WIDTH.equals(key)) {
            if (value instanceof String) {
                value = Integer.valueOf((String) value);
            }
            threadWidth = (Integer) value;
        } else if (LINK_KEY.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            linkKey = (NamedKeyStroke) value;
        } else if (UNLINK_KEY.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            unlinkKey = (NamedKeyStroke) value;
        }
    }

    @Override
    public void addTo(Buildable parent) {

        // add this component to the game and register a mouse listener
        if (parent instanceof Map) {
            this.map = (Map) parent;
            GameModule mod = GameModule.getGameModule();
            mod.addCommandEncoder(this);
            mod.getGameState().addGameComponent(this);
            map.addDrawComponent(this);
            map.getView().addKeyListener(this);
        }
    }

    /**
     * Draw a line between the two linked pieces
     */
    @Override
    public void draw(Graphics g, Map map) {
        if (visible && !links.isEmpty()) {

            g.setColor(threadColor);

            Graphics2D g2d = (Graphics2D) g;

            Stroke oldStroke = g2d.getStroke();
            g2d.setStroke(new BasicStroke(threadWidth));

            final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();

            final GameState gs = GameModule.getGameModule().getGameState();

            for (String fromPieceID : links.keySet()) {
                GamePiece fromPiece = gs.getPieceForId(fromPieceID);
                if (fromPiece != null &&
                        isSelected(fromPiece) &&
                        (fromPiece.getProperty(Properties.INVISIBLE_TO_ME) == null || Boolean.FALSE.equals(fromPiece.getProperty(Properties.INVISIBLE_TO_ME))) &&
                        (fromPiece.getProperty(Properties.OBSCURED_TO_ME) == null || Boolean.FALSE.equals(fromPiece.getProperty(Properties.OBSCURED_TO_ME)))) {

                    ArrayList<String> toPieceIDs = links.get(fromPieceID);
                    for (String s : toPieceIDs) {
                        GamePiece toPiece = gs.getPieceForId(s);
                        if (toPiece != null &&
                                (toPiece.getProperty(Properties.INVISIBLE_TO_ME) == null || Boolean.FALSE.equals(toPiece.getProperty(Properties.INVISIBLE_TO_ME))) &&
                                (toPiece.getProperty(Properties.OBSCURED_TO_ME) == null || Boolean.FALSE.equals(toPiece.getProperty(Properties.OBSCURED_TO_ME)))) {
                            Point p1 = map.mapToDrawing(fromPiece.getPosition(), os_scale);
                            Point p2 = map.mapToDrawing(toPiece.getPosition(), os_scale);
                            g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
                        }
                    }
                }
            }
            g2d.setStroke(oldStroke);
        }
    }

    @Override
    public boolean drawAboveCounters() {
        return false;
    }

    /**
     * Encodes a link command
     * @param c the link command
     * @return command string of the form LINK_PIECE:<piece ID>:<piece ID>
     */
    public String encode(Command c) {
        if (c instanceof LinkPiecesCommand) {
            LinkPiecesCommand lpc = (LinkPiecesCommand) c;
            return LINK_COMMAND_PREFIX + lpc.getFromPieceID() + "," + lpc.getToPieceID();
        } else if (c instanceof UnlinkPiecesCommand) {
            UnlinkPiecesCommand upc = (UnlinkPiecesCommand) c;
            return UNLINK_COMMAND_PREFIX + upc.getFromPieceID() + "," + upc.getToPieceID();
        } else {
            return null;
        }
    }

    /**
     * Decodes a link command
     * @param s command string of the form LINK_PIECE:<piece ID>:<piece ID>
     * @return
     */
    public Command decode(String s) {
        if (s.startsWith(LINK_COMMAND_PREFIX)) {

            // decode the linked piece IDs
            String fromPieceID = s.substring(s.indexOf(":") + 1, s.indexOf(","));
            String toPieceID = s.substring(s.indexOf(",") + 1);

            return new LinkPiecesCommand(this, fromPieceID, toPieceID);
        } else if (s.startsWith(UNLINK_COMMAND_PREFIX)) {

            // decode the linked piece IDs
            String fromPieceID = s.substring(s.indexOf(":") + 1, s.indexOf(","));
            String toPieceID = s.substring(s.indexOf(",") + 1);

            return new UnlinkPiecesCommand(this, fromPieceID, toPieceID);
        } else {
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
     * Called when a game is starting
     */
    public void setup(boolean gameStarting) {

        // clear links when game is closed
        if (!gameStarting) {
            links.clear();
        }
    }

    @Override
    /**
     * This command is created when a saved game is opened
     */
    public Command getRestoreCommand() {

        Command c = new NullCommand();
        if (!links.isEmpty()) {
            for (String fromPieceID : links.keySet()) {
                for (String toPieceID : links.get(fromPieceID)) {
                    Command l = new LinkPiecesCommand(this, fromPieceID, toPieceID);
                    c.append(l);
                }
            }
        }
        return c;
    }

    @Override
    public void removeFrom(Buildable parent) {

    }

    /**
     * Links two pices
     * @param fromLinkID the from piece
     * @param toLinkID the too piece
     */
    private void addLink(String fromLinkID, String toLinkID) {

        // can't link a piece to itself
        if (fromLinkID.equals(toLinkID)) {
            return;
        }

        // link can't already exist
        if (links.containsKey(fromLinkID) && links.get(fromLinkID).contains(toLinkID)) {
            return;
        }

        ArrayList<String> tempList;
        if (links.containsKey(fromLinkID)) {
            tempList = links.get(fromLinkID);
        } else {
            tempList = new ArrayList<String>();
        }
        tempList.add(toLinkID);
        links.put(fromLinkID, tempList);
    }

    /**
     * Remove the link between two pieces
     * @param fromLinkID the from piece
     * @param toLinkID the too piece
     */
    private void removeLink(String fromLinkID, String toLinkID) {

        ArrayList<String> tempList;
        if (links.containsKey(fromLinkID)) {
            tempList = links.get(fromLinkID);
            if (tempList.size() == 1) {
                links.remove(fromLinkID);
            } else {
                tempList.remove(toLinkID);
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    /**
     * Link and unlink keys are defined via the configuration
     * The defaults are CTL+ALT+l and CTL+ALT+u
     */
    public void keyPressed(KeyEvent e) {

        final int code = e.getKeyCode();
        if (linkKey.equals(NamedKeyStroke.of(e))) {

            LinkedList<GamePiece> selectedPieces = getSelectedPieces();
            if (selectedPieces.size() == 2) {
                // links go both ways
                Command c = new LinkPiecesCommand(this, selectedPieces.get(0).getId(), selectedPieces.get(1).getId());
                c.append(new LinkPiecesCommand(this, selectedPieces.get(1).getId(), selectedPieces.get(0).getId()));
                c.execute();
                GameModule.getGameModule().sendAndLog(c);
                map.repaint();
            }
            e.consume();
        } else if (unlinkKey.equals(NamedKeyStroke.of(e))) {
            LinkedList<GamePiece> selectedPieces = getSelectedPieces();
            if (selectedPieces.size() == 2) {
                Command c = new UnlinkPiecesCommand(this, selectedPieces.get(0).getId(), selectedPieces.get(1).getId());
                c.append(new UnlinkPiecesCommand(this, selectedPieces.get(1).getId(), selectedPieces.get(0).getId()));
                c.execute();
                GameModule.getGameModule().sendAndLog(c);
                map.repaint();
            }
            e.consume();
        }

    }

    /**
     * Get all currently selected pieces
     *
     * @return LinkedList of selected pieces
     */
    private LinkedList<GamePiece> getSelectedPieces() {

        LinkedList<GamePiece> temp = new LinkedList<GamePiece>();
        for (GamePiece piece : GameModule.getGameModule().getGameState().getAllPieces()) {
            if (isSelected(piece)) {
                temp.add(piece);
            }
        }
        return temp;
    }

    /**
     * @param p the piece
     * @return true if the piece is selected
     */
    private boolean isSelected(GamePiece p) {
        return Boolean.TRUE.equals(p.getProperty(Properties.SELECTED)) &&
                p.getId() != null &&
                !"".equals(p.getId());
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    /**
     * Command to link two pieces
     */
    private class LinkPiecesCommand extends Command {

        PieceLinker linker;
        String fromPieceID;
        String toPieceID;

        LinkPiecesCommand(PieceLinker linker, String fromPieceID, String toPieceID) {
            this.linker = linker;
            this.fromPieceID = fromPieceID;
            this.toPieceID = toPieceID;
        }

        protected void executeCommand() {

            linker.addLink(fromPieceID, toPieceID);
        }

        protected Command myUndoCommand() {
            return new UnlinkPiecesCommand(linker, fromPieceID, toPieceID);
        }

        String getFromPieceID() {
            return fromPieceID;
        }

        String getToPieceID() {
            return toPieceID;
        }
    }

    /**
     * Command to unlink two pieces
     */
    private class UnlinkPiecesCommand extends LinkPiecesCommand {

        UnlinkPiecesCommand(PieceLinker linker, String fromPieceID, String toPieceID) {
            super(linker, fromPieceID, toPieceID);
        }

        protected void executeCommand() {
            linker.removeLink(fromPieceID, toPieceID);
        }

        protected Command myUndoCommand() {
            return new LinkPiecesCommand(linker, fromPieceID, toPieceID);
        }

    }
}
