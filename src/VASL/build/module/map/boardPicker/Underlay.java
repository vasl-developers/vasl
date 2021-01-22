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

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import VASSAL.build.GameModule;
import VASSAL.tools.DataArchive;
import VASSAL.tools.image.ImageUtils;
import org.apache.commons.io.IOUtils;

/**
 * A special kind of SSROverlay constructed on the fly by underlaying a patterned GIF under a board GIF with certain
 * colors turned transparent
 */
public class Underlay extends SSROverlay {
  private int transparentList[];
  private String imageName;
  private DataArchive archive;

  public Underlay(String im, int trans[], DataArchive a, ASLBoard board) {
    imageName = im;
    transparentList = trans;
    this.archive = a;
    this.board = board;

    boundaries.setSize(board.bounds().getSize());
    Point pos = board.getCropBounds().getLocation();
    boundaries.setLocation(pos.x, pos.y);
  }

  public void readData() {
  }

  public Image loadImage() {
    Image underlayImage = null;
    // Get the image from the module data archive...
    try(InputStream in = GameModule.getGameModule().getDataArchive().getInputStream("boardData/" + imageName)) {
      underlayImage = ImageIO.read(new MemoryCacheImageInputStream(in));
    } catch(IOException ex) {
        System.err.println("Underlay image " + imageName + " not found in " + archive.getName());
        return new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
    }

    Point pos = new Point(0, 0);
    pos = board.getCropBounds().getLocation();
    boundaries.setSize(board.bounds().getSize());
    Image boardBase = board.getBaseImage();
    boundaries.setLocation(pos.x, pos.y);
    BufferedImage base = new BufferedImage(boardBase.getWidth(null),boardBase.getHeight(null),BufferedImage.TYPE_INT_ARGB);
    base.getGraphics().drawImage(boardBase, 0, 0, null);
    new HolePunch(transparentList, 0).transform(base);
    BufferedImage replacement = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(
        boundaries.width, boundaries.height, Transparency.BITMASK);
    Graphics2D g2 = replacement.createGraphics();
    int w = underlayImage.getWidth(null);
    int h = underlayImage.getHeight(null);
    for (int x = 0; x < boundaries.width; x += w)
      for (int y = 0; y < boundaries.height; y += h)
        g2.drawImage(underlayImage, x, y, null);
    g2.drawImage(base, -pos.x, -pos.y, null);
    g2.dispose();
    new HolePunch(new int[]{0}).transform(replacement);
    return replacement;
  }

  /**
   * Takes an image and turns a list of colors transparent
   */
  private static class HolePunch {
    int transparent[]; /* List of colors to turn transparent (and red) */
    int mask = -1; /* All other colors become this (unchanged if -1) */

    public HolePunch(int trans[], int mask) {
      transparent = trans;
      this.mask = mask;
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

    public void transform(BufferedImage image) {
      final int h = image.getHeight();
      final int[] row = new int[image.getWidth()];
      for (int y = 0; y < h; ++y) {
        image.getRGB(0, y, row.length, 1, row, 0, row.length);
        for (int x = 0; x < row.length; ++x) {
          row[x] = filterRGB(x, y, row[x]);
        }
        image.setRGB(0, y, row.length, 1, row, 0, row.length);
      }
    }
  }
}
