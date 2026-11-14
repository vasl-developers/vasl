// ═══════════════════════════════════════════════════════════════
// COMPOSANTS TACTILES — feuille du bas, barre de pile, éventail,
// poses et déplacements en deux appuis, verrou des pions
// ═══════════════════════════════════════════════════════════════

// ── Feuille du bas générique : fond sombre, poignée, fermeture par glissé
// vers le bas, Échap ou appui sur le voile.
//   hauteur : 'auto' | 'moitie' | 'plein'
const _feuilles = new Map();      // id → {el, voile, fermer}
// deplacable : une poignée ⠿ dans l'en-tête promène la feuille sur
// l'écran (double clic : elle revient en bas) ; la place est gardée, par
// feuille, pour les ouvertures suivantes.
const _posFeuilles = new Map();   // id → {x, y} à l'écran
function ouvrirFeuille({id = 'feuille', titre = '', hauteur = 'auto', fermer, voile = true, deplacable = false} = {}){
  fermerFeuille(id);
  const v = document.createElement('div');
  v.className = 'voile' + (voile ? '' : ' transparent');
  const f = document.createElement('div');
  f.className = 'feuille ' + hauteur;
  f.dataset.feuille = id;
  f.setAttribute('role', 'dialog');
  f.setAttribute('aria-modal', 'true');
  f.innerHTML = '<div class="poigneeFeuille" aria-hidden="true"></div>'
    + '<div class="teteFeuille"><span class="tt"></span><button type="button" class="ferme" aria-label="Fermer">✕</button></div>'
    + '<div class="corpsFeuille"></div>';
  f.querySelector('.tt').textContent = titre;
  if(!titre) f.querySelector('.teteFeuille').classList.add('sansTitre');
  document.body.append(v, f);
  const h = {el: f, voile: v, corps: f.querySelector('.corpsFeuille'), fermer: () => fermerFeuille(id),
             titre: t => { f.querySelector('.tt').textContent = t; }};
  h._surFermer = fermer;
  _feuilles.set(id, h);
  v.addEventListener('pointerdown', e => { e.preventDefault(); e.stopPropagation(); h.fermer(); });
  f.addEventListener('pointerdown', e => e.stopPropagation());
  f.querySelector('.ferme').addEventListener('click', () => h.fermer());
  if(deplacable){
    const placer = () => {
      const p = _posFeuilles.get(id);
      f.classList.toggle('deplacee', !!p);
      if(!p){ f.style.left = f.style.top = f.style.width = ''; return; }
      f.style.width = Math.min(720, innerWidth) + 'px';
      const x = Math.max(0, Math.min(p.x, innerWidth - f.offsetWidth));
      const y = Math.max(0, Math.min(p.y, innerHeight - f.offsetHeight));
      f.style.left = x + 'px'; f.style.top = y + 'px';
    };
    const tete = f.querySelector('.teteFeuille');
    tete.insertBefore(_poigneeVolant(() => { const r = f.getBoundingClientRect(); return {x: r.left, y: r.top}; },
      (x, y) => { f.style.transition = 'none'; _posFeuilles.set(id, {x, y}); placer(); },
      () => { _posFeuilles.delete(id); placer(); }), tete.firstChild);
    h.placer = placer;
    placer();
  }
  // Glisser la poignée (ou l'en-tête) vers le bas referme la feuille.
  for(const prise of [f.querySelector('.poigneeFeuille'), f.querySelector('.teteFeuille')]){
    prise.addEventListener('pointerdown', e => {
      if(e.target.closest('button')) return;
      const id0 = e.pointerId, y0 = e.clientY;
      let dy = 0;
      f.style.transition = 'none';
      const bouge = ev => { if(ev.pointerId !== id0) return; dy = Math.max(0, ev.clientY - y0); f.style.transform = `translateY(${dy}px)`; };
      const finir = ev => {
        if(ev.pointerId !== id0) return;
        document.removeEventListener('pointermove', bouge);
        document.removeEventListener('pointerup', finir);
        document.removeEventListener('pointercancel', finir);
        f.style.transition = ''; f.style.transform = '';
        if(dy > 80) h.fermer();
      };
      document.addEventListener('pointermove', bouge);
      document.addEventListener('pointerup', finir);
      document.addEventListener('pointercancel', finir);
    });
  }
  setTimeout(() => { const b = f.querySelector('.corpsFeuille button, .corpsFeuille input, .corpsFeuille select'); if(b && _profil === 'pc') b.focus(); }, 0);
  return h;
}
function fermerFeuille(id){
  const h = _feuilles.get(id);
  if(!h) return false;
  _feuilles.delete(id);
  h.el.remove(); h.voile.remove();
  if(h._surFermer) h._surFermer();
  return true;
}
const feuilleOuverte = () => _feuilles.size > 0;
// Dans une feuille, ↑ et ↓ passent d'une entrée à l'autre.
window.addEventListener('keydown', e => {
  if(!_feuilles.size || (e.key !== 'ArrowDown' && e.key !== 'ArrowUp')) return;
  const f = [..._feuilles.values()].pop().el;
  const l = [...f.querySelectorAll('.corpsFeuille button:not(:disabled)')];
  if(!l.length || /^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)) return;
  e.preventDefault(); e.stopImmediatePropagation();
  const i = l.indexOf(document.activeElement);
  l[(i + (e.key === 'ArrowDown' ? 1 : -1) + l.length) % l.length].focus();
}, true);
// Échap referme la dernière feuille ouverte, avant tout le reste.
window.addEventListener('keydown', e => {
  if(e.key !== 'Escape' || !_feuilles.size) return;
  e.preventDefault(); e.stopImmediatePropagation();
  fermerFeuille([..._feuilles.keys()].pop());
}, true);

// ── Commandes rapides : celles, nommées et dotées d'une touche, des pions
// donnés, avec les filtres du menu ; classées par fréquence d'usage, puis
// dans l'ordre des traits.
// Les commandes d'état de COMMANDES_ETAT viennent en tête, dans cet ordre,
// suivies de trois autres (cinq s'il n'y en a aucune).
function commandesRapides(pions, n = 5){
  const vues = new Map();
  let rang = 0;
  for(const p of pions){
    const a = arbreCommandes(p);
    if(!a) continue;
    const aplatir = items => { for(const c of items){
      if(c.items){ aplatir(c.items); continue; }
      if(!c.touche || c.grise) continue;
      const k = c.nom + '\u0001' + c.touche;
      if(!vues.has(k)) vues.set(k, {nom: c.nom, touche: c.touche, cmd: c.cmd, p, pions: [], rang: rang++});
      vues.get(k).pions.push({p, cmd: c.cmd});
    } };
    aplatir(a.items);
  }
  const l = [...vues.values()];
  const etat = COMMANDES_ETAT.flatMap(nom => l.filter(c => c.nom === nom));
  for(const c of etat){
    const oui = c.pions.filter(x => _etatCommande(x.p, c.nom, x.cmd)).length;
    c.etat = oui === 0 ? 'non' : oui === c.pions.length ? 'oui' : 'mixte';
  }
  const autres = l.filter(c => !etat.includes(c))
                  .sort((a, b) => (_freqCmd[b.nom] || 0) - (_freqCmd[a.nom] || 0) || a.rang - b.rang);
  return [...etat, ...autres.slice(0, etat.length ? 3 : n)];
}

// ── Commandes d'état, en tête des barres rapides : Prep Fire, First Fire
// et Pin posent leur marqueur, Break brise l'unité (et pose le DM),
// Conceal la dissimule. Leur bouton est allumé quand toutes les unités
// visées sont dans cet état ; un appui les y met toutes, un second les
// en sort — il retire le marqueur, ou renvoie la touche (Break, Conceal).
const COMMANDES_ETAT = ['Prep Fire', 'First Fire', 'Break', 'Pin', 'Conceal'];
// Le marqueur de ce nom au-dessus de p, dans son lieu. Celui qu'on pose
// au sommet (placement 0 : Prep Fire, First Fire) vaut pour toutes les
// unités du lieu ; les autres (Pin) pour l'unité qu'ils coiffent, jusqu'à
// l'unité suivante.
function _marqueurAuDessus(p, m){
  const s = pile(cle(p));
  for(const q of s.slice(s.indexOf(p) + 1)){
    if(_lieuSepare(q)) break;
    if(!q.deleted && q.nom === m.nom) return q;
    if(m.placement && q.couche === 'unite' && !infoDissimulation(q).compteur) break;
  }
  return null;
}
function _etatCommande(p, nom, cmd){
  if(nom === 'Break') return /broken/i.test(p.nom);
  if(nom === 'Conceal') return !!infoDissimulation(p).proprio;
  return !!(cmd && cmd.marqueur && _marqueurAuDessus(p, cmd.marqueur));
}
// Icône d'une commande d'état : le marqueur qu'elle pose (Prep Fire,
// First Fire, Pin, le DM de Break) ou le « ? » de l'unité (Conceal), pris
// dans la base VASL et réduit au côté voulu. Le nom reste écrit tant que
// l'image n'est pas chargée, ou si elle est introuvable.
function _iconeCommande(c, cote){
  const boite = document.createElement('span');
  boite.className = 'iconeCmd';
  boite.style.width = boite.style.height = cote + 'px';
  const txt = document.createElement('span');
  txt.className = 'lbIcone'; txt.textContent = c.nom;
  boite.appendChild(txt);
  if(c.nom === 'Conceal'){
    const im = c.pions.map(x => infoDissimulation(x.p).image).find(Boolean);
    if(im) chargerImage(im).then(u => {
      if(!u) return;
      const i = new Image(); i.src = u; i.alt = '';
      i.style.cssText = 'left:0;top:0;width:100%;height:100%';
      boite.replaceChildren(i);
    });
  } else if(_bdd){
    const ch = c.pions.map(x => x.cmd.pose).find(Boolean);
    const n = ch && (ch.replace(/\\(.)/g, '$1').match(/PieceSlot:([^/]*)$/) || [])[1];
    const x = ch && (_slotDuChemin(ch) || [..._bdd.parGpid.values()].find(y => y.n === n));
    if(x) composerVignette(boite, lireSlot(x), cote);
  }
  return boite;
}
function basculerCommandeEtat(c){
  const l = c.pions.filter(x => !accesRefuse(x.p));
  if(!l.length){ refuser(c.pions[0].p); return; }
  const dans = x => _etatCommande(x.p, c.nom, x.cmd);
  if(l.every(dans)){
    const marques = [...new Set(l.map(x => x.cmd.marqueur && _marqueurAuDessus(x.p, x.cmd.marqueur)).filter(Boolean))];
    if(marques.length){
      for(const q of marques) retirer(q, true);
      chatLog(`✖️ ${c.nom} retiré${marques.length > 1 ? ` (${marques.length})` : ''}`);
    } else commander(l.map(x => x.p), c.touche);
    return;
  }
  // Une à une : le marqueur posé au sommet de la pile (Prep Fire, First
  // Fire) vaut pour les unités suivantes du même lieu.
  for(const x of l) if(!dans(x)) commander([x.p], c.touche);
}
// Pions visés par les barres : la sélection, sinon la pile courante.
const _visesBarre = () => _sel.size ? trierParPile([..._sel])
                    : _pileSel ? pile(_pileSel).filter(selectionnable) : [];

function _rangeeCommandes(boite, avecDeplacer){
  const vises = _visesBarre();
  const haut = vises[vises.length - 1];
  // 🗑 et non ✕ : la croix rouge ferme le panneau, elle ne retire rien.
  // Une seule corbeille : elle prend la place de la commande « Delete »
  // des pions, à défaut la fin de la rangée.
  let corbeille = false;
  const poserCorbeille = () => {
    corbeille = true;
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'act bouton danger';
    b.textContent = '🗑'; b.title = 'Retirer de la carte'; b.setAttribute('aria-label', b.title);
    b.addEventListener('click', () => retirerSelection());
    boite.appendChild(b);
  };
  for(const c of commandesRapides(vises)){
    if(c.nom === 'Delete' && !c.etat){ if(!corbeille) poserCorbeille(); continue; }
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'act bouton cmdRapide';
    b.textContent = c.nom;
    const t = libelleTouche(c.touche);
    b.title = c.nom + (t ? ` [${t}]` : '');
    if(c.etat){
      // Le pion plutôt que le texte : le marqueur posé, ou le « ? ».
      b.replaceChildren(_iconeCommande(c, _profil === 'pc' ? 32 : 36));
      b.setAttribute('aria-label', c.nom);
      b.classList.add('cmdEtat');
      b.classList.toggle('actif', c.etat === 'oui');
      b.classList.toggle('mixte', c.etat === 'mixte');
      b.setAttribute('aria-pressed', c.etat === 'oui' ? 'true' : c.etat === 'mixte' ? 'mixed' : 'false');
      if(c.etat === 'oui') b.title += ' — appuyer de nouveau pour l\'enlever';
      else if(c.etat === 'mixte') b.title += ` — ${c.pions.filter(x => _etatCommande(x.p, c.nom, x.cmd)).length} unité(s) sur ${c.pions.length}`;
    }
    b.addEventListener('click', () => {
      _freqCmd[c.nom] = (_freqCmd[c.nom] || 0) + 1;
      try { localStorage.setItem('aslweb.freqCmd', JSON.stringify(_freqCmd)); } catch(e){}
      if(c.etat) basculerCommandeEtat(c);
      else if(_sel.size) commanderSelection(c.touche, undefined, c.nom);
      else commander(vises.filter(p => !accesRefuse(p, c.touche) && _aCommande(p, c.touche, c.nom)), c.touche);
      afficherPile();
    });
    boite.appendChild(b);
  }
  const outil = (txt, titre, f, cls) => {
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'act bouton ' + (cls || '');
    b.textContent = txt; b.title = titre; b.setAttribute('aria-label', titre);
    b.addEventListener('click', f);
    boite.appendChild(b);
    return b;
  };
  if(haut) outil('⋮ Toutes', 'Toutes les commandes', e => {
    const r = e.currentTarget.getBoundingClientRect();
    ouvrirMenu(haut, r.left, r.bottom, _profil === 'pc' ? 'mouse' : 'touch');
  });
  if(avecDeplacer && vises.length) outil('Déplacer…', 'Déplacer : toucher ensuite la destination', () => armerDeplacement());
  if(vises.length && !corbeille) poserCorbeille();
}

// Retire la sélection (ou la pile courante) ; confirmation dès 3 pions.
function retirerSelection(){
  const l = _visesBarre().filter(p => !accesRefuse(p));
  if(!l.length){ const v = _visesBarre(); if(v.length) refuser(v[0]); return; }
  if(l.length >= 3 && !confirm(T(`Retirer ${l.length} pions de la carte ?`))) return;
  for(const p of l) retirer(p, l.length > 1);
  if(l.length > 1) chatLog(`✖️ ${l.length} pions retirés`);
}

// ── Barre de pile (tous profils ; au PC, elle remplace l'onglet Pile) :
// en bas de la carte, tant qu'une pile est sélectionnée et que
// l'éventail est fermé.
function majBarrePile(){
  let b = document.getElementById('barrePile');
  const montrer = _profil && (_sel.size || _pileSel) && !_eventail && !_modeCarte;
  document.body.classList.toggle('avecBarrePile', !!montrer);
  if(!montrer){ if(b) b.remove(); if(typeof _placerVolants === 'function') _placerVolants(); return; }
  if(!b){
    b = document.createElement('div');
    b.id = 'barrePile'; b.className = 'barrePile';
    b.setAttribute('role', 'toolbar');
    b.addEventListener('pointerdown', e => e.stopPropagation());
    document.getElementById('corps').appendChild(b);
  }
  b.replaceChildren();
  const vises = _visesBarre();
  const s = _pileSel ? pile(_pileSel).filter(p => !p.cache) : [];
  const haut = vises[vises.length - 1] || s[s.length - 1];
  // La pile en miniature : un appui ouvre l'éventail.
  const mini = document.createElement('button');
  mini.type = 'button'; mini.className = 'miniPile';
  mini.setAttribute('aria-label', 'Pions de la pile');
  if(haut) mini.appendChild(_copiePion(haut, 34));
  const nb = document.createElement('span');
  nb.className = 'nb';
  nb.textContent = _sel.size && _sel.size < s.filter(selectionnable).length ? `${_sel.size}/${s.length}` : String(s.length);
  mini.appendChild(nb);
  mini.addEventListener('click', () => { if(_pileSel) ouvrirEventail(_pileSel); });
  b.appendChild(mini);
  _rangeeCommandes(b, true);
  b.appendChild(_croixFermer('Fermer la pile'));
  _placerBarrePile();
  _placerVolants();
}
function _placerBarrePile(){
  const b = document.getElementById('barrePile');
  if(!b) return;
  const w = document.getElementById('mapWrap');
  b.style.left = w.offsetLeft + 'px';
  b.style.width = w.clientWidth + 'px';
}

// Copie réduite d'un pion affiché (face, couches, orientation).
function _copiePion(p, cote){
  const boite = document.createElement('div');
  boite.className = 'copiePion';
  boite.style.width = boite.style.height = cote + 'px';
  const c = p.el.cloneNode(true);
  c.classList.remove('sel', 'deplie', 'coup', 'verrou');
  c.removeAttribute('title');
  c.style.cssText = `position:absolute;left:0;top:0;width:${p.sz}px;height:${p.sz}px;`
                  + `transform:scale(${cote / p.sz});transform-origin:0 0;opacity:1`;
  boite.appendChild(c);
  return boite;
}

// ── Poignée d'une fenêtre flottante : glissée (souris ou doigt), elle
// déplace la fenêtre ; un double clic (double appui) la rend à sa place.
// depart() donne la position de la fenêtre, bouger(x, y) la change.
function _poigneeVolant(depart, bouger, revenir){
  const h = document.createElement('div');
  h.className = 'poigneeVolant';
  h.textContent = '⠿';
  const titre = 'Glisser pour déplacer la fenêtre (double clic : la remettre à sa place)';
  h.title = titre; h.setAttribute('aria-label', titre);
  h.addEventListener('pointerdown', e => {
    if(e.button > 0) return;
    e.preventDefault(); e.stopPropagation();
    const id = e.pointerId, o = depart(), x0 = e.clientX, y0 = e.clientY;
    try { h.setPointerCapture(id); } catch(err){}
    const bouge = ev => { if(ev.pointerId === id){ ev.preventDefault(); bouger(o.x + ev.clientX - x0, o.y + ev.clientY - y0); } };
    const fin = ev => {
      if(ev.pointerId !== id) return;
      h.removeEventListener('pointermove', bouge);
      h.removeEventListener('pointerup', fin);
      h.removeEventListener('pointercancel', fin);
    };
    h.addEventListener('pointermove', bouge);
    h.addEventListener('pointerup', fin);
    h.addEventListener('pointercancel', fin);
  });
  h.addEventListener('dblclick', e => { e.preventDefault(); e.stopPropagation(); revenir(); });
  return h;
}

// ── Éventail de pile : au doigt, viser un pion décalé de 4 px est
// impossible ; une bande flottante montre la pile en grand, du fond
// (à gauche) vers le sommet, au-dessus de la pile.
let _eventail = null;            // {k, el, coche} ; coche : sélection faite aux cases
let _posEventail = null;         // {x, y} à l'écran, une fois déplacé
// Éventail tourné de 90° (en colonne) : réglage du poste.
let _eventailVertical = _lirePref('aslweb.eventailVertical') === '1';
const EVENTAIL_COTE = 60, EVENTAIL_PAR_RANG = 6;
function ouvrirEventail(k){
  if(_filArme) return;
  // Un seul éventail à la fois : l'ancien se ferme d'abord, sans quoi il
  // restait à l'écran, figé, sans que rien ne puisse le fermer.
  if(_eventail) fermerEventail();
  _eventail = {k, el: null};
  majEventail();
  majBarrePile();
}
function fermerEventail(){
  document.querySelectorAll('.eventail').forEach(e => e.remove());
  if(!_eventail) return;
  _eventail = null;
  majBarrePile();
}
function majEventail(){
  const ev = _eventail;
  if(!ev) return;
  const s = pile(ev.k).filter(selectionnable);
  if(!s.length){ fermerEventail(); return; }
  const defil = ev.el ? ev.el.querySelector('.bandeEv').scrollTop : 0;
  if(ev.el) ev.el.remove();
  const el = ev.el = document.createElement('div');
  el.id = 'eventail'; el.className = 'eventail';
  el.setAttribute('role', 'dialog');
  el.setAttribute('aria-label', 'Pions de la pile');
  el.addEventListener('pointerdown', e => e.stopPropagation());
  el.addEventListener('contextmenu', e => e.preventDefault());
  el.classList.toggle('vertical', _eventailVertical);
  const bande = document.createElement('div');
  bande.className = 'bandeEv';
  if(!_eventailVertical) bande.style.maxWidth = (EVENTAIL_PAR_RANG * (EVENTAIL_COTE + 8) + 56) + 'px';
  const tout = document.createElement('button');
  // Tout (☑) et Aucun (☐) : sur la barre du haut, de part et d'autre du Flare.
  tout.type = 'button'; tout.className = 'tout';
  tout.textContent = '☑';
  tout.title = 'Sélectionner toute la pile';
  tout.setAttribute('aria-label', tout.title);
  tout.addEventListener('pointerdown', e => e.stopPropagation());
  tout.addEventListener('click', e => { e.stopPropagation(); ev.coche = true; selectionnerPile(ev.k); vibrer(); });
  const marque = document.createElement('div');
  marque.className = 'inserEv';
  marque.hidden = true;
  for(const p of s) bande.appendChild(_vignetteEventail(p, ev.k, bande, marque));
  // Aucun : la pile reste ouverte, sans pion sélectionné.
  const aucun = document.createElement('button');
  aucun.type = 'button'; aucun.className = 'aucun';
  aucun.textContent = '☐';
  aucun.title = 'Désélectionner tous les pions de la pile';
  aucun.setAttribute('aria-label', aucun.title);
  aucun.addEventListener('pointerdown', e => e.stopPropagation());
  aucun.addEventListener('click', e => {
    e.stopPropagation();
    ev.coche = false;
    selectionner([]);
    _pileSel = ev.k;
    afficherPile();
    vibrer();
  });
  bande.append(marque);
  const actions = document.createElement('div');
  actions.className = 'actionsEv';
  _rangeeCommandes(actions, false);
  // Flare (js/flare.js) : la pile montrée à l'adversaire. Au centre de
  // la barre du haut.
  const flare = document.createElement('button');
  flare.type = 'button'; flare.className = 'flareEv';
  flare.textContent = '✺';
  flare.title = 'Flare : montrer la pile à l\'adversaire';
  flare.setAttribute('aria-label', flare.title);
  flare.addEventListener('pointerdown', e => e.stopPropagation());
  flare.addEventListener('click', e => { e.stopPropagation(); flarePile(ev.k); });
  // Pas de poignée : tout ce qui, dans l'éventail, n'est ni un bouton ni
  // une vignette le déplace (souris ou doigt) ; un double clic (double
  // appui) au même endroit le rend à sa place, près de la pile. Une bande
  // qui défile garde ses vides pour défiler.
  const defile = () => bande.scrollHeight > bande.clientHeight + 1 || bande.scrollWidth > bande.clientWidth + 1;
  const libre = t => t === el || t === actions || t === haut || (t === bande && !defile());
  el.addEventListener('pointerdown', e => {
    if(e.button > 0 || !libre(e.target)) return;
    e.preventDefault();
    const id = e.pointerId, x0 = e.clientX, y0 = e.clientY;
    const o = {x: parseFloat(el.style.left) || 0, y: parseFloat(el.style.top) || 0};
    try { el.setPointerCapture(id); } catch(err){}
    el.classList.add('deplace');
    const bouge = m => {
      if(m.pointerId !== id) return;
      m.preventDefault();
      _posEventail = {x: o.x + m.clientX - x0, y: o.y + m.clientY - y0};
      placerEventail();
    };
    const fin = m => {
      if(m.pointerId !== id) return;
      el.classList.remove('deplace');
      el.removeEventListener('pointermove', bouge);
      el.removeEventListener('pointerup', fin);
      el.removeEventListener('pointercancel', fin);
    };
    el.addEventListener('pointermove', bouge);
    el.addEventListener('pointerup', fin);
    el.addEventListener('pointercancel', fin);
  });
  el.addEventListener('dblclick', e => {
    if(!libre(e.target)) return;
    e.preventDefault(); e.stopPropagation();
    _posEventail = null; placerEventail();
  });
  el.title = 'Glisser pour déplacer la fenêtre (double clic : la remettre à sa place)';
  // Tourner de 90° : bande ↔ colonne ; l'éventail se replace près de la pile.
  const tourner = document.createElement('button');
  tourner.type = 'button'; tourner.className = 'tournerEv';
  tourner.textContent = '⟳';            // celui de « Tourner la carte de 90° »
  tourner.title = 'Tourner l\'éventail de 90°';
  tourner.setAttribute('aria-label', tourner.title);
  tourner.addEventListener('click', e => {
    e.stopPropagation();
    _eventailVertical = !_eventailVertical;
    try { localStorage.setItem('aslweb.eventailVertical', _eventailVertical ? '1' : '0'); } catch(err){}
    _posEventail = null;
    majEventail();
  });
  const haut = document.createElement('div');
  haut.className = 'hautEv';
  haut.append(tout, flare, aucun);
  el.append(tourner, haut, _croixFermer('Fermer la pile'), bande, actions);
  document.body.appendChild(el);
  // Le doigt déplace l'éventail par les vides de la bande, sauf si elle défile.
  bande.style.touchAction = defile() ? '' : 'none';
  bande.scrollTop = defil;
  placerEventail();
}
function placerEventail(){
  const ev = _eventail;
  if(!ev || !ev.el) return;
  const s = pile(ev.k).filter(p => !p.cache);
  const haut = s[s.length - 1];
  if(!haut){ fermerEventail(); return; }
  const r = haut.el.getBoundingClientRect(), bas = s[0].el.getBoundingClientRect();
  const w = document.getElementById('mapWrap').getBoundingClientRect();
  const el = ev.el, L = el.offsetWidth, H = el.offsetHeight;
  let x = (r.left + r.right) / 2 - L / 2;
  let y = Math.min(r.top, bas.top) - H - 10;
  if(y < w.top + 4) y = Math.max(r.bottom, bas.bottom) + 10;
  // En colonne : à droite de la pile, ou à gauche faute de place.
  if(el.classList.contains('vertical')){
    x = Math.max(r.right, bas.right) + 10;
    if(x + L > w.right - 8) x = Math.min(r.left, bas.left) - L - 10;
    y = (r.top + r.bottom) / 2 - H / 2;
  }
  // Déplacé par sa poignée : il reste où on l'a mis, d'une pile à l'autre.
  if(_posEventail){ x = _posEventail.x; y = _posEventail.y; }
  x = Math.max(w.left + 8, Math.min(x, w.right - L - 8));
  y = Math.max(w.top + 4, Math.min(y, w.bottom - H - 4));
  el.style.left = x + 'px'; el.style.top = y + 'px';
}

function _vignetteEventail(p, k, bande, marque){
  const v = document.createElement('div');
  v.className = 'vigEv';
  const masque = accesRefuse(p), horsCouche = !COUCHES_SEL[p.couche || 'unite'];
  v.classList.toggle('sel', _sel.has(p));
  v.classList.toggle('masque', masque);
  v.classList.toggle('horsCouche', horsCouche);
  v.title = p.el.title || p.nom;
  v.setAttribute('aria-label', (p.el.title || p.nom) + (_sel.has(p) ? ', sélectionné' : ''));
  v.appendChild(_copiePion(p, EVENTAIL_COTE));
  if(masque){ const c = document.createElement('span'); c.className = 'cadenas'; c.textContent = '🔒'; v.appendChild(c); }
  if(horsCouche) return v;        // couche non sélectionnable : vue, pas prise
  const ck = document.createElement('button');
  ck.type = 'button'; ck.className = 'ckEv';
  ck.textContent = _sel.has(p) ? '☑' : '☐';
  ck.setAttribute('aria-label', _sel.has(p) ? 'Retirer de la sélection' : 'Ajouter à la sélection');
  ck.addEventListener('pointerdown', e => e.stopPropagation());
  ck.addEventListener('click', e => { e.stopPropagation(); if(_eventail) _eventail.coche = true; selectionner([p], 'basculer'); vibrer(); });
  v.appendChild(ck);
  // À la souris : le clic droit ouvre les commandes du pion, comme sur la carte.
  v.addEventListener('contextmenu', e => {
    if(_dernierPointeur !== 'mouse') return;
    e.preventDefault(); e.stopPropagation();
    if(!_sel.has(p)) selectionner([p]);
    ouvrirMenu(p, e.clientX, e.clientY, 'mouse');
  });
  v.addEventListener('pointerdown', e => {
    if(e.button > 0) return;
    const x0 = e.clientX, y0 = e.clientY;
    // Appui long : le menu de ce pion, en feuille du bas.
    const timer = setTimeout(() => {
      if(_glisse) _glisse.abandonner();
      vibrer();
      if(!_sel.has(p)) selectionner([p]);
      ouvrirMenu(p, x0, y0, 'touch');
    }, APPUI_LONG);
    // Rang visé dans la bande : avant ou après la vignette sous le doigt.
    const rangVise = ev => {
      const cases = [...bande.querySelectorAll('.vigEv')];
      let best = null, bd = Infinity;
      for(const c of cases){
        const r = c.getBoundingClientRect();
        const d = Math.hypot((r.left + r.right) / 2 - ev.clientX, (r.top + r.bottom) / 2 - ev.clientY);
        if(d < bd){ bd = d; best = {c, r}; }
      }
      if(!best) return null;
      // En colonne, « après » (plus haut dans la pile) est au-dessous.
      const apres = _eventailVertical ? ev.clientY > (best.r.top + best.r.bottom) / 2
                                      : ev.clientX > (best.r.left + best.r.right) / 2;
      return {c: best.c, r: best.r, apres, i: cases.indexOf(best.c)};
    };
    const doigt = !!e.pointerType && e.pointerType !== 'mouse';
    // Un pion tiré de l'éventail part seul. Il n'emporte les autres que
    // s'il est coché avec eux : la sélection de la pile entière, faite en
    // ouvrant l'éventail, ne compte pas, il faut les cases à cocher.
    const emporte = () => _eventail && _eventail.coche && _sel.has(p) && _sel.size > 1 ? piecesADeplacer() : [p];
    glisser(e, () => sommetDeplace(emporte(), p), EVENTAIL_COTE, (cx, cy) => {
      // Lâché sur la carte : ce pion, ou les pions cochés s'il en est.
      const c = clientVersCarte(cx, cy);
      if(!c || masque) return;
      (doigt ? deplacerAuDoigt : deplacer)(emporte(), c.x, c.y);
    }, () => {
      clearTimeout(timer);
      if(_eventail) _eventail.coche = false;
      selectionner([p]); vibrer();
    }, {
      zone: bande,
      debut: () => clearTimeout(timer),
      fin: () => { clearTimeout(timer); marque.hidden = true; },
      dansZone: ev => {
        const r = rangVise(ev);
        if(!r) return;
        const br = bande.getBoundingClientRect();
        marque.hidden = false;
        if(_eventailVertical){
          // Trait horizontal, au-dessus ou au-dessous de la vignette visée.
          marque.style.left = (r.r.left - br.left + bande.scrollLeft) + 'px';
          marque.style.top = ((r.apres ? r.r.bottom + 3 : r.r.top - 5) - br.top + bande.scrollTop) + 'px';
          marque.style.width = r.r.width + 'px'; marque.style.height = '3px';
        } else {
          marque.style.left = ((r.apres ? r.r.right + 3 : r.r.left - 5) - br.left + bande.scrollLeft) + 'px';
          marque.style.top = (r.r.top - br.top + bande.scrollTop) + 'px';
          marque.style.width = ''; marque.style.height = r.r.height + 'px';
        }
      },
      horsZone: () => { marque.hidden = true; },
      lacherZone: ev => {
        // Glissé le long de la bande : un seul M/, au rang visé.
        const r = rangVise(ev);
        if(!r || masque) return;
        const vus = pile(k).filter(selectionnable);
        const q = vus[r.i];
        if(!q || q === p) return;
        const sans = pile(k).filter(x => x !== p);
        deplacerDansPile(p, sans.indexOf(q) + (r.apres ? 1 : 0));
        vibrer();
      }
    });
  });
  return v;
}

// L'éventail et la barre suivent la sélection, la pile et la vue.
_apresRedimension.push(() => { placerEventail(); _placerBarrePile(); _placerPanneauFil(); if(_fil) dessinerFil(); });
_apresProfil.push(() => { fermerEventail(); majBarrePile(); });
document.addEventListener('pointerdown', e => {
  if(_eventail && !e.target.closest('#eventail, .feuille, .voile, #barrePile')) {
    // Un appui sur une pile la rouvre (son geste) ; à côté, dans le vide,
    // il ferme tout : la sélection tolérante ne doit pas la reprendre.
    fermerEventail();
    if(!e.target.closest('.pion')) _eventailFermeA = Date.now();
  }
}, true);
let _eventailFermeA = 0;

// Fermer la pile au doigt : l'éventail, la barre de pile et la sélection.
function fermerPile(){
  fermerEventail();
  if(_sel.size || _pileSel) selectionnerPile(null);
  majBarrePile();
}
// Croix rouge cerclée, en haut à droite d'un panneau de pile.
function _croixFermer(titre = 'Fermer'){
  const x = document.createElement('button');
  x.type = 'button'; x.className = 'croixFermer';
  x.textContent = '✕';
  x.title = titre; x.setAttribute('aria-label', titre);
  x.addEventListener('pointerdown', e => e.stopPropagation());
  x.addEventListener('click', e => { e.stopPropagation(); fermerPile(); });
  return x;
}

// ── Poses et déplacements en deux appuis : un bandeau dit quoi faire,
// l'appui suivant sur la carte (ou sur une pile) agit.
let _modeCarte = null;           // {texte, toucher(c), multiple}
function armerModeCarte(m){
  annulerModeCarte();
  _modeCarte = m;
  fermerEventail();
  const b = document.createElement('div');
  b.id = 'bandeauMode'; b.className = 'bandeauMode';
  b.setAttribute('role', 'status');
  const t = document.createElement('span');
  t.textContent = m.texte;
  b.appendChild(t);
  if(m.optionMultiple){
    const l = document.createElement('label');
    l.innerHTML = '<input type="checkbox"> poses multiples';
    const c = l.querySelector('input');
    c.checked = !!m.multiple;
    c.addEventListener('change', () => { m.multiple = c.checked; try { localStorage.setItem('aslweb.posesMultiples', c.checked ? '1' : '0'); } catch(e){} });
    b.appendChild(l);
  }
  const x = document.createElement('button');
  x.type = 'button'; x.textContent = '✕'; x.setAttribute('aria-label', 'Annuler');
  x.addEventListener('click', annulerModeCarte);
  b.appendChild(x);
  b.addEventListener('pointerdown', e => e.stopPropagation());
  document.getElementById('corps').appendChild(b);
  const w = document.getElementById('mapWrap');
  b.style.left = (w.offsetLeft + 8) + 'px';
  b.style.maxWidth = (w.clientWidth - 16) + 'px';
  majBarrePile();
}
function annulerModeCarte(){
  _modeCarte = null;
  const b = document.getElementById('bandeauMode');
  if(b) b.remove();
  majBarrePile();
}
function toucherModeCarte(c){
  const m = _modeCarte;
  if(!m || !c) return false;
  if(!m.multiple) annulerModeCarte();
  m.toucher(c);
  return true;
}
function armerDeplacement(){
  const l = _sel.size ? piecesADeplacer() : (_pileSel ? pile(_pileSel) : []);
  if(!l.length) return;
  armerModeCarte({texte: `➡️ ${l.length > 1 ? l.length + ' pions' : l[0].nom} — touchez la destination`,
    toucher: c => deplacerAuDoigt(l.filter(p => !p.deleted), c.x, c.y)});
}
function armerPose(x){
  armerModeCarte({texte: `♟ ${x.n} — touchez la carte pour poser`, optionMultiple: true,
    multiple: _lirePref('aslweb.posesMultiples') === '1',
    toucher: c => poserDepuisPalette(x, c.x, c.y)});
  // En affichage tactile, la palette se ferme pour montrer la carte.
  if(_telLike()) vueMobile('carte');
}

// ── Verrou des pions : un glissé déplace toujours la carte, jamais une
// pile ; on déplace alors par « Déplacer… ».
// Le verrou n'a plus de bouton (masqué en affichage tactile) : on part
// toujours déverrouillé, faute de quoi il resterait pris.
let _verrouPions = false;
function basculerVerrou(){
  _verrouPions = !_verrouPions;
  try { localStorage.setItem('aslweb.verrouPions', _verrouPions ? '1' : '0'); } catch(e){}
  chatLog(_verrouPions ? '🔒 Pions verrouillés : un glissé déplace la carte' : '🔓 Pions déverrouillés');
}

// Au doigt, un appui hors de tout pion prend la pile la plus proche si son
// centre est à moins de 30 px à l'écran : après « Ajuster », un pion de
// téléphone ne fait que quelques pixels.
function pileProche(cx, cy){
  const c = clientVersCarte(cx, cy, null, true);
  if(!c) return null;
  let best = null, bd = 30;
  for(const [k, l] of indexerPiles()){
    const vus = l.filter(p => selectionnable(p) && COUCHES_SEL[p.couche || 'unite']);
    if(!vus.length) continue;
    const d = Math.hypot(l[0].x - c.x, l[0].y - c.y) * SC * zoom;
    if(d < bd){ bd = d; best = k; }
  }
  return best;
}

// Appui (doigt) sur une pile : elle est sélectionnée, prête à être
// déplacée (un glissé depuis elle la déplace aussitôt), et la barre de
// pile paraît. Seul
// un double appui ouvre l'éventail, même pour un pion seul : c'est là que
// sont le choix d'un pion de la pile et le menu de ses commandes.
let _dernierAppui = {k: null, t: 0};
function appuiSurPile(k){
  const double = _dernierAppui.k === k && Date.now() - _dernierAppui.t < 350;
  _dernierAppui = {k, t: Date.now()};
  if(!(_sel.size && pileEntiereSel(k)) || !_eventail || _eventail.k !== k) selectionnerPile(k);
  if(double && pile(k).some(selectionnable)) ouvrirEventail(k);
  else majBarrePile();
}

