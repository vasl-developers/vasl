package VASL.exception;

import VASL.build.module.ASLChatter;

public class ASLDiceException extends RuntimeException{
    public ASLDiceException( String message) {
      super(message);
    }
}
