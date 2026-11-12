// ═══════════════════════════════════════════════════════════════
// APPLICATION INSTALLABLE (PWA)
// Une seule ligne à garder dans VASLweb.html, dans <head> :
//   <script src="pwa.js" defer></script>
// Servie en HTTPS (GitHub Pages), la page s'installe comme une
// application : sw.js la garde hors ligne avec les boards déjà vus.
// Ouverte en file://, rien de tout cela : la page marche comme avant.
// ═══════════════════════════════════════════════════════════════
(function(){
  if(location.protocol === 'file:') return;
  // Version de cette page : le « ?v=<commit> » que la publication ajoute à
  // pwa.js (absent hors publication : pas de bandeau de mise à jour).
  let versionPage = null;
  try { versionPage = new URL(document.currentScript.src).searchParams.get('v'); } catch(e){}

  // Manifeste, icônes et réglages iOS, ajoutés à <head>.
  const ajouter = (balise, attrs) => {
    const el = document.createElement(balise);
    for(const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
    document.head.appendChild(el);
  };
  ajouter('link', {rel: 'manifest', href: 'manifest.webmanifest'});
  ajouter('link', {rel: 'icon', type: 'image/png', href: 'icons/favicon-32.png'});
  ajouter('link', {rel: 'apple-touch-icon', href: 'icons/apple-touch-icon.png'});
  ajouter('meta', {name: 'mobile-web-app-capable', content: 'yes'});
  ajouter('meta', {name: 'apple-mobile-web-app-capable', content: 'yes'});
  ajouter('meta', {name: 'apple-mobile-web-app-title', content: 'VASLweb 1.03'});

  if('serviceWorker' in navigator){
    navigator.serviceWorker.register('sw.js').catch(err => console.warn('Service worker :', err));
    surveillerVersion();
  }

  // Installée, elle demande que ses données (localStorage, cache des
  // boards) ne soient pas effacées quand le disque manque de place.
  if(navigator.storage && navigator.storage.persist && matchMedia('(display-mode: standalone)').matches)
    navigator.storage.persist().catch(() => {});

  // Mise à jour : une application installée revenue au premier plan (sur
  // Android, toucher l'icône reprend souvent la page déjà chargée, sans la
  // redemander au serveur) demande au service worker de se revérifier. Une
  // version neuve s'installe et s'active d'elle-même (skipWaiting, claim)
  // puis annonce sa VERSION (message { vaslweb: VERSION }) : si ce n'est
  // pas celle de la page, un bandeau propose de recharger. Jamais de
  // rechargement imposé, qui couperait une partie en cours.
  function surveillerVersion(){
    navigator.serviceWorker.addEventListener('message', e => {
      const v = e.data && e.data.vaslweb;
      if(versionPage && v && v !== versionPage) bandeauMiseAJour();
    });
    const verifier = () => navigator.serviceWorker.getRegistration()
      .then(r => r && r.update()).catch(() => {});
    document.addEventListener('visibilitychange', () => { if(document.visibilityState === 'visible') verifier(); });
    window.addEventListener('online', verifier);
  }
  function bandeauMiseAJour(){
    if(document.getElementById('majVaslweb') || !document.body) return;
    const b = document.createElement('div');
    b.id = 'majVaslweb';
    b.setAttribute('role', 'status');
    b.style.cssText = 'position:fixed;left:50%;bottom:16px;transform:translateX(-50%);z-index:10000;'
      + 'display:flex;align-items:center;gap:10px;max-width:calc(100% - 32px);box-sizing:border-box;'
      + 'padding:8px 8px 8px 14px;border-radius:8px;background:#2b2b2b;color:#e6e6e6;'
      + 'border:1px solid #d9a441;box-shadow:0 2px 10px rgba(0,0,0,.5);font:14px system-ui,sans-serif';
    const texte = document.createElement('span');
    texte.textContent = 'Nouvelle version de VASLweb disponible.';
    const bouton = (libelle, titre, style, action) => {
      const el = document.createElement('button');
      el.type = 'button'; el.textContent = libelle;
      if(titre) el.title = titre;
      el.style.cssText = 'min-height:36px;font-size:16px;border-radius:6px;cursor:pointer;' + style;
      el.addEventListener('click', action);
      return el;
    };
    b.append(texte,
      bouton('Recharger', '', 'padding:4px 12px;border:0;background:#d9a441;color:#1e1e1e', () => location.reload()),
      bouton('×', 'Fermer', 'padding:0 10px;border:0;background:none;color:#e6e6e6', () => b.remove()));
    document.body.appendChild(b);
  }

  // Double-clic sur un .vsav ou un .vlog : l'application l'ouvre, une
  // fois la page prête (après l'initialisation de VASLweb.html).
  // pwa.js, chargé en defer dans <head>, passe avant les scripts defer de
  // l'application (js/…) : openGameFile n'y est connue qu'à
  // DOMContentLoaded, quand tous les scripts defer ont tourné.
  const brancherFichiers = () => {
    if(!('launchQueue' in window) || typeof openGameFile !== 'function') return;
    window.launchQueue.setConsumer(async p => {
      if(p.files && p.files.length) openGameFile(await p.files[0].getFile());
    });
  };
  if(typeof openGameFile === 'function') brancherFichiers();
  else document.addEventListener('DOMContentLoaded', brancherFichiers);
})();
