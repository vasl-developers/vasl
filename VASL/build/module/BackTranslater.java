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

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameState;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.tools.Decoder;

import java.io.ByteArrayOutputStream;
import java.util.StringTokenizer;

/**
 * Provides backward-compatibility for VASL 3.0 savefiles
 */
public class BackTranslater extends AbstractBuildable implements CommandEncoder {
  public void addTo(Buildable b) {
    GameModule.getGameModule().addCommandEncoder(this);
  }

  public String[] getAttributeNames() {
    return new String[0];
  }

  public void setAttribute(String name, Object value) {
  }

  public String getAttributeValueString(String name) {
    return null;
  }

  public String encode(Command c) {
    return null;
  }

  public static String decodeString(String in) {
    if (in == null)
      return null;

    if (in.indexOf("%") < 0) {
      return in;
    }

    String s = Decoder.URLdecode(in);
    ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());

    for (int i = 0; i < s.length(); i++) {
      int c = (int) s.charAt(i) ^ 0x117e;
      out.write(c);
    }
    return out.toString();
  }


  public Command decode(String s) {
    boolean encoded = false;
    if (s.startsWith("%1C%1Aw")) {
      encoded = true;
    }
    else if (s.startsWith("bd\t")
        && (s.indexOf("\n") > 0 || s.indexOf("\r") > 0)) {
      encoded = false;
    }
    else {
      return null;
    }

    Command c = new GameState.SetupCommand(false);
    StringTokenizer st = new StringTokenizer(s, "\n\r");
    while (st.hasMoreTokens()) {
      String sub = decodeString(st.nextToken());
      try {
        c.append(GameModule.getGameModule().decode(sub));
      }
      catch (Exception ex) {
        System.err.println("Error decoding " + sub);
        ex.printStackTrace();
      }
    }
    return c;
  }
}
