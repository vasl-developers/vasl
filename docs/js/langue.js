// ═══════════════════════════════════════════════════════════════
// LANGUE DE L'INTERFACE
// La langue du terminal décide : français s'il l'est (fr, fr-FR, fr-CA…),
// l'interface reste telle quelle ; sinon, anglais, avec les termes de
// VASSAL et de VASL (counter, stack, Undo, Logfile, Room…). « ?lang=fr »
// ou « ?lang=en » dans l'adresse l'impose. Seule l'interface se traduit :
// le journal, les rapports au chat, le log et les fichiers restent en
// l'état, tels que les reçoivent VASL et les autres joueurs.
// Le code garde ses libellés français ; T() les traduit à l'affichage,
// et un MutationObserver traduit ce qui paraît dans la page (texte,
// title, placeholder, aria-label), hors carte et journal.
// ═══════════════════════════════════════════════════════════════
const LANGUE = (() => {
  const q = /[?&]lang=([a-z]{2})/i.exec(location.search);
  const l = q ? q[1] : (navigator.languages && navigator.languages[0]) || navigator.language || 'fr';
  return /^fr/i.test(l) ? 'fr' : 'en';
})();

// Libellés exacts (espaces regroupés).
const EN = {
  // Page et carte
  'Ouvrir un fichier .vsav ou .vlog, ou le déposer ici,': 'Open a .vsav or .vlog file, or drop it here,',
  'pour afficher la carte du scénario.': 'to display the scenario map.',
  'Nouvelle version de VASLweb disponible.': 'New VASLweb version available.', 'Recharger': 'Reload',
  'Glisser pour régler la largeur du panneau': 'Drag to resize the panel',
  'Glisser pour régler la hauteur du journal': 'Drag to resize the chat log',
  'Glisser pour déplacer la fenêtre (double clic : la remettre à sa place)': 'Drag to move the window (double-click: put it back)',
  'Pion dissimulé': 'Concealed counter',
  // Panneau : Pile, Palette, Dés
  'Couches que le clic peut sélectionner ; les autres laissent passer le clic':
    'Layers a click can select; the others let the click through',
  'Sélection :': 'Selection:',
  'Unités': 'Units', 'Marqueurs': 'Markers', 'Cartes et panneaux': 'Maps and panels',
  'Tables QRDC sous la carte': 'QRDC charts below the map', 'Tables QRDC (fenêtre)': 'QRDC charts (window)',
  'Tables QRDC indisponibles.': 'QRDC charts unavailable.',
  'OBA — pioches de cartes (onglet des tables QRDC)': 'OBA — draw piles (QRDC charts tab)',
  'Aucun module OBA.': 'No OBA module.', '+ Module OBA': '+ OBA module',
  'Ajouter une pioche OBA (8 noires, 3 rouges)': 'Add an OBA draw pile (8 black, 3 red)',
  'Nom du module': 'Module name', 'Retirer ce module': 'Remove this module',
  'Retirer ce module OBA ?': 'Remove this OBA module?', 'Module d\'un autre joueur': 'Another player\'s module',
  'Noire': 'Black', 'Rouge': 'Red',
  'Une carte noire de moins': 'One less black card', 'Une carte noire de plus': 'One more black card',
  'Une carte rouge de moins': 'One less red card', 'Une carte rouge de plus': 'One more red card',
  'Tirer': 'Draw', 'Tirer une carte': 'Draw a card', 'Pioche': 'Draw pile', 'Pioche vide': 'Empty draw pile',
  'Noire — remettre dans la pioche': 'Black — shuffle back into the draw pile',
  'Rouge — remettre dans la pioche': 'Red — shuffle back into the draw pile',
  'Rechercher un pion…': 'Search counters…', 'Rechercher un pion': 'Search counters',
  'Piège Axis': 'Axis Booby Trap', 'Piège Allied': 'Allied Booby Trap',
  'Pile': 'Stack', 'Palette': 'Counters', 'Dés': 'Dice',
  'Fermer le panneau': 'Close panel', 'Fermer': 'Close',
  '▲ Sommet': '▲ Top', '▼ Fond': '▼ Bottom',
  'Commandes du pion (clic droit)': 'Counter commands (right-click)',
  'Monter': 'Move up', 'Descendre': 'Move down',
  'Glisser pour déplacer le pion dans la pile — clic : commandes du pion': 'Drag to move the counter within the stack — click: counter commands',
  'Tout en haut (sommet de la pile)': 'To the top of the stack', 'Tout en bas (fond de la pile)': 'To the bottom of the stack',
  'Retirer de la carte (la sélection, si ce pion en fait partie)': 'Delete from map (the selection, if this counter is in it)',
  'Aucune commande — pion masqué par un autre joueur': 'No commands — counter hidden by another player',
  'Aucune commande': 'No commands',
  'Chargement de la base VASL…': 'Loading VASL counters…',
  'Base indisponible.': 'Counter database unavailable.',
  'Lâché hors de la carte : rien posé': 'Dropped off the map: nothing placed',
  // Barres de commandes, éventail, poses
  '⋮ Toutes': '⋮ All', 'Toutes les commandes': 'All commands',
  'Commandes rapides': 'Quick commands',
  'appuyer de nouveau pour l\'enlever': 'press again to remove it',
  'Déplacer…': 'Move…', 'Déplacer : toucher ensuite la destination': 'Move: then tap the destination',
  'Retirer de la carte': 'Delete from map',
  'Pions de la pile': 'Counters in stack',
  'Tout': 'All', 'Sélectionner toute la pile': 'Select the whole stack',
  'Tourner l\'éventail de 90°': 'Rotate the stack view 90°',
  'Flare : montrer la pile à l\'adversaire': 'Flare: show the stack to your opponent',
  'Flare (montrer un point à l\'adversaire)': 'Flare (show a point to your opponent)',
  'Alt+clic · double appui dans le vide': 'Alt+click · double tap on empty map',
  'Aucun': 'None', 'Désélectionner tous les pions de la pile': 'Deselect all counters of the stack',
  'Retirer de la sélection': 'Remove from selection', 'Ajouter à la sélection': 'Add to selection',
  'poses multiples': 'multiple placements', 'Annuler': 'Cancel',
  // Ligne de vue
  'Ligne de vue': 'Line of sight', 'Cible': 'Target',
  'Monter le niveau de la source': 'Raise source level', 'Descendre le niveau de la source': 'Lower source level',
  'Monter le niveau de la cible': 'Raise target level', 'Descendre le niveau de la cible': 'Lower target level',
  'Check : résultat de la ligne de vue, et rapport « Checked LOS » au chat': 'Check: LOS result, and “Checked LOS” report to the chat',
  'Vérification impossible : overlay sur la ligne de vue': 'LOS check disabled: overlay on the line of sight',
  'Données LOS de VASL absentes : portée seule': 'No VASL LOS data: range only',
  'LOS bloquée': 'LOS blocked', 'LOS bloquée en': 'LOS blocked in', 'LOS dégagée': 'LOS clear', 'LOS dégagée, avec gêne': 'LOS clear, with hindrance',
  'hindrance': 'hindrance', 'niveau': 'level',
  'Vert : From ; or : To': 'Green: From; gold: To', 'ligne dégagée': 'clear line',
  'après la gêne': 'past the hindrance', 'après le blocage': 'past the block', 'croix : point de blocage': 'cross: blocking point',
  'Vérification de la LOS désactivée (préférences) : portée seule': 'LOS checking disabled (preferences): range only',
  'Tourner le panneau de 90°': 'Rotate the panel 90°',
  'From : toucher la carte pour choisir de nouveau l\'hexagone de la source': 'From: tap the map to choose the source hex again',
  'To : toucher la carte pour choisir de nouveau l\'hexagone de la cible': 'To: tap the map to choose the target hex again',
  'Annuler la ligne de vue, sans rapport': 'Cancel the LOS, without report',
  // Actions : menus, barres, feuilles
  'Fichier': 'File', 'Partie': 'Game', 'Affichage': 'View', 'Outils': 'Tools', 'Réseau': 'Server', 'Aide': 'Help',
  'Ouvrir .vsav / .vlog': 'Open Game (.vsav / .vlog)', 'Ouvrir': 'Open',
  'Exporter .vsav': 'Save Game (.vsav)', 'Exporter': 'Save',
  'Commencer un fichier de log': 'Begin Logfile…', 'Log': 'Log', '⏹ Log': '⏹ Log',
  'Annuler le dernier coup (Undo)': 'Undo last move', 'Undo': 'Undo',
  'Mark unmoved — lever le statut Moved de tous les pions': 'Mark all counters unmoved', 'Unmoved': 'Unmoved',
  'Remove all — retirer tous les pions d\'un type…': 'Remove all counters of a given type…',
  'Entourer les unités brisées et les armes MALF': 'Highlight broken units / malfunctioned weapons',
  'Entourer le sniper': 'Highlight sniper counters', 'Broken': 'Broken', 'Sniper': 'Sniper',
  'Masquer les pions (montrer carte et overlays)': 'Hide counters (show map and overlays)',
  'Montrer tous les pions': 'Show all counters', 'Pions': 'Counters',
  'LOS (ligne de vue)': 'LOS (line of sight)', 'LOS': 'LOS',
  'LOS inv (ligne de vue silencieuse)': 'LOS inv (silent line of sight)', 'LOS inv': 'LOS inv',
  'LOS détaillée (Verbose LOS mode)': 'Verbose LOS mode',
  // Préférences (⚙)
  'Préférences (LOS, décompte MF / MP)…': 'Preferences (LOS, MF / MP count)…', 'Préférences': 'Preferences',
  'Préférences de la LOS (couleurs)…': 'LOS preferences (colors)…',
  'Décompte MF / MP': 'MF / MP count',
  'Couleur du fil': 'Thread Color', 'Vérifier la ligne de vue': 'Enable LOS checking',
  'Couleur du fil après une gêne': 'Hindrance Thread Color', 'Couleur du fil après le blocage': 'Blocked Thread Color',
  'LOS détaillée': 'Verbose LOS mode', 'Opacité des pions pendant la visée': 'Unit opacity % during LOS checks',
  'Aperçu : dégagé, puis après la première gêne, puis après le blocage.': 'Preview: clear, then past the first hindrance, then past the block.',
  'Couleurs de VASL': 'VASL colors',
  'Afficher le décompte des MF (infanterie)': 'Show the MF count (infantry)',
  'Afficher le décompte des MP (véhicules)': 'Show the MP count (vehicles)',
  'Masqué, le décompte continue d\'être tenu, mais n\'est plus écrit au chat ni au log.':
    'When hidden, the count is still kept, but no longer written to the chat or the log.',
  'Zoom arrière': 'Zoom out', 'Zoom avant': 'Zoom in',
  'Ajuster la carte à la vue': 'Fit map to window', 'Ajuster': 'Fit',
  'Hors ligne': 'Offline', 'Connexion…': 'Connecting…', 'Observateur': 'Observer', 'observateur': 'observer',
  'Panneau Pile, Palette, Dés': 'Stack, Counters and Dice panel',
  'Éventail de la pile': 'Stack view', 'double-clic · double appui': 'double-click · double tap',
  'Journal et chat': 'Chat log', 'Journal': 'Chat',
  'SAN et pièges du scénario…': 'Scenario SAN and Booby Traps…', 'SAN': 'SAN',
  'Palette des pions': 'Counters palette', 'Pile sélectionnée': 'Selected stack',
  'Verrou des pions (un glissé déplace la carte)': 'Lock counters (dragging scrolls the map)', 'Verrou': 'Lock',
  'Afficher l\'effectif des piles (vue de loin)': 'Show stack size (zoomed out)',
  'Raccourcis clavier et gestes…': 'Keyboard shortcuts and gestures…',
  'Sélection : unités': 'Selection: units', 'Sélection : marqueurs': 'Selection: markers',
  'Sélection : cartes et panneaux': 'Selection: maps and panels',
  'Sélection : overlays': 'Selection: overlays',
  'Affichage : automatique': 'Display: automatic', 'Affichage : PC': 'Display: PC',
  'Affichage : tactile': 'Display: touch',
  'Début du log': 'Start of logfile', 'Fin du log': 'End of logfile',
  'Coup précédent': 'Step back', 'Coup suivant': 'Step forward',
  'Un coup a été joué ici pendant la relecture : Undo pour l\'annuler':
    'A move was made here during replay: Undo to cancel it',
  'Plus…': 'More…', 'Plus': 'More', 'Carte': 'Map',
  'Aperçu du chat sur la carte': 'Chat preview on the map',
  'Dés en 3D sur la carte': '3D dice on the map',
  'Dés : un appui DR, un appui long les catégories': 'Dice: tap for a DR, long press for categories',
  'SAN et pièges…': 'SAN and Booby Traps…', 'SAN et pièges': 'SAN and Booby Traps',
  'Pile et palette': 'Stack and counters', 'Vues': 'Views',
  'DR [F2] — appui long ou glissé vers le haut : toutes les catégories': 'DR [F2] — long press or swipe up: all categories',
  'Lancer un DR ; appui long : catégories de dés': 'Roll a DR; long press: dice categories',
  // Chat
  'Message au chat': 'Chat message', 'Message au chat (Entrée pour envoyer)': 'Chat message (Enter to send)',
  'Envoyer': 'Send', 'Envoyer au chat': 'Send to chat', 'Écrire au chat': 'Write to chat',
  'Placer le chat en haut de l\'écran': 'Move chat to the top of the screen',
  'Placer le chat en bas de l\'écran': 'Move chat to the bottom of the screen',
  'Messages': 'Messages', 'Rapports': 'Reports', 'Système': 'System',
  'Filtres du journal': 'Chat log filters', 'Saisie rapide': 'Quick chat',
  // Aide
  'Raccourcis clavier et gestes': 'Keyboard shortcuts and gestures',
  'Menus': 'Menus',
  'Pions et piles': 'Counters and stacks',
  'Commandes du pion (menu)': 'Counter commands (menu)', 'clic droit · double appui': 'right-click · double tap',
  'Sélectionner la pile': 'Select stack', 'clic': 'click',
  'Déplier / replier la pile': 'Expand / collapse stack', 'double-clic': 'double-click',
  'Ajouter ou retirer un pion': 'Add or remove a counter', 'Ctrl+clic': 'Ctrl+click', 'Maj+clic': 'Shift+click',
  'Rectangle de sélection': 'Selection box', 'Maj+glisser dans le vide': 'Shift+drag on empty map',
  'Mettre au sommet de la pile': 'Move to top of stack', 'Mettre au fond': 'Move to bottom of stack',
  'Monter d\'un cran': 'Move up one', 'Descendre d\'un cran': 'Move down one',
  'Replier, puis désélectionner': 'Collapse, then deselect',
  'Ligne de vue (pendant le tracé)': 'Line of sight (while drawing)',
  'Niveau de la cible': 'Target level', 'Niveau de la source': 'Source level',
  'Plus haut niveau hors toit': 'Highest non-rooftop level', 'Ctrl à l\'appui': 'Ctrl on press',
  'Relecture': 'Replay', 'Coup suivant / précédent': 'Step forward / back',
  'Page suivante / précédente': 'Page Down / Page Up',
  // Identification et utilisateurs
  'Identification': 'Login',
  'Utilisateur': 'User', 'Nom du nouvel utilisateur': 'New user name', 'nom affiché': 'display name',
  'Mot de passe': 'Password', 'mot de passe': 'password',
  'Se connecter': 'Log in', 'Utilisateurs…': 'Users…', 'Utilisateurs': 'Users',
  'Nouvel utilisateur…': 'New user…',
  'Pour un joueur VASL, le mot de passe est celui de VASL : il ouvre ses pions dissimulés et en HIP.':
    'For a VASL player, the password is the VASL one: it reveals their concealed and HIP counters.',
  'Mot de passe administrateur incorrect.': 'Incorrect administrator password.',
  'Saisir le mot de passe.': 'Enter the password.', 'Ce mot de passe est réservé.': 'This password is reserved.',
  'Nom requis : 24 caractères au plus, sans ; / \\ , < > & ".': 'Name required: 24 characters at most, without ; / \\ , < > & ".',
  'Cet utilisateur existe déjà : le choisir dans la liste.': 'This user already exists: pick it from the list.',
  'Mot de passe incorrect pour cet utilisateur.': 'Incorrect password for this user.',
  'Camp': 'Side', '— choisir —': '— choose —', 'Axe': 'Axis', 'Alliés': 'Allied', 'Les deux': 'Both',
  'Choisir le camp.': 'Choose the side.', 'Pion du camp adverse': 'Opposing side counter',
  'Déplacer quand même': 'Move anyway', 'Déplacement inhabituel': 'Unusual move',
  'Déplacer un pion du camp adverse demande une confirmation ; « Les deux » ne demande rien. Le camp reste sur ce poste, hors des fichiers VASL.':
    'Moving a counter of the opposing side asks for confirmation; “Both” asks nothing. The side stays on this device, outside VASL files.',
  'Connecté :': 'Logged in as:', 'Pions masqués': 'Hidden counters', '(vous)': '(you)',
  'Oublier': 'Forget', 'fichier': 'file', 'Aucun utilisateur': 'No users',
  'Changer d\'utilisateur…': 'Change user…',
  'Comme dans VASL, tous les pions se déplacent librement ; un pion dissimulé ou en HIP n\'est visible et commandable que par son joueur et par l\'administrateur.':
    'As in VASL, every counter moves freely; a concealed or HIP counter can only be seen and commanded by its player and by the administrator.',
  // Partie à distance
  'Partie à distance': 'Server',
  'Comme le serveur de VASSAL : l\'hôte ouvre une salle et donne son code ; qui la rejoint reçoit la partie du propriétaire, puis chaque coup joué part chez les autres.':
    'Like the VASSAL server: the host opens a room and gives its code; whoever joins receives the owner\'s game, then every move is sent to the others.',
  'Vous jouez sous le nom': 'You are playing as',
  'Liaison': 'Connection', 'Internet (direct ou par relais)': 'Internet (direct or relayed)',
  'Onglets de ce navigateur': 'Tabs of this browser',
  'Nom de la salle (pour héberger)': 'Room name (to host)', 'Code de la salle (pour rejoindre)': 'Room code (to join)',
  'ex. k7m2qa': 'e.g. k7m2qa',
  'Héberger': 'Host', 'Rejoindre': 'Join', 'Identification…': 'Login…',
  'Internet : l\'invité rejoint l\'hôte en direct (WebRTC) quand le réseau le permet, sinon par un relais chiffré (serveurs MQTT publics). L\'hôte doit garder sa page ouverte, au premier plan sur un téléphone : elle tient lieu de serveur. Mieux vaut héberger sur le PC.':
    'Internet: the guest reaches the host directly (WebRTC) when the network allows it, otherwise through an encrypted relay (public MQTT servers). The host must keep their page open, in the foreground on a phone: it acts as the server. Hosting on the PC is best.',
  'Salle': 'Room', '— code': '— code', 'Connexion en cours… — code': 'Connecting… — code', 'Copier': 'Copy',
  'Onglets de ce navigateur.': 'Tabs of this browser.', 'Onglets de ce navigateur, vous hébergez.': 'Tabs of this browser, you are hosting.',
  'pas de chemin réseau': 'no network path', 'aucun serveur de relais joignable': 'no relay server reachable',
  'serveur injoignable': 'server unreachable', 'pas d\'hôte sur le relais': 'no host on the relay',
  'Joueur': 'Player', 'propriétaire': 'owner', 'En attente du serveur…': 'Waiting for the server…',
  'Synchroniser': 'Synchronize', 'Fermer la salle': 'Close room', 'Quitter la salle': 'Leave room',
  'Donner le code à l\'adversaire. Fermer cette page ferme la salle.': 'Give the code to your opponent. Closing this page closes the room.',
  'Synchroniser redemande l\'état de la partie au propriétaire de la salle.': 'Synchronize asks the room owner for the game state again.',
  'Saisir le code donné par l\'hôte.': 'Enter the code given by the host.',
  'Copié': 'Copied', 'Copie impossible : noter le code.': 'Cannot copy: write the code down.',
  'Synchronisation impossible pour l\'instant.': 'Cannot synchronize for now.',
  'bibliothèque PeerJS injoignable': 'PeerJS library unreachable',
  'salle introuvable — vérifier le code': 'room not found — check the code',
  'ce code de salle est déjà pris': 'this room code is already taken', 'erreur WebRTC': 'WebRTC error',
  'serveur de rendez-vous PeerJS injoignable': 'PeerJS rendezvous server unreachable',
  'salle injoignable': 'room unreachable', 'code de salle invalide': 'invalid room code',
  // Fichier de log
  'Nom du fichier': 'File name', 'Commencer': 'Begin',
  'Exporter la partie': 'Save Game', 'Enregistrer': 'Save', 'Fichier prêt': 'File ready',
  'Fichier prêt :': 'File ready:', 'Partager / Enregistrer': 'Share / Save',
  'Puis « Enregistrer dans Fichiers » pour choisir le dossier, ou Mail, Messages, AirDrop pour l\'envoyer.':
    'Then “Save to Files” to choose the folder, or Mail, Messages, AirDrop to send it.',
  '« Enregistrer dans Fichiers » choisit le dossier ; Mail, Messages ou AirDrop envoient la partie.':
    '“Save to Files” chooses the folder; Mail, Messages or AirDrop send the game.',
  'Permettre à mes adversaires de démasquer mes unités': 'Allow opponents to unmask my units',
  'Vous êtes observateur : la case s\'appliquera à vos unités dès que vous vous identifierez (Utilisateur).':
    'You are an observer: the box will apply to your units as soon as you log in (User).',
  'Les coups joués, reçus ou relus à partir de cette position y seront enregistrés ; « Log » à nouveau termine le fichier et l\'écrit.':
    'Moves played, received or replayed from this position will be recorded in it; “Log” again ends the file and writes it.',
  'La relecture commence. Commencer un nouveau fichier de log ?': 'Replay begins. Begin a new logfile?',
  'Partie ouverte. Commencer un nouveau fichier de log ?': 'Game opened. Begin a new logfile?',
  'Relecture terminée. Commencer un nouveau fichier de log ?': 'Replay finished. Begin a new logfile?',
  'Fichier de log': 'Logfile', 'Oui': 'Yes', 'Non': 'No', 'Ne plus demander': 'Don\'t ask again',
  // Lancer IFT (affichage tactile)
  'Lancer IFT': 'IFT attack', 'Modificateur DR': 'DR modifier',
  'Valider': 'Roll', 'Relancer': 'Roll again'
};

// Libellés à trous : « {} » prend n'importe quel texte, lui-même traduit.
const EN_MOTIFS = [
  ['{} [{}]', '{} [{}]'],
  ['Terminer le log « {} » et l\'écrire', 'End Logfile “{}” and write it'],
  ['Partie à distance — {}', 'Server — {}'],
  ['Liaison : {} — vous hébergez.', 'Connection: {} — you are hosting.'],
  ['Liaison : {}.', 'Connection: {}.'],
  ['Internet : {}', 'Internet: {}'],
  ['onglets de ce navigateur', 'tabs of this browser'],
  ['relais {}', 'relay {}'],
  ['directe + {}', 'direct + {}'],
  ['directe', 'direct'],
  ['relais', 'relay'],
  ['Utilisateur : {} — changer ou gérer les utilisateurs', 'User: {} — change or manage users'],
  ['Pile ({})', 'Stack ({})'],
  ['{} unité(s) sur {}', '{} of {} unit(s)'],
  ['Cible {}', 'Target {}'],
  ['↓ {} nouveaux', '↓ {} new'], ['↓ {} nouveau', '↓ {} new'],
  ['{} pions', '{} counters'], ['{} pion', '{} counter'],
  ['{} pion(s)', '{} counter(s)'], ['{} pion(s) (200 premiers)', '{} counter(s) (first 200)'],
  ['{} — {}', '{} — {}'],
  ['{} — glisser dans la liste pour changer son rang, vers la carte pour le déplacer',
   '{} — drag within the list to change its position, onto the map to move it'],
  ['{}, sélectionné', '{}, selected'],
  ['{} (gpid {}) — glisser vers la carte, ou cliquer pour poser au centre ; au doigt, toucher puis toucher la carte',
   '{} (gpid {}) — drag onto the map, or click to place it in the centre; by touch, tap then tap the map'],
  ['➡️ {} — touchez la destination', '➡️ {} — tap the destination'],
  ['♟ {} — touchez la carte pour poser', '♟ {} — tap the map to place'],
  ['Message en tant que {}', 'Message as {}'],
  ['voit tous les pions masqués', 'sees all hidden counters'],
  ['{} pion(s) masqué(s)', '{} hidden counter(s)'],
  ['compte de ce poste', 'account on this device'],
  ['Choisir l\'utilisateur, puis saisir son mot de passe. Les pions dissimulés et HIP ne sont visibles que de leur joueur ; cette carte en contient de {} joueur(s).',
   'Choose the user, then enter their password. Concealed and HIP counters are only visible to their player; this map has some from {} player(s).'],
  ['Choisir l\'utilisateur, puis saisir son mot de passe. Les pions dissimulés et HIP ne sont visibles que de leur joueur.',
   'Choose the user, then enter their password. Concealed and HIP counters are only visible to their player.'],
  ['Retirer {} pions de la carte ?', 'Delete {} counters from the map?'],
  ['Déplacement de {} hexagones (plus de {}) : {}. Le faire quand même ?', 'Move of {} hexes (more than {}): {}. Move anyway?'],
  ['Déplacement de {} hexagones (plus de {}) : {}… Le faire quand même ?', 'Move of {} hexes (more than {}): {}… Move anyway?'],
  ['↩️ Déplacement annulé : {} hexagones', '↩️ Move cancelled: {} hexes'],
  ['{} pion(s) du camp adverse ({}) : {}. Les déplacer quand même ?', '{} counter(s) of the opposing side ({}): {}. Move them anyway?'],
  ['Dernier camp : {}', 'Last side: {}'],
  ['Le log « {} » n\'est pas écrit ({} coup(s)). L\'écrire maintenant ?', 'Logfile “{}” is not written ({} move(s)). Write it now?'],
  ['Le log « {} » n\'a pas été écrit avant la fermeture de la page ({} coup(s)). L\'écrire maintenant ?',
   'Logfile “{}” was not written before the page closed ({} move(s)). Write it now?']
].map(([fr, en]) => {
  const parts = fr.split('{}').map(s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  return {re: new RegExp('^' + parts.join('(.+?)') + '$'), en};
});

// Touches, comme VASSAL les nomme.
const EN_TOUCHES = [[/Maj\+/g, 'Shift+'], [/Méta\+/g, 'Meta+'], [/\bÉchap\b/g, 'Esc'], [/Entrée/g, 'Enter'],
  [/Suppr/g, 'Delete'], [/Espace/g, 'Space'], [/Page précédente/g, 'Page Up'], [/Page suivante/g, 'Page Down']];
const _estTouche = s => /^(?:(?:Ctrl|Alt|Maj|Méta)\+)*(?:[^\s+]{1,4}|Échap|Entrée|Suppr|Espace|Num\d|Page (?:précédente|suivante))(?: \/ .+)?$/.test(s);

function T(s){
  if(LANGUE === 'fr' || typeof s !== 'string') return s;
  const k = s.replace(/\s+/g, ' ').trim();
  if(!k) return s;
  if(Object.hasOwn(EN, k)) return EN[k];
  if(_estTouche(k)) return EN_TOUCHES.reduce((t, [re, en]) => t.replace(re, en), k);
  for(const m of EN_MOTIFS){
    const r = m.re.exec(k);
    if(!r) continue;
    let i = 1;
    const t = m.en.replace(/\{\}/g, () => T(r[i++]));
    if(t !== k) return t;
  }
  return s;
}

// Traduction de la page : textes et attributs, hors carte et journal
// (noms des pions, messages des joueurs, rapports).
const _ATTR_TRADUITS = ['title', 'placeholder', 'aria-label'];
const _HORS_TRADUCTION = '#mapInner, #journal, #apercuChat .txt, script, style';
function _traduireAttr(el, a){
  const v = el.getAttribute(a);
  if(v){ const t = T(v); if(t !== v) el.setAttribute(a, t); }
}
function _traduireNoeud(n){
  if(n.nodeType === 3){
    const p = n.parentElement;
    if(!p || p.closest(_HORS_TRADUCTION)) return;
    const v = n.nodeValue, t = T(v);
    // Les espaces autour du libellé restent. Réécrire la même valeur
    // relancerait l'observateur sans fin : T rend « web » pour « web ».
    const nv = /^\s*/.exec(v)[0] + t + /\s*$/.exec(v)[0];
    if(t !== v && nv !== v) n.nodeValue = nv;
    return;
  }
  if(n.nodeType !== 1 || n.closest(_HORS_TRADUCTION)) return;
  for(const a of _ATTR_TRADUITS) _traduireAttr(n, a);
  for(const c of n.childNodes) _traduireNoeud(c);
}
function _brancherLangue(){
  document.documentElement.lang = LANGUE;
  if(LANGUE === 'fr') return;
  document.title = T(document.title);
  _traduireNoeud(document.body);
  new MutationObserver(l => {
    for(const m of l){
      if(m.type === 'childList') m.addedNodes.forEach(_traduireNoeud);
      else if(m.type === 'characterData') _traduireNoeud(m.target);
      else if(!m.target.closest(_HORS_TRADUCTION)) _traduireAttr(m.target, m.attributeName);
    }
  }).observe(document.body, {childList: true, subtree: true, characterData: true,
                             attributes: true, attributeFilter: _ATTR_TRADUITS});
}

