package VASL.build.module.map.boardArchive;

import VASL.LOS.Map.Terrain;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class SharedBoardMetadata extends AbstractMetadata {

    private static final String sharedBoardMetadataElement = "sharedBoardMetadata";

    private static final String terrainTypesElement = "terrainTypes";
    private static final String terrainTypeElement = "terrainType";
    private static final String terrainTypeNameAttr = "name";
    private static final String terrainTypeCodeAttr = "typeCode";
    private static final String terrainTypeIsLOSObstacleAttr = "isLOSObstacle";
    private static final String terrainTypeIsLOSHindranceAttr = "isLOSHindrance";
    private static final String terrainTypeIsHalfLevelHeightAttr = "isHalfLevelHeight";
    private static final String terrainTypeIsInherentTerrainAttr = "isInherentTerrain";
    private static final String terrainTypeSplitAttr = "split";
    private static final String terrainTypeIsLowerLOSObstacleAttr = "isLowerLOSObstacle";
    private static final String terrainTypeIsLowerLOSHindranceAttr = "isLowerLOSHindrance";
    private static final String terrainTypeHeightAttr = "height";
    private static final String terrainTypeMapColorRedAttr = "mapColorRed";
    private static final String terrainTypeMapColorGreenAttr = "mapColorGreen";
    private static final String terrainTypeMapColorBlueAttr = "mapColorBlue";
    private static final String terrainTypeLOSCategory = "LOSCategory";

    // maps terrain names to terrain objects
    private LinkedHashMap<String, Terrain> terrainTypes = new LinkedHashMap<String, Terrain>(256);


    /**
     * Parses a shared board metadata file
     * @param metadata an <code>InputStream</code> for the shared board metadata XML file
     * @throws JDOMException
     */
    public void parseSharedBoardMetadataFile(InputStream metadata) throws JDOMException {


        SAXBuilder parser = new SAXBuilder();

        try {

            // the root element will be the boardMetadata element
            Document doc = parser.build(metadata);
            Element root = doc.getRootElement();

            // read the shared metadata
            if(root.getName().equals(sharedBoardMetadataElement)) {

                parseColors(root.getChild(colorsElement));
                parseColorSSRules(root.getChild(colorSSRulesElement));
                parseTerrainTypes(root.getChild(terrainTypesElement));
                parseLOSSSRules(root.getChild(LOSSSRulesElement));
                parseLOSCounterRules(root.getChild(LOSCounterRulesElement));
                parseOverlaySSRules(root.getChild(overlaySSRulesElement));
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new JDOMException("Error reading the shared board metadata", e);
        }
    }

    /**
     * Parses the terrain types
     * @param element the terrainTypes element
     * @throws org.jdom2.JDOMException
     */
    protected void parseTerrainTypes(Element element) throws JDOMException {

        // make sure we have the right element
        assertElementName(element, terrainTypesElement);

        for(Element e: element.getChildren()) {

            // ignore any child elements that are not terrainType
            if(e.getName().equals(terrainTypeElement)){

                // read the terrainType attributes
                Terrain terrain = new Terrain();
                terrain.setName(e.getAttribute(terrainTypeNameAttr).getValue());
                terrain.setType(e.getAttribute(terrainTypeCodeAttr).getIntValue());
                terrain.setLOSObstacle(e.getAttribute(terrainTypeIsLOSObstacleAttr).getBooleanValue());
                terrain.setLOSHindrance(e.getAttribute(terrainTypeIsLOSHindranceAttr).getBooleanValue());
                terrain.setHalfLevelHeight(e.getAttribute(terrainTypeIsHalfLevelHeightAttr).getBooleanValue());
                terrain.setInherentTerrain(e.getAttribute(terrainTypeIsInherentTerrainAttr).getBooleanValue());
                terrain.setSplit(e.getAttribute(terrainTypeSplitAttr).getFloatValue());
                terrain.setLowerLOSHindrance(e.getAttribute(terrainTypeIsLowerLOSHindranceAttr).getBooleanValue());
                terrain.setLowerLOSObstacle(e.getAttribute(terrainTypeIsLowerLOSObstacleAttr).getBooleanValue());
                terrain.setHeight(e.getAttribute(terrainTypeHeightAttr).getIntValue());

                // create and set the map color
                Color color = new Color(
                        e.getAttribute(terrainTypeMapColorRedAttr).getIntValue(),
                        e.getAttribute(terrainTypeMapColorGreenAttr).getIntValue(),
                        e.getAttribute(terrainTypeMapColorBlueAttr).getIntValue()
                );
                terrain.setMapColor(color);

                // set the LOS category
                String LOSCategory = e.getAttributeValue(terrainTypeLOSCategory);
                if(LOSCategory.equals("MARKETPLACE")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.MARKETPLACE);
                }
                else if(LOSCategory.equals("BUILDING")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.BUILDING);
                }
                else if(LOSCategory.equals("FACTORY")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.FACTORY);
                }
                else if(LOSCategory.equals("OPEN")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.OPEN);
                }
                else if(LOSCategory.equals("WATER")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.WATER);
                }
                else if(LOSCategory.equals("WOODS")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.WOODS);
                }
                else if(LOSCategory.equals("ROAD")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.ROAD);
                }
                else if(LOSCategory.equals("DEPRESSION")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.DEPRESSION);
                }
                else if(LOSCategory.equals("STREAM")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.STREAM);
                }
                else if(LOSCategory.equals("TUNNEL")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.TUNNEL);
                }
                else if(LOSCategory.equals("BRIDGE")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.BRIDGE);
                }
                else if(LOSCategory.equals("HEXSIDE")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.HEXSIDE);
                }
                else if(LOSCategory.equals("ENTRENCHMENT")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.ENTRENCHMENT);
                }
                else if(LOSCategory.equals("OTHER")) {
                    terrain.setLOSCategory(Terrain.LOSCategories.OTHER);
                }
                else {
                    System.err.println("Invalid LOS category found: " + LOSCategory);
                }

                // add the terrain type to the terrain list
                terrainTypes.put(terrain.getName(), terrain);
            }
        }
    }

    /**
     * @return the list of terrain types
     */
    public HashMap<String, Terrain> getTerrainTypes() {

        return terrainTypes;
    }
}
