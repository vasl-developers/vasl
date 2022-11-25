package VASL.build.module.dice;

import VASL.build.module.ASLChatter;

public class ASLColoredDie implements ASLDie{
  DieColor color;
  public ASLColoredDie(DieColor color){
    this.color = color;
  }
  @Override
  public String getDieHTMLFragment(int roll) {
    return String.format(m_strFileNameFormat, String.valueOf(roll), color);
  }
}
