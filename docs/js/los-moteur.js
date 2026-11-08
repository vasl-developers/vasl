// ═══════════════════════════════════════════════════════════════
// LIGNE DE VUE (LOS) — portage du moteur de VASL (paquet VASL.LOS.Map)
// Terrain, Location, Hex, Bridge, LOSResult et Map.LOS() avec toutes ses
// règles, méthode par méthode, noms Java conservés pour qu'on retrouve
// le code d'origine. Repère : pixels des boards sans marge ni loupe,
// (0,0) au coin haut gauche du premier board.
// Les pions sont lus par LosPions (VASLGameInterface, plus bas) ; la
// partie n'est jamais de nuit (ni éclairage, ni lueur de tir).
// ═══════════════════════════════════════════════════════════════

// a.equals(b) de Java : lève une erreur si a est nul, comme le NPE que
// VASL laisse filer jusqu'au try/catch de VASLThread.doLOS.
function _eqJ(a, b){
  if(a == null) throw new TypeError('NPE');
  return a === b;
}

// ── java.awt.Polygon (contains d'après la règle pair-impair de Java).
class JPolygon {
  constructor(){ this.xpoints = []; this.ypoints = []; this.npoints = 0; this._b = null; }
  addPoint(x, y){ this.xpoints.push(x); this.ypoints.push(y); this.npoints++; this._b = null; }
  getBounds(){
    if(!this._b){
      const xs = this.xpoints, ys = this.ypoints;
      const x0 = Math.min(...xs), y0 = Math.min(...ys);
      this._b = {x: x0, y: y0, width: Math.max(...xs) - x0, height: Math.max(...ys) - y0};
    }
    return this._b;
  }
  contains(x, y){
    const b = this.getBounds();
    if(this.npoints <= 2 || !(x >= b.x && y >= b.y && x < b.x + b.width && y < b.y + b.height)) return false;
    const xp = this.xpoints, yp = this.ypoints, n = this.npoints;
    let hits = 0, lastx = xp[n - 1], lasty = yp[n - 1], curx, cury;
    for(let i = 0; i < n; lastx = curx, lasty = cury, i++){
      curx = xp[i]; cury = yp[i];
      if(cury === lasty) continue;
      let leftx;
      if(curx < lastx){
        if(x >= lastx) continue;
        leftx = curx;
      } else {
        if(x >= curx) continue;
        leftx = lastx;
      }
      let test1, test2;
      if(cury < lasty){
        if(y < cury || y >= lasty) continue;
        if(x < leftx){ hits++; continue; }
        test1 = x - curx; test2 = y - cury;
      } else {
        if(y < lasty || y >= cury) continue;
        if(x < leftx){ hits++; continue; }
        test1 = x - lastx; test2 = y - lasty;
      }
      if(test1 < (test2 / (lasty - cury) * (lastx - curx))) hits++;
    }
    return (hits & 1) !== 0;
  }
  // Rectangle.intersects de Java, appliqué au rectangle englobant.
  intersectsRect(r){
    const b = this.getBounds();
    return b.width > 0 && b.height > 0 && r.width > 0 && r.height > 0
      && r.x < b.x + b.width && r.x + r.width > b.x && r.y < b.y + b.height && r.y + r.height > b.y;
  }
}
const _rectContient = (r, x, y) => x >= r.x && y >= r.y && x < r.x + r.width && y < r.y + r.height;

// ── java.awt.geom.Line2D
function _relativeCCW(x1, y1, x2, y2, px, py){
  x2 -= x1; y2 -= y1; px -= x1; py -= y1;
  let ccw = px * y2 - py * x2;
  if(ccw === 0){
    ccw = px * x2 + py * y2;
    if(ccw > 0){
      px -= x2; py -= y2;
      ccw = px * x2 + py * y2;
      if(ccw < 0) ccw = 0;
    }
  }
  return ccw < 0 ? -1 : (ccw > 0 ? 1 : 0);
}
function _linesIntersect(x1, y1, x2, y2, x3, y3, x4, y4){
  return _relativeCCW(x1, y1, x2, y2, x3, y3) * _relativeCCW(x1, y1, x2, y2, x4, y4) <= 0
      && _relativeCCW(x3, y3, x4, y4, x1, y1) * _relativeCCW(x3, y3, x4, y4, x2, y2) <= 0;
}
function _ptSegDist(x1, y1, x2, y2, px, py){
  x2 -= x1; y2 -= y1; px -= x1; py -= y1;
  let dot = px * x2 + py * y2, proj;
  if(dot <= 0) proj = 0;
  else {
    px = x2 - px; py = y2 - py;
    dot = px * x2 + py * y2;
    proj = dot <= 0 ? 0 : dot * dot / (x2 * x2 + y2 * y2);
  }
  const l = px * px + py * py - proj;
  return Math.sqrt(l < 0 ? 0 : l);
}
const _dist = (x1, y1, x2, y2) => Math.hypot(x1 - x2, y1 - y2);

// ── Terrain (VASL.LOS.Map.Terrain), d'après les terrainTypes partagés.
const _MURS_RANGEES = new Set(['Rowhouse Wall', 'Rowhouse Wall, 1 Level', 'Rowhouse Wall, 2 Level',
  'Rowhouse Wall, 3 Level', 'Rowhouse Wall, 4 Level', 'Breach',
  'Interior Factory Wall, 1 Level', 'Interior Factory Wall, 2 Level']);
const _MURS_USINE = new Set(['Stone Factory Wall, 1.5 Level', 'Stone Factory Wall, 2.5 Level',
  'Wooden Factory Wall, 1.5 Level', 'Wooden Factory Wall, 2.5 Level']);
class LosTerrain {
  constructor(o){ Object.assign(this, o); }
  getName(){ return this.name; }
  getType(){ return this.type; }
  getHeight(){ return this.height; }
  getLOSCategory(){ return this.cat; }
  isLOSObstacle(){ return this.LOSObstacle; }
  isLOSHindrance(){ return this.LOSHindrance; }
  isLowerLOSObstacle(){ return this.lowerLOSObstacle; }
  isLowerLOSHindrance(){ return this.lowerLOSHindrance; }
  isHalfLevelHeight(){ return this.halfLevelHeight; }
  isInherentTerrain(){ return this.inherentTerrain; }
  hasSplit(){ return this.split !== 0; }
  isEntrenchmentTerrain(){ return this.cat === 'ENTRENCHMENT'; }
  isBuildingTerrain(){ return this.cat === 'MARKETPLACE' || this.cat === 'BUILDING'; }
  isWaterTerrain(){ return this.cat === 'WATER'; }
  isBridge(){ return this.cat === 'BRIDGE'; }
  isTunnel(){ return this.cat === 'TUNNEL'; }
  isRoad(){ return this.cat === 'ROAD'; }
  isDepression(){ return this.cat === 'DEPRESSION'; }
  isBuilding(){ return this.cat === 'BUILDING' || this.cat === 'FACTORY' || this.cat === 'MARKETPLACE'; }
  isHexsideTerrain(){ return this.cat === 'HEXSIDE' || this.isRowhouseFactoryWallOrBreach(); }
  isOpen(){ return this.cat === 'OPEN' || this.cat === 'ROAD' || this.cat === 'WATER'; }
  isRowhouseFactoryWallOrBreach(){ return _MURS_RANGEES.has(this.name); }
  isOutsideFactoryWall(){ return _MURS_USINE.has(this.name); }
  isCellar(){ return this.name.includes('Cellar'); }
  isRooftop(){ return this.name.includes('Rooftop'); }
  isRoofless(){ return this.name.includes('Roofless') || this.name.includes('Gutted'); }
  isCliff(){ return this.name.includes('Cliff'); }
  isFactoryTerrain(){ return this.cat === 'FACTORY'; }
}

// ── Location (VASL.LOS.Map.Location)
class LosLocation {
  constructor(name, levelInHex, LOSPoint, auxLOSPoint, edgeCenterPoint, hex, terrain){
    this.name = name; this.levelInHex = levelInHex;
    this.LOSPoint = LOSPoint; this.auxLOSPoint = auxLOSPoint; this.edgeCenterPoint = edgeCenterPoint;
    this.hex = hex; this.terrain = terrain;
    this.depressionTerrain = null; this.upLocation = null; this.downLocation = null;
  }
  // new Location(Location l) : mêmes points (copiés), même hex, copyLocation.
  static copie(l){
    const c = p => p ? {x: p.x, y: p.y} : null;
    const n = new LosLocation(l.name, l.levelInHex, c(l.LOSPoint), c(l.auxLOSPoint), c(l.edgeCenterPoint), l.hex, l.terrain);
    n.depressionTerrain = l.depressionTerrain;
    return n;
  }
  getName(){ return this.name; }
  getLevelInHex(){ return this.levelInHex; }
  setLevelInHex(v){ this.levelInHex = v; }
  getAbsoluteHeight(){ return this.levelInHex + this.hex.getBaseLevelofHex(); }
  getTerrain(){ return this.terrain; }
  setTerrain(t){ this.terrain = t; }
  getHex(){ return this.hex; }
  getLOSPoint(){ return this.LOSPoint; }
  getAuxLOSPoint(){ return this.auxLOSPoint; }
  getEdgeCenterPoint(){ return this.edgeCenterPoint; }
  getDepressionTerrain(){ return this.depressionTerrain; }
  setDepressionTerrain(t){ this.depressionTerrain = t; }
  isDepressionTerrain(){ return this.depressionTerrain != null; }
  isCenterLocation(){ return this.hex.isCenterLocation(this); }
  getUpLocation(){ return this.upLocation; }
  setUpLocation(l){ this.upLocation = l; }
  getDownLocation(){ return this.downLocation; }
  setDownLocation(l){ this.downLocation = l; }
  getNextLevelLocation(sens){ return sens === 'up' ? this.upLocation : sens === 'down' ? this.downLocation : null; }
}

// ── Bridge (VASL.LOS.Map.Bridge) — pont d'un seul hex, sans rotation.
class LosBridge {
  constructor(terrain, roadLevel, location, singleHex, center){
    this.terrain = terrain; this.roadLevel = roadLevel; this.location = location;
    this.singleHex = singleHex; this.center = center; this.rotation = 0;
  }
  getRoadLevel(){ return this.roadLevel; }
  _rect(inset){
    const w = this.singleHex ? 32 : 16, h = this.singleHex ? 48 : 64;
    const cx = Math.trunc(this.center.x), cy = Math.trunc(this.center.y);
    return {x: cx - w/2 + inset, y: cy - h/2, width: w - inset*2, height: h};
  }
  getShape(){ return this._rect(0); }
  getRoadShape(){ return this._rect(this.singleHex ? 9 : 2); }
}

// ── Hex (VASL.LOS.Map.Hex)
class LosHex {
  constructor(col, row, name, centerDot, hexHeight, hexWidth, map, baseLevelofHex, terrain){
    this.baseLevelofHex = baseLevelofHex;
    this.columnNumber = col; this.rowNumber = row;
    this.map = map; this.name = name;
    this.northOnMap = this.northEastOnMap = this.southEastOnMap = true;
    this.southOnMap = this.southWestOnMap = this.northWestOnMap = true;
    this.hexBorder = new JPolygon(); this.extendedHexBorder = new JPolygon();
    this.hexsideLocations = new Array(6);
    this.hexsideTerrain = [null, null, null, null, null, null];
    this.hexsideHasCliff = [false, false, false, false, false, false];
    this.slopes = [false, false, false, false, false, false];
    this.rbrrembankments = [false, false, false, false, false, false];
    this.partialorchards = [false, false, false, false, false, false];
    this.bridge = null; this.stairway = false;
    this.hexWidth = hexWidth; this.hexHeight = hexHeight;
    this.centerDot = {x: centerDot.x, y: centerDot.y};
    this.center = this.fixMapEdgePoints(centerDot.x, centerDot.y);
    this.initHexNew(terrain);
  }

  initHexNew(terr){
    const x = this.centerDot.x, y = this.centerDot.y;
    const hexside = this.hexWidth * 2.0 / 3.0;
    const verticalOffset = this.hexHeight / 2.0;
    const v = [
      this.fixMapEdgePoints(-hexside / 2.0 + x, -verticalOffset + y),
      this.fixMapEdgePoints(hexside / 2.0 + x, -verticalOffset + y),
      this.fixMapEdgePoints(hexside + x, y),
      this.fixMapEdgePoints(hexside / 2.0 + x, verticalOffset + y),
      this.fixMapEdgePoints(-hexside / 2.0 + x, verticalOffset + y),
      this.fixMapEdgePoints(-hexside + x, y)];
    const ext = (this.hexWidth > 168 && this.hexHeight > 194) ? 2 : 1;
    const dx = [-ext, ext, ext, ext, -ext, -ext], dy = [-ext, -ext, 0, ext, ext, 0];
    for(let i = 0; i < 6; i++){
      this.hexBorder.addPoint(Math.round(v[i].x), Math.round(v[i].y));
      this.extendedHexBorder.addPoint(Math.trunc(v[i].x) + dx[i], Math.trunc(v[i].y) + dy[i]);
    }
    const h = Math.cos(Math.PI / 6) * verticalOffset;
    const T = Math.trunc;
    const s = [
      this.fixMapEdgePoints(T(x), T(-verticalOffset + y + 1.0)),
      this.fixMapEdgePoints(T(h + x - 1), T(-verticalOffset/2.0 + y + 1.0)),
      this.fixMapEdgePoints(T(h + x - 1), T(verticalOffset/2.0 + y - 1.0)),
      this.fixMapEdgePoints(T(x), T(verticalOffset + y - 1.0)),
      this.fixMapEdgePoints(T(-h + x + 1), T(verticalOffset/2.0 + y - 1.0)),
      this.fixMapEdgePoints(T(-h + x + 1), T(-verticalOffset/2.0 + y + 1.0))];
    this.setHexFlags();
    this.createLocations(terr, v, s);
  }

  resetHexAndLocationNames(name){
    this.name = name;
    this.centerLocation.name = name;
    this.resetHexsideLocationNames();
  }

  createLocations(terr, v, s){
    const P = p => ({x: Math.trunc(p.x), y: Math.trunc(p.y)});
    this.centerLocation = new LosLocation(this.name, this.baseLevelofHex,
      this.getHexCenter(), this.getHexCenter(), this.getHexCenter(), this, terr);
    const noms = [':North', ':NorthEast', ':SouthEast', ':South', ':SouthWest', ':NorthWest'];
    for(let i = 0; i < 6; i++)
      this.hexsideLocations[i] = new LosLocation(this.name + noms[i], this.baseLevelofHex,
        P(v[i]), P(v[(i + 1) % 6]), P(s[i]), this, terr);
  }

  fixMapEdgePoints(x, y){
    const m = this.map;
    let nx = x === -1.0 ? 0.0 : x;
    let ny = y === -1.0 ? 0.0 : y;
    nx = (Math.trunc(nx) === m.getGridWidth() || Math.trunc(nx) === m.getGridWidth() + 1) ? m.getGridWidth() - 1.0 : nx;
    ny = (Math.trunc(ny) === m.getGridHeight() || Math.trunc(ny) === m.getGridHeight() + 1) ? m.getGridHeight() - 1.0 : ny;
    return {x: nx, y: ny};
  }

  setHexFlags(){
    const m = this.map, cfg = m.getMapConfiguration(), c = this.columnNumber, r = this.rowNumber;
    const off = (...l) => { for(const k of l) this[k] = false; };
    if(cfg === 'Normal' && m.getA1CenterY() !== 65){
      if(c === 0) off('southWestOnMap', 'northWestOnMap');
      if(c + 1 === m.getWidth()) off('southEastOnMap', 'northEastOnMap');
      if(c % 2 === 1 && r === 0) off('northOnMap', 'northEastOnMap', 'northWestOnMap');
      if(c % 2 === 1 && r === m.getHeight()) off('southOnMap', 'southEastOnMap', 'southWestOnMap');
    } else if(cfg === 'Toplefthalfheight'){
      if(c === 0) off('southWestOnMap', 'northWestOnMap');
      if(c + 1 === m.getWidth()) off('southEastOnMap', 'northEastOnMap');
      if(c % 2 === 0 && r === 0) off('northOnMap', 'northEastOnMap', 'northWestOnMap');
      if(c % 2 === 0 && r === m.getHeight()) off('southOnMap', 'southEastOnMap', 'southWestOnMap');
    } else if(cfg === 'FullHex'){
      if(c % 2 === 1 && r === 0) off('northOnMap', 'northEastOnMap', 'northWestOnMap');
      if(c % 2 === 1 && r === m.getHeight()) off('southOnMap', 'southEastOnMap', 'southWestOnMap');
    } else if(cfg === 'FullHexhalfheight'){
      if(c % 2 === 0 && r === 0) off('northOnMap', 'northEastOnMap', 'northWestOnMap');
      if(c % 2 === 0 && r === m.getHeight()) off('southOnMap', 'southEastOnMap', 'southWestOnMap');
    } else if(cfg === 'ToplefthalfheightEqualRowCount' || m.getA1CenterY() === 65){
      if(c === 0) off('southWestOnMap', 'northWestOnMap');
      if(c + 1 === m.getWidth()) off('southEastOnMap', 'northEastOnMap');
      if(c % 2 === 0 && r === 0) off('northOnMap', 'northEastOnMap', 'northWestOnMap');
      if(c % 2 === 1 && r + 1 === m.getHeight()) off('southOnMap', 'southEastOnMap', 'southWestOnMap');
    } else if(cfg === 'FullHexEqualRowCount'){
      if(c % 2 === 1 && r === 0) off('northOnMap', 'northEastOnMap', 'northWestOnMap');
      if(c % 2 === 0 && r === m.getHeight() - 1) off('southOnMap', 'southEastOnMap', 'southWestOnMap');
    }
  }

  resetHexsideLocationNames(){
    const m = this.map;
    for(let i = 0; i < 6; i++){
      const a = m.getAdjacentHex(this, i);
      // VASL efface le côté 5 (et non 4) quand le voisin 4 manque : repris tel quel.
      if(a != null) this.hexsideLocations[i].name = this.name + '/' + a.getName();
      else this.hexsideLocations[i === 4 ? 5 : i].name = '';
    }
  }

  getMap(){ return this.map; }
  getBridge(){ return this.bridge; }
  setBridge(b){ this.bridge = b; }
  hasBridge(){ return this.bridge != null; }
  isHexsideOnMap(h){
    switch(h){
      case 0: return this.northOnMap;      case 1: return this.northEastOnMap;
      case 2: return this.southEastOnMap;  case 3: return this.southOnMap;
      case 4: return this.southWestOnMap;  case 5: return this.northWestOnMap;
      default: return false;
    }
  }
  getColumnNumber(){ return this.columnNumber; }
  getRowNumber(){ return this.rowNumber; }
  getName(){ return this.name; }
  getHexBorder(){ return this.hexBorder; }
  getExtendedHexBorder(){ return this.extendedHexBorder; }
  getHexCenter(){ return {x: Math.trunc(this.center.x), y: Math.trunc(this.center.y)}; }
  getBaseLevelofHex(){ return this.baseLevelofHex; }
  setBaseLevelofHex(v){ this.baseLevelofHex = v; }
  hasSlope(h){ return this.slopes[h]; }
  getCenterLocation(){ return this.centerLocation; }
  getPartialOrchards(){ return this.partialorchards; }
  getRBrrembankments(){ return this.rbrrembankments; }
  hasStairway(){ return this.stairway; }
  setStairway(v){ this.stairway = v; }
  static getOppositeHexside(h){ return (h >= 0 && h <= 2) ? h + 3 : (h >= 3 && h <= 5) ? h - 3 : -1; }
  getOppositeHexside(h){ return LosHex.getOppositeHexside(h); }

  isCenterLocation(l){
    if(this.centerLocation === l) return true;
    for(let t = this.centerLocation; t.getUpLocation() != null; t = t.getUpLocation())
      if(l === t.getUpLocation()) return true;
    for(let t = this.centerLocation; t.getDownLocation() != null; t = t.getDownLocation())
      if(l === t.getDownLocation()) return true;
    return false;
  }
  getLocationHexside(l){
    for(let x = 0; x < 6; x++) if(l === this.hexsideLocations[x]) return x;
    return -1;
  }
  // Hex.setDepressionTerrain(Terrain)
  setDepressionTerrainT(terr){
    this.centerLocation.setDepressionTerrain(terr);
    if(terr == null) for(let x = 0; x < 6; x++) this.hexsideLocations[x].setDepressionTerrain(terr);
  }
  isDepressionTerrain(){ return this.centerLocation.isDepressionTerrain(); }

  resetTerrain(){
    const m = this.map;
    let centerLocationTerrain = this.getnearcenterLocationTerrain();
    const centerLocationElevation = this.getnearcenterLocationElevation();
    if(centerLocationTerrain == null) centerLocationTerrain = m.getTerrain('Open Ground');
    if(!centerLocationTerrain.isBuilding() && this.getHexsideBuildingTerrain() != null)
      centerLocationTerrain = this.getHexsideBuildingTerrain();
    // bdRO : entrepôts en bois I24-I26 qui manquent le point central.
    if(!centerLocationTerrain.isBuilding()){
      const p = this.centerLocation.getLOSPoint();
      const essais = [[p.x + 5, p.y], [p.x - 5, p.y], [p.x, p.y + 5], [p.x, p.y - 5]];
      for(const [tx, ty] of essais){
        const t = m.onMap(tx, ty) ? m.getGridTerrain(tx, ty) : null;
        if(t != null && t.isBuilding()) centerLocationTerrain = t;
      }
    }
    this.centerLocation.setTerrain(centerLocationTerrain);
    this.addBuildingLevels(centerLocationTerrain, false);

    for(let x = 0; x < 6; x++){
      if(!this.isHexsideOnMap(x)) continue;
      const e = this.getHexsideLocation(x).getEdgeCenterPoint();
      let terrain = m.getGridTerrain(Math.trunc(e.x), Math.trunc(e.y));
      if(this.rbrrembankments[x]){ terrain = m.getTerrain('Rrembankment'); this.hexsideTerrain[x] = terrain; }
      if(this.partialorchards[x]){ terrain = m.getTerrain('PartialOrchard'); this.hexsideTerrain[x] = terrain; }
      if(terrain == null) terrain = m.getTerrain('Open Ground');
      const oppositeHex = m.getAdjacentHex(this, x);
      if(oppositeHex != null){
        const oe = oppositeHex.getHexsideLocation((x + 3) % 6).getEdgeCenterPoint();
        const ot = m.getGridTerrain(Math.trunc(oe.x), Math.trunc(oe.y));
        if(ot != null && !terrain.isHexsideTerrain() && ot.isHexsideTerrain()) terrain = ot;
      }
      if(terrain.isHexsideTerrain()){
        this.hexsideTerrain[x] = terrain;
        if(terrain.getName() === 'Cliff') this.hexsideHasCliff[x] = true;
      }
      this.getHexsideLocation(x).setTerrain(terrain);
      if(terrain.getName().includes('OutOfBounds')) this.setHexsideOffMap(x);
    }
    this.setBaseLevelofHex(centerLocationElevation);
    this.setDepressionTerrainHex();
    this.setInherentTerrain();
    this.resetHexsideTerrain();
    this.fixBridgesTunnelWater();
  }

  setHexsideOffMap(x){
    const k = ['northOnMap', 'northEastOnMap', 'southEastOnMap', 'southOnMap', 'southWestOnMap', 'northWestOnMap'][x];
    if(k) this[k] = false;
  }

  _presDuCentre(lire){
    const m = this.map, p = this.centerLocation.getLOSPoint(), x = Math.trunc(p.x), y = Math.trunc(p.y);
    if(m.onMap(x + 1, y - 1)) return lire(x + 1, y - 1);
    if(m.onMap(x + 1, y + 1)) return lire(x + 1, y + 1);
    if(m.onMap(x - 1, y + 1)) return lire(x - 1, y + 1);
    return undefined;
  }
  getnearcenterLocationTerrain(){
    const m = this.map, p = this.centerLocation.getLOSPoint(), x = Math.trunc(p.x), y = Math.trunc(p.y);
    const t = this._presDuCentre((a, b) => m.getGridTerrain(a, b));
    if(t !== undefined) return t;
    if(m.onMap(x - 1, y - 1)) return m.getGridTerrain(x - 1, y - 1);
    return m.getGridTerrain(x, y);
  }
  getnearcenterLocationElevation(){
    const m = this.map, p = this.centerLocation.getLOSPoint(), x = Math.trunc(p.x), y = Math.trunc(p.y);
    const e = this._presDuCentre((a, b) => m.getGridElevation(a, b));
    if(e !== undefined) return e;
    // VASL lit ici (x-1, y+1) : repris tel quel.
    if(m.onMap(x - 1, y - 1)) return m.getGridElevation(x - 1, y + 1);
    return m.getGridElevation(x, y);
  }

  getHexsideBuildingTerrain(){
    for(let x = 0; x < 6; x++){
      const p = this.hexsideLocations[x].getEdgeCenterPoint();
      const t = this.map.getGridTerrain(p.x, p.y);
      if(t != null && t.isBuilding()) return t;
    }
    return null;
  }

  // Premier pixel de l'hexagone qui remplit la condition, ou null.
  _chercherPixel(test){
    const m = this.map, r = this.getHexBorder().getBounds();
    for(let x = r.x; x < r.x + r.width; x++)
      for(let y = r.y; y < r.y + r.height; y++)
        if(this.getHexBorder().contains(x, y) && m.onMap(x, y)){
          const t = m.getGridTerrain(x, y);
          if(test(t)) return t;
        }
    return null;
  }
  hasBridgeOrTunnelTerrain(){ return this._chercherPixel(t => t.isBridge() || t.isTunnel()) != null; }
  getBridgeTerrain(){ return this._chercherPixel(t => t.isBridge()); }
  getDepressionTerrainHex(){ return this._chercherPixel(t => t.isDepression()); }

  fixBridgesTunnelWater(){
    if(!this.hasBridgeOrTunnelTerrain()) return;
    const c = this.centerLocation;
    let bridgeLocation = null;
    if(c.isDepressionTerrain()){
      const n = LosLocation.copie(c);
      n.setDepressionTerrain(null);
      n.setTerrain(this.getBridgeTerrain());
      n.setLevelInHex(this.getBridgeLocationAbsoluteLevel() - n.getHex().getBaseLevelofHex());
      n.setDownLocation(c); c.setUpLocation(n);
      bridgeLocation = n;
    } else if(c.getTerrain().isBridge()){
      const n = LosLocation.copie(c);
      n.setDepressionTerrain(this.getDepressionTerrainHex());
      let depressionadj = 1;
      if(this.baseLevelofHex === 0 && c.getLevelInHex() === 0) c.setLevelInHex(1);
      else if(this.baseLevelofHex > 0) depressionadj = 2;
      n.setLevelInHex(c.getLevelInHex() - depressionadj);
      n.setTerrain(this.getDepressionTerrainHex());
      n.setUpLocation(c); c.setDownLocation(n);
      bridgeLocation = c;
    } else if(c.getTerrain().isTunnel()){
      const n = LosLocation.copie(c);
      n.setDepressionTerrain(null);
      n.setTerrain(this.map.getTerrain(this.getName().includes('II50') ? 'Woods' : 'Open Ground'));
      n.setLevelInHex(c.getLevelInHex() + 1);
      n.setDownLocation(c); c.setUpLocation(n);
    } else if(c.getTerrain().isWaterTerrain()){
      const n = LosLocation.copie(c);
      n.setDepressionTerrain(null);
      n.setLevelInHex(this.getBridgeLocationAbsoluteLevel() - n.getHex().getBaseLevelofHex());
      n.setTerrain(this.getBridgeTerrain());
      n.setDownLocation(c); c.setUpLocation(n);
      bridgeLocation = n;
    }
    if(bridgeLocation != null)
      this.setBridge(new LosBridge(this.getBridgeTerrain(), this.getBridgeLocationAbsoluteLevel(), bridgeLocation,
        true, {x: Math.trunc(this.center.x), y: Math.trunc(this.center.y)}));
  }

  addBuildingLevels(t){
    if(!t.isBuilding()) return;
    const m = this.map, c = this.centerLocation;
    const sansEtage = ['Wooden Building', 'Stone Building', 'Huts', 'MultipleWooden'].includes(t.getName());
    const oldStairway = this.stairway
      && c.getTerrain().getName() !== 'Stone Building, 1 Level'
      && c.getTerrain().getName() !== 'Wooden Building, 1 Level';
    if(t.getLOSCategory() === 'MARKETPLACE') c.setTerrain(m.getTerrain('Open Ground'));
    let prev = c;
    for(let level = 1; level <= t.getHeight(); level++){
      if(sansEtage) continue;
      if(t.getLOSCategory() === 'FACTORY' && !this.stairway) continue;
      const l = new LosLocation(c.getName() + ' Level ' + level, level, c.getLOSPoint(), c.getLOSPoint(), null, this, t);
      prev.setUpLocation(l); l.setDownLocation(prev); prev = l;
    }
    this.stairway = c.getTerrain().getName() === 'Stone Building, 1 Level'
      || c.getTerrain().getName() === 'Wooden Building, 1 Level' || oldStairway;
    if(!sansEtage && t.getLOSCategory() !== 'FACTORY'){
      const l = new LosLocation(c.getName() + ' Cellar ', -1, c.getLOSPoint(), c.getLOSPoint(), null, this, m.getTerrain('Cellar'));
      c.setDownLocation(l); l.setUpLocation(c);
    }
    if(!sansEtage && !t.isRoofless()){
      prev = c;
      let level = 0;
      while(prev.getUpLocation() != null){ prev = prev.getUpLocation(); level++; }
      if(t.getLOSCategory() === 'FACTORY') level = t.getHeight();
      const l = new LosLocation(c.getName() + ' Rooftop ', level + 1, c.getLOSPoint(), c.getLOSPoint(), null, this, m.getTerrain('Rooftop'));
      prev.setUpLocation(l); l.setDownLocation(prev);
    }
    this.fixspecialcasesAddRooftops();
  }

  // Hex.setDepressionTerrain() sans argument.
  setDepressionTerrainHex(){
    const c = this.centerLocation;
    if(c.getTerrain().isDepression()) c.setDepressionTerrain(c.getTerrain());
    else if(c.getDepressionTerrain() == null){ /* rien */ }
    else if(c.getDepressionTerrain().isDepression()) c.setTerrain(c.getDepressionTerrain());
  }

  setInherentTerrain(){
    const m = this.map, r = this.getHexBorder().getBounds();
    let terrain = null;
    for(let x = r.x; x < r.x + r.width && terrain == null && x < m.getGridWidth(); x++)
      for(let y = r.y; y < r.y + r.height && terrain == null; y++)
        if(m.onMap(x, y) && x >= 0 && m.getGridTerrain(x, y).isInherentTerrain()
           && this.getNearestLocation(x, y) === this.centerLocation)
          terrain = m.getGridTerrain(x, y);
    if(terrain != null) this.centerLocation.setTerrain(terrain);
  }

  resetHexsideTerrain(){
    const m = this.map;
    for(let x = 0; x < 6; x++){
      if(!this.isHexsideOnMap(x)) continue;
      const l = this.getHexsideLocation(x), e = l.getEdgeCenterPoint();
      let t = m.getGridTerrain(Math.trunc(e.x), Math.trunc(e.y));
      if(t != null && t.isOpen()) t = this.checkWallHedgeGap(l);
      if(t == null) t = m.getTerrain('Open Ground');
      if(t.isHexsideTerrain()) this.setHexsideTerrain(x, t);
      else if(t.isDepression()) l.setDepressionTerrain(t);
      const h2 = m.getAdjacentHex(this, x);
      if(h2 != null && h2.getHexsideTerrain(LosHex.getOppositeHexside(x)) != null)
        this.setHexsideTerrain(x, h2.getHexsideTerrain(LosHex.getOppositeHexside(x)));
    }
  }

  checkWallHedgeGap(l){
    const hs = l.getHex().getLocationHexside(l), e = l.getEdgeCenterPoint(), b = this.hexBorder;
    const sommets = [[0, 1], [1, 2], [3, 2], [4, 3], [5, 4], [0, 5]][hs];
    if(!sommets) return null;
    const pt = i => [Math.trunc((b.xpoints[i] + e.x) / 2), Math.trunc((b.ypoints[i] + e.y) / 2)];
    const [fx, fy] = pt(sommets[0]), [sx, sy] = pt(sommets[1]);
    const m = this.getMap();
    const mur = (x, y) => {
      const n = m.getGridTerrain(x, y).getName();
      return n === 'Wall' ? m.getTerrain('Wall') : n === 'Hedge' ? m.getTerrain('Hedge') : null;
    };
    if(m.onMap(fx, fy)) return mur(fx, fy);
    if(m.onMap(sx, sy)) return mur(sx, sy);
    return null;
  }

  getHexsideTerrain(side){ return this.hexsideTerrain[side]; }
  hasCliff(side){ return this.hexsideHasCliff[side]; }
  setHexsideTerrain(side, terr){
    if(terr == null){ this.hexsideTerrain[side] = null; this.hexsideHasCliff[side] = false; }
    else if(terr.isHexsideTerrain()){
      this.hexsideTerrain[side] = terr;
      if(terr.isCliff()) this.hexsideHasCliff[side] = true;
    }
  }
  getHexsideLocation(h){ return this.hexsideLocations[h === -1 ? 0 : h]; }
  setHexsideLocationTerrain(h, terr){
    this.hexsideLocations[h].setTerrain(terr);
    if(terr == null) this.hexsideLocations[h].setTerrain(this.getMap().getTerrain('Open Ground'));
    else if(terr.isHexsideTerrain() || terr.isInherentTerrain()) this.hexsideTerrain[h] = terr;
  }

  getBridgeLocationAbsoluteLevel(){
    for(let x = 0; x < 6; x++){
      const t = this.getHexsideLocation(x).getTerrain();
      if(t != null && !t.isDepression() && t.isRoad()){
        const e = this.getHexsideLocation(x).getEdgeCenterPoint();
        return this.map.getGridElevation(Math.trunc(e.x), Math.trunc(e.y));
      }
    }
    return this.getBaseLevelofHex() + 1;
  }

  contains(x, y){ return _rectContient(this.hexBorder.getBounds(), x, y) && this.hexBorder.contains(x, y); }
  containsExtended(x, y){ return this.extendedHexBorder.contains(x, y); }

  getNearestLocation(x, y){
    const c = this.centerLocation.getLOSPoint();
    let distance = _dist(x, y, c.x, c.y), current = this.centerLocation;
    for(let side = 0; side < 6; side++){
      if(!this.isHexsideOnMap(side)) continue;
      const e = this.hexsideLocations[side].getEdgeCenterPoint();
      const d = _dist(x, y, e.x, e.y);
      if(d < distance){ distance = d; current = this.hexsideLocations[side]; }
    }
    return current;
  }
  getNearestHexside(x, y, side1, side2){
    const a = this.hexsideLocations[side1].getEdgeCenterPoint(), b = this.hexsideLocations[side2].getEdgeCenterPoint();
    return _dist(x, y, a.x, a.y) < _dist(x, y, b.x, b.y) ? side1 : side2;
  }

  // Hex.changeAllTerrain(Terrain, Terrain, Shape)
  changeAllTerrain(from, to, s){
    const c = this.centerLocation, inC = s.contains(c.getLOSPoint().x, c.getLOSPoint().y);
    if(c.getTerrain().getType() === from.getType() && inC) c.setTerrain(to);
    if(c.getDepressionTerrain() != null && c.getDepressionTerrain().getType() === from.getType() && inC)
      this.setDepressionTerrainT(to);
    for(let x = 0; x < 6; x++){
      const l = this.hexsideLocations[x], e = l.getEdgeCenterPoint();
      if(l.getTerrain().getType() === from.getType() && s.contains(e.x, e.y)) l.setTerrain(to);
      if(l.getDepressionTerrain() != null && l.getDepressionTerrain().getType() === from.getType() && s.contains(e.x, e.y))
        l.setDepressionTerrain(to);
    }
    for(let x = 0; x < 6; x++){
      const e = this.hexsideLocations[x].getEdgeCenterPoint();
      if(this.hexsideTerrain[x] != null && this.hexsideTerrain[x].getType() === from.getType() && s.contains(e.x, e.y))
        this.hexsideTerrain[x] = to;
    }
  }

  fixspecialcasesAddRooftops(){
    const t = this.getCenterLocation().getTerrain();
    const RO = ['I21', 'I22', 'I23', 'I24', 'I25', 'I26', 'I29', 'I30', 'J21', 'J22', 'J23', 'J24', 'J25', 'J26'];
    if(t.getName() === 'Wooden Building' && RO.includes(this.getName())){
      const c = this.centerLocation;
      const l = new LosLocation(c.getName() + ' Rooftop ', 1, c.getLOSPoint(), c.getLOSPoint(), null, this, this.map.getTerrain('Rooftop'));
      c.setUpLocation(l); l.setDownLocation(c);
    }
  }

  isPointOnHexside(x, y, hexside, startint, stopint){
    const l = this.getHexsideLocation(hexside), A = l.getLOSPoint(), B = l.getAuxLOSPoint();
    const px = Math.trunc(x), py = Math.trunc(y);
    for(let tx = -startint; tx < stopint; tx++)
      for(let ty = -startint; ty < stopint; ty++)
        if(_ptSegDist(A.x, A.y, B.x, B.y, px + tx, py + ty) < 1) return true;
    return false;
  }

  // Niveaux virtuels des flèches haut/bas (jusqu'au niveau 10, ou -3).
  // garderPoint (VASLweb) : le niveau virtuel garde le point visé du lieu
  // de départ (côté ou sommet) au lieu de passer au centre de l'hexagone.
  setvirtualLocation(newlevel, currentLocation, direction, garderPoint = false){
    const c = this.centerLocation;
    const lib = newlevel === 10 ? ' Aerial LOS' : newlevel === -3 ? ' Bedrock!' : ' Virtual Location';
    const p = garderPoint ? currentLocation.getLOSPoint() : c.getLOSPoint();
    const a = garderPoint ? currentLocation.getAuxLOSPoint() : c.getLOSPoint();
    const l = new LosLocation(c.getName() + lib, Math.trunc(newlevel), p, a, null, this,
      this.map.getTerrain('Open Ground'));
    if(direction === 'up'){ currentLocation.setUpLocation(l); l.setDownLocation(currentLocation); }
    else { currentLocation.setDownLocation(l); l.setUpLocation(currentLocation); }
  }
}

// ── LOSResult (VASL.LOS.Map.LOSResult)
class LosResult {
  constructor(){
    this.mapHindrances = new Map(); this.smokeHindrances = new Map();
    this.vehicleHindrances = new Map(); this.obaHindrances = new Set();
    this.reset();
  }
  isBlocked(){ return this.blocked; }
  getBlockedAtPoint(){ return this.blockedAtPoint; }
  firstHindranceAt(){ return this.firstHindranceAtPt; }
  getReason(){ return this.reason; }
  isLOSisHorizontal(){ return this.LOSisHorizontal; }
  isLOSis60Degree(){ return this.LOSis60Degree; }
  hasHindrance(){
    return this.mapHindrances.size + this.smokeHindrances.size + this.vehicleHindrances.size + this.obaHindrances.size > 0;
  }
  getHindrance(){
    let h = 0;
    for(const v of this.mapHindrances.values()) h += v;
    for(const v of this.smokeHindrances.values()) h += v;
    for(const v of this.vehicleHindrances.values()) h += v;
    h += this.obaHindrances.size;
    return Math.floor(h);
  }
  // Une gêne par portée : la plus forte l'emporte.
  _ajouterGene(liste, h, hindrance, x, y){
    if(this.firstHindranceAtPt == null) this.firstHindranceAtPt = {x, y};
    const src = this.sourceLocation.getHex();
    const range = LosMap.range(src, h, src.getMap().getMapConfiguration());
    if(!liste.has(range) || hindrance > liste.get(range)) liste.set(range, hindrance);
    if(this.getHindrance() > 5) this.setBlocked(x, y, 'Hindrance total of six or more (B.10)');
  }
  addMapHindrance(h, hindrance, x, y){ this._ajouterGene(this.mapHindrances, h, hindrance, x, y); }
  addSmokeHindrance(h, hindrance, x, y){ this._ajouterGene(this.smokeHindrances, h, hindrance, x, y); }
  addVehicleHindrance(h, hindrance, x, y){ this._ajouterGene(this.vehicleHindrances, h, hindrance, x, y); }
  checkHindranceExistsAtThisRange(range){ return this.vehicleHindrances.has(range); }
  addOBAHindrance(oba, x, y){
    if(this.firstHindranceAtPt == null) this.firstHindranceAtPt = {x, y};
    this.obaHindrances.add(oba);
    if(this.getHindrance() > 5) this.setBlocked(x, y, 'Hindrance total of six or more (B.10)');
  }
  setSourceLocation(l){ this.sourceLocation = l; }
  setTargetLocation(l){ this.targetLocation = l; }
  setSourceExitHexspine(h){ this.sourceExitHexspine = h; }
  setTargetEnterHexspine(h){ this.targetEnterHexspine = h; }
  getSourceExitHexspine(){ return this.sourceExitHexspine; }
  getTargetEnterHexspine(){ return this.targetEnterHexspine; }
  setContinuousSlope(v){ this.continuousSlope = v; }
  setLOSis60Degree(v){ this.LOSis60Degree = v; }
  setLOSisHorizontal(v){ this.LOSisHorizontal = v; }
  setRange(r){ this.range = r; }
  getRange(){ return this.range; }
  setBlocked(x, y, r){ this.blocked = true; this.blockedAtPoint = {x, y}; this.reason = r; }
  reset(){
    this.blocked = false; this.blockedAtPoint = null; this.firstHindranceAtPt = null;
    this.range = 0; this.reason = ''; this.continuousSlope = false; this.LOSis60Degree = false;
    this.LOSisHorizontal = false;
    this.mapHindrances.clear(); this.smokeHindrances.clear();
    this.vehicleHindrances.clear(); this.obaHindrances.clear();
    this.sourceLocation = null; this.targetLocation = null;
    this.sourceExitHexspine = -1; this.targetEnterHexspine = -1;
  }
  resetreportingonly(){ this.blocked = false; this.blockedAtPoint = null; this.reason = ''; }
}

// VASLGameInterface sans pion : aucun terrain de pion, aucun niveau imposé.
const LOS_SANS_PIONS = {
  getTerrain: () => null, getHexside: () => null, getLevel: () => -99, getSmoke: () => null,
  getVehicles: () => null, getOBA: () => null, getExtraNobaHexes: () => [null, null], pieceOverlay: () => null,
  aDesCotes: false
};
// ── Map (VASL.LOS.Map.Map) : grilles terrain/niveau par pixel et grille
// d'hexagones [colonne][rangée], A1 en (0,0).
const _HILLOCK = 'Hillock', _WALL = 'Wall', _HEDGE = 'Hedge', _BOCAGE = 'Bocage',
      _PARTIALORCHARD = 'PartialOrchard', _STONE_RUBBLE = 'Stone Rubble',
      _WOODEN_RUBBLE = 'Wooden Rubble', _GRAIN = 'Grain', _BRUSH = 'Brush';

class LosMap {
  // Constructeur des cartes géomorphiques de VASL (ASLMap.buildVASLMap).
  constructor(hexWidth, hexHeight, A1CenterX, A1CenterY, widthInHexes, heightInHexes, gridWidth, gridHeight, terrains){
    this.hexWidth = hexWidth; this.hexHeight = hexHeight;
    this.A1CenterX = A1CenterX; this.A1CenterY = A1CenterY;
    this.width = widthInHexes; this.height = heightInHexes;
    this.gridWidth = gridWidth; this.gridHeight = gridHeight;
    this.terrainList = new Array(256).fill(null);
    this.terrainNameMap = terrains;
    for(const t of terrains.values()) this.terrainList[t.type] = t;
    this.terrainGrid = new Uint8Array(gridWidth * gridHeight);
    this.elevationGrid = new Int8Array(gridWidth * gridHeight);
    this.cropconfiguration = (A1CenterX === 0 ? 'HalfHexWidth' : 'FullHexWidth')
                           + (A1CenterY === 0 ? 'LeftHexHalfHeight' : 'LeftHexFullHeight');
    this.hillocks = [];
    this.createtheHexGrid();
  }

  // Les colonnes paires ont « height » hexagones et les impaires un de
  // plus (demi-hexagones haut et bas), ou l'inverse si A1 est un demi-hex.
  createtheHexGrid(){
    const t0 = this.terrainList[0];
    this.hexGrid = new Array(this.width);
    for(let col = 0; col < this.width; col++){
      const extra = this.A1CenterY === 0 ? (col % 2 === 0 ? 1 : 0) : col % 2;
      this.hexGrid[col] = new Array(this.height + extra);
      for(let row = 0; row < this.height + extra; row++)
        this.hexGrid[col][row] = new LosHex(col, row, this.getGEOHexName(col, row), this.getHexCenterPoint(col, row),
          this.hexHeight, this.hexWidth, this, 0, t0);
    }
    for(const c of this.hexGrid) for(const h of c) h.resetHexsideLocationNames();
  }

  getHexCenterPoint(col, row){
    const hw = this.hexWidth, hh = this.hexHeight, x0 = this.A1CenterX < 0 ? 0 : this.A1CenterX;
    let p;
    if(this.A1CenterY === 0){
      const evencol = col % 2 === 0 ? 1 : 0;
      p = {x: x0 + hw * col, y: hh/2 + hh * row - hh/2 * evencol};
    } else if(this.A1CenterY === 65){
      p = {x: x0 + hw * col, y: hh * row + hh/2 * (col % 2)};
    } else {
      p = {x: x0 + hw * col, y: (this.A1CenterY < 0 ? hh/2 : this.A1CenterY) + hh * row - hh/2 * (col % 2)};
    }
    if(p.x >= this.gridWidth) p.x = this.gridWidth - 1;
    return p;
  }

  getGEOHexName(col, row){
    const c = String.fromCharCode(65 + col % 26);
    return c.repeat(1 + Math.trunc(col / 26)) + (row + (col % 2 === 0 ? 1 : 0));
  }

  getHexWidth(){ return this.hexWidth; }
  getHexHeight(){ return this.hexHeight; }
  getGridTerrainCode(x, y){ return this.terrainGrid[x + y * this.gridWidth]; }
  getGridTerrain(x, y){
    return this.onMap(x, y) ? this.terrainList[this.terrainGrid[x + y * this.gridWidth]] : null;
  }
  setGridTerrainCode(code, x, y){ this.terrainGrid[x + y * this.gridWidth] = code; }
  getGridElevation(x, y){ return this.onMap(x, y) ? this.elevationGrid[x + y * this.gridWidth] : 0; }
  setGridElevation(e, x, y){ this.elevationGrid[x + y * this.gridWidth] = e; }

  resetHexTerrain(){
    for(const c of this.hexGrid) for(const h of c) h.resetTerrain();
    this.buildHillocks();
  }

  getWidth(){ return this.width; }
  getHeight(){ return this.height; }
  getGridWidth(){ return this.gridWidth; }
  getGridHeight(){ return this.gridHeight; }
  getA1CenterX(){ return this.A1CenterX; }
  getA1CenterY(){ return this.A1CenterY; }
  getTerrainParCode(t){ return this.terrainList[t]; }
  getTerrain(name){ return this.terrainNameMap.get(name) || null; }
  getHex(col, row){ return this.hexGrid[col][row]; }
  getHexGrid(){ return this.hexGrid; }
  hexOnMap(col, row){ return col >= 0 && col < this.hexGrid.length && row >= 0 && row < this.hexGrid[col].length; }
  getMapConfiguration(){ return this.cropconfiguration; }
  onMap(c, r){ return !(r < 0 || c < 0 || c >= this.gridWidth || r >= this.gridHeight); }

  // gridToHex mis en cache pixel par pixel (passes des overlays).
  hexAuPixel(x, y){
    if(!this._hexPx){
      this._hexListe = this.hexGrid.flat();
      this._hexId = new Map(this._hexListe.map((h, i) => [h, i]));
      this._hexPx = new Int16Array(this.gridWidth * this.gridHeight).fill(-1);
    }
    const k = x + y * this.gridWidth;
    let v = this._hexPx[k];
    if(v === -1){
      const h = this.gridToHex(x, y);
      v = this._hexPx[k] = h ? this._hexId.get(h) : -2;
    }
    return v >= 0 ? this._hexListe[v] : null;
  }

  getAdjacentHex(h, hexside){
    if(hexside > 5) return null;
    let col = h.getColumnNumber(), row = h.getRowNumber();
    const even = col % 2 === 0;
    const cfg = this.cropconfiguration;
    if(cfg.includes('LeftHexFullHeight') && this.A1CenterY !== 65){
      switch(hexside){
        case 0: row -= 1; break;
        case 1: col += 1; row += even ? 0 : -1; break;
        case 2: col += 1; row += even ? 1 : 0; break;
        case 3: row += 1; break;
        case 4: col -= 1; row += even ? 1 : 0; break;
        case 5: col -= 1; row += even ? 0 : -1; break;
      }
    } else if(cfg.includes('HalfHeight') || this.A1CenterY === 65){
      switch(hexside){
        case 0: row -= 1; break;
        case 1: col += 1; row += even ? -1 : 0; break;
        case 2: col += 1; row += even ? 0 : 1; break;
        case 3: row += 1; break;
        case 4: col -= 1; row += even ? 0 : 1; break;
        case 5: col -= 1; row += even ? -1 : 0; break;
      }
    }
    return this.hexOnMap(col, row) ? this.getHex(col, row) : null;
  }

  gridToHex(x, y){
    if(!this.onMap(x, y)) return null;
    const g = this.hexGrid, hw = this.hexWidth, hh = this.hexHeight;
    // hexGrid[c][r] de Java : hors des bornes, l'exception renvoie null.
    const G = (c, r) => {
      const a = g[c];
      if(!a || r < 0 || r >= a.length) throw new RangeError('hors grille');
      return a[r];
    };
    try {
      const z = Math.trunc((x - (this.A1CenterX < 0 ? 0 : this.A1CenterX)) / (hw / 3.0));
      let row, col;
      if((z - 1) % 3 === 0){
        col = Math.ceil((z - 1.0) / 3.0);
        if(this.A1CenterY === 0) row = Math.trunc(col % 2 === 0 ? (y + hh/2) / hh : y / hh);
        else row = Math.trunc(col % 2 === 0 ? y / hh : (y + hh/2) / hh);
        if(G(col, row).contains(x, y)) return G(col, row);
        if(col < g.length && row-1 >= 0 && row-1 < g[col].length && G(col, row-1).contains(x, y)) return G(col, row-1);
        if(col < g.length && row+1 < g[col].length && G(col, row+1).contains(x, y)) return G(col, row+1);
        if(col+1 < g.length && row < g[col+1].length && G(col+1, row).contains(x, y)) return G(col+1, row);
        if(col+1 < g.length && row-1 >= 0 && row-1 < g[col+1].length && G(col+1, row-1).contains(x, y)) return G(col+1, row-1);
        if(col+1 < g.length && row+1 < g[col+1].length && G(col+1, row+1).contains(x, y)) return G(col+1, row+1);
        if(col-1 >= 0 && row < g[col-1].length && G(col-1, row).contains(x, y)) return G(col-1, row);
        if(col-1 >= 0 && row-1 >= 0 && row-1 < g[col-1].length && G(col-1, row-1).contains(x, y)) return G(col-1, row-1);
        if(col-1 >= 0 && row+1 < g[col-1].length){
          if(G(col-1, row+1).contains(x, y)) return G(col-1, row+1);
        } else if(col % 2 === 0 && (this.cropconfiguration.includes('Normal') || this.cropconfiguration === 'FullHex')){
          try {
            if(col+1 < g.length && row+1 < g[col+1].length){
              if(G(col+1, row+1).contains(x, y)) return G(col+1, row+1);
              if(row < g[col+1].length) return G(col+1, row).contains(x, y) ? G(col+1, row) : null;
            }
          } catch(e){
            if(col+1 < g.length && row < g[col+1].length) return G(col+1, row).contains(x, y) ? G(col+1, row) : null;
          }
        } else {
          if(col+1 < g.length && row-1 >= 0 && row-1 < g[col+1].length){
            if((row-1 >= 0 && G(col+1, row-1).contains(x, y)) || row === this.height) return G(col+1, row-1);
            if(row < g[col+1].length){
              if(G(col+1, row).contains(x, y)) return G(col+1, row);
            } else return null;
          }
        }
        if(G(col, row).containsExtended(x, y)) return G(col, row);
      } else {
        col = Math.ceil(z / 3.0);
        if(this.A1CenterY === 0 || this.A1CenterY === 65)
          row = Math.trunc(col % 2 === 0 ? (y + hh/2) / hh : y / hh);
        else
          row = Math.trunc(col % 2 === 0 ? y / hh : (y + hh/2) / hh);
        return G(col, row);
      }
    } catch(e){
      return null;
    }
    return null;
  }

  static range(source, target, mapconfig){
    const dirX = target.getColumnNumber() > source.getColumnNumber() ? 1 : -1;
    const dirY = target.getRowNumber() > source.getRowNumber() ? 1 : -1;
    let rng = 0, currentRow = source.getRowNumber(), currentCol = source.getColumnNumber();
    let pair;
    if(mapconfig.includes('LeftHexFullHeight')) pair = c => (c % 2 === 0 && dirY === 1) || (c % 2 === 1 && dirY === -1);
    else if(mapconfig.includes('HalfHeight')) pair = c => (c % 2 === 1 && dirY === 1) || (c % 2 === 0 && dirY === -1);
    else return rng;
    while(currentCol !== target.getColumnNumber()){
      if(currentRow !== target.getRowNumber() && pair(currentCol)) currentRow += dirY;
      currentCol += dirX;
      rng += 1;
    }
    if(currentRow !== target.getRowNumber()) rng += Math.abs(target.getRowNumber() - currentRow);
    return rng;
  }

  // ══ LOS : de la source à la cible, colonne de pixels par colonne ══
  LOS(source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, vaslgameinterface){
    result.reset();
    result.setSourceLocation(source);
    result.setTargetLocation(target);
    if(source === target){ result.setRange(0); return; }

    const status = new LosStatus(this, source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result,
                                 vaslgameinterface || LOS_SANS_PIONS);
    if(this.checkSameHexSmokeRule(status, result)) return;
    if(this.checkSameHexRule(status, result)) return;

    status.currentCol = status.sourceX;
    for(let col = 0; col < status.numCols; col++){
      status.currentRow = Math.trunc(status.enter);
      const numRows = Math.abs(Math.trunc(status.exit) - Math.trunc(status.enter)) + 1;
      for(let row = 0; row < numRows; row++){
        status.currentTerrain = this.getGridTerrain(status.currentCol, status.currentRow);
        status.groundLevel = this.getGridElevation(status.currentCol, status.currentRow);
        if(status.sourceHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow))
          status.tempHex = status.sourceHex;
        else if(status.targetHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow))
          status.tempHex = status.targetHex;
        else
          status.tempHex = this.gridToHex(status.currentCol, status.currentRow);
        if(status.tempHex == null) return;

        if(!status.LOSLeavesBuilding && status.currentTerrain != null && !status.currentTerrain.isBuilding())
          status.LOSLeavesBuilding = true;

        let adjacentHexes = null;
        if(status.LOSisHorizontal || status.LOSis60Degree) adjacentHexes = this.getAdjacentHexes(status, status.tempHex);

        if(adjacentHexes == null){
          if((status.currentRow === 0 || status.currentRow + 1 === this.getGridHeight())
             && status.tempHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow)){
            if(this.checkLOSOnHexsideRule(result, status, status.tempHex)) return;
          }
          if(this.applyLOSRules(status, result)) return;
        } else {
          if(adjacentHexes.top != null && adjacentHexes.top.getExtendedHexBorder().contains(status.currentCol, status.currentRow)){
            if(this.checkLOSOnHexsideRule(result, status, adjacentHexes.top)) return;
          }
          if(adjacentHexes.bottom != null && adjacentHexes.bottom.getExtendedHexBorder().contains(status.currentCol, status.currentRow)){
            if(this.checkLOSOnHexsideRule(result, status, adjacentHexes.bottom)) return;
          }
        }
        status.currentRow += status.rowDir;
      }
      status.enter = status.exit;
      status.currentCol += status.colDir;
      if(col + 1 === status.numCols) status.exit = status.targetY;
      else status.exit += status.deltaY;
    }
    result.setContinuousSlope(status.continuousSlope);
  }

  checkLOSOnHexsideRule(result, status, hex){
    status.tempHex = hex;
    if(_eqJ(hex, status.sourceHex) || _eqJ(hex, status.targetHex)) return false;
    else if(status.currentTerrain.isHexsideTerrain() && hex.getCenterLocation().getTerrain().isInherentTerrain()){
      if(this.applyLOSRules(status, result)) return true;
    }
    const ct = status.tempHex.getCenterLocation().getTerrain();
    if(ct.isInherentTerrain()){
      if(!((ct.getName() === 'Dense Jungle' || ct.getName() === 'Bamboo') && status.currentTerrain.getLOSCategory() === 'WATER'))
        status.currentTerrain = ct;
    }
    const hexside = status.tempHex.getLocationHexside(status.tempHex.getNearestLocation(status.currentCol, status.currentRow));
    if(hexside !== -1 && status.tempHex.getPartialOrchards()[hexside]){
      const t = status.tempHex.getHexsideTerrain(hexside);
      if(t != null && t.isHexsideTerrain() && t.getName().includes(_PARTIALORCHARD)) status.currentTerrain = t;
    }
    return this.applyLOSRules(status, result);
  }

  getAdjacentHexes(status, rangeHex){
    const sourceHex = status.source.getHex();
    if(status.sourceHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow)
       || status.targetHex.getExtendedHexBorder().contains(status.currentCol, status.currentRow)
       || (status.source.getHex().getCenterLocation() === status.source
           && LosMap.range(sourceHex, rangeHex, this.getMapConfiguration()) % 2 === 0))
      return null;
    for(let x = 0; x < 6; x++){
      const t = this.getAdjacentHex(rangeHex, x);
      if(t != null && t.getExtendedHexBorder().contains(status.currentCol, status.currentRow)
         && t !== status.sourceHex && t !== status.targetHex)
        return {top: rangeHex, bottom: t};
    }
    for(let x = 0; x < 6; x++){
      if(rangeHex.getLocationHexside(rangeHex.getNearestLocation(status.currentCol, status.currentRow)) === x){
        const t = this.getAdjacentHex(rangeHex, x);
        if(t != null && t !== status.sourceHex && t !== status.targetHex) return {top: rangeHex, bottom: t};
      }
    }
    return null;
  }

  applyLOSRules(status, result){
    const gi = status.vaslGameInterface;
    if(gi.getTerrain(status.tempHex) != null){
      const ct = this.getTerrain(gi.getTerrain(status.tempHex));
      if(status.tempHex !== status.sourceHex && status.tempHex !== status.targetHex){
        if(!ct.isEntrenchmentTerrain()) this.applyInterveningCounterValues(status);
      } else if(!ct.isRowhouseFactoryWallOrBreach()) this.applyInterveningCounterValues(status);
    }
    if(status.currentTerrain == null) return true;
    status.currentTerrainHgt = status.currentTerrain.getHeight();
    const hexsides = status.getHexsideCrossed(status.tempHex);

    let previousTerrain = null, insamehex = false;
    if(!_eqJ(status.tempHex, status.currentHex)){
      let newequalsprevioushex = false;
      previousTerrain = status.currentHex.getCenterLocation().getTerrain();
      if(status.previousHex == null) status.previousHex = status.currentHex;
      else if(!_eqJ(status.previousHex, status.tempHex) && status.currentHex != null) status.previousHex = status.currentHex;
      else newequalsprevioushex = true;
      status.currentHex = status.tempHex;
      status.rangeToSource = LosMap.range(status.currentHex, status.sourceHex, this.getMapConfiguration());
      status.rangeToTarget = LosMap.range(status.currentHex, status.targetHex, this.getMapConfiguration());
      status.bridge = status.currentHex.getBridge();
      if(status.bridge != null){
        status.bridgeArea = status.bridge.getShape();
        status.bridgeRoadArea = status.bridge.getRoadShape();
      }
      if(Math.abs(status.sourceElevation - status.currentHex.getBaseLevelofHex()) !== status.rangeToSource)
        status.continuousSlope = false;
      let sourceadj = 0, targetadj = 0;
      if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1) sourceadj = -0.5;
      if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1) targetadj = -0.5;
      if(this.losFollowsDepression(status, sourceadj, targetadj, newequalsprevioushex))
        status.ignoreGroundLevelHex = status.currentHex;
      status.setHillockStatus();
    } else insamehex = true;

    // Terrain de côté porté par un pion (haie, mur, congère, brèche…).
    let usehexside = -1;
    for(const hexside of hexsides){
      let counter = null;
      if(gi.getHexside(status.currentHex) != null){
        counter = gi.getHexside(status.currentHex);
        usehexside = counter.getHexside();
      } else if(gi.getHexside(status.previousHex) != null){
        counter = gi.getHexside(status.previousHex);
        usehexside = status.previousHex.getOppositeHexside(counter.getHexside());
      } else if(gi.aDesCotes){
        for(let hside = 0; hside < 6; hside++){
          if(status.currentHex.isPointOnHexside(status.currentCol, status.currentRow, hside, 2, 3)){
            const testhex = this.getAdjacentHex(status.currentHex, hside);
            if(gi.getHexside(testhex) != null){
              counter = gi.getHexside(testhex);
              if(counter.getHexside() === testhex.getOppositeHexside(hside)) usehexside = hside;
              else counter = null;
              break;
            }
          }
        }
      }
      if(counter != null){
        const ca = counter.getCoverArch();
        if(counter.getName().includes('Overlay')){
          if(usehexside === -1) status.currentTerrain = this.getTerrainFromOverlayCounterImage(status, counter, counter.getCoverArch());
          else if(status.currentHex.isPointOnHexside(status.currentCol, status.currentRow, usehexside, 2, 3))
            status.currentTerrain = this.getTerrainFromOverlayCounterImage(status, counter, usehexside);
        } else if(hexside === ca) status.currentTerrain = this.getTerrain(counter.getTerrain());
        else if(status.LOSis60Degree || status.LOSisHorizontal){
          // getNearestLocation(rangée, colonne) : arguments inversés, comme VASL.
          if(status.sourceExitHexsides[0] === ca || status.sourceExitHexsides[1] === ca
             || status.targetEnterHexsides[0] === ca || status.targetEnterHexsides[1] === ca
             || status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentRow, status.currentCol)) === ca)
            status.currentTerrain = this.getTerrain(counter.getTerrain());
        }
        status.currentTerrainHgt = status.currentTerrain.getHeight();
        break;
      }
    }

    if(this.checkNVRRule(status, result)) return true;
    if(this.checkDepressionRule(status, result)) return true;
    if(this.checkBuildingRestrictionRule(status, result, previousTerrain)) return true;
    if(status.currentTerrain.isHexsideTerrain() && !status.currentTerrain.isCliff()){
      if(this.checkHexsideTerrainRule(status, result)) return true;
    }
    if(this.checkRBrrembankments(status, result, hexsides)) return true;
    if(this.checkPartialOrchards(status, result, hexsides)) return true;
    if(this.checkHexSmokeRule(status, result)) return true;
    if(this.checkVehicleHindranceRule(status, result, insamehex)) return true;
    if(this.checkOBAHindranceRule(status, result)) return true;

    let test1 = false, test2 = false, test3 = false, test4 = false, test5 = false;
    if(!_eqJ(status.currentHex, status.sourceHex)
       && (status.range !== status.rangeToTarget || (status.range === status.rangeToTarget && !status.source.isCenterLocation())))
      test1 = true;
    if(test1){
      if(!_eqJ(status.currentHex, status.targetHex)
         && (status.range !== status.rangeToSource || (status.range === status.rangeToSource && !status.target.isCenterLocation())))
        test2 = true;
      else if(_eqJ(status.currentHex, status.sourceHex) && !status.currentTerrain.isOpen()
              && !status.currentTerrain.isHexsideTerrain() && !status.source.isCenterLocation())
        test3 = true;
      else if(_eqJ(status.currentHex, status.targetHex) && !status.currentTerrain.isOpen()
              && !status.currentTerrain.isHexsideTerrain() && !status.target.isCenterLocation())
        test4 = true;
      else if((status.range === status.rangeToSource && (status.LOSis60Degree || status.LOSisHorizontal))
              && !status.currentTerrain.isOpen() && !status.currentTerrain.isHexsideTerrain() && !status.target.isCenterLocation())
        test5 = true;
    }
    if(test1 && (test2 || test3 || test4 || test5)){
      if(status.currentTerrain.isInherentTerrain() && status.currentHex.getCenterLocation().getTerrain() !== status.currentTerrain){
        if(gi.getTerrain(status.currentHex) == null){
          if(this.inherentspilltest(status)) return false;
        }
      }
      if(this.checkBridgeHindranceRule(status, result)) return true;
      if(this.checkGroundLevelRule(status, result)) return true;
      if(this.checkSplitTerrainRule(status, result)) return true;
      if(this.checkHalfLevelTerrainRule(status, result)) return true;
      if(this.checkTerrainIsHigherRule(status, result)) return true;
      if(this.checkTerrainHeightRule(status, result)) return true;
      if(this.checkBlindHexRule(status, result)) return true;
      if(this.checkHillockRule(status, result)) return true;
    }
    if(status.blocked){
      result.setBlocked(status.currentCol, status.currentRow, status.reason);
      return true;
    }
    return false;
  }

  applyInterveningCounterValues(status){
    const gi = status.vaslGameInterface;
    const t = this.getTerrain(gi.getTerrain(status.tempHex));
    if(t.isInherentTerrain() || t.isOpen()) status.currentTerrain = t;
    else {
      if(gi.pieceOverlay(status.currentHex) == null) return;
      status.currentTerrain = this.getTerrainFromOverlayCounterImage(status, null, -1);
    }
    status.currentTerrainfromCounter = true;
    const lv = status.vaslGameInterface.getLevel(status.tempHex);
    if(lv !== -99) status.groundLevel = lv;
  }

  checkSameHexRule(status, result){
    if(!_eqJ(status.source.getHex(), status.target.getHex())) return false;
    result.setRange(0);
    if(status.source.isCenterLocation() || status.target.isCenterLocation()){
      const sp = status.source.getLOSPoint();
      if(status.source.getTerrain().isBuildingTerrain() && status.target.getTerrain().isBuildingTerrain()){
        if(Math.abs(status.source.getLevelInHex() - status.target.getLevelInHex()) > 1 || !status.source.getHex().hasStairway()){
          result.setBlocked(Math.trunc(sp.x), Math.trunc(sp.y), 'Crosses building level or no stairway');
          return true;
        }
      }
      if(((status.source.getTerrain().isBridge() || status.source.getTerrain().isTunnel()) && status.target.isCenterLocation())
         || ((status.target.getTerrain().isBridge() || status.source.getTerrain().isTunnel()) && status.source.isCenterLocation())){
        result.setBlocked(Math.trunc(sp.x), Math.trunc(sp.y), 'Cannot see location under the bridge');
        return true;
      }
      return true;
    }
    return false;
  }

  // VASL lit le pixel de l'image du pion overlay posé dans l'hexagone
  // courant. Ici : le terrain du pion sur son côté, ailleurs celui de la
  // carte ; sans pièce dans l'hexagone, terrain dégagé (image absente).
  getTerrainFromOverlayCounterImage(status, counter, cote){
    const gi = status.vaslGameInterface;
    if(gi.pieceOverlay(status.currentHex) == null) return this.getTerrain('Open Ground');
    let t = null;
    if(counter == null) t = this.getTerrain(gi.getTerrain(status.tempHex));
    else if(cote >= 0 && cote < 6 && status.currentHex.isPointOnHexside(status.currentCol, status.currentRow, cote, 2, 3))
      t = this.getTerrain(counter.getTerrain());
    else t = this.getGridTerrain(status.currentCol, status.currentRow);
    return t || this.getTerrain('Open Ground');
  }

  checkSameHexSmokeRule(status, result){
    if(_eqJ(status.source.getHex(), status.target.getHex())){
      const fumees = status.vaslGameInterface.getSmoke(status.source.getHex());
      if(fumees != null && fumees.size){
        let h = 0;
        for(const s of fumees){
          if(LosMap.locationInSmoke(status.source, s)) h += s.getHindrance() + 1;
          if(LosMap.locationInSmoke(status.target, s)) h += s.getHindrance();
        }
        if(h > 5) result.setBlocked(status.sourceX, status.sourceY, 'Hindrance total of six or more (B.10)');
        else result.addSmokeHindrance(status.sourceHex, h, status.sourceX, status.sourceY);
      }
    }
    return result.isBlocked();
  }

  static locationInSmoke(l, s){
    return l.getLevelInHex() >= s.getLocation().getLevelInHex() && l.getLevelInHex() < s.getLocation().getLevelInHex() + s.getHeight();
  }

  checkHexSmokeRule(status, result){
    const gi = status.vaslGameInterface, hex = status.currentHex;
    const fumees = gi.getSmoke(hex);
    if(fumees == null || !fumees.size) return false;
    let h = 0, sourceInSmoke = false;
    const sH = status.source.getAbsoluteHeight(), tH = status.target.getAbsoluteHeight();
    for(const s of fumees){
      const bas = s.getLocation().getAbsoluteHeight(), haut = bas + s.getHeight();
      if(_eqJ(hex, status.sourceHex)){
        if(LosMap.locationInSmoke(status.source, s)){ h += s.getHindrance() + 1; sourceInSmoke = true; }
        else if(sH === haut && tH < sH) h += s.getHindrance();
      } else if(_eqJ(hex, status.targetHex)){
        if(LosMap.locationInSmoke(status.source, s)) h += s.getHindrance();
        else if(tH === haut && sH < tH) h += s.getHindrance();
      } else {
        if(sH >= bas && sH <= haut && tH >= bas && tH <= haut) h += s.getHindrance();
        else if(LosMap.isBlindHex(status, s.getHeight())) h += s.getHindrance();
      }
      // Incendie d'épave : gêne de 2.
      if(s.getName() === 'Blaze' && gi.getVehicles(hex) != null && gi.getVehicles(hex).size) h = 2;
    }
    if(h > 0){
      h = sourceInSmoke ? Math.min(h, 4) : Math.min(h, 3);
      result.addSmokeHindrance(hex, h, status.currentCol, status.currentRow);
      if(result.isBlocked()) return true;
    }
    return false;
  }

  checkVehicleHindranceRule(status, result, insamehex){
    const gi = status.vaslGameInterface, hex = status.currentHex;
    const vehicules = gi.getVehicles(hex);
    if(vehicules == null || !vehicules.size) return false;
    if(result.checkHindranceExistsAtThisRange(status.rangeToSource)) return false;
    let h = 0;
    for(const v of vehicules){
      if(!_eqJ(hex, status.sourceHex) && !_eqJ(hex, status.targetHex)){
        if(status.source.getAbsoluteHeight() === status.target.getAbsoluteHeight()
           && status.source.getAbsoluteHeight() === v.getLocation().getAbsoluteHeight()){
          if(status.slopes || (status.startsOnHillock && status.endsOnHillock && status.crossingHillock == null)) return false;
          if(v.getLocation().isCenterLocation() || v.getLocation() === hex.getNearestLocation(status.currentCol, status.currentRow)){
            // La source et la cible doivent voir le véhicule (une fois par hexagone).
            if(!insamehex){
              const r1 = new LosResult(), r2 = new LosResult();
              this.LOS(status.source, status.useAuxSourceLOSPoint, v.getLocation(), false, r1, gi);
              this.LOS(status.target, status.useAuxTargetLOSPoint, v.getLocation(), false, r2, gi);
              if(!r1.isBlocked() && !r2.isBlocked()) h++;
            }
          }
        }
      }
      if(h > 0){
        const fumees = gi.getSmoke(hex);
        if(fumees != null) for(const s of fumees) if(s.getName() === 'Blaze') h = 0;
      }
    }
    if(h > 0){
      result.addVehicleHindrance(hex, h, status.currentCol, status.currentRow);
      if(result.isBlocked()) return true;
    }
    return false;
  }

  checkOBAHindranceRule(status, result){
    const gi = status.vaslGameInterface, obas = gi.getOBA();
    if(obas == null || !obas.size) return false;
    const cfg = this.getMapConfiguration();
    const ajoute = oba => { result.addOBAHindrance(oba, status.currentCol, status.currentRow); return result.isBlocked(); };
    for(const oba of obas){
      if(oba.getName().includes('Sm ') || oba.getName().includes('WP')) continue;
      if(oba.getisBarrage()){
        const bh = this.getBarrageHexes(oba);
        for(let x = 0; x < 9; x++){
          if(bh[x] == null) continue;
          if(_eqJ(this.getHexParNom(bh[x]), status.currentHex)){
            if(ajoute(oba)) return true;
            break;
          }
        }
      } else {
        const c = oba.getHex().getCenterLocation().getAbsoluteHeight(), haut = c + oba.getBlastHeight();
        const sH = status.source.getAbsoluteHeight(), tH = status.target.getAbsoluteHeight();
        if(LosMap.range(status.currentHex, oba.getHex(), cfg) <= oba.getBlastAreaRadius()){
          if(this.locationInOBA(status.source, oba) || this.locationInOBA(status.target, oba)){ if(ajoute(oba)) return true; }
          else if(sH >= c && sH < haut && tH >= c && tH < haut){ if(ajoute(oba)) return true; }
          else if(LosMap.isBlindHex(status, oba.getBlastHeight())){ if(ajoute(oba)) return true; }
        } else if(oba.getName().includes('Naval')){
          const n = gi.getExtraNobaHexes(oba.getRotation(), this.getAllAdjacentHexes(oba.getHex()));
          if(status.currentHex === n[0] || status.currentHex === n[1]){ if(ajoute(oba)) return true; }
        }
      }
    }
    return false;
  }

  locationInOBA(l, oba){
    const c = oba.getHex().getCenterLocation().getLevelInHex();
    return LosMap.range(oba.getHex(), l.getHex(), this.getMapConfiguration()) <= oba.getBlastAreaRadius()
      && l.getLevelInHex() >= c && l.getLevelInHex() < c + oba.getBlastHeight();
  }

  // Map.getHex(String) : le premier hexagone de ce nom.
  getHexParNom(nom){
    if(nom == null) return null;
    const n = nom.toLowerCase();
    for(const c of this.hexGrid) for(const h of c) if(h.getName().toLowerCase() === n) return h;
    return null;
  }

  getAllAdjacentHexes(h){
    const r = [h.getName()];
    for(let x = 0; x < 6; x++){
      const a = this.getAdjacentHex(h, x);
      r.push(a ? a.getName() : 'offboard');
    }
    return r;
  }

  // Barrage : neuf hexagones, deux bras de quatre selon la rotation (1 à 6).
  getBarrageHexes(oba){
    const r = new Array(9).fill(null);
    r[0] = oba.getHex().getName();
    const rot = oba.getRotation(), alt = oba.getName().includes('alt');
    let bras;
    if(rot === 1 || rot === 3 || rot === 5){
      const s = rot === 1 ? 0 : rot === 3 ? 1 : 2;
      bras = () => [s, LosHex.getOppositeHexside(s)];
    } else if(rot === 2 || rot === 4 || rot === 6){
      const paires = {2: [[0, 4], [1, 3]], 4: [[1, 5], [2, 4]], 6: [[3, 5], [2, 0]]}[rot];
      const [a, b] = alt ? [paires[1], paires[0]] : paires;
      bras = x => (x === 1 || x === 3) ? a : b;
    } else return r;
    let h1 = oba.getHex(), h2 = oba.getHex();
    for(let x = 1; x < 5; x++){
      const [c1, c2] = bras(x);
      const a1 = this.getAdjacentHex(h1, c1);
      if(a1 != null){ r[x] = a1.getName(); h1 = a1; }
      const a2 = this.getAdjacentHex(h2, c2);
      if(a2 != null){ r[x + 4] = a2.getName(); h2 = a2; }
    }
    return r;
  }

  // Map.getAllHexesInRadiusOf : de proche en proche, par les noms. Près
  // du bord, le tableau n'est pas rempli et VASL échoue (NPE) : le pion
  // est alors ignoré.
  getAllHexesInRadiusOf(origine, rayon){
    const max = Math.trunc((rayon / 2) * (1 + rayon) * 6) + 1;
    const hexes = new Array(max).fill(null);
    hexes[0] = origine.getName();
    let x = 0;
    for(let i = 0; i < max; i++){
      const h = this.getHexParNom(hexes[i]);
      if(!h) throw new TypeError('NPE');
      for(const n of this.getAllAdjacentHexes(h)){
        if(n === 'offboard' || hexes.includes(n)) continue;
        const a = this.getHexParNom(n);
        if(a && LosMap.range(a, origine, this.getMapConfiguration()) <= rayon && x + 1 < max) hexes[++x] = n;
      }
    }
    return hexes;
  }

  checkBuildingRestrictionRule(status, result, previousTerrain){
    let rooftopadj = 0, rangehex = null;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1){ rooftopadj = -1; rangehex = status.sourceHex; }
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1){ rooftopadj = -1; rangehex = status.targetHex; }
    if(!status.LOSLeavesBuilding && !_eqJ(status.currentHex, status.sourceHex)){
      if(status.currentTerrain.isBuildingTerrain()){
        if(!(status.sourceElevation > status.currentTerrainHgt + status.groundLevel)){
          if(status.sourceElevation !== status.targetElevation){
            status.reason = 'LOS must leave the building before leaving the source hex to see a location with a different elevation (A6.8 Example 2)';
            status.blocked = true;
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
          }
        }
      } else if(!status.LOSLeavesBuilding && (status.target.getTerrain().isFactoryTerrain() | status.source.getTerrain().isFactoryTerrain())){
        if(!_eqJ(status.sourceHex, status.targetHex)){
          if(status.LOSis60Degree || status.LOSisHorizontal){
            if(status.rangeToSource % 2 === 0)
              return this.isRooftopLOSBlocked(status.currentHex, status.currentTerrain, status.currentTerrainHgt, status, result, rooftopadj);
            if(!this.isRooftopLOSBlocked(status.currentHex, status.currentTerrain, status.currentTerrainHgt, status, result, rooftopadj))
              return false;
            if(rangehex != null){
              status.reason = ''; status.blocked = false; result.resetreportingonly();
              return this._toitDuVoisin(status, result, rooftopadj);
            }
            return true;
          }
          return this.isRooftopLOSBlocked(status.currentHex, status.currentTerrain, status.currentTerrainHgt, status, result, rooftopadj);
        }
      }
    }
    return false;
  }

  // Côtés 1 et 4 après l'épine de sortie : le voisin touché doit être sans toit.
  _cotesTest(result){
    let a = result.sourceExitHexspine + 1, b = result.sourceExitHexspine + 4;
    if(a > 5) a -= 6;
    if(b > 5) b -= 6;
    return [a, b];
  }
  _toitDuVoisin(status, result, rooftopadj){
    const [a, b] = this._cotesTest(result);
    const touche = status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentCol, status.currentRow));
    if(touche === a || touche === b){
      const t = this.getAdjacentHex(status.currentHex, touche);
      return this.isRooftopLOSBlocked(t, t.getCenterLocation().getTerrain(), t.getCenterLocation().getTerrain().getHeight(), status, result, rooftopadj);
    }
    return false;
  }

  isRooftopLOSBlocked(hex, terrain, terrainHgt, status, result, rooftopadj){
    if(terrain.getName().includes('Rubble')) return false;
    if((status.source.getTerrain().isRooftop() && !status.target.getTerrain().isRooftop())
       && (!hex.getCenterLocation().getTerrain().isRoofless()
           && (!_eqJ(hex, status.targetHex) || (_eqJ(hex, status.targetHex) && status.range === 1)))){
      status.reason = 'LOS from/to Factory Rooftop within Factory only exists in same hex ground level location (B23.87)';
      status.blocked = true;
      result.setBlocked(status.currentCol, status.currentRow, status.reason);
      return true;
    } else if((status.target.getTerrain().isRooftop() && !status.source.getTerrain().isRooftop())
       && ((!hex.getCenterLocation().getTerrain().isRoofless() && status.rangeToSource === 1
            && !status.sourceHex.getCenterLocation().getTerrain().isRoofless())
           || (!hex.getCenterLocation().getTerrain().isRoofless() && !_eqJ(hex, status.targetHex)
               && (terrainHgt + terrain.getHeight() >= status.targetElevation + rooftopadj)))){
      status.reason = status.rangeToSource === 1
        ? 'LOS must leave the building before leaving the source hex to see a location with a different elevation (A6.8 Example 2)'
        : 'LOS from/to Factory Rooftop within Factory only exists in same hex ground level location (B23.87)';
      status.blocked = true;
      result.setBlocked(status.currentCol, status.currentRow, status.reason);
      return true;
    }
    return false;
  }

  // Pas de nuit (ScenInfo) dans cette page : le NVR ne s'applique pas.
  checkNVRRule(){ return false; }

  checkDepressionRule(status, result){
    let sourceadj = 0, targetadj = 0;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1) sourceadj = -0.5;
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1) targetadj = -0.5;
    let test1 = false, test2 = false;
    const fS = status.sourceElevation + sourceadj, fT = status.targetElevation + targetadj;
    const ch = status.currentHex, gl = status.groundLevel;
    const distCentre = (hex) => _dist(hex.getHexCenter().x, hex.getHexCenter().y, status.currentCol, status.currentRow)
      > _dist(hex.getHexCenter().x, hex.getHexCenter().y, ch.getHexCenter().x, ch.getHexCenter().y);

    if(status.exitsSourceDepression){
      let base = ch.getBaseLevelofHex(), dep = ch.isDepressionTerrain();
      if(ch.getCenterLocation().getTerrain().isBridge()){ dep = true; base = base - 1; }
      const pont = this.losCrossingBridgeDepiction(status) && !this.exitsByRoadHexside(status);
      test1 = status.rangeToTarget > (fT - fS) && !_eqJ(ch, status.targetHex)
        && (!(dep && ((gl === base || fS >= gl) || pont)) && !(!dep && gl <= fS));
      if(test1){
        if(status.range === 1 && status.exitsDepressionTerrainHexside()) test1 = false;
        if(fT - fS === 0 && !_eqJ(ch, status.targetHex) && ((dep && (gl === base || pont)) || (!dep && gl <= fT))) test1 = false;
        if(this.specialtestDepressionGroundLevelOnExit(status)) test1 = false;
      }
      if(!test1)
        test2 = status.rangeToTarget === 1 && (dep && !(gl === base || (fS >= gl || fT > gl)) && !pont)
          && distCentre(status.targetHex);
      if(!test1 && !test2){
        if(_eqJ(ch, status.sourceHex) && status.currentTerrain.isCliff()){
          if(this.checkBlindHexRule(status, result)) return true;
        }
      }
      if(test1 || test2){
        status.blocked = true;
        status.reason = 'Exits depression before range/elevation restrictions are satisfied (A6.3)';
        result.setBlocked(status.currentCol, status.currentRow, status.reason);
        return true;
      }
      if(status.exitHexsideIsCrest() && (status.LOSis60Degree || status.LOSisHorizontal) && fS >= fT && !status.source.getTerrain().isBridge()){
        status.blocked = true;
        status.reason = 'Exits Crest Line - Depression hexside at vertex (B19.51)';
        result.setBlocked(status.currentCol, status.currentRow, status.reason);
        return true;
      }
    }
    if(status.entersTargetDepression){
      let base = ch.getBaseLevelofHex(), dep = ch.isDepressionTerrain();
      if(ch.getCenterLocation().getTerrain().isBridge()){ dep = true; base = base - 1; }
      const pont = this.losCrossingBridgeDepiction(status) && !this.entersByRoadHexside(status);
      test1 = status.rangeToSource > (fS - fT) && !_eqJ(ch, status.sourceHex)
        && (!(dep && ((gl === base || fT >= gl) || pont)) && !(!dep && gl <= fT));
      if(test1){
        if(status.range === 1 && status.exitsDepressionTerrainHexside()) test1 = false;
        if(fS - fT === 0 && !_eqJ(ch, status.sourceHex) && ((dep && (gl === base || pont)) || (!dep && gl <= fT))) test1 = false;
        if(this.specialtestDepressionGroundLevelOnEntry(status)) test1 = false;
      }
      if(!test1)
        test2 = status.rangeToSource === 1 && (dep && !(gl === base || (fT >= gl || fS > gl)) && !pont)
          && distCentre(status.sourceHex);
      if(!test1 && !test2){
        if(_eqJ(ch, status.targetHex) && status.currentTerrain.isCliff()){
          if(this.checkBlindHexRule(status, result)) return true;
        }
      }
      if(test1 || test2){
        status.blocked = true;
        status.reason = 'Does not enter depression while range/elevation restrictions are satisfied (A6.3)';
        result.setBlocked(status.currentCol, status.currentRow, status.reason);
        return true;
      }
      if(status.enterHexsideIsCrest() && (status.LOSis60Degree || status.LOSisHorizontal) && fT >= fS && !status.target.getTerrain().isBridge()){
        status.blocked = true;
        status.reason = 'Enters Crest Line - Depression hexside at vertex (B19.51)';
        result.setBlocked(status.currentCol, status.currentRow, status.reason);
        return true;
      }
    }
    return false;
  }

  specialtestDepressionGroundLevelOnEntry(status){
    if(status.rangeToSource !== 1) return false;
    if(status.sourceElevation <= status.currentHex.getBaseLevelofHex()) return false;
    const s = status.sourceHex.getHexCenter(), c = status.currentHex.getHexCenter();
    return status.currentHex.isDepressionTerrain()
      && _dist(s.x, s.y, status.currentCol, status.currentRow) < _dist(s.x, s.y, c.x, c.y);
  }
  specialtestDepressionGroundLevelOnExit(status){
    if(status.rangeToTarget !== 1) return false;
    if(status.targetElevation <= status.currentHex.getBaseLevelofHex()) return false;
    const t = status.targetHex.getHexCenter(), c = status.currentHex.getHexCenter();
    return status.currentHex.isDepressionTerrain()
      && _dist(t.x, t.y, status.currentCol, status.currentRow) < _dist(t.x, t.y, c.x, c.y);
  }
  losCrossingBridgeDepiction(status){ return status.currentTerrain.isBridge() || status.currentTerrain.isRoad(); }
  entersByRoadHexside(status){
    if(status.LOSis60Degree || status.LOSisHorizontal) return false;
    if(status.previousHex == null) return false;
    for(let x = 0; x < 6; x++){
      const a = this.getAdjacentHex(status.currentHex, x);
      if(a != null && a === status.previousHex && status.currentHex.getHexsideLocation(x).getTerrain().isRoad()) return true;
    }
    return false;
  }
  exitsByRoadHexside(status){
    if(status.LOSis60Degree || status.LOSisHorizontal) return false;
    const c = status.getHexsideCrossed(status.currentHex);
    return c.length > 0 && status.currentHex.getHexsideLocation(c[0]).getTerrain().isRoad();
  }

  losFollowsDepression(status, sourceadj, targetadj, newequalsprevioushex){
    let rangeadj = 0;
    const plat = status.LOSisHorizontal || status.LOSis60Degree;
    if(status.exitsSourceDepression && !newequalsprevioushex && !plat){
      const l = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
      if(status.targetElevation + targetadj > status.sourceElevation + sourceadj){
        for(const h of status.getHexsideCrossed(status.currentHex)){
          const hl = status.currentHex.getHexsideLocation(h);
          if(hl === l && (hl.isDepressionTerrain() | hl.getTerrain().isWaterTerrain())){
            rangeadj = status.rangeToSource - 1;
            return (status.targetElevation + targetadj) - (status.sourceElevation + sourceadj) >= status.rangeToSource - rangeadj;
          }
        }
      }
    }
    if(status.entersTargetDepression && !newequalsprevioushex && !plat){
      const l = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
      if(status.sourceElevation + sourceadj > status.targetElevation + targetadj && !_eqJ(status.currentHex, status.targetHex)){
        for(const h of status.getHexsideCrossed(status.currentHex)){
          const hl = status.currentHex.getHexsideLocation(h);
          if(hl !== l && (hl.isDepressionTerrain() | hl.getTerrain().isWaterTerrain())){
            rangeadj = status.rangeToSource - 1;
            return (status.sourceElevation + sourceadj) - (status.targetElevation + targetadj) >= status.rangeToSource - rangeadj;
          }
        }
      }
    }
    return false;
  }

  checkHexsideTerrainRule(status, result){
    if(status.startsOnHillock || status.endsOnHillock) return false;
    let sourceadj = 0, targetadj = 0;
    if(status.source.getTerrain().isCellar()) sourceadj = 1;
    if(status.target.getTerrain().isCellar()) targetadj = 1;
    if(status.source.getTerrain().getName().includes('Railroad, Embankment')) sourceadj = 0.5;
    if(status.target.getTerrain().getName().includes('Railroad, Embankment')) targetadj = 0.5;
    const bloque = r => {
      status.reason = r; status.blocked = true;
      result.setBlocked(status.currentCol, status.currentRow, r);
      return true;
    };
    if(status.currentTerrain.isRowhouseFactoryWallOrBreach()){
      if(this.checkRowhouseFactoryWallAndBreach(status, result)) return bloque('Cannot see through rowhouse/factory wall (B23.71/O5.31)');
      return false;
    }
    const top = status.groundLevel + status.currentTerrainHgt;
    if(status.source.getTerrain().isEntrenchmentTerrain() && status.currentTerrain.getName() !== 'Dune, Crest Low'){
      if(status.range > 1 && status.targetElevation <= status.sourceElevation)
        return bloque('Unit in entrenchment cannot see over hexside terrain to non-adjacent lower target (B27.2)');
    } else if(status.target.getTerrain().isEntrenchmentTerrain() && status.currentTerrain.getName() !== 'Dune, Crest Low'){
      if(status.range > 1 && status.targetElevation >= status.sourceElevation)
        return bloque('Cannot see non-adjacent unit in higher elevation entrenchment over hexside terrain (B27.2)');
    } else if(status.source.getTerrain().isCellar()){
      if(status.range !== 1 && status.rangeToSource !== 1 && status.targetElevation + targetadj <= status.sourceElevation + sourceadj)
        return bloque('Unit in cellar cannot see over hexside terrain to non-adjacent target (O6.3)');
    } else if(status.target.getTerrain().isCellar()){
      if(status.range !== 1 && status.rangeToTarget !== 1 && status.targetElevation + targetadj >= status.sourceElevation + sourceadj)
        return bloque('Unit in cellar cannot be seen over hexside terrain by non-adjacent target (O6.3)');
    } else {
      const near = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
      const ignore = this.isIgnorableHexsideTerrain(status.sourceHex, near, status.result.getSourceExitHexspine(), status)
                  || this.isIgnorableHexsideTerrain(status.targetHex, near, status.result.getTargetEnterHexspine(), status);
      if(!ignore){
        if(status.currentTerrain.getName() === _BOCAGE){
          if((top > status.sourceElevation && top > status.targetElevation)
             || (top === status.sourceElevation && top === status.targetElevation && status.currentTerrain.isHalfLevelHeight())
             || (top === Math.max(status.sourceElevation, status.targetElevation)
                 && top > Math.min(status.sourceElevation, status.targetElevation)) && !status.slopes)
            return bloque('Cannot see through/over bocage (B9.52)');
          else if(LosMap.isBlindHex(status, status.currentTerrainHgt))
            return bloque('Source or Target location is in a blind hex (B9.52)');
        } else if(status.currentTerrain.getName() === _PARTIALORCHARD){
          if(top < Math.max(status.sourceElevation + sourceadj, status.targetElevation + targetadj)
             && top <= Math.min(status.sourceElevation + sourceadj, status.targetElevation + targetadj))
            return false;
          else if(status.groundLevel === status.sourceElevation && status.groundLevel === status.targetElevation){
            if(status.range > 1 && this.addHindranceHex(status, result)) return true;
          } else if(LosMap.isBlindHex(status, status.currentTerrainHgt))
            return bloque('Source or Target location is in a blind hex (B14.2)');
        } else if(status.groundLevel === status.sourceElevation + sourceadj && status.groundLevel === status.targetElevation + targetadj && !status.slopes)
          return bloque('Intervening hexside terrain (B9.2)');
      }
    }
    return false;
  }

  checkBlindHexRule(status, result){
    let sourceadj = 0, targetadj = 0, rangehex = null;
    if(status.source.getTerrain().isCellar()) sourceadj = 1;
    if(status.target.getTerrain().isCellar()) targetadj = 1;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1){ sourceadj = -0.5; rangehex = status.sourceHex; }
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1){ targetadj = -0.5; rangehex = status.targetHex; }
    const bloque = r => {
      status.reason = r; status.blocked = true;
      result.setBlocked(status.currentCol, status.currentRow, r);
      return true;
    };
    const sE = status.sourceElevation + sourceadj, tE = status.targetElevation + targetadj;
    const top = status.groundLevel + status.currentTerrainHgt;
    if(top === Math.max(sE, tE) && top > Math.min(sE, tE)){
      if((status.startsOnHillock || status.slopes) && LosMap.isBlindHex(status, status.currentTerrainHgt))
        return bloque('Source or Target location is in a blind hex from an up-slope location (F2.3)');
    }
    if(status.currentTerrain.getName() === _BOCAGE) return false;
    if(!(status.currentTerrain.isCliff() || (top > Math.min(sE, tE) && top < Math.max(sE, tE)))) return false;

    let cliffHexsideTerrainHeightadjustment = -99, cliffalonghexsideinthishex = false;
    if((status.LOSis60Degree || status.LOSisHorizontal) && status.rangeToSource % 2 !== 0){
      const x = this.getHexsideWhenLOSAlongHexside(status);
      if(x !== -1 && status.currentHex.getHexsideLocation(x).getTerrain().isCliff()){
        cliffalonghexsideinthishex = true;
        const temp = this.getAdjacentHex(status.currentHex, x);
        if(!_eqJ(temp, status.sourceHex) && !_eqJ(temp, status.targetHex))
          cliffHexsideTerrainHeightadjustment = Math.min(status.currentHex.getBaseLevelofHex(), temp.getBaseLevelofHex());
      }
    }
    let isCliffHexside = false;
    if(status.currentTerrain.isCliff()){
      isCliffHexside = true;
      for(let x = 0; x < 2; x++){
        if(_eqJ(this.getAdjacentHex(status.sourceHex, status.sourceExitHexsides[x]), status.currentHex)){
          const o = status.currentHex.getHexsideLocation(LosHex.getOppositeHexside(status.sourceExitHexsides[x]));
          const c = status.currentHex.getHexCenter();
          if(o.getTerrain().isCliff()
             && _dist(o.getEdgeCenterPoint().x, o.getEdgeCenterPoint().y, status.currentCol, status.currentRow)
                < _dist(c.x, c.y, status.currentCol, status.currentRow)){
            isCliffHexside = false;
            break;
          }
        }
      }
    }
    if(!LosMap.isBlindHex(status, status.currentTerrainHgt, isCliffHexside, cliffHexsideTerrainHeightadjustment)) return false;

    const roofless = h => h.getCenterLocation().getTerrain().isRoofless();
    const voisinAToit = () => {
      const [a, b] = this._cotesTest(result);
      const touche = status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentCol, status.currentRow));
      if(touche === a || touche === b){
        const t = this.getAdjacentHex(status.currentHex, touche);
        if(!roofless(t)) return bloque('Source or Target location is in a blind hex (A6.4)');
        return null;
      }
      return false;
    };
    const ct = status.currentTerrain;
    if((ct.isLOSObstacle() && !ct.isHexsideTerrain()) || ct.isOutsideFactoryWall()){
      if(!roofless(status.currentHex) || ct.isOutsideFactoryWall()){
        if(!ct.isInherentTerrain() || status.currentHex.getCenterLocation().getTerrain() === ct || status.currentTerrainfromCounter){
          if((status.LOSis60Degree || status.LOSisHorizontal) && ct.isBuilding()){
            if((status.rangeToTarget % 2 === 0 || (status.rangeToTarget % 2 !== 0 && !status.target.isCenterLocation())) || ct.isOutsideFactoryWall())
              return bloque('Source or Target location is in a blind hex (A6.4)');
            if(status.currentHex != null){
              status.reason = ''; status.blocked = false; result.resetreportingonly();
              const r = voisinAToit();
              if(r !== null) return r;
            }
          } else return bloque('Source or Target location is in a blind hex (A6.4)');
        }
      }
    } else if(ct.getLOSCategory() === 'FACTORY' && (status.LOSLeavesBuilding || status.targetElevation > status.currentTerrainHgt + ct.getHeight())
              && !roofless(status.currentHex) && !ct.getName().includes('Interior Factory Wall')){
      if(status.LOSis60Degree || status.LOSisHorizontal){
        if(status.rangeToTarget % 2 === 0) return bloque('Source or Target location is in a blind hex (B10.23)');
        if(rangehex != null){
          status.reason = ''; status.blocked = false; result.resetreportingonly();
          const r = voisinAToit();
          if(r !== null) return r;
        }
      } else {
        if(status.rangeToTarget === 1 && roofless(status.targetHex)) return false;
        return bloque('Source or Target location is in a blind hex (A6.4)');
      }
    } else if((isCliffHexside && !cliffalonghexsideinthishex)
              || (status.groundLevel > Math.min(sE, tE) && status.groundLevel < Math.max(sE, tE) && LosMap.isBlindHex(status, 0, false, 0))){
      return bloque('Source or Target location is in a blind hex (B10.23)');
    } else if(!_eqJ(status.currentHex, status.targetHex) && !_eqJ(status.currentHex, status.sourceHex)){
      if(ct.getName() === 'Orchard, Out of Season'){
        if(status.rangeToTarget === 1 && this.addHindranceHex(status, result)) return true;
      } else if(isCliffHexside || ct.getLOSCategory() === 'FACTORY' || ct.getLOSCategory() === 'OPEN') return false;
      else if(this.addHindranceHex(status, result)) return true;
    }
    return false;
  }

  checkTerrainHeightRule(status, result){
    let sourceadj = 0, targetadj = 0, obstacleadj = 0, rooftopadj = 0, rangehex = null;
    const ct = status.currentTerrain;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1){ sourceadj = -0.5; rooftopadj = 1; rangehex = status.sourceHex; }
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1){ targetadj = -0.5; rooftopadj = 1; rangehex = status.targetHex; }
    if(status.source.getTerrain().getName().includes('Railroad, Embankment')) sourceadj = 0.5;
    if(status.target.getTerrain().getName().includes('Railroad, Embankment')) targetadj = 0.5;
    if(ct.isHalfLevelHeight() && ct.isBuilding()) obstacleadj = 0.5;
    if(ct.isHalfLevelHeight() && ct.getName().includes('Railroad, Embankment')) obstacleadj = 0.5;
    if(status.source.getTerrain().isCellar()) sourceadj = 1;
    if(status.target.getTerrain().isCellar()) targetadj = 1;
    if(ct.getName() === 'Orchard, Out of Season') obstacleadj = -1.0;
    const sE = status.sourceElevation + sourceadj, tE = status.targetElevation + targetadj;
    const top = status.groundLevel + status.currentTerrainHgt + obstacleadj;
    const ch = status.currentHex, cc = ch.getHexCenter();

    if(top === Math.max(sE, tE) && top > Math.min(sE, tE)){
      const s = status.sourceHex.getHexCenter(), t = status.targetHex.getHexCenter();
      if(status.rangeToSource === 1 && (status.currentTerrainHgt + obstacleadj === 0 && status.sourceElevation > ch.getBaseLevelofHex())
         && _dist(s.x, s.y, status.currentCol, status.currentRow) < _dist(s.x, s.y, cc.x, cc.y)) return false;
      if(status.rangeToTarget === 1 && (status.currentTerrainHgt + obstacleadj === 0 && status.targetElevation > ch.getBaseLevelofHex())
         && _dist(t.x, t.y, status.currentCol, status.currentRow) < _dist(t.x, t.y, cc.x, cc.y)) return false;
      if(status.rangeToSource === 0 || status.rangeToTarget === 0){
        if(ct.isLowerLOSHindrance() || ct.isLOSHindrance()) return false;
      }
      if(status.slopes || status.startsOnHillock || status.endsOnHillock) return false;
      if(ct.getName() === _BOCAGE) return false;
      if(ct.isRowhouseFactoryWallOrBreach()) return false;
      if(!status.LOSLeavesBuilding && (status.target.getTerrain().isFactoryTerrain() | status.source.getTerrain().isFactoryTerrain())
         && !ct.isOutsideFactoryWall()){
        if(!_eqJ(status.sourceHex, status.targetHex)){
          if(status.LOSis60Degree || status.LOSisHorizontal){
            if(status.rangeToSource % 2 === 0)
              return this.isRooftopLOSBlocked(ch, ct, status.currentTerrainHgt, status, result, rooftopadj);
            if(!this.isRooftopLOSBlocked(ch, ct, status.currentTerrainHgt, status, result, rooftopadj)) return false;
            if(rangehex != null){
              status.reason = ''; status.blocked = false; result.resetreportingonly();
              return this._toitDuVoisin(status, result, rooftopadj);
            }
            return true;
          }
          return this.isRooftopLOSBlocked(ch, ct, status.currentTerrainHgt, status, result, rooftopadj);
        }
      }
      if((status.LOSis60Degree || status.LOSisHorizontal) && status.rangeToSource % 2 !== 0){
        const x = this.getHexsideWhenLOSAlongHexside(status);
        if(x !== -1 && ch.getHexsideLocation(x).getTerrain().isCliff()){
          const temp = this.getAdjacentHex(ch, x);
          const lower = (!_eqJ(temp, status.sourceHex) && !_eqJ(temp, status.targetHex))
            ? Math.min(ch.getBaseLevelofHex(), temp.getBaseLevelofHex()) : ch.getBaseLevelofHex();
          if(lower < Math.max(status.sourceElevation, status.targetElevation)) return false;
        }
      }
      if(!(status.ignoreGroundLevelHex != null && status.ignoreGroundLevelHex.containsExtended(status.currentCol, status.currentRow))
         && !(status.entersTargetDepression && ch.isDepressionTerrain())
         && (!(status.exitsSourceDepression && ct.isDepression()) || (ch.isDepressionTerrain() && status.currentTerrainHgt >= 1))){
        const eau = ch.getCenterLocation().getTerrain().isWaterTerrain() && ct.getHeight() < 1
          && ((status.rangeToSource === 1 && status.sourceElevation > status.targetElevation
               && status.target.getHex().getCenterLocation().getTerrain().isWaterTerrain())
              || (status.rangeToTarget === 1 && status.targetElevation > status.sourceElevation
               && status.source.getHex().getCenterLocation().getTerrain().isWaterTerrain()));
        if(!eau){
          if(ct.getName() === 'Huts' || ct.getName() === 'Tower Hindrance'){
            if(this.addHindranceHex(status, result)) return true;
          } else {
            status.reason = 'Must have a height advantage to see over this terrain (A6.2)';
            status.blocked = true;
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
          }
        }
      }
    } else {
      const t2 = status.groundLevel + status.currentTerrainHgt;
      if(ct.getName() === 'Orchard, Out of Season' && t2 === Math.max(sE, tE) && t2 > Math.min(sE, tE)){
        if(this.addHindranceHex(status, result)) return true;
      }
    }
    return false;
  }

  checkTerrainIsHigherRule(status, result){
    let sourceadj = 0, targetadj = 0, obstacleadj = 0;
    const ct = status.currentTerrain;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1) sourceadj = -0.5;
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1) targetadj = -0.5;
    if(status.source.getTerrain().getName().includes('Railroad, Embankment')) sourceadj = 0.5;
    if(status.target.getTerrain().getName().includes('Railroad, Embankment')) targetadj = 0.5;
    if(status.startsOnHillock) sourceadj = 0.5;
    if(status.endsOnHillock) targetadj = 0.5;
    if(ct.isHalfLevelHeight() && !ct.isHexsideTerrain()) obstacleadj = 0.5;
    if(status.source.getTerrain().isCellar()) sourceadj = 1;
    if(status.target.getTerrain().isCellar()) targetadj = 1;
    if(ct.hasSplit() && status.groundLevel === status.sourceElevation + sourceadj && status.groundLevel === status.targetElevation + targetadj)
      return false;
    if(ct.getName() === _BOCAGE || ct.getName() === _PARTIALORCHARD || ct.getName() === _HILLOCK) return false;
    const top = status.groundLevel + status.currentTerrainHgt + obstacleadj;
    if(!(top > status.sourceElevation + sourceadj && top > status.targetElevation + targetadj)) return false;
    const leger = ['Orchard', 'Palm Trees', 'Light Woods'].includes(ct.getName());
    if((_eqJ(status.targetHex, status.currentHex) || _eqJ(status.sourceHex, status.currentHex)) && leger) return false;
    else if((status.LOSis60Degree || status.LOSisHorizontal) && status.rangeToSource % 2 !== 0){
      const x = this.getHexsideWhenLOSAlongHexside(status);
      if(x !== -1){
        const temp = this.getAdjacentHex(status.currentHex, x);
        if((_eqJ(status.targetHex, temp) || _eqJ(status.sourceHex, temp)) && leger) return false;
      }
    }
    if(this.sanddunetest(status)) return false;
    const bloque = r => {
      status.reason = r; status.blocked = true;
      result.setBlocked(status.currentCol, status.currentRow, r);
      return true;
    };
    if(ct.isLOSObstacle() && !ct.getName().includes('Light Woods'))
      return bloque('Terrain is higher than both the source and target (A6.2)');
    if(!status.LOSLeavesBuilding && ct.getLOSCategory() === 'FACTORY') return false;
    else if(status.LOSLeavesBuilding && ct.getLOSCategory() === 'FACTORY')
      return bloque('Terrain is higher than both the source and target (A6.2)');
    else if((ct.isBridge() || ct.getName().includes('Road')) && status.groundLevel > status.sourceElevation && status.groundLevel > status.targetElevation)
      return false;
    else if(!status.slopes){
      if(ct.getName().includes('Volga Pier')) return this.addVolgaPierHindrance(status, result);
      else if(this.addHindranceHex(status, result)) return true;
    }
    return false;
  }

  addVolgaPierHindrance(status, result){
    const sW = status.source.getTerrain().isWaterTerrain(), tW = status.target.getTerrain().isWaterTerrain();
    const sP = status.source.getTerrain().getName().includes('Volga Pier'), tP = status.target.getTerrain().getName().includes('Volga Pier');
    if((sW && tW) || (sP && tW) || (sW && tP)) return this.addHindranceHex(status, result);
    return false;
  }

  inherentspilltest(status){
    const dc = [0, 10, 10, 0, -10, -10], dr = [-10, -10, 10, 10, 10, -10];
    for(let x = 0; x < 6; x++){
      const cx = status.currentCol + dc[x], cy = status.currentRow + dr[x];
      if(status.currentHex.contains(cx, cy) && this.getGridTerrain(cx, cy) === status.currentTerrain) return false;
    }
    return true;
  }

  checkHillockRule(status, result){
    if(!this.hillockRuleApplicable(status)) return false;
    const ct = status.currentTerrain, cfg = this.getMapConfiguration();
    if(status.startsOnHillock && status.endsOnHillock){
      if(ct.getName() === 'Hillock Summit' && (LosMap.range(status.sourceHex, status.currentHex, cfg) !== 1
         && LosMap.range(status.targetHex, status.currentHex, cfg) !== 1))
        return this.blockByHillock(status, result);
      return false;
    } else if(status.startsOnHillock || status.endsOnHillock){
      if(ct.getName() === 'Hillock Summit') return this.blockByHillock(status, result);
      if(status.startsOnHillock && status.crossedHillocks.size - (status.targetAdjacentHillock != null ? 1 : 0)
         + (status.exitsSlopeHexside() ? 1 : 0) > 2)
        return this.blockByHillock(status, result);
      else if(status.endsOnHillock && status.crossedHillocks.size - (status.sourceAdjacentHillock != null ? 1 : 0)
         + (status.entersSlopeHexside() ? 1 : 0) > 1)
        return this.blockByHillock(status, result);
      else if(ct.getName() === _WALL || ct.getName() === _HEDGE){
        if(status.firstWallCrossed == null){
          status.firstWallCrossed = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
          status.firstWallPoint = {x: status.currentCol, y: status.currentRow};
        } else {
          const near = status.currentHex.getNearestLocation(status.currentCol, status.currentRow);
          const lh = near.getHex(), side = lh.getLocationHexside(near);
          const opp = this.getAdjacentHex(lh, side);
          const oppLoc = opp.getHexsideLocation(LosHex.getOppositeHexside(side));
          if(!near.isCenterLocation() && (near === status.firstWallCrossed || oppLoc === status.firstWallCrossed)) return false;
          const s1 = status.startsOnHillock, s2 = status.endsOnHillock;
          status.startsOnHillock = false; status.endsOnHillock = false;
          if(this.checkHexsideTerrainRule(status, new LosResult())
             && _dist(status.firstWallPoint.x, status.firstWallPoint.y, status.currentCol, status.currentRow) > 15){
            status.reason = 'More than one intervening wall/hedge (F6.4)';
            status.blocked = true;
            result.setBlocked(status.currentCol, status.currentRow, status.reason);
            return true;
          }
          status.startsOnHillock = s1; status.endsOnHillock = s2;
          status.blocked = false; status.reason = '';
        }
      } else if(ct.getName() === _STONE_RUBBLE || ct.getName() === _WOODEN_RUBBLE){
        if(status.currentHex !== status.firstRubbleCrossed && LosMap.range(status.currentHex, status.targetHex, cfg) !== 1){
          status.reason = 'More than one intervening rubble hex (F6.4)';
          status.blocked = true;
          result.setBlocked(status.currentCol, status.currentRow, status.reason);
          return true;
        }
      }
    } else if((status.sourceAdjacentHillock != null && status.sourceAdjacentHillock.has(status.currentHex) && !status.source.getTerrain().isEntrenchmentTerrain())
              || (status.targetAdjacentHillock != null && status.targetAdjacentHillock.has(status.currentHex) && !status.target.getTerrain().isEntrenchmentTerrain())){
      return false;
    } else if(ct.getName() === 'Hillock Summit'){
      return this.blockByHillock(status, result);
    } else if(ct.isHalfLevelHeight() && !ct.isHexsideTerrain()){
      return this.applyHalfLevelTerrain(status, result);
    }
    return false;
  }

  blockByHillock(status, result){
    status.reason = 'Intervening hillock (F6.4)';
    status.blocked = true;
    result.setBlocked(status.currentCol, status.currentRow, status.reason);
    return true;
  }

  hillockRuleApplicable(status){
    const top = status.groundLevel + status.currentTerrainHgt;
    return top === status.sourceElevation && top === status.targetElevation
      && (status.crossingHillock != null || status.startsOnHillock || status.endsOnHillock
          || status.sourceAdjacentHillock != null || status.targetAdjacentHillock != null);
  }

  // Une butte (hillock) : l'ensemble de ses hexagones (Set).
  getHillock(hex){
    for(const h of this.hillocks) if(h.has(hex)) return h;
    return null;
  }

  buildHillocks(){
    let restants = [];
    for(const c of this.hexGrid) for(const h of c)
      if(h.getCenterLocation().getTerrain().getName() === _HILLOCK) restants.push(new Set([h]));
    const cfg = this.getMapConfiguration();
    const voisins = (a, b) => {
      for(const o of b) for(const i of a) if(LosMap.range(i, o, cfg) === 1) return true;
      return false;
    };
    const faits = [];
    while(restants.length){
      let consolidation = true;
      while(consolidation && restants.length > 1){
        const h = restants[0];
        consolidation = false;
        for(let i = 1; i < restants.length; ){
          if(voisins(h, restants[i])){
            for(const x of restants[i]) h.add(x);
            restants.splice(i, 1);
            consolidation = true;
          } else i++;
        }
      }
      faits.push(restants.shift());
    }
    this.hillocks = faits;
  }

  checkHalfLevelTerrainRule(status, result){
    const ct = status.currentTerrain;
    if((this.hillockRuleApplicable(status) && !status.slopes) || this.hillockHindranceToLowerElevation(status)){
      if(status.firstHalfLevelHindrance == null
         && (ct.getName() === _BRUSH || ct.getName() === _GRAIN || ct.getName() === 'Rice Paddy, In Season')
         && !(status.startsOnHillock && status.endsOnHillock)){
        status.firstHalfLevelHindrance = status.currentHex;
        if(this.addHindranceHex(status, result)) return true;
      }
      return false;
    }
    let sourceadj = 0, targetadj = 0;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1 && !status.target.getTerrain().isRooftop()) sourceadj = -0.5;
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1 && !status.source.getTerrain().isRooftop()) targetadj = -0.5;
    if(status.source.getTerrain().getName().includes('Railroad, Embankment')) sourceadj = 0.5;
    if(status.target.getTerrain().getName().includes('Railroad, Embankment')) targetadj = 0.5;
    if(this.sanddunetest(status)) return false;
    const top = status.groundLevel + status.currentTerrainHgt;
    return ct.isHalfLevelHeight() && !ct.isHexsideTerrain()
      && top === status.sourceElevation + sourceadj && top === status.targetElevation + targetadj
      && !status.slopes && this.applyHalfLevelTerrain(status, result);
  }

  sanddunetest(status){
    const dune = l => l.getTerrain().getName().includes('Sand Dune, Low')
      || (l.getUpLocation() != null && l.getUpLocation().getTerrain().getName().includes('Sand Dune, Low'));
    return dune(status.source) && dune(status.target) && status.currentTerrain.getName().includes('Sand Dune');
  }

  hillockHindranceToLowerElevation(status){
    const top = status.groundLevel + status.currentTerrainHgt;
    return (status.startsOnHillock && top === status.sourceElevation && top > status.targetElevation)
        || (status.endsOnHillock && top === status.targetElevation && top > status.sourceElevation);
  }

  applyHalfLevelTerrain(status, result){
    if(status.currentTerrain.isLOSObstacle()){
      status.reason = 'Half level terrain is higher than both the source and/or the target (A6.2)';
      status.blocked = true;
      result.setBlocked(status.currentCol, status.currentRow, status.reason);
      return true;
    }
    if(status.currentTerrain.getName().includes('Volga Pier')) return this.addVolgaPierHindrance(status, result);
    return this.addHindranceHex(status, result);
  }

  checkSplitTerrainRule(status, result){
    let sourceadj = 0, targetadj = 0;
    if(status.source.getTerrain().isCellar()) sourceadj = 1;
    if(status.target.getTerrain().isCellar()) targetadj = 1;
    const ct = status.currentTerrain;
    if(ct.hasSplit() && status.groundLevel === status.sourceElevation + sourceadj && status.groundLevel === status.targetElevation + targetadj){
      if(status.slopes){
        if(ct.isLOSObstacle()){
          status.reason = 'This terrain blocks LOS to up-slope location';
          status.blocked = true;
          result.setBlocked(status.currentCol, status.currentRow, status.reason);
          return true;
        }
        if(this.addHindranceHex(status, result)) return true;
      } else if(ct.isLowerLOSObstacle() | (!status.LOSLeavesBuilding && ct.isOutsideFactoryWall())){
        status.reason = 'This terrain blocks LOS to same same elevation Source and Target';
        status.blocked = true;
        result.setBlocked(status.currentCol, status.currentRow, status.reason);
        return true;
      } else if(ct.isLowerLOSHindrance()){
        if(this.addHindranceHex(status, result)) return true;
      }
    }
    return false;
  }

  checkGroundLevelRule(status, result){
    let sourceadj = 0, targetadj = 0;
    if(status.source.getTerrain().isCellar()) sourceadj = 1;
    if(status.target.getTerrain().isCellar()) targetadj = 1;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1) sourceadj = -0.5;
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1) targetadj = -0.5;
    if(status.source.getTerrain().getName().includes('Railroad, Embankment')) sourceadj = 0.5;
    if(status.target.getTerrain().getName().includes('Railroad, Embankment')) targetadj = 0.5;
    if(status.currentTerrain.isBridge()) status.ignoreGroundLevelHex = status.currentHex;
    if((status.LOSis60Degree || status.LOSisHorizontal) && status.rangeToSource % 2 !== 0){
      const x = this.getHexsideWhenLOSAlongHexside(status);
      if(x !== -1 && status.currentHex.getHexsideLocation(x).getTerrain().isCliff()){
        const temp = this.getAdjacentHex(status.currentHex, x);
        const lower = (!_eqJ(temp, status.sourceHex) && !_eqJ(temp, status.targetHex))
          ? Math.min(status.currentHex.getBaseLevelofHex(), temp.getBaseLevelofHex()) : status.currentHex.getBaseLevelofHex();
        if(lower <= Math.max(status.sourceElevation, status.targetElevation)) return false;
      }
    }
    const deir = (loc, hex) => {
      if(loc.getTerrain().isEntrenchmentTerrain() && hex.getCenterLocation().getTerrain().getName() === 'Deir'
         && status.sourceElevation === status.targetElevation){
        for(let x = 0; x < 6; x++) if(hex.getHexsideLocation(x).getTerrain().getName() === 'Deir Lip') return false;
        status.blocked = true;
        status.reason = 'Unit in entrenchment cannot see/be seen over Deir Lip (F4.4)';
        result.setBlocked(status.currentCol, status.currentRow, status.reason);
        return true;
      }
      return null;
    };
    const d1 = deir(status.source, status.sourceHex);
    if(d1) return true;
    if(d1 === null && deir(status.target, status.targetHex)) return true;
    if(status.groundLevel > status.sourceElevation + sourceadj && status.groundLevel > status.targetElevation + targetadj
       && status.ignoreGroundLevelHex !== status.currentHex){
      status.reason = 'Ground level is higher than both the source and target (A6.2)';
      status.blocked = true;
      result.setBlocked(status.currentCol, status.currentRow, status.reason);
      return true;
    }
    return false;
  }

  checkBridgeHindranceRule(status, result){
    if(!status.currentHex.hasBridge()) return false;
    if(status.sourceElevation === status.targetElevation && status.sourceElevation === status.bridge.getRoadLevel()){
      for(let x = 0; x < 6; x++){
        const t = status.currentHex.getHexsideLocation(x).getTerrain();
        if(t != null && !t.isDepression() && t.getName().includes('Railroad, Embankment')) return false;
      }
      if(_rectContient(status.bridgeArea, status.currentCol, status.currentRow)
         && !_rectContient(status.bridgeRoadArea, status.currentCol, status.currentRow) && !status.currentTerrain.isRoad()){
        if(this.addHindranceHex(status, result)) return true;
      }
    }
    return false;
  }

  isAdjacentHexside(h, l){
    if(l.isCenterLocation()) return false;
    if(_eqJ(l.getHex(), h)) return true;
    for(let x = 0; x < 6; x++){
      const h2 = this.getAdjacentHex(h, x);
      if(h2 != null && h2.getHexsideLocation(LosHex.getOppositeHexside(x)) === l) return true;
    }
    return false;
  }

  isHexspine(h, l){
    const paires = [[2, 4], [3, 5], [4, 0], [5, 1], [0, 2], [1, 3]];
    for(let x = 0; x < 6; x++){
      const h2 = this.getAdjacentHex(h, x);
      if(h2 != null && (h2.getHexsideLocation(paires[x][0]) === l || h2.getHexsideLocation(paires[x][1]) === l)) return true;
    }
    return false;
  }

  isIgnorableHexsideTerrain(h, l, LOSHexspine, status){
    let locationHex = l.getHex();
    let locationHexside = locationHex.getLocationHexside(l);
    if(locationHexside === -1) return true;
    const lht = locationHex.getHexsideTerrain(locationHexside);
    if(lht == null){
      if(!status.currentTerrain.isHexsideTerrain()) return true;
    } else if(status.currentTerrain.isHexsideTerrain() && lht !== status.currentTerrain) return true;
    // Terrain de côté porté par un pion : il prime sur celui de la carte.
    const gi = status.vaslGameInterface;
    if(gi != null && gi.getHexside(status.currentHex) != null){
      const counter = gi.getHexside(status.currentHex), ca = counter.getCoverArch();
      if(counter.getTerrain() === status.currentTerrain.getName()) return this.isAdjacentHexside(h, l);
      else if(locationHexside === ca) return this.isAdjacentHexside(h, l);
      else if(status.LOSis60Degree || status.LOSisHorizontal){
        if(status.sourceExitHexsides[0] === ca || status.sourceExitHexsides[1] === ca
           || status.targetEnterHexsides[0] === ca || status.targetEnterHexsides[1] === ca){
          const a = this.getAdjacentHex(status.currentHex, ca);
          return _eqJ(a, status.sourceHex) || _eqJ(a, status.targetHex)
            || _eqJ(status.currentHex, status.sourceHex) || _eqJ(status.currentHex, status.targetHex);
        }
      }
    }
    const cfg = this.getMapConfiguration();
    if(LosMap.range(h, locationHex, cfg) > 2) return false;
    if(_eqJ(status.currentHex, status.targetHex) || _eqJ(status.currentHex, status.sourceHex)) return true;
    if(this.isAdjacentHexside(h, l) && (lht != null && lht.getName() !== _PARTIALORCHARD)) return true;
    if(this.isHexspine(h, l) && lht != null)
      return !(lht.getName() === _BOCAGE || lht.getName() === _PARTIALORCHARD);
    if(LosMap.range(h, locationHex, cfg) === 1 && !l.getTerrain().isHexsideTerrain()) return true;
    if(LOSHexspine >= 0){
      if(LosMap.range(h, locationHex, cfg) === 2){
        const oppositeHex = this.getAdjacentHex(locationHex, locationHexside);
        let oppositeHexside = locationHex.getOppositeHexside(locationHexside);
        if(oppositeHex == null) return true;
        if(LosMap.range(h, oppositeHex, cfg) > 1){
          const otherEndHex = _eqJ(h, status.sourceHex) ? status.targetHex : status.sourceHex;
          if(oppositeHex.getHexsideTerrain(oppositeHexside) != null && LosMap.range(locationHex, otherEndHex, cfg) === 2){
            const thirdHex = this.getAdjacentHex(locationHex, LOSHexspine);
            oppositeHexside = locationHex.getOppositeHexside(LOSHexspine);
            if(thirdHex == null) return true;
            return !(thirdHex.getHexsideTerrain(oppositeHexside) != null && LosMap.range(locationHex, otherEndHex, cfg) === 2);
          }
          return false;
        }
        locationHex = oppositeHex;
        locationHexside = LosHex.getOppositeHexside(locationHexside);
        l = locationHex.getHexsideLocation(locationHexside);
      }
      const hexside = LOSHexspine === 0 ? 5 : LOSHexspine - 1;
      const hexspine = LOSHexspine < 2 ? LOSHexspine + 4 : LOSHexspine - 2;
      const hex1 = this.getAdjacentHex(h, hexside), hex2 = this.getAdjacentHex(h, LOSHexspine);
      if(hex1 == null || hex2 == null) return false;
      const l2 = hex1.getHexsideLocation(LOSHexspine), l3 = hex2.getHexsideLocation(hexside);
      const t1 = hex2.getHexsideTerrain(hexspine), t2 = hex1.getHexsideTerrain(LOSHexspine), t3 = hex2.getHexsideTerrain(hexside);
      return t1 != null && (l === l2 || l === l3) && (t2 == null || t3 == null);
    }
    return false;
  }

  addHindranceHex(status, result){
    if(_eqJ(status.currentHex, status.sourceHex) || _eqJ(status.currentHex, status.targetHex)) return false;
    const cfg = this.getMapConfiguration();
    const r = LosMap.range(status.sourceHex, status.targetHex, cfg);
    if(!(LosMap.range(status.sourceHex, status.currentHex, cfg) < r && LosMap.range(status.targetHex, status.currentHex, cfg) < r)) return false;
    const ct = status.currentTerrain, n = ct.getName();
    const demi = n.includes('Rice Paddy, In Season') || n.includes('Rice Paddy Bank') || n.includes('Light Grain');
    let v = 1;
    if((status.LOSis60Degree || status.LOSisHorizontal) && status.rangeToSource % 2 !== 0){
      if(ct.isRoofless()) v = 2;
      const [a, b] = this._cotesTest(result);
      const touche = status.currentHex.getLocationHexside(status.currentHex.getNearestLocation(status.currentCol, status.currentRow));
      if(touche === a || touche === b){
        const t = this.getAdjacentHex(status.currentHex, touche);
        if(t != null){
          if(t.getCenterLocation().getTerrain().isRoofless() || n.includes('Light Woods')) v = 2;
          else if(demi) v = 0.5;
        }
      } else return false;
    } else if(n.includes('Light Woods')) v = 2;
    else if(demi) v = 0.5;
    else if(ct.isRoofless()) v = 2;
    result.addMapHindrance(status.currentHex, v, status.currentCol, status.currentRow);
    return result.isBlocked();
  }

  static isBlindHex(status, terrainHeight, isCliffHexside = false, cliffAdj = -99){
    let sourceadj = 0, targetadj = 0, demiHauteur = false;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1){ sourceadj = -0.5; demiHauteur = true; }
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1){ targetadj = -0.5; demiHauteur = true; }
    let terrainHeightadj = 0;
    if(demiHauteur && status.currentTerrain.isBuilding() && status.currentTerrain.isHalfLevelHeight()) terrainHeightadj = 0.5;
    let sourceElevation = status.sourceElevation, targetElevation = status.targetElevation;
    let rangeToSource = status.rangeToSource, rangeToTarget = status.rangeToTarget;
    let groundLevel = status.groundLevel;
    let starthex = status.sourceHex, finishhex = status.targetHex;
    if(sourceElevation === targetElevation) return false;
    sourceElevation += sourceadj; targetElevation += targetadj;
    let swapLOS = false;
    if(sourceElevation < targetElevation){
      swapLOS = true;
      [sourceElevation, targetElevation] = [targetElevation, sourceElevation];
      starthex = status.targetHex; finishhex = status.sourceHex;
      [rangeToSource, rangeToTarget] = [rangeToTarget, rangeToSource];
    }
    if(sourceElevation - (groundLevel + terrainHeight) > 1) sourceElevation = Math.floor(sourceElevation);
    if(status.slopes || status.startsOnHillock){
      if(status.groundLevel + status.currentTerrainHgt === Math.max(status.sourceElevation, status.targetElevation)) sourceElevation++;
    }
    const ch = status.currentHex;
    if(isCliffHexside && !status.LOSis60Degree && !status.LOSisHorizontal){
      const tl = ch.getNearestLocation(status.currentCol, status.currentRow);
      if(ch.isDepressionTerrain() && !_eqJ(ch, status.targetHex)) return false;
      const cc = ch.getHexCenter(), s = starthex.getHexCenter(), f = finishhex.getHexCenter();
      if(rangeToSource === 1 && sourceElevation > terrainHeight && tl.getTerrain().isCliff()
         && _dist(s.x, s.y, status.currentCol, status.currentRow) < _dist(s.x, s.y, cc.x, cc.y)) return false;
      if(rangeToTarget === 1 && sourceElevation > terrainHeight && tl.getTerrain().isCliff()
         && _dist(f.x, f.y, status.currentCol, status.currentRow) > _dist(f.x, f.y, cc.x, cc.y)) return false;
      const b = ch.getBaseLevelofHex();
      if((sourceElevation >= b && targetElevation > b) || (sourceElevation > b && targetElevation >= b)) return false;
      if(b > status.groundLevel) groundLevel = b;
    } else if(isCliffHexside && (status.LOSis60Degree || status.LOSisHorizontal)){
      if(cliffAdj !== -99) groundLevel = cliffAdj;
    }
    if(status.currentTerrain.isRowhouseFactoryWallOrBreach()){
      rangeToTarget += 1;
      rangeToSource -= 1;
    }
    if((terrainHeight === 0 && !isCliffHexside) || (isCliffHexside && _eqJ(ch, status.sourceHex))){
      let depressionadj = 0;
      const c = status.getHexsideCrossed(ch);
      if(c.length && ch.getHexsideLocation(c[0]).isDepressionTerrain()) depressionadj = -1;
      return rangeToTarget <= Math.max(2 * (groundLevel + depressionadj + terrainHeight + terrainHeightadj)
        + (rangeToSource / 5) - sourceElevation - targetElevation, 0);
    }
    const cliffadj = isCliffHexside ? -1 : 0;
    const bocageadj = (status.currentTerrain.getName().includes('Bocage') && (sourceElevation - (groundLevel + terrainHeight) >= 2)) ? -1 : 0;
    return LosMap.getBlind(groundLevel, terrainHeight, terrainHeightadj, rangeToSource, rangeToTarget, sourceElevation, targetElevation, cliffadj, bocageadj);
  }

  static getBlind(groundLevel, terrainHeight, terrainHeightadj, rangeToSource, rangeToTarget, sourceElevation, targetElevation, cliffadj, bocageadj){
    return rangeToTarget <= Math.max(2 * (groundLevel + terrainHeight + terrainHeightadj) + (rangeToSource / 5)
      - sourceElevation - targetElevation + 1 + cliffadj, 1 + bocageadj);
  }

  checkRBrrembankments(status, result){
    const ch = status.currentHex;
    const hexside = ch.getLocationHexside(ch.getNearestLocation(status.currentCol, status.currentRow));
    if(hexside === -1 || !ch.getRBrrembankments()[hexside]) return false;
    const t = ch.getHexsideTerrain(hexside);
    if(t == null || !t.isHexsideTerrain() || !t.getName().includes('Rrembankment')) return false;
    const bloque = r => {
      status.blocked = true; status.reason = r;
      result.setBlocked(status.currentCol, status.currentRow, r);
      return true;
    };
    if(status.source.getTerrain().isEntrenchmentTerrain()){
      if(status.range > 1 && status.targetElevation <= status.sourceElevation)
        return bloque('Unit in entrenchment cannot see over hexside terrain to non-adjacent lower target (B27.2)');
    } else if(status.target.getTerrain().isEntrenchmentTerrain()){
      if(status.range > 1 && status.targetElevation >= status.sourceElevation)
        return bloque('Cannot see non-adjacent unit in higher elevation entrenchment over hexside terrain (B27.2)');
    } else if(status.source.getTerrain().isCellar()){
      if(status.range !== 1 && status.rangeToSource === 1 && status.targetElevation <= status.sourceElevation + 1)
        return bloque('Unit in cellar cannot see over hexside terrain to non-adjacent target (O6.3)');
    } else if(status.target.getTerrain().isCellar()){
      if(status.range !== 1 && status.rangeToTarget === 1 && status.targetElevation + 1 >= status.sourceElevation)
        return bloque('Unit in cellar cannot be seen over hexside terrain by non-adjacent target (O6.3)');
    } else if(!_eqJ(ch, status.sourceHex)){
      const near = ch.getNearestLocation(status.currentCol, status.currentRow);
      const ignore = this.isIgnorableHexsideTerrain(status.sourceHex, near, status.result.getSourceExitHexspine(), status)
                  || this.isIgnorableHexsideTerrain(status.targetHex, near, status.result.getTargetEnterHexspine(), status);
      if(!ignore && !_eqJ(ch, status.targetHex)){
        const opp = this.getAdjacentHex(ch, hexside);
        if(!_eqJ(opp, status.sourceHex) && !_eqJ(opp, status.targetHex)){
          if(status.groundLevel === status.sourceElevation && status.groundLevel === status.targetElevation && !status.slopes)
            return bloque('Intervening hexside terrain (B9.2)');
        }
      }
    }
    return false;
  }

  checkPartialOrchards(status, result){
    const ch = status.currentHex;
    const hexside = ch.getLocationHexside(ch.getNearestLocation(status.currentCol, status.currentRow));
    if(hexside === -1 || !ch.getPartialOrchards()[hexside]) return false;
    const t = ch.getHexsideTerrain(hexside);
    if(t != null && t.isHexsideTerrain() && t.getName().includes(_PARTIALORCHARD)){
      if(status.currentTerrain.getName().includes('Water')) status.groundLevel += 1;
      status.currentTerrain = this.getTerrain(_PARTIALORCHARD);
      status.currentTerrainHgt = status.currentTerrain.getHeight();
      const e = status.getExitFromCenterHexsides();
      if(e[0] !== -1) result.setSourceExitHexspine(e[0]);
      if(this.checkHexsideTerrainRule(status, result)) return true;
    }
    return false;
  }

  checkRowhouseFactoryWallAndBreach(status, result){
    let ct = status.currentTerrain;
    const ch = status.currentHex;
    let sourceadj = 0, targetadj = 0;
    if(ct.getName().includes('Gutted Building Wall') && (ch === status.sourceHex || ch === status.targetHex)) return false;
    if(status.source.getTerrain().isRooftop() && status.source.getLevelInHex() !== 1) sourceadj = -1;
    if(status.target.getTerrain().isRooftop() && status.target.getLevelInHex() !== 1) targetadj = -1;
    // Pion de brèche sur ce côté.
    if(ct.getName().includes('Interior Factory Wall') || ct.getName().includes('Rowhouse Wall')){
      const counter = status.vaslGameInterface.getHexside(ch);
      if(counter != null && counter.getName().includes('Breach')
         && counter.getHexside() === ch.getLocationHexside(ch.getNearestLocation(status.currentCol, status.currentRow)))
        ct = status.currentTerrain = this.getTerrain('Breach');
    }
    if(ct.getName().includes('Breach')){
      if(this.checkBreachApplies(status, result, ch.getNearestLocation(status.currentCol, status.currentRow))) return false;
    }
    const sE = status.sourceElevation + sourceadj, tE = status.targetElevation + targetadj;
    const top = status.groundLevel + status.currentTerrainHgt;
    const auDessus = () => (top > sE && top > tE)
      || (top === sE && top === tE && !(status.source.getTerrain().isRooftop() && status.target.getTerrain().isRooftop()) && ct.isHalfLevelHeight())
      || (top === Math.max(sE, tE) && top > Math.min(sE, tE));
    const mod6 = v => v === -1 ? 5 : v === 6 ? 0 : v;
    const estMur = (hex, side) => { const t = hex.getHexsideTerrain(side); return t != null && t.isRowhouseFactoryWallOrBreach(); };
    const adj = (hex, side) => this.getAdjacentHex(hex, side);

    if(ct.getName().includes('Interior Factory Wall') && (result.isLOSis60Degree() | result.isLOSisHorizontal())){
      let first = result.getSourceExitHexspine() + 1;
      if(first >= 6) first -= 6;
      let second = first + 3;
      if(second >= 6) second -= 6;
      if(!_eqJ(ch, status.sourceHex) && !_eqJ(ch, status.targetHex)){
        const touche = ch.getLocationHexside(ch.getNearestLocation(status.currentCol, status.currentRow));
        if((_eqJ(ct, ch.getHexsideTerrain(first)) || _eqJ(ct, ch.getHexsideTerrain(second))) && status.rangeToSource % 2 !== 0){
          let altHex, v1, v2, v3, v4;
          if(_eqJ(ct, ch.getHexsideTerrain(first)) && touche === first){
            altHex = adj(ch, first); v1 = first - 1; v2 = first + 1; v3 = second - 1; v4 = second + 1;
          } else if(_eqJ(ct, ch.getHexsideTerrain(second)) && touche === second){
            altHex = adj(ch, second); v1 = second - 1; v2 = second + 1; v3 = first - 1; v4 = first + 1;
          } else return false;
          [v1, v2, v3, v4] = [v1, v2, v3, v4].map(mod6);
          if((top <= sE || top <= tE)
             && (ch.getCenterLocation().getTerrain().getName().includes('Roofless') || altHex.getCenterLocation().getTerrain().getName().includes('Roofless'))){
            const horsBout = (hex, side) => !(_eqJ(status.sourceHex, adj(hex, side)) || _eqJ(status.targetHex, adj(hex, side)));
            if((estMur(ch, v1) && horsBout(ch, v1)) || (estMur(ch, v2) && horsBout(ch, v2))
               || (estMur(ch, v3) && horsBout(altHex, v3)) || (estMur(ch, v4) && horsBout(altHex, v4)))
              return LosMap.isBlindHex(status, status.currentTerrainHgt);
            return false;
          } else if(top <= sE || top <= tE) return false;
          return true;
        }
        let v1 = mod6(first - 1), v2 = mod6(first + 1), v3 = mod6(second - 1), v4 = mod6(second + 1);
        const t1 = ch.getHexsideTerrain(v1), t2 = ch.getHexsideTerrain(v2), t3 = ch.getHexsideTerrain(v3), t4 = ch.getHexsideTerrain(v4);
        const sTest = ch === adj(status.sourceHex, status.sourceExitHexsides[0]) || ch === adj(status.sourceHex, status.sourceExitHexsides[1]);
        const tTest = ch === adj(status.targetHex, status.targetEnterHexsides[0]) || ch === adj(status.targetHex, status.targetEnterHexsides[1]);
        if(status.source.getTerrain().isRooftop() && (status.rangeToSource === 0 | (status.rangeToSource === 1 && sTest))) return false;
        else if(status.target.getTerrain().isRooftop() && (status.rangeToTarget === 0 | (status.rangeToTarget === 1 && tTest))) return false;
        else if((status.source.getTerrain().isRooftop() || status.target.getTerrain().isRooftop()) && status.rangeToSource % 2 === 0
                && t1 == null && t2 == null && t3 == null && t4 == null) return false;
        else if(auDessus()) return true;
        else if(LosMap.isBlindHex(status, status.currentTerrainHgt)) return true;
      } else {
        if(ch === status.sourceHex && status.source.getTerrain().isRooftop()) return false;
        if(ch === status.targetHex && status.target.getTerrain().isRooftop()) return false;
        const sides = ch === status.sourceHex ? status.sourceExitHexsides : status.targetEnterHexsides;
        for(const s of sides)
          if(s !== -1 && ch.getHexsideTerrain(s) != null && ch.getHexsideTerrain(s) === ct) return true;
        return false;
      }
    } else {
      const sTest = ch === this.getAdjacentHex(status.sourceHex, status.sourceExitHexsides[0]);
      const tTest = ch === this.getAdjacentHex(status.targetHex, status.targetEnterHexsides[0]);
      if(status.source.getTerrain().isRooftop() && (status.rangeToSource === 0 || (status.rangeToSource === 1 && sTest))) return false;
      else if(status.target.getTerrain().isRooftop() && (status.rangeToTarget === 0 || (status.rangeToTarget === 1 && tTest))) return false;
      else if(top < Math.max(sE, tE) && top === Math.min(sE, tE)) return false;
      else if(auDessus()) return true;
      else {
        const testDepuis = (cle) => {
          const side = ch.getLocationHexside(ch.getNearestLocation(status.currentCol, status.currentRow));
          const testhex = this.getAdjacentHex(ch, side);
          const testrange = LosMap.range(status.sourceHex, testhex, this.getMapConfiguration());
          const old = status[cle];
          if(status[cle] > testrange) status[cle] = testrange;
          const b = LosMap.isBlindHex(status, status.currentTerrainHgt);
          status[cle] = old;
          return b;
        };
        if(status.sourceElevation > status.targetElevation){ if(testDepuis('rangeToSource')) return true; }
        else if(status.targetElevation > status.sourceElevation){ if(testDepuis('rangeToTarget')) return true; }
      }
    }
    return false;
  }

  checkBreachApplies(status, result, l){
    if(status.LOSis60Degree || status.LOSisHorizontal) return false;
    if(this.isAdjacentHexside(status.sourceHex, l) && this.isAdjacentHexside(status.targetHex, l)) return true;
    return l.getTerrain().getName().includes('Interior Factory Wall');
  }

  getHexsideWhenLOSAlongHexside(status){
    const t = status.targetEnterHexsides;
    if(t[0] === -1) t[0] = this.findMissingTargetEnterHexside(0, status);
    if(t[1] === -1) t[1] = this.findMissingTargetEnterHexside(1, status);
    const paire = (a, b) => (t[0] === a && t[1] === b) || (t[0] === b && t[1] === a);
    const ch = status.currentHex, x = status.currentCol, y = status.currentRow;
    if(paire(3, 4)) return ch.getNearestHexside(x, y, 2, 5);
    if(paire(4, 5)) return ch.getNearestHexside(x, y, 3, 0);
    if(paire(5, 0)) return ch.getNearestHexside(x, y, 4, 1);
    if(paire(0, 1)) return ch.getNearestHexside(x, y, 5, 2);
    if(paire(1, 2)) return ch.getNearestHexside(x, y, 0, 3);
    if(paire(2, 3)) return ch.getNearestHexside(x, y, 1, 4);
    return -1;
  }

  findMissingTargetEnterHexside(missingside, status){
    const s = status.sourceExitHexsides, t = status.targetEnterHexsides;
    const m6 = v => v > 5 ? v - 6 : v;
    if(missingside === 0){
      const match = m6(s[1] + 3);
      return match === t[1] ? m6(s[0] + 3) : match;
    }
    const match = m6(s[0] + 3);
    return match === t[0] ? m6(s[1] + 3) : match;
  }
}

// ── LOSStatus (classe interne de Map) : l'état de la ligne en cours.
class LosStatus {
  constructor(map, source, useAuxSourceLOSPoint, target, useAuxTargetLOSPoint, result, gi){
    this.map = map;
    this.source = source; this.useAuxSourceLOSPoint = useAuxSourceLOSPoint;
    this.target = target; this.useAuxTargetLOSPoint = useAuxTargetLOSPoint;
    this.result = result; this.vaslGameInterface = gi;
    this.blocked = false; this.reason = '';
    this.currentTerrain = null; this.currentTerrainfromCounter = false;
    this.currentTerrainHgt = 0; this.groundLevel = -9999;
    this.tempHex = null; this.rangeToSource = 0;
    this.bridgeArea = null; this.bridgeRoadArea = null; this.bridge = null;
    this.continuousSlope = true;
    this.sourceExitHexsides = [-1, -1]; this.targetEnterHexsides = [-1, -1];
    this.crossingHillock = null; this.crossedHillocks = new Set();
    this.sourceAdjacentHillock = null; this.targetAdjacentHillock = null;
    this.firstWallCrossed = null; this.firstWallPoint = null;
    this.firstRubbleCrossed = null; this.firstHalfLevelHindrance = null;
    this.ignoreGroundLevelHex = null; this.previousHex = null;
    this.depressionhexsidesreducerange = false;

    const sp = useAuxSourceLOSPoint ? source.getAuxLOSPoint() : source.getLOSPoint();
    const tp = useAuxTargetLOSPoint ? target.getAuxLOSPoint() : target.getLOSPoint();
    this.sourceX = Math.trunc(sp.x); this.sourceY = Math.trunc(sp.y);
    this.targetX = Math.trunc(tp.x); this.targetY = Math.trunc(tp.y);
    this.colDir = this.targetX - this.sourceX < 0 ? -1 : 1;
    this.rowDir = this.targetY - this.sourceY < 0 ? -1 : 1;
    this.numCols = Math.abs(this.targetX - this.sourceX) + 1;
    this.sourceHex = source.getHex(); this.targetHex = target.getHex();
    this.currentHex = this.sourceHex;
    this.setSourceAndTargetElevations();
    this.range = LosMap.range(this.sourceHex, this.targetHex, map.getMapConfiguration());
    this.rangeToTarget = this.range;
    this.LOSLeavesBuilding = !this.sourceHex.getCenterLocation().getTerrain().isBuilding();
    this.deltaY = (this.targetY - this.sourceY) / this.numCols;
    this.enter = this.sourceY;
    this.exit = this.enter + this.deltaY;
    result.setRange(this.rangeToTarget);
    this.LOSisHorizontal = this.sourceY === this.targetY;
    result.setLOSisHorizontal(this.LOSisHorizontal);
    this.slope = Math.abs((this.sourceY - this.targetY) / (this.sourceX - this.targetX));
    let tolerance = 0.05;
    if(this.range >= 5 && this.range <= 15) tolerance = 0.03;
    else if(this.range > 15) tolerance = 0.015;
    this.LOSis60Degree = Math.abs(this.slope - Math.tan(Math.PI / 3)) < tolerance;
    result.setLOSis60Degree(this.LOSis60Degree);
    if(this.LOSis60Degree){
      if(this.colDir === 1){
        if(this.rowDir === 1){ result.setSourceExitHexspine(3); result.setTargetEnterHexspine(0); }
        else { result.setSourceExitHexspine(1); result.setTargetEnterHexspine(4); }
      } else {
        if(this.rowDir === 1){ result.setSourceExitHexspine(4); result.setTargetEnterHexspine(1); }
        else { result.setSourceExitHexspine(0); result.setTargetEnterHexspine(3); }
      }
    } else if(this.slope === 0){
      if(this.colDir === 1){ result.setSourceExitHexspine(2); result.setTargetEnterHexspine(5); }
      else { result.setSourceExitHexspine(5); result.setTargetEnterHexspine(2); }
    }
    this.setEnterExitHexsides();

    let sourceadj = 0, targetadj = 0;
    if(source.getTerrain().isRooftop() && source.getLevelInHex() !== 1) sourceadj = -1;
    if(target.getTerrain().isRooftop() && target.getLevelInHex() !== 1) targetadj = -1;
    const sE = this.sourceElevation + sourceadj, tE = this.targetElevation + targetadj, rg = this.range;
    const plat = this.LOSis60Degree || this.LOSisHorizontal;
    this.exitsSourceDepression = source.isDepressionTerrain()
      && ((tE - sE > 0 && tE - sE <= rg && rg !== 1) || (plat && this.exitsDepressionTerrainHexside()) || tE === sE || sE > tE);
    this.entersTargetDepression = target.isDepressionTerrain()
      && ((sE - tE > 0 && sE - tE <= rg && rg !== 1) || (plat && this.entersDepressionTerrainHexside()) || tE === sE || tE > sE);
    this.slopes = (this.exitsSlopeHexside() && this.sourceElevation >= this.targetElevation)
               || (this.entersSlopeHexside() && this.targetElevation >= this.sourceElevation);
    this.startsOnHillock = source.getTerrain().getName() === _HILLOCK || this.slopes;
    this.endsOnHillock = target.getTerrain().getName() === _HILLOCK;
    if(this.startsOnHillock) this.crossingHillock = map.getHillock(this.sourceHex);
    this.setAdjacentToHillock();
  }

  exitsSlopeHexside(){
    const s = this.sourceExitHexsides;
    return (s[0] !== -1 && this.sourceHex.hasSlope(s[0])) || (s[1] !== -1 && this.sourceHex.hasSlope(s[1]));
  }
  entersSlopeHexside(){
    const t = this.targetEnterHexsides;
    return (t[0] !== -1 && this.targetHex.hasSlope(t[0])) || (t[1] !== -1 && this.targetHex.hasSlope(t[1]));
  }
  exitsDepressionTerrainHexside(){
    if(this.LOSis60Degree || this.LOSisHorizontal) return false;
    const s = this.sourceExitHexsides;
    return (s[0] !== -1 && this.sourceHex.getHexsideLocation(s[0]).isDepressionTerrain())
        || (s[1] !== -1 && this.sourceHex.getHexsideLocation(s[1]).isDepressionTerrain());
  }
  entersDepressionTerrainHexside(){
    if(this.LOSis60Degree || this.LOSisHorizontal) return false;
    const t = this.targetEnterHexsides;
    return (t[0] !== -1 && this.targetHex.getHexsideLocation(t[0]).isDepressionTerrain())
        || (t[1] !== -1 && this.targetHex.getHexsideLocation(t[1]).isDepressionTerrain());
  }
  // Ligne de crête : niveaux différents, ou côté de dépression entre une
  // dépression et un hex qui n'en est pas une (ou l'inverse).
  _crete(hex, side){
    if(side === -1) return false;
    const m = this.map, a = m.getAdjacentHex(hex, side);
    if(hex.getBaseLevelofHex() !== a.getBaseLevelofHex()) return true;
    const hd = hex.getHexsideLocation(side).isDepressionTerrain();
    return (hex.isDepressionTerrain() && hd && !a.isDepressionTerrain())
        || (!hex.isDepressionTerrain() && hd && a.isDepressionTerrain());
  }
  exitHexsideIsCrest(){
    const s = this.sourceExitHexsides;
    return this._crete(this.sourceHex, s[0]) || this._crete(this.sourceHex, s[1]);
  }
  enterHexsideIsCrest(){
    if(this.targetHex == null) return false;
    const t = this.targetEnterHexsides;
    return this._crete(this.targetHex, t[0]) || this._crete(this.targetHex, t[1]);
  }

  setSourceAndTargetElevations(){
    let adj = (!this.source.isCenterLocation() && this.sourceHex.isDepressionTerrain()) ? 1 : 0;
    this.sourceElevation = this.sourceHex.getBaseLevelofHex() + this.source.getLevelInHex() + adj;
    adj = (!this.target.isCenterLocation() && this.targetHex.isDepressionTerrain()) ? 1 : 0;
    this.targetElevation = this.targetHex.getBaseLevelofHex() + this.target.getLevelInHex() + adj;
  }

  setHillockStatus(){
    const m = this.map, h = m.getHillock(this.currentHex);
    if(this.crossingHillock != null && h == null){
      this.crossedHillocks.add(this.crossingHillock);
      this.crossingHillock = null;
    } else if(this.crossingHillock == null && h != null) this.crossingHillock = h;
    const n = this.currentTerrain.getName(), c = this.currentHex.getCenterLocation().getTerrain().getName();
    const gi = this.vaslGameInterface.getTerrain(this.currentHex);
    if(n === _STONE_RUBBLE){
      if((c === _STONE_RUBBLE || gi === _STONE_RUBBLE) && this.firstRubbleCrossed == null) this.firstRubbleCrossed = this.currentHex;
    } else if(n === _WOODEN_RUBBLE){
      if((c === _WOODEN_RUBBLE || gi === _WOODEN_RUBBLE) && this.firstRubbleCrossed == null) this.firstRubbleCrossed = this.currentHex;
    }
  }

  setAdjacentToHillock(){
    const m = this.map;
    const butte = (hex, side) => {
      if(side === -1) return null;
      const a = m.getAdjacentHex(hex, side);
      return a != null && a.getCenterLocation().getTerrain().getName() === _HILLOCK ? m.getHillock(a) : undefined;
    };
    if(!this.startsOnHillock) for(const s of this.sourceExitHexsides){
      const b = butte(this.sourceHex, s);
      if(b !== null && b !== undefined) this.sourceAdjacentHillock = b;
    }
    if(!this.endsOnHillock) for(const s of this.targetEnterHexsides){
      const b = butte(this.targetHex, s);
      if(b !== null && b !== undefined) this.targetAdjacentHillock = b;
    }
  }

  setEnterExitHexsides(){
    let sourceSet = false, targetSet = false;
    if(this.source.isCenterLocation()){ this.sourceExitHexsides = this.getExitFromCenterHexsides(); sourceSet = true; }
    if(this.target.isCenterLocation()){
      const t = this.getExitFromCenterHexsides();
      this.targetEnterHexsides = [LosHex.getOppositeHexside(t[0]), LosHex.getOppositeHexside(t[1])];
      targetSet = true;
    }
    const cotes = (hex, dest) => {
      let h = this.getHexsideCrossed(hex);
      if(h.length > 2) h = this.removeVertexHexsides(hex, h);
      if(h.length > 0) dest[0] = h[0];
      if(h.length > 1) dest[1] = h[1];
    };
    if(!sourceSet) cotes(this.sourceHex, this.sourceExitHexsides);
    if(!targetSet) cotes(this.targetHex, this.targetEnterHexsides);
  }

  getExitFromCenterHexsides(){
    const slope = (this.sourceY - this.targetY) / (this.sourceX - this.targetX);
    const e = [-1, -1];
    if(slope === Infinity) e[0] = 0;
    else if(slope === -Infinity) e[0] = 3;
    else if(this.LOSisHorizontal){
      if(this.colDir === 1){ e[0] = 1; e[1] = 2; } else { e[0] = 4; e[1] = 5; }
    } else if(this.LOSis60Degree){
      if(this.colDir === 1){
        if(slope > 0){ e[0] = 2; e[1] = 3; } else { e[0] = 0; e[1] = 1; }
      } else {
        if(slope > 0){ e[0] = 5; e[1] = 0; } else { e[0] = 3; e[1] = 4; }
      }
    } else {
      const h = this.getHexsideCrossed(this.sourceHex);
      if(h.length) e[0] = h[0];
    }
    return e;
  }

  // Côtés de l'hexagone (bordure élargie) que coupe la ligne, dans l'ordre.
  getHexsideCrossed(hex){
    const p = hex.getExtendedHexBorder(), xs = p.xpoints, ys = p.ypoints, n = p.npoints;
    const out = [];
    for(let i = 0; i < n; i++){
      const j = (i + 1) % n;
      if(_linesIntersect(xs[i], ys[i], xs[j], ys[j], this.sourceX, this.sourceY, this.targetX, this.targetY)) out.push(i);
    }
    return out;
  }

  removeVertexHexsides(hex, hexsides){
    return hexsides.filter(i => {
      const l = hex.getHexsideLocation(i), a = l.getLOSPoint(), b = l.getAuxLOSPoint();
      return !((a.x === this.sourceX && a.y === this.sourceY) || (b.x === this.sourceX && b.y === this.sourceY)
            || (a.x === this.targetX && a.y === this.targetY) || (b.x === this.targetX && b.y === this.targetY));
    });
  }
}
// ── Données LOS des boards et assemblage de la carte
// Remplace ASLMap.buildVASLMap et BoardArchive.addLOSDatatoVASLMap :
// les grilles de chaque board, recadrées et retournées comme son image,
// sont posées à sa place ; la grille d'hexagones suit le board du coin
// haut gauche et chaque hexagone prend le nom de son board.

// Attributs d'un élément XML (éléments sur une ligne, sans imbrication).
function _attrsXML(balise){
  const a = {};
  for(const m of balise.matchAll(/([\w:-]+)\s*=\s*"([^"]*)"/g)) a[m[1]] = m[2];
  return a;
}
function _elementsXML(texte, nom){
  const sans = texte.replace(/<!--[\s\S]*?-->/g, '');
  return [...sans.matchAll(new RegExp('<' + nom + '\\b([^>]*)>', 'g'))].map(m => _attrsXML(m[1]));
}

// terrainTypes et LOSSSRules de SharedBoardMetadata.xml.
function lireTerrainsLOS(texte){
  const vrai = v => /^(true|yes|on|1)$/i.test((v || '').trim());
  const terrains = new Map(), regles = new Map();
  for(const a of _elementsXML(texte, 'terrainType')){
    const t = new LosTerrain({
      name: a.name, type: parseInt(a.typeCode, 10),
      LOSObstacle: vrai(a.isLOSObstacle), LOSHindrance: vrai(a.isLOSHindrance),
      halfLevelHeight: vrai(a.isHalfLevelHeight), inherentTerrain: vrai(a.isInherentTerrain),
      split: parseFloat(a.split) || 0,
      lowerLOSHindrance: vrai(a.isLowerLOSHindrance), lowerLOSObstacle: vrai(a.isLowerLOSObstacle),
      height: parseInt(a.height, 10) || 0, cat: (a.LOSCategory || 'OPEN').trim()
    });
    if(t.name && t.type >= 0 && t.type < 256) terrains.set(t.name, t);
  }
  for(const a of _elementsXML(texte, 'LOSSSRule'))
    if(a.name) regles.set(a.name, {name: a.name, type: a.type, fromValue: a.fromValue, toValue: a.toValue});
  return {terrains, regles};
}

// Fichier LOSData : flux ObjectOutputStream compressé (gzip) de valeurs
// simples — en-tête ACED 0005, puis blocs 0x77 (longueur sur un octet) ou
// 0x7A (sur quatre). Contenu : largeur et hauteur en hexagones, largeur
// et hauteur de la grille, puis par pixel (x, puis y) le niveau et le
// code de terrain, puis un octet d'escalier par hexagone.
function lireLOSData(brut){
  if(brut[0] !== 0xAC || brut[1] !== 0xED) throw new Error('LOSData illisible');
  let n = 0;
  for(let i = 4; i < brut.length; ){
    const t = brut[i];
    const l = t === 0x77 ? brut[i+1] : t === 0x7A ? ((brut[i+1] << 24) | (brut[i+2] << 16) | (brut[i+3] << 8) | brut[i+4]) >>> 0 : -1;
    if(l < 0) break;
    n += l; i += (t === 0x77 ? 2 : 5) + l;
  }
  const d = new Uint8Array(n);
  let o = 0;
  for(let i = 4; i < brut.length; ){
    const t = brut[i];
    if(t !== 0x77 && t !== 0x7A) break;
    const e = t === 0x77 ? 2 : 5;
    const l = t === 0x77 ? brut[i+1] : ((brut[i+1] << 24) | (brut[i+2] << 16) | (brut[i+3] << 8) | brut[i+4]) >>> 0;
    d.set(brut.subarray(i + e, i + e + l), o);
    o += l; i += e + l;
  }
  const v = new DataView(d.buffer);
  const r = {width: v.getInt32(0), height: v.getInt32(4), gridWidth: v.getInt32(8), gridHeight: v.getInt32(12), octets: d};
  r.debutHex = 16 + r.gridWidth * r.gridHeight * 2;
  if(d.length < r.debutHex) throw new Error('LOSData tronqué');
  return r;
}
// Niveau (signé) et terrain du pixel x, y du board.
const _losNiveau  = (d, x, y) => (d.octets[16 + (x * d.gridHeight + y) * 2] << 24) >> 24;
const _losTerrain = (d, x, y) => d.octets[17 + (x * d.gridHeight + y) * 2];

// Côtés d'hexagone d'un BoardMetadata.xml : <slope hex="B5" hexsides="012"/>.
function _cotesSpeciaux(texte, nom){
  const r = [];
  for(const a of _elementsXML(texte, nom)){
    if(!a.hex || !a.hexsides) continue;
    const f = [false, false, false, false, false, false];
    for(const c of a.hexsides) if(c >= '0' && c <= '5') f[c.charCodeAt(0) - 48] = true;
    r.push([a.hex, f]);
  }
  return r;
}

// ── Règles SSR appliquées aux grilles d'un board (VASLBoard.
// applyColorSSRulestoTerrainElevationGrids), limitées à son rectangle.
function _zone(m, R, f){
  for(let y = Math.max(0, R.y); y < Math.min(m.gridHeight, R.y + R.h); y++)
    for(let x = Math.max(0, R.x); x < Math.min(m.gridWidth, R.x + R.w); x++) f(x, y);
}
function _changerTerrain(m, R, de, vers){
  if(!de || !vers) return;
  _zone(m, R, (x, y) => { if(m.getGridTerrainCode(x, y) === de.type) m.setGridTerrainCode(vers.type, x, y); });
}
const _TOUS_BATIMENTS_1 = [
  ['Stone Building, 1 Level', 'Stone Building'], ['Stone Building, 2 Level', 'Stone Building'],
  ['Stone Building, 3 Level', 'Stone Building'], ['Stone Building, 4 Level', 'Stone Building'],
  ['Stone Factory, 1.5 Level', 'Wooden Factory, 1 Level'], ['Stone Factory, 2.5 Level', 'Wooden Factory, 1 Level'],
  ['Stone Factory Wall, 1.5 Level', 'Wooden Factory Wall, 1 Level'], ['Stone Factory Wall, 2.5 Level', 'Wooden Factory Wall, 1 Level'],
  ['Stone Marketplace', 'Stone Building'],
  ['Rowhouse Wall, 1 Level', 'Rowhouse Wall'], ['Rowhouse Wall, 2 Level', 'Rowhouse Wall'],
  ['Rowhouse Wall, 3 Level', 'Rowhouse Wall'], ['Rowhouse Wall, 4 Level', 'Rowhouse Wall'],
  ['Interior Factory Wall, 1.5 Level', 'Wooden Factory Wall, 1 Level'], ['Interior Factory Wall, 2.5 Level', 'Wooden Factory Wall, 1 Level'],
  ['Gutted Stone Factory, 1.5 Level', 'Wooden Factory, 1 Level'], ['Gutted Factory Wall, 2.5 Level', 'Wooden Factory, 1 Level'],
  ['Gutted Stone Building, 1 Level', 'Gutted Stone Building'], ['Gutted Stone Building, 2 Level', 'Gutted Stone Building'],
  ['Gutted Stone Building, 3 Level', 'Gutted Stone Building'], ['Gutted Stone Building, 4 Level', 'Gutted Stone Building'],
  ['Wooden Building, 1 Level', 'Wooden Building'], ['Wooden Building, 2 Level', 'Wooden Building'],
  ['Wooden Building, 3 Level', 'Wooden Building'], ['Wooden Building, 4 Level', 'Wooden Building'],
  ['Wooden Factory, 1.5 Level', 'Wooden Factory, 1 Level'], ['Wooden Factory, 2.5 Level', 'Wooden Factory, 1 Level'],
  ['Wooden Factory Wall, 1.5 Level', 'Wooden Factory Wall, 1 Level'], ['Wooden Factory Wall, 2.5 Level', 'Wooden Factory Wall, 1 Level'],
  ['Wooden Marketplace', 'Wooden Building'],
  ['Tower, 2 Level Hindrance', 'Tower Hindrance'], ['Tower, 3 Level Hindrance', 'Tower Hindrance'],
  ['Tower, 2 Level Obstacle', 'Tower Obstacle'], ['Tower, 3 Level Obstacle', 'Tower Obstacle'],
  ['Storage Tank, 2 Level', 'Storage Tank'], ['BFP Tower, 1 Level', 'Tower Obstacle'], ['BFP Tower, 2 Level', 'Tower Obstacle']];
const _SSR_GRILLE_HEX = new Set(['AllBuildingsLevel1', 'NoStairwells', 'NoBridge', 'BridgeToFord', 'RoadsToPaths',
  'NoWoodsRoads', 'NoWoodsRoad', 'SwampToSwampPattern']);

function _ssrGrilles(m, R, ssr, regles){
  const T = n => m.getTerrain(n);
  let PTO = false;
  for(const s of ssr){
    const r = regles.get(s);
    if(!r) continue;                      // SSR inconnue : sans effet (comme VASL hors RB/RO)
    const de = r.fromValue, vers = r.toValue;
    if(r.type === 'customCode'){
      if(s === 'RowhouseBarsToBuildings'){
        _changerTerrain(m, R, T('Rowhouse Wall'), T('Stone Building'));
        for(let i = 1; i <= 4; i++) _changerTerrain(m, R, T(`Rowhouse Wall, ${i} Level`), T(`Stone Building, ${i} Level`));
      } else if(s === 'RowhouseBarsToOpenGround'){
        _changerTerrain(m, R, T('Rowhouse Wall'), T('Open Ground'));
        for(let i = 1; i <= 4; i++) _changerTerrain(m, R, T(`Rowhouse Wall, ${i} Level`), T('Open Ground'));
      } else if(s === 'NoCliffs') _changerTerrain(m, R, T('Cliff'), T('Open Ground'));
      else if(s === 'Bamboo'){ _changerTerrain(m, R, T('Brush'), T('Bamboo')); PTO = true; }
      else if(s === 'PalmTrees'){
        _changerTerrain(m, R, T('Orchard'), T('Palm Trees'));
        _changerTerrain(m, R, T('Orchard, Out of Season'), T('Palm Trees'));
        PTO = true;
      } else if(s === 'DenseJungle'){ _changerTerrain(m, R, T('Woods'), T('Dense Jungle')); PTO = true; }
      else if(!_SSR_GRILLE_HEX.has(s)) throw new Error('SSR ' + s + ' non gérée par le moteur LOS');
    } else if(r.type === 'terrainMap') _changerTerrain(m, R, T(de), T(vers));
    else if(r.type === 'elevationMap'){
      const a = parseInt(de, 10), b = parseInt(vers, 10);
      if(isNaN(a) || isNaN(b)) throw new Error('SSR ' + s + ' : niveaux invalides');
      _zone(m, R, (x, y) => { if(m.getGridElevation(x, y) === a) m.setGridElevation(b, x, y); });
    } else if(r.type === 'terrainToElevationMap'){
      const t = T(de), b = parseInt(vers, 10), og = T('Open Ground');
      // La variante « GrainTo… » de VASL ne joue jamais (compteur bloqué à 1).
      if(t && !isNaN(b)) _zone(m, R, (x, y) => {
        if(m.getGridTerrainCode(x, y) === t.type){ m.setGridElevation(b, x, y); m.setGridTerrainCode(og.type, x, y); }
      });
    } else if(r.type === 'elevationToTerrainMap') _niveauVersTerrain(m, R, r);
    else if(r.type === 'terrainToSelectElevationMap') _terrainANiveau(m, R, r);
  }
  if(PTO) _changerTerrain(m, R, T('Woods'), T('Light Jungle'));
}
function _niveauVersTerrain(m, R, r){
  const a = parseInt(r.fromValue, 10), t = m.getTerrain(r.toValue);
  if(isNaN(a) || !t) return;
  _zone(m, R, (x, y) => {
    if(m.getGridElevation(x, y) === a){
      m.setGridElevation(0, x, y);
      if(!m.getGridTerrain(x, y).isLOSObstacle()) m.setGridTerrainCode(t.type, x, y);
    }
  });
}
function _terrainANiveau(m, R, r){
  const t = m.getTerrain(r.fromValue), b = parseInt(r.toValue, 10), og = m.getTerrain('Open Ground');
  if(!t || isNaN(b)) return;
  _zone(m, R, (x, y) => {
    if(m.getGridTerrainCode(x, y) === t.type && m.getGridElevation(x, y) === b) m.setGridTerrainCode(og.type, x, y);
  });
}

// Formes de LOSDataEditor : ellipse et polygone, avec leur rectangle.
function _ellipse(x, y, w, h){
  return {getBounds(){ const x1 = Math.floor(x), y1 = Math.floor(y); return {x: x1, y: y1, width: Math.ceil(x + w) - x1, height: Math.ceil(y + h) - y1}; },
          contains(px, py){ if(w <= 0 || h <= 0) return false; const nx = (px - x) / w - 0.5, ny = (py - y) / h - 0.5; return nx*nx + ny*ny < 0.25; }};
}
// LOSDataEditor.setGridTerrain (cas utiles : terrain ordinaire, jungle dense).
function _poserTerrain(m, s, terr){
  const r = s.getBounds();
  for(let x = Math.max(r.x, 0); x < Math.min(r.x + r.width, m.gridWidth); x++)
    for(let y = Math.max(r.y, 0); y < Math.min(r.y + r.height, m.gridHeight); y++){
      if(!s.contains(x, y) || !m.onMap(x, y)) continue;
      if(terr.getName() === 'Dense Jungle' && m.getGridTerrain(x, y).getLOSCategory() === 'WATER') continue;
      m.setGridTerrainCode(terr.type, x, y);
    }
}

// Règles SSR qui demandent la grille d'hexagones (applyColorSSRulestoHexGrid).
function _ssrHexagones(m, R, ssr, regles){
  const T = n => m.getTerrain(n);
  const dans = h => { const c = h.getCenterLocation().getLOSPoint(); return c.x >= R.x && c.x < R.x + R.w && c.y >= R.y && c.y < R.y + R.h; };
  const hexes = m.hexGrid.flat().filter(dans);
  for(const s of ssr){
    const r = regles.get(s);
    if(!r) continue;
    if(r.type === 'customCode'){
      if(s === 'AllBuildingsLevel1'){
        for(const [de, vers] of _TOUS_BATIMENTS_1) _changerTerrain(m, R, T(de), T(vers));
      } else if(s === 'NoStairwells'){
        for(const h of hexes) h.setStairway(false);
      } else if(s === 'NoBridge' || s === 'BridgeToFord'){
        const gully = T('Gully'), ponts = new Set();
        _zone(m, R, (x, y) => {
          if(m.getGridTerrain(x, y).getLOSCategory() === 'BRIDGE'){
            m.setGridTerrainCode(gully.type, x, y);
            const h = m.gridToHex(x, y);
            if(h) ponts.add(h);
          }
        });
        for(const h of ponts){
          const p = h.getCenterLocation().getLOSPoint(), e = _ellipse(p.x - m.hexHeight/3, p.y - m.hexHeight/3, m.hexHeight*2/3, m.hexHeight*2/3);
          const b = e.getBounds();
          for(let x = Math.max(b.x, 0); x <= Math.min(b.x + b.width, m.gridWidth - 1); x++)
            for(let y = Math.max(b.y, 0); y <= Math.min(b.y + b.height, m.gridHeight - 1); y++){
              if(!e.contains(x, y)) continue;
              const c = m.gridToHex(x, y);
              if(c) m.setGridElevation(c.isDepressionTerrain() ? c.getBaseLevelofHex() : c.getBaseLevelofHex() - 1, x, y);
            }
        }
      } else if(s === 'RoadsToPaths' || s === 'NoWoodsRoads' || s === 'NoWoodsRoad'){
        const xadj = m.getMapConfiguration().includes('FullHex') ? -m.getA1CenterX() : 0;
        for(const h of hexes){
          const route = h.getCenterLocation().getTerrain().getLOSCategory() === 'ROAD'
            && [0, 1, 2, 3, 4, 5].some(i => h.getHexsideLocation(i).getTerrain().getName() === 'Woods');
          if(!route) continue;
          const p = h.getCenterLocation().getLOSPoint();
          _poserTerrain(m, _ellipse(p.x + xadj - m.hexHeight/3, p.y - m.hexHeight/3, m.hexHeight*2/3, m.hexHeight*2/3), T('Woods'));
        }
      } else if(s === 'SwampToSwampPattern'){
        const marais = T('Marsh'), swamp = T('Swamp');
        const bois = n => n === 'Woods' || n === 'Light Jungle' || n === 'Dense Jungle';
        for(const h of hexes){
          const t = h.getnearcenterLocationTerrain();
          if(!t || t.getName() !== 'Marsh') continue;
          let ok = false;
          for(let x = 0; x < 6; x++){
            const a = m.getAdjacentHex(h, x), at = a && a.getnearcenterLocationTerrain();
            if(at && bois(at.getName())) ok = true;
          }
          if(!ok) continue;
          const poly = h.getHexBorder(), b = poly.getBounds();
          _zone(m, {x: b.x, y: b.y, w: b.width + 1, h: b.height + 1}, (x, y) => {
            if(m.getGridTerrainCode(x, y) === marais.type && poly.contains(x, y)) m.setGridTerrainCode(swamp.type, x, y);
          });
        }
      }
    } else if(r.type === 'elevationToTerrainMap') _niveauVersTerrain(m, R, r);
    else if(r.type === 'terrainToSelectElevationMap') _terrainANiveau(m, R, r);
  }
}

// Hexagones d'un board : nom local et centre dans le repère LOS, comme
// _buildHexTable (demi-hexagones de bord compris).
function _hexesBoardLOS(b, g, R, z){
  const out = [];
  const cropX = b.crop ? (b.crop.x || 0) : 0, cropY = b.crop ? (b.crop.y || 0) : 0;
  const i0 = Math.max(0, Math.floor((-g.hw/4 - g.x0) / g.hw)), i1 = Math.ceil((g.w + g.hw/4 - g.x0) / g.hw);
  const r0 = Math.floor((-g.hh/4 - g.y0) / g.hh), r1 = Math.ceil((g.h + g.hh/4 - g.y0) / g.hh) + 1;
  for(let i = i0; i <= i1; i++){
    const col = hexrow(i);
    for(let r = r0; r <= r1; r++){
      const c = centreHex(g, col + r);
      if(c.x <= -g.hw/4 || c.x >= g.w + g.hw/4 || c.y <= -g.hh/4 || c.y >= g.h + g.hh/4) continue;
      const lx = c.x - cropX, ly = c.y - cropY;
      if(ly < -g.hh/2 || ly > R.h + g.hh/2 || lx < -g.hw/2 || lx > R.w + g.hw/2) continue;
      out.push({nom: col + r, x: R.x + (b.reversed ? R.w - lx : lx), y: R.y + (b.reversed ? R.h - ly : ly)});
    }
  }
  return out;
}

// Hexagone de la grille LOS dont le centre est au point (x, y), à 3 px près.
function _hexAuCentre(m, x, y){
  const hw = m.hexWidth, hh = m.hexHeight, x0 = m.A1CenterX < 0 ? 0 : m.A1CenterX;
  const col = Math.round((x - x0) / hw);
  if(col < 0 || col >= m.hexGrid.length) return null;
  const row = m.A1CenterY === 0
    ? Math.round((y - hh/2 + hh/2 * (col % 2 === 0 ? 1 : 0)) / hh)
    : Math.round((y - (m.A1CenterY < 0 ? hh/2 : m.A1CenterY) + hh/2 * (col % 2)) / hh);
  if(!m.hexOnMap(col, row)) return null;
  const h = m.getHex(col, row), c = h.getHexCenter();
  return Math.abs(c.x - x) <= 3 && Math.abs(c.y - y) <= 3 ? h : null;
}

// ── Overlays d'hexagones dans la LOS (ASLMap.adjustLOSForOverlays)
// Chaque pixel opaque de l'overlay donne son terrain et son niveau par sa
// couleur (couleurs VASL partagées, remplacées par celles du board). La
// première passe réécrit les grilles terrain et niveau ; la seconde, sur
// la grille d'hexagones encore vierge, étend le terrain inhérent à tout
// l'hexagone, pose les côtés et le niveau du point central.

// Couleurs VASL : nom → {rgb, terrain, niveau} et rgb → nom.
function lireCouleursLOS(texteCommun, texteBoard){
  const parNom = new Map(), parRgb = new Map();
  const lire = (texte, remplace) => {
    const bloc = (texte.replace(/<!--[\s\S]*?-->/g, '').match(/<colors>([\s\S]*?)<\/colors>/) || [])[1];
    if(!bloc) return;
    for(const a of _elementsXML(bloc, 'color')){
      if(!a.name || a.red === undefined) continue;
      const rgb = (+a.red << 16) | (+a.green << 8) | +a.blue;
      parNom.set(a.name, {rgb, terrain: a.terrain, niveau: a.elevation});
      if(remplace || !parRgb.has(rgb)) parRgb.set(rgb, a.name);
    }
  };
  lire(texteCommun, false);
  if(texteBoard) lire(texteBoard, true);
  return {parNom, parRgb};
}

const _NOIR = 0, _BLANC = 0xFFFFFF;
// Couleurs à sauter en cherchant une couleur voisine : SnowHexDots2,
// points centraux, numéros de colline jaunes, bouches d'égout.
const _COUL_IGNOREES = new Set([-5261152, -262915, -259, -246, -16776960].map(v => v & 0xFFFFFF));
const _NUMEROS_BOARD = new Set(['WhiteHexNumbers', 'WinterBlackHexNumbers', 'MudBoardNum', 'DTO_BoardNum', 'AD_WinterBlackHexNumbers']);

// Type de terrain d'un overlay d'après son nom (getOverlayTerrainType).
function _typeOverlayLOS(n){
  const regles = [['Steppe', 'Steppe'], ['SSO', 'SSO'], ['Bocage', 'Bocage'], ['BrokenTerrain', 'Broken'],
    ['PalmTrees', 'Palm Trees'], ['Bamboo', 'Bamboo'], ['elrr', 'Elevated Railroad'], ['surr', 'Sunken Railroad'],
    ['rr', 'Railroad'], ['NoRoads', 'NoRoads'], ['rv', 'River'], ['sw', 'Swamp'], ['be', 'Beach'], ['b', 'Brush'],
    ['hd', 'Hedges'], ['sh', 'Shellholes']];
  for(const [k, t] of regles) if(n.includes(k)) return t;
  if(n.includes('og') || n === 'dx1' || n === 'dx5') return 'Open Ground';
  const suite = [['m', 'Marsh'], ['g', 'Grain'], ['p', 'Water'], ['ow', 'Woods'], ['oc', 'Ocean']];
  for(const [k, t] of suite) if(n.includes(k)) return t;
  if(n.includes('o') || n === 'dx3' || n === 'dx7') return 'Orchard';
  if(n.includes('wd') || n === 'dx2' || n === 'dx4') return 'Woods';
  if(n.includes('x') && !n.includes('dx')) return 'Building';
  const fin = [['hi', 'Hill'], ['st', 'Stream'], ['sr', 'Stone Rubble'], ['wr', 'Wooden Rubble'], ['wt', 'Water'],
    ['v', 'Vineyard'], ['w', ' '], ['rp', 'Rice Paddy'], ['ef', 'Effluent'], ['LightGrain', 'Light Grain']];
  for(const [k, t] of fin) if(n.includes(k)) return t;
  return '';
}

// Bâtiments des overlays « x » : le type vient du numéro de l'overlay.
function _batimentOverlay(nom, terr){
  const n = /^\d+$/.test(nom.substring(1)) ? parseInt(nom.substring(1), 10) : 0;
  const types = {'Stone Building': [1, 2, 4, 5, 6, 7, 11], 'Wooden Building': [3, 9, 26, 28, 29],
    'Stone Building, 1 Level': [12, 17, 19, 20, 21, 22, 23, 24, 25, 27, 30, 39],
    'Wooden Building, 1 Level': [13, 41], 'Stone Building, 2 Level': [8, 16, 38, 40], 'Wooden Building, 2 Level': [10]};
  for(const [t, l] of Object.entries(types)) if(l.includes(n)) return t;
  return terr.getName();   // « Multiple Types » : VASL demande le type au joueur
}

// État d'un overlay en cours d'application (LOSonOverlays).
class LosOverlay {
  constructor(m, couleurs, ov, R, b){
    this.m = m; this.c = couleurs; this.ov = ov; this.R = R; this.b = b;
    this.cropX = b.crop ? (b.crop.x || 0) : 0; this.cropY = b.crop ? (b.crop.y || 0) : 0;
    this.bamboo = (b.ssrList || []).includes('Bamboo');
    const type = _typeOverlayLOS(ov.nom);
    this.type = type === 'Brush' && this.bamboo ? 'Bamboo' : type;
    this.cx = 0; this.cy = 0; this.px = 0; this.py = 0;
  }
  // Point de l'overlay → pixel de la carte LOS (board recadré, retourné).
  // Hors du board, le point tombe sur le board voisin : un overlay à
  // cheval sur deux boards les couvre tous les deux.
  versCarte(cx, cy){
    const lx = this.ov.x + cx - this.cropX, ly = this.ov.y + cy - this.cropY;
    return {x: this.R.x + (this.b.reversed ? this.R.w - 1 - lx : lx), y: this.R.y + (this.b.reversed ? this.R.h - 1 - ly : ly)};
  }
  surOverlay(x, y){ return x >= 0 && x < this.ov.w && y >= 0 && y < this.ov.h; }
  argb(x, y){ const i = (y * this.ov.w + x) * 4, d = this.ov.data; return {a: d[i + 3], rgb: (d[i] << 16) | (d[i + 1] << 8) | d[i + 2]}; }
  nomCouleur(rgb){ return rgb == null ? null : this.c.parRgb.get(rgb) || null; }
  terrainNom(nom){
    const e = this.c.parNom.get(nom);
    if(!e || e.terrain === 'UNKNOWN') return -1;
    const t = this.m.getTerrain(e.terrain);
    return t ? t.type : -1;
  }
  terrainCouleur(rgb){ const n = this.nomCouleur(rgb); return n ? this.terrainNom(n) : -1; }
  niveauCouleur(rgb){
    const n = this.nomCouleur(rgb), e = n && this.c.parNom.get(n);
    if(!e || e.niveau === 'UNKNOWN') return -99;
    const v = parseInt(e.niveau, 10);
    return isNaN(v) ? -99 : v;
  }

  // getOverlayNearestColor : couleur d'un pixel voisin, en diagonale,
  // jusqu'à six pixels.
  couleurProche(nx, ny){
    let c = 0, a = 2, coul = _NOIR;
    const lisible = (dx, dy, ox, oy) => this.m.onMap(nx + dx, ny + dy) && this.surOverlay(this.cx + ox, this.cy + oy);
    while(coul === _NOIR || _NUMEROS_BOARD.has(this.nomCouleur(coul)) || _COUL_IGNOREES.has(coul)){
      let p = null;
      if(lisible(a, a, a - 1, a)) p = [a - 1, a];
      else if(lisible(a, -a, a - 1, -a)) p = [a - 1, -a];
      else if(lisible(-a, a, -(a - 1), a)) p = [-(a - 1), a];
      else if(lisible(-a, -a, -(a - 1), -a)) p = [-(a - 1), -a];
      if(p){ const q = this.argb(this.cx + p[0], this.cy + p[1]); if(q.a) c = q.rgb | 0x1000000; }
      if(c !== 0) coul = c & 0xFFFFFF;
      if(++a > 6) break;
    }
    return coul;
  }

  // getOverlayTerrainfromColor
  terrainDeCouleur(rgb){
    const t = this.terrainCouleur(rgb);
    if(t >= 0) return this.m.getTerrainParCode(t);
    let terr = null;
    while(terr == null){
      const coul = this.couleurProche(this.px, this.py);
      if(coul === _BLANC) terr = this.m.getTerrainParCode(this.terrainNom('L0Winter'));
      else if(coul === _NOIR) terr = this.m.getGridTerrain(this.px, this.py);
      else {
        let k = this.terrainCouleur(coul);
        if(k === -1) k = 0;
        terr = this.m.getTerrainParCode(k);
      }
      if(terr == null) terr = this.m.getTerrain('Open Ground');
    }
    return terr;
  }

  // getOverlayElevationfromColor
  niveauDeCouleur(rgb){
    let e = rgb == null ? -99 : this.niveauCouleur(rgb);
    if(e === -99){
      const coul = this.couleurProche(this.px, this.py);
      if(coul === _BLANC) e = 0;
      else if(coul !== _NOIR) e = this.niveauCouleur(coul);
    }
    return e;
  }

  // pixelOnTransparentOverlayBorder
  bordTransparent(){
    const {cx, cy, px, py, ov} = this;
    let a = 3;
    if(cx === 0 || cx === ov.w - 1){
      if(this.m.onMap(px, py + a)){ if(cy + a > ov.h - 1) a = -3; return !this.argb(cx, cy + a).a; }
      if(this.m.onMap(px, py - a)){ if(cy - a < 0) a = -3; return !this.argb(cx, cy - a).a; }
      return true;
    }
    if(cy === 0 || cy === ov.h - 1){
      if(this.m.onMap(px + a, py)){ if(cx + a > ov.w - 1) a = -3; return !this.argb(cx + a, cy).a; }
      if(this.m.onMap(px - a, py)){ if(cx - a < 0) a = -3; return !this.argb(cx - a, cy).a; }
      return true;
    }
    return false;
  }

  // setTerrainForTransparentPixel : point central ou numéro d'hexagone.
  terrainPixelTransparent(){
    if(this.bordTransparent()) return null;
    const {cx, cy, px, py} = this, m = this.m;
    for(let r = 1; r <= 2; r++){
      for(const [dx, dy] of [[r, 0], [0, r]]){
        if(m.onMap(px - dx, py - dy) && this.surOverlay(cx - dx, cy - dy) && m.onMap(px + dx, py + dy) && this.surOverlay(cx + dx, cy + dy)){
          const c1 = this.argb(cx - dx, cy - dy), c2 = this.argb(cx + dx, cy + dy);
          const coul = c1.a ? c1.rgb : c2.a ? c2.rgb : null;
          if(coul !== null){
            const t = this.terrainDeCouleur(coul);
            if(t != null) return t;
          }
        }
      }
    }
    return null;
  }

  // updateTerrainElevationGridsforOverlays (sans « Preserve Levels »)
  passeGrilles(){
    const m = this.m, ov = this.ov;
    for(this.cx = 0; this.cx < ov.w; this.cx++)
      for(this.cy = 0; this.cy < ov.h; this.cy++){
        const p = this.versCarte(this.cx, this.cy);
        if(!p || !m.onMap(p.x, p.y) || !m.gridToHex(p.x, p.y)) continue;
        this.px = p.x; this.py = p.y;
        const q = this.argb(this.cx, this.cy);
        let terr, rgb = null;
        if(q.a){ rgb = q.rgb; terr = this.terrainDeCouleur(rgb); }
        else {
          terr = this.terrainPixelTransparent();
          if(terr == null) continue;
        }
        if(this.bamboo && terr.getName() === 'Brush') terr = m.getTerrain('Bamboo') || terr;
        if(terr.isBuildingTerrain() && !terr.isHexsideTerrain()){
          const t = m.getTerrain(_batimentOverlay(ov.nom, terr)) || terr;
          m.setGridTerrainCode(t.type, p.x, p.y);
        } else m.setGridTerrainCode(terr.type, p.x, p.y);
        let e = this.niveauDeCouleur(rgb);
        if(e === -99) e = m.getGridElevation(p.x, p.y);
        if(terr.isDepression()) e = -1;
        m.setGridElevation(e, p.x, p.y);
      }
  }

  // updateHexGridforOverlayTerrain, sur une grille d'hexagones vierge :
  // VASL parcourt la carte depuis le coin de l'overlay jusqu'aux bords.
  passeHexagones(){
    const m = this.m, type = this.type, inh = new Map(), elev = new Set(), R = this.R;
    const lx = this.ov.x - this.cropX, ly = this.ov.y - this.cropY;
    const ox = R.x + (this.b.reversed ? R.w - (lx + this.ov.w) : lx), oy = R.y + (this.b.reversed ? R.h - (ly + this.ov.h) : ly);
    for(let x = Math.max(0, ox); x < Math.min(m.gridWidth, ox + m.gridWidth); x++)
      for(let y = Math.max(0, oy); y < Math.min(m.gridHeight, oy + m.gridHeight); y++){
        const hex = m.hexAuPixel(x, y);
        if(!hex) continue;
        let terr = m.getGridTerrain(x, y) || m.getTerrain('Open Ground');
        if(this.bamboo && terr.getName() === 'Brush') terr = m.getTerrain('Bamboo') || terr;
        const n = terr.getName();
        if(terr.isInherentTerrain() || (type === 'Steppe' && (n === 'Brush' || n === 'Woods'))
           || (type === 'Broken' && n === 'Brush') || (type === 'Bamboo' && n === 'Brush')){
          if(!inh.has(hex)){
            const c = hex.getHexCenter();
            if(Math.hypot(c.x - x, c.y - y) < 15){
              inh.set(hex, terr);
              // doNonInherentToInherentFix : VASL déplace au passage les
              // points des côtés haut et bas de l'hexagone.
              for(let z = 0; z < 6; z++){
                const pt = hex.getHexsideLocation(z).getEdgeCenterPoint();
                if(z === 0) pt.y -= 1;
                if(z === 3) pt.y += 1;
                if(m.onMap(pt.x, pt.y)) m.setGridTerrainCode(terr.type, pt.x, pt.y);
              }
            }
          }
        }
        elev.add(hex);
        this.terrainDuLieu(hex, terr, x, y);
      }
    for(const [hex, terr] of inh){
      const r = hex.getHexBorder().getBounds();
      for(let i = r.x; i < r.x + r.width; i++)
        for(let j = r.y; j < r.y + r.height; j++)
          if(m.onMap(i, j) && hex.contains(i, j) && !m.getGridTerrain(i, j).isHexsideTerrain()) m.setGridTerrainCode(terr.type, i, j);
      hex.getCenterLocation().setTerrain(terr);
      hex.resetHexsideTerrain();
    }
    for(const hex of elev){
      const e = this.niveauDansHex(hex), p = hex.getCenterLocation().getLOSPoint();
      if(m.onMap(p.x, p.y)) m.setGridElevation(e, p.x, p.y);
    }
  }

  // setOverlayTerrainType : dépression au centre, terrain de côté.
  terrainDuLieu(hex, terr, x, y){
    const near = hex.getNearestLocation(x, y);
    if(near.isCenterLocation() && !this.type.includes('NoRoads') && !terr.isCliff() && !terr.isHexsideTerrain()){
      if(terr.isDepression()) hex.getCenterLocation().setDepressionTerrain(terr);
      return;
    }
    const side = hex.getLocationHexside(near);
    if(side === -1) return;
    const e = hex.getHexsideLocation(side).getEdgeCenterPoint();
    if(Math.abs(x - e.x) >= 10 || Math.abs(y - e.y) >= 10) return;
    if(terr.isHexsideTerrain()){
      hex.setHexsideTerrain(side, terr);
      hex.setHexsideLocationTerrain(side, terr);
    } else if(hex.getHexsideTerrain(side) != null){
      hex.setHexsideTerrain(side, null);
      hex.setHexsideLocationTerrain(side, null);
      const a = this.m.getAdjacentHex(hex, side);
      if(a != null){
        a.setHexsideTerrain(LosHex.getOppositeHexside(side), terr);
        a.setHexsideLocationTerrain(LosHex.getOppositeHexside(side), terr);
      }
    }
  }

  // findElevInHex : niveau le plus haut de l'hexagone, -1 s'il contient
  // une vraie zone de dépression.
  niveauDansHex(hex){
    const m = this.m, r = hex.getHexBorder().getBounds();
    let max = -4;
    for(let i = r.x; i < r.x + r.width; i++)
      for(let j = r.y; j < r.y + r.height; j++){
        if(!m.onMap(i, j) || !hex.contains(i, j)) continue;
        if(m.getGridTerrain(i, j).isDepression() && this.depressionPleine(i, j)) return -1;
        max = Math.max(max, m.getGridElevation(i, j));
      }
    return max;
  }
  depressionPleine(i, j){
    const m = this.m;
    for(let x = i - 1; x < i + 2; x++)
      for(let y = j - 1; y < j + 2; y++){
        if(!m.onMap(x, y)) return false;
        const h = m.hexAuPixel(i, j);
        if(!h || !h.contains(x, y)) return false;
        if(!m.getGridTerrain(x, y).isDepression()) return false;
      }
    return true;
  }
}

// Overlays d'un board pris par la LOS (VASL saute ceux que ses SSR gèrent).
const _overlaysLOS = b => (b.ovrLOS || []).filter(o => o.data && !/NoCliffs|LightWoods|RB_Gutted/.test(o.nom));

// Construit la carte LOS d'une disposition de boards (sortie de
// _parseBoardSegment après _layoutBoards). Lève une erreur lisible si
// la carte ne s'y prête pas.
async function construireCarteLOS(boards, terrainsXML, archiveDe, opts = {}){
  const {terrains, regles} = lireTerrainsLOS(terrainsXML);
  if(!terrains.size) throw new Error('terrains LOS introuvables');
  const reels = boards.filter(b => !/^NULV?$/.test(b.name));
  if(!reels.length) throw new Error('aucun board');
  const z = boards[0].zoom || 1;
  if(boards.some(b => (b.zoom || 1) !== z)) throw new Error('boards à des échelles différentes');
  const g0 = geoBoard(reels[0]);
  for(const b of reels){
    const g = geoBoard(b);
    if(Math.round(g.hw) !== Math.round(g0.hw) || Math.round(g.hh) !== Math.round(g0.hh))
      throw new Error('boards de tailles d\'hexagone différentes');
  }
  const hw = g0.hw === 56.3125 ? 56.25 : g0.hw, hh = g0.hh;

  // Rectangles des boards, en pixels de board.
  for(const b of boards){
    const g = geoBoard(b);
    b._los = {x: Math.round((b.px || 0) / z), y: Math.round((b.py || 0) / z),
              w: (b.crop && b.crop.w > 0) ? b.crop.w : g.w, h: (b.crop && b.crop.h > 0) ? b.crop.h : g.h};
  }
  const gridW = Math.max(...boards.map(b => b._los.x + b._los.w));
  const gridH = Math.max(...boards.map(b => b._los.y + b._los.h));

  const donnees = new Map();
  for(const b of reels){
    if(donnees.has(b.name)) continue;
    const files = await archiveDe(b.name);
    const brut = _fichier(files, 'LOSData');
    if(!brut && opts.sansDonnees){ donnees.set(b.name, null); continue; }
    if(!brut) throw new Error('le board ' + b.name + ' n\'a pas de données LOS');
    const xml = _fichier(files, 'BoardMetadata.xml');
    donnees.set(b.name, {d: lireLOSData(await _gunzip(brut)), meta: xml ? new TextDecoder().decode(xml) : ''});
  }

  // Grille d'hexagones : A1 suit le board du coin haut gauche.
  const coin = boards.find(b => b._los.x === 0 && b._los.y === 0) || boards[0];
  const hx = _hexesBoardLOS(coin, geoBoard(coin), coin._los, z);
  if(!hx.length) throw new Error('board ' + coin.name + ' sans hexagone');
  const xmin = Math.min(...hx.filter(h => h.x >= -3).map(h => h.x));
  const ymin = Math.min(...hx.filter(h => Math.abs(h.x - xmin) < 3 && h.y >= -3).map(h => h.y));
  const A1x = xmin < hw/4 ? 0 : hw/2;
  const A1y = ymin < hh/4 ? 0 : hh/2;
  const largeur = Math.floor((gridW - A1x) / hw + 0.02) + 1;
  const hauteur = Math.round(gridH / hh);
  const m = new LosMap(hw, hh, A1x, A1y, largeur, hauteur, gridW, gridH, terrains);

  // Pixels de chaque board, recadrés puis retournés comme son image.
  const og = m.getTerrain('Open Ground');
  for(const b of reels){
    if(!donnees.get(b.name)) continue;
    const {d} = donnees.get(b.name), R = b._los;
    const cx = b.crop ? (b.crop.x || 0) : 0, cy = b.crop ? (b.crop.y || 0) : 0;
    for(let ly = 0; ly < R.h; ly++){
      const Y = R.y + ly;
      if(Y >= gridH) break;
      const sy = cy + (b.reversed ? R.h - 1 - ly : ly);
      for(let lx = 0; lx < R.w; lx++){
        const X = R.x + lx;
        if(X >= gridW) break;
        const sx = cx + (b.reversed ? R.w - 1 - lx : lx);
        const dedans = sx >= 0 && sy >= 0 && sx < d.gridWidth && sy < d.gridHeight;
        let t = dedans ? _losTerrain(d, sx, sy) : og.type;
        if(!m.terrainList[t]) t = og.type;
        m.setGridElevation(dedans ? _losNiveau(d, sx, sy) : 0, X, Y);
        // Demi-hexagones de raccord : le terrain du board voisin l'emporte
        // sur un terrain découvert.
        if(lx === 0 && R.x > 0){
          const a = m.getGridTerrain(R.x - 1, Y);
          if(a && !a.isOpen() && m.terrainList[t].isOpen()) t = a.type;
        }
        if(ly === 0 && R.y > 0){
          const a = m.getGridTerrain(X, R.y - 1);
          if(a && !a.isOpen() && m.terrainList[t].isOpen()) t = a.type;
        }
        m.setGridTerrainCode(t, X, Y);
      }
    }
    _ssrGrilles(m, R, b.ssrList || [], regles);
  }
  // Overlays une fois tous les boards posés (ASLMap.adjustLOSForOverlays) :
  // ce qui déborde sur un board voisin n'est plus recouvert par lui.
  for(const b of reels){
    if(!donnees.get(b.name)) continue;
    for(const ov of _overlaysLOS(b)){
      if(!b._couleurs) b._couleurs = lireCouleursLOS(terrainsXML, donnees.get(b.name).meta);
      new LosOverlay(m, b._couleurs, ov, b._los, b).passeGrilles();
    }
  }

  // Noms, escaliers et côtés spéciaux, board par board (le dernier posé
  // nomme les demi-hexagones de raccord, comme dans VASL).
  let rates = 0, total = 0;
  for(const b of reels){
    const {d, meta} = donnees.get(b.name) || {d: null, meta: ''}, g = geoBoard(b), R = b._los;
    const parNom = new Map();
    for(const h of _hexesBoardLOS(b, g, R, z)){
      // Demi-hexagones coupés par le recadrage : hors de la grille.
      if(h.x < R.x - 3 || h.x > R.x + R.w + 3 || h.y < R.y - 3 || h.y > R.y + R.h + 3) continue;
      const hex = _hexAuCentre(m, h.x, h.y);
      total++;
      if(!hex){ rates++; continue; }
      hex.resetHexAndLocationNames(h.nom);
      hex.board = b.name;
      parNom.set(h.nom.toUpperCase(), hex);
    }
    const lettres = g.x0 < 0 ? Math.round(-g.x0 / g.hw) : 0;
    const decalage = g.y0 < 0 ? Math.trunc(-g.y0 / g.hh) + 1 : 0;
    const egal = /EqualRowCount/.test((_elementsXML(meta, 'boardMetadata')[0] || {}).HexGridConfig || '');
    let k = d ? d.debutHex : 0;
    if(d) for(let col = 0; col < d.width; col++)
      for(let row = 0; row < d.height + (egal ? 0 : col % 2); row++){
        const v = d.octets[k++];
        const hex = parNom.get((hexrow(col + lettres) + (row + decalage + (col % 2 === 0 ? 1 : 0))).toUpperCase());
        if(hex && k <= d.octets.length) hex.setStairway(v === 1);
      }
    const inv = f => b.reversed ? [3, 4, 5, 0, 1, 2].map(i => f[i]) : f;
    for(const [n, f] of _cotesSpeciaux(meta, 'rrembankment')){ const h = parNom.get(n.toUpperCase()); if(h) h.rbrrembankments = inv(f); }
    for(const [n, f] of _cotesSpeciaux(meta, 'partialorchard')){ const h = parNom.get(n.toUpperCase()); if(h) h.partialorchards = inv(f); }
    for(const [n, f] of _cotesSpeciaux(meta, 'slope')){ const h = parNom.get(n.toUpperCase()); if(h) h.slopes = inv(f); }
  }
  if(rates > total / 10) throw new Error('grille d\'hexagones non alignée sur les boards');
  for(const c of m.hexGrid) for(const h of c) h.resetHexsideLocationNames();
  for(const b of reels)
    for(const ov of _overlaysLOS(b))
      if(b._couleurs) new LosOverlay(m, b._couleurs, ov, b._los, b).passeHexagones();
  m.resetHexTerrain();

  const avecHex = reels.filter(b => (b.ssrList || []).some(s => _SSR_GRILLE_HEX.has(s) || (regles.get(s) || {}).type === 'elevationToTerrainMap'));
  if(avecHex.length){
    for(const b of avecHex) _ssrHexagones(m, b._los, b.ssrList, regles);
    m.resetHexTerrain();
  }
  m.boards = boards;
  return m;
}
