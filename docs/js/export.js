// ═══════════════════════════════════════════════════════════════
// EXPORT .vsav ET .vlog
// On repart des segments du fichier chargé : seules changent les
// positions, les piles de la carte de jeu, les pions retirés et ceux
// posés depuis la palette. Tout le reste — boards, autres fenêtres,
// options — est réécrit mot pour mot.
// ═══════════════════════════════════════════════════════════════
// Enregistrement du log, comme BasicLogger de VASSAL : « Begin Logfile »
// fige l'état de la partie et choisit le fichier ; chaque coup joué, reçu
// d'un autre joueur ou relu d'un .vlog s'y ajoute ; « End Logfile »
// écrit l'état de départ suivi des coups.
let _logEnr = null;       // commandes du log en cours ; null : pas de log
let _logDepart = null;    // état de la partie au début du log
let _logNom = null;       // fichier choisi au début du log

// Toutes les commandes nées d'une même action du joueur — le même tour de
// boucle d'événements — forment un coup, écrit en une seule entrée LOG
// composée, comme le fait VASSAL.
let _logGroupes = [], _actionNo = 0, _actionOuverte = false;
// o.horsLog : envoyé (réseau) sans entrer dans le log enregistré ;
// o.horsReseau : consigné dans le log seul.
function _logCmd(c, o){
  if(!o || typeof o !== 'object') o = {};      // forEach(_logCmd) passe l'indice
  _planifierSecours();
  if(_net && !o.horsReseau) _netCoup(c);
  // Un coup relu ne change pas la partie : le .vlog le rejouera.
  if(!_etapeLog && !_relecture) _partieModifiee = true;
  // Un coup joué ici (ni reçu, ni relu) : les CVP recomptés le suivent.
  if(!_netReception && !_etapeLog && !_relecture && !/^(MutableProperty|GlobalProperty)\t/.test(c)) _planifierCVP();
  // Pièce déplacée ici : elle quitte sa pile VASSAL, comme dans VASSAL
  // (un coup reçu, relu ou annulé passe déjà par rejouerLog).
  if(!_netReception && !_etapeLog && !_relecture && !_annulation && c.startsWith('M/')){
    const t = _decoupeCmd(c.substring(2));
    if(parUid(_nul(t[0]))) _changerDePile(t[0], _nul(t[4]));
  }
  if(!_actionOuverte){
    _actionNo++; _actionOuverte = true;
    setTimeout(() => { _actionOuverte = false; }, 0);
  }
  _histoCmd(c);
  if(!_logEnr || o.horsLog) return;
  _logEnr.push(c);
  _logGroupes.push(_actionNo);
  majBoutons();
}
const _nbActions = () => new Set(_logGroupes).size;
// Les commandes suivantes forment une nouvelle action (un nouveau pas du
// log), même dans le même tour de boucle.
function _nouvelleAction(){
  _actionNo++; _actionOuverte = true;
  setTimeout(() => { _actionOuverte = false; }, 0);
}

const enregistreLog = () => _logEnr !== null;
function basculerLog(){ return enregistreLog() ? terminerLog() : commencerLog(); }

// beginOutput : le nom du fichier d'abord (getSaveFile), puis l'état de
// la partie, figé à cet instant.
function commencerLog(){
  if(enregistreLog()) return false;
  if(!_source){ chatLog('⚠️ Ouvrir d\'abord une partie : le log part d\'un état que VASL sache relire.'); return false; }
  _fenetreLog('Commencer un fichier de log', `
      <label>Nom du fichier<input id="lgNom" autocomplete="off" value="${_esc(_nomExport('vlog'))}"></label>
      <label class="coche"><input id="lgDevoile" type="checkbox"${demasquablesParMoi() ? ' checked' : ''}>
        Permettre à mes adversaires de démasquer mes unités</label>${_utilisateur || _admin ? '' : `
      <p class="fnote">Vous êtes observateur : la case s'appliquera à vos unités dès que vous
        vous identifierez (Utilisateur).</p>`}
      <div class="fb"><button id="lgOk" class="prim">Commencer</button><button id="lgAnnul">Annuler</button></div>
      <p class="fnote">Les coups joués, reçus ou relus à partir de cette position y seront
        enregistrés ; « Log » à nouveau termine le fichier et l'écrit.</p>`, w => {
    const inp = w.querySelector('#lgNom');
    const ok = () => {
      const nom = _nomPropre(inp.value, 'vlog');
      if(!nom) return;
      w.remove();
      _logNom = nom;
      _logEnr = []; _logGroupes = [];
      permettreDemasquage(w.querySelector('#lgDevoile').checked);
      _logDepart = etatPartie();
      // beginOutput vide aussi logOutput : on n'annule pas en deçà du log.
      _histo = []; majUndo();
      chatLog(`⏺️ Enregistrement du log commencé : ${nom}`);
      majBoutons();
    };
    w.querySelector('#lgOk').onclick = ok;
    w.querySelector('#lgAnnul').onclick = () => w.remove();
    inp.addEventListener('keydown', e => { if(e.key === 'Enter') ok(); if(e.key === 'Escape') w.remove(); });
    inp.focus(); inp.select();
  });
  return true;
}

// endLogAction : write() n'écrit rien si aucun coup n'a été enregistré.
function terminerLog(){
  if(!enregistreLog()) return false;
  if(_logEnr.length) ecrireVlog();
  else chatLog('⏹️ Log terminé : aucun coup enregistré, aucun fichier écrit');
  _logEnr = null; _logDepart = null; _logNom = null; _logGroupes = [];
  _garderLog();
  majBoutons();
  return true;
}

// BasicLogger.setup(false) : la partie se ferme, un log en cours peut
// encore être écrit.
function fermerLog(){
  if(!enregistreLog()) return;
  if(_logEnr.length && confirm(T(`Le log « ${_logNom} » n'est pas écrit (${_nbActions()} coup(s)). L'écrire maintenant ?`)))
    ecrireVlog();
  _logEnr = null; _logDepart = null; _logNom = null; _logGroupes = [];
  _garderLog();
  const f = document.getElementById('fenLog');
  if(f) f.remove();
  majBoutons();
}

// ═══ STOCKAGE DU POSTE — IndexedDB ═══
// Une petite base « aslweb » : le secours de la partie et du log
// (magasin secours, clés partie et log) et le buildFile analysé (magasin
// bdd, voir bdd.js). Ouverte à la première demande ; absente ou refusée
// (Safari privé, certaines ouvertures en file://, voir
// docs/indexeddb-file.md), _idb() donne null et chacun garde son repli.
// Toutes les opérations sont sous try/catch : aucune ne lève.
let _idbPromesse = null;
function _idb(){
  if(!_idbPromesse) _idbPromesse = new Promise(f => {
    try {
      if(typeof indexedDB === 'undefined' || !indexedDB) return f(null);
      const rq = indexedDB.open('aslweb', 1);
      rq.onupgradeneeded = () => {
        const db = rq.result;
        for(const m of ['secours', 'bdd']) if(!db.objectStoreNames.contains(m)) db.createObjectStore(m);
      };
      rq.onsuccess = () => { const db = rq.result; db.onversionchange = () => db.close(); f(db); };
      rq.onerror = () => f(null);
      rq.onblocked = () => f(null);
    } catch(e){ f(null); }
  });
  return _idbPromesse;
}
// Lecture : la valeur, undefined si absente, null si la base manque.
async function _idbLire(magasin, cle){
  const db = await _idb();
  if(!db) return null;
  try {
    return await new Promise((f, r) => {
      const rq = db.transaction(magasin, 'readonly').objectStore(magasin).get(cle);
      rq.onsuccess = () => f(rq.result); rq.onerror = () => r(rq.error);
    });
  } catch(e){ return null; }
}
// Écriture (valeur undefined : effacement). Vrai si elle a abouti.
async function _idbEcrire(magasin, cle, v){
  const db = await _idb();
  if(!db) return false;
  try {
    return await new Promise(f => {
      const tx = db.transaction(magasin, 'readwrite');
      if(v === undefined) tx.objectStore(magasin).delete(cle); else tx.objectStore(magasin).put(v, cle);
      tx.oncomplete = () => f(true); tx.onerror = () => f(false); tx.onabort = () => f(false);
    });
  } catch(e){ return false; }
}

// Segments compactés : chaque TYPE de pièce une fois dans types, chaque
// segment de pièce [indice du TYPE, « uid/ÉTAT »], les autres [-1, texte].
// _decompacter redonne les segments à l'octet près (vérifié à l'écriture :
// un segment qui ne se recompose pas reste en texte).
function _compacter(segs, types, ix){
  return segs.map(s => {
    if(typeof s === 'string' && s.startsWith('+/')){
      const te = _typeEtEtat(s), k = s.indexOf('/', 2);
      if(te && k >= 2){
        const uid = s.substring(2, k);
        if('+/' + uid + '/' + te.type + '/' + te.etat === s){
          let i = ix.get(te.type);
          if(i === undefined){ i = types.length; types.push(te.type); ix.set(te.type, i); }
          return [i, uid + '/' + te.etat];
        }
      }
    }
    return [-1, s];
  });
}
function _decompacter(segments, types){
  return segments.map(([i, r]) => {
    if(i < 0) return r;
    const k = r.indexOf('/');
    return '+/' + r.substring(0, k) + '/' + types[i] + '/' + r.substring(k + 1);
  });
}

// Copies de secours, prises à l'instant (l'écriture, elle, est asynchrone).
// IndexedDB d'abord, localStorage en repli ; écrite en IndexedDB, la copie
// efface celle du localStorage, qu'une version précédente a pu laisser.
// Un repli trop gros pour le localStorage le dit au chat (une fois, tant
// qu'il le reste) au lieu de disparaître sans rien dire.
const _secoursTropGros = {partie: false, log: false};
function _secoursLocal(quoi, cle, texte){
  try {
    if(texte === null) localStorage.removeItem(cle); else localStorage.setItem(cle, texte());
    _secoursTropGros[quoi] = false;
  } catch(e){
    // Pas de copie périmée non plus.
    try { localStorage.removeItem(cle); } catch(e2){}
    if(texte !== null && !_secoursTropGros[quoi]){
      _secoursTropGros[quoi] = true;
      chatLog(quoi === 'partie'
        ? '⚠️ Partie trop grosse pour la copie de secours du navigateur : elle ne sera pas reprise si la page se ferme. L\'exporter (.vsav) avant de fermer.'
        : '⚠️ Log trop gros pour la copie de secours du navigateur : il ne sera pas repris si la page se ferme. L\'écrire (Log) avant de fermer.');
    }
  }
}
function _secours(quoi, cle, idb, local){
  _idb().then(async db => {
    if(db && await _idbEcrire('secours', quoi, idb === null ? undefined : idb)){
      try { localStorage.removeItem(cle); } catch(e){}
      _secoursTropGros[quoi] = false;
      return;
    }
    _secoursLocal(quoi, cle, local);
  });
}

// Secours écrit peu après chaque coup, pas seulement à la fermeture :
// IndexedDB est asynchrone, et Safari iOS peut couper la page avant la
// fin d'une écriture lancée par pagehide.
let _secoursMinuteur = 0;
function _planifierSecours(){
  clearTimeout(_secoursMinuteur);
  _secoursMinuteur = setTimeout(() => { _garderLog(); _garderPartie(); }, 800);
}

// Safari iOS ignore beforeunload et ferme sans prévenir un onglet laissé
// en arrière-plan : dès que la page se cache, le log pas encore écrit est
// mis de côté, puis proposé au chargement suivant.
const CLE_LOG_SECOURS = 'aslweb.logSecours';
const _versB64 = o => { let b = ''; for(let i = 0; i < o.length; i += 0x8000) b += String.fromCharCode(...o.subarray(i, i + 0x8000)); return btoa(b); };
const _deB64 = t => Uint8Array.from(atob(t), c => c.charCodeAt(0));
function _garderLog(){
  try {
    if(!_logEnr || !_logEnr.length || !_logDepart) return _secours('log', CLE_LOG_SECOURS, null, null);
    const nom = _logNom, depart = _logDepart.slice(), enr = _logEnr.slice(), groupes = _logGroupes.slice(), annexes = _annexes;
    const types = [], ix = new Map();
    _secours('log', CLE_LOG_SECOURS,
      {nom, date: Date.now(), depart: _compacter(depart, types, ix), enr: _compacter(enr, types, ix), types,
       groupes, annexes: new Map(annexes)},
      () => JSON.stringify({nom, depart, enr, groupes, annexes: [...annexes].map(([n, d]) => [n, _versB64(d)])}));
  } catch(e){}
}
// Le log gardé : en IndexedDB, sinon au localStorage. Repris une fois,
// il est effacé des deux.
async function _logGarde(){
  let g = null;
  try {
    const v = await _idbLire('secours', 'log');
    if(v && Array.isArray(v.types)){
      g = {nom: v.nom, depart: _decompacter(v.depart, v.types), enr: _decompacter(v.enr, v.types),
           groupes: v.groupes, annexes: v.annexes instanceof Map ? v.annexes : new Map()};
    }
  } catch(e){ g = null; }
  if(!g){
    try {
      const l = JSON.parse(localStorage.getItem(CLE_LOG_SECOURS));
      if(l) g = Object.assign(l, {annexes: new Map((l.annexes || []).map(([k, t]) => [k, _deB64(t)]))});
    } catch(e){}
  }
  await _idbEcrire('secours', 'log', undefined);
  try { localStorage.removeItem(CLE_LOG_SECOURS); } catch(e){}
  return g;
}
async function _reprendreLogGarde(){
  const g = await _logGarde();
  if(!g || !Array.isArray(g.enr) || !g.enr.length || !Array.isArray(g.depart)) return;
  const n = new Set(g.groupes).size;
  if(!confirm(T(`Le log « ${g.nom} » n'a pas été écrit avant la fermeture de la page (${n} coup(s)). L'écrire maintenant ?`))) return;
  ecrireVlog({enr: g.enr, groupes: g.groupes || [], depart: g.depart, nom: g.nom, annexes: g.annexes});
}

// Même secours pour la partie elle-même, log ou non : sur Android, la PWA
// glissée hors des applications récentes, ou fermée par le système, ne
// prévient pas. Dès que la page se cache, une partie modifiée et pas
// encore exportée est mise de côté, puis proposée au lancement suivant.
// En IndexedDB : {nom, date, types, segments, annexes} — segments compactés
// (_compacter), annexes en binaire (Map de Uint8Array, sans base64).
const CLE_PARTIE_SECOURS = 'aslweb.partieSecours';
let _partieModifiee = false;   // coups joués depuis l'ouverture ou le dernier .vsav
function _garderPartie(){
  try {
    // Pas de partie ouverte (reprise ratée, par exemple) : la copie reste.
    if(!_source) return;
    if(!_partieModifiee) return _secours('partie', CLE_PARTIE_SECOURS, null, null);
    const nom = _nomFichier, date = Date.now(), segs = etatPartie(), annexes = _annexes;
    const types = [];
    _secours('partie', CLE_PARTIE_SECOURS,
      {nom, date, types, segments: _compacter(segs, types, new Map()), annexes: new Map(annexes)},
      () => JSON.stringify({nom, date, texte: segs.join('\x1b'), annexes: [...annexes].map(([n, d]) => [n, _versB64(d)])}));
  } catch(e){}
}
// La partie gardée : {nom, date, texte, annexes}, ou null.
async function _partieGardee(){
  try {
    const v = await _idbLire('secours', 'partie');
    if(v && Array.isArray(v.types) && Array.isArray(v.segments))
      return {nom: v.nom, date: v.date, texte: _decompacter(v.segments, v.types).join('\x1b'),
              annexes: v.annexes instanceof Map ? v.annexes : new Map()};
  } catch(e){}
  try {
    const g = JSON.parse(localStorage.getItem(CLE_PARTIE_SECOURS));
    if(g) return Object.assign(g, {annexes: new Map((g.annexes || []).map(([k, t]) => [k, _deB64(t)]))});
  } catch(e){}
  return null;
}
async function _oublierPartieGardee(){
  await _idbEcrire('secours', 'partie', undefined);
  try { localStorage.removeItem(CLE_PARTIE_SECOURS); } catch(e){}
}
async function _reprendrePartieGardee(){
  const g = await _partieGardee();
  if(!g || typeof g.texte !== 'string' || !g.texte) return;
  const quand = g.date ? new Date(g.date).toLocaleString() : '';
  if(!confirm(T(`La partie « ${g.nom} » a été modifiée et pas enregistrée avant la fermeture${quand ? ` (${quand})` : ''}. La reprendre ?`))){
    await _oublierPartieGardee();
    return;
  }
  try {
    const blob = await _archiveVsav(g.texte, g.annexes);
    await openGameFile(new File([blob], (g.nom || 'partie') + '.vsav'));
    // Toujours pas exportée : encore à garder si l'on referme.
    if(_source) _partieModifiee = true;
  } catch(e){
    chatLog('❌ Reprise impossible : ' + (e && e.message ? e.message : e));
  }
}

// queryNewLogFile : à l'ouverture d'une partie et à la fin d'une
// relecture, VASSAL propose un nouveau log, sauf si l'on a choisi de
// ne plus être interrogé (préférences PromptNewLogAtStart et
// PromptNewLogEnd).
const CLE_LOG_DEBUT = 'aslweb.PromptNewLogAtStart', CLE_LOG_FIN = 'aslweb.PromptNewLogEnd';
function proposerLog(auDebut){
  if(enregistreLog() || !_source) return;
  const cle = auDebut ? CLE_LOG_DEBUT : CLE_LOG_FIN;
  if(_lireLocal(cle, '1') !== '1') return;
  const quoi = auDebut ? (_etapes.length ? 'La relecture commence.' : 'Partie ouverte.') : 'Relecture terminée.';
  _fenetreLog('Fichier de log', `
      <p>${quoi} Commencer un nouveau fichier de log ?</p>
      <div class="fb"><button id="lgOui" class="prim">Oui</button><button id="lgNon">Non</button>
        <button id="lgJamais">Ne plus demander</button></div>`, w => {
    w.querySelector('#lgOui').onclick = () => { w.remove(); commencerLog(); };
    w.querySelector('#lgNon').onclick = () => w.remove();
    w.querySelector('#lgJamais').onclick = () => { _ecrireLocal(cle, '0'); w.remove(); };
    w.querySelector('#lgOui').focus();
  });
}

function _fenetreLog(titre, corps, brancher){
  const ancien = document.getElementById('fenLog');
  if(ancien) ancien.remove();
  const w = document.createElement('div');
  w.id = 'fenLog'; w.className = 'fenetre';
  w.innerHTML = `<div class="ft">${_esc(titre)}</div><div class="fc">${corps}</div>`;
  document.body.appendChild(w);
  brancher(w);
}

function majBoutons(){ rafraichirActions(['vsav', 'log', 'logTxt']); }

// Piles de la carte de jeu, d'après l'état courant. VASL écrit aussi les
// piles d'un seul pion. La couche (@@n) est celle de la pièce du fond
// dans le fichier d'origine, -1 (« non liée ») sinon.
// Une pile garde l'identifiant de sa pile VASSAL (_pilesVassal) : les
// coups relus puis consignés dans un nouveau log, ou reçus d'un joueur
// sous VASL, déplacent une pile par cet identifiant (« M/<id pile>/… »).
// Renommée, elle ne bougerait plus à la relecture, ni ici ni dans VASL.
// Chaque identifiant va à la pile qui partage le plus de pièces avec lui ;
// une pile qui n'en partage aucune en reçoit un neuf.
function _segmentsPiles(){
  const out = [];
  let n = 0;
  // Une pièce qui ne s'empile pas n'appartient à aucune pile VASSAL.
  const piles = [...new Set(vivants().filter(p => !p.seul).map(cle))].map(pile);
  const paires = [];
  piles.forEach((s, i) => {
    const uids = new Set(s.map(p => p.uid));
    for(const [id, ids] of _pilesVassal){
      const k = ids.filter(u => uids.has(u)).length;
      if(k) paires.push({i, id, k, fond: ids[0] === s[0].uid});
    }
  });
  paires.sort((a, b) => b.k - a.k || b.fond - a.fond);
  const idDe = new Map(), pris = new Set();
  for(const c of paires){
    if(idDe.has(c.i) || pris.has(c.id)) continue;
    idDe.set(c.i, c.id); pris.add(c.id);
  }
  piles.forEach((s, i) => {
    let couche = -1;
    for(const p of s) if(_coucheParUid.has(p.uid)){ couche = _coucheParUid.get(p.uid); break; }
    let id = idDe.get(i);
    if(!id) do { id = `w${Date.now()}${++n}`; } while(_pilesVassal.has(id));
    out.push(`+/${id}/stack/${_carteJeu};${s[0].x};${s[0].y};`
             + s.map(p => p.uid).join(';') + `;@@${couche}`);
  });
  return out;
}

function etatPartie(){
  const parUidMap = new Map(pions.map(p => [p.uid, p]));
  const sortie = [], connus = new Set();
  let obaPose = false;
  for(const seg of _source){
    // Modules OBA : ceux de la partie telle qu'elle est, à la place des
    // premiers du fichier.
    if(_cleOBA(seg).startsWith('OBA/')){
      if(!obaPose){ sortie.push(..._segmentsOBA()); obaPose = true; }
      continue;
    }
    if(/^\+\/[^/]+\/stack\//.test(seg)){
      const mc = seg.replace(/\\/g, '').match(/\/stack\/([^;]+);/);
      if(mc && mc[1] === _carteJeu) continue;          // régénérée
      sortie.push(seg); continue;
    }
    if(seg.startsWith('INFO\t')){ sortie.push('INFO\t' + _infoEtat); continue; }
    if(_CMD_LIEN.test(seg.replace(/\\+$/, ''))) continue;          // régénérés
    const m = seg.match(/^\+\/([^/]+)\//);
    const p = m && parUidMap.get(m[1]);
    if(!p){ sortie.push(seg); continue; }
    connus.add(p.uid);
    if(!p.deleted || p.ailleurs) sortie.push(p.seg);
  }
  // Pions posés ou créés par le log : avant la fin de sauvegarde.
  const neufs = pions.filter(p => (!p.deleted || p.ailleurs) && !connus.has(p.uid)).map(p => p.seg);
  const ajout = [...neufs, ..._segmentsPiles(), ...segmentsLiens(), ...(obaPose ? [] : _segmentsOBA()), ..._segmentsGlob(sortie)];
  // SAN saisis ici alors que le fichier n'avait pas d'informations de scénario.
  if(_infoModifiee && !_source.some(x => x.startsWith('INFO\t'))) ajout.push('INFO\t' + _infoEtat);
  const iFin = sortie.lastIndexOf('end_save');
  if(iFin >= 0) sortie.splice(iFin, 0, ...ajout); else sortie.push(...ajout);
  return sortie;
}

// Comme VASSAL, les entrées de l'archive sont comprimées : le savedGame
// obfusqué (hexadécimal) y perd plus de 90 % de sa taille.
async function _archiveVsav(texte, annexes = _annexes){
  const files = new Map([['savedGame', await _deflate(_vcskEncode(texte))]]);
  for(const [n, d] of annexes) files.set(n, await _deflate(d));
  return _zipWrite(files);
}

// iPhone et iPad (iPadOS se présente en « Macintosh », mais tactile).
function _surIOS(){
  const ua = navigator.userAgent || '';
  return /iPad|iPhone|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
}

// Sur iOS, un téléchargement finit dans Fichiers > Téléchargements, sans
// dire où, et ne marche guère dans l'application installée. La feuille de
// partage, elle, propose « Enregistrer dans Fichiers » (on choisit le
// dossier), Mail, Messages, AirDrop… Ailleurs : téléchargement.
function _fichierPartageable(blob, nom){
  if(!_surIOS() || !navigator.share || !navigator.canShare) return null;
  // octet-stream : iOS garde l'extension .vsav / .vlog telle quelle.
  const f = new File([blob], nom, {type: 'application/octet-stream'});
  try { return navigator.canShare({files: [f]}) ? f : null; } catch(e){ return null; }
}

function _telechargerBlob(blob, nom){
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = nom;
  document.body.appendChild(a); a.click(); a.remove();
  // Safari demande d'abord s'il faut télécharger : l'URL doit vivre le
  // temps qu'on réponde.
  setTimeout(() => URL.revokeObjectURL(a.href), 120000);
}

// navigator.share exige un geste de l'utilisateur encore « frais » : refusé
// (NotAllowedError), on le redemande par un bouton.
function _partager(f){
  return navigator.share({files: [f]}).then(() => 'partage', e => e && e.name);
}

// Enregistre (ou partage) le fichier ; résout en 'partage', 'telecharge'
// ou 'annule'. À appeler, si possible, dans le geste de l'utilisateur.
async function _enregistrer(blob, nom){
  const f = _fichierPartageable(blob, nom);
  if(!f){ _telechargerBlob(blob, nom); return 'telecharge'; }
  const r = await _partager(f);
  if(r === 'partage') return r;
  if(r === 'AbortError') return 'annule';
  if(r === 'NotAllowedError') return _proposerPartage(f, blob, nom);
  _telechargerBlob(blob, nom);
  return 'telecharge';
}

function _proposerPartage(f, blob, nom){
  return new Promise(fin => {
    _fenetreLog('Fichier prêt', `
      <p>Fichier prêt : <b>${_esc(nom)}</b></p>
      <div class="fb"><button id="ptOk" class="prim">Partager / Enregistrer</button><button id="ptAnnul">Annuler</button></div>
      <p class="fnote">« Enregistrer dans Fichiers » choisit le dossier ; Mail, Messages ou AirDrop envoient la partie.</p>`, w => {
      const ok = w.querySelector('#ptOk');
      ok.onclick = () => {
        // Appel direct dans le clic : le geste est frais.
        _partager(f).then(r => {
          if(r === 'AbortError') return;          // feuille fermée : on peut réessayer
          w.remove();
          if(r === 'partage') fin(r);
          else { _telechargerBlob(blob, nom); fin('telecharge'); }
        });
      };
      w.querySelector('#ptAnnul').onclick = () => { w.remove(); fin('annule'); };
      ok.focus();
    });
  });
}

async function _telecharger(texte, nom, annexes = _annexes){
  return _enregistrer(await _archiveVsav(texte, annexes), nom);
}

// Nom du fichier saisi : caractères interdits remplacés, extension ajoutée.
function _nomPropre(v, ext){
  const nom = String(v || '').trim().replace(/[\\/:*?"<>|]/g, '_');
  if(!nom) return '';
  return new RegExp('\\.' + ext + '$', 'i').test(nom) ? nom : nom + '.' + ext;
}

// Nom du dernier log écrit : le suivant en reprend le numéro.
let _dernierLog = null;

// « j56 toto mickey 089 » → « j56 toto mickey 090 » : le numéro final
// augmente d'un, avec autant de chiffres (zéros en tête compris).
function _nomSuivant(base){
  const m = base.match(/^(.*?)(\d+)$/);
  if(!m) return null;
  const n = String(BigInt(m[2]) + 1n);
  return m[1] + n.padStart(m[2].length, '0');
}

// Fichier numéroté (« … 089 ») : on propose le numéro suivant. Sinon
// « partie_20261102-1430.vsav » : unique à la minute, sans empiler les
// dates d'un fichier déjà exporté.
function _nomExport(ext){
  const source = (ext === 'vlog' && _dernierLog) || _nomFichier;
  const brut = source.replace(new RegExp('\\.' + ext + '$', 'i'), '');
  if(!/_\d{8}(?:-\d{4})?$/.test(brut)){
    const suivant = _nomSuivant(brut);
    if(suivant) return `${suivant}.${ext}`;
  }
  const d = new Date(), z = n => String(n).padStart(2, '0');
  const j = d.getFullYear() + z(d.getMonth()+1) + z(d.getDate()) + '-' + z(d.getHours()) + z(d.getMinutes());
  const base = brut.replace(/_\d{8}(?:-\d{4})?$/, '') || 'partie';
  return `${base}_${j}.${ext}`;
}

// Comme le log : le nom d'abord. L'état est figé et l'archive préparée
// pendant qu'on le saisit, pour que le partage parte dans le clic.
function ecrireVsav(){
  if(!_source) return false;
  const nb = vivants().length, carte = _carteJeu;
  const prete = _archiveVsav(etatPartie().join('\x1b'));
  let blob = null;
  prete.then(b => { blob = b; }, () => {});
  _fenetreLog('Exporter la partie', `
      <label>Nom du fichier<input id="svNom" autocomplete="off" value="${_esc(_nomExport('vsav'))}"></label>
      <div class="fb"><button id="svOk" class="prim">Enregistrer</button><button id="svAnnul">Annuler</button></div>${_surIOS() ? `
      <p class="fnote">Puis « Enregistrer dans Fichiers » pour choisir le dossier, ou Mail, Messages, AirDrop pour l'envoyer.</p>` : ''}`, w => {
    const inp = w.querySelector('#svNom');
    const ok = async () => {
      const nom = _nomPropre(inp.value, 'vsav');
      if(!nom) return;
      w.remove();
      const r = await _enregistrer(blob || await prete, nom);
      if(r === 'annule') chatLog(`💾 Export de ${nom} annulé`);
      else { chatLog(`💾 .vsav écrit : ${nom} — ${nb} pion(s) sur « ${carte} »`); _partieModifiee = false; _garderPartie(); }
    };
    w.querySelector('#svOk').onclick = ok;
    w.querySelector('#svAnnul').onclick = () => w.remove();
    inp.addEventListener('keydown', e => { if(e.key === 'Enter') ok(); if(e.key === 'Escape') w.remove(); });
    inp.focus();
    // Le nom seul, sans l'extension : c'est lui qu'on change.
    const v = inp.value, p = v.lastIndexOf('.');
    inp.setSelectionRange(0, p > 0 ? p : v.length);
  });
  return true;
}

// BasicLogger : état de départ, puis « LOG\t<commande> » par action.
function ecrireVlog(log = {enr: _logEnr, groupes: _logGroupes, depart: _logDepart, nom: _logNom, annexes: _annexes}){
  if(!log.enr || !log.enr.length || !log.depart) return false;
  // BasicLogger.write : chaque coup devient une LogCommand, dont chaque
  // sous-commande est elle-même une LogCommand. GameModule.encode écrit
  // donc « LOG\t<c1> ESC LOG\t<c2> … », et ces ESC, imbriqués dans la
  // sauvegarde, prennent une contre-oblique (SequenceEncoder). Sans le
  // « LOG\t » de chaque sous-commande, VASSAL les exécute au chargement
  // au lieu d'en faire un coup à relire.
  // SequenceEncoder.append met entre apostrophes un jeton qui finit par
  // une contre-oblique (l'état d'un pion VASL, « …;OldMap;Main Map\ ») :
  // sans elles, elle échapperait l'ESC qui le suit, et le coup suivant se
  // fondrait dans celui-ci à la relecture.
  const jeton = t => {
    const e = t.replace(/\x1b/g, '\\\x1b');
    return t.endsWith('\\') || (t.startsWith("'") && t.endsWith("'")) ? `'${e}'` : e;
  };
  const entrees = [];
  log.enr.forEach((c, i) => {
    if(i && log.groupes[i] === log.groupes[i - 1]) entrees[entrees.length - 1].push(c);
    else entrees.push([c]);
  });
  const lignes = entrees.map(l => jeton(l.map(c => jeton('LOG\t' + c)).join('\x1b')));
  const nom = log.nom || _nomExport('vlog'), n = entrees.length, nc = log.enr.length;
  _dernierLog = nom;
  Promise.resolve(_telecharger([...log.depart, ...lignes].join('\x1b'), nom, log.annexes)).then(r => {
    if(r === 'annule') chatLog(`📼 Log ${nom} non enregistré (partage annulé)`);
    else chatLog(`📼 Log écrit : ${nom} — ${n} coup(s), ${nc} commande(s)`);
  });
  return true;
}

// ObfuscatingOutputStream : « !VCSK » + clé, puis les octets XOR-és en
// hexadécimal minuscule, comme VASSAL.
function _vcskEncode(texte){
  const octets = new TextEncoder().encode(texte);
  const k = 1 + Math.floor(Math.random() * 254);
  const h = new Array(octets.length);
  for(let i = 0; i < octets.length; i++) h[i] = ((octets[i] ^ k) & 0xFF).toString(16).padStart(2, '0');
  return new TextEncoder().encode('!VCSK' + k.toString(16).padStart(2, '0') + h.join(''));
}

// ── Écriture ZIP en méthode STORED : format valide, lisible par VASSAL.
let _crcTable = null;
function _crc32(bytes){
  if(!_crcTable){
    _crcTable = new Uint32Array(256);
    for(let i = 0; i < 256; i++){
      let c = i;
      for(let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      _crcTable[i] = c >>> 0;
    }
  }
  let crc = 0xFFFFFFFF;
  for(let i = 0; i < bytes.length; i++) crc = _crcTable[(crc ^ bytes[i]) & 0xFF] ^ (crc >>> 8);
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

// Entrée d'archive comprimée (deflate) : {brut, comp} pour _zipWrite ;
// sans CompressionStream, l'entrée reste brute (stockée).
async function _deflate(brut){
  if(typeof CompressionStream === 'undefined') return brut;
  const st = new Blob([brut]).stream().pipeThrough(new CompressionStream('deflate-raw'));
  return {brut, comp: new Uint8Array(await new Response(st).arrayBuffer())};
}

function _zipWrite(files){
  const enc = new TextEncoder();
  const locals = [], centrals = [];
  let offset = 0;
  // Date et heure MS-DOS de l'écriture.
  const t = new Date();
  const heure = (t.getHours() << 11) | (t.getMinutes() << 5) | (t.getSeconds() >> 1);
  const jour = ((t.getFullYear() - 1980) << 9) | ((t.getMonth() + 1) << 5) | t.getDate();
  for(const [name, f] of files){
    // Une entrée {brut, comp} est déjà comprimée (deflate) : méthode 8.
    const data = f.comp || f, brut = f.brut || f, methode = f.comp ? 8 : 0;
    const nb = enc.encode(name), crc = _crc32(brut), size = data.length, usize = brut.length;
    const lh = new Uint8Array(30 + nb.length), lv = new DataView(lh.buffer);
    lv.setUint32(0, 0x04034b50, true); lv.setUint16(4, 20, true); lv.setUint16(8, methode, true);
    lv.setUint16(10, heure, true); lv.setUint16(12, jour, true);
    lv.setUint32(14, crc, true); lv.setUint32(18, size, true); lv.setUint32(22, usize, true);
    lv.setUint16(26, nb.length, true);
    lh.set(nb, 30);
    locals.push(lh, data);
    const cd = new Uint8Array(46 + nb.length), cv = new DataView(cd.buffer);
    cv.setUint32(0, 0x02014b50, true); cv.setUint16(4, 20, true); cv.setUint16(6, 20, true);
    cv.setUint16(10, methode, true);
    cv.setUint16(12, heure, true); cv.setUint16(14, jour, true);
    cv.setUint32(16, crc, true); cv.setUint32(20, size, true); cv.setUint32(24, usize, true);
    cv.setUint16(28, nb.length, true); cv.setUint32(42, offset, true);
    cd.set(nb, 46);
    centrals.push(cd);
    offset += lh.length + size;
  }
  let cdSize = 0;
  for(const c of centrals) cdSize += c.length;
  const eo = new Uint8Array(22), ev = new DataView(eo.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, files.size, true); ev.setUint16(10, files.size, true);
  ev.setUint32(12, cdSize, true); ev.setUint32(16, offset, true);
  return new Blob([...locals, ...centrals, eo], {type: 'application/octet-stream'});
}

