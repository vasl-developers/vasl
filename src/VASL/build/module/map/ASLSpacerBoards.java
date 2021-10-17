package VASL.build.module.map;

import java.util.Arrays;
import java.util.List;

public class ASLSpacerBoards {
  private final static List<String> spacerBoards = Arrays.asList("bdNUL", "bdNULV");

  public static boolean isSpacerBoard(final String boardName) {
    return spacerBoards.contains(boardName);
  }
}
