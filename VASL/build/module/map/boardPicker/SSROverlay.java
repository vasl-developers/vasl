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
package VASL.build.module.map.boardPicker;

import VASSAL.tools.DataArchive;

import java.awt.*;
import java.util.StringTokenizer;

public class SSROverlay extends Overlay {
  private Point basePos;

  public SSROverlay() {
  }

  public SSROverlay(String s) {
    try {
      StringTokenizer st = new StringTokenizer(s);
      name = st.nextToken();
      String position = st.nextToken();

      basePos = new Point(Integer.parseInt(position.substring
                                           (0, position.indexOf(','))),
                          Integer.parseInt(position.substring
                                           (position.indexOf(',') + 1)));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void setImage(ASLBoard b, Component map) {
    setImage(b, null, map);
  }

  public void setImage(ASLBoard b, Overlay o, Component map) {
    if (o != null) {
      try {
        image = DataArchive.getImage(DataArchive.getFileStream(o.getFile(), name));
      }
      catch (java.io.IOException e) {
        image = null;
      }
    }
    else {
      try {
        image = DataArchive.getImage(DataArchive.getFileStream(b.getFile(), name));
      }
      catch (java.io.IOException e2) {
        image = null;
      }
    }
    if (image == null) {
      image = map.createImage(1, 1);
    }

    MediaTracker track = new MediaTracker(map);
    try {
      track.addImage(image, 0);
      track.waitForID(0);
    }
    catch (Exception e) {
    }
    boundaries.setSize(image.getWidth(map), image.getHeight(map));

    boundaries.setLocation(basePos);

    if (o != null)
      image = o.getTerrain().recolor(image, map);
    else if (b.getTerrain() != null)
      image = b.getTerrain().recolor(image, map);
    try {
      track.addImage(image, 0);
      track.waitForID(0);
    }
    catch (Exception eWaitOvr) {
    }
  }

  public String toString() {
    return "";
  }
}
