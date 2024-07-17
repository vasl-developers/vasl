/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.Buildable;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.GlobalMap;
import java.awt.Insets;
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
public class ASLGlobalMap extends GlobalMap {
    
  // menuitem in the popup menu
  private JMenuItem menuItem = null;
    
  public ASLGlobalMap() 
  {
    // does the original constructor
    super();
    
    // copy the properties from the jbutton
    if ((launch instanceof JButton) && (launch.getListeners(ActionListener.class).length > 0)) {
        PropertyChangeListener propertyChangeListener = propertyChangeEvent -> {
            String property = propertyChangeEvent.getPropertyName();

            if ("icon".equals(property))
                menuItem.setIcon((Icon)propertyChangeEvent.getNewValue());
        };
        
        String tooltipText = launch.getToolTipText();
        
        int tooltipTextEnd = tooltipText.indexOf("[");
        
        if (tooltipTextEnd > 0)
            tooltipText = tooltipText.substring(0, tooltipTextEnd - 1);
        else
            tooltipText = "";
 
        menuItem = new JMenuItem(tooltipText);
        menuItem.addActionListener(((JButton)launch).getListeners(ActionListener.class)[0]);
        menuItem.setMargin(new Insets(0,0,0,0));
        
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
     ((ASLMap)map).getPopupMenu().add(menuItem);
  }
}
