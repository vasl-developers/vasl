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
package VASL.build.module.map;

import CASL.VASL.VASLThread;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.GlobalMap;
import VASSAL.build.module.map.BoardPicker;
import VASSAL.counters.GamePiece;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Allows the user the change boards in a Map window while
 * keeping the positions of all the counters on the map
 */
public class BoardSwapper extends AbstractBuildable {
  private Map map;
  private JButton launch;

  private Vector pieces = new Vector();
  private Vector positions = new Vector();
  private Vector boards = new Vector();

  public void addTo(Buildable b) {
    map = (Map) b;
    launch = new JButton("Change boards");
    launch.setAlignmentY(0.0F);
    launch.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        recordPiecePositions();
        ASLBoardPicker picker = new Picker(map);
        picker.setup(true);
        restorePiecePositions();
        map.repaint();
      }
    });
    try {
      launch.setIcon(new ImageIcon(GameModule.getGameModule()
                                   .getDataArchive().getCachedImage("newBoards.gif")));
      launch.setText("");
      launch.setToolTipText("Pick new boards for this scenario");
    }
    catch (java.io.IOException ex) {
    }
    map.getToolBar().add(launch);
  }

  protected void recordPiecePositions() {
    pieces.removeAllElements();
    positions.removeAllElements();
    boards.removeAllElements();

    GamePiece[] piece = map.getPieces();
    for (int n = 0; n < piece.length; ++n) {
      ASLBoard b = (ASLBoard) map.findBoard(piece[n].getPosition());
      if (b != null) {
        Point p = new Point(piece[n].getPosition().x - b.bounds().x,
                            piece[n].getPosition().y - b.bounds().y);
        p = b.localCoordinates(p);
        pieces.addElement(piece[n]);
        boards.addElement(b.getName());
        positions.addElement(p);
      }
    }
  }

  protected void restorePiecePositions() {
    if (pieces.size() > 0) {
      for (int n = 0; n < pieces.size(); ++n) {
        ASLBoard b = null;
        if (boards.elementAt(n) != null) {
          String boardName = (String) boards.elementAt(n);
          GamePiece piece = (GamePiece) pieces.elementAt(n);
          for (Enumeration e = map.getAllBoards();
               e.hasMoreElements();) {
            ASLBoard board = (ASLBoard) e.nextElement();
            if (boardName.equals(board.getName())) {
              b = board;
              break;
            }
          }
          if (b != null) {
            Point p = b.globalCoordinates((Point) positions.elementAt(n));
            piece.setPosition(new Point(p.x + b.bounds().x,
                                        p.y + b.bounds().y));
          }
        }
      }
    }
  }

  public String[] getAttributeNames() {
    return new String[0];
  }

  public String getAttributeValueString(String attName) {
    return null;
  }

  public void setAttribute(String attName, Object value) {
  }

  private static class Picker extends ASLBoardPicker {
    private Vector oldBoards;

    public Picker(Map m) {
      this.map = m;
      setBoardDir((File) GameModule.getGameModule().getPrefs().getValue(BOARD_DIR));
      initTerrainEditor();
      allowMultiple = true;
      oldBoards = new Vector();
      for (Enumeration e = this.map.getAllBoards(); e.hasMoreElements();) {
        oldBoards.addElement(e.nextElement());
      }
    }

    public void setVisible(boolean b) {
      if (b) {
        setBoards(oldBoards.elements());
      }
      super.setVisible(b);
    }

    public void actionPerformed(ActionEvent e) {
      if ("Cancel".equals(e.getActionCommand())) {
        currentBoards = oldBoards;
        setVisible(false);
      }
      else if ("Ok".equals(e.getActionCommand())) {
        super.actionPerformed(e);
        Runnable runnable = new Runnable() {
          public void run() {
            save();
          }
        };
        SwingUtilities.invokeLater(runnable);
      }
      else {
        super.actionPerformed(e);
      }
    }

    public void save() {
      new BoardPicker.SetBoards(map.getBoardPicker(),currentBoards).execute();
      for (Enumeration e = GameModule.getGameModule().getGameState().getGameComponentsEnum();
           e.hasMoreElements();) {
        Object o = e.nextElement();
        if (o instanceof GlobalMap) {
          ((GlobalMap) o).setup(true);
          break;
        }
      }
      for (Enumeration e = this.map.getComponents(VASLThread.class); e.hasMoreElements();) {
        VASLThread t = (VASLThread) e.nextElement();
        t.setup(false);
        t.setup(true);
      }
    }
  }
}
