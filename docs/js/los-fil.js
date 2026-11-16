// ═══════════════════════════════════════════════════════════════
// FIL DE VISÉE — VASLThread (bouton LOS, F10 ; silencieux Ctrl+Maj+F10)
// Le bouton arme le fil et rend les pions transparents ; l'appui sur la
// carte fixe la source, le glisser la cible. Pendant le geste : flèches
// haut et bas pour le niveau de la cible, Ctrl + flèches pour celui de la
// source ; Ctrl à l'appui ou au glisser prend le plus haut niveau hors
// toit. Au lâcher, le rapport part au chat et le fil disparaît
// (persistence « Never » du module).
// ═══════════════════════════════════════════════════════════════
// Préférences de l'onglet « LOS » de VASL (VASLThread.addTo), réglées
// par la fenêtre des préférences (⚙, js/preferences.js) et gardées sur le
// poste : couleurs du fil (Thread Color), de la gêne (Hindrance Thread
// Color) et du blocage (Blocked Thread Color), vérification de la LOS
// (Enable LOS checking), opacité des pions pendant la visée (Unit opacity
// % during LOS checks). Par défaut, ceux de VASL : fil et gêne rouges,
// blocage bleu, vérification active, pions à 33 %.
const LOS_DEFAUTS = {fil: '#ff0000', gene: '#ff0000', bloc: '#0000ff', verif: true, opacite: '33'};
const LOS_OPACITES = ['0', '25', '33', '50', '66', '75', '100'];
const _couleurPref = (c, d) => { const v = _lirePref(c); return /^#[0-9a-f]{6}$/i.test(v || '') ? v.toLowerCase() : d; };
let LOS_COUL_FIL = _couleurPref('aslweb.losCouleurFil', LOS_DEFAUTS.fil);
let LOS_COUL_GENE = _couleurPref('aslweb.losCouleurGene', LOS_DEFAUTS.gene);
let LOS_COUL_BLOC = _couleurPref('aslweb.losCouleurBloc', LOS_DEFAUTS.bloc);
let _losVerif = _lirePref('aslweb.losVerif') !== '0';
let _losOpacite = LOS_OPACITES.includes(_lirePref('aslweb.losOpacite')) ? _lirePref('aslweb.losOpacite') : LOS_DEFAUTS.opacite;
// L'opacité passe à la feuille de style (#mapInner.losActif .pion).
const _appliquerOpaciteLOS = () => document.documentElement.style.setProperty('--los-opacite', String(_losOpacite / 100));
_appliquerOpaciteLOS();
// Change une préférence de la LOS, la garde sur le poste et redessine le
// fil en cours. quoi : 'fil' | 'gene' | 'bloc' | 'verif' | 'opacite'.
function reglerLOS(quoi, v){
  const cles = {fil: 'losCouleurFil', gene: 'losCouleurGene', bloc: 'losCouleurBloc', verif: 'losVerif', opacite: 'losOpacite'};
  if(quoi === 'fil') LOS_COUL_FIL = v;
  else if(quoi === 'gene') LOS_COUL_GENE = v;
  else if(quoi === 'bloc') LOS_COUL_BLOC = v;
  else if(quoi === 'verif') _losVerif = !!v;
  else if(quoi === 'opacite'){ _losOpacite = String(v); _appliquerOpaciteLOS(); }
  else return;
  try { localStorage.setItem('aslweb.' + cles[quoi], quoi === 'verif' ? (v ? '1' : '0') : String(v)); } catch(e){}
  if(_fil){
    if(quoi === 'verif' && _fil.m){ _fil.complet = !!(_fil.completCarte && _losVerif); _faireLOS(_fil); }
    dessinerFil();
  }
}
const LOS_POLICE = 14, LOS_DESCENTE = 3;   // hauteur et descente de RANGE_FONT, en px
let _losBoards = null;      // disposition de la carte affichée
let _losCarte = null;       // Promise<{map, complet}> ou null
let _losPrete = null;       // la même, une fois construite (décompte des MF)
let _metaPions = null;      // Promise<Map> de CounterMetadata.xml
let _filArme = null;        // null, 'L' (fil « LOS ») ou 'l' (fil silencieux)
let _fil = null;            // fil en cours de tracé
// « Verbose LOS mode » : nom des lieux, raison du blocage, gênes.
let _losVerbeux = true;
try { _losVerbeux = localStorage.getItem('losVerbeux') !== '0'; } catch(e){}

async function _gunzip(octets){
  if(typeof DecompressionStream === 'undefined')
    throw new Error('DecompressionStream indisponible — navigateur trop ancien');
  const flux = new Blob([octets]).stream().pipeThrough(new DecompressionStream('gzip'));
  return new Uint8Array(await new Response(flux).arrayBuffer());
}

// Nouvelle carte affichée : la carte LOS sera reconstruite à la demande.
function oublierCarteLOS(boards){
  if(_filArme) desarmerFil();
  _losBoards = boards; _losCarte = null; _losPrete = null;
}

// Carte LOS de la disposition courante (construite une fois). Sans les
// données d'un board, VASL repasse au fil de VASSAL : la grille seule
// donne encore la portée.
function carteLOS(){
  if(!_losCarte && _losBoards){
    const boards = _losBoards;
    _losCarte = (async () => {
      const xml = await textePartage();
      try {
        const m = await construireCarteLOS(boards, xml, _archive);
        try { m.pions = new LosPions(m, await metaPions()); }
        catch(e){ chatLog('⚠️ CounterMetadata.xml information did not load properly. LOS results may be incorrect.'); }
        chatLog('👁️ VASL LOS Enabled');
        _losPrete = m;               // pour le décompte des MF, synchrone
        return {map: m, complet: true};
      } catch(e){
        chatLog(`⚠️ VASL LOS disabled — ${e.message}. Fil sans vérification de la ligne de vue.`);
        try { return {map: await construireCarteLOS(boards, xml, _archive, {sansDonnees: true}), complet: false}; }
        catch(e2){ return {map: null, complet: false}; }
      }
    })().catch(e => {
      chatLog('⚠️ VASL LOS disabled — ' + e.message);
      _losCarte = null;
      return {map: null, complet: false};
    });
  }
  return _losCarte || Promise.resolve({map: null, complet: false});
}

function metaPions(){
  if(!_metaPions){
    _metaPions = fetch(VASL_DIST_URL + 'CounterMetadata.xml').then(r => {
      if(!r.ok) throw new Error('HTTP ' + r.status);
      return r.text();
    }).then(lireMetaPions);
    _metaPions.catch(() => { _metaPions = null; });
  }
  return _metaPions;
}

// Les pièces de la carte telles que VASLGameInterface les voit : ni HIP
// ni pion dissimulé de l'adversaire. Une pile est la liste des pièces
// posées au même point.
function _piecesLOS(){
  const piles = new Map(), out = [];
  for(const p of vivants()){
    if(p.cache || p.outil || p.ovrExtPose) continue;
    if(p.masqueProprio && !voitMasque(p.masqueProprio)) continue;
    if(!memeEtat(p._losSeg, p)){
      const ts = tsPion(p), pr = _proprietes(ts);
      p._losSeg = empreinteEtat(p);
      p._losPr = {hindrance: 'Hindrance' in pr, overlay: 'overlay' in pr, deplace: !!_deplace(ts)};
    }
    const c = _versLOS(p), d = Object.assign({nom: p.nom || '', x: c.x, y: c.y, ordre: p.ordre}, p._losPr);
    const k = cle(p);
    if(!piles.has(k)) piles.set(k, []);
    piles.get(k).push(d);
    out.push(d);
  }
  for(const l of piles.values()) if(l.length > 1){
    l.sort((a, b) => a.ordre - b.ordre);
    for(const d of l) d.pile = l;
  }
  return out;
}
// VASLThread.initializeMap : instantané des pions au lancement du fil.
function _pionsDuFil(map){
  if(!map || !map.pions) return LOS_SANS_PIONS;
  map.pions.updatePieces(_piecesLOS());
  return map.pions;
}

function armerFil(mode){
  if(_filArme){ desarmerFil(); return; }
  if(!_losBoards){ chatLog('ℹ️ Ouvrir une partie avant de tracer une ligne de vue'); return; }
  _filArme = mode;
  rafraichirActions(['los', 'losMuet']);
  document.getElementById('mapWrap').classList.add('visee');
  document.getElementById('mapInner').classList.add('losActif');
  fermerMenu(); cacherSurvol();
  carteLOS();
}
function desarmerFil(){
  _filArme = null;
  rafraichirActions(['los', 'losMuet']);
  document.getElementById('mapWrap').classList.remove('visee');
  document.getElementById('mapInner').classList.remove('losActif');
  _effacerFil();
}

function basculerVerbeux(){
  _losVerbeux = !_losVerbeux;
  try { localStorage.setItem('losVerbeux', _losVerbeux ? '1' : '0'); } catch(e){}
  chatLog(`👁️ Verbose LOS mode : ${_losVerbeux ? 'oui' : 'non'}`);
  if(_fil) dessinerFil();
}

// Pixels natifs de la carte ↔ repère LOS (sans marge, sans loupe).
const _losZoom = () => (_losBoards && _losBoards[0] && _losBoards[0].zoom) || 1;
const _versLOS = c => ({x: Math.round((c.x - BOARD_MARGIN) / _losZoom()), y: Math.round((c.y - BOARD_MARGIN) / _losZoom())});
const _depuisLOS = p => ({x: (p.x * _losZoom() + BOARD_MARGIN) * SC, y: (p.y * _losZoom() + BOARD_MARGIN) * SC});

// Lieu le plus proche du point, et le point de visée le plus proche
// (useAuxLOSPoint) : le côté contourné a deux bouts.
function _lieuAuPoint(m, c){
  const p = _versLOS(c);
  if(!m.onMap(p.x, p.y)) return null;
  const h = m.gridToHex(p.x, p.y);
  if(!h) return null;
  const l = h.getNearestLocation(p.x, p.y), a = l.getLOSPoint(), b = l.getAuxLOSPoint();
  return {l, aux: _dist(p.x, p.y, a.x, a.y) > _dist(p.x, p.y, b.x, b.y)};
}
const _plusHautHorsToit = l => {
  while(l.getUpLocation() != null && !l.getUpLocation().getName().includes('Rooftop')) l = l.getUpLocation();
  return l;
};
// Niveau affiché d'un lieu (VASLThread.mousePressed / mouseDragged).
function _niveauAffiche(l){
  let adj = 0;
  if(l.getName().includes('Rooftop') && l.getTerrain().getHeight() !== 1) adj = -0.5;
  const c = l.getHex().getHexCenter(), p = l.getLOSPoint();
  if(l.getHex().isDepressionTerrain() && !(p.x === c.x && p.y === c.y)) adj = 1;
  if(l.getTerrain().getName().includes('Railroad, Embankment')) adj = 0.5;
  return l.getAbsoluteHeight() + adj;
}
function _nomLieu(f, l){
  const multi = _losBoards.length > 1;
  return (multi ? (l.getHex().board || '') : '') + l.getName();
}

const _nouveauFil = (mode, c0, c1) => ({mode, c0, c1, ctrl0: false, ctrl1: false,
  m: null, complet: false, completCarte: false, src: null, tgt: null, auxS: false, auxT: false,
  sl: 0, tl: 0, res: null, texte: '', compte: 0, de: '', vers: ''});

// Au doigt, la visée se fait en deux temps, chacun avec la loupe dès
// l'appui : le premier appui (ou glissé) vise la source, posée au relâché ;
// le fil reste alors ancré, avec un panneau pour les niveaux, la validation
// et l'annulation, et chaque nouvel appui ou glissé sur la carte vise la
// cible, posée au relâché. Un deuxième doigt pendant une visée l'abandonne
// et pince la carte. À la souris, on glisse de la source à la cible ; au
// relâché, le fil reste ancré avec le même panneau (From / To, niveaux,
// ✓ Check), et un nouveau clic ou glissé sur la carte déplace le bout
// choisi.
let _doigtFil = null;               // {id, x, y, abandonner} : visée au doigt en cours
function _suivreDoigtFil(e, abandonner){
  _doigtFil = {id: e.pointerId, x: e.clientX, y: e.clientY, abandonner};
}
function _finDoigtFil(id){ if(_doigtFil && _doigtFil.id === id) _doigtFil = null; }
function commencerFil(e){
  const c = clientVersCarte(e.clientX, e.clientY);
  if(!c) return;
  const f = _fil = _nouveauFil(_filArme, c, c);
  f.ctrl0 = e.ctrlKey || e.metaKey;
  const doigt = !!e.pointerType && e.pointerType !== 'mouse', id = e.pointerId;
  const bouge = ev => {
    if(ev.pointerId !== id) return;
    ev.preventDefault();
    if(doigt){
      // La source suit le doigt.
      _doigtFil.x = ev.clientX; _doigtFil.y = ev.clientY;
      f.c0 = f.c1 = clientVersCarte(ev.clientX, ev.clientY) || f.c0;
      if(f.m) _poserSource(f);
      dessinerFil();
      _loupeFil(ev, false);
      return;
    }
    f.c1 = clientVersCarte(ev.clientX, ev.clientY) || f.c1;
    f.ctrl1 = ev.ctrlKey;
    if(f.m) _poserCible(f);
    dessinerFil();
    _loupeFil(ev, true);
  };
  const detacher = () => {
    document.removeEventListener('pointermove', bouge, true);
    document.removeEventListener('pointerup', fin, true);
    document.removeEventListener('pointercancel', fin, true);
    _finDoigtFil(id);
    cacherLoupe();
  };
  const fin = ev => {
    if(ev.pointerId !== id) return;
    detacher();
    // Au doigt comme à la souris, le fil reste ancré avec son panneau : la
    // LOS n'est validée (fenêtre, chat, log) qu'au ✓ Check.
    if(_fil === f){ f.ancre = true; panneauFil(f); return; }
    if(ev.type === 'pointerup') f.lache = true;
    if(f.m || f.pret) terminerFil(f);
  };
  document.addEventListener('pointermove', bouge, true);
  document.addEventListener('pointerup', fin, true);
  document.addEventListener('pointercancel', fin, true);
  if(doigt){
    _suivreDoigtFil(e, () => { detacher(); if(_fil === f) _effacerFil(); });
    _loupeFil(e, false);
  }
  carteLOS().then(({map, complet}) => {
    if(_fil !== f) return;
    f.m = map; f.completCarte = complet; f.complet = complet && _losVerif; f.pret = true;
    f.gi = _pionsDuFil(map);
    if(map) _poserSource(f);
    if(map && (f.c1.x !== f.c0.x || f.c1.y !== f.c0.y)) _poserCible(f);
    dessinerFil();
    // Doigt encore posé : la loupe nomme la source.
    if(doigt && _doigtFil && _doigtFil.id === id) _loupeFil({clientX: _doigtFil.x, clientY: _doigtFil.y}, false);
    if(f.ancre) panneauFil(f);
    else if(f.lache) terminerFil(f);
  });
  dessinerFil();
}

// Fil ancré : un nouvel appui ou glissé sur la carte déplace le bout
// choisi dans le panneau (From : la source, To : la cible, par défaut).
function deplacerBoutFil(e){
  const f = _fil, id = e.pointerId;
  const doigt = !!e.pointerType && e.pointerType !== 'mouse';
  const source = f.viser === 'src';
  // Bouts d'avant, rendus si un pincement abandonne la visée.
  const avant = {};
  for(const k of ['c0', 'c1', 'src', 'srcBrut', 'tgt', 'tgtBrut', 'auxS', 'auxT', 'sl', 'tl', 'de', 'vers',
                  'compte', 'res', 'surOverlay']) avant[k] = f[k];
  const suivre = ev => {
    if(ev.pointerId !== id) return;
    if(doigt){ _doigtFil.x = ev.clientX; _doigtFil.y = ev.clientY; }
    const c = clientVersCarte(ev.clientX, ev.clientY);
    if(!c) return;
    if(source){
      f.c0 = c; f.ctrl0 = false;
      if(f.m) _deplacerSource(f);
    } else {
      f.c1 = c; f.ctrl1 = false;
      if(f.m) _poserCible(f);
    }
    dessinerFil();
    _loupeFil(ev, !ev.pointerType || ev.pointerType === 'mouse');
    panneauFil(f);
  };
  const detacher = () => {
    document.removeEventListener('pointermove', bouge, true);
    document.removeEventListener('pointerup', fin, true);
    document.removeEventListener('pointercancel', fin, true);
    _finDoigtFil(id);
    cacherLoupe();
  };
  const fin = ev => { if(ev.pointerId === id) detacher(); };
  const bouge = ev => { ev.preventDefault(); suivre(ev); };
  if(doigt) _suivreDoigtFil(e, () => {
    detacher();
    if(_fil !== f) return;
    Object.assign(f, avant);
    dessinerFil();
    if(f.compte) _envoyerFil(f, false, true);
    panneauFil(f);
  });
  suivre(e);
  document.addEventListener('pointermove', bouge, true);
  document.addEventListener('pointerup', fin, true);
  document.addEventListener('pointercancel', fin, true);
}

// Pendant le tracé, la loupe de dépôt montre le fil et le lieu ciblé : au
// doigt, qui cache la cible, un disque au-dessus centré sur le point visé ;
// à la souris, l'hexagone survolé seul, à côté du curseur.
function _loupeFil(ev, hexSeul){
  const f = _fil;
  const c = f && clientVersCarte(ev.clientX, ev.clientY);
  if(!c){ cacherLoupe(); return; }
  // Le lieu visé : la cible, ou la source tant qu'il n'y a pas de cible.
  const texte = f.src ? (f.viser === 'src' ? f.de : f.vers) : '';
  const a = hexSeul ? Object.assign({}, ancragePlusProche(c.x, c.y) || c, {texte}) : {x: c.x, y: c.y, label: texte};
  dessinerLoupe(ev.clientX, ev.clientY, a, null, hexSeul, (g, k) => {
    for(const t of document.querySelectorAll('#filLOS line')){
      g.strokeStyle = t.getAttribute('stroke'); g.lineWidth = 3 / k;
      g.beginPath();
      g.moveTo(+t.getAttribute('x1'), +t.getAttribute('y1'));
      g.lineTo(+t.getAttribute('x2'), +t.getAttribute('y2'));
      g.stroke();
    }
  });
}

// Panneau du fil ancré, au-dessus de la carte : From (la source) et To
// (la cible), chacun sur ses flèches de niveau (même enchaînement que les
// flèches du clavier) ; un appui sur From ou To choisit le bout que le
// prochain appui sur la carte déplace. Check envoie le rapport, la croix
// (à côté de la poignée) annule.
// Le bouton ⟳ le tourne de 90°, comme l'éventail (réglage du poste).
let _filVertical = _lirePref('aslweb.filVertical') === '1';
function panneauFil(f){
  let b = document.getElementById('panneauFil');
  if(!b){
    b = document.createElement('div');
    b.id = 'panneauFil'; b.className = 'panneauFil losCompact';
    b.setAttribute('role', 'toolbar');
    b.setAttribute('aria-label', 'Ligne de vue');
    b.addEventListener('pointerdown', e => e.stopPropagation());
    document.getElementById('corps').appendChild(b);
  }
  b.classList.toggle('vertical', _filVertical);
  b.setAttribute('aria-orientation', _filVertical ? 'vertical' : 'horizontal');
  const rond = (txt, titre, cls, faire, actif = true) => {
    const x = document.createElement('button');
    x.type = 'button'; x.className = cls;
    x.textContent = txt; x.title = titre; x.setAttribute('aria-label', titre);
    x.disabled = !actif;
    x.addEventListener('click', e => { e.stopPropagation(); faire(); });
    return x;
  };
  // Tête : la croix (annuler, sans rapport), la poignée et ⟳.
  const tete = document.createElement('div');
  tete.className = 'teteFil';
  const croix = rond('✕', 'Annuler la ligne de vue, sans rapport', 'croixFermer', () => desarmerFil());
  const poignee = _poigneeVolant(() => ({x: b.offsetLeft, y: b.offsetTop}),
    (x, y) => { _posPanneauFil = {x, y}; _placerPanneauFil(); },
    () => { _posPanneauFil = null; _placerPanneauFil(); });
  const tourner = rond('⟳', 'Tourner le panneau de 90°', 'rondFil tournerFil', () => {
    _filVertical = !_filVertical;
    try { localStorage.setItem('aslweb.filVertical', _filVertical ? '1' : '0'); } catch(e){}
    panneauFil(f);
  });
  tourner.setAttribute('aria-pressed', String(_filVertical));
  // En colonne : ⟳ à gauche de la poignée, la croix à droite.
  if(_filVertical) tete.append(tourner, poignee, croix);
  else tete.append(croix, poignee, tourner);
  b.replaceChildren(tete);
  const pret = !!(f.m && f.src);
  const viser = f.viser === 'src' ? 'src' : 'tgt';
  const niveau = (loc, sens) => {
    if(!pret) return;
    _changerNiveau(f, loc === 'src' ? 'src' : 'tgt', sens);
    _faireLOS(f); dessinerFil(); _envoyerFil(f, true, false);
    panneauFil(f);
  };
  const bt = (txt, titre, faire, actif = true, cls = '', dans = b) => {
    const x = document.createElement('button');
    x.type = 'button'; x.className = 'act bouton ' + cls;
    x.textContent = txt; x.title = titre; x.setAttribute('aria-label', titre);
    x.disabled = !actif;
    x.addEventListener('click', faire);
    dans.appendChild(x);
    return x;
  };
  const niv = v => (Math.round(v) > Math.trunc(v) ? v : Math.trunc(v));
  const groupe = (k, nom, titre) => {
    const g = document.createElement('div');
    g.className = 'grpFil';
    b.appendChild(g);
    const choix = bt(nom + (pret ? ' ' + niv(k === 'src' ? f.sl : f.tl) : ''), titre, () => {
      f.viser = k; vibrer(); panneauFil(f);
    }, true, 'boutFil' + (viser === k ? ' vise' : ''), g);
    choix.setAttribute('aria-pressed', String(viser === k));
    const fl = document.createElement('div');
    fl.className = 'flechesFil';
    g.appendChild(fl);
    return fl;
  };
  let fl = groupe('src', 'From', 'From : toucher la carte pour choisir de nouveau l\'hexagone de la source');
  bt('▼', 'Descendre le niveau de la source', () => niveau('src', 'down'), pret, '', fl);
  bt('▲', 'Monter le niveau de la source', () => niveau('src', 'up'), pret, '', fl);
  fl = groupe('tgt', 'To', 'To : toucher la carte pour choisir de nouveau l\'hexagone de la cible');
  bt('▼', 'Descendre le niveau de la cible', () => niveau('tgt', 'down'), pret, '', fl);
  bt('▲', 'Monter le niveau de la cible', () => niveau('tgt', 'up'), pret, '', fl);
  // Pied : ✓ Check, à droite (en bas en colonne).
  const pied = document.createElement('div');
  pied.className = 'piedFil';
  pied.appendChild(rond('✓ Check', 'Check : résultat de la ligne de vue, et rapport « Checked LOS » au chat',
    'rondFil checkFil prim', () => terminerFil(f), pret && f.compte > 0));
  b.appendChild(pied);
  _placerPanneauFil();
}
// En haut à gauche de la carte, ou là où sa poignée l'a mis : il y reste
// pour les lignes de vue suivantes, sans sortir de la carte.
let _posPanneauFil = null;       // {x, y} dans #corps, une fois déplacé
function _placerPanneauFil(){
  const b = document.getElementById('panneauFil');
  if(!b) return;
  const w = document.getElementById('mapWrap');
  b.style.maxWidth = (w.clientWidth - 16) + 'px';
  b.style.maxHeight = (w.clientHeight - 16) + 'px';
  let x = w.offsetLeft + 8, y = w.offsetTop + 8;
  if(_posPanneauFil){
    x = Math.max(w.offsetLeft, Math.min(_posPanneauFil.x, w.offsetLeft + w.clientWidth - b.offsetWidth));
    y = Math.max(w.offsetTop, Math.min(_posPanneauFil.y, w.offsetTop + w.clientHeight - b.offsetHeight));
  }
  b.style.left = x + 'px'; b.style.top = y + 'px';
}
function _fermerPanneauFil(){ const b = document.getElementById('panneauFil'); if(b) b.remove(); }

function _poserSource(f){
  const s = _lieuAuPoint(f.m, f.c0);
  if(!s) return;
  f.src = s.l; f.auxS = s.aux;
  if(f.ctrl0) f.src = _plusHautHorsToit(f.src);
  f.sl = _niveauAffiche(f.src);
  f.de = _nomLieu(f, s.l);
  f.vers = f.de;
  f.tgt = f.src; f.auxT = f.auxS; f.tl = f.sl;
}

// From choisi dans le panneau : la source change d'hexagone, la cible reste.
function _deplacerSource(f){
  if(!f.compte){ _poserSource(f); return; }      // pas encore de cible
  const s = _lieuAuPoint(f.m, f.c0);
  if(!s || (s.l === f.srcBrut && s.aux === f.auxS)) return;
  f.srcBrut = s.l;
  f.src = s.l; f.auxS = s.aux;
  f.sl = _niveauAffiche(f.src);
  f.de = _nomLieu(f, s.l);
  _faireLOS(f);
  _envoyerFil(f, false, true);
}

function _poserCible(f){
  if(!f.src) return;
  const t = _lieuAuPoint(f.m, f.c1);
  if(!t || (t.l === f.tgt && t.aux === f.auxT)) return;
  if(t.l === f.tgtBrut && t.aux === f.auxT) return;
  f.tgtBrut = t.l;
  f.tgt = t.l; f.auxT = t.aux;
  f.vers = _nomLieu(f, t.l);
  if(f.ctrl1) f.tgt = _plusHautHorsToit(f.tgt);
  f.tl = _niveauAffiche(f.tgt);
  f.compte++;
  _faireLOS(f);
  _envoyerFil(f, false, true);
}

// VASLLOSCommand : « LOS\tid\tax\tay\tbx\tby\tpersisting\tmirroring\t
// niveau source\tniveau cible », points en pixels de la carte. Le fil
// « LOS » (global « Always ») la consigne à chaque nouvelle cible : les
// autres joueurs le voient se tracer, puis s'effacer au ✓ Check.
// Le log en cours d'enregistrement ne garde pas ces tracés : seule la
// ligne vérifiée (✓ Check) y entre, par
// _consignerFilVerifie ; avec log, l'effacement qui la suit y entre aussi.
const _cmdFil = (f, persiste, miroir) => {
  const dbl = v => Number.isInteger(v) ? v.toFixed(1) : String(v);
  return ['LOS', 'LOS_Thread1', Math.round(f.c0.x), Math.round(f.c0.y), Math.round(f.c1.x), Math.round(f.c1.y),
          String(persiste), String(miroir), dbl(f.sl), dbl(f.tl)].join('\t');
};
function _envoyerFil(f, persiste, miroir, log = false){
  if(f.mode !== 'L' || f.distant || !f.src || !f.m) return;
  f.envoye = true;
  _logCmd(_cmdFil(f, persiste, miroir), {horsLog: !log});
}
// La ligne vérifiée, dans le log seul (le réseau l'a déjà vue se tracer).
function _consignerFilVerifie(f){
  if(f.mode !== 'L' || f.distant || !f.src || !f.m || !enregistreLog()) return false;
  _logCmd(_cmdFil(f, false, true), {horsReseau: true});
  return true;
}

// Fil d'un autre joueur, ou d'un log relu : visible tant qu'il persiste
// ou se reflète (VASLThread.setEndPointsandLevels).
function filDistant(cmd){
  const t = cmd.split('\t');
  if(t.length < 10 || (_fil && !_fil.distant)) return;
  if(t[6] !== 'true' && t[7] !== 'true'){ if(_fil) _effacerFil(); return; }
  const n = i => parseFloat(t[i]) || 0;
  const f = _fil = _nouveauFil('L', {x: n(2), y: n(3)}, {x: n(4), y: n(5)});
  f.distant = true;
  carteLOS().then(({map, complet}) => {
    if(_fil !== f) return;
    f.m = map; f.completCarte = complet; f.complet = complet && _losVerif;
    f.gi = _pionsDuFil(map);
    if(map) _poserSource(f);
    const c = map && f.src && _lieuAuPoint(map, f.c1);
    if(c){
      f.tgt = c.l; f.auxT = c.aux; f.vers = _nomLieu(f, c.l); f.compte = 1;
      _niveauxDistants(f, n(8), n(9));
      _faireLOS(f);
    }
    dessinerFil();
  });
}

// VASLThread.setSourceandTargetLevels : monter puis descendre jusqu'au
// niveau reçu (les niveaux partent de 0, comme après initializeMap).
function _niveauxDistants(f, ns, nt){
  const adj = (l, rr) => {
    let a = 0;
    if(l.getName().includes('Rooftop') && l.getTerrain().getHeight() !== 1) a = -0.5;
    const c = l.getHex().getHexCenter(), p = l.getLOSPoint();
    if(l.getHex().isDepressionTerrain() && !(p.x === c.x && p.y === c.y)) a = 1;
    if(rr && l.getTerrain().getName().includes('Railroad, Embankment'))
      for(let x = 0; x < 6; x++){
        const t = l.getHex().getHexsideLocation(x).getTerrain();
        if(t != null && !t.isDepression() && t.getName().includes('Railroad, Embankment')) a = 0.5;
      }
    return a;
  };
  for(const k of ['src', 'tgt']){
    const voulu = k === 'src' ? ns : nt;
    let l = f[k], niv = 0, a = 0;
    while(voulu > niv && l.getUpLocation() != null){
      l = l.getUpLocation(); a = adj(l, k === 'src'); niv = l.getAbsoluteHeight() + a;
    }
    niv = l.getAbsoluteHeight() + a;
    while(voulu < niv && l.getDownLocation() != null){
      l = l.getDownLocation(); a = adj(l, false); niv = l.getAbsoluteHeight() + a;
    }
    f[k] = l;
    f[k === 'src' ? 'sl' : 'tl'] = l.getAbsoluteHeight() + a;
  }
}

// VASLThread.processLevelChange : niveau suivant, ou niveau virtuel
// jusqu'à 10 (et -3). bout : 'src' (From) ou 'tgt' (To), donné par le
// bouton ou la touche, et non déduit de l'hexagone (From et To dans le
// même hexagone : la flèche de To changeait From). Écart voulu avec
// VASL : un niveau virtuel garde le point visé, From et To ne sautent
// pas au centre de l'hexagone.
function _changerNiveau(f, bout, sens){
  let loc = bout === 'src' ? f.src : f.tgt;
  let adj = 0;
  const suiv = loc.getNextLevelLocation(sens);
  if(suiv != null){
    loc = suiv;
    if(loc.getName().includes('Rooftop') && loc.getLevelInHex() !== 1) adj = -0.5;
    // Pont de voie ferrée en remblai (FB) : un demi-niveau plus haut.
    for(const lo of [f.src, f.tgt])
      if(lo.getTerrain().getName().includes('Railroad, Embankment'))
        for(let x = 0; x < 6; x++){
          const t = lo.getHex().getHexsideLocation(x).getTerrain();
          if(t != null && !t.isDepression() && t.getName().includes('Railroad, Embankment')) adj = 0.5;
        }
  } else if(-3 < loc.getLevelInHex() && loc.getLevelInHex() < 10){
    loc.getHex().setvirtualLocation(loc.getLevelInHex() + (sens === 'up' ? 1 : -1), loc, sens, true);
    loc = sens === 'up' ? loc.getUpLocation() : loc.getDownLocation();
  }
  if(bout === 'src'){ f.sl = loc.getAbsoluteHeight() + adj; f.src = loc; }
  else { f.tl = loc.getAbsoluteHeight() + adj; f.tgt = loc; }
}

function _faireLOS(f){
  if(!f.src || !f.tgt) return;
  f.res = null;
  if(!f.complet) return;
  f.surOverlay = _overlayCoupe(f);
  if(f.surOverlay) return;
  const r = new LosResult();
  try { f.m.LOS(f.src, f.auxS, f.tgt, f.auxT, r, f.gi || LOS_SANS_PIONS); } catch(e){ /* VASL ignore l'erreur */ }
  f.res = r;
  f.texte = 'Range: ' + r.getRange();
  if(_losVerbeux && f.complet){
    if(r.isBlocked()){
      const h = f.m.gridToHex(r.getBlockedAtPoint().x, r.getBlockedAtPoint().y);
      if(h) f.texte += '  Blocked in ' + h.getName() + ' ( ' + r.getReason() + ')';
    } else if(r.getHindrance() > 0) f.texte += '  Hindrances: ' + r.getHindrance();
  }
}

// Overlays que VASL ne sait pas lire (VASLThread.initializeMap) : la
// vérification s'arrête si le fil traverse leurs pixels opaques. Les
// overlays déplaçables (pions « overlay ») sont toujours dans ce cas.
const _OVR_LUS = new Set(['og', 'b', 'p', 'm', 'wd', 'g', 'o', 'hi', 'x', 'hd', 'ow', 'sh', 'st', 'be', 'oc', 'v',
  'wt', 'wr', 'sr', 'br', 'dj', 'elrr', 'rv', 'sw', 'rr', 'surr', 'rp', '1', '2', '3', '4', '5', '6', '7', '8', '9',
  '10', 'dx', 'ef', 'h', 'd', 'w', 'sd']);
const _ovrLuParVASL = o => _OVR_LUS.has((o.archive || '').replace(/^ovr/i, '').toLowerCase())
  || /BSO|SSO|Bocage/.test(o.nom);
function _boiteOpaque(o){
  if(o.boite !== undefined) return o.boite;
  let x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for(let y = 0; y < o.h; y++) for(let x = 0; x < o.w; x++)
    if(o.data[(y * o.w + x) * 4 + 3]){ if(x < x0) x0 = x; if(x > x1) x1 = x; if(y < y0) y0 = y; if(y > y1) y1 = y; }
  return o.boite = x1 < 0 ? null : {x: x0, y: y0, w: x1 - x0, h: y1 - y0};
}
function _zonesOverlay(){
  const z = [];
  for(const b of _losBoards || []){
    const R = b._los;
    if(!R) continue;
    const cx = b.crop ? (b.crop.x || 0) : 0, cy = b.crop ? (b.crop.y || 0) : 0;
    for(const o of b.ovrLOS || []){
      const bo = !_ovrLuParVASL(o) && _boiteOpaque(o);
      if(!bo) continue;
      const x = o.x - cx + bo.x, y = o.y - cy + bo.y;
      z.push({x: R.x + (b.reversed ? R.w - x - bo.w : x), y: R.y + (b.reversed ? R.h - y - bo.h : y), w: bo.w, h: bo.h});
    }
  }
  for(const p of vivants()){
    if(p.cache || p.ovrExtPose) continue;
    if(p._overlay === undefined) p._overlay = 'overlay' in _proprietes(tsPion(p));
    if(!p._overlay) continue;
    const w = (p.larg || p.sz) / SC / _losZoom(), h = (p.haut || p.sz) / SC / _losZoom(), c = _versLOS(p);
    z.push({x: c.x - w/2, y: c.y - h/2, w, h});
  }
  return z;
}
function _segmentCoupeRect(x1, y1, x2, y2, r){
  const dedans = (x, y) => x >= r.x && y >= r.y && x <= r.x + r.w && y <= r.y + r.h;
  if(dedans(x1, y1) || dedans(x2, y2)) return true;
  const c = [[r.x, r.y, r.x + r.w, r.y], [r.x + r.w, r.y, r.x + r.w, r.y + r.h],
             [r.x + r.w, r.y + r.h, r.x, r.y + r.h], [r.x, r.y + r.h, r.x, r.y]];
  return c.some(([a, b, cc, d]) => _linesIntersect(x1, y1, x2, y2, a, b, cc, d));
}
function _overlayCoupe(f){
  const a = f.auxS ? f.src.getAuxLOSPoint() : f.src.getLOSPoint();
  const b = f.auxT ? f.tgt.getAuxLOSPoint() : f.tgt.getLOSPoint();
  return _zonesOverlay().find(r => _segmentCoupeRect(a.x, a.y, b.x, b.y, r)) || null;
}

// ── Dessin : le fil en trois couleurs, et les étiquettes noires de
// VASLThread.draw, de taille fixe à l'écran.
function _calqueFil(){
  let s = document.getElementById('filLOS');
  if(!s){
    s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.id = 'filLOS';
    document.getElementById('mapInner').appendChild(s);
  }
  s.setAttribute('width', _mapW); s.setAttribute('height', _mapH);
  return s;
}
function _effacerFil(){
  _fil = null;
  _fermerPanneauFil();
  const s = document.getElementById('filLOS');
  if(s) s.remove();
  document.querySelectorAll('.etiqLOS').forEach(e => e.remove());
}

function dessinerFil(){
  const f = _fil;
  if(!f) return;
  const s = _calqueFil();
  s.innerHTML = '';
  document.querySelectorAll('.etiqLOS').forEach(e => e.remove());
  const NS = 'http://www.w3.org/2000/svg';
  const trait = (a, b, coul) => {
    const l = document.createElementNS(NS, 'line');
    l.setAttribute('x1', a.x); l.setAttribute('y1', a.y); l.setAttribute('x2', b.x); l.setAttribute('y2', b.y);
    l.setAttribute('stroke', coul); l.setAttribute('stroke-width', 2); l.setAttribute('vector-effect', 'non-scaling-stroke');
    s.appendChild(l);
  };
  const etiquette = (p, dx, dy, texte) => {
    // drawText : x, y est la ligne de base ; le fond déborde d'un pixel.
    const e = document.createElement('div');
    e.className = 'etiqLOS';
    e.textContent = texte;
    // Décalage vu à l'écran ; le texte reste droit si la vue est tournée.
    const c = _ecartVersCarte(dx - 1, dy - 1 - LOS_POLICE + LOS_DESCENTE);
    e.style.left = (p.x + c.x / zoom) + 'px';
    e.style.top = (p.y + c.y / zoom) + 'px';
    e.style.transform = `rotate(${-_rotVue}deg) scale(${1 / zoom})`;
    document.getElementById('mapInner').appendChild(e);
  };
  if(!f.src){
    // Fil de VASSAL, sans carte LOS : un trait, sans portée.
    trait({x: f.c0.x * SC, y: f.c0.y * SC}, {x: f.c1.x * SC, y: f.c1.y * SC}, LOS_COUL_FIL);
    return;
  }
  const pS = _depuisLOS(f.auxS ? f.src.getAuxLOSPoint() : f.src.getLOSPoint());
  const pT = _depuisLOS(f.auxT ? f.tgt.getAuxLOSPoint() : f.tgt.getLOSPoint());
  const r = f.res;
  if(f.surOverlay || !f.complet || !r) trait(pS, pT, LOS_COUL_FIL);
  else {
    const pB = r.isBlocked() ? _depuisLOS(r.getBlockedAtPoint()) : null;
    const pH = r.hasHindrance() ? _depuisLOS(r.firstHindranceAt()) : null;
    if(pB && pH){ trait(pS, pH, LOS_COUL_FIL); trait(pH, pB, LOS_COUL_GENE); trait(pB, pT, LOS_COUL_BLOC); }
    else if(pB){ trait(pS, pB, LOS_COUL_FIL); trait(pB, pT, LOS_COUL_BLOC); }
    else if(pH){ trait(pS, pH, LOS_COUL_FIL); trait(pH, pT, LOS_COUL_GENE); }
    else trait(pS, pT, LOS_COUL_FIL);
  }
  if(f.src === f.tgt && !f.compte) return;
  // Décalages des étiquettes à l'écran (targetLOSLabelXoffset…).
  const ecS = _ecartVersVue(pS.x * zoom, pS.y * zoom), ecT = _ecartVersVue(pT.x * zoom, pT.y * zoom);
  const xT = ecS.x === ecT.x ? -10 : 10;
  const yT = ecS.y === ecT.y ? -40 : ecS.x === ecT.x ? (ecS.y > ecT.y ? -20 : 20)
    : ecS.y > ecT.y ? (ecS.x > ecT.x ? -30 : -10) : (ecS.x > ecT.x ? 10 : -10);
  const yS = ecS.y === ecT.y ? -40 : ecS.x === ecT.x ? (ecS.y > ecT.y ? -20 : -10)
    : ecS.y > ecT.y ? -20 : -10;
  const decale = ecS.y > ecT.y, h = LOS_POLICE;
  if(f.surOverlay){
    const o = f.surOverlay, a = _depuisLOS({x: o.x, y: o.y}), b = _depuisLOS({x: o.x + o.w, y: o.y + o.h});
    const rc = document.createElementNS(NS, 'rect');
    rc.setAttribute('x', a.x); rc.setAttribute('y', a.y); rc.setAttribute('width', b.x - a.x); rc.setAttribute('height', b.y - a.y);
    rc.setAttribute('fill', 'none'); rc.setAttribute('stroke', '#ff0000'); rc.setAttribute('vector-effect', 'non-scaling-stroke');
    s.appendChild(rc);
    etiquette(pT, xT, yT + (decale ? 0 : h) - LOS_DESCENTE,
      'LOS Check Disabled - Overlay nearby. Range: ' + LosMap.range(f.src.getHex(), f.tgt.getHex(), f.m.getMapConfiguration()));
    return;
  }
  const niv = v => 'Level ' + (Math.round(v) > Math.trunc(v) ? v : Math.trunc(v));
  const libS = niv(f.sl) + (f.src.getTerrain().isEntrenchmentTerrain() ? ' (Entrenched)' : '');
  const libT = niv(f.tl) + (f.tgt.getTerrain().isEntrenchmentTerrain() ? ' (Entrenched)' : '');
  if(_losVerbeux) etiquette(pS, -20, -yS - (decale ? 0 : h) - LOS_DESCENTE, f.src.getName() + '  (' + libS + ')');
  else if(f.src.getLevelInHex() !== 0) etiquette(pS, -20, -yS - (decale ? 0 : h) - LOS_DESCENTE, libS);
  if(_losVerbeux) etiquette(pT, xT, yT + (decale ? 0 : h) - LOS_DESCENTE, f.tgt.getName() + '  (' + libT + ')');
  else if(f.tgt.getLevelInHex() !== 0) etiquette(pT, xT, yT + (decale ? 0 : h) - LOS_DESCENTE, libT);
  const texte = f.complet ? f.texte : 'Range: ' + LosMap.range(f.src.getHex(), f.tgt.getHex(), f.m.getMapConfiguration());
  etiquette(pT, xT, decale ? yT - h : yT + h * 2 - 2, texte);
}

// ✓ Check : rapport « Checked LOS » (fil « L » : lieux et portée), puis
// le fil s'efface et le bouton se réarme.
// Au ✓ Check du panneau (doigt ou souris), le résultat s'affiche aussi
// dans une fenêtre, avec l'image de la ligne.
// Dans le log, la ligne vérifiée et le CHAT forment un pas, l'effacement
// le pas suivant : VASL exécute d'un bloc les commandes d'un même pas, et
// une ligne effacée dans son propre pas n'y était jamais dessinée.
function terminerFil(f){
  if(_fil !== f) return;
  const verifie = f.compte > 0 && f.src && f.tgt;
  const consigne = verifie && f.envoye && _consignerFilVerifie(f);
  let portee = 0;
  if(verifie){
    portee = LosMap.range(f.src.getHex(), f.tgt.getHex(), f.m.getMapConfiguration());
    const msg = f.mode === 'l' ? `${joueur()} Checked LOS`
      : `${joueur()} Checked LOS from ${f.de} to ${f.vers}, Range=${portee}`;
    _logCmd('CHAT' + msg.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'));
    afficherChat(msg);
  }
  if(f.envoye){
    if(consigne) _nouvelleAction();
    _envoyerFil(f, false, false, consigne);
  }
  if(verifie) _fenetreResultatLOS(f, portee);
  desarmerFil();
}

// Résultat de la LOS en clair : {etat: 'libre' | 'gene' | 'bloque' |
// 'inconnu', texte}. Les raisons sont celles de VASL (en anglais).
function _resultatLOS(f){
  if(f.surOverlay) return {etat: 'inconnu', texte: T('Vérification impossible : overlay sur la ligne de vue')};
  const r = f.res;
  if(f.completCarte && !_losVerif) return {etat: 'inconnu', texte: T('Vérification de la LOS désactivée (préférences) : portée seule')};
  if(!f.complet || !r) return {etat: 'inconnu', texte: T('Données LOS de VASL absentes : portée seule')};
  if(r.isBlocked()){
    const b = r.getBlockedAtPoint(), h = b && f.m.gridToHex(b.x, b.y);
    return {etat: 'bloque', texte: (h ? T('LOS bloquée en') + ' ' + h.getName() : T('LOS bloquée'))
      + (r.getReason() ? ' — ' + r.getReason() : '')};
  }
  if(r.hasHindrance()) return {etat: 'gene', texte: T('LOS dégagée, avec gêne') + ' : ' + r.getHindrance()
    + ' (' + T('hindrance') + ')'};
  return {etat: 'libre', texte: T('LOS dégagée')};
}

// Image « fantôme » de la ligne de vue : la carte entre les deux bouts,
// les pions en transparence sauf les unités de départ et d'arrivée, From
// et To cerclés, la ligne aux couleurs de VASL (rouge
// jusqu'au blocage, bleu au-delà) et le point de blocage marqué d'une
// croix. Tournée comme la vue.
const LOS_IMAGE_MAX = 560;
function _imageLOS(f){
  const pS = _depuisLOS(f.auxS ? f.src.getAuxLOSPoint() : f.src.getLOSPoint());
  const pT = _depuisLOS(f.auxT ? f.tgt.getAuxLOSPoint() : f.tgt.getLOSPoint());
  const hex = _hautHexAu(pS.x / SC, pS.y / SC) * SC;
  const rot = _rotVue * Math.PI / 180, cs = Math.cos(rot), sn = Math.sin(rot);
  const vue = p => ({x: p.x * cs - p.y * sn, y: p.x * sn + p.y * cs});
  const vS = vue(pS), vT = vue(pT), m = hex * 0.9;
  const vx0 = Math.min(vS.x, vT.x) - m, vy0 = Math.min(vS.y, vT.y) - m;
  const w = Math.max(vS.x, vT.x) + m - vx0, h = Math.max(vS.y, vT.y) + m - vy0;
  const maxW = Math.min(LOS_IMAGE_MAX, window.innerWidth - 40), maxH = Math.min(LOS_IMAGE_MAX, window.innerHeight * 0.55);
  const s = Math.min(maxW / w, maxH / h, 2), k = s * 2;
  const cv = document.createElement('canvas');
  cv.className = 'imageLOS';
  cv.width = Math.round(w * k); cv.height = Math.round(h * k);
  cv.style.width = Math.round(w * s) + 'px'; cv.style.height = Math.round(h * s) + 'px';
  const g = cv.getContext('2d');
  g.fillStyle = '#222'; g.fillRect(0, 0, cv.width, cv.height);
  g.setTransform(k, 0, 0, k, -vx0 * k, -vy0 * k); g.rotate(rot);
  for(const b of document.querySelectorAll('#mapInner canvas.boardLayer')){
    try { g.drawImage(b, parseFloat(b.style.left), parseFloat(b.style.top), parseFloat(b.style.width), parseFloat(b.style.height)); } catch(e){}
  }
  // Unités des bouts : centrées à moins d'un demi-hexagone du point visé
  // (un côté ou un sommet visé appartient à l'hexagone voisin).
  const auBout = p => {
    const L = parseFloat(p.el.style.left), Tp = parseFloat(p.el.style.top), W = parseFloat(p.el.style.width) || p.sz;
    const x = L + W / 2, y = Tp + W / 2;
    return Math.hypot(x - pS.x, y - pS.y) < hex * 0.55 || Math.hypot(x - pT.x, y - pT.y) < hex * 0.55;
  };
  // Pions : fantômes, sauf ceux des deux bouts, posés par-dessus.
  const bouts = [];
  const poser = (p, alpha) => {
    const L = parseFloat(p.el.style.left), Tp = parseFloat(p.el.style.top), W = parseFloat(p.el.style.width) || p.sz;
    try {
      g.save(); g.globalAlpha = alpha;
      g.translate(L + W / 2, Tp + W / 2); g.rotate(-rot);
      g.drawImage(p.face, -W / 2, -W / 2, W, W);
    } catch(e){} finally { g.restore(); }
  };
  for(const p of pions){
    if(p.deleted || p.cache || !p.face || !p.face.src || p.face.style.display === 'none') continue;
    if(p.masqueProprio && !voitMasque(p.masqueProprio)) continue;
    if(!p.el.classList.contains('terrain') && auBout(p)){ bouts.push(p); continue; }
    poser(p, p.el.classList.contains('terrain') ? 1 : 0.3);
  }
  for(const p of bouts) poser(p, 1);
  // From et To cerclés autour du point visé.
  const cercle = (c, coul) => {
    g.beginPath(); g.arc(c.x, c.y, hex * 0.55, 0, 2 * Math.PI);
    g.strokeStyle = coul; g.lineWidth = 4 / k * 2; g.stroke();
  };
  cercle(pS, '#2ecc71'); cercle(pT, '#d9a441');
  // La ligne, avec un liseré sombre pour se voir sur tout terrain.
  const trait = (a, b, coul) => {
    for(const [c, l] of [['rgba(0,0,0,.7)', 7], [coul, 3.5]]){
      g.strokeStyle = c; g.lineWidth = l / k * 2; g.lineCap = 'round';
      g.beginPath(); g.moveTo(a.x, a.y); g.lineTo(b.x, b.y); g.stroke();
    }
  };
  const r = f.res, ok = f.complet && r && !f.surOverlay;
  const pB = ok && r.isBlocked() ? _depuisLOS(r.getBlockedAtPoint()) : null;
  const pH = ok && r.hasHindrance() ? _depuisLOS(r.firstHindranceAt()) : null;
  if(pB && pH){ trait(pS, pH, LOS_COUL_FIL); trait(pH, pB, LOS_COUL_GENE); trait(pB, pT, LOS_COUL_BLOC); }
  else if(pB){ trait(pS, pB, LOS_COUL_FIL); trait(pB, pT, LOS_COUL_BLOC); }
  else if(pH){ trait(pS, pH, LOS_COUL_FIL); trait(pH, pT, LOS_COUL_GENE); }
  else trait(pS, pT, LOS_COUL_FIL);
  const point = (p, coul, rayon) => {
    g.beginPath(); g.arc(p.x, p.y, rayon / k * 2, 0, 2 * Math.PI);
    g.fillStyle = coul; g.fill(); g.strokeStyle = '#000'; g.lineWidth = 1.5 / k * 2; g.stroke();
  };
  point(pS, '#2ecc71', 6); point(pT, '#d9a441', 6);
  if(pH) point(pH, '#ff9800', 5);
  if(pB){
    // Croix blanche cernée de noir sur le point de blocage.
    const e = 9 / k * 2;
    for(const [c, l] of [['#000', 6], ['#fff', 3]]){
      g.strokeStyle = c; g.lineWidth = l / k * 2;
      g.beginPath(); g.moveTo(pB.x - e, pB.y - e); g.lineTo(pB.x + e, pB.y + e);
      g.moveTo(pB.x + e, pB.y - e); g.lineTo(pB.x - e, pB.y + e); g.stroke();
    }
  }
  return cv;
}

// Légende de l'image de la ligne, aux couleurs des préférences.
function _legendeLOS(){
  const pastille = c => `<span class="pastilleLOS" style="background:${c}"></span>`;
  return `${_esc(T('Vert : From ; or : To'))} ; ${pastille(LOS_COUL_FIL)} ${_esc(T('ligne dégagée'))} ; `
    + `${pastille(LOS_COUL_GENE)} ${_esc(T('après la gêne'))} ; ${pastille(LOS_COUL_BLOC)} ${_esc(T('après le blocage'))} ; `
    + _esc(T('croix : point de blocage'));
}

// Fenêtre du résultat (bouton ✓ Check) : lieux, niveaux, portée, résultat
// et image de la ligne. OK, Entrée ou Échap la ferment.
function _fenetreResultatLOS(f, portee){
  const anc = document.getElementById('fenLOS');
  if(anc) anc.remove();
  const res = _resultatLOS(f);
  const niv = v => (Math.round(v) > Math.trunc(v) ? v : Math.trunc(v));
  const w = document.createElement('div');
  w.id = 'fenLOS'; w.className = 'fenetre';
  w.setAttribute('role', 'dialog');
  w.innerHTML = `
    <div class="ft">${_esc(T('Ligne de vue'))}</div>
    <div class="fc">
      <p class="lieuxLOS"><b>From</b> ${_esc(f.de)} (${_esc(T('niveau'))} ${niv(f.sl)}) → <b>To</b> ${_esc(f.vers)}
        (${_esc(T('niveau'))} ${niv(f.tl)}) — Range ${portee}</p>
      <p class="resLOS ${res.etat}">${_esc(res.texte)}</p>
      <div class="cadreImageLOS"></div>
      <p class="legendeLOS">${_legendeLOS()}</p>
      <div class="fb"><button id="losOk" class="prim">OK</button></div>
    </div>`;
  try { w.querySelector('.cadreImageLOS').appendChild(_imageLOS(f)); } catch(e){}
  w.addEventListener('pointerdown', e => e.stopPropagation());
  document.body.appendChild(w);
  const fermer = () => { w.remove(); document.removeEventListener('keydown', clavier, true); };
  const clavier = ev => {
    if(ev.key === 'Escape' || ev.key === 'Enter'){ ev.preventDefault(); ev.stopImmediatePropagation(); fermer(); }
  };
  document.addEventListener('keydown', clavier, true);
  w.querySelector('#losOk').onclick = fermer;
  w.querySelector('#losOk').focus();
}

function _brancherFil(){
  const wrap = document.getElementById('mapWrap');
  // Pris avant les pions et le panoramique.
  wrap.addEventListener('pointerdown', e => {
    if(!_filArme || e.button > 0) return;
    // Deuxième doigt pendant une visée : elle est abandonnée, et les deux
    // doigts pincent la carte (le premier est rendu au panoramique).
    if(_doigtFil && e.pointerId !== _doigtFil.id && e.pointerType && e.pointerType !== 'mouse'){
      const d = _doigtFil;
      d.abandonner();
      _pointeursCarte.set(d.id, {x: d.x, y: d.y, pion: false, surPion: false, x0: d.x, y0: d.y, t0: Date.now(), btn: 0});
      return;
    }
    if(_fil && _fil.ancre && !_fil.distant){ e.preventDefault(); e.stopPropagation(); deplacerBoutFil(e); return; }
    if(_fil) return;
    e.preventDefault(); e.stopPropagation();
    commencerFil(e);
  }, true);
  window.addEventListener('keydown', e => {
    if(/^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName) || e.target.isContentEditable) return;
    if(e.key === 'F10'){
      e.preventDefault(); e.stopImmediatePropagation();
      // Ctrl+F10 seul : HidePiecesButton de VASL ; Ctrl+Maj+F10, la LOS muette.
      if(e.ctrlKey && !e.shiftKey){ executerAction('masquer'); return; }
      armerFil(e.ctrlKey && e.shiftKey ? 'l' : 'L');
      return;
    }
    if(e.key === 'Escape' && _filArme){ e.stopImmediatePropagation(); desarmerFil(); return; }
    const f = _fil;
    if(!f || !f.src || !f.m) return;
    const sens = (e.key === 'ArrowUp' || e.key === 'Up') ? 'up' : (e.key === 'ArrowDown' || e.key === 'Down') ? 'down' : '';
    if(!sens) return;
    e.preventDefault(); e.stopImmediatePropagation();
    _changerNiveau(f, e.ctrlKey ? 'src' : 'tgt', sens);
    _faireLOS(f);
    dessinerFil();
    _envoyerFil(f, true, false);
  }, true);
}

