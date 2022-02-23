package VASL.build.module.fullrules.LOSClasses;

import VASL.LOS.Map.Hex;
import VASL.build.module.fullrules.Constantvalues;
import VASL.build.module.fullrules.DataClasses.DataC;
import VASL.build.module.fullrules.DataClasses.EnemyHexLOSHFPdrm;
import VASL.build.module.fullrules.DataClasses.LineofBattle;
import VASL.build.module.fullrules.DataClasses.SupportWeapon;
import VASL.build.module.fullrules.Game.ScenarioC;
import VASL.build.module.fullrules.ObjectClasses.AltHexGTerrain;
import VASL.build.module.fullrules.ObjectClasses.CombatTerrain;
import VASL.build.module.fullrules.ObjectClasses.PersUniti;
import VASL.build.module.fullrules.UtilityClasses.CommonFunctionsC;
import java.util.LinkedList;

/* derived from VB.LOSClassLibrary.ThreadManagerC
* Does not run parallel threads
* */
public class LOSThreadManagerC {
    public LinkedList<AltHexGTerrain> AltHexLOSGroup = new LinkedList<AltHexGTerrain>();   // holds CombatTerrain.AltHexGTerrain instances
    public LinkedList<AltHexGTerrain> TempAlthexcol = new LinkedList<AltHexGTerrain>();


    public LOSThreadManagerC() {

    }

    public void Runthreads(LinkedList<TempSolution> Tempsollist, LinkedList<CombatTerrain> TempTerrCol, LinkedList<EnemyHexLOSHFPdrm> EnemyHexList) {
        Constantvalues.LosStatus PassLOSStatus = Constantvalues.LosStatus.None;
        // going to delete so dont fix LinkedList<GameLO> TempNewLosResults = new LinkedList<GameLO>;
        LinkedList<EnemyHexLOSHFPdrm> ParallelEnemyHexList = EnemyHexList;
        LinkedList<CombatTerrain> ParallelTempTerrCol = new LinkedList<CombatTerrain>();

        for (TempSolution TempSol : Tempsollist) {
            ThreadedLOSCheckC ThreadLOSTest = new ThreadedLOSCheckC();
            PassLOSStatus = ThreadLOSTest.LOSCheck(TempSol);
            if (PassLOSStatus == Constantvalues.LosStatus.None) {
                TempSol.setSolworks(false);
            } else {
                TempSol.setSolworks(true);
                for (CombatTerrain Usehex : ThreadLOSTest.TempCombatTerrCol) {
                    if (TempSol.getID() == Usehex.getSolID()) {
                        ParallelTempTerrCol.add(Usehex);
                    }
                }
                for (AltHexGTerrain Usealthex : ThreadLOSTest.TempAltHexLOSGroup) {
                    if (TempSol.getID() == Usealthex.getTempSolID()) {
                        TempAlthexcol.add(Usealthex);
                    }
                }
            }
            // store the values for hex being checked
            for (EnemyHexLOSHFPdrm EnemyHex : ParallelEnemyHexList) {
                if ((EnemyHex.getHexname() == TempSol.getSeenHex().getName() || EnemyHex.getHexname() == TempSol.getSeeHex().getName()) && EnemyHex.getSolID() == TempSol.getID()) {
                    EnemyHex.setLOSStatus(PassLOSStatus);
                }
            }
        }
        // going to delete so dont fix
        /*for (GameLO NewResult : TempNewLosResults) {
            NewLOSResults.add(NewResult);
        }*/
        for (CombatTerrain Copyhex : ParallelTempTerrCol) {
            TempTerrCol.add(Copyhex);
        }
        EnemyHexList = ParallelEnemyHexList;
    }

    public void CreateNewThreadSolution(TempSolution Tempsol, LinkedList<LOSSolution> ValidSolutions_Thread) {
        // called by EnemyValuesConcreteC.SetLOSHFPdrmValues
        // sets up various lists needed by IFTC when it calculates FP and drm

        // TempSol to ValidSol; uses IFTC as needed to do specific calculations/actions
        LinkedList<CombatTerrain> PassHexesInLOS = new LinkedList<CombatTerrain>(); // NEED TO FIX AS THIS WILL ALWAYS CONTAIN NO ITEMS
        boolean SolutionAdded = AddtoValidSolutions_Thread(Tempsol, ValidSolutions_Thread, PassHexesInLOS);
        // Temp Althexgrain to AltHexLOSGroup
        boolean AltSolutionAdded = AddtoAltHexLOSGroup();
        // return the fire solution

    }

    public boolean AddtoValidSolutions_Thread(TempSolution TempSolitem, LinkedList<LOSSolution> ValidSolutions_Thread, LinkedList<CombatTerrain> PassHexesInLOS) {
        // called by IFT.DetermineFireSolution
        // adds a validated fire solution to the ValidSolutions group

        try {
            ValidSolutions_Thread.add(new LOSSolution(TempSolitem.getSeeHex(),
                    TempSolitem.getSeeLevelInHex(),
                    TempSolitem.getTotalSeeLevel(),
                    TempSolitem.getSeePositionInHex(),
                    TempSolitem.getSeenHex(),
                    TempSolitem.getSeenLevelInHex(),
                    TempSolitem.getTotalSeenLevel(),
                    TempSolitem.getSeenPositionInHex(),
                    TempSolitem.getSolworks(),
                    TempSolitem.getLOSFollows(),
                    TempSolitem.getID(),
                    TempSolitem.getScenMap(),
                    PassHexesInLOS));
            return true;
        } catch (Exception e) {
            return false;
            // MsgBox("Error adding Fire Solution to ValidSolutions", , "IFT.AddtoValidSolutions_Thread")
        }

    }

    public boolean AddtoAltHexLOSGroup() {
        for (AltHexGTerrain TempAltHex : TempAlthexcol) {
            AltHexLOSGroup.add(TempAltHex);
        }
        TempAlthexcol.clear();
        return true;
    }

    public Constantvalues.LosStatus LOSRangeTest(Hex Starthex, Hex Testhex, LinkedList<PersUniti> SeeingTocheck) {
        // called by MapShade.LOSShade
        // determines range between two hexes (pointblank, normal, long, beyond)
        Constantvalues.LosStatus losrangetest=Constantvalues.LosStatus.None;
        int UnitRange = 0;
        int RangetoUse = 1000;
        // Get range between hexes
        // tempoary while debugging undo
        /*Dim MapGeo as mapgeoclasslibrary.aslxna.mapgeoc = MapGeoClassLibrary.ASLXNA.MapGeoC.GetInstance(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        'can use null values if sure instance already exists*/
        int LOSRange = 0;  // MapGeo.CalcRange(Starthexnum, Testhexnum, True);
        // get normal range of "firing" unit
        // get enemy units in hex
        ScenarioC scen = ScenarioC.getInstance();
        CommonFunctionsC comfun = new CommonFunctionsC(scen.getScenID());
        // determine lowest range
        for (PersUniti SelectedUnit : SeeingTocheck) {
            if (SelectedUnit.getbaseunit().getTypeType_ID() == Constantvalues.Typetype.SW) { // Is MG
                SupportWeapon basesw = comfun.GetSupportWeapon(Integer.toString(SelectedUnit.getbaseunit().getLOBLink()));
                UnitRange =  basesw.getRANGE(); //Integer) Linqdata.GetLOBSWData(Constantvalues.LOBItem.GETRANGE, SelectedUnit.getbaseunit().getLOBLink());
            } else {
                LineofBattle lineofBattle = comfun.Getlob(Integer.toString(SelectedUnit.getbaseunit().getLOBLink()));
                UnitRange = lineofBattle.getRange();
                //UnitRange = Integer.parseInt(Linqdata.GetLOBData(Constantvalues.LOBItem.GETRANGE, SelectedUnit.getbaseunit().getLOBLink()));
                // temporary while debugging undo
                /*Dim GetUnit As DataClassLibrary.OrderofBattle = Linqdata.GetUnitfromCol(SelectedUnit.BasePersUnit.Unit_ID);
                // adjust if wounded
                if (GetUnit.FortitudeStatus = Constantvalues.FortitudeStatus.Enc_Wnd ||
                        GetUnit.FortitudeStatus = Constantvalues.FortitudeStatus.Fan_Wnd ||
                        GetUnit.FortitudeStatus = Constantvalues.FortitudeStatus.Fan_Wnd_Enc ||
                        GetUnit.FortitudeStatus = Constantvalues.FortitudeStatus.Wounded) {
                    if (UnitRange == 4) {
                        UnitRange = 3;
                    }  // handle wounded heros and ldrheros
                }*/
            }
            if (UnitRange < RangetoUse && UnitRange > 0) {
                RangetoUse = UnitRange;
            }
        }
        // determine range type
        if (LOSRange > RangetoUse && LOSRange <= (2 * RangetoUse)) {
            losrangetest = Constantvalues.LosStatus.LongRange;
        } else if (LOSRange > (2 * RangetoUse)) {
            losrangetest = Constantvalues.LosStatus.BeyondLR;
        } else if (LOSRange > 1 && LOSRange <= RangetoUse) { // normal range but not PBF
            losrangetest = Constantvalues.LosStatus.NormalRange;
        } else if (LOSRange == 1 || LOSRange == 0) {   // PBF/TPBF
            losrangetest = Constantvalues.LosStatus.PointBlank;
        } else {
            // System.Windows.Forms.MessageBox.Show("Important range calculation error", "MapShade.LOSRangeTest")
        }
        return losrangetest;
    }

    public boolean AddtoCombatTerrain(LinkedList<CombatTerrain> TempTerrCol, LinkedList<CombatTerrain> CombatTerrcol) {
        for (CombatTerrain TempCT: TempTerrCol) {
            if (TempCT==null) {
                // MessageBox.Show("found an error in combatterrain")
            } else {
                CombatTerrcol.add(TempCT);
            }
        }
        return true;
    }
}
