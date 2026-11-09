// ═══ ZIP natif (DecompressionStream) ═══
async function _zipRead(arrayBuffer){
  const buf = new Uint8Array(arrayBuffer);
  const dv  = new DataView(arrayBuffer);
  const files = new Map();
  let eocd = -1;
  for(let i = buf.length - 22; i >= 0 && i >= buf.length - 65558; i--){
    if(dv.getUint32(i, true) === 0x06054b50){ eocd = i; break; }
  }
  if(eocd < 0) throw new Error('Pas un fichier ZIP valide (EOCD introuvable)');
  const entryCount = dv.getUint16(eocd + 10, true);
  let cdOffset     = dv.getUint32(eocd + 16, true);
  for(let n = 0; n < entryCount; n++){
    if(dv.getUint32(cdOffset, true) !== 0x02014b50) break;
    const method    = dv.getUint16(cdOffset + 10, true);
    const compSize  = dv.getUint32(cdOffset + 20, true);
    const nameLen   = dv.getUint16(cdOffset + 28, true);
    const extraLen  = dv.getUint16(cdOffset + 30, true);
    const cmtLen    = dv.getUint16(cdOffset + 32, true);
    const localOff  = dv.getUint32(cdOffset + 42, true);
    const name      = new TextDecoder().decode(buf.subarray(cdOffset + 46, cdOffset + 46 + nameLen));
    if(dv.getUint32(localOff, true) === 0x04034b50){
      const lNameLen  = dv.getUint16(localOff + 26, true);
      const lExtraLen = dv.getUint16(localOff + 28, true);
      const dataStart = localOff + 30 + lNameLen + lExtraLen;
      const raw = buf.subarray(dataStart, dataStart + compSize);
      let out;
      if(method === 0) out = new Uint8Array(raw);
      else if(method === 8) out = await _inflateRaw(raw);
      else throw new Error('Méthode de compression ZIP non supportée : ' + method);
      files.set(name, out);
    }
    cdOffset += 46 + nameLen + extraLen + cmtLen;
  }
  return files;
}

async function _inflateRaw(bytes){
  if(typeof DecompressionStream === 'undefined')
    throw new Error('DecompressionStream indisponible — navigateur trop ancien');
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

// ── Décompression XZ (VASSAL ≥ 3.8 : savedGame = flux XZ, filtre LZMA2)
// Décodeur minimal : un ou plusieurs flux, blocs LZMA2 sans filtre BCJ ni
// delta ; les sommes de contrôle ne sont pas vérifiées.
function _estXZ(b){
  return b.length >= 6 && b[0] === 0xFD && b[1] === 0x37 && b[2] === 0x7A
    && b[3] === 0x58 && b[4] === 0x5A && b[5] === 0x00;
}
function _xzDecode(inp){
  let ip = 0;
  let out = new Uint8Array(Math.max(1024, inp.length * 8)), op = 0;
  const reserver = n => {
    if(op + n <= out.length) return;
    let t = out.length * 2; while(t < op + n) t *= 2;
    const o = new Uint8Array(t); o.set(out.subarray(0, op)); out = o;
  };
  const err = m => { throw new Error('XZ : ' + m); };
  const octet = () => { if(ip >= inp.length) err('données tronquées'); return inp[ip++]; };
  const varint = () => {
    let v = 0, m = 1, b;
    do { b = octet(); v += (b & 0x7F) * m; m *= 128; } while(b & 0x80);
    return v;
  };
  const aligner = () => { while(ip & 3) ip++; };

  // ── Décodeur LZMA (état conservé d'un bloc LZMA2 à l'autre)
  let rng = 0, code = 0;
  let lc = 0, lp = 0, pb = 0, lit = null;
  let state = 0, rep0 = 0, rep1 = 0, rep2 = 0, rep3 = 0, dictDebut = 0;
  let isMatch, isRep, isRepG0, isRepG1, isRepG2, isRep0Long, posSlot, posDec, align, lenD, repLenD;
  const nouvLen = () => ({choix: new Uint16Array(2), bas: new Uint16Array(16 << 3),
    mil: new Uint16Array(16 << 3), haut: new Uint16Array(256)});
  const remplir = a => a.fill(1024);
  const resetEtat = () => {
    lit = new Uint16Array(0x300 << (lc + lp));
    isMatch = new Uint16Array(12 << 4); isRep = new Uint16Array(12);
    isRepG0 = new Uint16Array(12); isRepG1 = new Uint16Array(12); isRepG2 = new Uint16Array(12);
    isRep0Long = new Uint16Array(12 << 4); posSlot = new Uint16Array(4 << 6);
    posDec = new Uint16Array(115); align = new Uint16Array(16);
    lenD = nouvLen(); repLenD = nouvLen();
    for(const a of [lit, isMatch, isRep, isRepG0, isRepG1, isRepG2, isRep0Long, posSlot, posDec, align,
      lenD.choix, lenD.bas, lenD.mil, lenD.haut, repLenD.choix, repLenD.bas, repLenD.mil, repLenD.haut]) remplir(a);
    state = 0; rep0 = rep1 = rep2 = rep3 = 0;
  };
  let fin = 0; // fin des données comprimées du bloc LZMA courant
  const bit = (p, i) => {
    const v = p[i], borne = (rng >>> 11) * v;
    let b;
    if(code < borne){ rng = borne; p[i] = v + ((2048 - v) >>> 5); b = 0; }
    else { rng -= borne; code -= borne; p[i] = v - (v >>> 5); b = 1; }
    if(rng < 0x1000000){ rng = (rng * 256) >>> 0; code = ((code * 256) + octet()) >>> 0; }
    return b;
  };
  const directs = n => {
    let r = 0;
    while(n--){
      rng >>>= 1;
      let b = 0;
      if(code >= rng){ code -= rng; b = 1; }
      r = (r * 2 + b) >>> 0;
      if(rng < 0x1000000){ rng = (rng * 256) >>> 0; code = ((code * 256) + octet()) >>> 0; }
    }
    return r;
  };
  const arbre = (p, base, n) => {
    let m = 1;
    for(let i = 0; i < n; i++) m = (m << 1) | bit(p, base + m);
    return m - (1 << n);
  };
  const arbreInv = (p, base, n) => {
    let m = 1, s = 0;
    for(let i = 0; i < n; i++){ const b = bit(p, base + m); m = (m << 1) | b; s |= b << i; }
    return s;
  };
  const longueur = (L, ps) => {
    if(!bit(L.choix, 0)) return arbre(L.bas, ps << 3, 3);
    if(!bit(L.choix, 1)) return 8 + arbre(L.mil, ps << 3, 3);
    return 16 + arbre(L.haut, 0, 8);
  };
  const lzma = taille => {
    rng = 0xFFFFFFFF; code = 0;
    if(octet() !== 0) err('codeur d’intervalle invalide');
    for(let i = 0; i < 4; i++) code = ((code * 256) + octet()) >>> 0;
    reserver(taille);
    const limite = op + taille, pbM = (1 << pb) - 1, lpM = (1 << lp) - 1;
    while(op < limite){
      const pos = op - dictDebut, ps = pos & pbM;
      if(!bit(isMatch, (state << 4) + ps)){
        const prec = pos > 0 ? out[op - 1] : 0;
        const base = 0x300 * (((pos & lpM) << lc) + (prec >> (8 - lc)));
        let s = 1;
        if(state >= 7){
          if(rep0 >= pos) err('distance invalide');
          let mb = out[op - rep0 - 1];
          do {
            const mbit = (mb >> 7) & 1; mb <<= 1;
            const b = bit(lit, base + ((1 + mbit) << 8) + s);
            s = (s << 1) | b;
            if(mbit !== b) break;
          } while(s < 0x100);
        }
        while(s < 0x100) s = (s << 1) | bit(lit, base + s);
        out[op++] = s & 0xFF;
        state = state < 4 ? 0 : state < 10 ? state - 3 : state - 6;
        continue;
      }
      let len;
      if(bit(isRep, state)){
        if(pos === 0) err('répétition en tête de dictionnaire');
        if(!bit(isRepG0, state)){
          if(!bit(isRep0Long, (state << 4) + ps)){
            state = state < 7 ? 9 : 11;
            out[op] = out[op - rep0 - 1]; op++;
            continue;
          }
        } else {
          let d;
          if(!bit(isRepG1, state)) d = rep1;
          else {
            if(!bit(isRepG2, state)) d = rep2;
            else { d = rep3; rep3 = rep2; }
            rep2 = rep1;
          }
          rep1 = rep0; rep0 = d;
        }
        len = longueur(repLenD, ps);
        state = state < 7 ? 8 : 11;
      } else {
        rep3 = rep2; rep2 = rep1; rep1 = rep0;
        len = longueur(lenD, ps);
        state = state < 7 ? 7 : 10;
        const slot = arbre(posSlot, (len < 3 ? len : 3) << 6, 6);
        if(slot < 4) rep0 = slot;
        else {
          const nd = (slot >> 1) - 1;
          let d = ((2 | (slot & 1)) * Math.pow(2, nd)) >>> 0;
          if(slot < 14) d = (d + arbreInv(posDec, d - slot, nd)) >>> 0;
          else d = (d + directs(nd - 4) * 16 + arbreInv(align, 0, 4)) >>> 0;
          rep0 = d;
        }
        if(rep0 === 0xFFFFFFFF) err('marqueur de fin inattendu');
      }
      len += 2;
      if(rep0 >= op - dictDebut) err('distance invalide');
      if(op + len > limite) err('correspondance hors bloc');
      for(let i = 0; i < len; i++, op++) out[op] = out[op - rep0 - 1];
    }
    if(ip > fin) err('bloc LZMA trop long');
    ip = fin;
  };

  const lzma2 = () => {
    let propsOk = false;
    for(;;){
      const c = octet();
      if(c === 0) return;
      if(c === 1 || c === 2){
        if(c === 1) dictDebut = op;
        const n = ((octet() << 8) | octet()) + 1;
        if(ip + n > inp.length) err('données tronquées');
        reserver(n); out.set(inp.subarray(ip, ip + n), op); op += n; ip += n;
        continue;
      }
      if(c < 0x80) err('commande LZMA2 invalide');
      const taille = ((c & 0x1F) << 16) + ((octet() << 8) | octet()) + 1;
      const comp = ((octet() << 8) | octet()) + 1;
      const reset = (c >> 5) & 3;
      if(reset === 3) dictDebut = op;
      if(reset >= 2){
        let d = octet();
        if(d >= 225) err('propriétés LZMA invalides');
        lc = d % 9; d = (d / 9) | 0; lp = d % 5; pb = (d / 5) | 0;
        if(lc + lp > 4) err('propriétés LZMA invalides');
        propsOk = true;
      }
      if(!propsOk) err('propriétés LZMA absentes');
      if(reset >= 1) resetEtat();
      fin = ip + comp;
      lzma(taille);
    }
  };

  const tailleCheck = t => t === 0 ? 0 : 4 << ((t - 1) / 3 | 0);
  while(ip < inp.length){
    // Remplissage entre flux
    if(inp[ip] === 0 && ip + 4 <= inp.length && !inp[ip+1] && !inp[ip+2] && !inp[ip+3]){ ip += 4; continue; }
    if(!_estXZ(inp.subarray(ip))) err('en-tête de flux invalide');
    const check = inp[ip + 7] & 0x0F;
    ip += 12;
    for(;;){
      const h = octet();
      if(h === 0){
        // Index : nombre d'enregistrements, puis (taille non comprimée, taille) par bloc
        const n = varint();
        for(let i = 0; i < n; i++){ varint(); varint(); }
        aligner(); ip += 4;   // CRC32 de l'index
        ip += 12;             // pied de flux
        break;
      }
      const debut = ip - 1, finEntete = debut + (h + 1) * 4;
      const flags = octet();
      const nf = (flags & 3) + 1;
      if(flags & 0x40) varint();
      if(flags & 0x80) varint();
      for(let i = 0; i < nf; i++){
        const id = varint(), np = varint();
        if(id !== 0x21 || i !== nf - 1)
          err('filtre non supporté (0x' + id.toString(16) + ')');
        ip += np;
      }
      ip = finEntete;
      lzma2();
      aligner();
      ip += tailleCheck(check);
    }
  }
  return out.slice(0, op);
}

// ── XOR decode VASSAL (!VCSK + 2 chars hex key + payload hexadécimal)
function _xorDecode(bytes){
  // VASSAL 3.8 : savedGame comprimé en XZ, texte en clair une fois décomprimé
  if(_estXZ(bytes)) bytes = _xzDecode(bytes);
  const magic = [0x21,0x56,0x43,0x53,0x4B];
  let start = -1;
  for(let i = 0; i < bytes.length-7; i++){
    if(bytes[i]===magic[0]&&bytes[i+1]===magic[1]&&bytes[i+2]===magic[2]
     &&bytes[i+3]===magic[3]&&bytes[i+4]===magic[4]){ start = i; break; }
  }
  if(start < 0) return new TextDecoder().decode(bytes);
  const key = parseInt(String.fromCharCode(bytes[start+5], bytes[start+6]), 16);
  if(isNaN(key)) throw new Error('Clé XOR illisible');
  const hexPart = bytes.subarray(start+7);
  const n = hexPart.length >> 1;
  const out = new Uint8Array(n);
  const hv = c => c<=57 ? c-48 : (c<=70 ? c-55 : c-87);
  for(let i = 0; i < n; i++) out[i] = ((hv(hexPart[i*2])<<4) | hv(hexPart[i*2+1])) ^ key;
  return new TextDecoder('utf-8', {fatal:false}).decode(out);
}

// ═══ OUVERTURE DE FICHIER ═══
// On identifie le format par sa signature : sur Android le nom de
// fichier perd souvent son extension.
function _sniffFileType(b, name){
  if(b.length >= 4 && b[0] === 0x50 && b[1] === 0x4B
     && (b[2] === 3 || b[2] === 5 || b[2] === 7)) return 'vsav';
  if(b.length >= 5 && b[0] === 0x21 && b[1] === 0x56
     && b[2] === 0x43 && b[3] === 0x53 && b[4] === 0x4B) return 'raw';
  if(/\.(vsav|vlog|zip)$/i.test(name || '')) return 'vsav';
  return 'unknown';
}

async function openGameFile(file){
  if(!file) return;
  const label = file.name || 'fichier';
  // isNewGameAllowed : dans une salle, seul le propriétaire ouvre une partie.
  if(!netProprietaire()){
    chatLog('🔒 Dans une salle, seul son propriétaire ouvre une partie : utiliser Synchroniser');
    return;
  }
  try {
    const head = new Uint8Array(await file.slice(0, 64).arrayBuffer());
    const kind = _sniffFileType(head, file.name);
    let texte;
    // savedata et moduledata : repris tels quels à l'écriture, VASSAL y
    // vérifie la version du module et les extensions chargées.
    const annexes = new Map();
    if(kind === 'vsav'){
      const files = await _zipRead(await file.arrayBuffer());
      let raw = null, nomRaw = null;
      for(const [n, d] of files)
        if(n === 'savedGame' || n.toLowerCase().endsWith('savedgame')){ raw = d; nomRaw = n; break; }
      if(!raw){ chatLog(`❌ Pas d'entrée savedGame dans ${label}`); return; }
      for(const [n, d] of files) if(n !== nomRaw) annexes.set(n, d);
      texte = _xorDecode(raw);
    } else if(kind === 'raw'){
      texte = _xorDecode(new Uint8Array(await file.arrayBuffer()));
    } else {
      chatLog(`❌ Format non reconnu : ${label}`);
      return;
    }
    if(!await chargerPartie(texte, label, annexes)) return;
    netPartieChargee();
    // Qui joue ? Ce que l'on voit et ce que l'on peut toucher en dépend.
    demanderUtilisateur();
    // GameState.loadGame : « Replay commencing. Begin new log file? »
    proposerLog(true);
  } catch(err){
    chatLog('❌ Lecture impossible : ' + (err && err.message ? err.message : err));
    console.error(err);
  }
}

// Texte d'une sauvegarde : boards, puis pions. Sert aussi à la
// synchronisation d'une partie à distance.
async function chargerPartie(texte, label, annexes){
  const boards = _parseBoardSegment(texte);
  if(!boards.length){ chatLog(`⚠️ Aucun board dans ${label}`); return false; }
  fermerLog();
  document.title = 'VASLweb 1.01 — ' + label;
  chatLog(`🗺️ ${label} — boards : `
    + boards.map(b => b.name + (b.reversed ? ' (retourné)' : '')).join(', '));
  await _renderBoards(boards);
  importerPions(texte, label, annexes);
  poserTables();
  _partieModifiee = false;
  return true;
}

const EXT_ACCEPTEES = ['.vsav', '.vlog'];
function doOpen(){
  const el = document.getElementById('fileImport');
  el.value = '';
  // Sur Android, « accept » grise les .vsav : on s'en abstient.
  if(!/Android/i.test(navigator.userAgent || '')) el.setAttribute('accept', EXT_ACCEPTEES.join(','));
  el.click();
}
function handleFileImport(input){
  const f = input.files && input.files[0];
  if(f) openGameFile(f);
}

function _setupDropZone(){
  const zone = document.getElementById('mapWrap');
  const stop = e => { e.preventDefault(); e.stopPropagation(); };
  let depth = 0;
  zone.addEventListener('dragenter', e => { stop(e); if(++depth === 1) zone.classList.add('dropping'); });
  zone.addEventListener('dragover', stop);
  zone.addEventListener('dragleave', e => {
    stop(e); if(--depth <= 0){ depth = 0; zone.classList.remove('dropping'); }
  });
  zone.addEventListener('drop', e => {
    stop(e); depth = 0; zone.classList.remove('dropping');
    const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if(f) openGameFile(f);
  });
  document.addEventListener('paste', e => {
    const items = e.clipboardData && e.clipboardData.items;
    if(!items) return;
    for(const it of items) if(it.kind === 'file'){
      const f = it.getAsFile();
      if(f){ e.preventDefault(); openGameFile(f); return; }
    }
  });
}

