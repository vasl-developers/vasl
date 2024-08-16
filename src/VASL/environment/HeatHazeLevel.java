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
  public static HeatHazeLevel getHeatHLevel(String s){
    switch (s) {
      case "NONE":
        return HeatHazeLevel.NONE;
      case "HEAT_HAZE":
        return HeatHazeLevel.HEAT_HAZE;
      case "INTENSE_HEAT_HAZE":
        return HeatHazeLevel.INTENSE_HEAT_HAZE;
      default:
        return HeatHazeLevel.NONE;

    }
  }
  public HeatHazeLevel next() {
    return values()[ordinal() + 1];
  }
}
