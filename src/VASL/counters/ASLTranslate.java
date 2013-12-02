/*
 * $Id$
 *
 * Copyright (c) 2005 by Rodney Kinney
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

import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;

import VASL.build.module.map.boardPicker.ASLBoard;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.counters.Translate;

/**
 * Modifies the {@link Translate} base class by not moving counters with the {@link ASLProperties#LOCATION} trait
 */
public class ASLTranslate extends Translate {
  public ASLTranslate() {
  }

  public ASLTranslate(String type, GamePiece inner) {
    super(type, inner);
  }

  // FredKors 30/11/2013 : Filter INVISIBLE_TO_ME counters
  protected Command moveTarget(GamePiece target) {
      Command c;
      if (target instanceof Stack) {
          Stack s = (Stack) target;
          ArrayList movable = new ArrayList();
          ArrayList visibleToMe = new ArrayList();

          for (Iterator<GamePiece> it = s.getPiecesIterator(); it.hasNext();) {
              GamePiece piece = it.next();

              if (!Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME))) {
                  visibleToMe.add(piece);
              }
          }

          for (Iterator it = visibleToMe.iterator(); it.hasNext();) {
              GamePiece piece = (GamePiece) it.next();

              if (piece.getProperty(ASLProperties.LOCATION) == null) {
                  movable.add(piece);
              }
          }

          if (movable.size() == s.getPieceCount()) {
              return super.moveTarget(s);
          } else {
              c = new NullCommand();
              for (Iterator it = movable.iterator(); it.hasNext();) {
                  GamePiece gamePiece = (GamePiece) it.next();
                  c.append(super.moveTarget(gamePiece));
              }
          }
      } else {
          c = super.moveTarget(target);
      }
    
    return c;
  }

  @Override
  protected void translate(Point p) {
    Board b = getMap().findBoard(p);
    if (b != null && ((HexGrid) b.getGrid()).getHexSize() != ASLBoard.DEFAULT_HEX_HEIGHT) {
      int x = p.x;
      int y = p.y;
      super.translate(p);
      double scale = ((HexGrid) b.getGrid()).getHexSize() / ASLBoard.DEFAULT_HEX_HEIGHT;
      p.x = x + (int) Math.round(scale * (p.x - x));
      p.y = y + (int) Math.round(scale * (p.y - y));
    }
    else {
      super.translate(p);
    }
  } 
}
