package VASL.LOS;

import VASL.build.module.ScenInfo;
import VASSAL.build.GameModule;
import VASSAL.build.module.properties.MutableProperty;

public class LOS {

    public static final String PERSIST_ELEVATION_PROPERTY = "PersistElevation";

    public LOS(){

    }

    public String tryGetMutableProperty(String propertyName, String valueIfMissing) {
        final String ERROR_NO_PROPERTY = "buildFile does not contain expected GlobalProperty " + propertyName;
        GameModule gm = GameModule.getGameModule();
        MutableProperty propertyObj = gm.getMutableProperty(propertyName);
        if (propertyObj == null) {
            gm.warn(ERROR_NO_PROPERTY);
            return valueIfMissing;
        }
        return propertyObj.getPropertyValue();
    }

    public boolean isPersistElevation() {
        return Boolean.parseBoolean(tryGetMutableProperty(PERSIST_ELEVATION_PROPERTY, Boolean.FALSE.toString()));
    }

}
