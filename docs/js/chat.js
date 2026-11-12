// ═══ CHAT — CLAVIER VIRTUEL ═══
// Au doigt, tant qu'un champ texte a le focus, body.saisie : la navigation
// et la relecture s'effacent, et le chat se cale sur le bord haut du
// clavier d'après visualViewport, qu'il recouvre la page (iOS, Chrome
// Android) ou qu'il la réduise. Ni recadrage ni changement de profil avant
// que le clavier se ferme ; alors, un seul.
const enSaisie = () => document.body.classList.contains('saisie');
const _champTexte = el => !!el && el.matches && el.matches('input:not([type=checkbox]):not([type=radio]), textarea');
function _brancherClavier(){
  const vv = window.visualViewport, r = document.documentElement.style;
  const suivre = () => {
    if(!vv) return;
    r.setProperty('--clavier', Math.max(0, innerHeight - vv.height - vv.offsetTop) + 'px');
    r.setProperty('--vv-haut', vv.offsetTop + 'px');   // défilement imposé par iOS
    r.setProperty('--vv-h', vv.height + 'px');          // hauteur réellement visible
  };
  if(vv){ vv.addEventListener('resize', suivre); vv.addEventListener('scroll', suivre); suivre(); }
  document.addEventListener('focusin', e => {
    if(!_profil || _profil === 'pc' || !_champTexte(e.target)) return;
    document.body.classList.add('saisie');
    suivre();
  });
  document.addEventListener('focusout', () => {
    // Le focus peut passer d'un champ à l'autre : on regarde après coup.
    setTimeout(() => {
      if(_champTexte(document.activeElement) || !enSaisie()) return;
      document.body.classList.remove('saisie');
      suivre();
      _surRedimension();
      reevaluerProfil();
      for(const f of _apresSaisie) f();
    }, 0);
  });
  // Un appui sur la carte ferme le clavier ; le geste continue.
  document.getElementById('mapWrap').addEventListener('pointerdown', () => {
    if(enSaisie() && _champTexte(document.activeElement)){ _clavierFermeA = Date.now(); document.activeElement.blur(); }
  }, true);
}
const _apresSaisie = [];
let _clavierFermeA = 0;

// ═══ CHAT — SUR LA CARTE DU TÉLÉPHONE ═══
// Trois états, sans quitter la vue Carte : l'aperçu (une ligne au-dessus
// de la relecture), le chat compact (une feuille au-dessus de la relecture
// et de la navigation, la carte visible au-dessus), la saisie (la même,
// sur le clavier) ; l'onglet Journal reste l'état plein. Retour, glissé
// vers le bas ou appui sur la carte reculent d'un cran. Non-lus, aperçu et
// pastilles sont des indications d'interface : rien ne part au log.
let _etatChat = null;            // null (aperçu), 'compact', 'plein'
let _nonLus = 0;
// L'option n'a plus de bouton (masquée) : l'aperçu est toujours montré.
let _apercuChat = true;
// Place de l'aperçu, en haut ou en bas de l'écran, retenue sur cet appareil.
let _posApercu = _lirePref('aslweb.apercuPos') === 'haut' ? 'haut' : 'bas';
function placerApercuChat(pos){
  _posApercu = pos;
  try { localStorage.setItem('aslweb.apercuPos', pos); } catch(e){}
  document.body.dataset.chat = pos;
  if(typeof rafraichirActions === 'function') rafraichirActions(['chatHaut']);
  const ap = document.getElementById('apercuChat');
  if(ap){
    document.getElementById(pos === 'haut' ? 'chromeHaut' : 'chromeBas').prepend(ap);
    _majApercuChat();
  }
  _surRedimension();
  majHautBas();
  _majReserveCarte();
}
// Le chat placé en haut s'ouvre en haut, la saisie en tête (CSS
// body[data-chat=haut]) ; en bas, il s'ouvre en bas, la saisie au pied.
document.body.dataset.chat = _posApercu;

// Hauteur réelle du bas de l'écran (aperçu, relecture, navigation) : le
// chat ouvert s'arrête au-dessus, la relecture reste accessible. De même,
// le bord bas du haut de l'écran (barre QC) : le chat placé en haut s'ouvre
// dessous.
function majHautBas(){
  const b = document.getElementById('chromeBas'), h = document.getElementById('chromeHaut');
  if(b) document.documentElement.style.setProperty('--hautBas', b.offsetHeight + 'px');
  if(h) document.documentElement.style.setProperty('--hautHaut', Math.max(0, h.getBoundingClientRect().bottom) + 'px');
}
_apresRedimension.push(majHautBas);
_apresProfil.push(() => { majHautBas(); _majApercuChat(); });

// Sous le chat compact, une marge de la hauteur couverte : la carte peut
// défiler jusqu'à montrer son bas au-dessus du chat ; chat placé en haut,
// la marge passe au-dessus : la carte montre son haut sous le chat.
function _majReserveCarte(){
  const w = document.getElementById('mapWrap'), s = document.getElementById('mapScaler');
  const couvert = (w.clientHeight - hauteurVisibleCarte()) + 'px', haut = _chatEnHaut();
  const avant = margeHautCarte();
  s.style.marginBottom = haut ? '0px' : couvert;
  s.style.marginTop = haut ? couvert : '0px';
  // La carte ne saute pas : ce qu'on voyait reste à sa place à l'écran.
  w.scrollTop += margeHautCarte() - avant;
}
const _chatEnHaut = () => _posApercu === 'haut' && _etatChat === 'compact';
// Marge posée au-dessus de la carte par le chat compact placé en haut ;
// les calculs du zoom la retirent du défilement.
function margeHautCarte(){
  return parseFloat(document.getElementById('mapScaler').style.marginTop) || 0;
}
_apresRedimension.push(_majReserveCarte);

// Hauteur de la carte que le chat compact ne couvre pas.
function hauteurVisibleCarte(){
  const w = document.getElementById('mapWrap'), bas = document.getElementById('bas');
  if(_etatChat !== 'compact' || bas.hidden) return w.clientHeight;
  const rw = w.getBoundingClientRect(), rb = bas.getBoundingClientRect();
  if(_posApercu === 'haut') return Math.max(80, Math.min(w.clientHeight, rw.bottom - rb.bottom));
  return Math.max(80, Math.min(w.clientHeight, rb.top - rw.top));
}

function _construireApercuChat(){
  const ap = document.createElement('div');
  ap.id = 'apercuChat'; ap.className = 'apercuChat';
  ap.setAttribute('aria-hidden', 'true');      // il répète le journal, déjà aria-live
  const t = document.createElement('span');
  t.className = 'txt';
  const bg = document.createElement('span');
  bg.className = 'badgeAct'; bg.hidden = true;
  const pl = document.createElement('button');
  pl.type = 'button'; pl.className = 'act placer'; pl.tabIndex = -1;
  pl.addEventListener('click', e => { e.stopPropagation(); placerApercuChat(_posApercu === 'haut' ? 'bas' : 'haut'); });
  const ec = document.createElement('button');
  ec.type = 'button'; ec.className = 'act ecrire'; ec.tabIndex = -1;
  ec.textContent = '✏';
  ec.title = 'Écrire au chat';
  // iOS n'ouvre le clavier que si le focus part du geste lui-même.
  ec.addEventListener('click', e => { e.stopPropagation(); ouvrirChat(true); });
  ap.addEventListener('click', () => ouvrirChat(false));
  ap.append(pl, t, bg, ec);
  setTimeout(_majApercuChat, 0);
  return ap;
}
function _majApercuChat(){
  const ap = document.getElementById('apercuChat');
  if(!ap) return;
  ap.hidden = !_apercuChat;
  const pl = ap.querySelector('.placer');
  pl.textContent = _posApercu === 'haut' ? '⬇' : '⬆';
  pl.title = _posApercu === 'haut' ? 'Placer le chat en bas de l\'écran' : 'Placer le chat en haut de l\'écran';
  // Les quatre dernières lignes : messages, rapports, et toujours les jets
  // de dés, même quand le filtre Dés les cache dans le journal.
  const l = [...document.querySelectorAll('#journal > .msg, #journal > .rap, #journal > .jet')].slice(-4);
  const t = ap.querySelector('.txt');
  t.replaceChildren(...(l.length ? l : [null]).map(x => {
    const d = document.createElement('div');
    d.className = x && x.classList.contains('jet') ? 'jet' : '';
    d.textContent = x ? _texteLigneChat(x) : 'Chat';
    return d;
  }));
  const bg = ap.querySelector('.badgeAct');
  bg.textContent = _nonLus > 99 ? '99+' : String(_nonLus);
  bg.hidden = !_nonLus;
  majHautBas();
}
// Texte d'une ligne du journal ; un jet de dés se lit « IFT DR 3 5 = 8 Pierre ».
function _texteLigneChat(x){
  if(!x.classList.contains('jet')) return x.textContent;
  return [...x.childNodes].map(n => n.nodeName === 'IMG' ? n.alt : n.textContent).filter(v => v && v.trim()).join(' ');
}
function basculerApercuChat(){
  _apercuChat = !_apercuChat;
  try { localStorage.setItem('aslweb.apercuChat', _apercuChat ? '1' : '0'); } catch(e){}
  _majApercuChat();
  _surRedimension();
}
function _majNonLus(){ _majApercuChat(); rafraichirActions(['vueJournal']); }

// Chaque ligne : l'aperçu la montre ; un message d'un autre joueur, reçu
// ou relu, compte parmi les non-lus tant que le chat est fermé, et un
// message reçu du réseau fait vibrer.
_apresLigne.push(el => {
  if(!_telLike()) return;
  if(el.classList.contains('msg') && (_netReception || _relecture)){
    if(!_etatChat){ _nonLus++; _majNonLus(); }
    if(_netReception) vibrer();
  }
  if(el.classList.contains('msg') || el.classList.contains('rap') || el.classList.contains('jet')) _majApercuChat();
});

function _poserEtatChat(e, depuisRetour){
  const bas = document.getElementById('bas');
  const avant = _etatChat;
  _etatChat = e;
  if(e) bas.dataset.etat = e; else delete bas.dataset.etat;
  document.body.classList.toggle('chatOuvert', !!e);
  if(e){
    _nonLus = 0; _majNonLus();
    if(!avant) _pousserHistorique();
    fermerEventail();
  } else if(avant){
    if(depuisRetour) _histChat = false; else _retirerHistorique();
  }
  majHautBas();
  _majReserveCarte();
  for(const f of _apresEtatChat) f(e);
}
const _apresEtatChat = [];

// Chat compact, depuis la carte ; saisir : le clavier s'ouvre aussitôt.
function ouvrirChat(saisir){
  if(!_telLike()) return;
  if(_vue() !== 'carte') vueMobile('carte');
  const bas = document.getElementById('bas');
  bas.hidden = false;
  if(_etatChat !== 'compact') _poserEtatChat('compact');
  else _majReserveCarte();
  const j = document.getElementById('journal');
  j.scrollTop = j.scrollHeight;
  _nouveauxChat = 0; _majNouveauxChat();
  if(saisir) document.getElementById('chatIn').focus();
}
function fermerChat(depuisRetour){
  if(_etatChat !== 'compact') return;
  const ci = document.getElementById('chatIn');
  if(document.activeElement === ci) ci.blur();
  document.getElementById('bas').hidden = true;
  _poserEtatChat(null, depuisRetour);
}
// Sélectionner une pile ramène le chat compact à l'aperçu.
_apresSelection.push(() => { if(_etatChat === 'compact' && _sel.size) fermerChat(); });

// ── Bouton Retour d'Android : ouvrir le chat ajoute une entrée à
// l'historique ; Retour referme le chat au lieu de quitter la page.
let _histChat = false, _popIgnore = false;
function _pousserHistorique(){
  if(_histChat) return;
  try { history.pushState({chat: true}, ''); _histChat = true; } catch(e){}
}
function _retirerHistorique(){
  if(!_histChat) return;
  _histChat = false;
  _popIgnore = true;
  history.back();
}
window.addEventListener('popstate', () => {
  if(_popIgnore){ _popIgnore = false; return; }
  if(!_histChat) return;
  _histChat = false;
  if(_etatChat === 'plein') vueMobile('carte');
  else if(_etatChat === 'compact') fermerChat(true);
});

// Poignée du chat compact : vers le bas, l'aperçu ; vers le haut, le plein.
// Chat placé en haut, la poignée est au pied et le geste s'inverse.
function _brancherPoigneeChat(){
  const bas = document.getElementById('bas');
  const pg = document.createElement('div');
  pg.className = 'poigneeChat';
  pg.setAttribute('aria-hidden', 'true');
  bas.insertBefore(pg, bas.firstChild);
  pg.addEventListener('pointerdown', e => {
    e.preventDefault();
    const id = e.pointerId, y0 = e.clientY;
    const fin = ev => {
      if(ev.pointerId !== id) return;
      document.removeEventListener('pointerup', fin);
      document.removeEventListener('pointercancel', fin);
      const dy = (ev.clientY - y0) * (_posApercu === 'haut' ? -1 : 1);
      if(dy > 50) fermerChat();
      else if(dy < -50 || Math.abs(dy) < 6) vueMobile('journal');
    };
    document.addEventListener('pointerup', fin);
    document.addEventListener('pointercancel', fin);
  });
}

// ═══ CHAT — SAISIE RAPIDE ═══
// Au doigt, une rangée de phrases au-dessus du champ : un appui l'envoie
// au chat telle quelle, sans passer par le champ (le brouillon reste).
const PHRASES_CHAT = ['Hello', 'Assault Move', 'Bypass', 'Double Time', 'Dash'];
function majSaisieRapide(){
  const r = document.getElementById('chatRapide');
  if(!r) return;
  r.replaceChildren();
  for(const texte of PHRASES_CHAT){
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'pastilleChat phrase';
    b.textContent = texte;
    b.title = 'Envoyer au chat';
    b.addEventListener('pointerdown', e => e.preventDefault());   // le clavier ne bouge pas
    b.addEventListener('click', () => { vibrer(); envoyerChat(texte); });
    r.appendChild(b);
  }
}

// ═══ CHAT — LECTURE ═══
// Filtres du journal (au doigt) : Messages, Dés, Rapports, Système, par
// une classe CSS sur #journal ; une alerte n'est jamais masquée. Les lieux
// cités dans les messages et les rapports deviennent des liens qui centrent
// la carte. Le texte vient des autres joueurs : des nœuds texte et des
// span, jamais innerHTML.
const FILTRES_CHAT = [['msg', 'Messages'], ['jet', 'Dés'], ['rap', 'Rapports'], ['sys', 'Système']];
let _filtresChat = (() => {
  try { const l = JSON.parse(localStorage.getItem('aslweb.filtresChat') || 'null'); if(Array.isArray(l)) return new Set(l); } catch(e){}
  return new Set(['msg', 'jet', 'rap']);
})();
function majFiltresChat(){
  const j = document.getElementById('journal');
  j.dataset.filtres = [..._filtresChat].join(' ');
  for(const b of document.querySelectorAll('#filtresChat button')){
    const oui = _filtresChat.has(b.dataset.filtre);
    b.classList.toggle('actif', oui);
    b.setAttribute('aria-pressed', String(oui));
  }
}
function _construireFiltresChat(){
  const f = document.createElement('div');
  f.id = 'filtresChat'; f.className = 'filtresChat';
  f.setAttribute('role', 'toolbar');
  f.setAttribute('aria-label', 'Filtres du journal');
  for(const [c, lib] of FILTRES_CHAT){
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'pastilleChat'; b.dataset.filtre = c;
    b.textContent = lib;
    b.addEventListener('pointerdown', e => e.preventDefault());
    b.addEventListener('click', () => {
      if(_filtresChat.has(c)) _filtresChat.delete(c); else _filtresChat.add(c);
      try { localStorage.setItem('aslweb.filtresChat', JSON.stringify([..._filtresChat])); } catch(e){}
      majFiltresChat();
      const j = document.getElementById('journal'); j.scrollTop = j.scrollHeight;
    });
    f.appendChild(b);
  }
  const bas = document.getElementById('bas');
  bas.insertBefore(f, document.getElementById('journal'));
  majFiltresChat();
}

// Lieux d'une ligne de message ou de rapport, en liens.
function _lierLieux(el){
  if(!_hexParNom.size || !(el.classList.contains('msg') || el.classList.contains('rap'))) return;
  const t = el.textContent;
  const re = /[A-Za-z0-9]+(?:\/(?:NE|NO|SE|SO|N|S))?/g;
  let m, k = 0, morceaux = [];
  while((m = re.exec(t))){
    if(!_hexParNom.has(m[0])) continue;
    if(m.index > k) morceaux.push(document.createTextNode(t.slice(k, m.index)));
    const s = document.createElement('span');
    s.className = 'lieuChat';
    s.textContent = m[0];
    morceaux.push(s);
    k = m.index + m[0].length;
  }
  if(!morceaux.length) return;
  if(k < t.length) morceaux.push(document.createTextNode(t.slice(k)));
  el.replaceChildren(...morceaux);
}
_apresLigne.push(_lierLieux);

function _brancherLectureChat(){
  _construireFiltresChat();
  document.getElementById('journal').addEventListener('click', e => {
    const s = e.target.closest && e.target.closest('.lieuChat');
    if(!s || !_profil || _profil === 'pc') return;
    const pt = _hexParNom.get(s.textContent);
    if(!pt) return;
    // Depuis le journal plein, on revient à la carte pour voir le lieu.
    if(_etatChat === 'plein') vueMobile('carte');
    cadrerSur([pt]);
  });
}

// ═══ CHAT — CHAMP DE SAISIE ═══
// Comme le Chatter de VASSAL, une seule ligne, envoyée par Entrée ; au
// doigt, un bouton ➤ et la touche « Envoyer » du clavier, qui reste ouvert
// pour enchaîner. Une seule voie d'envoi : envoyerSaisie().
function envoyerSaisie(){
  const ci = document.getElementById('chatIn');
  envoyerChat(ci.value);
  ci.value = '';
  _majEnvoi();
}
function _majEnvoi(){
  document.getElementById('chatEnvoi').disabled = !document.getElementById('chatIn').value.trim();
}
// Texte d'aide : sur PC, celui d'avant ; au doigt, le nom sous lequel le
// message partira (« web » pour un observateur).
function majAideChat(){
  document.getElementById('chatIn').placeholder = !_profil || _profil === 'pc'
    ? 'Message au chat (Entrée pour envoyer)' : `Message en tant que ${joueur()}`;
}
function _brancherChat(){
  const ci = document.getElementById('chatIn'), bt = document.getElementById('chatEnvoi');
  ci.addEventListener('keydown', e => {
    if(e.key === 'Enter'){
      e.preventDefault();
      // Entrée sur un message vide : on quitte le chat, les raccourcis reviennent.
      if(!ci.value.trim()){ if(!_profil || _profil === 'pc') ci.blur(); return; }
      envoyerSaisie();
    } else if(e.key === 'Escape' && (!_profil || _profil === 'pc')){ e.preventDefault(); ci.blur(); }
  });
  // Sur PC, Entrée hors de tout champ place le curseur dans le chat.
  document.addEventListener('keydown', e => {
    if(e.key !== 'Enter' || e.defaultPrevented || e.ctrlKey || e.altKey || e.metaKey || e.shiftKey) return;
    if(_profil && _profil !== 'pc') return;
    const t = e.target;
    if(/^(INPUT|SELECT|TEXTAREA|BUTTON|A)$/.test(t.tagName) || t.isContentEditable) return;
    if(document.querySelector('.fenetre, .voile, #ctx') || feuilleOuverte()) return;
    if(ci.offsetParent === null) return;
    e.preventDefault();
    ci.focus();
  });
  ci.addEventListener('input', _majEnvoi);
  // Le bouton ne prend pas le focus : le clavier reste ouvert.
  bt.addEventListener('pointerdown', e => e.preventDefault());
  bt.addEventListener('click', () => { if(ci.value.trim()) envoyerSaisie(); });
  const r = document.createElement('div');
  r.id = 'chatRapide'; r.className = 'chatRapide';
  r.setAttribute('aria-label', 'Saisie rapide');
  document.getElementById('bas').insertBefore(r, document.getElementById('chatBarre'));
  majSaisieRapide();
  _brancherLectureChat();
  _brancherClavier();
  _brancherPoigneeChat();
  majAideChat();
}
_apresProfil.push(majAideChat);

