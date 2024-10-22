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
import VASSAL.build.GameModule;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.BoardSlot;
import VASSAL.build.module.map.boardPicker.board.MapGrid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;

public class ASLBoardSlot extends BoardSlot {
  Timer timer;
  private String terrain = "";
  private boolean preservelevels;
  public ASLBoardSlot(ASLBoardPicker bp) {
    super(bp);
    reverseCheckBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        Runnable runnable = new Runnable() {
          public void run() {
            if (board != null) {
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

  public String addOverlay(String ovr, String hex1, String hex2, boolean preservelevels){
    String msg = "";
    this.preservelevels= preservelevels;
    try {
      if (hex1.equals("") && hex2.equals("")) {
        getASLBoard().removeOverlay(ovr);
        msg = "Removed Overlay " + ovr;
      }
      else {
        if (hex2.length() == 0)
          hex2 = hex1;
        if (hex1.length() == 0)
          hex1 = hex2;
        Overlay o;
        try {
          o = new Overlay(ovr + "\t" + hex1 + "\t" + hex2 + "\t" + String.valueOf(this.preservelevels), getASLBoard(),new File(((ASLBoardPicker) picker)
                  .getBoardDir(), "overlays"));
          getASLBoard().addOverlay(o);
          checkOverlap(o);
          msg = "Added Overlay " + o.getName() + " (ver " + o.getVersion() + ")";
        }
        catch (IOException e) {
          if (e.getMessage() != null && e.getMessage().length() > 0) {
            throw new BoardException(e.getMessage());
          }
          e.printStackTrace();
        }
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
    
    // fredkors 14.nov.2013
    // bbox rect is in 'screen' coordinates (only width and height count)
    // while obox rect is in 'map' coordinates
    
    //boolean overlapLeft = obox.x < bbox.x;
    //boolean overlapRight = obox.x + obox.width > bbox.x + bbox.width;
    //boolean overlapTop = obox.y < bbox.y;
    //boolean overlapBottom = obox.y + obox.height > bbox.y + bbox.height;
    
    boolean overlapLeft = obox.x < 0;
    boolean overlapRight = obox.x + obox.width > bbox.width;
    boolean overlapTop = obox.y < 0;
    boolean overlapBottom = obox.y + obox.height > bbox.height;
    
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
      // FredKors 01.01.2015 When an overlay is shared between two maps, the copy added to the other map
      // should report a reference to the 'other' map and not to the getASLBoard()
      o = new Overlay(o.getName() + "\t" + hex1 + "\t" + hex2 + "\t" +String.valueOf(this.preservelevels), otherBoard, new File(((ASLBoardPicker) picker)
              .getBoardDir(), "overlays"));
      otherBoard.addOverlay(o);
    }
    catch (MapGrid.BadCoords ex) {
      GameModule.getGameModule().warn(ex.getMessage());
    }
    catch (IOException e) {
      e.printStackTrace();
      if (e.getMessage() != null && e.getMessage().length() > 0) {
        GameModule.getGameModule().warn(e.getMessage());
      }
    }
    catch (BoardException e) {
      e.printStackTrace();
    }
  }

  // In order to increase the responsiveness of the board picker, we delay the execution
  // of setBoard by 750 milliseconds. This prevents the board picker from updating the
  // board every time the user selects a new board from the drop-down list while scrolling through the list.
  @Override
  public void actionPerformed(ActionEvent e) {
    if (boards.getSelectedItem().equals("Select board")) {
      setBoard(null);
    } else {
      final String selectedBoard = (String) boards.getSelectedItem();
      if (selectedBoard != null) {
        // Cancel any existing timer
        if (timer != null && timer.isRunning()) {
          timer.stop();
        }

        // Delay the execution of setBoard by 750 milliseconds
        timer = new Timer(750, new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            Board b = picker.getLocalizedBoard(selectedBoard);
            if (picker.getBoardsFromControls().contains(b)) {
              b = b.copy();
            }
            setBoard(b);
          }
        });
        timer.setRepeats(false); // Ensure the timer only runs once
        timer.start();
      }
    }
  }
}
