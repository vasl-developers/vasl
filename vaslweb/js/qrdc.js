// ═══════════════════════════════════════════════════════════════
// TABLES QRDC — la fenêtre « QRDC » de VASL (ChartWindow)
// Ses tables (IFT, CC, TH, TK, Concealment), avec les onglets du module.
// Au PC, elles s'ouvrent dans une fenêtre flottante, comme dans VASL.
// En tactile, elles sont posées sur la carte, sous sa zone, c'est-à-dire
// sous l'emprise des boards affichés, à trois petits pions d'écart. C'est
// une couche de l'affichage seulement : rien n'en va dans le .vsav ni au
// log, et aucun pion ne peut s'y poser. Les pièces déjà posées sous les
// boards (carte de scénario, unités hors carte) gardent leur place : les
// tables passent sous elles.
// Les pioches OBA y sont un onglet de plus, après ceux du module.
// ═══════════════════════════════════════════════════════════════
const ECART_TABLES = 3 * 48;          // trois petits pions, pixels natifs
let _empriseBoards = null;            // {x, y, w, h} en pixels de #mapInner
let _mapBase = null;                  // taille de la carte sans les tables
let _tablesEl = null;
let _tablesVisibles = _lirePref('aslweb.tables') !== '0';   // en tactile
const _tablesSel = (() => {
  try { return new Map(JSON.parse(localStorage.getItem('qrdcSel') || '[]')); } catch(e){ return new Map(); }
})();

function _oublierTables(){
  if(_tablesEl) _tablesEl.remove();
  _tablesEl = null;
}

async function poserTables(){
  _oublierTables();
  if(!_empriseBoards || !_tablesVisibles || !_telLike()) return;
  let bdd;
  try { bdd = await chargerBDD(); } catch(e){ return; }
  if(!bdd.qrdc || _tablesEl || !_empriseBoards || !_tablesVisibles || !_telLike()) return;
  const el = document.createElement('div');
  el.id = 'tablesQRDC';
  el.className = 'tablesQRDC';
  el.setAttribute('role', 'region');
  el.setAttribute('aria-label', 'Tables QRDC');
  // Sous l'emprise des boards, et sous les pièces déjà posées plus bas.
  let bas = _empriseBoards.y + _empriseBoards.h;
  for(const p of vivants()){
    if(p.cache || p.y * SC < bas) continue;
    bas = Math.max(bas, Math.ceil(p.y * SC + (p.haut || p.sz || 48 * SC) / 2 + (p.decY || 0) * SC));
  }
  el._place = {left: _empriseBoards.x + 'px', top: Math.round(bas + ECART_TABLES * SC) + 'px'};
  _tablesEl = el;
  _dessinerTables();
  _placerTables();
  // Les images arrivent après coup : la carte s'agrandit à leur mesure.
  if(window.ResizeObserver) new ResizeObserver(() => {
    if(_tablesEl === el) _etendreCarte(true);
  }).observe(el);
  _etendreCarte();
}

// Carte non tournée : les tables sont dans #mapInner, sous les boards.
// Vue tournée : elles en sortent pour rester droites, et _majScaler les
// pose sous la carte affichée, au zoom de la carte.
function _placerTables(){
  const el = _tablesEl;
  if(!el) return;
  if(_rotVue) document.getElementById('mapScaler').appendChild(el);
  else {
    Object.assign(el.style, el._place, {transform: '', transformOrigin: ''});
    document.getElementById('mapInner').appendChild(el);
  }
}

// Sur la carte, à l'échelle des boards ; dans la fenêtre, à la taille de
// VASL (un pixel de l'image pour un pixel de l'écran).
function _dessinerTables(){
  if(!_bdd || !_bdd.qrdc) return;
  if(_tablesEl){ _tablesEl.replaceChildren(); rendreTable(_bdd.qrdc, _tablesEl, SC); }
  const f = document.querySelector('#fenQRDC .qCorps');
  if(f){ f.replaceChildren(); rendreTable(_bdd.qrdc, f, 1); }
}

// Comme les widgets de la palette : seule la branche choisie est dessinée,
// et le choix de chaque onglet est retenu d'une visite à l'autre.
function rendreTable(n, boite, k){
  if(n.t === 'Casualties'){
    const z = document.createElement('div');
    z.className = 'bin ongletBin';
    _brancherOBA(z);
    _rendreBin(z);
    boite.appendChild(z);
    return;
  }
  if(n.t === 'OBA'){
    const z = document.createElement('div');
    z.className = 'oba ongletOBA';
    _brancherOBA(z);
    _rendreOBA(z);
    boite.appendChild(z);
    return;
  }
  if(n.t === 'Chart'){
    const im = document.createElement('img');
    im.alt = im.title = n.nom;
    im.draggable = false;
    im.addEventListener('load', () => { im.style.width = Math.round(im.naturalWidth * k) + 'px'; });
    chargerImage(n.fichier).then(u => { if(u) im.src = u; });
    boite.appendChild(im);
    return;
  }
  if(n.t === 'TabWidget' && n.enfants.length){
    const sel = Math.min(_tablesSel.get(n.id) || 0, n.enfants.length - 1);
    const o = document.createElement('div');
    o.className = 'qOnglets';
    o.setAttribute('role', 'tablist');
    n.enfants.forEach((w, i) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = i === sel ? 'actif' : '';
      b.setAttribute('role', 'tab');
      b.setAttribute('aria-selected', i === sel);
      b.textContent = w.nom || '—';
      // Le bouton garde son appui : la carte ne le capture pas pour défiler.
      b.addEventListener('pointerdown', e => e.stopPropagation());
      b.addEventListener('click', () => {
        _tablesSel.set(n.id, i);
        try { localStorage.setItem('qrdcSel', JSON.stringify([..._tablesSel])); } catch(e){}
        _dessinerTables();
        rafraichirActions(['oba', 'bin']);
      });
      o.appendChild(b);
    });
    const z = document.createElement('div');
    z.className = 'qContenu';
    rendreTable(n.enfants[sel], z, k);
    boite.append(o, z);
    return;
  }
  // PanelWidget, ChartWindow : les enfants dans l'ordre du module.
  const z = document.createElement('div');
  z.className = 'qPanneau' + (n.vert ? ' vert' : '');
  for(const e of n.enfants) rendreTable(e, z, k);
  boite.appendChild(z);
}

// Rectangle des tables, en pixels de #mapInner, ou null sans tables (ou
// hors de la carte, vue tournée).
function rectTables(){
  const e = _tablesEl;
  if(!e || !e.isConnected || _rotVue) return null;
  return {x: e.offsetLeft, y: e.offsetTop, w: e.offsetWidth, h: e.offsetHeight};
}

// Point VASL natif sur les tables : aucun pion ne s'y pose.
function surTables(x, y){
  const r = rectTables();
  return !!r && _rectContient({x: r.x, y: r.y, width: r.w, height: r.h}, x * SC, y * SC);
}

// Fenêtre flottante du PC : on la déplace par son titre, on la
// redimensionne par son coin ; elle ne bloque pas la carte.
// Fenêtre flottante (QRDC, OBA) : titre à déplacer, croix pour fermer.
function _fenetreFlottante(id, classe, titreTxt, corps, fermer){
  const w = document.createElement('div');
  w.id = id;
  w.className = classe;
  w.setAttribute('role', 'dialog');
  w.setAttribute('aria-label', titreTxt);
  w.innerHTML = `<div class="qTitre"><span>${_esc(titreTxt)}</span><button type="button" class="qFermer" title="Fermer" aria-label="Fermer">✕</button></div>`
    + `<div class="qCorps">${corps}</div>`;
  document.body.appendChild(w);
  w.querySelector('.qFermer').addEventListener('click', fermer);
  const titre = w.querySelector('.qTitre');
  titre.addEventListener('pointerdown', e => {
    if(e.button > 0 || e.target.closest('button')) return;
    e.preventDefault();
    const r = w.getBoundingClientRect(), dx = e.clientX - r.left, dy = e.clientY - r.top;
    const bouge = ev => {
      w.style.left = Math.max(0, Math.min(innerWidth - 60, ev.clientX - dx)) + 'px';
      w.style.top = Math.max(0, Math.min(innerHeight - 30, ev.clientY - dy)) + 'px';
    };
    const fin = () => { removeEventListener('pointermove', bouge); removeEventListener('pointerup', fin); };
    addEventListener('pointermove', bouge);
    addEventListener('pointerup', fin);
  });
  return w;
}

async function ouvrirFenetreTables(){
  if(document.getElementById('fenQRDC')) return;
  const w = _fenetreFlottante('fenQRDC', 'fenQRDC', 'QRDC',
    '<div style="padding:8px;color:var(--doux)">Chargement de la base VASL…</div>', fermerFenetreTables);
  rafraichirActions(['tables']);
  try { await chargerBDD(); } catch(e){}
  if(!_bdd || !_bdd.qrdc){
    const c = w.querySelector('.qCorps');
    if(c) c.innerHTML = '<div style="padding:8px">Tables QRDC indisponibles.</div>';
    return;
  }
  _dessinerTables();
}
function fermerFenetreTables(){
  const w = document.getElementById('fenQRDC');
  if(w){ w.remove(); rafraichirActions(['tables', 'oba', 'bin']); }
}

// Changement de profil : la fenêtre au PC, la couche de la carte en tactile.
_apresProfil.push(() => {
  if(_telLike()){ fermerFenetreTables(); if(!_tablesEl) poserTables(); }
  else if(_tablesEl){ _oublierTables(); _etendreCarte(true); }
});


function basculerTables(){
  if(!_telLike()){
    if(document.getElementById('fenQRDC')) fermerFenetreTables(); else ouvrirFenetreTables();
    return;
  }
  _tablesVisibles = !_tablesVisibles;
  try { localStorage.setItem('aslweb.tables', _tablesVisibles ? '1' : '0'); } catch(e){}
  if(_tablesVisibles) poserTables();
  else {
    _oublierTables();
    _etendreCarte(true);
  }
  rafraichirActions(['tables', 'oba', 'bin']);
}

