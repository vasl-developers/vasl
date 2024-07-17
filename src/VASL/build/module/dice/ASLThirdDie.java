package VASL.build.module.dice;

public class ASLThirdDie implements ASLDie{
  DieColor color;
  public ASLThirdDie(DieColor color){
    this.color = color;
  }
  @Override
  public String getDieHTMLFragment(int roll) {
    return String.format(DIE_FILE_NAME_FORMAT, String.valueOf(roll), color);
  }
}
