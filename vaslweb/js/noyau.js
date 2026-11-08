// ═══════════════════════════════════════════════════════════════
// VASL — CARTE ET PIONS
// Repris de baseVaslWeb : lecture du .vsav, disposition des boards
// d'après ASLBoardPicker, images tirées du dépôt GitHub de VASL,
// recadrage, retournement, zoom et repeint des SSR de terrain ; puis
// les pions : import, palette du module, export .vsav et .vlog.
// ═══════════════════════════════════════════════════════════════
const SC = 1.6;                       // échelle d'affichage, comme l'original
const BOARD_MARGIN = 400;             // marge de carte VASSAL
const VASL_BOARDS_URL =
  'https://raw.githubusercontent.com/vasl-developers/vasl/develop/boards/bdFiles/';
const VASL_BOARDDATA_URL =          // métadonnées et motifs partagés
  'https://raw.githubusercontent.com/vasl-developers/vasl/develop/dist/boardData/';
const VASL_DIST_URL =               // CounterMetadata.xml (pions et ligne de vue)
  'https://raw.githubusercontent.com/vasl-developers/vasl/develop/dist/';
const BOARD_W = 1800, BOARD_H = 645;  // board géomorphique standard
const HEX_COLS = 33, HEX_ROWS = 10;

let zoom = 1, _mapW = 0, _mapH = 0;
// Rotation de l'affichage de la carte, en degrés (0, 90, 180, 270), dans le
// sens horaire. Elle ne touche que la vue de ce joueur : les positions
// des pions, l'état de la partie et ce que voit l'adversaire n'en dépendent pas.
let _rotVue = [90, 180, 270].includes(+_lirePref('aslweb.rotation')) ? +_lirePref('aslweb.rotation') : 0;

// ── Journal
// Lignes de la page elle-même : « sys », et « alerte » pour un
// avertissement, une erreur ou un refus, que les filtres ne cachent jamais.
function chatLog(msg){
  const d = document.createElement('div');
  d.className = /^(⚠️|❌|🔒)/u.test(msg) ? 'sys alerte' : 'sys';
  d.textContent = msg;
  ajouterLigne(d);
}

// Une ligne au journal. Il ne redescend que si l'on était déjà en bas (à
// 40 px près) ; sinon, la lecture n'est pas interrompue et une pastille
// « ↓ n nouveaux » y ramène.
let _nouveauxChat = 0;
const _apresLigne = [];           // fonctions appelées pour chaque ligne ajoutée
function ajouterLigne(el){
  const j = document.getElementById('journal');
  const enBas = j.scrollHeight - j.scrollTop - j.clientHeight < 40;
  j.appendChild(el);
  if(enBas) j.scrollTop = j.scrollHeight;
  else if(el.offsetHeight){
    _nouveauxChat++;
    _majNouveauxChat();
  }
  for(const f of _apresLigne) f(el);
}
function _majNouveauxChat(){
  let b = document.getElementById('nouveauxChat');
  if(!_nouveauxChat){ if(b) b.hidden = true; return; }
  if(!b){
    b = document.createElement('button');
    b.type = 'button'; b.id = 'nouveauxChat'; b.className = 'nouveauxChat';
    b.addEventListener('pointerdown', e => e.preventDefault());
    b.addEventListener('click', () => { const j = document.getElementById('journal'); j.scrollTop = j.scrollHeight; _nouveauxChat = 0; _majNouveauxChat(); });
    document.getElementById('bas').appendChild(b);
  }
  b.hidden = false;
  b.textContent = `↓ ${_nouveauxChat} nouveau${_nouveauxChat > 1 ? 'x' : ''}`;
  const j = document.getElementById('journal');
  b.style.bottom = (document.getElementById('bas').getBoundingClientRect().bottom - j.getBoundingClientRect().bottom + 8) + 'px';
}

