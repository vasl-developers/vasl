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
      public String toString(){return "G";}
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

