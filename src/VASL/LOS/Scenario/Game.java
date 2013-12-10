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
package VASL.LOS.Scenario;

import java.io.*;
import java.util.zip.*;

/**
 * Title:        Game.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public 	class Game
		implements Serializable {

	// serial ID
	static final long serialVersionUID = 000100L;

	// game phase variables
	public static final int RPh		= 1;
	public static final int PFPh	= 2;
	public static final int MPh		= 3;
	public static final int DFPh	= 4;
	public static final int AFPh	= 5;
	public static final int APh		= 6;
	public static final int RtPh	= 7;
	public static final int CCPh	= 8;

	public int currentPhase;

	public Game() {
	}

	public static Game readGame(String filename){

	ObjectInputStream   infile;
	Game game;

		try {
			infile =
			new ObjectInputStream(
			new BufferedInputStream(
			new GZIPInputStream(
			new FileInputStream(filename))));

			game  = (Game) infile.readObject();
			infile.close();

		} catch(Exception e) {
			System.out.println("Cannot open the game file: " + filename);
			e.printStackTrace(System.out);
			System.out.println(e);
			return null;
		}
		return game;
	}

	public void writeGame(String filename){

	ObjectOutputStream  outfile;

		// open output file and save map
		try {
			outfile =
			new ObjectOutputStream(
			new BufferedOutputStream(
			new GZIPOutputStream(
			new FileOutputStream(filename))));

			outfile.writeObject(this);
			outfile.close();
		} catch(Exception e) {
			System.out.println("Cannot save the map file: " + filename);
	            e.printStackTrace();
		}
	}

}
