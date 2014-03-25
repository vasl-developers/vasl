package VASL.LOSGUI.Selection;

import VASL.LOS.Map.Terrain;

import java.awt.*;

/**
 * Created with IntelliJ IDEA.
 * User: USULLD2
 * Date: 5/30/13
 * Time: 9:48 PM
 */
public class RotatedRectangularSelection extends RectangularSelection {

    int centerX;
    int centerY;
    int width;
    int height;
    int rotation;

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getRotation() {
        return rotation;
    }

    public RotatedRectangularSelection(Shape paintShape, int centerX, int centerY, int width, int height, int rotation) {

        this.paintShape = paintShape;
        this.centerX = centerX;
        this.centerY = centerY;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }

    public String getTerrainXMLSnippet(Terrain terrain){

        return
                "<terrainEdit type=\"RotatedRectangle\" " +
                        "centerX=\"" + centerX + "\" " +
                        "centerY=\"" + centerY + "\" " +
                        "width=\"" + width + "\" " +
                        "height=\"" + height + "\" " +
                        "rotation=\"" + rotation + "\" " +
                        "elevation=\"" + terrain.getName() + "\" " +
                        "/>";
    }

    public String getElevationXMLSnippet(int elevation){

        return
                "<elevationEdit type=\"RotatedRectangle\" " +
                        "centerX=\"" + centerX + "\" " +
                        "centerY=\"" + centerY + "\" " +
                        "width=\"" + width + "\" " +
                        "height=\"" + height + "\" " +
                        "rotation=\"" + rotation + "\" " +
                        "elevation=\"" + elevation + "\" " +
                        "/>";
    }
}
