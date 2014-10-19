/*
 * $Id: ASLPieceFinder.java 0000 2009-03-09 03:22:10Z davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASL.build.module.map;

import static VASL.build.module.map.boardPicker.ASLBoard.DEFAULT_HEX_HEIGHT;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.Command;
import VASSAL.configure.PropertyExpression;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceIterator;

import java.awt.*;
import java.util.ArrayList;

/**
 * This component highlights a spot on the board.
 * It's handy when you need to draw your opponent's attention to a piece you are rallying, moving, etc.
 */
public class ASLBrokenFinder extends AbstractConfigurable implements GameComponent, Drawable {

    private Map m_objMap;

    private Boolean m_bVisible = false;
    final PropertyExpression m_piecePropertiesFilter = new PropertyExpression();
    final ArrayList<Point> mar_objPointList = new ArrayList<Point>();

    // this component is not configurable
    @Override
    public Class<?>[] getAttributeTypes() {
        return new Class<?>[] {String.class};
    }

    @Override
    public String[] getAttributeNames() {
        return new String[] {"Name"};
    }

    @Override
    public String[] getAttributeDescriptions() {
        return new String[] {"Name"};
    }

    @Override
    public String getAttributeValueString(String key) {
        return "ASL Broken Pieces Finder";
    }

    @Override
    public void setAttribute(String key, Object value) {
    }

    @Override
    public void addTo(Buildable parent) {

        // add this component to the game and register a mouse listener
        if (parent instanceof Map) 
        {
            this.m_objMap = (Map) parent;
            m_objMap.addDrawComponent(this);
            
            m_piecePropertiesFilter.setExpression("InvisibleToOthers != true && BRK_Active = true || PieceName = DM || PieceName = Disrupt");
        }
    }

    @Override
    public void draw(Graphics g, Map map) {

        if (m_bVisible) 
        {
            LoadBrokenPiecesPosition();
            
            if (mar_objPointList.size() > 0)
            {
                int l_iCircleSize = (int)(m_objMap.getZoom() * DEFAULT_HEX_HEIGHT);
                g.setColor(Color.RED);

                Graphics2D l_objGraph2D = (Graphics2D) g;
                
                Stroke l_objOldStroke = l_objGraph2D.getStroke();
                l_objGraph2D.setStroke(new BasicStroke(4));

                for (int l_i = 0; l_i < mar_objPointList.size(); l_i++)
                {
                    Point l_objPoint = m_objMap.componentCoordinates(mar_objPointList.get(l_i));

                    l_objGraph2D.drawOval(l_objPoint.x - l_iCircleSize/2, l_objPoint.y - l_iCircleSize/2, l_iCircleSize, l_iCircleSize);
                }
                
                l_objGraph2D.setStroke(l_objOldStroke);
            }
        }
    }
    
    private void LoadBrokenPiecesPosition()
    {
        for (VASSAL.build.module.Map m : VASSAL.build.module.Map.getMapList()) 
          m.getPieces();

        mar_objPointList.clear();

        final PieceIterator l_objPI = new PieceIterator(
                                            GameModule.getGameModule().getGameState().getAllPieces().iterator(),
                                            m_piecePropertiesFilter
                                          );

        while (l_objPI.hasMoreElements()) 
        {
            final GamePiece l_objPiece = l_objPI.nextPiece();

            if (l_objPiece instanceof Decorator || l_objPiece instanceof BasicPiece) 
            {
                Point l_objPos = l_objPiece.getPosition();

                if (!mar_objPointList.contains(l_objPos))
                    mar_objPointList.add(l_objPos);
            }
        }
    }

    public void findBrokenPiece(boolean bVisible) 
    {
        m_bVisible = bVisible;
        
        m_objMap.getView().repaint();
    }
    
    @Override
    public boolean drawAboveCounters() {
        return true;
    }

    @Override
    public void removeFrom(Buildable parent) {

    }

    @Override
    public HelpFile getHelpFile() {
        return null;
    }

    @Override
    public Class[] getAllowableConfigureComponents() {
        return new Class[0];
    }

    @Override
    public void setup(boolean gameStarting) {
    }

    @Override
    public Command getRestoreCommand() {
        return null;
    }
}
