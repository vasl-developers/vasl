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
import VASSAL.counters.Decorator;
import VASSAL.counters.EditablePiece;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyCommand;
import VASSAL.counters.PieceEditor;
import VASSAL.counters.Properties;
import VASSAL.counters.SimplePieceEditor;
import VASSAL.tools.swing.SwingUtils;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.InputEvent;
import javax.swing.KeyStroke;
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
  private Dimension zoomedInfoSize;
  private double lastZoom;
  private static Font font = new Font("Dialog", 0, 11);

  public TextInfo() {
    this(ID, null);
  }

  public TextInfo(String type, GamePiece p) {
    setInner(p);
    info = type.substring(type.indexOf(';') + 1);
    infoSize = null;
    zoomedInfoSize = null;
    lastZoom = 0.0;
  }

  public void mySetType(String type) {
    info = type.substring(type.indexOf(';') + 1);
    infoSize = null;
    zoomedInfoSize = null;
    lastZoom = 0.0;
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
      commands[0] = new KeyCommand(
        "Show Info",
        KeyStroke.getKeyStroke('I', InputEvent.CTRL_MASK),
        Decorator.getOutermost(this)
      );
    }
    final boolean concealed =
      Boolean.TRUE.equals(getProperty(Properties.OBSCURED_TO_ME));
    commands[0].setEnabled(getMap() != null && !concealed);
    return concealed ? EMPTY_COMMANDS : commands;
  }

  public Command myKeyEvent(KeyStroke stroke) {
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
      Rectangle infoRec = new Rectangle(
        getInfoOffset().x,
        getInfoOffset().y,
        infoSize.width,
        infoSize.height
      );
      return r.union(infoRec);
    }
  }

  private Point getInfoOffset() {
    return new Point(
      piece.getShape().getBounds().width / 2 + 6,
      -infoSize.height / 2
    );
  }

  public String getName() {
    return piece.getName();
  }

  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    piece.draw(g, x, y, obs, zoom);
    if (showInfo) {
      if (infoSize == null) {
        g.setFont(font);
        infoSize = getInfoSize(info, g.getFontMetrics(), 1.0);
      }

      final Font zfont = font.deriveFont(((float)(font.getSize() * zoom)));
      g.setFont(zfont);

      if (zoom != lastZoom) {
        // NB: We can't just scale the size at 1.0, because fonts do not scale
        // uniformly when rendered at different sizes.
        zoomedInfoSize = getInfoSize(info, g.getFontMetrics(), zoom);
      }

      drawInfo(
        g,
        x + (int)(getInfoOffset().x * zoom),
        y,
        zoomedInfoSize.width,
        zoomedInfoSize.height,
        zoom
      );
    }
  }

  /**
   * Returns the size of the box into which the text info will be drawn
   */
  protected Dimension getInfoSize(String s, FontMetrics fm, double zoom) {
    int w = 0;
    StringTokenizer st = new StringTokenizer(s, "^,", true);
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      switch (token.charAt(0)) {
      case '^':
        break;
      case ',':
        w += fm.stringWidth("  ");
        break;
      default:
        w += fm.stringWidth(token);
      }
    }
    w += 14*zoom;
    final int h = fm.getAscent() * 2;
    return new Dimension(w, h);
  }

  /**
   * Draw the special info.  The info string is broken into comma-separated
   * tokens, with each token separated by two spaces
   * Some characters are handled specially:
   * Tokens beginning with 'r' are written in red
   * 'R' is circled (radioless)
   * '^' indicates the beginning/end of a superscript
   */
  protected void drawInfo(Graphics g, int x, int y, int w, int h, double zoom) {
    FontMetrics fm = g.getFontMetrics();

    g.setColor(Color.white);
    g.fillRect(x, y - h / 2, w, h);
    g.setColor(Color.black);
    g.drawRect(x, y - h / 2, w - 1, h - 1);

    x += 7*zoom;
    y += h / 2 - 6*zoom;

    final int ascent = fm.getAscent();
    final StringTokenizer st = new StringTokenizer(info, "^,", true);
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
              (int) (x - 3*zoom),
              y - ascent,
              (int) (ascent + 2*zoom),
              (int) (ascent + 2*zoom)
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
