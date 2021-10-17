package VASL.counters;

import VASL.build.module.map.ASLSpacerBoards;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.widget.PieceSlot;
import VASSAL.counters.Embellishment;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Properties;
import VASSAL.i18n.Resources;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;

public class ASLOverlayEmbellishment extends Embellishment {
  public ASLOverlayEmbellishment() {
    this(ID + Resources.getString("Editor.Embellishment.activate"), null);
  }

  public ASLOverlayEmbellishment(String type, GamePiece inner) {
    super(type,inner);
  }
  @Override
  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    if(obs != null && !(obs instanceof PieceSlot.Panel)){
      if(obs instanceof Map.View) {
        final boolean drawUnder = drawUnderneathWhenSelected && Boolean.TRUE.equals(getProperty(Properties.SELECTED));

        if (!drawUnder) {
          //piece.draw(g, x, y, obs, zoom);
        }

        checkPropertyLevel();

        if (!isActive()) {
          Rectangle rect = piece.boundingBox();
          rect.x += getMap().getEdgeBuffer().width;
          rect.y += getMap().getEdgeBuffer().height;
          Area area = new Area(rect);

          Area boardArea = getBoardClip(obs);
          boardArea.getBounds().x += getMap().getEdgeBuffer().width;
          boardArea.getBounds().y += rect.y += getMap().getEdgeBuffer().height;
          g.drawRect(area.getBounds().x, area.getBounds().y, area.getBounds().width, area.getBounds().height);
          if (zoom != 1.0) {
            area = new Area(AffineTransform.getScaleInstance(zoom, zoom)
                .createTransformedShape(area));
            boardArea = new Area(AffineTransform.getScaleInstance(zoom, zoom)
                .createTransformedShape(boardArea));
            area.intersect(boardArea);
            g.setColor(Color.BLUE);
            g.fillRect(area.getBounds().x,area.getBounds().y, area.getBounds().width, area.getBounds().height);

          }
          if (drawUnder) {
            //piece.draw(g, x, y, obs, zoom);
          }
          return;
        }

        final int i = value - 1;
        Area area = new Area(getCurrentImageBounds());
        Area boardArea = null;
        if (zoom != 1.0) {
          area = new Area(AffineTransform.getScaleInstance(zoom, zoom)
              .createTransformedShape(area));
          boardArea = new Area(AffineTransform.getScaleInstance(zoom, zoom)
              .createTransformedShape(getBoardClip(obs)));
          area.intersect(boardArea);
          g.drawRect(area.getBounds().x,area.getBounds().y, area.getBounds().width, area.getBounds().height);

        }
        if (i < imagePainter.length && imagePainter[i] != null) {
          final Rectangle r = getCurrentImageBounds();

          imagePainter[i].draw(g, x + (int)(zoom * r.x), y + (int)(zoom * r.y), zoom, obs);
        }

        if (drawUnder) {
          //piece.draw(g, x, y, obs, zoom);
        }
      } else {
        super.draw(g,x,y,obs,zoom);
      }
    } else {
      super.draw(g,x,y,obs,zoom);
    }

  }
  private Area getBoardClip(final Component obs) {
    Area boardClip = null;
    if (this.getMap() != null) {
      Map map = getMap();
      if (map != null) {
          boardClip = new Area();
          for (final Board b : getMap().getBoards()) {
            final String boardName = b.getName();
            if (!ASLSpacerBoards.isSpacerBoard(boardName)) {
              boardClip.add(new Area(b.bounds()));
            }
        }
      }
    }
    Rectangle r = new Rectangle(boardClip.getBounds());
    r.x += getMap().getEdgeBuffer().width;
    r.y += getMap().getEdgeBuffer().height;
    return new Area(r);
  }
}
