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
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

import VASL.build.module.map.boardPicker.board.ASLHexGrid;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid;
import VASSAL.tools.DataArchive;
import VASSAL.tools.ImageUtils;
import VASSAL.tools.imageop.AbstractTiledOpImpl;
import VASSAL.tools.imageop.ImageOp;
import VASSAL.tools.imageop.SourceOp;
import VASSAL.tools.imageop.SourceOpBitmapImpl;
import VASSAL.tools.imageop.SourceTileOpBitmapImpl;

/** A Board is a geomorphic or HASL board. */
public class ASLBoard extends Board {
  public String version = "0.0";
  private Rectangle cropBounds = new Rectangle(0, 0, -1, -1);
  private Dimension uncroppedSize;
  private List<Overlay> overlays = new ArrayList();
  private String terrainChanges = "";
  private SSRFilter terrain;
  private File boardFile;
  private DataArchive boardArchive;

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
    try {
      return new SourceOpBitmapImpl(imageFile,boardArchive).getImage(null);
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public Enumeration getOverlays() {
    return Collections.enumeration(overlays);
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
    resetImage();
  }

  public void setFile(File f) {
    boardFile = f;
    try {
      boardArchive = new DataArchive(boardFile.getPath(),"");
    }
    catch (IOException e) {
      throw new IllegalArgumentException("Cannot open file "+boardFile.getPath());
    }
    resetImage();
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
      if ((Overlay) overlays.get(i) instanceof SSROverlay)
        overlays.remove(i--);
    }
    if (changes.length() > 0) {
      terrain = new SSRFilter(changes, boardFile, this);
      for (SSROverlay o : terrain.getOverlays()) {
        overlays.add(0, o);
      }
    }
    resetImage();
  }

  public String getVersion() {
    return version;
  }

  public void readData() {
    try {
      InputStream in = boardArchive.getFileStream("data");
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

  protected void resetImage() {
    boardImageOp = new BoardOp();
    if (boardArchive != null) {
      uncroppedSize = new SourceOpBitmapImpl(imageFile, boardArchive).getSize();
    }
    fixedBoundaries = false;
    scaledImageOp = null;
  }
  
  public static String archiveName(String s) {
    return "bd" + s.toUpperCase();
  }

  public static String fileName(String s) {
    return "bd" + s + ".gif";
  }

  public void addOverlay(Overlay o) {
    overlays.add(o);
    resetImage();
  }

  public boolean removeOverlay(String s) {
    boolean changed = false;
    for (int i = 0; i < overlays.size(); ++i) {
      Overlay o = (Overlay) overlays.get(i);
      if (o.name.equals(s)) {
        overlays.remove(i--);
        changed = true;
      }
    }
    if (changed) {
      resetImage();
    }
    return changed;
  }

  public void setCropBounds(Rectangle r) {
    cropBounds = new Rectangle(r);
    resetImage();
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
    if (nearestFullRow) {
      if (cropBounds.width > 0 && Math.abs(cropBounds.x + cropBounds.width - uncroppedSize.width) > dx / 4) {
        cropBounds.width += (int) (dx / 2);
      }
      if (cropBounds.x != 0) {
        cropBounds.x -= (int) (dx / 2);
        cropBounds.width += (int) (dx / 2);
      }
    }
    resetImage();
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
  public Point uncroppedCoordinates(Point input) {
    Point p = new Point(input);
    if (reversed) {
      p.translate(cropBounds.width > 0 ? uncroppedSize.width - cropBounds.x - cropBounds.width : 0, cropBounds.height > 0 ? uncroppedSize.height - cropBounds.y
          - cropBounds.height : 0);
    }
    else {
      p.translate(cropBounds.x, cropBounds.y);
    }
    return p;
  }

  public Point snapToVertex(Point p) {
    return globalCoordinates(((HexGrid) getGrid()).snapToHexVertex(localCoordinates(p)));
  }

  public String getState() {
    String val = relativePosition().x + "\t" + relativePosition().y + "\t" + (reversed ? "r" : "") + imageFile.substring(2, imageFile.indexOf(".gif")) + "\t";
    if (cropBounds.width > 0 || cropBounds.height > 0)
      val += cropBounds.x + "\t" + cropBounds.y + "\t" + cropBounds.width + "\t" + cropBounds.height + "\t";
    for (Overlay o : overlays) {
      val += o+"\t";
    }
    if (terrainChanges.length() > 0)
      val += "SSR\t" + terrainChanges;
    return val;
  }
  
  private class BoardOp extends AbstractTiledOpImpl implements SourceOp {
    private String boardState;
    private BoardOp() {
      boardState = ASLBoard.this.getState();
    }
    @Override
    protected ImageOp createTileOp(int tileX, int tileY) {
      return new SourceTileOpBitmapImpl(this, tileX, tileY);
    }

    @Override
    public Image apply() throws Exception {
      if (size == null) {
        fixSize();
      }
      ImageOp base = new SourceOpBitmapImpl(imageFile,boardArchive);
      if (terrain == null && overlays.isEmpty() && cropBounds.width < 0 && cropBounds.height < 0) {
        return base.getImage(null); 
      }
      BufferedImage im = ImageUtils.createEmptyLargeImage(size.width, size.height);
      Graphics2D g = (Graphics2D) im.getGraphics();
      Rectangle visible = new Rectangle(cropBounds.getLocation(), ASLBoard.this.bounds().getSize());
      g.drawImage(base.getImage(null), 0, 0, visible.width, visible.height, 
            cropBounds.x, cropBounds.y, cropBounds.x + visible.width, cropBounds.y + visible.height, null);
      Component comp = new Component() {};
      for (Enumeration e = ASLBoard.this.getOverlays(); e.hasMoreElements();) {
        Overlay o = (Overlay) e.nextElement();
        Rectangle r = visible.intersection(o.bounds());
        if (!r.isEmpty()) {
          int x = Math.max(visible.x-o.bounds().x, 0);
          int y = Math.max(visible.y-o.bounds().y, 0);
          g.drawImage(o.getImage(), r.x-visible.x, r.y-visible.y, r.x-visible.x +r.width, r.y-visible.y+r.height, 
                x,y,x+r.width, y+r.height, comp);
        }
        if (o.getTerrain() != getTerrain() && o.getTerrain() != null) {
          for (SSROverlay ssrOverlay : o.getTerrain().getOverlays()) {
            if (ssrOverlay.getImage() != null) {
              Rectangle oBounds = ssrOverlay.bounds();
              if (o.getOrientation() == 'a') {
                oBounds.translate(o.bounds().x, o.bounds().y);
                r = visible.intersection(oBounds);
                if (!r.isEmpty()) {
                  int x = Math.max(visible.x-o.bounds().x, 0);
                  int y = Math.max(visible.y-o.bounds().y, 0);
                  g.drawImage(ssrOverlay.getImage(), r.x-visible.x, r.y-visible.y, r.x-visible.x +r.width, r.y-visible.y+r.height, 
                        x,y,x+r.width, y+r.height, comp);
                }
              }
              else {
                try {
                  Point p1 = o.offset(o.getOrientation(), ASLBoard.this);
                  Point p2 = o.offset('a', ASLBoard.this);
                  Point p = new Point(p1.x + p2.x - oBounds.x + o.bounds().x - visible.x, p1.y + p2.y - oBounds.y + o.bounds().y - visible.y);
                  g.drawImage(ssrOverlay.getImage(), p.x, p.y, p.x - oBounds.width, p.y - oBounds.height, 0, 0, oBounds.width, oBounds.height, comp);
                }
                catch (BoardException e1) {
                  e1.printStackTrace();
                }
              }
            }
          }
        }
      }
      if (terrain != null) {
        terrain.transform(im);
      }
      return im;
    }
    
    @Override
    protected void fixSize() {
      size = new Dimension(cropBounds.width > 0 ? cropBounds.width : uncroppedSize.width, cropBounds.height > 0 ? cropBounds.height : uncroppedSize.height);

      tileSize = new Dimension(256,256);

      numXTiles = (int) Math.ceil((double)size.width/tileSize.width);
      numYTiles = (int) Math.ceil((double)size.height/tileSize.height);

      tiles = new ImageOp[numXTiles*numYTiles];
    }

    public ImageOp getSource() {
      return null;
    }

    public String getName() {
      return ASLBoard.this.getName();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ASLBoard.BoardOp)) return false;
      BoardOp op = (BoardOp) obj;
      return boardState.equals(op.boardState);
    }

    @Override
    public int hashCode() {
      return boardState.hashCode();
    }
  }

  public DataArchive getBoardArchive() {
    return boardArchive;
  }
  
}
