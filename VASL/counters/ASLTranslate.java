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

import java.util.ArrayList;
import java.util.Iterator;

import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.counters.GamePiece;
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

  protected Command moveTarget(GamePiece target) {
    Command c;
    if (target instanceof Stack) {
      Stack s = (Stack) target;
      ArrayList movable = new ArrayList();
      for (Iterator<GamePiece> it = s.getPiecesIterator(); it.hasNext();) {
        GamePiece piece = it.next();
        if (piece.getProperty(ASLProperties.LOCATION) == null) {
          movable.add(piece);
        }
      }
      if (movable.size() == s.getPieceCount()) {
        return super.moveTarget(s);
      }
      else {
        c = new NullCommand();
        for (Iterator it = movable.iterator(); it.hasNext();) {
          GamePiece gamePiece = (GamePiece) it.next();
          c.append(super.moveTarget(gamePiece));
        }
      }
    }
    else {
    c = super.moveTarget(target);
    }
    return c;
  }
}
