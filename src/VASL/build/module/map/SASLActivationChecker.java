package VASL.build.module.map;

import VASL.LOS.Map.LOSResult;
import VASL.LOS.Map.Location;
import VASL.LOS.Map.Terrain;
import VASL.LOS.VASLGameInterface;
import VASL.build.module.ASLMap;
import VASL.counters.ASLProperties;
import VASL.counters.Concealable;
import VASL.environment.Environment;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.AutoConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.build.module.map.boardPicker.board.Region;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.configure.*;
import VASSAL.counters.*;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.i18n.TranslatableConfigurerFactory;
import VASSAL.tools.NamedKeyStroke;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;

import static VASSAL.build.GameModule.getGameModule;

/*
 * This component identifies ENEMY S? counters that are now within LOS of a FRIENDLY unit and vice versa.
 */
public class SASLActivationChecker extends AbstractConfigurable implements GameComponent, Drawable, KeyListener {
    public static SASLActivationChecker saslActivationChecker;

    protected static ASLMap mainMap;
    protected static Map saslMap;
    protected static Map sacMap;

    protected VASL.LOS.VASLGameInterface VASLGameInterface;

    ArrayList<GamePiece> movingFriendlyCounters = new ArrayList<GamePiece>();
    ArrayList<GamePiece> movingSuspectCounters = new ArrayList<GamePiece>();

    final ArrayList<GamePiece> pieceList = new ArrayList<GamePiece>();

    private String friendlyNationality = "";    // Uses the 2 character nationalities, e.g., "ge", "ru", "ax", etc.
    private String alliedNationalityOne = "";   // Uses the 2 character nationalities, e.g., "ge", "ru", "ax", etc.
    private String alliedNationalityTwo = "";   // Uses the 2 character nationalities, e.g., "ge", "ru", "ax", etc.
    private String enemyNationality = "";       // Uses the SASL nationalities as defined in "private static final String[] nationalities" below.

    private int nvr = -1;

    // KeyListener (begin)
    private static final String NAME = "Name";

    private static final String CLEAR_FLARES_KEY = "ClearFlaresKey";
    private NamedKeyStroke clearFlaresKey = new NamedKeyStroke("d85f6a40"); // CTL+ALT+X, 88,650
    private static final String CHECK_ACTIVATIONS_KEY = "CheckActivationsKey";
    private NamedKeyStroke checkActivationsKey = new NamedKeyStroke("d85f6a41"); // CTL+ALT+S, 83,650
    // KeyListener (end)

    private static final String[] nationalities = {
        "None",
        "Allied Minors",
        "Axis Minors",
        "China",
        "France",
        "Germany",
        "Great Britain",
        "Italy",
        "Japan",
        "Partisan",
        "Russia",
        "US/USMC 44+",
        "USMC 41-43",
    };
    //
    // Values correspond to the layer/level displayed by the suspect counter.
    // 2 - Automatic
    // 3 - Less than or equal to 2
    // 4 - Less than or equal to 1
    // 5 - Less than or equal to 0
    // 6 - Less than or equal to -1
    // 7 - Less than or equal to -2
    // 8 - Long range
    //
    private static final int[][] activationRangesInfantry = {
        // 0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16 }, // Ranges
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // None
        {  2,  2,  3,  4,  5,  5,  6,  7,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // Allied Minors
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Axis Minors
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // China
        {  2,  2,  3,  4,  5,  5,  6,  7,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // France (Vichy)
        {  2,  2,  3,  4,  4,  5,  5,  6,  6,  7,  7,  7,  7,  8,  8,  8,  8 }, // Germany
        {  2,  2,  3,  4,  4,  5,  6,  6,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // Great Britain
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Italy
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Japan
        {  2,  2,  4,  5,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8,  8,  8 }, // Partisan
        {  2,  2,  3,  4,  5,  6,  6,  7,  7,  8,  8,  8,  8,  8,  8,  8,  8 }, // Russia
        {  2,  2,  3,  4,  4,  5,  5,  6,  6,  7,  7,  7,  7,  8,  8,  8,  8 }, // US/USMC 44+
        {  2,  2,  3,  4,  4,  5,  6,  6,  7,  7,  7,  8,  8,  8,  8,  8,  8 }, // USMC 41-43
    };

    private static final int[][] activationRangesVehicles = {
            // 0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16 }, // Ranges
            {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // None
            {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Allied Minors
            {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Axis Minors
            {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // China
            {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // France (Vichy)
            {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Germany
            {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Great Britain
            {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Italy
            {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Japan
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Partisan
            {  2,  2,  3,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Russia
            {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // US/USMC 44+
            {  2,  2,  3,  4,  4,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // USMC 41-43
    };
    /* TODO: NOT NEEDED?
    private static final int[][] activationRangesTrackedVehicles = {
            // 0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16 }, // Ranges
            {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // None
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Allied Minors
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Axis Minors
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // China
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // France (Vichy)
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Germany
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Great Britain
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Italy
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Japan
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Partisan
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // Russia
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // US/USMC 44+
            {  2,  2,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // USMC 41-43
    };
    TODO: NOT NEEDED? */
    // Activation Ranges (end)

    enum CounterType {
        NON_VEHICLE,
        VEHICLE,
        TRACKED_VEHICLE
    }
    /**
     * @return true if SASLActivationChecker should be active.
     */
    public boolean isEnabled() {
        return isSaslExtensionPresent();
    }

    /**
     * Update the mainMap
     * @param m the mainMap
     */
    public static void setMap(ASLMap m) {
        mainMap = m;
    }

    private String getEnemyNationality(GamePiece piece) {
        String result = "";
        Object prop = piece.getProperty("SuspectNationality");
        if (prop != null) {
            result = prop.toString();
        }
        return result;
    }

    private String getNationality(GamePiece piece) {
        String result = "";

        Concealable c = (Concealable)Decorator.getDecorator(piece, Concealable.class);
        if (c != null && c.isMaskable()) {
            Object prop = c.getProperty(ASLProperties.NATIONALITY);
            if (prop != null) {
                result = prop.toString();
            }
        }

        return result;
    }

    private String checkPieceLocation(GamePiece piece, String regionName) {
        String result = "";
        Point currentPoint = piece.getPosition();
        Region region = saslMap.findRegion(regionName);

        if ((region != null) && (currentPoint.getX() == region.getOrigin().getX()) && (currentPoint.getY() == region.getOrigin().getY())) {
            if (regionName.equals("Enemy")) {
                result = getEnemyNationality(piece);
            } else {
                result = getNationality(piece);
            }
        }

        return result;
    }

    private String nationalityOfPieceInRegion(GamePiece[]p, String currentNationality, String regionName) {
        String result = currentNationality;
        String nationality = "";

        outerloop:
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    String temp = checkPieceLocation(pi.nextPiece(), regionName);

                    if (!temp.isEmpty()) {
                        nationality = temp;
                        break outerloop;
                    }
                }
            } else {
                String temp = checkPieceLocation(aP, regionName);

                if (!temp.isEmpty()) {
                    nationality = temp;
                    break outerloop;
                }
            }
        }

        if (currentNationality.isEmpty()) {
            if (!nationality.isEmpty()) {
                result = nationality;
            }
        } else {
            if (nationality.isEmpty()) {
               result = "";
            }
        }

        return result;
    }

    private void nationalityOfEnemy(GamePiece[]p) {
        String nationality = "None";
        String regionName = "Enemy";

        outerloop:
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    String temp = checkPieceLocation(pi.nextPiece(), regionName);

                    if (!temp.isEmpty()) {
                        nationality = temp;
                        break outerloop;
                    }
                }
            } else {
                String temp = checkPieceLocation(aP, regionName);

                if (!temp.isEmpty()) {
                    nationality = temp;
                    break outerloop;
                }
            }
        }

        if (enemyNationality.isEmpty()) {
            if (!nationality.contains("None")) {
                enemyNationality = nationality;
            }
        } else {
            if (nationality.contains("None")) {
                enemyNationality = "";
            }
        }
    }

    private boolean isFriendlyUnit(GamePiece piece) {
        String nationality = getNationality(piece);

        return (!nationality.isEmpty() && (nationality.equals(friendlyNationality) || nationality.equals(alliedNationalityOne) || nationality.equals(alliedNationalityTwo)));
    }

    private void updateNationalities() {
        GamePiece[]p = saslMap.getPieces();

        friendlyNationality = nationalityOfPieceInRegion(p, friendlyNationality, "FrNat");
        alliedNationalityOne = nationalityOfPieceInRegion(p, alliedNationalityOne, "AlNat1");
        alliedNationalityTwo = nationalityOfPieceInRegion(p, alliedNationalityTwo, "AlNat2");

        nationalityOfEnemy(p);
     }

    private int getNvr() {
        GamePiece[]p = sacMap.getPieces();
        GamePiece nvr = null;

        outerloop:
        for (GamePiece aP : p) {
            if (aP instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) aP).getPiecesIterator()); pi.hasMoreElements(); ) {
                    if (pi.nextPiece().getName().contains("NVR")) {
                        nvr = pi.nextPiece();
                        break outerloop;
                    }
                }
            } else {
                if (aP.getName().contains("NVR")) {
                    nvr = aP;
                    break;
                }
            }
        }

        int result = -1;

        if (nvr != null) {
            result = Integer.parseInt(nvr.getName().replace("NVR ", ""));
        }

        return result;
    }

    private void updateNightStatus() {
        if ((sacMap == null) && !isSacExtensionPresent()) {
            return; // Nothing to see here, move along ...
        }

        Environment env = new Environment();

        if (env.isNight()) {
            nvr = getNvr();
        } else {
            nvr = -1;
        }
    }

    private void pieceListClear() {
        if (!pieceList.isEmpty()) {
            for (GamePiece piece : pieceList) {
                piece.setProperty("ActivationFlag", 1);
                piece.setProperty("RangeBracket", 1);
            }

            pieceList.clear();
        }
    }

    private boolean clearMovingCounters(boolean clear) {
        boolean result = clear;

        if (clear) {
            movingFriendlyCounters.clear();
            movingSuspectCounters.clear();
            result = false;
        }

        return result;
    }

    /**
     * Updates the player's view of the mainMap, revealing pieces that are now in LOS
     */
    private void updateView(ArrayList<GamePiece>movedunits) {
        boolean clear = true;

        if ((saslMap == null) && !isSaslExtensionPresent()) {
            return; // Nothing to see here, move along ...
        }

        if (mainMap == null ||  mainMap.getVASLMap() == null) {
            return; // Gotta have a mainMap to update the view
        }

        updateNationalities();

        updateNightStatus();

        VASLGameInterface = new VASLGameInterface(mainMap, mainMap.getVASLMap());
        VASLGameInterface.updatePieces();

        for (GamePiece piece : movedunits) {
            if (piece instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack)piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece currentPiece = pi.nextPiece();

                    if (canActivate(currentPiece)) {
                        clear = clearMovingCounters(clear);
                        movingFriendlyCounters.add(currentPiece);
                    } else if (canBeActivated(currentPiece)) {
                        clear = clearMovingCounters(clear);
                        movingSuspectCounters.add(currentPiece);
                    }
                }
            } else if (canActivate(piece)) {
                clear = clearMovingCounters(clear);
                movingFriendlyCounters.add(piece);
            } else if (canBeActivated(piece)) {
                clear = clearMovingCounters(clear);
                movingSuspectCounters.add(piece);
            }
        }

        generateFlareList();
    }

    private void generateFlareList() {
        pieceListClear();

        if (!movingFriendlyCounters.isEmpty() || !movingSuspectCounters.isEmpty()) { // if (movingFriendlyCounter != null || movingSuspectCounter != null) {
            GamePiece[] allPieces = mainMap.getPieces();

            for (GamePiece piece : allPieces) {
                if (piece instanceof Stack) {
                    for (PieceIterator pi = new PieceIterator(((Stack)piece).getPiecesIterator()); pi.hasMoreElements(); ) {
                        testActivation(pi.nextPiece());
                    }
                } else {
                    testActivation(piece);
                }
            }
        }
    }

    private boolean isSaslExtensionPresent() {
        boolean containsExtension = false;

        for (Buildable checkForSaslExtension : getGameModule().getBuildables()) {
            if (checkForSaslExtension instanceof Map && ((Map)checkForSaslExtension).getMapName().equals("SASL Campaign Roster")) {
                saslMap = (Map)checkForSaslExtension;
                containsExtension = true;
                break;
            }
        }

        return containsExtension;
    }

    private boolean isSacExtensionPresent() {
        boolean containsExtension = false;

        for (Buildable checkForSacExtension : getGameModule().getBuildables()) {
            if (checkForSacExtension instanceof Map && ((Map)checkForSacExtension).getMapName().equals("Scenario Aid Card")) {
                sacMap = (Map)checkForSacExtension;
                containsExtension = true;
                break;
            }
        }

        return containsExtension;
    }

    private boolean canActivate(GamePiece piece) {
        if (!isOnboard(piece) || !isFriendlyUnit(piece)) {
            return false;
        }

        if (piece.getName().contains("?")) { // let ? stacks trigger activation question
            return true;
        }

        if (!VASLGameInterface.isUnitCounter(piece)) { // non units can't trigger
            return false;
        }

        return !piece.getName().contains("broken") && !piece.getName().contains("Berserk") && !piece.getName().contains("Prisoner"); // Must be Good Order
    }

    private boolean canBeActivated(GamePiece piece) {
        if (!isOnboard(piece) || isFriendlyUnit(piece)) {
            return false;
        }

        return piece.getName().contains("Suspect");
    }

    CounterType getCounterType(GamePiece piece) {
        CounterType result = CounterType.NON_VEHICLE;

        if (isTrackedVehicle(piece)) {
            result = CounterType.TRACKED_VEHICLE;
        } else if (isVehicle(piece)) {
            result = CounterType.VEHICLE;
        }

        return result;
    }

    private void testActivation(GamePiece piece) {
        int range = -1;

        if (isOnboard(piece)) {
            if (!movingFriendlyCounters.isEmpty()) {
                for (GamePiece testPiece : movingFriendlyCounters) {
                    if (!(testPiece == piece) && !isFriendlyUnit(piece) && piece.getName().contains("Suspect")) {
                        CounterType counterType = getCounterType(testPiece);

                        if ((range = losRange(testPiece, piece, counterType)) >= 0) {
                            setPieceSpotted(piece, range, true, counterType);
                        }
                    }
                }
            } else if (!movingSuspectCounters.isEmpty()) {
                for (GamePiece testPiece : movingSuspectCounters) {
                    if (!(testPiece == piece) && isFriendlyUnit(piece) && !piece.getName().contains("Suspect")) {
                        if ((range = losRange(testPiece, piece, CounterType.NON_VEHICLE)) >= 0) {
                            setPieceSpotted(testPiece, range, false, CounterType.NON_VEHICLE);
                        }
                    }
                }
            }
        }
    }

    /**
     * Marks a piece as spotted so it will be drawn on the mainMap
     * @param piece the piece
     */
    private void setPieceSpotted(GamePiece piece, int range, boolean friendly, CounterType counterType) {
        if (!Decorator.getInnermost(piece).getName().isEmpty()) {
            if (piece instanceof Decorator || piece instanceof BasicPiece) {
                if (!pieceList.contains(piece)) {
                    pieceList.add(piece);

                    if (friendly) {
                        int rangeBracket = 0;

                        switch (counterType) {
                            case VEHICLE:
                            case TRACKED_VEHICLE:
                                rangeBracket = activationRangesVehicles[getActivationRangesIndex(getSuspectNationality(piece))][range];
                                // TODO: not needed? rangeBracket = activationRangesTrackedVehicles[getActivationRangesIndex(getSuspectNationality(piece))][range];
                                break;

                            default:
                                rangeBracket = activationRangesInfantry[getActivationRangesIndex(getSuspectNationality(piece))][range];
                                break;
                        }

                        if (rangeBracket != 0) {
                            piece.setProperty("ActivationFlag", 2);
                            piece.setProperty("RangeBracket", rangeBracket);
                        }
                    } else {
                        piece.setProperty("ActivationFlag", 2);
                    }
                }
            }
        }
    }

    private String getSuspectNationality(GamePiece piece) {
        String nationality = "None";
        Object prop = piece.getProperty("SuspectNationality");
        if (prop != null) {
            nationality = prop.toString();
        }

        if (nationality.equals("None") && !enemyNationality.isEmpty()) {
            nationality = enemyNationality;
        }

        return nationality;
    }


    private int getActivationRangesIndex(String nationality) {
        int result = 0;

        int length = nationalities.length;
        int idx = 0;

        while (idx < length) {
            if (nationalities[idx].equals(nationality)) {
                result = idx;
                break;
            }

            idx++;
        }

        return result;
    }

    private int range(GamePiece piece1, GamePiece piece2) {
        int range = -1;

        if (piece1 != null && piece2 != null) {
            Location l1 = VASLGameInterface.getLocation(piece1);
            Location l2 = VASLGameInterface.getLocation(piece2);

            if (l1 != null && l2 != null) {
                // check the range
                LOSResult losResult = new LOSResult();
                mainMap.getVASLMap().LOS(l1, false, l2, false, losResult, VASLGameInterface);

                range = losResult.getRange();
            }
        }

        return range;
    }

    private int getBlazeIlluminationRange(GamePiece blaze) {
        int range = 0;

        // TODO: should be a way?!? ASLAreaOfEffect blazeAoE = (ASLAreaOfEffect)blaze.getProperty("ASLAreaOfEffect");

        // TODO: should be a way?!? if (blazeAoE != null) {
        // TODO: should be a way?!?     range = blazeAoE.getRadius();
        // TODO: should be a way?!? }
        switch (blaze.getName()) {
            case "Blaze":
            case "1-level Blaze":
                range = 2;
                break;

            case "2-level Blaze":
                range = 4;
                break;

            case "3-level Blaze":
                range = 6;
                break;

            case "4-level Blaze":
                range = 8;
                break;

            default:
                break;
        }

        return range;
    }

    private boolean isOnboard(GamePiece piece) {
        return (VASLGameInterface.getLocation(piece) != null);
    }

    private boolean illuminates(GamePiece target, GamePiece possibleIlluminator) {
        boolean result = false;

        if (isOnboard(possibleIlluminator)) {
            if (possibleIlluminator.getName().contains("Starshell") && (range(target, possibleIlluminator) <= 3)) {
                result = true;
            } else if (possibleIlluminator.getName().contains("IR") && (range(target, possibleIlluminator) <= 6)) {
                result = true;
            } else if (possibleIlluminator.getName().contains("Blaze") && (range(target, possibleIlluminator) <= getBlazeIlluminationRange(possibleIlluminator))) {
                result = true;
            }
        }

        return result;
    }

    private boolean illuminated(GamePiece piece) {
        boolean result = false;
        GamePiece[] allPieces = mainMap.getPieces();

        outerloop:
        for (GamePiece tempPiece : allPieces) {
            if (tempPiece instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack)tempPiece).getPiecesIterator()); pi.hasMoreElements(); ) {
                    if (true == (result = illuminates(piece, pi.nextPiece()))) {
                        break outerloop;
                    }
                }
            } else {
                if (true == (result = illuminates(piece, tempPiece))) {
                    break outerloop;
                }
            }
        }

        return result;
    }

    boolean isTrackedVehicle(GamePiece piece) {
        boolean result = false;
        Object prop = piece.getProperty("vehicleType");

        if (prop != null) {
            switch (prop.toString()) {
                case "ft":      // fully-tracked
                case "ft_a":    // fully-tracked (amphibious)
                case "ht":      // half-tracked
                    result = true;
                    break;

                default:
                    break;
            }
        }

        return result;
    }

    boolean isVehicle(GamePiece piece) {
        boolean result = false;
        Object prop = piece.getProperty("vehicleType");

        if (prop != null) {
            switch (prop.toString()) {
                case "ac":      // armored car
                case "hd":      // horse-drawn
                case "mc":      // motorcycle
                case "sk":      // skis
                case "tr":      // truck
                case "tr_a":    // truck (amphibious)
                    result = true;
                    break;

                default:
                    GameModule.getGameModule().getChatter().send("gemhack: WARNING! isVehicle('" + piece.getName() +"') got " + prop.toString());
                    break;
            }
        }

        return result;
    }
    /**
     * Can piece1 see piece2?
     * Sub-classes could override this method to implement custom sighting rules
     * @param piece1 the piece being viewed
     * @param piece2 the piece doing the viewing
     * @return the range if piece1 can see piece2, -1 otherwise
     */
    public int losRange(GamePiece piece1, GamePiece piece2, CounterType counterType) {
        int range = -1;

        if (piece1 != null && piece2 != null) {
            Location l1 = VASLGameInterface.getLocation(piece1);
            Location l2 = VASLGameInterface.getLocation(piece2);

            if (l1 != null && l2 != null) {
                // check the LOS
                LOSResult losResult = new LOSResult();
                mainMap.getVASLMap().LOS(l1, false, l2, false, losResult, VASLGameInterface);

                if (!losResult.isBlocked() && (losResult.getRange() <= 16)) {
                    int myNvr = nvr;

                    range = losResult.getRange();

                    if (counterType != CounterType.NON_VEHICLE) {
                        double temp = (double)nvr;

                        if (counterType == CounterType.TRACKED_VEHICLE) {
                            temp = (myNvr == 0) ? 2.0 : (temp * 2.0);
                        } else if (counterType == CounterType.VEHICLE) {
                            temp = (myNvr == 0) ? 1.0 : (temp * 1.5);
                        }

                        myNvr = (int) Math.round(temp);
                    }

                    if (myNvr >= 0) {
                        if (illuminated(piece2)) {
                            if (!illuminated(piece1)) {
                                range = -1;
                            }
                        } else if (range > myNvr) {
                            if (!illuminated(piece1)) {
                                range = -1;
                            }
                        }
                    }
                }
            }
        }

        return range;
    }

    private boolean isConcealmentTerrain(Terrain checkterrain) {
        return checkterrain.isBuildingTerrain() || checkterrain.getName().equals("Woods") ||
                checkterrain.getName().equals("Forest") || checkterrain.getName().equals("PineWoods") ||
                checkterrain.getName().equals("Brush") || checkterrain.getName().equals("Light Woods") || checkterrain.getName().equals("Bamboo") ||
                checkterrain.getName().equals("Vineyard") || checkterrain.getName().equals("PFZ Vineyard") ||
                checkterrain.getName().equals("Orchard") || checkterrain.getName().equals("Palm Trees") || checkterrain.getName().equals("Rice Paddy, In Season") ||
                checkterrain.getName().equals("Cactus Patch") || checkterrain.getName().equals("Broken Ground") ||
                checkterrain.getName().equals("Olive Grove") || checkterrain.getName().equals("Orchard, Out of Season") ||
                checkterrain.getName().equals("Grain") || checkterrain.getName().equals("Kunai") ||
                checkterrain.getName().equals("Marsh") || checkterrain.getName().equals("Palm Debris") ||
                checkterrain.getName().equals("Wooden Rubble") || checkterrain.getName().equals("Stone Rubble") ||
                checkterrain.getName().equals("Light Jungle") || checkterrain.getName().equals("Dense Jungle") ||
                checkterrain.getName().equals("Bocage") || checkterrain.getName().equals("Scrub") ||
                checkterrain.getName().equals("Swamp");
    }

    protected GamePiece getPiece(String revealId) {
        GamePiece[] allPieces = mainMap.getPieces();

        for (GamePiece p : allPieces) {
            if (p instanceof Stack) {
                for (PieceIterator pi = new PieceIterator(((Stack) p).getPiecesIterator()); pi.hasMoreElements(); ) {
                    GamePiece p2 = pi.nextPiece();

                    if (p2.getId().equals(revealId)) {
                        return p2;
                    }
                }
            } else if (p.getId().equals(revealId)) {
                return p;
            }
        }

        return null;
    }

    /**
     * Get all currently selected pieces
     * @return LinkedList of selected pieces
     */
    private ArrayList<GamePiece> getSelectedPieces() {
        ArrayList<GamePiece> temp = new ArrayList<GamePiece>();

        for (GamePiece piece : GameModule.getGameModule().getGameState().getAllPieces()) {
            if (isSelected(piece)) {
                temp.add(piece);
            }
        }

        return temp;
    }

    /**
     * @param p the piece
     * @return true if the piece is selected
     */
    private boolean isSelected(GamePiece p) {
        return Boolean.TRUE.equals(p.getProperty(Properties.SELECTED)) && p.getId() != null && !"".equals(p.getId());
    }

   @Override
   public Class<?>[] getAttributeTypes() {
       return new Class<?>[] {
               String.class,
               NamedKeyStroke.class,
               NamedKeyStroke.class
       };
   }

   @Override
   public String[] getAttributeNames() {
       return new String[] {
               NAME,
               CLEAR_FLARES_KEY,
               CHECK_ACTIVATIONS_KEY
       };
   }

   @Override
   public String[] getAttributeDescriptions() {
       return new String[] {
               "SASLActivationChecker ",
               "Clear Flares Key ",
               "Check Activations Key "
       };
   }

   @Override
   public String getAttributeValueString(String key) {
       if (NAME.equals(key)) {
           return getConfigureName();
       } else if (CLEAR_FLARES_KEY.equals(key)) {
           return NamedHotKeyConfigurer.encode(clearFlaresKey);
       } else if (CHECK_ACTIVATIONS_KEY.equals(key)) {
           return NamedHotKeyConfigurer.encode(checkActivationsKey);
       } else {
           return null;
       }
   }

   @Override
   public void setAttribute(String key, Object value) {
       if (NAME.equals(key)) {
           if (value instanceof String) {
               name = (String)value;
           }
       } else if (CLEAR_FLARES_KEY.equals(key)) {
           if (value instanceof String) {
               value = NamedHotKeyConfigurer.decode((String)value);
           }
           clearFlaresKey = (NamedKeyStroke)value;
       } else if (CHECK_ACTIVATIONS_KEY.equals(key)) {
           if (value instanceof String) {
               value = NamedHotKeyConfigurer.decode((String)value);
           }
           checkActivationsKey = (NamedKeyStroke)value;
       }
   }

   public void addTo(Buildable parent) {
       // add this component to the game and register a mouse listener
       if (parent instanceof ASLMap) {
           mainMap = (ASLMap)parent;
           mainMap.addDrawComponent(this);
           mainMap.getView().addKeyListener(this);
       }

       // add this component to the mainMap toolbar  ToDo IS THIS NEEDED?
       if (parent instanceof Map) {
           assert parent instanceof ASLMap;
           setMap((ASLMap) parent);
       }

       getGameModule().getGameState().addGameComponent(this);
   }

   public void removeFrom(Buildable parent) {
   }

   public HelpFile getHelpFile() {
        return null;
    }

    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }

    public void runUpdate(java.util.List<GamePiece> allDraggedPieces) {
        if (mainMap.getVASLMap() == null){return;} // error handling; SASLActivationChecker only works on LOS enabled maps
        ArrayList movedUnits = (ArrayList)allDraggedPieces;
        updateView(movedUnits);
    }

    public void setup(boolean gameStarting) {
    }

    /**
     * Saves the player list and HIPFortification state
     */
    public Command getRestoreCommand() {
        return new NullCommand();
    }

    @Override
    public void draw(Graphics graphics, Map map) {
    }

    @Override
    public boolean drawAboveCounters() {
        return true;
    }

    // KeyListener (begin)

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (clearFlaresKey.equals(NamedKeyStroke.of(e))) {
            movingFriendlyCounters.clear();
            movingSuspectCounters.clear();

            pieceListClear();

            e.consume();
        } else if (checkActivationsKey.equals(NamedKeyStroke.of(e))) {
            pieceListClear();

            ArrayList<GamePiece> selectedPieces = getSelectedPieces();

            if (!selectedPieces.isEmpty()) {
                updateView(selectedPieces);
            }

            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    // KeyListener (end)

    /**
     * Configurer for the icon image
     */
    public static class IconConfig implements ConfigurerFactory {
        public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
            return new IconConfigurer(key, name, "");
        }
    }

    /**
     * Configurer for the chatter report formatter
     */
    public static class ReportFormatConfig implements TranslatableConfigurerFactory {
        public Configurer getConfigurer(AutoConfigurable c, String key, String name) {
            return new PlayerIdFormattedStringConfigurer(key, name, new String[] {
                    ASLProperties.LOCATION,
                    Properties.MOVED});
        }
    }
}
