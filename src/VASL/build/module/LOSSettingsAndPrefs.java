package VASL.build.module;

import VASL.LOS.Map.ActivatePersistElevationCommand;
import VASL.LOS.Map.DeactivatePersistElevationCommand;
import VASL.LOS.Map.LOSCommandConfig;
import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.tools.SequenceEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LOSSettingsAndPrefs extends AbstractBuildable implements CommandEncoder {

    private static final Logger logger = LoggerFactory.getLogger(LOSSettingsAndPrefs.class);

    private final char ENCODING_DELIM = '|';
    private final String COMMAND_PREFIX = "command.los.";
    private final String COMMAND_TYPE_PERSISTELEVATION = "persistelevation";

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
        gm.addCommandEncoder(this);
    }
    // endregion

    // This is how to serialize/deserialize the command to set the persist elevation
    // region CommandEncoder
    @Override
    public Command decode(String s) {
        if (!s.startsWith(COMMAND_PREFIX)) return null;
        SequenceEncoder.Decoder decoder = new SequenceEncoder.Decoder(s, ENCODING_DELIM);
        String prefType = decoder.nextToken();
        if (prefType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_PERSISTELEVATION)) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivatePersistElevationCommand();
                }
                return new DeactivatePersistElevationCommand();
            }
        }
        // Keep this as an example if need to create more LOS Settings or Preferences
        /*else if (prefType.equalsIgnoreCase(COMMAND_PREFIX + COMMAND_TYPE_DUST)) {
            if (decoder.hasMoreTokens()) {
                String firstValueIsOnOrOff = decoder.nextToken();
                boolean visible = Boolean.parseBoolean(firstValueIsOnOrOff);
                if (visible) {
                    return new ActivateDustShaderCommand();
                }
                return new DeactivateDustShaderCommand();
            }
        }*/
        return null;
    }

    @Override
    public String encode(Command command) {
        if (!(command instanceof LOSCommandConfig)) return null;
        SequenceEncoder encoder = new SequenceEncoder(ENCODING_DELIM);
        String prefType;
        if (command instanceof ActivatePersistElevationCommand) {
            encoder.append(true);
            prefType = COMMAND_TYPE_PERSISTELEVATION;
        } else if (command instanceof DeactivatePersistElevationCommand) {
            encoder.append(false);
            prefType = COMMAND_TYPE_PERSISTELEVATION;
        } else {
            return null;
        }
        return COMMAND_PREFIX + prefType + ENCODING_DELIM + encoder.getValue();

        // Keep this as an example if need to create more LOS Settings or Preferences
        /*else if (command instanceof ActivateDustShaderCommand) {
            encoder.append(true);
            shaderType = COMMAND_TYPE_DUST;
        } else if (command instanceof DeactivateDustShaderCommand) {
            encoder.append(false);
            shaderType = COMMAND_TYPE_DUST;
        }*/

    }
    // endregion
}
