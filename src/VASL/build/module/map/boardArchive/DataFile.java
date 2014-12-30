/*
 * $Id: ASLMap.java 8530 2012-12-26 04:37:04Z David Sullivan $
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

    public static final String VERSION = "version";
    public static final String DEFAULT_VERSION = "0.0";

    private InputStream dataFile;
    private HashMap<String, String> attributes = new HashMap<String, String>();

    public DataFile(InputStream file) throws IOException {

        this.dataFile = file;
        attributes.put(VERSION, DEFAULT_VERSION); // set a default version
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
    public String getVersion() {return  attributes.get(VERSION);}
}
