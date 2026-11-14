// ═══════════════════════════════════════════════════════════════
// DISPOSITION DES BOARDS — d'après ASLBoardPicker.java (VASL)
//     x \t y \t nom [\t cropX \t cropY \t cropW \t cropH]
//     [\t VER ...] [\t OVR ...] [\t SSR ...] [\t ZOOM z] [\t FH]
// Le miroir d'un board retourné porte sur les dimensions RECADRÉES.
// ═══════════════════════════════════════════════════════════════
function _parseBoardSegment(text){
  const seg = text.split('\x1b').find(s => s.startsWith('bd\t'));
  if(!seg) return [];
  const out = [];
  for(const part of seg.split('bd\t')){
    if(!part.trim()) continue;
    const t = part.split('\t');
    if(t.length < 3) continue;
    const col = parseInt(t[0], 10), row = parseInt(t[1], 10);
    if(isNaN(col) || isNaN(row)) continue;
    const raw = t[2];
    // 'r' seul ou 'rdx' sont de vrais noms ; sinon 'rXX' = XX retourné.
    // Le « r » est minuscule (ASLBoardPicker.buildBoard) : RBv3, RR, RO…
    // sont des noms de boards, pas des boards retournés.
    let reversed = false, name = raw;
    if(raw.startsWith('r') && !/^r(dx)?$/i.test(raw)){ reversed = true; name = raw.substring(1); }
    let crop = null;
    const n = [t[3], t[4], t[5], t[6]].map(v => parseInt(v, 10));
    if(n.every(v => !isNaN(v))) crop = {x:n[0], y:n[1], w:n[2], h:n[3]};
    const zm = part.match(/ZOOM\t([\d.]+)/);
    // Les SSR sont séparées par des tabulations, avec des champs vides
    // au milieu : on lit jusqu'au mot-clé de section suivant.
    const m = part.match(/\tSSR\t([^]*?)(?:\tVER\t|\tOVR\t|\tZOOM\t|$)/);
    out.push({
      col, row, name, reversed, crop,
      zoom: zm ? parseFloat(zm[1]) : 1,
      ssrList: m ? m[1].split('\t').filter(s => s && s !== 'true' && s !== 'false' && s !== 'FH') : [],
      overlays: _parseOverlays(part)
    });
  }
  return out;
}

// Overlays d'hexagones, lus comme ASLBoardPicker : à chaque « OVR », les
// trois jetons qui suivent sont le nom, hex1 et hex2.
function _parseOverlays(part){
  const out = [];
  let s = part;
  for(let i = s.indexOf('OVR'); i >= 0; i = s.indexOf('OVR')){
    s = s.substring(i + 4);
    const tk = s.split(/[\t ]+/).filter(x => x);
    if(tk.length >= 3) out.push({nom: tk[0], hex1: tk[1], hex2: tk[2]});
  }
  return out;
}

// ── Géométrie de chaque board, lue dans BoardMetadata.xml : tous ne
// font pas 33 x 10 (23a, Deluxe, HASL).
const _geoBoards = new Map();
const _archives = new Map();          // nom → Promise<Map(fichier → octets)>

function _archive(nom){
  if(!_archives.has(nom)){
    _archives.set(nom, fetch(VASL_BOARDS_URL + 'bd' + nom).then(async r => {
      if(!r.ok) throw new Error('bd' + nom + ' introuvable (HTTP ' + r.status + ')');
      return _zipRead(await r.arrayBuffer());
    }));
    _archives.get(nom).catch(() => _archives.delete(nom));
  }
  return _archives.get(nom);
}

async function chargerGeoBoard(nom){
  if(_geoBoards.has(nom)) return _geoBoards.get(nom);
  let g;
  try {
    const files = await _archive(nom);
    const xml = _fichier(files, 'BoardMetadata.xml');
    const att = {};
    let meta = null;
    if(xml){
      const doc = new DOMParser().parseFromString(new TextDecoder().decode(xml), 'application/xml');
      const racine = doc.documentElement;
      if(racine && racine.nodeName === 'boardMetadata'){
        for(const a of racine.attributes) att[a.name] = a.value;
        meta = _lireMetaSSR(racine);
      }
    } else {
      // Board ancien (v5) : fichier « data », une paire « clé valeur » par ligne.
      const data = _fichier(files, 'data');
      if(data) for(const l of new TextDecoder().decode(data).split(/\r?\n/)){
        const m = l.trim().split(/\s+/);
        if(m.length >= 2) att['data.' + m[0].toLowerCase()] = m[1];
      }
    }
    const nb = (v, d) => { const x = parseFloat(v); return isNaN(x) ? d : x; };
    // Libellés du menu Terrain SSR propres au board (SSRControls de
    // l'archive : « Board XX Special overlays »…), règle → texte.
    const libelles = {};
    const ctl = _fichier(files, 'SSRControls');
    if(ctl){
      const doc = new DOMParser().parseFromString(new TextDecoder().decode(ctl), 'application/xml');
      for(const e of doc.getElementsByTagName('entry')){
        const regle = e.getAttribute('rule'), texte = e.getAttribute('text') || e.getAttribute('name');
        if(regle && texte) for(const r of regle.split(',')) if(r.trim()) libelles[r.trim()] = texte;
      }
    }
    g = {
      libelles,
      cols: parseInt(att.width, 10)  || HEX_COLS,
      rows: parseInt(att.height, 10) || HEX_ROWS,
      hw: nb(att.hexWidth  ?? att['data.dx'], 56.25),
      hh: nb(att.hexHeight ?? att['data.dy'], 64.5),
      meta,
    };
    // Origine de la grille (centre de A1). VASL la tronque en entier
    // (ASLBoard.initializeFromArchive : « (int) getA1CenterX() »).
    g.x0 = Math.trunc(nb(att.A1CenterX ?? att['data.x0'], 0));
    g.y0 = Math.trunc(nb(att.A1CenterY ?? att['data.y0'], g.hh / 2));
    g.alt = (att.altHexGrain ?? att['data.alternate']) === 'true';
    // La taille de l'image fait foi : les métadonnées manquent sur les
    // boards les plus anciens.
    const img = _gifDuBoard(files, nom, att.boardImageFileName);
    if(img && img.length > 10 && img[0] === 0x47){
      g.w = img[6] | (img[7] << 8);
      g.h = img[8] | (img[9] << 8);
    } else {
      g.w = Math.round((g.cols - 1) * g.hw);
      g.h = Math.round(g.rows * g.hh);
    }
  } catch(e){
    g = {cols:HEX_COLS, rows:HEX_ROWS, hw:56.25, hh:64.5, x0:0, y0:32, alt:false,
         w:BOARD_W, h:BOARD_H, defaut:true};
  }
  _geoBoards.set(nom, g);
  return g;
}

function geoBoard(b){
  return _geoBoards.get(b && b.name)
      || {cols:HEX_COLS, rows:HEX_ROWS, hw:56.25, hh:64.5, x0:0, y0:32, alt:false,
          w:BOARD_W, h:BOARD_H};
}

// Fichier d'une archive, sans tenir compte de la casse.
function _fichier(files, nom){
  if(files.has(nom)) return files.get(nom);
  const voulu = nom.toLowerCase();
  for(const [n, d] of files) if(n.toLowerCase() === voulu) return d;
  return null;
}

// ── Grille VASL (ASLHexGrid). Colonnes A…Z, AA…ZZ, AAA… ; les colonnes
// paires (A, C…) ont leur rangée 1 à y0, les impaires une demi-rangée
// plus bas (plus haut si la grille est « alternate »).
function hexrow(i){
  let s = '';
  for(let n = i; n >= 0; n -= 26) s += C26[n % 26];
  return s;
}
function centreHex(g, nom){
  const m = /^([a-z])(\1*)(-?\d+)$/i.exec(nom.trim());
  if(!m) return null;
  const col = m[1].toLowerCase().charCodeAt(0) - 97 + 26 * m[2].length;
  const rang = parseInt(m[3], 10);
  const y = col % 2 === 0 ? Math.trunc(g.hh * (rang - 1) + .5)
          : g.alt         ? Math.trunc(g.hh * (rang - 1.5) + .5)
          :                 Math.trunc(g.hh * (rang - .5) + .5);
  return {x: Math.trunc(g.hw * col + .5) + g.x0, y: y + g.y0};
}

// ── Point d'accrochage VASSAL d'un hexagone (HexGrid.hexPoint et
// sideX/sideY, repère local du board). centreHex suit getLocation, qui
// arrondit ; l'accrochage d'un pion lâché, lui, tronque :
//   x = (int)(dx·nx + x0)
//   y = (int)(dy·ny + y0)              colonne paire
//     = (int)(dy·ny + (int)(dy/2) + y0)  colonne impaire
// (G4 du board 8 : 337, 193 et non 338, 194). Une pile posée au pixel
// arrondi ne se fond pas, chez VASL, avec un pion qu'on y lâche ensuite.
// Côtés : grille au demi-pas, (int)(dx/2·sx + x0) et (int)(dy/2·sy + y0),
// ou (int)(dy/2·sy + (int)(dy/4) + y0) entre deux colonnes.
// Rend le centre et ses six côtés (1 = N … 6 = NO).
function accrocheHex(g, i, rang){
  const dx = g.hw, dy = g.hh, x0 = g.x0, y0 = g.y0;
  const impaire = i % 2 !== 0;
  const ny = impaire && g.alt ? rang - 2 : rang - 1;
  const cx = Math.trunc(dx * i + x0);
  const cy = impaire ? Math.trunc(dy * ny + Math.trunc(dy / 2) + y0) : Math.trunc(dy * ny + y0);
  const c = impaire ? 2 * ny + 1 : 2 * ny;          // centre, en demi-pas dy/2
  const sxG = Math.trunc(dx / 2 * (2 * i - 1) + x0), sxD = Math.trunc(dx / 2 * (2 * i + 1) + x0);
  const yCol = sy => Math.trunc(dy / 2 * sy + y0);
  const yEntre = sy => Math.trunc(dy / 2 * sy + Math.trunc(dy / 4) + y0);
  return {x: cx, y: cy, cotes: [null,
    {x: cx,  y: yCol(c - 1)}, {x: sxD, y: yEntre(c - 1)}, {x: sxD, y: yEntre(c)},
    {x: cx,  y: yCol(c + 1)}, {x: sxG, y: yEntre(c)},     {x: sxG, y: yEntre(c - 1)}]};
}

// ── Règles SSR d'un fichier de métadonnées (SharedBoardMetadata.xml ou
// BoardMetadata.xml d'un board), dans l'ordre du fichier :
//   colors    : [[nom, 0xRRGGBB]]
//   colorSSR  : [[règle, [[nom de, nom vers], …]]]
//   overlays  : [[règle, [{image, x, y}, …]]]   (images de l'archive)
//   underlays : [[règle, {image, couleurs: [nom, …]}]]
function _lireMetaSSR(racine){
  const enfant = (el, nom) => [...el.children].find(e => e.nodeName === nom);
  const sous = (el, nom) => el ? [...el.children].filter(e => e.nodeName === nom) : [];
  const meta = {colors: [], colorSSR: [], overlays: [], underlays: []};
  for(const c of sous(enfant(racine, 'colors'), 'color')){
    const v = ['red', 'green', 'blue'].map(k => parseInt(c.getAttribute(k), 10));
    if(v.every(x => x >= 0 && x <= 255)) meta.colors.push([c.getAttribute('name'), v[0]<<16 | v[1]<<8 | v[2]]);
  }
  for(const r of sous(enfant(racine, 'colorSSRules'), 'colorSSR'))
    meta.colorSSR.push([r.getAttribute('name'),
      sous(r, 'colorMap').map(m => [m.getAttribute('fromColor'), m.getAttribute('toColor')])]);
  const img = e => ({image: e.getAttribute('image'),
                     x: parseInt(e.getAttribute('x'), 10) || 0, y: parseInt(e.getAttribute('y'), 10) || 0});
  for(const e of [...(enfant(racine, 'overlaySSRules') || {children: []}).children]){
    const nom = e.getAttribute('name');
    if(e.nodeName === 'overlaySSRule') meta.overlays.push([nom, [img(e)]]);
    else if(e.nodeName === 'overlaySSRuleSet') meta.overlays.push([nom, sous(e, 'overlaySSRuleImage').map(img)]);
    else if(e.nodeName === 'underlaySSRule')
      meta.underlays.push([nom, {image: e.getAttribute('image'),
                                 couleurs: sous(e, 'color').map(c => c.getAttribute('name'))}]);
  }
  return meta;
}

// Métadonnées partagées du module, téléchargées une fois, seulement si un
// board redéfinit des couleurs ou des règles : sans cela, LOS_SSR suffit.
let _partagee = null, _textePartage = null;
function textePartage(){
  if(!_textePartage){
    _textePartage = fetch(VASL_BOARDDATA_URL + 'SharedBoardMetadata.xml').then(r => {
      if(!r.ok) throw new Error('SharedBoardMetadata.xml introuvable (HTTP ' + r.status + ')');
      return r.text();
    });
    _textePartage.catch(() => { _textePartage = null; });
  }
  return _textePartage;
}
function metaPartagee(){
  if(!_partagee){
    _partagee = textePartage().then(texte => {
      const doc = new DOMParser().parseFromString(texte, 'application/xml');
      return _lireMetaSSR(doc.documentElement);
    });
    _partagee.catch(() => { _partagee = null; });
  }
  return _partagee;
}

function _gifDuBoard(files, nom, prefere){
  if(prefere && files.get(prefere)) return files.get(prefere);
  const voulu = ('bd' + nom + '.gif').toLowerCase();
  for(const [n, d] of files) if(n.toLowerCase() === voulu) return d;
  return null;
}

// Comme Map.getLocation de VASSAL : un board est décalé de la largeur des
// boards qui le précèdent sur SA rangée, et de la hauteur de ceux qui le
// précèdent dans SA colonne ; une case vide compte pour zéro. Avec des
// boards recadrés ou de tailles différentes, ce n'est pas une grille.
function _layoutBoards(boards){
  const larg = new Map(), haut = new Map();
  for(const b of boards){
    const g = geoBoard(b);
    const w  = (b.crop && b.crop.w > 0) ? b.crop.w : g.w;
    const hh = (b.crop && b.crop.h > 0) ? b.crop.h : g.h;
    b.dispW = Math.round(w * b.zoom);
    b.dispH = Math.round(hh * b.zoom);
    larg.set(b.col + ',' + b.row, b.dispW);
    haut.set(b.col + ',' + b.row, b.dispH);
  }
  let totalW = 0, totalH = 0;
  for(const b of boards){
    b.px = 0; b.py = 0;
    for(let c = 0; c < b.col; c++) b.px += larg.get(c + ',' + b.row) || 0;
    for(let r = 0; r < b.row; r++) b.py += haut.get(b.col + ',' + r) || 0;
    totalW = Math.max(totalW, b.px + b.dispW);
    totalH = Math.max(totalH, b.py + b.dispH);
  }
  return {totalW, totalH};
}

// ── Beaucoup de GPU Android plafonnent un canvas à 4096 px de côté.
let _canvasMax = null;
function _maxCanvasSide(){
  if(_canvasMax !== null) return _canvasMax;
  for(const s of [16384, 8192, 4096, 2048]){
    try {
      const c = document.createElement('canvas');
      c.width = s; c.height = 1;
      const g = c.getContext('2d');
      if(!g) continue;
      g.fillStyle = '#fff'; g.fillRect(s-1, 0, 1, 1);
      if(g.getImageData(s-1, 0, 1, 1).data[3] === 255){ _canvasMax = s; break; }
    } catch(e){ /* taille refusée */ }
  }
  if(_canvasMax === null) _canvasMax = 2048;
  return _canvasMax;
}
function _sizeCanvasSafely(cvs, wantW, wantH){
  const max = _maxCanvasSide();
  const k = Math.min(1, max/wantW, max/wantH);
  cvs.width  = Math.max(1, Math.floor(wantW*k));
  cvs.height = Math.max(1, Math.floor(wantH*k));
  return k;
}

async function _runLimited(items, worker, limit){
  limit = limit || (navigator.maxTouchPoints > 0 ? 4 : 8);
  let i = 0;
  await Promise.all(new Array(Math.min(limit, items.length)).fill(0).map(async () => {
    while(i < items.length){
      const k = i++;
      try { await worker(items[k], k); } catch(e){ /* isolé */ }
    }
  }));
}

const _boardCache = new Map();          // nom → URL de Blob
async function _fetchBoardImage(name){
  if(_boardCache.has(name)) return _boardCache.get(name);
  const files = await _archive(name);
  const gif = _gifDuBoard(files, name);
  if(!gif) throw new Error('bd' + name + '.gif absent de l\'archive');
  const url = URL.createObjectURL(new Blob([gif], {type:'image/gif'}));
  _boardCache.set(name, url);
  return url;
}

// ═══ SSR DE TERRAIN ═══
// VASL repeint l'image du board couleur par couleur (colorSSR).
// LOS_SSR, SSR_LOS_SEULES : js/donnees/ssr.js

// Table de substitution, construite comme SSRFilter.loadRules : une seule
// table pour toutes les règles, parcourues dans l'ordre du fichier de
// métadonnées (celui de LOS_SSR), pas dans celui de la sauvegarde. La
// première règle qui remappe une couleur l'emporte, et chaque nouvelle
// correspondance prolonge celles qui aboutissaient à sa couleur source :
// Level6ToLevel5 puis Level5ToLevel4… ramène ainsi un niveau 6 jusqu'à 0.
// tbl : LOS_SSR, ou la table propre au board (reglesBoard).
function tableSSR(regles, tbl = LOS_SSR){
  const voulues = new Set(regles);
  const table = new Map();
  for(const [nom, r] of Object.entries(tbl)){
    if(!voulues.has(nom) || !r.c) continue;
    for(const [de, vers] of r.c){
      if(!de || !vers) continue;
      const a = de[0]<<16 | de[1]<<8 | de[2], b = vers[0]<<16 | vers[1]<<8 | vers[2];
      if(a === b) continue;
      if(!table.has(a)) table.set(a, b);
      for(const [k, v] of table) if(v === a) table.set(k, b);
    }
  }
  return table;
}

// Repeint un canvas en une passe (SSRFilter.transform) ; l'alpha est
// conservé. Renvoie le nombre de pixels changés.
function repeindre(cv, table){
  if(!table.size) return 0;
  const g = cv.getContext('2d', {willReadFrequently:true});
  const d = g.getImageData(0, 0, cv.width, cv.height);
  const px = d.data;
  let n = 0;
  for(let k = 0; k < px.length; k += 4){
    const c = table.get(px[k]<<16 | px[k+1]<<8 | px[k+2]);
    if(c !== undefined){
      px[k] = c >> 16; px[k+1] = (c >> 8) & 255; px[k+2] = c & 255;
      n++;
    }
  }
  if(n) g.putImageData(d, 0, 0);
  return n;
}

// ── Motifs (underlaySSRule) : boardData/<motif>.gif du module VASL.
const _motifs = new Map();              // nom → Promise<ImageData>

function chargerMotif(nom){
  if(!_motifs.has(nom)){
    _motifs.set(nom, (async () => {
      const r = await fetch(VASL_BOARDDATA_URL + nom);
      if(!r.ok) throw new Error(nom + ' introuvable (HTTP ' + r.status + ')');
      const im = await createImageBitmap(await r.blob());
      const cv = document.createElement('canvas');
      cv.width = im.width; cv.height = im.height;
      const g = cv.getContext('2d', {willReadFrequently:true});
      g.drawImage(im, 0, 0);
      return g.getImageData(0, 0, im.width, im.height);
    })());
    _motifs.get(nom).catch(() => _motifs.delete(nom));
  }
  return _motifs.get(nom);
}

// Pose les motifs sur l'image visible, comme Underlay.loadImage : le motif,
// répété depuis le coin du canvas, n'apparaît que là où le board d'origine
// (non repeint) porte l'une des couleurs de la règle. Ses pixels
// transparents ou noirs (HolePunch({0})) laissent voir le board. VASL
// insère les underlays en tête de liste : le dernier dessiné, donc celui
// qui l'emporte, est le premier dans l'ordre du fichier (celui de tbl).
// base : image où chercher les couleurs, si ce n'est pas le canvas lui-même
// (l'image d'un overlay avant ses propres images SSR) ; ses pixels
// transparents ne reçoivent pas de motif.
async function poserMotifs(b, cv, regles, tbl = LOS_SSR, base = null){
  const motifs = [];
  for(const r of regles){
    try {
      const m = await chargerMotif(tbl[r].m[0]);
      motifs.push({m, couleurs: new Set(tbl[r].m[1].filter(c => c)
                                 .map(c => c[0]<<16 | c[1]<<8 | c[2]))});
    } catch(e){
      chatLog(`⚠️ bd${b.name} : motif de ${r} indisponible (${e.message})`);
    }
  }
  if(!motifs.length) return 0;
  const g = cv.getContext('2d', {willReadFrequently:true});
  const d = g.getImageData(0, 0, cv.width, cv.height);
  const px = d.data, W = cv.width, H = cv.height;
  const ref = base ? base.data : px;
  const toutes = new Set(motifs.flatMap(o => [...o.couleurs]));
  let n = 0;
  for(let y = 0, k = 0; y < H; y++){
    for(let x = 0; x < W; x++, k += 4){
      const rgb = ref[k]<<16 | ref[k+1]<<8 | ref[k+2];
      if(ref[k+3] < 128 || !toutes.has(rgb)) continue;
      for(const {m, couleurs} of motifs){
        if(!couleurs.has(rgb)) continue;
        const s = m.data, j = ((y % m.height) * m.width + (x % m.width)) * 4;
        if(s[j+3] < 128 || !(s[j] | s[j+1] | s[j+2])) continue;
        px[k] = s[j]; px[k+1] = s[j+1]; px[k+2] = s[j+2];
        n++;
        break;
      }
    }
  }
  if(n) g.putImageData(d, 0, 0);
  return n;
}

// ── Règles d'un board. Son BoardMetadata.xml part des métadonnées
// partagées (BoardMetadata) : ses couleurs remplacent celles du même nom,
// ses colorSSR s'ajoutent (ou complètent la règle du même nom), ses
// underlays remplacent la règle du même nom à sa place. Quand le board ne
// touche à rien de ce que demande la sauvegarde, LOS_SSR, calculée sur les
// couleurs partagées, donne le même résultat sans téléchargement.
async function reglesBoard(b){
  const g = geoBoard(b), meta = g.meta;
  const voulues = new Set(b.ssrList || []);
  const touche = meta && (
       meta.colorSSR.some(([n]) => voulues.has(n))
    || meta.underlays.some(([n]) => voulues.has(n))
    || (meta.colors.length && [...voulues].some(r => LOS_SSR[r] && (LOS_SSR[r].c || LOS_SSR[r].m))));
  if(!touche) return LOS_SSR;
  if(g.tblSSR) return g.tblSSR;
  let p;
  try { p = await metaPartagee(); }
  catch(e){
    chatLog(`⚠️ bd${b.name} : couleurs propres au board ignorées (${e.message})`);
    return LOS_SSR;
  }
  const couleurs = new Map(p.colors);
  for(const [n, c] of meta.colors) couleurs.set(n, c);
  const colorSSR = new Map();
  for(const [n, maps] of [...p.colorSSR, ...meta.colorSSR])
    colorSSR.set(n, (colorSSR.get(n) || []).concat(maps));
  const underlays = new Map(p.underlays);
  for(const [n, u] of meta.underlays) underlays.set(n, u);
  const rgb = n => {
    const c = couleurs.get(n);
    return c === undefined ? null : [c >> 16, (c >> 8) & 255, c & 255];
  };
  const tbl = {};
  for(const [n, maps] of colorSSR) tbl[n] = {c: maps.map(([de, vers]) => [rgb(de), rgb(vers)])};
  for(const [n, u] of underlays) tbl[n] = Object.assign(tbl[n] || {}, {m: [u.image, u.couleurs.map(rgb)]});
  g.tblSSR = tbl;
  return tbl;
}

// ── Images SSR du board (SSROverlay) : posées à x,y, repère du board non
// recadré. setTerrain les insère une à une en tête de liste : elles se
// dessinent donc dans l'ordre inverse du fichier, sur les motifs et sous
// les overlays d'hexagones.
async function poserImagesSSR(b, cv, regles, sx, sy){
  const files = await _archive(b.name);
  const liste = regles.flatMap(([r, imgs]) => imgs.map(i => [r, i])).reverse();
  const g = cv.getContext('2d');
  let n = 0;
  for(const [r, i] of liste){
    const octets = _fichier(files, i.image);
    if(!octets){ chatLog(`⚠️ bd${b.name} : image ${i.image} (${r}) absente de l'archive`); continue; }
    try {
      const bm = await createImageBitmap(new Blob([octets]));
      g.drawImage(bm, i.x - sx, i.y - sy);
      bm.close();
      n++;
    } catch(e){
      chatLog(`⚠️ bd${b.name} : image ${i.image} illisible`);
    }
  }
  return n;
}

// ═══ OVERLAYS D'HEXAGONES ═══ (Overlay.java)
// L'image « ovr<nom><orientation>.gif » se trouve dans l'archive
// boards/bdFiles/overlays/ovr<NOM SANS CHIFFRES>. Son fichier « data » donne,
// pour chaque orientation a…f, l'hexagone d'origine : l'image se pose à
// centre(hex1) − centre(origine), dans le repère du board non recadré.
const VASL_OVERLAYS_URL =
  'https://raw.githubusercontent.com/vasl-developers/vasl/develop/boards/bdFiles/overlays/';
const _archivesOvr = new Map();         // nom d'archive → Promise<{files, data, meta}>

function _equivalence(f){
  if(/^be[23]/.test(f)) return 'be1' + f.substring(3);
  if(/^be[56]/.test(f)) return 'be4' + f.substring(3);
  if(f.startsWith('oc2')) return 'oc1' + f.substring(3);
  if(f.startsWith('oc4')) return 'oc3' + f.substring(3);
  return f;
}
// Retire l'orientation, puis la queue de chiffres si le nom commence
// par une lettre : « o3a » → « o », « rr10a » → « rr », « 1a » → « 1 ».
function _trimNom(s){
  s = s.substring(0, s.length - 1);
  if(/^[0-9]/.test(s)) return s;
  return s.replace(/[0-9]+$/, '') || s;
}
const archiveOverlay = nom => 'ovr' + _trimNom(_equivalence(nom + 'a')).toUpperCase();
const imageOverlay   = nom => 'ovr' + _equivalence(nom) + '.gif';

function chargerArchiveOverlay(arch){
  if(!_archivesOvr.has(arch)){
    _archivesOvr.set(arch, fetch(VASL_OVERLAYS_URL + arch).then(async r => {
      if(!r.ok) throw new Error(arch + ' introuvable (HTTP ' + r.status + ')');
      const files = await _zipRead(await r.arrayBuffer());
      const data = _fichier(files, 'data');
      const xml = _fichier(files, 'BoardMetadata.xml');
      let meta = null;
      if(xml){
        const doc = new DOMParser().parseFromString(new TextDecoder().decode(xml), 'application/xml');
        if(doc.documentElement && doc.documentElement.nodeName === 'boardMetadata')
          meta = _lireMetaSSR(doc.documentElement);
      }
      return {files, data: data ? new TextDecoder().decode(data) : '', meta};
    }));
    _archivesOvr.get(arch).catch(() => _archivesOvr.delete(arch));
  }
  return _archivesOvr.get(arch);
}

// Origines par défaut, quand le fichier data n'en donne pas
// (Overlay.getDefaultOriginList).
const ORIGINES_OVR = (() => {
  const t = {}, def = (noms, o) => noms.split(' ').forEach(n => t[n] = o);
  def('st1', 'b7;g4;g3;d5;h3;i4'); def('st2', 'd6;c5;g2;g4;g6;h4'); def('st3', 'e8;c6;g1;i3;i7;j4');
  def('1 2 3', 'b2; ; ;l8; ; '); def('4', 'b0; ; ;j10; ; '); def('5', 'c2; ; ;g9; ; ');
  def('d1', 'b3;c2;e4;f4;e6;c4'); def('d2', 'b3;b2;d1;f3;f4;d5'); def('d3', 'b4;b2;b1;b1;e1;e3');
  def('d4', 'b2;b1;c1;e2;e3;c4'); def('d5', 'b3;d1;h2;h5;h7;d6'); def('d6', 'b3;d2;f3;h3;f4;d3');
  def('e1', 'a1; ; ;gg10; ; ');
  def('h1', 'b4;f1;c7;j6;h9;i4'); def('h2', 'c5;d3;f3;e5;f5;f1'); def('h3', 'b3;b1;e1;f1;f3;c4');
  def('h4', 'd6;h3;h5;d6;h4;f5'); def('h5', 'c6;e3;f3;e6;i5;f5'); def('h6', 'b4;d2;e2;f4;h3;e5');
  def('s1', 'b3;d1;e2;d3;f2;c4'); def('s2', 'b2;b1;c1;d1;d2;c3'); def('s3', 'c3;e2;e3;e4;e3;c4');
  def('s4', 'b3;c2;d1;d2;e2;d2'); def('s5', 'c5;e5;e2;g4;e4;g3'); def('s6', 'b3;b2;b2;d1;d2;d2');
  def('s7', 'b3;b1;b1;d1;f1;d3'); def('s8', 'b3;b2;d5;f3;f4;d1');
  def('sd1', 'b4;d2;e3;d3;f4;e4'); def('sd2', 'c2;f1;e4;g5;f4;c5'); def('sd3', 'b3;b2;d1;f2;f3;d4');
  def('sd4', 'b3;b1;e2;f2;f4;c4'); def('sd5', 'c6;f2;f4;e5;h4;f4'); def('sd6', 'c6;b3;d1;g2;h4;f4');
  def('sd7', 'b2;b1;e2;f2;f3;c3'); def('sd8', 'b4;c3;g1;f4;g5;e4');
  def('w1', 'e6;c6;h1;g4;e6;h3'); def('w2', 'h5;g6;d6;f4;i1;f5'); def('w3', 'b7;c4;h1;f4;i6;h4');
  def('w4', 'b6;d2;h2;h5;j6;f6');
  def('ef1', 'e3;e3;b4;e3;e3;c4'); def('ef2', 'b3;e2;f2;d4;e4;d3'); def('ef3', 'c5;d3;e2;e4;g3;f5');
  def('be1 be2 be3 be7', 'g11;e10;b5;b1;k1;n5'); def('be4 be5 be6', 'm7;i12;b9;b1;g1;n3');
  def('oc1 oc2', 'm13;h15;d9;b1;o1;s7'); def('oc3 oc4', 'm14;h15;c9;b1;o1;t7');
  def('x20 x21', 'c2;f2;f4;c5;c5;c2'); def('x23 x24', 'b2;d1;c2;e2;d3;d4');
  def('rr1 rr2 rr8', 'b5;h4;h4;b7;f5;f3'); def('rr3 rr11', 'c5;d4;d2;c2;g3;g4');
  def('rr4 rr12', 'c4;d4;d2;c3;e3;e4'); def('rr7', 'b7;f4;f3;b5;h3;h4');
  def('rr13', 'c6;d2;g2;g5;h6;e7'); def('rr14', 'b6;f3;g3;c5;h3;g4');
  def('re1', 'c3;b1;k1;l5;d9;d7'); def('re2', 'c5;b1;k2;l7;f9;e9'); def('re3', 'h5;h6;c7;d6;e3;d3');
  def('re4 re5', 'b4;b1;c1;e1;f3;d4');
  def('hd5 hd6 hd7', 'b4;b2;b1;c1;d1;d2'); def('hd8', 'b3;c2;d1;d2;d2;d2;');
  def('hd9 hd10', 'b4;b3;b1;c1;f1;f3'); def('hd11', 'd6;b5;b1;c1;g2;h4'); def('ow1', 'c3;c3;d2;e3;d3;d3');
  return t;
})();
function _originesDefaut(nom){
  if(ORIGINES_OVR[nom]) return ORIGINES_OVR[nom];
  if(/^(wd|g|m|p|x|b|rp|rr|hd|o)/.test(nom)) return 'b2;b1;c1;d1;d2;c3';
  return null;
}

// Fichier data (Overlay.readData) : « nom origines… », « placeAt h1 h2 ».
function _lireDataOverlay(texte, nom){
  const r = {origines: _originesDefaut(nom), placeAt: null};
  for(const l of texte.split(/\r?\n/)){
    const tk = l.trim().split(/\s+/);
    if(tk.length < 2) continue;
    if(tk[0].toLowerCase() === nom.toLowerCase()) r.origines = l.trim().substring(nom.length).trim();
    else if(tk[0].toLowerCase() === 'placeat') r.placeAt = [tk[1], tk[2] || tk[1]];
  }
  return r;
}

// Orientation a…f d'après la position de hex2 par rapport à hex1.
function _orientation(g, hex1, hex2){
  if(hex1 === hex2) return 'a';
  const p = centreHex(g, hex1), q = centreHex(g, hex2);
  if(!p || !q) return 'a';
  if(p.x === q.x) return p.y < q.y ? 'd' : 'a';
  if(p.x > q.x) return p.y > q.y ? 'f' : 'e';
  return p.y > q.y ? 'b' : 'c';
}

// Hexagone d'origine pour une orientation, avec les deux bricolages de
// VASL (Overlay.offset). La liste Java porte la chaîne unique
// « 22b, 23b, 24b, 25b, 26b » : ces boards-là ne sont pas décalés.
const BOARDS_B = new Set(['1b','2b','3b','4b','5b','6b','7b','8b','9b','10b','11b','12b','13b',
  '14b','15b','16b','17b','18b','19b','20b','21b']);
function _origine(origines, c, board){
  let o = (origines || '').split(';')[c.charCodeAt(0) - 97];
  if(o === undefined) return null;
  o = o.trim();
  if(!o) return null;
  if(BOARDS_B.has(board)) o = String.fromCharCode(o.charCodeAt(0) + 16) + o.substring(1);
  if(/^BFPDW[1-7]b$/.test(board)){
    const r = parseInt(o.substring(1), 10);
    if(!isNaN(r)) o = o.substring(0, 1) + (r + 10);
  }
  return o;
}

// Prépare un overlay : image, position (repère du board non recadré) et
// métadonnées de son archive. Lève une erreur lisible en cas d'échec.
async function preparerOverlay(b, o){
  const g = geoBoard(b);
  const arch = archiveOverlay(o.nom);
  const a = await chargerArchiveOverlay(arch);
  const d = _lireDataOverlay(a.data, o.nom);
  let hex1 = o.hex1, hex2 = o.hex2;
  if(d.placeAt) [hex1, hex2] = d.placeAt;
  const unHex = !!_fichier(a.files, imageOverlay(o.nom + 'a')) && !_fichier(a.files, imageOverlay(o.nom + 'd'));
  if(unHex && hex1 !== hex2) throw new Error('un seul hexagone attendu');
  if(!unHex && hex1 === hex2) throw new Error('deux hexagones attendus');
  const p1 = centreHex(g, hex1), p2 = centreHex(g, hex2);
  if(!p1 || !p2) throw new Error('hexagone ' + (p1 ? hex2 : hex1) + ' invalide');
  // Overlay.check : hex2 doit toucher hex1.
  if(Math.abs(p2.x - p1.x) > 1.1 * g.hw || Math.abs(p2.y - p1.y) > 1.1 * g.hh)
    throw new Error(hex1 + ' et ' + hex2 + ' ne sont pas voisins');
  const c = unHex ? 'a' : _orientation(g, hex1, hex2);
  const fichier = imageOverlay(o.nom + c);
  const octets = _fichier(a.files, fichier);
  if(!octets) throw new Error(fichier + ' absent de ' + arch);
  const nomOrigine = _origine(d.origines, c, b.name);
  const po = nomOrigine && centreHex(g, nomOrigine);
  if(!po) throw new Error(`orientation « ${c} » sans origine`);
  const image = await createImageBitmap(new Blob([octets]));
  return {nom: o.nom, archive: arch, meta: a.meta, files: a.files, image,
          x: p1.x - po.x, y: p1.y - po.y, orientation: c};
}

// Dessin d'un overlay (ASLBoard.BoardOp) : son image, puis, si le board a
// des SSR et que l'archive de l'overlay a son BoardMetadata.xml
// (Overlay.getTerrain), les images SSR de cette archive et les motifs du
// board découpés dans l'image de l'overlay — dans cet ordre.
async function dessinerOverlay(b, cv, ov, tbl, voulues, sx, sy){
  let src = ov.image;
  if(voulues.size && ov.meta){
    const c = document.createElement('canvas');
    c.width = ov.image.width; c.height = ov.image.height;
    const g = c.getContext('2d', {willReadFrequently:true});
    g.drawImage(ov.image, 0, 0);
    const base = g.getImageData(0, 0, c.width, c.height);
    const images = ov.meta.overlays.filter(([r]) => voulues.has(r));
    let nImg = 0;
    for(const [r, imgs] of images) for(const i of imgs){
      const octets = _fichier(ov.files, i.image);
      if(!octets){ chatLog(`⚠️ ${ov.archive} : image ${i.image} (${r}) absente`); continue; }
      const bm = await createImageBitmap(new Blob([octets]));
      g.drawImage(bm, i.x, i.y);
      bm.close();
      nImg++;
    }
    if(nImg) chatLog(`🖼️ overlay ${ov.nom} : ${images.map(([r]) => r).join(', ')} — ${nImg} image(s) de l'overlay`);
    const parImage = new Set(images.map(([r]) => r));
    const motifs = Object.keys(tbl).filter(r => voulues.has(r) && tbl[r].m && !parImage.has(r));
    if(motifs.length){
      const n = await poserMotifs(b, c, motifs, tbl, base);
      if(n) chatLog(`🧩 overlay ${ov.nom} : ${motifs.join(', ')} — ${n.toLocaleString('fr')} pixels de motif`);
    }
    src = c;
  }
  cv.getContext('2d').drawImage(src, ov.x - sx, ov.y - sy);
  return src;
}

// Pixels d'un overlay posé (repère du board non recadré), lus par la LOS
// pour en tirer terrain et niveaux (ASLMap.adjustLOSForOverlays).
function _pixelsOverlay(nom, image, x, y){
  const c = document.createElement('canvas');
  c.width = image.width; c.height = image.height;
  const g = c.getContext('2d', {willReadFrequently:true});
  g.drawImage(image, 0, 0);
  return {nom, x, y, w: c.width, h: c.height, data: g.getImageData(0, 0, c.width, c.height).data};
}

// Image d'un board telle que la compose ASLBoard.BoardOp : la partie
// visible (recadrée) au format natif, puis les motifs, les images SSR et
// les overlays, puis le repeint des SSR sur l'ensemble. Le retournement et
// le zoom viennent après, au dessin sur la carte.
async function composerBoard(b, img){
  const gb = geoBoard(b);
  const sx = b.crop ? b.crop.x : 0, sy = b.crop ? b.crop.y : 0;
  const sw = (b.crop && b.crop.w > 0) ? b.crop.w : gb.w;
  const sh = (b.crop && b.crop.h > 0) ? b.crop.h : gb.h;
  const cv = document.createElement('canvas');
  cv.width = sw; cv.height = sh;
  cv.getContext('2d').drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh);
  const voulues = new Set(b.ssrList || []);
  // Ceux de la sauvegarde (Add overlays), puis ceux des pièces de
  // l'extension VASL Overlays, posés de la même façon.
  const overlays = [...(b.overlays || []), ...(b.overlaysExt || [])];
  b.ovrLOS = [];
  b.ovrDebords = [];
  if(!voulues.size && !overlays.length) return cv;
  let tbl = LOS_SSR, couleurs = [];
  if(voulues.size){
    try {
      tbl = await reglesBoard(b);
      // Dans l'ordre du fichier de métadonnées, pas dans celui de la sauvegarde.
      const ordre = Object.keys(tbl).filter(r => voulues.has(r));
      const images = ((gb.meta && gb.meta.overlays) || []).filter(([r]) => voulues.has(r));
      // SSRFilter retire de la liste les règles servies par une image :
      // elles ne passent plus par l'underlay.
      const parImage = new Set(images.map(([r]) => r));
      const motifs = ordre.filter(r => tbl[r].m && !parImage.has(r));
      couleurs = ordre.filter(r => tbl[r].c);
      if(motifs.length){
        const n = await poserMotifs(b, cv, motifs, tbl);
        if(n) chatLog(`🧩 bd${b.name} : ${motifs.join(', ')} — ${n.toLocaleString('fr')} pixels de motif`);
        else  chatLog(`⚠️ bd${b.name} : aucun terrain à couvrir pour ${motifs.join(', ')}`);
      }
      if(images.length){
        const n = await poserImagesSSR(b, cv, images, sx, sy);
        const lib = r => gb.libelles && gb.libelles[r] ? `${r} (${gb.libelles[r]})` : r;
      if(n) chatLog(`🖼️ bd${b.name} : ${[...parImage].map(lib).join(', ')} — ${n} image(s) du board`);
      }
    } catch(e){
      chatLog(`⚠️ SSR sur bd${b.name} impossible : ${e.message}`);
    }
  }
  // Overlays d'hexagones, dans l'ordre de la sauvegarde, sur les SSR.
  // Ce qui dépasse du board (overlay posé à cheval sur deux boards) est
  // gardé à part : _dessinerBoard le pose sur le board voisin.
  b.ovrDebords = [];
  for(const o of overlays){
    try {
      const ov = await preparerOverlay(b, o);
      const img = await dessinerOverlay(b, cv, ov, tbl, voulues, sx, sy);
      b.ovrLOS.push(Object.assign(_pixelsOverlay(o.nom, img, ov.x, ov.y), {archive: ov.archive}));
      if(ov.x < sx || ov.y < sy || ov.x + img.width > sx + sw || ov.y + img.height > sy + sh){
        const c = document.createElement('canvas');
        c.width = img.width; c.height = img.height;
        c.getContext('2d').drawImage(img, 0, 0);
        b.ovrDebords.push({nom: o.nom, image: c, x: ov.x, y: ov.y});
      }
      ov.image.close();
      chatLog(`🧱 bd${b.name} : overlay ${o.nom} en ${o.hex1}-${o.hex2} (${ov.archive}, orientation ${ov.orientation})`
        + (o.piece ? ` — pièce ${o.piece} de l'extension VASL Overlays` : ''));
    } catch(e){
      chatLog(`⚠️ bd${b.name} : overlay ${o.nom} non posé — ${e.message}`);
    }
  }
  // Le repeint porte sur toute l'image : board, motifs, images et overlays.
  if(couleurs.length){
    try {
      const t = tableSSR(couleurs, tbl);
      const n = repeindre(cv, t);
      for(const d of b.ovrDebords) repeindre(d.image, t);
      if(n) chatLog(`🎨 bd${b.name} : ${couleurs.join(', ')} — ${n.toLocaleString('fr')} pixels repeints`);
      else  chatLog(`⚠️ bd${b.name} : aucune couleur à repeindre pour ${couleurs.join(', ')}`);
    } catch(e){
      chatLog(`⚠️ SSR sur bd${b.name} impossible : ${e.message}`);
    }
  }
  return cv;
}

// ═══ RENDU DE LA CARTE ═══
async function _renderBoards(boards){
  const noms = [...new Set(boards.map(b => b.name))];
  chatLog(`⏳ Téléchargement de ${noms.length} board(s)…`);
  await _runLimited(noms, n => chargerGeoBoard(n));
  const divers = noms.filter(n => {
    const g = _geoBoards.get(n);
    return g && !g.defaut && (g.cols !== HEX_COLS || g.rows !== HEX_ROWS);
  });
  if(divers.length)
    chatLog('📐 Board(s) non standard : ' + divers.map(n => {
      const g = _geoBoards.get(n);
      return `${n} — ${g.cols}×${g.rows} hex, ${g.w}×${g.h} px`;
    }).join(' · '));

  const mi = document.getElementById('mapInner');
  mi.querySelectorAll('.boardLayer').forEach(e => e.remove());
  _oublierTables();
  document.getElementById('vide').style.display = 'none';

  const {totalW, totalH} = _layoutBoards(boards);
  oublierCarteLOS(boards);
  const ssrTot = [...new Set(boards.flatMap(b => b.ssrList || []))];
  if(ssrTot.length){
    chatLog(`🌾 Modifications de terrain (SSR) : ${ssrTot.join(', ')}`);
    // Une règle sans effet graphique en général peut avoir une image, une
    // couleur ou un motif propre à l'un des boards.
    const propre = new Set(boards.flatMap(b => {
      const m = geoBoard(b).meta;
      return m ? [...m.overlays, ...m.colorSSR, ...m.underlays].map(([r]) => r) : [];
    }));
    const losSeule  = ssrTot.filter(r => !LOS_SSR[r] && !propre.has(r) && SSR_LOS_SEULES[r]);
    const inconnues = ssrTot.filter(r => !LOS_SSR[r] && !propre.has(r) && !SSR_LOS_SEULES[r]);
    if(losSeule.length)  chatLog('ℹ️ Sans effet graphique (visée seule) : ' + losSeule.join(', '));
    if(inconnues.length) chatLog('⚠️ SSR non reconnue(s), terrain inchangé : ' + inconnues.join(', '));
  }

  _setMapSize(Math.round((totalW + BOARD_MARGIN*2) * SC), Math.round((totalH + BOARD_MARGIN*2) * SC));
  _buildHexTable(boards);
  const M = Math.round(BOARD_MARGIN * SC);
  // Zone de la carte : l'emprise des boards affichés, sans la marge.
  _empriseBoards = {x: M, y: M, w: Math.round(totalW * SC), h: Math.round(totalH * SC)};
  _mapBase = {w: _mapW, h: _mapH};
  let ok = 0; const fail = [];

  await _runLimited(noms, n => _fetchBoardImage(n));

  for(const b of boards){
    const x = Math.round(b.px * SC) + M, y = Math.round(b.py * SC) + M;
    const w = Math.round(b.dispW * SC), hgt = Math.round(b.dispH * SC);
    try {
      // Le recadrage et le miroir imposent un canvas plutôt qu'un <img>.
      const cv = document.createElement('canvas');
      cv.className = 'boardLayer';
      b._kb = _sizeCanvasSafely(cv, b.dispW, b.dispH);
      cv.style.cssText = `left:${x}px;top:${y}px;width:${w}px;height:${hgt}px;`;
      b._cv = cv;
      await _peindreBoard(b);
      mi.appendChild(cv);
      ok++;
    } catch(err){
      b._cv = null;
      fail.push(b.name + (err && err.message ? ' (' + err.message + ')' : ''));
      const ph = document.createElement('div');
      ph.className = 'boardLayer';
      ph.style.cssText = `left:${x}px;top:${y}px;width:${w}px;height:${hgt}px;`
        + 'background:#222;border:1px dashed #666;';
      mi.appendChild(ph);
    }
  }
  // Une fois tous les boards composés : les débords de leurs overlays.
  for(const b of boards) if(b._cv) for(const l of _dessinerBoard(b)) chatLog('🧱 ' + l);
  if(ok) chatLog(`🗺️ ${ok} board(s) — composition ${totalW}×${totalH} px`);
  if(fail.length) chatLog('⚠️ Board(s) non chargé(s) : ' + fail.join(', '));
  zoomFit();
}

// Compose un board et le peint dans son canvas : au chargement, puis à
// nouveau quand ses overlays de l'extension changent.
async function _peindreBoard(b){
  const im = new Image();
  await new Promise((res, rej) => { im.onload = res; im.onerror = rej; _fetchBoardImage(b.name).then(u => im.src = u, rej); });
  b._comp = await composerBoard(b, im);
  _dessinerBoard(b);
}

// Peint dans son canvas l'image composée d'un board, puis ce qui y
// déborde des overlays des boards voisins. Rend les débords posés.
function _dessinerBoard(b){
  const cv = b._cv, g = cv.getContext('2d');
  g.setTransform(1, 0, 0, 1, 0, 0);
  g.clearRect(0, 0, cv.width, cv.height);
  if(b._kb !== 1) g.scale(b._kb, b._kb);
  if(b.reversed){ g.translate(b.dispW, b.dispH); g.scale(-1, -1); }
  g.drawImage(b._comp, 0, 0, b.dispW, b.dispH);
  return _poserDebordsOverlays(b, _losBoards || []);
}

// Overlay d'hexagones à cheval sur deux boards : VASL le coupe au bord de
// son board ; on pose la partie qui dépasse sur les boards voisins, par-
// dessus leur image et leurs propres overlays. Le repère est celui du
// board de l'overlay (recadrage, zoom, retournement), ramené à la carte.
function _poserDebordsOverlays(B, boards){
  const poses = [];
  for(const b of boards){
    if(b === B || !b.ovrDebords || !b.ovrDebords.length) continue;
    const gb = geoBoard(b);
    const sx = b.crop ? b.crop.x : 0, sy = b.crop ? b.crop.y : 0;
    const sw = (b.crop && b.crop.w > 0) ? b.crop.w : gb.w;
    const sh = (b.crop && b.crop.h > 0) ? b.crop.h : gb.h;
    const kx = b.dispW / sw, ky = b.dispH / sh;
    for(const d of b.ovrDebords){
      // Emprise de l'overlay sur la carte (pixels affichés, avant SC).
      const lx = d.x - sx, ly = d.y - sy, w = d.image.width, h = d.image.height;
      const x0 = b.px + (b.reversed ? sw - lx - w : lx) * kx;
      const y0 = b.py + (b.reversed ? sh - ly - h : ly) * ky;
      const x1 = x0 + w * kx, y1 = y0 + h * ky;
      if(x1 <= B.px || x0 >= B.px + B.dispW || y1 <= B.py || y0 >= B.py + B.dispH) continue;
      const g = B._cv.getContext('2d');
      g.save();
      g.setTransform(B._kb, 0, 0, B._kb, -B.px * B._kb, -B.py * B._kb);
      g.translate(b.px, b.py);
      if(b.reversed){ g.translate(b.dispW, b.dispH); g.scale(-1, -1); }
      g.scale(kx, ky);
      g.drawImage(d.image, lx, ly);
      g.restore();
      poses.push(`overlay ${d.nom} de bd${b.name} prolongé sur bd${B.name}`);
    }
  }
  return poses;
}

function _setMapSize(w, h){
  _mapW = w; _mapH = h;
  const mi = document.getElementById('mapInner');
  mi.style.width = w + 'px'; mi.style.height = h + 'px';
  _majScaler();
}
function _majScaler(){
  const s = document.getElementById('mapScaler');
  let {w, h} = _tailleVue();
  // Vue tournée : les tables QRDC restent droites, sous la carte affichée.
  const t = _tablesEl && _tablesEl.isConnected && _rotVue ? _tablesEl : null;
  if(t){
    const ecart = Math.round(ECART_TABLES * SC), x = Math.round(48 * SC);
    Object.assign(t.style, {left: x * zoom + 'px', top: (h + ecart) * zoom + 'px', transform: `scale(${zoom})`, transformOrigin: '0 0'});
    w = Math.max(w, x + t.offsetWidth + ecart);
    h += ecart + t.offsetHeight + ecart;
  }
  s.style.width  = Math.round(w * zoom) + 'px';
  s.style.height = Math.round(h * zoom) + 'px';
  // La carte tourne autour de son coin, puis revient dans la zone de
  // défilement : la vue garde son origine en haut à gauche.
  const W = _mapW * zoom, H = _mapH * zoom;
  const [tx, ty] = {90: [H, 0], 180: [W, H], 270: [0, W]}[_rotVue] || [0, 0];
  const mi = document.getElementById('mapInner');
  mi.style.transform = (_rotVue ? `translate(${tx}px, ${ty}px) rotate(${_rotVue}deg) ` : '') + `scale(${zoom})`;
  mi.style.setProperty('--rotVue', _rotVue + 'deg');
}

// ── Rotation de la vue. « Vue » : la carte telle qu'elle est affichée,
// tournée, au zoom 1, origine en haut à gauche ; « carte » : pixels de
// #mapInner. Les pions et les textes restent droits à l'écran (règle
// .pion de la feuille de style) ; ce qui est dans un trait rotate
// (Covered Arc, véhicule) suit la carte, pour rester face à un côté
// d'hexagone.
const _tailleVue = () => _rotVue % 180 ? {w: _mapH, h: _mapW} : {w: _mapW, h: _mapH};
function _carteVersVue(x, y){
  switch(_rotVue){
    case 90:  return {x: _mapH - y, y: x};
    case 180: return {x: _mapW - x, y: _mapH - y};
    case 270: return {x: y, y: _mapW - x};
    default:  return {x, y};
  }
}
function _vueVersCarte(x, y){
  switch(_rotVue){
    case 90:  return {x: y, y: _mapH - x};
    case 180: return {x: _mapW - x, y: _mapH - y};
    case 270: return {x: _mapW - y, y: x};
    default:  return {x, y};
  }
}
// Un décalage vu à l'écran (dx vers la droite, dy vers le bas), dans le
// repère de la carte ; et l'inverse.
function _ecartVersCarte(dx, dy){
  switch(_rotVue){
    case 90:  return {x: dy, y: -dx};
    case 180: return {x: -dx, y: -dy};
    case 270: return {x: -dy, y: dx};
    default:  return {x: dx, y: dy};
  }
}
function _ecartVersVue(dx, dy){
  switch(_rotVue){
    case 90:  return {x: -dy, y: dx};
    case 180: return {x: -dx, y: -dy};
    case 270: return {x: dy, y: -dx};
    default:  return {x: dx, y: dy};
  }
}

// Tourne la vue de `pas` degrés (multiple de 90, horaire si positif), en
// gardant au centre de l'écran le point de la carte qui y était.
function tournerVue(pas){
  const wrap = document.getElementById('mapWrap');
  const centre = _mapW && wrap.clientWidth
    ? _vueVersCarte((wrap.scrollLeft + wrap.clientWidth/2) / zoom, (wrap.scrollTop + wrap.clientHeight/2) / zoom) : null;
  _rotVue = (((_rotVue + pas) % 360) + 360) % 360;
  try { localStorage.setItem('aslweb.rotation', String(_rotVue)); } catch(e){}
  _placerTables();
  _etendreCarte(true);
  _majScaler();
  if(centre && !_zoomManuel) zoomFit();
  else if(centre){
    const v = _carteVersVue(centre.x, centre.y);
    wrap.scrollLeft = v.x * zoom - wrap.clientWidth/2;
    wrap.scrollTop  = v.y * zoom - wrap.clientHeight/2;
  }
  _noterCentre();
  rendreToutes();
  if(_fil) dessinerFil();
  if(_liens.size) dessinerLiens();
  if(_eventail) placerEventail();
  rafraichirActions(['rotGauche', 'rotDroite', 'rot180', 'rot0']);
}

// ═══ ZOOM ET DÉPLACEMENT ═══
function setZoom(z, fx, fy){
  z = Math.max(0.1, Math.min(3, z));
  const wrap = document.getElementById('mapWrap');
  const wr = wrap.getBoundingClientRect();
  if(fx === undefined){ fx = wr.left + wr.width/2; fy = wr.top + wr.height/2; }
  const wx = (wrap.scrollLeft + (fx - wr.left)) / zoom;
  const mh = typeof margeHautCarte === 'function' ? margeHautCarte() : 0;
  const wy = (wrap.scrollTop - mh + (fy - wr.top )) / zoom;
  const loin = _effectifPiles && _vueDeLoin();
  zoom = z;
  if(!_zoomAuto) _zoomManuel = true;
  const mi = document.getElementById('mapInner');
  mi.style.setProperty('--zinv', 1 / zoom);
  _majScaler();
  wrap.scrollLeft = wx*zoom - (fx - wr.left);
  wrap.scrollTop  = wy*zoom + mh - (fy - wr.top);
  rafraichirActions(['zoomTxt']);
  if(_fil) dessinerFil();
  if(_liens.size) dessinerLiens();
  if(_eventail) placerEventail();
  // L'effectif des piles ne se montre que de loin.
  if(_effectifPiles && loin !== _vueDeLoin()) rendreToutes();
}
function zoomIn(){  setZoom(zoom*1.25); }
function zoomOut(){ setZoom(zoom/1.25); }
// Zoom choisi par le joueur, ou seulement celui d'« Ajuster » : après une
// rotation de l'écran, on n'ajuste de nouveau que dans ce second cas.
let _zoomManuel = false, _zoomAuto = false;
function zoomFit(){
  if(!_mapW) return;
  const wrap = document.getElementById('mapWrap');
  _zoomAuto = true;
  // Les tables restent sous la zone ajustée : on y descend en défilant.
  // (Vue tournée : les tables tournent avec la carte, on ajuste le tout.)
  const t = !_rotVue && rectTables();
  const v = _tailleVue();
  const h = t ? Math.min(v.h, t.y) : v.h;
  try { setZoom(Math.min(wrap.clientWidth/v.w, wrap.clientHeight/h)); } finally { _zoomAuto = false; }
  _zoomManuel = false;
  wrap.scrollLeft = 0; wrap.scrollTop = 0;
}

// Fenêtre redimensionnée ou écran tourné : on garde le point de la carte
// qui était au centre de la vue, ou l'on ajuste si le joueur n'a pas zoomé.
let _centreVue = null, _redim = null;
function _noterCentre(){
  const w = document.getElementById('mapWrap');
  if(_mapW && w.clientWidth) _centreVue = {x: (w.scrollLeft + w.clientWidth/2) / zoom, y: (w.scrollTop + w.clientHeight/2) / zoom};
}
function _surRedimension(){
  clearTimeout(_redim);
  _redim = setTimeout(() => {
    _redim = null;
    const w = document.getElementById('mapWrap');
    if(_mapW && w.clientWidth){
      if(!_zoomManuel) zoomFit();
      else if(_centreVue){
        w.scrollLeft = _centreVue.x * zoom - w.clientWidth/2;
        w.scrollTop  = _centreVue.y * zoom - w.clientHeight/2;
      }
    }
    _noterCentre();
    for(const f of _apresRedimension) f();
  }, 200);
}
const _apresRedimension = [];
const _apresProfil = [];          // (p, ancien) après un changement de profil

// Sélection d'un pion à l'autre d'une pile (Alt + molette) : +1 vers le
// sommet, −1 vers le fond, en boucle.
function tournerDansPile(p, sens){
  if(!p) return;
  const k = cle(p), s = pile(k).filter(selectionnable);
  if(!s.length) return;
  deplier(k, true);
  const cur = s.findIndex(q => _sel.has(q) && selDans(k).length === 1);
  const i = cur < 0 ? (sens > 0 ? 0 : s.length - 1) : (cur + sens + s.length) % s.length;
  selectionner([s[i]]);
}

// Double appui dans le vide, au doigt : un Flare au point touché (voir
// js/flare.js), comme l'Alt+clic de VASL. Le zoom passe par le pincement.
let _dernierVide = {t: 0, x: 0, y: 0};
function _doubleAppuiVide(e){
  const d = Date.now() - _dernierVide.t < 320 && Math.hypot(e.clientX - _dernierVide.x, e.clientY - _dernierVide.y) < 30;
  _dernierVide = d ? {t: 0, x: 0, y: 0} : {t: Date.now(), x: e.clientX, y: e.clientY};
  if(!d) return false;
  const c = clientVersCarte(e.clientX, e.clientY, null, true);
  if(c) lancerFlare(c.x, c.y);
  return true;
}

// Clic dans le vide de la carte : comme KeyBufferer, la sélection se vide.
// Au doigt, les piles dépliées se replient aussi ; à la souris, elles
// restent dépliées, comme dans VASSAL (Échap les replie).
function _clicVide(e){
  if(_filArme) return;
  // Le chat ouvert recule d'un cran : un appui qui vient de fermer le
  // clavier s'arrête là ; sinon le chat compact revient à l'aperçu.
  if(_etatChat === 'compact'){
    if(Date.now() - _clavierFermeA > 800) fermerChat();
    return;
  }
  if(_modeCarte){ toucherModeCarte(clientVersCarte(e.clientX, e.clientY, null, true)); return; }
  if(e.pointerType && e.pointerType !== 'mouse'){
    // Sélection tolérante : la pile la plus proche, à moins de 30 px.
    const k = pileProche(e.clientX, e.clientY);
    // L'appui qui vient de fermer l'éventail ferme aussi la pile, sans en
    // reprendre une voisine.
    if(Date.now() - _eventailFermeA < 800){ _eventailFermeA = 0; replierTout(); if(_sel.size || _pileSel) selectionnerPile(null); return; }
    if(k){ appuiSurPile(k); return; }
    if(_doubleAppuiVide(e)) return;
    replierTout();
    fermerEventail();
  }
  if(_sel.size || _pileSel) selectionnerPile(null);
}

// Rectangle de sélection (Maj + glissé dans le vide). Dans VASSAL, c'est
// un simple glissé ; ici, le glissé déplace la carte, d'où la touche Maj.
// Comme KBDeckVisitor : une pile repliée entre tout entière si son point
// est dans le rectangle ; d'une pile dépliée, chaque pion dont le point est
// dedans. Ctrl + Maj retire de la sélection au lieu d'y ajouter.
function _lasso(e){
  e.preventDefault();
  const x0 = e.clientX, y0 = e.clientY, id = e.pointerId;
  const r = document.createElement('div');
  r.className = 'lasso';
  document.body.appendChild(r);
  const peindre = ev => {
    Object.assign(r.style, {left: Math.min(x0, ev.clientX) + 'px', top: Math.min(y0, ev.clientY) + 'px',
      width: Math.abs(ev.clientX - x0) + 'px', height: Math.abs(ev.clientY - y0) + 'px'});
  };
  peindre(e);
  const bouge = ev => { if(ev.pointerId === id){ ev.preventDefault(); peindre(ev); } };
  const fin = ev => {
    if(ev.pointerId !== id) return;
    document.removeEventListener('pointermove', bouge);
    document.removeEventListener('pointerup', fin);
    document.removeEventListener('pointercancel', fin);
    r.remove();
    if(ev.type !== 'pointerup') return;
    const c0 = clientVersCarte(x0, y0, null, true);
    const c1 = clientVersCarte(ev.clientX, ev.clientY, null, true);
    if(!c0 || !c1) return;
    // Coins opposés, quelle que soit la rotation de la vue.
    const a = {x: Math.min(c0.x, c1.x), y: Math.min(c0.y, c1.y)}, b = {x: Math.max(c0.x, c1.x), y: Math.max(c0.y, c1.y)};
    const dedans = (x, y) => x >= a.x && x <= b.x && y >= a.y && y <= b.y;
    const pris = [];
    for(const [k, l] of indexerPiles()){
      const vus = l.filter(p => !p.cache);
      if(_depliees.has(k)){
        rectsPile(vus, true).forEach((q, i) => {
          if(dedans((q.x + q.w/2) / SC, (q.y + q.h/2) / SC)) pris.push(vus[i]);
        });
      } else if(dedans(l[0].x, l[0].y)) pris.push(...vus);
    }
    const choisis = pris.filter(p => selectionnable(p) && COUCHES_SEL[p.couche || 'unite']);
    selectionner(choisis, _basculeSel(ev) ? 'retirer' : 'ajouter');
  };
  document.addEventListener('pointermove', bouge);
  document.addEventListener('pointerup', fin);
  document.addEventListener('pointercancel', fin);
}

// Les pointeurs posés sur la carte, pions compris : un pincement qui
// commence sur un pion zoome, et abandonne le glissé du pion.
const _pointeursCarte = new Map();
function _setupPanZoom(){
  const wrap = document.getElementById('mapWrap');
  const pts = _pointeursCarte;
  let pinch = null;
  wrap.addEventListener('pointerdown', e => {
    // Un appui sur la carte ferme le panneau posé par-dessus (liste de la
    // pile, palette), sauf pendant une pose en deux appuis ; le geste continue.
    if(coteRecouvre() && !_modeCarte && !_filArme) fermerCote();
    // Un pion garde son geste (sélection, glissé) : seul, son doigt ne
    // déplace pas la carte.
    const pion = !!(e.target.closest && e.target.closest('.pion'))
      && !(_verrouPions && e.pointerType && e.pointerType !== 'mouse');
    // Maj + glissé dans le vide, à la souris : rectangle de sélection.
    if(!pion && e.shiftKey && e.button === 0 && (e.pointerType === 'mouse' || !e.pointerType)){ _lasso(e); return; }
    // surPion : l'appui a commencé sur un pion ; avec le verrou, son doigt
    // déplace la carte (pion faux) mais ce n'est pas un appui dans le vide.
    const surPion = !!(e.target.closest && e.target.closest('.pion'));
    pts.set(e.pointerId, {x:e.clientX, y:e.clientY, pion, surPion, x0: e.clientX, y0: e.clientY, t0: Date.now(), btn: e.button});
    if(!pion){ try { wrap.setPointerCapture(e.pointerId); } catch(err){} }
    if(!pion || pts.size > 1) wrap.classList.add('pan');
    if(pts.size === 2){
      if(_glisse) _glisse.abandonner();
      for(const q of pts.values()) q.pion = false;
      const [a, b] = [...pts.values()];
      pinch = {d: Math.hypot(a.x-b.x, a.y-b.y), z: zoom};
    }
  });
  window.addEventListener('pointermove', e => {
    const p = pts.get(e.pointerId);
    if(!p) return;
    if(pts.size === 1 && !p.pion){
      wrap.scrollLeft -= e.clientX - p.x;
      wrap.scrollTop  -= e.clientY - p.y;
    }
    p.x = e.clientX; p.y = e.clientY;
    if(pts.size === 2 && pinch){
      const [a, b] = [...pts.values()];
      const d = Math.hypot(a.x-b.x, a.y-b.y);
      if(pinch.d > 0) setZoom(pinch.z * d / pinch.d, (a.x+b.x)/2, (a.y+b.y)/2);
    }
  });
  let pince = false;
  const fin = e => {
    const p = pts.get(e.pointerId);
    if(pts.size > 1) pince = true;
    pts.delete(e.pointerId);
    if(pts.size < 2) pinch = null;
    if(!pts.size) wrap.classList.remove('pan');
    // Un appui bref dans le vide, sans glisser ni pincer : clic sur la carte.
    if(p && !p.pion && !p.surPion && e.type === 'pointerup' && !pince && p.btn === 0
       && Math.hypot(e.clientX - p.x0, e.clientY - p.y0) < 6) _clicVide(e);
    if(!pts.size) pince = false;
  };
  window.addEventListener('pointerup', fin);
  window.addEventListener('pointercancel', fin);
  wrap.addEventListener('scroll', () => { if(!_redim) _noterCentre(); });
  // Molette, comme dans VASL : Ctrl + molette zoome (Cmd + molette sur Mac
  // et iPad, et le pincement du pavé tactile, que le navigateur signale par
  // ctrlKey) ; la molette seule fait défiler la carte, la molette
  // horizontale (ou Maj + molette) la fait défiler de côté. Au doigt, la
  // molette verticale seule zoome toujours.
  wrap.addEventListener('wheel', e => {
    // Alt + molette sur une pile : la sélection passe d'un pion à l'autre
    // de la pile, dépliée au besoin.
    const pion = e.altKey && e.target.closest && e.target.closest('.pion');
    if(pion){ e.preventDefault(); tournerDansPile(pions.find(p => p.el === pion), e.deltaY > 0 ? -1 : 1); return; }
    if(e.ctrlKey || e.metaKey){
      e.preventDefault();
      // Un cran de molette (~100 px) : ×1,1 ; le pavé tactile, par petits pas.
      const d = e.deltaY * (e.deltaMode === 1 ? 33 : e.deltaMode === 2 ? 800 : 1);
      setZoom(zoom * Math.pow(1.1, Math.max(-3, Math.min(3, -d / 100))), e.clientX, e.clientY);
      return;
    }
    // Défilement natif du navigateur (vertical, horizontal, Maj + molette).
    if(_profil === 'pc' || e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) return;
    e.preventDefault();
    if(e.deltaY) setZoom(zoom * (e.deltaY < 0 ? 1.1 : 1/1.1), e.clientX, e.clientY);
  }, {passive:false});
  // Pincement du pavé tactile dans Safari (Mac, iPad avec trackpad) : il
  // n'envoie pas Ctrl + molette mais des GestureEvent. Un pincement au
  // doigt passe par les pointeurs (plus haut) : ignoré ici.
  let zoomGeste = null;
  wrap.addEventListener('gesturestart', e => { e.preventDefault(); zoomGeste = pts.size ? null : zoom; });
  wrap.addEventListener('gesturechange', e => {
    e.preventDefault();
    if(zoomGeste !== null && !pts.size) setZoom(zoomGeste * e.scale, e.clientX, e.clientY);
  });
  wrap.addEventListener('gestureend', e => { e.preventDefault(); zoomGeste = null; });
}

