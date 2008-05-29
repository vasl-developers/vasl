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
import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

public class SSROverlay extends Overlay {
  private Point basePos;

  protected SSROverlay() {
  }

  public SSROverlay(String s, File archiveFile) {
    try {
      StringTokenizer st = new StringTokenizer(s);
      name = st.nextToken();
      String position = st.nextToken();

      basePos = new Point(Integer.parseInt(position.substring
                                           (0, position.indexOf(','))),
                          Integer.parseInt(position.substring
                                           (position.indexOf(',') + 1)));
      overlayFile = archiveFile;
      try {
        archive = new DataArchive(overlayFile.getPath(),"");
      }
      catch (IOException e) {
        throw new IllegalArgumentException("Unable to open "+overlayFile);
      }
      boundaries.setSize(archive.getImageSize(name));

      boundaries.setLocation(basePos);
    }
    catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
  
  @Override
  public SSRFilter getTerrain() {
    return null;
  }

  protected Image loadImage() {
    Image im = null;
    try {
      im = ImageIO.read(new MemoryCacheImageInputStream(archive.getImageInputStream(name)));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return im;
  }

  public String toString() {
    return "";
  }
}
