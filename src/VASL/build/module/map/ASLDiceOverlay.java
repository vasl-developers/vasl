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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;

import VASL.build.module.ASLChatter;
import VASL.build.module.ASLChatter.ChatterListener;
import VASL.build.module.ASLDiceBot;
import VASL.build.module.ASLMap;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.configure.ColorConfigurer;
import VASSAL.configure.FontConfigurer;
import VASSAL.configure.HotKeyConfigurer;
import VASSAL.configure.LongConfigurer;
import VASSAL.configure.StringConfigurer;
import VASSAL.preferences.Prefs;
import VASSAL.tools.imageop.Op;
import VASSAL.tools.swing.SwingUtils;

import static VASSAL.build.module.Chatter.getAnonymousUserName;

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
    private int m_iFirstDie;
    private int m_iSecondDie;
    private boolean m_bAxisSAN;
    private boolean m_bAlliedSAN;

    public DiceRollHandler(long lCount, long lClock, String strCategory, String strNickName, String strSAN, int iFirstDie, int iSecondDie)
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
        m_iFirstDie = iFirstDie;
        m_iSecondDie = iSecondDie;

        if (strSAN == null || strSAN.isEmpty())
        {
            m_bAxisSAN = false;
            m_bAlliedSAN = false;
        }
        else if (strSAN.compareToIgnoreCase("Axis SAN") == 0)
        {
            m_bAxisSAN = true;
            m_bAlliedSAN = false;
        }
        else if (strSAN.compareToIgnoreCase("Allied SAN") == 0)
        {
            m_bAxisSAN = false;
            m_bAlliedSAN = true;
        }
        else if (strSAN.compareToIgnoreCase("Axis/Allied SAN") == 0)
        {
            m_bAxisSAN = true;
            m_bAlliedSAN = true;
        }
        else
        {
            m_bAxisSAN = false;
            m_bAlliedSAN = false;
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
     * @return the m_iFirstDie
     */
    public int getFirstDie() {
        return m_iFirstDie;
    }

    /**
     * @return the m_iSecondDie
     */
    public int getSecondDie() {
        return m_iSecondDie;
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
        return m_bAxisSAN && !m_bAlliedSAN;
    }

    /**
     * @return the m_bAlliedSAN
     */
    public boolean isAlliedSAN() {
        return m_bAlliedSAN && !m_bAxisSAN;
    }

    /**
     * @return the m_bBothSAN
     */
    public boolean isBothSAN() {
        return m_bAlliedSAN && m_bAxisSAN;
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
    private static final String DR_SECONDS_LIFE = "DRPersistenceOnScreen";
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
    private boolean m_bKeepAlive = false;

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
    private Timer m_objClock = null;
    private boolean m_bRegisteredForDiceEvents = false;

    public DiceRollQueueHandler()
    {
    }

    public void SetupPreferences()
    {
        RebuildWhiteDiceFaces();

        final Prefs l_objModulePrefs = GameModule.getGameModule().getPrefs();

        // **************************************************************************************
        FontConfigurer l_objCaptionFontConfigurer = (FontConfigurer)l_objModulePrefs.getOption(DR_PANEL_CAPTION_FONT);

        if (l_objCaptionFontConfigurer == null)
        {
            l_objCaptionFontConfigurer = new FontConfigurer(DR_PANEL_CAPTION_FONT, "DR panel caption font: ",
                                                new Font("SansSerif", Font.PLAIN, 12), new int[]{9, 10, 11, 12, 15, 18, 21, 24, 28, 32}); //$NON-NLS-1$ //$NON-NLS-2$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objCaptionFontConfigurer); //$NON-NLS-1$
        }

        l_objCaptionFontConfigurer.addPropertyChangeListener(e -> {
            m_objDRPanelCaptionFont = (Font) e.getNewValue();
            RebuildFriendlyPanel();
            RebuildEnemyPanel();
            FireNeedRepaint();
        });

        l_objCaptionFontConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objDRPanelCaptionFontColor = (ColorConfigurer)l_objModulePrefs.getOption(DR_PANEL_CAPTION_FONT_COLOR);

        if (l_objDRPanelCaptionFontColor == null)
        {
            l_objDRPanelCaptionFontColor = new ColorConfigurer(DR_PANEL_CAPTION_FONT_COLOR, "DR panel caption font color: ", m_clrDRPanelCaptionFontColor); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objDRPanelCaptionFontColor); //$NON-NLS-1$
        }

        l_objDRPanelCaptionFontColor.addPropertyChangeListener(e -> {
            m_clrDRPanelCaptionFontColor = (Color) e.getNewValue();
            RebuildFriendlyPanel();
            RebuildEnemyPanel();
            FireNeedRepaint();
        });

        l_objDRPanelCaptionFontColor.fireUpdate();

        // **************************************************************************************
        FontConfigurer l_objDRCategoryFontConfigurer = (FontConfigurer)l_objModulePrefs.getOption(DR_CATEGORY_FONT);

        if (l_objDRCategoryFontConfigurer == null)
        {
            l_objDRCategoryFontConfigurer = new FontConfigurer(DR_CATEGORY_FONT, "DR category font: ",
                                                new Font("SansSerif", Font.PLAIN, 12), new int[]{9, 10, 11, 12, 15, 18, 21, 24, 28, 32}); //$NON-NLS-1$ //$NON-NLS-2$; //$NON-NLS-1$ //$NON-NLS-2$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objDRCategoryFontConfigurer); //$NON-NLS-1$
        }

        l_objDRCategoryFontConfigurer.addPropertyChangeListener(e -> {
            m_objDRCategoryFont = (Font) e.getNewValue();
            FireNeedRepaint();
        });

        l_objDRCategoryFontConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objDRCategoryFontColor = (ColorConfigurer)l_objModulePrefs.getOption(DR_CATEGORY_FONT_COLOR);

        if (l_objDRCategoryFontColor == null)
        {
            l_objDRCategoryFontColor = new ColorConfigurer(DR_CATEGORY_FONT_COLOR, "DR category font color: ", m_clrDRCategoryFontColor); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objDRCategoryFontColor); //$NON-NLS-1$
        }

        l_objDRCategoryFontColor.addPropertyChangeListener(e -> {
            m_clrDRCategoryFontColor = (Color) e.getNewValue();
            FireNeedRepaint();
        });

        l_objDRCategoryFontColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objFriendlyDRPanelColor = (ColorConfigurer)l_objModulePrefs.getOption(FRIENDLY_DR_PANEL_COLOR);

        if (l_objFriendlyDRPanelColor == null)
        {
            l_objFriendlyDRPanelColor = new ColorConfigurer(FRIENDLY_DR_PANEL_COLOR, "Player's DR panel background color:  ", m_clrFriendlyDRPanel); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objFriendlyDRPanelColor); //$NON-NLS-1$
        }

        l_objFriendlyDRPanelColor.addPropertyChangeListener(e -> {
            m_clrFriendlyDRPanel = (Color) e.getNewValue();
            RebuildFriendlyPanel();
            FireNeedRepaint();
        });

        l_objFriendlyDRPanelColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objFriendlyDRCaptionColor = (ColorConfigurer)l_objModulePrefs.getOption(FRIENDLY_DR_CAPTION_COLOR);

        if (l_objFriendlyDRCaptionColor == null)
        {
            l_objFriendlyDRCaptionColor = new ColorConfigurer(FRIENDLY_DR_CAPTION_COLOR, "Player's DR panel caption color:  ", m_clrFriendlyDRCaption); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objFriendlyDRCaptionColor); //$NON-NLS-1$
        }

        l_objFriendlyDRCaptionColor.addPropertyChangeListener(e -> {
            m_clrFriendlyDRCaption = (Color) e.getNewValue();
            RebuildFriendlyPanel();
            FireNeedRepaint();
        });

        l_objFriendlyDRCaptionColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objEnemyDRPanelColor = (ColorConfigurer)l_objModulePrefs.getOption(ENEMY_DR_PANEL_COLOR);

        if (l_objEnemyDRPanelColor == null)
        {
            l_objEnemyDRPanelColor = new ColorConfigurer(ENEMY_DR_PANEL_COLOR, "Opponent's DR panel background color:  ", m_clrEnemyDRPanel); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objEnemyDRPanelColor); //$NON-NLS-1$
        }

        l_objEnemyDRPanelColor.addPropertyChangeListener(e -> {
            m_clrEnemyDRPanel = (Color) e.getNewValue();
            RebuildEnemyPanel();
            FireNeedRepaint();
        });

        l_objEnemyDRPanelColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objEnemyDRCaptionColor = (ColorConfigurer)l_objModulePrefs.getOption(ENEMY_DR_CAPTION_COLOR);

        if (l_objEnemyDRCaptionColor == null)
        {
            l_objEnemyDRCaptionColor = new ColorConfigurer(ENEMY_DR_CAPTION_COLOR, "Opponent's DR panel caption color:  ", m_clrEnemyDRCaption); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objEnemyDRCaptionColor); //$NON-NLS-1$
        }

        l_objEnemyDRCaptionColor.addPropertyChangeListener(e -> {
            m_clrEnemyDRCaption = (Color) e.getNewValue();
            RebuildEnemyPanel();
            FireNeedRepaint();
        });

        l_objEnemyDRCaptionColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objColoredDiceColor = (ColorConfigurer)l_objModulePrefs.getOption(COLORED_DICE_COLOR_OVER_MAP);

        if (l_objColoredDiceColor == null)
        {
            l_objColoredDiceColor = new ColorConfigurer(COLORED_DICE_COLOR_OVER_MAP, "Colored die color:  ", Color.YELLOW); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objColoredDiceColor); //$NON-NLS-1$
        }

        l_objColoredDiceColor.addPropertyChangeListener(e -> {
            m_clrColoredDiceColor = (Color) e.getNewValue();
            RebuildColoredDiceFaces();
            FireNeedRepaint();
        });

        l_objColoredDiceColor.fireUpdate();

        // **************************************************************************************
        ColorConfigurer l_objColoredDieColor = (ColorConfigurer)l_objModulePrefs.getOption(SINGLE_DIE_COLOR_OVER_MAP);

        if (l_objColoredDieColor == null)
        {
            l_objColoredDieColor = new ColorConfigurer(SINGLE_DIE_COLOR_OVER_MAP, "Single die color:  ", Color.RED); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objColoredDieColor); //$NON-NLS-1$
        }

        l_objColoredDieColor.addPropertyChangeListener(e -> {
            m_clrSingleDieColor = (Color) e.getNewValue();
            RebuildSingleDieFaces();
            FireNeedRepaint();
        });

        l_objColoredDieColor.fireUpdate();

        // **************************************************************************************
        LongConfigurer l_objDRSecondsLifeNum = (LongConfigurer)l_objModulePrefs.getOption(DR_SECONDS_LIFE);

        if (l_objDRSecondsLifeNum == null)
        {
            l_objDRSecondsLifeNum = new LongConfigurer(DR_SECONDS_LIFE, "DR persistence on the screen (seconds):  ", 10L); //$NON-NLS-1$
            l_objModulePrefs.addOption(PREFERENCE_TAB, l_objDRSecondsLifeNum); //$NON-NLS-1$
        }

        l_objDRSecondsLifeNum.addPropertyChangeListener(e -> {
            setMaxAge((Long) e.getNewValue());
        });

        l_objDRSecondsLifeNum.fireUpdate();
    }

    public static String GetFriendlyPlayerNick()
    {
        String l_strReturn = GlobalOptions.getInstance().getPlayerId();
        l_strReturn = (l_strReturn.length() == 0 ? "(" + getAnonymousUserName() + ")" : l_strReturn);

        return l_strReturn;
    }

    private void DrawCaption(Graphics2D g, String strCaption)
    {
        g.setFont(m_objDRPanelCaptionFont);

        // Get font metrics for the current font
        FontMetrics l_objFM = g.getFontMetrics();

        // Get the position of the leftmost character in the baseline
        // getWidth() and getHeight() returns the width and height of this component
        //int msgX = m_iCaptionWidth / 2 - l_objFM.stringWidth(strNickName) / 2;
        int msgY = m_iCaptionHeight / 2 + l_objFM.getHeight() / 2;

        g.setColor(m_clrDRPanelCaptionFontColor);

        final Shape old_clip = g.getClip();
        g.clipRect(3, 3, m_iCaptionWidth - 3, m_iCaptionHeight);
        g.drawString(strCaption, 10, msgY);
        g.setClip(old_clip);
    }

    private void DrawCategory(Graphics2D g, String strCaption, Rectangle objRect)
    {
        g.setFont(m_objDRCategoryFont);

        // Get font metrics for the current font
        FontMetrics l_objFM = g.getFontMetrics();

        // Get the position of the leftmost character in the baseline
        // getWidth() and getHeight() returns the width and height of this component
        int msgX = objRect.x + objRect.width / 2 - l_objFM.stringWidth(strCaption) / 2;
        int msgY = objRect.y + (objRect.height / 2) + (l_objFM.getAscent()/ 2);

        g.setColor(m_clrDRCategoryFontColor);

        final Shape old_clip = g.getClip();
        g.clipRect(objRect.x, objRect.y, objRect.width, objRect.height);
        g.drawString(strCaption, msgX, msgY);
        g.setClip(old_clip);
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

            Graphics2D g = m_objFriendlyDRPanel.createGraphics();
            g.drawImage(l_objImage, 4, 4, null);
            g.dispose();
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

            Graphics2D g = m_objEnemyDRPanel.createGraphics();
            g.drawImage(l_objImage, 4, 4, null);
            g.dispose();
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

    public boolean RegisterForDiceEvents(boolean bAdd)
    {
        if (!m_bRegisteredForDiceEvents && bAdd)
        {
            if (GameModule.getGameModule().getChatter() instanceof ASLChatter)
            {
                if (bAdd)
                {
                    ((ASLChatter) (GameModule.getGameModule().getChatter())).addListener(this);

                    if (m_objClock == null)
                    {
                        m_objClock = new Timer(1000, this);
                        m_objClock.start();
                    }
                    else
                    {
                        m_objClock.stop();
                        m_objClock.setInitialDelay(1000);
                        m_objClock.setDelay(1000);
                        m_objClock.restart();
                    }
                }
                else
                {
                    ((ASLChatter) (GameModule.getGameModule().getChatter())).removeListener(this);

                    if (m_objClock != null)
                    {
                        m_objClock.stop();
                        m_objClock = null;
                    }
                }

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

        for (DiceRollHandler l_objDRH : mar_DRH)
        {
            l_objDRH.setClock(m_lClock);
            if (!l_objDRH.IsAlive())
            {
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

        for (DiceRollHandler l_objDRH : mar_DRH)
        {
            if (l_objDRH.IsAlive())
            {
                if (m_lClock - l_objDRH.getClock() >= m_lMaxAge)
                {
                    l_objDRH.Dead();
                    l_bRepaint = true;
                }
            }
            else
                break;
        }

        if (!m_bKeepAlive)
            m_lClock++;

        if (l_bRepaint)
            FireNeedRepaint();
    }

    synchronized public void PushDR(String strCategory, String strUser, String strSAN, int iFirstDie, int iSecondDie)
    {
        mar_DRH.add(0, new DiceRollHandler(++m_lCount, m_lClock, strCategory, strUser, strSAN, iFirstDie, iSecondDie));

        if (mar_DRH.size() > getMaxNumEntries())
            mar_DRH.subList(getMaxNumEntries(), mar_DRH.size()).clear();

        FireNeedRepaint();
    }

    private void drawDRPanel(Graphics2D g, Rectangle r, ToolBarPosition enToolbarPosition, DiceRollHandler drh, int i) {
        Point l_objPoint = new Point(r.x + (enToolbarPosition == ToolBarPosition.TP_EAST ? r.width - 190 : 10), r.y + r.height - 100 - 100 * i);

        g.translate(l_objPoint.x, l_objPoint.y);

        // draw the background
        g.drawImage(drh.isFriendly() ? m_objFriendlyDRPanel : m_objEnemyDRPanel, 0, 0, null);

        // draw the caption
        if (m_objDRPanelCaptionFont != null) {
             DrawCaption(g, drh.getCount() + ". " + (drh.isFriendly() ? GetFriendlyPlayerNick() : drh.getNickName()));
        }

        // draw the dice
        if (drh.getSecondDie() != -1)
        {
            g.drawImage(mar_objWhiteDieImage[drh.getSecondDie() - 1], 129, 33, null);
            g.drawImage(mar_objColoredDieImage[drh.getFirstDie() - 1], 82, 33, null);
        }
        else {
            g.drawImage(mar_objSingleDieImage[drh.getFirstDie() - 1], 105, 33, null);
        }

        if (drh.getCategory() != null && !drh.getCategory().isEmpty()) {
            DrawCategory(g, drh.getCategory(), new Rectangle(10, 33, 66, 43));
        }

        g.translate(-l_objPoint.x, -l_objPoint.y);

        if (drh.isAxisSAN())
        {
            g.drawImage(m_objAxisSAN, l_objPoint.x + 133, l_objPoint.y - 15, null);
        }
        else if (drh.isAlliedSAN())
        {
            g.drawImage(m_objAlliedSAN, l_objPoint.x + 133, l_objPoint.y - 15, null);
        }
        else if (drh.isBothSAN())
        {
            g.drawImage(m_objAxisSAN, l_objPoint.x + 90, l_objPoint.y - 15, null);
            g.drawImage(m_objAlliedSAN, l_objPoint.x + 133, l_objPoint.y - 15, null);
        }
    }

    synchronized public void draw(Graphics2D g, Map map, ToolBarPosition enToolbarPosition)
    {
        final double os_scale = g.getDeviceConfiguration().getDefaultTransform().getScaleX();
        final AffineTransform orig_t = g.getTransform();
        final AffineTransform new_t = new AffineTransform(orig_t);
        new_t.scale(os_scale, os_scale);
        g.setTransform(new_t);

        final Rectangle r = map.getView().getVisibleRect();

        for (int l_i = 0; l_i < mar_DRH.size(); l_i++)
        {
            DiceRollHandler l_objDRH = mar_DRH.get(l_i);
            if (l_objDRH.IsAlive())
            {
                drawDRPanel(g, r, enToolbarPosition, l_objDRH, l_i);
            }
        }

        g.setTransform(orig_t);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        ClockTick();
    }

    public void DiceRoll(String strCategory, String strUser, String strSAN, int iFirstDie, int iSecondDie) {
        PushDR(strCategory, strUser, strSAN, iFirstDie, iSecondDie);
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

    /**
     * @return the m_bKeepAlive
     */
    public boolean isKeepAlive() {
        return m_bKeepAlive;
    }

    /**
     * @param m_bKeepAlive the m_bKeepAlive to set
     */
    public void setKeepAlive(boolean m_bKeepAlive) {
        this.m_bKeepAlive = m_bKeepAlive;
    }
}

enum ToolBarPosition {TP_EAST, TP_WEST};

/**
 * This component highlights a spot on the board.
 * It's handy when you need to draw your opponent's attention to a piece you are rallying, moving, etc.
 */
public class ASLDiceOverlay extends AbstractConfigurable implements GameComponent, Drawable, NeedRepaintEvent {

    private ASLMap m_objASLMap;
    private JToolBar m_Toolbar = null;
    private DiceRollQueueHandler m_objDRQH = null;
    private ToolBarPosition m_enToolbarPosition = ToolBarPosition.TP_EAST;
    private boolean m_bToolbarActive = false;
    private final String DICEOVERLAYTOOLBARPOS = "DiceOverlayToolbarPos";
    private final String DICEOVERLAYTOOLBARACTIVE = "DiceOverlayToolbarActive";

    // this component is not configurable
    @Override
    public Class<?>[] getAttributeTypes() {return new Class<?>[0];}

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
        final Prefs prefs = GameModule.getGameModule().getPrefs();

        if (prefs.getOption(DICEOVERLAYTOOLBARPOS) == null)
            prefs.addOption(null, new StringConfigurer(DICEOVERLAYTOOLBARPOS, null));

        if (prefs.getOption(DICEOVERLAYTOOLBARACTIVE) == null)
            prefs.addOption(null, new StringConfigurer(DICEOVERLAYTOOLBARACTIVE, null));

        readToolbarPos();
        readToolbarActive();

        // add this component to the game and register a mouse listener
        if (parent instanceof ASLMap)
        {
            m_objASLMap = (ASLMap) parent;
            m_objASLMap.addDrawComponent(this);

            final JPopupMenu pm = m_objASLMap.getPopupMenu();

            pm.addSeparator();
            JMenuItem l_Toggletoolbar = new JMenuItem("Dice-over-the-map toolbar");
            l_Toggletoolbar.setBackground(new Color(255,255,255));
            pm.add(l_Toggletoolbar);
            pm.addSeparator();

            // button toolbar activation
            JCheckBoxMenuItem l_objToolbarVisibleChange = new JCheckBoxMenuItem("Toolbar activation (on/off)");

            l_objToolbarVisibleChange.setSelected(m_bToolbarActive);

            l_objToolbarVisibleChange.addActionListener(e -> {
                m_bToolbarActive = !m_bToolbarActive;
                CreateToolbar();
                saveToolbarActive();
            });

            pm.add(l_objToolbarVisibleChange);

            m_objDRQH = new DiceRollQueueHandler();
            m_objDRQH.addRepaintListener(this);

            m_objDRQH.SetupPreferences();
        }

        GameModule.getGameModule().getGameState().addGameComponent(this);
    }

    public void readToolbarPos()
    {
        String l_strPref = (String)GameModule.getGameModule().getPrefs().getValue(DICEOVERLAYTOOLBARPOS);
        if (l_strPref == null) l_strPref = "EAST";

        m_enToolbarPosition = l_strPref.compareToIgnoreCase("EAST") == 0 ? ToolBarPosition.TP_EAST : ToolBarPosition.TP_WEST;
    }	

    public void readToolbarActive()
    {
        String l_strPref = (String)GameModule.getGameModule().getPrefs().getValue(DICEOVERLAYTOOLBARACTIVE);
        if (l_strPref == null) l_strPref = "NO";

        m_bToolbarActive = l_strPref.compareToIgnoreCase("YES") == 0;
    }	

    public void saveToolbarPos()
    {
        final Prefs prefs = GameModule.getGameModule().getPrefs();
        prefs.setValue(DICEOVERLAYTOOLBARPOS, m_enToolbarPosition == ToolBarPosition.TP_EAST ? "EAST" : "WEST");

        try
        {
            prefs.save();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void saveToolbarActive()
    {
        final Prefs prefs = GameModule.getGameModule().getPrefs();
        prefs.setValue(DICEOVERLAYTOOLBARACTIVE, m_bToolbarActive ? "YES" : "NO");

        try
        {
            prefs.save();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void draw(Graphics g, Map map)
    {
        if (m_Toolbar != null && m_Toolbar.isVisible())
            m_objDRQH.draw((Graphics2D) g, map, m_enToolbarPosition);
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
    public void setup(boolean gameStarting)
    {
        if (gameStarting)
        {
            SwingUtilities.invokeLater(() -> CreateToolbar());
            m_objDRQH.RegisterForDiceEvents(true);
        }
    }

    @Override
    public Command getRestoreCommand() {return null;}

    public void CreateToolbar()
    {
        if (m_Toolbar == null)
        {
            m_Toolbar = new JToolBar(SwingConstants.VERTICAL);
            m_Toolbar.setVisible(false);
            m_Toolbar.setFloatable(false);
            m_Toolbar.setMargin(new Insets(0,0,0,0));

            final JPanel p = new JPanel();
            m_Toolbar.add(p);

            final int iW = 32;
            int l_iRow = 0;

            GridBagLayout layout = new GridBagLayout();
            layout.columnWidths = new int[] {iW};
            layout.rowHeights = new int[] {0, iW};
            layout.columnWeights = new double[]{0.0};
            layout.rowWeights = new double[]{1.0, 0.0, 0.0};
            p.setLayout(layout);

            java.util.List<AbstractButton> comps = new ArrayList<>();

            AbstractButton c;

            Component l_objVertGlue = Box.createVerticalGlue();
            AddButton(p, l_objVertGlue, l_iRow++, 2);

            c = CreateKeepAliveButton("chatter/PINUP.png", "chatter/PINDOWN.png", "", "Keep DRs on the screen");
            AddButton(p, c, l_iRow++, 20);
            comps.add(c);

            c = CreateActionButton("chatter/CLEAR.png", "", "Clear the screen from DRs", e -> m_objDRQH.KillAll());
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateActionButton("chatter/REWIND.png", "", String.format("Show last %s DRs", m_objDRQH.getMaxNumEntries()), e -> m_objDRQH.ShowLastDR());
            AddButton(p, c, l_iRow++, 20);
            comps.add(c);

            c = CreateDiceButton("DRs.gif", "", "DR", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), true, ASLDiceBot.OTHER_CATEGORY);
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "IFT", "IFT attack DR", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "IFT");
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "TH", "To Hit DR", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "TH");
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "TK", "To Kill DR", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "TK");
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "MC", "Morale Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "MC");
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "R", "Rally DR", KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "Rally");
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "CC", "Close Combat DR", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "CC");
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "TC", "Task Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "TC");
            AddButton(p, c, l_iRow++, 10);
            comps.add(c);

            c = CreateDiceButton("dr.gif", "", "dr", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), false, ASLDiceBot.OTHER_CATEGORY);
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "SA", "Sniper Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false, "SA");
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            c = CreateDiceButton("", "RS", "Random Selection dr", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false, "RS");
            AddButton(p, c, l_iRow++, 20);
            comps.add(c);

            c = CreateActionButton("chatter/ARROW.png", "", "Move the toolbar to the other side", e -> ToolbarMove());
            AddButton(p, c, l_iRow++, 2);
            comps.add(c);

            // find the minimum width for full text display on all buttons,
            // but don't go under 32px
            int text_w = 32;
            for (AbstractButton b : comps) {
              final Insets i = b.getInsets();
              text_w = (int) Math.max(b.getFontMetrics(b.getFont()).getStringBounds(b.getText(), b.getGraphics()).getWidth() + i.left + i.right, text_w);
            }

            // set button and column width to permit full text display
            for (AbstractButton b : comps) {
              b.setPreferredSize(new Dimension(text_w, 32));
              b.setMaximumSize(new Dimension(text_w, 32));
            }
            layout.columnWidths = new int[] {text_w};

            final Window w = SwingUtilities.getWindowAncestor(m_objASLMap.getLayeredPane());
            if (w != null) {
                w.add(m_Toolbar, m_enToolbarPosition == ToolBarPosition.TP_EAST ? BorderLayout.EAST: BorderLayout.WEST);
            }
        }

        m_Toolbar.setVisible(m_bToolbarActive);

        m_objASLMap.getView().revalidate();
        m_Toolbar.revalidate();
    }

    private void ToolbarMove()
    {
        final ToolBarPosition oldpos = m_enToolbarPosition;
        m_enToolbarPosition = oldpos == ToolBarPosition.TP_EAST ? ToolBarPosition.TP_WEST : ToolBarPosition.TP_EAST;

        final Window w = SwingUtilities.getWindowAncestor(m_objASLMap.getLayeredPane());
        w.getLayout().removeLayoutComponent(m_Toolbar);
        w.add(m_Toolbar, oldpos == ToolBarPosition.TP_EAST ? BorderLayout.WEST : BorderLayout.EAST);

        saveToolbarPos();

        m_objASLMap.getView().revalidate();
        m_Toolbar.revalidate();

        NeedRepaint();
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

    private void SetupButtonCommon(AbstractButton b) {
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setMinimumSize(new Dimension(10, 10));
        b.setFocusable(false);
        b.setRolloverEnabled(false);
    }

    private JToggleButton CreateKeepAliveButton(String strImageOff, String strImageOn, String strCaption, String strTooltip)
    {
        JToggleButton l_btn = new JToggleButton(strCaption);
        SetupButtonCommon(l_btn);

        if (!strImageOff.isEmpty()) {
            l_btn.setIcon(new ImageIcon(Op.load(strImageOff).getImage()));
        }

        if (!strImageOn.isEmpty()) {
            l_btn.setSelectedIcon(new ImageIcon(Op.load(strImageOn).getImage()));
        }

        l_btn.addItemListener(e -> {
            m_objDRQH.setKeepAlive(e.getStateChange() == ItemEvent.SELECTED);
        });
        AddHotKeyToTooltip(l_btn, null, strTooltip);

        return l_btn;
    }

    private JButton CreateActionButton(String strImage, String strCaption, String strTooltip, ActionListener objList)
    {
        JButton l_btn = new JButton(strCaption);
        SetupButtonCommon(l_btn);

        if (!strImage.isEmpty()) {
            l_btn.setIcon(new ImageIcon(Op.load(strImage).getImage()));
        }

        l_btn.addActionListener(objList);
        AddHotKeyToTooltip(l_btn, null, strTooltip);

        return l_btn;
    }

    private JButton CreateDiceButton(String strImage, String strCaption, String strTooltip, KeyStroke keyStroke, final boolean bDice, final String strCat)
    {
        JButton l_btn = new JButton(strCaption);
        SetupButtonCommon(l_btn);

        if (!strImage.isEmpty()) {
            l_btn.setIcon(new ImageIcon(Op.load(strImage).getImage()));
        }

        l_btn.addActionListener(e -> {
            try {
                ASLDiceBot l_objDice = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();

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
        });
        AddHotKeyToTooltip(l_btn, keyStroke, strTooltip);

        return l_btn;
    }

    private void AddHotKeyToTooltip(JComponent objButton, KeyStroke keyStroke, String strTooltipText)
    {
        String txt = strTooltipText;
        if (keyStroke != null) {
            txt += " [" + HotKeyConfigurer.getString(keyStroke) + "]";
        }
        objButton.setToolTipText(txt);
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
