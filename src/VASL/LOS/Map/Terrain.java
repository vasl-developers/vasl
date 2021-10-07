package VASL.LOS.Map;

import java.awt.*;

/**
 * A terrain type
 */
public class Terrain {

    // private variables
    private String  name;
    private int	 type;
    private boolean LOSObstacle			= false;
    private boolean LOSHindrance		= false;
    private boolean lowerLOSObstacle	= false;
    private boolean lowerLOSHindrance	= false;
    private boolean inherentTerrain     = false;
    private boolean halfLevelHeight		= false;
    private int	    height				= 0;
    private float   split				= (float) 0.0;

    private Color mapColor			    = new Color(0, 0, 0);

    private LOSCategories LOSCategory = LOSCategories.OPEN;

    /**
     * The LOS categories
     */
    public enum LOSCategories {
        HEXSIDE,
        BUILDING,
        MARKETPLACE,
        FACTORY,
        OPEN,
        ENTRENCHMENT,
        BRIDGE,
        TUNNEL,
        DEPRESSION,
        ROAD,
        WOODS,
        STREAM,
        WATER,
        OTHER
    }

    public boolean isEntrenchmentTerrain() {
        return LOSCategory == LOSCategories.ENTRENCHMENT;
    }

    /**
     * @return return true if the terrain is a building
     */
    public boolean isBuildingTerrain() {
        return LOSCategory == LOSCategories.MARKETPLACE || LOSCategory == LOSCategories.BUILDING;
    }

    /**
     * @return return true if the terrain is water
     */
    public boolean isWaterTerrain() {
        return LOSCategory == LOSCategories.WATER;
    }

    /**
     * @return return true if the terrain is a bridge
     */
    public boolean isBridge() {
        return LOSCategory == LOSCategories.BRIDGE;
    }

    /**
     * @return return true if the terrain is a road
     */
    public boolean isRoad() {
        return LOSCategory == LOSCategories.ROAD;
    }
    /**
     * @return return true if the terrain is depression terrain
     */
    public boolean isDepression() {
        return LOSCategory == LOSCategories.DEPRESSION;
    }

    /**
     * @return return true if the terrain is inherent terrain
     */
    public boolean isInherentTerrain(){

        return inherentTerrain;
    }

    /**
     * @return the name of the terrain
     */
    public String getName() {
        return name;
    }

    /**
     * Set the terrain name
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the terrain type code
     */
    public int getType() {
        return type;
    }

    /**
     * Set the terrain type code
     * @param type the new type
     */
    public void setType(int type) {
        this.type = type;
    }

    /**
     * @return return true if the terrain is an LOS obstacle
     */
    public boolean isLOSObstacle() {
        return LOSObstacle;
    }

    /**
     * Set the LOS obstacle flag
     * @param LOSObstacle the new LOS obstacle flag
     */
    public void setLOSObstacle(boolean LOSObstacle) {
        this.LOSObstacle = LOSObstacle;
    }

    /**
     * Set the LOS hindrance flag
     * @param LOSHindrance the new LOS hindrance flag
     */public void setLOSHindrance(boolean LOSHindrance) {
        this.LOSHindrance = LOSHindrance;
    }

    /**
     * @return true is terrain has a lower LOS obstacle
     */
    public boolean isLowerLOSObstacle() {
        return lowerLOSObstacle;
    }

    /**
     * Set the lower LOS obstacle flag. Only used for "split" terrain types
     * @param lowerLOSObstacle the new lower LOS obstacle flag
     */
    public void setLowerLOSObstacle(boolean lowerLOSObstacle) {
        this.lowerLOSObstacle = lowerLOSObstacle;
    }

    /**
     * @return true if the terrain is a lower LOS hindrance
     */
    public boolean isLowerLOSHindrance() {
        return lowerLOSHindrance;
    }

    /**
     * Set the lower LOS hindrance flag. Only used for "split" terrain types
     * @param lowerLOSHindrance the lower LOS hindrance flag
     */
    public void setLowerLOSHindrance(boolean lowerLOSHindrance) {
        this.lowerLOSHindrance = lowerLOSHindrance;
    }

    /**
     * @return true if the terrain is a half level height. E.g. walls, 1.5 level buildings
     */
    public boolean isHalfLevelHeight() {
        return halfLevelHeight;
    }

    /**
     * Set the half level height flag
     * @param halfLevelHeight the new half level height flag
     */
    public void setHalfLevelHeight(boolean halfLevelHeight) {
        this.halfLevelHeight = halfLevelHeight;
    }

    /**
     * @return the height of the terrain in levels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Set the height of the terrain
     * @param height the height of the terrain in levels
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * @return true if the terrain is a "split" type terrain. Only used for marketplaces.
     */
    public float getSplit() {
        return split;
    }

    /**
     * Set the level of the terrain split.
     * @param split the level of the terrain split
     */
    public void setSplit(float split) {
        this.split = split;
    }

    /**
     * @return the default color on the terrain on the map
     */
    public Color getMapColor() {
        return mapColor;
    }

    /**
     * Set the default <code>Color</code>
     * @param mapColor the new <code>Color</code>
     */
    public void setMapColor(Color mapColor) {
        this.mapColor = mapColor;
    }

    /**
     * Set the inherent terrain flag
     * @param inherentTerrain the new inherent terrain flag
     */
    public void setInherentTerrain(boolean inherentTerrain) {
        this.inherentTerrain = inherentTerrain;
    }

    /**
     * Set the LOS category. See the <code>LOSCategories</code> enumeration
     * @param LOSCategory the new LOS categories
     */
    public void setLOSCategory(LOSCategories LOSCategory) {
        this.LOSCategory = LOSCategory;
    }

    /**
     * Get the LOS category
     * @return the LOS category
     */
    public LOSCategories getLOSCategory(){
        return LOSCategory;
    }

    /**
     * @return true if the terrain is a building
     */
    public boolean isBuilding() {

        return
                LOSCategory == LOSCategories.BUILDING ||
                        LOSCategory == LOSCategories.FACTORY ||
                        LOSCategory == LOSCategories.MARKETPLACE;
    }

    /**
     * @return true if the terrain is hexside terrain
     */
    public boolean isHexsideTerrain() {

        return LOSCategory == LOSCategories.HEXSIDE ||
                isRowhouseFactoryWall();
    }

    /**
     * @return true if terrain is open terrain
     */
    public boolean isOpen() {

        return LOSCategory == LOSCategories.OPEN ||
                LOSCategory == LOSCategories.ROAD ||
                LOSCategory == LOSCategories.WATER;
    }

    /**
     * @return true if the terrain has a split. E.g. marketplace
     */
    public boolean hasSplit() {

        return split != 0.0;
    }

    /**
     * @return true if terrain is a rowhouse or interior factory wall
     */
    public boolean isRowhouseFactoryWall(){

        return name.equals("Rowhouse Wall") ||
                name.equals("Rowhouse Wall, 1 Level") ||
                name.equals("Rowhouse Wall, 2 Level") ||
                name.equals("Rowhouse Wall, 3 Level") ||
                name.equals("Rowhouse Wall, 4 Level") ||
                // code added DR to handle interior factory walls
                name.equals("Interior Factory Wall, 1 Level") ||
                name.equals("Interior Factory Wall, 2 Level");

    }
    /**
     * @return true if terrain is an exterior factory wall
     */
    public boolean isOutsideFactoryWall(){

        return name.equals("Stone Factory Wall, 1.5 Level") ||
                name.equals("Stone Factory Wall, 2.5 Level")||
                name.equals("Wooden Factory Wall, 1.5 Level") ||
                name.equals("Wooden Factory Wall, 2.5 Level") ;
    }
    /**
     * @return true if the terrain is a stream
     */
    public boolean isStream(){

        return name.equals("Dry Stream") ||
                name.equals("Shallow Stream") ||
                name.equals("Deep Stream") ||
                name.equals("Flooded Stream");
    }

    // DR added to test for Rooftops, roofless buildings, cellars and cliffs
    /**
     * @return true if the terrain is a cellar
     */
    public boolean isCellar(){

        return name.contains("Cellar");
    }
    /**
     * @return true if the terrain is a rooftop
     */
    public boolean isRooftop(){

        return name.contains("Rooftop");
    }

    /**
     * @return true if the terrain is a roofless building
     */
    public boolean isRoofless(){

        return (name.contains("Roofless") || name.contains("Gutted"));
    }
    /**
     * @return true if the terrain is a cliff
     */
    public boolean isCliff(){

        return name.contains("Cliff");
    }
    /**
     * @return true if the terrain is a factory
     */
    public boolean isFactoryTerrain() {
        return LOSCategory == LOSCategories.FACTORY;
    }
}
