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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import VASL.LOS.Map.Location;

/**
 * Title:        Infantry.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public class Infantry
	extends Unit {

	// serial ID: version 1.0.0
	static final long serialVersionUID = 000100L;

	// unit type ID constant
	public static final int NO_UNIT_TYPE_ID = -1;

	// unit types
	public static final int SQUAD	= 0;
	public static final int HS		= 1;
	public static final int CREW	= 2;
	public static final int LEADER	= 3;
	public static final int ARMOR_LEADER	= 4;
	public static final int HERO		= 5;

	// graphics display variables
	protected static Rectangle	paintAllInformationArea	= new Rectangle(0, 0, 600, 600);

	// unit substitution variables
	protected int ELRUnitTypeID		= NO_UNIT_TYPE_ID;
	protected int ELRFromUnitTypeID	= NO_UNIT_TYPE_ID;
	protected int reducedUnitTypeID	= NO_UNIT_TYPE_ID;
	protected int parentSquadTypeID	= NO_UNIT_TYPE_ID;
	protected transient Infantry ELRUnitType;
	protected transient Infantry ELRFromUnitType;
	protected transient Infantry reducedUnitType;
	protected transient Infantry parentSquadType;

	// the big 3
	protected int 		FP;
	protected boolean	assaultFire;
	protected int		smokeExponent;
	protected boolean	WPGrenades;
	protected int 		range;
	protected boolean	sprayingFire;
	protected int 		morale;
	protected int 		brokenMorale;
	protected boolean	underlinedMorale;

	// capabilities, class, etc
	protected int 		unitType;
	protected int 		ELR;
	protected int 		staticELR;
	protected boolean	selfRally;
	protected boolean	stealthy		= false;
	protected boolean	lax			= false;
	protected boolean	deploy		= true;
	protected boolean	MOL			= false;
	protected boolean	PF			= false;
	protected boolean	ATMM			= false;
	protected boolean	skiEquipped		= false;
	protected boolean	winterCamouflage	= false;
	protected boolean	commando		= false;
	protected boolean	combatEngineer	= false;
	protected boolean	assaultEngineer	= false;
	protected boolean	untrainedBoatUse	= true;
	protected boolean	sewerMovement	= false;
	protected boolean	ammoShortage	= false;

	// status variables
	protected boolean	pinned;
	protected boolean	TI;
	protected boolean	CX;
	protected boolean	encircled;
	protected boolean	broken;
	protected boolean	DM;
	protected boolean	berserk;
	protected boolean	fanatic;
	protected boolean	disrupted;
	protected boolean	captured;

	// RPh status variables
	protected boolean	attemptedRally;

	// PFPh status variables
	protected boolean	usedSupportWeapon;

	// MPh status variables
	protected boolean	usingHazMovement;
	protected boolean	assaultMoving;
	protected boolean	dashing;

	protected boolean	armoredAssault;	// ???
	protected boolean	minimumMove;	// ???

	// RtPh status variables
	protected boolean	routed;
	protected boolean	routing;

	// APh status variables
	protected boolean	advanced;

	// CCPh status variables
	protected boolean	attackedCC;
	protected boolean	inMelee;
	protected boolean	inCC;

	// contructors
	public Infantry(){

		setMovementType(INFANTRY_MOVEMENT);
		unitType = SQUAD;
	}

	public Infantry(Location loc){

		setMovementType(INFANTRY_MOVEMENT);
		setLocation(loc);
		unitType = SQUAD;
	}

	public Infantry(
		int		typeID,
		String 	name,
		String 	imageName,
		int		nationality,
		int		unitType,
		int		classType,
		int		staticELR,
		int		BPV,
		int		FP,
		boolean	assaultFire,
		int		smokeExponent,
		int		range,
		boolean	sprayingFire,
		int		morale,
		int		brokenMorale,
		boolean	underlinedMorale,
		boolean	selfRally,
		boolean	combatEngineer,
		boolean	stealthy,
		boolean	lax,
		boolean	deploy,
		int 	ELRUnitTypeID,
		int 	ELRFromUnitTypeID,
		int 	reducedUnitTypeID,
		int 	parentSquadTypeID){

		// assign the parameters
		this.typeID				= typeID;
		this.name				= name;
		this.imageName			= imageName;
		this.nationality		= nationality;
		this.unitType			= unitType;
		this.classType			= classType;
		this.staticELR			= staticELR;
		this.BPV					= BPV;
		this.FP					= FP;
		this.assaultFire		= assaultFire;
		this.smokeExponent		= smokeExponent;
		this.range				= range;
		this.sprayingFire		= sprayingFire;
		this.morale				= morale;
		this.brokenMorale		= brokenMorale;
		this.underlinedMorale	= underlinedMorale;
		this.selfRally			= selfRally;
		this.combatEngineer		= combatEngineer;
		this.stealthy			= stealthy;
		this.lax				= lax;
		this.deploy				= deploy;
		this.ELRUnitTypeID		= ELRUnitTypeID;
		this.ELRFromUnitTypeID	= ELRFromUnitTypeID;
		this.reducedUnitTypeID	= reducedUnitTypeID;
		this.parentSquadTypeID	= parentSquadTypeID;
	}

	// functions for inter-unit relationship indexes
	public int  getELRUnitTypeID(){ return ELRUnitTypeID;}
	public void setELRUnitTypeID(int id){ELRUnitTypeID = id;}

	public int  getELRFromUnitTypeID(){ return ELRFromUnitTypeID;}
	public void setELRFromUnitTypeID(int id){ELRFromUnitTypeID = id;}

	public int  getReducedUnitTypeID(){ return reducedUnitTypeID;}
	public void setReducedUnitTypeID(int id){reducedUnitTypeID = id;}

	public int  getParentSquadTypeID(){ return parentSquadTypeID;}
	public void setParentSquadTypeID(int id){parentSquadTypeID = id;}

	public Infantry getELRUnitType(){ return ELRUnitType;}
	public void 	setELRUnitType(Infantry id){ELRUnitType = id;}

	public Infantry	getELRFromUnitType(){ return ELRFromUnitType;}
	public void 	setELRFromUnitType(Infantry id){ELRFromUnitType = id;}

	public Infantry	getReducedUnitType(){ return reducedUnitType;}
	public void 	setReducedUnitType(Infantry id){reducedUnitType = id;}

	public Infantry	getParentSquadType(){ return parentSquadType;}
	public void 	setParentSquadType(Infantry id){parentSquadType = id;}

	// functions for 'the big three'
	public int  getFP(){ return FP;}
	public void setFP(int fp){FP = fp;}

	public boolean	canAssaultFire(){ return assaultFire;}
	public void 	setAssaultFire(boolean af){assaultFire = af;}

	public int  getSmokeExponent(){ return smokeExponent;}
	public void setSmokeExponent(int se){smokeExponent = se;}

	public boolean	hasWPGrenades(){ return WPGrenades;}
	public void 	setWPGrenades(boolean wpg){WPGrenades = wpg;}

	public int  getRange(){ return range;}
	public void setRange(int r){range = r;}

	public boolean	hasSprayingFire(){ return sprayingFire;}
	public void 	setSprayingFire(boolean sf){sprayingFire = sf;}

	public int  getMorale(){ return morale;}
	public void setMorale(int m){morale = m;}

	public int  getBrokenMorale(){ return brokenMorale;}
	public void setBrokenMorale(int m){brokenMorale = m;}
	public int  getBaseMorale(){

		if (broken)	return brokenMorale;
		else 		return morale;
	}

	public int  getMCMorale(){

		int m = getBaseMorale();

		// adjust for status variables
		if (fanatic) 	m++;
		if (encircled) 	m--;

		// japanese with leader

		// commissar

		return m;
	}

	public int  getRallyMorale(){

		int m = getBaseMorale();

		// adjust for status variables
		if (DM)	 		m-=4;
		if (fanatic) 	m++;
		if (encircled) 	m--;

		// japanese with leader
		// commissar

		return m;
	}
	public boolean	hasUnderlinedMorale(){ return underlinedMorale;}
	public void 	setUnderlinedMorale(boolean um){underlinedMorale = um;}

	// functions for capabilities, class
	public int  getUnitType(){ return unitType;}
	public void setUnitType(int ut){unitType = ut;}

	public int  getELR(){ return ELR;}
	public void setELR(int elr){ELR = elr;}

	public int  getStaticELR(){ return staticELR;}
	public void setStaticELR(int elr){staticELR = elr;}

	public boolean	canSelfRally(){ return selfRally;}
	public void 	setSelfRally(boolean sr){selfRally = sr;}

	public boolean	isStealthy(){ return stealthy;}
	public void 	setStealthy(boolean s){stealthy = s;}

	public boolean	isLax(){ return lax;}
	public void 	setLax(boolean l){lax = l;}

	public boolean	canDeploy(){ return deploy;}
	public void 	setDeploy(boolean d){deploy = d;}

	public boolean	hasMOL(){ return MOL;}
	public void 	setMOL(boolean m){MOL = m;}

	public boolean	hasPF(){ return PF;}
	public void 	setPF(boolean p){PF = p;}

	public boolean	hasATMM(){ return ATMM;}
	public void 	setATMM(boolean a){ATMM = a;}

	public boolean	isSkiEquipped(){ return skiEquipped;}
	public void 	setSkiEquipped(boolean se){skiEquipped = se;}

	public boolean	hasWinterCamouflage(){ return winterCamouflage;}
	public void 	setHasWinterCamouflage(boolean wc){winterCamouflage = wc;}

	public boolean	isCommando(){ return commando;}
	public void 	setCommando(boolean c){commando = c;}

	public boolean	isAssaultEngineer(){ return assaultEngineer;}
	public void 	setAssaultEngineer(boolean ae){assaultEngineer = ae;}

	public boolean	isCombatEngineer(){ return combatEngineer;}
	public void 	setCombatEngineer(boolean ce){combatEngineer = ce;}

	public boolean	hasUntrainedBoatUse(){ return untrainedBoatUse;}
	public void 	setUntrainedBoatUse(boolean u){untrainedBoatUse = u;}

	public boolean	hasSewerMovement(){ return sewerMovement;}
	public void 	setSewerMovement(boolean u){sewerMovement = u;}

	public boolean	hasAmmoShortage(){ return ammoShortage;}
	public void 	setAmmoShortage(boolean as){ammoShortage = as;}

	// override unit setNationalitySubtype function
	public void setNationalitySubtype(int nation){

		// British
		if (nationality == BRITISH) {

			// removing subtype distinction
			if (nationalitySubtype == NO_NATIONALITY_SUBTYPE) {

				// reset
				if (nationalitySubtype == GURKA && classType == GREEN){

					commando = false;
				}
				else if (nationalitySubtype == ANZAC && classType == GREEN){

					stealthy = false;
				}
			}

			// Gurkas are commandos unless green
			else if (nationalitySubtype != GURKA && nation == GURKA && classType != GREEN){

				commando = true;
			}

			// ANZAC are stealthy unless green
			else if (nationalitySubtype != ANZAC && nation == ANZAC && classType != GREEN){

				stealthy = true;
			}
		}

		// set the new nationality subtype
		nationalitySubtype = nation;
	}

	// functions for status variables
	public boolean	isPinned(){ return pinned;}
	public void 	setPinned(boolean p){pinned = p;}

	public boolean	isTI(){ return TI;}
	public void 	setTI(boolean ti){TI = ti;}

	public boolean	isCX(){ return CX;}
	public void 	setCX(boolean cx){CX = cx;}

	public boolean	isEncircled(){ return encircled;}
	public void 	setEncircled(boolean e){encircled = e;}

	public boolean	isBroken(){ return broken;}
	public void 	setBroken(boolean b){broken = b;}

	public boolean	isDM(){ return DM;}
	public void 	setDM(boolean dm){DM = dm;}

	public boolean	isBerserk(){ return berserk;}
	public void 	setBerserk(boolean b){berserk = b;}

	public boolean	isFanatic(){ return fanatic;}
	public void 	setFanatic(boolean f){fanatic = f;}

	public boolean	isDisrupted(){ return disrupted;}
	public void 	setDisrupted(boolean d){disrupted = d;}

	public boolean	isCaptured(){ return captured;}
	public void 	setCaptured(boolean c){disrupted = c;}

	// functions for RPh
	public boolean	hasAttemptedRally(){ return attemptedRally;}
	public void 	setAttemptedRally(boolean ar){attemptedRally = ar;}

	// functions for PFPh
	public boolean	hasUsedSupportWeapon(){ return usedSupportWeapon;}
	public void 	setUsedSupportWeapon(boolean sw){usedSupportWeapon = sw;}

	// functions for MFPh
	public boolean	isUsingHazMovement(){ return usingHazMovement;}
	public void 	setUsingHazMovement(boolean hm){usingHazMovement = hm;}

	public boolean	isAssaultMoving(){ return assaultMoving;}
	public void 	setAssaultMoving(boolean am){assaultMoving = am;}

	public boolean	isDashing(){ return dashing;}
	public void 	setDashing(boolean am){dashing = am;}

	// functions for RtPh
	public boolean	hasRouted(){ return routed;}
	public void 	setRouted(boolean r){routed = r;}

	public boolean	isRouting(){ return routing;}
	public void 	setRouting(boolean r){routing = r;}

	// functions for APh
	public boolean	hasAdvanced(){ return advanced;}
	public void 	setAdvanced(boolean a){advanced = a;}

	// functions for CCPh
	public boolean	hasAttackedCC(){ return attackedCC;}
	public void 	setAttackedCC(boolean a){attackedCC = a;}

	public boolean	isInMelee(){ return inMelee;}
	public void 	setInMelee(boolean m){inMelee = m;}

	public boolean	isInCC(){ return inCC;}
	public void 	setInCC(boolean m){inCC = m;}

	// other functions
	public boolean isGoodOrder(){return !broken && !berserk && ! inMelee && ! captured;}


	// unit graphics
	public int getCounterSize(){ return SMALL_COUNTER_WIDTH;}

	public boolean isOnCounter(Point p) {

		if (p != null && this.getLocation() != null && this.getLocation().getLOSPoint() != null){

			if (Math.abs(this.getLocation().getLOSPoint().getX() - p.getX()) <= this.getCounterSize()/2 &&
				Math.abs(this.getLocation().getLOSPoint().getY() - p.getY()) <= this.getCounterSize()/2){

				return true;
			}
		}

		return false;
	}

	// unit type
	public boolean isVehicle(){ return false;}
	public boolean isInfantry(){ return true;}

	// game status functions
	public void	setPlayerTurn()	{};
	public void	setGameTurn()	{};
	public void	setPhase()		{};

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

		//Paint the unit relationships
		int currentTop = paintUnitRelationships(g, x, y, obs, background, text, checkImage, spaceBetweenLines);

		// set the margins for the other information
		int leftTextMargin 	= x + (int) paintAllInformationArea.getWidth()/20;
		int leftInfoMargin 	= x + (int) paintAllInformationArea.getWidth() * 5/20;
		int centerTextMargin 	= x + (int) paintAllInformationArea.getWidth() * 7/20;
		int centerInfoMargin 	= x + (int) paintAllInformationArea.getWidth() * 11/20;
		int rightTextMargin 	= x + (int) paintAllInformationArea.getWidth() * 7/10;
		int rightInfoMargin 	= x + (int) paintAllInformationArea.getWidth() * 17/20;

		//Paint the basic information
		currentTop = paintBasicInformation(
			g, x, currentTop, obs, background, text, checkImage, spaceBetweenLines,
			leftTextMargin,
			leftInfoMargin ,
			centerTextMargin,
			centerInfoMargin,
			rightTextMargin,
			rightInfoMargin
		);

		//Paint the special capabilities
		currentTop = paintSpecialCapabilities(
			g, x, currentTop, obs, background, text, checkImage, spaceBetweenLines,
			leftTextMargin,
			leftInfoMargin ,
			centerTextMargin,
			centerInfoMargin,
			rightTextMargin,
			rightInfoMargin
		);

		//Paint the current status
		currentTop = paintCurrentStatus(
			g, x, currentTop, obs, background, text, checkImage, spaceBetweenLines,
			leftTextMargin,
			leftInfoMargin ,
			centerTextMargin,
			centerInfoMargin,
			rightTextMargin,
			rightInfoMargin
		);
	}

	// paint subroutines
	public int paintUnitRelationships(
		Graphics2D 		g,
		int 			x,
		int 			y,
		ImageObserver 	obs,
		Color 		background,
		Color 		text,
		Image 		checkImage,
		int			spaceBetweenLines
	){

		Rectangle2D	tempRect1;

		FontMetrics fm		= g.getFontMetrics();

		// find the verticle center
		int currentVertCenter = x + (int) paintAllInformationArea.getWidth()/2;

		// paint the unit image and name
		Rectangle paintRect = paintCounterImageAndName(y + spaceBetweenLines, currentVertCenter, spaceBetweenLines, text, g, obs);
		int currentTop = (int) paintRect.getMaxY() + 2 * spaceBetweenLines;

		// save the center of the paint area
		int paintAreaCenter = currentVertCenter;

		// set the current verticle center to the ELR unit
		currentVertCenter = x + (int) paintAllInformationArea.getWidth()/4;

		// draw the relationship lines and text
		g.setColor(text);
		g.drawLine(currentVertCenter, currentTop, currentVertCenter + (int) paintAllInformationArea.getWidth()/2, currentTop);
		g.drawLine(currentVertCenter, currentTop, currentVertCenter, currentTop + 3 * spaceBetweenLines);
		g.drawLine(currentVertCenter + (int) paintAllInformationArea.getWidth()/2, currentTop, currentVertCenter + (int) paintAllInformationArea.getWidth()/2, currentTop + 3 * spaceBetweenLines);
		if (reducedUnitTypeID != NO_UNIT_TYPE_ID || parentSquadTypeID != NO_UNIT_TYPE_ID){
			g.drawLine(paintAreaCenter, currentTop - spaceBetweenLines, paintAreaCenter, currentTop + 20 * spaceBetweenLines);
		}
		currentTop += 4 * spaceBetweenLines;

		tempRect1	= fm.getStringBounds("ELR to :", g);
		g.drawString(
			"ELR to :",
			currentVertCenter - (int) tempRect1.getWidth()/2,
			currentTop + (int) tempRect1.getHeight()
		);

		tempRect1	= fm.getStringBounds("Battle harden to :", g);
		g.drawString(
			"Battle hardens to :",
			currentVertCenter + (int) paintAllInformationArea.getWidth()/2 - (int) tempRect1.getWidth()/2,
			currentTop + (int) tempRect1.getHeight()
		);

		currentTop += (int) tempRect1.getHeight() + spaceBetweenLines;

		// need to save the current top to print the reduction unit
		int topOfELRUnits = currentTop;

		// paint the ELR unit
		if (ELRUnitTypeID != NO_UNIT_TYPE_ID){

			paintRect = ELRUnitType.paintCounterImageAndName(topOfELRUnits, currentVertCenter, spaceBetweenLines, text, g, obs);
			currentTop 	= (int) paintRect.getMaxY();
		}

		// set the current verticle center
		currentVertCenter = x + (int) paintAllInformationArea.getWidth() - (int) paintAllInformationArea.getWidth()/4;

		// paint the ELR from unit
		if (ELRFromUnitTypeID != NO_UNIT_TYPE_ID && ELRFromUnitType != null){

			paintRect = ELRFromUnitType.paintCounterImageAndName(topOfELRUnits, currentVertCenter, spaceBetweenLines, text, g, obs);
			currentTop 	= (int) paintRect.getMaxY();
		}

		// paint the reduction unit
		if (reducedUnitTypeID != NO_UNIT_TYPE_ID && reducedUnitType != null){

			paintRect = reducedUnitType.paintCounterImageAndName(topOfELRUnits + 7 * spaceBetweenLines, paintAreaCenter, spaceBetweenLines, text, g, obs);
			currentTop 	= (int) paintRect.getMaxY();
		}

		// paint the parent squad
		else if (parentSquadTypeID != NO_UNIT_TYPE_ID && parentSquadType != null){

			paintRect 	= parentSquadType.paintCounterImageAndName(topOfELRUnits + 7 * spaceBetweenLines, paintAreaCenter, spaceBetweenLines, text, g, obs);
			currentTop 	= (int) paintRect.getMaxY();
		}

		return currentTop;
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

		int checkImageHeight 	= checkImage.getHeight(obs);
		@SuppressWarnings("unused")
    int checkImageWidth	= checkImage.getWidth(obs);

		// first line
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

		// second line
		tempRect1	= fm.getStringBounds("Assault fire:", g);
		tempRect2	= fm.getStringBounds("Spaying fire:", g);
		tempRect3	= fm.getStringBounds("Underlined:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), Math.max(tempRect2.getHeight(), tempRect3.getHeight())) + spaceBetweenLines;
		g.drawString("Assault fire:", leftTextMargin, currentTop);
		g.drawString("Spaying fire:", centerTextMargin, currentTop);
		g.drawString("Underlined:", rightTextMargin, currentTop);
		if (assaultFire) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (sprayingFire) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (underlinedMorale) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// third line
		tempRect1	= fm.getStringBounds("Smoke exponent:", g);
		currentTop 	+= (int) tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Smoke exponent:", leftTextMargin, currentTop);
		g.drawString(Integer.toString(smokeExponent), leftInfoMargin, currentTop);

		// fourth line
		tempRect1	= fm.getStringBounds("WP grenades:", g);
		currentTop 	+= (int) tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("WP grenades:", leftTextMargin, currentTop);
		if (WPGrenades) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
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

		int checkImageHeight 	= checkImage.getHeight(obs);
		@SuppressWarnings("unused")
    int checkImageWidth	= checkImage.getWidth(obs);

		// header
		tempRect1	= fm.getStringBounds("Special capabilities:", g);
		currentTop 	+= (int) 2 * tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Special capabilities:", x + 5, currentTop);

		// first line
		tempRect1	= fm.getStringBounds("Self rally:", g);
		tempRect2	= fm.getStringBounds("Stealthy:", g);
		tempRect3	= fm.getStringBounds("MOL:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), Math.max(tempRect2.getHeight(), tempRect3.getHeight())) + spaceBetweenLines;
		g.drawString("Self rally:", leftTextMargin, currentTop);
		g.drawString("Stealthy:", centerTextMargin, currentTop);
		g.drawString("MOL:", rightTextMargin, currentTop);
		if (selfRally) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (stealthy) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (MOL) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// second line
		tempRect1	= fm.getStringBounds("May deploy:", g);
		tempRect2	= fm.getStringBounds("Lax:", g);
		tempRect3	= fm.getStringBounds("PF:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), Math.max(tempRect2.getHeight(), tempRect3.getHeight())) + spaceBetweenLines;
		g.drawString("May deploy:", leftTextMargin, currentTop);
		g.drawString("Lax:", centerTextMargin, currentTop);
		g.drawString("PF:", rightTextMargin, currentTop);
		if (deploy) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (lax) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (PF) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// third line
		tempRect1	= fm.getStringBounds("Combat eng:", g);
		tempRect2	= fm.getStringBounds("Untrained boat:", g);
		tempRect3	= fm.getStringBounds("ATMM:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect3.getHeight()) + spaceBetweenLines;
		g.drawString("Combat eng:", leftTextMargin, currentTop);
		g.drawString("Untrained boat:", centerTextMargin, currentTop);
		g.drawString("ATMM:", rightTextMargin, currentTop);
		if (combatEngineer) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (untrainedBoatUse) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (ATMM) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// fourth line
		tempRect1	= fm.getStringBounds("Assault eng:", g);
		tempRect2	= fm.getStringBounds("Sewer movement:", g);
		tempRect3	= fm.getStringBounds("Ski equipped:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect3.getHeight()) + spaceBetweenLines;
		g.drawString("Assault eng:", leftTextMargin, currentTop);
		g.drawString("Sewer movement:", centerTextMargin, currentTop);
		g.drawString("Ski equipped:", rightTextMargin, currentTop);
		if (assaultEngineer) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (sewerMovement) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (skiEquipped) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// fifth line
		tempRect1	= fm.getStringBounds("Commando:", g);
		tempRect2	= fm.getStringBounds("Ammo shortage:", g);
		tempRect3	= fm.getStringBounds("Winter camo:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), tempRect3.getHeight()) + spaceBetweenLines;
		g.drawString("Commando:", leftTextMargin, currentTop);
		g.drawString("Ammo shortage:", centerTextMargin, currentTop);
		g.drawString("Winter camo:", rightTextMargin, currentTop);
		if (commando) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (ammoShortage) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (winterCamouflage) {

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

		int checkImageHeight 	= checkImage.getHeight(obs);
		@SuppressWarnings("unused")
    int checkImageWidth	= checkImage.getWidth(obs);

		// header
		tempRect1	= fm.getStringBounds("Current status:", g);
		currentTop 	+= (int) 2 * tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("Current status:", x + 5, currentTop);

		// first line
		tempRect1	= fm.getStringBounds("Broken:", g);
		tempRect2	= fm.getStringBounds("TI:", g);
		tempRect3	= fm.getStringBounds("Disrupted:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), Math.max(tempRect2.getHeight(), tempRect3.getHeight())) + spaceBetweenLines;
		g.drawString("Broken:", leftTextMargin, currentTop);
		g.drawString("TI:", centerTextMargin, currentTop);
		g.drawString("Disrupted:", rightTextMargin, currentTop);
		if (broken) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (TI) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (disrupted) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// second line
		tempRect1	= fm.getStringBounds("DM:", g);
		tempRect2	= fm.getStringBounds("Berserk:", g);
		tempRect3	= fm.getStringBounds("Encircled:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), Math.max(tempRect2.getHeight(), tempRect3.getHeight())) + spaceBetweenLines;
		g.drawString("DM:", leftTextMargin, currentTop);
		g.drawString("Berserk:", centerTextMargin, currentTop);
		g.drawString("Encircled:", rightTextMargin, currentTop);
		if (DM) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (berserk) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (encircled) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// third line
		tempRect1	= fm.getStringBounds("Pinned:", g);
		tempRect2	= fm.getStringBounds("Fanatic:", g);
		tempRect3	= fm.getStringBounds("Captured:", g);
		currentTop 	+= (int) Math.max(tempRect1.getHeight(), Math.max(tempRect2.getHeight(), tempRect3.getHeight())) + spaceBetweenLines;
		g.drawString("Pinned:", leftTextMargin, currentTop);
		g.drawString("Fanatic:", centerTextMargin, currentTop);
		g.drawString("Captured:", rightTextMargin, currentTop);
		if (pinned) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (fanatic) {

			g.drawImage(
				checkImage,
				centerInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}
		if (captured) {

			g.drawImage(
				checkImage,
				rightInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		// fourth line
		tempRect1	= fm.getStringBounds("Pinned:", g);
		currentTop 	+= (int) tempRect1.getHeight() + spaceBetweenLines;
		g.drawString("CX:", leftTextMargin, currentTop);
		if (CX) {

			g.drawImage(
				checkImage,
				leftInfoMargin,
				currentTop - checkImageHeight,
				obs);
		}

		return currentTop;
	}

	// menu functions
	public JPopupMenu getSetupMenu(ActionListener listener){

		// create the menu
		JPopupMenu	menu = new JPopupMenu();
		menu.setFont(new Font("Dialog", 0, 11));

		JMenu		elrMenu		= new JMenu("ELR");
		JMenu		capabilitiesMenu	= new JMenu("Special Capabilities");
		JMenu		weaponsMenu		= new JMenu("Special Weapons");
		JMenuItem	item 			= new JMenuItem();

		// create the ELR menu
		ButtonGroup group = new ButtonGroup();

		item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem("ELR = 0"));
		item.addActionListener(listener);
		if (ELR == 0) item.setSelected(true);
		elrMenu.add(item);
		group.add(item);

		item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem("ELR = 1"));
		item.addActionListener(listener);
		if (ELR == 1) item.setSelected(true);
		group.add(item);

		item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem("ELR = 2"));
		item.addActionListener(listener);
		if (ELR == 2) item.setSelected(true);
		group.add(item);

		item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem("ELR = 3"));
		item.addActionListener(listener);
		if (ELR == 3) item.setSelected(true);
		group.add(item);

		item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem("ELR = 4"));
		item.addActionListener(listener);
		if (ELR == 4) item.setSelected(true);
		group.add(item);

		item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem("ELR = 5"));
		item.addActionListener(listener);
		if (ELR == 5) item.setSelected(true);
		group.add(item);

		menu.add(elrMenu);

		// create the nationality menu
		if (nationality == BRITISH){

			JMenu		nationalityMenu	= new JMenu("Nationality");
			group = new ButtonGroup();

			item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem(nationalitySubtypeNames[NO_NATIONALITY_SUBTYPE]));
			item.addActionListener(listener);
			if (nationalitySubtype == NO_NATIONALITY_SUBTYPE) item.setSelected(true);
			nationalityMenu.add(item);
			group.add(item);

			item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem(nationalitySubtypeNames[GURKA]));
			item.addActionListener(listener);
			if (nationalitySubtype == GURKA) item.setSelected(true);
			nationalityMenu.add(item);
			group.add(item);

			item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem(nationalitySubtypeNames[ANZAC]));
			item.addActionListener(listener);
			if (nationalitySubtype == ANZAC) item.setSelected(true);
			nationalityMenu.add(item);
			group.add(item);

			item = (JRadioButtonMenuItem) elrMenu.add(new JRadioButtonMenuItem(nationalitySubtypeNames[FREE_FRENCH]));
			item.addActionListener(listener);
			if (nationalitySubtype == FREE_FRENCH) item.setSelected(true);
			nationalityMenu.add(item);
			group.add(item);

			menu.add(nationalityMenu);
		}
		else if (nationality == ALLIED_MINOR) {

			JMenu		nationalityMenu	= new JMenu("Nationality");

			item = new JMenuItem (nationalitySubtypeNames[NO_NATIONALITY_SUBTYPE]);
			item.addActionListener(listener);
			nationalityMenu.add(item);
			item = new JMenuItem (nationalitySubtypeNames[GREEK]);
			item.addActionListener(listener);
			nationalityMenu.add(item);
			item = new JMenuItem (nationalitySubtypeNames[YOGOSLAVIAN]);
			item.addActionListener(listener);
			nationalityMenu.add(item);

			menu.add(nationalityMenu);
		}

		// create the special capabilities menu
		item = new JCheckBoxMenuItem("Elite");
		if (elite) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Stealthy");
		if (stealthy) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Lax");
		if (lax) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Deployable");
		if (deploy) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Ski Equipped");
		if (skiEquipped) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Winter Camouflage");
		if (winterCamouflage) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Commando");
		if (commando) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Combat Engineer");
		if (combatEngineer) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Assault Engineer");
		if (assaultEngineer) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Untrained Boat Use");
		if (untrainedBoatUse) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Sewer Movement");
		if (sewerMovement) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		item = new JCheckBoxMenuItem("Ammo Shortage");
		if (ammoShortage) item.setSelected(true);
		item.addActionListener(listener);
		capabilitiesMenu.add(item);

		menu.add(capabilitiesMenu);

		// create the weapons menu
		item = new JCheckBoxMenuItem("ATMM");
		if (ATMM) item.setSelected(true);
		item.addActionListener(listener);
		weaponsMenu.add(item);

		item = new JCheckBoxMenuItem("MOL");
		item.addActionListener(listener);
		if (MOL) item.setSelected(true);
		weaponsMenu.add(item);

		item = new JCheckBoxMenuItem("PF");
		item.addActionListener(listener);
		if (PF) item.setSelected(true);
		weaponsMenu.add(item);

		menu.add(weaponsMenu);

		return menu;
	}

	public boolean setupMenuHandler(ActionEvent e) {

		boolean changed = false;

		// get the command
		String cmd = e.paramString().substring(e.paramString().indexOf('=') + 1);

		// execute the command
		if      (cmd.equals("ATMM"))		{ATMM = !ATMM;	changed = true;}
		else if (cmd.equals("PF"))		{PF = !PF;		changed = true;}
		else if (cmd.equals("MOL"))		{MOL = !MOL;	changed = true;}
		else if (cmd.equals("ELR = 0"))	{ELR = 0;		changed = true;}
		else if (cmd.equals("ELR = 1"))	{ELR = 1;		changed = true;}
		else if (cmd.equals("ELR = 2"))	{ELR = 2;		changed = true;}
		else if (cmd.equals("ELR = 3"))	{ELR = 3;		changed = true;}
		else if (cmd.equals("ELR = 4"))	{ELR = 4;		changed = true;}
		else if (cmd.equals("ELR = 5"))	{ELR = 5;		changed = true;}

		else if (cmd.equals("Elite"))		{elite = !elite;					changed = true;}
		else if (cmd.equals("Stealthy"))		{stealthy = !stealthy;				changed = true;}
		else if (cmd.equals("Lax"))			{lax = !lax;					changed = true;}
		else if (cmd.equals("Deployable"))		{deploy = !deploy;				changed = true;}
		else if (cmd.equals("Ski Equipped"))	{skiEquipped = !skiEquipped;			changed = true;}
		else if (cmd.equals("Winter Camouflage")){winterCamouflage = !winterCamouflage;	changed = true;}
		else if (cmd.equals("Commando"))		{commando = !commando;				changed = true;}
		else if (cmd.equals("Combat Engineer"))	{combatEngineer = !combatEngineer;		changed = true;}
		else if (cmd.equals("Assault Engineer"))	{assaultEngineer = !assaultEngineer;	changed = true;}
		else if (cmd.equals("Untrained Boat Use")){untrainedBoatUse = !untrainedBoatUse;	changed = true;}
		else if (cmd.equals("Sewer Movement"))	{sewerMovement = !sewerMovement;		changed = true;}
		else if (cmd.equals("Ammo Shortage"))	{ammoShortage = !ammoShortage;		changed = true;}

		return changed;
	}

//	public void		popupMenuEvent(String actionEvent){}
}

class ToggleUIListener implements ItemListener {
	public void itemStateChanged(ItemEvent e) {}
}
