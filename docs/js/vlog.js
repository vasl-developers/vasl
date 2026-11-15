// ═══════════════════════════════════════════════════════════════
// LECTURE D'UN .vlog — coup par coup
// Un coup est une entrée « LOG » de BasicLogger ; les sous-commandes d'un
// coup composé (une pile qui bouge, un pion posé puis placé) la suivent
// dans des segments à part. Avancer rejoue le coup ; reculer restaure
// l'instantané pris juste avant lui.
// ═══════════════════════════════════════════════════════════════
let _etapes = [];          // coups : listes de segments
let _logPos = 0;           // coups déjà joués
let _instantanes = [];     // état avant chaque coup joué
let _relecture = false;    // vrai pendant qu'un coup est rejoué
let _logPlancher = 0;      // pas de recul avant un coup joué en cours de relecture

function preparerRelecture(segs){
  clearTimeout(_coupDiffere); _coupDiffere = null;
  _etapes = []; _logPos = 0; _instantanes = []; _logPlancher = 0;
  for(const tok of _decoupeSeq(segs.join('\x1b'), '\x1b')){
    // Les entrées vides (« LOG\t » seul) ne sont pas des coups.
    const cmds = _commandesLog(tok).filter(c => c.trim());
    if(!cmds.length) continue;
    if(_estLog(tok) || !_etapes.length) _etapes.push(cmds);
    else _etapes[_etapes.length - 1].push(...cmds);
  }
  majBarreLog();
}

// Entrée LOG, même mise entre apostrophes par SequenceEncoder.
const _estLog = t => /^'*LOG\t/.test(t);

// SequenceEncoder.Decoder de VASSAL : un délimiteur précédé d'une
// contre-oblique appartient au jeton, qui perd cette contre-oblique ; un
// jeton entre apostrophes les perd aussi.
function _decoupeSeq(val, delim){
  const out = [];
  let debut = 0, buf = '';
  for(let i = 0; i < val.length; i++){
    if(val[i] !== delim) continue;
    if(i > 0 && val[i - 1] === '\\'){ buf += val.substring(debut, i - 1); debut = i; }
    else { out.push(buf + val.substring(debut, i)); buf = ''; debut = i + 1; }
  }
  out.push(buf + val.substring(debut));
  return out.map(t => t.length > 1 && t[0] === "'" && t[t.length - 1] === "'" ? t.slice(1, -1) : t);
}

// GameModule.decode puis BasicLogger.decode : une entrée LOG est une
// commande dont chaque sous-commande est elle-même une entrée LOG
// (LogCommand). On en tire les commandes simples, dans l'ordre.
function _commandesLog(tok){
  const parts = _decoupeSeq(tok, '\x1b');
  if(parts.length > 1) return parts.flatMap(_commandesLog);
  if(tok.length > 1 && tok[0] === "'" && tok.endsWith("'")) tok = tok.slice(1, -1);
  if(tok.startsWith('LOG\t')) return _commandesLog(tok.substring(4));
  return tok ? [tok] : [];
}

function _instantane(){
  return {info: _infoEtat, piles: new Map(_pilesVassal), liens: new Map(_liens), oba: _oba.map(m => ({...m})),
          glob: new Map(_globConnu), globales: Object.assign({}, _globales),
          // L'ÉTAT seulement (et la référence du TYPE) : pas de segment
          // recomposé gardé coup après coup.
          pions: pions.map(p => ({p, typeRef: p.typeRef, etat: p.etat, _seg: p._seg, x: p.x, y: p.y, ordre: p.ordre, deleted: p.deleted, ailleurs: p.ailleurs}))};
}

function _restaurer(snap){
  const connus = new Set(snap.pions.map(e => e.p));
  // Pions nés après l'instantané : ils disparaissent.
  for(const p of pions) if(!connus.has(p)) p.el.remove();
  pions = snap.pions.map(e => e.p);
  for(const e of snap.pions){
    const p = e.p;
    const segChange = p.typeRef !== e.typeRef || p.etat !== e.etat || p._seg !== e._seg;
    Object.assign(p, {typeRef: e.typeRef, etat: e.etat, _seg: e._seg, x: e.x, y: e.y, ordre: e.ordre, deleted: e.deleted, ailleurs: e.ailleurs});
    if(p.deleted) p.el.remove();
    else if(!p.el.isConnected) document.getElementById('mapInner').appendChild(p.el);
    if(segChange && !p.deleted) majApparence(p);
  }
  _pilesVassal = new Map(snap.piles);
  _liens = new Map(snap.liens); _planifierLiens();
  if(_infoEtat !== snap.info){ _infoEtat = snap.info; majChampsInfo(); }
  if(snap.oba && JSON.stringify(snap.oba) !== JSON.stringify(_oba)){ _oba = snap.oba.map(m => ({...m})); _majOBA(); }
  if(snap.glob){
    _globConnu.clear(); for(const [k, v] of snap.glob) _globConnu.set(k, v);
    for(const k of Object.keys(_globales)) delete _globales[k];
    Object.assign(_globales, snap.globales);
    _cvpAJour(false);
  }
  if(_fil && _fil.distant) _effacerFil();
}

// Le coup est lu en entier avant d'être joué : la vue rejoint d'abord la
// zone où des pions vont bouger, être posés ou retirés, et le coup (ses
// messages au chat compris) ne se joue qu'après une courte pause, pour
// que l'on voie partir les pions et non seulement leur arrivée.
const PAUSE_COUP = 500;
let _coupDiffere = null;
function avancerLog(muet){
  _jouerCoupDiffere();
  if(_logPos >= _etapes.length) return false;
  if(!muet && !_rendreDiffere){
    const pts = _ciblesEtape(_etapes[_logPos]);
    const wrap = document.getElementById('mapWrap');
    const avant = [zoom, wrap.scrollLeft, wrap.scrollTop].join();
    if(pts.length) cadrerSur(pts);
    // Vue inchangée : rien à attendre.
    if(pts.length && [zoom, wrap.scrollLeft, wrap.scrollTop].join() !== avant){
      _coupDiffere = setTimeout(() => { _coupDiffere = null; _avancerLog(false, true); }, PAUSE_COUP);
      return true;
    }
    return _avancerLog(false, pts.length > 0);
  }
  return _avancerLog(muet);
}
// Un coup en attente est joué tout de suite : coup suivant demandé pendant
// la pause, recul, saut, coup joué ici.
function _jouerCoupDiffere(){
  if(!_coupDiffere) return;
  clearTimeout(_coupDiffere); _coupDiffere = null;
  _avancerLog(false, true);
}

// Points (pixels natifs) que le coup va toucher, lus avant de le jouer,
// dans l'ordre de signalerCoup : arrivées, sinon retraits, sinon simples
// changements d'état. Les pions HIP invisibles d'ici n'y sont pas.
function _ciblesEtape(segs){
  const arrives = [], retires = [], modifies = [];
  const piles = new Map(_pilesVassal);
  const nes = new Map();   // pièces posées par le coup : uid → visible
  const invisible = d => d.hip && !voitMasque(d.hipProprio);
  const surCarte = uid => { const p = parUid(uid); return p && !p.cache ? p : null; };
  const ici = (l, p) => { if(p) l.push({x: p.x, y: p.y}); };
  for(let s of segs){
    if(s.startsWith('LOG\t')) s = s.substring(4);
    s = s.replace(/\\+$/, '');
    try {
      if(s.startsWith('+/')){
        if(/^\+\/[^/]+\/stack\//.test(s)){
          piles.set(s.split('/')[1], _idsPile(s.substring(s.indexOf('/stack/') + 7)));
          continue;
        }
        const d = lirePiece(s);
        if(parUid(d.uid)) continue;
        nes.set(d.uid, !invisible(d));
        if(d.carte === _carteJeu && !invisible(d)) arrives.push({x: d.x, y: d.y});
      } else if(s.startsWith('-/')){
        const id = s.substring(2);
        for(const u of piles.get(id) || [id]) ici(retires, surCarte(u));
      } else if(s.startsWith('D/')){
        const t = _champsD(s.substring(2));
        if(piles.has(t[0])){ if(t[1]) piles.set(t[0], _idsPile(t[1])); continue; }
        const p = surCarte(_nul(t[0]));
        const te = p && t[1] && tePion(p);
        if(!te) continue;
        const d = lirePiece('+/' + p.uid + '/' + te.type + '/' + t[1]);
        if(d.carte === _carteJeu && (d.x !== p.x || d.y !== p.y)) arrives.push({x: d.x, y: d.y});
        else ici(modifies, p);
      } else if(s.startsWith('M/')){
        const t = _decoupeCmd(s.substring(2));
        if(t.length < 5) continue;
        const id = _nul(t[0]);
        const x = parseInt(t[2], 10) || 0, y = parseInt(t[3], 10) || 0;
        for(const u of _idsPileM(t, piles) || [id]){
          const p = parUid(u);
          const vu = p ? !p.cache : nes.get(u);
          if(!vu) continue;
          if(t[1] === _carteJeu) arrives.push({x, y});
          else ici(retires, p);
        }
      }
    } catch(e){ /* segment illisible : le coup le dira en se jouant */ }
  }
  return arrives.length ? arrives : retires.length ? retires : modifies;
}

// precadre : la vue a déjà rejoint le coup, elle n'y revient pas.
function _avancerLog(muet, precadre){
  if(_logPos >= _etapes.length) return false;
  const etape = _etapes[_logPos];
  _instantanes[_logPos] = _instantane();
  _instantanes[_logPos].log = _logEnr ? {enr: _logEnr, n: _logEnr.length} : null;
  _instantanes[_logPos].histo = _histo.length;
  const photo = _photo();
  // Décompte des MF / MP du coup relu : cumuls mémorisés pour un recul,
  // positions relevées avant le coup.
  _instantanes[_logPos].mvt = typeof _photoMvt === 'function' ? _photoMvt() : null;
  const avantMvt = typeof _avantRelecture === 'function' ? _avantRelecture(etape) : null;
  _relecture = true;
  try { rejouerLog(etape); } finally { _relecture = false; }
  if(avantMvt) try { compterRelecture(etape, avantMvt); }
               catch(e){ chatLog(`⚠️ Décompte des MF / MP impossible : ${e.message}`); }
  // BasicLogger.step : le coup relu passe par sendAndLog, il entre donc
  // dans le log en cours et part chez les autres joueurs.
  _actionOuverte = false;
  _etapeLog = true;
  try {
    for(const c of _cmdsConsignees(etape)) _logCmd(c);
  } finally { _etapeLog = false; _mParPion = new Map(); }
  _actionOuverte = false;
  _logPos++;
  if(!_rendreDiffere){ rendreToutes(); if(_pileSel) afficherPile(); }
  // Les pions touchés par le coup sont signalés, et la vue les rejoint.
  if(!muet) signalerCoup(photo, precadre);
  majBarreLog();
  if(_logPos === _etapes.length){
    chatLog('🏁 Fin du log');
    // « Replay completed. Begin new log file? »
    proposerLog(false);
  }
  return true;
}

// Commandes d'un coup relu telles que le log en cours les consigne. Un
// M/ sur une pile que l'état de départ de ce log ne connaît pas (fichier
// écrit par une version de VASLweb qui renommait les piles) ne
// déplacerait rien à la relecture, ni ici ni dans VASL : il est écrit pion
// par pion, comme VASLweb écrit ses propres déplacements de pile.
const _pilesDuDepart = new WeakMap();
function _cmdsConsignees(etape){
  const cmds = etape.filter(c => !c.startsWith('UNDO'));
  if(!_logEnr || !_logDepart) return cmds;
  let connues = _pilesDuDepart.get(_logDepart);
  if(!connues){
    connues = new Set(_logDepart.map(x => (/^\+\/([^/]+)\/stack\//.exec(x) || [])[1]).filter(Boolean));
    _pilesDuDepart.set(_logDepart, connues);
  }
  return cmds.flatMap(c => {
    const pp = _mParPion.get(c);
    return pp && !connues.has(_decoupeCmd(c.replace(/\\+$/, '').substring(2))[0]) ? pp : [c];
  });
}

function reculerLog(muet){
  _jouerCoupDiffere();
  if(_logPos <= _logPlancher) return false;
  _logPos--;
  const photo = _photo();
  const snap = _instantanes[_logPos];
  _restaurer(snap);
  if(snap.mvt && typeof _restaurerMvt === 'function') _restaurerMvt(snap.mvt);
  if(snap.histo !== undefined){ _histo.length = Math.min(snap.histo, _histo.length); majUndo(); }
  // VASSAL ne recule pas dans une relecture : ici, le coup relu sort du
  // log en cours. Revenir avant le début du log le fait repartir de là.
  if(_logEnr){
    if(snap.log && snap.log.enr === _logEnr){
      _logEnr.length = _logGroupes.length = Math.min(snap.log.n, _logEnr.length);
    } else {
      _logEnr.length = _logGroupes.length = 0;
      _logDepart = etatPartie();
      chatLog(`⏺️ Log : il repart désormais du coup ${_logPos}`);
    }
    majBoutons();
  }
  if(!_rendreDiffere){ rendreToutes(); if(_pileSel) afficherPile(); }
  if(!muet) signalerCoup(photo);
  majBarreLog();
  return true;
}

// Plusieurs coups d'un coup (⏮, ⏭) : un seul rendu, à la fin.
let _rendreDiffere = false;
function allerLog(cible){
  _rendreDiffere = true;
  try {
    while(_logPos < cible && avancerLog(true));
    while(_logPos > cible && reculerLog(true));
  } finally { _rendreDiffere = false; }
  rendreToutes();
  if(_pileSel) afficherPile();
}

// État visible des pions avant un coup, pour savoir ce qu'il a changé.
// Les pions HIP ne comptent pas : la vue ne doit pas les trahir.
function _photo(){
  return new Map(vivants().filter(p => !p.cache).map(p => [p, {x: p.x, y: p.y, etat: empreinteEtat(p)}]));
}

// Rayon, en hexagones, de la zone montrée autour d'une action.
const RAYON_COUP = 6;

function signalerCoup(photo, sansCadrer){
  document.querySelectorAll('.pion.coup').forEach(e => e.classList.remove('coup'));
  const ici = vivants().filter(p => !p.cache);
  // Arrivées : pions déplacés ou posés. Retraits : pions disparus, montrés
  // un instant à leur ancienne place. Sinon, simples changements d'état.
  const arrives = ici.filter(p => { const a = photo.get(p); return !a || a.x !== p.x || a.y !== p.y; });
  const modifies = ici.filter(p => { const a = photo.get(p); return a && a.x === p.x && a.y === p.y && !memeEtat(a.etat, p); });
  const encore = new Set(ici);
  const retires = [...photo.keys()].filter(p => !encore.has(p));
  for(const p of [...arrives, ...modifies]){
    p.el.classList.add('coup');
    setTimeout(() => p.el.classList.remove('coup'), 1600);
  }
  for(const p of retires){
    const f = p.el.cloneNode(true);
    f.classList.remove('sel', 'coup');
    f.classList.add('retire');
    document.getElementById('mapInner').appendChild(f);
    setTimeout(() => f.remove(), 1600);
  }
  // La vue se centre sur l'action : là où les pions arrivent, sinon là où
  // ils ont été retirés ou modifiés.
  const cibles = arrives.length ? arrives : retires.length ? retires : modifies;
  if(cibles.length && !sansCadrer) cadrerSur(cibles.map(p => ({x: p.x, y: p.y})));
}

// Centre la vue sur des points (pixels natifs) avec un zoom qui montre
// RAYON_COUP hexagones autour ; des points plus étalés (retrait en masse)
// sont tous cadrés, sans descendre sous le zoom « Ajuster ».
function cadrerSur(pts){
  const wrap = document.getElementById('mapWrap');
  if(!_mapW || !wrap.clientWidth) return;
  const xs = pts.map(q => q.x), ys = pts.map(q => q.y);
  const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
  const demiL = Math.max((RAYON_COUP + .5) * 2 * HEX_HX, (x1 - x0) / 2 + 2 * HEX_HX);
  const demiH = Math.max((RAYON_COUP + .5) * 2 * HEX_HY, (y1 - y0) / 2 + 2 * HEX_HY);
  // Seule compte la partie de la carte que le chat ouvert ne couvre pas.
  const hv = hauteurVisibleCarte();
  // Le zoom « Ajuster » de la partie visible : une carte plus petite que la
  // vue ne défile pas, elle doit tenir au-dessus du chat.
  const tv = _tailleVue();
  const ajuste = Math.min(wrap.clientWidth / tv.w, hv / tv.h);
  // Vue tournée d'un quart de tour : la largeur cadrée est montrée en hauteur.
  const [eL, eH] = _rotVue % 180 ? [demiH, demiL] : [demiL, demiH];
  const z = Math.max(ajuste, Math.min(wrap.clientWidth / (2 * eL * SC), hv / (2 * eH * SC)));
  setZoom(z);
  const v = _carteVersVue((x0 + x1) / 2 * SC, (y0 + y1) / 2 * SC);
  wrap.scrollLeft = v.x * zoom - wrap.clientWidth / 2;
  wrap.scrollTop  = v.y * zoom - hv / 2;
}

function majBarreLog(){ rafraichirActions(['relecture', 'relectureRail', 'relectureFin', ...ACTIONS.relecture.groupe]); majHautBas(); }

// Trait Immobilized sans « L » en tête des options d'empilement : la
// pièce ne s'empile pas (Properties.NO_STACK) — clocher, tunnel, marqueur
// de la piste des tours. VASSAL ne la met dans aucune pile.
const _nonEmpilable = new Map();      // type → booléen
function neSEmpilePas(p){
  const te = tePion(p);
  if(!te) return false;
  if(!_nonEmpilable.has(te.type) || !_bdd){
    const ts = tsPion(p);
    const im = ts && ts.typ.map(t => _seq(t.replace(/\\+$/, ''), ';')).find(f => f[0] === 'immob');
    const v = !!im && (im[3] === undefined ? 'R' : im[3].replace(/\\/g, '')).charAt(0) !== 'L';
    if(!_bdd) return v;
    _nonEmpilable.set(te.type, v);
  }
  return _nonEmpilable.get(te.type);
}

// La pièce sur laquelle p est posée, pour un M/ : la plus proche en
// dessous qui s'empile, ou rien si p elle-même ne s'empile pas. Une pièce
// invisible pour moi (HIP adverse) n'est jamais dans ma pile chez VASSAL
// (canPiecesMerge) : elle ne sert pas de dessous.
function _dessous(p){
  if(neSEmpilePas(p)) return 'null';
  const s = pile(cle(p));
  for(let i = s.indexOf(p) - 1; i >= 0; i--) if(!neSEmpilePas(s[i]) && (!s[i].cache || p.cache)) return s[i].uid;
  return 'null';
}

// Position finale d'un lâcher : le centre d'une case de panneau (carte de
// scénario, bandeau), sinon l'ancrage d'hexagone, ou une pile déjà là —
// les piles d'un fichier VASL ne tombent pas toujours au pixel près sur
// nos centres calculés. liste : les pions lâchés ; un panneau qu'on
// déplace ne s'accroche pas aux cases.
function positionDepot(x, y, liste){
  const c = liste && liste.some(p => p.fond) ? null : caseDePanneau(x, y);
  const a = c || ancragePlusProche(x, y) || {x: Math.round(x), y: Math.round(y)};
  let best = null, bd = 12*12;
  for(const p of vivants()){
    if(c && p.fond) continue;
    const d = (p.x-a.x)*(p.x-a.x) + (p.y-a.y)*(p.y-a.y);
    if(d < bd){ bd = d; best = p; }
  }
  return best ? {x: best.x, y: best.y, label: a.label} : a;
}

function deplacer(liste, x, y, campConfirme){
  // Un pion invisible pour moi (HIP adverse) ne part jamais avec les miens.
  liste = liste.filter(p => !p.cache && !p.deleted);
  if(!liste.length) return;
  if(surTables(x, y)){ chatLog('⛔ Pas de pion sur les tables QRDC : rien déplacé'); return; }
  // Pion du camp adverse (camp choisi à l'identification) : le déplacement
  // est bloqué jusqu'à la réponse de la fenêtre de confirmation.
  if(!campConfirme && !confirmerCampAdverse(liste, () => deplacer(liste, x, y, true))) return;
  _avantAction();
  const pos = positionDepot(x, y, liste);
  const kArr = pos.x + ',' + pos.y;
  // Une unité dissimulée sortie de sa pile emporte sa dissimulation
  // (A12.15) : le « ? » qui ne couvrirait plus rien la suit, et entre
  // dans la liste pour se poser au sommet, avec son propre « M/ ».
  const sousQ = _qmarkDesPartants(liste, kArr);
  liste = liste.concat(_qmarkQuiSuivent(liste, kArr, sousQ));
  // Passagers et Riders emportés par leur véhicule (D6.3) : relevés avant
  // que la pile ne change.
  const portes = typeof _transportes === 'function' ? _transportes(liste) : new Set();
  const avant = liste.map(p => ({p, x: p.x, y: p.y, sous: _dessous(p), k: cle(p),
    lieu: typeof lieuDansPile === 'function' ? lieuDansPile(p) : null}));
  // Une unité arrive toujours au rez-de-chaussée (B23.421) : sous le
  // marqueur d'étage le plus bas de la pile d'arrivée, s'il y en a un. Le
  // joueur la fait ensuite monter en la plaçant sur le marqueur.
  const deja = pile(kArr).filter(q => !liste.includes(q));
  const iEtage = typeof _niveauCompteur === 'function' && !liste.some(q => _niveauCompteur(q) !== null)
    ? deja.findIndex(q => _niveauCompteur(q) !== null) : -1;
  if(iEtage >= 0){
    for(const p of liste){ p.x = pos.x; p.y = pos.y; }
    [...deja.slice(0, iEtage), ...liste, ...deja.slice(iEtage)].forEach((q, k) => { q.ordre = k; });
  } else {
    let haut = Math.max(-1, ...deja.map(q => q.ordre));
    for(const p of liste){ p.x = pos.x; p.y = pos.y; p.ordre = ++haut; }
  }
  for(const a of avant){
    _majSeg(a.p);
    _logCmd(`M/${a.p.uid}/${_carteJeu}/${a.p.x}/${a.p.y}/${_dessous(a.p)}`
          + `/${_carteJeu}/${a.x}/${a.y}/${a.sous}/${joueur()}`);
  }
  // Comme la carte de VASL : un pion qui change de place est marqué
  // « moved », puis reçoit la touche de mouvement (Ctrl+Alt+M), qui
  // inscrit notamment le joueur dans sa propriété Owner.
  for(const a of avant) a.p.ancienLieu = lieu(a.x, a.y);
  // MF / MP dépensés, d'après le terrain traversé : au bout de la ligne
  // du déplacement. Une erreur du décompte ne doit ni bloquer le
  // déplacement ni passer inaperçue ; les alertes restent au chat local.
  let decompte = null;
  try { decompte = compterMouvement(avant, portes); }
  catch(e){ chatLog(`⚠️ Décompte des MF / MP impossible : ${e.message}`); }
  rapportDeplacement(liste, avant[0].x, avant[0].y, decompte && decompte.texte);
  if(decompte) for(const t of decompte.alertes) chatLog('⚠️ ' + t);
  for(const a of avant){
    if(a.x === a.p.x && a.y === a.p.y) continue;
    marquerDeplace(a.p, true);
    executerTouche(a.p, TOUCHE_MOUVEMENT);
  }
  // Sinon, l'ancien « ? » coiffe encore d'autres unités : les partants
  // reçoivent un « ? » neuf, faute de quoi la pile d'arrivée les
  // démasquerait.
  _qmarkPourPartants(liste, kArr, sousQ);
  new Set([...avant.map(a => a.k), kArr]).forEach(k => { ajusterDissimulation(k); rendrePile(k); });
  // La sélection reste celle des pions déplacés : les unités qui
  // occupaient déjà l'hexagone n'y entrent pas, et le glissé suivant
  // poursuit le mouvement sans les embarquer.
  const bouges = liste.filter(selectionnable);
  if(bouges.length) selectionner(bouges); else selectionnerPile(kArr);
  chatLog(`➡️ ${liste.length > 1 ? liste.length + ' pions' : liste[0].nom} → ${pos.label || kArr}`);
}

// ── Saisie d'un label, sur la carte, à la place de la boîte de VASSAL.
// Une touche envoyée à une pile entière n'ouvre qu'une saisie, qui
// s'applique à chaque pion concerné. Entrée valide, Échap annule ; un
// texte vide efface le label.
function editerLabel(p, i, titre, lot){
  if(lot && lot.label){ lot.label.cibles.push({p, i}); return; }
  const cibles = [{p, i}];
  if(lot) lot.label = {cibles};
  fermerEditeurLabel();
  const te = tePion(p);
  const actuel = te ? _texteLabel(decoderTraits(te.etat)[i]) : '';
  const box = document.createElement('div');
  box.id = 'editLabel'; box.className = 'editLabel';
  const t = document.createElement('span'); t.textContent = titre;
  const inp = document.createElement('input'); inp.value = actuel;
  const ok = document.createElement('button'); ok.textContent = 'OK';
  box.append(t, inp, ok);
  document.body.appendChild(box);
  const r = p.el.getBoundingClientRect();
  box.style.left = Math.max(4, Math.min(r.left, window.innerWidth - box.offsetWidth - 4)) + 'px';
  box.style.top = Math.max(4, Math.min(r.bottom + 6, window.innerHeight - box.offsetHeight - 4)) + 'px';
  _ancrerAuClavier(box);
  const valider = () => {
    const v = inp.value.replace(/[\t\x1b\\]/g, ' ').trim().replace(/\//g, '\\/');
    fermerEditeurLabel();
    for(const c of cibles) fixerTrait(c.p, c.i, v);
    chatLog(`🏷️ ${titre} : ${inp.value.trim() || '(effacé)'}`);
  };
  inp.addEventListener('keydown', e => {
    e.stopPropagation();
    if(e.key === 'Enter') valider();
    else if(e.key === 'Escape') fermerEditeurLabel();
  });
  ok.addEventListener('click', valider);
  box.addEventListener('pointerdown', e => e.stopPropagation());
  setTimeout(() => { inp.focus(); inp.select(); }, 0);
}

// Profils tactiles : la boîte s'ancre en bas de la zone visible, au-dessus
// du clavier virtuel, et le suit (visualViewport).
function _ancrerAuClavier(box){
  if(!_profil || _profil === 'pc') return;
  box.classList.add('auClavier');
  const vv = window.visualViewport;
  const placer = () => {
    if(!box.isConnected){ if(vv) vv.removeEventListener('resize', placer), vv.removeEventListener('scroll', placer); return; }
    const bas = vv ? window.innerHeight - vv.height - vv.offsetTop : 0;
    box.style.left = '8px'; box.style.top = 'auto';
    box.style.bottom = Math.max(8, bas + 8) + 'px';
  };
  placer();
  if(vv){ vv.addEventListener('resize', placer); vv.addEventListener('scroll', placer); }
}

function fermerEditeurLabel(){
  const b = document.getElementById('editLabel');
  if(b) b.remove();
}

// Change l'état d'un trait d'un pion, avec son « D/ ».
function fixerTrait(p, i, valeur){
  if(!p || p.deleted) return false;
  const te = tePion(p);
  if(!te) return false;
  const S = decoderTraits(te.etat);
  if(S[i] === valeur) return false;
  S[i] = valeur;
  _avantAction();
  const neuf = encoderTraits(S);
  fixerEtat(p, neuf);
  _logCmd(`D/${p.uid}/${neuf}/${te.etat}`);
  majApparence(p);
  return true;
}

