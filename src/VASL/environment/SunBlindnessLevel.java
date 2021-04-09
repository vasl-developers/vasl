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

  public SunBlindnessLevel next() {
    return values()[ordinal() + 1];
  }
}
