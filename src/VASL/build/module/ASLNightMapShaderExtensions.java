package VASL.build.module;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.tools.SequenceEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

public class ASLNightMapShaderExtensions extends AbstractBuildable implements GameComponent, CommandEncoder {

    private static final Logger logger = LoggerFactory.getLogger(ASLNightMapShaderExtensions.class);
    public static final String LAST_KNOWN_VISIBILITY_PROPERTY = "NightShaderVisibility";
    public static final String LAST_KNOWN_LEVEL_PROPERTY = "NONE";
    public static final String DEBUG_NO_VISIBILITY_PROPERTY = "buildFile does not contain expected GlobalProperty " + LAST_KNOWN_VISIBILITY_PROPERTY;
    public static final String DEBUG_NO_LEVEL_PROPERTY = "buildFile does not contain expected GlobalProperty " + LAST_KNOWN_LEVEL_PROPERTY;
    private final char ENCODING_DELIM = '|';
    private final String COMMAND_PREFIX = "shader.config.night" + ENCODING_DELIM;
    // Use GameComponent set the initial conditions based on the last known setting

    // region GameComponent
    @Override
    public void setup(boolean gameStarting) {
        // the "game" is started when a save file is loaded, and then game is NOT starting
        // when the map becomes visible so this isn't useful to us as a reset point.
    }

    @Override
    public Command getRestoreCommand() {
        GameModule gm = GameModule.getGameModule();
        // try to reset to off when it seems correct
        if (gm.getGameFileMode() == GameModule.GameFileMode.NEW_GAME) {
            return new DectivateCommand();
        }
        // get the last known state of the shader
        MutableProperty visibilityProperty = gm.getMutableProperty(LAST_KNOWN_VISIBILITY_PROPERTY);
        if (visibilityProperty == null) {
            gm.warn(DEBUG_NO_VISIBILITY_PROPERTY);
            return null;
        }
        boolean visibilityValue = Boolean.parseBoolean(visibilityProperty.getPropertyValue());
        Command restoreCommand;
        if (visibilityValue) {
            logger.trace("restore: activate");
            restoreCommand = new ActivateCommand();
        } else {
            logger.trace("restore: disabled");
            restoreCommand = new DectivateCommand();
        }
        return restoreCommand;
    }
    //endregion

    // Use Buildable to add the GameComponent and CommandEncoder or else the Commands are never persisted to the log
    // region Buildable
    @Override
    public String[] getAttributeNames() {
        return new String[0];
    }

    @Override
    public void setAttribute(String s, Object o) {

    }

    @Override
    public String getAttributeValueString(String s) {
        return "";
    }

    @Override
    public void addTo(Buildable buildable) {
        GameModule gm = GameModule.getGameModule();
        gm.getGameState().addGameComponent(this);
        gm.addCommandEncoder(this);
    }
    // endregion

    // This is how to serialize/deserialize the command to set the shader
    // region CommandEncoder
    @Override
    public Command decode(String s) {
        if (!s.startsWith(COMMAND_PREFIX)) return null;
        SequenceEncoder.Decoder decoder = new SequenceEncoder.Decoder(s, ENCODING_DELIM);
        //noinspection unused
        String prefixTokenIgnored = decoder.nextToken(); // ignore the prefix
        if (decoder.hasMoreTokens()) {
            String firstValueIsOnOrOff = decoder.nextToken();
            boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
            if (visible) {
                return new ActivateCommand();
            }
            return new DectivateCommand();
        }
        return null;
    }

    @Override
    public String encode(Command command) {
        if (!(command instanceof ASLNightMapShader.CommandConfig)) return null;
        SequenceEncoder encoder = new SequenceEncoder(ENCODING_DELIM);
        if (command instanceof ActivateCommand) {
            encoder.append(true);
            return COMMAND_PREFIX + encoder.getValue();
        }
        encoder.append(false);
        return COMMAND_PREFIX + encoder.getValue();
    }
    // endregion

    // this interface allows us to filter encoding easier

    public static class ActivateCommand extends Command implements ASLNightMapShader.CommandConfig {

        private static final Logger logger = LoggerFactory.getLogger(ActivateCommand.class);

        @Override
        protected void executeCommand() {

            logger.trace("ASLNightMapShaderComponent$ActivateCommand.executeCommand()");

            GameModule gm = GameModule.getGameModule();
            Iterator<ASLMap> mapIterator = gm.getComponentsOf(ASLMap.class).iterator();
            if (mapIterator.hasNext()) {
                ASLMap map = mapIterator.next();
                Iterator<ASLNightMapShader> shaderIterator = map.getComponentsOf(ASLNightMapShader.class).iterator();
                if (!shaderIterator.hasNext()) {return;}

                ASLNightMapShader shaderObj = shaderIterator.next();
                shaderObj.setShadingVisibility(false);

                MutableProperty visibilityProperty = gm.getMutableProperty(LAST_KNOWN_VISIBILITY_PROPERTY);
                if (visibilityProperty == null) {
                    gm.warn(DEBUG_NO_VISIBILITY_PROPERTY);
                    logger.warn(DEBUG_NO_VISIBILITY_PROPERTY);
                    return;
                }
                visibilityProperty.setPropertyValue(Boolean.TRUE.toString()).execute();

                MutableProperty levelProperty = gm.getMutableProperty(LAST_KNOWN_LEVEL_PROPERTY);
                if (levelProperty == null) {
                    gm.warn(DEBUG_NO_LEVEL_PROPERTY);
                    logger.warn(DEBUG_NO_LEVEL_PROPERTY);
                    return;
                }
                levelProperty.setPropertyValue("NONE").execute();
            }

        }

        private ASLNightMapShader getShader() {
            GameModule gm = GameModule.getGameModule();
            Iterator<ASLMap> mapIterator = gm.getComponentsOf(ASLMap.class).iterator();
            if (mapIterator.hasNext()) {
                ASLMap map = mapIterator.next();
                Iterator<ASLNightMapShader> shaderIterator = map.getComponentsOf(ASLNightMapShader.class).iterator();
                if (!shaderIterator.hasNext()) {
                    return null;
                }
                return shaderIterator.next();
            }
            return null;
        }

        @Override
        protected Command myUndoCommand() {
            return new DectivateCommand();
        }
    }

    public static class DectivateCommand extends Command implements ASLNightMapShader.CommandConfig {

        private static final Logger logger = LoggerFactory.getLogger(DectivateCommand.class);

        @Override
        protected void executeCommand() {

            logger.trace("ASLNightMapShaderComponent$DeactivateCommand.executeCommand");

            GameModule gm = GameModule.getGameModule();
            Iterator<ASLMap> mapIterator = gm.getComponentsOf(ASLMap.class).iterator();
            if (mapIterator.hasNext()) {
                ASLMap map = mapIterator.next();
                Iterator<ASLNightMapShader> shaderIterator = map.getComponentsOf(ASLNightMapShader.class).iterator();
                if (!shaderIterator.hasNext()) {return;}

                ASLNightMapShader shaderObj = shaderIterator.next();
                shaderObj.setShadingVisibility(false);

                MutableProperty visibilityProperty = gm.getMutableProperty(LAST_KNOWN_VISIBILITY_PROPERTY);
                if (visibilityProperty == null) {
                    gm.warn(DEBUG_NO_VISIBILITY_PROPERTY);
                    logger.warn(DEBUG_NO_VISIBILITY_PROPERTY);
                    return;
                }
                visibilityProperty.setPropertyValue(Boolean.FALSE.toString()).execute();

                MutableProperty levelProperty = gm.getMutableProperty(LAST_KNOWN_LEVEL_PROPERTY);
                if (levelProperty == null) {
                    gm.warn(DEBUG_NO_LEVEL_PROPERTY);
                    logger.warn(DEBUG_NO_LEVEL_PROPERTY);
                    return;
                }
                levelProperty.setPropertyValue("NONE").execute();
            }
        }

        @Override
        protected Command myUndoCommand() {
            return new ActivateCommand();
        }
    }
}


