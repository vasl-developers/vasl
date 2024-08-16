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

import VASL.environment.*;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.map.MapShader;
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
import java.util.Iterator;
import java.util.StringTokenizer;

public class ScenInfo extends AbstractBuildable implements GameComponent, CommandEncoder {

  private JTextField AxisELR, AxisSAN, AlliedELR, AlliedSAN, AxisBoobyTrap, AlliedBoobyTrap;
  private TextConfigurer notes;
  private Hashtable privateNotes = new Hashtable();
  private JComboBox movesFirst;
  private JButton launch;
  private JButton nextTurn;
  private JFrame frame;

  private TurnMarker turn;

  private KeyStrokeListener keyListener;
  private AbstractAction launchAction;

  private int axisSAN, alliedSAN;
  private String axisBooby, alliedBooby;

  private boolean nightShade;
  private boolean dustShade;
  private DustLevel dustLevel = DustLevel.NONE;
    private boolean fogShade;
  private FogLevel fogLevel = FogLevel.NONE;
  private boolean heathShade;
  private HeatHazeLevel heathLevel = HeatHazeLevel.NONE;
  private boolean lvShade;
  private LVLevel lvLevel = LVLevel.NONE;
  private boolean sunbShade;
  private SunBlindnessLevel sunbLevel = SunBlindnessLevel.NONE;
  private String shaderLevel;
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
  // retrieves values from tokenized String (either SavedGame file or from (getState)
  public void setState(String in) {
    reset();

    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(in, '\t');
    String mf = "Axis",pl = "Axis",c = "1",xELR = "?",lELR = "?",xSAN = "?",lSAN = "?", xBooby = "?", lBooby = "?",
            nightshd ="false", dustshd = "false", fogshd = "false", heathshd = "false", lvshd = "false", sunbshd = "false",
            nightlevel = "NONE", dustlevel = "NONE", foglevel = "NONE", heathlevel = "NONE", lvlevel = "NONE", sunblevel = "NONE";
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
      nightshd = st.nextToken();
      dustshd = st.nextToken();
      fogshd = st.nextToken();
      heathshd = st.nextToken();
      lvshd = st.nextToken();
      sunbshd = st.nextToken();
      nightlevel = st.nextToken();
      dustlevel = st.nextToken();
      foglevel = st.nextToken();
      heathlevel = st.nextToken();
      lvlevel = st.nextToken();
      sunblevel = st.nextToken();
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

    turn.movesFirst = mf;
    movesFirst.setSelectedItem(mf + " moves first");
    turn.player = pl;
    turn.current = Integer.parseInt(c);
    AxisELR.setText(xELR);
    AlliedELR.setText(lELR);
    AxisSAN.setText(xSAN);
    AlliedSAN.setText(lSAN);
    AxisBoobyTrap.setText(xBooby);
    AlliedBoobyTrap.setText(lBooby);

    turn.repaint();

    axisSAN = getSAN(xSAN);
    alliedSAN = getSAN(lSAN);
    axisBooby = getBooby(xBooby);
    alliedBooby = getBooby(lBooby);
    nightShade = getShade(nightshd);
    dustShade = getShade(dustshd);
    fogShade = getShade(fogshd);
    heathShade = getShade(heathshd);
    lvShade = getShade(lvshd);
    sunbShade = getShade((sunbshd));
    dustLevel = DustLevel.getDustLevel(dustlevel);
    fogLevel = FogLevel.getFogLevel(foglevel);
    heathLevel = HeatHazeLevel.getHeatHLevel(heathlevel);
    lvLevel = LVLevel.getLVLevel(lvlevel);
    sunbLevel = SunBlindnessLevel.getSunBLevel(sunblevel);

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
    // persistence code from Zoltan
    Iterator<ASLMap> mapIterator = GameModule.getGameModule().getComponentsOf(ASLMap.class).iterator();
    if (mapIterator.hasNext()) {
      ASLMap map = mapIterator.next();
      this.setShaderVisibility(map, ASLNightMapShader.class, nightShade);
      this.setShaderVisibility(map, ASLDTODustMapShader.class, dustShade);
      this.setShaderVisibility(map, ASLFogMapShader.class, fogShade);
      this.setShaderVisibility(map, ASLHeatHazeMapShader.class, heathShade);
      this.setShaderVisibility(map, ASLLVMapShader.class, lvShade);
      this.setShaderVisibility(map, ASLSunBlindnessMapShader.class,  sunbShade);
      this.setShaderState(map, ASLNightMapShader.class, nightShade, nightlevel);
      this.setShaderState(map, ASLDTODustMapShader.class, dustShade, dustlevel);
      this.setShaderState(map, ASLFogMapShader.class, fogShade, foglevel);
      this.setShaderState(map, ASLHeatHazeMapShader.class, heathShade, heathlevel);
      this.setShaderState(map, ASLLVMapShader.class, lvShade, lvlevel);
      this.setShaderState(map, ASLSunBlindnessMapShader.class,  sunbShade, sunblevel);
    }
  }

  // persistence code from Zoltan
  private <T extends MapShader> void setShaderVisibility(ASLMap map, Class<T> shader, boolean value) {
    Iterator<T> shaderIterator = map.getComponentsOf(shader).iterator();
    if (!shaderIterator.hasNext()) {return;}
    MapShader shaderObj = shaderIterator.next();
    shaderObj.setShadingVisibility(value);
  }
  private <T extends VisibilityQueryable> boolean getShaderVisibility(ASLMap map, Class<T> shader) {
    Iterator<T> shaderIterator = map.getComponentsOf(shader).iterator();
    if (!shaderIterator.hasNext()) {return false;}
    VisibilityQueryable shaderObj = shaderIterator.next();
    Boolean visibleShader = shaderObj.getShadingVisible();
    if (!shaderIterator.equals(ASLNightMapShader.class)){
      shaderLevel = (visibleShader == true ? shaderObj.getShadingLevel() : "NONE");
    }
    return visibleShader;
  }
  private <T extends VisibilityQueryable> void setShaderState(ASLMap map, Class<T> shader, boolean value, String state) {
    Iterator<T> shaderIterator = map.getComponentsOf(shader).iterator();
    if (!shaderIterator.hasNext()) {return;}
    VisibilityQueryable shaderObj = shaderIterator.next();
    if (value){
        shaderObj.setStateFromSavedGame(value, state);
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
  private Boolean getShade(String s){
    Boolean gs = false;
    if (s.trim().equals("true")){
      gs = true;
    }
    return gs;
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
  // gets values added via scen info window or via Shader class/buttons
  // puts them into tokenized String
  // returns the tokenized String
  public String getState() {
    axisSAN = getSAN(AxisSAN.getText());
    alliedSAN = getSAN(AlliedSAN.getText());
    axisBooby = getBooby(AxisBoobyTrap.getText());
    alliedBooby = getBooby(AlliedBoobyTrap.getText());
    String nsstring = "false", dsstring = "false", fsstring = "false", hsstring = "false", lsstring = "false", sbsstring = "false";
    String nlstring = "", dlstring = "", flstring = "", hlstring = "", llstring = "", sblstring = "";
    Iterator<ASLMap> mapIterator = GameModule.getGameModule().getComponentsOf(ASLMap.class).iterator();
    if (mapIterator.hasNext()) {
      ASLMap map = mapIterator.next();
      nightShade = this.getShaderVisibility(map, ASLNightMapShader.class);
      nsstring = (nightShade == true ? "true" : "false");
      nlstring = "NONE";
      dustShade = this.getShaderVisibility(map, ASLDTODustMapShader.class);
      dsstring = (dustShade == true ? "true" : "false");
      dlstring = shaderLevel; //dustLevel.name();
      fogShade = this.getShaderVisibility(map, ASLFogMapShader.class);
      fsstring = (fogShade == true ? "true" : "false");
      flstring = shaderLevel; //fogLevel.name();
      heathShade = this.getShaderVisibility(map, ASLHeatHazeMapShader.class);
      hsstring = (heathShade == true ? "true" : "false");
      hlstring = shaderLevel; //heathLevel.name();
      lvShade = this.getShaderVisibility(map, ASLLVMapShader.class);
      lsstring = (lvShade == true ? "true" : "false");
      llstring = shaderLevel; //lvLevel.name();
      sunbShade = this.getShaderVisibility(map, ASLSunBlindnessMapShader.class);
      sbsstring = (sunbShade == true ? "true" : "false");
      sblstring = shaderLevel; //sunbLevel.name();
    }
    SequenceEncoder se = new SequenceEncoder('\t');
    se.append(turn.movesFirst).append(turn.player).append("" + turn.current)
        .append(AxisELR.getText()).append(AxisSAN.getText())
        .append(AlliedELR.getText()).append(AlliedSAN.getText()).append(AxisBoobyTrap.getText()).append(AlliedBoobyTrap.getText()).append(nsstring)
            .append(dsstring).append(fsstring).append(hsstring).append(lsstring).append(sbsstring).append(nlstring).append(dlstring).append(flstring)
            .append(hlstring).append(llstring).append(sblstring);
    se.append(notes.getValueString());
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
