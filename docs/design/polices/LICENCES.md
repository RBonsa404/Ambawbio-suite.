# Polices

Toutes sous **SIL Open Font License 1.1** (usage commercial, intégration et auto-hébergement autorisés ; ne pas vendre les polices seules ; conserver la licence).

| Rôle | Famille | Graisses / axes | Source | Licence |
|---|---|---|---|---|
| Titres, montants, logotype | **Archivo** (variable) | wght 700–800, wdth 100–125 (utilisé : 105, 112, 115, 118, 120 %) | https://fonts.google.com/specimen/Archivo · https://github.com/Omnibus-Type/Archivo | OFL 1.1 |
| Interface, texte | **IBM Plex Sans** | 400, 500, 600, 700 | https://fonts.google.com/specimen/IBM+Plex+Sans · https://github.com/IBM/plex | OFL 1.1 |
| Codes, IFU, références, tickets | **IBM Plex Mono** | 400, 500, 600 | https://fonts.google.com/specimen/IBM+Plex+Mono | OFL 1.1 |

## Intégration recommandée (auto-hébergement, données au Burkina)
- `npm i @fontsource-variable/archivo @fontsource/ibm-plex-sans @fontsource/ibm-plex-mono`
- Sous-ensemble `latin` + `latin-ext` uniquement (accents français, « », espaces fines U+202F). Format WOFF2.
- `font-display: swap`. Budget : ~60 Ko Archivo variable + ~4 × 18 Ko Plex Sans + 18 Ko Plex Mono.
- Toujours `font-variant-numeric: tabular-nums` sur les montants, quantités et colonnes de chiffres.

## Logotype
Le logotype est composé en Archivo 800, largeur 120 %, approche +4 %. Les SVG de `logo/` sont vectorisés (Archivo Expanded ExtraBold compressé à 96 %, IBM Plex Sans SemiBold). Versions avec texte modifiable dans `logo/source/`.
