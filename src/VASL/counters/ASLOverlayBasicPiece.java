package VASL.counters;

import VASSAL.counters.BasicPiece;

import java.awt.*;

public class ASLOverlayBasicPiece extends BasicPiece {

  public static final String ID = "overlayPiece;";
  public ASLOverlayBasicPiece() {
    this(ID + ";;;;");
  }

  public ASLOverlayBasicPiece(String type) {
    mySetType(type);
  }

  @Override
  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    if (imageBounds == null) {
      imageBounds = boundingBox();
    }
    imagePainter.draw(g, x + (int) (zoom * imageBounds.x), y + (int) (zoom * imageBounds.y), zoom, obs);
  }
}
