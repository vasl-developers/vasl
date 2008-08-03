/*
 * Copyright (c) 2000-2007 by Rodney Kinney
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

import java.awt.Rectangle;

import VASL.counters.TextInfo;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;

public class CounterDetailViewer extends VASSAL.build.module.map.CounterDetailViewer {
  @Override
  protected Rectangle getBounds(GamePiece piece) {
    TextInfo info = (TextInfo) Decorator.getDecorator(piece, TextInfo.class);
    if (info != null && info.isInfoShowing()) {
      return piece.boundingBox();
    }
    else {
      return super.getBounds(piece);
    }
  }
}
