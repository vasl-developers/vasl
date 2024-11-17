package VASL.build.module.map;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.MenuDisplayer;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.StringArrayConfigurer;
import VASSAL.configure.StringConfigurer;
import VASSAL.counters.*;
import VASSAL.preferences.Prefs;
import VASSAL.tools.NamedKeyManager;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


public class ASLMenuDisplayer extends MenuDisplayer implements Buildable {
    private static final String POPUP = "Popup Menu";
    private static final String UNUSED = "Unused";
    private final GameModule gameModule;
    private static StringConfigurer popUp;
    private static String keyCommands;

    public ASLMenuDisplayer() {
        this.gameModule = GameModule.getGameModule();
        final Prefs modulePrefs = gameModule.getPrefs();
        // Get the stored value for the key commands, if null set to empty string
        keyCommands = modulePrefs.getStoredValue(POPUP);
        if (keyCommands == null) {
            keyCommands = "";
        }
        popUp = new StringConfigurer(POPUP, "Popup Menu Modifications", keyCommands);
        modulePrefs.addOption("VASL", popUp);
        popUp.setLabelVisible(false);
        popUp.setEnabled(false);

    }

    @Override
    public void addTo(Buildable b) {
        targetSelector = createTargetSelector();
        map = (Map) b;
        map.addLocalMouseListener(this);
    }

    // This both eliminates duplicate code AND makes this critical menu-building functionality able to "play well with others".
    // Menu text & behavior can now be custom-classed without needing to override the monster that is MenuDisplayer#createPopup.
    protected static JMenuItem makeMenuItem(KeyCommand keyCommand) {
        final CustomMenuItem item = new CustomMenuItem(keyCommand.isMenuSeparator() ? MenuSeparator.SEPARATOR_NAME : getMenuText(keyCommand), keyCommand);
        if (!NamedKeyManager.isNamed(keyCommand.getKeyStroke())) { // If the KeyStroke is named, then there is no accelerator
            item.setAccelerator(keyCommand.getKeyStroke());
        }
        item.addActionListener(keyCommand);

        item.setEnabled(keyCommand.isEnabled());

        return item;
    }


    protected static class CustomMenuItem extends JMenuItem {
        private final KeyCommand keyCommand;

        public CustomMenuItem(String text, KeyCommand keyCommand) {
            super(text);
            this.keyCommand = keyCommand;
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            if (e.getID() == MouseEvent.MOUSE_PRESSED && SwingUtilities.isRightMouseButton(e)) {
                JPopupMenu popupMenu = (JPopupMenu) this.getParent();
                Component invoker = popupMenu.getInvoker();
                JMenu unwantedMenu = null;
                String selectedCommandId = keyCommand.getName();

                //Check to see if the "Unwanted menu is in the current popup menu
                for (Component component : popupMenu.getComponents()) {
                    if (component instanceof JMenu && UNUSED.equals(((JMenu) component).getText())) {
                        unwantedMenu = (JMenu) component;
                        popupMenu.remove(this);
                        unwantedMenu.add(this);
                        break;
                    }
                }
                // Check to see if the invoker is in the unwanted menu, if not consume event and exit
                if (unwantedMenu == null) {
                    if (invoker instanceof JMenu && UNUSED.equals(((JMenu) invoker).getText())) {
                        unwantedMenu = (JMenu) invoker;
                        unwantedMenu.remove(this);
                    } else {
                        // Make sure the event does not propagate to the parent component
                        e.consume();
                        return;
                    }
                }

                // if popUp.getValueString() contains the commandId, remove it, else add it
                if (keyCommands.contains(selectedCommandId)) {
                    keyCommands = keyCommands.replace(selectedCommandId, "");
                } else {
                    keyCommands = keyCommands + " " + selectedCommandId;
                }
                popUp.setValue(keyCommands);

                // Revalidate and repaint the root JPopupMenu
                popupMenu.revalidate();
                popupMenu.repaint();
                // Hide and show the root popup menu to force it to resize
                popupMenu.setVisible(false);
                popupMenu.setVisible(true);

            } else {
                super.processMouseEvent(e);
            }
        }
    }

    public static JPopupMenu createPopup(GamePiece target) {
        return createPopup(target, false);
    }

    /**
     * @param target
     * @param global If true, then apply the KeyCommands globally,
     *               i.e. to all selected pieces
     * @return
     */
    public static JPopupMenu createPopup(GamePiece target, boolean global) {
    final JPopupMenu popup = new JPopupMenu();
    popup.putClientProperty("gamePiece", target);
    keyCommands = popUp.getValueString();
    final KeyCommand[] c = (KeyCommand[]) target.getProperty(Properties.KEY_COMMANDS);
    if (c != null) {
        final java.util.List<JMenuItem> commands = new ArrayList<>();
        final java.util.List<KeyStroke> strokes = new ArrayList<>();
        final java.util.Map<KeyCommandSubMenu, JMenu> subMenus = new HashMap<>();
        final java.util.Map<String, java.util.List<JMenuItem>> commandNames = new HashMap<>();
        final JMenu unwantedMenu = new JMenu(UNUSED);

        for (final KeyCommand keyCommand : c) {
            keyCommand.setGlobal(global);
            JMenuItem item = null;

            if (!keyCommands.contains(keyCommand.getName())) {
                if (keyCommand instanceof KeyCommandSubMenu) {
                    final JMenu subMenu = new JMenu(getMenuText(keyCommand));
                    subMenus.put((KeyCommandSubMenu) keyCommand, subMenu);
                    item = subMenu;
                    commands.add(item);
                    strokes.add(KeyStroke.getKeyStroke('\0'));
                } else {
                    final KeyStroke stroke = keyCommand.getKeyStroke();
                    if (strokes.contains(stroke) && !keyCommand.isMenuSeparator() && !(keyCommand instanceof MultiLocationCommand.MultiLocationKeyCommand)) {
                        final JMenuItem command = commands.get(strokes.indexOf(stroke));
                        final Action action = command.getAction();
                        if (action != null) {
                            final String commandName = (String) command.getAction().getValue(Action.NAME);
                            if (commandName == null || commandName.length() < keyCommand.getName().length()) {
                                item = makeMenuItem(keyCommand);
                                commands.set(strokes.indexOf(stroke), item);
                            }
                        }
                    } else {
                        strokes.add((stroke != null && !keyCommand.isMenuSeparator()) ? stroke : KeyStroke.getKeyStroke('\0'));
                        item = makeMenuItem(keyCommand);
                        commands.add(item);
                    }
                }
            } else {
                JMenuItem unwantedItem = makeMenuItem(keyCommand);
                if (unwantedItem.getText() != null && !unwantedItem.getText().isBlank()) {
                    unwantedMenu.add(unwantedItem);
                }
            }

            if (keyCommand.getName() != null && keyCommand.getName().length() > 0 && item != null) {
                final java.util.List<JMenuItem> l = commandNames.computeIfAbsent(keyCommand.getName(), k -> new ArrayList<>());
                l.add(item);
            }
        }

        for (final java.util.Map.Entry<KeyCommandSubMenu, JMenu> e : subMenus.entrySet()) {
            final KeyCommandSubMenu menuCommand = e.getKey();
            final JMenu subMenu = e.getValue();

            for (final Iterator<String> it2 = menuCommand.getCommands(); it2.hasNext(); ) {
                final java.util.List<JMenuItem> matchingCommands = commandNames.get(it2.next());
                if (matchingCommands != null) {
                    for (final JMenuItem item : matchingCommands) {
                        subMenu.add(item);
                        commands.remove(item);
                    }
                }
            }
        }

        for (final JMenuItem item : commands) {
            final String text = item.getText();
            if (MenuSeparator.SEPARATOR_NAME.equals(text)) {
                popup.addSeparator();
            } else if (text != null && !text.isBlank()) {
                popup.add(item);
            }
        }
        popup.addSeparator();
        popup.add(unwantedMenu);
    }

    return popup;
}

    @Override
    public void mousePressed(MouseEvent e) {
        maybePopup(e, false);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        maybePopup(e, true);
    }

    protected void maybePopup(MouseEvent e) {
        maybePopup(e, false);
    }

    protected void maybePopup(MouseEvent e, boolean specialLaunchAllowed) {
        final GamePiece p = map.findPiece(e.getPoint(), targetSelector);
        if (p == null) {
            return;
        }

        // This block detects ActionButton traits
        if (!e.isPopupTrigger()) {
            if (e.isAltDown() || e.isShiftDown() || !specialLaunchAllowed) {
                return;
            }
            if (map.getPieceMover().getBreachedThreshold()) { // If we're finishing a legit drag
                return;
            }
            if (map.getKeyBufferer().isLasso()) { // If we dragged a selection box
                return;
            }

            final Point epos = e.getPoint();
            final Point rel = map.positionOf(p);
            epos.translate(-rel.x, -rel.y);
            final Shape s = p.getShape();
            if (!s.contains(epos)) {
                return;
            }

            // Get a list of the ActionButton traits in this piece that overlap this mouseclick
            final java.util.List<GamePiece> actionButtons = ActionButton.matchingTraits(p, epos);

            boolean anyMenu = false;
            // Check if any of the overlapping action buttons have the launch-menu flag set
            for (final GamePiece trait : actionButtons) {
                final ActionButton action = (ActionButton) trait;

                if (!action.isLaunchPopupMenu()) continue;
                anyMenu = true;
                break;
            }

            if (!anyMenu) {
                return;
            }
        }

        final EventFilter filter = (EventFilter) p.getProperty(Properties.SELECT_EVENT_FILTER);
        if (filter != null && filter.rejectEvent(e)) {
            return;
        }

        final JPopupMenu popup = createPopup(p, true);
        if (popup != null) {
            popup.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuCanceled(PopupMenuEvent evt) {
                    map.repaint();
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent evt) {
                    map.repaint();
                }

                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent evt) {
                }
            });

            // NB: The conversion back to component coordinates is correct. The
            // master mouse event listener on the map translates all coordinates to
            // map coordinates before passing them on.
            final Point pt = map.mapToComponent(e.getPoint());

            // It is possible for the map to close before the menu is displayed
            if (map.getView().isShowing()) {

                // Inform the piece where player clicked, if it wants to know.
                KeyBuffer.getBuffer().setClickPoint(e.getPoint());

                popup.show(map.getView(), pt.x, pt.y);
            }
        }

        e.consume();
    }


}
