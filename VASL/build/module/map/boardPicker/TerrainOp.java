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
package VASL.build.module.map.boardPicker;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;

import VASSAL.tools.imageop.AbstractTiledOpImpl;
import VASSAL.tools.imageop.ImageOp;
import VASSAL.tools.imageop.SourceOp;
import VASSAL.tools.imageop.SourceTileOpBitmapImpl;


/**
 * ImageOp that applies terrain-transformation recolorings to a board image 
 * @author rkinney
 */
public class TerrainOp extends AbstractTiledOpImpl implements SourceOp {
  private SourceOp boardImage;
  private SSRFilter terrain;
  
  public TerrainOp(SourceOp boardImage, SSRFilter terrain) {
    super();
    this.boardImage = boardImage;
    this.terrain = terrain;
  }

  @Override
  protected ImageOp createTileOp(int tileX, int tileY) {
    return new SourceTileOpBitmapImpl(this, tileX, tileY);
  }

  @Override
  public Image apply() throws Exception {
    Image image = boardImage.getImage(null);
    if (terrain != null) {
      image = terrain.apply((BufferedImage)image);
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
    return boardImage.getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TerrainOp)) return false;
    TerrainOp op = (TerrainOp) obj;
    return boardImage.equals(op.boardImage) && (terrain == null ? op.terrain == null : terrain.equals(op.terrain));
  }

  @Override
  public int hashCode() {
    return boardImage.hashCode();
  }
  
  
}
