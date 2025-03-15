package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.configure.NamedHotKeyConfigurer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.KeyStrokeListener;
import VASSAL.tools.NamedKeyStroke;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.security.Key;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import static VASSAL.build.GameModule.getGameModule;

/**
 * This class provides functionality for the Draw Counter map window
 * which allows users to add counters to the window from the counter palette and toolbar
 * and then randomly select one at a time which is then moved to the Main Map window
 * and placed close to the top left corner of the map image
 *
 * A number of the methods are required by the implemented classes.
 */
public class RandomSelectCounter extends AbstractConfigurable implements KeyListener, GameComponent, Drawable, CommandEncoder {
    ASLMap map;
    Map rsc;
    boolean notbuilt=true;

    private static final String SELECT_LEFT = "SelectLeft";
    private static final String SELECT_CENTER = "SelectCenter";
    private static final String SELECT_RIGHT = "SelectRight";
    private static final String MOVE_SELECTION = "MoveSelection";
    //the value of these NamedKeyStrokes are passed to the class from the buildFile entry for the class
    //they represent the keystrokes that KeyPressed "listens" for
    private NamedKeyStroke selectLeft = new NamedKeyStroke("selleft"); // CTL+SHIFT+N 78,195
    private NamedKeyStroke selectCenter = new NamedKeyStroke("selcenter"); // CTL+SHIFT+Y 89,195
    private NamedKeyStroke selectRight = new NamedKeyStroke("selright"); // CTL+SHIFT+Z 90,195
    //This keystroke is used in doSelectAndMove to trigger the selected counter to move to the Main map
    //it is "listened" for by a Send To Location trait in various counter prototypes (Unit, DBGlobal, DBCommon)
    private NamedKeyStroke moveSelection = new NamedKeyStroke("movselection"); // CTL+SHIFT+B 66,195

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{
            "Select Left",
            "Select Center",
            "Select Right",
            "Move Selection"
        };
    }

    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{
            NamedKeyStroke.class,
            NamedKeyStroke.class,
            NamedKeyStroke.class,
            NamedKeyStroke.class
        };
    }

    public String[] getAttributeNames() {
        return new String[]{
            SELECT_LEFT,
            SELECT_CENTER,
            SELECT_RIGHT,
            MOVE_SELECTION
        };
    }
    public void setAttribute(String key, Object value) {
        if (SELECT_LEFT.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            selectLeft = (NamedKeyStroke) value;
        } else if (SELECT_CENTER.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            selectCenter = (NamedKeyStroke) value;
        } else if (SELECT_RIGHT.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            selectRight = (NamedKeyStroke) value;
        } else if (MOVE_SELECTION.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            moveSelection = (NamedKeyStroke) value;
        }
    }
    public String getAttributeValueString(String key) {
        if (SELECT_LEFT.equals(key)) {
            return NamedHotKeyConfigurer.encode(selectLeft);
        } else if (SELECT_CENTER.equals(key)) {
            return NamedHotKeyConfigurer.encode(selectCenter);
        } else if (SELECT_RIGHT.equals(key)) {
            return NamedHotKeyConfigurer.encode(selectRight);
        } else if (MOVE_SELECTION.equals(key)) {
            return NamedHotKeyConfigurer.encode(moveSelection);
        } else {
            return null;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (rsc == null) {return;}
        if (selectLeft.equals(NamedKeyStroke.of(e))) {
            doSelectandMove(rsc, "Left Select");
        } else if (selectCenter.equals(NamedKeyStroke.of(e))) {
            doSelectandMove(rsc, "Center Select");
        } else if (selectRight.equals(NamedKeyStroke.of(e))) {
            doSelectandMove(rsc, "Right Select");
        }
        e.consume();
    }

    public void keyReleased(KeyEvent e) { }

    @Override
    public void addTo(Buildable b) {
        // add this component to the game and register a key listener
        if (b instanceof ASLMap) {
            map = (ASLMap) b;
            GameModule mod = GameModule.getGameModule();
            mod.getGameState().addGameComponent(this);
            map.addDrawComponent(this);
            map.getView().addKeyListener(this);
            // this menu item opens the Draw Counters map window
            // if first time, it adds the Select buttons to the Draw Counters window
            JMenuItem nextmenuItem = new JMenuItem("Draw Counters Window");
            nextmenuItem.setEnabled(true);
            nextmenuItem.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent evt) {
                   startSelectCounter();
               }
            });
            map.getPopupMenu().add(nextmenuItem);
        }
    }

    private void startSelectCounter() {
        Iterator var7 = GameModule.getGameModule().getBuildables().iterator();
        while (var7.hasNext()) {
            Buildable drawcounter = (Buildable) var7.next();
            if (drawcounter instanceof Map && ((Map) drawcounter).getMapName().equals("Select Counters")) {
                rsc = (Map) drawcounter;
                rsc.getView().addKeyListener(this);
                if (notbuilt) {
                    setuprscwindow(rsc);
                    notbuilt = false;
                }
                rsc.showMap();
                return;

            }
        }
        String notFound = "You do not have the Draw Counters Extension installed. Add the extension and try again.";
        GameModule.getGameModule().getChatter().send(notFound);
    }

    public void setuprscwindow(Map rsc){
        // add buttons to the toolbar of the Draw Counters map window
        // could not be pre-built as part of the Draw Counter map window because
        // listeners here would not "hear" the button clicks
        JButton buttonDrawLeft = new JButton("Pull From Left");
        buttonDrawLeft.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt)  {
                if (evt.getSource() instanceof JButton) {
                    doSelectandMove(rsc, "Left Select");
                }
            }
        });
        rsc.getToolBar().add(buttonDrawLeft);
        JButton buttonDrawCenter = new JButton("Pull From Center");
        buttonDrawCenter.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt)  {
                if (evt.getSource() instanceof JButton) {
                    doSelectandMove(rsc, "Center Select");
                }
            }
        });
        rsc.getToolBar().add(buttonDrawCenter);
        JButton buttonDrawRight = new JButton("Pull From Right");
        buttonDrawRight.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt)  {
                if (evt.getSource() instanceof JButton) {
                    doSelectandMove(rsc, "Right Select");
                }
            }
        });
        rsc.getToolBar().add(buttonDrawRight);
    }

    public void doSelectandMove(Map rsc, String selectedzone){
       // Randomly Select One Counter and move it the Main Map Window
        LinkedList<GamePiece> drawList = new LinkedList<GamePiece>();
        // get all of the game pieces on the Map
        GamePiece[] p = rsc.getPieces();
        GamePiece piece;
        // add each of the pieces in selected zone to a list
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    piece = pi.nextPiece();
                    if (piece.getProperty("CurrentZone")  == selectedzone) {
                        drawList.add(piece);
                    }
                }
            } else {
                if (aP.getProperty("CurrentZone")  == selectedzone) {
                    drawList.add(aP);
                }
            }
        }
        // randomly select from list - code from Google search; it appears to work
        Random rand = new Random();
        GamePiece randomCounter = null;
        int numberOfElements = 1;
        for (int i = 0; i < numberOfElements; i++) {
            int randomIndex = rand.nextInt(drawList.size());
            randomCounter = drawList.get(randomIndex);
            drawList.remove(randomIndex);
            randomCounter.setProperty(Properties.SELECTED, Boolean.TRUE);
            // send keystroke that we be heard by the selected counters Send To Location trait - which contains
            // required conditions (on Draw Counters map window, Selected, and in CurrentZone
            randomCounter.keyEvent(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK));

        }
    }

    @Override
    public String encode(Command c) {
        return null;
    }

    @Override
    public Command decode(String s) {
        return null;
    }

    @Override
    public void setup(boolean show) {

    }

    @Override
    public Command getRestoreCommand() {
        Command c = new NullCommand();

        return c;
    }

    public void executeCommand() {

    }

    protected Command myUndoCommand() {
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

    @Override
    public void draw(Graphics graphics, Map map) {

    }

    @Override
    public boolean drawAboveCounters() {
        return false;
    }
}
