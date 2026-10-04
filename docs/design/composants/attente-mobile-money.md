# Écran d'attente Mobile Money — `<amb-attente-mobile-money>` (A-10)

## Rôle
Accompagner la caissière pendant que le client valide le paiement sur son propre téléphone, puis l'orienter selon l'issue.

## Entrées / sorties
| Propriété | Type |
|---|---|
| `operateur` | `'orange-money' \| 'moov-money' \| 'wave'` (affiché en texte : « Orange Money », « Moov Money », « Wave ») |
| `telephone` | `string` E.164 (+226…) affiché « +226 70 12 34 56 » |
| `montant` | `number` FCFA |
| `delaiSecondes` | `number` défaut 120 |
| `etat` | `'attente' \| 'recu' \| 'refuse' \| 'expire'` |
| `reference?` | `string` (reçu) |
Sorties : `(renvoyer)`, `(changerMoyen)`, `(saisirReference)` → A-11, `(terminer)` → A-12.

## Mise en page (360 × 640)
1. Barre du haut charbon « Paiement Mobile Money » + établissement ; indicateur de synchro.
2. Ligne : puce opérateur (texte 14/600, bordure 2 px) à gauche, numéro 16 px tabulaire à droite.
3. Bloc montant entre deux filets 2 px : « Montant demandé » 15 px, montant Archivo 800 44 px tabulaire.
4. Anneau compte à rebours 112 px, trait 9 px : piste `banco-200`, progression `primaire` ; temps « m:ss » Archivo 800 30 px au centre.
5. « Le client valide sur son téléphone » 20 px/700 ; consigne à lire au client 15 px : « Vous allez recevoir une demande. Tapez votre code secret pour confirmer. »
6. Bas : « Renvoyer la demande » (secondaire, 52 px) puis lien « Changer de moyen de paiement ».

## Issues
| État | Visuel | Texte | Action principale |
|---|---|---|---|
| recu | fond `vert-100`, bordure 3 px `vert-800`, icône circle-check 40 px | « Paiement reçu » + montant + réf. Plex Mono | Automatique vers A-12 après 1,5 s |
| refuse | fond `rouge-100`, bordure `rouge-800`, circle-x | « Paiement refusé par le client. Proposez un autre moyen de paiement. » | « Choisir un autre moyen » |
| expire | fond `or-100`, bordure `or-800`, timer-off | « Délai dépassé. Le client a-t-il reçu un SMS de confirmation ? » | « Saisir la référence » (principal) · « Renvoyer » |

## Règles
- Anneau mis à jour 1 fois/s (pas d'animation CSS continue). À 0 : état `expire`.
- Si le réseau tombe pendant l'attente : passer en `expire` avec le texte « Connexion perdue » et proposer A-11 (référence SMS, statut **à vérifier**).
- Jamais de logo ni de couleur d'opérateur.
- Le bouton retour système demande confirmation : « Annuler la demande de paiement ? ».
- `aria-live="assertive"` sur le changement d'issue.
