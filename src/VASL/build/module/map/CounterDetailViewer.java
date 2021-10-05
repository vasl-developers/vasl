/*
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
package VASL.build.module.map;

import VASL.LOS.Map.Map;
import VASL.build.module.ASLMap;
import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_HEIGHT;
import java.awt.Rectangle;

import VASL.counters.TextInfo;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.mapgrid.Zone;
import VASSAL.build.module.properties.SumProperties;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.Deck;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Labeler;
import VASSAL.tools.image.LabelUtils;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.i18n.Resources;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

public class CounterDetailViewer extends VASSAL.build.module.map.CounterDetailViewer 
{
  final int MAX_NUM_PIECES_PER_ROW = 9;
  final int MAP_ICON_SIZE = (int)(DEFAULT_HEX_HEIGHT * 1.2);
  Point[] ar_Pieces_Pos = null;
    
  @Override
  protected Rectangle getBounds(GamePiece piece) {
    TextInfo info = (TextInfo) Decorator.getDecorator(piece, TextInfo.class);
    if (info != null && info.isInfoShowing()) {
      return piece.boundingBox();
    }
    else {
      return super.getBounds(piece);
    }
  }

  /** Set the bounds field large enough to accommodate the given set of pieces */
  @Override
  protected void fixBounds(List<GamePiece> pieces) 
  {
    int iNumRows = pieces.size() / (MAX_NUM_PIECES_PER_ROW + 1) + 1;
    int iPiecePos, iCurTotalWidth = borderWidth * 2 + MAP_ICON_SIZE, iCurTotalHeight = borderWidth;
    int iMaxTotalWidth = 0;
    int[] ar_iMaxHeigth = new int [iNumRows];
    int[] ar_iMaxWidth = new int [MAX_NUM_PIECES_PER_ROW];
    
    // calculate each row and col max size
    for (int iRow = 0; iRow < iNumRows; iRow++)
    {
        for (int iCol = 0; iCol < MAX_NUM_PIECES_PER_ROW; iCol++)
        {
            iPiecePos = iRow * MAX_NUM_PIECES_PER_ROW + iCol; 
            
            if (iPiecePos < pieces.size())
            {
                GamePiece piece = pieces.get(iPiecePos);
                final Dimension pieceBounds = getBounds(piece).getSize();
                
                if (Math.round(pieceBounds.height * graphicsZoomLevel) > ar_iMaxHeigth[iRow])
                    ar_iMaxHeigth[iRow] = (int)Math.round(pieceBounds.height * graphicsZoomLevel);

                if (Math.round(pieceBounds.width * graphicsZoomLevel) > ar_iMaxWidth[iCol])
                    ar_iMaxWidth[iCol] = (int)Math.round(pieceBounds.width * graphicsZoomLevel);
            }
            else
                break;
        }
    }
    
    // where save the position of each piece  
    ar_Pieces_Pos = new Point[pieces.size()];

    iCurTotalHeight = borderWidth;
    // calculate the pos of each pieces
    for (int iRow = 0; iRow < iNumRows; iRow++)
    {
        iCurTotalWidth = borderWidth * 2 + MAP_ICON_SIZE;
        
        for (int iCol = 0; iCol < MAX_NUM_PIECES_PER_ROW; iCol++)
        {
            iPiecePos = iRow * MAX_NUM_PIECES_PER_ROW + iCol; 
            
            if (iPiecePos < pieces.size())
            {
                GamePiece piece = pieces.get(iPiecePos);
                final Dimension pieceBounds = getBounds(piece).getSize();
                
                ar_Pieces_Pos[iPiecePos] = new Point();
                
                if (Math.round(pieceBounds.width * graphicsZoomLevel) < ar_iMaxWidth[iCol])
                    ar_Pieces_Pos[iPiecePos].x = iCurTotalWidth + ((ar_iMaxWidth[iCol] - (int)Math.round(pieceBounds.width * graphicsZoomLevel)) / 2);
                else
                    ar_Pieces_Pos[iPiecePos].x = iCurTotalWidth;

                if (Math.round(pieceBounds.height * graphicsZoomLevel) < ar_iMaxHeigth[iRow])
                    ar_Pieces_Pos[iPiecePos].y = iCurTotalHeight + ((ar_iMaxHeigth[iRow] - (int)Math.round(pieceBounds.height * graphicsZoomLevel)) / 2);
                else
                    ar_Pieces_Pos[iPiecePos].y = iCurTotalHeight;

                iCurTotalWidth += ar_iMaxWidth[iCol] + borderWidth;
            }
            else
                break;
        }
        
        if (iCurTotalWidth > iMaxTotalWidth)
            iMaxTotalWidth = iCurTotalWidth;
        
        iCurTotalHeight += ar_iMaxHeigth[iRow] + borderWidth + (isTextUnderCounters() ? 15 : 0);
    }
    
    bounds.width += iMaxTotalWidth;
    bounds.height += iCurTotalHeight;
    bounds.height = Math.max(bounds.height, MAP_ICON_SIZE + borderWidth * 2);
    
    bounds.y -= bounds.height;
  }
  
  @Override
    protected void drawGraphics(Graphics g, Point pt, JComponent comp, List<GamePiece> pieces) {
    fixBounds(pieces);

    if (bounds.width <= 0) {
      return;
    }

    final Graphics2D g2d = (Graphics2D) g;
    final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
 
    final Rectangle dbounds = new Rectangle(bounds);
    dbounds.x *= os_scale;
    dbounds.y *= os_scale;
    dbounds.width *= os_scale;
    dbounds.height *= os_scale;

    final Rectangle visibleRect = comp.getVisibleRect();
    visibleRect.x *= os_scale;
    visibleRect.y *= os_scale;
    visibleRect.width *= os_scale;
    visibleRect.height *= os_scale;

    dbounds.x = Math.min(dbounds.x, visibleRect.x + visibleRect.width - dbounds.width - 1);
    if (dbounds.x < visibleRect.x)
      dbounds.x = visibleRect.x;
    
    dbounds.y = Math.min(dbounds.y, visibleRect.y + visibleRect.height - dbounds.height - 1);
    int minY = visibleRect.y + (textVisible ? g.getFontMetrics().getHeight() + 6 : 0);
    
    if (dbounds.y < minY)
      dbounds.y = minY;

    if (bgColor != null) 
    {
      g.setColor(bgColor);
      g.fillRect(dbounds.x, dbounds.y, dbounds.width, dbounds.height);
    }
    
    if (fgColor != null) 
    {
      g.setColor(fgColor);
      g.drawRect(dbounds.x - 1, dbounds.y - 1, dbounds.width + 1, dbounds.height + 1);
      g.drawRect(dbounds.x - 2, dbounds.y - 2, dbounds.width + 3, dbounds.height + 3);
    }
    
    Shape oldClip = g.getClip();

    // draw the map
    final Point ptPosition = pieces.isEmpty() ?
      map.componentToMap(currentMousePosition.getPoint()) :
      pieces.get(0).getPosition();

    // get the icon of the map
    final double isize = MAP_ICON_SIZE * os_scale;
    final BufferedImage imgMapIcon = ((ASLMap)map).getImgMapIcon(ptPosition, isize, os_scale);
    
    // draw the image
    final int dborderWidth = (int)(borderWidth * os_scale);
    g.setClip(dbounds.x - 3, dbounds.y - 3, dbounds.width + 5, dbounds.height + 5);
    g.drawImage(imgMapIcon, dbounds.x + dborderWidth, dbounds.y + dborderWidth, null);
    g.setColor(fgColor);
    g.drawRect(dbounds.x + dborderWidth, dbounds.y + dborderWidth, (int) isize, (int) isize);
    g.drawRect(dbounds.x + dborderWidth, dbounds.y + dborderWidth, imgMapIcon.getWidth(), imgMapIcon.getHeight());
    g.setClip(oldClip);
    
    // draw pieces
    for (int i = 0; i < pieces.size(); i++) 
    {
      if (ar_Pieces_Pos[i] == null)
          continue;
        
      GamePiece piece = pieces.get(i);
      Rectangle pieceBounds = getBounds(piece);
      
      if (unrotatePieces) 
          piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.TRUE);
      
      g.setClip(dbounds.x - 3, dbounds.y - 3, dbounds.width + 5, dbounds.height + 5);

      Object owner = null;
      final Stack parent = piece.getParent();
      
      if (parent instanceof Deck) 
      {
        owner = piece.getProperty(Properties.OBSCURED_BY);
        final boolean faceDown = ((Deck) parent).isFaceDown();
        piece.setProperty(Properties.OBSCURED_BY, faceDown ? Deck.NO_USER : null);
      }

      final int x = dbounds.x + (int)((ar_Pieces_Pos[i].x - (int)(pieceBounds.x * graphicsZoomLevel)) * os_scale);
      final int y = dbounds.y + (int)((ar_Pieces_Pos[i].y - (int)(pieceBounds.y * graphicsZoomLevel)) * os_scale);
      
      piece.draw(g, x, y, comp, graphicsZoomLevel * os_scale);
      
      if (parent instanceof Deck) 
          piece.setProperty(Properties.OBSCURED_BY, owner);
      
      if (unrotatePieces) 
          piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.FALSE);
      
      g.setClip(oldClip);

      if (isTextUnderCounters()) 
      {
        String text = counterReportFormat.getLocalizedText(piece);
        
        if (text.length() > 0) 
        {
          drawLabel(g, new Point(x, y + 5), text, LabelUtils.CENTER, LabelUtils.TOP);
        }
      }
    }
  }

  @Override
  protected void drawText(Graphics g, Point pt, JComponent comp, List<GamePiece> pieces) {
    /*
     * Label with the location If the counter viewer is being displayed, then
     * place the location name just above the left hand end of the counters. If
     * no counter viewer (i.e. single piece or expanded stack), then place the
     * location name above the centre of the first piece in the stack.
     */
    final Graphics2D g2d = (Graphics2D) g;
    final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();

    String report = "";
    int x = (int)(bounds.x * os_scale);
    int y = (int)((bounds.y - 5) * os_scale);
    String offboard = Resources.getString("Map.offboard");  //$NON-NLS-1$

    if (displayablePieces.isEmpty()) {
      Point mapPt = map.componentToMap(currentMousePosition.getPoint());
      Point snapPt = map.snapTo(mapPt);
      String locationName = map.localizedLocationName(snapPt);
      emptyHexReportFormat.setProperty(BasicPiece.LOCATION_NAME, locationName.equals(offboard) ? "" : locationName);
      emptyHexReportFormat.setProperty(BasicPiece.CURRENT_MAP, map.getLocalizedMapName());
      Board b = map.findBoard(snapPt);
      String boardName = (b == null) ? "" : b.getLocalizedName();
      emptyHexReportFormat.setProperty(BasicPiece.CURRENT_BOARD, boardName);
      Zone z = map.findZone(snapPt);
      String zone = (z == null) ? "" : z.getLocalizedName();
      emptyHexReportFormat.setProperty(BasicPiece.CURRENT_ZONE, zone);
      report = emptyHexReportFormat.getLocalizedText();
      x -= g.getFontMetrics().stringWidth(report) / 2;
    }
    else {
      GamePiece topPiece = displayablePieces.get(0);
      String locationName = (String) topPiece.getLocalizedProperty(BasicPiece.LOCATION_NAME);
      emptyHexReportFormat.setProperty(BasicPiece.LOCATION_NAME, locationName.equals(offboard) ? "" : locationName);
      report = summaryReportFormat.getLocalizedText(new SumProperties(displayablePieces));
      report += " Base Level " + getBaseLevel(report);
      x -= borderWidth * os_scale;
    }

    if (report.length() > 0) {
      drawLabel(g, new Point(x, y), report, LabelUtils.RIGHT, LabelUtils.BOTTOM);
    }
  }
  private String getBaseLevel(String hexname){
      VASL.build.module.ASLMap aslmap = (ASLMap) map;
      VASL.LOS.Map.Map vaslmap = aslmap.getVASLMap();
      String baselevel = Integer.toString(vaslmap.getHex(hexname).getBaseHeight());
      return baselevel;
  }
}
