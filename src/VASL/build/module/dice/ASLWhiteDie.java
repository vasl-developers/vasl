package VASL.build.module.dice;

import VASL.build.module.ASLChatter;

public class ASLWhiteDie implements ASLDie{
  DieColor color;
  public ASLWhiteDie(DieColor color){
    this.color = color;
  }
  @Override
  public String getDieHTMLFragment(int roll) {
    return getWhiteDiceFile(roll);
  }
  private String getWhiteDiceFile(int roll){
    switch (roll){
      case 1:
        return "DC1_W.png";
      case 2:
        return "DC2_W.png";
      case 3:
        return "DC3_W.png";
      case 4:
        return "DC4_W.png";
      case 5:
        return "DC5_W.png";
      case 6:
        return "DC6_W.png";
      default:
        return null;
    }
  }
}
