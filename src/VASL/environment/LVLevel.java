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
}
