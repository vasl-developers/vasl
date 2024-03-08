package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.AbstractToolbarItem;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.tools.LaunchButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Class to add a button to a map toolbar.
 * Pressing the button changes the zoom level of the boards, independent of the counters.
 **/
public class BoardZoomer extends AbstractToolbarItem {
    protected ASLMap map;
    protected JPopupMenu menu;
    protected ButtonGroup zoomGroup;
    protected ButtonGroup snapGroup;
    protected static double selectedScaleFactor;
    protected static double oldselectedScaleFactor;
    protected static final double[] scaleFactors = {
            0.333333333333,
            0.5,
            0.666666666666,
            1.0,
            1.25,
            1.5,
            2.0,
            3.0
    };
    protected static int selectedSnapFactor;
    protected static final int[] snapFactors = {
            1,
            2
    };
    protected boolean snapGroupBuilt = false;

    private void OutputString(String strMsg) {
        GameModule.getGameModule().getChatter().send(strMsg);
    }

    public BoardZoomer() {
        menu = new JPopupMenu();
        buildMenu();
        setLaunchButton(makeLaunchButton(
                "tool tip",
                "button text",
                "",
                e -> apply()
        ));
        launch = getLaunchButton(); // for compatibility
        launch.setMargin(new Insets(0,0,0,0));
    }

    private void apply() {
        final LaunchButton lb = getLaunchButton();
        if (lb.isShowing()) {
            menu.show(lb, 0, lb.getHeight());
        }
        selectedSnapFactor = checkSnapScale();
    }

    protected void buildMenu() {
        menu.removeAll();
        zoomGroup = new ButtonGroup();
        snapGroup = new ButtonGroup();
        //menu.add(new JLabel("Board Zoom"));
        initZoomItems();
        menu.addSeparator();
        //menu.add(new JLabel("Snap Levels"));
        initSnapItems();
    }

    @Override
    public void addTo(Buildable b) {
        map = (ASLMap) b;
        map.getToolBar().add(getLaunchButton());
        GameModule.getGameModule().getGameState().addGameComponent(this);
    }

    @Override
    public void removeFrom(Buildable b) {
        map = (ASLMap) b;
        map.getToolBar().remove(getLaunchButton());
        map.getToolBar().revalidate();
        GameModule.getGameModule().getGameState().removeGameComponent(this);
    }

    @Override
    public HelpFile getHelpFile() {
        return null;
    }

    @Override
    public Class<?>[] getAllowableConfigureComponents() {
        return new Class<?>[0];
    }

    public void initZoomItems() {
        for (int i = scaleFactors.length-1; i >= 0; --i) {
            final String zs = Math.round(scaleFactors[i] * 100) + "%"; //$NON-NLS-1$
            final JMenuItem item = new JRadioButtonMenuItem(zs.intern());
            item.setActionCommand(Integer.toString(i).intern());
            item.addActionListener(a -> {
                int j = Integer.parseInt(a.getActionCommand());
                oldselectedScaleFactor = selectedScaleFactor;
                selectedScaleFactor = scaleFactors[j];
                //Check ASLMap.class has the required methods
                boolean safe = false;
                ASLMap cl = new ASLMap();
                Class c = cl.getClass();
                try {
                    Method m = c.getDeclaredMethod("getbZoom");
                    safe = true;
                } catch (NoSuchMethodException e) {
                }
                if (safe) {
                    //Change the board zoom
                    double cbZoom = oldselectedScaleFactor;
                    map.setbZoom(selectedScaleFactor);
                    map.getZoomer().setZoomFactor(map.getbZoom() / cbZoom * map.getZoom());
                }
            });
            zoomGroup.add(item);
            menu.add(item);
            selectedScaleFactor = 1.0; //Default to 1.0 board zoom
            if (scaleFactors[i] == selectedScaleFactor) {
                item.setSelected(true);
            }
        }
    }

    public void initSnapItems() {
        for (int i = 0; i < snapFactors.length; ++i) {
            final String zs = "Snap " + snapFactors[i]; //$NON-NLS-1$
            final JMenuItem item = new JRadioButtonMenuItem(zs.intern());
            item.setActionCommand(Integer.toString(i).intern());
            item.addActionListener(a -> {
                int j = Integer.parseInt(a.getActionCommand());
                selectedSnapFactor = snapFactors[j];
                //Change the snap levels
                if (map != null) {
                    for (Board b : map.getBoards()) {
                        ((HexGrid) b.getGrid()).setSnapScale(selectedSnapFactor);
                    }
                }
            });
            snapGroup.add(item);
            menu.add(item);
        }
        selectedSnapFactor = checkSnapScale();
    }

    public int checkSnapScale() {
        int snapScale = 1;
        if (map != null) {
            for (Board b : map.getBoards()) {
                snapScale = ((HexGrid) b.getGrid()).getSnapScale();
            }
        }
        int i = 0;
        //OutputString("snapScale = "+snapScale);
        for (Iterator<AbstractButton> it = snapGroup.getElements().asIterator(); it.hasNext(); ) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) it.next();
            if (snapFactors[i] == snapScale) {item.setSelected(true);}
            i++;
        }
        return snapScale;
    }
}
