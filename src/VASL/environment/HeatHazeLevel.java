package VASL.environment;

public enum HeatHazeLevel {
  NONE ("No Heat Haze"),
  HEAT_HAZE("Heat Haze (F11.62)"),
  INTENSE_HEAT_HAZE("Intense Heat Haze (F11.621)")
  {
    @Override
    public HeatHazeLevel next() {
      return NONE;
    };
  };

  private String heatHazeDescription;

  HeatHazeLevel(String s) {
    heatHazeDescription = s;
  }

  public String toString() {
    return this.heatHazeDescription;
  }

  public HeatHazeLevel next() {
    return values()[ordinal() + 1];
  }
}
