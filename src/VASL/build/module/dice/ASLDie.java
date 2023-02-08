package VASL.build.module.dice;

import VASL.build.module.ASLChatter;

public interface ASLDie {

  final static String m_strFileNameFormat = "DC%s_%s.png";

  public String getDieHTMLFragment(final int roll);
}
