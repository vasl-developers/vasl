/*
 * $Id: ASLMap.java 8530 2012-12-26 04:37:04Z uckelman $
 *
 * Copyright (c) 2013 by Brent Easton
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

package VASL.build.module;

import VASL.LOS.Map.ASLPersistElevation;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Terrain;
import VASL.LOS.counters.CounterMetadataFile;
import VASL.build.module.map.ASLPieceMover;
import VASL.build.module.map.ASLStackMetrics;
import VASL.build.module.map.boardArchive.BoardMetadata;
import VASL.build.module.map.boardArchive.SharedBoardMetadata;
import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.build.module.map.boardPicker.BoardException;
import VASL.build.module.map.boardPicker.Overlay;
import VASL.build.module.map.boardPicker.VASLBoard;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.PieceWindow;
import VASSAL.build.module.map.PieceMover;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.widget.ListWidget;
import VASSAL.build.widget.PanelWidget;
import VASSAL.build.widget.PieceSlot;
import VASSAL.configure.*;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.DataArchive;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.imageop.Op;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.List;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Array;
import java.util.*;

import static VASSAL.build.GameModule.getGameModule;


public class ASLMap extends Map {
    private JPopupMenu mainpopup = null;
    private VASL.LOS.Map.Map VASLMap;
    private static final String sharedBoardMetadataFileName = "boardData/SharedBoardMetadata.xml"; // name of the shared board metadata file
    private static SharedBoardMetadata sharedBoardMetadata = null;
    private boolean legacyMode;                     // true if unable to create a VASL map or LOS data is missing
    // counter metadata
    private static CounterMetadataFile counterMetadata = null;
    // used to log errors in the VASSAL error log
    private static final Logger logger = LoggerFactory.getLogger(ASLMap.class);
    private ShowMapLevel showmaplevel = ShowMapLevel.ShowAll;
    // background color preference
    private static final String preferenceTabName = "VASL";

    //JY - independent zoom factors for the boards and pieces
    //Intended to be activated by a separate extension, but needs to be coded in the main module
    protected ASLStackMetrics ASLmetrics;
    private static double bZoom; //Additional zoom factor for the boards only
    private static double oldbZoom;
    private ArrayList<String> pieceslotgpidlist = new ArrayList<>(); //List of pieces that scale with the board (mostly overlays)
    public static final String SCALEWITHBOARDZOOM = "ScaleWithBoardZoom"; //Property name for any counters that should scale with the board zoom level
    public static final String SCALEWITHBOARDMAG = "ScaleWithBoardMag"; //Property name for any counters that should also scale with the board magnification (not the same as the zoom)
    public ArrayList<String> dxAvailBoards = new ArrayList<>(); //List of all available deluxe boards
    protected LinkedList<VASL.LOS.Map.Hex> hexestofixlist = new LinkedList<>();

    public ASLMap() {
        super();
        setbZoom(1.0D);

        try {
            readMetadata();
        } catch (JDOMException e) {
            // give up if there's any problem reading the shared metadata file
            ErrorDialog.bug(e);
        }
        mainpopup = new JPopupMenu();
        // creation of the toolbar button that opens the popup menu
        JButton lMenu = new JButton();

        try {
            lMenu.setIcon(new ImageIcon(Op.load("QC/menu.png").getImage(null)));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        lMenu.setMargin(new Insets(0, 0, 0, 0));
        lMenu.setAlignmentY(0.0F);
        lMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                if (evt.getSource() instanceof JButton) {
                    mainpopup.show((JButton) evt.getSource(), 0, 0);
                }
            }
        });

        // add the first element to the popupp menu
        JMenuItem selectitem = new JMenuItem("Select");
        selectitem.setBackground(new Color(255, 255, 255));
        mainpopup.add(selectitem);
        mainpopup.addSeparator();
        // add the menu button to the toolbar
        getToolBar().add(lMenu);
        getToolBar().addSeparator();

        // background color preference
        final ColorConfigurer backgroundcolor = new ColorConfigurer("backcolor", "Set Color of space around Map (requires VASL restart)", Color.white);
        getGameModule().getPrefs().addOption(preferenceTabName, backgroundcolor);

    }

    @Override
    public void addTo(Buildable b) {
        super.addTo(b);

        // This code is added to fix an issue with Linux users when using seperate windows, the drag
        // gesture is not reinitializing the counter image when entering the main map from the VASL
        // counters window. The issue is that the main map uses the ASLPiecemover and the counter window
        // uses the older Piecemover.
        if (System.getProperty("os.name").contains("nux")) {
            DropTargetListener aslPieceListener = ASLPieceMover.DragHandler.makeDropTarget(this.theMap, 2, this);
            DropTargetListener pieceListener = PieceMover.DragHandler.makeDropTarget(this.theMap, 2, this);

            // Define the combined listener
            DropTargetListener combinedListener = new DropTargetListener() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    pieceListener.dragEnter(dtde);
                }

                @Override
                public void dragOver(DropTargetDragEvent dtde) {
                    aslPieceListener.dragOver(dtde);
                }

                @Override
                public void dropActionChanged(DropTargetDragEvent dtde) {
                    aslPieceListener.dropActionChanged(dtde);
                }

                @Override
                public void dragExit(DropTargetEvent dte) {
                    aslPieceListener.dragExit(dte);
                }

                @Override
                public void drop(DropTargetDropEvent dtde) {
                    aslPieceListener.drop(dtde);
                }
            };

            // Set the DropTarget with the combined listener
            this.theMap.setDropTarget(new DropTarget(this.theMap, DnDConstants.ACTION_MOVE, combinedListener, true));
        } else {
            this.theMap.setDropTarget(ASLPieceMover.DragHandler.makeDropTarget(this.theMap, 2, this));
        }
    }

    /*
     *  Work-around for VASL board being 1 pixel too large causing double stacks to form along board edges.
     *  Any snap to a board top or left edge half hex, bump it 1 pixel up or left on to the next board.
     *
     *  */
    public Point snapTo(Point p) {

        final Point pSnapTo = super.snapTo(p);
        Point pShiftedXY, pShiftedY, pShiftedX;

        pShiftedXY = new Point(pSnapTo);

        pShiftedXY.x -= 3;
        pShiftedXY.y -= 3; // move the snap point 3 pixel up and left: if the map changes, the snapTo could return a different point, otherwise nothing changes
        pShiftedXY = super.snapTo(pShiftedXY);

        if (findBoard(pShiftedXY) != null) { //  Return to the snapTo point if moved off the top border or the left border
            return pShiftedXY;
        }
        pShiftedY = new Point(pSnapTo);

        pShiftedY.y -= 3; // move the snap point 3 pixel up: if the map changes, the snapTo could return a different point, otherwise nothing changes
        pShiftedY = super.snapTo(pShiftedY);

        if (findBoard(pShiftedY) == null) { // moved off the top border, return to the snapTo point
            pShiftedY.y = pSnapTo.y;
        }
        pShiftedX = new Point(pShiftedY);

        pShiftedX.x -= 3; // move the snap point 3 pixel left: if the map changes, the snapTo could return a different point, otherwise nothing changes
        pShiftedX = super.snapTo(pShiftedX);

        if (findBoard(pShiftedX) == null) { // moved off the left border
            return pShiftedY;
        }
        return pShiftedX;

    }

    // return the popup menu
    public JPopupMenu getPopupMenu() {
        return mainpopup;
    }

    @Override
    public synchronized void setBoards(Collection<Board> c) {
        final GameModule mod = getGameModule();
        for (Board boardc : c) {
            VASLBoard testboardexists = (VASLBoard) boardc;
            if (testboardexists.getVASLBoardArchive() == null) {
                mod.getChatter().send("Board missing. Auto-synching of boards requires board directory in board picker matches the board directory set in preferences. Close this game and start new game");
                return;
            }
        }
        super.setBoards(c);
        String info = "Using board(s): ";
        for (Board board : boards) {
            ASLBoard b = (ASLBoard) board;
            info += b.getName() + "(v" + b.getVersion() + ") ";
        }
        mod.warn(info);
        buildVASLMap();

        // BoardZoomer methods
        findOverlays();
        createDeluxeBoardsList();

        // Add OBObserver location
        if (VASLMap != null) {
            for (GameComponent gc : mod.getGameState().getGameComponents()) {
                //String classname = gc.getClass().getName();
                if (gc.getClass().getName() == "VASL.build.module.OBA") {
                    OBA oba = (OBA) gc;
                    oba.checkforOBO();
                }
            }
        }
    }

    /**
     * read the shared board metadata
     */
    private void readMetadata() throws JDOMException {

        final DataArchive archive = getGameModule().getDataArchive();
        // shared board metadata
        try (InputStream inputStream = archive.getInputStream(sharedBoardMetadataFileName)) {
            sharedBoardMetadata = new SharedBoardMetadata();
            sharedBoardMetadata.parseSharedBoardMetadataFile(inputStream);

            // give up on any errors
        } catch (IOException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        } catch (JDOMException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        } catch (NullPointerException e) {
            sharedBoardMetadata = null;
            throw new JDOMException("Cannot read the shared metadata file", e);
        }
    }

    /**
     * Builds the VASL map
     * a VASL map is required by the VASL LOS engine; if map does not support VASL LOS checking then no need for VASL Map
     * the VASL map is first created as an empty shell of the correct size in pixels and with a hex grid
     * that has the required number of hexes and also the proper hex configuration along the left and right map edges
     * this grid will always have hex A1 as the top left hex with whatever hex configuration (full/half height and width)
     * is required by cropping
     * after the empty VASL map is created addBoardsToMap is called to populate the VASL map with board data and to handle flipping
     */
    protected void buildVASLMap() {
        // setup for map window
        final GameModule mod = getGameModule();
        // set background color from preference
        super.bgColor = (Color) mod.getPrefs().getValue("backcolor");
        // set Player Window Always on Top from preference
        final Boolean alwaysontop = Boolean.TRUE.equals(mod.getPrefs().getValue("PWAlwaysOnTop"));
        mod.getPlayerWindow().setAlwaysOnTop(alwaysontop);
        repaint();

        // set variables
        legacyMode = false;  // if legacyMode then no VASL LOS checking
        boolean nullBoards = false; // are null boards being used?
        LinkedList<VASLBoard> vaslboards = new LinkedList<VASLBoard>(); // list of boards
        String gridconfigWidth = "";
        String fliphexconfig = "";
        boolean iscropping = false;
        double hexheight = 0.0; //hex height in pixels
        double hexwidth = 0.0;  //hex width in pixels
        int indexOfCol1 = 0;  //numerical index of map columns (A, B, C, . . .) zero-based
        int indexOfCol2 = 0;
        int valueOfRow1 = 0;  //numerical index of map Rows - zero-based
        int valueOfRow2 = 0;
        int mapwidthinhexes =0;
        int mapheightinhexes =0;
        // populate board list
        try {
            // see if there are any legacy boards in the board set
            // and determine the size of the map in pixels
            final Rectangle mapBoundary = new Rectangle(0, 0);
            for (Board b : boards) {
                final VASLBoard board = (VASLBoard) b;
                // if legacy, abort
                if (!"NUL".equals(b.getName()) && !"NULV".equals(b.getName())) {
                    if (board.isLegacyBoard()) {
                        throw new Exception("VASL LOS disabled - Board " + board.getName() + " does not support LOS checking. VASSAL los active - safe to continue play");
                    }
                    mapBoundary.add(b.bounds());
                }
                //mapBoundary.add(b.bounds());
                vaslboards.add(board);
                // make sure the hex geometry of all boards is the same
                if (hexheight != 0.0 && Math.round(board.getHexHeight()) != Math.round(hexheight) || hexwidth != 0.0 && Math.round(board.getHexWidth()) != Math.round(hexwidth)) {
                    throw new Exception("VASL LOS disabled: Map configuration contains multiple hex sizes. VASSAL los active - safe to continue play");
                }
                hexheight = board.getHexHeight();
                hexwidth = board.getHexWidth();
            }
            /* handle non-standard boards separately.
            * there are only 4 but they complexify the crop/flip options enormously so pull out
            * Dinant follows standard board layout and so can be treated as geo
            */
            for (VASLBoard board : vaslboards) {
                if (board.getName().equals("RBv3") || board.getName().equals("RO") || board.getName().equals("DaE") ||
                         board.getName().equals("SG") || board.getName().equals("HT") || board.getName().equals("VotG") ||
                         board.getName().equals("SaPF")) {
                    if (board.isReversed()){
                        return;
                    }
                    buildVASLMapforNonStandardBoards(vaslboards, mapBoundary, mod);
                    return;
                }
            }
            /* all boards past this point have either standard geo 33 x 10 or a/b 17 x 20 configurations
            * with half-hexes on left and right sides and 10/11 or 20/21 hex row configurations
            * no other configurations will work and should be added to the non standard list above
            * the code below will support all possible width and height crops with or without flipping
            * see vasl repo on github Wiki tab for list of all crop and flip configurations
            */
            // this is a hack to fix problem with board geometry. Standard geo hexes cannot have a width greater than 56.25 or they will exceed the board size of 1800 pixels
            // even if they are actually 56.3125 in size
            // ToDo need to edit BoardMetaData.xml to change hexHeight to 56.25 - this is a hack for incorrect BoardMetaData - need to correct Board files
            if (hexwidth == 56.3125) {
                hexwidth = 56.25;
            }
            // remove the edge buffer from the map boundary size
            mapBoundary.width -= edgeBuffer.width;
            mapBoundary.height -= edgeBuffer.height;

            // create the VASL map object with the correct size and underlying hex grid configuration
            // variables to pass cropping values
            gridconfigWidth = "HalfHexWidth"; //A1 default value before cropping/flipping adjustment
            String toplefthexheight = "LeftHexFullHeight"; // holds height of top left hex after crop; start with default value
            String toprighthexheight = "RightHexFullHeight"; // holds height of top right hex after crop; start with default value
            String toplefthexwidth = "HalfHexWidth"; // holds with width of the top left hex after crop; start with default value
            int previousx = 0; int previousy = 0; double passA1centerx = 0; double passA1centery = 0;

            for (VASLBoard b : vaslboards) {
                indexOfCol1 = 0; indexOfCol2 = b.getWidth() - 1; valueOfRow1 = 0; valueOfRow2 = b.getHeight(); //default value
                if (b.isCropped()) {
                    //set Width value
                    if (b.nearestFullRow) {  //value set in ASLBoard.getState() or ASLBoard.crop()
                        // if both left and right edges of this board are cropped, cropgridconfig will equal "FullHexWidth"
                        gridconfigWidth = "FullHexWidth"; // set as default when nearestFullRow is selected
                        toplefthexwidth = "FullHexWidth";
                        // left edge is not cropped to full hex; half width value whether cropped or not
                        if (b.getCropBounds().getX() == 0) {
                            gridconfigWidth = "FullHexWidthLeftHalf";
                            toplefthexwidth = "HalfHexWidth";
                        }
                        // right edge is not cropped to full hex; half width value whether cropped or not
                        if (b.getCropBounds().getMaxX() == b.getUncroppedSize().getWidth()) {
                            gridconfigWidth = "FullHexWidthRightHalf";
                        }
                    } // no need to handle if nearestFullRow is false - simply use default value as all geo and a/b boards are initially halfwidth left and right

                    //retrieve crop values
                    CropValues cropvalues = new CropValues(b, gridconfigWidth, toplefthexwidth);
                    b.nearestFullRow = cropvalues.nearestFullRow;
                    gridconfigWidth = cropvalues.getgridconfigWidth();
                    toplefthexwidth = cropvalues.gettoplefthexwidth();
                    indexOfCol1 = cropvalues.getindexOfCol1();
                    indexOfCol2 = cropvalues.getindexOfCol2();
                    valueOfRow1 = cropvalues.getvalueOfRow1();
                    valueOfRow2 = cropvalues.getvalueOfRow2();

                    // use crop values to determine left- and right-edge hex height configuration
                    // hexgrid contains zero-based arrays so first col, col[0] (ie A) is always even
                    // cropping height in hexes (via Coord) seems to have no impact
                    boolean Col1isOdd = indexOfCol1 % 2 == 0 ? false : true;
                    boolean Col1isEven = !Col1isOdd;
                    boolean Col2isOdd = indexOfCol2 % 2 == 0 ? false : true;
                    boolean Col2isEven = !Col2isOdd;
                    if (Col1isEven) {
                        toplefthexheight = "LeftHexFullHeight";
                    } else if (Col1isOdd) {
                        toplefthexheight = "LeftHexHalfHeight";
                    }
                    if (Col2isEven) {
                        toprighthexheight = "RightHexFullHeight";
                    } else if (Col2isOdd) {
                        toprighthexheight = "RightHexHalfHeight";
                    }
                }
                // flip values
                // these values are set/used here and passed to next method (addBoardsToMap) which uses them to flip
                if (b.isReversed()) {
                    // hex width
                    if (b.nearestFullRow) {
                        fliphexconfig = "FullHexWidth";
                        toplefthexwidth = "FullHexWidth";
                    }
                    if (gridconfigWidth.equals("FullHexWidthRightHalf")) {
                        fliphexconfig = "FullHexWidthLeftHalf";
                        toplefthexwidth = "HalfHexWidth";
                    } else if (gridconfigWidth.equals("FullHexWidthLeftHalf")) {
                        fliphexconfig = "FullHexWidthRightHalf";
                        toplefthexwidth = "FullHexWidth";
                    }
                    // hex height
                    toplefthexheight = toprighthexheight.equals("RightHexFullHeight") ? "LeftHexFullHeight" : "LeftHexHalfHeight";
                    fliphexconfig += toplefthexheight;;
                }

                // set crop variables
                int boardwidthinhexes = indexOfCol2 - indexOfCol1 + 1;
                mapheightinhexes = (int) Math.round(mapBoundary.height / b.getHexHeight());
                if (b.equals(boards.get(0))) {passA1centerx = setA1CenterX(b, toplefthexwidth);}
                if (b.equals(vaslboards.get(0))) {
                    passA1centery = toplefthexheight.equals("LeftHexFullHeight") ? hexheight / 2 : 0;
                }
                // hack to handle wonky boards
                if (b.getName().contains("Dinant") && toplefthexheight.equals("LeftHexFullHeight")) {passA1centery = 32.25;}
                // update map values
                if (b.bounds().getX() > previousx) {
                    mapwidthinhexes += previousx == 0 ? boardwidthinhexes : boardwidthinhexes -1;
                    previousx += b.bounds().getX();
                }
            }

            // create the map for either a single or multi-board map; missing values just the shell
            VASLMap = new VASL.LOS.Map.Map(vaslboards, passA1centerx, passA1centery, sharedBoardMetadata.getTerrainTypes(), mapwidthinhexes, mapheightinhexes, mapBoundary);
            addBoardsToMap(vaslboards, mod, fliphexconfig);
            if (VASLMap != null) {mod.warn("VASL LOS Enabled");}

            // clean up and fall back to legacy mode if an unexpected exception is thrown
        } catch (BoardException e) {
            setLegacyMode();
            logError(e.toString());
            mod.getChatter().send(e.toString());
        } catch (Exception e) {
            setLegacyMode();
            vaslboards = null;
            logError("LOS disabled - unexpected error");
            logException(e);
            mod.getChatter().send("VASL LOS disabled due to unexpected board issue. Safe to continue play. Use VASSAL LOS string");
        }

        if (hexestofixlist.size() != 0){
            doBuildingfix();
        }
    }

    /**
     * Overlay color schemes do not distinguish between building types
     * Fixes building hexes in an overlay which has multiple building types
     * such overlays are handled as exceptions:
     * - asks user to select each hex covered by overlay and specify the building type
     * - the Terrain Grid is updated to apply these types to building pixels
     * - Hex data is updated to include terrain type and create level locations as required
     */
    private void doBuildingfix() {
        JFrame frame = new JFrame("Adding Overlays To LOS");
        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        Box b = Box.createHorizontalBox();
        // Create a JComboBox
        JLabel hexlabel = new JLabel("Select Hex:");
        final JComboBox<String> hexList = new JComboBox<>();
        for (Hex fixhex : hexestofixlist) {
            hexList.addItem(fixhex.getName());
        }
        hexList.setSelectedIndex(0); // Optional: sets the default selected item
        // Add an ActionListener to handle item selection events
        hexList.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Get the selected item
                String newhex = (String) hexList.getSelectedItem();
            }
        });
        b.add(hexlabel);
        b.add(hexList);
        frame.getContentPane().add(b);

        b = Box.createHorizontalBox();
        b.add(new JLabel("Select Building Type for Hex: "));
        // Define the items for the dropdown list
        String[] buildingtypes = {"Stone Building", "Wooden Building", "Stone Building, 1 Level", "Wooden Building, 1 Level", "Stone Building, 2 Level", "Wooden Building, 2 Level", "Stone Building, 3 Level", "Wooden Building, 3 Level"};
        // Create a JComboBox
        final JComboBox<String> buildingList = new JComboBox<>(buildingtypes);
        buildingList.setSelectedIndex(0); // Optional: sets the default selected item
        // Add an ActionListener to handle item selection events
        buildingList.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Get the selected item
                String newterrain = (String) buildingList.getSelectedItem();

                            }
        });
        b.add(buildingList);
        frame.getContentPane().add(b);
        JButton updateButton = new JButton("Update");
        updateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String newhex = (String) hexList.getSelectedItem();
                String newterrain = (String) buildingList.getSelectedItem();
                updateBuildingHex(newhex, newterrain);
            }
        });
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                frame.setVisible(false);
            }
        });

        b = Box.createHorizontalBox();
        b.add(updateButton);
        b.add(closeButton);
        frame.getContentPane().add(b);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                //save();
                frame.setVisible(false);
            }
        });

        // Make the frame visible
        frame.pack();
        frame.setVisible(true);

    }

    private void updateBuildingHex(String hexname, String terrainname) {
        VASL.LOS.Map.Map losMap = getVASLMap();
        Hex fixhex = losMap.getHex(hexname);
        int startx = (int) fixhex.getCenterLocation().getLOSPoint().getX() - (int) (losMap.getHexWidth()/2 +5);
        int starty = (int) fixhex.getCenterLocation().getLOSPoint().getY() - (int) (losMap.getHexHeight()/2 + 5);

        for (int x = startx ; x < fixhex.getCenterLocation().getLOSPoint().getX() + (losMap.getHexWidth()/2 + 6); x++){
            for (int y = starty; y < fixhex.getCenterLocation().getLOSPoint().getY() + (losMap.getHexHeight()/2 + 6); y++){
                Point testpoint = new Point(x, y);
                if (fixhex.contains(testpoint)){
                    if(losMap.getGridTerrain(x, y).isBuilding()){
                        int terrtype = losMap.getTerrain(terrainname).getType();
                        losMap.setGridTerrainCode(terrtype, x, y);
                    }
                }
            }
        }
        fixhex.resetTerrain();
    }

    /**
     * Populates the VASL map with terrain, elevation, and hex information from the boards used in the map
     * For each board used in the map, this method:
     * - adds the LOS data
     * - amends the LOS data for terrain transformations applied to the map
     * - amends the LOS data to reflect overlays applied to the map
     * - flips the Terrain and elevation LOS data which should not be flipped until the previous two changes have been applied
     */
    protected void addBoardsToMap(LinkedList<VASLBoard> vaslboards, GameModule mod, String fliphexconfig) {
        for (VASLBoard board : vaslboards) {
            // no need to add losdata for NUL boards; just placeholders
            if (!"NUL".equals(board.getName()) && !"NULV".equals(board.getName())) {
                addOneBoardToMap(board, mod, fliphexconfig);
            }
        }
        // ToDo test this method is working properly - especially for hexsides (Bocage!)
        // does it need to be done here?
        VASLMap.resetHexTerrain(0);

    }

    /**
     * Populates the VASL map with terrain, elevation, and hex information from the boards used in the map
     * For each board used in the map, this method:
     * - adds the LOS data
     * - amends the LOS data for terrain transformations applied to the map
     * - amends the LOS data to reflect overlays applied to the map
     * - flips the Terrain and elevation LOS data which should not be flipped until the previous two changes have been applied
     */
    protected void addHASLBoardsToMap(LinkedList<VASLBoard> vaslboards, GameModule mod, String fliphexconfig) throws BoardException {
        for (VASLBoard board : vaslboards) {
            addOneHASLBoardToMap(board, mod, fliphexconfig);
        }
        // ToDo test this method is working properly - especially for hexsides (Bocage!)
        // does it need to be done here?
        VASLMap.resetHexTerrain(0);

    }
    protected void addOneBoardToMap(VASLBoard board, GameModule mod, String fliphexconfig) {
        // add the board to the VASL map
        try {
            if (!legacyMode) {
                // Add the LOS data to the map - cropped if necessary
                ASLPersistElevation aslpe = new ASLPersistElevation();
                boolean persistelevation = aslpe.setpersistelevprop(true);
                VASL.LOS.Map.Map newvaslmap = board.getVASLBoardArchive().addLOSDatatoVASLMap(sharedBoardMetadata.getTerrainTypes(), board, VASLMap, fliphexconfig, this, persistelevation);
                VASLMap = newvaslmap;
            }
        } catch (BoardException e) {
            setLegacyMode();
            logError("LOS disabled - unexpected error");
            mod.getChatter().send("VASL LOS Disabled. Safe to continue to play: VASSAL los active");
        } catch (Exception e) {
            setLegacyMode();
            logError("LOS disabled - unexpected error");
            logException(e);
            mod.getChatter().send("VASL LOS disabled due to Board issue. Safe to continue to play. VASSAL los active");
        } finally {

        }
    }

    protected void addOneHASLBoardToMap(VASLBoard board, GameModule mod, String fliphexconfig) throws BoardException {
        // add the board to the VASL map
        try {
            if (!legacyMode) {
                // Add the LOS data to the map - cropped if necessary
                ASLPersistElevation aslpe = new ASLPersistElevation();
                boolean persistelevation = aslpe.setpersistelevprop(true);
                VASL.LOS.Map.Map newvaslmap = board.getVASLBoardArchive().addHASLLOSDatatoVASLMap(sharedBoardMetadata.getTerrainTypes(), board, VASLMap, fliphexconfig, this, persistelevation);
                VASLMap = newvaslmap;
            }
        } catch (BoardException e) {
            setLegacyMode();
            logError("LOS disabled - unexpected error");
            mod.getChatter().send("VASL LOS Disabled. Safe to continue to play: VASSAL los active");
        } catch (Exception e) {
            setLegacyMode();
            logError("LOS disabled - unexpected error");
            logException(e);
            mod.getChatter().send("VASL LOS disabled due to Board issue. Safe to continue to play. VASSAL los active");
        } finally {

        }
    }

    /**
     * Builds the VASL map
     * a VASL map is required by the VASL LOS engine; if map does not support VASL LOS checking then no need for VASL Map
     * the VASL map is first created as an empty shell of the correct size in pixels and with a hex grid
     * that has the required number of hexes and also the proper hex configuration along the left and right map edges
     * this grid will always have hex A1 as the top left hex with whatever hex configuration (full/half height and width)
     * is required by cropping
     * after the empty VASL map is created addBoardsToMap is called to populate the VASL map with board data
     *
     * Use this method to initiate LOS for non-standard boards that support los checking (currently only RBv3, RO, Singling and DaE)
     */
    private void buildVASLMapforNonStandardBoards(LinkedList<VASLBoard> vaslboards, Rectangle mapBoundary, GameModule mod) {
        String passcropgridconfig = "Normal"; //default value before cropping/flipping adjustment
        String passboardgridconfig = "Normal"; // default value of grid configuration of geo and a/b boards
        boolean iscropping = false;
        double hexheight = 0.0; //hex height in pixels
        double hexwidth = 0.0;  //hex width in pixels
        String gridconfigWidth = "";
        String fliphexconfig = "";
        int indexOfCol1 = 0;  //numerical index of map columns (A, B, C, . . .) zero-based
        int indexOfCol2 = 0;
        int valueOfRow1 = 0;  //numerical index of map Rows - zero-based
        int valueOfRow2 = 0;
        int mapwidthinhexes =0;
        int mapheightinhexes =0;

        // remove the edge buffer from the map boundary size
        mapBoundary.width -= edgeBuffer.width;
        mapBoundary.height -= edgeBuffer.height;

        // create the VASL map object with the correct size and underlying hex grid configuration
        // variables to pass cropping values
        gridconfigWidth = "HalfHexWidth"; //A1 default value before cropping/flipping adjustment
        String toplefthexheight = "LeftHexFullHeight"; // holds height of top left hex after crop; start with default value
        String toprighthexheight = "RightHexFullHeight"; // holds height of top right hex after crop; start with default value
        String toplefthexwidth = "HalfHexWidth"; // holds with width of the top left hex after crop; start with default value
        String toprighthexwidth = "HalfHexWidth";
        int previousx = 0; int previousy = 0; double passA1centerx = 0; double passA1centery = 0;
        // given the small number of HASL/non-standard maps that are los enabled, each is handled individually from now on
        // if number grows may need to change code to reflect map configurations/layouts shared across multiple maps
        try {
            for (VASLBoard b : vaslboards) {
                indexOfCol1 = 0;
                indexOfCol2 = b.getWidth() - 1;
                valueOfRow1 = 0;
                valueOfRow2 = b.getHeight(); //default value
                if (b.getName().contains("RBv3") || b.getName().contains("SaPF")) {
                    gridconfigWidth = "HalfHexWidthOffset";
                    toplefthexheight = "LeftHexFullHeight";
                    toplefthexwidth = "HalfHexWidthOffset";
                } else if (b.getName().contains("RO")) {
                    gridconfigWidth = "HalfHexWidthOffset";
                    toplefthexheight = "LeftHexHalfHeight";
                    toplefthexwidth = "HalfHexWidthOffset";
                } else if (b.getName().contains("DaE")) {
                    gridconfigWidth = "FullHexWidthOffset";
                    toplefthexwidth = "FullHexWidthOffset";
                } else if (b.getName().contains("SG")) {
                    gridconfigWidth = "HalfHexWidthOffset";
                    toplefthexheight = "LeftHexFullHeightOffset";
                    toplefthexwidth = "HalfHexWidthOffset";
                } else if (b.getName().contains("HT")) {
                    gridconfigWidth = "HalfHexWidthOffset";
                    toplefthexheight = "LeftHexFullHeight";
                    toplefthexwidth = "HalfHexWidthOffset";
                } else if (b.getName().contains("VotG")) {
                    gridconfigWidth = "FullHexWidthOffset";
                    toplefthexwidth = "FullHexWidthOffset";
                }
                if (b.isCropped()) {
                    //set Width value
                    if (b.nearestFullRow) {  //value set in ASLBoard.getState() or ASLBoard.crop()
                        // if both left and right edges of this board are cropped, cropgridconfig will equal "FullHexWidth"
                        // left edge is cropped to full hex; otherwise default value by bd
                        if (b.getCropBounds().getX() != 0) {
                            gridconfigWidth = "FullHexWidth";
                            toplefthexwidth = "FullHexWidth";
                        }
                        // right edge is cropped to full hex; if not cropped then default value
                        if (b.getCropBounds().getMaxX() != b.getUncroppedSize().getWidth()) {
                            //gridconfigWidth = "FullHexWidth";
                            toprighthexwidth = "FullHexWidth";
                        } else {
                            if (b.getName().contains("RBv3") || b.getName().contains("SaPF")) {
                                //gridconfigWidth = "HalfHexWidthOffset";
                                toprighthexwidth = "HalfHexWidthOffset";
                            } else if (b.getName().contains("RO")) {
                                //gridconfigWidth = "HalfHexWidthOffset";
                                toplefthexheight = "LeftHexHalfHeight";
                                toprighthexwidth = "HalfHexWidthOffset";
                            } else if (b.getName().contains("DaE")) {
                                //gridconfigWidth = "FullHexWidth";
                                toprighthexwidth = "FullHexWidthOffset";
                            } else if (b.getName().contains("SG")) {
                                //gridconfigWidth = "HalfHexWidthOffset";
                                toplefthexheight = "LeftHexFullHeightOffset";
                                toprighthexwidth = "HalfHexWidthOffset";
                            }else if (b.getName().contains("VotG")) {
                                //gridconfigWidth = "FullHexWidth";
                                toprighthexwidth = "FullHexWidthOffset";
                            }
                        }
                    }
                    else if (b.getCropBounds().getX() != 0){  // board is cropped to half hex on left side
                         toplefthexwidth = "HalfHexWidth";

                    } // if not cropped on left side use default value

                    //retrieve crop values
                    CropValues cropvalues = new CropValues(b, gridconfigWidth, toplefthexwidth);
                    b.nearestFullRow = cropvalues.nearestFullRow;
                    gridconfigWidth = cropvalues.getgridconfigWidth();
                    toplefthexwidth = cropvalues.gettoplefthexwidth();
                    indexOfCol1 = cropvalues.getindexOfCol1();
                    indexOfCol2 = cropvalues.getindexOfCol2();
                    valueOfRow1 = cropvalues.getvalueOfRow1();
                    valueOfRow2 = cropvalues.getvalueOfRow2();

                    // use crop values to determine left- and right-edge hex height configuration
                    // hexgrid contains zero-based arrays so first col, col[0] (ie A) is always even
                    // cropping height in hexes (via Coord) seems to have no impact
                    boolean Col1isOdd = indexOfCol1 % 2 == 0 ? false : true;
                    boolean Col1isEven = !Col1isOdd;
                    boolean Col2isOdd = indexOfCol2 % 2 == 0 ? false : true;
                    boolean Col2isEven = !Col2isOdd;
                    if (Col1isEven) {
                        toplefthexheight = b.getName().equals("RO") ? "LeftHexHalfHeight" : "LeftHexFullHeight";
                        toplefthexheight = b.getName().equals("SG") ? toplefthexheight + "Offset" : toplefthexheight;  // SG has top border
                    } else if (Col1isOdd) {
                        toplefthexheight = b.getName().equals("RO") ? "LeftHexFullHeight" : "LeftHexHalfHeight";
                        toplefthexheight = b.getName().equals("SG") ? toplefthexheight + "Offset" : toplefthexheight;   // SG has top border
                    }
                    if (Col2isEven) {
                        toprighthexheight = b.getName().equals("RO") ? "RightHexHalfHeight" : "RightHexFullHeight";
                    } else if (Col2isOdd) {
                        toprighthexheight = b.getName().equals("RO") ? "RightHexFullHeight" : "RightHexHalfHeight";
                    }

                    // handle row cropping exceptions explicitly
                    if (b.getName().contains("RO") && valueOfRow1 > 0) {
                        toplefthexheight =  Col1isOdd ? "LeftHexHalfHeight" : "LeftHexFullHeight";
                    }
                }
                // No flipping of HASL Boards
                if (b.isReversed()) {
                    JOptionPane.showMessageDialog(null, "Cannot add LOS to a HASL board that is flipped. No update possible at present. Continue without LOS or Re-select Boards with no flipping.",
                            "Cannot Add LOS to this Map . . . ", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                //Leave this code as may try to add support for flipping later
                /*// flip values
                // these values are set/used here and passed to next method (addBoard . . . ) which uses them to flip
                if (b.isReversed()) {
                    // hex width
                    if (b.nearestFullRow) {
                        fliphexconfig = "FullHexWidth";
                        toplefthexwidth = "FullHexWidth";
                    }
                    if (gridconfigWidth.equals("FullHexWidthRightHalf")) {
                        fliphexconfig = "FullHexWidthLeftHalf";
                        toplefthexwidth = "HalfHexWidth";
                    } else if (gridconfigWidth.equals("FullHexWidthLeftHalf")) {
                        fliphexconfig = "FullHexWidthRightHalf";
                        toplefthexwidth = "FullHexWidth";
                    }
                    // hex height
                    toplefthexheight = toprighthexheight.equals("RightHexFullHeight") ? "LeftHexFullHeight" : "LeftHexHalfHeight";
                    fliphexconfig += toplefthexheight;;
                }*/

                // set crop variables
                int boardwidthinhexes = indexOfCol2 - indexOfCol1 + 1;
                mapheightinhexes = (int) Math.round(mapBoundary.height / b.getHexHeight());
                if (b.equals(boards.get(0))) {
                    if (toplefthexwidth.contains("HalfHexWidthOffset")) {
                        passA1centerx = b.getA1CenterX(); // board is not cropped on left edge and A1centerx will include offset
                    }
                    else if ((b.getVASLBoardArchive().getBoardName().contains("DaE") || b.getVASLBoardArchive().getBoardName().contains("VotG")) && toplefthexwidth.contains("FullHexWidthOffset")) {
                        passA1centerx = b.getA1CenterX();
                    }
                    else if (toplefthexwidth.contains("FullHexWidth")) {
                        passA1centerx = b.getHexWidth() / 2;  // board is cropped to a full hex on left side, ignore offset
                    }
                    else if (toplefthexwidth.contains("HalfHexWidth")) {
                        passA1centerx = 0;  // board is cropped to a half hex on left side; ignore offset
                    }

                }
                if (b.equals(vaslboards.get(0))) {
                    if (toplefthexheight.contains("Offset")) {
                        passA1centery = toplefthexheight.contains("HalfHeight") ? (b.getA1CenterY() - b.getHexHeight() /2) : b.getA1CenterY();
                    } else {
                        passA1centery = toplefthexheight.equals("LeftHexFullHeight") ? b.getHexHeight() / 2 : 0;
                    }
                }

                // update map values
                if (b.bounds().getX() > previousx) {
                    mapwidthinhexes += previousx == 0 ? boardwidthinhexes : boardwidthinhexes - 1;
                    previousx += b.bounds().getX();
                }
            }
            // create the map for either a single or multi-board map; missing values just the shell
            boolean haslnongeo = true;
            VASLMap = new VASL.LOS.Map.Map(vaslboards, passA1centerx, passA1centery, sharedBoardMetadata.getTerrainTypes(), mapwidthinhexes, mapheightinhexes, mapBoundary, haslnongeo);
            addHASLBoardsToMap(vaslboards, mod, fliphexconfig);
            if (VASLMap != null) {
                mod.warn("VASL LOS Enabled");
            }
        }
        // clean up and fall back to legacy mode if an unexpected exception is thrown
        catch (Exception e) {
            setLegacyMode();
            logError(e.toString());
            mod.getChatter().send(e.toString());
        }
    }

    private double setA1CenterX(VASLBoard board, String topleftHexWidth) {
        if (topleftHexWidth.equals("HalfHexWidth")) {
            return 0;
        } else if (topleftHexWidth.equals("FullHexWidth")) {
            return board.getHexWidth() / 2;
        } else {
            return 0;
        }
    }

    /**
     * A class that allows the LOSData, Graphic image and point information to be passed to various methods and classes
     * Note that all properties are public to eliminate getter/setter clutter
     */
    //ToDo move this class to LOS.Map.Map
    public class LOSonOverlays {
        public VASL.LOS.Map.Map newlosdata;
        public BufferedImage bi;
        public VASLBoard board;
        public Rectangle ovrrec;
        public int currentx;  //position on overlay
        public int currenty;  // position on overlay
        public int overpositionx; //position on mapboard
        public int overpositiony;  // position on mapboard
        public int ovrXstart;   // left side of overlay
        public int ovrYstart;   // top side of overlay
        public int overXfinish;  // right side of overlay
        public int overYfinish;  // botton side of overlay
        public LinkedList<VASL.LOS.Map.Hex> inherentTerrainHexesToCheckList = new LinkedList<>();

        protected boolean checkIfMapImageTerrainIsInherent(){
            Hex hextotest = newlosdata.gridToHex(overpositionx, overpositiony);
            return hextotest.getCenterLocation().getTerrain().isInherentTerrain() ? true : false;
        }

        protected void addToListOfReplacedInherentTerrainHexes(Hex hexToCheck){
            if (!inherentTerrainHexesToCheckList.contains(hexToCheck)) {
                inherentTerrainHexesToCheckList.add(hexToCheck);
            }
        }
    }

    public VASL.LOS.Map.Map adjustLOSForOverlays(VASLBoard board, VASL.LOS.Map.Map losdata, boolean hexGridtest, boolean persistevelation) {
        //ToDo check this still works with revised cropping and flipping
        final LOSonOverlays losonoverlays = new LOSonOverlays();
        losonoverlays.newlosdata = losdata;
        losonoverlays.board = board;
        final Enumeration overlays = board.getOverlays();
        while (overlays.hasMoreElements()) {
            Overlay o = (Overlay) overlays.nextElement();
            // ToDo test terrain transforms that use underlays
            if (o.getName().equals("")) {  // prevents error when using underlays (which are added as overlays)
                continue;
            }
            // ToDo see if BSO will work with overlay code
            // ToDo check VASLBoard.applyColorSSRulestoTerrainElevationGrids for BSO
            //if (o.getName().contains("BSO") && (!o.getName().contains("BSO_LFT3"))) { // prevents error when using BSO which are handled elsewhere
            //    continue;
            //}
            // ToDo - fixed Cliffs/Cliff bug - this may now work
            if (o.getName().contains("NoCliffs")) { // cliff los adjustment handled in VASLBoard
                continue;
            }
            if (o.getName().contains("LightWoods")) { // Light Woods are handled by LOSSSRule terrain mapping. dont need to go through overlay method
                continue;
            }
            if (o.getName().contains("RB_Gutted")) { //RB Gutted Factory overlays handled in VASLBoard.applyColorSSRulestoTerrainElevationGrids
                continue;
            }
            losonoverlays.ovrrec = o.bounds();

            // get the image as a buffered image
            final Image i = o.getImage();
            losonoverlays.bi = new BufferedImage(i.getWidth(null), i.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            final Graphics2D bgr = losonoverlays.bi.createGraphics();
            bgr.drawImage(i, 0, 0, null);
            bgr.dispose();
            //ToDo these variables seem to be working; confirm for other configurations
            losonoverlays.ovrXstart = (int) (o.bounds().x + (board.bounds().getX() - board.getMap().getEdgeBuffer().getWidth()));
            losonoverlays.ovrYstart = (int) (o.bounds().y + (board.bounds().getY() - board.getMap().getEdgeBuffer().getHeight()));
            // ToDo can setDeirLip() be model for Rice Paddies - banks?
            String terraintype = getOverlayTerrainType(o);
            terraintype = resetfortransform(terraintype, losonoverlays);
            //setOverlayTerrain(losonoverlays, terraintype, o.getPreserveElevation());
            // add preserve board elevation status
            if (persistevelation) {
                o.setPersistElevation(persistevelation);
                o.transform(persistevelation);
            }
            if(hexGridtest){
                // need to flip overlay as Terrain and Elevation Grids are flipped by this point
                if (board.isReversed()) {
                    // need to calculate coordinates of the overlay being used taking cropping into account
                    if (board.isCropped()) {

                    }
                    else {
                        int boardwidth  = 1800; //(int) (board.isCropped() ? board.getCropBounds().getWidth() : board.bounds().getWidth()) ;
                        if (boardwidth == -1) {boardwidth = (int) board.bounds().getWidth();}
                        int boardheight = 645; //(int) (board.isCropped() ? board.getCropBounds().getHeight() : board.bounds().getHeight());
                        if (boardheight == -1) {boardheight = (int) board.bounds().getHeight();}
                        losonoverlays.ovrXstart = (int) (boardwidth - (o.bounds().x + o.bounds().getWidth() ) + (board.bounds().getX() - board.getMap().getEdgeBuffer().getWidth()));
                        losonoverlays.ovrYstart = (int) (boardheight - (o.bounds().y + o.bounds().getHeight()) + (board.bounds().getY() - board.getMap().getEdgeBuffer().getHeight()));
                    }
                }
                updateHexGridforOverlayTerrain(losonoverlays, terraintype, o.getPersistElevation(), board.isReversed());
            }
            else {
                updateTerrainElevationGridsforOverlays(losonoverlays, terraintype, o);
            }
        }
        losonoverlays.newlosdata.buildHillocks();
        losonoverlays.newlosdata.setDeirLip();
        return losonoverlays.newlosdata;
    }

    private void setDuneCrest(LOSonOverlays losonoverlays, int usepositionx, int usepositiony, Rectangle ovrRec, boolean isreversed) {
        // reset the terrain
        //ToDo fix this code; it is not working
        //return;
        Hex dunehex = null;
        Location dunecrestloc = null;
        if (isreversed) {
            // check code here - why duplicate 2 and 4th line?
            dunehex = losonoverlays.newlosdata.gridToHex(usepositionx - ovrRec.x, usepositiony - ovrRec.y);
            dunecrestloc = dunehex.getNearestLocation(usepositionx - ovrRec.x, usepositiony);
            dunehex = losonoverlays.newlosdata.gridToHex(usepositionx + ovrRec.x, usepositiony + ovrRec.y);
            dunecrestloc = dunehex.getNearestLocation(usepositionx + ovrRec.x, usepositiony);
        }
        int hexside = dunehex.getLocationHexside(dunecrestloc);
        if (hexside != -1) {
            dunehex.setHexsideTerrain(hexside, losonoverlays.newlosdata.getTerrain("Dune, Crest Low"));
            dunehex.setHexsideLocationTerrain(hexside, losonoverlays.newlosdata.getTerrain("Dune, Crest Low"));
        }
    }

    private void updateTerrainElevationGridsforOverlays(LOSonOverlays losonoverlays, String terraintype, Overlay o) {
        // ToDo delete this call if no longer required
        // first test for inherent terrain type and send to separate method; use this method for non-inherent or mixed non-inherent/inherent overlays
        //if (isInherenttype(terraintype)) {
        //    updateTerrainElevationGridsforOverlayInherentTerrain(losonoverlays, terraintype);
        //} else {
            boolean preserveelevation = o.getPersistElevation();
            HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> inhhexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
            HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> bdghexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
            losonoverlays.overpositionx = 0; //position on map
            losonoverlays.overpositiony = 0; // position on map
            int c = 0; Terrain terr = null; int elevint = 0;
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    losonoverlays.overpositionx = losonoverlays.currentx + (int) losonoverlays.ovrXstart - (int) losonoverlays.board.getCropBounds().getX();
                    losonoverlays.overpositiony = losonoverlays.currenty + (int) losonoverlays.ovrYstart - (int) losonoverlays.board.getCropBounds().getY();
                    if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx, losonoverlays.overpositiony) && losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony) != null) {
                        c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                        terr = null; elevint = 0; Color color = null; //clear previous values
                        if ((c >> 24) != 0x00 ){
                            // not a transparent pixel
                            //Retrieving the R G B values
                            color = getRGBColor(c);
                            terr = getOverlayTerrainfromColor(color, losonoverlays);
                        }
                        else { // handle transparent pixel on overlay - ToDO move to method
                            terr = setTerrainForTransparentPixel(losonoverlays);
                            if (terr == null) {
                                continue;
                            }

                            /*
                            // handle special case of transparent center dot
                            if (isCenterDot(losonoverlays)) {
                                color = getOverlayNearestColor(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                                if (color == Color.BLACK) {  // no other color around center dot meaning whole hex is transparent
                                    continue;
                                } if (color.equals(Color.white)) {
                                    terr = losonoverlays.newlosdata.getTerrain(losonoverlays.board.getVASLBoardArchive().getTerrainForVASLColor("L0Winter"));
                                } else {
                                    terr = getOverlayTerrainfromColor(color, losonoverlays);
                                    if (terr == null) {
                                        terr = fixnullterrain(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                                    }
                                }

                            }*/
                        }

                        // special case for transform where image does not change
                        terr = resetterraintypefortransform(losonoverlays.board.getTerrainChanges(), terr);
                        // special case for building overlays
                        if (terr.isBuildingTerrain()) {
                            if (!terr.isHexsideTerrain()) {
                                setBuildingTerrainFromOverlay(losonoverlays, o, terr);
                            }
                            else {
                                // is Rowhouse or Factory Wall
                                losonoverlays.newlosdata.setGridTerrainCode(terr.getType(), losonoverlays.overpositionx, losonoverlays.overpositiony);
                            }
                        }
                        else {
                            // handle terrain update
                            losonoverlays.newlosdata.setGridTerrainCode(terr.getType(), losonoverlays.overpositionx, losonoverlays.overpositiony);
                        }

                        // handle elevation update
                        if (!preserveelevation) {
                            elevint = getOverlayElevationfromColor(losonoverlays, color);
                        }
                        // if elevint = -99 then method above could not find a proper elevation for terrain; revert to current elevation in mapboard losdata
                        if (elevint == -99 || preserveelevation ) {
                            elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
                            //ToDo add code to preserve terraincode
                        }
                        if (terr.isDepression()) {
                            elevint = preserveelevation ? losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony) - 1 : -1;
                        //    losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setBaseLevelofHex(elevint);
                        }

                        //if (!preserveelevation) {
                            // ToDo turn this into a method if can do so with reversed board
                            //set elevation for point; no need to set if preserveelevation=true; just use elevation from board

                        losonoverlays.newlosdata.setGridElevation(elevint, losonoverlays.overpositionx, losonoverlays.overpositiony);

                    }
                }
            }
    }

    // handles all the hexGrid changes for Overlays that can't be added until all Terrain/Elevation changes are done
    private void updateHexGridforOverlayTerrain(LOSonOverlays losonoverlays, String terraintype, boolean preserveelevation, boolean isreversed) {
        // ToDo delete this call if no longer needed
        // first test for inherent terrain type and send to separate method; use this method for non-inherent or mixed non-inherent/inherent overlays
        //if (isInherenttype(terraintype)) {
        //    updateTerrainElevationGridsforOverlayInherentTerrain(losonoverlays, terraintype);
        //} else {
        HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> inhhexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
        HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> bdghexes = new HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain>();
        LinkedList<VASL.LOS.Map.Hex> elevhexes = new LinkedList<VASL.LOS.Map.Hex>();
        //    losonoverlays.overpositionx = 0; losonoverlays.overpositiony = 0;
        //    int overlayx =0; int overlayy = 0;
        int c = 0; Terrain terr = null; int elevint = 0;
            // ToDo Need to flip the overlay for this to work when board is reversed - HOW?
        //for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
        //    for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
        for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.newlosdata.getGridWidth(); losonoverlays.currentx++) {
            for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.newlosdata.getGridHeight(); losonoverlays.currenty++) {

                    //ToDo need to sort this out: need to calculate overpositionx and overpositony for normal and flipped configurations
                    /*if (isreversed) {
                        // position on overlay - work back to front to reflect flip
                        overlayx = losonoverlays.bi.getWidth() - losonoverlays.currentx -1;
                        overlayy = losonoverlays.bi.getHeight() - losonoverlays.currenty -1;
                        //position on map
                        losonoverlays.overpositionx = losonoverlays.currentx;
                        losonoverlays.overpositionx += (int) losonoverlays.ovrXstart;
                        //losonoverlays.overpositionx -= (int) losonoverlays.board.getCropBounds().getX();
                        losonoverlays.overpositiony = losonoverlays.currenty + (int) losonoverlays.ovrYstart; // - (int) losonoverlays.board.getCropBounds().getY();
                    }
                    else {
                        overlayx = losonoverlays.currentx;
                        overlayy = losonoverlays.currenty;
                        losonoverlays.overpositionx = losonoverlays.currentx + (int) losonoverlays.ovrXstart - (int) losonoverlays.board.getCropBounds().getX();
                        losonoverlays.overpositiony = losonoverlays.currenty + (int) losonoverlays.ovrYstart - (int) losonoverlays.board.getCropBounds().getY();
                    }*/
                losonoverlays.overpositionx = losonoverlays.currentx + (int) losonoverlays.ovrXstart - (int) losonoverlays.board.getCropBounds().getX();
                losonoverlays.overpositiony = losonoverlays.currenty + (int) losonoverlays.ovrYstart - (int) losonoverlays.board.getCropBounds().getY();
                if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx, losonoverlays.overpositiony) && losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony) != null) {

                //ToDo could simplify this by using the terrain code from the terrainGrid as that is already set!
                terr = null; elevint = 0; //clear previous values
                //if ((c >> 24) != 0x00) { // not a transparent pixel
                    //Retrieving the R G B values
                    Color color = null; // = getRGBColor(c);
                    terr = losonoverlays.newlosdata.getGridTerrain(losonoverlays.overpositionx, losonoverlays.overpositiony);
                    //terr = getOverlayTerrainfromColor(color, losonoverlays);
                    while (terr == null) {  // handles cases where pixel color does not match any color from ShardBoardMetaData.xml
                        color = getOverlayNearestColor(losonoverlays, losonoverlays.currentx, losonoverlays.currenty);
                        if (color.equals(Color.white)) {
                            terr = losonoverlays.newlosdata.getTerrain(losonoverlays.board.getVASLBoardArchive().getTerrainForVASLColor("L0Winter"));
                        } else {
                            terr = getOverlayTerrainfromColor(color, losonoverlays);
                            if (terr == null) {
                                terr = fixnullterrain(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                            }
                        }
                    }
                    // special case for removing inherent terrain beneath overlay
                    // this no longer seems to be needed; remove in 673 if no problems in 672
                    /*if(losonoverlays.checkIfMapImageTerrainIsInherent()){
                        // add to list to be processed in updateHexGridforOverlayTerrain
                        Hex hextocheck = losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony);
                        //this is a hack to fix a problem with rice paddy overalys
                        if (!hextocheck.getCenterLocation().getTerrain().getName().equals("Rice Paddy, In Season") &&
                                !hextocheck.getCenterLocation().getTerrain().getName().equals("Rice Paddy Bank")){
                            losonoverlays.addToListOfReplacedInherentTerrainHexes(hextocheck);
                        }
                    }*/

                    // special case for transform where image does not change
                    //ToDo is this needed in this loop or in TEGrid loop
                    terr = resetterraintypefortransform(losonoverlays.board.getTerrainChanges(), terr);
                    //add Hex to collections of inherent hexes and building hexes on the overlay
                    addHextoOverlayInhandBldgMaps(terraintype, terr, losonoverlays, inhhexes, bdghexes);
                    addHextoOverlayElevationMaps (terr, losonoverlays, elevhexes);
                    // set terrain type for center location or hexside location (if hexside terrain)
                    setOverlayTerrainType(losonoverlays, terr, terraintype);
                    // handle elevation update
                    // elevint = getOverlayElevationfromColor(losonoverlays, color);
                    elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
                    // if elevint = -99 then method above could not find a proper elevation for terrain; revert to current elevation in mapboard losdata
                    /*if (elevint == -99) {
                        elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
                    }*/
                    // reset base level of Hex then adjust if depression - ToDO check this depression routine is needed; may double down
                    losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setBaseLevelofHex(elevint);
                    if (terr.isDepression()) {
                        elevint = preserveelevation ? losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony) - 1 : -1;
                        losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setBaseLevelofHex(elevint);
                        if (!preserveelevation) {
                            // ToDo turn this into a method if can do so with reversed board
                            //set elevation for point; no need to set if preserveelevation=true; just use elevation from board
                            //losonoverlays.newlosdata.setGridElevation(elevint, losonoverlays.overpositionx, losonoverlays.overpositiony);
                            //test if pixel is hex center
                            if (losonoverlays.overpositionx == (int) (losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).getHexCenter()).getX() &&
                                    losonoverlays.overpositiony == (int) (losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).getHexCenter()).getY()) {

                                // if white center dot on overlay aligns with hex center, won't set elevation properly so need to look for nearby terrain type
                                // bit of a hack but should work - try it until we get a bug
                                color = getRGBColor(c);
                                if (color.equals(Color.white) || color.equals(Color.black)) { // && j<=(x+6)) {
                                    color = getOverlayNearestColor(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                                    elevint = color.equals(Color.white) ? 0 : getOverlayElevationfromColor(losonoverlays, color);
                                    // if elevint = -99 then method above could not find a proper elevation for terrain; revert to current elevation in mapboard losdata
                                    if (elevint == -99) {
                                        elevint = 0;  //this is a hack and may not always return a useful result - watch for errors
                                    }
                                    // add depression terrain test as elevation will always be unknown for them - depression must be on overlay
                                    if (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx, losonoverlays.currenty)) {
                                        terr = getOverlayTerrainfromColor(color, losonoverlays);
                                        if (terr == null) {
                                            terr = fixnullterrain(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                                            // use OG with elevint from existing losdata; this is a hack when can't find terrain
                                        }
                                        if (terr.isDepression()) {
                                            // use current base level for point as it will have been set in line 1145
                                            elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
                                            //losonoverlays.newlosdata.setGridElevation(elevint, losonoverlays.overpositionx, losonoverlays.overpositiony);
                                        }
                                    }
                                }
                            }
                        }
                        // this sets base elevation for the hex - crest line & depression hexes can contain multiple elevations
                        // "SnowHexDots2" is a hack for LFT3; change if applies to other boards
                        if (!(color == null) && !losonoverlays.board.getVASLBoardArchive().getVASLColorName(color).contains("SnowHexDots2")) {
                                    losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony).setBaseLevelofHex(elevint);
                        }
                   }

                }
         //          }
            }
        }
        if (!losonoverlays.inherentTerrainHexesToCheckList.isEmpty()){
            checkInherentTerrainProperlyRemoved(losonoverlays);
        }
        addOverlayInhTerrainToLOS(inhhexes, losonoverlays, losonoverlays.board);
        //addOverlayBldgLevelsToLOS(bdghexes, losonoverlays);
        addOverlayHexElevationToLOS(elevhexes, losonoverlays);
        //}
    }

    private void checkInherentTerrainProperlyRemoved(LOSonOverlays losonoverlays){
        for (Hex hexToCheck : losonoverlays.inherentTerrainHexesToCheckList) {
            Terrain terr = hexToCheck.getCenterLocation().getTerrain();
            //if (!terr.isInherentTerrain()) {
                //loop through all pixels in hex
                Rectangle s = hexToCheck.getHexBorder().getBounds();
                for (int i = (int) s.getX(); i < s.getX() + s.getWidth(); i++) {
                    for (int j = (int) s.getY(); j < s.getY() + s.getHeight(); j++) {
                        if (losonoverlays.newlosdata.onMap(i, j)) {
                            if (hexToCheck.contains(i, j)) {
                                // if still inherent change to OG
                                if (losonoverlays.newlosdata.getGridTerrain(i, j).isInherentTerrain()) {
                                    losonoverlays.newlosdata.setGridTerrainCode(0, i, j); // 0 = Open Ground
                                }
                            }
                        }
                    }
                }
                hexToCheck.getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terr.getType()));
                hexToCheck.resetHexsideTerrain(0);
            //}
        }

    }

    private void addOverlayInhTerrainToLOS(HashMap<Hex, Terrain> inhhexes, LOSonOverlays losonoverlays, ASLBoard board) {
        for (Hex inhterrhex : inhhexes.keySet()) {
            final Integer terrtype = inhhexes.get(inhterrhex).getType();
            Rectangle s = inhterrhex.getHexBorder().getBounds();
            for (int i = (int) s.getX(); i < s.getX() + s.getWidth(); i++) {
                for (int j = (int) s.getY(); j < s.getY() + s.getHeight(); j++) {
                    if (losonoverlays.newlosdata.onMap(i, j)) {
                        if (inhterrhex.contains(i, j)) {
                            if (!losonoverlays.newlosdata.getGridTerrain(i, j).isHexsideTerrain()) {
                                losonoverlays.newlosdata.setGridTerrainCode(terrtype, i, j);
                            }
                        }
                    }
                }
            }
            inhterrhex.getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terrtype));
            inhterrhex.resetHexsideTerrain(0);
        }
    }

    public void setBuildingTerrainFromOverlay(LOSonOverlays losonoverlays, Overlay o, Terrain terr) {
        String newterrain = null;
        String overlayname = o.getName().substring(1);
        int passoverlay;
        try {
            passoverlay = Integer.parseInt(overlayname);
        }
        catch (NumberFormatException e) {
            passoverlay = 0;
        }
        switch (passoverlay) {
            case 1:
            case 2:
            case 4:
            case 5:
            case 6:
            case 7:
            case 11:
                newterrain = "Stone Building";
                break;
            case 3:
            case 9:
            case 26:
            case 28:
            case 29:
                newterrain = "Wooden Building";
                break;
            case 12:
            case 17:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 27:
            case 30:
            case 39:
                newterrain = "Stone Building, 1 Level";
                break;
            case 13:
            case 41:
                newterrain = "Wooden Building, 1 Level";
                break;
            case 8:
            case 16:
            case 38:
            case 40:
                newterrain = "Stone Building, 2 Level";
                break;
            case 10:
                newterrain = "Wooden Building, 2 Level";
                break;
            //case -99:
            //    newterrain = "Stone Building, 3 Level";
            //    break;
            case 14:
            case 15:
            case 18:
            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 37:
                newterrain = "Multiple Types";
                break;
            case 0:
            default:
                newterrain = terr.getName();
        }
        if (newterrain == "Multiple Types") {
            addtoFixHexList(losonoverlays);
            newterrain = terr.getName();
        }
        int terrainCode = losonoverlays.newlosdata.getTerrain(newterrain).getType();
        losonoverlays.newlosdata.setGridTerrainCode(terrainCode, losonoverlays.overpositionx, losonoverlays.overpositiony);

    }
    private void addtoFixHexList(LOSonOverlays losonoverlays) {
        Hex hextoadd = losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony);
        if (!hexestofixlist.contains(hextoadd)) {
            hexestofixlist.add(hextoadd);
        }
    }

    private void addOverlayHexElevationToLOS(LinkedList<VASL.LOS.Map.Hex> elevhexes,  LOSonOverlays losonoverlays) {
        //elevhexes contains every hex covered by the overlay need to test them all to Depression Terrain settings
        for (Hex elevhex : elevhexes) {
            boolean existingdepression = false;
            int maxelev = findinHex(elevhex, losonoverlays, existingdepression);
            // if here then no depression terrain found on overlay; so reset hex values
            if (existingdepression ) {
                elevhex.getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain("Open Ground"));
                elevhex.setDepressionTerrain(losonoverlays.newlosdata.getTerrain("Open Ground"));
            }
            elevhex.setBaseLevelofHex(maxelev);
            //need to set the elevation of the centerLocation point in the ElevationGrid
            losonoverlays.newlosdata.setGridElevation(maxelev,  (int) elevhex.getCenterLocation().getLOSPoint().getX(), (int) elevhex.getCenterLocation().getLOSPoint().getY());
        }
    }
    // used to test of Depression pixel is surrounded by other depression pixels - handle hexside pixels problem
    private boolean testAgainForDepression(LOSonOverlays losonoverlays, int i, int j) {
        for (int x = i - 1; x < (i + 2); x++) {
            for (int y = j - 1; y < (j + 2); y++) {
                if (!losonoverlays.newlosdata.onMap(x, y)) {return false;}
                Hex testhex = losonoverlays.newlosdata.gridToHex(i, j);
                if(!testhex.contains(x, y)) {return false;}
                if (!losonoverlays.newlosdata.getGridTerrain(x, y).isDepression()){
                    return false;
                };
            }
        }
        return true;
    }

    private int findinHex(Hex elevhex, LOSonOverlays losonoverlays, boolean existingdepression) {
        Rectangle s = elevhex.getHexBorder().getBounds();
        int maxelev = -4;
        for (int i = (int) s.getX(); i < s.getX() + s.getWidth(); i++) {
            for (int j = (int) s.getY(); j < s.getY() + s.getHeight(); j++) {
                if (losonoverlays.newlosdata.onMap(i, j)) {
                    if (elevhex.contains(i, j)) {
                        //ToDo this is a hack to handle depression overlays; needs a better solution
                        boolean isDepression = losonoverlays.newlosdata.getGridTerrain(i, j).isDepression();
                        if (isDepression && testAgainForDepression(losonoverlays, i, j)) {
                            existingdepression = true;
                            return -1;

                        } else {
                            maxelev = losonoverlays.newlosdata.getGridElevation(i, j) > maxelev ? losonoverlays.newlosdata.getGridElevation(i, j) : maxelev;
                            if (!(elevhex.getCenterLocation().getDepressionTerrain() == null)) {
                                existingdepression = (elevhex.getCenterLocation().getDepressionTerrain().isDepression());
                            }
                        }
                    }
                }
            }
        }
        return maxelev;
    }
    //set terrain type for center location or hexside location (if hexside terrain)
    private void setOverlayTerrainType(LOSonOverlays losonoverlays, Terrain terr, String overlaytype) {
        // handle center and hexside locations
        Hex inhex = losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony);
        if (inhex.getNearestLocation(losonoverlays.overpositionx, losonoverlays.overpositiony).isCenterLocation() && !overlaytype.contains("NoRoads") &&
                !terr.isCliff() && !terr.isHexsideTerrain()) {
            if (!inhex.getCenterLocation().getTerrain().getName().contains("Deir") && !inhex.getCenterLocation().getTerrain().getName().contains("Sand Dune, Low")) {
                inhex.getCenterLocation().setTerrain(terr);
            }
            if (terr.isDepression()) {inhex.getCenterLocation().setDepressionTerrain(terr);}
        } else if (terr != null && terr.isHexsideTerrain()) {
            int hexside = inhex.getLocationHexside(inhex.getNearestLocation(losonoverlays.overpositionx, losonoverlays.overpositiony));
            Point hexsidecenter = inhex.getHexsideLocation(hexside).getEdgeCenterPoint();
            //only set hexside terrain for hex and hexside location if within 10 pixels of hexside centre - avoids mistaken hexsides
            if (Math.abs(losonoverlays.overpositionx - hexsidecenter.x) < 10 && Math.abs(losonoverlays.overpositiony - hexsidecenter.y) < 10) {
                inhex.setHexsideTerrain(hexside, terr);
                inhex.setHexsideLocationTerrain(hexside, terr);
            }
        }
        else if (terr != null) {
            // need to remove existing hexside terrain if covered by other terrain
            int hexside = inhex.getLocationHexside(inhex.getNearestLocation(losonoverlays.overpositionx, losonoverlays.overpositiony));
            if (hexside == -1 ) {return;}  // not on hexside
            Point hexsidecenter = inhex.getHexsideLocation(hexside).getEdgeCenterPoint();
            if (inhex.getHexsideTerrain(hexside) != null) {
                //only set hexside terrain for hex and hexside location if within 10 pixels of hexside centre - avoids mistaken hexsides
                if (Math.abs(losonoverlays.overpositionx - hexsidecenter.x) < 10 && Math.abs(losonoverlays.overpositiony - hexsidecenter.y) < 10) {
                    inhex.setHexsideTerrain(hexside, null);
                    inhex.setHexsideLocationTerrain(hexside, null);
                    final Hex adjhex = losonoverlays.newlosdata.getAdjacentHex(inhex, hexside);
                    if (adjhex != null) {
                        adjhex.setHexsideTerrain(Hex.getOppositeHexside(hexside), terr);
                        adjhex.setHexsideLocationTerrain(Hex.getOppositeHexside(hexside), terr);
                    }
                }
            }
        }
    }

    private Terrain  getOverlayTerrainfromColor(Color color, LOSonOverlays losonoverlays) {
        Terrain terr = null;
        int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
        if (terrint >= 0) {
            return losonoverlays.newlosdata.getTerrain(terrint);
        } else {
            while (terr == null) {  // handles cases where pixel color does not match any color from ShardBoardMetaData.xml
                color = getOverlayNearestColor(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
                if (color == null ) { //transparent pixel
                    terr = losonoverlays.newlosdata.getGridTerrain(losonoverlays.overpositionx, losonoverlays.overpositiony);
                } else if (color.equals(Color.white)) {
                    terr = losonoverlays.newlosdata.getTerrain(losonoverlays.board.getVASLBoardArchive().getTerrainForVASLColor("L0Winter"));
                } else if( color.equals(Color.BLACK)) {
                    terr = losonoverlays.newlosdata.getGridTerrain(losonoverlays.overpositionx, losonoverlays.overpositiony);
                } else {
                    terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                    if (terrint == -1){
                        // exception handling: I have tried this before and it caused bugs
                        terrint = 0; //OG
                    }
                    terr = losonoverlays.newlosdata.getTerrain(terrint);
                }
            }
        }
        return terr;
    }

    private Integer getOverlayElevationfromColor(LOSonOverlays losonoverlays, Color color) {
        int elevint = losonoverlays.board.getVASLBoardArchive().getElevationForColor(color);
        if (elevint == BoardMetadata.NO_ELEVATION) {
            Color newcolor = getOverlayNearestColor(losonoverlays, losonoverlays.overpositionx, losonoverlays.overpositiony);
            if (newcolor == null) { //transparent pixel
                elevint = losonoverlays.newlosdata.getGridElevation(losonoverlays.overpositionx, losonoverlays.overpositiony);
            } else if (newcolor.equals(Color.white)) {
                elevint = 0;
            } else if( newcolor.equals(Color.BLACK)) {
                // do nothing - BLACK returned when no color found; use board elevation
            } else {
                elevint = losonoverlays.board.getVASLBoardArchive().getElevationForColor(newcolor);
            }
        }
        return elevint;
    }

    private Color getOverlayNearestColor(LOSonOverlays losonoverlays, int newovrx, int newovry) {
        int c = 0;
        int a = 2;
        Color color = Color.BLACK;
        //ToDo fix use of int values of c - this need to be a method to handle all the non-terrain colors on the map
        while (color.equals(Color.BLACK) || isOverlayBoardNumColor(color, losonoverlays) || color.equals(getRGBColor(-5261152)) ||
                color.equals(getRGBColor(-262915)) || color.equals(getRGBColor(-259)) || color.equals(getRGBColor(-246)) ||
                color.equals(getRGBColor(-16776960))) {  //-5261152 = 175,184,160 - SnowHexDots2 -259 = 255, 254, 253 - some overlay center dots -246 = 255, 255, 10 - yellow Hill Num
                // -1677690 = 0,1,0 - manholes
            // point must be (a) on map (b) on overlay (c) not transparent
            if (losonoverlays.newlosdata.onMap(newovrx + a, newovry + a) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx + (a - 1), losonoverlays.currenty + a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx + (a - 1), losonoverlays.currenty + a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx + (a - 1), losonoverlays.currenty + a);
            } else if ((losonoverlays.newlosdata.onMap(newovrx + a, newovry - a)) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx + (a - 1), losonoverlays.currenty - a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx + (a - 1), losonoverlays.currenty - a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx + (a - 1), losonoverlays.currenty - a);
            } else if ((losonoverlays.newlosdata.onMap(newovrx - a, newovry + a)) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx - (a - 1), losonoverlays.currenty + a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx - (a - 1), losonoverlays.currenty + a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx - (a - 1), losonoverlays.currenty + a);
            } else if ((losonoverlays.newlosdata.onMap(newovrx - a, newovry - a)) && (pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx - (a - 1), losonoverlays.currenty - a) && (!((losonoverlays.bi.getRGB(losonoverlays.currentx - (a - 1), losonoverlays.currenty - a) >> 24) == 0X00)))) {
                c = losonoverlays.bi.getRGB(losonoverlays.currentx - (a - 1), losonoverlays.currenty - a);
            } else {
                //
            }
            if (c != 0) {
                color = getRGBColor(c);
            }
            a += 1;
            if (a > 6) {
                break;
            }
        }
        return color;
    }

    // used to test if transparent pixel is part of center dot (oftent the case on overlays)
    private Boolean isCenterDot(LOSonOverlays losonoverlays){
        Hex inhex = losonoverlays.newlosdata.gridToHex(losonoverlays.overpositionx, losonoverlays.overpositiony);
        Point centerpoint = inhex.getHexCenter();
        double left = centerpoint.getX() -3;
        double right = centerpoint.getX() + 3;
        double top = centerpoint.getY() -3;
        double bottom = centerpoint.getY() + 3;
        if (left < losonoverlays.overpositionx && losonoverlays.overpositionx < right &&
            top < losonoverlays.overpositiony && losonoverlays.overpositiony < bottom){
            // is center dot - not an exact test but any transparent pixel this close to the center dot is going to be part of it
            return true;
        }
        return false;
    }
    private Boolean pixelOnTransparentOverlayBorder(LOSonOverlays losonoverlays) {
        int c = 0, b = 0, a = 3;
        if (losonoverlays.currentx == 0 || losonoverlays.currentx == losonoverlays.bi.getWidth() - 1) {
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx, losonoverlays.overpositiony + a)) {
                if (losonoverlays.currenty + a > losonoverlays.bi.getHeight() - 1) {
                    a = -3;
                }  //need to ensure testing with pixel on overlay
                c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty + a);
                return (c >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx, losonoverlays.overpositiony - a)) {
                if (losonoverlays.currenty - a < 0) {
                    a = -3;
                }  //need to ensure testing with pixel on overlay
                b = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty - a);
                return (b >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            return true; //transparent pixel
        } else if (losonoverlays.currenty == 0 || losonoverlays.currenty == losonoverlays.bi.getHeight() - 1) {
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx + a, losonoverlays.overpositiony)) {
                if (losonoverlays.currentx + a > losonoverlays.bi.getWidth() - 1) {
                    a = -3;
                }  //need to ensure testing with pixel on overlay
                c = losonoverlays.bi.getRGB(losonoverlays.currentx + a, losonoverlays.currenty);
                return (c >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            if (losonoverlays.newlosdata.onMap(losonoverlays.overpositionx - a, losonoverlays.overpositiony)) {
                if (losonoverlays.currentx - a < 0) {
                    a = -3;
                }  //need to ensure testing with pixel on overlay
                b = losonoverlays.bi.getRGB(losonoverlays.currentx - a, losonoverlays.currenty);
                return (b >> 24) != 0x00 ? false : true;  // not a transparent pixel
            }
            return true; //transparent pixel
        }
        return false;  // not on border
    }

    private Terrain setTerrainForTransparentPixel (LOSonOverlays losonoverlays){
        Terrain transterrain = null;
        // (1) if pixel is on the overlay edge and (2) if so are pixels 2 away also transparent
        // in those conditions, skip actions
        if (!pixelOnTransparentOverlayBorder(losonoverlays)) {
            int j = 0; int k = 0; int c = 0;
            while ((c >> 24) == 0x00 && j <= 6) {
                j += 2;
                k += 2;
                if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + j, losonoverlays.currenty + k) && pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx + j, losonoverlays.currenty + k)) {
                    c = losonoverlays.bi.getRGB(losonoverlays.currentx + j, losonoverlays.currenty + k);
                } else if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + j, losonoverlays.currenty - k) && pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx + j, losonoverlays.currenty - k)) {
                    c = losonoverlays.bi.getRGB(losonoverlays.currentx + j, losonoverlays.currenty - k);
                } else if (losonoverlays.newlosdata.onMap(losonoverlays.currentx - j, losonoverlays.currenty + k) && pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx - j, losonoverlays.currenty + k)) {
                    c = losonoverlays.bi.getRGB(losonoverlays.currentx - j, losonoverlays.currenty + k);
                } else if (losonoverlays.newlosdata.onMap(losonoverlays.currentx - j, losonoverlays.currenty - k) && pointIsOnOverlay(losonoverlays.bi, losonoverlays.currentx - j, losonoverlays.currenty - k)) {
                    c = losonoverlays.bi.getRGB(losonoverlays.currentx - j, losonoverlays.currenty - k);
                } else {
                    break;
                }
                if ((c >> 24) != 0x00) {
                    final Color color = getRGBColor(c);
                    transterrain = getOverlayTerrainfromColor(color, losonoverlays);
                }
            }
        }
        return transterrain;
    }
    private boolean pointIsOnOverlay(BufferedImage bi, int usex, int usey) {
        return usex >= 0 && usex < bi.getWidth() && usey >= 0 && usey < bi.getHeight();
    }

    //add Hex to collections of inherent hexes and building hexes on the overlay
    private void  addHextoOverlayInhandBldgMaps(String terraintype, Terrain terr, LOSonOverlays losonoverlays, HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> inhhexes, HashMap<VASL.LOS.Map.Hex, VASL.LOS.Map.Terrain> bdghexes) {
        if (terr != null) {
            if (terr.isInherentTerrain() ||
                    (terraintype == "Steppe" && (terr.getName().equals("Brush") || terr.getName().equals("Woods"))) ||
                    (terraintype == "Broken" && terr.getName().equals("Brush")) ||
                    (terraintype == "Bamboo" && (terr.getName().equals("Brush")))) {
                Hex testhex = losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony);
                // if hex is on this list then need to remove inherent not add it
                // this is a fix to deal with hexside and hex center pixels which are transparent on overlay but contain terrain on map image
                // that must be removed
                if (!losonoverlays.inherentTerrainHexesToCheckList.contains(testhex)) {
                    if (!inhhexes.containsKey(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony))) {
                        //hack - ensure that the pixel is not close to a hexside as VASL geometry can put it in an adjacent hex
                        final Point hexcenter = losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony).getHexCenter();
                        final Double d = Math.sqrt(((Math.pow(hexcenter.x - losonoverlays.overpositionx, 2) + (Math.pow(hexcenter.y - losonoverlays.overpositiony, 2)))));
                        if (d < 25) {
                            inhhexes.put(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony), terr);
                            doNonInherentToInherentFix(terraintype, terr, losonoverlays);
                        }
                    }
                }
                else {
                    if (inhhexes.containsKey(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony))) {
                        inhhexes.remove(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony));
                    }
                }

            } else if (terr.isBuilding()) {
                // ToDo no longer needed due to change to Hex code
                /*if (!terr.getName().equals("Stone Building") && !terr.getName().equals("Wooden Building") && !terr.getName().contains("Rowhouse Wall")) {
                    if (!bdghexes.containsKey(losonoverlays.newlosdata.gridToHex((int) losonoverlays.currentx, (int) losonoverlays.currenty))) {
                        bdghexes.put(losonoverlays.newlosdata.gridToHex((int) losonoverlays.currentx, (int) losonoverlays.currenty), terr);
                    }
                }*/
            }
        }
    }

    //add Hex to collections of hexes on the overlay - need to test every hex for elevation changes
    private void  addHextoOverlayElevationMaps(Terrain terr, LOSonOverlays losonoverlays, LinkedList<VASL.LOS.Map.Hex> elevhexes) {
        if (terr != null) {
            if (!elevhexes.contains(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony))) {
                elevhexes.add(losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony));
            }
        }
    }

    private boolean isOverlayBoardNumColor(Color testcolor, LOSonOverlays losonoverlays) {
        if (testcolor == null) {
            return false;
        }
        final String colorName = losonoverlays.board.getVASLBoardArchive().getVASLColorName(testcolor);
        return "WhiteHexNumbers".equals(colorName) || "WinterBlackHexNumbers".equals(colorName) ||
                "MudBoardNum".equals(colorName) || "DTO_BoardNum".equals(colorName) ||
                "AD_WinterBlackHexNumbers".equals(colorName);
    }

    private Terrain fixnullterrain(LOSonOverlays passlosonoverlays, int overpositionx, int overpositiony) {
    // use OG with elevint from existing losdata; this is a hack when can't find terrain
    int passelev = passlosonoverlays.newlosdata.getGridElevation(overpositionx, overpositiony);
    String colorname = useDefaultTerrain(passelev);
    return passlosonoverlays.newlosdata.getTerrain(passlosonoverlays.board.getVASLBoardArchive().getTerrainForVASLColor(colorname));
}
    private String useDefaultTerrain (int passelev) {
        switch (passelev) {
            case -2:
                return "Level_2";
            case -1:
                return "Level_1";
            case 0:
                return "Level0";
            case 1:
                return "Level1";
            case 2:
                return "Level2";
            case 3:
                return "Level3";
            case 4:
                return "Level4";
            case 5:
                return "Level5";
            case 6:
                return "Level6";
            default:
                return "Level0";

        }

    }

    // if overlayname returns "" from this method then los checking won't work with the overlay
    // when adding items here also add them to VASLThread.initializeMap
    private String getOverlayTerrainType(Overlay o){
        final String overlayname = o.getName();
        if (overlayname.contains("Steppe")) {
            return "Steppe";
        }
        if (overlayname.contains("SSO")) {
            return "SSO";
        }
        if (overlayname.contains("Bocage")) {
            return "Bocage";
        }
        if(overlayname.contains("BrokenTerrain")) {
            return "Broken";
        }
        if (overlayname.contains("PalmTrees")) {
            return "Palm Trees";
        }
        if (overlayname.contains("Bamboo")) {
            return "Bamboo";
        }
        if (overlayname.contains("elrr")){
            return "Elevated Railroad";
        }
        if (overlayname.contains("surr")){
            return "Sunken Railroad";
        }
        if (overlayname.contains("rr")){
            return "Railroad";
        }
        if (overlayname.contains("NoRoads")){
            return "NoRoads";
        }
        if (overlayname.contains("rv")){
            return "River";
        }
        if (overlayname.contains("sw")){
            return "Swamp";
        }
        if (overlayname.contains("be")){
            return "Beach";
        }
        if (overlayname.contains("b")){
            return "Brush";
        }
        if (overlayname.contains("hd")){
            return "Hedges";
        }
        if (overlayname.contains("sh")){
            return "Shellholes";
        }
        else if (overlayname.contains("og") || overlayname.equals("dx1") || overlayname.equals("dx5")){
            return "Open Ground";
        }
        else if (overlayname.contains("m")){
            return "Marsh";
        }
        else if (overlayname.contains("g")){
            return "Grain";
        }
        else if (overlayname.contains("p")){
            return "Water";
        }
        else if (overlayname.contains("ow")){
            return "Woods";
        }
        else if (overlayname.contains("oc")){
            return "Ocean";
        }
        else if (overlayname.contains("o") || overlayname.equals("dx3") || overlayname.equals("dx7")){
            return "Orchard";
        }
        else if (overlayname.contains("wd") || overlayname.equals("dx2") || overlayname.equals("dx4")){
            return "Woods";
        }
        else if (overlayname.contains("x") && !overlayname.contains("dx")) {
            return "Building";
        }
        else if (overlayname.contains("hi")) {
            return "Hill";
        }
        else if (overlayname.contains("st")) {
            return "Stream";
        }
        else if (overlayname.contains("sr")) {
            return "Stone Rubble";
        }
        else if (overlayname.contains("wr")) {
            return "Wooden Rubble";
        }
        else if (overlayname.contains("wt")) {
            return "Water";
        }
        else if (overlayname.contains("v")) {
            return "Vineyard";
        }
        else if (overlayname.contains("w")) {
            return " ";
        }
        else if (overlayname.contains("rp")) {
            return "Rice Paddy";
        }
        else if (overlayname.contains("ef")) {
            return "Effluent";
        }
        else if (overlayname.contains("LightGrain")) {
            return "Light Grain";
        }
        else {
            return "";
        }

    }

    //ToDo this would not seem necessary any more due to Sept 14 changes
    // integrate into main method and delete
    private void updateTerrainElevationGridsforOverlayInherentTerrain(LOSonOverlays losonoverlays, String terraintype) {
        //Hex temphex = null; Hex newhex;
        //Hex previoushex = null;

        // exclude terraintype changes with no LOS impact
        if (terraintype == "Palm Trees"){ return;}  // no LOS data change required - are there other terrain types that should be excluded
            for (losonoverlays.currentx = 0; losonoverlays.currentx < losonoverlays.bi.getWidth(); losonoverlays.currentx++) {
                for (losonoverlays.currenty = 0; losonoverlays.currenty < losonoverlays.bi.getHeight(); losonoverlays.currenty++) {
                    if (losonoverlays.newlosdata.onMap(losonoverlays.currentx + losonoverlays.ovrXstart - (int) losonoverlays.board.getCropBounds().getX(), losonoverlays.currenty + losonoverlays.ovrYstart - (int) losonoverlays.board.getCropBounds().getY())) {
                        losonoverlays.overpositionx = losonoverlays.currentx + (int) losonoverlays.ovrXstart - (int) losonoverlays.board.getCropBounds().getX();
                        losonoverlays.overpositiony = losonoverlays.currenty + (int) losonoverlays.ovrYstart - (int) losonoverlays.board.getCropBounds().getY();
                        //Hex hextouse = losonoverlays.newlosdata.gridToHex(losonoverlays.currentx + losonoverlays.ovrXstart - (int) losonoverlays.board.getCropBounds().getX(), losonoverlays.currenty + losonoverlays.ovrYstart - (int) losonoverlays.board.getCropBounds().getY());
                        //if (!hextouse.equals(previoushex)) {
                            int c = losonoverlays.bi.getRGB(losonoverlays.currentx, losonoverlays.currenty);
                            if ((c >> 24) != 0x00) { // not a transparent pixel
                                String terraintouse = "Open Ground";
                                Terrain terr = null;
                                //Retrieving the R G B values
                                final Color color = getRGBColor(c);
                                final int terrint = losonoverlays.board.getVASLBoardArchive().getTerrainForColor(color);
                                if (terrint >= 0) {
                                    terr = losonoverlays.newlosdata.getTerrain(terrint);
                                    if (terr.getName().equals(terraintype)) {
                                        terraintouse = terraintype;
                                    }
                                }

                                if (!terraintouse.equals("Open Ground") && terr != null) {  // terrain is inherent terrain

                                    //hextouse.getCenterLocation().setTerrain(losonoverlays.newlosdata.getTerrain(terraintouse));
                                    //hextouse.setOverlayBorder();
                                    losonoverlays.newlosdata.setGridTerrainCode(terr.getType(), losonoverlays.overpositionx, losonoverlays.overpositiony);
                                    /*final LOSDataEditor loseditor = new LOSDataEditor(losonoverlays.newlosdata);
                                    loseditor.setGridTerrain(hextouse.getHexBorder(), terr);
                                    for (int z = 0; z < 6; z++) {
                                        hextouse.setHexsideTerrain(z, terr);
                                        hextouse.setHexsideLocationTerrain(z, terr);
                                        final Hex adjhex = losonoverlays.newlosdata.getAdjacentHex(hextouse, z);
                                        if (adjhex != null) {
                                            adjhex.setHexsideTerrain(Hex.getOppositeHexside(z), terr);
                                            adjhex.setHexsideLocationTerrain(Hex.getOppositeHexside(z), terr);
                                        }
                                    }*/
                        //            previoushex = hextouse;
                                }
                            }
                        //}
                    }
                }
            }
        //}
    }

    private void doNonInherentToInherentFix(String terraintype, Terrain terr, LOSonOverlays losonoverlays){
        Point pointtoset = null;
        Hex setterrainonhexside = losonoverlays.newlosdata.gridToHex((int) losonoverlays.overpositionx, (int) losonoverlays.overpositiony);
        for (int z = 0; z < 6; z++) {
            pointtoset = setterrainonhexside.getHexsideLocation(z).getEdgeCenterPoint();
            if (z == 0){pointtoset.y -= 1;}
            if (z == 3){pointtoset.y += 1;}
            if(losonoverlays.newlosdata.onMap(pointtoset.x, pointtoset.y)) {
                losonoverlays.newlosdata.setGridTerrainCode(terr.getType(), pointtoset.x, pointtoset.y);
            }
        }
    }
    // enables terrain transformations to be applied to overlay losdata
    // this should be a generic approach but starting with specific transforms

    private Terrain resetterraintypefortransform(String terrainchanges, Terrain terr){
        if (terrainchanges.contains("Bamboo") && terr.getName().equals("Brush")){
            return sharedBoardMetadata.getTerrainTypes().get("Bamboo");
        }
        return terr;
    }
    private String resetfortransform (String terraintype, LOSonOverlays losonoverlays){
        if (terraintype.equals("Brush") && losonoverlays.board.getTerrainChanges().contains("Bamboo")){
            return "Bamboo";
        }
        return terraintype;
    }

    /**
     * Sets status of LOS engine to legacy mode
     */
    private void setLegacyMode() {
        legacyMode = true;
        VASLMap = null;
    }
    /**
     * @return the VASL map
     */
    public VASL.LOS.Map.Map getVASLMap() {
        return VASLMap;
    }

    /**
     * @return the shared board metadata
     */
    public static SharedBoardMetadata getSharedBoardMetadata() {
        return sharedBoardMetadata;
    }

    /**
     * @return the counter metadata
     */
    public static CounterMetadataFile getCounterMetadata() {
        return counterMetadata;
    }

    /**
     * @return true if the map is in legacy mode (i.e. pre-6.0)
     */
    public boolean isLegacyMode(){
        return legacyMode;
    }

    /**
     * Log a string to the VASSAL error log
     * @param error the error string
     */
    private void logError(String error) {
        logger.info(error);
    }

    /**
     * Log an exception to the VASSAL error log
     * @param error the exception
     */
    private void logException(Throwable error) {
        logger.info("", error);
    }

    public BufferedImage getImgMapIcon(Point pt, double size, double os_scale) {
        BufferedImage img = new BufferedImage((int)size, (int)size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = img.createGraphics();

        double dzoom = 0.0;
        Rectangle rect = null;

        for (Board b: getBoards()) {
            if (rect == null) {
                final double mag = b.getMagnification();
                dzoom = os_scale / mag;

                rect = new Rectangle(
                  (int)((pt.x * os_scale - size/2) / mag),
                  (int)((pt.y * os_scale - size/2) / mag),
                  (int)(size / mag),
                  (int)(size / mag)
                );

                g2d.translate(-rect.x, -rect.y);
            }

            b.drawRegion(g2d, getLocation(b, dzoom), rect, dzoom, null);
        }

        drawPiecesNonStackableInRegion(g2d, rect, dzoom);

        g2d.dispose();
        return img;
    }

    protected void drawPiecesNonStackableInRegion(Graphics g, Rectangle visibleRect, double dZoom)
    {
        Graphics2D g2d = (Graphics2D) g;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

        GamePiece[] stack = pieces.getPieces();

        for (int i = 0; i < stack.length; ++i)
        {
            Point pt = stack[i].getPosition();

            if (stack[i].getClass() != Stack.class)
            {
                if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK)))
                {
                    //JY
                    //stack[i].draw(g, (int) (pt.x * dZoom), (int) (pt.y * dZoom), null, dZoom);
                    double pZoom = PieceScalerBoardZoom(stack[i]);
                    stack[i].draw(g, (int) (pt.x * dZoom), (int) (pt.y * dZoom), null, dZoom*pZoom);
                    //JY
                }
            }
        }

        g2d.setComposite(oldComposite);
    }

    public void setShowMapLevel(ShowMapLevel showmaplevel) {
      this.showmaplevel = showmaplevel;
    }
    
    @Override
    public boolean isPiecesVisible() {
        return pieceOpacity != 0;
    }

    @Override
    public void drawPiecesInRegion(Graphics g,
                                   Rectangle visibleRect,
                                   Component c) {

        Graphics2D g2d = (Graphics2D) g;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

        final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
        final double dzoom = getZoom() * os_scale;

        GamePiece[] stack = pieces.getPieces();
        // Create a java.util.map indication how many stacks are at each point on the map
        // This is used to determine if a HIP stack has enemy game pieces at the same location
        // If it does, then it will be drawn offset to be visible to owner
        java.util.Map<Point, Integer> pieceMap = new HashMap<Point, Integer>();
        for (int i = 0; i < stack.length; ++i) {
            // increment the count of pieces at this point
            String name = stack[i].getName();
            Stack s = null;
            if (stack[i] instanceof Stack) {
                s = (Stack) stack[i];
            } else {
                continue;
            }
            int x = s.getPieceCount();
            if (x == 0 || name.equals(""))
                //empty stack, ignore
                continue;
            Point pt = stack[i].getPosition();
            Integer count = pieceMap.get(pt);
            if (count == null) {
                count = 0;
            }
            pieceMap.put(pt, count + 1);
        }

        for (int i = 0; i < stack.length; ++i) {
            Point pt = mapToDrawing(stack[i].getPosition(), os_scale);
            double pZoom = PieceScalerBoardZoom(stack[i]);
            if (stack[i].getClass() == Stack.class) {
                // If a unit is HIP and there are more than one stack in that location,
                // we offset the hidden units so they are visible to owner
                if (stack[i].getName().contains("HIP") && pieceMap.get(stack[i].getPosition()) != null && pieceMap.get(stack[i].getPosition()) > 1) {
                    // Create an offset point for the hidden stack
                    Point hiddenpoint = new Point(pt.x - 15, pt.y - 15);
                    getStackMetrics().draw((Stack) stack[i], hiddenpoint, g, this, dzoom*pZoom, visibleRect);
                }
                else if (showmaplevel == ShowMapLevel.ShowAll) {
                    getStackMetrics().draw((Stack) stack[i], pt, g, this, dzoom*pZoom, visibleRect);
                }
            }
            else {
                if (showmaplevel == ShowMapLevel.ShowAll  || (stack[i].getProperty("overlay") != null && showmaplevel == ShowMapLevel.ShowMapOnly)) {// always show overlays
                    stack[i].draw(g, pt.x, pt.y, c, dzoom*pZoom);
                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                        highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom*pZoom);
                    }
                }
                else if (showmaplevel == ShowMapLevel.ShowMapAndOverlay) {
                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK))) {
                        stack[i].draw(g, pt.x, pt.y, c, dzoom*pZoom);
                        if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                            highlighter.draw(stack[i], g, pt.x, pt.y, c, dzoom*pZoom);
                        }
                    }
                }
            }
        /*
        // draw bounding box for debugging
        final Rectangle bb = stack[i].boundingBox();
        g.drawRect(pt.x + bb.x, pt.y + bb.y, bb.width, bb.height);
        */
        }
        g2d.setComposite(oldComposite);
    }
    @Override
    public void drawPieces(Graphics g, int xOffset, int yOffset) {

        Graphics2D g2d = (Graphics2D) g;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceOpacity));

        final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
        final double dzoom = getZoom() * os_scale;

        GamePiece[] stack = pieces.getPieces();

        for (int i = 0; i < stack.length; ++i)         {
            double pZoom = PieceScalerBoardZoom(stack[i]);
            if (showmaplevel == ShowMapLevel.ShowAll || (stack[i].getProperty("overlay") != null && showmaplevel == ShowMapLevel.ShowMapOnly)) {// always show overlays
                Point pt = mapToDrawing(stack[i].getPosition(), os_scale);
                stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom*pZoom);

                if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                    highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom * pZoom);
                }
            }
            else if (showmaplevel == ShowMapLevel.ShowMapAndOverlay) {
                if (stack[i].getClass() != Stack.class) {
                    if (Boolean.TRUE.equals(stack[i].getProperty(Properties.NO_STACK))) {
                        Point pt = mapToDrawing(stack[i].getPosition(), os_scale);
                        stack[i].draw(g, pt.x + xOffset, pt.y + yOffset, theMap, dzoom*pZoom);
                        if (Boolean.TRUE.equals(stack[i].getProperty(Properties.SELECTED))) {
                            highlighter.draw(stack[i], g, pt.x - xOffset, pt.y - yOffset, theMap, dzoom * pZoom);
                        }
                    }
                }
            }
        }
        g2d.setComposite(oldComposite);
    }
    private Color getRGBColor(int c){
        final int red = (c & 0x00ff0000) >> 16;
        final int green = (c & 0x0000ff00) >> 8;
        final int blue = c & 0x000000ff;
        return new Color(red, green, blue);
    }
  
    public enum ShowMapLevel {
      ShowAll,
      ShowMapAndOverlay,
      ShowMapOnly        
    }

    public void setStackMetrics(ASLStackMetrics sm) {
        this.ASLmetrics = sm;
    }
    @Override
    public ASLStackMetrics getStackMetrics() {
        if (this.ASLmetrics == null) {
            this.ASLmetrics = new ASLStackMetrics();
            this.ASLmetrics.build((Element)null);
            this.add(this.ASLmetrics);
            this.ASLmetrics.addTo(this);
        }
        return this.ASLmetrics;
    }

    public static double getbZoom() {
        return bZoom;
    }
    public void setbZoom (double z) {
        if (bZoom != 0.0D) {
            oldbZoom = bZoom;
        }
        else {
            oldbZoom = 1.0D;
        }
        bZoom = z;
    }

    private void findOverlays() {
        //All pieces that should scale with the map, not the counters
        //Mostly overlays, but some others
        String[] ovlPalettes = {"Draggable Overlays", "Deluxe Draggable", "Terrain Overlays", "Overlays (Large)"};
        java.util.List<PieceWindow> pwList = GameModule.getGameModule().getAllDescendantComponentsOf(PieceWindow.class);
        for (PieceWindow pw: pwList) {
            String pwName = pw.getAttributeValueString("name");
            if (Arrays.asList(ovlPalettes).contains(pwName)) {
                java.util.List<PieceSlot> psList = pw.getAllDescendantComponentsOf(PieceSlot.class);
                for (PieceSlot ps: psList) {
                    pieceslotgpidlist.add(ps.getGpId());
                }
            }
        }
        String[] ovlPanels = {"Phase Track", "Turn Markers"};
        java.util.List<PanelWidget> panList = GameModule.getGameModule().getAllDescendantComponentsOf(PanelWidget.class);
        for (PanelWidget pw: panList) {
            String pwName = pw.getAttributeValueString("entryName");
            if (Arrays.asList(ovlPanels).contains(pwName)) {
                java.util.List<PieceSlot> psList = pw.getAllDescendantComponentsOf(PieceSlot.class);
                for (PieceSlot ps: psList) {
                    pieceslotgpidlist.add(ps.getGpId());
                }
            }
        }
        String[] ovlScrolls = {"< Turn Tracks", "Turn Markers (by Module)"};
        java.util.List<ListWidget> scrList = GameModule.getGameModule().getAllDescendantComponentsOf(ListWidget.class);
        for (ListWidget pw: scrList) {
            String pwName = pw.getAttributeValueString("entryName");
            if (Arrays.asList(ovlScrolls).contains(pwName)) {
                java.util.List<PieceSlot> psList = pw.getAllDescendantComponentsOf(PieceSlot.class);
                for (PieceSlot ps: psList) {
                    pieceslotgpidlist.add(ps.getGpId());
                }
            }
        }
    }

    private void createDeluxeBoardsList() {
        dxAvailBoards = VASL.build.module.map.BoardDataReader.getDeluxeBoardNamesList();
    }

    public double PieceScalerBoardZoom(GamePiece gp) {
        //Test if piece is an overlay or otherwise should scale with the board
        boolean keepAtBoardScale = true;
        if (gp instanceof Stack) {
            Stack s = (Stack) gp;
            for (int i = s.getPieceCount(); i > 0; i--) { //Top down in the stack
                GamePiece sgp = s.getPieceAt(i - 1);
                if (sgp.getProperty(Properties.PIECE_ID) != null){
                    if (!(pieceslotgpidlist.contains(sgp.getProperty(Properties.PIECE_ID).toString()) || (sgp.getProperty(SCALEWITHBOARDZOOM) != null))) {
                        keepAtBoardScale = false;
                    }
                } else {
                    keepAtBoardScale = false;
                }
            }
        }
        else {
            if (gp.getProperty(Properties.PIECE_ID) != null) {
                if (!(pieceslotgpidlist.contains(gp.getProperty(Properties.PIECE_ID).toString()) || (gp.getProperty(SCALEWITHBOARDZOOM) != null))) {
                    keepAtBoardScale = false;
                }
            } else {
                keepAtBoardScale = false;
            }
        }
        return (keepAtBoardScale? 1.0D : 1.0D/getbZoom())*PieceScalerBoardMag(gp);
    }

    public double PieceScalerBoardMag(GamePiece gp) {
        //Look for pieces that should get additional zoom due to board magnification level
        double mag = 1.0;
        for (Board b: getBoards()) {
            mag = b.getMagnification();
        }
        boolean deluxe = false;
        for (Board b: getBoards()) {
            String bdName = b.getName();
            deluxe = dxAvailBoards.contains(bdName);
        }
        if (deluxe) {mag = mag*3.0;}

        double magZoom = mag;
        if (gp instanceof Stack) {
            Stack s = (Stack) gp;
            for (int i = s.getPieceCount(); i > 0; i--) { //Top down in the stack
                GamePiece sgp = s.getPieceAt(i - 1);
                if (!((sgp.getProperty(SCALEWITHBOARDMAG) != null) && (mag != 1.0))) {
                    magZoom = 1.0;
                }
            }
        }
        else {
            if (!((gp.getProperty(SCALEWITHBOARDMAG) != null) && (mag != 1.0))) {
                magZoom = 1.0;
            }
        }
        return magZoom;
    }

    protected class CropValues {
        private final VASLBoard b;
        private String gridconfigWidth;
        private String toplefthexwidth;
        private double hexwidth;  // in pixels
        private double hexheight;
        private boolean nearestFullRow;
        int indexOfCol1 = 0;  //numerical index of map columns (A, B, C, . . .) zero-based
        int indexOfCol2 = 0;
        int valueOfRow1 = 0;  //numerical index of map rows  zero-based
        int valueOfRow2 = 0;
        boolean isbboard;  // "b" board test
        boolean isdwboard;  // "DW" board test

        //Constructor
        protected CropValues(VASLBoard board, String gridconfigWidth, String toplefthexwidth) {
            this.b = board;
            this.hexwidth = b.getHexWidth();
            if (this.hexwidth == 56.3125) {this.hexwidth = 56.25;}  //hack to fix board size issue - fix by updating board archive files
            this.hexheight = b.getHexHeight();
            this.nearestFullRow = b.nearestFullRow;
            this.gridconfigWidth = gridconfigWidth;
            this.toplefthexwidth = toplefthexwidth;
            setvalues();

        }
        //getters
        protected String getgridconfigWidth() {return gridconfigWidth;}
        protected String gettoplefthexwidth() {return toplefthexwidth;}
        protected int getindexOfCol1() {return indexOfCol1;}
        protected int getindexOfCol2() {return indexOfCol2;}
        protected int getvalueOfRow1() {return valueOfRow1;}
        protected int getvalueOfRow2() {return valueOfRow2;}
        protected boolean getnearestFullRow() {return nearestFullRow;}

        // set crop values
        protected void setvalues() {
            isbboard = b.getA1CenterX() == -901 ? true : false;  // "b" board test
            isdwboard = b.getA1CenterY() == -612.75 ? true : false;  // "DW" board test
            String column_names = "abcdefghijklmnopqrstuvwxyz";
            // if values below are empty then cropping saved game
            if (b.getRow1() == null && b.getRow2() == null && b.getCoord1() == null && b.getCoord2() == null){
                int colrowint =0; int coladj = 0;
                // Do cropped to full row test
                // if width not cropped then skip and use default values
                if (b.getCropBounds().getWidth() != -1) {  // width is not cropped
                    if (b.getCropBounds().getX() != 0 && b.getCropBounds().getMaxX() != b.getUncroppedSize().getWidth()) { //both left and right edges are cropped
                        double croptest = b.getCropBounds().getX() % hexwidth;
                        if (26 < croptest && croptest < 30) { // cropped to nearestfullrow
                            b.nearestFullRow = true;
                            gridconfigWidth = "FullHexWidth";
                            toplefthexwidth = "FullHexWidth";
                        } else {  // cropped to half hex width
                            gridconfigWidth = "HalfHexWidth";
                            toplefthexwidth = "HalfHexWidth";
                        }
                    } else if (b.getCropBounds().getX() != 0) {  //left-edge is cropped
                        //only deal with right not cropped as both cropped already dealt with
                        double croptest = b.getCropBounds().getX() % hexwidth;
                        if (-5 > croptest || croptest > 5) { // cropped to nearestfullrow
                            b.nearestFullRow = true;
                            gridconfigWidth = "FullHexWidthRightHalf";
                            toplefthexwidth = "FullHexWidth";
                        } else {  // cropped to half hex width
                            gridconfigWidth = "HalfHexWidth";
                            toplefthexwidth = "HalfHexWidth";
                        }
                    } else if (b.getCropBounds().getMaxX() != b.getUncroppedSize().getWidth()) { // right edge is cropped
                        //only deal with left not cropped as both cropped already dealt with
                        double croptest = b.getCropBounds().getWidth() % hexwidth;
                        if ((-5 > croptest || croptest > 5) && (55 > croptest || croptest > 57)) { // cropped to nearestfullrow
                            b.nearestFullRow = true;
                            gridconfigWidth = "FullHexWidthLeftHalf";
                            toplefthexwidth = "HalfHexWidth";
                        } else {  // cropped to half hex width
                            gridconfigWidth = "HalfHexWidth";
                            toplefthexwidth = "HalfHexWidth";
                        }
                    }
                    if (b.nearestFullRow) {
                        coladj = !gridconfigWidth.contains("LeftHalf") ? (int) (hexwidth / 2) : 0;
                        coladj += !gridconfigWidth.contains("RightHalf") ? (int) (hexwidth / 2) : 0;

                    }
                }
                coladj += 1; //the +1 is a hack to push into the next column when pixel value is just off center
                colrowint = (int) ((b.getCropBounds().getX() + coladj) / hexwidth); // + 1) / hexwidth);  //the +1 is a hack to push into the next column
                String colname = colrowint > 25 ? new StringBuilder().append(column_names.charAt(colrowint - 26)).append(column_names.charAt(colrowint - 26)).toString() : new StringBuilder().append(column_names.charAt(colrowint )).toString();
                b.setRow1(colname);
                indexOfCol1 = colrowint;
                colrowint = b.getWidth() -1;
                if (b.getCropBounds().getMaxX() != -1 && b.getCropBounds().getMaxX() != b.getUncroppedSize().getWidth()) {  //ToDo fix this
                    int colrowintadj = b.nearestFullRow ? 1 : 0;  // hack to adjust num of columns when cropped to fullhex
                    colrowint = (int) ((b.getCropBounds().getMaxX() + coladj) / hexwidth) - colrowintadj;
                }
                colname = colrowint > 25 ? new StringBuilder().append(column_names.charAt(colrowint - 26)).append(column_names.charAt(colrowint - 26)).toString() : new StringBuilder().append(column_names.charAt(colrowint)).toString();
                b.setRow2(colname);
                indexOfCol2 = colrowint;
                colrowint = 0;
                if (b.getCropBounds().getY() > 0) {
                    colrowint = (int) (b.getCropBounds().getY() / hexwidth);
                }
                b.setCoord1(String.valueOf(colrowint));
                valueOfRow1 = colrowint;
                colrowint = b.getHeight() -1;
                if (b.getCropBounds().getMaxY() != -1) {
                    colrowint = (int) (b.getCropBounds().getMaxY() / hexheight);
                }
                b.setCoord2(String.valueOf(colrowint));
                valueOfRow2 = colrowint;
            }
            else {
                // new game - use values entered in Boardpicker
                indexOfCol1 = b.getRow1().length() != 0 ? (b.getRow1().length() == 2 ? column_names.indexOf(b.getRow1().charAt(0)) + 26 : column_names.indexOf(b.getRow1().charAt(0))) : 0;
                indexOfCol2 = b.getRow2().length() != 0 ? (b.getRow2().length() == 2 ? column_names.indexOf(b.getRow2().charAt(0)) + 26 : column_names.indexOf(b.getRow2().charAt(0))) : b.getVASLBoardArchive().getBoardWidth() - 1;
                valueOfRow1 = b.getCoord1().equals("") ? 0 : Integer.parseInt(b.getCoord1());
                valueOfRow2 = b.getCoord2().equals("") ? b.getVASLBoardArchive().getBoardHeight() : Integer.parseInt(b.getCoord2());
            }
            indexOfCol1 = isbboard ? indexOfCol1 - 16 : indexOfCol1;  //adjust col value for "b" boards 1b - 22b to ensure correct hex name
            indexOfCol2 = isbboard ? indexOfCol2 - 16 : indexOfCol2;
            valueOfRow1 = isdwboard ? valueOfRow1 - 10 : valueOfRow1;  //adjust col value for "b" boards 1b - 22b to ensure correct hex name
            valueOfRow2 = isdwboard ? valueOfRow2 - 10 : valueOfRow2;
            b.setstartcropcol(b.getRow1(), indexOfCol1);
            b.setendcropcol(b.getRow2(), indexOfCol2);
            b.setstartcroprow(b.getCoord1(), valueOfRow1);
            b.setendcroprow(b.getCoord2(), valueOfRow2);
        }
    }
  }
