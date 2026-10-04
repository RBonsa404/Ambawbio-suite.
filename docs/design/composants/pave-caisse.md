# Pavé de caisse et rendu de monnaie — `<amb-pave-caisse>` (A-09)

## Rôle
Saisir le montant reçu en espèces le plus vite possible, et afficher le rendu de monnaie sans erreur possible.

## Entrées / sorties
`totalAPayer: number` · `valeur: number` (FCFA, entier) · `max: number` défaut 9 999 999 · sorties `(valeurChange)`, `(valider)`.

## Mise en page (largeur 332 px dans un écran 360)
1. Champ « Reçu » : fond blanc, bordure 2 px `texte`, libellé 14/600 à gauche, valeur Archivo 800 30 px tabulaire à droite, séparateur de milliers U+202F automatique.
2. Raccourcis (3 boutons, hauteur 42 px, bordure 2 px) : billets/arrondis supérieurs au total. Algorithme : `[arrondiSup(total, 5000), arrondiSup(total, 10000), arrondiSup(total, 50000)]` dédoublonnés, en écartant ceux égaux au total ; si moins de 3 valeurs, compléter avec le montant exact.
3. Grille 3 × 4, gap 6 px, touches hauteur 46 px (téléphone) / 64 px (tablette), fond `banco-100`, pressé `banco-300`, chiffres Archivo 700 22 px : 1 2 3 / 4 5 6 / 7 8 9 / 00 0 ⌫. Appui long ⌫ = effacer tout.
4. Bloc résultat (bordure 2 px) :
   - `valeur >= total` : fond `vert-100`, texte `vert-800`, « Rendu » + montant Archivo 800 **40 px minimum**.
   - sinon : fond `rouge-100`, texte `rouge-800`, « Il manque » + montant.
5. Bouton « Valider le paiement » 56 px : actif seulement si `valeur >= total` ; désactivé = fond `banco-200`, texte `banco-600`.

## Règles
- Pas de décimales (FCFA). Ignorer les zéros de tête. Retour haptique léger à chaque touche (Capacitor Haptics) si activé.
- Clavier physique / douchette : chiffres, Retour arrière, Entrée = valider.
- Plein soleil : touches fond blanc bordure 2 px noire, chiffres noirs 24 px, rendu 48 px.
- Contraste du montant de rendu ≥ 7:1 (AAA).
- Mode **mixte** : même composant, `totalAPayer` = reste après la part Mobile Money.
