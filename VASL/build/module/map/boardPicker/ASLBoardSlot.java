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

import VASL.build.module.map.ASLBoardPicker;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.BoardSlot;
import VASSAL.build.module.map.boardPicker.board.MapGrid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;

public class ASLBoardSlot extends BoardSlot {
  private String terrain = "";

  public ASLBoardSlot(ASLBoardPicker bp) {
    super(bp);
    reverseCheckBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Runnable runnable = new Runnable() {
          public void run() {
            if (board != null) {
              board.fixImage();
              picker.repaint();
            }
          }
        };
        SwingUtilities.invokeLater(runnable);
      }
    });
  }

  public void setTerrain(String s) {
    terrain = s;
  }

  public String getTerrain() {
    return terrain;
  }

  public String addOverlay(String ovr, String hex1, String hex2) {
    String msg = "";
    try {
      if (hex1.equals("") && hex2.equals("")) {
        if (getASLBoard().removeOverlay(ovr)) {
          board.fixImage();
        }
        msg = "Removed Overlay " + ovr;
      }
      else {
        if (hex2.length() == 0)
          hex2 = hex1;
        if (hex1.length() == 0)
          hex1 = hex2;
        Overlay o = new Overlay(ovr + "\t" + hex1 + "\t" + hex2);
        o.setFile(new File(new File(((ASLBoardPicker) picker)
                                    .getBoardDir(), "overlays"),
                           o.archiveName()));
        o.check(getASLBoard());
        getASLBoard().addOverlay(o);
        board.fixImage();
        checkOverlap(o);
        msg = "Added Overlay " + o.getName() + " (ver " + o.getVersion() + ")";
      }
    }
    catch (BoardException e) {
      msg = e.getMessage();
    }
    picker.repaintAll();
    return msg;
  }

  public void setBoard(Board b) {
    super.setBoard(b);
    if (b != null
        && ((ASLBoard) b).getTerrain() != null) {
      setTerrain(((ASLBoard) b).getTerrain().toString());
    }
  }

  private ASLBoard getASLBoard() {
    return (ASLBoard) board;
  }

  public void checkOverlap(Overlay o) throws BoardException {
    if (o instanceof SSROverlay) {
      return;
    }
    Rectangle bbox = getBoard().bounds();
    Rectangle obox = new Rectangle(board.globalCoordinates(o.bounds().getLocation()), o.bounds().getSize());
    if (board.isReversed()) {
      obox.translate(-obox.width, -obox.height);
    }
    boolean overlapLeft = obox.x < bbox.x;
    boolean overlapRight = obox.x + obox.width > bbox.x + bbox.width;
    boolean overlapTop = obox.y < bbox.y;
    boolean overlapBottom = obox.y + obox.height > bbox.y + bbox.height;
    if (overlapLeft) {
      if (overlapTop) {
        overlap(o, -1, -1);
      }
      overlap(o, -1, 0);
      if (overlapBottom) {
        overlap(o, -1, 1);
      }
    }
    if (overlapRight) {
      if (overlapTop) {
        overlap(o, 1, -1);
      }
      overlap(o, 1, 0);
      if (overlapBottom) {
        overlap(o, 1, 1);
      }
    }
    if (overlapTop) {
      overlap(o, 0, -1);
    }
    if (overlapBottom) {
      overlap(o, 0, 1);
    }
  }

  private void overlap(Overlay o, int dx, int dy) {
    BoardSlot slot = picker.getNeighbor(this, dx, dy);
    if (slot == null || slot.getBoard() == null) {
      return;
    }
    ASLBoard otherBoard = (ASLBoard) slot.getBoard();
    int offX = 0;
    int offY = 0;
    switch (dx) {
      case -1:
        offX = otherBoard.bounds().width;
        break;
      case 1:
        offX = -board.bounds().width;
    }
    switch (dy) {
      case -1:
        offY = otherBoard.bounds().height;
        break;
      case 1:
        offY = -board.bounds().height;
    }
    try {
      Point p = getASLBoard().globalCoordinates(board.getGrid().getLocation(o.hex1));
      p.translate(offX, offY);
      p = otherBoard.localCoordinates(p);
      String hex1 = otherBoard.getGrid().locationName(p);
      p = getASLBoard().globalCoordinates(board.getGrid().getLocation(o.hex2));
      p.translate(offX, offY);
      p = otherBoard.localCoordinates(p);
      String hex2 = otherBoard.getGrid().locationName(p);
      o = new Overlay(o.getName() + "\t" + hex1 + "\t" + hex2);
      o.setFile(new File(new File(((ASLBoardPicker) picker)
                                  .getBoardDir(), "overlays"),
                         o.archiveName()));
      otherBoard.addOverlay(o);
      otherBoard.fixImage();
    }
    catch (MapGrid.BadCoords ex) {
      ex.printStackTrace();
    }
  }
}
