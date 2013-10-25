/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASL.build.module.ASLMap;
import VASSAL.build.Buildable;
import VASSAL.build.module.Map;
import VASSAL.tools.imageop.Op;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;

/**
 * This class add a menuitem to the popup manu of the ASLMap
 * instead of a button to the toolbar
 * @author FredKors
 */
public class ASLTextSaver extends VASSAL.build.module.map.TextSaver
{
    // menuitem in the popup menu
    JMenuItem m_MenuItem = null;
    
  public ASLTextSaver() 
  {
    // does the original constructor
    super();
    
    // copy the properties from the jbutton
    m_MenuItem = new JMenuItem(launch.getToolTipText());
    
    try
    {
        m_MenuItem.setIcon(new ImageIcon(Op.load("QC/text.png").getImage(null)));
    }
    catch (Exception ex) 
    {
        ex.printStackTrace();
    }
    
    m_MenuItem.addActionListener(((JButton)launch).getListeners(ActionListener.class)[0]);
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
