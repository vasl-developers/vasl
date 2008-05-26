package VASL.build.module.map.boardPicker;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Enumeration;

import VASSAL.tools.imageop.AbstractTiledOpImpl;
import VASSAL.tools.imageop.ImageOp;
import VASSAL.tools.imageop.SourceOp;
import VASSAL.tools.imageop.SourceTileOpBitmapImpl;

/*
 *
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
/**
 * ImageOp that draws overlays on top of a board image
 * @author rkinney
 */
public class ApplyOverlaysOp extends AbstractTiledOpImpl implements SourceOp {
  private ImageOp boardImage;
  private ASLBoard board;
  public ApplyOverlaysOp(ASLBoard board, ImageOp boardImage) {
    super();
    this.boardImage = boardImage;
    this.board = board;
  }

  @Override
  protected ImageOp createTileOp(int tileX, int tileY) {
    return new SourceTileOpBitmapImpl(this, tileX, tileY);
  }

  @Override
  public Image apply() throws Exception {
    Component comp = new Component() {};
    Image image = boardImage.getImage(null);
    Graphics g = null;
    for (Enumeration e = board.getOverlays(); e.hasMoreElements();) {
      if (g == null) {
        g = image.getGraphics();
      }
      Overlay o = (Overlay) e.nextElement();
      o.readData();
      o.setImage(board, comp);
      g.drawImage(o.getImage(), o.bounds().x, o.bounds().y, comp);
      if (o.getTerrain() != board.getTerrain() && o.getTerrain() != null) {
        for (SSROverlay ssrOverlay : o.getTerrain().getOverlays()) {
          ssrOverlay.setImage(board, o, comp);
          if (ssrOverlay.getImage() != null) {
            Rectangle r = ssrOverlay.bounds();
            if (o.getOrientation(board) == 'a') {
              g.drawImage(ssrOverlay.getImage(), r.x + o.bounds().x, r.y + o.bounds().y, comp);
            }
            else {
              try {
                Point p1 = o.offset(o.getOrientation(board), board);
                Point p2 = o.offset('a', board);
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
    return image;
  }

  @Override
  protected void fixSize() {
    size = boardImage.getSize();

    tileSize = new Dimension(256,256);

    numXTiles = (int) Math.ceil((double)size.width/tileSize.width);
    numYTiles = (int) Math.ceil((double)size.height/tileSize.height);

    tiles = new ImageOp[numXTiles*numYTiles];
  }

  public ImageOp getSource() {
    return null;
  }

  public String getName() {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ApplyOverlaysOp)) return false;
    ApplyOverlaysOp op = (ApplyOverlaysOp) obj;
    return board.getState().equals(op.board.getState());
  }

  @Override
  public int hashCode() {
    return board.getState().hashCode();
  }
}
