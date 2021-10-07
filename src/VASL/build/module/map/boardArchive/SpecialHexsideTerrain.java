package VASL.build.module.map.boardArchive;

import java.util.HashMap;
import java.util.LinkedList;

public interface SpecialHexsideTerrain {

    //HashMap<String, boolean[]> SpecialHexsides[];

    void addSpecialHexside(String hex, boolean[] hexsides);

    boolean[] getSpecialHexside(String hex);

    boolean hasSpecialHexside(String hex, int hexside);

    HashMap<String, boolean[]> getAllSpecialHexsides();

}
