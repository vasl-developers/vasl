// ═══════════════════════════════════════════════════════════════
// PDF D'UN SCÉNARIO — l'image de la fiche, et les bibliothèques
// chargées à la demande (seulement quand un PDF est ouvert)
// Fiche scannée (une image JPEG par page) : l'image est prise telle
// quelle dans le PDF, sans bibliothèque, comme le fait le prototype
// (pymupdf, extract_image) ; elle est ensuite décodée par le navigateur.
// Autre PDF (image d'un autre format, PDF chiffré, page à vrai texte) :
// la page 1 est rendue par pdf.js à la taille de son image, au moins
// 1700 px de large, et son texte est relevé.
// Les adresses des bibliothèques sont dans OB_BIBLIO ; versions fixées.
// ═══════════════════════════════════════════════════════════════
const OB_BIBLIO = {
  pdfjs: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@4.10.38/build/pdf.min.mjs',
  pdfjsWorker: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@4.10.38/build/pdf.worker.min.mjs',
  tesseract: 'https://cdn.jsdelivr.net/npm/tesseract.js@7.0.0/dist/tesseract.min.js',
  tesseractWorker: 'https://cdn.jsdelivr.net/npm/tesseract.js@7.0.0/dist/worker.min.js',
  tesseractCore: 'https://cdn.jsdelivr.net/npm/tesseract.js-core@7.0.0',
  // Données « eng » du prototype (Tesseract 5.3.4 d'Ubuntu, paquet
  // tesseract-ocr-eng 4.1.0 = tessdata_fast 4.1.0, même fichier à l'octet).
  langue: 'https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0',
};
const OB_LARGEUR_MIN = 1700;          // rendu pdf.js : largeur minimale

const _obHorsLigne = (quoi, e) => new Error(`${quoi} injoignable : une connexion Internet est nécessaire la première fois`
  + (e && e.message ? ` (${e.message})` : ''));

// ── Bibliothèques ───────────────────────────────────────────────
let _obPdfjs = null, _obTesseract = null;
function obChargerPdfjs(){
  if(!_obPdfjs) _obPdfjs = import(OB_BIBLIO.pdfjs).then(m => {
    // Worker d'un autre domaine : pdf.js le lance lui-même par une
    // adresse blob (_createCDNWrapper), ce qui marche aussi en file://.
    m.GlobalWorkerOptions.workerSrc = OB_BIBLIO.pdfjsWorker;
    return m;
  }).catch(e => { _obPdfjs = null; throw _obHorsLigne('pdf.js', e); });
  return _obPdfjs;
}
function obChargerTesseract(){
  if(typeof Tesseract !== 'undefined') return Promise.resolve(Tesseract);
  if(!_obTesseract) _obTesseract = new Promise((ok, ko) => {
    const s = document.createElement('script');
    s.src = OB_BIBLIO.tesseract;
    s.onload = () => typeof Tesseract !== 'undefined' ? ok(Tesseract) : ko(new Error('Tesseract absent'));
    s.onerror = () => ko(new Error('chargement refusé'));
    document.head.appendChild(s);
  }).catch(e => { _obTesseract = null; throw _obHorsLigne('Tesseract (OCR)', e); });
  return _obTesseract;
}

// ── Image JPEG d'un PDF, sans bibliothèque ──────────────────────
// Parcourt les objets du fichier : chaque flux d'image (/Subtype /Image)
// au seul filtre /DCTDecode est un JPEG complet. On garde la plus grande.
// null si aucune (ou PDF chiffré : les flux le sont aussi).
function obExtraireJpeg(octets){
  const txt = new TextDecoder('latin1').decode(octets);   // un caractère par octet
  if(/\/Encrypt\s/.test(txt)) return null;
  const images = [];
  const re = /\bstream(\r\n|\n|\r)/g;
  let m;
  while((m = re.exec(txt))){
    const debutObj = txt.lastIndexOf(' obj', m.index);
    if(debutObj < 0) continue;
    const dico = txt.substring(debutObj, m.index);
    if(!/\/Subtype\s*\/Image\b/.test(dico)) continue;
    const filtre = dico.match(/\/Filter\s*(\[[^\]]*\]|\/\w+)/);
    if(!filtre || filtre[1].replace(/[\[\]\s]/g, '') !== '/DCTDecode') continue;
    const larg = +(dico.match(/\/Width\s+(\d+)/) || [])[1], haut = +(dico.match(/\/Height\s+(\d+)/) || [])[1];
    if(!larg || !haut) continue;
    const debut = m.index + m[0].length;
    let long = NaN;
    const l = dico.match(/\/Length\s+(\d+)(?:\s+(\d+)\s+R)?/);
    if(l && l[2] === undefined) long = +l[1];
    else if(l){
      const ref = txt.match(new RegExp(`(?:^|[\\r\\n\\s])${l[1]}\\s+${l[2]}\\s+obj\\s*(\\d+)`));
      if(ref) long = +ref[1];
    }
    let fin = debut + long;
    if(!(long > 0) || !/^\s*endstream/.test(txt.substr(fin, 30))){
      fin = txt.indexOf('endstream', debut);          // longueur absente ou fausse
      if(fin < 0) continue;
      while(fin > debut && /[\r\n]/.test(txt[fin - 1])) fin--;
    }
    const jpeg = octets.subarray(debut, fin);
    if(jpeg[0] !== 0xFF || jpeg[1] !== 0xD8) continue;
    images.push({jpeg, largeur: larg, hauteur: haut});
  }
  if(!images.length) return null;
  images.sort((a, b) => b.largeur * b.hauteur - a.largeur * a.hauteur);
  return Object.assign(images[0], {nbImages: images.length});
}

// JPEG → pixels RVBA, décodés par le navigateur sans conversion de couleur
// (comme cv2.imdecode).
async function obDecoderJpeg(jpeg){
  const bmp = await createImageBitmap(new Blob([jpeg], {type: 'image/jpeg'}),
    {colorSpaceConversion: 'none', premultiplyAlpha: 'none'});
  return _obPixels(bmp, bmp.width, bmp.height);
}
function _obPixels(source, w, h){
  const cv = typeof OffscreenCanvas !== 'undefined' ? new OffscreenCanvas(w, h)
    : Object.assign(document.createElement('canvas'), {width: w, height: h});
  const cx = cv.getContext('2d', {willReadFrequently: true});
  cx.drawImage(source, 0, 0);
  const id = cx.getImageData(0, 0, w, h);
  if(source.close) source.close();
  return {w, h, d: id.data};
}

// ── Page 1 par pdf.js ───────────────────────────────────────────
async function obRendrePdf(octets){
  const pdfjs = await obChargerPdfjs();
  let doc;
  try { doc = await pdfjs.getDocument({data: octets.slice(), isEvalSupported: false}).promise; }
  catch(e){ throw new Error('PDF illisible' + (e && e.message ? ` (${e.message})` : '')); }
  const page = await doc.getPage(1);
  const v1 = page.getViewport({scale: 1});
  // largeur de la plus grande image de la page, si pdf.js la donne
  let largeurImage = 0;
  try {
    const ops = await page.getOperatorList();
    for(let i = 0; i < ops.fnArray.length; i++){
      if(ops.fnArray[i] !== pdfjs.OPS.paintImageXObject) continue;
      const nom = ops.argsArray[i][0];
      const img = page.objs.has(nom) ? page.objs.get(nom) : (page.commonObjs.has(nom) ? page.commonObjs.get(nom) : null);
      if(img && img.width > largeurImage) largeurImage = img.width;
    }
  } catch(e){}
  const echelle = Math.max(largeurImage, OB_LARGEUR_MIN) / v1.width;
  const vp = page.getViewport({scale: echelle});
  const w = Math.round(vp.width), h = Math.round(vp.height);
  const cv = Object.assign(document.createElement('canvas'), {width: w, height: h});
  const cx = cv.getContext('2d', {willReadFrequently: true});
  cx.fillStyle = '#fff'; cx.fillRect(0, 0, w, h);
  await page.render({canvasContext: cx, viewport: vp}).promise;
  let texte = '';
  try { texte = (await page.getTextContent()).items.map(t => t.str).join(' ').trim(); } catch(e){}
  const pixels = {w, h, d: cx.getImageData(0, 0, w, h).data};
  doc.destroy();
  return {pixels, texte, largeurImage};
}

// ── Image de la fiche ───────────────────────────────────────────
// fichier : File / Blob / ArrayBuffer / Uint8Array.
// → {rgba, gris, source ('jpeg' | 'pdf.js'), nbImages, texte, ms}
async function obImageDuPdf(fichier){
  const t0 = performance.now();
  const octets = fichier instanceof Uint8Array ? fichier
    : new Uint8Array(fichier instanceof ArrayBuffer ? fichier : await fichier.arrayBuffer());
  if(new TextDecoder('latin1').decode(octets.subarray(0, 1024)).indexOf('%PDF') < 0)
    throw new Error('Ce fichier n’est pas un PDF');
  let rgba = null, source = '', nbImages = 0, texte = '';
  const j = obExtraireJpeg(octets);
  if(j){
    try { rgba = await obDecoderJpeg(j.jpeg); source = 'jpeg'; nbImages = j.nbImages; }
    catch(e){ rgba = null; }
  }
  if(!rgba){
    const r = await obRendrePdf(octets);
    rgba = r.pixels; source = 'pdf.js'; texte = r.texte;
  }
  return {rgba, gris: obImage.gris(rgba), source, nbImages, texte, ms: Math.round(performance.now() - t0)};
}
