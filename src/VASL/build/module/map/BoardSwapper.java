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

import VASL.build.module.ASLMap;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.BoardPicker;
import VASSAL.build.module.map.GlobalMap;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.counters.GamePiece;
import VASSAL.tools.imageop.Op;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Iterator;
import java.util.Vector;

/**
 * Allows the user the change boards in a Map window while
 * keeping the positions of all the counters on the map
 */
public class BoardSwapper extends AbstractBuildable {
  private Map map;
  private JButton launch;
    // menuitem in the ASLMap popup menu
  JMenuItem m_MenuItem = null;

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
        final ASLBoardPicker picker = new Picker(map);
        final JDialog d = new JDialog(GameModule.getGameModule().getPlayerWindow(),true);
        d.getContentPane().setLayout(new BoxLayout(d.getContentPane(),BoxLayout.Y_AXIS));
        d.getContentPane().add(picker.getControls());
        JButton okButton = new JButton("Ok");
        okButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent arg0) {
            d.setVisible(false);
            picker.finish();
          }
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent arg0) {
            d.setVisible(false);
          }
        });
        Box buttonBox = Box.createHorizontalBox();
        buttonBox.add(okButton);
        buttonBox.add(cancelButton);
        d.getContentPane().add(buttonBox);
        d.pack();
        d.setLocationRelativeTo(GameModule.getGameModule().getPlayerWindow());
        d.setVisible(true);
        restorePiecePositions();
        map.repaint();
      }
    });
    try {
      launch.setIcon(new ImageIcon(Op.load("newBoards.gif").getImage(null)));
      launch.setText("");
      launch.setToolTipText("Pick new boards for this scenario");
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    // creates the menuitem 
    m_MenuItem = new JMenuItem(launch.getToolTipText());

    // copy the properties from the jbutton
    m_MenuItem.addActionListener(launch.getListeners(ActionListener.class)[0]);
    m_MenuItem.setIcon(launch.getIcon());
    
    // doesn't add the button to the toolbar
    //map.getToolBar().add(launch);
    // adds the menuitem to the ASLMap popup menu
    ((ASLMap)map).getPopupMenu().add(m_MenuItem);
    
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
          for (Iterator<Board> it = map.getBoards().iterator();
               it.hasNext();) {
            ASLBoard board = (ASLBoard) it.next();
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
    public Picker(Map m) {
      this.map = m;
      allowMultiple = true;
      setBoardDir((File) GameModule.getGameModule().getPrefs().getValue(BOARD_DIR));
    }

    public void finish() {
      super.finish();
      for (Iterator<GameComponent> e = GameModule.getGameModule().getGameState().getGameComponents().iterator();
           e.hasNext();) {
        Object o = e.next();
        if (o instanceof GlobalMap) {
          ((GlobalMap) o).setup(true);
        }
      }
      for (VASLThread t : this.map.getComponentsOf(VASLThread.class)) {
        t.setup(false);
        t.setup(true);
      }
      for (BoardPicker p : this.map.getComponentsOf(BoardPicker.class)) {
        ((ASLBoardPicker)p).setGlobalMapScale();
        new SetBoards(p, currentBoards).execute();
      }
    }

    @Override
    public Component getControls() {
      Component c = super.getControls();
      setBoards(this.map.getBoards());
      return c;
    }
  }
}
