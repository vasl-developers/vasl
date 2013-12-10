package VASL.build.module.map.boardArchive;

import java.awt.*;

/**
 * A named VASL board color with LOS terrain and elevation codes
 */
public class BoardColor {

    private String VASLColorName;
    private Color color;
    private String terrainName;
    private String elevation;

    public BoardColor(String VASLColorName, Color color, String terrainName, String elevation) {

        this.VASLColorName = VASLColorName;
        this.color = color;
        this.terrainName = terrainName;
        this.elevation = elevation;

    }

    /**
     * @return the VASL color name
     */
    public String getVASLColorName() {
        return VASLColorName;
    }

    /**
     * @return the LOS elevation
     */
    public String getElevation() {

        return elevation;
    }

    /**
     * @return the LOS terrain
     */
    public String getTerrainName() {

        return terrainName;
    }

    /**
     * @return the board color
     */
    public Color getColor() {
        return color;
    }
}
