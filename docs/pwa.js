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
  ajouter('meta', {name: 'apple-mobile-web-app-title', content: 'ASLwebtest'});

  if('serviceWorker' in navigator)
    navigator.serviceWorker.register('sw.js').catch(err => console.warn('Service worker :', err));

  // Installée, elle demande que ses données (localStorage, cache des
  // boards) ne soient pas effacées quand le disque manque de place.
  if(navigator.storage && navigator.storage.persist && matchMedia('(display-mode: standalone)').matches)
    navigator.storage.persist().catch(() => {});

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
