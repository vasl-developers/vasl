package VASL.build.module;

import VASL.environment.Environment;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GlobalOptions;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;

class UniqueRollSequence
{
    public int sequenceNumber;
    public float sequenceOrder;
}

class InstanceComparator implements Comparator<UniqueRollSequence>
{
    @Override
    public int compare(UniqueRollSequence shot1, UniqueRollSequence shot2)
    {
        Float f1 = shot1.sequenceOrder;
        Float f2 = shot2.sequenceOrder;

        return f1.compareTo(f2);
    }
}

class CategoryDiceStats
{
    private final String categoryName;
    private int totalNumberOfRolls;
    private int totalSumOfRolls;
    private int totalSumOfColoredDieRolls;
    private int totalSumOfWhiteDieRolls;
    private final boolean isTwoDiceCategory;
    private final int[] detailedOneDieResults;
    private final int[] detailedTwoDiceResults;

    /**
     * @return the category
     */
    public String getCategoryName() {return categoryName;}

    /**
     * @return the totalNumberOfRolls
     */
    public int getTotalNumberOfRolls() { return totalNumberOfRolls; }

    /**
     * @return the totalSumOfRolls
     */
    public int getTotalSumOfRolls() { return totalSumOfRolls; }

    /**
     * @return the totalSumOfColoredDieRolls
     */
    public int getTotalSumOfColoredDieRolls() { return totalSumOfColoredDieRolls; }

    /**
     * @return the totalSumOfWhiteDieRolls
     */
    public int getTotalSumOfWhiteDieRolls() { return totalSumOfWhiteDieRolls; }

    /**
     * @return the isTwoDiceCategory
     */
    public boolean isTwoDiceCategory() { return isTwoDiceCategory; }

    /**
     * @return the detailedOneDieResults
     */
    public int getDetailedOneDieResults(int dieResult)
    {
        if ((dieResult > 0) && (dieResult <= detailedOneDieResults.length))
            return detailedOneDieResults[dieResult - 1];

        return 0;
    }

    /**
     * @return the detailedTwoDiceResults
     */
    public int getDetailedTwoDiceResults(int diceResult)
    {
        if (((diceResult - 2) >= 0) && ((diceResult - 2) < detailedTwoDiceResults.length))
            return detailedTwoDiceResults[diceResult - 2];

        return 0;
    }


    public CategoryDiceStats(String categ, boolean twoDiceCateg)
    {
        categoryName = categ;
        totalNumberOfRolls = 0;
        totalSumOfRolls = 0;
        totalSumOfColoredDieRolls = 0;
        totalSumOfWhiteDieRolls = 0;
        isTwoDiceCategory = twoDiceCateg;
        detailedOneDieResults = new int[6];
        detailedTwoDiceResults = new int[11];

        Arrays.fill(detailedOneDieResults, 0);
        Arrays.fill(detailedTwoDiceResults, 0);
    }

    void Add_DR(int coloredDieResult, int whiteDieResult)
    {
        if (isTwoDiceCategory)
        {
            if ((coloredDieResult > 0) && (coloredDieResult <= 6)
                && (whiteDieResult > 0) && (whiteDieResult <= 6)) {

                totalNumberOfRolls++;
                totalSumOfRolls += coloredDieResult + whiteDieResult;
                totalSumOfColoredDieRolls += coloredDieResult;
                totalSumOfWhiteDieRolls += whiteDieResult;

                detailedTwoDiceResults[coloredDieResult + whiteDieResult - 2]++;
            }
        }
    }

    void Add_dr(int singleDieResult)
    {
        if (!isTwoDiceCategory)
        {
            if ((singleDieResult > 0) && (singleDieResult <= 6))
            {
                totalNumberOfRolls++;
                totalSumOfRolls += singleDieResult;

                detailedOneDieResults[singleDieResult - 1]++;
            }
        }
    }

    public String GetTextualAverage()
    {
        NumberFormat numberFormat = NumberFormat.getInstance();

        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);

        if (getTotalNumberOfRolls() == 0)
            return "-";

        return numberFormat.format((double) getTotalSumOfRolls() / (double) getTotalNumberOfRolls());
    }

    public double GetNumericAverage()
    {
        if (getTotalNumberOfRolls() == 0)
            return 0;

        return (double) getTotalSumOfRolls() / (double) getTotalNumberOfRolls();
    }

    public String GetColoredDieAverage()
    {
        NumberFormat numberFormat = NumberFormat.getInstance();

        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);

        if (getTotalNumberOfRolls() == 0)
            return "-";

        return numberFormat.format((double) getTotalSumOfColoredDieRolls() / (double) getTotalNumberOfRolls());
    }

    public String GetWhiteDieAverage()
    {
        NumberFormat numberFormat = NumberFormat.getInstance();

        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);

        if (getTotalNumberOfRolls() == 0)
            return "-";

        return numberFormat.format((double) getTotalSumOfWhiteDieRolls() / (double) getTotalNumberOfRolls());
    }
}

class DiceStats extends HashMap<String, CategoryDiceStats>
{
    public void Add_DR(String categName, int coloredDieResult, int whiteDieResult)
    {
        CategoryDiceStats categoryDiceStats = get("D2_"+ categName);

        if (categoryDiceStats == null)
        {
            categoryDiceStats = new CategoryDiceStats(categName, true);
            put("D2_"+ categName, categoryDiceStats);
        }

        categoryDiceStats.Add_DR(coloredDieResult, whiteDieResult);
    }

    public void Add_dr(String categName, int singleDieResult)
    {
        CategoryDiceStats categoryDiceStats = get("D1_"+ categName);

        if (categoryDiceStats == null)
        {
            categoryDiceStats = new CategoryDiceStats(categName, false);
            put("D1_"+ categName, categoryDiceStats);
        }

        categoryDiceStats.Add_dr(singleDieResult);
    }

    public String GetNumRolledDROnTotal(String categName, int diceResult)
    {
        CategoryDiceStats categoryDiceStats = get("D2_"+ categName);

        if (categoryDiceStats == null)
            return "-/-";

        return categoryDiceStats.getDetailedTwoDiceResults(diceResult) + " / " + categoryDiceStats.getTotalNumberOfRolls();
    }

    public String GetNumRolleddrOnTotal(String categName, int dieResult)
    {
        CategoryDiceStats categoryDiceStats = get("D1_"+ categName);

        if (categoryDiceStats == null)
            return "-/-";

        return categoryDiceStats.getDetailedOneDieResults(dieResult) + " / " + categoryDiceStats.getTotalNumberOfRolls();
    }

    public String GetAverageDR(String categName)
    {
        CategoryDiceStats categoryDiceStats = get("D2_"+ categName);

        if (categoryDiceStats == null)
            return "-";

        return categoryDiceStats.GetTextualAverage();
    }

    public String GetAveragedr(String categName)
    {
        CategoryDiceStats categoryDiceStats = get("D1_"+ categName);

        if (categoryDiceStats == null)
            return "-";

        return categoryDiceStats.GetTextualAverage();
    }
}

public class ASLDiceBot extends AbstractBuildable
{
    private static final String RANDOM_ORG_OPTION = "randomorgoption"; //$NON-NLS-1$
    private static final String SHOW_EXTRA_DICE_STATS = "showExtraDiceStats"; //$NON-NLS-1$
    private static final String SHOW_ROF_DIE = "showRofDie";
    private static final String TOTAL_CATEGORY_NAME = "Total";
    public static final String OTHER_CATEGORY = "Other";
    public static final String NAME = "name"; //$NON-NLS-1$

    public static final String HTMLSingle =
            "<table class=\"tbl\"><thead><tr><th colspan=\"3\">%s</th></tr><tr><th>Category</th><th>drs</th><th>Avg</th></tr></thead><tbody>%s</tbody></table></html>";
    public static final String HTMLDouble =
            "<table class=\"tbl\"><thead><tr><th colspan=\"5\">%s</th></tr><tr><th>Category</th><th>DRs</th><th>Avg 1st</th><th>Avg 2nd</th><th>Avg</th></tr></thead><tbody>%s</tbody></table></html>";

    public static final String HTMLSingle_Extra =
            "<table class=\"tbl\"><thead><tr><th colspan=\"9\">%s</th></tr><tr><th>Category</th><th>drs</th><th>Avg</th><th>1</th><th>2</th><th>3</th><th>4</th><th>5</th><th>6</th></tr></thead><tbody>%s</tbody></table></html>";
    public static final String HTMLDouble_Extra =
            "<table class=\"tbl\"><thead><tr><th colspan=\"16\">%s</th></tr><tr><th>Category</th><th>DRs</th><th>Avg 1st</th><th>Avg 2nd</th><th>Avg</th><th>2</th><th>3</th><th>4</th><th>5</th><th>6</th><th>7</th><th>8</th><th>9</th><th>10</th><th>11</th><th>12</th></tr></thead><tbody>%s</tbody></table></html>";

    private final int MAX_SEQUENCE_LENGTH = 99;
    private int currentSeries = 0, numbersInCurrentSeries = MAX_SEQUENCE_LENGTH;

    private static final int MAX_RANDOM_NUMBERS = 1000;
    private final UniqueRollSequence[] uniqueRollSequence = new UniqueRollSequence[MAX_SEQUENCE_LENGTH];
    private final int[] randomNumbers = new int[MAX_RANDOM_NUMBERS];
    private final ArrayList<Integer> unusedElementList = new ArrayList<>();
    private boolean usingRandomOrg = false;
    private boolean showExtraDiceStats = false;
    private boolean showROFDie = false;
    private final DiceStats diceStats = new DiceStats();
    private final Environment environment = new Environment();

    public ASLDiceBot() {
        Arrays.setAll(uniqueRollSequence, i -> new UniqueRollSequence());
    }

    public void setAttribute(String key, Object value) { }
    public String[] getAttributeNames() {
        return new String[0];
    }
    public String[] getAttributeDescriptions() {
        return new String[0];
    }
    public String getAttributeValueString(String key) {
        return "";
    }
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[0];
    }

    public void addTo(Buildable parent) {
        final Prefs modulePrefs = ((GameModule) parent).getPrefs();
        BooleanConfigurer randomOrgOption;
        BooleanConfigurer existingRandomOrgOption = (BooleanConfigurer)modulePrefs.getOption(RANDOM_ORG_OPTION);

        if (existingRandomOrgOption == null)
        {
            randomOrgOption = new BooleanConfigurer(RANDOM_ORG_OPTION, "Get DR/dr random numbers from random.org", Boolean.FALSE);  //$NON-NLS-1$
            //modulePrefs.addOption(Resources.getString("Prefs.general_tab"), randomOrgOption); //$NON-NLS-1$
            modulePrefs.addOption("VASL", randomOrgOption);
        }
        else
            randomOrgOption = existingRandomOrgOption;

        usingRandomOrg = (Boolean) (modulePrefs.getValue(RANDOM_ORG_OPTION));

        randomOrgOption.addPropertyChangeListener(e -> {
            usingRandomOrg = (Boolean) e.getNewValue();
            ReadRolls();
        });

        BooleanConfigurer showExtraDiceStatsOption;
        BooleanConfigurer existingShowExtraDiceStatsOption = (BooleanConfigurer)modulePrefs.getOption(SHOW_EXTRA_DICE_STATS);

        if (existingShowExtraDiceStatsOption == null)
        {
            showExtraDiceStatsOption = new BooleanConfigurer(SHOW_EXTRA_DICE_STATS, "Show detailed dice stats", Boolean.FALSE);  //$NON-NLS-1$
            //modulePrefs.addOption(Resources.getString("Chatter.chat_window"), showExtraDiceStatsOption); //$NON-NLS-1$
            modulePrefs.addOption("VASL", showExtraDiceStatsOption);
        }
        else
            showExtraDiceStatsOption = existingShowExtraDiceStatsOption;

        showExtraDiceStats = (Boolean) (modulePrefs.getValue(SHOW_EXTRA_DICE_STATS));

        showExtraDiceStatsOption.addPropertyChangeListener(e -> showExtraDiceStats = (Boolean) e.getNewValue());

        // ROF die pref
        BooleanConfigurer showROFdieOption = (BooleanConfigurer) modulePrefs.getOption(SHOW_ROF_DIE);
        if (showROFdieOption == null) {
            showROFdieOption = new BooleanConfigurer(SHOW_ROF_DIE, "Show a ROF die as part of each IFT/TH dice roll", Boolean.FALSE);  //$NON-NLS-1$
            modulePrefs.addOption(Resources.getString("Chatter.chat_window"), showROFdieOption); //$NON-NLS-1$
        }
        showROFDie = (Boolean) modulePrefs.getValue((SHOW_ROF_DIE));
        showROFdieOption.addPropertyChangeListener(e -> showROFDie = (Boolean) e.getNewValue());

    }

    private ScenInfo GetScenarioInfo()
    {
        return GameModule.getGameModule().getComponentsOf(ScenInfo.class).iterator().next();
    }

    private Random GetRandomizer() {
        return GameModule.getGameModule().getRNG();
    }

    private BufferedReader GetRemoteDataReader() throws Exception
    {
        URL url = new URL("https://www.random.org/integers/?num=" + MAX_RANDOM_NUMBERS + "&min=1&max=6&col=1&base=10&format=plain&rnd=new");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();

        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        return new BufferedReader(new InputStreamReader(connection.getInputStream()));
    }

    private void ReadRolls()
    {
        unusedElementList.clear();

        /* if random.org is used then reads data from web */
        if (usingRandomOrg)
        {
            int index = 0;

            OutputString(" ");
            OutputString(" Reading data from random.org");

            try
            {
                BufferedReader reader = GetRemoteDataReader();
                String line = reader.readLine();

                while (line != null)
                {
                    randomNumbers[index] = Integer.parseInt(line);
                    unusedElementList.add(index);
                    index++;

                    line = reader.readLine();
                }

                reader.close();

                if (index != MAX_RANDOM_NUMBERS) {
                    OutputString(" Random.org not reachable or read failed");
                    unusedElementList.clear();
                }
                else
                    OutputString(" Data correctly retrieved");

            }
            catch (Exception e)
            {
                OutputString(" Random.org not reachable or read failed: error= " + e.getMessage());
            }

            OutputString(" ");
        }
        else // generate data with standard dicebot
        {
            Random randomizer = GetRandomizer();

            for (int i = 0; i < MAX_RANDOM_NUMBERS; i++)
            {
                randomNumbers[i] = (int)((randomizer.nextFloat() * 6) + 1);
                randomNumbers[i] = randomizer.nextInt(6) + 1;
                unusedElementList.add(i);
            }

            OutputString(" ");
            OutputString(" Data correctly generated");
        }
    }

    private String GetPlayer() {
        return GlobalOptions.getInstance().getPlayerId();
    }

    private String IsSpecialDiceRoll(int diceRollResult)
    {
        ScenInfo scenarioInfo = GetScenarioInfo();

        boolean axisSan = diceRollResult == scenarioInfo.getAxisSAN();
        boolean alliedSan = diceRollResult == scenarioInfo.getAlliedSAN();

        if (axisSan && alliedSan) {
            return "Axis/Allied SAN    ";
        }
        else {
            if (axisSan)
                return "Axis SAN    ";
            else if (alliedSan)
                return "Allied SAN    ";
        }

        if ((diceRollResult >= 11) && ((!"".equals(scenarioInfo.getAxisBooby())) || (!"".equals(scenarioInfo.getAlliedBooby())))) {

            boolean axisBoobyTrap = ("C".equals(scenarioInfo.getAxisBooby()) && diceRollResult == 12 ||
                                     "B".equals(scenarioInfo.getAxisBooby()) && diceRollResult == 11 ||
                                     "A".equals(scenarioInfo.getAxisBooby()));

            boolean alliedBoobyTrap = ("C".equals(scenarioInfo.getAlliedBooby()) && diceRollResult == 12 ||
                                       "B".equals(scenarioInfo.getAlliedBooby()) && diceRollResult == 11 ||
                                       "A".equals(scenarioInfo.getAlliedBooby()));

            if (axisBoobyTrap && alliedBoobyTrap) {
                    return "Axis/Allied Booby Trap    ";
            }
            else {
                if (axisBoobyTrap) {
                    return "Axis Booby Trap   ";
                }
                else if (alliedBoobyTrap) {
                    return "Allied Booby Trap   ";
                }
            }
        }

        return "";
    }

    private void OutputString(String strMsg) {
        GameModule.getGameModule().getChatter().send(strMsg);
    }

    private int GetDieRoll()
    {
        Random randomizer = GetRandomizer();

        if (unusedElementList.isEmpty())
            ReadRolls();

        if (!unusedElementList.isEmpty())
        {
            // take a random unused elementIndex
            int elementIndex = (int) (randomizer.nextFloat() * unusedElementList.size());

            // pick up a random unused element, remove it from the list and return it
            return randomNumbers[unusedElementList.remove(elementIndex)];
        }

        return 0;
    }

    public void DR(String categName) {
        //final Prefs modulePrefs = GameModule.getGameModule().getPrefs();
        //if ((Boolean) modulePrefs.getValue(SHOW_ROF_DIE) == null) {
        //    showROFDie = false;
        //} else {
        //    showROFDie = (Boolean) modulePrefs.getValue((SHOW_ROF_DIE));
        //}

        int whiteDieResult = GetDieRoll();
        int coloredDieResult = GetDieRoll();
        int rofDieResult =  showROFDie ? GetDieRoll() : 0;
        int dustDieResult = environment.dustInEffect() ? GetDieRoll() : 0;

        if ((coloredDieResult != 0) && (whiteDieResult != 0))
        {
            String output;

            diceStats.Add_DR(TOTAL_CATEGORY_NAME, coloredDieResult, whiteDieResult);
            diceStats.Add_DR(categName, coloredDieResult, whiteDieResult);

            if( (environment.dustInEffect() && dustDieResult != 0) &&
               (categName.equals(("TH")) || categName.equals(("IFT")) || categName.equals(("MC"))))
            {
                output = String.format("*** (%s DR) %s,%s,%s *** - total with %s: %s     <%s>      %s[%s   avg   %s (%s)]    (%s%s)",
                        categName,
                        coloredDieResult,
                        whiteDieResult,
                        dustDieResult,
                        environment.getCurrentDustLevel().toString(),
                        environment.isLightDust()   ?
                                Integer.toString(coloredDieResult + whiteDieResult + (dustDieResult / 2) ) :
                                Integer.toString(coloredDieResult + whiteDieResult + ((dustDieResult / 2) + (dustDieResult % 2) ) ),
                        GetPlayer(),
                        IsSpecialDiceRoll(coloredDieResult + whiteDieResult),
                        diceStats.GetNumRolledDROnTotal(TOTAL_CATEGORY_NAME, coloredDieResult + whiteDieResult),
                        diceStats.GetAverageDR(categName),
                        diceStats.GetAverageDR(TOTAL_CATEGORY_NAME),
                        getSerieInstanceNumber(),
                        (usingRandomOrg ? " - by random.org" : "")
                );
            } else {
                output = String.format("*** (%s DR) %s,%s ***   <%s>      %s[%s   avg   %s (%s)]    (%s%s)",
                        categName,
                        coloredDieResult,
                        whiteDieResult,
                        GetPlayer(),
                        IsSpecialDiceRoll(coloredDieResult + whiteDieResult),
                        diceStats.GetNumRolledDROnTotal(TOTAL_CATEGORY_NAME, coloredDieResult + whiteDieResult),
                        diceStats.GetAverageDR(categName),
                        diceStats.GetAverageDR(TOTAL_CATEGORY_NAME),
                        getSerieInstanceNumber(),
                        (usingRandomOrg ? " - by random.org" : "")
                );
            }

            // showing special ROF Die
            if (showROFDie && ("TH".equals(categName) || "IFT".equals(categName))){
                output +=  " ROF die: " + rofDieResult;
            }

            OutputString(output);
        }
    }

    public void dr(String category)
    {
        int singleDieResult = GetDieRoll();

        if (singleDieResult != 0)
        {
            diceStats.Add_dr(TOTAL_CATEGORY_NAME, singleDieResult);
            diceStats.Add_dr(category, singleDieResult);

            String output = String.format("*** (%s dr) %s ***   <%s>      [%s   avg   %s (%s)]    (%s%s)",
                                        category,
                                        singleDieResult,
                                        GetPlayer(),
                                        diceStats.GetNumRolleddrOnTotal(TOTAL_CATEGORY_NAME, singleDieResult),
                                        diceStats.GetAveragedr(category),
                                        diceStats.GetAveragedr(TOTAL_CATEGORY_NAME),
                                        getSerieInstanceNumber(),
                                        (usingRandomOrg ? " - by random.org" : "")
                                        );

            OutputString(output);
        }
    }

    String getSerieInstanceNumber()
    {
        Random randomizer = GetRandomizer();
        NumberFormat numberFormat = NumberFormat.getInstance();

        if (numbersInCurrentSeries >= MAX_SEQUENCE_LENGTH) {
            currentSeries++;
            numbersInCurrentSeries = 0;

            for (int i = 0; i < MAX_SEQUENCE_LENGTH; i++) {
                uniqueRollSequence[i].sequenceNumber = i + 1;
                uniqueRollSequence[i].sequenceOrder = randomizer.nextFloat();
            }

            Arrays.sort(uniqueRollSequence, new InstanceComparator());
        }

        numberFormat.setMinimumIntegerDigits(2);

        return numberFormat.format(currentSeries) + "." + numberFormat.format(uniqueRollSequence[numbersInCurrentSeries++].sequenceNumber);
    }

    public void statsToday()
    {
        StringBuilder stringBuilder = new StringBuilder();
        List<CategoryDiceStats> stats = getCategoryDiceStats();

        for (CategoryDiceStats categoryStats : stats)
        {
            if (categoryStats.isTwoDiceCategory())
                continue;

            if (categoryStats.getCategoryName().compareToIgnoreCase(TOTAL_CATEGORY_NAME) == 0)
                continue;

            if (showExtraDiceStats) {
                stringBuilder.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                        categoryStats.getCategoryName(),
                        categoryStats.getTotalNumberOfRolls(),
                        categoryStats.GetTextualAverage(),
                        categoryStats.getDetailedOneDieResults(1),
                        categoryStats.getDetailedOneDieResults(2),
                        categoryStats.getDetailedOneDieResults(3),
                        categoryStats.getDetailedOneDieResults(4),
                        categoryStats.getDetailedOneDieResults(5),
                        categoryStats.getDetailedOneDieResults(6))
                );
            } else {
                stringBuilder.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td></tr>",
                        categoryStats.getCategoryName(),
                        categoryStats.getTotalNumberOfRolls(),
                        categoryStats.GetTextualAverage())
                );
            }
        }

        CategoryDiceStats categoryTotal = diceStats.get("D1_" + TOTAL_CATEGORY_NAME);

        if (categoryTotal != null)
        {
            if (showExtraDiceStats) {
                stringBuilder.append(String.format("<tr class=\"total\"><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td></tr>",
                        categoryTotal.getCategoryName(),
                        categoryTotal.getTotalNumberOfRolls(),
                        categoryTotal.GetTextualAverage(),
                        categoryTotal.getDetailedOneDieResults(1),
                        categoryTotal.getDetailedOneDieResults(2),
                        categoryTotal.getDetailedOneDieResults(3),
                        categoryTotal.getDetailedOneDieResults(4),
                        categoryTotal.getDetailedOneDieResults(5),
                        categoryTotal.getDetailedOneDieResults(6))
                );
            } else {
                stringBuilder.append(String.format("<tr class=\"total\"><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td></tr>",
                        categoryTotal.getCategoryName(),
                        categoryTotal.getTotalNumberOfRolls(),
                        categoryTotal.GetTextualAverage())
                );
            }
        }

        if (showExtraDiceStats) {
            OutputString(String.format("!!" + HTMLSingle_Extra, GetPlayer() + "'s drs today", stringBuilder));
        } else {
            OutputString(String.format("!!" + HTMLSingle, GetPlayer() + "'s drs today", stringBuilder));
        }

        stringBuilder.setLength(0);

        for (CategoryDiceStats categoryStats : stats)
        {
            if (!categoryStats.isTwoDiceCategory())
                continue;

            if (categoryStats.getCategoryName().compareToIgnoreCase(TOTAL_CATEGORY_NAME) == 0)
                continue;

            double average = categoryStats.GetNumericAverage();
            String bgColor = getBgColor(average);

            if (showExtraDiceStats) {
                stringBuilder.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td %s>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                        categoryStats.getCategoryName(),
                        categoryStats.getTotalNumberOfRolls(),
                        categoryStats.GetColoredDieAverage(),
                        categoryStats.GetWhiteDieAverage(),
                        bgColor,
                        categoryStats.GetTextualAverage(),
                        categoryStats.getDetailedTwoDiceResults(2),
                        categoryStats.getDetailedTwoDiceResults(3),
                        categoryStats.getDetailedTwoDiceResults(4),
                        categoryStats.getDetailedTwoDiceResults(5),
                        categoryStats.getDetailedTwoDiceResults(6),
                        categoryStats.getDetailedTwoDiceResults(7),
                        categoryStats.getDetailedTwoDiceResults(8),
                        categoryStats.getDetailedTwoDiceResults(9),
                        categoryStats.getDetailedTwoDiceResults(10),
                        categoryStats.getDetailedTwoDiceResults(11),
                        categoryStats.getDetailedTwoDiceResults(12))
                );
            } else {
                stringBuilder.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td %s>%s</td></tr>",
                        categoryStats.getCategoryName(),
                        categoryStats.getTotalNumberOfRolls(),
                        categoryStats.GetColoredDieAverage(),
                        categoryStats.GetWhiteDieAverage(),
                        bgColor,
                        categoryStats.GetTextualAverage())
                );
            }
        }

        CategoryDiceStats totalCategory = diceStats.get("D2_" + TOTAL_CATEGORY_NAME);

        if (totalCategory != null)
        {
            double average = totalCategory.GetNumericAverage();
            String bgColor = getBgColor(average);

            if (showExtraDiceStats) {
                stringBuilder.append(String.format("<tr class=\"total\"><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\" %s>%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td></tr>",
                        totalCategory.getCategoryName(),
                        totalCategory.getTotalNumberOfRolls(),
                        totalCategory.GetColoredDieAverage(),
                        totalCategory.GetWhiteDieAverage(),
                        bgColor,
                        totalCategory.GetTextualAverage(),
                        totalCategory.getDetailedTwoDiceResults(2),
                        totalCategory.getDetailedTwoDiceResults(3),
                        totalCategory.getDetailedTwoDiceResults(4),
                        totalCategory.getDetailedTwoDiceResults(5),
                        totalCategory.getDetailedTwoDiceResults(6),
                        totalCategory.getDetailedTwoDiceResults(7),
                        totalCategory.getDetailedTwoDiceResults(8),
                        totalCategory.getDetailedTwoDiceResults(9),
                        totalCategory.getDetailedTwoDiceResults(10),
                        totalCategory.getDetailedTwoDiceResults(11),
                        totalCategory.getDetailedTwoDiceResults(12))
                );
            } else {
                stringBuilder.append(String.format("<tr class=\"total\"><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\" %s>%s</td></tr>",
                        totalCategory.getCategoryName(),
                        totalCategory.getTotalNumberOfRolls(),
                        totalCategory.GetColoredDieAverage(),
                        totalCategory.GetWhiteDieAverage(),
                        bgColor,
                        totalCategory.GetTextualAverage())
                );
            }
        }

        if (showExtraDiceStats) {
            OutputString(String.format("!!" + HTMLDouble_Extra, GetPlayer() + "'s DRs today", stringBuilder));
        } else {
            OutputString(String.format("!!" + HTMLDouble,  GetPlayer() + "'s DRs today", stringBuilder));
        }
    }

    private String getBgColor(double average) {
        return average < 6.75 ? "bgcolor=#83C07A" : (average > 7.25 ? "bgcolor=#FE686D" : "");
    }

    private List<CategoryDiceStats> getCategoryDiceStats() {

        List<CategoryDiceStats> stats = new ArrayList<>(diceStats.values());

        stats.sort((o1, o2) -> {

            double avg1 = o1.GetNumericAverage();
            double avg2 = o2.GetNumericAverage();
            String[] categoryOrder = new String[]{"SA", "RS", "Rally", "IFT", "MC", "TC", "CC", "TH", "TK", "Other", "Total"};
            String cat1 = o1.getCategoryName();
            String cat2 = o2.getCategoryName();
            int catIndex1 = 0;
            int catIndex2 = 0;

            if (showExtraDiceStats) {
                for (int i = 0; i < 11; i++) {
                    if (cat1.equals(categoryOrder[i]))
                        catIndex1 = i;
                    if (cat2.equals(categoryOrder[i]))
                        catIndex2 = i;
                }
                return Integer.compare(catIndex1, catIndex2);
            } else {
                return Double.compare(avg1, avg2);
            }
        });
        return stats;
    }
}

