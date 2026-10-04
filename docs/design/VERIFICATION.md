# Vérification du paquet de design par rapport au guide

Contrôle du paquet Claude Design (AMB-CONC-10) au regard du guide AMB-CONC-09 : section 9 (client Angular et Android) et règle R-16 (« design issu de Claude Design ; aucune couleur ou police en dur »). Réalisé le 2026-10-04, à l'intégration du paquet (branche `design/integration-paquet`).

## Résumé

| Point contrôlé | Résultat |
|---|---|
| Noms des jetons en français, sans accents, en kebab-case | ✅ Conforme (156 jetons, aucun nom hors règle) |
| Cohérence `tokens.json` ↔ `theme.css` | ✅ Les 66 primitives et les 24 sémantiques ont les mêmes valeurs, en thème normal comme en plein soleil |
| Variante plein soleil | ⚠️ Couleurs complètes et contrastées ; **bordures épaissies et textes +2 px non traduits en jetons** (écart E-02) |
| Contrastes WCAG | ✅ AA pour tous les textes ; montants (couleur `texte`) en AAA ; voir le détail plus bas |
| Polices sous licence OFL 1.1 | ✅ Archivo, IBM Plex Sans, IBM Plex Mono ; `OFL.txt` avec les deux mentions de copyright |
| Polices auto-hébergées | ✅ Corrigé à l'intégration (écart E-05) |
| Logos vectorisés | ✅ Aucun `<text>` dans `logo/*.svg` ; originaux avec texte dans `logo/source/` |
| Icônes d'application PNG | ✅ Générées par `infra/scripts/generer-icones.mjs` |

## Écarts relevés

| ID | Écart | Référence | Gravité | Proposition |
|---|---|---|---|---|
| E-01 | Pas de jetons pour les **graisses** (`--font-weight-*`), l'**espacement des lettres** (`--tracking-*`) ni la **largeur d'Archivo** (112 à 120 %). Ces valeurs ne figurent que dans les README et les maquettes. | Brief §8.1 (typographie : graisses, espacement des lettres) ; R-16 | Moyenne : sans jeton, la tentation est de les écrire en dur | Ajouter dans `tokens.json` et `theme.css` (LOT 3) : `--font-weight-normal/moyen/semi-gras/gras/extra-gras`, `--tracking-titre`, `--tracking-descripteur`, et une utilitaire `font-stretch` pour les titres |
| E-02 | Variante plein soleil : « bordures 2–3 px, textes +2 px » annoncés dans `README.md` mais absents de `tokens.json` et de `theme.css` (seules les couleurs changent). | Brief §7.1, §12 ; guide §9.1 | Moyenne | Ajouter des jetons `--border-width-*` et un décalage de taille pour `[data-theme="plein-soleil"]` (LOT 3), ou trancher qu'il s'agit de règles de composant |
| E-03 | Les trois polices **ne contiennent pas l'espace fine insécable U+202F**. Or le format des montants (`12 500 FCFA`) et `Intl.NumberFormat('fr-FR')` l'utilisent : le navigateur la prend dans une police de secours. | Guide §9.2 ; README §Interactions (formats) | Moyenne : rendu des montants légèrement irrégulier | Au LOT 3, décider entre : (a) remplacer U+202F par U+00A0 dans `formaterFcfa` ; (b) une mini-police « espace fine » limitée à U+202F par `unicode-range`. Ne pas modifier IBM Plex (nom réservé « Plex » de l'OFL) |
| E-04 | Les durées `--duration-*` ne sont pas un espace de noms du thème Tailwind 4 : aucune utilitaire `duration-rapide` n'est générée, et la variable risque de ne pas être émise si rien ne la référence. | R-16 | Faible | À vérifier au LOT 3 ; au besoin, `@theme static` ou utilisation explicite `duration-(--duration-rapide)` |
| E-05 | *(Corrigé)* Les CSS de polices livrées pointaient vers `fonts.gstatic.com` (pas d'auto-hébergement), `archivo-variable.css` était vide et Archivo était en largeur 100 % seulement, alors que le logotype et les titres demandent 112 à 120 %. | `polices/LICENCES.md` (auto-hébergement, données au Burkina) | — | Remplacé par Archivo variable (axes wght et wdth), Plex Sans variable et Plex Mono, sous-ensembles latin et latin-ext seulement, et un `polices.css` en chemins relatifs. Le tout est généré par `infra/scripts/telecharger-polices.mjs` (≈ 350 Ko au lieu de 1,1 Mo) |
| E-06 | Les SVG (logos, icônes d'application) ne sont pas « optimisés » : chacun embarque environ 7,6 Ko de manifeste de provenance C2PA dans `<metadata>`, pour moins de 300 octets de dessin. | Brief §6.1 (« fichiers SVG optimisés ») | Faible | Garder les originaux dans `docs/design/` ; supprimer les métadonnées (svgo) lors de la copie vers `frontend/` au LOT 3 |
| E-07 | Captures manquantes (D-07 : fournies à la main par le porteur du projet, à la demande) : `references/captures/` ne contient que la caisse (10 PNG). Rien pour les documents D-01 à D-08 ni pour le site vitrine, alors que `README.md` annonce « D-01.png… ». | Échange avec Claude Design | Faible | Générer les captures avec Playwright au moment des lots concernés (LOT 6 pour les documents) |
| E-08 | `README.md` du paquet mentionne « un README qui explique ce qui est en haute fidélité » dans `references/` : ce fichier n'existe pas. Le tableau « Fidélité » de `docs/design/README.md` le remplace. | — | Faible | Aucune action : le tableau fait foi |
| E-09 | Les icônes Android sont livrées en SVG ; Android attend des VectorDrawable ou des PNG. | Guide §9.3 | — | PNG générés pour toutes les densités (`icones-app/png/android/res/`) ; VectorDrawable possible plus tard via Android Studio |

## Contrastes (WCAG 2.x)

| Paire | Normal | Plein soleil |
|---|---|---|
| `texte` / `fond` | 15,22 | 21,00 |
| `texte-secondaire` / `surface` | 7,47 | 14,10 |
| `primaire-texte` / `primaire` | 4,99 (AA) | 10,89 |
| `succes`, `avertissement`, `danger`, `info` / `surface` | 7,11 à 8,08 | 13,81 à 14,79 |
| États métier (texte / fond de l'état) | 8,46 à 11,46 | 16,97 à 21,00 |
| `accent` / `surface` | **2,42** | **3,81** |

`accent` (or) ne doit pas servir de couleur de texte : il est réservé aux aplats, filets et décors. À rappeler dans la documentation des composants (LOT 3).

## Points conformes à noter

- Les préfixes `--color-`, `--text-`, `--radius-`, `--shadow-`, `--ease-`, `--breakpoint-` sont imposés par Tailwind 4 ; seuls les suffixes sont en français, ce qui respecte la consigne.
- Les sémantiques de `@theme` référencent les primitives par `var()` (pas de `@theme inline`), ce qui permet à `[data-theme="plein-soleil"]` de les redéfinir à l'exécution. À conserver.
- Les états métier ont toujours une icône et un libellé (fiches `composants/`), conformément au brief §7.1.
- Opérateurs Mobile Money : nom en texte dans une puce neutre, aucun logo (`icones/README.md`).
- Zones réservées `[PRIX À DÉFINIR]`, `[MENTIONS DGI À CONFIRMER]`, `[IFU EN COURS]`, `[RCCM EN COURS]` présentes dans les maquettes et les gabarits ; suivies dans `docs/QUESTIONS.md` (Q-01, Q-08, Q-09).
