# LOT 5 — Caisse hors-ligne

**Références** : F-POS-01 à 07 ; UC-POS-01 à 03, 05 à 08 ; SD-02, SD-06, SD-08 (partie caisse) ; RG-09, 15 ; INV-14.
**Acceptation** : vente complète en moins de 20 secondes pour 5 articles ; 7 jours de ventes hors-ligne simulées puis synchronisées sans perte ; impression testée sur une imprimante réelle.
**Statut** : terminé, en attente de validation (impression sur imprimante réelle et APK à tester par le porteur du projet).

## Réalisation

| Élément | Contenu |
|---|---|
| Base (`pos.*`) | `point_de_vente` (seuil d'écart, comptage à l'aveugle, remise maximale, vente sans stock), `session_caisse` (index unique partiel : une session OUVERTE par point de vente, INV-14), `vente`, `ligne_vente`, `encaissement` (sans UPDATE ni DELETE, INV-01 ; numéro unique par société, type et année, INV-02) ; `socle.code_pin` (code PIN de responsable) |
| Opérations synchronisées | `SESSION_OUVERTE`, `VENTE_ENREGISTREE`, `RETOUR_ENREGISTRE`, `SESSION_CLOTUREE`. Le serveur refait le calcul de chaque ligne et refuse tout écart (`CALCUL_DIVERGENT`). Il contrôle aussi : le numéro dans la plage du terminal ; les encaissements égaux au total ; la remise (droit `pos:remiser`, maximum de la caisse) ; le droit de l'utilisateur sur l'établissement ; un retour jamais supérieur à la quantité vendue, retours précédents déduits. Le caissier est lu dans la charge signée |
| Session concurrente | Deuxième ouverture sur la même caisse : la session est conservée en `EN_CONFLIT` avec alerte dans le journal d'audit, ses ventes ne sont pas perdues (D-30) |
| Clôture (RG-09) | Espèces attendues = fonds + espèces des ventes − espèces remboursées, calculées de la même façon sur le terminal et sur le serveur ; au-delà du seuil, statut `ECART_A_VALIDER` ; validation par un responsable (droit `pos:valider-ecart`) avec son code PIN de 6 chiffres vérifié par le serveur (PBKDF2, 5 essais puis blocage 15 min, D-31) |
| Événements | `VenteEnregistree`, `RetourEnregistre`, `SessionCloturee` (registre Spring Modulith) pour le stock (LOT 9), la comptabilité (LOT 10) et la facture certifiée (LOT 6) |
| API | `/api/v1/pos/points-de-vente`, `/sessions`, `/sessions/{id}/rapport` (rapport Z), `/sessions/{id}/validation-ecart` ; `PUT /api/v1/socle/moi/code-pin` |
| Terminal | Service `Caisse` : catalogue local, recherche sans accents, codes-barres, conditionnements, remise, numérotation dans la plage (`consommerNumero`), pièces locales chiffrées, retours, clôture. Calcul identique au serveur en arithmétique entière (`calcul-vente.ts`) |
| Écrans | A-05 ouverture, A-06 vente (téléphone et tablette), A-07 ligne, A-09 pavé de caisse et rendu, A-11 Mobile Money de secours (référence du SMS), mode mixte, carte, A-12 vente enregistrée (retour automatique après 8 s), A-13 retour, A-15 clôture à l'aveugle, A-16 validation d'écart ; W-13 Caisses (caisses, sessions récentes, rapport Z, validation, code PIN) ; choix de l'imprimante (écran Synchronisation) |
| Impression (F-POS-05) | Encodeur ESC/POS (page de code PC858, accents français, gras, double taille, coupe), tickets 58 mm (32 colonnes) et 80 mm (48 colonnes) ; imprimante Bluetooth LE sur Android, impression système dans le navigateur |
| Lecture de codes | Caméra (ML Kit, hors-ligne) sur Android ; douchette USB ou Bluetooth (saisie clavier rapide + Entrée) partout |
| Stockage local chiffré | AES-GCM 256 sur les charges des opérations et les données des entités ; clé dans le Keystore (Android) ou clé WebCrypto non exportable (navigateur) (D-33, remplace la SQLite chiffrée annoncée en D-26) |
| Démarrage hors-ligne | Jeton hors-ligne Keycloak (`offline_access`) conservé sur les terminaux, contexte utilisateur en cache, service worker pour le navigateur, verrouillage par code PIN au lieu de la déconnexion (D-29) |
| Déploiement | Images Docker (serveur, application, Keycloak, PostgreSQL), configuration lue à l'exécution (`config.js`, D-34), tutoriel Railway complet (`docs/deploiement/railway.md`), répétition locale (`infra/railway/docker-compose.essai.yml`), profil `demo` |

## Critères d'acceptation

| Critère | Résultat | Preuve |
|---|---|---|
| Vente de 5 articles < 20 s | ✅ | `e2e/caisse.spec.ts` : 5 codes saisis, encaissement espèces par raccourci de billet, écran « Vente enregistrée » ; mesuré **0,6 s** (automate ; un caissier ajoute le temps de ses gestes) |
| 7 jours hors-ligne sans perte | ✅ | `CaisseHorsLigneTest.septJoursDeVentesHorsLignePuisSynchronisesSansPerteNiTrou` : 7 sessions, 1 050 ventes, 1 064 opérations envoyées par lots ; total exact, numérotation continue, 7 sessions clôturées ; dans le navigateur, ventes faites hors connexion puis envoyées au retour du réseau (E2E) |
| Imprimante réelle | ⏳ | À faire par le porteur du projet : APK (workflow **android**), imprimante thermique Bluetooth LE 58 ou 80 mm |

Autres preuves :

- **Tests serveur** : 7 tests de caisse (calcul, SD-02 avec rejeu, numéro hors plage, calcul faussé, remise excessive, INV-14, SD-08, RG-09 avec code PIN) et le moteur LOT 4, mis à jour pour les plages de 2 000 tickets.
- **Tests application** : 26 tests unitaires, dont calcul (mêmes valeurs que le serveur), ticket ESC/POS et chiffrement au repos.
- **E2E** : 14 scénarios réussis sur les images de production (répétition Railway), téléphone et bureau.

## Écarts et reports

- **Stock** (RG-15 « vente avec stock insuffisant ») : le paramètre existe sur la caisse ; l'alerte et le blocage arrivent avec le stock (LOT 9), qui consommera `VenteEnregistree`.
- **Facture certifiée demandée en caisse** : la demande est enregistrée sur la vente et portée par l'événement ; l'établissement et la mise en file de certification arrivent au LOT 6.
- **Mobile Money** : saisie de secours de la référence du SMS seulement ; demande de paiement et vérification par l'agrégateur au LOT 7 (A-10).
- **Validation d'écart hors-ligne** : elle exige le réseau, car le code PIN est vérifié par le serveur et jamais stocké sur le terminal (D-31). Sans réseau, la session reste « écart à valider » et peut être validée depuis le bureau.
- **Ouverture et clôture en ligne** (`POST /pos/sessions` du catalogue §12) : elles passent par les opérations synchronisées, en ligne comme hors-ligne. Un seul chemin.
- **Textes des écrans de caisse** : en français directement dans les gabarits (pas encore dans `fr.json`). Externalisation au LOT 13, comme les composants partagés.
- **Changement de caissier par code PIN sur le même terminal** (D-03) : à faire avec la gestion des utilisateurs du terminal (LOT 13). Aujourd'hui, le caissier est l'utilisateur connecté.
- **Lecture du QR d'appairage par la caméra** : le service caméra existe ; le branchement sur l'écran d'appairage se fera au LOT 13.
- **Workflow `e2e.yml` en CI** : toujours reporté ; la répétition Railway permet de lancer toute la suite sur un environnement déployé.
