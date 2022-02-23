package VASL.build.module.fullrules.MovementClasses.HexandLocation;

import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Terrain;
import VASL.LOS.VASLGameInterface;
import VASL.LOS.counters.CounterMetadata;
import VASL.LOS.counters.CounterMetadataFile;
import VASL.LOS.counters.Smoke;
import VASL.build.module.fullrules.Constantvalues;
import VASL.build.module.fullrules.DataClasses.ScenarioTerrain;
import VASL.build.module.fullrules.Game.ScenarioC;
import VASL.build.module.fullrules.UtilityClasses.ConversionC;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;

public class Locationc implements Locationi{
    // Concrete component class, holds information about the base hex, location and terrain
    // use it to hold ALL relevant info about a location, wrapping the vaslhex, vasllocation and vasl

    private Hex vaslhex;
    private Location vasllocation;
    private Terrain vaslterrain;
    private String hexname;
    private Terrain vaslotherterrain;
    private LinkedList<ScenarioTerrain> scenterraininhex;
    private Constantvalues.UMove MoveOption;
    private Constantvalues.Location locationvalue;
    // the LOS counter rules from the shared metadata file
    private LinkedHashMap<String, CounterMetadata> counterMetadata;
    // constructor
    public Locationc (Location vasllocclicked, Constantvalues.UMove MovementOptionClicked) {
        // set hex, location and terrain data
        vasllocation = vasllocclicked;
        vaslhex = vasllocclicked.getHex();
        vaslterrain = vasllocclicked.getTerrain();
        MoveOption = MovementOptionClicked;  // capture any context popup selections
        //add any scenario terrain in this hex
        scenterraininhex = getScenarioterrain();
        ConversionC confrom = new ConversionC();
        locationvalue = confrom.ConverttoLocationtypefromVASLLocation(vasllocclicked);
        // get the counter metadata
        CounterMetadataFile counterMetadataFile = new CounterMetadataFile();
        counterMetadata =  counterMetadataFile.getMetadataElements();
    }

    public Location getvasllocation() {return vasllocation;}
    public Hex getvaslhex() {return vaslhex;}
    public Terrain getvaslterrain() {return vaslterrain;}
    public Terrain getvaslotherterrain() {return vaslotherterrain;}
    public LinkedList<ScenarioTerrain> getScenTerraininhex() {return scenterraininhex;};
    public String gethexname() {return hexname;}
    public Constantvalues.Location getLocationtype() {return locationvalue;}

    public double gethexsideentrycost() {
        return 0.0; // not coded yet; need a routine to access vasl info and calculate
    }

    public double getlocationentrycost(Hex Currenthex) {
        // retrieve cost to enter location

        Double basemf = 0.0;  // Loctouse.MFCot
        // deal with exceptions not covered by decorators
        if (MoveOption == Constantvalues.UMove.Roadrate || MoveOption == Constantvalues.UMove.bypassrate
            || MoveOption == Constantvalues.UMove.extrabypassrate) {
            basemf = 0.0;
        }
        switch (MoveOption) {
            case EnterPillbox: case StairsDown: case StairsUp: case ExitCrestStatus: case FeatureExit: case EnterVehicle:
            case ExitVehicle: case RecoverSW: case RecoverSWBrk: case StairsDownWA: case StairsUpWA: case EnterTerrain:
            case ExitTerrain: case EnterConnectingTrench:
                basemf = 1.0;
                break;
            case EnterWire: case DropSW:
                basemf = 0.0;
                break;
            case ThrowSmokeSame:
                basemf = 1.0;
                break;
            case ThrowSmoke0: case ThrowSmoke1: case ThrowSmoke2: case ThrowSmoke3: case ThrowSmoke4: case ThrowSmoke5: case ThrowSmokeDown: case ThrowSmokeUp: case ThrowSmokeGround:
                basemf = 2.0;
                break;
            case EnterViaConnection:
                // need to code
                /*if (Loctouse.Entrenchment = Constantvalues.Feature.Trench) {
                    basemf = 1.0;
                } else {

                }
                break;*/
            default:
                 basemf = this.vaslterrain.getMF() ;
        }
        return basemf;
    }

    private LinkedList<ScenarioTerrain> getScenarioterrain() {

        // create master scenterrain list in ScenarioCollectionsc and use this routine to extract items in this hex
        // not using this option as VASLGameInterface filters by hex already

        ScenarioC scen = ScenarioC.getInstance();
        VASLGameInterface VASLGameInterface = new VASLGameInterface(scen.getASLMap(), scen.getGameMap());
        VASLGameInterface.updatePieces();
        LinkedList<ScenarioTerrain> scenterrinhex = new LinkedList<ScenarioTerrain>();
        Smoke nextSmoke = null;
        HashSet<Smoke> LookforSmoke= VASLGameInterface.getSmoke(vaslhex);
        // test for Smoke, if found then return
        Iterator<Smoke> itr=LookforSmoke.iterator();
        while(itr.hasNext()){
            ScenarioTerrain smokeinhex = new ScenarioTerrain();
            nextSmoke = itr.next();
            smokeinhex.setFeature(nextSmoke.getName());
            smokeinhex.setHexname(vaslhex.getName());
            smokeinhex.setFeaturetype(Constantvalues.Feature.Smoke);
            smokeinhex.setScenario(scen.getScenID());
            smokeinhex.setScenter_id(0); // do we even need this?
            smokeinhex.sethexlocation(vasllocation);
            smokeinhex.sethexposition(Constantvalues.AltPos.None);
            smokeinhex.setVisibilityStatus(Constantvalues.VisibilityStatus.Visible);
            scenterrinhex.add(smokeinhex);
        }
        return scenterrinhex;
    }
    public boolean HasWire(){
        return false;  // needs to be coded
    }
    public int getAPMines(){
        return 0; // needs to be coded
    }
    public void setAPMines (int value){int apmines = value;} // needs to be coded
    public boolean IsPillbox(){

        switch (locationvalue) {
            case Pill1571: case Pill1350: case Pill1351: case Pill1352: case Pill1353: case Pill1354: case Pill1355: case Pill1460: case Pill1461:case Pill1462: case Pill1463: case Pill1464:
            case Pill1465: case Pill1570: case Pill1572: case Pill1573: case Pill1574: case Pill1575: case Pill2350: case Pill2351: case Pill2352: case Pill2353: case Pill2354: case Pill2355:
            case Pill2460: case Pill2461: case Pill2462: case Pill2463: case Pill2464: case Pill2465: case Pill2570: case Pill2571: case Pill2572: case Pill2573: case Pill2574: case Pill2575:
            case Pill3350: case Pill3351: case Pill3352: case Pill3353: case Pill3354: case Pill3355: case Pill3570: case Pill3571: case Pill3572: case Pill3573: case Pill3574: case Pill3575:
            case Bombprf: case BunkUnder:
                return true;
            default:
                return false;
        }
    }


}
