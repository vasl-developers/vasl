// ═══ CVP — LE CASUALTIES BIN ═══
// Dans VASL, AlliedCVP et AxisCVP sont des propriétés globales de la
// fenêtre Casualties. Un pion envoyé au bin les incrémente (touche de
// déplacement de la carte, 77,715) ; rien ne les décrémente quand il en
// sort : seuls les boutons « Allied CVP » / « Axis CVP » les recomptent
// (le marqueur remet le total à 0, chaque pion de la zone ajoute sa
// valeur : iCVP, gCVP, tCVP + tankmod, pCVP ; 0 pour un « ? »).
// Ici, le total est toujours ce recomptage, refait après chaque coup.
// VASL le reçoit comme il le transmet lui-même :
//   MutableProperty\t<nom>\t<ancien>\t<nouveau>\t<carte>   (log, réseau)
//   GlobalProperty\t;<nom>;<valeur>;<carte>                (sauvegarde)
const _globConnu = new Map();   // nom\u0001carte → valeur connue de VASL
let _cvpAffiche = '';
const _cleGlob = (nom, carte) => nom + '\u0001' + (carte || '');

// Zone de la carte « carte » qui contient (x, y) : Polygon.contains,
// règle pair-impair.
function _zoneDe(carte, x, y){
  if(!_bdd || !_bdd.zones) return null;
  for(const z of _bdd.zones.values()){
    if(z.carte !== carte || !z.poly) continue;
    let dedans = false;
    for(let i = 0, j = z.poly.length - 1; i < z.poly.length; j = i++){
      const [xi, yi] = z.poly[i], [xj, yj] = z.poly[j];
      if((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) dedans = !dedans;
    }
    if(dedans) return z.nom;
  }
  return null;
}

// Commande de propriété globale (GlobalProperty ou MutableProperty) :
// {nom, valeur, ancien, carte}, ou null.
function _lireGlob(c){
  if(c.startsWith('GlobalProperty\t')){
    const f = _seq(c, ';');
    return f[1] ? {nom: f[1], valeur: f[2] || '', carte: f[3] || ''} : null;
  }
  if(c.startsWith('MutableProperty\t')){
    const f = _seq(c.substring(16), '\t');
    return f[0] ? {nom: f[0], ancien: f[1] || '', valeur: f[2] || '', carte: f[3] || ''} : null;
  }
  return null;
}
function _appliquerGlob(c){
  const g = _lireGlob(c);
  if(!g) return false;
  _globConnu.set(_cleGlob(g.nom, g.carte), g.valeur);
  _globales[g.nom] = g.valeur;
  return true;
}

// Recomptage des compteurs, comme leurs boutons : la touche du bouton
// passe dans chaque pièce de la carte qui répond au filtre, le total
// partant de 0. Les propriétés globales n'en gardent rien : rend
// {prop: valeur}, ou null sans le module.
function _recompterCVP(){
  if(!_bdd || !_bdd.compteurs || !_bdd.compteurs.length) return null;
  const res = {};
  for(const c of _bdd.compteurs) res[c.prop] = _compter(c, pions);
  Object.assign(_globales, res);
  return res;
}
// Total du compteur c sur les pièces de « liste » (une seule : la valeur
// d'un pion), sans rien laisser dans les propriétés globales.
function _compter(c, liste){
  const avant = Object.assign({}, _globales);
  try {
    _globales[c.prop] = '0';
    for(const p of liste){
      if(!p.deleted || p.ailleurs !== c.carte) continue;
      const te = tePion(p);
      if(!te) continue;
      const S = decoderTraits(te.etat);
      const {cmds, T} = commandesDe(te.type);
      if(c.filtre && !evaluerCondition(c.filtre, proprietesPion(p, T, S))) continue;
      _passerTouche(p, T, S, cmds, c.touche);
    }
    return String(Number(_globales[c.prop]) || 0);
  } finally {
    for(const k of Object.keys(_globales)) if(!(k in avant)) delete _globales[k];
    Object.assign(_globales, avant);
  }
}

// Recompte, montre le total s'il a changé et, pour un coup joué ici
// (emettre), envoie à VASL les totaux qui diffèrent de ce qu'il en sait.
function _cvpAJour(emettre){
  const r = _recompterCVP();
  if(!r) return;
  const libelle = c => c.prop.replace(/([a-z])([A-Z])/g, '$1 $2');
  const ecarts = [];
  for(const c of _bdd.compteurs){
    const k = _cleGlob(c.prop, c.carte);
    const connu = _globConnu.has(k) ? _globConnu.get(k) : (c.initial || '0');
    if(r[c.prop] === connu) continue;
    if(emettre){
      _logCmd(`MutableProperty\t${c.prop}\t${connu}\t${r[c.prop]}\t${c.carte}`);
      _globConnu.set(k, r[c.prop]);
    } else ecarts.push(`${libelle(c)} ${connu}`);
  }
  const texte = _bdd.compteurs.map(c => `${libelle(c)} ${r[c.prop]}`).join(' · ');
  if(texte !== _cvpAffiche && (_cvpAffiche || _bdd.compteurs.some(c => r[c.prop] !== '0') || ecarts.length)){
    chatLog(`🎖️ ${texte}` + (ecarts.length ? ` (VASL : ${ecarts.join(' · ')}, corrigé au prochain coup)` : ''));
  }
  _cvpAffiche = texte;
  _dessinerBin();
  rafraichirActions(['bin']);
}

// Après chaque coup joué ici, une fois toutes ses commandes passées.
let _cvpPlanifie = false;
function _planifierCVP(){
  if(_cvpPlanifie) return;
  _cvpPlanifie = true;
  queueMicrotask(() => { _cvpPlanifie = false; _cvpAJour(true); });
}

// Partie ouverte : les valeurs que la sauvegarde donne aux propriétés
// globales, puis le recomptage (le module est lu s'il y a des pièces
// hors de la carte de jeu).
function lireGlobales(segs){
  _globConnu.clear(); _cvpAffiche = '';
  for(const k of Object.keys(_globales)) delete _globales[k];
  for(const s of segs) if(s.startsWith('GlobalProperty\t')) _appliquerGlob(s.replace(/\\+$/, ''));
  if(_bdd) _cvpAJour(false);
  else if(pions.some(p => p.ailleurs)) chargerBDD().catch(() => {});
}

// Sauvegarde : les compteurs recomptés à la place des valeurs lues ; un
// compteur absent du fichier n'y entre que s'il n'est pas nul.
function _segmentsGlob(sortie){
  const r = _recompterCVP();
  if(!r) return [];
  const vus = new Set();
  for(let i = 0; i < sortie.length; i++){
    const g = sortie[i].startsWith('GlobalProperty\t') && _lireGlob(sortie[i]);
    const c = g && _bdd.compteurs.find(x => x.prop === g.nom && x.carte === g.carte);
    if(!c) continue;
    vus.add(c);
    sortie[i] = `GlobalProperty\t;${c.prop};${r[c.prop]};${c.carte}`;
  }
  return _bdd.compteurs.filter(c => !vus.has(c) && r[c.prop] !== '0')
    .map(c => `GlobalProperty\t;${c.prop};${r[c.prop]};${c.carte}`);
}

// ── Le panneau Casualties Bin : onglet des tables QRDC, comme l'OBA.
// Une colonne par zone de la fenêtre Casualties de VASL, avec son total
// de CVP ; une ligne par pion, du sommet au fond, avec sa valeur. Dans
// VASL, chaque zone n'est qu'une pile, au centre de la zone.
//   · ↩ remet le pion sur la carte, à la place qu'il a quittée (sinon au
//     centre de la vue) ; un glissé de la ligne vers la carte l'y pose ;
//   · ⇄ ou un glissé vers l'autre colonne le fait changer de zone ;
//   · ✕ le retire du jeu.
// Chaque geste est un M/ (ou un -/) comme dans VASL ; Undo l'annule, et
// les CVP sont recomptés après lui.
const _ongletBin = () => _bdd && _bdd.qrdc && _bdd.qrdc.bin;
const _surOngletBin = () => { const o = _ongletBin(); return !!o && (_tablesSel.get(o.onglets) || 0) === o.rang; };
const binOuvert = () => _tablesOuvertes() && _surOngletBin();
const basculerBin = () => _basculerOnglet(_ongletBin, binOuvert, '.ongletBin', 'Casualties');

const _zonesBin = carte => [...(_bdd ? _bdd.zones.values() : [])].filter(z => z.carte === carte);
const _zoneDuPion = p => { const d = lirePiece(p); return _zoneDe(p.ailleurs, d.x, d.y); };
const _compteurDe = (carte, zone) => (_bdd.compteurs || []).find(c => c.carte === carte && c.filtre.includes('"' + zone + '"'));
// Pions du bin, du sommet au fond.
const _pionsBin = carte => pions.filter(p => p.deleted && p.ailleurs === carte).sort((a, b) => b.ordre - a.ordre);

function _dessinerBin(){
  for(const b of document.querySelectorAll('.ongletBin')) _rendreBin(b);
}

function _rendreBin(boite){
  boite.replaceChildren();
  const o = _ongletBin();
  if(!o) return;
  const zones = _zonesBin(o.carte);
  const tous = _pionsBin(o.carte);
  const colonnes = [];
  // Une pièce hors des zones (déplacée dans VASL) a sa colonne.
  const groupes = zones.map(z => ({z, nom: z.nom, liste: tous.filter(p => _zoneDuPion(p) === z.nom)}));
  const hors = tous.filter(p => !zones.some(z => z.nom === _zoneDuPion(p)));
  if(hors.length) groupes.push({z: null, nom: 'Hors zone', liste: hors});
  for(const g of groupes){
    const col = document.createElement('div');
    col.className = 'binZone';
    col.setAttribute('role', 'group');
    col.setAttribute('aria-label', g.nom);
    const tete = document.createElement('div');
    tete.className = 'binTete';
    const t = document.createElement('span');
    t.className = 'binTitre'; t.textContent = g.nom;
    tete.appendChild(t);
    const c = g.z && _compteurDe(o.carte, g.z.nom);
    if(c){
      const n = document.createElement('span');
      n.className = 'binTotal'; n.textContent = `${_globales[c.prop] || '0'} CVP`;
      tete.appendChild(n);
    }
    col.appendChild(tete);
    if(!g.liste.length){
      const v = document.createElement('div');
      v.className = 'binVide'; v.textContent = 'Aucune perte';
      col.appendChild(v);
    }
    for(const p of g.liste) col.appendChild(_ligneBin(p, c, zones, g.z, () => colonnes, boite));
    colonnes.push({col, z: g.z});
    boite.appendChild(col);
  }
}

function _ligneBin(p, c, zones, zone, colonnes, boite){
  const l = document.createElement('div');
  l.className = 'binLig';
  const nom = nomRapport(p);
  l.setAttribute('aria-label', nom);
  l.title = nom + ' — glisser vers la carte pour l\'y remettre, vers une autre zone pour l\'y passer';
  const im = document.createElement('div');
  im.className = 'binMini';
  const sz = p.sz || 48;
  const copie = p.el.cloneNode(true);
  copie.classList.remove('sel', 'deplie', 'coup');
  copie.removeAttribute('title');
  copie.style.cssText = `position:absolute;left:0;top:0;width:${sz}px;height:${sz}px;`
                      + `transform:scale(calc(${40 / sz} * max(1, var(--zinv, 1))));transform-origin:0 0;opacity:1`;
  im.appendChild(copie);
  const n = document.createElement('span');
  n.className = 'binNom'; n.textContent = nom;
  l.append(im, n);
  if(c){
    const v = document.createElement('span');
    v.className = 'binCvp'; v.textContent = _compter(c, [p]) + ' CVP';
    l.appendChild(v);
  }
  const autre = zones.length > 1 ? zones[(zones.indexOf(zone) + 1) % zones.length] : null;
  const boutons = [['↩', 'Remettre sur la carte', () => remettreDuBin(p)]];
  if(autre && zone) boutons.push(['⇄', `Passer en ${autre.nom}`, () => changerZoneBin(p, autre)]);
  boutons.push(['✕', 'Retirer du jeu', () => retirerDuBin(p)]);
  for(const [txt, titre, f] of boutons){
    const b = document.createElement('button');
    b.type = 'button'; b.textContent = txt; b.title = titre; b.setAttribute('aria-label', titre + ' : ' + nom);
    b.addEventListener('click', f);
    l.appendChild(b);
  }
  // Glissé : vers la carte, le pion y revient ; vers une autre colonne, il
  // change de zone. Au doigt, après un appui long (sinon la carte défile).
  const colonneSous = ev => colonnes().find(x => {
    const r = x.col.getBoundingClientRect();
    return ev.clientX >= r.left && ev.clientX <= r.right && ev.clientY >= r.top && ev.clientY <= r.bottom;
  });
  l.addEventListener('pointerdown', e => {
    if(e.button > 0 || e.target.closest('button')) return;
    const id = e.pointerId;
    glisser(e, p.face && p.face.src, sz * zoom, (cx, cy) => {
      const m = clientVersCarte(cx, cy);
      if(m) remettreDuBin(p, m.x, m.y);
    }, null, {
      appuiLong: 300,
      zone: boite,
      // Le doigt qui glisse une ligne posée sur la carte ne la fait plus défiler.
      debut: () => {
        _pointeursCarte.delete(id);
        const w = document.getElementById('mapWrap');
        try { w.releasePointerCapture(id); } catch(err){}
        w.classList.remove('pan');
      },
      dansZone: ev => { const x = colonneSous(ev); for(const y of colonnes()) y.col.classList.toggle('vise', y === x && y.z !== zone); },
      fin: () => { for(const y of colonnes()) y.col.classList.remove('vise'); },
      lacherZone: ev => { const x = colonneSous(ev); if(x && x.z && x.z !== zone) changerZoneBin(p, x.z); }
    });
  });
  return l;
}

// Place que le pion a quittée sur la carte de jeu : retenue à son départ,
// sinon celle que garde son trait SendToLocation (« carte;x;y »).
function _retourDe(p){
  if(p.retour) return p.retour;
  const te = tePion(p);
  if(!te) return null;
  const T = decoderTraits(te.type), S = decoderTraits(te.etat);
  for(let i = 0; i < T.length; i++){
    if(!/^sendto;/.test(T[i])) continue;
    const f = (S[i] || '').replace(/\\+$/, '').split(';');
    const x = parseInt(f[1], 10), y = parseInt(f[2], 10);
    if(Number.isFinite(x) && Number.isFinite(y)) return {x, y};
  }
  return null;
}

// Centre de la vue, ou des boards si la vue montre les tables.
function _placeAuCentre(){
  const r = document.getElementById('mapWrap').getBoundingClientRect();
  const c = clientVersCarte((r.left + r.right) / 2, (r.top + r.bottom) / 2, null, true);
  if(c && !surTables(c.x, c.y)) return c;
  const e = _empriseBoards;
  return e ? {x: (e.x + e.w / 2) / SC, y: (e.y + e.h / 2) / SC} : c;
}

// Le pion revient sur la carte de jeu, en (x, y) ou à sa place d'avant,
// comme un glissé de la fenêtre Casualties vers la carte dans VASL.
function remettreDuBin(p, x, y){
  if(!p.deleted || !p.ailleurs || refuser(p)) return;
  if(x === undefined){ const r = _retourDe(p) || _placeAuCentre(); if(!r) return; x = r.x; y = r.y; }
  if(surTables(x, y)){ chatLog('⛔ Pas de pion sur les tables QRDC : rien déplacé'); return; }
  _avantAction();
  const d = lirePiece(p), carte = p.ailleurs;
  const pos = positionDepot(x, y), k = pos.x + ',' + pos.y;
  const haut = Math.max(-1, ...pile(k).map(q => q.ordre));
  p.deleted = false; p.ailleurs = null; p.retour = null;
  p.x = pos.x; p.y = pos.y; p.ordre = haut + 1;
  document.getElementById('mapInner').appendChild(p.el);
  _majSeg(p);
  _logCmd(`M/${p.uid}/${_carteJeu}/${p.x}/${p.y}/${_dessous(p)}/${carte}/${d.x}/${d.y}/null/${joueur()}`);
  majApparence(p);
  marquerDeplace(p, true);
  executerTouche(p, TOUCHE_MOUVEMENT);
  ajusterDissimulation(k); rendrePile(k);
  if(selectionnable(p)) selectionner([p]);
  chatLog(`📥 ${nomRapport(p)} : ${carte} → ${pos.label || k}`);
}

// Le pion passe dans une autre zone du bin, au centre, comme son envoi.
function changerZoneBin(p, z){
  if(!p.deleted || p.ailleurs !== z.carte || refuser(p)) return;
  const te = tePion(p);
  if(!te) return;
  _avantAction();
  const d = lirePiece(p);
  fixerEtat(p, _majPosEtat(te.etat, z.carte, z.x, z.y));
  p.ordre = Math.max(-1, ..._pionsBin(z.carte).filter(q => q !== p && _zoneDuPion(q) === z.nom).map(q => q.ordre)) + 1;
  _logCmd(`M/${p.uid}/${z.carte}/${z.x}/${z.y}/null/${z.carte}/${d.x}/${d.y}/null/${joueur()}`);
  chatLog(`📤 ${nomRapport(p)} → ${z.nom}`);
}

// Le pion quitte le jeu (Delete dans la fenêtre Casualties).
function retirerDuBin(p){
  if(!p.deleted || !p.ailleurs || refuser(p)) return;
  _avantAction();
  p.ailleurs = null;
  _logCmd('-/' + p.uid);
  chatLog(`✖️ ${nomRapport(p)} retiré du jeu`);
}

