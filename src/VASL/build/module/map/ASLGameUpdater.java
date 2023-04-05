package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.*;
import VASSAL.build.module.*;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.DrawPile;
import VASSAL.build.module.map.SetupStack;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.metadata.AbstractMetaData;
import VASSAL.build.module.metadata.MetaDataFactory;
import VASSAL.build.module.metadata.SaveMetaData;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.*;
import VASSAL.counters.*;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.i18n.Resources;
import VASSAL.tools.WarningDialog;
import VASSAL.tools.version.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Replace all counters in the same game with the current version
 * of the counters defined in the module
 * <p>
 * Note: Counters that are Hidden or Obscured to us cannot be updated.
 */
public class ASLGameUpdater extends AbstractConfigurable implements CommandEncoder, GameComponent {

    private static final Logger logger = LoggerFactory.getLogger(ASLGameUpdater.class);

    private static final char DELIMITER = '\t'; //$NON-NLS-1$
    public static final String COMMAND_PREFIX = "DECKREPOS" + DELIMITER; //$NON-NLS-1$

    private Action updateAction;
    private GpIdSupport gpIdSupport;
    private ASLGpIdChecker gpIdChecker;
    private int updatedCount;
    private int notFoundCount;
    private int noStackCount;
    private int noMapCount;
    private GameModule theModule;
    private final Set<String> options = new HashSet<>();
    private boolean hasAlreadyRun = false;

    public List<DrawPile> getModuleDrawPiles() {
        return theModule.getAllDescendantComponentsOf(DrawPile.class);
    }

    /*public ASLGameUpdater(GpIdSupport gpIdSupport) {
        this.gpIdSupport = gpIdSupport;
        theModule = GameModule.getGameModule();
    }*/

    @Override
    public String encode(final Command c) {
        return null;
    }

    @Override
    public Command decode(final String s) {
        return null;
    }

    public void addTo(Buildable parent) {
        theModule = GameModule.getGameModule();
        if (parent instanceof ASLMap) {

            GameModule.getGameModule().addCommandEncoder(this);

            ASLMap map = (ASLMap) parent;

            // On-going game converter
            JMenuItem nextmenuItem = new JMenuItem("Update game...");
            nextmenuItem.setEnabled(true);
            nextmenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {

                    askToUpdate();
                }
            });
            map.getPopupMenu().add(nextmenuItem);


        }

       //TODO Do I need this? Is it ever used?
        updateAction = new AbstractAction(Resources.getString("GameRefresher.refresh_counters")) { //$NON-NLS-1$
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {

                final BasicLogger bl = GameModule.getGameModule().getBasicLogger();
                if ((bl != null) && bl.isReplaying()) {
                    final Command ac = new AlertCommand(Resources.getString("GameRefresher.game_is_replaying"));
                    ac.execute();
                }
                else {
                    new VASSAL.build.module.GameRefresher(gpIdSupport).start();
                }
            }
        };
        GameModule.getGameModule().getGameState().addGameComponent(this);
        GameModule.getGameModule().addCommandEncoder(this);
        updateAction.setEnabled(false);
    }

    public Action getUpdateAction() {
        return updateAction;
    }

    public boolean isTestMode() {
        return options.contains("TestMode"); //$NON-NLS-1$
    }

    public boolean isDeleteNoMap() {
        return options.contains("DeleteNoMap"); //$NON-NLS-1$
    }

    public void start() {
        if (hasAlreadyRun) {
            //return;
        }
        hasAlreadyRun = true;
        final GameModule g = GameModule.getGameModule();
        Command command = new NullCommand();
        final String player = GlobalOptions.getInstance().getPlayerId();
        final Command msg = new Chatter.DisplayText(g.getChatter(), Resources.getString("GameRefresher.run_refresh_counters_v2", player, g.getGameVersion()));
        msg.execute();
        command = command.append(msg);
        execute(command);
        // Send the update to other clients (only done in Player mode)
        g.sendAndLog(command);
    }

    public void log(String message) {
        // Log to chatter
        GameModule.getGameModule().warn(message);
        logger.info(message);
    }

    /**
     * Build a list of all the Refreshables in the module in Visual order (bottom to top) so that we can ensure
     * the visibility order of the refreshed pieces does not change.
     * A Refreshable can be one of
     * - Stack
     * - Deck
     * - Mat with contained Cargo
     * - Single non-Mat unstacked piece
     *
     * @return
     */
    public List<ASLUpdater> getRefreshables() {
        final List<ASLUpdater> refreshables = new ArrayList<>();
        final List<MatUpdater> loadedMats = new ArrayList<>();
        int totalCount = 0;
        int notOwnedCount = 0;
        int notVisibleCount = 0;

        // Process map by map
        for (final Map map : Map.getMapList()) {

            // Get the pieces on this map in visual order
            for (final GamePiece piece : map.getAllPieces()) {

                // A Deck. Pieces in a Deck can always be refreshed
                if (piece instanceof Deck) {
                    final Deck deck = (Deck) piece;
                    totalCount += deck.getPieceCount();
                    refreshables.add(new DeckUpdater(deck));
                }

                // A standard Stack
                else if (piece instanceof Stack) {
                    for (final Iterator<GamePiece> i = ((Stack) piece).getPiecesInVisibleOrderIterator(); i.hasNext(); ) {
                        final GamePiece p = i.next();
                        if (!Boolean.TRUE.equals(p.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))
                                && !Boolean.TRUE.equals(p.getProperty(VASSAL.counters.Properties.OBSCURED_TO_ME))) {
                            totalCount++;
                        }
                        else {
                            if (Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))) {
                                notVisibleCount++;
                            }
                            else {
                                notOwnedCount++;
                            }
                        }
                    }
                    if (((Stack) piece).getMap() != null) {
                        refreshables.add(new StackUpdater((Stack) piece));
                    }
                }

                // An Unstacked piece
                else {
                    final GamePiece p = (GamePiece) piece;

                    // Only visible, unobscured pieces are refreshable
                    if (!Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))
                            && !Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.OBSCURED_TO_ME))) {
                        totalCount++;

                        // Mats with loaded cargo need to be handled separately
                        if (p.getProperty(Mat.MAT_ID) != null && !"0".equals(p.getProperty(Mat.MAT_NUM_CARGO))) {
                            final MatUpdater mr = new MatUpdater(p);
                            refreshables.add(mr);
                            loadedMats.add(mr);
                        }
                        else {
                            refreshables.add(new PieceUpdater(p, true));
                        }
                    }
                    else {
                        if (Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))) {
                            notVisibleCount++;
                        }
                        else {
                            notOwnedCount++;
                        }
                        // Add as non-refreshable
                        refreshables.add(new PieceUpdater(p, false));
                    }
                }
            }

            // If there are any loaded Mats, then find the Stacks of their cargo in the general Refeshables list,
            // remove them and add them to the MatRefresher.
            for (final MatUpdater mr : loadedMats) {
                mr.grabMyCargo(refreshables);
            }

        }

        return refreshables;
    }

    private boolean isGameActive() {
        final GameModule gm = GameModule.getGameModule();
        final BasicLogger logger = gm.getBasicLogger();
        return gm.isMultiplayerConnected() || ((logger != null) && logger.isLogging());
    }


    /**
     * The default execute() method calls: GameModule.getGameModule().getGameState().getAllPieces()
     * to set the pieces list, this method provides an alternative way to specify which pieces should be refreshed.
     *
     * @throws IllegalBuildException - if we get a gpIdChecker error
     */
    public void execute(Command command) throws IllegalBuildException {
        final List<Deck> decks = new ArrayList<>();
        options.clear();
        //options.add("UseName"); //$NON-NLS-1$
        options.add("DeleteNoMap"); //$NON-NLS-1$
        if (command == null) {
            command = new NullCommand();
        }

        notFoundCount = 0;
        updatedCount = 0;
        noMapCount = 0;
        noStackCount = 0;
        /*
         * 1. Use the GpIdChecker to build a cross-reference of all available
         * PieceSlots and PlaceMarker's in the module.
         */
        if (Objects.isNull(gpIdChecker)) { //Only setup gpIdChecker once and keep it in the instance of GameRefresher.
            gpIdChecker = new ASLGpIdChecker(options);
            for (final PieceSlot slot : theModule.getAllDescendantComponentsOf(PieceSlot.class)) {
                gpIdChecker.add(slot);
            }

            // Add any PieceSlots in Prototype Definitions
            for (final PrototypesContainer pc : theModule.getComponentsOf(PrototypesContainer.class)) {
                pc.getDefinitions().forEach(gpIdChecker::add);
            }

            if (gpIdChecker.hasErrors()) {
                // Any gpid errors should have been resolved by the GpId check when the editor is run.
                // If a module created before gpIDChecker was setup is run on a vassal version with gmIDChecker
                // is run in the player, errors might still be present.
                // Inform user that he must upgrade the module to the latest vassal version before running Refresh
                //gpIdChecker.fixErrors();
               log(Resources.getString("GameRefresher.gpid_error_message"));
               //TODO re-instate this at the end of development
                //return;
            }
        }

        /*
         * 2. Build a list in visual order of all stacks, decks, mats and other pieces that need refreshing
         */
        final List<ASLUpdater> refreshables = getRefreshables();

        /*
         * 3. And refresh them. Keep a list of the Decks in case we need to update their attributes
         */
        for (final ASLUpdater refresher : refreshables) {
            refresher.refresh(command);
            if (refresher instanceof DeckUpdater) {
                decks.add(((DeckUpdater) refresher).getDeck());
            }
        }

        if(notFoundCount>0){

            log(Resources.getString("GameRefresher.counters_not_found", notFoundCount));
        }
        if(noMapCount>0){log(Resources.getString("GameRefresher.counters_no_map", noMapCount));}
        if(noStackCount>0){log(Resources.getString("GameRefresher.counters_no_stack", noStackCount));}

        /*
         * 4/ Refresh properties of decks in the game
         */
        if (options.contains("RefreshDecks")) { //NON-NLS
            if (isGameActive()) {
                // If somebody feels like packaging all these things into Commands, help yourself...
                log(Resources.getString("GameRefresher.deck_refresh_during_multiplayer"));
            }
            else {

                //Drawpiles have the module definition of the Deck in the dummy child object
                //  and a link to the actual Deck in the game.
                final List<Deck> decksToDelete = new ArrayList<>();
                final List<DrawPile> drawPiles = getModuleDrawPiles();
                final List<DrawPile> foundDrawPiles = new ArrayList<>();
                final List<DrawPile> decksToAdd = new ArrayList<>();

                int refreshable = 0;
                int deletable = 0;
                int addable = 0;

                log(Resources.getString("GameRefresher.refreshing_decks"));
                for (final Map map : Map.getMapList()) {
                    for (final GamePiece pieceOrStack : map.getPieces()) {
                        if (pieceOrStack instanceof Deck) {
                            final Deck deck = (Deck) pieceOrStack;
                            // Match with a DrawPile if possible
                            boolean deckFound = false;
                            for (final DrawPile drawPile : drawPiles) {
                                final String deckName = deck.getDeckName();
                                if (deckName.equals(drawPile.getAttributeValueString(SetupStack.NAME))) {

                                    // If drawPile is owned by a specific board, then we can only match it if that board is active in this game
                                    if (drawPile.getOwningBoardName() != null) {
                                        if (map.getBoardByName(drawPile.getOwningBoardName()) == null) {
                                            continue;
                                        }

                                        // If the drawPile is on a map that doesn't have its current owning board active, then we
                                        // cannot match that drawPile.
                                        if (drawPile.getMap().getBoardByName(drawPile.getOwningBoardName()) == null) {
                                            continue;
                                        }
                                    }

                                    deckFound = true;
                                    foundDrawPiles.add(drawPile);

                                    final String drawPileName = drawPile.getAttributeValueString(SetupStack.NAME);
                                    log(Resources.getString("GameRefresher.refreshing_deck", deckName, drawPileName));

                                    // This refreshes the existing deck with all the up-to-date drawPile fields from the module
                                    deck.removeListeners();
                                    deck.myRefreshType(drawPile.getDeckType());
                                    deck.addListeners();

                                    // Make sure the deck is in the right place
                                    final Point pt = drawPile.getPosition();
                                    final Map newMap = drawPile.getMap();
                                    if (newMap != map) {
                                        map.removePiece(deck);
                                        newMap.addPiece(deck);
                                    }
                                    deck.setPosition(pt);
                                    for (final GamePiece piece : deck.asList()) {
                                        piece.setMap(newMap);
                                        piece.setPosition(pt);
                                    }

                                    refreshable++;
                                    break;
                                }
                            }
                            if (!deckFound) {
                                deletable++;
                                decksToDelete.add(deck);
                            }
                        }
                    }
                }

                if (options.contains("DeleteOldDecks")) { //NON-NLS
                    //log("List of Decks to remove");
                    for (final Deck deck : decksToDelete) {
                        log(Resources.getString("GameRefresher.deleting_old_deck", deck.getDeckName()));

                        final Stack newStack = new Stack();
                        newStack.setMap(deck.getMap());

                        // First let's remove all the pieces from the deck and put them in a new stack.
                        for (final GamePiece piece : deck.asList()) {
                            newStack.add(piece);
                        }
                        newStack.setPosition(deck.getPosition());

                        // Now, the deck goes bye-bye
                        deck.removeAll();
                        if (deck.getMap() != null) {
                            deck.removeListeners();
                            deck.getMap().removePiece(deck);
                            deck.setMap(null);
                        }

                        // If there were any pieces left in the deck, add the new stack to the map
                        if ((newStack.getPieceCount() > 0) && (newStack.getMap() != null)) {
                            GameModule.getGameModule().getGameState().addPiece(newStack);
                            newStack.getMap().placeAt(newStack, newStack.getPosition());
                        }
                    }
                }
                else if (!decksToDelete.isEmpty()) {
                    log(Resources.getString("GameRefresher.deletable_with_option"));
                    for (final Deck deck : decksToDelete) {
                        log(deck.getDeckName());
                    }
                }

                // Figure out if any decks need to be added
                for (final DrawPile drawPile : drawPiles) {
                    boolean matchFound = false;

                    final Map map = drawPile.getMap();
                    final Collection<Board> boards = map.getBoards();
                    final String boardName = drawPile.getOwningBoardName();
                    final Board board = drawPile.getConfigureBoard(true);
                    if ((boardName == null) || boards.contains(board)) {
                        for (final DrawPile drawPile2 : foundDrawPiles) {
                            if (drawPile.getAttributeValueString(SetupStack.NAME).equals(drawPile2.getAttributeValueString(SetupStack.NAME))) {
                                matchFound = true;
                                break;
                            }
                        }
                        if (!matchFound) {
                            decksToAdd.add(drawPile);
                            addable++;
                        }
                    }
                }

                if (!decksToAdd.isEmpty()) {
                    if (options.contains("AddNewDecks")) { //NON-NLS
                        for (final DrawPile drawPile : decksToAdd) {
                            log(Resources.getString("GameRefresher.adding_new_deck", drawPile.getAttributeValueString(SetupStack.NAME)));

                            final Deck newDeck = drawPile.makeDeck();
                            final Map newMap = drawPile.getMap();
                            if (newMap != null) {
                                drawPile.setDeck(newDeck);
                                GameModule.getGameModule().getGameState().addPiece(newDeck);
                                newMap.placeAt(newDeck, drawPile.getPosition());
                                if (GameModule.getGameModule().getGameState().isGameStarted()) {
                                    newDeck.addListeners();
                                }
                            }
                        }
                    }
                    else {
                        log(Resources.getString("GameRefresher.addable_with_option"));
                        for (final DrawPile drawPile : decksToAdd) {
                            log(drawPile.getAttributeValueString(SetupStack.NAME));
                        }
                    }
                }

                log(Resources.getString("GameRefresher.refreshable_decks", refreshable));
                log(Resources.getString(options.contains("DeleteOldDecks") ? "GameRefresher.deletable_decks" : "GameRefresher.deletable_decks_2", deletable)); //NON-NLS
                log(Resources.getString(options.contains("AddNewDecks") ? "GameRefresher.addable_decks" : "GameRefresher.addable_decks_2", addable)); //NON-NLS
            }
        }
        // update extensions
        ASLUpdater extupdate = new ExtensionUpdater();
        extupdate.refresh(command);
        // update LOS checking
        boolean legacyMode = false;
        for (final Map map : Map.getMapList()) {
            if (map.getMapName().equals("Main Map")) {
                final ASLMap theMap = (ASLMap) map;
                if (theMap == null || theMap.isLegacyMode()) {
                    legacyMode = true;
                } else {
                    legacyMode = false;
                    VASL.LOS.Map.Map LOSMap = theMap.getVASLMap();
                    if (LOSMap == null) {
                        legacyMode = true;
                        return;
                    }
                }
                if (legacyMode) {
                    int dialogResult = JOptionPane.showConfirmDialog(null, "LOS Checking Disabled. To restore, re-select boards for the game. Proceed?",
                            "Updating LOS Checking . . . ", JOptionPane.YES_NO_OPTION);
                    if (dialogResult == JOptionPane.YES_OPTION) {
                        dolosrestore(theMap);
                    }
                }
            }
        }
    }

    public void dolosrestore(ASLMap map){
        BoardSwapper bs = map.getComponentsOf(BoardSwapper.class).get(0);


        bs.recordPiecePositions();
        final ASLBoardPicker picker = new BoardSwapper.Picker(map);
        final JDialog d = new JDialog(GameModule.getGameModule().getPlayerWindow(),true);
        d.getContentPane().setLayout(new BoxLayout(d.getContentPane(),BoxLayout.Y_AXIS));
        d.getContentPane().add(picker.getControls());
        JButton okButton = new JButton("Ok");
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                d.setVisible(false);
                picker.finish();
            }
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                d.setVisible(false);
            }
        });
        Box buttonBox = Box.createHorizontalBox();
        buttonBox.add(okButton);
        buttonBox.add(cancelButton);
        d.getContentPane().add(buttonBox);
        d.pack();
        d.setLocationRelativeTo(GameModule.getGameModule().getPlayerWindow());
        d.setVisible(true);
        bs.restorePiecePositions();
        map.repaint();
    }
    @Override
    public Command getRestoreCommand() {
        return null;
    }

    /**
     * Enable Refresh menu item when game is running only.
     */
    @Override
    public void setup(boolean gameStarting) {
        updateAction.setEnabled(gameStarting);
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[0];
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class[0];
    }

    @Override
    public String[] getAttributeNames() {
        return new String[0];
    }

    @Override
    public void setAttribute(String s, Object o) {

    }

    @Override
    public String getAttributeValueString(String s) {
        return null;
    }

    @Override
    public void removeFrom(Buildable buildable) {

    }

    @Override
    public HelpFile getHelpFile() {
        return null;
    }

    @Override
    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }



    /**
     * Checks for saveGame version older than current module version
     * Displays the confirmation dialog if true and initiates the conversion if the user "yes"
     * Displays no conversion message if false and exits
     */
    public void askToUpdate() {
        final String moduleVersion = GameModule.getGameModule().getGameVersion();
        String filename = GameModule.getGameModule().getGameFile();
        String filepath = GameModule.getGameModule().getGameState().getSavedGameDirectoryPreference().getValueString();
        File file = new File(filepath+"\\"+filename);
        if(file.getName()!="") {
            final AbstractMetaData metaData = MetaDataFactory.buildMetaData(file);
            if (!(metaData instanceof SaveMetaData)) {
                WarningDialog.show("GameState.invalid_save_file", file.getPath()); //NON-NLS
                return;
            }
            // Check if saveGame version matches the module version
            final SaveMetaData saveData = (SaveMetaData) metaData;
            String saveModuleVersion = "?";
            final GameModule g = GameModule.getGameModule();
            // Was the Module Data that created the save stored in the save? (Vassal 3.0+)
            if (saveData.getModuleData() != null) {
                saveModuleVersion = saveData.getModuleVersion();
                // For Module Version and just report in chat.
                if (!saveModuleVersion.equals(moduleVersion)) {
                    int dialogResult = JOptionPane.showConfirmDialog(null, "Make sure all necessary extensions are installed and Chat Window shows no error messages. Proceed?",
                            "Updating Game . . . ", JOptionPane.YES_NO_OPTION);
                    if(dialogResult == JOptionPane.YES_OPTION) {
                        doupdate();
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "No update possible; game was saved with current version or higher",
                            "Updating Game . . . ", JOptionPane.WARNING_MESSAGE);
                }
            }

        }
    }
    /**
     * Execute the update
     */
    public void doupdate() {
        start();


        final Command command = new NullCommand();
        final Chatter chatter = theModule.getChatter();
        final Command msg = new Chatter.DisplayText(chatter, "The game has been updated");

        msg.append(new Chatter.DisplayText(chatter, updatedCount + " counters were updated"));

        if (notFoundCount > 0) {
            msg.append(new Chatter.DisplayText(chatter, notFoundCount + " counters were not found"));
        }

        msg.execute();
        command.append(msg);
    }

    private interface ASLUpdater {
        void refresh(Command command);
        List<GamePiece> getPieces();
        List<GamePiece> getRefreshedPieces();
    }

    /**
     * Class to hold and refresh a Mat and it's cargo
     */
    private class MatUpdater extends MatHolder implements ASLUpdater {

        private final List<ASLUpdater> loadedCargo = new ArrayList<>();

        public MatUpdater(GamePiece piece) {
            super(piece);
        }

        private boolean refresher_for_cargo(ASLUpdater refresher) {
            final GamePiece p = refresher instanceof PieceUpdater ?
                    ((PieceUpdater) refresher).getPiece() :
                    (refresher instanceof StackUpdater ?
                            ((StackUpdater) refresher).getStack().getPieceAt(0) : null);

            return p != null && getMat().hasCargo(p);
        }

        /**
         * Search through the list of Refreshables for any individual pieces, or Stacks that contain pieces,
         * that are loaded onto this Mat. Remove the Refreshable from the supplied List and record it here
         * @param refreshables List of refreshables
         */
        public void grabMyCargo(List<ASLUpdater> refreshables) {
            refreshables.removeIf(refresher -> {
                if (refresher_for_cargo(refresher)) {
                    loadedCargo.add(refresher);
                    return true;
                }
                return false;
            });
        }

        @Override
        public List<GamePiece> getPieces() {
            return null;
        }

        @Override
        public List<GamePiece> getRefreshedPieces() {
            return null;
        }

        /**
         * Refresh this Mat and it's cargo
         * 1. Remove all cargo from the Mat
         * 2. Refresh the Mat
         * 3. Refresh each Cargo and place back on the Mat
         *
         * @param command
         */
        @Override
        public void refresh(Command command) {
            // Remove any existing cargo
            command = command.append(getMat().makeRemoveAllCargoCommand());

            // Refresh the Mat piece
            final PieceUpdater pr = new PieceUpdater(getMatPiece(), true);
            pr.refresh(command);
            final GamePiece newMatPiece = pr.getRefreshedPieces().get(0);

            // Now refresh each cargo stack or piece
            for (final ASLUpdater r : loadedCargo) {
                r.refresh(command);
            }

            // And add the cargo back onto the mat
            for (final ASLUpdater r : loadedCargo) {
                for (final GamePiece refreshedCargo : r.getRefreshedPieces()) {
                    command = command.append(((Mat) Decorator.getDecorator(newMatPiece, Mat.class)).makeAddCargoCommand(refreshedCargo));
                }
            }
        }
    }

    /**
     * Class to refresh a Deck of GamePieces
     */
    private class DeckUpdater implements ASLUpdater {
        private final Deck deck;
        private final List<GamePiece> refreshedPieces = new ArrayList<>();

        public DeckUpdater(Deck deck) {
            this.deck = deck;
        }

        public Deck getDeck() {
            return deck;
        }

        @Override
        public List<GamePiece> getPieces() {
            return deck.asList();
        }

        @Override
        public List<GamePiece> getRefreshedPieces() {
            return refreshedPieces;
        }

        @Override
        public void refresh(Command command) {
            if (isTestMode()) {
                final List<GamePiece> pieces = deck.asList();
                for (final GamePiece piece : pieces) {
                    // Create a new, updated piece
                    if (gpIdChecker.createUpdatedPiece(piece) == null) {
                        notFoundCount++;
                        GameModule.getGameModule().warn(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()))  ;
                        //log(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()));
                    }
                    else {
                        updatedCount++;
                    }
                }
                return;
            }

            // Prevent any listeners or hot Keys firing while we fiddle the Deck
            deck.removeListeners();
            final boolean saveHotKeyOnEmpty = deck.isHotkeyOnEmpty();
            deck.setHotkeyOnEmpty(false);

            // Take a copy of the pieces in the Deck
            final List<GamePiece> pieces = deck.asList();

            // Remove all the pieces from the Deck  and create a set of fresh, updated pieces
            for (final GamePiece piece : pieces) {

                // Remove the existing piece from the Deck, the Map and the GameState
                final Command remove = new RemovePiece(piece);
                remove.execute();
                command = command.append(remove);

                // Create a new, updated piece
                GamePiece newPiece = gpIdChecker.createUpdatedPiece(piece);
                if (newPiece == null) {
                    notFoundCount++;
                    GameModule.getGameModule().warn(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()))  ;
                    //log(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()));
                    // Could not create a new piece for some reason, use the old piece
                    newPiece = piece;
                }
                else {
                    updatedCount++;
                }

                // Keep a list of the new pieces to add back into the Deck
                refreshedPieces.add(newPiece);

                // Add the new pieces back into the GameState
                final Command add = new AddPiece(newPiece);
                add.execute();
                command = command.append(add);
            }

            // Load the new pieces back into the Deck in the same order
            for (final GamePiece piece : refreshedPieces) {
                command = command.append(deck.getMap().placeOrMerge(piece, deck.getPosition()));
            }

            deck.addListeners();
            deck.setHotkeyOnEmpty(saveHotKeyOnEmpty);
            deck.getMap().getPieceCollection().moveToFront(deck);
        }
    }

    /**
     * Class to refresh a Stack of GamePieces
     */
    private class StackUpdater implements ASLUpdater {
        private final Stack stack;
        private final List<GamePiece> refreshedPieces = new ArrayList<>();

        public StackUpdater(Stack stack) {
            this.stack = stack;
        }

        public Stack getStack() {
            return stack;
        }

        @Override
        public List<GamePiece> getPieces() {
            return stack.asList();
        }

        @Override
        public List<GamePiece> getRefreshedPieces() {
            return refreshedPieces;
        }

        @Override
        public void refresh(Command command) {

            // Test mode, just try to create a new piece for each visible to me piece in the Stack
            if (isTestMode()) {
                final List<GamePiece> pieces = stack.asList();
                for (final GamePiece piece : pieces) {
                    if (!Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))
                            && !Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.OBSCURED_TO_ME))) {
                        // Create a new, updated piece
                        if (gpIdChecker.createUpdatedPiece(piece) == null) {
                            notFoundCount++;
                            log(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()));
                        }
                        else {
                            updatedCount++;
                        }
                    }
                }
                return;
            }

            // Take a copy of the pieces in the stack
            final List<GamePiece> pieces = stack.asList();

            // Remove all the pieces from the stack and create a set of fresh, updated pieces
            for (final GamePiece piece : pieces) {

                // Remove the existing piece from the stack, the Map and the GameState
                final Command remove = new RemovePiece(piece);
                remove.execute();
                command = command.append(remove);

                GamePiece newPiece;
                if (!Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))
                        && !Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.OBSCURED_TO_ME))) {
                    // Create a new, updated piece
                    newPiece = gpIdChecker.createUpdatedPiece(piece);
                    if (newPiece == null) {
                        notFoundCount++;
                        GameModule.getGameModule().warn(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()))  ;

                        //log(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()));
                        // Could not create a new piece for some reason, use the old piece
                        newPiece = piece;
                    }
                    else {
                        updatedCount++;
                    }
                }
                else {
                    newPiece = piece;
                }

                // Keep a list of the new pieces to add back into the stack
                refreshedPieces.add(newPiece);

                // Add the new pieces back into the GameState
                final Command add = new AddPiece(newPiece);
                add.execute();
                command = command.append(add);
            }

            // Load the new pieces back into the Stack in the same order
            for (final GamePiece piece : refreshedPieces) {
                command = command.append(stack.getMap().placeOrMerge(piece, stack.getPosition()));
                piece.setMap(stack.getMap());
            }

            stack.getMap().getPieceCollection().moveToFront(stack);
        }
    }

    /**
     * Class to refresh an individual non-stacking piece.
     * If the piece is not refreshable, do not attempt to refresh it.
     */
    private class PieceUpdater implements ASLUpdater {
        private final GamePiece piece;
        private GamePiece refreshedPiece;
        private final boolean refreshable; // Is this piece refreshable by this player?

        public PieceUpdater(GamePiece piece, boolean refreshable) {
            this.piece = piece;
            this.refreshable = refreshable;
        }

        public GamePiece getPiece() {
            return piece;
        }

        public boolean isRefreshable() {
            return refreshable;
        }

        @Override
        public List<GamePiece> getPieces() {
            return List.of(piece);
        }

        @Override
        public List<GamePiece> getRefreshedPieces() {
            return List.of(refreshedPiece);
        }

        @Override
        public void refresh(Command command) {
            final Point position = piece.getPosition();
            final Map map = piece.getMap();
            refreshedPiece = piece;

            if (refreshable) {
                // Test mode, just try to create a new piece for each visible to me piece in the Stack
                if (isTestMode()) {
                    if (!Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))
                            && !Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.OBSCURED_TO_ME))) {
                        // Create a new, updated piece
                        if (gpIdChecker.createUpdatedPiece(piece) == null) {
                            notFoundCount++;
                            log(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()));
                        }
                        else {
                            updatedCount++;
                        }
                    }
                    return;
                }

                // Remove the existing piece the Map and the GameState
                final Command remove = new RemovePiece(piece);
                remove.execute();
                command = command.append(remove);

                // Refresh it
                if (!Boolean.TRUE.equals(piece.getProperty(VASSAL.counters.Properties.INVISIBLE_TO_ME))
                        && !Boolean.TRUE.equals(piece.getProperty(Properties.OBSCURED_TO_ME))) {
                    // Create a new, updated piece
                    refreshedPiece = gpIdChecker.createUpdatedPiece(piece);
                    if (refreshedPiece == null) {
                        notFoundCount++;
                        GameModule.getGameModule().warn(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()))  ;

                        //log(Resources.getString("GameRefresher.refresh_error_nomatch_pieceslot", piece.getName(), piece.getId()));
                        // Could not create a new piece for some reason, use the old piece
                        refreshedPiece = piece;
                    }
                    else {
                        updatedCount++;
                    }
                }
                else {
                    refreshedPiece = piece;
                }

                // Add the new pieces back into the GameState
                final Command add = new AddPiece(refreshedPiece);
                add.execute();
                command = command.append(add);

                // Place the piece back in the correct position
                command = command.append(map.placeOrMerge(refreshedPiece, position));
            }

            map.getPieceCollection().moveToFront(refreshedPiece);

        }
    }
    private class ExtensionUpdater implements ASLUpdater {

        //private final List<ASLUpdater> updatedExt = new ArrayList<>();


        public ExtensionUpdater() {
            super();
        }


        @Override
        public List<GamePiece> getPieces() {
            return null;
        }

        @Override
        public List<GamePiece> getRefreshedPieces() {
            return null;
        }

        /**
         * Refresh this Extension
         *
         * @param command
         */
        @Override
        public void refresh(Command command) {
            boolean containsExtension = false;
            Iterator extit = GameModule.getGameModule().getComponentsOf(ModuleExtension.class).iterator();

            while(extit.hasNext()) {
                ModuleExtension ext = (ModuleExtension) extit.next();

                String availableVersion = null;
                try {
                    availableVersion = ExtensionVersionChecker.getlatestVersionnumberfromwebrepository(ext.getName());
                } catch (Exception e) {
                    // Fail silently if we can't find a version
                    //TODO fix this
                    GameModule.getGameModule().warn(this.getVersionErrorMsg(ext.getName()));
                }
                double serverVersion;
                double localVersion;
                boolean doUpdate;

                if (availableVersion == null) {
                    serverVersion = -1;
                } else {
                    try {
                        serverVersion = Double.parseDouble(availableVersion);
                    } catch (NumberFormatException nfe) {
                        serverVersion = -1;
                    }
                }

                if (ext.getVersion() == null) {
                    localVersion = -1;
                } else {
                    try {
                        localVersion = Double.parseDouble(ext.getVersion());
                    } catch (NumberFormatException nfe) {
                        localVersion = 0;
                    }
                }

                if (localVersion == 0) {
                    // local extension is of an indeterminate state, update the extension if the server version is good
                    doUpdate = (serverVersion > -1);
                } else {
                    // update the extension if the server version greater than local version
                    doUpdate = (serverVersion > localVersion);
                }

                if (doUpdate) {
                    //if update available, ask if user wants to update
                    int dialogResult = JOptionPane.showConfirmDialog(null, "An update is available for Extension " + ext.getName() + ". Proceed?",
                            "Updating Extensions . . . ", JOptionPane.YES_NO_OPTION);
                    if (dialogResult == JOptionPane.YES_OPTION) {
                        // try to update extension if out of date
                        GameModule.getGameModule().warn("Extension " + ext.getName() + " is out of date. Updating...");
                        if (!ExtensionVersionChecker.updateextension(ext.getName())) {
                            GameModule.getGameModule().warn("Update failed");
                        } else {
                            GameModule.getGameModule().warn("Update succeeded");
                        }
                    }
                }

            }
        }
        protected String getVersionErrorMsg(String v) {
            //return Resources.getString("ModuleExtension.wrong_extension_version", new Object[]{this.version, this.name, v});
            return "No version data available so could not determine if update required for " + v;
        }
    }
}
