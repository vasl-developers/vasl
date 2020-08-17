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
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.counters.Translate;

import static VASSAL.build.GameModule.getGameModule;

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
          ArrayList<GamePiece> movable = new ArrayList<GamePiece>();
          ArrayList<GamePiece> visibleToMe = new ArrayList<GamePiece>();

          for (Iterator<GamePiece> it = s.getPiecesIterator(); it.hasNext();) {
              GamePiece piece = it.next();

              if (!Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME))) {
                  visibleToMe.add(piece);
              }
          }

          for (Iterator<GamePiece> it = visibleToMe.iterator(); it.hasNext();) {
              GamePiece piece = it.next();

              if (piece.getProperty(ASLProperties.LOCATION) == null) {
                  movable.add(piece);
              }
              else
              {
                  KeyBuffer.getBuffer().remove(piece);
              }              
          }

          if (movable.size() == s.getPieceCount()) {
              return super.moveTarget(s);
          } else {
              c = new NullCommand();
              for (Iterator<GamePiece> it = movable.iterator(); it.hasNext();) {
                  GamePiece gamePiece = it.next();
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
      if (getMap().getMapName().equals("Casualties")){  // need to disable one hex moves by CTRL-numberpad because there is no grid in Casbin; VASSAL error results
          getGameModule().getChatter().send("Key Combos cannot move units in the Casualties Bin. Drag and Drop instead!");
      } else {
          if (b != null && ((HexGrid) b.getGrid()).getHexSize() != ASLBoard.DEFAULT_HEX_HEIGHT) {
              int x = p.x;
              int y = p.y;
              super.translate(p);
              double scale = ((HexGrid) b.getGrid()).getHexSize() / ASLBoard.DEFAULT_HEX_HEIGHT;
              p.x = x + (int) Math.round(scale * (p.x - x));
              p.y = y + (int) Math.round(scale * (p.y - y));
          } else {
              super.translate(p);
          }
      }
  } 
}
