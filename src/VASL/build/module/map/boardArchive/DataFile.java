/*
 * $Id$
 *
 * Copyright (c) 2015 by David Sullivan
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
package VASL.build.module.map.boardArchive;

import VASSAL.build.module.map.boardPicker.board.HexGrid;

import java.io.*;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * A class that accesses the legacy "data" file in the board archive
 */
public class DataFile {

    public static final String VERSION_KEY = "version";
    public static final String DEFAULT_VERSION = "0.0";
    public static final String NAME_KEY = "name";
    private InputStream dataFile;
    private HashMap<String, String> attributes = new HashMap<String, String>();

    public DataFile(InputStream file) throws IOException {

        this.dataFile = file;
        attributes.put(VERSION_KEY, DEFAULT_VERSION); // set a default version
        readData();
    }

    /**
     * Reads the data file in the board archive
     */
    private void readData() throws IOException {

        if (dataFile != null) {

            BufferedReader file = new BufferedReader(new InputStreamReader(dataFile));
            String s;
            while ((s = file.readLine()) != null) {
                parseDataLine(s);
            }
        }
    }

    /**
     * Parses one line in the data file setting the appropriate attribute
     * @param s the line of text
     */
    private void parseDataLine(String s) {

        StringTokenizer st = new StringTokenizer(s);

        if (st.countTokens() >= 2) {
            String s1 = st.nextToken().toLowerCase();
            attributes.put(s1, st.nextToken());
        }
    }

    // named attributes getters for simplicity
    public String getX0() { return attributes.get(HexGrid.X0);}
    public String getY0() { return attributes.get(HexGrid.Y0);}
    public String getDX() { return attributes.get(HexGrid.DX);}
    public String getDY() { return attributes.get(HexGrid.DY);}
    public String getSnapScale() {return attributes.get(HexGrid.SNAP_SCALE);}
    public String getVersion() {
        if (this.dataFile==null){return DEFAULT_VERSION;}
        return  attributes.get(VERSION_KEY);
    }
    public String getBoardImageFileName() {
        if (this.dataFile == null) {return null;}
        if (attributes.get(NAME_KEY) == null) {return null;}
        return attributes.get(NAME_KEY);
    }
    public String getAltHexGrid() {return attributes.get(BoardArchive.ALT_HEX_GRID_KEY);}
}
