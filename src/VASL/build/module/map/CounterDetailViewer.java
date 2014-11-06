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
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.i18n.Resources;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JComponent;

public class CounterDetailViewer extends VASSAL.build.module.map.CounterDetailViewer {
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
  protected void fixBounds(List<GamePiece> pieces) {
    for (GamePiece piece : pieces) {
      final Dimension pieceBounds = getBounds(piece).getSize();
      bounds.width += (int) Math.round(pieceBounds.width * graphicsZoomLevel) + borderWidth;
      bounds.height = Math.max(bounds.height, (int) Math.round(pieceBounds.height * graphicsZoomLevel) + borderWidth * 2);
    }

    bounds.height = Math.max(bounds.height, (int)(DEFAULT_HEX_HEIGHT * 1.2) + borderWidth * 2);
    bounds.width += borderWidth * 2 + DEFAULT_HEX_HEIGHT * 1.2;
    bounds.y -= bounds.height;
  }
  
  @Override
    protected void drawGraphics(Graphics g, Point pt, JComponent comp, List<GamePiece> pieces) {

    Object owner = null;

    fixBounds(pieces);

    if (bounds.width > 0) {

      Rectangle visibleRect = comp.getVisibleRect();
      bounds.x = Math.min(bounds.x, visibleRect.x + visibleRect.width - bounds.width);
      if (bounds.x < visibleRect.x)
        bounds.x = visibleRect.x;
      bounds.y = Math.min(bounds.y, visibleRect.y + visibleRect.height - bounds.height) - (isTextUnderCounters() ? 15 : 0);
      int minY = visibleRect.y + (textVisible ? g.getFontMetrics().getHeight() + 6 : 0);
      if (bounds.y < minY)
        bounds.y = minY;

      if (bgColor != null) {
        g.setColor(bgColor);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }
      if (fgColor != null) {
        g.setColor(fgColor);
        g.drawRect(bounds.x - 1, bounds.y - 1, bounds.width + 1, bounds.height + 1);
        g.drawRect(bounds.x - 2, bounds.y - 2, bounds.width + 3, bounds.height + 3);
      }
      Shape oldClip = g.getClip();

      int borderOffset = borderWidth;
      double dMapIconWidth = DEFAULT_HEX_HEIGHT * 1.2;

      // get coordinates of the stack
      Point ptPosition = map.mapCoordinates(currentMousePosition.getPoint());
      
      if (!pieces.isEmpty())
      {
        GamePiece topPiece = pieces.get(0);
        ptPosition = topPiece.getPosition();        
      }

      // get the icon of the map
      BufferedImage imgMapIcon = ((ASLMap)map).getImgMapIcon(ptPosition, dMapIconWidth);
      
      // draw the image 
      g.setClip(bounds.x - 3, bounds.y - 3, bounds.width + 5, bounds.height + 5);
      g.drawImage(imgMapIcon, bounds.x + borderWidth, bounds.y + borderWidth, null);
      g.setColor(fgColor);
      g.drawRect(bounds.x + borderWidth, bounds.y + borderWidth, imgMapIcon.getWidth(), imgMapIcon.getHeight());
      g.setClip(oldClip);
      bounds.translate((int) (dMapIconWidth), 0);
      borderOffset += borderWidth;
      
      // draw pieces
      double graphicsZoom = graphicsZoomLevel;
      for (int i = 0; i < pieces.size(); i++) {
        // Draw the next piece
        // pt is the location of the left edge of the piece
        GamePiece piece = pieces.get(i);
        Rectangle pieceBounds = getBounds(piece);
        if (unrotatePieces) piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.TRUE);
        g.setClip(bounds.x - 3, bounds.y - 3, bounds.width + 5, bounds.height + 5);
        final Stack parent = piece.getParent();
        if (parent instanceof Deck) {
          owner = piece.getProperty(Properties.OBSCURED_BY);
          final boolean faceDown = ((Deck) parent).isFaceDown();
          piece.setProperty(Properties.OBSCURED_BY, faceDown ? Deck.NO_USER : null);
        }
        piece.draw(g, bounds.x - (int) (pieceBounds.x * graphicsZoom) + borderOffset, bounds.y - (int) (pieceBounds.y * graphicsZoom) + borderWidth, comp,
            graphicsZoom);
        if (parent instanceof Deck) piece.setProperty(Properties.OBSCURED_BY, owner);
        if (unrotatePieces) piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.FALSE);
        g.setClip(oldClip);

        if (isTextUnderCounters()) {
          String text = counterReportFormat.getLocalizedText(piece);
          if (text.length() > 0) {
            int x = bounds.x - (int) (pieceBounds.x * graphicsZoom) + borderOffset;
            int y = bounds.y + bounds.height + 10;
            drawLabel(g, new Point(x, y), text, Labeler.CENTER, Labeler.CENTER);
          }
        }

        bounds.translate((int) (pieceBounds.width * graphicsZoom), 0);
        borderOffset += borderWidth;
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
    String report = "";
    int x = bounds.x - bounds.width;
    int y = bounds.y - 5;
    String offboard = Resources.getString("Map.offboard");  //$NON-NLS-1$

    if (displayablePieces.isEmpty()) {
      Point mapPt = map.mapCoordinates(currentMousePosition.getPoint());
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
      x += borderWidth * (pieces.size() + 1) + 2;
    }

    if (report.length() > 0) {
      drawLabel(g, new Point(x, y), report, Labeler.RIGHT, Labeler.BOTTOM);
    }
  }
}
