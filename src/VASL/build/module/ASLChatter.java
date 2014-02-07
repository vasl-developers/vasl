/*
 * $Id: Chatter.java 8851 2013-10-01 19:34:04Z uckelman $
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GlobalOptions;
import VASSAL.command.CommandEncoder;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.ColorConfigurer;
import VASSAL.configure.FontConfigurer;
import VASSAL.configure.HotKeyConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.tools.KeyStrokeListener;
import VASSAL.tools.KeyStrokeSource;
import VASSAL.tools.ScrollPane;
import VASSAL.tools.imageop.Op;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;

/**
 * The chat window component.  Displays text messages and
 * accepts i.  Also acts as a {@link CommandEncoder},
 * encoding/decoding commands that display message in the text area
 */
public class ASLChatter extends VASSAL.build.module.Chatter
{
  public static final String BEFORE_CATEGORY = "   ";
  private static final String USE_DICE_IMAGES = "useDiceImages"; //$NON-NLS-1$
  private static final String COLORED_DICE_COLOR = "coloredDiceColor"; //$NON-NLS-1$
  private static final String SINGLE_DIE_COLOR = "singleDieColor"; //$NON-NLS-1$
  private final static String m_strFileNameFormat = "chatter/DC%s_%s.png";

  private Color m_clrGameMsg;
  private Color m_clrSystemMsg;
  private Color m_crlMyChatMsg;
  private Color m_clrOtherChatMsg;
  private Color m_clrColoredDiceColor;
  private Color m_clrSingleDieColor;
  
  private JButton m_btnDR;
  private JButton m_btnIFT;
  private JButton m_btnTH;
  private JButton m_btnTK;
  private JButton m_btnMC;
  private JButton m_btnRally;
  private JButton m_btnCC;
  private JButton m_btnTC;
  private JButton m_btndr;
  private JButton m_btnSA;
  private JButton m_btnRS;
  
  private JTextPane m_objChatPanel;
  private StyledDocument m_objDocument;
  private StyleContext m_objStyleContext;
  
  private Font m_objChatterFont;
  
  private Style m_objMainStyle;
  private Style m_objIconStyle;
  private boolean m_bUseDiceImages;
  
  private final Icon [] mar_objWhiteDCIcon = new Icon[6];
  private final Icon [] mar_objColoredDCIcon = new Icon[6];
  private final Icon [] mar_objSingleDieIcon = new Icon[6];
  
  private JTextField m_edtInputText;
  private final JScrollPane m_objScrollPane = new ScrollPane(
       JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
       JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    public void RemoveActionForKeyStroke(KeyStroke aKeyStroke) 
    {
        ActionMap am = m_objChatPanel.getActionMap();

        if (am == null) {
            return;
        }

        for (int counter = 0; counter < 3; counter++) {
            InputMap inputMap = m_objChatPanel.getInputMap(counter);

            if (inputMap != null) {
                Object actionBinding = inputMap.get(aKeyStroke);

                if (actionBinding != null) {
                    Action action = am.get(actionBinding);

                    if (action != null) {
                        action.setEnabled(false);
                    }
                }
            }
        }
    }

    public ASLChatter() {
        super();

        // remove chatter components
        if (input != null)
            remove(input);
        
        if (scroll != null)
        {
            scroll.setViewportView(null);
            remove(scroll);
        }

        // free chatter components
        if (conversation != null)
            conversation = null;
        if (input != null)
            input = null;
        if (scroll != null)
            scroll = null;

        m_clrGameMsg = Color.magenta;
        m_clrSystemMsg = new Color(160, 160, 160);
        m_crlMyChatMsg = Color.gray;
        m_clrOtherChatMsg =  Color.black;
        m_clrColoredDiceColor = Color.YELLOW;
        m_clrSingleDieColor = Color.RED;
        
        // create new components
        m_objStyleContext = new StyleContext();
        m_objDocument = new DefaultStyledDocument(m_objStyleContext);

        Style l_objDefaultStyle = m_objStyleContext.getStyle(StyleContext.DEFAULT_STYLE);

        m_objMainStyle = m_objStyleContext.addStyle("MainStyle", l_objDefaultStyle);
        m_objIconStyle = m_objStyleContext.addStyle("IconStyle", l_objDefaultStyle);
        
        try
        {
            for (int l_i = 0; l_i < 6; l_i++)
            {
                mar_objWhiteDCIcon[l_i] = new ImageIcon(Op.load(String.format(m_strFileNameFormat, String.valueOf(l_i + 1), "W")).getImage(null));
                mar_objColoredDCIcon[l_i] = null;
                mar_objSingleDieIcon[l_i] = null;
            }
            
            RebuildColoredDiceFaces();
            RebuildSingleDieFaces();
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
        
        m_objChatPanel = new JTextPane(m_objDocument);
        m_objChatPanel.setEditable(false);
        
        m_objChatPanel.addKeyListener(new KeyListener() 
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
        
        RemoveActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK));        
        RemoveActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
       
        m_objChatPanel.addComponentListener(new ComponentAdapter() 
        {
            public void componentResized(ComponentEvent e) 
            {
                m_objScrollPane.getVerticalScrollBar().setValue(m_objScrollPane.getVerticalScrollBar().getMaximum());
            }
        });

        m_btnDR = CreateChatterDiceButton("DRs.gif", "DR", "DR", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), true, ASLDiceBot.OTHER_CATEGORY);
        m_btnIFT = CreateChatterDiceButton("", "IFT", "IFT attack DR", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "IFT");
        m_btnTH = CreateChatterDiceButton("", "TH", "To Hit DR", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "TH");
        m_btnTK = CreateChatterDiceButton("", "TK", "To Kill DR", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "TK");
        m_btnMC = CreateChatterDiceButton("", "MC", "Morale Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "MC");
        m_btnRally = CreateChatterDiceButton("", "Rally", "Rally DR", KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "Rally");
        m_btnCC = CreateChatterDiceButton("", "CC", "Close Combat DR", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "CC");
        m_btnTC = CreateChatterDiceButton("", "TC", "Task Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), true, "TC");
        m_btndr = CreateChatterDiceButton("dr.gif", "dr", "dr", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), false, ASLDiceBot.OTHER_CATEGORY);
        m_btnSA = CreateChatterDiceButton("", "SA", "Sniper Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), false, "SA");
        m_btnRS = CreateChatterDiceButton("", "RS", "Random Selection dr", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK), false, "RS");
// For the future?        
//        JButton l_btnThinking = CreateInfoButton("Thinking", "I'm thinking", "I'm thinking", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK));
//        JButton l_btnHold = CreateInfoButton("Wait", "Wait, please", "Wait, please", KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK));
//        JButton l_btnContinue = CreateInfoButton("Continue", "Continue, please", "Continue, please", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK));
//        JButton l_btnOk  = CreateInfoButton("Ok", "Ok", "Ok", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK));
        
        JPanel l_objPanelContainer = new JPanel();
        l_objPanelContainer.setLayout(new BoxLayout(l_objPanelContainer, BoxLayout.LINE_AXIS));
        l_objPanelContainer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        
        JPanel l_objButtonPanel = new JPanel();
        l_objButtonPanel.setLayout(new GridBagLayout());
        l_objButtonPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 2, 1));
        l_objButtonPanel.setMaximumSize(new Dimension(800, 800));
        
        GridBagConstraints l_objGridBagConstraints = new GridBagConstraints();
        l_objGridBagConstraints.fill = GridBagConstraints.BOTH;
        l_objGridBagConstraints.weightx = 0.5;
        l_objGridBagConstraints.weighty = 0.5;
        l_objGridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);

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
// For the future?
//        l_objButtonPanel.add(l_btnThinking);
//        l_objButtonPanel.add(l_btnHold);
//        l_objButtonPanel.add(l_btnContinue);
//        l_objButtonPanel.add(l_btnOk);


        m_edtInputText = new JTextField(60);
        m_edtInputText.setFocusTraversalKeysEnabled(false);
        m_edtInputText.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent e) 
            {
                send(formatChat(e.getActionCommand()));                
                m_edtInputText.setText(""); //$NON-NLS-1$
            }
        });

        m_edtInputText.setMaximumSize(new Dimension(m_edtInputText.getMaximumSize().width,
        m_edtInputText.getPreferredSize().height));

        m_objScrollPane.setViewportView(m_objChatPanel);
        
        l_objPanelContainer.add(l_objButtonPanel);
        
        GroupLayout l_objGroupLayout = new GroupLayout(this);
        setLayout(l_objGroupLayout);
        l_objGroupLayout.setHorizontalGroup(
            l_objGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(m_objScrollPane)
            .addComponent(l_objPanelContainer, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(m_edtInputText)
        );
        l_objGroupLayout.setVerticalGroup(
            l_objGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(l_objGroupLayout.createSequentialGroup()
                .addComponent(m_objScrollPane, GroupLayout.DEFAULT_SIZE, 350, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(l_objPanelContainer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(m_edtInputText, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );
    }

    private void SetButtonsFonts(Font objFont) 
    {
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
    
// For the future?
//    private JButton CreateInfoButton(String strCaption, String strTooltip, final String strMsg, KeyStroke objKeyStroke) 
//    {
//        JButton l_btn = new JButton(strCaption);
//        
//        l_btn.setPreferredSize(new Dimension(90, 25));
//        l_btn.setMargin(new Insets(l_btn.getMargin().top, 0, l_btn.getMargin().bottom, 0));
//        
//        ActionListener l_objAL = new ActionListener()
//        {
//            public void actionPerformed(ActionEvent e)
//            {
//                send(formatChat(strMsg));
//            }
//        };
//        l_btn.addActionListener(l_objAL);
//        KeyStrokeListener l_objListener = new KeyStrokeListener(l_objAL);
//        l_objListener.setKeyStroke(objKeyStroke);
//        AddHotKeyToTooltip(l_btn, l_objListener, strTooltip);
//        l_btn.setFocusable(false);
//        GameModule.getGameModule().addKeyStrokeListener(l_objListener);
//        return l_btn;
//    }

    private JButton CreateChatterDiceButton(String strImage, String strCaption, String strTooltip, KeyStroke keyStroke, final boolean bDice, final String strCat) 
    {
        JButton l_btn = new JButton(strCaption);
        
        l_btn.setMargin(new Insets(0, 0, 0, 0));       
        
        if (strImage != "")
        {
            try
            {
                l_btn.setIcon(new ImageIcon(Op.load(strImage).getImage(null)));
            }
            catch (Exception ex)
            {
            }
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
        KeyStrokeListener l_Listener = new KeyStrokeListener(l_objAL);
        l_Listener.setKeyStroke(keyStroke);
        AddHotKeyToTooltip(l_btn, l_Listener, strTooltip);
        l_btn.setFocusable(false);
        GameModule.getGameModule().addKeyStrokeListener(l_Listener);
        
        return l_btn;
    }

    private void AddHotKeyToTooltip(JButton objButton, KeyStrokeListener objListener, String strTooltipText) 
    {
        if (objListener.getKeyStroke() != null)
            objButton.setToolTipText(strTooltipText + " [" + HotKeyConfigurer.getString(objListener.getKeyStroke()) + "]");
    }
    
    private String formatChat(String text) 
    {
        final String id = GlobalOptions.getInstance().getPlayerId();
        
        return "<" + (id.length() == 0 ? "(" + getAnonymousUserName() + ")" : id) + "> - " + text; //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public JTextField getInputField() 
    {
        return m_edtInputText;
    }
    
    String [] FindUser (String strVal)
    {
        String [] lar_strRetValue = new String[] {strVal,"",""};
        
        int l_iUserStart = strVal.indexOf("<");
        int l_iUserEnd = strVal.indexOf(">");
        
        if ((l_iUserStart != -1) && (l_iUserEnd != -1))
        {
            lar_strRetValue[0] = strVal.substring(0, l_iUserStart + 1);
            lar_strRetValue[1] = strVal.substring(l_iUserStart + 1, l_iUserEnd);
            lar_strRetValue[2] = strVal.substring(l_iUserEnd);
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
                if (strMsg.startsWith("*** DR = "))
                {
                    ParseOldDR(strMsg);
                }
                else if (strMsg.startsWith("*** dr = "))
                {
                    ParseOlddr(strMsg);
                }
                else if (strMsg.startsWith("*** ("))
                {
                    ParseNewDiceRoll(strMsg);
                }
                else if (strMsg.startsWith("<"))
                {
                    ParseUserMsg(strMsg);
                }
                else if (strMsg.startsWith("-"))
                {
                    ParseSystemMsg(strMsg);
                }
                else if (strMsg.startsWith("*"))
                {
                    ParseMoveMsg(strMsg);
                }
                else
                {
                    ParseDefaultMsg(strMsg);
                }
            }
        } 
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
    }

    private void ParseDefaultMsg(String strMsg) {
        try
        {
            StyleConstants.setForeground(m_objMainStyle, Color.black);
            m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void ParseMoveMsg(String strMsg) {
        try
        {
            StyleConstants.setForeground(m_objMainStyle, m_clrGameMsg);
            m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
    
    private void ParseSystemMsg(String strMsg) {
        try
        {
            StyleConstants.setForeground(m_objMainStyle, m_clrSystemMsg);
            m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
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

            if (strMsg.startsWith(formatChat(""))) { //$NON-NLS-1$
                StyleConstants.setForeground(m_objMainStyle, m_crlMyChatMsg);
            } else {
                StyleConstants.setForeground(m_objMainStyle, m_clrOtherChatMsg);
            }

            if ((lar_strParts[1] != "") && (lar_strParts[2] != ""))
            {
                m_objDocument.insertString(m_objDocument.getLength(), "\n" + lar_strParts[0], m_objMainStyle);
                StyleConstants.setBold(m_objMainStyle, true);
                m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[1], m_objMainStyle);
                StyleConstants.setBold(m_objMainStyle, false);
                m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[2], m_objMainStyle);
            }
            else
                m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void ParseOlddr(String strMsg) {
        try
        {
            String l_strRestOfMsg = strMsg.substring("*** dr = ".length());
            int l_iPos = l_strRestOfMsg.indexOf(" ***");

            StyleConstants.setForeground(m_objMainStyle, m_clrGameMsg);

            if (l_iPos != -1)
            {
                String l_strLast = l_strRestOfMsg.substring(l_iPos);
                String l_strDice = l_strRestOfMsg.substring(0, l_iPos);

                if (l_strDice.length() == 1)
                {
                    int l_iDice = Integer.parseInt(l_strDice);

                    if ((l_iDice > 0)
                        && (l_iDice < 7))
                    {
                        String[] lar_strParts = FindUser(l_strLast);

                        if ((lar_strParts[1] != "") && (lar_strParts[2] != ""))
                        {
                            m_objDocument.insertString(m_objDocument.getLength(), "\n*** dr = ", m_objMainStyle);
                            if (m_bUseDiceImages)
                            {
                                PaintIcon(l_iDice, true, true);
                            }
                            else
                            {
                                StyleConstants.setBold(m_objMainStyle, true);
                                m_objDocument.insertString(m_objDocument.getLength(), l_strDice, m_objMainStyle);
                                StyleConstants.setBold(m_objMainStyle, false);
                            }
                            m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[0], m_objMainStyle);
                            StyleConstants.setBold(m_objMainStyle, true);
                            m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[1], m_objMainStyle);
                            StyleConstants.setBold(m_objMainStyle, false);
                            m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[2], m_objMainStyle);
                        }
                        else
                        {
                            m_objDocument.insertString(m_objDocument.getLength(), "\n*** DR = ", m_objMainStyle);
                            PaintIcon(l_iDice, true, true);                            
                            m_objDocument.insertString(m_objDocument.getLength(), l_strLast, m_objMainStyle);
                        }
                    }
                    else
                        m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                }
                else
                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
            }
            else
                m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void ParseNewDiceRoll(String strMsg)  
    {
        // *** (Other DR) 4,2 ***   <FredKors>      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        
        String l_strCategory, l_strDice, l_strUser;
        int l_iFirstDice, l_iSecondDice;
        
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
                
                    if (l_strDice.length() == 3)
                    {
                        String [] lar_strDice = l_strDice.split(",");

                        if (lar_strDice.length == 2)
                        {
                            l_iFirstDice = Integer.parseInt(lar_strDice[0]);
                            l_iSecondDice = Integer.parseInt(lar_strDice[1]);

                            if ((l_iFirstDice > 0)
                                && (l_iFirstDice < 7)
                                && (l_iSecondDice > 0)
                                && (l_iSecondDice < 7))
                            {
                                String[] lar_strParts = FindUser(l_strRestOfMsg);

                                if ((lar_strParts[1] != "") && (lar_strParts[2] != ""))
                                {
                                    String l_strSAN = "";
                                    
                                    l_strUser = lar_strParts[1];
                                    l_strRestOfMsg = lar_strParts[2]; // >      Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        
                                    
                                    l_strRestOfMsg = l_strRestOfMsg.replace(">", " ").trim(); //Allied SAN    [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        
                                    
                                    if (l_strRestOfMsg.startsWith("Axis SAN"))
                                    {
                                        l_strSAN = "Axis SAN";
                                        l_strRestOfMsg = l_strRestOfMsg.substring("Axis SAN".length());
                                    }
                                    else if (l_strRestOfMsg.startsWith("Allied SAN"))
                                    {
                                        l_strSAN = "Allied SAN";
                                        l_strRestOfMsg = l_strRestOfMsg.substring("Allied SAN".length());
                                    }
                                    else if (l_strRestOfMsg.startsWith("Axis/Allied SAN"))
                                    {
                                        l_strSAN = "Axis/Allied SAN";
                                        l_strRestOfMsg = l_strRestOfMsg.substring("Axis/Allied SAN".length());
                                    }
                                    
                                    StyleConstants.setForeground(m_objMainStyle, Color.BLACK);
                                    StyleConstants.setBold(m_objMainStyle, true);
                                    
                                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + BEFORE_CATEGORY + l_strCategory + "\t", m_objMainStyle);
                                    
                                    StyleConstants.setForeground(m_objMainStyle, m_clrGameMsg);
                                    StyleConstants.setBold(m_objMainStyle, false);
                                    
                                    if (m_bUseDiceImages)
                                    {
                                        PaintIcon(l_iFirstDice, true, false);
                                        m_objDocument.insertString(m_objDocument.getLength(), " ", m_objMainStyle);
                                        PaintIcon(l_iSecondDice, false, false);
                                    }
                                    else
                                    {
                                        StyleConstants.setBold(m_objMainStyle, true);
                                        m_objDocument.insertString(m_objDocument.getLength(), l_strDice, m_objMainStyle);
                                        StyleConstants.setBold(m_objMainStyle, false);
                                    }
                                    m_objDocument.insertString(m_objDocument.getLength(), "  ...  ", m_objMainStyle);

                                    StyleConstants.setBold(m_objMainStyle, true);
                                    m_objDocument.insertString(m_objDocument.getLength(), l_strUser, m_objMainStyle);
                                    
                                    StyleConstants.setBold(m_objMainStyle, false);
                                    m_objDocument.insertString(m_objDocument.getLength(), "   ", m_objMainStyle);                                    
                                    
                                    StyleConstants.setBold(m_objMainStyle, true);
                                    StyleConstants.setUnderline(m_objMainStyle, true);
                                    m_objDocument.insertString(m_objDocument.getLength(), l_strSAN, m_objMainStyle);                                    
                                    
                                    StyleConstants.setBold(m_objMainStyle, false);
                                    StyleConstants.setUnderline(m_objMainStyle, false);
                                    m_objDocument.insertString(m_objDocument.getLength(), l_strRestOfMsg, m_objMainStyle);                                    
                                }
                                else
                                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                            }
                            else
                                m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                        }
                        else
                            m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                    }
                    else
                        m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                }
                else
                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
            }
            else // *** (Other dr) 3 ***   <FredKors>      [1 / 1   avg   3,00 (3,00)]    (01.84)
            {
                l_iPos = l_strRestOfMsg.indexOf(" dr) ");
            
                if (l_iPos != -1)
                {
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

                                if ((lar_strParts[1] != "") && (lar_strParts[2] != ""))
                                {
                                    l_strUser = lar_strParts[1];
                                    l_strRestOfMsg = lar_strParts[2]; // >      [1 / 8   avg   6,62 (6,62)]    (01.51 - by random.org)        
                                    
                                    l_strRestOfMsg = l_strRestOfMsg.replace(">", " ").trim();
                                    
                                    StyleConstants.setForeground(m_objMainStyle, Color.BLACK);
                                    StyleConstants.setBold(m_objMainStyle, true);
                                    
                                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + BEFORE_CATEGORY + l_strCategory + "\t", m_objMainStyle);
                                    
                                    StyleConstants.setForeground(m_objMainStyle, m_clrGameMsg);
                                    StyleConstants.setBold(m_objMainStyle, false);
                                    
                                    if (m_bUseDiceImages)
                                    {
                                        PaintIcon(l_iDice, true, true);
                                    }
                                    else
                                    {
                                        StyleConstants.setBold(m_objMainStyle, true);
                                        m_objDocument.insertString(m_objDocument.getLength(), l_strDice, m_objMainStyle);
                                        StyleConstants.setBold(m_objMainStyle, false);
                                    }
                                    m_objDocument.insertString(m_objDocument.getLength(), "  ...  ", m_objMainStyle);

                                    StyleConstants.setBold(m_objMainStyle, true);
                                    m_objDocument.insertString(m_objDocument.getLength(), l_strUser, m_objMainStyle);
                                    
                                    StyleConstants.setBold(m_objMainStyle, false);
                                    m_objDocument.insertString(m_objDocument.getLength(), "   " + l_strRestOfMsg, m_objMainStyle);                                    
                                }
                                else
                                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);                            
                            }
                            else
                                m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);                            
                        }                       
                        else
                            m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);                        
                    }
                    else
                        m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                }
                else
                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
            }            
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }        
    }
    
    private void ParseOldDR(String strMsg)  
    {
        try
        {
            String l_strRestOfMsg = strMsg.substring("*** DR = ".length());
            int l_iPos = l_strRestOfMsg.indexOf(" ***");

            StyleConstants.setForeground(m_objMainStyle, m_clrGameMsg);

            if (l_iPos != -1)
            {
                String l_strLast = l_strRestOfMsg.substring(l_iPos);
                String l_strDice = l_strRestOfMsg.substring(0, l_iPos);

                if (l_strDice.length() == 3)
                {
                    String [] lar_strDice = l_strDice.split(",");

                    if (lar_strDice.length == 2)
                    {
                        int l_iFirstDice = Integer.parseInt(lar_strDice[0]);
                        int l_iSecondDice = Integer.parseInt(lar_strDice[1]);

                        if ((l_iFirstDice > 0)
                            && (l_iFirstDice < 7)
                            && (l_iSecondDice > 0)
                            && (l_iSecondDice < 7))
                        {
                            String[] lar_strParts = FindUser(l_strLast);

                            if ((lar_strParts[1] != "") && (lar_strParts[2] != ""))
                            {
                                m_objDocument.insertString(m_objDocument.getLength(), "\n*** DR = ", m_objMainStyle);
                                if (m_bUseDiceImages)
                                {
                                    PaintIcon(l_iFirstDice, true, false);
                                    m_objDocument.insertString(m_objDocument.getLength(), " ", m_objMainStyle);
                                    PaintIcon(l_iSecondDice, false, false);
                                }
                                else
                                {
                                    StyleConstants.setBold(m_objMainStyle, true);
                                    m_objDocument.insertString(m_objDocument.getLength(), l_strDice, m_objMainStyle);
                                    StyleConstants.setBold(m_objMainStyle, false);
                                }
                                m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[0], m_objMainStyle);
                                StyleConstants.setBold(m_objMainStyle, true);
                                m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[1], m_objMainStyle);
                                StyleConstants.setBold(m_objMainStyle, false);
                                m_objDocument.insertString(m_objDocument.getLength(), lar_strParts[2], m_objMainStyle);
                            }
                            else
                            {
                                m_objDocument.insertString(m_objDocument.getLength(), "\n*** DR = ", m_objMainStyle);
                                PaintIcon(l_iFirstDice, true, false);
                                m_objDocument.insertString(m_objDocument.getLength(), " ", m_objMainStyle);
                                PaintIcon(l_iSecondDice, false, false);
                                m_objDocument.insertString(m_objDocument.getLength(), l_strLast, m_objMainStyle);
                            }
                        }
                        else
                            m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                    }
                    else
                        m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
                }
                else
                    m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
            }
            else
                m_objDocument.insertString(m_objDocument.getLength(), "\n" + strMsg, m_objMainStyle);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private void PaintIcon(int l_iDice, boolean bColored, boolean bSingle) 
    {
        try
        {
            JLabel l_objLabel = new JLabel((bSingle ? mar_objSingleDieIcon[l_iDice - 1] : (bColored ? mar_objColoredDCIcon[l_iDice - 1] : mar_objWhiteDCIcon[l_iDice - 1])));
            l_objLabel.setAlignmentY(0.7f);
            
            StyleConstants.setComponent(m_objIconStyle, l_objLabel);        
            m_objDocument.insertString(m_objDocument.getLength(), "Ignored", m_objIconStyle);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
    
    private void RebuildStyles()
    {
        send(" ");
        
        StyleConstants.setAlignment(m_objMainStyle, StyleConstants.ALIGN_LEFT);
        StyleConstants.setFontFamily(m_objMainStyle, m_objChatterFont.getFamily());
        StyleConstants.setFontSize(m_objMainStyle, m_objChatterFont.getSize());
        StyleConstants.setSpaceAbove(m_objMainStyle, 2);
        StyleConstants.setSpaceBelow(m_objMainStyle, 2);
        
        send("- Chatter font changed");
        send(" ");
        
        FontMetrics l_objFM = m_objChatPanel.getFontMetrics(m_objChatterFont);
        
        float l_f = (float)l_objFM.stringWidth(BEFORE_CATEGORY + ASLDiceBot.OTHER_CATEGORY + "XXX");
        TabStop[] lar_objTabs = new TabStop[10]; // this sucks

        for(int l_i = 0; l_i < lar_objTabs.length; l_i++)
        {
             lar_objTabs[l_i] = new TabStop(l_f * (l_i + 1), TabStop.ALIGN_LEFT, TabStop.LEAD_NONE);
        }

        TabSet l_objTabset = new TabSet(lar_objTabs);
     
        StyleConstants.setTabSet(m_objMainStyle, new TabSet(new TabStop[0]));
        StyleConstants.setTabSet(m_objMainStyle, l_objTabset);
        
        m_objChatPanel.setParagraphAttributes(m_objMainStyle, true);
    }

    @Override
    public void setFont(Font f) 
    {
        if (m_edtInputText != null) 
        {
            if (m_edtInputText.getText().length() == 0) 
            {
                m_edtInputText.setText("XXX"); //$NON-NLS-1$
                m_edtInputText.setFont(f);
                m_edtInputText.setText(""); //$NON-NLS-1$
                
            } 
            else 
            {
                m_edtInputText.setFont(f);
            }
        }
        
        m_objChatterFont = f;
    }
  
    @Override
    public void build(org.w3c.dom.Element e) 
    {
    }

    @Override
    public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) 
    {
        return doc.createElement(getClass().getName());
    }

    private void RebuildColoredDiceFaces() 
    {
        BufferedImage l_objImage = null;

        try
        {
            for (int l_i = 0; l_i < 6; l_i++)
            {
                l_objImage = Op.load(String.format(m_strFileNameFormat, String.valueOf(l_i + 1), "W")).getImage(null);
                mar_objColoredDCIcon[l_i] = new ImageIcon(ColorChanger.changeColor(l_objImage, Color.white, m_clrColoredDiceColor));
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
                l_objImage = Op.load(String.format(m_strFileNameFormat, String.valueOf(l_i + 1), "W")).getImage(null);
                mar_objSingleDieIcon[l_i] = new ImageIcon(ColorChanger.changeColor(l_objImage, Color.white, m_clrSingleDieColor));
            }
        }
        catch (Exception ex) 
        {
            ex.printStackTrace();
        }
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
            try
            {
                l_objGameModule.getControlPanel().remove(l_objGameModule.getChatter());
                l_objGameModule.remove(l_objGameModule.getChatter());
            }
            catch(Exception ex)
            { }               
        }
        
        l_objGameModule.setChatter(this);
        l_objGameModule.addCommandEncoder(this);
        l_objGameModule.addKeyStrokeSource(new KeyStrokeSource(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT));
        l_objGameModule.getControlPanel().add(this, BorderLayout.CENTER);

        final Prefs l_objModulePrefs = l_objGameModule.getPrefs();
        
        FontConfigurer l_objChatFontConfigurer = null;
        FontConfigurer l_objChatFontConfigurer_Exist = (FontConfigurer)l_objModulePrefs.getOption("ChatFont");

        if (l_objChatFontConfigurer_Exist == null)
        {
            l_objChatFontConfigurer = new FontConfigurer("ChatFont", Resources.getString("Chatter.chat_font_preference")); //$NON-NLS-1$ //$NON-NLS-2$
            l_objModulePrefs.addOption(Resources.getString("Chatter.chat_window"), l_objChatFontConfigurer); //$NON-NLS-1$
        }
        else
            l_objChatFontConfigurer = l_objChatFontConfigurer_Exist;
        
        l_objChatFontConfigurer.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent evt) 
            {
                setFont((Font) evt.getNewValue());
                RebuildStyles();
            }
        });
        
        l_objChatFontConfigurer.fireUpdate();    
        
        final Prefs l_objGlobalPrefs = Prefs.getGlobalPrefs(); //l_objGameModule.getPrefs();
        
        FontConfigurer l_objButtonsFontConfigurer = null;
        FontConfigurer l_objButtonsFontConfigurer_Exist = (FontConfigurer)l_objGlobalPrefs.getOption("ButtonFont");

        if (l_objButtonsFontConfigurer_Exist == null)
        {
            l_objButtonsFontConfigurer = new FontConfigurer("ButtonFont", "Chatter's dice buttons font: "); //$NON-NLS-1$ //$NON-NLS-2$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objButtonsFontConfigurer); //$NON-NLS-1$
        }
        else
            l_objButtonsFontConfigurer = l_objButtonsFontConfigurer_Exist;
        
        l_objButtonsFontConfigurer.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent evt) 
            {
                SetButtonsFonts((Font) evt.getNewValue());
            }
        });
        
        l_objButtonsFontConfigurer.fireUpdate();
        

        ColorConfigurer l_objGameMsgColor = null;
        ColorConfigurer l_objGameMsgColor_Exist = (ColorConfigurer)l_objGlobalPrefs.getOption(GAME_MSG_COLOR);
        
        if (l_objGameMsgColor_Exist == null)
        {
            l_objGameMsgColor = new ColorConfigurer(GAME_MSG_COLOR, Resources.getString("Chatter.game_messages_preference"), Color.magenta); //$NON-NLS-1$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objGameMsgColor); //$NON-NLS-1$
        }
        else
            l_objGameMsgColor = l_objGameMsgColor_Exist;
        
        m_clrGameMsg = (Color) l_objGlobalPrefs.getValue(GAME_MSG_COLOR);
        
        l_objGameMsgColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrGameMsg = (Color) e.getNewValue();
            }
        });

        ColorConfigurer l_objSystemMsgColor = null;
        ColorConfigurer l_objSystemMsgColor_Exist = (ColorConfigurer)l_objGlobalPrefs.getOption(SYS_MSG_COLOR);

        if (l_objSystemMsgColor_Exist == null)
        {
            l_objSystemMsgColor = new ColorConfigurer(SYS_MSG_COLOR, Resources.getString("Chatter.system_message_preference"), new Color(160, 160, 160)); //$NON-NLS-1$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objSystemMsgColor); //$NON-NLS-1$
        }
        else
            l_objSystemMsgColor = l_objSystemMsgColor_Exist;        
        
        m_clrSystemMsg = (Color) l_objGlobalPrefs.getValue(SYS_MSG_COLOR);
        
        l_objSystemMsgColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrSystemMsg = (Color) e.getNewValue();
            }
        });

        ColorConfigurer l_objMyChatColor = null;
        ColorConfigurer l_objMyChatColor_Exist = (ColorConfigurer)l_objGlobalPrefs.getOption(MY_CHAT_COLOR);

        if (l_objMyChatColor_Exist == null)
        {
            l_objMyChatColor = new ColorConfigurer(MY_CHAT_COLOR, Resources.getString("Chatter.my_text_preference"), Color.gray); //$NON-NLS-1$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objMyChatColor); //$NON-NLS-1$
        }
        else
            l_objMyChatColor = l_objMyChatColor_Exist;

        m_crlMyChatMsg = (Color) l_objGlobalPrefs.getValue(MY_CHAT_COLOR);
        
        l_objMyChatColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_crlMyChatMsg = (Color) e.getNewValue();
            }
        });

        ColorConfigurer l_objOtherChatColor = null;
        ColorConfigurer l_objOtherChatColor_Exist = (ColorConfigurer)l_objGlobalPrefs.getOption(OTHER_CHAT_COLOR);
        
        if (l_objOtherChatColor_Exist == null)
        {
            l_objOtherChatColor = new ColorConfigurer(OTHER_CHAT_COLOR, Resources.getString("Chatter.other_text_preference"), Color.black); //$NON-NLS-1$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objOtherChatColor); //$NON-NLS-1$
        }
        else
            l_objOtherChatColor = l_objOtherChatColor_Exist;
        
        m_clrOtherChatMsg = (Color) l_objGlobalPrefs.getValue(OTHER_CHAT_COLOR);
        
        l_objOtherChatColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrOtherChatMsg = (Color) e.getNewValue();
            }
        });
        
        BooleanConfigurer l_objUseDiceImagesOption = null;
        BooleanConfigurer l_objUseDiceImagesOption_Exist = (BooleanConfigurer)l_objGlobalPrefs.getOption(USE_DICE_IMAGES);
        
        if (l_objUseDiceImagesOption_Exist == null)
        {
            l_objUseDiceImagesOption = new BooleanConfigurer(USE_DICE_IMAGES, "Use images for dice rolls", Boolean.TRUE);  //$NON-NLS-1$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objUseDiceImagesOption); //$NON-NLS-1$
        }
        else
            l_objUseDiceImagesOption = l_objUseDiceImagesOption_Exist;
        
        m_bUseDiceImages = (Boolean) (l_objGlobalPrefs.getValue(USE_DICE_IMAGES));
        
        l_objUseDiceImagesOption.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_bUseDiceImages = (Boolean) e.getNewValue();
            }
        });
        
        // TODO Resources ???
        //final ColorConfigurer l_objColoredDiceColor = new ColorConfigurer(COLORED_DICE_COLOR, Resources.getString("Chatter.colored_dice_color"), Color.YELLOW); //$NON-NLS-1$
        ColorConfigurer l_objColoredDiceColor = null;
        ColorConfigurer l_objColoredDiceColor_Exist = (ColorConfigurer)l_objGlobalPrefs.getOption(COLORED_DICE_COLOR);
        
        if (l_objColoredDiceColor_Exist == null)
        {
            l_objColoredDiceColor = new ColorConfigurer(COLORED_DICE_COLOR, "Colored die color:  ", Color.YELLOW); //$NON-NLS-1$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objColoredDiceColor); //$NON-NLS-1$
        }
        else
            l_objColoredDiceColor = l_objColoredDiceColor_Exist;
        
        l_objColoredDiceColor.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrColoredDiceColor = (Color) e.getNewValue();
                RebuildColoredDiceFaces();
            }
        });

        l_objColoredDiceColor.fireUpdate();
        
        // TODO Resources ???
        //final ColorConfigurer l_objColoredDiceColor = new ColorConfigurer(SINGLE_DIE_COLOR, Resources.getString("Chatter.single_die_color"), Color.RED); //$NON-NLS-1$
        ColorConfigurer l_objColoredDieColor = null;
        ColorConfigurer l_objColoredDieColor_Exist = (ColorConfigurer)l_objGlobalPrefs.getOption(SINGLE_DIE_COLOR);
        
        if (l_objColoredDieColor_Exist == null)
        {
            l_objColoredDieColor = new ColorConfigurer(SINGLE_DIE_COLOR, "Single die color:  ", Color.RED); //$NON-NLS-1$
            l_objGlobalPrefs.addOption(Resources.getString("Chatter.chat_window"), l_objColoredDieColor); //$NON-NLS-1$
        }
        else
            l_objColoredDieColor = l_objColoredDieColor_Exist;
        
        l_objGlobalPrefs.getOption(SINGLE_DIE_COLOR).addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_clrSingleDieColor = (Color) e.getNewValue();
                RebuildSingleDieFaces();
            }
        });
        
        l_objColoredDieColor.fireUpdate();
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
    
    /**
     * Determines the color with which to draw a given line of text
     *
     * @return the Color to draw
     */
    @Override
    protected Color getColor(Element elem) 
    {
        return Color.black;
    }

  public static void main(String[] args) 
  {
    ASLChatter chat = new ASLChatter();
    JFrame f = new JFrame();
    f.add(chat);
    f.pack();
    f.setVisible(true);
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
