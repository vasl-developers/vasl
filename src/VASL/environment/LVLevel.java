package VASL.environment;

public enum LVLevel {
  NONE ("No LV"),
  SHADE_ONLY("LV Shading Only"),
  DAWN_DUSK ("+1 LV (E3.1)"),
  //FOG("Fog (E3.31) -  (fog intensity and map level not yet implemented)"),
  MIST("Mist (E3.32) +0/+1/..."),
  RAIN("Rain (E3.51) +0/+1/..."),
  HEAVY_RAIN("Heavy Rain (E3.51) +1/+2/..." ),
  SNOW("Falling Snow (3.71) +0/+1/..."),
  HEAVY_SNOW("Heavy Falling Snow(3.71) +1/+2/...")

  //EARLY_MORNING_SUN_BLINDNESS("Sun Blindness(AM) (F11.611)"),
  //LATE_AFTERNOON_SUN_BLINDNESS("Sun Blindness(PM) (F11.612)"),
  //HEAT_HAZE("Heat Haze (F11.62)"),
  //INTENSE_HEAT_HAZE("Intense Heat Haze (F11.621)")
  {
    @Override
    public LVLevel next() {
      return NONE;
    };
  };

  private String lvDescription;

  LVLevel(String s) {
    lvDescription = s;
  }

  public String toString() {
    return this.lvDescription;
  }

  public LVLevel next() {
    return values()[ordinal() + 1];
  }
}
