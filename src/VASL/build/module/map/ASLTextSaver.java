/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.i18n.Resources;
import VASSAL.tools.imageop.Op;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import javax.swing.*;

/**
 * This class add a menuitem to the popup manu of the ASLMap
 * instead of a button to the toolbar
 * @author FredKors
 */
public class ASLTextSaver extends VASSAL.build.module.map.TextSaver
{
    // menuitem in the popup menu
    JMenuItem m_MenuItem;
    
  public ASLTextSaver() 
  {
    // does the original constructor
    super();
    
    // copy the properties from the jbutton

    // old code replaced wtg new Jan 2023
      if ((getLaunchButton() != null) && getLaunchButton().getListeners(ActionListener.class).length > 0)
      {
          PropertyChangeListener propertyChangeListener = propertyChangeEvent -> {
              String property = propertyChangeEvent.getPropertyName();

              if ("icon".equals(property))
                  m_MenuItem.setIcon((Icon)propertyChangeEvent.getNewValue());
          };

          m_MenuItem = new JMenuItem(getLaunchButton().getToolTipText());
          m_MenuItem.addActionListener(e -> apply());
          getLaunchButton().addPropertyChangeListener(propertyChangeListener);
      }




    /*m_MenuItem = new JMenuItem(getLaunchButton().getToolTipText());
    
    try
    {
        m_MenuItem.setIcon(new ImageIcon(Op.load("QC/text.png").getImage(null)));
    }
    catch (Exception ex) 
    {
        ex.printStackTrace();
    }
    
    m_MenuItem.addActionListener(getLaunchButton().getListeners(ActionListener.class)[0]);*/
}
  
  @Override
  public void addTo(Buildable b) 
  {
     map = (Map) b;
     // does the original addto
     super.addTo(b);
     // removes immediately the button from the toolbar
     map.getToolBar().remove(getLaunchButton());
      // adds the menuitem to the ASLMap popup menu
     ((ASLMap)map).getPopupMenu().add(m_MenuItem);
  }
    public void apply(){
        switch (JOptionPane.showConfirmDialog(GameModule.getGameModule().getPlayerWindow(), Resources.getString("Editor.TextSaver.by_opponents"), "", JOptionPane.YES_NO_OPTION)) {
            case JOptionPane.NO_OPTION:
                writeMapAsText();
                break;
            case JOptionPane.YES_OPTION:
                final String myId = GameModule.getTempUserId();
                GameModule.setTempUserId("yendoR117"); //NON-NLS
                writeMapAsText();
                GameModule.setTempUserId(myId);
                break;
        }
    }
}
