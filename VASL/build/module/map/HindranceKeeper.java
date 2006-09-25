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
package VASL.build.module.map;

import VASL.counters.ASLProperties;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.Drawable;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.Configurer;
import VASSAL.counters.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * This is a {@link Drawable} class that draws only counters that have the
 * {@link ASLProperties#HINDRANCE} property set.  It is enables when
 * the LOS Thread is being drawn.
 */
public class HindranceKeeper extends AbstractBuildable implements Drawable, KeyListener, PieceFilter {
  public static final String DRAW_HINDRANCES = "DrawHindrances";
  private Map map;

  public void addTo(Buildable b) {
    map = (Map) b;
    map.addDrawComponent(this);
    GameModule.getGameModule().getPrefs().addOption
        ("LOS", new BooleanConfigurer(DRAW_HINDRANCES, "Retain LOS-hindrance counters (toggle with shift-F10)"));
    map.getView().addKeyListener(this);
  }

  public String[] getAttributeNames() {
    return new String[0];
  }

  public void setAttribute(String name, Object value) {
  }

  public String getAttributeValueString(String name) {
    return null;
  }

  public void draw(Graphics g, Map m) {
    if (!m.isPiecesVisible()
        && Boolean.TRUE.equals(GameModule.getGameModule().getPrefs().getValue(DRAW_HINDRANCES))) {
      GamePiece[] p = m.getPieces();
      java.awt.Point pt;
      for (int i = 0; i < p.length; ++i) {
        if (p[i] instanceof Stack) {
          Stack temp = getVisibleHindrances((Stack) p[i]);
          if (temp != null) {
            pt = map.componentCoordinates(p[i].getPosition());
            map.getStackMetrics().draw(temp,g,pt.x,pt.y,map.getView(),map.getZoom());
          }
        }
        else if (isVisibleHindrance(p[i])) {
          pt = map.componentCoordinates(p[i].getPosition());
          p[i].draw(g, pt.x, pt.y, map.getView(), map.getZoom());
        }
      }
    }
  }

  public boolean drawAboveCounters() {
    return true;
  }

  public boolean accept(GamePiece piece) {
    return isVisibleHindrance(piece);
  }

  private boolean isVisibleHindrance(GamePiece p) {
    return p.getProperty(ASLProperties.HINDRANCE) != null
            && !Boolean.TRUE.equals(p.getProperty(Properties.INVISIBLE_TO_ME))
            && !Boolean.TRUE.equals(p.getProperty(Properties.OBSCURED_TO_ME));
  }

  private Stack getVisibleHindrances(Stack s) {
    class TempStack extends Stack {
      // This method adds a piece to the stack without removing it from its current parent
      // and without setting the parent on the child piece
      public void add(GamePiece c) {
        if (pieceCount >= contents.length) {
          GamePiece[] newContents = new GamePiece[contents.length + 5];
          System.arraycopy(contents, 0, newContents, 0, pieceCount);
          contents = newContents;
        }
        contents[pieceCount++] = c;
      }
      public boolean isExpanded() {
        return true;
      }
    }
    Stack tempStack = null;
    for (PieceIterator pi = new PieceIterator(s.getPieces(),this);pi.hasMoreElements();) {
      if (tempStack == null) {
        tempStack = new TempStack();
      }
      tempStack.add(pi.nextPiece());
    }
    return tempStack;
  }

  public void keyPressed(KeyEvent e) {
  }

  public void keyReleased(KeyEvent e) {
    if (!map.isPiecesVisible()
        && KeyStroke.getKeyStrokeForEvent(e).equals(KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_MASK, true))) {
      Configurer config = GameModule.getGameModule().getPrefs().getOption(DRAW_HINDRANCES);
      config.setValue(Boolean.TRUE.equals(config.getValue()) ? Boolean.FALSE : Boolean.TRUE);
      map.getView().repaint();
    }
  }

  public void keyTyped(KeyEvent e) {
  }
}
