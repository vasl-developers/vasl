// ═══ DISSIMULATION — Concealable et Concealment (VASL) ═══
// Un « ? » (trait concealment;propriétaire;nation) dissimule les unités
// de sa nationalité placées SOUS lui dans la pile ; une unité dissimulée
// porte dans son trait conceal l'identifiant du joueur qui l'a masquée.

// Ce que les traits d'un pion disent de la dissimulation.
function infoDissimulation(p){
  const te = tePion(p);
  if(!te) return {};
  const T = decoderTraits(te.type), S = decoderTraits(te.etat);
  for(let i = 0; i < T.length; i++){
    const ch = T[i].split(';');
    if(ch[0] === 'conceal'){
      const proprio = (S[i] || '').split(';')[0].replace(/\\/g, '').trim();
      return {i, unite: true, nation: (ch[3] || '').trim(), image: (ch[2] || '').replace(/\\/g, ''),
              marqueur: (ch[5] || '').trim(), proprio: proprio && proprio !== 'null' ? proprio : null};
    }
    if(ch[0] === 'concealment') return {i, compteur: true, nation: (ch[2] || '').trim()};
  }
  return {};
}

// Pion qui sépare deux lieux dans une pile (prototype « Location » :
// niveau de bâtiment, pont…) : propriété separateLocation. Un « ? » ne
// dissimule rien sous lui (Concealment.keyEvent, Concealable).
function _lieuSepare(p){
  const ts = tsPion(p);
  return !!ts && ts.typ.some(t => /^mark;separateLocation(;|$)/.test(t.replace(/\\/g, '')));
}

// Masque (`qui` = joueur) ou démasque (null) une unité, avec son « D/ ».
function fixerMasque(p, qui){
  const te = tePion(p);
  const m = infoDissimulation(p);
  if(!te || !m.unite) return false;
  const S = decoderTraits(te.etat);
  const f = (S[m.i] || '').split(';');
  f[0] = qui || 'null';
  S[m.i] = f.join(';');
  const neuf = encoderTraits(S);
  if(neuf === te.etat) return false;
  _avantAction();
  fixerEtat(p, neuf);
  _logCmd(`D/${p.uid}/${neuf}/${te.etat}`);
  majApparence(p);
  return true;
}

// Ctrl+C sur une unité (Concealable.myKeyEvent) : déjà dissimulée, elle
// est démasquée ; sinon elle est masquée et, faute de « ? » de sa
// nationalité au-dessus d'elle, on pose celui que désigne son trait.
function dissimuler(p){
  const m = infoDissimulation(p);
  if(!m.unite) return;
  if(m.proprio){
    if((voitMasque(m.proprio) || demasquable(p)) && fixerMasque(p, null)) chatLog(`👁️ ${p.nom} démasqué`);
    return;
  }
  fixerMasque(p, idJoueur());
  const s = pile(cle(p));
  let dejaLa = false;
  for(const q of s.slice(s.indexOf(p) + 1)){
    if(_lieuSepare(q)) break;
    const c = infoDissimulation(q);
    if(c.compteur && c.nation === m.nation){ dejaLa = true; break; }
  }
  if(!dejaLa) poserConcealment(p, m);
  ajusterDissimulation(cle(p));
  chatLog(`❔ ${p.nom} dissimulé`);
}

// Le « ? » : celui que nomme le trait (chemin de composants), à défaut le
// « ? » ou « L? » de la nationalité, selon la taille de l'image de masque.
// Comme Concealment.setId, il prend le joueur pour propriétaire.
function poserConcealment(p, m){
  if(!_bdd){ chatLog('⚠️ Base VASL pas encore chargée'); return null; }
  let x = _slotDuChemin(m.marqueur);
  if(!x) x = _bdd.parChemin.get(['?', m.nation, /58/.test(m.image) ? 'L?' : '?'].join('\u0001'));
  const def = x && developperDefinition(x.cmd);
  if(!def){ chatLog(`⚠️ ${p.nom} : contre-pion « ? » introuvable pour ${m.nation || '?'}`); return null; }
  const i = def.T.findIndex(t => /^concealment;/.test(t));
  if(i >= 0){
    const ch = def.T[i].split(';');
    ch[1] = idJoueur();
    def.T[i] = ch.join(';');
  }
  return poserTraits(def.T, def.S, p.x, p.y, p, 2);
}

// Recalcul d'une pile (Concealment.keyEvent / Concealable.adjustConcealment) :
// de haut en bas, une unité est dissimulée dès qu'un « ? » de sa
// nationalité se trouve au-dessus d'elle. Seules les unités du joueur sont
// touchées — celles de l'adversaire ne sont pas « maskable ».
let _ajustEnCours = false;
function ajusterDissimulation(k){
  if(_ajustEnCours) return;
  const s = pile(k);
  const infos = s.map(infoDissimulation);
  if(!infos.some(m => m.compteur || (m.unite && m.proprio === idJoueur()))) return;
  _ajustEnCours = true;
  try {
    const nations = new Set();
    for(let i = s.length - 1; i >= 0; i--){
      const m = infos[i];
      if(m.compteur){ nations.add(m.nation); continue; }
      if(_lieuSepare(s[i])){ nations.clear(); continue; }
      if(!m.unite || (m.proprio && m.proprio !== idJoueur())) continue;
      const doit = nations.has(m.nation);
      if(doit && !m.proprio) fixerMasque(s[i], idJoueur());
      else if(!doit && m.proprio) fixerMasque(s[i], null);
    }
  } finally { _ajustEnCours = false; }
}

// ── « ? » qui suivent les unités sorties d'une pile (A12.15).
// Le « ? » de nationalité `nation` le plus proche au-dessus de s[i]. Un
// pion qui sépare deux lieux (niveau, pont) arrête la recherche, comme
// dans ajusterDissimulation.
function _qmarkAuDessus(s, i, nation){
  for(let j = i + 1; j < s.length; j++){
    if(_lieuSepare(s[j])) return null;
    const c = infoDissimulation(s[j]);
    if(c.compteur && c.nation === nation) return s[j];
  }
  return null;
}

// Une unité non dissimulée du joueur, de cette nationalité, déjà dans la
// pile d'arrivée : un « ? » posé au sommet la masquerait aussi. On n'en
// pose alors aucun.
function _arriveeDecouverte(kArr, liste, nation){
  return pile(kArr).some(q => {
    if(liste.includes(q)) return false;
    const m = infoDissimulation(q);
    return m.unite && !m.proprio && m.nation === nation;
  });
}

// Avant le déplacement : pour chaque unité dissimulée qui quitte sa pile,
// le « ? » qui la coiffe.
function _qmarkDesPartants(liste, kArr){
  const r = new Map();
  for(const p of liste){
    const k = cle(p);
    if(k === kArr) continue;
    const m = infoDissimulation(p);
    if(!m.unite || !m.proprio) continue;
    const s = pile(k);
    const q = _qmarkAuDessus(s, s.indexOf(p), m.nation);
    if(q && !liste.includes(q)) r.set(p, q);
  }
  return r;
}

// Les « ? » dont toutes les unités partent ensemble : ils les suivent.
function _qmarkQuiSuivent(liste, kArr, sousQ){
  const r = [];
  for(const q of new Set(sousQ.values())){
    const nation = infoDissimulation(q).nation;
    if(_arriveeDecouverte(kArr, liste, nation)) continue;
    const s = pile(cle(q));
    const couverts = s.filter((z, i) => {
      const m = infoDissimulation(z);
      return m.unite && m.proprio && m.nation === nation && _qmarkAuDessus(s, i, nation) === q;
    });
    if(couverts.every(z => liste.includes(z))){
      r.push(q);
      chatLog(`❔ le « ? » suit ${couverts.length > 1 ? couverts.length + ' unités' : couverts[0].nom}`);
    }
  }
  return r;
}

// Après le déplacement : un « ? » neuf pour les partants qu'aucun « ? »
// ne coiffe à l'arrivée, posé au-dessus du plus haut d'entre eux.
function _qmarkPourPartants(liste, kArr, sousQ){
  const s = pile(kArr);
  const parNation = new Map();
  s.forEach((p, i) => {
    if(!sousQ.has(p)) return;
    const m = infoDissimulation(p);
    if(!m.unite || !m.proprio || _qmarkAuDessus(s, i, m.nation)) return;
    if(_arriveeDecouverte(kArr, liste, m.nation)) return;
    parNation.set(m.nation, {p, m});           // le dernier est le plus haut
  });
  for(const {p, m} of parNation.values())
    if(poserConcealment(p, m)) chatLog(`❔ « ? » posé sur ${p.nom}`);
}

// Clone.myKeyEvent : la copie rejoint la pile par placeOrMerge, au sommet.
function cloner(p){
  const te = tePion(p);
  if(!te) return null;
  const q = poserTraits(decoderTraits(te.type), decoderTraits(te.etat), p.x, p.y, p, 0);
  chatLog(`➕ ${p.nom} cloné`);
  return q;
}

// ── Le menu, en deux temps : l'arbre des commandes, puis son rendu —
// menu flottant à la souris, feuille du bas au doigt. L'exécution passe
// toujours par commander().
//   item : {nom, touche, cmd, grise} ou sous-menu {nom, items}
function arbreCommandes(p){
  const te = tePion(p);
  if(!te) return null;
  const {cmds, sousMenus, T} = commandesDe(te.type);
  // Restrictions (hideCmd) en vigueur : elles visent les commandes des
  // traits qui leur sont intérieurs.
  const S = decoderTraits(te.etat);
  let pr = null;
  const restriction = c => {
    for(const r of cmds){
      if(!r.bloque || r.i >= c.i || !c.touche || !r.bloque.touches.includes(c.touche)) continue;
      pr = pr || proprietesPion(p, T, S);
      if(evaluerCondition(r.bloque.cond, pr)) return r.bloque.cacher ? 'cacher' : 'griser';
    }
    return null;
  };
  // Deux traits peuvent offrir la même commande (les deux « Set Sides » de
  // la roue) : la touche les atteint tous, une seule entrée suffit.
  const vues = new Set();
  // Comme Obscurable de VASSAL : un pion masqué par un autre joueur
  // n'offre aucune commande.
  // Sauf Conceal, si son joueur laisse l'adversaire la démasquer.
  const visibles = accesRefuse(p) ? cmds.filter(c => c.demasque && demasquable(p)) : cmds.filter(c => {
    if(!c.nom || restriction(c) === 'cacher') return false;
    const k = c.touche ? c.nom + '\u0001' + c.touche : null;
    if(k && vues.has(k)) return false;
    if(k) vues.add(k);
    return true;
  });

  // Arbre du menu, comme MenuDisplayer de VASSAL : une commande citée par
  // un trait SubMenu va dans ce sous-menu, qui peut lui-même figurer dans
  // un autre ; chaque sous-menu prend la place de sa première commande.
  const parent = new Map();
  for(const sm of sousMenus) for(const n of sm.cmds) if(n && n !== sm.nom && !parent.has(n)) parent.set(n, sm.nom);
  const racine = {items: []}, noeuds = new Map();
  const sousMenu = (nom, prof) => {
    if(noeuds.has(nom)) return noeuds.get(nom);
    const nd = {nom, items: []};
    noeuds.set(nom, nd);
    const pp = parent.get(nom);
    (pp && prof < 8 ? sousMenu(pp, prof + 1) : racine).items.push(nd);
    return nd;
  };
  for(const c of visibles){
    const pp = parent.get(c.nom);
    (pp ? sousMenu(pp, 0) : racine).items.push({nom: c.nom, touche: c.touche, cmd: c, grise: restriction(c) === 'griser'});
  }
  // Véhicule : Start / Stop et Reverse, juste avant le changement de VCA.
  const ajoutes = !accesRefuse(p) && typeof itemsVehicule === 'function' ? itemsVehicule(p) : [];
  if(ajoutes.length){
    const placer = items => {
      const i = items.findIndex(it => /^CA c?cw$/.test(it.nom));
      if(i >= 0){ items.splice(i, 0, ...ajoutes); return true; }
      return items.some(it => it.items && placer(it.items));
    };
    if(!placer(racine.items)) racine.items.push(...ajoutes);
  }
  // Infanterie : Load / Unload en tête du menu (D6.4, D6.5).
  // Puis Smoke Hex / Smoke ADJ d'une escouade à exposant Smoke (A24.1).
  const transport = !accesRefuse(p) && typeof itemsTransport === 'function' ? [...itemsTransport(p), ...itemsSmoke(p)] : [];
  if(transport.length) racine.items.unshift(...transport);
  // Unité à pied : CX, le marqueur posé sur elle.
  const nat = !accesRefuse(p) && typeof natureMouvement === 'function' ? natureMouvement(p) : null;
  if(nat && nat.unite === 'MF')
    racine.items.push({nom: 'CX', cmd: {nom: 'CX', faire: ({effets}) => effets.push(() => poserCX(p))}});
  // MG : Fire Lane, posée et liée à la MG.
  if(!accesRefuse(p) && estMG(p))
    racine.items.push({nom: 'Fire Lane', cmd: {nom: 'Fire Lane', faire: ({effets}) => effets.push(() => poserFireLane(p))}});
  return {titre: p.el.title || p.nom, items: racine.items,
          vide: visibles.length ? null : accesRefuse(p) ? 'Aucune commande — pion masqué par un autre joueur' : 'Aucune commande'};
}

// Une commande choisie dans un menu, une feuille ou une barre : un pion
// sélectionné la transmet aux pions de la sélection qui ont une commande
// du même nom sur la même touche. Le raccourci clavier, lui, part à toute
// la sélection, comme dans VASSAL : dans VASL, Ctrl+F casse les unités
// (Break), mais met aussi la MMG en malf et fait tourner le marqueur
// Prep Fire en Bnd. Fire (Flip) ; choisir « Break » sur une pile entière
// ne doit casser que les unités.
let _freqCmd = (() => { try { return JSON.parse(localStorage.getItem('aslweb.freqCmd') || '{}') || {}; } catch(e){ return {}; } })();
function lancerCommande(p, c){
  _freqCmd[c.nom] = (_freqCmd[c.nom] || 0) + 1;
  try { localStorage.setItem('aslweb.freqCmd', JSON.stringify(_freqCmd)); } catch(e){}
  if(c.touche && _sel.has(p) && _sel.size > 1) commanderSelection(c.touche, undefined, c.nom);
  else if(c.touche) commander([p], c.touche); else commander([p], null, c.cmd);
}

// pointerType : celui du geste qui ouvre le menu ; à défaut, le profil.
function ouvrirMenu(p, cx, cy, pointerType){
  fermerMenu();
  const arbre = arbreCommandes(p);
  if(!arbre) return;
  const tactile = pointerType ? pointerType !== 'mouse' : _profil !== 'pc';
  if(tactile) rendreFeuilleCommandes(arbre, p);
  else rendreMenuFlottant(arbre, p, cx, cy);
}

function rendreMenuFlottant(arbre, p, cx, cy){
  const m = document.createElement('div');
  m.id = 'ctx';
  m.setAttribute('role', 'menu');
  const tt = document.createElement('div');
  tt.className = 'tt'; tt.textContent = arbre.titre;
  m.appendChild(tt);
  const remplir = (conteneur, items) => {
    for(const c of items){
      const it = document.createElement('div');
      it.className = 'it';
      it.setAttribute('role', 'menuitem');
      const a = document.createElement('span'); a.textContent = c.nom;
      const b = document.createElement('span'); b.className = 'rc';
      it.append(a, b);
      if(c.items){
        // Sous-menu : s'ouvre au survol, ou au toucher.
        it.classList.add('smi');
        b.textContent = '▸';
        const sm = document.createElement('div');
        sm.className = 'ssmenu';
        remplir(sm, c.items);
        it.appendChild(sm);
        const ouvrir = () => {
          for(const f of it.parentElement.children) if(f !== it) f.classList.remove('ouvert');
          it.classList.add('ouvert');
          placerSousMenu(it, sm);
        };
        it.addEventListener('mouseenter', ouvrir);
        it.addEventListener('mouseleave', () => it.classList.remove('ouvert'));
        it.addEventListener('click', e => { e.stopPropagation(); if(e.target === it || e.target.parentElement === it) ouvrir(); });
      } else {
        b.textContent = libelleTouche(c.touche);
        it.addEventListener('mouseenter', () => {
          for(const f of it.parentElement.children) if(f !== it) f.classList.remove('ouvert');
        });
        if(c.grise){
          it.classList.add('off');
          it.addEventListener('click', e => e.stopPropagation());
        } else it.addEventListener('click', e => {
          e.stopPropagation();
          fermerMenu();
          lancerCommande(p, c);
        });
      }
      conteneur.appendChild(it);
    }
  };
  remplir(m, arbre.items);
  if(arbre.vide){
    const v = document.createElement('div');
    v.className = 'vide';
    v.textContent = arbre.vide;
    m.appendChild(v);
  }
  document.body.appendChild(m);
  const x = Math.min(cx, window.innerWidth - m.offsetWidth - 4);
  const y = Math.min(cy, window.innerHeight - m.offsetHeight - 4);
  m.style.left = Math.max(4, x) + 'px'; m.style.top = Math.max(4, y) + 'px';
}

// Feuille du bas : un sous-menu remplace le contenu, avec un en-tête
// « ‹ nom » pour revenir.
function rendreFeuilleCommandes(arbre, p){
  const f = ouvrirFeuille({id: 'feuilleCmd', titre: arbre.titre, hauteur: 'moitie'});
  const pileNiv = [];
  const remplir = (items, nom) => {
    f.corps.replaceChildren();
    if(nom){
      const r = document.createElement('button');
      r.type = 'button'; r.className = 'act ligne retour';
      r.innerHTML = '<span class="ic">‹</span><span class="lb"></span>';
      r.querySelector('.lb').textContent = nom;
      r.addEventListener('click', () => { pileNiv.pop(); const h = pileNiv[pileNiv.length - 1]; remplir(h ? h.items : arbre.items, h && h.nom); });
      f.corps.appendChild(r);
    }
    for(const c of items){
      const b = document.createElement('button');
      b.type = 'button'; b.className = 'act ligne';
      b.innerHTML = '<span class="lb"></span><span class="rc"></span>';
      b.querySelector('.lb').textContent = c.nom;
      b.querySelector('.rc').textContent = c.items ? '›' : libelleTouche(c.touche);
      if(c.grise) b.disabled = true;
      b.addEventListener('click', () => {
        if(c.items){ pileNiv.push(c); remplir(c.items, c.nom); return; }
        f.fermer();
        lancerCommande(p, c);
      });
      f.corps.appendChild(b);
    }
    if(!items.length && arbre.vide){
      const v = document.createElement('div');
      v.className = 'videFeuille'; v.textContent = arbre.vide;
      f.corps.appendChild(v);
    }
  };
  remplir(arbre.items, null);
}

// À droite de l'entrée, ou à gauche faute de place ; jamais hors de l'écran.
function placerSousMenu(it, sm){
  const r = it.getBoundingClientRect();
  let x = r.right - 2, y = r.top - 4;
  if(x + sm.offsetWidth > window.innerWidth - 4) x = Math.max(4, r.left - sm.offsetWidth + 2);
  if(y + sm.offsetHeight > window.innerHeight - 4) y = Math.max(4, window.innerHeight - sm.offsetHeight - 4);
  sm.style.left = x + 'px'; sm.style.top = y + 'px';
}

// Raccourcis des pions : comme dans VASSAL, la touche s'adresse à toute la
// sélection — ici la pile sélectionnée. Elle passe avant ceux des dés.
function _brancherCommandes(){
  document.addEventListener('pointerdown', e => {
    if(!e.target.closest('#ctx, .feuille, .voile')) fermerMenu();
    if(!e.target.closest('#editLabel')) fermerEditeurLabel();
  }, true);
  document.getElementById('mapWrap').addEventListener('scroll', fermerMenu);
  document.addEventListener('keydown', e => {
    if(e.key === 'Escape'){
      // Le menu d'abord, puis les piles dépliées, puis la sélection.
      if(document.getElementById('ctx')){ fermerMenu(); return; }
      if(/^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)) return;
      if(!replierTout() && (_sel.size || _pileSel)) selectionnerPile(null);
      return;
    }
    // Begin Logfile [Alt+W], End Logfile [Alt+Maj+W], comme VASSAL.
    if(e.altKey && !e.ctrlKey && e.code === 'KeyW'){
      e.preventDefault();
      if(e.shiftKey) terminerLog(); else commencerLog();
      return;
    }
    if((e.key === 'PageDown' || e.key === 'PageUp') && _etapes.length
       && !/^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)){
      e.preventDefault();
      if(e.key === 'PageDown') avancerLog(); else reculerLog();
      return;
    }
    if((!_pileSel && !_sel.size) || /^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)) return;
    if(['Control', 'Shift', 'Alt', 'Meta'].includes(e.key)) return;
    // PieceLinker : Ctrl+Alt+L lie, Ctrl+Alt+U délie.
    if(e.ctrlKey && e.altKey && !e.shiftKey && !e.metaKey && (e.code === 'KeyL' || e.code === 'KeyU')){
      e.preventDefault(); e.stopImmediatePropagation();
      fermerMenu();
      lierSelection(e.code === 'KeyU');
      return;
    }
    const k = toucheClavier(e);
    if(ciblesTouche(k).length){
      e.preventDefault(); e.stopImmediatePropagation();
      fermerMenu();
      commanderSelection(k);
      return;
    }
    // Touches de rang du module (StackMetrics), si aucun trait ne les prend.
    const m = metriques();
    const rang = ['top', 'bottom', 'up', 'down'].find(n => m[n] === k);
    if(rang && _sel.size){
      e.preventDefault(); e.stopImmediatePropagation();
      fermerMenu();
      rangerSelection(rang);
    }
  }, true);
}

// Les pions visés par une touche : la sélection si elle existe, sinon la
// pile courante. Ceux qui n'ont pas la touche sont écartés.
// nom : seuls les pions dont la commande de cette touche porte ce nom
// (voir lancerCommande).
function ciblesTouche(touche, nom){
  const base = _sel.size ? [..._sel] : (_pileSel ? pile(_pileSel).filter(p => !p.cache) : []);
  return trierParPile(base.filter(p => !p.cache && _aCommande(p, touche, nom)));
}
const _aCommande = (p, touche, nom) => commandesDe((tePion(p) || {}).type || '')
  .cmds.some(c => c.touche === touche && (nom == null || c.nom === nom));
// Comme un raccourci clavier de VASSAL : la touche part à toute la
// sélection. Les pions masqués par un autre joueur ne la reçoivent pas ;
// un seul avertissement pour tous.
function commanderSelection(touche, cmd, nom){
  const tous = ciblesTouche(touche, nom);
  if(!tous.length) return false;
  const cibles = tous.filter(p => !accesRefuse(p, touche));
  if(!cibles.length){ refuser(tous[0]); return true; }
  commander(cibles, touche, cmd);
  return true;
}

// Envoie une commande à des pions. Si elle demande un choix (propriété
// de type E, comme « Set Sides » de la roue des phases), la boîte de
// choix s'ouvre d'abord, et les réponses accompagnent la touche.
function commander(pions, touche, cmdDirecte){
  const lot = {};
  const k = touche || (cmdDirecte && cmdDirecte.touche);
  let invites = [], S = null;
  for(const p of pions){
    const te = tePion(p);
    if(!te || !k) continue;
    invites = commandesDe(te.type).cmds.filter(c => c.invite && c.touche === k).map(c => c.invite);
    if(invites.length){ S = decoderTraits(te.etat); break; }
  }
  const lancer = () => { for(const p of pions) executerTouche(p, touche, cmdDirecte, lot); };
  if(!invites.length) return lancer();
  demanderChoix(pions[0], invites, S, reponses => { lot.reponses = reponses; lancer(); });
}

// Boîte de choix, à la place de celle de VASSAL : une liste par invite.
function demanderChoix(p, invites, S, valider){
  fermerEditeurLabel();
  const box = document.createElement('div');
  box.id = 'editLabel'; box.className = 'editLabel choix';
  const listes = invites.map(inv => {
    const l = document.createElement('label');
    const t = document.createElement('span'); t.textContent = inv.titre;
    const sel = document.createElement('select');
    for(const o of inv.options){
      const op = document.createElement('option'); op.value = op.textContent = o;
      sel.appendChild(op);
    }
    const actuel = inv.actuel(S);
    if(inv.options.includes(actuel)) sel.value = actuel;
    l.append(t, sel);
    box.appendChild(l);
    return sel;
  });
  const ok = document.createElement('button'); ok.textContent = 'OK';
  box.appendChild(ok);
  document.body.appendChild(box);
  const r = p.el.getBoundingClientRect();
  box.style.left = Math.max(4, Math.min(r.left, window.innerWidth - box.offsetWidth - 4)) + 'px';
  box.style.top = Math.max(4, Math.min(r.bottom + 6, window.innerHeight - box.offsetHeight - 4)) + 'px';
  _ancrerAuClavier(box);
  const fini = () => {
    const rep = {};
    invites.forEach((inv, j) => { rep[inv.cle] = listes[j].value; });
    fermerEditeurLabel();
    valider(rep);
  };
  box.addEventListener('keydown', e => {
    e.stopPropagation();
    if(e.key === 'Enter') fini();
    else if(e.key === 'Escape') fermerEditeurLabel();
  });
  ok.addEventListener('click', fini);
  box.addEventListener('pointerdown', e => e.stopPropagation());
  setTimeout(() => listes[0] && listes[0].focus(), 0);
}

// Ctrl+Z : Undo, si aucun pion de la pile sélectionnée n'a pris la touche.
document.addEventListener('keydown', e => {
  if(!(e.ctrlKey || e.metaKey) || e.shiftKey || e.altKey || (e.key || '').toLowerCase() !== 'z') return;
  if(/^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName) || e.target.isContentEditable) return;
  e.preventDefault();
  annuler();
});

function fermerMenu(){
  const m = document.getElementById('ctx');
  if(m) m.remove();
  fermerFeuille('feuilleCmd');
}

// ── Geste sur un pion : glisser déplace toute la pile, un simple clic
// la sélectionne. Un pion seul se tire depuis la liste de la pile.
// L'événement continue jusqu'à la carte, qui compte le pointeur : un
// deuxième doigt en fait un pincement.
let _dernierPointeur = 'mouse';
document.addEventListener('pointerdown', e => { _dernierPointeur = e.pointerType || 'mouse'; }, true);
const vibrer = () => { try { if(navigator.vibrate) navigator.vibrate(10); } catch(e){} };
// Au doigt, un pion de la carte ne se saisit qu'après cet appui maintenu
// (ms) ; avant, le doigt qui bouge fait défiler la carte.
const DELAI_SAISIE_TACTILE = 300;

// À la souris, comme KeyBufferer.mousePressed : l'appui sélectionne.
//   · pion hors de la sélection : elle est remplacée (Maj : complétée) par
//     sa pile entière si elle est repliée, par lui seul si elle est
//     dépliée ou avec Ctrl (Cmd sur Mac), qui ajoute ou retire ;
//   · pion déjà sélectionné : Ctrl le retire (sa pile, si elle est
//     repliée) ; un simple clic sans glisser ne garde que lui (sa pile).
// Le glissé emporte ensuite toute la sélection.
// Au doigt, la sélection se fait au relâché, et le glissé emporte la
// sélection si le pion en fait partie, sinon sa pile (ou lui seul, dans
// une pile dépliée).
const _basculeSel = e => e.ctrlKey || e.metaKey;
function _pionPointerDown(e, p){
  if(e.button > 0) return;
  cacherSurvol();
  const k = cle(p), souris = e.pointerType === 'mouse' || !e.pointerType;
  const deplie = () => _depliees.has(k);
  const pileOuLui = () => deplie() ? [p] : pile(k).filter(selectionnable);
  // Pose ou déplacement en deux appuis : la pile touchée est la destination.
  if(_modeCarte){
    glisser(e, null, 0, () => {}, () => toucherModeCarte({x: p.x, y: p.y}), {tapSeul: true});
    return;
  }
  // Pions verrouillés : le doigt qui glisse déplace la carte.
  if(!souris && _verrouPions){
    glisser(e, null, 0, () => {}, () => { if(deplie() && selectionnable(p)) selectionner([p]); else appuiSurPile(k); }, {tapSeul: true});
    return;
  }
  let reduire = false;
  // Pile où sont arrivés des pions en mouvement : saisir l'un des pions
  // qui l'occupaient déjà poursuit le mouvement de la sélection, sans le
  // prendre. Un simple clic, lui, sélectionne la pile entière ; Ctrl ou
  // Maj ajoutent des pions au groupe en mouvement.
  const enCours = !_sel.has(p) && !deplie() && selDans(k).length > 0 && !_basculeSel(e) && !e.shiftKey;
  if(enCours) reduire = true;
  else if(souris && selectionnable(p)){
    if(!_sel.has(p)){
      if(_basculeSel(e)) selectionner([p], 'basculer');
      else selectionner(pileOuLui(), e.shiftKey ? 'ajouter' : 'remplacer');
    } else if(_basculeSel(e)) selectionner(pileOuLui(), 'retirer');
    else reduire = !e.shiftKey;
  }
  const aDeplacer = () => {
    if(_sel.has(p) || enCours) return piecesADeplacer();
    if(!souris && deplie() && selectionnable(p)) return [p];
    // Les pions HIP de l'adversaire, invisibles, restent où ils sont.
    return pile(k).filter(q => !q.cache);
  };
  // Au doigt : un pion déjà sélectionné (par un appui simple, ou arrivé
  // avec la sélection en mouvement) part dès que le doigt glisse. Sinon,
  // saisie après DELAI_SAISIE_TACTILE ms d'appui immobile (les pions qui
  // vont partir se soulèvent) ; un doigt qui bouge avant rend son pointeur
  // à la carte, qui défile en rattrapant le chemin parcouru.
  const pris = _sel.has(p) || enCours;
  const tactile = souris ? {} : pris ? {} : {
    appuiLong: DELAI_SAISIE_TACTILE,
    arme: () => { for(const q of aDeplacer()) if(q.el) q.el.classList.add('saisi'); },
    fin: () => { document.querySelectorAll('.pion.saisi').forEach(el => el.classList.remove('saisi')); },
    surAbandon: ev => {
      const pt = _pointeursCarte.get(e.pointerId);
      if(!pt) return;
      pt.pion = false; pt.surPion = true;
      const wrap = document.getElementById('mapWrap');
      wrap.classList.add('pan');
      wrap.scrollLeft -= ev.clientX - pt.x0;
      wrap.scrollTop  -= ev.clientY - pt.y0;
      pt.x = ev.clientX; pt.y = ev.clientY;
    }
  };
  glisser(e, () => sommetDeplace(aDeplacer(), p), q => q.sz * zoom, (cx, cy) => {
    const c = clientVersCarte(cx, cy);
    const l = aDeplacer();
    if(c && l.length) (souris ? deplacer : deplacerAuDoigt)(l, c.x, c.y);
  }, () => {
    if(souris){
      if(!selectionnable(p)) selectionnerPile(k);
      else if(reduire) selectionner(pileOuLui());
    }
    else if(deplie() && selectionnable(p)) selectionner([p]);
    else appuiSurPile(k);
  }, tactile);
}

// Glisser générique avec fantôme : la capture est prise sur le document,
// faute de quoi le geste se perd en quittant l'élément de départ. Seul le
// pointeur qui a commencé le geste le fait avancer ; un pincement
// l'abandonne (rien n'est déplacé).
//   src            : URL de l'image du fantôme, ou un pion de la carte,
//                    dont le fantôme copie l'aspect (orientation, tourelle),
//                    ou une fonction qui le donne au début du glissé ;
//                    taille peut alors être une fonction de ce pion.
//   opts.appuiLong : au doigt, dans une liste qui défile, le glissé ne
//                    part qu'après cet appui maintenu (ms) ; avant, le
//                    doigt fait défiler la liste.
//   opts.zone      : élément où le glissé reste local (changer de rang dans
//                    une liste) : tant que le pointeur y est, pas de
//                    fantôme, mais opts.dansZone(ev) ; lâché dedans,
//                    opts.lacherZone(ev). Hors de la zone, vers la carte.
//   opts.tapSeul   : pas de glissé (le geste revient à la carte) ; seul le
//                    toucher compte.
//   opts.debut     : appelé quand le geste devient un glissé.
//   opts.arme      : appelé quand l'appui maintenu (opts.appuiLong) arme
//                    le glissé, après la vibration.
//   opts.surAbandon: appelé (avec l'événement) quand le doigt a bougé
//                    avant l'armement : le geste n'est pas un glissé. Pas
//                    pour les autres abandons (pincement, menu).
//   opts.fin       : appelé à la fin du geste, quelle qu'elle soit.
let _glisse = null;
function glisser(e, src, taille, auLacher, auClic, opts = {}){
  if(_glisse) _glisse.abandonner();
  const id = e.pointerId, x0 = e.clientX, y0 = e.clientY;
  const doigt = !!e.pointerType && e.pointerType !== 'mouse';
  const seuil = doigt ? 12 : 8;
  let arme = !(doigt && opts.appuiLong), timer = null;
  let fantome = null, cible = null, rects = null, raf = 0, dernier = null, demarre = false;
  if(!arme) timer = setTimeout(() => { arme = true; vibrer(); if(opts.arme) opts.arme(); }, opts.appuiLong);
  const wrap = document.getElementById('mapWrap');
  const oublierRects = () => { rects = null; };
  const zr = opts.zone ? opts.zone.getBoundingClientRect() : null;
  const dansZone = ev => !!zr && ev.clientX >= zr.left && ev.clientX <= zr.right && ev.clientY >= zr.top && ev.clientY <= zr.bottom;
  const montrer = oui => { if(fantome){ fantome.style.display = oui ? '' : 'none'; if(!oui){ cible.style.display = 'none'; cacherLoupe(); } } };
  const peindre = () => {
    raf = 0;
    const ev = dernier;
    if(!ev) return;
    if(dansZone(ev)){ montrer(false); if(opts.dansZone) opts.dansZone(ev); return; }
    if(zr && opts.horsZone) opts.horsZone(ev);
    if(!fantome) creerFantome();
    montrer(true);
    // Lectures d'abord (une seule fois par image), écritures ensuite.
    if(!rects) rects = _rectsCarte();
    const c = clientVersCarte(ev.clientX, ev.clientY, rects);
    const a = c && positionDepot(c.x, c.y);
    const t = Math.max(24, taille);
    fantome.style.left = (ev.clientX - t/2) + 'px';
    fantome.style.top  = (ev.clientY - t/2) + 'px';
    cible.style.display = a ? '' : 'none';
    if(a){ cible.style.left = a.x*SC + 'px'; cible.style.top = a.y*SC + 'px'; }
    // Au doigt, le fantôme et la cible passent sous le doigt : une loupe
    // montre, au-dessus, l'hexagone visé.
    // À la souris, la loupe ne montre que l'hexagone survolé.
    dessinerLoupe(ev.clientX, ev.clientY, a, rects, !doigt);
    if(opts.pendant) opts.pendant(ev, a);
  };
  const creerFantome = () => {
    // Fantôme et taille choisis quand le geste devient un glissé : la
    // sélection, qui décide de ce qui part, est alors arrêtée.
    if(typeof src === 'function') src = src();
    if(typeof taille === 'function') taille = taille(src);
    const t = Math.max(24, taille);
    if(src && src.el){
      // Pion de la carte : le fantôme en est une copie, telle qu'affichée
      // — CA du véhicule (trait rotate), tourelle (TCA), couches et
      // étiquettes —, et non sa seule image de base, toujours droite.
      fantome = document.createElement('div');
      const c = src.el.cloneNode(true);
      const w = parseFloat(src.el.style.width) || src.sz, h = parseFloat(src.el.style.height) || src.sz;
      const k = t / (src.sz || w);
      c.className = 'pion';
      c.removeAttribute('title');
      c.style.cssText = `left:${(t - w*k)/2}px;top:${(t - h*k)/2}px;width:${w}px;height:${h}px;`
        + `transform:scale(${k});transform-origin:0 0;background:${src.el.style.background || ''}`;
      // Vue tournée : à l'écran, le pion suit la même rotation que sur la
      // carte (--rotVue, posé sur #mapInner, n'atteint pas le fantôme) ;
      // une pièce de terrain tourne aussi avec la carte.
      fantome.style.setProperty('--rotVue', _rotVue + 'deg');
      if(src.el.classList.contains('terrain')) fantome.style.rotate = _rotVue + 'deg';
      fantome.appendChild(c);
    } else {
      fantome = document.createElement('img');
      if(src) fantome.src = src;
    }
    fantome.className = 'fantome';
    fantome.style.width = fantome.style.height = t + 'px';
    document.body.appendChild(fantome);
    cible = document.createElement('div');
    cible.className = 'cible';
    document.getElementById('mapInner').appendChild(cible);
  };
  const bouge = ev => {
    if(ev.pointerId !== id) return;
    if(!demarre && Math.hypot(ev.clientX-x0, ev.clientY-y0) < seuil) return;
    if(!arme || opts.tapSeul){
      const avantArme = !arme;
      abandonner();
      if(avantArme && opts.surAbandon) opts.surAbandon(ev);
      return;
    }
    ev.preventDefault();
    if(!demarre){
      demarre = true;
      clearTimeout(timer);
      if(!zr) creerFantome();
      if(opts.debut) opts.debut();
    }
    dernier = ev;
    if(!raf) raf = requestAnimationFrame(peindre);
  };
  const nettoyer = () => {
    document.removeEventListener('pointermove', bouge);
    document.removeEventListener('pointerup', fin);
    document.removeEventListener('pointercancel', fin);
    wrap.removeEventListener('scroll', oublierRects);
    clearTimeout(timer);
    if(raf) cancelAnimationFrame(raf);
    if(fantome){ fantome.remove(); cible.remove(); }
    cacherLoupe();
    if(opts.fin) opts.fin();
    if(_glisse === g) _glisse = null;
  };
  const fin = ev => {
    if(ev.pointerId !== id) return;
    const avait = demarre;
    nettoyer();
    if(avait){
      if(ev.type !== 'pointerup') return;
      if(dansZone(ev)){ if(opts.lacherZone) opts.lacherZone(ev); }
      else auLacher(ev.clientX, ev.clientY, ev);
    }
    else if(ev.type === 'pointerup' && auClic) auClic(ev);
  };
  const abandonner = () => { fantome = fantome && (fantome.remove(), cible.remove(), null); nettoyer(); };
  const g = _glisse = {abandonner, id, actif: () => arme && !opts.tapSeul && (demarre || !!opts.appuiLong)};
  document.addEventListener('pointermove', bouge);
  document.addEventListener('pointerup', fin);
  document.addEventListener('pointercancel', fin);
  wrap.addEventListener('scroll', oublierRects);
  return g;
}
// Un glissé armé dans une liste : le doigt ne la fait plus défiler.
document.addEventListener('touchmove', e => { if(_glisse && _glisse.actif()) e.preventDefault(); }, {passive: false});

// ── Loupe de dépôt : un disque décalé au-dessus du doigt, qui montre la
// zone des boards et les faces des pions sur un rayon de 1 hexagone
// autour de l'ancrage visé (1,5 hexagone du centre au bord, pour voir les
// six voisins en entier), et le nom de l'ancrage.
const LOUPE = 200, LOUPE_RAYON_HEX = 1.5;
// Hauteur d'un hexagone (pixels natifs) du board sous le point : un board
// deluxe a des hexagones trois fois plus grands que les 64,5 px standard.
function _hautHexAu(x, y){
  for(const b of _losBoards || []){
    const L = (b.px || 0) + BOARD_MARGIN, T = (b.py || 0) + BOARD_MARGIN;
    if(x < L || y < T || x > L + (b.dispW || 0) || y > T + (b.dispH || 0)) continue;
    const g = geoBoard(b);
    if(g && g.hh) return g.hh;
  }
  return 2 * HEX_HY;
}
// À la souris (hexSeul), la loupe ne montre que l'hexagone survolé, à sa
// forme, à côté du curseur.
const LOUPE_HEX = 150;
// surcouche(g) : dessin en plus, en pixels de #mapInner (le fil de la LOS) ;
// a.texte, s'il est donné, remplace a.label sous la loupe.
function dessinerLoupe(cx, cy, a, rects, hexSeul, surcouche){
  let l = document.getElementById('loupe');
  if(!a){ cacherLoupe(); return; }
  // Diamètre à l'écran : 200 px au doigt (moins sur un écran étroit), 150
  // à la souris.
  const d = hexSeul ? LOUPE_HEX : Math.min(LOUPE, window.innerWidth - 16);
  if(l && l.offsetWidth !== d){ l.remove(); l = null; }
  if(!l){
    l = document.createElement('div');
    l.id = 'loupe'; l.className = 'loupe' + (hexSeul ? ' hexSeul' : '');
    l.innerHTML = `<canvas width="${d * 2}" height="${d * 2}"></canvas><span></span>`;
    l.style.width = d + 'px';
    l.querySelector('canvas').style.width = l.querySelector('canvas').style.height = d + 'px';
    document.body.appendChild(l);
  }
  const cv = l.querySelector('canvas'), g = cv.getContext('2d');
  // Point visé et côté de la zone montrée, en pixels de #mapInner : un
  // hexagone et demi de part et d'autre ; à la souris, l'hexagone seul,
  // centré sur son centre même si l'ancrage est un de ses côtés.
  const hex = _hautHexAu(a.x, a.y) * SC;
  const centre = hexSeul && _hexParNom.get((a.label || '').split('/')[0]) || a;
  const px = centre.x * SC, py = centre.y * SC;
  // Un hexagone à côtés plats en haut et en bas : 2/√3 fois plus large que haut.
  const cote = hexSeul ? hex * 2 / Math.sqrt(3) * 1.04 : 2 * LOUPE_RAYON_HEX * hex;
  const x0 = px - cote / 2, y0 = py - cote / 2, k = cv.width / cote;
  // Repère de la carte dans la loupe, tourné comme la vue autour du centre.
  const repere = () => {
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.translate(cv.width / 2, cv.height / 2); g.rotate(_rotVue * Math.PI / 180); g.translate(-cv.width / 2, -cv.height / 2);
    g.transform(k, 0, 0, k, -x0 * k, -y0 * k);
  };
  g.setTransform(1, 0, 0, 1, 0, 0);
  g.clearRect(0, 0, cv.width, cv.height);
  const contourHex = () => {
    const R = hex / Math.sqrt(3), h = hex / 2;
    g.beginPath();
    [[R, 0], [R / 2, h], [-R / 2, h], [-R, 0], [-R / 2, -h], [R / 2, -h]]
      .forEach(([x, y], i) => g[i ? 'lineTo' : 'moveTo'](px + x, py + y));
    g.closePath();
  };
  repere();
  if(hexSeul){ g.save(); contourHex(); g.clip(); }
  else { g.setTransform(1, 0, 0, 1, 0, 0); g.fillStyle = '#222'; g.fillRect(0, 0, cv.width, cv.height); repere(); }
  for(const b of document.querySelectorAll('#mapInner canvas.boardLayer')){
    const L = parseFloat(b.style.left), T = parseFloat(b.style.top), W = parseFloat(b.style.width), H = parseFloat(b.style.height);
    if(L > x0 + cote || T > y0 + cote || L + W < x0 || T + H < y0) continue;
    try { g.drawImage(b, L, T, W, H); } catch(e){}
  }
  for(const p of pions){
    if(p.deleted || p.cache || !p.face.src || p.face.style.display === 'none') continue;
    const L = parseFloat(p.el.style.left), T = parseFloat(p.el.style.top), W = parseFloat(p.el.style.width) || p.sz;
    if(L > x0 + cote || T > y0 + cote || L + W < x0 || T + W < y0) continue;
    // Droit à l'écran, comme sur la carte.
    try {
      g.save(); g.translate(L + W / 2, T + W / 2); g.rotate(-_rotVue * Math.PI / 180);
      g.drawImage(p.face, -W / 2, -W / 2, W, W);
    } catch(e){} finally { g.restore(); }
  }
  if(hexSeul){
    if(surcouche) surcouche(g, k);
    // Le bord de l'hexagone, et l'ancrage visé s'il est sur un côté.
    g.restore();
    g.strokeStyle = '#d9a441'; g.lineWidth = 3 / k;
    contourHex(); g.stroke();
    if(a !== centre && (a.x !== centre.x || a.y !== centre.y)){
      g.beginPath(); g.arc(a.x * SC, a.y * SC, hex * 0.12, 0, 2 * Math.PI); g.stroke();
    }
    g.setTransform(1, 0, 0, 1, 0, 0);
  } else {
    if(surcouche) surcouche(g, k);
    g.setTransform(1, 0, 0, 1, 0, 0);
    // L'ancrage visé : un cercle de 0,7 hexagone de diamètre.
    g.strokeStyle = '#d9a441'; g.lineWidth = 4;
    g.beginPath(); g.arc(cv.width / 2, cv.height / 2, hex * k * 0.35, 0, 2 * Math.PI); g.stroke();
  }
  const lib = l.querySelector('span');
  const texte = a.texte !== undefined ? a.texte : a.label;
  lib.textContent = texte || '';
  lib.style.display = texte ? '' : 'none';
  if(hexSeul){
    // À droite du curseur, un peu au-dessus ; à gauche faute de place.
    let x = cx + 28, y = cy - d - 12;
    if(x + d > window.innerWidth - 8) x = cx - d - 28;
    if(y < 4) y = cy + 28;
    l.style.left = x + 'px'; l.style.top = y + 'px';
    return;
  }
  // Au-dessus du doigt ; trop près du haut de l'écran, en dessous.
  let haut = cy - d - 60;
  if(haut < 4) haut = cy + 60;
  l.style.left = Math.max(8, Math.min(cx - d / 2, window.innerWidth - d - 8)) + 'px';
  l.style.top = haut + 'px';
}
function cacherLoupe(){ const l = document.getElementById('loupe'); if(l) l.remove(); }

const _rectsCarte = () => ({wrap: document.getElementById('mapWrap').getBoundingClientRect(),
                            inner: document.getElementById('mapInner').getBoundingClientRect()});

// Point de l'écran → pixels VASL natifs, ou null hors de la carte — et
// null aussi sur un panneau ou une fenêtre qui la recouvre, sauf `libre`.
function clientVersCarte(cx, cy, rects, libre){
  const {wrap: wr, inner: mr} = rects || _rectsCarte();
  if(cx < wr.left || cx > wr.right || cy < wr.top || cy > wr.bottom) return null;
  // Tables QRDC hors de la carte tournée : ce n'est pas la carte.
  if(_rotVue && _tablesEl && _tablesEl.isConnected){
    const t = _tablesEl.getBoundingClientRect();
    if(cx >= t.left && cx <= t.right && cy >= t.top && cy <= t.bottom) return null;
  }
  if(!libre){
    const el = document.elementFromPoint(cx, cy);
    if(el && !document.getElementById('mapWrap').contains(el)) return null;
  }
  const c = _vueVersCarte((cx - mr.left) / zoom, (cy - mr.top) / zoom);
  return {x: c.x / SC, y: c.y / SC};
}

// ── Panneau de la pile sélectionnée, du sommet vers le fond.
// La pile entière : c'est le clic sur une pile repliée, la dépose, la
// pose, et la relecture.
function selectionnerPile(k){
  selectionner(k ? pile(k) : []);
  if(k && _pileSel !== k){
    // Pile sans pion sélectionnable (une carte de VASL Tools) : elle reste
    // la pile courante, comme avant.
    const ancien = _pileSel;
    _pileSel = k;
    if(ancien) rendrePile(ancien);
    afficherPile();
  }
}

// Panneau de la pile courante, du sommet (en haut) vers le fond. Chaque
// ligne montre si son pion est sélectionné. Clic : ce pion seul ; Ctrl
// (Cmd) : l'ajouter ou le retirer ; Maj : la plage depuis le dernier clic.
// Glisser une ligne dans la liste change son rang (un seul M/) ; vers la
// carte, elle emporte la sélection si elle en fait partie, sinon ce pion.
let _ancrePile = null;
function afficherPile(){
  purgerSelection();
  majEventail();
  majBarrePile();
  const box = document.getElementById('pile');
  box.innerHTML = '';
  if(!_pileSel) return;
  const s = pile(_pileSel).filter(p => !p.cache).reverse();
  if(!s.length) return;
  // Au PC, les commandes rapides de la sélection (ou de la pile) en tête
  // du panneau ; au doigt, la barre de pile les porte déjà.
  if(_profil === 'pc'){
    const a = document.createElement('div');
    a.className = 'actionsPile';
    a.setAttribute('role', 'toolbar');
    a.setAttribute('aria-label', 'Commandes rapides');
    _rangeeCommandes(a, false);
    if(a.childElementCount) box.appendChild(a);
  }
  const tete = document.createElement('div');
  tete.className = 'bordPile';
  tete.textContent = '▲ Sommet';
  box.appendChild(tete);
  const marque = document.createElement('div');
  marque.className = 'inserPile';
  marque.hidden = true;
  const lignes = [];
  // Vignettes de 56 px au doigt, de 36 px à la souris.
  const coteMini = _profil && _profil !== 'pc' ? 56 : 36;
  for(const p of s){
    const l = document.createElement('div');
    l.className = 'lig' + (_sel.has(p) ? ' sel' : '');
    const nom = p.el.title || p.nom;
    l.title = nom + ' — glisser dans la liste pour changer son rang, vers la carte pour le déplacer';
    l.setAttribute('role', 'option');
    l.setAttribute('aria-label', nom);
    l.setAttribute('aria-selected', String(_sel.has(p)));
    // Copie réduite du pion affiché : fond, couches et orientation.
    const im = document.createElement('div');
    im.className = 'mini';
    const copie = p.el.cloneNode(true);
    copie.classList.remove('sel', 'deplie');
    copie.removeAttribute('title');
    copie.style.cssText = `position:absolute;left:0;top:0;width:${p.sz}px;height:${p.sz}px;`
                        + `transform:scale(${coteMini / p.sz});transform-origin:0 0;opacity:1`;
    im.appendChild(copie);
    // Le pion seul, sans son nom.
    l.append(im);
    l.addEventListener('contextmenu', e => { e.preventDefault(); if(_dernierPointeur === 'mouse') ouvrirMenu(p, e.clientX, e.clientY, 'mouse'); });
    const plusieurs = () => _sel.has(p) && _sel.size > 1;
    // Rang visé : avant ou après la ligne la plus proche du pointeur.
    const vise = ev => {
      let best = null, bd = Infinity;
      for(const x of lignes){
        const r = x.l.getBoundingClientRect();
        const d = Math.abs((r.top + r.bottom) / 2 - ev.clientY);
        if(d < bd){ bd = d; best = {x, r}; }
      }
      return best && {q: best.x.p, r: best.r, dessus: ev.clientY < (best.r.top + best.r.bottom) / 2};
    };
    // Glissé local à la liste : la marque d'insertion suit le pointeur,
    // le lâcher pose le pion à ce rang (un seul M/).
    const rangListe = {
      zone: box,
      dansZone: ev => {
        const v = vise(ev);
        if(!v) return;
        const br = box.getBoundingClientRect();
        marque.hidden = false;
        marque.style.top = ((v.dessus ? v.r.top : v.r.bottom) - br.top + box.scrollTop - 1) + 'px';
      },
      horsZone: () => { marque.hidden = true; },
      fin: () => { marque.hidden = true; },
      lacherZone: ev => {
        const v = vise(ev);
        if(!v || v.q === p) return;
        // La liste va du sommet au fond : « dessus » veut dire juste au-dessus.
        const sans = pile(cle(p)).filter(x => x !== p);
        deplacerDansPile(p, sans.indexOf(v.q) + (v.dessus ? 1 : 0));
      }
    };
    // Au PC, ⋮ est la poignée du rang : la glisser dans la liste déplace
    // le pion dans la pile (un clic ouvre toujours ses commandes), et les
    // flèches l'envoient d'un coup au sommet ou au fond. Au doigt, ⋮ ouvre
    // les commandes et les flèches font un cran.
    const pc = _profil === 'pc';
    const boutons = pc
      ? [['⋮', 'Glisser pour déplacer le pion dans la pile — clic : commandes du pion', e => ouvrirMenu(p, e.clientX, e.clientY, e.pointerType || _dernierPointeur)],
         ['▲', 'Tout en haut (sommet de la pile)', () => rangExtreme(p, +1)],
         ['▼', 'Tout en bas (fond de la pile)', () => rangExtreme(p, -1)]]
      : [['⋮', 'Commandes du pion (clic droit)', e => ouvrirMenu(p, e.clientX, e.clientY, e.pointerType || _dernierPointeur)],
         ['▲', 'Monter', () => changerRang(p, +1)],
         ['▼', 'Descendre', () => changerRang(p, -1)]];
    boutons.push(['✕', 'Retirer de la carte (la sélection, si ce pion en fait partie)',
                  () => plusieurs() ? retirerSelection() : retirer(p)]);
    for(const [txt, titre, f] of boutons){
      const b = document.createElement('button');
      b.textContent = txt; b.title = titre; b.setAttribute('aria-label', titre);
      if(pc && txt === '⋮'){
        // Poignée : le glissé change le rang ; le clic qui suit un glissé
        // n'ouvre pas le menu.
        let glisse = false;
        b.className = 'poigneeRang';
        b.addEventListener('pointerdown', e => {
          e.stopPropagation();
          if(e.button > 0) return;
          glisse = false;
          glisser(e, () => p, q => q.sz * zoom, () => {}, null,
                  {...rangListe, debut: () => { glisse = true; }});
        });
        b.addEventListener('click', e => { if(glisse){ glisse = false; return; } f(e); });
      } else {
        b.addEventListener('pointerdown', e => e.stopPropagation());
        b.addEventListener('click', f);
      }
      l.appendChild(b);
    }
    l.addEventListener('pointerdown', e => {
      if(e.button > 0) return;
      const doigt = !!e.pointerType && e.pointerType !== 'mouse';
      glisser(e, () => sommetDeplace(aEmporter(p, doigt), p), q => q.sz * zoom, (cx, cy) => {
        const c = clientVersCarte(cx, cy);
        if(c) (doigt ? deplacerAuDoigt : deplacer)(aEmporter(p, doigt), c.x, c.y);
      }, ev => {
        if(!selectionnable(p)) return;
        if(ev && (ev.ctrlKey || ev.metaKey)) selectionner([p], 'basculer');
        else if(ev && ev.shiftKey && _ancrePile && s.includes(_ancrePile)){
          const a = s.indexOf(_ancrePile), b = s.indexOf(p);
          selectionner(s.slice(Math.min(a, b), Math.max(a, b) + 1));
          return;
        } else selectionner([p]);
        _ancrePile = p;
      }, {appuiLong: 300, ...rangListe});
    });
    lignes.push({l, p});
    box.appendChild(l);
  }
  const pied = document.createElement('div');
  pied.className = 'bordPile';
  pied.textContent = '▼ Fond';
  box.append(pied, marque);
}

// ═══ APERÇU DES PILES AU SURVOL ═══
// Comme le CounterDetailViewer de VASSAL : quand la souris s'arrête sur
// une pile, une bande montre tous ses pions côte à côte, du fond (à
// gauche) vers le sommet, en grand et sans le décalage d'empilement,
// avec la position et l'effectif. L'aperçu ne prend aucun geste.
const SURVOL_DELAI = 180, SURVOL_ECHELLE = 0.75;
let _survolTimer = null, _survolCache = null, _survolCle = null;

function survolEntre(p, e){
  clearTimeout(_survolCache); _survolCache = null;
  const k = cle(p);
  const t = document.getElementById('survol');
  if(t && _survolCle === k){ _placerSurvol(t, e.clientX, e.clientY); return; }
  clearTimeout(_survolTimer);
  const x = e.clientX, y = e.clientY;
  _survolTimer = setTimeout(() => afficherSurvol(k, x, y), SURVOL_DELAI);
}

// Passer d'un pion à l'autre d'une même pile ne doit pas faire clignoter.
function survolSort(){
  clearTimeout(_survolTimer); _survolTimer = null;
  clearTimeout(_survolCache);
  _survolCache = setTimeout(cacherSurvol, 120);
}

function cacherSurvol(){
  clearTimeout(_survolTimer); _survolTimer = null;
  clearTimeout(_survolCache); _survolCache = null;
  const t = document.getElementById('survol');
  if(t) t.remove();
  _survolCle = null;
}

function afficherSurvol(k, cx, cy){
  const s = pile(k).filter(p => !p.cache);
  if(!s.length) return;
  cacherSurvol();
  const t = document.createElement('div');
  t.id = 'survol'; t.className = 'survol';
  const a = ancragePlusProche(s[0].x, s[0].y);
  // VASL arrondit ses centres autrement que nous : quelques pixels d'écart
  // désignent encore le même ancrage.
  const ici = a && Math.hypot(a.x - s[0].x, a.y - s[0].y) < 4 ? a.label : `${s[0].x}, ${s[0].y}`;
  const tt = document.createElement('div');
  tt.className = 'tt';
  tt.textContent = `${ici} — ${s.length} pion${s.length > 1 ? 's' : ''}`;
  const rang = document.createElement('div');
  rang.className = 'rang';
  const terrain = _terrainSous(a && Math.hypot(a.x - s[0].x, a.y - s[0].y) < 4 ? a : s[0], ici);
  if(terrain) rang.appendChild(terrain);
  for(const p of s){
    // Copie du pion affiché : face, couches et orientation comprises.
    const c = p.el.cloneNode(true);
    c.classList.remove('sel');
    c.removeAttribute('title');
    c.style.cssText = `position:absolute;left:0;top:0;width:${p.sz}px;height:${p.sz}px;`
                    + `transform:scale(${SURVOL_ECHELLE});transform-origin:0 0;`;
    const boite = document.createElement('div');
    const cote = Math.round(p.sz * SURVOL_ECHELLE);
    boite.style.cssText = `position:relative;width:${cote}px;height:${cote}px`;
    boite.appendChild(c);
    // Le pion seul, sans son nom.
    const cel = document.createElement('div');
    cel.className = 'cel';
    cel.append(boite);
    rang.appendChild(cel);
  }
  t.append(tt, rang);
  document.body.appendChild(t);
  _placerSurvol(t, cx, cy);
  _survolCle = k;
}

// Terrain sous la pile, comme l'option « show terrain beneath » du
// CounterDetailViewer de VASSAL : l'hexagone de la pile seul, découpé à
// sa forme dans les boards affichés — overlays et SSR compris — et
// dessiné à l'échelle des pions de l'aperçu. Les voisins n'y figurent
// pas. Le nom du lieu est écrit dessous.
function _terrainSous(c, lieu){
  // Centre de l'hexagone, même si la pile est sur un de ses côtés.
  const centre = _hexParNom.get(String(lieu || '').split('/')[0]) || c;
  // Hexagone à côtés plats en haut et en bas, en pixels de carte affichée.
  const hex = _hautHexAu(centre.x, centre.y) * SC;
  const R = hex / Math.sqrt(3), h = hex / 2;
  const Lw = 2 * R * 1.04, Lh = hex * 1.04;
  const x0 = centre.x * SC - Lw / 2, y0 = centre.y * SC - Lh / 2;
  const cw = Math.round(Lw * SURVOL_ECHELLE), ch = Math.round(Lh * SURVOL_ECHELLE);
  const cv = document.createElement('canvas');
  cv.className = 'terrain';
  cv.width = cw; cv.height = ch;
  const g = cv.getContext('2d');
  const contourHex = () => {
    const kx = cw / Lw, ky = ch / Lh, mx = cw / 2, my = ch / 2;
    g.beginPath();
    [[R, 0], [R / 2, h], [-R / 2, h], [-R, 0], [-R / 2, -h], [R / 2, -h]]
      .forEach(([x, y], i) => g[i ? 'lineTo' : 'moveTo'](mx + x * kx, my + y * ky));
    g.closePath();
  };
  g.save(); contourHex(); g.clip();
  g.fillStyle = '#222'; g.fillRect(0, 0, cw, ch);
  let vu = false;
  for(const b of document.querySelectorAll('#mapInner canvas.boardLayer')){
    const bx = parseFloat(b.style.left) || 0, by = parseFloat(b.style.top) || 0;
    const bw = parseFloat(b.style.width) || b.width, bh = parseFloat(b.style.height) || b.height;
    // Intersection de la zone et du board, en pixels de carte.
    const ix = Math.max(x0, bx), iy = Math.max(y0, by);
    const ax = Math.min(x0 + Lw, bx + bw), ay = Math.min(y0 + Lh, by + bh);
    if(ax <= ix || ay <= iy) continue;
    // Le canvas du board a sa propre résolution (plafonnée sur mobile).
    const rx = b.width / bw, ry = b.height / bh;
    try {
      g.drawImage(b, (ix - bx) * rx, (iy - by) * ry, (ax - ix) * rx, (ay - iy) * ry,
                  (ix - x0) / Lw * cw, (iy - y0) / Lh * ch, (ax - ix) / Lw * cw, (ay - iy) / Lh * ch);
      vu = true;
    } catch(e){ /* canvas illisible : pas de terrain */ }
  }
  g.restore();
  // Le bord de l'hexagone.
  g.strokeStyle = '#000'; g.lineWidth = 1.5;
  contourHex(); g.stroke();
  if(!vu) return null;
  const cel = document.createElement('div');
  cel.className = 'cel';
  const t = document.createElement('div');
  t.className = 'lieuT';
  t.textContent = lieu;
  cel.append(cv, t);
  return cel;
}

// Au-dessus du curseur, sans sortir de la fenêtre.
function _placerSurvol(t, cx, cy){
  let x = cx - t.offsetWidth/2, y = cy - t.offsetHeight - 16;
  x = Math.max(4, Math.min(x, window.innerWidth - t.offsetWidth - 4));
  if(y < 4) y = cy + 24;
  t.style.left = x + 'px'; t.style.top = y + 'px';
}

// Durée d'un appui long (ms) : menu d'une vignette de l'éventail, menu
// long d'une icône. Sur un pion de la carte, l'appui maintenu ne sert
// qu'à le saisir (DELAI_SAISIE_TACTILE) : le menu passe par l'éventail,
// ouvert d'un double appui sur la pile.
const APPUI_LONG = 500;

