// ═══ INTERFACE — BARRE DE MENUS (profil PC) ═══
// Menus déroulants ; Alt seul ouvre le premier (F10 reste la LOS de VASL),
// les flèches y circulent, Entrée choisit, Échap referme.
let _menuOuvert = null;     // {titre, liste, items, i}
function construireMenus(def){
  const barre = document.createElement('div');
  barre.className = 'menus';
  barre.setAttribute('role', 'menubar');
  const h = document.createElement('h1');
  h.textContent = 'VASL — Carte';
  barre.appendChild(h);
  for(const [nom, ids] of Object.entries(def)){
    const t = document.createElement('button');
    t.type = 'button';
    t.className = 'titreMenu';
    t.textContent = nom;
    t.setAttribute('role', 'menuitem');
    t.setAttribute('aria-haspopup', 'true');
    t._ids = ids;
    t.addEventListener('pointerdown', e => e.stopPropagation());
    t.addEventListener('click', e => {
      e.stopPropagation();
      if(_menuOuvert && _menuOuvert.titre === t) fermerDeroulants(); else ouvrirDeroulant(t);
    });
    t.addEventListener('mouseenter', () => { if(_menuOuvert && _menuOuvert.titre !== t) ouvrirDeroulant(t); });
    barre.appendChild(t);
  }
  return barre;
}

function ouvrirDeroulant(t, focusPremier){
  fermerDeroulants(); fermerMenu();
  const m = document.createElement('div');
  m.className = 'deroulant';
  m.setAttribute('role', 'menu');
  for(const id of t._ids){
    if(id === '-'){ const s = document.createElement('div'); s.className = 'sepm'; s.setAttribute('role', 'separator'); m.appendChild(s); continue; }
    m.appendChild(rendreAction(id, 'menu'));
  }
  m.addEventListener('pointerdown', e => e.stopPropagation());
  document.body.appendChild(m);
  const r = t.getBoundingClientRect();
  m.style.left = Math.max(4, Math.min(r.left, window.innerWidth - m.offsetWidth - 4)) + 'px';
  m.style.top = r.bottom + 'px';
  t.classList.add('ouvert');
  t.setAttribute('aria-expanded', 'true');
  const items = [...m.querySelectorAll('.it')];
  _menuOuvert = {titre: t, liste: m, items, i: focusPremier ? 0 : -1};
  if(focusPremier) _focusItem(0);
}
function _focusItem(i){
  const o = _menuOuvert;
  if(!o || !o.items.length) return;
  o.i = (i + o.items.length) % o.items.length;
  o.items.forEach((it, k) => it.classList.toggle('focus', k === o.i));
  o.items[o.i].focus({preventScroll: true});
  o.items[o.i].scrollIntoView({block: 'nearest'});
}
function fermerDeroulants(){
  if(!_menuOuvert) return;
  _menuOuvert.liste.remove();
  _menuOuvert.titre.classList.remove('ouvert');
  _menuOuvert.titre.setAttribute('aria-expanded', 'false');
  _menuOuvert = null;
}
function _brancherMenus(){
  document.addEventListener('pointerdown', () => fermerDeroulants());
  let altSeul = false;
  window.addEventListener('keydown', e => {
    if(e.key === 'Alt'){ altSeul = !e.ctrlKey && !e.shiftKey && !e.metaKey; return; }
    altSeul = false;
    const o = _menuOuvert;
    if(!o) return;
    const titres = [...document.querySelectorAll('.menus .titreMenu')];
    const j = titres.indexOf(o.titre);
    const k = e.key;
    if(!['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight', 'Enter', ' ', 'Escape', 'Home', 'End'].includes(k)) return;
    e.preventDefault(); e.stopImmediatePropagation();
    if(k === 'Escape'){ const t = o.titre; fermerDeroulants(); t.focus(); return; }
    if(k === 'ArrowDown') _focusItem(o.i + 1);
    else if(k === 'ArrowUp') _focusItem(o.i < 0 ? -1 : o.i - 1);
    else if(k === 'Home') _focusItem(0);
    else if(k === 'End') _focusItem(-1);
    else if(k === 'ArrowLeft' || k === 'ArrowRight')
      ouvrirDeroulant(titres[(j + (k === 'ArrowLeft' ? -1 : 1) + titres.length) % titres.length], true);
    else if(o.i >= 0) o.items[o.i].click();
  }, true);
  window.addEventListener('keyup', e => {
    if(e.key !== 'Alt' || !altSeul) return;
    altSeul = false;
    if(_profil !== 'pc' || /^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)) return;
    const t = document.querySelector('.menus .titreMenu');
    if(!t) return;
    e.preventDefault();
    if(_menuOuvert) fermerDeroulants(); else ouvrirDeroulant(t, true);
  });
  // Alt enfoncé puis un clic : ce n'est pas « Alt seul ».
  window.addEventListener('pointerdown', () => { altSeul = false; }, true);
}

// Pose au centre de la vue, même si un panneau la recouvre.
function _poserAuCentre(x){
  const wr = document.getElementById('mapWrap').getBoundingClientRect();
  const c = clientVersCarte(wr.left + wr.width/2, wr.top + wr.height/2, null, true);
  if(c) poserDepuisPalette(x, c.x, c.y);
}

// ═══ INTERFACE — AIDE DES RACCOURCIS ═══
function aideRaccourcis(){
  fermerFenetres();
  const m = metriques();
  const lignes = (titre, l) => `<tr><th colspan="2">${_esc(titre)}</th></tr>`
    + l.map(([a, b]) => `<tr><td>${_esc(a)}</td><td class="nb">${_esc(b)}</td></tr>`).join('');
  const actions = Object.values(ACTIONS).filter(a => _v(a.touche) && !a.de).map(a => [_v(a.lib), _v(a.touche)]);
  const w = document.createElement('div');
  w.id = 'fenUtil'; w.className = 'fenetre';
  w.innerHTML = `<div class="ft">Raccourcis clavier et gestes</div><div class="fc"><table>
    ${lignes('Partie', [...actions, ['Menus', 'Alt']])}
    ${lignes('Dés', DES.map(d => [d.titre, d.touche]))}
    ${lignes('Pions et piles', [
      ['Commandes du pion (menu)', 'clic droit · double appui'],
      ['Sélectionner la pile', 'clic'], ['Déplier / replier la pile', 'double-clic'],
      ['Ajouter ou retirer un pion', 'Ctrl+clic'], ['Ajouter à la sélection', 'Maj+clic'],
      ['Rectangle de sélection', 'Maj+glisser dans le vide'],
      ['Mettre au sommet de la pile', libelleTouche(m.top) || '↑'], ['Mettre au fond', libelleTouche(m.bottom) || '↓'],
      ['Monter d\'un cran', libelleTouche(m.up) || '→'], ['Descendre d\'un cran', libelleTouche(m.down) || '←'],
      ['Replier, puis désélectionner', 'Échap']])}
    ${lignes('Ligne de vue (pendant le tracé)', [['Niveau de la cible', '↑ / ↓'], ['Niveau de la source', 'Ctrl+↑ / Ctrl+↓'],
      ['Plus haut niveau hors toit', 'Ctrl à l\'appui'], ['Annuler', 'Échap']])}
    ${lignes('Relecture', [['Coup suivant / précédent', 'Page suivante / précédente']])}
    </table><div class="fb"><button id="utFermer" class="prim">Fermer</button></div></div>`;
  document.body.appendChild(w);
  w.querySelector('#utFermer').onclick = () => w.remove();
}

// Le profil suit la fenêtre et le pointeur.
function _brancherInterface(){
  window.addEventListener('resize', reevaluerProfil);
  for(const q of ['(pointer: coarse)', '(hover: hover)']){
    const mq = matchMedia(q);
    if(mq.addEventListener) mq.addEventListener('change', reevaluerProfil);
  }
}
let _reevaluation = null;
function reevaluerProfil(){
  clearTimeout(_reevaluation);
  if(enSaisie()) return;
  _reevaluation = setTimeout(() => { if(enSaisie()) return; const p = detecterProfil(); if(p !== _profil) appliquerProfil(p); }, 200);
}

document.addEventListener('DOMContentLoaded', () => {
  _brancherLangue();
  _setupDropZone();
  _setupPanZoom();
  _brancherCouches();
  // Ni sélection de texte ni glisser natif d'image sur la carte : le clic
  // y sert au panoramique et aux pions, jamais à surligner le terrain.
  const mw = document.getElementById('mapWrap');
  const saisie = t => t && t.closest && t.closest('input,textarea,select,[contenteditable="true"]');
  mw.addEventListener('selectstart', e => { if(!saisie(e.target)) e.preventDefault(); });
  mw.addEventListener('dragstart',   e => { if(!saisie(e.target)) e.preventDefault(); });
  const r = document.getElementById('palRech');
  r.addEventListener('input', () => { _palFiltre = r.value.trim().toLowerCase(); dessinerPalette(); });
  _brancherCommandes();
  _brancherMenus();
  _brancherInterface();
  _brancherPoignees();
  construireDes();
  appliquerProfil(detecterProfil());
  // Libellé des boutons d'icône pour les lecteurs d'écran : leur infobulle.
  for(const b of document.querySelectorAll('button[title]:not([aria-label])')) b.setAttribute('aria-label', b.title);
  // Pendant la saisie au doigt, le clavier change la taille de la fenêtre :
  // rien ne bouge avant qu'il se ferme (voir CHAT — CLAVIER VIRTUEL).
  window.addEventListener('resize', () => { if(enSaisie()) return; _redim = _redim || -1; _surRedimension(); });
  window.addEventListener('orientationchange', () => { if(!enSaisie()) _surRedimension(); });
  _brancherFil();
  majUtilisateur();
  _brancherDes();
  _brancherChat();
  // La carte qui défile ou zoome emporte la pile survolée : on referme.
  const w = document.getElementById('mapWrap');
  w.addEventListener('scroll', cacherSurvol);
  w.addEventListener('scroll', () => placerEventail());
  w.addEventListener('wheel', cacherSurvol);
  dessinerPalette();
  majNet();
  window.addEventListener('pagehide', () => { _garderLog(); _garderPartie(); if(_net) netQuitter(); });
  document.addEventListener('visibilitychange', () => {
    if(document.visibilityState === 'hidden'){ _garderLog(); _garderPartie(); }
    else if(_net) _net.aReprendre.forEach(f => f());
  });
  // Le log d'abord, puis la partie, l'un après l'autre (lectures
  // asynchrones d'IndexedDB).
  setTimeout(async () => { await _reprendreLogGarde(); await _reprendrePartieGardee(); }, 0);
  // Comme VASSAL à la fermeture : des coups enregistrés et pas encore
  // écrits ne partent pas sans avertissement.
  window.addEventListener('beforeunload', e => {
    if((_logEnr && _logEnr.length) || (_source && _partieModifiee)){ e.preventDefault(); e.returnValue = ''; }
  });
});
