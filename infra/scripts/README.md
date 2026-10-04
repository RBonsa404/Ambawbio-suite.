# Scripts d'outillage

Prérequis : Node.js 20 ou plus. Installer une fois les dépendances : `npm ci`.

| Commande | Effet |
|---|---|
| `npm run icones` | Génère les PNG des favicons, des icônes PWA, de l'icône Apple et de l'icône Android adaptative à partir des SVG de `docs/design/icones-app/` (voir son README). Option : `node generer-icones.mjs --sortie <dossier>` |
| `npm run polices` | Télécharge Archivo, IBM Plex Sans et IBM Plex Mono (OFL 1.1, latin + latin-ext, WOFF2) dans `docs/design/polices/` et écrit `polices.css`. Derrière un proxy : `NODE_USE_ENV_PROXY=1 npm run polices` |

Les fichiers produits sont versionnés : relancer le script après toute modification d'un SVG source et committer le résultat.
