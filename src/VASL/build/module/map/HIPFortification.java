package VASL.build.module.map;

import VASL.LOS.Map.LOSResult;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Terrain;
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
import VASSAL.build.module.map.Drawable;
import VASSAL.chat.SynchCommand;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.configure.*;
import VASSAL.counters.*;
import VASSAL.i18n.TranslatableConfigurerFactory;
import VASSAL.preferences.Prefs;
import VASSAL.tools.FormattedString;
import VASSAL.tools.NamedKeyStroke;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

import static VASSAL.build.GameModule.getGameModule;

/* This component identifies HIP Fortifications now in opponent's LOS
        */
public class HIPFortification  extends AbstractConfigurable implements CommandEncoder, GameComponent, Drawable {


    public static HIPFortification hipFortViewer;

    protected static final String COMMAND_SEPARATOR = ":";
    public static final String COMMAND_PREFIX = "HIP_FORT" + COMMAND_SEPARATOR;
    public static final String ENABLE_COMMAND_PREFIX = "ENABLE_HIP_FORT" + COMMAND_SEPARATOR;
    public static final String COMMAND_REVEAL = "HIP_REVEAL" +COMMAND_SEPARATOR;
    public static final String COMMAND_QUERY = "HIP_QUERY" + COMMAND_SEPARATOR;
    protected static final String TEXT_ICON = "HIPF Synch";
    protected static final String DEFAULT_PASSWORD = "#$none$#";
    protected static final String PLAYER_NAME = "RealName";

    // VASSAL attribute codes
    public static final String PROPERTY_TAB = "propertiesTab"; // properties tab name
    public static final String REPORT_FORMAT = "reportFormat"; // report updates?
    public static final String REPORT = "report";              // chatter string when HIPFort update reported

    // piece dynamic property constants
    protected static final String OWNER_PROPERTY = "Owner"; // contains the player name of the piece owner
    protected GamePiece revealpiece;
    protected GamePiece querypiece;
    protected GamePiece spotterpiece;

    protected List asktoreveal = new List();
    protected List thePlayer = new List();
    private boolean hipFortViewerActive = false;

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
    //private ASLMap m_objASLMap;

    protected String myPlayerName;
    protected VASL.LOS.VASLGameInterface VASLGameInterface;
    protected HashMap<String, HIPFortification.Player> players = new HashMap<String, HIPFortification.Player>(2); // list of all players in the game

    public HIPFortification() {

        hipFortViewer = this;
    }

    /**
     * Enable/disable FortHIP play
     * @param e enabled?
     */
    /*public void enableFortHIP(boolean e) {  DISABLED initially to force use of preference/restart

        if(enabled && !e) {
            getGameModule().getChatter().send("Auto-reveal of HIP Fortifications has been disabled for this game");
        }
        else if(!enabled && e) {
            getGameModule().getChatter().send("Auto-reveal of HIP Fortifcations has been enabled for this game");
        }

        enabled = e;

    }*/

    /**
     * @return true if FortHIP play has been enabled
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
     * Updates the player's view of the map, revealing pieces that are now in LOS
     */
    private void updateView(ArrayList<GamePiece> movedunits) {


        // gotta have a map to update the view
        if(map == null ||  map.getVASLMap() == null) {
            return;
        }
        VASLGameInterface = new VASLGameInterface(map, map.getVASLMap());
        VASLGameInterface.updatePieces();  //ensures all fort counters are present and accounted for
        // clear list of Pieces to reveal
        asktoreveal.removeAll();
        // select spotter
        spotterpiece = null;
        for (GamePiece testpiece : movedunits){
            if (testpiece instanceof Stack){
                for (PieceIterator pi = new PieceIterator(((Stack) testpiece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece nexttest = pi.nextPiece();
                    if (canReveal(nexttest)){
                        spotterpiece = nexttest;
                        break;
                    }
                }
            } else {
                if (canReveal(testpiece)){
                    spotterpiece = testpiece;
                    break;
                }
            };
        }

        if (spotterpiece != null) { // if no counter qualified to reveal units has been moved then return

            // reveal all HIP Fortification pieces now in LOS
            GamePiece[] allPieces = map.getPieces();
            for (GamePiece piece : allPieces) {
                if (piece instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack) piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                        testPieceVisibility(pi.nextPiece());
                    }
                } else {
                    testPieceVisibility(piece);
                }
            }
            GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationQueryCommand(thePlayer, asktoreveal, spotterpiece));
            //map.repaint();
        }
    }

    private boolean canReveal(GamePiece testpiece){
        // can the unit spot?
        if (!VASLGameInterface.isUnitCounter(testpiece)) {
            return false;
        }
        // spotter must be Good Order
        if (!isGoodOrder(testpiece)){
            return false;
        }
        return true;
    }

    private boolean isGoodOrder(GamePiece testpiece){
        // other conditions disqualify GoodOrder put can't be moved unit so no need to test
        if(testpiece.getName().contains("broken") || testpiece.getName().contains("Berserk") || testpiece.getName().contains("Prisoner")){
            return false;
        }
        return true;
    }
    /**
     * Hides a piece if it's out of LOS
     * @param piece the piece
     */
    public void testPieceVisibility(GamePiece piece) {

        // HIP pieces are los checked; ignore all others
        if(isHIP(piece)) {
            // opponent owns piece; ignore others
            if (!isMyPiece(piece)) {
                // check for LOS
                if (isViewable(piece)) {
                    setPieceSpotted(piece);
                    if (!piece.getName().equals("")) {
                        //debug("Piece " + piece.getName() + " was NOT Spotted");
                    }
                }
                else {
                    //setPieceUnspotted(piece);
                }
            }
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

        //if(!piece.getName().equals("")){
        if (!Decorator.getInnermost(piece).getName().equals("")){
            querypiece = piece;
            //HIPFortification.Player thePlayer = null;
            String hiddenBy = (String) piece.getProperty(Properties.HIDDEN_BY);
            for (HIPFortification.Player p : players.values()) {
                if(hiddenBy.equals(p.getID())) {
                    thePlayer.add(p.getID());
                    break;
                }
            }
            asktoreveal.add(querypiece.getId());
            //GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationQueryCommand(thePlayer, querypiece, spotterpiece));
        }

    }

    /**
     * Marks a piece as NOT spotted so it will NOT be drawn on the map
     * @param piece the piece
     */
    /*private void setPieceUnspotted(GamePiece piece) {

        // get the owner's game password or use default
        String owner = (String) piece.getProperty(OWNER_PROPERTY);
        String gamePassword = DEFAULT_PASSWORD;
        HIPFortification.Player opponent = players.get(owner);
        if(opponent != null) {
            gamePassword = opponent.getGamePassword();
        }

        // use owner's game password to hide the piece
        if(!piece.getName().equals("")){
            //debug("Setting piece UNspotted: " + piece.getName());
        }
        piece.setProperty(Properties.HIDDEN_BY, gamePassword);
    }*/

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
        for (HIPFortification.Player p : players.values()) {
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
     * @param piece the piece being viewed
     * @return true if piece1 can see piece2
     */
    public boolean isViewable(GamePiece piece) {

        // get the piece location
        Location l1 = getLocation(spotterpiece);
        Location l2 = getLocation(piece);
        // off board?
        if(l1 == null || l2 == null){
            return false;
        }
        // check the LOS
        LOSResult result = new LOSResult();
        map.getVASLMap().LOS(l1, false, l2, false, result, VASLGameInterface);
        int range = result.getRange();
        if(!result.isBlocked()) {
            if (range > 16 && isConcealmentTerrain(l2.getTerrain())){
                return false; // can't be seen
            }
        } else {
            return false; // can't be seen
        }
        return true;
    }

    private boolean isConcealmentTerrain(Terrain checkterrain){
        if (checkterrain.isBuildingTerrain() || checkterrain.getName().equals("Woods") ||
                checkterrain.getName().equals("Forest") || checkterrain.getName().equals("PineWoods") ||
                checkterrain.getName().equals("Brush") || checkterrain.getName().equals("Light Woods") || checkterrain.getName().equals("Bamboo") ||
                checkterrain.getName().equals("Vineyard") || checkterrain.getName().equals("PFZ Vineyard") ||
                checkterrain.getName().equals("Orchard") || checkterrain.getName().equals("Palm Trees") || checkterrain.getName().equals("Rice Paddy, In Season") ||
                checkterrain.getName().equals("Cactus Patch") || checkterrain.getName().equals("Broken Ground") ||
                checkterrain.getName().equals("Olive Grove") || checkterrain.getName().equals("Orchard, Out of Season") ||
                checkterrain.getName().equals("Grain") || checkterrain.getName().equals("Kunai") ||
                checkterrain.getName().equals("Marsh") || checkterrain.getName().equals("Palm Debris") ||
                checkterrain.getName().equals("Wooden Rubble") || checkterrain.getName().equals("Stone Rubble") ||
                checkterrain.getName().equals("Light Jungle") || checkterrain.getName().equals("Dense Jungle") ||
                checkterrain.getName().equals("Bocage") || checkterrain.getName().equals("Scrub") ||
                checkterrain.getName().equals("Swamp") ) {
            return true;
        }
        return false;
    }

    /**
     * Finds the location for the given piece
     * @param piece the piece
     * @return the location - null if error or none
     */
    protected Location getLocation(GamePiece piece) {

        // get the VASL interface if necessary
        if(VASLGameInterface == null){
            VASLGameInterface = new VASLGameInterface(map, map.getVASLMap());
            VASLGameInterface.updatePieces();  //ensures all fort counters are present and accounted for
        }

        return VASLGameInterface.getLocation(piece);
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{
                String.class,
                Boolean.class,
                HIPFortification.ReportFormatConfig.class,
                String.class,
                String.class,
                HIPFortification.IconConfig.class,
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
            return "";  //reportFormat.getText();
        }
        else {
            return null; //launchButton.getAttributeValueString(key);
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
            //launchButton.setAttribute(key, value);
        }
    }

    public void addTo(Buildable parent) {

        final Prefs prefs = GameModule.getGameModule().getPrefs();
        BooleanConfigurer HIPFORTVIEWERACTIVE = (BooleanConfigurer) prefs.getOption("useautoreveal");

        if (HIPFORTVIEWERACTIVE == null) {
            HIPFORTVIEWERACTIVE = new BooleanConfigurer("useautoreveal", "Auto-Reveal HIP Fortifications", Boolean.TRUE);  //$NON-NLS-1$
            prefs.addOption("VASL", HIPFORTVIEWERACTIVE); //$NON-NLS-1$
        }
        hipFortViewerActive = (Boolean) prefs.getValue("useautoreveal");
        HIPFORTVIEWERACTIVE.addPropertyChangeListener(e -> hipFortViewerActive = (Boolean) e.getNewValue());
        readHipFortViewerActive();

        // add this component to the game and register a mouse listener
        if (parent instanceof ASLMap) {
            map = (ASLMap) parent;
            map.addDrawComponent(this);
        }


        GameModule.getGameModule().getGameState().addGameComponent(this);

        // add this component to the map toolbar
        if (parent instanceof Map) {
            setMap((ASLMap) parent);
            // map.getToolBar().add(launchButton);
            GameModule.getGameModule().addCommandEncoder(this);
        }

        // record the player information
        myPlayerName = (String) getGameModule().getPrefs().getValue(PLAYER_NAME);
        players.put(myPlayerName, new HIPFortification.Player(myPlayerName, GameModule.getUserId(), GameModule.getUserId())); // + "-HF"));
        GameModule.getGameModule().getGameState().addGameComponent(this);
    }

    public void removeFrom(Buildable parent) {

        if (parent instanceof Map) {
            GameModule.getGameModule().removeCommandEncoder(this);
            //map.getToolBar().remove(launchButton);
        }
    }
    public void readHipFortViewerActive()
    {
        String AUTO_REVEAL = "useautoreveal";
        Boolean HIPFPref = (Boolean)GameModule.getGameModule().getPrefs().getValue(AUTO_REVEAL);
        if (HIPFPref == null) {
            HIPFPref = false;
        }
        hipFortViewerActive = HIPFPref;
        enabled = hipFortViewerActive;
    }

    /**
     * The HIP_FORT command is used to exchange player information and trigger an update of the view
     * The ENABLE_HIP_FORT command marks the game as using auto-reveal
     * @param c the command
     * @return the command string
     */
    public String encode(Command c) {

        if (c instanceof SynchCommand) {

            // push player information when I synch with my opponent
            GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationUpdateCommand(players.get(myPlayerName)));
        }

        // Command string is HIP_FORT:<player name>:<player ID>:<game password>
        if (c instanceof HIPFortification.HIPFortificationUpdateCommand) {
            HIPFortification.Player me = players.get(myPlayerName);
            String commandString =
                    COMMAND_PREFIX +
                            me.getName() +
                            COMMAND_SEPARATOR +
                            me.getID() +
                            COMMAND_SEPARATOR +
                            me.getGamePassword();
            return commandString;
        }
        if (c instanceof HIPFortification.HIPFortificationQueryCommand){
            HIPFortification.HIPFortificationQueryCommand hipqc = (HIPFortification.HIPFortificationQueryCommand) c;
            for (String queryid : hipqc.asktoreveallist){

            }
            return LINK_COMMAND_PREFIX + lpc.getFromPieceID() + "," + lpc.getToPieceID();
            String commandString = COMMAND_QUERY + querypiece.getProperty("Owner") + COMMAND_SEPARATOR + querypiece.getId() + COMMAND_SEPARATOR + spotterpiece.getId();
            return commandString;
        }
        if (c instanceof HIPFortification.HIPFortificationRevealCommand){
            String commandString = COMMAND_REVEAL + revealpiece.getId() + COMMAND_SEPARATOR + spotterpiece.getId();
            return commandString;
        }
        return null;
    }

    /**
     * @param s the command string
     * @return the HIPFortificationUpdateCommand
     */
    public Command decode(String s) {

        // Command string is ENABLE_HIP_FORT:<enable>
        /*if (s.startsWith(ENABLE_COMMAND_PREFIX)) {  DISABLED initially to force use of preference/restart
            debug("Decoded enable HIPF command string: " + s);
            String strings[] = s.split(COMMAND_SEPARATOR);
            return new EnableHIPFortCommand(this, Boolean.valueOf(strings[1]));
        }*/

        // Command string is HIP_FORT:<player name>:<player ID>:<game password>
        if (s.startsWith(COMMAND_PREFIX)) {

            // debug("Decoded command string: " + s);
            // build the player object
            String strings[] = s.split(COMMAND_SEPARATOR);
            HIPFortification.Player thePlayer = new HIPFortification.Player(strings[1], strings[2], strings[3]);

            // add to the players list if necessary
            if(!players.containsKey(thePlayer.getName())) {
                players.put(thePlayer.getName(), thePlayer);
            }
            return new HIPFortification.HIPFortificationUpdateCommand(thePlayer);
        }
        else if (s.startsWith(COMMAND_QUERY)){
            GamePiece thepiece = null; GamePiece thespotter = null;
            String strings[] = s.split(COMMAND_SEPARATOR);
            HIPFortification.Player theplayer = (players.get(strings[1]));
            GamePiece[] allPieces = map.getPieces();
            for (GamePiece p : allPieces) {
                if (p instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator()); pi.hasMoreElements(); ) {
                        GamePiece p2 = pi.nextPiece();
                        if (p2.getId().equals(strings[2])) {
                            thepiece = p2;
                        }
                        else if (p2.getId().equals(strings[3])){
                            thespotter = p2;
                        }
                    }
                }
                else {
                    if (p.getId().equals(strings[2])) {
                        thepiece = p;
                    }
                    else if (p.getId().equals(strings[3])){
                        thespotter=p;
                    }

                }
            }
            return new HIPFortification.HIPFortificationQueryCommand(theplayer, thepiece, thespotter);
        }
        else if (s.startsWith(COMMAND_REVEAL)){
            GamePiece thePiece = null;
            String strings[] = s.split(COMMAND_SEPARATOR);
            GamePiece[] allPieces = map.getAllPieces();
            for (GamePiece p : allPieces) {
                if (p instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator()); pi.hasMoreElements(); ) {
                        GamePiece p2 = pi.nextPiece();
                        if (p2.getId().equals(strings[1])) {
                            thePiece = p2;
                        }
                    }
                }
                else {
                    if (p.getId().equals(strings[1])) {
                        thePiece = p;
                    }
                }
                if (p instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator()); pi.hasMoreElements(); ) {
                        GamePiece p2 = pi.nextPiece();
                        if (p2.getId().equals(strings[2])) {
                            spotterpiece = p2;
                        }
                    }
                }
                else {
                    if (p.getId().equals(strings[2])) {
                        spotterpiece = p;
                    }
                }
            }
            return new HIPFortification.HIPFortificationRevealCommand(thePiece, spotterpiece);
        }
        else {

            // push our player information when opponent synchronizes with me
            if (s.startsWith("SYNC")) {
                GameModule.getGameModule().getChatter().send(myPlayerName + " is using Auto-Reveal of HIP Fortifications. Both players should have it enabled in Preferences/VASL");
                GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationUpdateCommand(players.get(myPlayerName)));
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
    public void runupdate(java.util.List<GamePiece> allDraggedPieces){
        spotterpiece = null;
        revealpiece = null;
        querypiece = null;
        ArrayList movedunits = (ArrayList) allDraggedPieces;
        updateView(movedunits);
    }

    public void setup(boolean gameStarting) {

        if(enabled) {

            if(gameStarting) {

                // push player information
                GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationUpdateCommand(players.get(myPlayerName)));

                // save preferences 'cause we disable them during DB play
                oldAutoReport   = (Boolean) getGameModule().getPrefs().getOption("centerOnMove").getValue();
                oldCenterOnMove = (Boolean) getGameModule().getPrefs().getOption("autoReport").getValue();
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


    /**
     * Saves the player list and HIPFortification state
     */
    public Command getRestoreCommand() {

        //debug("Creating restore command for HIP Fortification - " + enabled + ". Player count = " + players.size());
        Command c = new NullCommand();
        /*if(enabled) {

            for (HIPFortification.Player p: players.values()) {
                c = c.append(new HIPFortification.HIPFortificationUpdateCommand(p));
            }
            //c = c.append(new EnableHIPFortCommand(this, enabled)); DISABLED initially to force use of preference/restart
        }*/
        return c;
    }

    @Override
    public void draw(Graphics graphics, Map map) {

    }

    @Override
    public boolean drawAboveCounters() {
        return false;
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
     * Use this command to update the players
     */
    class HIPFortificationUpdateCommand extends Command {

        private HIPFortification.Player player;

        public HIPFortificationUpdateCommand(HIPFortification.Player player) {
            this.player = player;
        }

        protected void executeCommand() {

            //debug("Executing HIP Fortification update command ");
            players.put(player.getName(), player);
            //updateView();
        }

        protected Command myUndoCommand() {
            return null;
        }

        public int getValue() {
            return 0;
        }
    }
    class HIPFortificationRevealCommand extends Command {

        private GamePiece thepiece;
        private GamePiece theSpotter;

        public HIPFortificationRevealCommand(GamePiece piece, GamePiece spotter) {
            revealpiece = piece;
            spotterpiece = spotter;
        }

        protected void executeCommand() {

            revealpiece.setProperty(Properties.HIDDEN_BY, null);
            GameModule.getGameModule().getChatter().send(revealpiece.getName() + " spotted by " + spotterpiece.getName() + " and revealed in " + map.locationName(revealpiece.getPosition()));
            map.repaint();
        }

        protected Command myUndoCommand() {
            return null;
        }

        public int getValue() {
            return 0;
        }
    }
    class HIPFortificationQueryCommand extends Command {

        protected List playerlist;
        protected List asktoreveallist;

        public HIPFortificationQueryCommand(List thePlayer, List asktoreveal, GamePiece spotter) {

            List playerlist = thePlayer;
            List asktoreveallist = asktoreveal;
            spotterpiece = spotter;
        }

        protected void executeCommand() {
            if(revealpiece != null && spotterpiece != null) {
                if (myPlayerName.equals(player.getName())) {
                    int dialogResult = -100;
                    Location l2 = getLocation(revealpiece);
                    do {
                        dialogResult = JOptionPane.showConfirmDialog(null, "Auto-Detection has found a HIP Fortification (" + Decorator.getInnermost(revealpiece).getName() + " in " + l2.getName() + ") now in LOS. Reveal?",
                                "Using Auto-Reveal of HIP Fortifications . . . ", JOptionPane.YES_NO_OPTION);

                    } while (dialogResult == -100);
                    if (dialogResult == JOptionPane.YES_OPTION) {
                        revealpiece.setProperty(Properties.HIDDEN_BY, null);
                        GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationRevealCommand(revealpiece, spotterpiece));
                    }
                }
            }
        }

        protected Command myUndoCommand() {
            return null;
        }

        public int getValue() {
            return 0;
        }
    }
    /**
     * Contains player information needed for HIP Fortification reveal
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
