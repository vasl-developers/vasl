// ═══ OUTILS COMMUNS ═══
// Déclarations appelées au chargement par un fichier qui précède
// celui de leur section (pas de hissage d'un script à l'autre).
// Leur place d'origine porte un renvoi « … : js/outils.js ».

// (_lireLocal n'est pas encore défini à ce point du script.)
function _lirePref(c){ try { return localStorage.getItem(c); } catch(e){ return null; } }

function _touche(code, mods){
  const c = (mods & (2 | 128)) ? 1 : 0, sh = (mods & (1 | 64)) ? 1 : 0;
  const a = (mods & (8 | 512)) ? 1 : 0, m = (mods & (4 | 256)) ? 1 : 0;
  return code + ':' + c + sh + a + m;
}
