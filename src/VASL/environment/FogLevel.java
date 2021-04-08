package VASL.environment;

public enum FogLevel {
  NONE ("No Fog"),
  LIGHT_FOGM1("Light Fog (E3.311), L-1, +1"),
  LIGHT_FOGL0("Light Fog (E3.311) L0,+1"),
  LIGHT_FOGL1("Light Fog (E3.311) L1, +1"),
  LIGHT_FOGL2("Light Fog (E3.311) L2, +1"),
  LIGHT_FOGL3("Light Fog (E3.311) L3, +1"),
  LIGHT_FOGL4("Light Fog (E3.311) L4, +1"),

  MODERATE_FOGM1("Moderate Fog (E3.311) L-1, +2"),
  MODERATE_FOGL0("Moderate Fog (E3.311) L0, +2"),
  MODERATE_FOGL1("Moderate Fog (E3.311) L1, +2"),
  MODERATE_FOGL2("Moderate Fog (E3.311) L2, +2"),
  MODERATE_FOGL3("Moderate Fog (E3.311) L3, +2"),
  MODERATE_FOGL4("Moderate Fog (E3.311) L4, +2"),

  HEAVY_FOGM1("Heavy Fog (E3.31) L-1, +3"),
  HEAVY_FOGL0("Heavy Fog (E3.31) L0, +3"),
  HEAVY_FOGL1("Heavy Fog (E3.31) L1, +3"),
  HEAVY_FOGL2("Heavy Fog (E3.31) L2, +3"),
  HEAVY_FOGL3("Heavy Fog (E3.31) L3, +3"),
  HEAVY_FOGL4("Heavy Fog (E3.31) L4, +3")
  {
    @Override
    public FogLevel next() {
      return NONE;
    };
  };

  private String fogDescription;

  FogLevel(String s) {
    fogDescription = s;
  }

  public String toString() {
    return this.fogDescription;
  }

  public FogLevel next() {
    return values()[ordinal() + 1];
  }
}
