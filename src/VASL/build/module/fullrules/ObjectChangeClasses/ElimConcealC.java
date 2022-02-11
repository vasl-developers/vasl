package VASL.build.module.fullrules.ObjectChangeClasses;

import VASL.build.module.fullrules.Constantvalues;
import VASL.build.module.fullrules.DataClasses.DataC;
import VASL.build.module.fullrules.ObjectClasses.PersUniti;
import VASL.build.module.fullrules.ObjectClasses.ScenarioCollectionsc;

public class   ElimConcealC implements VisibilityChangei {
    //'Private MovUnitCon As DataClassLibrary.Concealment
    private ScenarioCollectionsc Scencolls = ScenarioCollectionsc.getInstance();
    private int pConToRemove;
    private String myRevealResults = "";

    public ElimConcealC(int ConID) {
        //'MovUnitCon = Linqdata.GetConcealmentfromCol(UnitID)
        pConToRemove = ConID;
    }

    public boolean TakeAction() {
        // alled by Movement.MovementValidation.New
        PersUniti ConToRemoveUnit=null;
        /*if (Linqdata.RemoveConFromCol(pConToRemove)) { // (CInt(MovUnitCon.Con_ID)) Then   TEMP while debugging REMOVE
            for (PersUniti ConToGo : Scencolls.Unitcol) {
                if (ConToGo.getbaseunit().getSW_ID() == pConToRemove &&
                        ConToGo.getbaseunit().getTypeType_ID() == Constantvalues.Typetype.Concealment) {
                    ConToRemoveUnit = ConToGo;
                    break;
                }
            }
        }*/
        if (ConToRemoveUnit != null) {
            Scencolls.Unitcol.remove(ConToRemoveUnit);
            return true;
        } else {
            return false;
        }
    }

    public String getActionResult() {

        return myRevealResults;
    }
}
