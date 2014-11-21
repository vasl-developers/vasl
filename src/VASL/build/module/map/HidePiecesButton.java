/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASL.build.module.ASLMap.ShowMapLevel;
import VASSAL.build.AutoConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.Configurable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import static VASSAL.build.module.map.HidePiecesButton.BUTTON_TEXT;
import static VASSAL.build.module.map.HidePiecesButton.DEFAULT_HIDDEN_ICON;
import static VASSAL.build.module.map.HidePiecesButton.DEFAULT_SHOWING_ICON;
import static VASSAL.build.module.map.HidePiecesButton.HIDDEN_ICON;
import static VASSAL.build.module.map.HidePiecesButton.HOTKEY;
import static VASSAL.build.module.map.HidePiecesButton.LAUNCH_ICON;
import static VASSAL.build.module.map.HidePiecesButton.SHOWING_ICON;
import static VASSAL.build.module.map.HidePiecesButton.TOOLTIP;
import VASSAL.command.Command;
import VASSAL.configure.AutoConfigurer;
import VASSAL.configure.Configurer;
import VASSAL.configure.ConfigurerFactory;
import VASSAL.configure.IconConfigurer;
import VASSAL.configure.VisibilityCondition;
import VASSAL.i18n.ComponentI18nData;
import VASSAL.i18n.Resources;
import VASSAL.i18n.Translatable;
import VASSAL.tools.LaunchButton;
import VASSAL.tools.NamedKeyStroke;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import org.w3c.dom.Element;

/**
 *
 * @author Federico
 */
public class HidePiecesButton extends JPanel implements MouseListener,
    AutoConfigurable, GameComponent, Drawable {
    
  private static final long serialVersionUID = 2L;
  protected ShowMapLevel showMapLevel = ShowMapLevel.ShowAll;
  protected Map map;
  protected LaunchButton launch;
  protected String showAllIcon;
  protected String showMapAndOverlay;
  protected String showMapOnly;
  protected ComponentI18nData myI18nData;
  
  public static final String DEFAULT_SHOWALL_ICON = "showall.png";
  public static final String DEFAULT_SHOWMAPANDOVERLAY_ICON = "showmapandoverlay.png";
  public static final String DEFAULT_SHOWMAPONLY_ICON = "showmaponly.png";

  public static final String HOTKEY = "hotkey";
  public static final String SHOWALL_ICON = "showAllIcon";
  public static final String SHOWMAPANDOVERLAY_ICON = "showMapAndOverlayIcon";
  public static final String SHOWMAPONLY_ICON = "showMapOnlyIcon";
  public static final String LAUNCH_ICON = "icon";
  public static final String TOOLTIP = "tooltip";
  public static final String BUTTON_TEXT = "buttonText";
  
  public HidePiecesButton() {
    ActionListener al = new ActionListener() 
    {
      public void actionPerformed(ActionEvent e) 
      {
        changeShowMode(true);
      }
    };
    
    launch = new LaunchButton(null, TOOLTIP, BUTTON_TEXT, HOTKEY, LAUNCH_ICON, al);
    launch.setAttribute(TOOLTIP, "Hide/Show pieces/draggable overlays/map");
    addMouseListener(this);
  }
  
    protected void changeShowMode(boolean bChange) {
        
    if (bChange)
    {
        if (showMapLevel == ShowMapLevel.ShowAll)
            showMapLevel = ShowMapLevel.ShowMapAndOverlay;
        else if (showMapLevel == ShowMapLevel.ShowMapAndOverlay)
            showMapLevel = ShowMapLevel.ShowMapOnly;
        else
            showMapLevel = ShowMapLevel.ShowAll;
    }
        
    ((ASLMap)map).setShowMapLevel(showMapLevel);
    
    if (showMapLevel == ShowMapLevel.ShowAll)
        launch.setAttribute(LAUNCH_ICON, showAllIcon);
    else if (showMapLevel == ShowMapLevel.ShowMapAndOverlay)
        launch.setAttribute(LAUNCH_ICON, showMapAndOverlay);
    else
        launch.setAttribute(LAUNCH_ICON, showMapOnly);
    
    launch.setMargin(new Insets(0,0,0,0));
        
    map.repaint();
  }


  /**
   * Expects to be added to a {@link Map}.  Adds itself as a {@link
   * GameComponent} and a {@link Drawable} component */
  public void addTo(Buildable b) {
    map = (Map) b;

    GameModule.getGameModule().getGameState().addGameComponent(this);

    map.addDrawComponent(this);

    map.getToolBar().add(launch);

    if (b instanceof Translatable) {
      getI18nData().setOwningComponent((Translatable) b);
    }
  }
  
  public void add(Buildable b) {
  }

  public void remove(Buildable b) {
  }

  public void removeFrom(Buildable b) {
    map = (Map) b;
    map.removeDrawComponent(this);
    map.getToolBar().remove(launch);
    GameModule.getGameModule().getGameState().removeGameComponent(this);
  }

  public void setAttribute(String key, Object value) {
    if (SHOWALL_ICON.equals(key)) {
      showAllIcon = (String) value;
    }
    else if (SHOWMAPANDOVERLAY_ICON.equals(key)) {
      showMapAndOverlay = (String) value;
    }
    else if (SHOWMAPONLY_ICON.equals(key)) {
      showMapOnly = (String) value;
    }
    else {
      launch.setAttribute(key,value);
    }
  }
  
  public void build(Element e) {
    AutoConfigurable.Util.buildAttributes(e, this);
  }

  public String[] getAttributeNames() {
    return new String[]{BUTTON_TEXT, TOOLTIP, HOTKEY, SHOWALL_ICON, SHOWMAPANDOVERLAY_ICON, SHOWMAPONLY_ICON};
  }

  public VisibilityCondition getAttributeVisibility(String name) {
    return null;
  }

  public String getAttributeValueString(String key) {
    String s = null;
    if (SHOWALL_ICON.equals(key)) {
      s = showAllIcon;
    }
    else if (SHOWMAPANDOVERLAY_ICON.equals(key)) {
      s = showMapAndOverlay;
    }
    else if (SHOWMAPONLY_ICON.equals(key)) {
      s = showMapOnly;
    }
    else {
      s = launch.getAttributeValueString(key);
    }
    return s;
  }

  public String[] getAttributeDescriptions() {
    return new String[]{
      Resources.getString(Resources.BUTTON_TEXT),
      Resources.getString(Resources.TOOLTIP_TEXT),
      Resources.getString(Resources.HOTKEY_LABEL),
      Resources.getString("Icon when pieces and draggable overlays are showing:"), //$NON-NLS-1$
      Resources.getString("Icon when only draggable overlays are showing:"), //$NON-NLS-1$
      Resources.getString("Icon when only map is showing:"), //$NON-NLS-1$
    };
  }

  public Class<?>[] getAttributeTypes() {
    return new Class<?>[]{
      String.class,
      String.class,
      NamedKeyStroke.class,
      ShowingAllConfig.class,
      ShowingMapAndOverlayConfig.class,
      ShowingMapOnlyConfig.class
    };
  }

  public static class ShowingAllConfig implements ConfigurerFactory {
    public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
      return new IconConfigurer(key,name,DEFAULT_SHOWALL_ICON);
    }
  }

  public static class ShowingMapAndOverlayConfig implements ConfigurerFactory {
    public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
      return new IconConfigurer(key,name,DEFAULT_SHOWMAPANDOVERLAY_ICON);
    }
  }

  public static class ShowingMapOnlyConfig implements ConfigurerFactory {
    public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
      return new IconConfigurer(key,name,DEFAULT_SHOWMAPONLY_ICON);
    }
  }

  public void draw(Graphics g, Map m) {
    repaint();
  }

  public boolean drawAboveCounters() {
    return false;
  }

  public void paint(Graphics g) {
  }

  public void mousePressed(MouseEvent e) {
  }

  public void mouseEntered(MouseEvent e) {
  }

  public void mouseExited(MouseEvent e) {
  }

  public void mouseClicked(MouseEvent e) {
  }

  public void mouseReleased(MouseEvent e) {
  }

  public String getToolTipText(MouseEvent e) {
    return null;
  }

  public Command getRestoreCommand() {
    return null;
  }

  public void setup(boolean show) {
    if (show) {
        changeShowMode(false);
    }
  }

  public static String getConfigureTypeName() {
    return Resources.getString("Change Show Map Mode Button"); //$NON-NLS-1$
  }

  public String getConfigureName() {
    return null;
  }

  public Configurer getConfigurer() {
    return new AutoConfigurer(this);
  }

  public Configurable[] getConfigureComponents() {
    return new Configurable[0];
  }

  public Class<?>[] getAllowableConfigureComponents() {
    return new Class<?>[0];
  }

  public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
  }

  public HelpFile getHelpFile() {
    return null;
  }

  public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) {
    return AutoConfigurable.Util.getBuildElement(doc, this);
  }

  public ComponentI18nData getI18nData() {
    if (myI18nData == null) {
      myI18nData = new ComponentI18nData(this, "ShowMapMode");
    }
    return myI18nData;
  }
}
