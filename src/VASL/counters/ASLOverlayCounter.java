package VASL.counters;

import VASL.build.module.map.ASLSpacerBoards;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.command.Command;
import VASSAL.counters.*;
import VASSAL.tools.SequenceEncoder;
import VASSAL.tools.imageop.ScaledImagePainter;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;

public class ASLOverlayCounter extends Decorator implements EditablePiece {

  public static final String ID = "prototype;OverlayCloneDelete";
  public static final String OVR_PROPS_ID = "prototype;OverlayProperties";
  private Area boardClip = null;
  private ScaledImagePainter scaledImagePainter = new ScaledImagePainter();
  private String imageName;
  private char cloneKey;
  private char deleteKey;
  private String commonName;
  private Rectangle imageBounds;
  private Object commands;
  private ASLOverlayBasicPiece thePiece;

  public ASLOverlayCounter() {
    this(ID, null);
  }

  public ASLOverlayCounter(String type, GamePiece p) {
    setInner(p);
    try {
      thePiece = (ASLOverlayBasicPiece) p;
    } catch (Exception ex) {
      int bp = 1;
    }
    mySetType(type);
  }

  @Override
  public void mySetState(String newState) {

  }

  @Override
  public String myGetState() {
    return null;
  }

  @Override
  public String myGetType() {
    return null;
  }

  @Override
  protected KeyCommand[] myGetKeyCommands() {
    return new KeyCommand[0];
  }

  @Override
  public Command myKeyEvent(KeyStroke stroke) {
    return null;
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public void mySetType(String type) {
    if(type == null || piece == null) {
      return;
    }
    if(type.startsWith("prototype")) {
      type = piece.getType();
    }
    final SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(type, ';');
    st.nextToken();
    cloneKey = st.nextChar('\0');
    deleteKey = st.nextChar('\0');
    imageName = st.nextToken();
    commonName = st.nextToken();
    scaledImagePainter.setImageName(imageName);
    imageBounds = null;  // New image, clear the old imageBounds
    commands = null;
  }

  @Override
  public HelpFile getHelpFile() {
    return getInner().getMap().getHelpFile();
  }

  @Override
  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    Area boardArea = getBoardClip(obs);
    if(boardArea == null) {
      getInner().draw(g,x,y,obs,zoom);
      return;
    }
    if(imageBounds == null) {
      imageBounds = getInner().boundingBox();
    }
    imageBounds = imageBounds.intersection(boardArea.getBounds());

    scaledImagePainter.draw(g, x + (int) (zoom * imageBounds.x), y + (int) (zoom * imageBounds.y), zoom, obs);
  }

  @Override
  public Rectangle boundingBox() {
    return getInner().boundingBox();
  }

  @Override
  public Shape getShape() {
    return getInner().getShape();
  }

  @Override
  public String getName() {
    return getInner().getName();
  }

  private Area getBoardClip(final Component obs) {
    if (this.getMap() != null) {
      Map map = getMap();
      if (map != null) {
        if (boardClip == null) {
          boardClip = new Area();
          for (final Board b : getMap().getBoards()) {
            final String boardName = b.getName();
            if (!ASLSpacerBoards.isSpacerBoard(boardName)) {
              boardClip.add(new Area(b.bounds()));
            }
          }
        }
      }
      return boardClip;
    }
    return null;
  }
}
