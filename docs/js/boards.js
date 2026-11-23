// ═══════════════════════════════════════════════════════════════
// « PICK NEW BOARDS FOR THIS SCENARIO » — BoardSwapper de VASL
// (VASL/build/module/map/BoardSwapper.java, entrée du menu de la carte).
// Le dialogue est le BoardPicker de VASSAL tel que l'étendent
// ASLBoardPicker et ASLBoardSlot : grille de cases pré-remplie par la
// disposition actuelle, Add row / Add column / Clear, et dans chaque case
// une recherche, la liste des boards, « flip » et l'aperçu à l'échelle 0,2.
// Ok recompose la carte (SetBoards exécutée localement : ni log, ni
// réseau) puis replace chaque pièce dans son hexagone : même board, même
// point de l'image (recordPiecePositions / restorePiecePositions).
// Crop boards : le Cropper d'ASLBoardPicker et ASLBoard.crop. Add overlays :
// l'Overlayer, ASLBoardSlot.addOverlay / checkOverlap et Overlay. Terrain
// SSR : le TerrainEditor (« Terrain Transformations »).
// ═══════════════════════════════════════════════════════════════
const VASL_BOARD_VERSIONS_URL =         // boardVersionURL du buildFile
  'https://raw.githubusercontent.com/vasl-developers/vasl/develop/boards/v5boardVersions.xml';
const BP_ECHELLE = 0.2;                 // slotScale
const BP_CASE_VIDE = {w: 350, h: 125};  // slotWidth × slotHeight
const BP_SELECT = 'Select board';       // BoardPicker.select_board
const BP_ORDRE = ['New', 'Updated', 'Geoboard', 'Double-sided', 'Starter Kit', 'HASL', 'Deluxe'];

// ── Liste des boards (ASLBoardPicker.getallboards puis le tri de
// refreshPossibleBoards), téléchargée une fois.
let _listeBoards = null;
function listeBoards(){
  if(!_listeBoards){
    _listeBoards = fetch(VASL_BOARD_VERSIONS_URL, {cache: 'no-cache'}).then(r => {
      if(!r.ok) throw new Error('v5boardVersions.xml introuvable (HTTP ' + r.status + ')');
      return r.text();
    }).then(t => _trierBoards(_lireVersionsBoards(t)));
    _listeBoards.catch(() => { _listeBoards = null; });
  }
  return _listeBoards;
}

// Jour (UTC) d'une date « yyyy/MM/dd », ou une erreur si elle est invalide.
function _jourBoard(s){
  const m = /^(\d{4})\/(\d{2})\/(\d{2})$/.exec(s || '');
  if(!m) throw new Error('date ' + s);
  const t = Date.UTC(+m[1], +m[2] - 1, +m[3]), d = new Date(t);
  if(d.getUTCMonth() !== +m[2] - 1 || d.getUTCDate() !== +m[3]) throw new Error('date ' + s);
  return t / 864e5;
}

// parseBoardVersionFile : « nom  type » (deux espaces) ; un board mis à
// jour depuis 90 jours au plus, ou paru depuis 90 jours au plus, figure une
// seconde fois avec son statut « Updated » ou « New » (New l'emporte).
function _lireVersionsBoards(texte){
  const doc = new DOMParser().parseFromString(texte, 'application/xml');
  const racine = doc.documentElement, out = [];
  if(!racine || racine.nodeName !== 'boardsMetadata') return out;
  const d = new Date(), aujourdhui = Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 864e5;
  for(const section of racine.children) for(const e of section.children){
    const nom = e.getAttribute('name');
    if(!nom) continue;
    const type = e.getAttribute('boardType') || '';
    out.push(nom + '  ' + type);
    let statut = '';
    try {
      if(aujourdhui - _jourBoard(e.getAttribute('versionDate')) <= 90) statut = 'Updated';
      const paru = e.getAttribute('issued') || '';
      if(paru && aujourdhui - _jourBoard(paru) <= 90) statut = 'New';
    } catch(err){ /* date illisible : statut inchangé */ }
    if(statut) out.push(nom + '  ' + type + '  ' + statut);
  }
  return out;
}

// Le comparateur de refreshPossibleBoards, bogue compris : deux libellés du
// même rang prioritaire sont égaux (o1Parts[0] comparé à lui-même) et
// gardent, le tri étant stable, l'ordre du fichier.
function _trierBoards(liste){
  const parts = s => { const i = s.indexOf(' '); return i < 0 ? [s, ''] : [s.substring(0, i), s.substring(i + 1).trim()]; };
  const rang = s => BP_ORDRE.findIndex(o => s.includes(o));
  const cmp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
  return liste.slice().sort((o1, o2) => {
    const [a1, b1] = parts(o1), [a2, b2] = parts(o2);
    const i1 = rang(b1), i2 = rang(b2);
    if(i1 !== -1 && i2 !== -1) return i1 === i2 ? 0 : i1 - i2;
    if(i1 !== -1) return -1;
    if(i2 !== -1) return 1;
    return cmp(b1, b2) || cmp(a1, a2);
  });
}

// ASLBoardPicker.getBoard : le texte avant le premier espace ; « 2 » →
// « 02 », « A » → « dxa ».
function nomDuLibelle(lib){
  let n = lib.split(' ')[0];
  if(/^[0-9]$/.test(n)) n = '0' + n;
  else if(/^[A-H]$/.test(n)) n = 'dx' + n.toLowerCase();
  return n;
}

// ── État écrit d'un board (ASLBoard.getState). Un board de la carte
// garde le sien mot pour mot, place et retournement mis à jour, et son
// recadrage s'il a été refait ; un board neuf n'a que sa place, son nom,
// son recadrage et sa version.
//   x \t y \t [r]NOM \t [cropX \t cropY \t cropW \t cropH \t] VER \t v \t … [\t FH \t true]
function _champsCrop(b){
  const c = b.crop;
  return c && (c.w > 0 || c.h > 0) ? `${c.x}\t${c.y}\t${c.w}\t${c.h}\t` : '';
}
const _champFH = b => b.fh ? '\tFH\ttrue' : '';
function etatBoard(b){
  const r = b.reversed ? 'r' : '';
  if(b.etat){
    const t = b.etat.split('\t');
    t[0] = String(b.col); t[1] = String(b.row); t[2] = r + b.nomEcrit;
    let etat = t.join('\t');
    if(b.cropModifie){
      // Recadré dans le dialogue : les quatre champs du recadrage et le FH
      // final sont réécrits, le reste (VER, OVR, SSR, ZOOM) ne bouge pas.
      let reste = t.slice(3);
      if(reste.length >= 4 && reste.slice(0, 4).every(v => /^-?\d+$/.test(v))) reste = reste.slice(4);
      const suite = reste.join('\t').replace(/\tFH\t(true|false)/g, '');
      etat = t.slice(0, 3).join('\t') + '\t' + _champsCrop(b) + suite + _champFH(b);
    }
    if(b.ovrModifie) etat = _remplacerOVR(etat, b.overlays);
    return b.ssrModifie ? _remplacerSSR(etat, b.terrain) : etat;
  }
  const g = geoBoard(b);
  return `${b.col}\t${b.row}\t${r}${g.nomImage || b.name}\t${_champsCrop(b)}VER\t${g.version || '0.0'}\t`
    + (b.overlays || []).map(o => `OVR\t${o.nom}\t${o.hex1}\t${o.hex2}\t`).join('')
    + (b.terrain ? 'SSR\t' + b.terrain : '') + _champFH(b);
}

// Règles SSR écrites (terrainChanges) : ce qui suit « SSR », jusqu'au ZOOM
// ou au FH final.
function _terrainEcrit(etat){
  const i = (etat || '').indexOf('\tSSR\t');
  if(i < 0) return '';
  return etat.substring(i + 5).replace(/(\tZOOM\t[^\t]*)?(\tFH\t(true|false))?$/, '');
}
// Règles SSR changées dans le dialogue : « SSR \t règles » remplacé (ou
// retiré), ZOOM et FH gardés en fin d'état, comme dans getState.
function _remplacerSSR(etat, terrain){
  const fin = /(\tZOOM\t[^\t]*)?(\tFH\t(true|false))?$/.exec(etat)[0];
  let tete = etat.substring(0, etat.length - fin.length);
  const i = tete.indexOf('\tSSR\t');
  if(i >= 0) tete = tete.substring(0, i + 1);
  else if(!tete.endsWith('\t')) tete += '\t';
  return tete + (terrain ? 'SSR\t' + terrain : '') + fin;
}
// SSRFilter : les règles sont les mots des règles écrites.
const _reglesDuTerrain = t => [...new Set((t || '').split(/\s+/).filter(x => x && x !== 'true' && x !== 'false' && x !== 'FH'))];

// Overlays changés dans le dialogue : chaque « OVR nom hex1 hex2 » est
// retiré, puis la liste entière est écrite à sa place dans getState —
// après « VER v » et les champs vides des images SSR (SSROverlay, placées
// en tête de liste, s'écrivent ""), avant « SSR ».
function _remplacerOVR(etat, overlays){
  const t = etat.split('\t'), out = [];
  for(let i = 0; i < t.length; i++){
    if(i >= 3 && t[i] === 'OVR'){ i += 3; continue; }
    out.push(t[i]);
  }
  let k = out.indexOf('VER', 3);
  k = k < 0 ? Math.min(out.length, /^-?\d+$/.test(out[3] || '') ? 7 : 3) : k + 2;
  while(k < out.length - 1 && out[k] === '') k++;
  out.splice(k, 0, ...overlays.flatMap(o => ['OVR', o.nom, o.hex1, o.hex2]));
  return out.join('\t');
}
// ASLBoardPicker.encode
const segmentBoards = boards => 'bd\t' + boards.map(etatBoard).join('bd\t');

// Nom écrit d'un board lu dans la sauvegarde, sans le « r » du retournement.
function _nomEcrit(b){
  const t = (b.etat || '').split('\t')[2];
  if(!t) return b.name;
  return b.reversed ? t.substring(1) : t;
}

// Copie d'un board pour le dialogue : Cancel ne touche à rien.
function _copieBoard(b){
  return {name: b.name, reversed: !!b.reversed, crop: b.crop ? Object.assign({}, b.crop) : null,
          zoom: b.zoom || 1, ssrList: (b.ssrList || []).slice(),
          overlays: (b.overlays || []).map(o => Object.assign({}, o)),
          etat: b.etat || null, nomEcrit: b.etat ? (b.nomEcrit || _nomEcrit(b)) : null,
          fh: !!b.fh, cropModifie: !!b.cropModifie, ovrModifie: !!b.ovrModifie, ssrModifie: !!b.ssrModifie,
          terrain: b.terrain !== undefined ? b.terrain : _terrainEcrit(b.etat),
          // Image composée de la carte (recadrée), et le recadrage qu'elle suit.
          _comp: b._comp || null, _compCrop: b._comp ? (b.hasOwnProperty('_compCrop') ? b._compCrop : b.crop) : null,
          _compPlein: b._compPlein || null};
}
const _boardNeuf = nom => ({name: nom, reversed: false, crop: null, zoom: 1, ssrList: [], overlays: [],
                            etat: null, nomEcrit: null, fh: false, cropModifie: false, ovrModifie: false,
                            ssrModifie: false, terrain: '',
                            _comp: null, _compCrop: null, _compPlein: null});
const _memeCrop = (a, b) => (!a && !b) || (!!a && !!b && a.x === b.x && a.y === b.y && a.w === b.w && a.h === b.h);

// ═══ LE DIALOGUE ═══
let _bp = null;            // dialogue ouvert
let _bpEnCours = false;    // carte en cours de recomposition

// nouvelle : partie neuve (nouvellePartie) — grille vide, et l'état
// complet renvoyé à la salle après Ok, la carte n'étant faite qu'alors.
function ouvrirChoixBoards({nouvelle = false} = {}){
  if(_bp || _bpEnCours) return;
  if(!_source || !_losBoards){ chatLog('⚠️ Aucune partie ouverte'); return; }
  fermerMenu();
  // recordPiecePositions : avant l'ouverture du dialogue.
  const releve = releverPions(_losBoards);
  const voile = document.createElement('div');
  voile.className = 'voile';
  const w = document.createElement('div');
  w.className = 'fenetre fenBoards';
  w.setAttribute('role', 'dialog');
  w.setAttribute('aria-modal', 'true');
  w.setAttribute('aria-label', 'Pick new boards for this scenario');
  w.innerHTML = `<div class="ft">Pick new boards for this scenario</div><div class="fc">
      <div class="bpEtat" aria-live="polite"></div>
      <div class="bpCorps">
        <div class="bpOutils">
          <button type="button" data-bp="ligne">Add row</button>
          <button type="button" data-bp="colonne">Add column</button>
          <button type="button" data-bp="vider">Clear</button>
          <button type="button" data-bp="pdf" title="Pré-remplir la grille et l’OB d’après le PDF de la fiche du scénario">Depuis un PDF…</button>
          <button type="button" data-bp="overlays">Add overlays</button>
          <button type="button" data-bp="rogner">Crop boards</button>
          <button type="button" data-bp="terrain">Terrain SSR</button>
        </div>
        <div class="bpDefile"><div class="bpGrille"></div></div>
        <div class="bpPdf" hidden></div>
      </div>
      <div class="fb"><button type="button" class="prim" data-bp="ok">Ok</button><button type="button" data-bp="annuler">Cancel</button></div>
    </div>`;
  document.body.append(voile, w);
  _bp = {w, voile, releve, nouvelle, nx: 1, ny: 1, cases: [], liste: null, etatMin: null};
  // Les touches ne vont ni aux pions sélectionnés ni aux raccourcis.
  w.addEventListener('keydown', e => {
    e.stopPropagation();
    if(e.key === 'Escape'){ e.preventDefault(); fermerChoixBoards(); }
  });
  w.addEventListener('pointerdown', e => e.stopPropagation());
  voile.addEventListener('pointerdown', e => { e.preventDefault(); e.stopPropagation(); });
  _deplacableParTitre(w);
  const q = s => w.querySelector(`[data-bp="${s}"]`);
  q('ligne').onclick = () => { _bpAjouterLigne(); _bpRendre(); };
  q('colonne').onclick = () => { _bpAjouterColonne(); _bpRendre(); };
  q('vider').onclick = () => { _bpReset(); _bpRendre(); };
  q('rogner').onclick = () => ouvrirRognage();
  q('overlays').onclick = () => ouvrirOverlays();
  // Le choix du fichier se fait dans le geste du clic ; la lecture (modules
  // chargés à la demande) vient ensuite.
  q('pdf').onclick = () => {
    const i = document.createElement('input');
    i.type = 'file'; i.accept = 'application/pdf,.pdf';
    i.onchange = () => {
      const f = i.files && i.files[0];
      if(!f) return;
      _bpEtat('Chargement de la lecture des PDF…');
      chargerModulesOb().then(() => obDepuisPdf(f)).catch(e => _bpEtat('⚠️ ' + e.message));
    };
    i.click();
  };
  q('terrain').onclick = () => ouvrirTerrain();
  q('annuler').onclick = () => fermerChoixBoards();
  q('ok').onclick = () => validerChoixBoards();
  // getControls : reset(), puis setBoards(map.getBoards()) ; pour une
  // nouvelle partie, reset() seul.
  _bpPoserBoards(nouvelle ? [] : _losBoards.map(b => Object.assign(_copieBoard(b), {col: b.col, row: b.row})));
  _bpRendre();
  // Les boards ajoutés d'un autre emplacement (roue ⚙, carte.js) suivent
  // ceux du dépôt VASL.
  listeBoards().then(async l => {
    const perso = [...(await boardsEnCache())].filter(([nom, s]) => s && !l.some(x => nomDuLibelle(x) === nom))
      .map(([nom]) => nom + '  Perso').sort();
    return l.concat(perso);
  }).then(l => {
    if(_bp && _bp.w === w){ _bp.liste = l; for(const c of _bp.cases) _bpRemplirListe(c, c.rech.value); }
  }).catch(e => {
    if(_bp && _bp.w === w){ _bp.liste = []; _bpEtat('⚠️ ' + e.message); for(const c of _bp.cases) _bpRemplirListe(c, c.rech.value); }
  });
  setTimeout(() => { if(_profil === 'pc') q('ok').focus(); }, 0);
}

function fermerChoixBoards(){
  if(!_bp) return;
  if(typeof obFermetureBoards === 'function') obFermetureBoards(!!_bp.valide);
  fermerRognage();
  fermerOverlays();
  fermerTerrain();
  for(const c of _bp.cases) _bpOublierCase(c);
  clearInterval(_bp.etatMin);
  _bp.w.remove(); _bp.voile.remove();
  _bp = null;
}

// Fenêtre déplacée par sa barre de titre (PC ; au doigt, elle est plein écran).
function _deplacableParTitre(w){
  const t = w.querySelector('.ft');
  t.addEventListener('pointerdown', e => {
    if(e.button > 0 || _profil !== 'pc') return;
    e.preventDefault();
    const r = w.getBoundingClientRect(), dx = e.clientX - r.left, dy = e.clientY - r.top;
    t.setPointerCapture(e.pointerId);
    const bouge = ev => {
      w.style.transform = 'none';
      w.style.left = Math.max(0, Math.min(innerWidth - 80, ev.clientX - dx)) + 'px';
      w.style.top = Math.max(0, Math.min(innerHeight - 30, ev.clientY - dy)) + 'px';
    };
    const fin = () => { t.removeEventListener('pointermove', bouge); t.removeEventListener('pointerup', fin); t.removeEventListener('pointercancel', fin); };
    t.addEventListener('pointermove', bouge);
    t.addEventListener('pointerup', fin);
    t.addEventListener('pointercancel', fin);
  });
}

// Ligne d'état (BoardPicker.warn).
function _bpEtat(s){
  if(_bp) _bp.w.querySelector('.bpEtat').textContent = s;
}

// ── Grille : les cases dans l'ordre de GridLayout, case (i, j) à l'indice
// i + nx·j.
function _bpNouvelleCase(){
  const el = document.createElement('div');
  el.className = 'bpCase';
  el.innerHTML = `<div class="bpChoix">
      <input type="search" class="bpRech" title="Search for a board" aria-label="Search for a board" placeholder="Search" autocomplete="off" spellcheck="false">
      <select class="bpListe" aria-label="Board"></select>
      <label class="coche bpFlip" hidden><input type="checkbox"><span>flip</span></label>
    </div><canvas class="bpApercu"></canvas>`;
  const c = {el, board: null, minuteur: null, attente: null, gen: 0, terrain: '',
             rech: el.querySelector('.bpRech'), sel: el.querySelector('.bpListe'),
             flip: el.querySelector('.bpFlip'), cv: el.querySelector('.bpApercu')};
  c.rech.addEventListener('input', () => _bpFiltrer(c));
  c.sel.addEventListener('change', () => {
    if(c.sel.size > 1) c.sel.size = 1;
    _bpChoisir(c, c.sel.value);
  });
  c.flip.querySelector('input').addEventListener('change', e => {
    if(!c.board) return;
    c.board.reversed = e.target.checked;
    _bpApercu(c);
  });
  _bpRemplirListe(c);
  return c;
}
function _bpOublierCase(c){
  clearTimeout(c.minuteur);
  c.minuteur = c.attente = null;
  c.gen++;
}
function _bpReset(){
  if(_bp.cases) for(const c of _bp.cases) _bpOublierCase(c);
  _bp.nx = _bp.ny = 1;
  _bp.cases = [_bpNouvelleCase()];
  _bpEtat('');
}
function _bpAjouterLigne(){
  _bp.ny++;
  for(let i = 0; i < _bp.nx; i++) _bp.cases.push(_bpNouvelleCase());
}
function _bpAjouterColonne(){
  _bp.nx++;
  for(let j = 0; j < _bp.ny; j++) _bp.cases.splice((j + 1) * _bp.nx - 1, 0, _bpNouvelleCase());
}
// BoardPicker.setBoards
function _bpPoserBoards(boards){
  _bpReset();
  for(const b of boards){
    while(b.col > _bp.nx - 1) _bpAjouterColonne();
    while(b.row > _bp.ny - 1) _bpAjouterLigne();
  }
  for(const b of boards){
    const c = _bp.cases[b.col + _bp.nx * b.row];
    _bpPoser(c, b);
    _bpRemplirListe(c);
  }
}
function _bpRendre(){
  const g = _bp.w.querySelector('.bpGrille');
  g.style.gridTemplateColumns = `repeat(${_bp.nx}, max-content)`;
  g.replaceChildren(..._bp.cases.map(c => c.el));
  for(const c of _bp.cases) if(!c.board) _bpApercu(c);
}

// ── Liste d'une case : « Select board », puis tous les boards. Une case
// pré-remplie montre son board ; un board absent de la liste y est ajouté.
function _bpLibelles(){
  return [BP_SELECT, ...(_bp && _bp.liste || [])];
}
function _bpLibelleDe(c){
  if(!c.board) return BP_SELECT;
  const l = _bpLibelles().find(x => x !== BP_SELECT && nomDuLibelle(x) === c.board.name);
  return l || c.board.name;
}
function _bpRemplirListe(c, filtre){
  const tous = _bpLibelles();
  const actuel = _bpLibelleDe(c);
  if(!tous.includes(actuel)) tous.splice(1, 0, actuel);
  const f = (filtre || '').toLowerCase();
  const vus = f ? tous.filter(x => x.toLowerCase().includes(f)) : tous;
  const frag = document.createDocumentFragment();
  for(const x of vus){
    const o = document.createElement('option');
    o.value = x === BP_SELECT ? '' : x;
    o.textContent = x;
    frag.appendChild(o);
  }
  c.sel.replaceChildren(frag);
  if(f){
    // ASLBoardSlot.filterBoards : la liste filtrée s'ouvre. Rien n'y est
    // choisi d'avance : tout choix, même le premier, change de board.
    c.sel.selectedIndex = -1;
    c.sel.size = Math.max(2, Math.min(8, vus.length));
  } else {
    c.sel.size = 1;
    c.sel.value = actuel === BP_SELECT ? '' : actuel;
  }
}
function _bpFiltrer(c){
  _bpRemplirListe(c, c.rech.value);
}

// ── Choix d'un board : « Select board » vide la case aussitôt ; un board
// est chargé 500 ms après le dernier choix (Timer d'ASLBoardSlot).
function _bpChoisir(c, lib){
  clearTimeout(c.minuteur);
  c.minuteur = null;
  if(!lib){ c.attente = null; c.gen++; _bpPoser(c, null); return; }
  c.attente = lib;
  c.minuteur = setTimeout(() => { c.minuteur = null; _bpCharger(c); }, 500);
}
// Le board choisi est posé dans la case (un nouvel objet : le même board
// peut figurer deux fois), puis son aperçu se charge.
function _bpCharger(c){
  const lib = c.attente;
  c.attente = null;
  if(!lib) return;
  _bpPoser(c, _boardNeuf(nomDuLibelle(lib)));
}
function _bpPoser(c, b){
  c.board = b;
  // ASLBoardSlot.setBoard : la case reprend les règles SSR du board s'il
  // en a ; sinon elle garde les siennes (une case vide n'en a pas).
  if(b && b.terrain) c.terrain = b.terrain;
  c.flip.hidden = !b;
  c.flip.querySelector('input').checked = !!(b && b.reversed);
  _bpApercu(c);
}

// ── Aperçu à l'échelle slotScale : l'image composée du board de la carte
// (recadrage, SSR, overlays), à défaut l'image du board ; retourné si
// « flip ». Une case vide prend la taille de la première case.
async function _bpApercu(c){
  const gen = ++c.gen, b = c.board, cv = c.cv;
  const k = window.devicePixelRatio || 1;
  const taille = (w, h) => {
    cv.width = Math.max(1, Math.round(w * k)); cv.height = Math.max(1, Math.round(h * k));
    cv.style.width = w + 'px'; cv.style.height = h + 'px';
  };
  if(!b){
    const c0 = _bp && _bp.cases[0];
    const t = c0 && c0 !== c && c0.board && c0.cv.style.width
      ? {w: parseInt(c0.cv.style.width, 10), h: parseInt(c0.cv.style.height, 10)} : BP_CASE_VIDE;
    taille(t.w, t.h);
    cv.classList.add('vide');
    return;
  }
  cv.classList.remove('vide');
  const nom = b.name;
  let alterne = false;
  _bpEtat('loading ' + nom);
  clearInterval(_bp.etatMin);
  _bp.etatMin = setInterval(() => { alterne = !alterne; _bpEtat((alterne ? 'loading ... ' : 'loading ') + nom); }, 1000);
  const fini = s => { if(gen !== c.gen || !_bp) return false; clearInterval(_bp.etatMin); _bpEtat(s); return true; };
  try {
    const g = await chargerGeoBoard(nom);
    let src = null, sx = 0, sy = 0;
    const sw = (b.crop && b.crop.w > 0) ? b.crop.w : g.w, sh = (b.crop && b.crop.h > 0) ? b.crop.h : g.h;
    // L'image composée de la carte vaut tant que le recadrage n'a pas
    // changé ; sinon, l'image entière, découpée ici : composée elle aussi
    // (une fois) si le board a des SSR ou des overlays.
    if(b._comp && _memeCrop(b._compCrop, b.crop)) src = b._comp;
    else {
      if(b._compPlein) src = b._compPlein;
      else {
        const im = new Image();
        await new Promise((res, rej) => { im.onload = res; im.onerror = () => rej(new Error('bd' + nom + '.gif illisible')); _fetchBoardImage(nom).then(u => im.src = u, rej); });
        src = im;
        if((b.ssrList || []).length || (b.overlays || []).length){
          src = b._compPlein = await composerBoard(Object.assign({}, b, {crop: null, _comp: null, _compPlein: null}), im);
        }
      }
      if(b.crop){ sx = b.crop.x || 0; sy = b.crop.y || 0; }
    }
    if(gen !== c.gen) return;
    const z = b.zoom || 1;
    const w = Math.trunc(BP_ECHELLE * Math.round(sw * z)), h = Math.trunc(BP_ECHELLE * Math.round(sh * z));
    taille(w, h);
    const ctx = cv.getContext('2d');
    ctx.imageSmoothingQuality = 'high';
    if(b.reversed){ ctx.translate(cv.width, cv.height); ctx.scale(-1, -1); }
    ctx.drawImage(src, sx, sy, sw, sh, 0, 0, cv.width, cv.height);
    fini(nom + ' loaded');
    // Les cases vides suivent la taille de la première.
    if(_bp && c === _bp.cases[0]) for(const x of _bp.cases) if(!x.board) _bpApercu(x);
  } catch(e){
    if(fini('Unable to find board ' + nom + ' — ' + e.message)){
      taille(BP_CASE_VIDE.w, BP_CASE_VIDE.h);
      cv.classList.add('vide');
    }
  }
}

// ── Ok : ASLBoardPicker.finish, puis SetBoards et restorePiecePositions.
async function validerChoixBoards(){
  if(!_bp) return;
  // Un choix encore en attente de son délai compte.
  for(const c of _bp.cases) if(c.minuteur){ clearTimeout(c.minuteur); c.minuteur = null; _bpCharger(c); }
  // getBoardsFromControls : colonne par colonne.
  const boards = [];
  for(let i = 0; i < _bp.nx; i++) for(let j = 0; j < _bp.ny; j++){
    const c = _bp.cases[i + _bp.nx * j];
    if(c && c.board) boards.push(Object.assign(_copieBoard(c.board), {col: i, row: j}));
  }
  if(!boards.length){ _bpEtat('No boards selected'); return; }
  const manque = _bp.nx * _bp.ny !== boards.length, releve = _bp.releve, nouvelle = _bp.nouvelle;
  _bp.valide = true;
  fermerChoixBoards();
  if(manque){
    // Chatter.send : affiché, envoyé et consigné.
    const msg = 'Board missing. Your board configuration must match X by X row/column matrix. Use NUL or NULV boards if required';
    _logCmd('CHAT' + msg);
    afficherChat(msg);
  }
  await appliquerBoards(boards, releve);
  // Partie neuve dans une salle : les autres joueurs reçoivent la carte faite.
  if(nouvelle && typeof netPartieChargee === 'function') netPartieChargee();
  // OB lu dans le PDF d'un scénario : son panneau s'ouvre (js/ob-panneau.js).
  if(typeof obApresBoards === 'function') obApresBoards();
}

// SetBoards exécutée ici seulement (ni M/, ni log, ni réseau), puis les
// pièces reprennent leur place sur leur board, s'il est encore là.
async function appliquerBoards(boards, releve){
  _bpEnCours = true;
  try {
    replierTout();
    selectionner([]);
    chatLog('🗺️ Pick new boards — boards : '
      + boards.map(b => b.name + (b.reversed ? ' (retourné)' : '')).join(', '));
    for(const b of boards){ delete b._comp; delete b._compCrop; delete b._compPlein; }
    await _renderBoards(boards);
    const n = restaurerPions(boards, releve);
    if(releve.length) chatLog(`♟️ ${n} pion(s) replacé(s) sur leur board` + (n < releve.length ? `, ${releve.length - n} laissé(s) en place (board retiré)` : ''));
    // Le segment « bd » de la sauvegarde.
    const seg = segmentBoards(boards);
    _source = _source.map(s => s.startsWith('bd\t') ? seg + s.match(/\\*$/)[0] : s);
    _etendreCarte();
    rendreToutes();
    afficherPile();
    poserTables();
    if(typeof planifierOverlaysExt === 'function') planifierOverlaysExt();
    if(typeof carteLOS === 'function') carteLOS();
    _partieModifiee = true;
    if(typeof _planifierSecours === 'function') _planifierSecours();
    majBoutons();
  } catch(e){
    chatLog('❌ Changement de boards impossible : ' + (e && e.message ? e.message : e));
    console.error(e);
  } finally {
    _bpEnCours = false;
  }
}

// recordPiecePositions : chaque pièce de la carte posée sur un board, avec
// le nom de ce board et son point dans l'image non recadrée.
function releverPions(boards){
  const out = [];
  for(const p of vivants()){
    const b = boardSous(boards, p.x, p.y);
    if(!b) continue;
    out.push({p, nom: b.name, l: localCoordinates(b, {x: p.x - BOARD_MARGIN - b.px, y: p.y - BOARD_MARGIN - b.py})});
  }
  return out;
}

// restorePiecePositions : le premier board du même nom ; sans lui, la
// pièce garde ses coordonnées. Rend le nombre de pièces replacées.
function restaurerPions(boards, releve){
  let n = 0;
  for(const r of releve){
    if(r.p.deleted || !pions.includes(r.p)) continue;
    const b = boards.find(x => x.name === r.nom);
    if(!b) continue;
    const g = globalCoordinates(b, r.l);
    r.p.x = g.x + b.px + BOARD_MARGIN;
    r.p.y = g.y + b.py + BOARD_MARGIN;
    _majSeg(r.p);
    n++;
  }
  return n;
}

// ═══ CROP BOARDS — ASLBoardPicker.Cropper ═══
// Fenêtre modale au-dessus du dialogue : le board (liste prise à
// l'ouverture, dans l'ordre de getBoardsFromControls ; deux boards du
// même nom se distinguent par leur rang), Hexrows de–à, Coords de–à,
// « Crop to middle of hex row » (choisi par défaut) ou « Crop to nearest
// full hex row », Crop et Done. Crop, ou Entrée dans un champ, recadre
// puis vide les champs ; une saisie invalide ne fait rien, sans message.
let _rg = null;

function ouvrirRognage(){
  if(!_bp || _rg || _ov || _te) return;
  const noms = _bpBoardsControles().map(b => b.name);
  const voile = document.createElement('div');
  voile.className = 'voile voileRognage';
  const w = document.createElement('div');
  w.className = 'fenetre fenRognage';
  w.setAttribute('role', 'dialog');
  w.setAttribute('aria-modal', 'true');
  w.setAttribute('aria-label', 'Crop boards');
  w.innerHTML = `<div class="ft">Crop boards</div><div class="fc">
      <label class="rgLigne">Board: <select data-rg="board"></select></label>
      <div class="rgLigne"><span>Hexrows:</span><input data-rg="row1" size="2" autocomplete="off" spellcheck="false" aria-label="Hexrows from"><span>-</span><input data-rg="row2" size="2" autocomplete="off" spellcheck="false" aria-label="Hexrows to"></div>
      <div class="rgLigne"><span>Coords:</span><input data-rg="coord1" size="2" autocomplete="off" spellcheck="false" aria-label="Coords from"><span>-</span><input data-rg="coord2" size="2" autocomplete="off" spellcheck="false" aria-label="Coords to"></div>
      <label class="coche"><input type="radio" name="rgMode" data-rg="demi" checked><span>Crop to middle of hex row</span></label>
      <label class="coche"><input type="radio" name="rgMode" data-rg="plein"><span>Crop to nearest full hex row</span></label>
      <div class="fb"><button type="button" class="prim" data-rg="crop">Crop</button><button type="button" data-rg="done">Done</button></div>
    </div>`;
  const sel = w.querySelector('[data-rg="board"]');
  noms.forEach((n, i) => { const o = document.createElement('option'); o.value = String(i); o.textContent = n; sel.appendChild(o); });
  document.body.append(voile, w);
  _rg = {w, voile};
  const q = s => w.querySelector(`[data-rg="${s}"]`);
  w.addEventListener('keydown', e => {
    e.stopPropagation();
    if(e.key === 'Escape'){ e.preventDefault(); fermerRognage(); }
    else if(e.key === 'Enter' && e.target.matches('input:not([type=radio])')){ e.preventDefault(); rogner(); }
  });
  w.addEventListener('pointerdown', e => e.stopPropagation());
  voile.addEventListener('pointerdown', e => { e.preventDefault(); e.stopPropagation(); });
  _deplacableParTitre(w);
  q('crop').onclick = () => rogner();
  q('done').onclick = () => fermerRognage();
  setTimeout(() => { if(_profil === 'pc') q('row1').focus(); }, 0);

  function rogner(){
    const v = s => q(s).value.toLowerCase().trim();
    try {
      const c = _bpCaseDuRang(sel.selectedIndex);
      cropBoard(c.board, v('row1'), v('row2'), v('coord1'), v('coord2'), q('plein').checked);
      _bpApercu(c);
    } catch(e){ /* comme VASL : rien */ }
    for(const s of ['row1', 'row2', 'coord1', 'coord2']) q(s).value = '';
  }
}
function fermerRognage(){
  if(!_rg) return;
  _rg.w.remove(); _rg.voile.remove();
  _rg = null;
}

// getBoardsFromControls : les boards des cases, colonne par colonne.
function _bpBoardsControles(){
  const out = [];
  for(let i = 0; i < _bp.nx; i++) for(let j = 0; j < _bp.ny; j++){
    const c = _bp.cases[i + _bp.nx * j];
    if(c && c.board) out.push(c.board);
  }
  return out;
}
// ASLBoardPicker.newmatch : la case du y-ième board, cherchée parmi les
// premières cases seulement (autant que de boards). Avec une case vide
// avant lui dans la grille, le board n'est pas trouvé et le recadrage
// échoue, comme dans VASL.
function _bpCaseDuRang(y){
  const boards = _bpBoardsControles(), b = boards[y];
  for(let i = 0; i < boards.length; i++) if(b && _bp.cases[i] && _bp.cases[i].board === b) return _bp.cases[i];
  throw new Error('board introuvable');
}

// ASLBoard.crop : bornes en pixels de l'image non recadrée, d'après les
// centres des hexagones (HexGrid.getLocation). Une borne vide garde le
// bord du board (largeur ou hauteur -1 : jusqu'au bout). Lève une erreur
// sur un nom d'hexagone invalide.
function cropBoard(b, row1, row2, coord1, coord2, nearestFullRow){
  const g = geoBoard(b), dx = g.hw, dy = g.hh;
  const loc = n => {
    const p = centreHex(g, n);
    if(!p) throw new Error('hexagone ' + n + ' invalide');
    return p;
  };
  const r = {x: 0, y: 0, w: -1, h: -1};
  r.x = row1.length === 0 ? 0 : loc(row1 + '0').x;
  r.w = row2.length === 0 ? -1 : loc(row2 + '0').x - r.x;
  r.y = coord1.length === 0 ? 0 : loc('a' + coord1).y - Math.trunc(dy / 2);
  // « hack to fix problem with RB cropping »
  if(dy === 64.47 || dy === 64.4528){
    if(coord1 === '') coord1 = '0';
    if(!/^[+-]?\d+$/.test(coord1)) throw new Error('coord ' + coord1);
    r.y += Math.ceil(parseInt(coord1, 10) / 10);
  }
  r.h = coord2.length === 0 ? -1 : loc('a' + coord2).y + Math.trunc(dy / 2) - r.y;
  if(nearestFullRow){
    if(r.w > 0 && Math.abs(r.x + r.w - g.w) > dx / 4) r.w += Math.trunc(dx / 2);
    if(r.x !== 0){
      r.x -= Math.trunc(dx / 2);
      r.w += Math.trunc(dx / 2);
    }
  }
  b.crop = r.x === 0 && r.y === 0 && r.w <= 0 && r.h <= 0 ? null : r;
  b.fh = !!nearestFullRow;
  b.cropModifie = true;
  return b.crop;
}

// ═══ ADD OVERLAYS — ASLBoardPicker.Overlayer ═══
// Overlay, Board (liste prise à l'ouverture), Hex 1, Hex 2, Add et Done ;
// la ligne d'état dit le résultat. Add, ou Entrée dans un champ, puis les
// trois champs se vident. Hexagones vides : l'overlay de ce nom est retiré
// du board ; un seul hexagone : les deux sont égaux.
// « Preserve Board Elevation » : cochée au moment d'un Add, elle vaut pour
// la suite du dialogue (preservelevels) ; les overlays ajoutés gardent les
// niveaux du board, et Done active PersistElevation pour la partie.
let _ov = null;

function ouvrirOverlays(){
  if(!_bp || _ov || _rg || _te) return;
  const noms = _bpBoardsControles().map(b => b.name);
  const voile = document.createElement('div');
  voile.className = 'voile voileRognage';
  const w = document.createElement('div');
  w.className = 'fenetre fenRognage fenOverlays';
  w.setAttribute('role', 'dialog');
  w.setAttribute('aria-modal', 'true');
  w.setAttribute('aria-label', 'Overlays');
  w.innerHTML = `<div class="ft">Overlays</div><div class="fc">
      <div class="ovEtat" aria-live="polite">Enter blank hexes to delete.</div>
      <div class="ovChamps">
        <label>Overlay<input data-ov="nom" autocomplete="off" spellcheck="false" autocapitalize="off"></label>
        <label>Board<select data-ov="board"></select></label>
        <label>Hex 1<input data-ov="hex1" autocomplete="off" spellcheck="false" autocapitalize="off"></label>
        <label>Hex 2<input data-ov="hex2" autocomplete="off" spellcheck="false" autocapitalize="off"></label>
      </div>
      <label class="coche"><input type="checkbox" data-ov="preserve"><span>Preserve Board Elevation</span></label>
      <div class="fb"><button type="button" class="prim" data-ov="add">Add</button><button type="button" data-ov="done">Done</button></div>
    </div>`;
  const sel = w.querySelector('[data-ov="board"]');
  noms.forEach((n, i) => { const o = document.createElement('option'); o.value = String(i); o.textContent = n; sel.appendChild(o); });
  document.body.append(voile, w);
  _ov = {w, voile};
  const q = s => w.querySelector(`[data-ov="${s}"]`);
  const etat = t => { q('add').closest('.fc').querySelector('.ovEtat').textContent = t; };
  w.addEventListener('keydown', e => {
    e.stopPropagation();
    if(e.key === 'Escape'){ e.preventDefault(); fermerOverlays(); }
    else if(e.key === 'Enter' && e.target.matches('input')){ e.preventDefault(); ajouter(); }
  });
  w.addEventListener('pointerdown', e => e.stopPropagation());
  voile.addEventListener('pointerdown', e => { e.preventDefault(); e.stopPropagation(); });
  _deplacableParTitre(w);
  q('add').onclick = () => ajouter();
  q('done').onclick = () => {
    // ASLPersistElevation.togglePersistElevation, sur une instance neuve :
    // toujours l'activation.
    if(_bp && _bp.preserve) activerPersistElevation();
    fermerOverlays();
  };
  setTimeout(() => { if(_profil === 'pc') q('nom').focus(); }, 0);

  let enCours = false;
  async function ajouter(){
    if(enCours) return;
    const nom = q('nom').value.toLowerCase(), h1 = q('hex1').value.toLowerCase(), h2 = q('hex2').value.toLowerCase();
    if(q('preserve').checked && _bp) _bp.preserve = true;
    let c;
    try { c = _bpCaseDuRang(sel.selectedIndex); } catch(e){ c = null; }
    if(!c){
      for(const s of ['nom', 'hex1', 'hex2']) q(s).value = '';
      etat('Missing board: fix Map Config.');
      return;
    }
    enCours = true;
    q('add').disabled = true;
    etat('Working ...');
    let msg;
    try { msg = await ajouterOverlay(c, nom, h1, h2); }
    catch(e){ msg = e.message; }
    finally { enCours = false; q('add').disabled = false; }
    if(_ov && _ov.w === w) etat(msg);
    for(const s of ['nom', 'hex1', 'hex2']) q(s).value = '';
  }
}
function fermerOverlays(){
  if(!_ov) return;
  _ov.w.remove(); _ov.voile.remove();
  _ov = null;
}

// Le board d'une case a changé d'overlays : son état les réécrira, son
// aperçu se recompose.
function _bpOverlaysChanges(c){
  const b = c.board;
  b.ovrModifie = true;
  b._comp = b._compPlein = null;
  _bpApercu(c);
}

// ASLBoardSlot.addOverlay. Rend le message de la ligne d'état ; une
// erreur porte le message de VASL (BoardException).
async function ajouterOverlay(c, nom, hex1, hex2){
  const b = c.board;
  if(hex1 === '' && hex2 === ''){
    const avant = b.overlays.length;
    b.overlays = b.overlays.filter(o => o.nom !== nom);
    if(b.overlays.length !== avant) _bpOverlaysChanges(c);
    return 'Removed Overlay ' + nom;
  }
  if(hex2.length === 0) hex2 = hex1;
  if(hex1.length === 0) hex1 = hex2;
  await chargerGeoBoard(b.name);
  const o = await construireOverlay(b, nom, hex1, hex2);
  b.overlays.push(Object.assign({nom: o.nom, hex1: o.hex1, hex2: o.hex2}, _bp.preserve ? {persist: true} : {}));
  _bpOverlaysChanges(c);
  await _checkOverlap(c, o);
  return `Added Overlay ${o.nom} (ver ${o.version})`;
}

// new Overlay(« nom \t hex1 \t hex2 », board) : readData (origines,
// version, placeAt), setBounds, puis check — dans cet ordre, avec leurs
// messages. Les bornes sont dans le repère du board non recadré.
async function construireOverlay(b, nom, hex1, hex2){
  const g = geoBoard(b);
  const arch = archiveOverlay(nom);
  let a;
  try { a = await chargerArchiveOverlay(arch); }
  catch(e){ throw new Error(e.message); }
  if(!_fichier(a.files, 'data')) throw new Error('data not found in ' + arch);
  const d = _lireDataOverlay(a.data, nom);
  let version = '0';
  for(const l of a.data.split(/\r?\n/)){
    const tk = l.trim().split(/\s+/);
    if(tk.length >= 2 && tk[0].toLowerCase() === 'version') version = tk[1];
  }
  if(d.placeAt) [hex1, hex2] = d.placeAt;
  const loc = n => { const p = centreHex(g, n); if(!p) throw new Error('Bad Hex Coordinates'); return p; };
  const offset = c => {
    const n = _origine(d.origines, c, b.name), p = n && centreHex(g, n);
    if(!p) throw new Error(`Illegal orientation '${c}' for overlay ${nom}`);
    return p;
  };
  const image = c => _fichier(a.files, imageOverlay(nom + c));
  const taille = octets => (octets && octets.length > 10 && octets[0] === 0x47)
    ? {w: octets[6] | (octets[7] << 8), h: octets[8] | (octets[9] << 8)} : null;
  // setBounds
  const c = _orientation(g, hex1, hex2);
  const p1 = loc(hex1), po = offset(c);
  const dim = taille(image(c));
  if(!dim) throw new Error('Overlay not found in ' + arch);
  // check
  const unHex = !!image('a') && !image('d');
  if(unHex){
    if(hex2 !== hex1 && hex1.length > 0 && hex2.length > 0) throw new Error('Specify a single hex');
  } else if(hex1.length === 0 || hex2.length === 0 || hex1 === hex2) throw new Error('Specify two hexes');
  const p2 = loc(hex2);
  const dx = loc('B1').x - loc('A1').x, dy = loc('B1').y - loc('B0').y;
  if(Math.abs(p2.x - p1.x) > 1.1 * dx || Math.abs(p2.y - p1.y) > 1.1 * dy) throw new Error('Illegal coordinates');
  return {nom, hex1, hex2, version, x: p1.x - po.x, y: p1.y - po.y, w: dim.w, h: dim.h};
}

// ASLHexGrid.locationName (sans les côtés : les points viennent de centres
// d'hexagones) ; « offboard » hors de la grille.
function nomHexLocal(g, p){
  const x = p.x - g.x0, y = p.y + Math.trunc(g.hh / 2 - g.y0);
  const i = Math.floor(x / g.hw + 0.5);
  let r = i % 2 === 0 ? Math.floor(y / g.hh) + 1 : Math.floor(y / g.hh + 0.5);
  if(g.alt && i % 2 !== 0) r++;
  if(i < 0 || r < 0) return 'offboard';
  return hexrow(i) + r;
}

// Taille affichée d'un board de la grille (bounds()).
function _dimsBoard(b){
  const g = geoBoard(b), z = b.zoom || 1;
  b.dispW = Math.round(((b.crop && b.crop.w > 0) ? b.crop.w : g.w) * z);
  b.dispH = Math.round(((b.crop && b.crop.h > 0) ? b.crop.h : g.h) * z);
}

// ASLBoardSlot.checkOverlap : un overlay qui dépasse de son board est
// recopié sur chaque voisin de la grille qu'il touche (diagonales
// comprises), ses hexagones traduits dans le repère du voisin. Une copie
// impossible est ignorée sans message.
async function _checkOverlap(c, o){
  const b = c.board;
  _dimsBoard(b);
  const q = globalCoordinates(b, {x: o.x, y: o.y});
  if(b.reversed){ q.x -= o.w; q.y -= o.h; }
  const gauche = q.x < 0, droite = q.x + o.w > b.dispW, haut = q.y < 0, bas = q.y + o.h > b.dispH;
  const dirs = [];
  if(gauche){ if(haut) dirs.push([-1, -1]); dirs.push([-1, 0]); if(bas) dirs.push([-1, 1]); }
  if(droite){ if(haut) dirs.push([1, -1]); dirs.push([1, 0]); if(bas) dirs.push([1, 1]); }
  if(haut) dirs.push([0, -1]);
  if(bas) dirs.push([0, 1]);
  for(const [dx, dy] of dirs) await _overlap(c, o, dx, dy);
}
async function _overlap(c, o, dx, dy){
  const i = _bp.cases.indexOf(c), x = i % _bp.nx + dx, y = Math.floor(i / _bp.nx) + dy;
  if(x < 0 || x >= _bp.nx || y < 0 || y >= _bp.ny) return;
  const v = _bp.cases[x + y * _bp.nx];
  if(!v || !v.board) return;
  const b = c.board, autre = v.board;
  await chargerGeoBoard(autre.name);
  _dimsBoard(b); _dimsBoard(autre);
  const offX = dx === -1 ? autre.dispW : dx === 1 ? -b.dispW : 0;
  const offY = dy === -1 ? autre.dispH : dy === 1 ? -b.dispH : 0;
  const g = geoBoard(b), ga = geoBoard(autre);
  const traduire = h => {
    const p = globalCoordinates(b, centreHex(g, h));
    return nomHexLocal(ga, localCoordinates(autre, {x: p.x + offX, y: p.y + offY}));
  };
  try {
    const n = await construireOverlay(autre, o.nom, traduire(o.hex1), traduire(o.hex2));
    autre.overlays.push(Object.assign({nom: n.nom, hex1: n.hex1, hex2: n.hex2}, _bp.preserve ? {persist: true} : {}));
    _bpOverlaysChanges(v);
  } catch(e){ /* BoardException : ignorée */ }
}

// ═══ TERRAIN SSR — ASLBoardPicker.TerrainEditor ═══
// « Terrain Transformations » : en haut les options de base (Basic : mois,
// vergers, céréales…), puis la ligne d'état ; à gauche Board (vide = tous
// les boards), Apply, Reset, Done ; au milieu la liste des catégories
// (Option), à droite les réglages de la catégorie choisie. Les options
// viennent de boardData/SSRControls du module, puis des catégories
// « Transformations » (quatre lignes « All … is … ») et de celles du
// fichier SSRControls de chaque board. Une option marquée <Source/>
// coche ou décoche, par ses <Target>, les entrées qui en dépendent.
// Apply ajoute les règles des catégories à la case (ASLBoardSlot.terrain)
// et pose sur le board « règles de base + règles de la case », puis remet
// les catégories à leur défaut ; Reset efface les règles de toutes les
// cases ; Done remet toutes les options à leur défaut et ferme.
let _te = null;
let _ssrModule = null;        // Promise<texte> de boardData/SSRControls
function texteSSRControls(){
  if(!_ssrModule){
    _ssrModule = fetch(VASL_BOARDDATA_URL + 'SSRControls').then(r => {
      if(!r.ok) throw new Error('SSRControls introuvable (HTTP ' + r.status + ')');
      return r.text();
    });
    _ssrModule.catch(() => { _ssrModule = null; });
  }
  return _ssrModule;
}

// Une option de terrain (TerrainOption) : Menu, ScrollList ou Checkbox.
function _optionTerrain(ed, e){
  const o = {nom: e.getAttribute('name') || '', type: e.tagName, regles: new Map(), textes: new Map(),
             defauts: [], entrees: [], el: document.createElement('div')};
  o.el.className = 'teOption';
  if(e.getElementsByTagName('Source').length) ed.sources.add(o);
  let ctrl;
  if(o.type === 'Menu') ctrl = document.createElement('select');
  else if(o.type === 'ScrollList'){ ctrl = document.createElement('select'); ctrl.multiple = true; }
  else if(o.type === 'Checkbox'){ ctrl = document.createElement('input'); ctrl.type = 'checkbox'; }
  else throw new Error('Unrecognized SSR component type ' + o.type);
  o.ctrl = ctrl;
  let texteCase = '';
  for(const en of e.getElementsByTagName('entry')){
    const n = en.getAttribute('name') || '';
    o.entrees.push(n);
    o.regles.set(n, (en.getAttribute('rule') || '').replace(/,/g, '\t'));
    o.textes.set(n, en.getAttribute('text') || '');
    if(o.type === 'Checkbox') texteCase = n;
    else {
      const op = document.createElement('option');
      op.value = op.textContent = n;
      ctrl.appendChild(op);
      if(o.type === 'Menu' && en.getAttribute('default')) o.defauts.push(n);
    }
    for(const t of en.getElementsByTagName('Target')){
      const act = [...t.getElementsByTagName('activate')].map(a => a.getAttribute('sourceProperty'));
      const des = [...t.getElementsByTagName('deactivate')].map(a => a.getAttribute('sourceProperty'));
      if(act.length || des.length){
        const src = t.getAttribute('sourceName') || '';
        if(!ed.cibles.has(src)) ed.cibles.set(src, []);
        ed.cibles.get(src).push({o, prop: n, act: act.length ? act : null, des: des.length ? des : null});
      }
    }
  }
  if(o.type === 'ScrollList') ctrl.size = Math.max(2, Math.min(6, o.entrees.length));
  o.texteCase = texteCase;
  // Le nom, sauf pour une case à cocher qui le porte déjà.
  if(o.type === 'Checkbox'){
    const l = document.createElement('label');
    l.className = 'coche';
    const sp = document.createElement('span');
    sp.textContent = texteCase;
    l.append(ctrl, sp);
    if(o.nom !== texteCase){ const t = document.createElement('span'); t.className = 'teNom'; t.textContent = o.nom; o.el.appendChild(t); }
    o.el.appendChild(l);
  } else {
    const t = document.createElement('span');
    t.className = 'teNom';
    t.textContent = o.nom;
    o.el.append(t, ctrl);
  }
  o.el.hidden = e.getElementsByTagName('invisible').length > 0;
  o.actives = () => {
    if(o.type === 'Checkbox') return [ctrl.checked ? texteCase : ''];
    return [...ctrl.options].filter(x => x.selected).map(x => x.value);
  };
  o.activer = (val, oui) => {
    if(o.type === 'Checkbox') ctrl.checked = oui && texteCase === val;
    else if(o.type === 'Menu'){
      if(val == null || !oui) ctrl.value = o.defauts.length ? o.defauts[0] : (o.entrees[0] ?? '');
      else if(o.entrees.includes(val)) ctrl.value = val;
    } else {
      const op = [...ctrl.options].find(x => x.value === val);
      if(op) op.selected = oui;
    }
    _teChange(ed, o);
  };
  o.reset = () => {
    for(const v of o.actives()) o.activer(v, false);
    for(const v of o.defauts) o.activer(v, true);
  };
  o.regle = () => o.actives().map(v => o.regles.get(v)).filter(x => x).map(x => x + '\t').join('');
  o.texte = () => o.actives().map(v => o.textes.get(v)).filter(x => x).map(x => x + ', ').join('');
  ctrl.addEventListener('change', () => _teChange(ed, o));
  return o;
}

// TransformOption : « All [de] is [vers] » → « DeToVers ».
const TE_DE = ['-', 'Woods', 'Brush', 'Grain', 'Marsh', 'Marsh -1', 'Marsh 0', 'Level -1', 'Level 1', 'Level 2', 'Level 3', 'Level 4'];
const TE_VERS = ['-', 'Woods', 'Brush', 'Grain', 'Marsh', 'Level -1', 'Level 0', 'Level 1', 'Water'];
function _optionTransformation(){
  const o = {nom: '', type: 'Transform', defauts: [], el: document.createElement('div')};
  o.el.className = 'teOption teTransfo';
  const liste = l => { const s = document.createElement('select'); for(const v of l){ const op = document.createElement('option'); op.value = op.textContent = v; s.appendChild(op); } return s; };
  const de = liste(TE_DE), vers = liste(TE_VERS);
  const mot = t => { const sp = document.createElement('span'); sp.textContent = t; return sp; };
  o.el.append(mot('All'), de, mot('is'), vers);
  const nom = v => v.split(/\s+/).join('').replace(/-/g, '_');
  o.regle = () => de.value !== '-' && vers.value !== '-' ? nom(de.value) + 'To' + nom(vers.value) : '';
  o.texte = () => de.value !== '-' && vers.value !== '-' ? 'all ' + de.value.toLowerCase() + ' is ' + vers.value.toLowerCase() : '';
  o.reset = () => { de.selectedIndex = 0; vers.selectedIndex = 0; };
  o.actives = () => [];
  return o;
}

// TerrainMediator : une source change → ses cibles s'activent (une de
// leurs propriétés « activate » est choisie) ou se désactivent.
function _teChange(ed, o){
  if(!ed.sources.has(o) || ed.profondeur > 20) return;
  ed.profondeur++;
  try {
    const actives = o.actives();
    for(const c of ed.cibles.get(o.nom) || []){
      if(c.act && c.act.some(v => actives.includes(v))) c.o.activer(c.prop, true);
      else if(c.des && c.des.some(v => actives.includes(v))) c.o.activer(c.prop, false);
    }
  } finally { ed.profondeur--; }
}

// SSRControlsFile : les Basic, puis chaque Option (catégorie) et ses
// réglages. Une catégorie déjà présente n'est pas reprise à l'écran (ses
// réglages comptent quand même, comme dans VASL).
function _teLireOptions(ed, texte){
  let doc;
  try { doc = new DOMParser().parseFromString(texte, 'application/xml'); } catch(e){ return; }
  if(!doc || doc.getElementsByTagName('parsererror').length) return;
  const basic = doc.getElementsByTagName('Basic')[0];
  if(basic){
    const panneau = document.createElement('div');
    panneau.className = 'teBasic';
    for(const e of basic.children){
      try { const o = _optionTerrain(ed, e); panneau.appendChild(o.el); ed.base.push(o); } catch(err){}
    }
    ed.basics.unshift(panneau);
  }
  for(const e of doc.getElementsByTagName('Option')){
    const boite = document.createElement('div');
    boite.className = 'teCategorie';
    for(const x of e.children){
      try { const o = _optionTerrain(ed, x); boite.appendChild(o.el); ed.groupe.push(o); } catch(err){}
    }
    _teCategorie(ed, e.getAttribute('name') || '', boite);
  }
}
function _teCategorie(ed, nom, boite){
  if(ed.categories.has(nom)) return;
  ed.categories.set(nom, boite);
}

// L'éditeur des règles de terrain (sans fenêtre) : SSRControls du module,
// les quatre transformations, puis les options de chaque board. Chaque
// option garde le rang du board qui l'a apportée (o.rangBoard), pour les
// overlays propres à un scénario (js/ob-panneau.js).
async function _teEditeur(boards, surErreur){
  const ed = {sources: new Set(), cibles: new Map(), base: [], basics: [], groupe: [], categories: new Map(), profondeur: 0};
  let texte = '';
  try { texte = await texteSSRControls(); } catch(e){ if(surErreur) surErreur(e); }
  _teLireOptions(ed, texte);
  // setup : les quatre transformations, puis les options de chaque board.
  const transfo = document.createElement('div');
  transfo.className = 'teCategorie';
  for(let i = 0; i < 4; i++){ const o = _optionTransformation(); transfo.appendChild(o.el); ed.groupe.push(o); }
  _teCategorie(ed, 'Transformations', transfo);
  let versions = '', n = 0;
  for(const [rang, b] of boards.entries()){
    const g = await chargerGeoBoard(b.name);
    if(g.defaut) continue;
    try {
      const files = await _archive(b.name);
      const ctl = _fichier(files, 'SSRControls');
      const avant = ed.groupe.length;
      if(ctl) _teLireOptions(ed, new TextDecoder().decode(ctl));
      for(const o of ed.groupe.slice(avant)) o.rangBoard = rang;
    } catch(e){}
    n++;
    versions += `${g.nomBoard} (ver ${g.version}) `;
  }
  return {ed, n, versions};
}

async function ouvrirTerrain(){
  if(!_bp || _te || _rg || _ov) return;
  const bp = _bp;
  const boards = _bpBoardsControles();
  _bpEtat('Working ...');
  const {ed, n, versions} = await _teEditeur(boards, e => _bpEtat('⚠️ ' + e.message));
  if(_bp !== bp || _te) return;
  _bpEtat('');
  const voile = document.createElement('div');
  voile.className = 'voile voileRognage';
  const w = document.createElement('div');
  w.className = 'fenetre fenRognage fenTerrain';
  w.setAttribute('role', 'dialog');
  w.setAttribute('aria-modal', 'true');
  w.setAttribute('aria-label', 'Terrain Transformations');
  w.innerHTML = `<div class="ft">Terrain Transformations</div><div class="fc">
      <div class="teBasics"></div>
      <input class="teEtat" readonly aria-label="Status" value="Leave board number blank to apply to all boards:  ">
      <div class="teCorps">
        <div class="teBoutons">
          <label class="teBoard">Board <select data-te="board"></select></label>
          <button type="button" class="prim" data-te="apply">Apply</button>
          <button type="button" data-te="reset">Reset</button>
          <button type="button" data-te="done">Done</button>
        </div>
        <select class="teListe" size="6" aria-label="Categories"></select>
        <div class="teCartes"></div>
      </div>
    </div>`;
  const q = s => w.querySelector(`[data-te="${s}"]`);
  w.querySelector('.teBasics').append(...ed.basics);
  // Board : une ligne vide (tous les boards), puis les boards de la grille.
  const sel = q('board');
  ['', ...boards.map(b => b.name)].forEach((nom, i) => { const o = document.createElement('option'); o.value = String(i); o.textContent = nom; sel.appendChild(o); });
  const liste = w.querySelector('.teListe'), cartes = w.querySelector('.teCartes');
  for(const [nom, boite] of ed.categories){
    const o = document.createElement('option');
    o.value = o.textContent = nom;
    liste.appendChild(o);
    boite.hidden = true;
    cartes.appendChild(boite);
  }
  // CardLayout : la première carte est montrée tant que rien n'est choisi.
  const montrer = nom => { for(const [k, b] of ed.categories) b.hidden = k !== nom; };
  montrer([...ed.categories.keys()][0]);
  liste.addEventListener('change', () => montrer(liste.value));
  document.body.append(voile, w);
  _te = {w, voile, ed};
  const etat = t => { w.querySelector('.teEtat').value = t; };
  // Le terrain est remis à ses défauts, puis la ligne d'état dit les boards.
  _teReset(ed, true);
  etat(n === 0 ? 'No boards loaded' : n === 1 ? 'Loaded board ' + versions
     : 'Loaded boards ' + versions + ' (leave board number blank to apply to all)');
  w.addEventListener('keydown', e => {
    e.stopPropagation();
    if(e.key === 'Escape'){ e.preventDefault(); terminer(); }
  });
  w.addEventListener('pointerdown', e => e.stopPropagation());
  voile.addEventListener('pointerdown', e => { e.preventDefault(); e.stopPropagation(); });
  _deplacableParTitre(w);
  const terminer = () => { _teReset(ed, true); fermerTerrain(); };
  q('done').onclick = terminer;
  q('apply').onclick = () => {
    etat('Working ...');
    const regles = _teReglesOptions(ed), base = ed.base.map(o => o.regle()).join('');
    const cases = [];
    if(sel.selectedIndex <= 0){
      for(let t = 0; t < sel.options.length - 1; t++){
        let c = null;
        try { c = _bpCaseDuRang(t); } catch(e){}
        if(!c){ etat('Incorrect Board Configuration: must complete X by X rectangle - use bdNull'); return; }
        cases.push(c);
      }
    } else {
      let c = null;
      try { c = _bpCaseDuRang(sel.selectedIndex - 1); } catch(e){}
      if(!c){ etat('Incorrect Board Configuration: must complete X by X rectangle - use bdNull'); return; }
      cases.push(c);
    }
    for(const c of cases){
      c.terrain = c.terrain + '\t' + regles;
      if(c.board) _bpTerrain(c, base + c.terrain);
    }
    _teReset(ed, false);
  };
  q('reset').onclick = () => {
    _teReset(ed, true);
    for(const c of _bp.cases){
      c.terrain = '';
      if(c.board) _bpTerrain(c, '');
    }
    etat('Back to normal');
  };
}
// « Apply » du dialogue Terrain pour un seul board (le rang-ième de la
// grille), avec les options ed.groupe dans leur état : ce que fait le
// joueur qui choisit le board, coche, puis Apply.
function _teAppliquerAuBoard(ed, rang){
  const regles = _teReglesOptions(ed), base = ed.base.map(o => o.regle()).join('');
  const c = _bpCaseDuRang(rang);
  c.terrain = c.terrain + '\t' + regles;
  if(c.board) _bpTerrain(c, base + c.terrain);
  _teReset(ed, false);
}

// ── Lecture du PDF d'un scénario (js/ob-*.js) : chargée à la demande, au
// premier clic sur « Depuis un PDF… », avec la version de l'application.
const OB_MODULES = ['js/ob-image.js', 'js/ob-pdf.js', 'js/ob-ocr.js', 'js/donnees/ob-reference.js',
  'js/ob-lecture.js', 'js/ob-carte.js', 'js/ob-panneau.js'];
let _obModules = null;
function chargerModulesOb(){
  if(!_obModules){
    const s0 = [...document.scripts].find(s => /js\/boards\.js/.test(s.src));
    const v = s0 && /\?v=[^&]+/.exec(s0.src);
    _obModules = OB_MODULES.reduce((p, f) => p.then(() => new Promise((ok, ko) => {
      const s = document.createElement('script');
      s.src = f + (v ? v[0] : '');
      s.onload = ok;
      s.onerror = () => ko(new Error(f + ' introuvable'));
      document.head.appendChild(s);
    })), Promise.resolve());
    _obModules.catch(() => { _obModules = null; });
  }
  return _obModules;
}

function fermerTerrain(){
  if(!_te) return;
  _te.w.remove(); _te.voile.remove();
  _te = null;
}
// reset(optionGroup), et les options de base avec tout.
function _teReset(ed, tout){
  for(const o of [...(tout ? ed.base : []), ...ed.groupe]) o.reset();
}
// optionRules : les règles de chaque réglage, séparées par une tabulation
// quand il y a déjà quelque chose (chaque règle finit déjà par la sienne).
function _teReglesOptions(ed){
  let s = '';
  for(const o of ed.groupe){
    const r = o.regle();
    s = s.length > 0 && r.length > 0 ? s + '\t' + r : s + r;
  }
  return s;
}
// ASLBoard.setTerrain : les règles écrites du board, et son aperçu.
function _bpTerrain(c, terrain){
  const b = c.board;
  b.terrain = terrain;
  b.ssrList = _reglesDuTerrain(terrain);
  b.ssrModifie = true;
  b._comp = b._compPlein = null;
  _bpApercu(c);
}

// ═══ PERSISTELEVATION — LOSSettingsAndPrefs, ASLPersistElevation ═══
// Propriété du module, gardée dans la partie (GlobalProperty
// « ;PersistElevation;true;Module »). Elle s'active par Done dans Add
// overlays ; la commande part au log et aux autres joueurs, suivie d'un
// message au chat. Elle prend effet à la prochaine composition de la carte
// (Ok du dialogue, ouverture de la partie) : chaque overlay y garde les
// niveaux du board.
const CMD_PERSIST = 'command.los.persistelevation|';
function fixerPersistElevation(oui){
  const v = oui ? 'true' : 'false';
  _globales.PersistElevation = v;
  _globConnu.set(_cleGlob('PersistElevation', 'Module'), v);
  // Une partie sans la propriété la reçoit, comme VASL l'écrirait.
  if(_source && !_source.some(x => x.startsWith('GlobalProperty\t;PersistElevation;'))){
    const iFin = _source.lastIndexOf('end_save');
    _source.splice(iFin >= 0 ? iFin : _source.length, 0, `GlobalProperty\t;PersistElevation;${v};Module`);
  }
}
function activerPersistElevation(){
  _avantAction();
  fixerPersistElevation(true);
  _logCmd(CMD_PERSIST + 'true');
  const msg = 'Persist Board Elevation when adding overlays is in effect.';
  _logCmd('CHAT' + msg);
  afficherChat(msg);
}
