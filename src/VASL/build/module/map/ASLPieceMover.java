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
import VASL.counters.ASLHighlighter;
import VASL.counters.ASLProperties;
import VASL.counters.Concealable;
import VASL.counters.Concealment;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.PieceMover;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.counters.*;
import VASSAL.tools.LaunchButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

public class ASLPieceMover extends PieceMover {
    /**
     * Preferences key for whether to mark units as having moved
     */
    public static final String MARK_MOVED = "MarkMoved";
    public static final String HOTKEY = "hotkey";

    private LaunchButton clear;

    public ASLPieceMover() {
        ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                GamePiece[] p = getMap().getPieces();
                Command c = new NullCommand();
                for (int i = 0; i < p.length; ++i) {
                    c.append(markMoved(p[i], false));
                }
                GameModule.getGameModule().sendAndLog(c);
                getMap().repaint();
            }
        };
        clear = new LaunchButton("Mark unmoved", null, HOTKEY, al);
    }

    public Map getMap() {
        return map;
    }

    public String[] getAttributeNames() {
        String[] s = super.getAttributeNames();
        String[] all = new String[s.length + 1];
        System.arraycopy(s, 0, all, 0, s.length);
        all[all.length - 1] = HOTKEY;
        return all;
    }

    public String getAttributeValueString(String key) {
        if (HOTKEY.equals(key)) {
            return clear.getAttributeValueString(key);
        } else {
            return super.getAttributeValueString(key);
        }
    }

    public void setAttribute(String key, Object value) {
        if (HOTKEY.equals(key)) {
            clear.setAttribute(key, value);
        } else {
            super.setAttribute(key, value);
        }
    }

    public void addTo(Buildable b) {
        super.addTo(b);

        map.setHighlighter(new ASLHighlighter());
    }

    @Override
    public void setup(boolean gameStarting) {
        super.setup(gameStarting);

        if (gameStarting) {

            if (markUnmovedButton != null) {
                for (int l_i = map.getToolBar().getComponents().length - 1; l_i >= 0; l_i--) {
                    Component l_objComponent = map.getToolBar().getComponent(l_i);

                    if (l_objComponent instanceof JButton) {
                        if ("MarkMovedPlaceHolder".equals(((JButton) l_objComponent).getName())) {
                            map.getToolBar().remove(markUnmovedButton);
                            map.getToolBar().remove(l_objComponent);

                            map.getToolBar().add(markUnmovedButton, l_i);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * When a piece is moved ensure all pieces are properly stacked
     * This fixes a bug where stacks can be slightly off on older versions of VASL
     * @param p
     */
    private Command snapErrantPieces() {

        ASLMap m = (ASLMap) map;
        final ArrayList<GamePiece> pieces = new ArrayList<GamePiece>();
        final GameModule theModule = GameModule.getGameModule();

        // get the set of all pieces not on the grid snap point
        for (GamePiece piece : theModule.getGameState().getAllPieces()) {

            if (piece instanceof Stack) {
                for (Iterator<GamePiece> i = ((Stack) piece).getPiecesInVisibleOrderIterator(); i.hasNext();) {
                    final GamePiece p = i.next();
                    if(!p.getPosition().equals(m.snapTo(p.getPosition()))) {
                        // System.out.println("Piece " + p.getName() + " is off - Current: " + p.getPosition() + " Snap: " + m.snapTo(p.getPosition()));
                        pieces.add(0, p);
                    }
                }
            }
            else if (piece.getParent() == null) {
                if(!piece.getPosition().equals(m.snapTo(piece.getPosition()))) {
                    // System.out.println("Piece " + piece.getName() + " is off - Current: " + piece.getPosition() + " Snap: " + m.snapTo(piece.getPosition()));
                    pieces.add(0, piece);
                }
            }
        }

        // fix stacking problem by moving piece out of its hex and then moving back in
        final Command command = new NullCommand();
        Point tempPoint;
        for (GamePiece p : pieces) {
            tempPoint = new Point(p.getPosition());
            tempPoint.translate(-100, 0);
            command.append(map.placeOrMerge(p, tempPoint));
        }
        for (GamePiece p : pieces) {
            tempPoint = new Point(p.getPosition());
            tempPoint.translate(100,0);
            command.append(map.placeOrMerge(p, m.snapTo(tempPoint)));
        }
        return command;
    }

    /**
     * In addition to moving pieces normally, we mark units that have moved
     * and adjust the concealment status of units
     */
    public Command movePieces(Map m, java.awt.Point p) {
        extractMovable();

        GamePiece movingConcealment = null;
        Stack formerParent = null;
        PieceIterator it = DragBuffer.getBuffer().getIterator();
        if (it.hasMoreElements()) {
            GamePiece moving = it.nextPiece();
            if (moving instanceof Stack) {
                Stack s = (Stack) moving;
                moving = s.topPiece();
                if (moving != s.bottomPiece()) {
                    moving = null;
                }
            }
            if (Decorator.getDecorator(moving, Concealment.class) != null
                    && !it.hasMoreElements()) {
                movingConcealment = moving;
                formerParent = movingConcealment.getParent();
            }
        }
        Command c = super.movePieces(m, p);
        if (c == null || c.isNull()) {
            return c;
        }
        if (movingConcealment != null) {
            if (movingConcealment.getParent() != null) {
                c.append(Concealable.adjustConcealment(movingConcealment.getParent()));
            }
            if (formerParent != null) {
                c.append(Concealable.adjustConcealment(formerParent));
            }
        }
        c.append(snapErrantPieces());
        return c;
    }

    /**
     * Remove all un-movable pieces from the DragBuffer.  Un-movable pieces
     * are those with the ASLProperties.LOCATION property set.
     */
    public void extractMovable() {
        Vector movable = new Vector();
        for (PieceIterator it = DragBuffer.getBuffer().getIterator();
             it.hasMoreElements(); ) {
            GamePiece p = it.nextPiece();
            if (p instanceof Stack) {
                Vector toMove = new Vector();
                for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator());
                     pi.hasMoreElements(); ) {
                    GamePiece p1 = pi.nextPiece();
                    if (p1.getProperty(ASLProperties.LOCATION) == null) {
                        toMove.addElement(p1);
                    } else // FRedKors 20/12/2013 If a stack contains an immobile counter, I don't move it AND I deselect it
                    {
                        KeyBuffer.getBuffer().remove(p1);
                    }
                }
                if (toMove.size() == ((Stack) p).getPieceCount()
                        || toMove.size() == 0) {
                    movable.addElement(p);
                } else {
                    for (int i = 0; i < toMove.size(); ++i) {
                        movable.addElement(toMove.elementAt(i));
                    }
                }
            } else {
                movable.addElement(p);
            }
        }

        // FredKors 30/11/2013 : PRB if a stack contains INVISIBLE_TO_ME counters, they are added as single counters as movable
        DragBuffer.getBuffer().clear();

        for (Enumeration e = movable.elements();
             e.hasMoreElements(); ) {
            GamePiece p = (GamePiece) e.nextElement();

            if (p.getProperty(ASLProperties.LOCATION) == null)
                DragBuffer.getBuffer().add(p);
            else {
                Stack s = p.getParent();
                int iNumSameParent = 0;
                boolean bOnlyFixedCounters = true;

                if (s != null) {
                    for (Enumeration en = movable.elements();
                         en.hasMoreElements(); ) {
                        GamePiece pp = (GamePiece) en.nextElement();

                        if (pp.getParent() == s) {
                            iNumSameParent++;

                            if (pp.getProperty(ASLProperties.LOCATION) == null)
                                bOnlyFixedCounters = false;
                        }
                    }

                    // if there are more than a single counter of the same stack, I don't move the fixed counter
                    // unless they are all fixed counter
                    if ((iNumSameParent == 1) || (bOnlyFixedCounters))
                        DragBuffer.getBuffer().add(p);
                    else
                        KeyBuffer.getBuffer().remove(p);// FRedKors 20/12/2013 If a stack contains an immobile counter, I don't move it AND I deselect it
                } else
                    DragBuffer.getBuffer().add(p); // if it is a single counter, I move it
            }
        }
    }

    /**
     * When the user clicks on the map, a piece from the map is selected by
     * the dragTargetSelector. What happens to that piece is determined by
     * the {@link PieceVisitorDispatcher} instance returned by this method.
     * The default implementation does the following: If a Deck, add the top
     * piece to the drag buffer If a stack, add it to the drag buffer.
     * Otherwise, add the piece and any other multi-selected pieces to the
     * drag buffer.
     *
     * @return
     * @see #createDragTargetSelector
     */
    protected PieceVisitorDispatcher createSelectionProcessor() {
        return new DeckVisitorDispatcher(new DeckVisitor() {
            public Object visitDeck(Deck d) {
                DragBuffer.getBuffer().clear();
                for (PieceIterator it = d.drawCards(); it.hasMoreElements(); ) {
                    DragBuffer.getBuffer().add(it.nextPiece());
                }
                return null;
            }

            // Modified by FredKors 30/11/2013 : Filter INVISIBLE_TO_ME counters
            public Object visitStack(Stack s) {
                DragBuffer.getBuffer().clear();
                // RFE 1629255 - Only add selected pieces within the stack to the DragBuffer
                // Add whole stack if all pieces are selected - better drag cursor
                int selectedCount = 0;
                int invisibleCount = 0;
                for (int i = 0; i < s.getPieceCount(); i++) {
                    if (Boolean.TRUE.equals(s.getPieceAt(i).getProperty(Properties.SELECTED))) {
                        if (!Boolean.TRUE.equals(s.getPieceAt(i).getProperty(Properties.INVISIBLE_TO_ME)))
                            selectedCount++;
                        else
                            invisibleCount++;
                    } else if (Boolean.TRUE.equals(s.getPieceAt(i).getProperty(Properties.INVISIBLE_TO_ME)))
                        invisibleCount++;
                }

                if (((Boolean) GameModule.getGameModule().getPrefs().getValue(Map.MOVING_STACKS_PICKUP_UNITS)).booleanValue() || s.getPieceCount() == 1 || s.getPieceCount() == selectedCount) {
                    if (invisibleCount == 0)
                        DragBuffer.getBuffer().add(s);
                    else {
                        for (int i = 0; i < s.getPieceCount(); i++) {
                            final GamePiece p = s.getPieceAt(i);

                            if (!Boolean.TRUE.equals(s.getPieceAt(i).getProperty(Properties.INVISIBLE_TO_ME))) {
                                DragBuffer.getBuffer().add(p);
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < s.getPieceCount(); i++) {
                        final GamePiece p = s.getPieceAt(i);
                        if (Boolean.TRUE.equals(p.getProperty(Properties.SELECTED))) {
                            DragBuffer.getBuffer().add(p);
                        }
                    }
                }
                // End RFE 1629255
                if (KeyBuffer.getBuffer().containsChild(s)) {
                    // If clicking on a stack with a selected piece, put all selected
                    // pieces in other stacks into the drag buffer
                    KeyBuffer.getBuffer().sort(ASLPieceMover.this);
                    for (Iterator<GamePiece> i =
                         KeyBuffer.getBuffer().getPiecesIterator(); i.hasNext(); ) {
                        final GamePiece piece = i.next();
                        if (piece.getParent() != s) {
                            DragBuffer.getBuffer().add(piece);
                        }
                    }
                }
                return null;
            }

            public Object visitDefault(GamePiece selected) {
                DragBuffer.getBuffer().clear();
                if (KeyBuffer.getBuffer().contains(selected)) {
                    // If clicking on a selected piece, put all selected pieces into the
                    // drag buffer
                    KeyBuffer.getBuffer().sort(ASLPieceMover.this);
                    for (Iterator<GamePiece> i =
                         KeyBuffer.getBuffer().getPiecesIterator(); i.hasNext(); ) {
                        DragBuffer.getBuffer().add(i.next());
                    }
                } else {
                    DragBuffer.getBuffer().clear();
                    DragBuffer.getBuffer().add(selected);
                }
                return null;
            }
        });
    }

}
