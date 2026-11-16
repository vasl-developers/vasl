// ═══ FLARE — Flare de VASSAL (VASSAL.build.module.map.Flare) ═══
// Un cercle rouge qui se resserre sur un point de la carte, chez soi et
// chez l'adversaire, pour lui montrer un hexagone ou des unités. Réglages
// du Flare de la carte de jeu dans le buildFile de VASL :
//   circleColor="255,0,0" circleScale="true" circleSize="100"
//   flareKey="keyAlt" flarePulses="6" flarePulsesPerSec="3" reportFormat=""
// Commande (Flare.encode) : « FLARE\t<id>\t<x>\t<y> », point en pixels de
// la carte ; <id> vient de UniqueIdManager("Flare") : « Flare0 » pour le
// premier Flare du module, celui de la carte de jeu (Main Map). Le coup
// passe par sendAndLog : il part chez les autres joueurs et entre dans le
// log ; VASL le rejoue en le relisant. reportFormat vide : rien au chat.
// Gestes : Alt+clic sur la carte (PC, comme VASL), double appui dans le
// vide de la carte (doigt), bouton « Flare » de l'éventail de la pile.
const FLARE_ID = 'Flare0';
const FLARE_DIAMETRE = 100;          // circleSize, en pixels de la carte (circleScale : suit le zoom)
const FLARE_PULSATIONS = 6;          // flarePulses
const FLARE_PAR_SECONDE = 3;         // flarePulsesPerSec
const FLARE_TRAIT = 3;               // Flare.STROKE, en pixels de l'écran

// Flare lancé ici, au point (x, y) de la carte.
function lancerFlare(x, y){
  if(!_source || !isFinite(x) || !isFinite(y)) return false;
  x = Math.round(x); y = Math.round(y);
  _logCmd(`FLARE\t${FLARE_ID}\t${x}\t${y}`);
  montrerFlare(x, y, true);
  vibrer();
  return true;
}

// Flare sur une pile : son point, celui de son pion du fond.
function flarePile(k){
  const l = pile(k).filter(p => !p.cache);
  return l.length ? lancerFlare(l[0].x, l[0].y) : false;
}

// Flare.decode : seul celui de la carte de jeu est montré ; celui d'une
// autre fenêtre (Casualties Bin…) n'a rien à montrer ici.
function flareDistant(cmd){
  const t = cmd.split('\t');
  if(t[0] !== 'FLARE' || t[1] !== FLARE_ID) return false;
  const x = parseInt(t[2], 10) || 0, y = parseInt(t[3], 10) || 0;
  // Saut dans un log (◀◀, ▶▶, curseur) : rien à animer.
  if(!_rendreDiffere) montrerFlare(x, y, false);
  return true;
}

// Flare.startAnimation et draw : un cercle de FLARE_DIAMETRE au zoom 1,
// qui se resserre jusqu'au point, FLARE_PULSATIONS fois. Un seul à la
// fois, comme le Flare de VASSAL (un nouveau relance l'animation). Celui
// d'un autre joueur, hors de la vue, la fait venir à lui.
function montrerFlare(x, y, local){
  const mi = document.getElementById('mapInner');
  if(!mi) return;
  document.querySelectorAll('.flare').forEach(e => e.remove());
  if(!local) _vueSurPoint(x, y);
  const f = document.createElement('div');
  f.className = 'flare';
  f.style.left = (x * SC) + 'px';
  f.style.top = (y * SC) + 'px';
  f.style.setProperty('--d', (FLARE_DIAMETRE * SC) + 'px');
  // Trait de largeur fixe à l'écran : #mapInner est mis à l'échelle du zoom.
  f.style.setProperty('--trait', (FLARE_TRAIT / Math.max(zoom, .05)) + 'px');
  f.style.setProperty('--pulsation', Math.round(1000 / FLARE_PAR_SECONDE) + 'ms');
  f.style.setProperty('--pulsations', String(FLARE_PULSATIONS));
  mi.appendChild(f);
  setTimeout(() => f.remove(), Math.round(1000 / FLARE_PAR_SECONDE) * FLARE_PULSATIONS + 50);
}

// Centre la vue sur un point de la carte qui n'y est pas, sans changer le
// zoom (Map.centerAt).
function _vueSurPoint(x, y){
  const wrap = document.getElementById('mapWrap');
  if(!wrap || !wrap.clientWidth) return;
  const v = _carteVersVue(x * SC, y * SC);
  const hv = hauteurVisibleCarte();
  const ex = v.x * zoom - wrap.scrollLeft, ey = v.y * zoom - wrap.scrollTop;
  const marge = FLARE_DIAMETRE * SC * zoom / 2;
  if(ex >= marge && ex <= wrap.clientWidth - marge && ey >= marge && ey <= hv - marge) return;
  wrap.scrollLeft = v.x * zoom - wrap.clientWidth / 2;
  wrap.scrollTop = v.y * zoom - hv / 2;
}

// Alt+clic (bouton principal, sans Maj ni Ctrl) : flareKey="keyAlt", comme
// Flare.mousePressed. Pris avant le pion ou la carte, qui ne le voient pas.
function _brancherFlare(){
  const wrap = document.getElementById('mapWrap');
  if(!wrap) return;
  wrap.addEventListener('pointerdown', e => {
    if(!e.altKey || e.shiftKey || e.ctrlKey || e.metaKey || e.button !== 0) return;
    if(e.target.closest && e.target.closest('.eventail, .barrePile, .bandeauMode, .panneauFil, .tablesQRDC')) return;
    const c = clientVersCarte(e.clientX, e.clientY, null, true);
    if(!c) return;
    e.preventDefault(); e.stopPropagation();
    lancerFlare(c.x, c.y);
  }, true);
}
