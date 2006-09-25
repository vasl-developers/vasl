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

import VASSAL.build.GameModule;
import VASSAL.command.Command;
import VASSAL.counters.Decorator;
import VASSAL.counters.EditablePiece;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyCommand;
import VASSAL.tools.SequenceEncoder;

import java.awt.*;

/**
 * A GamePiece that draws itself as a box of a fixed size and color
 */
public class ColoredBox extends Decorator implements EditablePiece {
  public static final String ID = "color;";

  private String colorId;
  private Dimension size;

  public ColoredBox() {
    this(ID + "255,255,255;48;48", null);
  }

  public ColoredBox(String type, GamePiece p) {
    mySetType(type);
    setInner(p);
  }

  public void mySetType(String type) {
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(type, ';');
    st.nextToken();

    colorId = st.nextToken();
    size = new Dimension(Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()));
  }

  public void mySetState(String newState) {
  }

  public String myGetState() {
    return "";
  }

  public String myGetType() {
    return ID + colorId + ";" + size.width + ";" + size.height;
  }

  protected KeyCommand[] myGetKeyCommands() {
    return new KeyCommand[0];
  }

  public Command myKeyEvent(javax.swing.KeyStroke stroke) {
    return null;
  }

  public Shape getShape() {
    Rectangle r = new Rectangle(new Point(), size);
    r.translate(-r.width / 2, -r.height / 2);
    return r;
  }

  public Rectangle boundingBox() {
    return piece.boundingBox();
  }

  public String getName() {
    return piece.getName();
  }

  public Color getColor() {
    return (Color) GameModule.getGameModule().getPrefs().getValue(colorId);
  }

  public String getColorId() {
    return colorId;
  }

  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    piece.draw(g, x, y, obs, zoom);
    g.setColor(getColor());
    g.fillRect(x - (int) (zoom * size.width) / 2,
               y - (int) (zoom * size.height) / 2,
               (int) (zoom * size.width), (int) (zoom * size.height));
  }

  public String getDescription() {
    return "Colored Background";
  }

  public VASSAL.build.module.documentation.HelpFile getHelpFile() {
    return null;
  }
}
