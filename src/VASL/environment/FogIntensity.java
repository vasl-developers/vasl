package VASL.environment;

public enum FogIntensity {
  LIGHT ("Light Fog"),
  MODERATE("Moderate Fog"),
  DENSE("Heavy Fog"){
    @Override
    public FogIntensity next() {
      return LIGHT;
    };
  };
  private String fogDescription;

  FogIntensity(String s) {
    fogDescription = s;
  }

  public String toString() {
    return this.fogDescription;
  }

  public FogIntensity next() {
    return values()[ordinal() + 1];
  }
}
