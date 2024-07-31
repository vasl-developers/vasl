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
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.ItemEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.util.*;

import VASL.build.module.ASLChatter;
import VASL.build.module.ASLChatter.ChatterListener;
import VASL.build.module.ASLDiceBot;
import VASL.build.module.ASLMap;
import VASL.build.module.dice.DieColor;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.configure.*;
import VASSAL.preferences.Prefs;
import VASSAL.tools.imageop.Op;

import static VASL.build.module.dice.ASLDie.DIE_FILE_NAME_FORMAT;
import static VASSAL.build.module.Chatter.getAnonymousUserName;

interface NeedRepaintEvent {
    void NeedRepaint();
}

class DiceRollPanelHandler {

    private final long rollCount;
    private long birthTime;
    private boolean isAlive;
    private final boolean isFriendly;
    private final String categoryName;
    private final String secondaryMsg;
    private final String userNickName;
    private final int firstDieResult;
    private final int secondDieResult;
    private final int thirdDieResult;
    private final boolean isAxisSAN;
    private final boolean isAlliedSAN;

    public DiceRollPanelHandler(long roll, long birth, String categName, String secondMsg, String userNick, String sanDescr, int firstDie, int secondDie, int thirdDie) {

        rollCount = roll;
        birthTime = birth;
        isAlive = true;

        isFriendly = userNick.compareToIgnoreCase(DiceRollQueueHandler.GetFriendlyPlayerNick()) == 0;

        categoryName = categName;
        secondaryMsg = secondMsg;
        userNickName = userNick;
        firstDieResult = firstDie;
        secondDieResult = secondDie;
        thirdDieResult = thirdDie;

        isAxisSAN = (sanDescr.compareToIgnoreCase("Axis SAN") == 0) || (sanDescr.compareToIgnoreCase("Axis/Allied SAN") == 0);
        isAlliedSAN = (sanDescr.compareToIgnoreCase("Allied SAN") == 0) || (sanDescr.compareToIgnoreCase("Axis/Allied SAN") == 0);
    }

    public long getBirthTime() {
        return birthTime;
    }
    public void setBirthTime(long birth) {
        birthTime = birth;
    }
    public void Kill()
    {
        isAlive = false;
    }
    public void Resurrect() { isAlive = true; }
    public boolean IsAlive()
    {
        return isAlive;
    }
    public boolean isFriendly() {
        return isFriendly;
    }
    public String getCategoryName() {
        return categoryName;
    }
    public String getSecondaryMsg() {
        return secondaryMsg;
    }
    public String getUserNickName() { return userNickName; }
    public int getFirstDieResult() {
        return firstDieResult;
    }
    public int getSecondDieResult() {
        return secondDieResult;
    }
    public int getThirdDieResult() {
        return thirdDieResult;
    }
    public long getRollCount() {
        return rollCount;
    }
    public boolean isOnlyAxisSAN() {
        return isAxisSAN && !isAlliedSAN;
    }
    public boolean isOnlyAlliedSAN() {
        return isAlliedSAN && !isAxisSAN;
    }
    public boolean isBothSAN() {
        return isAlliedSAN && isAxisSAN;
    }
}

class DiceRollQueueHandler implements ActionListener, ChatterListener {

    final String[] dieColors = {
            "Black",
            "Blue",
            "Cyan",
            "Purple",
            "Red",
            "Green",
            "Yellow",
            "Orange",
            "AlliedM",
            "AxisM",
            "American",
            "British",
            "Finnish",
            "French",
            "German",
            "Italian",
            "Japanese",
            "Russian",
            "Swedish"
    };

    private static final String DR_PANEL_CAPTION_FONT = "DRPanelCaptionFont"; //$NON-NLS-1$
    private static final String DR_PANEL_CAPTION_FONT_COLOR = "DRPanelCaptionFontColor"; //$NON-NLS-1$
    private static final String DR_CATEGORY_FONT = "DRCategoryFont"; //$NON-NLS-1$
    private static final String DR_CATEGORY_FONT_COLOR = "DRCategoryFontColor"; //$NON-NLS-1$
    private static final String FRIENDLY_DR_PANEL_COLOR = "friendlyDRPanelColor"; //$NON-NLS-1$
    private static final String FRIENDLY_DR_CAPTION_COLOR = "friendlyDRCaptionColor"; //$NON-NLS-1$
    private static final String ENEMY_DR_PANEL_COLOR = "enemyDRPanelColor"; //$NON-NLS-1$
    private static final String ENEMY_DR_CAPTION_COLOR = "enemyDRCaptionColor"; //$NON-NLS-1$
    private static final String COLORED_DICE_COLOR_OVER_MAP = "coloredDiceColorOverMap"; //$NON-NLS-1$
    private static final String THIRD_DIE_COLOR_OVER_MAP = "thirdDieColorOverMap";
    private static final String SINGLE_DIE_COLOR_OVER_MAP = "singleDieColorOverMap"; //$NON-NLS-1$
    private static final String DICE_ROLL_LIFE_IN_SECONDS = "DRPersistenceOnScreen";
    private static final String PANEL_FILE_NAME = "chatter/PNL.png";
    private static final String CAPTION_FILE_NAME = "chatter/CAPT.png";
    private static final String AXIS_SAN_FILE_NAME = "chatter/AXSAN.png";
    private static final String ALLIED_SAN_FILE_NAME = "chatter/ALSAN.png";
    private static final String PREFERENCE_TAB = "Dice over the map";

    private Font panelCaptionFont = null;
    private Color panelCaptionFontColor = Color.black;
    private Font categoryFont = null;
    private Color categoryFontColor = Color.black;
    private String coloredDiceColor = "Yellow";
    private String singleDieColor = "Yellow";
    private String thirdDieColor = "Red";
    private Color friendlyPanelColor = new Color(235, 244, 251);
    private Color friendlyPanelCaptionColor = new Color(173, 210, 241);
    private Color opponentPanelColor = new Color(253, 239, 230);
    private Color opponentPanelCaptionColor = new Color(251, 214, 192);

    private final BufferedImage [] whiteDieImages = new BufferedImage[6];
    private final BufferedImage [] coloredDieImages = new BufferedImage[6];
    private final BufferedImage [] singleDieImages = new BufferedImage[6];
    private final BufferedImage [] thirdDieImages = new BufferedImage[6];
    private BufferedImage friendlyPanelImage = null;
    private BufferedImage opponentPanelImage = null;
    private BufferedImage axisSAN = null;
    private BufferedImage alliedSAN = null;

    private final String [] quotes = {"It could be worse... it could be raining",
                                      "Only bad players need to be lucky!",
                                      "I prefer unlucky things. Luck is vulgar",
                                      "Strong men believe in cause and effect",
                                      "When nothing goes right... go left!",
                                      "Wishes upon a star, gets hit by meteor",
                                      "I think your guardian angel drinks",
                                      "Hit shappens!",
                                      "Fools wait for a lucky day!",
                                      "It's a cruel, cruel world!",
                                      "No one ever said life was fair",
                                      "No bad luck lasts forever",
                                      "Even the lion has unlucky days!",
                                      "Often bad luck means a bad plan",
                                      "You can’t avoid mistakes and bad luck",
                                      "You are lucky in bad luck"
    };

    private int captionWidth = 0;
    private int captionHeight = 0;

    private final int dice_y = 42;
    private final int dice_height = 26;
    private final int last_dice_x = 144;
    private final int middle_dice_x = 112;
    private final int first_dice_x = 80;
    private final int text_x = 12;
    private final int text_width = 54;

    private final int maxRollsOnScreen = 8;
    private long maxSecondsOnScreen = 10;
    private long clockNow = 0;
    private long rollCount = 0;
    private boolean keepAlive = false;

    public void setMaxSecondsOnScreen(long maxage) {

        if (maxage > 0) {
            if (maxage < maxSecondsOnScreen)
                clockNow += maxSecondsOnScreen - maxage;

            maxSecondsOnScreen = maxage;
        }
    }

    private final ArrayList<NeedRepaintEvent> needrepaint_listeners = new ArrayList<>();
    private final ArrayList<DiceRollPanelHandler> rollPanels = new ArrayList<>();
    private Timer timer = null;
    private boolean isRegisteredForDiceEvents = false;

    public DiceRollQueueHandler() {}

    public void SetupPreferences() {

        RebuildSANAndWhiteDiceImages();

        final Prefs prefs = GameModule.getGameModule().getPrefs();

        // **************************************************************************************
        FontConfigurer captionFontConfigurer = (FontConfigurer)prefs.getOption(DR_PANEL_CAPTION_FONT);

        if (captionFontConfigurer == null) {
            captionFontConfigurer = new FontConfigurer(DR_PANEL_CAPTION_FONT, "DR panel caption font: ",
                                                new Font("SansSerif", Font.PLAIN, 12), new int[] {9, 10, 11, 12, 15, 18, 21, 24, 28, 32}); //$NON-NLS-1$ //$NON-NLS-2$
            prefs.addOption(PREFERENCE_TAB, captionFontConfigurer); //$NON-NLS-1$
        }

        captionFontConfigurer.addPropertyChangeListener(evt -> {
            panelCaptionFont = (Font) evt.getNewValue();
            RebuildFriendlyPanel();
            RebuildOpponentPanel();
            FireNeedRepaint();
        });

        captionFontConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer panelCaptionFontColorConfigurer = (ColorConfigurer)prefs.getOption(DR_PANEL_CAPTION_FONT_COLOR);

        if (panelCaptionFontColorConfigurer == null) {
            panelCaptionFontColorConfigurer = new ColorConfigurer(DR_PANEL_CAPTION_FONT_COLOR, "DR panel caption font color: ", panelCaptionFontColor); //$NON-NLS-1$
            prefs.addOption(PREFERENCE_TAB, panelCaptionFontColorConfigurer); //$NON-NLS-1$
        }

        panelCaptionFontColorConfigurer.addPropertyChangeListener(e -> {
            panelCaptionFontColor = (Color) e.getNewValue();
            RebuildFriendlyPanel();
            RebuildOpponentPanel();
            FireNeedRepaint();
        });

        panelCaptionFontColorConfigurer.fireUpdate();

        // **************************************************************************************
        FontConfigurer categoryFontConfigurer = (FontConfigurer)prefs.getOption(DR_CATEGORY_FONT);

        if (categoryFontConfigurer == null) {
            categoryFontConfigurer = new FontConfigurer(DR_CATEGORY_FONT, "DR category font: ",
                                                new Font("SansSerif", Font.PLAIN, 12), new int[] {9, 10, 11, 12, 15, 18, 21, 24, 28, 32}); //$NON-NLS-1$ //$NON-NLS-2$; //$NON-NLS-1$ //$NON-NLS-2$
            prefs.addOption(PREFERENCE_TAB, categoryFontConfigurer); //$NON-NLS-1$
        }

        categoryFontConfigurer.addPropertyChangeListener(e -> {
            categoryFont = (Font) e.getNewValue();
            RebuildFriendlyPanel();
            RebuildOpponentPanel();
            FireNeedRepaint();
        });

        categoryFontConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer categoryFontColorConfigurer = (ColorConfigurer)prefs.getOption(DR_CATEGORY_FONT_COLOR);

        if (categoryFontColorConfigurer == null) {
            categoryFontColorConfigurer = new ColorConfigurer(DR_CATEGORY_FONT_COLOR, "DR category font color: ", categoryFontColor); //$NON-NLS-1$
            prefs.addOption(PREFERENCE_TAB, categoryFontColorConfigurer); //$NON-NLS-1$
        }

        categoryFontColorConfigurer.addPropertyChangeListener(e -> {
            categoryFontColor = (Color) e.getNewValue();
            RebuildFriendlyPanel();
            RebuildOpponentPanel();
            FireNeedRepaint();
        });

        categoryFontColorConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer friendlyPanelColorConfigurer = (ColorConfigurer)prefs.getOption(FRIENDLY_DR_PANEL_COLOR);

        if (friendlyPanelColorConfigurer == null) {
            friendlyPanelColorConfigurer = new ColorConfigurer(FRIENDLY_DR_PANEL_COLOR, "Player's DR panel background color:  ", friendlyPanelColor); //$NON-NLS-1$
            prefs.addOption(PREFERENCE_TAB, friendlyPanelColorConfigurer); //$NON-NLS-1$
        }

        friendlyPanelColorConfigurer.addPropertyChangeListener(e -> {
            friendlyPanelColor = (Color) e.getNewValue();
            RebuildFriendlyPanel();
            FireNeedRepaint();
        });

        friendlyPanelColorConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer friendlyCaptionColorConfigurer = (ColorConfigurer)prefs.getOption(FRIENDLY_DR_CAPTION_COLOR);

        if (friendlyCaptionColorConfigurer == null) {
            friendlyCaptionColorConfigurer = new ColorConfigurer(FRIENDLY_DR_CAPTION_COLOR, "Player's DR panel caption color:  ", friendlyPanelCaptionColor); //$NON-NLS-1$
            prefs.addOption(PREFERENCE_TAB, friendlyCaptionColorConfigurer); //$NON-NLS-1$
        }

        friendlyCaptionColorConfigurer.addPropertyChangeListener(e -> {
            friendlyPanelCaptionColor = (Color) e.getNewValue();
            RebuildFriendlyPanel();
            FireNeedRepaint();
        });

        friendlyCaptionColorConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer opponentPanelColorConfigurer = (ColorConfigurer)prefs.getOption(ENEMY_DR_PANEL_COLOR);

        if (opponentPanelColorConfigurer == null) {
            opponentPanelColorConfigurer = new ColorConfigurer(ENEMY_DR_PANEL_COLOR, "Opponent's DR panel background color:  ", opponentPanelColor); //$NON-NLS-1$
            prefs.addOption(PREFERENCE_TAB, opponentPanelColorConfigurer); //$NON-NLS-1$
        }

        opponentPanelColorConfigurer.addPropertyChangeListener(e -> {
            opponentPanelColor = (Color) e.getNewValue();
            RebuildOpponentPanel();
            FireNeedRepaint();
        });

        opponentPanelColorConfigurer.fireUpdate();

        // **************************************************************************************
        ColorConfigurer opponentCaptionColorConfigurer = (ColorConfigurer)prefs.getOption(ENEMY_DR_CAPTION_COLOR);

        if (opponentCaptionColorConfigurer == null) {
            opponentCaptionColorConfigurer = new ColorConfigurer(ENEMY_DR_CAPTION_COLOR, "Opponent's DR panel caption color:  ", opponentPanelCaptionColor); //$NON-NLS-1$
            prefs.addOption(PREFERENCE_TAB, opponentCaptionColorConfigurer); //$NON-NLS-1$
        }

        opponentCaptionColorConfigurer.addPropertyChangeListener(e -> {
            opponentPanelCaptionColor = (Color) e.getNewValue();
            RebuildOpponentPanel();
            FireNeedRepaint();
        });

        opponentCaptionColorConfigurer.fireUpdate();

        // **************************************************************************************
        StringEnumConfigurer coloredDiceColorConfigurer = (StringEnumConfigurer)prefs.getOption(COLORED_DICE_COLOR_OVER_MAP);

        if (coloredDiceColorConfigurer == null) {
            coloredDiceColorConfigurer = new StringEnumConfigurer(COLORED_DICE_COLOR_OVER_MAP, "Colored die color:  ", new String[] {"Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
            prefs.addOption(PREFERENCE_TAB, coloredDiceColorConfigurer); //$NON-NLS-1$

            coloredDiceColorConfigurer.setValue(coloredDiceColor);
        }

        coloredDiceColorConfigurer.addPropertyChangeListener(e -> {
            coloredDiceColor = (String) e.getNewValue();
            if (coloredDiceColor == null) {
                coloredDiceColor = "Red";
            }
            RebuildColoredDiceImages(DieColor.getEnum(coloredDiceColor));
            FireNeedRepaint();
        });

        final Set<String> COLORARRAY = new HashSet<>(Arrays.asList(dieColors));

        if (!COLORARRAY.contains(coloredDiceColor)){
            coloredDiceColor = "Red";
        }

        coloredDiceColorConfigurer.fireUpdate();

        // **************************************************************************************
        StringEnumConfigurer thirdDieColorConfigurer = (StringEnumConfigurer)prefs.getOption(THIRD_DIE_COLOR_OVER_MAP);

        if (thirdDieColorConfigurer == null) {
            thirdDieColorConfigurer = new StringEnumConfigurer(THIRD_DIE_COLOR_OVER_MAP, "Third die color:", dieColors );
            prefs.addOption(PREFERENCE_TAB, thirdDieColorConfigurer); //$NON-NLS-1$

            thirdDieColorConfigurer.setValue(thirdDieColor);
        }

        thirdDieColorConfigurer.addPropertyChangeListener(e -> {
            thirdDieColor = (String) e.getNewValue();
            if (thirdDieColor == null) {
                thirdDieColor = "Red";
            }
            RebuildThirdDieFaces(DieColor.getEnum(thirdDieColor));
            FireNeedRepaint();
        });

        thirdDieColorConfigurer.fireUpdate();

        // **************************************************************************************
        StringEnumConfigurer singleDieColorConfigurer = (StringEnumConfigurer)prefs.getOption(SINGLE_DIE_COLOR_OVER_MAP);

        if (singleDieColorConfigurer == null) {
            singleDieColorConfigurer = new StringEnumConfigurer(SINGLE_DIE_COLOR_OVER_MAP, "Single die color:  ", dieColors );
            prefs.addOption(PREFERENCE_TAB, singleDieColorConfigurer); //$NON-NLS-1$

            singleDieColorConfigurer.setValue(singleDieColor);
        }

        singleDieColorConfigurer.addPropertyChangeListener(e -> {
            singleDieColor = (String) e.getNewValue();
            if (singleDieColor == null) {
                singleDieColor = "Yellow";
            }
            RebuildSingleDieFaces(DieColor.getEnum(singleDieColor));
            FireNeedRepaint();
        });

        singleDieColorConfigurer.fireUpdate();

        // **************************************************************************************
        LongConfigurer secondsLifeNumConfigurer = (LongConfigurer)prefs.getOption(DICE_ROLL_LIFE_IN_SECONDS);

        if (secondsLifeNumConfigurer == null) {
            secondsLifeNumConfigurer = new LongConfigurer(DICE_ROLL_LIFE_IN_SECONDS, "DR persistence on the screen (seconds):  ", 10L); //$NON-NLS-1$
            prefs.addOption(PREFERENCE_TAB, secondsLifeNumConfigurer); //$NON-NLS-1$
        }

        secondsLifeNumConfigurer.addPropertyChangeListener(e -> setMaxSecondsOnScreen((Long) e.getNewValue()));

        secondsLifeNumConfigurer.fireUpdate();
    }

    public static String GetFriendlyPlayerNick() {
        String playerId = GlobalOptions.getInstance().getPlayerId();

        return (playerId.isEmpty() ? "(" + getAnonymousUserName() + ")" : playerId);
    }

    private void DrawCaption(Graphics2D g, String strCaption) {
        g.setFont(panelCaptionFont);

        // Get font metrics for the current font
        FontMetrics fontMetrics = g.getFontMetrics();

        // Get the position of the leftmost character in the baseline
        // getWidth() and getHeight() returns the width and height of this component
        //int msgX = m_iCaptionWidth / 2 - fontMetrics.stringWidth(strNickName) / 2;
        int msgY = captionHeight / 2 + fontMetrics.getHeight() / 2;

        g.setColor(panelCaptionFontColor);

        final Shape old_clip = g.getClip();
        g.clipRect(3, 3, captionWidth - 3, captionHeight);
        g.drawString(strCaption, 10, msgY);
        g.setClip(old_clip);
    }

    private void DrawCategory(Graphics2D g, String caption, String secondaryMsg, Rectangle objRect) {

        g.setFont(categoryFont);

        // Get font metrics for the current font
        FontMetrics fontMetrics = g.getFontMetrics();

        // Get the position of the leftmost character in the baseline
        // getWidth() and getHeight() returns the width and height of this component
        int msgX = objRect.x;
        int msgY = objRect.y + fontMetrics.getAscent();

        g.setColor(categoryFontColor);

        final Shape old_clip = g.getClip();
        g.clipRect(objRect.x, objRect.y, objRect.width, objRect.height);
        g.drawString(caption, msgX, msgY);
        g.setClip(old_clip);

        if (!"".equals(secondaryMsg)) {

            HashMap<TextAttribute, Object> at = new HashMap<TextAttribute, Object>();

            at.put(TextAttribute.FAMILY, "SansSerif");
            at.put(TextAttribute.WIDTH, 0.85f);
            at.put(TextAttribute.SIZE, 11);
            Font secondaryMsgFont = Font.getFont(at);

            g.setFont(secondaryMsgFont);

            FontMetrics secondMsgFontMetrics = g.getFontMetrics();

            msgY += secondMsgFontMetrics.getAscent() + 7;

            g.setColor(categoryFontColor);

            g.drawString(secondaryMsg, msgX, msgY);
        }
    }

    private void RebuildFriendlyPanel() {
        try {
            // background
            friendlyPanelImage = ColorChanger.changeColor(Op.load(PANEL_FILE_NAME).getImage(null), Color.red, friendlyPanelColor);

            // caption
            BufferedImage captionImage = ColorChanger.changeColor(Op.load(CAPTION_FILE_NAME).getImage(null), Color.red, friendlyPanelCaptionColor);

            captionWidth = captionImage.getWidth();
            captionHeight = captionImage.getHeight();

            Graphics2D g = friendlyPanelImage.createGraphics();
            g.drawImage(captionImage, 4, 4, null);
            g.dispose();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void RebuildOpponentPanel() {
        try {
            // background
            opponentPanelImage = ColorChanger.changeColor(Op.load(PANEL_FILE_NAME).getImage(null), Color.red, opponentPanelColor);

            // caption
            BufferedImage captionImage = ColorChanger.changeColor(Op.load(CAPTION_FILE_NAME).getImage(null), Color.red, opponentPanelCaptionColor);

            if (captionWidth == 0)
                captionWidth = captionImage.getWidth();
            if (captionHeight == 0)
                captionHeight = captionImage.getHeight();

            Graphics2D g = opponentPanelImage.createGraphics();
            g.drawImage(captionImage, 4, 4, null);
            g.dispose();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void RebuildSANAndWhiteDiceImages() {

        try {
            axisSAN = Op.load(AXIS_SAN_FILE_NAME).getImage(null);
            alliedSAN = Op.load(ALLIED_SAN_FILE_NAME).getImage(null);

            DieColor color = DieColor.WHITE;

            for (int i = 0; i < 6; i++) {
                whiteDieImages[i] = Op.load(String.format(DIE_FILE_NAME_FORMAT, i + 1, color)).getImage(null);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void RebuildColoredDiceImages(DieColor color) {

        try {
            for (int i = 0; i < 6; i++) {
                coloredDieImages[i] = Op.load(String.format(DIE_FILE_NAME_FORMAT, i + 1, color)).getImage(null);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void RebuildSingleDieFaces(DieColor color) {

        try {
            for (int i = 0; i < 6; i++) {
                singleDieImages[i] = Op.load(String.format(DIE_FILE_NAME_FORMAT, i + 1, color)).getImage(null);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void RebuildThirdDieFaces(DieColor color) {

        try {
            for (int i = 0; i < 6; i++) {
                thirdDieImages[i] = Op.load(String.format(DIE_FILE_NAME_FORMAT, i + 1, color)).getImage(null);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void RegisterForDiceEvents(boolean addRegistration) {

        if (!isRegisteredForDiceEvents && addRegistration) {
            if (GameModule.getGameModule().getChatter() instanceof ASLChatter) {
                if (addRegistration) {

                    ((ASLChatter) (GameModule.getGameModule().getChatter())).addListener(this);

                    if (timer == null) {
                        timer = new Timer(1000, this);
                        timer.start();
                    }
                    else {
                        timer.stop();
                        timer.setInitialDelay(1000);
                        timer.setDelay(1000);
                        timer.restart();
                    }
                }
                else {

                    ((ASLChatter) (GameModule.getGameModule().getChatter())).removeListener(this);

                    if (timer != null) {
                        timer.stop();
                        timer = null;
                    }
                }

                isRegisteredForDiceEvents = true;

            }
        }
    }

    public void KillAll() {
        clockNow += maxSecondsOnScreen;
        ClockTick();
    }

    synchronized public void ShowLastDiceRoll() {
        boolean needRepaint = false;

        for (DiceRollPanelHandler rollPanel : rollPanels) {
            rollPanel.setBirthTime(clockNow);

            if (!rollPanel.IsAlive()) {
                rollPanel.Resurrect();
                needRepaint = true;
            }
        }

        if (needRepaint)
            FireNeedRepaint();
    }

    synchronized public void ClockTick() {
        boolean needRepaint = false;

        for (DiceRollPanelHandler rollPanel : rollPanels) {
            if (rollPanel.IsAlive()) {
                if (clockNow - rollPanel.getBirthTime() >= maxSecondsOnScreen) {
                    rollPanel.Kill();
                    needRepaint = true;
                }
            }
            else
                break;
        }

        if (!keepAlive)
            clockNow++;

        if (needRepaint)
            FireNeedRepaint();
    }

    synchronized public void PushDiceRoll(String categoryName, String strUser, String strSAN, int firstDie, int secondDie, int thirdDie, int rofDie) {

        String secondMsg = "";
        // add hit location info
        if (categoryName.trim().equals("TH")) {

            if (firstDie < secondDie) {
                secondMsg = "TURRET HIT";
            } else {
                secondMsg = "HULL HIT";

                if ((firstDie == 6) && (secondDie == 6)) {
                    secondMsg = quotes[GameModule.getGameModule().getRNG().nextInt(quotes.length)];
                }
            }
        }

        rollPanels.add(0, new DiceRollPanelHandler(++rollCount, clockNow, categoryName.trim(), secondMsg, strUser, strSAN, firstDie, secondDie, thirdDie));

        if (rollPanels.size() > getMaxRollsOnScreen())
            rollPanels.subList(getMaxRollsOnScreen(), rollPanels.size()).clear();

        FireNeedRepaint();
    }

    private void drawDiceRollPanel(Graphics2D g, Rectangle r, ToolBarPosition toolbarPosition, DiceRollPanelHandler drPanel, int index) {

        Point panelPosition = new Point(r.x + (toolbarPosition == ToolBarPosition.TP_EAST ? r.width - 190 : 10), r.y + r.height - 100 - 100 * index);

        g.translate(panelPosition.x, panelPosition.y);

        int local_dice_y = dice_y - (!"".equals(drPanel.getSecondaryMsg()) ? 5 : 0);

        // draw the background
        g.drawImage(drPanel.isFriendly() ? friendlyPanelImage : opponentPanelImage, 0, 0, null);

        // draw the caption
        if (panelCaptionFont != null) {
             DrawCaption(g, drPanel.getRollCount() + ". " + (drPanel.isFriendly() ? GetFriendlyPlayerNick() : drPanel.getUserNickName()));
        }

        // draw the dice
        if (drPanel.getThirdDieResult() != -1) {
            if (drPanel.getSecondDieResult() != -1) {
                g.drawImage(thirdDieImages[drPanel.getThirdDieResult() - 1], last_dice_x, local_dice_y, null);
                g.drawImage(whiteDieImages[drPanel.getSecondDieResult() - 1], middle_dice_x, local_dice_y, null);
                g.drawImage(coloredDieImages[drPanel.getFirstDieResult() - 1], first_dice_x, local_dice_y, null);
            } else {
                g.drawImage(thirdDieImages[drPanel.getThirdDieResult() - 1], last_dice_x, local_dice_y, null);
                g.drawImage(singleDieImages[drPanel.getFirstDieResult() - 1], middle_dice_x, local_dice_y, null);
            }
        }
        else {
            if (drPanel.getSecondDieResult() != -1) {
                g.drawImage(whiteDieImages[drPanel.getSecondDieResult() - 1], last_dice_x, local_dice_y, null);
                g.drawImage(coloredDieImages[drPanel.getFirstDieResult() - 1], middle_dice_x, local_dice_y, null);
            } else {
                g.drawImage(singleDieImages[drPanel.getFirstDieResult() - 1], last_dice_x, local_dice_y, null);
            }
        }

        if (drPanel.getCategoryName() != null && !drPanel.getCategoryName().isEmpty()) {
            DrawCategory(g, drPanel.getCategoryName(), drPanel.getSecondaryMsg(), new Rectangle(text_x, local_dice_y, text_width, dice_height));
        }

        g.translate(-panelPosition.x, -panelPosition.y);

        if (drPanel.isOnlyAxisSAN()) {
            g.drawImage(axisSAN, panelPosition.x + 133, panelPosition.y - 15, null);
        }
        else if (drPanel.isOnlyAlliedSAN()) {
            g.drawImage(alliedSAN, panelPosition.x + 133, panelPosition.y - 15, null);
        }
        else if (drPanel.isBothSAN()) {
            g.drawImage(axisSAN, panelPosition.x + 90, panelPosition.y - 15, null);
            g.drawImage(alliedSAN, panelPosition.x + 133, panelPosition.y - 15, null);
        }
    }

    synchronized public void draw(Graphics2D g, Map map, ToolBarPosition toolbarPosition) {

        final double os_scale = g.getDeviceConfiguration().getDefaultTransform().getScaleX();
        final AffineTransform orig_t = g.getTransform();
        final AffineTransform new_t = new AffineTransform(orig_t);
        new_t.scale(os_scale, os_scale);
        g.setTransform(new_t);

        final Rectangle r = map.getView().getVisibleRect();

        for (int index = 0; index < rollPanels.size(); index++) {

            DiceRollPanelHandler rollPanel = rollPanels.get(index);

            if (rollPanel.IsAlive()) {
                drawDiceRollPanel(g, r, toolbarPosition, rollPanel, index);
            }
        }

        g.setTransform(orig_t);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ClockTick();
    }

    public void DiceRoll(String categoryName, String user, String san, int firstDieResult, int secondDieResult, int thirdDie, int rofDie) {
        PushDiceRoll(categoryName, user, san, firstDieResult, secondDieResult, thirdDie, rofDie);
    }

    public void addRepaintListener(NeedRepaintEvent toAdd) {
        needrepaint_listeners.add(toAdd);
    }

    public void removeRepaintListener(NeedRepaintEvent toRemove) {
        needrepaint_listeners.remove(toRemove);
    }

    private void FireNeedRepaint() {
        for (NeedRepaintEvent objListener : needrepaint_listeners) {
            objListener.NeedRepaint();
        }
    }

    public int getMaxRollsOnScreen() {
        return maxRollsOnScreen;
    }

    public void setKeepAlive(boolean ka) {
        keepAlive = ka;
    }
}

enum ToolBarPosition {TP_EAST, TP_WEST}

public class ASLDiceOverlay extends AbstractConfigurable implements GameComponent, Drawable, NeedRepaintEvent {

    private ASLMap aslMap;
    private JToolBar toolbar = null;
    private DiceRollQueueHandler diceRollQueueHandler = null;
    private ToolBarPosition toolbarPosition = ToolBarPosition.TP_EAST;
    private boolean isToolbarActive = false;
    private final String DICE_OVERLAY_TOOLBAR_POS = "DiceOverlayToolbarPos";
    private final String DICE_OVERLAY_TOOLBAR_ACTIVE = "DiceOverlayToolbarActive";

    // this component is not configurable
    @Override
    public Class<?>[] getAttributeTypes() { return new Class<?>[0]; }

    @Override
    public String[] getAttributeNames() { return new String[0]; }

    @Override
    public String[] getAttributeDescriptions() { return new String[0]; }

    @Override
    public String getAttributeValueString(String key)  { return null; }

    @Override
    public void setAttribute(String key, Object value) { }

    @Override
    public void addTo(Buildable parent) {
        final Prefs prefs = GameModule.getGameModule().getPrefs();

        if (prefs.getOption(DICE_OVERLAY_TOOLBAR_POS) == null)
            prefs.addOption(null, new StringConfigurer(DICE_OVERLAY_TOOLBAR_POS, null));

        if (prefs.getOption(DICE_OVERLAY_TOOLBAR_ACTIVE) == null)
            prefs.addOption(null, new StringConfigurer(DICE_OVERLAY_TOOLBAR_ACTIVE, null));

        readToolbarPosition();
        readToolbarActive();

        // add this component to the game and register a mouse listener
        if (parent instanceof ASLMap) {

            aslMap = (ASLMap) parent;
            aslMap.addDrawComponent(this);

            final JPopupMenu pm = aslMap.getPopupMenu();

            pm.addSeparator();
            JMenuItem toggleToolbar = new JMenuItem("Dice-over-the-map toolbar");
            toggleToolbar.setBackground(new Color(255,255,255));
            pm.add(toggleToolbar);
            pm.addSeparator();

            // button toolbar activation
            JCheckBoxMenuItem toolbarVisibleChange = new JCheckBoxMenuItem("Toolbar activation (on/off)");

            toolbarVisibleChange.setSelected(isToolbarActive);

            toolbarVisibleChange.addActionListener(e -> {
                isToolbarActive = !isToolbarActive;
                CreateToolbar();
                saveToolbarActive();
            });

            pm.add(toolbarVisibleChange);

            diceRollQueueHandler = new DiceRollQueueHandler();
            diceRollQueueHandler.addRepaintListener(this);

            diceRollQueueHandler.SetupPreferences();
        }

        GameModule.getGameModule().getGameState().addGameComponent(this);
    }

    public void readToolbarPosition() {

        String pref = (String)GameModule.getGameModule().getPrefs().getValue(DICE_OVERLAY_TOOLBAR_POS);

        toolbarPosition = "WEST".compareToIgnoreCase(pref) == 0 ? ToolBarPosition.TP_WEST : ToolBarPosition.TP_EAST;
    }	

    public void readToolbarActive() {

        String pref = (String)GameModule.getGameModule().getPrefs().getValue(DICE_OVERLAY_TOOLBAR_ACTIVE);

        isToolbarActive = "YES".compareToIgnoreCase(pref) == 0;
    }

    public void saveToolbarPos() {

        final Prefs prefs = GameModule.getGameModule().getPrefs();

        prefs.setValue(DICE_OVERLAY_TOOLBAR_POS, toolbarPosition == ToolBarPosition.TP_EAST ? "EAST" : "WEST");

        try {
            prefs.save();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void saveToolbarActive() {

        final Prefs prefs = GameModule.getGameModule().getPrefs();

        prefs.setValue(DICE_OVERLAY_TOOLBAR_ACTIVE, isToolbarActive ? "YES" : "NO");

        try {
            prefs.save();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void draw(Graphics g, Map map) {

        if (toolbar != null && toolbar.isVisible())
            diceRollQueueHandler.draw((Graphics2D) g, map, toolbarPosition);
    }

    @Override
    public boolean drawAboveCounters()  { return true; }

    @Override
    public void removeFrom(Buildable parent) { }

    @Override
    public HelpFile getHelpFile() { return null; }

    @Override
    public Class[] getAllowableConfigureComponents() { return new Class[0]; }

    @Override
    public void setup(boolean gameStarting) {

        if (gameStarting) {
            SwingUtilities.invokeLater(this::CreateToolbar);
            diceRollQueueHandler.RegisterForDiceEvents(true);
        }
    }

    @Override
    public Command getRestoreCommand() { return null; }

    public void CreateToolbar() {

        if (toolbar == null) {

            toolbar = new JToolBar(SwingConstants.VERTICAL);
            toolbar.setVisible(false);
            toolbar.setFloatable(false);
            toolbar.setMargin(new Insets(0,0,0,0));

            final JPanel p = new JPanel();
            toolbar.add(p);

            final int iW = 32;
            int rowNum = 0;

            GridBagLayout layout = new GridBagLayout();
            layout.columnWidths = new int[] { iW };
            layout.rowHeights = new int[] { 0, iW };
            layout.columnWeights = new double[] { 0.0 };
            layout.rowWeights = new double[] { 1.0, 0.0, 0.0 };
            p.setLayout(layout);

            java.util.List<AbstractButton> comps = new ArrayList<>();

            AbstractButton btn;

            Component l_objVertGlue = Box.createVerticalGlue();
            AddButton(p, l_objVertGlue, rowNum++, 2);

            btn = CreateKeepAliveButton();
            AddButton(p, btn, rowNum++, 20);
            comps.add(btn);

            btn = CreateActionButton("chatter/CLEAR.png", "Clear the screen from DRs", e -> diceRollQueueHandler.KillAll());
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateActionButton("chatter/REWIND.png", String.format("Show last %s DRs", diceRollQueueHandler.getMaxRollsOnScreen()), e -> diceRollQueueHandler.ShowLastDiceRoll());
            AddButton(p, btn, rowNum++, 20);
            comps.add(btn);

            btn = CreateDiceButton("DRs.gif", "", "DR", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), true, ASLDiceBot.OTHER_CATEGORY);
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "IFT", "IFT attack DR", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "IFT");
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "TH", "To Hit DR", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "TH");
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "TK", "To Kill DR", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "TK");
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "MC", "Morale Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "MC");
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "R", "Rally DR", KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "Rally");
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "CC", "Close Combat DR", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "CC");
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "TC", "Task Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), true, "TC");
            AddButton(p, btn, rowNum++, 10);
            comps.add(btn);

            btn = CreateDiceButton("dr.gif", "", "dr", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), false, ASLDiceBot.OTHER_CATEGORY);
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "SA", "Sniper Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false, "SA");
            AddButton(p, btn, rowNum++, 2);
            comps.add(btn);

            btn = CreateDiceButton("", "RS", "Random Selection dr", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false, "RS");
            AddButton(p, btn, rowNum++, 20);
            comps.add(btn);

            btn = CreateActionButton("chatter/ARROW.png", "Move the toolbar to the other side", e -> ToolbarMove());
            AddButton(p, btn, rowNum, 2);
            comps.add(btn);

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

            layout.columnWidths = new int[] { text_w };

            final Window w = SwingUtilities.getWindowAncestor(aslMap.getLayeredPane());
            if (w != null) {
                w.add(toolbar, toolbarPosition == ToolBarPosition.TP_EAST ? BorderLayout.EAST: BorderLayout.WEST);
            }
        }

        toolbar.setVisible(isToolbarActive);

        aslMap.getView().revalidate();
        toolbar.revalidate();
    }

    private void ToolbarMove() {
        final ToolBarPosition oldpos = toolbarPosition;
        toolbarPosition = oldpos == ToolBarPosition.TP_EAST ? ToolBarPosition.TP_WEST : ToolBarPosition.TP_EAST;

        final Window w = SwingUtilities.getWindowAncestor(aslMap.getLayeredPane());

        w.getLayout().removeLayoutComponent(toolbar);
        w.add(toolbar, oldpos == ToolBarPosition.TP_EAST ? BorderLayout.WEST : BorderLayout.EAST);

        saveToolbarPos();

        aslMap.getView().revalidate();
        toolbar.revalidate();

        NeedRepaint();
    }

    private void AddButton(JPanel panel, Component component, int row, int gap) {
        GridBagConstraints gridBagConstraints;

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.insets = new Insets(0, 0, gap, 0);
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = row;

        panel.add(component, gridBagConstraints);
    }

    private void SetupButtonCommon(AbstractButton btn) {

        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setMinimumSize(new Dimension(10, 10));
        btn.setFocusable(false);
        btn.setRolloverEnabled(false);
    }

    private JToggleButton CreateKeepAliveButton() {

        JToggleButton btn = new JToggleButton("");
        SetupButtonCommon(btn);

        btn.setIcon(new ImageIcon(Op.load("chatter/PINUP.png").getImage()));
        btn.setSelectedIcon(new ImageIcon(Op.load("chatter/PINDOWN.png").getImage()));

        btn.addItemListener(e -> diceRollQueueHandler.setKeepAlive(e.getStateChange() == ItemEvent.SELECTED));
        AddHotKeyToTooltip(btn, null, "Keep DRs on the screen");

        return btn;
    }

    private JButton CreateActionButton(String image, String tooltip, ActionListener listener) {

        JButton btn = new JButton("");
        SetupButtonCommon(btn);

        if (!image.isEmpty()) {
            btn.setIcon(new ImageIcon(Op.load(image).getImage()));
        }

        btn.addActionListener(listener);
        AddHotKeyToTooltip(btn, null, tooltip);

        return btn;
    }

    private JButton CreateDiceButton(String image, String caption, String tooltip, KeyStroke keyStroke, final boolean twoDice, final String categoryName) {
        JButton btn = new JButton(caption);
        SetupButtonCommon(btn);

        if (!image.isEmpty()) {
            btn.setIcon(new ImageIcon(Op.load(image).getImage()));
        }

        btn.addActionListener(e -> {
            try {
                ASLDiceBot aslDiceBot = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();

                if (aslDiceBot != null) {
                    if (twoDice) {
                        aslDiceBot.DR(categoryName);
                    }
                    else {
                        aslDiceBot.dr(categoryName);
                    }
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        AddHotKeyToTooltip(btn, keyStroke, tooltip);

        return btn;
    }

    private void AddHotKeyToTooltip(JComponent button, KeyStroke keyStroke, String tooltipText) {
        String txt = tooltipText;

        if (keyStroke != null) {
            txt += " [" + HotKeyConfigurer.getString(keyStroke) + "]";
        }

        button.setToolTipText(txt);
    }

    public void NeedRepaint() {
        if (aslMap != null)
            aslMap.repaint();
    }
}

class ColorChanger {
    public static final int ALPHA = 0;
    public static final int RED = 1;
    public static final int GREEN = 2;
    public static final int BLUE = 3;

    public static final int HUE = 0;
    public static final int SATURATION = 1;
    public static final int BRIGHTNESS = 2;

    public static final int TRANSPARENT = 0;

    public static BufferedImage changeColor(BufferedImage image, Color mask, Color replacement) {

        BufferedImage destImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

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

        return Color.HSBtoRGB(replHSB[HUE],
            replHSB[SATURATION], destHSB[BRIGHTNESS]);
    }

    private static boolean matches(int maskRGB, int destRGB) {

        float[] hsbMask = getHSBArray(maskRGB);
        float[] hsbDest = getHSBArray(destRGB);

        return hsbMask[HUE] == hsbDest[HUE]
                && hsbMask[SATURATION] == hsbDest[SATURATION]
                && getRGBArray(destRGB)[ALPHA] != TRANSPARENT;
    }

    private static int[] getRGBArray(int rgb) {

        return new int[]{(rgb >> 24) & 0xff, (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff};
    }

    private static float[] getHSBArray(int rgb) {

        int[] rgbArr = getRGBArray(rgb);

        return Color.RGBtoHSB(rgbArr[RED], rgbArr[GREEN], rgbArr[BLUE], null);
    }
}

