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
import java.awt.geom.AffineTransform;

/**
 * Title:        Bridge.java
 * Copyright:    Copyright (c) 2001 David Sullivan Zuericher Strasse 6 12205 Berlin Germany. All rights reserved.
 * @author       David Sullivan
 * @version      1.0
 */
public class Bridge {

	// public constants
	public static final int	CUSTOM_BRIDGE_WIDTH	= (int) Map.GEO_HEX_HEIGHT /4;
	public static final int	CUSTOM_BRIDGE_HEIGHT	= (int) Map.GEO_HEX_HEIGHT;
	public static final int	ROAD_AREA_INSET		= 2;

	public static final int	SINGLE_HEX_CUSTOM_BRIDGE_WIDTH	= 32;
	public static final int	SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT	= 48;
	public static final int	SINGLE_HEX_ROAD_AREA_INSET		= 9;

	// private variables
	private Terrain terrain;	// woodBridge or stoneBridge
	private int			roadLevel;	// absolute level of the road
	private int			rotation;	// orientation of road
	private Location	location;	// unit location on bridge
	private boolean     singleHex;	// single hex bridge or span two hexsides?
	private Point       center;     // center point of bridge
	private	transient Shape       shape;
	private	transient Shape       roadShape;

	// constructors
	public Bridge(
		Terrain newTerrain,
		int			newRoadLevel,
		int			newRotation,
		Location	newLocation,
		boolean     newSingleHex,
		Point       newCenter
	) {

		terrain			= newTerrain;
		roadLevel		= newRoadLevel;
		rotation		= newRotation;
		location		= newLocation;
		singleHex		= newSingleHex;
		center          = newCenter;

		setShape();
		setRoadShape();
	}

	public Bridge(
		Terrain newTerrain,
		int			newRoadLevel,
		int			newRotation,
		Location	newLocation,
		boolean     newSingleHex
	) {

		terrain			= newTerrain;
		roadLevel		= newRoadLevel;
		rotation		= newRotation;
		location		= newLocation;
		singleHex		= newSingleHex;
		center          = new Point(0, 0);

		setShape();
		setRoadShape();
	}

	public Terrain getTerrain(){ return terrain;}
	public int			getRoadLevel(){ return roadLevel;}
	public int			getRotation(){ return rotation;}
	public Location		getLocation(){ return location;}
	public void			setLocation(Location newLocation){ location = newLocation;}
	public Point 		getCenter(){ return center;}
	public void	setCenter(Point newCenter){

		center = newCenter;
		setShape();
		setRoadShape();
	}

	public void	setRotation(int newRotation){

		rotation = newRotation;
		setShape();
		setRoadShape();
	}
	public boolean      isSingleHex(){return singleHex;}
	public Shape        getShape(){

		if (shape == null){

			setShape();
		}
		return shape;
	}
	public Shape        getRoadShape(){

		if (roadShape == null){

			setRoadShape();
		}
		return roadShape;
	}

	private void setShape(){

		AffineTransform at = AffineTransform.getRotateInstance(
			Math.toRadians(rotation), (int) center.getX(), (int) center.getY());

		if (singleHex){

			shape = at.createTransformedShape(new Rectangle(
				(int) center.getX() - SINGLE_HEX_CUSTOM_BRIDGE_WIDTH/2,
				(int) center.getY() - SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT/2,
				SINGLE_HEX_CUSTOM_BRIDGE_WIDTH,
				SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT));
		}
		else {

			shape = at.createTransformedShape(new Rectangle(
				(int) center.getX() - CUSTOM_BRIDGE_WIDTH/2,
				(int) center.getY() - CUSTOM_BRIDGE_HEIGHT/2,
				CUSTOM_BRIDGE_WIDTH,
				CUSTOM_BRIDGE_HEIGHT));
		}
	}

	private void setRoadShape(){

		AffineTransform at = AffineTransform.getRotateInstance(
			Math.toRadians(rotation), (int) center.getX(), (int) center.getY());

		if (singleHex){

			roadShape = at.createTransformedShape(new Rectangle(
				(int) center.getX() - SINGLE_HEX_CUSTOM_BRIDGE_WIDTH/2 + SINGLE_HEX_ROAD_AREA_INSET,
				(int) center.getY() - SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT/2,
				SINGLE_HEX_CUSTOM_BRIDGE_WIDTH - SINGLE_HEX_ROAD_AREA_INSET * 2,
				SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT));
		}
		else {

			roadShape = at.createTransformedShape(new Rectangle(
				(int) center.getX() - CUSTOM_BRIDGE_WIDTH/2 + ROAD_AREA_INSET,
				(int) center.getY() - CUSTOM_BRIDGE_HEIGHT/2,
				CUSTOM_BRIDGE_WIDTH - ROAD_AREA_INSET * 2,
				CUSTOM_BRIDGE_HEIGHT));
		}
	}
}

/*
	public Shape getShape(){

		AffineTransform at = AffineTransform.getRotateInstance(
			Math.toRadians(rotation), (int) center.getX(), (int) center.getY());

		if (singleHex){

			return at.createTransformedShape(new Rectangle(
				(int) center.getX() - SINGLE_HEX_CUSTOM_BRIDGE_WIDTH/2,
				(int) center.getY() - SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT/2,
				SINGLE_HEX_CUSTOM_BRIDGE_WIDTH,
				SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT));
		}
		else {

			return at.createTransformedShape(new Rectangle(
				(int) center.getX() - CUSTOM_BRIDGE_WIDTH/2,
				(int) center.getY() - CUSTOM_BRIDGE_HEIGHT/2,
				CUSTOM_BRIDGE_WIDTH,
				CUSTOM_BRIDGE_HEIGHT));
		}
	}

	public Shape getRoadShape(){

		AffineTransform at = AffineTransform.getRotateInstance(
			Math.toRadians(rotation), (int) center.getX(), (int) center.getY());

		if (singleHex){

			return at.createTransformedShape(new Rectangle(
				(int) center.getX() - SINGLE_HEX_CUSTOM_BRIDGE_WIDTH/2 + SINGLE_HEX_ROAD_AREA_INSET,
				(int) center.getY() - SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT/2,
				SINGLE_HEX_CUSTOM_BRIDGE_WIDTH - SINGLE_HEX_ROAD_AREA_INSET * 2,
				SINGLE_HEX_CUSTOM_BRIDGE_HEIGHT));
		}
		else {

			return at.createTransformedShape(new Rectangle(
				(int) center.getX() - CUSTOM_BRIDGE_WIDTH/2 + ROAD_AREA_INSET,
				(int) center.getY() - CUSTOM_BRIDGE_HEIGHT/2,
				CUSTOM_BRIDGE_WIDTH - ROAD_AREA_INSET * 2,
				CUSTOM_BRIDGE_HEIGHT));
		}
	}
*/
