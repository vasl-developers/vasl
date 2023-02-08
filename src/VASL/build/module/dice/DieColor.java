package VASL.build.module.dice;

public enum DieColor {

    WHITE("White"){
    public String toString(){return "W";}
    },
    BLACK("Black"){
      public String toString(){return "B";}
    },
    BLUE("Blue"){
      public String toString(){return "DB";}
    },
    RED("Red"){
      public String toString(){return "R";}
    },
    GREEN("Green"){
      public String toString(){return "DG";}
    },
    YELLOW("Yellow"){
      public String toString(){return "Y";}
    },
    CYAN("Cyan"){
      public String toString(){return "C";}
    },
    ORANGE("Orange"){
      public String toString(){return "O";}
    },
    PURPLE("Purple"){
      public String toString(){return "P";}
    },
    SWEDISH("Swedish"){
        public String toString(){return "SV";}
    },
    RUSSIAN("Russian"){
        public String toString(){return "RU";}
    },
    JAPANESE("Japanese"){
        public String toString(){return "JA";}
    },
    ITALIAN("Italian"){
        public String toString(){return "IT";}
    },
    GERMAN("German"){
        public String toString(){return "GE";}
    },
    FRENCH("French"){
        public String toString(){return "FR";}
    },
    FINNISH("Finnish"){
        public String toString(){return "FI";}
    },
    BRITISH("British"){
        public String toString(){return "BR";}
    },
    AMERICAN("American"){
        public String toString(){return "AM";}
    },
    AXISM("AxisM"){
        public String toString(){return "AX";}
    },
    ALLIEDM("AlliedM"){
        public String toString(){return "AL";}
    };
    private String color;

    public String getColor() {
      return color;
    }

    DieColor(String color) {
      this.color = color;
    }
  public static DieColor getEnum(String value) {
    for (DieColor d : values()) {
      if (d.getColor().equalsIgnoreCase(value)) {
        return d;
      }
    }
    // non enum value stored - return white.
    return WHITE;
  }
}

