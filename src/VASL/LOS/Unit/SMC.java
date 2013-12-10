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
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;

/**
 * Title:        SMC.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public class SMC
	extends Infantry {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// variables
	protected int	leadershipModifier;
	protected boolean commissar;

	// status variables
	protected boolean	wounded;

	// contructors
	public SMC(
		int		typeID,
		int		nationality,
		String 	name,
		String 	imageName,
		int		unitType,
		int		BPV,
		int		FP,
		int		range,
		int		morale,
		int		leadershipModifier,
		boolean	commissar,
		boolean	selfRally,
		int 		ELRUnitTypeID,
		int 		ELRFromUnitTypeID
	){
		this.typeID				= typeID;
		this.nationality			= nationality;
		this.name				= name;
		this.imageName			= imageName;
		this.unitType			= unitType;
		this.BPV				= unitType;
		this.FP				= FP;
		this.range				= range;
		this.morale				= morale;
		this.brokenMorale			= morale;
		this.leadershipModifier		= leadershipModifier;
		this.commissar			= commissar;
		this.selfRally			= selfRally;
		this.ELRUnitTypeID		= ELRUnitTypeID;
		this.ELRFromUnitTypeID		= ELRFromUnitTypeID;

		deploy = false;

	}

	// functions for commissar flag
	public boolean	isCommissar(){ return commissar;}
	public void 	setIsCommissar(boolean c){commissar = c;}

	// functions for leadership modifier
	public int	getLeadershipModifier(){ return leadershipModifier;}
	public void	getLeadershipModifier(int l){leadershipModifier = l;}

	// functions for wounded flag
	public boolean	isWounded(){ return wounded;}
	public void 	setIsWounded(boolean w){wounded = w;}

	// graphics display functions
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
		int 			centerTextMargin,
		int 			centerInfoMargin,
		int 			rightTextMargin,
		int 			rightInfoMargin
	){

		Rectangle2D	tempRect1;
		Rectangle2D	tempRect2;
		Rectangle2D	tempRect3;

		FontMetrics fm		= g.getFontMetrics();

		int currentTop = y;

		// header
		tempRect1	= fm.getStringBounds("Basic:", g);
		currentTop 	+= (int) tempRect1.getHeight() + 3 * spaceBetweenLines;
		g.drawString("Basic:", x + 5, currentTop);

		@SuppressWarnings("unused")
    int checkImageHeight 	= checkImage.getHeight(obs);
		@SuppressWarnings("unused")
    int checkImageWidth	= checkImage.getWidth(obs);

		// first line
		if (unitType == LEADER || unitType == ARMOR_LEADER){

			tempRect1	= fm.getStringBounds("Morale:", g);
			tempRect2	= fm.getStringBounds("Leadership:", g);
			currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect2.getHeight()) + spaceBetweenLines;
			g.drawString("Morale:", leftTextMargin, currentTop);
			g.drawString(Integer.toString(morale) + "/" + Integer.toString(brokenMorale), leftInfoMargin, currentTop);
			g.drawString("Leadership:", centerTextMargin, currentTop);
			g.drawString(Integer.toString(leadershipModifier), centerInfoMargin, currentTop);
		}
		else {

			tempRect1	= fm.getStringBounds("FP:", g);
			tempRect2	= fm.getStringBounds("Range:", g);
			tempRect3	= fm.getStringBounds("Morale:", g);
			currentTop 	+= (int) Math.max(tempRect1.getHeight(), Math.max(tempRect2.getHeight(), tempRect3.getHeight())) + spaceBetweenLines;
			g.drawString("FP:", leftTextMargin, currentTop);
			g.drawString(Integer.toString(FP), leftInfoMargin, currentTop);
			g.drawString("Range:", centerTextMargin, currentTop);
			g.drawString(Integer.toString(range), centerInfoMargin, currentTop);
			g.drawString("Morale:", rightTextMargin, currentTop);
			g.drawString(Integer.toString(morale) + "/" + Integer.toString(brokenMorale), rightInfoMargin, currentTop);
		}

		return currentTop;
	}
}
