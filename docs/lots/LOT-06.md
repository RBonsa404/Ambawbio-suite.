# LOT 6 — Facturation et conformité FEC

**Références** : F-FAC-01 à 06 ; UC-FAC-01, 02 ; SD-05, SD-08 ; RG-01, 02, 03 ; INV-01 à 04.
**Acceptation** : 1 000 validations concurrentes → aucun trou ni doublon ; facture validée impossible à modifier (API et base) ; certification différée testée (DGI simulée indisponible puis disponible).
**Statut** : terminé, en attente de validation.

## Réalisation

| Élément | Contenu |
|---|---|
| Module `facturation` | Pièce fiscale (facture, avoir) et lignes ; brouillon modifiable puis validation. À la validation : numéro sans trou `FA-2026-000154` (compteur par société, type et exercice, verrouillé dans la transaction, guide §6.7) ; mentions de l'émetteur et du client figées (RG-02) ; échéance selon le délai du client ; IFU du client contrôlé auprès du port de certification (F-FAC-03) ; IFU obligatoire pour l'émetteur et pour un client assujetti (INV-03) |
| Immuabilité (RG-01, INV-01) | Refus côté domaine et API (code `RG-01`) ; déclencheurs PostgreSQL qui refusent toute modification ou suppression d'une pièce validée et de ses lignes, même par une requête SQL de l'application. Seuls évoluent ensuite le statut de paiement ou d'annulation, le cumul des avoirs et la certification |
| Avoirs (F-FAC-05, SD-08, INV-04) | Avoir partiel (quantités par ligne) ou total, numéroté `AV-2026-…`, jamais au-delà du reste non annulé ; facture « annulée par avoir » quand tout est couvert |
| Caisse (RG-03) | La facture demandée en caisse est numérotée hors-ligne dans la plage FACTURE du terminal (`FA-C01-2026-000001`), avec le client choisi ; elle est établie à la réception de la vente, déjà payée, puis mise en file de certification. Un retour sur une vente facturée produit l'avoir `AV-C01-…` |
| Module `conformite` | `PortCertificationFiscale` (certifier, vérifier un IFU) ; `SimulateurFec` (identifiant `SIM-…`, QR code, horodatage ; modes disponible, indisponible, rejet, réglables depuis W-10 en démonstration) ; `AdaptateurDgi` en structure seulement (Q-01). File de certification : mise en file dans la transaction de validation, première tentative juste après, puis réessais à 1 min, 5 min, 15 min, 1 h et ensuite toutes les heures ; alerte au-delà de 24 h ; relance manuelle |
| PDF et archivage (F-FAC-06) | Gabarits D-01 et D-02 rendus en PDF : polices de la charte, taxes par taux, montant en lettres, bloc de certification selon l'état (QR à venir, certifiée, simulée avec filigrane « SANS VALEUR FISCALE », rejetée). Chaque version (validation, puis certification) est archivée avec son empreinte SHA-256, sans modification ni suppression possible |
| Écran W-10 | Liste et fiche côte à côte, filtres (brouillons, en file, rejetées, avoirs), badge FEC de la charte, éditeur (client, produits du catalogue, lignes libres), validation, avoir partiel ou total, aperçu et téléchargement du PDF, relance de la certification, bandeau « Mode démonstration » |
| Caisse (A-09, A-12) | Case « facture demandée » avec choix du client (catalogue local) ; numéro de facture affiché « en attente de certification » sur l'écran de confirmation et sur le ticket |

## Critères d'acceptation

| Critère | Résultat | Preuve |
|---|---|---|
| 1 000 validations concurrentes | ✅ | `FacturationTest.milleValidationsConcurrentesSansTrouNiDoublon` : 1 000 brouillons validés par 24 fils en parallèle → 1 000 numéros distincts, séquence de 1 à 1 000 |
| Facture validée immuable (API et base) | ✅ | `UC_FAC_01_…` : modification et suppression refusées par l'API (`RG-01`) ; `UPDATE` de la facture ou d'une ligne et `DELETE` refusés par la base même pour le rôle applicatif ; archives PDF non modifiables |
| Certification différée | ✅ | `certificationDiffereeQuandLeServiceEstIndisponiblePuisDisponible` : simulateur indisponible → en file, délais croissants, alerte après 24 h ; service rétabli → certifiée `SIM-…`. Même scénario dans l'interface (`e2e/factures.spec.ts`) |

Autres tests :

- **Serveur** : rejet de certification, avoirs (INV-04), RG-02 (le numéro n'est pas consommé en cas de refus), montant en lettres, facture de caisse et avoir sur retour (`CaisseHorsLigneTest`).
- **E2E** : 15 scénarios réussis, dont W-10 et la vente « facture demandée » en caisse.

## Écarts et reports

- **Adaptateur DGI** : non implémenté (cahier des charges technique de la FEC non publié, Q-01). La structure, la configuration (`ambawbio.fec.adaptateur=dgi`) et le comportement prudent sont en place : sans spécification, les pièces restent en file et aucune certification n'est inventée.
- **Mentions DGI complémentaires** (« [MENTIONS DGI À CONFIRMER] ») : non affichées tant qu'elles ne sont pas connues ; les mentions retenues en Q-01 (identifiant, QR code, horodatage, IFU et régime, détail des taxes) figurent sur le PDF.
- **Archivage dans PostgreSQL** plutôt que dans le stockage S3 : voir D-36.
- **Paiements, reste à payer et impayés** : LOT 7 (le statut « non payée » s'affiche dès maintenant). Écritures comptables : LOT 10 (événements `FactureValidee` et `AvoirValide` déjà publiés).
- **Alertes de certification** : visibles dans W-10 et dans les journaux ; envoi au comptable par courriel ou SMS avec le module de notification (LOT 12).
- **Envoi de la facture au client** (courriel D-06, SMS D-07) : LOT 12.
