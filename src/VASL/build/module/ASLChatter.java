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

  public enum DiceType
  {
      WHITE,
      COLORED,
      OTHER_DUST,
      BOTH,
      SINGLE
  }

  private Color m_clrBackground;
  private String m_clrColoredDiceColor;
  private String m_clrDustColoredDiceColor;
  private String m_clrSingleDieColor;
  private final JButton m_btnStats;
  private final JButton m_btnDR;
  private final JButton m_btnIFT;
  private final JButton m_btnTH;
  private final JButton m_btnTK;
  private final JButton m_btnMC;
  private final JButton m_btnRally;
  private final JButton m_btnCC;
  private final JButton m_btnTC;
  private final JButton m_btndr;
  private final JButton m_btnSA;
  private final JButton m_btnRS;
  private final JPanel l_objButtonPanel;
  private boolean m_bUseDiceImages;
  private boolean m_bShowDiceStats;

  private final Environment environment = new Environment();
  private final ASLDiceFactory diceFactory = new ASLDiceFactory();

  private final JTextField m_edtInputText;
    private int m_DRNotificationLevel;

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

        m_clrBackground = Color.white;
        conversationPane.addKeyListener(new KeyListener()
        {
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
        }
        );

        m_btnStats = CreateStatsDiceButton(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.CTRL_DOWN_MASK));
        m_btnDR = CreateChatterDiceButton("DRs.gif", "DR", "DR", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), true, ASLDiceBot.OTHER_CATEGORY);
        m_btnIFT = CreateChatterDiceButton("", "IFT", "IFT attack DR", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "IFT");
        m_btnTH = CreateChatterDiceButton("", "TH", "To Hit DR", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "TH");
        m_btnTK = CreateChatterDiceButton("", "TK", "To Kill DR", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "TK");
        m_btnMC = CreateChatterDiceButton("", "MC", "Morale Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "MC");
        m_btnRally = CreateChatterDiceButton("", "Rally", "Rally DR", KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "Rally");
        m_btnCC = CreateChatterDiceButton("", "CC", "Close Combat DR", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "CC");
        m_btnTC = CreateChatterDiceButton("", "TC", "Task Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "TC");
        m_btndr = CreateChatterDiceButton("dr.gif", "dr", "dr", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), false, ASLDiceBot.OTHER_CATEGORY);
        m_btnSA = CreateChatterDiceButton("", "SA", "Sniper Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "SA");
        m_btnRS = CreateChatterDiceButton("", "RS", "Random Selection dr", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "RS");

        JPanel l_objPanelContainer = new JPanel();
        l_objPanelContainer.setLayout(new BoxLayout(l_objPanelContainer, BoxLayout.LINE_AXIS));
        l_objPanelContainer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        l_objButtonPanel = new JPanel();
        l_objButtonPanel.setLayout(new GridBagLayout());
        l_objButtonPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 2, 1));
        l_objButtonPanel.setMaximumSize(new Dimension(1000, 1000));

        GridBagConstraints l_objGridBagConstraints = new GridBagConstraints();
        l_objGridBagConstraints.fill = GridBagConstraints.BOTH;
        l_objGridBagConstraints.weightx = 0.5;
        l_objGridBagConstraints.weighty = 0.5;
        l_objGridBagConstraints.insets = new Insets(0, 1, 0, 1);

        l_objButtonPanel.add(m_btnStats, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnDR, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnIFT, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnTH, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnTK, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnMC, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnTC, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnRally, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnCC, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btndr, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnSA, l_objGridBagConstraints);
        l_objButtonPanel.add(m_btnRS, l_objGridBagConstraints);

        m_edtInputText = new JTextField(60);
        m_edtInputText.setFocusTraversalKeysEnabled(false);
        m_edtInputText.addActionListener(e -> {
            send(formatChat(e.getActionCommand()));
            m_edtInputText.setText(""); //$NON-NLS-1$
        });

        m_edtInputText.setMaximumSize(new Dimension(m_edtInputText.getMaximumSize().width,
        m_edtInputText.getPreferredSize().height));

        scroll.setViewportView(conversationPane);

        l_objPanelContainer.add(l_objButtonPanel);

        GroupLayout m_objGroupLayout = new GroupLayout(this);
        setLayout(m_objGroupLayout);
        m_objGroupLayout.setHorizontalGroup(
            m_objGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(scroll)
            .addComponent(l_objPanelContainer, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(m_edtInputText)
        );
        m_objGroupLayout.setVerticalGroup(
            m_objGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(m_objGroupLayout.createSequentialGroup()
                    .addComponent(scroll, GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                    .addGap(0, 0, 0)
                    .addComponent(l_objPanelContainer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, 0)
                    .addComponent(m_edtInputText, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );

    }
    // Is this still used?
    @Deprecated
    public JPanel getButtonPanel() {  //used by SASLDice extension
        return l_objButtonPanel;
    }
    private void SetButtonsFonts(Font objFont)
    {
        m_btnStats.setFont(objFont);
        m_btnDR.setFont(objFont);
        m_btnIFT.setFont(objFont);
        m_btnTH.setFont(objFont);
        m_btnTK.setFont(objFont);
        m_btnMC.setFont(objFont);
        m_btnRally.setFont(objFont);
        m_btnCC.setFont(objFont);
        m_btnTC.setFont(objFont);
        m_btndr.setFont(objFont);
        m_btnSA.setFont(objFont);
        m_btnRS.setFont(objFont);
    }

    //Is this still used?
    @Deprecated
    private JButton CreateInfoButton(String strCaption, String strTooltip, final String strMsg, KeyStroke objKeyStroke) {  // used by SASLDice extension
       JButton l_btn = new JButton(strCaption);

       l_btn.setPreferredSize(new Dimension(90, 25));
       l_btn.setMargin(new Insets(l_btn.getMargin().top, 0, l_btn.getMargin().bottom, 0));

        ActionListener l_objAL = e -> send(formatChat(strMsg));
        l_btn.addActionListener(l_objAL);
        KeyStrokeListener l_objListener = new KeyStrokeListener(l_objAL);
        l_objListener.setKeyStroke(objKeyStroke);
        AddHotKeyToTooltip(l_btn, l_objListener, strTooltip);
        l_btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(l_objListener);
        return l_btn;
    }

    private JButton CreateStatsDiceButton(KeyStroke keyStroke) {
        JButton l_btn = new JButton("");
        l_btn.setMinimumSize(new Dimension(5, 30));
        l_btn.setMargin(new Insets(0, 0, 0, -1));

        try {
          l_btn.setIcon(new ImageIcon(Op.load("stat.png").getImage(null)));
        } catch (Exception ignored) {

        }
        ActionListener l_objAL = e -> {
            try {
                ASLDiceBot l_objDice = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();
                if (l_objDice != null) {
                    l_objDice.statsToday();
                }
            } catch (Exception ignored) {

            }
        };

        l_btn.addActionListener(l_objAL);
        KeyStrokeListener l_Listener = new KeyStrokeListener(l_objAL);
        l_Listener.setKeyStroke(keyStroke);
        AddHotKeyToTooltip(l_btn, l_Listener, "Dice rolls stats");
        l_btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(l_Listener);

        return l_btn;
    }

    public JButton CreateChatterDiceButton(String strImage, String strCaption, String strTooltip, KeyStroke keyStroke, final boolean bDice, final String strCat)
    {
        JButton l_btn = new JButton(strCaption);
        l_btn.setMinimumSize(new Dimension(5, 30));
        l_btn.setMargin(new Insets(0, 0, 0, -1));
        try {
            if (!strImage.isEmpty()) {
                l_btn.setIcon(new ImageIcon(Op.load(strImage).getImage(null)));
            }
        } catch (Exception ignored) {

        }
        ActionListener l_objAL = e -> {
            try {
                ASLDiceBot l_objDice = GameModule.getGameModule().getComponentsOf(ASLDiceBot.class).iterator().next();
                if (l_objDice != null) {
                    if (bDice) {
                        l_objDice.DR(strCat);
                    } else {
                        l_objDice.dr(strCat);
                    }
                }
            } catch (Exception ignored) {

            }
        };

        l_btn.addActionListener(l_objAL);
        KeyStrokeListener l_Listener = new KeyStrokeListener(l_objAL);
        l_Listener.setKeyStroke(keyStroke);
        AddHotKeyToTooltip(l_btn, l_Listener, strTooltip);
        l_btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(l_Listener);

        return l_btn;
    }

    private void AddHotKeyToTooltip(JButton objButton, KeyStrokeListener objListener, String strTooltipText) {
        if (objListener.getKeyStroke() != null)
            objButton.setToolTipText(strTooltipText + " [" + HotKeyConfigurer.getString(objListener.getKeyStroke()) + "]");
    }
    protected void makeASLStyleSheet(Font f) {
        if (this.style != null) {
            if (f == null) {
                if (this.myFont == null) {
                    f = new Font("SansSerif", Font.PLAIN, 12);
                    this.myFont = f;
                } else {
                    f = this.myFont;
                }
            }

            this.addStyle(".msgcategory", f, Color.black, "bold", 0);
            this.addStyle(".msguser", f, myChat, "bold", 0);
            this.addStyle(".msgspecial", f, gameMsg, "bold", 0);

            style.addRule(
                    " .tbl { border:0px solid #C0C0C0; border-collapse:collapse; border-spacing:0px; padding:0px; background:#CCFFCC;}" +
                            " .tbl th { border:1px solid #C0C0C0; padding:5px; background:#FFFF66;}" +
                            " .tbl td {border:1px solid #C0C0C0; padding:5px; text-align: right;}" +
                            " .tbl tr.total {border:2px solid #black; background:#CCFFFF;}" +
                            " .tbl td.up {border-top:2px solid black; padding:5px; font-weight: bold; text-align: right;}");

        }
    }

    @Override
    protected String formatChat(String text) {

        final String id = GlobalOptions.getInstance().getPlayerId();
        return "<" + (id.length() == 0 ? "(" + getAnonymousUserName() + ")" : id) + "> - " + text; //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public JTextField getInputField() {
        return m_edtInputText;
    }

    String [] FindUser (String strVal) {
        String [] lar_strRetValue = new String[] {strVal,"",""};

        int l_iUserStart = strVal.indexOf("<");
        int l_iUserEnd = strVal.indexOf(">");

        if ((l_iUserStart != -1) && (l_iUserEnd != -1)) {
            lar_strRetValue[0] = strVal.substring(0, l_iUserStart + 1);
            lar_strRetValue[1] = strVal.substring(l_iUserStart + 1, l_iUserEnd);
            lar_strRetValue[2] = strVal.substring(l_iUserEnd+1);
        }

        return lar_strRetValue;
    }

    @Override
    public void show(String strMsg)
    {
        try
        {
            if (strMsg.length() > 0)
            {

                if (strMsg.startsWith("!!"))  // dice stats button has been clicked
                {
                    strMsg = makeTableString(strMsg);

                }
                else if (strMsg.startsWith("*** 3d6 = "))
                {
                    //Parse3d6(strMsg);
                }
                else if (strMsg.startsWith("*** ("))
                {
                    ParseNewDiceRoll(strMsg);
                    strMsg = makeMessageString();

                }
                else if (strMsg.startsWith("<"))
                {
                    ParseUserMsg(strMsg);
                    strMsg = makeMessageString();
                }
                else if (strMsg.startsWith("-"))
                {
                    ParseSystemMsg(strMsg);
                }
                else if (strMsg.startsWith("*"))
                {
                    strMsg = ParseMoveMsg(strMsg);
                }
                else {
                    //ParseDefaultMsg(strMsg);
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        if (SwingUtilities.isEventDispatchThread()) {
            this.doShow(strMsg);
        } else {
            String finalStrMsg = strMsg;
            SwingUtilities.invokeLater(() -> {
                this.doShow(finalStrMsg);
            });
        }
    }

    //temporary - to fix VASSAL issues parsing messages pertaining to concealed counters
    private void doShow(String s) {
        s = s.trim();
        String style;
        boolean html_allowed;
        if (!s.isEmpty()) {
            if (s.startsWith("*")) {
                html_allowed = QuickColors.getQuickColor(s, "*") >= 0 || GlobalOptions.getInstance().chatterHTMLSupport();
                style = getQuickColorHTMLStyleLocal(s, "*");
                s = stripQuickColorTagLocal(s, "*");
            } else if (s.startsWith("-")) {
                html_allowed = true;
                //dirty quick fix for system messages not displaying with correct fonts, colors
                style = "sys";
                s = QuickColors.stripQuickColorTag(s, "-");
            } else {
                style = this.getChatStyle(s);
                html_allowed = false;
            }
        } else {
            style = "msg";
            html_allowed = false;
        }

        if (!html_allowed) {
            s = s.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        }

        String keystring = Resources.getString("PlayerRoster.observer");
        String replace = keystring.replace("<", "&lt;").replace(">", "&gt;");
        if (!replace.equals(keystring)) {
            s = s.replace(keystring, replace);
        }

        try {
            this.kit.insertHTML(this.doc, this.doc.getLength(), "\n<div class=" + style + ">" + s + "</div>", 0, 0, (HTML.Tag)null);
        } catch (IOException | BadLocationException var7) {
            ErrorDialog.bug(var7);
        }

        this.conversationPane.repaint();
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
            } else if (!s.startsWith(prefix)) {
                return -1;
            } else {
                String s2 = s.substring(prefix.length()).trim();
                return s2.isEmpty() ? -1 : getQuickColorLocal(s2.charAt(0));
            }
        } else {
            return -1;
        }
    }
    public static int getQuickColorLocal(String s) {
        if (StringUtils.isEmpty(s)) {
            return -1;
        } else {
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
        try
        {
            //StyleConstants.setForeground(m_objMainStyle, m_clrSystemMsg);
            //m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void ParseUserMsg(String strMsg) {
        try
        {
            String[] lar_strParts = FindUser(strMsg);

            if ((!lar_strParts[1].isEmpty()) && (!lar_strParts[2].isEmpty()))  {
                msgpartCategory = ""; msgpartCdice=""; msgpartWdice=""; msgpartSAN="";
                msgpartDiceImage="";msgpartSpecial="";
                msgpartUser = lar_strParts[1];
                msgpartRest = lar_strParts[2];
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String ParseMoveMsg(String strMsg) {
        // test for html tags that must be removed
        int l_iUserStart=0 ;
        int l_iUserEnd=0 ;
        do {
            try {
                l_iUserStart = strMsg.indexOf("<");
                l_iUserEnd = strMsg.indexOf(">");

                if ((l_iUserStart != -1) && (l_iUserEnd != -1)) {
                    String deletestring = strMsg.substring(l_iUserStart, l_iUserEnd+1);
                    strMsg = strMsg.replace(deletestring, "");
                    l_iUserStart=0 ; l_iUserEnd=0 ;
                } else if ((l_iUserStart == -1) && (l_iUserEnd != -1)) {
                    break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } while ((l_iUserStart != -1) && (l_iUserEnd != -1));
        //test
        return strMsg;
    }

    private void ParseNewDiceRoll(String strMsg)
    {
        // *** (Other DR) 4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)
        String l_strCategory, l_strDice, l_strUser, l_strSAN = "";
        int l_iFirstDice, l_iSecondDice;
        msgpartCategory=null; msgpartUser=null; msgpartCdice=null; msgpartWdice=null; msgpartSpecial=null; msgpartRest=null; msgpartDiceImage=null;
        Map<DiceType, Integer> otherDice = new HashMap<>();
        try
        {
            String l_strRestOfMsg = strMsg.substring("*** (".length()); // Other DR) 4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        

            int l_iPos = l_strRestOfMsg.indexOf(" DR) ");

            if (l_iPos != -1)
            {
                l_strCategory = l_strRestOfMsg.substring(0, l_iPos);
                l_strRestOfMsg = l_strRestOfMsg.substring(l_iPos + " DR) ".length()); //4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        

                l_iPos = l_strRestOfMsg.indexOf(" ***");

                if (l_iPos != -1)
                {
                    l_strDice = l_strRestOfMsg.substring(0, l_iPos);
                    l_strRestOfMsg = l_strRestOfMsg.substring(l_iPos + " ***".length());//   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)

                    if (l_strDice.length() == 3 || l_strDice.length() == 5)
                    {
                        String [] lar_strDice = l_strDice.split(",");

                        if (lar_strDice.length == 2 || (lar_strDice.length == 3 && environment.dustInEffect()))
                        {
                            l_iFirstDice = Integer.parseInt(lar_strDice[0]);
                            l_iSecondDice = Integer.parseInt(lar_strDice[1]);
                            if(environment.dustInEffect() && lar_strDice.length == 3)
                            {
                                otherDice.put(DiceType.OTHER_DUST, Integer.parseInt(lar_strDice[2]));
                            }

                            if ((l_iFirstDice > 0)
                                && (l_iFirstDice < 7)
                                && (l_iSecondDice > 0)
                                && (l_iSecondDice < 7))
                            {
                                String[] lar_strParts = FindUser(l_strRestOfMsg);

                                ArrayList<String> specialMessages = new ArrayList<>();

                                if ((!lar_strParts[1].isEmpty()) && (!lar_strParts[2].isEmpty()))
                                {
                                    l_strUser = lar_strParts[1];
                                    l_strRestOfMsg = lar_strParts[2]; // >      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        

                                    l_strRestOfMsg = l_strRestOfMsg.replace(">", " ").trim(); //Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        


                                    // Add special event hints, if necessary
                                    // First, SAN, which should not trigger for Rally, TK and CC rolls
                                    // and should happen on "Only Sniper" setting and in Full ASL mode
                                    if(m_DRNotificationLevel == 3 || m_DRNotificationLevel == 1)
                                    {
                                        if ((!l_strCategory.equals("TK")) && (!l_strCategory.equals("CC"))
                                                && (!l_strCategory.equals(("Rally"))))
                                        {
                                            if (l_strRestOfMsg.startsWith("Axis SAN"))
                                            {
                                                l_strSAN = "Axis SAN";
                                                specialMessages.add("Axis SAN");
                                                l_strRestOfMsg = l_strRestOfMsg.substring("Axis SAN".length());
                                            } else if (l_strRestOfMsg.startsWith("Allied SAN"))
                                            {
                                                l_strSAN = "Allied SAN";
                                                specialMessages.add("Allied SAN");
                                                l_strRestOfMsg = l_strRestOfMsg.substring("Allied SAN".length());
                                            } else if (l_strRestOfMsg.startsWith("Axis/Allied SAN"))
                                            {
                                                l_strSAN = "Axis/Allied SAN";
                                                specialMessages.add("Axis/Allied SAN");
                                                l_strRestOfMsg = l_strRestOfMsg.substring("Axis/Allied SAN".length());
                                            }
                                        }

                                        if (l_strCategory.equals("TC"))
                                        {
                                            if (l_strRestOfMsg.startsWith("Axis Booby Trap"))
                                            {
                                                l_strSAN = "Axis Booby Trap";
                                                specialMessages.add("Axis Booby Trap");
                                                l_strRestOfMsg = l_strRestOfMsg.substring("Axis Booby Trap".length());
                                            } else if (l_strRestOfMsg.startsWith("Allied Booby Trap"))
                                            {
                                                l_strSAN = "Allied Booby Trap";
                                                specialMessages.add("Allied Booby Trap");
                                                l_strRestOfMsg = l_strRestOfMsg.substring("Allied Booby Trap".length());
                                            } else if (l_strRestOfMsg.startsWith("Axis/Allied Booby Trap"))
                                            {
                                                l_strSAN = "Axis/Allied Booby Trap";
                                                specialMessages.add("Axis/Allied Booby Trap");
                                                l_strRestOfMsg = l_strRestOfMsg.substring("Axis/Allied Booby Trap".length());
                                            }
                                        }
                                    }
                                    msgpartSAN=l_strSAN;
                                    // ALL of these happen only in Starter Kit mode or Full ASL mode
                                    if(m_DRNotificationLevel >= 2)
                                    {
                                        // For TH rolls only, show possible hit location, Unlikely hit and multiple hit
                                      switch (l_strCategory) {
                                        case "TH":
                                          if (l_iFirstDice == l_iSecondDice) {
                                            // Starter Kit + Full ASL
                                            if (l_iFirstDice == 1) {
                                              specialMessages.add("Unlikely Hit (C3.6)");
                                            }
                                            // Full ASL only
                                            if (m_DRNotificationLevel == 3) {
                                              specialMessages.add("Multiple Hits 15..40mm (C3.8)");
                                            }
                                          }
                                          if (l_iFirstDice < l_iSecondDice) {
                                            specialMessages.add("Turret");
                                          } else {
                                            specialMessages.add("Hull");
                                          }
                                          HandleSpecialMessagesForOtherDice(l_strCategory, specialMessages, l_iFirstDice, l_iSecondDice, otherDice);

                                          break;
                                        case "TK":
                                          if (l_iFirstDice == l_iSecondDice) {
                                            if (l_iFirstDice == 6) {
                                              specialMessages.add("Dud (C7.35)");
                                            }
                                          }
                                          break;
                                        case "MC":
                                          // Full ASL only
                                          if (l_iFirstDice == 1 && l_iSecondDice == 1 && m_DRNotificationLevel == 3) {
                                            specialMessages.add("Heat of Battle (A15.1)");
                                          }
                                          // Starter Kit & Full ASL
                                          else if (l_iFirstDice == 6 && l_iSecondDice == 6) {
                                            specialMessages.add("Casualty MC (A10.31)");
                                          }
                                          HandleSpecialMessagesForOtherDice(l_strCategory, specialMessages, l_iFirstDice, l_iSecondDice, otherDice);
                                          break;
                                        case "TC":

                                          break;
                                        case "Rally":
                                          // Full ASL only
                                          if (l_iFirstDice == 1 && l_iSecondDice == 1 && m_DRNotificationLevel == 3) {
                                            specialMessages.add("Heat of Battle (A15.1) or Field Promotion (A18.11)");
                                          }
                                          // Starter Kit + Full ASL
                                          else if (l_iFirstDice == 6 && l_iSecondDice == 6) {
                                            specialMessages.add("Fate -> Casualty Reduction (A10.64)");
                                          }

                                          break;
                                        case "IFT":
                                          // check for cowering
                                          if (l_iFirstDice == l_iSecondDice) {
                                            // Full ASL only
                                            if (l_iFirstDice == 1 && m_DRNotificationLevel == 3) {
                                              specialMessages.add("Unlikely Kill vs * (A7.309)");
                                            }
                                            // Starter Kit + Full ASL
                                            specialMessages.add("Cower if MMC w/o LDR");
                                          }
                                          HandleSpecialMessagesForOtherDice(l_strCategory, specialMessages, l_iFirstDice, l_iSecondDice, otherDice);
                                          break;
                                        case "CC":
                                          // Full ASL only
                                          if (l_iFirstDice == 1 && l_iSecondDice == 1 && m_DRNotificationLevel == 3) {
                                            specialMessages.add("Infiltration (A11.22), Field Promotion (A18.12), Unlikely Kill (A11.501)");
                                          } else if (l_iFirstDice == 6 && l_iSecondDice == 6 && m_DRNotificationLevel == 3) {
                                            specialMessages.add("Infiltration (A11.22)");
                                          }
                                          break;
                                      }
                                    }

                                    // check if SASL Dice button clicked and if so ask for special message string - SASL Dice buttons are created via extension
                                    if(m_DRNotificationLevel == 3 && l_strCategory.equals("EP")){
                                        if (l_iFirstDice == l_iSecondDice) {
                                            switch (l_iFirstDice) {
                                                case 1:
                                                    specialMessages.add("Green and Conscript Units Panic");
                                                    break;
                                                case 2:
                                                    specialMessages.add("2nd Line, Partisan, Green, and Conscript Units Panic");
                                                    break;
                                                case 3: case 4:
                                                    specialMessages.add("1st Line, 2nd Line, Partisan, Green, and Conscript Units Panic");
                                                    break;
                                                case 5: case 6:
                                                    specialMessages.add("Any Unit Panics");
                                                    break;
                                            }
                                        }
                                    }
                                    // Construct Special Message string
                                    StringBuilder l_strSpecialMessages = new StringBuilder();
                                    for (int i = 0; i < specialMessages.size(); ++i)
                                    {
                                        l_strSpecialMessages.append(specialMessages.get(i));
                                        if (i < specialMessages.size() - 1)
                                        {
                                            l_strSpecialMessages.append(", ");
                                        }
                                    }
                                    msgpartCategory = BEFORE_CATEGORY + l_strCategory;

                                    if (m_bUseDiceImages)
                                    {
                                        msgpartCdice = Integer.toString(l_iFirstDice);
                                        msgpartWdice= Integer.toString(l_iSecondDice);

                                        PaintIcon(l_iFirstDice, DiceType.COLORED);
                                        PaintIcon(l_iSecondDice, DiceType.WHITE);
                                        //Add any other dice required
                                        for ( Map.Entry<DiceType, Integer> entry : otherDice.entrySet())
                                        {
                                            PaintIcon(entry.getValue(), entry.getKey());
                                        }
                                    }
                                    else
                                    {
                                        msgpartCdice = Integer.toString(l_iFirstDice);
                                        msgpartWdice= Integer.toString(l_iSecondDice);
                                    }
                                    msgpartUser = l_strUser;
                                    msgpartSpecial = l_strSpecialMessages.toString();

                                    if (m_bShowDiceStats) {
                                        msgpartRest = l_strRestOfMsg;
                                    }
                                    FireDiceRoll();
                                }
                            }
                        }
                    }
                }
            }
            else // *** (Other dr) 3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)
            {
                //reset SAN message to the latest state for dice over map
                msgpartSAN = l_strSAN;
                l_iPos = l_strRestOfMsg.indexOf(" dr) ");

                if (l_iPos != -1) {
                    l_strCategory = l_strRestOfMsg.substring(0, l_iPos);
                    l_strRestOfMsg = l_strRestOfMsg.substring(l_iPos + " dr) ".length()); //3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)

                    l_iPos = l_strRestOfMsg.indexOf(" ***");

                    if (l_iPos != -1)
                    {
                        l_strDice = l_strRestOfMsg.substring(0, l_iPos);
                        l_strRestOfMsg = l_strRestOfMsg.substring(l_iPos + " ***".length());//   <FredKors>      [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        

                        if (l_strDice.length() == 1)
                        {
                            int l_iDice = Integer.parseInt(l_strDice);

                            if ((l_iDice > 0)
                                && (l_iDice < 7))
                            {
                                String[] lar_strParts = FindUser(l_strRestOfMsg);

                                if ((!lar_strParts[1].isEmpty()) && (!lar_strParts[2].isEmpty()))
                                {
                                    l_strUser = lar_strParts[1];

                                  msgpartCategory = BEFORE_CATEGORY + l_strCategory;

                                    if (m_bUseDiceImages)
                                    {
                                        msgpartCdice = (l_strDice);
                                        msgpartWdice="-1";
                                        PaintIcon(l_iDice, DiceType.SINGLE);
                                    }
                                    else
                                        msgpartCdice = l_strDice;
                                        msgpartWdice="-1";
                                    {

                                    }
                                    msgpartUser = l_strUser;
                                    // added by DR 2018 to add chatter text on Sniper Activation dr
                                    if (l_strCategory.equals("SA")) {
                                        String sniperstring="";
                                        if (l_iDice == 1) {
                                            sniperstring ="Eliminates SMC, Dummy stack, Sniper; Stuns & Recalls CE crew; breaks MMC & Inherent crew of certain vehicles; immobilizes unarmored vehicle (A14.3)" ;
                                        } else if (l_iDice == 2) {
                                            sniperstring ="Eliminates Dummy stack; Wounds SMC; Stuns CE crew; pins MMC, Inherent crew of certain vehicles, Sniper (A14.3)" ;
                                        }
                                        msgpartSpecial = sniperstring;
                                    }
                                    if (m_bShowDiceStats) {
                                        msgpartRest = l_strRestOfMsg;
                                    }

                                    FireDiceRoll();
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void HandleSpecialMessagesForOtherDice(final String l_strCategory, final ArrayList<String> specialMessages,
                                                   final int l_iFirstDice, final int l_iSecondDice, final Map<DiceType, Integer> otherDice)
    {
      int total = l_iFirstDice + l_iSecondDice;
      int unmodifiedTotal = total;
      final String SPACE = " ";
      // Dust
      if(environment.dustInEffect() && m_DRNotificationLevel == 3 && !otherDice.isEmpty())
      {
        switch (l_strCategory)
        {
          case "TH":
          case "IFT": {
            if (environment.isSpecialDust()) {
              total += environment.getSpecialDust(otherDice.get(DiceType.OTHER_DUST));
            } else {
              if (environment.isLightDust()) {
                total += Environment.getLightDust(otherDice.get(DiceType.OTHER_DUST));
              } else {
                total += Environment.getModerateDust(otherDice.get(DiceType.OTHER_DUST));
              }
            }
            specialMessages.add(environment.getCurrentDustLevel().toString() + SPACE);
            break;
          }

          case "MC": {
            if (environment.isSpecialDust()) {
              total -= environment.getSpecialDust(otherDice.get(DiceType.OTHER_DUST));
            } else {
              if (environment.isLightDust()) {
                total -= Environment.getLightDust(otherDice.get(DiceType.OTHER_DUST));
              } else {
                total -= Environment.getModerateDust(otherDice.get(DiceType.OTHER_DUST));
              }
            }
            specialMessages.add(environment.getCurrentDustLevel().toString() +" - if interdicted, MC is " + total);
            break;
          }
        }
      }
      // Night
      if(environment.isNight()  && m_DRNotificationLevel == 3)
      {
        switch (l_strCategory)
        {

          case "TH":
          case "IFT":
          {
            total += 1;
            specialMessages.add( "+1 Night LV"  + SPACE);
            break;
          }
        }
      }
      // LV
      if(environment.isLV())
      {
        LVLevel lvLevel = environment.getCurrentLVLevel();
        switch (l_strCategory) {
          case "TH":
          case "IFT":
          {
            switch (lvLevel)
            {
              case DAWN_DUSK: {
                total += 1;
                break;
              }

            }
            specialMessages.add(lvLevel + SPACE);
            break;
          }
        }
      }
      // Fog
      if(environment.isFog())
      {
        switch (l_strCategory)
        {

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
      if(environment.isHeatHaze())
      {
        switch (l_strCategory)
        {
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
      if(environment.isSunBlindness())
      {
        switch (l_strCategory)
        {
          case "TH":
          case "IFT":
          {
            SunBlindnessLevel sunBlindnessLevel = environment.getCurrentSunBlindnessLevel();
            specialMessages.add(sunBlindnessLevel.toString() + SPACE);
            break;
          }
        }
      }

      if( unmodifiedTotal < total || environment.dustInEffect()) {
        specialMessages.add("Total: " + total);
      }
    }



    private void PaintIcon(int l_iDice, DiceType diceType)     {

        try {
            ASLDie die = diceFactory.getASLDie(diceType);
            String dicefile = die.getDieHTMLFragment(l_iDice);
            if (msgpartDiceImage==null) {
                msgpartDiceImage = "<img  alt=\"alt text\" src=\"" + dicefile + "\">";
            } else {
                msgpartDiceImage = msgpartDiceImage + "&nbsp <img  alt=\"alt text\" src=\"" + dicefile + "\"> &nbsp";
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void build(org.w3c.dom.Element e) {
    }

    @Override
    public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc){
        return doc.createElement(getClass().getName());
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
            msgpartWdice="";
        }

        String catstyle = "msgcategory";
        String userstyle = getUserStyle();
        String specialstyle = "msgspecial";  //text-decoration: underline";  //<p style="text-decoration: underline;">This text will be underlined.</p>
        if (m_bUseDiceImages) {
            return "*~<span class=" + userstyle + ">" + msgpartDiceImage + "</span>"
                + "<span class=" + catstyle + ">" + msgpartCategory + "</span>"
                + "<span class=" + userstyle + ">" + USER_SPACING_PADDING + msgpartUser+ "</span>"
                + " " + "<u>"
                + "<span class=" + specialstyle + ">" + msgpartSpecial + "</span>"
                + "</u>" + " "
                + "<span class=" + userstyle + ">" + msgpartRest + "</span>";
        } else {
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
      if(msgpartUser.equals(me)) {
        return "mychat";
      }
      return "other";
    }
    private String makeTableString(String strMsg){
        strMsg= strMsg.substring(2);  // strip out "!!"
        String tablestyle = "tbl";
        return "*~<span class=" + tablestyle + ">" + strMsg + "</span>";
    }
   /**
   * Expects to be added to a GameModule.  Adds itself to the
   * controls window and registers itself as a
   * {@link CommandEncoder} */
    @Override
    public void addTo(Buildable b)
    {
        GameModule l_objGameModule = (GameModule) b;
        if (l_objGameModule.getChatter() != null)
        {
            // deleted code here which removed VASSAL elements but getChatter is always null at this point
        }

        l_objGameModule.setChatter(this);
        l_objGameModule.addCommandEncoder(this);
        l_objGameModule.addKeyStrokeSource(new KeyStrokeSource(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT));
        l_objGameModule.getPlayerWindow().addChatter(this);
        l_objGameModule.getControlPanel().add(this, BorderLayout.CENTER);
        final Prefs l_objModulePrefs = l_objGameModule.getPrefs();

        // font pref
        FontConfigurer l_objChatFontConfigurer;
        FontConfigurer l_objChatFontConfigurer_Exist = (FontConfigurer)l_objModulePrefs.getOption("ChatFont");
        if (l_objChatFontConfigurer_Exist == null) {
            l_objChatFontConfigurer = new FontConfigurer(CHAT_FONT, Resources.getString("Chatter.chat_font_preference")); //$NON-NLS-1$ //$NON-NLS-2$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objChatFontConfigurer); //$NON-NLS-1$
        } else {
            l_objChatFontConfigurer = l_objChatFontConfigurer_Exist;
        }
        l_objChatFontConfigurer.addPropertyChangeListener(evt -> {
            setFont((Font) evt.getNewValue());
            makeStyleSheet((Font) evt.getNewValue());
            makeASLStyleSheet((Font) evt.getNewValue());
            send(" ");
            send("- Chatter font changed");
            send(" ");
        });
        l_objChatFontConfigurer.fireUpdate();
        // buttons font pref
        FontConfigurer l_objButtonsFontConfigurer;
        FontConfigurer l_objButtonsFontConfigurer_Exist = (FontConfigurer)l_objModulePrefs.getOption("ButtonFont");
        if (l_objButtonsFontConfigurer_Exist == null)
        {
            l_objButtonsFontConfigurer = new FontConfigurer(BUTTON_FONT, "Chatter's dice buttons font: "); //$NON-NLS-1$ //$NON-NLS-2$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objButtonsFontConfigurer); //$NON-NLS-1$
        } else {
            l_objButtonsFontConfigurer = l_objButtonsFontConfigurer_Exist;
        }
        l_objButtonsFontConfigurer.addPropertyChangeListener(evt -> SetButtonsFonts((Font) evt.getNewValue()));
        l_objButtonsFontConfigurer.fireUpdate();
        //background colour pref
        ColorConfigurer l_objBackgroundColor;
        ColorConfigurer l_objBackgroundColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(CHAT_BACKGROUND_COLOR);
        if (l_objBackgroundColor_Exist == null)
        {
            l_objBackgroundColor = new ColorConfigurer(CHAT_BACKGROUND_COLOR, "Background color: ", Color.white); //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objBackgroundColor); //$NON-NLS-1$
        } else {
            l_objBackgroundColor = l_objBackgroundColor_Exist;
        }
        m_clrBackground = (Color) l_objModulePrefs.getValue(CHAT_BACKGROUND_COLOR);
        l_objBackgroundColor.addPropertyChangeListener(e -> {
            m_clrBackground = (Color) e.getNewValue();
            conversationPane.setBackground(m_clrBackground);
        });
        l_objBackgroundColor.fireUpdate();
        // game message color pref
        Prefs globalPrefs = Prefs.getGlobalPrefs();
        ColorConfigurer gameMsgColor = new ColorConfigurer("HTMLgameMessage1Color", Resources.getString("Chatter.game_messages_preference"), Color.black);
        gameMsgColor.addPropertyChangeListener((e) -> {
            gameMsg = (Color)e.getNewValue();
            makeStyleSheet(null);
            makeASLStyleSheet(null);
        });
        globalPrefs.addOption(Resources.getString("Chatter.chat_window"), gameMsgColor);
        gameMsg = (Color)globalPrefs.getValue("HTMLgameMessage1Color");

        // sys messages pref
        ColorConfigurer l_objSystemMsgColor;
        ColorConfigurer l_objSystemMsgColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(SYS_MSG_COLOR);
        if (l_objSystemMsgColor_Exist == null) {
            l_objSystemMsgColor = new ColorConfigurer(SYS_MSG_COLOR, Resources.getString("Chatter.system_message_preference"), new Color(160, 160, 160)); //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objSystemMsgColor); //$NON-NLS-1$
        } else {
            l_objSystemMsgColor = l_objSystemMsgColor_Exist;
        }
        systemMsg = (Color) l_objModulePrefs.getValue(SYS_MSG_COLOR);
        makeStyleSheet(null);
        l_objSystemMsgColor.addPropertyChangeListener(e -> {
            systemMsg = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // myChat preference
        ColorConfigurer l_objMyChatColor;
        ColorConfigurer l_objMyChatColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(MY_CHAT_COLOR);
        if (l_objMyChatColor_Exist == null) {
            l_objMyChatColor = new ColorConfigurer(MY_CHAT_COLOR, "My Name and Text Messages" , Color.gray); //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objMyChatColor); //$NON-NLS-1$
        } else {
            l_objMyChatColor = l_objMyChatColor_Exist;
        }
        myChat = (Color) l_objModulePrefs.getValue(MY_CHAT_COLOR);
        makeStyleSheet(null);
        l_objMyChatColor.addPropertyChangeListener(e -> {
            myChat = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // other chat preference
        ColorConfigurer l_objOtherChatColor;
        ColorConfigurer l_objOtherChatColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(OTHER_CHAT_COLOR);
        if (l_objOtherChatColor_Exist == null)
        {
            l_objOtherChatColor = new ColorConfigurer(OTHER_CHAT_COLOR, Resources.getString("Chatter.other_text_preference"), Color.black); //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objOtherChatColor); //$NON-NLS-1$
        } else {
            l_objOtherChatColor = l_objOtherChatColor_Exist;
        }
        otherChat = (Color) l_objModulePrefs.getValue(OTHER_CHAT_COLOR);
        makeStyleSheet(null);
        l_objOtherChatColor.addPropertyChangeListener(e -> {
            otherChat = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // dice chat pref
        ColorConfigurer l_objDiceChatColor;
        ColorConfigurer l_objDiceChatColor_Exist = (ColorConfigurer)l_objModulePrefs.getOption(DICE_CHAT_COLOR);
        if (l_objDiceChatColor_Exist == null)
        {
            l_objDiceChatColor = new ColorConfigurer(DICE_CHAT_COLOR, "Dice Results font color: ", Color.black); //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objDiceChatColor); //$NON-NLS-1$
        } else {
            l_objDiceChatColor = l_objDiceChatColor_Exist;
        }
        gameMsg5 = (Color) l_objModulePrefs.getValue(DICE_CHAT_COLOR);
        makeStyleSheet(null);
        l_objDiceChatColor.addPropertyChangeListener(e -> {
            gameMsg5 = (Color) e.getNewValue();
            makeStyleSheet(null);
        });
        // dice images pref
        BooleanConfigurer l_objUseDiceImagesOption;
        BooleanConfigurer l_objUseDiceImagesOption_Exist = (BooleanConfigurer)l_objModulePrefs.getOption(USE_DICE_IMAGES);
        if (l_objUseDiceImagesOption_Exist == null)
        {
            l_objUseDiceImagesOption = new BooleanConfigurer(USE_DICE_IMAGES, "Use images for dice rolls", Boolean.TRUE);  //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objUseDiceImagesOption); //$NON-NLS-1$
        } else {
            l_objUseDiceImagesOption = l_objUseDiceImagesOption_Exist;
        }
        m_bUseDiceImages = (Boolean) (l_objModulePrefs.getValue(USE_DICE_IMAGES));
        l_objUseDiceImagesOption.addPropertyChangeListener(e -> m_bUseDiceImages = (Boolean) e.getNewValue());
        // dice stats pref
        BooleanConfigurer l_objShowDiceStatsOption;
        BooleanConfigurer l_objShowDiceStatsOption_Exist = (BooleanConfigurer)l_objModulePrefs.getOption(SHOW_DICE_STATS);
        if (l_objShowDiceStatsOption_Exist == null)
        {
            l_objShowDiceStatsOption = new BooleanConfigurer(SHOW_DICE_STATS, "Show dice stats after each dice rolls", Boolean.FALSE);  //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objShowDiceStatsOption); //$NON-NLS-1$
        } else {
            l_objShowDiceStatsOption = l_objShowDiceStatsOption_Exist;
        }
        m_bShowDiceStats = (Boolean) (l_objModulePrefs.getValue(SHOW_DICE_STATS));
        l_objShowDiceStatsOption.addPropertyChangeListener(e -> m_bShowDiceStats = (Boolean) e.getNewValue());

        // coloured die pref
        StringEnumConfigurer coloredDiceColor;
        StringEnumConfigurer coloredDiceColor_Exist = (StringEnumConfigurer) l_objModulePrefs.getOption(COLORED_DICE_COLOR);
        if (coloredDiceColor_Exist==null){
            coloredDiceColor = new StringEnumConfigurer(COLORED_DICE_COLOR, "Colored Die Color:", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), coloredDiceColor);
        } else {
            coloredDiceColor = coloredDiceColor_Exist;
        }
        coloredDiceColor.addPropertyChangeListener(e -> {
          m_clrColoredDiceColor = (String) e.getNewValue();
          if (m_clrColoredDiceColor!=null) {
            diceFactory.setDieColor(DiceType.COLORED, DieColor.getEnum(m_clrColoredDiceColor));
          }
        });
        m_clrColoredDiceColor = l_objModulePrefs.getStoredValue("coloredDiceColor");
        if(m_clrColoredDiceColor != null)
          diceFactory.setDieColor(DiceType.COLORED,DieColor.getEnum(m_clrColoredDiceColor));

        // single die pref
        StringEnumConfigurer l_objColoredDieColor;
        StringEnumConfigurer l_objColoredDieColor_Exist = (StringEnumConfigurer)l_objModulePrefs.getOption(SINGLE_DIE_COLOR);
        if (l_objColoredDieColor_Exist == null) {
            l_objColoredDieColor = new StringEnumConfigurer(SINGLE_DIE_COLOR, "Single die color:  ", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objColoredDieColor); //$NON-NLS-1$
        } else {
            l_objColoredDieColor = l_objColoredDieColor_Exist;
        }
        l_objColoredDieColor.addPropertyChangeListener(e -> {
          m_clrSingleDieColor = (String) e.getNewValue();
          if (m_clrSingleDieColor!=null) {
            diceFactory.setDieColor(DiceType.SINGLE, DieColor.getEnum(m_clrSingleDieColor));
          }
        });
        m_clrSingleDieColor = l_objModulePrefs.getStoredValue("singleDieColor");
        if(m_clrSingleDieColor != null)
          diceFactory.setDieColor(DiceType.SINGLE,DieColor.getEnum(m_clrSingleDieColor));

        // third die pref
        StringEnumConfigurer l_objThirdDieColor;
        l_objThirdDieColor = new StringEnumConfigurer(THIRD_DIE_COLOR, "Third die color:  ", new String[] {"Black", "Blue","Cyan", "Purple", "Red", "Green", "Yellow", "Orange", "AlliedM", "AxisM", "American", "British", "Finnish", "French", "German", "Italian", "Japanese", "Russian", "Swedish"} );
        l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objThirdDieColor); //$NON-NLS-1$
        l_objThirdDieColor.addPropertyChangeListener(e -> {
          m_clrDustColoredDiceColor = (String) e.getNewValue();
          if (m_clrDustColoredDiceColor!=null) {
            diceFactory.setDieColor(DiceType.OTHER_DUST, DieColor.getEnum(m_clrDustColoredDiceColor));
          }
        });
        m_clrDustColoredDiceColor = l_objModulePrefs.getStoredValue("thirdDieColor");
        if(m_clrDustColoredDiceColor != null)
          diceFactory.setDieColor(DiceType.SINGLE,DieColor.getEnum(m_clrDustColoredDiceColor));
        l_objThirdDieColor.fireUpdate();

        // rule set pref
        StringEnumConfigurer l_objSpecialDiceRollNotificationLevel = (StringEnumConfigurer)l_objModulePrefs.getOption(NOTIFICATION_LEVEL);
        final String[] l_DROptions = {
                "None",
                "Snipers only",
                "Starter Kit",
                "Full ASL"
        };
        if(l_objSpecialDiceRollNotificationLevel == null) {
            l_objSpecialDiceRollNotificationLevel = new StringEnumConfigurer(NOTIFICATION_LEVEL,
                    "Notify about special DRs: ", l_DROptions);
            l_objSpecialDiceRollNotificationLevel.setValue("Full ASL");
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objSpecialDiceRollNotificationLevel);
        }
        for(int i = 0; i < l_DROptions.length; ++i) {
            if (l_DROptions[i].equals(l_objSpecialDiceRollNotificationLevel.getValueString())) {
                m_DRNotificationLevel = i;
                break;
            }
        }
        // just for access from inside the event handler
        final StringEnumConfigurer __cfg = l_objSpecialDiceRollNotificationLevel;
        l_objSpecialDiceRollNotificationLevel.addPropertyChangeListener(e -> {
            for(int i = 0; i < l_DROptions.length; ++i){
                if(l_DROptions[i].equals(__cfg.getValueString())) {
                    m_DRNotificationLevel = i;
                    return;
                }
            }
            m_DRNotificationLevel = 3;
        });
        // Player Window pref
        l_objColoredDieColor.fireUpdate();
        final BooleanConfigurer AlwaysOnTop = new BooleanConfigurer("PWAlwaysOnTop", "Player Window (menus, toolbar, chat) is always on top in uncombined application mode (requires a VASSAL restart)", false);
        getGameModule().getPrefs().addOption(preferenceTabName, AlwaysOnTop);
    }

    @Override
    public void add(Buildable b)
    {
    }

    @Override
    public void keyCommand(KeyStroke e)
    {
        if ((e.getKeyCode() == 0 || e.getKeyCode() == KeyEvent.CHAR_UNDEFINED)
            && !Character.isISOControl(e.getKeyChar()))
        {
            m_edtInputText.setText(m_edtInputText.getText() + e.getKeyChar());
        }
        else if (e.isOnKeyRelease())
        {
            switch (e.getKeyCode())
            {
                case KeyEvent.VK_ENTER:
                    if (m_edtInputText.getText().length() > 0)
                    {
                        send(formatChat(m_edtInputText.getText()));
                    }

                    m_edtInputText.setText(""); //$NON-NLS-1$
                    break;

                case KeyEvent.VK_BACK_SPACE:
                case KeyEvent.VK_DELETE:
                    String s = m_edtInputText.getText();
                    if (s.length() > 0)
                    {
                        m_edtInputText.setText(s.substring(0, s.length() - 1));
                    }
                    break;
            }
        }
    }



  private void FireDiceRoll() {
        for (ChatterListener objListener : chatter_listeners)
            objListener.DiceRoll(msgpartCategory, msgpartUser, msgpartSAN, Integer.parseInt(msgpartCdice), Integer.parseInt(msgpartWdice));
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
