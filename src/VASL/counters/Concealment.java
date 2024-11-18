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
import VASSAL.preferences.Prefs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Objects;

/**
 * A Concealment counter
 */
public class Concealment extends Decorator implements EditablePiece {
  public static final String ID = "concealment;";
  private KeyCommand[] commands;
  private String nation;
  private String owner;
  Prefs prefs = GameModule.getGameModule().getPrefs();

  public Concealment() {
    this(ID, null);
  }

  public Concealment(String type, GamePiece p) {
    setInner(p);
    mySetType(type);
  }

  public void mySetType(String type) {
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(type.substring(ID.length()), ';');
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
      commands[0] = new KeyCommand("Conceal", KeyStroke.getKeyStroke('C', InputEvent.CTRL_DOWN_MASK), Decorator.getOutermost(this));
    }
    return owner == null || owner.equals(GameModule.getUserId()) || ObscurableOptions.getInstance().isUnmaskable(owner) ? commands : new KeyCommand[0];
  }

  public Command myKeyEvent(javax.swing.KeyStroke stroke) {
    return null;
  }

  public Command keyEvent(javax.swing.KeyStroke stroke) {
    if (owner == null || owner.equals(GameModule.getUserId()) || ObscurableOptions.getInstance().isUnmaskable(owner)) {
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
        // Set the concealment for all counters in the stack.
        // All counters between a location delimiting counter (i.e. a buidling level counter)
        // and a concealment counter should be concealed
        boolean shouldConceal = false;
        for (int i = parent.getPieceCount()-1; i >= 0; i--) {
          GamePiece child = parent.getPieceAt(i);
          // Counters below the concealment counter should be concealed
          if (Decorator.getDecorator(child, Concealment.class) != null) {
            shouldConceal = true;
          }
          // If the counter has a separateLocation property, counters below should be unconcealed
          else if (child.getProperty("separateLocation") != null) {
              shouldConceal = false;
            }
          else {
            c.append(setConcealed(child, shouldConceal));
          }
        }
        tracker.repaint();
        return c;
      } else {
        return super.keyEvent(stroke);
      }
    } else {
      return null;
    }
  }

  /**
   * Conceal or unconceal the given unit. Do nothing if the the unit is not concealable by this concealment counter
   */
  public Command setConcealed(GamePiece p, boolean concealed) {
    if (canConceal(p)) {
      String state = p.getState();
      p.setProperty(Properties.OBSCURED_BY, concealed ? GameModule.getUserId() : null);
      return new ChangePiece(p.getId(), state, p.getState());
    } else {
      return null;
    }
  }

  /**
   * @return true if this concealment counter is applicable to the given piece (i.e. if the piece is a concealable
   * counter of the same nationality)
   */
  public boolean canConceal(GamePiece p) {
    Concealable c = (Concealable) Decorator.getDecorator(p, Concealable.class);
    if (c == null || !c.isMaskable()) {
      return false;
    } else {
      return getNationality().equals(c.getProperty(ASLProperties.NATIONALITY));
    }
  }

  private String getNationality() {
    String value = nation;
    if (value == null) {
      ColoredBox b = (ColoredBox) Decorator.getDecorator(this, ColoredBox.class);
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
    // Check if the hideconcealmentcounter preference is set to true, and if the counter is a concealment counter
    // and if the stack contains an unconcealed counter below the concealment counter
    if (!(prefs.getValue("hideconcealmentcounter") == null) && (boolean) prefs.getValue("hideconcealmentcounter")
            && isConcealmentCounter() && containsUnconcealedCounterBelow()) {
      // Draw the top concealment counter in a friendly non-dummy stack with reduced opacity
      Graphics2D g2d = (Graphics2D) g;
      Composite originalComposite = g2d.getComposite();
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
      piece.draw(g, x, y, obs, zoom);
      g2d.setComposite(originalComposite);
    }
    else {
      piece.draw(g, x, y, obs, zoom);
    }
  }
  // Check if the counter is a concealment counter
  private boolean isConcealmentCounter() {
    GamePiece piece = this;
    if (Decorator.getDecorator(piece, Concealment.class) != null) {
      Concealment concealment = (Concealment) Decorator.getDecorator(piece, Concealment.class);
      //Check if the current player is the owner of the concealment counter
        return Objects.equals(concealment.owner, GameModule.getUserId());
    }
    return false;
  }
  // Check if the stack contains an unconcealed counter below the concealment counter
  // for the purpose of determining if the concealment counter should be drawn with reduced opacity
  private boolean containsUnconcealedCounterBelow() {
    Stack stack = getParent();
    if ( stack != null) {
      int index = stack.indexOf(Decorator.getOutermost(this));
      for (int i = index - 1; i >= 0; i--) {
        GamePiece piece = stack.getPieceAt(i);
        if (Decorator.getDecorator(piece, Concealment.class) == null) {
          if (piece.getProperty(Properties.OBSCURED_TO_ME) == null || !(boolean) piece.getProperty(Properties.OBSCURED_TO_ME)) {
              return true;
          }
        }
      }
    }
    return false;
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
