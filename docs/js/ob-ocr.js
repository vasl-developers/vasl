// ═══════════════════════════════════════════════════════════════
// OCR — pool de workers tesseract.js pour lire la fiche d'un scénario
// Chaque lecture porte ses réglages (mode de page, liste blanche),
// comme un appel de pytesseract : tesseract.js les pose pour cette
// lecture seulement, puis remet les précédents. Les images partent en
// PGM (obImage.versPGM), lues telles quelles par Leptonica.
// Mêmes réglages que le prototype : moteur LSTM seul (« --oem 3 » avec
// des données sans moteur ancien), données eng de tessdata_fast 4.1.0.
// Les lectures attendent dans une file ; « arreter » la vide et ferme
// les workers (les lectures en attente échouent avec l'erreur
// OB_ARRET).
// ═══════════════════════════════════════════════════════════════
const OB_ARRET = 'Lecture arrêtée';

const obOcr = (() => {
  let _workers = [];          // {w, occupe}
  let _demarrage = null;
  let _file = [];             // {img, opts, sortie, ok, ko}
  let _faits = 0, _total = 0;
  let _surProgression = null;

  const nbParDefaut = () => {
    const n = (typeof navigator !== 'undefined' && navigator.hardwareConcurrency) || 2;
    return Math.max(1, Math.min(4, n - 1));
  };

  // T : l'objet Tesseract (chargé par obChargerTesseract, ou require
  // sous Node) ; options : chemins des fichiers (OB_BIBLIO par défaut).
  function demarrer({T, n = nbParDefaut(), chemins = null, surProgression = null} = {}){
    _surProgression = surProgression;
    if(_demarrage) return _demarrage;
    _demarrage = (async () => {
      if(!T) T = await obChargerTesseract();
      const c = chemins || {
        workerPath: OB_BIBLIO.tesseractWorker, corePath: OB_BIBLIO.tesseractCore, langPath: OB_BIBLIO.langue,
      };
      const opts = {gzip: false, cachePath: 'vaslweb-tessdata-fast-4.1.0', workerBlobURL: true, ...c};
      try {
        _workers = await Promise.all(Array.from({length: n},
          // debug_file : les messages de Tesseract (« Estimating
          // resolution… ») ne vont pas dans la console.
          () => T.createWorker('eng', T.OEM.LSTM_ONLY, opts, {debug_file: '/dev/null'}).then(w => ({w, occupe: false}))));
      } catch(e){
        _workers = []; _demarrage = null;
        throw new Error('Moteur d’OCR indisponible : '
          + (typeof e === 'string' ? e : e && e.message || 'erreur inconnue')
          + '. Une connexion Internet est nécessaire la première fois.');
      }
      return _workers.length;
    })();
    return _demarrage;
  }

  function _servir(){
    for(const x of _workers){
      if(x.occupe || !_file.length) continue;
      const job = _file.shift();
      x.occupe = true;
      x.w.recognize(job.img, job.opts, job.sortie).then(r => {
        x.occupe = false; _faits++;
        job.ok(job.sortie.tsv ? {texte: r.data.text || '', tsv: r.data.tsv || ''} : (r.data.text || ''));
      }, e => {
        x.occupe = false; _faits++;
        job.ko(e instanceof Error ? e : new Error(String(e)));
      }).finally(() => {
        if(_surProgression) try { _surProgression(_faits, _total); } catch(e){}
        _servir();
      });
    }
  }

  // Lit une image en gris {w, h, d}. psm : mode de page (7 = une ligne…),
  // liste : liste blanche ('' = aucune) ; donnees : TSV en plus du texte
  // (comme image_to_data). → texte brut (à « strip » par l'appelant,
  // comme le prototype) ou {texte, tsv}.
  async function lire(img, {psm = 7, liste = '', donnees = false} = {}){
    if(!_demarrage) await demarrer();
    else await _demarrage;
    const opts = {tessedit_pageseg_mode: String(psm)};
    if(liste) opts.tessedit_char_whitelist = liste;
    const sortie = donnees ? {text: true, tsv: true} : {text: true};
    _total++;
    return new Promise((ok, ko) => {
      _file.push({img: obImage.versPGM(img), opts, sortie, ok, ko});
      _servir();
    });
  }

  // Mots d'un TSV (niveau 5) : [{texte, conf, x, y, w, h}], comme les
  // colonnes text / conf de pytesseract.image_to_data.
  function mots(tsv){
    const o = [];
    for(const l of String(tsv).split('\n')){
      const c = l.split('\t');
      if(c.length < 12 || c[0] !== '5') continue;
      o.push({texte: c.slice(11).join('\t'), conf: parseFloat(c[10]), x: +c[6], y: +c[7], w: +c[8], h: +c[9]});
    }
    return o;
  }

  async function arreter(){
    const f = _file; _file = [];
    for(const j of f) j.ko(new Error(OB_ARRET));
    const ws = _workers; _workers = []; _demarrage = null;
    await Promise.all(ws.map(x => x.w.terminate().catch(() => {})));
  }

  const progression = () => ({faits: _faits, total: _total, enAttente: _file.length});
  const remettreCompteurs = () => { _faits = 0; _total = 0; };

  return {demarrer, lire, mots, arreter, progression, remettreCompteurs, nbParDefaut};
})();
if(typeof module === 'object' && module.exports) module.exports = {obOcr, OB_ARRET};
