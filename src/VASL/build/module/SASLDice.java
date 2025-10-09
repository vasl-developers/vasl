package VASL.build.module;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.documentation.HelpFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class SASLDice extends AbstractConfigurable {
    ASLChatter chatter = null;

    public SASLDice() {
        for (Object o : GameModule.getGameModule().getBuildables()) {
            if (o instanceof ASLChatter) {
                chatter = (ASLChatter) o;
                break;
            }
        }

        if (chatter != null && chatter.getShowSaslDice()) {
            JPanel buttonPanel = chatter.getButtonPanel();
            JPanel saslButtonPanel = chatter.getButtonPanelSasl();

            GridBagConstraints l_objGridBagConstraints = new GridBagConstraints();
            l_objGridBagConstraints.fill = GridBagConstraints.BOTH;
            l_objGridBagConstraints.weightx = 0.5;
            l_objGridBagConstraints.weighty = 0.5;
            l_objGridBagConstraints.insets = new Insets(0, 1, 0, 1);

            if (buttonPanel != null) {
                JButton m_btnCMD = chatter.CreateChatterDiceButton("", "CMD", "FRIENDLY CMD DR", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "CMD");
                m_btnCMD.setBackground(new Color(82, 165, 82));

                JButton m_btnFRE = chatter.CreateChatterDiceButton("", "RE", "FRIENDLY RE DR", KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "RE");
                m_btnFRE.setBackground(new Color(82, 165, 82));

                buttonPanel.add(new JToolBar.Separator(), l_objGridBagConstraints);
                buttonPanel.add(m_btnCMD, l_objGridBagConstraints);
                buttonPanel.add(m_btnFRE, l_objGridBagConstraints);
            }

            if (saslButtonPanel != null) {
                JButton m_btnStats = chatter.createStatsDiceButtonSasl(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.ALT_DOWN_MASK));
                m_btnStats.setBackground(new Color(165, 165, 165));

                JButton m_btnDR = chatter.CreateChatterDiceButton("DRs.gif", "DR", "ENEMY DR", KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.ALT_DOWN_MASK), true, "ENEMY Other");
                m_btnDR.setBackground(new Color(165, 165, 165));

                JButton m_btnIFT = chatter.CreateChatterDiceButton("", "IFT", "ENEMY IFT attack DR", KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY IFT");
                m_btnIFT.setBackground(new Color(165, 165, 165));

                JButton m_btnTH = chatter.CreateChatterDiceButton("", "TH", "ENEMY To Hit DR", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY TH");
                m_btnTH.setBackground(new Color(165, 165, 165));

                JButton m_btnTK = chatter.CreateChatterDiceButton("", "TK", "ENEMY To Kill DR", KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY TK");
                m_btnTK.setBackground(new Color(165, 165, 165));

                JButton m_btnMC = chatter.CreateChatterDiceButton("", "MC", "ENEMY Morale Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY MC");
                m_btnMC.setBackground(new Color(165, 165, 165));

                JButton m_btnTC = chatter.CreateChatterDiceButton("", "TC", "ENEMY Task Check DR", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY TC");
                m_btnTC.setBackground(new Color(165, 165, 165));

                JButton m_btnRally = chatter.CreateChatterDiceButton("", "Rally", "ENEMY Rally DR", KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY Rally");
                m_btnRally.setBackground(new Color(165, 165, 165));

                JButton m_btnCC = chatter.CreateChatterDiceButton("", "CC", "ENEMY Close Combat DR", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY CC");
                m_btnCC.setBackground(new Color(165, 165, 165));

                JButton m_btndr = chatter.CreateChatterDiceButton("dr.gif", "dr", "ENEMY dr", KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.ALT_DOWN_MASK), false, "ENEMY Other");
                m_btndr.setBackground(new Color(165, 165, 165));

                JButton m_btnSA = chatter.CreateChatterDiceButton("", "SA", "ENEMY Sniper Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "ENEMY SA");
                m_btnSA.setBackground(new Color(165, 165, 165));

                JButton m_btnRS = chatter.CreateChatterDiceButton("", "RS", "ENEMY Random Selection dr", KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "ENEMY RS");
                m_btnRS.setBackground(new Color(165, 165, 165));

                JButton m_btnEA = chatter.CreateChatterDiceButton("", "AC", "ENEMY AC dr", KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "ENEMY AC");
                m_btnEA.setBackground(new Color(165, 165, 165));

                JButton m_btnEP = chatter.CreateChatterDiceButton("", "ACT", "ENEMY Action DR", KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY Action");
                m_btnEP.setBackground(new Color(165, 165, 165));

                JButton m_btnRE = chatter.CreateChatterDiceButton("", "RE", "ENEMY RE DR", KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "ENEMY RE");
                m_btnRE.setBackground(new Color(165, 165, 165));

                saslButtonPanel.add(m_btnStats, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnDR, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnIFT, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnTH, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnTK, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnMC, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnTC, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnRally, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnCC, l_objGridBagConstraints);
                saslButtonPanel.add(m_btndr, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnSA, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnRS, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnEA, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnEP, l_objGridBagConstraints);
                saslButtonPanel.add(m_btnRE, l_objGridBagConstraints);
            }
        }
    }

    public Class<?>[] getAttributeTypes() {
        return new Class<?>[] {String.class};
    }

    @Override
    public String[] getAttributeNames() {
        return new String[] {"Name"};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[] {"Name"};
    }

    @Override
    public String getAttributeValueString(String key) {

        return "SASL Dice Buttons";
    }

    @Override
    public void setAttribute(String key, Object value) {
    }
    @Override
    public void addTo(Buildable parent) {

    }
    @Override
    public void removeFrom(Buildable parent) {

    }
    @Override
    public HelpFile getHelpFile() {
        return null;
    }
    @Override
    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }
}
