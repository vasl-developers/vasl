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
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Chatter;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.KeyBufferer;
import VASSAL.build.module.map.PieceMover;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.counters.*;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.DebugControls;
import VASSAL.tools.LaunchButton;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.swing.SwingUtils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;

import javax.swing.*;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ASLPieceMover extends PieceMover {
    /**
     * Preferences key for whether to mark units as having moved
     */
    public static final String MARK_MOVED = "MarkMoved";

    private final LaunchButton clear;

    public ASLPieceMover() {
        final ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                final Command c = new NullCommand();
                for (final GamePiece p: getMap().getPieces()) {
                    c.append(markMoved(p, false));
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

    @Override
    public String[] getAttributeNames() {
        return ArrayUtils.addAll(super.getAttributeNames(), HOTKEY);
    }

    @Override
    public String getAttributeValueString(String key) {
        if (HOTKEY.equals(key)) {
            return clear.getAttributeValueString(key);
        }
        else {
            return super.getAttributeValueString(key);
        }
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (HOTKEY.equals(key)) {
            clear.setAttribute(key, value);
        }
        else {
            super.setAttribute(key, value);
        }
    }

    @Override
    public void addTo(Buildable b) {
        //JY
        //Duplicate VASSAL PieceMover code to force addition of an ASLPieceMover
        //Taken from VASSAL 3.7.9
        //JY
        //super.addTo(b);  //undone rem
        // Create our target selection filters
        dragTargetSelector = createDragTargetSelector();
        selectionProcessor = createSelectionProcessor();
        dropTargetSelector = createDropTargetSelector(); // Obsolete from 3.6, but maintains backwards-compatibility with e.g. VASL

        // Register with our parent map
        map = (Map) b;
        map.addLocalMouseListener(this);
        GameModule.getGameModule().getGameState().addGameComponent(this);

        //JY PieceMover.DragHandler.addPieceMover(this);
        ASLPieceMover.DragHandler.addPieceMover(this);
        map.getView().addMouseMotionListener(this);
        //JY map.setDragGestureListener(PieceMover.DragHandler.getTheDragHandler());
        map.setDragGestureListener(ASLPieceMover.DragHandler.getTheDragHandler());
        map.setPieceMover(this);

        // Because of the strange legacy scheme of halfway-running a Toolbar button "on behalf of Map", we have to set some its attributes
        setAttribute(Map.MARK_UNMOVED_TEXT,
                map.getAttributeValueString(Map.MARK_UNMOVED_TEXT));
        setAttribute(Map.MARK_UNMOVED_ICON,
                map.getAttributeValueString(Map.MARK_UNMOVED_ICON));
        setAttribute(Map.MARK_UNMOVED_HOTKEY,
                map.getAttributeValueString(Map.MARK_UNMOVED_HOTKEY));
        setAttribute(Map.MARK_UNMOVED_REPORT,
                map.getAttributeValueString(Map.MARK_UNMOVED_REPORT));
        //JY

        map.setHighlighter(new ASLHighlighter());
    }

    @Override
    public void setup(boolean gameStarting) {
        super.setup(gameStarting);

        if (gameStarting && markUnmovedButton != null) {
            for (int i = map.getToolBar().getComponents().length - 1; i >= 0; i--) {
                final Component objComponent = map.getToolBar().getComponent(i);

                if (objComponent instanceof JButton) {
                    if ("MarkMovedPlaceHolder".equals(((JButton) objComponent).getName())) {
                        map.getToolBar().remove(markUnmovedButton);
                        map.getToolBar().remove(objComponent);

                        map.getToolBar().add(markUnmovedButton, i);
                        break;
                    }
                }
            }
        }
    }

    /**
     * When a piece is moved ensure all pieces are properly stacked
     * This fixes a bug where stacks can be slightly off on older versions of VASL
     */
    private Command snapErrantPieces() {
        final ASLMap m = (ASLMap) map;
        final ArrayList<GamePiece> pieces = new ArrayList<GamePiece>();
        final GameModule theModule = GameModule.getGameModule();

        // get the set of all pieces not on the grid snap point
        for (final GamePiece piece : theModule.getGameState().getAllPieces()) {
            if (piece instanceof Stack) {
                for (final Iterator<GamePiece> i = ((Stack) piece).getPiecesInVisibleOrderIterator(); i.hasNext();) {
                    final GamePiece p = i.next();
                    if(p.getLocalizedProperty(Properties.NO_STACK) == Boolean.FALSE && !p.getPosition().equals(m.snapTo(p.getPosition()))) {
                        // System.out.println("Piece " + p.getName() + " is off - Current: " + p.getPosition() + " Snap: " + m.snapTo(p.getPosition()));
                        pieces.add(0, p);
                    }
                }
            }
            else if (piece.getParent() == null) {
                if (piece.getLocalizedProperty(Properties.NO_STACK) == Boolean.FALSE && !piece.getPosition().equals(m.snapTo(piece.getPosition()))) {
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
            tempPoint.translate(100, 0);
            command.append(map.placeOrMerge(p, m.snapTo(tempPoint)));
        }
        return command;
    }

    /**
     * In addition to moving pieces normally, we mark units that have moved
     * and adjust the concealment status of units
     */
    @Override
    public Command movePieces(Map m, Point p) {
        extractMovable();

        GamePiece movingConcealment = null;
        Stack formerParent = null;
        final PieceIterator it = DragBuffer.getBuffer().getIterator();
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

        final Command c = _movePieces(m, p);
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
     * Unfortunately to suppress reporting of HIP counters we must duplicate the VASSAL code here to change one line
     *
     * @param map Map
     * @param p Point mouse released
     */
    public Command _movePieces(Map map, Point p) {
        final PieceIterator it = DragBuffer.getBuffer().getIterator();
        if (!it.hasMoreElements()) {
            return null;
        }

        final List<GamePiece> allDraggedPieces = new ArrayList<>();

        Point offset = null;
        Command comm = new NullCommand();

        final BoundsTracker tracker = new BoundsTracker();

        final HashMap<Point, List<GamePiece>> mergeTargets = new HashMap<>();

        final List<GamePiece> otherPieces = new ArrayList<>();
        final List<GamePiece> cargoPieces = new ArrayList<>();
        //java.util.List<MatMover> matPieces = new ArrayList();

        while (it.hasMoreElements()) {
            final GamePiece piece = it.nextPiece();
            if (offset == null) {
                offset = new Point(p.x - piece.getPosition().x, p.y - piece.getPosition().y);
            }

            if (Boolean.TRUE.equals(piece.getProperty("IsCargo"))) {
                cargoPieces.add(piece);
            //} else if (piece.getProperty("MatID") != null) {
            //    matPieces.add(new MatMover(piece));
            } else {
                otherPieces.add(piece);
            }
        }

        //Iterator var31 = matPieces.iterator();

        //while(var31.hasNext()) {
        //    MatMover mm = (MatMover)var31.next();
        //    mm.grabCargo(cargoPieces);
        //}

        final List<GamePiece> newDragBuffer = new ArrayList();
        newDragBuffer.addAll(otherPieces);
        newDragBuffer.addAll(cargoPieces);
        //Iterator var33 = matPieces.iterator();

        //while(var33.hasNext()) {
        //    MatMover mm = (MatMover)var33.next();
        //    newDragBuffer.add(mm.getMatPiece());
        //    newDragBuffer.addAll(mm.getCargo());
        //}

        final GameModule gm = GameModule.getGameModule();
        final boolean isMatSupport = gm.isMatSupport();
        Mat currentMat = null;
        MatCargo currentCargo = null;
        Iterator var17 = newDragBuffer.iterator();

        //Command comm;
        label246:
        while(var17.hasNext()) {
            GamePiece gp = (GamePiece)var17.next();
            this.dragging = gp;
            tracker.addPiece(this.dragging);
            Mat tempMat = (Mat)Decorator.getDecorator(gp, Mat.class);
            if (tempMat != null) {
                currentMat = tempMat;
            }

            currentCargo = (MatCargo)Decorator.getDecorator(gp, MatCargo.class);
            ArrayList<GamePiece> draggedPieces = new ArrayList(0);
            if (this.dragging instanceof Stack) {
                draggedPieces.addAll(((Stack)this.dragging).asList());
            } else {
                draggedPieces.add(this.dragging);
            }

            if (offset != null) {
                p = new Point(this.dragging.getPosition().x + offset.x, this.dragging.getPosition().y + offset.y);
            }

            List<GamePiece> mergeCandidates = (List) mergeTargets.get(p);
            GamePiece mergeWith = null;
            //int i;
            GamePiece piece;
            if (mergeCandidates != null) {
                final int n = mergeCandidates.size();

                for(int i = 0; i < n; ++i) {
                    piece = (GamePiece)mergeCandidates.get(i);
                    if (map.getPieceCollection().canMerge(piece, this.dragging)) {
                        mergeWith = piece;
                        mergeCandidates.set(i, this.dragging);
                        break;
                    }
                }
            }

            //ArrayList mergeCandidates;
            if (mergeWith == null) {
                mergeWith = map.findAnyPiece(p, this.getDropTargetSelector(this.dragging, currentCargo, currentMat));
                if (mergeWith == null) {
                    boolean ignoreGrid = false;
                    Boolean b;
                    if (currentCargo == null) {
                        b = (Boolean)this.dragging.getProperty("IgnoreGrid");
                        ignoreGrid = b != null && b;
                    } else if (currentMat == null && currentCargo.locateNewMat(map, p) == null) {
                        b = (Boolean)this.dragging.getProperty("baseIgnoreGrid");
                        ignoreGrid = b != null && b;
                    } else {
                        ignoreGrid = true;
                    }

                    if (!ignoreGrid) {
                        p = map.snapTo(p);
                        mergeWith = map.findAnyPiece(p, this.getDropTargetSelector(this.dragging, currentCargo, currentMat));
                    }
                }

                offset = new Point(p.x - this.dragging.getPosition().x, p.y - this.dragging.getPosition().y);
                if (mergeWith != null && map.getStackMetrics().isStackingEnabled()) {
                    mergeCandidates = new ArrayList();
                    mergeCandidates.add(this.dragging);
                    mergeCandidates.add(mergeWith);
                    mergeTargets.put(p, mergeCandidates);
                }
            }

            //GamePiece piece;
            Iterator var44;
            if (mergeWith == null) {
                comm = ((Command)comm).append(this.movedPiece(this.dragging, p));
                comm = comm.append(map.placeAt(this.dragging, p));
                if (!(this.dragging instanceof Stack) && !Boolean.TRUE.equals(this.dragging.getProperty("NoStack"))) {
                    Stack parent = map.getStackMetrics().createStack(this.dragging);
                    if (parent != null) {
                        comm = ((Command)comm).append(map.placeAt(parent, p));
                        mergeCandidates = new ArrayList();
                        mergeCandidates.add(this.dragging);
                        mergeCandidates.add(parent);
                        mergeTargets.put(p, mergeCandidates);
                    }
                }
            } else {
                if (mergeWith instanceof Deck) {
                    ArrayList<GamePiece> newList = new ArrayList(0);
                    Iterator var41 = draggedPieces.iterator();

                    label206:
                    while(true) {
                        boolean isObscuredToMe;
                        do {
                            do {
                                do {
                                    if (!var41.hasNext()) {
                                        if (newList.size() != draggedPieces.size()) {
                                            draggedPieces.clear();
                                            draggedPieces.addAll(newList);
                                        }
                                        break label206;
                                    }

                                    piece = (GamePiece)var41.next();
                                } while(!((Deck)mergeWith).mayContain(piece));
                            } while(!((Deck)mergeWith).isAccessible());

                            isObscuredToMe = Boolean.TRUE.equals(piece.getProperty("Obscured"));
                        } while(isObscuredToMe && !"nobody".equals(piece.getProperty("obs;")));

                        newList.add(piece);
                    }
                }

                if (mergeWith instanceof Stack) {
                    for(var44 = draggedPieces.iterator(); var44.hasNext(); comm = comm.append(map.getStackMetrics().merge(mergeWith, piece))) {
                        piece = (GamePiece)var44.next();
                        comm = ((Command)comm).append(this.movedPiece(piece, mergeWith.getPosition()));
                    }
                } else {
                    int i;
                    for(i = draggedPieces.size() - 1; i >= 0; --i) {
                        comm = ((Command)comm).append(this.movedPiece((GamePiece)draggedPieces.get(i), mergeWith.getPosition()));
                        comm = comm.append(map.getStackMetrics().merge(mergeWith, (GamePiece)draggedPieces.get(i)));
                    }
                }
            }

            var44 = draggedPieces.iterator();

            while(true) {
                MatCargo cargo;
                GamePiece oldMat;
                do {
                    while(true) {
                        do {
                            if (!var44.hasNext()) {
                                allDraggedPieces.addAll(draggedPieces);
                                tracker.addPiece(this.dragging);
                                continue label246;
                            }

                            piece = (GamePiece)var44.next();
                            KeyBuffer.getBuffer().add(piece);
                        } while(!isMatSupport);

                        if (Boolean.TRUE.equals(piece.getProperty("IsCargo"))) {
                            cargo = (MatCargo)Decorator.getDecorator(piece, MatCargo.class);
                            oldMat = cargo.getMat();
                            break;
                        }

                        if (piece.getProperty("MatName") != null) {
                            Mat thisMat = (Mat)Decorator.getDecorator(piece, Mat.class);
                            if (thisMat != null) {
                                List<GamePiece> contents = thisMat.getContents();
                                Iterator var27 = contents.iterator();

                                while(var27.hasNext()) {
                                    GamePiece pcargo = (GamePiece)var27.next();
                                    if (!draggedPieces.contains(pcargo) && !allDraggedPieces.contains(pcargo) && !DragBuffer.getBuffer().contains(pcargo)) {
                                        MatCargo theCargo = (MatCargo)Decorator.getDecorator(pcargo, MatCargo.class);
                                        if (theCargo != null) {
                                            comm = ((Command)comm).append(theCargo.findNewMat(pcargo.getMap(), pcargo.getPosition()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } while(oldMat != null && (draggedPieces.contains(oldMat) || allDraggedPieces.contains(oldMat) || DragBuffer.getBuffer().contains(oldMat)));

                comm = ((Command)comm).append(cargo.findNewMat(map, p));
            }
        }

        if (GameModule.getGameModule().isTrueMovedSupport()) {
            comm = ((Command)comm).append(this.doTrueMovedSupport(allDraggedPieces));
        }


        if (GlobalOptions.getInstance().autoReportEnabled()) {
            if (dragging.getName().substring(0, Math.min(dragging.getName().length(), 6)).equals("<html>")) {
                // new code to handle Labels with html code; stop it pasting to chat
                Command c = new NullCommand();
                c.append(new Chatter.DisplayText(GameModule.getGameModule().getChatter(), "* " + "Label Counter moved" ));
                c.execute();
                comm = comm.append(c);
            } else {
                // Here is the one line we have to change
                // final Command report = createMovementReporter(comm).getReportCommand().append(new MovementReporter.HiddenMovementReporter(comm).getReportCommand());
                final Command report = createMovementReporter(comm).getReportCommand();
                report.execute();
                comm = comm.append(report);
                // trigger auto-reveal fortifications
                HIPFortification hipfort = map.getComponentsOf(HIPFortification.class).get(0);
                hipfort.runupdate(allDraggedPieces);

            }
        }

        //if (GlobalOptions.getInstance().autoReportEnabled()) {
        //    Command report = this.createMovementReporter((Command)comm).getReportCommand().append((new MovementReporter.HiddenMovementReporter((Command)comm)).getReportCommand());
        //    report.execute();
        //    comm = ((Command)comm).append(report);
        //}

        if (map.getMoveKey() != null) {
            comm = ((Command)comm).append(this.applyKeyAfterMove(allDraggedPieces, map.getMoveKey()));
        }

        comm = gm.getDeckManager().checkEmptyDecks((Command)comm);
        KeyBuffer.getBuffer().setSuppressActionButtons(true);
        tracker.repaint();
        return comm;

    }

    /**
     * Remove all un-movable pieces from the DragBuffer.  Un-movable pieces
     * are those with the ASLProperties.LOCATION property set.
     */
    public void extractMovable() {
        ArrayList<GamePiece> movable = new ArrayList<GamePiece>();
        for (PieceIterator it = DragBuffer.getBuffer().getIterator();
             it.hasMoreElements(); ) {
            GamePiece p = it.nextPiece();
            if (p instanceof Stack) {
                ArrayList<GamePiece> toMove = new ArrayList<GamePiece>();
                for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator());
                     pi.hasMoreElements(); ) {
                    GamePiece p1 = pi.nextPiece();
                    if (p1.getProperty(ASLProperties.LOCATION) == null) {
                        toMove.add(p1);
                    } else // FRedKors 20/12/2013 If a stack contains an immobile counter, I don't move it AND I deselect it
                    {
                        KeyBuffer.getBuffer().remove(p1);
                    }
                }
                if (toMove.size() == ((Stack) p).getPieceCount()
                        || toMove.size() == 0) {
                    movable.add(p);
                } else {
                    movable.addAll(toMove);
                }
            } else {
                movable.add(p);
            }
        }

        // FredKors 30/11/2013 : PRB if a stack contains INVISIBLE_TO_ME counters, they are added as single counters as movable
        DragBuffer.getBuffer().clear();

        for (Iterator<GamePiece> i = movable.iterator(); i.hasNext(); ) {
            GamePiece p = i.next();

            if (p.getProperty(ASLProperties.LOCATION) == null)
                DragBuffer.getBuffer().add(p);
            else {
                Stack s = p.getParent();
                int iNumSameParent = 0;
                boolean bOnlyFixedCounters = true;

                if (s != null) {
                    for (Iterator<GamePiece> j = movable.iterator(); j.hasNext(); ) {
                        GamePiece pp = j.next();

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
    @Override
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

    //JY
    //Duplicate VASSAL PieceMover.AbstractDragHandler class to allow overloading of various methods
    //Taken from VASSAL 3.7.9
    /**
     * Common functionality for DragHandler for cases with and without drag
     * image support.
     *
     * @author Pieter Geerkens
     */
    public abstract static class AbstractDragHandler
            implements DragGestureListener, DragSourceListener,
            DragSourceMotionListener, DropTargetListener {

        //JY
        private void OutputString(String strMsg) {
            GameModule.getGameModule().getChatter().send(strMsg);
        }

        public Rectangle scalePiece(Rectangle r, double f) {
            Rectangle o = new Rectangle();
            o.width = (int) (r.width * f);
            o.height = (int) (r.height * f);
            o.x = (int) (r.getCenterX() - 0.5*r.width*f);
            o.y = (int) (r.getCenterY() - 0.5*r.height*f);
            return o;
        }

        public Shape scalePiece(Shape s, double f) {
            double cx = s.getBounds2D().getCenterX();
            double cy = s.getBounds2D().getCenterY();
            final AffineTransform t = AffineTransform.getTranslateInstance(-cx, -cy);
            t.scale(f, f);
            t.translate(cx, cy);
            return t.createTransformedShape(s);
        }

        //JY private static PieceMover.AbstractDragHandler theDragHandler = PieceMover.AbstractDragHandler.AbstractDragHandlerFactory.getCorrectDragHandler();
        private static ASLPieceMover.AbstractDragHandler theDragHandler = ASLPieceMover.AbstractDragHandler.AbstractDragHandlerFactory.getCorrectDragHandler();
        //JY

        /** returns the singleton DragHandler instance */
        //JY public static PieceMover.AbstractDragHandler getTheDragHandler() {return theDragHandler;}
        public static ASLPieceMover.AbstractDragHandler getTheDragHandler() {return theDragHandler;}

        //JY public static void setTheDragHandler(PieceMover.AbstractDragHandler myHandler) {theDragHandler = myHandler;}
        public static void setTheDragHandler(ASLPieceMover.AbstractDragHandler myHandler) {theDragHandler = myHandler;}

        /**
         * Picks the correct drag handler based on our OS, DragSource, and preferences.
         */
        public static class AbstractDragHandlerFactory {
            //JY public static PieceMover.AbstractDragHandler getCorrectDragHandler() {
            public static ASLPieceMover.AbstractDragHandler getCorrectDragHandler() {
                if (!DragSource.isDragImageSupported() || GlobalOptions.getInstance().isForceNonNativeDrag()) {
                    return new DragHandlerNoImage();
                }
                else {
                    return SystemUtils.IS_OS_MAC ? new DragHandlerMacOSX() : new DragHandler();
                }
            }
        }

        /**
         * Finds all the piece slots in a module and resets their drop targets to use a new DragHandler
         * @param target recursive search through components
         */
        public static void resetRecursivePieceSlots(AbstractBuildable target) {
            for (final Buildable b : target.getBuildables()) {
                if (b instanceof PieceSlot) {
                    final Component panel = ((PieceSlot)b).getComponent();
                    panel.setDropTarget(makeDropTarget(panel, DnDConstants.ACTION_MOVE, null));
                }
                else if (b instanceof AbstractBuildable) {
                    resetRecursivePieceSlots((AbstractBuildable)b);
                }
            }
        }

        /**
         * Reset our drag handler, e.g. if our preferences change.
         */
        public static void resetDragHandler() {
            //JY final PieceMover.AbstractDragHandler newHandler = PieceMover.AbstractDragHandler.AbstractDragHandlerFactory.getCorrectDragHandler();
            final ASLPieceMover.AbstractDragHandler newHandler = ASLPieceMover.AbstractDragHandler.AbstractDragHandlerFactory.getCorrectDragHandler();
            setTheDragHandler(newHandler);
            for (final Map map : Map.getMapList()) {
                map.setDragGestureListener(newHandler);
                map.getComponent().setDropTarget(makeDropTarget(map.getComponent(), DnDConstants.ACTION_MOVE, map));
            }

            resetRecursivePieceSlots(GameModule.getGameModule());
        }

        /**
         * Registers a PieceMover
         * @param pm PieceMover for this dragHandler
         */
        //JY public static void addPieceMover(PieceMover pm) {
        public static void addPieceMover(ASLPieceMover pm) {
            if (!pieceMovers.contains(pm)) {
                pieceMovers.add(pm);
            }
        }

        /**
         * Creates a new DropTarget and hooks us into the beginning of a
         * DropTargetListener chain. DropTarget events are not multicast;
         * there can be only one "true" listener.
         */
        public static DropTarget makeDropTarget(Component theComponent, int dndContants, DropTargetListener dropTargetListener) {
            if (dropTargetListener != null) {
                DragHandler.getTheDragHandler()
                        .dropTargetListeners.put(theComponent, dropTargetListener);
            }
            return new DropTarget(theComponent, dndContants,
                    DragHandler.getTheDragHandler());
        }

        /**
         * Removes a dropTarget component
         * @param theComponent component to remove
         */
        public static void removeDropTarget(Component theComponent) {
            DragHandler.getTheDragHandler().dropTargetListeners.remove(theComponent);
        }

        //JY
        /*
        private static StackMetrics getStackMetrics(GamePiece piece) {
            final Map map = piece.getMap();
            if (map != null) {
                final StackMetrics sm = map.getStackMetrics();
                if (sm != null) {
                    return sm;
                }
            }
            return new StackMetrics();
        }
        */
        private static ASLStackMetrics getStackMetrics(GamePiece piece) {
            final Map map = piece.getMap();
            if (map != null) {
                final ASLStackMetrics sm = ((ASLMap)map).getStackMetrics();
                if (sm != null) {
                    return sm;
                }
            }
            return new ASLStackMetrics();
        }

        //JY protected static List<PieceMover> pieceMovers = new ArrayList<>(); // our piece movers
        protected static List<ASLPieceMover> pieceMovers = new ArrayList<>(); // our piece movers

        protected static final int CURSOR_ALPHA = 127; // pseudo cursor is 50% transparent
        protected static final int EXTRA_BORDER = 4;   // pseudo cursor is includes a 4 pixel border

        protected Rectangle boundingBox;    // image bounds
        protected Rectangle boundingBoxComp;    // image bounds

        private int originalPieceOffsetX; // How far drag STARTED from GamePiece's center (on original map)
        private int originalPieceOffsetY;

        protected double dragPieceOffCenterZoom = 1.0; // zoom at start of drag

        protected int currentPieceOffsetX; // How far cursor is CURRENTLY off-center, a function of dragPieceOffCenter{X,Y,Zoom}
        protected int currentPieceOffsetY; // I.e. on current map (which may have different zoom)

        // Seems there can be only one DropTargetListener per drop target. After we
        // process a drop target event, we manually pass the event on to this listener.
        protected java.util.Map<Component, DropTargetListener> dropTargetListeners = new HashMap<>();

        // used by DragHandlerNoImage only
        protected Point lastDragLocation = new Point();

        // used by DragHandlerNoImage only
        protected JLabel dragCursor;      // An image label. Lives on current DropTarget's LayeredPane.

        // used by DragHandlerNoImage only
        protected double dragCursorZoom = 1.0; // Current cursor scale (zoom)

        /**
         * @return platform-dependent offset multiplier
         */
        protected abstract int getOffsetMult();

        /**
         * @param dge DG event
         * @return platform-dependent device scale
         */
        protected abstract double getDeviceScale(DragGestureEvent dge);

        protected abstract double getDeviceScale(DropTargetDragEvent e);

        /**
         * @param e DropTargetEvent
         * @return associated DropTargetListener
         */
        protected DropTargetListener getListener(DropTargetEvent e) {
            final Component component = e.getDropTargetContext().getComponent();
            return dropTargetListeners.get(component);
        }

        /**
         * Moves the drag cursor on the current draw window
         * @param dragX x position
         * @param dragY y position
         */

        protected void moveDragCursor(int dragX, int dragY) {}

        /**
         * Removes the drag cursor from the current draw window
         */

        protected void removeDragCursor() {}

        /** calculates the offset between cursor dragCursor positions */
        protected void calcDrawOffset() {}

        /**
         * creates or moves cursor object to given window. Called when drag
         * operation begins in a window or the cursor is dragged over a new
         * drop-target window
         *
         * @param newDropWin window component to be our new draw window.
         */
        public void setDrawWinToOwnerOf(Component newDropWin) {}

        /**
         * Common functionality abstracted from makeDragImage and makeDragCursor
         *
         * @param zoom Zoom Level
         * @param doOffset Drag Offset
         * @param target Target Component
         * @param setSize Set Size
         * @return Drag Image
         */
        @Deprecated(since = "2023-05-08", forRemoval = true)
        protected BufferedImage makeDragImageCursorCommon(double zoom, boolean doOffset, Component target, boolean setSize) {
            return makeDragImageCursorCommon(zoom, 1.0, doOffset, target);
        }

        protected BufferedImage makeDragImageCursorCommon(double mapzoom, double os_scale, boolean doOffset, Component target) {
            // FIXME: Should be an ImageOp for caching?
            final double zoom = mapzoom * os_scale;

            currentPieceOffsetX =
                    (int) (originalPieceOffsetX / dragPieceOffCenterZoom * mapzoom + 0.5);
            currentPieceOffsetY =
                    (int) (originalPieceOffsetY / dragPieceOffCenterZoom * mapzoom + 0.5);

            final List<Point> relativePositions = buildBoundingBox();

            // convert boundingBoxComp to component space
            boundingBoxComp = new Rectangle(boundingBox);
            boundingBoxComp.width *= mapzoom;
            boundingBoxComp.height *= mapzoom;
            boundingBoxComp.x *= mapzoom;
            boundingBoxComp.y *= mapzoom;

            if (doOffset) {
                calcDrawOffset();
            }

            // convert boundingBox, relativePosisions to drawing space
            boundingBox.width *= zoom;
            boundingBox.height *= zoom;
            boundingBox.x *= zoom;
            boundingBox.y *= zoom;

            for (Point p: relativePositions) {
                p.x *= zoom;
                p.y *= zoom;
            }

            //JY
            //final int w = boundingBox.width + EXTRA_BORDER * 2;
            //final int h = boundingBox.height + EXTRA_BORDER * 2;
            int eb = Math.max((int) (EXTRA_BORDER / ASLMap.getbZoom()), EXTRA_BORDER);
            final int w = boundingBox.width + eb * 2;
            final int h = boundingBox.height + eb * 2;
            //JY

            final BufferedImage image = ImageUtils.createCompatibleTranslucentImage(w, h);
            drawDragImage(image, target, relativePositions, zoom);

            return image;
        }

        /**
         * Creates the image to use when dragging based on the zoom factor
         * passed in.
         *
         * zoom DragBuffer.getBuffer
         * @return dragImage
         */
        private BufferedImage makeDragImage(double mapzoom, double os_scale) {
            return makeDragImageCursorCommon(mapzoom, os_scale, false, null);
        }

        @Deprecated(since = "2023-05-08", forRemoval = true)
        protected void makeDragCursor(double zoom) {}

        private List<Point> buildBoundingBox() {
            // boundingBox and relativePositions are constructed in map
            // coordinates in this function

            final ArrayList<Point> relativePositions = new ArrayList<>();
            final PieceIterator dragContents = DragBuffer.getBuffer().getIterator();
            final GamePiece firstPiece = dragContents.nextPiece();
            GamePiece lastPiece = firstPiece;

            boundingBox = firstPiece.getShape().getBounds();
            //JY
            double pZoom = 1.0;
            final ASLMap map = (ASLMap) firstPiece.getMap();
            if (map != null) {
                pZoom = map.PieceScalerBoardZoom(firstPiece);
            }
            if (!(firstPiece instanceof Stack)) {
                boundingBox = this.scalePiece(firstPiece.getShape().getBounds(), pZoom); //Centred on 0 0, with min and max corresponding to width
            }
            //JY
            relativePositions.add(new Point(0, 0));

            int stackCount = 0;
            while (dragContents.hasMoreElements()) {
                final GamePiece nextPiece = dragContents.nextPiece();
                //JY
                //final Rectangle r = nextPiece.getShape().getBounds();
                Rectangle r = nextPiece.getShape().getBounds();
                pZoom = 1.0;
                if (map != null) {
                    pZoom = map.PieceScalerBoardZoom(nextPiece);
                }
                //pZoom = map.PieceScalerBoardZoom(nextPiece);
                if (!(nextPiece instanceof Stack)) {
                    r = this.scalePiece(nextPiece.getShape().getBounds(), pZoom);
                }
                //JY
                final Point p = new Point(
                        nextPiece.getPosition().x - firstPiece.getPosition().x,
                        nextPiece.getPosition().y - firstPiece.getPosition().y
                );
                r.translate(p.x, p.y);

                if (nextPiece.getPosition().equals(lastPiece.getPosition())) {
                    stackCount++;
                    //JY final StackMetrics sm = getStackMetrics(nextPiece);
                    final ASLStackMetrics sm = getStackMetrics(nextPiece);
                    //JY r.translate(sm.unexSepX * stackCount, -sm.unexSepY * stackCount
                    int usx = Integer.parseInt(sm.getAttributeValueString("unexSepX"));
                    int usy = Integer.parseInt(sm.getAttributeValueString("unexSepY"));
                    r.translate(usx * stackCount, -usy * stackCount);
                }

                boundingBox.add(r);
                relativePositions.add(p);
                lastPiece = nextPiece;
            }

            return relativePositions;
        }

        //JY
        protected void nextPositionAPM(Point currentPos, Rectangle currentBounds, Point nextPos, Rectangle nextBounds, int dx, int dy) {
            int deltaX;
            if (dx > 0) {
                deltaX = currentBounds.x + dx - nextBounds.x;
            } else if (dx < 0) {
                deltaX = currentBounds.x + currentBounds.width - nextBounds.width + dx - nextBounds.x;
            } else {
                deltaX = currentPos.x - nextPos.x;
            }
            int deltaY;
            if (dy > 0) {
                deltaY = currentBounds.y + currentBounds.height - nextBounds.height - nextBounds.y - dy;
            } else if (dy < 0) {
                deltaY = currentBounds.y - dy - nextBounds.y;
            } else {
                deltaY = currentPos.y - nextPos.y;
            }
            nextBounds.translate(deltaX, deltaY);
            nextPos.translate(deltaX, deltaY);
        }
        private void drawStack(Stack stack, Graphics g, int x, int y, Component obs, double zoom) {
            int val = stack.getMaximumVisiblePieceCount();
            Highlighter highlighter = stack.getMap() == null ? BasicPiece.getHighlighter() : stack.getMap().getHighlighter();
            Point[] positions = new Point[stack.getPieceCount()];
            final ASLStackMetrics sm = getStackMetrics(stack);
            int usx = Integer.parseInt(sm.getAttributeValueString("unexSepX")); //usx = Math.max((int)(usx*pZoom), 1);
            int usy = Integer.parseInt(sm.getAttributeValueString("unexSepY")); //usy = Math.max((int)(usy*pZoom), 2);
            int dx = usx;
            int dy = usy;
            Point currentPos = null;
            Rectangle currentSelBounds = null;
            for(int index = 0; index < val; ++index) {
                GamePiece child = stack.getPieceAt(index);
                double pZoom = ((ASLMap) stack.getMap()).PieceScalerBoardZoom(child);
                Rectangle bbox;
                if (Boolean.TRUE.equals(child.getProperty("Invisible"))) {
                    bbox = new Rectangle(x, y, 0, 0);
                    positions[index] = bbox.getLocation();
                } else {
                    child.setProperty("useUnrotatedShape", Boolean.TRUE);
                    Rectangle nextSelBounds = scalePiece(child.getShape().getBounds(), pZoom);
                    child.setProperty("useUnrotatedShape", Boolean.FALSE);
                    Point nextPos = new Point(0, 0);
                    if (currentPos == null) {
                        nextSelBounds.translate(x, y);
                        currentPos = new Point(x, y);
                        nextPos = currentPos;
                    } else {
                        nextPositionAPM(currentPos, currentSelBounds, nextPos, nextSelBounds, dx, dy);
                    }
                    positions[index] = nextPos;
                    currentPos = nextPos;
                    currentSelBounds = nextSelBounds;
                }
            }
            for(int index = 0; index < val; ++index) {
                GamePiece next = stack.getPieceAt(index);
                int nextX = x + (int) (zoom * (positions[index].x - x));
                int nextY = y + (int) (zoom * (positions[index].y - y));
                next.draw(g, nextX, nextY, obs, zoom);
                highlighter.draw(next, g, nextX, nextY, obs, zoom);
            }
        }
        //JY

        private void drawDragImage(BufferedImage image, Component target,
                                   List<Point> relativePositions, double zoom) {
            final Graphics2D g = image.createGraphics();
            g.addRenderingHints(SwingUtils.FONT_HINTS);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int index = 0;
            Point lastPos = null;
            int stackCount = 0;
            for (final PieceIterator dragContents = DragBuffer.getBuffer().getIterator();
                 dragContents.hasMoreElements(); ) {

                final GamePiece piece = dragContents.nextPiece();
                final Point pos = relativePositions.get(index++);
                //JY
                //final Map map = piece.getMap();
                final ASLMap map = (ASLMap) piece.getMap();
                double pZoom = ((ASLMap)map).PieceScalerBoardZoom(piece);
                //JY

                if (piece instanceof Stack) {
                    stackCount = 0;
                    //JY piece.draw(g, EXTRA_BORDER - boundingBox.x + pos.x,
                    //JY        EXTRA_BORDER - boundingBox.y + pos.y,
                    //JY        map == null ? target : map.getView(), zoom);
                    drawStack((Stack) piece, g, EXTRA_BORDER - boundingBox.x + pos.x,
                            EXTRA_BORDER - boundingBox.y + pos.y, map.getView(), zoom * pZoom);
                }
                else {
                    final Point offset = new Point(0, 0);
                    if (pos.equals(lastPos)) {
                        stackCount++;
                        //JY
                        //final StackMetrics sm = getStackMetrics(piece);
                        final ASLStackMetrics sm = getStackMetrics(piece);
                        //offset.x = (int) Math.round(sm.unexSepX * stackCount * zoom);
                        //offset.y = (int) Math.round(sm.unexSepY * stackCount * zoom);
                        int usx = Integer.parseInt(sm.getAttributeValueString("unexSepX")); usx = Math.max((int)(usx*pZoom), 1);
                        int usy = Integer.parseInt(sm.getAttributeValueString("unexSepY")); usy = Math.max((int)(usy*pZoom), 2);
                        offset.x = (int) Math.round(usx * stackCount * zoom);
                        offset.y = (int) Math.round(usy * stackCount * zoom);
                        //JY
                    }
                    else {
                        stackCount = 0;
                    }

                    //JY
                    //final int x = EXTRA_BORDER - boundingBox.x + pos.x + offset.x;
                    //final int y = EXTRA_BORDER - boundingBox.y + pos.y - offset.y;
                    int eb = EXTRA_BORDER;
                    if ((ASLMap.getbZoom() < 1.0) || (ASLMap.getbZoom() > 1.0)) {
                        eb = Math.max((int) (EXTRA_BORDER / ASLMap.getbZoom() * (zoom > 1.0 ? zoom : 1.0)), EXTRA_BORDER);
                    }
                    final int x = eb - boundingBox.x + pos.x + offset.x;
                    final int y = eb - boundingBox.y + pos.y - offset.y;
                    //JY

                    String owner = "";
                    final GamePiece parent = piece.getParent();
                    boolean faceDown = false;
                    if (parent instanceof Deck) {
                        owner = (String)piece.getProperty(Properties.OBSCURED_BY);
                        faceDown = ((Deck) parent).isFaceDown();
                        piece.setProperty(Properties.OBSCURED_BY, faceDown ? Deck.NO_USER : null);
                        if (faceDown) {
                            piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.TRUE);
                        }
                    }

                    final AffineTransform t = AffineTransform.getScaleInstance(zoom, zoom);
                    t.translate(x / zoom, y / zoom);
                    //JY
                    //g.setClip(t.createTransformedShape(piece.getShape()));
                    //piece.draw(g, x, y, map == null ? target : map.getView(), zoom);
                    Shape s = this.scalePiece(piece.getShape(), pZoom);
                    g.setClip(t.createTransformedShape(s));
                    piece.draw(g, x, y, map == null ? target : map.getView(), zoom*pZoom);
                    //JY

                    g.setClip(null);

                    final Highlighter highlighter = map == null ?
                            BasicPiece.getHighlighter() : map.getHighlighter();
                    //JY
                    //highlighter.draw(piece, g, x, y, null, zoom);
                    highlighter.draw(piece, g, x, y, null, zoom*pZoom);
                    //JY

                    if (piece.getParent() instanceof Deck) {
                        piece.setProperty(Properties.OBSCURED_BY, owner);
                        if (faceDown) {
                            piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.FALSE);
                        }
                    }

                    final Mat mat = (Mat) Decorator.getDecorator(piece, Mat.class);
                    if (mat != null) {
                        mat.drawCargo(g, x, y, null, zoom);
                    }
                }

                lastPos = pos;
            }

            // make the drag image transparent
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
            g.setColor(new Color(0xFF, 0xFF, 0xFF, CURSOR_ALPHA));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());

            g.dispose();
        }

        /******************************************************************************
         * DRAG GESTURE LISTENER INTERFACE
         *
         * EVENT uses SCALED, DRAG-SOURCE coordinate system. ("component coordinates")
         * PIECE uses SCALED, OWNER (arbitrary) coordinate system ("map coordinates")
         *
         * Fires after user begins moving the mouse several pixels over a map. This
         * method will be overridden, but called as a super(), by the Drag Gesture
         * extension that is used, which will either be {@link DragHandler} if DragImage
         * is supported by the JRE, or {@link DragHandlerNoImage} if not. Either one will
         * have called {dragGestureRecognizedPrep}, immediately below, before it
         * calls this method.
         ******************************************************************************/
        @Override
        public void dragGestureRecognized(DragGestureEvent dge) {
            try {
                beginDragging(dge);
            }
            // FIXME: Fix by replacing AWT Drag 'n Drop with Swing DnD.
            // Catch and ignore spurious DragGestures
            catch (InvalidDnDOperationException ignored) {
            }
        }

        /**
         * Sets things up at the beginning of a drag-and-drop operation:
         * <br> - Screen out any immovable pieces
         * <br> - Account for any offsets on in the window
         * <br> - Sets dragWin to our source window
         *
         * @param dge dg event
         * @return mousePosition if we processed, or null if we bailed.
         */
        protected Point dragGestureRecognizedPrep(DragGestureEvent dge) {
            // Ensure the user has dragged on a counter before starting the drag.
            final DragBuffer db = DragBuffer.getBuffer();
            if (db.isEmpty()) return null;

            // Remove any Immovable pieces from the DragBuffer that were
            // selected in a selection rectangle, unless they are being
            // dragged from a piece palette (i.e., getMap() == null).
            final List<GamePiece> pieces = new ArrayList<>();
            for (final PieceIterator i = db.getIterator();  // NOPMD
                 i.hasMoreElements(); pieces.add(i.nextPiece()));
            for (final GamePiece piece : pieces) {
                if (piece.getMap() != null &&
                        Boolean.TRUE.equals(piece.getProperty(Properties.NON_MOVABLE))) {
                    db.remove(piece);
                }
            }

            // Bail out if this leaves no pieces to drag.
            if (db.isEmpty()) return null;

            final GamePiece piece = db.getIterator().nextPiece();

            final Map map = dge.getComponent() instanceof Map.View ?
                    ((Map.View) dge.getComponent()).getMap() : null;

            final Point mousePosition = dge.getDragOrigin(); //BR// Bug13137 - now that we're not pre-adulterating dge's event, it already arrives in component coordinates

            Point piecePosition = piece.getPosition();

            // If DragBuffer holds a piece with invalid coordinates (for example, a
            // card drawn from a deck), drag from center of piece
            if (piecePosition.x <= 0 || piecePosition.y <= 0) {
                piecePosition = map == null ? mousePosition :
                        map.componentToMap(mousePosition);
            }

            // If coming from a map, we use the map's zoom. Otherwise if our
            // PieceWindow has stashed a starting scale for us then use that, else 1.0
            if (map != null) {
                // Account for offset of piece within stack. We do this even for
                // un-expanded stacks, since the offset can still be significant if
                // the stack is large
                final Stack parent = piece.getParent();
                if (parent != null) {
                    final Point offset = parent.getStackMetrics()
                            .relativePosition(parent, piece);
                    piecePosition.translate(offset.x, offset.y);
                }

                piecePosition = map.mapToComponent(piecePosition);

                dragPieceOffCenterZoom = map.getZoom();
            }
            else {
                // NB: In the case where there is no map, piecePosition is already
                // in the component coordinate system, so we don't convert here.

                final Object tempZoom = piece.getProperty(PieceSlot.PIECE_PALETTE_SCALE);
                if (tempZoom != null) {
                    final BasicPiece bp = (BasicPiece)Decorator.getInnermost(piece);
                    bp.setPersistentProperty(PieceSlot.PIECE_PALETTE_SCALE, null);

                    if (tempZoom instanceof Double) {
                        dragPieceOffCenterZoom = (Double)tempZoom;
                    }
                    else {
                        dragPieceOffCenterZoom = Double.parseDouble((String)tempZoom);
                    }
                }
                else {
                    dragPieceOffCenterZoom = 1.0;
                }
            }

            // dragging from UL results in positive offsets
            originalPieceOffsetX = piecePosition.x - mousePosition.x;
            originalPieceOffsetY = piecePosition.y - mousePosition.y;

            return mousePosition;
        }

        /**
         * The the Drag Gesture Recognizer that we're officially beginning a drag.
         * @param dge DG event
         */
        protected void beginDragging(DragGestureEvent dge) {
            final double os_scale = getDeviceScale(dge);

            // this call is needed to instantiate the boundingBox object
            final BufferedImage bImage = makeDragImage(dragPieceOffCenterZoom, os_scale);

            final Point dragPointOffset = new Point(
                    (int) Math.round(getOffsetMult() * ((boundingBoxComp.x + currentPieceOffsetX) * os_scale - EXTRA_BORDER)),
                    (int) Math.round(getOffsetMult() * ((boundingBoxComp.y + currentPieceOffsetY) * os_scale - EXTRA_BORDER))
            );

            //BR// Inform PieceMovers of relevant metrics
            //JY for (final PieceMover pieceMover : pieceMovers) {
            for (final ASLPieceMover pieceMover : pieceMovers) {
                pieceMover.setCurPieceOffset(currentPieceOffsetX, currentPieceOffsetY);
                pieceMover.setBreachedThreshold(true);
            }

            dge.startDrag(
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                    GlobalOptions.getInstance().isForceNonNativeDrag() ? null : bImage,
                    dragPointOffset,
                    new StringSelection(""),
                    this
            );

            dge.getDragSource().addDragSourceMotionListener(this);

            // Let our map's KeyBufferer know that this is now a drag not a click.
            final Map map = dge.getComponent() instanceof Map.View ?
                    ((Map.View) dge.getComponent()).getMap() : null;
            if (map != null) {
                final KeyBufferer kb = map.getKeyBufferer();
                if (kb != null) {
                    kb.dragStarted();
                }
            }
        }

        /**************************************************************************
         * DRAG SOURCE LISTENER INTERFACE
         * @param e DragSourceDropEvent
         **************************************************************************/
        @Override
        public void dragDropEnd(DragSourceDropEvent e) {
            final DragSource ds = e.getDragSourceContext().getDragSource();
            ds.removeDragSourceMotionListener(this);
        }

        @Override
        public void dragEnter(DragSourceDragEvent e) {}

        @Override
        public void dragExit(DragSourceEvent e) {}

        @Override
        public void dragOver(DragSourceDragEvent e) {}

        @Override
        public void dropActionChanged(DragSourceDragEvent e) {}

        /*************************************************************************
         * DRAG SOURCE MOTION LISTENER INTERFACE
         *
         * EVENT uses UNSCALED, SCREEN coordinate system
         *
         * Moves cursor after mouse. Used to check for real mouse movement.
         * Warning: dragMouseMoved fires 8 times for each point on development
         * system (Win2k)
         ************************************************************************/
        @Override
        public void dragMouseMoved(DragSourceDragEvent dsde) {
            if (dsde.getDragSourceContext().getComponent() instanceof Map.View) {
                final Map map = ((Map.View) dsde.getDragSourceContext().getComponent()).getMap();

                Point pt = dsde.getLocation();
                SwingUtilities.convertPointFromScreen(pt, map.getComponent());
                pt.translate(currentPieceOffsetX, currentPieceOffsetY);
                pt = map.componentToMap(pt);

                final DebugControls dc = GameModule.getGameModule().getDebugControls();
                dc.setCursorLocation(pt, map);
            }
        }

        /**************************************************************************
         * DROP TARGET INTERFACE
         *
         * EVENT uses UNSCALED, DROP-TARGET coordinate system
         *
         * dragEnter - switches current drawWin when mouse enters a new DropTarget
         **************************************************************************/
        @Override
        public void dragEnter(DropTargetDragEvent e) {
            final DropTargetListener forward = getListener(e);
            if (forward != null) {
                forward.dragEnter(e);
            }
        }

        /**************************************************************************
         * DROP TARGET INTERFACE
         *
         * EVENT uses UNSCALED, DROP-TARGET coordinate system
         *
         * drop() - Last event of the drop operation. We adjust the drop point for
         * off-center drag, remove the cursor, and pass the event along
         * listener chain.
         **************************************************************************/
        @Override
        public void drop(DropTargetDropEvent e) {
            // EVENT uses UNSCALED, DROP-TARGET coordinate system
            e.getLocation().translate(currentPieceOffsetX, currentPieceOffsetY);
            final DropTargetListener forward = getListener(e);
            if (forward != null) {
                forward.drop(e);
            }
        }

        /** Passes event along listener chain */
        @Override
        public void dragExit(DropTargetEvent e) {
            final DropTargetListener forward = getListener(e);
            if (forward != null) forward.dragExit(e);
        }

        /** Passes event along listener chain */
        @Override
        public void dragOver(DropTargetDragEvent e) {
            final DropTargetListener forward = getListener(e);
            if (forward != null) forward.dragOver(e);
        }

        /** Passes event along listener chain */
        @Override
        public void dropActionChanged(DropTargetDragEvent e) {
            final DropTargetListener forward = getListener(e);
            if (forward != null) forward.dropActionChanged(e);
        }
    }

    //JY
    //Duplicate the various VASSAL PieceMover.DragHandler classes to make sure these extend ASLPieceMover, not PieceMover
    //Taken from VASSAL 3.7.9
    /**
     * VASSAL's front-line drag handler for drag-and-drop of pieces.
     *
     * Implementation of AbstractDragHandler when DragImage is supported by JRE.
     * {@link PieceMover.DragHandlerMacOSX} extends this for special Mac platform
     *
     * @author Pieter Geerkens
     */
    //JY public static class DragHandler extends PieceMover.AbstractDragHandler {
    public static class DragHandler extends ASLPieceMover.AbstractDragHandler {
        @Override
        public void dragGestureRecognized(DragGestureEvent dge) {
            if (dragGestureRecognizedPrep(dge) != null) {
                super.dragGestureRecognized(dge);
            }
        }

        @Override
        protected int getOffsetMult() {
            return -1;
        }

        @Override
        protected double getDeviceScale(DragGestureEvent dge) {
            // Get the OS scaling; note that this handler is _probably_ running only
            // on Windows.
            final Graphics2D g2d = (Graphics2D) dge.getComponent().getGraphics();
            final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
            g2d.dispose();
            return os_scale;
        }

        @Override
        protected double getDeviceScale(DropTargetDragEvent e) {
            // Get the OS scaling; note that this handler is _probably_ running only
            // on Windows.
            final Graphics2D g2d = (Graphics2D) e.getDropTargetContext().getComponent().getGraphics();
            final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
            g2d.dispose();
            return os_scale;
        }
    }

    /**
     * Special MacOSX variant of DragHandler, because of differences in how
     * device scaling is handled.
     */
    //JY public static class DragHandlerMacOSX extends PieceMover.DragHandler {
    public static class DragHandlerMacOSX extends ASLPieceMover.DragHandler {
        @Override
        protected int getOffsetMult() {
            return 1;
        }

        @Override
        protected double getDeviceScale(DragGestureEvent dge) {
            // Retina Macs account for the device scaling for the drag icon,
            // so we don't have to.
            return 1.0;
        }

        @Override
        protected double getDeviceScale(DropTargetDragEvent e) {
            return 1.0;
        }
    }

    /**
     * Fallback drag-handler when DragImage not supported by JRE. Implements a
     * pseudo-cursor that follows the mouse cursor when user drags game pieces.
     * Supports map zoom by resizing cursor when it enters a drop target of
     * type Map.View.
     * <br>
     * @author Jim Urbas
     * @version 0.4.2
     */
    //JY public static class DragHandlerNoImage extends PieceMover.AbstractDragHandler {
    public static class DragHandlerNoImage extends ASLPieceMover.AbstractDragHandler {
        protected Component dragWin; // the component that initiated the drag operation
        protected Component dropWin; // the drop target the mouse is currently over
        protected JLayeredPane drawWin; // the component that owns our pseudo-cursor

        protected final Point drawOffset = new Point(); // translates event coords to local drawing coords

        @Override
        public void dragGestureRecognized(DragGestureEvent dge) {
            final Point mousePosition = dragGestureRecognizedPrep(dge);
            if (mousePosition == null) {return;}

            dragWin = dge.getComponent();
            drawWin = null;
            dropWin = null;

            makeDragCursor(dragPieceOffCenterZoom, getDeviceScale(dge));
            setDrawWinToOwnerOf(dragWin);
            SwingUtilities.convertPointToScreen(mousePosition, drawWin);
            moveDragCursor(mousePosition.x, mousePosition.y);

            super.dragGestureRecognized(dge);
        }

        /**
         * Installs the cursor image into our dragCursor JLabel.
         * Sets current zoom. Should be called at beginning of drag
         * and whenever zoom changes. INPUT: DragBuffer.getBuffer OUTPUT:
         * dragCursorZoom cursorOffCenterX cursorOffCenterY boundingBox
         * @param zoom DragBuffer.getBuffer
         *
         */
        protected void makeDragCursor(double zoom, double os_scale) {
            // create the cursor if necessary
            if (dragCursor == null) {
                dragCursor = new JLabel();
                dragCursor.setVisible(false);
            }

            final BufferedImage img = makeDragImageCursorCommon(zoom, os_scale, true, dragCursor);
            dragCursor.setSize(img.getWidth(), img.getHeight());
            dragCursor.setIcon(new ImageIcon(img));
            dragCursorZoom = zoom;
        }

        /**
         * Moves the drag cursor on the current draw window
         * @param dragX x position
         * @param dragY y position
         */
        @Override
        protected void moveDragCursor(int dragX, int dragY) {
            if (drawWin != null) {
                dragCursor.setLocation(dragX - drawOffset.x, dragY - drawOffset.y);
            }
        }

        /**
         * Removes the drag cursor from the current draw window
         */
        @Override
        protected void removeDragCursor() {
            if (drawWin != null) {
                if (dragCursor != null) {
                    dragCursor.setVisible(false);
                    drawWin.remove(dragCursor);
                }
                drawWin = null;
            }
        }

        /**
         * creates or moves cursor object to given JLayeredPane. Usually called by setDrawWinToOwnerOf()
         * @param newDrawWin JLayeredPane that is to be our new drawWin
         */
        private void setDrawWin(JLayeredPane newDrawWin) {
            if (newDrawWin != drawWin) {
                // remove cursor from old window
                if (dragCursor.getParent() != null) {
                    dragCursor.getParent().remove(dragCursor);
                }
                if (drawWin != null) {
                    drawWin.repaint(dragCursor.getBounds());
                }
                drawWin = newDrawWin;
                calcDrawOffset();
                dragCursor.setVisible(false);
                drawWin.add(dragCursor, JLayeredPane.DRAG_LAYER);
            }
        }

        /** calculates the offset between cursor dragCursor positions */
        @Override
        protected void calcDrawOffset() {
            if (drawWin != null) {
                // drawOffset is the offset between the mouse location during a drag
                // and the upper-left corner of the cursor
                // accounts for difference between event point (screen coords)
                // and Layered Pane position, boundingBox and off-center drag
                drawOffset.x = -boundingBoxComp.x - currentPieceOffsetX + EXTRA_BORDER;
                drawOffset.y = -boundingBoxComp.y - currentPieceOffsetY + EXTRA_BORDER;
                SwingUtilities.convertPointToScreen(drawOffset, drawWin);
            }
        }
        public void setDrawWinToOwnerOf(Component newDropWin) {
            if (newDropWin != null) {
                JRootPane rootWin = SwingUtilities.getRootPane(newDropWin);
                if (rootWin != null) {
                    this.setDrawWin(rootWin.getLayeredPane());
                }
            }

        }

        @Override
        protected int getOffsetMult() {
            return 1;
        }

        @Override
        protected double getDeviceScale(DragGestureEvent dge) {
            return 1.0;
        }

        @Override
        protected double getDeviceScale(DropTargetDragEvent e) {
            return 1.0;
        }

        @Override
        public void dragDropEnd(DragSourceDropEvent e) {
            removeDragCursor();
            super.dragDropEnd(e);
        }

        @Override
        public void dragMouseMoved(DragSourceDragEvent e) {
            super.dragMouseMoved(e);
            if (!e.getLocation().equals(lastDragLocation)) {
                lastDragLocation = e.getLocation();

                moveDragCursor(e.getX(), e.getY());
                if (dragCursor != null && !dragCursor.isVisible()) {
                    dragCursor.setVisible(true);
                }
            }
        }

        @Override
        public void dragEnter(DropTargetDragEvent e) {
            final Component newDropWin = e.getDropTargetContext().getComponent();
            if (newDropWin != dropWin) {
                final double newZoom = newDropWin instanceof Map.View
                        ? ((Map.View) newDropWin).getMap().getZoom() : 1.0;
                if (Math.abs(newZoom - dragCursorZoom) > 0.01) {
                    makeDragCursor(newZoom, getDeviceScale(e));
                }
                setDrawWinToOwnerOf(e.getDropTargetContext().getComponent());
                dropWin = newDropWin;
            }
            super.dragEnter(e);
        }

        @Override
        public void drop(DropTargetDropEvent e) {
            removeDragCursor();
            super.drop(e);
        }
    }

    private class MatMover extends MatHolder {

        public MatMover(GamePiece piece) {
            super(piece);
        }

        @Override public void grabCargo(List<GamePiece> allCargo) {
            super.grabCargo(allCargo);
            // Re-order the cargo by stack by position in stack so that we can move
            // it in the correct order to maintain stacking order.
            final List<GamePiece> tempCargo = new ArrayList<>();
            for (final GamePiece c : getCargo()) {
                final GamePiece parent = c.getParent();
                // If this piece belongs to a Stack and does not already exist in tempCargo, then
                // copy the entire contents of the Stack to tempCargo bottom up
                if (parent instanceof Stack) {
                    if (!tempCargo.contains(c)) {
                        final Stack s = (Stack) parent;
                        for (int i = 0; i < s.getPieceCount(); i++) {
                            tempCargo.add(s.getPieceAt(i));
                        }
                    }
                }
                // Not in a Stack, just copy it.
                else {
                    tempCargo.add(c);
                }
            }
            setCargo(tempCargo);
        }
    }
    //JY
}
