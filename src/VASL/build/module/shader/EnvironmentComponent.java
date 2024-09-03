package VASL.build.module.shader;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.tools.SequenceEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: rename as EnvironmentBuildable? EnvironmentCommandEncoder?
public class EnvironmentComponent  extends AbstractBuildable implements CommandEncoder {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentComponent.class);

    private final char ENCODING_DELIM = '|';
    private final String COMMAND_PREFIX = "command.shader.";
    private final String COMMAND_TYPE_NIGHT = "night";
    private final String COMMAND_TYPE_DUST = "dust";
    private final String COMMAND_TYPE_FOG = "fog";
    private final String COMMAND_TYPE_LV = "low_vis";
    private final String COMMAND_TYPE_HEAT = "heat";
    private final String COMMAND_TYPE_SUN = "sun_bln";

    // Use GameComponent set the initial conditions based on the last known setting

    // region GameComponent
//    @Override
//    public void setup(boolean gameStarting) {
//        // the "game" is started when a save file is loaded, and then game is NOT starting
//        // when the map becomes visible so this isn't useful to us as a reset point.
//    }
//
//    @Override
//    public Command getRestoreCommand() {
//        GameModule gm = GameModule.getGameModule();
//        // try to reset to off when it seems correct
//        if (gm.getGameFileMode() == GameModule.GameFileMode.NEW_GAME) {
//            return null;
//        }
//
//        Environment env = new Environment();
//
//        Command restoreCommand = new NullCommand();
//
//        if (env.isNight()) {
//            logger.trace("night: activate");
//            restoreCommand.append(new ActivateNightShaderCommand());
//        } else {
//            logger.trace("night: disabled");
//            restoreCommand.append(new DeactivateNightShaderCommand());
//        }
//
//        if (env.isDust()) {
//            logger.trace("dust: activate");
//            restoreCommand.append(new ActivateDustShaderCommand());
//        } else {
//            logger.trace("dust: disabled");
//            restoreCommand.append(new DeactivateDustShaderCommand());
//        }
//
//        if (env.isFog()) {
//            logger.trace("fog: activate");
//            restoreCommand.append(new ActivateFogShaderCommand());
//        } else {
//            logger.trace("fog: disabled");
//            restoreCommand.append(new DeactivateFogShaderCommand());
//        }
//
//        if (env.isHeatHaze()) {
//            logger.trace("heat: activate");
//            restoreCommand.append(new ActivateHeatHazeShaderCommand());
//        } else {
//            logger.trace("heat: disabled");
//            restoreCommand.append(new DeactivateHeatHazeShaderCommand());
//        }
//
//        if (env.isLV()) {
//            logger.trace("lv: activate");
//            restoreCommand.append(new ActivateLowVisibilityShaderCommand());
//        } else {
//            logger.trace("lv: disabled");
//            restoreCommand.append(new DeactivateLowVisibilityShaderCommand());
//        }
//
//        if (env.isSunBlindness()) {
//            logger.trace("sb: activate");
//            restoreCommand.append(new ActivateSunBlindnessShaderCommand());
//        } else {
//            logger.trace("sb: disabled");
//            restoreCommand.append(new DeactivateSunBlindnessShaderCommand());
//        }
//
//        return restoreCommand;
//    }
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
        //gm.getGameState().addGameComponent(this);
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
        if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_NIGHT)) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateNightShaderCommand();
                }
                return new DeactivateNightShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_DUST)) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateDustShaderCommand();
                }
                return new DeactivateDustShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_HEAT)) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateHeatHazeShaderCommand();
                }
                return new DeactivateHeatHazeShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_FOG)) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateFogShaderCommand();
                }
                return new DeactivateFogShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_LV)) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateLowVisibilityShaderCommand();
                }
                return new DeactivateLowVisibilityShaderCommand();
            }
        } else if (shaderType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_SUN)) {
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
            shaderType = COMMAND_TYPE_NIGHT;
        } else if (command instanceof DeactivateNightShaderCommand) {
            encoder.append(false);
            shaderType = COMMAND_TYPE_NIGHT;
        } else if (command instanceof ActivateDustShaderCommand) {
            encoder.append(true);
            shaderType = COMMAND_TYPE_DUST;
        } else if (command instanceof DeactivateDustShaderCommand) {
            encoder.append(false);
            shaderType = COMMAND_TYPE_DUST;
        } else if (command instanceof ActivateHeatHazeShaderCommand) {
            encoder.append(true);
            shaderType = COMMAND_TYPE_HEAT;
        } else if (command instanceof DeactivateHeatHazeShaderCommand) {
            encoder.append(false);
            shaderType = COMMAND_TYPE_HEAT;
        } else if (command instanceof ActivateFogShaderCommand) {
            encoder.append(true);
            shaderType = COMMAND_TYPE_FOG;
        } else if (command instanceof DeactivateFogShaderCommand) {
            encoder.append(false);
            shaderType = COMMAND_TYPE_FOG;
        } else if (command instanceof ActivateLowVisibilityShaderCommand) {
            encoder.append(true);
            shaderType = COMMAND_TYPE_LV;
        } else if (command instanceof DeactivateLowVisibilityShaderCommand) {
            encoder.append(false);
            shaderType = COMMAND_TYPE_LV;
        } else if (command instanceof ActivateSunBlindnessShaderCommand) {
            encoder.append(true);
            shaderType = COMMAND_TYPE_SUN;
        } else if (command instanceof DeactivateSunBlindnessShaderCommand) {
            encoder.append(false);
            shaderType = COMMAND_TYPE_SUN;
        } else {
            return null;
        }
        return COMMAND_PREFIX + shaderType + ENCODING_DELIM + encoder.getValue();
    }
    // endregion
}
