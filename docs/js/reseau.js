// ═══════════════════════════════════════════════════════════════
// PARTIE À DISTANCE — d'après VASSAL.chat.node (NodeClient, ServerNode,
// Protocol) et VASSAL.chat (SynchAction, SynchCommand, CommandDecoder)
// VASSAL relie les joueurs par un serveur de salles. Le dialogue se
// fait ligne à ligne :
//   REG\tid\tchemin\tinfo   s'inscrire, d'abord dans « VASL/Main Room »
//   JOIN\tVASL/salle        changer de salle
//   STATS\tinfo             nouveau nom du joueur
//   FWD\tchemin\tmessage    faire suivre : « VASL/salle/~id » toute la
//                           salle sauf soi, « VASL/*/id » un seul joueur
//   LIST\tchemin=info\t…    (serveur) les joueurs, salle par salle
//   ROOM_INFO\t…            (serveur) le propriétaire de chaque salle,
//                           son premier occupant
// Un message de plus de 1000 caractères part en « !ZIP! » suivi du
// base64 d'un zip à une entrée (Compressor).
// Entrer dans une salle occupée (NodeClient.setRoom) : JOIN, puis, à la
// LIST qui confirme l'arrivée, une demande « SYNC » au propriétaire.
// Celui-ci répond (SynchCommand) par l'état complet de la partie,
// begin_save … end_save, envoyé au seul demandeur. Ensuite chaque coup
// est exécuté, consigné et envoyé aux autres (sendAndLog) ; à réception
// il est décodé, exécuté et consigné (CommandDecoder).
// Un navigateur n'ouvre pas de socket TCP : la page qui héberge tient le
// rôle du serveur, et les lignes passent par WebRTC (PeerJS) entre
// postes, ou par BroadcastChannel entre onglets d'un même navigateur.
// ═══════════════════════════════════════════════════════════════
const NET_MODULE = 'VASL';
const NET_SALLE_DEFAUT = 'Main Room';       // DEFAULT_ROOM_NAME
const NET_ZIP = '!ZIP!', NET_ZIP_SEUIL = 1000;
const NET_SYNC_TROP_TOT = 15000;            // SynchAction.TOO_SOON
const NET_PREFIXE = 'aslweb-';
const PEERJS_URL = 'https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js';

let _net = null;            // connexion en cours
let _netReception = false;  // vrai pendant l'exécution d'un coup reçu

// Propriétés d'un joueur ou d'une salle : « clé=valeur&… », sans
// tabulation ni « / » qui séparent les champs du protocole.
const _propsEnc = o => Object.entries(o).map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v)).join('&');
function _propsDec(s){
  const o = {};
  for(const e of (s || '').split('&')){
    const i = e.indexOf('=');
    if(i > 0) try { o[decodeURIComponent(e.substring(0, i))] = decodeURIComponent(e.substring(i + 1)); } catch(err){}
  }
  return o;
}
const _nomSalle = t => String(t || '').replace(/[\/\t=&\\]/g, ' ').trim().substring(0, 40);
const _netInfo = n => _propsEnc({id: n.moi, name: joueur(), client: 'ASLweb', looking: 'false', away: 'false'});

// ── !ZIP! : un zip à une entrée « Dummy », comme Compressor.
function _b64(u8){
  let s = '';
  for(let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
  return btoa(s);
}
async function _netZip(texte){
  const f = await _deflate(new TextEncoder().encode(texte));
  return _b64(new Uint8Array(await _zipWrite(new Map([['Dummy', f]])).arrayBuffer()));
}
async function _netDezip(b64){
  const s = atob(b64), u = new Uint8Array(s.length);
  for(let i = 0; i < s.length; i++) u[i] = s.charCodeAt(i);
  const [d] = (await _zipRead(u.buffer)).values();
  return new TextDecoder().decode(d || new Uint8Array(0));
}

// ── Le serveur (ServerNode), tenu par la page qui héberge. Chaque joueur
// est une feuille « VASL/salle/id » ; un lien est sa connexion.
function creerServeur(){
  const joueurs = new Map();      // id → {id, chemin, info, lien}, par ordre d'arrivée
  let minuterie = null;
  const chemin = c => {
    const t = String(c || '').split('/');
    return NET_MODULE + '/' + (_nomSalle(t[1]) || NET_SALLE_DEFAUT);
  };
  const srv = {
    recevoir(lien, ligne){
      if(typeof ligne !== 'string') return;
      if(ligne.startsWith('REG\t')){
        const [id, ch, info] = ligne.substring(4).split('\t');
        if(!id) return;
        lien.id = id;
        joueurs.delete(id);
        joueurs.set(id, {id, chemin: chemin(ch), info: info || '', lien});
        srv.changer();
      } else if(!joueurs.has(lien.id)){
        lien.envoyer('REG_REQUEST\t');             // inconnu : prié de s'inscrire
      } else if(ligne.startsWith('JOIN\t')){
        // Changer de salle place le joueur après ses occupants.
        const j = joueurs.get(lien.id);
        joueurs.delete(j.id);
        j.chemin = chemin(ligne.substring(5).split('\t')[0]);
        joueurs.set(j.id, j);
        srv.changer();
      } else if(ligne.startsWith('STATS\t')){
        joueurs.get(lien.id).info = ligne.substring(6);
        srv.changer();
      } else if(ligne.startsWith('FWD\t')){
        const r = ligne.substring(4), i = r.indexOf('\t');
        if(i > 0) for(const j of srv.cibles(r.substring(0, i))) j.lien.envoyer(r.substring(i + 1));
      }
    },
    quitter(lien){
      if(lien.id && joueurs.get(lien.id) && joueurs.get(lien.id).lien === lien){
        joueurs.delete(lien.id);
        srv.changer();
      }
    },
    // getMsgSender : un segment désigne un nœud, « * » tous, « ~id » tous
    // sauf id ; le message va à toutes les feuilles désignées.
    cibles(ch){
      const parts = ch.split('/');
      return [...joueurs.values()].filter(j => {
        const p = [...j.chemin.split('/'), j.id];
        return parts.every((s, k) => s === '*' || (s.startsWith('~') ? p[k] !== s.substring(1) : p[k] === s));
      });
    },
    // ReportContentsThread : LIST puis ROOM_INFO à chacun, après un
    // changement.
    changer(){
      if(minuterie) return;
      minuterie = setTimeout(() => {
        minuterie = null;
        const tous = [...joueurs.values()];
        const liste = 'LIST\t' + tous.map(j => `${j.chemin}/${j.id}=${j.info}`).join('\t');
        const salles = {};
        for(const j of tous){
          const s = j.chemin.split('/')[1];
          if(s !== NET_SALLE_DEFAUT && !salles[s]) salles[s] = _propsEnc({owner: j.id});
        }
        const info = 'ROOM_INFO\t' + _propsEnc(salles);
        for(const j of tous){ j.lien.envoyer(liste); j.lien.envoyer(info); }
      }, 50);
    },
    nb: () => joueurs.size
  };
  return srv;
}

// ── Transports : un message est une ligne de texte.
async function _chargerPeerJS(){
  if(window.Peer) return window.Peer;
  await new Promise((ok, ko) => {
    const s = document.createElement('script');
    s.src = PEERJS_URL; s.onload = ok;
    s.onerror = () => ko(new Error('bibliothèque PeerJS injoignable'));
    document.head.appendChild(s);
  });
  return window.Peer;
}
const _attendre = (sur, ms, quoi) => new Promise((ok, ko) => {
  const t = setTimeout(() => ko(new Error(quoi)), ms);
  sur((v) => { clearTimeout(t); ok(v); }, e => { clearTimeout(t); ko(e); });
});
const _errPeer = e => new Error(e && e.type === 'peer-unavailable' ? 'salle introuvable — vérifier le code'
  : e && e.type === 'unavailable-id' ? 'ce code de salle est déjà pris'
  : (e && (e.message || e.type)) || 'erreur WebRTC');

// « Internet » ouvre deux voies vers l'hôte. La voie directe (WebRTC,
// mise en relation par le serveur public de PeerJS) échoue souvent entre
// un PC et un téléphone : NAT de l'opérateur mobile, adresses locales en
// « .local » que la box ne résout pas, box qui ne renvoie pas vers son
// propre réseau ce qui vise son adresse publique. Le relais (des serveurs
// MQTT publics, joints en WebSocket chiffré) passe partout où passe le
// web. L'hôte écoute sur les deux voies. L'invité essaie les deux à la
// fois : il garde la voie directe si elle s'ouvre, le relais sinon.
async function _hebergerPeer(n, srv){
  const Peer = await _chargerPeerJS();
  const peer = new Peer(NET_PREFIXE + n.code);
  try {
    await _attendre((ok, ko) => { peer.on('open', ok); peer.on('error', e => ko(_errPeer(e))); },
                    15000, 'serveur de rendez-vous PeerJS injoignable');
  } catch(e){ peer.destroy(); throw e; }
  // Coupé du serveur de rendez-vous, l'hôte s'y réinscrit : les joueurs
  // déjà là restent reliés, les nouveaux pourront entrer. Un téléphone
  // coupe ce lien quand la page passe en arrière-plan : on le reprend
  // aussi au retour au premier plan.
  const reprendre = () => { if(_net === n && peer.disconnected && !peer.destroyed) peer.reconnect(); };
  peer.on('disconnected', reprendre);
  n.aReprendre.push(reprendre);
  peer.on('connection', conn => {
    const lien = {envoyer: l => { if(conn.open) conn.send(l); }};
    conn.on('data', l => srv.recevoir(lien, l));
    conn.on('close', () => srv.quitter(lien));
    conn.on('error', () => srv.quitter(lien));
  });
  return {fermer: () => peer.destroy()};
}

// Voie directe de l'invité : une voie ouverte, ou une erreur qui dit où
// elle a échoué (rendez-vous, hôte absent, ou ICE : pas de chemin réseau).
async function _essaiPeer(n){
  const Peer = await _chargerPeerJS();
  const peer = new Peer();
  try {
    await _attendre((ok, ko) => { peer.on('open', ok); peer.on('error', e => ko(_errPeer(e))); },
                    15000, 'serveur de rendez-vous PeerJS injoignable');
    const conn = peer.connect(NET_PREFIXE + n.code, {reliable: true});
    try {
      await _attendre((ok, ko) => { conn.on('open', ok); conn.on('error', ko); peer.on('error', e => ko(_errPeer(e))); },
                      20000, 'salle injoignable');
    } catch(e){
      const ice = conn.peerConnection && conn.peerConnection.iceConnectionState;
      throw ice && ice !== 'new' && ice !== 'connected' ? new Error(`pas de chemin réseau (ICE ${ice})`) : e;
    }
    return {nom: 'directe', envoyer: l => { if(conn.open) conn.send(l); }, fermer: () => peer.destroy(),
            brancher(recevoir, perdu){ conn.on('data', recevoir); conn.on('close', perdu); }};
  } catch(e){ peer.destroy(); throw e; }
}

// ── Relais MQTT : MQTT 3.1.1 sur WebSocket, qualité de service 0. Une
// liste propre peut remplacer les serveurs publics :
//   localStorage['aslweb.relais'] = '["wss://…/mqtt"]'
const RELAIS_PUBLICS = ['wss://broker.emqx.io:8084/mqtt', 'wss://broker.hivemq.com:8884/mqtt',
                        'wss://test.mosquitto.org:8081/mqtt'];
const CLE_RELAIS = 'aslweb.relais';
const NET_MORCEAU = 16000;                  // caractères par publication
function relaisReseau(){
  try {
    const l = JSON.parse(_lireLocal(CLE_RELAIS, 'null'));
    if(Array.isArray(l) && l.some(u => /^wss?:\/\//.test(u))) return l.filter(u => /^wss?:\/\//.test(u));
  } catch(e){}
  return RELAIS_PUBLICS;
}

// Connexion à un serveur MQTT. `testament` : le message que le serveur
// publie si la connexion meurt sans DISCONNECT (page fermée, réseau perdu).
function _mqtt(url, id, testament){
  return new Promise((ok, ko) => {
    let ws;
    try { ws = new WebSocket(url, 'mqtt'); } catch(e){ ko(e); return; }
    ws.binaryType = 'arraybuffer';
    const enc = new TextEncoder(), dec = new TextDecoder();
    const octets = (...l) => {
      const u = new Uint8Array(l.reduce((s, x) => s + x.length, 0));
      let i = 0;
      for(const x of l){ u.set(x, i); i += x.length; }
      return u;
    };
    const chaine = s => { const b = typeof s === 'string' ? enc.encode(s) : s; return octets([b.length >> 8, b.length & 255], b); };
    const paquet = (type, corps) => {
      const lg = [];
      let r = corps.length;
      do { let d = r % 128; r = Math.floor(r / 128); if(r) d |= 128; lg.push(d); } while(r);
      return octets([type], lg, corps);
    };
    const envoyer = p => { if(ws.readyState === 1) ws.send(p); };
    const c = {ouvert: false, surMessage: null, surFin: null, pid: 0, attentes: new Map()};
    let recu = new Uint8Array(0), ping = null;
    const delai = setTimeout(() => { ko(new Error('serveur injoignable')); try { ws.close(); } catch(e){} }, 10000);
    ws.onopen = () => {
      let drapeaux = 0x02;                      // session propre
      const charge = [chaine(id)];
      if(testament){ drapeaux |= 0x04; charge.push(chaine(testament.sujet), chaine(testament.texte)); }
      envoyer(paquet(0x10, octets(chaine('MQTT'), [4, drapeaux, 0, 30], ...charge)));
    };
    const traiter = (type, corps) => {
      switch(type >> 4){
        case 2:                                 // CONNACK
          clearTimeout(delai);
          if(corps[1] !== 0){ ko(new Error('connexion refusée (' + corps[1] + ')')); ws.close(); return; }
          c.ouvert = true;
          ping = setInterval(() => envoyer(new Uint8Array([0xC0, 0])), 20000);
          ok(c);
          break;
        case 3: {                               // PUBLISH
          const lt = (corps[0] << 8) | corps[1];
          const sujet = dec.decode(corps.subarray(2, 2 + lt));
          const debut = 2 + lt + ((type >> 1) & 3 ? 2 : 0);
          if(c.surMessage) c.surMessage(sujet, dec.decode(corps.subarray(debut)));
          break;
        }
        case 9: {                               // SUBACK
          const f = c.attentes.get((corps[0] << 8) | corps[1]);
          if(f) f(corps[2] !== 0x80);
          break;
        }
      }
    };
    ws.onmessage = e => {
      recu = octets(recu, new Uint8Array(e.data));
      for(;;){
        let lg = 0, m = 1, i = 1, d;
        do { if(i >= recu.length) return; d = recu[i++]; lg += (d & 127) * m; m *= 128; } while(d & 128);
        if(recu.length < i + lg) return;
        const type = recu[0], corps = recu.slice(i, i + lg);
        recu = recu.slice(i + lg);
        traiter(type, corps);
      }
    };
    ws.onclose = ws.onerror = () => {
      clearTimeout(delai); clearInterval(ping);
      if(!c.ouvert){ ko(new Error('serveur injoignable')); return; }
      c.ouvert = false;
      const f = c.surFin;
      c.surFin = null;
      if(f) f();
    };
    c.abonner = sujet => new Promise((oui, non) => {
      const pid = c.pid = c.pid % 65535 + 1;
      const t = setTimeout(() => { c.attentes.delete(pid); non(new Error('abonnement sans réponse')); }, 8000);
      c.attentes.set(pid, r => { clearTimeout(t); c.attentes.delete(pid); r ? oui() : non(new Error('abonnement refusé')); });
      envoyer(paquet(0x82, octets([pid >> 8, pid & 255], chaine(sujet), [0])));
    });
    c.publier = (sujet, texte) => envoyer(paquet(0x30, octets(chaine(sujet), enc.encode(texte))));
    // DISCONNECT : le testament n'est pas publié.
    c.fermer = () => { c.surFin = null; envoyer(new Uint8Array([0xE0, 0])); try { ws.close(); } catch(e){} };
  });
}

// Sujets et chiffrement d'une salle, tirés de son code : le serveur de
// relais ne voit ni le code ni les coups. AES-GCM si le navigateur
// l'offre (page en https) ; sinon les messages vont en clair.
async function _canalRelais(code){
  const enc = new TextEncoder();
  let base, cle = null;
  if(window.crypto && crypto.subtle){
    const h = new Uint8Array(await crypto.subtle.digest('SHA-256', enc.encode('aslweb-salle|' + code)));
    base = 'aslweb/' + [...h.subarray(0, 12)].map(x => x.toString(16).padStart(2, '0')).join('');
    const k = await crypto.subtle.digest('SHA-256', enc.encode('aslweb-cle|' + code));
    cle = await crypto.subtle.importKey('raw', k, 'AES-GCM', false, ['encrypt', 'decrypt']);
  } else base = 'aslweb/' + _empreinte('aslweb-salle|' + code) + _empreinte(code + '|aslweb-salle');
  return {
    serveur: base + '/s', fin: base + '/f', client: id => base + '/c/' + id,
    async emballer(o){
      const t = JSON.stringify(o);
      if(!cle) return t;
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const ch = new Uint8Array(await crypto.subtle.encrypt({name: 'AES-GCM', iv}, cle, enc.encode(t)));
      return '~' + _b64(iv) + '.' + _b64(ch);
    },
    async deballer(t){
      try {
        if(t[0] !== '~') return cle ? null : JSON.parse(t);
        if(!cle) return null;
        const [iv, ch] = t.substring(1).split('.');
        return JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({name: 'AES-GCM', iv: _deB64(iv)}, cle, _deB64(ch))));
      } catch(e){ return null; }
    }
  };
}

// Envoi d'une ligne vers `sujet`, en morceaux, dans l'ordre malgré le
// chiffrement asynchrone. `entete` accompagne chaque morceau.
function _voieRelais(c, canal, sujet, entete){
  let file = Promise.resolve(), k = 0;
  return l => {
    const nb = Math.max(1, Math.ceil(l.length / NET_MORCEAU)), id = ++k;
    for(let i = 0; i < nb; i++){
      const l2 = l.substring(i * NET_MORCEAU, (i + 1) * NET_MORCEAU);
      file = file.then(async () => { if(c.ouvert) c.publier(sujet, await canal.emballer({...entete, k: id, i, n: nb, l: l2})); })
                 .catch(e => console.error(e));
    }
  };
}
// Réception : déchiffrée et recollée, dans l'ordre d'arrivée.
function _recepteurRelais(c, canal, traiter){
  let file = Promise.resolve();
  const morceaux = new Map();
  c.surMessage = (sujet, texte) => {
    file = file.then(async () => {
      const e = await canal.deballer(texte);
      if(!e || typeof e !== 'object') return;
      if(typeof e.l === 'string' && e.n > 1){
        const cle = e.de + '|' + e.k, t = morceaux.get(cle) || [];
        t[e.i] = e.l;
        morceaux.set(cle, t);
        if(t.filter(x => typeof x === 'string').length < e.n) return;
        morceaux.delete(cle);
        e.l = t.join('');
      }
      traiter(sujet, e);
    }).catch(err => console.error(err));
  };
}

// L'hôte s'abonne sur chaque serveur de relais joignable. Un invité y est
// une connexion (`cnx`) : une nouvelle connexion remplace l'ancienne, et
// la fin d'une ancienne (son testament, publié en retard) est ignorée.
async function _hebergerRelais(n, srv){
  const canal = await _canalRelais(n.code);
  const ouverts = new Map();                  // url → client MQTT
  let actif = true;
  const ouvrir = async url => {
    const c = await _mqtt(url, 'aslw' + Math.random().toString(36).substring(2, 14),
                          {sujet: canal.fin, texte: await canal.emballer({fin: 1})});
    const liens = new Map();                  // invité → lien
    _recepteurRelais(c, canal, (sujet, e) => {
      if(sujet !== canal.serveur || !e.de) return;
      let lien = liens.get(e.de);
      if(e.fin){
        if(lien && lien.cnx === e.cnx){ srv.quitter(lien); liens.delete(e.de); }
        return;
      }
      if(!lien || lien.cnx !== e.cnx){
        if(lien) srv.quitter(lien);
        lien = {cnx: e.cnx, envoyer: _voieRelais(c, canal, canal.client(e.de), {})};
        liens.set(e.de, lien);
      }
      if(e.ping) canal.emballer({pong: 1}).then(t => c.publier(canal.client(e.de), t));
      else if(typeof e.l === 'string') srv.recevoir(lien, e.l);
    });
    await c.abonner(canal.serveur).catch(err => { c.fermer(); throw err; });
    if(!actif){ c.fermer(); return c; }
    ouverts.set(url, c);
    // Connexion perdue : les invités de ce serveur le sont aussi ; on y
    // revient tant que la salle est ouverte.
    c.surFin = () => {
      ouverts.delete(url);
      for(const l of liens.values()) srv.quitter(l);
      const relancer = d => setTimeout(() => {
        if(actif && _net === n) ouvrir(url).catch(() => relancer(Math.min(d * 2, 30000)));
      }, d);
      relancer(2000);
    };
    return c;
  };
  const r = await Promise.allSettled(relaisReseau().map(ouvrir));
  const nb = r.filter(x => x.status === 'fulfilled').length;
  if(!nb){ actif = false; throw new Error('aucun serveur de relais joignable'); }
  return {info: `${nb}/${r.length}`, fermer: () => {
    actif = false;
    for(const c of ouverts.values())
      canal.emballer({fin: 1, definitif: 1}).then(t => { c.publier(canal.fin, t); c.fermer(); });
    ouverts.clear();
  }};
}

// Relais de l'invité : un ping sur chaque serveur, le premier où l'hôte
// répond est gardé. Perdu en cours de partie, il est repris, puis
// l'invité se réinscrit et se resynchronise, comme une nouvelle arrivée.
async function _essaiRelais(n){
  const canal = await _canalRelais(n.code);
  const connecter = async () => {
    const cnx = Math.random().toString(36).substring(2, 10);
    const testament = {sujet: canal.serveur, texte: await canal.emballer({de: n.moi, cnx, fin: 1})};
    let gagne = null;
    const essais = relaisReseau().map(async url => {
      const c = await _mqtt(url, 'aslw' + cnx + Math.random().toString(36).substring(2, 6), testament);
      try {
        const pong = new Promise(ok => _recepteurRelais(c, canal, (s, e) => { if(e.pong) ok(); }));
        await c.abonner(canal.client(n.moi));
        await c.abonner(canal.fin);
        c.publier(canal.serveur, await canal.emballer({de: n.moi, cnx, ping: 1}));
        await _attendre(ok => pong.then(ok), 8000, 'pas d\'hôte sur le relais');
        if(gagne) throw new Error('inutile');
        gagne = c;
        return c;
      } catch(e){ c.fermer(); throw e; }
    });
    try { return {c: await Promise.any(essais), cnx}; }
    catch(e){ throw new Error([...new Set((e.errors || [e]).map(x => x.message))].join(', ')); }
  };
  let {c, cnx} = await connecter();
  let recevoir = null, perdu = null, fini = false, enReprise = false, envoyerLigne = null;
  const brancherC = () => {
    envoyerLigne = _voieRelais(c, canal, canal.serveur, {de: n.moi, cnx});
    _recepteurRelais(c, canal, (sujet, e) => {
      if(sujet === canal.fin){ if(e.definitif){ fini = true; c.fermer(); if(perdu) perdu(); } else reprise(); }
      else if(typeof e.l === 'string' && recevoir) recevoir(e.l);
    });
    c.surFin = () => reprise();
  };
  const reprise = async () => {
    if(fini || enReprise) return;
    enReprise = true;
    c.fermer();
    chatLog('📶 Liaison au relais perdue : reconnexion…');
    for(let i = 0; i < 10 && !fini && _net === n; i++){
      await new Promise(ok => setTimeout(ok, 3000));
      if(fini || _net !== n) break;
      try { ({c, cnx} = await connecter()); } catch(e){ continue; }
      brancherC();
      enReprise = false;
      chatLog('📶 Relais retrouvé : nouvelle synchronisation');
      n.attente = null; n.dernierSync = 0;
      _netInscrire(n);
      return;
    }
    enReprise = false;
    if(!fini && perdu) perdu();
  };
  brancherC();
  return {nom: 'relais', envoyer: l => envoyerLigne(l),
    fermer(){
      fini = true;
      const c0 = c;
      canal.emballer({de: n.moi, cnx, fin: 1}).then(t => { c0.publier(canal.serveur, t); c0.fermer(); });
    },
    brancher(r, p){ recevoir = r; perdu = p; }};
}

async function _hebergerInternet(n, srv){
  const fermetures = [];
  n.fermerTransport = () => { n.ferme = true; fermetures.splice(0).forEach(f => { try { f(); } catch(e){} }); };
  const garder = (p, nom) => p.then(v => {
    if(_net !== n || n.ferme){ v.fermer(); return v; }
    fermetures.push(v.fermer);
    n.voies[nom] = v.info || true;
    majNet();
    return v;
  });
  const d = garder(_hebergerPeer(n, srv), 'directe'), r = garder(_hebergerRelais(n, srv), 'relais');
  try { await Promise.any([d, r]); }
  catch(e){ throw new Error(`ni voie directe (${e.errors[0].message}), ni relais (${e.errors[1].message})`); }
  // La voie directe met souvent plus longtemps : on lui laisse un instant.
  await Promise.race([Promise.allSettled([d, r]), new Promise(ok => setTimeout(ok, 3000))]);
  d.catch(e => { if(_net === n) chatLog(`⚠️ Voie directe indisponible (${e.message}) : les invités passeront par le relais`); });
  r.catch(e => { if(_net === n) chatLog(`⚠️ Relais indisponible (${e.message}) : seule la voie directe est ouverte`); });
}

async function _rejoindreInternet(n){
  const d = _essaiPeer(n), r = _essaiRelais(n);
  let direct = null;
  d.then(v => { direct = v; }, () => {});
  let g;
  try { g = await Promise.any([d, r]); }
  catch(e){ throw new Error(`voie directe : ${e.errors[0].message} ; relais : ${e.errors[1].message}`); }
  // Le relais répond d'ordinaire le premier : la voie directe, plus
  // rapide ensuite, a encore quelques secondes pour s'ouvrir.
  if(g !== direct) g = await Promise.race([d.catch(() => g), new Promise(ok => setTimeout(() => ok(g), 4000))]);
  for(const p of [d, r]) p.then(v => { if(v !== g) v.fermer(); }, () => {});
  if(_net !== n){ g.fermer(); throw new Error('connexion abandonnée'); }
  n.envoyer = g.envoyer;
  n.fermerTransport = g.fermer;
  n.voies[g.nom] = true;
  g.brancher(l => _netRecevoir(n, l), () => _netPerdu(n));
  d.catch(e => { if(_net === n && g.nom === 'relais') chatLog(`📶 Voie directe impossible (${e.message}) : la partie passe par le relais`); });
}

function _hebergerOnglets(n, srv){
  const bc = new BroadcastChannel(NET_PREFIXE + n.code);
  const liens = new Map();
  bc.onmessage = e => {
    const {de, a, ligne, fin} = e.data || {};
    if(a !== 'serveur' || !de) return;
    let lien = liens.get(de);
    if(!lien){ lien = {envoyer: l => bc.postMessage({a: de, ligne: l})}; liens.set(de, lien); }
    if(fin){ srv.quitter(lien); liens.delete(de); }
    else srv.recevoir(lien, ligne);
  };
  n.fermerTransport = () => { bc.postMessage({a: '*', fin: true}); bc.close(); };
}

function _rejoindreOnglets(n){
  const bc = new BroadcastChannel(NET_PREFIXE + n.code);
  bc.onmessage = e => {
    const {a, ligne, fin} = e.data || {};
    if(a === '*' && fin) _netPerdu(n);
    else if(a === n.moi) _netRecevoir(n, ligne);
  };
  n.envoyer = l => bc.postMessage({de: n.moi, a: 'serveur', ligne: l});
  n.fermerTransport = () => { bc.postMessage({de: n.moi, a: 'serveur', fin: true}); bc.close(); };
}

// ── Le client (NodeClient).
function _nouvelleConnexion(hote, moyen, code, salle){
  return {hote, moyen, code, salleVoulue: salle, moi: 'p' + Math.random().toString(36).substring(2, 10),
          salle: null, joueurs: [], proprios: {}, attente: null, dernierSync: 0,
          envoi: Promise.resolve(), reception: Promise.resolve(), tampon: null,
          voies: {}, aReprendre: [], envoyer: () => {}, fermerTransport: () => {}};
}

function _codeSalle(){
  const a = 'abcdefghjkmnpqrstuvwxyz23456789', t = new Uint8Array(6);
  crypto.getRandomValues(t);
  return [...t].map(x => a[x % a.length]).join('');
}

async function heberger(moyen, salle){
  if(_net) netQuitter();
  const n = _nouvelleConnexion(true, moyen, _codeSalle(), _nomSalle(salle) || 'Partie de ' + joueur());
  const srv = creerServeur();
  n.serveur = srv;
  _net = n;
  try {
    if(moyen === 'onglets') _hebergerOnglets(n, srv); else await _hebergerInternet(n, srv);
  } catch(e){ _netEchec(n, e); throw e; }
  // Le client de l'hôte parle à son serveur sans réseau, mais pas dans le
  // même tour, comme à travers une socket.
  const lien = {envoyer: l => setTimeout(() => _netRecevoir(n, l))};
  n.envoyer = l => setTimeout(() => srv.recevoir(lien, l));
  _netInscrire(n);
  _netRejoindreSalle(n, n.salleVoulue);
  chatLog(`🌐 Salle « ${n.salleVoulue} » ouverte — code ${n.code} (${_texteLiaison(n)})`);
  majNet();
  return n.code;
}

async function rejoindre(moyen, code){
  code = String(code || '').trim().toLowerCase().replace(/^aslweb-/, '');
  if(!/^[a-z0-9]{4,16}$/.test(code)) throw new Error('code de salle invalide');
  if(_net) netQuitter();
  const n = _nouvelleConnexion(false, moyen, code, null);
  _net = n;
  try {
    if(moyen === 'onglets') _rejoindreOnglets(n); else await _rejoindreInternet(n);
  } catch(e){ _netEchec(n, e); throw e; }
  _netInscrire(n);
  chatLog(`🌐 Connexion à la salle ${code} (${_texteLiaison(n)})…`);
  // Sans LIST du serveur, personne n'héberge ce code.
  setTimeout(() => {
    if(_net === n && !n.joueurs.length){ chatLog('❌ Aucune réponse : salle fermée ou code erroné'); netQuitter(); }
  }, 8000);
  majNet();
}

// Liaison en service : onglets, ou voies Internet ouvertes.
function _texteLiaison(n){
  if(n.moyen === 'onglets') return 'onglets de ce navigateur';
  const v = [];
  if(n.voies.directe) v.push('directe');
  if(n.voies.relais) v.push(n.hote ? `relais ${n.voies.relais}` : 'relais');
  return 'Internet' + (v.length ? ' : ' + v.join(' + ') : '');
}

function _netEchec(n, e){
  if(_net === n) _net = null;
  try { n.fermerTransport(); } catch(err){}
  chatLog('❌ Réseau : ' + (e && e.message ? e.message : e));
  majNet();
}

function netQuitter(){
  const n = _net;
  if(!n) return;
  _net = null;
  try { n.fermerTransport(); } catch(e){}
  chatLog(n.hote ? '🔌 Salle fermée' : '🔌 Déconnecté de la salle');
  majNet();
}

function _netPerdu(n){
  if(_net !== n) return;
  _net = null;
  try { n.fermerTransport(); } catch(e){}
  chatLog('🔌 Connexion perdue : l\'hôte a fermé la salle');
  majNet();
}

// Les lignes partent dans l'ordre, même quand une compression les retarde.
function _netLigne(n, ligne){
  n.envoi = n.envoi.then(async () => {
    if(typeof ligne === 'function') ligne = await ligne();
    if(_net === n) n.envoyer(ligne);
  }).catch(e => console.error(e));
}
function _netForward(n, chemin, msg){
  _netLigne(n, async () => 'FWD\t' + chemin + '\t'
    + (msg.length > NET_ZIP_SEUIL ? NET_ZIP + await _netZip(msg) : msg));
}
const _netSalleCourante = n => n && n.salle && n.salle !== NET_SALLE_DEFAUT ? n.salle : null;
// sendToOthers : toute la salle, sauf soi.
function _netAuxAutres(n, msg){
  const s = _netSalleCourante(n);
  if(s) _netForward(n, `${NET_MODULE}/${s}/~${n.moi}`, msg);
}
// sendTo : un joueur, où qu'il soit.
const _netA = (n, id, msg) => _netForward(n, `${NET_MODULE}/*/${id}`, msg);

function _netInscrire(n){
  _netLigne(n, `REG\t${n.moi}\t${NET_MODULE}/${NET_SALLE_DEFAUT}\t${_netInfo(n)}`);
}
// setRoom : JOIN ; si l'on n'y est pas le premier, la synchronisation
// attend la LIST qui confirme l'arrivée.
function _netRejoindreSalle(n, salle){
  _netLigne(n, `JOIN\t${NET_MODULE}/${salle}`);
  const occupee = n.joueurs.some(j => j.salle === salle && j.id !== n.moi);
  if(occupee){ n.attente = salle; chatLog('🔄 Synchronisation en attente…'); }
}
// Nouveau nom (seConnecter) : STATS, comme setUserInfo.
function _netMajInfo(){
  if(_net) _netLigne(_net, 'STATS\t' + _netInfo(_net));
}

function _netRecevoir(n, ligne){
  if(typeof ligne !== 'string') return;
  n.reception = n.reception.then(() => _netTraiter(n, ligne)).catch(e => {
    console.error(e); chatLog('❌ Réseau : ' + (e && e.message ? e.message : e));
  });
}

async function _netTraiter(n, ligne){
  if(_net !== n) return;
  if(ligne.startsWith('LIST\t')){ _netListe(n, ligne.substring(5)); return; }
  if(ligne.startsWith('ROOM_INFO\t')){
    const s = _propsDec(ligne.substring(10));
    n.proprios = {};
    for(const k in s) n.proprios[k] = _propsDec(s[k]).owner;
    majNet();
    return;
  }
  if(ligne.startsWith('REG_REQUEST')){ _netInscrire(n); return; }
  if(ligne.startsWith(NET_ZIP)) ligne = await _netDezip(ligne.substring(NET_ZIP.length));
  // Commandes du module (CommandDecoder).
  if(ligne.startsWith('SYNC')) _netRepondreSync(n, _propsDec(ligne.substring(4)));
  else if(ligne.startsWith('begin_save')) await _netRestaurer(n, ligne);
  else _netAppliquer(ligne);
}

function _netListe(n, corps){
  const avant = new Map(n.joueurs.map(j => [j.id, j]));
  n.joueurs = [];
  for(const e of corps ? corps.split('\t') : []){
    const i = e.indexOf('=');
    const ch = (i < 0 ? e : e.substring(0, i)).split('/');
    if(ch[0] !== NET_MODULE || ch.length < 3) continue;
    n.joueurs.push({..._propsDec(i < 0 ? '' : e.substring(i + 1)), salle: ch[1], id: ch[2]});
  }
  const moi = n.joueurs.find(j => j.id === n.moi);
  n.salle = moi ? moi.salle : null;
  // Arrivées et départs dans ma salle.
  const s = _netSalleCourante(n);
  if(s){
    for(const j of n.joueurs)
      if(j.id !== n.moi && j.salle === s && (!avant.has(j.id) || avant.get(j.id).salle !== s)) chatLog(`🔌 ${j.name || j.id} est dans la salle`);
    for(const [id, j] of avant)
      if(id !== n.moi && j.salle === s && !n.joueurs.some(x => x.id === id && x.salle === s)) chatLog(`🔌 ${j.name || id} a quitté la salle`);
  }
  if(n.attente && n.salle === n.attente){
    // Arrivé : SYNC au propriétaire, le premier occupant.
    const salle = n.attente;
    n.attente = null;
    const proprio = n.joueurs.find(j => j.salle === salle);
    if(proprio && proprio.id !== n.moi){
      netSynchroniser(proprio);
      const msg = `* ${joueur()} rejoint la salle « ${salle} »`;
      afficherChat(msg);
      _netAuxAutres(n, 'CHAT' + msg.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'));
    }
  } else if(!n.hote && !n.attente && n.salle === NET_SALLE_DEFAUT){
    // Invité : il entre dans la salle de l'hôte.
    const cible = n.joueurs.find(j => j.salle !== NET_SALLE_DEFAUT);
    if(cible) _netRejoindreSalle(n, cible.salle);
  }
  majNet();
}

const _netProprio = n => {
  const s = _netSalleCourante(n);
  return s ? (n.proprios[s] || (n.joueurs.find(j => j.salle === s) || {}).id) : null;
};
const netProprietaire = () => !_net || !_netSalleCourante(_net) || _netProprio(_net) === _net.moi;

// SynchAction : pas deux demandes à la même salle en moins de 15 s.
function netSynchroniser(cible){
  const n = _net;
  if(!n) return false;
  cible = cible || n.joueurs.find(j => j.id === _netProprio(n));
  if(!cible || cible.id === n.moi) return false;
  const t = Date.now();
  if(t - n.dernierSync < NET_SYNC_TROP_TOT){ chatLog('⏳ Synchronisation demandée il y a moins de 15 s'); return false; }
  n.dernierSync = t;
  _netA(n, cible.id, 'SYNC' + _netInfo(n));
  chatLog(`🔄 Synchronisation demandée à ${cible.name || cible.id}`);
  return true;
}

// SynchCommand : l'état complet de la partie, au seul demandeur.
function _netRepondreSync(n, dem){
  if(!dem.id) return;
  if(!_source){ chatLog(`⚠️ ${dem.name || dem.id} demande la partie : aucune n'est ouverte ici`); return; }
  chatLog(`📤 Envoi de la partie à ${dem.name || dem.id}`);
  _netA(n, dem.id, etatPartie().join('\x1b'));
}

// Le propriétaire qui ouvre une partie l'envoie aux occupants de la salle.
function netPartieChargee(){
  const n = _net;
  if(!n || !_source || !_netSalleCourante(n) || !netProprietaire()) return;
  const autres = n.joueurs.filter(j => j.salle === n.salle && j.id !== n.moi);
  if(!autres.length) return;
  chatLog(`📤 Partie envoyée à ${autres.map(j => j.name || j.id).join(', ')}`);
  _netAuxAutres(n, etatPartie().join('\x1b'));
}

async function _netRestaurer(n, texte){
  chatLog('📥 Partie reçue de la salle');
  if(!await chargerPartie(texte, n.salle || 'salle', new Map())) return;
  n.synchro = true;
  chatLog('✅ Synchronisation terminée');
  majNet();
  // Un joueur du fichier se fait reconnaître pour voir ses pions masqués.
  if(!_utilisateur && !_admin) demanderUtilisateur();
}

// Coup reçu : exécuté, consigné, puis montré comme une relecture.
function _netAppliquer(texte){
  const segs = texte.split('\x1b').filter(s => s);
  if(!segs.length) return;
  _avantAction();
  const photo = _photo();
  _netReception = true;
  try {
    rejouerLog(segs);
    segs.forEach(_logCmd);
  } finally { _netReception = false; }
  rendreToutes();
  if(_pileSel) afficherPile();
  signalerCoup(photo);
}

// Coup joué ici : ses commandes, nées d'une même action, partent en une
// commande composée, comme GameModule.encode.
function _netCoup(c){
  const n = _net;
  if(!n || _netReception || !_netSalleCourante(n)) return;
  if(!n.tampon){
    n.tampon = [];
    setTimeout(() => {
      const l = n.tampon;
      n.tampon = null;
      if(_net === n && l.length) _netAuxAutres(n, l.length > 1 ? l.map(x => '\x1b' + x).join('') : l[0]);
    }, 0);
  }
  n.tampon.push(c);
}

function majNet(){
  rafraichirActions(['reseau']);
  const f = document.getElementById('fenUtil');
  if(f && f.dataset.net) ouvrirReseau();
}

// ── Fenêtre « Partie à distance ».
function ouvrirReseau(){
  const ancien = document.getElementById('fenUtil');
  const garde = ancien && ancien.dataset.net ? {moyen: (ancien.querySelector('#nwMoyen') || {}).value,
    salle: (ancien.querySelector('#nwSalle') || {}).value, code: (ancien.querySelector('#nwCode') || {}).value} : {};
  fermerFenetres();
  const n = _net;
  const w = document.createElement('div');
  w.id = 'fenUtil'; w.className = 'fenetre'; w.dataset.net = '1';
  const qui = _esc(joueur() === 'web' ? 'Observateur' : joueur());
  if(!n){
    w.innerHTML = `
      <div class="ft">Partie à distance</div>
      <div class="fc">
        <p>Comme le serveur de VASSAL : l'hôte ouvre une salle et donne son code ; qui la
          rejoint reçoit la partie du propriétaire, puis chaque coup joué part chez les autres.</p>
        <p>Vous jouez sous le nom <b>${qui}</b>.</p>
        <label>Liaison<select id="nwMoyen">
          <option value="internet">Internet (direct ou par relais)</option>
          <option value="onglets">Onglets de ce navigateur</option></select></label>
        <label>Nom de la salle (pour héberger)<input id="nwSalle" maxlength="40" autocomplete="off"
          value="${_esc(garde.salle || (_source ? _nomFichier : 'Partie de ' + joueur()))}"></label>
        <label>Code de la salle (pour rejoindre)<input id="nwCode" maxlength="16" autocomplete="off"
          autocapitalize="none" autocorrect="off" spellcheck="false"
          placeholder="ex. k7m2qa" value="${_esc(garde.code || '')}"></label>
        <div class="ferr" id="nwErr"></div>
        <div class="fb">
          <button id="nwHeberger" class="prim">Héberger</button>
          <button id="nwRejoindre">Rejoindre</button>
          <button id="nwIdent">Identification…</button>
          <button id="nwFermer">Fermer</button>
        </div>
        <p class="fnote">Internet : l'invité rejoint l'hôte en direct (WebRTC) quand le réseau le
          permet, sinon par un relais chiffré (serveurs MQTT publics). L'hôte doit garder sa page
          ouverte, au premier plan sur un téléphone : elle tient lieu de serveur. Mieux vaut
          héberger sur le PC.</p>
      </div>`;
  } else {
    const s = _netSalleCourante(n), proprio = _netProprio(n);
    const lignes = n.joueurs.filter(j => !s || j.salle === s);
    w.innerHTML = `
      <div class="ft">Partie à distance</div>
      <div class="fc">
        <p>${s ? `Salle <b>${_esc(s)}</b>` : 'Connexion en cours…'} — code
          <b id="nwCodeAff" style="font-family:monospace;font-size:15px">${_esc(n.code)}</b>
          <button id="nwCopier" class="petit">Copier</button>
          <br>Liaison : ${_esc(_texteLiaison(n))}${n.hote ? ' — vous hébergez' : ''}.</p>
        <table><tr><th>Joueur</th><th></th></tr>
          ${lignes.map(j => `<tr><td>${_esc(j.name || j.id)}${j.id === n.moi ? ' <b>(vous)</b>' : ''}</td>
            <td>${j.id === proprio ? 'propriétaire' : ''}</td></tr>`).join('')
            || '<tr><td colspan="2"><i>En attente du serveur…</i></td></tr>'}</table>
        <div class="ferr" id="nwErr"></div>
        <div class="fb">
          ${s && proprio !== n.moi ? '<button id="nwSync" class="prim">Synchroniser</button>' : ''}
          <button id="nwQuitter">${n.hote ? 'Fermer la salle' : 'Quitter la salle'}</button>
          <button id="nwFermer">Fermer</button>
        </div>
        <p class="fnote">${n.hote ? 'Donner le code à l\'adversaire. Fermer cette page ferme la salle.'
          : 'Synchroniser redemande l\'état de la partie au propriétaire de la salle.'}</p>
      </div>`;
  }
  document.body.appendChild(w);
  const $ = id => document.getElementById(id);
  $('nwFermer').onclick = () => w.remove();
  const err = $('nwErr');
  const tenter = async f => {
    err.textContent = '';
    w.querySelectorAll('button').forEach(b => b.disabled = true);
    try { await f(); } catch(e){ err.textContent = e && e.message ? e.message : String(e); }
    if(w.isConnected) w.querySelectorAll('button').forEach(b => b.disabled = false);
  };
  if(!n){
    if(garde.moyen) $('nwMoyen').value = garde.moyen;
    $('nwHeberger').onclick = () => tenter(() => heberger($('nwMoyen').value, $('nwSalle').value));
    $('nwRejoindre').onclick = () => tenter(() => {
      if(!$('nwCode').value.trim()){ $('nwCode').focus(); throw new Error('Saisir le code donné par l\'hôte.'); }
      return rejoindre($('nwMoyen').value, $('nwCode').value);
    });
    $('nwIdent').onclick = () => demanderUtilisateur();
    $('nwCode').addEventListener('keydown', e => { if(e.key === 'Enter') $('nwRejoindre').click(); });
  } else {
    $('nwQuitter').onclick = () => { netQuitter(); w.remove(); };
    $('nwCopier').onclick = () => {
      try { navigator.clipboard.writeText(n.code).then(() => { err.textContent = ''; $('nwCopier').textContent = 'Copié'; }); }
      catch(e){ err.textContent = 'Copie impossible : noter le code.'; }
    };
    if($('nwSync')) $('nwSync').onclick = () => { if(!netSynchroniser()) err.textContent = 'Synchronisation impossible pour l\'instant.'; };
  }
}

