# LOT 0 — Initialisation du projet

**Objectif** (guide §15) : un dépôt prêt, qui compile, teste et se déploie localement.
**Statut** : terminé, en attente de validation (démonstration de l'APK à faire sur un téléphone).

## Plan et réalisation

| Tâche | Fait | Fichiers |
|---|---|---|
| Structure du dépôt (§5) | ✅ | `backend/`, `frontend/`, `infra/`, `docs/`, `.github/` |
| `CLAUDE.md`, `docs/DECISIONS.md`, `docs/QUESTIONS.md` | ✅ | déjà présents, complétés |
| Projet Spring Boot 4.1 (Java 25) + `ModularityTest` | ✅ | `backend/` |
| Angular 22 + Tailwind 4 (jetons Claude Design) + Transloco + ESLint + Vitest + Playwright | ✅ | `frontend/` |
| Capacitor Android initialisé (icônes de la marque) | ✅ | `frontend/android/`, `frontend/capacitor.config.ts` |
| `docker-compose.dev.yml` (PostgreSQL 18, Keycloak 26.8, Mailpit, MinIO en profil) | ✅ | `infra/` |
| Royaume Keycloak de démonstration (6 utilisateurs, 7 rôles) | ✅ | `infra/keycloak/realm-ambawbio.json` |
| Workflows `backend.yml`, `frontend.yml`, `codeql.yml` (+ `android.yml` manuel) | ✅ | `.github/workflows/` |
| Dependabot, modèle de pull request | ✅ | `.github/` |

## Critères d'acceptation

| Critère | Résultat |
|---|---|
| `./mvnw verify` au vert | ✅ en local (8 tests, Modulith, Checkstyle, PostgreSQL 18 par Testcontainers). CI : à confirmer sur la pull request |
| `npm test` au vert | ✅ en local (7 tests Vitest) ; lint et build de production au vert (79 Ko transférés au premier affichage) |
| Page « Ambawbio Suite » authentifiée par Keycloak | ✅ vérifié par Playwright (téléphone 393 px et bureau 1440 px) : connexion d'Awa, rôles affichés, appel `/api/moi` validé par le serveur |
| APK de démonstration installable | ⏳ construit par le workflow `android` (lancement manuel) ; installation sur un téléphone à faire par le porteur du projet |

## Démarrer en local

```bash
docker compose -f infra/docker-compose.dev.yml up -d          # PostgreSQL, Keycloak, Mailpit
cd backend && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run  # API sur :8080
cd frontend && npm ci && npm start                              # http://localhost:4200 (awa / demo-ambawbio)
cd frontend && npm run e2e                                      # test de connexion de bout en bout
```

## APK de démonstration sur un téléphone

1. Relever l'adresse IP du poste sur le réseau local (ex. `192.168.1.20`), téléphone sur le même Wi-Fi.
2. Démarrer l'environnement avec cette adresse : `AMBAWBIO_HOTE_DEV=192.168.1.20 docker compose -f infra/docker-compose.dev.yml up -d`, puis le serveur avec `AMBAWBIO_KEYCLOAK_EMETTEUR=http://192.168.1.20:8180/realms/ambawbio AMBAWBIO_KEYCLOAK_JWKS=http://192.168.1.20:8180/realms/ambawbio/protocol/openid-connect/certs`.
3. Sur GitHub : Actions → **android** → *Run workflow*, saisir `http://192.168.1.20:8080/api` et `http://192.168.1.20:8180` (depuis le LOT 5, adresses complètes ; pour Railway, voir `docs/deploiement/railway.md`). Télécharger l'artefact `ambawbio-suite-demo-apk`.
4. Installer l'APK sur le téléphone (autoriser les sources inconnues), se connecter avec `awa` / `demo-ambawbio`.

Ces autorisations HTTP (texte en clair, contenu mixte) servent uniquement à la démonstration et seront supprimées au LOT 13.

## Écarts par rapport au plan

- Dépendances de la section 4 non encore utilisées (MapStruct, jOOQ, JasperReports, ShedLock, Caffeine, ZXing, Dexie, ECharts, SQLite…) : ajoutées dans le lot qui en a besoin, avec justification (D-09).
- Thème Keycloak aux couleurs de la marque (écran A-03) : LOT 3.
- `e2e.yml` (Playwright en CI sur l'environnement complet) : à partir du LOT 3, quand les parcours SD arrivent.
- MinIO : image communautaire plus publiée sur Docker Hub (Q-14) ; service placé dans un profil Compose, inutilisé avant le LOT 6.
