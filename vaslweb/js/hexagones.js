// ═══════════════════════════════════════════════════════════════
// TABLE DES HEXAGONES — positions légales des pions
// Un pion occupe le centre d'un hexagone ou le milieu d'un de ses six
// côtés (Bypass, marqueurs d'hexside). Repère : pixels VASL natifs,
// marge de carte comprise — celui des coordonnées du .vsav.
// ═══════════════════════════════════════════════════════════════
const C26 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const HEX_HY = 32.25;          // centre → milieu du côté haut/bas
const HEX_HX = 28.125;         // composante x des côtés obliques
// Les milieux des côtés sont ceux de l'accrochage VASSAL (accrocheHex).
const SNAP_COTE = 0.45;        // le côté ne gagne que tout près de son milieu
let _hexTable = [];
// Les hexagones rangés par cases de GRILLE_HEX pixels : la recherche de
// l'ancrage ne regarde que les 9 cases autour du point. La case est plus
// grande que la distance d'accrochage (75 px), ce qui suffit.
const GRILLE_HEX = 80;
let _grilleHex = new Map();
let _hexParNom = new Map();
const _caseHex = (x, y) => Math.floor(x / GRILLE_HEX) + ',' + Math.floor(y / GRILLE_HEX);

// Les hexagones retenus sont ceux dont le centre tombe sur l'image du board,
// demi-hexagones de bord compris (B0, B10…) : la grille VASL les connaît.
// Les boards « b » (A1CenterX = −901) commencent ainsi à la colonne Q.
function _buildHexTable(boards){
  _hexTable = [];
  for(const b of boards){
    const g = geoBoard(b);
    const cropX = b.crop ? (b.crop.x || 0) : 0;
    const cropY = b.crop ? (b.crop.y || 0) : 0;
    const dispW = b.dispW || g.w, dispH = b.dispH || g.h;
    const i0 = Math.max(0, Math.floor((-g.hw/4 - g.x0) / g.hw));
    const i1 = Math.ceil((g.w + g.hw/4 - g.x0) / g.hw);
    const r0 = Math.floor((-g.hh/4 - g.y0) / g.hh), r1 = Math.ceil((g.h + g.hh/4 - g.y0) / g.hh) + 1;
    for(let i = i0; i <= i1; i++){
      const col = hexrow(i);
      for(let r = r0; r <= r1; r++){
        const c = centreHex(g, col + r);
        if(c.x <= -g.hw/4 || c.x >= g.w + g.hw/4 || c.y <= -g.hh/4 || c.y >= g.h + g.hh/4) continue;
        // ASLBoard.globalCoordinates : recadrage, puis grossissement (ZOOM),
        // puis retournement sur la taille affichée.
        const z = b.zoom || 1;
        const lx = (c.x - cropX) * z, ly = (c.y - cropY) * z;
        if(ly < -g.hh*z/2 || ly > dispH + g.hh*z/2) continue;
        if(lx < -g.hw*z/2 || lx > dispW + g.hw*z/2) continue;
        // Le point posé est celui de l'accrochage de VASSAL (tronqué), passé
        // par le même globalCoordinates (Math.round après grossissement).
        const a = accrocheHex(g, i, r);
        const versCarte = q => {
          const qx = z === 1 ? q.x - cropX : Math.round((q.x - cropX) * z);
          const qy = z === 1 ? q.y - cropY : Math.round((q.y - cropY) * z);
          return {x: (b.px || 0) + (b.reversed ? dispW - qx : qx) + BOARD_MARGIN,
                  y: (b.py || 0) + (b.reversed ? dispH - qy : qy) + BOARD_MARGIN};
        };
        const p = versCarte(a);
        // Board retourné : le côté N local devient le côté S de la carte.
        const cotes = [null];
        for(let s = 1; s <= 6; s++) cotes.push(versCarte(a.cotes[b.reversed ? (s + 2) % 6 + 1 : s]));
        _hexTable.push({name: b.name + col + r, x: p.x, y: p.y, cotes});
      }
    }
  }
  // Nom d'ancrage → point, côtés compris (« 33B5 », « 33B5/NE ») : les
  // lieux cités au chat deviennent cliquables.
  _hexParNom = new Map();
  for(const h of _hexTable){
    _hexParNom.set(h.name, {x: h.x, y: h.y});
    for(let c = 1; c <= 6; c++)
      _hexParNom.set(h.name + '/' + ['', 'N', 'NE', 'SE', 'S', 'SO', 'NO'][c], {x: h.cotes[c].x, y: h.cotes[c].y});
  }
  _grilleHex = new Map();
  for(const h of _hexTable){
    const k = _caseHex(h.x, h.y);
    if(!_grilleHex.has(k)) _grilleHex.set(k, []);
    _grilleHex.get(k).push(h);
  }
}

// Ancrage légal le plus proche d'un point (pixels natifs), ou null au
// large des boards : le pion reste alors où on l'a lâché.
function ancragePlusProche(x, y){
  let hex = null, dh = Infinity;
  const cx = Math.floor(x / GRILLE_HEX), cy = Math.floor(y / GRILLE_HEX);
  for(let i = -1; i <= 1; i++) for(let j = -1; j <= 1; j++){
    for(const h of _grilleHex.get((cx + i) + ',' + (cy + j)) || []){
      const d = (h.x-x)*(h.x-x) + (h.y-y)*(h.y-y);
      if(d < dh){ dh = d; hex = h; }
    }
  }
  if(!hex || dh > 75*75) return null;
  let side = 0, ds = Infinity;
  for(let s = 1; s <= 6; s++){
    const mx = hex.cotes[s].x, my = hex.cotes[s].y;
    const d = (mx-x)*(mx-x) + (my-y)*(my-y);
    if(d < ds){ ds = d; side = s; }
  }
  const rayon = HEX_HY * SNAP_COTE;
  if(!(ds < rayon*rayon && ds < dh)) side = 0;
  return {
    x: side ? hex.cotes[side].x : hex.x,
    y: side ? hex.cotes[side].y : hex.y,
    label: hex.name + (side ? '/' + ['','N','NE','SE','S','SO','NO'][side] : '')
  };
}

