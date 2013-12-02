/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.build.module.map;

import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.KeyBufferer;
import VASSAL.counters.ColoredBorder;
import VASSAL.counters.Deck;
import VASSAL.counters.DeckVisitor;
import VASSAL.counters.EventFilter;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Immobilized;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.PieceFinder;
import VASSAL.counters.PieceVisitorDispatcher;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

/**
 *
 * @author Federico
 * Added by FredKors 30/11/2013 : Filter INVISIBLE_TO_ME counters
 */
public class ASLKeyBufferer extends KeyBufferer {
    
  @Override
  protected PieceVisitorDispatcher createDragSelector(boolean selecting, boolean altDown) 
  {
    return new PieceVisitorDispatcher(new ASLKBDeckVisitor(selecting, altDown));
  }

  public class ASLKBDeckVisitor implements DeckVisitor 
  {
    boolean selecting = false;
    boolean altDown = false;

    public ASLKBDeckVisitor(boolean b, boolean c) {
      selecting = b;
      altDown = c;
    }

    public Object visitDeck(Deck d) {
      return null;
    }

    public Object visitStack(Stack s) {
      if (s.topPiece() != null) {
        if (s.isExpanded()) {
          Point[] pos = new Point[s.getPieceCount()];
          map.getStackMetrics().getContents(s, pos, null, null, s.getPosition().x, s.getPosition().y);
          for (int i = 0; i < pos.length; ++i) {
            if (selection.contains(pos[i]) && !Boolean.TRUE.equals(s.getPieceAt(i).getProperty(Properties.INVISIBLE_TO_ME))) {
              if (selecting) {
                KeyBuffer.getBuffer().add(s.getPieceAt(i));
              }
              else {
                KeyBuffer.getBuffer().remove(s.getPieceAt(i));
              }
            }
          }
        }
        else if (selection.contains(s.getPosition())) {
          for (int i = 0, n = s.getPieceCount(); i < n; ++i) {
            if (!Boolean.TRUE.equals(s.getPieceAt(i).getProperty(Properties.INVISIBLE_TO_ME))) 
                if (selecting) {
                  KeyBuffer.getBuffer().add(s.getPieceAt(i));
                }
                else {
                  KeyBuffer.getBuffer().remove(s.getPieceAt(i));
                }
          }
        }
      }
      return null;
    }

    // Handle non-stacked units, including Does Not Stack units
    // Does Not Stack units deselect normally once selected
    public Object visitDefault(GamePiece p) {
      if (selection.contains(p.getPosition()) && !Boolean.TRUE.equals(p.getProperty(Properties.INVISIBLE_TO_ME))) {
        if (selecting) {
          final EventFilter filter = (EventFilter) p.getProperty(Properties.SELECT_EVENT_FILTER);
          final boolean altSelect = (altDown && filter instanceof Immobilized.UseAlt);
          if (filter == null || altSelect) {
            KeyBuffer.getBuffer().add(p);
          }
        }
        else {
          KeyBuffer.getBuffer().remove(p);
        }
      }
      return null;
    }
  }
  
    public void mousePressed(MouseEvent e) {
    if (e.isConsumed()) {
      return;
    }
    GamePiece p = map.findPiece(e.getPoint(), PieceFinder.PIECE_IN_STACK);
    // Don't clear the buffer until we find the clicked-on piece
    // Because selecting a piece affects its visibility
    EventFilter filter = null;
    if (p != null) {
      filter = (EventFilter) p.getProperty(Properties.SELECT_EVENT_FILTER);
    }
    boolean ignoreEvent = filter != null && filter.rejectEvent(e);
    if (p != null && !ignoreEvent) {
      boolean movingStacksPickupUnits = ((Boolean) GameModule.getGameModule().getPrefs().getValue(Map.MOVING_STACKS_PICKUP_UNITS)).booleanValue();
      if (!KeyBuffer.getBuffer().contains(p)) {
        if (!e.isShiftDown() && !e.isControlDown()) {
          KeyBuffer.getBuffer().clear();
        }
        // RFE 1629255 - If the top piece of an unexpanded stack is left-clicked
        // while not selected, then select all of the pieces in the stack
        // RFE 1659481 - Control clicking only deselects
        if (!e.isControlDown()) {
          if (movingStacksPickupUnits || p.getParent() == null || p.getParent().isExpanded() || e.isMetaDown()
              || Boolean.TRUE.equals(p.getProperty(Properties.SELECTED))) {
                if (!Boolean.TRUE.equals(p.getProperty(Properties.INVISIBLE_TO_ME)))
                    KeyBuffer.getBuffer().add(p);
          }
          else {
            Stack s = p.getParent();
            for (int i = 0; i < s.getPieceCount(); i++) {
                if (!Boolean.TRUE.equals(s.getPieceAt(i).getProperty(Properties.INVISIBLE_TO_ME)))
                    KeyBuffer.getBuffer().add(s.getPieceAt(i));
            }
          }
        }
        // End RFE 1629255
      }
      else {
        // RFE 1659481 Ctrl-click deselects clicked units
        if (e.isControlDown() && Boolean.TRUE.equals(p.getProperty(Properties.SELECTED))) {
          Stack s = p.getParent();
          if (s == null) {
            KeyBuffer.getBuffer().remove(p);
          }
          else if (!s.isExpanded()) {
            for (int i = 0; i < s.getPieceCount(); i++) {
              KeyBuffer.getBuffer().remove(s.getPieceAt(i));
            }
          }
        }
        // End RFE 1659481
      }
      if (p.getParent() != null) {
        map.getPieceCollection().moveToFront(p.getParent());
      }
      else {
        map.getPieceCollection().moveToFront(p);
      }
    }
    else {
      if (!e.isShiftDown() && !e.isControlDown()) { // No deselect if shift key down
        KeyBuffer.getBuffer().clear();
      }
      anchor = map.componentCoordinates(e.getPoint());
      selection = new Rectangle(anchor.x, anchor.y, 0, 0);
      if (map.getHighlighter() instanceof ColoredBorder) {
        ColoredBorder b = (ColoredBorder) map.getHighlighter();
        color = b.getColor();
        thickness = b.getThickness();
      }
    }
  }

  
}
