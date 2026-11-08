// ═══════════════════════════════════════════════════════════════
// DÉS — d'après ASLDiceBot et ASLChatter (VASL)
// Le jet est un message de chat au format d'ASLDiceBot, que VASL relit
// et décore lui-même :
//   *** (IFT DR) 3,1 ***   <Pierre>      Axis SAN    [1 / 8   avg   6,62 (6,62)]    (01.51)
// Le premier dé est le COLORÉ, le second le blanc. Le SAN se contrôle
// sur le total du DR original (A14.2), comme IsSpecialDiceRoll().
// ═══════════════════════════════════════════════════════════════
const DE_COLORE = 'R', DE_BLANC = 'W';     // DC<n>_<couleur>.png du module

// ═══ UTILISATEURS ET PROPRIÉTÉ DES PIONS ═══
// Comme dans VASSAL, un joueur a un NOM, qui s'inscrit dans la propriété
// Owner des pions qu'il pose ou déplace ($PlayerName$), et un MOT DE
// PASSE, qui est l'identifiant de ses masques : l'état des traits conceal
// et hide d'un fichier VASL porte ce mot de passe. Le saisir prouve donc
// qu'on est ce joueur, sans qu'aucune liste de mots de passe ne soit lue.
//
// Comme dans VASL, la propriété ne joue que pour les masques : tout le
// monde déplace tous les pions, mais un pion dissimulé (« ? ») ou en HIP
// n'est visible et commandable que par le joueur qui l'a masqué.
// L'administrateur voit et commande tout : arbitrage, relecture, partie
// solitaire.
//
// Rien de tout cela ne résiste à quelqu'un qui ouvre la console : tout
// s'exécute dans son navigateur. Le contrôle évite les regards
// distraits, pas la triche — il faudrait un serveur pour cela.
const NOM_ADMIN = 'Administrateur';
// Le mot de passe administrateur n'est pas dans le code : seulement son
// empreinte (_empreinte, FNV-1a), à laquelle on compare celle de la
// saisie. Cela empêche de le lire dans le dépôt publié, rien de plus :
// quiconque modifie le JavaScript chez lui se donne les droits
// d'administrateur, et une empreinte de 32 bits se retrouve par essais.
// Une vraie protection demanderait un arbitre de confiance, hors de
// portée d'une PWA sans serveur.
const ADMIN_EMPREINTE = 'c1e00cff';
const estMdpAdmin = mdp => _empreinte(mdp) === ADMIN_EMPREINTE;
let _utilisateur = null;              // {nom, mdp} ; null = observateur
let _admin = false;

// Nom du joueur : Owner, chat, dés, rapports et commandes du log.
const joueur = () => _admin ? NOM_ADMIN : (_utilisateur ? _utilisateur.nom : 'web');
// Identifiant des masques (conceal, hide) : le mot de passe.
const idJoueur = () => _admin ? 'admin' : (_utilisateur ? _utilisateur.mdp : 'web');

// Vrai si l'utilisateur voit et commande ce que masque l'identifiant id.
const voitMasque = id => _admin || id === idJoueur();

// ── Dévoilement par l'adversaire : ObscurableOptions de VASSAL.
// La préférence « OpponentUnmaskable » met l'identifiant du joueur dans
// la liste des masques que l'adversaire peut lever. La partie la garde
// dans une commande « UNMASK\t<id>\t<id>… » (SetAllowed), écrite dans le
// .vsav et l'état de départ du .vlog. Comme Concealable.isMaskable de
// VASL, l'adversaire ne voit toujours que le « ? » : il n'a que la
// commande Conceal, qui démasque l'unité. Le HIP (Hideable) n'en tient
// pas compte : il reste invisible.
const UNMASK = 'UNMASK\t';
const CLE_DEMASQUABLE = 'aslweb.OpponentUnmaskable';
let _demasquables = new Set();
function lireDemasquables(segs){
  const seg = segs.find(x => x.startsWith(UNMASK));
  return new Set(seg ? _decoupeSeq(seg.substring(UNMASK.length), '\t').filter(x => x) : []);
}
// Réécrit la commande UNMASK de l'état chargé, d'où partent .vsav et .vlog.
function _ecrireDemasquables(){
  if(!_source) return;
  const i = _source.findIndex(x => x.startsWith(UNMASK));
  const seg = UNMASK + [..._demasquables].map(x => x.replace(/\t/g, '\\\t')).join('\t');
  if(i >= 0) _source[i] = seg;
  else {
    const iFin = _source.lastIndexOf('end_save');
    _source.splice(iFin >= 0 ? iFin : _source.length, 0, seg);
  }
  // Un log en cours part de cet état : son départ suit (SetAllowed).
  if(_logDepart){
    const j = _logDepart.findIndex(x => x.startsWith(UNMASK));
    if(j >= 0) _logDepart[j] = seg;
    else { const jFin = _logDepart.lastIndexOf('end_save'); _logDepart.splice(jFin >= 0 ? jFin : _logDepart.length, 0, seg); }
  }
}
// Le joueur permet-il à l'adversaire de démasquer ses unités ? Comme
// une préférence de VASSAL, le choix vaut aussi avant l'identification :
// il s'applique dès que le joueur se fait reconnaître.
const demasquablesParMoi = () => (_utilisateur || _admin) ? _demasquables.has(idJoueur())
                                                         : _lireLocal(CLE_DEMASQUABLE, '0') === '1';
function permettreDemasquage(oui){
  _ecrireLocal(CLE_DEMASQUABLE, oui ? '1' : '0');
  if(!_utilisateur && !_admin) return;
  const id = idJoueur();
  if(oui === _demasquables.has(id)) return;
  if(oui) _demasquables.add(id); else _demasquables.delete(id);
  _ecrireDemasquables();
  chatLog(oui ? '🔓 Vos adversaires peuvent démasquer vos unités dissimulées'
              : '🔒 Vos adversaires ne peuvent plus démasquer vos unités dissimulées');
}
// Unité dissimulée par un autre joueur qui l'a permis : seule la commande
// Conceal lui reste (Obscurable.keyEvent → myKeyEvent).
function demasquable(p){
  if(!p || !p.masqueProprio || voitMasque(p.masqueProprio) || !_demasquables.has(p.masqueProprio)) return false;
  const autrui = id => !!id && id !== 'web' && !voitMasque(id);
  return !autrui(p.hipProprio) && !autrui(p.compteurProprio);
}
const _cmdDemasque = p => { const te = tePion(p); return te ? commandesDe(te.type).cmds.find(c => c.demasque) : null; };

const CLE_COMPTES = 'aslweb.comptes';
const CLE_DERNIER = 'aslweb.dernierUtilisateur';
const _lireLocal = (k, def) => { try { const v = localStorage.getItem(k); return v === null ? def : v; } catch(e){ return def; } };
const _ecrireLocal = (k, v) => { try { localStorage.setItem(k, v); return true; } catch(e){ return false; } };

// Comptes créés sur ce poste. Le mot de passe n'y est pas en clair :
// une empreinte FNV-1a suffit à refuser une faute de frappe, et
// crypto.subtle manque quand la page est ouverte en file://.
function _empreinte(t){
  let h = 0x811c9dc5;
  for(const c of String(t)){ h ^= c.codePointAt(0); h = Math.imul(h, 0x01000193) >>> 0; }
  return h.toString(16).padStart(8, '0');
}
function comptesLocaux(){
  try {
    const l = JSON.parse(_lireLocal(CLE_COMPTES, '[]'));
    return Array.isArray(l) ? l.filter(c => c && c.nom && c.h) : [];
  } catch(e){ return []; }
}
const _memeNom = (a, b) => !!a && !!b && a.toLowerCase() === b.toLowerCase();
function enregistrerCompte(nom, mdp){
  const l = comptesLocaux();
  const c = l.find(x => _memeNom(x.nom, nom));
  if(c) c.h = _empreinte(mdp); else l.push({nom, h: _empreinte(mdp)});
  return _ecrireLocal(CLE_COMPTES, JSON.stringify(l));
}
function oublierCompte(nom){
  memoriserCamp(nom, null);
  return _ecrireLocal(CLE_COMPTES, JSON.stringify(comptesLocaux().filter(x => !_memeNom(x.nom, nom))));
}
// Le nom est écrit tel quel dans l'état Owner : « ; », « / », « \ »,
// « , » et la tabulation sont des séparateurs de VASSAL.
const nomValide = nom => /^[^;/\\\t,<>&"]{1,24}$/.test(nom) && !_memeNom(nom, NOM_ADMIN) && !_memeNom(nom, 'web');

// Joueurs du fichier : un par identifiant de masque, nommé d'après
// l'Owner majoritaire des pions qu'il masque.
function joueursDuFichier(){
  const par = new Map();
  for(const p of vivants()){
    for(const id of [p.masqueProprio, p.hipProprio, p.compteurProprio]){
      if(!id || id === 'web' || id === 'admin') continue;
      if(!par.has(id)) par.set(id, {id, n: 0, noms: new Map()});
      const f = par.get(id);
      f.n++;
      if(p.proprietaire) f.noms.set(p.proprietaire, (f.noms.get(p.proprietaire) || 0) + 1);
    }
  }
  return [...par.values()].map((f, i) => {
    const nom = [...f.noms.entries()].sort((a, b) => b[1] - a[1])[0];
    return {id: f.id, n: f.n, nom: nom ? nom[0] : null, etiquette: 'Joueur ' + (i + 1)};
  }).sort((a, b) => b.n - a.n);
}

function seConnecter(u, admin){
  _utilisateur = admin ? null : u;
  _admin = !!admin;
  // ObscurableOptions.setup : la préférence du joueur vaut pour la partie.
  if((_utilisateur || _admin) && _lireLocal(CLE_DEMASQUABLE, '0') === '1') permettreDemasquage(true);
  majUtilisateur();
  if(typeof majAideChat === 'function') majAideChat();
  _netMajInfo();
  // Ce qu'on voit d'une unité dissimulée ou en HIP dépend de qui l'on est.
  vivants().forEach(p => majApparence(p));
  if(_pileSel) afficherPile();
  chatLog(_admin ? '👁️ Administrateur : tous les pions dissimulés et HIP sont visibles'
    : (_utilisateur ? `👤 ${_utilisateur.nom}${monCamp() ? ` (${CAMPS[monCamp()]})` : ''} — ${nbMasques(_utilisateur.mdp)} pion(s) masqué(s) à vous`
                    : '👤 Observateur : les pions dissimulés et HIP restent cachés'));
}

function majUtilisateur(){ rafraichirActions(['utilisateur']); }

// ── Accès aux pions masqués.
// « web » est l'identifiant d'avant les comptes : il ne réserve rien.
const _masqueDe = (p, id) => !!id && (p.masqueProprio === id || p.hipProprio === id || p.compteurProprio === id);
const nbMasques = id => vivants().filter(p => _masqueDe(p, id)).length;
// Avec une touche : celle de Conceal reste permise sur une unité que son
// joueur laisse démasquer.
function accesRefuse(p, touche){
  if(!p || _admin) return false;
  if(touche && demasquable(p)){ const c = _cmdDemasque(p); if(c && c.touche === touche) return false; }
  const autrui = id => !!id && id !== 'web' && !voitMasque(id);
  return autrui(p.masqueProprio) || autrui(p.hipProprio) || autrui(p.compteurProprio);
}
// Vrai si la commande est refusée ; le refus est dit au journal.
function refuser(p, touche){
  if(!accesRefuse(p, touche)) return false;
  chatLog(`🔒 ${p.el.title || p.nom} : masqué par un autre joueur, aucune commande`
    + ` — vous êtes ${_utilisateur ? _utilisateur.nom : 'observateur'}`);
  return true;
}

// ── Camps (Axe / Alliés) : données de la seule version HTML.
// VASL ne lie pas un joueur à un camp. Le camp se choisit donc à
// l'identification et se garde sur ce poste, par utilisateur ; il n'entre
// ni dans le .vsav, ni dans le .vlog, ni dans les messages réseau.
// Le camp d'un pion vient de sa nationalité, avec les codes du module
// (dossiers Axis O/B et Allied O/B de la palette) : celle du trait conceal
// ou concealment, sinon celle du premier prototype national (geINF,
// ruOTtur…). Les marqueurs, les Suédois (dans les deux O/B) et les pions
// partagés n'ont pas de camp.
// Déplacer un pion du camp adverse demande une confirmation, au PC comme
// au doigt. Rien n'est demandé à l'administrateur, à l'observateur, ni à
// qui a choisi « Les deux ».
const CAMPS = {axe: 'Axe', allies: 'Alliés', tous: 'Les deux'};
const NATIONS_AXE = new Set(['ge', 'ss', 'ax', 'it', 'ja', 'fi', 'hu', 'nk', 'cc']);
const NATIONS_ALLIES = new Set(['al', 'am', 'br', 'ch', 'fr', 'ru', 'un', 'sk']);
const CLE_CAMPS = 'aslweb.camps';
const campDeNation = n => NATIONS_AXE.has(n) ? 'axe' : NATIONS_ALLIES.has(n) ? 'allies' : null;
function nationPion(p){
  const m = infoDissimulation(p);
  if(m.nation) return m.nation;
  const te = tePion(p);
  if(!te) return null;
  for(const t of decoderTraits(te.type)){
    const r = /^prototype;([a-z]{2})[A-Z0-9(]/.exec(t);
    if(r && campDeNation(r[1])) return r[1];
  }
  return null;
}
const campPion = p => campDeNation(nationPion(p));
// Camp de l'utilisateur connecté ; null : aucun contrôle.
const monCamp = () => _admin ? 'tous' : (_utilisateur && CAMPS[_utilisateur.camp] ? _utilisateur.camp : null);

// Camps mémorisés sur ce poste, par nom d'utilisateur (sans la casse).
function _campsLocaux(){
  try {
    const o = JSON.parse(_lireLocal(CLE_CAMPS, '{}'));
    return o && typeof o === 'object' && !Array.isArray(o) ? o : {};
  } catch(e){ return {}; }
}
const campMemorise = nom => { const c = nom ? _campsLocaux()[nom.toLowerCase()] : null; return CAMPS[c] ? c : null; };
function memoriserCamp(nom, camp){
  if(!nom) return;
  const o = _campsLocaux();
  if(CAMPS[camp]) o[nom.toLowerCase()] = camp; else delete o[nom.toLowerCase()];
  _ecrireLocal(CLE_CAMPS, JSON.stringify(o));
}
// Camp d'un joueur du fichier, d'après la nationalité des pions qu'il masque.
function campDeduit(id){
  const n = {axe: 0, allies: 0};
  for(const p of vivants()) if(_masqueDe(p, id)){ const c = campPion(p); if(c) n[c]++; }
  return n.axe > n.allies ? 'axe' : n.allies > n.axe ? 'allies' : null;
}

// Vrai si le déplacement peut se faire tout de suite : aucun pion adverse.
// Sinon le déplacement est bloqué : une fenêtre (la même au PC et au
// doigt) demande la confirmation ; « Déplacer quand même » appelle
// refaire(), « Annuler » (ou Échap) laisse les pions où ils sont.
function pionsAdverses(liste){
  const moi = monCamp();
  if(!moi || moi === 'tous') return [];
  return liste.filter(p => { const c = campPion(p); return c && c !== moi; });
}
function confirmerCampAdverse(liste, refaire){
  const adverses = pionsAdverses(liste);
  if(!adverses.length) return true;
  const camp = CAMPS[monCamp() === 'axe' ? 'allies' : 'axe'];
  const noms = adverses.slice(0, 5).map(p => accesRefuse(p) ? T('Pion dissimulé') : p.nom).join(', ')
             + (adverses.length > 5 ? '…' : '');
  return _fenetreConfirmerDeplacement('camp', T('Pion du camp adverse'),
    T(`${adverses.length} pion(s) du camp adverse (${camp}) : ${noms}. Les déplacer quand même ?`),
    `↩️ Déplacement annulé : ${adverses.length} pion(s) du camp ${camp}`, refaire);
}

// Fenêtre de confirmation d'un déplacement bloqué (la même au PC et au
// doigt) : « Déplacer quand même » (ou Entrée) appelle refaire(),
// « Annuler » (ou Échap) laisse les pions où ils sont. Identifiants :
// fen<Nom>, <nom>Oui, <nom>Non. Rend toujours faux (déplacement suspendu).
function _fenetreConfirmerDeplacement(nom, titre, texte, annule, refaire){
  const N = nom[0].toUpperCase() + nom.slice(1);
  const ancienne = document.getElementById('fen' + N);
  if(ancienne) ancienne.remove();
  const w = document.createElement('div');
  w.id = 'fen' + N; w.className = 'fenetre';
  w.innerHTML = `
    <div class="ft">${_esc(titre)}</div>
    <div class="fc">
      <p>${_esc(texte)}</p>
      <div class="fb">
        <button id="${nom}Oui" class="prim">${T('Déplacer quand même')}</button>
        <button id="${nom}Non">${T('Annuler')}</button>
      </div>
    </div>`;
  document.body.appendChild(w);
  const fermer = () => { w.remove(); document.removeEventListener('keydown', clavier, true); };
  const non = () => { fermer(); chatLog(annule); };
  const clavier = ev => {
    if(ev.key === 'Escape'){ ev.preventDefault(); ev.stopImmediatePropagation(); non(); }
    else if(ev.key === 'Enter'){ ev.preventDefault(); ev.stopImmediatePropagation(); fermer(); refaire(); }
  };
  document.addEventListener('keydown', clavier, true);
  w.querySelector(`#${nom}Oui`).onclick = () => { fermer(); refaire(); };
  w.querySelector(`#${nom}Non`).onclick = non;
  w.querySelector(`#${nom}Oui`).focus();
  return false;
}

// ── Déplacement anormal, au doigt : plus de DISTANCE_ANORMALE hexagones
// entre la place d'un pion et la destination (doigt qui glisse trop loin,
// appui à côté en « Déplacer… »). Le déplacement attend la confirmation ;
// la souris, les commandes et la relecture n'y passent pas.
const DISTANCE_ANORMALE = 6;
// Portée en hexagones entre deux points de la carte, comme LosMap.range
// quand la carte LOS est prête (elle se construit à l'ouverture de la
// partie) ; sinon d'après la grille du board le plus proche, à la taille
// de ses hexagones (56,25 × 64,5 px, ou plus grands sur les boards HD).
// null hors des boards.
function distanceHex(x1, y1, x2, y2){
  const m = _losPrete;
  if(m){
    const a = _hexDe(m, x1, y1), b = _hexDe(m, x2, y2);
    return a && b ? LosMap.range(a, b, m.getMapConfiguration()) : null;
  }
  const proche = (x, y) => {
    let best = null, bd = Infinity;
    for(const h of _hexTable || []){ const d = Math.hypot(h.x - x, h.y - y); if(d < bd){ bd = d; best = h; } }
    return best && {h: best, d: bd};
  };
  const a = proche(x1, y1), b = proche(x2, y2);
  if(!a || !b) return null;
  // Colonnes verticales : le voisin NE est à (hw, −hh/2), le côté N à hh/2.
  const hw = Math.abs(2 * (a.h.cotes[2].x - a.h.x)), hh = Math.abs(2 * (a.h.y - a.h.cotes[1].y));
  if(!hw || !hh || a.d > hh || b.d > hh) return null;
  const col = Math.round(Math.abs(b.h.x - a.h.x) / hw);
  return col + Math.max(0, Math.round((Math.abs(b.h.y - a.h.y) - col * hh / 2) / hh));
}
function deplacerAuDoigt(liste, x, y){
  liste = liste.filter(p => !p.cache && !p.deleted);
  if(!liste.length) return;
  const pos = positionDepot(x, y);
  const loin = Math.max(-1, ...liste.map(p => distanceHex(p.x, p.y, pos.x, pos.y) ?? -1));
  if(loin <= DISTANCE_ANORMALE) return deplacer(liste, x, y);
  const noms = liste.slice(0, 5).map(p => accesRefuse(p) ? T('Pion dissimulé') : p.nom).join(', ')
             + (liste.length > 5 ? '…' : '');
  _fenetreConfirmerDeplacement('distance', T('Déplacement inhabituel'),
    T(`Déplacement de ${loin} hexagones (plus de ${DISTANCE_ANORMALE}) : ${noms}${noms.endsWith('…') ? '' : '.'} Le faire quand même ?`),
    `↩️ Déplacement annulé : ${loin} hexagones`, () => deplacer(liste, x, y));
}

// ── Boîte d'identification, ouverte à chaque carte chargée.
// Choisir une ligne ne donne aucun accès : c'est le mot de passe qui
// prouve l'identité.
//   · joueur du fichier : son mot de passe est l'identifiant de ses masques
//   · compte du poste   : comparé à l'empreinte enregistrée
//   · administrateur    : comparé à ADMIN_EMPREINTE
const _esc = t => String(t).replace(/[&<>"]/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'})[c]);

function demanderUtilisateur(){
  fermerFenetres();
  const fiches = joueursDuFichier();
  const comptes = comptesLocaux();
  const compteDe = nom => comptes.find(c => _memeNom(c.nom, nom)) || null;
  const entrees = [{type: 'admin', nom: NOM_ADMIN, info: 'voit tous les pions masqués'}];
  const deja = nom => entrees.some(e => _memeNom(e.nom, nom));
  for(const f of fiches){
    if(f.nom && deja(f.nom)) continue;
    entrees.push({type: 'joueur', nom: f.nom || f.etiquette, fiche: f, compte: compteDe(f.nom),
                  info: `${f.n} pion(s) masqué(s)`});
  }
  for(const c of comptes) if(!deja(c.nom)) entrees.push({type: 'joueur', nom: c.nom, compte: c, info: 'compte de ce poste'});
  entrees.push({type: 'nouveau', nom: 'Nouvel utilisateur…', info: ''});

  // Présélection : l'utilisateur de la dernière fois, sinon le premier joueur.
  const dernier = _lireLocal(CLE_DERNIER, '');
  let defaut = entrees.findIndex(e => e.type !== 'nouveau' && _memeNom(e.nom, dernier));
  if(defaut < 0) defaut = entrees.length > 2 ? 1 : entrees.length - 1;

  const w = document.createElement('div');
  w.id = 'fenUtil'; w.className = 'fenetre';
  w.innerHTML = `
    <div class="ft">Identification</div>
    <div class="fc">
      <p>Choisir l'utilisateur, puis saisir son mot de passe. Les pions dissimulés
        et HIP ne sont visibles que de leur joueur${fiches.length ? ` ; cette carte en
        contient de ${fiches.length} joueur(s)` : ''}.</p>
      <label>Utilisateur<select id="utSel">${entrees.map((e, i) =>
        `<option value="${i}"${i === defaut ? ' selected' : ''}>${_esc(e.nom)}${e.info ? ' — ' + _esc(e.info) : ''}</option>`).join('')}</select></label>
      <label id="utNomL">Nom du nouvel utilisateur<input id="utNom" maxlength="24" autocomplete="off" placeholder="nom affiché"></label>
      <label>Mot de passe<input id="utMdp" type="password" autocomplete="off" placeholder="mot de passe"></label>
      <label id="utCampL">Camp<select id="utCamp"><option value="">— choisir —</option>${
        Object.entries(CAMPS).map(([v, l]) => `<option value="${v}">${l}</option>`).join('')}</select><span id="utCampInd"></span></label>
      <div class="ferr" id="utErr"></div>
      <div class="fb">
        <button id="utOk" class="prim">Se connecter</button>
        <button id="utObs">Observateur</button>
        <button id="utGerer">Utilisateurs…</button>
      </div>
      <p class="fnote">Pour un joueur VASL, le mot de passe est celui de VASL : il ouvre ses
        pions dissimulés et en HIP.</p>
      <p class="fnote">Déplacer un pion du camp adverse demande une confirmation ; « Les deux » ne
        demande rien. Le camp reste sur ce poste, hors des fichiers VASL.</p>
    </div>`;
  document.body.appendChild(w);
  const $ = id => document.getElementById(id);
  const sel = $('utSel'), err = $('utErr');
  const majNom = () => {
    const e = entrees[+sel.value], nouveau = e.type === 'nouveau';
    $('utNomL').style.display = nouveau ? '' : 'none';
    // Camp : choix explicite à chaque connexion, jamais présélectionné ;
    // le dernier camp de ce nom sur ce poste (ou celui des pions qu'il
    // masque) n'est qu'indiqué.
    $('utCampL').style.display = e.type === 'admin' ? 'none' : '';
    $('utCamp').value = '';
    const indice = nouveau || e.type === 'admin' ? null
      : campMemorise(e.fiche ? (e.fiche.nom || e.nom) : e.nom) || (e.fiche && campDeduit(e.fiche.id));
    $('utCampInd').textContent = indice ? T(`Dernier camp : ${CAMPS[indice]}`) : '';
    err.textContent = '';
    (nouveau ? $('utNom') : $('utMdp')).focus();
  };
  sel.addEventListener('change', majNom);
  const connecter = (nom, mdp, camp) => {
    _ecrireLocal(CLE_DERNIER, nom);
    memoriserCamp(nom, camp);
    w.remove();
    seConnecter({nom, mdp, camp}, false);
  };
  $('utOk').onclick = () => {
    const e = entrees[+sel.value], mdp = $('utMdp').value;
    if(e.type === 'admin'){
      if(!estMdpAdmin(mdp)){ err.textContent = 'Mot de passe administrateur incorrect.'; return; }
      _ecrireLocal(CLE_DERNIER, NOM_ADMIN);
      w.remove();
      seConnecter(null, true);
      return;
    }
    if(!mdp){ err.textContent = 'Saisir le mot de passe.'; return; }
    const camp = $('utCamp').value;
    if(!CAMPS[camp]){ err.textContent = 'Choisir le camp.'; $('utCamp').focus(); return; }
    if(estMdpAdmin(mdp) || mdp === 'web' || mdp === 'admin'){ err.textContent = 'Ce mot de passe est réservé.'; return; }
    if(e.type === 'nouveau'){
      const nom = $('utNom').value.trim();
      if(!nomValide(nom)){ err.textContent = 'Nom requis : 24 caractères au plus, sans ; / \\ , < > & ".'; return; }
      if(entrees.some(x => x.type !== 'nouveau' && _memeNom(x.nom, nom))){
        err.textContent = 'Cet utilisateur existe déjà : le choisir dans la liste.'; return;
      }
      enregistrerCompte(nom, mdp);
      chatLog(`➕ Utilisateur créé : ${nom}`);
      connecter(nom, mdp, camp);
      return;
    }
    // Le mot de passe doit être celui de l'utilisateur DÉSIGNÉ.
    const parMasque = e.fiche && mdp === e.fiche.id;
    const parCompte = e.compte && _empreinte(mdp) === e.compte.h;
    if(!parMasque && !parCompte){ err.textContent = 'Mot de passe incorrect pour cet utilisateur.'; return; }
    // Un nom réel encore inconnu du poste y devient un compte : il sera
    // reconnu la prochaine fois, même sans pion masqué.
    const nom = e.fiche ? (e.fiche.nom || e.nom) : e.nom;
    if(!e.compte && nomValide(nom)) enregistrerCompte(nom, mdp);
    connecter(nom, mdp, camp);
  };
  $('utObs').onclick = () => { _ecrireLocal(CLE_DERNIER, ''); w.remove(); seConnecter(null, false); };
  $('utGerer').onclick = () => { w.remove(); gererUtilisateurs(); };
  for(const c of [$('utMdp'), $('utNom')])
    c.addEventListener('keydown', ev => {
      if(ev.key === 'Enter') $('utOk').click();
      if(ev.key === 'Escape') w.remove();
    });
  majNom();
}

// ── Gestion des utilisateurs : comptes du poste et joueurs du fichier,
// avec le nombre de pions que chacun tient masqués.
function gererUtilisateurs(){
  fermerFenetres();
  const fiches = joueursDuFichier();
  const lignes = [];
  for(const f of fiches)
    lignes.push({nom: f.nom || f.etiquette, n: f.n, local: false, moi: !!_utilisateur && f.id === _utilisateur.mdp});
  for(const c of comptesLocaux()){
    const l = lignes.find(x => _memeNom(x.nom, c.nom));
    if(l) l.local = true;
    else lignes.push({nom: c.nom, n: 0, local: true, moi: !!_utilisateur && _memeNom(c.nom, _utilisateur.nom)});
  }
  const w = document.createElement('div');
  w.id = 'fenUtil'; w.className = 'fenetre';
  w.innerHTML = `
    <div class="ft">Utilisateurs</div>
    <div class="fc">
      <p>Connecté : <b>${_esc(_admin ? NOM_ADMIN : (_utilisateur ? _utilisateur.nom : 'observateur'))}</b>.</p>
      <table><tr><th>Utilisateur</th><th>Camp</th><th>Pions masqués</th><th></th></tr>
        ${lignes.map(l => `<tr><td>${_esc(l.nom)}${l.moi ? ' <b>(vous)</b>' : ''}</td>
          <td>${CAMPS[(l.moi && monCamp()) || campMemorise(l.nom)] || '—'}</td><td class="nb">${l.n}</td>
          <td>${l.local ? `<button class="oubli" data-nom="${_esc(l.nom)}">Oublier</button>` : '<i>fichier</i>'}</td></tr>`).join('')
          || '<tr><td colspan="4"><i>Aucun utilisateur</i></td></tr>'}</table>
      <div class="fb">
        <button id="utChanger" class="prim">Changer d'utilisateur…</button>
        <button id="utFermer">Fermer</button>
      </div>
      <p class="fnote">Comme dans VASL, tous les pions se déplacent librement ; un pion
        dissimulé ou en HIP n'est visible et commandable que par son joueur et par
        l'administrateur.</p>
    </div>`;
  document.body.appendChild(w);
  w.querySelectorAll('.oubli').forEach(b => b.onclick = () => {
    oublierCompte(b.dataset.nom);
    chatLog(`➖ Compte oublié sur ce poste : ${b.dataset.nom}`);
    gererUtilisateurs();
  });
  document.getElementById('utChanger').onclick = () => demanderUtilisateur();
  document.getElementById('utFermer').onclick = () => w.remove();
}

function fermerFenetres(){
  const f = document.getElementById('fenUtil');
  if(f) f.remove();
}
const _d6 = () => {
  // Tirage uniforme sans biais de modulo.
  const t = new Uint8Array(1);
  do { crypto.getRandomValues(t); } while(t[0] >= 252);
  return 1 + (t[0] % 6);
};

// ── Informations de scénario (ScenInfo, segment « INFO\t ») :
//   qui joue en premier, joueur, tour, ELR Axis, SAN Axis, ELR Allied,
//   SAN Allied, piège Axis, piège Allied, météo…
let _infoEtat = 'Axis\tAxis\t1\t?\t?\t?\t?\t?\t?';
let _infoModifiee = false;
const INFO_SAN_AXIS = 4, INFO_SAN_ALLIED = 6, INFO_BB_AXIS = 7, INFO_BB_ALLIED = 8;

function _infoChamp(i){
  return (_infoEtat.split('\t')[i] || '?').replace(/\\/g, '').trim();
}
function _infoFixer(i, v){
  const f = _infoEtat.split('\t');
  while(f.length <= i) f.push('?');
  f[i] = v;
  _infoEtat = f.join('\t');
}

function lireInfo(segs){
  const seg = segs.find(x => x.startsWith('INFO\t'));
  _infoEtat = seg ? seg.substring(5) : 'Axis\tAxis\t1\t?\t?\t?\t?\t?\t?';
  _infoModifiee = false;
  majChampsInfo();
  const sa = _infoChamp(INFO_SAN_AXIS), sl = _infoChamp(INFO_SAN_ALLIED);
  if(seg) chatLog(`🎯 SAN Axis ${sa} · Allied ${sl}`);
}

function majChampsInfo(){
  const n = v => /^\d+$/.test(v) ? v : '';
  document.getElementById('sanAxis').value = n(_infoChamp(INFO_SAN_AXIS));
  document.getElementById('sanAllied').value = n(_infoChamp(INFO_SAN_ALLIED));
  const b = v => ['A','B','C'].includes(v) ? v : '?';
  document.getElementById('bbAxis').value = b(_infoChamp(INFO_BB_AXIS));
  document.getElementById('bbAllied').value = b(_infoChamp(INFO_BB_ALLIED));
}

// Saisie : VASL diffuse et consigne la fiche entière (SetInfo).
function changerInfo(){
  const v = id => document.getElementById(id).value.trim();
  _avantAction();
  _infoFixer(INFO_SAN_AXIS, v('sanAxis') || '?');
  _infoFixer(INFO_SAN_ALLIED, v('sanAllied') || '?');
  _infoFixer(INFO_BB_AXIS, v('bbAxis'));
  _infoFixer(INFO_BB_ALLIED, v('bbAllied'));
  _infoModifiee = true;
  _logCmd('INFO\t' + _infoEtat);
  chatLog(`🎯 SAN Axis ${_infoChamp(INFO_SAN_AXIS)} · Allied ${_infoChamp(INFO_SAN_ALLIED)}`
        + ` — pièges Axis ${_infoChamp(INFO_BB_AXIS)} · Allied ${_infoChamp(INFO_BB_ALLIED)}`);
}

// IsSpecialDiceRoll() : SAN des deux camps sur le total, pièges sur 11-12.
function _special(total){
  const san = c => { const v = parseInt(_infoChamp(c), 10); return !isNaN(v) && v === total; };
  const ax = san(INFO_SAN_AXIS), al = san(INFO_SAN_ALLIED);
  if(ax && al) return 'Axis/Allied SAN    ';
  if(ax) return 'Axis SAN    ';
  if(al) return 'Allied SAN    ';
  if(total >= 11){
    const bt = c => { const v = _infoChamp(c); return v === 'A' || (v === 'B' && total === 11) || (v === 'C' && total === 12); };
    const bx = bt(INFO_BB_AXIS), bl = bt(INFO_BB_ALLIED);
    if(bx && bl) return 'Axis/Allied Booby Trap    ';
    if(bx) return 'Axis Booby Trap   ';
    if(bl) return 'Allied Booby Trap   ';
  }
  return '';
}

// ── Statistiques de la session, par catégorie et au total (DiceStats).
const _stats = new Map();
function _stat(cle){
  if(!_stats.has(cle)) _stats.set(cle, {n: 0, somme: 0, detail: new Map()});
  return _stats.get(cle);
}
function _ajouterStat(cle, valeur){
  const s = _stat(cle);
  s.n++; s.somme += valeur;
  s.detail.set(valeur, (s.detail.get(valeur) || 0) + 1);
}
const _fmt2 = v => v.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2});
const _moy = cle => { const s = _stats.get(cle); return s && s.n ? _fmt2(s.somme / s.n) : '-'; };
const _surTotal = (cle, v) => { const s = _stats.get(cle); return s ? `${s.detail.get(v) || 0} / ${s.n}` : '-/-'; };

// Numéro de série d'ASLDiceBot : séries de 99, numéros tirés dans le désordre.
let _serie = 0, _rangSerie = 99, _ordreSerie = [];
function _numeroSerie(){
  if(_rangSerie >= 99){
    _serie++; _rangSerie = 0;
    _ordreSerie = Array.from({length: 99}, (_, i) => i + 1);
    for(let i = 98; i > 0; i--){
      const j = Math.floor(Math.random() * (i + 1));
      [_ordreSerie[i], _ordreSerie[j]] = [_ordreSerie[j], _ordreSerie[i]];
    }
  }
  const d2 = n => String(n).padStart(2, '0');
  return d2(_serie) + '.' + d2(_ordreSerie[_rangSerie++]);
}

function lancerDR(cat){
  const c = _d6(), b = _d6();
  _ajouterStat('D2_Total', c + b); _ajouterStat('D2_' + cat, c + b);
  const msg = `*** (${cat} DR) ${c},${b} ***   <${joueur()}>      ${_special(c + b)}`
            + `[${_surTotal('D2_Total', c + b)}   avg   ${_moy('D2_' + cat)} (${_moy('D2_Total')})]`
            + `    (${_numeroSerie()})`;
  _publierJet(msg);
  return {c, b, total: c + b};
}

function lancerdr(cat){
  const d = _d6();
  _ajouterStat('D1_Total', d); _ajouterStat('D1_' + cat, d);
  const msg = `*** (${cat} dr) ${d} ***   <${joueur()}>      `
            + `[${_surTotal('D1_Total', d)}   avg   ${_moy('D1_' + cat)} (${_moy('D1_Total')})]`
            + `    (${_numeroSerie()})`;
  _publierJet(msg);
  return {d};
}

// ASLDiceBot envoie le jet tel quel, « <joueur> » compris : ASLChatter
// y cherche le joueur entre < et >. Échappé, le jet n'est pas reconnu
// et VASL affiche « *2~null ... » à sa place.
function _publierJet(msg){
  _logCmd('CHAT' + msg);
  afficherChat(msg);
}

function _desechappe(s){
  return (s || '').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&amp;/g,'&');
}

// ── Affichage d'un message de chat, jets décodés comme ASLChatter :
// images des dés, total, joueur, SAN ou piège, et les annonces de règle
// du niveau « Full ASL ».
function afficherChat(msg){
  // Tirage d'une carte OBA (VASL.build.module.OBA) : la carte, puis le texte.
  const oba = msg.match(/^\s*\*\*\* Battery Access Draw = (Black|Red) \*\*\*\s*$/);
  if(oba){
    const l = document.createElement('div');
    l.className = 'rap obaJet';
    const c = document.createElement('span');
    c.className = 'obaMini ' + (oba[1] === 'Black' ? 'b' : 'r');
    l.append(c, msg.trim());
    ajouterLigne(l);
    return;
  }
  const m = msg.match(/^\*\*\* \((.+?) (DR|dr)\) ([\d,]+) \*\*\*.*?<([^>]*)>\s*(.*)$/);
  if(!m){
    // Rapports (« * … ») et messages des joueurs, comme le chat de VASSAL.
    const l = document.createElement('div');
    l.className = /^\*/.test(msg) ? 'rap' : 'msg';
    l.textContent = msg;
    ajouterLigne(l);
    return;
  }
  const [, catBrute, type, des, qui, reste] = m;
  const cat = catBrute.replace('ENEMY ', '');
  const v = des.split(',').map(Number);
  const ligne = document.createElement('div');
  ligne.className = 'jet';
  const t = document.createElement('span');
  t.className = 'cat'; t.textContent = `${catBrute === 'Other' ? '' : catBrute + ' '}${type}`;
  ligne.appendChild(t);
  v.forEach((n, i) => {
    const im = document.createElement('img');
    im.alt = String(n); im.title = String(n);
    chargerImage(`DC${n}_${i === 0 && type === 'DR' ? DE_COLORE : DE_BLANC}.png`).then(u => { if(u) im.src = u; });
    ligne.appendChild(im);
  });
  const tot = document.createElement('span');
  tot.className = 'tot';
  tot.textContent = type === 'DR' ? `= ${v[0] + v[1]}` : '';
  ligne.appendChild(tot);
  const q = document.createElement('span');
  q.className = 'qui'; q.textContent = qui;
  ligne.appendChild(q);
  // Annonces soulignées après le joueur, comme ASLChatter : le SAN ou le
  // piège en tête, puis les annonces de règle, séparés par des virgules.
  let san = null, sp;
  if(type === 'DR'){
    san = (reste.match(/^(Axis\/Allied|Axis|Allied) (SAN|Booby Trap)/) || [])[0];
    // ASLChatter ne signale pas le SAN sur Rally, TK et CC, ni les pièges hors TC.
    if(san && !(/Booby/.test(san) ? cat === 'TC' : !['TK','CC','Rally'].includes(cat))) san = null;
    sp = _annoncesDR(cat, v[0], v[1]);
  } else sp = _annoncesdr(cat, v[0]);
  if(san || sp.length){
    const s = document.createElement('span');
    s.className = 'spe';
    if(san){
      const e = document.createElement('span');
      e.className = 'san'; e.textContent = san;
      s.appendChild(e);
      if(sp.length) s.append(', ');
    }
    s.append(sp.join(', '));
    ligne.appendChild(s);
  }
  ajouterLigne(ligne);
}

// dr d'activation du Sniper (A14.3), comme ASLChatter.
function _annoncesdr(cat, d){
  if(cat !== 'SA') return [];
  if(d === 1) return ['Eliminates SMC, Dummy stack, Sniper; Stuns & Recalls CE crew; breaks MMC & Inherent crew of certain vehicles; immobilizes unarmored vehicle (A14.3)'];
  if(d === 2) return ['Eliminates Dummy stack; Wounds SMC; Stuns CE crew; pins MMC, Inherent crew of certain vehicles, Sniper (A14.3)'];
  return [];
}

function _annoncesDR(cat, a, b){
  const sp = [];
  switch(cat){
    case 'TH':
      if(a === b){ if(a === 1) sp.push('Unlikely Hit (C3.6)'); sp.push('Multiple Hits 15..40mm (C3.8)'); }
      sp.push(a < b ? 'Turret' : 'Hull');
      break;
    case 'TK':
      if(a === 6 && b === 6) sp.push('Dud (C7.35)');
      break;
    case 'MC':
      if(a === 1 && b === 1) sp.push('Heat of Battle (A15.1)');
      else if(a === 6 && b === 6) sp.push('Casualty MC (A10.31)');
      break;
    case 'Rally':
      if(a === 1 && b === 1) sp.push('Heat of Battle (A15.1) or Field Promotion (A18.11)');
      else if(a === 6 && b === 6) sp.push('Fate -> Casualty Reduction (A10.64)');
      break;
    case 'IFT':
      if(a === b){ if(a === 1) sp.push('Unlikely Kill vs * (A7.309)'); sp.push('Cower if MMC w/o LDR'); }
      break;
    case 'CC':
      if(a === 1 && b === 1) sp.push('Infiltration (A11.22), Field Promotion (A18.12), Unlikely Kill (A11.501)');
      else if(a === 6 && b === 6) sp.push('Infiltration (A11.22)');
      break;
  }
  return sp;
}

