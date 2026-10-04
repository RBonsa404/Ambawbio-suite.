# Journal des modifications

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/), versions [SemVer](https://semver.org/lang/fr/).

## [Non publié]

### Ajouté
- LOT 1 : socle technique — multi-tenant à triple barrière (jeton, Hibernate, RLS PostgreSQL), entreprises, sociétés (INV-15), établissements, dépôts, modules par pack, utilisateurs synchronisés avec Keycloak, rôles et permissions « module:action » par établissement (RG-14), barèmes versionnés (RG-10), journal d'audit chaîné et vérifié chaque nuit (RG-11), administration de la plateforme, demandes sur les données personnelles, MFA conditionnelle dans Keycloak, données de démonstration.
- LOT 0 : serveur Spring Boot 4.1 (Java 25, Spring Modulith) avec `GET /api/moi` sécurisé par Keycloak ; client Angular 22 + Tailwind 4 aux jetons Claude Design, Transloco, page d'accueil authentifiée ; projet Android Capacitor ; environnement local Docker Compose et royaume Keycloak de démonstration ; CI GitHub Actions (backend, frontend, CodeQL, APK de démonstration) et Dependabot.
- Paquet de design Claude Design intégré dans `docs/design` (polices auto-hébergées, icônes PNG, vérification).
