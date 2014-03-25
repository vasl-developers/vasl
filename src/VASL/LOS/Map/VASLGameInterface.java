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

import VASL.build.module.ASLMap;
import VASL.build.module.map.boardArchive.LOSCounterRule;
import VASL.counters.ASLProperties;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;

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
    LinkedHashMap<String, LOSCounterRule> LOSCounterRules;

    // the counter lists
    protected HashMap<Hex, Terrain> terrainList;
    protected HashMap<Hex, HashSet<Smoke>> smokeList;
    protected HashMap<Hex, HashSet<OBA>> OBAList;
    protected HashMap<Hex, HashSet<Wreck>> wreckList;
    protected HashMap<Hex, HashSet<Vehicle>> vehicleList;

    // empty lists are used a lot so create them once
    protected final static HashSet<Vehicle> emptyVehicleList = new HashSet<Vehicle>(0);
    protected final static HashSet<OBA> emptyOBAList = new HashSet<OBA>(0);
    protected final static HashSet<Smoke> emptySmokeList = new HashSet<Smoke>(0);
    protected final static HashSet<Wreck> emptyWreckList = new HashSet<Wreck>(0);

	public VASLGameInterface(ASLMap GameMap, VASL.LOS.Map.Map LOSMap) {

        this.gameMap = GameMap;
        this.LOSMap = LOSMap;

        LOSCounterRules =  ASLMap.getSharedBoardMetadata().getLOSCounterRules();
	}

    /**
     * Updates the game pieces, creating a snapshot of the game LOS counters
     */
    public void updatePieces() {

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

        // ignore any piece whose name is prefixed by an ignore rule
        for(LOSCounterRule rule: LOSCounterRules.values()){

            if(rule.getType() == LOSCounterRule.CounterType.IGNORE && name.startsWith(rule.getName())){
                return;
            }
        }

        // add the piece
        if (!Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME))) {

            LOSCounterRule rule = LOSCounterRules.get(name);

            if(rule != null) {

                // add counter object to the appropriate list
                switch (rule.getType()) {
                    case OBA:
                        OBA oba = new OBA(rule.getName(), h);
                        addCounter(OBAList, oba, h);

                        break;
                    case TERRAIN:
                        // we assume there is only one terrain-type counter in a hex
                        terrainList.put(h, LOSMap.getTerrain(rule.getTerrain()));
                        break;

                    case SMOKE:
                        Smoke smoke = new Smoke(rule.getName(), h.getNearestLocation(p.x, p.y), rule.getHeight(), rule.getHindrance());
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
}
