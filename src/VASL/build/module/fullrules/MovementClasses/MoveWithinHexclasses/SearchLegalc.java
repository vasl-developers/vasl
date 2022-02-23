package VASL.build.module.fullrules.MovementClasses.MoveWithinHexclasses;

import VASL.LOS.Map.Hex;
import VASL.LOS.Map.Location;
import VASL.build.module.fullrules.Constantvalues;
import VASL.build.module.fullrules.MovementClasses.MoveNewHexclasses.LegalMovei;
import VASL.build.module.fullrules.ObjectClasses.PersUniti;
import VASL.build.module.fullrules.ObjectClasses.ScenarioCollectionsc;
import VASL.build.module.fullrules.ObjectFactoryClasses.PersCreation;
import VASL.build.module.fullrules.ObjectFactoryClasses.SWCreation;
import VASL.build.module.fullrules.UtilityClasses.DiceC;

public class SearchLegalc implements LegalMovei {
    private Hex hexclickedvalue;
    private Constantvalues.UMove MovementOptionvalue;
    private Location locationchangevalue;
    private String returnresultsvalue;
    private int hexescansearch;
    private ScenarioCollectionsc Scencolls = ScenarioCollectionsc.getInstance();

    // constructor
    public SearchLegalc(Hex passnewhexclicked, Constantvalues.UMove passmovementoption) {
        hexclickedvalue = passnewhexclicked;
        MovementOptionvalue = passmovementoption;
        returnresultsvalue = "";
        //locationchangevalue = passhexlocationclicked;
    }

    // properties
    public String getresultsstring() {
        return returnresultsvalue;
    }

    public int getHexescansearch() {
        return hexescansearch;
    }
    public Location getLocationchangevalue() {
        return null;
    }
    // methods
    public boolean IsMovementLegal() {
        //Determine number of hexes that cannot be searched
        int SearchSize = 0;  int FinalLdrDrm = 0; int FinalCantSearch =0;
        int StealthyDRM = 0; int CXDRM = 0; int JapDRM = 0; int LdrDRM =0;
        DiceC SearchRoll = new DiceC();
        int SearchDieroll = SearchRoll.Dieroll();
        for (PersUniti MovingUnit : Scencolls.SelMoveUnits) {
            Constantvalues.Utype SearchUnitsize = MovingUnit.getbaseunit().getUnittype();
            switch (SearchUnitsize) {
                case Squad:
                    SearchSize += 2;
                    if (MovingUnit.getbaseunit().getCharacterStatus() == Constantvalues.CharacterStatus.STEALTHY) {
                        StealthyDRM -= 1;
                    } else if (MovingUnit.getbaseunit().getCharacterStatus() == Constantvalues.CharacterStatus.LAX) {
                        StealthyDRM += 1;
                    }
                    break;
                case Crew: case HalfSquad:
                    SearchSize += 1;
                    if (MovingUnit.getbaseunit().getCharacterStatus() == Constantvalues.CharacterStatus.STEALTHY) {
                        StealthyDRM -= 1;
                    } else if (MovingUnit.getbaseunit().getCharacterStatus() == Constantvalues.CharacterStatus.LAX) {
                        StealthyDRM += 1;
                    }
                    break;
                case LdrHero: case Leader:
                    if (MovingUnit.getFiringunit() == null ){
                        PersCreation ObjCreate = new PersCreation();
                        MovingUnit = ObjCreate.CreatefiringUnitandProperty(MovingUnit);
                    }
                    LdrDRM += MovingUnit.getFiringunit().getLdrDRM();
                    if (FinalLdrDrm > LdrDRM) {FinalLdrDrm = LdrDRM;}
                    break;
                default:
                    SearchSize += 0;
                    break;
            }
            if (MovingUnit.getbaseunit().getCX()) {CXDRM = 1;}
        }
        if (SearchSize == 0) {return false;}
        if (VersusJapanNonBldgRub()) {JapDRM = 2;}
        FinalCantSearch = SearchDieroll + (SearchSize - 1) + FinalLdrDrm + StealthyDRM + CXDRM + JapDRM;
        if (FinalCantSearch >=6) {
            returnresultsvalue = "Final Search DR = " + Integer.toString(FinalCantSearch) + " therefore can't search beyond own hex";
            hexescansearch = 1;
        } else {
            hexescansearch = java.lang.Math.abs(FinalCantSearch - 6);
            returnresultsvalue = "Final Search DR = " + Integer.toString(FinalCantSearch) + " therefore can search " + Integer.toString(hexescansearch) + " hexes beyond own hex";
            hexescansearch += 1; // now add own hex to calculation
        }
        return true;
    }
    private boolean VersusJapanNonBldgRub() {
        // still to code
        return false;
    }
}
