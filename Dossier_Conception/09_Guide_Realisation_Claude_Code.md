# Ambawbio Suite — Guide de réalisation pour Claude Code

**Référence** : AMB-CONC-09 · **Version** : 1.0 · **Date** : octobre 2026 · **Porteur du projet** : Rachid Bonsa
**Destinataire** : Claude Code (agent de développement), et toute personne qui développe Ambawbio Suite.

> Ce guide explique comment réaliser Ambawbio Suite de A à Z, de façon professionnelle, jusqu'à une version prête à être utilisée par des entreprises. Il condense les huit livrables du dossier de conception (AMB-CONC-01 à 08). En cas de doute, ces livrables font foi. Le design (logo, identité, maquettes, jetons de design) est produit séparément par Claude Design (AMB-CONC-10) et doit être intégré tel quel.

---

## Table des matières

0. Comment utiliser ce guide
1. Le projet en une page
2. Fichier CLAUDE.md prêt à copier
3. Règles non négociables
4. Pile technique et versions
5. Structure du dépôt
6. Back-end : organisation et conventions
7. Base de données
8. Moteur hors-ligne et synchronisation (spécification)
9. Client Angular et application Android
10. Intégrations externes et simulateurs
11. Règles métier, calculs et comptabilisation
12. Catalogue de l'API du MVP
13. Tests et qualité
14. CI/CD, environnements et exploitation
15. Feuille de route par lots (de A à Z)
16. Définition du « terminé » et listes de contrôle
17. Questions ouvertes et points bloquants connus
18. Annexes : référentiel des identifiants, glossaire

---

## 0. Comment utiliser ce guide

### 0.1 Mise en place

1. Crée le dépôt GitHub `ambawbio-suite` (privé).
2. Copie ce guide dans `docs/conception/AMB-CONC-09-guide-claude-code.md`, et les livrables Word et les diagrammes (archives PlantUML) dans `docs/conception/`.
3. Crée `CLAUDE.md` à la racine du dépôt avec le contenu de la section 2. Claude Code le lit à chaque session.
4. Ajoute dans `docs/design/` les livrables de Claude Design : logo, jetons de design (`tokens.json`), configuration Tailwind, maquettes.

### 0.2 Manière de travailler (obligatoire)

Pour chaque lot de la feuille de route (section 15) :

1. **Lire** : les sections du guide citées par le lot, les cas d'utilisation (UC), les scénarios de séquence (SD) et les règles (RG, INV) référencés.
2. **Planifier** : écrire un plan court dans `docs/lots/LOT-xx.md` : tâches, fichiers touchés, tests prévus, risques. Le présenter au porteur du projet si le lot touche à la conformité fiscale, à la sécurité ou au modèle de données.
3. **Tester d'abord les règles** : pour toute règle de gestion ou calcul (fiscalité, numérotation, stock, comptabilité), écrire le test avant le code.
4. **Implémenter par petites tranches verticales** : base de données → domaine → service → API → écran, une fonctionnalité à la fois. Chaque tranche se termine par des tests au vert.
5. **Vérifier** : `./mvnw verify`, `npm run lint && npm test`, tests de bout en bout du lot, vérification des frontières de modules. Ne jamais annoncer « terminé » sans avoir exécuté ces commandes.
6. **Documenter** : mettre à jour l'OpenAPI, le `CHANGELOG.md`, et le fichier du lot (ce qui est fait, ce qui reste, écarts par rapport au plan).
7. **Committer** : messages au format Conventional Commits (`feat(pos): enregistrer une vente hors-ligne`), une branche par lot, pull request avec la liste de contrôle de la section 16.

### 0.3 Ce que tu ne dois jamais faire

- Inventer une spécification officielle (format FEC de la DGI, API d'un opérateur Mobile Money, barème fiscal). Utilise le simulateur et l'interface (port), marque le point dans `docs/QUESTIONS.md` et continue.
- Introduire un microservice, un courtier de messages (Kafka, RabbitMQ), un autre langage serveur ou une autre base de données.
- Stocker un montant en `double` ou `float`.
- Contourner l'isolation multi-tenant, même dans un test « temporaire ».
- Désactiver un test pour faire passer la CI.
- Ajouter une dépendance sans justification dans la pull request.
- Écrire des secrets dans le dépôt.

### 0.4 Quand demander au porteur du projet

Demande avant d'agir si : une règle métier est ambiguë ; un choix change le modèle de données publié ; une fonctionnalité sort de la matrice MoSCoW ; une décision a un impact légal ou financier. Sinon, choisis l'option la plus standard et la plus recommandée, et consigne-la dans `docs/DECISIONS.md` (format ADR court).

---

## 1. Le projet en une page

**Ambawbio Suite** (« celui qui connaît demain ») est une suite de gestion d'entreprise intégrée (ERP tout-en-un) pour le Burkina Faso, inspirée d'Odoo. Elle compte huit piliers sur une base de données unique : Ventes & Relation client, Finance & Comptabilité, Opérations & Logistique, Ressources humaines, Site Web & E-commerce, Marketing & Communication, Productivité & Gestion de projet, Studio No-Code.

**Trois promesses** :

| Promesse | Ce que cela impose au code |
|---|---|
| Conformité locale native | SYSCOHADA révisé, IFU, **Facture Électronique Certifiée (FEC)** de la DGI (obligatoire depuis le 1er juillet 2026 pour le régime normal, 2027 pour les petites entreprises, 2028 pour les micro-entreprises), paie conforme (Release 2). Module de conformité isolé et paramétrable. |
| Hors-ligne | Caisse, facturation, stock, inventaire, pointage : au moins 7 jours sans réseau. Synchronisation sans perte ni doublon. |
| Mobile Money natif | Orange Money, Moov Money, Wave via un agrégateur agréé. Simulateur pendant le développement, API réelles au plus tard en V1.0. |

**Releases** (matrice MoSCoW, AMB-CONC-02 v1.1, 156 fonctionnalités) :

| Release | Période | Contenu |
|---|---|---|
| R1 MVP | mois 2 à 10 | 57 Must : socle, synchronisation, caisse, ventes, facturation FEC, Mobile Money (simulateur), comptabilité, stock, achats, champs personnalisés, abonnement |
| R2 Pilote | mois 10 à 15 | 48 Should : CRM complet ★ (prioritaire, livré en totalité), boutique en ligne, Mobile Money réel, MFA, API publique, portail client, comptabilité avancée, RH, paie, notes de frais, projets, GED |
| R3 V1.0 | mois 15 à 24 | 43 Could : site web, marketing, fabrication, qualité, maintenance, assistance, Studio avancé |
| R4 | après 24 mois | 8 Won't : PLM, forum, eLearning, réseaux sociaux, apps no-code, marketplace, biométrie, rendez-vous |

**Décisions du porteur du projet** (à respecter) :

- Architecture **client-serveur, monolithe modulaire**, pas de microservices.
- **Java + Spring Boot**, **Angular + Tailwind CSS**, **PostgreSQL**.
- **Application Android en priorité** (caisse, stock, pointage), application web pour le bureau.
- **Keycloak** pour l'identité ; **GitHub** pour le code ; **Angular SSR** pour la boutique et les pages publiques.
- Interface **en français uniquement** au lancement, mais entièrement internationalisée.
- Stock insuffisant en caisse : **vente autorisée avec alerte** (paramétrable).
- Hors-ligne : déverrouillage de session par **code PIN** du caissier.
- Régimes fiscaux : **liste paramétrable** (en attente de validation par un expert-comptable).
- Multi-sociétés : **réservé au pack Enterprise**.
- Lots et péremption : modélisés dès le MVP, **activés en R2**.
- Confirmation Mobile Money en caisse : **interrogation périodique** au MVP ; **EVO-01** (WebSocket ou SSE) plus tard.

---

## 2. Fichier CLAUDE.md prêt à copier

Copie le bloc suivant dans `CLAUDE.md` à la racine du dépôt.

````markdown
# Ambawbio Suite — instructions pour Claude Code

ERP tout-en-un pour le Burkina Faso. Guide complet : docs/conception/AMB-CONC-09-guide-claude-code.md (à lire avant tout lot).

## Règles non négociables
- Monolithe modulaire Spring Boot (Spring Modulith). Pas de microservices, pas de courtier de messages.
- Java 25, Spring Boot 4.x, Angular 22 + Tailwind CSS 4, PostgreSQL 18, Keycloak. Android via Capacitor.
- Langue : interface, messages d'erreur et documentation en français. Code (classes, méthodes, tables) en français sans accents, camelCase / snake_case.
- Montants : type Montant (long, francs CFA sans décimales). Jamais double/float.
- Identifiants : UUID v7 générés par l'application (client ou serveur).
- Multi-tenant : chaque table métier a tenant_id + politique RLS. Jamais de contournement.
- Idempotence : toute création porte un identifiant fourni par le client ; un rejeu ne crée pas de doublon.
- Pièces fiscales validées non modifiables ; correction par avoir. Numérotation sans trou.
- Intégrations derrière des ports (PortPaiement, PortCertificationFiscale, PortMessagerie, PortBanque). Simulateurs par défaut en dev et en test.
- Ne jamais inventer une spécification officielle (DGI, opérateurs). Noter dans docs/QUESTIONS.md.
- Tests d'abord pour les règles métier. Aucun test désactivé.

## Commandes
- Back-end : cd backend && ./mvnw verify          (tests + frontières de modules)
- Front-end : cd frontend && npm run lint && npm test && npm run build
- E2E : cd frontend && npm run e2e
- Environnement local : docker compose -f infra/docker-compose.dev.yml up -d
- Android : cd frontend && npx cap sync android && npx cap open android

## Méthode
Lire le lot → plan dans docs/lots/LOT-xx.md → tests → code par tranches verticales → verify → docs → PR avec checklist.
Décisions dans docs/DECISIONS.md, questions dans docs/QUESTIONS.md.
````

---

## 3. Règles non négociables (avec leur raison)

| N° | Règle | Raison (livrable source) |
|---|---|---|
| R-01 | Monolithe modulaire, un seul déploiement serveur | Choix du porteur ; simplicité d'exploitation au Burkina Faso et sur site (AMB-CONC-04, ADR-01) |
| R-02 | Un module = un package Java = un schéma PostgreSQL ; accès aux autres modules uniquement par leur package `api` ou par événements | Frontières vérifiées, évolutivité (ADR-01, ADR-02) |
| R-03 | Événements entre modules enregistrés dans PostgreSQL (Spring Modulith event publication registry) | Aucun événement perdu, pas de courtier (ADR-02) |
| R-04 | `tenant_id` + sécurité au niveau des lignes (RLS) sur toutes les tables métier | Isolation stricte, risque R12 (ADR-03, ENF-06) |
| R-05 | UUID v7 générés par l'application | Création hors-ligne sans conflit (ADR-04) |
| R-06 | Montants en `long` FCFA via l'objet valeur `Montant` | Exactitude comptable (AMB-CONC-06 §2.2) |
| R-07 | Documents fiscaux validés immuables ; avoir pour corriger ; numérotation sans trou | RG-01, INV-01, INV-02 |
| R-08 | Mouvements de stock immuables, stock = somme des mouvements | RG-05, INV-09 |
| R-09 | Idempotence de toute écriture venant d'un client | SD-02, SD-03, SD-04 |
| R-10 | Ports et adaptateurs pour toute intégration externe, simulateur obligatoire | R15, R16, ADR-06 |
| R-11 | Barèmes fiscaux et sociaux versionnés par date d'effet, jamais en dur | RG-10, R03 |
| R-12 | Journal d'audit chaîné par empreinte pour toute opération sensible | RG-11, R13, R14 |
| R-13 | Aucune donnée personnelle en clair dans les journaux techniques | Loi n°001-2021/AN |
| R-14 | Interface en français, textes externalisés (Transloco), formats locaux | ENF-11 |
| R-15 | Budgets de performance respectés sur Android d'entrée de gamme | ENF-03, ENF-04 |
| R-16 | Design issu de Claude Design (jetons, composants) ; aucune couleur ou police en dur | AMB-CONC-10 |

---

## 4. Pile technique et versions

Au démarrage, vérifie les dernières versions stables et utilise-les. Les versions ci-dessous sont celles d'octobre 2026.

| Domaine | Choix | Remarques |
|---|---|---|
| Langage serveur | Java 25 (LTS) | Records, pattern matching, threads virtuels activés pour les E/S |
| Cadre serveur | Spring Boot 4.x (Spring Framework 7) | Démarreurs web, security (oauth2-resource-server), data-jpa, validation, actuator |
| Modularité | Spring Modulith 2.x | `spring-modulith-starter-jpa` (registre d'événements), `spring-modulith-starter-test` |
| Persistance | Spring Data JPA (Hibernate), jOOQ pour les rapports | Pas de `ddl-auto` en dehors des tests ; schéma géré par Flyway |
| Migrations | Flyway | Un dossier de migrations par module |
| Correspondance objets | MapStruct | Pas de Lombok (records Java à la place) |
| Identifiants | Bibliothèque `uuid-creator` (UUID v7) | Côté client : fonction équivalente en TypeScript |
| Planification | Spring Scheduling + ShedLock (JDBC) | Une seule exécution avec plusieurs instances |
| Documents | JasperReports (PDF), Apache POI (Excel), ZXing (QR codes) | Modèles dans `backend/src/main/resources/rapports` |
| API | springdoc-openapi | `/v3/api-docs`, interface Swagger en dev uniquement |
| Identité | Keycloak (dernière version stable) | Royaume `ambawbio`, OpenID Connect |
| Cache | Caffeine | En mémoire, par instance |
| Tests serveur | JUnit 5, AssertJ, Testcontainers (PostgreSQL, Keycloak), ArchUnit, Spring Modulith Test, WireMock | Pas de base H2 |
| Front-end | Angular 22 (composants autonomes, signaux, contrôle de flux natif) | Stratégie OnPush partout |
| Style | Tailwind CSS 4 | Jetons de Claude Design dans `@theme` |
| Traductions | Transloco | `fr.json` par fonctionnalité |
| Hors-ligne web | Angular Service Worker, Dexie.js (IndexedDB) | |
| Android | Capacitor (dernière version stable), `@capacitor-community/sqlite` (chiffrement SQLCipher), stockage sécurisé (Keystore Android) | minSdk 26 (Android 8) |
| Graphiques | ECharts (ngx-echarts) | Chargés à la demande |
| Tests front | Vitest, Testing Library, Playwright | |
| Base de données | PostgreSQL 18 | Extensions : `pgcrypto`, `pg_trgm` |
| Stockage d'objets | MinIO (compatible S3) | |
| Conteneurs | Docker, Docker Compose | Images non root |
| Passerelle | Nginx | TLS, gzip/brotli, limitation de débit |
| Observabilité | Micrometer, Prometheus, Grafana, journaux JSON | |
| Sauvegardes | pgBackRest | Archivage WAL continu |
| CI/CD | GitHub Actions, Dependabot, CodeQL | |
| Qualité | SonarQube (ou SonarCloud), Checkstyle, ESLint, Prettier | |

---

## 5. Structure du dépôt

Monodépôt unique :

```
ambawbio-suite/
├── CLAUDE.md
├── README.md
├── CHANGELOG.md
├── docs/
│   ├── conception/          livrables AMB-CONC-01 à 10, diagrammes PlantUML
│   ├── design/              livrables Claude Design (logo, tokens.json, maquettes)
│   ├── lots/                LOT-00.md … plan et compte rendu de chaque lot
│   ├── DECISIONS.md         décisions d'architecture (ADR courts)
│   ├── QUESTIONS.md         questions ouvertes au porteur du projet
│   └── exploitation/        installation, sauvegarde, restauration, incidents
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/bf/ambawbio/
│       │   ├── AmbawbioApplication.java
│       │   ├── shared/              Montant, Ifu, Adresse, Uuid7, erreurs, pagination
│       │   ├── socle/               identite, tenancy, parametrage, audit, synchronisation,
│       │   │                        notification, documents, studio (sous-modules)
│       │   ├── referentiel/
│       │   ├── conformite/          conformite fiscale (port FEC)
│       │   ├── pos/
│       │   ├── ventes/
│       │   ├── facturation/
│       │   ├── paiement/
│       │   ├── comptabilite/
│       │   ├── stock/
│       │   ├── achats/
│       │   └── abonnement/
│       ├── main/resources/
│       │   ├── application.yml, application-dev.yml, application-prod.yml
│       │   ├── db/migration/{socle,referentiel,pos,…}/
│       │   ├── rapports/            modèles JasperReports
│       │   └── i18n/messages_fr.properties
│       └── test/java/bf/ambawbio/…
├── frontend/
│   ├── package.json, angular.json, capacitor.config.ts, tailwind (dans styles.css)
│   ├── android/                     projet Android généré par Capacitor
│   └── src/app/
│       ├── core/                    auth, intercepteurs, contexte, i18n, erreurs
│       ├── shared/                  composants UI du système de conception, pipes
│       ├── offline/                 LocalStore, outbox, sync agent, réseau
│       ├── features/                pos, ventes, facturation, paiements, comptabilite,
│       │                            stock, achats, referentiel, administration, tableau-de-bord
│       └── public/                  boutique et pages publiques (SSR, Release 2)
├── infra/
│   ├── docker-compose.dev.yml       postgres, keycloak, minio, mailpit
│   ├── docker-compose.prod.yml      installation sur site
│   ├── keycloak/realm-ambawbio.json
│   ├── nginx/
│   └── pgbackrest/
└── .github/
    ├── workflows/ backend.yml, frontend.yml, android.yml, e2e.yml, codeql.yml, release.yml
    ├── pull_request_template.md
    └── dependabot.yml
```

---

## 6. Back-end : organisation et conventions

### 6.1 Structure d'un module

```
bf.ambawbio.facturation
├── package-info.java            @ApplicationModule(displayName = "Facturation")
├── api/                         visible par les autres modules
│   ├── FacturationApi.java      interface publique (méthodes utilisées par d'autres modules)
│   ├── FactureDto.java          records
│   └── evenements/FactureValidee.java, AvoirValide.java
└── internal/                    invisible de l'extérieur
    ├── web/                     contrôleurs REST, objets de requête/réponse
    ├── application/             services de cas d'utilisation (@Transactional)
    ├── domaine/                 entités, objets valeur, règles, exceptions métier
    └── infrastructure/          dépôts JPA, requêtes jOOQ, adaptateurs
```

- Un test `ModularityTest` appelle `ApplicationModules.of(AmbawbioApplication.class).verify()` : il échoue si un module accède à `internal` d'un autre.
- Les règles métier vivent dans le **domaine** (méthodes des entités : `facture.valider(numero)`, `session.cloturer(comptage)`), pas dans les contrôleurs.
- Les services applicatifs orchestrent : chargement, appel au domaine, sauvegarde, publication d'événements.

### 6.2 Conventions de code

- Noms métier en **français sans accents** : `Facture`, `LigneFacture`, `numeroSuivant()`, table `ligne_facture`.
- Objets de transfert en `record` ; validation Jakarta (`@NotNull`, `@Positive`) avec messages en français.
- Entités : classe de base `EntiteMetier` (id, tenantId, creeLe, modifieLe, creePar, version, champsPerso) ; `@Version` pour le verrouillage optimiste ; les entités implémentent `Persistable<UUID>` (identifiant fourni à la création).
- Erreurs : exceptions métier typées (`RegleMetierException` avec un code `RG-01`, etc.) converties en `ProblemDetail` (RFC 9457) par un `@RestControllerAdvice` global. Messages en français, code stable pour le client.
- Journaux : SLF4J, format JSON en production, identifiant de corrélation par requête, **jamais** de nom, téléphone, IFU ou montant client dans les journaux.
- Transactions : une transaction par cas d'utilisation ; les écouteurs d'événements s'exécutent dans leur propre transaction (`@ApplicationModuleListener`).
- Threads virtuels activés (`spring.threads.virtual.enabled=true`).

### 6.3 Objet valeur Montant

```java
public record Montant(long valeur, Devise devise) implements Comparable<Montant> {
    public static Montant fcfa(long valeur) { return new Montant(valeur, Devise.XOF); }
    public Montant ajouter(Montant autre) { verifierDevise(autre); return new Montant(Math.addExact(valeur, autre.valeur), devise); }
    public Montant soustraire(Montant autre) { verifierDevise(autre); return new Montant(Math.subtractExact(valeur, autre.valeur), devise); }
    public Montant multiplier(BigDecimal facteur) { // arrondi au franc, demi supérieur
        return new Montant(new BigDecimal(valeur).multiply(facteur).setScale(0, RoundingMode.HALF_UP).longValueExact(), devise);
    }
    // compareTo, estPositif(), estNul(), verifierDevise(...)
}
```

Les quantités sont en `BigDecimal` (échelle 3) pour gérer les conditionnements (0,5 sac).

### 6.4 Multi-tenant (double barrière)

1. **Jeton** : Keycloak ajoute la revendication `tenant_id` (attribut de l'utilisateur) et les rôles. Un `TenantContext` (portée requête) est rempli par un filtre après validation du jeton. L'entreprise n'est **jamais** lue dans un paramètre de requête.
2. **Hibernate** : la colonne `tenant_id` est annotée `@TenantId` (multi-tenancy par discriminant de Hibernate) ; un `CurrentTenantIdentifierResolver` lit le `TenantContext`.
3. **PostgreSQL (RLS)** : au début de chaque transaction, exécuter `SELECT set_config('app.tenant_id', :tenant, true)` (portée transaction). Chaque table métier a la politique de la section 7.3. L'application se connecte avec un rôle **sans** `BYPASSRLS` et non propriétaire des tables.
4. **Traitements système** (planificateur, rapprochement) : itérer explicitement sur les entreprises et positionner le contexte pour chacune.
5. **Test d'isolation obligatoire** pour chaque nouvel agrégat : créer des données pour deux entreprises et vérifier qu'aucune lecture, liste, recherche ou export de l'entreprise A ne renvoie de donnée de B.

### 6.5 Sécurité et autorisations

- Spring Security en **serveur de ressources** JWT (Keycloak). Conversion des rôles Keycloak en autorités.
- Permissions métier fines : `module:action` (ex. `facturation:valider`). Contrôle par `@PreAuthorize("hasAuthority('facturation:valider')")` et par un vérificateur d'établissement autorisé (AffectationRole).
- MFA exigée par Keycloak pour les rôles `administrateur`, `comptable`, `dirigeant` (politique de flux d'authentification).
- Limitation de débit sur l'authentification et les notifications entrantes.
- En-têtes de sécurité, CORS restreint aux origines connues, CSRF non nécessaire pour l'API à jeton porteur ; cookies sécurisés pour le BFF éventuel.

### 6.6 Journal d'audit chaîné

```
empreinte = SHA-256( empreintePrecedente || horodatage || utilisateurId || action || entite || entiteId || avant || apres )
```

- Une chaîne **par entreprise**. Insertion sérialisée par un verrou consultatif PostgreSQL (`pg_advisory_xact_lock(hash(tenant))`).
- Un traitement planifié vérifie la chaîne chaque nuit et alerte en cas de rupture.
- Actions à journaliser au minimum : connexion, validation/annulation de pièce, avoir, modification de prix ou de taxe, clôture de caisse, validation d'écart, changement de droits, appairage ou révocation de terminal, export de données, demande d'effacement.

### 6.7 Numérotation sans trou

- Table `facturation.sequence_piece(tenant_id, societe_id, type_piece, exercice, prochain)`.
- Attribution dans la transaction de validation : `SELECT … FOR UPDATE`, puis incrément. En cas d'annulation de la transaction, le numéro n'est pas consommé.
- Format affiché : `{PREFIXE}-{AAAA}-{NNNNNN}` (ex. `FA-2027-000154`), préfixe paramétrable par société et par type.
- Hors-ligne : plages réservées par terminal (section 8.6) avec format `{PREFIXE}-{CODE_TERMINAL}-{AAAA}-{NNNNNN}`. Le format exact exigé par la DGI pour la FEC sera appliqué dès réception des spécifications (question Q-01).

### 6.8 Événements de domaine

| Événement | Publié par | Consommé par |
|---|---|---|
| `VenteEnregistree` | pos | stock (sortie), comptabilite (caisse, ventes) |
| `RetourEnregistre` | pos | stock, facturation (avoir) |
| `SessionCloturee` | pos | comptabilite (écart de caisse) |
| `FactureValidee`, `AvoirValide` | facturation | comptabilite, conformite (certification), notification |
| `CertificationObtenue`, `CertificationRejetee` | conformite | facturation, notification |
| `PaiementConfirme` | paiement | facturation (reste à payer), comptabilite |
| `ReceptionValidee` | achats | stock |
| `LivraisonValidee` | ventes | stock |
| `MouvementValorise` | stock | comptabilite |
| `InventaireValide` | stock | comptabilite |
| `AbonnementActive`, `AbonnementSuspendu` | abonnement | tenancy, notification |

Les événements sont des `record` immuables, versionnés (champ `version`), contenant des identifiants et les données nécessaires (pas d'entité JPA).

---

## 7. Base de données

### 7.1 Schémas

`socle`, `referentiel`, `pos`, `ventes`, `facturation`, `paiement`, `comptabilite`, `stock`, `achats`, `abonnement`, `sync`, `audit`, plus la table `event_publication` de Spring Modulith (schéma `public`).

### 7.2 Colonnes communes et conventions

```sql
id           uuid primary key,              -- UUID v7 fourni par l'application
tenant_id    uuid not null,
cree_le      timestamptz not null default now(),
modifie_le   timestamptz not null default now(),
cree_par     uuid,
version      bigint not null default 0,
champs_perso jsonb not null default '{}'::jsonb
```

- Noms en `snake_case` au singulier. Index sur `(tenant_id, …)` pour toutes les recherches.
- Énumérations en `text` avec contrainte `CHECK`.
- Montants : `bigint` (`montant_ttc bigint not null`) ; devise en `char(3)` si multi-devises.
- Références vers un autre module : colonne `uuid` **sans** clé étrangère inter-schéma.
- Migrations : `backend/src/main/resources/db/migration/{module}/V{AAAAMMJJHHmm}__{module}_{description}.sql`. Jamais de modification d'une migration déjà appliquée.

### 7.3 Politique RLS type

```sql
alter table facturation.document_fiscal enable row level security;
alter table facturation.document_fiscal force row level security;
create policy isolation_tenant on facturation.document_fiscal
  using (tenant_id = current_setting('app.tenant_id')::uuid)
  with check (tenant_id = current_setting('app.tenant_id')::uuid);
```

Une migration utilitaire fournit une fonction `socle.activer_rls(nom_table text)` appelée pour chaque nouvelle table.

### 7.4 Tables clés du MVP (extraits)

```sql
create table facturation.document_fiscal (
  id uuid primary key, tenant_id uuid not null,
  societe_id uuid not null,
  type text not null check (type in ('FACTURE','AVOIR')),
  numero text,                                   -- null tant que brouillon
  date_emission date,
  client_id uuid not null,                       -- référentiel.tiers
  facture_origine_id uuid references facturation.document_fiscal(id), -- pour un avoir
  total_ht bigint not null default 0, total_taxes bigint not null default 0, total_ttc bigint not null default 0,
  statut text not null check (statut in ('BROUILLON','VALIDEE','PARTIELLEMENT_PAYEE','PAYEE','ANNULEE_PAR_AVOIR')),
  origine text, origine_id uuid,                 -- vente, commande
  cree_le timestamptz not null default now(), modifie_le timestamptz not null default now(),
  cree_par uuid, version bigint not null default 0, champs_perso jsonb not null default '{}',
  unique (tenant_id, societe_id, type, numero)
);

create table facturation.certification_fec (
  document_id uuid primary key references facturation.document_fiscal(id),
  tenant_id uuid not null,
  statut text not null check (statut in ('NON_SOUMISE','EN_FILE','CERTIFIEE','REJETEE')),
  identifiant_dgi text, code_qr text, horodatage_dgi timestamptz,
  tentatives int not null default 0, prochain_essai timestamptz, dernier_message text
);

create table stock.mouvement_stock (
  id uuid primary key, tenant_id uuid not null,
  produit_id uuid not null, lot_id uuid,
  emplacement_source_id uuid, emplacement_destination_id uuid,
  type text not null check (type in ('RECEPTION','VENTE','RETOUR_CLIENT','RETOUR_FOURNISSEUR','TRANSFERT','AJUSTEMENT_INVENTAIRE','LIVRAISON')),
  quantite numeric(18,3) not null check (quantite > 0),
  cout_unitaire bigint not null,
  date timestamptz not null,
  origine text not null, origine_id uuid not null,
  operation_sync_id uuid unique,                 -- idempotence
  cree_le timestamptz not null default now(), cree_par uuid
  -- pas de modifie_le : table en insertion seule
);

create table sync.operation_recue (
  tenant_id uuid not null,
  id_operation uuid not null,
  terminal_id uuid not null,
  type_operation text not null,
  charge jsonb not null,
  horodatage_local timestamptz not null,
  signature text not null,
  statut text not null check (statut in ('RECUE','APPLIQUEE','IGNOREE_DOUBLON','EN_CONFLIT')),
  recue_le timestamptz not null default now(),
  primary key (tenant_id, id_operation)
);

create table sync.flux_changements (
  sequence bigserial primary key,
  tenant_id uuid not null,
  etablissement_id uuid,                         -- null = toute l'entreprise
  entite text not null, entite_id uuid not null,
  operation text not null check (operation in ('UPSERT','SUPPRESSION')),
  donnees jsonb,
  cree_le timestamptz not null default now()
);

create table audit.journal (
  id bigserial primary key, tenant_id uuid not null,
  horodatage timestamptz not null, utilisateur_id uuid,
  action text not null, entite text not null, entite_id uuid,
  avant jsonb, apres jsonb,
  empreinte_precedente text not null, empreinte text not null
);
```

Les tables en insertion seule (`mouvement_stock`, `audit.journal`, `operation_recue`) refusent `UPDATE` et `DELETE` par un déclencheur ou par l'absence de privilèges.

### 7.5 Données de démarrage

Migrations « R » (repeatable) ou scripts de paramétrage chargés à la création d'une entreprise : plan comptable SYSCOHADA révisé, journaux, taxes, régimes fiscaux, rôles par défaut, unités et conditionnements usuels (pièce, carton, sac, bidon, palette, kg, litre). **Ces valeurs par défaut sont à faire valider par l'expert-comptable** (Q-03, Q-04).

---

## 8. Moteur hors-ligne et synchronisation (spécification)

C'est le composant le plus critique (risques R07, R08). Il est développé au lot 4, avant la caisse, et couvert par des tests de robustesse.

### 8.1 Principes

- Le terminal ne connaît que les données de son entreprise, de son établissement et de son rôle.
- Toute action hors-ligne produit une **opération** : enregistrement immuable, horodaté, signé, avec un identifiant unique.
- Le serveur est la source de vérité. Le terminal applique ses opérations localement de façon optimiste, puis le serveur les accepte, les ignore (doublon) ou les met en conflit.

### 8.2 Enveloppe d'une opération

```json
{
  "idOperation": "0192f3a4-…-v7",
  "terminalId": "…",
  "type": "VENTE_ENREGISTREE",
  "versionSchema": 1,
  "horodatageLocal": "2027-03-14T09:41:22.315Z",
  "utilisateurId": "…",
  "charge": { "venteId": "…", "numero": "TK-C01-2027-000412", "lignes": [ … ], "paiements": [ … ] },
  "signature": "base64(ECDSA-P256-SHA256(idOperation|type|horodatageLocal|sha256(charge)))"
}
```

Types d'opérations du MVP : `SESSION_OUVERTE`, `VENTE_ENREGISTREE`, `RETOUR_ENREGISTRE`, `PAIEMENT_SECOURS_SAISI`, `SESSION_CLOTUREE`, `ECART_VALIDE`, `FACTURE_DEMANDEE`, `DEVIS_CREE`, `RECEPTION_SAISIE`, `TRANSFERT_SAISI`, `COMPTAGE_INVENTAIRE`, `FICHE_CLIENT_MODIFIEE`.

### 8.3 Envoi (push)

`POST /api/v1/sync/push` — corps : `{ "terminalId", "operations": [ … ] }` (au plus 100 opérations, compressé gzip).

Traitement serveur, pour chaque opération dans l'ordre :

1. Vérifier le terminal (actif, appartenant à l'entreprise du jeton) et la signature.
2. `INSERT INTO sync.operation_recue … ON CONFLICT DO NOTHING`. Si aucune ligne insérée : statut `IGNOREE_DOUBLON`, accusé de réception positif.
3. Appeler le gestionnaire du type (`GestionnaireOperation<T>`), dans une transaction par opération.
4. Le gestionnaire applique la règle de conflit (8.5), écrit les entités, publie les événements.
5. Statut `APPLIQUEE` ou `EN_CONFLIT` (avec motif).

Réponse : `{ "accuses": [ { "idOperation", "statut", "motif?" } ] }`. Le terminal ne supprime de sa file que les opérations accusées `APPLIQUEE` ou `IGNOREE_DOUBLON`.

### 8.4 Réception (pull)

`GET /api/v1/sync/pull?curseur={n}&limite=500`

- Lit `sync.flux_changements` avec `sequence > n`, filtré par entreprise (RLS), établissement et périmètre du rôle.
- Réponse : `{ "changements": [ … ], "curseur": m, "encore": true|false, "plages": [ … ] }`.
- Les écritures dans `flux_changements` sont faites **par l'application**, dans la même transaction que la modification de l'entité synchronisable (catalogue, prix, taxes, clients, stock du dépôt, utilisateurs autorisés, paramètres de caisse).
- Chargement initial : `curseur=0`, par lots, après l'appairage (SD-11).
- Purge : les changements de plus de 90 jours sont compactés ; un terminal trop ancien reçoit l'ordre de recharger entièrement.

### 8.5 Règles de conflit

| Donnée | Règle |
|---|---|
| Mouvements de stock | Cumul : chaque opération ajoute ses mouvements ; jamais d'écrasement (RG-05). Stock négatif autorisé et signalé. |
| Ventes, retours, paiements | Créations uniquement ; l'identifiant unique empêche les doublons. |
| Fiche client | Dernière modification (horodatage serveur de réception) ; le terminal ne peut modifier que les coordonnées. |
| Prix, taxes, paramètres fiscaux | Modifiables uniquement en ligne (RG-06) ; une opération de terminal qui les modifie est refusée. |
| Session de caisse | Une seule session ouverte par point de vente (INV-14) ; deux ouvertures concurrentes → la seconde passe en conflit. |
| Cas sensibles | Retour supérieur à la quantité vendue, paiement de secours introuvable : `EN_CONFLIT` + alerte au responsable. |

### 8.6 Plages de numérotation

- À l'appairage et à chaque synchronisation : le serveur garantit au terminal une plage active par type (`TICKET`, `FACTURE`, `AVOIR`) d'une taille paramétrable (par défaut 500).
- Le terminal consomme localement ; à 80 % il affiche une alerte (RG-04) ; à 100 % il bloque la vente et invite à synchroniser.
- Les plages ne se chevauchent jamais (INV-11) : contrainte d'exclusion sur `(tenant_id, societe_id, type_piece, int8range(debut, fin))`.

### 8.7 Côté terminal

- **LocalStore** : interface unique (`lire`, `ecrireTransaction`, `requete`) avec deux implémentations : `SqliteStore` (Android, chiffrée, clé dans le Keystore) et `DexieStore` (navigateur).
- **Outbox** : table locale `operation_sortante` (id, type, charge, signature, statut, tentatives).
- **SyncAgent** : déclenché au retour du réseau, toutes les 2 minutes en ligne, et à la demande. Envoi par lots, puis réception. Réessais avec délai exponentiel plafonné. Verrou pour éviter deux synchronisations simultanées.
- **Indicateurs visibles** dans l'interface : état réseau, nombre d'opérations en attente, heure de la dernière synchronisation, alerte si plus de 24 h sans synchronisation.
- **Stockage persistant** : sur le web, demander `navigator.storage.persist()` ; sur Android, la base SQLite est persistante par nature.

### 8.8 Tests obligatoires du moteur

- Rejeu du même lot trois fois → aucun doublon.
- Coupure au milieu d'un lot → reprise sans perte.
- Deux terminaux vendent le même produit hors-ligne → stock cumulé correct.
- Sept jours d'opérations simulées (au moins 5 000 ventes) puis synchronisation → tout appliqué, performances acceptables.
- Terminal révoqué → refus, opérations conservées localement.
- Signature invalide → refus.

---

## 9. Client Angular et application Android

### 9.1 Principes

- Composants **autonomes**, **signaux** pour l'état, `ChangeDetectionStrategy.OnPush`, contrôle de flux natif (`@if`, `@for`).
- Routes chargées à la demande par fonctionnalité (budget du premier chargement : moins de 250 Ko compressés pour la caisse).
- Formulaires réactifs typés ; messages d'erreur en français issus des codes `ProblemDetail`.
- Aucune couleur, police, taille ou espacement en dur : uniquement les jetons du système de conception de Claude Design, déclarés dans `@theme` de Tailwind 4.
- Accessibilité : contrastes WCAG AA, cibles tactiles de 48 dp minimum, navigation au clavier sur le web.

### 9.2 Formats locaux

```ts
// 12 500 FCFA
export const formaterFcfa = (v: number) =>
  new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(v) + ' FCFA';
// Dates : 14/03/2027 · Heures : 09:41
```

Les numéros de téléphone sont saisis au format burkinabè (8 chiffres) avec l'indicatif +226 ajouté automatiquement.

### 9.3 Application Android (prioritaire)

- Projet Capacitor dans `frontend/android`, même code Angular.
- Écrans prioritaires : caisse, inventaire, réception, transfert, consultation stock et clients, devis mobile.
- Plugins : SQLite chiffrée, stockage sécurisé (clé de chiffrement et clé privée de signature dans le Keystore), caméra (lecture de codes-barres), impression ESC/POS en Bluetooth (tickets 58 et 80 mm), état du réseau, état de la batterie.
- Mode kiosque optionnel pour les caisses dédiées.
- Signature de l'application par la CI (secrets GitHub), distribution d'abord par fichier APK/AAB interne, puis Google Play.
- Tests sur au moins deux appareils d'entrée de gamme (2 Go de RAM, écran 5 à 6 pouces).

### 9.4 Écrans du MVP

L'inventaire complet des écrans est donné par le brief de design (AMB-CONC-10, section « Inventaire des écrans »). Implémente les écrans **à partir des maquettes** de Claude Design, en réutilisant les composants partagés.

### 9.5 Rendu côté serveur

Angular SSR uniquement pour la boutique et les pages publiques (Release 2). L'application de gestion reste rendue dans le navigateur.

---

## 10. Intégrations externes et simulateurs

### 10.1 Port de paiement

```java
public interface PortPaiement {
    ReponseInitiation initierEncaissement(DemandeEncaissement demande); // idempotent sur demande.idPaiement()
    ReponseInitiation initierDecaissement(DemandeDecaissement demande);
    StatutTransaction consulterStatut(String referenceExterne);
    NotificationVerifiee verifierNotification(String corps, Map<String, String> entetes); // signature
    List<TransactionReleve> releve(LocalDate date);
}
```

- Implémentations : `SimulateurPaiement` (profil `dev`, `test`, démonstrations), `AdaptateurAgregateur` (à écrire quand le contrat et la documentation de l'agrégateur seront disponibles), `AdaptateurOperateurDirect` (futur).
- Sélection par configuration, **par entreprise** : `ambawbio.paiement.adaptateur=simulateur|agregateur`.
- Machine d'états du paiement : `INITIE → EN_ATTENTE → CONFIRME | ECHOUE | ANNULE` ; `A_VERIFIER → CONFIRME` (rapprochement) ou reste `A_VERIFIER` avec alerte (RG-07, INV-07).
- Notifications entrantes : `POST /api/v1/paiements/notifications/{adaptateur}` ; vérification de la signature, idempotence par la clé de la notification, réponse rapide (traitement asynchrone).

### 10.2 Comportement du simulateur de paiement

Le simulateur reproduit les cas réels, choisis selon les derniers chiffres du numéro du payeur :

| Numéro se terminant par | Comportement |
|---|---|
| `…01` | Succès, notification après 3 secondes |
| `…02` | Refus par le client |
| `…03` | Expiration sans réponse (2 minutes) |
| `…04` | Succès avec notification envoyée deux fois |
| `…05` | Succès, notification retardée de 10 minutes |
| `…06` | Solde insuffisant |
| autre | Succès immédiat |

Il expose aussi un relevé quotidien cohérent avec les transactions simulées, pour tester le rapprochement (SD-09). Un écran d'administration (profil `dev`) permet de déclencher manuellement une notification.

### 10.3 Port de certification fiscale (FEC)

```java
public interface PortCertificationFiscale {
    ResultatCertification certifier(DocumentACertifier document);   // facture ou avoir
    ResultatVerificationIfu verifierIfu(String ifu);
}
```

- `SimulateurFec` : renvoie un identifiant `SIM-…`, un contenu de QR code et un horodatage ; peut simuler l'indisponibilité et le rejet. Les documents certifiés par le simulateur portent la mention « CERTIFICATION SIMULÉE – SANS VALEUR FISCALE ».
- `AdaptateurDgi` : **ne pas l'implémenter tant que les spécifications officielles de la FEC ne sont pas fournies** (Q-01). Préparer la structure, les tests de contrat et la configuration.
- File de certification : statut `EN_FILE`, `prochain_essai` avec délai croissant (1 min, 5 min, 15 min, 1 h, puis toutes les heures), alerte au comptable après 24 h.

### 10.4 Messagerie

`PortMessagerie.envoyer(Message)` avec adaptateurs `Smtp` (Mailpit en dev), `Sms` et `WhatsApp` (simulés tant que les contrats ne sont pas signés). Envois asynchrones, suivi du statut, respect du consentement (Contact.consentementMarketing).

### 10.5 Banques

Release 2 : import de relevés (CSV ou formats bancaires), virements groupés par fichier. Pas d'intégration directe au MVP.

---

## 11. Règles métier, calculs et comptabilisation

### 11.1 Règles de gestion (à couvrir par des tests nommés `RG_xx_...`)

| ID | Règle |
|---|---|
| RG-01 | Facture validée non modifiable ni supprimable ; seule un avoir l'annule |
| RG-02 | Mentions FEC et IFU du vendeur et du client assujetti obligatoires |
| RG-03 | Facture hors-ligne : numéro de plage du terminal, certification au retour du réseau |
| RG-04 | Alerte à 80 % de consommation d'une plage |
| RG-05 | Mouvements de stock cumulés à la synchronisation ; stock négatif signalé |
| RG-06 | Fiches : dernière modification ; prix, taxes, paramètres fiscaux en ligne uniquement |
| RG-07 | Paiement Mobile Money confirmé uniquement par notification ou rapprochement |
| RG-08 | Toute opération validée génère son écriture comptable |
| RG-09 | Écart de caisse au-delà du seuil validé par le responsable |
| RG-10 | Barèmes versionnés par date d'effet |
| RG-11 | Opérations sensibles au journal d'audit chaîné |
| RG-12 | Effacement = anonymisation des données à conservation légale |
| RG-13 | Coût moyen pondéré recalculé à chaque entrée, coûts d'approche inclus |
| RG-14 | Accès limité à l'entreprise et aux établissements autorisés |
| RG-15 | Vente avec stock insuffisant autorisée avec alerte (paramétrable) |
| RG-16 | Hors pack Enterprise : une seule société |

### 11.2 Invariants (AMB-CONC-06)

INV-01 document validé non modifiable · INV-02 numéro unique par société et type · INV-03 IFU obligatoire pour un client assujetti · INV-04 avoir ≤ reste non annulé · INV-05 écriture équilibrée · INV-06 pas d'écriture en période clôturée · INV-07 paiement confirmé seulement avec notification ou rapprochement · INV-08 affectations ≤ montant du paiement · INV-09 mouvement de stock immuable · INV-10 idOperation unique par entreprise · INV-11 plages sans chevauchement · INV-12 tenant_id obligatoire · INV-13 un seul barème en vigueur par code et par date · INV-14 une seule session ouverte par point de vente · INV-15 une seule société hors pack Enterprise.

### 11.3 Calculs

- **Taxes** : calculées **par ligne** puis totalisées par taux dans `LigneTaxe` ; arrondi au franc, demi supérieur. Le taux normal de TVA, les exonérations et les autres taxes sont des **paramètres** (`Taxe`, `Bareme`) ; les valeurs par défaut (taux normal de TVA de 18 %, à confirmer) sont validées par l'expert-comptable.
- **Prix** : priorité ligne de liste de prix du client la plus spécifique (quantité, période) > liste de prix du client > prix de vente du produit.
- **Coût moyen pondéré** : `nouveauCout = (stockAvant × coutAvant + qteEntree × coutEntree) / (stockAvant + qteEntree)` ; `coutEntree` inclut la quote-part des coûts d'approche répartie au prorata de la valeur. Si `stockAvant ≤ 0`, `nouveauCout = coutEntree`.
- **Conditionnements** : `quantiteUniteStock = quantiteSaisie × facteur` ; arrondi à 3 décimales.
- **Reste à payer** : `totalTtc − somme(affectations confirmées) − somme(avoirs validés)`.

### 11.4 Comptabilisation automatique (comptes par défaut)

Comptes du plan SYSCOHADA révisé, **paramétrables et à valider par l'expert-comptable** (Q-04) :

| Opération | Débit | Crédit |
|---|---|---|
| Facture de vente | 411 Clients (TTC) | 701 Ventes de marchandises ou 706 Services (HT) ; 4431 TVA facturée |
| Encaissement espèces | 571 Caisse | 411 Clients |
| Encaissement Mobile Money | Compte de trésorerie Mobile Money (paramétrable, un sous-compte par opérateur) | 411 Clients |
| Vente au comptant en caisse | 571 Caisse ou trésorerie Mobile Money | 701 et 4431 |
| Avoir | Inverse de la facture (extourne) | |
| Facture fournisseur | 601 Achats de marchandises (HT) ; 4452 TVA récupérable | 401 Fournisseurs |
| Variation de stock | 31 Marchandises / 6031 Variation des stocks (sens selon la variation) | |
| Écart de caisse | Compte de charge ou de produit d'écart (paramétrable) | 571 Caisse (ou inverse) |

Chaque écriture porte `origine` et `origineId` pour la traçabilité, et est générée par un écouteur d'événement du module comptabilité (RG-08). Une écriture déséquilibrée lève une exception et bloque l'événement (rejoué après correction).

### 11.5 Clôture et états financiers

- Clôture mensuelle : verrouillage de la période (INV-06).
- États SYSCOHADA (système normal et système minimal de trésorerie) produits par requêtes jOOQ sur la balance, à partir de tables de correspondance comptes → postes, **paramétrables** et validées par l'expert-comptable.

---

## 12. Catalogue de l'API du MVP

Préfixe `/api/v1`. Toutes les créations acceptent un `id` UUID v7 fourni par le client (idempotence). Pagination `?page=&taille=&tri=`.

| Module | Méthode et chemin | Usage | UC / SD |
|---|---|---|---|
| socle | `GET /socle/contexte` | Contexte de l'utilisateur connecté | UC-SOC-01, SD-01 |
| socle | `GET/PUT /socle/entreprise`, `GET/POST /socle/etablissements`, `/socle/depots` | Paramétrage | UC-SOC-02 |
| socle | `GET/POST/PUT /socle/utilisateurs`, `/socle/roles` | Utilisateurs et rôles (synchronisés avec Keycloak) | UC-SOC-03 |
| socle | `POST /socle/terminaux`, `POST /socle/terminaux/appairage`, `POST /socle/terminaux/{id}/revocation` | Terminaux | UC-SOC-04, SD-11 |
| sync | `POST /sync/push`, `GET /sync/pull` | Synchronisation | UC-SOC-05, SD-03 |
| socle | `POST /socle/imports/{type}` (+ statut, rapport d'erreurs) | Import de données | UC-SOC-06 |
| socle | `GET /socle/tableau-de-bord` | Indicateurs | UC-SOC-07 |
| audit | `GET /audit/journal` | Consultation du journal | UC-SOC-08 |
| referentiel | `/referentiel/tiers`, `/referentiel/produits`, `/referentiel/categories`, `/referentiel/unites`, `/referentiel/taxes`, `/referentiel/regimes-fiscaux`, `/referentiel/listes-prix` | Référentiels | UC-SOC-09 |
| studio | `/studio/champs` | Champs personnalisés | UC-SOC-10 |
| socle | `POST /socle/donnees-personnelles/demandes` | Accès, effacement | UC-SOC-11 |
| abonnement | `POST /abonnements`, `GET /abonnements/courant`, `POST /abonnements/{id}/renouvellement` | Abonnement | UC-SOC-12, SD-13 |
| plateforme | `/plateforme/entreprises` | Administration éditeur | UC-SOC-13 |
| pos | `/pos/points-de-vente`, `POST /pos/sessions`, `POST /pos/sessions/{id}/cloture`, `POST /pos/sessions/{id}/validation-ecart` | Caisse (en ligne ; hors-ligne via sync) | UC-POS-01, 07, 08 |
| ventes | `/ventes/devis`, `POST /ventes/devis/{id}/envoi`, `POST /ventes/devis/{id}/conversion`, `/ventes/commandes`, `POST /ventes/commandes/{id}/livraison`, `POST /ventes/commandes/{id}/facturation` | Cycle de vente | UC-VEN-01 à 04, SD-10 |
| facturation | `/facturation/documents`, `POST /facturation/documents/{id}/validation`, `POST /facturation/documents/{id}/avoirs`, `GET /facturation/documents/{id}/pdf` | Factures et avoirs | UC-FAC-01, 02, SD-05, SD-08 |
| paiement | `POST /paiements`, `GET /paiements/{id}`, `POST /paiements/notifications/{adaptateur}`, `POST /paiements/rapprochements` | Paiements | UC-POS-04, UC-PAY-01, 02, SD-04, SD-09 |
| comptabilite | `/comptabilite/comptes`, `/comptabilite/journaux`, `/comptabilite/ecritures`, `POST /comptabilite/lettrages`, `GET /comptabilite/balance`, `GET /comptabilite/grand-livre`, `GET /comptabilite/etats-financiers` | Comptabilité | UC-CPT-01 à 06 |
| achats | `/achats/demandes-prix`, `/achats/commandes`, `POST /achats/receptions`, `/achats/factures-fournisseurs` | Achats | UC-ACH-01 à 04, SD-07 |
| stock | `GET /stock/disponibilites`, `POST /stock/transferts`, `/stock/inventaires`, `POST /stock/inventaires/{id}/validation`, `GET /stock/valorisation` | Stock | UC-STK-01 à 04, SD-12 |

Chaque contrôleur est documenté (OpenAPI : résumé et description en français, exemples). Le client Angular est généré à partir de l'OpenAPI (`openapi-generator`, mode `typescript-angular`) ou écrit à la main avec des types partagés générés.

---

## 13. Tests et qualité

### 13.1 Pyramide de tests

| Niveau | Outils | Exigence |
|---|---|---|
| Unitaires domaine | JUnit 5, AssertJ | 100 % des règles RG et INV, des calculs (taxes, CMUP, conversions, reste à payer) et de la numérotation |
| Intégration | Testcontainers PostgreSQL, Spring Boot Test | Chaque dépôt et service ; RLS active pendant les tests |
| Isolation multi-tenant | Testcontainers | Un test par agrégat : aucune fuite entre deux entreprises |
| Modularité | Spring Modulith `verify()`, ArchUnit | Aucune dépendance interdite |
| Contrat d'API | Tests MockMvc / RestAssured, OpenAPI validé | Codes HTTP, ProblemDetail, idempotence |
| Synchronisation | Tests dédiés (section 8.8) | Rejeu, coupure, concurrence, volume |
| Front unitaires | Vitest, Testing Library | Composants et services, notamment la couche hors-ligne |
| Bout en bout | Playwright | Les 13 scénarios SD-01 à SD-13 |
| Android | Tests instrumentés de base + recette manuelle guidée | Vente hors-ligne, impression, synchronisation |
| Performance | k6 (API), Lighthouse / profilage Android | ENF-03, ENF-14 |
| Sécurité | Dependabot, CodeQL, OWASP Dependency-Check, ZAP (base) | Aucune vulnérabilité critique ou élevée |

Couverture minimale : 80 % des lignes sur le domaine et les services ; 100 % des classes de règles fiscales.

### 13.2 Jeux de données

- `backend/src/test/resources/jeux/` : entreprise de démonstration « Quincaillerie Wend-Panga » (fictive), 200 produits avec conditionnements, 50 clients dont des assujettis avec IFU fictifs, 3 établissements, 2 dépôts.
- Les jeux de test comptables validés par l'expert-comptable sont stockés à part (`jeux/comptabilite/`) et ne sont jamais modifiés sans son accord.

### 13.3 Normes de code

- Checkstyle (style Google adapté), Spotless pour le formatage Java ; ESLint + Prettier pour Angular.
- Pas de code mort, pas de `TODO` sans référence à une question (`Q-xx`) ou à un ticket.
- Revue de code obligatoire (humaine ou Claude) avec la liste de contrôle de la section 16.

---

## 14. CI/CD, environnements et exploitation

### 14.1 Environnement local

`infra/docker-compose.dev.yml` : PostgreSQL 18, Keycloak (royaume importé depuis `infra/keycloak/realm-ambawbio.json` avec des utilisateurs de démonstration par rôle), MinIO, Mailpit. Profil Spring `dev` : simulateurs activés.

### 14.2 Workflows GitHub Actions

| Workflow | Déclencheur | Étapes |
|---|---|---|
| `backend.yml` | push, pull request | Java 25, cache Maven, `./mvnw verify` (tests, Modulith, Checkstyle), rapport de couverture, Sonar |
| `frontend.yml` | push, pull request | Node LTS, `npm ci`, lint, tests, build production, budgets |
| `e2e.yml` | pull request vers `main`, nuit | Docker Compose complet, Playwright SD-01 à SD-13, artefacts (vidéos en cas d'échec) |
| `android.yml` | tag, manuel | `cap sync`, build Gradle, signature (secrets), publication de l'AAB/APK en artefact |
| `codeql.yml` | hebdomadaire, pull request | Analyse Java et TypeScript |
| `release.yml` | tag `v*` | Images Docker (serveur, front, Nginx) publiées dans GHCR, notes de version |

Branche `main` protégée : pull request, CI verte et revue obligatoires.

### 14.3 Environnements

Développement (local) → Intégration (déploiement automatique de `main`) → Recette (validation du porteur et des experts) → Préproduction (copie anonymisée) → Production. Mêmes images Docker partout ; configuration par variables d'environnement ; secrets dans les secrets GitHub et un coffre sur les serveurs.

### 14.4 Exploitation

- Actuator : santé, métriques Prometheus ; tableaux de bord Grafana (disponibilité, latence, erreurs, file de certification, opérations en conflit, synchronisations, paiements en attente).
- Alertes : indisponibilité, file FEC bloquée plus d'une heure, rupture de la chaîne d'audit, paiements « à vérifier » de plus de 48 h, sauvegarde échouée.
- Sauvegardes pgBackRest : complète quotidienne, différentielle, archivage WAL continu (RPO 1 h), copie chiffrée hors site ; test de restauration trimestriel documenté dans `docs/exploitation/`.
- Installation sur site : `docker-compose.prod.yml` + script d'installation + guide ; mises à jour par images signées.

---

## 15. Feuille de route par lots (de A à Z)

Chaque lot se termine par une démonstration au porteur du projet. Les lots 0 à 14 constituent la **Release 1 (MVP)**. Durées indicatives pour une petite équipe assistée par Claude Code.

### LOT 0 — Initialisation du projet (1 semaine)

- **Objectif** : un dépôt prêt, qui compile, teste et se déploie localement.
- **Contenu** : structure de la section 5 ; `CLAUDE.md` ; projet Spring Boot (Java 25, dépendances de la section 4) avec `ModularityTest` ; projet Angular avec Tailwind 4, Transloco, ESLint, Vitest, Playwright ; Capacitor Android initialisé ; `docker-compose.dev.yml` ; royaume Keycloak de démonstration ; workflows `backend.yml`, `frontend.yml`, `codeql.yml` ; Dependabot ; modèles de PR ; `docs/DECISIONS.md`, `docs/QUESTIONS.md`.
- **Acceptation** : `./mvnw verify` et `npm test` au vert dans la CI ; l'application Angular affiche une page « Ambawbio Suite » authentifiée par Keycloak ; l'APK de démonstration s'installe sur un téléphone.

### LOT 1 — Socle technique (3 semaines)

- **Références** : F-SOC-01, 03, 04, 05, 09, 10, 18, 21, 28 ; UC-SOC-01, 02, 03, 08, 13 ; SD-01 ; RG-10, 11, 14, 16 ; INV-12, 15.
- **Contenu** : `shared` (Montant, Ifu, Adresse, Uuid7, ProblemDetail, pagination) ; `EntiteMetier` ; multi-tenant complet (section 6.4) avec RLS ; sécurité JWT, permissions, établissements autorisés ; modules `tenancy` (Entreprise, Societe, Etablissement, Depot, Abonnement, ModuleActive), `identite` (Utilisateur, Role, Permission, AffectationRole, synchronisation avec l'API d'administration de Keycloak), `parametrage` (Bareme versionné), `audit` (journal chaîné + vérification nocturne) ; administration plateforme minimale ; protection des données personnelles (registre, demandes) ; messages en français.
- **Acceptation** : tests d'isolation entre deux entreprises au vert ; MFA exigée pour un comptable ; chaîne d'audit vérifiée ; règle INV-15 testée.

### LOT 2 — Référentiels et import (2 semaines)

- **Références** : F-SOC-06, 07, 16 ; F-STU-01 ; UC-SOC-06, 09, 10.
- **Contenu** : Tiers (client/fournisseur, IFU, RCCM, régime fiscal paramétrable, contacts avec consentement, comptes Mobile Money), Produit, Catégorie, CodeBarre, UniteMesure, Conditionnement, Taxe, ListePrix ; champs personnalisés (JSONB + définition des champs + rendu dynamique dans les formulaires) ; assistant d'import CSV/Excel avec validation ligne par ligne et rapport d'erreurs ; données de démarrage (section 7.5).
- **Acceptation** : import de 1 000 produits et 500 clients avec rapport d'erreurs exact ; recherche plein texte rapide ; champs personnalisés visibles et filtrables.

### LOT 3 — Socle de l'application (2 semaines, en parallèle des lots 1-2)

- **Références** : AMB-CONC-10 (design), ENF-03, 04, 09, 11, 16.
- **Contenu** : intégration des jetons de Claude Design dans Tailwind ; bibliothèque de composants partagés (boutons, champs, tableaux, listes, cartes, modales, notifications, montant FCFA, sélecteur de quantité et de conditionnement, indicateur réseau/synchronisation) ; coquille de l'application (navigation, contexte entreprise/établissement, profil) ; écrans de connexion ; écrans de référentiels ; coquille Android (plugins, stockage sécurisé, code PIN).
- **Acceptation** : écrans conformes aux maquettes (comparaison visuelle) ; budgets de performance respectés ; contrastes AA vérifiés.

### LOT 4 — Moteur de synchronisation et terminaux (4 semaines)

- **Références** : section 8 ; F-SOC-11, 12, 13 ; UC-SOC-04, 05 ; SD-03, SD-11 ; RG-03, 04, 05, 06 ; INV-10, 11 ; ENF-01, 02.
- **Contenu** : tables `sync.*` ; appairage par QR code et clés ECDSA ; plages de numérotation ; push/pull ; gestionnaires d'opérations (cadre générique + premier type `FICHE_CLIENT_MODIFIEE`) ; flux de changements ; LocalStore (SQLite chiffrée et Dexie), outbox, SyncAgent, indicateurs ; révocation.
- **Acceptation** : tous les tests de la section 8.8 au vert ; appairage d'un téléphone réel et chargement initial de 2 000 produits en moins de 2 minutes en 3G simulée.

### LOT 5 — Caisse hors-ligne (4 semaines)

- **Références** : F-POS-01 à 07 ; UC-POS-01 à 03, 05 à 08 ; SD-02, SD-06, SD-08 (partie caisse) ; RG-09, 15 ; INV-14.
- **Contenu** : point de vente, sessions, écran de caisse tactile (recherche, scan caméra, conditionnements, remises autorisées), encaissement espèces et mixte, tickets ESC/POS 58/80 mm, retours, clôture avec comptage et validation d'écart par code PIN du responsable ; opérations de caisse dans la synchronisation ; facture demandée en caisse (mise en file de certification).
- **Acceptation** : vente complète en moins de 20 secondes pour 5 articles ; 7 jours de ventes hors-ligne simulées puis synchronisées sans perte ; impression testée sur une imprimante réelle.

### LOT 6 — Facturation et conformité FEC (3 semaines)

- **Références** : F-FAC-01 à 06 ; UC-FAC-01, 02 ; SD-05, SD-08 ; RG-01, 02, 03 ; INV-01 à 04.
- **Contenu** : DocumentFiscal (Facture, Avoir), lignes, taxes par taux, numérotation sans trou (section 6.7), validation, verrouillage, avoirs, PDF (modèle de Claude Design) avec QR code, archivage, module `conformite` avec `PortCertificationFiscale`, `SimulateurFec`, file de certification et relances, alertes.
- **Acceptation** : 1 000 validations concurrentes → aucun trou ni doublon ; facture validée impossible à modifier (API et base) ; certification différée testée (DGI simulée indisponible puis disponible).

### LOT 7 — Paiements Mobile Money (simulateur) et rapprochement (3 semaines)

- **Références** : F-PAY-01 à 04, 06 ; F-POS-04 ; F-CPT-05 ; F-SOC-29 ; UC-POS-04, UC-PAY-01, 02, UC-VEN-07 ; SD-04, SD-09 ; RG-07 ; INV-07, 08.
- **Contenu** : Paiement, AffectationPaiement, TransactionMobileMoney, NotificationPaiement ; `PortPaiement` et `SimulateurPaiement` (section 10.2) ; interrogation périodique depuis la caisse (EVO-01 plus tard) ; mode de secours ; lien de paiement pour les factures ; rapprochement quotidien et manuel.
- **Acceptation** : les 7 comportements du simulateur produisent les bons statuts ; notification en double sans effet ; rapprochement correct sur un relevé de 500 transactions avec écarts volontaires.

### LOT 8 — Ventes entre entreprises (2 semaines)

- **Références** : F-VEN-01 à 04 ; F-CRM-01 ; UC-VEN-01 à 06 ; SD-10.
- **Contenu** : devis, envoi (e-mail ; WhatsApp simulé), conversion en commande, acomptes, livraison (événement vers stock), facturation, suivi des impayés (balance âgée).
- **Acceptation** : parcours SD-10 automatisé de bout en bout.

### LOT 9 — Stocks, achats et inventaire (3 semaines)

- **Références** : F-STK-01 à 05 ; F-ACH-01 à 03 ; UC-ACH-01 à 04, UC-STK-01 à 04 ; SD-07, SD-12 ; RG-05, 13 ; INV-09.
- **Contenu** : dépôts, emplacements, StockQuant, mouvements immuables, valorisation au coût moyen pondéré, transferts, inventaires hors-ligne avec instant de référence, demandes de prix, commandes fournisseurs, réceptions avec conditionnements, factures fournisseurs et rapprochement.
- **Acceptation** : stock recalculé à partir des mouvements identique au StockQuant ; inventaire pendant des ventes correct ; CMUP vérifié sur jeu de test.

### LOT 10 — Comptabilité SYSCOHADA (4 semaines)

- **Références** : F-CPT-01 à 06 ; UC-CPT-01 à 06 ; RG-08 ; INV-05, 06 ; section 11.4.
- **Contenu** : plan comptable SYSCOHADA révisé, journaux, exercices et périodes, écritures automatiques par écouteurs d'événements, saisie manuelle, lettrage, balance, grand livre, états financiers (système normal et minimal), multi-dossiers pour l'expert-comptable.
- **Acceptation** : toutes les opérations des lots 5 à 9 produisent des écritures équilibrées ; états financiers validés par l'expert-comptable sur le jeu de test.

### LOT 11 — Abonnement et administration plateforme (2 semaines)

- **Références** : F-SOC-05, 28, 29 ; UC-SOC-12, 13 ; SD-13.
- **Contenu** : inscription, choix du pack, paiement par Mobile Money (simulateur), activation (groupe Keycloak, paramétrage initial), facture d'abonnement, renouvellement, délai de grâce, suspension avec export possible ; console éditeur.
- **Acceptation** : une entreprise peut s'inscrire seule et vendre en caisse en moins de 30 minutes.

### LOT 12 — Tableaux de bord et exports (1 semaine)

- **Références** : F-SOC-14, 15 ; UC-SOC-07.
- **Contenu** : tableau de bord dirigeant (chiffre d'affaires, trésorerie par moyen de paiement, stock et ruptures, impayés, marges), exports Excel et PDF de toutes les listes.

### LOT 13 — Durcissement (3 semaines)

- **Contenu** : performance (k6, profilage Android, index), sécurité (revue OWASP ASVS niveau 2, ZAP, correction des alertes), robustesse hors-ligne (campagne 7 jours sur appareils réels), accessibilité, journaux et supervision, sauvegardes et restauration testées, réversibilité (export complet).
- **Acceptation** : critères d'acceptation du MVP (cahier des charges §16.2) tous vérifiés.

### LOT 14 — Mise en production du MVP (2 semaines)

- **Contenu** : infrastructure chez l'hébergeur au Burkina Faso, déploiement, supervision, procédures d'exploitation, guides utilisateurs en français par profil (caissier, comptable, administrateur, dirigeant), formation des pilotes, recette avec procès-verbal.

### Après le MVP

| Ordre | Contenu | Remarques |
|---|---|---|
| 1 | CRM complet ★ (F-CRM-02 à 04) | Prioritaire, à livrer en totalité en Release 2 |
| 2 | Connexion Mobile Money réelle (F-PAY-05, EVO-02) | Écrire `AdaptateurAgregateur` à partir de la documentation officielle de l'agrégateur retenu |
| 3 | Adaptateur DGI réel | Dès réception des spécifications FEC (peut devenir prioritaire avant tout le reste) |
| 4 | Boutique en ligne (F-WEB-02) en Angular SSR, portail client | |
| 5 | MFA généralisée, API publique, webhooks | |
| 6 | RH et paie (barèmes IUTS, CNSS validés par un expert), notes de frais | Tests de paie validés par un juriste et un expert-comptable |
| 7 | Comptabilité avancée, analytique, projets, GED, abonnements | |
| 8 | EVO-01 : notifications instantanées (SSE de préférence, WebSocket si besoin bidirectionnel) | Remplace l'interrogation périodique |
| 9 | Release 3 puis 4 selon la matrice MoSCoW | |

Pour chaque nouveau module : nouveau package `bf.ambawbio.<module>`, nouveau schéma, migrations, RLS, tests d'isolation, événements documentés dans la section 6.8, écrans à partir des maquettes de Claude Design.

---

## 16. Définition du « terminé » et listes de contrôle

### 16.1 Définition du « terminé » (chaque fonctionnalité)

- [ ] Critères d'acceptation du cas d'utilisation vérifiés.
- [ ] Tests unitaires des règles, tests d'intégration, test d'isolation multi-tenant.
- [ ] `./mvnw verify` et `npm run lint && npm test && npm run build` au vert.
- [ ] Scénario de bout en bout mis à jour si le parcours est concerné.
- [ ] OpenAPI documenté en français ; messages d'erreur en français.
- [ ] Écrans conformes aux maquettes et aux jetons de design ; état hors-ligne et état vide traités.
- [ ] Journal d'audit pour les opérations sensibles.
- [ ] Pas de donnée personnelle dans les journaux techniques.
- [ ] `CHANGELOG.md` et fichier du lot mis à jour.

### 16.2 Liste de contrôle d'une pull request

- [ ] Le module ne dépend que d'API publiques d'autres modules (Modulith vert).
- [ ] Toute nouvelle table : `tenant_id`, RLS, index, migration Flyway nouvelle (pas de modification d'une ancienne).
- [ ] Montants en `Montant`/`bigint`, quantités en `BigDecimal`/`numeric(18,3)`.
- [ ] Créations idempotentes (identifiant fourni par le client).
- [ ] Aucune spécification officielle inventée ; simulateurs utilisés ; questions notées.
- [ ] Nouvelle dépendance justifiée.
- [ ] Aucun secret.

### 16.3 Liste de contrôle avant mise en production

- [ ] Critères d'acceptation du MVP (cahier des charges §16.2) vérifiés et signés.
- [ ] Test d'intrusion réalisé, vulnérabilités critiques et élevées corrigées.
- [ ] Restauration d'une sauvegarde testée sur un environnement vierge.
- [ ] Supervision et alertes actives.
- [ ] Formalités CIL effectuées ; mentions légales et politique de confidentialité publiées.
- [ ] Adaptateur FEC : **réel** si commercialisation à des entreprises soumises à la FEC ; sinon, mention claire de la certification simulée et commercialisation suspendue pour ces entreprises.
- [ ] Documentation utilisateur en français par profil.

---

## 17. Questions ouvertes et points bloquants connus

Reporte et mets à jour ces questions dans `docs/QUESTIONS.md`.

| ID | Question | Impact | Conduite à tenir en attendant |
|---|---|---|---|
| Q-01 | Spécifications techniques officielles de la FEC (format, API, procédure d'agrément des logiciels, traitement hors-ligne) | Adaptateur DGI, format de numérotation, mentions | Simulateur FEC, port isolé, tests de contrat préparés |
| Q-02 | Agrégateur Mobile Money retenu (contrat, documentation, environnement de test) | Adaptateur réel | Simulateur de paiement |
| Q-03 | Liste des régimes fiscaux, taux de TVA, exonérations, retenues | Paramétrage par défaut | Valeurs paramétrables, marquées « à valider » |
| Q-04 | Comptes par défaut et tables de correspondance des états financiers SYSCOHADA | Comptabilisation, états | Comptes de la section 11.4, paramétrables |
| Q-05 | Hébergeur au Burkina Faso retenu (capacités, sauvegardes, site de secours) | Déploiement | Développement sur Docker Compose |
| Q-06 | Durée légale de conservation exacte | Archivage, purge | Aucune purge de pièces comptables |
| Q-07 | Barèmes IUTS, CNSS, taxe patronale (Release 2) | Paie | Ne pas commencer la paie sans validation |
| Q-08 | Prix des packs en FCFA | Abonnement | Prix paramétrables |

---

## 18. Annexes

### 18.1 Référentiel des fonctionnalités (AMB-CONC-02 v1.1)

| ID | Fonctionnalité | Description | Priorité | Release | Source |
|---|---|---|---|---|---|
| F-SOC-01 | Authentification sécurisée | Connexion par identifiant et mot de passe haché, sessions à jeton, verrouillage après échecs répétés. | Must | R1 | EX-13 |
| F-SOC-02 | Authentification forte (MFA) | Second facteur par code à usage unique (application ou SMS), obligatoire pour les rôles sensibles. | Should | R2 | EX-13 |
| F-SOC-03 | Rôles et permissions | Droits par module, par action (lire, créer, modifier, valider, supprimer) et par établissement. | Must | R1 | EX-13 |
| F-SOC-04 | Multi-sociétés et multi-établissements | Une entreprise cliente gère plusieurs sociétés, établissements et dépôts. Un cabinet comptable accède à plusieurs dossiers. | Must | R1 | EX-04 |
| F-SOC-05 | Gestion des packs et activation des modules | Chaque entreprise active les modules de son abonnement (Essentiel, Business, Enterprise, Institution). | Must | R1 | – |
| F-SOC-06 | Référentiel unique des tiers | Clients, fournisseurs et contacts partagés par tous les modules, avec IFU, RCCM, régime fiscal et localisation. | Must | R1 | EX-01 |
| F-SOC-07 | Référentiel unique des produits et services | Articles, catégories, unités, codes-barres, taxes, prix ; partagés entre ventes, caisse, stock, achats et e-commerce. | Must | R1 | – |
| F-SOC-08 | Devises | Franc CFA (XOF) par défaut ; multi-devises avec taux de change pour l'import-export. | Should | R2 | – |
| F-SOC-09 | Paramétrage fiscal centralisé | Taux de TVA, retenues à la source, barèmes IUTS et CNSS, mentions FEC : modifiables sans redéploiement. | Must | R1 | EX-02 |
| F-SOC-10 | Journal d'audit inaltérable | Traçabilité de toutes les opérations sensibles (qui, quoi, quand, avant/après), chaînée par empreinte pour détecter toute altération. | Must | R1 | EX-13 |
| F-SOC-11 | Mode hors-ligne | Base locale chiffrée sur le poste ou le téléphone, contenant les données utiles au rôle ; les opérations sont enregistrées dans un journal local. | Must | R1 | EX-05 |
| F-SOC-12 | Synchronisation et gestion des conflits | Envoi et réception automatiques au retour du réseau, par lots, avec reprise ; règles par type de donnée (cumul, dernière modification, validation manuelle). | Must | R1 | EX-05 |
| F-SOC-13 | Plages de numérotation hors-ligne | Chaque caisse ou poste reçoit à l'avance une plage de numéros de pièces pour garder une séquence continue sans conflit. | Must | R1 | EX-01 |
| F-SOC-14 | Tableau de bord dirigeant | Indicateurs clés en temps réel : chiffre d'affaires, trésorerie, stock, impayés, marges. | Must | R1 | EX-11 |
| F-SOC-15 | Exports Excel et PDF | Export de toute liste et de tout rapport. | Must | R1 | – |
| F-SOC-16 | Import de données | Assistant d'import CSV/Excel des tiers, produits, stocks initiaux et soldes comptables (migration). | Must | R1 | – |
| F-SOC-17 | Notifications | Notifications dans l'application et par e-mail ; SMS et WhatsApp pour les alertes et relances. | Should | R2 | EX-14 |
| F-SOC-18 | Protection des données personnelles | Recueil du consentement, registre des traitements, droits d'accès, de rectification et d'effacement. | Must | R1 | EX-08 |
| F-SOC-19 | Réversibilité | Export complet des données d'une entreprise dans des formats ouverts. | Should | R2 | EX-09 |
| F-SOC-20 | Sauvegarde et restauration | Sauvegardes automatiques chiffrées, restauration par entreprise. | Must | R1 | EX-09 |
| F-SOC-21 | Interface en français, internationalisée | Tous les textes dans des fichiers de traduction ; français seul au lancement. | Must | R1 | EX-10 |
| F-SOC-22 | Application mobile | Application web progressive installable, puis application Android empaquetée. | Should | R2 | EX-10 |
| F-SOC-23 | API REST documentée | Toutes les fonctions exposées par une API sécurisée et documentée (OpenAPI). | Should | R2 | – |
| F-SOC-24 | Webhooks | Notifications sortantes vers des systèmes tiers lors d'événements (facture validée, paiement reçu…). | Could | R3 | – |
| F-SOC-25 | Portail client | Espace où le client final consulte ses devis et factures, les télécharge et les paie par Mobile Money. | Should | R2 | – |
| F-SOC-26 | Discussion interne | Fil de discussion et mentions sur chaque document (devis, commande, tâche…). | Could | R3 | – |
| F-SOC-27 | Circuits d'approbation | Demandes génériques soumises à validation (achat, dépense, congé, document). | Could | R3 | – |
| F-SOC-28 | Administration de la plateforme (éditeur) | Gestion des entreprises abonnées, des abonnements, des mises à jour et du support. | Must | R1 | – |
| F-SOC-29 | Paiement de l'abonnement par Mobile Money | L'entreprise règle son abonnement Ambawbio en FCFA par Mobile Money. | Must | R1 | EX-06 |
| F-CRM-01 | Fiche client enrichie | IFU, RCCM, secteur, localisation, historique unifié des échanges, documents et paiements. | Must | R1 | EX-01 |
| F-CRM-02 | Pipeline des opportunités | Pistes et opportunités en vue Kanban, étapes personnalisables, montant et probabilité. | Should ★ | R2 ★ | – |
| F-CRM-03 | Activités, agenda et rappels | Appels, rendez-vous et tâches planifiés avec rappels. | Should ★ | R2 ★ | – |
| F-CRM-04 | Saisie mobile hors-ligne | Les commerciaux terrain consultent et saisissent sur téléphone, même sans réseau. | Should ★ | R2 ★ | EX-05 |
| F-CRM-05 | Notation et segmentation | Score des prospects, segments de clientèle. | Could | R3 | – |
| F-CRM-06 | Prévisions et rapports commerciaux | Prévision de chiffre d'affaires, performance par commercial. | Could | R3 | – |
| F-VEN-01 | Catalogue et listes de prix | Prix par client, par quantité ou par période ; remises et marges. | Must | R1 | – |
| F-VEN-02 | Cycle devis, commande, facture | Transformation d'un devis en bon de commande puis en facture, sans ressaisie. | Must | R1 | – |
| F-VEN-03 | Conditions de paiement et acomptes | Échéances, acomptes et soldes, réglables par Mobile Money. | Must | R1 | EX-06 |
| F-VEN-04 | Suivi des impayés | Balance âgée des créances clients, relances. | Must | R1 | – |
| F-VEN-05 | Suivi des livraisons | Bons de livraison liés au stock, livraisons partielles. | Should | R2 | – |
| F-VEN-06 | Retenues à la source | Gestion des retenues sur les marchés publics et les grands comptes. | Should | R2 | – |
| F-VEN-07 | Relances par SMS et WhatsApp | Envoi automatique de rappels d'échéance et de liens de paiement. | Should | R2 | EX-14 |
| F-VEN-08 | Devis en ligne | Le client consulte, signe et paie le devis depuis un lien. | Could | R3 | – |
| F-POS-01 | Interface de caisse tactile | Écran rapide, recherche et lecture de codes-barres, adapté aux terminaux modestes. | Must | R1 | EX-10 |
| F-POS-02 | Sessions de caisse | Ouverture avec fonds de caisse, clôture avec comptage et écarts, rapport Z. | Must | R1 | – |
| F-POS-03 | Multi-caisses | Plusieurs caisses par magasin et plusieurs magasins. | Must | R1 | – |
| F-POS-04 | Encaissement multi-moyens | Espèces, Orange Money, Moov Money, Wave, carte ; paiement mixte. | Must | R1 | EX-06 |
| F-POS-05 | Tickets et factures FEC | Ticket thermique et Facture Électronique Certifiée à la demande du client. | Must | R1 | EX-01 |
| F-POS-06 | Caisse 100 % hors-ligne | Ventes, tickets et mouvements de stock continuent pendant les coupures. | Must | R1 | EX-05 |
| F-POS-07 | Retours et avoirs | Retour de marchandise avec avoir ou remboursement. | Must | R1 | – |
| F-POS-08 | Vendeurs et commissions | Attribution des ventes par vendeur, calcul des commissions. | Should | R2 | – |
| F-POS-09 | Programme de fidélité | Points, cartes de fidélité, bons d'achat. | Could | R3 | – |
| F-POS-10 | Mode restaurant | Plan de salle, tables, envoi des commandes en cuisine. | Could | R3 | – |
| F-ABO-01 | Contrats récurrents | Loyers, maintenance, scolarité, cotisations : facturation automatique. | Should | R2 | – |
| F-ABO-02 | Renouvellements et relances | Échéances, avenants, relances et paiement Mobile Money. | Should | R2 | EX-14 |
| F-ABO-03 | Location de matériel | Réservation, retrait, retour et facturation à la durée. | Could | R3 | – |
| F-ABO-04 | Réparations / service après-vente | Ordres de réparation, pièces consommées, facturation. | Could | R3 | – |
| F-CPT-01 | Plan comptable SYSCOHADA révisé | Préchargé, conforme, extensible. | Must | R1 | EX-03 |
| F-CPT-02 | Journaux, grand livre, balance | Saisie et consultation des écritures, balances générales et auxiliaires. | Must | R1 | EX-03 |
| F-CPT-03 | Écritures automatiques | Générées à partir des ventes, achats, caisse, stock et paie. | Must | R1 | EX-03 |
| F-CPT-04 | Lettrage | Rapprochement des factures et des règlements. | Must | R1 | – |
| F-CPT-05 | Rapprochement Mobile Money | Correspondance automatique entre transactions Mobile Money et factures. | Must | R1 | EX-06 |
| F-CPT-06 | États financiers SYSCOHADA | Bilan, compte de résultat, tableau des flux ; système normal et système minimal de trésorerie. | Must | R1 | EX-03 |
| F-CPT-07 | Rapprochement bancaire | Import de relevés et rapprochement assisté. | Should | R2 | – |
| F-CPT-08 | Liasse fiscale | Production automatique de la liasse fiscale annuelle. | Should | R2 | EX-03 |
| F-CPT-09 | Clôtures | Clôtures mensuelles et annuelles, reports à nouveau. | Should | R2 | – |
| F-CPT-10 | Immobilisations et amortissements | Registre, plans d'amortissement, cessions. | Should | R2 | – |
| F-CPT-11 | Comptabilité analytique | Par projet, centre de coût ou bailleur de fonds. | Should | R2 | EX-12 |
| F-CPT-12 | Budgets et trésorerie prévisionnelle | Budgets, suivi des écarts, prévision de trésorerie. | Could | R3 | EX-12 |
| F-FAC-01 | Numérotation inviolable | Séquence continue, aucune facture supprimable ; correction uniquement par avoir. | Must | R1 | EX-01 |
| F-FAC-02 | Facture Électronique Certifiée | Identifiant unique, QR code vérifiable, horodatage, transmission à la DGI selon les spécifications officielles. | Must | R1 | EX-01 |
| F-FAC-03 | Contrôle de l'IFU | Vérification de l'IFU du client et mentions légales obligatoires. | Must | R1 | EX-01 |
| F-FAC-04 | File d'attente de certification | Hors-ligne, les factures sont mises en attente puis certifiées au retour du réseau. | Must | R1 | EX-05 |
| F-FAC-05 | Avoirs | Avoirs totaux et partiels liés à la facture d'origine. | Must | R1 | – |
| F-FAC-06 | Archivage légal | Conservation des factures pendant la durée légale, non modifiables. | Must | R1 | – |
| F-FAC-07 | Télédéclarations préparées | Préparation des déclarations de TVA et de retenues. | Should | R2 | – |
| F-FAC-08 | Relances automatiques | Relances par e-mail, SMS et WhatsApp. | Should | R2 | EX-14 |
| F-PAY-01 | Encaissement Mobile Money | Demande de paiement (push ou QR) via un agrégateur agréé. | Must | R1 | EX-06 |
| F-PAY-02 | Suivi des paiements en temps réel | Statut reçu par notification de l'agrégateur, rapproché de la pièce. | Must | R1 | EX-06 |
| F-PAY-03 | Mode de secours | Saisie manuelle d'une référence de transaction, puis réconciliation. | Must | R1 | EX-06 |
| F-PAY-04 | Simulateur de paiement Mobile Money | Connecteur simulé (bac à sable) reproduisant les réponses des opérateurs : succès, échec, délai, notification. Utilisé pendant le développement et les démonstrations. | Must | R1 | EX-06 |
| F-PAY-05 | Connexion aux API réelles | Branchement du connecteur réel de l'agrégateur ou des opérateurs, au plus tard pour la version 1.0 commerciale. | Should | R2 | EX-06 |
| F-PAY-06 | Couche d'abstraction des opérateurs | Ajout d'un nouvel opérateur ou agrégateur sans modifier les modules métier. | Must | R1 | EX-06 |
| F-PAY-07 | Décaissements groupés | Paiement des salaires et des fournisseurs par lot (Mobile Money ou virement). | Should | R2 | EX-06 |
| F-NDF-01 | Notes de frais mobiles | Saisie avec photo du justificatif, circuit de validation, plafonds. | Should | R2 | – |
| F-NDF-02 | Remboursement par Mobile Money | Remboursement direct de l'employé. | Should | R2 | EX-06 |
| F-NDF-03 | Missions et per diem | Ordres de mission et indemnités journalières. | Could | R3 | – |
| F-NDF-04 | Signature électronique | Signature de devis, contrats et documents RH, horodatage, preuve. | Could | R3 | – |
| F-STK-01 | Entrepôts et emplacements | Plusieurs dépôts et emplacements par dépôt. | Must | R1 | – |
| F-STK-02 | Mouvements et transferts | Entrées, sorties, transferts inter-dépôts, tous tracés. | Must | R1 | – |
| F-STK-03 | Unités de conditionnement | Sac, carton, bidon, palette… et leurs conversions. | Must | R1 | – |
| F-STK-04 | Inventaires | Inventaires complets et tournants, y compris hors-ligne. | Must | R1 | EX-05 |
| F-STK-05 | Valorisation du stock | Coût moyen pondéré et premier entré, premier sorti. | Must | R1 | EX-03 |
| F-STK-06 | Lots, séries, péremption | Traçabilité par lot et date de péremption (pharmacie, agroalimentaire). | Should | R2 | – |
| F-STK-07 | Règles de réapprovisionnement | Stock minimum et maximum, propositions d'achat. | Should | R2 | – |
| F-STK-08 | Lecture mobile des codes-barres | Réception, préparation et inventaire avec la caméra du téléphone. | Should | R2 | – |
| F-ACH-01 | Demandes de prix et bons de commande | Cycle d'achat complet. | Must | R1 | – |
| F-ACH-02 | Réceptions | Réception totale ou partielle, mise à jour du stock. | Must | R1 | – |
| F-ACH-03 | Factures fournisseurs | Enregistrement et rapprochement avec la commande et la réception. | Must | R1 | – |
| F-ACH-04 | Coûts d'approche | Transit et dédouanement intégrés au coût de revient. | Should | R2 | – |
| F-ACH-05 | Échéancier fournisseurs | Suivi des dettes et des paiements. | Should | R2 | – |
| F-ACH-06 | Comparaison des offres | Tableau comparatif des devis fournisseurs. | Could | R3 | – |
| F-ACH-07 | Évaluation des fournisseurs | Délais, qualité, prix. | Could | R3 | – |
| F-MRP-01 | Nomenclatures et ordres de fabrication | Nomenclatures multi-niveaux, ordres de fabrication, consommations. | Could | R3 | – |
| F-MRP-02 | Planification et coûts de production | Postes de travail, gammes, calcul des besoins, coûts de revient. | Could | R3 | – |
| F-MRP-03 | Sous-traitance | Envoi de composants et réception de produits finis. | Could | R3 | – |
| F-MRP-04 | Contrôle qualité | Points de contrôle, non-conformités, actions correctives. | Could | R3 | – |
| F-MRP-05 | Maintenance des équipements | Préventive (heures de fonctionnement des groupes électrogènes, moulins) et corrective. | Could | R3 | – |
| F-MRP-06 | Parc de véhicules | Véhicules, carburant, entretiens, assurances. | Could | R3 | – |
| F-MRP-07 | Interventions sur site | Planification et compte rendu des interventions des techniciens. | Could | R3 | – |
| F-MRP-08 | Cycle de vie produit (PLM) | Versions de nomenclatures, ordres de modification technique. | Won't | R4 | – |
| F-RH-01 | Dossier employé | Contrats conformes au Code du travail, numéro CNSS, pièces, historique. | Should | R2 | EX-07 |
| F-RH-02 | Alertes d'échéances | Fin de contrat, fin de période d'essai. | Should | R2 | – |
| F-RH-03 | Congés et absences | Types, soldes, circuit de validation, jours fériés. | Should | R2 | EX-07 |
| F-RH-04 | Pointage mobile hors-ligne | Pointage sur téléphone pour les chantiers et sites distants. | Should | R2 | EX-05 |
| F-RH-05 | Heures supplémentaires | Calculées selon le Code du travail et les conventions. | Should | R2 | EX-07 |
| F-RH-06 | Organigramme et postes | Structure de l'organisation. | Could | R3 | – |
| F-RH-07 | Planning des équipes | Planification des postes et des rotations. | Could | R3 | – |
| F-RH-08 | Recrutement | Offres, candidatures depuis un téléphone, pipeline d'entretiens. | Could | R3 | – |
| F-RH-09 | Évaluations et formations | Campagnes d'évaluation, objectifs, plans de formation. | Could | R3 | – |
| F-RH-10 | Pointage biométrique | Intégration de pointeuses biométriques. | Won't | R4 | – |
| F-PAI-01 | Calcul de la paie conforme | IUTS, cotisations CNSS, taxe patronale et d'apprentissage ; barèmes paramétrables. | Should | R2 | EX-07 |
| F-PAI-02 | Éléments variables | Primes, avances, prêts, absences. | Should | R2 | – |
| F-PAI-03 | Bulletins et journal de paie | Bulletins PDF, livre de paie. | Should | R2 | EX-07 |
| F-PAI-04 | Écritures comptables de paie | Génération automatique dans la comptabilité. | Should | R2 | EX-03 |
| F-PAI-05 | Déclarations sociales et fiscales | États CNSS et IUTS préparés. | Should | R2 | EX-07 |
| F-PAI-06 | Paiement des salaires | Par virement ou Mobile Money. | Should | R2 | EX-06 |
| F-PAI-07 | Soldes de tout compte | Calcul en fin de contrat. | Could | R3 | – |
| F-WEB-01 | Éditeur visuel de sites | Création par glisser-déposer, modèles sectoriels, pages légères. | Could | R3 | – |
| F-WEB-02 | Boutique en ligne | Vitrine minimale prête à l'emploi, catalogue, panier, stock et prix synchronisés, paiement Mobile Money, paiement à la livraison. | Should | R2 | EX-06 |
| F-WEB-03 | Chat en direct | Discussion sur le site, prolongée sur WhatsApp. | Could | R3 | EX-14 |
| F-WEB-04 | Blog | Articles et actualités. | Could | R3 | – |
| F-WEB-05 | Forum | Communautés d'utilisateurs. | Won't | R4 | – |
| F-WEB-06 | eLearning | Cours en ligne. | Won't | R4 | – |
| F-MKT-01 | Campagnes SMS | Envoi segmenté via les opérateurs et agrégateurs locaux. | Could | R3 | EX-14 |
| F-MKT-02 | Campagnes e-mail | Gabarits légers, listes, désinscription, consentement. | Could | R3 | EX-08 |
| F-MKT-03 | Automatisations marketing | Scénarios : bienvenue, relance, anniversaire, fêtes. | Could | R3 | – |
| F-MKT-04 | Événements et billetterie | Inscriptions, billets QR payés par Mobile Money, contrôle d'accès hors-ligne. | Could | R3 | – |
| F-MKT-05 | Sondages et enquêtes | Questionnaires, collecte terrain hors-ligne. | Could | R3 | – |
| F-MKT-06 | Marketing sur les réseaux sociaux | Publication et suivi des réseaux sociaux. | Won't | R4 | – |
| F-PRJ-01 | Projets et tâches | Tâches, jalons, vues Kanban et Gantt. | Should | R2 | – |
| F-PRJ-02 | Suivi budgétaire par bailleur | Budget par projet et par source de financement, rapports de suivi-évaluation. | Should | R2 | EX-12 |
| F-PRJ-03 | Feuilles de temps | Saisie mobile hors-ligne, validation, facturation au temps passé. | Should | R2 | – |
| F-PRJ-04 | Gestion électronique de documents | Dépôt, classement, versions, recherche, droits d'accès. | Should | R2 | – |
| F-PRJ-05 | OCR et circuits d'approbation documentaire | Reconnaissance de texte des pièces numérisées, workflows. | Could | R3 | – |
| F-PRJ-06 | Centre d'assistance | Tickets multicanaux (e-mail, web, WhatsApp), SLA, satisfaction. | Could | R3 | – |
| F-PRJ-07 | Base de connaissances | Wiki interne et articles d'aide. | Could | R3 | – |
| F-PRJ-08 | Analyse et tableaux croisés | Feuilles de calcul et tableaux croisés sur les données de la suite. | Could | R3 | – |
| F-PRJ-09 | Prise de rendez-vous en ligne | Réservation de créneaux par les clients. | Won't | R4 | – |
| F-STU-01 | Champs personnalisés | Ajout de champs (texte, nombre, date, liste, fichier) sur les écrans et documents. | Must | R1 | – |
| F-STU-02 | Formulaires sur mesure | Formulaires de saisie, de collecte et d'approbation. | Could | R3 | – |
| F-STU-03 | Rapports et tableaux de bord personnalisés | Vues, filtres et graphiques construits par l'utilisateur. | Could | R3 | – |
| F-STU-04 | Automatisations et règles | Actions déclenchées par des conditions (alerte, validation, SMS). | Could | R3 | – |
| F-STU-05 | Applications métier légères | Modèles de données, vues et actions assemblés sans code. | Won't | R4 | – |
| F-STU-06 | Modèles sectoriels | Configurations prêtes : pharmacie, école, garage, quincaillerie, ONG. | Could | R3 | – |
| F-STU-07 | Marketplace et kit de développement | Extensions de partenaires, commissions. | Won't | R4 | – |

### 18.2 Cas d'utilisation du MVP (AMB-CONC-05 v1.1)

| ID | Cas d'utilisation | Acteur principal | Fonctionnalités |
|---|---|---|---|
| UC-SOC-01 | S'authentifier | Utilisateur | F-SOC-01 à 03 |
| UC-SOC-02 | Paramétrer l'entreprise | Administrateur entreprise | F-SOC-04, 05, 09 |
| UC-SOC-03 | Gérer les utilisateurs et les rôles | Administrateur entreprise | F-SOC-03 |
| UC-SOC-04 | Enregistrer un terminal | Administrateur entreprise | F-SOC-11 |
| UC-SOC-05 | Synchroniser un terminal | Utilisateur | F-SOC-11 à 13 |
| UC-SOC-06 | Importer des données | Administrateur entreprise | F-SOC-16 |
| UC-SOC-07 | Consulter le tableau de bord | Dirigeant | F-SOC-14, 15 |
| UC-SOC-08 | Consulter le journal d'audit | Administrateur entreprise | F-SOC-10 |
| UC-SOC-09 | Gérer les tiers et les produits | Utilisateur habilité | F-SOC-06, 07 |
| UC-SOC-10 | Ajouter un champ personnalisé | Administrateur entreprise | F-STU-01 |
| UC-SOC-11 | Traiter une demande sur les données personnelles | Administrateur entreprise | F-SOC-18 |
| UC-SOC-12 | Souscrire et payer l'abonnement | Administrateur entreprise | F-SOC-05, 29 |
| UC-SOC-13 | Gérer les entreprises abonnées | Administrateur plateforme | F-SOC-28 |
| UC-POS-01 | Ouvrir une session de caisse | Caissier | F-POS-02, 03 |
| UC-POS-02 | Enregistrer une vente | Caissier | F-POS-01, 06 |
| UC-POS-03 | Encaisser | Caissier | F-POS-04 |
| UC-POS-04 | Payer par Mobile Money | Caissier | F-PAY-01 à 04 |
| UC-POS-05 | Émettre le ticket et la facture certifiée | Caissier | F-POS-05 |
| UC-POS-06 | Traiter un retour | Caissier | F-POS-07 |
| UC-POS-07 | Clôturer la session | Caissier | F-POS-02 |
| UC-POS-08 | Valider un écart de caisse | Responsable de magasin | F-POS-02 |
| UC-VEN-01 | Établir un devis | Commercial | F-VEN-01, 02 |
| UC-VEN-02 | Convertir le devis en commande | Commercial | F-VEN-02 |
| UC-VEN-03 | Facturer une commande | Commercial | F-VEN-02 |
| UC-VEN-04 | Enregistrer un acompte | Commercial | F-VEN-03 |
| UC-VEN-05 | Suivre les impayés | Comptable | F-VEN-04 |
| UC-VEN-06 | Relancer un client | Comptable | F-VEN-07 (R2) |
| UC-VEN-07 | Payer une facture par Mobile Money | Client final | F-PAY-01 |
| UC-FAC-01 | Valider et certifier une facture | Comptable | F-FAC-01 à 06 |
| UC-FAC-02 | Émettre un avoir | Comptable | F-FAC-05 |
| UC-CPT-01 | Consulter journaux, grand livre et balance | Comptable | F-CPT-02 |
| UC-CPT-02 | Saisir une écriture manuelle | Comptable | F-CPT-02 |
| UC-CPT-03 | Lettrer des comptes | Comptable | F-CPT-04 |
| UC-CPT-04 | Produire les états financiers | Comptable | F-CPT-06 |
| UC-CPT-05 | Générer les écritures automatiques | Planificateur (système) | F-CPT-03 |
| UC-CPT-06 | Changer de dossier | Expert-comptable externe | F-SOC-04 |
| UC-PAY-01 | Recevoir une notification de paiement | Agrégateur (système) | F-PAY-02 |
| UC-PAY-02 | Rapprocher les paiements Mobile Money | Comptable | F-CPT-05 |
| UC-ACH-01 | Demander des prix | Acheteur | F-ACH-01 |
| UC-ACH-02 | Passer une commande fournisseur | Acheteur | F-ACH-01 |
| UC-ACH-03 | Réceptionner une commande | Magasinier | F-ACH-02 |
| UC-ACH-04 | Enregistrer une facture fournisseur | Comptable | F-ACH-03 |
| UC-STK-01 | Transférer du stock entre dépôts | Magasinier | F-STK-02 |
| UC-STK-02 | Réaliser un inventaire | Magasinier | F-STK-04 |
| UC-STK-03 | Valider un inventaire | Responsable de magasin | F-STK-04 |
| UC-STK-04 | Consulter le stock et sa valeur | Comptable, responsable | F-STK-05 |

### 18.3 Scénarios de séquence (AMB-CONC-07 v1.1) — base des tests de bout en bout

| ID | Scénario | Cas d'utilisation | Règles |
|---|---|---|---|
| SD-01 | S'authentifier | UC-SOC-01 | RG-11, RG-14 ; ENF-05 |
| SD-02 | Enregistrer une vente hors-ligne | UC-POS-02, UC-POS-03, UC-POS-05 | RG-01, RG-03, RG-04, RG-05 ; ENF-01, ENF-03 |
| SD-03 | Synchroniser un terminal | UC-SOC-05 | RG-03, RG-04, RG-05, RG-06 ; ENF-01, ENF-02 |
| SD-04 | Payer par Mobile Money | UC-POS-04, UC-PAY-01 | RG-07, RG-08 |
| SD-05 | Valider et certifier une facture | UC-FAC-01 | RG-01, RG-02, RG-03, RG-08 ; ENF-07 |
| SD-06 | Clôturer une session de caisse | UC-POS-07, UC-POS-08 | RG-08, RG-09, RG-11 |
| SD-07 | Réceptionner une commande et la comptabiliser | UC-ACH-03, UC-CPT-05 | RG-05, RG-08, RG-13 |
| SD-08 | Traiter un retour et émettre un avoir | UC-POS-06, UC-FAC-02 | RG-01, RG-05, INV-04 |
| SD-09 | Rapprocher les paiements Mobile Money | UC-PAY-02 | RG-07, RG-08 |
| SD-10 | Du devis à la facture | UC-VEN-01 à UC-VEN-04 | RG-01, RG-08 |
| SD-11 | Enregistrer un terminal et charger ses données | UC-SOC-04 | RG-03, RG-14 ; ENF-05 |
| SD-12 | Réaliser et valider un inventaire | UC-STK-02, UC-STK-03 | RG-05, RG-11 |
| SD-13 | Souscrire et payer l'abonnement | UC-SOC-12 | INV-15 ; modèle économique |

### 18.4 Exigences non fonctionnelles

| ID | Exigence | Niveau | Priorité |
|---|---|---|---|
| ENF-01 | Hors-ligne | Caisse, facturation, stock, inventaire, pointage : 7 jours sans réseau | Must |
| ENF-02 | Faible débit | Utilisable en 3G, dégradé en 2G | Must |
| ENF-03 | Performance | Ajout d'article < 300 ms ; écran < 2 s en 3G | Must |
| ENF-04 | Terminaux | Android 8+, 5 pouces ; PC 4 Go ; navigateurs récents | Must |
| ENF-05 | Sécurité | TLS 1.2+, chiffrement au repos, OWASP ASVS niveau 2 | Must |
| ENF-06 | Isolation | Multi-tenant strict testé | Must |
| ENF-07 | Conformité | FEC, SYSCOHADA, Code du travail, loi n°001-2021/AN | Must |
| ENF-08 | Conservation | Durée légale (10 ans OHADA, à confirmer) | Must |
| ENF-09 | Ergonomie | Caissier formé en moins de 30 min | Must |
| ENF-10 | Maintenabilité | Monolithe modulaire, règles fiscales testées à 100 % | Must |
| ENF-11 | Langue | Français, internationalisation | Must |
| ENF-12 | Disponibilité | 99,5 % par mois | Should |
| ENF-13 | Continuité | RPO 1 h, RTO 4 h, 3-2-1 | Should |
| ENF-14 | Capacité | 500 entreprises, 2 000 utilisateurs simultanés | Should |
| ENF-15 | Réversibilité | Export complet en formats ouverts | Should |
| ENF-16 | Accessibilité | Adaptatif, WCAG AA visé | Should |

### 18.5 Évolutions prévues

| ID | Évolution | Échéance |
|---|---|---|
| EVO-01 | Notifications instantanées serveur → application (SSE ou WebSocket) à la place de l'interrogation périodique | R2 ou R3 |
| EVO-02 | Connexion aux API réelles Mobile Money | Au plus tard V1.0 |
| EVO-03 | Langues nationales | Après V1.0 |
| EVO-04 | Connecteurs bancaires directs, télédéclarations en ligne | Selon partenariats |
| EVO-05 | Localisations UEMOA / OHADA | R4 |

### 18.6 Glossaire

| Terme | Définition |
|---|---|
| FEC | Facture Électronique Certifiée de la DGI (identifiant unique, QR code, horodatage) |
| IFU | Identifiant Financier Unique |
| RCCM | Registre du commerce et du crédit mobilier |
| SYSCOHADA | Système comptable OHADA révisé |
| IUTS / CNSS | Impôt unique sur les traitements et salaires / Caisse nationale de sécurité sociale |
| Tenant | Entreprise cliente de la plateforme |
| RLS | Row-Level Security, sécurité au niveau des lignes de PostgreSQL |
| Outbox | File locale des opérations à envoyer au serveur |
| Idempotence | Un rejeu produit le même résultat, sans doublon |
| Plage de numérotation | Bloc de numéros de pièces réservé à un terminal pour travailler hors-ligne |
| Agrégateur | Prestataire agréé donnant accès à plusieurs portefeuilles Mobile Money |

---

*Fin du guide. En cas de contradiction entre ce guide et un livrable de conception plus récent, le livrable le plus récent validé par le porteur du projet fait foi ; signale la contradiction dans `docs/QUESTIONS.md`.*
