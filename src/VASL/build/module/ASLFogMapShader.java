package VASL.build.module;

import VASL.LOS.Map.Hex;
import VASL.build.module.shader.*;
import VASL.environment.Environment;
import VASL.environment.FogLevel;
import VASSAL.build.GameModule;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.MapShader;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;

import static VASL.environment.FogLevel.NONE;

public class ASLFogMapShader extends MapShader {

  // used by draw()
  private FogLevel _fogLevel = NONE;

  public ASLFogMapShader() {
    super();
  }

  @Override
  public void setup(boolean gameStarting) {
    super.setup(gameStarting);
    Environment env = new Environment();
    Command command;
    if (env.isFog()) {
      command = new ActivateFogShaderCommand();
    } else {
      command = new DeactivateFogShaderCommand();
    }
    command.execute();
  }

  public Command getRestoreCommand() {
//    Environment env = new Environment();
//    if (env.isFog()) {
//      return new ActivateFogShaderCommand();
//    }
//    return new DeactivateFogShaderCommand();
    return null;
  }

  @Override
  protected void toggleShading() {

    this.boardClip=null;

    Environment env = new Environment();

    Object[] possibilities = FogLevel.values();
    FogLevel tempFogLevel = (FogLevel)JOptionPane.showInputDialog(
        getLaunchButton().getParent(),
        "Select Fog level and intensity:",
        "Fog Level",
        JOptionPane.PLAIN_MESSAGE,
        getLaunchButton().getIcon(),
        possibilities,
        env.getCurrentFogLevel().toString());

    if (tempFogLevel == null) return;

    // since Commands don't accept parameters outside of encode/decode,
    // inject the opacity into the game before we turn on/off visibility

    GameModule gm = GameModule.getGameModule();
    MutableProperty levelProperty = gm.getMutableProperty(Environment.FOG_LEVEL_PROPERTY);
    if (levelProperty == null) return;
    Command setPropertyCommand = levelProperty.setPropertyValue(tempFogLevel.name());
    setPropertyCommand.execute();
    gm.sendAndLog(setPropertyCommand);

    Command visibilityCommand;
    if (tempFogLevel == FogLevel.NONE) {
      visibilityCommand = new DeactivateFogShaderCommand();
    } else {
      visibilityCommand = new ActivateFogShaderCommand();
    }

    visibilityCommand.execute();
    gm.sendAndLog(visibilityCommand);

    gm.getChatter().send(tempFogLevel + " is in effect.");

    // used by draw()
    _fogLevel = tempFogLevel;

  }

  @Override
  public void draw(Graphics g, Map map) {
    if (!shadingVisible) {
      return;
    }

    ASLMap aslMap = (ASLMap) map;
    // if there is no hex grid for the map, we can't shade hex by hex.
    if(aslMap.isLegacyMode()) {
      super.draw(g, map);
      return;
    }

    Hex[][] hexes = aslMap.getVASLMap().getHexGrid();
    this.boardClip=null;
    Area boardArea = getBoardClip();

    for (Hex[] row:hexes) {
      for (Hex hex :row) {
        if( hex.getBaseHeight() <= _fogLevel.fogHeight()) {
          Polygon finalHexPolygon = new Polygon(hex.getHexBorder().xpoints,  hex.getHexBorder().ypoints, hex.getHexBorder().npoints);

          // offset to the actual board rather than draw from 0,0 on the Map.
          for(int n = 0; n < 6; ++n) {
            finalHexPolygon.xpoints[n] += aslMap.getEdgeBuffer().width;
            finalHexPolygon.ypoints[n] += aslMap.getEdgeBuffer().height;
          }
          Area area = new Area(finalHexPolygon);
          if (area.isEmpty()) {
            return;
          }

          final Graphics2D g2d = (Graphics2D) g;
          final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
          final double zoom = map.getZoom() * os_scale;

          if (zoom != 1.0) {
            area = new Area(AffineTransform.getScaleInstance(zoom, zoom)
                .createTransformedShape(area));
            boardArea = new Area(AffineTransform.getScaleInstance(zoom, zoom)
                .createTransformedShape(getBoardClip()));

          }
          // remove the bits of the hex that aren't on the board
          area.intersect(boardArea);

          final Composite oldComposite = g2d.getComposite();
          final Color oldColor = g2d.getColor();
          final Paint oldPaint = g2d.getPaint();

          g2d.setComposite(getComposite());
          g2d.setColor(getColor());
          g2d.setPaint(getTexture(zoom));
          g2d.fill(area);

          if (border) {
            final Stroke oldStroke = g2d.getStroke();
            g2d.setComposite(getBorderComposite());
            g2d.setStroke(getStroke(zoom));
            g2d.setColor(getBorderColor());
            g2d.draw(area);

            g2d.setStroke(oldStroke);
          }

          g2d.setComposite(oldComposite);
          g2d.setColor(oldColor);
          g2d.setPaint(oldPaint);

        }
      }
    }
  }
}
