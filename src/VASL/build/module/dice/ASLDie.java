package VASL.build.module.dice;

public interface ASLDie {

  final static String DIE_FILE_NAME_FORMAT = "DC%s_%s.png";

  public String getDieHTMLFragment(final int roll);
}
