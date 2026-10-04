# Polices

Toutes sous **SIL Open Font License 1.1** (usage commercial, intégration et auto-hébergement autorisés ; ne pas vendre les polices seules ; conserver la licence).

| Rôle | Famille | Graisses / axes | Source | Licence |
|---|---|---|---|---|
| Titres, montants, logotype | **Archivo** (variable) | wght 700–800, wdth 100–125 (utilisé : 105, 112, 115, 118, 120 %) | https://fonts.google.com/specimen/Archivo · https://github.com/Omnibus-Type/Archivo | OFL 1.1 |
| Interface, texte | **IBM Plex Sans** | 400, 500, 600, 700 | https://fonts.google.com/specimen/IBM+Plex+Sans · https://github.com/IBM/plex | OFL 1.1 |
| Codes, IFU, références, tickets | **IBM Plex Mono** | 400, 500, 600 | https://fonts.google.com/specimen/IBM+Plex+Mono | OFL 1.1 |

## Fichiers fournis (auto-hébergement, données au Burkina)
- `polices.css` : règles `@font-face` en chemins relatifs, sans appel à un serveur externe. À importer tel quel ou à recopier dans `frontend/src/assets/polices/`.
- `archivo-700-800-*.woff2` : Archivo **variable** (axes `wght` et `wdth`, largeur 100–125 % déclarée), pour les titres, les montants et le logotype.
- `ibm-plex-sans-400-700-*.woff2` : IBM Plex Sans **variable** (400 à 700).
- `ibm-plex-mono-{400,500,600}-*.woff2` : IBM Plex Mono.
- Sous-ensembles `latin` et `latin-ext` uniquement (accents français, « »), WOFF2, `font-display: swap`. Total ≈ 350 Ko, dont le navigateur ne charge que le sous-ensemble utile.
- `OFL.txt` : texte de la licence et mentions de copyright. À livrer avec les polices.
- Régénération : `cd infra/scripts && NODE_USE_ENV_PROXY=1 npm run polices` (source : Google Fonts, mêmes fichiers que les dépôts officiels).

## Points d'attention
- Toujours `font-variant-numeric: tabular-nums` sur les montants, quantités et colonnes de chiffres.
- **Espace fine insécable U+202F absente des trois polices** (voir `../VERIFICATION.md`, écart E-03) : elle s'affiche avec la police de secours. Ne pas modifier IBM Plex pour l'ajouter : « Plex » est un nom réservé au sens de l'OFL, une version modifiée devrait changer de nom.
- Alternative npm si l'on préfère : `@fontsource-variable/archivo`, `@fontsource-variable/ibm-plex-sans`, `@fontsource/ibm-plex-mono` (mêmes licences).

## Logotype
Le logotype est composé en Archivo 800, largeur 120 %, approche +4 %. Les SVG de `logo/` sont vectorisés (Archivo Expanded ExtraBold compressé à 96 %, IBM Plex Sans SemiBold). Versions avec texte modifiable dans `logo/source/`.
