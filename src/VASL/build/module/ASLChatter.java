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
    private final JPanel buttonPannel;
    private boolean useDiceImages;
    private boolean showDiceStats;

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

        JPanel panelContainer = new JPanel();
        panelContainer.setLayout(new BoxLayout(panelContainer, BoxLayout.LINE_AXIS));
        panelContainer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        buttonPannel = new JPanel();
        buttonPannel.setLayout(new GridBagLayout());
        buttonPannel.setBorder(BorderFactory.createEmptyBorder(1, 1, 2, 1));
        buttonPannel.setMaximumSize(new Dimension(1000, 1000));

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.5;
        gridBagConstraints.insets = new Insets(0, 1, 0, 1);

        buttonPannel.add(btnStats, gridBagConstraints);
        buttonPannel.add(btnDR, gridBagConstraints);
        buttonPannel.add(btnIFT, gridBagConstraints);
        buttonPannel.add(btnTH, gridBagConstraints);
        buttonPannel.add(btnTK, gridBagConstraints);
        buttonPannel.add(btnMC, gridBagConstraints);
        buttonPannel.add(btnTC, gridBagConstraints);
        buttonPannel.add(btnRally, gridBagConstraints);
        buttonPannel.add(btnCC, gridBagConstraints);
        buttonPannel.add(btndr, gridBagConstraints);
        buttonPannel.add(btnSA, gridBagConstraints);
        buttonPannel.add(btnRS, gridBagConstraints);

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

        panelContainer.add(buttonPannel);

        GroupLayout groupLayout = new GroupLayout(this);
        setLayout(groupLayout);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(scroll)
                .addComponent(panelContainer, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(edtInputText)
            );
        groupLayout.setVerticalGroup(
            groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(
            groupLayout.createSequentialGroup()
                .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(panelContainer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(edtInputText, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            )
        );
    }

    // Is this still used?
    @Deprecated
    public JPanel getButtonPanel() {  //used by SASLDice extension
        return buttonPannel;
    }

    private void SetButtonsFonts(Font font) {
        btnStats.setFont(font);
        btnDR.setFont(font);
        btnIFT.setFont(font);
        btnTH.setFont(font);
        btnTK.setFont(font);
        btnMC.setFont(font);
        btnRally.setFont(font);
        btnCC.setFont(font);
        btnTC.setFont(font);
        btndr.setFont(font);
        btnSA.setFont(font);
        btnRS.setFont(font);
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
        ActionListener al = e -> {
            try {
                ASLDiceBot dice = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();
                if (dice != null) {
                    dice.statsToday();
                }
            }
            catch (Exception ignored) {
            }
        };

        btn.addActionListener(al);
        KeyStrokeListener Listener = new KeyStrokeListener(al);
        Listener.setKeyStroke(keyStroke);
        AddHotKeyToTooltip(btn, Listener, "Dice rolls stats");
        btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(Listener);

        return btn;
    }

    public JButton CreateChatterDiceButton(String strImage, String strCaption, String tooltip, KeyStroke keyStroke, final boolean bDice, final String strCat)
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
        ActionListener al = e -> {
            try {
                ASLDiceBot dice = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();
                if (dice != null) {
                    if (bDice) {
                        dice.DR(strCat);
                    }
                    else {
                        dice.dr(strCat);
                    }
                }
            }
            catch (Exception ignored) {
            }
        };

        btn.addActionListener(al);
        KeyStrokeListener Listener = new KeyStrokeListener(al);
        Listener.setKeyStroke(keyStroke);
        AddHotKeyToTooltip(btn, Listener, tooltip);
        btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(Listener);

        return btn;
    }

    private void AddHotKeyToTooltip(JButton button, KeyStrokeListener listener, String tooltipText) {
        if (listener.getKeyStroke() != null) {
            button.setToolTipText(tooltipText + " [" + HotKeyConfigurer.getString(listener.getKeyStroke()) + "]");
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

    String[] FindUser (String val) {
        String[] retValue = new String[] {val,"",""};

        int userStart = val.indexOf("<");
        int userEnd = val.indexOf(">");

        if (userStart != -1 && userEnd != -1) {
            retValue[0] = val.substring(0, userStart + 1);
            retValue[1] = val.substring(userStart + 1, userEnd);
            retValue[2] = val.substring(userEnd+1);
        }

        return retValue;
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

    private void ParseSystemMsg(String msg) {
        try {
            //StyleConstants.setForeground(mainStyle, clrSystemMsg);
            //document.insertString(document.getLength(), "\n" + msg, mainStyle);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void ParseUserMsg(String msg) {
        try {
            String[] parts = FindUser(msg);

            if (!parts[1].isEmpty() && !parts[2].isEmpty()) {
                msgpartCategory = "";
                msgpartCdice = "";
                msgpartWdice = "";
                msgpartSAN = "";
                msgpartDiceImage = "";
                msgpartSpecial = "";
                msgpartUser = parts[1];
                msgpartRest = parts[2];
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String ParseMoveMsg(String msg) {
        // test for html tags that must be removed
        int userStart = 0;
        int userEnd = 0;
        do {
            try {
                userStart = msg.indexOf("<");
                userEnd = msg.indexOf(">");

                if (userStart != -1 && userEnd != -1) {
                    String deletestring = msg.substring(userStart, userEnd+1);
                    msg = msg.replace(deletestring, "");
                    userStart = 0;
                    userEnd = 0;
                }
                else if (userStart == -1 && userEnd != -1) {
                    break;
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        } while (userStart != -1 && userEnd != -1);
        //test
        return msg;
    }

    private void ParseNewDiceRoll(String msg) {
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
            String restOfMsg = msg.substring("*** (".length()); // Other DR) 4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

            int iPos = restOfMsg.indexOf(" DR) ");

            if (iPos != -1) {
                strCategory = restOfMsg.substring(0, iPos);
                restOfMsg = restOfMsg.substring(iPos + " DR) ".length()); //4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                iPos = restOfMsg.indexOf(" ***");

                if (iPos != -1) {
                    strDice = restOfMsg.substring(0, iPos);
                    restOfMsg = restOfMsg.substring(iPos + " ***".length());//   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

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
                                String[] parts = FindUser(restOfMsg);

                                ArrayList<String> specialMessages = new ArrayList<>();

                                if (!parts[1].isEmpty() && !parts[2].isEmpty()) {
                                    strUser = parts[1];
                                    restOfMsg = parts[2]; // >      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                                    restOfMsg = restOfMsg.replace(">", " ").trim(); //Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)


                                    // Add special event hints, if necessary
                                    // First, SAN, which should not trigger for Rally, TK and CC rolls
                                    // and should happen on "Only Sniper" setting and in Full ASL mode
                                    if (DRNotificationLevel == 3 || DRNotificationLevel == 1) {
                                        if (!strCategory.equals("TK") && !strCategory.equals("CC") && !strCategory.equals("Rally")) {
                                            if (restOfMsg.startsWith("Axis SAN")) {
                                                strSAN = "Axis SAN";
                                                specialMessages.add("Axis SAN");
                                                restOfMsg = restOfMsg.substring("Axis SAN".length());
                                            }
                                            else if (restOfMsg.startsWith("Allied SAN")) {
                                                strSAN = "Allied SAN";
                                                specialMessages.add("Allied SAN");
                                                restOfMsg = restOfMsg.substring("Allied SAN".length());
                                            }
                                            else if (restOfMsg.startsWith("Axis/Allied SAN")) {
                                                strSAN = "Axis/Allied SAN";
                                                specialMessages.add("Axis/Allied SAN");
                                                restOfMsg = restOfMsg.substring("Axis/Allied SAN".length());
                                            }
                                        }

                                        if (strCategory.equals("TC")) {
                                            if (restOfMsg.startsWith("Axis Booby Trap")) {
                                                strSAN = "Axis Booby Trap";
                                                specialMessages.add("Axis Booby Trap");
                                                restOfMsg = restOfMsg.substring("Axis Booby Trap".length());
                                            }
                                            else if (restOfMsg.startsWith("Allied Booby Trap")) {
                                                strSAN = "Allied Booby Trap";
                                                specialMessages.add("Allied Booby Trap");
                                                restOfMsg = restOfMsg.substring("Allied Booby Trap".length());
                                            }
                                            else if (restOfMsg.startsWith("Axis/Allied Booby Trap")) {
                                                strSAN = "Axis/Allied Booby Trap";
                                                specialMessages.add("Axis/Allied Booby Trap");
                                                restOfMsg = restOfMsg.substring("Axis/Allied Booby Trap".length());
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

                                    if (useDiceImages) {
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

                                    if (showDiceStats) {
                                        msgpartRest = restOfMsg;
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
                iPos = restOfMsg.indexOf(" dr) ");

                if (iPos != -1) {
                    strCategory = restOfMsg.substring(0, iPos);
                    restOfMsg = restOfMsg.substring(iPos + " dr) ".length()); //3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)

                    iPos = restOfMsg.indexOf(" ***");

                    if (iPos != -1) {
                        strDice = restOfMsg.substring(0, iPos);
                        restOfMsg = restOfMsg.substring(iPos + " ***".length());//   <FredKors>      [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                        if (strDice.length() == 1) {
                            int iDice = Integer.parseInt(strDice);

                            if (iDice > 0 && iDice < 7) {
                                String[] parts = FindUser(restOfMsg);

                                if (!parts[1].isEmpty() && !parts[2].isEmpty()) {
                                    strUser = parts[1];

                                    msgpartCategory = BEFORE_CATEGORY + strCategory;

                                    if (useDiceImages) {
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
                                    if (showDiceStats) {
                                        msgpartRest = restOfMsg;
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
        if (useDiceImages) {
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

    private String makeTableString(String msg){
        msg = msg.substring(2);  // strip out "!!"
        String tablestyle = "tbl";
        return "*~<span class=" + tablestyle + ">" + msg + "</span>";
    }

    /**
     * Expects to be added to a GameModule.  Adds itself to the
     * controls window and registers itself as a
     * {@link CommandEncoder} */
    @Override
    public void addTo(Buildable b) {
        GameModule gameModule = (GameModule) b;
        if (gameModule.getChatter() != null) {
            // deleted code here which removed VASSAL elements but getChatter is always null at this point
        }

        gameModule.setChatter(this);
        gameModule.addCommandEncoder(this);
        gameModule.addKeyStrokeSource(new KeyStrokeSource(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT));
        gameModule.getPlayerWindow().addChatter(this);
        gameModule.getControlPanel().add(this, BorderLayout.CENTER);
        final Prefs modulePrefs = gameModule.getPrefs();

        // font pref
        FontConfigurer chatFontConfigurer;
        FontConfigurer chatFontConfigurer_Exist = (FontConfigurer)modulePrefs.getOption("ChatFont");
        if (chatFontConfigurer_Exist == null) {
            chatFontConfigurer = new FontConfigurer(CHAT_FONT, Resources.getString("Chatter.chat_font_preference")); //$NON-NLS-1$ //$NON-NLS-2$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), chatFontConfigurer); //$NON-NLS-1$
        }
        else {
            chatFontConfigurer = chatFontConfigurer_Exist;
        }
        chatFontConfigurer.addPropertyChangeListener(evt -> {
            setFont((Font) evt.getNewValue());
            makeStyleSheet((Font) evt.getNewValue());
            makeASLStyleSheet((Font) evt.getNewValue());
            send(" ");
            send("- Chatter font changed");
            send(" ");
        });
        chatFontConfigurer.fireUpdate();
        // buttons font pref
        FontConfigurer buttonsFontConfigurer;
        FontConfigurer buttonsFontConfigurer_Exist = (FontConfigurer)modulePrefs.getOption("ButtonFont");
        if (buttonsFontConfigurer_Exist == null) {
            buttonsFontConfigurer = new FontConfigurer(BUTTON_FONT, "Chatter's dice buttons font: "); //$NON-NLS-1$ //$NON-NLS-2$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), buttonsFontConfigurer); //$NON-NLS-1$
        }
        else {
            buttonsFontConfigurer = buttonsFontConfigurer_Exist;
        }
        buttonsFontConfigurer.addPropertyChangeListener(evt -> SetButtonsFonts((Font) evt.getNewValue()));
        buttonsFontConfigurer.fireUpdate();
        //background colour pref
        ColorConfigurer backgroundColor;
        ColorConfigurer backgroundColor_Exist = (ColorConfigurer)modulePrefs.getOption(CHAT_BACKGROUND_COLOR);
        if (backgroundColor_Exist == null) {
            backgroundColor = new ColorConfigurer(CHAT_BACKGROUND_COLOR, "Background color: ", Color.white); //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), backgroundColor); //$NON-NLS-1$
        }
        else {
            backgroundColor = backgroundColor_Exist;
        }
        clrBackground = (Color) modulePrefs.getValue(CHAT_BACKGROUND_COLOR);
        backgroundColor.addPropertyChangeListener(e -> {
            clrBackground = (Color) e.getNewValue();
            conversationPane.setBackground(clrBackground);
        });
        backgroundColor.fireUpdate();
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
        ColorConfigurer systemMsgColor;
        ColorConfigurer systemMsgColor_Exist = (ColorConfigurer)modulePrefs.getOption(SYS_MSG_COLOR);
        if (systemMsgColor_Exist == null) {
            systemMsgColor = new ColorConfigurer(SYS_MSG_COLOR, Resources.getString("Chatter.systemessage_preference"), new Color(160, 160, 160)); //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), systemMsgColor); //$NON-NLS-1$
        }
        else {
            systemMsgColor = systemMsgColor_Exist;
        }
        systemMsg = (Color) modulePrefs.getValue(SYS_MSG_COLOR);
        makeStyleSheet(null);
        systemMsgColor.addPropertyChangeListener(e -> {
            systemMsg = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // myChat preference
        ColorConfigurer myChatColor;
        ColorConfigurer myChatColor_Exist = (ColorConfigurer)modulePrefs.getOption(MY_CHAT_COLOR);
        if (myChatColor_Exist == null) {
            myChatColor = new ColorConfigurer(MY_CHAT_COLOR, "My Name and Text Messages" , Color.gray); //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), myChatColor); //$NON-NLS-1$
        }
        else {
            myChatColor = myChatColor_Exist;
        }
        myChat = (Color) modulePrefs.getValue(MY_CHAT_COLOR);
        makeStyleSheet(null);
        myChatColor.addPropertyChangeListener(e -> {
            myChat = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // other chat preference
        ColorConfigurer otherChatColor;
        ColorConfigurer otherChatColor_Exist = (ColorConfigurer)modulePrefs.getOption(OTHER_CHAT_COLOR);
        if (otherChatColor_Exist == null) {
            otherChatColor = new ColorConfigurer(OTHER_CHAT_COLOR, Resources.getString("Chatter.other_text_preference"), Color.black); //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), otherChatColor); //$NON-NLS-1$
        }
        else {
            otherChatColor = otherChatColor_Exist;
        }
        otherChat = (Color) modulePrefs.getValue(OTHER_CHAT_COLOR);
        makeStyleSheet(null);
        otherChatColor.addPropertyChangeListener(e -> {
            otherChat = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // dice chat pref
        ColorConfigurer diceChatColor;
        ColorConfigurer diceChatColor_Exist = (ColorConfigurer)modulePrefs.getOption(DICE_CHAT_COLOR);
        if (diceChatColor_Exist == null) {
            diceChatColor = new ColorConfigurer(DICE_CHAT_COLOR, "Dice Results font color: ", Color.black); //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), diceChatColor); //$NON-NLS-1$
        }
        else {
            diceChatColor = diceChatColor_Exist;
        }
        gameMsg5 = (Color) modulePrefs.getValue(DICE_CHAT_COLOR);
        makeStyleSheet(null);
        diceChatColor.addPropertyChangeListener(e -> {
            gameMsg5 = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // dice images pref
        BooleanConfigurer useDiceImagesOption;
        BooleanConfigurer useDiceImagesOption_Exist = (BooleanConfigurer)modulePrefs.getOption(USE_DICE_IMAGES);
        if (useDiceImagesOption_Exist == null) {
            useDiceImagesOption = new BooleanConfigurer(USE_DICE_IMAGES, "Use images for dice rolls", Boolean.TRUE);  //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), useDiceImagesOption); //$NON-NLS-1$
        }
        else {
            useDiceImagesOption = useDiceImagesOption_Exist;
        }
        useDiceImages = (Boolean) (modulePrefs.getValue(USE_DICE_IMAGES));
        useDiceImagesOption.addPropertyChangeListener(e -> useDiceImages = (Boolean) e.getNewValue());
        // dice stats pref
        BooleanConfigurer showDiceStatsOption;
        BooleanConfigurer showDiceStatsOption_Exist = (BooleanConfigurer)modulePrefs.getOption(SHOW_DICE_STATS);
        if (showDiceStatsOption_Exist == null) {
            showDiceStatsOption = new BooleanConfigurer(SHOW_DICE_STATS, "Show dice stats after each dice rolls", Boolean.FALSE);  //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), showDiceStatsOption); //$NON-NLS-1$
        }
        else {
            showDiceStatsOption = showDiceStatsOption_Exist;
        }
        showDiceStats = (Boolean) (modulePrefs.getValue(SHOW_DICE_STATS));
        showDiceStatsOption.addPropertyChangeListener(e -> showDiceStats = (Boolean) e.getNewValue());

        // coloured die pref
        StringEnumConfigurer coloredDiceColor;
        StringEnumConfigurer coloredDiceColor_Exist = (StringEnumConfigurer) modulePrefs.getOption(COLORED_DICE_COLOR);
        if (coloredDiceColor_Exist == null) {
            coloredDiceColor = new StringEnumConfigurer(COLORED_DICE_COLOR, "Colored Die Color:", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), coloredDiceColor);
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
        clrColoredDiceColor = modulePrefs.getStoredValue("coloredDiceColor");
        if (clrColoredDiceColor != null) {
            diceFactory.setDieColor(DiceType.COLORED,DieColor.getEnum(clrColoredDiceColor));
        }

        // single die pref
        StringEnumConfigurer coloredDieColor;
        StringEnumConfigurer coloredDieColor_Exist = (StringEnumConfigurer)modulePrefs.getOption(SINGLE_DIE_COLOR);
        if (coloredDieColor_Exist == null) {
            coloredDieColor = new StringEnumConfigurer(SINGLE_DIE_COLOR, "Single die color:  ", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), coloredDieColor); //$NON-NLS-1$
        }
        else {
            coloredDieColor = coloredDieColor_Exist;
        }
        coloredDieColor.addPropertyChangeListener(e -> {
            clrSingleDieColor = (String) e.getNewValue();
            if (clrSingleDieColor != null) {
                diceFactory.setDieColor(DiceType.SINGLE, DieColor.getEnum(clrSingleDieColor));
            }
        });
        clrSingleDieColor = modulePrefs.getStoredValue("singleDieColor");
        if (clrSingleDieColor != null) {
            diceFactory.setDieColor(DiceType.SINGLE, DieColor.getEnum(clrSingleDieColor));
        }

        // third die pref
        StringEnumConfigurer thirdDieColor;
        thirdDieColor = new StringEnumConfigurer(THIRD_DIE_COLOR, "Third die color:  ", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
        modulePrefs.addOption(Resources.getString("Chatter.chat_window"), thirdDieColor); //$NON-NLS-1$
        thirdDieColor.addPropertyChangeListener(e -> {
            clrDustColoredDiceColor = (String) e.getNewValue();
            if (clrDustColoredDiceColor != null) {
                diceFactory.setDieColor(DiceType.OTHER_DUST, DieColor.getEnum(clrDustColoredDiceColor));
            }
        });
        clrDustColoredDiceColor = modulePrefs.getStoredValue("thirdDieColor");
        if (clrDustColoredDiceColor != null) {
            diceFactory.setDieColor(DiceType.SINGLE,DieColor.getEnum(clrDustColoredDiceColor));
        }
        thirdDieColor.fireUpdate();

        // rule set pref
        StringEnumConfigurer specialDiceRollNotificationLevel = (StringEnumConfigurer)modulePrefs.getOption(NOTIFICATION_LEVEL);
        final String[] DROptions = {
            "None",
            "Snipers only",
            "Starter Kit",
            "Full ASL"
        };
        if (specialDiceRollNotificationLevel == null) {
            specialDiceRollNotificationLevel = new StringEnumConfigurer(NOTIFICATION_LEVEL,
                    "Notify about special DRs: ", DROptions);
            specialDiceRollNotificationLevel.setValue("Full ASL");
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), specialDiceRollNotificationLevel);
        }
        for (int i = 0; i < DROptions.length; ++i) {
            if (DROptions[i].equals(specialDiceRollNotificationLevel.getValueString())) {
                DRNotificationLevel = i;
                break;
            }
        }
        // just for access from inside the event handler
        final StringEnumConfigurer cfg = specialDiceRollNotificationLevel;
        specialDiceRollNotificationLevel.addPropertyChangeListener(e -> {
            for (int i = 0; i < DROptions.length; ++i) {
                if (DROptions[i].equals(cfg.getValueString())) {
                    DRNotificationLevel = i;
                    return;
                }
            }
            DRNotificationLevel = 3;
        });
        // Player Window pref
        coloredDieColor.fireUpdate();
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
        for (ChatterListener listener : chatter_listeners) {
            listener.DiceRoll(msgpartCategory, msgpartUser, msgpartSAN, Integer.parseInt(msgpartCdice), Integer.parseInt(msgpartWdice));
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
