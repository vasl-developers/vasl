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

public class SW extends Counter {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// NONE constant
	public static final int NONE			= -1;

	// SW types
	public static final int LMG			= 0;
	public static final int MMG			= 1;
	public static final int HMG			= 2;
	public static final int DC			= 3;
	public static final int RADIO			= 4;
	public static final int BAZOOKA		= 5;
	public static final int PANZERSCHRECK	= 6;
	public static final int PIAT			= 7;
	public static final int ATR			= 8;
	public static final int LIGHT_MORTAR	= 9;
	public static final int FLAME_THROWER	= 10;
	public static final int RECOILLESS_RIFLE	= 11;

	public static final String SWTypeNames[] = {
		"LMG", 	"MMG", 		"HMG",		"DC",
		"Radio", 	"Bazooka",		"Panzerschreck",	"Piat",
		"ATR", 	"Light Mortar",	"Flame-thrower",	"Recoilless Rifle"};

	// capabilities, class, etc
	protected int 	SWType;
	protected int	FP;
	protected int	DMFP;
	protected int	range;
	protected int	DMRange;
	protected int	minimumRange;
	protected int	ROF;
	protected int	DMROF;
	protected int	PP;
	protected int	DMPP;
	protected boolean	DM;			// may DM
	protected boolean	sprayingFire;
	protected boolean	leadershipModifier;
	protected int	breakdownNumber;
	protected int	eliminationNumber;
	protected int	repairNumber;
	protected int	repairEliminationNumber;

	// Status variables
	protected boolean	fired;	// fired but retained ROF
	protected boolean	prepFired;
	protected boolean	firstFired;
	protected boolean	finalFired;
	protected boolean	intensiveFired;
	protected boolean	malfunctioned;
	protected boolean	dismantled;	// currently DM

	// graphics display variables
	protected static Rectangle	paintAllInformationArea	= new Rectangle(0, 0, 250, 350);

	// constructor for text file
	public SW(
		int		typeID,
		int		nationality,
	 	String	name,
	 	String	imageName,
	 	int		SWType,
		int		BPV,
		int		FP,
		int		DMFP,
		int		range,
		int		DMRange,
		int		minimumRange,
		int		ROF,
		int		DMROF,
		int		PP,
		int		DMPP,
		boolean	DM,
		boolean	sprayingFire,
		boolean	leadershipModifier,
		int		breakdownNumber,
		int		eliminationNumber,
		int		repairNumber,
		int		repairEliminationNumber
	){

			this.typeID			= typeID;
			this.nationality		= nationality;
		 	this.name			= name;
		 	this.imageName		= imageName;
		 	this.SWType			= SWType;
			this.BPV			= BPV;
			this.FP			= FP;
			this.DMFP			= DMFP;
			this.range			= range;
			this.DMRange		= DMRange;
			this.minimumRange		= minimumRange;
			this.ROF			= ROF;
			this.DMROF			= DMROF;
			this.PP			= PP;
			this.DMPP			= DMPP;
			this.DM			= DM;
			this.sprayingFire		= sprayingFire;
			this.leadershipModifier	= leadershipModifier;
			this.breakdownNumber	= breakdownNumber;
			this.eliminationNumber	= eliminationNumber;
			this.repairNumber		= repairNumber;
			this.repairEliminationNumber = repairEliminationNumber;

	}

	// type
	public 	int  getSWType(){ return SWType;}
	protected 	void setSWType(int type){SWType = type;}

	// FP
	public int  getFP(){ return dismantled ? DMFP : FP;}
	public void setFP(int fp){FP = fp;}
	public void setDMFP(int fp){DMFP = fp;}

	// range
	public int  getRange(){ return dismantled ? DMRange : range;}
	public void setRange(int r){range = r;}
	public void setDMRange(int r){DMRange = r;}
	public int  getMinimumRange(){ return minimumRange;}
	public void setMinimumRange(int r){minimumRange = r;}

	// ROF
	public int  getROF(){ return dismantled ? DMROF : ROF;}
	public void setROF(int rof){FP = rof;}
	public void setDMROF(int rof){DMROF = rof;}

	// PP
	public int  getPP(){ return dismantled ? DMPP : PP;}
	public void setPP(int pp){PP = pp;}
	public void setDMPP(int pp){DMPP = pp;}

	// DM
	public void		setDM(boolean d){DM = d;}
	public boolean	mayDM() {return DM;}
	public void		setDismantled(boolean d){dismantled = d;}
	public boolean	isDismantled() {return dismantled;}

	// spraying fire
	public boolean	hasSprayingFire(){ return sprayingFire;}
	public void 	setSprayingFire(boolean sf){sprayingFire = sf;}

	// leadership
	public boolean	usesLeadershipModifier(){ return leadershipModifier;}
	public void 	setLeadershipModifier(boolean lm){leadershipModifier = lm;}

	// breakdown/elimination/repair
	public int  getBreakdownNumber(){ return breakdownNumber;}
	public void setBreakdownNumber(int bn){breakdownNumber = bn;}
	public int  getEliminationNumber(){ return eliminationNumber;}
	public void setEliminationNumber(int en){eliminationNumber = en;}
	public int  getRepairNumber(){ return repairNumber;}
	public void setRepairNumber(int rn){repairNumber = rn;}
	public int  getRepairEliminationNumber(){ return repairEliminationNumber;}
	public void setRepairEliminationNumber(int en){repairEliminationNumber = en;}

	// fire status functions
	public void		setFired(boolean f)	{fired = f;}
	public boolean	hasFired()			{return fired;}
	public void		setPrepFired(boolean pf){prepFired = pf;}
	public boolean	hasPrepFired()		{return prepFired;}
	public void		setFirstFired(boolean ff){firstFired = ff;}
	public boolean	hasFirstFired()		{return firstFired;}
	public void		setFinalFired(boolean ff){finalFired = ff;}
	public boolean	hasFinalFired()		{return finalFired;}
	public void		setIntensiveFired(boolean i)	{intensiveFired = i;}
	public boolean	hasIntensiveFired()		{return intensiveFired;}

	// menu functions
	public JPopupMenu 	getSetupMenu(ActionListener listener){return null;}
	public boolean		setupMenuHandler(ActionEvent e){return false;}
	public void			popupMenuEvent(String action){}

	public Rectangle paintCounterImageAndName(int top, int vertCenter, int spaceBetweenLines, Color c, Graphics2D g, ImageObserver obs){

		FontMetrics fm			= g.getFontMetrics();
		String 	displayName		= nationalityNames[nationality] + " " + name;
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

		int	spaceBetweenLines	= 3;

		// clear the background
		g.setColor(background);
		g.fillRect(x, y, (int) paintAllInformationArea.getWidth(), (int) paintAllInformationArea.getHeight());

		// paint the border
		g.setColor(text);
		g.fillRect(x, y, (int) paintAllInformationArea.getWidth(), 2);	// top
		g.fillRect(x, y, 2, (int) paintAllInformationArea.getHeight());	// left
		g.fillRect(x, y + (int) paintAllInformationArea.getHeight() - 2, (int) paintAllInformationArea.getWidth(), 2);	// bottom
		g.fillRect(x + (int) paintAllInformationArea.getWidth() - 2, y, 2, (int) paintAllInformationArea.getHeight());	// right

		// paint the image and name
		Rectangle paintRect = paintCounterImageAndName(y + spaceBetweenLines, x + (int) paintAllInformationArea.getWidth()/2, spaceBetweenLines, text, g, obs);
		int currentTop = (int) paintRect.getMaxY() + 2 * spaceBetweenLines;

		// set the margins for the other information
		int leftTextMargin 	= x + (int) paintAllInformationArea.getWidth()/20;
		int leftInfoMargin 	= x + (int) paintAllInformationArea.getWidth() * 5/20;
		int rightTextMargin 	= x + (int) paintAllInformationArea.getWidth() * 10/20;
		int rightInfoMargin 	= x + (int) paintAllInformationArea.getWidth() * 14/20;

		//Paint the basic information
		currentTop = paintBasicInformation(
			g, x, currentTop, obs, background, text, checkImage, spaceBetweenLines,
			leftTextMargin,
			leftInfoMargin ,
			rightTextMargin,
			rightInfoMargin
		);

		//Paint the special capabilities
		currentTop = paintSpecialCapabilities(
			g, x, currentTop, obs, background, text, checkImage, spaceBetweenLines,
			leftTextMargin,
			rightInfoMargin
		);

		//Paint the current status
		currentTop = paintCurrentStatus(
			g, x, currentTop, obs, background, text, checkImage, spaceBetweenLines,
			leftTextMargin,
			leftInfoMargin
		);
	}

	public int paintBasicInformation(
		Graphics2D 		g,
		int 			x,
		int 			y,
		ImageObserver 	obs,
		Color 		background,
		Color 		text,
		Image 		checkImage,
		int			spaceBetweenLines,
		int 			leftTextMargin,
		int 			leftInfoMargin ,
		int 			rightTextMargin,
		int 			rightInfoMargin
	){

		Rectangle2D	tempRect1;
		Rectangle2D	tempRect2;

		FontMetrics fm		= g.getFontMetrics();
		String tempString;
		int currentTop = y;

		// header
		tempRect1	= fm.getStringBounds("Basic:   ('/n' = Dismantled value)", g);
		currentTop 	+= (int) tempRect1.getHeight() + 3 * spaceBetweenLines;
		g.drawString("Basic:", x + 5, currentTop);

		@SuppressWarnings("unused")
    int checkImageHeight 	= checkImage.getHeight(obs);
		@SuppressWarnings("unused")
    int checkImageWidth	= checkImage.getWidth(obs);

		// first line
		tempRect1	= fm.getStringBounds("FP:", g);
		tempRect2	= fm.getStringBounds("Range:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect2.getHeight()) + spaceBetweenLines;

		// FP
		g.drawString("FP:", leftTextMargin, currentTop);
		if (DM && DMFP != 0) {
			g.drawString((Integer.toString(FP) + "/" + Integer.toString(DMFP)), leftInfoMargin, currentTop);
		}
		else {
			g.drawString(Integer.toString(FP), leftInfoMargin, currentTop);
		}

		// range
		g.drawString("Range:", rightTextMargin, currentTop);
		if (minimumRange > 0) {
			tempString = Integer.toString(minimumRange) + " - ";
		}
		else {
			tempString = "";
		}
		if (DM && DMRange != 0) {
			g.drawString((tempString + Integer.toString(range) + "/" + Integer.toString(DMRange)), rightInfoMargin, currentTop);
		}
		else {
			g.drawString(tempString + Integer.toString(range), rightInfoMargin, currentTop);
		}


		// second line
		tempRect1	= fm.getStringBounds("PP:", g);
		tempRect2	= fm.getStringBounds("ROF:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect2.getHeight()) + spaceBetweenLines;
		g.drawString("PP:", leftTextMargin, currentTop);
		g.drawString("ROF:", rightTextMargin, currentTop);

		// PP
		if (DM) {
			g.drawString((Integer.toString(PP) + "/" + Integer.toString(DMPP)), leftInfoMargin, currentTop);
		}
		else {
			g.drawString(Integer.toString(PP), leftInfoMargin, currentTop);
		}

		// ROF
		if (DM && DMROF != 0) {
			g.drawString((Integer.toString(ROF) + "/" + Integer.toString(DMROF)), rightInfoMargin, currentTop);
		}
		else {
			g.drawString(Integer.toString(ROF), rightInfoMargin, currentTop);
		}

		// third line
		tempRect1	= fm.getStringBounds("B#:", g);
		tempRect2	= fm.getStringBounds("X#:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect2.getHeight()) + spaceBetweenLines;
		g.drawString("B#:", leftTextMargin, currentTop);
		g.drawString("X#:", rightTextMargin, currentTop);

		// B#
		if (breakdownNumber > 0) {
			g.drawString(Integer.toString(breakdownNumber), leftInfoMargin, currentTop);
		}

		// X#
		if (eliminationNumber > 0) {
			g.drawString(Integer.toString(eliminationNumber), rightInfoMargin, currentTop);
		}

		// fourth line
		tempRect1	= fm.getStringBounds("Repair:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect2.getHeight()) + spaceBetweenLines;
		g.drawString("Repair:", leftTextMargin, currentTop);
		if (breakdownNumber > 0) {
			g.drawString(Integer.toString(repairNumber) + " (Eliminate on " + Integer.toString(repairEliminationNumber) + ")", leftInfoMargin, currentTop);
		}
		else {
			g.drawString("NA", leftInfoMargin, currentTop);
		}

		return currentTop;
	}

	public int paintSpecialCapabilities(
		Graphics2D 		g,
		int 			x,
		int 			y,
		ImageObserver 	obs,
		Color 		background,
		Color 		text,
		Image 		checkImage,
		int			spaceBetweenLines,
		int 			leftTextMargin,
		int 			rightInfoMargin
	){

		Rectangle2D	tempRect1;

		FontMetrics fm		= g.getFontMetrics();

		int currentTop = y;

		int checkImageHeight 	= checkImage.getHeight(obs);
		@SuppressWarnings("unused")
    int checkImageWidth	= checkImage.getWidth(obs);

		// header
		tempRect1	= fm.getStringBounds("Special capabilities:", g);
		currentTop 	+= (int) 2 * tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Special capabilities:", x + 5, currentTop);

		// first line
		tempRect1	= fm.getStringBounds("No leadership mod:", g);
		currentTop 	+= (int) tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("No leadership mod:", leftTextMargin, currentTop);
		if (!leadershipModifier) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// second line
		tempRect1	= fm.getStringBounds("Spraying fire:", g);
		currentTop 	+= (int) tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Spraying fire:", leftTextMargin, currentTop);
		if (sprayingFire) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		return currentTop;
	}

	public int paintCurrentStatus(
		Graphics2D 		g,
		int 			x,
		int 			y,
		ImageObserver 	obs,
		Color 		background,
		Color 		text,
		Image 		checkImage,
		int			spaceBetweenLines,
		int 			leftTextMargin,
		int 			rightInfoMargin
	){

		Rectangle2D	tempRect1;

		FontMetrics fm		= g.getFontMetrics();

		int currentTop = y;

		int checkImageHeight 	= checkImage.getHeight(obs);
		@SuppressWarnings("unused")
    int checkImageWidth	= checkImage.getWidth(obs);

		// header
		tempRect1	= fm.getStringBounds("Current status:", g);
		currentTop 	+= (int) 2 * tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Current status:", x + 5, currentTop);

		// first line
		tempRect1	= fm.getStringBounds("Malfunctioned:", g);
		currentTop 	+= (int) tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Malfunctioned:", leftTextMargin, currentTop);
		if (malfunctioned) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// second line
		tempRect1	= fm.getStringBounds("Dismantled:", g);
		currentTop 	+= (int) tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Dismantled:", leftTextMargin, currentTop);
		if (dismantled) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		return currentTop;
	}
}
