package VASL.build.module.fullrules.IFTCombatClasses;

import VASL.LOS.Map.Hex;
import VASL.LOS.Map.LOSResult;
import VASL.LOS.Map.Location;
import VASL.LOS.VASLGameInterface;
import VASL.build.module.ASLMap;
import VASL.build.module.fullrules.Constantvalues;
import VASL.build.module.fullrules.DataClasses.IFTMods;
import VASL.build.module.fullrules.DataClasses.LineofBattle;
import VASL.build.module.fullrules.DataClasses.Scenario;
import VASL.build.module.fullrules.Game.ScenarioC;
import VASL.build.module.fullrules.LOSClasses.*;
import VASL.build.module.fullrules.ObjectClasses.*;
import VASL.build.module.fullrules.ObjectFactoryClasses.PersCreation;
import VASL.build.module.fullrules.ObjectFactoryClasses.SWCreation;
import VASL.build.module.fullrules.TerrainClasses.TerrainChecks;
import VASL.build.module.fullrules.UtilityClasses.CommonFunctionsC;
import VASL.build.module.fullrules.UtilityClasses.DiceC;
import VASSAL.build.GameModule;
import VASSAL.counters.GamePiece;

import java.util.LinkedList;
import java.util.List;

public class IFTC implements IIFTC {
    // Handles determination of combat parameters and combat results
    // Does NOT process combat result; that is done in CombatResolution.vb

    protected LinkedList<PersUniti> TempFireGroup = new LinkedList<PersUniti>(); // holds selected items before LOS confirmed
    protected LinkedList<PersUniti> FireGroup = new LinkedList<PersUniti>();     // holds selected items with LOS
    public LinkedList<PersUniti> TempTarget = new LinkedList<PersUniti>();   // holds selected items before LOS confirmed
    public   LinkedList<PersUniti> TargGroup = new LinkedList<PersUniti>();   // holds selected items with LOS
    protected LinkedList<TempSolution> TempSolutions = new LinkedList<TempSolution>();  // holds LOScheck before LOS confirmed
    protected LinkedList<LOSSolution> ValidSolutions = new LinkedList<LOSSolution>(); // holds LOSCheck with LOS
    private Constantvalues.Nationality pFirerSide;      // holds value of firing side (Enum Nationality)
    private Constantvalues.Nationality pTargetSide;     // holds value of target side (Enum Nationality)
    private int pFirerSan;  // holds value of firer san (Scenario.attsan or scenario.dfnsan)
    private int pTargetSan;  //holds value of target san
    // private CombatReport As New ReportingEvents
    protected LinkedList<IFTMods> FinalDRMLIst = new LinkedList<IFTMods>();  // holds info about drm for current combat
    private Hex pTargethex;  // first selected target hex - used to determine if other hexes eligible
    private Hex pFirerhex;   // first selected Firer hex - used to determine if other hexes eligible
    private Location pTargetloc;  // first selected target loc - used to determine if other hexes eligible
    private Location pFirerloc; // first selected Firer loc - used to determine if other hexes eligible
    private Constantvalues.AltPos pFirerpos; // first selected Firer pos - used to determine if other hexes eligible
    public LOSThreadManagerC ThreadManager = new LOSThreadManagerC();  // class with combat management methods
    public ThreadedLOSCheckC LOSTest = new ThreadedLOSCheckC();   // loscheckclass
    public DiceC DR = new DiceC();       // utility class that handles all variations of DR, dr - REPLACE with vasl classes
    public String DRstring;    // holds info on DR for reporting
    public String FirerSanString;
    public String TargSanString;  // holds info for reporting
    private CombatResi CombatRes;   // class that processes combat resolution
    private boolean myNeedToResumeResolution;  // toogle for interruptions due to user choice popups (sniper, surrender)
    private Hex FirerHexes[];       // holds list of firer hexes to be redrawn after combat
    private Hex TargetHexes[];      // holds list of target hexes to be redrawn after combat
    private IFTResulti IFTRes;   // class with methods for combat resolution
    private ScenarioCollectionsc Scencolls = ScenarioCollectionsc.getInstance();  // class that holds game object collections
    private int pScenID; // holds value of scenario index
    private Scenario scendet;  // Scenario object
    private ScenarioC scen = ScenarioC.getInstance();
    private VASL.LOS.Map.LOSResult losresult;

    // constructor
    public IFTC(int PassScenID) {
        // called by Scenario.Joinphase
        pFirerSide = Constantvalues.Nationality.None;
        pTargetSide = Constantvalues.Nationality.None;
        pFirerSan = 0;
        pTargetSan = 0;
        pFirerhex = null;
        pTargethex = null;
        pFirerloc = null;
        pTargetloc = null;
        pFirerpos = Constantvalues.AltPos.None;
        myNeedToResumeResolution = false;
        pScenID = PassScenID;
    }

    public VASL.LOS.Map.LOSResult getLOSResult() {return losresult;}

    public Constantvalues.Nationality getFirerSide() {
        return pFirerSide;
    }

    public Constantvalues.Nationality getTargetSide() {
        return pTargetSide;
    }

    public int getFirerSan() {
        return pFirerSan;
    }

    public int getTargetSan() {
        return pTargetSan;
    }

    public Hex getTargethex() {
        return pTargethex;
    }

    public void setTargethex(Hex value) {
        pTargethex = value;
    }

    public Hex getFirerhex() {
        return pFirerhex;
    }

    public void setFirerhex(Hex value) {
        pFirerhex = value;
    }

    public Location getTargetloc() {
        return pTargetloc;
    }

    public void setTargetloc(Location value) {
        pTargetloc = value;
    }

    public Location getFirerloc() {
        return pFirerloc;
    }

    public void setFirerloc(Location value) {
        pFirerloc = value;
    }

    public Constantvalues.AltPos getFirerpos() {
        return pFirerpos;
    }

    public void setFirerpos(Constantvalues.AltPos value) {
        pFirerpos = value;
    }

    public boolean getNeedtoResumeResolution() {
        return myNeedToResumeResolution;
    }



    // methods
    public boolean InitializeIFTCombat() {
        // called by ifT.FirephasePreparation
        // 'uses playerturn and phase parametets to identify which side is firing and which is target, sets san values

        // elsewhere in program need to handle reverse fire issues - AFV combat
        // INTEGRATE THIS INTO CLASS GETSIDEFORPHASE IN ASLGAME
        boolean test1;
        boolean test2;
        scen = ScenarioC.getInstance();
        scendet = scen.getScendet();
        Constantvalues.Phase test3 = scendet.getPhase();
        test1 = (test3 == Constantvalues.Phase.PrepFire);
        test2 = (test3 == Constantvalues.Phase.AdvancingFire);
        Constantvalues.WhoCanDo test4 = scendet.getPTURN();
        if ((test4 == Constantvalues.WhoCanDo.Attacker && (test1 || test2)) ||
                (test4 != Constantvalues.WhoCanDo.Attacker && (!test1 && !test2))) {
            // ATTACKER
            pFirerSide = scendet.getATT1();
            pTargetSide = scendet.getDFN1();
            pFirerSan = scendet.getATTSAN();
            pTargetSan = scendet.getDFNSAN();
        } else {
            // Defender
            pFirerSide = scendet.getDFN1();
            pTargetSide = scendet.getATT1();
            pFirerSan = scendet.getDFNSAN();
            pTargetSan = scendet.getATTSAN();
        }
        return true;

    }

    public void FirePhasePreparation() {
        // called by ASLGame.Scenario.CreatePhaseMVC
        // if new phase is a fire phase then moves game into ifT combat context if not already there

        // needs routine to check if already in combat mode and ask user if wishes to cancel

        //  set combat context: attacker, defender, SAN values
        InitializeIFTCombat();
    }

    public boolean ValidSolutionMatch(TempSolution Tempsolitem, int BringBackid) {
        // called by ifT.DetermineFireSolution
        // compares TempSol to all current Valid Solutions

        boolean ValidMatch = false;
        for (LOSSolution ValidSol: ValidSolutions){
            if (Tempsolitem.getSeeHex().getName() == ValidSol.getSeeHex().getName() && Tempsolitem.getSeenHex().getName() == ValidSol.getSeenHex().getName() &&
                    ValidSol.getSolworks() == true) {
                // if match the LOS is good; no further check needed
                BringBackid = ValidSol.getID();
                return true;
            }
        }
        return ValidMatch;
    }

    protected void DetermineFireSolution() {
        // called by ifT.ClickedOnNewParticipants and MGandInherentFPSelection.ProcessChoice
        // either add units to existing Fire Solution or create new solution if LOS exists
        // then return to ClickedOnNewParticipants and move to ManageCombatSolutionDetermination for Calc(FP And DRM)
        boolean CheckResult = false;
        VASL.LOS.Map.Map MapInUse = scen.getGameMap();
        ASLMap theMap = scen.getASLMap();
        losresult = new LOSResult();
        boolean SetCleartotrue = false;
        int BringBackID = 0;

        if (TempSolutions.size() > 0) {
            // new temp solutions exists to test
            // check for existing LOS determination
            for (TempSolution TempSolitem : TempSolutions) {
                if (ValidSolutions.size() > 0) {
                    // adding to existing solution
                    if (ValidSolutionMatch(TempSolitem, BringBackID)) {
                        // solution already exists
                        AddtoFireGroupandTargetGroup(BringBackID);
                        // clear TempSol and other Temps
                        TempSolitem.setID(BringBackID);
                        TempSolitem.setSolworks(true);
                        SetCleartotrue = true;
                        break;
                    } else {
                        if (TempSolitem.getID() == 0) {
                            TempSolitem.setID(ValidSolutions.size()); // should be size - 1 ???
                        }
                    }
                }
                // no match in ValidSolution
                // so check LOS database table

                // REPLACE VS CODE WITH CALL TO VASLTHREAD
                // 'need to clear TempCombatTerrcol at this point to ensure create valid list for each Fire solution (unique LOS)
                LOSTest.TempCombatTerrCol.clear();

                // call VASL LOS routine
                // do the LOS

//                ASLMap theMap = (ASLMap) VASLThread.getmap();   temporary while debugging
//                if(theMap == null || theMap.isLegacyMode()) {
//                    TempSolitem.setSolworks(false);
//                }
//                else {

                VASLGameInterface VASLGameInterface = new VASLGameInterface(theMap, MapInUse);
                VASLGameInterface.updatePieces();
                boolean useAuxSourceLOSPoint = false; // temporary while debugging, need to determine properly
                boolean useAuxTargetLOSPoint = false;
                Location source = TempSolitem.getSeeHex().getCenterLocation();
                Location target = TempSolitem.getSeenHex().getCenterLocation();
                MapInUse.LOS(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, losresult, VASLGameInterface);
                if (getLOSResult().isBlocked()) {
                    TempSolitem.setSolworks(false);
                    SetCleartotrue = true;
                } else {
                    TempSolitem.setSolworks(true);
                }
                AddtoTempTerrain(losresult, TempSolitem);

                //}
                /*
                Dim NewChecks = New List(Of MapDataClassLibrary.GameLO)
                Dim PassLOSStatus As Integer = LOSTest.LOSCheck(TempSolitem, NewChecks)
                If PassLOSStatus <> constantclasslibrary.aslxna.LosStatus.None Then
                TempSolitem.Solworks = True
                Else
                TempSolitem.Solworks = False
                SetCleartoTrue = True
                End If*/
                // NEED TO SET VALUE OF TempSolution.Solworks
                // test code assumes all los is valid
                TempSolitem.setSolworks(true);
                //SetCleartotrue = true;
            }
            for (TempSolution TempSolitem : TempSolutions) {
                if (TempSolitem.getSolworks() == false) {
                    // if one LOS is invalid, don' t add any Tempsolutions or units or terrain to valid solutions
                    // trigger reporting event
                    // CombatReport.ShowAddFailure(TempFireGroup, TempTarget);
                    // clear Temp variables
                    if (SetCleartotrue) {
                        ClearTempCombat();
                    }
                    // if no valid solutions clear combat
                    if (ValidSolutions.size() == 0) {
                        ClearCurrentIFT();
                    }
                    // end process
                    return;
                }
            }
            // at this point all Temp Solutions are valid so add TempSolutions, TempUnuits and TempCombatTerr to valid solutions
            for (TempSolution TempSolitem : TempSolutions) {
                // TempSol to ValidSol, including hexes in los
                AddtoValidSolutions(TempSolitem, LOSTest.TempCombatTerrCol);
                // add firer(s) to FireGroup, target(s) to Target Group
                AddtoFireGroupandTargetGroup(TempSolitem.getID());
                // clear Temps
                SetCleartotrue = true;
            }
            for (AltHexGTerrain Usealthex : LOSTest.TempAltHexLOSGroup) {
                ThreadManager.TempAlthexcol.add(Usealthex);
            }
            ThreadManager.AddtoAltHexLOSGroup();
        } else {
            // no new temp solution to test - here due to error in ifT.IsThereASolutionToTest
            // clear temps and preserve existing solutions
            SetCleartotrue = true;
        }
        if (SetCleartotrue) {ClearTempCombat();}

    }

    protected boolean ConfirmValidFG(List<PersUniti> FireGroup) {
        // called by ifT.ManageCombatSolutionDetermination
        // used check valid FG configuration
        boolean ldrpresent = false; boolean Unitpresent = false;
        // First test: no leaders with just leaders
        for (CombatTerrain TempCombathex:  LOSTest.TempCombatTerrCol) {
            ldrpresent = false;
            Unitpresent = false;
            if (TempCombathex.getHexrole() == Constantvalues.Hexrole.Firer ||
                    TempCombathex.getHexrole() == Constantvalues.Hexrole.FirerInt ||
                    TempCombathex.getHexrole() == Constantvalues.Hexrole.FirerTargetInt ||
                    TempCombathex.getHexrole() == Constantvalues.Hexrole.FirerTarget) {
                // check each hex in Combat terrain collection; if a firer
                // hex, need more than a ldr to be part of FG
                for (PersUniti FiringUnit : FireGroup) {
                    if ((FiringUnit.getbaseunit().getUnittype() == Constantvalues.Utype.Leader && TempCombathex.getHexName().equals(FiringUnit.getbaseunit().getHexName())) ||
                            (FiringUnit.getbaseunit().getUnittype() == Constantvalues.Utype.LdrHero &&
                            FiringUnit.getFiringunit().getUseHeroOrLeader() == Constantvalues.Utype.Leader &&
                            TempCombathex.getHexName() == FiringUnit.getbaseunit().getHexName())) {
                        ldrpresent = true;
                    } else {
                        if (TempCombathex.getHexName().equals(FiringUnit.getbaseunit().getHexName())) {
                            Unitpresent = true;
                        }
                    }
                }
                if (ldrpresent == true && Unitpresent == false) {
                    // only ldr present - can' t be part of FG
                    GameModule.getGameModule().getChatter().send("Only leader present in hex {0}. Can't be part of FG: " + TempCombathex.getHexName());
                    return false;
                } else if (ldrpresent == false && Unitpresent == false) {
                    // no units in hex!!
                    GameModule.getGameModule().getChatter().send("No units present in hex {0}. Can't be part of FG: " + TempCombathex.getHexName());
                    return false;
                }
            }
        }
        return true;
    }

    protected boolean AddTargetUnit(PersUniti Selunit) {
        // called by ifT.clickedonnewparticipants
        // confirms that unit is eligible to join target group and then adds to TempTarget

        boolean GroupAddOk = false;  // toggle to signal if unit can be part of Target group
        if (getTargethex() == null) { // first target in group, set target hex and add to TempTarget
            setTargethex(scen.getGameMap().getHex(Selunit.getbaseunit().getHexName()));
            setTargetloc(Selunit.getbaseunit().gethexlocation());
            if (AddtoTempTarget(Selunit)) { // 'true if add ok; false if add fails
                return true;
            } else {
                return false;
            }
        } else {
            // determine if eligible to join existing selection
            // not already added; to add additional units, must be adjacent so check range
            Hex NewTarget = scen.getGameMap().getHex(Selunit.getbaseunit().getHexName());
            LinkedList<PersUniti> TestList = new LinkedList<PersUniti>();
            if (TempTarget.size() > 0) {  // have not yet clicked on attacker or resolved first LOS
                TestList = TempTarget;
            } else if (TargGroup.size() > 0) {  // defender clicked and one LOS confirmed already
                TestList = TargGroup;
            } else {
                // error because not first Target but no other units in targetgroups
                GameModule.getGameModule().getChatter().send("Failure to Add Target Unit in AddTargetUnit");
                return false;
            }
            // check new unit against items already selected
            for (PersUniti ExistingUnit : TestList) {
                if (ExistingUnit.getbaseunit().getUnit_ID() == Selunit.getbaseunit().getUnit_ID() && ExistingUnit.getbaseunit().getTypeType_ID() == Selunit.getbaseunit().getTypeType_ID()) {   // already added
                    //GameModule.getGameModule().getChatter().send("Failure to Add Target Unit as already added in AddTargetUnit");
                    return false;
                } else {
                    int UnitDistance = scen.getGameMap().range(scen.getGameMap().getHex(ExistingUnit.getbaseunit().getHexName()), NewTarget, scen.getGameMap().getMapConfiguration());
                    if (UnitDistance == 1 && (ExistingUnit.getbaseunit().getLevelinHex() - Selunit.getbaseunit().getLevelinHex() == 0)) {
                        // is adjacent so ok
                        GroupAddOk = true;
                        break;
                    } else {
                        if (UnitDistance == 0) {
                            if (ExistingUnit.getbaseunit().getLevelinHex() - Selunit.getbaseunit().getLevelinHex() <= 1) {
                                // is adjacent so ok
                                GroupAddOk = true;
                                break;
                            }
                        }
                    }
                }
            }
            // if eligible then add
            if (GroupAddOk) {
                // add new firer to TempFireGroup array
                return AddtoTempTarget(Selunit) ? true : false;
            } else {
                GameModule.getGameModule().getChatter().send(Selunit.getbaseunit().getUnitName() + " cannot be part of multiple target locations. Not Adjacent (A7.5): Fire Routine");
                return false;
            }
        }
    }

    // overloaded
    protected boolean AddFirerUnit(PersUniti Unititem) {
        // called by ifT.Clickonnewparticipants
        // determines what unit and/or MG should be added and sends to ProcessAddFirer (some via context popup click)
        Constantvalues.CombatSel Selectionmade = Constantvalues.CombatSel.InhandFirstMG;
        boolean result;
        // do status checks - SHOULD THIS BE HERE OR ELSEWHERE?
        if (Unititem.getbaseunit().getOrderStatus() != Constantvalues.OrderStatus.Broken &&
                Unititem.getbaseunit().getOrderStatus() != Constantvalues.OrderStatus.Broken_DM &&
                Unititem.getbaseunit().getOrderStatus() != Constantvalues.OrderStatus.Disrupted &&
                Unititem.getbaseunit().getOrderStatus() != Constantvalues.OrderStatus.DisruptedDM) {
            if (Unititem.getbaseunit().getnumSW() != 0) {// need to add SW if part of fire and determine impact on inherent FP
                MGandInherentFPSelection MGifPSel = new MGandInherentFPSelection();
                // Selectionmade = MGifPSel.ShowChoices(Unititem); undo NEED TO COME BACK TO
                // determine which combo of Inherent and MG are being added - via popup and send result to ProcessAddFirer
                // remmed out while debugging - undo    NEED TO COME BACK TO
                /*if (Selectionmade == Constantvalues.CombatSel.None ||
                        Selectionmade == Constantvalues.CombatSel.ShowMenu) {
                    return false;
                }*/
            }
            String CheckFire = CheckFireEligibility(Unititem);
            if (CheckFire != null) {
                GameModule.getGameModule().getChatter().send("Failure to Add Firer Unit: " + Unititem.getbaseunit().getUnitName() + CheckFire);
                return false;
            }
            result = ProcessAddFirer(Unititem, Selectionmade);
        } else {
            result = false;
        }
        if (result ==  false) {
            GameModule.getGameModule().getChatter().send("Failure to Add Firer Unit: " + Unititem.getbaseunit().getUnitName() + ": ClickedOnNewParticipants: Add New Firer");
            return false;
        } else {
            return true;
        }
    }

    /*protected boolean AddFirerUnit(VisibleOccupiedhexes OH) {
        'called by ClickLeftAlt mouse events
        'determines what unit to include and sends to ProcessAddFirer
        Dim Selectionmade As Integer = Constantvalues.CombatSel.InhOnly :Dim Result As Boolean = false
        Dim TypeCheck = New Utilvalues.TypeUtil
        'do status checks - SHOULD THIS BE HERE OR ELSEWHERE?
        For Each displaysprite As Objectvalues.SpriteOrder In OH.VisibleCountersInHex
        if TypeCheck.IsThingATypeOf(Constantvalues.Typetype.Personnel, displaysprite.TypeID)
        AndAlso displaysprite.Selected Then
        Dim Unititem As Objectvalues.PersUniti = (From getunit As Objectvalues.PersUniti In
        Scencolls.Unitcol Where getunit.BasePersUnit.Unit_ID = displaysprite.ObjID Select getunit).First
        if Not Unititem.BasePersUnit.OrderStatus = Constantvalues.OrderStatus.Broken And Not
        Unititem.BasePersUnit.OrderStatus = Constantvalues.OrderStatus.Broken_DM And Not
        Unititem.BasePersUnit.OrderStatus = Constantvalues.OrderStatus.Disrupted And
        Not Unititem.BasePersUnit.OrderStatus = Constantvalues.OrderStatus.DisruptedDM Then
                Result = ProcessAddFirer(Unititem, Selectionmade)
        Else
        MessageBox.Show("Failure to Add Firer Unit: " & Trim(Unititem.BasePersUnit.UnitName), "ClickLeftAlt: Add New Firer")
        End if
        End if
        Next
        if Result = false Then
        return false
        Else
        return true
        End if
    }*/

    protected boolean ProcessAddFirer(PersUniti Unititem, Constantvalues.CombatSel Selectionmade) {   // Selectionmade shoudl be optional
        // called by AddFirerUnit or Contextpopup selection
        // confirms that unit is eligible to be part of fire group and then adds to TempFireGroup
        // Dim MapGeo as mapgeovalues.mapgeoc = MapGeovalues.MapGeoC.GetInstance(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        // can use null values if sure instance already exists
        boolean GroupAddOk = false; // toggle to signal if unit can be part of FG
        if (getFirerhex() == null) {
            // first firer in group, set fire hex and add to TempFG
            setFirerhex(scen.getGameMap().getHex(Unititem.getbaseunit().getHexName()));
            setFirerloc(Unititem.getbaseunit().gethexlocation());
            return AddtoTempFG(Unititem, Selectionmade) ? true : false;
        } else {
            // determine if eligible to join existing selection
            // to add additional units, must be adjacent
            // check range
            Hex NewTarget = scen.getGameMap().getHex(Unititem.getbaseunit().getHexName());
            LinkedList<PersUniti> TestList;
            // start with TempFG
            if (TempFireGroup.size() > 0) { // have not yet clicked on defender or resolved first LOS
                TestList = TempFireGroup;
            } else if (FireGroup.size() > 0) { // defender clicked and one LOS confirmed already
                TestList = FireGroup;
            } else {
                // error because not first firer but no other units in firegroup
                GameModule.getGameModule().getChatter().send(Unititem.getbaseunit().getUnitName() + " cannot be part of Fire Group. Unknown error: ProcessAddFirer");
                return false;
            }
            for (PersUniti TestFiringUnit : TestList) {
                if (TestFiringUnit.getbaseunit().getUnit_ID() == Unititem.getbaseunit().getUnit_ID() &&
                        Constantvalues.Typetype.Personnel == TestFiringUnit.getbaseunit().getTypeType_ID()) {
                    return false;
                } // already added
                int UnitDistance = scen.getGameMap().range(scen.getGameMap().getHex(TestFiringUnit.getbaseunit().getHexName()), NewTarget, scen.getGameMap().getMapConfiguration());
                if (UnitDistance == 1 && TestFiringUnit.getbaseunit().getLevelinHex() - Unititem.getbaseunit().getLevelinHex() == 0) {
                    GroupAddOk = true;
                    break;
                } else if (UnitDistance == 0) {
                    if (TestFiringUnit.getbaseunit().getLevelinHex() - Unititem.getbaseunit().getLevelinHex() <= 1) {
                        GroupAddOk = true;
                        break;
                    }
                }
            }
            // if eligible then add
            if (GroupAddOk) {
                // add new firer to TempFireGroup array
                return AddtoTempFG(Unititem, Selectionmade) ? true : false;
            } else {
                GameModule.getGameModule().getChatter().send(Unititem.getbaseunit().getUnitName() + " cannot be part of Fire Group. Not Adjacent (A7.5): Fire Routine");
                return false;
            }
        }

    }

    private String CheckFireEligibility(PersUniti CheckFireUnit){

        switch (CheckFireUnit.getbaseunit().getCombatStatus()) {
            case PrepFirer:
                return " has Prep Fired. Can't fire again.";
            case AdvFirer:
                return " has Advance Fired. Can't fire again.";
            case FinalFirer:
                return " has Final Fired. Can't fire again.";
            default:
                return null;
        }
    }
    protected boolean AddtoTempTerrain(LOSResult result, TempSolution Tempsolitem) {
        //MapDataC MapData = scen.Maptables;
        //LinkedList<GameLocation> LocationCol = null;  //MapData.getLocationCol();
        TerrainChecks PassTerrChk = new TerrainChecks();
        ThreadedLOSCheckCommonc LOSCheckCommon = new ThreadedLOSCheckCommonc(PassTerrChk);

        for (Hex HexInLOS: result.gethexesInLOS()){
            // create CombatTerrain object
            LOSCheckCommon.AddCombatTerrainHex(HexInLOS.getCenterLocation(), Tempsolitem.getID(), HexInLOS.getCombatHexrole(), result);  // getCenterLocation is not right
        }
        for (CombatTerrain addCombatTerr: LOSCheckCommon.TempCombatTerrColCommon){
            LOSTest.TempCombatTerrCol.add(addCombatTerr);
        }
        return true;
    }


    protected boolean IsThereASolutiontoTest(Constantvalues.CombatStatus WhichOne) {
        // called by ifT.ClickedOnNewParticipants and MGandInherentFPSelection.ProcessChoice
        // after code returns from ifT.AddFirerUnit or ifT.AddTargetUnit
        // creates or gets Tempsolution or Solution as required and adds new selected units to it

        // NEED TO BREAK THIS UP INTO SEPARATE ROUTINES
        boolean SolToTest = false;
        boolean AddYes;
        Hex PassFirerhex = null;
        double PassFirerlevelinhex = 0;
        Constantvalues.AltPos PassFirerPositionInHex = Constantvalues.AltPos.None;
        int PassFirerLosIndex = 0;
        boolean PassSolworks = false;
        int SolIDforFirer = 0;
        // retrieves scenario data
        //int scenmap = scendet.getMap();
        // select if Firer or Target being added
        if (WhichOne == Constantvalues.CombatStatus.Firing) {
            // retrieve most recently created TempFiringUnit to get data for solution
            PersUniti TempFiringUnit = TempFireGroup.getLast();
            AddYes = false;
            // put LOS-related info into variables to use in creating TempSolution
            PassFirerhex = scen.getGameMap().getHex(TempFiringUnit.getbaseunit().getHexName());
            PassFirerlevelinhex = TempFiringUnit.getbaseunit().getLevelinHex();
            PassFirerPositionInHex = TempFiringUnit.getbaseunit().gethexPosition();

            // check if a target has been selected, if only firers selected, do nothing, already added to Group
            if (TempTarget.size() > 0) {
                // if TempTarget exists then working on TempSolution
                // need to make sure using current values if Target Moving
                if (scen.DoMove != null && Scencolls.SelMoveUnits != null && Scencolls.SelMoveUnits.size() > 0) {
                    for (PersUniti MovingUnit : Scencolls.SelMoveUnits) {
                        for (PersUniti TempTarg : TempTarget) {
                            if (TempTarg.getbaseunit().getUnit_ID() == MovingUnit.getbaseunit().getUnit_ID()) {
                                TempTarg.getbaseunit().setHex(MovingUnit.getbaseunit().getHex());
                                TempTarg.getbaseunit().sethexlocation(MovingUnit.getbaseunit().gethexlocation());
                                TempTarg.getbaseunit().sethexPosition(MovingUnit.getbaseunit().gethexPosition());
                                TempTarg.getbaseunit().setLevelinHex(MovingUnit.getbaseunit().getLevelinHex());
                            }
                        }
                    }
                }
                for (PersUniti TempTargU : TempTarget) {
                    // put LOS-related info into variables for use in creating TempSolution
                    Hex PassTargethex = scen.getGameMap().getHex(TempTargU.getbaseunit().getHexName());
                    double PassTargetlevelinhex = TempTargU.getbaseunit().getLevelinHex();
                    Constantvalues.AltPos PassTargetPositionInHex = TempTargU.getbaseunit().gethexPosition();
                    // check and see if same TEmpSol already exists
                    AddYes = true;
                    if (TempSolutions.size() > 0) {
                        for (TempSolution TempSolitem : TempSolutions) {
                            if (TempTargU.getbaseunit().getHexName().equals(TempSolitem.getSeenHex().getName())) {
                                // same TempSolution already exists no need to create new one
                                for (PersUniti TempFirUnit : TempFireGroup) {
                                    TempFirUnit.getbaseunit().setSolID(TempSolitem.getID());
                                }
                                SolToTest = true;
                                TempTargU.getbaseunit().setSolID(TempSolitem.getID());
                                AddYes = false;
                                break;
                            }
                        }
                        if (AddYes) {
                            // add new TempSol to the TempSolutions collection
                            if (AddtoTempSol(PassFirerhex, PassFirerlevelinhex,
                                    PassFirerPositionInHex, PassTargethex, PassTargetlevelinhex,
                                    PassTargetPositionInHex, PassSolworks, TempSolutions.size(), scen.getGameMap())) {

                                TempSolution TempSol = TempSolutions.get(TempSolutions.size() - 1);
                                // update TempFirer and TempTarget to include new TempSolution id
                                for (PersUniti TempFirUnit : TempFireGroup) {
                                    TempFirUnit.getbaseunit().setSolID(TempSol.getID());
                                }
                                TempTargU.getbaseunit().setSolID(TempSol.getID());
                                SolToTest = true;
                                AddYes = false;
                            } else {
                                // error handling - will return to calling routine without adding to TempSol
                                // should not cause crash; report failure in AddtoTempSol
                            }
                        }
                    }
                    if (AddYes) {
                        // add new TempSol to the TempSolutions collection
                        if (AddtoTempSol(PassFirerhex, PassFirerlevelinhex,
                                PassFirerPositionInHex, PassTargethex, PassTargetlevelinhex,
                                PassTargetPositionInHex, PassSolworks, ValidSolutions.size(), scen.getGameMap())) {
                            TempSolution TempSol = TempSolutions.get(TempSolutions.size() - 1);
                            // update TempFirer and TempTarget to include new TempSolution id
                            for (PersUniti TempFirUnit : TempFireGroup) {
                                TempFirUnit.getbaseunit().setSolID(TempSol.getID());
                            }
                            TempTargU.getbaseunit().setSolID(TempSol.getID());
                            SolToTest = true;
                        } else {
                            // error handling - will return to calling routine without adding to TempSol
                            // should not cause crash; report failure in AddtoTempSol
                        }

                    }
                }
            } else if (ValidSolutions.size() > 0) {
                // if no target selected but Solution exists then add to FireGroup; if no solution exists then create temp solution
                AddYes = true;
                for (LOSSolution ValidSol : ValidSolutions) {
                    if (TempFiringUnit.getbaseunit().getHexName() == ValidSol.getSeeHex().getName()) {
                        // if Firer location already part of valid solution, no need to check LOS again
                        AddYes = false;
                        GameModule.getGameModule().getChatter().send("No need to add this to temp sol since already validated BUT must add units");
                        // will return to calling routine, having added to FireGroup but not new Sol
                        // should not cause crash
                        for (PersUniti TempFirUnit : TempFireGroup) {
                            if (TempFirUnit.getbaseunit().getHexName() == ValidSol.getSeeHex().getName()) {
                                TempFirUnit.getbaseunit().setSolID(ValidSol.getID());
                            }
                        }
                        AddtoFireGroupandTargetGroup(ValidSol.getID());
                        SolToTest = true;
                    }
                }
                // if new Firer location then need to create temp solution
                if (AddYes) {
                    LOSSolution ValidSol = ValidSolutions.getFirst();
                    Hex PassTargethex = ValidSol.getSeenHex();
                    double PassTargetlevelinhex = ValidSol.getSeenLevelInHex();
                    Constantvalues.AltPos PassTargetPositionInHex = ValidSol.getSeenPositionInHex();
                    if (AddtoTempSol(PassFirerhex, PassFirerlevelinhex,
                            PassFirerPositionInHex, PassTargethex, PassTargetlevelinhex,
                            PassTargetPositionInHex, PassSolworks, ValidSolutions.size(), scen.getGameMap())) {
                        TempSolution TempSol = TempSolutions.get(TempSolutions.size() - 1);
                        for (PersUniti TempFirUnit : TempFireGroup) {
                            TempFirUnit.getbaseunit().setSolID(TempSol.getID());
                        }
                        SolToTest = true;
                    } else {
                        // error handling - will return to calling routine, having added nothing
                        // should not cause crash; report failure in AddtoTempSol
                    }
                }
            } else {
                // just adding new firers; nothing else to do
            }
        } else {
            // if only targets selected, do nothing, else create Temp solution
            try {
                PersUniti TempTargUnit = TempTarget.getLast();
                // deals with one Target Unit at a time; get data
                AddYes = false;
                // put data in variabales to pass to other methods
                Hex PassTargethex = scen.getGameMap().getHex(TempTargUnit.getbaseunit().getHexName());
                double PassTargetlevelinhex = TempTargUnit.getbaseunit().getLevelinHex();
                Constantvalues.AltPos PassTargetPositioninhex = TempTargUnit.getbaseunit().gethexPosition();
                PassSolworks = false;
                // if TempFirers then create a TempSol
                if (TempFireGroup.size() > 0) {
                    for (PersUniti TempFiringUnit : TempFireGroup) {
                        // put firer data into variables for passing
                        PassFirerhex = scen.getGameMap().getHex(TempFiringUnit.getbaseunit().getHexName());
                        PassFirerlevelinhex = TempFiringUnit.getbaseunit().getLevelinHex();
                        PassFirerPositionInHex = TempFiringUnit.getbaseunit().gethexPosition();
                        if (TempSolutions.size() == 0) {
                            // if no TempSol then create a new one
                            AddYes = true;
                        } else {
                            for (TempSolution TempSolitem : TempSolutions) {
                                // check against existing tempsol; if match then don' t create new one
                                if (TempFiringUnit.getbaseunit().getHexName() == TempSolitem.getSeeHex().getName() && TempTargUnit.getbaseunit().getHexName() == TempSolitem.getSeenHex().getName()) {
                                    SolToTest = true;
                                    AddYes = false;
                                    break;
                                } else {
                                    AddYes = true;
                                }
                            }
                        }
                        if (AddYes) {
                            // if no match then create new TempSol
                            if (AddtoTempSol(PassFirerhex, PassFirerlevelinhex,
                                    PassFirerPositionInHex, PassTargethex, PassTargetlevelinhex,
                                    PassTargetPositioninhex, PassSolworks, TempSolutions.size() - 1, scen.getGameMap())) {
                                TempSolution TempSol = TempSolutions.getLast();
                                TempFiringUnit.getbaseunit().setSolID(TempSol.getID());
                                TempTargUnit.getbaseunit().setSolID(TempSol.getID());
                                SolToTest = true;
                            } else {
                                // error handling will return to calling routine, having added nothing
                                // should not cause crash; report failure in AddtoTempSol
                            }
                        }
                    }
                } else if (ValidSolutions.size() > 0) {
                        // check if LOS already validated
                        AddYes = true;
                        for (LOSSolution ValidSol : ValidSolutions) {
                            if (TempTargUnit.getbaseunit().getHexName() == ValidSol.getSeenHex().getName()) {
                                // if Target location already part of valid solution, no need to check LOS again
                                AddYes = false;
                                GameModule.getGameModule().getChatter().send("No need to add this to temp sol since already validated BUT must add units");
                                // will return to calling routine, having added to TargetGroup but not new Sol
                                // should not cause crash
                                TempTargUnit.getbaseunit().setSolID(ValidSol.getID());
                                AddtoFireGroupandTargetGroup(ValidSol.getID());
                                SolToTest = true;
                            }
                        }
                        if (AddYes) {
                            // if not match with existing sol then create TempSol
                            for (LOSSolution ValidSol : ValidSolutions) {
                                PassFirerhex = ValidSol.getSeeHex();
                                PassFirerlevelinhex = ValidSol.getSeeLevelInHex();
                                PassFirerPositionInHex = ValidSol.getSeePositionInHex();
                                if (AddtoTempSol(PassFirerhex, PassFirerlevelinhex,
                                        PassFirerPositionInHex, PassTargethex, PassTargetlevelinhex,
                                        PassTargetPositioninhex, PassSolworks, TempSolutions.size() - 1, scen.getGameMap())) {
                                    TempSolution TempSol = TempSolutions.getLast();
                                    TempTargUnit.getbaseunit().setSolID(TempSol.getID());
                                    SolToTest = true;
                                } else {
                                    // error handling - will return to calling routine, having added nothing
                                    // should not cause crash; report failure in AddtoTempSol
                                }
                            }
                        }
                } else {
                        // just adding new targets; nothing else to do
                }
            }catch (Exception e) {
                    // Needed for same hex combat when OH.VisibleUnitsinHex contains both firer and target with selected=true
                if (TargGroup.size() > 0) {
                    SolToTest = true;
                } else {
                    SolToTest = false;
                }
            }
        }
        return SolToTest;
        // now that all TempSolutions/Solutions are set, return to ClickOnNewParticipants and call DetermineFireSolution
    }

    protected boolean AddtoTempFG(PersUniti Unititem, Constantvalues.CombatSel selectionmade) {
        // called by ifT.AddFirerUnit
        // creates a new instance of firepersUnit property in the persuniti instance and then adds that to TempFireGroup
        // if LOS exists, persuniti instances are later added to FireGroup
        SuppWeapi UsingMG;
        // create Firingpersunitproperty
        PersCreation ObjCreate = new PersCreation();
        SWCreation SWCreate = new SWCreation();
        Unititem = ObjCreate.CreatefiringUnitandProperty(Unititem);
        // UsingMG = SWCreate.CreatefiringSwandProperty(UsingMG);
        switch (selectionmade) {
            case None:
                break;
            case InhandFirstMG:
                Unititem.getFiringunit().setUsingInherentFP(true);
                UsingMG = getFiringSW(Unititem.getbaseunit().getFirstSWLink());
                if (UsingMG !=null) {
                UsingMG = SWCreate.CreatefiringSwandProperty(UsingMG);
                Unititem.getFiringunit().FiringMGs.add(UsingMG);
                Unititem.getFiringunit().setUsingfirstMG(true);
                }
                break;
            case InhandSecondMG:
                Unititem.getFiringunit().setUsingInherentFP(true);
                UsingMG = getFiringSW(Unititem.getbaseunit().getSecondSWLink());
                if (UsingMG !=null){
                UsingMG = SWCreate.CreatefiringSwandProperty(UsingMG);
                Unititem.getFiringunit().FiringMGs.add(UsingMG);
                Unititem.getFiringunit().setUsingsecondMG(true);
                }
                break;
            case BothMG:
                Unititem.getFiringunit().setUsingInherentFP(false);
                UsingMG = getFiringSW(Unititem.getbaseunit().getFirstSWLink());
                if (UsingMG !=null) {
                UsingMG = SWCreate.CreatefiringSwandProperty(UsingMG);
                Unititem.getFiringunit().FiringMGs.add(UsingMG);
                Unititem.getFiringunit().setUsingfirstMG(true);
                }
                UsingMG = getFiringSW(Unititem.getbaseunit().getSecondSWLink());
                if (UsingMG !=null) {
                    UsingMG = SWCreate.CreatefiringSwandProperty(UsingMG);
                Unititem.getFiringunit().FiringMGs.add(UsingMG);
                Unititem.getFiringunit().setUsingsecondMG(true);
                }
                break;
            case FirstMG:
                Unititem.getFiringunit().setUsingInherentFP(false);
                UsingMG = getFiringSW(Unititem.getbaseunit().getFirstSWLink());
                if (UsingMG !=null) {
                UsingMG = SWCreate.CreatefiringSwandProperty(UsingMG);
                Unititem.getFiringunit().FiringMGs.add(UsingMG);
                Unititem.getFiringunit().setUsingfirstMG(true);
                }
                break;
            case SecondMG:
                Unititem.getFiringunit().setUsingInherentFP(false);
                UsingMG = getFiringSW(Unititem.getbaseunit().getSecondSWLink());
                if (UsingMG !=null) {
                UsingMG = SWCreate.CreatefiringSwandProperty(UsingMG);
                Unititem.getFiringunit().FiringMGs.add(UsingMG);
                Unititem.getFiringunit().setUsingsecondMG(true);
                }
                break;
            case InhOnly:
                Unititem.getFiringunit().setUsingInherentFP(true);
                Unititem.getFiringunit().setUsingfirstMG(false);
                Unititem.getFiringunit().setUsingsecondMG(false);
                break;
            default:
        }
        // add to TempFireGroup
        try {
            TempFireGroup.add(Unititem);
            return true;
        } catch (Exception e) {
            GameModule.getGameModule().getChatter().send("Error adding unit to TempFireGroup: IFT.AddtoTempFG");
            return false;
        }
    }
    private SuppWeapi getFiringSW(int swtofind){
        SuppWeapi SWtoCheck= null;
        if (swtofind == 0) {return null;}  // no SW found
        for (SuppWeapi findSW: Scencolls.SWCol) {
            if (findSW.getbaseSW().getSW_ID() == swtofind) {
                SWtoCheck = findSW;
                break;
            }
        }
        return SWtoCheck;
    }

    protected boolean AddtoFireGroupandTargetGroup(int SolUsedID) {
        // called by ifT.DetermineFireSolution
        // adds units from valid Temp Solution to FireGroup and TargetGroup
        ScenarioC scen = ScenarioC.getInstance();
        CommonFunctionsC comfun = new CommonFunctionsC(pScenID);

        boolean AddtoFGTG = false;
        // add firer(s) to FireGroup
        int Firerlink = 0;
        boolean FirerAdded = false;
        if (TempFireGroup.size() > 0) {
            for (PersUniti TempFiringUnit : TempFireGroup) {
                if (TempFiringUnit.getbaseunit().getSolID() == SolUsedID) {
                    Firerlink = TempFiringUnit.getbaseunit().getUnit_ID();
                    if (Constantvalues.Typetype.Personnel == TempFiringUnit.getbaseunit().getTypeType_ID()) {
                        // unit
                        // check if already added to Fire Group
                        for (PersUniti AlreadyAdded : FireGroup) {
                            if (AlreadyAdded.getbaseunit().getUnit_ID() == Firerlink && Constantvalues.Typetype.Personnel == AlreadyAdded.getbaseunit().getTypeType_ID()) {
                                // alreadyadded so do nothing
                                FirerAdded = true;
                                break;
                            }
                        }
                        if (!FirerAdded) {
                            if (TempFiringUnit.getFiringunit().getCombatStatus() == Constantvalues.CombatStatus.None ||
                                    TempFiringUnit.getbaseunit().getCombatStatus() == Constantvalues.CombatStatus.None) {
                                TempFiringUnit.getFiringunit().setCombatStatus(Constantvalues.CombatStatus.Firing);
                            }
                            int myLdrdrm = 0;
                            int myHeroDRM = 0;
                            int WhichtoUse = 0;

                            //LineofBattle TempFiringLOBRecord = comfun.Getlob(Integer.toString(TempFiringUnit.getbaseunit().getLOBLink()));
                            Constantvalues.Utype myType = TempFiringUnit.getbaseunit().getUnittype();
                            if (myType == Constantvalues.Utype.Leader || myType == Constantvalues.Utype.LdrHero) {
                                SMC tempSMC = comfun.GetSMC(Integer.toString(TempFiringUnit.getbaseunit().getLOBLink()));
                                myLdrdrm = tempSMC.getLDRM();
                            } else {
                                myLdrdrm = 0;
                            }
                            if (myType == Constantvalues.Utype.Hero) {
                                myHeroDRM = -1;
                            } else {
                                myHeroDRM = 0;
                            }
                            if (myLdrdrm == -1 && myType == Constantvalues.Utype.LdrHero) {
                                // ldrhero
                                // need user to choose which drm to use - DONT TO THIS HERE; DO IT IN CALCldrDRM
                            }
                            FireGroup.add(TempFiringUnit);
                        }
                    }
                    AddtoFGTG = true;
                }
            }
        }
        // add target(s)to Target Group
        int Targetlink = 0;
        boolean TargetAdded = false;
        if (TempTarget.size() > 0) {
            for (PersUniti TempTargetUnit: TempTarget){
                if (TempTargetUnit.getbaseunit().getSolID() == SolUsedID){ // part of this fire solution
                    Targetlink = TempTargetUnit.getbaseunit().getUnit_ID();
                    // check if already added to Target group
                    TargetAdded = false;
                    for(PersUniti Alreadyadded: TargGroup) {
                        if (Alreadyadded.getbaseunit().getUnit_ID() == Targetlink && TempTargetUnit.getbaseunit().getTypeType_ID() == Alreadyadded.getbaseunit().getTypeType_ID()) {
                            // already added so do nothing
                            TargetAdded = true;
                            break;
                        }
                    }
                    if (!TargetAdded) {
                        TargGroup.add(TempTargetUnit);
                    }
                    AddtoFGTG = true;
                }
            }
        }
        return AddtoFGTG;
    }

    protected boolean AddtoTempTarget(PersUniti Unititem) {
        // called by ifT.AddTargetUnit
        // eligible to be added as target to create Targetpersunit property and add to TempTarget
        // if LOS exists, added to TargetGroup later

        // create Targetpersunitproperty
        PersCreation ObjCreate = new PersCreation();
        Unititem = ObjCreate.CreateTargetUnitandProperty(Unititem, getFirerSan());

        // 'add to TempTarget
        try {
            TempTarget.add(Unititem);
            return true;
        } catch (Exception e) {
            GameModule.getGameModule().getChatter().send("Error adding unit to TempTarget in ifT.AddtoTempTarget");
            return false;
        }
    }

    protected boolean AddtoTempSol(Hex PassFirerhex, double PassFirerlevelinhex, Constantvalues.AltPos PassFirerPositioninHex,
        Hex PassTargethex, double PassTargetlevelinhex, Constantvalues.AltPos PassTargetPositionInHex,
        boolean PassSolWorks, int PassTempSolID, VASL.LOS.Map.Map PassScenMap){
        // called by ifT.IsThereASolutionToTest
        // adds a new temporary LOS to be validated
        try {
            TempSolutions.add(new TempSolution(PassFirerhex, PassFirerlevelinhex,
                    PassFirerPositioninHex, PassTargethex, PassTargetlevelinhex,
                    PassTargetPositionInHex, PassSolWorks, PassTempSolID, PassScenMap));
            return true;
        }catch(Exception ex){
            GameModule.getGameModule().getChatter().send("Error adding Fire Solution to TempSolutions: ifT.AddtoTempSol");
            return false;
        }
    }

        protected boolean AddtoValidSolutions (TempSolution TempSolitem, LinkedList<CombatTerrain> PassHexesInLOS) {
            // called by ifT.DetermineFireSolution
            // adds a validated fire solution to the ValidSolutions group

            int PassID  = 0;
            if (TempSolitem.getID() == 0 && ValidSolutions.size() > 0) {
                PassID = ValidSolutions.size();
            } else {
                PassID = TempSolitem.getID();
            }
            try {
                ValidSolutions.add(new LOSSolution(TempSolitem.getSeeHex(), TempSolitem.getSeeLevelInHex(), TempSolitem.getTotalSeeLevel(),
                        TempSolitem.getSeePositionInHex(), TempSolitem.getSeenHex(), TempSolitem.getSeenLevelInHex(), TempSolitem.getTotalSeenLevel(),
                        TempSolitem.getSeenPositionInHex(), TempSolitem.getSolworks(), TempSolitem.getLOSFollows(), PassID, TempSolitem.getScenMap(), PassHexesInLOS));
                return true;
            } catch (Exception e) {
                GameModule.getGameModule().getChatter().send("Error adding Fire Solution to ValidSolutions: IFT.AddtoValidSolutions");
                return false;
            }

        }

    public void ClickedOnNewParticipants(Hex ClickedHex, LinkedList<GamePiece> SelectedUnits) {
        // called by Game.DetermineClickPossibilities  in lots of classes
        // this routine manages Firer/Target selections and when valid triggers the combat process up to clicking on Fire or cancel
        // which checks LOS, determines FP and drm
        // once completed returns to Game.DetermineClickPossibilities with no further action there
        // it can be called multiple times to add additional target/firer units before resolving combat

        PersUniti Addunit = null;
        boolean GoCombatSolutionTest = false;
        boolean GoCombatSolution = false;
        Constantvalues.CombatStatus WhichOne = Constantvalues.CombatStatus.None;
        int ObjIDlink = 0;
        // Get list of PersUniti objects for the selected units
        ScenarioCollectionsc Scencolls = ScenarioCollectionsc.getInstance();

        // temporary while debugging
        if (ValidSolutions.size() > 0) { // selecting one of possible solutions already checked
            for (GamePiece SelUnit : SelectedUnits) {
                ObjIDlink = Integer.parseInt(SelUnit.getProperty("TextLabel").toString());
                for(PersUniti findunit: Scencolls.Unitcol){
                    if(findunit.getbaseunit().getUnit_ID() == ObjIDlink) {
                        if (findunit.getbaseunit().getNationality() == getTargetSide()) {
                            WhichOne = Constantvalues.CombatStatus.None;
                        } else {
                            WhichOne = Constantvalues.CombatStatus.Firing;
                        }
                    }
                }
            }
        }

        // no solution yet in place, so add units and test for solution
        // if selected determine if unit or ? and use nationality to determine if Target or Firer
        for (GamePiece SelUnit : SelectedUnits) {
            ObjIDlink = Integer.parseInt(SelUnit.getProperty("TextLabel").toString());
            for (PersUniti findunit : Scencolls.Unitcol) {
                if (findunit.getbaseunit().getUnit_ID() == ObjIDlink) {
                    if (findunit.getbaseunit().getNationality() == getTargetSide()) {
                        WhichOne = Constantvalues.CombatStatus.None;
                    } else {
                        WhichOne = Constantvalues.CombatStatus.Firing;
                    }
                    Addunit = findunit;
                    break;
                }
            }
            // add unit or ? to Target or Firer (? not added to firer)
            if (WhichOne == Constantvalues.CombatStatus.None && Addunit != null) {  // TargetUniut
                if (AddTargetUnit(Addunit)) {
                    GoCombatSolutionTest = true;
                }
            } else {  // FiringUnit
                if (Addunit.getbaseunit().getVisibilityStatus() != Constantvalues.VisibilityStatus.Visible) {
                    // clicked on concealed unit; don't add
                    GameModule.getGameModule().getChatter().send("Failure to Add Concealed Firer Unit: " + Addunit.getbaseunit().getUnitName() + " in ClickedOnNewParticipants");
                } else {
                    if (AddFirerUnit(Addunit)) {
                        GoCombatSolutionTest = true;
                    }
                }
            }
        }
        // See if a possible solution exists
        if (GoCombatSolutionTest) {
            // if possible solution then decide to test it
            if (IsThereASolutiontoTest(WhichOne)) {GoCombatSolution = true;}
        }

        // if test required then
        if (GoCombatSolution) {
            DetermineFireSolution(); // does LOSCheck and sets up valid solutinos
            // call overarching method to manage LOS, FP and DRM calculations based on Firer/Target selections and display Fire button
            if (ValidSolutions.size() > 0) {ManageCombatSolutionDetermination();}
        }
    }
/*
    protected void ResetParticipants(PersUniti FiringUnit) {
        'called by ASLXNA.Dispform.FireCheck_CheckedChanged
        'this routine manages reset of Firer selections due to Displayform selection
        'if valid Firer/Target selection found, triggers the combat process up to clicking on Fire or cancel
        ' which checks LOS, determines FP and drm
        'once completed returns to XX with no further action there

        'Remove deselected unit from FireGroup and deselect the sprite
        Dim hexnumber As Integer = FiringUnit.BasePersUnit.Hexnum
        'Get list of counters for the hex
        Dim OH As VisibleOccupiedhexes
        OH = CType(Game.Scenario.HexesWithCounter(hexnumber), VisibleOccupiedhexes)

        'remove from FireGroup
        FireGroup.Remove(FiringUnit)
        'confirm that all remaining FG members are ADJACENT (A7.5) - this is only test that needs to be done
        'LOS must still be valid as was prior to deselection; leader only hexes will be checked in ManageCombatSolutionDetermination
        Dim Firinghex As Integer = FirerHex
        Dim Firingloc As Integer = Firerloc
        Dim FiringPos As Integer = Firerpos
        Dim Getlocs = New Terrainvalues.GetALocationFromMapTable(Game.Scenario.LocationCol)
        Dim BaseHexloc As MapDataClassLibrary.GameLocation = Getlocs.RetrieveLocationfromMaptable(Firinghex, Firingloc)
        Dim OKHexloc As MapDataClassLibrary.GameLocation:
        Dim Addtrue As Boolean = false
        Dim StillIn = New List(Of Objectvalues.PersUniti)
        Do
                Addtrue = false
        For Each StillFiring As Objectvalues.PersUniti In FireGroup
        if StillIn.Count = 0 Then
        if BaseHexloc.Hexnum = StillFiring.BasePersUnit.Hexnum And BaseHexloc.
        Location = StillFiring.BasePersUnit.hexlocation Then
        'still qualifies for FireGroup
        StillIn.Add(StillFiring)
        Addtrue = true
        Else
        Dim TestHexloc As MapDataClassLibrary.
        GameLocation = Getlocs.RetrieveLocationfromMaptable(StillFiring.BasePersUnit.Hexnum, StillFiring.BasePersUnit.hexlocation)
        Dim ADJTest As New CombatTerrainvalues.HexBesideC(BaseHexloc, TestHexloc, FiringPos)
        if ADJTest.AreLocationsADJACENT Then
        StillIn.Add(StillFiring)
        Addtrue = true
        End if
        End if
        Else
        For Each StillOK As Objectvalues.PersUniti In StillIn
                OKHexloc = Getlocs.RetrieveLocationfromMaptable(StillOK.BasePersUnit.Hexnum, StillOK.BasePersUnit.hexlocation)
        Dim TestHexloc As MapDataClassLibrary.
        GameLocation = Getlocs.RetrieveLocationfromMaptable(StillFiring.BasePersUnit.Hexnum, StillFiring.BasePersUnit.hexlocation)
        Dim ADJTest As New
        CombatTerrainvalues.HexBesideC(OKHexloc, TestHexloc, StillOK.BasePersUnit.hexPosition)
        if ADJTest.AreLocationsADJACENT Then
        StillIn.Add(StillFiring) :Addtrue = true
        Exit For
        End if
        Next
        End if
        Next
        if Addtrue = false Then Exit Do
                Loop
        'Update Firegroup to be those still in
        FireGroup = StillIn
        'update display sprites
        'deselect all and reselect those still in
        For Each DisplaySprite As Objectvalues.SpriteOrder In OH.VisibleCountersInHex
        Dim Lambdasprite As Objectvalues.SpriteOrder = DisplaySprite
        DisplaySprite.Selected = false
        Try
        Dim FindUnit As Objectvalues.PersUniti = (From DoMatch In FireGroup Where
        DoMatch.BasePersUnit.Unit_ID = Lambdasprite.ObjID).First
        DisplaySprite.Selected = true
        Catch ex As Exception
        DisplaySprite.Selected = false
        End Try
        Next
        DetermineFireSolution()
        'call overarching method to manage LOS, FP and DRM calculations based on Firer/Target selections and display Fire button
        ManageCombatSolutionDetermination()
    }
*/
    protected void  ManageCombatSolutionDetermination() {
        // called by ClickOnNewParticipants and ResetParticipants and MGandInherentFPSelection.ProcessChoice
        // takes valid Firer/Target selections and manages LOS and FP, DRM calculations
        // calls routines and functions which handle parts and then return here
        // ResetParticipants can start with new FireGroup here as LOS tests are still valid and leader only checks still to come

        if (ValidSolutions.size() > 0) {
            // last check - determine if Fire Group is valid
            // (ie no hexes with just leaders - there may be other tests)
            int Firepower;
            if (FireGroup != null) {
                if (!ConfirmValidFG(FireGroup)) {
                    // no fire possible
                    GameModule.getGameModule().getChatter().send("No FP possible: IfT.ManageCombatSolutionDetermination");
                    ClearCurrentIFT();
                    return;
                }
                FinalDRMLIst.clear(); // this is new code; does it behave properly May 13?
                // confirm terrain variables are in place
                for (LOSSolution Validsol : ValidSolutions) {
                    if (Validsol.getHexesInLOS().size() == 0) {
                        for (CombatTerrain ComTer : LOSTest.TempCombatTerrCol) {
                            if (ComTer.getSolID() == Validsol.getID()) {
                                Validsol.AddtoLOSList(ComTer);
                            }
                        }
                    } else {
                        //GameModule.getGameModule().getChatter().send("No Need to add Hexes to HexesInLOS; already there: IFTC.ManageCombatSolutionDetermination");
                    }
                    if (Validsol.getAltHexesInLOS().size() == 0) {
                        for (AltHexGTerrain Althex : ThreadManager.AltHexLOSGroup) {
                            if (Althex.getTempSolID() == Validsol.getID()) {
                                Validsol.AddtoAltHexList(Althex);
                            }
                        }
                    }
                }
                LOSTest.TempCombatTerrCol.clear(); // can' t do in ClearTempCombat (called in determinefiresolution)as need to use in above loop
                // now see if FireGroup can bring FP to bear
                CombatCalci CombatCalc = new CombatCalcC(ValidSolutions);
                Firepower = CombatCalc.CalcCombatFPandDRM(FireGroup, TargGroup, scendet, -1);
                // pass UsingSol as -1 to indicate need to use all ValidSolutions (possible multi-location FG)
                FinalDRMLIst = CombatCalc.getFinalDRMList();
                if (Firepower == -99) {   // LOS Blocked
                    ClearCurrentIFT();
                    return;
                }

                // fire solution report and request
                String combatstring = "You have an IFT attack ready: ";
                String attackersverb = "attacks ";
                String combatunits="";
                if(FireGroup.size() > 1 ) {
                    attackersverb = "attack ";
                }
                for (PersUniti firingunit: FireGroup){
                    combatunits += firingunit.getbaseunit().getUnitName() + " ";
                }
                combatstring += combatunits + attackersverb;
                for (PersUniti eachTarget : TargGroup) {
                    combatstring += eachTarget.getbaseunit().getUnitName() + " ";
                }
                String drmstring = "";
                for (IFTMods combatdrm: FinalDRMLIst) {
                    if (combatdrm.getDRMLocation() != null) {drmstring = " in " + combatdrm.getDRMLocation().getHex().getName() + " ";}
                    drmstring += Integer.toString(combatdrm.getDRM()) + " " + combatdrm.getDRMdesc() + drmstring;
                }
                PersUniti testTarg = TargGroup.getFirst();
                combatstring += "with  " + Integer.toString(testTarg.getTargetunit().getAttackedbyFP()) +
                        " FP and a " + Integer.toString(testTarg.getTargetunit().getAttackedbydrm()) + " drm";
                if (FinalDRMLIst.size() > 0) combatstring += "(" + drmstring + ")";
                GameModule.getGameModule().getChatter().send(combatstring);
                // clicking fire button will trigger combat; clicking units will rework fire solution back to here
                GameModule.getGameModule().getChatter().send("Click Fire button to attack!");
            }
        }
    }

    public void ClearCurrentIFT() {
        // called by Gameform.buClear_click, IFT.ManageCombatsolutionDetermination, EnemyValuesConcreteC.SetLOSHFPdrmValues
        // clears all temporary variables associated with ifT combat
        // Dim MapGeo as mapgeoclasslibrary.aslxna.mapgeoc = MapGeovalues.MapGeoC.GetInstance(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        int Hextoclear = 0;
        if (TargGroup.size() > 0) {
            for (PersUniti FirstItem : TargGroup) {
                if (FirstItem.getbaseunit().getSolID() != Hextoclear) {
                    //Hextoclear = FirstItem.getbaseunit().getHexnum();
                    // create as function - deselcting VASL counters
                    /*Dim OH As VisibleOccupiedhexes
                    OH = CType(Game.Scenario.HexesWithCounter(Hextoclear), VisibleOccupiedhexes)
                    For Each DisplaySprite As Objectvalues.SpriteOrder In OH.VisibleCountersInHex
                    DisplaySprite.Selected = false
                    Next*/

                }
                FirstItem.setTargetunit(null);
            }
            TargGroup.clear();
        }
        Hextoclear = 0;
        if (FireGroup.size() > 0) {
            for (PersUniti FirstItem : FireGroup) {
                if (FirstItem.getbaseunit().getSolID() != Hextoclear) {
                    //Hextoclear = FirstItem.getbaseunit().getHexnum();
                    // create as function - deselcting VASL counters
                    /*Dim OH As VisibleOccupiedhexes
                    OH = CType(Game.Scenario.HexesWithCounter(Hextoclear), VisibleOccupiedhexes)
                    For Each DisplaySprite As Objectvalues.SpriteOrder In OH.VisibleCountersInHex
                    DisplaySprite.Selected = false
                    Next*/
                }
                FirstItem.setFiringunit(null);
            }
            FireGroup.clear();
        }
        ValidSolutions.clear();
        FinalDRMLIst.clear();
        ClearTempCombat();
        setFirerhex(null); setFirerloc(null); setFirerpos(Constantvalues.AltPos.None);
        setTargethex(null); setTargetloc(null);
        // ThreadManager.AltHexLOSGroup.clear();
        // ThreadManager.TempAlthexcol.clear();
        LOSTest.TempCombatTerrCol.clear();
        //  Mapgeo.RangeC.ResetValues()
        // CombatReport.ShowClearCombat();
        // saves any terrain changes or LOS check updates/additions to the database
        if (!(scen.getPhase() == Constantvalues.Phase.Movement)) {
            // CODE TO AVOID DUPLICATION - NEEDS TO BE MOVED ELSEWHERE OCT 13
            // temporary while debugging UNDO
            /*Dim ASLMapLink As String = "Scen" & CStr(Game.Scenario.ScenID) :Dim ASLLOSLink
            As String = "LOS" & CStr(Game.Scenario.ScenID)
            // need to pass string value to create terrain collection
            Dim Maptables = MapDatavalues.MapDataC.GetInstance(Trim(ASLMapLink), Game.Scenario.ScenID)
            Maptables.UpdateMaptables(ASLMapLink, ASLLOSLink)*/
        }
    }

    protected void ClearTempCombat() {
        TempTarget.clear();
        TempFireGroup.clear();
        TempSolutions.clear();
        LOSTest.TempAltHexLOSGroup.clear();
    }


    public void ProcessIFTCombat() {

        LinkedList<Hex> TargetHexes = new LinkedList<Hex>();
        LinkedList<Hex> FirerHexes = new LinkedList<Hex>();
        boolean AlreadyAdded = false;
        LinkedList<PersUniti> RemoveList = new LinkedList<PersUniti>();
        int ODR = DR.Diceroll();

        // need to handle cowering and SW breakdown before obtaining ifT result - have to redo FP and drm calc - handled in ifTResultC
        // W Breakdown DR, ROF result, HitLocation Result all set by ifTResultC as accessible properties

        // store target and firer hexes for sprite redraw at end of method
        for (PersUniti TargetUnit: TargGroup) {
            AlreadyAdded = false;
            for (Hex HexAdded: TargetHexes) {
                if (HexAdded.getName() == TargetUnit.getbaseunit().getHexName()) {
                    AlreadyAdded = true;
                }
            }
            if (!AlreadyAdded) {TargetHexes.add(scen.getGameMap().getHex(TargetUnit.getbaseunit().getHexName()));}
        }
        for (PersUniti FirerUnit: FireGroup) {
            AlreadyAdded = false;
            for (Hex HexAdded: FirerHexes) {
                if (HexAdded.getName() == FirerUnit.getbaseunit().getHexName()) {
                    AlreadyAdded = true;
                }
            }
            if (!AlreadyAdded) {FirerHexes.add(scen.getGameMap().getHex(FirerUnit.getbaseunit().getHexName()));}
        }
        // check for SAN
        if (ODR == getTargetSan()) {
            // create sniper hover
            // NEED TO IMPLMENT NEW WAY OF DOING THIS
            /*Dim SnipTexture As String = Trim(Game.Linqdata.GetNatInfo(TargetSide, 2)) & "Sniper"
            TargSanString = SnipTexture
            Dim Passposition As Integer = 1 :Dim PassTexture As Microsoft.
            Xna.Framework.Graphics.Texture2D = Game.Content.Load(Of Texture2D) (Trim(SnipTexture))
            Dim HoverToAdd = New
            Objectvalues.HoverItem(PassTexture, 1, "Sniper Check", Passposition, TargetSide, Constantvalues.HoverAction.SniperAvail, 0)
            Game.SniperToDraw.Add(HoverToAdd)*/
        }
        // returns same stack but with ifT result added for each thing in the stack - concealed units are revealed and OBUnit replaces them in TargGroup
        IFTRes = new IFTResultC();
        // check cowering and breakdown - final FP adjustements
        FireGroup = IFTRes.getSWBrkDwn(TargGroup, DR, FireGroup);
        if (FireGroup.size() == 0) { // no FP left so result is NR
            for (PersUniti TargetUnit : TargGroup) {
                TargetUnit.getTargetunit().setIFTResult(Constantvalues.IFTResult.NR);
            }
        } else {
            if (IFTRes.getBreakdown12()) {
                scen  = ScenarioC.getInstance();
                scendet = scen.getScendet();
                CombatCalci CombatCalc = new  CombatCalcC(ValidSolutions);
                CombatCalc.CalcCombatFPandDRM(FireGroup, TargGroup, scendet, -1);
            }
        }
        FireGroup = IFTRes.getCoweringAdj(TargGroup, DR, FireGroup);
        // now determine IFT results
        TargGroup = IFTRes.GetIFTResult(TargGroup, DR, FireGroup);

        // test code
//        for (PersUniti eachTarget: TargGroup){
//            GameModule.getGameModule().getChatter().send(eachTarget.getTargetunit().getCombatResultsString() + " test");
//        }
        // move to combat resolution
        CombatRes = new CombatResC();
        CombatRes.ResolveCombat(TargGroup, IFTRes.getFPdrmCombos(), getFirerSan(), scendet.getScenNum());
        // temporary while debugging UNDO
        /*if (CombatRes.NeedAPopup) {
            ShowPopup(CombatRes.PopupItems, TargGroup.Item(0).BasePersUnit.Hexnum)
            myNeedToResumeResolution = true;
            return;
        }*/
// test code
        for (PersUniti eachTarget: TargGroup){
            GameModule.getGameModule().getChatter().send(eachTarget.getTargetunit().getCombatResultsString());
        }
        // Update Target Group
        for (PersUniti TargUnit: TargGroup) {
            if (TargUnit.getbaseunit().getOrderStatus() == Constantvalues.OrderStatus.NotInPlay) {
                RemoveList.add(TargUnit);
            }
        }
        for (PersUniti RemoveUnit: RemoveList) {
            TargGroup.remove(RemoveUnit);
        }
        for (PersUniti AddNewUnit: CombatRes.getNewTargets()) {
            if (AddNewUnit.getbaseunit().getOrderStatus() == Constantvalues.OrderStatus.Prisoner) {
                // NEED A DIFFERERNT IMPLEMENTATION
                /*Dim OH As VisibleOccupiedhexes
                OH = CType(Game.Scenario.HexesWithCounter(AddNewUnit.BasePersUnit.Hexnum), VisibleOccupiedhexes)
                OH.GetAllSpritesInHex()
                OH.RedoDisplayOrder()*/
            }
            TargGroup.add(AddNewUnit);
        }
        RemoveList.clear();
        // Update Fire Group
        for (PersUniti FireUnit: FireGroup) {
            if (FireUnit.getbaseunit().getOrderStatus() == Constantvalues.OrderStatus.NotInPlay) {
                RemoveList.add(FireUnit);
            }
        }
        for (PersUniti RemoveUnit: RemoveList) {
            FireGroup.remove(RemoveUnit);
        }
        for (PersUniti AddNewUnit: CombatRes.getNewFirings()) {
            FireGroup.add(AddNewUnit);
        }
        // need to manage firing and target sprites here: changes due to revealing, breaking, reducing, prep fire, etc
        Constantvalues.CombatStatus NewCombatStatus = GetCombatStatus();
        for (PersUniti firer: FireGroup) {
            firer.getFiringunit().UpdateCombatStatus(firer, NewCombatStatus, IFTRes.getROFdr());
        }

        if (CombatRes.NeedToResume()) {
            myNeedToResumeResolution = true;
            //  create sniper hover
            // NEED TO IMPLEMENT
            return;
        } else {
            CombatRes = null;
        }

        // NEED TO ADD RESID FP COUNTER PLACEMENT
    }
    protected void ResumeCombatResolution() {
        if (CombatRes== null) {return;}
        CombatRes.ResumeResolution();
        myNeedToResumeResolution = false;
        // 'need to manage firing and target sprites here: changes due to revealing, breaking, reducing, prep fire, etc
        Constantvalues.CombatStatus NewCombatStatus = GetCombatStatus();
        for (PersUniti firer: FireGroup) {
            firer.getFiringunit().UpdateCombatStatus(firer, NewCombatStatus, IFTRes.getROFdr());
        }
        // best way is to recreate all sprites in the hex based on final status at this point
        // NEED A NEW WAY TO IMPLEMENT
        /*For Each Firehex As Integer In FirerHexes
        Dim OH As VisibleOccupiedhexes
        OH = CType(Game.Scenario.HexesWithCounter(Firehex), VisibleOccupiedhexes)
        OH.GetAllSpritesInHex()
        OH.RedoDisplayOrder()
        Next
        For Each TargetHex As Integer In TargetHexes
        Dim OH As VisibleOccupiedhexes
        OH = CType(Game.Scenario.HexesWithCounter(TargetHex), VisibleOccupiedhexes)
        OH.GetAllSpritesInHex()
        OH.RedoDisplayOrder()
        Next*/

        /*CombatRes = null;
        if (Game.Unittab==null) {
            Game.Unittab.ShowCombat();
            Game.Unittab.ShowResults();
            Game.Unittab.Visible = true;
        }*/
    }
    protected void ResumeSurrenderResolution(int AssignGuard, int PassTarg) {
        if (CombatRes== null) {return;}
        LinkedList<PersUniti> RemoveList = new LinkedList<PersUniti>();
        CombatRes.ResumeSurrenderResolution(AssignGuard, PassTarg);
        myNeedToResumeResolution = false;
        // Update Target Group
        for (PersUniti TargUnit: TargGroup) {
            if (TargUnit.getbaseunit().getOrderStatus() == Constantvalues.OrderStatus.NotInPlay) {
                RemoveList.add(TargUnit);
            }
        }
        for (PersUniti RemoveUnit: RemoveList) {
            TargGroup.remove(RemoveUnit);
        }
        for (PersUniti AddNewUnit: CombatRes.getNewTargets()) {
            if (AddNewUnit.getbaseunit().getOrderStatus() == Constantvalues.OrderStatus.Prisoner) {
                // NEED A NEW WAY TO IMPLEMENT
            /*Dim OH As VisibleOccupiedhexes
            OH = CType(Game.Scenario.HexesWithCounter(AddNewUnit.BasePersUnit.Hexnum), VisibleOccupiedhexes)
            OH.GetAllSpritesInHex()
            OH.RedoDisplayOrder()
            End if*/
            }
            TargGroup.add(AddNewUnit);
        }
        RemoveList.clear();
        // Update Fire Group
        for (PersUniti FireUnit: FireGroup) {
            if (FireUnit.getbaseunit().getOrderStatus() == Constantvalues.OrderStatus.NotInPlay) {
                RemoveList.add(FireUnit);
            }
        }
        for (PersUniti RemoveUnit: RemoveList) {
            FireGroup.remove(RemoveUnit);
        }
        for (PersUniti AddUnit: CombatRes.getNewFirings()) {
            FireGroup.add(AddUnit);
        }
        // need to manage firing and target sprites here: changes due to revealing, breaking, reducing, prep fire, etc
        Constantvalues.CombatStatus NewCombatStatus = GetCombatStatus();
        for (PersUniti firer: FireGroup) {
            firer.getFiringunit().UpdateCombatStatus(firer, NewCombatStatus, IFTRes.getROFdr());
        }
        // best way is to recreate all sprites in the hex based on final status at this point
        /*For Each Firehex As Integer In FirerHexes
        Dim OH As VisibleOccupiedhexes
        OH = CType(Game.Scenario.HexesWithCounter(Firehex), VisibleOccupiedhexes)
        OH.GetAllSpritesInHex()
        OH.RedoDisplayOrder()
        Next
        For Each TargetHex As Integer In TargetHexes
        Dim OH As VisibleOccupiedhexes
        OH = CType(Game.Scenario.HexesWithCounter(TargetHex), VisibleOccupiedhexes)
        OH.GetAllSpritesInHex()
        OH.RedoDisplayOrder()
        Next
        'Need to redo Guard hex as well to show new prisoner unit
        For Each TargUnit As Objectvalues.PersUniti In CombatRes.NewTargets
        Dim OH As VisibleOccupiedhexes
        OH = CType(Game.Scenario.HexesWithCounter(TargUnit.BasePersUnit.Hexnum), VisibleOccupiedhexes)
        OH.GetAllSpritesInHex()
        OH.RedoDisplayOrder()
        Next*/
        CombatRes = null;
        /*if Not IsNothing(Game.Unittab) Then
        Game.Unittab.ShowCombat()
        Game.Unittab.ShowResults()
        Game.Unittab.Visible = true
        End if*/
    }
    private Constantvalues.CombatStatus GetCombatStatus() {
        switch (scendet.getPhase()) {
            case PrepFire:
                return Constantvalues.CombatStatus.PrepFirer;
            case Movement:
                return Constantvalues.CombatStatus.FirstFirer;
            case DefensiveFire:
                return Constantvalues.CombatStatus.FinalFirer;
            case AdvancingFire:
                return Constantvalues.CombatStatus.AdvFirer;
            default:
                return Constantvalues.CombatStatus.None;
        }
    }

    private void DR_OnDRChanged() {
        // Handles DR.OnDRChanged
        /*String ODRstring = (DR.White + DR.Colored).ToString;
        DRstring = ": White " + DR.White.ToString + "; Colored " + DR.Colored.ToString + " = " + ODRstring;*/
    }
    /*private void ShowPopup (ByVal menuitems As List(Of Objectvalues.MenuItemObjectholderinteface), ByVal
        hexclicked As Integer)
        Dim ListofMenuThings = New ContextBuilder(menuitems)
        'create Context control
        ListofMenuThings.CreateMenu()
        Game.Scenario.ContextMenu.Tag = CStr(hexclicked)
        Game.Scenario.ContextMenu.Text = "Guard"
        Dim popuppoint As New System.Drawing.Point
        Dim MapGeo as mapgeoclasslibrary.
        aslxna.mapgeoc = MapGeovalues.MapGeoC.GetInstance(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        popuppoint = MapGeo.SetPoint(hexclicked)
        popuppoint.X = CInt(popuppoint.X + Game.Window.ClientBounds.Left + Game.translation.X)
        popuppoint.Y = CInt(popuppoint.Y + Game.Window.ClientBounds.Top + Game.translation.Y)
        Game.Scenario.ContextMenu.Show(popuppoint)
        Game.Scenario.ContextMenu.BringToFront()
        Game.contextshowing = true
    }*/

}
