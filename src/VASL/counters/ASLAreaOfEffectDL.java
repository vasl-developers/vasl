/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package VASL.counters;

import VASSAL.configure.ColorConfigurer;
import VASSAL.counters.GamePiece;

  // FredKors 17-nov-2013 support for the deluxe hex
/**
 *
 * @author Federico
 */
public class ASLAreaOfEffectDL extends ASLAreaOfEffect {
    
    public static final String ID = "ASLAreaOfEffectDL;";
    
    public ASLAreaOfEffectDL() {
        this(ID + ColorConfigurer.colorToString(defaultTransparencyColor), null);
    }
    
    public ASLAreaOfEffectDL(String type, GamePiece inner) {
        super(type, inner);
        setMagnification(3.0);
    }
}
