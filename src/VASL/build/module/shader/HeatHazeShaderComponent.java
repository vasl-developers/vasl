package VASL.build.module.shader;

import VASL.environment.Environment;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.tools.SequenceEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeatHazeShaderComponent extends AbstractBuildable implements GameComponent, CommandEncoder {

    private static final Logger logger = LoggerFactory.getLogger(HeatHazeShaderComponent.class);

    private final char ENCODING_DELIM = '|';
    private final String COMMAND_PREFIX = "command.shader.heat_haze" + ENCODING_DELIM;

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
            return new DeactivateHeatHazeShaderCommand();
        }

        Environment env = new Environment();

        Command restoreCommand;
        if (env.isHeatHaze()) {
            logger.trace("restore: activate");
            restoreCommand = new ActivateHeatHazeShaderCommand();
        } else {
            logger.trace("restore: disabled");
            restoreCommand = new DeactivateHeatHazeShaderCommand();
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
        // if we ever wanted to sync MapShader state between clients, this would need to happen
        //noinspection ConstantValue
        if (false) {
            gm.addCommandEncoder(this);
        }
    }
    // endregion

    // This is how to serialize/deserialize the command to set the shader
    // Not currently used since we don't need to synchronize between clients
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
                return new ActivateHeatHazeShaderCommand();
            }
            return new DeactivateHeatHazeShaderCommand();
        }
        return null;
    }

    @Override
    public String encode(Command command) {
        if (!(command instanceof ShaderCommandConfig)) return null;
        SequenceEncoder encoder = new SequenceEncoder(ENCODING_DELIM);
        if (command instanceof ActivateHeatHazeShaderCommand) {
            encoder.append(true);
            return COMMAND_PREFIX + encoder.getValue();
        }
        encoder.append(false);
        return COMMAND_PREFIX + encoder.getValue();
    }
    // endregion
}
