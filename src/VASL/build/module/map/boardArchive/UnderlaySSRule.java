package VASL.build.module.map.boardArchive;

import java.util.ArrayList;

/**
 * A simple class for an SSR underlay transformation
 */
public class UnderlaySSRule {

    private String name;
    private String imageName;
    private ArrayList<String> colors;

    UnderlaySSRule(String name, String imageName, ArrayList<String> colors) {

        this.name = name;
        this.imageName = imageName;
        this.colors = colors;
    }

    /**
     * @return the underlay SSR name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the underlay image name
     */
    public String getImageName() {
        return imageName;
    }

    /**
     * @return the list of colors
     */
    public ArrayList<String> getColors() {
        return colors;
    }
}
