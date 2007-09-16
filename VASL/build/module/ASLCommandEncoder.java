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

import VASL.counters.*;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.command.AddPiece;
import VASSAL.command.Command;
import VASSAL.configure.ColorConfigurer;
import VASSAL.counters.*;
import VASSAL.tools.SequenceEncoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;

public class ASLCommandEncoder extends VASSAL.build.module.BasicCommandEncoder implements ColorTable {
  public static VASL.counters.CounterNames names = new VASL.counters.CounterNames();
  private Map map;
  private boolean promptForColors = true;

  public void addTo(Buildable b) {
    super.addTo(b);
    BasicPiece.setHighlighter(new ASLHighlighter());
/*
	   // Klaus' colors
	   initColor("ge","German",new Color(184,232,255));
	   initColor("ru","Russian",new Color(240,160,0));
	   initColor("am","American",new Color(207,255,43));
	   initColor("br","British",new Color(255,232,184));
	   initColor("ja","Japanese",new Color(255,232,54));
	   initColor("fr","French",new Color(125,255,255));
	   initColor("it","Italian",new Color(158,158,158));
	   initColor("ax","Axis Minor",new Color(217,255,217));
	   initColor("al","Allied Minor",new Color(102,255,54));
	*/
// VASL 5.0 colors
    initColor("ge", "German", new Color(145,205,245));
    initColor("fi", "Finnish", new Color(206, 211, 211));
    initColor("ru", "Russian", new Color(214,141,26));
    initColor("am", "American", new Color(205,240,0));
    initColor("br", "British", new Color(229, 206, 160));
    initColor("ja", "Japanese", new Color(255, 219, 0));
    initColor("fr", "French", new Color(65,165,255));
    initColor("it", "Italian", new Color(166, 173, 178));
    initColor("ax", "Axis Minor", new Color(29, 226, 86));
    initColor("al", "Allied Minor", new Color(130, 237, 189));
  }

  public void build(Element e) {
    super.build(e);
    promptForColors = !"true".equals(e.getAttribute("noColorPreferences"));
  }

  public Element getBuildElement(Document doc) {
    Element el = super.getBuildElement(doc);
    el.setAttribute("noColorPreferences", promptForColors ? "" : "true");
    return el;
  }

  public Command decode(String command) {
    Command c = super.decode(command);
    if (c == null) {
      c = decodeBackwardCompatible(command);
    }
    return c;
  }

  public Decorator createDecorator(String type, GamePiece inner) {
    if (type.startsWith(ColoredBox.ID)) {
      return new ColoredBox(type, inner);
    }
    else if (type.startsWith(Concealable.ID)) {
      return new Concealable(type, inner);
    }
    else if (type.startsWith(Concealment.ID)) {
      return new Concealment(type, inner);
    }
    else if (type.startsWith(TextInfo.ID)) {
      return new TextInfo(type, inner);
    }
    else if (type.startsWith(Turreted.ID)) {
      return new Turreted(type, inner);
    }
    else if (type.startsWith(MarkMoved.ID)) {
      return new MarkMoved(type, inner);
    }
    else if (type.startsWith(PlaceDM.ID)) {
      return new PlaceDM(type, inner);
    }
    else if (type.startsWith(Translate.ID)) {
      return new ASLTranslate(type,inner);
    }
    else {
      return super.createDecorator(type, inner);
    }
  }

  public Command decodeBackwardCompatible(String command) {
    GamePiece c = null;
    if (command.startsWith("count"))
      c = convertCounter(command);
    else if (command.startsWith("lab"))
      c = convertLabeled(command);
    else if (command.startsWith("rot"))
      c = convertRotator(command);
    else if (command.startsWith("unit"))
      c = convertUnit(command);
    else if (command.startsWith("gun"))
      c = convertGun(command);
    else if (command.startsWith("veh"))
      c = convertVehicle(command);
    else
      return null;

    Command comm = new AddOldPiece(c, c.getState());
    if (c.getMap() != null) {
      Point p = c.getPosition();
      p.translate(c.getMap().getEdgeBuffer().width,
                  c.getMap().getEdgeBuffer().height);
      c.setPosition(p);
    }
    return comm;
  }

  public GamePiece convertCounter(String spec) {
    CounterInfo info = new CounterInfo(spec);
    return hideable(flippable(createBasic(info), info), info);
  }

  public GamePiece createBasic(CounterInfo info) {
    GamePiece p = new BasicPiece(BasicPiece.ID + "K;D;" + info.front + ';' + names.nameOf(info.front));
    if (info.immobile) {
      p = new Marker(Marker.ID + ASLProperties.LOCATION, p);
    }
    p.setPosition(info.pos);
    if (info.pos.x == -1 && info.pos.y == -1) {
      p.setMap(null);
    }
    else {
      p.setMap(getMap());
    }
    return p;
  }

  public GamePiece flippable(GamePiece p, CounterInfo info) {
    if (info.back != null) {
      p = new Embellishment(Embellishment.ID + "F;Flip;;;;;0;0;" + info.back + ',' + names.nameOf(info.back), p);
      ((Embellishment) p).setValue(0);
    }
    return p;
  }

  public GamePiece convertLabeled(String spec) {
    LabeledInfo info = new LabeledInfo(spec);
    return hideable(createLabeled(info), info);
  }

  public GamePiece createLabeled(LabeledInfo info) {
    GamePiece p = flippable(createBasic(info), info);
    return new Labeler(Labeler.ID + 'L', p);
  }

  public GamePiece convertRotator(String spec) {
    RotatorInfo info = new RotatorInfo(spec);
    return createRotator(info);
  }

  public GamePiece createRotator(RotatorInfo info) {
    GamePiece p = createBasic(info);
    String embType = Embellishment.ID + ";;X;Rotate Right;Z;Rotate Left;0;0";
    for (int i = 1; i <= 6; ++i) {
      embType += ';' + info.front + i + "," + "+ CA = " + i;
    }
    p = new Embellishment(embType, p);
    ((Embellishment) p).setValue(info.CA);
    return hideable(p, info);
  }

  public GamePiece convertUnit(String spec) {
    UnitInfo info = new UnitInfo(spec);
    return createUnit(info);
  }

  public GamePiece createUnit(UnitInfo info) {
    GamePiece p = createBasic(info);
    boolean large = info.front.substring(0, 1).toUpperCase().
        equals(info.front.substring(0, 1));
    String size = large ? "60;60" : "48;48";
    if ("ch".equals(info.nation)) {
      p = new ColoredBox(ColoredBox.ID + "ru" + ";" + size, p);
      p = new ColoredBox(ColoredBox.ID + "ge" + ";"
                         + (large ? "48;48" : "36;36"), p);
    }
    else {
      p = new ColoredBox(ColoredBox.ID + info.nation + ";" + size, p);
    }
    if (info.back != null) {
      p = new Embellishment(Embellishment.ID + ";;F;Flip;;;0;0;"
                            + info.front + "," + names.nameOf(info.front) + ";"
                            + info.back + "," + "broken " + names.nameOf(info.front), p);
    }
    else {
      p = new Embellishment(Embellishment.ID + ";;;;;;0;0;"
                            + info.front + "," + names.nameOf(info.front), p);
    }
    if (info.front.equals("qmark")
        || info.front.equals("Qmark58")) {
      p = new Concealment(Concealment.ID, p);
    }
    else {
      p = new Labeler(Labeler.ID + 'L', p);
      if ("ch".equals(info.nation)) {
        p = new Concealable(Concealable.ID + "C;"
                            + (large ? "Qmark58" : "qmark") + ";ru;ge", p);
      }
      else {
        p = new Concealable(Concealable.ID + "C;"
                            + (large ? "Qmark58" : "qmark") + ";" + info.nation, p);
      }
    }
    p.setProperty(Properties.OBSCURED_BY, info.obscuredBy);
    p = new MarkMoved(MarkMoved.ID + (large ? "moved58" : "moved"), p);
    return hideable(p, info);
  }

  public GamePiece convertGun(String command) {
    return createGun(new GunInfo(command));
  }

  public GamePiece createGun(GunInfo info) {
    GamePiece p = createBasic(info);
    if ("ch".equals(info.nation)) {
      p = new ColoredBox(ColoredBox.ID + "ru;60;60", p);
      p = new ColoredBox(ColoredBox.ID + "ge;48;48", p);
    }
    else {
      p = new ColoredBox(ColoredBox.ID + info.nation + ";60;60", p);
    }
    p = new Embellishment(Embellishment.ID + "F;Flip;;;;;0;0;"
                          + info.front + "," + names.nameOf(info.front.substring(3)), p);
    ((Embellishment) p).setActive(true);
    String embType = Embellishment.ID + ";;X;Rotate Right;Z;Rotate Left;0;0";
    for (int i = 1; i <= 6; ++i) {
      embType += ';' + info.back + i + "," + "+ CA = " + i;
    }
    p = new Embellishment(embType, p);
    ((Embellishment) p).setActive(true);
    ((Embellishment) p).setValue(info.CA);
    p = new Embellishment(Embellishment.ID + "F;Flip;;;;;0;0;ordnance/R1X6,Malf +", p);
    ((Embellishment) p).setActive(false);
    if (info.text != null) {
      p = new TextInfo(TextInfo.ID + info.text, p);
    }
    if ("ch".equals(info.nation)) {
      p = new Concealable(Concealable.ID + "C;Qmark58;ru;ge", p);
    }
    else {
      p = new Concealable(Concealable.ID + "C;Qmark58;" + info.nation, p);
    }
    p.setProperty(Properties.OBSCURED_BY, info.obscuredBy);
    p = new Labeler(Labeler.ID + 'L', p);
    p = new MarkMoved(MarkMoved.ID + "moved58", p);
    return hideable(p, info);
  }

  public GamePiece convertVehicle(String command) {
    return createVehicle(new VehicleInfo(command));
  }

  public GamePiece createVehicle(VehicleInfo info) {
    GamePiece p = createBasic(info);
    if ("ch".equals(info.nation)) {
      p = new ColoredBox(ColoredBox.ID + "ru;60;60", p);
      p = new ColoredBox(ColoredBox.ID + "ge;45;45", p);
    }
    else {
      p = new ColoredBox(ColoredBox.ID + info.nation + ";60;60", p);
    }
    p = new Embellishment(Embellishment.ID + "F;Flip;;;;;0;0;"
                          + info.front + "," + names.nameOf(info.front.substring(3)), p);
    ((Embellishment) p).setActive(true);
    String embType = Embellishment.ID + ";;X;Rotate Right;Z;Rotate Left;0;0";
    for (int i = 1; i <= 6; ++i) {
      embType += ';' + info.back + i + "," + "+ CA = " + i;
    }
    p = new Embellishment(embType, p);
    ((Embellishment) p).setActive(true);
    ((Embellishment) p).setValue(info.CA);
    if (info.turreted) {
      if (info.OT) {
        p = new Turreted(Turreted.ID + "tcaCE;tca;SX;AZ", p);
        ((Turreted) p).setFlipped(!info.CE);
      }
      else {
        p = new Turreted(Turreted.ID + "tca;tcaCE;SX;AZ", p);
        ((Turreted) p).setFlipped(info.CE);
      }
      ((Turreted) p).setValue(info.TCA);
    }
    else if (info.OT) {
      p = new Embellishment(Embellishment.ID + "B;Button Up;;;;;0;0;ordnance/bu", p);
    }
    else {
      p = new Embellishment(Embellishment.ID + "B;Button Up;;;;;0;0;ordnance/ce", p);
    }
    p = new Embellishment(Embellishment.ID + "F;Flip;;;;;0;0;" + info.back + "0,Wreck", p);
    ((Embellishment) p).setActive(false);
    p = new TextInfo(TextInfo.ID + info.text, p);
    if ("ch".equals(info.nation)) {
      p = new Concealable(Concealable.ID + "C;Qmark58;ru;ge", p);
    }
    else {
      p = new Concealable(Concealable.ID + "C;Qmark58;" + info.nation, p);
    }
    p.setProperty(Properties.OBSCURED_BY, info.obscuredBy);
    p = new Labeler(Labeler.ID + 'L', p);
    p = new MarkMoved(MarkMoved.ID + "moved58", p);
    p = new Marker(Marker.ID + ASLProperties.HINDRANCE, p);
    p.setProperty(ASLProperties.HINDRANCE, "true");
    return hideable(p, info);
  }

  public GamePiece hideable(GamePiece p, CounterInfo info) {
    p = new Hideable("hide;H;HIP", p);
    p.setProperty(Hideable.HIDDEN_BY, info.hiddenBy);
    return p;
  }

  private void initColor(String key, String name, Color defaultColor) {
    ColorConfigurer c = new Col(key, name, defaultColor);
    GameModule.getGameModule().getPrefs().addOption(promptForColors ? "Nationality Colors" : null, c);
    if (c.getValue() == null) {
      c.setValue(defaultColor);
    }
  }

  public Color getColor(String s) {
    return (Color) GameModule.getGameModule().getPrefs().getValue(s);
/*
	if (colors == null) {
	    colors = new java.util.Hashtable();
	    colors.put("ge",new Color(106,184,255));
	    colors.put("ru",new Color(145,145,0));
	    colors.put("al",new Color(130,237,189));
	    colors.put("am",new Color(102,204,0));
	    colors.put("ax",new Color(29,226,86));
	    colors.put("br",new Color(229,206,160));
	    colors.put("fr",new Color(0,140,255));
	    colors.put("ja",new Color(255,219,0));
	    colors.put("it",new Color(166,173,178));
	}
	Color c = (Color)colors.get(s);
	return c == null ? Color.white : c;
	*/
  }

  public Map getMap() {
    if (map == null) {
      map = VASSAL.build.GameModule.getGameModule().getComponentsOf(Map.class).iterator().next();
    }
    return map;
  }

  public static class Col extends ColorConfigurer {
    private JPanel p;
    private Color defaultColor;

    public Col(String key, String name, Color c) {
      super(key, name, c);
      defaultColor = c;
    }

    public Component getControls() {
      if (p == null) {
        p = (JPanel) super.getControls();
        JButton b = new JButton("Default");
        p.add(b);
        b.addActionListener
            (new java.awt.event.ActionListener() {
              public void actionPerformed
                  (java.awt.event.ActionEvent e) {
                setValue(defaultColor);
              }
            });
      }
      return p;
    }
  }
}

/**
 * Command that adds a counter from an old (v3.02) savefile
 */
class AddOldPiece extends AddPiece {
  public AddOldPiece(GamePiece p, String state) {
    super(p, state);
  }

  protected void executeCommand() {
    getTarget().getMap().placeOrMerge(getTarget(), getTarget().getPosition());
  }
}

class CounterInfo {
  public String front, back, hiddenBy;
  public boolean flipped, immobile;
  public Point pos;

  public CounterInfo() {
  }

  public CounterInfo(String info) {
    read(info);
  }

  public void read(String info) {
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(info, '\t');
    st.nextToken();
    pos = new Point(Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()));
    String owner = st.nextToken();
    boolean invisible = owner.startsWith("HIP");
    hiddenBy = null;
    if (invisible) {
      hiddenBy = owner.substring(3);
    }
    immobile = owner.equals("none");
    String type = st.nextToken();
    int index = type.indexOf(';');
    if (index > 0) {
      front = type.substring(0, index);
      back = type.substring(index + 1);
    }
    else {
      front = type;
    }
    flipped = st.nextToken().equals("true");
  }
}

class LabeledInfo extends CounterInfo {
  public String label;

  public LabeledInfo() {
  }

  public LabeledInfo(String info) {
    read(info);
  }

  public void read(String info) {
    String counterInfo = "count\t";
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(info, '\t');
    st.nextToken();
    counterInfo += st.nextToken() + '\t' + st.nextToken() + '\t' + st.nextToken() + '\t' + st.nextToken() + '\t';
    label = st.nextToken();
    counterInfo += st.nextToken();
    super.read(info);
  }
}

class RotatorInfo extends CounterInfo {
  public int CA;

  public RotatorInfo() {
  }

  public RotatorInfo(String info) {
    read(info);
  }

  public void read(String info) {
    String counterInfo = "count\t";
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(info, '\t');
    st.nextToken();
    counterInfo += st.nextToken() + '\t' + st.nextToken() + '\t' + st.nextToken() + '\t' + st.nextToken() + '\t' + st.nextToken();
    CA = Integer.parseInt(st.nextToken()) - 1;
    super.read(counterInfo);
  }
}

class UnitInfo extends LabeledInfo {
  public String nation;
  public String obscuredBy;

  public UnitInfo() {
  }

  public UnitInfo(String info) {
    read(info);
  }

  public void read(String info) {
    String labeledInfo = "lab\t";
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(info, '\t');
    st.nextToken();
    labeledInfo += st.nextToken() + '\t' + st.nextToken() + '\t';
    String owner = st.nextToken();
    labeledInfo += owner + '\t' + st.nextToken() + '\t' + st.nextToken() + '\t';
    nation = st.nextToken();
    obscuredBy = "true".equals(st.nextToken()) ? owner : null;
    labeledInfo += st.nextToken();
    super.read(labeledInfo);
  }
}

class GunInfo extends UnitInfo {
  public String text;
  public int CA;

  public GunInfo() {
  }

  public GunInfo(String info) {
    read(info);
  }

  public void read(String info) {
    String unitInfo = "unit\t";
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(info, '\t');
    st.nextToken();
    unitInfo += st.nextToken() + '\t' + st.nextToken() + '\t'
        + st.nextToken() + '\t' + st.nextToken() + '\t' + st.nextToken() + '\t'
        + st.nextToken() + '\t' + st.nextToken() + '\t';
    CA = Integer.parseInt(st.nextToken()) - 1;
    unitInfo += st.nextToken();
    super.read(info);
    int index = back.indexOf(';');
    if (index >= 0) {
      text = back.substring(index + 1);
      back = back.substring(0, index);
    }
    front = nation + '/' + front;
    back = "ordnance/" + nation + '/' + nation + back;
  }
}

class VehicleInfo extends UnitInfo {
  public String text;
  public int CA, TCA;
  public boolean OT, turreted, CE;

  public VehicleInfo(String info) {
    read(info);
  }

  public void read(String info) {
    String unitInfo = "unit\t";
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(info, '\t');
    st.nextToken();
    unitInfo += st.nextToken() + '\t' + st.nextToken() + '\t'
        + st.nextToken() + '\t' + st.nextToken() + '\t' + st.nextToken() + '\t'
        + st.nextToken() + '\t' + st.nextToken() + '\t';
    CA = Integer.parseInt(st.nextToken()) - 1;
    TCA = Integer.parseInt(st.nextToken()) - 1;
    unitInfo += st.nextToken();
    CE = new Boolean(st.nextToken()).booleanValue();
    super.read(unitInfo);
    int index = back.indexOf(';');
    if (index >= 0) {
      text = back.substring(index + 1);
      back = back.substring(0, index);
    }
    OT = back.toLowerCase().startsWith("ot");
    turreted = back.toLowerCase().equals(back);
    front = nation + '/' + front;
    back = "ordnance/" + nation + '/' + nation + back.toLowerCase();
  }
}
