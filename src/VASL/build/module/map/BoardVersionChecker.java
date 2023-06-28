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

import VASL.build.module.map.boardPicker.ASLBoard;
import VASL.build.module.map.boardPicker.Overlay;
import VASL.counters.Concealable;
import VASSAL.Info;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.ServerConnection;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.command.Command;
import VASSAL.configure.StringConfigurer;
import VASSAL.tools.PropertiesEncoder;
import org.apache.commons.io.IOUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Copyright (c) 2003 by Rodney Kinney.  All rights reserved.
 * Date: Jun 11, 2003
 */
public class BoardVersionChecker extends AbstractBuildable implements GameComponent, PropertyChangeListener {

    private static String boardVersionURL;
    private static Properties overlayVersions;
    private String overlayVersionURL;
    private String boardPageURL;
    private static String boardRepositoryURL;
    private static String overlayRepositoryURL;

    public static String BOARD_VERSION_PROPERTY_KEY = "boardVersions";

    private Map map;
    // private Properties boardVersions;
    //private Properties overlayVersions;

    private static final String BOARD_VERSION_URL = "boardVersionURL";
    private static final String OVERLAY_VERSION_URL = "overlayVersionURL";
    private static final String BOARD_PAGE_URL = "boardPageURL";
    private static final String BOARD_VERSIONS = BOARD_VERSION_PROPERTY_KEY;
    private static final String OVERLAY_VERSIONS = "overlayVersions";
    private static final String BOARD_REPOSITORY_URL = "boardRepositoryURL";
    private static final String OVERLAY_REPOSITORY_URL = "overlayRepositoryURL";
    // for use with v5boardVersions.xml
    private static final String boardsFileElement = "boardsMetadata";
    private static final String overlaysFileElement = "overlaysMetadata";
    private static final String coreboardElement = "coreBoards";
    private static final String boarddataType = "boarddata";
    private static final String overlaydataType = "overlaydata";
    private static final String coreboardNameAttr = "name";
    private static final String coreboardversionAttr = "version";
    private static final String coreboardversiondateAttr = "versionDate";
    private static final String coreboarddescAttr = "description";
    private static final String overlayNameAttr = "name";
    private static final String overlayversionAttr = "version";
    private static final String overlayversiondateAttr = "versionDate";
    private static final String overlaydescAttr = "description";
    private static final String otherboardElement = "otherBoards";
    private static final String otherboardNameAttr = "name";
    private static final String otherboardversionAttr = "version";
    private static final String otherboardversiondateAttr = "versionDate";
    private static final String otherboarddescAttr = "description";
    private static LinkedHashMap<String, BoardVersions> boardversions = new LinkedHashMap<String, BoardVersions>(500);
    private static LinkedHashMap<String, OverlayVersions> overlayversions = new LinkedHashMap<String, OverlayVersions>(500);

    public String[] getAttributeNames() {
        return new String[]{BOARD_VERSION_URL, OVERLAY_VERSION_URL, BOARD_PAGE_URL, BOARD_REPOSITORY_URL, OVERLAY_REPOSITORY_URL};
    }

    public String getAttributeValueString(String key) {
        if (BOARD_VERSION_URL.equals(key)) {
            return boardVersionURL;
        } else if (OVERLAY_VERSION_URL.equals(key)) {
            return overlayVersionURL;
        } else if (BOARD_PAGE_URL.equals(key)) {
            return boardPageURL;
        } else if (BOARD_REPOSITORY_URL.equals(key)) {
            return boardRepositoryURL;
        } else if (OVERLAY_REPOSITORY_URL.equals(key)) {
            return overlayRepositoryURL;
        }


        return null;
    }

    public void setAttribute(String key, Object value) {
        if (BOARD_VERSION_URL.equals(key)) {
            boardVersionURL = (String) value;
        } else if (OVERLAY_VERSION_URL.equals(key)) {
            overlayVersionURL =  (String) value;
        } else if (BOARD_PAGE_URL.equals(key)) {
            boardPageURL = (String) value;
        } else if (BOARD_REPOSITORY_URL.equals(key)) {
            boardRepositoryURL = (String) value;
        } else if (OVERLAY_REPOSITORY_URL.equals(key)){
            overlayRepositoryURL = (String) value;
        }
    }
    public static String getboardVersionURL(){
        return boardVersionURL;
    }
    public void addTo(Buildable parent)  {

        map = (Map) parent;
        GameModule.getGameModule().getGameState().addGameComponent(this);
        GameModule.getGameModule().getServer().addPropertyChangeListener(ServerConnection.CONNECTED, this);
        GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(BOARD_VERSIONS, null));
        GameModule.getGameModule().getPrefs().addOption(null, new StringConfigurer(OVERLAY_VERSIONS, null));

        //TODO property change listener not firing - force update
        readVersionFiles();

    }

    public Command getRestoreCommand() {
        return null;
    }

    public void setup(boolean gameStarting) {

    }

    public void propertyChange(PropertyChangeEvent evt)  {
        if (Boolean.TRUE.equals(evt.getNewValue())) {

            readVersionFiles();
        }
    }

    /**
     * Reads the board and overlay versions using the URLs in the build file
     */
    private void readVersionFiles()  {

        // Need to disable SNI to read from Github
        //System.setProperty("jsse.enableSNIExtension", "false");

        try {
            URL base = new URL(boardVersionURL);
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);

            try (InputStream input = conn.getInputStream()){
                parseboardversionFile(input);
            }

        } catch (IOException e) {
            // Fail silently if we can't contact the server
        }

        try {
            URL base = new URL(overlayVersionURL);
            URLConnection conn = base.openConnection();
            conn.setUseCaches(false);

            try (InputStream input = conn.getInputStream()){
                parseoverlayversionFile(input);
            }

        } catch (IOException e) {
            // Fail silently if we can't contact the server
        }
    }

    private void parseboardversionFile(InputStream metadata) {

        SAXBuilder parser = new SAXBuilder();

        try {
            // the root element will be the boardsMetadata element
            Document doc = parser.build(metadata);
            org.jdom2.Element root = doc.getRootElement();

            // read the shared metadata
            if(root.getName().equals(boardsFileElement)) {

                for(org.jdom2.Element e: root.getChildren()) {

                    // handle coreBoards
                    if(e.getName().equals(coreboardElement)){
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                // read the coreBoards attributes
                                BoardVersions bdversion = new BoardVersions();
                                bdversion.setName(f.getAttribute(coreboardNameAttr).getValue());
                                bdversion.setboardversion(f.getAttribute(coreboardversionAttr).getValue());
                                bdversion.setversiondate(f.getAttribute(coreboardversiondateAttr).getValue());
                                bdversion.setdescription(f.getAttribute(coreboarddescAttr).getValue());

                                // add the board and version to the version list
                                boardversions.put(bdversion.getName(), bdversion);
                            }
                        }
                    }
                    else { // handle otherBoards
                        for(org.jdom2.Element f: e.getChildren()) {
                            if(f.getName().equals(boarddataType)) {
                                // read the otherBoards attributes
                                BoardVersions bdversion = new BoardVersions();
                                bdversion.setName(f.getAttribute(otherboardNameAttr).getValue());
                                bdversion.setboardversion(f.getAttribute(otherboardversionAttr).getValue());
                                bdversion.setversiondate(f.getAttribute(otherboardversiondateAttr).getValue());
                                bdversion.setdescription(f.getAttribute(otherboarddescAttr).getValue());

                                // add the board and version to the version list
                                boardversions.put(bdversion.getName(), bdversion);
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
        } catch (JDOMException e) {

        }

    }
    private void parseoverlayversionFile(InputStream metadata) {

        SAXBuilder parser = new SAXBuilder();

        try {
            // the root element will be the overlaysMetadata element
            Document doc = parser.build(metadata);
            org.jdom2.Element root = doc.getRootElement();

            // read the shared metadata
            if(root.getName().equals(overlaysFileElement)) {

                for(org.jdom2.Element e: root.getChildren()) {
                    if(e.getName().equals(overlaydataType)) {
                            // read the overlay attributes
                            OverlayVersions ovrversion = new OverlayVersions();
                            ovrversion.setName(e.getAttribute(overlayNameAttr).getValue());
                            ovrversion.setboardversion(e.getAttribute(overlayversionAttr).getValue());
                            ovrversion.setversiondate(e.getAttribute(overlayversiondateAttr).getValue());
                            ovrversion.setdescription(e.getAttribute(overlaydescAttr).getValue());

                            // add the overlay and version to the list
                            overlayversions.put(ovrversion.getName(), ovrversion);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
        } catch (JDOMException e) {

        }

    }
    /**
     * Copies a board from the GitHub board repository to the board directory
     * @param boardName the board name (sans bd)
     * @return true if the board was successfully copied, otherwise false
     */
    public static boolean updateBoard(String boardName) {

        String qualifiedBoardName =
                GameModule.getGameModule().getPrefs().getStoredValue(ASLBoardPicker.BOARD_DIR) +
                        System.getProperty("file.separator", "\\") +
                        "bd" + boardName;
        String testBoardName =
                GameModule.getGameModule().getPrefs().getStoredValue(ASLBoardPicker.BOARD_DIR) +
                        System.getProperty("file.separator", "\\") +
                        "bdtest";
        String url = boardRepositoryURL + "/bd" + boardName;
        //return getRepositoryFile(url, qualifiedBoardName);  // previous code
        // code changes to improve auto-sync workflow -if bdtest is valid, copy over existing boardname file; whether valid or not, delete temp file bdtest
        final Path testpath =  Paths.get(testBoardName);
        if(Boolean.TRUE.equals(getRepositoryFile(url, testBoardName))){
            final Path qualifiedpath = Paths.get(qualifiedBoardName);
            try {
                Files.copy(testpath, qualifiedpath, REPLACE_EXISTING);
                Files.delete(testpath);
            }  catch (IOException e) {
                // Fail silently on any error
                return false;
            }
            return true;
        } else {
            try {
                Files.delete(testpath);
            }  catch (IOException e) {
                GameModule.getGameModule().warn("bdtest deletion failed; remove manually");
                return false;
            }
            return false;
        }
    }

    /**
     * Copies an overlay file from the GitHub board repository to the overlay directory
     * @param overlayName the overlay name (sans ovr)
     * @return true if the overlay was successfully copied, otherwise false
     */
    public static boolean updateOverlayFile(String overlayName) {
        String qualifiedOverlayName =
                GameModule.getGameModule().getPrefs().getStoredValue(ASLBoardPicker.BOARD_DIR) +
                        System.getProperty("file.separator", "\\") +
                        "overlays" +
                        System.getProperty("file.separator", "\\") + overlayName;
        String url = overlayRepositoryURL + "/" + overlayName;

        return getRepositoryFile(url, qualifiedOverlayName);

    }

    /**
     * Copy a file from a website to local disk
     * Assumes URL is on github.com
     * NOTE - will overwrite existing file
     * @param url URL to the file on the website
     * @param fileName fully qualified file name
     * @return true if copy succeeded, otherwise false
     */
    private static boolean getRepositoryFile(String url, String fileName)  {

        // Need to disable SNI to read from Github
        System.setProperty("jsse.enableSNIExtension", "false");

        try {

            URL website = new URL( encodeUrl(url) );
            URLConnection conn = website.openConnection();
            conn.setUseCaches(false);
            try (FileOutputStream outFile = new FileOutputStream(fileName);
                InputStream in = conn.getInputStream()) {
                ReadableByteChannel rbc = Channels.newChannel(in);
                outFile.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                if (outFile.getChannel().size()==0){return false;}  // new test
            }
            return true;

        } catch (IOException e) {
            // Fail silently on any error
            return false;
        }
    }

    // do not use on an already-encoded URL. It will double-encode it.
    private static String encodeUrl( String unencodedURL ) {
        try {
            URL url = new URL(unencodedURL);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            return uri.toURL().toString();
        }
        catch( java.net.URISyntaxException ex ) {
            return unencodedURL;
        }
        catch( java.net.MalformedURLException ex ) {
            return unencodedURL;
        }
    }
    // new method to get latest version number from xml file instead of .txt
    public static String getlatestOverlayVersionnumberfromwebrepository(String ovrName){
        if (ovrName.contains("ovr")){ ovrName = ovrName.substring(3);}
        OverlayVersions findversion =  overlayversions.get(ovrName);
        return findversion.getoverlayversion();
    }

    public static String getlatestVersionnumberfromwebrepository(String unReversedBoardName){
        BoardVersions findversion = boardversions.get(unReversedBoardName);
        return findversion.getboardversion();

    }

    public class BoardVersions{
        private String boardname;
        private String boardversion;
        private String versiondate;
        private String description;

        public String getName(){return boardname;}
        public void setName(String value ) {boardname = value;}
        public String getboardversion() {return boardversion;}
        public void setboardversion(String value){boardversion = value;}
        public String getversiondate(){return versiondate;}
        public void setversiondate(String value){versiondate = value;}
        public String getdescription(){return description;}
        public void setdescription(String value){description = value;}
    }
    public class OverlayVersions{
        private String overlayname;
        private String overlayversion;
        private String versiondate;
        private String description;

        public String getName(){return overlayname;}
        public void setName(String value ) {overlayname = value;}
        public String getoverlayversion() {return overlayversion;}
        public void setboardversion(String value){overlayversion = value;}
        public String getversiondate(){return versiondate;}
        public void setversiondate(String value){versiondate = value;}
        public String getdescription(){return description;}
        public void setdescription(String value){description = value;}
    }
}