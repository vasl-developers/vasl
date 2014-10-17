package VASL.build.module;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import VASSAL.build.*;
import VASSAL.build.module.GlobalOptions;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.StringConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.tools.LaunchButton;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.HttpURLConnection;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

class InstanceNumber 
{
    public int m_iIndex;
    public float m_fOrder;
}

class InstanceComparator implements Comparator<InstanceNumber> 
{
    @Override
    public int compare(InstanceNumber shot1, InstanceNumber shot2) 
    {
        Float l_f1 = shot1.m_fOrder;
        Float l_f2 = shot2.m_fOrder;

        return l_f1.compareTo(l_f2);
    }
}

class CategoryDiceStats
{
    private String m_strCategory;
    private int m_iTotNumRolls;
    private int m_iTotSumRolls;
    private int m_iTotSumColoredRolls;
    private int m_iTotSumWhiteRolls;
    private boolean m_bTwoDice;
    private int[] mar_iDetailNumColoredRolls;
    private int[] mar_iDetailNumWhiteRolls;
    private int[] mar_iDetailNumTotalRolls;

    /**
     * @return the m_strCategory
     */
    public String getCategory() {return m_strCategory;}

    /**
     * @return the m_iTotNumRolls
     */
    public int getTotNumRolls() { return m_iTotNumRolls; }

    /**
     * @return the m_iTotSumRolls
     */
    public int getTotSumRolls() { return m_iTotSumRolls; }

    /**
     * @return the m_iTotSumColoredRolls
     */
    public int getTotSumColoredRolls() { return m_iTotSumColoredRolls; }

    /**
     * @return the m_iTotSumWhiteRolls
     */
    public int getTotSumWhiteRolls() { return m_iTotSumWhiteRolls; }

    /**
     * @return the m_bTwoDice
     */
    public boolean IsTwoDice() { return m_bTwoDice; }

    /**
     * @return the mar_iDetailNumColoredRolls
     */
    public int getDetailNumColoredRolls(int iNum) 
    { 
        if ((iNum > 0) && (iNum <= mar_iDetailNumColoredRolls.length)) 
            return mar_iDetailNumColoredRolls[iNum - 1]; 
        
        return 0;    
    }

    /**
     * @return the mar_iDetailNumWhiteRolls
     */
    public int getDetailNumWhiteRolls(int iNum) 
    { 
        if ((iNum > 0) && (iNum <= mar_iDetailNumWhiteRolls.length)) 
            return mar_iDetailNumWhiteRolls[iNum - 1]; 
        
        return 0;    
    }
    
    /**
     * @return the mar_iDetailNumTotalRolls
     */
    public int getDetailNumRolls(int iNum) 
    { 
        if (((iNum - 2) >= 0) && ((iNum - 2) < mar_iDetailNumTotalRolls.length)) 
            return mar_iDetailNumTotalRolls[iNum - 2]; 
        
        return 0;    
    }
    
    
    public CategoryDiceStats(String strCategory, boolean bTwoDice)
    {
        m_strCategory = strCategory;
        m_iTotNumRolls = 0;
        m_iTotSumRolls = 0;
        m_iTotSumColoredRolls = 0;
        m_iTotSumWhiteRolls = 0;
        m_bTwoDice = bTwoDice;
        mar_iDetailNumColoredRolls = new int[6];
        mar_iDetailNumWhiteRolls = new int[6];
        mar_iDetailNumTotalRolls = new int[11];
        
        for (int l_i = 0; l_i < mar_iDetailNumColoredRolls.length; l_i++)
            mar_iDetailNumColoredRolls[l_i] = 0;

        for (int l_i = 0; l_i < mar_iDetailNumWhiteRolls.length; l_i++)
            mar_iDetailNumWhiteRolls[l_i] = 0;
        
        for (int l_i = 0; l_i < mar_iDetailNumTotalRolls.length; l_i++)
            mar_iDetailNumTotalRolls[l_i] = 0;
    }

    void Add_DR(int iColoredDie, int iWhiteDie) 
    {
        if (m_bTwoDice)
        {
            if (
                (iColoredDie > 0) 
                && (iColoredDie <= mar_iDetailNumColoredRolls.length)
                && (iWhiteDie > 0) 
                && (iWhiteDie <= mar_iDetailNumWhiteRolls.length)
                ) 
            {
                m_iTotNumRolls += 1;
                m_iTotSumRolls += iColoredDie + iWhiteDie;
                m_iTotSumColoredRolls += iColoredDie;
                m_iTotSumWhiteRolls += iWhiteDie;

                mar_iDetailNumColoredRolls[iColoredDie - 1] += 1;
                mar_iDetailNumWhiteRolls[iWhiteDie - 1] += 1;
                mar_iDetailNumTotalRolls[iColoredDie + iWhiteDie - 2] += 1;
            }
        }
    }

    void Add_dr(int iSingleDie) 
    {
        if (!m_bTwoDice)
        {
            if (
                (iSingleDie > 0) 
                && (iSingleDie <= mar_iDetailNumColoredRolls.length)
                ) 
            {
                m_iTotNumRolls += 1;
                m_iTotSumRolls += iSingleDie;

                mar_iDetailNumColoredRolls[iSingleDie - 1] += 1;
            }
        }
    }
    
    public String GetAvg()
    {
        NumberFormat l_nf = NumberFormat.getInstance();

        l_nf.setMinimumFractionDigits(2);
        l_nf.setMaximumFractionDigits(2);
        
        if (getTotNumRolls() == 0)
            return "-";

        return l_nf.format((double) getTotSumRolls() / (double) getTotNumRolls());
    }
    
    public double GetNumericAvg()
    {
        if (getTotNumRolls() == 0)
            return 0;

        return (double) getTotSumRolls() / (double) getTotNumRolls();
    }
    
    public String GetAvgColored()
    {
        NumberFormat l_nf = NumberFormat.getInstance();

        l_nf.setMinimumFractionDigits(2);
        l_nf.setMaximumFractionDigits(2);
        
        if (getTotNumRolls() == 0)
            return "-";

        return l_nf.format((double) getTotSumColoredRolls() / (double) getTotNumRolls());
    }

    public String GetAvgWhite()
    {
        NumberFormat l_nf = NumberFormat.getInstance();

        l_nf.setMinimumFractionDigits(2);
        l_nf.setMaximumFractionDigits(2);
        
        if (getTotNumRolls() == 0)
            return "-";

        return l_nf.format((double) getTotSumWhiteRolls() / (double) getTotNumRolls());
    }
}

class DiceStats extends HashMap<String, CategoryDiceStats>
{
    public void Add_DR(String strCategory, int iColoredDie, int iWhiteDie)
    {
        CategoryDiceStats l_objCategoryDiceStats = get("D2_"+ strCategory);
        
        if (l_objCategoryDiceStats == null)
        {
            l_objCategoryDiceStats = new CategoryDiceStats(strCategory, true);
            put("D2_"+ strCategory, l_objCategoryDiceStats);
        }        
        
        l_objCategoryDiceStats.Add_DR(iColoredDie, iWhiteDie);
    }
    
    public void Add_dr(String strCategory, int iSingleDie)
    {
        CategoryDiceStats l_objCategoryDiceStats = get("D1_"+ strCategory);
        
        if (l_objCategoryDiceStats == null)
        {
            l_objCategoryDiceStats = new CategoryDiceStats(strCategory, false);
            put("D1_"+ strCategory, l_objCategoryDiceStats);
        }        
        
        l_objCategoryDiceStats.Add_dr(iSingleDie);
    }
    
    public String GetNumRolledDROnTotal(String strCategory, int iNum)    
    {
        CategoryDiceStats l_objCategoryDiceStats = get("D2_"+ strCategory);
        
        if (l_objCategoryDiceStats == null)
            return "-/-";
        
        return Integer.toString(l_objCategoryDiceStats.getDetailNumRolls(iNum)) + " / " + Integer.toString(l_objCategoryDiceStats.getTotNumRolls());
    }

    public String GetNumRolleddrOnTotal(String strCategory, int iNum)    
    {
        CategoryDiceStats l_objCategoryDiceStats = get("D1_"+ strCategory);
        
        if (l_objCategoryDiceStats == null)
            return "-/-";
        
        return Integer.toString(l_objCategoryDiceStats.getDetailNumColoredRolls(iNum)) + " / " + Integer.toString(l_objCategoryDiceStats.getTotNumRolls());
    }
    
    public String GetAvgDR(String strCategory)    
    {
        CategoryDiceStats l_objCategoryDiceStats = get("D2_"+ strCategory);
        
        if (l_objCategoryDiceStats == null)
            return "-";
        
        return l_objCategoryDiceStats.GetAvg();
    }

    public String GetAvgdr(String strCategory)    
    {
        CategoryDiceStats l_objCategoryDiceStats = get("D1_"+ strCategory);
        
        if (l_objCategoryDiceStats == null)
            return "-";
        
        return l_objCategoryDiceStats.GetAvg();
    }
}

public class ASLDiceBot extends AbstractBuildable 
{
    private static final String RANDOM_ORG_OPTION = "randomorgoption"; //$NON-NLS-1$
    private static final String TOTAL_CATEGORY = "Total";
    public static final String OTHER_CATEGORY = "Other";
    public static final String BUTTON_TEXT = "text"; //$NON-NLS-1$
    public static final String TOOLTIP = "tooltip"; //$NON-NLS-1$
    public static final String NAME = "name"; //$NON-NLS-1$
    public static final String ICON = "icon"; //$NON-NLS-1$
    public static final String HOTKEY = "hotkey"; //$NON-NLS-1$
    public static final String m_strHTMLSingle = 
        
"<html><style>\n" +
"	.demo { border:0px solid #C0C0C0; border-collapse:collapse; border-spacing:0px; padding:0px; }\n" +
"	.demo th { border:1px solid #C0C0C0; padding:5px; background:#FCFEE2;}\n" +
"	.demo td {border:1px solid #C0C0C0; padding:5px; text-align: right;}\n" +
"	.demo tr.total {border:2px solid #black; background:#DFF1FA;}\n" +
"	.demo td.up {border-top:2px solid black; padding:5px; font-weight: bold; text-align: right;}\n" +
"</style>\n" +
"<table class=\"demo\"><thead><tr><th colspan=\"3\">%s</th></tr><tr><th>Category</th><th>drs</th><th>Avg</th></tr></thead><tbody>%s</tbody></table</html>";

    public static final String m_strHTMLDouble = 
        
"<html><style>\n" +
"	.demo { border:0px solid #C0C0C0; border-collapse:collapse; border-spacing:0px; padding:0px; }\n" +
"	.demo th { border:1px solid #C0C0C0; padding:5px; background:#FCFEE2;}\n" +
"	.demo td {border:1px solid #C0C0C0; padding:5px; text-align: right;}\n" +
"	.demo tr.total {border-top:2px solid black; background:#DFF1FA;}\n" +
"	.demo td.up {border-top:2px solid black; padding:5px; font-weight: bold; text-align: right;}\n" +
"</style>\n" +
"<table class=\"demo\"><thead><tr><th colspan=\"5\">%s</th></tr><tr><th>Category</th><th>DRs</th><th>Avg 1st</th><th>Avg 2nd</th><th>Avg</th></tr></thead><tbody>%s</tbody></table</html>";
    
    private LaunchButton m_objButtonStats; //, m_objButtonDR, m_objButtondr;

    private final int m_MaxInstancesPerSeries = 99;
    private int m_iCurrentSeries = 0, m_iInstancesInCurrentSeries = m_MaxInstancesPerSeries;
    
    private static final int m_iMAXNUM = 1000;
    private final int[] mar_iRandomNumbers = new int[m_iMAXNUM];
    private final InstanceNumber[] mar_objInstanceNumber = new InstanceNumber[m_MaxInstancesPerSeries];
    private final ArrayList<Integer> mar_iDereferencingIndex = new ArrayList<Integer>();
    private boolean m_bUseRandomOrg = false;
    private final DiceStats map_objStats = new DiceStats();
    
    public ASLDiceBot() {
        for (int i = 0; i < m_MaxInstancesPerSeries; i++) {
            mar_objInstanceNumber[i] = new InstanceNumber();
        }
    }
    
    public void setAttribute(String key, Object value) {
    }

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
        final Prefs l_objModulePrefs = ((GameModule) parent).getPrefs();
        BooleanConfigurer l_objRandomOrgOption = null;
        
        BooleanConfigurer l_objRandomOrgOption_Exist = (BooleanConfigurer)l_objModulePrefs.getOption(RANDOM_ORG_OPTION);
        
        if (l_objRandomOrgOption_Exist == null)
        {
            l_objRandomOrgOption = new BooleanConfigurer(RANDOM_ORG_OPTION, "Get DR/dr random numbers from random.org", Boolean.FALSE);  //$NON-NLS-1$
            l_objModulePrefs.addOption(Resources.getString("Prefs.general_tab"), l_objRandomOrgOption); //$NON-NLS-1$
        }
        else
            l_objRandomOrgOption = l_objRandomOrgOption_Exist;

        m_bUseRandomOrg = (Boolean) (l_objModulePrefs.getValue(RANDOM_ORG_OPTION));
        
        l_objRandomOrgOption.addPropertyChangeListener(new PropertyChangeListener() 
        {
            public void propertyChange(PropertyChangeEvent e) 
            {
                m_bUseRandomOrg = (Boolean) e.getNewValue();
                ReadRolls();
            }
        });
    }

    private ScenInfo GetScenInfo() 
    {
        return (ScenInfo) GameModule.getGameModule().getComponentsOf(ScenInfo.class).iterator().next();
    }

    private Random GetRandomizer() {
        return GameModule.getGameModule().getRNG();
    }

    private BufferedReader GetRemoteDataReader() throws Exception 
    {
        URL l_Url = new URL("http://www.random.org/integers/?num=" + String.valueOf(m_iMAXNUM) + "&min=1&max=6&col=1&base=10&format=plain&rnd=new");
        HttpURLConnection l_Conn = (HttpURLConnection)l_Url.openConnection();

        l_Conn.setConnectTimeout(60000);
        l_Conn.setReadTimeout(60000);
        l_Conn.setRequestMethod("GET");
        l_Conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                
        return new BufferedReader(new InputStreamReader(l_Conn.getInputStream()));
    }

    private void ReadRolls() 
    {
        mar_iDereferencingIndex.clear();
        
        // if use random.org read data from web
        if (m_bUseRandomOrg)
        {
            int l_i = 0;
            
            OutputString(" ");
            OutputString(" Reading data from random.org");
            
            try 
            {
                BufferedReader reader = GetRemoteDataReader();
                String l_strLine = reader.readLine();

                while (l_strLine != null) 
                {
                    mar_iRandomNumbers[l_i] = Integer.parseInt(l_strLine);
                    mar_iDereferencingIndex.add(l_i);
                    l_i++;

                    l_strLine = reader.readLine();
                }

                reader.close();

                if (l_i != m_iMAXNUM) {
                    OutputString(" Random.org not reachable or read failed");
                    mar_iDereferencingIndex.clear();
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
            Random l_RND = GetRandomizer();
            
            for (int l_i = 0; l_i < m_iMAXNUM; l_i++)
            {
                mar_iRandomNumbers[l_i] = (int)((l_RND.nextFloat() * 6) + 1);
                mar_iDereferencingIndex.add(l_i);
            }        
            
            OutputString(" ");
            OutputString(" Data correctly generated");
        }
    }

    private String GetPlayer() {
        return GlobalOptions.getInstance().getPlayerId();
    }

    private String IsSpecial(int iTotal) 
    {
        ScenInfo l_objScenInfo = GetScenInfo();
    
        if (iTotal == l_objScenInfo.getAxisSAN()) 
        {
            if (iTotal == l_objScenInfo.getAlliedSAN()) 
            {
                return "Axis/Allied SAN    ";
            } else {
                return "Axis SAN    ";
            }
        } 
        else if (iTotal == l_objScenInfo.getAlliedSAN()) 
        {
            return "Allied SAN    ";
        }

        return "";
    }

    private void OutputString(String strMsg) {
        GameModule.getGameModule().getChatter().send(strMsg);
    }

    private int GetDieRoll() 
    {
        int l_iSlot, l_iRet;
        Random l_RND = GetRandomizer();

        if (mar_iDereferencingIndex.size() == 0) 
            ReadRolls();

        if (mar_iDereferencingIndex.size() > 0) 
        {
            // scelgo casualmente quale numero tra 0 e m_PointersList.size
            l_iSlot = (int) (l_RND.nextFloat() * mar_iDereferencingIndex.size());

            // prendo il numero scelto
            l_iRet = mar_iRandomNumbers[mar_iDereferencingIndex.get(l_iSlot)];

            // lo rimuovo per renderlo inutilizzabile
            mar_iDereferencingIndex.remove(l_iSlot);

            // ritorno il valore
            return l_iRet;
        }

        // se torno 0 qualcosa è andato storto
        return 0;
    }

    public void DR(String strCategory) 
    {
        int l_iWhiteDie = 0;
        int l_iColoredDie = GetDieRoll();

        if (l_iColoredDie != 0)
            l_iWhiteDie = GetDieRoll();

        if ((l_iColoredDie != 0) && (l_iWhiteDie != 0)) 
        {
            String l_strOutput;
                
            map_objStats.Add_DR(TOTAL_CATEGORY, l_iColoredDie, l_iWhiteDie);
            map_objStats.Add_DR(strCategory, l_iColoredDie, l_iWhiteDie);

            l_strOutput = String.format("*** (%s DR) %s,%s ***   <%s>      %s[%s   avg   %s (%s)]    (%s%s", 
                                        strCategory, 
                                        Integer.toString(l_iColoredDie),
                                        Integer.toString(l_iWhiteDie),
                                        GetPlayer(),
                                        IsSpecial(l_iColoredDie + l_iWhiteDie),
                                        map_objStats.GetNumRolledDROnTotal(TOTAL_CATEGORY, l_iColoredDie + l_iWhiteDie),
                                        map_objStats.GetAvgDR(strCategory),
                                        map_objStats.GetAvgDR(TOTAL_CATEGORY),
                                        getSerieInstanceNumber(),
                                        (m_bUseRandomOrg ? " - by random.org)" : ")")
                                        );
                
            OutputString(l_strOutput);
        }
    }

    public void dr(String strCategory) 
    {
        int l_iSingleDie = 0;

        l_iSingleDie = GetDieRoll();

        if (l_iSingleDie != 0) 
        {
            String l_strOutput;
            
            map_objStats.Add_dr(TOTAL_CATEGORY, l_iSingleDie);
            map_objStats.Add_dr(strCategory, l_iSingleDie);
            
            l_strOutput = String.format("*** (%s dr) %s ***   <%s>      [%s   avg   %s (%s)]    (%s%s", 
                                        strCategory, 
                                        Integer.toString(l_iSingleDie),
                                        GetPlayer(),
                                        map_objStats.GetNumRolleddrOnTotal(TOTAL_CATEGORY, l_iSingleDie),
                                        map_objStats.GetAvgdr(strCategory),
                                        map_objStats.GetAvgdr(TOTAL_CATEGORY),
                                        getSerieInstanceNumber(),
                                        (m_bUseRandomOrg ? " - by random.org)" : ")")                             
                                        );
                
            OutputString(l_strOutput);
        }
    }

    String getSerieInstanceNumber() 
    {
        Random l_RND = GetRandomizer();
        NumberFormat l_nf = NumberFormat.getInstance();

        if (m_iInstancesInCurrentSeries >= m_MaxInstancesPerSeries) {
            m_iCurrentSeries++;
            m_iInstancesInCurrentSeries = 0;

            for (int i = 0; i < m_MaxInstancesPerSeries; i++) {
                mar_objInstanceNumber[i].m_iIndex = i + 1;
                mar_objInstanceNumber[i].m_fOrder = l_RND.nextFloat();
            }

            Arrays.sort(mar_objInstanceNumber, new InstanceComparator());
        }

        l_nf.setMinimumIntegerDigits(2);

        return l_nf.format(m_iCurrentSeries) + "." + l_nf.format(mar_objInstanceNumber[m_iInstancesInCurrentSeries++].m_iIndex);
    }

    public void statsToday() 
    {
        StringBuilder l_objStringBuilder = new StringBuilder();
        List<CategoryDiceStats> lar_objStats = new ArrayList<CategoryDiceStats>(map_objStats.values());

        Collections.sort(lar_objStats, new Comparator<CategoryDiceStats>() {

            public int compare(CategoryDiceStats o1, CategoryDiceStats o2) 
            {
                double l_dAvg1 = o1.GetNumericAvg();
                double l_dAvg2 = o2.GetNumericAvg();
                
                if (l_dAvg1 > l_dAvg2)
                    return 1;
                else if (l_dAvg1 < l_dAvg2)
                    return -1;
                else
                    return 0;
            }
        });

        for (CategoryDiceStats l_objCat_dr : lar_objStats)
        {
            if (l_objCat_dr.IsTwoDice())
                continue;
            
            if (l_objCat_dr.getCategory().compareToIgnoreCase(TOTAL_CATEGORY) == 0)
                continue;
            
            l_objStringBuilder.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td></tr>", 
                                                l_objCat_dr.getCategory(), 
                                                l_objCat_dr.getTotNumRolls(),
                                                l_objCat_dr.GetAvg())
                                        );
        }

        CategoryDiceStats l_objCat_dr = map_objStats.get("D1_" + TOTAL_CATEGORY);
        
        if (l_objCat_dr != null)
        {
            l_objStringBuilder.append(String.format("<tr class=\"total\"><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td></tr>", 
                                                l_objCat_dr.getCategory(), 
                                                l_objCat_dr.getTotNumRolls(),
                                                l_objCat_dr.GetAvg())
                                            );
        }
        
        OutputString(String.format(m_strHTMLSingle, GetPlayer() + "'s drs today", l_objStringBuilder.toString()));
        
        
        l_objStringBuilder = new StringBuilder();
        
        for (CategoryDiceStats l_objCat_DR : lar_objStats)
        {
            if (!l_objCat_DR.IsTwoDice())
                continue;
            
            if (l_objCat_DR.getCategory().compareToIgnoreCase(TOTAL_CATEGORY) == 0)
                continue;

            String l_strBgColor = "";
            double l_dAvg = l_objCat_DR.GetNumericAvg();
            
            if (l_dAvg < 6.75)
                l_strBgColor = "bgcolor=#83C07A";
            else if (l_dAvg > 7.25)
                l_strBgColor = "bgcolor=#FE686D";
            
            l_objStringBuilder.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td %s>%s</td></tr>", 
                                                l_objCat_DR.getCategory(), 
                                                l_objCat_DR.getTotNumRolls(),
                                                l_objCat_DR.GetAvgColored(),
                                                l_objCat_DR.GetAvgWhite(),
                                                l_strBgColor,
                                                l_objCat_DR.GetAvg())
                                        );
        }

        CategoryDiceStats l_objCat_DR = map_objStats.get("D2_" + TOTAL_CATEGORY);
        
        if (l_objCat_DR != null)
        {
            String l_strBgColor = "";
            double l_dAvg = l_objCat_DR.GetNumericAvg();
            
            if (l_dAvg < 6.75)
                l_strBgColor = "bgcolor=#83C07A";
            else if (l_dAvg > 7.25)
                l_strBgColor = "bgcolor=#FE686D";
            
            l_objStringBuilder.append(String.format("<tr class=\"total\"><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\">%s</td><td class=\"up\" %s>%s</td></tr>", 
                                                l_objCat_DR.getCategory(), 
                                                l_objCat_DR.getTotNumRolls(),
                                                l_objCat_DR.GetAvgColored(),
                                                l_objCat_DR.GetAvgWhite(),
                                                l_strBgColor,
                                                l_objCat_DR.GetAvg())
                                            );
        }

        OutputString(String.format(m_strHTMLDouble, GetPlayer() + "'s DRs today", l_objStringBuilder.toString()));        
    }
}
