// ═══════════════════════════════════════════════════════════════
// BASE DE DONNÉES DES PIONS — le buildFile du module VASL
// Chaque PieceSlot y donne le nom, le gpid et la définition VASSAL
// complète du pion (« +/null/TYPE/ÉTAT »), celle-là même que VASL écrit
// quand on tire un pion de sa palette. On la lit une fois, à la première
// ouverture de la palette.
// ═══════════════════════════════════════════════════════════════
const VASL_BUILDFILE_URL =
  'https://raw.githubusercontent.com/vasl-developers/vasl/develop/dist/buildFile';
// Les deux fenêtres d'overlays déplaçables (routes, murs, décombres, FFE…)
// suivent les pions, dans l'ordre du module.
const FENETRES_PALETTE = ['VASL Counters', 'Draggable Overlays', 'Deluxe Draggable', '?'];
let _bdd = null, _bddPromesse = null;

function chargerBDD(){
  if(!_bddPromesse) _bddPromesse = _chargerBDD().catch(e => {
    _bddPromesse = null;
    chatLog('❌ Base de données VASL indisponible : ' + e.message);
    throw e;
  });
  return _bddPromesse;
}

// ── Analyse gardée sur le poste (IndexedDB, magasin bdd) : le buildFile
// change avec VASL, on le télécharge donc à chaque session (réseau d'abord
// dans sw.js), mais son analyse XML n'est refaite que s'il a changé.
// Le spec prévoyait l'ETag (If-None-Match, réponse 304) ; vérifié le
// 2026-11-08, raw.githubusercontent.com ne s'y prête pas depuis une page :
// pas d'Access-Control-Expose-Headers (l'ETag se lit null), et un
// If-None-Match posé par la page déclenche un preflight refusé (403).
// L'analyse gardée est donc repérée par l'empreinte du texte reçu
// (FNV-1a et longueur, une dizaine de ms pour 4 Mo) ; le navigateur, lui,
// revalide le téléchargement par son propre cache HTTP. Hors ligne, c'est
// l'analyse gardée qui sert.
// FORMAT_BDD : à augmenter à chaque changement de _analyserBuildFile, pour
// qu'une analyse gardée par une version précédente soit refaite.
const FORMAT_BDD = 1;
let _analysesBuildFile = 0;            // analyses XML faites (tests)
function _empreinteTexte(t){
  let h = 0x811c9dc5;
  for(let i = 0; i < t.length; i++){ h ^= t.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
  return h.toString(16).padStart(8, '0') + ':' + t.length;
}

async function _chargerBDD(){
  chatLog('⏳ Lecture de la base de données des pions VASL…');
  let garde = null;
  try {
    const g = await _idbLire('bdd', 'buildFile');
    if(g && g.format === FORMAT_BDD && g.url === VASL_BUILDFILE_URL && g.bdd) garde = g;
  } catch(e){}
  let r = null, texte = null;
  try {
    r = await fetch(VASL_BUILDFILE_URL);
    if(r.ok) texte = await r.text();
  } catch(e){ if(!garde) throw e; }
  if(texte === null && !garde) throw new Error('HTTP ' + (r ? r.status : '?'));
  const empreinte = texte === null ? null : _empreinteTexte(texte);
  if(garde && (texte === null || garde.empreinte === empreinte)) return _bddPrete(garde.bdd);
  const bdd = _analyserBuildFile(texte);
  _analysesBuildFile++;
  // Gardée telle qu'analysée, avant tout cache ajouté à l'usage (x.lu,
  // _typesPion…) : copiée tout de suite (l'écriture est asynchrone) ; les
  // Map passent par le clonage structuré.
  const garder = v => _idbEcrire('bdd', 'buildFile', {format: FORMAT_BDD, url: VASL_BUILDFILE_URL, empreinte, date: Date.now(), bdd: v});
  if(typeof structuredClone === 'function'){
    try { garder(structuredClone(bdd)); } catch(e){}
  } else await garder(bdd);
  return _bddPrete(bdd);
}

// Le buildFile analysé, prêt à servir.
function _bddPrete(bdd){
  _bdd = bdd;
  // Les TYPE des PieceSlot entrent au registre : un pion posé depuis la
  // palette partage d'emblée son TYPE avec ses semblables.
  for(const x of bdd.pions){
    const te = _typeEtEtat(x.cmd);
    if(te) entreeType(te.type);
  }
  // Les plans de développement des TYPE ont été faits sans les
  // prototypes : ils se refont (le registre, lui, reste).
  _viderPlans();
  rendreToutes();
  // Les pions déjà sur la carte gagnent les couches de leurs prototypes.
  for(const p of vivants()) majApparence(p);
  // Les CVP du Casualties Bin se recomptent dès que le module est connu.
  _cvpAJour(false);
  chatLog(`📚 Base VASL ${_bdd.version} : ${bdd.pions.length} pions, ${bdd.cats.length} catégories`);
  return _bdd;
}

// Analyse du buildFile : tout ce que _bdd en garde, sans rien toucher
// d'autre (le résultat se range tel quel dans IndexedDB).
function _analyserBuildFile(texte){
  const doc = new DOMParser().parseFromString(texte, 'application/xml');
  const mod = doc.documentElement;
  const cats = [], slots = [], idxCat = new Map();
  // Arbre des widgets de la palette, tel que VASL le construit :
  // TabWidget (onglets), ListWidget (liste), BoxWidget (liste déroulante),
  // PanelWidget (grille), et les PieceSlot dans l'ordre du module.
  let nid = 0;
  const construire = (el, chemin) => {
    const t = el.tagName.split('.').pop();
    const nom = el.getAttribute('entryName') || el.getAttribute('name') || '';
    const noeud = {id: nid++, t, nom, enfants: []};
    for(const c of el.children){
      const tag = c.tagName.split('.').pop();
      if(tag === 'PieceSlot'){
        const ch = chemin.join(' / ');
        if(!idxCat.has(ch)){ idxCat.set(ch, cats.length); cats.push(ch); }
        const x = {n: c.getAttribute('entryName') || '?', g: c.getAttribute('gpid'),
                   c: idxCat.get(ch), cmd: (c.textContent || '').trim()};
        slots.push(x);
        noeud.enfants.push(x);
      } else if(/Widget$/.test(tag)){
        const n = c.getAttribute('entryName') || c.getAttribute('name') || '';
        noeud.enfants.push(construire(c, n ? [...chemin, n] : chemin));
      }
    }
    return noeud;
  };
  const arbre = {id: nid++, t: 'Racine', nom: '', enfants: []};
  const protos = new Map();
  for(const e of doc.getElementsByTagName('*'))
    if(e.tagName.endsWith('.PrototypeDefinition'))
      protos.set(e.getAttribute('name'), (e.textContent || '').trim());
  for(const w of mod.children){
    if(w.tagName.split('.').pop() !== 'PieceWindow') continue;
    const nom = w.getAttribute('name');
    if(FENETRES_PALETTE.includes(nom)) arbre.enfants.push(construire(w, nom === '?' ? ['?'] : []));
  }
  // Tous les PieceSlot du module, par gpid : un pion peut en poser un
  // autre (Place Marker, Replace, DM, ACQ), même hors de la palette.
  const parGpid = new Map();
  for(const e of doc.getElementsByTagName('*'))
    if(e.tagName.endsWith('.PieceSlot') && e.getAttribute('gpid'))
      parGpid.set(e.getAttribute('gpid'), {n: e.getAttribute('entryName') || '?', g: e.getAttribute('gpid'),
                                           cmd: (e.textContent || '').trim()});
  // … et par chemin de composants (ComponentPathBuilder) : c'est ainsi que
  // le trait de dissimulation désigne son contre-pion « ? ».
  const parChemin = new Map();
  for(const e of doc.getElementsByTagName('*')){
    if(!e.tagName.endsWith('.PieceSlot')) continue;
    const noms = [];
    for(let a = e; a && a !== mod; a = a.parentElement)
      noms.unshift(a.getAttribute('entryName') || a.getAttribute('name') || '');
    parChemin.set(noms.join('\u0001'), parGpid.get(e.getAttribute('gpid')));
  }
  // Bouton « ALL » de la carte (MassRemover) : chaque entrée donne une
  // action et un nom de pion ; les actions gardent l'ordre du module.
  const massRemover = [];
  for(const e of doc.getElementsByTagName('*')){
    if(!e.tagName.endsWith('.MassRemover')) continue;
    for(const en of e.getElementsByTagName('*')){
      const nom = en.getAttribute('name') || '';
      let a = massRemover.find(x => x.nom === nom);
      if(!a) massRemover.push(a = {nom, pions: []});
      a.pions.push(en.textContent || '');
    }
  }
  // Empilement de la carte de jeu (StackMetrics) : écarts replié et
  // déplié, en pixels natifs, et touches qui changent le rang d'un pion.
  let sm = null;
  for(const e of doc.getElementsByTagName('*')){
    if(!e.tagName.endsWith('.StackMetrics')) continue;
    const carte = e.parentElement && e.parentElement.getAttribute('mapName');
    if(!sm || carte === 'Main Map') sm = e;
  }
  const entier = (n, d) => { const v = parseInt(sm.getAttribute(n), 10); return Number.isFinite(v) ? v : d; };
  const stackMetrics = sm ? {
    unexSepX: entier('unexSepX', 2), unexSepY: entier('unexSepY', 4),
    exSepX: entier('exSepX', 6), exSepY: entier('exSepY', 18),
    disabled: sm.getAttribute('disabled') === 'true',
    top: lireTouche(sm.getAttribute('top')) || METRIQUES_DEFAUT.top,
    bottom: lireTouche(sm.getAttribute('bottom')) || METRIQUES_DEFAUT.bottom,
    up: lireTouche(sm.getAttribute('up')) || METRIQUES_DEFAUT.up,
    down: lireTouche(sm.getAttribute('down')) || METRIQUES_DEFAUT.down} : null;
  // Zones des cartes à board fixe (fenêtre Casualties : « Allied
  // Casualties », « Axis Casualties »), pour un SendToLocation vers une
  // zone : Zone.getCenter, centre du rectangle qui contient le polygone,
  // décalé de la place du board sur la carte (marges de la carte).
  const zones = new Map();
  for(const m of mod.children){
    if(!/\.(Map|PrivateMap|PlayerHand)$/.test(m.tagName)) continue;
    const boards = [...m.getElementsByTagName('*')].filter(e => e.tagName.endsWith('.Board'));
    if(boards.length !== 1) continue;
    const bx = parseInt(m.getAttribute('edgeWidth'), 10) || 0, by = parseInt(m.getAttribute('edgeHeight'), 10) || 0;
    for(const z of boards[0].getElementsByTagName('*')){
      if(!z.tagName.endsWith('.Zone')) continue;
      const pts = (z.getAttribute('path') || '').split(';').map(c => c.split(',').map(Number)).filter(c => c.length === 2 && c.every(Number.isFinite));
      if(!pts.length) continue;
      const xs = pts.map(c => c[0]), ys = pts.map(c => c[1]);
      const x0 = Math.min(...xs), y0 = Math.min(...ys);
      zones.set(m.getAttribute('mapName') + '\u0001' + z.getAttribute('name'),
                {x: bx + x0 + Math.floor((Math.max(...xs) - x0) / 2), y: by + y0 + Math.floor((Math.max(...ys) - y0) / 2),
                 carte: m.getAttribute('mapName'), nom: z.getAttribute('name'), poly: pts.map(([x, y]) => [bx + x, by + y])});
    }
  }
  // Compteurs d'une carte (Casualties : AlliedCVP, AxisCVP) : une
  // propriété globale de la carte que recompte un bouton de sa barre
  // (MassKeyCommand « Allied CVP ») ; il envoie sa touche aux pièces de la
  // carte qui passent son filtre (CurrentZone=="Allied Casualties"), et
  // dit la propriété dans son rapport (« Allied CVP: $AlliedCVP$ »).
  const compteurs = [];
  for(const m of mod.children){
    if(!/\.(Map|PrivateMap|PlayerHand)$/.test(m.tagName)) continue;
    const glob = new Map();
    for(const g of m.getElementsByTagName('*'))
      if(g.tagName.endsWith('.GlobalProperty')) glob.set(g.getAttribute('name'), g.getAttribute('initialValue') || '');
    for(const k of m.children){
      if(!k.tagName.endsWith('.MassKeyCommand')) continue;
      const touche = lireTouche(k.getAttribute('hotkey') || '');
      const prop = ((k.getAttribute('reportFormat') || '').match(/\$(\w+)\$/g) || []).map(x => x.slice(1, -1)).find(x => glob.has(x));
      if(touche && prop) compteurs.push({carte: m.getAttribute('mapName'), prop, initial: glob.get(prop),
                                         touche, filtre: (k.getAttribute('filter') || '').trim(), nom: k.getAttribute('buttonText') || prop});
    }
  }
  // Fenêtre QRDC (ChartWindow) : les tables de référence, en onglets.
  // Chart : une image du module ; TabWidget : des onglets ; PanelWidget :
  // ses enfants côte à côte, ou l'un sous l'autre (vert).
  let qid = 0;
  const lireTables = el => {
    const t = el.tagName.split('.').pop();
    const n = {id: qid++, t, nom: el.getAttribute('entryName') || el.getAttribute('chartName') || '',
               vert: el.getAttribute('vert') !== 'false', enfants: []};
    if(t === 'Chart') n.fichier = el.getAttribute('fileName');
    for(const c of el.children) if(/(Widget|\.Chart)$/.test(c.tagName)) n.enfants.push(lireTables(c));
    return n;
  };
  const fenCharts = [...mod.children].filter(w => w.tagName.endsWith('.ChartWindow'));
  const cw = fenCharts.find(w => w.getAttribute('name') === 'QRDC') || fenCharts[0];
  const qrdc = cw ? lireTables(cw) : null;
  // Les pioches OBA : un onglet de plus, après ceux du module.
  const premierOnglets = n => n.t === 'TabWidget' ? n : n.enfants.reduce((r, e) => r || premierOnglets(e), null);
  const ongletsQRDC = qrdc && premierOnglets(qrdc);
  if(ongletsQRDC){
    ongletsQRDC.enfants.push({id: qid++, t: 'OBA', nom: 'OBA', enfants: []});
    qrdc.oba = {onglets: ongletsQRDC.id, rang: ongletsQRDC.enfants.length - 1};
  }
  // Le Casualties Bin : un onglet de plus, pour la carte des compteurs.
  if(ongletsQRDC && compteurs.length){
    const carte = compteurs[0].carte;
    const m = [...mod.children].find(x => x.getAttribute('mapName') === carte);
    ongletsQRDC.enfants.push({id: qid++, t: 'Casualties', nom: carte, enfants: []});
    qrdc.bin = {onglets: ongletsQRDC.id, rang: ongletsQRDC.enfants.length - 1, carte,
                titre: (m && m.getAttribute('buttonName')) || carte};
  }
  return {cats, pions: slots, protos, parGpid, parChemin, arbre, massRemover, stackMetrics, qrdc, zones, compteurs, version: mod.getAttribute('version')};
}

// Lecture d'un PieceSlot comme d'une pièce, faite à la demande.
function lireSlot(x){
  if(!x.lu) x.lu = lirePiece(x.cmd.replace(/^\+\/null\//, '+/0/'));
  return x.lu;
}

let _palFiltre = '';

async function dessinerPalette(){
  const box = document.getElementById('palCorps');
  if(!_bdd){
    box.innerHTML = '<div style="padding:8px;color:var(--doux)">Chargement de la base VASL…</div>';
    try { await chargerBDD(); } catch(e){ box.innerHTML = '<div style="padding:8px">Base indisponible.</div>'; return; }
  }
  const B = _bdd;
  const defil = box.scrollTop;
  box.innerHTML = '';
  if(_palFiltre){
    const t = B.pions.filter(x => x.n.toLowerCase().includes(_palFiltre)).slice(0, 200);
    const info = document.createElement('div');
    info.style.cssText = 'opacity:.6;margin:4px 0';
    info.textContent = t.length + ' pion(s)' + (t.length === 200 ? ' (200 premiers)' : '');
    box.appendChild(info);
    box.appendChild(remplirGrille(t));
    return;
  }
  rendreWidget(B.arbre, box);
  box.scrollTop = defil;
}

// ── Widgets de la palette VASL. Seule la branche sélectionnée est dessinée :
// les vignettes ne se chargent qu'à l'ouverture de leur panneau.
// La sélection de chaque widget est retenue d'une visite à l'autre.
const _palSel = (() => {
  try { return new Map(JSON.parse(localStorage.getItem('palSel') || '[]')); } catch(e){ return new Map(); }
})();
function _choisir(noeud, i){
  _palSel.set(noeud.id, i);
  try { localStorage.setItem('palSel', JSON.stringify([..._palSel])); } catch(e){}
  dessinerPalette();
}

function rendreWidget(n, boite){
  const widgets = n.enfants.filter(e => e.enfants);
  const pions = n.enfants.filter(e => !e.enfants);
  const sel = Math.min(_palSel.get(n.id) || 0, Math.max(0, widgets.length - 1));
  const contenu = () => {
    const z = document.createElement('div');
    z.className = 'wcontenu';
    if(widgets[sel]) rendreWidget(widgets[sel], z);
    return z;
  };
  // Une fenêtre de palette qui contient plusieurs widgets les présente en
  // onglets, comme PieceWindow (la fenêtre « ? » : un onglet par nation).
  if(n.t === 'TabWidget' || n.t === 'Racine' || (n.t === 'PieceWindow' && widgets.length > 1)){
    // Onglets : un par widget enfant.
    if(pions.length) boite.appendChild(remplirGrille(pions));
    if(!widgets.length) return;
    const o = document.createElement('div');
    o.className = 'onglets';
    widgets.forEach((w, i) => {
      const b = document.createElement('div');
      b.className = 'onglet' + (i === sel ? ' actif' : '');
      b.textContent = w.nom || '—';
      b.addEventListener('click', () => _choisir(n, i));
      o.appendChild(b);
    });
    boite.append(o, contenu());
  } else if(n.t === 'BoxWidget'){
    // Liste déroulante.
    const t = document.createElement('div');
    t.className = 'wtitre'; t.textContent = n.nom;
    const s = document.createElement('select');
    s.className = 'wboite';
    widgets.forEach((w, i) => s.add(new Option(w.nom || '—', i, false, i === sel)));
    s.addEventListener('change', () => _choisir(n, +s.value));
    boite.append(t, s);
    if(pions.length) boite.appendChild(remplirGrille(pions));
    if(widgets.length) boite.appendChild(contenu());
  } else if(n.t === 'ListWidget'){
    // Liste : le choix d'une entrée affiche son contenu en dessous.
    if(widgets.length){
      const l = document.createElement('div');
      l.className = 'wliste';
      widgets.forEach((w, i) => {
        const e = document.createElement('div');
        e.className = 'wli' + (i === sel ? ' actif' : '');
        e.textContent = w.nom || '—';
        e.addEventListener('click', () => _choisir(n, i));
        l.appendChild(e);
      });
      boite.appendChild(l);
    }
    if(pions.length) boite.appendChild(remplirGrille(pions));
    if(widgets.length) boite.appendChild(contenu());
  } else {
    // PanelWidget, PieceWindow : tout le contenu, dans l'ordre du module ;
    // un panneau imbriqué porte son titre.
    let lot = [];
    const vider = () => { if(lot.length) boite.appendChild(remplirGrille(lot)); lot = []; };
    for(const e of n.enfants){
      if(!e.enfants){ lot.push(e); continue; }
      vider();
      if(e.t === 'PanelWidget' && e.nom){
        const t = document.createElement('div');
        t.className = 'wtitre'; t.textContent = e.nom;
        boite.appendChild(t);
      }
      rendreWidget(e, boite);
    }
    vider();
  }
}

function remplirGrille(liste){
  const g = document.createElement('div');
  g.className = 'grille';
  for(const x of liste){
    const d = document.createElement('div');
    d.className = 'vig';
    d.title = `${x.n}  (gpid ${x.g}) — glisser vers la carte, ou cliquer pour poser au centre ; au doigt, toucher puis toucher la carte`;
    d.setAttribute('aria-label', x.n);
    const mini = document.createElement('div');
    mini.className = 'mini';
    d.appendChild(mini);
    g.appendChild(d);
    composerVignette(mini, lireSlot(x), 42);
    // Glisser vers la carte, ou clic : pose au centre de la vue.
    d.addEventListener('pointerdown', e => glisser(e, (mini.querySelector('img') || {}).src, 48 * SC * zoom, (cx, cy) => {
      const c = clientVersCarte(cx, cy);
      if(c) poserDepuisPalette(x, c.x, c.y);
      else chatLog('Lâché hors de la carte : rien posé');
    }, ev => {
      // Au doigt : le pion est armé, l'appui suivant sur la carte le pose.
      if(ev && ev.pointerType && ev.pointerType !== 'mouse') armerPose(x);
      else _poserAuCentre(x);
    }, {appuiLong: 300}));
  }
  return g;
}

