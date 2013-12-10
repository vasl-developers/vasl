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

import VASL.LOS.Map.Location;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.ImageObserver;

/**
 * Title:        Vehicle.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public class Vehicle
	extends Unit {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// graphics display variables
	private static Rectangle	paintAllInformationArea	= new Rectangle(0, 0, 600, 600);

	// contructors
	public Vehicle(){

		setMovementType(TRACKED_MOVEMENT);
	}

	public Vehicle(Location loc){

		setMovementType(TRACKED_MOVEMENT);
		setLocation(loc);
	}

	// unit graphics
	public int getCounterSize(){ return LARGE_COUNTER_WIDTH;}

	public boolean isOnCounter(Point p) {

		if (p != null && this.getLocation() != null && this.getLocation().getLOSPoint() != null){

			if (Math.abs(this.getLocation().getLOSPoint().getX() - p.getX()) <= this.getCounterSize()/2 &&
				Math.abs(this.getLocation().getLOSPoint().getY() - p.getY()) <= this.getCounterSize()/2){

				return true;
			}
		}

		return false;
	}

	public boolean isGoodOrder(){ return true;};

	// unit type
	public boolean isVehicle(){ return true;}
	public boolean isInfantry(){ return false;}

	// game status functions
	public void	setPlayerTurn()	{};
	public void	setGameTurn()	{};
	public void	setPhase()		{};

	// paint functions
	public Rectangle	getPaintAllInformationArea(){ return paintAllInformationArea;}

	public void	paintAllInformation(Graphics2D g, int x, int y, ImageObserver obs, Color background, Color text, Image checkImage){


	}

	public JPopupMenu getSetupMenu(ActionListener listener){

		// create the menu
		JPopupMenu	menu = new JPopupMenu();
		menu.setFont(new Font("Dialog", 0, 11));

		return menu;
	}

	public void		popupMenuEvent(String actionEvent){}
	public boolean	setupMenuHandler(ActionEvent e){return false;}
}

