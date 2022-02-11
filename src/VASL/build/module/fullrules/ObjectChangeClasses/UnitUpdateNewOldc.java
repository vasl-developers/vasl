package VASL.build.module.fullrules.ObjectChangeClasses;

import VASL.build.module.fullrules.ObjectClasses.PersUniti;
import VASL.build.module.fullrules.ObjectClasses.ScenarioCollectionsc;
import VASL.build.module.fullrules.ObjectClasses.SuppWeapi;
import VASL.build.module.fullrules.ObjectFactoryClasses.PersCreation;

public class UnitUpdateNewOldc {

    private ScenarioCollectionsc Scencolls = ScenarioCollectionsc.getInstance();

    public UnitUpdateNewOldc(PersUniti NewUnit, PersUniti OldUnit) {
        // this class updates the values of a newly created unit (NewUnit) to match those of the unit it was created from (OldUnit)
        // called by various object changes, such as replaces, reduces, hardens

        PersCreation UseObjectFactory = new PersCreation();
        NewUnit.getbaseunit().setHexname(OldUnit.getbaseunit().getHexName());
        NewUnit.getbaseunit().sethexlocation(OldUnit.getbaseunit().gethexlocation());
        NewUnit.getbaseunit().sethexPosition(OldUnit.getbaseunit().gethexPosition());
        NewUnit.getbaseunit().setLevelinHex(OldUnit.getbaseunit().getLevelinHex());
        NewUnit.getbaseunit().setHexEntSideCrossed(OldUnit.getbaseunit().getHexEntSideCrossed());
        NewUnit.getbaseunit().setCX(OldUnit.getbaseunit().getCX());
        NewUnit.getbaseunit().setELR(OldUnit.getbaseunit().getELR());
        NewUnit.getbaseunit().setFirstSWLink(OldUnit.getbaseunit().getFirstSWLink());
        NewUnit.getbaseunit().setSecondSWLink(OldUnit.getbaseunit().getSecondSWLink());
        NewUnit.getbaseunit().setnumSW(OldUnit.getbaseunit().getnumSW());
        NewUnit.getbaseunit().setFortitudeStatus(OldUnit.getbaseunit().getFortitudeStatus());
        NewUnit.getbaseunit().setMovementStatus(OldUnit.getbaseunit().getMovementStatus());
        NewUnit.getbaseunit().setPinned(OldUnit.getbaseunit().getPinned());
        NewUnit.getbaseunit().setRoleStatus(OldUnit.getbaseunit().getRoleStatus());
        NewUnit.getbaseunit().setVisibilityStatus(OldUnit.getbaseunit().getVisibilityStatus());
        NewUnit.getbaseunit().UpdateBaseStatus(NewUnit);

        if (OldUnit.getTargetunit() != null) {
            int FirerSan = OldUnit.getTargetunit().getFirerSan();
            NewUnit = UseObjectFactory.CreateTargetUnitandProperty(NewUnit, FirerSan);
            NewUnit.getTargetunit().setAttackedbydrm(OldUnit.getTargetunit().getAttackedbydrm());
            NewUnit.getTargetunit().setAttackedbyFP(OldUnit.getTargetunit().getAttackedbyFP());
            NewUnit.getTargetunit().setELR5(OldUnit.getTargetunit().getELR5());
            NewUnit.getTargetunit().setIFTResult(OldUnit.getTargetunit().getIFTResult());
            NewUnit.getTargetunit().setIsConcealed(OldUnit.getTargetunit().getIsConcealed());
            NewUnit.getTargetunit().setIsDummy(OldUnit.getTargetunit().getIsDummy());
            NewUnit.getTargetunit().setPinned(OldUnit.getTargetunit().getPinned());
            NewUnit.getTargetunit().setQualityStatus(OldUnit.getTargetunit().getQualityStatus());
            NewUnit.getTargetunit().setRandomSelected(OldUnit.getTargetunit().getRandomSelected());
            NewUnit.getTargetunit().setSmoke(OldUnit.getTargetunit().getSmoke());
            NewUnit.getTargetunit().setHoBFlag(OldUnit.getTargetunit().getHoBFlag());
            NewUnit.getTargetunit().setMCNumber(OldUnit.getTargetunit().getMCNumber());
            NewUnit.getTargetunit().setTargStackLeaderDRM(OldUnit.getTargetunit().getTargStackLeaderDRM());
            NewUnit.getTargetunit().setPersUnitImpact(OldUnit.getTargetunit().getPersUnitImpact());
            NewUnit.getTargetunit().UpdateTargetStatus(NewUnit);
        }

    // THIS DOES NOT SEEM RIGHT; FIX WHEN HANDLE MOVEMENT
    /*if (OldUnit.getMovingunit() != null) {

        int PassID = OldUnit.getbaseunit().getSW_ID();
        UseObjectFactory.CreateMovingUnit(OldUnit.getbaseunit().getUnittype(), PassID, Scencolls.SelMoveUnits);
        for (PersUniti selunit: Scencolls.SelMoveUnits) {
            if (selunit.getbaseunit().getSW_ID() == PassID)  Select
        selunit).First
        With NewUnit.MovingPersUnit
                .AssaultMove = OldUnit.MovingPersUnit.AssaultMove
                .Dash = OldUnit.MovingPersUnit.Dash
                .HasLdrBonus = OldUnit.MovingPersUnit.HasLdrBonus
                .HexEnteredSideCrossed = OldUnit.MovingPersUnit.HexEnteredSideCrossed
                .IsConcealed = OldUnit.MovingPersUnit.IsConcealed
                .MFAvailable = OldUnit.MovingPersUnit.MFAvailable
                .MFUsed = OldUnit.MovingPersUnit.MFUsed
                .UsingDT = OldUnit.MovingPersUnit.UsingDT
        End With
    }*/
        if (OldUnit.getFiringunit() != null ) {

            NewUnit = UseObjectFactory.CreatefiringUnitandProperty(NewUnit);
                NewUnit.getFiringunit().setCombatStatus(OldUnit.getFiringunit().getCombatStatus());
                for (SuppWeapi addMG: OldUnit.getFiringunit().FiringMGs ) {
                    NewUnit.getFiringunit().FiringMGs.add(addMG);
                }
                NewUnit.getFiringunit().setIsPinned(OldUnit.getFiringunit().getIsPinned());
                NewUnit.getFiringunit().setUseHeroOrLeader(OldUnit.getFiringunit().getUseHeroOrLeader());
                // NEED TO REWORK THIS
                NewUnit.getFiringunit().setUsingfirstMG(OldUnit.getFiringunit().getUsingfirstMG());
                NewUnit.getFiringunit().setUsingsecondMG(OldUnit.getFiringunit().getUsingsecondMG());
                NewUnit.getFiringunit().setUsingInherentFP(OldUnit.getFiringunit().getUsingInherentFP());
                // NewUnit.getFiringunit().UpdateCombatStatus(OldUnit.getFiringunit().getCombatStatus(), OldUnit.getFiringunit().) //HANDLE THIS A BETTER WAY

        }
    }

}
