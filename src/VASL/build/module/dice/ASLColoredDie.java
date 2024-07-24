package VASL.build.module.dice;

public class ASLColoredDie implements ASLDie{
  DieColor color;
  public ASLColoredDie(DieColor color){
    this.color = color;
  }
  @Override
  public String getDieHTMLFragment(int roll) {
    return String.format(DIE_FILE_NAME_FORMAT, String.valueOf(roll), color);
  }
}
