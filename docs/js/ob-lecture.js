// ═══════════════════════════════════════════════════════════════
// LECTURE DE L'OB — les pions de la fiche scannée d'un scénario
// Portage du prototype Python (pions.py, obauto.py, ob.py, canons.py,
// vehicules.py), règle par règle : en cas de doute, le prototype fait
// foi. Jamais de choix forcé : un pion ambigu garde ses candidats, un
// pion illisible est rendu tel quel, avec ce qui en a été lu.
// Images : obImage ; OCR : obOcr ; données : OB_REF (ob-reference.js).
// Les lectures d'une même étape partent ensemble (pool de workers), mais
// leurs résultats sont comptés dans l'ordre du prototype : les votes et
// leurs égalités se départagent comme en Python (Counter).
// ═══════════════════════════════════════════════════════════════
const obLecture = (() => {
const O = obImage;

// ── Outils (équivalents Python) ─────────────────────────────────
// Counter : ordre d'insertion ; mostCommon trie de façon stable.
class Compteur {
  constructor(){ this.m = new Map(); }
  ajouter(k, n = 1){ this.m.set(k, (this.m.get(k) || 0) + n); return this; }
  get(k){ return this.m.get(k) || 0; }
  get taille(){ return this.m.size; }
  entrees(){ return [...this.m]; }
  plus(o){                                     // Counter + Counter
    const r = new Compteur();
    for(const [k, n] of this.m){ const v = n + o.get(k); if(v > 0) r.m.set(k, v); }
    for(const [k, n] of o.m) if(!this.m.has(k) && n > 0) r.m.set(k, n);
    return r;
  }
  mostCommon(n){
    const l = [...this.m].sort((a, b) => b[1] - a[1]);
    return n === undefined ? l : l.slice(0, n);
  }
}
const chiffres = s => String(s).replace(/\D/g, '');
const jetons = s => String(s).match(/\d{1,2}-\d{1,2}(?:-\d{1,2})?/g) || [];
const estNombre = s => /^\d+$/.test(s);
function lev(a, b){
  a = [...a]; b = [...b];
  const d = Array.from({length: b.length + 1}, (_, i) => i);
  for(let i = 1; i <= a.length; i++){
    let p = d[0]; d[0] = i;
    for(let j = 1; j <= b.length; j++){
      const t = d[j];
      d[j] = Math.min(d[j] + 1, d[j - 1] + 1, p + (a[i - 1] !== b[j - 1] ? 1 : 0));
      p = t;
    }
  }
  return d[b.length];
}
const ent = Math.trunc;                        // int() de Python
const val = s => s.replace(/\^[^-]*/g, '').replace(/[^\d-]/g, '');   // 6^2-2-8^8 -> 6-2-8
const enOrdre = ps => Promise.all(ps);         // résultats dans l'ordre des appels
// repr() de Python, pour écrire les verdicts comme le prototype
function pyRepr(x){
  if(x === null || x === undefined) return 'None';
  if(x === true) return 'True';
  if(x === false) return 'False';
  if(typeof x === 'number') return String(x);
  if(typeof x === 'string') return "'" + x.replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'";
  if(Array.isArray(x)) return '[' + x.map(pyRepr).join(', ') + ']';
  return '{' + Object.entries(x).map(([k, v]) => pyRepr(k) + ': ' + pyRepr(v)).join(', ') + '}';
}

// Masque des composantes retenues (255), puis recadré sur l'encre avec
// les marges du prototype (y0 - avant : y1 + apres), inversé.
function masque(cc, w, h, garder){
  const d = new Uint8Array(w * h), g = new Uint8Array(cc.n);
  for(const i of garder) g[i] = 1;
  for(let k = 0; k < d.length; k++) if(g[cc.lab[k]]) d[k] = 255;
  return {w, h, d};
}
function recadrer(m, avant, apres){
  const b = O.boiteNonNulle(m);
  if(!b) return null;
  return O.inverser(O.tranche(m, Math.max(0, b.y0 - avant), b.y1 + apres, Math.max(0, b.x0 - avant), b.x1 + apres));
}

// ── OCR (pytesseract.image_to_string) ───────────────────────────
const lire = (im, psm, liste = '') => obOcr.lire(im, {psm, liste});
const WL = '0123456789-?';
// ocr() de ob.py : ×3 cubique, marge blanche de 15
async function ocr(im, psm = 7, wl = WL){
  const t = await lire(O.bordure(O.redimensionner(im, 3, 3, 'cubique'), 15, 15, 15, 15), psm, wl);
  return t.trim().replace(/\n/g, ' ');
}
// _ocr() de canons.py : ×k cubique, marge de 20
async function ocrK(im, psm, wl, k = 4){
  const z = k === 1 ? im : O.redimensionner(im, k, k, 'cubique');
  return (await lire(O.bordure(z, 20, 20, 20, 20), psm, wl)).trim();
}
const lin = (im, k) => O.redimensionner(im, k, k);                    // cv2.resize par défaut
const otsuInv = z => O.binariserOtsu(z, true).img;
const otsuBin = z => O.binariserOtsu(z, false).img;

// ═══ pions.py : cases de pions ══════════════════════════════════
function grille(g, Lh, Lv){
  const bw = otsuInv(g);
  return O.dilater(O.ou(O.ouvrir(bw, Lh, 1), O.ouvrir(bw, 1, Lv)), 3, 3);
}
function casesPions(g){
  const W = g.w, L = Math.floor(W / 60), cases = [];
  for(const c of O.contours(O.inverser(grille(g, L, L)), 'ccomp'))
    if(0.045 * W < c.w && c.w < 0.09 * W && 0.8 < c.h / c.w && c.h / c.w < 1.6 && c.aire > 0.8 * c.w * c.h)
      cases.push([c.x, c.y, c.w, c.h]);
  cases.sort((a, b) => Math.floor(a[1] / 40) - Math.floor(b[1] / 40) || a[0] - b[0]);
  return cases;
}

// ═══ ob.py : petits pions ═══════════════════════════════════════
function sansSoulignes(z){
  const bw = otsuInv(z);
  let lig = O.ouvrir(bw, Math.max(8, Math.floor(z.h / 3)), 1);
  lig = O.dilater(lig, 1, 3);                   // np.ones((3, 1)) : 3 lignes × 1 colonne
  return O.inverser(O.soustraire(bw, lig));
}
async function lecturesBas(bas){
  const h = bas.h, b10 = O.tranche(bas, ent(h * .10));
  const zs = [bas, sansSoulignes(bas), b10, sansSoulignes(b10)];
  const ts = await enOrdre(zs.flatMap(z => [7, 6].map(psm => ocr(z, psm))));
  return ts.filter(t => t);
}

const NAT_SW = {RUSSIAN: 'Russian', GERMAN: 'German', Army: 'American', AMERICAN: 'American', FINNISH: 'Finnish',
  BRITISH: 'British', ITALIAN: 'Italian', JAPANESE: 'Japanese', FRENCH: 'French',
  'ALLIED MINOR': 'Allied Minor', 'AXIS MINOR': 'Axis Minor', Polish: 'Allied Minor', Hungarian: 'Axis Minor'};
const NAT_GUN = {RUSSIAN: 'Russian', GERMAN: 'German', Army: 'American', AMERICAN: 'American', FINNISH: 'Finnish',
  BRITISH: 'British', ITALIAN: 'Italian', JAPANESE: 'Japanese', FRENCH: 'French', 'AXIS MINOR': 'Axis Minor',
  'ALLIED MINOR': 'Allied Minor', CHINESE: 'Chinese'};
const swDe = nat => OB_REF.sw[NAT_SW[nat] || ''] || [];

async function lireSw(c, nat){
  const ch = c.h, cw = c.w;
  const haut = O.tranche(c, 0, ent(ch * 0.42), ent(cw * 0.35));
  const basD = O.tranche(c, ent(ch * 0.62), null, ent(cw * 0.3));
  const basL = O.tranche(c, ent(ch * 0.66));
  const pTyp = [];
  for(const psm of [7, 8]) for(const z of [haut, otsuBin(haut)])
    pTyp.push(lire(lin(z, 3), psm, 'LMGHATRDCFPSKOBZ0123456789'));
  const pVal = [];
  for(const z of [basD, basL]) for(const psm of [7, 8]) for(const zz of [z, sansSoulignes(z)])
    pVal.push(ocr(zz, psm, '0123456789-[]*'));
  const pPP = [lire(lin(c, 3), 11, '0123456789P')];
  const bande = O.tranche(c, ent(ch * .15), ent(ch * .85), ent(cw * .30), ent(cw * .62));
  for(const rot of ['horaire', 'antihoraire']) for(const psm of [7, 11])
    pPP.push(lire(O.redimensionner(O.pivoter(bande, rot), 3, 3, 'cubique'), psm, '0123456789P'));
  const [rTyp, rVal, rPP] = await enOrdre([enOrdre(pTyp), enOrdre(pVal), enOrdre(pPP)]);
  let typ = new Set();
  for(const t of rTyp){ const s = t.trim(); if(s) typ.add(s); }
  const vals = new Set();
  for(const t of rVal){ for(const j of jetons(t)) vals.add(j); if(t) vals.add(t); }
  const pplu = new Set();
  for(const t of rPP) for(const m of t.matchAll(/(\d)\s*PP/g)) pplu.add(m[1]);
  const combi = [];
  for(const a of typ) for(const b of typ) if(a !== b) combi.push(a + b);
  for(const x of combi) typ.add(x);
  const T = t => t.toUpperCase().replace(/DM/g, '');
  const liste = swDe(nat);
  const typesNat = new Set(liste.filter(x => x.valeurs).map(x => T(x.type || '')));
  typ = new Set([...typ].filter(t => t.length >= 2 && [...typesNat].some(u => lev(T(t), u) <= 1)));
  const best = [], vusIm = new Set();
  for(const s of liste){
    if(!s.valeurs || vusIm.has(s.image)) continue;
    vusIm.add(s.image);
    const dt = typ.size ? Math.min(...[...typ].map(t => lev(T(t), T(s.type || '')))) : 3;
    const po = s.valeurs.match(/\[(\d+-\d+)\]/);
    let dv;
    if(po && [...vals].some(v => new RegExp('(?<!\\d)' + po[1] + '(?!\\d)').test(v))) dv = 0;
    else dv = vals.size ? Math.min(...[...vals].map(v => lev(chiffres(v), chiffres(s.valeurs)))) : 3;
    best.push([dt + dv, dt, dv, s.type || '', s.valeurs, s.image, s.pp || '', s]);
  }
  const cmp = (a, b) => { for(let k = 0; k < 7; k++){ if(a[k] < b[k]) return -1; if(a[k] > b[k]) return 1; } return 0; };
  best.sort(cmp);
  const brut = {typ: [...typ].sort(), vals: [...vals].sort(), pp: [...pplu].sort()};
  const sortie = (b, note = '') => ({texte: `SW ${b[3]} ${b[4]} → ${b[5]}` + note, sw: b[7], probable: !!note, brut});
  // Départage par les PP lus. « glisses » : chiffres glissés dans la valeur
  // admis aussi. Garde ajoutée au prototype (zéro faux) : un chiffre n'est
  // « glissé » que s'il est en trop par rapport à la valeur de l'arme
  // (« 12-7 » = 1PP + 2-7), pas s'il en remplace un (« 1-6 » lu pour 7-16
  // désignait à tort LMG(f) 2-6, 1PP).
  const parPP = (gr, glisses = false) => {
    const ind = b => {
      const s = new Set(pplu);
      if(glisses) for(const v of vals) if(chiffres(v).length > chiffres(b[4]).length) for(const d of chiffres(v)) s.add(d);
      return s;
    };
    const pp = gr.filter(b => chiffres(b[6]) && ind(b).has(chiffres(b[6])) && gr.every(o => o === b || !ind(b).has(chiffres(o[6]))));
    return pp.length === 1 ? pp[0] : null;
  };
  const E = best.filter(b => b[2] === 0);
  if(E.length){
    if(E.length === 1 && (!typ.size || E[0][1] <= 1)) return sortie(E[0]);
    const m = Math.min(...E.map(b => b[1]));
    const T1 = E.filter(b => b[1] === m);
    if(typ.size && m <= 1 && T1.length === 1) return sortie(T1[0]);
    const b = parPP(typ.size && m <= 1 ? T1 : E);
    if(b) return sortie(b, ` (probable, départagé par ${b[6]})`);
    return {texte: null, brut, candidats: E.map(b => b[7])};
  }
  const T0 = best.filter(b => b[1] === 0 && b[2] <= 1);
  if(T0.length === 1) return sortie(T0[0], ' (probable)');
  if(!typ.size){
    const proches = best.filter(b => b[2] <= 1);
    if(proches.length > 1){
      const b = parPP(proches, true);
      if(b) return sortie(b, ` (probable, départagé par ${b[6]})`);
    }
  }
  return {texte: null, brut};
}
async function lettres(im){
  const t = (await lire(lin(im, 3), 6)).toUpperCase();
  const m = t.match(/[LMH]MG|ATR|MTR|DC|FT|PSK|BAZ|PIAT|AT|\?/);
  return m ? m[0] : '';
}
function candidats(nat){
  const ch = OB_REF.chart[nat] || {squads: [], crews: []};
  const c = [], vu = new Set();
  const aj = (v, k) => { const cle = v + '|' + k; if(!vu.has(cle)){ vu.add(cle); c.push([v, k]); } };
  for(const r of ch.squads){
    if(/^\d-\d-\d$/.test(val(r.squad))) aj(val(r.squad), 'S');
    if(/^\d-\d-\d$/.test(val(r.hs))) aj(val(r.hs), 'H');
  }
  for(const x of ch.crews) aj(x.crew, 'C');
  return c;
}
const LEADERS = ['10-3', '10-2', '9-2', '9-1', '8-1', '8-0', '7-0', '6+1'];
async function lireLeader(c){
  const ch = c.h, cw = c.w;
  const bw = otsuInv(c);
  const moitie = ent(cw * 0.5);
  for(let y = 0; y < ch; y++) bw.d.fill(0, y * cw, y * cw + moitie);
  const cc = O.composantes(bw, 8);
  const k = [];
  for(let i = 1; i < cc.n; i++) if(cc.st[i][4] > 15 && cc.st[i][3] < ch * 0.6) k.push(i);
  if(!k.length) return null;
  const x0 = Math.min(...k.map(i => cc.st[i][0])), x1 = Math.max(...k.map(i => cc.st[i][0] + cc.st[i][2]));
  const y0 = Math.min(...k.map(i => cc.st[i][1])), y1 = Math.max(...k.map(i => cc.st[i][1] + cc.st[i][3]));
  const z = O.inverser(O.tranche(bw, Math.max(0, y0 - 2), y1 + 2, Math.max(0, x0 - 2), x1 + 2));
  const ps = [];
  for(const rot of ['horaire', 'antihoraire']){
    const r = O.bordure(O.redimensionner(O.pivoter(z, rot), 4, 4, 'cubique'), 30, 30, 30, 30);
    for(const psm of [7, 8, 13]) ps.push(lire(r, psm, '0123456789-+'));
  }
  const d = O.tranche(c, 0, ch, ent(cw * .5));
  for(const rot of ['horaire', 'antihoraire']){
    const r = O.bordure(O.redimensionner(O.pivoter(d, rot), 4, 4, 'cubique'), 30, 30, 30, 30);
    for(const psm of [6, 7]) ps.push(lire(r, psm, '0123456789-+'));
  }
  const v = new Compteur();
  for(const t of await enOrdre(ps)){ const s = t.trim(); if(LEADERS.includes(s)) v.ajouter(s); }
  const mc = v.mostCommon(1);
  return mc.length ? mc[0][0] : null;
}
// Quantité en chiffres rouges sous le pion (rouge_quantite).
async function rougeQuantite(rgba, x, y, w, h){
  const z = O.trancheCouleur(rgba, y + h, y + h + ent(h * 0.55), x, x + w);
  const m = O.masqueRouge(z);
  if(O.compterNonNuls(m) < 40) return [1, ''];
  const b = O.boiteNonNulle(m);
  const c = O.inverser(O.tranche(m, b.y0, b.y1 + 1, b.x0, b.x1 + 1));
  const pGlob = ocr(O.flou3(c), 8, '0123456789');
  const cc = O.composantes(m, 8), st = cc.st;
  let cs = [];
  for(let i = 1; i < cc.n; i++) if(st[i][4] >= 15) cs.push(i);
  if(!cs.length){ const glob = await pGlob; return [estNombre(glob) ? parseInt(glob, 10) : null, glob]; }
  const H = Math.max(...cs.map(i => st[i][3]));
  const hautes = cs.filter(i => st[i][3] >= .6 * H);
  const y0 = Math.min(...hautes.map(i => st[i][1])), y1 = Math.max(...hautes.map(i => st[i][1] + st[i][3]));
  const x0 = Math.min(...hautes.map(i => st[i][0])), x1 = Math.max(...hautes.map(i => st[i][0] + st[i][2]));
  cs = cs.filter(i => hautes.includes(i) || (x0 < st[i][0] && st[i][0] < x1
    && y0 + .25 * (y1 - y0) < st[i][1] + st[i][3] / 2 && st[i][1] + st[i][3] / 2 < y1 - .25 * (y1 - y0)));
  cs = cs.slice().sort((a, b) => st[a][0] - st[b][0]);
  const parts = cs.map(i => {
    const [xx, yy, cw, chh] = st[i];
    if(chh < .45 * H && cw > chh) return '-';
    if(chh < .45 * H) return '';
    if(cw <= .42 * chh) return '1';
    const d = O.inverser(O.masqueComposante(cc, z.w, i));
    return ocr(O.flou3(d), 10, '0123456789').then(r => r ? r[0] : '?');
  });
  const glob = await pGlob;
  let txt = (await enOrdre(parts)).join('');
  if(txt.replace(/^-+|-+$/g, '').includes('-')) return [null, txt];
  txt = txt.replace(/^-+|-+$/g, '');
  if(estNombre(txt)){
    const unSeul1 = glob.length === txt.length && [...txt].every((a, k) => a === glob[k] || a === '1');
    if(estNombre(glob) && glob !== txt && !(txt.endsWith(glob) && txt[0] === '1') && !unSeul1) return [null, `${txt} / ${glob}`];
    return [parseInt(txt, 10), txt];
  }
  return [estNombre(glob) ? parseInt(glob, 10) : null, glob];
}
const FORTIF = /FOXH|TRENC|SANGA|ROADB|MINEF|DITCH|OVR|OBA:|RTPH|MPH\/|\d\+\d\+\d|PILLB/;

// ═══ canons.py ══════════════════════════════════════════════════
const TYPES = ['AT', 'INF', 'ART', 'AA', 'MTR', 'RCL'];
function _texte(z, H, hmin = .07, hmax = .30){
  const bw = otsuInv(z), cc = O.composantes(bw, 8), g = [];
  for(let i = 1; i < cc.n; i++){
    const [, , w, h] = cc.st[i];
    if(hmin * H <= h && h <= hmax * H && w <= .30 * H) g.push(i);
  }
  if(!g.length) return null;
  return recadrer(masque(cc, z.w, z.h, g), 1, 2);
}
function _variantes(z, H){
  const v = [z, otsuBin(z)], t = _texte(z, H);
  if(t) v.push(t);
  return v;
}
async function _votes(z, H, psms, wl, filtre){
  const ps = [];
  for(const im of _variantes(z, H)) for(const psm of psms) ps.push(ocrK(im, psm, wl));
  const v = new Compteur();
  for(const t of await enOrdre(ps)) for(const x of filtre(t)) v.ajouter(x);
  return v;
}
const _calibres = s => [...s.replace(/ /g, '').replace(/[xX]/g, '*').matchAll(/(\d{2,3})(LL|L)?/g)].map(m => m[1] + (m[2] || ''));
function cle(cal){
  const m = (cal || '').replace(/✽/g, '*').match(/(\d+(?:\.\d+)?)(LL|L)?/);
  return m ? m[1].replace('.', '') + (m[2] || '') : '';
}
function ligneCalibre(c, ctx){
  const h = c.h, w = c.w, y0 = ent(h * .55);
  const z = O.tranche(c, y0, null, 0, ent(w * .85));
  const bw = otsuInv(z), cc = O.composantes(bw, 8), st = cc.st;
  const cs = [];
  for(let i = 1; i < cc.n; i++){
    const [x, y, cw, ch, a] = st[i];
    if(.11 * h <= ch && ch <= .30 * h && cw <= .60 * h && a >= .12 * cw * ch) cs.push([x, y, cw, ch, i]);
  }
  if(!cs.length) return null;
  const fond = Math.max(...cs.map(k => k[1] + k[3]));
  const ligne = cs.filter(k => fond - (k[1] + k[3]) <= .05 * h).sort((a, b) => a[0] - b[0]);
  const garde = [ligne[0]];
  for(const k of ligne.slice(1)){
    const p = garde[garde.length - 1];
    if(k[0] - (p[0] + p[2]) <= .10 * h) garde.push(k); else break;
  }
  const hl = Math.max(...garde.map(k => k[3])), top = Math.min(...garde.map(k => k[1]));
  const der = garde[garde.length - 1], xd = der[0] + der[2];
  let etoile = false;
  for(let i = 1; i < cc.n; i++){
    const [x, y, cw, ch] = st[i];
    if(0 <= x - xd && x - xd <= .08 * h && .05 * h <= ch && ch <= .7 * hl && cw <= .16 * h && y <= top + .35 * hl){ etoile = true; break; }
  }
  ctx.ligne = {nb: garde.length, etoile};
  return recadrer(masque(cc, z.w, z.h, garde.map(k => k[4])), 2, 3);
}
async function lireM(c){
  const h = c.h, w = c.w, v = new Compteur();
  const z = O.tranche(c, ent(h * .22), ent(h * .55), ent(w * .45));
  const ps = [];
  for(const inv of [false, true]){
    const b = O.binariserOtsu(z, !inv).img;
    const cc = O.composantes(b, 8), st = cc.st;
    const cs = [];
    for(let i = 1; i < cc.n; i++) if(.07 * h <= st[i][3] && st[i][3] <= .20 * h && st[i][2] <= .16 * h) cs.push([st[i][0], i]);
    cs.sort((a, b) => a[0] - b[0]);
    const tirets = [];
    for(let i = 1; i < cc.n; i++) if(st[i][3] <= .04 * h && .03 * h <= st[i][2] && st[i][2] <= .09 * h) tirets.push(i);
    if(cs.length < 2) continue;
    const d = recadrer(masque(cc, z.w, z.h, [...cs.slice(1).map(k => k[1]), ...tirets]), 2, 3);
    for(const psm of [7, 8, 13]) ps.push(ocrK(d, psm, '0123456789-', 5));
  }
  for(const t of await enOrdre(ps)) for(const m of t.match(/-?\d{1,2}/g) || []) v.ajouter(m);
  return v;
}
async function lireRof(c){
  const h = c.h, w = c.w;
  const z = O.tranche(c, ent(h * .35), ent(h * .80), 0, ent(w * .45));
  const bw = otsuInv(z);
  const ps = [];
  for(const ct of O.contours(bw, 'ccomp')){
    const {x, y, w: cw, h: ch} = ct;
    if(!(.10 * h <= cw && cw <= .28 * h && .10 * h <= ch && ch <= .28 * h && .75 < cw / ch && cw / ch < 1.33)) continue;
    if(!ct.trou) continue;                          // pas de trou : pas un cadre
    const m = Math.max(2, Math.floor(cw / 6));
    const d = O.tranche(z, y + m, y + ch - m, x + m, x + cw - m);
    if(!d.w || !d.h) continue;
    for(const im of [d, otsuBin(d)]) for(const psm of [10, 8, 7]) ps.push(ocrK(im, psm, '123', 5));
  }
  const v = new Compteur();
  for(const t of await enOrdre(ps)) if(/^[123]$/.test(t)) v.ajouter(t);
  return v;
}
async function lireLigne(c, ctx){
  const v = new Compteur(), z = ligneCalibre(c, ctx);
  if(!z) return v;
  const ps = [];
  for(const k of [3, 4, 5]) for(const psm of [7, 8, 13]) ps.push(ocrK(z, psm, '0123456789L', k));
  for(const t of await enOrdre(ps)) for(const x of _calibres(t)) v.ajouter(x);
  return v;
}
const CONF = [new Set('147'), new Set('356890'), new Set('2Z')];
function uneErreurTypique(a, b){
  if(a.length === b.length){
    const d = [];
    for(let i = 0; i < a.length; i++) if(a[i] !== b[i]) d.push([a[i], b[i]]);
    return d.length === 1 && CONF.some(c => c.has(d[0][0]) && c.has(d[0][1]));
  }
  const [court, long] = a.length < b.length ? [a, b] : [b, a];
  if(long.length - court.length !== 1) return false;
  for(let i = 0; i < long.length; i++) if(long.slice(0, i) + long.slice(i + 1) === court && '1L'.includes(long[i])) return true;
  return false;
}
const canonsLev1 = (a, b) => lev(a, b) === 1 && uneErreurTypique(a, b);

async function lireChampsCanon(c, ctx){
  const h = c.h, w = c.w;
  const [cal1, cal2, typ, M1, M2, ROF, portee] = await enOrdre([
    _votes(O.tranche(c, ent(h * .64), null, 0, ent(w * .80)), h, [7, 8], '0123456789L*', _calibres),
    lireLigne(c, ctx),
    _votes(O.tranche(c, 0, ent(h * .33), ent(w * .40)), h, [7, 8], 'ATINFRMC', s => TYPES.filter(t => s === t)),
    _votes(O.tranche(c, ent(h * .22), ent(h * .50), ent(w * .55)), h, [7, 8], 'M0123456789-', s => { const m = s.match(/M?(-?\d{1,2})$/); return m ? [m[1]] : []; }),
    lireM(c),
    lireRof(c),
    _votes(O.tranche(c, ent(h * .66), null, ent(w * .50)), h, [7], '0123456789[]-', s => [...s.matchAll(/\[?(\d{1,2}-\d{2,3})\]?/g)].map(m => m[1])),
  ]);
  return {cal: cal1.plus(cal2), type: typ, M: M1.plus(M2), ROF, portee};
}
async function lireCanon(c, canons){
  const ctx = {ligne: null};
  const lu = await lireChampsCanon(c, ctx);
  const cal = lu.cal.mostCommon().filter(([, n]) => n >= 2).map(([k]) => k).slice(0, 4);
  const un = k => {
    const mc = lu[k].mostCommon(2);
    if(!mc.length || mc[0][1] < 2 || (mc.length > 1 && mc[1][1] * 2 > mc[0][1])) return null;
    return mc[0][0];
  };
  const typ = un('type'), mm = un('M'), rof = un('ROF'), po = un('portee');
  const lg = ctx.ligne || {};
  const resume = {cal, type: typ, M: mm, ROF: rof, portee: po, ligne: ctx.ligne};
  const res = (texte, g = null, extra = {}) => ({texte, canon: g, resume, ...extra});
  if(!cal.length) return res('canon illisible (calibre non lu)');
  const vus = new Map();
  for(const g of canons){
    if(!g.calibre || !g.type) continue;
    const k = JSON.stringify([g.type, cle(g.calibre), g.M || '', g.ROF || '', g.B || '', g.IFE || '', g.portee || '']);
    if(!vus.has(k)) vus.set(k, g);
  }
  const POIDS = {type: 1, ROF: 1, 'portée': 1, 'M#': .5, 'M#faux': -.5, 'étoile': .5, 'étoilefaux': -.5, nb: .5, nbfaux: -.5};
  const compare = (nom, l, r) => {
    if(!l || !r) return null;
    if(nom === 'M#' && l !== r && (r.includes(l.replace(/^-+/, '')) || l.includes(r.replace(/^-+/, '')))) return null;
    return l === r;
  };
  const notes = [];
  for(const g of vus.values()){
    const r = cle(g.calibre);
    const d = cal.includes(r) ? 0 : (cal.some(k => lev(k, r) === 1 && uneErreurTypique(k, r)) ? 1 : 9);
    if(d > 1) continue;
    const champs = [['type', typ, g.type], ['M#', mm, g.M || ''], ['ROF', rof, g.ROF || ''], ['portée', po, g.portee || '']];
    const contre = champs.filter(([n, l, r2]) => compare(n, l, r2) === false && n !== 'M#').map(([n]) => n);
    const dM = champs.some(([n, l, r2]) => n === 'M#' && compare(n, l, r2) === false) ? 1 : 0;
    const pour = champs.filter(([n, l, r2]) => compare(n, l, r2)).map(([n]) => n);
    const doux = dM ? ['M#faux'] : [];
    if(lg.nb) doux.push(lg.nb === cle(g.calibre).length ? 'nb' : 'nbfaux');
    if('etoile' in lg && lg.nb){
      const eRef = /\d(?:LL|L)?[*✽]/.test(g.calibre.replace(/✽/g, '*'));
      if(lg.etoile) doux.push(eRef ? 'étoile' : 'étoilefaux');
    }
    notes.push([d, contre, [...pour, ...doux], g]);
  }
  if(!notes.length) return res(`canon illisible (lu ${cal[0]} ${typ || ''}, absent de la nation)`);
  const propres = notes.filter(n => !n[1].length);
  if(!propres.length)
    return res('canon illisible (contradictions : ' + notes.slice(0, 3).map(n => `${n[3].type} ${n[3].calibre} ${n[1].join(',')}`).join(' / ') + ')');
  if(notes.some(n => n[0] === 0 && n[1].length) && !propres.some(n => n[0] === 0))
    return res('canon illisible (calibre lu ' + cal[0] + ' contredit par ' + notes.find(n => n[0] === 0 && n[1].length)[1].join(',') + ')');
  const score = n => n[2].reduce((s, x) => s + POIDS[x], 0) - 1.5 * n[0];
  const sc = propres.map(n => [score(n), n]).sort((a, b) => b[0] - a[0]);
  const [s0, n0] = sc[0], g = n0[3];
  const sansL = x => cle(x.calibre).replace(/L/g, '');
  let jumeaux = [...vus.values()].filter(x => x.type === g.type && sansL(x) === sansL(g) && cle(x.calibre) !== cle(g.calibre));
  if(jumeaux.length && n0[0] === 0 && lg.nb === cle(g.calibre).length && jumeaux.every(x => lg.nb !== cle(x.calibre).length)
      && !jumeaux.some(x => cal.includes(cle(x.calibre))))
    jumeaux = [];
  const sansM = n => n[2].filter(x => !x.startsWith('M#')).reduce((s, x) => s + POIDS[x], 0) - 1.5 * n[0];
  let net = sc.length === 1 || (s0 - sc[1][0] >= 1.25 && sansM(n0) - Math.max(...sc.slice(1).map(([, n]) => sansM(n))) >= 1);
  const appui = n0[2].filter(x => POIDS[x] > 0).reduce((s, x) => s + POIDS[x], 0);
  if(net && sc.length > 1){
    const cg = cle(g.calibre);
    const champ = n => n[2].filter(x => ['type', 'ROF', 'portée'].includes(x)).reduce((s, x) => s + POIDS[x], 0);
    for(const [, n] of sc.slice(1)){
      const r = cle(n[3].calibre);
      if(cal.some(k => k !== cg && (k === r || canonsLev1(k, r))) && champ(n0) - champ(n) < 1){ net = false; break; }
    }
  }
  if(net && !jumeaux.length && (n0[0] === 0 || appui >= 2))
    return res(`${g.type} ${g.calibre} ${g.nom_vt || g.nom} → gpid ${g.gpid}` + (n0[0] ? ' (probable)' : ''), g, {probable: !!n0[0]});
  return res('canon ambigu : ' + sc.slice(0, 4).map(([, n]) => `${n[3].type} ${n[3].calibre} M${n[3].M || ''}`).join(' / '), null,
    {candidats: sc.slice(0, 4).map(([, n]) => n[3])});
}

// ═══ vehicules.py ═══════════════════════════════════════════════
function grandsChiffres(z, H, hmin = .10, hmax = .26){
  const bw = otsuInv(z), cc = O.composantes(bw, 8), g = [];
  for(let i = 1; i < cc.n; i++){
    const [, , cw, ch] = cc.st[i];
    if(hmin * H <= ch && ch <= hmax * H && cw <= .35 * H) g.push(i);
  }
  if(!g.length) return null;
  return recadrer(masque(cc, z.w, z.h, g), 2, 3);
}
// masque booléen (255) -> forme d'un « L »
function estL(m){
  const h = m.h, w = m.w;
  if(h < 8 || w < .35 * h) return false;
  const px = (x, y) => m.d[y * w + x] !== 0;
  const cg = Math.max(1, ent(w * .40));
  let n = 0;
  for(let y = 0; y < h; y++){ for(let x = 0; x < cg; x++) if(px(x, y)){ n++; break; } }
  const gauche = n / h;
  const yb = ent(h * .78);
  n = 0;
  for(let x = 0; x < w; x++){ for(let y = yb; y < h; y++) if(px(x, y)){ n++; break; } }
  const bas = yb < h ? n / w : NaN;
  const yh = ent(h * .65), xh = ent(w * .50);
  let s = 0, t = 0;
  for(let y = 0; y < yh; y++) for(let x = xh; x < w; x++){ t++; if(px(x, y)) s++; }
  const hd = t ? s / t : NaN;
  return gauche > .9 && bas > .8 && hd < .06;
}
async function parCaracteres(z, H, hmin, hmax, wl, haut = null, bas = false){
  const K = 3, zz = O.redimensionner(z, K, K, 'cubique'), HH = H * K;
  const t0 = O.otsu(zz);
  const lignes = [];
  for(const dt of [0, 30, 50]){
    const bw = O.binariser(zz, t0 - dt, true);
    const cc = O.composantes(bw, 8), st = cc.st;
    let cs = [];
    for(let i = 1; i < cc.n; i++)
      if(hmin * HH <= st[i][3] && st[i][3] <= hmax * HH && st[i][2] <= .6 * HH && (haut === null || st[i][1] <= haut * HH)) cs.push(i);
    if(!cs.length) continue;
    const hl = Math.max(...cs.map(i => st[i][3]));
    if(bas){
      const yb = Math.max(...cs.map(i => st[i][1] + st[i][3]));
      cs = cs.filter(i => st[i][1] + st[i][3] >= yb - .5 * hl);
    } else {
      const y0 = Math.min(...cs.map(i => st[i][1]));
      cs = cs.filter(i => st[i][1] <= y0 + .5 * hl);
    }
    cs.sort((a, b) => st[a][0] - st[b][0]);
    const parts = cs.map(i => {
      const [x, y, cw, ch, a] = st[i];
      if(cw <= .42 * ch && a >= .45 * cw * ch) return '1';
      const msk = O.masqueComposante(cc, zz.w, i);
      if(wl.includes('L') && estL(msk)) return 'L';
      const g = O.tranche(zz, Math.max(0, y - 4), y + ch + 4, Math.max(0, x - 4), x + cw + 4);
      const m = O.inverser(msk);
      const ps = [];
      for(const [im, k] of [[g, 1], [g, .5], [m, 1]])
        for(const psm of (cw <= .9 * ch ? [10, 8] : [8, 7]))
          ps.push(ocrK(k !== 1 ? O.redimensionner(im, k, k, 'aire') : im, psm, wl, 1));
      return enOrdre(ps).then(rs => {
        const vv = new Compteur();
        for(const r of rs) if(r) vv.ajouter(r);
        const mc = vv.mostCommon(1);
        return mc.length ? mc[0][0] : '?';
      });
    });
    lignes.push(enOrdre(parts).then(p => p.join('')));
  }
  return (await enOrdre(lignes)).filter(t => t);
}
async function lireMp(c){
  const h = c.h, w = c.w, v = new Compteur();
  const z = O.tranche(c, 0, ent(h * .34), ent(w * .55));
  const g = grandsChiffres(z, h);
  const [pc, rg] = await enOrdre([
    parCaracteres(z, h, .10, .24, '0123456789', .20),
    enOrdre(g ? [7, 8].map(psm => ocrK(g, psm, '0123456789', 3)) : []),
  ]);
  for(const t of pc) for(const m of t.match(/\d{1,2}/g) || []) if(3 <= +m && +m <= 40) v.ajouter(m, 3);
  for(const t of rg) for(const m of t.match(/\d{1,2}/g) || []) if(3 <= +m && +m <= 40) v.ajouter(m, 1);
  return v;
}
// est_vehicule : la lecture des MP est gardée sur le pion (même résultat
// relu par lire()).
async function estVehicule(c, memo){
  if(!memo.mp) memo.mp = lireMp(c);
  const v = await memo.mp, mc = v.mostCommon(1);
  return [mc.length && mc[0][1] >= 3 && +mc[0][0] >= 5 ? mc[0][0] : null, v];
}
function mpsPlausibles(v){
  const mc = v.mostCommon();
  if(!mc.length) return [];
  return mc.filter(([k, n]) => n >= Math.max(3, .6 * mc[0][1]) && +k >= 5).map(([k]) => k);
}
async function lireCalibreVeh(c){
  const h = c.h, w = c.w, v = new Compteur();
  const z = O.tranche(c, ent(h * .60), null, 0, ent(w * .62));
  const ctx = {ligne: null};
  const [pc, ll] = await enOrdre([parCaracteres(z, h, .10, .26, '0123456789L', null, true), lireLigne(c, ctx)]);
  for(const t of pc) for(const k of _calibres(t)) v.ajouter(k, 2);
  return v.plus(ll);
}
function seuils(z, k = 3){
  const zz = O.redimensionner(z, k, k, 'cubique'), t0 = O.otsu(zz);
  return [0, 30, 50].map(dt => O.binariser(zz, t0 - dt, false));
}
const normMg = s => (s || '').replace(/[*✽★]/g, '');
async function lireMg(c){
  const h = c.h, w = c.w, v = new Compteur();
  const z = O.tranche(c, ent(h * .72), null, ent(w * .50));
  const ps = [];
  for(const im of [...seuils(z), ..._variantes(z, h)]) for(const psm of [7, 8]) ps.push(ocrK(im, psm, '0123456789-/*', 4));
  for(const t of await enOrdre(ps))
    for(const m of t.replace(/ /g, '').match(/[-\d*]{1,2}\/[-\d*]{1,2}(?:\/[-\d*]{1,2})?/g) || []) v.ajouter(normMg(m));
  return v;
}
async function lireAf(c){
  const h = c.h, w = c.w, v = new Compteur();
  const z = O.tranche(c, ent(h * .28), ent(h * .72), ent(w * .70));
  const K = 3, zz = O.redimensionner(z, K, K, 'cubique'), HH = h * K;
  const t0 = O.otsu(zz);
  const ps = [];
  for(const dt of [0, 30, 50]){
    const bw = O.binariser(zz, t0 - dt, true);
    const cc = O.composantes(bw, 8), st = cc.st;
    const cs = [];
    for(let i = 1; i < cc.n; i++) if(.045 * HH <= st[i][3] && st[i][3] <= .12 * HH && st[i][2] <= .12 * HH) cs.push(i);
    const ligneDe = i => Math.floor(st[i][1] / (HH * .06));
    cs.sort((a, b) => ligneDe(a) - ligneDe(b) || st[a][0] - st[b][0]);
    const lignes = [];
    for(const i of cs){
      const [x, y, cw, ch] = st[i];
      const L = lignes[lignes.length - 1], p = L && L[L.length - 1];
      if(p && Math.abs(p[1] - y) < .04 * HH && x - (p[0] + p[2]) < .05 * HH) L.push([x, y, cw, ch]);
      else lignes.push([[x, y, cw, ch]]);
    }
    for(const L of lignes){
      const x0 = Math.min(...L.map(a => a[0])), y0 = Math.min(...L.map(a => a[1]));
      const x1 = Math.max(...L.map(a => a[0] + a[2])), y1 = Math.max(...L.map(a => a[1] + a[3]));
      if(L.length === 1 && L[0][2] <= .42 * L[0][3]){ ps.push(Promise.resolve(['1'])); continue; }
      const g = O.tranche(zz, Math.max(0, y0 - 4), y1 + 4, Math.max(0, x0 - 4), x1 + 4);
      ps.push(enOrdre([1, .6].map(k => ocrK(k !== 1 ? O.redimensionner(g, k, k, 'aire') : g, L.length > 1 ? 8 : 10, '0123456789', 1)))
        .then(rs => rs.filter(r => /^\d{1,2}$/.test(r))));
    }
  }
  for(const l of await enOrdre(ps)) for(const r of l) v.ajouter(r);
  return v;
}
async function lireVehicule(c, vehs, memo){
  const [[mp, vmp], vcal, vrof, vmg, vaf] = await enOrdre([estVehicule(c, memo), lireCalibreVeh(c), lireRof(c), lireMg(c), lireAf(c)]);
  const mps = mpsPlausibles(vmp);
  const cal = vcal.mostCommon().filter(([, n]) => n >= 2).map(([k]) => k).slice(0, 4);
  const un = v => {
    const mc = v.mostCommon(2);
    return mc.length && mc[0][1] >= 2 && (v.taille === 1 || mc[1][1] * 2 <= mc[0][1]) ? mc[0][0] : null;
  };
  const rof = un(vrof), mg = un(vmg);
  const af = new Set(vaf.entrees().filter(([, n]) => n >= 2).map(([k]) => k));
  const resume = {MP: mps.join('/'), cal, ROF: rof, MG: mg, AF: [...af].sort()};
  const res = (texte, g = null, extra = {}) => ({texte, vehicule: g, resume, ...extra});
  if(!mp) return res('véhicule non identifié (MP non lus)');
  const vus = new Map();
  for(const g of vehs){
    if(!g.MP) continue;
    const k = [g.MP, cle(g.calibre), normMg(g.MG), g.nums || [], g.autres || []];
    if(g.source) k.push(g.nom_vt || g.nom);
    const ks = JSON.stringify(k);
    if(!vus.has(ks)) vus.set(ks, []);
    vus.get(ks).push(g);
  }
  const notes = [];
  for(const gs of vus.values()){
    const g = gs[0];
    if(!mps.includes(g.MP)) continue;
    const pour = [], contre = [];
    let d = 0;
    const r = cle(g.calibre);
    if(cal.length && r){
      if(cal.includes(r)) pour.push('calibre');
      else if(cal.some(x => lev(x, r) === 1 && uneErreurTypique(x, r))) d = 1;
      else contre.push('calibre');
    } else if(cal.length && !r && !g.source) d = 1;
    const nums = g.nums || [];
    const rofRef = nums.length >= 3 ? nums[0] : null;
    const afRef = nums.length >= 2 ? new Set(nums.slice(-2)) : new Set();
    if(rof && rofRef) (rof === rofRef ? pour : contre).push('ROF');
    if(mg && g.MG){
      if(mg === normMg(g.MG)) pour.push('MG'); else contre.push('MG~');
    }
    if(af.size && afRef.size){
      const n = [...af].filter(x => afRef.has(x)).length;
      if(n) pour.push('AF' + n);
    }
    notes.push([d, contre, pour, gs]);
  }
  if(!notes.length) return res(`véhicule non identifié (aucun véhicule de la nation à ${mps.join('/')} MP)`);
  const P = {calibre: 2, ROF: 1, MG: 1, AF1: .5, AF2: 1};
  const dur = notes.filter(n => !n[1].includes('calibre'));
  if(!dur.length) return res('véhicule non identifié (contradictions)');
  const sc = dur.map(n => [n[2].reduce((s, x) => s + P[x], 0) - 1.5 * n[0] - .75 * n[1].length, n]).sort((a, b) => b[0] - a[0]);
  const [s0, n0] = sc[0];
  const forts = n0[2].filter(x => ['calibre', 'MG', 'ROF'].includes(x));
  const net = sc.length === 1 || s0 - sc[1][0] >= 1;
  const assez = n0[2].includes('calibre') || forts.length >= 2 || (!cle(n0[3][0].calibre) && n0[2].includes('MG'));
  const nom = x => x.nom_vt || x.nom;
  if(net && assez){
    const gs = n0[3], g = gs[0];
    const noms = [...new Set(gs.map(nom))];
    const autres = noms.length > 1 ? ` (même texte imprimé : ${noms.join(' / ')})` : '';
    return res(`${nom(g)} [${g.calibre || '-'} MP${g.MP} ${g.MG || ''}] → gpid ${g.gpid}` + (n0[0] ? ' (probable)' : '') + autres, g,
      {probable: !!n0[0], memeTexte: gs.slice(1)});
  }
  const courte = sc.filter(([s]) => s >= s0 - .5).map(([, n]) => n).slice(0, 6);
  return res('véhicule ambigu, au choix : ' + courte.map(n => `${nom(n[3][0])} ${n[3][0].calibre || ''}`.trim()).join(' / ')
    + (sc.length > courte.length ? ` (+${sc.length - courte.length} moins probables)` : ''), null,
    {candidats: courte.map(n => n[3][0])});
}

// ═══ obauto.py : cadres d'OB, nations ═══════════════════════════
const ADJ = {GERMAN: 'GERMAN', BRITISH: 'BRITISH', CANADIAN: 'BRITISH', 'NEW ZEALAND': 'BRITISH', AUSTRALIAN: 'BRITISH',
  ANZAC: 'BRITISH', INDIAN: 'BRITISH', COMMONWEALTH: 'BRITISH', AMERICAN: 'Army', RUSSIAN: 'RUSSIAN',
  ITALIAN: 'ITALIAN', FRENCH: 'FRENCH', FINNISH: 'FINNISH', JAPANESE: 'JAPANESE', BELGIAN: 'ALLIED MINOR',
  GREEK: 'ALLIED MINOR', DUTCH: 'ALLIED MINOR', POLISH: 'Polish', HUNGARIAN: 'Hungarian', ROMANIAN: 'AXIS MINOR',
  PARTISAN: 'PARTISAN', CHINESE: 'CHINESE'};
const ROUGE_BLEU = new Set(['BRITISH', 'FRENCH', 'ALLIED MINOR']);
function valeursNat(nat){
  const v = new Set(), ch = OB_REF.chart[nat];
  if(!ch) return v;
  for(const r of ch.squads){
    for(const k of ['squad', 'hs']) if(/^\d-\d-\d$/.test(val(r[k]))) v.add(chiffres(val(r[k])));
    if(r.squad.split('-')[0].includes('^')) v.add(chiffres(r.squad.replace(/\^\d+$/, '').replace(/\^/g, '')));
  }
  for(const c of ch.crews) v.add(chiffres(c.crew));
  return v;
}
function couleurCadre(rgba, [x, y, w, h]){
  const t = O.hsv(O.trancheCouleur(rgba, y, y + h, x, x + ent(w * .09)));
  let n = 0;
  for(let i = 0; i < t.S.length; i++) if(t.S[i] > 90 && t.V[i] > 60) n++;
  return t.S.length ? n / t.S.length : 0;
}
// Cadres d'OB (géométrie seule) : grands rectangles de traits longs, sous
// les 20 % du haut ; un cadre sans pion n'est pas un OB.
function cadresOb(g, cases){
  const W = g.w, H = g.h, L = Math.floor(W / 25);
  let cadres = [];
  for(const c of O.contours(grille(g, L, Math.floor(L / 3)), 'externe'))
    if(c.w > .55 * W && .05 * H < c.h && c.h < .45 * H && c.y > .2 * H) cadres.push([c.x, c.y, c.w, c.h]);
  cadres.sort((a, b) => a[1] - b[1]);
  const dans = (r, b) => b[0] <= r[0] + r[2] / 2 && r[0] + r[2] / 2 <= b[0] + b[2] && b[1] <= r[1] + r[3] / 2 && r[1] + r[3] / 2 <= b[1] + b[3];
  let parCadre = cadres.map(b => cases.filter(r => dans(r, b)));
  const garde = parCadre.map((pc, i) => pc.length ? i : -1).filter(i => i >= 0);
  return {cadres: garde.map(i => cadres[i]), parCadre: garde.map(i => parCadre[i])};
}
async function separerOb(rgba, g, cases){
  const W = g.w, H = g.h;
  let {cadres, parCadre} = cadresOb(g, cases);
  // nations de la piste des tours : bande au-dessus du premier cadre
  const y1 = cadres.length ? cadres[0][1] : ent(H * .45);
  const bande = O.tranche(g, Math.max(0, y1 - ent(.10 * H)), y1, 0, ent(W * .40));
  const natPiste = [];
  for(const [k, psm] of [[2, 6], [3, 6], [3, 4]]){
    const txt = (await lire(O.redimensionner(bande, k, k, 'cubique'), psm)).toUpperCase();
    for(const mot of txt.match(/[A-Z]{5,}/g) || [])
      for(const a of Object.keys(ADJ))
        if(!a.includes(' ') && lev(mot, a) <= (a.length <= 6 ? 1 : 2) && !natPiste.includes(ADJ[a])) natPiste.push(ADJ[a]);
    if(natPiste.length >= 2) break;
  }
  // valeurs lues dans chaque cadre
  const lus = async cs => {
    const ps = [];
    for(const [x, y, w, h] of cs){
      if(w > .065 * W) continue;
      const c = O.tranche(g, y + 3, y + h - 3, x + 3, x + w - 3), bas = O.tranche(c, ent(c.h * .62));
      for(const z of [bas, sansSoulignes(bas)])
        ps.push(lire(O.bordure(O.redimensionner(z, 3, 3, 'cubique'), 15, 15, 15, 15), 7, '0123456789-'));
    }
    return (await enOrdre(ps)).map(t => t.trim()).filter(t => chiffres(t).length >= 3).map(chiffres);
  };
  const lusCadres = await enOrdre(parCadre.map(lus));
  const attrib = [];
  const cands = natPiste.length >= 2 ? natPiste.slice(0, 2) : Object.keys(OB_REF.chart);
  cadres.forEach((b, i) => {
    const L_ = lusCadres[i];
    const sc = cands.map(n => { const vs = valeursNat(n); return [n, L_.filter(t => vs.has(t)).length]; });
    const best = sc.slice().sort((a, b2) => b2[1] - a[1]);
    const col = couleurCadre(rgba, b);
    let nat = null, how;
    if(best.length > 1 && best[0][1] > best[1][1]){ nat = best[0][0]; how = 'squads ' + JSON.stringify(Object.fromEntries(sc)); }
    else if(cands.length === 2 && ROUGE_BLEU.has(cands[0]) !== ROUGE_BLEU.has(cands[1])){
      nat = cands.find(n => ROUGE_BLEU.has(n) === (col > .01)); how = `emblème (couleur ${col.toFixed(3)})`;
    } else { nat = null; how = 'indéterminée'; }
    if(nat === null && natPiste.length === 1 && attrib.some(a => a.nation === natPiste[0])){
      const pool = Object.keys(OB_REF.chart).filter(n => ROUGE_BLEU.has(n) === (col > .01) && n !== natPiste[0]);
      const sc2 = pool.map(n => { const vs = valeursNat(n); return [L_.filter(t => vs.has(t)).length, n]; })
        .sort((a, b2) => b2[0] - a[0] || (b2[1] > a[1] ? 1 : b2[1] < a[1] ? -1 : 0));
      if(sc2.length && sc2[0][0] > 0 && (sc2.length === 1 || sc2[0][0] > sc2[1][0])){ nat = sc2[0][1]; how = 'emblème + squads'; }
      else if(col > .01){ nat = 'BRITISH'; how = `emblème en cocarde (couleur ${col.toFixed(3)}) : BRITISH supposé`; }
    }
    attrib.push({cadre: b, nation: nat, comment: how, n_pions: parCadre[i].length, couleur: Math.round(col * 1e4) / 1e4});
  });
  if(cands.length === 2 && attrib.length >= 2)
    for(const a of attrib) if(a.nation === null){
      const autres = new Set(attrib.filter(b => b !== a && b.nation).map(b => b.nation));
      if(autres.size === 1){ a.nation = cands.find(n => !autres.has(n)); a.comment += ' -> par élimination'; }
    }
  return {nations_piste: natPiste, cadres: attrib, C: parCadre.flat(), parCadre};
}

// ═══ ob.py : une case ═══════════════════════════════════════════
const GENRE = {S: 'squad', H: 'HS', C: 'crew'};
async function lireCase(fiche, [x, y, w, h], nat){
  const {g, rgba} = fiche, W = g.w;
  const c = O.tranche(g, y + 3, y + h - 3, x + 3, x + w - 3), ch = c.h, cw = c.w;
  const bas = O.tranche(c, ent(ch * 0.62));
  const chart = OB_REF.chart[nat] || {squads: [], crews: []};
  const grand = w > 0.065 * W;
  const memo = {};
  // lectures indépendantes, lancées ensemble
  const [lbBrut, mot, [q, qlu], ld, texteFortif, texteMoral] = await enOrdre([
    lecturesBas(bas),
    lettres(O.tranche(c, 0, ent(ch * 0.45), ent(cw * 0.4))),
    rougeQuantite(rgba, x, y, w, h),
    lireLeader(c),
    grand ? lire(lin(c, 2), 6) : Promise.resolve(''),
    grand ? Promise.resolve('') : lire(lin(O.tranche(c, ent(ch * 0.55)), 3), 6),
  ]);
  const lb = lbBrut.map(t => t.replace(/^(\d)\?/, '$1'));
  const lusBas = lb.reduce((m, t) => chiffres(t).length > chiffres(m).length ? t : m, lb.length ? lb[0] : '');
  let verdict = '?', pion = {genre: 'inconnu'};
  let t3 = lb.flatMap(l => jetons(l).filter(t => (t.match(/-/g) || []).length === 2));
  const expo = {};
  for(const r of chart.squads)
    if(r.squad.split('-')[0].includes('^')) expo[chiffres(r.squad.replace(/\^\d+$/, '').replace(/\^/g, ''))] = val(r.squad);
  for(const l of lb) if(expo[chiffres(l)] !== undefined) t3.push(expo[chiffres(l)]);
  t3 = [...new Set(t3)];
  const tl = ld ? [ld] : [];
  const t2 = jetons(lusBas).filter(t => (t.match(/-/g) || []).length === 1);
  const cands = candidats(nat);
  if(grand && FORTIF.test(texteFortif.toUpperCase().replace(/ /g, ''))){
    verdict = 'fortification (non traitée)'; pion = {genre: 'fortification'};
  } else if(grand){
    const vt = await _votes(O.tranche(c, 0, ent(ch * .33), ent(cw * .40)), ch, [7, 8], 'ATINFRMC', s => TYPES.filter(t => s === t));
    const mc = vt.mostCommon(1);
    const typeCanon = vt.taille && mc[0][1] >= 2;
    const mp = typeCanon ? null : (await estVehicule(c, memo))[0];
    if(mp){
      const r = await lireVehicule(c, OB_REF.veh[NAT_GUN[nat]] || [], memo);
      verdict = r.vehicule ? 'véhicule ' + r.texte : `${r.texte} ${pyRepr(r.resume)}`;
      pion = r.vehicule ? {genre: 'vehicule', ref: r.vehicule, probable: r.probable, memeTexte: r.memeTexte}
        : r.candidats ? {genre: 'vehicule', ambigu: true, candidats: r.candidats, lu: r.resume}
        : {genre: 'vehicule', illisible: true, lu: r.resume};
    } else {
      const r = await lireCanon(c, OB_REF.guns[NAT_GUN[nat]] || []);
      verdict = r.canon ? 'canon ' + r.texte : `grand pion non identifié (${r.texte})`;
      pion = r.canon ? {genre: 'canon', ref: r.canon, probable: r.probable}
        : r.candidats ? {genre: 'canon', ambigu: true, candidats: r.candidats, lu: r.resume}
        : {genre: 'grand', illisible: true, lu: r.resume};
    }
  } else if(texteMoral.toUpperCase().replace(/ /g, '').includes('MORAL')){
    verdict = '? (pion leurre)'; pion = {genre: 'leurre'};
  } else if(t3.length || (chiffres(lusBas).length === 3 && cands.some(([v]) => chiffres(v) === chiffres(lusBas)) && !mot)){
    // Garde ajoutée au prototype (zéro faux) : trois chiffres lus sans tirets
    // ne font pas un squad quand un type d'arme est lu en haut à droite
    // (LMG « 1PP » + « 2-7 » lu « 127 » = crew 1-2-7 britannique).
    const cvals = {};
    for(const [v] of cands) cvals[chiffres(v)] = v;
    const votes = new Compteur();
    for(const l of lb){
      const ts = [...jetons(l), ...(expo[chiffres(l)] !== undefined ? [expo[chiffres(l)]] : [])];
      for(const t of ts) if(cvals[chiffres(t)] !== undefined) votes.ajouter(cvals[chiffres(t)]);
    }
    const mc = votes.mostCommon(2);
    let lu;
    if(mc.length > 1 && mc[1][1] * 2 > mc[0][1]) lu = null;
    else lu = mc.length ? mc[0][0]
      : (t3.find(t => cands.some(([v]) => chiffres(t) === chiffres(v))) ?? (t3.length ? t3[0] : lusBas));
    const best = lu === null ? [] : cands.map(([v, k]) => [lev(chiffres(lu), chiffres(v)), v, k])
      .sort((a, b) => a[0] - b[0] || (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0) || (a[2] < b[2] ? -1 : a[2] > b[2] ? 1 : 0));
    if(lu === null){
      verdict = 'squad illisible (lectures en désaccord : ' + mc.map(([k, n]) => `${k} ×${n}`).join(' / ') + ')';
      pion = {genre: 'squad', illisible: true, lu: mc.map(([k]) => k)};
    } else if(best.length && best[0][0] <= 1 && (best.length === 1 || best[1][0] > best[0][0])){
      const [, v, k] = best[0];
      const imgs = (OB_REF.vasl[nat] || {})[`${v} ${k}`] || {};
      const noms = Object.keys(imgs);
      verdict = `${GENRE[k]} ${v}` + (lev(chiffres(lu), chiffres(v)) ? ` (lu ${lu})` : '') + ` → ${noms.length ? noms[0] : '—'}`;
      pion = {genre: GENRE[k], valeur: v, image: noms[0] || null, cases: noms.length ? imgs[noms[0]] : [], lu: lev(chiffres(lu), chiffres(v)) ? lu : null};
    } else {
      verdict = `squad illisible (lu ${lu})`;
      pion = {genre: 'squad', illisible: true, lu: [lu]};
    }
  } else if(t2.length || mot || (chiffres(lusBas).length >= 2 && !tl.length)){
    const r = await lireSw(c, nat);
    verdict = r.texte || `SW illisible ${pyRepr(r.brut.typ)} ${pyRepr(r.brut.vals)} PP${pyRepr(r.brut.pp)}`;
    pion = r.sw ? {genre: 'sw', ref: r.sw, probable: r.probable}
      : {genre: 'sw', illisible: true, lu: r.brut, candidats: r.candidats};
  }
  if(verdict === '?' && tl.length){ verdict = `leader ${tl[0]}`; pion = {genre: 'leader', valeur: tl[0]}; }
  return {verdict, pion, q, qlu};
}

// ═══ Une fiche entière ══════════════════════════════════════════
// fiche : {rgba, gris} (obImageDuPdf). options : {separation} pour imposer
// la séparation des OB ({C, nat_de} comme le prototype), surCase(r) appelé
// à chaque case lue (ordre d'arrivée), arret() vrai pour arrêter.
async function lireFiche(fiche, {separation = null, surCase = null, surSeparation = null, arret = () => false} = {}){
  const g = fiche.gris, rgba = fiche.rgba;
  let sep;
  if(separation) sep = {nations_piste: [], cadres: [], C: separation.C, nat_de: separation.nat_de};
  else {
    sep = await separerOb(rgba, g, casesPions(g));
    sep.nat_de = Object.fromEntries(sep.cadres.map(a => [String(a.cadre[1] + a.cadre[3]), a.nation || '?']));
  }
  // nation d'une case : premier cadre dont le bas est sous elle ; le
  // dernier cadre va jusqu'en bas de la page (comme les essais du prototype)
  const lim = Object.entries(sep.nat_de).map(([l, n]) => [+l, n]).sort((a, b) => a[0] - b[0]);
  if(lim.length) lim[lim.length - 1][0] = Infinity;
  const natDe = y => (lim.find(([l]) => y < l) || [0, '?'])[1];
  // groupe : rang du cadre (bas du cadre au-dessus de la case)
  const cases = sep.C.map((cas, i) => ({i, cas, nat: natDe(cas[1]), groupe: lim.findIndex(([l]) => cas[1] < l)}));
  if(surSeparation) surSeparation(Object.assign({}, sep, {cases}));
  await enOrdre(cases.map(async r => {
    if(arret()) return;
    try { Object.assign(r, await lireCase({g, rgba}, r.cas, r.nat)); }
    catch(e){
      if(e && e.message === OB_ARRET) throw e;
      r.verdict = 'erreur de lecture : ' + (e && e.message || e); r.pion = {genre: 'inconnu'};
    }
    if(surCase) surCase(r);
  }));
  return {nations_piste: sep.nations_piste, cadres: sep.cadres, nat_de: sep.nat_de, cases};
}

// Ligne de sortie du prototype (res*.txt)
function ligneRes(r){
  const q = r.q !== null && r.q !== undefined ? r.q : '?';
  const v = r.verdict.length < 42 ? r.verdict.padEnd(42) : r.verdict;
  return `${String(r.i).padStart(2)} ${r.nat.slice(0, 3)} ${v} ×${q}` + (r.qlu && String(r.q) !== r.qlu ? `  (rouge lu « ${r.qlu} »)` : '');
}

return {casesPions, cadresOb, separerOb, lireFiche, lireCase, ligneRes, lev, Compteur, cle,
  _interne: {lireSw, lireLeader, rougeQuantite, lireCanon, lireVehicule, parCaracteres, lecturesBas}};
})();
if(typeof module === 'object' && module.exports) module.exports = obLecture;
