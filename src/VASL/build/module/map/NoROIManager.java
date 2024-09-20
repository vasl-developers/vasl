package VASL.build.module.map;

import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.build.module.map.boardPicker.board.ASLHexGrid;
import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_HEIGHT;
import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_WIDTH;
import static VASSAL.build.GameModule.getGameModule;
import static java.awt.event.KeyEvent.VK_C;

import VASL.counters.Concealable;
import VASL.counters.Concealment;
import VASSAL.build.module.*;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.*;
import VASSAL.configure.ColorConfigurer;
import VASSAL.configure.NamedHotKeyConfigurer;
import VASSAL.counters.*;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.NamedKeyStroke;
import VASSAL.tools.hex.Hex;
import VASSAL.tools.hex.OffsetCoord;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;

public class NoROIManager extends AbstractConfigurable implements KeyListener, CommandEncoder, GameComponent, Drawable{
    private static final String ADDALL_COMMAND_PREFIX = "ADDALL_NOROI:";
    private static final String REMOVEALL_COMMAND_PREFIX = "REMOVEALL_NOROI:";
    private Map map;
    private static final String NAME = "Name";

    private static final String ADDALL_KEY = "AddAllKey";
    private static final String REMOVEALL_KEY = "RemoveAllKey";
    private NamedKeyStroke addallKey = new NamedKeyStroke("71dd9847"); // CTL+ALT+G, 71,650
    private NamedKeyStroke removeallKey = new NamedKeyStroke("b385612"); // CTL+ALT+H, 72,650

        @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{
                String.class,
                NamedKeyStroke.class,
                NamedKeyStroke.class
        };
    }

    @Override
    public String[] getAttributeNames() {
        return new String[]{
                NAME,
                ADDALL_KEY,
                REMOVEALL_KEY};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{
                "NOROIManager",
                "Add All Key",
                "Remove All Key",
        };
    }

    @Override
    public String getAttributeValueString(String key) {

        if (NAME.equals(key)) {
            return getConfigureName();
        } else if (ADDALL_KEY.equals(key)) {
            return NamedHotKeyConfigurer.encode(addallKey);
        } else if (REMOVEALL_KEY.equals(key)) {
            return NamedHotKeyConfigurer.encode(removeallKey);
        } else {
            return null;
        }
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (NAME.equals(key)) {
            setConfigureName((String) value);
        } else if (ADDALL_KEY.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            addallKey = (NamedKeyStroke) value;
        } else if (REMOVEALL_KEY.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            removeallKey = (NamedKeyStroke) value;
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

    @Override
    public void draw(Graphics g, Map map) { }

    @Override
    public boolean drawAboveCounters() {
        return false;
    }

    // Encodes a command
    public String encode(Command c) {
        String theroiPlayer = (String) getGameModule().getPrefs().getValue("RealName");
        if (c instanceof NoROIManager.AddAllCommand) {
            String selpiecename = getSelectedPieces().get(0).getName();
            String concealedBy = GameModule.getUserId();
            return ADDALL_COMMAND_PREFIX + theroiPlayer + ":" + selpiecename + ":" + concealedBy;
        } else if (c instanceof NoROIManager.RemoveAllCommand) {
            return REMOVEALL_COMMAND_PREFIX + theroiPlayer;
        } else {
            return null;
        }
    }

    // Decodes a command
    public Command decode(String s) {
        if (s.startsWith(ADDALL_COMMAND_PREFIX)) {
            String[] strings = s.split(":");
            String theroiPlayer = strings[1];
            String selpiecename = strings[2];
            String concealedBy = strings[3];
            return new NoROIManager.AddAllCommand(this, theroiPlayer, selpiecename, concealedBy);
        } else if (s.startsWith(REMOVEALL_COMMAND_PREFIX)) {
            String[] strings = s.split(":");
            String theroiPlayer = strings[1];
            return new NoROIManager.RemoveAllCommand(this, theroiPlayer);
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

        if (!gameStarting) {;}
    }

    @Override
    /**
     * This command is created when a saved game is opened
     */
    public Command getRestoreCommand() {
        return null;
    }

    @Override
    public void removeFrom(Buildable parent) { }

    @Override
    public void keyTyped(KeyEvent e) { }

    @Override
    /**
     * AddAll and RemoveAll keys are defined via the configuration
     * The defaults are CTL+ALT+G and CTL+ALT+H
     */
    public void keyPressed(KeyEvent e) {
        String roiPlayerName = (String) getGameModule().getPrefs().getValue("RealName");
        if (getSelectedPieces() == null || getSelectedPieces().size() == 0) {return;}
        if (addallKey.equals(NamedKeyStroke.of(e))) {
            String selpiecename = getSelectedPieces().get(0).getName();
            String concealedBy = GameModule.getUserId();
            GameModule.getGameModule().getChatter().send(roiPlayerName + " added No ROI counters to their stacks");
            Command addc = new NoROIManager.AddAllCommand(this, roiPlayerName, selpiecename, concealedBy);
            addc.execute();
            GameModule.getGameModule().sendAndLog(addc);
            map.repaint();
            e.consume();
        } else if (removeallKey.equals(NamedKeyStroke.of(e))) {
            LinkedList<GamePiece> selectedPieces = getSelectedPieces();
            if (selectedPieces.size() > 0) {
                GameModule.getGameModule().getChatter().send(roiPlayerName + " removed all their No ROI counters");
                Command remc = new NoROIManager.RemoveAllCommand(this, roiPlayerName);
                remc.execute();
                GameModule.getGameModule().sendAndLog(remc);
                map.repaint();
            }
            e.consume();
        }
    }
    public void removePiecesWithName(String name, String roiPlayerName) {
        Command comm = new NullCommand();
        GamePiece piece[] = map.getPieces();
        for (int i = 0; i < piece.length; ++i) {
            if (piece[i] instanceof Stack) {
                if (((Stack) piece[i]).getPieceAt(0).getProperty("Owner") != null && ((Stack)piece[i]).getPieceAt(0).getProperty("Owner").equals(roiPlayerName)) {
                    for (Iterator<GamePiece> it = ((Stack) piece[i]).getPiecesIterator(); it.hasNext(); ) {
                        GamePiece child = it.next();
                        if (child.getName().contains(name)) {
                            comm = comm.append(new RemovePiece(child));
                        } else if (child.getName().contains("?")) {
                            child.setProperty(Properties.OBSCURED_BY, null);
                        }
                    }
                }
            }
            else if (isMatch(piece[i], name)) {
                if (piece[i].getProperty("Owner") != null && piece[i].getProperty("Owner").equals(roiPlayerName)) {
                    comm = comm.append(new RemovePiece(piece[i]));
                }
            }
        }
        comm.execute();
        GameModule.getGameModule().sendAndLog(comm);
        map.repaint();

    }

    protected boolean isMatch(GamePiece p, String name) {
        return p.getName().equals(name);
    }

    /**
     * Get all currently selected pieces
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
                p.getId() != null && !"".equals(p.getId());
    }

    public void addNoRoiToStacks(String playername, String piecename, String concealedBy) {
        String roiPlayerName = playername;
        Command comm = new NullCommand();
        final PieceIterator pi = new PieceIterator(GameModule.getGameModule().getGameState().getAllPieces().iterator());
        GamePiece clonepiece = null;
        while (pi.hasMoreElements()) {
            final GamePiece piece = pi.nextPiece();
            if (isMatch(piece, piecename)) {
                clonepiece = piece;
                break;
            }
        }
        GamePiece corepiece = null;
        if (clonepiece == null){return;}
        if (clonepiece instanceof Stack){
            corepiece = ((Stack) clonepiece).getPieceAt(0);
        }
        if (corepiece != null){clonepiece = corepiece;}
        LinkedList<GamePiece> addPieces = new LinkedList<GamePiece>();
        LinkedList<Point> addPoints = new LinkedList<Point>();
        LinkedList<Stack> addStacks = new LinkedList<Stack>();
        final PieceIterator pi3 = new PieceIterator(GameModule.getGameModule().getGameState().getAllPieces().iterator());
        while (pi3.hasMoreElements()) {
            GamePiece piece = pi3.nextPiece();
            Point testpt = piece.getPosition();
            if (addPoints.contains(testpt)) {continue;}
            GamePiece newPiece = null;
            Point pt = null;
            if (piece instanceof Stack) {
                // dont add to opponent's stacks
                if (((Stack) piece).getPieceAt(0).getProperty("Owner") != null && ((Stack) piece).getPieceAt(0).getProperty("Owner").equals(roiPlayerName)) {
                    if (((Stack) piece).getPieceCount() >= 2) {
                        pt = piece.getPosition();
                        newPiece = PieceCloner.getInstance().clonePiece(clonepiece);
                    }
                    if (newPiece != null) {
                        addPieces.add(newPiece);
                        addPoints.add(pt);
                        addStacks.add((Stack) piece);
                    }
                }
            }

        }
        // report action
        // now actually create the new counters, HIP them, move them to the stack location and add them to the stack and conceal the counters below
        for (int i = 0; i < addPieces.size(); ++i) {
            comm = comm.append(new AddPiece(addPieces.get(i)));
            setPieceHidden(addPieces.get(i), concealedBy);
            this.map.placeAt(addPieces.get(i), addPoints.get(i));
            addPieceToStack(addPieces.get(i), addStacks.get(i), concealedBy);
        }
        GameModule.getGameModule().sendAndLog(comm);
    }

    private void setPieceHidden(GamePiece piece, String concealedBy) {
        // use owner's game password to hide the piece
        piece.setProperty(Properties.HIDDEN_BY, concealedBy);
    }
    private void addPieceToStack(GamePiece p, Stack stack, String concealedBy){
        //insert the No ROI counter into the stack
        stack.insert(p, stack.getPieceCount()-1);
        // conceal pieces below the No ROI counter
        for (int i = 0; i < stack.getPieceCount()-2; ++i) {
            // use owner's game password to conceal the piece
            stack.getPieceAt(i).setProperty(Properties.OBSCURED_BY, concealedBy);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    private class AddAllCommand extends Command  {
        NoROIManager noroimgr;
        String roiplayer;
        String selectedpiecename;
        String concealedBy;

        AddAllCommand(NoROIManager passnoroimgr, String passroiplayer, String passpiecename, String passconcealby) {
            this.noroimgr = passnoroimgr;
            this.roiplayer = passroiplayer;
            this.selectedpiecename = passpiecename;
            this.concealedBy = passconcealby;
        }

        protected void executeCommand() {
            noroimgr.addNoRoiToStacks(this.roiplayer, this.selectedpiecename, this.concealedBy);
        }
        protected Command myUndoCommand() {
            return null;
        }
    }

    private class RemoveAllCommand extends Command {
        NoROIManager noroimgr;
        String roiplayer;
        RemoveAllCommand(NoROIManager passnoroimgr, String passroiplayer) {
            this.noroimgr = passnoroimgr;
            this.roiplayer = passroiplayer;
        }

        protected void executeCommand() {
            noroimgr.removePiecesWithName("NoRoi", this.roiplayer);
        }

        protected Command myUndoCommand() {
            return null;
        }

    }
}
