package VASL.build.module.map.boardArchive;

import java.util.HashMap;

/**
 * A simple class for an SSR overlay transformation
 */
public class OverlaySSRule {

    String name;
    HashMap<String, OverlaySSRuleImage> images = new HashMap<String, OverlaySSRuleImage>(1);

    OverlaySSRule (String name) {

        this.name = name;
    }

    public String getName() {
        return name;
    }

    public HashMap<String, OverlaySSRuleImage> getImages() {
        return images;
    }

    public void addImage(OverlaySSRuleImage image) {
        images.put(image.getImageName(), image);
    }
}
