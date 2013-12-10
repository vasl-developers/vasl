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
package VASL.LOS.Unit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.ImageObserver;
import java.io.Serializable;

public abstract class Counter
	implements Serializable, Cloneable {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// nationalities
	public static final int NONE		= -1;
	public static final int GERMAN	= 0;
	public static final int RUSSIAN	= 1;
	public static final int AMERICAN	= 2;
	public static final int BRITISH	= 3;
	public static final int JAPANESE	= 4;
	public static final int CHINESE	= 5;
	public static final int ALLIED_MINOR = 6;
	public static final int AXIS_MINOR 	= 7;
	public static final int ITALIAN	= 8;
	public static final int FRENCH	= 9;
	public static final int FINNISH	= 10;
	public static final int PARTISAN	= 11;

	public static final String nationalityNames[] = {
		"German", 		"Russian", 		"American", "British", "Japanese", "Chinese",
		"Allied Minor",	"Axis Minor",	"Italian",	"French",	"Finnish",	"Partisan"};

	// nationality subtypes
	public static final int NO_NATIONALITY_SUBTYPE	= 0;
	public static final int GURKA				= 1;
	public static final int ANZAC				= 2;
	public static final int FREE_FRENCH			= 3;
	public static final int GREEK				= 4;
	public static final int YOGOSLAVIAN			= 5;

	public static final String nationalitySubtypeNames[] = {
		"None", "Gurka", "ANZAC", "Free French", "Greek", "Yogoslavian"};

	protected int 		BPV;
	protected int 		nationality;
	protected int 		nationalitySubtype;

	protected int 		typeID;		// unique unit type number (one per unit type)
	protected int		ID;			// unique unit number (one for each distinct unit in game)

	protected String	name = "";

	// image variables
	protected String	imageName = "";
	protected String	reverseSideImageName = "";
	protected transient Image image;
	protected transient Image reverseSideImage;

	// the clone function
	public Object clone(){

		try {

			return super.clone();
		}
		catch (Exception e){

			return null;
		}
	}

	public int  	getBPV(){ return BPV;}
	public void 	setBPV(int bpv){BPV = bpv;}

	public int  getNationality(){ return nationality;}
	public void setNationality(int nation){nationality = nation;}

	public int  getNationalitySubtype(){ return nationalitySubtype;}
	public void setNationalitySubtype(int nation){nationalitySubtype = nation;}

	public String	getName(){ return name;}
	public void 	setName(String name){this.name = name;}

	public int  getTypeID(){ return typeID;}
	public void setTypeID(int id){typeID = id;}

	public int  getID(){ return ID;}
	public void setID(int id){ID = id;}

	public String	getImageName(){ return imageName;}
	public void 	setImageName(String name){imageName = name;}

	public Image	getImage(){ return image;}
	public void 	setImage(Image image){this.image = image;}

	public String	getReverseSideImageName(){ return reverseSideImageName;}
	public void 	setReverseSideImageName(String name){reverseSideImageName = name;}

	public Image	getReverseSideImage(){ return reverseSideImage;}
	public void 	setReverseSideImage(Image image){this.image = reverseSideImage;}

	// paint routines
	public abstract Rectangle	getPaintAllInformationArea();
	public abstract void		paintAllInformation(Graphics2D g, int x, int y, ImageObserver obs, Color background, Color text, Image checkImage);
	public abstract Rectangle 	paintCounterImageAndName(int top, int vertCenter, int spaceBetweenLines, Color c, Graphics2D g, ImageObserver obs);

	// menu functions
	public JPopupMenu getSetupMenu(ActionListener listener){

		// create the menu
		JPopupMenu	menu = new JPopupMenu();
		menu.setFont(new Font("Dialog", 0, 11));

		JMenuItem	item 			= new JMenuItem();

		// create the empty item
		item = new JMenuItem("<None>");
		item.setEnabled(false);
		item.addActionListener(listener);
		menu.add(item);

		return menu;
	}

	public boolean setupMenuHandler(ActionEvent e){return false;}

	public void	popupMenuEvent(String action){};
}
