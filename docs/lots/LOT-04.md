# LOT 4 — Moteur de synchronisation et terminaux

**Références** : guide §8 ; F-SOC-11, 12, 13 ; UC-SOC-04, 05 ; SD-03, SD-11 ; RG-03, 04, 05, 06 ; INV-10, 11 ; ENF-01, 02.
**Acceptation** : tous les tests de la section 8.8 au vert ; appairage d'un téléphone réel et chargement initial de 2 000 produits en moins de 2 minutes en 3G simulée.
**Statut** : terminé, en attente de validation (appairage sur téléphone réel à faire par le porteur du projet).

## Réalisation

| Élément | Contenu |
|---|---|
| Base de données (`sync.*`) | `terminal`, `operation_recue` (clé tenant + idOperation, sans DELETE), `flux_changements` (séquence), `plage_numerotation` avec contrainte d'exclusion GiST sur les intervalles (INV-11) ; RLS partout |
| Terminaux (UC-SOC-04, 05) | Enregistrement par la gérance (`terminaux:gerer`), code d'appairage 8 caractères à usage unique valable 15 min (stocké haché), QR code `{"t","c"}`, clé publique ECDSA P-256 enregistrée à l'appairage, révocation définitive (plages clôturées, synchronisation refusée en 403) |
| Opérations (push) | Lots de 100, signature vérifiée (D-25), idempotence par `idOperation` (rejeu → `IGNOREE_DOUBLON`), chaque opération dans sa transaction ; règle métier violée → `EN_CONFLIT` avec motif ; erreur technique → `A_RENVOYER` |
| Gestionnaires | Cadre générique `GestionnaireOperation` ; premier type `FICHE_CLIENT_MODIFIEE` (coordonnées seulement, RG-06) |
| Changements (pull) | Flux alimenté par le référentiel (produits, clients, taxes, unités, catégories), pages de 500, filtrage par établissement du terminal, initialisation au démarrage pour les entreprises existantes |
| Numérotation (RG-04) | Plages de 500 numéros par terminal, type de pièce et année ; nouvelle plage accordée à 80 % de consommation ; préfixe `TK-C01-2026-…` |
| Réseau (ENF-02) | gzip dans les deux sens (filtre de décompression, compression des réponses) |
| Application | `LocalStore` (Dexie, D-26), clés WebCrypto non exportables, file d'envoi, `AgentSynchro` (au retour du réseau, toutes les 2 min, à la demande ; réessais exponentiels plafonnés à 5 min ; alerte 24 h, D-27) ; écrans **W-18 Terminaux**, **A-02 Appairage**, **A-17 Synchronisation** ; indicateur de la coquille relié à l'état réel |

## Critères d'acceptation

| Critère | Résultat | Preuve |
|---|---|---|
| Tests §8.8 | ✅ | `MoteurSynchronisationTest` (11 tests) : appairage et plages disjointes, exclusion INV-11, rejeu ×3 sans doublon, coupure au milieu d'un lot, deux terminaux qui cumulent, 5 000 opérations, terminal révoqué, signature invalide, conflit RG-06, pagination et alerte de plage, gzip |
| 2 000 produits en 3G < 2 min | ✅ 13,1 s | `e2e/synchronisation.spec.ts` : import de 2 000 produits, création du terminal, appairage, chargement sous limitation réseau Chromium (1,6 Mbit/s, 750 kbit/s, 562 ms) ; mesure : 6 008 produits (catalogue cumulé de plusieurs passages) reçus en 13,1 s |
| Téléphone réel | ⏳ | À faire par le porteur du projet : APK de démonstration (workflow `android`), écran Paramètres › Terminaux sur ordinateur, puis Appairage sur le téléphone en collant/saisissant le code |

Tests unitaires application : agent (accusés, conflits, révocation, pagination et plages) et stockage local, 17 tests au total.

## Écarts et reports

- **Cumul de stock entre terminaux** (§8.8) : le stock n'existe qu'à partir des lots 5 et 9 ; le test utilise un gestionnaire de test `TEST_CUMUL` qui vérifie le même mécanisme (deux terminaux, opérations entrelacées, total exact). Le test réel sera ajouté avec le stock.
- **Lecture du QR code par la caméra** : LOT 5, avec le scanner de codes-barres ; l'appairage se fait d'ici là par saisie ou collage du contenu.
- **SQLite chiffrée sur Android** : LOT 5 (D-26).
- **Onglets de la fiche produit** (Conditionnements / Prix / Stock / Champs perso, capture W-05) : la fiche reste sur une seule page ; découpage en onglets au LOT 9 avec le stock.
- **Workflow `e2e.yml` en CI** : reporté (environnement complet Keycloak + PostgreSQL en CI à monter) ; les scénarios tournent en local.
