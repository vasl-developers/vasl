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
package VASL.LOS.Map;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;

/**
 * Title:        Unit.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public abstract class Unit
		extends Counter {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// movement types
	public static final int INFANTRY_MOVEMENT	= 0;
	public static final int TRACKED_MOVEMENT	= 1;
	public static final int HALFTRACK_MOVEMENT	= 2;
	public static final int AC_MOVEMENT			= 3;
	public static final int TRUCK_MOVEMENT		= 4;
	public static final int WAGON_MOVEMENT		= 5;
	public static final int CALVARY_MOVEMENT	= 6;

	// class types
	public static final int ELITE		= 0;
	public static final int FIRST_LINE	= 1;
	public static final int SECOND_LINE	= 2;
	public static final int GREEN		= 3;
	public static final int CONSRIPT	= 4;
	public static final int SS			= 5;
	public static final int MARINES		= 6;

	public static final String classTypeNames[] = {
		"Elite", 		"First line", 	"Second line",	"Green",
		"Conscript", 	"SS",			"Marines",		"Partisan"};

	// size of the unit graphics
	public static final int LARGE_COUNTER_WIDTH	= 50;
	public static final int SMALL_COUNTER_WIDTH	= 40;

	// leader generation factors for each nationality
	@SuppressWarnings("unused")
  private static final float LGFactors[] =
		{(float)4, (float)8, (float)5.5, (float)5, (float)0, (float)0,
		 (float)7, (float)6, (float)8,   (float)6, (float)0, (float)7};

	// types
	protected int 		movementType;
	protected int 		classType;
	protected boolean		elite;		// is elite for depletion number purposes
	protected String 	identity;	// unit identity (A, B, C, etc.)
	protected int 		USNumber;

	// PFPh status variables
	protected boolean	prepFired;
	protected boolean	oppFire;

	// MPh status variables
	protected boolean	moving;
	protected boolean	moved;

	// DFPh status variables
	protected boolean	firstFired;
	protected boolean	finalFired;

	Location loc;

	public abstract int 	getCounterSize();
	public abstract boolean isOnCounter(Point p);

	public abstract boolean isVehicle();
	public abstract boolean isInfantry();
	public abstract boolean isGoodOrder();

	public 	int  getMovementType(){ return movementType;}
	protected 	void setMovementType(int type){movementType = type;}

	public int  	getClassType(){ return classType;}
	public void 	setClassType(int classtype){classType = classtype;}
	public String	getClassTypeName(){ return classTypeNames[classType];}

	public Location getLocation(){ return loc;}
	public void     setLocation(Location loc){this.loc = loc;}

	public 	void		setElite(boolean e){elite = e;}
	public 	boolean	isElite()		{return elite;}

	public String	getIdentity(){ return identity;}
	public void		setIdentity(String id){identity = id;}

	public int  getUSNumber(){ return USNumber;}
	public void setUSNumber(int number){USNumber = number;}

	// PFPh status functions
	protected 	void		setOppFire(boolean of)	{oppFire = of;}
	public 	boolean	hasOppFired()		{return oppFire;}
	protected 	void		setPrepFired(boolean pf){prepFired = pf;}
	public 	boolean	hasPrepFired()		{return prepFired;}

	// MPh status functions
	protected 	void		setMoving(boolean moving)	{this.moving = moving;}
	protected 	void		setMoved(boolean moved)		{this.moved = moved;}
	public 	boolean	isMoving()				{return moving;}
	public 	boolean	hasMoved()				{return moved;}

	// DFPh status functions
	protected 	void		setFirstFired(boolean ff)	{firstFired = ff;}
	protected 	void		setFinalFired(boolean ff)	{finalFired = ff;}
	public 	boolean	hasFirstFired()			{return firstFired;}
	public 	boolean	hasFinalFired()			{return finalFired;}

	// game status functions
	public abstract void	setPlayerTurn();
	public abstract void	setGameTurn();
	public abstract void	setPhase();

	// paint routines
	public abstract Rectangle	getPaintAllInformationArea();
	public abstract void		paintAllInformation(Graphics2D g, int x, int y, ImageObserver obs, Color background, Color text, Image checkImage);

	// paint the unit image and name
	public Rectangle paintCounterImageAndName(int top, int vertCenter, int spaceBetweenLines, Color c, Graphics2D g, ImageObserver obs){

		FontMetrics fm			= g.getFontMetrics();
		String 	displayName		= "";
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
}
