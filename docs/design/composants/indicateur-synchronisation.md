# Indicateur réseau et synchronisation — `<amb-indicateur-sync>`

## Rôle
Dire en permanence, sans ambiguïté, si le terminal est en ligne, ce qui est stocké sur l'appareil et ce qui reste à envoyer. Présent sur tous les écrans de caisse et de magasin (sous la barre du haut) ; version compacte dans la barre web.

## Entrées
| Propriété | Type | Description |
|---|---|---|
| `etat` | `'en-ligne' \| 'hors-ligne' \| 'envoi' \| 'en-attente' \| 'alerte'` | Calculé par le service de synchro |
| `enAttente` | `number` | Opérations dans la file locale |
| `envoyees` | `number` | Pendant `envoi` |
| `horsLigneDepuis` | `Date \| null` | |
| `derniereSynchro` | `Date \| null` | |
| `variante` | `'bandeau' \| 'pastille'` | bandeau = mobile/tablette (pleine largeur), pastille = web |
Sortie : `(ouvrir)` → écran A-17.

## États
| État | Condition | Fond / texte (jetons) | Icône Lucide | Libellé |
|---|---|---|---|---|
| en-ligne | réseau OK, file vide | `etat-certifie-fond` / `etat-certifie` | cloud-check | « En ligne — tout est synchronisé » + heure |
| hors-ligne | pas de réseau | `etat-hors-ligne-fond` / `etat-hors-ligne` | cloud-off | « Hors-ligne — {n} ventes sur l'appareil » (ou « … déverrouillage possible » sur A-04) |
| envoi | synchro en cours | `etat-synchronisation-fond` / `etat-synchronisation` | refresh-cw | « Envoi en cours — {envoyees} sur {total} » + barre de progression 80 × 6 px |
| en-attente | réseau OK, file non vide, envoi non démarré | `etat-en-attente-fond` / `etat-en-attente` | clock | « {n} opérations en attente d'envoi » + lien « Détails » |
| alerte | pas de synchro depuis > 48 h **ou** plage de numéros < 20 | `danger` fond rouge-100 / rouge-800 | triangle-alert | « Pas de synchronisation depuis {j} jours » + « Synchroniser » |

Hors-ligne n'est **pas** une erreur : couleur neutre. Ne jamais afficher l'état par la couleur seule.

## Dimensions
Bandeau : hauteur 32 px (min), padding 7 × 14 px, texte 14 px/600, icône 16 px, cible tactile = toute la largeur (≥ 48 dp avec la marge). Pastille web : hauteur 32 px, padding 6 × 10 px, texte 13 px/600.
Plein soleil : fond blanc, texte noir, bordure inférieure 3 px noire, texte 16 px/700, icône 20 px.

## Comportement
- Mise à jour sur événement (pas d'interrogation visible). Transition d'état : changement immédiat, pas d'animation continue ; l'icône refresh peut tourner seulement si `prefers-reduced-motion` n'est pas actif et uniquement pendant `envoi`.
- Passage hors-ligne → en ligne : toast « Réseau rétabli — envoi de {n} opérations ».
- Accessibilité : `role="status"` `aria-live="polite"` ; le libellé complet est le nom accessible.
- Vocabulaire interdit : « outbox », « queue », « sync failed ».
