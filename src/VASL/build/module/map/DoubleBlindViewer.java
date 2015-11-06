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
import VASL.build.module.map.EnableDoubleBlindCommand;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import static VASSAL.build.GameModule.getGameModule;

/**
 * This component filters the players view of the board so only units in the player's LOS are shown.
 */
public class DoubleBlindViewer extends AbstractConfigurable implements CommandEncoder, GameComponent {

    // todo - this is a hack to allow the board picker to find the DB viewer
    public static DoubleBlindViewer doubleBlindViewer;

    protected static final String COMMAND_SEPARATOR = ":";
    public static final String COMMAND_PREFIX = "DOUBLE_BLIND" + COMMAND_SEPARATOR;
    public static final String ENABLE_COMMAND_PREFIX = "ENABLE_DOUBLE_BLIND" + COMMAND_SEPARATOR;
    protected static final String TEXT_ICON = "DB Synch";
    protected static final String DEFAULT_PASSWORD = "#$none$#";
    protected static final String PLAYER_NAME = "RealName";

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
    protected static boolean enabled = false;
    protected static ASLMap map;
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
        launchButton.setAttribute(TOOLTIP, "Update DB View");
        launchButton.setEnabled(false); // button inactive unless DB explicitly enabled.

        doubleBlindViewer = this;
    }

    /**
     * Enable/disable DB play
     * @param e enabled?
     */
    public void enableDB(boolean e) {

        if(enabled && !e) {
            getGameModule().getChatter().send("Double blind play has been disabled for this game");
        }
        else if(!enabled && e) {
            getGameModule().getChatter().send("Double blind play has been enabled for this game");
        }

        enabled = e;
        launchButton.setEnabled(e);
    }

    /**
     * @return true if DB play has been enabled
     */
    public boolean isEnabled() {
        return enabled;
    }


    /**
     * Update the map
      * @param m the map
     */
    public static void setMap(ASLMap m) {
        map = m;
    }

    /**
     * Sends the command and updates the view when the button or button hot key is pressed
     */
    public void buttonAction() {

        // warn only if the DB preference is turned off
        if(!enabled) {
            getGameModule().getChatter().send("Double blind is not enabled on this game");
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


        // gotta have a map to update the view
        if(map == null ||  map.getVASLMap() == null) {
            return;
        }

        VASLGameInterface = new VASLGameInterface(map, map.getVASLMap());
        VASLGameInterface.updatePieces();

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

        // HIP pieces never touched
        if(isHIP(piece)) {
            return;
        }
        // global pieces and unowned pieces are always visible
        if(VASLGameInterface.isDBGlobalCounter(piece) || (piece.getProperty(OWNER_PROPERTY) == null)){
            setPieceSpotted(piece);
            return;
        }

        // player owns piece?
        if(!isMyPiece(piece)) {

            // step through all pieces owned by me and check for LOS
            GamePiece[] allPieces = map.getAllPieces();
            for (GamePiece p : allPieces) {
                if (p instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator()); pi.hasMoreElements(); ) {
                        GamePiece p2 = pi.nextPiece();
                        if (isMyPiece(p2)) {

                            if (isViewable(p2, piece)) {
                                setPieceSpotted(piece);
                                if(!piece.getName().equals("")){
                                    debug("Piece " + piece.getName() + " was Spotted by " + p2.getName());
                                }
                                return;
                            }
                        }
                    }
                } else {

                    if (isViewable(p, piece)) {
                        setPieceSpotted(piece);
                        if(!piece.getName().equals("")){
                            debug("Piece " + piece.getName() + " was Spotted by " + p.getName());
                        }
                        return;
                    }
                }
            }

            if(!piece.getName().equals("")){
                debug("Piece " + piece.getName() + " was NOT Spotted");
            }
            setPieceUnspotted(piece);
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
     * Marks a piece as spotted so it will be drawn on the map
     * @param piece the piece
     */
    private void setPieceSpotted(GamePiece piece) {

        if(!piece.getName().equals("")){
            debug("Setting piece spotted: " + piece.getName());
        }
        piece.setProperty(Properties.HIDDEN_BY, null);
    }

    /**
     * Marks a piece as NOT spotted so it will NOT be drawn on the map
     * @param piece the piece
     */
    private void setPieceUnspotted(GamePiece piece) {

        // get the owner's game password or use default
        String owner = (String) piece.getProperty(OWNER_PROPERTY);
        String gamePassword = DEFAULT_PASSWORD;
        Player opponent = players.get(owner);
        if(opponent != null) {
            gamePassword = opponent.getGamePassword();
        }

        // use owner's game password to hide the piece
        if(!piece.getName().equals("")){
            debug("Setting piece UNspotted: " + piece.getName());
        }
        piece.setProperty(Properties.HIDDEN_BY, gamePassword);
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

        // not HIP if hidden by me
        for (Player p : players.values()) {
            if(hiddenBy.equals(p.getID())) {
                return true;
            }
        }
        // must not be HIP
        return false;
    }

    /**
     * Can piece1 see piece2?
     * Sub-classes could override this method to implement custom sighting rules
     * @param piece1 the viewing game piece
     * @param piece2 the piece being viewed
     * @return true if piece1 can see piece2
     */
    public boolean isViewable(GamePiece piece1, GamePiece piece2) {

        // can the unit spot?
        if (!VASLGameInterface.isDBUnitCounter(piece1)) {
            return false;
        }

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

    public void addTo(Buildable parent) {

        // add this component to the map toolbar
        if (parent instanceof Map) {
            setMap((ASLMap) parent);
            // map.getToolBar().add(launchButton);
            GameModule.getGameModule().addCommandEncoder(this);
        }

        // record the player information
        myPlayerName = (String) getGameModule().getPrefs().getValue(PLAYER_NAME);
        players.put(myPlayerName, new Player(myPlayerName, GameModule.getUserId(), GameModule.getUserId() + "-DB"));
        GameModule.getGameModule().getGameState().addGameComponent(this);
    }

    public void removeFrom(Buildable parent) {

        if (parent instanceof Map) {
            GameModule.getGameModule().removeCommandEncoder(this);
            map.getToolBar().remove(launchButton);
        }
    }

    /**
     * The DOUBLE_BLIND command is used to exchange player information and trigger an update of the DB view
     * The ENABLE_DOUBLE_BLIND command marks the game as a DB game
     * @param c the command
     * @return the command string
     */
    public String encode(Command c) {

        if (c instanceof SynchCommand) {

            // push player information when I synch with my opponent
            debug("Encoding synch command");
            GameModule.getGameModule().sendAndLog(new DoubleBlindUpdateCommand(players.get(myPlayerName)));
        }

        // Command string is ENABLE_DOUBLE_BLIND:<enable>
        if(c instanceof  EnableDoubleBlindCommand) {

            debug("Encoded enable DB command string");
            return  ENABLE_COMMAND_PREFIX + Boolean.toString(enabled);
        }

        // Command string is DOUBLE_BLIND:<player name>:<player ID>:<game password>
        if (c instanceof DoubleBlindUpdateCommand) {
            Player me = players.get(myPlayerName);
            String commandString =
                    COMMAND_PREFIX +
                            me.getName() +
                            COMMAND_SEPARATOR +
                            me.getID() +
                            COMMAND_SEPARATOR +
                            me.getGamePassword();
            debug("Encoded command string");
            return commandString;
        }
        else {
            return null;
        }
    }

    /**
     * @param s the command string
     * @return the DoubleBlindUpdateCommand
     */
    public Command decode(String s) {

        // Command string is ENABLE_DOUBLE_BLIND:<enable>
        if (s.startsWith(ENABLE_COMMAND_PREFIX)) {
            debug("Decoded enable DB command string: " + s);
            String strings[] = s.split(COMMAND_SEPARATOR);
            return new EnableDoubleBlindCommand(this, Boolean.valueOf(strings[1]));
        }

        // Command string is DOUBLE_BLIND:<player name>:<player ID>:<game password>
        if (s.startsWith(COMMAND_PREFIX)) {

            // debug("Decoded command string: " + s);
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

    public HelpFile getHelpFile() {
        return null;
    }

    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }

    public void setup(boolean gameStarting) {

        debug("Set up called - starting " + gameStarting);

        if(enabled){

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

                enabled = false;
            }
        }
    }

    /**
     * Dumps a message to the local chatter and console for debugging
     * @param message the message
     */
    private void debug(String message) {
        // getGameModule().warn(message);
        // System.out.println(message);
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
                        if(!p.getName().equals("")){
                            debug("Piece was reclaimed: " + p.getName());
                        }
                    }
                }
            } else {
                if(isMyPiece(piece) && piece.getProperty(Properties.HIDDEN_BY) != null && !piece.getProperty(Properties.HIDDEN_BY).equals(players.get(myPlayerName).getID())){
                    piece.setProperty(Properties.HIDDEN_BY, null);
                    if(!piece.getName().equals("")){
                        debug("Piece was reclaimed: " + piece.getName());
                    }
                }
            }
        }
        map.repaint();
    }

    /**
     * Saves the player list and DB state
     */
    public Command getRestoreCommand() {

        debug("Creating restore command for DB - " + enabled + ". Player count = " + players.size());
        Command c = new NullCommand();
        if(enabled) {

            for (Player p: players.values()) {
                c = c.append(new DoubleBlindUpdateCommand(p));
            }
            c = c.append(new EnableDoubleBlindCommand(this, enabled));
        }
        return c;
    }

    /**
     * Configurer for the icon image
     */
    public static class IconConfig implements ConfigurerFactory {
        public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
            return new IconConfigurer(key, name, "");
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

            debug("Executing DB update command ");
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


