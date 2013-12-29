package VASL.build.module.map.boardArchive;


import org.jdom2.Element;
import org.jdom2.JDOMException;

import java.awt.*;
import java.util.LinkedHashMap;

/**
 * Contains metadata that is common to the shared and board metadata files
 */
public abstract class AbstractMetadata {

    protected static final String colorsElement = "colors";
    private static final String colorElement = "color";
    private static final String colorNameAttribute = "name";
    private static final String colorRedAttribute = "red";
    private static final String colorGreenAttribute = "green";
    private static final String colorBlueAttribute = "blue";
    private static final String colorTerrainAttribute = "terrain";
    private static final String colorElevationAttribute = "elevation";

    protected static final String colorSSRulesElement = "colorSSRules";
    private static final String colorSSRElement = "colorSSR";
    private static final String colorSSRuleName = "name";
    private static final String colorMapElement = "colorMap";
    private static final String colorMapFromColorAttribute = "fromColor";
    private static final String colorMapToColorAttribute = "toColor";

    // Maps color names to board color object
    protected LinkedHashMap<String, BoardColor> boardColors = new LinkedHashMap<String, BoardColor>(100);

    // Maps a Color object to VASL color name
    protected LinkedHashMap<Color, String> colorToVASLColorName = new LinkedHashMap<Color, String>(100);

    // Maps rule name to the rule object
    protected LinkedHashMap<String, ColorSSRule> colorSSRules = new LinkedHashMap<String, ColorSSRule>(100);

    /**
     * Assert the element has the given name otherwise throw an exception
     * @param element the element
     * @param elementName the element name
     * @throws org.jdom2.JDOMException
     */
    protected void assertElementName(Element element, String elementName) throws JDOMException {

        // make sure we have the right element
        if(!element.getName().equals(elementName)) {
            throw new JDOMException("Invalid element passed to an element parser: " + elementName);
        }
    }

    /**
     * Parses the colors element
     * @param element the colors element
     * @throws org.jdom2.JDOMException
     */
    protected void parseColors(Element element) throws JDOMException {

        parseColors(element, false);
    }

    /**
     * Parses the colors element, replacing any existing colors
     * @param element the colors element
     * @param replace replace existing colors?
     * @throws org.jdom2.JDOMException
    */
    protected void parseColors(Element element, boolean replace) throws JDOMException {

        // make sure we have the right element
        assertElementName(element, colorsElement);

        for(Element e: element.getChildren()) {

            // ignore any child elements that are not color elements
            if(e.getName().equals(colorElement)){

                // read the color attributes
                String name = e.getAttributeValue(colorNameAttribute);
                String terrain = e.getAttributeValue(colorTerrainAttribute);
                String elevation = e.getAttributeValue(colorElevationAttribute);

                // create and set the color
                Color color = new Color(
                        e.getAttribute(colorRedAttribute).getIntValue(),
                        e.getAttribute(colorGreenAttribute).getIntValue(),
                        e.getAttribute(colorBlueAttribute).getIntValue()
                );

                BoardColor boardColor = new BoardColor(name, color, terrain, elevation);

                // add the color to the list of VASL colors
                boardColors.put(name, boardColor);

                // replace existing?
                if(replace) {
                    colorToVASLColorName.put(color, name);
                }

                // if there are redundant colors (and there are) keep the first one in the list
                else if (!colorToVASLColorName.containsKey(color)) {

                    colorToVASLColorName.put(color, name);
                }
            }
        }
    }

    /**
     * Parses the scenario-specific color rules element
     * @param element the colorSSRules element
     * @throws org.jdom2.JDOMException
     */
    protected void parseColorSSRules(Element element) throws JDOMException {

        // make sure we have the right element
        assertElementName(element, colorSSRulesElement);

        for(Element e: element.getChildren()) {

            ColorSSRule colorSSRule = new ColorSSRule();
            String name = e.getAttributeValue(colorSSRuleName);

            // ignore any child elements that are not colorSSRules
            if(e.getName().equals(colorSSRElement)) {

                // read all of the mappings
                for (Element map: e.getChildren()) {

                    // ignore any child element that is not a color map
                    if(map.getName().equals(colorMapElement)) {

                        String fromColor = map.getAttributeValue(colorMapFromColorAttribute);
                        String toColor = map.getAttributeValue(colorMapToColorAttribute);

                        colorSSRule.addColorMap(fromColor, toColor);
                    }
                }
            }

            // make sure there is at least one mapping
            if (colorSSRule.getColorMaps().size() < 1) {
                throw new JDOMException("colorSSRule " + name + " has no mappings");
            }

            // save the rule
            colorSSRules.put(name, colorSSRule);
        }
    }

    /**
     * @return the board colors
     */
    protected LinkedHashMap<String, BoardColor> getBoardColors() {
        return boardColors;
    }

    /**
      * @return the color SSR rules
     */
    protected LinkedHashMap<String, ColorSSRule> getColorSSRules() {
        return colorSSRules;
    }

    /**
     * @return the color to VASL color name mapping
     */
    public LinkedHashMap<Color, String> getColorToVASLColorName() {
        return colorToVASLColorName;
    }
}
