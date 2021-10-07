/*
 * $Id: VASLThread 11/25/13 davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Enumeration;

import javax.swing.JCheckBox;

import VASL.LOS.Map.LOSResult;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Map;
import VASL.build.module.ASLMap;
import VASL.LOS.VASLGameInterface;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.counters.ASLProperties;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.map.LOS_Thread;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.command.Command;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.ColorConfigurer;

import static VASSAL.build.GameModule.getGameModule;
// Needed to enable LOS checking on boards with overlays
import VASL.build.module.map.boardPicker.Overlay;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
// added as part of fixing remote event problem DR
import VASSAL.tools.SequenceEncoder;
import VASSAL.tools.swing.SwingUtils;

import java.util.LinkedList;
import java.awt.image.BufferedImage;
import java.awt.Image;
import java.awt.Graphics2D;

public class VASLThread extends LOS_Thread implements KeyListener, GameComponent {

    private static final String ENABLED = "LosCheckEnabled";
    private static final String HINDRANCE_THREAD_COLOR = "hindranceThreadColor";
    private static final String BLOCKED_THREAD_COLOR = "blockedThreadColor";
    private boolean legacyMode;
    private boolean initialized; // LOS has been initialized?
    private static final String preferenceTabName = "LOS";
    private VASL.LOS.Map.Map LOSMap;

    // Needed to enable LOS checking on boards with overlays
    private LinkedList<Rectangle> overlayBoundaries = new LinkedList<Rectangle>();
    private LinkedList<GamePiece> draggableOverlays = new LinkedList<GamePiece>();
    private Rectangle showovrboundaries;
    private GamePiece draggableOverlay;

    // LOS stuff
    private LOSResult result;
    private Location source;
    private Location target;
    private VASLGameInterface VASLGameInterface;
    private ASLBoard upperLeftBoard;
    private boolean useAuxSourceLOSPoint;
    private boolean useAuxTargetLOSPoint;
    private String resultsString = "";
    // fixing remote event problem
    private double sourcelevel;
    private double targetlevel;
    // LOS colors
    private Color LOSColor;
    private Color hindranceColor;
    private Color blockedColor;
    private double magnification;
    private void setGridSnapToVertex(boolean toVertex) {
        for (Board b : map.getBoards()) {
            HexGrid grid =
                    (HexGrid)b.getGrid();
            grid.setCornersLegal(toVertex);
            grid.setEdgesLegal(!toVertex);
        }
    }

    /**
     * Called when the LOS check is started
     */
    @Override
	protected void launch() {

        super.launch();

        VASLLOSButtonCommand vasllosbuttonCommand= new VASLLOSButtonCommand(this, false);
        GameModule.getGameModule().sendAndLog(vasllosbuttonCommand);
        setGridSnapToVertex(true);
        initializeMap();

    }

    private void initializeMap(){

        // make sure we have a map otherwise disable LOS
        String VASLVersion=GameModule.getGameModule().getGameVersion();
        final ASLMap theMap = (ASLMap) map;
        if(theMap == null || theMap.isLegacyMode()) {
            legacyMode = true;
        }
        else {
            legacyMode = false;
            LOSMap = theMap.getVASLMap();

            // initialize LOS
            result = new LOSResult();
            VASLGameInterface = new VASLGameInterface(theMap, LOSMap);
            VASLGameInterface.updatePieces();

            // setting these to null prevents the last LOS from being shown when launched
            source = null;
            target = null;
            sourcelevel=0;
            targetlevel=0;


            // set the boundaries of overlay rectangles to limit LOS Checking disablement
            try {
                overlayBoundaries.clear();
                for (Board board : theMap.getBoards()) {
                    ASLBoard b = (ASLBoard) board;
                    magnification = b.getMagnification();
                    final Enumeration overlays = b.getOverlays();
                    while (overlays.hasMoreElements()) {
                        Overlay o = (Overlay) overlays.nextElement();

                        // ignore terrain transformation overlays which cover most/all of the board
                        // treat BSO and SSR overlays as regular overlays; won't disable los for entire board DR Dec 2020
                        if((!o.hex1.equals("")) || o.getName().contains("BSO") || o.getName().contains("SSO")) {

                            Rectangle ovrRec= o.bounds();
                            // get the image as a buffered image
                            Image i = o.getImage();
                            BufferedImage bi = new BufferedImage(i.getWidth(null), i.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                            Graphics2D bGr = bi.createGraphics();
                            bGr.drawImage(i, 0, 0, null);
                            bGr.dispose();

                            boolean firstPixel = true;
                            int minx = 0;
                            int miny = 0;
                            int maxx = 0;
                            int maxy = 0;
                            for(int x = 0; x < bi.getWidth(); x++){
                                for(int y = 0; y < bi.getHeight(); y++){

                                    int c = bi.getRGB(x, y);
                                    if( (c>>24) != 0x00 ) { // not a transparent pixel
                                        if (firstPixel){
                                            minx = x;
                                            maxx = x;
                                            miny = y;
                                            maxy = y;
                                            firstPixel = false;
                                        }
                                        else {
                                            minx = Math.min(minx,x);
                                            maxx = Math.max(maxx,x);
                                            miny = Math.min(miny, y);
                                            maxy = Math.max(maxy,y);
                                        }
                                    }
                                }
                            }
                            //Set the boundaries rectangle
                            Rectangle ovrMinbounds= new Rectangle(ovrRec.x+minx, ovrRec.y+miny, maxx - minx, maxy - miny);
                            // Need to adjust y value when board cropped by coordinates
                            Rectangle CropAdjust = b.getCropBounds();
                            ovrMinbounds.y -= CropAdjust.y;
                            //Now check if need to flip
                            if (b.isReversed()) {
                                // flip moves x,y point to bottom right, subtracting width and height resets it to top left
                                ovrMinbounds.x = b.bounds().width - ovrMinbounds.x - 1;
                                ovrMinbounds.y = b.bounds().height - ovrMinbounds.y - 1;
                                ovrMinbounds.x -= ovrMinbounds.width;
                                ovrMinbounds.y -= ovrMinbounds.height;
                            }
                            //Now adjust for multiple rows and columns
                            ovrMinbounds.x = ovrMinbounds.x + b.bounds().x - 400;
                            ovrMinbounds.y = ovrMinbounds.y + b.bounds().y - 400;
                            if(o.getFile().getName().equalsIgnoreCase("ovrH") || o.getFile().getName().equalsIgnoreCase("ovrD")
                                    || o.getFile().getName().equalsIgnoreCase("ovrW") || o.getFile().getName().equalsIgnoreCase("ovrSD")) {
                            } else {
                                overlayBoundaries.add(ovrMinbounds);
                            }
                        }
                    }
                }

                // get the set of new draggable overlays
                GamePiece[] p = theMap.getPieces();
                for (GamePiece aP : p) {
                    if (aP instanceof Stack) {
                        for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                            GamePiece p2 = pi.nextPiece();
                            if(p2.getProperty("overlay") != null){
                                draggableOverlays.add(p2);
                            }
                        }
                    } else {
                        if(aP.getProperty("overlay") != null){
                            draggableOverlays.add(aP);
                        }
                    }
                }
            }
            catch (Exception e) {
                // bury any exception
            }
        }

        // grab the user preferences for the thread colors
        super.threadColor = (Color) getGameModule().getPrefs().getValue(LOS_COLOR);
        LOSColor = (Color) getGameModule().getPrefs().getValue(LOS_COLOR);
        if (LOSPrefActive()) {

            LOSColor = (Color) getGameModule().getPrefs().getValue(LOS_COLOR);
            hindranceColor = (Color) getGameModule().getPrefs().getValue(HINDRANCE_THREAD_COLOR);
            blockedColor = (Color) getGameModule().getPrefs().getValue(BLOCKED_THREAD_COLOR);
        }

        initialized = true;
    }

    @Override
	public void addTo(Buildable buildable) {

        super.addTo(buildable);
        idMgr.add(this);
        // add the key listener
        map.getView().addKeyListener(this);

        // add additional thread colors
        final BooleanConfigurer enable = new BooleanConfigurer(ENABLED, "Enable LOS checking", Boolean.TRUE);
        final JCheckBox enableBox = findBox(enable.getControls());

        final ColorConfigurer thread = new ColorConfigurer(LOS_COLOR, "Thread Color", Color.red);
        final ColorConfigurer hindrance = new ColorConfigurer(HINDRANCE_THREAD_COLOR, "Hindrance Thread Color", Color.red);
        final ColorConfigurer blocked = new ColorConfigurer(BLOCKED_THREAD_COLOR, "Blocked Thread Color", Color.blue);
        final BooleanConfigurer verbose = new BooleanConfigurer("verboseLOS", "Verbose LOS mode");
        getGameModule().getPrefs().addOption(preferenceTabName, thread);
        getGameModule().getPrefs().addOption(preferenceTabName, enable);
        getGameModule().getPrefs().addOption(preferenceTabName, hindrance);
        getGameModule().getPrefs().addOption(preferenceTabName, blocked);
        getGameModule().getPrefs().addOption(preferenceTabName, verbose);
        final ItemListener l = new ItemListener() {

			public void itemStateChanged(ItemEvent evt) {
                enableAll(hindrance.getControls(), enableBox.isSelected());
                enableAll(blocked.getControls(), enableBox.isSelected());
                enableAll(verbose.getControls(), enableBox.isSelected());
            }
        };
        enableBox.addItemListener(l);
        enableAll(hindrance.getControls(), Boolean.TRUE.equals(enable.getValue()));
        enableAll(blocked.getControls(), Boolean.TRUE.equals(enable.getValue()));
        enableAll(verbose.getControls(), Boolean.TRUE.equals(enable.getValue()));

        // hook for game opening/closing
        getGameModule().getGameState().addGameComponent(this);
    }

    protected static JCheckBox findBox(Component c) {
        JCheckBox val = null;
        if (c instanceof JCheckBox) {
            val = (JCheckBox) c;
        }
        for (int i = 0; i < ((Container) c).getComponentCount(); ++i) {
            val = findBox(((Container) c).getComponent(i));
            if (val != null) {
                break;
            }
        }
        return val;
    }

    private static void enableAll(Component c, boolean enable) {
        c.setEnabled(enable);
        if (c instanceof Container) {
            for (int i = 0; i < ((Container) c).getComponentCount(); ++i) {
                enableAll(((Container) c).getComponent(i), enable);
            }
        }
    }

    @Override
	public void mousePressed(MouseEvent e) {

        super.mousePressed(e);
        if (!isEnabled() || legacyMode) {
            return;
        }

        setSourceFromMousePressedEvent(new Point(e.getPoint()));

        if(source == null) {
            return;
        }

        // if Ctrl click, use upper-most non-rooftop location
        if (SwingUtils.isSelectionToggle(e)) { //BR// Vassal 3.3 mouse interface adjustment
            while (source.getUpLocation() != null && !source.getUpLocation().getName().contains("Rooftop")) {
                source = source.getUpLocation();
            }
        }
        double leveladj=0;
        if(source.getName().contains("Rooftop")) {
            leveladj=-0.5;
        }
        if(source.getHex().isDepressionTerrain() && !source.isCenterLocation()) {
            leveladj=+1;
        }
        sourcelevel= source.getBaseHeight() + source.getHex().getBaseHeight() + leveladj ;

        // make the source and the target the same
        target = source;
        useAuxTargetLOSPoint = useAuxSourceLOSPoint;

    }

    /**
     * Sets the source location using a mouse-pressed event point
     * @param eventPoint the point in mouse pressed coordinates
     */
    private void setSourceFromMousePressedEvent(Point eventPoint) {
        try {
            final Point p = mapMouseToMapCoordinates(eventPoint);
            adjustformagnification(p);
            if (p == null || !LOSMap.onMap(p.x, p.y)) {
                source = null;
            } else {
                source = LOSMap.gridToHex(p.x, p.y).getNearestLocation(p.x, p.y);
                useAuxSourceLOSPoint = useAuxLOSPoint(source, p.x, p.y);
            }
        }
        catch (Exception e) {
            return;
        }
    }
    // adjust the p.x and p.y values to reflect any magnification (such as using deluxe size hexes; not zooming)
    private void adjustformagnification(Point adjustpoint){
        adjustpoint.x = (int) Math.round(adjustpoint.x / magnification);
        adjustpoint.y = (int) Math.round(adjustpoint.y / magnification);
    }
    private void adjustformagnification(Point adjustpoint, Point breakpoint){
        adjustpoint.x = (int) Math.round(breakpoint.x * magnification);
        adjustpoint.y = (int) Math.round(breakpoint.y * magnification);
    }
    @Override
	public void mouseReleased(MouseEvent e) {

        if(!super.persisting && !super.mirroring) {
            VASLLOSCommand vasllosCommand;
            if(!super.retainAfterRelease || super.ctrlWhenClick && super.persistence.equals("Ctrl-Click & Drag")) {
                if(e.getWhen() != super.lastRelease) {
                    super.visible = false;
                    super.getLaunchButton().setEnabled(true);
                    if(super.global.equals("Always") || super.global.equals("When Persisting")) {
                        if(!super.persistence.equals("Always") && (!super.ctrlWhenClick || !super.persistence.equals("Ctrl-Click & Drag"))) {
                            vasllosCommand = new VASLLOSCommand(this, this.getAnchor(), this.getArrow(), false, false, sourcelevel,   targetlevel);
                            GameModule.getGameModule().sendAndLog(vasllosCommand);
                        } else {
                            super.anchor = super.lastAnchor;
                            vasllosCommand = new VASLLOSCommand(this, this.getAnchor(), this.getArrow(), true, false, sourcelevel,   targetlevel);
                            GameModule.getGameModule().sendAndLog(vasllosCommand);
                            super.setPersisting(true);
                        }
                    }

                    VASLLOSButtonCommand vasllosbuttonCommand= new VASLLOSButtonCommand(this, true);
                    GameModule.getGameModule().sendAndLog(vasllosbuttonCommand);
                    super.map.setPieceOpacity(1.0F);
                    super.map.popMouseListener();
                    super.map.repaint();
                }
            } else {
                super.retainAfterRelease = false;
                if(super.global.equals("Always")) {
                    vasllosCommand = new VASLLOSCommand(this, this.getAnchor(), this.getArrow(), false, true, sourcelevel,   targetlevel);
                    GameModule.getGameModule().sendAndLog(vasllosCommand);
                }
            }

            this.lastRelease = e.getWhen();
            if(super.getLosCheckCount() > 0) {
                super.reportFormat.setProperty("FromLocation", super.anchorLocation);
                super.reportFormat.setProperty("ToLocation", super.lastLocation);
                if (LOSMap == null) {
                    super.reportFormat.setProperty("Range", super.lastRange);
                } else if(source == null || target == null ) {
                    return;
                } else {
                    super.reportFormat.setProperty("Range", String.valueOf(Map.range(source.getHex(), target.getHex(), LOSMap.getMapConfiguration()))); //super.lastRange);
                }
                super.reportFormat.setProperty("NumberOfLocationsChecked", String.valueOf(super.getLosCheckCount()));
                super.reportFormat.setProperty("AllLocationsChecked", super.getLosCheckList());
                GameModule.getGameModule().getChatter().send(super.reportFormat.getLocalizedText());
            }
        }

        super.ctrlWhenClick = false;

        if (!isVisible()) {
            setGridSnapToVertex(false);
        }
    }

    @Override
	public void mouseDragged(MouseEvent e) {

        if (!legacyMode && source != null && isEnabled()) {

            final Location oldLocation = target;
            final boolean oldAuxFlag = useAuxTargetLOSPoint;

            setTargetFromMouseDraggedEvent(new Point(e.getPoint()));

            // are we really in a new location?
            if (target == null || (target.equals(oldLocation) && useAuxTargetLOSPoint == oldAuxFlag)) {
                return;
            }

            // if Ctrl click, use upper-most non-rooftop location
            if (e.isControlDown()) {
                while (target.getUpLocation() != null && !target.getUpLocation().getName().contains("Rooftop")) {
                    target = target.getUpLocation();
                }
            }
            double leveladj=0;
            if(target.getName().contains("Rooftop")) {
                leveladj=-0.5;
            }
            if(target.getHex().isDepressionTerrain() && !target.isCenterLocation()) {
                leveladj=+1;
            }
            targetlevel= target.getBaseHeight() + target.getHex().getBaseHeight() + leveladj ;

        }
        super.mouseDragged(e);
        map.repaint();
    }

    /**
     * Sets the target using a mouse-dragged event point
     * @param eventPoint the point in mouse dragged coordinates
     */
    private void setTargetFromMouseDraggedEvent(Point eventPoint) {
        try {
            final Point p = map.componentToMap(eventPoint);
            p.translate(-map.getEdgeBuffer().width, -map.getEdgeBuffer().height);
            adjustformagnification(p);
            if (p == null || !LOSMap.onMap(p.x, p.y)) return;
            target = LOSMap.gridToHex(p.x, p.y).getNearestLocation(p.x, p.y);
            useAuxTargetLOSPoint = useAuxLOSPoint(target, p.x, p.y);
        }
        catch (Exception e) {
            // trap error - no need for action DR
        }
    }

    /**
     * Sets the target using a remote event point
     * @param eventPoint the point in remote event coordinates
     */
    private void setTargetFromRemoteEvent(Point eventPoint) {

        final Point p = mapMouseToMapCoordinates(eventPoint);
        adjustformagnification(p);
        //
        if (p == null || !LOSMap.onMap(p.x, p.y)) return;
        target = LOSMap.gridToHex(p.x, p.y).getNearestLocation(p.x, p.y);
        useAuxTargetLOSPoint = useAuxLOSPoint(target, p.x, p.y);

    }

    private boolean isEnabled() {
        return visible && LOSPrefActive();
    }

    private static boolean LOSPrefActive() {
        return Boolean.TRUE.equals(getGameModule().getPrefs().getValue(ENABLED));
    }

    @Override
    public void draw(Graphics g, VASSAL.build.module.Map m) {

        if (!LOSPrefActive() || legacyMode || !visible) {
            super.draw(g, m);
            return;
        }

        final Graphics2D g2d = (Graphics2D) g;
        final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();

        lastAnchor = map.mapToDrawing(anchor, os_scale);
        lastArrow = map.mapToDrawing(arrow, os_scale);

        if (source != null && target != null) {
            // source LOS point
            Point sourceLOSPoint;
            if (useAuxSourceLOSPoint) {
                sourceLOSPoint = new Point(source.getAuxLOSPoint());
            }
            else {
                sourceLOSPoint = new Point(source.getLOSPoint());
            }
            adjustformagnification(sourceLOSPoint, sourceLOSPoint);
            // preserve point on map for overlay checking
            Point sourcestart = sourceLOSPoint;
            sourceLOSPoint = mapPointToScreen(sourceLOSPoint, os_scale);

            // target LOS point
            Point targetLOSPoint;
            if (useAuxTargetLOSPoint) {
                targetLOSPoint = new Point(target.getAuxLOSPoint());
            }
            else {
                targetLOSPoint = new Point(target.getLOSPoint());
            }
            adjustformagnification(targetLOSPoint, targetLOSPoint);
            // preserve point on map for overlay checking
            Point targetend = targetLOSPoint;
            targetLOSPoint = mapPointToScreen(targetLOSPoint, os_scale);

            // call overlay check function
            // set the LOS line from source to target - based on map not screen coordinates
            boolean losOnOverlay = false;
            // test if LOS line cross an overlay
            Line2D losline = new Line2D.Double(sourcestart.getLocation(), targetend.getLocation());
            if(!checkifLOScrossesOverlay(losline)) {
                try {
                    doLOS();
                } catch(Exception e) {

                }
            } else {
                losOnOverlay =true;
            }

            // transform the blocked-at point
            Point b = new Point(0,0);
            if (result.isBlocked()) {
                // adjust for magnification such as using deluxe sized hexes
                adjustformagnification(b, result.getBlockedAtPoint());
                b = mapPointToScreen(b, os_scale);
            }
            // transform the hindrance point
            Point h = new Point(0,0);
            if (result.hasHindrance()) {
                // adjust for magnification such as using deluxe sized hexes
                adjustformagnification(h, result.firstHindranceAt());
                h = mapPointToScreen(h, os_scale);
            }
            // draw the LOS thread
            if (losOnOverlay) {
                // if crosses overlay draw straight line in default color
                g.setColor(LOSColor);
                g.drawLine(
                        sourceLOSPoint.x,
                        sourceLOSPoint.y,
                        targetLOSPoint.x,
                        targetLOSPoint.y);
            }
            else {
                if (result.isBlocked()) {
                    if (result.hasHindrance()) {
                        g.setColor(LOSColor);
                        g.drawLine(
                                sourceLOSPoint.x,
                                sourceLOSPoint.y,
                                h.x,
                                h.y);
                        g.setColor(hindranceColor);
                        g.drawLine(
                                h.x,
                                h.y,
                                b.x,
                                b.y);
                        g.setColor(blockedColor);
                        g.drawLine(
                                b.x,
                                b.y,
                                targetLOSPoint.x,
                                targetLOSPoint.y);
                    } else {
                        g.setColor(LOSColor);
                        g.drawLine(
                                sourceLOSPoint.x,
                                sourceLOSPoint.y,
                                b.x,
                                b.y);
                        g.setColor(blockedColor);
                        g.drawLine(
                                b.x,
                                b.y,
                                targetLOSPoint.x,
                                targetLOSPoint.y);
                    }
                } else if (result.hasHindrance()) {
                    g.setColor(LOSColor);
                    g.drawLine(
                            sourceLOSPoint.x,
                            sourceLOSPoint.y,
                            h.x,
                            h.y);
                    g.setColor(hindranceColor);
                    g.drawLine(
                            h.x,
                            h.y,
                            targetLOSPoint.x,
                            targetLOSPoint.y);
                } else {
                    g.setColor(LOSColor);
                    g.drawLine(
                            sourceLOSPoint.x,
                            sourceLOSPoint.y,
                            targetLOSPoint.x,
                            targetLOSPoint.y);
                }
            }
            // use the draw range property to turn all text on/off
            if (drawRange) {
                // determine if the text should be above or below the location
                final boolean shiftSourceText = sourceLOSPoint.y > targetLOSPoint.y;
                final int shift = g.getFontMetrics().getHeight();

                // draw the source elevation
                switch (source.getBaseHeight() + source.getHex().getBaseHeight()) {
                    case -1:
                    case -2:
                        g.setColor(Color.red);
                        break;
                    case 0:
                        g.setColor(Color.gray);
                        break;
                    case 1:
                        g.setColor(Color.darkGray);
                        break;
                    case 2:
                        g.setColor(Color.black);
                        break;
                    default:
                        g.setColor(Color.white);
                }

                g.setFont(RANGE_FONT.deriveFont((float)(RANGE_FONT.getSize() * os_scale)));

                // inform user that LOS checking disabled due to overlay in LOS - show overlay boundaries and show text message
                if (losOnOverlay) {

                    Color oldcolor = g.getColor();
                    g.setColor(Color.red);
                    double ovrZoom = map.getZoom() * os_scale;

                    if (showovrboundaries != null) {

                        Point drawboundaries = new Point(showovrboundaries.x, showovrboundaries.y);
                        drawboundaries = mapPointToScreen(drawboundaries, os_scale);
                        // need to adjust width and height of overlay boundary display rectangle according to level of zoom; x,y are already handled
                        int ovrwidth = (int)(showovrboundaries.width * ovrZoom);
                        int ovrheight = (int)(showovrboundaries.height * ovrZoom);
                        g.drawRect(drawboundaries.x, drawboundaries.y, ovrwidth, ovrheight);
                    }
                    else if (draggableOverlay != null) {

                        int overlayWidth  = (int)(draggableOverlay.boundingBox().width * ovrZoom);
                        int overlayHeight = (int)(draggableOverlay.boundingBox().height * ovrZoom);
                        Point overlayCenter = new Point((int)(draggableOverlay.getPosition().x * ovrZoom), (int) (draggableOverlay.getPosition().y * ovrZoom));

                        g.drawRect(
                                overlayCenter.x - overlayWidth/2,
                                overlayCenter.y - overlayHeight/2,
                                overlayWidth,
                                overlayHeight);
                    }

                    g.setColor(oldcolor);
                    lastRangeRect.add(drawText(g, targetLOSPoint.x + targetLOSLabelXoffset(sourceLOSPoint, targetLOSPoint), targetLOSPoint.y + targetLOSLabelYoffset(sourceLOSPoint, targetLOSPoint) + (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                            "LOS Check Disabled - Overlay nearby. Range: " + Map.range(source.getHex(), target.getHex(), LOSMap.getMapConfiguration())));

                } else {
                    // code added by DR to handle rooftop levels
                    double leveladj = 0;
                    String sourcelevelString = "";
                    String targetlevelString = "";

                    if ((int)Math.round(sourcelevel) > (int)sourcelevel) {  //need to show decimal place
                        sourcelevelString = "Level " + sourcelevel;
                    }
                    else {  // hide decimal place
                        sourcelevelString = "Level " + (int)sourcelevel;
                    }
                    if (isVerbose()) {
                        lastRangeRect = drawText(g,
                                sourceLOSPoint.x - 20,
                                sourceLOSPoint.y - sourceLOSLabelYoffset(sourceLOSPoint, targetLOSPoint) - (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                                source.getName() + "  (" + sourcelevelString + ")");
                    } else if (source.getBaseHeight() != 0) {
                        lastRangeRect = drawText(g,
                                sourceLOSPoint.x - 20,
                                sourceLOSPoint.y - sourceLOSLabelYoffset(sourceLOSPoint, targetLOSPoint) -  (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                                sourcelevelString );
                    }

                    // draw the target elevation
                    switch (target.getBaseHeight() + target.getHex().getBaseHeight()) {
                        case -1:
                        case -2:
                            g.setColor(Color.red);
                            break;
                        case 0:
                            g.setColor(Color.gray);
                            break;
                        case 1:
                            g.setColor(Color.darkGray);
                            break;
                        case 2:
                            g.setColor(Color.black);
                            break;
                        default:
                            g.setColor(Color.white);
                    }

                    if ((int)Math.round(targetlevel) > (int)targetlevel) {  //need to show decimal place
                        targetlevelString="Level " + targetlevel;
                    }
                    else {  // hide decimal place
                        targetlevelString="Level " + (int)targetlevel;
                    }
                    if (isVerbose()) {
                        lastRangeRect.add(drawText(g,
                                targetLOSPoint.x + targetLOSLabelXoffset(sourceLOSPoint, targetLOSPoint), //- 20,
                                targetLOSPoint.y + targetLOSLabelYoffset(sourceLOSPoint, targetLOSPoint) + (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                                target.getName() + "  (" + targetlevelString + ")"));
                    } else if (target.getBaseHeight() != 0) {
                        lastRangeRect.add(drawText(g,
                                targetLOSPoint.x + targetLOSLabelXoffset(sourceLOSPoint, targetLOSPoint), //- 20,
                                targetLOSPoint.y + targetLOSLabelYoffset(sourceLOSPoint, targetLOSPoint) + (shiftSourceText ? 0 : shift) - g.getFontMetrics().getDescent(),
                                targetlevelString ));
                    }
                    // draw the verbose text
                    g.setColor(Color.black);
                    if (shiftSourceText) {
                        //lastRangeRect.add(drawText(g, targetLOSPoint.x - 20, targetLOSPoint.y - shift, resultsString));
                        lastRangeRect.add(drawText(g, targetLOSPoint.x + targetLOSLabelXoffset(sourceLOSPoint, targetLOSPoint), targetLOSPoint.y + targetLOSLabelYoffset(sourceLOSPoint, targetLOSPoint) - shift, resultsString));
                    }
                    else {
                        //lastRangeRect.add(drawText(g, targetLOSPoint.x - 20, targetLOSPoint.y + shift * 2 - 2, resultsString));
                        lastRangeRect.add(drawText(g, targetLOSPoint.x + targetLOSLabelXoffset(sourceLOSPoint, targetLOSPoint), targetLOSPoint.y + targetLOSLabelYoffset(sourceLOSPoint, targetLOSPoint)+ shift * 2 - 2, resultsString));
                    }

                }

            }
        }
    }

    private static boolean isVerbose() {
        return Boolean.TRUE.equals(getGameModule().getPrefs().getValue("verboseLOS"));
    }

	public void keyTyped(KeyEvent e) {
    }

	public void keyReleased(KeyEvent e) {
    }

	public void keyPressed(KeyEvent e) {

        if ((!isEnabled() && legacyMode) || !isVisible()) {
            return;
        }
        final int code = e.getKeyCode();
        // move up
        if (code == KeyEvent.VK_KP_UP || code == KeyEvent.VK_UP) {
            e.consume(); // prevents the map from scrolling when trying to move end point
            double leveladj=0;
            // move the source up
            if (e.isControlDown() && source != null) {
                if (source.getUpLocation() != null) {
                    source = source.getUpLocation();
                }else if (source.getBaseHeight()<10) {
                    source.getHex().setvirtualLocation(source.getBaseHeight() + 1, source, "Up");
                    source = source.getUpLocation();
                }
                leveladj=0;
                if(source.getName().contains("Rooftop")) {
                    if (!source.getDownLocation().getTerrain().getName().equals("Wooden Building")) {  // exception to handle Wooden Warehouses in bdRO
                        leveladj = -0.5;
                    }
                }
                if(source.getHex().isDepressionTerrain() && !source.isCenterLocation()) {
                    leveladj=+1;
                }
                sourcelevel= source.getBaseHeight() + source.getHex().getBaseHeight() + leveladj ;
                doLOS();
                map.repaint();
            }
            // move the target up
            else if (target != null) {
                if (target.getUpLocation() != null) {
                    target = target.getUpLocation();
                }else if (target.getBaseHeight()<10){
                    target.getHex().setvirtualLocation( target.getBaseHeight()+1, target, "Up");
                    target = target.getUpLocation();
                }
                leveladj=0;
                if(target.getName().contains("Rooftop")) {
                    if (!target.getDownLocation().getTerrain().getName().equals("Wooden Building")) {  // exception to handle Wooden Warehouses in bdRO
                        leveladj = -0.5;
                    }
                }
                if(target.getHex().isDepressionTerrain() && !target.isCenterLocation()) {
                    leveladj=+1;
                }
                targetlevel= target.getBaseHeight() + target.getHex().getBaseHeight() + leveladj ;
                doLOS();
                map.repaint();
            }
        }
        // move down
        else if (code == KeyEvent.VK_KP_DOWN || code == KeyEvent.VK_DOWN) {
            e.consume();
            double leveladj=0;
            // move the source down
            if (e.isControlDown() && source != null) {
                if (source.getDownLocation() != null) {
                    source = source.getDownLocation();
                }else if (source.getBaseHeight()>-3){
                    source.getHex().setvirtualLocation(source.getBaseHeight() - 1, source, "Down");
                    source = source.getDownLocation();
                }
                leveladj=0;
                if(source.getName().contains("Rooftop")) {
                    leveladj=-0.5;
                }
                if(source.getHex().isDepressionTerrain() && !source.isCenterLocation()) {
                    leveladj=+1;
                }
                sourcelevel= source.getBaseHeight() + source.getHex().getBaseHeight() + leveladj ;
                doLOS();
                map.repaint();
            }
            // move the target down
            else if (target != null) {
                if (target.getDownLocation() != null) {
                    target = target.getDownLocation();
                }else if (target.getBaseHeight()>-3){
                    target.getHex().setvirtualLocation(target.getBaseHeight() - 1, target, "Down");
                    target = target.getDownLocation();
                }
                leveladj=0;
                if(target.getName().contains("Rooftop")) {
                    leveladj=-0.5;
                }
                if(target.getHex().isDepressionTerrain() && !target.isCenterLocation()) {
                    leveladj=+1;
                }
                targetlevel= target.getBaseHeight() + target.getHex().getBaseHeight() + leveladj ;
                doLOS();
                map.repaint();
            }
        }
        else {
            return;
        }

        VASLLOSCommand vasllosCommand= new VASLLOSCommand(this, this.getAnchor(), this.getArrow(), true, false, sourcelevel,   targetlevel);
        GameModule.getGameModule().sendAndLog(vasllosCommand);

    }

    private static boolean useAuxLOSPoint(Location l, int x, int y) {

        final Point LOSPoint = l.getLOSPoint();
        final Point AuxLOSPoint = l.getAuxLOSPoint();

        // use the closest LOS point
		return Point2D.distance((double)x, (double)y, (double)LOSPoint.x, (double)LOSPoint.y) > Point2D.distance((double)x, (double)y, (double)AuxLOSPoint.x, (double)AuxLOSPoint.y);
	}

	public Command getRestoreCommand() {
        return null;
    }

    private Point mapPointToScreen(Point p, double os_scale) {
        final Point temp = map.mapToDrawing(p, os_scale);
        final double scale = upperLeftBoard == null ? 1.0 : upperLeftBoard.getMagnification() * ((HexGrid)upperLeftBoard.getGrid()).getHexSize()/ASLBoard.DEFAULT_HEX_HEIGHT;
        if (upperLeftBoard != null) {
            temp.x = (int)Math.round((double)temp.x * scale);
            temp.y = (int)Math.round((double)temp.y * scale);
        }
        temp.translate(
          (int) (map.getEdgeBuffer().width * map.getZoom() * os_scale),
          (int) (map.getEdgeBuffer().height * map.getZoom() * os_scale)
        );

        return temp;
    }

    private Point mapMouseToMapCoordinates(Point p) {

        // just remove edge buffer
        final Point temp = new Point(p);
        temp.translate(-map.getEdgeBuffer().width, -map.getEdgeBuffer().height);
        return temp;
    }

    /**
     * Draws some text on the map
     * @param g the map graphics
     * @param x upper left point
     * @param y upper left point
     * @param s the text
     * @return a bounding region of the text drawn
     */
    private static Rectangle drawText(Graphics g, int x, int y, String s) {

        // paint the background
        final int border = 1;

        g.setColor(Color.black);

        final Rectangle region = new Rectangle(
                x - border,
                y - border - g.getFontMetrics().getHeight() + g.getFontMetrics().getDescent(),
                g.getFontMetrics().stringWidth(s) + 2,
                g.getFontMetrics().getHeight() + 2);
        g.fillRect(region.x, region.y, region.width, region.height);

        // draw the text
        g.setColor(Color.white);
        g.drawString(s, x, y);
        return region;
    }

    /**
     * Execute the LOS
     */
    private void doLOS() {

        // silently ignore invalid LOS checks
        if(source == null || target == null || result == null || VASLGameInterface == null)
        {
            return;
        }

        // do the LOS
        result = new LOSResult();
        LOSMap.LOS(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, VASLGameInterface);

        try {
            // set the result string
            resultsString =
                    "Range: " + result.getRange();
            lastRange = String.valueOf(result.getRange());
            if (isVerbose()) {
                if (result.isBlocked()) {
                    resultsString += "  Blocked in " + LOSMap.gridToHex(result.getBlockedAtPoint().x, result.getBlockedAtPoint().y).getName() +
                            " ( " + result.getReason() + ")";
                } else {
                    resultsString += (result.getHindrance() > 0 ? ("  Hindrances: " + result.getHindrance()) : "");
                }
            }
        }
        catch (Exception e) {
            //trap error if hex not found - just report nothing DR
        }

    }

	// force a paint when remote LOS command received
	@Override
	public Command decode(String command) {
        SequenceEncoder.Decoder comdecode = null;
        if(command.startsWith("LOS\tLOS_Thread1")) {
            comdecode = new SequenceEncoder.Decoder(command, '\t');
            comdecode.nextToken();
            comdecode.nextToken();
            Point passanchor = new Point(comdecode.nextInt(0), comdecode.nextInt(0));
            Point passarrow = new Point(comdecode.nextInt(0), comdecode.nextInt(0));
            boolean passpersisting = comdecode.nextBoolean(false);
            boolean passmirroring = comdecode.nextBoolean(false);
            double passsourcelevel = comdecode.nextDouble(0);
            double passtargetlevel = comdecode.nextDouble(0);
            return new VASLThread.VASLLOSCommand(this, passanchor, passarrow, passpersisting, passmirroring, passsourcelevel, passtargetlevel);
        } else if(command.startsWith("VASLLOSButtonCommand\t")) {
            comdecode = new SequenceEncoder.Decoder(command, '\t');
            comdecode.nextToken();
            boolean passbuttonstatus = comdecode.nextBoolean(false);
            return new VASLThread.VASLLOSButtonCommand(this, passbuttonstatus);
        } else {
            return null;
        }
	}

    //@Override
    protected void setEndPointsandLevels(Point newAnchor, Point newArrow, double sourceLevel, double targetLevel) {
        anchor.x = newAnchor.x;
        anchor.y = newAnchor.y;
        arrow.x = newArrow.x;
        arrow.y = newArrow.y;

        initializeMap();

        if (!legacyMode) {
            setSourceFromMousePressedEvent(newAnchor);
            setTargetFromRemoteEvent(newArrow);
            setSourceandTargetLevels (sourceLevel, targetLevel);
            doLOS();
        }

        map.repaint();
    }
    protected void setSourceandTargetLevels(double newsourceLevel, double newtargetLevel) {
        double leveladj;
        if (source != null && target != null) {
            while (newsourceLevel > sourcelevel) {
                if (source.getUpLocation() != null) {
                    source = source.getUpLocation();
                } else {
                    break;
                }
                leveladj = 0;
                if (source.getName().contains("Rooftop")) {
                    leveladj = -0.5;
                }
                if (source.getHex().isDepressionTerrain() && !source.isCenterLocation()) {
                    leveladj = +1;
                }
                sourcelevel = source.getBaseHeight() + source.getHex().getBaseHeight() + leveladj;
            }
            while (newsourceLevel < sourcelevel) {
                if (source.getDownLocation() != null) {
                    source = source.getDownLocation();
                } else {
                    break;
                }
                leveladj = 0;
                if (source.getName().contains("Rooftop")) {
                    leveladj = -0.5;
                }
                if (source.getHex().isDepressionTerrain() && !source.isCenterLocation()) {
                    leveladj = +1;
                }
                sourcelevel = source.getBaseHeight() + source.getHex().getBaseHeight() + leveladj;
            }
            while (newtargetLevel > targetlevel) {
                if (target.getUpLocation() != null) {
                    target = target.getUpLocation();
                } else {
                    break;
                }
                leveladj = 0;
                if (target.getName().contains("Rooftop")) {
                    leveladj = -0.5;
                }
                if (target.getHex().isDepressionTerrain() && !target.isCenterLocation()) {
                    leveladj = +1;
                }
                targetlevel = target.getBaseHeight() + target.getHex().getBaseHeight() + leveladj;
            }
            while (newtargetLevel < targetlevel) {
                if (target.getDownLocation() != null) {
                    target = target.getDownLocation();
                } else {
                    break;
                }
                leveladj = 0;
                if (target.getName().contains("Rooftop")) {
                    leveladj = -0.5;
                }
                if (target.getHex().isDepressionTerrain() && !target.isCenterLocation()) {
                    leveladj = +1;
                }
                targetlevel = target.getBaseHeight() + target.getHex().getBaseHeight() + leveladj;
            }
        }
    }

    // check if LOS crosses an overlay; if so, set overlay boundaries to be shown
    private boolean checkifLOScrossesOverlay(Line2D losline) {

        try {
            // this is for standard overlays
            for (Rectangle ovrRec : overlayBoundaries) {

                if (losline.intersects(ovrRec.x, ovrRec.y, ovrRec.width, ovrRec.height)) {
                    showovrboundaries=ovrRec;
                    draggableOverlay = null;
                    return true;
                }
            }

            // this is for the new draggable overlays
            for (GamePiece p : draggableOverlays) {
                int overlayWidth  = p.boundingBox().width;
                int overlayHeight = p.boundingBox().height;
                Point overlayCenter = new Point (p.getPosition().x, p.getPosition().y);
                if (losline.intersects(
                        overlayCenter.x - overlayWidth/2  - map.getEdgeBuffer().width,
                        overlayCenter.y - overlayHeight/2 - map.getEdgeBuffer().height,
                        overlayWidth,
                        overlayHeight)){

                    draggableOverlay = p;
                    showovrboundaries = null;
                    return true;
                }
            }
        }
        catch (Exception e) {
            // bury any exception
        }

        return false;


    }

    // these two methods are used to push the LOS check text label away from the los thread so that the thread can be viewed clearly
    private int targetLOSLabelXoffset(Point sourceLOSpoint, Point targetLOSpoint) {
        if (sourceLOSpoint.getX() == targetLOSpoint.getX()) {
            // LOS is vertical; no need to adjust x, use standard offset
            return -10;
        } else if (sourceLOSpoint.getX() > targetLOSpoint.getX()) {
            // start point is to the right of end point, push label to the right
            return 10;
        } else if (sourceLOSpoint.getX() < targetLOSpoint.getX()) {
            // start point is to the left of end point, push label to the right
            return 10;
        }
        return 0; // should never get here
    }

    private int targetLOSLabelYoffset(Point sourceLOSpoint, Point targetLOSpoint) {
        if (sourceLOSpoint.getY() == targetLOSpoint.getY()) {
            // LOS is horizontal; push label above thread
            return -40;
        } else if (sourceLOSpoint.getX() == targetLOSpoint.getX() && sourceLOSpoint.getY() > targetLOSpoint.getY() ) {
            // LOS is vertical; start is below end; push label above thread
            return -20;
        } else if (sourceLOSpoint.getX() == targetLOSpoint.getX() && sourceLOSpoint.getY() < targetLOSpoint.getY() ) {
            // LOS is vertical; start is above end; push label below thread
            return 20;
        } else if (sourceLOSpoint.getY() > targetLOSpoint.getY() && sourceLOSpoint.getX() > targetLOSpoint.getX()) {
            // start point is below end point and start is right of end, push label above thread
            return -30;
        } else if (sourceLOSpoint.getY() > targetLOSpoint.getY() && sourceLOSpoint.getX() < targetLOSpoint.getX()) {
            // start point is below end point and start is left of end, push label up
            return -10;
        } else if (sourceLOSpoint.getY() < targetLOSpoint.getY() && sourceLOSpoint.getX() > targetLOSpoint.getX()) {
            // start point is above end point and start is right of end, push label below thread
            return 10;
        } else if (sourceLOSpoint.getY() < targetLOSpoint.getY() && sourceLOSpoint.getX() < targetLOSpoint.getX()) {
            // start point is above end point and start is left of end, push label up
            return -10;
        }
        return 0; // should never get here
    }

    private int sourceLOSLabelYoffset(Point sourceLOSpoint, Point targetLOSpoint) {
        if (sourceLOSpoint.getY() == targetLOSpoint.getY()) {
            // LOS is horizontal; push label above thread
            return -40;
        } else if (sourceLOSpoint.getX() == targetLOSpoint.getX() && sourceLOSpoint.getY() > targetLOSpoint.getY() ) {
            // LOS is vertical; start is below end; push label below thread
            return -20;
        } else if (sourceLOSpoint.getX() == targetLOSpoint.getX() && sourceLOSpoint.getY() < targetLOSpoint.getY() ) {
            // LOS is vertical; start is above end; push label above thread
            return -10;
        } else if (sourceLOSpoint.getY() > targetLOSpoint.getY() && sourceLOSpoint.getX() > targetLOSpoint.getX()) {
            // start point is below end point and start is right of end, push label above thread
            return -20;
        } else if (sourceLOSpoint.getY() > targetLOSpoint.getY() && sourceLOSpoint.getX() < targetLOSpoint.getX()) {
            // start point is below end point and start is left of end, push label up
            return -20;
        } else if (sourceLOSpoint.getY() < targetLOSpoint.getY() && sourceLOSpoint.getX() > targetLOSpoint.getX()) {
            // start point is above end point and start is right of end, push label below thread
            return -10;
        } else if (sourceLOSpoint.getY() < targetLOSpoint.getY() && sourceLOSpoint.getX() < targetLOSpoint.getX()) {
            // start point is above end point and start is left of end, push label up
            return -10;
        }
        return 0; // should never get here
    }

    @Override
    public String encode(Command var1) {
        if(var1 instanceof LOS_Thread.LOSCommand) {
            return super.encode(var1);
        } else if( var1 instanceof VASLLOSCommand) {
            VASLThread.VASLLOSCommand vaslloscom = (VASLThread.VASLLOSCommand) var1;
            SequenceEncoder comencode = new SequenceEncoder(vaslloscom.target.getId(), '\t');
            comencode.append(vaslloscom.newAnchor.x).append(vaslloscom.newAnchor.y).append(vaslloscom.newArrow.x).append(vaslloscom.newArrow.y).append(vaslloscom.newPersisting).append(vaslloscom.newMirroring).append(vaslloscom.sourceLevel).append(vaslloscom.targetLevel);
            return "LOS\t" + comencode.getValue();
        } else if(var1 instanceof VASLLOSButtonCommand){
            VASLThread.VASLLOSButtonCommand vaslbuttoncom = (VASLThread.VASLLOSButtonCommand) var1;
            return "VASLLOSButtonCommand\t" + Boolean.toString(vaslbuttoncom.enableButton);
        } else {
            return null;
        }
    }
    public void setButtonState(boolean buttonstatus){
        for (VASLThread t : this.map.getComponentsOf(VASLThread.class)) {
            t.setup(buttonstatus);
        }

    }

    public static class VASLLOSCommand extends Command {
        protected VASLThread target;
        protected String oldState;
        protected Point newAnchor;
        protected Point oldAnchor;
        protected Point newArrow;
        protected Point oldArrow;
        protected boolean newPersisting;
        protected boolean oldPersisting;
        protected boolean newMirroring;
        protected boolean oldMirroring;
        protected double sourceLevel;
        protected double targetLevel;

        public VASLLOSCommand(VASLThread passthread, Point passanchor, Point passarrow, boolean passpersisting, boolean passmirroring, double sourcelevel, double targetlevel) {
            this.target = passthread;
            this.oldAnchor = this.target.getAnchor();
            this.oldArrow = this.target.getArrow();
            this.oldPersisting = this.target.isPersisting();
            this.oldMirroring = this.target.isMirroring();
            this.newAnchor = passanchor;
            this.newArrow = passarrow;
            this.newPersisting = passpersisting;
            this.newMirroring = passmirroring;
            this.sourceLevel=sourcelevel;
            this.targetLevel=targetlevel;
        }

        protected void executeCommand() {
            this.target.setEndPointsandLevels(this.newAnchor, this.newArrow, this.sourceLevel, this.targetLevel);
            this.target.setPersisting(this.newPersisting);
            this.target.setMirroring(this.newMirroring);

        }

        protected Command myUndoCommand() {
            return new VASLThread.VASLLOSCommand(this.target, this.oldAnchor, this.oldArrow, this.oldPersisting, this.oldMirroring, this.target.sourcelevel, this.target.targetlevel);
        }
    }
    public static class VASLLOSButtonCommand extends Command {
        protected VASLThread target;
        protected boolean enableButton;

        public VASLLOSButtonCommand(VASLThread vaslthread, boolean buttonstatus) {
            this.target = vaslthread;
            this.enableButton = buttonstatus;
        }

        protected void executeCommand() {
            this.target.setButtonState(this.enableButton);
        }

        protected Command myUndoCommand() {
            return new VASLThread.VASLLOSButtonCommand(this.target, this.enableButton);
        }
    }
}
