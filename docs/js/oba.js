// ═══════════════════════════════════════════════════════════════
// OBA — les pioches de cartes de VASL (VASL.build.module.OBA, bouton
// « OBA », F6, fenêtre « OBA Modules »)
// Un module OBA est une pioche de cartes noires et rouges (Battery Access,
// C1.21) : 8 noires et 3 rouges à sa création. Il appartient au joueur qui
// l'a ajouté (son identifiant VASSAL : ici le mot de passe, comme pour les
// masques) ; lui seul tire, remet une carte, change les nombres ou le nom.
// Les autres voient la pioche et les cartes tirées.
//   · un appui sur la pioche tire une carte, noire avec la probabilité
//     noires / (noires + rouges), et l'annonce au chat :
//       « *** Battery Access Draw = Black *** » (ou Red) ;
//   · un appui sur une carte tirée la remet dans la pioche (sans message) ;
//   · changer le nombre de noires ou de rouges vide les cartes tirées ;
//     0 noire et 0 rouge sans carte tirée retire le module.
// Commandes de VASL, séparateur « / » (SequenceEncoder) :
//   OBA/+/<rang>/<état>   CreateModule (aussi dans la sauvegarde)
//   OBA/D/<rang>/<état>   UpdateModule
//   OBA/-/<rang>          RemoveModule
// et l'état, séparateur « , » :
//   propriétaire,nom,rouges,noires,tirées,hexOBO,niveauOBO
// « tirées » : une lettre par carte tirée, « b » noire, « r » rouge.
// L'observateur hors carte (OBO) est gardé tel quel mais pas proposé :
// la LOS de cette page ne le prend pas en compte.
// Ils sont un onglet des tables QRDC : sous les boards en tactile, dans la
// fenêtre QRDC au PC. Le bouton OBA (F6) ouvre les tables sur cet onglet.
// ═══════════════════════════════════════════════════════════════
let _oba = [];                        // {owner, label, red, black, showing, obohex, obolevel}
const _obaInv = new Map();            // commande appliquée → ses inverses (Undo)

// SequenceEncoder.append : délimiteur précédé d'une contre-oblique ;
// entre apostrophes un jeton qui finit par une contre-oblique.
function _seqJeton(v, d){
  const t = String(v == null ? '' : v);
  const e = t.split(d).join('\\' + d);
  return t.endsWith('\\') || (t.startsWith("'") && t.endsWith("'")) ? `'${e}'` : e;
}
const _obaEtat = m => [m.owner, m.label, m.red, m.black, m.showing, m.obohex, m.obolevel]
  .map(x => _seqJeton(x, ',')).join(',');
function _obaLire(etat){
  const t = _decoupeSeq(etat || '', ',');
  const n = (v, d) => { const x = parseInt(v, 10); return Number.isFinite(x) ? x : d; };
  return {owner: t[0] || '', label: t[1] || '', red: n(t[2], 3), black: n(t[3], 8), showing: t[4] || '',
          obohex: t.length > 5 ? t[5] || '' : '', obolevel: t.length > 5 ? n(t[6], 0) : 0};
}
const _obaCmd = (op, i, m) => `OBA/${op}/${i}` + (m ? '/' + _seqJeton(_obaEtat(m), '/') : '');
const _cleOBA = c => c.replace(/^LOG\t/, '').replace(/\\+$/, '');
const _obaAMoi = m => voitMasque(m.owner);
function _segmentsOBA(){ return _oba.map((m, i) => _obaCmd('+', i, m)); }

// Exécution d'une commande OBA, jouée ici, reçue ou relue. Son inverse
// est noté pour Undo : l'état d'avant, comme UpdateModule.executeCommand.
function _obaAppliquer(c){
  const t = _decoupeSeq(_cleOBA(c), '/');
  if(t[0] !== 'OBA' || t.length < 3) return false;
  const i = parseInt(t[2], 10);
  if(!Number.isFinite(i) || i < 0) return false;
  let inv;
  if(t[1] === '+'){
    const k = Math.min(i, _oba.length);
    _oba.splice(k, 0, _obaLire(t[3]));
    inv = [`OBA/-/${k}`];
  } else if(t[1] === '-'){
    if(i >= _oba.length) return false;
    const [m] = _oba.splice(i, 1);
    inv = [_obaCmd('+', i, m)];
  } else if(t[1] === 'D'){
    const m = _oba[i];
    if(!m) return false;
    inv = [_obaCmd('D', i, m)];
    Object.assign(m, _obaLire(t[3]));
  } else return false;
  _obaInv.set(_cleOBA(c), inv);
  _majOBA();
  return true;
}
function _inverseOBA(c){
  const k = _cleOBA(c), inv = _obaInv.get(k);
  _obaInv.delete(k);
  return inv || null;
}

// Modules de la partie chargée : ses CreateModule (getRestoreCommand).
function lireOBA(segs){
  _oba = [];
  for(const s of segs) if(_cleOBA(s).startsWith('OBA/')) _obaAppliquer(s);
  _obaInv.clear();
  _majOBA();
  if(_oba.length) chatLog(`🎴 OBA : ${_oba.length} module(s)`);
}

// Un coup OBA joué ici : le message d'abord, puis la commande, comme le
// Chatter.DisplayText auquel VASL ajoute l'UpdateModule.
function _obaJouer(cmd, msg){
  _avantAction();
  _obaAppliquer(cmd);
  if(msg){ _logCmd('CHAT' + msg); afficherChat(msg); }
  _logCmd(cmd);
}

// Entier uniforme dans [0, n), sans biais.
function _obaHasard(n){
  const t = new Uint32Array(1), lim = Math.floor(0x100000000 / n) * n;
  do { crypto.getRandomValues(t); } while(t[0] >= lim);
  return t[0] % n;
}

function obaAjouter(){
  const m = {owner: idJoueur(), label: '', red: 3, black: 8, showing: '', obohex: '', obolevel: 0};
  _obaJouer(_obaCmd('+', _oba.length, m));
}

// DeckView.mouseReleased : tirer une carte de la pioche.
function obaTirer(i){
  const m = _oba[i];
  if(!m || !_obaAMoi(m) || m.red + m.black <= 0) return false;
  const noire = _obaHasard(m.red + m.black) < m.black;
  const n = {...m, showing: m.showing + (noire ? 'b' : 'r')};
  if(noire) n.black--; else n.red--;
  _obaJouer(_obaCmd('D', i, n), ` *** Battery Access Draw = ${noire ? 'Black' : 'Red'} ***`);
  return noire ? 'b' : 'r';
}

// … ou remettre une carte tirée dans la pioche.
function obaRemettre(i, j){
  const m = _oba[i];
  if(!m || !_obaAMoi(m) || j < 0 || j >= m.showing.length) return false;
  const n = {...m, showing: m.showing.slice(0, j) + m.showing.slice(j + 1)};
  if(m.showing[j] === 'r') n.red++; else n.black++;
  _obaJouer(_obaCmd('D', i, n));
  return true;
}

// ModuleControls.valuesUpdated : nouveaux nombres de cartes (les cartes
// tirées sont alors écartées), ou nouveau nom.
function obaRegler(i, champs){
  const m = _oba[i];
  if(!m || !_obaAMoi(m)) return false;
  const n = {...m, ...champs};
  n.red = Math.max(0, n.red); n.black = Math.max(0, n.black);
  if(n.red !== m.red || n.black !== m.black) n.showing = '';
  if(n.red + n.black === 0 && !n.showing){ _obaJouer(`OBA/-/${i}`); return true; }
  if(_obaEtat(n) === _obaEtat(m)) return false;
  _obaJouer(_obaCmd('D', i, n));
  return true;
}

function obaRetirer(i){
  const m = _oba[i];
  if(!m || !_obaAMoi(m)) return false;
  if(!confirm(T('Retirer ce module OBA ?'))) return false;
  _obaJouer(`OBA/-/${i}`);
  return true;
}

// ── Affichage : l'onglet OBA, sur la carte (tactile) ou dans la fenêtre.
function _dessinerOBA(){
  for(const b of document.querySelectorAll('.ongletOBA')) _rendreOBA(b);
}
function _majOBA(){
  _dessinerOBA();
  rafraichirActions(['oba', 'bin']);
}

// Les appuis sur les commandes ne sont pas pris par la carte (défilement).
function _brancherOBA(boite){
  boite.addEventListener('pointerdown', e => { if(e.target.closest('button, input')) e.stopPropagation(); });
}

function _rendreOBA(boite){
  // Un nom en cours de saisie n'est pas perdu par un coup reçu.
  const actif = document.activeElement;
  const saisie = actif && boite.contains(actif) && actif.classList.contains('obaNom')
    ? {i: +actif.dataset.i, v: actif.value, a: actif.selectionStart, b: actif.selectionEnd} : null;
  boite.replaceChildren();
  if(!_oba.length){
    const v = document.createElement('div');
    v.className = 'obaVide';
    v.textContent = 'Aucun module OBA.';
    boite.appendChild(v);
  }
  _oba.forEach((m, i) => boite.appendChild(_rendreModuleOBA(m, i)));
  const aj = document.createElement('button');
  aj.type = 'button';
  aj.className = 'obaAjout';
  aj.textContent = '+ Module OBA';
  aj.title = 'Ajouter une pioche OBA (8 noires, 3 rouges)';
  aj.addEventListener('click', obaAjouter);
  boite.appendChild(aj);
  if(saisie){
    const inp = boite.querySelector(`input.obaNom[data-i="${saisie.i}"]`);
    if(inp){ inp.value = saisie.v; inp.focus(); try { inp.setSelectionRange(saisie.a, saisie.b); } catch(e){} }
  }
}

function _rendreModuleOBA(m, i){
  const moi = _obaAMoi(m);
  const d = document.createElement('div');
  d.className = 'obaModule' + (moi ? ' moi' : '');
  d.dataset.i = i;
  const bouton = (cls, txt, titre, f) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = cls;
    if(txt) b.textContent = txt;
    if(titre) b.title = titre;
    if(f) b.addEventListener('click', f); else b.disabled = true;
    return b;
  };
  // Nom du module (« Module Name » de VASL).
  const tete = document.createElement('div');
  tete.className = 'obaTete';
  const titre = document.createElement('span');
  titre.className = 'obaTitre';
  titre.textContent = 'Module Name:';
  tete.appendChild(titre);
  if(moi){
    const inp = document.createElement('input');
    inp.className = 'obaNom';
    inp.dataset.i = i;
    inp.value = m.label;
    inp.setAttribute('aria-label', 'Module Name');
    inp.autocomplete = 'off';
    inp.addEventListener('change', () => obaRegler(i, {label: inp.value}));
    inp.addEventListener('keydown', e => { if(e.key === 'Enter') inp.blur(); });
    tete.appendChild(inp);
    tete.appendChild(bouton('obaSuppr', '✕', 'Retirer ce module', () => obaRetirer(i)));
  } else {
    const nom = document.createElement('span');
    nom.className = 'obaNomLu';
    nom.textContent = m.label || '—';
    const v = document.createElement('span');
    v.className = 'obaVerrou';
    v.textContent = '🔒';
    v.title = 'Module d\'un autre joueur';
    tete.append(nom, v);
  }
  d.appendChild(tete);
  // Pioche : B (noires) et R (rouges), comme « Draw Pile (B/R) » de VASL.
  const compte = document.createElement('div');
  compte.className = 'obaCompte';
  const reglage = (lib, cls, cle, moins, plus) => {
    const st = document.createElement('span');
    st.className = 'obaSt ' + cls;
    const l = document.createElement('span');
    l.textContent = lib;
    const v = document.createElement('b');
    v.textContent = m[cle];
    if(moi){
      st.append(l, bouton('', '−', moins, m[cle] > 0 ? () => obaRegler(i, {[cle]: m[cle] - 1}) : null),
                v, bouton('', '+', plus, () => obaRegler(i, {[cle]: m[cle] + 1})));
    } else st.append(l, v);
    return st;
  };
  const pioche = document.createElement('span');
  pioche.className = 'obaTitre';
  pioche.textContent = 'Draw Pile (B/R):';
  compte.append(pioche, reglage('B', 'obaNoir', 'black', 'Une carte noire de moins', 'Une carte noire de plus'),
                reglage('R', 'obaRouge', 'red', 'Une carte rouge de moins', 'Une carte rouge de plus'));
  d.appendChild(compte);
  // Cartes tirées, puis la pioche.
  const cartes = document.createElement('div');
  cartes.className = 'obaCartes';
  [...m.showing].forEach((c, j) => {
    if(c !== 'r' && c !== 'b') return;
    const nom = c === 'r' ? 'Rouge' : 'Noire';
    cartes.appendChild(bouton('obaCarte ' + c, '', moi ? `${nom} — remettre dans la pioche` : nom,
      moi ? () => obaRemettre(i, j) : null));
  });
  const reste = m.red + m.black;
  const p = bouton('obaPioche', '', reste ? (moi ? 'Tirer une carte' : 'Pioche') : 'Pioche vide',
    moi && reste ? () => obaTirer(i) : null);
  p.innerHTML = `<b>${reste}</b><small>${moi && reste ? _esc(T('Tirer')) : ''}</small>`;
  cartes.appendChild(p);
  d.appendChild(cartes);
  return d;
}

// ── Bouton OBA (F6) : les tables QRDC, ouvertes sur l'onglet OBA ; un
// second appui les referme. Sans partie ouverte, en tactile, la fenêtre.
const _ongletOBA = () => _bdd && _bdd.qrdc && _bdd.qrdc.oba;
const _surOngletOBA = () => { const o = _ongletOBA(); return !!o && (_tablesSel.get(o.onglets) || 0) === o.rang; };
const _tablesOuvertes = () => _telLike() && _empriseBoards ? !!_tablesEl : !!document.getElementById('fenQRDC');
const obaOuvert = () => _tablesOuvertes() && _surOngletOBA();

const basculerOBA = () => _basculerOnglet(_ongletOBA, obaOuvert, '.ongletOBA', 'OBA');

// Les tables QRDC ouvertes sur un onglet (OBA, Casualties) ; fermées si
// elles y sont déjà.
async function _basculerOnglet(onglet, ouvert, classe, nom){
  if(ouvert()){ _telLike() && _empriseBoards ? basculerTables() : fermerFenetreTables(); return; }
  try { await chargerBDD(); } catch(e){}
  const o = onglet();
  if(!o){ chatLog(`⚠️ Tables QRDC indisponibles : pas d'onglet ${nom}.`); return; }
  _tablesSel.set(o.onglets, o.rang);
  try { localStorage.setItem('qrdcSel', JSON.stringify([..._tablesSel])); } catch(e){}
  if(_telLike() && _empriseBoards){
    if(!_tablesEl) basculerTables(); else _dessinerTables();
    // Les tables sont sous les boards : la vue y descend.
    for(let n = 0; n < 40 && !document.querySelector('#tablesQRDC ' + classe); n++) await new Promise(r => setTimeout(r, 50));
    const z = document.querySelector('#tablesQRDC .qOnglets');
    if(z) z.scrollIntoView({block: 'start', inline: 'nearest', behavior: 'smooth'});
  } else if(document.getElementById('fenQRDC')) _dessinerTables();
  else await ouvrirFenetreTables();
  rafraichirActions(['oba', 'bin', 'tables']);
}

