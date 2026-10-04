# Déployer Ambawbio Suite sur Railway (mode démonstration)

Ce guide met en ligne une instance de démonstration complète, utilisable depuis un navigateur (ordinateur, tablette, téléphone) et depuis l'APK Android. Chaque fusion dans la branche `main` redéploie automatiquement : les fonctionnalités des lots suivants deviennent testables dès qu'elles sont fusionnées.

> **Démonstration uniquement.** Les comptes de démonstration ont tous le même mot de passe connu (`demo-ambawbio`) et les données sont fictives. N'y saisissez pas de vraies données de clients. La mise en production réelle (hébergement au Burkina, Q-05) fait l'objet du LOT 14.

Ce déploiement a été répété à l'identique en local avant d'être documenté : `infra/railway/docker-compose.essai.yml`, mêmes images, mêmes variables. Les 14 scénarios Playwright y passent (voir § 13).

---

## Sommaire

1. [Ce que vous allez obtenir](#1-ce-que-vous-allez-obtenir)
2. [Prérequis](#2-prérequis)
3. [Préparer les secrets](#3-préparer-les-secrets)
4. [Créer le projet et les quatre services](#4-créer-le-projet-et-les-quatre-services)
5. [Service `postgres`](#5-service-postgres)
6. [Générer les adresses publiques](#6-générer-les-adresses-publiques)
7. [Service `keycloak`](#7-service-keycloak)
8. [Service `serveur`](#8-service-serveur)
9. [Service `application`](#9-service-application)
10. [Premier démarrage et vérifications](#10-premier-démarrage-et-vérifications)
11. [Comptes de démonstration](#11-comptes-de-démonstration)
12. [Parcours de test guidé](#12-parcours-de-test-guidé)
13. [Tester automatiquement l'instance](#13-tester-automatiquement-linstance)
14. [Téléphone Android (APK)](#14-téléphone-android-apk)
15. [Mises à jour au fil des lots](#15-mises-à-jour-au-fil-des-lots)
16. [Réinitialiser la démonstration](#16-réinitialiser-la-démonstration)
17. [Coûts](#17-coûts)
18. [Dépannage](#18-dépannage)
19. [Répéter le déploiement en local](#19-répéter-le-déploiement-en-local)
20. [Récapitulatif des variables](#20-récapitulatif-des-variables)

---

## 1. Ce que vous allez obtenir

Un projet Railway avec **quatre services**, tous construits depuis le dépôt GitHub :

| Service (nom exact) | Rôle | Fichier de construction | Adresse publique |
|---|---|---|---|
| `postgres` | PostgreSQL 18 : données de l'application et de Keycloak | `infra/railway/postgres/Dockerfile` | aucune (réseau privé seulement) |
| `keycloak` | Connexion, comptes, MFA (royaume `ambawbio`, thème de la marque) | `infra/railway/keycloak/Dockerfile` | `https://keycloak-….up.railway.app` |
| `serveur` | API Spring Boot (avec données de démonstration) | `backend/Dockerfile` | `https://serveur-….up.railway.app` |
| `application` | Application Angular servie par nginx | `frontend/Dockerfile` | `https://application-….up.railway.app` ← **l'adresse à ouvrir** |

```
 Navigateur / APK ──HTTPS──► application (nginx, fichiers statiques + config.js)
        │
        ├──HTTPS──► keycloak ──réseau privé──► postgres (base « keycloak »)
        └──HTTPS──► serveur  ──réseau privé──► postgres (base « ambawbio »)
                       └──HTTPS──► keycloak (clés de signature, comptes)
```

**Important : nommez les services exactement `postgres`, `keycloak`, `serveur` et `application`.** Les variables de ce guide y font référence (`${{postgres.RAILWAY_PRIVATE_DOMAIN}}`, etc.). Si vous choisissez d'autres noms, adaptez chaque référence.

---

## 2. Prérequis

1. **Un compte Railway** (https://railway.com) avec un forfait payant (*Hobby* suffit) : l'essai gratuit limite la mémoire, et Keycloak et le serveur Java ont chacun besoin d'environ 512 Mo à 1 Go.
2. **GitHub relié à Railway** : *Account Settings → Integrations → GitHub*, puis autoriser l'accès au dépôt `RBonsa404/Ambawbio-suite.`.
3. **Le code à jour dans `main`** : Railway déploie une branche. Fusionnez d'abord les pull requests des lots (LOT 4 n° 15, LOT 5 et ce guide n° 16). Pour tester une branche non fusionnée, choisissez-la à l'étape 4.4.
4. Un **générateur de mots de passe** (votre gestionnaire de mots de passe, ou `openssl rand -hex 20` dans un terminal).

Aucun outil n'est à installer sur votre poste : tout se fait dans l'interface web de Railway.

---

## 3. Préparer les secrets

Générez **quatre** secrets et notez-les dans votre gestionnaire de mots de passe. **N'utilisez que des lettres et des chiffres** (pas d'apostrophe, de `$` ni d'espace), car certains sont insérés dans des commandes SQL au premier démarrage.

| Nom dans ce guide | Sert à | Exemple de forme |
|---|---|---|
| `MDP_PROPRIETAIRE` | Propriétaire de la base `ambawbio` (migrations Flyway) | `k3v9…` (40 caractères) |
| `MDP_APPLICATION` | Rôle `ambawbio_app` (sans BYPASSRLS) utilisé par le serveur | `p8d2…` |
| `MDP_KEYCLOAK_BD` | Utilisateur `keycloak` de la base `keycloak` | `w1m7…` |
| `SECRET_SERVEUR` | Secret du client `ambawbio-serveur` (compte de service Keycloak) | `s5q0…` |

Plus un cinquième pour la console d'administration de Keycloak : `MDP_ADMIN_KEYCLOAK`.

Dans les blocs ci-dessous, remplacez chaque `<…>` par le secret correspondant. Chaque secret n'est saisi **qu'une fois** : les autres services le reprennent par une référence (`${{postgres.AMBAWBIO_BD_MOT_DE_PASSE}}`, etc.), ce qui évite les fautes de recopie.

---

## 4. Créer le projet et les quatre services

1. Tableau de bord Railway → **New Project** → **Empty Project**. Renommez le projet `ambawbio-demo` (*Settings → Project name*).
2. Dans le projet : **Create** (ou le bouton **+**) → **GitHub Repo** → choisissez `Ambawbio-suite.`. Un service apparaît : cliquez dessus → **Settings** → renommez-le `postgres`.
3. Répétez trois fois l'étape 2 pour créer `keycloak`, `serveur` et `application` (même dépôt à chaque fois).
4. Pour **chacun des quatre services**, dans **Settings** :
   - **Source → Root Directory** : laissez **vide**. Les quatre images se construisent depuis la racine du dépôt (elles partagent `docs/design` et `infra/keycloak`).
   - **Source → Branch** : `main` (ou la branche à tester).
   - **Watch Paths** (facultatif, évite de tout reconstruire à chaque modification) :
     - `postgres` : `/infra/railway/postgres/**`
     - `keycloak` : `/infra/keycloak/**`, `/infra/railway/keycloak/**`
     - `serveur` : `/backend/**`
     - `application` : `/frontend/**`, `/docs/design/**`

Les premières constructions échouent ou redémarrent en boucle tant que les variables ne sont pas posées : c'est normal, on les corrige aux étapes suivantes.

---

## 5. Service `postgres`

### 5.1 Variables

Service `postgres` → **Variables** → **Raw Editor**, collez puis enregistrez (**Update Variables**) :

```env
RAILWAY_DOCKERFILE_PATH=/infra/railway/postgres/Dockerfile
POSTGRES_DB=ambawbio
POSTGRES_USER=ambawbio
POSTGRES_PASSWORD=<MDP_PROPRIETAIRE>
AMBAWBIO_BD_MOT_DE_PASSE=<MDP_APPLICATION>
KEYCLOAK_BD_MOT_DE_PASSE=<MDP_KEYCLOAK_BD>
```

- Le serveur crée ou met à jour lui-même le rôle `ambawbio_app` à chaque démarrage (D-39) : il fonctionne aussi avec la base PostgreSQL proposée par Railway, à condition de créer à la main la base de Keycloak.
- Au **premier** démarrage seulement (volume vide), le script `infra/railway/postgres/initialiser.sh` crée le rôle `ambawbio_app` (sans superutilisateur ni BYPASSRLS, guide §6.4) ainsi que l'utilisateur et la base `keycloak`. **Changer ces mots de passe plus tard ne modifie pas la base** : voir § 16 et § 18.

### 5.2 Volume (indispensable)

Sans volume, toutes les données disparaissent à chaque redéploiement.

Clic droit sur le service `postgres` (ou **Ctrl/Cmd + K** → *Volume*) → **Attach Volume** → chemin de montage **`/var/lib/postgresql`**.

PostgreSQL 18 range ses données dans `/var/lib/postgresql/18/docker`, un sous-dossier du volume : ne montez pas le volume ailleurs.

### 5.3 Réseau

N'ajoutez **pas** d'adresse publique à `postgres` : les autres services le joignent par le réseau privé, à l'adresse `postgres.railway.internal:5432` (variable `${{postgres.RAILWAY_PRIVATE_DOMAIN}}`).

**Vérifiez l'adresse privée réelle** : service base de données → **Settings** → section **Private Networking**. Railway fixe ce nom à la création du service ; il ne suit pas forcément le nom affiché sur le canevas (exemple constaté : `ambawbio-suite.railway.internal` pour un service affiché `postgres`). Si ce nom diffère de `postgres.railway.internal`, écrivez-le en clair dans `KC_DB_URL` (keycloak) et `AMBAWBIO_BD_URL` (serveur) à la place de `${{postgres.RAILWAY_PRIVATE_DOMAIN}}`, par exemple `jdbc:postgresql://ambawbio-suite.railway.internal:5432/ambawbio`.

Déployez (**Deploy**, ou le bandeau *Apply changes*). Dans **Deployments → View logs**, vous devez voir `database system is ready to accept connections`. Au tout premier démarrage, `CREATE ROLE` et `CREATE DATABASE` apparaissent juste avant.

---

## 6. Générer les adresses publiques

Les services se font référence par leurs adresses : générez-les **avant** de poser les variables.

Pour `keycloak`, `serveur` et `application` : **Settings → Networking → Public Networking → Generate Domain**. Indiquez le port **8080** si Railway le demande (*target port*).

Notez les trois adresses, par exemple :

- `keycloak-production-1a2b.up.railway.app`
- `serveur-production-3c4d.up.railway.app`
- `application-production-5e6f.up.railway.app`

Vous n'aurez pas à les recopier : les variables utilisent `${{keycloak.RAILWAY_PUBLIC_DOMAIN}}`, que Railway remplace automatiquement. Un domaine personnalisé (`demo.ambawbio.bf`) peut être ajouté plus tard au même endroit (*Custom Domain*).

---

## 7. Service `keycloak`

### 7.1 Variables

```env
RAILWAY_DOCKERFILE_PATH=/infra/railway/keycloak/Dockerfile
PORT=8080
KC_DB_URL=jdbc:postgresql://${{postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=${{postgres.KEYCLOAK_BD_MOT_DE_PASSE}}
KC_HOSTNAME=https://${{RAILWAY_PUBLIC_DOMAIN}}
KC_BOOTSTRAP_ADMIN_USERNAME=admin
KC_BOOTSTRAP_ADMIN_PASSWORD=<MDP_ADMIN_KEYCLOAK>
AMBAWBIO_URL_APPLICATION=https://${{application.RAILWAY_PUBLIC_DOMAIN}}
AMBAWBIO_KEYCLOAK_SECRET=<SECRET_SERVEUR>
JAVA_OPTS_KC_HEAP=-XX:MaxRAMPercentage=70
```

À quoi servent ces variables :

- `KC_HOSTNAME` fixe l'adresse publique, qui figure dans les jetons (champ `iss`). Elle doit être **exactement** celle que verra le navigateur.
- L'image intègre le royaume de démonstration (`infra/keycloak/realm-ambawbio.json`). À la construction, `http://localhost:4200` y est remplacé par `${AMBAWBIO_URL_APPLICATION}` et le secret du client serveur par `${AMBAWBIO_KEYCLOAK_SECRET}`. Keycloak résout ces deux valeurs à l'import.
- Les adresses `https://localhost` et `http://localhost` restent autorisées pour l'APK Android (Capacitor).
- `KC_BOOTSTRAP_ADMIN_PASSWORD` est le mot de passe de la console d'administration, `https://<keycloak>/admin`, compte `admin`. Il ne sert qu'à créer ce compte au premier démarrage.

### 7.2 Vérifier

Logs attendus : `Keycloak 26.8… started`, puis `Realm 'ambawbio' imported`.

Ouvrez `https://<keycloak>/realms/ambawbio/.well-known/openid-configuration` : un document JSON doit s'afficher, et son champ `issuer` doit valoir `https://<keycloak>/realms/ambawbio`.

> **Le royaume n'est importé qu'une fois** (base `keycloak` vide). Si vous modifiez ensuite `AMBAWBIO_URL_APPLICATION` ou `AMBAWBIO_KEYCLOAK_SECRET`, corrigez la valeur dans la console (*Clients → ambawbio-web → Valid redirect URIs / Web origins* ; *Clients → ambawbio-serveur → Credentials*) ou réinitialisez (§ 16).

---

## 8. Service `serveur`

### 8.1 Variables

```env
RAILWAY_DOCKERFILE_PATH=/backend/Dockerfile
PORT=8080
SPRING_PROFILES_ACTIVE=prod,demo
AMBAWBIO_BD_URL=jdbc:postgresql://${{postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/ambawbio
AMBAWBIO_BD_UTILISATEUR=ambawbio_app
AMBAWBIO_BD_MOT_DE_PASSE=${{postgres.AMBAWBIO_BD_MOT_DE_PASSE}}
AMBAWBIO_BD_PROPRIETAIRE=ambawbio
AMBAWBIO_BD_PROPRIETAIRE_MOT_DE_PASSE=${{postgres.POSTGRES_PASSWORD}}
AMBAWBIO_KEYCLOAK_EMETTEUR=https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}/realms/ambawbio
AMBAWBIO_KEYCLOAK_JWKS=https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}/realms/ambawbio/protocol/openid-connect/certs
AMBAWBIO_KEYCLOAK_URL=https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}
AMBAWBIO_KEYCLOAK_SECRET=${{keycloak.AMBAWBIO_KEYCLOAK_SECRET}}
AMBAWBIO_CORS_ORIGINES=https://${{application.RAILWAY_PUBLIC_DOMAIN}},https://localhost,http://localhost
```

À quoi servent ces variables :

- **`prod,demo`** : `prod` coupe la documentation Swagger et les détails de santé. `demo` charge, au premier démarrage, les deux entreprises de démonstration (Quincaillerie Wend-Panga et Pharmacie du Progrès), leurs utilisateurs (mêmes identifiants que dans Keycloak) et le catalogue.
- **Deux comptes de base** : Flyway crée les tables avec le propriétaire (`ambawbio`). L'application travaille ensuite avec `ambawbio_app`, soumis aux politiques RLS, ce qui maintient l'isolation entre entreprises.
- **Keycloak joint par son adresse publique** (HTTPS) : cela fonctionne quel que soit le type de réseau privé du projet (IPv4/IPv6).
- **`AMBAWBIO_CORS_ORIGINES`** : l'application web et l'APK (`https://localhost`) appellent l'API depuis une autre origine.

### 8.2 Vérifier

Logs attendus :

- `Successfully applied … migrations`, puis `Started AmbawbioApplication` ;
- `Données de démonstration créées : Quincaillerie Wend-Panga` ;
- `Catalogue de démonstration créé`.

Ouvrez `https://<serveur>/actuator/health` : la page doit afficher `{"status":"UP"…}`.

---

## 9. Service `application`

### 9.1 Variables

```env
RAILWAY_DOCKERFILE_PATH=/frontend/Dockerfile
PORT=8080
AMBAWBIO_API_URL=https://${{serveur.RAILWAY_PUBLIC_DOMAIN}}/api
AMBAWBIO_KEYCLOAK_URL=https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}
```

Au démarrage du conteneur, `frontend/docker/40-configuration.sh` écrit `config.js` à partir de ces deux variables (décision D-34). La même image sert donc partout : changer d'adresse ne demande qu'un redémarrage, pas de reconstruction.

### 9.2 Vérifier

- Ouvrez `https://<application>/config.js` : la page doit afficher vos deux adresses.
- Logs attendus : `config.js : api=https://… keycloak=https://…`.

---

## 10. Premier démarrage et vérifications

Ordre conseillé (si un service a démarré trop tôt, utilisez **Redeploy** dans *Deployments*) :

1. `postgres`
2. `keycloak`
3. `serveur`
4. `application`

Liste de contrôle :

| Vérification | Adresse / action | Attendu |
|---|---|---|
| Base | logs `postgres` | `ready to accept connections` |
| Keycloak | `https://<keycloak>/realms/ambawbio` | JSON avec `"realm":"ambawbio"` |
| Serveur | `https://<serveur>/actuator/health` | `"status":"UP"` |
| Configuration | `https://<application>/config.js` | les deux adresses `https://` |
| Connexion | `https://<application>` | page de connexion aux couleurs Ambawbio |
| Session | se connecter avec `aminata` / `demo-ambawbio` | « Bonjour Aminata », entreprise Quincaillerie Wend-Panga |

---

## 11. Comptes de démonstration

Mot de passe commun : **`demo-ambawbio`**.

| Identifiant | Personne | Entreprise | Rôle | Pour tester |
|---|---|---|---|---|
| `aminata` | Aminata Zongo | Quincaillerie Wend-Panga | Gérante | **Le parcours complet** : caisses, terminaux, caisse, produits, import, validation d'écart |
| `moussa` | Moussa Sawadogo | Quincaillerie Wend-Panga | Gérant + administrateur | Utilisateurs, rôles ; **MFA demandée** (application d'authentification) |
| `awa` | Awa Kaboré | Quincaillerie Wend-Panga | Caissière (siège uniquement) | La caisse sans droit de remise ni de paramétrage |
| `mariam` | Mariam Ilboudo | Quincaillerie Wend-Panga | Comptable | **MFA obligatoire** : configuration de l'OTP à la première connexion |
| `issouf` | Issouf Ouédraogo | Quincaillerie Wend-Panga | Magasinier | Menus réduits (le stock arrive au LOT 9) |
| `boukary` | Boukary Compaoré | Quincaillerie Wend-Panga | Commercial | Menus réduits (ventes au LOT 8) |
| `salimata` | Salimata Traoré | Pharmacie du Progrès | Administratrice | **Isolation** : ne voit rien de la quincaillerie ; MFA demandée |
| `editeur` | Équipe Ambawbio | — (plateforme) | Éditeur | Administration de la plateforme (console éditeur au LOT 11) |

Pour la MFA, installez une application TOTP (Google Authenticator, Aegis, FreeOTP…) et scannez le QR code proposé à la connexion.

---

## 12. Parcours de test guidé

Faites ce parcours de préférence sur un ordinateur (ou une tablette) avec **Chrome** ou **Edge**.

### 12.1 Référentiels (LOT 2–3)

1. Connectez-vous avec `aminata`. Menu **Produits** : la liste de démonstration s'affiche (Ciment CPJ 45, Riz 25 kg…).
2. **Nouveau produit** : code `TEST-1`, nom « Pâte d'arachide 1 kg », prix `1750` → **Enregistrer**. Cherchez `pate` (sans accent) : le produit est retrouvé.
3. Menu **Import de données** → **J'ai déjà mon fichier** → importez un CSV séparé par des points-virgules, avec les colonnes `code;nom;prix_vente;taxe;code_barre`. Le modèle est téléchargeable dans l'assistant.

### 12.2 Créer une caisse et votre code PIN de responsable (LOT 5, W-13)

1. Menu **Caisses** → **Nouvelle caisse** : code `CAISSE1`, nom « Caisse comptoir », établissement, seuil d'écart `500` → **Créer la caisse**.
2. En bas de page, **Mon code PIN de responsable** : choisissez 6 chiffres (par exemple `482916`) → **Enregistrer**.

### 12.3 Faire de ce navigateur un terminal de caisse (LOT 4, W-18 et A-02)

1. Menu **Terminaux** : nom « Caisse comptoir » → **Enregistrer et afficher le QR code**. Notez l'identifiant (petite ligne grise) et le code `XXXX-XXXX`.
2. Ouvrez l'adresse `https://<application>/appairage`. Collez l'identifiant, saisissez le code → **Appairer**. Le chargement initial affiche le nombre de produits reçus.
3. **Synchronisation** : terminal, plages de numéros (TK, FA, AV) et dernière synchronisation.

Sur un téléphone, on peut appairer en collant le contenu du QR code, `{"t":"…","c":"…"}`. La lecture du QR code par la caméra arrive avec l'APK, au fil des lots suivants.

### 12.4 Vendre (A-05 à A-12)

1. Menu **Caisse** → choisissez « Caisse comptoir », fonds de caisse `10000` → **Ouvrir la caisse**.
2. Touchez des tuiles produits, ou tapez un code (`CIM-50`, `RIZ-25`, `SAV-400`) puis **Entrée**. Une douchette USB fonctionne de la même manière.
3. **Modifier** une ligne : choix du conditionnement, quantité, remise (limitée au maximum de la caisse, et réservée aux utilisateurs qui en ont le droit).
4. **Encaisser** → **Espèces** → touchez un billet proposé (`35 000`, etc.) → le rendu s'affiche → **Valider le paiement**.
5. L'écran « Vente enregistrée » affiche le numéro `TK-C01-2026-000001`. Dans un navigateur, **Réimprimer** ouvre l'impression du système (imprimante du poste). Sur Android, le ticket part vers l'imprimante thermique Bluetooth.
6. Essayez aussi **Mobile Money** (référence du SMS saisie en secours ; la vérification auprès de l'opérateur arrive au LOT 7), **Mixte** et **Carte**.

### 12.5 Couper le réseau (F-POS-06)

1. Outils de développement de Chrome (**F12**) → onglet **Réseau** (*Network*) → choisissez **Hors connexion** (*Offline*). Ou bien coupez le Wi-Fi d'une tablette.
2. Faites deux ou trois ventes. L'indicateur en haut de l'écran passe à « Hors-ligne — N en attente ».
3. Rétablissez le réseau. L'envoi part tout seul (ou utilisez **Synchronisation → Synchroniser maintenant**) et le compteur « En attente d'envoi » revient à 0.
4. Rechargez la page **hors connexion** (avec le navigateur appairé comme terminal) : l'application redémarre sans réseau grâce au service worker et aux jetons conservés (D-29).

### 12.6 Retour de marchandise (A-13)

**Caisse → Retour** → choisissez un ticket → quantités retournées → **Rembourser en espèces**. Un numéro `AV-C01-…` est attribué. Il est impossible de retourner plus que ce qui a été vendu, même en plusieurs fois.

### 12.7 Clôturer et faire valider un écart (A-15, A-16 ; RG-09)

1. **Caisse → Clôturer** : saisissez les espèces comptées **sans voir** le montant attendu (comptage à l'aveugle).
2. Saisissez volontairement un montant faux : l'écart dépasse le seuil, une validation par un responsable est demandée.
3. Identifiant `aminata`, code PIN choisi au § 12.2, raison → **Valider l'écart**.
4. Menu **Caisses** : la session apparaît dans « Sessions récentes ». **Voir** ouvre le **rapport Z** (ventes, retours, ventilation espèces / Mobile Money / carte, taxes, écart).

### 12.8 Factures et avoirs (LOT 6, W-10)

1. Menu **Factures et avoirs** → **Nouvelle facture** : client « Bâtir Faso SARL (démo) », ajoutez « Ciment CPJ 45 » (quantité 10) et une ligne libre (« Livraison », 15 000) → **Enregistrer le brouillon**.
2. **Valider la facture** : numéro `FA-2026-000001`, badge « Simulée — sans valeur fiscale » au bout d'une seconde ; l'aperçu PDF montre le QR code et le filigrane de démonstration. **Télécharger le PDF**.
3. Certification différée : dans le bandeau « Mode démonstration », passez le service en **indisponible**, validez une autre facture → badge « En file de certification » et compteur en haut. Repassez en **disponible** → **Relancer la certification**.
4. **Créer un avoir** sur la première facture : motif, quantités → **Avoir partiel** (`AV-2026-000001`). Il est impossible d'annuler plus que le reste.
5. En caisse : **Encaisser** → cochez « Le client demande une facture certifiée », choisissez le client → l'écran de confirmation affiche « Facture FA-C01-2026-000001 en attente de certification » ; elle apparaît ensuite dans **Factures et avoirs**.

Une facture validée ne peut plus être modifiée ni supprimée (même en base) : on la corrige par un avoir.

### 12.9 Isolation entre entreprises (LOT 1)

Déconnectez-vous (avatar en haut à droite), puis connectez-vous avec `salimata`. Vous êtes dans la Pharmacie du Progrès : aucune donnée de la quincaillerie n'est visible.

---

## 13. Tester automatiquement l'instance

Les scénarios Playwright du dépôt peuvent viser votre instance Railway (sur un poste où Node 24 est installé) :

```bash
cd frontend
npm ci
npx playwright install chromium
AMBAWBIO_E2E_URL=https://<application> npx playwright test
```

Attendu : 15 scénarios réussis, 3 ignorés (scénarios réservés au bureau). Les scénarios créent des données de test (produits `TST-…`, caisses `CE…`, terminaux) : réinitialisez ensuite si vous voulez une démonstration propre (§ 16).

---

## 14. Téléphone Android (APK)

1. GitHub → **Actions** → workflow **android** → **Run workflow**. Renseignez :
   - `api_url` : `https://<serveur>/api`
   - `keycloak_url` : `https://<keycloak>`
2. À la fin (environ 10 minutes), téléchargez l'artefact `ambawbio-suite-demo-apk` (fichier zip contenant l'APK).
3. Sur le téléphone : autorisez l'installation depuis des sources inconnues, installez l'APK et ouvrez **Ambawbio Suite**.
4. Connectez-vous (par exemple avec `awa`), choisissez un code PIN de déverrouillage, puis appairez le téléphone comme au § 12.3. Utilisez le **Nouveau QR code** de l'écran Terminaux sur l'ordinateur et collez le contenu sur le téléphone.
5. Allumez l'imprimante thermique Bluetooth, puis **Synchronisation → Imprimante de tickets → Choisir une imprimante Bluetooth** (58 ou 80 mm). En caisse, le bouton caméra lit les codes-barres.

L'APK embarque les adresses saisies à l'étape 1 : relancez le workflow si les adresses Railway changent.

---

## 15. Mises à jour au fil des lots

- Chaque fusion dans `main` reconstruit et redéploie les services concernés (selon les *Watch Paths*). Railway attend que la nouvelle version démarre avant de couper l'ancienne.
- Pour ne déployer qu'une version qui a passé la CI : **Settings → Deploy → Wait for CI** (option *Check Suites*).
- Les migrations de base s'appliquent toutes seules au démarrage du serveur (Flyway). Les données existantes sont conservées.
- **Nouveaux comptes ou rôles dans le royaume Keycloak** (rare) : l'import ne se refait pas sur une base existante. Ajoutez-les dans la console Keycloak, ou réinitialisez la démonstration (§ 16).
- Chaque rapport de lot (`docs/lots/LOT-xx.md`) indique ce qui devient testable dans l'interface.

---

## 16. Réinitialiser la démonstration

Pour repartir de données neuves :

1. Service `postgres` → **Volume** → **Wipe Volume** (ou supprimez le volume puis recréez-le sur `/var/lib/postgresql`).
2. Redéployez dans l'ordre : `postgres`, puis `keycloak`, `serveur` et enfin `application`.

Le rôle applicatif, la base Keycloak, le royaume et les données de démonstration sont recréés. Les terminaux déjà appairés ne fonctionnent plus (ils sont inconnus de la nouvelle base). Dans le navigateur, effacez les données du site (*Paramètres → Confidentialité → Données de site*) avant de les appairer à nouveau.

---

## 17. Coûts

Railway facture la mémoire et le processeur réellement consommés, plus le stockage du volume. Ordre de grandeur pour cette démonstration, au repos ou peu utilisée :

| Service | Mémoire typique |
|---|---|
| `postgres` | 50 à 150 Mo |
| `keycloak` | 400 à 700 Mo |
| `serveur` | 300 à 600 Mo |
| `application` | moins de 20 Mo |

Comptez quelques dollars à une quinzaine de dollars par mois selon l'usage. Le forfait Hobby inclut un crédit mensuel. Consultez **Usage** dans le projet et fixez une limite de dépense dans *Workspace Settings → Usage → Usage limits*.

Pour réduire les coûts quand vous ne testez pas, utilisez **Settings → Serverless** sur `application` et `serveur` (mise en veille sans trafic). Le réveil prend quelques secondes ; c'est déconseillé pour Keycloak.

---

## 18. Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| La construction échoue : « Dockerfile does not exist » | `RAILWAY_DOCKERFILE_PATH` absent, ou *Root Directory* rempli | Vérifier la variable (avec le `/` initial) ; vider *Root Directory* |
| `postgres` : `initdb: directory … exists but is not empty` | Volume monté au mauvais endroit | Monter le volume sur `/var/lib/postgresql` exactement |
| `serveur` : `password authentication failed for user "ambawbio_app"` | Version antérieure au correctif D-39 et rôle absent ou mot de passe changé | Depuis D-39, le serveur crée ou met à jour ce rôle lui-même au démarrage : redéployez `serveur`. Vérifiez que `AMBAWBIO_BD_MOT_DE_PASSE` n'est pas vide et que `AMBAWBIO_BD_PROPRIETAIRE_MOT_DE_PASSE` est bien le mot de passe de `POSTGRES_PASSWORD` |
| `serveur` : `password authentication failed for user "ambawbio"` | Mot de passe du propriétaire faux | `AMBAWBIO_BD_PROPRIETAIRE_MOT_DE_PASSE` = `${{postgres.POSTGRES_PASSWORD}}` ; si `POSTGRES_PASSWORD` a changé après le premier démarrage, remettez l'ancien ou réinitialisez (§ 16) |
| `serveur` : `Connection to localhost:5432 refused` | `AMBAWBIO_BD_URL` absente, ou référence `${{...}}` vers un service ou une variable inexistants (adresse vide, remplacée par `localhost`) | Écrivez l'adresse en clair : `jdbc:postgresql://<adresse privée>:5432/ambawbio` (§ 5.3) |
| `serveur` ou `keycloak` : `UnknownHostException: xxx.railway.internal` | Adresse privée différente du nom du service, ou base arrêtée | Relevez l'adresse dans base de données → Settings → Private Networking (§ 5.3) ; vérifiez que la base affiche `ready to accept connections` |
| `keycloak` : `password authentication failed for user "keycloak"` | Idem pour `KEYCLOAK_BD_MOT_DE_PASSE` | Idem |
| Page blanche, la console du navigateur signale `config.js` | Variables de `application` manquantes : le conteneur refuse de démarrer | Renseigner `AMBAWBIO_API_URL` et `AMBAWBIO_KEYCLOAK_URL` ; voir les logs |
| Keycloak affiche « Invalid parameter: redirect_uri » | `AMBAWBIO_URL_APPLICATION` faux au moment de l'import | Console Keycloak → *Clients → ambawbio-web* : ajouter `https://<application>/*` dans *Valid redirect URIs* et `https://<application>` dans *Web origins* |
| Connexion réussie puis erreurs 401 partout | `AMBAWBIO_KEYCLOAK_EMETTEUR` différent du champ `issuer` de Keycloak | Comparer avec `/.well-known/openid-configuration` (même schéma `https`, même domaine) |
| Erreur CORS sur `/api/...` dans la console | Origine absente de `AMBAWBIO_CORS_ORIGINES` | Ajouter `https://<application>` (sans `/` final) et redéployer `serveur` |
| « Le serveur ne répond pas » | `serveur` arrêté ou en construction | Logs de `serveur` ; `/actuator/health` |
| Création d'utilisateur : erreur d'annuaire | Secret du compte de service différent | Même valeur de `AMBAWBIO_KEYCLOAK_SECRET` dans `keycloak` et `serveur`, et dans la console Keycloak (*ambawbio-serveur → Credentials*) |
| Le terminal reste « en attente d'envoi » | Terminal révoqué, base réinitialisée, ou session expirée | Écran **Synchronisation** (message d'erreur) ; se reconnecter ; appairer à nouveau |
| Le service redémarre : « OutOfMemory » | Mémoire limitée par le forfait | Forfait Hobby ou supérieur ; vérifier *Settings → Resources* |

Pour lire les logs : service → **Deployments** → déploiement actif → **View logs** (*Build logs* pour la construction, *Deploy logs* pour l'exécution).

---

## 19. Répéter le déploiement en local

Avant de modifier un Dockerfile ou des variables, on peut tout répéter sur un poste équipé de Docker :

```bash
docker compose -f infra/railway/docker-compose.essai.yml up --build
# Application : http://localhost:9000   Keycloak : http://localhost:9180   Serveur : http://localhost:9080
cd frontend && AMBAWBIO_E2E_URL=http://localhost:9000 npx playwright test
```

---

## 20. Récapitulatif des variables

| Service | Variable | Valeur |
|---|---|---|
| tous sauf `postgres` | `PORT` | `8080` |
| `postgres` | `RAILWAY_DOCKERFILE_PATH` | `/infra/railway/postgres/Dockerfile` |
| | `POSTGRES_DB` / `POSTGRES_USER` | `ambawbio` / `ambawbio` |
| | `POSTGRES_PASSWORD` | secret (propriétaire) |
| | `AMBAWBIO_BD_MOT_DE_PASSE` | secret (rôle applicatif) |
| | `KEYCLOAK_BD_MOT_DE_PASSE` | secret (Keycloak) |
| `keycloak` | `RAILWAY_DOCKERFILE_PATH` | `/infra/railway/keycloak/Dockerfile` |
| | `KC_DB_URL` | `jdbc:postgresql://${{postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/keycloak` |
| | `KC_DB_USERNAME` / `KC_DB_PASSWORD` | `keycloak` / `${{postgres.KEYCLOAK_BD_MOT_DE_PASSE}}` |
| | `KC_HOSTNAME` | `https://${{RAILWAY_PUBLIC_DOMAIN}}` |
| | `KC_BOOTSTRAP_ADMIN_USERNAME` / `…_PASSWORD` | `admin` / secret |
| | `AMBAWBIO_URL_APPLICATION` | `https://${{application.RAILWAY_PUBLIC_DOMAIN}}` |
| | `AMBAWBIO_KEYCLOAK_SECRET` | secret (client serveur) |
| `serveur` | `RAILWAY_DOCKERFILE_PATH` | `/backend/Dockerfile` |
| | `SPRING_PROFILES_ACTIVE` | `prod,demo` |
| | `AMBAWBIO_BD_URL` | `jdbc:postgresql://${{postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/ambawbio` |
| | `AMBAWBIO_BD_UTILISATEUR` / `…_MOT_DE_PASSE` | `ambawbio_app` / `${{postgres.AMBAWBIO_BD_MOT_DE_PASSE}}` |
| | `AMBAWBIO_BD_PROPRIETAIRE` / `…_MOT_DE_PASSE` | `ambawbio` / `${{postgres.POSTGRES_PASSWORD}}` |
| | `AMBAWBIO_KEYCLOAK_EMETTEUR` | `https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}/realms/ambawbio` |
| | `AMBAWBIO_KEYCLOAK_JWKS` | `…/realms/ambawbio/protocol/openid-connect/certs` |
| | `AMBAWBIO_KEYCLOAK_URL` | `https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}` |
| | `AMBAWBIO_KEYCLOAK_SECRET` | `${{keycloak.AMBAWBIO_KEYCLOAK_SECRET}}` |
| | `AMBAWBIO_CORS_ORIGINES` | `https://${{application.RAILWAY_PUBLIC_DOMAIN}},https://localhost,http://localhost` |
| `application` | `RAILWAY_DOCKERFILE_PATH` | `/frontend/Dockerfile` |
| | `AMBAWBIO_API_URL` | `https://${{serveur.RAILWAY_PUBLIC_DOMAIN}}/api` |
| | `AMBAWBIO_KEYCLOAK_URL` | `https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}` |
