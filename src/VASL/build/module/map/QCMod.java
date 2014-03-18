/*
 * $Id$
 *
 * Copyright (c) 2013-2113 by Federico Corso (FredKors)
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
package VASL.build.module.map;

import org.w3c.dom.Element;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.BasicLogger;
import VASSAL.build.module.Map;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.Timer;

/**
 * A class to remove all GamePieces with a given name
 */
public class QCMod implements Buildable, ActionListener {

    private Timer m_objClock;
    Map m_objMap;
    
  public void build(Element e) 
  {
  }

  public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) 
  {
    return doc.createElement(getClass().getName());
  }

  public void addTo(Buildable b) 
  {
    m_objMap = (Map) b;
    
    for (int l_i = 0; l_i < GameModule.getGameModule().getToolBar().getComponentCount(); l_i++)
    {
        if (GameModule.getGameModule().getToolBar().getComponent(l_i) instanceof JButton)
        {
            JButton l_objB = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(l_i)));

            if (l_objB.getAction() instanceof BasicLogger.UndoAction)
            {
                JButton l_objCopy = CopyActionButton(l_objB, true);
                m_objMap.getToolBar().add(new JToolBar.Separator(), 1);
                m_objMap.getToolBar().add(l_objCopy, 2);
                break;
            }                        
        }
    }

        m_objClock = new Timer (5000, this);
        m_objClock.start();
  }

    @Override
    public void actionPerformed(ActionEvent e) 
    {
        m_objClock.stop();
        
        for (int l_i = 0; l_i < GameModule.getGameModule().getToolBar().getComponentCount(); l_i++)
        {
            if (GameModule.getGameModule().getToolBar().getComponent(l_i) instanceof JButton)
            {
                JButton l_objB = ((JButton)(GameModule.getGameModule().getToolBar().getComponent(l_i)));

                if (l_objB.getToolTipText()!= null)
                {
                    if (l_objB.getToolTipText().contains("VASL Counters window"))
                    {
                        JButton l_objCopy = CopyActionButton(l_objB, false);
                        
                        for (int l_j = 0; l_j < m_objMap.getToolBar().getComponentCount(); l_j++)
                        {
                            if (m_objMap.getToolBar().getComponent(l_j) instanceof QCButton)
                            {
                                m_objMap.getToolBar().add(l_objCopy, l_j);
                                m_objMap.getToolBar().add(new JToolBar.Separator(), l_j + 1);
                                return;
                            }
                        }
                        
                        // button not found, restart the timer
                        m_objClock.restart();
                        
                        return;
                    }                        
                }
            }
        }
        
        m_objClock.restart();
    }
    
    private JButton CopyActionButton(JButton objCopy, boolean bAction) 
    {
        JButton l_btn = new JButton(objCopy.getText());
        
        try
        {
            if (objCopy.getIcon() != null)
                l_btn.setIcon(objCopy.getIcon());
        }
        catch (Exception ex)
        {
        }
        
        if (bAction)
        {
            if (objCopy.getAction() != null)
                l_btn.setAction(objCopy.getAction());            
        }
        else
        {
            for (int l_i = 0; l_i < objCopy.getActionListeners().length; l_i++)
                l_btn.addActionListener(objCopy.getActionListeners()[l_i]);
        }
        
        l_btn.setToolTipText(objCopy.getToolTipText());
       
        return l_btn;
    }
    
  public void add(Buildable b) 
  {
  }
}
