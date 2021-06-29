package VASL.build.module.map.boardArchive;

import java.util.HashMap;

/**
 * Created by dougr_000 on 4/19/2016.
 */
// code added by DR to handle RB rr embankments
public class RBrrembankments implements SpecialHexsideTerrain {
    private HashMap<String, boolean[]> rbrrembankments = new HashMap<String, boolean[]>(10);

    public void addSpecialHexside(String hex, boolean[] hexsides) {
        rbrrembankments.put(hex, hexsides);
    }

    public boolean[] getSpecialHexside(String hex) {
        return rbrrembankments.get(hex);
    }

    public boolean hasSpecialHexside(String hex, int hexside) {
        try {
            return rbrrembankments.get(hex)[hexside];
        }
        catch (Exception e) {
            return false;
        }
    }

    public HashMap<String, boolean[]> getAllSpecialHexsides(){
        return rbrrembankments;
    }
}
