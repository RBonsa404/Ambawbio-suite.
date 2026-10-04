# Gabarits de documents

Références visuelles : `references/Phase 6 - Documents.dc.html` (D-01 à D-08). Implémentation suggérée : composants Angular rendus en HTML → PDF côté serveur (Puppeteer / WeasyPrint) pour A4 ; ESC/POS pour les tickets thermiques.

Convention des zones : `{objet.champ}` ; `[]` = liste répétée ; `?` = facultatif.

## D-01 Facture A4 (certifiée FEC) — 210 × 297 mm, marges 13 mm
| Zone | Contenu | Règles |
|---|---|---|
| `{entreprise.logo?}` | Logo de l'entreprise cliente, 19 × 19 mm max | Si absent : raison sociale seule. **Jamais le logo Ambawbio en grand.** |
| `{entreprise.raison_sociale}` `{entreprise.forme}` `{entreprise.capital}` | | 12 pt gras pour la raison sociale |
| `{entreprise.adresse}` `{entreprise.telephone}` `{entreprise.email}` | | |
| `{entreprise.ifu}` `{entreprise.rccm}` `{entreprise.regime}` `{entreprise.division_fiscale}` | | IFU en Plex Mono |
| `{facture.type}` | « Facture », « Avoir », « Facture d'acompte » | Archivo 800 118 %, capitales, 22 pt |
| `{facture.numero}` `{facture.date}` `{facture.echeance}` `{etablissement.nom}` | | Numéro en Plex Mono |
| `{client.raison_sociale}` `{client.adresse}` `{client.ifu?}` `{client.rccm?}` `{client.regime?}` | | IFU obligatoire pour client assujetti |
| `{references.commande?}` `{references.livraison?}` `{vendeur.nom}` | | |
| `{lignes[]}` → `designation` `quantite` `unite` `prix_unitaire_ht` `taux_tva` `montant_ht` | | Montants alignés à droite, chiffres tabulaires, séparateur U+202F |
| `{taxes[]}` → `libelle` `base_ht` `montant` | Récapitulatif par taux (18 %, exonéré…) | |
| `{totaux.ht}` `{totaux.tva}` `{totaux.precompte?}` `{totaux.ttc}` `{totaux.deja_regle?}` `{totaux.reste_a_payer?}` | | TTC en Archivo 800 18 pt, filet 3 px au-dessus |
| `{totaux.ttc_lettres}` | « Un million deux cent quarante-deux mille huit cents francs CFA » | Conversion FR (règles de 1990 non appliquées : traits d'union classiques) |
| `{mentions_dgi[]}` | **[MENTIONS DGI À CONFIRMER]** | Zone réservée : liste paramétrable, en attente des spécifications officielles de la FEC |
| `{paiement.virement?}` `{paiement.mobile_money[]}` `{paiement.conditions}` | Opérateur + numéro, en texte | |
| `{fec.statut}` | `certifiee` `en_attente` `rejetee` `simulee` | Pilote le bloc de certification ci-dessous |
| `{fec.qr}` | QR DGI (≥ 30 mm, quiet zone 4 modules) | `en_attente` : zone hachurée « QR à venir » |
| `{fec.identifiant}` `{fec.horodatage}` `{fec.mcf?}` | | Plex Mono |
| Bloc certification | certifiée : bordure `vert-800` · en attente : fond `or-50`, bordure `or-800` · simulée : bordure `rouge-700` + filigrane « SANS VALEUR FISCALE » à 13 % d'opacité | Texte d'aide selon statut (voir référence) |
| Pied | `{entreprise.raison_sociale}` · IFU · « Page {n}/{total} » · symbole 3 mm + « Édité avec Ambawbio Suite » | Frise chevrons noire 3 mm en bas de page (désactivable) |

## D-02 Avoir — même gabarit
`{facture.type}` = « Avoir », `{avoir.facture_origine.numero}` `{avoir.facture_origine.date}` `{avoir.facture_origine.fec_id}` `{avoir.motif}` dans un bloc fond `banco-50` sous l'en-tête. Montants affichés en négatif.

## D-03 Devis · Bon de commande · Bon de livraison
Même en-tête et pied. Spécifique : devis `{devis.validite}` + zone « Bon pour accord » ; BC `{commande.date_livraison_souhaitee}` ; BL **sans prix**, `{livraison.receptionnaire}` + zone signature. Pas de bloc FEC.

## D-04 Ticket de caisse 58 mm (384 px à 203 dpi) et 80 mm (576 px)
Noir seul, aucune trame. Police imprimante (Font A) ou Plex Mono rendue en bitmap.
Ordre : `{entreprise.logo_mono?}` (ou symbole Ambawbio si aucun logo) → raison sociale CAPITALES → adresse, IFU, téléphone → tirets → `{ticket.numero}` `{ticket.date_heure}` `{caisse.nom}` `{caissier.prenom}` → `{lignes[]}` (58 mm : désignation sur une ligne, « qté × P.U. » + montant sur la suivante ; 80 mm : 4 colonnes) → TVA incluse → **TOTAL encadré, double hauteur** → `{paiements[]}` → **RENDU** double largeur → `{fec.qr}` 24 mm → `{facture.numero?}` + statut en CAPITALES (« EN ATTENTE DE CERTIFICATION ») → remerciement → « Édité avec Ambawbio Suite ».

## D-05 Rapport Z — format ticket
`{z.numero}` `{session.caisse}` `{session.date}` `{session.ouverture}` `{session.cloture}` `{session.caissier}` · ventes (nb, montant) · retours · tableau `{moyens[]}` attendu/compté · **ÉCART encadré** · `{ecart.motif?}` `{ecart.valide_par?}` · plage de tickets · paiements à vérifier.

## D-06 Courriels — HTML 600 px + texte brut
Modèles : `devis`, `facture`, `relance_j7`, `relance_j30`, `bienvenue`, `renouvellement`. Expéditeur = l'entreprise cliente. Tableaux HTML, styles en ligne, pas d'image obligatoire (< 30 Ko). Bouton principal latérite `#B4532A` texte blanc. Variables : `{client.civilite_nom}` `{document.numero}` `{document.montant_ttc}` `{document.reste_a_payer}` `{document.echeance}` `{lien.paiement}` `{lien.pdf}` `{signataire.nom}`.

## D-07 SMS (≤ 160 caractères GSM-7, sans accents) et WhatsApp
Voir textes dans la référence. Liens courts `amb.bf/f/{code}` (facture) et `amb.bf/p/{code}` (paiement). Le compteur de caractères est contrôlé à l'envoi.

## D-08 Supports physiques
Autocollant terminal Ø 60 mm (fond latérite, symbole et logotype blancs) · carte de visite 85 × 55 mm · en-tête de lettre A4 · kakémono 85 × 200 cm. Fichiers d'impression à produire en CMJN avec texte vectorisé.
