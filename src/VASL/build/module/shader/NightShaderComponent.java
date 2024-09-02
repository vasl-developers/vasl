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

public class NightShaderComponent  extends AbstractBuildable implements GameComponent, CommandEncoder {

    private static final Logger logger = LoggerFactory.getLogger(NightShaderComponent.class);

    private final char ENCODING_DELIM = '|';
    private final String COMMAND_PREFIX = "command.shader.";

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
            return new DeactivateNightShaderCommand();
        }

        Environment env = new Environment();

        Command restoreCommand;
        if (env.isNight()) {
            logger.trace("restore: activate");
            restoreCommand = new ActivateNightShaderCommand();
        } else {
            logger.trace("restore: disabled");
            restoreCommand = new DeactivateNightShaderCommand();
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
    // Not currently used since we don't need to synchronize between clients
    // region CommandEncoder
    @Override
    public Command decode(String s) {
        if (!s.startsWith(COMMAND_PREFIX)) return null;
        SequenceEncoder.Decoder decoder = new SequenceEncoder.Decoder(s, ENCODING_DELIM);
        String shaderType = decoder.nextToken();
        if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + "night")) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateNightShaderCommand();
                }
                return new DeactivateNightShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + "dust")) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateDustShaderCommand();
                }
                return new DeactivateDustShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + "heat")) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateHeatHazeShaderCommand();
                }
                return new DeactivateHeatHazeShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + "fog")) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateFogShaderCommand();
                }
                return new DeactivateFogShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + "low_vis")) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateLowVisibilityShaderCommand();
                }
                return new DeactivateLowVisibilityShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + "sun_bln")) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateSunBlindnessShaderCommand();
                }
                return new DeactivateSunBlindnessShaderCommand();
            }
        }
        return null;
    }

    @Override
    public String encode(Command command) {
        if (!(command instanceof ShaderCommandConfig)) return null;
        SequenceEncoder encoder = new SequenceEncoder(ENCODING_DELIM);
        String shaderType;
        if (command instanceof ActivateNightShaderCommand) {
            encoder.append(true);
            shaderType = "night";
        } else if (command instanceof DeactivateNightShaderCommand) {
            encoder.append(false);
            shaderType = "night";
        } else if (command instanceof ActivateDustShaderCommand) {
            encoder.append(true);
            shaderType = "dust";
        } else if (command instanceof DeactivateDustShaderCommand) {
            encoder.append(false);
            shaderType = "dust";
        } else if (command instanceof ActivateHeatHazeShaderCommand) {
            encoder.append(true);
            shaderType = "heat";
        } else if (command instanceof DeactivateHeatHazeShaderCommand) {
            encoder.append(false);
            shaderType = "heat";
        } else if (command instanceof ActivateFogShaderCommand) {
            encoder.append(true);
            shaderType = "fog";
        } else if (command instanceof DeactivateFogShaderCommand) {
            encoder.append(false);
            shaderType = "fog";
        } else if (command instanceof ActivateLowVisibilityShaderCommand) {
            encoder.append(true);
            shaderType = "low_vis";
        } else if (command instanceof DeactivateLowVisibilityShaderCommand) {
            encoder.append(false);
            shaderType = "low_vis";
        } else if (command instanceof ActivateSunBlindnessShaderCommand) {
            encoder.append(true);
            shaderType = "sun_bln";
        } else if (command instanceof DeactivateSunBlindnessShaderCommand) {
            encoder.append(false);
            shaderType = "sun_bln";
        } else {
            return null;
        }
        return COMMAND_PREFIX + shaderType + ENCODING_DELIM + encoder.getValue();
    }
    // endregion
}
