# Journal des modifications

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/), versions [SemVer](https://semver.org/lang/fr/).

## [Non publié]

### Ajouté
- LOT 4 : moteur de synchronisation — terminaux appairés par QR code et clés ECDSA P-256, révocation, opérations signées et idempotentes (push par lots, accusés par opération, conflits RG-06), flux de changements paginé et filtré par établissement (pull), plages de numérotation sans chevauchement (INV-11) avec nouvelle plage à 80 %, compression gzip ; premier gestionnaire `FICHE_CLIENT_MODIFIEE` ; côté application : stockage local IndexedDB, file d'envoi, agent de synchronisation (réessais, alerte 24 h), écrans Terminaux (W-18), Appairage (A-02) et Synchronisation (A-17), indicateur relié à l'état réel.
- Coquille ajustée d'après les captures des maquettes (titres, sélecteurs entreprise/établissement, menu actif).
- LOT 3 : application — jetons complétés, bibliothèque de composants (boutons, champs, montant FCFA, quantité et conditionnement, tableau, notifications, confirmation, indicateur de synchronisation, champs personnalisés dynamiques), coquille (barre latérale, contexte entreprise/établissement, navigation basse), écrans Produits, Clients et fournisseurs, Import, pages système, thème de connexion Keycloak, verrouillage par code PIN sur Android ; accessibilité WCAG AA vérifiée automatiquement.
- LOT 2 : référentiels (tiers, produits, conditionnements, codes-barres, catégories, unités, taxes, régimes fiscaux, listes de prix), recherche rapide sans accents, champs personnalisés du Studio (typés, filtrables), import CSV/Excel avec rapport d'erreurs ligne par ligne, données de démarrage par événement, catalogue de démonstration.
- Questions ouvertes tranchées (Q-01 à Q-17, sauf Q-09) avec sources ; stockage S3 de développement : SeaweedFS.
- LOT 1 : socle technique — multi-tenant à triple barrière (jeton, Hibernate, RLS PostgreSQL), entreprises, sociétés (INV-15), établissements, dépôts, modules par pack, utilisateurs synchronisés avec Keycloak, rôles et permissions « module:action » par établissement (RG-14), barèmes versionnés (RG-10), journal d'audit chaîné et vérifié chaque nuit (RG-11), administration de la plateforme, demandes sur les données personnelles, MFA conditionnelle dans Keycloak, données de démonstration.
- LOT 0 : serveur Spring Boot 4.1 (Java 25, Spring Modulith) avec `GET /api/moi` sécurisé par Keycloak ; client Angular 22 + Tailwind 4 aux jetons Claude Design, Transloco, page d'accueil authentifiée ; projet Android Capacitor ; environnement local Docker Compose et royaume Keycloak de démonstration ; CI GitHub Actions (backend, frontend, CodeQL, APK de démonstration) et Dependabot.
- Paquet de design Claude Design intégré dans `docs/design` (polices auto-hébergées, icônes PNG, vérification).
