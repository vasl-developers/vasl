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
package VASL.counters;

import VASSAL.build.Configurable;
import VASSAL.build.GameModule;
import VASSAL.build.module.ObscurableOptions;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.ChangePiece;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.configure.ChooseComponentPathDialog;
import VASSAL.configure.StringConfigurer;
import VASSAL.counters.*;
import VASSAL.tools.ComponentPathBuilder;
import VASSAL.tools.SequenceEncoder;
import VASSAL.tools.imageop.Op;
import VASSAL.tools.imageop.ScaledImagePainter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

public class Concealable extends Obscurable implements EditablePiece {
  public static final String ID = "conceal;";

  private String nation;
  private String nation2;
  private String concealmentMarker;
  private Image concealedToMe;
  private Image concealedToOthers;
  private Dimension imageSize = new Dimension(-1, -1);

  public Concealable() {
    this(ID + "C;Qmark58;ge", null);
  }

  public Concealable(String type, GamePiece inner) {
    super(type, inner);
    mySetType(type);
    hideCommand = "Conceal";
  }

  public void mySetType(String in) {
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(in, ';');
    st.nextToken();
    obscureKey = st.nextToken().toUpperCase().charAt(0);
    imageName = st.nextToken();
    nation = st.nextToken();
    nation2 = st.nextToken(null);
    if ("".equals(nation2)) {
      nation2 = null;
    }
    concealmentMarker = st.nextToken(null);
    if ("".equals(concealmentMarker)) {
      concealmentMarker = null;
    }
  }

  public String myGetType() {
    SequenceEncoder se = new SequenceEncoder(';');
    se.append(obscureKey).append(imageName).append(nation);
    se.append(nation2 != null ? nation2 : "");
    se.append(concealmentMarker != null ? concealmentMarker : "");
    return ID + se.getValue();
  }

  protected void drawObscuredToMe(Graphics g, int x, int y, Component obs, double zoom) {
    loadImages(obs);
    int size = (int) (zoom * imageSize.width);

    //try {
        if (conceal != null) {
            conceal.draw(g, x - size / 2, y - size / 2, zoom, obs);
        } else {
            g.drawImage(concealedToMe, x - size / 2, y - size / 2, size, size, obs);
        }
    //}
    //catch (Exception e) {
    //}
  }

  protected void drawObscuredToOthers(Graphics g, int x, int y, Component obs, double zoom) {
    loadImages(obs);
    piece.draw(g, x, y, obs, zoom);
    int size = (int) (zoom * imageSize.width);
    //try {
      g.drawImage(concealedToOthers,
                  x - size / 2, y - size / 2, size, size, obs);
    //}
    //catch (Exception e) {
    //}
  }

  public Command myKeyEvent(KeyStroke stroke) {
    myGetKeyCommands();
    Command c = null;
    if (hide.matches(stroke)
        && getMap() != null
        && !obscuredToOthers()
        && !obscuredToMe()) {
      c = super.myKeyEvent(stroke);
      boolean concealmentExists = false;
      GamePiece outer = Decorator.getOutermost(this);
      if (getParent() != null) {
        for (int i = getParent().indexOf(outer),j = getParent().getPieceCount(); i < j; ++i) {
          Concealment conceal = (Concealment) Decorator.getDecorator(getParent().getPieceAt(i), Concealment.class);
          if (conceal != null
              && conceal.canConceal(outer)) {
            concealmentExists = true;
            break;
          }
        }
      }
      if (!concealmentExists) {
        GamePiece concealOuter = createConcealment();
        Concealment conceal = (Concealment) Decorator.getDecorator(concealOuter, Concealment.class);
        c.append
            (getMap().getStackMetrics().merge
             (outer, concealOuter));
        for (int i = 0,j = getParent().indexOf(outer);
             i < j; ++i) {
          c.append(conceal.setConcealed(getParent().getPieceAt(i), true));
        }
      }
    }
    else {
      c = super.myKeyEvent(stroke);
    }
    return c;
  }

  public Command keyEvent(javax.swing.KeyStroke stroke) {
    Stack parent = getParent();
    if (parent != null) {
      int lastIndex = getParent().indexOf(Decorator.getOutermost(this));
      Command c = super.keyEvent(stroke);
      if (getParent() != null) {
        int newIndex = getParent().indexOf(Decorator.getOutermost(this));
        if (newIndex != lastIndex) {
          c.append(adjustConcealment());
        }
      }
      return c;
    }
    else {
      return super.keyEvent(stroke);
    }
  }

  /**
   * Conceal/unconceal this unit according to whether a concealment
   * counter is on top of it in a stack
   */
  public Command adjustConcealment() {
    if (isMaskable()) {
      GamePiece outer = Decorator.getOutermost(this);
      String state = outer.getState();
      setProperty(Properties.OBSCURED_BY, null);
      if (getParent() != null) {
        for (int i = getParent().indexOf(outer),j = getParent().getPieceCount(); i < j; ++i) {
          Concealment p = (Concealment) Decorator.getDecorator(getParent().getPieceAt(i), Concealment.class);
          if (p != null && p.canConceal(this)) {
            setProperty(Properties.OBSCURED_BY, GameModule.getUserId());
            break;
          }
        }
        getMap().repaint(getMap().boundingBoxOf(getParent()));
      }
      return outer.getState().equals(state) ? null
          : new ChangePiece(outer.getId(), state, outer.getState());
    }
    else {
      return null;
    }
  }

  /**
   * Conceal/unconceal units in this stack according to positions
   * of concealment counters in the stack
   */
  public static Command adjustConcealment(Stack s) {
    if (s == null || s.getMap() == null) {
      return null;
    }
    Command c = new NullCommand();
    for (int i = 0,j = s.getPieceCount(); i < j; ++i) {
      Concealable p = (Concealable) Decorator.getDecorator(s.getPieceAt(i), Concealable.class);
      if (p != null) {
        c = c.append(p.adjustConcealment());
      }
    }
    s.getMap().repaint(s.getMap().boundingBoxOf(s));
    return c;
  }

  @Override
  public boolean isMaskable() {
    return access.currentPlayerCanModify(obscuredBy)
      || ObscurableOptions.getInstance().isUnmaskable(obscuredBy);
  }

  /**
   * @return a new GamePiece that is a concealment counter
   * appropriate for this unit
   */
  public GamePiece createConcealment() {
    GamePiece p = null;
    if (concealmentMarker != null) {
      try {
        Configurable[] c = ComponentPathBuilder.getInstance().getPath(concealmentMarker);
        if (c[c.length - 1] instanceof PieceSlot) {
          p = PieceCloner.getInstance().clonePiece(((PieceSlot) c[c.length - 1]).getPiece());
        }
      }
      catch (ComponentPathBuilder.PathFormatException e) {
      }
    }
    if (p == null) {
      p = new BasicPiece(BasicPiece.ID + "K;D;" + imageName + ";?");
      boolean large = imageName.indexOf("58") > 0;
      if (!imageName.startsWith(nation)) { // Backward compatibility with generic concealment markers
        large = imageName.substring(0, 1).toUpperCase().
            equals(imageName.substring(0, 1));
        String size = large ? "60;60" : "48;48";
        if (nation2 != null) {
          p = new ColoredBox(ColoredBox.ID + "ru" + ";" + size, p);
          p = new ColoredBox(ColoredBox.ID + "ge" + ";"
                             + (large ? "48;48" : "36;36"), p);
        }
        else {
          p = new ColoredBox(ColoredBox.ID + nation + ";" + size, p);
        }
        p = new Embellishment(Embellishment.OLD_ID + ";;;;;;0;0;"
                              + imageName + ",?", p);
      }
      p = new Concealment(Concealment.ID + GameModule.getUserId() + ";" + nation, p);
      p = new MarkMoved(MarkMoved.ID + (large ? "moved58" : "moved"), p);
      p = new Hideable("hide;H;HIP;255,255,255", p);
      p = new FreeRotator("rotate;6;88,130;90,130;CA cw;CA ccw;;;", p);
    }
    return p;
  }

  public Rectangle boundingBox() {
    return piece.boundingBox();
  }

  public Shape getShape() {
    return piece.getShape();
  }

  private ScaledImagePainter conceal = null;

  private void loadImages(Component obs) {
    if (concealedToMe == null) {
      //try {
        concealedToMe = Op.load(imageName + ".gif").getImage();
        if (concealedToMe != null) {
          concealedToMe = null;
          conceal = new ScaledImagePainter();
          conceal.setImageName(imageName + ".gif");
          imageSize = conceal.getImageSize();
        }
        else {
          //imageSize.setSize(0, 0);
          concealedToMe = obs.createImage(20, 20);
          java.awt.Graphics g = concealedToMe.getGraphics();
          g.drawString("?", 0, 0);

        }
      //}
      //catch (Exception ex) {
        //concealedToMe = obs.createImage(20, 20);
        //java.awt.Graphics g = concealedToMe.getGraphics();
        //g.drawString("?", 0, 0);
      //}
    }
    if (concealedToOthers == null) {
      concealedToOthers = Op.load(nation + "/" + nation + "qmarkme.gif").getImage();
      if (concealedToOthers == null) {
        // Using generic qmarkme.gif image and prefs-specified colors
        BufferedImage rev = new BufferedImage(20, 20, BufferedImage.TYPE_4BYTE_ABGR);
        final Graphics2D g = rev.createGraphics();
        g.drawString("?", 0, 0);
        concealedToOthers = rev;
        g.dispose();
      }
    }

  }

  public String getName() {
    if (obscuredToMe()) {
      return "?";
    }
    else if (obscuredToOthers()) {
      return piece.getName() + "(?)";
    }
    else {
      return piece.getName();
    }
  }

  public Object getProperty(Object key) {
    if (ASLProperties.HINDRANCE.equals(key)
        && obscuredToMe()) {
      return null;
    }
    else if (ASLProperties.NATIONALITY.equals(key)) {
      return nation;
    }
    else {
      return super.getProperty(key);
    }
  }

  public Color getColor(String s) {
    return (Color) GameModule.getGameModule().getPrefs().getValue(s);
  }

  public String getDescription() {
    return "Can be concealed";
  }

  public VASSAL.build.module.documentation.HelpFile getHelpFile() {
    return null;
  }

  public PieceEditor getEditor() {
    return new Ed(this);
  }

  protected static class Ed implements PieceEditor {
    private Box controls;
    private StringConfigurer keyConfig;
    private ImagePicker imageConfig;
    private StringConfigurer nationConfig;
    private String nation2;
    private PieceSlot pieceInput;
    private String concealmentMarkerPath;

    public Ed(Concealable c) {
      controls = Box.createVerticalBox();
      keyConfig = new StringConfigurer(null, "Key Command CTRL-", String.valueOf(c.obscureKey));
      controls.add(keyConfig.getControls());
      imageConfig = new ImagePicker();
      imageConfig.setImageName(c.imageName);
      Box b = Box.createHorizontalBox();
      b.add(new JLabel("View when concealed:  "));
      b.add(imageConfig);
      controls.add(b);
      nationConfig = new StringConfigurer(null, "Nationality:  ", c.nation);
      controls.add(nationConfig.getControls());
      nation2 = c.nation2;

      pieceInput = new PieceSlot();
      JButton selectButton = new JButton("Select");
      selectButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ChooseComponentPathDialog d = new ChooseComponentPathDialog((Frame) SwingUtilities.getAncestorOfClass(Frame.class, controls), PieceSlot.class);
          d.setVisible(true);
          if (d.getTarget() instanceof PieceSlot) {
            pieceInput.setPiece(((PieceSlot) d.getTarget()).getPiece());
          }
          if (d.getPath() != null) {
            concealmentMarkerPath = ComponentPathBuilder.getInstance().getId(d.getPath());
          }
          else {
            concealmentMarkerPath = null;
          }
        }
      });
      b = Box.createHorizontalBox();
      b.add(new JLabel("Concealment Marker"));
      b.add(pieceInput.getComponent());
      b.add(selectButton);
      controls.add(b);

    }

    public Component getControls() {
      return controls;
    }

    public String getState() {
      return "null";
    }

    public String getType() {
      SequenceEncoder se = new SequenceEncoder(';');
      se.append(keyConfig.getValueString()).append(imageConfig.getImageName()).append(nationConfig.getValueString());
      se.append(nation2 != null ? nation2 : "");
      se.append(concealmentMarkerPath != null ? concealmentMarkerPath : "");
      return ID + se.getValue();
    }
  }
}
