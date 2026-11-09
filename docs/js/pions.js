// ═══════════════════════════════════════════════════════════════
// PIONS SUR LA CARTE
// Chaque pion garde de quoi refaire son segment VASSAL complet : ses
// traits ne sont pas reconstructibles depuis l'affichage, et c'est ce
// segment, position mise à jour, que l'export réécrit. Le TYPE, commun à
// tous les pions d'un modèle, est au registre (TYPES, js/pieces.js) :
//   typeRef entrée du registre {id, type}, null pour un segment brut (_seg)
//   etat    ÉTAT courant, changé par fixerEtat seulement
//   seg     « +/uid/TYPE/ÉTAT », recomposé à la lecture (lecture seule)
//   x, y    position en pixels VASL natifs
//   ordre   rang dans la pile, du fond vers le sommet
// ═══════════════════════════════════════════════════════════════
let pions = [];
let _pileSel = null;                  // clé de la pile courante : celle du dernier pion sélectionné
let _source = null;                   // segments de l'état chargé
let _carteJeu = 'Main Map';           // nom VASSAL de la carte de jeu
let _annexes = new Map();
let _nomFichier = 'partie';
let _coucheParUid = new Map();        // uid → couche (@@n) de sa pile d'origine
let _pilesVassal = new Map();         // id de pile VASSAL → uids, du fond au sommet
let _pilesRetirees = new Map();       // piles retirées d'un seul -/, pour Undo
let _uidSeq = 0;

// Une pièce qui ne s'empile pas (trait Immobilized « R ») forme à elle
// seule sa pile : un Fire Lane posé sur des unités ne les rejoint pas.
const cle = p => p.seul ? '#' + p.uid : p.x + ',' + p.y;
const vivants = () => pions.filter(p => !p.deleted);
// Une pile, du fond vers le sommet. Simple filtre, toujours exact : seul
// le rendu complet (rendreToutes) passe par un index des piles.
function pile(k){
  return pions.filter(p => !p.deleted && cle(p) === k).sort((a, b) => a.ordre - b.ordre);
}

// Toutes les piles en une passe : clé → pions, du fond vers le sommet.
function indexerPiles(){
  const ix = new Map();
  for(const p of pions){
    if(p.deleted) continue;
    const k = cle(p);
    let l = ix.get(k);
    if(!l) ix.set(k, l = []);
    l.push(p);
  }
  for(const l of ix.values()) l.sort((a, b) => a.ordre - b.ordre);
  return ix;
}

// ═══ SÉLECTION — d'après KeyBuffer de VASSAL ═══
// La sélection est un ensemble de pions, pas une pile : une touche
// s'applique à chacun d'eux. _pileSel désigne la pile « courante », celle
// du dernier pion sélectionné ; tout le code qui le lit continue de marcher.
// Une pile dépliée (Stack.setExpanded) écarte ses pions : on peut alors en
// viser un seul.
let _sel = new Set();          // pions sélectionnés
let _depliees = new Set();     // clés des piles dépliées
// Ni un pion HIP de l'adversaire, ni une carte ou un panneau de VASL Tools,
// ni un overlay de l'extension VASL Overlays.
const selectionnable = p => !!p && !p.deleted && !p.cache && !p.fond && !p.affichageSeul;
const selDans = k => pile(k).filter(p => _sel.has(p));
const pileEntiereSel = k => { const s = pile(k).filter(selectionnable); return s.length > 0 && s.every(p => _sel.has(p)); };
// Fonctions appelées après chaque changement de sélection (panneau, éventail…).
const _apresSelection = [];

// Les pions retirés, partis ailleurs ou devenus invisibles quittent la sélection.
function purgerSelection(){
  for(const p of _sel) if(!selectionnable(p) || !pions.includes(p)) _sel.delete(p);
}

// mode : 'remplacer' | 'ajouter' | 'basculer' | 'retirer'
function selectionner(liste, mode = 'remplacer'){
  const avant = new Set(_sel), ancienne = _pileSel;
  liste = liste.filter(selectionnable);
  if(mode === 'remplacer') _sel = new Set(liste);
  else for(const p of liste){
    if(mode === 'ajouter') _sel.add(p);
    else if(mode === 'retirer' || (mode === 'basculer' && _sel.has(p))) _sel.delete(p);
    else _sel.add(p);
  }
  purgerSelection();
  const dernier = [...liste].reverse().find(p => _sel.has(p)) || [..._sel].pop();
  _pileSel = dernier ? cle(dernier) : null;
  const touchees = new Set([...avant, ..._sel].map(cle));
  if(ancienne) touchees.add(ancienne);
  if(_pileSel) touchees.add(_pileSel);
  touchees.forEach(k => rendrePile(k));
  afficherPile();
  for(const f of _apresSelection) f();
}

// Pions rangés par pile (de haut en bas, puis de gauche à droite sur la
// carte) puis du fond vers le sommet : l'ordre de leurs M/ garde ainsi
// l'ordre relatif de chaque pile.
const trierParPile = l => l.slice().sort((a, b) => a.y - b.y || a.x - b.x || a.ordre - b.ordre);

// Ce que déplace un glissé de la sélection. Une pile repliée sélectionnée
// en entier part tout entière, sauf les pions HIP de l'adversaire : comme
// chez VASSAL (canPiecesMerge), ils ne sont pas dans ma pile ; ailleurs,
// seuls les pions sélectionnés.
function piecesADeplacer(){
  const cles = [...new Set(trierParPile([..._sel]).map(cle))];
  const out = [];
  for(const k of cles){
    if(pileEntiereSel(k) && !_depliees.has(k)) out.push(...pile(k).filter(p => !p.cache));
    else out.push(...selDans(k));
  }
  return out;
}

// Ce qu'emporte un pion tiré du panneau ou de l'éventail : la sélection
// s'il en fait partie et qu'elle a été choisie pion par pion ; si elle
// n'est que sa pile entière (le simple clic sur la pile), lui seul, comme
// on tirait un pion de la liste.
// Pion qui représente un groupe en mouvement dans le fantôme du glissé :
// le plus haut de ceux qui partent de la pile de p (ou, à défaut, du
// groupe), et non le sommet de la pile, qui peut rester sur place.
function sommetDeplace(l, p){
  const k = cle(p), dans = l.filter(q => cle(q) === k), s = dans.length ? dans : l;
  return s.length ? s.reduce((a, b) => b.ordre > a.ordre ? b : a) : p;
}

// Au doigt (doigt vrai), un pion sélectionné emporte toujours toute la
// sélection, pile entière comprise : pour n'en tirer qu'un, on le touche
// d'abord (il reste seul sélectionné) ou on décoche les autres.
function aEmporter(p, doigt){
  const k = cle(p);
  const seulementSaPile = !doigt && pileEntiereSel(k) && selDans(k).length === _sel.size;
  return _sel.has(p) && _sel.size > 1 && !seulementSaPile ? piecesADeplacer() : [p];
}

function deplier(k, oui){
  const avant = _depliees.has(k);
  if(oui && pile(k).filter(p => !p.cache).length > 1) _depliees.add(k); else _depliees.delete(k);
  if(avant !== _depliees.has(k)) rendrePile(k);
}
function replierTout(){
  const l = [..._depliees];
  _depliees.clear();
  l.forEach(k => rendrePile(k));
  return l.length > 0;
}
function nouvelUid(){ return String(Date.now()) + String(++_uidSeq); }

function _majPosEtat(etat, carte, x, y){
  const t = etat.lastIndexOf('\t');
  const f = etat.substring(t + 1).split(';');
  if(f.length < 3) return etat;
  f[0] = carte; f[1] = String(x); f[2] = String(y);
  return etat.substring(0, t + 1) + f.join(';');
}

// Réécrit la position dans le segment du pion.
function _majSeg(p){
  const te = tePion(p);
  if(!te) return;
  fixerEtat(p, _majPosEtat(te.etat, _carteJeu, p.x, p.y));
}

function creerPion(seg, ordre){
  const d = lirePiece(seg);
  const el = document.createElement('div');
  el.className = 'pion';
  const face = document.createElement('img');
  face.className = 'face';
  el.appendChild(face);
  // p.seg se lit sur PION : le TYPE est au registre (TYPES), le pion ne
  // garde que son ÉTAT.
  const p = Object.assign(Object.create(PION), {id: pions.length, uid: d.uid, x: d.x, y: d.y, ordre, nom: d.nom,
             el, face, sz: Math.round(48 * SC), deleted: false,
             seul: !!(d.immob && !d.immob.empile)});
  _attacherSeg(p, seg);
  // Sélection réservée à Maj+clic (ou impossible) : un clic simple la
  // traverse vers les pièces de dessous, comme dans VASSAL.
  if(d.immob && (d.immob.maj || d.immob.jamais)) el.classList.add(d.immob.jamais ? 'immob-jamais' : 'immob');
  // Vues de HidePiecesButton : ce qui reste visible quand les pions sont masqués.
  if(p.seul) el.classList.add('nonEmpile');
  pions.push(p);
  el.addEventListener('pointerdown', e => _pionPointerDown(e, p));
  el.addEventListener('contextmenu', e => {
    e.preventDefault(); e.stopPropagation();
    // Au doigt, le menu passe par l'éventail (double appui sur la pile) ;
    // Android enverrait aussi ce contextmenu, iOS jamais.
    if(_dernierPointeur !== 'mouse') return;
    cacherSurvol();
    // KeyBufferer : le clic droit sur un pion non sélectionné le
    // sélectionne seul (Maj : en plus de la sélection).
    if(!_sel.has(p) && selectionnable(p)) selectionner([p], e.shiftKey ? 'ajouter' : 'remplacer');
    ouvrirMenu(p, e.clientX, e.clientY, 'mouse');
  });
  // Double-clic : l'éventail de la pile, comme le double appui au doigt
  // (il remplace le dépliage de StackExpander et l'onglet Pile du PC).
  el.addEventListener('dblclick', e => {
    e.preventDefault(); e.stopPropagation();
    // Au doigt, le double appui a son propre geste (appuiSurPile).
    if(_dernierPointeur !== 'mouse') return;
    const k = cle(p);
    if(_filArme || !pile(k).some(selectionnable)) return;
    if(!_sel.size || !pileEntiereSel(k)) selectionnerPile(k);
    ouvrirEventail(k);
  });
  el.addEventListener('pointerenter', e => { if(e.pointerType === 'mouse' && !e.buttons) survolEntre(p, e); });
  el.addEventListener('pointermove', e => { if(e.pointerType === 'mouse' && !e.buttons) survolEntre(p, e); });
  el.addEventListener('pointerleave', e => { if(e.pointerType === 'mouse') survolSort(); });
  document.getElementById('mapInner').appendChild(el);
  majApparence(p, d);
  return p;
}

// Pièce de l'extension VASL Tools : la carte et les panneaux sont dessinés
// en HTML à l'échelle de la carte, les outils restent invisibles. Une page
// HTML de label (VASL Information Tables) se dessine de même.
async function _majPieceVaslTools(p, d, gen){
  if(!d.vt){ await _majPieceFond(p, d, gen, await _vtElementPage(d.page), SC * SWING_PX); return; }
  if(d.vt.genre === 'outil'){
    p.el.style.display = 'none';
    p.cache = p.outil = true;
    return;
  }
  const e = d.vt.genre === 'carte' ? await _vtElementCarte(d.vt.champs)
    : d.vt.genre === 'bandeau' ? _vtElementBandeau(d.vt.champs) : _vtElementNotes(d.vt.champs);
  await _majPieceFond(p, d, gen, e, SC);
}

// Pose une carte, un panneau ou une page sur son pion, à l'échelle ech :
// la pièce passe au fond, sous les pions posés dans ses cases.
async function _majPieceFond(p, d, gen, e, ech){
  if(gen !== p.gen) return;
  p.face.removeAttribute('src');
  p.face.style.display = 'none';
  p.el.style.background = '';
  p.el.title = d.nom;
  p.el.querySelectorAll('.couche, .vtc, .vtn, .vtp').forEach(x => x.remove());
  e.style.position = 'absolute';
  e.style.left = e.style.top = '0';
  // Sans cela, le tableau se tasserait à la largeur du pion qui le porte.
  e.style.width = 'max-content';
  e.style.transformOrigin = '0 0';
  e.style.transform = `scale(${ech})`;
  p.el.appendChild(e);
  // Les logos fixent la largeur de leurs cases : on mesure une fois
  // qu'ils sont décodés.
  await Promise.all([...(e.shadowRoot || e).querySelectorAll('img')].map(i => i.decode().catch(() => {})));
  if(gen !== p.gen) return;
  p.fond = true;
  p.larg = Math.round(e.offsetWidth * ech);
  p.haut = Math.round(e.offsetHeight * ech);
  if(d.vt && (d.vt.genre === 'carte' || d.vt.genre === 'bandeau')) _vtCaler(p, e);
  _etendreCarte();
  p.sz = Math.max(p.larg, p.haut);
  rendrePile(cle(p));
}

// ── Show Info (TextInfo de VASL) : la bande s'affiche à 6 px du bord
// droit du pion, centrée en hauteur, en Dialog 11. Le texte suit les
// conventions de TextInfo.drawInfo : « , » vaut deux espaces, un jeton
// commençant par « r » est en rouge, « R » seul est cerclé (sans radio),
// « ^ » ouvre et ferme un exposant, « [red]…[/red] » colore en rouge.
function basculerInfo(p){
  p.infoVisible = !p.infoVisible;
  majApparence(p);
}
function _elementInfo(info, cote){
  const e = document.createElement('div');
  e.className = 'couche infoTxt';
  const taille = 11 * SC;
  e.style.cssText = `left:${cote + 6 * SC}px;top:${cote / 2}px;font-size:${taille}px;`
    + `height:${Math.round(taille * 1.8)}px;line-height:${Math.round(taille * 1.8) - 2}px;padding:0 ${7 * SC}px`;
  let haut = false, cible = e;
  for(const tok of info.split(/([\^,])/)){
    if(!tok) continue;
    if(tok === '^'){
      haut = !haut;
      if(haut){ cible = document.createElement('sup'); e.appendChild(cible); } else cible = e;
      continue;
    }
    if(tok === ','){ cible.appendChild(document.createTextNode('\u00a0\u00a0')); continue; }
    let t = tok, rouge = false;
    if(t[0] === 'r'){ t = t.substring(1); rouge = true; }
    if(t === 'R'){
      const r = document.createElement('span'); r.className = 'radio'; r.textContent = 'R';
      cible.appendChild(r); continue;
    }
    // Balises [red]…[/red] à l'intérieur d'un jeton.
    for(const [j, bout] of t.split(/\[\/?red\]/).entries()){
      if(!bout) continue;
      const sp = document.createElement('span');
      if(rouge || (j % 2 === 1 && /\[red\]/.test(t))) sp.className = 'rouge';
      sp.textContent = bout;
      cible.appendChild(sp);
    }
  }
  return e;
}

// Face, surimpressions et orientation, d'après le segment courant.
async function majApparence(p, d){
  // Deux mises à jour peuvent se croiser (création, puis arrivée de la
  // base) : seule la plus récente pose ses surimpressions.
  const gen = p.gen = (p.gen || 0) + 1;
  d = d || lirePiece(p);
  p.nom = d.nom;
  // Comme Concealable de VASL : l'adversaire ne voit que le « ? », le
  // joueur qui a dissimulé l'unité la voit, marquée d'un petit « ? ».
  // L'administrateur voit tout.
  const voile = d.masque && !voitMasque(d.masqueProprio) ? d.masque : null;
  p.el.title = voile ? T('Pion dissimulé') : d.nom + (d.masque ? ' (?)' : '');
  p.proprietaire = d.proprietaire;
  p.masqueProprio = d.masqueProprio;
  p.hipProprio = d.hipProprio;
  p.compteurProprio = d.compteurProprio;
  // HIP : invisible de l'adversaire ; son joueur et l'administrateur le
  // voient en transparence, comme Hideable de VASSAL.
  const voitHip = d.hip && voitMasque(d.hipProprio);
  p.el.style.display = (d.hip && !voitHip) || p.ovrExtPose ? 'none' : '';
  p.el.style.opacity = voitHip ? '.55' : '';
  p.cache = d.hip && !voitHip;
  // Le « ? » qui coiffe une pile : son propriétaire et l'administrateur
  // le voient en transparence, pour distinguer leurs unités dessous ;
  // l'adversaire, lui, ne voit que le « ? » opaque.
  p.el.classList.toggle('qTransparent', !!d.compteurProprio
    && (_admin || d.compteurProprio === idJoueur()));
  p.couche = coucheDe(d);
  // Pièce de l'extension VASL Overlays (image « ovr… ») : jamais
  // sélectionnable, même case « Overlays » cochée ; l'overlay du dépôt
  // VASL est posé à sa place dans le board (majOverlaysExt).
  p.affichageSeul = /^ovr/i.test(d.img) && !/^(DO|DD)\//.test(d.img);
  if(p.affichageSeul) planifierOverlaysExt();
  if(d.vt || d.page){ await _majPieceVaslTools(p, d, gen); return; }
  const imgFace = voile || d.img;
  const url = voile === '?' ? _pionInconnu() : await chargerImage(imgFace);
  if(gen !== p.gen) return;
  if(url){
    p.face.src = url;
    await p.face.decode().catch(() => {});
    // Taille propre au pion : 48 px pour un 1/2", 60 px pour un 5/8".
    // Une image qui n'est pas carrée — le Fire Lane en surimpression,
    // 85×76 pour un hexagone — garde ses proportions, centrée dans le
    // carré de son plus grand côté : étirée, ses bras ne tombaient plus
    // sur les côtés d'hexagone.
    const {w: nw, h: nh} = await _tailleImage(url);
    if(gen !== p.gen) return;
    if(nw) p.sz = Math.round(Math.max(nw, nh) * SC);
    p.face.style.cssText = '';
    if(nw && nw !== nh){
      const w = nw * SC, h = nh * SC;
      p.face.style.cssText = `width:${w}px;height:${h}px;left:${(p.sz - w) / 2}px;top:${(p.sz - h) / 2}px`;
    }
    if(d.faceCouche && !voile){
      // Pion fait de couches : sa taille est celle de la plus grande, et
      // chacune garde la sienne, centrée — comme Embellishment.draw().
      let cote = Math.max(nw, nh);
      for(const c of d.couches){
        if(!c.actif) continue;
        const u = await chargerImage(_imgCouche(c));
        if(!u) continue;
        const o = new Image(); o.src = u;
        await o.decode().catch(() => {});
        cote = Math.max(cote, o.naturalWidth || 0, o.naturalHeight || 0);
      }
      if(gen !== p.gen) return;
      if(cote > Math.max(nw, nh)){
        p.sz = Math.round(cote * SC);
        const w = nw * SC, h = nh * SC;
        p.face.style.cssText = `width:${w}px;height:${h}px;`
          + `left:${(p.sz - w) / 2}px;top:${(p.sz - h) / 2}px`;
      }
    }
  } else {
    p.face.removeAttribute('src');
    p.el.style.background = '#b81414';
    p.el.title = `${d.nom} — image introuvable : ${imgFace || '?'}`;
  }
  p.face.style.transform = _dansRotation(d.rotations, Infinity) ? _rotCSS(d.rot) : '';
  // Tout ce qui est intérieur à un trait rotate tourne avec la face,
  // autour du centre du pion (FreeRotator.draw).
  const orienter = (o, i, gauche, haut) => {
    if(!_dansRotation(d.rotations, i)) return;
    o.style.transformOrigin = gauche === undefined ? '50% 50%' : `${p.sz/2 - gauche}px ${p.sz/2 - haut}px`;
    o.style.transform = _rotCSS(_angleInterieur(d.rotations, i));
  };
  // Les couches de marqueur (ML/, MS/…) se superposent au visage ; une
  // image de nationalité est une autre face du pion, pas une surimpression.
  const couches = voile ? [] : await imagesCouches(d, SC, p.sz);
  if(d.masque && !voile && d.masqueNation){
    const u = await chargerImage(`${d.masqueNation}/${d.masqueNation}qmarkme.svg`);
    if(u){
      const o = new Image();
      o.className = 'couche'; o.src = u;
      o.style.cssText = 'left:0;top:0;width:100%;height:100%';
      orienter(o, d.iMasque);
      couches.push(o);
    }
  }
  if(!voile && d.tourelle){
    const u = await chargerImage(d.tourelle.img);
    if(u){
      const o = new Image();
      o.className = 'couche'; o.src = u;
      await o.decode().catch(() => {});
      // Taille propre de l'image, pas celle du pion : elle déborde.
      const w = (o.naturalWidth || 68) * SC, h = (o.naturalHeight || 68) * SC;
      const gauche = p.sz / 2 + d.tourelle.dx * SC - w / 2, haut = p.sz / 2 + d.tourelle.dy * SC - h / 2;
      o.style.cssText = `width:${w}px;height:${h}px;left:${gauche}px;top:${haut}px`;
      orienter(o, d.tourelle.i, gauche, haut);
      couches.push(o);
    }
  }
  if(!voile && d.deplace){
    const u = await chargerImage(d.deplace.img);
    if(u){
      const o = new Image();
      o.className = 'couche'; o.src = u;
      await o.decode().catch(() => {});
      const gauche = d.deplace.droite ? p.sz : d.deplace.dx * SC;
      const haut = d.deplace.droite ? 0 : d.deplace.dy * SC;
      o.style.cssText = `width:${o.naturalWidth * SC}px;height:${o.naturalHeight * SC}px;`
        + `top:${haut}px;left:${gauche}px`;
      orienter(o, d.deplace.i, gauche, haut);
      couches.push(o);
    }
  }
  if(!voile && p.infoVisible && d.info) couches.push(_elementInfo(d.info, p.sz));
  if(!voile) for(const t of d.etiquettes){
    const e = document.createElement('div');
    e.className = 'couche etiq';
    remplirLabel(e, t);
    const famille = /serif/i.test(t.police) && !/sans/i.test(t.police) ? 'Georgia,serif'
                  : /mono/i.test(t.police) ? 'monospace' : 'Arial,Helvetica,sans-serif';
    e.style.font = `${t.style & 2 ? 'italic ' : ''}${t.style & 1 ? 'bold ' : ''}`
                 + `${Math.round(t.taille * SC)}px/1.15 ${famille}`;
    e.style.color = t.couleur;
    if(t.fond) e.style.background = t.fond;
    e._label = t;
    e._rotPion = _dansRotation(d.rotations, t.i) ? _angleInterieur(d.rotations, t.i) : null;
    couches.push(e);
  }
  if(gen !== p.gen) return;
  p.el.querySelectorAll('.couche').forEach(e => e.remove());
  p.face.before(...couches.filter(o => o.dessous));
  p.el.append(...couches.filter(o => !o.dessous));
  for(const e of p.el.querySelectorAll('.etiq')) placerLabel(e, p.sz);
  rendrePile(cle(p));
  // La liste de pile copie le pion affiché : elle suit son image.
  if(_pileSel === cle(p)) afficherPile();
}

// ── Surimpressions d'un pion, prêtes à poser dans un cadre de côté
// `cote` dessiné à l'échelle `k`. Les couches de marqueur (ML/, MS/…)
// se superposent au visage ; une image de nationalité est une autre
// face du pion, pas une surimpression.
// Comme Embellishment.draw() : chaque image garde sa taille propre, à la
// même échelle que le pion, centrée sur lui puis décalée de (dx, dy).
// L'étirer à la taille du pion grossissait d'autant le chiffre de SAN du
// sniper (18 px dans un pion de 60).
async function imagesCouches(d, k, cote){
  const out = [];
  // Les couches vont de la plus externe à la plus interne. Quand le visage
  // est lui-même une couche, celles qui la suivent sont DESSOUS : le fond
  // coloré d'un Wire, d'un Trench ou d'un Tetrahedron (_brownd58.svg…)
  // est opaque et recouvrait le GIF transparent du dessin.
  const iFace = d.couches.findIndex(c => c.actif && c.images.length && _imgCouche(c) === d.img);
  for(let i = 0; i < d.couches.length; i++){
    const c = d.couches[i];
    if(!c.actif) continue;
    const im = _imgCouche(c);
    // Une image de nationalité posée en (0, 0) est une autre face du pion
    // (brisé, HS…). Décalée, c'est un marqueur : le CE (xxCEnt) ou le BU
    // (xxBUnt) d'un véhicule sans tourelle, les tourelles d'un xxCT2tur.
    if(!im || !im.trim() || im === d.img || (/^[a-z]{2}\//.test(im) && !c.dx && !c.dy) || /^ovr/i.test(im)) continue;
    const u = await chargerImage(im);
    if(!u) continue;
    const o = new Image();
    o.className = 'couche';
    o.src = u;
    await o.decode().catch(() => {});
    const w = o.naturalWidth * k, h = o.naturalHeight * k;
    if(w && h){
      o.style.width = w + 'px'; o.style.height = h + 'px';
      o.style.left = (cote/2 + c.dx*k - w/2) + 'px';
      o.style.top  = (cote/2 + c.dy*k - h/2) + 'px';
    }
    // Couche intérieure à un trait rotate : elle tourne autour du centre
    // du pion, décalage compris.
    if(_dansRotation(d.rotations, c.i)){
      o.style.transformOrigin = (w && h) ? `${w/2 - c.dx*k}px ${h/2 - c.dy*k}px` : '50% 50%';
      o.style.transform = _rotCSS(_angleInterieur(d.rotations, c.i));
    }
    o.dessous = iFace >= 0 && i > iFace;
    out.push(o);
  }
  // Ordre de dessin de VASSAL : de la couche la plus interne à la plus
  // externe. Le compteur Firelane pose sa face opaque (FL-1…) sous le
  // triangle « Alt Grain » : dans l'ordre inverse, le triangle disparaissait.
  return out.reverse();
}

// Vignette composée — fond et couches — d'un pion de la palette. Bien
// des pions n'ont pour image de base qu'un fond vierge (« _white12 ») :
// la Fire Lane, plusieurs First Fire… Leur dessin est tout entier dans
// les couches, et une vignette réduite à l'image de base restait blanche.
async function composerVignette(boite, d, cote){
  const u = await chargerImage(d.img);
  if(!u) return;
  const face = new Image();
  face.src = u;
  await face.decode().catch(() => {});
  const k = cote / (face.naturalWidth || 48);
  face.style.cssText = 'left:0;top:0;width:100%;height:100%';
  const couches = await imagesCouches(d, k, cote);
  // La plus interne d'abord : c'est l'ordre de dessin de VASSAL.
  boite.replaceChildren(...couches.filter(o => o.dessous), face,
                        ...couches.filter(o => !o.dessous));
}

// Taille propre d'une image, lue sur une copie hors de la page : sous
// WebKit (Safari, et Firefox sur iPhone et iPad), le naturalWidth d'un
// SVG affiché est sa taille à l'écran. Le pion, mesuré sur sa propre
// face, grossissait de SC à chaque rendu.
const _taillesImages = new Map();     // URL → Promise<{w, h}>
function _tailleImage(url){
  if(!_taillesImages.has(url)) _taillesImages.set(url, (async () => {
    const o = new Image();
    o.src = url;
    await o.decode().catch(() => {});
    const w = o.naturalWidth || 0;
    if(!w) _taillesImages.delete(url);     // image illisible : on réessaiera
    return {w, h: o.naturalHeight || w};
  })());
  return _taillesImages.get(url);
}

// « ? » neutre, pour une pièce dissimulée dont le trait ne nomme pas
// d'image de masque : on ne montre jamais la face cachée.
function _pionInconnu(){
  return 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" '
    + 'width="48" height="48"><rect width="48" height="48" rx="3" fill="#555" stroke="#111"/>'
    + '<text x="24" y="33" font-family="Arial" font-size="26" font-weight="bold" '
    + 'fill="#fff" text-anchor="middle">?</text></svg>');
}

// Position d'un label, comme Labeler.getLabelPosition() : point de départ
// au bord du pion désigné (posV, posH), décalage (le vertical compte vers
// le bas), puis justification selon la taille du texte. La rotation se
// fait autour du centre du pion.
function placerLabel(e, sz, k = SC){
  const t = e._label;
  const w = e.offsetWidth, h = e.offsetHeight;
  let x = t.decH * k, y = t.decV * k;
  if(t.posV === 't') y -= sz/2; else if(t.posV === 'b') y += sz/2;
  if(t.posH === 'l') x -= sz/2; else if(t.posH === 'r') x += sz/2;
  if(t.justV === 'b') y -= h; else if(t.justV === 'c') y -= h/2;
  if(t.justH === 'c') x -= w/2; else if(t.justH === 'r') x -= w;
  e.style.left = (sz/2 + x) + 'px';
  e.style.top = (sz/2 + y) + 'px';
  // Rotation propre du label, plus celle d'un trait rotate extérieur :
  // toutes deux autour du centre du pion. Dans un trait rotate, le label
  // suit aussi la vue tournée.
  const dans = e._rotPion !== null && e._rotPion !== undefined;
  const a = (t.rotation || 0) + (e._rotPion || 0);
  if(a || dans){
    e.style.transformOrigin = `${-x}px ${-y}px`;
    e.style.transform = dans ? _rotCSS(a) : `rotate(${a}deg)`;
  }
}

// ═══ EMPILEMENT — StackMetrics de la carte de jeu ═══
// Écarts en pixels VASL natifs, lus dans le buildFile (unexSep : pile
// repliée, exSep : pile dépliée). Tant que la base n'est pas chargée, on
// garde l'ancien décalage de 4 px à l'écran, et les écarts dépliés et les
// touches par défaut de StackMetrics.
const METRIQUES_DEFAUT = {unexSepX: 4 / SC, unexSepY: 4 / SC, exSepX: 6, exSepY: 18, disabled: false,
  top: _touche(38, 0), bottom: _touche(40, 0), up: _touche(39, 0), down: _touche(37, 0)};
const metriques = () => (_bdd && _bdd.stackMetrics) || METRIQUES_DEFAUT;

// Rectangles des pions visibles d'une pile, à l'écran (pixels de
// #mapInner), comme StackMetrics.getContents puis nextPosition : chaque
// pion se cale sur le bord de celui de dessous, décalé de dx vers la
// droite et de dy vers le HAUT. Les pions de même taille sont donc
// simplement décalés de (dx, −dy).
// Vue tournée : la pile s'étale de même à l'écran ; les rectangles sont
// calculés dans le repère de l'écran, autour du centre du premier pion,
// puis ramenés dans celui de la carte.
// Vue tournée : un pion reste droit à l'écran ; un overlay de terrain suit
// la carte, comme une carte de scénario ou un panneau, dont les cases
// portent des pions qui doivent y rester.
const _droitALEcran = p => p.couche !== 'overlay' && p.couche !== 'carte';
function rectsPile(s, deplie){
  const m = metriques();
  const dx = m.disabled ? 0 : (deplie ? m.exSepX : m.unexSepX) * SC;
  const dy = m.disabled ? 0 : (deplie ? m.exSepY : m.unexSepY) * SC;
  const quart = _rotVue % 180 !== 0;
  const out = [];
  let prec = null;
  for(const p of s){
    const w = p.larg || p.sz, h = p.haut || p.sz;
    // Taille du pion vu à l'écran : un quart de tour échange les côtés
    // d'un pion resté droit.
    const W = quart && _droitALEcran(p) ? h : w, H = quart && _droitALEcran(p) ? w : h;
    let x = -W/2, y = -H/2;
    if(prec){
      x = dx > 0 ? prec.x + dx : dx < 0 ? prec.x + prec.w - W + dx : prec.x + prec.w/2 - W/2;
      y = dy > 0 ? prec.y + prec.h - H - dy : dy < 0 ? prec.y - dy : prec.y + prec.h/2 - H/2;
    }
    prec = {x, y, w: W, h: H};
    const c = _ecartVersCarte(x + W/2, y + H/2);
    out.push({x: s[0].x * SC + c.x - w/2, y: s[0].y * SC + c.y - h/2, w, h});
  }
  return out;
}

function rendrePile(k, tous){
  planifierOverlaysExt();
  const s = (tous || pile(k)).filter(p => !p.cache);
  const deplie = _depliees.has(k) && s.length > 1;
  if(!deplie) _depliees.delete(k);
  const rects = rectsPile(s, deplie);
  s.forEach((p, i) => {
    // Une carte ou un panneau n'est pas carré, et passe sous les pions
    // posés dans ses cases.
    const r = rects[i];
    p.el.style.width = r.w + 'px';
    p.el.style.height = r.h + 'px';
    p.el.style.left = (r.x + (p.decX || 0) * SC) + 'px';
    p.el.style.top  = (r.y + (p.decY || 0) * SC) + 'px';
    // Une pile dépliée passe au-dessus de ses voisines. Une pièce qui ne
    // s'empile pas (un tunnel) se dessine au-dessus de la pile de son
    // hexagone, comme la pièce posée en dernier dans VASSAL.
    // Une carte, un panneau ou une page reste au fond, même s'il ne
    // s'empile pas : les pions posés dans ses cases passent par-dessus.
    // Un overlay déplaçable se pose sur le terrain, au-dessus des cartes et
    // sous tous les pions (Fire Lane compris) ; entre overlays, le dernier
    // posé est dessus.
    p.el.style.zIndex = p.fond ? 2 + i : p.couche === 'overlay' ? 6 : p.seul ? 10 + _hauteurSous(p) : (deplie ? 5000 : 10) + i;
    p.el.classList.toggle('sel', _sel.has(p));
    p.el.classList.toggle('deplie', deplie);
    p.el.classList.toggle('verrou', p.affichageSeul || !COUCHES_SEL[p.couche || 'unite']);
    p.el.classList.toggle('terrain', !_droitALEcran(p));
  });
  _decorPile(k, s, rects, deplie);
  if(_reperes.brises || _reperes.sniper) dessinerReperes();
  if(_liens.size) _planifierLiens();
  // Les pièces non empilées posées au même endroit restent au-dessus.
  if(s.length && !s[0].seul)
    for(const q of vivants()) if(q.seul && !q.fond && !q.cache && q.couche !== 'overlay' && q.x === s[0].x && q.y === s[0].y)
      q.el.style.zIndex = 10 + _hauteurSous(q);
}
// Hauteur de la pile ordinaire sur laquelle une pièce non empilée est posée.
const _hauteurSous = q => pile(q.x + ',' + q.y).filter(x => !x.cache).length;

// Rendu complet : une passe pour grouper, une pour placer.
function rendreToutes(){
  const ix = indexerPiles();
  purgerSelection();
  for(const [k, s] of ix) rendrePile(k, s);
  for(const k of [..._decors.keys()]) if(!ix.has(k)) _decorPile(k, [], [], false);
  for(const k of [..._depliees]) if(!ix.has(k)) _depliees.delete(k);
}

// ── Décor d'une pile, hors des données du jeu : la trame d'une pile
// dépliée, et une pastille « 2/5 » quand la sélection n'en prend qu'une
// partie (ou, en option, l'effectif d'une pile vue de loin). La pastille
// garde sa taille à l'écran, quel que soit le zoom.
const _decors = new Map();       // clé → {fond, pastille}
// _lirePref : js/outils.js
// Plus de commande pour l'afficher (masquée partout) : on part toujours sans.
let _effectifPiles = false;
const _vueDeLoin = () => zoom * 48 * SC < 30;

function _decorPile(k, s, rects, deplie){
  let d = _decors.get(k);
  const vus = s.filter(selectionnable);
  const nSel = vus.filter(p => _sel.has(p)).length;
  const texte = nSel && nSel < vus.length ? `${nSel}/${vus.length}`
              : _effectifPiles && vus.length >= 3 && _vueDeLoin() ? String(vus.length) : '';
  if(!deplie && !texte){
    if(d){ if(d.fond) d.fond.remove(); if(d.pastille) d.pastille.remove(); _decors.delete(k); }
    return;
  }
  if(!d) _decors.set(k, d = {fond: null, pastille: null});
  const mi = document.getElementById('mapInner');
  const x0 = Math.min(...rects.map(r => r.x)), y0 = Math.min(...rects.map(r => r.y));
  const x1 = Math.max(...rects.map(r => r.x + r.w)), y1 = Math.max(...rects.map(r => r.y + r.h));
  if(deplie){
    if(!d.fond){ d.fond = document.createElement('div'); d.fond.className = 'fondPile'; mi.appendChild(d.fond); }
    Object.assign(d.fond.style, {left: (x0 - 4) + 'px', top: (y0 - 4) + 'px', width: (x1 - x0 + 8) + 'px', height: (y1 - y0 + 8) + 'px'});
  } else if(d.fond){ d.fond.remove(); d.fond = null; }
  if(texte){
    if(!d.pastille){ d.pastille = document.createElement('div'); d.pastille.className = 'pastille'; mi.appendChild(d.pastille); }
    d.pastille.textContent = texte;
    d.pastille.classList.toggle('partielle', !!nSel);
    // Au coin haut droit du pion du dessus, vu à l'écran.
    const haut = rects[rects.length - 1];
    const [W, H] = _rotVue % 180 && _droitALEcran(s[s.length - 1]) ? [haut.h, haut.w] : [haut.w, haut.h];
    const c = _ecartVersCarte(W/2, -H/2);
    d.pastille.style.left = (haut.x + haut.w/2 + c.x) + 'px';
    d.pastille.style.top = (haut.y + haut.h/2 + c.y) + 'px';
  } else if(d.pastille){ d.pastille.remove(); d.pastille = null; }
}
function _viderDecors(){
  for(const d of _decors.values()){ if(d.fond) d.fond.remove(); if(d.pastille) d.pastille.remove(); }
  _decors.clear();
}

// ── Couches de sélection.
// La carte de scénario et les panneaux de VASL Tools couvrent de grandes
// zones du terrain : un clic destiné à la carte les prenait, ou un pion
// voisin. Chaque famille peut donc être retirée de la sélection ; ses
// pièces restent affichées mais ne répondent plus au clic. Les overlays
// déplaçables font partie du terrain : en jeu, le clic les traverse.
const COUCHES_SEL = {unite: true, marqueur: true, carte: false, overlay: false};
try {
  const m = JSON.parse(localStorage.getItem('couchesSel') || 'null');
  if(m) for(const k in COUCHES_SEL) if(typeof m[k] === 'boolean') COUCHES_SEL[k] = m[k];
} catch(e){}

function coucheDe(d){
  if(d.vt || d.page) return 'carte';
  // Le « ? » de dissimulation voyage avec les unités qu'il couvre.
  if(d.masque || /qmark/i.test(d.img)) return 'unite';
  const dir = d.img.includes('/') ? d.img.split('/')[0] : '';
  // Images des fenêtres Draggable Overlays (DO) et Deluxe Draggable (DD),
  // et pièces de l'extension VASL Overlays (propriété « overlay », image
  // d'un overlay d'hexagones « ovr… »).
  if(/^(DO|DD)$/.test(dir) || d.overlay || /^ovr/i.test(d.img)) return 'overlay';
  return !dir || /^(MS|ML|MC|sh|tables|CIDs)$/.test(dir) ? 'marqueur' : 'unite';
}

function _brancherCouches(){
  document.querySelectorAll('#couches input').forEach(c => {
    c.checked = COUCHES_SEL[c.dataset.couche];
    c.addEventListener('change', () => {
      COUCHES_SEL[c.dataset.couche] = c.checked;
      try { localStorage.setItem('couchesSel', JSON.stringify(COUCHES_SEL)); } catch(e){}
      rendreToutes();
      rafraichirActions(['couche_' + c.dataset.couche]);
    });
  });
}

// reduire : repartir de la taille des boards, pour que la carte se resserre
// aussi (tables masquées, onglet plus petit).
function _etendreCarte(reduire){
  let w = _mapW, h = _mapH;
  if(reduire && _mapBase){ w = _mapBase.w; h = _mapBase.h; }
  for(const p of vivants()){
    // Une carte de scénario ou un panneau de notes déborde de son centre.
    w = Math.max(w, Math.ceil(p.x * SC + (p.larg || 0) / 2 + (p.decX || 0) * SC + 80));
    h = Math.max(h, Math.ceil(p.y * SC + (p.haut || 0) / 2 + (p.decY || 0) * SC + 80));
  }
  // Les tables QRDC, et autant de marge sous elles que d'écart au-dessus.
  const t = rectTables();
  if(t){
    w = Math.max(w, t.x + t.w + Math.round(ECART_TABLES * SC));
    h = Math.max(h, t.y + t.h + Math.round(ECART_TABLES * SC));
  }
  if(reduire ? w !== _mapW || h !== _mapH : w > _mapW || h > _mapH) _setMapSize(w, h);
  else if(_rotVue) _majScaler();    // tables sous la carte tournée
}

// ═══ IMPORT ═══
// Les piles VASSAL sont des pièces « stack » dont l'état liste les
// identifiants du fond vers le sommet : +/<uid>/stack/Main Map;x;y;id0;…;@@couche
function _parseStacks(segs){
  const rangs = new Map();
  _coucheParUid = new Map();
  _pilesVassal = new Map();
  _pilesRetirees = new Map();
  for(const seg of segs){
    if(!/^\+\/[^/]+\/stack\//.test(seg)) continue;
    const ch = seg.substring(seg.indexOf('/stack/') + 7).replace(/\\/g, '').split(';');
    const ids = ch.slice(3).filter(c => c && !c.startsWith('@@'));
    _pilesVassal.set(seg.split('/')[1], ids);
    const at = ch.slice(3).find(c => c.startsWith('@@'));
    const couche = at ? parseInt(at.substring(2), 10) : -1;
    ids.forEach((u, i) => { rangs.set(u, i); _coucheParUid.set(u, Number.isFinite(couche) ? couche : -1); });
  }
  return rangs;
}

// La carte de jeu porte le nom par défaut de VASSAL ; à défaut, la plus
// peuplée hors fenêtres auxiliaires (Casualties, Scenario Aid…).
function _carteDeJeu(infos){
  const tally = {};
  for(const d of infos) if(d.carte && d.carte !== 'null') tally[d.carte] = (tally[d.carte] || 0) + 1;
  const noms = Object.keys(tally);
  const std = ['Main Map', 'Carte principale'].find(n => tally[n]);
  if(std) return std;
  const AUX = /Casualties|Field Maintenance|Campaign Roster|Scenario Aid|Charts?|Reference|Dice|Roster|Aid Card/i;
  const jeu = noms.filter(n => !AUX.test(n));
  return (jeu.length ? jeu : noms).sort((a, b) => tally[b] - tally[a])[0] || 'Main Map';
}

function importerPions(texte, label, annexes){
  for(const p of pions) p.el.remove();
  pions = []; _pileSel = null; _sel = new Set(); _depliees = new Set(); _viderDecors(); afficherPile();
  _histo = []; majUndo();
  const segs = texte.split('\x1b');
  // L'état de la partie précède le premier « LOG » ; la suite, dans un
  // .vlog, ce sont les coups.
  let iLog = segs.findIndex(_estLog);
  if(iLog < 0) iLog = segs.length;
  _source = segs.slice(0, iLog).filter(s => !s.startsWith('UNDO'));
  _annexes = annexes || new Map();
  _demasquables = lireDemasquables(_source);
  _nomFichier = label.replace(/\.(vsav|vlog)$/i, '');

  const rangs = _parseStacks(_source);
  const candidats = [];
  _source.forEach((seg, i) => {
    if(!seg.startsWith('+/') || /^\+\/[^/]+\/stack\//.test(seg)) return;
    const d = lirePiece(seg);
    if(d.carte !== null) candidats.push({seg, d, i});
  });
  _carteJeu = _carteDeJeu(candidats.map(c => c.d));
  let n = 0;
  for(const c of candidats){
    // Autres fenêtres (Casualties…) : la pièce est gardée à l'écart, comme
    // une pièce envoyée ailleurs (les CVP la comptent), et réécrite telle
    // quelle.
    if(c.d.carte !== _carteJeu){
      const q = creerPion(c.seg.replace(/\\+$/, ''), 1000 + c.i);
      q.deleted = true; q.ailleurs = c.d.carte; q.el.remove();
      continue;
    }
    // Hors de toute pile : une pièce qui ne s'empile pas est dessinée
    // sous les piles (DefaultPieceCollection, couche 0), les autres dessus.
    // La contre-oblique finale échappe l'ESC qui suit le segment, elle
    // n'est pas à l'état : gardée, elle finirait les D/ de ce pion
    // (« …;OldMap;Main Map\/ancien ») et VASSAL, y voyant un « / »
    // échappé, collerait l'ancien état au nouveau.
    const q = creerPion(c.seg.replace(/\\+$/, ''), rangs.has(c.d.uid) ? rangs.get(c.d.uid) : 1000 + c.i);
    if(!rangs.has(c.d.uid) && neSEmpilePas(q)) q.ordre = -1000 + c.i;
    n++;
  }
  const caches = vivants().filter(p => p.cache && !p.outil).length;
  const outils = vivants().filter(p => p.outil).length;
  chatLog(`♟️ ${n} pion(s) sur « ${_carteJeu} »` + (caches ? ` dont ${caches} HIP, non affiché(s)` : '')
    + (outils ? ` ; ${outils} outil(s) VASL Tools masqué(s)` : ''));

  lireInfo(_source);
  lireLiens(_source);
  lireOBA(_source);
  lireGlobales(_source);
  // Un .vlog s'ouvre, comme dans VASSAL, sur l'état de départ : on le
  // parcourt ensuite coup par coup.
  preparerRelecture(segs.slice(iLog));
  _etendreCarte();
  rendreToutes();
  majBoutons();
  if(_etapes.length) chatLog(`🎬 .vlog : ${_etapes.length} coup(s) à relire — ▶ ou Page suivante pour avancer`);
  // Le décompte des MF / MP, au premier déplacement comme aux coups
  // relus, a besoin du terrain : la carte LOS se construit dès maintenant.
  if(typeof carteLOS === 'function') carteLOS();
}

// ═══ ANNULER — le bouton Undo de BasicLogger ═══
// Chaque coup joué, reçu ou relu entre dans l'historique (logOutput).
// Undo prend le dernier coup qui n'est pas encore annulé, en exécute
// l'inverse — getUndoCommand : les sous-commandes en ordre inverse,
// AddPiece ↔ RemovePiece, ChangePiece avec ses états échangés, MovePiece
// vers l'ancienne place — puis le consigne et l'envoie aux autres joueurs
// entre « UNDO\ttrue » et « UNDO\tfalse ». Ce coup-là ne s'annule pas.
// Un message ou un jet de dés n'a pas d'inverse : Undo passe par-dessus.
let _histo = [];          // {no, cmds, inv, annulable, annule, etape}
let _annulation = false;  // vrai pendant qu'Undo consigne son coup
let _etapeLog = false;    // vrai pendant qu'un coup relu est consigné

// Champs d'une commande « / » tels qu'écrits (guillemets et
// contre-obliques gardés), pour la réécrire dans l'autre sens. Comme
// SequenceEncoder.Decoder, seul un « / » précédé d'une contre-oblique
// est échappé ; les apostrophes ne protègent rien.
function _decoupeBrut(s){
  const out = []; let debut = 0;
  for(let i = 0; i < s.length; i++){
    if(s[i] === '/' && (i === 0 || s[i-1] !== '\\')){ out.push(s.substring(debut, i)); debut = i + 1; }
  }
  out.push(s.substring(debut));
  return out;
}

// RemovePiece.myUndoCommand : AddPiece de la pièce telle qu'elle était,
// plus le ChangePiece qui la remet dans sa pile. Nos piles n'ont pas
// d'identifiant VASSAL : un M/ sur la pièce qui était dessous (null :
// au fond) la remet à son rang, chez nous comme chez un client VASSAL.
function _remise(p, sous){
  const d = lirePiece(p);
  if(d.carte !== _carteJeu) return [p.seg];
  return [p.seg, `M/${p.uid}/${_carteJeu}/${d.x}/${d.y}/${sous || 'null'}/${_carteJeu}/${d.x}/${d.y}/null/${joueur()}`];
}

// Pièce encore en jeu juste sous p, d'après les rangs d'avant le retrait.
function _dessousRetire(p){
  if(neSEmpilePas(p)) return null;
  let best = null;
  for(const q of vivants())
    if(q !== p && q.x === p.x && q.y === p.y && q.ordre < p.ordre && !neSEmpilePas(q) && (!best || q.ordre > best.ordre)) best = q;
  return best ? best.uid : null;
}

// Commande(s) inverse(s), ou null si elle n'en a pas.
function _inverse(c){
  if(/^\+\/[^/]+\/stack\//.test(c)) return null;
  // LinkPiecesCommand ↔ UnlinkPiecesCommand.
  if(_CMD_LIEN.test(c)) return [c.startsWith('UN') ? c.substring(2) : 'UN' + c];
  // CreateModule ↔ RemoveModule, UpdateModule vers l'état d'avant.
  if(_cleOBA(c).startsWith('OBA/')) return _inverseOBA(c);
  // ChangePropertyCommand : la valeur d'avant.
  if(c.startsWith('MutableProperty\t')){
    const g = _lireGlob(c);
    return g ? [`MutableProperty\t${g.nom}\t${g.valeur}\t${g.ancien}\t${g.carte}`] : null;
  }
  if(c.startsWith('+/')) return ['-/' + c.split('/')[1]];
  if(c.startsWith('-/')){
    const uid = c.substring(2);
    const p = pions.filter(q => q.uid === uid).pop();
    if(p) return _remise(p, _dessousRetire(p));
    // Une pile entière : ses pièces reviennent du fond vers le sommet.
    const ids = _pilesRetirees.get(uid);
    if(!ids) return null;
    const out = [];
    let sous = null;
    for(const u of ids){
      const q = pions.filter(x => x.uid === u).pop();
      if(!q) continue;
      out.push(..._remise(q, sous));
      sous = u;
    }
    return out.length ? out : null;
  }
  if(c.startsWith('D/')){
    const t = _champsD(c.substring(2), true);
    return t.length >= 3 ? [`D/${t[0]}/${t[2]}/${t[1]}`] : null;
  }
  if(c.startsWith('M/')){
    const t = _decoupeBrut(c.substring(2));
    if(t.length < 9) return null;
    return ['M/' + [t[0], t[5], t[6], t[7], t[8] || 'null', t[1], t[2], t[3], t[4], ...t.slice(9)].join('/')];
  }
  return null;
}

function _histoCmd(c){
  let e = _histo[_histo.length - 1];
  if(!e || e.no !== _actionNo){
    e = {no: _actionNo, cmds: [], inv: [], annulable: !_annulation, annule: false, etape: _etapeLog};
    _histo.push(e);
  }
  e.cmds.push(c);
  if(c.startsWith('UNDO')){ e.annulable = false; e.inv = []; }
  const i = e.annulable ? _inverse(c) : null;
  if(i) e.inv.unshift(...i);
  majUndo();
}

const _aAnnuler = () => {
  for(let i = _histo.length - 1; i >= 0; i--){
    const e = _histo[i];
    // Un coup relu d'un .vlog n'est pas annulé par Undo : seul ◀ le
    // recule. Undo ne touche qu'aux coups joués ici (ou reçus).
    if(e.etape) continue;
    if(e.annulable && !e.annule && (e.inv.length || (e.js && e.js.length))) return e;
  }
  return null;
};
// Annulation propre à l'application, pour un coup sans commande VASSAL à
// inverser (Start, Stop, Reverse du décompte des MP) : rattachée au coup
// en cours, elle est jouée par Undo.
function _histoJs(f){
  const e = _histo[_histo.length - 1];
  if(e && e.no === _actionNo && e.annulable) (e.js = e.js || []).push(f);
}

function majUndo(){ rafraichirActions(['undo']); }

function annuler(){
  const e = _aAnnuler();
  if(!e) return false;
  fermerMenu();
  // Undo ne recule jamais la relecture d'un .vlog (c'est le rôle de ◀) :
  // reculer ici tronquerait le log en cours d'enregistrement.
  _avantAction();
  const photo = _photo();
  e.annule = true;
  rejouerLog(e.inv);
  if(e.js) for(const f of [...e.js].reverse()) f();
  _actionOuverte = false;
  _annulation = true;
  try {
    ['UNDO\ttrue', ...e.inv, 'UNDO\tfalse'].forEach(_logCmd);
  } finally { _annulation = false; }
  _actionOuverte = false;
  recalerMouvements();
  rendreToutes();
  if(_pileSel) afficherPile();
  signalerCoup(photo);
  chatLog(`↶ Coup annulé (${e.inv.length} commande(s))`);
  majUndo();
  // Plus aucun coup joué pendant la relecture : on peut de nouveau reculer.
  if(_logPlancher && !_histo.some(x => !x.etape && x.annulable && !x.annule && x.inv.length)){
    _logPlancher = 0; majBarreLog();
  }
  return true;
}

// ═══ RELECTURE D'UN .vlog ═══
// Commandes de BasicCommandEncoder, séparateur « / » :
//   +/id/type/état · -/id · D/id/nouvelÉtat/ancienÉtat
//   M/id/carte/x/y/dessous/carteAnc/xAnc/yAnc/dessousAnc/joueur
// Les sous-commandes d'un coup composé suivent leur « LOG » dans des
// segments à part : tout segment de la zone de log est donc examiné.
// Un champ entouré d'apostrophes les perd (SequenceEncoder.unquote).
function _decoupeCmd(s){
  return _decoupeBrut(s).map(t => t.length > 1 && t[0] === "'" && t[t.length - 1] === "'" ? t.slice(1, -1) : t);
}
// Champs d'un D/. Les .vlog des versions précédentes d'ASLweb (et ceux
// que VASSAL en a réécrits) finissent l'état d'un pion de la sauvegarde
// par une contre-oblique (« …;OldMap;Main Map\ ») devant le « / » qui
// sépare le nouvel état de l'ancien, et ce « \/ » passe pour un « / »
// échappé. Les deux états ont autant de traits : la
// coupure est le « \/ » qui les équilibre, la contre-oblique restant au
// nouvel état. brut : apostrophes gardées, comme _decoupeBrut.
function _champsD(s, brut){
  const t = brut ? _decoupeBrut(s) : _decoupeCmd(s);
  if(t.length !== 2) return t;
  const e = t[1];
  for(let i = e.indexOf('\\/'); i >= 0; i = e.indexOf('\\/', i + 1)){
    const a = e.substring(0, i + 1), b = e.substring(i + 2);
    if(_traits(a).length === _traits(b).length) return [t[0], a, b];
  }
  return t;
}
const _nul = v => (v === 'null' || v === '' ? null : v);
const parUid = uid => pions.find(p => p.uid === uid && !p.deleted);
// Contenu d'une pile VASSAL d'après son état « carte;x;y;id0;…;@@couche ».
const _idsPile = etat => etat.replace(/\\/g, '').split(';').slice(3).filter(c => c && !c.startsWith('@@'));

// Pièces d'une pile VASSAL que déplace un M/ (champs t de _decoupeCmd),
// du fond vers le sommet, ou null si l'id est celui d'une pièce. Une
// pile que l'état de départ ne connaît pas (un .vlog écrit par une
// version précédente de VASLweb renommait les piles de l'état de départ
// sans renommer celles des coups relus) est la pile qui se trouvait à
// l'ancienne place du M/ : dans VASSAL, les pièces empilables d'un même
// emplacement forment une seule pile.
function _idsPileM(t, piles = _pilesVassal){
  const id = _nul(t[0]);
  if(!id || pions.some(q => q.uid === id)) return null;
  if(piles.has(id)) return piles.get(id);
  if(t.length < 8 || t[5] !== _carteJeu) return null;
  const s = pile((parseInt(t[6], 10) || 0) + ',' + (parseInt(t[7], 10) || 0));
  return s.length ? s.map(p => p.uid) : null;
}

// Retrait d'une pièce, ou de toute une pile : VASSAL retire une pile
// entière (« Delete » sur la pile, commande globale « remove all ») par
// un seul -/ portant l'identifiant de la pile.
function _retirer(id){
  const p = pions.find(q => q.uid === id && (!q.deleted || q.ailleurs));
  if(p){
    p.deleted = true; p.ailleurs = null; p.el.remove(); _sel.delete(p);
    return 1;
  }
  const ids = _pilesVassal.get(id);
  if(!ids) return 0;
  _pilesVassal.delete(id);
  _pilesRetirees.set(id, ids);
  let n = 0;
  for(const u of ids) n += _retirer(u);
  return n;
}

// M/ de pile rejoués : commande d'origine → un M/ par pion, du fond vers
// le sommet (voir _cmdsConsignees).
let _mParPion = new Map();

function rejouerLog(segs){
  let n = 0;
  for(let s of segs){
    const brut = s;
    if(s.startsWith('LOG\t')) s = s.substring(4);
    s = s.replace(/\\+$/, '');
    if(s.startsWith('CHAT')){
      afficherChat(_desechappe(s.substring(4)));
      n++;
    } else if(s.startsWith('LOS\t')){
      filDistant(s);
    } else if(s.startsWith('FLARE\t')){
      flareDistant(s);
    } else if(_appliquerLien(s)){
      n++;
    } else if(s.startsWith('INFO\t')){
      _infoEtat = s.substring(5); majChampsInfo();
      n++;
    } else if(s.startsWith('OBA/')){
      if(_obaAppliquer(s)) n++;
    } else if(s.startsWith('MutableProperty\t') || s.startsWith('GlobalProperty\t')){
      if(_appliquerGlob(s)) n++;
    } else if(s.startsWith('+/')){
      if(/^\+\/[^/]+\/stack\//.test(s)){
        _pilesVassal.set(s.split('/')[1], _idsPile(s.substring(s.indexOf('/stack/') + 7)));
        continue;
      }
      const d = lirePiece(s);
      if(!parUid(d.uid)){
        const q = creerPion(s, 1e9);
        // Née hors de la carte de jeu (« null;0;0 ») : elle attend le M/
        // qui la place, à l'écart comme une pièce envoyée ailleurs.
        if(d.carte !== _carteJeu){ q.deleted = true; q.ailleurs = d.carte || 'null'; q.el.remove(); }
        n++;
      }
    } else if(s.startsWith('-/')){
      n += _retirer(s.substring(2));
    } else if(s.startsWith('D/')){
      const t = _champsD(s.substring(2));
      if(_pilesVassal.has(t[0])){
        if(t[1]) _pilesVassal.set(t[0], _idsPile(t[1]));
        continue;
      }
      // Une pièce tenue sur une autre fenêtre (Casualties…) change d'état
      // elle aussi : elle le garde en revenant, et dans la sauvegarde.
      const id = _nul(t[0]);
      const p = pions.find(q => q.uid === id && (!q.deleted || q.ailleurs));
      const te = p && tePion(p);
      if(te && t[1]){
        fixerEtat(p, t[1]);
        const d = lirePiece(p);
        if(d.carte === _carteJeu){ p.x = d.x; p.y = d.y; }
        majApparence(p, d);
        n++;
      }
    } else if(s.startsWith('M/')){
      const t = _decoupeCmd(s.substring(2));
      if(t.length < 5) continue;
      const id = _nul(t[0]), dessous = _nul(t[4]);
      const pos = [t[1], parseInt(t[2], 10) || 0, parseInt(t[3], 10) || 0];
      const idsPile = _idsPileM(t);
      if(idsPile){
        // VASSAL déplace une pile entière d'un seul M/ portant son
        // identifiant : ses pions la suivent, du fond vers le sommet.
        // Une pile retrouvée à sa place prend ici son identifiant.
        _pilesVassal.set(id, idsPile);
        const ids = idsPile;
        const parPion = [];
        let sous = dessous;
        for(const u of ids){
          const p = parUid(u);
          const ancien = p ? `${_carteJeu}/${p.x}/${p.y}/${_dessous(p)}` : 'null/0/0/null';
          if(_deplacerPiece(u, pos, sous)){
            parPion.push(`M/${u}/${pos.join('/')}/${sous || 'null'}/${ancien}/${t[9] || joueur()}`);
            n++; sous = u;
          }
        }
        _mParPion.set(brut, parPion);
        // Posée sur une autre pile, elle s'y fond.
        if(dessous){
          _pilesVassal.delete(id);
          for(const u of ids) _changerDePile(u, null);
          let prec = dessous;
          for(const u of ids){ _changerDePile(u, prec); prec = u; }
        }
        continue;
      }
      _changerDePile(id, dessous);
      if(_deplacerPiece(id, pos, dessous)) n++;
    }
  }
  // Les CVP suivent les pièces entrées dans le bin ou sorties.
  _cvpAJour(false);
  return n;
}

// Place la pièce uid en pos [carte, x, y], au-dessus de « dessous ».
function _deplacerPiece(uid, [carte, x, y], dessous){
  const p = pions.find(q => q.uid === uid && (!q.deleted || q.ailleurs));
  if(!p) return false;
  // Retour sur la carte de jeu d'une pièce tenue à l'écart.
  if(p.ailleurs && carte === _carteJeu){
    p.deleted = false; p.ailleurs = null;
    document.getElementById('mapInner').appendChild(p.el);
  }
  if(carte !== _carteJeu){
    // Partie de la carte de jeu : sa place, pour l'y remettre.
    if(!p.ailleurs && !p.deleted) p.retour = {x: p.x, y: p.y};
    const te = tePion(p);
    if(te) fixerEtat(p, _majPosEtat(te.etat, carte, x, y));
    p.deleted = true; p.ailleurs = carte; p.el.remove();
    return true;
  }
  p.x = x; p.y = y;
  _empiler(p, parUid(dessous));
  _majSeg(p);
  return true;
}

// Une pièce déplacée quitte sa pile VASSAL et rejoint celle de la pièce
// sur laquelle elle est posée.
function _changerDePile(uid, dessous){
  for(const [id, ids] of _pilesVassal){
    if(ids.includes(uid)) _pilesVassal.set(id, ids.filter(u => u !== uid));
  }
  if(!dessous) return;
  for(const [id, ids] of _pilesVassal){
    const i = ids.indexOf(dessous);
    if(i >= 0){ _pilesVassal.set(id, [...ids.slice(0, i + 1), uid, ...ids.slice(i + 1)]); return; }
  }
}

// Range p juste au-dessus de « dessous » dans sa pile, ou au fond.
function _empiler(p, dessous){
  const s = pile(cle(p)).filter(q => q !== p);
  const i = dessous && s.includes(dessous) ? s.indexOf(dessous) + 1 : (dessous ? s.length : 0);
  s.splice(i, 0, p);
  s.forEach((q, k) => { q.ordre = k; });
}

// ═══ LIENS ENTRE PIÈCES — PieceLinker de VASL ═══
// Ctrl+Alt+L lie les deux pièces sélectionnées, Ctrl+Alt+U les délie.
// Un lien va dans les deux sens : deux commandes « LINK_PIECE:a,b » et
// « LINK_PIECE:b,a » (UNLINK_PIECE pour délier), consignées et envoyées
// comme les autres ; la sauvegarde les reprend (getRestoreCommand). Une
// pièce liée sélectionnée montre un trait rouge vers chacune de ses
// pièces liées et la portée en hexagones ; une pièce HIP de l'adversaire
// ne montre pas les siens, ni n'apparaît au bout d'un autre.
let _liens = new Map();        // uid → [uid…], dans l'ordre des liens
const _CMD_LIEN = /^(UN)?LINK_PIECE:([^,]*),(.*)$/;

function _appliquerLien(c){
  const m = _CMD_LIEN.exec(c);
  if(!m) return false;
  const [, un, de, a] = m;
  const l = _liens.get(de) || [];
  // addLink : ni lien d'une pièce à elle-même, ni lien en double.
  if(!un && de !== a && !l.includes(a)) _liens.set(de, [...l, a]);
  // removeLink : un seul lien restant, la pièce perd toute sa liste.
  if(un && _liens.has(de)){
    if(l.length === 1) _liens.delete(de); else _liens.set(de, l.filter(u => u !== a));
  }
  _planifierLiens();
  return true;
}

// Les liens d'une sauvegarde, comme ses commandes de restauration.
function lireLiens(segs){
  _liens = new Map();
  for(const s of segs) _appliquerLien(s.replace(/\\+$/, ''));
}
const segmentsLiens = () => [..._liens].flatMap(([de, l]) => l.map(a => `LINK_PIECE:${de},${a}`));

// keyPressed : lie (ou délie) les deux pièces, dans les deux sens.
function lierPions(a, b, delier){
  if(!a || !b || a === b) return false;
  const k = delier ? 'UNLINK_PIECE:' : 'LINK_PIECE:';
  [k + a.uid + ',' + b.uid, k + b.uid + ',' + a.uid].forEach(c => { _appliquerLien(c); _logCmd(c); });
  return true;
}
function lierSelection(delier){
  const s = [..._sel].filter(p => !p.deleted && !p.cache);
  if(s.length !== 2) return false;
  lierPions(s[0], s[1], delier);
  chatLog(`${delier ? '✂️' : '🔗'} ${s[0].nom} ${delier ? 'délié de' : 'lié à'} ${s[1].nom}`);
  return true;
}

// Portée entre deux pièces sur la carte LOS (map.range), 0 hors carte.
function _porteeLien(a, b){
  const m = _losPrete;
  if(!m) return null;
  try {
    const pa = _versLOS(a), pb = _versLOS(b);
    if(!m.onMap(pa.x, pa.y) || !m.onMap(pb.x, pb.y)) return 0;
    const ha = m.gridToHex(pa.x, pa.y), hb = m.gridToHex(pb.x, pb.y);
    return ha && hb ? LosMap.range(ha, hb, m.getMapConfiguration()) : 0;
  } catch(e){ return 0; }
}

// Un seul dessin par image, quel que soit le nombre de piles rendues.
let _liensPlanifies = false;
function _planifierLiens(){
  if(_liensPlanifies) return;
  _liensPlanifies = true;
  requestAnimationFrame(() => { _liensPlanifies = false; dessinerLiens(); });
}

// draw : sous les pions (drawAboveCounters faux), un trait de 2 px ;
// l'étiquette « Range: n » à 30 px à droite et en dessous de la pièce
// sélectionnée, de taille fixe à l'écran.
function dessinerLiens(){
  document.querySelectorAll('.etiqLien').forEach(e => e.remove());
  let s = document.getElementById('liensPions');
  const vu = p => p && !p.deleted && !p.cache && pions.includes(p);
  const paires = [];
  for(const [de, l] of _liens){
    const a = parUid(de);
    if(!vu(a) || !_sel.has(a)) continue;
    for(const u of l){ const b = parUid(u); if(vu(b)) paires.push([a, b]); }
  }
  if(!paires.length){ if(s) s.remove(); return; }
  if(!s){
    s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.id = 'liensPions';
    document.getElementById('mapInner').appendChild(s);
  }
  s.setAttribute('width', _mapW); s.setAttribute('height', _mapH);
  s.innerHTML = '';
  // La portée attend la carte LOS : on redessine quand elle est prête.
  if(!_losPrete && _losBoards) carteLOS().then(({map}) => { if(map && _losPrete) _planifierLiens(); });
  for(const [a, b] of paires){
    const l = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    l.setAttribute('x1', a.x * SC); l.setAttribute('y1', a.y * SC);
    l.setAttribute('x2', b.x * SC); l.setAttribute('y2', b.y * SC);
    l.setAttribute('stroke', '#ff0000'); l.setAttribute('stroke-width', 2);
    l.setAttribute('vector-effect', 'non-scaling-stroke');
    s.appendChild(l);
    const r = _porteeLien(a, b);
    if(r === null) continue;
    const e = document.createElement('div');
    e.className = 'etiqLien';
    e.textContent = 'Range: ' + r;
    const c = _ecartVersCarte(29, 29 - LOS_POLICE + LOS_DESCENTE);
    e.style.left = (a.x * SC + c.x / zoom) + 'px';
    e.style.top = (a.y * SC + c.y / zoom) + 'px';
    e.style.transform = `rotate(${-_rotVue}deg) scale(${1 / zoom})`;
    document.getElementById('mapInner').appendChild(e);
  }
}

_apresSelection.push(() => { if(_liens.size) _planifierLiens(); });

// Fire Lane (A9.22) depuis le menu d'une MG : le pion Firelane de la
// palette (onglet Unit, panneau Basic) est posé dans l'hexagone de la MG,
// lié à elle comme par Ctrl+Alt+L, et sélectionné pour être orienté.
const GPID_FIRELANE = '8';
const estMG = p => /^(?:[LMH]MG|\.50-cal)(?![A-Za-z])/.test(lirePiece(p).nomBase || '');
function poserFireLane(mg){
  if(!_bdd){ chatLog('⚠️ Base VASL pas encore chargée'); return null; }
  const x = _bdd.parGpid.get(GPID_FIRELANE) || [..._bdd.parGpid.values()].find(y => y.n === 'Firelane');
  const def = x && developperDefinition(x.cmd);
  if(!def){ chatLog(`⚠️ ${mg.nom} : pion Firelane introuvable`); return null; }
  const q = poserTraits(def.T, def.S, mg.x, mg.y, mg, 0);
  lierPions(mg, q);
  chatLog(`➕ Fire Lane posée et liée à ${mg.nom}`);
  selectionner([q]);
  return q;
}

// CX (A4.51) : le marqueur « CX » de la palette de VASL, posé juste
// au-dessus de l'unité, comme si on l'y glissait depuis la palette.
function poserCX(u){
  if(!_bdd){ chatLog('⚠️ Base VASL pas encore chargée'); return null; }
  const x = _bdd.pions.find(y => y.n === 'CX');
  const def = x && developperDefinition(x.cmd);
  if(!def){ chatLog(`⚠️ ${u.nom} : marqueur CX introuvable`); return null; }
  return poserTraits(def.T, def.S, u.x, u.y, u, 2);
}

// ═══ JEU : DÉPLACER, POSER, RETIRER ═══
// Chaque action est appliquée puis consignée au log, comme VASSAL :
// un M/ par pion déplacé, du fond vers le sommet, chacun désignant la
// pièce qui doit se trouver dessous.
function _avantAction(){
  // Jouer au milieu d'une relecture : comme BasicLogger, les coups
  // restants du fichier sont gardés et ▶ les applique ensuite sur la
  // position jouée. Reculer au-delà effacerait ce coup sans le dire :
  // le recul s'arrête ici (Undo l'annule).
  if(!_relecture) _jouerCoupDiffere();
  if(!_relecture && _logPos < _etapes.length && _logPlancher < _logPos){
    chatLog(`⏸️ Coup joué pendant la relecture, au coup ${_logPos} : ${_etapes.length - _logPos} coup(s) restant(s) gardé(s) — ▶ pour continuer`);
    _logPlancher = _logPos;
    majBarreLog();
  }
}

