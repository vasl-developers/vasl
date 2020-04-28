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
package VASL.build.module;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.DiceButton;

/**
 * Adds ability to detect SAN rolls
 */
public class ASLDiceButton extends DiceButton {
  private ScenInfo info;

  public void addTo(Buildable b) {
    super.addTo(b);
    info = GameModule.getGameModule().getComponentsOf(ScenInfo.class).iterator().next();
  }

  protected void DR() {
    String val = "";
    int total = 0;
    for (int i = 0; i < nDice; ++i) {
      int roll = (int) (ran.nextFloat() * nSides + 1) + plus;
      total += roll;
      val += roll;
      if (i < nDice - 1) {
        val += ",";
      }
    }

    val = formatResult(val);
    if (total == info.getAxisSAN()) {
      if (total == info.getAlliedSAN()) {
        val += " Axis/Allied SAN";
      }
      else {
        val += " Axis SAN";
      }
    }
    else if (total == info.getAlliedSAN()) {
      val += " Allied SAN";
    }
    GameModule.getGameModule().getChatter().send(val);
  }
}
