/*
 * $Id$
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

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.configure.TextConfigurer;
import VASSAL.tools.KeyStrokeListener;
import VASSAL.tools.SequenceEncoder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.StringTokenizer;

public class ScenInfo extends AbstractBuildable implements GameComponent, CommandEncoder {

  private JTextField AxisELR, AxisSAN, AlliedELR, AlliedSAN, AxisBoobyTrap, AlliedBoobyTrap;
  public JComboBox EC, Weather, Wind, WindDir, Night, NVR;
  private TextConfigurer notes;
  private Hashtable privateNotes = new Hashtable();
  private JComboBox movesFirst;
  private JButton launch;
  private JButton nextTurn;
  private JFrame frame;

  private TurnMarker turn;

  private KeyStrokeListener keyListener;
  private AbstractAction launchAction;

  private int axisSAN, alliedSAN, ecvalue, nvrvalue;
  private String axisBooby, alliedBooby, weather, wind, windir, night;
  private TextConfigurer myPrivate;

  public ScenInfo() {
    frame = new JFrame("Scenario Information");
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    launch = new JButton("Info");
    launch.setAlignmentY(0.0F);
    launch.setToolTipText("Scenario Info Window [F7]");
    launchAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        frame.setVisible(!frame.isShowing());
      }
    };
    launch.addActionListener(launchAction);
    launchAction.setEnabled(false);
    launch.setEnabled(false);

    keyListener = new KeyStrokeListener(launchAction);
    keyListener.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0, false));
    // Side variables
    turn = new TurnMarker("Axis", "Axis", 1);
    AxisELR = new JTextField(" ? ");
    AxisELR.setMaximumSize(AxisELR.getPreferredSize());
    AlliedELR = new JTextField(" ? ");
    AlliedELR.setMaximumSize(AlliedELR.getPreferredSize());
    AxisSAN = new JTextField(" ? ");
    AxisSAN.setMaximumSize(AxisSAN.getPreferredSize());
    AlliedSAN = new JTextField(" ? ");
    AlliedSAN.setMaximumSize(AlliedSAN.getPreferredSize());
    AxisBoobyTrap = new JTextField(" ? ");
    AxisBoobyTrap.setMaximumSize(AxisBoobyTrap.getPreferredSize());
    AlliedBoobyTrap = new JTextField(" ? ");
    AlliedBoobyTrap.setMaximumSize(AlliedBoobyTrap.getPreferredSize());

    movesFirst = new JComboBox();
    movesFirst.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent evt) {
        turn.current = 1;
        String s = (String) movesFirst.getSelectedItem();
        int len = (s.startsWith("Ax") ? 4 : 6);
        turn.movesFirst = s.substring(0, len);
        turn.player = s.substring(0, len);
        turn.repaint();
      }
    });
    movesFirst.addItem("Axis moves first");
    movesFirst.addItem("Allied moves first");
    movesFirst.setSelectedIndex(0);

    frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

    Box b = Box.createHorizontalBox();
    b.add(movesFirst);
    b.add(turn);

    nextTurn = new JButton("Next Turn");
    nextTurn.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        turn.advance();
      }
    });
    b.add(nextTurn);
    frame.getContentPane().add(b);

    b = Box.createHorizontalBox();
    b.add(new JLabel("Axis ELR: "));
    b.add(AxisELR);
    b.add(new JLabel("Axis SAN: "));
    b.add(AxisSAN);
    frame.getContentPane().add(b);

    b = Box.createHorizontalBox();
    b.add(new JLabel("Allied ELR: "));
    b.add(AlliedELR);
    b.add(new JLabel("Allied SAN: "));
    b.add(AlliedSAN);
    frame.getContentPane().add(b);

    b = Box.createHorizontalBox();
    b.add(new JLabel("Allied Booby Trap(A/B/C): "));
    b.add(AlliedBoobyTrap);
    b.add(new JLabel("Axis Booby Trap(A/B/C): "));
    b.add(AxisBoobyTrap);
    frame.getContentPane().add(b);

    frame.getContentPane().add(Box.createRigidArea(new Dimension(0, 10))); // Use (10, 0) for horizontal

    createEnvironmentPanel();

    frame.getContentPane().add(Box.createRigidArea(new Dimension(0, 10))); // Use (10, 0) for horizontal

    notes = new TextConfigurer(null, "Notes: ");
    frame.getContentPane().add(notes.getControls());

    myPrivate = new TextConfigurer(null, "Private notes: ");
    frame.getContentPane().add(myPrivate.getControls());
    frame.getContentPane().add(notes.getControls());

    JPanel p = new JPanel();
    JButton saveButton = new JButton("Save");
    saveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        save();
        frame.setVisible(false);
      }
    });
    p.add(saveButton);
    frame.getContentPane().add(p);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        save();
        frame.setVisible(false);
      }
    });

    frame.pack();
  }

  private void save() {
    privateNotes.put(GameModule.getUserId(), myPrivate.getValue());
    GameModule.getGameModule().sendAndLog(new SetInfo(getState(), this));
  }

  public void addTo(Buildable b) {
    GameModule.getGameModule().getToolBar().add(launch);
    GameModule.getGameModule().getGameState().addGameComponent(this);
    GameModule.getGameModule().addKeyStrokeListener(keyListener);
    GameModule.getGameModule().addCommandEncoder(this);
  }

  public void setState(String in) {
    reset();

    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(in, '\t');
    String mf = "Axis",pl = "Axis",c = "1",xELR = "?",lELR = "?",xSAN = "?",lSAN = "?",xBooby = "?",lBooby = "?", we = "Clear", ec = "Moderate: 0",
            wi = "None", wd = "None", ni = "No", nv = "0";
    try {
      mf = st.nextToken();
      pl = st.nextToken();
      c = st.nextToken();
      xELR = st.nextToken();
      xSAN = st.nextToken();
      lELR = st.nextToken();
      lSAN = st.nextToken();
      xBooby = st.nextToken();
      lBooby = st.nextToken();
      we = st.nextToken();
      ec = st.nextToken();
      wi = st.nextToken();
      wd = st.nextToken();
      ni = st.nextToken();
      nv = st.nextToken();

    }
    catch (Exception e) {
    }

    // trap errors when opening games saved in older versions
    if (!xBooby.equals("?") && !xBooby.equals("A") && !xBooby.equals("B") && !xBooby.equals("C")) {
      xBooby = "?";
    }
    if (!lBooby.equals("?") && !lBooby.equals("A") && !lBooby.equals("B") && !lBooby.equals("C")) {
      lBooby = "?";
    }
    if (we == "") {we = "Clear";}
    if (ec == "") {ec = "Moderate: 0";}
    if (wi == "") {wi = "None";}
    if (wd == "") {wd = "None";}
    if (ni == "") {ni = "No";}
    if (nv == "") {nv = "0";}

    turn.movesFirst = mf;
    movesFirst.setSelectedItem(mf + " moves first");
    turn.player = pl;
    try {
        Integer currentTurn = Integer.parseInt(c);
        turn.current = currentTurn;
    } catch (NumberFormatException e) {
        turn.current = 1;
    }
    AxisELR.setText(xELR);
    AlliedELR.setText(lELR);
    AxisSAN.setText(xSAN);
    AlliedSAN.setText(lSAN);
    AxisBoobyTrap.setText(xBooby);
    AlliedBoobyTrap.setText(lBooby);
    Weather.setSelectedItem(we);
    String ecitem = makeECItem(ec);
    EC.setSelectedItem(ecitem);
    Wind.setSelectedItem(wi);
    WindDir.setSelectedItem(wd);
    Night.setSelectedItem(ni);
    NVR.setSelectedItem(nv);

    axisSAN = getSAN(xSAN);
    alliedSAN = getSAN(lSAN);
    axisBooby = getBooby(xBooby);
    alliedBooby = getBooby(lBooby);
    weather = getWeather(we);
    ecvalue = getEC(ec);
    wind = getWind(wi);
    windir = getWindDir(wd);
    night = getNight(ni);
    nvrvalue = getNVR(nv);
    turn.repaint();

    if (st.hasMoreTokens()) {
      notes.setValue(st.nextToken());
    }
    while (st.hasMoreTokens()) {
      String id = st.nextToken();
      String encodedNotes = st.nextToken();
      StringBuffer buffer = new StringBuffer();
      SequenceEncoder.Decoder st2 = new SequenceEncoder.Decoder(encodedNotes,'|');
      while (st2.hasMoreTokens()) {
        buffer.append(st2.nextToken());
        if (st2.hasMoreTokens()) {
          buffer.append('\n');
        }
      }
      if (id.equals(GameModule.getUserId())) {
        myPrivate.setValue(buffer.toString());
      }
      privateNotes.put(id,buffer.toString());
    }
  }

  public static final String COMMAND_PREFIX = "INFO\t";

  public Command decode(String command) {
    if (command.startsWith(COMMAND_PREFIX)) {
      return new SetInfo(command.substring(COMMAND_PREFIX.length()), this);
    }
    return null;
  }

  public String encode(Command c) {
    if (c instanceof SetInfo) {
      return COMMAND_PREFIX + ((SetInfo) c).getState();
    }
    else {
      return null;
    }
  }

  public int getAxisSAN() {
    return axisSAN;
  }

  public int getAlliedSAN() {
    return alliedSAN;
  }

  public int getEC(String ec) {
    int n = 0;
    if (ec.contains(":")){
      String ecstr = ec.substring(0, ec.indexOf(':'));
      switch (ecstr){
        case "Snow":
        case "Mud" :
          n = -3;
          break;
        case "Wet":
          n = -2;
          break;
        case "Moist":
          n = -1;
          break;
        case "Moderate":
          n = 0;
          break;
        case "Dry":
          n = 1;
          break;
        case "Very Dry":
          n = 2;
          break;
        default:
          n = 0;
      }
    }
    else {

      try {
        n = Integer.parseInt(ec.trim());
      }
      catch (Exception e) {
      }
    }

    return n;
  }

  public String makeECItem(String ec) {
    String eccomboitem = "";
    switch (ec){
      case "-3":
        eccomboitem = "Snow: -3";
        break;
      case "-2":
        eccomboitem = "Wet: -2";
        break;
      case "-1":
        eccomboitem = "Moist: -1";
        break;
      case "0":
        eccomboitem = "Moderate: 0";
        break;
      case "1":
        eccomboitem = "Dry: 1";
        break;
      case "2":
        eccomboitem = "Very Dry: 2";
        break;
      default:
        eccomboitem = "Moderate: 0";
    }
    return eccomboitem;
  }

  public int getNvrvalue() {
    return nvrvalue;
  }
  private int getNVR(String nv) {
    int n = 0;
    try {
      n = Integer.parseInt(nv.trim());
    }
    catch (Exception e) {
    }
    return n;
  }

  private int getSAN(String s) {
    int n = 0;
    try {
      n = Integer.parseInt(s.trim());
    }
    catch (Exception e) {
    }
    return n;
  }

  public String getAxisBooby() {
    return axisBooby;
  }

  public String getAlliedBooby() {
    return alliedBooby;
  }

  public String getWeather(String s) {return weather;}
  public String getWind(String s) {return wind;}
  public String getWindDir(String s) {return windir;}
  private String getNight(String s) {return night;}

  public String getNightvalue(){
    return night;
  }

  private String getBooby(String s) {
    String n = "";
    if (s.trim().equals("A") ){
      return "A";
    } else if (s.trim().equals("B")){
      return "B";
    } else if (s.trim().equals("C")){
      return "C";
    }
     return n;
  }

  public void setup(boolean show) {
    launch.setEnabled(show);
    launchAction.setEnabled(show);
    if (!show) {
      reset();
      frame.setVisible(false);
    }
  }

  public Command getRestoreCommand() {
    return new SetInfo(getState(), this);
  }

  public void reset() {
    notes.setValue("");
    myPrivate.setValue("");
    privateNotes.clear();
  }

  public String[] getAttributeNames() {
    return new String[0];
  }

  public void setAttribute(String name, Object value) {
  }

  public String getAttributeValueString(String name) {
    return null;
  }

  public String getState() {
    axisSAN = getSAN(AxisSAN.getText());
    alliedSAN = getSAN(AlliedSAN.getText());
    axisBooby = getBooby(AxisBoobyTrap.getText());
    alliedBooby = getBooby(AlliedBoobyTrap.getText());
    weather = getWeather(weather);
    ecvalue = getEC(EC.getSelectedItem().toString());
    wind = getWind(wind);
    windir = getWindDir(windir);
    night = getNight(night);
    nvrvalue = getNVR(NVR.getSelectedItem().toString());


    SequenceEncoder se = new SequenceEncoder('\t');
    se.append(turn.movesFirst).append(turn.player).append("" + turn.current)
        .append(AxisELR.getText()).append(AxisSAN.getText())
        .append(AlliedELR.getText()).append(AlliedSAN.getText()).append(AxisBoobyTrap.getText()).append(AlliedBoobyTrap.getText())
        .append(weather).append(Integer.toString(ecvalue)).append(wind).append(windir).append(night).append(Integer.toString(nvrvalue));
    for (Enumeration e = privateNotes.keys(); e.hasMoreElements();) {
      String id = (String) e.nextElement();
      String notes = (String) privateNotes.get(id);
      if (notes != null && notes.length() > 0) {
        SequenceEncoder se2 = new SequenceEncoder('|');
        StringTokenizer st = new StringTokenizer(notes,"\r\n");
        while (st.hasMoreTokens()) {
          se2.append(st.nextToken());
        }
        se.append(id);
        se.append(se2.getValue());
      }
    }
    return se.getValue();
  }

  private void createEnvironmentPanel() {

      // Environment Variables
      frame.getContentPane().add(Box.createRigidArea(new Dimension(0, 10))); // Use (10, 0) for horizontal
      frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

      EC = new JComboBox();
      EC.addItemListener(new ItemListener() {
          public void itemStateChanged(ItemEvent evt) {
              String s = (String) EC.getSelectedItem();
              String strvalue = s.substring(s.length() - 1);
              String seclastChar = s.substring(s.length() - 2);
              ecvalue = Integer.parseInt(strvalue);
              if (seclastChar.contains("-")) {
                ecvalue = -ecvalue;
              }
          }
      });
      EC.addItem("Snow: -3");
      EC.addItem("Mud: -3");
      EC.addItem("Wet: -2");
      EC.addItem("Moist: -1");
      EC.addItem("Moderate: 0");
      EC.addItem("Dry: 1");
      EC.addItem("Very Dry: 2");
      EC.setSelectedIndex(0);

      Weather = new JComboBox();
      Weather.addItemListener(new ItemListener() {
          public void itemStateChanged(ItemEvent evt) {
              weather = (String) Weather.getSelectedItem();
          }
      });
      Weather.addItem("Clear");
      Weather.addItem("Clear & Gusty");
      Weather.addItem("Overcast");
      Weather.addItem("Fog & Mist");
      Weather.addItem("Mud");
      Weather.addItem("Mun & Overcast");
      Weather.addItem("Snow");
      Weather.addItem("Heavy Rain");
      Weather.addItem("Mud & Heavy Rain");
      Weather.setSelectedIndex(0);

      Box b = Box.createHorizontalBox();
      b.add(new JLabel("Weather: "));
      b.add(Weather);
      b.add(new JLabel("EC: "));
      b.add(EC);
      frame.getContentPane().add(b);

      frame.getContentPane().add(Box.createRigidArea(new Dimension(0, 10))); // Use (10, 0) for horizontal

      Wind = new JComboBox();
      Wind.addItemListener(new ItemListener() {
          public void itemStateChanged(ItemEvent evt) {
              wind = (String) Wind.getSelectedItem();
          }
      });
      Wind.addItem("None");
      Wind.addItem("Mild Breeze");
      Wind.addItem("Heavy Winds");
      Wind.setSelectedIndex(0);

      WindDir = new JComboBox();
      WindDir.addItemListener(new ItemListener() {
          public void itemStateChanged(ItemEvent evt) {
              windir = (String) WindDir.getSelectedItem();
          }
      });
      WindDir.addItem("1");
      WindDir.addItem("2");
      WindDir.addItem("3");
      WindDir.addItem("4");
      WindDir.addItem("5");
      WindDir.addItem("6");
      WindDir.setSelectedIndex(0);

      b = Box.createHorizontalBox();
      b.add(new JLabel("Wind: "));
      b.add(Wind);
      b.add(new JLabel("Wind Dir: "));
      b.add(WindDir);
      frame.getContentPane().add(b);

      frame.getContentPane().add(Box.createRigidArea(new Dimension(0, 10))); // Use (10, 0) for horizontal

      Night = new JComboBox();
      Night.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent evt) {
            night = (String) Night.getSelectedItem();
        }
      });
      Night.addItem("No");
      Night.addItem("Yes");
      Night.setSelectedIndex(0);

      NVR = new JComboBox();
      NVR.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent evt) {
            String s = (String) NVR.getSelectedItem();
            nvrvalue = Integer.parseInt(s);
        }
      });
      NVR.addItem("0");
      NVR.addItem("1");
      NVR.addItem("2");
      NVR.addItem("3");
      NVR.addItem("4");
      NVR.addItem("5");
      NVR.addItem("6");
      NVR.setSelectedIndex(0);

      b = Box.createHorizontalBox();
      b.add(new JLabel("Night: "));
      b.add(Night);
      b.add(new JLabel("NVR: "));
      b.add(NVR);
      frame.getContentPane().add(b);

  }

  public static class SetInfo extends Command {
    private ScenInfo info;
    private String oldState;
    private String newState;

    public SetInfo(String value, ScenInfo info) {
      newState = value;
      oldState = info.getState();
      this.info = info;
    }

    public String getState() {
      return newState;
    }

    protected void executeCommand() {
      info.setState(newState);
    }

    protected Command myUndoCommand() {
      return new SetInfo(oldState, info);
    }
  }

  class AppendNotes extends Command {
    private String newLine;

    AppendNotes(String s) {
      newLine = s;
    }

    protected void executeCommand() {
      notes.setValue((String) notes.getValue() + '\n' + newLine);
    }

    protected Command myUndoCommand() {
      return null;
    }
  }
}

class TurnMarker extends Canvas {
  int current;
  String player, movesFirst;

  TurnMarker(String f, String p, int c) {
    movesFirst = f;
    player = p;
    current = c;
  }

  public void paint(Graphics g) {
    FontMetrics fm;
    g.setColor(Color.white);
    g.fillRect(0, 0, getSize().width - 1, getSize().height - 1);
    g.setColor(Color.black);
    g.drawRect(0, 0, getSize().width - 1, getSize().height - 1);
    fm = g.getFontMetrics();
    g.drawString(player, getSize().width / 2 - fm.stringWidth(player) / 2,
                 getSize().height - 2);
    g.setFont(new Font("TimesRoman", Font.BOLD, 18));
    fm = g.getFontMetrics();
    g.drawString("" + current,
                 getSize().width / 2 - fm.stringWidth("" + current) / 2,
                 getSize().height / 2 + fm.getAscent() / 2);
  }

  public void advance() {
    current += (movesFirst.equals(player) ? 0 : 1);
    player = (player.equals("Axis") ? "Allied" : "Axis");
    repaint();
  }

  public Dimension getPreferredSize() {
    return (new Dimension(48, 48));
  }
}
