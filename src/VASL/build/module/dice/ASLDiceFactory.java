package VASL.build.module.dice;

import VASL.build.module.ASLChatter;
import VASL.exception.ASLDiceException;

import java.util.HashMap;

public class ASLDiceFactory {
  private final HashMap<ASLChatter.DiceType, DieColor> colors = new HashMap<>();

  public ASLDiceFactory() {
    //default dice colors if none set.
    colors.put(ASLChatter.DiceType.WHITE, DieColor.WHITE);
    colors.put(ASLChatter.DiceType.COLORED, DieColor.BLACK);
    colors.put(ASLChatter.DiceType.OTHER_DUST, DieColor.ORANGE);
    colors.put(ASLChatter.DiceType.SINGLE, DieColor.RED);
  }

  public void setDieColor(ASLChatter.DiceType type, DieColor color) {
    colors.put(type, color);
  }
  public ASLDie getASLDie(final ASLChatter.DiceType type) {
    DieColor color = colors.get(type);
    switch (type) {
      case WHITE: {
        return new ASLWhiteDie(color);
      }
      case OTHER_DUST: {
        return new ASLThirdDie(color);
      }
      case SINGLE:
      case COLORED: {
        return new ASLColoredDie(color);
      }
      default:
        //don't handle - crash if we can't create dice.
        throw new ASLDiceException("Cannot create a die of type "+ type);
    }
  }
}
