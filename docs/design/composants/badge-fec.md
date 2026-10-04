# Badge de certification FEC — `<amb-badge-fec>`

## Rôle
Afficher l'état de certification DGI d'une facture ou d'un avoir, partout où la pièce apparaît (listes W-10, détail, caisse A-12/A-17, portail client, PDF).

## Entrées
`statut`: `'certifiee' | 'en-file' | 'rejetee' | 'simulee'` · `taille`: `'sm' | 'md'` · `detail?`: `{ identifiant, horodatage, motifRejet? }`

## Variantes
| Statut | Fond | Texte / icône | Bordure | Icône Lucide | Libellé |
|---|---|---|---|---|---|
| certifiee | `vert-100` #CEF2DD | `vert-800` #1B4332 | 1,5 px `vert-300` | badge-check | « Certifiée DGI » |
| en-file | `or-100` #FBE6B4 | `or-800` #503D01 | 1,5 px `or-300` | clock | « En file de certification » |
| rejetee | `rouge-100` | `rouge-800` | 1,5 px `rouge-300` | circle-x | « Rejetée — à corriger » |
| simulee | hachures 135° `banco-50`/`banco-100` | `banco-900` | 1,5 px **pointillée** `banco-600` | flask-conical | « Simulée — sans valeur fiscale » |

Tailles : md = padding 7 × 12 px, texte 14/600, icône 16 px ; sm (tableaux) = padding 3 × 8 px, texte 13/600, icône 14 px. Rayon 0.

## Bloc détaillé (fiche facture, PDF)
QR 72 px (écran) / 30 mm (PDF) + titre 14/700 couleur du statut + identifiant Plex Mono + « Certifiée le {date} à {heure} ». Bordure 2 px couleur du statut.
Rejet : afficher `motifRejet` en clair + action « Corriger et renvoyer » (crée un avoir si la facture est déjà émise, selon règles DGI).

## Règles
- Icône + libellé obligatoires ; le badge sm peut tronquer le libellé à « Certifiée », « En file », « Rejetée », « Simulée » mais garde l'icône et un `title`/`aria-label` complet.
- `simulee` ne doit jamais apparaître en production : afficher en plus une bannière d'environnement « Mode démonstration ».
