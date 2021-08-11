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

import VASL.build.module.map.boardArchive.SSRControlsFile;
import VASL.build.module.map.boardPicker.*;
import VASSAL.Info;
import VASSAL.build.*;
import VASSAL.build.module.map.BoardPicker;
import VASSAL.build.module.map.GlobalMap;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.BoardSlot;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.configure.DirectoryConfigurer;
import VASSAL.configure.ValidationReport;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.ReadErrorDialog;
import org.apache.commons.codec.digest.DigestUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.text.Collator;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ASLBoardPicker extends BoardPicker implements ActionListener  {
    private static final Logger logger =
            LoggerFactory.getLogger(ASLBoardPicker.class);

    /**
     * The key for the preferences setting giving the board directory
     */
    public static final String BOARD_DIR = "boardURL";
    private static final String MODULE_SSR_CONTROL_FILE_NAME = "boardData/SSRControls";
    private static final String OVERLAY_SSR_CONTROL_FILE_NAME = "SSRControls";

    private File boardDir;
    protected TerrainEditor terrain;
    private SetupControls setupControls;
    private boolean enableDeluxe;
    private boolean enableDB = false;

    // implement using xml file for board versions
    private static final String boardsFileElement = "boardsMetadata";
    private static final String coreboardElement = "coreBoards";
    private static final String boarddataType = "boarddata";
    private static final String coreboardNameAttr = "name";
    private static final String coreboardversionAttr = "version";
    private static final String coreboardversiondateAttr = "versionDate";
    private static final String coreboarddescAttr = "description";
    private static final String otherboardElement = "otherBoards";
    private static final String otherboardNameAttr = "name";
    private static final String otherboardversionAttr = "version";
    private static final String otherboardversiondateAttr = "versionDate";
    private static final String otherboarddescAttr = "description";

    public ASLBoardPicker() {

    }

    protected void initComponents() {
        initTerrainEditor();
        super.initComponents();
        addButton("Add overlays");
        addButton("Crop boards");
        addButton("Terrain SSR");
    }

    public Command decode(String command) {
        if (command.startsWith("bd\t")) {
            List<Board> v = new ArrayList<Board>();
            Command comm = new NullCommand();
            if (command.length() > 3) {
                command = command.substring(3);
                for (int index = command.indexOf("bd\t"); index > 0; index = command.indexOf("bd\t")) {
                    ASLBoard b = new VASLBoard();
                    String boardDesc = command.substring(0, index);
                    try {
                        StringTokenizer st2 = new StringTokenizer(boardDesc, "\t\n");
                        b.relativePosition().move(Integer.parseInt(st2.nextToken()), Integer.parseInt(st2.nextToken()));
                        String baseName = st2.nextToken();
                        updateBoard(baseName);
                        this.tileBoard(baseName);
                        buildBoard(b, boardDesc);
                        v.add(b);
                    } catch (final BoardException e) {
                        ErrorDialog.dataWarning(new BadDataReport("Board not found", boardDesc, e));
                    }
                    command = command.substring(index + 3);
                }
                ASLBoard b = new VASLBoard();
                try {
                    StringTokenizer st2 = new StringTokenizer(command, "\t\n");
                    b.relativePosition().move(Integer.parseInt(st2.nextToken()), Integer.parseInt(st2.nextToken()));
                    String baseName = st2.nextToken();
                    updateBoard(baseName);
                    this.tileBoard(baseName);
                    buildBoard(b, command);
                    v.add(b);
                } catch (final BoardException e) {
                    ErrorDialog.dataWarning(new BadDataReport("Unable to build board", command, e));
                }
            }
            comm = comm.append(new SetBoards(this, v));
            return comm;
        } else {
            return null;
        }
    }

    public String encode(Command c) {
        if (c instanceof SetBoards && map != null) {
            String s = "bd\t";
            for (Iterator it = getSelectedBoards().iterator(); it.hasNext(); ) {
                ASLBoard b = (ASLBoard) it.next();
                s += b.getState();
                if (it.hasNext()) {
                    s += "bd\t";
                }
            }
            return s;
        } else {
            return null;
        }
    }

    public void addTo(Buildable b) {
        DirectoryConfigurer config = new VASSAL.configure.DirectoryConfigurer(BOARD_DIR, "Board Directory");

        final GameModule g = GameModule.getGameModule();

        g.getPrefs().addOption("VASL", config);
        String storedValue = g.getPrefs().getStoredValue(BOARD_DIR);
        if (storedValue == null || !new File(storedValue).exists()) {
            File archive = new File(g.getDataArchive().getName());
            File dir = archive.getParentFile();
            File defaultDir = new File(dir, "boards");
            if (!defaultDir.exists()) {
                defaultDir.mkdir();
            }
            config.setValue(defaultDir);
        }
        setBoardDir((File) g.getPrefs().getValue(BOARD_DIR));
        g.getPrefs().getOption(BOARD_DIR).addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                setBoardDir((File) evt.getNewValue());
            }
        });
        super.addTo(b);
    }

    public void setBoardDir(File dir) {
        boardDir = dir;
        refreshPossibleBoards();
        reset();
    }

    @Override
    public void setup(boolean show) {
        super.setup(show);
        if (show) {
            setGlobalMapScale();
        }
    }

    @Override
    /**
     * This is a hack to prevent setup from being called multiple times when creating a new game.
     * When this happens the VASL map gets created twice, which is bad
     */
    public void finish() {
        currentBoards = new ArrayList<Board>(getBoardsFromControls());
        // test for proper board configuration
        if (slotPanel.getComponentCount() != currentBoards.size()){
            GameModule.getGameModule().getChatter().send("Board missing. Your board configuration must match X by X row/column matrix. Use NUL or NULV boards if required");
        }

    }

    public void setGlobalMapScale() {
        Collection<Board> bds = getSelectedBoards();
        if (bds.iterator().hasNext()) {
            double mag = bds.iterator().next().getMagnification();
            double globalScale = 0.19444444;
            if (mag > 1.0) {
                globalScale /= mag;
            }
            map.getComponentsOf(GlobalMap.class).get(0).setAttribute("scale", globalScale);
        }
    }

    public File getBoardDir() {
        return boardDir;
    }

    public void initTerrainEditor() {
        if (terrain == null) {
            terrain = new TerrainEditor();
            try ( InputStream in = GameModule.getGameModule().getDataArchive().getInputStream(MODULE_SSR_CONTROL_FILE_NAME)) {
                SSRControlsFile ssrControlsFile = new SSRControlsFile(in, "game module");
                terrain.readOptions(ssrControlsFile.getBasicNodes(), ssrControlsFile.getOptionNodes());
            } catch (IOException e) {
                logger.warn("Error reading SSR controls file in module archive: " + e.getMessage());
            }
        }
    }

    /**
     * Reads the current board directory and constructs the list of available boards
     */
    public void refreshPossibleBoards() {
        final String files[] = boardDir != null ? boardDir.list() : null;

        // all boards are added to the list whether in local directory or not
        final List<String> sorted = getallboards();

        if (files != null) {
            for (String file : files) {
                // TODO - remove requirement that boards start with "bd"
                // add all boards found in local directory
                if (file.startsWith("bd") && !(new File(boardDir, file)).isDirectory()) {
                    String name = file.substring(2);

                    if (name.contains(".")) {
                        name = null;
                    }
                    if (name != null && !sorted.contains(name)) { // prevents duplicate items in list
                        sorted.add(name);
                    }
                }
            }
        }

        //
        // * Strings with leading zeros sort ahead of those without.
        // * Strings with leading integer parts sort ahead of those without.
        // * Strings with lesser leading integer parts sort ahead of those with
        //   greater leading integer parts.
        // * Strings which are otherwise equal are sorted lexicographically by
        //   their trailing noninteger parts.
        //

        final Comparator<Object> alpha = Collator.getInstance();
        final Pattern pat = Pattern.compile("((0*)\\d*)(.*)");

        Comparator<String> comp = new Comparator<String>() {
            public int compare(String o1, String o2) {
                final Matcher m1 = pat.matcher(o1);
                final Matcher m2 = pat.matcher(o2);

                if (!m1.matches()) {
                    // impossible
                    throw new IllegalStateException();
                }

                if (!m2.matches()) {
                    // impossible
                    throw new IllegalStateException();
                }

                // count leading zeros
                final int z1 = m1.group(2).length();
                final int z2 = m2.group(2).length();

                // more leading zeros comes first
                if (z1 < z2) {
                    return 1;
                } else if (z1 > z2) {
                    return -1;
                }

                // same number of leading zeros
                final String o1IntStr = m1.group(1);
                final String o2IntStr = m2.group(1);
                if (o1IntStr.length() > 0) {
                    if (o2IntStr.length() > 0) {
                        try {
                            // both strings have integer parts
                            final BigInteger o1Int = new BigInteger(o1IntStr);
                            final BigInteger o2Int = new BigInteger(o2IntStr);

                            if (!o1Int.equals(o2Int)) {
                                // one integer part is smaller than the other
                                return o1Int.compareTo(o2Int);

                            }
                        } catch (NumberFormatException e) {
                            // impossible
                            throw new IllegalStateException(e);
                        }
                    } else {
                        // only o1 has an integer part
                        return -1;
                    }
                } else if (o2IntStr.length() > 0) {
                    // only o2 has an integer part
                    return 1;
                }

                // the traling string part is decisive
                return alpha.compare(m1.group(3), m2.group(3));
            }
        };

        Collections.sort(sorted, comp);
        possibleBoards.clear();
        for (String aSorted : sorted) {
            addBoard(aSorted);
        }
    }

    // adds all the boards to the selection list whether present in local directory or not
    private ArrayList<String> getallboards() {
        ArrayList<String> allboardslist = new ArrayList<String>();
        try {
            URL base = new URL(BoardVersionChecker.getboardVersionURL());
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);
            try (InputStream inputStream = conn.getInputStream()) {
                allboardslist = parseboardversionFile(inputStream);
            }

        } catch (IOException e){

        } catch (JDOMException e) {
            // throw new JDOMException("Cannot read the shared metadata file", e);
        }
        return allboardslist;
    }

    /**
     * Parses the board metadata xml file
     * @param metadata an <code>InputStream</code> for the v5boardVersions XML file
     * @throws JDOMException
     */
    private ArrayList<String> parseboardversionFile(InputStream metadata) throws JDOMException {

        ArrayList<String> addtoboardlist = new ArrayList<String>();
        SAXBuilder parser = new SAXBuilder();

        try {
            // the root element will be the boardsMetadata element
            Document doc = parser.build(metadata);
            org.jdom2.Element root = doc.getRootElement();

            // read the shared metadata
            if(root.getName().equals(boardsFileElement)) {

                for(org.jdom2.Element e: root.getChildren()) {

                    //add coreBoards
                    if(e.getName().equals(coreboardElement)){
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                // read the coreBoards attributes
                                addtoboardlist.add(f.getAttribute(coreboardNameAttr).getValue());
                            }
                        }
                    }
                    // add all other boards
                    if(e.getName().equals(otherboardElement)){
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                // read the coreBoards attributes
                                addtoboardlist.add(f.getAttribute(otherboardNameAttr).getValue());
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new JDOMException("Error reading the v5boardVersions.xml metadata", e);
        }
        return addtoboardlist;
    }

    public void build(Element e) {
        allowMultiple = true;
    }

    /*
     * Tiles a board by common name (e.g. 21, not bd21)
     */
    private void tileBoard(String commonName) {
        final GameModule g = GameModule.getGameModule();
        final String hstr =
                DigestUtils.sha1Hex(g.getGameName() + "_" + g.getGameVersion());

        String unReversedBoardName;
        if (commonName.startsWith("r")) {
            if (commonName.equalsIgnoreCase("r")) {
                // board r or R, this is ok
                unReversedBoardName = commonName;
            } else {
                // board rX, this is really X
                // Red Barricades (RB) and Ruweisat Ridge (RR) should not have gotten here
                unReversedBoardName = commonName.substring(1);
            }
        } else {
            unReversedBoardName = commonName;
        }

        final File fpath = new File(boardDir, "bd" + unReversedBoardName);

        final ASLTilingHandler th = new ASLTilingHandler(
                fpath.getAbsolutePath(),
                new File(Info.getConfDir(), "tiles/" + hstr),
                new Dimension(256, 256),
                1024,
                42
        );

        try {
            th.sliceTiles();
        } catch (IOException e) {
            ReadErrorDialog.error(e, fpath);
        }
    }

    /**
     * Ensures the given board exists and is up to date. If not it fetches it from the repository
     * @param baseName the board base name (i.e. reversed board may start with r)
     */
    private void updateBoard(String baseName) {

        // if the boardDir doesn't exist, bail early
        if (boardDir != null) {

            // normalize the base name by stripping r from reversed board
            String unReversedBoardName = baseName;
            if (baseName.startsWith("r") && !baseName.equalsIgnoreCase("r")) {
                // board rX, this is really X; r or R are ok
                // Red Barricades (RB) and Ruweisat Ridge (RR) should not have gotten here
                unReversedBoardName = baseName.substring(1);
            }

            final File boardFile = new File(boardDir, "bd" + unReversedBoardName);

            // try to grab the board from repository if missing
            if (!boardFile.exists()) {

                // try to fetch the missing board
                GameModule.getGameModule().warn("Board " + unReversedBoardName + " is missing. Downloading...");
                if (!BoardVersionChecker.updateBoard(unReversedBoardName)) {
                    GameModule.getGameModule().warn("Board download failed");
                } else {
                    GameModule.getGameModule().warn("Board download succeeded");
                }

            } else {

                // Update the board if it's out of date

                // get the board version
                VASLBoard b = new VASLBoard();
                try {
                    b.initializeFromArchive(boardFile);
                } catch (Exception e) {
                    // Fail silently if we can't find a version
                    return;
                }
                /*// get the current board version from the game properties
                Properties properties;
                String availableVersion = null;
                String boardVersions = ((String) GameModule.getGameModule().getPrefs().getValue(BoardVersionChecker.BOARD_VERSION_PROPERTY_KEY));
                if (boardVersions != null && boardVersions.length() > 0) {
                    try {
                        properties = new PropertiesEncoder(boardVersions).getProperties();
                        availableVersion = properties.getProperty(unReversedBoardName);

                    } catch (Exception e) {
                        // Fail silently if we can't find a version
                        return;
                    }
                }*/
                String availableVersion = null;
                try {
                    availableVersion = BoardVersionChecker.getlatestVersionnumberfromwebrepository(unReversedBoardName);
                } catch (Exception e) {
                    // Fail silently if we can't find a version
                    return;
                }
                double serverVersion;
                double localVersion;
                boolean doUpdate;

                if (availableVersion == null) {
                    serverVersion = -1;
                } else {
                    try {
                        serverVersion = Double.parseDouble(availableVersion);
                    } catch (NumberFormatException nfe) {
                        serverVersion = -1;
                    }
                }

                if (b.getVersion() == null) {
                    localVersion = -1;
                } else {
                    try {
                        localVersion = Double.parseDouble(b.getVersion());
                    } catch (NumberFormatException nfe) {
                        localVersion = 0;
                    }
                }

                if (localVersion == 0) {
                    // local board is of an indeterminate state, update the board if the server version is good
                    doUpdate = (serverVersion > -1);
                } else {
                    // update the board if the server version greater than local version
                    doUpdate = (serverVersion > localVersion);
                }

                if (doUpdate) {

                    // try to update board if out of date
                    GameModule.getGameModule().warn("Board " + unReversedBoardName + " is out of date. Updating...");
                    if (!BoardVersionChecker.updateBoard(unReversedBoardName)) {
                        GameModule.getGameModule().warn("Update failed");
                    } else {
                        GameModule.getGameModule().warn("Update succeeded");
                    }

                }
            }
        }
    }

    public void addBoard(String name) {
        final ASLBoard b = new VASLBoard();
        b.setCommonName(name);
        possibleBoards.add(b);
    }

    public void validate(Buildable target, ValidationReport report) {
    }

    public String[] getAllowableBoardNames() {
        String s[] = new String[possibleBoards.size()];
        for (int i = 0; i < s.length; ++i) {
            s[i] = ((ASLBoard) possibleBoards.get(i)).getCommonName();
        }
        return s;
    }

    public Configurable[] getConfigureComponents() {
        return new Configurable[0];
    }

    public Board getBoard(String name, boolean localized) {

        updateBoard(name);
        this.tileBoard(name);
        ASLBoard b = new VASLBoard();
        if (name != null) {
            if (name.length() == 1 && name.charAt(0) <= '9' && name.charAt(0) >= '0') {
                name = '0' + name;
            } else if (name.length() == 1 && name.charAt(0) <= 'H' && name.charAt(0) >= 'A') {
                name = "dx" + name.toLowerCase();
            }
            try {
                buildBoard(b, "0\t0\t" + name);
            } catch (BoardException e) {
                e = null;
                ErrorDialog.dataWarning(new BadDataReport("Unable to find board", name, e));
            }
        }
        if (enableDeluxe) {
            b.setMagnification(3.0);
            ((HexGrid) b.getGrid()).setSnapScale(2);
        }
        return b;
    }

    public void buildBoard(ASLBoard b, String bd) throws BoardException {
        StringTokenizer st2 = new StringTokenizer(bd, "\t\n");
        try {
            b.relativePosition().move(Integer.parseInt(st2.nextToken()), Integer.parseInt(st2.nextToken()));
            String baseName = st2.nextToken();
            String unReversedBoardName;
            if (baseName.startsWith("r")) {
                if (baseName.equalsIgnoreCase("r")) {
                    // board r or R, this is ok
                    unReversedBoardName = baseName;
                    b.setReversed(false);
                } else {
                    // board rX, this is really X
                    // Red Barricades (RB) and Ruweisat Ridge (RR) should not have gotten here
                    unReversedBoardName = baseName.substring(1);
                    b.setReversed(true);
                }
            } else {
                unReversedBoardName = baseName;
                b.setReversed(false);
            }
            File f;
            f = new File(boardDir, "bd" + unReversedBoardName);
            if (!f.exists()) {

                throw new BoardException("Unable to find board " + baseName);
            }
            b.initializeFromArchive(f);
            if (b.getVASLBoardArchive() == null ){
                throw new BoardException("Unable to create board " + baseName);
            }
        } catch (Exception eParse) {
            throw new BoardException(eParse.getMessage(), eParse);
        }
        try {
            int x1 = Integer.parseInt(st2.nextToken());
            int y1 = Integer.parseInt(st2.nextToken());
            int wid = Integer.parseInt(st2.nextToken());
            int hgt = Integer.parseInt(st2.nextToken());
            b.setCropBounds(new Rectangle(x1, y1, wid, hgt));
        } catch (Exception e) {
            b.setCropBounds(new Rectangle(0, 0, -1, -1));
        }

        if (bd.contains("VER")) {
            StringTokenizer st = new StringTokenizer(bd.substring(bd.indexOf("VER") + 4), "\t");
            if (st.countTokens() >= 1) {
                String reqver = st.nextToken();
                if (reqver.compareTo(b.getVersion()) != 0)
                    GameModule.getGameModule().warn("This game was saved with board " + b.getName() + " v" + reqver + ". You are using v" + b.getVersion());
            }
        }

        while (bd.contains("OVR")) {
            bd = bd.substring(bd.indexOf("OVR") + 4);
            try {
                b.addOverlay(new Overlay(bd, b, new File(getBoardDir(), "overlays")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (bd.contains("SSR")) {
            b.setTerrain(bd.substring(bd.indexOf("SSR") + 4));
        }
        if (bd.contains("ZOOM")) {

            // occasionally ZOOM is encoded multiple times - ignore this error as it's very rare
            // and ignoring zoom has no real side effects
            try {
                b.setMagnification(Double.parseDouble(bd.substring(bd.indexOf("ZOOM") + 5)));
            }
            catch (Exception e) {
                // bury
            }
        }

        if (bd.contains("FH")) {

            // if board is cropped to a full hex must set flag
            try {
                b.nearestFullRow = true;
            }
            catch (Exception e) {
                // bury
            }
        }
    }

    protected void addColumn() {
        slotPanel.setLayout(new GridLayout(ny, ++nx));
        for (int j = 0; j < ny; ++j) {
            slotPanel.add(new ASLBoardSlot(this), (j + 1) * nx - 1);
        }
        slotPanel.revalidate();
    }

    protected void addRow() {
        slotPanel.setLayout(new GridLayout(++ny, nx));
        for (int i = 0; i < nx; ++i) {
            slotPanel.add(new ASLBoardSlot(this), -1);
        }
        slotPanel.revalidate();
    }

    public void reset() {
        super.reset();
        removeAllBoards();
        slotPanel.add(new ASLBoardSlot(this), 0);
        terrain.reset();
        if (setupControls == null) {
            setupControls = new SetupControls();
        }
        setupControls.reset();
    }

    public void actionPerformed(ActionEvent e) {

        String label = e.getActionCommand();
        if ("Add overlays".equals(label)) {
            Overlayer o;
            Dialog d = getDialogContainer();
            o = d == null ? new Overlayer(getFrameContainer()) : new Overlayer(d);
            o.setLocationRelativeTo(controls.getTopLevelAncestor());
            o.setVisible(true);
        } else if ("Crop boards".equals(label)) {
            Cropper crop;
            Dialog d = getDialogContainer();
            crop = d == null ? new Cropper(getFrameContainer()) : new Cropper(d);
            crop.setLocationRelativeTo(controls.getTopLevelAncestor());
            crop.setVisible(true);
        } else if ("Terrain SSR".equals(label)) {
            currentBoards = getBoardsFromControls();
            terrain.setup(currentBoards);
        } else {
            super.actionPerformed(e);
        }
    }

    protected Dialog getDialogContainer() {
        Container top = controls.getTopLevelAncestor();
        return (Dialog) (top instanceof Dialog ? top : null);
    }

    protected Frame getFrameContainer() {
        Container top = controls.getTopLevelAncestor();
        return (Frame) (top instanceof Frame ? top : null);
    }

    public BoardSlot match(String s) throws BoardException {
        if ("".equals(s)) {
            if (slotPanel.getComponentCount() == 1)
                return (BoardSlot) slotPanel.getComponent(0);
            else
                throw new BoardException("No Such Board");
        }
        for (int i = 0; i < nx; ++i)
            for (int j = 0; j < ny; ++j) {
                BoardSlot b = getSlot(i + nx * j);
                if (b.getBoard() == null)
                    continue;
                if (b.getBoard().getName().equalsIgnoreCase(s))
                    return (b);
            }
        throw new BoardException("No Such Board");
    }
    /*public BoardSlot newmatch (int x) throws BoardException {
        //BoardSlot b = getSlot(x);
        if (nx == 1 || ny ==1) {return getSlot(x);}
        for (int i = 0; i < nx; ++i)
            for (int j = 0; j < ny; ++j) {
                    if (x == i + nx * j) {return getSlot(i + nx * j);}
            }
        throw new BoardException("No Such Board");
    }*/
    public BoardSlot newmatch (int y) throws BoardException {
        //BoardSlot b = getSlot(x);

        List<Board> boards = getBoardsFromControls();
        Iterator it = boards.iterator();
        for (int x = 0; x < boards.size(); x++) {
            ASLBoard b = (ASLBoard) it.next();
            if (x == y) {
                for (int i = 0; i < boards.size(); ++i) {

                    ASLBoard c = (ASLBoard) getSlot(i).getBoard();
                    if (b == c) {
                        return getSlot(i);
                    }

                }
            } //throw new BoardException("No Such Board");
            //}
            //}


            //if (nx == 1 || ny ==1) {return getSlot(x);}

        }
        return null;
    }
    public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) {
        return doc.createElement(getClass().getName());
    }

    public Component getControls() {
        reset();
        return setupControls;
    }

    private class SetupControls extends JPanel {
        private DirectoryConfigurer dirConfig;

        public SetupControls() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            final DirectoryConfigurer pref = (DirectoryConfigurer) GameModule.getGameModule().getPrefs().getOption(BOARD_DIR);
            dirConfig = new DirectoryConfigurer(null, pref.getName());
            dirConfig.setValue(pref.getFileValue());
            add(dirConfig.getControls());

            JCheckBox deluxe = new JCheckBox("Deluxe-size hexes");
            deluxe.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    enableDeluxe = e.getStateChange() == ItemEvent.SELECTED;
                    int n = 0;
                    ASLBoardSlot slot;
                    while ((slot = (ASLBoardSlot) getSlot(n++)) != null) {
                        if (slot.getBoard() != null) {
                            slot.getBoard().setMagnification(enableDeluxe ? 3.0 : 1.0);
                            slot.setSize(slot.getPreferredSize());
                            slot.revalidate();
                            slot.repaint();
                        }
                    }
                }
            });
            add(deluxe);

            // check-box for DB flag
/*
            JCheckBox db = new JCheckBox("Enable double blind");
            if(DoubleBlindViewer.doubleBlindViewer != null){
                db.setSelected(DoubleBlindViewer.doubleBlindViewer.isEnabled());
            }
            db.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    enableDB = e.getStateChange() == ItemEvent.SELECTED;
                }
            });
*/
            // add(db);

            //setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))

            add(controls);
            dirConfig.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    pref.setFrozen(true);
                    pref.setValue(evt.getNewValue());
                    pref.setFrozen(false);
                    setBoardDir((File) evt.getNewValue());
                }
            });
            pref.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    pref.setFrozen(true);
                    dirConfig.setValue(evt.getNewValue());
                    pref.setFrozen(false);
                }
            });
        }

        public void reset() {
        }
    }

    protected class Cropper extends JDialog implements ActionListener {
        private JTextField row1, row2, coord1, coord2;

        private JComboBox<String> bdName;

        private JRadioButton halfrow, fullrow;

        protected Cropper(Frame owner) {
             super(owner, true);
            init();
        }

        protected Cropper(Dialog owner) {
            super(owner, true);
            init();
        }

        private void init() {

            row1 = new JTextField(2);
            row1.addActionListener(this);
            row2 = new JTextField(2);
            row2.addActionListener(this);
            coord1 = new JTextField(2);
            coord1.addActionListener(this);
            coord2 = new JTextField(2);
            coord2.addActionListener(this);
            halfrow = new JRadioButton("Crop to middle of hex row");
            fullrow = new JRadioButton("Crop to nearest full hex row");
            fullrow.setSelected(true);
            ButtonGroup bg = new ButtonGroup();
            bg.add(halfrow);
            bg.add(fullrow);
            halfrow.setSelected(true);

            getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
            Box box = Box.createHorizontalBox();
            JLabel l = new JLabel("Board:");
            box.add(l);

            // code added by DR to enable multiple instances of same board to be cropped
            ArrayList<BoardNames> boardlist = new ArrayList<BoardNames>();
            int x = 0;
            String bdlist[] = getBoards();
            for (String nextboardname : bdlist) {
                boardlist.add(new BoardNames(nextboardname, Integer.toString(x)));
                x+=1;
            }
            BoardNames[] bx = new BoardNames[boardlist.size()];
            Iterator it = boardlist.iterator();
            for(int y = 0; y < bx.length; y++) {
                BoardNames newboard = (BoardNames) it.next();
                bx[y] = newboard;
            }
            bdName = new JComboBox(bx);
            // end of changes


            box.add(bdName);

            getContentPane().add(box);
            box = Box.createHorizontalBox();
            box.add(new JLabel("Hexrows:"));
            box.add(row1);
            box.add(new JLabel("-"));
            box.add(row2);
            getContentPane().add(box);
            box = Box.createHorizontalBox();
            box.add(new JLabel("Coords:"));
            box.add(coord1);
            box.add(new JLabel("-"));
            box.add(coord2);
            getContentPane().add(box);
            box = Box.createVerticalBox();
            box.add(halfrow);
            box.add(fullrow);
            getContentPane().add(box);
            box = Box.createHorizontalBox();
            JButton b = new JButton("Crop");
            b.addActionListener(this);
            box.add(b);
            b = new JButton("Done");
            b.addActionListener(this);
            box.add(b);
            getContentPane().add(box);
            pack();
            setLocation(Toolkit.getDefaultToolkit().getScreenSize().width / 2 - getWidth() / 2, Toolkit.getDefaultToolkit().getScreenSize().height / 2 - getHeight()
                    / 2);
        }

        /**
         * @return an array of boards in the map
         */
        private String[] getBoards() {

            List<Board> boards = getBoardsFromControls();
            String[] b = new String[boards.size()];
            Iterator it = boards.iterator();
            for(int x = 0; x < b.length; x++) {
                b[x] = ((ASLBoard) it.next()).getCommonName();
            }

            return b;
        }

        public void clear() {
            row1.setText("");
            row2.setText("");
            coord1.setText("");
            coord2.setText("");
        }

        public void actionPerformed(ActionEvent e) {
            if ("Done".equals(e.getActionCommand())) {
                setVisible(false);
                return;
            }
            try {
                BoardSlot b = newmatch(bdName.getSelectedIndex());
                ((ASLBoard) b.getBoard()).crop(row1.getText().toLowerCase().trim(), row2.getText().toLowerCase().trim(), coord1.getText().toLowerCase().trim(), coord2
                        .getText().toLowerCase().trim(), fullrow.isSelected());
                b.invalidate();
                b.repaint();
                row1.setText("");
                row2.setText("");
                coord1.setText("");
                coord2.setText("");
            } catch (Exception ex) {
            }
        }
    }
    // code added by DR to enable multiple instances of same board to be cropped, have Overlays, and apply SSR
    public class BoardNames {
        String bdname;
        String bdlistid;
        public BoardNames(String nameinput, String idinput){
            bdname = nameinput;
            bdlistid = idinput;
        }
        public String toString(){return bdname;}
    }
    protected class Overlayer extends JDialog implements ActionListener {

        private JTextField hex1, hex2, ovrName;
        private JLabel status;

        private JComboBox<String> bdName;

        protected Overlayer(Frame f) {
            super(f, true);
            setTitle("Overlays");
            init();
        }

        protected Overlayer(Dialog d) {
            super(d, true);
            setTitle("Overlays");
            init();
        }

        private void init() {
            hex1 = new JTextField(2);
            hex1.addActionListener(this);
            hex2 = new JTextField(2);
            hex2.addActionListener(this);
            ovrName = new JTextField(2);
            ovrName.addActionListener(this);
            status = new JLabel("Enter blank hexes to delete.", JLabel.CENTER);
            getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
            getContentPane().add(status);
            Box box = Box.createHorizontalBox();
            Box vBox = Box.createVerticalBox();
            vBox.add(new JLabel("Overlay"));
            vBox.add(ovrName);
            box.add(vBox);
            vBox = Box.createVerticalBox();
            vBox.add(new JLabel("Board"));

            // code added by DR to enable multiple instances of same board to have ovelays
            ArrayList<BoardNames> boardlist = new ArrayList<BoardNames>();
            int x = 0;
            String bdlist[] = getBoards();
            for (String nextboardname : bdlist) {
                boardlist.add(new BoardNames(nextboardname, Integer.toString(x)));
                x=+1;
            }
            BoardNames[] bx = new BoardNames[boardlist.size()];
            Iterator it = boardlist.iterator();
            for(int y = 0; y < bx.length; y++) {
                BoardNames newboard = (BoardNames) it.next();
                bx[y] = newboard;
            }
            bdName = new JComboBox(bx);
            // end of changes
            vBox.add(bdName);

            box.add(vBox);
            vBox = Box.createVerticalBox();
            vBox.add(new JLabel("Hex 1"));
            vBox.add(hex1);
            box.add(vBox);
            vBox = Box.createVerticalBox();
            vBox.add(new JLabel("Hex 2"));
            vBox.add(hex2);
            box.add(vBox);
            getContentPane().add(box);
            box = Box.createHorizontalBox();
            JButton b = new JButton("Add");
            b.addActionListener(this);
            box.add(b);
            b = new JButton("Done");
            b.addActionListener(this);
            box.add(b);
            getContentPane().add(box);
            pack();
            setLocation(Toolkit.getDefaultToolkit().getScreenSize().width / 2 - getSize().width / 2, Toolkit.getDefaultToolkit().getScreenSize().height / 2
                    - getSize().height / 2);
        }

        public void clear() {
            status.setText("Enter blank hexes to delete.");
            hex1.setText("");
            hex2.setText("");
            ovrName.setText("");
        }

        public void actionPerformed(ActionEvent e) {
            if ("Done".equals(e.getActionCommand())) {
                setVisible(false);
                return;
            }
            try {
                BoardSlot b = newmatch(bdName.getSelectedIndex());
                if (b==null){
                    // fail silently
                    clear();
                    status.setText("Missing board: fix Map Config.");
                    return;
                }
                status.setText(((ASLBoardSlot) (b)).addOverlay(ovrName.getText().toLowerCase(), hex1.getText().toLowerCase(), hex2.getText().toLowerCase()));
                ovrName.setText("");
                hex1.setText("");
                hex2.setText("");
            } catch (BoardException excep) {
                status.setText(excep.getMessage());
            }
        }

        /**
         * @return an array of boards in the map
         */
        private String[] getBoards() {

            List<Board> boards = getBoardsFromControls();
            String[] b = new String[boards.size()];
            Iterator it = boards.iterator();
            for(int x = 0; x < b.length; x++) {
                b[x] = ((ASLBoard) it.next()).getCommonName();
            }

            return b;
        }
    }

    protected class TerrainEditor extends JDialog implements ActionListener {

        private Vector optionGroup = new Vector();
        private JList optionList;
        private JTextField status;
        private JPanel options;
        private CardLayout card;
        private Vector basicOptions = new Vector();
        private TerrainMediator mediator = new TerrainMediator();
        private Vector boards;
        protected JButton apply, reset, done;

        private JComboBox<BoardNames> bdName = new JComboBox<BoardNames>();

        protected TerrainEditor() {
            super((Frame) null, true);
            setTitle("Terrain Transformations");
            boards = new Vector();
            getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
            status = new JTextField("Leave board number blank to apply to all boards:  ");
            status.setMaximumSize(new Dimension(status.getMaximumSize().width, status.getPreferredSize().height));
            status.setEditable(false);
            card = new CardLayout();
            options = new JPanel();
            options.setLayout(card);
            optionList = new JList(new DefaultListModel());
            optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            optionList.setVisibleRowCount(4);
            optionList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
                public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                    showOption();
                }
            });
            getContentPane().add(status);
            Box box = Box.createHorizontalBox();
            JPanel p = new JPanel();
            p.setLayout(new GridLayout(4, 1));

            JPanel pp = new JPanel();
            pp.setLayout(new GridBagLayout());
            pp.add(new JLabel("Board "));
            pp.add(bdName);

            p.add(pp);
            apply = new JButton("Apply");
            apply.addActionListener(this);
            reset = new JButton("Reset");
            reset.addActionListener(this);
            done = new JButton("Done");
            done.addActionListener(this);
            p.add(apply);
            p.add(reset);
            p.add(done);
            box.add(p);
            box.add(new JScrollPane(optionList));
            box.add(options);
            getContentPane().add(box, -1);
            pack();
        }

        private void showOption() {
            card.show(options, (String) optionList.getSelectedValue());
        }

        public ASLBoardPicker getBoardPicker() {
            return ASLBoardPicker.this;
        }

        public void setup(Collection<Board> boardList) {
            Box box = Box.createVerticalBox();
            for (int i = 0; i < 4; ++i) {
                TransformOption opt = new TransformOption();
                box.add(opt.getComponent());
                optionGroup.addElement(opt);
            }
            addOption("Transformations", box);
            boards.removeAllElements();
            String version = "";
            int nboards = 0;
            for (Board board : boardList) {
                ASLBoard b = (ASLBoard) board;
                boards.addElement(b);
                if (b != null) {

                    readOptions(b.getVASLBoardArchive().getBasicNodes(), b.getVASLBoardArchive().getOptionNodes());

                    for (Enumeration oEnum = b.getOverlays(); oEnum.hasMoreElements(); ) {
                        Overlay o = (Overlay) oEnum.nextElement();
                        if (!(o instanceof SSROverlay)) {
                            try (InputStream in = GameModule.getGameModule().getDataArchive().getInputStream(OVERLAY_SSR_CONTROL_FILE_NAME)) {
                                SSRControlsFile ssrControlsFile = new SSRControlsFile(in, o.getName());
                                readOptions(ssrControlsFile.getBasicNodes(), ssrControlsFile.getOptionNodes());

                            } catch (IOException ignore) {
                            }
                        }
                    }
                    nboards++;
                    version = version.concat(b.getName() + " (ver " + b.version + ") ");
                }
            }
            switch (nboards) {
                case 0:
                    warn("No boards loaded");
                    break;
                case 1:
                    warn("Loaded board " + version);
                    break;
                default:
                    warn("Loaded boards " + version + " (leave board number blank to apply to all)");
            }
            pack();
            setVisible(true);
        }

        public void warn(String s) {
            status.setText(s);
            Container c = this;
            while (c.getParent() != null)
                c = c.getParent();
            c.invalidate();
            c.validate();
            c.repaint();
        }

        public void readOptions(NodeList basicNodes, NodeList optionNodes) {
            try {
                if (basicNodes != null && basicNodes.getLength() > 0) {
                    basicNodes = (basicNodes.item(0)).getChildNodes();
                    Box basicPanel = Box.createHorizontalBox();
                    for (int j = 0; j < basicNodes.getLength(); ++j) {
                        if (basicNodes.item(j).getNodeType() == Node.ELEMENT_NODE) {
                            Element el2 = (Element) basicNodes.item(j);
                            TerrainOption opt = new TerrainOption(mediator, el2);
                            ((Container) opt.getComponent()).setLayout(new BoxLayout((Container) opt.getComponent(), BoxLayout.Y_AXIS));
                            basicPanel.add(opt.getComponent());
                            basicOptions.addElement(opt);
                        }
                    }
                    getContentPane().add(basicPanel, 0);
                }
                if(optionNodes != null){

                    for (int i = 0; i < optionNodes.getLength(); ++i) {
                        Element el = (Element) optionNodes.item(i);
                        Box box = Box.createVerticalBox();
                        NodeList nl = el.getChildNodes();
                        for (int j = 0; j < nl.getLength(); ++j) {
                            if (nl.item(j).getNodeType() == Node.ELEMENT_NODE) {
                                Element el2 = (Element) nl.item(j);
                                TerrainOption opt = new TerrainOption(mediator, el2);
                                box.add(opt.getComponent());
                                optionGroup.addElement(opt);
                            }
                        }
                        addOption(el.getAttribute("name"), box);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error processing SSR control file: " + e.getMessage());
            }
        }

        private void addOption(String name, Component c) {
            for (int i = 0; i < optionList.getModel().getSize(); ++i) {
                if (optionList.getModel().getElementAt(i).equals(name))
                    return;
            }
            ((DefaultListModel) optionList.getModel()).addElement(name);
            options.add(c, name);
            card.addLayoutComponent(c, name);
        }

        private void reset(Vector opts) {
            for (int i = 0; i < opts.size(); ++i) {
                ((TerrainOption) opts.elementAt(i)).reset();
            }
        }

        public void reset() {
            reset(basicOptions);
            reset(optionGroup);
        }

        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof JButton) {
                if ("Apply".equals(e.getActionCommand())) {
                    warn("Working ...");
                    String opText = optionText();
                    String bText = basicText();
                    try {
                        // changes by DR to support Terrain SSR when using multiple boards of same name
                        if (bdName.getSelectedIndex() == 0 ) { // all boards
                            for (int t =0; t < bdName.getItemCount()-1; ++t) {
                                ASLBoardSlot slot = (ASLBoardSlot) newmatch(t);
                                // check first for missing boards
                                if (slot == null) {
                                    //ensure that board config requirement is respected
                                    warn("Incorrect Board Configuration: must complete X by X rectangle - use bdNull");
                                    return;
                                }
                            }
                            // now do same loop to apply transform
                            for (int t =0; t < bdName.getItemCount()-1; ++t) {
                                ASLBoardSlot slot = (ASLBoardSlot) newmatch(t);
                                    slot.setTerrain(slot.getTerrain() + '\t' + optionRules());
                                    if (slot.getBoard() != null) {
                                        ((ASLBoard) slot.getBoard()).setTerrain(basicRules() + slot.getTerrain());
                                        slot.repaint();
                                    }
                            }
                        } else { // specific board chosen
                            ASLBoardSlot slot = (ASLBoardSlot) newmatch(bdName.getSelectedIndex()-1);
                            slot.setTerrain(slot.getTerrain() + '\t' + optionRules());
                            if (slot.getBoard() != null) {
                                ((ASLBoard) slot.getBoard()).setTerrain(basicRules() + slot.getTerrain());
                                slot.repaint();
                            }
                        }
                        if (opText.length() > 0) {
                            bText = bText.length() == 0 ? opText : bText + ", " + opText;
                        }

                    } catch (BoardException e1) {
                        e1.printStackTrace();
                        warn(e1.getMessage());
                    }
                    reset(optionGroup);
                } else if ("Reset".equals(e.getActionCommand())) {
                    reset();
                    try {
                        int n = 0;
                        while (true) {
                            ASLBoardSlot slot = null;
                            try {
                                slot = (ASLBoardSlot) getSlot(n++);
                            } catch (Exception noSuchSlot) {
                                break;
                            }
                            slot.setTerrain("");
                            try {
                                ((ASLBoard) slot.getBoard()).setTerrain("");
                            } catch (Exception e2) {
                            }
                            slot.repaint();
                        }
                        warn("Back to normal");
                    } catch (Exception resetError) {
                        warn(resetError.getMessage());
                    }
                } else if ("Done".equals(e.getActionCommand())) {
                    reset();
                    setVisible(false);
                }
            }
        }

        public String basicRules() {
            String s = "";
            for (int i = 0; i < basicOptions.size(); ++i) {
                s = s.concat(((TerrainOption) basicOptions.elementAt(i)).getRule());
            }
            return s;
        }

        public String basicText() {
            String s = "";
            for (int i = 0; i < basicOptions.size(); ++i) {
                s = s.concat(((TerrainOption) basicOptions.elementAt(i)).getText());
            }
            return s;
        }

        public String optionRules() {
            String s = "";
            for (int i = 0; i < optionGroup.size(); ++i) {
                // s = s.concat(((TerrainOption) optionGroup.elementAt(i)).getRule());
                if (s.length() > 0 && ((TerrainOption) optionGroup.elementAt(i)).getRule().length() > 0) {
                    s = s.concat("\t").concat(((TerrainOption) optionGroup.elementAt(i)).getRule());
                } else {
                    s = s.concat(((TerrainOption) optionGroup.elementAt(i)).getRule());
                }
            }
            return s;
        }

        public String optionText() {
            String s = "";
            for (int i = 0; i < optionGroup.size(); ++i) {
                s = s.concat(((TerrainOption) optionGroup.elementAt(i)).getText());
            }
            return s.endsWith(", ") ? s.substring(0, s.length() - 2) : s;
        }

        protected class TerrainOption {
            protected Component comp;
            protected JPanel panel = new JPanel();
            private Vector active;
            private String text;
            private String rule;
            private String name;
            protected Hashtable rules = new Hashtable();
            protected Hashtable texts = new Hashtable();
            protected Vector defaults = new Vector();
            protected PropertyChangeSupport propChange = new PropertyChangeSupport(this);

            protected TerrainOption() {
            }

            public TerrainOption(TerrainMediator mediator, Element e) {
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                name = e.getAttribute("name");
                if (e.getElementsByTagName("Source").getLength() > 0)
                    mediator.addSource(this);
                if (e.getTagName().equals("Menu")) {
                    comp = new JComboBox();
                    ((JComboBox) comp).addItemListener(new ItemListener() {
                        public void itemStateChanged(ItemEvent evt) {
                            invalidate();
                            propChange.firePropertyChange("active", null, getActive());
                        }
                    });
                    comp.setMaximumSize(new Dimension(comp.getMaximumSize().width, comp.getPreferredSize().height));
                } else if (e.getTagName().equals("ScrollList")) {
                    comp = new JList(new DefaultListModel());
                    ((JList) comp).addListSelectionListener(new ListSelectionListener() {
                        public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                            invalidate();
                            propChange.firePropertyChange("active", null, getActive());
                        }
                    });
                } else if (e.getTagName().equals("Checkbox")) {
                    comp = new JCheckBox();
                    ((JCheckBox) comp).addItemListener(new ItemListener() {
                        public void itemStateChanged(ItemEvent evt) {
                            invalidate();
                            propChange.firePropertyChange("active", null, getActive());
                        }
                    });
                } else {
                    throw new RuntimeException("Unrecognized SSR component type " + e.getTagName());
                }
                NodeList n = e.getElementsByTagName("entry");
                for (int i = 0; i < n.getLength(); ++i) {
                    Element entry = (Element) n.item(i);
                    String entryName = entry.getAttribute("name");
                    rules.put(entryName, entry.getAttribute("rule").replace(',', '\t'));
                    texts.put(entryName, entry.getAttribute("text"));
                    if (comp instanceof JCheckBox) {
                        ((JCheckBox) comp).setText(entryName);
                    } else if (comp instanceof JList) {
                        ((DefaultListModel) ((JList) comp).getModel()).addElement(entryName);
                    } else if (comp instanceof JComboBox) {
                        ((DefaultComboBoxModel) ((JComboBox) comp).getModel()).addElement(entryName);
                        if (entry.getAttribute("default").length() > 0) {
                            defaults.addElement(entryName);
                        }
                    }
                    NodeList targList = entry.getElementsByTagName("Target");
                    for (int targIndex = 0; targIndex < targList.getLength(); ++targIndex) {
                        Vector activate = null;
                        Vector deactivate = null;
                        String sourceName = null;
                        Element targ = (Element) targList.item(targIndex);
                        sourceName = targ.getAttribute("sourceName");
                        NodeList nl = targ.getElementsByTagName("activate");
                        if (nl.getLength() > 0) {
                            activate = new Vector();
                            for (int j = 0; j < nl.getLength(); ++j) {
                                activate.addElement(((Element) nl.item(j)).getAttribute("sourceProperty"));
                            }
                        }
                        nl = targ.getElementsByTagName("deactivate");
                        if (nl.getLength() > 0) {
                            deactivate = new Vector();
                            for (int j = 0; j < nl.getLength(); ++j) {
                                deactivate.addElement(((Element) nl.item(j)).getAttribute("sourceProperty"));
                            }
                        }
                        if (activate != null || deactivate != null) {
                            mediator.addTarget(this, entryName, sourceName, activate, deactivate);
                        }
                    }
                }
                if (!(comp instanceof JCheckBox) || !getName().equals(((JCheckBox) comp).getText())) {
                    panel.add(new JLabel(getName()));
                }
                if (comp instanceof JList) {
                    panel.add(new JScrollPane(comp));
                } else {
                    panel.add(comp);
                }
                panel.setVisible(e.getElementsByTagName("invisible").getLength() == 0);
            }

            public Component getComponent() {
                return panel;
            }

            public void reset() {
                for (Enumeration e = getActive().elements(); e.hasMoreElements(); ) {
                    activate((String) e.nextElement(), false);
                }
                for (Enumeration e = defaults.elements(); e.hasMoreElements(); ) {
                    activate((String) e.nextElement(), true);
                }
            }

            public void activate(String val, boolean isActive) {
                invalidate();
                if (comp instanceof JCheckBox) {
                    ((JCheckBox) comp).setSelected(isActive && ((JCheckBox) comp).getText().equals(val));
                } else if (comp instanceof JComboBox) {
                    if (val == null || !isActive) {
                        if (defaults.size() > 0) {
                            ((JComboBox) comp).setSelectedItem(defaults.elementAt(0));
                        } else {
                            ((JComboBox) comp).setSelectedIndex(0);
                        }
                    } else {
                        ((JComboBox) comp).setSelectedItem(val);
                    }
                } else if (comp instanceof JList) {
                    ListModel model = ((JList) comp).getModel();
                    for (int j = 0; j < model.getSize(); ++j) {
                        if (model.getElementAt(j).equals(val)) {
                            if (isActive) {
                                ((JList) comp).addSelectionInterval(j, j);
                            } else {
                                ((JList) comp).removeSelectionInterval(j, j);
                            }
                            break;
                        }
                    }
                }
                propChange.firePropertyChange("active", null, getActive());
            }

            public String getName() {
                return name;
            }

            private void invalidate() {
                active = null;
                rule = null;
                text = null;
            }

            public Vector getActive() {
                if (active == null) {
                    active = new Vector();
                    if (comp instanceof JCheckBox) {
                        active.addElement(((JCheckBox) comp).isSelected() ? ((JCheckBox) comp).getText() : "");
                    } else if (comp instanceof JComboBox) {
                        active.addElement(((JComboBox) comp).getSelectedItem());
                    } else if (comp instanceof JList) {
                        List<Object> val = ((JList) comp).getSelectedValuesList();
                        for (Object o : val ) {
                            active.addElement(o);
                        }
                    }
                }
                return active;
            }

            public String getRule() {
                if (rule == null) {
                    rule = "";
                    for (Enumeration e = getActive().elements(); e.hasMoreElements(); ) {
                        String s = (String) rules.get(e.nextElement());
                        if (s != null && s.length() > 0)
                            rule = rule.concat(s + '\t');
                    }
                }
                return rule;
            }

            public String getText() {
                if (text == null) {
                    text = "";
                    for (Enumeration e = getActive().elements(); e.hasMoreElements(); ) {
                        String s = (String) texts.get(e.nextElement());
                        if (s != null && s.length() > 0)
                            text = text.concat(s + ", ");
                    }
                }
                return text;
            }

            public void addPropertyChangeListener(PropertyChangeListener l) {
                propChange.addPropertyChangeListener(l);
            }
        }

        protected abstract class TerrainOptions extends JPanel {
            protected Vector choices = new Vector();
            protected Vector checkboxes = new Vector();
            protected Vector lists = new Vector();
            protected Hashtable translations;
            protected Hashtable plain;

            TerrainOptions() {
                translations = new Hashtable();
                plain = new Hashtable();
                translations.put("Normal", "");
                plain.put("Normal", "");
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            }

            void addComponent(String name, Component c) {
                Box box = Box.createHorizontalBox();
                box.add(new JLabel(name));
                box.add(c);
                add(box);
                if (c instanceof JCheckBox) {
                    checkboxes.addElement(c);
                } else if (c instanceof JComboBox) {
                    choices.addElement(c);
                } else if (c instanceof JList) {
                    lists.addElement(c);
                }
            }

            void addToChoice(JComboBox c, String text, String translation, String plainName) {
                ((DefaultComboBoxModel) c.getModel()).addElement(text);
                setTranslation(text, translation, plainName);
            }

            void setTranslation(String text, String translation, String plainName) {
                if (translation.length() > 0) {
                    StringTokenizer st = new StringTokenizer(translation, ",");
                    while (st.hasMoreTokens()) {
                        translations.put(text, st.nextToken() + "\t");
                    }
                } else
                    translations.put(text, "");
                if (plainName.length() > 0)
                    plain.put(text, plainName + ", ");
                else
                    plain.put(text, "");
            }

            public void reset() {
                JCheckBox cb;
                JComboBox c;
                JList l;
                for (int i = 0; i < checkboxes.size(); ++i) {
                    cb = (JCheckBox) checkboxes.elementAt(i);
                    cb.setSelected(false);
                }
                for (int i = 0; i < choices.size(); ++i) {
                    c = (JComboBox) choices.elementAt(i);
                    c.setSelectedIndex(0);
                }
                for (int i = 0; i < lists.size(); ++i) {
                    l = (JList) lists.elementAt(i);
                    l.setSelectedIndex(-1);
                }
            }

            public String toString() {
                String s = "";
                JCheckBox cb;
                JComboBox c;
                JList l;
                for (int i = 0; i < checkboxes.size(); ++i) {
                    cb = (JCheckBox) checkboxes.elementAt(i);
                    if (cb.isSelected())
                        s = s.concat((String) translations.get(cb.getText()));
                }
                for (int i = 0; i < choices.size(); ++i) {
                    c = (JComboBox) choices.elementAt(i);
                    s = s.concat((String) translations.get(c.getSelectedItem()));
                }
                for (int i = 0; i < lists.size(); ++i) {
                    l = (JList) lists.elementAt(i);
                    for (int n = 0; n < l.getModel().getSize(); ++n) {
                        if (l.isSelectedIndex(n)) {
                            s = s.concat((String) translations.get(l.getModel().getElementAt(n)));
                        }
                    }
                }
                return s;
            }

            public String plainText() {
                String s = "";
                JCheckBox cb;
                JComboBox c;
                JList l;
                for (int i = 0; i < checkboxes.size(); ++i) {
                    cb = (JCheckBox) checkboxes.elementAt(i);
                    if (cb.isSelected())
                        s = s.concat((String) plain.get(cb.getText()));
                }
                for (int i = 0; i < choices.size(); ++i) {
                    c = (JComboBox) choices.elementAt(i);
                    s = s.concat((String) plain.get(c.getSelectedItem()));
                }
                for (int i = 0; i < lists.size(); ++i) {
                    l = (JList) lists.elementAt(i);
                    for (int n = 0; n < l.getModel().getSize(); ++n) {
                        if (l.isSelectedIndex(n)) {
                            s = s.concat((String) plain.get(l.getModel().getElementAt(n)));
                        }
                    }
                }
                return s;
            }
        }

        protected class TerrainMediator implements PropertyChangeListener {
            private Hashtable targets = new Hashtable();
            private Hashtable sources = new Hashtable();

            TerrainMediator() {
            }

            public void addSource(TerrainOption opt) {
                opt.addPropertyChangeListener(this);
                sources.put(opt.getName(), opt);
            }

            public void addTarget(TerrainOption targ, String targetProp, String sourceName, Vector activate, Vector deactivate) {
                getTargets(sourceName).addElement(new Target(targ, targetProp, activate, deactivate));
            }

            public void propertyChange(PropertyChangeEvent evt) {
                Vector v = getTargets(((TerrainOption) evt.getSource()).getName());
                for (Enumeration e = v.elements(); e.hasMoreElements(); ) {
                    ((Target) e.nextElement()).sourceStateChanged((Vector) evt.getNewValue());
                }
            }

            public void itemStateChanged(ItemEvent e) {
                sourceStateChanged((String) sources.get(e.getSource()), getSourceSelection((Component) e.getSource()));
            }

            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                sourceStateChanged((String) sources.get(e.getSource()), getSourceSelection((Component) e.getSource()));
            }

            private Vector getTargets(String srcName) {
                Vector v = (Vector) targets.get(srcName);
                if (v == null) {
                    v = new Vector();
                    targets.put(srcName, v);
                }
                return v;
            }

            private Vector getSourceSelection(Component source) {
                Vector v = new Vector();
                if (source instanceof JCheckBox) {
                    if (((JCheckBox) source).isSelected()) {
                        v.addElement(((JCheckBox) source).getText());
                    }
                } else if (source instanceof JComboBox) {
                    Object o = ((JComboBox) source).getSelectedItem();
                    if (o != null)
                        v.addElement(o);
                } else if (source instanceof JList) {
                    List<Object> objects = ((JList) source).getSelectedValuesList();
                    for (Object o : objects) {
                        v.addElement(o);
                    }
                } else {
                    throw new RuntimeException("Illegal source component " + source);
                }
                return v;
            }

            private void sourceStateChanged(String srcName, Vector active) {
                Vector v = getTargets(srcName);
                if (v != null) {
                    for (int i = 0; i < v.size(); ++i) {
                        ((Target) v.elementAt(i)).sourceStateChanged(active);
                    }
                }
            }
        }

        protected class Target {
            private TerrainOption target;
            private String targetProperty;
            private Vector activators;
            private Vector deactivators;

            Target(TerrainOption opt, String prop, Vector activate, Vector deactivate) {
                activators = activate;
                deactivators = deactivate;
                targetProperty = prop;
                target = opt;
            }

            public void sourceStateChanged(Vector active) {
                if (activators != null) {
                    for (int i = 0; i < activators.size(); ++i) {
                        if (active.contains(activators.elementAt(i))) {
                            target.activate(targetProperty, true);
                            return;
                        }
                    }
                }
                if (deactivators != null) {
                    for (int i = 0; i < deactivators.size(); ++i) {
                        if (active.contains(deactivators.elementAt(i))) {
                            target.activate(targetProperty, false);
                            return;
                        }
                    }
                }
            }

            public String toString() {
                return target.getName() + "[" + targetProperty + "] " + activators;
            }
        }

        protected class TransformOption extends TerrainOption {
            private JComboBox from;
            private JComboBox to = new JComboBox();

            TransformOption() {
                from = new JComboBox();
                DefaultComboBoxModel model = (DefaultComboBoxModel) from.getModel();
                model.addElement("-");
                model.addElement("Woods");
                model.addElement("Brush");
                model.addElement("Grain");
                model.addElement("Marsh");
                model.addElement("Marsh -1");
                model.addElement("Marsh 0");
                model.addElement("Level -1");
                model.addElement("Level 1");
                model.addElement("Level 2");
                model.addElement("Level 3");
                model.addElement("Level 4");
                from.setMaximumSize(new Dimension(from.getMaximumSize().width, from.getPreferredSize().height));
                to = new JComboBox();
                model = (DefaultComboBoxModel) to.getModel();
                model.addElement("-");
                model.addElement("Woods");
                model.addElement("Brush");
                model.addElement("Grain");
                model.addElement("Marsh");
                model.addElement("Level -1");
                model.addElement("Level 0");
                model.addElement("Level 1");
                to.setMaximumSize(new Dimension(to.getMaximumSize().width, to.getPreferredSize().height));
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                panel.add(new JLabel("All"));
                panel.add(from);
                panel.add(new JLabel("is"));
                panel.add(to);
            }

            public String getRule() {
                String s = "";
                if (!from.getSelectedItem().equals("-") && !to.getSelectedItem().equals("-")) {
                    String fromRule = "";
                    for (StringTokenizer st = new StringTokenizer((String) from.getSelectedItem()); st.hasMoreTokens(); ) {
                        fromRule += st.nextToken();
                    }
                    fromRule = fromRule.replace('-', '_');
                    String toRule = "";
                    for (StringTokenizer st = new StringTokenizer((String) to.getSelectedItem()); st.hasMoreTokens(); ) {
                        toRule += st.nextToken();
                    }
                    toRule = toRule.replace('-', '_');
                    s = s.concat(fromRule);
                    s = s.concat("To");
                    s = s.concat(toRule);
                }
                return s;
            }

            public void reset() {
                from.setSelectedIndex(0);
                to.setSelectedIndex(0);
            }

            public String getText() {
                String s = "";
                if (!from.getSelectedItem().equals("-") && !to.getSelectedItem().equals("-")) {
                    s = "all " + ((String) from.getSelectedItem()).toLowerCase() + " is " + ((String) to.getSelectedItem()).toLowerCase();
                }
                return s;
            }
        }

        @Override
        public void setVisible(boolean visible) {

            // update the board list when the dialog is shown
            if(visible) {
                bdName.removeAllItems();
                // code added by DR to enable multiple instances of same board to have Terrain SSR applied
                ArrayList<BoardNames> boardlist = new ArrayList<BoardNames>();
                boardlist.add(new BoardNames("", "0")); // need a blank board name for "all"
                int x = 0;
                Iterator it = getBoardsFromControls().iterator();
                while (it.hasNext()) {
                    boardlist.add(new BoardNames(((ASLBoard) it.next()).getCommonName(), Integer.toString(x)));
                    x=+1;
                }
                Iterator itr = boardlist.iterator();
                for(int y = 0; y < boardlist.size(); y++) {
                    BoardNames newboard = (BoardNames) itr.next();
                    bdName.addItem(newboard);
                }
                // end of changes
            }

            super.setVisible(visible);
        }
    }
}
