// ═══════════════════════════════════════════════════════════════
// IMAGES — traitements d'image pour lire la fiche d'un scénario (PDF)
// Le prototype (Python) s'appuie sur OpenCV ; ce module en reprend, sans
// dépendance, les seules fonctions utilisées, en suivant leurs formules
// (arrondis, bords, ancres) pour que les découpes lues par l'OCR soient
// les mêmes que celles du prototype :
//   cvtColor (gris, HSV) · threshold (+ Otsu) · resize (cubique, linéaire,
//   aire) · erode / dilate / morphologyEx(OPEN) à noyau rectangulaire ·
//   connectedComponentsWithStats · findContours (EXTERNAL, CCOMP) +
//   boundingRect + contourArea · GaussianBlur 3×3 · rotate ·
//   copyMakeBorder · subtract.
// Image en niveaux de gris : {w, h, d: Uint8Array} (ligne par ligne).
// Image en couleur : {w, h, d} RVBA (ImageData ou équivalent).
// Les images binaires valent 0 ou 255, comme celles d'OpenCV.
// Utilisable dans la page (variable globale obImage) et sous Node
// (module.exports), pour les essais de non-régression.
// ═══════════════════════════════════════════════════════════════
const obImage = (() => {

const creer = (w, h, v = 0) => {
  const d = new Uint8Array(w * h);
  if(v) d.fill(v);
  return {w, h, d};
};
const copier = im => ({w: im.w, h: im.h, d: im.d.slice()});

// arrondi d'OpenCV (cvRound) : au plus proche, demi vers le pair
function arrondi(x){
  const r = Math.round(x);
  return (r - x === 0.5 && (r & 1)) ? r - 1 : r;
}
const sat8 = v => v < 0 ? 0 : v > 255 ? 255 : v;

// ── Découpe à la manière de numpy : im[y0:y1, x0:x1] ───────────
// Bornes absentes (undefined / null) = bord de l'image ; bornes
// négatives comptées depuis la fin, puis ramenées dans l'image,
// comme une tranche Python.
function _borne(v, n, defaut){
  if(v === undefined || v === null) return defaut;
  v = Math.trunc(v);
  if(v < 0) v += n;
  return v < 0 ? 0 : v > n ? n : v;
}
function tranche(im, y0, y1, x0, x1){
  const a = _borne(y0, im.h, 0), b = Math.max(a, _borne(y1, im.h, im.h));
  const c = _borne(x0, im.w, 0), e = Math.max(c, _borne(x1, im.w, im.w));
  const w = e - c, h = b - a, d = new Uint8Array(w * h);
  for(let y = 0; y < h; y++) d.set(im.d.subarray((a + y) * im.w + c, (a + y) * im.w + e), y * w);
  return {w, h, d};
}

// ── Couleur ─────────────────────────────────────────────────────
// cvtColor(BGR2GRAY), 8 bits : coefficients entiers sur 15 bits.
function gris(rgba){
  const n = rgba.w * rgba.h, s = rgba.d, d = new Uint8Array(n);
  for(let i = 0, j = 0; i < n; i++, j += 4)
    d[i] = (s[j] * 9798 + s[j + 1] * 19235 + s[j + 2] * 3735 + 16384) >> 15;
  return {w: rgba.w, h: rgba.h, d};
}
// cvtColor(BGR2HSV), 8 bits : teinte 0-179, saturation et valeur 0-255,
// par les tables entières d'OpenCV (décalage de 12 bits).
const _sdiv = new Int32Array(256), _hdiv = new Int32Array(256);
for(let i = 1; i < 256; i++){
  _sdiv[i] = arrondi((255 << 12) / i);
  _hdiv[i] = arrondi((180 << 12) / (6 * i));
}
function hsv(rgba){
  const n = rgba.w * rgba.h, s = rgba.d;
  const H = new Uint8Array(n), S = new Uint8Array(n), V = new Uint8Array(n);
  for(let i = 0, j = 0; i < n; i++, j += 4){
    const r = s[j], g = s[j + 1], b = s[j + 2];
    const v = Math.max(r, g, b), vmin = Math.min(r, g, b), diff = v - vmin;
    const vr = v === r ? -1 : 0, vg = v === g ? -1 : 0;
    let h = (vr & (g - b)) + (~vr & ((vg & (b - r + 2 * diff)) + (~vg & (r - g + 4 * diff))));
    h = (h * _hdiv[diff] + 2048) >> 12;
    if(h < 0) h += 180;
    H[i] = h;
    S[i] = (diff * _sdiv[v] + 2048) >> 12;
    V[i] = v;
  }
  return {w: rgba.w, h: rgba.h, H, S, V};
}
// Chiffres rouges des quantités (ob.py, rouge_quantite) :
// teinte < 12 ou > 165 (échelle 0-179 d'OpenCV), saturation > 70.
function masqueRouge(rgba){
  const t = hsv(rgba), d = new Uint8Array(rgba.w * rgba.h);
  for(let i = 0; i < d.length; i++)
    if((t.H[i] < 12 || t.H[i] > 165) && t.S[i] > 70) d[i] = 255;
  return {w: rgba.w, h: rgba.h, d};
}

// ── Seuils ──────────────────────────────────────────────────────
// Seuil d'Otsu, tel que getThreshVal_Otsu_8u.
function otsu(im){
  const hist = new Float64Array(256), n = im.d.length;
  for(let i = 0; i < n; i++) hist[im.d[i]]++;
  const scale = 1 / n, EPS = 1.1920928955078125e-7;
  let mu = 0;
  for(let i = 0; i < 256; i++) mu += i * hist[i];
  mu *= scale;
  let q1 = 0, mu1 = 0, maxSigma = 0, maxVal = 0;
  for(let i = 0; i < 256; i++){
    const p = hist[i] * scale;
    mu1 *= q1;
    q1 += p;
    const q2 = 1 - q1;
    if(Math.min(q1, q2) < EPS || Math.max(q1, q2) > 1 - EPS) continue;
    mu1 = (mu1 + i * p) / q1;
    const mu2 = (mu - q1 * mu1) / q2;
    const sigma = q1 * q2 * (mu1 - mu2) * (mu1 - mu2);
    if(sigma > maxSigma){ maxSigma = sigma; maxVal = i; }
  }
  return maxVal;
}
// threshold : BINARY (pixel > seuil → 255) ou BINARY_INV (pixel > seuil → 0).
function binariser(im, seuil, inverse = false){
  const n = im.d.length, s = im.d, d = new Uint8Array(n);
  const t = Math.floor(seuil);          // seuil réel : même effet que > seuil
  for(let i = 0; i < n; i++) d[i] = (s[i] > t) !== inverse ? 255 : 0;
  return {w: im.w, h: im.h, d};
}
// threshold(…, THRESH_OTSU) : {seuil, img}
function binariserOtsu(im, inverse = false){
  const seuil = otsu(im);
  return {seuil, img: binariser(im, seuil, inverse)};
}

// ── Opérations point à point ────────────────────────────────────
function inverser(im){
  const d = new Uint8Array(im.d.length);
  for(let i = 0; i < d.length; i++) d[i] = 255 - im.d[i];
  return {w: im.w, h: im.h, d};
}
function soustraire(a, b){              // cv2.subtract (saturé)
  const d = new Uint8Array(a.d.length);
  for(let i = 0; i < d.length; i++){ const v = a.d[i] - b.d[i]; d[i] = v > 0 ? v : 0; }
  return {w: a.w, h: a.h, d};
}
function ou(a, b){                      // a | b
  const d = new Uint8Array(a.d.length);
  for(let i = 0; i < d.length; i++) d[i] = a.d[i] | b.d[i];
  return {w: a.w, h: a.h, d};
}
// Plus petite boîte des pixels non nuls (np.nonzero) : {x0, y0, x1, y1}
// bornes incluses, ou null.
function boiteNonNulle(im){
  let x0 = im.w, y0 = im.h, x1 = -1, y1 = -1;
  for(let y = 0; y < im.h; y++) for(let x = 0, o = y * im.w; x < im.w; x++)
    if(im.d[o + x]){
      if(x < x0) x0 = x; if(x > x1) x1 = x;
      if(y < y0) y0 = y; if(y > y1) y1 = y;
    }
  return x1 < 0 ? null : {x0, y0, x1, y1};
}
function compterNonNuls(im){
  let n = 0;
  for(let i = 0; i < im.d.length; i++) if(im.d[i]) n++;
  return n;
}

// ── Morphologie (images binaires, noyau rectangulaire) ─────────
// Noyau de kw colonnes × kh lignes, ancre au centre (k/2 entier), comme
// getStructuringElement(MORPH_RECT, (kw, kh)) ; np.ones((3, 1)) est un
// noyau de 3 lignes × 1 colonne. Fenêtre de [x - a, x - a + k - 1] pour
// l'érosion comme pour la dilatation (OpenCV ne retourne pas le noyau).
// Bords : hors de l'image ne compte pas (érosion : plein ; dilatation :
// vide), comme les valeurs de bord par défaut d'OpenCV.
function _morph1D(im, k, horizontal, erosion){
  const {w, h} = im, s = im.d, d = new Uint8Array(w * h);
  if(k <= 1){ d.set(s); return {w, h, d}; }
  const a = k >> 1;
  const n = horizontal ? w : h, lignes = horizontal ? h : w;
  const pas = horizontal ? 1 : w, saut = horizontal ? w : 1;
  const cumul = new Int32Array(n + 1);
  for(let l = 0; l < lignes; l++){
    const base = l * saut;
    for(let i = 0; i < n; i++) cumul[i + 1] = cumul[i] + (s[base + i * pas] ? 1 : 0);
    for(let i = 0; i < n; i++){
      let g = i - a, dr = i - a + k;               // [g, dr)
      if(g < 0) g = 0; if(dr > n) dr = n;
      const plein = cumul[dr] - cumul[g];
      const v = erosion ? plein === dr - g : plein > 0;
      if(v) d[base + i * pas] = 255;
    }
  }
  return {w, h, d};
}
function eroder(im, kw, kh){ return _morph1D(_morph1D(im, kw, true, true), kh, false, true); }
function dilater(im, kw, kh){ return _morph1D(_morph1D(im, kw, true, false), kh, false, false); }
function ouvrir(im, kw, kh){ return dilater(eroder(im, kw, kh), kw, kh); }   // MORPH_OPEN

// ── Composantes connexes (connectedComponentsWithStats) ────────
// {n, lab: Int32Array, st: [[x, y, w, h, aire], …]} ; étiquette 0 = fond
// (st[0] : boîte et aire des pixels nuls). Ordre des étiquettes : celui
// d'OpenCV (algorithme par blocs 2×2 en 8-connexité : ordre du premier
// bloc 2×2 touché, ligne de blocs par ligne de blocs ; pixel par pixel
// en 4-connexité).
function composantes(im, connexite = 8){
  const {w, h} = im, s = im.d, n = w * h;
  const lab = new Int32Array(n);
  const parent = [0];
  const racine = x => { while(parent[x] !== x){ parent[x] = parent[parent[x]]; x = parent[x]; } return x; };
  const unir = (a, b) => { a = racine(a); b = racine(b); if(a !== b){ if(a < b) parent[b] = a; else parent[a] = b; } return a < b ? a : b; };
  for(let y = 0; y < h; y++){
    for(let x = 0; x < w; x++){
      const i = y * w + x;
      if(!s[i]) continue;
      let l = 0;
      const voir = j => { const m = lab[j]; if(m) l = l ? unir(l, m) : m; };
      if(x > 0) voir(i - 1);
      if(y > 0){
        voir(i - w);
        if(connexite === 8){
          if(x > 0) voir(i - w - 1);
          if(x < w - 1) voir(i - w + 1);
        }
      }
      if(!l){ l = parent.length; parent.push(l); }
      lab[i] = l;
    }
  }
  // clé d'ordre de chaque composante
  const cle = new Float64Array(parent.length).fill(Infinity);
  for(let y = 0; y < h; y++) for(let x = 0; x < w; x++){
    const i = y * w + x;
    if(!lab[i]) continue;
    const r = racine(lab[i]);
    const k = connexite === 8 ? ((y >> 1) * ((w + 1) >> 1) + (x >> 1)) * 4 + (y & 1) * 2 + (x & 1) : i;
    if(k < cle[r]) cle[r] = k;
  }
  const racines = [];
  for(let l = 1; l < parent.length; l++) if(racine(l) === l) racines.push(l);
  racines.sort((a, b) => cle[a] - cle[b]);
  const final = new Int32Array(parent.length);
  racines.forEach((r, k) => { final[r] = k + 1; });
  const nb = racines.length + 1;
  const x0 = new Int32Array(nb).fill(w), y0 = new Int32Array(nb).fill(h);
  const x1 = new Int32Array(nb).fill(-1), y1 = new Int32Array(nb).fill(-1), aire = new Int32Array(nb);
  for(let y = 0; y < h; y++) for(let x = 0; x < w; x++){
    const i = y * w + x;
    const l = lab[i] ? final[racine(lab[i])] : 0;
    lab[i] = l;
    aire[l]++;
    if(x < x0[l]) x0[l] = x; if(x > x1[l]) x1[l] = x;
    if(y < y0[l]) y0[l] = y; if(y > y1[l]) y1[l] = y;
  }
  const st = [];
  for(let l = 0; l < nb; l++)
    st.push(aire[l] ? [x0[l], y0[l], x1[l] - x0[l] + 1, y1[l] - y0[l] + 1, aire[l]] : [0, 0, 0, 0, 0]);
  return {n: nb, lab, st};
}
// Masque d'une composante dans sa boîte : 255 là où lab == i.
function masqueComposante(c, largeur, i){
  const [x, y, w, h] = c.st[i], d = new Uint8Array(w * h);
  for(let yy = 0; yy < h; yy++) for(let xx = 0; xx < w; xx++)
    if(c.lab[(y + yy) * largeur + x + xx] === i) d[yy * w + xx] = 255;
  return {w, h, d};
}

// ── Contours (findContours, suivi de Suzuki comme OpenCV) ──────
// mode 'externe' (RETR_EXTERNAL) : bords extérieurs des composantes qui ne
// sont dans le trou d'aucune autre ; mode 'ccomp' (RETR_CCOMP) : bords
// extérieurs de toutes les composantes et bords de leurs trous.
// Chaque contour : {x, y, w, h} (boundingRect), aire (contourArea, aire du
// polygone des centres des pixels du bord), trou (bord extérieur qui a au
// moins un trou : hiérarchie « premier enfant » ≥ 0), estTrou (bord d'un
// trou). Les pixels non nuls forment des composantes 8-connexes, le fond
// des régions 4-connexes ; l'image est bordée de fond, comme dans OpenCV.
// Ordre de sortie, comme OpenCV : bords extérieurs dans l'ordre inverse de
// leur découverte (balayage ligne par ligne), chacun suivi de ses trous
// (même ordre inverse).
const _DX = [1, 1, 0, -1, -1, -1, 0, 1], _DY = [0, -1, -1, -1, 0, 1, 1, 1];
function contours(im, mode = 'ccomp'){
  const W = im.w + 2, H = im.h + 2, P = new Uint8Array(W * H);
  for(let y = 0; y < im.h; y++) for(let x = 0; x < im.w; x++)
    if(im.d[y * im.w + x]) P[(y + 1) * W + x + 1] = 1;
  const D = _DX.map((dx, k) => _DY[k] * W + dx);
  // régions du fond (4-connexité) : 1 = l'extérieur (relié au bord)
  const fond = new Int32Array(W * H);
  const premierFond = [null, null];       // premier pixel de chaque région (ordre de lecture)
  const pile = new Int32Array(W * H);
  let nf = 0;
  for(let i = 0; i < W * H; i++){
    if(P[i] || fond[i]) continue;
    nf++;
    premierFond[nf] = i;
    let sp = 0; pile[sp++] = i; fond[i] = nf;
    while(sp){
      const j = pile[--sp], x = j % W;
      if(x > 0 && !P[j - 1] && !fond[j - 1]){ fond[j - 1] = nf; pile[sp++] = j - 1; }
      if(x < W - 1 && !P[j + 1] && !fond[j + 1]){ fond[j + 1] = nf; pile[sp++] = j + 1; }
      if(j >= W && !P[j - W] && !fond[j - W]){ fond[j - W] = nf; pile[sp++] = j - W; }
      if(j < W * (H - 1) && !P[j + W] && !fond[j + W]){ fond[j + W] = nf; pile[sp++] = j + W; }
    }
  }
  // composantes (8-connexité), avec leur premier pixel
  const comp = new Int32Array(W * H), premierComp = [null];
  let nc = 0;
  for(let i = 0; i < W * H; i++){
    if(!P[i] || comp[i]) continue;
    nc++;
    premierComp[nc] = i;
    let sp = 0; pile[sp++] = i; comp[i] = nc;
    while(sp){
      const j = pile[--sp];
      for(let k = 0; k < 8; k++){
        const m = j + D[k];
        if(P[m] && !comp[m]){ comp[m] = nc; pile[sp++] = m; }
      }
    }
  }
  // suivi d'un bord à partir du pixel i0 ; trou : le fond est à droite
  function suivre(i0, trou){
    let s = trou ? 0 : 4;
    const sFin0 = s;
    let i1 = -1;
    do { s = (s - 1) & 7; i1 = i0 + D[s]; } while(!P[i1] && s !== sFin0);
    const xs = [], ys = [];
    if(!P[i1]){                                     // pixel isolé
      xs.push(i0 % W - 1); ys.push(Math.floor(i0 / W) - 1);
    } else {
      let i3 = i0;
      for(;;){
        const sFin = s;
        let i4 = i3;
        for(let t = 1; t <= 8; t++){                  // sens trigonométrique, après sFin
          const k = (sFin + t) & 7;
          i4 = i3 + D[k];
          if(P[i4]){ s = k; break; }
        }
        xs.push(i3 % W - 1); ys.push(Math.floor(i3 / W) - 1);
        if(i4 === i0 && i3 === i1) break;
        i3 = i4; s = (s + 4) & 7;
      }
    }
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity, a = 0;
    for(let k = 0; k < xs.length; k++){
      const x = xs[k], y = ys[k];
      if(x < x0) x0 = x; if(x > x1) x1 = x;
      if(y < y0) y0 = y; if(y > y1) y1 = y;
      const k2 = (k + 1) % xs.length;
      a += x * ys[k2] - xs[k2] * y;
    }
    return {x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1, aire: Math.abs(a) / 2};
  }
  // trous de chaque composante (régions du fond hors extérieur) ; le
  // pixel à gauche du premier pixel d'un trou appartient à sa composante
  const trousDe = new Map();
  for(let r = 2; r <= nf; r++){
    const i0 = premierFond[r] - 1, k = comp[i0];
    if(!trousDe.has(k)) trousDe.set(k, []);
    trousDe.get(k).push(i0);
  }
  const sortie = [];
  for(let k = nc; k >= 1; k--){
    const i0 = premierComp[k];
    if(mode === 'externe' && fond[i0 - 1] !== 1) continue;
    const c = suivre(i0, false);
    const trous = trousDe.get(k) || [];
    c.estTrou = false; c.trou = trous.length > 0;
    sortie.push(c);
    if(mode !== 'ccomp') continue;
    for(const t of trous.slice().sort((a, b) => b - a)){
      const ct = suivre(t, true);
      ct.estTrou = true; ct.trou = false;
      sortie.push(ct);
    }
  }
  return sortie;
}

// ── Géométrie ───────────────────────────────────────────────────
// cv2.rotate : 'horaire' (ROTATE_90_CLOCKWISE), 'antihoraire', '180'.
function pivoter(im, sens){
  const {w, h} = im, s = im.d;
  if(sens === '180'){
    const d = new Uint8Array(w * h);
    for(let i = 0, n = w * h; i < n; i++) d[i] = s[n - 1 - i];
    return {w, h, d};
  }
  const d = new Uint8Array(w * h);           // nouvelle image : h colonnes × w lignes
  for(let y = 0; y < w; y++) for(let x = 0; x < h; x++)
    d[y * h + x] = sens === 'horaire' ? s[(h - 1 - x) * w + y] : s[x * w + (w - 1 - y)];
  return {w: h, h: w, d};
}
// cv2.copyMakeBorder(…, BORDER_CONSTANT, value)
function bordure(im, haut, bas, gauche, droite, valeur = 255){
  const w = im.w + gauche + droite, h = im.h + haut + bas;
  const d = new Uint8Array(w * h).fill(valeur);
  for(let y = 0; y < im.h; y++) d.set(im.d.subarray(y * im.w, (y + 1) * im.w), (y + haut) * w + gauche);
  return {w, h, d};
}
// cv2.GaussianBlur(im, (3, 3), 0) en 8 bits : noyau [1 2 1]/4 dans chaque
// sens, calcul exact (virgule fixe d'OpenCV), arrondi au plus proche,
// bords en miroir sans répéter le bord (BORDER_REFLECT_101).
function flou3(im){
  const {w, h} = im, s = im.d;
  const r101 = (i, n) => n === 1 ? 0 : i < 0 ? -i : i >= n ? 2 * n - 2 - i : i;
  const t = new Uint16Array(w * h);
  for(let y = 0; y < h; y++) for(let x = 0; x < w; x++){
    const o = y * w;
    t[o + x] = s[o + r101(x - 1, w)] + 2 * s[o + x] + s[o + r101(x + 1, w)];
  }
  const d = new Uint8Array(w * h);
  for(let y = 0; y < h; y++){
    const a = r101(y - 1, h) * w, b = y * w, c = r101(y + 1, h) * w;
    for(let x = 0; x < w; x++) d[b + x] = (t[a + x] + 2 * t[b + x] + t[c + x] + 8) >> 4;
  }
  return {w, h, d};
}

// ── Redimensionnement (cv2.resize avec fx, fy) ─────────────────
// Taille d'arrivée : arrondi de w·fx, h·fy (cvRound). 'cubique'
// (INTER_CUBIC, a = −0,75) et 'lineaire' (INTER_LINEAR, l'option par
// défaut de cv2.resize) : coefficients en virgule fixe sur 11 bits, bords
// répétés ; passe verticale comme OpenCV : en flottant 32 bits pour le
// cubique, par la formule entière de ses instructions vectorielles pour
// le linéaire. 'aire' (INTER_AREA) : réductions (moyenne des blocs pour
// un facteur entier, pondérée sinon).
// Vérifié identique au pixel près à OpenCV 5.1 (×1,5 à ×5) ; quand OpenCV
// passe par IPP (selon la machine), son cubique s'écarte lui-même de ±1
// sur environ 0,3 % des pixels.
const f32 = Math.fround;
function _coefsCubiques(x){
  const A = -0.75;
  const c0 = f32(f32(f32(f32(f32(A * f32(x + 1)) - 5 * A) * f32(x + 1)) + 8 * A) * f32(x + 1) - 4 * A);
  const c1 = f32(f32(f32(f32((A + 2) * x) - (A + 3)) * x) * x + 1);
  const y = f32(1 - x);
  const c2 = f32(f32(f32(f32((A + 2) * y) - (A + 3)) * y) * y + 1);
  const c3 = f32(f32(f32(1 - c0) - c1) - c2);
  return [c0, c1, c2, c3];
}
function _tables(n, nArr, inv, cubique, vertical){
  const k = cubique ? 4 : 2, ofs = new Int32Array(nArr * k), co = new Int32Array(nArr * k);
  const echelle = 1 / inv;
  for(let i = 0; i < nArr; i++){
    let f = f32((i + 0.5) * echelle - 0.5);
    let sx = Math.floor(f);
    f = f32(f - sx);
    let cs;
    if(cubique) cs = _coefsCubiques(f);
    else {
      if(!vertical){                     // en vertical, seul l'indice est borné
        if(sx < 0){ f = 0; sx = 0; }
        if(sx >= n - 1){ f = 0; sx = n - 1; }
      }
      cs = [f32(1 - f), f];
    }
    for(let j = 0; j < k; j++){
      const p = sx + j - (cubique ? 1 : 0);
      ofs[i * k + j] = p < 0 ? 0 : p >= n ? n - 1 : p;
      co[i * k + j] = arrondi(cs[j] * 2048);
    }
  }
  return {k, ofs, co};
}
function redimensionner(im, fx, fy, mode = 'lineaire'){
  const W = arrondi(im.w * fx), H = arrondi(im.h * fy);
  if(mode === 'aire') return _reduireAire(im, fx, fy, W, H);
  const cub = mode === 'cubique';
  const tx = _tables(im.w, W, fx, cub, false), ty = _tables(im.h, H, fy, cub, true);
  const k = tx.k, s = im.d;
  // passe horizontale : lignes de l'image de départ, entiers (× 2048)
  const lignes = new Int32Array(im.h * W);
  for(let y = 0; y < im.h; y++){
    const o = y * im.w, oo = y * W;
    for(let x = 0; x < W; x++){
      let v = 0;
      for(let j = 0; j < k; j++) v += s[o + tx.ofs[x * k + j]] * tx.co[x * k + j];
      lignes[oo + x] = v;
    }
  }
  // passe verticale puis retour sur 8 bits
  const d = new Uint8Array(W * H);
  const echelle = f32(1 / (2048 * 2048));
  for(let y = 0; y < H; y++){
    if(cub){
      const b = [0, 1, 2, 3].map(j => f32(ty.co[y * 4 + j] * echelle));
      const r = [0, 1, 2, 3].map(j => ty.ofs[y * 4 + j] * W);
      for(let x = 0; x < W; x++){
        let v = 0;
        for(let j = 0; j < 4; j++) v = f32(v + f32(lignes[r[j] + x] * b[j]));
        d[y * W + x] = sat8(arrondi(v));
      }
    } else {
      const b0 = ty.co[y * 2], b1 = ty.co[y * 2 + 1];
      const r0 = ty.ofs[y * 2] * W, r1 = ty.ofs[y * 2 + 1] * W;
      for(let x = 0; x < W; x++){
        const v = (((lignes[r0 + x] >> 4) * b0) >> 16) + (((lignes[r1 + x] >> 4) * b1) >> 16);
        d[y * W + x] = sat8((v + 2) >> 2);
      }
    }
  }
  return {w: W, h: H, d};
}
function _reduireAire(im, fx, fy, W, H){
  const sx = Math.round(1 / fx), sy = Math.round(1 / fy);
  if(Math.abs(1 / fx - sx) > 1e-9 || Math.abs(1 / fy - sy) > 1e-9) return _aireGenerale(im, fx, fy, W, H);
  const d = new Uint8Array(W * H), n = sx * sy;
  for(let y = 0; y < H; y++) for(let x = 0; x < W; x++){
    let v = 0, c = 0;
    for(let j = 0; j < sy; j++){
      const yy = y * sy + j;
      if(yy >= im.h) continue;
      for(let i = 0; i < sx; i++){
        const xx = x * sx + i;
        if(xx >= im.w) continue;
        v += im.d[yy * im.w + xx]; c++;
      }
    }
    d[y * W + x] = c === n ? Math.floor((v + (n >> 1)) / n) : sat8(arrondi(f32(v / c)));
  }
  return {w: W, h: H, d};
}
// INTER_AREA d'un facteur quelconque (computeResizeAreaTab d'OpenCV) :
// moyenne pondérée par la part de chaque pixel de départ, en flottant.
function _tableAire(n, N, echelle){
  const tab = [];
  for(let dx = 0; dx < N; dx++){
    const f1 = dx * echelle, f2 = f1 + echelle, larg = Math.min(echelle, n - f1);
    let s1 = Math.ceil(f1), s2 = Math.floor(f2);
    s2 = Math.min(s2, n - 1); s1 = Math.min(s1, s2);
    const t = [];
    if(s1 - f1 > 1e-3) t.push([s1 - 1, f32((s1 - f1) / larg)]);
    for(let k = s1; k < s2; k++) t.push([k, f32(1 / larg)]);
    if(f2 - s2 > 1e-3) t.push([s2, f32(Math.min(Math.min(f2 - s2, 1), larg) / larg)]);
    tab.push(t);
  }
  return tab;
}
function _aireGenerale(im, fx, fy, W, H){
  const tx = _tableAire(im.w, W, 1 / fx), ty = _tableAire(im.h, H, 1 / fy);
  const lignes = new Float32Array(im.h * W);
  for(let y = 0; y < im.h; y++) for(let x = 0; x < W; x++){
    let v = 0;
    for(const [k, a] of tx[x]) v = f32(v + f32(im.d[y * im.w + k] * a));
    lignes[y * W + x] = v;
  }
  const d = new Uint8Array(W * H);
  for(let y = 0; y < H; y++) for(let x = 0; x < W; x++){
    let v = 0;
    for(const [k, b] of ty[y]) v = f32(v + f32(lignes[k * W + x] * b));
    d[y * W + x] = sat8(arrondi(v));
  }
  return {w: W, h: H, d};
}

// Découpe d'une image en couleur (RVBA), comme tranche.
function trancheCouleur(im, y0, y1, x0, x1){
  const a = _borne(y0, im.h, 0), b = Math.max(a, _borne(y1, im.h, im.h));
  const c = _borne(x0, im.w, 0), e = Math.max(c, _borne(x1, im.w, im.w));
  const w = e - c, h = b - a, d = new Uint8ClampedArray(w * h * 4);
  for(let y = 0; y < h; y++) d.set(im.d.subarray(((a + y) * im.w + c) * 4, ((a + y) * im.w + e) * 4), y * w * 4);
  return {w, h, d};
}

// ── Passage vers l'OCR et l'écran ───────────────────────────────
// PGM binaire (P5) : lu tel quel par Leptonica, sans canvas ni
// compression ; même image que le PNG en gris que pytesseract fournit.
function versPGM(im){
  const tete = new TextEncoder().encode(`P5\n${im.w} ${im.h}\n255\n`);
  const o = new Uint8Array(tete.length + im.d.length);
  o.set(tete); o.set(im.d, tete.length);
  return o;
}
// ImageData (RVBA) d'une image en gris, pour un canvas.
function versRVBA(im){
  const o = new Uint8ClampedArray(im.w * im.h * 4);
  for(let i = 0, j = 0; i < im.d.length; i++, j += 4){
    o[j] = o[j + 1] = o[j + 2] = im.d[i]; o[j + 3] = 255;
  }
  return typeof ImageData !== 'undefined' ? new ImageData(o, im.w, im.h) : {width: im.w, height: im.h, data: o};
}

return {
  creer, copier, arrondi, tranche, trancheCouleur,
  gris, hsv, masqueRouge,
  otsu, binariser, binariserOtsu,
  inverser, soustraire, ou, boiteNonNulle, compterNonNuls,
  eroder, dilater, ouvrir,
  composantes, masqueComposante, contours,
  pivoter, bordure, flou3, redimensionner,
  versPGM, versRVBA,
};
})();
if(typeof module === 'object' && module.exports) module.exports = obImage;
