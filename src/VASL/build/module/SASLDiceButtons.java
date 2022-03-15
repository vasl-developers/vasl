package VASL.build.module;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.Configurable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.GlobalMap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Iterator;


public class SASLDiceButtons  extends AbstractConfigurable {
   ASLChatter saslchat = null;

   public SASLDiceButtons(){
       for (Iterator<Buildable> e = GameModule.getGameModule().getBuildables().iterator();
            e.hasNext();) {
           Object o = e.next();
           if (o instanceof ASLChatter) {
               saslchat = (ASLChatter) o;
               break;
           }
       }
       JPanel saslp = saslchat.getButtonPanel();
       JLabel emptylabel = new JLabel();
       emptylabel.setText("                   ");
       emptylabel.setBorder(null);
       saslp.add(emptylabel);
       JButton m_btnEP = saslchat.CreateChatterDiceButton("", "Enemy Panic", "Enemy Panic DR", KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "EP");
       m_btnEP.setBackground(Color.orange);
       JButton m_btnEA = saslchat.CreateChatterDiceButton("", "Enemy Activation", "Enemy Activation dr", KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), false, "EA");
       m_btnEA.setBackground(Color.orange);
       JButton m_btnRE = saslchat.CreateChatterDiceButton("", "Friendly & Enemy RE", "Friendly & Enemy RE DR", KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "RE");
       m_btnRE.setBackground(Color.orange);
       JButton m_btnCMD = saslchat.CreateChatterDiceButton("", "Friendly CMD", "Friendly CMD DR", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK), true, "CMD");
       m_btnCMD.setBackground(Color.orange);

       GridBagConstraints l_objGridBagConstraints = new GridBagConstraints();
       l_objGridBagConstraints.fill = GridBagConstraints.BOTH;
       l_objGridBagConstraints.weightx = 0.5;
       l_objGridBagConstraints.weighty = 0.5;
       l_objGridBagConstraints.insets = new Insets(0, 1, 0, 1);
       saslp.add(m_btnEP, l_objGridBagConstraints );
       saslp.add(m_btnEA, l_objGridBagConstraints);
       saslp.add(m_btnRE, l_objGridBagConstraints);
       saslp.add(m_btnCMD, l_objGridBagConstraints);

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
