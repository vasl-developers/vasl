/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.counters;

import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_HEIGHT;
import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_WIDTH;
import VASL.build.module.map.boardPicker.board.ASLHexGrid;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.GeometricGrid;
import VASSAL.build.module.map.boardPicker.board.HexGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid;
import VASSAL.configure.ColorConfigurer;
import VASSAL.counters.AreaOfEffect;
import static VASSAL.counters.AreaOfEffect.ID;
import VASSAL.counters.GamePiece;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import static java.lang.Math.abs;
import static java.lang.Math.round;
import javax.swing.JOptionPane;

  // FredKors 16-nov-2013 fix for the starshell - IR off map
/**
 *
 * @author Federico
 */
public class ASLAreaOfEffect extends AreaOfEffect {

    public static final String ID = "ASLAreaOfEffect;";
    private double m_dMagnification = 1.0;   // FredKors 17-nov-2013 support for the deluxe hex
  
    public ASLAreaOfEffect() {
        this(ID + ColorConfigurer.colorToString(defaultTransparencyColor), null);
    }
    
    public ASLAreaOfEffect(String type, GamePiece inner) {
        super(type, inner);
    }

    @Override
  public String getDescription() {
    String d = "ASL Area Of Effect";
    if (description.length() > 0) {
      d += " - " + description;
    }
    return d;
  }
    public MapGrid GetASLGrid()
    {
        MapGrid l_objGrid = new ASLHexGrid(DEFAULT_HEX_HEIGHT, false);
        ((HexGrid)l_objGrid).setHexWidth(DEFAULT_HEX_WIDTH);
        ((HexGrid)l_objGrid).setEdgesLegal(true);
        
        return l_objGrid;
    }
@Override
  protected Area getArea() {
    Area a;
    final Map map = getMap();
    // Always draw the area centered on the piece's current position
    // (For instance, don't draw it at an offset if it's in an expanded stack)
    final Point mapPosition = getPosition();
    final int myRadius = getRadius();

    final Board board = map.findBoard(mapPosition);
    final MapGrid grid = board == null ? null : board.getGrid();

    if (grid instanceof GeometricGrid) {
      final GeometricGrid gGrid = (GeometricGrid) grid;

      final Rectangle boardBounds = board.bounds();
      final Point boardPosition = new Point(
        mapPosition.x-boardBounds.x, mapPosition.y-boardBounds.y);

      a = gGrid.getGridShape(boardPosition, myRadius); // In board co-ords
      final AffineTransform t = AffineTransform.getTranslateInstance(
        boardBounds.x, boardBounds.y); // Translate back to map co-ords

      final double mag = board.getMagnification();
      if (mag != 1.0) {
        t.translate(boardPosition.x, boardPosition.y);
        t.scale(mag, mag);
        t.translate(-boardPosition.x, -boardPosition.y);
      }
      a = a.createTransformedArea(t);
    }
    else { // FredKors 16-nov-2013 fix for the starshell - IR off map
        //a = new Area(
        //new Ellipse2D.Double(mapPosition.x - myRadius,
        //                     mapPosition.y - myRadius,
        //                     myRadius * 2, myRadius * 2));
        a = getGridShape(mapPosition, myRadius);

        // FredKors 17-nov-2013 support for the deluxe hex
        if (m_dMagnification != 1.0) {
            final AffineTransform t = AffineTransform.getTranslateInstance(mapPosition.x, mapPosition.y);
            t.scale(m_dMagnification, m_dMagnification);
            t.translate(-mapPosition.x, -mapPosition.y);
            a = a.createTransformedArea(t);
        }
    }
    return a;
  }

  // FredKors 16-nov-2013 fix for the starshell - IR off map
    protected Area getSingleHexShape(int centerX, int centerY) {
    Polygon poly = new Polygon();

    float x = (float) centerX;
    float y = (float) centerY;

    float x1,y1, x2,y2, x3,y3, x4, y4, x5, y5, x6, y6;

    float deltaX = (float) (DEFAULT_HEX_WIDTH);
    float deltaY = (float) (DEFAULT_HEX_HEIGHT);

    float r = 2.F * deltaX / 3.F;

    Point p1 = new Point();
    Point p2 = new Point();
    Point p3 = new Point();
    Point p4 = new Point();
    Point p5 = new Point();
    Point p6 = new Point();

    x1 = x - r;
    y1 = y;
    p1.setLocation(round(x1), round(y1));

    x2 = x - 0.5F * r;
    y2 = y - 0.5F * deltaY;
    p2.setLocation(round(x2), round(y2));

    x3 = x + 0.5F * r;
    y3 = y2;
    p3.setLocation(round(x3) + 1, round(y3));

    x4 = x + r;
    y4 = y;
    p4.setLocation(round(x4) + 1, round(y4));

    x5 = x3;
    y5 = y + 0.5F * deltaY;
    p5.setLocation(round(x5) + 1, round(y5) + 1);

    x6 = x2;
    y6 = y5;
    p6.setLocation(round(x6), round(y6) + 1);

    poly.addPoint(p1.x, p1.y);
    poly.addPoint(p2.x, p2.y);
    poly.addPoint(p3.x, p3.y);
    poly.addPoint(p4.x, p4.y);
    poly.addPoint(p5.x, p5.y);
    poly.addPoint(p6.x, p6.y);
    poly.addPoint(p1.x, p1.y);

    return new Area(poly);
  }

// FredKors 16-nov-2013 fix for the starshell - IR off map
  public Area getGridShape(Point center, int range) 
  {
      //Choose a starting point
      Point origin = new Point(0, 0);
      Area shape = getSingleHexShape(0, 0);

      for (int i = -range; i <= range; i++) 
      {
          int x = origin.x + (int) (i * DEFAULT_HEX_WIDTH);

          int length = range * 2 + 1 - abs(i);

          int startY = 0;
          if (length % 2 == 1) {
              startY = origin.y - (int) (DEFAULT_HEX_HEIGHT * (length - 1) / 2);
          } else {
              startY = origin.y - (int) (DEFAULT_HEX_HEIGHT * (0.5 + (length - 2) / 2));
          }

          int y = startY;
          for (int j = 0; j < length; j++) {
              Point p = new Point(x, y);
              shape.add(getSingleHexShape(p.x, p.y));
              y += DEFAULT_HEX_HEIGHT;
          }
      }

      shape.transform(
          AffineTransform.getTranslateInstance(0 - origin.x, 0 - origin.y));

      shape = new Area(AffineTransform.getTranslateInstance(center.x, center.y).createTransformedShape(shape));
      
      return shape;
  }    

    /**
     * @param m_dMagnification the m_dMagnification to set
     */
    public void setMagnification(double m_dMagnification) {
        this.m_dMagnification = m_dMagnification;
    }
}
