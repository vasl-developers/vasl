package VASL.build.module.map;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.configure.NamedHotKeyConfigurer;
import VASSAL.tools.NamedKeyStroke;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Iterator;

public class ASLCasbin extends AbstractConfigurable implements KeyListener, GameComponent, Drawable {

    private Buildable pparent;
    private Map map;
    private static final String LINK_KEY = "LinkKey";
    private KeyStroke linkKey = KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_MASK + InputEvent.ALT_MASK);

    public Class<?>[] getAttributeTypes() {
        return new Class<?>[]{NamedKeyStroke.class};
    }

    @Override
    public String[] getAttributeNames() {
        return new String[]{LINK_KEY};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[]{"Link Key"};
    }

    @Override
    public String getAttributeValueString(String key) {
        if (LINK_KEY.equals(key)) {
            return null; //NamedHotKeyConfigurer.encode(linkKey);
        } else {
            return null;
        }
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (LINK_KEY.equals(key)) {
            if (value instanceof String) {
                value = NamedHotKeyConfigurer.decode((String) value);
            }
            //linkKey = (NamedKeyStroke) value;
        }
    }

    public void addTo(Buildable parent) {
        pparent = parent;
        // add this component to the game
        // add this component to the game and register a mouse listener
        if (parent instanceof Map) {
            this.map = (Map) parent;
            GameModule mod = GameModule.getGameModule();
            mod.getGameState().addGameComponent(this);
            map.addDrawComponent(this);
            map.getView().addKeyListener(this);
        }
    }

    public void removeFrom(Buildable parent) {

    }

    public HelpFile getHelpFile() {
        return null;
    }

    @Override
    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }

    @Override
    public void setup(boolean gameStarting) {
    }

    @Override
    public Command getRestoreCommand() {
        return null;
    }

    public void draw(Graphics g, Map map) {
    }

    @Override
    public boolean drawAboveCounters() {
        return true;
    }

    public void startcasbin(boolean visible) {
        Iterator var7 = GameModule.getGameModule().getBuildables().iterator();

        while (var7.hasNext()) {
            Buildable CheckforCasbin = (Buildable) var7.next();
            if (CheckforCasbin instanceof Map && ((Map) CheckforCasbin).getMapName().equals("Casualties")) {
                Map casbin = (Map) CheckforCasbin;
                String alliedkey = "AlliedCVP";
                String alliedcvp = ((String) casbin.getProperty(alliedkey));
                GameModule.getGameModule().getChatter().send("Allied CVP: " + alliedcvp);
                String axiskey = "AxisCVP";
                String axiscvp = ((String) casbin.getProperty(axiskey));
                GameModule.getGameModule().getChatter().send("Axis CVP: " + axiscvp);
            }
        }

    }

    public void keyPressed(KeyEvent e) {

        if (linkKey.equals(KeyStroke.getKeyStrokeForEvent(e))) {
            Iterator var7 = GameModule.getGameModule().getBuildables().iterator();

            while (var7.hasNext()) {
                Buildable CheckforCasbin = (Buildable) var7.next();
                if (CheckforCasbin instanceof Map && CheckforCasbin != pparent) {
                    Map casbin = (Map) CheckforCasbin;
                    String alliedkey = "AlliedCVP";
                    String alliedcvp = ((String) casbin.getProperty(alliedkey));
                    GameModule.getGameModule().getChatter().send("Allied CVP: " + alliedcvp);
                    String axiskey = "AxisCVP";
                    String axiscvp = ((String) casbin.getProperty(axiskey));
                    GameModule.getGameModule().getChatter().send("Axis CVP: " + axiscvp);
                }
            }
        }
    }

    public void keyReleased(KeyEvent e) {

    }

    public void keyTyped(KeyEvent e) {

    }
    public class MyClassWithText {
        protected PropertyChangeSupport propertyChangeSupport;
        private String text;

        public MyClassWithText () {
            propertyChangeSupport = new PropertyChangeSupport(this);
        }

        public void setText(String text) {
            String oldText = this.text;
            this.text = text;
            propertyChangeSupport.firePropertyChange("MyTextProperty",oldText, text);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.addPropertyChangeListener(listener);
        }
    }

    public class MyTextListener implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (event.getPropertyName().equals("MyTextProperty")) {
                System.out.println(event.getNewValue().toString());
            }
        }
    }
}
