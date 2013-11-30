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

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;

import javax.swing.JPopupMenu;

public class Fortification extends Counter {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// NONE constant
	public static final int NONE			= -1;

	// fortification types
	public static final int PILLBOX_1_3_5	= 0;
	public static final int PILLBOX_2_3_5	= 1;
	public static final int PILLBOX_3_3_5	= 2;
	public static final int PILLBOX_1_5_7	= 3;
	public static final int PILLBOX_2_5_7	= 4;
	public static final int PILLBOX_3_5_7	= 5;
	public static final int FOXHOLE_1S		= 6;
	public static final int FOXHOLE_2S		= 7;
	public static final int FOXHOLE_3S		= 8;
	public static final int AP_MINES		= 9;
	public static final int AT_MINES		= 10;
	public static final int WIRE			= 11;
	public static final int TRENCH		= 12;
	public static final int AT_DITCH 		= 13;

	// capabilities, class, etc
	protected int 	fortificationType;
	protected int 	squadCapacity;
	protected int 	coveredArcTEM;
	protected int 	nonCoveredArcTEM;
	protected int 	facing;

	// capabilities, class, etc, functions
	public int  getFortificatitionType()	{return fortificationType;}
	public int  getSquadCapacity()		{return squadCapacity;}
	public void setSquadCapacity(int c)		{squadCapacity = c;}
	public int  getCoveredArcTEM()		{return coveredArcTEM;}
	public void setCoveredArcTEM(int c)		{coveredArcTEM = c;}
	public int  getNonCoveredArcTEM()		{return nonCoveredArcTEM;}
	public void setNonCoveredArcTEM(int c)	{nonCoveredArcTEM = c;}
	public int  getFacing()				{return facing;}
	public void setFacing(int f)			{facing = f;}

	// graphics display variables
	protected static Rectangle	paintAllInformationArea	= new Rectangle(0, 0, 250, 350);

	// constructor for text file
	public Fortification(
		int		typeID,
	 	String	name,
	 	String	imageName,
	 	int		fortificationType,
		int		BPV
	){

		this.typeID			= typeID;
		this.name			= name;
		this.imageName		= imageName;
		this.fortificationType	= fortificationType;
		this.BPV			= BPV;

		nationality		= NONE;
		squadCapacity 	= NONE;
		coveredArcTEM 	= NONE;
		nonCoveredArcTEM	= NONE;

		// set the squad capacity
		switch(fortificationType) {

			case PILLBOX_1_3_5:
			case PILLBOX_1_5_7:
			case FOXHOLE_1S:

				squadCapacity = 1;
				break;

			case PILLBOX_2_3_5:
			case PILLBOX_2_5_7:
			case FOXHOLE_2S:

				squadCapacity = 2;
				break;

			case PILLBOX_3_3_5:
			case PILLBOX_3_5_7:
			case FOXHOLE_3S:

				squadCapacity = 3;
				break;
		}

		// set the covered arc variables
		switch(fortificationType) {

			case PILLBOX_1_3_5:
			case PILLBOX_3_3_5:
			case PILLBOX_2_3_5:

				coveredArcTEM 	= 3;
				nonCoveredArcTEM	= 5;
				break;

			case PILLBOX_1_5_7:
			case PILLBOX_2_5_7:
			case PILLBOX_3_5_7:

				coveredArcTEM 	= 5;
				nonCoveredArcTEM	= 7;
				break;
		}
	}

	// menu functions
	public JPopupMenu 	getSetupMenu(ActionListener listener){return null;}
	public boolean		setupMenuHandler(ActionEvent e){return false;}
	public void			popupMenuEvent(String action){}

	public Rectangle paintCounterImageAndName(int top, int vertCenter, int spaceBetweenLines, Color c, Graphics2D g, ImageObserver obs){

		FontMetrics fm			= g.getFontMetrics();
		String 	displayName		= name;
		Rectangle2D	nameRect	= fm.getStringBounds(displayName, g);
		String	noImageString 	= "(No image)";
		Rectangle2D	noImageStringRect = fm.getStringBounds(noImageString, g);

		// set the color
		g.setColor(c);

		if (image != null){

			// paint the image
			g.drawImage(
				image,
				vertCenter - (int) image.getWidth(obs)/2,
				top,
				image.getHeight(obs),
				image.getWidth(obs),
				obs);

			// draw the unit name
			g.drawString(
				displayName,
				vertCenter - (int) nameRect.getWidth()/2,
				top + (int) image.getHeight(obs) + (int) nameRect.getHeight() + spaceBetweenLines);

			// return the the drawing area
			return new Rectangle(
				Math.min(vertCenter - (int) image.getWidth(obs)/2, vertCenter - (int) nameRect.getWidth()/2),
				top,
				Math.max((int) image.getWidth(obs), (int) nameRect.getWidth()),
				(int) image.getHeight(obs) + (int) nameRect.getHeight() + spaceBetweenLines
			);

		}
		else {

			// draw the unit name
			g.drawString(
				noImageString,
				vertCenter - (int) noImageStringRect.getWidth()/2,
				top + (int) noImageStringRect.getHeight());
			g.drawString(
				displayName,
				vertCenter - (int) nameRect.getWidth()/2,
				top + (int) noImageStringRect.getHeight() + (int) nameRect.getHeight() + spaceBetweenLines);

			// return the the drawing area
			return new Rectangle(
				Math.min(vertCenter - (int) noImageStringRect.getWidth()/2, vertCenter - (int) nameRect.getWidth()/2),
				top,
				Math.max((int) noImageStringRect.getWidth(), (int) nameRect.getWidth()),
				(int) noImageStringRect.getHeight() + (int) nameRect.getHeight() + spaceBetweenLines
			);
		}
	}

	// graphics display functions
	public Rectangle	getPaintAllInformationArea(){ return paintAllInformationArea;}
	public void 	paintAllInformation(Graphics2D g, int x, int y, ImageObserver obs, Color background, Color text, Image checkImage){

		// clear the background
		g.setColor(background);
		g.fillRect(x, y, (int) paintAllInformationArea.getWidth(), (int) paintAllInformationArea.getHeight());
	}
}
