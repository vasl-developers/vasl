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

        btnStats = createStatsDiceButton(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.CTRL_DOWN_MASK));
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

        final JPanel panelContainer = new JPanel();
        panelContainer.setLayout(new BoxLayout(panelContainer, BoxLayout.LINE_AXIS));
        panelContainer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        buttonPannel = new JPanel();
        buttonPannel.setLayout(new GridBagLayout());
        buttonPannel.setBorder(BorderFactory.createEmptyBorder(1, 1, 2, 1));
        buttonPannel.setMaximumSize(new Dimension(1000, 1000));

        final GridBagConstraints gridBagConstraints = new GridBagConstraints();
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

        final GroupLayout groupLayout = new GroupLayout(this);
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

    private void setButtonsFonts(Font font) {
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

    private JButton createStatsDiceButton(KeyStroke keyStroke) {
        final JButton btn = new JButton("");
        btn.setMinimumSize(new Dimension(5, 30));
        btn.setMargin(new Insets(0, 0, 0, -1));

        try {
            btn.setIcon(new ImageIcon(Op.load("stat.png").getImage(null)));
        }
        catch (Exception ignored) {
        }

        final GameModule g = GameModule.getGameModule();

        final ActionListener al = e -> {
            g.getComponentsOf(ASLDiceBot.class)
                .stream()
                .findFirst()
                .ifPresent(dice -> dice.statsToday());
        };

        btn.addActionListener(al);
        final KeyStrokeListener listener = new KeyStrokeListener(al);
        listener.setKeyStroke(keyStroke);
        addHotKeyToTooltip(btn, listener, "Dice rolls stats");
        btn.setFocusable(false);
        g.addKeyStrokeListener(listener);

        return btn;
    }

    public JButton CreateChatterDiceButton(String strImage, String caption, String tooltip, KeyStroke keyStroke, final boolean bDice, final String strCat)
    {
        final JButton btn = new JButton(caption);
        btn.setMinimumSize(new Dimension(5, 30));
        btn.setMargin(new Insets(0, 0, 0, -1));

        try {
            if (!strImage.isEmpty()) {
                btn.setIcon(new ImageIcon(Op.load(strImage).getImage(null)));
            }
        }
        catch (Exception ignored) {
        }

        final GameModule g = GameModule.getGameModule();

        final ActionListener al = e -> {
            g.getComponentsOf(ASLDiceBot.class)
                .stream()
                .findFirst()
                .ifPresent(dice -> {
                    if (bDice) {
                        dice.DR(strCat);
                    }
                    else {
                        dice.dr(strCat);
                    }
                });
        };

        btn.addActionListener(al);
        final KeyStrokeListener listener = new KeyStrokeListener(al);
        listener.setKeyStroke(keyStroke);
        addHotKeyToTooltip(btn, listener, tooltip);
        btn.setFocusable(false);
        g.addKeyStrokeListener(listener);

        return btn;
    }

    private void addHotKeyToTooltip(JButton button, KeyStrokeListener listener, String tooltipText) {
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

    private String[] findUser(String val) {
        final String[] retValue = new String[] {val, "", ""};

        final int userStart = val.indexOf("<");
        final int userEnd = val.indexOf(">");

        if (userStart != -1 && userEnd != -1) {
            retValue[0] = val.substring(0, userStart + 1);
            retValue[1] = val.substring(userStart + 1, userEnd);
            retValue[2] = val.substring(userEnd + 1);
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
                    parseNewDiceRoll(s);
                    s = makeMessageString();
                }
                else if (s.startsWith("<")) {
                    parseUserMsg(s);
                    s = makeMessageString();
                }
                else if (s.startsWith("-")) {
                    parseSystemMsg(s);
                }
                else if (s.startsWith("*")) {
                    s = parseMoveMsg(s);
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

        final String keystring = Resources.getString("PlayerRoster.observer");
        final String replace = keystring.replace("<", "&lt;").replace(">", "&gt;");
        if (!replace.equals(keystring)) {
            s = s.replace(keystring, replace);
        }

        try {
            kit.insertHTML(doc, doc.getLength(), "\n<div class=" + style + ">" + s + "</div>", 0, 0, (HTML.Tag)null);
        }
        catch (IOException | BadLocationException ble) {
            ErrorDialog.bug(ble);
        }

        conversationPane.repaint();
    }

    public static String stripQuickColorTagLocal(String s, String prefix) {
        final String[] QUICK_COLOR_REGEX = new String[]{"\\|", "!", "~", "`"};
        final int quickIndex = getQuickColorLocal(s, prefix);
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
                final String s2 = s.substring(prefix.length()).trim();
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
            final String s2 = s.trim();
            return s2.isEmpty() ? -1 : getQuickColorLocal(s2.charAt(0));
        }
    }

    public static int getQuickColorLocal(char c) {
        return "|!~`".indexOf(c);
    }

    public static String getQuickColorHTMLStyleLocal(String s, String prefix) {
        final int quickIndex = getQuickColorLocal(s, prefix);
        final Object var10000 = quickIndex <= 0 ? "" : quickIndex + 1;
        return "msg" + var10000;
    }

    private void parseSystemMsg(String msg) {
        try {
            //StyleConstants.setForeground(mainStyle, clrSystemMsg);
            //document.insertString(document.getLength(), "\n" + msg, mainStyle);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void parseUserMsg(String msg) {
        final String[] parts = findUser(msg);

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

    private String parseMoveMsg(String msg) {
        // test for html tags that must be removed
        int userStart = 0;
        int userEnd = 0;
        do {
            userStart = msg.indexOf("<");
            userEnd = msg.indexOf(">");

            if (userStart != -1 && userEnd != -1) {
                final String deletestring = msg.substring(userStart, userEnd+1);
                msg = msg.replace(deletestring, "");
                userStart = 0;
                userEnd = 0;
            }
        } while (userStart != -1 && userEnd != -1);
        //test
        return msg;
    }

    private void parseNewDiceRoll(String msg) {
        // *** (Other DR) 4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)
        String category, dice, user, san = "";
        int firstDice, secondDice;
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

            int pos = restOfMsg.indexOf(" DR) ");

            if (pos != -1) {
                category = restOfMsg.substring(0, pos);
                restOfMsg = restOfMsg.substring(pos + " DR) ".length()); //4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                pos = restOfMsg.indexOf(" ***");

                if (pos != -1) {
                    dice = restOfMsg.substring(0, pos);
                    restOfMsg = restOfMsg.substring(pos + " ***".length());//   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                    if (dice.length() == 3 || dice.length() == 5) {
                        final String[] diceArr = dice.split(",");

                        if (diceArr.length == 2 || (diceArr.length == 3 && environment.dustInEffect())) {
                            firstDice = Integer.parseInt(diceArr[0]);
                            secondDice = Integer.parseInt(diceArr[1]);
                            if (environment.dustInEffect() && diceArr.length == 3) {
                                otherDice.put(DiceType.OTHER_DUST, Integer.parseInt(diceArr[2]));
                            }

                            if (firstDice > 0
                                    && firstDice < 7
                                    && secondDice > 0
                                    && secondDice < 7) {
                                final String[] parts = findUser(restOfMsg);

                                final ArrayList<String> specialMessages = new ArrayList<>();

                                if (!parts[1].isEmpty() && !parts[2].isEmpty()) {
                                    user = parts[1];
                                    restOfMsg = parts[2]; // >      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                                    restOfMsg = restOfMsg.replace(">", " ").trim(); //Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)


                                    // Add special event hints, if necessary
                                    // First, SAN, which should not trigger for Rally, TK and CC rolls
                                    // and should happen on "Only Sniper" setting and in Full ASL mode
                                    if (DRNotificationLevel == 3 || DRNotificationLevel == 1) {
                                        if (!category.equals("TK") && !category.equals("CC") && !category.equals("Rally")) {
                                            if (restOfMsg.startsWith("Axis SAN")) {
                                                san = "Axis SAN";
                                                specialMessages.add("Axis SAN");
                                                restOfMsg = restOfMsg.substring("Axis SAN".length());
                                            }
                                            else if (restOfMsg.startsWith("Allied SAN")) {
                                                san = "Allied SAN";
                                                specialMessages.add("Allied SAN");
                                                restOfMsg = restOfMsg.substring("Allied SAN".length());
                                            }
                                            else if (restOfMsg.startsWith("Axis/Allied SAN")) {
                                                san = "Axis/Allied SAN";
                                                specialMessages.add("Axis/Allied SAN");
                                                restOfMsg = restOfMsg.substring("Axis/Allied SAN".length());
                                            }
                                        }

                                        if (category.equals("TC")) {
                                            if (restOfMsg.startsWith("Axis Booby Trap")) {
                                                san = "Axis Booby Trap";
                                                specialMessages.add("Axis Booby Trap");
                                                restOfMsg = restOfMsg.substring("Axis Booby Trap".length());
                                            }
                                            else if (restOfMsg.startsWith("Allied Booby Trap")) {
                                                san = "Allied Booby Trap";
                                                specialMessages.add("Allied Booby Trap");
                                                restOfMsg = restOfMsg.substring("Allied Booby Trap".length());
                                            }
                                            else if (restOfMsg.startsWith("Axis/Allied Booby Trap")) {
                                                san = "Axis/Allied Booby Trap";
                                                specialMessages.add("Axis/Allied Booby Trap");
                                                restOfMsg = restOfMsg.substring("Axis/Allied Booby Trap".length());
                                            }
                                        }
                                    }
                                    msgpartSAN = san;
                                    // ALL of these happen only in Starter Kit mode or Full ASL mode
                                    if (DRNotificationLevel >= 2) {
                                        // For TH rolls only, show possible hit location, Unlikely hit and multiple hit
                                        switch (category) {
                                        case "TH":
                                            if (firstDice == secondDice) {
                                                // Starter Kit + Full ASL
                                                if (firstDice == 1) {
                                                    specialMessages.add("Unlikely Hit (C3.6)");
                                                }
                                                // Full ASL only
                                                if (DRNotificationLevel == 3) {
                                                    specialMessages.add("Multiple Hits 15..40mm (C3.8)");
                                                }
                                            }
                                            if (firstDice < secondDice) {
                                                specialMessages.add("Turret");
                                            }
                                            else {
                                                specialMessages.add("Hull");
                                            }
                                            handleSpecialMessagesForOtherDice(category, specialMessages, firstDice, secondDice, otherDice);

                                            break;
                                        case "TK":
                                            if (firstDice == secondDice) {
                                                if (firstDice == 6) {
                                                    specialMessages.add("Dud (C7.35)");
                                                }
                                            }
                                            break;
                                        case "MC":
                                            // Full ASL only
                                            if (firstDice == 1 && secondDice == 1 && DRNotificationLevel == 3) {
                                                specialMessages.add("Heat of Battle (A15.1)");
                                            }
                                            // Starter Kit & Full ASL
                                            else if (firstDice == 6 && secondDice == 6) {
                                                specialMessages.add("Casualty MC (A10.31)");
                                            }
                                            handleSpecialMessagesForOtherDice(category, specialMessages, firstDice, secondDice, otherDice);
                                            break;
                                        case "TC":

                                            break;
                                        case "Rally":
                                            // Full ASL only
                                            if (firstDice == 1 && secondDice == 1 && DRNotificationLevel == 3) {
                                                specialMessages.add("Heat of Battle (A15.1) or Field Promotion (A18.11)");
                                            }
                                            // Starter Kit + Full ASL
                                            else if (firstDice == 6 && secondDice == 6) {
                                                specialMessages.add("Fate -> Casualty Reduction (A10.64)");
                                            }

                                            break;
                                        case "IFT":
                                            // check for cowering
                                            if (firstDice == secondDice) {
                                                // Full ASL only
                                                if (firstDice == 1 && DRNotificationLevel == 3) {
                                                    specialMessages.add("Unlikely Kill vs * (A7.309)");
                                                }
                                                // Starter Kit + Full ASL
                                                specialMessages.add("Cower if MMC w/o LDR");
                                            }
                                            handleSpecialMessagesForOtherDice(category, specialMessages, firstDice, secondDice, otherDice);
                                            break;
                                        case "CC":
                                            // Full ASL only
                                            if (firstDice == 1 && secondDice == 1 && DRNotificationLevel == 3) {
                                                specialMessages.add("Infiltration (A11.22), Field Promotion (A18.12), Unlikely Kill (A11.501)");
                                            } else if (firstDice == 6 && secondDice == 6 && DRNotificationLevel == 3) {
                                                specialMessages.add("Infiltration (A11.22)");
                                            }
                                            break;
                                        }
                                    }

                                    // check if SASL Dice button clicked and if so ask for special message string - SASL Dice buttons are created via extension
                                    if (DRNotificationLevel == 3 && category.equals("EP")){
                                        if (firstDice == secondDice) {
                                            switch (firstDice) {
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
                                    final StringBuilder strSpecialMessages = new StringBuilder();
                                    for (int i = 0; i < specialMessages.size(); ++i) {
                                        strSpecialMessages.append(specialMessages.get(i));
                                        if (i < specialMessages.size() - 1) {
                                            strSpecialMessages.append(", ");
                                        }
                                    }
                                    msgpartCategory = BEFORE_CATEGORY + category;

                                    if (useDiceImages) {
                                        msgpartCdice = Integer.toString(firstDice);
                                        msgpartWdice = Integer.toString(secondDice);

                                        paintIcon(firstDice, DiceType.COLORED);
                                        paintIcon(secondDice, DiceType.WHITE);
                                        //Add any other dice required
                                        for (Map.Entry<DiceType, Integer> entry : otherDice.entrySet()) {
                                            paintIcon(entry.getValue(), entry.getKey());
                                        }
                                    }
                                    else {
                                        msgpartCdice = Integer.toString(firstDice);
                                        msgpartWdice = Integer.toString(secondDice);
                                    }
                                    msgpartUser = user;
                                    msgpartSpecial = strSpecialMessages.toString();

                                    if (showDiceStats) {
                                        msgpartRest = restOfMsg;
                                    }
                                    fireDiceRoll();
                                }
                            }
                        }
                    }
                }
            }
            else { // *** (Other dr) 3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)
                //reset SAN message to the latest state for dice over map
                msgpartSAN = san;
                pos = restOfMsg.indexOf(" dr) ");

                if (pos != -1) {
                    category = restOfMsg.substring(0, pos);
                    restOfMsg = restOfMsg.substring(pos + " dr) ".length()); //3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)

                    pos = restOfMsg.indexOf(" ***");

                    if (pos != -1) {
                        dice = restOfMsg.substring(0, pos);
                        restOfMsg = restOfMsg.substring(pos + " ***".length());//   <FredKors>      [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                        if (dice.length() == 1) {
                            final int diceVal = Integer.parseInt(dice);

                            if (diceVal > 0 && diceVal < 7) {
                                String[] parts = findUser(restOfMsg);

                                if (!parts[1].isEmpty() && !parts[2].isEmpty()) {
                                    user = parts[1];

                                    msgpartCategory = BEFORE_CATEGORY + category;

                                    if (useDiceImages) {
                                        msgpartCdice = dice;
                                        msgpartWdice = "-1";
                                        paintIcon(diceVal, DiceType.SINGLE);
                                    }
                                    else {
                                        msgpartCdice = dice;
                                    }
                                    msgpartWdice = "-1";

                                    msgpartUser = user;
                                    // added by DR 2018 to add chatter text on Sniper Activation dr
                                    if (category.equals("SA")) {
                                        String sniperstring = "";
                                        if (diceVal == 1) {
                                            sniperstring = "Eliminates SMC, Dummy stack, Sniper; Stuns & Recalls CE crew; breaks MMC & Inherent crew of certain vehicles; immobilizes unarmored vehicle (A14.3)" ;
                                        }
                                        else if (diceVal == 2) {
                                            sniperstring = "Eliminates Dummy stack; Wounds SMC; Stuns CE crew; pins MMC, Inherent crew of certain vehicles, Sniper (A14.3)" ;
                                        }
                                        msgpartSpecial = sniperstring;
                                    }
                                    if (showDiceStats) {
                                        msgpartRest = restOfMsg;
                                    }

                                    fireDiceRoll();
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

    private void handleSpecialMessagesForOtherDice(final String category, final ArrayList<String> specialMessages,
            final int firstDice, final int secondDice, final Map<DiceType, Integer> otherDice)
    {
        int total = firstDice + secondDice;
        final int unmodifiedTotal = total;
        // Dust
        if (environment.dustInEffect() && DRNotificationLevel == 3 && !otherDice.isEmpty()) {
            switch (category) {
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
                specialMessages.add(environment.getCurrentDustLevel().toString() + " ");
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
            switch (category) {
            case "TH":
            case "IFT":
                total += 1;
                specialMessages.add("+1 Night LV ");
                break;
            }
        }
        // LV
        if (environment.isLV()) {
            final LVLevel lvLevel = environment.getCurrentLVLevel();
            switch (category) {
            case "TH":
            case "IFT":
                switch (lvLevel) {
                case DAWN_DUSK:
                    total += 1;
                    break;
                }
                specialMessages.add(lvLevel + " ");
                break;
            }
        }
        // Fog
        if (environment.isFog()) {
            switch (category) {
            case "TH":
            case "IFT":
                {
                    final FogLevel fogLevel = environment.getCurrentFogLevel();
                    specialMessages.add(fogLevel.toString() + " ");
                    break;
                }
            }
        }
        //Heat Haze
        if (environment.isHeatHaze()) {
            switch (category) {
            case "TH":
            case "IFT":
                {
                    final HeatHazeLevel heatHazeLevel = environment.getCurrentHeatHazeLevel();
                    specialMessages.add(heatHazeLevel.toString() + " ");
                    break;
                }
            }
        }
        //Sun Blindness
        if (environment.isSunBlindness()) {
            switch (category) {
            case "TH":
            case "IFT":
                {
                    final SunBlindnessLevel sunBlindnessLevel = environment.getCurrentSunBlindnessLevel();
                    specialMessages.add(sunBlindnessLevel.toString() + " ");
                    break;
                }
            }
        }

        if (unmodifiedTotal < total || environment.dustInEffect()) {
            specialMessages.add("Total: " + total);
        }
    }

    private void paintIcon(int dice, DiceType diceType) {
        ASLDie die;
        try {
            die = diceFactory.getASLDie(diceType);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        final String dicefile = die.getDieHTMLFragment(dice);
        if (msgpartDiceImage == null) {
            msgpartDiceImage = "<img alt=\"alt text\" src=\"" + dicefile + "\">";
        }
        else {
            msgpartDiceImage += "&nbsp <img alt=\"alt text\" src=\"" + dicefile + "\"> &nbsp";
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
        buttonsFontConfigurer.addPropertyChangeListener(evt -> setButtonsFonts((Font) evt.getNewValue()));
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

    private void fireDiceRoll() {
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
        void DiceRoll(String category, String user, String san, int firstDice, int secondDice);
    }
}
