package VASL.build.module.fullrules.IFTCombatClasses;

import VASL.build.module.fullrules.Constantvalues;
import VASL.build.module.fullrules.Game.ScenarioC;
import VASL.build.module.fullrules.ObjectChangeClasses.ElimConcealC;
import VASL.build.module.fullrules.ObjectChangeClasses.RevealUnitC;
import VASL.build.module.fullrules.ObjectChangeClasses.VisibilityChangei;
import VASL.build.module.fullrules.ObjectClasses.PersUniti;
import VASL.build.module.fullrules.ObjectClasses.ScenarioCollectionsc;
import VASL.build.module.fullrules.ObjectClasses.SuppWeapi;
import VASL.build.module.fullrules.ObjectFactoryClasses.PersCreation;
import VASL.build.module.fullrules.UtilityClasses.*;
import VASSAL.build.GameModule;
import VASSAL.counters.GamePiece;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class IFTResultC implements IFTResulti {
    // called by 
    private Constantvalues.HitLocation pHitLoc;
    private int pROFdr;
    //'private pSniperDR As Integer
    private boolean pBreakdown12;
    private boolean pCowers;
    private LinkedList<TargetType> FPdrmList = new LinkedList<TargetType>();
    private ScenarioCollectionsc Scencolls = ScenarioCollectionsc.getInstance();
    public Constantvalues.HitLocation getHitLocation() {
        return pHitLoc;
    }
    public void setHitLocation(Constantvalues.HitLocation value) {
        pHitLoc = value;
    }
    public int getROFdr() {
        return pROFdr;
    }
    public void setROFdr(int value) {
        pROFdr = value;
    }
    public boolean getBreakdown12() {
        return pBreakdown12;
    }
    public void setBreakdown12(boolean value) {
        pBreakdown12 = value;
    }
    public boolean getCowers() {
        return pCowers;
    }
    public void setCowers(boolean value) {
        pCowers = value;
    }
    public LinkedList<TargetType> getFPdrmCombos() {
        return FPdrmList;
    }

    public LinkedList<PersUniti> getSWBrkDwn(LinkedList<PersUniti> TargGroup, DiceC DR, LinkedList<PersUniti> FireGroup) {
        // called by IFTC.ProcessIFTCombat
        // process dice roll against the stack, handling any SW breakdown
        // removes SW from FireGroup, FP recalc is done in IFTC.ProcessIFTCombat

        // Malfunction
        if (DR.getWhite() + DR.getColored() == 12) {
            pBreakdown12 = true;
        } else {
            pBreakdown12 = false;
        }
        // need to do here as could effect AttackedbyFP
        if (pBreakdown12 && FireGrouphasMG(FireGroup)) {
            MGBreakdown(FireGroup); // removes broken weapon from FireGroup

        }

        return FireGroup;
    }

    public LinkedList<PersUniti> getCoweringAdj(LinkedList<PersUniti> TargGroup, DiceC DR, LinkedList<PersUniti> FireGroup) {
        // called by IFTC.ProcessIFTCombat
        // process dice roll against the stack, handling any cowering
        // FP recalc is handled here

        // fp is already rounded down by IFTC.CalcFPandDRM routine
        // cowering
        int DoubleDrop = 0;
        if (DR.getWhite() == DR.getColored()) {
            if (FireGroupCowers(FireGroup, DoubleDrop)) {
                String cowerstring ="Firer cowers: ";
                for (PersUniti TargetUnit : TargGroup) {
                    int newAttackedbyFP = ApplyCowering(TargetUnit.getTargetunit().getAttackedbyFP(), DoubleDrop);
                    TargetUnit.getTargetunit().setAttackedbyFP(newAttackedbyFP);
                    cowerstring += TargetUnit.getbaseunit().getUnitName() + " is now attacked by " + Integer.toString(newAttackedbyFP) + " FP; ";
            }
                GameModule.getGameModule().getChatter().send(cowerstring);
                // need to add routine to determine which units get FinalFire
        }
        }
        return FireGroup;
    }

    public LinkedList<PersUniti> GetIFTResult(LinkedList<PersUniti> TargGroup, DiceC DR, LinkedList<PersUniti> FireGroup) {
        // called by

        // get ift result
        // use empty variables when know that instance already exists
        // how to handle KIA or K/# results - results are applied to other units iwth same FP && drm combo
        // create list of different FP && drm combinations
        TargetType NewTargtype; boolean NeedtoAdd = true;
        for (PersUniti TargetUnit: TargGroup) {
            NeedtoAdd = true;
            // compare FP adn drm of additional Targets
            for (TargetType AlreadyAdded : FPdrmList) {
                if (AlreadyAdded.getAttackedbyFP() == TargetUnit.getTargetunit().getAttackedbyFP() && AlreadyAdded.getattackedbydrm() == TargetUnit.getTargetunit().getAttackedbydrm()) {
                    NeedtoAdd = false;   // FP adn drm combination already exists; no need to create
                    break;
                }
            }
            if (NeedtoAdd) {
                // create new FP && drm combination && add to list
                NewTargtype = new TargetType(TargetUnit.getTargetunit().getAttackedbyFP(), TargetUnit.getTargetunit().getAttackedbydrm());
                FPdrmList.add(NewTargtype);
            }
        }
        // take all units undergoing the same attack && set their IFTResult
        LinkedList<PersUniti>  SameTarget = new LinkedList<PersUniti>(); Constantvalues.IFTResult IFTTableResult; int  FDR  = 0;
        for (TargetType UseTargettype: FPdrmList) {
            for (PersUniti AddtoSameTarget: TargGroup) {
                if (AddtoSameTarget.getTargetunit().getAttackedbyFP() == UseTargettype.getAttackedbyFP() &&
                        AddtoSameTarget.getTargetunit().getAttackedbydrm() == UseTargettype.getattackedbydrm()) {
                    SameTarget.add(AddtoSameTarget);
                }
            }
            FDR = DR.getColored() + DR.getWhite() + SameTarget.get(0).getTargetunit().getAttackedbydrm();
            //test code
            //FDR=7;
            if (FDR > 15) {
                IFTTableResult = Constantvalues.IFTResult.NR;
            } else {
                IFTTableResult = GetIFTResult(SameTarget.get(0).getTargetunit().getAttackedbyFP(), FDR);
            }
            if (IFTTableResult == Constantvalues.IFTResult.K1 ||
                    IFTTableResult == Constantvalues.IFTResult.K2 ||
                    IFTTableResult == Constantvalues.IFTResult.K3 ||
                    IFTTableResult == Constantvalues.IFTResult.K4 ||
                    IFTTableResult == Constantvalues.IFTResult.KIA ||
                    IFTTableResult == Constantvalues.IFTResult.KIA1 ||
                    IFTTableResult == Constantvalues.IFTResult.KIA2 ||
                    IFTTableResult == Constantvalues.IFTResult.KIA3 ||
                    IFTTableResult == Constantvalues.IFTResult.KIA4 ||
                    IFTTableResult == Constantvalues.IFTResult.KIA5 ||
                    IFTTableResult == Constantvalues.IFTResult.KIA6 ||
                    IFTTableResult == Constantvalues.IFTResult.KIA7) { // need to do random sel
                DoRandomSelection(IFTTableResult, SameTarget);
            } else {
                for (PersUniti SetTarget : SameTarget) {
                    SetTarget.getTargetunit().setIFTResult(IFTTableResult);
                }
            }
        }
        // need to check for concealment loss plus  if concealed stack suffers KIA or K result then need to do random selection
        ConcealmentLossCheck(TargGroup);
        // test code
        for (PersUniti Targtest: TargGroup) {
            FDR = DR.getColored() + DR.getWhite() + Targtest.getTargetunit().getAttackedbydrm();
            // report combat result
            //FDR=9;
            String MSG =  "FDR = " + Integer.toString(FDR) + ", ";
            CombatUtil CombatInfo = new CombatUtil();
            MSG += "result is " + CombatInfo.IFTResultstring(Targtest.getTargetunit().getIFTResult()) + ": ";
            Targtest.getTargetunit().setCombatResultsString(MSG);
            //GameModule.getGameModule().getChatter().send(MSG);
        }

        // ROF
        pROFdr =(DR.getColored() <= 3) ? DR.getColored() : 0;

        //'AmmoShortage()

        // HitLocation
        pHitLoc = (DR.getColored() < DR.getWhite()) ? Constantvalues.HitLocation.Turret : Constantvalues.HitLocation.Hull;

        return TargGroup;
    }

    private boolean FireGroupCowers(LinkedList<PersUniti> FireGroup, int Doubledrop) {
        // determines if a FG cowers
        boolean NotAllFan = false;
        Constantvalues.UClass Classcheck;
        pCowers = false;
        //OrderofBattle SelUnit;
        boolean NotAllBerserk = false; boolean NotAllFinns = false; boolean NotAllBritishElite = false;

        for (PersUniti FiringUnit: FireGroup) {
            if (Constantvalues.Typetype.SW == FiringUnit.getbaseunit().getTypeType_ID()) {
                int selSWOwner  = 0; // temporary while debugging UNDO (From GetSW As Objectvalues.SuppWeapi In Scencolls.SWCol Where
                //GetSW.BaseSW.Unit_ID = FiringUnit.BasePersUnit.Unit_ID Select GetSW.BaseSW.Owner).First
                        //'Dim Selswowner As String = Linqdata.GetOBSWData(Constantvalues.OBSWitem.Owner, FiringUnit.BasePersUnit.Unit_ID)
                        //SelUnit = Linqdata.GetUnitfromCol(selSWOwner);
            }
            if (FiringUnit.getbaseunit().IsUnitALeader()) {
                return false;
            }  // leader prevents cowering
            if (FiringUnit.getbaseunit().getFortitudeStatus() != Constantvalues.FortitudeStatus.None && FiringUnit.getbaseunit().getFortitudeStatus() != Constantvalues.FortitudeStatus.Encircled) {
                //'unit is fanatic  - THIS IS NOT RIGHT && NEEDS TO BE RECODED
            } else {
                NotAllFan = true;
            }
            if (FiringUnit.getbaseunit().getOrderStatus() != Constantvalues.OrderStatus.Berserk) {
                NotAllBerserk = true;
            }
            Classcheck =Constantvalues.UClass.NONE; // temporary while debugging UNDO Integer.parseInt(Linqdata.GetLOBData(Constantvalues.LOBItem.UNITCLASS, (int) SelUnit.getLOBLink()));
            if (FiringUnit.getbaseunit().getNationality() != Constantvalues.Nationality.British) {
                NotAllBritishElite = true;
            }
            if (FiringUnit.getbaseunit().getNationality() != Constantvalues.Nationality.Finns) {
                NotAllFinns = true;
            }
            if (Classcheck != Constantvalues.UClass.ELITE && Classcheck != Constantvalues.UClass.FIRSTLINE && Classcheck != Constantvalues.UClass.ENGINEER &&
                    Classcheck != Constantvalues.UClass.AIRBORNE && Classcheck != Constantvalues.UClass.AELITE && Classcheck != Constantvalues.UClass.AFIRSTLINE &&
                    Classcheck != Constantvalues.UClass.AENGINEER &&
                    Classcheck != Constantvalues.UClass.AAIRBORNE && Classcheck != Constantvalues.UClass.ASELITE && Classcheck != Constantvalues.UClass.ASFIRSTLINE &&
                    Classcheck != Constantvalues.UClass.ASENGINEER &&
                    Classcheck != Constantvalues.UClass.ASAIRBORNE && Classcheck != Constantvalues.UClass.SELITE && Classcheck != Constantvalues.UClass.SFIRSTLINE &&
                    Classcheck != Constantvalues.UClass.SENGINEER && Classcheck != Constantvalues.UClass.SAIRBORNE) {
                NotAllBritishElite = true;
            }
            if (Classcheck == Constantvalues.UClass.GREEN || Classcheck == Constantvalues.UClass.CONSCRIPT || Classcheck == Constantvalues.UClass.AGREEN ||
                    Classcheck == Constantvalues.UClass.ACONSCRIPT || Classcheck == Constantvalues.UClass.ASGREEN || Classcheck == Constantvalues.UClass.ASCONSCRIPT ||
                    Classcheck == Constantvalues.UClass.SGREEN || Classcheck == Constantvalues.UClass.SCONSCRIPT) {
                Doubledrop = -1;
            }
        }

        if (NotAllFan = false) {return false;}  // fanaticism prevents cowering
        if (NotAllBerserk = false) {return false;}  // berserk units dont cower
        if (NotAllBritishElite = false) {return false;}  // British elite units dont cower
        if (NotAllFinns = false) {return false;} // Finns don' t cower
        // past here, cowering occurs
        return true;
    }

    private int ApplyCowering(int StartingFP, int Doubledrop) {
        int FirePowerCowers = StartingFP;
        pCowers = true;
        for (int x = Doubledrop; x < 1; x++) {
            switch (FirePowerCowers) {
                case 36:
                    FirePowerCowers = 30;
                    break;
                case 30:
                    FirePowerCowers = 24;
                    break;
                case 24:
                    FirePowerCowers = 20;
                    break;
                case 20:
                    FirePowerCowers = 16;
                    break;
                case 16:
                    FirePowerCowers = 12;
                    break;
                case 12:
                    FirePowerCowers = 8;
                    break;
                case 8:
                    FirePowerCowers = 6;
                    break;
                case 6:
                    FirePowerCowers = 4;
                    break;
                case 4:
                    FirePowerCowers = 2;
                    break;
                case 2:
                    FirePowerCowers = 1;
                    break;
                case 1:
                    FirePowerCowers = 0;
                    break;
                default:
            }
        }
        return FirePowerCowers;
    }

    private void DoRandomSelection(Constantvalues.IFTResult IfTtableresult, LinkedList<PersUniti> SameTarget) {
        RandomSelection RndSel = new RandomSelection();
        // determine number of units  affected
        int Impact  = RndSel.DetermineImpact(IfTtableresult);
        // determine IFT result for ( unit
        Constantvalues.IFTResult PrimeResult = RndSel.DeterminePrimResult(IfTtableresult);
        Constantvalues.IFTResult SecondaryResult  = RndSel.DetermineSecondaryResult(IfTtableresult);
        // SET result for ( unit
        if (Impact >= SameTarget.size()) { // everyone impacted
            for (PersUniti UseTarget : SameTarget) {
                UseTarget.getTargetunit().setIFTResult(PrimeResult);
            }
            return;
        }
        // need to do random selection
        boolean[] SelItems;
        SelItems =RndSel.RandomSel(Impact,SameTarget.size());
        for (int y = 0; y < SameTarget.size(); y++) {
            if (SelItems[y] == true) { // random selected
                SameTarget.get(y).getTargetunit().setIFTResult(PrimeResult);
            } else {
                SameTarget.get(y).getTargetunit().setIFTResult(SecondaryResult);
            }
        }
    }
    private void ConcealmentLossCheck(LinkedList<PersUniti> TargGroup) {
        // checks for concealment loss && handles it plus if concealed stack suffers KIA or K result then need to do random selection
        // does not handle sprites

        // use empty variables when know that instance already exists
        LinkedList<PersUniti> RemoveConTarg = new LinkedList<PersUniti>();   // holds any revealed dummies
        ArrayList<String> RemoveCon = new ArrayList();  // holds any revealed Concealment ID
        String ConLost = ""; String Conrevealed = ""; String ConLostHex;
        int[] DelConAdded;
        LinkedList<PersUniti> Revealedlist = new LinkedList<PersUniti>();
        LinkedList<PersUniti> NewTargets = new LinkedList<PersUniti>();
        LinkedList<PersUniti> TempNewTargets = new LinkedList<PersUniti>();
        PersUniti AddTarget;
        for (PersUniti CheckItem: TargGroup) {
            if (CheckItem.getTargetunit().getVisibilityStatus() == Constantvalues.VisibilityStatus.Concealed ||
                    CheckItem.getTargetunit().getVisibilityStatus()== Constantvalues.VisibilityStatus.Hidden){
                if (CheckItem.getTargetunit().getIFTResult() != Constantvalues.IFTResult.NR) {
                    //concealed units are revealed
                    CommonFunctionsC ToDO = new CommonFunctionsC(CheckItem.getbaseunit().getScenario());
                    GamePiece ToReveal = ToDO.GetGamePieceConCounterFromID(CheckItem.getbaseunit().getUnit_ID());
                    if (ToReveal != null) {ToReveal.keyEvent(KeyStroke.getKeyStroke('D', java.awt.event.InputEvent.CTRL_MASK));}
                }
            }

            if (Constantvalues.Typetype.Concealment == CheckItem.getbaseunit().getTypeType_ID()) {
                if (CheckItem.getTargetunit().getIFTResult() != Constantvalues.IFTResult.NR) {
                    //concealed units are revealed
                    //'Dim ConCheck As DataClassLibrary.Concealment = Linqdata.GetConcealmentfromCol(CheckItem.BasePersUnit.Unit_ID)
                    // delete Con counter
                    RemoveCon.add(Integer.toString(CheckItem.getbaseunit().getUnit_ID())); // put in list to remove from collection
                    RemoveConTarg.add(CheckItem); // put in list to remove from TargGroup
                    ConLost = CheckItem.getbaseunit().getUnitName();
                    ConLostHex = CheckItem.getbaseunit().getHexName();
                    // check for dummies
                    if (CheckItem.getTargetunit().getIsDummy()) {
                        // process the removals after TargUnit loop to ensure integrity of lists
                        Conrevealed = "Dummy";
                    } else {
                        // find revealed units
                        for (PersUniti testunit: Scencolls.Unitcol) {
                            if (testunit.getbaseunit().getCon_ID() == CheckItem.getbaseunit().getUnit_ID()) {
                                Revealedlist.add(testunit);
                            }
                        }
                        for (PersUniti RevealUnit : Revealedlist) {
                            VisibilityChangei UnittoChange = new RevealUnitC(RevealUnit.getbaseunit().getUnit_ID());
                            int RevealID = CheckItem.getbaseunit().getCon_ID();
                            // must be set before Unit is revealed && Con_ID set to 0
                            if (UnittoChange.TakeAction()) { // this will add sprite
                                Conrevealed += " " + UnittoChange.getActionResult();
                                // create new target units && store in list - add to TargGroup later
                                if (RevealUnit.getTargetunit() == null) {
                                    //'Dim PassUnit As DataClassLibrary.OrderofBattle = Linqdata.GetUnitfromCol(RevealUnit.BasePersUnit.Unit_ID)
                                    PersCreation UseObjFact = new PersCreation();
                                    RevealUnit.setTargetunit(UseObjFact.createtargetunitproperty(RevealUnit, CheckItem.getTargetunit().getFirerSan()));
                                }
                                RevealUnit.getTargetunit().setAttackedbyFP(CheckItem.getTargetunit().getAttackedbyFP());
                                RevealUnit.getTargetunit().setAttackedbydrm(CheckItem.getTargetunit().getAttackedbydrm());
                                RevealUnit.getTargetunit().setIFTResult(CheckItem.getTargetunit().getIFTResult());
                                AddTarget = RevealUnit; //'New Objectvalues.PersUniti(RevealUnit, CheckItem.SolID)
                                TempNewTargets.add(AddTarget);
                            } else {
                                //'MessageBox.Show(MovingUnit.ItemName & " could not be revealed. Action fails")
                            }
                        }
                        // need to do random selection on revealed units if KIA or K result
                        if (CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.K1 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.K2 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.K3 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.K4 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA1 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA2 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA3 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA4 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA5 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA6 ||
                                CheckItem.getTargetunit().getIFTResult() == Constantvalues.IFTResult.KIA7) {
                            DoRandomSelection(CheckItem.getTargetunit().getIFTResult(), TempNewTargets);
                        }
                    }
                    //MessageBox.Show(ConLost & ": Concealment Lost - revealed as " & Trim(Conrevealed) & " in " & ConLostHex)
                    Conrevealed = "";
                }
            }
            for (PersUniti GetTarget : TempNewTargets) {
                NewTargets.add(GetTarget);
            }
        }
        // remove revealed Concealment from the collection && the target list
        // collection && Scencolls.concol
        if (RemoveCon.size() > 0) {
            for (String ConRemove : RemoveCon) {
                // delete unit from Concealment && Scencolls - since all concealed units under the ? counter are now revealed
                VisibilityChangei UnittoChange = new ElimConcealC(Integer.parseInt(ConRemove));
                UnittoChange.TakeAction();
            }
        }
        // remove revealed concealed targets from TargGroup
        for (PersUniti RemoveItem: RemoveConTarg) {
            TargGroup.remove(RemoveItem);
        }
        // add revealed units to TargGroup
        for (PersUniti GetAddTarget: NewTargets) {
            TargGroup.add(GetAddTarget);
        }
        if (TargGroup.size() == 0) {
            //MessageBox.Show("Dummies revealed: no target units left")
            return;
        }
    }
    private boolean FireGrouphasMG(LinkedList<PersUniti> FireGroup) {
        for (PersUniti FiringUnit: FireGroup) {
            if (FiringUnit.getFiringunit().getUsingfirstMG()) {
                for (SuppWeapi CheckMG: FiringUnit.getFiringunit().FiringMGs) {
                    if (CheckMG.getbaseSW().getSW_ID()== FiringUnit.getbaseunit().getFirstSWLink()) {return true;}
                }
            }
            if (FiringUnit.getFiringunit().getUsingsecondMG()) {
                for (SuppWeapi CheckMG : FiringUnit.getFiringunit().FiringMGs) {
                    if (CheckMG.getbaseSW().getSW_ID() == FiringUnit.getbaseunit().getSecondSWLink()) {return true;}
                }
            }
        }
        // if here then no MG
        return false;
    }

    private boolean MGBreakdown(LinkedList<PersUniti> FireGroup) {
        int MGCount=0; SuppWeapi[] FiringMG = new SuppWeapi[100] ;
        // determine how many MG
        for (PersUniti FiringUnit: FireGroup) {
            if (FiringUnit.getFiringunit().getUsingfirstMG()) {
                for (SuppWeapi CheckMG: FiringUnit.getFiringunit().FiringMGs) {
                    if (CheckMG.getbaseSW().getSW_ID()== FiringUnit.getbaseunit().getFirstSWLink()) {
                        MGCount=+1;
                        FiringMG[MGCount] = CheckMG;
                        break;
                    }
                }
            }
            if (FiringUnit.getFiringunit().getUsingsecondMG()) {
                for (SuppWeapi CheckMG : FiringUnit.getFiringunit().FiringMGs) {
                    if (CheckMG.getbaseSW().getSW_ID() == FiringUnit.getbaseunit().getSecondSWLink()) {
                        MGCount +=1;
                        FiringMG[MGCount] = CheckMG;
                        break;
                    }
                }
            }
        }
        // do random selection
        RandomSelection RndSel = new RandomSelection();
        boolean[] SelItems = new boolean[100];
        SelItems = RndSel.RandomSel(1, MGCount);
        for (int x = 0; x < MGCount; x++) {
            if (SelItems[x] == true) { //random selected
                // break MG
                SuppWeapi SWToBreak = FiringMG[x + 1];  // +1 is needed because FiringMG are added starting at 1 and SelItems is zero-based
                SWToBreak.getbaseSW().setSWStatus(Constantvalues.SWStatus.Brokendown);
                // remove from FG
                for (PersUniti FiringUnit: FireGroup){
                    if (FiringUnit.getbaseunit().getFirstSWLink() == SWToBreak.getbaseSW().getSW_ID()) {FiringUnit.getFiringunit().setUsingfirstMG(false);}
                    if (FiringUnit.getbaseunit().getSecondSWLink() == SWToBreak.getbaseSW().getSW_ID()) {FiringUnit.getFiringunit().setUsingsecondMG(false);}
                }
                // trigger counter action
                CounterActions counteractions = new CounterActions();
                counteractions.flipcounter(SWToBreak);
            }
        }
        return true;
    }
    private void RecalcFP(LinkedList<PersUniti> FireGroup, LinkedList<PersUniti> TargGroup) {

    }
    private Constantvalues.IFTResult GetIFTResult(int FPCol, int FDR){
        ScenarioC scen = ScenarioC.getInstance();
        HashMap<String, IFTTableResult> prIFTTableLookUp = scen.getIFTTableLookUp();
        String resultname = Integer.toString(FDR) + "." + Integer.toString(FPCol);
        IFTTableResult ifttableresult = prIFTTableLookUp.get(resultname);
        return ifttableresult.getIFTResult();
    }
}
