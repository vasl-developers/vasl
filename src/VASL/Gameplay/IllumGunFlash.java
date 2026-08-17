package VASL.Gameplay;

import VASL.Gamedata.IllumGunFlashDataSet;
import VASL.Gamedata.IllumGunFlashMetadata;
import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Map;
import VASL.LOS.counters.*;
import VASL.build.module.ASLMap;
import VASL.counters.ASLProperties;
import VASSAL.build.GameModule;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;

import java.awt.*;
import java.util.*;
import java.util.List;

import static VASSAL.build.GameModule.getGameModule;

/**
 * This class is based on VASLGameInterface and is meant to establish a pattern for
 * obtaining and accessing Game play data
 */
public class IllumGunFlash {

    public ASLMap gameMap;
    VASL.LOS.Map.Map LOSMap;
    // the LOS counter rules from the shared metadata file
    LinkedHashMap<String, IllumGunFlashMetadata> illumGunFlashMetadata;
    // list of hexes with illumination or gunflash counters;
    public HashMap<Hex, IllumGunFlashMetadata> illumRoundHexMap;
    protected HashMap<Hex, IllumGunFlashMetadata> starshellHexList;
    protected HashMap<Hex, IllumGunFlashMetadata> flameHexList;
    protected HashMap<Hex, IllumGunFlashMetadata> blazeHexList;
    protected HashMap<Hex, IllumGunFlashMetadata> tripflareHexList;
    protected HashMap<Hex, IllumGunFlashMetadata> searchlightHexList;
    protected HashMap<Hex, IllumGunFlashMetadata> gunflashHexList;

    public ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>> illumRoundList;
    public ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>> starshellList;
    public ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>> flameList;
    public ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>> blazeList;
    public ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>> tripflareList;
    public ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>> searchlightList;
    public ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>> gunflashList;

    //Constructor
    public IllumGunFlash(ASLMap GameMap, VASL.LOS.Map.Map LOSMap) {
        this.gameMap = GameMap;
        this.LOSMap = LOSMap;
        // change for losgui
        if (GameMap == null && LOSMap == null){return;}
        VASL.Gamedata.IllumGunFlashDataSet illumGunFlashDataSet = new IllumGunFlashDataSet();
        illumGunFlashMetadata =  illumGunFlashDataSet.getMetadataElements();
        if (illumGunFlashMetadata != null) {

        }
        else {
            final GameModule mod = getGameModule();
            mod.warn("IlluminationCounterMetaData.xml information did not load properly. LOS results may be incorrect. If problem persits, likely a network error occurred. Please check your network connectivity and try again.");
        }

    }
    public void updatePieces() {

        // reset the counter lists
        illumRoundHexMap = new HashMap<Hex, IllumGunFlashMetadata>();
        illumRoundList = new ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>>();
        starshellHexList = new HashMap<Hex, IllumGunFlashMetadata>();
        starshellList = new ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>>();
        flameHexList = new HashMap<Hex, IllumGunFlashMetadata>();
        flameList = new ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>>();
        blazeHexList = new HashMap<Hex, IllumGunFlashMetadata>();
        blazeList = new ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>>();
        tripflareHexList = new HashMap<Hex, IllumGunFlashMetadata>();
        tripflareList = new ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>>();
        searchlightHexList = new HashMap<Hex, IllumGunFlashMetadata>();
        searchlightList = new ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>>();
        gunflashHexList = new HashMap<Hex, IllumGunFlashMetadata>();
        gunflashList = new ArrayList<java.util.Map<Hex, IllumGunFlashMetadata>>();

        // get all of the game pieces
        GamePiece[] p = gameMap.getPieces();

        // add each of the IllumGunFlash pieces
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
    private void updatePiece(GamePiece piece) {
        int newcounter = 0;
        // determine what hex and location the piece is in
        //if (piece.getName().contains("Control") || piece.getName().contains("Blank") || piece.getName().contains("Perimeter") ||
        //        piece.getName().contains("perimeter") || piece.getName().contains("Fortified") || piece.getName().contains("Hex Grid")){return;}
        Point p = piece.getPosition();
        p.translate(-gameMap.getEdgeBuffer().width, -gameMap.getEdgeBuffer().height);

        // ToDo revise to handle Starshell/IR that land offboard
        if (p == null || !LOSMap.onMap(p.x, p.y) || LOSMap.gridToHex(p.x, p.y) == null) {
            return;
        } // error handling - no point or point not on map or not in a hex

        Hex h = LOSMap.gridToHex(p.x, p.y);
        Location hexloc = h.getNearestLocation(p.x, p.y);
        int hexside = -1;
        hexside = h.getLocationHexside(hexloc);
        Hex sh = LOSMap.getAdjacentHex(h, hexside);
        String name = piece.getName().trim();
        // need to take out any label info for next test
        //name = (parsepiecename(name)).trim();
        // ignore any piece whose name is prefixed by an ignore-type counter
        //for (IllumGunFlashMetadata illumGunFlash : illumGunFlashMetadata.values()) {
        //    if (illumGunFlash.getType() == IllumGunFlashMetadata.CounterType.IGNORE && name.startsWith(illumGunFlash.getName())) {
        //        return;
        //    }
        //}

        // add the piece
        if (!Boolean.TRUE.equals(piece.getProperty(Properties.INVISIBLE_TO_ME))) {
            newcounter++;
            IllumGunFlashMetadata illumGunFlash = illumGunFlashMetadata.get(name);
            if (illumGunFlash == null) {
                if (name.contains("Hedge Overlay")) {
                    name = "Hedge Overlay";
                } else if (name.contains("Wall Overlay")) {
                    name = "Wall Overlay";
                } else if (name.contains("Bocage Overlay")) {
                    name = "Bocage Overlay";
                } else if (name.contains("Road")) {
                    name = "Road";    // Dirt/Paved has no los impact
                } else if (name.contains("Foot") || name.contains("Pontoon")) { // Foot/Pontoon have no los impact
                    name = "";
                } else if (name.contains("Bridge")) { // all Bridges are same for LOS
                    name = "Bridge";
                } else if (name.contains("Rowhouse Bar")) {
                    name = "Rowhouse Bar Overlay";
                } else if (name.contains("StoneBreach")) {
                    name = "StoneBreach Rowhouse Overlay";
                } else if (name.contains("Wood Breach")) {
                    name = "Wood Breach Rowhouse Overlay";
                }
                // for searchlights ????
                String sidenum = null;
                if (hexside != -1) {
                    sidenum = " " + String.valueOf(hexside);
                }
                if (sidenum != null) {
                    name += sidenum;
                }
                illumGunFlash = illumGunFlashMetadata.get(name);
            }
            if (illumGunFlash != null) {

                // add counter object to the appropriate list
                switch (illumGunFlash.getType()) {

                    case STARSHELL:
                        starshellHexList.put(h, illumGunFlash);
                        starshellList.add(starshellHexList);
                        break;
                    case ILLUMROUND:
                        illumRoundHexMap.put(h, illumGunFlash);
                        illumRoundList.add(illumRoundHexMap);
                        break;
                    case FLAME:
                        flameHexList.put(h, illumGunFlash);
                        flameList.add(flameHexList);
                        break;
                    case BLAZE:
                        blazeHexList.put(h, illumGunFlash);
                        blazeList.add(blazeHexList);
                        break;
                    case TRIPFLARES:
                        tripflareHexList.put(h, illumGunFlash);
                        tripflareList.add(tripflareHexList);
                        break;
                    case SEARCHLIGHTS:
                        searchlightHexList.put(h, illumGunFlash);
                        searchlightList.add(searchlightHexList);
                        break;


                    default:
                }
                /*case OBA:
                    // ToDo Refactor after beta test
                    OBA oba = new OBA(counter.getName(), h, counter.getRotation(), counter.getIsBarrage());
                    // need to handle Smoke and WP FFE/Barrage by creating smoke "counter" in each hex
                    Hex smokehex = null; int hindrance = 0; int height = 0;
                    if(counter.getName().contains("Har")){
                        break;
                    }
                    if(counter.getName().contains("Sm ") || counter.getName().contains("WP")) {
                        if (oba.getisBarrage()) { //Barrage
                            // get four hexes from center hex on each side depending on rotation
                            String[] barragehexes = LOSMap.getBarrageHexes(oba);
                            for (String shex : barragehexes) {
                                smokehex = LOSMap.getHex(shex);
                                if (smokehex != null) {
                                    if (counter.getName().contains("+3")) {
                                        hindrance = 3;
                                        height = 2;
                                    } else if (counter.getName().contains("+1")) {
                                        hindrance = 1;
                                        height = 4;
                                    } else {
                                        hindrance = 2;
                                        height = (counter.getName().contains("Sm") ? 2 : 4);
                                    }
                                    Smoke smoke = new Smoke(counter.getName(), smokehex.getCenterLocation(), height, hindrance);
                                    addCounter(smokeList, smoke, smokehex);
                                }
                            }
                        } else {  // Smoke
                            // get 7 hex blast area
                            String[] ffehexes = LOSMap.getAllAdjacentHexes(oba.getHex());
                            for (String shex : ffehexes) {
                                if (!shex.equalsIgnoreCase("offboard")) {
                                    smokehex = LOSMap.getHex(shex);
                                    if (smokehex != null) {
                                        if (counter.getName().contains("+3")) {
                                            hindrance = 3;
                                            height = 2;
                                        } else if (counter.getName().contains("+1")) {
                                            hindrance = 1;
                                            height = 4;
                                        } else {
                                            hindrance = 2;
                                            height = (counter.getName().contains("Sm") ? 2 : 4);
                                        }
                                        Smoke smoke = new Smoke(counter.getName(), smokehex.getCenterLocation(), height, hindrance);
                                        addCounter(smokeList, smoke, smokehex);
                                    }
                                }
                            }
                            if(counter.getName().contains("Naval")) {
                                // add two extra hexes if NOBA
                                Hex[] nobahexes = getExtraNobaHexes(counter.getRotation(), ffehexes);
                                for (int x = 0; x < 2; x++) {
                                    if (nobahexes[x] != null) {
                                        Smoke smoke = new Smoke(counter.getName(), nobahexes[x].getCenterLocation(), height, hindrance);
                                        addCounter(smokeList, smoke, nobahexes[x]);
                                    }
                                }
                            }
                        }
                    }
                    // Handle NOBA Bombardment and FFE
                    if (counter.getName().contains("Bombardment") && (counter.getName().contains("Smoke") || counter.getName().contains("Dispersed"))) {
                        height = 2;
                        int nobaradius = (counter.getName().contains("Small") ? 2 : 3);
                        String [] nobaradisuhexes = LOSMap.getAllHexesInRadiusOf(oba.getHex(), nobaradius);
                        for (String nobahex : nobaradisuhexes) {
                            smokehex = LOSMap.getHex(nobahex);
                            if (smokehex != null) {
                                if (counter.getName().contains("Dispersed")) {
                                    hindrance = 2;
                                }
                                else {
                                    hindrance = 3;
                                }
                                Smoke smoke = new Smoke(counter.getName(), smokehex.getCenterLocation(), height, hindrance);
                                addCounter(smokeList, smoke, smokehex);
                            }
                        }
                    }

                    else {

                        addCounter(OBAList, oba, h);
                    }
                    break;
                case TERRAIN:
                    // we assume there is only one terrain-type counter in a hex
                    terrainList.put(h, counter);
                    break;
                case HEXSIDE:
                    counter.setHexside(hexside);
                    hexsideList.put(h, counter);
                    break;
                case ENTRENCHMENT:
                    // we assume there is only one terrain-type counter in a hex
                    terrainList.put(h, counter);
                    // now create a "location" in the hex
                    createLocationinHexForEntrenchments(h);
                    break;
                case BRIDGE:
                    // we assume there is only one terrain-type counter in a hex
                    terrainList.put(h, counter);
                    // now create a "location" in the hex
                    createLocationinHexForBridges(h);
                    break;
                case CREST:
                    // we assume there is only one terrain-type counter in a hex
                    terrainList.put(h, counter);
                    // now create a "location" in the hex
                    createLocationinHexForCrest(h);
                    break;
                case SMOKE:
                    Smoke smoke = new Smoke(counter.getName(), h.getNearestLocation(p.x, p.y), counter.getHeight(), counter.getHindrance());
                    addCounter(smokeList, smoke, h);
                    break;

                case WRECK:
                    // treat wrecks as vehicles for now
                    Vehicle vehicle = new Vehicle(name, h.getCenterLocation());
                    addCounter(vehicleList, vehicle, h);
                    break;*/
                //}
            }

        }
    }
}
