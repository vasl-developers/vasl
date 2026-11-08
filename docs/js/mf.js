// ═══ DÉCOMPTE DES MF / MP — coût du terrain de chaque hexagone (TEC) ═══
// Après chaque déplacement, le chat (et donc le log) reçoit, pour chaque
// unité, le coût de l'hexagone entré et le cumul de la phase :
//   « * 5-4-8 SSsq moves aA1 -> aA2 * 2MF/ 3MF »
// Le coût vient de la table des terrains de l'extension VASL Tools, lue
// sur la carte LOS : terrain du centre, dépression, côté franchi, niveau.
// Un saut de plusieurs hexagones suit le chemin le moins cher. Undo
// ramène le cumul à l'hexagone retrouvé ; Mark unmoved le remet à zéro.
//
// TEC_MVT, MP_VEHICULES : js/donnees/mouvement.js
const _COL_MVT = {inf: 1, cav: 2, hd: 3, mc: 4, ac: 5, ft: 6, ht: 7, tr: 8};
// vehicleType du module VASL → colonne du chart.
const _COL_VEH = {ft: 'ft', ft_a: 'ft', ht: 'ht', ac: 'ac', tr: 'tr', tr_a: 'tr', mc: 'mc', hd: 'hd'};

// Terrain de la carte LOS (SharedBoardMetadata.xml) → ligne du chart.
const _LIGNE_TEC = (() => {
  const t = {};
  const mettre = (ligne, ...noms) => noms.forEach(n => { t[n] = ligne; });
  mettre('ETO:Open Ground', 'Open Ground', 'Plowed Field', 'Snow', 'Deep Snow', 'Ice', 'Mud',
         'Path', 'Track', 'Rooftop', 'Pillbox', 'Sewer', 'Effluent', 'Crest');
  mettre('ETO:Road', 'Dirt Road', 'Paved Road');
  mettre('ETO:Road-Elevated', 'Elevated Road');
  mettre('ETO:Road-Sunken', 'Sunken Road');
  mettre('ETO:Runway', 'Runway');
  mettre('ETO:Railroads-GLRR', 'Railroad, Ground Level');
  mettre('ETO:Railroads-EmRR (D)', 'Railroad, Embankment');
  mettre('ETO:Railroads-ElRR (D O)', 'Railroad, Elevated');
  mettre('ETO:Railroads-SuRR (D S)', 'Railroad, Sunken');
  mettre('ETO:Brush (½)', 'Brush');
  mettre('ETO:Grain (½)', 'Grain', 'Light Grain');
  mettre('ETO:Orchard', 'Orchard', 'Orchard, Out of Season');
  mettre('ETO:Orchard-Dense', 'Dense Orchard');
  mettre('ETO:Olive Grove*', 'Olive Grove');
  mettre('ETO:Vineyard', 'Vineyard');
  mettre('ETO:Cactus Patch*', 'Cactus Patch');
  mettre('ETO:Woods', 'Woods', 'Forest');
  mettre('ETO:Woods-Pine', 'Pine Woods');
  mettre('ETO:Woods-Light', 'Light Woods');
  mettre('ETO:Marsh [Mudflat] (½)', 'Marsh', 'Mudflats');
  mettre('ETO:Graveyard', 'Graveyard');
  mettre('ETO:Crag (½)', 'Crags', 'BTCrags');
  mettre('ETO:Debris', 'Debris');
  mettre('ETO:Shellholes', 'Shellholes');
  mettre('ETO:Rubble', 'Stone Rubble', 'Wooden Rubble', 'Stone Rubble Ground Level', 'Stone Rubble Level 1',
         'Stone Rubble Level 2', 'Stone Rubble Level 3', 'Wooden Rubble Ground Level',
         'Wooden Rubble Level 1', 'Wooden Rubble Level 2', 'Wooden Rubble Level 3');
  mettre('ETO:Water Obstacle', 'Water', 'Shallow Water', 'River', 'Shallow River', 'Canal', 'Ford', 'Flooded Stream');
  mettre('ETO:Foxhole', 'Foxholes');
  mettre('ETO:Trench', 'Trench', 'Antitank Trench');
  mettre('ETO:Gully', 'Gully');
  mettre('ETO:Stream', 'Dry Stream', 'Shallow Stream', 'Deep Stream');
  mettre('ETO:Railroads-GLRR', 'Railroad, Ground Level');
  mettre('ETO:Railroads-EmRR (D)', 'Railroad, Embankment');
  mettre('ETO:Railroads-ElRR (D O)', 'Railroad, Elevated');
  mettre('ETO:Railroads-SuRR (D S)', 'Railroad, Sunken');
  mettre('ETO:Wall', 'Wall');
  mettre('ETO:Hedge', 'Hedge');
  mettre('ETO:Bocage', 'Bocage');
  mettre('ETO:Cactus Hedge', 'Cactus Hedge');
  mettre('ETO:Cliff', 'Cliff');
  mettre('ETO:Roadblock', 'Road Block');
  mettre('DTO:Hammada', 'Hammada');
  mettre('DTO:Scrub E', 'Scrub');
  mettre('DTO:Sand', 'Sand', 'Soft Sand');
  mettre('DTO:Broken Ground*', 'Broken Ground');
  mettre('DTO:Wadi', 'Wadi');
  mettre('DTO:Deir', 'Deir');
  mettre('DTO:Hillock', 'Hillock', 'Hillock Summit');
  mettre('DTO:Sangar', 'Sangar');
  mettre('DTO:Dune Crest', 'Dune, Crest Low', 'Dune, Crest High');
  mettre('PTO:Bamboo dmm', 'Bamboo');
  mettre('PTO:Dense Jungle dmn', 'Dense Jungle');
  mettre('PTO:Light Jungle d', 'Light Jungle');
  mettre('PTO:Kunai dn', 'Kunai');
  mettre('PTO:Palm Trees', 'Palm Trees');
  mettre('PTO:Swamp n', 'Swamp');
  mettre('PTO:Hut [Collapsed k]', 'Huts', 'Collapsed Huts');
  mettre('PTO:Cave', 'Cave');
  mettre('PTO:Panjis', 'Panji');
  mettre('PTO:Drained Paddy', 'Rice Paddy, Drained');
  mettre('PTO:In-Season Paddy E', 'Rice Paddy, In Season');
  mettre('PTO:Irrigated Paddy', 'Rice Paddy, Irrigated');
  mettre('PTO:OCEAN', 'Ocean', 'Shallow Ocean');
  mettre('PTO:Beach, Slight', 'Beach, Slight Slope');
  mettre('PTO:Beach, Moderate', 'Beach, Moderate Slope');
  mettre('PTO:Beach, Steep', 'Beach, Steep Slope');
  return t;
})();
const _ligneTerrain = c => !c ? 'ETO:Open Ground'
  : _LIGNE_TEC[c.getName()] || (c.isBuilding() ? (/Wooden/.test(c.getName()) ? 'ETO:Building-Wooden' : 'ETO:Building-Stone')
  : c.isOpen() ? 'ETO:Open Ground' : null);

// Ce qui bouge, et sa colonne du chart : l'infanterie (escouades, chefs,
// héros, équipages), ou un véhicule selon son vehicleType, avec ses MP
// imprimés (gpid) pour les coûts « Z », « z », « ¼ » et « ALL ».
function natureMouvement(p){
  if(p._natMvt && memeEtat(p._natMvt.etat, p)) return p._natMvt.n;
  let pr = {};
  try { pr = _proprietes(tsPion(p)) || {}; } catch(e){}
  const d = lirePiece(p);
  // Le nom de base d'un chef est souvent « geLDR » : son nom affiché
  // (« 9-1 ») vient d'un calque. Les deux sont essayés.
  const noms = [d.nomBase, d.nom, p.nom].map(x => (x || '').trim());
  const nom = noms[0];
  const te = tePion(p), S = te ? decoderTraits(te.etat) : [];
  const gpid = ((S[S.length - 1] || '').split(';')[3] || '').trim();
  // Pièce d'une sauvegarde faite avec un module plus ancien : son type de
  // véhicule, voire son type de pion, manque ; la définition actuelle du
  // même PieceSlot (gpid) les donne.
  if((!pr.vehicleType || !pr.CounterType) && gpid && _bdd && _bdd.parGpid){
    const x = _bdd.parGpid.get(gpid);
    if(x){
      if(!_bdd._typesPion) _bdd._typesPion = new Map();
      if(!_bdd._typesPion.has(gpid)){
        let v = {};
        try { v = _proprietes(tsSeg(x.cmd)) || {}; } catch(e){}
        _bdd._typesPion.set(gpid, {vehicleType: v.vehicleType || null, CounterType: v.CounterType || null});
      }
      const t = _bdd._typesPion.get(gpid);
      if(!pr.vehicleType && t.vehicleType) pr.vehicleType = t.vehicleType;
      if(!pr.CounterType && t.CounterType) pr.CounterType = t.CounterType;
    }
  }
  if(!pr.CounterType && pr.DBCounterType) pr.CounterType = pr.DBCounterType;
  let n = null;
  if(pr.vehicleType){
    // Boutonné : VASL l'écrit dans le nom courant (« … BU », sinon « CE »).
    n = {col: _COL_VEH[pr.vehicleType] || null, unite: 'MP', mp: MP_VEHICULES[gpid] || null,
         bu: /(^|\s)BU(\s|$)/.test(lirePiece(p).nom || '')};
  } else if(pr.CounterType === 'unit'){
    // Allocation de MF (A4.1) : 6 pour un chef ou un héros, 4 sinon ; elle
    // ne sert qu'à l'embarquement (D6.4).
    const chef = noms.some(x => /^\d{1,2}[-+]\d(\s|$)/.test(x) || /\bhero\b/i.test(x) || /^[a-z]{2}LDR/.test(x));
    if(chef) n = {col: 'inf', unite: 'MF', mp: null, mf: 6};
    else if(noms.some(x => /^\d+-\d+-\d+/.test(x))) n = {col: 'inf', unite: 'MF', mp: null, mf: 4};
  }
  p._natMvt = {etat: empreinteEtat(p), n};
  return n;
}

function _hexDe(m, x, y){
  const q = _versLOS({x, y});
  if(m.onMap(q.x, q.y)) return m.gridToHex(q.x, q.y);
  // Centre d'un demi-hexagone du bord : sur la limite de la carte LOS.
  const cx = Math.min(Math.max(q.x, 0), m.gridWidth - 1), cy = Math.min(Math.max(q.y, 0), m.gridHeight - 1);
  return Math.hypot(cx - q.x, cy - q.y) <= 2 ? m.gridToHex(cx, cy) : null;
}
function _coteVers(m, a, b){
  for(let s = 0; s < 6; s++) if(m.getAdjacentHex(a, s) === b) return s;
  return -1;
}

// Un coût : nombre, choix {a, b} (trous d'obus : 1 ou 2 MF au gré du
// joueur, B2.4), 'ALL', 'NA' ou null (inconnu).
const _estNb = x => typeof x === 'number' || !!(x && typeof x === 'object');
const _cMin = x => typeof x === 'number' ? x : x.a;
const _cMax = x => typeof x === 'number' ? x : x.b;
const _cFaire = (a, b) => a === b ? a : {a, b};
const _plus = (x, y) => _cFaire(_cMin(x) + _cMin(y), _cMax(x) + _cMax(y));
const _fois = (x, k) => _cFaire(_cMin(x) * k, _cMax(x) * k);

// Valeur d'une case du chart. ctx : {cot, mp, depense, ruisseau (0 sec,
// 1 peu profond, 2 profond), bu (véhicule boutonné)}. Les drapeaux (B, D,
// R, *…) ne changent pas le coût ; « R » (coût de route par un côté de
// route), « D » et « L » sont traités par l'appelant. « ALL » d'un
// véhicule : le reste de ses MP, dépense de la phase déduite (B13.41,
// D2.7) ; ctx.depense absent, toute l'allocation (chemin le moins cher).
function _valeurTEC(txt, ctx){
  // « [BU:1] » : la valeur pour un véhicule boutonné (route : ½, BU 1).
  const bu = /\[BU:\s*([^\]]+)\]/.exec(String(txt || ''));
  if(bu && ctx.bu) return _valeurTEC(bu[1], {cot: ctx.cot, mp: ctx.mp, depense: ctx.depense});
  let t = String(txt || '').replace(/\[[^\]]*\]|\{[^}]*\}/g, ' ').trim();
  if(!t) return null;
  // « 1 or 2* » : les deux options restent comptées.
  if(/\bor\b/.test(t)){
    const v = t.split(/\bor\b/).map(x => _valeurTEC(x.trim(), ctx));
    return v.every(_estNb) ? _cFaire(Math.min(...v.map(_cMin)), Math.max(...v.map(_cMax))) : v.find(_estNb) || null;
  }
  const parts = t.split('/').map(x => x.trim()).filter(Boolean);
  // Ruisseau : « *2/3/4 » pour sec, peu profond, profond.
  if(parts.length === 3 && parts.every(x => /^\*?\d/.test(x)) && ctx.ruisseau != null)
    return _valeurTEC(parts[ctx.ruisseau], ctx);
  if(parts.length > 1){
    // Alternatives (« ALL B*/Z ») : la moins chère qui s'évalue.
    const v = parts.map(x => _valeurTEC(x, ctx)).filter(x => x !== null && x !== 'NA');
    const n = v.filter(_estNb);
    return n.length ? n.reduce((x, y) => _cMin(y) < _cMin(x) ? y : x) : v[0] || _valeurTEC(parts[0], ctx);
  }
  const m = /^\*?\s*(NA|ALL|DOT|COT|CLIMB|[YMWT]|Z|z|¼|\d+½?|½)\*{0,3}\s*(\+\s*COT)?/.exec(t);
  if(!m) return null;
  const k = m[1], plus = m[2] ? ctx.cot : 0;
  let v;
  if(k === 'NA' || k === 'CLIMB') return 'NA';
  if(k === 'ALL' || k === 'Y' || k === 'M')                            // Y, M : mouvement minimum
    return !ctx.mp ? 'ALL' : ctx.depense != null ? Math.max(0, ctx.mp - ctx.depense) : ctx.mp;
  if(k === 'DOT' || k === 'COT') v = ctx.cot;
  else if(k === 'Z') v = ctx.mp ? ctx.mp / 2 : null;
  else if(k === 'z') v = ctx.mp ? ctx.mp / 3 : null;
  else if(k === '¼') v = ctx.mp ? ctx.mp / 4 : null;
  else if(k === 'W' || k === 'T') return null;
  else v = k === '½' ? 0.5 : parseFloat(k) + (k.endsWith('½') ? 0.5 : 0);
  if(!_estNb(v) || !_estNb(plus)) return null;
  return _plus(v, plus);
}
const _casesTEC = (ligne, col) => (TEC_MVT[ligne] || [])[_COL_MVT[col]];
const _parRouteTEC = c => /(^|\s)R(\s|$)/.test(c || '');
// Code « D » : pénalités d'épave / véhicule et de VCA doublées (motos
// exceptées) ; « L » : +1 MP pour un demi-niveau plus haut. « DOT » n'est
// pas un « D ». Notation du PTO : lettres accolées (« B*DR »).
const _codeTEC = (c, k) => String(c || '').replace(/\[[^\]]*\]/g, ' ').split(/[\s\/]+/)
  .some(t => /^[A-Za-z*]+$/.test(t) && !/^(DOT|COT|ALL|NA|CLIMB)$/.test(t) && t.replace(/\*/g, '').includes(k));

// Terrain de l'hexagone pour le coût. Dans une dépression, la carte LOS
// met la dépression au centre : l'autre terrain (bois d'un ravin-bois,
// 5Z8) se lit dans les pixels de l'hexagone, car les coûts se cumulent
// (B19.4). null : terrain ouvert. Mis en cache, le chemin le plus court
// interroge chaque hexagone plusieurs fois.
function _terrainMvt(h){
  const c = h.getCenterLocation().getTerrain();
  if(!c || !c.isDepression()) return c;
  if(!('_sousMvt' in h)) h._sousMvt = h._chercherPixel(t => !t.isDepression() && !t.isOpen()
    && !t.isHexsideTerrain() && !t.isBridge() && !!_ligneTerrain(t)) || null;
  return h._sousMvt;
}
// Hexagone de route pour le mouvement : route au centre, ou pont (B6).
function _routeMvt(h){
  const c = h.getCenterLocation().getTerrain();
  if(c && c.isRoad()) return true;
  if(!('_pontMvt' in h)) h._pontMvt = !!h.getBridgeTerrain();
  return h._pontMvt;
}
// Obstacle contournable : bois ou bâtiment (A4.3, D2.3) ; pas les
// décombres (D2.31), ni un ravin-bois (A4.3). Le centre ne suffit pas :
// un bois traversé par une route, ou un demi-hexagone du bord dont le
// centre tombe en terrain ouvert (20AA10), reste un bois.
const _estObstacle = t => !!t && (t.isBuilding() || t.getLOSCategory() === 'WOODS');
function _obstacleMvt(h){
  return _estObstacle(h.getCenterLocation().getTerrain()) || _estObstacle(_autreTerrainMvt(h));
}

// Terrain d'un hexagone traversé par une route ou un chemin. La carte LOS
// met au centre ce que touche le point central : la route de 5I4, le
// chemin (compté terrain ouvert) de 24W7, alors que ces hexagones sont des
// bois. Si le centre est ouvert, une route ou un chemin et qu'un autre
// terrain de la table couvre au moins le quart de l'hexagone, c'est lui
// qui compte pour le mouvement. Mis en cache.
function _autreTerrainMvt(h){
  if('_autreMvt' in h) return h._autreMvt;
  const c = h.getCenterLocation().getTerrain();
  h._autreMvt = null;
  if(c && (c.isOpen() || c.isRoad() || /Path|Track/.test(c.getName())) && !c.isDepression()){
    const compte = new Map();
    let total = 0;
    h._chercherPixel(t => {
      total++;
      if(t && !t.isOpen() && !t.isRoad() && !t.isHexsideTerrain() && !t.isDepression()
         && !t.isBridge() && !/Path|Track/.test(t.getName()) && _ligneTerrain(t))
        compte.set(t, (compte.get(t) || 0) + 1);
      return false;
    });
    let best = null, n = 0;
    for(const [t, k] of compte) if(k > n){ best = t; n = k; }
    if(best && n >= total / 4) h._autreMvt = best;
  }
  return h._autreMvt;
}
const _coteDe = (h, s) => h.getHexsideLocation(s) && h.getHexsideLocation(s).getTerrain();

// Demi-niveau d'un remblai de voie ferrée (code « L ») : la carte LOS de
// VASL le marque « Railroad, Embankment », isHalfLevelHeight.
const _demiNiveau = h => { const c = h.getCenterLocation().getTerrain();
                           return c && c.isHalfLevelHeight() && /Railroad, Embankment/.test(c.getName()) ? 0.5 : 0; };
// Coût d'entrée dans b depuis a, voisins : {cout, lib, hex, route, d}.
// n.depense : MP déjà dépensés dans la phase (« ALL » d'un véhicule).
// d : code « D » de l'hexagone entré (pénalité d'épave / véhicule doublée).
function coutEntree(m, a, b, n){
  const col = n.col, veh = n.unite === 'MP';
  if(!col) return {cout: null, lib: '?'};
  const s = _coteVers(m, a, b);
  const centre = _terrainMvt(b), autre = _autreTerrainMvt(b);
  const c = autre || centre;
  const ligne = _ligneTerrain(c);
  let lib = c ? c.getName() : 'Open Ground';
  // Côté de route (B3.4, B3.41), lu de part et d'autre. L'infanterie paie
  // la route OU l'autre terrain de l'hexagone, à son choix (A4.132) :
  // bois traversé par une route, 1 ou 2 MF. Un véhicule prend la route.
  const cs = s >= 0 ? _coteDe(a, s) : null, cb = s >= 0 ? _coteDe(b, (s + 3) % 6) : null;
  const parRoute = s >= 0 && ((cs && cs.isRoad()) || (cb && cb.isRoad()));
  const routeB = _routeMvt(b) || parRoute;
  // Hexagone de route entré par un côté sans route : terrain ouvert (B3.2),
  // le tarif de la route ne vaut qu'au franchissement d'un côté de route
  // (B3.41). Routes encaissées et surélevées : leurs propres lignes.
  const caseC = ligne && _casesTEC(ligne === 'ETO:Road' && !parRoute ? 'ETO:Open Ground' : ligne, col);
  const tarifRoute = parRoute && (routeB || _parRouteTEC(caseC));
  // Chemin (B13.6) : dans un bois ou des broussailles, entrer par un côté
  // de chemin coûte 1 MF (2 à cheval) au lieu de 2 (4) ; on reste dans le
  // bois.
  const parChemin = !tarifRoute && s >= 0 && ((cs && /Path/.test(cs.getName())) || (cb && /Path/.test(cb.getName())))
    && /^ETO:(Woods|Woods-Pine|Woods-Light|Brush \(½\))$/.test(ligne || '') && (col === 'inf' || col === 'cav');
  const autreCout = caseC != null ? _valeurTEC(caseC, {cot: 1, mp: n.mp, depense: n.depense}) : null;
  let cout = tarifRoute
    ? _valeurTEC(_casesTEC('ETO:Road', col), {cot: 1, mp: n.mp, bu: n.bu, depense: n.depense})
    : parChemin ? (col === 'inf' ? 1 : 2) : autreCout;
  if(tarifRoute && !veh && autre && _estNb(cout) && _estNb(autreCout) && _cMax(autreCout) > _cMax(cout))
    cout = _cFaire(_cMin(cout), _cMax(autreCout));
  if(parRoute && !(c && c.isRoad())) lib += routeB && b._pontMvt ? ' (pont)' : ' (route)';
  if(parChemin) lib += ' (chemin)';
  if(ligne === 'ETO:Road' && !parRoute) lib += ' (hors route)';
  const cotHex = cout;
  // Dépression : le ravin, le ruisseau ou le wadi coûte en plus du
  // terrain de l'hexagone (« +COT »). À pied : ravin 2 MF (B19.4),
  // ruisseau sec 2, peu profond 3, profond 4 (B20.4), plus le coût de
  // l'autre terrain s'il n'est pas ouvert (ravin-bois : 4 MF).
  // Sur un pont par la route, la dépression dessous ne coûte rien.
  const dep = b.isDepressionTerrain() ? b.getCenterLocation().getDepressionTerrain() : null;
  if(dep && !(parRoute && routeB)){
    const ld = _LIGNE_TEC[dep.getName()], cd = ld && _casesTEC(ld, col);
    if(cd != null){
      const nd = dep.getName();
      const ctx = {cot: _estNb(cout) ? cout : 1, mp: n.mp, depense: n.depense,
                   ruisseau: /Dry/.test(nd) ? 0 : /Shallow/.test(nd) ? 1 : /Deep/.test(nd) ? 2 : null};
      cout = _valeurTEC(cd, ctx);
      if(!veh && /^(ETO:Gully|ETO:Stream|DTO:Wadi)$/.test(ld) && _estNb(cout) && lib !== 'Open Ground') cout = _plus(cout, ctx.cot);
      lib = nd + (lib !== 'Open Ground' ? ', ' + lib : '');
    }
  }
  // Côté franchi : mur, haie, bocage… « 1+COT » inclut le coût de l'hexagone.
  if(s >= 0 && !parRoute){
    const r = _coteFranchi(a, b, s, col, n, cout);
    if(r){ cout = r.cout; lib += r.lib; }
  }
  // Changement d'élévation (B10.4, B10.51). Montée d'un niveau : h
  // (infanterie) coût du terrain doublé ; H (véhicules) +4 MP, +2 par la
  // route. Changement abrupt : chaque niveau intermédiaire coûte 2 MF / 4
  // MP en montée, 1 MF / 2 MP en descente ; le dernier niveau, le coût de
  // la montée d'un niveau, ou rien de plus en descente. Un ravin est au
  // niveau -1 : en sortir est une montée (B19.4). Un pont porte la route
  // au niveau du sol : pas de montée en le quittant par la route.
  const pont = parRoute && (a._pontMvt || b._pontMvt);
  const niv = pont ? 0 : b.getBaseLevelofHex() - a.getBaseLevelofHex();
  if(niv && _estNb(cout)){
    const sup = niv >= 1 ? (veh ? niv * (parRoute ? 2 : 4) : _plus(_estNb(cotHex) ? cotHex : 1, 2 * (niv - 1)))
              : niv <= -2 ? (veh ? 2 : 1) * (-niv - 1) : 0;
    if(niv >= 1 || sup) lib += `, ${niv > 0 ? '+' : ''}${niv} niveau${Math.abs(niv) > 1 ? 'x' : ''}`;
    cout = _plus(cout, sup);
  }
  // Code de la case : sur la route, celui de la route (un bois traversé par
  // la route n'impose pas son « D » au véhicule qui reste sur la route).
  const caseCode = tarifRoute && !/^ETO:Road/.test(ligne || '') ? _casesTEC('ETO:Road', col) : caseC;
  // Code « L » : +1 MP pour entrer un demi-niveau plus haut (remblai).
  if(veh && _codeTEC(caseCode, 'L') && _estNb(cout)
     && b.getBaseLevelofHex() + _demiNiveau(b) - a.getBaseLevelofHex() - _demiNiveau(a) === 0.5){
    cout = _plus(cout, 1); lib += ', +½ niveau (L)';
  }
  return {cout, lib, hex: b, route: tarifRoute, d: veh && col !== 'mc' && _codeTEC(caseCode, 'D')};
}
// Mur, haie, bocage sur le côté s de a (vers b) : nouveau coût, ou null.
function _coteFranchi(a, b, s, col, n, cout){
  const t = a.getHexsideTerrain(s) || b.getHexsideTerrain((s + 3) % 6);
  const lt = t && _LIGNE_TEC[t.getName()];
  let ct = lt && _casesTEC(lt, col);
  if(ct === 'W') ct = _casesTEC('ETO:Hedge', col);
  if(ct == null || ct === '') return null;
  const v = _valeurTEC(ct, {cot: _estNb(cout) ? cout : 1, mp: n.mp});
  if(v === null) return null;
  return {cout: _estNb(cout) || v === 'NA' || v === 'ALL' ? v : null, lib: ' + ' + t.getName()};
}

// Contournement de l'obstacle de x, entré depuis a (voisin). Infanterie
// (A4.31) : coût de l'autre terrain de l'hexagone, terrain ouvert en
// général, doublé en montée. Véhicule (D2.3, VBM) : le double de ce coût,
// montée comprise (4 MP + COT, × 2).
function coutContournement(m, a, x, n){
  const col = n.col, veh = n.unite === 'MP';
  if(!col) return {cout: null, lib: '?'};
  const og = _valeurTEC(_casesTEC('ETO:Open Ground', col), {cot: 1, mp: n.mp});
  if(!_estNb(og)) return {cout: og, lib: 'contournement de ' + x.getName()};
  let cout = og, lib = 'contournement de ' + x.getName();
  const s = _coteVers(m, a, x);
  if(s >= 0){ const r = _coteFranchi(a, x, s, col, n, cout); if(r){ cout = r.cout; lib += r.lib; } }
  const niv = x.getBaseLevelofHex() - a.getBaseLevelofHex();
  if(niv >= 1 && _estNb(cout)){ cout = _plus(cout, veh ? 4 * niv : _fois(og, niv)); lib += `, +${niv} niveau${niv > 1 ? 'x' : ''}`; }
  if(veh && _estNb(cout)){ cout = _fois(cout, 2); lib += ' (VBM)'; }
  return {cout, lib, hex: x};
}

// Chemin le moins cher de a à b (Dijkstra borné) : [{hex, cout, lib}].
function cheminMoinsCher(m, a, b, n){
  const poids = r => _estNb(r.cout) ? _cMin(r.cout) : r.cout === 'NA' ? Infinity : r.cout === 'ALL' ? 50 : 1;
  const dist = new Map([[a, 0]]), prec = new Map(), fait = new Set();
  const file = [a];
  while(file.length && fait.size < 3000){
    file.sort((x, y) => dist.get(x) - dist.get(y));
    const h = file.shift();
    if(fait.has(h)) continue;
    fait.add(h);
    if(h === b || dist.get(h) > 80) break;
    for(let s = 0; s < 6; s++){
      const v = m.getAdjacentHex(h, s);
      if(!v || fait.has(v)) continue;
      const r = coutEntree(m, h, v, n), w = poids(r);
      if(!isFinite(w)) continue;
      const d = dist.get(h) + w;
      if(!dist.has(v) || d < dist.get(v)){ dist.set(v, d); prec.set(v, {h, r}); file.push(v); }
    }
  }
  if(!prec.has(b)) return null;
  const out = [];
  for(let v = b; v !== a; v = prec.get(v).h) out.unshift(Object.assign({hex: v}, prec.get(v).r));
  return out;
}

// Position de mouvement : centre d'un hexagone, ou côté (contournement,
// commandes « Hexside » de VASL) avec ses deux hexagones, lus sur la
// carte LOS (vaut aussi pour les boards deluxe). cle : l'ancrage
// (« 5Z8 », « 5Z8/NE »), qui sert à retrouver l'étape après un Undo.
function _posMvt(m, x, y){
  const a = ancragePlusProche(x, y);
  const cle = a ? a.label : Math.round(x) + ',' + Math.round(y);
  const hex = _hexDe(m, x, y);
  if(!a || !/\//.test(a.label) || !hex) return {cle, hex, cote: null};
  const q = _versLOS({x, y});
  const s = hex.getLocationHexside(hex.getNearestLocation(q.x, q.y));
  const autre = s >= 0 ? m.getAdjacentHex(hex, s) : null;
  return {cle, hex, cote: autre ? [hex, autre] : null};
}
// Nom d'un hexagone avec son board : « K5 » existe sur chaque board.
const _nomLOS = h => (h.board || '') + h.getName();
function _hexParNomLOS(m, nom){
  if(!m._parNomMvt) m._parNomMvt = new Map(m.hexGrid.flat().filter(Boolean).map(h => [_nomLOS(h), h]));
  return m._parNomMvt.get(nom) || null;
}
const _voisins = (m, a, b) => _coteVers(m, a, b) >= 0;

// Étapes d'un déplacement de P à Q : [{cout, lib, byp, hex}] — byp : nom
// de l'hexagone contourné à l'arrivée ; hex : hexagone entré (pénalité
// d'épaves et de véhicules). prec : les étapes déjà faites.
function etapesDeplacement(m, P, Q, n, prec){
  const bypPrec = prec[prec.length - 1].byp;
  // Un côté qui ne touche aucun obstacle n'est pas un contournement : le
  // pion, lâché là, reste dans l'hexagone d'où il vient si le côté est le
  // sien, sinon il est dans celui où tombe son centre.
  const libre = c => c && !c.some(_obstacleMvt);
  const dernier = [...prec].reverse().find(e => e.hex);
  if(libre(P.cote) && !(bypPrec && P.cote.some(h => _nomLOS(h) === bypPrec)))
    P = Object.assign({}, P, {cote: null, hex: dernier && P.cote.includes(dernier.hex) ? dernier.hex : P.hex});
  if(libre(Q.cote)) Q = Object.assign({}, Q, {cote: null, hex: P.hex && Q.cote.includes(P.hex) ? P.hex : Q.hex});
  const chemin = (a, b) => {
    if(a === b) return [];
    const ch = _voisins(m, a, b) ? [coutEntree(m, a, b, n)] : cheminMoinsCher(m, a, b, n);
    return ch || [{cout: null, lib: 'aucun chemin'}];
  };
  // Hexagone contourné au départ : celui de l'étape précédente.
  const X0 = P.cote ? (bypPrec && _hexParNomLOS(m, bypPrec)) || P.cote.find(_obstacleMvt) || P.hex : null;
  if(Q.cote && !P.hex){
    // Entrée depuis le hors-carte directement en contournement.
    const X = Q.cote.find(_obstacleMvt);
    return X ? [Object.assign(coutContournement(m, X, X, n), {byp: _nomLOS(X)})] : [coutEntree(m, Q.hex, Q.hex, n)];
  }
  if(Q.cote){
    const obs = Q.cote.filter(_obstacleMvt);
    // L'étape garde tous les obstacles de son côté (byps) : un côté commun
    // à deux obstacles (20AA10|BB9) contourne l'un et l'autre, et le
    // contournement peut se poursuivre le long de chacun.
    const marquer = res => { if(res.length) res[res.length - 1].byps = obs.map(_nomLOS); return res; };
    const longe = (e, nom) => e.byp === nom || (e.byps || []).includes(nom);
    const der = prec[prec.length - 1];
    const Xs = X0 && obs.includes(X0) ? X0 : P.cote && obs.find(h => longe(der, _nomLOS(h)));
    // Suite du contournement le long du même obstacle. Infanterie
    // (A4.31) : un ou deux côtés contigus coûtent le terrain ouvert une
    // fois ; au-delà de deux, le coût de l'hexagone double — le troisième
    // côté coûte donc une fois de plus, les suivants rien. Véhicule
    // (D2.33) : 1 MP de VCA et 2 MP de VBM par côté.
    if(Xs){
      const veh = n.unite === 'MP';
      const og = _valeurTEC(_casesTEC('ETO:Open Ground', n.col), {cot: 1, mp: n.mp});
      const nom = _nomLOS(Xs);
      let k = 0;
      for(let i = prec.length - 1; i > 0 && longe(prec[i], nom); i--) if(prec[i].pos !== '·') k++;
      const rang = k + 1;
      if(veh) return marquer([{cout: _estNb(og) ? _plus(1, _fois(og, 2)) : og,
                       lib: 'suite du contournement de ' + Xs.getName() + ' (VCA + VBM)', byp: nom}]);
      return marquer([{cout: rang === 3 ? og : 0, byp: nom,
               lib: `contournement de ${Xs.getName()}, côté ${rang}` + (rang === 3 ? ' : coût doublé (A4.31)' : '')}]);
    }
    const A = X0 || P.hex;
    const X = obs.find(h => h !== A) || obs[0];
    if(!X || !A || X === A) return [{cout: null, lib: 'côté d\'hexagone', byp: X ? _nomLOS(X) : null}];
    // Contournement de X, en passant au besoin par un voisin de X.
    const voisin = _voisins(m, A, X) ? A
      : [0, 1, 2, 3, 4, 5].map(s => m.getAdjacentHex(X, s)).filter(Boolean)
          .sort((u, v) => Math.hypot(u.getHexCenter().x - A.getHexCenter().x, u.getHexCenter().y - A.getHexCenter().y)
                        - Math.hypot(v.getHexCenter().x - A.getHexCenter().x, v.getHexCenter().y - A.getHexCenter().y))[0];
    return marquer([...chemin(A, voisin), Object.assign(coutContournement(m, voisin, X, n), {byp: _nomLOS(X)})]);
  }
  if(!Q.hex) return [];
  // Entrée depuis le hors-carte (zone de mise en place, renforts) : coût
  // du premier hexagone, sans côté ni montée.
  if(!P.hex) return [coutEntree(m, Q.hex, Q.hex, n)];
  if(X0){
    // Quitter le contournement : vers un voisin de l'obstacle, coût
    // d'entrée normal ; dans l'obstacle lui-même, son plein coût (A4.3).
    if(Q.hex === X0){
      const autre = P.cote.find(h => h !== X0) || X0;
      const r = coutEntree(m, autre, X0, n);
      return [{cout: r.cout, lib: 'entrée dans ' + r.lib}];   // déjà dans l'hexagone : pas de pénalité D2.14
    }
    return chemin(X0, Q.hex);
  }
  return chemin(P.hex, Q.hex);
}

// Orientation (VCA) d'un véhicule : le trait rotate à 6 facettes « CA cw ».
function _vcaTraits(T, S){
  const i = T.findIndex(t => /^rotate;6;/.test(t) && /CA cw/.test(t));
  return i < 0 ? null : (parseInt(S[i], 10) || 0);
}
function _vcaPion(p){
  const te = tePion(p);
  return te ? _vcaTraits(decoderTraits(te.type), decoderTraits(te.etat)) : null;
}

// Le chemin de la phase : étapes {pos, vca, cout, lib, byp}, la première
// sans coût. Undo ramène à la dernière étape de même position et VCA.
function _mvtTronquer(p, pos, vca){
  if(!p.mvt) return;
  const e = p.mvt.etapes;
  let i = e.length - 1;
  while(i >= 0 && (e[i].depart || !(e[i].pos === pos && (vca == null || e[i].vca === vca)))) i--;
  if(i < 0){ delete p.mvt; return; }
  e.length = i + 1;
}
function _mvtDepart(p, pos, vca){
  const e = p.mvt && p.mvt.etapes;
  if(!e || e[e.length - 1].pos !== pos || e[e.length - 1].vca !== vca) _mvtTronquer(p, pos, vca);
  if(!p.mvt) p.mvt = {etapes: [{pos, vca, etat: 'arret', recul: false}]};
  return p.mvt.etapes;
}
// État courant d'un véhicule : 'arret' ou 'marche', et marche arrière.
const _etatMvt = e => e[e.length - 1];
const _fmtMF = v => (Math.round(v * 2) / 2).toString().replace(/\.5$/, '½').replace(/^0½$/, '½');
// Un coût : nombre, choix « 1 ou 2 », « ALL » (tous les MF/MP), « NA » (interdit) ou « ? ».
const _fmtCout = c => _estNb(c) ? (typeof c === 'number' ? _fmtMF(c) : `${_fmtMF(c.a)} ou ${_fmtMF(c.b)}`)
  : c === 'ALL' || c === 'NA' ? c : '?';
function _mvtCumul(p){
  const c = p.mvt ? p.mvt.etapes.slice(1).map(e => e.cout) : [];
  if(c.includes('ALL')) return 'ALL';
  const n = c.filter(_estNb).reduce(_plus, 0);
  return _fmtCout(n) + (c.some(x => !_estNb(x)) ? '+?' : '');
}

// Marqueur Motion dans la pile d'un pion (D2.4) : ni démarrage, ni arrêt.
function _enMotion(p, x, y, groupe){
  const ici = vivants().filter(q => q.x === x && q.y === y);
  return [...ici, ...(groupe || [])].some(q => /^Motion$/i.test((q.nom || '').trim()));
}
// Véhicules et épaves dans un hexagone (D2.14), hors du groupe qui bouge
// et hors motos.
function _vehiculesDans(m, h, groupe){
  let k = 0;
  for(const q of vivants()){
    if(groupe.includes(q) || q.cache) continue;
    const nom = (q.nom || '').trim();
    const n = natureMouvement(q);
    const veh = n && n.unite === 'MP' && n.col !== 'mc';
    const epave = /\bWreck\b/i.test(nom) && !/^Burnt/i.test(nom);
    if(!veh && !epave) continue;
    const P = _posMvt(m, q.x, q.y);
    if(!P.cote && P.hex === h) k++;
  }
  return k;
}
// Pions Smoke de la carte, lus comme le fait la ligne de vue (Smoke,
// Dispersed Smoke, WP, grenades, Smoke d'OBA, avec leur Location et leur
// hauteur) : rend (h, niveau) → la Location du niveau de h en
// contient-elle ? CounterMetadata.xml range aussi Blaze et Veh Dust parmi
// les SMOKE : ce n'est pas de la Smoke pour le mouvement.
const _estFumee = nom => /Smoke|WP|\bSm\b/.test(nom || '') && !/Blaze|Dust/.test(nom || '');
function _fumees(m){
  if(!m || !m.pions) return () => false;
  m.pions.updatePieces(_piecesLOS());
  return (h, niveau) => {
    const f = m.pions.getSmoke(h);
    if(!f || !f.size) return false;
    let l = h.getCenterLocation();
    for(let k = 0; k < niveau && l && l.getUpLocation(); k++) l = l.getUpLocation();
    return [...f].some(s => _estFumee(s.getName()) && LosMap.locationInSmoke(l, s));
  };
}
// Équivalent squad d'une unité (A5.1) : squad 1, HS ou équipage ½, SMC
// rien. Les traits de VASL ne le disent pas de façon sûre (iCVP 2 pour un
// équipage, aucun CounterType pour certains HS) ; l'image, oui : les
// faces s'appellent <nation><FP><portée><moral>S|H|C (« ge548S »,
// « ja238H », « ru228C »), les faces brisées <nation>s|h|c<moral>b
// (« ges9b »).
function _equivSquad(q){
  const img = (lirePiece(q).img || '').split('/').pop();
  const m = /^[a-z]{2}\d{3}([SHC])/.exec(img) || /^[a-z]{2}([shc])\d/.exec(img);
  return !m ? 0 : m[1].toLowerCase() === 's' ? 1 : 0.5;
}
// Camp d'un pion : la nation, dossier de son image (« ge/ », « ru/ »).
const _nationPion = q => ((lirePiece(q).img || '').match(/^([a-z]{2})\//) || [])[1] || null;
// Surempilement de la Location de p (A5.11, A5.1) : équivalents squad du
// même camp au même niveau au-delà de trois, arrondi au-dessus ; les
// passagers d'un véhicule ne comptent pas (A5.3).
function _surempilement(p){
  const nat = _nationPion(p), niv = lieuDansPile(p).niveau || 0;
  if(!nat) return 0;
  let t = 0;
  for(const q of pile(cle(p))){
    if(q.deleted || _nationPion(q) !== nat || _estPassager(q)) continue;
    const k = _equivSquad(q);
    if(k && (lieuDansPile(q).niveau || 0) === niv) t += k;
  }
  return Math.ceil(Math.max(0, t - 3));
}
// Premier étage en décombres de l'hexagone h (B24.11), ou null : pion de
// décombres posé sur un compteur d'étage de la pile s de l'hexagone, ou
// terrain « Rubble Level n » de la carte LOS. Le pion de décombres ne
// s'empile pas : il suffit qu'il soit dans l'hexagone ; il marque le plus
// haut compteur d'étage de la pile, les niveaux au-dessus des décombres
// en étant aussi. Sans compteur d'étage, l'hexagone entier est en
// décombres : ce n'est plus un bâtiment (B24.1).
function _niveauDecombres(h, s){
  let r = null;
  const garder = k => { if(k > 0 && (r === null || k < r)) r = k; };
  const m = _losPrete;
  if(m && h && vivants().some(q => /Rubble/i.test(lirePiece(q).nomBase || q.nom || '') && _hexDe(m, q.x, q.y) === h)){
    const niv = (s || []).map(_niveauCompteur).filter(v => typeof v === 'number');
    if(niv.length) garder(Math.max(...niv));
  }
  let k = 1;
  for(let l = h && h.getCenterLocation().getUpLocation(); l; l = l.getUpLocation(), k++){
    const t = l.getTerrain();
    if(t && /Rubble Level/.test(t.getName())){ garder(k); break; }
  }
  return r;
}
// Deux hexagones voisins du même bâtiment : bâtiment des deux côtés, et
// le côté qui les sépare en est aussi.
function _batimentRelie(m, a, b){
  const s = _coteVers(m, a, b);
  const ca = a.getCenterLocation().getTerrain(), cb = b.getCenterLocation().getTerrain();
  if(s < 0 || !ca || !cb || !ca.isBuilding() || !cb.isBuilding()) return false;
  const t1 = _coteDe(a, s), t2 = _coteDe(b, (s + 3) % 6);
  return !!((t1 && t1.isBuilding()) || (t2 && t2.isBuilding()));
}
// Démarrage (D2.12) : 1 MP avant le premier hexagone ou le premier VCA
// de la phase, sauf sous un marqueur Motion. Étape marquée « depart » :
// Undo la retire avec le mouvement qui l'a provoquée.
function _demarrer(p, e, pos, vca, motion){
  const d = _etatMvt(e);
  if(d.etat === 'marche') return 0;
  e.push({pos, vca, cout: motion ? 0 : 1, lib: 'démarrage', depart: true, byp: d.byp, etat: 'marche', recul: !!d.recul});
  return motion ? 0 : 1;
}
// Marche arrière (D2.21) : coût d'entrée multiplié, ×4 pour un véhicule à
// chenilles, ×3 pour un camion, ×2 pour une auto blindée. Le multiplicateur
// propre à un pion (« REV × # » au dos) n'est pas connu. Motos et
// attelages : pas de marche arrière.
const _MULT_RECUL = {ft: 4, ht: 4, tr: 3, ac: 2};
// Décompte : coût puis cumul, « 1MP/ 3MP (démarrage 1) », « 2MF/ 4MF ».
function _texteCout(p, cout, unite, plus){
  plus = (plus || []).filter(Boolean);
  return `${cout}${unite}/ ${_mvtCumul(p)}${unite}` + (plus.length ? ` (${plus.join(', ')})` : '');
}
const _texteMP = (p, cout, plus) => _texteCout(p, cout, 'MP', plus);
const _texteMF = (p, cout, plus) => _texteCout(p, cout, 'MF', plus);
const _mvtSomme = p => p.mvt.etapes.slice(1).map(e => e.cout).filter(_estNb).reduce(_plus, 0);

// Après deplacer() : coût de l'hexagone entré et cumul, au chat et au log.
// Rend le texte du décompte (« 2MF/ 3MF »), que l'appelant met au
// bout de la ligne du déplacement, et les alertes, pour le chat local
// seulement. null si la carte LOS n'est pas prête.
function compterMouvement(avant, portes, local){
  const m = _losPrete;
  // Carte LOS pas encore construite : le décompte attend qu'elle le soit,
  // puis paraît sur une ligne à part. Sans données de terrain (board
  // inconnu), rien n'est compté. Pendant une relecture, les positions
  // auront changé : le coup n'est pas compté.
  if(!m){
    if(!local) carteLOS().then(r => {
      if(!_losPrete) return;
      const d = compterMouvement(avant, portes);
      if(d && d.texte) rapport(`${_nomsDeplaces(avant)} : ${d.texte}`);
      if(d) for(const t of d.alertes) chatLog('⚠️ ' + t);
    });
    return null;
  }
  portes = portes || new Set();
  const groupe = avant.map(a => a.p);
  const enFumee = _fumees(m);
  const lignes = new Map();          // même texte → unités regroupées
  const pied = [];                   // infanterie : une ligne pour la pile
  const alertes = [], segments = [];
  for(const a of avant){
    const p = a.p, n = !p.deleted && natureMouvement(p);
    if(!n) continue;
    const P = _posMvt(m, a.x, a.y), Q = _posMvt(m, p.x, p.y);
    if(P.cle === Q.cle || !Q.hex) continue;
    // Passager ou Rider : son véhicule paie, lui ne dépense rien (D6.3).
    // L'étape garde sa position pour la suite du décompte.
    if(portes.has(p)){
      const e = _mvtDepart(p, P.cle, null);
      e.push({pos: Q.cle, vca: null, cout: 0, lib: 'transporté', etat: 'arret'});
      continue;
    }
    const veh = n.unite === 'MP';
    const vca = _vcaPion(p);
    const e = _mvtDepart(p, P.cle, vca);
    const ch = etapesDeplacement(m, P, Q, n, e);
    // Replacé dans le même hexagone (sur un côté sans obstacle, ou en
    // quittant un tel côté) : rien à payer, mais l'étape garde la position,
    // sans quoi le coup suivant passerait pour un Undo et perdrait le cumul.
    if(!ch.length){
      if(P.hex){
        e.push({pos: Q.cle, vca, cout: 0, lib: "dans l'hexagone", etat: _etatMvt(e).etat, recul: _etatMvt(e).recul});
        if(veh){ const t = _texteMP(p, 0); if(!lignes.has(t)) lignes.set(t, []); lignes.get(t).push(nomRapport(p)); }
        else pied.push({p, pas: '0'});
      }
      continue;
    }
    // Entrée depuis le hors-carte : en Motion, sans démarrage (D2.12, A2.52).
    const demarrage = veh ? _demarrer(p, e, P.cle, vca, !P.hex || _enMotion(p, a.x, a.y, groupe)) : 0;
    // Épaves et véhicules dans l'hexagone entré : +1 MP chacun, +2 au
    // tarif de la route (D2.14), doublé encore par le code « D » de
    // l'hexagone (route encaissée, bois : B4.42, B13.41). Une moto n'en
    // paie pas.
    if(veh && n.col !== 'mc') for(const r of ch){
      const k = r.hex ? _vehiculesDans(m, r.hex, groupe) : 0;
      if(!k || !_estNb(r.cout)) continue;
      r.cout = _plus(r.cout, k * (r.route ? 2 : 1) * (r.d ? 2 : 1));
      r.lib += ` + ${k} véhicule(s)/épave(s)` + (r.d ? ' (D)' : '');
    }
    // « ALL » d'un véhicule : le reste de ses MP, d'après ce que la phase
    // a déjà coûté, démarrage et hexagones précédents compris (B13.41).
    if(veh && n.mp){
      let dep = _cMax(_mvtSomme(p));
      ch.forEach((r, i) => {
        const A = i ? ch[i - 1].hex : P.cote ? null : P.hex || r.hex;
        if(r.hex && A && 'route' in r && (A === r.hex || _voisins(m, A, r.hex))){
          const r2 = coutEntree(m, A, r.hex, Object.assign({}, n, {depense: dep}));
          if(_estNb(r2.cout) && _estNb(r.cout) && _cMax(r2.cout) < _cMax(coutEntree(m, A, r.hex, n).cout)){
            r.cout = r2.cout; r.all = true; r.lib += ' (ALL : reste des MP)';
          }
        }
        if(_estNb(r.cout)) dep += _cMax(r.cout);
      });
    }
    const d = _etatMvt(e), recul = veh && d.recul && _MULT_RECUL[n.col];
    if(recul) for(const r of ch) if(_estNb(r.cout) && !r.all){ r.cout = _fois(r.cout, recul); r.lib += ` (marche arrière ×${recul})`; }
    // Surempilement (A5.11) à l'arrivée : +1 MF (MP) par équivalent squad
    // au-delà de trois, l'unité qui entre comprise.
    const der = ch[ch.length - 1], sur = Q.hex ? _surempilement(p) : 0;
    if(sur && _estNb(der.cout) && !der.all){ der.cout = _plus(der.cout, sur); der.lib += ` + surempilement ${sur}`; }
    // Étage en décombres (B24.2) : le rez-de-chaussée est couvert de
    // décombres, 3 MF, sauf depuis un hexagone du même bâtiment (2 MF).
    if(!veh && Q.hex && 'route' in der){
      const A = ch.length > 1 ? ch[ch.length - 2].hex : P.cote ? null : P.hex;
      const rd = A && A !== Q.hex && _voisins(m, A, Q.hex) && _niveauDecombres(Q.hex, pile(cle(p)));
      if(rd){
        const relie = _batimentRelie(m, A, Q.hex);
        const bois = /Wooden/.test(Q.hex.getCenterLocation().getTerrain().getName());
        const base = _valeurTEC(_casesTEC(!relie ? 'ETO:Rubble' : bois ? 'ETO:Building-Wooden' : 'ETO:Building-Stone', 'inf'), {cot: 1});
        if(_estNb(base)){
          der.cout = _plus(base, sur);
          der.lib = (relie ? 'bâtiment relié' : 'décombres') + ` (étage ${rd} en décombres, B24.2)` + (sur ? ` + surempilement ${sur}` : '');
        }
      }
    }
    // Smoke (A24.7) : +1 MF / MP par Location entrée qui en contient,
    // Dispersed Smoke comprise ; au rez-de-chaussée, où arrive l'unité.
    for(const r of ch) if(r.hex && !r.all && _estNb(r.cout) && enFumee(r.hex, 0)){ r.cout = _plus(r.cout, 1); r.lib += ' + Smoke'; }
    ch.forEach((r, i) => e.push(Object.assign({pos: i === ch.length - 1 ? Q.cle : '·', vca,
      etat: d.etat, recul: d.recul}, r)));
    // Infanterie : sortie d'une fortification, étage d'arrivée (B23.4,
    // B27.4). L'étape d'entrée garde de quoi se recalculer si l'unité est
    // aussitôt reclassée dans la pile (sous un foxhole, à un autre étage).
    let entree = null;
    if(!veh && a.lieu){
      const r = ch[ch.length - 1], arr = lieuDansPile(p);
      entree = {base: r.cout, lieuDep: a.lieu, lieuArr: arr, A: P.hex, B: Q.hex, p, arrivee: true};
      const c = _coutEntreeLieu(m, entree);
      delete entree.arrivee;
      if(c.cout !== r.cout){ r.cout = c.cout; r.lib += c.lib; }
      if(c.alerte) alertes.push(`${nomRapport(p)} : ${c.alerte}`);
    }
    ch.forEach((r, i) => Object.assign(e[e.length - ch.length + i], {cout: r.cout, lib: r.lib}));
    if(entree) Object.assign(e[e.length - 1], {entree});
    const pas = ch.map(r => _fmtCout(r.cout)).join('+');
    if(!veh){ pied.push({p, pas}); continue; }
    const txt = _texteMP(p, pas, [recul && `marche arrière ×${recul}`, demarrage && 'démarrage 1']);
    if(!lignes.has(txt)) lignes.set(txt, []);
    lignes.get(txt).push(nomRapport(p));
  }
  // Infanterie : la pile qui bouge est comptée d'un bloc, sans le détail
  // des unités. Son cumul est celui de l'unité qui a le plus dépensé
  // (une unité ralentit la pile). Une unité seule garde son nom.
  if(pied.length){
    const cle = x => { const c = x.p.mvt.etapes.slice(1).map(e => e.cout);
                       return c.includes('ALL') ? Infinity : _cMax(_mvtSomme(x.p)); };
    const max = pied.reduce((a, x) => cle(x) > cle(a) ? x : a);
    const pas = [...new Set(pied.map(x => x.pas))].join(' / ');
    segments.push({qui: pied.length > 1 ? 'Pile' : nomRapport(max.p), txt: `${pas}MF/ ${_mvtCumul(max.p)}MF`});
  }
  for(const [txt, noms] of lignes) segments.push({qui: noms.join(', '), txt});
  // Un seul groupe : le chiffre seul ; sinon chacun nommé.
  const texte = segments.length === 1 ? segments[0].txt : segments.map(x => `${x.qui} : ${x.txt}`).join(' ; ');
  return {texte, alertes};
}
const _nomsDeplaces = avant => avant.filter(a => !a.p.cache).map(a => nomRapport(a.p)).join(', ');
// Relecture : le décompte complète la ligne « … moves … * » du coup, déjà
// affichée ; à défaut, une ligne à part. Rien n'entre dans le log.
function _completerLigneMouvement(texte, avant){
  const j = document.getElementById('journal');
  const l = j && [...j.querySelectorAll('.rap')].reverse().find(x => / moves /.test(x.textContent));
  // Ligne écrite ici avec son décompte (« … * 2MF/ 4MF ») : elle le
  // porte déjà, il n'est pas répété. S'il diffère du décompte recalculé
  // (log écrit par une version qui comptait mal), l'écran montre le bon ;
  // le fichier, lui, garde son texte.
  if(l && !l.dataset.mvt && /M[FP]\/ /.test(l.textContent)){
    l.dataset.mvt = '1';
    // Le décompte est dans le dernier nœud de texte, après les liens des
    // hexagones : « … <span>20BB10</span> * 2MF/ 4MF ».
    const x = l.lastChild, i = x && x.nodeType === 3 ? x.data.lastIndexOf(' * ') : -1;
    if(i >= 0 && x.data.slice(i + 3).trim() !== texte) x.data = x.data.slice(0, i + 3) + texte;
    return;
  }
  if(l && !l.dataset.mvt){
    l.dataset.mvt = '1';
    l.appendChild(document.createTextNode(' ' + texte));
    if(typeof _majApercuChat === 'function' && typeof _profil !== 'undefined' && _telLike()) _majApercuChat();
  } else afficherChat(`* ${_nomsDeplaces(avant)} : ${texte}`);
}

// Changement d'orientation d'un véhicule (D2.11) : 1 MP par hexspine, 2
// dans un bâtiment, des bois ou des décombres (pas en contournement).
// Appelé par executerTouche quand l'état du pion change.
function compterVCA(p, T, S0, S1){
  const n = natureMouvement(p);
  if(!n || n.unite !== 'MP') return;
  const a = _vcaTraits(T, S0), b = _vcaTraits(T, S1);
  if(a === null || b === null || a === b) return;
  const pas = Math.min(((a - b) % 6 + 6) % 6, ((b - a) % 6 + 6) % 6);
  const m = _losPrete;
  const P = m ? _posMvt(m, p.x, p.y) : {cle: lieu(p.x, p.y), hex: null, cote: null};
  const c = P.hex && !P.cote && P.hex.getCenterLocation().getTerrain();
  const e0 = p.mvt && p.mvt.etapes;
  // Sur la route d'un hexagone de bois ou de bâtiment, le véhicule n'y est
  // pas (D2.11, B13.41) : sa dernière entrée s'est faite au tarif de la
  // route ; sans entrée dans la phase, la route au centre de l'hexagone.
  const entre = e0 && [...e0].reverse().find(x => x.hex && x.pos === P.cle);
  const surRoute = entre ? !!entre.route : !!c && c.isRoad();
  const dedans = !!c && !surRoute && (_obstacleMvt(P.hex) || /Rubble/.test(c.getName()));
  // Code « D » de l'hexagone occupé : VCA doublé, motos exceptées.
  const ligne = c && (surRoute ? (_LIGNE_TEC[c.getName()] || 'ETO:Road') : _ligneTerrain(_autreTerrainMvt(P.hex) || _terrainMvt(P.hex)));
  const codeD = !!c && n.col !== 'mc' && _codeTEC(ligne && _casesTEC(ligne, n.col), 'D');
  const e = _mvtDepart(p, P.cle, a);
  const motion = _enMotion(p, p.x, p.y, [p]);
  const demarrage = _demarrer(p, e, P.cle, a, motion);
  const d = _etatMvt(e);
  const cout = pas * (dedans || codeD ? 2 : 1);
  e.push({pos: P.cle, vca: b, cout, lib: 'VCA', byp: d.byp, etat: d.etat, recul: d.recul});
  rapport(`${nomRapport(p)} : VCA ${_texteMP(p, cout, [demarrage && 'démarrage 1'])}`);
}

// ── Start, Stop, Reverse : commandes du menu des véhicules, avant le
// changement de VCA. Start : 1 MP (D2.12) ; Stop : 1 MP (D2.13) ; Reverse
// en marche : arrêt puis démarrage dans l'autre sens (D2.12, D2.21).
// Chaque commande annonce son coût au chat et au log, et s'annule par Undo.
function commandeVehicule(p, quoi){
  const n = natureMouvement(p);
  if(!n || n.unite !== 'MP') return;
  const m = _losPrete;
  const P = m ? _posMvt(m, p.x, p.y) : {cle: lieu(p.x, p.y)};
  const avait = !!p.mvt;
  const vca = _vcaPion(p);
  const e = _mvtDepart(p, P.cle, vca), n0 = e.length;
  const d = _etatMvt(e);
  const ajout = (cout, lib, etat, recul) => e.push({pos: P.cle, vca, cout, lib, byp: d.byp, etat, recul});
  let quoiTxt = null, cout = 0, plus = [];
  if(quoi === 'start' && d.etat !== 'marche'){
    ajout(1, 'démarrage', 'marche', d.recul);
    quoiTxt = 'Start'; cout = 1; plus = [d.recul && 'marche arrière'];
  } else if(quoi === 'stop' && d.etat === 'marche'){
    ajout(1, 'arrêt', 'arret', d.recul);
    quoiTxt = 'Stop'; cout = 1;
  } else if(quoi === 'reverse'){
    const vers = !d.recul;
    quoiTxt = vers ? 'Reverse' : 'Marche avant';
    if(d.etat === 'marche'){
      ajout(1, 'arrêt', 'arret', d.recul);
      ajout(1, 'démarrage', 'marche', vers);
      cout = 2; plus = ['arrêt 1, démarrage 1'];
    } else {
      ajout(0, vers ? 'marche arrière' : 'marche avant', 'arret', vers);
      plus = [vers ? 'déclaré' : 'déclarée'];
    }
  }
  if(!quoiTxt){ if(!avait && e.length === 1) delete p.mvt; return; }
  rapport(`${nomRapport(p)} : ${quoiTxt} ${_texteMP(p, cout, plus)}`);
  const etapes = e;
  _histoJs(() => {
    if(p.mvt && p.mvt.etapes === etapes) etapes.length = n0;
    if(!avait && p.mvt && p.mvt.etapes.length <= 1) delete p.mvt;
  });
}
// Entrées du menu : Start/Stop et Reverse. Les coûts
// ne figurent pas dans le menu : ils sont annoncés au chat.
function itemsVehicule(p){
  const n = natureMouvement(p);
  if(!n || n.unite !== 'MP') return [];
  const d = p.mvt ? _etatMvt(p.mvt.etapes) : {etat: 'arret', recul: false};
  const cmd = (nom, quoi) => ({nom, cmd: {nom, faire: ({effets}) => effets.push(() => commandeVehicule(p, quoi))}});
  // Une seule entrée « Start/Stop » : elle démarre un véhicule arrêté et
  // arrête un véhicule en marche.
  const items = [cmd('Start/Stop', d.etat === 'marche' ? 'stop' : 'start')];
  if(_MULT_RECUL[n.col]) items.push(d.recul ? cmd('Marche avant', 'reverse') : cmd('Reverse', 'reverse'));
  items.push(...itemsTransportVehicule(p), ...itemsSmoke(p, true));
  return items;
}

// ── Passagers et Riders (D6). Dans VASL, l'infanterie transportée est
// posée au-dessus de son véhicule ; en dessous, elle est à pied (D6.3).
// Une infanterie arrivée à pied pendant la phase est posée au sommet par
// le déplacement : elle n'est passagère qu'après « Load ».
function _vehiculeSous(p){
  const s = pile(cle(p));
  for(let i = s.indexOf(p) - 1; i >= 0; i--){
    const n = natureMouvement(s[i]);
    if(n && n.unite === 'MP') return s[i];
  }
  return null;
}
function _estPassager(p){
  const n = natureMouvement(p);
  if(!n || n.unite !== 'MF' || !_vehiculeSous(p)) return false;
  const e = p.mvt && p.mvt.etapes;
  if(!e || e.length < 2) return true;               // déjà à bord au début de la phase
  return /^(embarquement|transporté)$/.test(e[e.length - 1].lib || '');
}
// Infanterie emportée par un véhicule du même groupe.
function _transportes(liste){
  const r = new Set();
  for(const p of liste) if(_estPassager(p) && liste.includes(_vehiculeSous(p))) r.add(p);
  return r;
}
const _mfDepenses = p => p.mvt ? _cMax(_mvtSomme(p)) : 0;
const _quartMP = mp => Math.ceil(mp / 4);           // un quart de l'allocation, FRU

// Load (D6.4) : 1 MF pour monter ; le véhicule, qui doit être arrêté et
// n'avoir pas bougé, garde un quart de ses MP par MF restant à
// l'infanterie (celle qui a le plus dépensé) : le reste est compté comme
// dépensé. Unload (D6.5) : un quart des MP par pile qui descend ;
// l'infanterie paie 1 MF et a déjà dépensé 1 MF par quart des MP du
// véhicule utilisé avant (FRU). Undo annule l'un et l'autre.
// veh : le véhicule choisi (commande lancée de son menu) ; sinon celui
// de la pile, le plus haut.
function commandeTransport(p, quoi, veh){
  const n = natureMouvement(p);
  if(!n || n.unite !== 'MF') return;
  const m = _losPrete;
  const posDe = q => m ? _posMvt(m, q.x, q.y).cle : lieu(q.x, q.y);
  const s = pile(cle(p));
  const v = quoi === 'unload' ? _vehiculeSous(p)
          : veh || s.filter(q => { const k = natureMouvement(q); return k && k.unite === 'MP'; }).pop();
  if(!v) return;
  const nv = natureMouvement(v);
  const avaitP = !!p.mvt, avaitV = !!v.mvt;
  const eP = _mvtDepart(p, posDe(p), null), nP = eP.length;
  const eV = _mvtDepart(v, posDe(v), _vcaPion(v)), nV = eV.length;
  const dV = _etatMvt(eV);
  const ajoutV = (cout, lib) => eV.push({pos: posDe(v), vca: _vcaPion(v), cout, lib, byp: dV.byp, etat: dV.etat, recul: dV.recul});
  const alertes = [];
  if(dV.etat === 'marche') alertes.push('véhicule en marche');
  let txtP, txtV = null, coutP, plusP = [], coutV = 0, plusV = [];
  if(quoi === 'load'){
    if(eV.some((x, i) => i > 0 && x.pos !== eV[0].pos)) alertes.push('véhicule déjà déplacé');
    eP.push({pos: posDe(p), vca: null, cout: 1, lib: 'embarquement', etat: 'arret'});
    txtP = 'Load'; coutP = 1;
    // Rang : juste au-dessus du véhicule.
    if(s.indexOf(p) < s.indexOf(v)) deplacerDansPile(p, s.indexOf(v));
    if(nv.mp){
      const reste = Math.max(0, (n.mf || 4) - _mfDepenses(p));
      const garde = Math.min(nv.mp, _quartMP(nv.mp) * reste);
      const deja = eV.filter(x => x.lib === 'chargement').reduce((t, x) => t + x.cout, 0);
      const cout = Math.max(0, nv.mp - garde - deja);
      if(cout){ ajoutV(cout, 'chargement'); txtV = 'chargement'; coutV = _fmtMF(cout); plusV = [`${_fmtMF(garde)} MP gardés`]; }
    } else { txtV = 'chargement'; plusV = ['MP du véhicule inconnus']; }
  } else {
    const depV = _cMax(_mvtSomme(v));
    if(nv.mp && depV > nv.mp * 3 / 4) alertes.push('plus des 3/4 des MP dépensés');
    // Un quart des MP par pile qui descend : une fois par coup.
    if(nv.mp && v._dechargeNo !== _actionNo){
      ajoutV(_quartMP(nv.mp), 'déchargement');
      txtV = 'déchargement'; coutV = _quartMP(nv.mp);
    }
    const quarts = nv.mp ? Math.ceil(depV / (nv.mp / 4)) : 0;
    const avant = _mfDepenses(p), cible = Math.max(avant, quarts) + 1;
    eP.push({pos: posDe(p), vca: null, cout: cible - avant, lib: 'débarquement', etat: 'arret'});
    txtP = 'Unload'; coutP = _fmtMF(cible - avant);
    plusP = [quarts > avant && `dont ${quarts - avant} pour le trajet en véhicule`];
    // Rang : sous le véhicule, à pied.
    deplacerDansPile(p, pile(cle(p)).indexOf(v));
  }
  rapport(`${nomRapport(p)} : ${txtP} ${_texteMF(p, coutP, plusP)}`);
  if(alertes.length) chatLog(`⚠️ ${nomRapport(p)} : ${alertes.join(', ')} (D6.${quoi === 'load' ? 4 : 5})`);
  if(txtV) rapport(`${nomRapport(v)} : ${txtV} ${_texteMP(v, coutV, plusV)}`);
  if(quoi === 'unload') v._dechargeNo = _actionNo;
  const etP = eP, etV = eV;
  _histoJs(() => {
    if(p.mvt && p.mvt.etapes === etP) etP.length = nP;
    if(!avaitP && p.mvt && p.mvt.etapes.length <= 1) delete p.mvt;
    if(v.mvt && v.mvt.etapes === etV) etV.length = nV;
    if(!avaitV && v.mvt && v.mvt.etapes.length <= 1) delete v.mvt;
    v._dechargeNo = null;
  });
}
// Entrées du menu de l'infanterie : Load s'il y a un véhicule dans la
// pile, Unload pour un passager.
function itemsTransport(p){
  const n = natureMouvement(p);
  if(!n || n.unite !== 'MF') return [];
  const cmd = (nom, quoi) => ({nom, cmd: {nom, faire: ({effets}) => effets.push(() => commandeTransport(p, quoi))}});
  if(_estPassager(p)) return [cmd('Unload', 'unload')];
  const vehicule = pile(cle(p)).some(q => { const k = natureMouvement(q); return k && k.unite === 'MP'; });
  return vehicule ? [cmd('Load', 'load')] : [];
}
// Menu du véhicule : Load embarque l'infanterie à pied de son camp posée
// dans son hexagone, Unload débarque ses passagers (D6.4, D6.5). Chaque
// unité est comptée comme depuis son propre menu ; le véhicule ne paie
// qu'un quart de ses MP pour toute la pile qui descend.
function _aEmbarquer(v){
  const c = campPion(v);
  return pile(cle(v)).filter(q => { const k = natureMouvement(q);
    if(!k || k.unite !== 'MF' || q.cache || accesRefuse(q) || _estPassager(q)) return false;
    const cq = campPion(q);
    return !c || !cq || cq === c; });
}
const _passagersDe = v => pile(cle(v)).filter(q => _estPassager(q) && _vehiculeSous(q) === v);
function itemsTransportVehicule(v){
  const r = [];
  const cmd = (nom, quoi, liste) => r.push({nom, cmd: {nom, faire: ({effets}) =>
    effets.push(() => { for(const q of liste) commandeTransport(q, quoi, v); })}});
  const a = _aEmbarquer(v), d = _passagersDe(v);
  if(a.length) cmd('Load', 'load', a);
  if(d.length) cmd('Unload', 'unload', d);
  return r;
}

// ── Smoke pendant le mouvement : seulement le comptage des MF / MP,
// pour la saisie en PBEM ; les conditions des règles ne sont pas
// vérifiées.
// Grenades de l'infanterie (A24.1) : une escouade dont le FP porte un
// exposant Smoke paie 1 MF dans sa propre Location (Smoke Hex), 2 MF dans
// une Location ADJACENT (Smoke ADJ).
// Dispositifs des AFV (D13) : sD, sM, sP, sN, d'après la bande de
// caractéristiques du véhicule (« sD6 », « sM8 ») : 1 MP (D13.2).
// Grenades Smoke d'un véhicule armé d'une MA (D13.35) : 1 MP, comme un
// dispositif. Le coût est annoncé au chat et au log, et s'annule par Undo.
// Exposant de l'image affichée (face cassée : pas d'exposant) ; null si
// l'image n'est pas encore lue.
function exposantSmoke(p){
  const img = lirePiece(p).img;
  if(!img) return '';
  if(!_exposantsSmoke.has(img)){ chargerImage(img); return null; }
  return _exposantsSmoke.get(img);
}
// Dispositifs d'un véhicule, lus dans son trait info : [{type, usage}].
function dispositifsSmoke(p){
  const info = lirePiece(p).info || '';
  const r = [];
  for(const x of info.matchAll(/(?:^|[,\s])s([DMPN])(\d+)/g))
    if(!r.some(y => y.type === x[1])) r.push({type: x[1], usage: x[2]});
  return r;
}
// Véhicule armé d'une MA (D13.35). Le module VASL ne la nomme pas (tous
// ses véhicules ont « MA Malf ») : tourelle (prototype « …tur »),
// munitions à épuisement (DeplAmmo), bande de caractéristiques qui cite
// la MA (« MA:MMG ») ou une de ses munitions (A4, HE7, s8, WP6…), ou
// chenillé / automitrailleuse blindé (prototype CT / OT). Un camion, une
// Jeep, un bulldozer ou un half-track sans canon n'en ont pas.
// Les prototypes sont développés dans le pion posé : leurs noms se lisent
// dans la définition du PieceSlot (gpid).
function aUneMA(p){
  const n = natureMouvement(p);
  if(!n || n.unite !== 'MP') return false;
  if(/\bMA\b|(?:^|[,\s])(?:A|HE|s|WP|C|D|T|H)\^?\d/.test(lirePiece(p).info || '')) return true;
  const te = tePion(p), S = te ? decoderTraits(te.etat) : [];
  const gpid = ((S[S.length - 1] || '').split(';')[3] || '').trim();
  const x = gpid && _bdd && _bdd.parGpid && _bdd.parGpid.get(gpid);
  const def = (x ? x.cmd : '') + '\n' + p.seg;
  if(/prototype;[a-z]{2}[A-Z]{2}tur\b|prototype;DeplAmmo/.test(def)) return true;
  return (n.col === 'ft' || n.col === 'ac') && /prototype;[a-z]{2}(CT|OT)/.test(def);
}
function commandeSmoke(p, quoi, usage){
  const n = natureMouvement(p);
  if(!n) return;
  const m = _losPrete;
  const P = m ? _posMvt(m, p.x, p.y) : {cle: lieu(p.x, p.y)};
  const avait = !!p.mvt;
  const veh = n.unite === 'MP';
  const vca = veh ? _vcaPion(p) : null;
  const e = _mvtDepart(p, P.cle, vca), n0 = e.length;
  const d = _etatMvt(e);
  if(veh){
    const txt = quoi === 'G' ? 'Smoke grenades' : `s${quoi}${usage}`;
    e.push({pos: P.cle, vca, cout: 1, lib: 'Smoke ' + txt, byp: d.byp, etat: d.etat, recul: d.recul});
    rapport(`${nomRapport(p)} : ${txt} ${_texteMP(p, 1)}`);
  } else {
    const cout = quoi === 'adj' ? 2 : 1;
    const txt = quoi === 'adj' ? 'Smoke ADJ' : 'Smoke Hex';
    e.push({pos: P.cle, vca: null, cout, lib: txt, etat: 'arret'});
    const x = exposantSmoke(p);
    rapport(`${nomRapport(p)} : ${txt} ${_texteMF(p, cout, [x && `exposant ${x}`])}`);
  }
  const etapes = e;
  _histoJs(() => {
    if(p.mvt && p.mvt.etapes === etapes) etapes.length = n0;
    if(!avait && p.mvt && p.mvt.etapes.length <= 1) delete p.mvt;
  });
}
// Entrées du menu : Smoke Hex / Smoke ADJ pour une escouade à exposant,
// à pied ; sD, sM, sP, sN pour un véhicule qui en est équipé, et Smoke
// grenades pour un véhicule armé d'une MA.
function itemsSmoke(p, vehicule){
  const n = natureMouvement(p);
  if(!n) return [];
  const cmd = (nom, quoi, usage) => ({nom,
    cmd: {nom, faire: ({effets}) => effets.push(() => commandeSmoke(p, quoi, usage))}});
  if(n.unite === 'MP'){
    if(!vehicule) return [];
    const r = dispositifsSmoke(p).map(s => cmd(`s${s.type}${s.usage}`, s.type, s.usage));
    if(aUneMA(p)) r.push(cmd('Smoke grenades', 'G'));
    return r;
  }
  if(vehicule || n.unite !== 'MF' || _estPassager(p) || !exposantSmoke(p)) return [];
  return [cmd('Smoke Hex', 'hex'), cmd('Smoke ADJ', 'adj')];
}

// ── Relecture d'un log : les coups relus (M/) sont comptés comme ceux
// joués ici. Un changement de phase relu (« … Turn 2 - MPh », ou la
// remise à zéro de ce décompte) remet les cumuls à zéro.
const _M_RELU = /^M\/([^\/]+)\//;
function _avantRelecture(etape){
  const avant = [];
  for(const c of etape){
    const k = _M_RELU.exec(c);
    if(!k) continue;
    // VASSAL déplace une pile entière d'un seul M/ portant son id.
    const ids = parUid(k[1]) ? [k[1]] : (_idsPileM(_decoupeCmd(c.replace(/\\+$/, '').substring(2))) || []);
    for(const id of ids){
      const p = parUid(id);
      if(p && !p.deleted && !avant.some(a => a.p === p)) avant.push({p, x: p.x, y: p.y, lieu: lieuDansPile(p)});
    }
  }
  // Emplacements des piles touchées, pour un changement de rang relu.
  const lieux = new Map();
  for(const a of avant) for(const [q, l] of _lieuxPile(pile(cle(a.p)))) lieux.set(q, l);
  return {avant, portes: _transportes(avant.map(a => a.p)), lieux};
}
function compterRelecture(etape, {avant, portes, lieux}){
  if(etape.some(c => /^CHAT\* .*(Turn \d+ - \w+|Mark unmoved : MF et MP)/.test(c)))
    for(const p of pions) delete p.mvt;
  // Unités restées dans leur hexagone : changement de rang (étage, fortification).
  const surPlace = new Map([...lieux].filter(([q, l]) => q.x === l.x && q.y === l.y));
  const bougent = avant.filter(a => a.p.x !== a.x || a.p.y !== a.y);
  if(bougent.length){
    const d = compterMouvement(bougent, portes, true);
    if(d && d.texte) _completerLigneMouvement(d.texte, bougent);
    if(d) for(const t of d.alertes) chatLog('⚠️ ' + t);
  }
  if(surPlace.size) compterLieux(surPlace, true);
}
// Cumuls de toute la carte, copiés pour un recul dans la relecture.
function _photoMvt(){
  const copie = e => Object.assign({}, e, e.entree ? {entree: Object.assign({}, e.entree)} : {});
  return pions.filter(p => p.mvt).map(p => [p, p.mvt.etapes.map(copie)]);
}
function _restaurerMvt(photo){
  for(const p of pions) delete p.mvt;
  const copie = e => Object.assign({}, e, e.entree ? {entree: Object.assign({}, e.entree)} : {});
  for(const [p, etapes] of photo) p.mvt = {etapes: etapes.map(copie)};
}

// ── Étages et fortifications : l'emplacement d'une unité dans sa pile.
// Étage (B23.421) : une unité posée sur un compteur « Building » est à
// l'étage qu'il montre (calque Lev-1, Lev0 … Lev3, LevRoof) ; sinon au
// rez-de-chaussée. Fortification (B27.1) : une unité placée SOUS un
// foxhole, une tranchée, un sangar ou un pillbox est dedans.
function _niveauCompteur(q){
  const te = tePion(q);
  if(!te) return null;
  const T = decoderTraits(te.type);
  const i = T.findIndex(t => /^emb2;/.test(t) && /Lev0/.test(t));
  if(i < 0) return null;
  const etages = [...T[i].matchAll(/Lev(-?\d|Roof)\./g)].map(x => x[1]);
  const v = Math.abs(parseInt(decoderTraits(te.etat)[i], 10) || 1);
  const e = etages[v - 1];
  return e === undefined ? 0 : e === 'Roof' ? 'toit' : parseInt(e, 10);
}
const _FORTIFS = [[/Foxhole/i, 'Foxhole'], [/Trench|A-T Ditch/i, 'Trench'], [/Sangar/i, 'Sangar'], [/Pillbox|Bunker|Bombproof/i, 'Pillbox']];
function _fortifCompteur(q){
  const noms = [lirePiece(q).nomBase, q.nom];
  for(const [re, type] of _FORTIFS) if(noms.some(x => re.test(x || ''))) return type;
  return null;
}
// Hauteur d'un bâtiment, en étages, d'après la carte LOS : pour le toit.
function _hauteurBatiment(p){
  const m = _losPrete, h = m && _hexDe(m, p.x, p.y);
  let n = 0;
  for(let l = h && h.getCenterLocation().getUpLocation(); l && !/Rooftop/.test(l.getName()); l = l.getUpLocation()) n++;
  return n || 1;
}
function lieuDansPile(p){
  const s = pile(cle(p)), i = s.indexOf(p);
  let niveau = 0, fort = null;
  for(let j = i - 1; j >= 0; j--){ const v = _niveauCompteur(s[j]); if(v !== null){ niveau = v; break; } }
  for(let j = i + 1; j < s.length; j++){
    if(_niveauCompteur(s[j]) !== null) break;
    const t = _fortifCompteur(s[j]);
    if(t){ fort = {q: s[j], type: t}; break; }
  }
  const toit = niveau === 'toit';
  if(toit) niveau = _hauteurBatiment(p) + 1;
  return {niveau, toit, fort, x: p.x, y: p.y};
}
// Infanterie d'une pile et son emplacement.
function _lieuxPile(s){
  const r = new Map();
  for(const q of s){ const n = natureMouvement(q); if(n && n.unite === 'MF') r.set(q, lieuDansPile(q)); }
  return r;
}
const _memeLieu = (a, b) => a.niveau === b.niveau && !!a.toit === !!b.toit && (a.fort && a.fort.q) === (b.fort && b.fort.q);
const _nomNiveau = n => n === -1 ? 'cave' : n === 0 ? 'rez-de-chaussée' : `niveau ${n}`;
const _nomEmplacement = l => l.toit ? 'toit' : _nomNiveau(l.niveau || 0);
const _decrireLieu = l => [l.fort ? 'sous ' + l.fort.type : '', l.niveau || l.toit ? _nomEmplacement(l) : ''].filter(Boolean).join(', ') || 'rez-de-chaussée, hors fortification';
// Ce qui change d'un emplacement à l'autre : « sous Foxhole », « niveau 1 »…
function _decrireChangement(l1, l2){
  const r = [];
  if((l1.fort && l1.fort.q) !== (l2.fort && l2.fort.q))
    r.push(l2.fort ? 'sous ' + l2.fort.type : 'sort du ' + l1.fort.type);
  if((l1.niveau || 0) !== (l2.niveau || 0) || !!l1.toit !== !!l2.toit) r.push(_nomEmplacement(l2));
  return r.join(', ') || _decrireLieu(l2);
}
// Changer d'étage dans l'hexagone h (B23.4) : 1 MF par niveau, dans un
// hexagone d'escalier, imprimé ou inhérent. La carte LOS de VASL sait où
// sont les escaliers : imprimés (1U3), inhérents à chaque hexagone d'une
// maison à deux niveaux (1F1, B23.22) ; un bâtiment à étages n'en a pas
// d'autres (1U2, B23.23) ; une maison d'un hexagone n'a pas d'étage (1D4,
// B23.21). Le toit a un escalier inhérent vers l'étage du dessous (B23.8).
// Un marqueur de niveau 3 (SSR) donne un escalier inhérent (B23.24). Une
// usine n'a pas d'étage : son toit coûte 2 MF (1½ niveau) ou 3 MF (2½),
// par un escalier imprimé ou un côté de route (B23.74, B23.87).
// Vers ou depuis un étage en décombres (B24.4) : 3 MF au lieu de 1.
// Rend {cout, alerte} ; le coût est compté même quand l'alerte est levée.
function _coutNiveaux(h, l1, l2, s){
  const n1 = l1.niveau || 0, n2 = l2.niveau || 0;
  if(n1 === n2) return {cout: 0, alerte: ''};
  const dn = Math.abs(n2 - n1), bas = Math.min(n1, n2), hautN = Math.max(n1, n2);
  if(!h) return {cout: dn, alerte: ''};
  const rd = _niveauDecombres(h, s);
  let esc = 0;
  for(let k = bas; k < hautN; k++) esc += rd !== null && k + 1 >= rd ? 3 : 1;
  const c = _terrainMvt(h);
  const nomBat = c ? c.getName() : '';
  const etages = [];
  for(let l = h.getCenterLocation().getUpLocation(); l; l = l.getUpLocation()) etages.push(l.getName());
  const nbEtages = etages.filter(x => /Level/.test(x)).length, toit = etages.some(x => /Rooftop/.test(x));
  const niveau3 = (s || []).some(q => _niveauCompteur(q) === 3);
  if(/Factory/.test(nomBat)){
    const route = [0, 1, 2, 3, 4, 5].some(k => { const t = _coteDe(h, k); return t && t.isRoad(); });
    return {cout: /2\.5/.test(nomBat) ? 3 : 2,
            alerte: h.hasStairway() || route ? '' : "toit d'usine : par un escalier imprimé ou un côté de route (B23.87)"};
  }
  if(!c || !c.isBuilding()) return {cout: esc, alerte: "pas de bâtiment dans l'hexagone"};
  if(!nbEtages && !niveau3 && hautN > 0) return {cout: esc, alerte: "pas d'étage dans ce bâtiment (B23.21)"};
  const sommet = niveau3 ? Math.max(nbEtages, 3) : nbEtages;
  if((l1.toit || l2.toit) && !toit) return {cout: esc, alerte: "pas de toit praticable ici (B23.8)"};
  const etageMax = Math.max(l1.toit ? 0 : n1, l2.toit ? 0 : n2);
  if(etageMax > sommet) return {cout: esc, alerte: `le niveau ${etageMax} n'existe pas ici`};
  for(let k = Math.max(bas, 0); k < hautN; k++){
    const versToit = toit && k + 1 === sommet + 1 && (l1.toit || l2.toit);
    if(!versToit && !h.hasStairway() && !niveau3) return {cout: esc, alerte: "pas d'escalier dans cet hexagone (B23.23)"};
  }
  return {cout: esc, alerte: ''};
}
// Changement d'emplacement sur place : étages (ci-dessus), +1 MF pour
// sortir d'une fortification, +1 MF pour y entrer (B27.4).
function _coutLieu(l1, l2, h, s){
  const nv = _coutNiveaux(h, l1, l2, s);
  let c = nv.cout;
  if((l1.fort && l1.fort.q) !== (l2.fort && l2.fort.q)) c += (l1.fort ? 1 : 0) + (l2.fort ? 1 : 0);
  return {cout: c, alerte: nv.alerte};
}
// Coût d'entrée d'un hexagone, emplacements de départ et d'arrivée
// compris. Tranchées reliées (B27.54) : 1 MF en tout, quel que soit le
// terrain, doublé en montée, sans coût d'entrée ni de sortie.
function _coutEntreeLieu(m, en){
  const {base, lieuDep: l1, lieuArr: l2} = en;
  const {A, B} = en;
  if(l1.fort && l2.fort && l1.fort.type === 'Trench' && l2.fort.type === 'Trench' && A && B && _voisins(m, A, B))
    return {cout: B.getBaseLevelofHex() > A.getBaseLevelofHex() ? 2 : 1, lib: ' (tranchées reliées)'};
  if(!_estNb(base)) return {cout: base, lib: '', alerte: ''};
  let c = base, lib = '', alerte = '';
  if(l1.fort){ c = _plus(c, 1); lib += `, sortie du ${l1.fort.type}`; }
  if(l2.fort){ c = _plus(c, 1); lib += `, sous ${l2.fort.type}`; }
  // À l'arrivée, l'unité est au rez-de-chaussée : son étage se règle en la
  // replaçant, et c'est ce reclassement qui compte l'escalier, d'un étage
  // de départ à l'étage d'arrivée (même étage d'un hexagone à l'autre du
  // bâtiment : rien de plus, B23.421).
  if(en.arrivee){
    if(l1.niveau) alerte = `parti du ${_nomEmplacement(l1)} : le replacer à son étage (B23.421)`;
  } else {
    const nv = _coutNiveaux(B, l1, l2, B ? pile(cle(en.p)) : null);
    if(nv.cout){ c = _plus(c, nv.cout); lib += `, ${l2.toit ? 'toit' : _nomNiveau(l2.niveau || 0)}`; }
    alerte = nv.alerte;
  }
  return {cout: c, lib, alerte};
}
// Après un changement de rang (ici ou relu) : chaque unité dont
// l'emplacement a changé paie. Juste après son entrée dans l'hexagone,
// c'est le coût de cette entrée qui est recalculé (B27.5 : 1 + 1 MF pour
// entrer sous un foxhole ; tranchées reliées).
function compterLieux(avant, local){
  const m = _losPrete;
  const emettre = local ? t => afficherChat('* ' + t) : rapport;
  const enFumee = _fumees(m);
  // Smoke (A24.7) : +1 MF pour passer d'un niveau sans Smoke à un niveau
  // qui en contient, dans le même hexagone.
  const fumee = (p, n1, n2) => { const h = m && _hexDe(m, p.x, p.y);
    return !!h && (n1 || 0) !== (n2 || 0) && enFumee(h, n2 || 0) && !enFumee(h, n1 || 0) ? 1 : 0; };
  for(const [p, l1] of avant){
    if(p.deleted) continue;
    const l2 = lieuDansPile(p);
    if(_memeLieu(l1, l2)) continue;
    const pos = m ? _posMvt(m, p.x, p.y).cle : lieu(p.x, p.y);
    const avait = !!p.mvt;
    const e = _mvtDepart(p, pos, null), n0 = e.length, der = e[e.length - 1];
    let txt;
    if(m && der.entree && der.pos === pos){
      // Reclassement juste après l'entrée : l'entrée se recalcule.
      const garde = {cout: der.cout, lib: der.lib, lieuArr: der.entree.lieuArr};
      der.entree.lieuArr = l2;
      der.entree.p = p;
      const c = _coutEntreeLieu(m, der.entree), f = fumee(p, 0, l2.niveau);
      der.cout = f && _estNb(c.cout) ? _plus(c.cout, f) : c.cout;
      txt = `${_decrireChangement(der.entree.lieuDep, l2)} : hexagone ${_texteMF(p, _fmtCout(der.cout), [c.lib.includes('tranchées') && 'tranchées reliées', f && 'Smoke'])}`;
      if(c.alerte) chatLog(`⚠️ ${nomRapport(p)} : ${c.alerte}`);
      if(!local) _histoJs(() => { der.cout = garde.cout; der.lib = garde.lib; der.entree.lieuArr = garde.lieuArr; });
    } else {
      const c = _coutLieu(l1, l2, m ? _hexDe(m, p.x, p.y) : null, pile(cle(p))), f = fumee(p, l1.niveau, l2.niveau);
      e.push({pos, vca: null, cout: c.cout + f, lib: _decrireChangement(l1, l2) + (f ? ' + Smoke' : ''), etat: 'arret'});
      txt = `${_decrireChangement(l1, l2)} : ${_texteMF(p, c.cout + f, [f && 'Smoke'])}`;
      if(c.alerte) chatLog(`⚠️ ${nomRapport(p)} : ${c.alerte}`);
      if(!local){
        const et = e;
        _histoJs(() => { if(p.mvt && p.mvt.etapes === et) et.length = n0; if(!avait && p.mvt && p.mvt.etapes.length <= 1) delete p.mvt; });
      }
    }
    emettre(`${nomRapport(p)} : ${txt}`);
  }
}

// Après Undo : chaque chemin revient à la position et au VCA de l'unité.
function recalerMouvements(){
  const m = _losPrete;
  if(!m) return;
  for(const p of pions){
    if(!p.mvt) continue;
    if(p.deleted){ delete p.mvt; continue; }
    _mvtTronquer(p, _posMvt(m, p.x, p.y).cle, _vcaPion(p));
  }
}

// ── Statut « moved » (traits moved de VASL et markmoved de VASSAL).
const TOUCHE_MOUVEMENT = _touche(77, 650);     // moveKey de la carte VASL

function marquerDeplace(p, oui){
  const te = tePion(p);
  if(!te) return false;
  const T = decoderTraits(te.type), S = decoderTraits(te.etat);
  let change = false;
  T.forEach((t, i) => {
    if(!/^(moved|markmoved);/.test(t)) return;
    const v = oui ? 'true' : 'false';
    if((S[i] || '').replace(/\\+$/, '') !== v){ S[i] = v; change = true; }
  });
  if(!change) return false;
  _avantAction();
  const neuf = encoderTraits(S);
  fixerEtat(p, neuf);
  _logCmd(`D/${p.uid}/${neuf}/${te.etat}`);
  majApparence(p);
  return true;
}

// Bouton « mark unmoved » de la carte : lève le statut sur tous les pions.
function leverDeplaces(){
  let n = 0;
  for(const p of vivants()) if(marquerDeplace(p, false)) n++;
  // Nouvelle phase : les MF / MP repartent de zéro, ce que le chat et le
  // log annoncent. Undo du Mark unmoved rend les cumuls effacés.
  const gardes = pions.filter(p => p.mvt).map(p => [p, p.mvt]);
  for(const [p] of gardes) delete p.mvt;
  if(gardes.length){
    rapport('Mark unmoved : MF et MP remis à zéro');
    _histoJs(() => { for(const [p, mvt] of gardes) p.mvt = mvt; });
  }
  chatLog(n ? `↩️ Statut Moved levé sur ${n} pion(s)` : 'Aucun pion marqué Moved');
  return n;
}

// ── « Remove all counters of a given type » : le bouton ALL de VASL
// (MassRemover). Son menu et ses listes de noms viennent du buildFile ;
// une action retire de la carte de jeu tous les pions dont le nom simple
// (BasicPiece) ou le nom affiché (« 1 FP », « Final Fire ») figure dans sa
// liste. Tous les retraits forment un seul coup du log.
async function ouvrirMassRemover(bt){
  fermerMenu();
  let bdd;
  try { bdd = await chargerBDD(); } catch(e){ return; }
  if(!bdd.massRemover.length){ chatLog('⚠️ Le module VASL ne déclare aucun MassRemover'); return; }
  if(_profil && _profil !== 'pc'){
    const f = ouvrirFeuille({id: 'feuilleAll', titre: 'Remove all counters of a given type'});
    for(const a of bdd.massRemover){
      const b = document.createElement('button');
      b.type = 'button'; b.className = 'act ligne';
      b.innerHTML = '<span class="lb"></span>';
      b.querySelector('.lb').textContent = a.nom;
      b.title = a.pions.join(', ');
      b.addEventListener('click', () => { f.fermer(); retirerTous(a); });
      f.corps.appendChild(b);
    }
    return;
  }
  const m = document.createElement('div');
  m.id = 'ctx';
  for(const a of bdd.massRemover){
    const it = document.createElement('div');
    it.className = 'it';
    it.textContent = a.nom;
    it.title = a.pions.join(', ');
    it.addEventListener('click', e => { e.stopPropagation(); fermerMenu(); retirerTous(a); });
    m.appendChild(it);
  }
  document.body.appendChild(m);
  const r = bt.getBoundingClientRect();
  m.style.left = Math.max(4, Math.min(r.left, window.innerWidth - m.offsetWidth - 4)) + 'px';
  m.style.top = Math.max(4, Math.min(r.bottom + 2, window.innerHeight - m.offsetHeight - 4)) + 'px';
}

function retirerTous(action){
  if(!_source){ chatLog('⚠️ Aucune partie ouverte'); return 0; }
  const noms = new Set(action.pions);
  const cibles = vivants().filter(p => {
    const d = lirePiece(p);
    return noms.has(d.nomBase) || noms.has(d.nom);
  });
  // Un pion masqué par un autre joueur n'a aucune commande : il reste.
  const garde = cibles.filter(p => accesRefuse(p));
  const partis = cibles.filter(p => !accesRefuse(p));
  for(const p of partis) retirer(p, true);
  chatLog(partis.length ? `✖️ Remove all « ${action.nom} » : ${partis.length} pion(s) retiré(s)`
                        : `Remove all « ${action.nom} » : aucun pion à retirer`);
  if(garde.length) chatLog(`🔒 ${garde.length} pion(s) masqué(s) par un autre joueur laissé(s) en place`);
  return partis.length;
}

function retirer(p, silencieux){
  if(p.deleted || refuser(p)) return;
  _avantAction();
  const k = cle(p);
  p.deleted = true; p.el.remove(); _sel.delete(p);
  _logCmd('-/' + p.uid);
  ajusterDissimulation(k);
  rendrePile(k); afficherPile();
  if(!silencieux) chatLog(`✖️ ${p.nom} retiré`);
}

// Place un pion au rang i de sa pile (0 = fond), en un seul M/ qui donne
// l'ancien et le nouveau « dessous ».
function deplacerDansPile(p, i){
  const s = pile(cle(p));
  const j = s.indexOf(p);
  i = Math.max(0, Math.min(s.length - 1, i));
  if(j < 0 || i === j) return false;
  _avantAction();
  const avant = _dessous(p);
  // Étages et fortifications (B23.4, B27.4) : l'emplacement de chaque
  // unité de la pile, relevé avant le changement de rang.
  const lieux = typeof _lieuxPile === 'function' ? _lieuxPile(s) : null;
  s.splice(j, 1); s.splice(i, 0, p);
  s.forEach((q, k) => { q.ordre = k; });
  _logCmd(`M/${p.uid}/${_carteJeu}/${p.x}/${p.y}/${_dessous(p)}/${_carteJeu}/${p.x}/${p.y}/${avant}/${joueur()}`);
  if(lieux) try { compterLieux(lieux, false); }
            catch(e){ chatLog(`⚠️ Décompte des MF / MP impossible : ${e.message}`); }
  ajusterDissimulation(cle(p));
  rendrePile(cle(p)); afficherPile();
  for(const f of _apresSelection) f();
  return true;
}

// Monte (+1) ou descend (-1) un pion dans sa pile. Le voisin visible : un
// pion HIP adverse ne compte pas ; p passe de l'autre côté de ce voisin,
// les pions sautés ne bougent pas.
function changerRang(p, sens){
  const s = pile(cle(p)), i = s.indexOf(p);
  let j = i + sens;
  while(j >= 0 && j < s.length && s[j].cache && !p.cache) j += sens;
  if(j < 0 || j >= s.length) return false;
  return deplacerDansPile(p, j);
}

// Envoie un pion tout au sommet (+1) ou tout au fond (-1) de sa pile.
// Comme changerRang, un pion HIP adverse ne compte pas : le pion se pose
// au-delà du dernier pion visible, les pions cachés du bord restent.
function rangExtreme(p, sens){
  const s = pile(cle(p)), i = s.indexOf(p);
  let j = sens > 0 ? s.length - 1 : 0;
  while(j !== i && s[j].cache && !p.cache) j -= sens;
  return deplacerDansPile(p, j);
}

// Touches de rang du StackMetrics (BasicPiece.keyEvent) : sommet, fond,
// un cran plus haut, un cran plus bas, pour chaque pion sélectionné.
// Comme KeyBuffer.keyCommand, l'ordre de traitement garde l'ordre relatif
// des pions sélectionnés d'une même pile.
function rangerSelection(quoi){
  const l = trierParPile([..._sel]);
  if(!l.length) return false;
  const montant = quoi === 'top' || quoi === 'down';      // traités du fond vers le sommet
  const ordre = montant ? l : l.slice().reverse();
  // Piles dont les pions sélectionnés forment déjà le haut (ou le bas) :
  // rien à faire, plutôt que de les reposer un à un.
  const enPlace = new Set();
  if(quoi === 'top' || quoi === 'bottom') for(const k of new Set(l.map(cle))){
    const s = pile(k), n = s.filter(q => _sel.has(q)).length;
    if((quoi === 'top' ? s.slice(s.length - n) : s.slice(0, n)).every(q => _sel.has(q))) enPlace.add(k);
  }
  for(const p of ordre){
    if(enPlace.has(cle(p))) continue;
    const s = pile(cle(p)), i = s.indexOf(p);
    // Un cran : bloqué par un pion sélectionné resté en place au bord.
    if((quoi === 'up' && _sel.has(s[i + 1])) || (quoi === 'down' && _sel.has(s[i - 1]))) continue;
    deplacerDansPile(p, quoi === 'top' ? s.length - 1 : quoi === 'bottom' ? 0 : i + (quoi === 'up' ? 1 : -1));
  }
  return true;
}

// Pose un pion de la palette : le type VASSAL vient du PieceSlot, l'état
// initial aussi (il porte déjà le gpid). VASL procède en deux temps — la
// pièce naît sans position, puis un M/ la place au sommet de la pile.
function poserDepuisPalette(x, cx, cy){
  if(!_source){ chatLog('⚠️ Ouvrir d\'abord une partie : le pion doit rejoindre une carte VASL.'); return; }
  const def = developperDefinition(x.cmd);
  if(!def){ chatLog(`⚠️ ${x.n} : définition VASSAL illisible`); return; }
  if(surTables(cx, cy)){ chatLog('⛔ Pas de pion sur les tables QRDC : rien posé'); return; }
  const pos = positionDepot(cx, cy);
  const p = poserTraits(def.T, def.S, pos.x, pos.y, null, 0);
  selectionnerPile(cle(p));
  if(!p.cache) rapport(_remplir(FORMAT_CREATION, {pieceName: nomRapport(p), location: lieu(p.x, p.y), mapName: _carteJeu}));
}

// Pose une pièce décrite par ses traits. VASL procède en deux temps — la
// pièce naît sans position, puis un M/ la place, ce qui fixe aussi son
// rang : 0 sommet de la pile, 1 fond, 2 juste au-dessus de `ref`,
// 3 juste en dessous (placements de PlaceMarker).
function poserTraits(T, S, x, y, ref, placement){
  _avantAction();

  const uid = nouvelUid();
  const n = S.length - 1;
  const S1 = S.slice(), S0 = S.slice();
  S1[n] = _majPosEtat(S[n], _carteJeu, x, y);
  S0[n] = _majPosEtat(S[n], 'null', 0, 0);
  const type = encoderTraits(T);
  const p = creerPion('+/' + uid + '/' + type + '/' + encoderTraits(S1), 0);
  const s = pile(cle(p)).filter(q => q !== p);
  let i = s.length;
  if(placement === 1) i = 0;
  else if(ref && placement === 2 && s.includes(ref)) i = s.indexOf(ref) + 1;
  else if(ref && placement === 3 && s.includes(ref)) i = s.indexOf(ref);
  s.splice(i, 0, p);
  s.forEach((q, k) => { q.ordre = k; });
  _logCmd('+/' + uid + '/' + type + '/' + encoderTraits(S0));
  _logCmd(`M/${uid}/${_carteJeu}/${p.x}/${p.y}/${_dessous(p)}/null/0/0/null/${joueur()}`);
  ajusterDissimulation(cle(p));
  rendrePile(cle(p));
  return p;
}

