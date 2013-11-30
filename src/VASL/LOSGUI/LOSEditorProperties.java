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

import java.io.FileInputStream;
import java.util.Properties;

/**
 * Title:        LOSEditorProperties.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class LOSEditorProperties {

    // properties
    private static String LOSEditorHome;
    private static String BoardDirectory;
    private static boolean loaded = false;

    // getters
    public static String getLOSEditorHome() {

        if (!loaded) load();

        return LOSEditorHome;
    }

    public static String getBoardDirectory() {

        if (!loaded) load();

        return BoardDirectory;
    }

    public LOSEditorProperties() {
    }

    private static void load() {

        // try to read the file
        try {

            Properties props = new Properties();
            FileInputStream in = new FileInputStream("LOSEditor.properties");

            // load the properties
            props.load(in);
            LOSEditorHome = props.getProperty("LOSEditorHome");
            BoardDirectory = props.getProperty("BoardDirectory");
            loaded = true;

        } catch (Exception e) {

        }

    }
}