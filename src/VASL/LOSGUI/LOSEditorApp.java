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

import javax.swing.*;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Title:        LOSEditorApp.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 *
 * @author David Sullivan
 * @version 1.0
 */
public class LOSEditorApp {

    // error file
    private static PrintStream errors = null;

    //Construct the application
    public LOSEditorApp(String mapName) {

        // set up the map editing frame
        LOSEditorJFrame frame = new LOSEditorJFrame();

        // open the map
        if (mapName != null) {

            frame.openMap(mapName);
        }
    }

    //Main method
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set the Java look-and-feel");
            System.exit(1);
        }

        // create the error file
        try {

            errors = new PrintStream(new BufferedOutputStream(new FileOutputStream("err.txt")));

        } catch (Exception e) {
            e.printStackTrace();
        }

        // read the LOS properties file, quit if there's a problem
        if (LOSEditorProperties.getLOSEditorHome() == null) {

            writeError("Cannot read the properties file LOSEditorApp.properties");
            System.exit(0);
        }

        // map name provided?
        if (args.length == 1) {

            new LOSEditorApp(args[0]);
        } else {

            new LOSEditorApp(null);
        }
    }

    // write error routine
    public static void writeError(String s) {

        if (errors == null) {

            System.err.println(s);
        } else {

            errors.println(s);
            errors.flush();
        }
    }
}