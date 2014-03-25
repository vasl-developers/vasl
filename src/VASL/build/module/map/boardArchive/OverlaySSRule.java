package VASL.build.module.map.boardArchive;

/**
 * A simple class for an SSR underlay transformation
 */
public class OverlaySSRule {

    String name;
    String imageName;
    int x;
    int y;

    OverlaySSRule (String name, String imageName, int x, int y) {

        this.name = name;
        this.imageName = imageName;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public String getImageName() {
        return imageName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
