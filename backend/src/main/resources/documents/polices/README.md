# Polices des documents PDF

Versions statiques (TTF) des polices de la charte, pour le moteur PDF (qui ne lit ni WOFF2 ni les axes variables).
Elles sont dérivées de `docs/design/polices` (licence SIL OFL 1.1, voir `OFL.txt`) avec fontTools :

| Fichier | Source | Instance |
|---|---|---|
| `documents-titre.ttf` | Archivo (variable) | graisse 800, largeur 118 % |
| `documents-sans-regulier.ttf` | IBM Plex Sans (variable) | graisse 400 |
| `documents-sans-gras.ttf` | IBM Plex Sans (variable) | graisse 700 |
| `documents-mono.ttf` | IBM Plex Mono | 400 |

Conformément à l'OFL, ces versions modifiées ne portent pas les noms réservés (« Plex ») : familles renommées
« Ambawbio Documents Sans / Mono / Titre ». Sous-ensemble latin (français complet ; U+202F absent, d'où U+00A0, D-05).
