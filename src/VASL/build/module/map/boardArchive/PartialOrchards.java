package VASL.build.module.map.boardArchive;

import java.util.HashMap;

public class PartialOrchards implements SpecialHexsideTerrain{
    private HashMap<String, boolean[]> partialorchards = new HashMap<String, boolean[]>(10);
    @Override
    public void addSpecialHexside(String hex, boolean[] hexsides) {
        partialorchards.put(hex, hexsides);
    }


    @Override
    public boolean[] getSpecialHexside(String hex) {
        return partialorchards.get(hex);
    }

    @Override
    public boolean hasSpecialHexside(String hex, int hexside) {
        try {
            return partialorchards.get(hex)[hexside];
        }
        catch (Exception e) {
            return false;
        }
    }

    @Override
    public HashMap<String, boolean[]> getAllSpecialHexsides() {
        return partialorchards;
    }
}
