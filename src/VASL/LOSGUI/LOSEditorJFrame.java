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
import VASL.LOS.Map.Map;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;

/**
 * Title:        LOSEditorJFrame.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class LOSEditorJFrame extends JFrame {

    // directories
    private String boardDirectory;

    // combo boxes
    private String[] functionList = {
            "LOS", "Set ground level", "Add terrain", "Add hexside terrain", "Add bridge", "Add road", "Add objects"};
    private JComboBox functionComboBox = new JComboBox(functionList);
    private JComboBox terrainComboBox = new JComboBox();
    private JComboBox brushComboBox = new JComboBox();

    // define the menu
    private JMenuBar menuBar1 = new JMenuBar();
    private JMenu menuFile = new JMenu();
    private JMenuItem menuFileExit = new JMenuItem();
    private JMenu menuHelp = new JMenu();
    private JMenuItem menuHelpAbout = new JMenuItem();
    private JMenuItem menuFileClose = new JMenuItem();
    private JMenuItem menuFileOpen = new JMenuItem();
    private JMenuItem menuFileNew = new JMenuItem();
    private JMenuItem menuFileSave = new JMenuItem();
    private JMenuItem menuFilePrint = new JMenuItem();

    private JMenu menuEdit = new JMenu();
    private JMenuItem menuEditFlip = new JMenuItem();
    private JMenuItem menuEditInsert = new JMenuItem();

    private JToolBar toolBar = new JToolBar();
    private JButton openButton = new JButton();
    private JButton newButton = new JButton();
    private JButton saveButton = new JButton();
    private ImageIcon squareBrushIcon;
    private ImageIcon roundBrushIcon;
    private ImageIcon VASLMapIcon;
    private ImageIcon LOSMapIcon;
    private JLabel statusBar = new JLabel();
    private BorderLayout borderLayout1 = new BorderLayout();
    private JScrollPane jScrollPane1 = new JScrollPane();
    private LOSEditorJComponent losEditorJComponent = new LOSEditorJComponent();

    private JPanel buttonBar = new JPanel();
    private JPanel functionSelector = new JPanel();
    private FlowLayout flowLayout3 = new FlowLayout();
    private JLabel spacer = new JLabel();
    private JButton bridgeButton = new JButton();
    private JButton undoFunctionButton = new JButton();
    private JButton updateMapButton = new JButton();
    private JToggleButton brushShapeButton = new JToggleButton();
    private JToggleButton mapToggleButton = new JToggleButton();
    private JLabel spacer3 = new JLabel();
    private JLabel jLabel1 = new JLabel();
    private JLabel jLabel2 = new JLabel();
    private JLabel jLabel3 = new JLabel();
    private FlowLayout flowLayout1 = new FlowLayout();
    private JLabel spacer4 = new JLabel();
    private JTextField rotationTextField = new JTextField();
    private JButton toArrowButton = new JButton();
    private JComboBox toTerrainComboBox = new JComboBox();
    private JLabel spacer5 = new JLabel();
    private JButton testButton = new JButton();

    //Construct the frame
    public LOSEditorJFrame() {

        enableEvents(AWTEvent.WINDOW_EVENT_MASK);

        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //Component initialization
    private void jbInit() throws Exception {

        // setup the window widgets
        ImageIcon image1 = new ImageIcon(losEditorJComponent.getImage("CASL/images/openFile.gif"));

        //TODO: save should not open a dialog
        this.getContentPane().setLayout(borderLayout1);
        this.setEnabled(true);
        this.setTitle("LOSEditorApp");
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.setText(" ");
        menuFile.setMargin(new Insets(0, 0, 0, 0));
        menuFile.setText("File");
        menuFile.setFont(new java.awt.Font("Dialog", 0, 11));
        menuFileExit.setPreferredSize(new Dimension(100, 20));
        menuFileExit.setMnemonic('0');
        menuFileExit.setText("Exit");
        menuFileExit.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                fileExit();
            }
        });

        menuEdit.setMargin(new Insets(0, 0, 0, 0));
        menuEdit.setText("Edit");
        menuEdit.setFont(new java.awt.Font("Dialog", 0, 11));
        menuEdit.setEnabled(false);

        menuEditFlip.setPreferredSize(new Dimension(100, 20));
        menuEditFlip.setText("Flip");
        menuEditFlip.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                editFlip();
            }
        });

        menuHelp.setMargin(new Insets(0, 0, 0, 0));
        menuHelp.setText("Help");
        menuHelp.setFont(new java.awt.Font("Dialog", 0, 11));
        menuHelpAbout.setText("About");
        menuHelpAbout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                helpAbout_actionPerformed();
            }
        });
        openButton.setIcon(image1);
        openButton.setMargin(new Insets(1, 1, 1, 1));
        openButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                openArchive();
            }
        });
        openButton.setMaximumSize(new Dimension(25, 25));
        openButton.setMinimumSize(new Dimension(25, 25));
        openButton.setPreferredSize(new Dimension(25, 25));
        openButton.setRequestFocusEnabled(false);
        openButton.setToolTipText("Open board");
        newButton.setMargin(new Insets(2, 2, 2, 2));
        newButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                newMap();
            }
        });
        newButton.setMaximumSize(new Dimension(25, 25));
        newButton.setMinimumSize(new Dimension(25, 25));
        newButton.setPreferredSize(new Dimension(25, 25));
        newButton.setRequestFocusEnabled(false);
        newButton.setToolTipText("New LOS data");
        newButton.setIcon(new ImageIcon(losEditorJComponent.getImage("CASL/images/newFile.gif")));
        toolBar.setAlignmentY((float) 0.5);
        toolBar.setFloatable(false);
        losEditorJComponent.setMinimumSize(new Dimension(100, 100));
        losEditorJComponent.setFrame(this);
        LOSEditorApp.writeError("Loading the terrain images... ");

        jScrollPane1.setMinimumSize(new Dimension(100, 100));
        jScrollPane1.setPreferredSize(new Dimension(100, 100));
        saveButton.setToolTipText("Save LOS data");
        saveButton.setMinimumSize(new Dimension(25, 25));
        saveButton.setPreferredSize(new Dimension(25, 25));
        saveButton.setRequestFocusEnabled(false);
        saveButton.setEnabled(false);
        saveButton.setMaximumSize(new Dimension(25, 25));
        saveButton.setIcon(new ImageIcon(losEditorJComponent.getImage("CASL/images/saveFile.gif")));
        saveButton.setMargin(new Insets(2, 2, 2, 2));
        saveButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                saveMap();
            }
        });

        menuFileClose.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                closeMap();
            }
        });
        menuFileClose.setPreferredSize(new Dimension(100, 20));
        menuFileClose.setEnabled(false);
        menuFileClose.setMnemonic('1');
        menuFileClose.setText("Close");
        menuFileClose.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                closeMap();
            }
        });
        menuFileOpen.setPreferredSize(new Dimension(100, 20));
        menuFileOpen.setMnemonic('1');
        menuFileOpen.setText("Open...");
        menuFileOpen.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                openArchive();
            }
        });
        menuFileNew.setPreferredSize(new Dimension(100, 20));
        menuFileNew.setMnemonic('1');
        menuFileNew.setText("New...");
        menuFileNew.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                newMap();
            }
        });
        menuFileSave.setPreferredSize(new Dimension(100, 20));
        menuFileSave.setEnabled(false);
        menuFileSave.setMnemonic('0');
        menuFileSave.setText("Save");
        menuFileSave.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                saveMap();
            }
        });
        menuFilePrint.setPreferredSize(new Dimension(100, 20));
        menuFilePrint.setEnabled(false);
        menuFilePrint.setMnemonic('0');
        menuFilePrint.setText("Print");

        menuEditInsert.setPreferredSize(new Dimension(100, 20));
        menuEditInsert.setMnemonic('1');
        menuEditInsert.setText("Insert...");
        menuEditInsert.setEnabled(true);
        menuEditInsert.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                insertMap();
            }
        });

        buttonBar.setLayout(flowLayout1);
        functionSelector.setBorder(BorderFactory.createEtchedBorder());
        functionSelector.setMaximumSize(new Dimension(11000, 1000));
        functionSelector.setMinimumSize(new Dimension(100, 30));
        functionSelector.setPreferredSize(new Dimension(850, 30));
        functionSelector.setLayout(flowLayout3);

        terrainComboBox.setEnabled(false);
        terrainComboBox.setMaximumSize(new Dimension(200, 20));
        terrainComboBox.setMinimumSize(new Dimension(200, 20));
        terrainComboBox.setPreferredSize(new Dimension(200, 20));
        terrainComboBox.setMaximumRowCount(5);
        terrainComboBox.addItemListener(new java.awt.event.ItemListener() {

            public void itemStateChanged(ItemEvent e) {
                terrainComboBox();
            }
        });
        functionComboBox.setPreferredSize(new Dimension(120, 20));
        functionComboBox.setMaximumRowCount(5);
        functionComboBox.addItemListener(new java.awt.event.ItemListener() {

            public void itemStateChanged(ItemEvent e) {
                functionComboBox();
            }
        });
        functionComboBox.setMinimumSize(new Dimension(120, 20));
        functionComboBox.setEnabled(false);
        functionComboBox.setMaximumSize(new Dimension(120, 20));
        brushComboBox.setEnabled(false);
        brushComboBox.setMaximumSize(new Dimension(120, 20));
        brushComboBox.setMinimumSize(new Dimension(120, 20));
        brushComboBox.setPreferredSize(new Dimension(120, 20));
        brushComboBox.setMaximumRowCount(5);
        brushComboBox.addItemListener(new java.awt.event.ItemListener() {

            public void itemStateChanged(ItemEvent e) {
                brushComboBox();
            }
        });
        brushComboBox.addItem("Hex");
        brushComboBox.addItem("64 Pixel");
        brushComboBox.addItem("32 Pixel");
        brushComboBox.addItem("16 Pixel");
        brushComboBox.addItem("8  Pixel");
        brushComboBox.addItem("4  Pixel");
        brushComboBox.addItem("2  Pixel");
        brushComboBox.addItem("1  Pixel");

        flowLayout3.setAlignment(FlowLayout.LEFT);
        flowLayout3.setHgap(2);
        flowLayout3.setVgap(2);

        spacer.setAlignmentX((float) 0.5);
        spacer.setAlignmentY((float) 0.0);
        spacer.setOpaque(true);
        spacer.setRequestFocusEnabled(false);
        spacer.setIconTextGap(0);
        spacer.setText("  ");

        updateMapButton.setMinimumSize(new Dimension(25, 25));
        updateMapButton.setFocusPainted(false);
        updateMapButton.setEnabled(false);
        updateMapButton.setMaximumSize(new Dimension(25, 25));
        updateMapButton.setToolTipText("Update LOS data");
        updateMapButton.setIcon(new ImageIcon(losEditorJComponent.getImage("CASL/images/updateMap.gif")));
        updateMapButton.addMouseListener(new java.awt.event.MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                updateMapButton();
            }
        });
        updateMapButton.setPreferredSize(new Dimension(25, 25));
        updateMapButton.setRequestFocusEnabled(false);

        squareBrushIcon = new ImageIcon(losEditorJComponent.getImage("CASL/images/squareSelector.gif"));
        roundBrushIcon = new ImageIcon(losEditorJComponent.getImage("CASL/images/roundSelector.gif"));
        brushShapeButton.setPreferredSize(new Dimension(25, 25));
        brushShapeButton.setIcon(squareBrushIcon);
        brushShapeButton.setMaximumSize(new Dimension(25, 25));
        brushShapeButton.setEnabled(false);
        brushShapeButton.setToolTipText("Brush shape");
        brushShapeButton.setFocusPainted(false);
        brushShapeButton.setRequestFocusEnabled(false);
        brushShapeButton.addChangeListener(new javax.swing.event.ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                brushShapeButton();
            }
        });
        brushShapeButton.setMinimumSize(new Dimension(25, 25));

        VASLMapIcon = new ImageIcon(losEditorJComponent.getImage("CASL/images/VASLMapIcon.gif"));
        LOSMapIcon = new ImageIcon(losEditorJComponent.getImage("CASL/images/LOSMapIcon.gif"));
        mapToggleButton.setPreferredSize(new Dimension(25, 25));
        mapToggleButton.setIcon(LOSMapIcon);
        mapToggleButton.setMaximumSize(new Dimension(25, 25));
        mapToggleButton.setEnabled(true);
        mapToggleButton.setToolTipText("Brush shape");
        mapToggleButton.setFocusPainted(false);
        mapToggleButton.setRequestFocusEnabled(false);
        mapToggleButton.addChangeListener(new javax.swing.event.ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                mapToggleButton();
            }
        });
        mapToggleButton.setMinimumSize(new Dimension(25, 25));

        spacer3.setText("  ");
        spacer3.setIconTextGap(0);
        spacer3.setRequestFocusEnabled(false);
        spacer3.setOpaque(true);
        spacer3.setAlignmentY((float) 0.0);
        spacer3.setAlignmentX((float) 0.5);
        jLabel1.setText("Function:");

        undoFunctionButton.setPreferredSize(new Dimension(25, 25));
        undoFunctionButton.setRequestFocusEnabled(false);
        undoFunctionButton.setIcon(new ImageIcon(losEditorJComponent.getImage("CASL/images/undo.gif")));
        undoFunctionButton.addMouseListener(new java.awt.event.MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                undoButton();
            }
        });
        undoFunctionButton.setEnabled(false);
        undoFunctionButton.setMaximumSize(new Dimension(25, 25));
        undoFunctionButton.setMinimumSize(new Dimension(25, 25));
        undoFunctionButton.setToolTipText("Remove selections");
        bridgeButton.setPreferredSize(new Dimension(25, 25));
        bridgeButton.setRequestFocusEnabled(false);
        bridgeButton.setIcon(new ImageIcon(losEditorJComponent.getImage("CASL/images/building.gif")));
        bridgeButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                showBridgeDialog();
            }
        });
        bridgeButton.setEnabled(false);
        bridgeButton.setMaximumSize(new Dimension(25, 25));
        bridgeButton.setMinimumSize(new Dimension(25, 25));
        jLabel2.setText("   Terrain: ");
        jLabel3.setText("   Brush: ");
        flowLayout1.setAlignment(FlowLayout.LEFT);
        flowLayout1.setHgap(0);
        flowLayout1.setVgap(0);
        spacer4.setAlignmentX((float) 0.5);
        spacer4.setAlignmentY((float) 0.0);
        spacer4.setOpaque(true);
        spacer4.setRequestFocusEnabled(false);
        spacer4.setIconTextGap(0);
        spacer4.setText("  ");
        rotationTextField.setPreferredSize(new Dimension(25, 21));
        rotationTextField.setText("0");
        rotationTextField.setEnabled(false);
        rotationTextField.addKeyListener(new java.awt.event.KeyAdapter() {

            public void keyReleased(KeyEvent e) {
                rotationTextField_keyReleased();
            }
        });
        toArrowButton.setMinimumSize(new Dimension(25, 25));
        toArrowButton.setMaximumSize(new Dimension(25, 25));
        toArrowButton.setEnabled(false);
        toArrowButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                toArrowButton_actionPerformed();
            }
        });
        toArrowButton.setRequestFocusEnabled(false);
        toArrowButton.setActionCommand("");
        toArrowButton.setIcon(new ImageIcon(losEditorJComponent.getImage("CASL/images/toArrow.gif")));
        toArrowButton.setPreferredSize(new Dimension(40, 25));
        toTerrainComboBox.addItemListener(new java.awt.event.ItemListener() {

            public void itemStateChanged(ItemEvent e) {
                toTerrainComboBox();
            }
        });
        toTerrainComboBox.setMaximumRowCount(5);
        toTerrainComboBox.setPreferredSize(new Dimension(200, 20));
        toTerrainComboBox.setMinimumSize(new Dimension(200, 20));
        toTerrainComboBox.setMaximumSize(new Dimension(200, 20));
        toTerrainComboBox.setEnabled(false);
        spacer5.setAlignmentX((float) 0.5);
        spacer5.setAlignmentY((float) 0.0);
        spacer5.setOpaque(true);
        spacer5.setRequestFocusEnabled(false);
        spacer5.setIconTextGap(0);
        spacer5.setText("  ");
        testButton.setIcon(new ImageIcon(losEditorJComponent.getImage("CASL/images/test.gif")));
        testButton.setToolTipText("Run LOS tests");
        testButton.setEnabled(false);
        testButton.setRequestFocusEnabled(false);
        testButton.setPreferredSize(new Dimension(25, 25));
        testButton.setMinimumSize(new Dimension(25, 25));
        testButton.setMaximumSize(new Dimension(25, 25));
        testButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                testButton();
            }
        });
        testButton.setMargin(new Insets(2, 2, 2, 2));

        menuFile.add(menuFileNew);
        menuFile.add(menuFileOpen);
        menuFile.add(menuFileClose);
        menuFile.addSeparator();
        menuFile.add(menuFileSave);
        menuFile.addSeparator();
        menuFile.add(menuFilePrint);
        menuFile.addSeparator();
        menuFile.add(menuFileExit);
        menuEdit.add(menuEditFlip);
        menuEdit.add(menuEditInsert);
        menuHelp.add(menuHelpAbout);
        menuBar1.add(menuFile);
        menuBar1.add(menuEdit);
        menuBar1.add(menuHelp);
        this.setJMenuBar(menuBar1);
        this.getContentPane().add(statusBar, BorderLayout.SOUTH);
        this.getContentPane().add(buttonBar, BorderLayout.NORTH);
        buttonBar.add(toolBar, null);
        toolBar.add(newButton);
        toolBar.add(openButton);
        toolBar.add(saveButton, null);
        toolBar.add(updateMapButton, null);
        toolBar.add(undoFunctionButton, null);
        toolBar.add(mapToggleButton, null);
        buttonBar.add(jLabel3, null);
        buttonBar.add(brushComboBox, null);
        buttonBar.add(brushShapeButton, null);
        buttonBar.add(bridgeButton, null);
        buttonBar.add(rotationTextField, null);
        buttonBar.add(spacer5, null);
        buttonBar.add(testButton, null);
        buttonBar.add(functionSelector, null);
        functionSelector.add(jLabel1, null);
        functionSelector.add(functionComboBox, null);
        functionSelector.add(jLabel2, null);
        functionSelector.add(terrainComboBox, null);
        functionSelector.add(toArrowButton, null);
        functionSelector.add(toTerrainComboBox, null);
        this.getContentPane().add(jScrollPane1, BorderLayout.CENTER);
        jScrollPane1.getViewport().add(losEditorJComponent, null);

        boardDirectory = LOSEditorProperties.getBoardDirectory();

        // set the status bar
        setStatusBarText("  ");

        // setup the window
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(screenSize.width, screenSize.height);
        setLocation(0, 0);
        setVisible(true);

        validate();
        requestFocus();
    }

    //File | Exit action performed
    public void fileExit() {

        // Map changed?
        if (losEditorJComponent.isMapChanged()) {

            int response = this.AskYesNo("Save changes?");
            if (response == JOptionPane.YES_OPTION) {

                saveMap();
                closeMapVariables();

            }
            else if (response == JOptionPane.CANCEL_OPTION) {

                return;
            }
        }

        System.exit(0);
    }

    //Help | About action performed
    public void helpAbout_actionPerformed() {
        LOSEditorFrame_AboutBox dlg = new LOSEditorFrame_AboutBox(this);
        Dimension dlgSize = dlg.getPreferredSize();
        Dimension frmSize = getSize();
        Point loc = getLocation();
        dlg.setLocation((frmSize.width - dlgSize.width) / 2 + loc.x, (frmSize.height - dlgSize.height) / 2 + loc.y);
        dlg.setModal(true);

        dlg.setVisible(true);
    }

    public void editFlip() {

        losEditorJComponent.flipMap();
    }

    //Overridden so we can exit on System Close
    protected void processWindowEvent(WindowEvent e) {
        super.processWindowEvent(e);
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            fileExit();
        }
    }

    void newMap() {

        showNewMapDialog();
    }

    public void showNewMapDialog() {

        NewMapDialog dialog = new NewMapDialog(this, "Create a new map", true);

        //Center the dialog box
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension frameSize = dialog.getSize();
        if (frameSize.height > screenSize.height) frameSize.height = screenSize.height;
        if (frameSize.width > screenSize.width) frameSize.width = screenSize.width;
        dialog.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
        dialog.setVisible(true);

    }

    public void showBridgeDialog(){

        // set the rotation to zero
        losEditorJComponent.setRotation(0);
        rotationTextField.setText("0");

        if (functionComboBox.getSelectedItem().equals("Add bridge")){

            BridgeDialog dialog = new BridgeDialog(
                    this,
                    "Set bridge parameters",
                    true,
                    losEditorJComponent.getCurrentTerrain(),
                    losEditorJComponent.getBridgeRoadElevation()
            );

            //Center the dialog box
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension frameSize = dialog.getSize();
            if (frameSize.height > screenSize.height)	frameSize.height = screenSize.height;
            if (frameSize.width > screenSize.width)  	frameSize.width = screenSize.width;
            dialog.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
            dialog.setVisible(true);
        }
    }

    public void setBridgeParameters(String terr, int roadElevation) {

        losEditorJComponent.setBridgeParameters(terr, roadElevation);
    }

    public void saveMap() {

        if (!losEditorJComponent.isMapOpen()) {
            return;
        }
        else {
            losEditorJComponent.saveLOSData();
        }
    }

    /**
     * Close current archive
     */
    //TODO: finish me
    public boolean closeArchive() {

        return  true;
    }

    public void openArchive(){

        // show the file chooser
        JFileChooser fileChooser = new JFileChooser(LOSEditorProperties.getBoardDirectory());
        int selected = fileChooser.showOpenDialog(this);

        if (selected == JFileChooser.APPROVE_OPTION) {

            // abort if user chooses not to close the current map
            if (!closeArchive()) return;

            losEditorJComponent.openArchive(fileChooser.getSelectedFile().getName());

            // enable menus/buttons
            if (losEditorJComponent.isMapOpen()) {
                saveButton.setEnabled(true);
                menuFileClose.setEnabled(true);
                menuFileSave.setEnabled(true);
                menuEdit.setEnabled(true);

                // set function
                setFunction("LOS");

                // make the map the active component
                losEditorJComponent.requestFocus();
                this.setTitle("LOSEditorApp - " + losEditorJComponent.getArchiveName());

            }
        }
    }

    public void openMap(String mapName) {

        // this is all redundant code
        // trap errors
        try {
            losEditorJComponent.openArchive(mapName);
        } catch (Exception e) {

            setStatusBarText("Cannot open the board archive " + losEditorJComponent.getArchiveName());
            e.printStackTrace(System.err);
            return;
        }

        // enable menus/buttons
        if (losEditorJComponent.isMapOpen()) {
            saveButton.setEnabled(true);
            menuFileClose.setEnabled(true);
            menuFileSave.setEnabled(true);
            menuEdit.setEnabled(true);

            // set function
            setFunction("LOS");

            // make the map the active component
            losEditorJComponent.requestFocus();
            this.setTitle("LOSEditorApp - " + losEditorJComponent.getArchiveName());
        }
    }

    public void insertMap() {

        // create the file filter
        MapFileFilter filter = new MapFileFilter("map", "Map files");
        filter.addExtension("map");
        filter.setDescription("Map files");

        // show the file chooser
        JFileChooser fileChooser = new JFileChooser(boardDirectory);
        fileChooser.setFileFilter(filter);
        int selected = fileChooser.showOpenDialog(this);

        if (selected == JFileChooser.APPROVE_OPTION) {

            String fileName = fileChooser.getCurrentDirectory().getPath() + System.getProperty("file.separator", "\\") + fileChooser.getSelectedFile().getName();

            // trap errors
            try {
                setStatusBarText("Opening map...");
                //TODO: won't work - need to get the shared metadata
                LOSDataEditor tempMap = new LOSDataEditor(fileName, LOSEditorProperties.getBoardDirectory(), null);

                // show the dialog
                InsertMapDialog dialog = new InsertMapDialog(this, "Insert a map", true, tempMap);

                //Center the dialog box
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                Dimension frameSize = dialog.getSize();
                if (frameSize.height > screenSize.height) frameSize.height = screenSize.height;
                if (frameSize.width > screenSize.width) frameSize.width = screenSize.width;
                dialog.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
                dialog.setVisible(true);

            } catch (Exception e) {

                JOptionPane.showMessageDialog(null, "Cannot open the map: " + fileName, "File not found or invalid", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void insertMap(Map insertMap, String upperLeft) {

        losEditorJComponent.insertMap(insertMap, upperLeft);
    }

    private void closeMapVariables() {

        // no function
        setFunction(null);

        // disable menus/buttons
        saveButton.setEnabled(false);
        menuFileClose.setEnabled(false);
        menuFileSave.setEnabled(false);
        menuEdit.setEnabled(false);

        //close the map
        losEditorJComponent.closeMap();
        this.setTitle("LOSEditorApp");
    }

    boolean closeMap() {

        // Map changed?
        if (losEditorJComponent.isMapChanged()) {

            int response = this.AskYesNo("Save changes?");
            if (response == JOptionPane.YES_OPTION) {

                saveMap();
                closeMapVariables();
                return true;
            } else if (response == JOptionPane.NO_OPTION) {

                closeMapVariables();
                return true;
            } else if (response == JOptionPane.CANCEL_OPTION) {

                return false;
            }
        } else {
            closeMapVariables();
            return true;
        }
        return false;
    }

    void setStatusBarText(String s) {

        statusBar.setText(s);
    }

    void functionComboBox() {

        setFunction((String) functionComboBox.getSelectedItem());
    }

    void terrainComboBox() {

        String selection = (String) terrainComboBox.getSelectedItem();

        // using bridge?
        if (selection != null && selection.equals("Custom Bridge")) {

            bridgeButton.setEnabled(true);
            losEditorJComponent.setCustomBridgeOn(true);
        } else {
            bridgeButton.setEnabled(false);
            losEditorJComponent.setCustomBridgeOn(false);
        }

        if (selection != null) {
            losEditorJComponent.setCurrentTerrain(selection);
        }
    }

    void toTerrainComboBox() {

        String selection = (String) toTerrainComboBox.getSelectedItem();

        if (selection != null) {
            losEditorJComponent.setCurrentToTerrain(selection);
        }
    }

    void brushComboBox() {

        losEditorJComponent.setCurrentBrush((String) brushComboBox.getSelectedItem());
    }

    void mapToggleButton() {

        if (mapToggleButton.isSelected()) {

            losEditorJComponent.setVASLImage(true);
            mapToggleButton.setIcon(VASLMapIcon);
        } else {
            losEditorJComponent.setVASLImage(false);
            mapToggleButton.setIcon(LOSMapIcon);
        }
        repaint();
    }

    void brushShapeButton() {

        if (brushShapeButton.isSelected()) {

            losEditorJComponent.setRoundBrush(true);
            brushShapeButton.setIcon(roundBrushIcon);
        } else {
            losEditorJComponent.setRoundBrush(false);
            brushShapeButton.setIcon(squareBrushIcon);
        }
    }

    public void createNewMap(int width, int height) {

        // abort if user chooses not to close the current map
        if (!closeMap()) return;

        losEditorJComponent.createNewMap(width, height);

        // enable menus/buttons
        saveButton.setEnabled(true);
        menuFileClose.setEnabled(true);
        menuFileSave.setEnabled(true);
        menuEdit.setEnabled(true);
        testButton.setEnabled(true);
        setFunction("LOS");
        validate();

        // make the map the active component
        losEditorJComponent.requestFocus();
    }

    void updateMapButton() {

        losEditorJComponent.updateMap();
    }

    void setFunction(String newFunction) {

        // no function?
        if (newFunction == null) {

            // disable widgets
            functionComboBox.setSelectedIndex(0);
            functionComboBox.setEnabled(false);
            terrainComboBox.setEnabled(false);
            toArrowButton.setEnabled(false);
            toTerrainComboBox.setEnabled(false);
            brushComboBox.setEnabled(false);
            updateMapButton.setEnabled(false);
            brushShapeButton.setEnabled(false);
            rotationTextField.setEnabled(false);
            undoFunctionButton.setEnabled(false);
            testButton.setEnabled(false);
            return;
        } else {
            functionComboBox.setEnabled(true);
        }

        losEditorJComponent.setCurrentFunction(newFunction);

        // clear the combo boxes
        if (terrainComboBox.getItemCount() != 0) {
            terrainComboBox.removeAllItems();
        }
        if (toTerrainComboBox.getItemCount() != 0) {
            toTerrainComboBox.removeAllItems();
        }

        // set the function, add appropriate list for function
        if (newFunction.equals("LOS")) {

            // setup the map editor
            losEditorJComponent.setCurrentTerrain("");

            // disable widgets
            terrainComboBox.setEnabled(false);
            toArrowButton.setEnabled(false);
            toTerrainComboBox.setEnabled(false);
            brushComboBox.setEnabled(false);
            updateMapButton.setEnabled(true);
            brushShapeButton.setEnabled(false);
            rotationTextField.setEnabled(false);
            undoFunctionButton.setEnabled(false);
            testButton.setEnabled(true);

        } else if (newFunction.equals("Set ground level")) {

            // set widgets
            terrainComboBox.setEnabled(true);
            toArrowButton.setEnabled(true);
            toTerrainComboBox.setEnabled(true);
            brushComboBox.setEnabled(true);
            updateMapButton.setEnabled(true);
            brushShapeButton.setEnabled(true);
            rotationTextField.setEnabled(true);
            undoFunctionButton.setEnabled(true);
            testButton.setEnabled(true);

            terrainComboBox.addItem("Hill Level 0");
            terrainComboBox.addItem("Hill Level 1");
            terrainComboBox.addItem("Hill Level 2");
            terrainComboBox.addItem("Hill Level 3");
            terrainComboBox.addItem("Hill Level 4");
            terrainComboBox.addItem("Hill Level 5");
            terrainComboBox.addItem("Hill Level 6");
            terrainComboBox.addItem("Hill Level 7");
            terrainComboBox.addItem("Hill Level 8");
            terrainComboBox.addItem("Hill Level 9");
            terrainComboBox.addItem("Hill Level 10");
            terrainComboBox.addItem("Valley -1");
            terrainComboBox.addItem("Valley -2");
            terrainComboBox.addItem("Gully");
            terrainComboBox.addItem("Dry Stream");
            terrainComboBox.addItem("Shallow Stream");
            terrainComboBox.addItem("Deep Stream");

            toTerrainComboBox.addItem("Hill Level 0");
            toTerrainComboBox.addItem("Hill Level 1");
            toTerrainComboBox.addItem("Hill Level 2");
            toTerrainComboBox.addItem("Hill Level 3");
            toTerrainComboBox.addItem("Hill Level 4");
            toTerrainComboBox.addItem("Hill Level 5");
            toTerrainComboBox.addItem("Hill Level 6");
            toTerrainComboBox.addItem("Hill Level 7");
            toTerrainComboBox.addItem("Hill Level 8");
            toTerrainComboBox.addItem("Hill Level 9");
            toTerrainComboBox.addItem("Hill Level 10");
            toTerrainComboBox.addItem("Valley -1");
            toTerrainComboBox.addItem("Valley -2");
            toTerrainComboBox.addItem("Gully");
            toTerrainComboBox.addItem("Dry Stream");
            toTerrainComboBox.addItem("Shallow Stream");
            toTerrainComboBox.addItem("Deep Stream");

            // setup the map editor
            losEditorJComponent.setCurrentTerrain("Hill Level 0");
        } else if (newFunction.equals("Add hexside terrain")) {

            // set widgets
            terrainComboBox.setEnabled(true);
            toArrowButton.setEnabled(true);
            toTerrainComboBox.setEnabled(true);
            brushComboBox.setEnabled(false);
            updateMapButton.setEnabled(true);
            brushShapeButton.setEnabled(false);
            rotationTextField.setEnabled(false);
            undoFunctionButton.setEnabled(true);
            testButton.setEnabled(true);

            // losEditorJComponent

            terrainComboBox.addItem("Wall");
            terrainComboBox.addItem("Road Block");
            terrainComboBox.addItem("Hedge");
            terrainComboBox.addItem("Bocage");
            terrainComboBox.addItem("Cliff");
            terrainComboBox.addItem("Rowhouse Wall");

            terrainComboBox.addItem("Panji");
            terrainComboBox.addItem("Rice Paddy Bank");
            terrainComboBox.addItem("Remove");

            toTerrainComboBox.addItem("Wall");
            toTerrainComboBox.addItem("Road Block");
            toTerrainComboBox.addItem("Hedge");
            toTerrainComboBox.addItem("Bocage");
            toTerrainComboBox.addItem("Cliff");
            toTerrainComboBox.addItem("Rowhouse Wall");
            toTerrainComboBox.addItem("Panji");
            toTerrainComboBox.addItem("Rice Paddy Bank");

            // setup the map editor
            losEditorJComponent.setCurrentTerrain("Wall");
        } else if (newFunction.equals("Add terrain")) {

            // set widgets
            terrainComboBox.setEnabled(true);
            toArrowButton.setEnabled(true);
            toTerrainComboBox.setEnabled(true);
            brushComboBox.setEnabled(true);
            updateMapButton.setEnabled(true);
            brushShapeButton.setEnabled(true);
            rotationTextField.setEnabled(true);
            undoFunctionButton.setEnabled(true);
            testButton.setEnabled(true);

            terrainComboBox.addItem("Open Ground");
            terrainComboBox.addItem("Plowed Field");
            terrainComboBox.addItem("Snow");
            terrainComboBox.addItem("Deep Snow");
            terrainComboBox.addItem("Ice");
            terrainComboBox.addItem("Mud");
            terrainComboBox.addItem("Mudflats");
            terrainComboBox.addItem("Water");
            terrainComboBox.addItem("Shallow Water");
            terrainComboBox.addItem("River");
            terrainComboBox.addItem("Shallow River");
            terrainComboBox.addItem("Ford");
            terrainComboBox.addItem("Canal");
            terrainComboBox.addItem("Marsh");
            terrainComboBox.addItem("Dirt Road");
            terrainComboBox.addItem("Paved Road");
            terrainComboBox.addItem("Runway");
            terrainComboBox.addItem("Path");
            terrainComboBox.addItem("Shellholes");

            terrainComboBox.addItem("Grain");
            terrainComboBox.addItem("Brush");
            terrainComboBox.addItem("Crags");
            terrainComboBox.addItem("Debris");
            terrainComboBox.addItem("Graveyard");
            terrainComboBox.addItem("Woods");
            terrainComboBox.addItem("Forest");
            terrainComboBox.addItem("Pine Woods");
            terrainComboBox.addItem("Orchard");
            terrainComboBox.addItem("Orchard, Out of Season");
            terrainComboBox.addItem("Lumberyard");
            terrainComboBox.addItem("Wooden Rubble");
            terrainComboBox.addItem("Wooden Building");
            terrainComboBox.addItem("Wooden Building, 1 Level");
            terrainComboBox.addItem("Wooden Building, 2 Level");
            terrainComboBox.addItem("Wooden Building, 3 Level");
            terrainComboBox.addItem("Wooden Building, 4 Level");
            terrainComboBox.addItem("Wooden Factory, 1.5 Level");
            terrainComboBox.addItem("Wooden Factory, 2.5 Level");
            terrainComboBox.addItem("Wooden Market Place");
            terrainComboBox.addItem("Stone Rubble");
            terrainComboBox.addItem("Stone Building");
            terrainComboBox.addItem("Stone Building, 1 Level");
            terrainComboBox.addItem("Stone Building, 2 Level");
            terrainComboBox.addItem("Stone Building, 3 Level");
            terrainComboBox.addItem("Stone Building, 4 Level");
            terrainComboBox.addItem("Stone Factory, 1.5 Level");
            terrainComboBox.addItem("Stone Factory, 2.5 Level");
            terrainComboBox.addItem("Stone Market Place");

            terrainComboBox.addItem("Temple");
            terrainComboBox.addItem("Light Jungle");
            terrainComboBox.addItem("Dense Jungle");
            terrainComboBox.addItem("Bamboo");
            terrainComboBox.addItem("Palm Trees");
            terrainComboBox.addItem("Huts");
            terrainComboBox.addItem("Collapsed Huts");
            terrainComboBox.addItem("Kunai");
            terrainComboBox.addItem("Swamp");
            terrainComboBox.addItem("Rice Paddy, Drained");
            terrainComboBox.addItem("Rice Paddy, Irrigated");
            terrainComboBox.addItem("Rice Paddy, In Season");

            toTerrainComboBox.addItem("Open Ground");
            toTerrainComboBox.addItem("Plowed Field");
            toTerrainComboBox.addItem("Snow");
            toTerrainComboBox.addItem("Deep Snow");
            toTerrainComboBox.addItem("Ice");
            toTerrainComboBox.addItem("Mud");
            toTerrainComboBox.addItem("Mudflats");
            toTerrainComboBox.addItem("Water");
            toTerrainComboBox.addItem("Shallow Water");
            toTerrainComboBox.addItem("River");
            toTerrainComboBox.addItem("Shallow River");
            toTerrainComboBox.addItem("Ford");
            toTerrainComboBox.addItem("Canal");
            toTerrainComboBox.addItem("Marsh");
            toTerrainComboBox.addItem("Dirt Road");
            toTerrainComboBox.addItem("Paved Road");
            toTerrainComboBox.addItem("Runway");
            toTerrainComboBox.addItem("Path");
            toTerrainComboBox.addItem("Shellholes");

            toTerrainComboBox.addItem("Grain");
            toTerrainComboBox.addItem("Brush");
            toTerrainComboBox.addItem("Crags");
            toTerrainComboBox.addItem("Debris");
            toTerrainComboBox.addItem("Graveyard");
            toTerrainComboBox.addItem("Woods");
            toTerrainComboBox.addItem("Forest");
            toTerrainComboBox.addItem("Pine Woods");
            toTerrainComboBox.addItem("Orchard");
            toTerrainComboBox.addItem("Orchard, Out of Season");
            toTerrainComboBox.addItem("Lumberyard");
            toTerrainComboBox.addItem("Wooden Rubble");
            toTerrainComboBox.addItem("Wooden Building");
            toTerrainComboBox.addItem("Wooden Building, 1 Level");
            toTerrainComboBox.addItem("Wooden Building, 2 Level");
            toTerrainComboBox.addItem("Wooden Building, 3 Level");
            toTerrainComboBox.addItem("Wooden Building, 4 Level");
            toTerrainComboBox.addItem("Wooden Factory, 1.5 Level");
            toTerrainComboBox.addItem("Wooden Factory, 2.5 Level");
            toTerrainComboBox.addItem("Wooden Market Place");
            toTerrainComboBox.addItem("Stone Rubble");
            toTerrainComboBox.addItem("Stone Building");
            toTerrainComboBox.addItem("Stone Building, 1 Level");
            toTerrainComboBox.addItem("Stone Building, 2 Level");
            toTerrainComboBox.addItem("Stone Building, 3 Level");
            toTerrainComboBox.addItem("Stone Building, 4 Level");
            toTerrainComboBox.addItem("Stone Factory, 1.5 Level");
            toTerrainComboBox.addItem("Stone Factory, 2.5 Level");
            toTerrainComboBox.addItem("Stone Market Place");
            toTerrainComboBox.addItem("Temple");
            toTerrainComboBox.addItem("Light Jungle");
            toTerrainComboBox.addItem("Dense Jungle");
            toTerrainComboBox.addItem("Bamboo");
            toTerrainComboBox.addItem("Palm Trees");
            toTerrainComboBox.addItem("Huts");
            toTerrainComboBox.addItem("Collapsed Huts");
            toTerrainComboBox.addItem("Kunai");
            toTerrainComboBox.addItem("Swamp");
            toTerrainComboBox.addItem("Rice Paddy, Drained");
            toTerrainComboBox.addItem("Rice Paddy, Irrigated");
            toTerrainComboBox.addItem("Rice Paddy, In Season");

            // setup the map editor
            losEditorJComponent.setCurrentTerrain("Open Ground");
        } else if (newFunction.equals("Add bridge")) {

            // set widgets
            terrainComboBox.setEnabled(true);
            toTerrainComboBox.setEnabled(false);
            brushComboBox.setEnabled(false);
            updateMapButton.setEnabled(true);
            brushShapeButton.setEnabled(false);
            rotationTextField.setEnabled(true);
            undoFunctionButton.setEnabled(true);
            bridgeButton.setEnabled(true);
            testButton.setEnabled(true);

            terrainComboBox.addItem("Custom Bridge");
            terrainComboBox.addItem("Remove");

            // setup the map editor
            losEditorJComponent.setBridgeParameters("Single Hex Wooden Bridge", 0);
        } else if (newFunction.equals("Add road")) {

            // set widgets
            terrainComboBox.setEnabled(true);
            toArrowButton.setEnabled(true);
            toTerrainComboBox.setEnabled(true);
            brushComboBox.setEnabled(false);
            updateMapButton.setEnabled(true);
            brushShapeButton.setEnabled(false);
            rotationTextField.setEnabled(false);
            undoFunctionButton.setEnabled(true);
            bridgeButton.setEnabled(false);
            testButton.setEnabled(true);

            terrainComboBox.addItem("Dirt Road");
            terrainComboBox.addItem("Paved Road");
            terrainComboBox.addItem("Runway");
            terrainComboBox.addItem("Sunken Road");
            terrainComboBox.addItem("Elevated Road");

            toTerrainComboBox.addItem("Dirt Road");
            toTerrainComboBox.addItem("Paved Road");
            toTerrainComboBox.addItem("Runway");
            toTerrainComboBox.addItem("Sunken Road");
            toTerrainComboBox.addItem("Elevated Road");
        } else if (newFunction.equals("Add objects")) {

            // set widgets
            terrainComboBox.setEnabled(true);
            toTerrainComboBox.setEnabled(false);
            brushComboBox.setEnabled(false);
            updateMapButton.setEnabled(true);
            rotationTextField.setEnabled(false);
            brushShapeButton.setEnabled(false);
            undoFunctionButton.setEnabled(true);
            bridgeButton.setEnabled(false);
            testButton.setEnabled(true);

            //TODO: remove tunnels/sewers
            terrainComboBox.addItem("Foxholes");
            terrainComboBox.addItem("Trench");
            terrainComboBox.addItem("Antitank Trench");
            terrainComboBox.addItem("Sangar");
            terrainComboBox.addItem("Stairway");
            terrainComboBox.addItem("Sewer");
            terrainComboBox.addItem("Tunnel");
            terrainComboBox.addItem("Smoke");
            terrainComboBox.addItem("Vehicle");
            terrainComboBox.addItem("Remove Stairway");
            terrainComboBox.addItem("Remove Tunnel/Sewer");
            terrainComboBox.addItem("Remove Entrenchment");
            terrainComboBox.addItem("Remove Smoke");
            terrainComboBox.addItem("Remove Vehicle");
        }
    }

    void undoButton() {
        losEditorJComponent.undoSelections();
    }

    public int AskYesNo(String question) {


        // get response
        return JOptionPane.showConfirmDialog(this, question);
    }

    public void paintImmediately() {

        statusBar.paintImmediately(0, 0, statusBar.getWidth(), statusBar.getHeight());

    }

    void rotationTextField_keyReleased() {

        try {
            losEditorJComponent.setRotation(Integer.parseInt(rotationTextField.getText()));
        } catch (Exception exp) {
            losEditorJComponent.setRotation(0);
        }
    }

    void toArrowButton_actionPerformed() {

        int response = this.AskYesNo(
                "Are you sure you want to convert all " +
                        terrainComboBox.getSelectedItem() + " terrain to " +
                        toTerrainComboBox.getSelectedItem() + "?");

        if (response == JOptionPane.YES_OPTION) {

            losEditorJComponent.changeAllTerrain();
        }
    }

    void testButton() {

        // losEditorJComponent.runLosTest();
        losEditorJComponent.runSingleLOS();
    }
}

