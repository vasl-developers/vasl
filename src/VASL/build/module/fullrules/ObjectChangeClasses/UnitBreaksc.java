package VASL.build.module.fullrules.ObjectChangeClasses;

import VASL.build.module.fullrules.Constantvalues;
import VASL.build.module.fullrules.Game.ScenarioC;
import VASL.build.module.fullrules.ObjectClasses.PersUniti;
import VASL.build.module.fullrules.ObjectFactoryClasses.PersCreation;
import VASL.build.module.fullrules.UtilityClasses.CommonFunctionsC;
import VASL.build.module.map.StartGame;
import VASL.counters.PlaceDM;
import VASSAL.build.GameModule;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.Stack;

import javax.swing.*;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.LinkedList;

public class UnitBreaksc implements StatusChangei {
    private LinkedList<PersUniti> myNewTargs = new LinkedList<PersUniti>();

    //private myPopUpList As New List(Of ObjectClassLibrary.ASLXNA.MenuItemObjectholderinteface)
    public UnitBreaksc() {

    }
    public boolean Takeaction(PersUniti TargParent) {
        /*Name:       TargetBreaks()

        Identifier UC 205

                    Preconditions()
        1.	Unbroken MMC Target is alive and fails a MC by <= its ELR

                    Basic Course
        1.	Use case begins when a Target fails a MC [several possible causes: UC102-TargetCRMCResult; UC103-TargetMCResult]
        2.	Use case ends when the Target changes status to Broken

        Alternate Course A:
        Condition:

        Inheritance:
        Condition:

                    Post conditions
        1.*/

        if (TargParent.getTargetunit() == null) {
            CommonFunctionsC ComFunc = new CommonFunctionsC(TargParent.getbaseunit().getScenario());
            int FirerSan = ComFunc.GetEnemySan(TargParent.getbaseunit().getNationality());
            PersCreation UseObjectFactory = new PersCreation();
            TargParent = UseObjectFactory.CreateTargetUnitandProperty(TargParent, FirerSan);
        }

        if (TargParent.getTargetunit() != null) {TargParent.getTargetunit().setOrderStatus(Constantvalues.OrderStatus.Broken_DM);}
        TargParent.getbaseunit().setOrderStatus(Constantvalues.OrderStatus.Broken_DM);
        TargParent.getbaseunit().setCX(false);
        TargParent.getbaseunit().setPinned(false);
        TargParent.getbaseunit().setCombatStatus(Constantvalues.CombatStatus.None);
        TargParent.getbaseunit().setMovementStatus(Constantvalues.MovementStatus.NotMoving);
        TargParent.getTargetunit().UpdateTargetStatus(TargParent);
        TargParent.getTargetunit().setCombatResultsString(TargParent.getTargetunit().getCombatResultsString() + " breaks; is DM");

        /*// flip counter and add DM
        CommonFunctionsC ToDO = new CommonFunctionsC(TargParent.getbaseunit().getScenario());
        GamePiece ToBreak = ToDO.GetGamePieceFromID(TargParent.getbaseunit().getSW_ID());
        if (ToBreak != null) {
            ToBreak.keyEvent(KeyStroke.getKeyStroke('F', java.awt.event.InputEvent.CTRL_MASK));
            // Need to push counter action to other computer
        }*/

        // HoB
        if (TargParent.getTargetunit().getHoBFlag()) {   // rolled a 2
            Constantvalues.PersUnitResult HobChange = TargParent.getTargetunit().HOBMC();
            StatusChangei RunStatusChange;
            SelectStatusChangec GetStatusChange = new SelectStatusChangec();
            RunStatusChange = GetStatusChange.HoBStatusChange(HobChange, TargParent);
            if (RunStatusChange != null ) {
                RunStatusChange.Takeaction(TargParent);
            } else {
                //myPopUpList = GetStatusChange.PopUpItems; temporary while debugging UNDO
                return false;
            }
            TargParent.getbaseunit().setOrderStatus(TargParent.getTargetunit().getOrderStatus());
            // update Target and Firing lists with new units
            if (RunStatusChange.getNewTargs() != null) {myNewTargs = RunStatusChange.getNewTargs();}
        }
        return true;
    }
    public LinkedList<PersUniti> getNewTargs () {
        return myNewTargs;
    }
    public LinkedList<PersUniti> getNewFirings () {
        // no code required; no new unit
        return null;
    }

       /*public ReadOnly Property NewPopupitems As List(Of ObjectClassLibrary.ASLXNA.MenuItemObjectholderinteface) Implements StatusChangei.NewPopupitems
            Get

    End Get
    End Property*/

}
