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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.RGBImageFilter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.ImageIcon;

import VASSAL.build.GameModule;
import VASSAL.tools.DataArchive;

/**
 * A special kind of SSROverlay constructed on the fly
 * by underlaying a patterned GIF under a board GIF
 * with certain colors turned transparent
 */
public class Underlay extends SSROverlay {

  private int transparentList[];
  private Image underlayImage;
  private String imageName;

  private static File archive;

  public Underlay(String im, int trans[]) {
    imageName = im;
    try {
      underlayImage = DataArchive.getImage(getStream("boardData/" + imageName));
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
    transparentList = trans;
  }

  public static void setGlobalArchive(File f) {
    archive = f;
  }

  public static InputStream getStream(String name) {
    try {
      return archive == null ?
          GameModule.getGameModule().getDataArchive()
          .getFileStream(name) :
          DataArchive.getFileStream(archive, name);
    }
    catch (IOException ex) {
      return null;
    }
  }

  public void readData() {
  }

  public void setImage(Component map) {
    if (underlayImage == null) {
      try {
        underlayImage = DataArchive.getImage(DataArchive.getFileStream(board.getFile(), imageName));
      }
      catch (IOException ex) {
        image = map.createImage(1, 1);
        System.err.println("Underlay image " + imageName + " not found in "
                           + board.getFile().getName());
        return;
      }
    }

    MediaTracker mt = new MediaTracker(map);

    try {
      mt.addImage(underlayImage, 0);
      mt.waitForAll();
    }
    catch (Exception e) {
    }
    if (board.getTerrain() != null) {
      underlayImage = board.getTerrain().recolor(underlayImage, map);
    }

    Point pos = new Point(0, 0);

    Image base = null;

    pos = board.getCropBounds().getLocation();
    boundaries.setSize(board.bounds().getSize());
    base = board.getBaseImage();
    try {
      mt.addImage(base, 0);
      mt.waitForAll();
    }
    catch (Exception e) {
    }

    boundaries.setLocation(pos.x, pos.y);

    base = Toolkit.getDefaultToolkit().createImage(new FilteredImageSource
        (base.getSource(),
         new HolePunch(transparentList, 0)));

    BufferedImage replacement = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(boundaries.width, boundaries.height, Transparency.BITMASK);
    Graphics2D g2 = replacement.createGraphics();
    try {
      mt.addImage(underlayImage, 0);
      mt.waitForAll();
    }
    catch (Exception e) {
    }
    int w = underlayImage.getWidth(map);
    int h = underlayImage.getHeight(map);
    for (int x = 0; x < boundaries.width; x += w)
      for (int y = 0; y < boundaries.height; y += h)
        g2.drawImage(underlayImage, x, y, map);
    try {
      mt.addImage(base, 0);
      mt.waitForAll();
    }
    catch (Exception e) {
    }
    g2.drawImage(base, -pos.x, -pos.y, map);
    try {
      mt.addImage(replacement, 0);
      mt.waitForAll();
    }
    catch (Exception e2) {
    }

    image = Toolkit.getDefaultToolkit().createImage(new FilteredImageSource
        (replacement.getSource(),
         new HolePunch(new int[]{0})));
    new ImageIcon(image);
    replacement = null;
    g2.dispose();
    System.gc();
  }
}

/**
 * Takes an image and turns a list of colors transparent
 */
class HolePunch extends RGBImageFilter {
  int transparent[]; /* List of colors to turn transparent (and red) */
  int mask = -1;     /* All other colors become this (unchanged if -1) */

  public HolePunch(int trans[], int mask) {
    transparent = trans;
    this.mask = mask;
    canFilterIndexColorModel = true;
  }

  public HolePunch(int trans[]) {
    this(trans, -1);
  }

  public int filterRGB(int x, int y, int rgb) {
    rgb = 0xffffff & rgb;
    for (int i = 0; i < transparent.length; ++i) {
      if (rgb == transparent[i])
        return 0xff;
    }
    return 0xff000000 | (mask >= 0 ? mask : rgb);
  }
}

