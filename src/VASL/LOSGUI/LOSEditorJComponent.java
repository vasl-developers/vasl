/*
 * Copyright (c) 2000-2003 by David Sullivan
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
package VASL.LOSGUI;

import VASL.LOS.LOSDataEditor;
import VASL.LOS.Map.*;
import VASL.LOS.Unit.Unit;
import VASL.LOSGUI.Selection.*;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Title:        LOSEditorJComponent.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class LOSEditorJComponent
        extends JComponent
        implements MouseListener,
        MouseMotionListener,
        Scrollable,
        KeyListener {

    // synthetic field used to access text files in jar
    static Class thisClass;

    // status variables
    private boolean mapChanged = false;
    private boolean mapOpen = false;

    // the map editor
    public LOSDataEditor losDataEditor = null;
    public BufferedImage mapImage = null;
    private int minDirtyX = -1;
    private int minDirtyY = -1;
    private int maxDirtyX = -1;
    private int maxDirtyY = -1;
    private BufferedImage terrainImages[] = new BufferedImage[256];
    private Image singleHexWoodenBridgeImage;
    private Image singleHexStoneBridgeImage;

    // scenario/unit stuff
    private Scenario scenario = new Scenario();
    private Image vehImage;

    // function variables
    private String currentFunctionName = "LOS";
    private String currentTerrainName;
    private Terrain currentTerrain;
    private String currentToTerrainName;
    private Terrain currentToTerrain;
    private String currentBrush;
    private int currentBrushSize;
    private int currentGroundLevel;
    private int currentToGroundLevel;
    private boolean roundBrush = false;

    private boolean VASLImage = false;
    private int rotation;

    // pseudo mouse cursors
    private Shape cursorShape;

    // custom building variables
    private int customBuildingWidth = 32;
    private int customBuildingHeight = 32;
    private boolean customBridgeOn = false;

    private final static int EDGE_TERRAIN_WIDTH = 39;
    private final static int EDGE_TERRAIN_HEIGHT = 5;

    // custom bridge variables
    private Bridge currentBridge;
    private int customBridgeRoadElevation = 0;

    // road variables
    private int roadWidth = (int) Hex.WIDTH / 6 + 1;
    private int roadHeight = (int) Hex.HEIGHT / 2;

    // selection list
    private LinkedList<Selection> allSelections = new LinkedList<Selection>();

    // ZIP file archive stuff
    private ZipFile archive;
    private SharedBoardMetadata sharedBoardMetadata;

    private boolean doingLOS = false;
    private int targetX;
    private int targetY;
    private LOSResult result = new LOSResult();
    private Location sourceLocation;
    private Location targetLocation;

    private LOSEditorJFrame frame;

    private Dimension dim;

    // selection output file - records XML snippets of edits applied to the map
    private final static String XMLSnippetFileName = "LOSXMLSnippets.txt";

    public LOSEditorJComponent() {

        enableEvents(AWTEvent.WINDOW_EVENT_MASK);

        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setFrame(LOSEditorJFrame newFrame) {

        frame = newFrame;
    }

    //Component initialization
    private void jbInit() throws Exception {

        this.setMinimumSize(new Dimension(100, 100));
        this.setEnabled(true);
        adjustMapViewSize();
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);

        // set up the image archive
        try {

            archive = new ZipFile(LOSEditorProperties.getLOSEditorHome() + System.getProperty("file.separator", "\\") + "LOSEditorData.zip");

        } catch (IOException e) {

            LOSEditorApp.writeError("Cannot read the archive file LOSEditorData.zip");
        }

        // read the shared metadata file
        loadSharedBoardMetadata();
    }

    /**
     * Reads the terrain types metadata from the LOS archive
     */
    public void loadSharedBoardMetadata()throws IOException {

        // parse the board metadata
        sharedBoardMetadata = new SharedBoardMetadata();
        try {

            // read the shared metadata file in the LOS archive and set the terrain types
            sharedBoardMetadata.parseSharedBoardMetadataFile(archive.getInputStream(archive.getEntry(BoardArchive.getSharedBoardMetadataFileName())));

        } catch (Exception e) {

            throw new IOException("Unable to read the shared board metadata from the LOS archive", e);
        }
    }

    /**
     * @return the terrain types
     */
    public HashMap<String, Terrain> getTerrainTypes(){

        return sharedBoardMetadata.getTerrainTypes();
    }

    // load the terrain graphics
    public void loadTerrainGraphics() {

        String sbuf[] = new String[256];
        String fileName = "CASL/init/TerrainImages.txt";

        String s;
        int current = 0;
        Graphics g;
        Image tempImage;

        // open and read the file
        try {

            BufferedReader r = new BufferedReader(new InputStreamReader(getTextFile(fileName)));

            // read in the text line for each building
            while ((s = r.readLine()) != null && current < 256) {

                sbuf[current++] = s;
            }
            r.close();
        } catch (IOException e) {

            // handle error
            LOSEditorApp.writeError("Error reading the terrain images file " + fileName);
        }

        // get the images
        current = 0;
        while (sbuf[current] != null && current < 256) {

            String terrainName = "";
            String imageFileName = "";

            // get the terrain and image names
            try {
                terrainName = sbuf[current].substring(0, sbuf[current].indexOf('|'));
                imageFileName = sbuf[current].substring(sbuf[current].indexOf('|') + 1);
            } catch (Exception e) {

                LOSEditorApp.writeError("Line " + (current + 1) + ": Cannot read the line... " + sbuf[current]);
            }

            // find the terrain
            Terrain t = losDataEditor.getMap().getTerrain(terrainName);
            if (t == null) {

                LOSEditorApp.writeError("Line " + (current + 1) + ": Terrain not found - " + terrainName);
            }

            // load the graphic
            else {

                // get the image from the file
                try {

                    tempImage = getImage("CASL/images/terrain/" + imageFileName);

                    // no buffered image for bridges (allow for transparency)
                    if (t.getType() == losDataEditor.getMap().getTerrain("Single Hex Wooden Bridge").getType()) {

                        singleHexWoodenBridgeImage = tempImage;
                    } else if (t.getType() == losDataEditor.getMap().getTerrain("Single Hex Stone Bridge").getType()) {

                        singleHexStoneBridgeImage = tempImage;
                    } else {

                        // draw it into the buffered image
                        terrainImages[t.getType()] = new BufferedImage(tempImage.getWidth(this), tempImage.getHeight(this), BufferedImage.TYPE_3BYTE_BGR);
                        g = terrainImages[t.getType()].getGraphics();
                        g.drawImage(tempImage, 0, 0, this);

                        // free up resources
                        g.dispose();
                    }

                } catch (Exception e) {
                    terrainImages[t.getType()] = null;
                    LOSEditorApp.writeError("Line " + (current + 1) + ": Cannot find terrain image file " + imageFileName);
                }
            }

            current++;
        }
    }

    public String getArchiveName() {
        return losDataEditor.getArchiveName();
    }

    public boolean isMapOpen() {
        return mapOpen;
    }

    public boolean isMapChanged() {
        return mapChanged;
    }

    public void adjustMapViewSize() {

        // adjust window to map size
        if (losDataEditor == null) {

            this.setPreferredSize(new Dimension(0, 0));
            this.revalidate();
        } else {
            dim = new Dimension(losDataEditor.getMap().getGridWidth(), losDataEditor.getMap().getGridHeight());
            this.setPreferredSize(dim);
            this.revalidate();
        }
    }

    public void paint(Graphics g) {

        // is the map open?
        if (!mapOpen) return;

        // paint the map
        Graphics2D screen2D = (Graphics2D) g;
        if (VASLImage) {
            screen2D.drawImage(losDataEditor.getBoardImage(), 0, 0, this);
        }
        else {
            screen2D.drawImage(mapImage, 0, 0, this);
        }

        // paint the scenario units
        screen2D.setColor(Color.white);
        for (Object o : scenario.getVehicles()) {

            Unit u = (Unit) o;
            screen2D.drawImage(
                    vehImage,
                    (int) u.getLocation().getLOSPoint().getX() - 24,
                    (int) u.getLocation().getLOSPoint().getY() - 24,
                    this);
        }

        if (currentFunctionName.equals("LOS")) {

            int spacing = 20;

            // set level color
            switch (sourceLocation.getBaseHeight() + sourceLocation.getHex().getBaseHeight()) {

                case -1:
                case -2:
                    screen2D.setColor(Color.red);
                    break;
                case 0:
                    screen2D.setColor(Color.gray);
                    break;
                case 1:
                    screen2D.setColor(Color.darkGray);
                    break;
                case 2:
                    screen2D.setColor(Color.black);
                    break;
                default:
                    screen2D.setColor(Color.white);
            }

            // draw the source location level
            screen2D.drawString(
                    "Level " + (sourceLocation.getBaseHeight() + sourceLocation.getHex().getBaseHeight()),
                    (int) sourceLocation.getLOSPoint().getX() - spacing / 2,
                    (int) sourceLocation.getLOSPoint().getY() + spacing / 2 + 15
            );


            if (doingLOS) {

                if (result.isBlocked()) {
                    if (result.hasHindrance()) {

                        screen2D.setColor(Color.white);
                        screen2D.drawLine(
                                (int) sourceLocation.getLOSPoint().getX(),
                                (int) sourceLocation.getLOSPoint().getY(),
                                (int) result.firstHindranceAt().getX(),
                                (int) result.firstHindranceAt().getY());

                        screen2D.setColor(Color.red);
                        screen2D.drawLine(
                                (int) result.firstHindranceAt().getX(),
                                (int) result.firstHindranceAt().getY(),
                                (int) result.getBlockedAtPoint().getX(),
                                (int) result.getBlockedAtPoint().getY());

                        screen2D.setColor(Color.black);
                        screen2D.drawLine(
                                (int) result.getBlockedAtPoint().getX(),
                                (int) result.getBlockedAtPoint().getY(),
                                targetX,
                                targetY);
                    } else {
                        screen2D.setColor(Color.white);
                        screen2D.drawLine(
                                (int) sourceLocation.getLOSPoint().getX(),
                                (int) sourceLocation.getLOSPoint().getY(),
                                (int) result.getBlockedAtPoint().getX(),
                                (int) result.getBlockedAtPoint().getY());

                        screen2D.setColor(Color.black);
                        screen2D.drawLine(
                                (int) result.getBlockedAtPoint().getX(),
                                (int) result.getBlockedAtPoint().getY(),
                                targetX,
                                targetY);
                    }
                } else if (result.hasHindrance()) {

                    screen2D.setColor(Color.white);
                    screen2D.drawLine(
                            (int) sourceLocation.getLOSPoint().getX(),
                            (int) sourceLocation.getLOSPoint().getY(),
                            (int) result.firstHindranceAt().getX(),
                            (int) result.firstHindranceAt().getY());

                    screen2D.setColor(Color.red);
                    screen2D.drawLine(
                            (int) result.firstHindranceAt().getX(),
                            (int) result.firstHindranceAt().getY(),
                            targetX,
                            targetY);
                } else {

                    screen2D.setColor(Color.white);
                    screen2D.drawLine(
                            (int) sourceLocation.getLOSPoint().getX(),
                            (int) sourceLocation.getLOSPoint().getY(),
                            targetX,
                            targetY);
                }

                // draw the target location level
                screen2D.setColor(Color.red);
                screen2D.drawString(
                        "Level " + (targetLocation.getBaseHeight() + targetLocation.getHex().getBaseHeight()),
                        (int) targetLocation.getLOSPoint().getX() - spacing / 2,
                        (int) targetLocation.getLOSPoint().getY() + spacing / 2 + 15
                );
            }
        } else {
            for (Object allSelection : allSelections) {

                ((Selection) allSelection).paint(screen2D);
            }
        }

        // show mouse pseudo cursor
        if (cursorShape != null) {

            // red square
            screen2D.setColor(Color.white);
            screen2D.draw(cursorShape);
        }

        // free resources
        screen2D.dispose();
    }

    public void update(Graphics screen) {
        paint(screen);
    }

    /**
     * ***************************
     * Mouse methods
     * ****************************
     */
    public void mouseReleased(MouseEvent e) {

        // is the map open?
        if (!mapOpen) return;

        Map map = losDataEditor.getMap();

        if (map.onMap(e.getX(), e.getY())) {
            if (currentFunctionName.equals("LOS")) {

                // once an LOS is made always show the LOS

            } else if (currentFunctionName.equals("Set ground level") ||
                    currentFunctionName.equals("Add terrain")) {

                // custom building?
                if (customBridgeOn) {

                    // create, rotate and add the rectangle
                    AffineTransform at = AffineTransform.getRotateInstance(
                            Math.toRadians(rotation),
                            e.getX(),
                            e.getY()
                    );
                    allSelections.add(new RectangularSelection(
                            at.createTransformedShape(new Rectangle(
                                    e.getX() - customBuildingWidth / 2,
                                    e.getY() - customBuildingHeight / 2,
                                    customBuildingWidth,
                                    customBuildingHeight)),
                            false
                    ));
                }

                // full hex selected?
                else if (currentBrush.equals("Hex")) {

                    Hex h = losDataEditor.getMap().gridToHex(e.getX(), e.getY());

                    // mark the hex
                    allSelections.add(new HexSelection(h.getExtendedHexBorder(), h));

                } else {
                    int currentX = e.getX() - currentBrushSize / 2;
                    int currentY = e.getY() - currentBrushSize / 2;

                    // need to rotate?
                    if (rotation != 0 && !roundBrush) {

                        // create, rotate and add the rectangle
                        AffineTransform at = AffineTransform.getRotateInstance(
                                Math.toRadians(rotation),
                                e.getX(),
                                e.getY()
                        );
                        allSelections.add(new RotatedRectangularSelection(
                                at.createTransformedShape(new Rectangle(
                                        e.getX() - currentBrushSize / 2,
                                        e.getY() - currentBrushSize / 2,
                                        currentBrushSize,
                                        currentBrushSize)),
                                e.getX() - currentBrushSize / 2,
                                e.getY() - currentBrushSize / 2,
                                currentBrushSize,
                                currentBrushSize,
                                rotation
                        ));
                    } else {

                        // create the rectangle and add
                        Rectangle rect = new Rectangle(currentX, currentY, currentBrushSize, currentBrushSize);
                        allSelections.add(new RectangularSelection(rect, roundBrush));
                    }
                }
            } else if (currentFunctionName.equals("Add hexside terrain")) {

                Location sourceLocation = losDataEditor.getMap().gridToHex(e.getX(), e.getY()).nearestLocation(e.getX(), e.getY());
                Hex hex = sourceLocation.getHex();

                //ignore the center location
                if (hex.isHexsideLocation(sourceLocation)) {

                    // create a hexside rectangles
                    Rectangle paintRect = new Rectangle(
                            (int) sourceLocation.getEdgeCenterPoint().getX() - EDGE_TERRAIN_WIDTH / 2,
                            (int) sourceLocation.getEdgeCenterPoint().getY() - EDGE_TERRAIN_HEIGHT / 2,
                            EDGE_TERRAIN_WIDTH,
                            EDGE_TERRAIN_HEIGHT
                    );
                    Rectangle gridRect = new Rectangle(
                            (int) sourceLocation.getEdgeCenterPoint().getX() - EDGE_TERRAIN_WIDTH / 2 - 1,
                            (int) sourceLocation.getEdgeCenterPoint().getY() - EDGE_TERRAIN_HEIGHT / 2 - 1,
                            EDGE_TERRAIN_WIDTH + 1,
                            EDGE_TERRAIN_HEIGHT + 1
                    );

                    // need to rotate?
                    int degrees = 0;
                    int side = hex.getLocationHexside(sourceLocation);
                    switch (side) {
                        case 1:
                        case 4:
                            degrees = 60;
                            break;
                        case 2:
                        case 5:
                            degrees = -60;
                            break;
                    }

                    // rotate the rectangle
                    AffineTransform at = AffineTransform.getRotateInstance(
                            Math.toRadians(degrees),
                            sourceLocation.getEdgeCenterPoint().getX(),
                            sourceLocation.getEdgeCenterPoint().getY()
                    );
                    allSelections.add(new HexsideSelection(
                            at.createTransformedShape(paintRect),
                            at.createTransformedShape(gridRect),
                            sourceLocation
                    ));
                }
            } else if (currentFunctionName.equals("Add bridge")) {

                Hex h = map.gridToHex(e.getX(), e.getY());
                // remove?
                if (currentTerrain == null) {

                    allSelections.add(new HexSelection(
                            new Rectangle((int) h.getCenterLocation().getLOSPoint().getX() - 8, (int) h.getCenterLocation().getLOSPoint().getY() - 8, 16, 16),
                            h
                    ));
                } else {
                    allSelections.add(new BridgeSelection(new Bridge(
                            currentBridge.getTerrain(),
                            this.customBridgeRoadElevation,
                            currentBridge.getRotation(),
                            currentBridge.getLocation(),
                            currentBridge.isSingleHex(),
                            currentBridge.getCenter()
                    )));
                }
            } else if (currentFunctionName.equals("Add road")) {

                Location sourceLocation = map.gridToHex(e.getX(), e.getY()).nearestLocation(e.getX(), e.getY());
                Hex hex = sourceLocation.getHex();

                // only place elevated roads on level 0
                if (!hex.getCenterLocation().getTerrain().getName().equals("Elevated Road") &&
                        currentTerrain.getName().equals("Elevated Road") &&
                        hex.getBaseHeight() != 0)
                    return;

                //ignore the center location
                if (hex.isHexsideLocation(sourceLocation)) {

                    // create the road rectangle
                    int roadOffset = 4;
                    Rectangle roadRect = new Rectangle(
                            (int) hex.getCenterLocation().getLOSPoint().getX() - roadWidth / 2,
                            (int) hex.getCenterLocation().getLOSPoint().getY() - roadHeight - 1,
                            roadWidth,
                            roadHeight + roadOffset
                    );

                    Rectangle elevationRect = new Rectangle(
                            (int) hex.getCenterLocation().getLOSPoint().getX() - roadWidth / 2 - 4,
                            (int) hex.getCenterLocation().getLOSPoint().getY() - roadHeight - 1,
                            roadWidth + 8,
                            roadHeight + roadOffset
                    );

                    // need to rotate?
                    int degrees = 0;
                    int side = hex.getLocationHexside(sourceLocation);
                    switch (side) {
                        case 1:
                            degrees = 60;
                            break;
                        case 2:
                            degrees = 120;
                            break;
                        case 3:
                            degrees = 180;
                            break;
                        case 4:
                            degrees = -120;
                            break;
                        case 5:
                            degrees = -60;
                            break;
                    }

                    // rotate the rectangle
                    AffineTransform at = AffineTransform.getRotateInstance(
                            Math.toRadians(degrees),
                            hex.getCenterLocation().getLOSPoint().getX(),
                            hex.getCenterLocation().getLOSPoint().getY()
                    );
                    allSelections.add(new HexsideSelection(
                            at.createTransformedShape(roadRect),
                            at.createTransformedShape(elevationRect),
                            sourceLocation
                    ));
                }
            } else if (currentFunctionName.equals("Add objects")) {

                // mark the hex
                Hex h = map.gridToHex(e.getX(), e.getY());
                allSelections.add(new HexSelection(
                        new Rectangle((int) h.getCenterLocation().getLOSPoint().getX() - 8, (int) h.getCenterLocation().getLOSPoint().getY() - 8, 16, 16),
                        h
                ));
            }

            repaint();
        }
    }

    public void mousePressed(MouseEvent e) {

        if (currentFunctionName.equals("LOS")) {

            sourceLocation = losDataEditor.getMap().gridToHex(e.getX(), e.getY()).nearestLocation(e.getX(), e.getY());

            // if Ctrl click, use upper-most location
            if (e.isControlDown()) {
                while (sourceLocation.getUpLocation() != null) {
                    sourceLocation = sourceLocation.getUpLocation();
                }
            }
            doingLOS = true;
            mouseDragged(e);

        }
        requestFocus();
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseMoved(MouseEvent e) {

        if(losDataEditor == null) return;

        int mouseX = e.getX();
        int mouseY = e.getY();

        Map map = losDataEditor.getMap();

        // is the map open?
        if (!mapOpen) return;
        if (!map.onMap(mouseX, mouseY)) return;

        Location newLocation = map.gridToHex(mouseX, mouseY).nearestLocation(mouseX, mouseY);

        // set the pseudo mouse cursor
        if (currentFunctionName.equals("Set ground level") ||
                currentFunctionName.equals("Add terrain")) {

            if (customBridgeOn) {

                // create and rotate the rectangle
                AffineTransform at = AffineTransform.getRotateInstance(
                        Math.toRadians(rotation),
                        e.getX(),
                        e.getY()
                );
                cursorShape = at.createTransformedShape(new Rectangle(
                        e.getX() - customBuildingWidth / 2,
                        e.getY() - customBuildingHeight / 2,
                        customBuildingWidth,
                        customBuildingHeight));

            } else if (roundBrush) {

                // set the cursor rectangle
                cursorShape = new Ellipse2D.Float(
                        (float) mouseX - currentBrushSize / 2,
                        (float) mouseY - currentBrushSize / 2,
                        (float) currentBrushSize,
                        (float) currentBrushSize
                );

            } else {
                // create and rotate the rectangle
                AffineTransform at = AffineTransform.getRotateInstance(
                        Math.toRadians(rotation),
                        e.getX(),
                        e.getY()
                );

                // set the cursor rectangle
                cursorShape = at.createTransformedShape(new Rectangle(
                        (mouseX - currentBrushSize / 2),
                        (mouseY - currentBrushSize / 2),
                        currentBrushSize,
                        currentBrushSize
                ));
            }
            repaint();
        }
        else if (currentFunctionName.equals("Add bridge")) {

            if (currentBridge != null) {

                currentBridge.setCenter(e.getPoint());
                currentBridge.setRotation(rotation);
                currentBridge.setLocation(map.gridToHex(e.getX(), e.getY()).getCenterLocation());

                cursorShape = currentBridge.getShape();
                repaint();
            }
        }
        else {
            cursorShape = null;
        }

        targetLocation = newLocation;
        String cursorString =
                " X: " + mouseX +
                        " Y: " + mouseY +
                        " Z: " + (map.getGridElevation(mouseX, mouseY) +
                        map.getGridTerrain(mouseX, mouseY).getHeight()) +
                        " (" + map.getGridTerrain(mouseX, mouseY).getName() +
                        ")";

        String locationString = " | Location: " + " " + targetLocation.getName();

        String heightString =
                " - Height: " + (targetLocation.getHex().getBaseHeight() + targetLocation.getBaseHeight());

        String terrainString = " - Terrain:  " + targetLocation.getTerrain().getName();

        // depression terrain?
        if (targetLocation.getDepressionTerrain() != null) {
            terrainString += "/" + targetLocation.getDepressionTerrain().getName();
        }

        // edge/cliff terrain?
        if (!targetLocation.getHex().isCenterLocation(targetLocation)) {
            if (targetLocation.getHex().getEdgeTerrain(targetLocation.getHex().getLocationHexside(targetLocation)) != null) {
                terrainString += "/" + targetLocation.getHex().getEdgeTerrain(targetLocation.getHex().getLocationHexside(targetLocation)).getName();
            }
            if (targetLocation.getHex().hasCliff(targetLocation.getHex().getLocationHexside(targetLocation))) {
                terrainString += "/Cliff";
            }
        }

        //Bridge?
        if (targetLocation.getHex().hasBridge()) {
            terrainString += "/" + targetLocation.getHex().getBridge().getTerrain().getName();
        }

        frame.setStatusBarText(cursorString + locationString + heightString + terrainString);
    }

    public void mouseDragged(MouseEvent e) {

        int mouseX = e.getX();
        int mouseY = e.getY();

        // is the map open?
        if (!mapOpen) return;
        Map map = losDataEditor.getMap();

        if (!map.onMap(mouseX, mouseY)) return;

        Location newLocation = map.gridToHex(mouseX, mouseY).nearestLocation(mouseX, mouseY);

        if (currentFunctionName.equals("LOS")) {

            if (doingLOS) {

                boolean useAuxTargetLOSPoint = false;
                Point LOSPoint = newLocation.getLOSPoint();

                // ensure the LOS point is on the map
                if (!map.onMap((int) newLocation.getLOSPoint().getX(), (int) newLocation.getLOSPoint().getY())) {

                    LOSPoint = newLocation.getAuxLOSPoint();
                    useAuxTargetLOSPoint = true;
                }
                // use the closest LOS point
                else if (Point.distance(mouseX, mouseY, (int) newLocation.getLOSPoint().getX(), (int) newLocation.getLOSPoint().getY()) >
                        Point.distance(mouseX, mouseY, (int) newLocation.getAuxLOSPoint().getX(), (int) newLocation.getAuxLOSPoint().getY())) {

                    LOSPoint = newLocation.getAuxLOSPoint();
                    useAuxTargetLOSPoint = true;

                }

                // are we really in a new location?
                if (targetLocation == newLocation && targetX == (int) LOSPoint.getX() && targetY == (int) LOSPoint.getY()) {
                    return;
                }

                targetLocation = newLocation;

                // if Ctrl click, use upper location
                if (e.isControlDown()) {
                    while (targetLocation.getUpLocation() != null) {
                        targetLocation = targetLocation.getUpLocation();
                    }
                }

                targetX = (int) LOSPoint.getX();
                targetY = (int) LOSPoint.getY();

                map.LOS(sourceLocation, false, targetLocation, useAuxTargetLOSPoint, result, scenario);

                if (result.isBlocked()) {
                    frame.setStatusBarText(
                            "Blocked at " + (int) result.getBlockedAtPoint().getX() + ", "
                                    + (int) result.getBlockedAtPoint().getY() +
                                    " Reason: " + result.getReason()
                    );
                } else {
                    frame.setStatusBarText(
                            " Hindrances: " + result.getHindrance() +
                                    " Continuous slope: " + result.isContinuousSlope() +
                                    " Range: " + result.getRange());
                }

                repaint();
            }
        }
    }

    public Dimension getPreferredScrollableViewportSize() {
        return dim;
    }

    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (losDataEditor == null) {
            return 0;
        } else {
            return (int) Hex.WIDTH;
        }
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (losDataEditor == null) {
            return 0;
        } else {
            return 200;
        }
    }

    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    public void setCurrentFunction(String newCurrentFunction) {

        clearSelections();
        frame.setStatusBarText("  ");

        Map map = losDataEditor.getMap();

        if (newCurrentFunction.equals("LOS")) {
            if (!currentFunctionName.equals("LOS")) {

                requestFocus();
                doingLOS = false;
                currentFunctionName = newCurrentFunction;
            }
        } else if (newCurrentFunction.equals("Set ground level")) {
            if (!currentFunctionName.equals("Set ground level")) {

                currentFunctionName = newCurrentFunction;
                currentGroundLevel = 0;
                currentToGroundLevel = 0;
            }
        } else if (newCurrentFunction.equals("Add hexside terrain")) {
            if (!currentFunctionName.equals("Add hexside terrain")) {
                currentFunctionName = newCurrentFunction;

                // start with wall terrain
                currentTerrain = map.getTerrain("Wall");
                currentTerrainName = "Wall";
                currentToTerrain = map.getTerrain("Wall");
                currentToTerrainName = "Wall";
            }
        } else if (newCurrentFunction.equals("Add terrain")) {
            if (!currentFunctionName.equals("Add terrain")) {
                currentFunctionName = newCurrentFunction;

                // start with open ground
                currentTerrain = map.getTerrain("Open Ground");
                currentTerrainName = "Open ground";
                currentToTerrain = map.getTerrain("Open Ground");
                currentToTerrainName = "Open ground";
                customBridgeOn = false;
            }
        } else if (newCurrentFunction.equals("Add bridge")) {
            if (!currentFunctionName.equals("Add bridge")) {
                currentFunctionName = newCurrentFunction;
                currentTerrain = map.getTerrain("Single Hex Wooden Bridge");
                currentTerrainName = "Wooden Building, One level";
            }
        } else if (newCurrentFunction.equals("Add road")) {
            if (!currentFunctionName.equals("Add road")) {
                currentFunctionName = newCurrentFunction;
                currentTerrain = map.getTerrain("Dirt Road");
                currentTerrainName = "Dirt Road";
                currentToTerrain = map.getTerrain("Dirt Road");
                currentToTerrainName = "Dirt Road";
            }
        } else if (newCurrentFunction.equals("Add objects")) {
            if (!currentFunctionName.equals("Add objects")) {
                currentFunctionName = newCurrentFunction;
                currentTerrain = map.getTerrain("Fox Holes");
                currentTerrainName = "Fox Holes";
            }
        }

        repaint();
    }

    public void setCurrentTerrain(String newCurrentTerrain) {

        currentTerrainName = newCurrentTerrain;

        Map map = losDataEditor.getMap();

        if (currentFunctionName.equals("Set ground level")) {

            // set the current ground level
            if (currentTerrainName.equals("Hill Level 0")) {
                currentTerrain = null;
                currentGroundLevel = 0;
            } else if (currentTerrainName.equals("Hill Level 1")) {
                currentTerrain = null;
                currentGroundLevel = 1;
            } else if (currentTerrainName.equals("Hill Level 2")) {
                currentTerrain = null;
                currentGroundLevel = 2;
            } else if (currentTerrainName.equals("Hill Level 3")) {
                currentTerrain = null;
                currentGroundLevel = 3;
            } else if (currentTerrainName.equals("Hill Level 4")) {
                currentTerrain = null;
                currentGroundLevel = 4;
            } else if (currentTerrainName.equals("Hill Level 5")) {
                currentTerrain = null;
                currentGroundLevel = 5;
            } else if (currentTerrainName.equals("Hill Level 6")) {
                currentTerrain = null;
                currentGroundLevel = 6;
            } else if (currentTerrainName.equals("Hill Level 7")) {
                currentTerrain = null;
                currentGroundLevel = 7;
            } else if (currentTerrainName.equals("Hill Level 8")) {
                currentTerrain = null;
                currentGroundLevel = 8;
            } else if (currentTerrainName.equals("Hill Level 9")) {
                currentTerrain = null;
                currentGroundLevel = 9;
            } else if (currentTerrainName.equals("Hill Level 10")) {
                currentTerrain = null;
                currentGroundLevel = 10;
            } else if (currentTerrainName.equals("Valley -1")) {
                currentTerrain = null;
                currentGroundLevel = -1;
            } else if (currentTerrainName.equals("Valley -2")) {
                currentTerrain = null;
                currentGroundLevel = -2;
            } else if (currentTerrainName.equals("Gully")) {
                currentTerrain = map.getTerrain("Gully");
                currentGroundLevel = -1;
            } else if (currentTerrainName.equals("Dry Stream")) {
                currentTerrain = map.getTerrain("Dry Stream");
                currentGroundLevel = -1;
            } else if (currentTerrainName.equals("Shallow Stream")) {
                currentTerrain = map.getTerrain("Shallow Stream");
                currentGroundLevel = -1;
            } else if (currentTerrainName.equals("Deep Stream")) {
                currentTerrain = map.getTerrain("Deep Stream");
                currentGroundLevel = -1;
            } else if (currentTerrainName.equals("Wadi")) {
                currentTerrain = map.getTerrain("Wadi");
                currentGroundLevel = -1;
            }
        } else if (currentFunctionName.equals("Add hexside terrain")) {

            Terrain t = map.getTerrain(currentTerrainName);

            if (currentTerrainName.equals("Remove")) {
                currentTerrain = null;
            } else if (t == null) {

                frame.setStatusBarText("Terrain " + currentTerrainName + " not found. Terrain set to 'Wall'.");
                currentTerrain = map.getTerrain("Wall");
                currentTerrainName = "Wall";
            } else {

                currentTerrain = t;
            }
        } else if (currentFunctionName.equals("Add terrain")) {

            Terrain t = map.getTerrain(currentTerrainName);

            if (t == null) {

                frame.setStatusBarText("Terrain " + currentTerrainName + " not found. Terrain set to 'Open Ground'.");
                currentTerrain = map.getTerrain("Open Ground");
                currentTerrainName = "Open Ground";
            } else {

                currentTerrain = t;
            }

        } else if (currentFunctionName.equals("Add bridge")) {


            if (currentTerrainName.equals("Remove")) {
                currentTerrain = null;
                currentBridge = null;
            } else if (currentTerrainName.equals("Single Hex Wooden Bridge")) {
                currentTerrain = map.getTerrain("Single Hex Wooden Bridge");
                currentBridge = new Bridge(currentTerrain, customBridgeRoadElevation, rotation, null, true);
            } else if (currentTerrainName.equals("Single Hex Stone Bridge")) {
                currentTerrain = map.getTerrain("Single Hex Stone Bridge");
                currentBridge = new Bridge(currentTerrain, customBridgeRoadElevation, rotation, null, true);
            } else if (currentTerrainName.equals("Wooden Bridge")) {
                currentTerrain = map.getTerrain("Wooden Bridge");
                currentBridge = new Bridge(currentTerrain, customBridgeRoadElevation, rotation, null, true);
            } else if (currentTerrainName.equals("Stone Bridge")) {
                currentTerrain = map.getTerrain("Stone Bridge");
                currentBridge = new Bridge(currentTerrain, customBridgeRoadElevation, rotation, null, true);
            }
        } else if (currentFunctionName.equals("Add road")) {

            if (currentTerrainName.equals("Dirt Road")) {
                currentTerrain = map.getTerrain("Dirt Road");
            } else if (currentTerrainName.equals("Paved Road")) {
                currentTerrain = map.getTerrain("Paved Road");
            } else if (currentTerrainName.equals("Elevated Road")) {
                currentTerrain = map.getTerrain("Elevated Road");
            } else if (currentTerrainName.equals("Sunken Road")) {
                currentTerrain = map.getTerrain("Sunken Road");
            } else if (currentTerrainName.equals("Runway")) {
                currentTerrain = map.getTerrain("Runway");
            }
        } else if (currentFunctionName.equals("Add objects")) {

            if (currentTerrainName.equals("Foxholes")) {
                currentTerrain = map.getTerrain("Fox Holes");
            } else if (currentTerrainName.equals("Trench")) {
                currentTerrain = map.getTerrain("Trench");
            } else if (currentTerrainName.equals("Tunnel")) {
                currentTerrain = map.getTerrain("Tunnel");
            } else if (currentTerrainName.equals("Sewer")) {
                currentTerrain = map.getTerrain("Sewer");
            } else if (currentTerrainName.equals("Stairway")) {
                currentTerrain = null;
            } else if (currentTerrainName.equals("Smoke")) {
                currentTerrain = null;
            } else if (currentTerrainName.equals("Vehicle")) {
                currentTerrain = null;
            } else if (currentTerrainName.equals("Remove Stairway")) {
                currentTerrain = null;
            } else if (currentTerrainName.equals("Remove Tunnel/Sewer")) {
                currentTerrain = null;
            } else if (currentTerrainName.equals("Remove Entrenchment")) {
                currentTerrain = null;
            } else if (currentTerrainName.equals("Remove Smoke")) {
                currentTerrain = null;
            } else if (currentTerrainName.equals("Remove Vehicle")) {
                currentTerrain = null;
            }
        }

        frame.setStatusBarText("  ");
        repaint();
    }

    public String getCurrentTerrain() {

        return currentTerrainName;
    }

    public void setCurrentToTerrain(String newCurrentToTerrain) {

        currentToTerrainName = newCurrentToTerrain;

        Map map = losDataEditor.getMap();

        if (currentFunctionName.equals("Set ground level")) {

            // set the current ground level
            if (currentToTerrainName.equals("Hill Level 0")) {
                currentToTerrain = null;
                currentToGroundLevel = 0;
            } else if (currentToTerrainName.equals("Hill Level 1")) {
                currentToTerrain = null;
                currentToGroundLevel = 1;
            } else if (currentToTerrainName.equals("Hill Level 2")) {
                currentToTerrain = null;
                currentToGroundLevel = 2;
            } else if (currentToTerrainName.equals("Hill Level 3")) {
                currentToTerrain = null;
                currentToGroundLevel = 3;
            } else if (currentToTerrainName.equals("Hill Level 4")) {
                currentToTerrain = null;
                currentToGroundLevel = 4;
            } else if (currentToTerrainName.equals("Hill Level 5")) {
                currentToTerrain = null;
                currentToGroundLevel = 5;
            } else if (currentToTerrainName.equals("Hill Level 6")) {
                currentToTerrain = null;
                currentToGroundLevel = 6;
            } else if (currentToTerrainName.equals("Hill Level 7")) {
                currentToTerrain = null;
                currentToGroundLevel = 7;
            } else if (currentToTerrainName.equals("Hill Level 8")) {
                currentToTerrain = null;
                currentToGroundLevel = 8;
            } else if (currentToTerrainName.equals("Hill Level 9")) {
                currentToTerrain = null;
                currentToGroundLevel = 9;
            } else if (currentToTerrainName.equals("Hill Level 10")) {
                currentToTerrain = null;
                currentToGroundLevel = 10;
            } else if (currentToTerrainName.equals("Valley -1")) {
                currentToTerrain = null;
                currentToGroundLevel = -1;
            } else if (currentToTerrainName.equals("Valley -2")) {
                currentToTerrain = null;
                currentToGroundLevel = -2;
            } else if (currentToTerrainName.equals("Gully")) {
                currentToTerrain = map.getTerrain("Gully");
                currentToGroundLevel = -1;
            } else if (currentToTerrainName.equals("Dry Stream")) {
                currentToTerrain = map.getTerrain("Dry Stream");
                currentToGroundLevel = -1;
            } else if (currentToTerrainName.equals("Shallow Stream")) {
                currentToTerrain = map.getTerrain("Shallow Stream");
                currentToGroundLevel = -1;
            } else if (currentToTerrainName.equals("Deep Stream")) {
                currentToTerrain = map.getTerrain("Deep Stream");
                currentToGroundLevel = -1;
            }
        } else if (currentFunctionName.equals("Add hexside terrain")) {

            Terrain t = map.getTerrain(currentToTerrainName);

            if (t == null) {

                frame.setStatusBarText("Terrain " + currentToTerrainName + " not found. Terrain set to 'Wall'.");
                currentToTerrain = map.getTerrain("Wall");
                currentToTerrainName = "Wall";
            } else {

                currentToTerrain = t;
            }
        } else if (currentFunctionName.equals("Add terrain")) {

            Terrain t = map.getTerrain(currentToTerrainName);

            if (t == null) {

                frame.setStatusBarText("Terrain " + currentToTerrainName + " not found. Terrain set to 'Open Ground'.");
                currentToTerrain = map.getTerrain("Open Ground");
                currentToTerrainName = "Open Ground";
            } else {

                currentToTerrain = t;
            }
        } else if (currentFunctionName.equals("Add road")) {

            if (currentToTerrainName.equals("Dirt Road")) {
                currentToTerrain = map.getTerrain("Dirt Road");
            } else if (currentToTerrainName.equals("Paved Road")) {
                currentToTerrain = map.getTerrain("Paved Road");
            } else if (currentToTerrainName.equals("Elevated Road")) {
                currentToTerrain = map.getTerrain("Elevated Road");
            } else if (currentToTerrainName.equals("Sunken Road")) {
                currentToTerrain = map.getTerrain("Sunken Road");
            }
        }

        frame.setStatusBarText("  ");
        repaint();
    }

    public void setCurrentBrush(String newCurrentBrush) {

        if (newCurrentBrush == null) {
            currentBrush = "";
        } else {
            currentBrush = newCurrentBrush;
        }

        if (currentBrush.equals("")) {
            currentBrushSize = 0;
        } else if (currentBrush.equals("1  Pixel")) {
            currentBrushSize = 1;
        } else if (currentBrush.equals("2  Pixel")) {
            currentBrushSize = 2;
        } else if (currentBrush.equals("4  Pixel")) {
            currentBrushSize = 4;
        } else if (currentBrush.equals("8  Pixel")) {
            currentBrushSize = 8;
        } else if (currentBrush.equals("16 Pixel")) {
            currentBrushSize = 16;
        } else if (currentBrush.equals("32 Pixel")) {
            currentBrushSize = 32;
        } else if (currentBrush.equals("64 Pixel")) {
            currentBrushSize = 64;
        } else if (currentBrush.equals("Hex")) {
            currentBrushSize = -1;
        }
        repaint();
    }

    public void writeTerrainEditsToFile(Terrain terrain) {


        try {

            // open the file for append
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(XMLSnippetFileName, true)));

            // write existing edits to temp file
            Iterator<Selection> iterator = allSelections.iterator();
            while (iterator.hasNext()){

                Selection s = iterator.next();

                out.println(s.getTerrainXMLSnippet(currentTerrain));
            }

            // close the file
            out.close();

        } catch (IOException e) {
            System.err.println("Cannot open the LOS XML snippet file" + XMLSnippetFileName);
            e.printStackTrace(System.err);
        }

    }

    public void writeElevationEditsToFile() {


        try {

            // open the file for append
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(XMLSnippetFileName, true)));

            // write existing edits to temp file
            Iterator<Selection> iterator = allSelections.iterator();
            while (iterator.hasNext()){

                Selection s = iterator.next();

                out.println(s.getElevationXMLSnippet(currentGroundLevel));
            }

            // close the file
            out.close();

        } catch (IOException e) {
            System.err.println("Cannot open the LOS XML snippet file" + XMLSnippetFileName);
            e.printStackTrace(System.err);
        }
    }

    public void updateMap() {

        // is the map open?
        if (!mapOpen) return;

        if (currentFunctionName.equals("Set ground level")) {

            writeElevationEditsToFile();

            // set the map grid first...
            Iterator iterator = allSelections.iterator();
            while (iterator.hasNext()) {

                Shape s = ((Selection) iterator.next()).getUpdateShape();

                losDataEditor.setGridGroundLevel(s, currentTerrain, currentGroundLevel);
                mapChanged = true;
                setDirtyArea(s.getBounds());
            }

            // ...then set the hex elevation/depression info
            iterator = allSelections.iterator();
            while (iterator.hasNext()) {

                losDataEditor.setHexGroundLevel(((Selection) iterator.next()).getUpdateShape(), currentTerrain, currentGroundLevel);
            }
        }
        else if (currentFunctionName.equals("Add hexside terrain")) {

            HexsideSelection selectedHexside;
            int hexside;
            Hex hex;

            for (Selection allSelection : allSelections) {

                selectedHexside = (HexsideSelection) allSelection;
                hex = selectedHexside.getLocation().getHex();

                // set the edge terrain in the location hex
                hexside = hex.getLocationHexside(selectedHexside.getLocation());
                hex.setEdgeTerrain(hexside, currentTerrain);

                // set the edge terrain in the adjacent hex
                hex = losDataEditor.getMap().getAdjacentHex(hex, hexside);
                if (hex != null) {
                    hex.setEdgeTerrain(hex.getOppositeHexside(hexside), currentTerrain);
                }

                // set the grid map
                // use open ground when removing terrain
                if (currentTerrain == null) {
                    losDataEditor.setGridTerrain(selectedHexside.getUpdateShape(), losDataEditor.getMap().getTerrain("Open Ground"));
                } else {
                    losDataEditor.setGridTerrain(selectedHexside.getUpdateShape(), currentTerrain);
                }
                setDirtyArea(selectedHexside.getUpdateShape().getBounds());
                mapChanged = true;
            }
        }
        else if (currentFunctionName.equals("Add terrain")) {

            Selection sel;

            writeTerrainEditsToFile(currentTerrain);

            for (Selection allSelection : allSelections) {

                sel = allSelection;
                Shape s = sel.getUpdateShape();

                losDataEditor.setGridTerrain(s, currentTerrain);
                losDataEditor.setHexTerrain(s, currentTerrain);
                mapChanged = true;
                setDirtyArea(s.getBounds());

            }
        } else if (currentFunctionName.equals("Add bridge")) {
            if (allSelections.size() > 0) {

                // selected bridge
                Iterator iterator = allSelections.iterator();
                while (iterator.hasNext()) {

                    // remove?
                    if (currentTerrain == null) {


                        Hex h = ((HexSelection) iterator.next()).getHex();
                        h.removeBridge();

                        // update the map
                        setDirtyArea(h.getHexBorder().getBounds());
                        mapChanged = true;
                    }

                    // add the bridge
                    else {

                        BridgeSelection sel = (BridgeSelection) iterator.next();

                        // Bridge location is currently the center of the hex
                        // need to create the new
                        sel.getHex().setBridge(sel.getBridge());

                        // update the map
                        setDirtyArea(sel.getUpdateShape().getBounds());
                        mapChanged = true;
                    }
                }
            }
        } else if (currentFunctionName.equals("Add road")) {
            Iterator iter;
            HexsideSelection selectedHexside;
            Terrain tempTerrain = currentTerrain;

            // set depression/groundlevel for sunken/elevated road
            if (currentTerrain.getName().equals("Elevated Road") || currentTerrain.getName().equals("Sunken Road")) {

                // convert sunken road to dirt roads for non-depression terrain
                if (currentTerrain.getName().equals("Sunken Road")) {
                    tempTerrain = losDataEditor.getMap().getTerrain("Dirt Road");
                }

                // set the map grid first...
                iter = allSelections.iterator();
                while (iter.hasNext()) {

                    selectedHexside = (HexsideSelection) iter.next();

                    if (currentTerrain.getName().equals("Elevated Road")) {

                        losDataEditor.setGridGroundLevel(
                                selectedHexside.getUpdateShape(),
                                null,
                                1);
                    } else {
                        losDataEditor.setGridGroundLevel(
                                selectedHexside.getUpdateShape(),
                                currentTerrain,
                                0);
                    }
                }

                // ...then set the hex elevation/depression info
                iter = allSelections.iterator();
                while (iter.hasNext()) {

                    selectedHexside = (HexsideSelection) iter.next();

                    if (currentTerrain.getName().equals("Elevated Road")) {

                        losDataEditor.setHexGroundLevel(
                                selectedHexside.getUpdateShape(),
                                null,
                                1);
                    } else {
                        losDataEditor.setHexGroundLevel(
                                selectedHexside.getUpdateShape(),
                                currentTerrain,
                                0);
                    }
                }
            }

            iter = allSelections.iterator();
            while (iter.hasNext()) {

                selectedHexside = (HexsideSelection) iter.next();

                losDataEditor.setGridTerrain(selectedHexside.getPaintShape(), tempTerrain);
                losDataEditor.setHexTerrain(selectedHexside.getPaintShape(), tempTerrain);

                setDirtyArea(selectedHexside.getUpdateShape().getBounds());
                mapChanged = true;
            }
        } else if (currentFunctionName.equals("Add objects")) {
            if (allSelections.size() > 0) {

                Hex h;
                for (Selection allSelection : allSelections) {

                    h = ((HexSelection) allSelection).getHex();

                    if (currentTerrain != null &&
                            (currentTerrain.getName().equals("Trench") || currentTerrain.getName().equals("Fox Holes"))) {

                        h.addEntrenchment(currentTerrain);
                    } else if (currentTerrain != null &&
                            (currentTerrain.getName().equals("Sewer") || currentTerrain.getName().equals("Tunnel"))) {

                        //TODO: remove tunnels
//                        h.addTunnel(currentTerrain);

                    } else if (currentTerrainName.equals("Stairway")) {

                        h.setStairway(true);

                    } else if (currentTerrainName.equals("Smoke")) {

                        losDataEditor.addSmoke(new Smoke(Smoke.SMOKE, h.getCenterLocation()));

                    } else if (currentTerrainName.equals("Vehicle")) {

                        //TODO: remove scenario dependency
//                        scenario.addUnit(new Vehicle(h.getCenterLocation()), Scenario.ALLIES);
                    } else if (currentTerrainName.equals("Remove Stairway")) {

                        h.setStairway(false);
                    } else if (currentTerrainName.equals("Remove Tunnel/Sewer")) {

//                        h.removeTunnel();
                    } else if (currentTerrainName.equals("Remove Entrenchment")) {

                        h.removeEntrenchment();
                    } else if (currentTerrainName.equals("Remove Smoke")) {

                        losDataEditor.removeSmoke(h.getCenterLocation());
                    } else if (currentTerrainName.equals("Remove Vehicle")) {

                        Hex vh;
                        Iterator iterator = scenario.getVehicles().iterator();
                        while (iterator.hasNext()) {

                            vh = (Hex) iterator.next();
                            if (vh == h) {

                                iterator.remove();
                            }
                        }
                    }

                    // adjust "dirty" area of map
                    setDirtyArea(h.getExtendedHexBorder().getBounds());
                    mapChanged = true;
                }
            }
        }

        // rebuild the map image
        paintMapImage(true);
        clearSelections();
        repaint();
    }

    public void setRoundBrush(boolean isRoundBrush) {

        roundBrush = isRoundBrush;
    }

    public void setVASLImage(boolean VASLImage) {
        this.VASLImage = VASLImage;
    }

    public void clearSelections() {

        if (allSelections.size() > 0) {
            allSelections.clear();
        }
        // reset the dirty area
        minDirtyX = -1;
        minDirtyY = -1;
        maxDirtyX = -1;
        maxDirtyY = -1;
    }

    public void createNewMap(int width, int height) {

        // create the map
        losDataEditor.createNewLOSData(width, height);

    }

    public void saveLOSData() {

        frame.setStatusBarText("Saving the map...");
        frame.paintImmediately();
        losDataEditor.saveLOSData();
        mapChanged = false;
        frame.setStatusBarText("");
        frame.paintImmediately();

    }

    public void openMap() {

        // create the map image
        frame.setStatusBarText("Creating the map image...");
        frame.paintImmediately();
        mapImage = new BufferedImage(losDataEditor.getMap().getGridWidth(), losDataEditor.getMap().getGridHeight(), BufferedImage.TYPE_3BYTE_BGR);
        paintMapImage(false);
        frame.setStatusBarText("  ");
        adjustMapViewSize();
        mapOpen = true;
        mapChanged = false;
        frame.setStatusBarText("  ");
        sourceLocation = losDataEditor.getMap().getHex(losDataEditor.getMap().getWidth() / 2, 1).getCenterLocation();
        targetLocation = sourceLocation;

    }

    /**
     * Open a VASL archive for editing LOS data
     * @param archiveName fully qualified name of the board archive
     */
    public void openArchive(String archiveName) {

        try {
            losDataEditor = new LOSDataEditor(
                    archiveName,
                    LOSEditorProperties.getBoardDirectory(),
                    sharedBoardMetadata);
        } catch (IOException e) {
            System.err.println("Cannot open the board archive: " + archiveName);
            e.printStackTrace();
        }

        // load the terrain images
        loadTerrainGraphics();

        // try to open LOS data
        frame.setStatusBarText("Reading or creating the LOS data...");
        losDataEditor.readLOSData();

        // create an empty geo board if no LOS data - for now
        if(losDataEditor.getMap() == null) {
            createNewMap(33, 10);
        }
        openMap();
    }

    public void closeMap() {

        // reset the map
        mapChanged = false;
        mapOpen = false;
        losDataEditor = null;
        mapImage = null;
        System.gc();        // recover space
        frame.setStatusBarText("  ");
        adjustMapViewSize();
        repaint();

    }

    public void undoSelections() {

        clearSelections();
        repaint();
        mapChanged = false;
    }

    public void setCustomBridgeOn(boolean newCustomBuildingOn) {

        customBridgeOn = newCustomBuildingOn;
    }

    public void setBridgeParameters(String terr, int roadElevation) {

        // set the current terrain
        setCurrentTerrain(terr);

        // set the custom bridge parameters
        customBridgeRoadElevation = roadElevation;
    }

    public int getBridgeRoadElevation() {
        return customBridgeRoadElevation;
    }

    public void setRotation(int rotation) {

        this.rotation = rotation;

    }

    /**
     * ***************************
     * Keyboard methods
     * ****************************
     */
    public void keyTyped(KeyEvent e) {

    }

    public void keyReleased(KeyEvent e) {

    }

    public void keyPressed(KeyEvent e) {

        int code = e.getKeyCode();
        String modifiers = KeyEvent.getKeyModifiersText(e.getModifiers());

        // is the map open?
        if (!mapOpen) return;

        // Not doing LOS?
        if (!currentFunctionName.equals("LOS")) {

            // undo
            if (code == KeyEvent.VK_Z && modifiers.equals("Ctrl")) {

                if (allSelections.size() > 0) {

                    allSelections.remove(allSelections.getLast());
                    repaint();
                }
            }
            // clear selections
            else if (code == KeyEvent.VK_ESCAPE) {

                clearSelections();
            }

            // update
            else if (code == KeyEvent.VK_U) {

                updateMap();
            }
        }
    }

    public void paintMapImage(boolean askToPaint) {

        Map map = losDataEditor.getMap();

        // map not dirty? ask if we should paint the whole thing
        if (minDirtyX == -1) {

            if (askToPaint) {

                int response = frame.AskYesNo("Do you want to recreate the entire map image?");

                if (response == JOptionPane.YES_OPTION) {

                    losDataEditor.paintMapArea(0, 0, map.getGridWidth(), map.getGridHeight(), mapImage, terrainImages, singleHexWoodenBridgeImage, singleHexStoneBridgeImage);
                    losDataEditor.paintMapShadows(0, 0, map.getGridWidth(), map.getGridHeight(), mapImage);
                    losDataEditor.paintMapContours(0, 0, map.getGridWidth(), map.getGridHeight(), mapImage);
                } else if (response == JOptionPane.NO_OPTION) {
                    return;
                } else if (response == JOptionPane.CANCEL_OPTION) {
                    return;
                }
            } else {
                losDataEditor.paintMapArea(0, 0, map.getGridWidth(), map.getGridHeight(), mapImage, terrainImages, singleHexWoodenBridgeImage, singleHexStoneBridgeImage);
                losDataEditor.paintMapShadows(0, 0, map.getGridWidth(), map.getGridHeight(), mapImage);
                losDataEditor.paintMapContours(0, 0, map.getGridWidth(), map.getGridHeight(), mapImage);
            }
        } else {
            losDataEditor.paintMapArea(
                    minDirtyX,
                    minDirtyY,
                    maxDirtyX - minDirtyX + 1,
                    maxDirtyY - minDirtyY + 1,
                    mapImage,
                    terrainImages,
                    singleHexWoodenBridgeImage,
                    singleHexStoneBridgeImage
            );
            losDataEditor.paintMapShadows(
                    minDirtyX,
                    minDirtyY,
                    maxDirtyX - minDirtyX + 1,
                    maxDirtyY - minDirtyY + 1,
                    mapImage
            );
            losDataEditor.paintMapContours(
                    minDirtyX,
                    minDirtyY,
                    maxDirtyX - minDirtyX + 1,
                    maxDirtyY - minDirtyY + 1,
                    mapImage
            );
        }
        losDataEditor.paintMapHexes(mapImage);
    }

    // adjust "dirty" area of map
    private void setDirtyArea(Rectangle rect) {

        //first time?
        if (minDirtyX == -1) {

            minDirtyX = (int) rect.getX();
            minDirtyY = (int) rect.getY();
            maxDirtyX = (int) (rect.getX() + rect.getWidth());
            maxDirtyY = (int) (rect.getY() + rect.getHeight());
        } else {

            minDirtyX = (int) Math.min(rect.getX(), minDirtyX);
            minDirtyY = (int) Math.min(rect.getY(), minDirtyY);
            maxDirtyX = (int) Math.max(rect.getX() + rect.getWidth(), maxDirtyX);
            maxDirtyY = (int) Math.max(rect.getY() + rect.getHeight(), maxDirtyY);
        }
    }

    public void changeAllTerrain() {

        boolean changed;

        frame.setStatusBarText("Changing the map...");
        frame.paintImmediately();

        // just the current selections?
        if (allSelections.size() > 0) {

            // update the map
            if (currentTerrain != null && currentToTerrain != null) {

                for (Selection s : allSelections) {

                    boolean selectionChanged = losDataEditor.changeAllTerrain(currentTerrain, currentToTerrain, s.getUpdateShape());

                    if (selectionChanged) {

                        this.setDirtyArea(s.getUpdateShape().getBounds());
                        mapChanged = true;
                    }

                }
            } else if (currentTerrain == null && currentToTerrain == null) {

                for (Selection s : allSelections) {

                    boolean selectionChanged = losDataEditor.changeAllGroundLevel(currentGroundLevel, currentToGroundLevel, s.getUpdateShape());

                    if (selectionChanged) {

                        this.setDirtyArea(s.getUpdateShape().getBounds());
                        mapChanged = true;
                    }

                }
            } else {
                frame.setStatusBarText("Illegal terrain mapping");
                return;
            }

            // clear the selections, set changed flag
            allSelections.clear();
        }

        // the whole map
        else {

            // update the map
            if (currentTerrain != null && currentToTerrain != null) {
                changed = losDataEditor.changeAllTerrain(currentTerrain, currentToTerrain);
            } else if (currentTerrain == null && currentToTerrain == null) {
                changed = losDataEditor.changeAllGroundLevel(currentGroundLevel, currentToGroundLevel);
            } else {
                frame.setStatusBarText("Illegal terrain mapping");
                return;
            }

            if (!changed) {

                frame.setStatusBarText("Nothing changed");
                return;

            }

            // mark the whole map as changed and recreate
            minDirtyX = -1;

        }

        frame.setStatusBarText("Recreating the map image...");
        frame.paintImmediately();
        paintMapImage(false);
        frame.setStatusBarText("");
        repaint();
    }

    public Image getImage(String imageName) {

        try {

            ZipEntry e = archive.getEntry(imageName);
            InputStream ip = archive.getInputStream(e);

            if (ip == null) {

                return null;
            } else {

                byte bytes[] = new byte[ip.available()];
                ip.read(bytes);
                Image temp = Toolkit.getDefaultToolkit().createImage(bytes);

                MediaTracker m = new MediaTracker(this);
                m.addImage(temp, 0);
                m.waitForID(0);

                return temp;
            }

        } catch (IOException e) {

            return null;

        } catch (InterruptedException e) {

            return null;

        } catch (Exception e) {

            return null;
        }
    }

    public InputStream getTextFile(String imageName) {

        try {

            ZipEntry e = archive.getEntry(imageName);
            return archive.getInputStream(e);


        } catch (Exception e) {

            return null;
        }
    }

    public void flipMap() {

        frame.setStatusBarText("Flipping the map...");
        frame.paintImmediately();
        losDataEditor.flip();
        frame.setStatusBarText("Rebuilding the map image...");
        frame.paintImmediately();
        paintMapImage(false);
        mapChanged = true;
        frame.setStatusBarText("");
    }

    public void runLosTest() {

        Map map = losDataEditor.getMap();

        int width = map.getWidth();
        int height = map.getHeight();
        int count = 0;
        int blocked = 0;

        LOSResult result = new LOSResult();

        // create a new location in the middle of the board
        Hex hex = map.getHex(map.getWidth() / 2, map.getHeight() / 2);
        Location l = hex.getCenterLocation();

        frame.setStatusBarText("Starting the LOS test...");
        frame.paintImmediately();

        // set the start time and save the base height
        int baseHeight = l.getBaseHeight();
        long startTime = System.currentTimeMillis();

        // check LOS at level zero
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height + (col % 2); row++) {

                result.setClear();
                map.LOS(l, false, map.getHex(col, row).getCenterLocation(), false, result, scenario);

                // increment counters
                count++;
                if (result.isBlocked()) {

                    blocked++;
                }
            }
        }

        // check LOS at level two
        l.setBaseHeight(2);
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height + (col % 2); row++) {

                result.setClear();
                map.LOS(l, false, map.getHex(col, row).getCenterLocation(), false, result, scenario);

                // increment counters
                count++;
                if (result.isBlocked()) {

                    blocked++;
                }
            }
        }

        // check LOS at level four
        l.setBaseHeight(4);
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height + (col % 2); row++) {

                result.setClear();
                map.LOS(l, false, map.getHex(col, row).getCenterLocation(), false, result, scenario);

                // increment counters
                count++;
                if (result.isBlocked()) {

                    blocked++;
                }
            }
        }

        frame.setStatusBarText(
                "LOS test complete. Total checks: " + count +
                        "  Blocked: " + (int) ((float) blocked / (float) count * 100) + "%" +
                        "  Time elapsed: " + (((double) System.currentTimeMillis() - (double) startTime) / 1000));
        frame.paintImmediately();

        // restore base height
        l.setBaseHeight(baseHeight);
    }

    public void insertMap(Map insertMap, String upperLeftHex) {

        if (losDataEditor.insertMap(insertMap, losDataEditor.getMap().getHex(upperLeftHex.toUpperCase()))) {

            frame.setStatusBarText("Rebuilding the map image...");
            frame.paintImmediately();

            paintMapImage(false);
            mapChanged = true;
            frame.setStatusBarText("");
        }
    }

}

