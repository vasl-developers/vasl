/*
 * $Id: DoubleBlindViewer 3/30/14 davidsullivan1 $
 *
 * Copyright (c) 2014 by David Sullivan
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

import VASL.LOS.Map.LOSResult;
import VASL.LOS.Map.Location;
import VASL.LOS.VASLGameInterface;
import VASL.build.module.ASLMap;
import VASL.counters.ASLProperties;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.AutoConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.chat.SynchCommand;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.configure.*;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.i18n.TranslatableConfigurerFactory;
import VASSAL.tools.FormattedString;
import VASSAL.tools.LaunchButton;
import VASSAL.tools.NamedKeyStroke;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import static VASSAL.build.GameModule.getGameModule;

/**
 * This component filters the players view of the board so only units in the player's LOS are shown.
 */
public class DoubleBlindViewer extends AbstractConfigurable implements CommandEncoder, GameComponent {

    protected static final String COMMAND_SEPARATOR = ":";
    public static final String COMMAND_PREFIX = "DOUBLE_BLIND" + COMMAND_SEPARATOR;
    protected static final String ICON_FILE_NAME = "/images/sauroneye.png";
    protected static final String TEXT_ICON = "<(o)>";
    protected static final String DEFAULT_PASSWORD = "#$none$#";
    protected static final String PLAYER_NAME = "RealName";

    // preference constants
    public static final String DB_ENABLED = "DoubleBlindEnabled";
    public static final String GAME_PASSWORD = "gamePassword";

    // VASSAL attribute codes
    public static final String PROPERTY_TAB = "propertiesTab"; // properties tab name
    public static final String REPORT_FORMAT = "reportFormat"; // report DB updates?
    public static final String REPORT = "report";              // chatter string when DB update reported

    // piece dynamic property constants
    protected static final String OWNER_PROPERTY = "Owner"; // contains the player name of the piece owner

    // button attribute codes
    public static final String LABEL = "label";
    public static final String TOOLTIP = "tooltip";
    public static final String HOT_KEY = "hotKey";
    public static final String ICON_NAME = "iconName";

    // attributes
    protected String propertyTab = "LOS";
    protected boolean report = true;
    protected FormattedString reportFormat = new FormattedString(TEXT_ICON);

    // save the old preference setting - these are disabled during DB play
    Boolean oldCenterOnMove = Boolean.TRUE;
    Boolean oldAutoReport = Boolean.TRUE;

    // class properties
    protected ASLMap map;
    protected LaunchButton launchButton;
    protected String myPlayerName;
    protected VASLGameInterface VASLGameInterface;
    protected HashMap<String, Player> players = new HashMap<String, Player>(2); // list of all players in the game

    public DoubleBlindViewer() {

        // initialize the button action listener
        ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                buttonAction();
            }
        };
        launchButton = new LaunchButton("", TOOLTIP, LABEL, HOT_KEY, ICON_NAME, al);
        launchButton.setAttribute(ICON_NAME, ICON_FILE_NAME);
        launchButton.setAttribute(TOOLTIP, "Update DB View");
    }

    /**
     * Sends the command and updates the view when the button or button hot key is pressed
     */
    public void buttonAction() {

        // get the VASL interface if necessary
        if(VASLGameInterface == null){
            VASLGameInterface = new VASLGameInterface(map, map.getVASLMap());
        }
        VASLGameInterface.updatePieces();

        // warn only if the DB preference is turned off
        if(getGameModule().getPrefs().getValue(DB_ENABLED).equals(Boolean.FALSE)) {
            getGameModule().getChatter().send("Double blind has not been enabled");
        }
        else {
            GameModule.getGameModule().sendAndLog(new DoubleBlindUpdateCommand(players.get(myPlayerName)));
            if(report) {
                GameModule.getGameModule().getChatter().send(TEXT_ICON);
            }
            updateView();
        }
    }

    /**
     * Updates the player's view of the map, hiding pieces that are out of LOS
     */
    private void updateView() {

        // turn off "report move" and "center on opponents move" options
        getGameModule().getPrefs().getOption("centerOnMove").setValue(Boolean.FALSE);
        getGameModule().getPrefs().getOption("autoReport").setValue(Boolean.FALSE);

        // hide all pieces out of LOS
        GamePiece[] allPieces = map.getAllPieces();
        for (GamePiece piece : allPieces) {
            if (piece instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    setPieceVisibility(pi.nextPiece());
                }
            } else {
                setPieceVisibility(piece);
            }
        }
        map.repaint();
    }

    /**
     * Hides a piece if it's out of LOS
     * @param piece the piece
     */
    public void setPieceVisibility(GamePiece piece) {

        // player owns piece?
        if(piece.getProperty(OWNER_PROPERTY) == null)  {
            setPieceInLOS(piece);
        }
        else if(!isMyPiece(piece)){

            // step through all pieces owned by me and check for LOS
            GamePiece[] allPieces = map.getAllPieces();
            for (GamePiece p : allPieces) {
                if (p instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator()); pi.hasMoreElements(); ) {
                        GamePiece p2 = pi.nextPiece();
                        if (isMyPiece(p2)) {

                            if (isViewable(piece, p2)) {
                                setPieceInLOS(piece);
                                return;
                            }
                        }
                    }
                } else {

                    if (isViewable(piece, p)) {
                        setPieceInLOS(p);
                        return;
                    }
                }
            }

            // this piece was not seen if we get this far
            setPieceNotInLOS(piece);
        }
    }

    /**
     * @param piece the piece
     * @return true if I own the piece
     */
    private boolean isMyPiece(GamePiece piece) {

        return  piece.getProperty("Owner") != null && piece.getProperty("Owner").equals(myPlayerName);
    }

    /**
     * Marks a piece as visible so it will be drawn on the map
     * @param piece the piece
     */
    private void setPieceInLOS(GamePiece piece) {

        // ignore HIP pieces
        if(!isHIP(piece)){
            piece.setProperty(Properties.HIDDEN_BY, null);
        }
    }

    /**
     * Marks a piece as NOT visible so it will NOT be drawn on the map
     * @param piece the piece
     */
    private void setPieceNotInLOS(GamePiece piece) {

        // get the owner's game password or use default
        String owner = (String) piece.getProperty(OWNER_PROPERTY);
        String gamePassword = DEFAULT_PASSWORD;
        Player opponent = players.get(owner);
        if(opponent != null) {
            gamePassword = opponent.getGamePassword();
        }

        // use owner's game password to hid the piece
        if(!isHIP(piece)) {
            piece.setProperty(Properties.HIDDEN_BY, gamePassword);
        }
    }

    /**
     * @param piece the piece
     * @return true if this piece has been HIP'ed by opponent
     */
    private boolean isHIP(GamePiece piece) {

        // not HIP if not hidden
        String hiddenBy = (String) piece.getProperty(Properties.HIDDEN_BY);
        if(hiddenBy == null) {
            return false;
        }

        debug("Piece " + piece.getName() + " is hidden by " + hiddenBy);

        // otherwise check other players
        for (Player p : players.values()) {
            if(!p.getName().equals(myPlayerName)) {
                if(hiddenBy.equals(p.getID())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Can piece1 see piece2?
     * Sub-classes could override this method to implement custom sighting rules
     * @param piece1 the viewing game piece
     * @param piece2 the
     * @return true if piece1 can see piece2
     */
    public boolean isViewable(GamePiece piece1, GamePiece piece2) {

/*        // can the unit spot?
        if (!VASLGameInterface.isDBUnit(piece1)) {
            return false;
        }*/
        // get the piece location
        Location l1 = getLocation(piece1);
        Location l2 = getLocation(piece2);

        // off board?
        if(l1 == null || l2 == null){
            return false;
        }

        // check the LOS
        LOSResult result = new LOSResult();
        map.getVASLMap().LOS(l1, false, l2, false, result, VASLGameInterface);
        return !result.isBlocked();
    }

    /**
     * Finds the location for the given piece
     * @param piece the piece
     * @return the location - null if error or none
     */
    protected Location getLocation(GamePiece piece) {

        // get the VASL interface if necessary
        if(VASLGameInterface == null){
            return null;
        }

        return VASLGameInterface.getLocation(piece);

/*        // determine what hex and location the piece is in
        Point p = map.mapCoordinates(new Point(piece.getPosition()));
        p.x *= map.getZoom();
        p.y *= map.getZoom();
        p.translate(-map.getEdgeBuffer().width, -map.getEdgeBuffer().height);

        if (map == null || map.getVASLMap() == null || !map.getVASLMap().onMap(p.x, p.y)) {
            return null;
        }
        Hex h = map.getVASLMap().gridToHex(p.x, p.y);
        return h.getNearestLocation(p.x, p.y);
 */
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{
                String.class,
                Boolean.class,
                ReportFormatConfig.class,
                String.class,
                String.class,
                IconConfig.class,
                NamedKeyStroke.class
        };
    }

    @Override
    public String[] getAttributeNames() {
        return new String[]{
                PROPERTY_TAB,
                REPORT,
                REPORT_FORMAT,
                LABEL,
                TOOLTIP,
                ICON_NAME,
                HOT_KEY
        };
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{
                "Properties tab: ",
                "Report DB updates?  ",
                "Report format: ",
                "Button text: ",
                "Tool tip: ",
                "Button icon: ",
                "Hotkey: "
        };
    }

    @Override
    public String getAttributeValueString(String key) {

        if (PROPERTY_TAB.equals(key)) {
            return propertyTab;
        }
        else if (REPORT.equals(key)) {
            return String.valueOf(report);
        }
        else  if(REPORT_FORMAT.equals(key)) {
            return reportFormat.getText();
        }
        else {
            return launchButton.getAttributeValueString(key);
        }
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (PROPERTY_TAB.equals(key)) {
            if (value instanceof String) {
                propertyTab = (String) value;
            }
        }
        else if (REPORT.equals(key)) {
            if (value instanceof String) {
                report = Boolean.valueOf((String) value);
            }
        }
        else if (REPORT_FORMAT.equals(key)) {
            if (value instanceof String) {
                reportFormat.setFormat((String) value);
            }
        }
        else {
            launchButton.setAttribute(key, value);
        }
    }

    @Override
    public void addTo(Buildable parent) {

        // player preferences - enable double blind and game password
        final BooleanConfigurer doubleBlind = new BooleanConfigurer(DB_ENABLED, "Enable double blind", Boolean.FALSE);
        final JCheckBox enableBox = findBox(doubleBlind.getControls());

        final StringConfigurer gamePassword = new StringConfigurer(GAME_PASSWORD, "Game password: " , DEFAULT_PASSWORD);
        getGameModule().getPrefs().addOption(propertyTab, doubleBlind);
        getGameModule().getPrefs().addOption(propertyTab, gamePassword);
        java.awt.event.ItemListener l = new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                enableAll(gamePassword.getControls(), enableBox.isSelected());
            }
        };
        enableBox.addItemListener(l);
        enableAll(gamePassword.getControls(), Boolean.TRUE.equals(doubleBlind.getValue()));

        // add this component to the map toolbar
        if (parent instanceof Map) {
            this.map = (ASLMap) parent;
            map.getToolBar().add(launchButton);
            GameModule.getGameModule().addCommandEncoder(this);
        }

        // record the player information
        myPlayerName = (String) getGameModule().getPrefs().getValue(PLAYER_NAME);
        String myGamePassword = (String) getGameModule().getPrefs().getValue(GAME_PASSWORD);
        if (myGamePassword == null || myGamePassword.equals("")) {
            myGamePassword = DEFAULT_PASSWORD;
        }
        players.put(myPlayerName, new Player(myPlayerName, GameModule.getUserId(), myGamePassword));

        GameModule.getGameModule().getGameState().addGameComponent(this);
    }

    /**
     * Find the checkbox for the given component
     * @param c the component
     * @return the checkbox
     */
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

    /**
     * Enables or disables all double blind preferences
     * @param c the component
     * @param enable true to enable
     */
    private void enableAll(Component c, boolean enable) {
        c.setEnabled(enable);
        if (c instanceof Container) {
            for (int i = 0; i < ((Container) c).getComponentCount(); ++i) {
                enableAll(((Container) c).getComponent(i), enable);
            }
        }
    }

    @Override
    public void removeFrom(Buildable parent) {

        if (parent instanceof Map) {
            GameModule.getGameModule().removeCommandEncoder(this);
            map.getToolBar().remove(launchButton);
        }
    }

    /**
     * Command string is DOUBLE_BLIND:<player name>:<player ID>:<game password>
     * The DOUBLE_BLIND command is used to exchange player information and trigger an update of the DB view
     * @param c the command
     * @return the command string
     */
    public String encode(Command c) {

        if (c instanceof SynchCommand) {

            // push player information when I synch with my opponent
            debug("Encoding synch command");
            GameModule.getGameModule().sendAndLog(new DoubleBlindUpdateCommand(players.get(myPlayerName)));
        }

        if (c instanceof DoubleBlindUpdateCommand) {
            Player me = players.get(myPlayerName);
            String commandString =
                    COMMAND_PREFIX +
                            me.getName() +
                            COMMAND_SEPARATOR +
                            me.getID() +
                            COMMAND_SEPARATOR +
                            me.getGamePassword();
            debug("Encoded command string: " + commandString);
            return commandString;
        }
        else {
            return null;
        }
    }

    /**
     * Command string is DOUBLE_BLIND:<player name>:<player ID>:<game password>
     * @param s the command string
     * @return the DoubleBlindUpdateCommand
     */
    public Command decode(String s) {

        if (s.startsWith(COMMAND_PREFIX)) {

            debug("Decoded command string: " + s);
            // build the player object
            String strings[] = s.split(COMMAND_SEPARATOR);
            Player thePlayer = new Player(strings[1], strings[2], strings[3]);

            // add to the players list if necessary
            if(!players.containsKey(thePlayer.getName())) {
                players.put(thePlayer.getName(), thePlayer);
            }
            return new DoubleBlindUpdateCommand(thePlayer);
        }
        else {

            // push our player information when opponent synchronizes with me
            if (s.startsWith("SYNC")) {
                debug("Sending player info on synch");
                GameModule.getGameModule().sendAndLog(new DoubleBlindUpdateCommand(players.get(myPlayerName)));
            }
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
    public void setup(boolean gameStarting) {

        debug("Set up called");

        if(gameStarting) {

            // push player information
            GameModule.getGameModule().sendAndLog(new DoubleBlindUpdateCommand(players.get(myPlayerName)));

            // save preferences 'cause we disable them during DB play
            oldAutoReport   = (Boolean) getGameModule().getPrefs().getOption("centerOnMove").getValue();
            oldCenterOnMove = (Boolean) getGameModule().getPrefs().getOption("autoReport").getValue();

            //reclaim my pieces and update the view
            reclaimMyPieces();
            updateView();

        }
        else {

            // restore preference settings
            getGameModule().getPrefs().getOption("centerOnMove").setValue(oldCenterOnMove);
            getGameModule().getPrefs().getOption("autoReport").setValue(oldAutoReport);
        }
    }

    /**
     * Dumps a message to the local chatter and console for debugging
     * @param message the message
     */
    private void debug(String message) {
        // getGameModule().warn(message);
        System.out.println(message);
    }

    /**
     * Reclaims pieces I own that have been hidden by my opponent.
     */
    private void reclaimMyPieces() {

        /*
        When saving a game all pieces that are out of LOS to me will be hidden to me using my opponents game password.
        If the game is saved and reopened those pieces will be hidden from the opponent when he synchs the game.
         */

        // reclaim if I'm the owner and piece is not hidden with my ID
        GamePiece[] allPieces = map.getAllPieces();
        for (GamePiece piece : allPieces) {
            if (piece instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece p = pi.nextPiece();
                    if(isMyPiece(p) && p.getProperty(Properties.HIDDEN_BY) != null && !p.getProperty(Properties.HIDDEN_BY).equals(players.get(myPlayerName).getID())){
                        p.setProperty(Properties.HIDDEN_BY, null);
                        debug("Piece was reclaimed: " + p.getName());
                    }
                }
            } else {
                if(isMyPiece(piece) && !piece.getProperty(Properties.HIDDEN_BY).equals(players.get(myPlayerName).getID())){
                    piece.setProperty(Properties.HIDDEN_BY, null);
                    debug("Piece was reclaimed: " + piece.getName());
                }
            }
        }
        map.repaint();
    }

    @Override
    /**
     * Saves the player list
     */
    public Command getRestoreCommand() {
        debug("Creating restore command. Player count = " + players.size());
        Command c = new NullCommand();
        for (Player p: players.values()) {
            c = c.append(new DoubleBlindUpdateCommand(p));
        }
        return c;
    }

    /**
     * Configurer for the icon image
     */
    public static class IconConfig implements ConfigurerFactory {
        public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
            return new IconConfigurer(key, name, ICON_FILE_NAME);
        }
    }

    /**
     * Configurer for the chatter report formatter
     */
    public static class ReportFormatConfig implements TranslatableConfigurerFactory {
        public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
            return new PlayerIdFormattedStringConfigurer(key, name, new String[] {
                    ASLProperties.LOCATION,
                    Properties.MOVED});
        }
    }

    /**
     * Use this command to update the players' view of the map for DB
     */
    class DoubleBlindUpdateCommand extends Command {

        private Player player;

        public DoubleBlindUpdateCommand(Player player) {
            this.player = player;
        }

        protected void executeCommand() {

            debug("Executing DB command ");
            players.put(player.getName(), player);
            updateView();
        }

        protected Command myUndoCommand() {
            return null;
        }

        public int getValue() {
            return 0;
        }
    }

    /**
     * Contains player information needed for double blind
     */
    class Player {
        private String name;
        private String ID; // this is really the player password
        private String gamePassword;

        public Player(String name, String ID, String gamePassword) {
            this.name = name;
            this.ID = ID;
            this.gamePassword = gamePassword;
        }

        String getName() {
            return name;
        }

        String getID() {
            return ID;
        }

        String getGamePassword() {
            return gamePassword;
        }
    }
}


