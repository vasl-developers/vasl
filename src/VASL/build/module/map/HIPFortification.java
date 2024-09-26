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

    public static final String COMMAND_REVEAL = "HIP_REVEAL";
    public static final String COMMAND_QUERY = "HIP_QUERY";
    protected static final String TEXT_ICON = "HIPF Synch";

    protected static final String PLAYER_NAME = "RealName";

    // VASSAL attribute codes
    public static final String PROPERTY_TAB = "propertiesTab"; // properties tab name
    public static final String REPORT_FORMAT = "reportFormat"; // report updates?
    public static final String REPORT = "report";              // chatter string when HIPFort update reported

    protected GamePiece revealpiece;
    protected GamePiece querypiece;
    protected GamePiece spotterpiece;

    protected List asktoreveal = new List();
    protected List thePlayer = new List();
    protected List usespotter = new List();
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
        // clear lists used to manage reveal
        thePlayer.removeAll();
        asktoreveal.removeAll();
        usespotter.removeAll();
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
            }
        }

        if (spotterpiece != null) { // if no counter qualified to reveal units has been moved then return

            // ask to reveal all HIP Fortification pieces now in LOS
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
            GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationQueryCommand(thePlayer, asktoreveal, usespotter));
            //map.repaint();
        }
    }

    private boolean canReveal(GamePiece testpiece){
        // can the unit spot?
        // let ? stacks trigger reveal question
        if (testpiece.getName().contains("?")) {return true;}
        // non units can't trigger
        if (!VASLGameInterface.isUnitCounter(testpiece)) {
            return false;
        }
        // spotter must be Good Order
        return isGoodOrder(testpiece);
    }

    private boolean isGoodOrder(GamePiece testpiece){
        // other conditions disqualify GoodOrder but can't be moved unit so no need to test
        return !testpiece.getName().contains("broken") && !testpiece.getName().contains("Berserk") && !testpiece.getName().contains("Prisoner");
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
                }
            }
        }
    }

    /**
     * @param piece the piece
     * @return true if I own the piece
     */
    private boolean isMyPiece(GamePiece piece) {

        return  piece.getProperty(Properties.HIDDEN_BY) != null && piece.getProperty(Properties.HIDDEN_BY).equals(myPlayerName);
    }

    /**
     * Marks a piece as spotted so it will be drawn on the map
     * @param piece the piece
     */
    private void setPieceSpotted(GamePiece piece) {

       if (!Decorator.getInnermost(piece).getName().isEmpty()){
            querypiece = piece;
            //HIPFortification.Player thePlayer = null;
            String hiddenBy = (String) piece.getProperty(Properties.HIDDEN_BY);
            for (HIPFortification.Player p : players.values()) {
                if(hiddenBy.equals(p.getID())) {
                    thePlayer.add(p.getName());
                    break;
                }
            }
            asktoreveal.add(querypiece.getId());
            usespotter.add(spotterpiece.getId());

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
        if (l1 == null || l2 == null) {
            return false;
        }
        // check the LOS
        LOSResult result = new LOSResult();
        map.getVASLMap().LOS(l1, false, l2, false, result, VASLGameInterface);
        int range = result.getRange();
        if (!result.isBlocked()) {
            if (range > 16 && isConcealmentTerrain(l2.getTerrain())) {
                return false; // can't be seen
            }
        } else {
            return false; // can't be seen
        }
        // only reveal if fortification
        if (!isFortification(piece)) {
            return false;
        }
        return true;
    }

    private boolean isFortification(GamePiece piece){
        if (Decorator.getInnermost(piece).getName().contains("Foxhole") || Decorator.getInnermost(piece).getName().contains("Trench") || Decorator.getInnermost(piece).getName().contains("Wire") || piece.getName().contains("Foxhole") ||
                Decorator.getInnermost(piece).getName().contains("Ditch") || Decorator.getInnermost(piece).getName().contains("PFZ") || Decorator.getInnermost(piece).getName().contains("Sangar")
                || Decorator.getInnermost(piece).getName().contains("Pillbox")) {
            return true;
        }
        return false;
    }
    private boolean isConcealmentTerrain(Terrain checkterrain){
        return checkterrain.isBuildingTerrain() || checkterrain.getName().equals("Woods") ||
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
                checkterrain.getName().equals("Swamp");
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
    protected GamePiece getPiece(String revealid){
        GamePiece[] allPieces = map.getPieces();
        for (GamePiece p : allPieces) {
            if (p instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece p2 = pi.nextPiece();
                    if (p2.getId().equals(revealid)) {
                        return p2;
                    }
                }
            } else {
                if (p.getId().equals(revealid)) {
                    return p;
                }
            }
        }
        return null;
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
        } else if (REPORT.equals(key)) {
            if (value instanceof String) {
                report = Boolean.parseBoolean((String) value);
            }
        } else if (REPORT_FORMAT.equals(key)) {
            if (value instanceof String) {
                reportFormat.setFormat((String) value);
            }
        }
    }
    public void addTo(Buildable parent) {

        final Prefs prefs = GameModule.getGameModule().getPrefs();
        BooleanConfigurer HIPFORTVIEWERACTIVE = (BooleanConfigurer) prefs.getOption("useautoreveal");

        if (HIPFORTVIEWERACTIVE == null) {
            HIPFORTVIEWERACTIVE = new BooleanConfigurer("useautoreveal", "Auto-Reveal HIP Fortifications (VASL restart required)", Boolean.TRUE);  //$NON-NLS-1$
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

        // add this component to the map toolbar  ToDo IS THIS NEEDED?
        if (parent instanceof Map) {
            assert parent instanceof ASLMap;
            setMap((ASLMap) parent);
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
            String commandString = COMMAND_QUERY;
            for (int i=0; i < hipqc.asktoreveallist.getItemCount(); i++){
                String askitem = hipqc.asktoreveallist.getItem(i);
                String owneritem = hipqc.ownerlist.getItem(i);
                String spotteritem = hipqc.spotterlist.getItem(i);
                commandString = commandString + COMMAND_SEPARATOR + owneritem + COMMAND_SEPARATOR + askitem + COMMAND_SEPARATOR + spotteritem;
            }
            return commandString;
        }
        if (c instanceof HIPFortification.HIPFortificationRevealCommand){
            //String commandString = COMMAND_REVEAL + revealpiece.getId() + COMMAND_SEPARATOR + spotterpiece.getId();
            HIPFortification.HIPFortificationRevealCommand hiprc = (HIPFortification.HIPFortificationRevealCommand) c;
            String commandString = COMMAND_REVEAL;
            for (int i=0; i < hiprc.asktoreveallist.getItemCount(); i++){
                String askitem = hiprc.asktoreveallist.getItem(i);
                String owneritem = hiprc.ownerlist.getItem(i);
                String spotteritem = hiprc.spotterlist.getItem(i);
                commandString = commandString + COMMAND_SEPARATOR + owneritem + COMMAND_SEPARATOR + askitem + COMMAND_SEPARATOR + spotteritem;
            }
            return commandString;
        }
        return null;
    }

    /**
     * @param s the command string
     * @return the HIPFortificationUpdateCommand
     */
    public Command decode(String s) {

        // Command string is HIP_FORT:<player name>:<player ID>:<game password>
        if (s.startsWith(COMMAND_PREFIX)) {

            // debug("Decoded command string: " + s);
            // build the player object
            String[] strings = s.split(COMMAND_SEPARATOR);
            HIPFortification.Player thePlayer = new HIPFortification.Player(strings[1], strings[2], strings[3]);

            // add to the players list if necessary
            if(!players.containsKey(thePlayer.getName())) {
                players.put(thePlayer.getName(), thePlayer);
            }
            return new HIPFortification.HIPFortificationUpdateCommand(thePlayer);
        }
        else if (s.startsWith(COMMAND_QUERY)){
            List ownerlist = new List(); List querylist = new List(); List spotterlist = new List();
            String[] strings = s.split(COMMAND_SEPARATOR);
            int queryno = (strings.length -1) /3;
            for (int i = 0; i < queryno; i++ ) {
                ownerlist.add(strings[(i*3) + 1]);
                querylist.add(strings[(i*3) + 2]);
                spotterlist.add(strings[(i*3) + 3]);
            }
            return new HIPFortification.HIPFortificationQueryCommand(ownerlist, querylist , spotterlist);

        }
        else if (s.startsWith(COMMAND_REVEAL)){
            List ownerlist = new List(); List querylist = new List(); List spotterlist = new List();
            String[] strings = s.split(COMMAND_SEPARATOR);
            int queryno = (strings.length -1) /3;
            for (int i = 0; i < queryno ; i++ ) {
                ownerlist.add(strings[(i*3) + 1]);
                querylist.add(strings[(i*3) + 2]);
                spotterlist.add(strings[(i*3) + 3]);
            }
            return new HIPFortification.HIPFortificationRevealCommand(ownerlist, querylist , spotterlist);
        }
        else {
            // push our player information when opponent synchronizes with me
            if (s.startsWith("SYNC")) {
                if (enabled) {
                    GameModule.getGameModule().getChatter().send(myPlayerName + " is using Auto-Reveal of HIP Fortifications. Both players should have it enabled in Preferences/VASL");
                    GameModule.getGameModule().sendAndLog(new HIPFortification.HIPFortificationUpdateCommand(players.get(myPlayerName)));
                }
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

    }

    /**
     * Saves the player list and HIPFortification state
     */
    public Command getRestoreCommand() {
        return new NullCommand();
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

        private final HIPFortification.Player player;

        public HIPFortificationUpdateCommand(HIPFortification.Player player) {
            this.player = player;
        }

        protected void executeCommand() {
            players.put(player.getName(), player);
        }

        protected Command myUndoCommand() {
            return null;
        }

        public int getValue() {
            return 0;
        }
    }
    class HIPFortificationRevealCommand extends Command {
        protected List ownerlist;
        protected List asktoreveallist;
        protected List spotterlist;
        public HIPFortificationRevealCommand(List thePlayer, List asktoreveal, List usespotter) {
            this.ownerlist = thePlayer;
            this.asktoreveallist = asktoreveal;
            this.spotterlist = usespotter;
        }
        protected void executeCommand() {
            for (int i = 0; i < this.ownerlist.getItemCount(); i++){
                if (this.ownerlist.getItem(i) != null) {
                    GamePiece revealpiece = getPiece(this.asktoreveallist.getItem(i));
                    GamePiece spotterpiece = getPiece(this.spotterlist.getItem(i));
                    revealpiece.setProperty(Properties.HIDDEN_BY, null);
                    //GameModule.getGameModule().getChatter().send(revealpiece.getName() + " spotted by " + spotterpiece.getName() + " and revealed in " + map.locationName(revealpiece.getPosition()));
                    map.repaint();
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
    class HIPFortificationQueryCommand extends Command {
        protected List ownerlist;
        protected List asktoreveallist;
        protected List spotterlist;

        public HIPFortificationQueryCommand(List thePlayer, List asktoreveal, List usespotter) {

            this.ownerlist = thePlayer;
            this.asktoreveallist = asktoreveal;
            this.spotterlist = usespotter;
        }

        protected void executeCommand() {
            if (!enabled){return;}
            String ownername = null; String spottername = null;
            List keepHIP = new List();
            for (int i = 0; i < this.ownerlist.getItemCount(); i++){
                if (this.ownerlist.getItem(i) != null) {
                    String playertoget = ownerlist.getItem(i);
                    HIPFortification.Player owner = players.get(playertoget);
                    if (owner == null){continue;}
                    ownername = owner.getName();
                    if (myPlayerName.equals(ownername)) {
                        int dialogResult = -100;
                        GamePiece revealpiece = getPiece(this.asktoreveallist.getItem(i));
                        GamePiece spotterpiece = getPiece(this.spotterlist.getItem(i));
                        if (spotterpiece == null) {return;}
                        spottername = spotterpiece.getName();
                        Location l2 = getLocation(revealpiece);
                        do {
                            dialogResult = JOptionPane.showConfirmDialog(null, "Auto-Detection has found a HIP Fortification (" + Decorator.getInnermost(revealpiece).getName() + " in " + l2.getName() + ") now in LOS of " + spottername + ". Reveal?",
                                    "Using Auto-Reveal of HIP Fortifications . . . ", JOptionPane.YES_NO_OPTION);

                        } while (dialogResult == -100);
                        if (dialogResult == JOptionPane.NO_OPTION) {
                            keepHIP.add(this.asktoreveallist.getItem(i));
                        } else {
                            String showname = (revealpiece.getName().contains("?") ? "?" : revealpiece.getName());
                            GameModule.getGameModule().getChatter().send(showname + " spotted by " + spottername + " and revealed in " + map.locationName(revealpiece.getPosition()));
                        }
                    }
                    else {
                        keepHIP.add(this.asktoreveallist.getItem(i));
                    }
                }
            }
            if (keepHIP.getItemCount() > 0) {
                for (int i = 0; i < keepHIP.getItemCount(); i++){
                    for (int y = 0; y < this.asktoreveallist.getItemCount(); y++) {
                        if (this.asktoreveallist.getItem(y).equals(keepHIP.getItem(i))) {
                            this.asktoreveallist.remove(y);
                            this.ownerlist.remove(y);
                            this.spotterlist.remove(y);
                            break;
                        }
                    }
                }
            }
            HIPFortification.HIPFortificationRevealCommand hipreveal = new HIPFortification.HIPFortificationRevealCommand(this.ownerlist, this.asktoreveallist, this.spotterlist);
            GameModule.getGameModule().sendAndLog(hipreveal);
            hipreveal.executeCommand();
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
        private final String name;
        private final String ID; // this is really the player password
        private final String gamePassword;

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
