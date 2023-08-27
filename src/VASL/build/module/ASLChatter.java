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
package VASL.build.module;

import VASL.build.module.dice.ASLDiceFactory;
import VASL.build.module.dice.ASLDie;
import VASL.build.module.dice.DieColor;
import VASL.environment.*;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GlobalOptions;
import VASSAL.command.CommandEncoder;
import VASSAL.configure.*;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.KeyStrokeListener;
import VASSAL.tools.KeyStrokeSource;
import VASSAL.tools.QuickColors;
import VASSAL.tools.imageop.Op;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTML;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static VASSAL.build.GameModule.getGameModule;

/**
 * The chat window component.  Displays text messages and
 * accepts i.  Also acts as a {@link CommandEncoder},
 * encoding/decoding commands that display message in the text area
 */
public class ASLChatter extends VASSAL.build.module.Chatter
{
    private final ArrayList<ChatterListener> chatter_listeners = new ArrayList<>();
    public static final String BEFORE_CATEGORY = "   ";
    private static final String CHAT_FONT = "ChatFont";
    private static final String BUTTON_FONT = "ButtonFont";
    private static final String USE_DICE_IMAGES = "useDiceImages"; //$NON-NLS-1$
    private static final String SHOW_DICE_STATS = "showDiceStats"; //$NON-NLS-1$
    private static final String CHAT_BACKGROUND_COLOR = "chatBackgroundColor"; //$NON-NLS-1$
    private static final String COLORED_DICE_COLOR = "coloredDiceColor"; //$NON-NLS-1$
    private static final String SINGLE_DIE_COLOR = "singleDieColor"; //$NON-NLS-1$
    private static final String THIRD_DIE_COLOR = "thirdDieColor"; //$NON-NLS-1$
    private static final String NOTIFICATION_LEVEL = "notificationLevel"; //$NON-NLS-1$
    private final static String USER_SPACING_PADDING =  "  ...  ";
    private static final String preferenceTabName = "VASL"; // alwaysontop preference
    protected static final String DICE_CHAT_COLOR = "HTMLDiceChatColor";

    public enum DiceType {
        WHITE,
        COLORED,
        OTHER_DUST,
        BOTH,
        SINGLE
    }

    private Color clrBackground;
    private String clrColoredDiceColor;
    private String clrDustColoredDiceColor;
    private String clrSingleDieColor;
    private final JButton btnStats;
    private final JButton btnDR;
    private final JButton btnIFT;
    private final JButton btnTH;
    private final JButton btnTK;
    private final JButton btnMC;
    private final JButton btnRally;
    private final JButton btnCC;
    private final JButton btnTC;
    private final JButton btndr;
    private final JButton btnSA;
    private final JButton btnRS;
    private final JPanel objButtonPanel;
    private boolean bUseDiceImages;
    private boolean bShowDiceStats;

    private final Environment environment = new Environment();
    private final ASLDiceFactory diceFactory = new ASLDiceFactory();

    private final JTextField edtInputText;
    private int DRNotificationLevel;

    // create message part objects; each will be styled differently and added to chat window
    String msgpartCategory;
    String msgpartUser;
    String msgpartWdice;
    String msgpartCdice;
    String msgpartRest;
    String msgpartSpecial;
    String msgpartSAN;
    String msgpartDiceImage;

    public ASLChatter() {
        super();

        clrBackground = Color.white;
        conversationPane.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {
                if (!e.isConsumed()) {
                    keyCommand(KeyStroke.getKeyStrokeForEvent(e));
                }
            }

            public void keyPressed(KeyEvent e) {
                if (!e.isConsumed()) {
                    keyCommand(KeyStroke.getKeyStrokeForEvent(e));
                }
            }

            public void keyReleased(KeyEvent e) {
                if (!e.isConsumed()) {
                    keyCommand(KeyStroke.getKeyStrokeForEvent(e));
                }
            }
        });

        btnStats = CreateStatsDiceButton(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.CTRL_DOWN_MASK));
        btnDR = CreateChatterDiceButton("DRs.gif", "DR", "DR", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), true, ASLDiceBot.OTHER_CATEGORY);
        btnIFT = CreateChatterDiceButton("", "IFT", "IFT attack DR", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "IFT");
        btnTH = CreateChatterDiceButton("", "TH", "To Hit DR", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "TH");
        btnTK = CreateChatterDiceButton("", "TK", "To Kill DR", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "TK");
        btnMC = CreateChatterDiceButton("", "MC", "Morale Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "MC");
        btnRally = CreateChatterDiceButton("", "Rally", "Rally DR", KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "Rally");
        btnCC = CreateChatterDiceButton("", "CC", "Close Combat DR", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "CC");
        btnTC = CreateChatterDiceButton("", "TC", "Task Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "TC");
        btndr = CreateChatterDiceButton("dr.gif", "dr", "dr", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), false, ASLDiceBot.OTHER_CATEGORY);
        btnSA = CreateChatterDiceButton("", "SA", "Sniper Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "SA");
        btnRS = CreateChatterDiceButton("", "RS", "Random Selection dr", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "RS");

        JPanel objPanelContainer = new JPanel();
        objPanelContainer.setLayout(new BoxLayout(objPanelContainer, BoxLayout.LINE_AXIS));
        objPanelContainer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        objButtonPanel = new JPanel();
        objButtonPanel.setLayout(new GridBagLayout());
        objButtonPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 2, 1));
        objButtonPanel.setMaximumSize(new Dimension(1000, 1000));

        GridBagConstraints objGridBagConstraints = new GridBagConstraints();
        objGridBagConstraints.fill = GridBagConstraints.BOTH;
        objGridBagConstraints.weightx = 0.5;
        objGridBagConstraints.weighty = 0.5;
        objGridBagConstraints.insets = new Insets(0, 1, 0, 1);

        objButtonPanel.add(btnStats, objGridBagConstraints);
        objButtonPanel.add(btnDR, objGridBagConstraints);
        objButtonPanel.add(btnIFT, objGridBagConstraints);
        objButtonPanel.add(btnTH, objGridBagConstraints);
        objButtonPanel.add(btnTK, objGridBagConstraints);
        objButtonPanel.add(btnMC, objGridBagConstraints);
        objButtonPanel.add(btnTC, objGridBagConstraints);
        objButtonPanel.add(btnRally, objGridBagConstraints);
        objButtonPanel.add(btnCC, objGridBagConstraints);
        objButtonPanel.add(btndr, objGridBagConstraints);
        objButtonPanel.add(btnSA, objGridBagConstraints);
        objButtonPanel.add(btnRS, objGridBagConstraints);

        edtInputText = new JTextField(60);
        edtInputText.setFocusTraversalKeysEnabled(false);
        edtInputText.addActionListener(e -> {
            send(formatChat(e.getActionCommand()));
            edtInputText.setText(""); //$NON-NLS-1$
        });

        edtInputText.setMaximumSize(new Dimension(
            edtInputText.getMaximumSize().width,
            edtInputText.getPreferredSize().height
        ));

        scroll.setViewportView(conversationPane);

        objPanelContainer.add(objButtonPanel);

        GroupLayout objGroupLayout = new GroupLayout(this);
        setLayout(objGroupLayout);
        objGroupLayout.setHorizontalGroup(
            objGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(scroll)
                .addComponent(objPanelContainer, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(edtInputText)
            );
        objGroupLayout.setVerticalGroup(
            objGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(
            objGroupLayout.createSequentialGroup()
                .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(objPanelContainer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(edtInputText, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
        );
    }

    // Is this still used?
    @Deprecated
    public JPanel getButtonPanel() {  //used by SASLDice extension
        return objButtonPanel;
    }

    private void SetButtonsFonts(Font objFont) {
        btnStats.setFont(objFont);
        btnDR.setFont(objFont);
        btnIFT.setFont(objFont);
        btnTH.setFont(objFont);
        btnTK.setFont(objFont);
        btnMC.setFont(objFont);
        btnRally.setFont(objFont);
        btnCC.setFont(objFont);
        btnTC.setFont(objFont);
        btndr.setFont(objFont);
        btnSA.setFont(objFont);
        btnRS.setFont(objFont);
    }

    //Is this still used?
    @Deprecated
    private JButton CreateInfoButton(String strCaption, String strTooltip, final String strMsg, KeyStroke objKeyStroke) {  // used by SASLDice extension
        JButton btn = new JButton(strCaption);

        btn.setPreferredSize(new Dimension(90, 25));
        btn.setMargin(new Insets(btn.getMargin().top, 0, btn.getMargin().bottom, 0));

        ActionListener objAL = e -> send(formatChat(strMsg));
        btn.addActionListener(objAL);
        KeyStrokeListener objListener = new KeyStrokeListener(objAL);
        objListener.setKeyStroke(objKeyStroke);
        AddHotKeyToTooltip(btn, objListener, strTooltip);
        btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(objListener);
        return btn;
    }

    private JButton CreateStatsDiceButton(KeyStroke keyStroke) {
        JButton btn = new JButton("");
        btn.setMinimumSize(new Dimension(5, 30));
        btn.setMargin(new Insets(0, 0, 0, -1));

        try {
            btn.setIcon(new ImageIcon(Op.load("stat.png").getImage(null)));
        }
        catch (Exception ignored) {
        }
        ActionListener objAL = e -> {
            try {
                ASLDiceBot objDice = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();
                if (objDice != null) {
                    objDice.statsToday();
                }
            }
            catch (Exception ignored) {
            }
        };

        btn.addActionListener(objAL);
        KeyStrokeListener Listener = new KeyStrokeListener(objAL);
        Listener.setKeyStroke(keyStroke);
        AddHotKeyToTooltip(btn, Listener, "Dice rolls stats");
        btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(Listener);

        return btn;
    }

    public JButton CreateChatterDiceButton(String strImage, String strCaption, String strTooltip, KeyStroke keyStroke, final boolean bDice, final String strCat)
    {
        JButton btn = new JButton(strCaption);
        btn.setMinimumSize(new Dimension(5, 30));
        btn.setMargin(new Insets(0, 0, 0, -1));
        try {
            if (!strImage.isEmpty()) {
                btn.setIcon(new ImageIcon(Op.load(strImage).getImage(null)));
            }
        }
        catch (Exception ignored) {
        }
        ActionListener objAL = e -> {
            try {
                ASLDiceBot objDice = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();
                if (objDice != null) {
                    if (bDice) {
                        objDice.DR(strCat);
                    }
                    else {
                        objDice.dr(strCat);
                    }
                }
            }
            catch (Exception ignored) {
            }
        };

        btn.addActionListener(objAL);
        KeyStrokeListener Listener = new KeyStrokeListener(objAL);
        Listener.setKeyStroke(keyStroke);
        AddHotKeyToTooltip(btn, Listener, strTooltip);
        btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(Listener);

        return btn;
    }

    private void AddHotKeyToTooltip(JButton objButton, KeyStrokeListener objListener, String strTooltipText) {
        if (objListener.getKeyStroke() != null) {
            objButton.setToolTipText(strTooltipText + " [" + HotKeyConfigurer.getString(objListener.getKeyStroke()) + "]");
        }
    }

    protected void makeASLStyleSheet(Font f) {
        if (style != null) {
            if (f == null) {
                if (myFont == null) {
                    f = new Font("SansSerif", Font.PLAIN, 12);
                    myFont = f;
                }
                else {
                    f = myFont;
                }
            }

            addStyle(".msgcategory", f, Color.black, "bold", 0);
            addStyle(".msguser", f, myChat, "bold", 0);
            addStyle(".msgspecial", f, gameMsg, "bold", 0);

            style.addRule(
                " .tbl { border:0px solid #C0C0C0; border-collapse:collapse; border-spacing:0px; padding:0px; background:#CCFFCC;}" +
                " .tbl th { border:1px solid #C0C0C0; padding:5px; background:#FFFF66;}" +
                " .tbl td {border:1px solid #C0C0C0; padding:5px; text-align: right;}" +
                " .tbl tr.total {border:2px solid #black; background:#CCFFFF;}" +
                " .tbl td.up {border-top:2px solid black; padding:5px; font-weight: bold; text-align: right;}"
            );
        }
    }

    @Override
    protected String formatChat(String text) {
        final String id = GlobalOptions.getInstance().getPlayerId();
        return "<" + (id.length() == 0 ? "(" + getAnonymousUserName() + ")" : id) + "> - " + text; //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public JTextField getInputField() {
        return edtInputText;
    }

    String[] FindUser (String strVal) {
        String[] strRetValue = new String[] {strVal,"",""};

        int iUserStart = strVal.indexOf("<");
        int iUserEnd = strVal.indexOf(">");

        if (iUserStart != -1 && iUserEnd != -1) {
            strRetValue[0] = strVal.substring(0, iUserStart + 1);
            strRetValue[1] = strVal.substring(iUserStart + 1, iUserEnd);
            strRetValue[2] = strVal.substring(iUserEnd+1);
        }

        return strRetValue;
    }

    @Override
    public void show(String s) {
        if (SwingUtilities.isEventDispatchThread()) {
            doShow(s);
        }
        else {
            SwingUtilities.invokeLater(() -> doShow(s));
        }
    }

    //temporary - to fix VASSAL issues parsing messages pertaining to concealed counters
    private void doShow(String s) {
        try {
            if (s.length() > 0) {

                if (s.startsWith("!!")) {  // dice stats button has been clicked
                    s = makeTableString(s);
                }
                else if (s.startsWith("*** 3d6 = ")) {
                    //Parse3d6(s);
                }
                else if (s.startsWith("*** (")) {
                    ParseNewDiceRoll(s);
                    s = makeMessageString();
                }
                else if (s.startsWith("<")) {
                    ParseUserMsg(s);
                    s = makeMessageString();
                }
                else if (s.startsWith("-")) {
                    ParseSystemMsg(s);
                }
                else if (s.startsWith("*")) {
                    s = ParseMoveMsg(s);
                }
                else {
                    //ParseDefaultMsg(s);
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        s = s.trim();
        String style;
        boolean htmallowed;
        if (!s.isEmpty()) {
            if (s.startsWith("*")) {
                htmallowed = QuickColors.getQuickColor(s, "*") >= 0 || GlobalOptions.getInstance().chatterHTMLSupport();
                style = getQuickColorHTMLStyleLocal(s, "*");
                s = stripQuickColorTagLocal(s, "*");
            }
            else if (s.startsWith("-")) {
                htmallowed = true;
                //dirty quick fix for system messages not displaying with correct fonts, colors
                style = "sys";
                s = QuickColors.stripQuickColorTag(s, "-");
            }
            else {
                style = getChatStyle(s);
                htmallowed = false;
            }
        }
        else {
            style = "msg";
            htmallowed = false;
        }

        if (!htmallowed) {
            s = s.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        }

        String keystring = Resources.getString("PlayerRoster.observer");
        String replace = keystring.replace("<", "&lt;").replace(">", "&gt;");
        if (!replace.equals(keystring)) {
            s = s.replace(keystring, replace);
        }

        try {
            kit.insertHTML(doc, doc.getLength(), "\n<div class=" + style + ">" + s + "</div>", 0, 0, (HTML.Tag)null);
        }
        catch (IOException | BadLocationException var7) {
            ErrorDialog.bug(var7);
        }

        conversationPane.repaint();
    }

    public static String stripQuickColorTagLocal(String s, String prefix) {
        final String[] QUICK_COLOR_REGEX = new String[]{"\\|", "!", "~", "`"};
        int quickIndex = getQuickColorLocal(s, prefix);
        return quickIndex < 0 ? s : s.replaceFirst(QUICK_COLOR_REGEX[quickIndex], "");
    }

    public static int getQuickColorLocal(String s, String prefix) {
        if (!StringUtils.isEmpty(s) && !s.isBlank()) {
            if (StringUtils.isEmpty(prefix)) {
                return getQuickColorLocal(s);
            }
            else if (!s.startsWith(prefix)) {
                return -1;
            }
            else {
                String s2 = s.substring(prefix.length()).trim();
                return s2.isEmpty() ? -1 : getQuickColorLocal(s2.charAt(0));
            }
        }
        else {
            return -1;
        }
    }

    public static int getQuickColorLocal(String s) {
        if (StringUtils.isEmpty(s)) {
            return -1;
        }
        else {
            String s2 = s.trim();
            return s2.isEmpty() ? -1 : getQuickColorLocal(s2.charAt(0));
        }
    }

    public static int getQuickColorLocal(char c) {
        return "|!~`".indexOf(c);
    }

    public static String getQuickColorHTMLStyleLocal(String s, String prefix) {
        int quickIndex = getQuickColorLocal(s, prefix);
        Object var10000 = quickIndex <= 0 ? "" : quickIndex + 1;
        return "msg" + var10000;
    }

    private void ParseSystemMsg(String strMsg) {
        try {
            //StyleConstants.setForeground(objMainStyle, clrSystemMsg);
            //objDocument.insertString(objDocument.getLength(), "\n" + strMsg, objMainStyle);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void ParseUserMsg(String strMsg) {
        try {
            String[] strParts = FindUser(strMsg);

            if (!strParts[1].isEmpty() && !strParts[2].isEmpty()) {
                msgpartCategory = "";
                msgpartCdice = "";
                msgpartWdice = "";
                msgpartSAN = "";
                msgpartDiceImage = "";
                msgpartSpecial = "";
                msgpartUser = strParts[1];
                msgpartRest = strParts[2];
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String ParseMoveMsg(String strMsg) {
        // test for html tags that must be removed
        int iUserStart = 0;
        int iUserEnd = 0;
        do {
            try {
                iUserStart = strMsg.indexOf("<");
                iUserEnd = strMsg.indexOf(">");

                if (iUserStart != -1 && iUserEnd != -1) {
                    String deletestring = strMsg.substring(iUserStart, iUserEnd+1);
                    strMsg = strMsg.replace(deletestring, "");
                    iUserStart = 0;
                    iUserEnd = 0;
                }
                else if (iUserStart == -1 && iUserEnd != -1) {
                    break;
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        } while (iUserStart != -1 && iUserEnd != -1);
        //test
        return strMsg;
    }

    private void ParseNewDiceRoll(String strMsg) {
        // *** (Other DR) 4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)
        String strCategory, strDice, strUser, strSAN = "";
        int iFirstDice, iSecondDice;
        msgpartCategory = null;
        msgpartUser = null;
        msgpartCdice = null;
        msgpartWdice = null;
        msgpartSpecial = null;
        msgpartRest = null;
        msgpartDiceImage = null;
        Map<DiceType, Integer> otherDice = new HashMap<>();
        try {
            String strRestOfMsg = strMsg.substring("*** (".length()); // Other DR) 4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

            int iPos = strRestOfMsg.indexOf(" DR) ");

            if (iPos != -1) {
                strCategory = strRestOfMsg.substring(0, iPos);
                strRestOfMsg = strRestOfMsg.substring(iPos + " DR) ".length()); //4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                iPos = strRestOfMsg.indexOf(" ***");

                if (iPos != -1) {
                    strDice = strRestOfMsg.substring(0, iPos);
                    strRestOfMsg = strRestOfMsg.substring(iPos + " ***".length());//   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                    if (strDice.length() == 3 || strDice.length() == 5) {
                        String[] strDiceArr = strDice.split(",");

                        if (strDiceArr.length == 2 || (strDiceArr.length == 3 && environment.dustInEffect())) {
                            iFirstDice = Integer.parseInt(strDiceArr[0]);
                            iSecondDice = Integer.parseInt(strDiceArr[1]);
                            if (environment.dustInEffect() && strDiceArr.length == 3) {
                                otherDice.put(DiceType.OTHER_DUST, Integer.parseInt(strDiceArr[2]));
                            }

                            if (iFirstDice > 0
                                    && iFirstDice < 7
                                    && iSecondDice > 0
                                    && iSecondDice < 7) {
                                String[] strParts = FindUser(strRestOfMsg);

                                ArrayList<String> specialMessages = new ArrayList<>();

                                if (!strParts[1].isEmpty() && !strParts[2].isEmpty()) {
                                    strUser = strParts[1];
                                    strRestOfMsg = strParts[2]; // >      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                                    strRestOfMsg = strRestOfMsg.replace(">", " ").trim(); //Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)


                                    // Add special event hints, if necessary
                                    // First, SAN, which should not trigger for Rally, TK and CC rolls
                                    // and should happen on "Only Sniper" setting and in Full ASL mode
                                    if (DRNotificationLevel == 3 || DRNotificationLevel == 1) {
                                        if (!strCategory.equals("TK") && !strCategory.equals("CC") && !strCategory.equals("Rally")) {
                                            if (strRestOfMsg.startsWith("Axis SAN")) {
                                                strSAN = "Axis SAN";
                                                specialMessages.add("Axis SAN");
                                                strRestOfMsg = strRestOfMsg.substring("Axis SAN".length());
                                            }
                                            else if (strRestOfMsg.startsWith("Allied SAN")) {
                                                strSAN = "Allied SAN";
                                                specialMessages.add("Allied SAN");
                                                strRestOfMsg = strRestOfMsg.substring("Allied SAN".length());
                                            }
                                            else if (strRestOfMsg.startsWith("Axis/Allied SAN")) {
                                                strSAN = "Axis/Allied SAN";
                                                specialMessages.add("Axis/Allied SAN");
                                                strRestOfMsg = strRestOfMsg.substring("Axis/Allied SAN".length());
                                            }
                                        }

                                        if (strCategory.equals("TC")) {
                                            if (strRestOfMsg.startsWith("Axis Booby Trap")) {
                                                strSAN = "Axis Booby Trap";
                                                specialMessages.add("Axis Booby Trap");
                                                strRestOfMsg = strRestOfMsg.substring("Axis Booby Trap".length());
                                            }
                                            else if (strRestOfMsg.startsWith("Allied Booby Trap")) {
                                                strSAN = "Allied Booby Trap";
                                                specialMessages.add("Allied Booby Trap");
                                                strRestOfMsg = strRestOfMsg.substring("Allied Booby Trap".length());
                                            }
                                            else if (strRestOfMsg.startsWith("Axis/Allied Booby Trap")) {
                                                strSAN = "Axis/Allied Booby Trap";
                                                specialMessages.add("Axis/Allied Booby Trap");
                                                strRestOfMsg = strRestOfMsg.substring("Axis/Allied Booby Trap".length());
                                            }
                                        }
                                    }
                                    msgpartSAN = strSAN;
                                    // ALL of these happen only in Starter Kit mode or Full ASL mode
                                    if (DRNotificationLevel >= 2) {
                                        // For TH rolls only, show possible hit location, Unlikely hit and multiple hit
                                        switch (strCategory) {
                                        case "TH":
                                            if (iFirstDice == iSecondDice) {
                                                // Starter Kit + Full ASL
                                                if (iFirstDice == 1) {
                                                    specialMessages.add("Unlikely Hit (C3.6)");
                                                }
                                                // Full ASL only
                                                if (DRNotificationLevel == 3) {
                                                    specialMessages.add("Multiple Hits 15..40mm (C3.8)");
                                                }
                                            }
                                            if (iFirstDice < iSecondDice) {
                                                specialMessages.add("Turret");
                                            }
                                            else {
                                                specialMessages.add("Hull");
                                            }
                                            HandleSpecialMessagesForOtherDice(strCategory, specialMessages, iFirstDice, iSecondDice, otherDice);

                                            break;
                                        case "TK":
                                            if (iFirstDice == iSecondDice) {
                                                if (iFirstDice == 6) {
                                                    specialMessages.add("Dud (C7.35)");
                                                }
                                            }
                                            break;
                                        case "MC":
                                            // Full ASL only
                                            if (iFirstDice == 1 && iSecondDice == 1 && DRNotificationLevel == 3) {
                                                specialMessages.add("Heat of Battle (A15.1)");
                                            }
                                            // Starter Kit & Full ASL
                                            else if (iFirstDice == 6 && iSecondDice == 6) {
                                                specialMessages.add("Casualty MC (A10.31)");
                                            }
                                            HandleSpecialMessagesForOtherDice(strCategory, specialMessages, iFirstDice, iSecondDice, otherDice);
                                            break;
                                        case "TC":

                                            break;
                                        case "Rally":
                                            // Full ASL only
                                            if (iFirstDice == 1 && iSecondDice == 1 && DRNotificationLevel == 3) {
                                                specialMessages.add("Heat of Battle (A15.1) or Field Promotion (A18.11)");
                                            }
                                            // Starter Kit + Full ASL
                                            else if (iFirstDice == 6 && iSecondDice == 6) {
                                                specialMessages.add("Fate -> Casualty Reduction (A10.64)");
                                            }

                                            break;
                                        case "IFT":
                                            // check for cowering
                                            if (iFirstDice == iSecondDice) {
                                                // Full ASL only
                                                if (iFirstDice == 1 && DRNotificationLevel == 3) {
                                                    specialMessages.add("Unlikely Kill vs * (A7.309)");
                                                }
                                                // Starter Kit + Full ASL
                                                specialMessages.add("Cower if MMC w/o LDR");
                                            }
                                            HandleSpecialMessagesForOtherDice(strCategory, specialMessages, iFirstDice, iSecondDice, otherDice);
                                            break;
                                        case "CC":
                                            // Full ASL only
                                            if (iFirstDice == 1 && iSecondDice == 1 && DRNotificationLevel == 3) {
                                                specialMessages.add("Infiltration (A11.22), Field Promotion (A18.12), Unlikely Kill (A11.501)");
                                            } else if (iFirstDice == 6 && iSecondDice == 6 && DRNotificationLevel == 3) {
                                                specialMessages.add("Infiltration (A11.22)");
                                            }
                                            break;
                                        }
                                    }

                                    // check if SASL Dice button clicked and if so ask for special message string - SASL Dice buttons are created via extension
                                    if (DRNotificationLevel == 3 && strCategory.equals("EP")){
                                        if (iFirstDice == iSecondDice) {
                                            switch (iFirstDice) {
                                            case 1:
                                                specialMessages.add("Green and Conscript Units Panic");
                                                break;
                                            case 2:
                                                specialMessages.add("2nd Line, Partisan, Green, and Conscript Units Panic");
                                                break;
                                            case 3:
                                            case 4:
                                                specialMessages.add("1st Line, 2nd Line, Partisan, Green, and Conscript Units Panic");
                                                break;
                                            case 5:
                                            case 6:
                                                specialMessages.add("Any Unit Panics");
                                                break;
                                            }
                                        }
                                    }
                                    // Construct Special Message string
                                    StringBuilder strSpecialMessages = new StringBuilder();
                                    for (int i = 0; i < specialMessages.size(); ++i) {
                                        strSpecialMessages.append(specialMessages.get(i));
                                        if (i < specialMessages.size() - 1) {
                                            strSpecialMessages.append(", ");
                                        }
                                    }
                                    msgpartCategory = BEFORE_CATEGORY + strCategory;

                                    if (bUseDiceImages) {
                                        msgpartCdice = Integer.toString(iFirstDice);
                                        msgpartWdice = Integer.toString(iSecondDice);

                                        PaintIcon(iFirstDice, DiceType.COLORED);
                                        PaintIcon(iSecondDice, DiceType.WHITE);
                                        //Add any other dice required
                                        for (Map.Entry<DiceType, Integer> entry : otherDice.entrySet()) {
                                            PaintIcon(entry.getValue(), entry.getKey());
                                        }
                                    }
                                    else {
                                        msgpartCdice = Integer.toString(iFirstDice);
                                        msgpartWdice = Integer.toString(iSecondDice);
                                    }
                                    msgpartUser = strUser;
                                    msgpartSpecial = strSpecialMessages.toString();

                                    if (bShowDiceStats) {
                                        msgpartRest = strRestOfMsg;
                                    }
                                    FireDiceRoll();
                                }
                            }
                        }
                    }
                }
            }
            else { // *** (Other dr) 3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)
                //reset SAN message to the latest state for dice over map
                msgpartSAN = strSAN;
                iPos = strRestOfMsg.indexOf(" dr) ");

                if (iPos != -1) {
                    strCategory = strRestOfMsg.substring(0, iPos);
                    strRestOfMsg = strRestOfMsg.substring(iPos + " dr) ".length()); //3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)

                    iPos = strRestOfMsg.indexOf(" ***");

                    if (iPos != -1) {
                        strDice = strRestOfMsg.substring(0, iPos);
                        strRestOfMsg = strRestOfMsg.substring(iPos + " ***".length());//   <FredKors>      [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                        if (strDice.length() == 1) {
                            int iDice = Integer.parseInt(strDice);

                            if (iDice > 0 && iDice < 7) {
                                String[] strParts = FindUser(strRestOfMsg);

                                if (!strParts[1].isEmpty() && !strParts[2].isEmpty()) {
                                    strUser = strParts[1];

                                    msgpartCategory = BEFORE_CATEGORY + strCategory;

                                    if (bUseDiceImages) {
                                        msgpartCdice = (strDice);
                                        msgpartWdice = "-1";
                                        PaintIcon(iDice, DiceType.SINGLE);
                                    }
                                    else {
                                        msgpartCdice = strDice;
                                    }
                                    msgpartWdice = "-1";

                                    msgpartUser = strUser;
                                    // added by DR 2018 to add chatter text on Sniper Activation dr
                                    if (strCategory.equals("SA")) {
                                        String sniperstring = "";
                                        if (iDice == 1) {
                                            sniperstring ="Eliminates SMC, Dummy stack, Sniper; Stuns & Recalls CE crew; breaks MMC & Inherent crew of certain vehicles; immobilizes unarmored vehicle (A14.3)" ;
                                        }
                                        else if (iDice == 2) {
                                            sniperstring ="Eliminates Dummy stack; Wounds SMC; Stuns CE crew; pins MMC, Inherent crew of certain vehicles, Sniper (A14.3)" ;
                                        }
                                        msgpartSpecial = sniperstring;
                                    }
                                    if (bShowDiceStats) {
                                        msgpartRest = strRestOfMsg;
                                    }

                                    FireDiceRoll();
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void HandleSpecialMessagesForOtherDice(final String strCategory, final ArrayList<String> specialMessages,
            final int iFirstDice, final int iSecondDice, final Map<DiceType, Integer> otherDice)
    {
        int total = iFirstDice + iSecondDice;
        int unmodifiedTotal = total;
        final String SPACE = " ";
        // Dust
        if (environment.dustInEffect() && DRNotificationLevel == 3 && !otherDice.isEmpty()) {
            switch (strCategory) {
            case "TH":
            case "IFT":
                if (environment.isSpecialDust()) {
                    total += environment.getSpecialDust(otherDice.get(DiceType.OTHER_DUST));
                }
                else {
                    if (environment.isLightDust()) {
                        total += Environment.getLightDust(otherDice.get(DiceType.OTHER_DUST));
                    }
                    else {
                        total += Environment.getModerateDust(otherDice.get(DiceType.OTHER_DUST));
                    }
                }
                specialMessages.add(environment.getCurrentDustLevel().toString() + SPACE);
                break;

            case "MC":
                if (environment.isSpecialDust()) {
                    total -= environment.getSpecialDust(otherDice.get(DiceType.OTHER_DUST));
                }
                else {
                    if (environment.isLightDust()) {
                        total -= Environment.getLightDust(otherDice.get(DiceType.OTHER_DUST));
                    }
                    else {
                        total -= Environment.getModerateDust(otherDice.get(DiceType.OTHER_DUST));
                    }
                }
                specialMessages.add(environment.getCurrentDustLevel().toString() +" - if interdicted, MC is " + total);
                break;
            }
        }
        // Night
        if (environment.isNight() && DRNotificationLevel == 3) {
            switch (strCategory) {
            case "TH":
            case "IFT":
                total += 1;
                specialMessages.add("+1 Night LV"  + SPACE);
                break;
            }
        }
        // LV
        if (environment.isLV()) {
            LVLevel lvLevel = environment.getCurrentLVLevel();
            switch (strCategory) {
            case "TH":
            case "IFT":
                switch (lvLevel) {
                case DAWN_DUSK:
                    total += 1;
                    break;
                }
                specialMessages.add(lvLevel + SPACE);
                break;
            }
        }
        // Fog
        if (environment.isFog()) {
            switch (strCategory) {
            case "TH":
            case "IFT":
                {
                    FogLevel fogLevel = environment.getCurrentFogLevel();
                    specialMessages.add(fogLevel.toString() + SPACE);
                    break;
                }
            }
        }
        //Heat Haze
        if (environment.isHeatHaze()) {
            switch (strCategory) {
            case "TH":
            case "IFT":
                {
                    HeatHazeLevel heatHazeLevel = environment.getCurrentHeatHazeLevel();
                    specialMessages.add(heatHazeLevel.toString() + SPACE);
                    break;
                }
            }
        }
        //Sun Blindness
        if (environment.isSunBlindness()) {
            switch (strCategory) {
            case "TH":
            case "IFT":
                {
                    SunBlindnessLevel sunBlindnessLevel = environment.getCurrentSunBlindnessLevel();
                    specialMessages.add(sunBlindnessLevel.toString() + SPACE);
                    break;
                }
            }
        }

        if (unmodifiedTotal < total || environment.dustInEffect()) {
            specialMessages.add("Total: " + total);
        }
    }

    private void PaintIcon(int iDice, DiceType diceType) {
        try {
            ASLDie die = diceFactory.getASLDie(diceType);
            String dicefile = die.getDieHTMLFragment(iDice);
            if (msgpartDiceImage == null) {
                msgpartDiceImage = "<img alt=\"alt text\" src=\"" + dicefile + "\">";
            }
            else {
                msgpartDiceImage += "&nbsp <img alt=\"alt text\" src=\"" + dicefile + "\"> &nbsp";
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String makeMessageString() {
        // need to add html formatting

        if (msgpartCategory == null) {
            msgpartCategory = "";
        }
        if (msgpartCdice == null) {
            msgpartCdice = "";
        }
        if (msgpartWdice == null) {
            msgpartWdice = "";
        }
        if (msgpartUser == null) {
            msgpartUser = "";
        }
        if (msgpartSpecial == null) {
            msgpartSpecial = "";
        }
        if (msgpartRest == null) {
            msgpartRest = "";
        }
        if (msgpartWdice.equals("-1")){
            msgpartWdice = "";
        }

        String catstyle = "msgcategory";
        String userstyle = getUserStyle();
        String specialstyle = "msgspecial";  //text-decoration: underline";  //<p style="text-decoration: underline;">This text will be underlined.</p>
        if (bUseDiceImages) {
            return "*~<span class=" + userstyle + ">" + msgpartDiceImage + "</span>"
                + "<span class=" + catstyle + ">" + msgpartCategory + "</span>"
                + "<span class=" + userstyle + ">" + USER_SPACING_PADDING + msgpartUser+ "</span>"
                + " " + "<u>"
                + "<span class=" + specialstyle + ">" + msgpartSpecial + "</span>"
                + "</u>" + " "
                + "<span class=" + userstyle + ">" + msgpartRest + "</span>";
        }
        else {
            return "*~<span class=" + catstyle + ">" + msgpartCategory + "</span>"
                + " " + msgpartCdice + " " + msgpartWdice + " "
                + "<span class=" + userstyle + ">" + USER_SPACING_PADDING + msgpartUser + "</span>"
                + " " + "<u>"
                + "<span class=" + specialstyle + ">" + msgpartSpecial + "</span>"
                + "</u>" + " "
                + "<span class=" + userstyle + ">" + msgpartRest + "</span>";
        }
    }

    protected String getUserStyle() {
        final String me = GlobalOptions.getInstance().getPlayerId();
        if (msgpartUser.equals(me)) {
            return "mychat";
        }
        return "other";
    }

    private String makeTableString(String strMsg){
        strMsg = strMsg.substring(2);  // strip out "!!"
        String tablestyle = "tbl";
        return "*~<span class=" + tablestyle + ">" + strMsg + "</span>";
    }

    /**
     * Expects to be added to a GameModule.  Adds itself to the
     * controls window and registers itself as a
     * {@link CommandEncoder} */
    @Override
    public void addTo(Buildable b) {
        GameModule objGameModule = (GameModule) b;
        if (objGameModule.getChatter() != null) {
            // deleted code here which removed VASSAL elements but getChatter is always null at this point
        }

        objGameModule.setChatter(this);
        objGameModule.addCommandEncoder(this);
        objGameModule.addKeyStrokeSource(new KeyStrokeSource(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT));
        objGameModule.getPlayerWindow().addChatter(this);
        objGameModule.getControlPanel().add(this, BorderLayout.CENTER);
        final Prefs objModulePrefs = objGameModule.getPrefs();

        // font pref
        FontConfigurer objChatFontConfigurer;
        FontConfigurer objChatFontConfigurer_Exist = (FontConfigurer)objModulePrefs.getOption("ChatFont");
        if (objChatFontConfigurer_Exist == null) {
            objChatFontConfigurer = new FontConfigurer(CHAT_FONT, Resources.getString("Chatter.chat_font_preference")); //$NON-NLS-1$ //$NON-NLS-2$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objChatFontConfigurer); //$NON-NLS-1$
        }
        else {
            objChatFontConfigurer = objChatFontConfigurer_Exist;
        }
        objChatFontConfigurer.addPropertyChangeListener(evt -> {
            setFont((Font) evt.getNewValue());
            makeStyleSheet((Font) evt.getNewValue());
            makeASLStyleSheet((Font) evt.getNewValue());
            send(" ");
            send("- Chatter font changed");
            send(" ");
        });
        objChatFontConfigurer.fireUpdate();
        // buttons font pref
        FontConfigurer objButtonsFontConfigurer;
        FontConfigurer objButtonsFontConfigurer_Exist = (FontConfigurer)objModulePrefs.getOption("ButtonFont");
        if (objButtonsFontConfigurer_Exist == null) {
            objButtonsFontConfigurer = new FontConfigurer(BUTTON_FONT, "Chatter's dice buttons font: "); //$NON-NLS-1$ //$NON-NLS-2$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objButtonsFontConfigurer); //$NON-NLS-1$
        }
        else {
            objButtonsFontConfigurer = objButtonsFontConfigurer_Exist;
        }
        objButtonsFontConfigurer.addPropertyChangeListener(evt -> SetButtonsFonts((Font) evt.getNewValue()));
        objButtonsFontConfigurer.fireUpdate();
        //background colour pref
        ColorConfigurer objBackgroundColor;
        ColorConfigurer objBackgroundColor_Exist = (ColorConfigurer)objModulePrefs.getOption(CHAT_BACKGROUND_COLOR);
        if (objBackgroundColor_Exist == null) {
            objBackgroundColor = new ColorConfigurer(CHAT_BACKGROUND_COLOR, "Background color: ", Color.white); //$NON-NLS-1$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objBackgroundColor); //$NON-NLS-1$
        }
        else {
            objBackgroundColor = objBackgroundColor_Exist;
        }
        clrBackground = (Color) objModulePrefs.getValue(CHAT_BACKGROUND_COLOR);
        objBackgroundColor.addPropertyChangeListener(e -> {
            clrBackground = (Color) e.getNewValue();
            conversationPane.setBackground(clrBackground);
        });
        objBackgroundColor.fireUpdate();
        // game message color pref
        Prefs globalPrefs = Prefs.getGlobalPrefs();
        ColorConfigurer gameMsgColor = new ColorConfigurer("HTMLgameMessage1Color", Resources.getString("Chatter.game_messages_preference"), Color.black);
        gameMsgColor.addPropertyChangeListener(e -> {
            gameMsg = (Color)e.getNewValue();
            makeStyleSheet(null);
            makeASLStyleSheet(null);
        });
        globalPrefs.addOption(Resources.getString("Chatter.chat_window"), gameMsgColor);
        gameMsg = (Color)globalPrefs.getValue("HTMLgameMessage1Color");

        // sys messages pref
        ColorConfigurer objSystemMsgColor;
        ColorConfigurer objSystemMsgColor_Exist = (ColorConfigurer)objModulePrefs.getOption(SYS_MSG_COLOR);
        if (objSystemMsgColor_Exist == null) {
            objSystemMsgColor = new ColorConfigurer(SYS_MSG_COLOR, Resources.getString("Chatter.systemessage_preference"), new Color(160, 160, 160)); //$NON-NLS-1$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objSystemMsgColor); //$NON-NLS-1$
        }
        else {
            objSystemMsgColor = objSystemMsgColor_Exist;
        }
        systemMsg = (Color) objModulePrefs.getValue(SYS_MSG_COLOR);
        makeStyleSheet(null);
        objSystemMsgColor.addPropertyChangeListener(e -> {
            systemMsg = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // myChat preference
        ColorConfigurer objMyChatColor;
        ColorConfigurer objMyChatColor_Exist = (ColorConfigurer)objModulePrefs.getOption(MY_CHAT_COLOR);
        if (objMyChatColor_Exist == null) {
            objMyChatColor = new ColorConfigurer(MY_CHAT_COLOR, "My Name and Text Messages" , Color.gray); //$NON-NLS-1$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objMyChatColor); //$NON-NLS-1$
        }
        else {
            objMyChatColor = objMyChatColor_Exist;
        }
        myChat = (Color) objModulePrefs.getValue(MY_CHAT_COLOR);
        makeStyleSheet(null);
        objMyChatColor.addPropertyChangeListener(e -> {
            myChat = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // other chat preference
        ColorConfigurer objOtherChatColor;
        ColorConfigurer objOtherChatColor_Exist = (ColorConfigurer)objModulePrefs.getOption(OTHER_CHAT_COLOR);
        if (objOtherChatColor_Exist == null) {
            objOtherChatColor = new ColorConfigurer(OTHER_CHAT_COLOR, Resources.getString("Chatter.other_text_preference"), Color.black); //$NON-NLS-1$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objOtherChatColor); //$NON-NLS-1$
        }
        else {
            objOtherChatColor = objOtherChatColor_Exist;
        }
        otherChat = (Color) objModulePrefs.getValue(OTHER_CHAT_COLOR);
        makeStyleSheet(null);
        objOtherChatColor.addPropertyChangeListener(e -> {
            otherChat = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // dice chat pref
        ColorConfigurer objDiceChatColor;
        ColorConfigurer objDiceChatColor_Exist = (ColorConfigurer)objModulePrefs.getOption(DICE_CHAT_COLOR);
        if (objDiceChatColor_Exist == null) {
            objDiceChatColor = new ColorConfigurer(DICE_CHAT_COLOR, "Dice Results font color: ", Color.black); //$NON-NLS-1$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objDiceChatColor); //$NON-NLS-1$
        }
        else {
            objDiceChatColor = objDiceChatColor_Exist;
        }
        gameMsg5 = (Color) objModulePrefs.getValue(DICE_CHAT_COLOR);
        makeStyleSheet(null);
        objDiceChatColor.addPropertyChangeListener(e -> {
            gameMsg5 = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // dice images pref
        BooleanConfigurer objUseDiceImagesOption;
        BooleanConfigurer objUseDiceImagesOption_Exist = (BooleanConfigurer)objModulePrefs.getOption(USE_DICE_IMAGES);
        if (objUseDiceImagesOption_Exist == null) {
            objUseDiceImagesOption = new BooleanConfigurer(USE_DICE_IMAGES, "Use images for dice rolls", Boolean.TRUE);  //$NON-NLS-1$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objUseDiceImagesOption); //$NON-NLS-1$
        }
        else {
            objUseDiceImagesOption = objUseDiceImagesOption_Exist;
        }
        bUseDiceImages = (Boolean) (objModulePrefs.getValue(USE_DICE_IMAGES));
        objUseDiceImagesOption.addPropertyChangeListener(e -> bUseDiceImages = (Boolean) e.getNewValue());
        // dice stats pref
        BooleanConfigurer objShowDiceStatsOption;
        BooleanConfigurer objShowDiceStatsOption_Exist = (BooleanConfigurer)objModulePrefs.getOption(SHOW_DICE_STATS);
        if (objShowDiceStatsOption_Exist == null) {
            objShowDiceStatsOption = new BooleanConfigurer(SHOW_DICE_STATS, "Show dice stats after each dice rolls", Boolean.FALSE);  //$NON-NLS-1$
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objShowDiceStatsOption); //$NON-NLS-1$
        }
        else {
            objShowDiceStatsOption = objShowDiceStatsOption_Exist;
        }
        bShowDiceStats = (Boolean) (objModulePrefs.getValue(SHOW_DICE_STATS));
        objShowDiceStatsOption.addPropertyChangeListener(e -> bShowDiceStats = (Boolean) e.getNewValue());

        // coloured die pref
        StringEnumConfigurer coloredDiceColor;
        StringEnumConfigurer coloredDiceColor_Exist = (StringEnumConfigurer) objModulePrefs.getOption(COLORED_DICE_COLOR);
        if (coloredDiceColor_Exist == null) {
            coloredDiceColor = new StringEnumConfigurer(COLORED_DICE_COLOR, "Colored Die Color:", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), coloredDiceColor);
        }
        else {
            coloredDiceColor = coloredDiceColor_Exist;
        }
        coloredDiceColor.addPropertyChangeListener(e -> {
            clrColoredDiceColor = (String) e.getNewValue();
            if (clrColoredDiceColor != null) {
            diceFactory.setDieColor(DiceType.COLORED, DieColor.getEnum(clrColoredDiceColor));
            }
        });
        clrColoredDiceColor = objModulePrefs.getStoredValue("coloredDiceColor");
        if (clrColoredDiceColor != null) {
            diceFactory.setDieColor(DiceType.COLORED,DieColor.getEnum(clrColoredDiceColor));
        }

        // single die pref
        StringEnumConfigurer objColoredDieColor;
        StringEnumConfigurer objColoredDieColor_Exist = (StringEnumConfigurer)objModulePrefs.getOption(SINGLE_DIE_COLOR);
        if (objColoredDieColor_Exist == null) {
            objColoredDieColor = new StringEnumConfigurer(SINGLE_DIE_COLOR, "Single die color:  ", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objColoredDieColor); //$NON-NLS-1$
        }
        else {
            objColoredDieColor = objColoredDieColor_Exist;
        }
        objColoredDieColor.addPropertyChangeListener(e -> {
            clrSingleDieColor = (String) e.getNewValue();
            if (clrSingleDieColor != null) {
                diceFactory.setDieColor(DiceType.SINGLE, DieColor.getEnum(clrSingleDieColor));
            }
        });
        clrSingleDieColor = objModulePrefs.getStoredValue("singleDieColor");
        if (clrSingleDieColor != null) {
            diceFactory.setDieColor(DiceType.SINGLE, DieColor.getEnum(clrSingleDieColor));
        }

        // third die pref
        StringEnumConfigurer objThirdDieColor;
        objThirdDieColor = new StringEnumConfigurer(THIRD_DIE_COLOR, "Third die color:  ", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
        objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objThirdDieColor); //$NON-NLS-1$
        objThirdDieColor.addPropertyChangeListener(e -> {
            clrDustColoredDiceColor = (String) e.getNewValue();
            if (clrDustColoredDiceColor != null) {
                diceFactory.setDieColor(DiceType.OTHER_DUST, DieColor.getEnum(clrDustColoredDiceColor));
            }
        });
        clrDustColoredDiceColor = objModulePrefs.getStoredValue("thirdDieColor");
        if (clrDustColoredDiceColor != null) {
            diceFactory.setDieColor(DiceType.SINGLE,DieColor.getEnum(clrDustColoredDiceColor));
        }
        objThirdDieColor.fireUpdate();

        // rule set pref
        StringEnumConfigurer objSpecialDiceRollNotificationLevel = (StringEnumConfigurer)objModulePrefs.getOption(NOTIFICATION_LEVEL);
        final String[] DROptions = {
            "None",
            "Snipers only",
            "Starter Kit",
            "Full ASL"
        };
        if (objSpecialDiceRollNotificationLevel == null) {
            objSpecialDiceRollNotificationLevel = new StringEnumConfigurer(NOTIFICATION_LEVEL,
                    "Notify about special DRs: ", DROptions);
            objSpecialDiceRollNotificationLevel.setValue("Full ASL");
            objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), objSpecialDiceRollNotificationLevel);
        }
        for (int i = 0; i < DROptions.length; ++i) {
            if (DROptions[i].equals(objSpecialDiceRollNotificationLevel.getValueString())) {
                DRNotificationLevel = i;
                break;
            }
        }
        // just for access from inside the event handler
        final StringEnumConfigurer cfg = objSpecialDiceRollNotificationLevel;
        objSpecialDiceRollNotificationLevel.addPropertyChangeListener(e -> {
            for (int i = 0; i < DROptions.length; ++i) {
                if (DROptions[i].equals(cfg.getValueString())) {
                    DRNotificationLevel = i;
                    return;
                }
            }
            DRNotificationLevel = 3;
        });
        // Player Window pref
        objColoredDieColor.fireUpdate();
        final BooleanConfigurer AlwaysOnTop = new BooleanConfigurer("PWAlwaysOnTop", "Player Window (menus, toolbar, chat) is always on top in uncombined application mode (requires a VASSAL restart)", false);
        getGameModule().getPrefs().addOption(preferenceTabName, AlwaysOnTop);
    }

    @Override
    public void keyCommand(KeyStroke e) {
        if ((e.getKeyCode() == 0 || e.getKeyCode() == KeyEvent.CHAR_UNDEFINED) && !Character.isISOControl(e.getKeyChar())) {
            edtInputText.setText(edtInputText.getText() + e.getKeyChar());
        }
        else if (e.isOnKeyRelease()) {
            switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
                if (edtInputText.getText().length() > 0) {
                    send(formatChat(edtInputText.getText()));
                }

                edtInputText.setText(""); //$NON-NLS-1$
                break;

            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_DELETE:
                String s = edtInputText.getText();
                if (s.length() > 0) {
                    edtInputText.setText(s.substring(0, s.length() - 1));
                }
                break;
            }
        }
    }

    private void FireDiceRoll() {
        for (ChatterListener objListener : chatter_listeners) {
            objListener.DiceRoll(msgpartCategory, msgpartUser, msgpartSAN, Integer.parseInt(msgpartCdice), Integer.parseInt(msgpartWdice));
        }
    }

    public void addListener(ChatterListener toAdd) {
        chatter_listeners.add(toAdd);
    }

    public void removeListener(ChatterListener toRemove) {
        chatter_listeners.remove(toRemove);
    }

    public interface ChatterListener {
        void DiceRoll(String strCategory, String strUser, String strSAN, int iFirstDice, int iSecondDice);
    }
}
