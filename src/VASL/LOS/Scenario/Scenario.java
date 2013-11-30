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

import VASL.LOS.Map.Hex;
import VASL.LOS.Unit.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.*;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Scenario
	extends	JComponent
	implements	Serializable,
			MouseListener,
			MouseMotionListener {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// global constants
	public final static int AXIS 		= 1;
	public final static int ALLIES 	= 2;

	private HashSet axisUnits		= new HashSet(20);
	private HashSet alliedUnits	= new HashSet(20);

	public final static int MAX_GROUPS 	= 5;

	private ScenarioGroup axisGroups[]	= new ScenarioGroup[MAX_GROUPS];
	private ScenarioGroup alliedGroups[]	= new ScenarioGroup[MAX_GROUPS];

	// Theaters
	public static final int WEST_FRONT	= 0;
	public static final int EAST_FRONT	= 1;
	public static final int DESERT	= 2;
	public static final int PTO		= 3;
	public static final int ITALY		= 4;

	public final static String theaterNames[] = {"Western Front", "Eastern Front", "Desert", "PTO", "Italy"};

	// environmental conditions
	public static final int SNOW		= 0;
	public static final int MUD		= 1;
	public static final int WET		= 2;
	public static final int MOIST		= 3;
	public static final int MODERATE	= 4;
	public static final int DRY		= 5;
	public static final int VERY_DRY	= 6;

	public final static String ECNames[] = {"Snow", "Mud", "Wet", "Moist", "Moderate", "Dry", "Very Dry"};

	// Text variables
	private String	mapName	= "";
	private String	name	= "";
	private String	preamble	= "";
	private String	SSR		= "";
	private String	aftermath	= "";
	private String	VC		= "";
	private String	balance	= "";

	// master counter lists
	private Infantry MMCUnitsList[];
	private SMC SMCUnitsList[];
	private SW SWList[];

	private Fortification fortificationList[];

	// initialization variables
	private int		theater;
	private boolean	changed;	// changed since the last time saved?
	private int		EC;
	private Date		date;
	private int		axisSAN;
	private int		alliedSAN;
	private int		axisDefaultELR;
	private int		alliedDefaultELR;
	private boolean	rooftops;
	private int		turns;
	private boolean	halfTurn;

	// constructors
	public Scenario() {

		// create the groups
		for (int x = 0; x < MAX_GROUPS; x++){

			// create the group
			axisGroups[x]	= new ScenarioGroup(this, AXIS);
			alliedGroups[x]	= new ScenarioGroup(this, ALLIES);
		}

		// set up the mouse listners
		addMouseListener(this);
		addMouseMotionListener(this);

		// set the changed flag
		changed = false;
	}

	// map functions
	public void		setMapName(String name) {mapName = name; changed = true;}
	public String	getMapName() {return mapName;}

	// name functions
	public void		setName(String name) {this.name = name;}
	public String	getName() {return name;}

	// theater functions
	public void		setTheater(int t) {theater = t; changed = true;}
	public int		getTheater() {return theater;}
	public String	getTheaterName() {return theaterNames[theater];}

	// text field functions
	public void		setPreamble(String s) {preamble = s; changed = true;}
	public String	getPreamble() {return preamble;}

	public void		setSSR(String s) {SSR = s; changed = true;}
	public String	getSSR() {return SSR;}

	public void		setAftermath(String s) {aftermath = s; changed = true;}
	public String	getAftermath() {return aftermath;}

	public void		setVC(String vc) {VC = vc; changed = true;}
	public String	getVC() {return VC;}

	public void		setBalance(String b) {balance = b; changed = true;}
	public String	getBalance() {return balance;}

	// EC functions
	public void		setEC(int ec) {EC = ec; changed = true;}
	public int		getEC() {return EC;}
	public String	getECName() {return ECNames[EC];}

	// scenario date functions
	public void		setDate(Date d) {date = d; changed = true;}
	public Date		getDate() {return date;}

	// default ELR functions
	public void		setAxisDefaultELR(int elr) {axisDefaultELR = elr; changed = true;}
	public int		getAxisDefaultELR() {return axisDefaultELR;}
	public void		setAlliedDefaultELR(int elr) {alliedDefaultELR = elr; changed = true;}
	public int		getAlliedDefaultELR() {return alliedDefaultELR;}

	// default SAN functions
	public void		setAxisSAN(int san) {axisSAN = san; changed = true;}
	public int		getAxisSAN() {return axisSAN;}
	public void		setAlliedSAN(int san) {alliedSAN = san; changed = true;}
	public int		getAlliedSAN() {return alliedSAN;}

	// rooftop functions
	public void		setRooftops(boolean r) {rooftops = r; changed = true;}
	public boolean	rooftopsInPlay() {return rooftops;}

	// turn functions
	public void		setTurns(int t) {turns = t; changed = true;}
	public int		getTurns() {return turns;}
	public void		setHalfTurn(boolean h) {halfTurn = h; changed = true;}
	public boolean	hasHalfTurn() {return halfTurn;}

	// used to control changes
	public void		setChanged(boolean c) {changed = true;}
	public boolean	hasChanged() {return changed;}

	// add units: depreciated
	public void addUnit(Unit u, int side){

		if (side == AXIS){

			axisUnits.add(u);
		}
		else {

			alliedUnits.add(u);
		}

		// set the changed flag
		changed = true;
	}

	public void addCounter(int side, int group, int bucket, Counter c, int qty){

		if (side == AXIS){

			axisGroups[group].addCounter(bucket, c, qty);
		}
		else if (side == ALLIES){

			alliedGroups[group].addCounter(bucket, c, qty);
		}
	}

	public void addCounterToSelected(Counter c) {

		// add to any group with selected bucket
		for (int x = 0; x < MAX_GROUPS; x++){

			alliedGroups[x].addCounterToSelected(c);
			axisGroups[x].addCounterToSelected(c);
		}
	}

	public void removeCounterFromSelected() {

		// remove from any group with selected bucket
		for (int x = 0; x < MAX_GROUPS; x++){

			alliedGroups[x].removeCounterFromSelected();
			axisGroups[x].removeCounterFromSelected();
		}
	}

	// scenarion group functions
	public ScenarioGroup[] getAxisGroups(){ return axisGroups;}
	public ScenarioGroup[] getAlliedGroups(){ return alliedGroups;}

	// master counter list functions
	public Infantry[]	getMMCUnitsList(){return MMCUnitsList;}
	public void		setMMCUnitsList(Infantry[] list){MMCUnitsList = list;}
	public SMC[]	getSMCUnitsList(){return SMCUnitsList;}
	public void		setSMCUnitsList(SMC[] list){SMCUnitsList = list;}
	public SW[]		getSWList(){return SWList;}
	public void		setSWList(SW[] list){SWList = list;}

	public Fortification[]	getFortificationList(){return fortificationList;}
	public void			setFortificationList(Fortification[] list){fortificationList = list;}

	public HashSet getUnits(){

		HashSet temp = new HashSet();

		// add the axis
		Iterator iter = axisUnits.iterator();
		while (iter.hasNext()){

			temp.add((Unit) iter.next());
		}

		// add the allies
		iter = alliedUnits.iterator();
		while (iter.hasNext()){

			temp.add((Unit) iter.next());
		}
		return temp;
	}

	public HashSet getVehicles(Hex h){

		return getVehicles(h, -1);
	}

	public HashSet getVehicles(Hex h,int level){

		HashSet temp = new HashSet();
		Unit u;

		// add the axis
		Iterator iter = axisUnits.iterator();
		while (iter.hasNext()){

			u = (Unit) iter.next();
			if (u.isVehicle() && u.getLocation().getHex() == h && (level == -1 || level == u.getLocation().getAbsoluteHeight())){

				temp.add(u);
			}
		}

		// add the allies
		iter = alliedUnits.iterator();
		while (iter.hasNext()){

			u = (Unit) iter.next();
			if (u.isVehicle() && u.getLocation().getHex() == h && (level == -1 || level == u.getLocation().getAbsoluteHeight())){

				temp.add(u);
			}
		}
		return temp;
	}

	public HashSet getVehicles(){

		HashSet temp = new HashSet();
		Unit u;

		// add the axis
		Iterator iter = axisUnits.iterator();
		while (iter.hasNext()){

			u = (Unit) iter.next();
			if (u.isVehicle()){

				temp.add(u);
			}
		}

		// add the allies
		iter = alliedUnits.iterator();
		while (iter.hasNext()){

			u = (Unit) iter.next();
			if (u.isVehicle()){

				temp.add(u);
			}
		}
		return temp;
	}

	public HashSet getUnits(Hex h){

		HashSet temp = new HashSet();
		Unit u;

		// add the axis
		Iterator iter = axisUnits.iterator();
		while (iter.hasNext()){

			u = (Unit) iter.next();
			if (u.getLocation().getHex() == h){

				temp.add(u);
			}
		}

		// add the allies
		iter = alliedUnits.iterator();
		while (iter.hasNext()){

			u = (Unit) iter.next();
			if (u.getLocation().getHex() == h){

				temp.add(u);
			}
		}
		return temp;
	}

	public int numberOfUnits(){return axisUnits.size() + alliedUnits.size();}

	public static Scenario readScenario(String filename){

		ObjectInputStream   infile;
		Scenario	scenario;

		try {
			infile =
			new ObjectInputStream(
			new BufferedInputStream(
			new GZIPInputStream(
			new FileInputStream(filename))));

			scenario  = (Scenario) infile.readObject();
			infile.close();

		} catch(Exception e) {
			System.out.println("Cannot open the scenario file: " + filename);
			e.printStackTrace(System.out);
			System.out.println(e);
			return null;
		}

		// reset the changed flag
		scenario.changed = false;

		return scenario;
	}

	public void writeScenario(String filename){

		ObjectOutputStream  outfile;

		// don't want to save the selections
		clearAllSelections();

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
			System.out.println("Cannot save the scenario file: " + filename);
	            e.printStackTrace();
		}

		// reset the changed flag
		changed = false;
	}

	public void resetCounterObjects(
		Infantry[] 		MMCUnitList,
		Image[] 		MMCImages,
		SMC[] 		SMCUnitList,
		Image[] 		SMCImages,
		SW[]	 		SWList,
		Image[] 		SWImages,
		Fortification[]	fortificationList,
		Image[] 		fortificationImages
	){

		// reset each group
		for (int x = 0; x < MAX_GROUPS; x++){

			if (axisGroups[x] != null) {
				axisGroups[x].resetCounterObjects(
					MMCUnitList, 		MMCImages,
					SMCUnitList, 		SMCImages,
					SWList, 			SWImages,
					fortificationList, 	fortificationImages);
			}
			if (alliedGroups[x] != null) {
				alliedGroups[x].resetCounterObjects(
					MMCUnitList, 		MMCImages,
					SMCUnitList, 		SMCImages,
					SWList, 			SWImages,
					fortificationList, 	fortificationImages);
			}
		}
	}

	/******************************
	Mouse methods
	******************************/
	public void mouseReleased(MouseEvent e) {

	}

	public void mousePressed(MouseEvent e) {}

	public void mouseClicked(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}

	public void mouseMoved(MouseEvent e) {}

	public void mouseDragged(MouseEvent e){}

	// clear all of the bucket selection
	public void clearAllSelections(){

		for (int x = 0; x < MAX_GROUPS; x++){

			alliedGroups[x].clearAllSelections();
			axisGroups[x].clearAllSelections();
		}
	}

	// initialize a counter with scenario values
	public void initializeCounter(Counter c, ScenarioGroup group){

		// set up the calendar
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);

		// unit?
		if (c instanceof Unit){

			Unit u = (Unit) c;

			// SS units are elite
			if (u.getClassType() == Unit.SS){

				u.setElite(true);
				@SuppressWarnings("unused")
        boolean b = u.isElite();
			}

			// infantry?
			if (u.getClass().getName().equals("LOS.Infantry")) {

				Infantry i = (Infantry) u;

				// set the default ELR
				if (group.getSide() == AXIS){

					i.setELR(axisDefaultELR);
				}
				else if (group.getSide() == ALLIES){

					i.setELR(alliedDefaultELR);
				}

				// Germans
				if (i.getNationality() == Unit.GERMAN){

					// PF
					if (cal.get(Calendar.YEAR) >= 1944){

						i.setPF(true);
					}

					// ATMM
					if ( cal.get(Calendar.YEAR) >= 1944  ||
					    (cal.get(Calendar.YEAR) == 1943  && cal.get(Calendar.MONTH) >= 10)){

						i.setATMM(true);
					}
				}

				// Americans
				if (i.getNationality() == Unit.AMERICAN){

					// Always get WP grenades
					i.setWPGrenades(true);
				}

				// British
				if (i.getNationality() == Unit.BRITISH){

					// WP grenades
					if (cal.get(Calendar.YEAR) >= 1944){

						i.setWPGrenades(true);
					}
				}
			}
		}
	}
}
