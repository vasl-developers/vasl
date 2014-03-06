/*
 * $Id: ASLPieceFinder.java 0000 2009-03-09 03:22:10Z davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
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

import VASL.build.module.ASLChatter;
import VASL.build.module.ASLDiceBot;
import VASL.build.module.ASLMap;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.configure.HotKeyConfigurer;
import VASSAL.tools.imageop.Op;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import VASL.build.module.ASLChatter.ChatterListener;
import static VASSAL.build.module.Chatter.getAnonymousUserName;
import VASSAL.build.module.GlobalOptions;
import VASSAL.configure.ColorConfigurer;
import VASSAL.configure.FontConfigurer;
import VASSAL.preferences.Prefs;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;

interface NeedRepaintEvent {
    public void NeedRepaint();
}

class DiceRollHandler
{
    private long m_lCount;
    private long m_lClock;
    private boolean m_bAlive;
    private boolean m_bFriendly;
    private String m_strCategory;
    private String m_strNickName;
    private int m_iFirstDice;
    private int m_iSecondDice;
    private boolean m_bAxisSAN;
    private boolean m_bAlliedSAN;
    private boolean m_bBothSAN;
    
    public DiceRollHandler(long lCount, long lClock, String strCategory, String strNickName, String strSAN, int iFirstDice, int iSecondDice)
    {
        m_lCount = lCount;
        m_lClock = lClock;
        m_bAlive = true;
        
        if (strNickName.compareToIgnoreCase(DiceRollQueueHandler.GetFriendlyPlayerNick()) == 0)
            m_bFriendly = true;
        else
            m_bFriendly = false;
        
        m_strCategory = strCategory;
        m_strNickName = strNickName;
        m_iFirstDice = iFirstDice;
        m_iSecondDice = iSecondDice;
        
        if ((strSAN == null) || (strSAN.isEmpty()))
        {
            m_bAxisSAN = false;
            m_bAlliedSAN = false;
            m_bBothSAN = false;
        }
        else if (strSAN.compareToIgnoreCase("Axis SAN") == 0)
        {
            m_bAxisSAN = true;
            m_bAlliedSAN = false;
            m_bBothSAN = false;
        }
        else if (strSAN.compareToIgnoreCase("Allied SAN") == 0)
        {
            m_bAxisSAN = false;
            m_bAlliedSAN = true;
            m_bBothSAN = false;
        }
        else if (strSAN.compareToIgnoreCase("Axis/Allied SAN") == 0)
        {
            m_bAxisSAN = false;
            m_bAlliedSAN = false;
            m_bBothSAN = true;
        }
        else
        {
            m_bAxisSAN = false;
            m_bAlliedSAN = false;
            m_bBothSAN = false;
        }
    }

    /**
     * @return the m_lClock
     */
    public long getClock() {
        return m_lClock;
    }

    /**
     * @param m_lClock the m_lClock to set
     */
    public void setClock(long lClock) {
        m_lClock = lClock;
    }
    
    public void Dead()
    {
        m_bAlive = false;
    }
    
    public void Alive()
    {
        m_bAlive = true;
    }

    public boolean IsAlive()
    {
        return m_bAlive;
    }

    /**
     * @return the m_bFriendly
     */
    public boolean isFriendly() {
        return m_bFriendly;
    }

    /**
     * @return the m_strCategory
     */
    public String getCategory() {
        return m_strCategory;
    }

    /**
     * @return the m_strUser
     */
    public String getNickName() {
        return m_strNickName;
    }

    /**
     * @return the m_iFirstDice
     */
    public int getFirstDice() {
        return m_iFirstDice;
    }

    /**
     * @return the m_iSecondDice
     */
    public int getSecondDice() {
        return m_iSecondDice;
    }

    /**
     * @return the m_lCount
     */
    public long getCount() {
        return m_lCount;
    }

    /**
     * @return the m_bAxisSAN
     */
    public boolean isAxisSAN() {
        return m_bAxisSAN;
    }

    /**
     * @return the m_bAlliedSAN
     */
    public boolean isAlliedSAN() {
        return m_bAlliedSAN;
    }

    /**
     * @return the m_bBothSAN
     */
    public boolean isBothSAN() {
        return m_bBothSAN;
    }
}

class DiceRollQueueHandler implements ActionListener, ChatterListener
{
    private static final String DR_PANEL_CAPTION_FONT = "DRPanelCaptionFont"; //$NON-NLS-1$
    private static final String DR_PANEL_CAPTION_FONT_COLOR = "DRPanelCaptionFontColor"; //$NON-NLS-1$
    private static final String DR_CATEGORY_FONT = "DRCategoryFont"; //$NON-NLS-1$
    private static final String DR_CATEGORY_FONT_COLOR = "DRCategoryFontColor"; //$NON-NLS-1$
    private static final String FRIENDLY_DR_PANEL_COLOR = "friendlyDRPanelColor"; //$NON-NLS-1$
    private static final String FRIENDLY_DR_CAPTION_COLOR = "friendlyDRCaptionColor"; //$NON-NLS-1$
    private static final String ENEMY_DR_PANEL_COLOR = "enemyDRPanelColor"; //$NON-NLS-1$
    private static final String ENEMY_DR_CAPTION_COLOR = "enemyDRCaptionColor"; //$NON-NLS-1$
    private static final String COLORED_DICE_COLOR_OVER_MAP = "coloredDiceColorOverMap"; //$NON-NLS-1$
    private static final String SINGLE_DIE_COLOR_OVER_MAP = "singleDieColorOverMap"; //$NON-NLS-1$
    private static final String DICE_FILE_NAME_FORMAT = "chatter/BIGDC%s.png";
    private static final String PANEL_FILE_NAME = "chatter/PNL.png";
    private static final String CAPTION_FILE_NAME = "chatter/CAPT.png";
    private static final String AXISSAN = "chatter/AXSAN.png";
    private static final String ALLIEDSAN = "chatter/ALSAN.png";
    private static final String PREFERENCE_TAB = "Dice over the map";
    
    private Font m_objDRPanelCaptionFont = null;
    private Color m_clrDRPanelCaptionFontColor = Color.black;
    private Font m_objDRCategoryFont = null;
    private Color m_clrDRCategoryFontColor = Color.black;
    private Color m_clrColoredDiceColor = Color.yellow;
    private Color m_clrSingleDieColor = Color.red;
    private Color m_clrFriendlyDRPanel = new Color(235, 244, 251);
    private Color m_clrFriendlyDRCaption = new Color(173, 210, 241);
    private Color m_clrEnemyDRPanel = new Color(253, 239, 230);
    private Color m_clrEnemyDRCaption = new Color(251, 214, 192);

    private final BufferedImage [] mar_objWhiteDieImage = new BufferedImage[6];
    private final BufferedImage [] mar_objColoredDieImage = new BufferedImage[6];
    private final BufferedImage [] mar_objSingleDieImage = new BufferedImage[6];
    private BufferedImage m_objFriendlyDRPanel = null;
    private BufferedImage m_objEnemyDRPanel = null;
    private BufferedImage m_objAxisSAN = null;
    private BufferedImage m_objAlliedSAN = null;
    
    private int m_iCaptionWidth = 0;
    private int m_iCaptionHeight = 0;
    
    private final int mc_iMaxNumEntries = 8;
    private long m_lMaxAge = 10;
    private long m_lClock = 0;
    private long m_lCount = 0;

    /**
     * @return the mc_DefaultAge
     */
    public long getMaxAge() {
        return m_lMaxAge;
    }

    /**
     * @param aMc_DefaultAge the mc_DefaultAge to set
     */
    public void setMaxAge(long lMaxAge) 
    {
        if (lMaxAge > 0)
        {
            if (lMaxAge < m_lMaxAge)
                m_lClock += m_lMaxAge - lMaxAge;

            m_lMaxAge = lMaxAge;
        }
    }
    private ArrayList<NeedRepaintEvent> needrepaint_listeners = new ArrayList<NeedRepaintEvent>();
    private ArrayList<DiceRollHandler> mar_DRH = new ArrayList<DiceRollHandler>();
    private Timer m_objClock;
    private boolean m_bRegisteredForDiceEvents = false;
    
    public DiceRollQueueHandler() 
    {
        m_objClock = new Timer (5000, this);
        m_objClock.start();
    }   
    
    public void SetupPreferences()
    {
        RebuildWhiteDiceFaces();
            
        final Prefs l_objModulePrefs = GameModule.getGameModule().getPrefs();

        // **************************************************************************************
        FontConfigurer l_objCaptionFontConfigurer = null;
        FontConfigurer l_objCaptionFontConfigurer_Exist = (FontConfigurer)l_objModulePrefs.getOption(DR_PANEL_CAPTION_FONT);

        if (l_objCaptionFontConfigurer_Exist == null)
        {
            l_objCaptionFontConfigurer = new FontConfigurer(DR_PANEL_CAPTION_FONT, "DR panel caption font: ", 
                                                new Font("SansSerif", Font.PLAIN, 12), new int[]{9, 10, 11, 12, 15, 18, 21, 24, 28, 32}); //$NON-NLS-1$ //$NON-NLS-2$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objCaptionFontConfigurer); //$NON-NLS-1$
        }
        else
            l_objCaptionFontConfigurer = l_objCaptionFontConfigurer_Exist;

        l_objCaptionFontConfigurer.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent evt) 
            {
                m_objDRPanelCaptionFont = (Font) evt.getNewValue();
                RebuildFriendlyPanel();
                RebuildEnemyPanel();
                FireNeedRepaint();
            }
        });

        l_objCaptionFontConfigurer.fireUpdate();    

        // **************************************************************************************
        ColorConfigurer l_objDRPanelCaptionFontColor = null;
        ColorConfigurer l_objDRPanelCaptionFontColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(DR_PANEL_CAPTION_FONT_COLOR);

        if (l_objDRPanelCaptionFontColor_Exist == null)
        {
            l_objDRPanelCaptionFontColor = new ColorConfigurer(DR_PANEL_CAPTION_FONT_COLOR, "DR panel caption font color: ", m_clrDRPanelCaptionFontColor); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objDRPanelCaptionFontColor); //$NON-NLS-1$
        }
        else
            l_objDRPanelCaptionFontColor = l_objDRPanelCaptionFontColor_Exist;

        l_objDRPanelCaptionFontColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrDRPanelCaptionFontColor = (Color) e.getNewValue();
                RebuildFriendlyPanel();
                RebuildEnemyPanel();
                FireNeedRepaint();
            }
        });

        l_objDRPanelCaptionFontColor.fireUpdate();

        // **************************************************************************************
        FontConfigurer l_objDRCategoryFontConfigurer = null;
        FontConfigurer l_objDRCategoryFontConfigurer_Exist = (FontConfigurer)l_objModulePrefs.getOption(DR_CATEGORY_FONT);

        if (l_objDRCategoryFontConfigurer_Exist == null)
        {
            l_objDRCategoryFontConfigurer = new FontConfigurer(DR_CATEGORY_FONT, "DR category font: ",
                                                new Font("SansSerif", Font.PLAIN, 12), new int[]{9, 10, 11, 12, 15, 18, 21, 24, 28, 32}); //$NON-NLS-1$ //$NON-NLS-2$; //$NON-NLS-1$ //$NON-NLS-2$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objDRCategoryFontConfigurer); //$NON-NLS-1$
        }
        else
            l_objDRCategoryFontConfigurer = l_objDRCategoryFontConfigurer_Exist;

        l_objDRCategoryFontConfigurer.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent evt) 
            {
                m_objDRCategoryFont = (Font) evt.getNewValue();
                FireNeedRepaint();
            }
        });

        l_objDRCategoryFontConfigurer.fireUpdate();    

        // **************************************************************************************
        ColorConfigurer l_objDRCategoryFontColor = null;
        ColorConfigurer l_objDRCategoryFontColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(DR_CATEGORY_FONT_COLOR);

        if (l_objDRCategoryFontColor_Exist == null)
        {
            l_objDRCategoryFontColor = new ColorConfigurer(DR_CATEGORY_FONT_COLOR, "DR category font color: ", m_clrDRCategoryFontColor); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objDRCategoryFontColor); //$NON-NLS-1$
        }
        else
            l_objDRCategoryFontColor = l_objDRCategoryFontColor_Exist;

        l_objDRCategoryFontColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrDRCategoryFontColor = (Color) e.getNewValue();
                FireNeedRepaint();
            }
        });

        l_objDRCategoryFontColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objFriendlyDRPanelColor = null;
        ColorConfigurer l_objFriendlyDRPanelColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(FRIENDLY_DR_PANEL_COLOR);

        if (l_objFriendlyDRPanelColor_Exist == null)
        {
            l_objFriendlyDRPanelColor = new ColorConfigurer(FRIENDLY_DR_PANEL_COLOR, "Player's DR panel background color:  ", m_clrFriendlyDRPanel); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objFriendlyDRPanelColor); //$NON-NLS-1$
        }
        else
            l_objFriendlyDRPanelColor = l_objFriendlyDRPanelColor_Exist;

        l_objFriendlyDRPanelColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrFriendlyDRPanel = (Color) e.getNewValue();
                RebuildFriendlyPanel();
                FireNeedRepaint();
            }
        });

        l_objFriendlyDRPanelColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objFriendlyDRCaptionColor = null;
        ColorConfigurer l_objFriendlyDRCaptionColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(FRIENDLY_DR_CAPTION_COLOR);

        if (l_objFriendlyDRCaptionColor_Exist == null)
        {
            l_objFriendlyDRCaptionColor = new ColorConfigurer(FRIENDLY_DR_CAPTION_COLOR, "Player's DR panel caption color:  ", m_clrFriendlyDRCaption); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objFriendlyDRCaptionColor); //$NON-NLS-1$
        }
        else
            l_objFriendlyDRCaptionColor = l_objFriendlyDRCaptionColor_Exist;

        l_objFriendlyDRCaptionColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrFriendlyDRCaption = (Color) e.getNewValue();
                RebuildFriendlyPanel();
                FireNeedRepaint();
            }
        });

        l_objFriendlyDRCaptionColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objEnemyDRPanelColor = null;
        ColorConfigurer l_objEnemyDRPanelColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(ENEMY_DR_PANEL_COLOR);

        if (l_objEnemyDRPanelColor_Exist == null)
        {
            l_objEnemyDRPanelColor = new ColorConfigurer(ENEMY_DR_PANEL_COLOR, "Opponent's DR panel background color:  ", m_clrEnemyDRPanel); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objEnemyDRPanelColor); //$NON-NLS-1$
        }
        else
            l_objEnemyDRPanelColor = l_objEnemyDRPanelColor_Exist;

        l_objEnemyDRPanelColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrEnemyDRPanel = (Color) e.getNewValue();
                RebuildEnemyPanel();
                FireNeedRepaint();
            }
        });

        l_objEnemyDRPanelColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objEnemyDRCaptionColor = null;
        ColorConfigurer l_objEnemyDRCaptionColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(ENEMY_DR_CAPTION_COLOR);

        if (l_objEnemyDRCaptionColor_Exist == null)
        {
            l_objEnemyDRCaptionColor = new ColorConfigurer(ENEMY_DR_CAPTION_COLOR, "Opponent's DR panel caption color:  ", m_clrEnemyDRCaption); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objEnemyDRCaptionColor); //$NON-NLS-1$
        }
        else
            l_objEnemyDRCaptionColor = l_objEnemyDRCaptionColor_Exist;

        l_objEnemyDRCaptionColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrEnemyDRCaption = (Color) e.getNewValue();
                RebuildEnemyPanel();
                FireNeedRepaint();
            }
        });

        l_objEnemyDRCaptionColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objColoredDiceColor = null;
        ColorConfigurer l_objColoredDiceColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(COLORED_DICE_COLOR_OVER_MAP);

        if (l_objColoredDiceColor_Exist == null)
        {
            l_objColoredDiceColor = new ColorConfigurer(COLORED_DICE_COLOR_OVER_MAP, "Colored die color:  ", Color.YELLOW); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objColoredDiceColor); //$NON-NLS-1$
        }
        else
            l_objColoredDiceColor = l_objColoredDiceColor_Exist;

        l_objColoredDiceColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrColoredDiceColor = (Color) e.getNewValue();
                RebuildColoredDiceFaces();
                FireNeedRepaint();
            }
        });

        l_objColoredDiceColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objColoredDieColor = null;
        ColorConfigurer l_objColoredDieColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(SINGLE_DIE_COLOR_OVER_MAP);

        if (l_objColoredDieColor_Exist == null)
        {
            l_objColoredDieColor = new ColorConfigurer(SINGLE_DIE_COLOR_OVER_MAP, "Single die color:  ", Color.RED); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objColoredDieColor); //$NON-NLS-1$
        }
        else
            l_objColoredDieColor = l_objColoredDieColor_Exist;

        l_objColoredDieColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrSingleDieColor = (Color) e.getNewValue();
                RebuildSingleDieFaces();
                FireNeedRepaint();
            }
        });

        l_objColoredDieColor.fireUpdate();
    }
    
    public static String GetFriendlyPlayerNick()
    {
        String l_strReturn = GlobalOptions.getInstance().getPlayerId();
        l_strReturn = (l_strReturn.length() == 0 ? "(" + getAnonymousUserName() + ")" : l_strReturn);
        
        return l_strReturn;
    }
    
    private void DrawCaption(Graphics2D objGraph, String strCaption)
    {
        objGraph.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        objGraph.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        objGraph.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        objGraph.setFont(m_objDRPanelCaptionFont);

        // Get font metrics for the current font
        FontMetrics l_objFM = objGraph.getFontMetrics();

        // Get the position of the leftmost character in the baseline
        // getWidth() and getHeight() returns the width and height of this component
        //int msgX = m_iCaptionWidth / 2 - l_objFM.stringWidth(strNickName) / 2;
        int msgY = m_iCaptionHeight / 2 + l_objFM.getHeight() / 2;

        objGraph.setColor(m_clrDRPanelCaptionFontColor);
        
        objGraph.clipRect(3, 3, m_iCaptionWidth - 3, m_iCaptionHeight);  
        objGraph.drawString(strCaption, 10, msgY);
        objGraph.setClip(null);
    }
    
    private void DrawCategory(Graphics2D objGraph, String strCaption, Rectangle objRect)
    {
        objGraph.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        objGraph.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        objGraph.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        objGraph.setFont(m_objDRCategoryFont);

        // Get font metrics for the current font
        FontMetrics l_objFM = objGraph.getFontMetrics();

        // Get the position of the leftmost character in the baseline
        // getWidth() and getHeight() returns the width and height of this component
        int msgX = objRect.x + objRect.width / 2 - l_objFM.stringWidth(strCaption) / 2;
        int msgY = objRect.y + (objRect.height / 2) + (l_objFM.getAscent()/ 2);
        
        objGraph.setColor(m_clrDRCategoryFontColor);
        
        objGraph.clipRect(objRect.x, objRect.y, objRect.width, objRect.height);  
        objGraph.drawString(strCaption, msgX, msgY);
        objGraph.setClip(null);
    }
    
    private void RebuildFriendlyPanel() 
    {
        BufferedImage l_objImage = null;
        
        try
        {
            // background
            m_objFriendlyDRPanel = ColorChanger.changeColor(Op.load(PANEL_FILE_NAME).getImage(null), Color.red, m_clrFriendlyDRPanel);

            // caption
            l_objImage = ColorChanger.changeColor(Op.load(CAPTION_FILE_NAME).getImage(null), Color.red, m_clrFriendlyDRCaption);
            
            if (m_iCaptionWidth == 0)
                m_iCaptionWidth = l_objImage.getWidth();
            if (m_iCaptionHeight == 0)
                m_iCaptionHeight = l_objImage.getHeight();

            Graphics2D l_objGraph = m_objFriendlyDRPanel.createGraphics();
        
            if (l_objGraph != null)
            {
                l_objGraph.drawImage(l_objImage, 4, 4, null);
                l_objGraph.dispose();
            }
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
    }
    
    private void RebuildEnemyPanel() 
    {
        BufferedImage l_objImage = null;
        
        try
        {
            // background
            m_objEnemyDRPanel = ColorChanger.changeColor(Op.load(PANEL_FILE_NAME).getImage(null), Color.red, m_clrEnemyDRPanel);

            // caption
            l_objImage = ColorChanger.changeColor(Op.load(CAPTION_FILE_NAME).getImage(null), Color.red, m_clrEnemyDRCaption);

            if (m_iCaptionWidth == 0)
                m_iCaptionWidth = l_objImage.getWidth();
            if (m_iCaptionHeight == 0)
                m_iCaptionHeight = l_objImage.getHeight();
            
            Graphics2D l_objGraph = m_objEnemyDRPanel.createGraphics();
        
            if (l_objGraph != null)
            {
                l_objGraph.drawImage(l_objImage, 4, 4, null);

                l_objGraph.dispose();
            }
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
    }
    
    private void RebuildWhiteDiceFaces() 
    {
        try
        {
            m_objAxisSAN = Op.load(AXISSAN).getImage(null);
            m_objAlliedSAN = Op.load(ALLIEDSAN).getImage(null);

            
            for (int l_i = 0; l_i < 6; l_i++)
                mar_objWhiteDieImage[l_i] = Op.load(String.format(DICE_FILE_NAME_FORMAT, String.valueOf(l_i + 1))).getImage(null);
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
    }
    
    private void RebuildColoredDiceFaces() 
    {
        BufferedImage l_objImage = null;

        try
        {
            for (int l_i = 0; l_i < 6; l_i++)
            {
                l_objImage = Op.load(String.format(DICE_FILE_NAME_FORMAT, String.valueOf(l_i + 1))).getImage(null);
                mar_objColoredDieImage[l_i] = ColorChanger.changeColor(l_objImage, Color.white, m_clrColoredDiceColor);
            }
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
    }
    
    private void RebuildSingleDieFaces() 
    {
        BufferedImage l_objImage = null;
        
        try
        {
            for (int l_i = 0; l_i < 6; l_i++)
            {
                l_objImage = Op.load(String.format(DICE_FILE_NAME_FORMAT, String.valueOf(l_i + 1))).getImage(null);
                mar_objSingleDieImage[l_i] = ColorChanger.changeColor(l_objImage, Color.white, m_clrSingleDieColor);
            }
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
    }
    
    private boolean RegisterForDiceEvents(boolean bAdd)
    {
        if ((!m_bRegisteredForDiceEvents) && (bAdd))
        {
            if (GameModule.getGameModule().getChatter() instanceof ASLChatter) 
            {
                if (bAdd) 
                    ((ASLChatter) (GameModule.getGameModule().getChatter())).addListener(this);
                else
                    ((ASLChatter) (GameModule.getGameModule().getChatter())).removeListener(this);

                m_bRegisteredForDiceEvents = bAdd;
                
                return true;
            }
        }
        
        return false;
    }
    
    public void KillAll()
    {
        m_lClock += m_lMaxAge;
        
        ClockTick();
    }
    
    synchronized public void ShowLastDR()
    {
        boolean l_bRepaint = false;
        
        for (Iterator<DiceRollHandler> it = mar_DRH.iterator(); it.hasNext(); )
        {
            DiceRollHandler l_objDRH = it.next();
            
            if (l_objDRH.IsAlive())
            {
                l_objDRH.setClock(m_lClock);
            }
            else
            {
                l_objDRH.setClock(m_lClock);
                l_objDRH.Alive();                
                l_bRepaint = true;
            }
        }
    
        if (l_bRepaint)
            FireNeedRepaint();
    }
    
    synchronized public void ClockTick()
    {
        boolean l_bRepaint = false;
        
        for (Iterator<DiceRollHandler> it = mar_DRH.iterator(); it.hasNext(); )
        {
            DiceRollHandler l_objDRH = it.next();
            
            if (l_objDRH.IsAlive())
            {
                if ((m_lClock - l_objDRH.getClock()) >= m_lMaxAge)
                {
                    l_objDRH.Dead();
                    l_bRepaint = true;
                }
            }
            else
                break;
        }
    
        m_lClock++;
        
        if (l_bRepaint)
            FireNeedRepaint();
    }

    synchronized public void PushDR(String strCategory, String strUser, String strSAN, int iFirstDice, int iSecondDice)
    {
        mar_DRH.add(0, new DiceRollHandler(++m_lCount, m_lClock, strCategory, strUser, strSAN, iFirstDice, iSecondDice));
        
        if (mar_DRH.size() > getMaxNumEntries())
            mar_DRH.subList(getMaxNumEntries(), mar_DRH.size()).clear();
        
        FireNeedRepaint();
    }

    synchronized public void draw(Graphics g, Map map) 
    {
        final Rectangle r = map.getView().getVisibleRect();
        Graphics2D gg = (Graphics2D) g;
        
        for (int l_i = 0; l_i < mar_DRH.size(); l_i++)
        {
            DiceRollHandler l_objDRH = mar_DRH.get(l_i);
            
            if (l_objDRH.IsAlive())
            {
                Point l_objPoint = new Point(r.x + r.width - 190, r.y + r.height - 100 - 100 * l_i);
                
                gg.drawImage(GetDRImage(l_objDRH), l_objPoint.x, l_objPoint.y, null);
                
                if (l_objDRH.isAxisSAN())
                {
                    gg.drawImage(m_objAxisSAN, l_objPoint.x + 133, l_objPoint.y - 15, null);
                }
                else if (l_objDRH.isAlliedSAN())
                {
                    gg.drawImage(m_objAlliedSAN, l_objPoint.x + 133, l_objPoint.y - 15, null);
                }
                else if (l_objDRH.isBothSAN())
                {
                    gg.drawImage(m_objAxisSAN, l_objPoint.x + 90, l_objPoint.y - 15, null);
                    gg.drawImage(m_objAlliedSAN, l_objPoint.x + 133, l_objPoint.y - 15, null);
                }                
            }          
        }
    }
    
    BufferedImage GetDRImage(DiceRollHandler objDRH)
    {
        BufferedImage l_objBackGroundImg;
        
        if (objDRH.isFriendly())
        {
            l_objBackGroundImg = deepCopy(m_objFriendlyDRPanel);

            Graphics2D l_objGraph = l_objBackGroundImg.createGraphics();

            if (l_objGraph != null)
            {
                if (m_objDRPanelCaptionFont != null)
                {
                    String l_strCaption = objDRH.getCount() + ". " + GetFriendlyPlayerNick();
                    
                    DrawCaption(l_objGraph, l_strCaption);
                }
                
                if (objDRH.getSecondDice() != -1)
                {
                    l_objGraph.drawImage(mar_objWhiteDieImage[objDRH.getSecondDice() - 1], 129, 33, null);

                    l_objGraph.drawImage(mar_objColoredDieImage[objDRH.getFirstDice() - 1], 82, 33, null);
                }
                else
                    l_objGraph.drawImage(mar_objSingleDieImage[objDRH.getFirstDice() - 1], 105, 33, null);
                
                if ((objDRH.getCategory() != null) && (!objDRH.getCategory().isEmpty()))
                    DrawCategory(l_objGraph, objDRH.getCategory(), new Rectangle(10, 33, 66, 43));
                
                l_objGraph.dispose();             
            }
        }
        else
        {
            l_objBackGroundImg = deepCopy(m_objEnemyDRPanel);
        
            Graphics2D l_objGraph = l_objBackGroundImg.createGraphics();
        
            if (l_objGraph != null)
            {
                if (m_objDRPanelCaptionFont != null)
                {
                    String l_strCaption = objDRH.getCount() + ". " + objDRH.getNickName();
                    
                    DrawCaption(l_objGraph, l_strCaption);
                }
                
                if (objDRH.getSecondDice() != -1)
                {
                    l_objGraph.drawImage(mar_objWhiteDieImage[objDRH.getSecondDice() - 1], 129, 33, null);

                    l_objGraph.drawImage(mar_objColoredDieImage[objDRH.getFirstDice() - 1], 82, 33, null);
                }
                else
                    l_objGraph.drawImage(mar_objSingleDieImage[objDRH.getFirstDice() - 1], 105, 33, null);
                
                if ((objDRH.getCategory() != null) && (!objDRH.getCategory().isEmpty()))
                    DrawCategory(l_objGraph, objDRH.getCategory(), new Rectangle(10, 33, 66, 43));
                
                l_objGraph.dispose();             
            }
        }
        
        return l_objBackGroundImg;
    }
    
    static BufferedImage deepCopy(BufferedImage objInputImage) 
    {
        return new BufferedImage(objInputImage.getColorModel(), objInputImage.copyData(null), objInputImage.getColorModel().isAlphaPremultiplied(), null);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) 
    {
        if (RegisterForDiceEvents(true)) 
        {
            m_objClock.stop();
            m_objClock.setInitialDelay(1000);
            m_objClock.setDelay(1000);
            m_objClock.restart();
        }
        else
        {    
            ClockTick();
        }
    }

    public void DiceRoll(String strCategory, String strUser, String strSAN, int iFirstDice, int iSecondDice) {
        PushDR(strCategory, strUser, strSAN, iFirstDice, iSecondDice);
    }
    
    public void addRepaintListener(NeedRepaintEvent toAdd) {
        needrepaint_listeners.add(toAdd);
    }
  
    public void removeRepaintListener(NeedRepaintEvent toRemove) {
        needrepaint_listeners.remove(toRemove);
    }
    
    private void FireNeedRepaint() {
        for (NeedRepaintEvent objListener : needrepaint_listeners)
            objListener.NeedRepaint();
    }      

    /**
     * @return the mc_iMaxNumEntries
     */
    public int getMaxNumEntries() {
        return mc_iMaxNumEntries;
    }
}

/**
 * This component highlights a spot on the board.
 * It's handy when you need to draw your opponent's attention to a piece you are rallying, moving, etc.
 */
public class ASLDiceOverlay extends AbstractConfigurable implements GameComponent, Drawable, NeedRepaintEvent {

    private ASLMap m_objASLMap;
    private JToolBar m_Toolbar = null;
    private DiceRollQueueHandler m_objDRQH = null;
  
    // this component is not configurable
    @Override
    public Class<?>[] getAttributeTypes() {return new Class<?>[] {};}

    @Override
    public String[] getAttributeNames() {return new String[0];}

    @Override
    public String[] getAttributeDescriptions() {return new String[0];}

    @Override
    public String getAttributeValueString(String key) {return null;}

    @Override
    public void setAttribute(String key, Object value) {}

    @Override
    public void addTo(Buildable parent) {

        // add this component to the game and register a mouse listener
        if (parent instanceof ASLMap) 
        {
            m_objASLMap = (ASLMap) parent;
            m_objASLMap.addDrawComponent(this);
            
            m_objASLMap.getPopupMenu().addSeparator();
            JMenuItem l_Toggletoolbar = new JMenuItem("Dice-over-map toolbar");
            l_Toggletoolbar.setBackground(new Color(255,255,255));
            m_objASLMap.getPopupMenu().add(l_Toggletoolbar);
            m_objASLMap.getPopupMenu().addSeparator();

            // button toolbar activation
            JCheckBoxMenuItem l_objToolbarVisibleChange = new JCheckBoxMenuItem("Toolbar activation (on/off)");

            l_objToolbarVisibleChange.addActionListener(new ActionListener() 
            {
                public void actionPerformed(ActionEvent evt) 
                {
                    boolean l_bVisible = true;
                    
                    if (m_Toolbar != null)
                        l_bVisible = !m_Toolbar.isVisible();
                    
                    CreateToolbar(l_bVisible);
                }
            });
            
            m_objASLMap.getPopupMenu().add(l_objToolbarVisibleChange);            
        
            m_objDRQH = new DiceRollQueueHandler();
            m_objDRQH.addRepaintListener(this);
            
            m_objDRQH.SetupPreferences();            
        }
    }

    @Override
    public void draw(Graphics g, Map map) 
    {
        if ((m_Toolbar != null) && (m_Toolbar.isVisible()))
            m_objDRQH.draw(g, map);
    }

    @Override
    public boolean drawAboveCounters() {return true;}

    @Override
    public void removeFrom(Buildable parent) {}

    @Override
    public HelpFile getHelpFile() {return null;}

    @Override
    public Class[] getAllowableConfigureComponents() {return new Class[0];}

    @Override
    public void setup(boolean gameStarting) {}

    @Override
    public Command getRestoreCommand() {return null;}
    
    public void CreateToolbar(boolean bVisible)
    {
        if (m_Toolbar == null)
        {
            final int iW = 32;
            int l_iRow = 0;
            JButton l_objBtn;
            
            m_Toolbar = new JToolBar(SwingConstants.VERTICAL);
            m_Toolbar.setVisible(false);
            m_Toolbar.setFloatable(false);
            m_Toolbar.setMargin(new Insets(0,0,0,0));

            JPanel l_objPanel = new JPanel();
            m_Toolbar.add(l_objPanel);
            GridBagLayout l_objGBL = new GridBagLayout();
            l_objGBL.columnWidths = new int[] {iW};
            l_objGBL.rowHeights = new int[] {0, iW};
            l_objGBL.columnWeights = new double[]{0.0};
            l_objGBL.rowWeights = new double[]{1.0, 0.0, 0.0};
            l_objPanel.setLayout(l_objGBL);
		
            Component l_objVertGlue = Box.createVerticalGlue();
            AddButton(l_objPanel, l_objVertGlue, l_iRow++, 2);
            
            JToggleButton l_objTBtn = CreateKeepAliveButton("chatter/REWIND.png", "chatter/CLEAR.png", "", "Keep DRs on the screen");
            AddButton(l_objPanel, l_objTBtn, l_iRow++, 20);
            
            l_objBtn = CreateActionButton("chatter/CLEAR.png", "", "Clear the screen from DRs", new ActionListener() {public void actionPerformed(ActionEvent e) {m_objDRQH.KillAll();} });
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);

            l_objBtn = CreateActionButton("chatter/REWIND.png", "", String.format("Show last %s DRs", m_objDRQH.getMaxNumEntries()), new ActionListener() {public void actionPerformed(ActionEvent e) {m_objDRQH.ShowLastDR();} });
            AddButton(l_objPanel, l_objBtn, l_iRow++, 20);

            l_objBtn = CreateDiceButton("DRs.gif", "", "DR", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), true, ASLDiceBot.OTHER_CATEGORY);
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);
                
            l_objBtn = CreateDiceButton("", "IFT", "IFT attack DR", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "IFT");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);
            
            l_objBtn = CreateDiceButton("", "TH", "To Hit DR", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "TH");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);
                
            l_objBtn = CreateDiceButton("", "TK", "To Kill DR", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "TK");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);
            
            l_objBtn = CreateDiceButton("", "MC", "Morale Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "MC");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);
            
            l_objBtn = CreateDiceButton("", "R", "Rally DR", KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "Rally");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);
            
            l_objBtn = CreateDiceButton("", "CC", "Close Combat DR", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "CC");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);

            l_objBtn = CreateDiceButton("", "TC", "Task Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "TC");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 10);

            l_objBtn = CreateDiceButton("dr.gif", "", "dr", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), false, ASLDiceBot.OTHER_CATEGORY);
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);

            l_objBtn = CreateDiceButton("", "SA", "Sniper Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), false, "SA");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);

            l_objBtn = CreateDiceButton("", "RS", "Random Selection dr", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), false, "RS");
            AddButton(l_objPanel, l_objBtn, l_iRow++, 2);
            
            SwingUtilities.getWindowAncestor(m_objASLMap.getLayeredPane()).add(m_Toolbar, BorderLayout.EAST);                
        }
        
        m_Toolbar.setVisible(bVisible);
        
        m_objASLMap.getView().revalidate();
        m_Toolbar.revalidate();
    }
    
    private void AddButton(JPanel objPanel, Component objComp, int iRow, int iGap)
    {
        GridBagConstraints l_objGBL_Btn;
        
        l_objGBL_Btn = new GridBagConstraints();
        l_objGBL_Btn.fill = GridBagConstraints.BOTH;
        l_objGBL_Btn.insets = new Insets(0, 0, iGap, 0);
        l_objGBL_Btn.gridx = 0;
        l_objGBL_Btn.gridy = iRow;
        
        objPanel.add(objComp, l_objGBL_Btn);
    }
    
    private JToggleButton CreateKeepAliveButton(String strImageOff, String strImageOn, String strCaption, String strTooltip) 
    {
        JToggleButton l_btn = new JToggleButton(strCaption);
        
        l_btn.setMargin(new Insets(0, 0, 0, 0));       
        l_btn.setMaximumSize(new Dimension(32, 32));
        l_btn.setMinimumSize(new Dimension(10, 10));
        l_btn.setPreferredSize(new Dimension(32, 32));
        l_btn.setFocusable(false);
        l_btn.setRolloverEnabled(false);
        
        try
        {
            if (!strImageOff.isEmpty())
                l_btn.setIcon(new ImageIcon(Op.load(strImageOff).getImage(null)));

            if (!strImageOn.isEmpty())
                l_btn.setSelectedIcon(new ImageIcon(Op.load(strImageOn).getImage(null)));
        }
        catch (Exception ex)
        {
        }
        
        ItemListener l_objIL = new ItemListener()
        {
            public void itemStateChanged(ItemEvent e) 
            {
                if(e.getStateChange() == ItemEvent.SELECTED)
                {
//                    totalGUI.setBackground(Color.green);
                }
                else
                {
//                    totalGUI.setBackground(Color.red);
                } 
            }
        };
            
        l_btn.addItemListener(l_objIL);
        AddHotKeyToTooltip(l_btn, null, strTooltip);
        l_btn.setFocusable(false);
       
        return l_btn;

    }
    
    private JButton CreateActionButton(String strImage, String strCaption, String strTooltip, ActionListener objList) 
    {
        JButton l_btn = new JButton(strCaption);
        
        l_btn.setMargin(new Insets(0, 0, 0, 0));       
        l_btn.setMaximumSize(new Dimension(32, 32));
        l_btn.setMinimumSize(new Dimension(10, 10));
        l_btn.setPreferredSize(new Dimension(32, 32));
        l_btn.setFocusable(false);
        l_btn.setRolloverEnabled(false);
        
        try
        {
            if (!strImage.isEmpty())
                l_btn.setIcon(new ImageIcon(Op.load(strImage).getImage(null)));
        }
        catch (Exception ex)
        {
        }
        
        l_btn.addActionListener(objList);
        AddHotKeyToTooltip(l_btn, null, strTooltip);
        l_btn.setFocusable(false);
       
        return l_btn;
    }
    
    private JButton CreateDiceButton(String strImage, String strCaption, String strTooltip, KeyStroke keyStroke, final boolean bDice, final String strCat) 
    {
        JButton l_btn = new JButton(strCaption);
        
        l_btn.setMargin(new Insets(0, 0, 0, 0));       
        l_btn.setMaximumSize(new Dimension(32, 32));
        l_btn.setMinimumSize(new Dimension(10, 10));
        l_btn.setPreferredSize(new Dimension(32, 32));
        l_btn.setFocusable(false);
        l_btn.setRolloverEnabled(false);
        
        try
        {
            if (!strImage.isEmpty())
                l_btn.setIcon(new ImageIcon(Op.load(strImage).getImage(null)));
        }
        catch (Exception ex)
        {
        }
        
        ActionListener l_objAL = new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    ASLDiceBot l_objDice = (ASLDiceBot) GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();

                    if (l_objDice != null)
                    {
                        if (bDice)
                            l_objDice.DR(strCat);
                        else
                            l_objDice.dr(strCat);
                    }
                }
                catch (Exception ex)
                {
                }
            }
        };
        
        l_btn.addActionListener(l_objAL);
        AddHotKeyToTooltip(l_btn, keyStroke, strTooltip);
        l_btn.setFocusable(false);
       
        return l_btn;
    }
    
    private void AddHotKeyToTooltip(JComponent objButton, KeyStroke keyStroke, String strTooltipText) 
    {
        if (keyStroke != null)
            objButton.setToolTipText(strTooltipText + " [" + HotKeyConfigurer.getString(keyStroke) + "]");
        else
            objButton.setToolTipText(strTooltipText);
    }

    public void NeedRepaint() 
    {
        if (m_objASLMap != null)
            m_objASLMap.repaint();
    }
}

class ColorChanger 
{
    public static final int ALPHA = 0;
    public static final int RED = 1;
    public static final int GREEN = 2;
    public static final int BLUE = 3;

    public static final int HUE = 0;
    public static final int SATURATION = 1;
    public static final int BRIGHTNESS = 2;

    public static final int TRANSPARENT = 0;

    public static BufferedImage changeColor(BufferedImage image, Color mask, Color replacement) {
        BufferedImage destImage = new BufferedImage(image.getWidth(),
            image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = destImage.createGraphics();
        g.drawImage(image, null, 0, 0);
        g.dispose();

        for (int i = 0; i < destImage.getWidth(); i++) {
            for (int j = 0; j < destImage.getHeight(); j++) {

                int destRGB = destImage.getRGB(i, j);

                if (matches(mask.getRGB(), destRGB)) {
                    int rgbnew = getNewPixelRGB(replacement.getRGB(), destRGB);
                    destImage.setRGB(i, j, rgbnew);
                }
            }
        }

        return destImage;
    }

    private static int getNewPixelRGB(int replacement, int destRGB) {
        float[] destHSB = getHSBArray(destRGB);
        float[] replHSB = getHSBArray(replacement);

        int rgbnew = Color.HSBtoRGB(replHSB[HUE],
            replHSB[SATURATION], destHSB[BRIGHTNESS]);
        return rgbnew;
    }

    private static boolean matches(int maskRGB, int destRGB) {
        float[] hsbMask = getHSBArray(maskRGB);
        float[] hsbDest = getHSBArray(destRGB);

        if (hsbMask[HUE] == hsbDest[HUE]
            && hsbMask[SATURATION] == hsbDest[SATURATION]
            && getRGBArray(destRGB)[ALPHA] != TRANSPARENT) {

            return true;
        }
        return false;
    }

    private static int[] getRGBArray(int rgb) {
        return new int[]{(rgb >> 24) & 0xff, (rgb >> 16) & 0xff,
            (rgb >> 8) & 0xff, rgb & 0xff};
    }

    private static float[] getHSBArray(int rgb) {
        int[] rgbArr = getRGBArray(rgb);
        return Color.RGBtoHSB(rgbArr[RED], rgbArr[GREEN], rgbArr[BLUE], null);
    }
}