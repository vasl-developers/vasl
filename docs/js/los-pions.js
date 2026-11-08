// ═══════════════════════════════════════════════════════════════
// PIONS DANS LA LIGNE DE VUE — VASLGameInterface et CounterMetadata.xml
// Fumée, épaves et véhicules, OBA, terrains de pion (décombres, vergers
// de niveau, brèches, haies, murs, congères…), retranchements, ponts et
// crêtes, lus à chaque fil comme le fait VASL au lancement du fil.
// ═══════════════════════════════════════════════════════════════
// CounterMetadata.xml : une entrée par nom de pion (le dernier l'emporte).
function lireMetaPions(texte){
  const sans = texte.replace(/<!--[\s\S]*?-->/g, '');
  const types = {smoke: 'SMOKE', terrain: 'TERRAIN', hexside: 'HEXSIDE', OBA: 'OBA', wreck: 'WRECK', ignore: 'IGNORE',
    bridge: 'BRIDGE', buildingLevel: 'BUILDING_LEVEL', roof: 'ROOF', entrenchment: 'ENTRENCHMENT', crest: 'CREST', climb: 'CLIMB'};
  const ent = v => v == null ? 0 : parseInt(v, 10) || 0;
  const meta = new Map();
  for(const m of sans.matchAll(/<(\w+)\b([^>]*)>/g)){
    if(m[1] === 'counterMetadata') continue;
    const a = _attrsXML(m[2]), type = types[m[1]] || null;
    meta.set(a.name, type && new LosPion(a.name, type, {
      terrain: type === 'ENTRENCHMENT' ? 'Foxholes' : a.terrain,
      height: ent(a.height), hindrance: ent(a.hindrance), level: ent(a.level), ca: ent(a.ca),
      hside: ent(a.hside), rotation: ent(a.rotation), isBarrage: a.isBarrage === 'true'
    }));
  }
  return meta;
}

// CounterMetadata (VASL.LOS.counters) : niveau 0 par défaut, comme l'int Java.
class LosPion {
  constructor(name, type, a){ this.name = name; this.type = type; Object.assign(this, a); }
  getName(){ return this.name; }
  getType(){ return this.type; }
  getTerrain(){ return this.terrain; }
  getHeight(){ return this.height; }
  getHindrance(){ return this.hindrance; }
  getLevel(){ return this.level; }
  getCoverArch(){ return this.ca - 1; }   // pions de 1 à 6, code de 0 à 5
  getRotation(){ return this.rotation; }
  getIsBarrage(){ return this.isBarrage; }
  getHexside(){ return this.hside; }
  setHexside(h){ this.hside = h; }
}
class LosFumee {
  constructor(name, location, height, hindrance){ this.name = name; this.location = location; this.height = height; this.hindrance = hindrance; }
  getName(){ return this.name; }
  getLocation(){ return this.location; }
  getHeight(){ return this.height; }
  getHindrance(){ return this.hindrance; }
}
class LosVehicule {
  constructor(name, location){ this.name = name; this.location = location; }
  getName(){ return this.name; }
  getLocation(){ return this.location; }
}
class LosOBA {
  constructor(name, hex, rotation, isBarrage){
    this.name = name; this.hex = hex; this.rotation = rotation; this.isBarrage = isBarrage;
    this.blastHeight = 2;
    this.blastAreaRadius = name.includes('Bombardment') ? (name.includes('Small') ? 2 : 3) : 1;
  }
  getName(){ return this.name; }
  getHex(){ return this.hex; }
  getRotation(){ return this.rotation; }
  getisBarrage(){ return this.isBarrage; }
  getBlastHeight(){ return this.blastHeight; }
  getBlastAreaRadius(){ return this.blastAreaRadius; }
}

// VASLGameInterface. Les pièces sont données par
// {nom, x, y (coordonnées LOS), hindrance, deplace, overlay, pile}.
class LosPions {
  constructor(map, meta){
    this.map = map; this.meta = meta;
    this.ignores = [...meta.values()].filter(c => c && c.type === 'IGNORE').map(c => c.name);
    this._origines = new Map();   // hex → lieux d'origine, avant les pions
    this.updatePieces([]);
  }

  // Nouvel instantané. VASL garde les lieux créés par les pions retirés
  // jusqu'au rechargement de la carte ; ici on repart de la carte.
  updatePieces(pieces){
    for(const [h, o] of this._origines){
      const c = h.getCenterLocation();
      c.setUpLocation(o.up); c.setDownLocation(o.down); h.setBridge(o.bridge);
    }
    this._origines.clear();
    this.smokeList = new Map(); this.terrainList = new Map(); this.hexsideList = new Map();
    this.OBAList = new Map(); this.vehicleList = new Map();
    this.pieces = pieces;
    for(const p of pieces){
      try { this.updatePiece(p); } catch(e){ /* pièce ignorée */ }
    }
    this.aDesCotes = this.hexsideList.size > 0;
  }

  static _sansEtiquette(n){
    for(;;){
      const a = n.indexOf('('), b = n.lastIndexOf(')');
      if(a === -1 || b === -1 || a > b + 1) return n;
      n = n.split(n.substring(a, b + 1)).join('');
    }
  }

  _ajouter(liste, objet, h){
    if(!liste.has(h)) liste.set(h, new Set());
    liste.get(h).add(objet);
  }

  _garder(h){
    if(this._origines.has(h)) return;
    const c = h.getCenterLocation();
    this._origines.set(h, {up: c.getUpLocation(), down: c.getDownLocation(), bridge: h.getBridge()});
  }

  updatePiece(piece){
    const m = this.map;
    if(/Control|Blank|Perimeter|perimeter|Fortified|Hex Grid/.test(piece.nom)) return;
    const x = piece.x, y = piece.y;
    if(!m.onMap(x, y) || m.gridToHex(x, y) == null) return;
    const h = m.gridToHex(x, y);
    const hexloc = h.getNearestLocation(x, y);
    const hexside = h.getLocationHexside(hexloc);
    let name = LosPions._sansEtiquette(piece.nom.trim()).trim();
    for(const i of this.ignores) if(name.startsWith(i)) return;
    let counter = this.meta.get(name) || null;
    if(counter == null){
      if(name.includes('Hedge Overlay')) name = 'Hedge Overlay';
      else if(name.includes('Wall Overlay')) name = 'Wall Overlay';
      else if(name.includes('Bocage Overlay')) name = 'Bocage Overlay';
      else if(name.includes('Road')) name = 'Road';
      else if(name.includes('Foot') || name.includes('Pontoon')) name = '';
      else if(name.includes('Bridge')) name = 'Bridge';
      else if(name.includes('Rowhouse Bar')) name = 'Rowhouse Bar Overlay';
      else if(name.includes('StoneBreach')) name = 'StoneBreach Rowhouse Overlay';
      else if(name.includes('Wood Breach')) name = 'Wood Breach Rowhouse Overlay';
      if(hexside !== -1) name += ' ' + hexside;
      counter = this.meta.get(name) || null;
    }
    if(counter != null){
      const cn = counter.getName();
      switch(counter.getType()){
        case 'OBA': {
          if(cn.includes('Har')) break;
          const oba = new LosOBA(cn, h, counter.getRotation(), counter.getIsBarrage());
          let hindrance = 0, height = 0;
          const fumee = sh => {
            if(cn.includes('+3')){ hindrance = 3; height = 2; }
            else if(cn.includes('+1')){ hindrance = 1; height = 4; }
            else { hindrance = 2; height = cn.includes('Sm') ? 2 : 4; }
            this._ajouter(this.smokeList, new LosFumee(cn, sh.getCenterLocation(), height, hindrance), sh);
          };
          if(cn.includes('Sm ') || cn.includes('WP')){
            if(oba.getisBarrage()){
              for(const n of m.getBarrageHexes(oba)){ const sh = m.getHexParNom(n); if(sh) fumee(sh); }
            } else {
              const ffe = m.getAllAdjacentHexes(oba.getHex());
              for(const n of ffe){ if(n.toLowerCase() === 'offboard') continue; const sh = m.getHexParNom(n); if(sh) fumee(sh); }
              if(cn.includes('Naval')){
                for(const nh of this.getExtraNobaHexes(counter.getRotation(), ffe))
                  if(nh) this._ajouter(this.smokeList, new LosFumee(cn, nh.getCenterLocation(), height, hindrance), nh);
              }
            }
          }
          if(cn.includes('Bombardment') && (cn.includes('Smoke') || cn.includes('Dispersed'))){
            height = 2;
            for(const n of m.getAllHexesInRadiusOf(oba.getHex(), cn.includes('Small') ? 2 : 3)){
              const sh = m.getHexParNom(n);
              if(sh) this._ajouter(this.smokeList, new LosFumee(cn, sh.getCenterLocation(), height, cn.includes('Dispersed') ? 2 : 3), sh);
            }
          } else this._ajouter(this.OBAList, oba, h);
          break;
        }
        case 'TERRAIN': this.terrainList.set(h, counter); break;
        case 'HEXSIDE': {
          // VASL partage l'objet entre tous les pions du même nom.
          const c = Object.assign(Object.create(LosPion.prototype), counter);
          c.setHexside(hexside);
          this.hexsideList.set(h, c);
          break;
        }
        case 'ENTRENCHMENT': this.terrainList.set(h, counter); this._lieuRetranchement(h); break;
        case 'BRIDGE': this.terrainList.set(h, counter); this._lieuPont(h); break;
        case 'CREST': this.terrainList.set(h, counter); this._lieuCrete(h); break;
        case 'SMOKE':
          this._ajouter(this.smokeList, new LosFumee(cn, h.getNearestLocation(x, y), counter.getHeight(), counter.getHindrance()), h);
          break;
        case 'WRECK': this._ajouter(this.vehicleList, new LosVehicule(name, h.getCenterLocation()), h); break;
      }
    } else if(piece.hindrance && !piece.deplace){
      // Toute pièce à gêne sans règle est un véhicule.
      this._ajouter(this.vehicleList, new LosVehicule(name, h.getNearestLocation(x, y)), h);
    }
  }

  _lieuRetranchement(h){
    this._garder(h);
    const c = h.getCenterLocation(), n = LosLocation.copie(c);
    n.setUpLocation(c);
    n.setTerrain(this.map.getTerrain('Foxholes'));
    c.setDownLocation(n);
  }
  _lieuPont(h){
    if(h.hasBridge()) return;
    this._garder(h);
    const c = h.getCenterLocation(), n = LosLocation.copie(c);
    n.setLevelInHex(h.getBridgeLocationAbsoluteLevel() - n.getHex().getBaseLevelofHex());
    n.setDownLocation(c);
    n.setTerrain(this.map.getTerrain('Stone Bridge'));
    c.setUpLocation(n);
    h.setBridge(new LosBridge(n.getTerrain(), n.getLevelInHex(), n, true, h.getHexCenter()));
  }
  _lieuCrete(h){
    this._garder(h);
    const c = h.getCenterLocation(), n = LosLocation.copie(c);
    n.setLevelInHex(h.getBridgeLocationAbsoluteLevel() - n.getHex().getBaseLevelofHex());
    n.setDownLocation(c);
    n.setTerrain(this.map.getTerrain('Crest'));
    c.setUpLocation(n);
  }

  getVehicles(h){ return this.vehicleList.get(h) || null; }
  getSmoke(h){ return this.smokeList.get(h) || null; }
  getTerrain(h){ const c = this.terrainList.get(h); return c ? c.getTerrain() : null; }
  getHexside(h){ return this.hexsideList.get(h) || null; }
  getLevel(h){
    const c = this.terrainList.get(h);
    if(c){
      if(c.getName().includes('Bridge')) return h.getBridgeLocationAbsoluteLevel();
      if(c.getLevel() !== -99) return c.getLevel();
    }
    return -99;
  }
  getOBA(){
    const r = new Set();
    for(const s of this.OBAList.values()) for(const o of s) r.add(o);
    return r;
  }

  // Deux hexagones de plus au bout de la zone d'un OBA naval.
  getExtraNobaHexes(rotation, ffe){
    const m = this.map, r = [null, null];
    const paires = {1: [[1, 1], [4, 4]], 2: [[2, 1], [5, 4]], 3: [[2, 2], [5, 5]],
      4: [[3, 2], [6, 5]], 5: [[3, 3], [6, 0]], 6: [[4, 3], [1, 0]]}[rotation];
    if(!paires) return r;
    paires.forEach(([i, cote], k) => {
      if(ffe[i].toLowerCase() === 'offboard') return;
      const a = m.getAdjacentHex(m.getHexParNom(ffe[i]), cote);
      if(a != null) r[k] = a;
    });
    return r;
  }

  // Map.getOverlayPiece : la première pièce posée dans l'hexagone — dans
  // une pile, la première qui a la propriété « overlay ».
  pieceOverlay(h){
    if(h == null) return null;
    for(const p of this.pieces){
      if(!h.containsExtended(p.x, p.y)) continue;
      if(!p.pile) return p;
      for(const q of p.pile) if(q.overlay) return q;
    }
    return null;
  }
}
