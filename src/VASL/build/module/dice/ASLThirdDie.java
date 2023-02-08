package VASL.build.module.dice;

import VASL.build.module.ASLChatter;

public class ASLThirdDie implements ASLDie{
  DieColor color;
  public ASLThirdDie(DieColor color){
    this.color = color;
  }
  @Override
  public String getDieHTMLFragment(int roll) {
    return String.format(m_strFileNameFormat, String.valueOf(roll), color);
  }
}
