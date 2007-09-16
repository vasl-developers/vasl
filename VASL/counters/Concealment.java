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
import VASSAL.build.module.ObscurableOptions;
import VASSAL.command.ChangePiece;
import VASSAL.command.Command;
import VASSAL.counters.*;
import VASSAL.tools.SequenceEncoder;

import javax.swing.*;
import java.awt.*;

/**
 * A Concealment counter
 */
public class Concealment extends Decorator implements EditablePiece {
  public static final String ID = "concealment;";

  private KeyCommand[] commands;
  private String nation;
  private String owner;

  public Concealment() {
    this(ID, null);
  }

  public Concealment(String type, GamePiece p) {
    setInner(p);
    mySetType(type);
  }

  public void mySetType(String type) {
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(type.substring(ID.length()),';');
    owner = st.nextToken(null);
    nation = st.nextToken(null);
  }

  public void setId(String id) {
    super.setId(id);
    if (id == null) {
      owner = GameModule.getUserId();
    }
  }

  public void mySetState(String newState) {
  }

  public String myGetState() {
    return "";
  }

  public String myGetType() {
    SequenceEncoder se = new SequenceEncoder(';');
    se.append(owner == null ? "null" : owner);
    String n = getNationality();
    if (n != null) {
      se.append(n);
    }
    return ID + se.getValue();
  }

  protected KeyCommand[] myGetKeyCommands() {
    if (commands == null) {
      commands = new KeyCommand[1];
      commands[0] = new KeyCommand("Conceal",
                                   KeyStroke.getKeyStroke('C', java.awt.event.InputEvent.CTRL_MASK),
                                   Decorator.getOutermost(this));
    }
    commands[0].setEnabled(owner == null
                           || owner.equals(GameModule.getUserId())
                           || ObscurableOptions.getInstance().isUnmaskable(owner));
    return commands;
  }

  public Command myKeyEvent(javax.swing.KeyStroke stroke) {
    return null;
  }

  public Command keyEvent(javax.swing.KeyStroke stroke) {
    Stack parent = getParent();
    if (parent != null) {
      BoundsTracker tracker = new BoundsTracker();
      tracker.addPiece(parent);
      int lastIndex = getParent().indexOf(Decorator.getOutermost(this));
      Command c = super.keyEvent(stroke);
      if (c == null || c.isNull()) {
        return c;
      }
      // Concealment counter was deleted or moved in the stack
      int newIndex = getParent() == null ? -1 : getParent().indexOf(Decorator.getOutermost(this));
      if (newIndex > lastIndex) {
        for (int i = lastIndex; i < newIndex; ++i) {
          c.append(setConcealed(parent.getPieceAt(i), true));
        }
      }
      else if (newIndex < lastIndex) {
        if (getParent() == null) {
          lastIndex--;
        }
        for (int i = lastIndex; i > newIndex; --i) {
          GamePiece child = parent.getPieceAt(i);
          if (Decorator.getDecorator(child, Concealment.class) != null) {
            break;
          }
          c.append(setConcealed(child, false));
        }
      }
      tracker.repaint();
      return c;
    }
    else {
      return super.keyEvent(stroke);
    }
  }

  /**
   * Conceal or unconceal the given unit.  Do nothing if the
   * the unit is not concealable by this concealment counter
   */
  public Command setConcealed(GamePiece p, boolean concealed) {
    if (canConceal(p)) {
      String state = p.getState();
      p.setProperty(Properties.OBSCURED_BY,
                    concealed ? GameModule.getUserId()
                    : null);
      return new ChangePiece(p.getId(), state, p.getState());
    }
    else {
      return null;
    }
  }

  /** @return true if this concealment counter is
   * applicable to the given piece (i.e. if the piece
   * is a concealable counter of the same nationality)
   */
  public boolean canConceal(GamePiece p) {
    Concealable c = (Concealable) Decorator.getDecorator(p, Concealable.class);
    if (c == null
        || !c.isMaskable()) {
      return false;
    }
    else {
      return getNationality().equals(c.getProperty(ASLProperties.NATIONALITY));
    }
  }

  private String getNationality() {
    String value = nation;
    if (value == null) {
      ColoredBox b = (ColoredBox) Decorator.getDecorator(this,ColoredBox.class);
      if (b != null) {
        value = b.getColorId();
      }
    }
    return value;
  }

  public Shape getShape() {
    return piece.getShape();
  }

  public Rectangle boundingBox() {
    return piece.boundingBox();
  }

  public String getName() {
    return piece.getName();
  }

  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    piece.draw(g, x, y, obs, zoom);
  }

  public String getDescription() {
    return "Is Concealment counter";
  }

  public VASSAL.build.module.documentation.HelpFile getHelpFile() {
    return null;
  }

  public PieceEditor getEditor() {
    return new SimplePieceEditor(this);
  }
}
