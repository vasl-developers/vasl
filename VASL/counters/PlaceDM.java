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
/*
 * Created by IntelliJ IDEA.
 * User: rkinney
 * Date: Aug 28, 2002
 * Time: 4:02:56 AM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package VASL.counters;

import VASSAL.command.Command;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyCommand;
import VASSAL.counters.PlaceMarker;

import javax.swing.*;
import java.util.Enumeration;

/**
 * Places a DM Marker only if the underlying counter is not broken
 */
public class PlaceDM extends PlaceMarker {
  public static final String ID = "placeDM;";

  public PlaceDM() {
    this(ID + "Flip;F;null", null);
  }

  public PlaceDM(String type, GamePiece inner) {
    super(type, inner);
  }

  protected KeyCommand[] myGetKeyCommands() {
    command.setEnabled(true);
    return new KeyCommand[]{command};
  }

  public Command myKeyEvent(KeyStroke stroke) {
    KeyCommand[] k = myGetKeyCommands();
    if (!k[0].matches(stroke)) {
      return null;
    }
    Command result = null;
    if (getMap() != null
        && piece.getName().indexOf("broken") < 0) {
      boolean dmExists = false;
      if (getParent() != null) {
        GamePiece outer = Decorator.getOutermost(this);
        for (Enumeration e = getParent().getPiecesInReverseOrder();
             e.hasMoreElements();) {
          GamePiece p = (GamePiece) e.nextElement();
          if (p.getName().equals("DM")) {
            dmExists = true;
            break;
          }
          if (p == outer) {
            break;
          }
        }
      }
      if (!dmExists) {
        result = super.myKeyEvent(stroke);
      }
    }
    return result;
  }

  public String getDescription() {
    return "Place DM";
  }

  public String myGetType() {
    String s = super.myGetType();
    return ID + s.substring(s.indexOf(";") + 1);
  }
}
