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
package VASL.LOS;

import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Terrain;
import VASL.LOS.counters.*;
import VASL.build.module.ASLMap;
import VASL.counters.ASLProperties;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.tools.imports.adc2.ADC2Module;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * This class provides access to VASL game information. E.g. the location and status of vehicles, counter terrain, etc.
 */
public class VASLGameInterface {

    ASLMap gameMap;
    VASL.LOS.Map.Map LOSMap;

    // the LOS counter rules from the shared metadata file
    LinkedHashMap<String, CounterMetadata> counterMetadata;

    // the marker used to tag units for DB play
    private final static String DB_MARKER_KEY = "isDBUnit";

    // the counter lists
    protected HashMap<Hex, Terrain> terrainList;
    protected HashMap<Hex, HashSet<Smoke>> smokeList;
    protected HashMap<Hex, HashSet<OBA>> OBAList;
    protected HashMap<Hex, HashSet<Wreck>> wreckList;
    protected HashMap<Hex, HashSet<Vehicle>> vehicleList;

    // this is a list of all location counters - not those currently on the board
    protected static HashMap<String, LocationCounter> locationCounterList = null;

    // empty lists are used a lot so create them once
    protected final static HashSet<Vehicle> emptyVehicleList = new HashSet<Vehicle>(0);
    protected final static HashSet<OBA> emptyOBAList = new HashSet<OBA>(0);
    protected final static HashSet<Smoke> emptySmokeList = new HashSet<Smoke>(0);
    protected final static HashSet<Wreck> emptyWreckList = new HashSet<Wreck>(0);

	public VASLGameInterface(ASLMap GameMap, VASL.LOS.Map.Map LOSMap) {

        this.gameMap = GameMap;
        this.LOSMap = LOSMap;

        // get the counter metadata and location counters
        CounterMetadataFile counterMetadataFile = new CounterMetadataFile();
        counterMetadata =  counterMetadataFile.getMetadataElements();
        createLocationCounters();
	}

    /**
     * Updates the game pieces, creating a snapshot of the game LOS counters
     */
    public void updatePieces() {

        printCounterLocations();

        // reset the counter lists
        smokeList = new HashMap<Hex, HashSet<Smoke>>();
        terrainList = new HashMap<Hex, Terrain>();
        OBAList = new HashMap<Hex, HashSet<OBA>>();
        wreckList = new HashMap<Hex, HashSet<Wreck>>();
        vehicleList = new HashMap<Hex, HashSet<Vehicle>>();

        // get all of the game pieces
        GamePiece[] p = gameMap.getPieces();

        // add each of the pieces
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    updatePiece(pi.nextPiece());
                }
            } else {
                updatePiece(aP);
            }
        }
    }

    /**
     * Updates a single piece creating a counter object if necessary
     * @param piece the piece
     */
    private void updatePiece(GamePiece piece) {

        // determine what hex and location the piece is in
        Point p = gameMap.mapCoordinates(new Point(piece.getPosition()));
        p.x *= gameMap.getZoom();
        p.y *= gameMap.getZoom();
        p.translate(-gameMap.getEdgeBuffer().width, -gameMap.getEdgeBuffer().height);

        if (!LOSMap.onMap(p.x, p.y)) return;
        Hex h = LOSMap.gridToHex(p.x, p.y);

        String name = piece.getName().trim();

        // ignore any piece whose name is prefixed by an ignore-type counter
        for(CounterMetadata counter: counterMetadata.values()){

            if(counter.getType() == CounterMetadata.CounterType.IGNORE && name.startsWith(counter.getName())){
                return;
            }
        }

        // add the piece
        if (!Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME))) {

            CounterMetadata counter = counterMetadata.get(name);

            if(counter != null) {

                // add counter object to the appropriate list
                switch (counter.getType()) {
                    case OBA:
                        OBA oba = new OBA(counter.getName(), h);
                        addCounter(OBAList, oba, h);

                        break;
                    case TERRAIN:
                        // we assume there is only one terrain-type counter in a hex
                        terrainList.put(h, LOSMap.getTerrain(counter.getTerrain()));
                        break;

                    case SMOKE:
                        Smoke smoke = new Smoke(counter.getName(), h.getNearestLocation(p.x, p.y), counter.getHeight(), counter.getHindrance());
                        addCounter(smokeList, smoke, h);
                        break;

                    case WRECK:
                        // treat wrecks as vehicles for now
                        Vehicle vehicle = new Vehicle(name, h.getCenterLocation());
                        addCounter(vehicleList, vehicle, h);
                        break;
                }
            }

            // add vehicles
            //TODO: assuming all hindrance counters that have no rule are vehicles - not good
            else if(piece.getProperty(ASLProperties.HINDRANCE) != null && !Boolean.TRUE.equals(piece.getProperty(Properties.MOVED))){

                Vehicle v = new Vehicle(name, h.getNearestLocation(p.x, p.y));
                addCounter(vehicleList, v, h);
            }
        }
    }

    /**
     * Create a location counters for all location counters in the metadata
     */
    private void createLocationCounters(){

        // only do this once
        if(locationCounterList == null){

            locationCounterList = new HashMap<String, LocationCounter>();

            for(CounterMetadata c: counterMetadata.values()) {

                LocationCounter lc;
                switch (c.getType()) {
                    case BUILDING_LEVEL:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.BUILDING_LEVEL);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case ROOF:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.ROOF);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case ENTRENCHMENT:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.ENTRENCHMENT);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case CLIMB:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.CLIMB);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                    case CREST:
                        lc = new LocationCounter(c, LocationCounter.LocationCounterType.CREST);
                        locationCounterList.put(lc.getName(), lc);
                        break;

                }
            }
        }
    }

    /**
     * Adds a counter object to the counter list
     * @param hashMap the counter list
     * @param counter the object to add
     * @param h the hex location
     */
    private void addCounter(HashMap hashMap, Counter counter, Hex h) {

        @SuppressWarnings("unchecked")
        HashMap<Hex, HashSet<Counter>> hm = (HashMap<Hex, HashSet<Counter>>) hashMap;

        HashSet<Counter> list = hm.get(h);
        if(list == null) {

            list = new HashSet<Counter>();
            list.add(counter);
            hm.put(h, list);
        }
        else {
            list.add(counter);
        }
    }

    /**
     * Get the set of vehicle counters in the given hex
     * @param h the hex
     * @return the set of vehicle counters - if none an empty list is returned
     */
    public HashSet<Vehicle> getVehicles(Hex h){

		if(vehicleList.get(h) != null){
            return vehicleList.get(h);
        }
        else {
            return emptyVehicleList;
        }
	}

    /**
     * Gets smoke counters in the given hex
     * @param hex the hex
     * @return the set of smoke counters - if none an empty list is returned
     */
    public HashSet<Smoke> getSmoke(Hex hex) {

        if(smokeList.get(hex) != null){
            return  smokeList.get(hex);
        }
        else {
            return emptySmokeList;
        }
    }

    /**
     * Get the terrain counter in the given hex
     * @param hex the hex
     * @return the terrain
     */
    public Terrain getTerrain(Hex hex) {
        return terrainList.get(hex);
    }

    /**
     * Get the OBA in the given hex
     * @param hex the hex
     * @return the set of OBA counters in the hex - if none an empty list is returned
     */
    public HashSet<OBA> getOBA(Hex hex){

        if(OBAList.get(hex) != null){
            return OBAList.get(hex);
        }
        else {
            return emptyOBAList;
        }
    }

    /**
     * @return all OBA counters - if none an empty list is returned
     */
    public HashSet<OBA> getOBA(){
        HashSet<OBA> OBACounters = new HashSet<OBA>();
        for(Hex hex : OBAList.keySet()) {
            OBACounters.addAll(getOBA(hex));
        }
        return OBACounters;
    }

    /**
     * Get the wrecks in the given hex
     * @param hex the hex
     * @return the set of wreck counters - if none an empty list is returned
     */
    public HashSet<Wreck> getWreck(Hex hex){

        if(wreckList.get(hex) != null){
            return wreckList.get(hex);
        }
        else {
            return emptyWreckList;
        }
    }

    /**
     * Finds the location for the given piece
     * @param piece the piece
     * @return the location - null if error or none
     */
    public Location getLocation(GamePiece piece) {

        // determine what hex and location the piece is in
        Point p = gameMap.mapCoordinates(new Point(piece.getPosition()));
        p.x *= gameMap.getZoom();
        p.y *= gameMap.getZoom();
        p.translate(-gameMap.getEdgeBuffer().width, -gameMap.getEdgeBuffer().height);

        if (gameMap == null || gameMap.getVASLMap() == null || !gameMap.getVASLMap().onMap(p.x, p.y)) {
            return null;
        }

        Hex h = LOSMap.gridToHex(p.x, p.y);
        Location location = h.getNearestLocation(p.x, p.y);
        LocationCounter locationCounter = getLocationCounterForPiece(piece);

        // if no location return the nearest location
        if (locationCounter == null) {
            return location;
        }

        // otherwise create a location appropriately
        else {

            switch (locationCounter.getType()) {
                case BUILDING_LEVEL:

                    // try to find the building location
                    Location l = location;
                    for(int x = 0; x < locationCounter.getLevel(); x++) {
                        if(l.getUpLocation() != null) {
                            l = l.getUpLocation();
                        }
                    }
                    if(l.getBaseHeight() == locationCounter.getLevel()) {
                        return l;
                    }

                    // otherwise create a new location
                    else {
                        Location newLocation = new Location(location);
                        newLocation.setBaseHeight(locationCounter.getLevel());
                        newLocation.setDownLocation(location);
                        return newLocation;
                    }

                case CLIMB:
                    break;

                case ENTRENCHMENT:
                    Location newLocation = new Location(location);
                    newLocation.setUpLocation(location);
                    newLocation.setTerrain(LOSMap.getTerrain("Foxholes")); // use foxholes as all fortifications have the same LOS rules
                    return newLocation;

                case ROOF:
                    break;

                case CREST:
                    break;
            }



            return location;
        }
    }


    /**
     * @param piece a game piece
     * @return true if piece has DB marker set
     */
    public boolean isDBUnit(GamePiece piece) {

        if(piece.getProperty(DB_MARKER_KEY) != null) {
            return true;
        }
        return false;
    }

    private void printCounterLocations(){

        GamePiece[] p = gameMap.getPieces();
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece p2 = pi.nextPiece();
                    LocationCounter location = getLocationCounterForPiece(p2);
                    if(location == null) {
                        System.out.println(p2.getName() + " has no location counter piece");
                    }
                    else {
                        System.out.println(p2.getName()  + " is in location " + location.getName());
                    }
                }
            } else {
                LocationCounter location = getLocationCounterForPiece(aP);
                if(location == null) {
                    System.out.println(aP.getName() + " has no location counter piece");
                }
                else {
                    System.out.println(aP.getName()  + " is in location " + location.getName());
                }
            }
        }
    }

    /**
     * Find the appropriate location counter for a piece
     * Location counters beneath a unit have preference over those above
     * @param piece the piece
     * @return the location counter for the given piece
     */
    private LocationCounter getLocationCounterForPiece (GamePiece piece) {

        Stack stack = piece.getParent();

        // single counter?
        if(stack == null || stack.getPieceCount() == 1) {
            return locationCounterList.get(piece.getName());
        }

        // search downward first - those location counters have precedence
        GamePiece somePiece = piece;
        LocationCounter locationCounter;
        while (somePiece != null) {

            locationCounter = locationCounterList.get(somePiece.getName());
            if(locationCounter != null && locationCounter.isAbovePiece()) {
                return locationCounter;
            }
            somePiece = stack.getPieceBeneath(somePiece);
        }

        // search the stack upward
        somePiece = piece;
        while (somePiece != null) {

            locationCounter = locationCounterList.get(somePiece.getName());
            if(locationCounter != null && locationCounter.isBelowPiece()) {
                return locationCounter;
            }
            somePiece = stack.getPieceAbove(somePiece);
        }
        return null;
    }
}
