package VASL.environment;

public enum SunBlindnessLevel {
  NONE ("No Sun Blindness"),
  EARLY_MORNING_SUN_BLINDNESS("Early Morning Sun Blindness (F11.611) +2 => E"),
  LATE_AFTERNOON_SUN_BLINDNESS ("Late Afternoon Sun Blindness (F11.612) +2 => W")
  {
    @Override
    public SunBlindnessLevel next() {
      return NONE;
    }
  };

  private String sunBlindnessDescription;

  SunBlindnessLevel(String s) {
    sunBlindnessDescription = s;
  }

  public String toString() {
    return this.sunBlindnessDescription;
  }
  public static SunBlindnessLevel getSunBLevel(String s) {
    switch (s) {
      case "NONE":
        return SunBlindnessLevel.NONE;
      case "EARLY_MORNING_SUN_BLINDNESS":
        return SunBlindnessLevel.EARLY_MORNING_SUN_BLINDNESS;
      case "LATE_AFTERNOON_SUN_BLINDNESS":
        return SunBlindnessLevel.LATE_AFTERNOON_SUN_BLINDNESS;
      default:
        return SunBlindnessLevel.NONE;
    }
  }
  public SunBlindnessLevel next() {
    return values()[ordinal() + 1];
  }
}
