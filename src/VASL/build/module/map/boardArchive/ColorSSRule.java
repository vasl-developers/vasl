package VASL.build.module.map.boardArchive;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class ColorSSRule {

    // the set of board color mappings for this SSR (fromColor, toColor)
    private LinkedHashMap<String, String> colorMaps = new LinkedHashMap<String, String>();

    /**
     * Add a new color mapping
     * @param fromColor the from color
     * @param toColor the to color
     */
    public void addColorMap(String fromColor, String toColor) {
        colorMaps.put(fromColor, toColor);
    }

    /**
     * @return the set of color maps for this SSR
     */
    public HashMap<String, String> getColorMaps(){
        return colorMaps;
    }

    /**
     * Get the mapping for the given board color
     * @param fromColor the from color
     * @return the to color
     */
    public String getMappedColor(String fromColor) {
        return colorMaps.get(fromColor);
    }
}
