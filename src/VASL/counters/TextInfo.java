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

import VASSAL.command.Command;
import VASSAL.counters.*;
import VASSAL.tools.swing.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.util.StringTokenizer;

/**
 * A trait that displays the vehicle/gun info (from the back of the physical counter) as a simple box of text
 */
public class TextInfo extends Decorator implements EditablePiece {
  public static final String ID = "info;";

  private String info;
  private KeyCommand[] commands;
  private static KeyCommand[] EMPTY_COMMANDS = new KeyCommand[0];
  private boolean showInfo = false;
  private Dimension infoSize;
  private Image infoImage;
  private static Font font = new Font("Dialog", 0, 11);

  public TextInfo() {
    this(ID, null);
  }

  public TextInfo(String type, GamePiece p) {
    setInner(p);
    info = type.substring(type.indexOf(';') + 1);
  }

  public void mySetType(String type) {
    info = type.substring(type.indexOf(';') + 1);
  }

  public void mySetState(String newState) {
  }

  public String myGetState() {
    return "";
  }

  public String myGetType() {
    return ID + info;
  }

  protected KeyCommand[] myGetKeyCommands() {
    if (commands == null) {
      commands = new KeyCommand[1];
      commands[0] = new KeyCommand("Show Info",
                                   KeyStroke.getKeyStroke('I', java.awt.event.InputEvent.CTRL_MASK),
                                   Decorator.getOutermost(this));
    }
    boolean concealed = Boolean.TRUE.equals(getProperty(Properties.OBSCURED_TO_ME));
    commands[0].setEnabled(getMap() != null && !concealed);
    return concealed ? EMPTY_COMMANDS : commands;
  }

  public Command myKeyEvent(javax.swing.KeyStroke stroke) {
    myGetKeyCommands();
    if (commands[0].matches(stroke)) {
      showInfo = !showInfo;
    }
    return null;
  }

  public boolean isInfoShowing() {
    return showInfo;
  }

  public Shape getShape() {
    return piece.getShape();
  }

  public Rectangle boundingBox() {
    Rectangle r = piece.boundingBox();
    if (infoSize == null) {
      return r;
    }
    else {
      Rectangle infoRec = new Rectangle
          (getInfoOffset().x, getInfoOffset().y,
           infoSize.width, infoSize.height);
      return r.union(infoRec);
    }
  }

  private Point getInfoOffset() {
    return new Point(piece.getShape().getBounds().width / 2 + 6,
                     -infoSize.height / 2);
  }

  public String getName() {
    return piece.getName();
  }

  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    piece.draw(g, x, y, obs, zoom);
    if (showInfo) {
      if (infoSize == null) {
        final Graphics2D g2d = (Graphics2D) g;
        final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
        final Font scaled_font = font.deriveFont(((float)(font.getSize() * os_scale)));

        g.setFont(scaled_font);
        infoSize = getInfoSize(info, g.getFontMetrics(), os_scale);
        infoImage = createInfoImage(obs, scaled_font, os_scale);
      }

      g.drawImage(
        infoImage,
        x + (int) (zoom * getInfoOffset().x),
        y + (int) (zoom * getInfoOffset().y),
        (int) (zoom * infoSize.width),
        (int) (zoom * infoSize.height), obs
      );
    }
  }

  /** Returns the size of the box into which the text info will be drawn
   */
  protected Dimension getInfoSize(String s, FontMetrics fm, double os_scale) {
    int wid = 0;
    StringTokenizer st = new StringTokenizer(s, "^,", true);
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      switch (token.charAt(0)) {
        case '^':
          break;
        case ',':
          wid += fm.stringWidth("  ");
          break;
        default:
          wid += fm.stringWidth(token);
      }
    }
    wid += 10 * os_scale;
    int hgt = fm.getAscent() * 2;
    return new Dimension(wid, hgt);
  }

  protected Image createInfoImage(Component obs, Font scaled_font, double os_scale) {
    Image im = obs.createImage(infoSize.width, infoSize.height);
    Graphics g = im.getGraphics();
    ((Graphics2D) g).addRenderingHints(SwingUtils.FONT_HINTS);
    g.setFont(scaled_font);
    writeInfo(g, 0, infoSize.height / 2, os_scale);
    g.dispose();
    return im;
  }

  /**
   * Write the special info.  The info string is broken into comma-separated
   * tokens, with each token separated by two spaces
   * Some characters are handled specially:
   * Tokens beginning with 'r' are written in red
   * 'R' is circled (radioless)
   * '^' indicates the beginning/end of a superscript
   */
  protected void writeInfo(Graphics g, int x, int y, double os_scale) {
    FontMetrics fm = g.getFontMetrics();

    g.setColor(Color.white);
    g.fillRect(x, y - infoSize.height / 2, infoSize.width, infoSize.height);
    g.setColor(Color.black);
    g.drawRect(x, y - infoSize.height / 2, infoSize.width - 1, infoSize.height - 1);

    final int ascent = fm.getAscent();

    StringTokenizer st = new StringTokenizer(info, "^,", true);

    x += 7 * os_scale;
    y += infoSize.height / 2 - 6 * os_scale;
    boolean superScript = false;
    while (st.hasMoreTokens()) {
      String s = st.nextToken();
      g.setColor(Color.black);

      switch (s.charAt(0)) {
        case 'r':
          s = s.substring(1);
          g.setColor(Color.red);
          break;
        case 'R':
          if (s.length() == 1) {
            g.drawOval(
              (int) (x - 3 * os_scale),
              y - ascent,
              (int) (ascent + 2 * os_scale),
              (int) (ascent + 2 * os_scale)
            );
          }
          break;
        case ',':
          s = "  ";
          break;
        case '^':
          s = "";
          superScript = !superScript;
          y += (superScript ? -ascent : ascent) / 2;
          break;
      }

      g.drawString(s, x, y);
      x += fm.stringWidth(s);
    }
  }

  public String getDescription() {
    return "Has info";
  }

  public VASSAL.build.module.documentation.HelpFile getHelpFile() {
    return null;
  }

  public PieceEditor getEditor() {
    return new SimplePieceEditor(this);
  }
}
