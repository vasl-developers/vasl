    /**
     * @return the hex snap scale
     */
package VASL.build.module.map.boardArchive;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

/**
 * Read the XML metadata in the board archive
 */
//TODO: add error checking and test
//TODO: make metadata transient to minimize memory footprint
public class BoardMetadata extends AbstractMetadata {

    // UNKNOWN is used in the XML to indicate an unknown value
    public static final String UNKNOWN = "UNKNOWN";

    // return code constants
    public final static int NO_ELEVATION = -99;
    public final static int NO_TERRAIN = -1;

    // maps hex names to building types
    private LinkedHashMap<String, String> buildingTypes = new LinkedHashMap<String, String>();

    // set of hexes with slopes
    private Slopes slopes = new Slopes();
    // code added by DR to handle RB rr embankments and partial orchards
    private RBrrembankments rbrrembankments = new RBrrembankments();
    private PartialOrchards partialOrchards = new PartialOrchards();
    // Board-level metadata
    public final static int MISSING = -999; // used to indicate the value was not in the metadata (for optional attributes)
    private String name;
    private String version;
    private String versionDate;
    private String author;
    private String boardImageFileName;
    private boolean hasHills;
    private int width;
    private int height;

    private double A1CenterX = MISSING;  // location of the hex A1 center dot
    private double A1CenterY = MISSING;
    private double hexWidth = (float) MISSING;
    private double hexHeight = (float) MISSING;
    private int snapScale = MISSING;
    private boolean altHexGrain = false; // upper left is A0, B1 is higher

    // Board metadata file element and attribute constants
    private static final String boardMetadataElement = "boardMetadata";
    private static final String boardMetadataImageFileNameAttr = "boardImageFileName";
    private static final String boardMetadataHasHillsAttr = "hasHills";
    private static final String boardMetadataWidthAttr = "width";
    private static final String boardMetadataHeightAttr = "height";
    private static final String boardMetadataNameAttr = "name";
    private static final String boardMetadataVersionAttr = "version";
    private static final String boardMetadataVersionDateAttr = "versionDate";
    private static final String boardMetadataAuthorAttr = "author";
    private static final String boardMetadataA1CenterXAttr = "A1CenterX";
    private static final String boardMetadataA1CenterYAttr = "A1CenterY";
    private static final String boardMetadataHexWidthAttr = "hexWidth";
    private static final String boardMetadataHexHeightAttr = "hexHeight";
    private static final String boardMetadataAltHexGrainAttr = "altHexGrain";
    private static final String boardMetadataSnapPointAttr = "snapScale";

    // added to handle non-geo hex grid configurations; can be left null for geo type boards
    private static final String boardMetadataHexGridConfigAttr = "HexGridConfig";
    private String hexgridconfig;

    private static final String buildingTypesElement = "buildingTypes";
    private static final String buildingTypeElement = "buildingType";
    private static final String buildingTypeHexNameAttr = "hexName";
    private static final String buildingTypeBuildingTypeNameAttr = "buildingTypeName";

    private static final String slopeElement = "slope";
    private static final String slopesElement = "slopes";
    private static final String slopeHexNameAttribute = "hex";
    private static final String slopeHexsidesAttribute = "hexsides";

    // code added by DR to manage railway embankment on board RB and partial orchards on Dinant
    private static final String rrembankmentElement = "rrembankment";
    private static final String rrembankmentsElement = "rrembankments";
    private static final String rrembankmentHexNameAttribute = "hex";
    private static final String rrembankmentHexsidesAttribute = "hexsides";
    private static final String partialorchardElement = "partialorchard";
    private static final String partialorchardsElement = "partialorchards";
    private static final String partialorchardHexNameAttribute = "hex";
    private static final String partialorchardHexsidesAttribute = "hexsides";
    // flags for board specific metadata
    private boolean boardSpecificColors = false;
    private boolean boardSpecificColorSSR = false;
    private boolean boardSpecificOverlayRules = false;
    private boolean boardSpecificUnderlayRules = false; // Needed?

    public BoardMetadata(SharedBoardMetadata sharedBoardMetadata){

        // we copy the shared board metadata as this board may change it
        // when the board meta data is parsed it will add to or replace the shared metadata
        boardColors.putAll(sharedBoardMetadata.getBoardColors());
        colorSSRules.putAll(sharedBoardMetadata.getColorSSRules());
        colorToVASLColorName.putAll(sharedBoardMetadata.getColorToVASLColorName());
        overlaySSRules.putAll(sharedBoardMetadata.getOverlaySSRules());
        underlaySSRules.putAll(sharedBoardMetadata.getUnderlaySSRules());

    }

    /**
     * Parses a board metadata file
     * @param metadata an <code>InputStream</code> for the board metadata XML file
     * @throws JDOMException
     */
    public void parseBoardMetadataFile(InputStream metadata) throws JDOMException {


        SAXBuilder parser = new SAXBuilder();

        try {

            // the root element will be the boardMetadata element
            Document doc = parser.build(metadata);
            Element root = doc.getRootElement();


            if (root.getName().equals(boardMetadataElement)){

                // read the board-level metadata
                width = root.getAttribute(boardMetadataWidthAttr).getIntValue();
                height = root.getAttribute(boardMetadataHeightAttr).getIntValue();
                hasHills = root.getAttribute(boardMetadataHasHillsAttr).getBooleanValue();
                boardImageFileName = root.getAttributeValue(boardMetadataImageFileNameAttr);
                name = root.getAttributeValue(boardMetadataNameAttr);
                version = root.getAttributeValue(boardMetadataVersionAttr);
                versionDate = root.getAttributeValue(boardMetadataVersionDateAttr);
                author = root.getAttributeValue(boardMetadataAuthorAttr);

                // optional attributes
                if(root.getAttribute(boardMetadataA1CenterXAttr)!= null){
                    A1CenterX = root.getAttribute(boardMetadataA1CenterXAttr).getDoubleValue();
                }
                if(root.getAttribute(boardMetadataA1CenterYAttr) != null){
                    A1CenterY = root.getAttribute(boardMetadataA1CenterYAttr).getDoubleValue();
                }
                if(root.getAttribute(boardMetadataHexHeightAttr) != null){
                    hexHeight = root.getAttribute(boardMetadataHexHeightAttr).getDoubleValue();
                }
                if(root.getAttribute(boardMetadataHexWidthAttr) != null){
                    hexWidth = root.getAttribute(boardMetadataHexWidthAttr).getDoubleValue();
                }
                if(root.getAttribute(boardMetadataAltHexGrainAttr) != null){
                    altHexGrain = root.getAttribute(boardMetadataAltHexGrainAttr).getBooleanValue();
                }
                if(root.getAttribute(boardMetadataSnapPointAttr) != null){
                    snapScale = root.getAttribute(boardMetadataSnapPointAttr).getIntValue();
                }
                // added to handle non-geo hex grid configurations; can be left null for geo type boards
                if(root.getAttribute(boardMetadataHexGridConfigAttr) != null){
                    hexgridconfig = root.getAttributeValue(boardMetadataHexGridConfigAttr);
                }


                // parse the board metadata elements
                parseBuildingTypes(root.getChild(buildingTypesElement));
                parseColors(root.getChild(colorsElement), true); // replace existing colors with those in board metadata
                parseColorSSRules(root.getChild(colorSSRulesElement));
                parseOverlaySSRules(root.getChild(overlaySSRulesElement));
                parseSlopes(root.getChild(slopesElement));
                // code added by DR to handle RB rr embankments and partialOrchards
                parseRrembankments(root.getChild(rrembankmentsElement));
                parsePartialOrchards(root.getChild(partialorchardsElement));
                // set flags indicating board-specific information
                boardSpecificColors = root.getChild(colorSSRulesElement) != null &&
                        root.getChild(colorSSRulesElement).getChildren().size() > 0;
                boardSpecificColorSSR = root.getChild(colorSSRulesElement) != null &&
                        root.getChild(colorSSRulesElement).getChildren().size() >0;
                boardSpecificOverlayRules = root.getChild(overlaySSRulesElement) != null &&
                        root.getChild(overlaySSRulesElement).getChildren().size() > 0;
            }

        } catch (IOException e) {
            System.err.println("Error reading the board archive metadata");
            e.printStackTrace(System.err);
        }
    }

    /**
     * Parses the board building types
     * @param element the buildingTypes element
     * @throws JDOMException
     */
    private void parseBuildingTypes(Element element) throws JDOMException {

        // make sure we have the right element
        assertElementName(element, buildingTypesElement);

        for(Element e: element.getChildren()) {

            // ignore any child elements that are not buildingType
            if(e.getName().equals(buildingTypeElement)){

                buildingTypes.put(
                        e.getAttributeValue(buildingTypeHexNameAttr),
                        e.getAttributeValue(buildingTypeBuildingTypeNameAttr)
                );
            }
        }
    }

    /**
     * Parses the board slopes
     * @param element the slopeElement element
     * @throws JDOMException
     */
    private void parseSlopes(Element element) throws JDOMException {

        // make sure we have the right element
        if (element != null) {
            assertElementName(element, slopesElement);

            for(Element e: element.getChildren()) {

                // ignore any child elements that are not slopes
                if(e.getName().equals(slopeElement)){

                    slopes.addSlope(
                            e.getAttributeValue(slopeHexNameAttribute),
                            parseSlopeHexsides(e.getAttributeValue(slopeHexsidesAttribute))
                    );
                }
            }
        }
    }

    // code added by DR to handle RB rr embankments and Partial Orchards
    private void parseRrembankments(Element element) throws JDOMException {

        // make sure we have the right element
        if (element != null) {
            assertElementName(element, rrembankmentsElement);

            for(Element e: element.getChildren()) {

                // ignore any child elements that are not slopes
                if(e.getName().equals(rrembankmentElement)){

                    rbrrembankments.addSpecialHexside(
                            e.getAttributeValue(rrembankmentHexNameAttribute),
                            parseRrembankmentHexsides(e.getAttributeValue(rrembankmentHexsidesAttribute))
                    );
                }
            }
        }
    }
    private void parsePartialOrchards(Element element) throws JDOMException {

        // make sure we have the right element
        if (element != null) {
            assertElementName(element, partialorchardsElement);

            for(Element e: element.getChildren()) {

                // ignore any child elements that are not slopes
                if(e.getName().equals(partialorchardElement)){

                    partialOrchards.addSpecialHexside(
                            e.getAttributeValue(partialorchardHexNameAttribute),
                            parseRrembankmentHexsides(e.getAttributeValue(partialorchardHexsidesAttribute))
                    );
                }
            }
        }
    }
    /**
     * Translates a string of hexside numbers into an array of boolean flags
     * @param hexsides the hexside string - e.g. 012345
     * @return the boolean flags
     * @throws JDOMException
     */
    private boolean[] parseSlopeHexsides (String hexsides) throws JDOMException {
        boolean[] hexsideFlags = new boolean[6];

        try {
            for (int x = 0; x < hexsides.length() && x < 6; x++) {
                char c = hexsides.charAt(x);
                hexsideFlags[(int) c - (int) '0'] = true;
            }
            return hexsideFlags;
        } catch (Exception e) {
            throw new JDOMException("Invalid " + slopeHexsidesAttribute +" attribute in board metadata: " + hexsides);
        }
    }

    // code added by DR to handle RB rr embankments and Partial Orchards
    private boolean[] parseRrembankmentHexsides (String hexsides) throws JDOMException {
        boolean[] hexsideFlags = new boolean[6];

        try {
            for (int x = 0; x < hexsides.length() && x < 6; x++) {
                char c = hexsides.charAt(x);
                hexsideFlags[(int) c - (int) '0'] = true;
            }
            return hexsideFlags;
        } catch (Exception e) {
            throw new JDOMException("Invalid " + rrembankmentHexsidesAttribute +" attribute in board metadata: " + hexsides);
        }
    }
    private boolean[] parsePartialOrchardHexsides (String hexsides) throws JDOMException {
        boolean[] hexsideFlags = new boolean[6];

        try {
            for (int x = 0; x < hexsides.length() && x < 6; x++) {
                char c = hexsides.charAt(x);
                hexsideFlags[(int) c - (int) '0'] = true;
            }
            return hexsideFlags;
        } catch (Exception e) {
            throw new JDOMException("Invalid " + partialorchardHexsidesAttribute +" attribute in board metadata: " + hexsides);
        }
    }
    /**
     * get the VASL color name for the given color
     * @param color the color
     * @return the VASL color name
     */
    public String getVASLColorName(Color color){
        return colorToVASLColorName.get(color);
    }

    /**
     *
     * @return the list of building terrain types by hex
     */
    public HashMap<String, String> getBuildingTypes(){

        return buildingTypes;
    }

    /**
     * @return the width of the board in hexes
     */
    public int getBoardWidth() {

        return width;
    }

    /**
     * @return the height of the board in hexes
     */
    public int getBoardHeight() {

        return height;
    }

    /**
     * @return map has hills?
     */
    public boolean hasHills() {

        return hasHills;
    }

    /**
     * @return the board image file name. E.g. bd01.gif
     */
    public String getBoardImageFileName() {

        return boardImageFileName;
    }

    /**
     * @return the map name. E.g. bd01
     */
    public String getName() {
        return name;
    }

    /**
     * @return the map version. E.g. 5.3
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return the map version edit date
     */
    public String getVersionDate() {
        return versionDate;
    }

    /**
     * @return the map author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * @return x location of the A1 center hex dot
     */
    public double getA1CenterX() {
        return A1CenterX;
    }

    /**
     * @return y location of the A1 center hex dot
     */
    public double getA1CenterY() {
        return A1CenterY;
    }

    /**
     * @return hex width in pixels
     */
    public double getHexWidth() {
        return hexWidth;
    }

    /**
     * @return hex height in pixels
     */
    public double getHexHeight() {
        return hexHeight;
    }

    /**
     * @return the hex snap scale
     */
    public int getSnapScale() {
        return snapScale;
    }

    /**
     * @return true if upper left hex is A0, B1 is higher, etc.
     */
    public boolean isAltHexGrain() {
        return altHexGrain;
    }

    /**
     * @return the scenario-specific overlay rules
     */
    public Slopes getSlopes(){ return slopes;}

    // code added by DR to handle RB rr embankments and Partial Orchards
    public RBrrembankments getRBrrembankments(){ return rbrrembankments;}

    public PartialOrchards getPartialOrchards(){ return partialOrchards;}

    /**
     * @return true if board metadata contains custom colors
     */
    public boolean hasBoardSpecificColors() {
        return boardSpecificColors;
    }

    /**
     * @return true if board metadata contains custom color SSR
     */
    public boolean hasBoardSpecificColorSSR() {
        return boardSpecificColorSSR;
    }

    /**
     * @return true if board metadata contains custom overlay rules
     */
    public boolean hasBoardSpecificOverlayRules() {
        return boardSpecificOverlayRules;
    }

    // added to handle non-geo hex grid configurations; can be left null for geo type boards
    public String getHexGridConfig(){
        return hexgridconfig;
    }
}
