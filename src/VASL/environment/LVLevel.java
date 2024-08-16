package VASL.environment;

public enum LVLevel {
  NONE ("No LV"),
  SHADE_ONLY("LV Shading Only"),
  DAWN_DUSK ("+1 LV (E3.1)"),
  MIST("Mist (E3.32) +0/+1/..."),
  RAIN("Rain (E3.51) +0/+1/..."),
  HEAVY_RAIN("Heavy Rain (E3.51) +1/+2/..." ),
  SNOW("Falling Snow (E3.71) +0/+1/..."),
  HEAVY_SNOW("Heavy Falling Snow(E3.71) +1/+2/...")
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

  public static LVLevel getLVLevel(String s){
    switch (s) {
      case "NONE":
        return LVLevel.NONE;
      case "SHADE_ONLY":
        return LVLevel.SHADE_ONLY;
      case "DAWN_DUSK":
        return LVLevel.DAWN_DUSK;
      case "MIST":
        return LVLevel.MIST;
      case "RAIN":
        return LVLevel.RAIN;
      case "HEAVY_RAIN":
        return LVLevel.HEAVY_RAIN;
      case "SNOW":
        return LVLevel.SNOW;
      case "HEAVY_SNOW":
        return LVLevel.HEAVY_SNOW;
      default:
        return LVLevel.NONE;

    }
  }
}
