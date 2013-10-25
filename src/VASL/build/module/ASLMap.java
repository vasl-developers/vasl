/*
 * $Id: ASLMap.java 8530 2012-12-26 04:37:04Z uckelman $
 *
 * Copyright (c) 2013 by Brent Easton
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

package VASL.build.module;

import java.awt.Point;

import VASSAL.build.module.Map;
import VASSAL.tools.imageop.Op;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class ASLMap extends Map {

    // FredKors popup menu
    private JPopupMenu m_mnuMainPopup = null;
    
  public ASLMap() {      
    super();
    
    m_mnuMainPopup = new JPopupMenu();
    
    // FredKors: creation of the toolbar button
    // that opens the popup menu
    JButton l_Menu = new JButton();
    
    try
    {
        l_Menu.setIcon(new ImageIcon(Op.load("QC/menu.png").getImage(null)));
    }
    catch (Exception ex) 
    {
        ex.printStackTrace();
    }
    
    l_Menu.setMargin(new Insets(0, 0, 0, 0));
    l_Menu.setAlignmentY(0.0F);
    
    l_Menu.addActionListener(new ActionListener() 
    {
        public void actionPerformed(ActionEvent evt) 
        {
            if (evt.getSource() instanceof JButton)
                m_mnuMainPopup.show((JButton)evt.getSource(), 0, 0);
        }
    });

    // add the first element to the popupp menu
    JMenuItem l_SelectItem = new JMenuItem("Select");
    l_SelectItem.setBackground(new Color(255,255,255));
    m_mnuMainPopup.add(l_SelectItem);
    m_mnuMainPopup.addSeparator();    

    // add the menu button to the toolbar
    getToolBar().add(l_Menu); 
    getToolBar().addSeparator();
}
  
  /*
   *  Work-around for VASL board being 1 pixel too large causing double stacks to form along board edges.
   *  Any snap to a board top or left edge half hex, bump it 1 pixel up or left on to the next board.
   *  
   *  */
  public Point snapTo(Point p) {
    final Point p1 = super.snapTo(p);
    final Point p2 = new Point (p1);
    final String loc = locationName(p1);
    
    // Ignore Edge and Corner locations
    if (! loc.contains("/")) {
      // Zero row hexes are all top edge half hexes, bump the snap up 1 pixel to the board above.
      if (loc.endsWith("0") && ! loc.endsWith("10")) {
        p2.y -= 1;
      }
      // Column A hexes are all left edge half gexes, bump the snap left 1 pixel to the board to the left 
      else if (loc.contains("A") && ! loc.contains("AA")) {
        p2.x -=1;
      }
    }
    // If the snap has been bumped offmap (must be top or right edge of map), use the original snap.
    if (findBoard(p2) == null) {
      return p1;
    }
    return p2;
  }
  // return the popup menu
  public JPopupMenu getPopupMenu()
  {
      return m_mnuMainPopup;
  }  
}