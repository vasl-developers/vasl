/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.Buildable;
import VASSAL.build.module.Map;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;

/**
 * This class add a menuitem to the popup manu of the ASLMap
 * instead of a button to the toolbar
 * @author FredKors
 */
public class ASLImageSaver extends VASSAL.build.module.map.ImageSaver 
{
    // menuitem in the popup menu
     JMenuItem m_MenuItem = null;
    
  public ASLImageSaver() 
  {
     // does the original constructor
     super();
    
     // copy the properties from the jbutton
     if ((launch instanceof JButton) && (((JButton)launch).getListeners(ActionListener.class).length > 0))
     {
        PropertyChangeListener propertyChangeListener = new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent propertyChangeEvent) 
            {
                String property = propertyChangeEvent.getPropertyName();
            
                if ("icon".equals(property)) 
                    m_MenuItem.setIcon((Icon)propertyChangeEvent.getNewValue());
            }
        };
        
        m_MenuItem = new JMenuItem(launch.getToolTipText());
        m_MenuItem.addActionListener(((JButton)launch).getListeners(ActionListener.class)[0]);
        
        launch.addPropertyChangeListener(propertyChangeListener);
     }
  }
  
  @Override
  public void addTo(Buildable b) 
  {
      map = (Map) b;
      // does the original addto
      super.addTo(b);
      // removes immediately the button from the toolbar
      map.getToolBar().remove(launch);
      // adds the menuitem to the ASLMap popup menu
      ((ASLMap)map).getPopupMenu().add(m_MenuItem);
  }
    
}
