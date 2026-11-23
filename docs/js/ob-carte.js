// ═══════════════════════════════════════════════════════════════
// LA CARTE D'UN SCÉNARIO — le schéma des boards de la fiche (PDF)
// Cases du schéma et lecture de leurs numéros : schema.py du prototype
// (traits longs de max(W,H)/40, aire, rapport des côtés, remplissage ;
// numéro lu dans les 4 rotations, texte épars, confiance > 60).
// Ajouté d'après les instructions (le prototype s'arrêtait là) :
// - les aplats rouges (hexrangées hors jeu) sont effacés avant les traits ;
// - doublons (bord d'une case et de son trou) écartés ;
// - seul le schéma est gardé : au-dessus des cadres d'OB, le groupe de
//   cases jointives qui porte le plus de numéros lus ;
// - numéro non lu : on isole la tache d'encre et on la relit ;
// - sens : le numéro est au bout gauche d'un board non retourné et se lit
//   en tournant de 90° horaire → lu à 90° = normal, à 270° = retourné.
//   Boards dessinés debout : toute la carte pivote de 90° horaire (nord à
//   droite) et chaque angle perd 90°. Lu à 0° ou 180° : sens incertain.
// - places : centres regroupés en colonnes et rangées.
// Rien n'est choisi à la place du joueur : une case non lue reste vide et
// signalée, la grille se corrige à la main.
// ═══════════════════════════════════════════════════════════════
const obCarte = (() => {
const O = obImage;
const lire = (im, psm, liste) => obOcr.lire(im, {psm, liste, donnees: true});

// Gris de la fiche, aplats rouges éclaircis : un pixel du masque rouge
// prend sa valeur (canal le plus clair) — l'aplat devient blanc, les traits
// noirs tracés par-dessus restent sombres.
function grisSansRouge(rgba, g){
  const t = O.hsv(rgba), d = g.d.slice();
  for(let i = 0; i < d.length; i++) if((t.H[i] < 12 || t.H[i] > 165) && t.S[i] > 70) d[i] = t.V[i];
  return {w: g.w, h: g.h, d};
}
function casesSchema(g){
  const W = g.w, H = g.h, L = Math.floor(Math.max(W, H) / 40);
  const bw = O.binariserOtsu(g, true).img;
  const grid = O.dilater(O.ou(O.ouvrir(bw, L, 1), O.ouvrir(bw, 1, L)), 3, 3);
  const cells = [];
  for(const c of O.contours(O.inverser(grid), 'ccomp')){
    const a = c.w * c.h;
    if(a < W * H / 2000 || a > W * H / 15) continue;
    if(c.aire < 0.85 * a) continue;
    const r = Math.max(c.w, c.h) / Math.min(c.w, c.h);
    if(1.3 < r && r < 4.5) cells.push([c.x, c.y, c.w, c.h]);
  }
  // doublons : une case qui en contient une autre presque aussi grande
  const dedans = (a, b) => b[0] >= a[0] && b[1] >= a[1] && b[0] + b[2] <= a[0] + a[2] && b[1] + b[3] <= a[1] + a[3];
  // cadre qui en englobe d'autres (tour du schéma), ou doublon d'une case
  return cells.filter(a => !cells.some(b => b !== a && dedans(a, b) && b[2] * b[3] >= 0.8 * a[2] * a[3])
    && cells.filter(b => b !== a && dedans(a, b)).length < 2);
}

// Numéro d'une case : schema.py (4 rotations, texte épars, confiance > 60),
// puis la tache d'encre isolée. Chaque lecture garde sa place dans la case
// (centre du mot ramené dans la découpe) : le sens se vérifie ensuite.
const ROTS = [[0, null], [90, 'horaire'], [180, '180'], [270, 'antihoraire']];
// point (X, Y) d'une image tournée (rot) puis agrandie (k) → image de départ (bw × bh)
function versDepart(X, Y, rot, k, bw, bh){
  const x = X / k, y = Y / k;
  if(rot === 'horaire') return [y, bh - 1 - x];
  if(rot === 'antihoraire') return [bw - 1 - y, x];
  if(rot === '180') return [bw - 1 - x, bh - 1 - y];
  return [x, y];
}
// Compte des chiffres autour du mot lu : taches de sa hauteur, contiguës.
// Un trait étroit et plein est un « 1 » (Tesseract le perd : 17 → « 7 »).
// → texte corrigé, ou null si le compte contredit la lecture.
function verifierChiffres(im, m, texte){
  const hm = m.h;
  const y0 = Math.max(0, Math.floor(m.y - .3 * hm)), y1 = Math.min(im.h, Math.ceil(m.y + 1.3 * hm));
  const x0 = Math.max(0, Math.floor(m.x - 1.5 * hm)), x1 = Math.min(im.w, Math.ceil(m.x + m.w + 1.5 * hm));
  const z = O.tranche(im, y0, y1, x0, x1);
  const cc = O.composantes(O.binariserOtsu(z, true).img, 8), cyM = m.y + hm / 2 - y0;
  const cs = [];
  for(let i = 1; i < cc.n; i++){
    const [x, y, w, h, a] = cc.st[i];
    if(h < .55 * hm || h > 1.6 * hm || w > 1.2 * hm || Math.abs(y + h / 2 - cyM) > .4 * hm) continue;
    cs.push({x, w, un: w <= .42 * h && a >= .45 * w * h});
  }
  cs.sort((a, b) => a.x - b.x);
  // chaîne contiguë qui recouvre le mot
  const dedans = c => c.x + c.w >= m.x - x0 && c.x <= m.x + m.w - x0;
  let k = cs.findIndex(dedans);
  if(k < 0) return null;
  let d = k, f = k;
  while(d > 0 && cs[d].x - (cs[d - 1].x + cs[d - 1].w) <= .8 * hm) d--;
  while(f < cs.length - 1 && cs[f + 1].x - (cs[f].x + cs[f].w) <= .8 * hm) f++;
  const ch = cs.slice(d, f + 1);
  // autant de taches que de chiffres : un trait étroit est un « 1 » (1 / 4 / 7)
  if(ch.length === texte.length) return ch.map((c, j) => c.un ? '1' : texte[j]).join('');
  const autres = ch.filter(c => !c.un).length;
  if(!texte.includes('1') && ch.length <= 2 && autres === texte.length){
    let j = 0;
    return ch.map(c => c.un ? '1' : texte[j++]).join('');
  }
  return null;
}
// seuil : confiance minimale (60 pour la lecture de schema.py ; 70 pour la
// tache d'encre isolée, ajoutée au prototype : « 21 » lu « 24 » à 63).
async function lectures(images, psm, seuil = 60){
  const rs = await Promise.all(images.map(({im, psm: p}) => lire(im, p || psm, '0123456789')));
  const out = [];
  rs.forEach((r, k) => {
    const {ang, im, rot, kx, bw, bh, ox, oy} = images[k];
    for(const m of obOcr.mots(r.tsv)){
      const t = m.texte.trim();
      if(!/^\d{1,2}$/.test(t) || !(m.conf > seuil)) continue;
      const v = verifierChiffres(im, m, t);
      if(!v) continue;
      const [cx, cy] = versDepart(m.x + m.w / 2, m.y + m.h / 2, rot, kx, bw, bh);
      out.push({board: v, lu: t, angle: ang, conf: m.conf, cx: cx + ox, cy: cy + oy});
    }
  });
  return out;
}
async function numeroCase(g, [x, y, w, h]){
  const crop = O.tranche(g, y + 4, y + h - 4, x + 4, x + w - 4);
  const de = (base, k, ox, oy, psm) => ROTS.map(([ang, rot]) => ({ang, rot, kx: k, bw: base.w, bh: base.h, ox, oy, psm,
    im: O.redimensionner(rot ? O.pivoter(base, rot) : base, k, k, k === 2 ? 'lineaire' : 'cubique')}));
  let c = await lectures(de(crop, 2, 0, 0), 11);
  if(c.length) return {mode: 'schema', candidats: c, w: crop.w, h: crop.h};
  // tache d'encre : composantes de la taille d'un chiffre, loin des bords
  const bw = O.binariserOtsu(crop, true).img, cc = O.composantes(bw, 8), cote = Math.min(crop.w, crop.h);
  const k = [];
  for(let i = 1; i < cc.n; i++){
    const [cx, cy, cw, ch, a] = cc.st[i];
    if(Math.max(cw, ch) < .08 * cote || Math.max(cw, ch) > .6 * cote || a < 12) continue;
    if(cx <= 1 || cy <= 1 || cx + cw >= crop.w - 1 || cy + ch >= crop.h - 1) continue;
    k.push(i);
  }
  if(!k.length) return {mode: 'tache', candidats: [], w: crop.w, h: crop.h};
  const grand = Math.max(...k.map(i => Math.max(cc.st[i][2], cc.st[i][3])));
  const garde = k.filter(i => Math.max(cc.st[i][2], cc.st[i][3]) >= .5 * grand);
  const x0 = Math.min(...garde.map(i => cc.st[i][0])), y0 = Math.min(...garde.map(i => cc.st[i][1]));
  const x1 = Math.max(...garde.map(i => cc.st[i][0] + cc.st[i][2])), y1 = Math.max(...garde.map(i => cc.st[i][1] + cc.st[i][3]));
  if((x1 - x0) > .5 * crop.w && (y1 - y0) > .5 * crop.h) return {mode: 'tache', candidats: [], w: crop.w, h: crop.h};
  const tx = Math.max(0, x0 - 3), ty = Math.max(0, y0 - 3);
  const z = O.bordure(O.tranche(crop, ty, y1 + 3, tx, x1 + 3), 10, 10, 10, 10);
  const kz = Math.max(1, Math.min(4, 60 / Math.max(1, Math.min(x1 - x0, y1 - y0))));
  c = await lectures(de(z, kz, tx - 10, ty - 10, 8), 8, 70);
  if(!c.length) c = await lectures(de(z, kz, tx - 10, ty - 10, 10), 10, 70);
  return {mode: 'tache', candidats: c, w: crop.w, h: crop.h};
}

// Groupes de cases jointives (boîtes qui se touchent, à 1 % de la page près).
function groupes(cases, W){
  const tol = Math.max(4, W * .01), n = cases.length, grp = cases.map((_, i) => i);
  const racine = i => grp[i] === i ? i : (grp[i] = racine(grp[i]));
  const touche = (a, b) => a[0] - tol <= b[0] + b[2] && b[0] - tol <= a[0] + a[2] && a[1] - tol <= b[1] + b[3] && b[1] - tol <= a[1] + a[3];
  for(let i = 0; i < n; i++) for(let j = i + 1; j < n; j++) if(touche(cases[i], cases[j])) grp[racine(i)] = racine(j);
  const m = new Map();
  cases.forEach((c, i) => { const r = racine(i); if(!m.has(r)) m.set(r, []); m.get(r).push(i); });
  return [...m.values()];
}
// Valeurs regroupées : indice de groupe pour chaque valeur (écart > seuil).
function rangs(vals, seuil){
  const ordre = vals.map((v, i) => [v, i]).sort((a, b) => a[0] - b[0]);
  const out = new Array(vals.length);
  let k = -1, prec = -Infinity;
  for(const [v, i] of ordre){ if(v - prec > seuil) k++; out[i] = k; prec = v; }
  return out;
}
const nomBoard = n => (+n < 10 ? '0' : '') + (+n);

// fiche : {rgba, gris} ; limiteY : haut du premier cadre d'OB (ou null).
// → {cases: [{cas, board, angle, conf, mode}], vertical, boards:
//   [{nom, numero, col, row, reversed, sens, cas}], vides: [{col, row, cas}],
//   nx, ny}
async function lireSchema(fiche, {limiteY = null} = {}){
  const g0 = fiche.gris, g = grisSansRouge(fiche.rgba, g0);
  let cand = casesSchema(g);
  if(limiteY !== null) cand = cand.filter(c => c[1] + c[3] <= limiteY);
  const lus = await Promise.all(cand.map(c => numeroCase(g, c)));
  const cases = cand.map((cas, i) => ({cas, lus: lus[i]}));
  if(!cases.length) return {cases, boards: [], vides: [], nx: 0, ny: 0, vertical: false};
  const vertical = cases.filter(c => c.cas[3] > c.cas[2]).length > cases.length / 2;
  // lecture retenue : sens (angle ramené à la carte) et place du numéro
  // d'accord — bout gauche si normal (90°), bout droit si retourné (270°) ;
  // une lecture qui les contredit est la même lue à l'envers (6 / 9).
  for(const c of cases){
    const {candidats, w, h} = c.lus;
    const ok = candidats.map(x => {
      const a = vertical ? (x.angle + 270) % 360 : x.angle;
      const f = vertical ? 1 - x.cy / h : x.cx / w;
      const sens = a === 90 ? 'normal' : a === 270 ? 'retourné' : 'incertain';
      const accord = sens === 'incertain' || (sens === 'normal' ? f < .5 : f > .5);
      return Object.assign({}, x, {sens, accord});
    }).filter(x => x.accord);
    const sur = ok.filter(x => x.sens !== 'incertain');
    const pool = sur.length ? sur : ok;
    const best = pool.reduce((m, x) => !m || x.conf > m.conf ? x : m, null);
    Object.assign(c, best ? {board: best.board, angle: best.angle, conf: best.conf, sens: best.sens, mode: c.lus.mode} : {board: null});
  }
  // le schéma : le groupe jointif qui a le plus de numéros (puis le plus haut)
  const gs = groupes(cand, g.w).map(ix => ({ix, n: ix.filter(i => cases[i].board).length, y: Math.min(...ix.map(i => cand[i][1]))}));
  gs.sort((a, b) => b.n - a.n || a.y - b.y);
  const sch = gs[0].ix.map(i => cases[i]);
  // centres dans le repère de la carte (pivotée de 90° horaire si debout)
  const pts = sch.map(c => {
    const cx = c.cas[0] + c.cas[2] / 2, cy = c.cas[1] + c.cas[3] / 2;
    return vertical ? [-cy, cx] : [cx, cy];
  });
  const larg = sch.map(c => vertical ? c.cas[3] : c.cas[2]), haut = sch.map(c => vertical ? c.cas[2] : c.cas[3]);
  const med = l => l.slice().sort((a, b) => a - b)[Math.floor(l.length / 2)];
  const cols = rangs(pts.map(p => p[0]), med(larg) / 2), rows = rangs(pts.map(p => p[1]), med(haut) / 2);
  const boards = [], vides = [];
  sch.forEach((c, i) => {
    const place = {col: cols[i], row: rows[i], cas: c.cas};
    if(!c.board){ vides.push(place); return; }
    boards.push(Object.assign(place, {nom: nomBoard(c.board), numero: c.board, reversed: c.sens === 'retourné', sens: c.sens, angle: c.angle, conf: c.conf, mode: c.mode}));
  });
  const nx = Math.max(...cols) + 1, ny = Math.max(...rows) + 1;
  return {cases, boards, vides, nx, ny, vertical};
}

// Numéro du scénario : nom du fichier (« ASL019… » → « 19 »), sinon le
// texte de la page (« ASL SCENARIO 19 »).
function numeroScenario(nomFichier, texte = ''){
  const m = /(?:^|[^A-Z0-9])ASL[\s_-]*0*(\d{1,3})(?!\d)/i.exec(nomFichier || '')
    || /ASL\s+SCENARIO\s+0*(\d{1,3})\b/i.exec(texte || '');
  return m ? m[1] : null;
}
// Entrée « 19: Backs To The Sea » d'un SSRControls → « 19 »
const idEntreeSso = nom => { const m = /^\s*([A-Za-z]*\d+[A-Za-z]?)\s*:/.exec(nom || ''); return m ? m[1] : null; };

return {lireSchema, numeroScenario, idEntreeSso, nomBoard, _interne: {casesSchema, numeroCase, grisSansRouge}};
})();
if(typeof module === 'object' && module.exports) module.exports = obCarte;
