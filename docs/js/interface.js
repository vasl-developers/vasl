// ═══════════════════════════════════════════════════════════════
// INTERFACE — PROFILS DE TERMINAL ET REGISTRE D'ACTIONS
// Toute action de l'interface est déclarée une seule fois (ACTIONS) ;
// chaque profil (PC, tactile) dit où l'afficher
// (DISPOSITIONS). rafraichirActions() met à jour tout élément qui porte
// data-action : libellé, raccourci, état actif, allumé, coché, visible.
// Les raccourcis clavier ne passent pas par ce registre : leurs écouteurs
// restent ceux de leur section ; « touche » ne sert qu'à l'affichage.
// ═══════════════════════════════════════════════════════════════
const _v = f => typeof f === 'function' ? f() : f;

// Dés : catégories et raccourcis d'ASLDiceBot (voir _brancherDes).
const DES = [
  {id: 'DR_Other', lib: 'DR', titre: 'DR', touche: 'F2', faire: () => lancerDR('Other')},
  {id: 'DR_IFT', lib: 'IFT', titre: 'IFT attack DR', touche: 'Ctrl+Maj+I', faire: () => boutonIFT()},
  {id: 'DR_TH', lib: 'TH', titre: 'To Hit DR', touche: 'Ctrl+Maj+H', faire: () => lancerDR('TH')},
  {id: 'DR_TK', lib: 'TK', titre: 'To Kill DR', touche: 'Ctrl+Maj+K', faire: () => lancerDR('TK')},
  {id: 'DR_MC', lib: 'MC', titre: 'Morale Check DR', touche: 'Ctrl+Maj+M', faire: () => lancerDR('MC')},
  {id: 'DR_Rally', lib: 'Rally', titre: 'Rally DR', touche: 'Ctrl+Maj+R', faire: () => lancerDR('Rally')},
  {id: 'DR_CC', lib: 'CC', titre: 'Close Combat DR', touche: 'Ctrl+Maj+C', faire: () => lancerDR('CC')},
  {id: 'DR_TC', lib: 'TC', titre: 'Task Check DR', touche: 'Ctrl+Maj+T', faire: () => lancerDR('TC')},
  {id: 'dr_Other', lib: 'dr', titre: 'dr', touche: 'F1', faire: () => lancerdr('Other')},
  {id: 'dr_SA', lib: 'SA', titre: 'Sniper Activation dr', touche: 'Ctrl+Maj+S', faire: () => lancerdr('SA')},
  {id: 'dr_RS', lib: 'RS', titre: 'Random Selection dr', touche: 'Ctrl+Maj+D', faire: () => lancerdr('RS')}
];

const _texteReseau = () => {
  const n = _net, s = n && _netSalleCourante(n);
  const nb = s ? n.joueurs.filter(j => j.salle === s).length : 0;
  return !n ? 'Hors ligne' : s ? `${s} (${nb})` : 'Connexion…';
};
const _nomUtilisateur = () => _admin ? NOM_ADMIN : (_utilisateur ? _utilisateur.nom : 'Observateur');
const _enRelecture = () => _etapes.length > 0;

// ── HidePiecesButton de VASL (Ctrl+F10), réduit à deux vues, de
// ASLMap.ShowMapLevel : tout (ShowAll) ; la carte et les pièces qui ne
// s'empilent pas — overlays déplaçables, Fire Lane — (ShowMapAndOverlay).
// Les overlays restent toujours visibles : la vue « carte seule » de VASL
// n'est pas reprise. Rien n'est retiré, l'affichage seul change.
const VUES_CARTE = [
  {lib: 'Masquer les pions (montrer carte et overlays)', img: 'showall.png'},
  {lib: 'Montrer tous les pions', img: 'showmapandoverlay.png'}
];
let _vueCarte = 0;
function basculerVueCarte(){
  _vueCarte = (_vueCarte + 1) % VUES_CARTE.length;
  document.getElementById('mapInner').classList.toggle('vue1', _vueCarte === 1);
  if(_vueCarte) cacherSurvol();
  chatLog(['👁 Tous les pions affichés', '🗺️ Pions masqués : carte et overlays'][_vueCarte], 'sys');
}

// ── ASLBrokenFinder et ASLSniperFinder de VASL : deux boutons à bascule
// (QC) qui entourent de rouge, au-dessus des pions, les hexagones des
// pièces brisées ou MALF — couche BRK active, ni HIP ni dissimulées —, des
// DM et des Disrupt, et ceux des snipers (cercle de 1,5 hexagone). Les
// boutons portent la LMG russe (gpid 482) et le sniper US (gpid 778) de la
// palette.
const ICONE_BRISES = {gpid: '482', ico: '⭕'};
const ICONE_SNIPER = {gpid: '778', ico: '🎯'};
const _reperes = {brises: false, sniper: false};
function basculerRepere(q){
  _reperes[q] = !_reperes[q];
  dessinerReperes();
  rafraichirActions([q]);
}

function _estBrise(p){
  if(p.nom === 'DM' || p.nom === 'Disrupt') return true;
  const d = lirePiece(p);
  if(d.hip || d.masque) return false;
  let pr = {};
  try { pr = _proprietes(tsPion(p)) || {}; } catch(e){}
  return pr.BRK_Active === 'true';
}
// Le nom que voit ce joueur : un pion masqué par un autre ne se trahit pas.
const _estSniper = p => !p.cache && !accesRefuse(p) && p.nom.includes('Sniper');

let _reperesPrevus = false;
function dessinerReperes(){
  if(_reperesPrevus) return;
  _reperesPrevus = true;
  requestAnimationFrame(() => { _reperesPrevus = false; _dessinerReperes(); });
}
function _dessinerReperes(){
  let s = document.getElementById('reperes');
  if(!_reperes.brises && !_reperes.sniper){ if(s) s.remove(); return; }
  const NS = 'http://www.w3.org/2000/svg';
  if(!s){
    s = document.createElementNS(NS, 'svg');
    s.id = 'reperes';
    document.getElementById('mapInner').appendChild(s);
  }
  s.setAttribute('width', _mapW); s.setAttribute('height', _mapH);
  s.innerHTML = '';
  const cercles = (test, diam) => {
    const vus = new Set();
    for(const p of vivants()){
      const k = cle(p);
      if(vus.has(k) || !test(p)) continue;
      vus.add(k);
      const c = document.createElementNS(NS, 'circle');
      c.setAttribute('cx', p.x * SC); c.setAttribute('cy', p.y * SC); c.setAttribute('r', diam * SC / 2);
      c.setAttribute('fill', 'none'); c.setAttribute('stroke', 'red'); c.setAttribute('stroke-width', 4);
      c.setAttribute('vector-effect', 'non-scaling-stroke');
      s.appendChild(c);
    }
  };
  // DEFAULT_HEX_HEIGHT de VASL : 64,5 px.
  if(_reperes.brises) cercles(_estBrise, 2 * HEX_HY);
  if(_reperes.sniper) cercles(_estSniper, 2 * HEX_HY * 1.5);
}

// Boutons LOS de VASL (VASLThread) : l'icône thread.gif de VASSAL et la
// lettre du bouton, « L » pour la LOS, « l » pour la LOS silencieuse.
const ICONE_LOS = 'https://raw.githubusercontent.com/vassalengine/vassal/master/vassal-app/src/main/resources/images/thread.gif';

const ACTIONS = {
  ouvrir:   {lib: 'Ouvrir .vsav / .vlog', court: 'Ouvrir', ico: '📂', faire: doOpen},
  vsav:     {lib: 'Exporter .vsav', court: 'Exporter', ico: '💾', faire: ecrireVsav, actif: () => !!_source},
  log:      {lib: () => enregistreLog() ? `Terminer le log « ${_logNom} » et l'écrire` : 'Commencer un fichier de log',
             court: () => enregistreLog() ? '⏹ Log' : 'Log', ico: '⏺', classe: 'enreg',
             touche: () => enregistreLog() ? 'Alt+Maj+W' : 'Alt+W', faire: basculerLog,
             actif: () => !!_source || enregistreLog(), allume: enregistreLog},
  logTxt:   {etiquette: () => enregistreLog() ? '⏺ ' + _nbActions() : '', classe: 'enreg'},
  undo:     {lib: 'Annuler le dernier coup (Undo)', court: 'Undo', ico: '↶', touche: 'Ctrl+Z', faire: annuler,
             actif: () => !!_aAnnuler()},
  unmoved:  {lib: 'Mark unmoved — lever le statut Moved de tous les pions', court: 'Unmoved', img: 'mov.gif',
             faire: leverDeplaces},
  all:      {lib: 'Remove all — retirer tous les pions d\'un type…', court: 'ALL', img: 'QC/Remove.png',
             faire: ancre => ouvrirMassRemover(ancre)},
  brises:   {lib: 'Entourer les unités brisées et les armes MALF', court: 'Broken', ico: '⭕', pion: ICONE_BRISES,
             faire: () => basculerRepere('brises'), allume: () => _reperes.brises},
  sniper:   {lib: 'Entourer le sniper', court: 'Sniper', ico: '🎯', pion: ICONE_SNIPER,
             faire: () => basculerRepere('sniper'), allume: () => _reperes.sniper},
  masquer:  {lib: () => VUES_CARTE[_vueCarte].lib, court: 'Pions', img: () => VUES_CARTE[_vueCarte].img,
             touche: 'Ctrl+F10', faire: basculerVueCarte, allume: () => _vueCarte > 0},
  los:      {lib: 'LOS (ligne de vue)', court: 'L', img: ICONE_LOS, lettre: true, touche: 'F10',
             faire: (ancre, e) => armerFil(e && e.ctrlKey && e.shiftKey ? 'l' : 'L'), allume: () => !!_filArme,
             // Au doigt, un appui long propose la LOS silencieuse et le mode détaillé.
             menuLong: ['los', 'losMuet', 'verbeux']},
  losMuet:  {lib: 'LOS inv (ligne de vue silencieuse)', court: 'l', img: ICONE_LOS, lettre: true, touche: 'Ctrl+Maj+F10',
             faire: () => armerFil('l'), allume: () => _filArme === 'l'},
  verbeux:  {ico: '📝', lib: 'LOS détaillée (Verbose LOS mode)', coche: () => _losVerbeux, faire: basculerVerbeux},
  zoomMoins:{lib: 'Zoom arrière', ico: '−', faire: zoomOut},
  zoomPlus: {lib: 'Zoom avant', ico: '+', faire: zoomIn},
  zoomTxt:  {etiquette: () => Math.round(zoom * 100) + ' %', classe: 'zoom'},
  ajuster:  {lib: 'Ajuster la carte à la vue', court: 'Ajuster', ico: '⤢', faire: zoomFit},
  zoom100:  {lib: 'Zoom 100 %', court: '100 %', ico: '1:1', faire: () => setZoom(1)},
  // Rotation de l'affichage de la carte seulement : les pions restent droits.
  rotDroite:{lib: 'Tourner la carte de 90° (sens horaire)', court: '90° ⟳', ico: '⟳', faire: () => tournerVue(90),
             allume: () => _rotVue !== 0},
  rotGauche:{lib: 'Tourner la carte de 90° (sens antihoraire)', court: '90° ⟲', ico: '⟲', faire: () => tournerVue(-90),
             allume: () => _rotVue !== 0},
  rot180:   {lib: 'Tourner la carte de 180°', court: '180°', ico: '⇅', faire: () => tournerVue(180),
             allume: () => _rotVue !== 0},
  rot0:     {lib: 'Carte non tournée', court: '0°', ico: '⬆', coche: () => _rotVue === 0, faire: () => tournerVue(-_rotVue)},
  reseau:   {lib: () => 'Partie à distance — ' + _texteReseau(), court: _texteReseau, ico: '🌐', classe: 'reseau',
             texteBarre: true, faire: ouvrirReseau, allume: () => !!_net},
  utilisateur: {lib: () => `Utilisateur : ${_nomUtilisateur()} — changer ou gérer les utilisateurs`,
             court: _nomUtilisateur, ico: '👤', texteBarre: true, faire: demanderUtilisateur,
             classe: () => _admin ? 'admin' : ''},
  pions:    {lib: 'Palette des pions', court: 'Pions', ico: '♟', pion: true, faire: () => basculerCote(),
             coche: () => !document.getElementById('cote').classList.contains('ferme')},
  journal:  {lib: 'Journal et chat', court: 'Journal', ico: '💬', faire: () => basculerJournal(),
             coche: () => !document.getElementById('bas').hidden},
  // SAN et pièges : leur feuille, au PC comme au doigt (plus d'onglet Dés).
  infoScenario: {lib: 'SAN et pièges du scénario…', court: 'SAN', ico: '🎯', faire: () => ouvrirFeuilleSan()},
  palette:  {lib: 'Palette des pions', court: 'Palette', ico: '🗂', faire: () => montrerOnglet('palette')},
  pile:     {lib: 'Pile sélectionnée', court: 'Pile', ico: '▤', faire: () => montrerOnglet('pile')},
  des:      {lib: 'Dés', court: 'Dés', ico: '🎲', faire: () => montrerOnglet('des')},
  verrou:   {lib: 'Verrou des pions (un glissé déplace la carte)', court: 'Verrou', ico: '🔒',
             coche: () => _verrouPions, faire: () => basculerVerrou()},
  tables:   {img: 'QRDC.gif', lib: () => _telLike() ? 'Tables QRDC sous la carte' : 'Tables QRDC (fenêtre)', court: 'QRDC',
             coche: () => _telLike() ? _tablesVisibles : !!document.getElementById('fenQRDC'), faire: basculerTables},
  // Bouton « OBA » de VASL (F6) : les pioches de cartes Battery Access.
  oba:      {lib: 'OBA — pioches de cartes (onglet des tables QRDC)',
             court: 'OBA', ico: 'OBA', classe: 'sigle', touche: 'F6', coche: obaOuvert, faire: basculerOBA},
  // Bouton « Casualties Bin » de VASL : les pertes et leurs CVP.
  bin:      {lib: () => 'Casualties Bin — pertes et CVP (onglet des tables QRDC)' + (_cvpAffiche ? ' : ' + _cvpAffiche : ''),
             court: 'Pertes', ico: 'CVP', classe: 'sigle', coche: binOuvert, faire: basculerBin},
  effectif: {ico: '🔢', lib: 'Afficher l\'effectif des piles (vue de loin)', coche: () => _effectifPiles, faire: () => basculerEffectif()},
  raccourcis: {lib: 'Raccourcis clavier et gestes…', court: 'Aide', ico: '?', faire: () => aideRaccourcis()},
  couche_unite:    {ico: '👥', lib: 'Sélection : unités', coche: () => COUCHES_SEL.unite, faire: () => basculerCouche('unite')},
  couche_marqueur: {ico: '🏷', lib: 'Sélection : marqueurs', coche: () => COUCHES_SEL.marqueur, faire: () => basculerCouche('marqueur')},
  couche_carte:    {ico: '📋', lib: 'Sélection : cartes et panneaux', coche: () => COUCHES_SEL.carte, faire: () => basculerCouche('carte')},
  // Icône de la fenêtre Draggable Overlays de VASL (PieceWindow, icon="overlay").
  couche_overlay:  {img: 'overlay.gif', lib: 'Sélection : overlays', coche: () => COUCHES_SEL.overlay, faire: () => basculerCouche('overlay')},
  profil_auto:     {ico: '🔄', lib: 'Affichage : automatique', coche: () => _profilChoisi === 'auto', faire: () => choisirProfil('auto')},
  coteColonne:     {ico: '⇄', court: 'Côté', lib: () => _colonne === 'droite' ? 'Placer la barre d\'icônes à gauche' : 'Placer la barre d\'icônes à droite',
                    faire: () => placerColonne(_colonne === 'droite' ? 'gauche' : 'droite')},
  profil_pc:       {ico: '🖥', lib: 'Affichage : PC', coche: () => _profilChoisi === 'pc', faire: () => choisirProfil('pc')},
  profil_tactile:  {ico: '📱', lib: 'Affichage : tactile', coche: () => _profilChoisi === 'tactile', faire: () => choisirProfil('tactile')},
  // Relecture d'un .vlog : visible seulement s'il reste des coups.
  relecture:{groupe: ['lgDebut', 'lgArr', 'logPos', 'lgAv', 'lgFin'], visible: _enRelecture},
  // Colonne d'icônes du tactile : sans ⏮ ; ⏭ sous l'icône Unmoved,
  // ◀, la position et ▶ tout en bas.
  relectureRail: {groupe: ['lgArr', 'logPos', 'lgAv'], visible: _enRelecture},
  relectureFin:  {groupe: ['lgFin'], visible: _enRelecture},
  lgDebut:  {lib: 'Début du log', ico: '⏮', faire: () => allerLog(0), actif: () => _logPos > _logPlancher},
  lgArr:    {lib: () => _logPlancher > 0 && _logPos <= _logPlancher
               ? 'Un coup a été joué ici pendant la relecture : Undo pour l\'annuler' : 'Coup précédent',
             ico: '◀', touche: 'Page précédente', faire: () => reculerLog(), actif: () => _logPos > _logPlancher},
  logPos:   {etiquette: () => `${_logPos} / ${_etapes.length}`},
  lgAv:     {lib: 'Coup suivant', ico: '▶', touche: 'Page suivante', faire: () => avancerLog(),
             actif: () => _logPos < _etapes.length},
  lgFin:    {lib: 'Fin du log', ico: '⏭', faire: () => allerLog(_etapes.length), actif: () => _logPos < _etapes.length}
};
for(const d of DES) ACTIONS[d.id] = {lib: d.titre, court: d.lib, ico: d.lib, touche: d.touche, faire: d.faire, de: true};

// ── Rendu d'une action, sous la forme demandée :
//   'icone'   bouton de barre (icône, et texte pour l'utilisateur, le réseau)
//   'bouton'  bouton à libellé court (dés, rail)
//   'menu'    entrée de menu déroulant, raccourci à droite
//   'ligne'   entrée de feuille ou de liste, en grand
function rendreAction(id, forme = 'icone'){
  const a = ACTIONS[id];
  if(!a) return document.createTextNode('');
  if(a.groupe){
    const g = document.createElement('span');
    g.className = 'groupeAct';
    g.dataset.action = id;
    for(const x of a.groupe) g.appendChild(rendreAction(x, forme === 'menu' ? 'icone' : forme));
    return g;
  }
  if(a.etiquette){
    const e = document.createElement('span');
    e.className = 'etiqAct ' + (a.classe || '');
    e.dataset.action = id;
    return e;
  }
  const menu = forme === 'menu';
  const el = document.createElement(menu ? 'div' : 'button');
  el.dataset.action = id;
  el.dataset.forme = forme;
  if(menu){
    el.className = 'it';
    el.setAttribute('role', a.coche ? 'menuitemcheckbox' : 'menuitem');
    el.tabIndex = -1;
    el.innerHTML = '<span class="ck"></span><span class="lb"></span><span class="rc"></span>';
  } else {
    el.type = 'button';
    el.className = 'act ' + forme + (a.de ? ' de' : '') + (a.lettre ? ' lettre' : '');
    if(a.pion && forme !== 'bouton'){
      el.appendChild(iconePion(a.pion === true ? undefined : a.pion));
    } else if(a.img){
      const im = document.createElement('img');
      im.alt = '';
      el.appendChild(im);
    } else if(a.ico && forme !== 'bouton'){
      const i = document.createElement('span');
      i.className = 'ic'; i.setAttribute('aria-hidden', 'true');
      i.textContent = a.ico;
      el.appendChild(i);
    }
    const t = document.createElement('span');
    t.className = 'lb';
    el.appendChild(t);
    if(forme === 'ligne'){ const rc = document.createElement('span'); rc.className = 'rc'; el.appendChild(rc); }
  }
  el.addEventListener('click', e => {
    if(el.classList.contains('off') || el.disabled) return;
    e.stopPropagation();
    if(el._long){ el._long = false; return; }
    if(menu) fermerDeroulants();
    executerAction(id, e.currentTarget, e);
  });
  if(a.menuLong && !menu) el.addEventListener('pointerdown', e => {
    if(e.pointerType === 'mouse') return;
    const t = setTimeout(() => { el._long = true; vibrer(); ouvrirFeuilleActions(_v(a.lib), a.menuLong); }, APPUI_LONG);
    const fin = () => { clearTimeout(t); el.removeEventListener('pointerup', fin); el.removeEventListener('pointercancel', fin); el.removeEventListener('pointerleave', fin); };
    el.addEventListener('pointerup', fin); el.addEventListener('pointercancel', fin); el.addEventListener('pointerleave', fin);
  });
  if(id === 'los') el.addEventListener('contextmenu', e => {
    // Clic droit sur le bouton LOS : Verbose LOS mode, comme VASL.
    if(_dernierPointeur !== 'mouse') return;
    e.preventDefault(); basculerVerbeux(); rafraichirActions(['verbeux']);
  });
  _majAction(el, a, id);
  return el;
}

// ── Icône des pions : le chef 10-3 de l'OB « Other UNC (Korea) » de la
// palette VASL (gpid 3b5:8394, prototype unLDR1(KFW)), son label Name
// réglé sur « Monclar ». Composée une fois, comme une vignette de la
// palette, puis copiée ; en attendant la base, le signe ♟. Une action
// peut donner son propre pion (« pion: {gpid} ») : le North de la vue
// Carte.
const ICONE_PION = {gpid: '3b5:8394', nom: 'Monclar'};
const ICONE_NORTH = {gpid: '291', ico: '🗺'};
const _modelesIconePion = new Map();     // gpid → Promise<modèle | null>
function _composerIconePion(spec = ICONE_PION){
  if(!_modelesIconePion.has(spec.gpid)) _modelesIconePion.set(spec.gpid, chargerBDD().then(async bdd => {
    const x = bdd.parGpid.get(spec.gpid);
    const def = x && developperDefinition(x.cmd);
    if(!def) return null;
    const i = def.T.findIndex(t => /^label;/.test(t) && t.split(';')[2] === 'Name');
    if(i >= 0 && spec.nom) def.S[i] = spec.nom;
    const d = lirePiece('+/icone/' + encoderTraits(def.T) + '/' + encoderTraits(def.S));
    const m = document.createElement('div');
    m.className = 'modelePion';
    await composerVignette(m, d, 48);
    // Les labels se mesurent dans la page, à l'échelle du pion (48 px).
    const cache = document.createElement('div');
    cache.style.cssText = 'position:fixed;left:-200px;top:0;visibility:hidden';
    cache.appendChild(m);
    document.body.appendChild(cache);
    for(const t of d.etiquettes){
      const e = document.createElement('div');
      e.className = 'etiq';
      remplirLabel(e, t);
      e.style.font = `${t.style & 2 ? 'italic ' : ''}${t.style & 1 ? 'bold ' : ''}${t.taille}px/1.15 Arial,Helvetica,sans-serif`;
      e.style.color = t.couleur;
      e._label = t;
      m.appendChild(e);
      placerLabel(e, 48, 1);
    }
    cache.remove();
    return m;
  }).catch(() => null));
  return _modelesIconePion.get(spec.gpid);
}
function iconePion(spec = ICONE_PION){
  const b = document.createElement('span');
  b.className = 'ic icPion';
  b.setAttribute('aria-hidden', 'true');
  b.textContent = spec.ico || '♟';
  _composerIconePion(spec).then(m => { if(m){ b.textContent = ''; b.appendChild(m.cloneNode(true)); } });
  return b;
}

// Une feuille du bas qui liste des actions, en grand.
function ouvrirFeuilleActions(titre, ids, id = 'feuilleActions'){
  const f = ouvrirFeuille({id, titre});
  for(const x of ids){
    if(x === '-'){ const s = document.createElement('div'); s.className = 'sepFeuille'; f.corps.appendChild(s); continue; }
    const el = rendreAction(x, 'ligne');
    el.addEventListener('click', () => { if(!ACTIONS[x].coche) f.fermer(); }, true);
    f.corps.appendChild(el);
  }
  return f;
}

// Les actions passent toutes par ici : la fréquence d'usage sert à trier
// les commandes des barres tactiles.
function executerAction(id, ancre, e){
  const a = ACTIONS[id];
  if(!a || !a.faire || (a.actif && !a.actif())) return;
  a.faire(ancre, e);
  rafraichirActions();
}

function _majAction(el, a, id){
  if(a.groupe){ el.hidden = !!(a.visible && !a.visible()); return; }
  if(a.etiquette){ el.textContent = _v(a.etiquette); el.hidden = !!(a.visible && !a.visible()); return; }
  const lib = _v(a.lib), t = _v(a.touche) || '';
  const forme = el.dataset.forme;
  // Icône tirée du dépôt VASL ; elle peut changer avec l'état (masquer).
  const im = a.img && el.querySelector(':scope > img');
  if(im && im._chemin !== _v(a.img)){
    const ch = im._chemin = _v(a.img);
    chargerImage(ch).then(u => { if(u && im._chemin === ch) im.src = u; });
  }
  el.title = t ? `${lib} [${t}]` : lib;
  el.setAttribute('aria-label', lib);
  const lb = el.querySelector('.lb');
  if(lb){
    lb.textContent = forme === 'menu' || forme === 'ligne' ? lib
                   : forme === 'bouton' || (forme === 'icone' && a.texteBarre)
                     || ((forme === 'icone' || forme === 'rail') && a.lettre)
                     || ((forme === 'icone' || forme === 'rail') && !a.ico && !a.img && !a.pion) ? _v(a.court || a.lib)
                   : '';
  }
  const rc = el.querySelector('.rc');
  if(rc) rc.textContent = t;
  const off = !!(a.actif && !a.actif());
  if(forme === 'menu'){ el.classList.toggle('off', off); el.setAttribute('aria-disabled', String(off)); }
  else el.disabled = off;
  const allume = !!(a.allume && a.allume());
  el.classList.toggle('actif', allume);
  if(forme !== 'menu' && a.allume) el.setAttribute('aria-pressed', String(allume));
  if(a.coche){
    const c = !!a.coche();
    el.setAttribute('aria-checked', String(c));
    const ck = el.querySelector('.ck');
    if(ck) ck.textContent = c ? '✓' : '';
    if(forme !== 'menu') el.classList.toggle('actif', c);
  }
  if(a.badge){
    const n = a.badge();
    let bg = el.querySelector('.badgeAct');
    if(n && !bg){ bg = document.createElement('span'); bg.className = 'badgeAct'; el.appendChild(bg); }
    if(bg){ bg.textContent = n > 99 ? '99+' : String(n); bg.hidden = !n; }
  }
  const cl = _v(a.classe);
  if(el._classe !== cl){ if(el._classe) el.classList.remove(el._classe); if(cl) el.classList.add(cl); el._classe = cl; }
  el.hidden = !!(a.visible && !a.visible());
}

// Met à jour les éléments rendus, tous ou ceux des actions données.
function rafraichirActions(ids){
  const sel = ids ? ids.map(i => `[data-action="${i}"]`).join(',') : '[data-action]';
  if(!sel) return;
  for(const el of document.querySelectorAll(sel)){
    const a = ACTIONS[el.dataset.action];
    if(a) _majAction(el, a, el.dataset.action);
  }
}

// ═══ INTERFACE — PROFILS DE TERMINAL ═══
// Le profil se choisit d'après le pointeur et la taille de l'écran, jamais
// d'après le navigateur annoncé ; le réglage « Affichage » peut l'imposer.
// Geste par geste, c'est pointerType qui décide (PC portable tactile).
// Un affichage tactile imposé à la main ne vaut que pour la séance : ce
// profil n'a plus le choix de l'affichage pour en revenir.
let _profilChoisi = _lirePref('aslweb.profil') === 'pc' ? 'pc' : 'auto';
let _profil = null;
function detecterProfil(){
  if(_profilChoisi !== 'auto') return _profilChoisi;
  // Pointeur principal grossier : un écran tactile, même si le navigateur
  // annonce aussi le survol (certains Android, les stylets) ; sinon le
  // téléphone prenait l'interface du PC, sans moyen de fermer ses panneaux.
  // Tablette et téléphone partagent le même affichage tactile.
  return matchMedia('(pointer: coarse)').matches ? 'tactile' : 'pc';
}
function choisirProfil(p){
  _profilChoisi = p;
  try { localStorage.setItem('aslweb.profil', p === 'pc' ? 'pc' : 'auto'); } catch(e){}
  appliquerProfil(detecterProfil());
}

const DISPOSITIONS = {
  pc: {
    menus: {
      'Fichier':   ['ouvrir', 'vsav', '-', 'log'],
      'Partie':    ['undo', '-', 'unmoved', 'all', '-', 'brises', 'sniper', '-', 'infoScenario'],
      // Masqués à la demande : sélection des cartes et panneaux, effectif
      // des piles, LOS détaillée (toujours au clic droit sur le bouton LOS).
      'Affichage': ['zoomPlus', 'zoomMoins', 'ajuster', 'zoom100', '-', 'rotDroite', 'rotGauche', 'rot180', 'rot0', '-', 'masquer', 'tables', '-', 'couche_unite', 'couche_marqueur',
                    'couche_overlay', '-', 'pions', 'journal', '-', 'profil_auto', 'profil_pc', 'profil_tactile'],
      'Outils':    ['los', 'losMuet', '-', 'oba', 'bin'],
      'Dés':       [...DES.map(d => d.id), '-', 'infoScenario'],
      'Réseau':    ['reseau', 'utilisateur'],
      'Aide':      ['raccourcis']
    },
    barre: ['ouvrir', 'vsav', 'log', 'logTxt', '|', 'undo', '|', 'zoomMoins', 'zoomTxt', 'zoomPlus', 'ajuster', 'rotGauche', 'rotDroite',
            '|', 'relecture', 'los', 'unmoved', 'all', 'brises', 'sniper', 'masquer', 'tables', 'oba', 'bin', '|', 'pions', '|', 'reseau', 'utilisateur'],
    // Les dés restent à portée, en boutons sous la barre d'icônes.
    barreDes: [...DES.filter(d => d.id.startsWith('DR_')).map(d => d.id), '|', ...DES.filter(d => d.id.startsWith('dr_')).map(d => d.id)],
    // Ni onglet Pile ni onglet Dés : la pile se gère comme au doigt
    // (barre de pile, éventail au double-clic), les dés sont dans la barre
    // et le menu Dés, SAN et pièges dans leur feuille. Reste la palette.
    panneau: ['palette']
  }
};
// Tant qu'un profil n'a pas sa disposition propre, il reprend celle du PC.
const disposition = p => DISPOSITIONS[p] || DISPOSITIONS.pc;

function appliquerProfil(p){
  const ancien = _profil;
  _profil = p;
  document.body.dataset.terminal = p;
  fermerDeroulants(); fermerMenu();
  _defaireProfil();
  const d = disposition(p);
  const haut = document.getElementById('chromeHaut'), gauche = document.getElementById('chromeGauche'),
        bas = document.getElementById('chromeBas');
  haut.replaceChildren(); gauche.replaceChildren(); bas.replaceChildren();
  if(d.menus) haut.appendChild(construireMenus(d.menus));
  if(d.barre) haut.appendChild(construireBarre(d.barre, 'barreIcones'));
  if(d.barreDes) haut.appendChild(construireBarre(d.barreDes, 'barreIcones barreDes'));
  construireOnglets(d.panneau || ['pile', 'palette', 'des']);
  if(d.construire) d.construire();
  // Les vues de l'affichage tactile partent fermées ; au PC aussi, la
  // palette : elle s'ouvre par ♟ (barre ou menu Affichage).
  if(p === 'pc') basculerCote(false);
  rafraichirActions();
  for(const f of _apresProfil) f(p, ancien);
  _surRedimension();
}

function construireBarre(ids, classe){
  const b = document.createElement('div');
  b.className = classe;
  b.setAttribute('role', 'toolbar');
  for(const id of ids){
    if(id === '|'){ const s = document.createElement('span'); s.className = 'sep'; b.appendChild(s); continue; }
    b.appendChild(rendreAction(id, 'icone'));
  }
  return b;
}

// ── Panneau latéral à onglets : Pile | Palette | Dés.
let _ongletCote = _lirePref('aslweb.onglet') || 'pile';
const ONGLETS = {pile: 'ongPile', palette: 'ongPalette', des: 'ongDes'};
function construireOnglets(liste){
  const o = document.getElementById('coteOnglets');
  o.replaceChildren();
  for(const n of liste){
    const b = document.createElement('button');
    b.type = 'button';
    b.dataset.onglet = n;
    b.setAttribute('role', 'tab');
    b.textContent = ACTIONS[n].court;
    b.addEventListener('click', () => montrerOnglet(n));
    o.appendChild(b);
  }
  // Croix rouge cerclée : partout où le panneau recouvre la carte
  // (affichage tactile, PC sur un écran étroit).
  const x = document.createElement('button');
  x.type = 'button'; x.className = 'fermeCote croixFermer';
  x.textContent = '✕'; x.title = 'Fermer le panneau'; x.setAttribute('aria-label', 'Fermer le panneau');
  x.addEventListener('click', e => { e.stopPropagation(); fermerCote(); });
  o.appendChild(x);
  if(!liste.includes(_ongletCote)) _ongletCote = liste[0];
  montrerOnglet(_ongletCote, true);
}
function montrerOnglet(n, sansOuvrir){
  // Pas d'onglet Dés en affichage tactile : SAN et dés ont leur feuille.
  if(n === 'des' && _profil !== 'pc' && !sansOuvrir){ ouvrirFeuilleDes(); return; }
  if(_telLike() && !sansOuvrir){ vueMobile(n === 'palette' ? 'pions' : 'pile'); return; }
  _ongletCote = n;
  try { localStorage.setItem('aslweb.onglet', n); } catch(e){}
  for(const [k, id] of Object.entries(ONGLETS)) document.getElementById(id).classList.toggle('actif', k === n);
  for(const b of document.querySelectorAll('#coteOnglets button')){
    b.classList.toggle('actif', b.dataset.onglet === n);
    b.setAttribute('aria-selected', String(b.dataset.onglet === n));
  }
  if(!sansOuvrir) basculerCote(true);
  _majOngletPile();
}
// L'onglet Pile dit combien de pions sont sélectionnés.
function _majOngletPile(){
  const b = document.querySelector('#coteOnglets button[data-onglet="pile"]');
  if(b) b.textContent = 'Pile' + (_sel.size ? ` (${_sel.size})` : '');
}
_apresSelection.push(_majOngletPile);

function basculerCote(ouvrir){
  const c = document.getElementById('cote');
  const ferme = ouvrir === undefined ? !c.classList.contains('ferme') : !ouvrir;
  if(ferme === c.classList.contains('ferme')) return;
  c.classList.toggle('ferme', ferme);
  rafraichirActions(['pions']);
  _surRedimension();
}
// Le panneau recouvre-t-il la carte ? (Il est alors posé par-dessus.)
const coteRecouvre = () => {
  const c = document.getElementById('cote');
  return !c.classList.contains('ferme') && getComputedStyle(c).position === 'absolute';
};
function fermerCote(){
  if(_telLike()) vueMobile('carte'); else basculerCote(false);
}

function basculerJournal(montrer){
  const b = document.getElementById('bas');
  b.hidden = montrer === undefined ? !b.hidden : !montrer;
  document.getElementById('poigneeJournal').hidden = b.hidden;
  rafraichirActions(['journal']);
  _surRedimension();
}
function basculerCouche(c){
  const i = document.querySelector(`#couches input[data-couche="${c}"]`);
  if(!i) return;
  i.checked = !i.checked;
  i.dispatchEvent(new Event('change'));
}
function basculerEffectif(){
  _effectifPiles = !_effectifPiles;
  try { localStorage.setItem('aslweb.effectifPiles', _effectifPiles ? '1' : '0'); } catch(e){}
  rendreToutes();
}

// ── Dés de l'onglet Dés : DR, puis dr, puis le rappel des raccourcis.
function construireDes(){
  const box = document.getElementById('desBoutons');
  box.replaceChildren();
  for(const d of DES){
    if(d.id === 'dr_Other'){ const s = document.createElement('div'); s.className = 'sepDes'; box.appendChild(s); }
    box.appendChild(rendreAction(d.id, 'bouton'));
  }
  document.getElementById('desAide').innerHTML = '<table>' + DES.map(d =>
    `<tr><td>${_esc(d.titre)}</td><td>${_esc(d.touche)}</td></tr>`).join('') + '</table>';
}

// ── Poignées : largeur du panneau (240 à 520 px), hauteur du journal
// (48 à 300 px), retenues d'une visite à l'autre.
function _brancherPoignees(){
  const racine = document.documentElement.style;
  const lc = parseInt(_lirePref('aslweb.largeurCote'), 10), hj = parseInt(_lirePref('aslweb.hautJournal'), 10);
  if(lc) racine.setProperty('--largeurCote', lc + 'px');
  if(hj) racine.setProperty('--hautJournal', hj + 'px');
  const brancher = (el, cle, varCss, mini, maxi, mesure) => el.addEventListener('pointerdown', e => {
    e.preventDefault();
    const id = e.pointerId, depart = mesure(e), x0 = e.clientX, y0 = e.clientY;
    let v = depart.v;
    const bouge = ev => {
      if(ev.pointerId !== id) return;
      v = Math.max(mini, Math.min(maxi, depart.v + depart.sens(ev.clientX - x0, ev.clientY - y0)));
      racine.setProperty(varCss, v + 'px');
    };
    const fin = ev => {
      if(ev.pointerId !== id) return;
      document.removeEventListener('pointermove', bouge);
      document.removeEventListener('pointerup', fin);
      document.removeEventListener('pointercancel', fin);
      try { localStorage.setItem(cle, String(Math.round(v))); } catch(err){}
      _surRedimension();
    };
    document.addEventListener('pointermove', bouge);
    document.addEventListener('pointerup', fin);
    document.addEventListener('pointercancel', fin);
  });
  brancher(document.getElementById('poigneeCote'), 'aslweb.largeurCote', '--largeurCote', 240, 520,
    () => ({v: document.getElementById('cote').offsetWidth, sens: dx => -dx}));
  brancher(document.getElementById('poigneeJournal'), 'aslweb.hautJournal', '--hautJournal', 48, 300,
    () => ({v: document.getElementById('journal').offsetHeight, sens: (dx, dy) => -dy}));
}

// ═══ INTERFACE — PROFIL TACTILE (TABLETTE ET TÉLÉPHONE) ═══
// Une seule interface pour tout écran tactile : colonne d'icônes sur le
// côté, une vue à la fois (Carte, Pile, Pions, Journal) par-dessus la carte.
const _vue = () => document.body.dataset.vue || 'carte';

Object.assign(ACTIONS, {
  plus:      {lib: 'Plus…', court: 'Plus', ico: '☰', faire: () => ouvrirFeuillePlus()},
  vueCarte:  {lib: 'Carte', court: 'Carte', ico: '🗺', pion: ICONE_NORTH, faire: () => vueMobile('carte'), allume: () => _vue() === 'carte'},
  vuePile:   {lib: 'Pile sélectionnée', court: 'Pile', ico: '▤', faire: () => vueMobile('pile'), allume: () => _vue() === 'pile'},
  vuePions:  {lib: 'Palette des pions', court: 'Pions', ico: '♟', pion: true, faire: () => vueMobile('pions'), allume: () => _vue() === 'pions'},
  vueJournal:{lib: 'Journal et chat', court: 'Journal', ico: '💬', faire: () => vueMobile('journal'), allume: () => _vue() === 'journal',
              badge: () => _nonLus},
  apercuChat:{ico: '💭', lib: 'Aperçu du chat sur la carte', coche: () => _apercuChat, faire: () => basculerApercuChat()},
  titre:     {etiquette: () => _source ? _nomFichier : 'VASLweb 1.01', classe: 'titreScen'},
  desMenu:   {lib: 'Dés : un appui DR, un appui long les catégories', court: 'Dés', ico: '🎲',
              faire: () => lancerDR('Other'), menuLong: null},
  sanPieges: {lib: 'SAN et pièges…', court: 'SAN', ico: 'INFO', classe: 'sigle', faire: () => ouvrirFeuilleSan()},
  tiroir:    {lib: 'Pile et palette', court: 'Pions', ico: '♟', pion: true, faire: () => basculerCote(),
              allume: () => !document.getElementById('cote').classList.contains('ferme')}
});

// Côté de la colonne d'icônes, en affichage tactile : à droite par
// défaut ; le choix de l'utilisateur est retenu sur cet appareil.
let _colonne = _lirePref('aslweb.colonne') === 'gauche' ? 'gauche' : 'droite';
document.body.dataset.colonne = _colonne;
function placerColonne(c){
  _colonne = c;
  document.body.dataset.colonne = c;
  try { localStorage.setItem('aslweb.colonne', c); } catch(e){}
  rafraichirActions(['coteColonne']);
  for(const f of _apresRedimension) f();
}

// Le reste des actions, dans la feuille « Plus » des profils tactiles.
// Masqués en affichage tactile, à la demande : sélection des unités,
// des marqueurs, des cartes et panneaux, zoom avant, arrière, ajuster, LOS
// détaillée, verrou des pions, effectif des piles, aperçu du chat,
// raccourcis ; du choix de l'affichage ne reste que l'affichage ordinateur,
// dans la feuille seulement. Les actions restent définies (menu Affichage du
// PC) : pour les rendre, remettre 'couche_unite', 'couche_marqueur' ici.
// La sélection des overlays reste : sans elle, un overlay posé ne pourrait
// plus être ni déplacé ni retiré au doigt.
const PLUS = ['ouvrir', 'vsav', 'log', '-', 'los', 'losMuet', 'unmoved', 'all', 'brises', 'sniper', 'masquer', 'tables', 'oba', 'bin', 'couche_overlay', '-', 'rotDroite', 'rot180', '-', 'reseau', 'utilisateur', '-',
  'sanPieges', '-', 'profil_pc'];
// Dans la feuille « Plus », mais pas dans la colonne d'icônes.
const PLUS_SANS_ICONE = ['profil_pc', 'reseau', 'utilisateur'];
function ouvrirFeuillePlus(){ return ouvrirFeuilleActions('Plus', PLUS, 'feuillePlus'); }

// SAN et pièges : les champs eux-mêmes viennent dans la feuille, et
// retournent à l'onglet Dés quand elle se ferme.
function ouvrirFeuilleSan(){
  const champs = document.getElementById('infoScen');
  const f = ouvrirFeuille({id: 'feuilleSan', titre: 'SAN et pièges', fermer: () => document.getElementById('ongDes').insertBefore(champs, document.getElementById('desAide'))});
  f.corps.appendChild(champs);
  return f;
}
// Toutes les catégories de dés, en gros boutons.
function ouvrirFeuilleDes(){
  const f = ouvrirFeuille({id: 'feuilleDes', titre: 'Dés'});
  const g = document.createElement('div');
  g.className = 'desBoutons grand';
  for(const d of DES){
    if(d.id === 'dr_Other'){ const s = document.createElement('div'); s.className = 'sepDes'; g.appendChild(s); }
    const b = rendreAction(d.id, 'bouton');
    b.addEventListener('click', () => f.fermer());
    g.appendChild(b);
  }
  const san = document.createElement('div');
  san.className = 'desBoutons';
  san.appendChild(rendreAction('sanPieges', 'ligne'));
  f.corps.append(g, san);
  return f;
}

DISPOSITIONS.tactile = {
  panneau: ['pile', 'palette'],
  construire(){
    // Pas de barre en haut : tout passe par la colonne d'icônes de droite ;
    // l'aperçu du chat se place en haut ou en bas, au choix.
    document.getElementById(_posApercu === 'haut' ? 'chromeHaut' : 'chromeBas').appendChild(_construireApercuChat());
    const nav = document.createElement('nav');
    nav.className = 'navBas navGauche';
    nav.setAttribute('aria-label', 'Vues');
    nav.setAttribute('aria-orientation', 'vertical');
    // Une colonne d'icônes sur la droite de l'écran : carte ; affichage de
    // la carte avec ou sans pions ; palette ; Remove all ; journal ; Undo ;
    // les dés (DR, IFT, TH, TK, MC, Rally, CC, TC, dr, SA, RS) ; puis toutes
    // les actions de la feuille « Plus », chacune par son icône, ⏭ (fin du
    // log) juste sous Unmoved ; le côté de la colonne (⇄, à droite ou à
    // gauche de l'écran) ; Plus ; tout en bas, ◀, la position et ▶. ⏭, ◀
    // et ▶ ne sont présents que pendant une relecture.
    const premieres = ['vueCarte', 'masquer', 'vuePions', 'all', 'vueJournal', 'undo', ...DES.map(d => d.id)];
    const ids = [...premieres, ...PLUS.filter(x => x !== '-' && !premieres.includes(x) && !PLUS_SANS_ICONE.includes(x))
                   .flatMap(x => x === 'unmoved' ? [x, 'relectureFin'] : [x]),
                 'coteColonne', 'plus', 'relectureRail'];
    // Défilement sans fin : la liste est posée trois fois ; près d'un bout,
    // on saute d'une longueur de liste sans que rien ne bouge à l'écran.
    // Monter au-delà de la première icône mène à la dernière, et l'inverse.
    // Seule la copie du milieu est lue par les lecteurs d'écran.
    const debuts = [];
    for(let copie = 0; copie < 3; copie++) ids.forEach((id, i) => {
      const el = rendreAction(id, 'rail');
      if(copie !== 1){
        el.setAttribute('aria-hidden', 'true');
        for(const x of [el, ...el.querySelectorAll('button')]) x.tabIndex = -1;
      }
      if(!i) debuts.push(el);
      nav.appendChild(el);
    });
    const longueur = () => debuts[2].offsetTop - debuts[1].offsetTop;
    nav.addEventListener('scroll', () => {
      const L = longueur();
      if(!L) return;
      if(nav.scrollTop < L * 0.5) nav.scrollTop += L;
      else if(nav.scrollTop > L * 1.5) nav.scrollTop -= L;
    }, {passive: true});
    setTimeout(() => { nav.scrollTop = longueur(); }, 0);
    document.getElementById('chromeGauche').appendChild(nav);
    vueMobile('carte');
  }
};

// Téléphone ou tablette : l'interface tactile commune.
function _telLike(){ return _profil === 'tactile'; }


// Vue de l'affichage tactile : la carte seule, ou une vue par-dessus elle.
function vueMobile(v){
  if(!_telLike()) return;
  document.body.dataset.vue = v;
  const cote = document.getElementById('cote');
  cote.classList.toggle('ferme', v !== 'pile' && v !== 'pions');
  if(v === 'pile' || v === 'pions'){
    const n = v === 'pile' ? 'pile' : 'palette';
    _ongletCote = n;
    for(const [k, id] of Object.entries(ONGLETS)) document.getElementById(id).classList.toggle('actif', k === n);
  }
  const bas = document.getElementById('bas');
  if(v === 'journal'){
    bas.hidden = false;
    _poserEtatChat('plein');
    const j = document.getElementById('journal'); j.scrollTop = j.scrollHeight;
  } else {
    if(_etatChat) _poserEtatChat(null);
    bas.hidden = true;
  }
  rafraichirActions(['vueCarte', 'vuePile', 'vuePions', 'vueJournal']);
  _surRedimension();
}



// Les éléments flottants suivent la carte (le tiroir peut la réduire).
function _placerVolants(){
  const w = document.getElementById('mapWrap'), corps = document.getElementById('corps');
  const droite = corps.clientWidth - (w.offsetLeft + w.clientWidth);
  const barre = document.getElementById('barrePile');
  const bas = barre ? barre.offsetHeight : 0;
}
_apresRedimension.push(_placerVolants);

// Ce qu'un profil a posé ailleurs revient à sa place quand on en change.
function _defaireProfil(){
  document.querySelectorAll('.chromeVolant').forEach(e => e.remove());
  const bas = document.getElementById('bas');
  bas.classList.remove('replie');
  bas.hidden = false;
  document.getElementById('poigneeJournal').hidden = false;
  delete document.body.dataset.vue;
  document.body.classList.remove('saisie');
  if(_etatChat) _poserEtatChat(null);
  const champs = document.getElementById('infoScen');
  if(champs.parentElement.id !== 'ongDes') document.getElementById('ongDes').insertBefore(champs, document.getElementById('desAide'));
  for(const id of [..._feuilles.keys()]) fermerFeuille(id);
  annulerModeCarte();
}


