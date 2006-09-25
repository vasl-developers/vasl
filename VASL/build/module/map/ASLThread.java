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

import VASSAL.build.module.map.LOS_Thread;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;

public class ASLThread extends LOS_Thread {

  protected void launch() {
    super.launch();
    setGridSnapToVertex(true);
  }

  public void mouseReleased(java.awt.event.MouseEvent e) {
    super.mouseReleased(e);
    if (!isVisible()) {
      setGridSnapToVertex(false);
    }
  }

  private void setGridSnapToVertex(boolean toVertex) {
    for (java.util.Enumeration e = map.getAllBoards();
         e.hasMoreElements();) {
      HexGrid grid =
          (HexGrid)
          ((Board) e.nextElement()).getGrid();
      grid.setCornersLegal(toVertex);
      grid.setEdgesLegal(!toVertex);
    }
  }
}
