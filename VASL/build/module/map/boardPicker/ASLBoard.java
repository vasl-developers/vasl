/*
 * $Id$
 *
 * Copyright (c) 2000-2004 by Rodney Kinney
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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JLabel;

import VASL.build.module.map.boardPicker.board.ASLHexGrid;
import VASSAL.build.GameModule;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid;
import VASSAL.tools.DataArchive;

/** A Board is a geomorphic or HASL board. */
public class ASLBoard extends Board {
  public String version = "0.0";
  private Rectangle cropBounds = new Rectangle(0, 0, -1, -1);
  private Dimension uncroppedSize;
  /** The image before cropping, overlays, and terrain changes */
  private Image baseImage;
  private Vector overlays = new Vector();
  private String terrainChanges = "";
  private SSRFilter terrain;
  private File boardFile;

  public ASLBoard() {
    setGrid(new ASLHexGrid(64.5, false));
    ((HexGrid) getGrid()).setHexWidth(56.25);
    ((HexGrid) getGrid()).setEdgesLegal(true);
    reversible = true;
  }

  public Rectangle getCropBounds() {
    return cropBounds;
  }

  /**
   * *
   * 
   * @return the size of the board if it were not cropped
   */
  public Dimension getUncroppedSize() {
    return uncroppedSize;
  }

  public Image getBaseImage() {
    return baseImage;
  }

  public Enumeration getOverlays() {
    return overlays.elements();
  }

  public SSRFilter getTerrain() {
    return terrain;
  }

  public String getCommonName() {
    return getConfigureName();
  }

  public void setCommonName(String s) {
    setConfigureName(s);
  }

  public String getLocalizedName() {
    return getConfigureName();
  }

  public String getBaseImageFileName() {
    return imageFile;
  }

  public void setBaseImageFileName(String s) {
    imageFile = s;
  }

  public void setFile(File f) {
    boardFile = f;
  }

  public File getFile() {
    return boardFile;
  }

  public void setTerrain(String changes) throws BoardException {
    terrainChanges = changes;
    terrain = null;
    if (changes == null) {
      return;
    }
    for (int i = 0; i < overlays.size(); ++i) {
      if ((Overlay) overlays.elementAt(i) instanceof SSROverlay)
        overlays.removeElementAt(i--);
    }
    for (int i = 0; i < overlays.size(); ++i) {
      ((Overlay) overlays.elementAt(i)).setTerrain(terrain);
    }
    if (changes.length() > 0) {
      terrain = new SSRFilter(changes, boardFile);
      for (int i = 0; i < overlays.size(); ++i) {
        ((Overlay) overlays.elementAt(i)).setTerrain(terrain);
      }
      for (Enumeration e = terrain.getOverlays(); e.hasMoreElements();) {
        SSROverlay o = (SSROverlay) e.nextElement();
        o.setFile(getFile()); /* Insert terrain overlays always before ordinary overlays */
        overlays.insertElementAt(o, 0);
      }
    }
    baseImage = null;
  }

  public String getVersion() {
    return version;
  }

  public void readData() {
    try {
      InputStream in = DataArchive.getFileStream(boardFile, "data");
      BufferedReader file = new BufferedReader(new InputStreamReader(in));
      String s;
      while ((s = file.readLine()) != null) {
        parseDataLine(s);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected void parseDataLine(String s) {
    StringTokenizer st = new StringTokenizer(s);
    if (st.countTokens() >= 2) {
      String s1 = st.nextToken().toLowerCase();
      if ("version".equals(s1)) {
        version = st.nextToken();
      }
      else {
        ((HexGrid) getGrid()).setAttribute(s1, st.nextToken());
      }
    }
  }

  public void fixImage() {
    if (baseImage != null || boardFile == null) {
      return;
    }
    Component comp = new JLabel();
    Cleanup.init();
    Cleanup.getInstance().addBoard(this);
    try {
      if (boardFile.getName().equals(imageFile)) {
        baseImage = DataArchive.getImage(new FileInputStream(boardFile));
      }
      else {
        baseImage = DataArchive.getImage(DataArchive.getFileStream(boardFile, imageFile));
      }
    }
    catch (java.io.IOException notFound) {
      notFound.printStackTrace();
      baseImage = null;
      javax.swing.JOptionPane.showMessageDialog(null, "Board image " + imageFile + " not found in " + boardFile, "Not Found",
          javax.swing.JOptionPane.ERROR_MESSAGE);
      return;
    }
    for (int i = 0; i < overlays.size(); ++i)
      ((Overlay) overlays.elementAt(i)).readData();
    MediaTracker track = new MediaTracker(comp);
    try {
      track.addImage(baseImage, 0);
      track.waitForID(0);
    }
    catch (Exception eWaitMain2) {
    }
    Image im = terrain == null ? baseImage : terrain.recolor(baseImage, comp);
    try {
      track.addImage(im, 0);
      track.waitForID(0);
    }
    catch (Exception eWaitMain2) {
    }
    boundaries.setSize(cropBounds.width > 0 ? cropBounds.width : baseImage.getWidth(comp), cropBounds.height > 0 ? cropBounds.height : baseImage
        .getHeight(comp));
    fixedBoundaries = true;
    uncroppedSize = new Dimension(baseImage.getWidth(comp), baseImage.getHeight(comp));
    if (terrain == null && overlays.size() == 0 && cropBounds.width < 0 && cropBounds.height < 0) {
      boardImage = baseImage;
      return;
    }
    boardImage = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(boundaries.width,
        boundaries.height, Transparency.BITMASK);
    Graphics2D g = ((BufferedImage) boardImage).createGraphics();
    g.translate(-cropBounds.x, -cropBounds.y);
    g.drawImage(im, 0, 0, comp);
    for (int i = 0; i < overlays.size(); ++i) {
      Overlay o = (Overlay) overlays.elementAt(i);
      o.setImage(this, comp);
      g.drawImage(o.getImage(), o.bounds().x, o.bounds().y, comp);
      if (o.getTerrain() != terrain && o.getTerrain() != null) {
        SSROverlay ssrOverlay;
        for (Enumeration e = o.getTerrain().getOverlays(); e.hasMoreElements();) {
          ssrOverlay = (SSROverlay) e.nextElement();
          ssrOverlay.setImage(this, o, comp);
          if (ssrOverlay.getImage() != null) {
            Rectangle r = ssrOverlay.bounds();
            if (o.getOrientation(this) == 'a') {
              g.drawImage(ssrOverlay.getImage(), r.x + o.bounds().x, r.y + o.bounds().y, comp);
            }
            else {
              try {
                Point p1 = o.offset(o.getOrientation(this), this);
                Point p2 = o.offset('a', this);
                Point p = new Point(p1.x + p2.x + o.bounds().x, p1.y + p2.y + o.bounds().y);
                p.translate(-r.x, -r.y);
                g.drawImage(ssrOverlay.getImage(), p.x, p.y, p.x - r.width, p.y - r.height, 0, 0, r.width, r.height, comp);
              }
              catch (BoardException e1) {
                e1.printStackTrace();
              }
            }
          }
        }
      }
    }
    im = null;
    g.dispose();
    System.gc();
  }

  public void fixBounds() {
    fixImage();
  }

  public static String archiveName(String s) {
    return "bd" + s.toUpperCase();
  }

  public static String fileName(String s) {
    return "bd" + s + ".gif";
  }

  public void addOverlay(Overlay o) {
    overlays.addElement(o);
    baseImage = null;
  }

  public boolean removeOverlay(String s) {
    boolean changed = false;
    for (int i = 0; i < overlays.size(); ++i) {
      Overlay o = (Overlay) overlays.elementAt(i);
      if (o.name.equals(s)) {
        overlays.removeElementAt(i--);
        changed = true;
      }
    }
    return changed;
  }

  public void setCropBounds(Rectangle r) {
    cropBounds = new Rectangle(r);
    baseImage = null;
  }

  public void crop(String row1, String row2, String coord1, String coord2) throws MapGrid.BadCoords {
    crop(row1, row2, coord1, coord2, true);
  }

  public void crop(String row1, String row2, String coord1, String coord2, boolean nearestFullRow) throws MapGrid.BadCoords {
    double dx = ((HexGrid) getGrid()).getHexWidth();
    double dy = ((HexGrid) getGrid()).getHexSize();
    cropBounds = new Rectangle(0, 0, -1, -1);
    cropBounds.x = (row1.length() == 0 ? 0 : getGrid().getLocation(row1 + "0").x);
    cropBounds.width = row2.length() == 0 ? -1 : (getGrid().getLocation(row2 + "0").x - cropBounds.x);
    cropBounds.y = coord1.length() == 0 ? 0 : (getGrid().getLocation("a" + coord1).y - (int) (dy / 2));
    cropBounds.height = coord2.length() == 0 ? -1 : (getGrid().getLocation("a" + coord2).y + (int) (dy / 2) - cropBounds.y);
    if (baseImage == null) {
      System.err.println("Cropping with null base image");
      return;
    }
    int baseWidth = baseImage.getWidth(GameModule.getGameModule().getFrame());
    if (nearestFullRow) {
      if (cropBounds.width > 0 && Math.abs(cropBounds.x + cropBounds.width - baseWidth) > dx / 4) {
        cropBounds.width += (int) (dx / 2);
      }
      if (cropBounds.x != 0) {
        cropBounds.x -= (int) (dx / 2);
        cropBounds.width += (int) (dx / 2);
      }
    }
    baseImage = null;
  }

  public String locationName(Point p) {
    if (getMap() != null && getMap().getBoardCount() > 1) {
      return getName() + super.locationName(p);
    }
    else {
      return super.locationName(p);
    }
  }

  public Point localCoordinates(Point p1) {
    Point p = new Point(p1.x, p1.y);
    if (reversed) {
      p.x = bounds().width - p.x;
      p.y = bounds().height - p.y;
    }
    p.translate(cropBounds.x, cropBounds.y);
    return p;
  }

  public Point globalCoordinates(Point input) {
    Point p = new Point(input);
    p.translate(-cropBounds.x, -cropBounds.y);
    if (reversed) {
      p.x = bounds().width - p.x;
      p.y = bounds().height - p.y;
    }
    return p;
  }

  /**
   * Transform from local board coordinates to local coordinates on the uncropped board
   */
  public Point uncroppedCoordinates(Point p) {
    return p;
  }

  public Point snapToVertex(Point p) {
    return globalCoordinates(((HexGrid) getGrid()).snapToHexVertex(localCoordinates(p)));
  }

  public String getState() {
    String val = relativePosition().x + "\t" + relativePosition().y + "\t" + (reversed ? "r" : "") + imageFile.substring(2, imageFile.indexOf(".gif")) + "\t";
    if (cropBounds.width > 0 || cropBounds.height > 0)
      val += cropBounds.x + "\t" + cropBounds.y + "\t" + cropBounds.width + "\t" + cropBounds.height + "\t";
    for (int i = 0; i < overlays.size(); ++i) {
      val += ((Overlay) overlays.elementAt(i)) + "\t";
    }
    if (terrainChanges.length() > 0)
      val += "SSR\t" + terrainChanges;
    return val;
  }
}
