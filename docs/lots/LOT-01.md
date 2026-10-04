# LOT 1 — Socle technique

**Références** : F-SOC-01, 03, 04, 05, 09, 10, 18, 21, 28 ; UC-SOC-01, 02, 03, 08, 13 ; SD-01 ; RG-10, 11, 14, 16 ; INV-12, 15.
**Acceptation** : tests d'isolation entre deux entreprises au vert ; MFA exigée pour un comptable ; chaîne d'audit vérifiée ; règle INV-15 testée.

## Plan

| Tranche | Contenu | Tests prévus |
|---|---|---|
| 1. `shared` | Montant, Devise, Ifu, Uuid7, EntiteMetier, erreurs ProblemDetail (fr), pagination | Unitaires `Montant`, `Ifu`, `Uuid7` |
| 2. Multi-tenant (§6.4) | `ContexteTenant`, `@TenantId` Hibernate, `set_config('app.tenant_id')` à chaque emprunt de connexion, RLS sur toutes les tables, rôle applicatif sans `BYPASSRLS` | Isolation A/B par JPA **et** par SQL brut (RLS seule) |
| 3. Tenancy | Entreprise, Société (INV-15/RG-16), Établissement, Dépôt, modules actifs par pack | `RG_16`/`INV_15` domaine + API |
| 4. Identité | Utilisateur, Rôle, Permission `module:action`, AffectationRole par établissement (RG-14), synchronisation Keycloak par un port (`PortAnnuaire`) + simulateur | 403 sans permission, établissement non autorisé refusé |
| 5. Paramétrage | Barème versionné par date d'effet (RG-10, INV-13) | Barème en vigueur à une date ; doublon refusé |
| 6. Audit | Journal chaîné SHA-256 par entreprise, verrou consultatif, insertion seule, vérification nocturne (ShedLock) | Chaîne vérifiée ; altération détectée ; UPDATE refusé |
| 7. Administration plateforme | Création d'entreprise (société, siège, dépôt, rôles et barèmes par défaut, administrateur dans Keycloak), suspension | Parcours complet avec simulateur |
| 8. Données personnelles | Registre des traitements, demandes d'accès/rectification/effacement | API |
| 9. Keycloak | Revendication `tenant_id`, MFA conditionnelle (comptable, administrateur, dirigeant), client de service | Playwright : OTP demandé à Mariam |

Risques : sécurité et modèle de données (guide §0.2) → choix consignés dans `docs/DECISIONS.md` (D-12 à D-16).

## Compte rendu

**Statut** : terminé, en attente de validation.

### Critères d'acceptation

| Critère | Résultat | Preuve |
|---|---|---|
| Isolation entre deux entreprises | ✅ | `IsolationEntreprisesTest` : API (établissements, utilisateurs, fiche, journal), usurpation de `tenant_id` refusée, RLS seule en SQL brut (lecture, absence de contexte, écriture pour une autre entreprise refusée) |
| MFA exigée pour un comptable | ✅ | Playwright `connexion.spec.ts` : Mariam doit configurer l'application d'authentification ; Awa (caissière) n'y est pas soumise |
| Chaîne d'audit vérifiée | ✅ | `JournalAuditTest` : chaîne intègre, altération directe en base détectée à la bonne entrée, UPDATE/DELETE refusés |
| INV-15 testée | ✅ | `SocietesTest` : 2ᵉ société refusée (pack Business), acceptée (Enterprise), création rejouée sans doublon |

Autres tests : permissions et établissements (RG-14), utilisateur désactivé, barèmes (RG-10, INV-13), modules hors pack (F-SOC-05), création/suspension/réactivation par l'éditeur (UC-SOC-13), demandes sur les données personnelles (F-SOC-18), objets valeur (`Montant`, `Ifu`, `Uuid7`). Contrat de l'adaptateur Keycloak (`AnnuaireKeycloakTest`) vérifié contre le royaume local : compte créé avec `tenant_id`, rôles alignés, compte désactivé, courriel de définition du mot de passe reçu (Mailpit).

Total : 27 tests serveur au vert (+ 1 test de contrat Keycloak à lancer à la demande), 7 tests client, 4 scénarios Playwright (2 × téléphone et bureau).

### API livrée (`/api/v1`)

| Méthode et chemin | Permission |
|---|---|
| `GET /socle/contexte`, `POST /socle/connexions` | utilisateur rattaché |
| `GET/PUT /socle/entreprise`, `GET/POST /socle/societes`, `/socle/etablissements`, `/socle/depots`, `GET/PUT /socle/modules` | `socle:consulter` / `socle:parametrer` |
| `GET/POST/PUT /socle/utilisateurs`, `POST /socle/utilisateurs/{id}/affectations`, `DELETE /socle/affectations/{id}`, `GET/POST/PUT /socle/roles` | `socle:utilisateurs` |
| `GET /socle/baremes`, `GET /socle/baremes/{code}?date=`, `POST /socle/baremes` | `socle:consulter` / `socle:parametrer` |
| `GET /audit/journal`, `GET /audit/journal/verification` | `audit:consulter` |
| `GET /socle/donnees-personnelles/registre`, `GET/POST …/demandes`, `POST …/demandes/{id}/prise-en-charge`, `…/cloture` | `donnees-personnelles:traiter` |
| `GET/POST /plateforme/entreprises`, `POST …/{id}/suspension`, `…/{id}/reactivation` | rôle Keycloak `admin-plateforme` |

Documentation interactive : `http://localhost:8080/swagger-ui.html` (profil `dev`).

### Écarts et reports

- **Écrans** (paramètres W-18, journal W-20, console W-23) : LOT 3 (socle de l'application) ; la page d'accueil affiche déjà l'entreprise et les établissements autorisés.
- **Effacement effectif (RG-12)** : les demandes sont tracées ; l'anonymisation sera réalisée par chaque module qui stocke des données de personnes (référentiel au LOT 2).
- **Alerte de rupture d'audit** : compteur Micrometer + journal d'erreur ; l'envoi d'alertes (Prometheus/Grafana) arrive au LOT 13.
- **Cabinet comptable multi-dossiers** (F-SOC-04, UC-CPT-06) : un utilisateur appartient aujourd'hui à une entreprise ; le changement de dossier sera traité au LOT 10.
- **Création de compte Keycloak** : faite pendant la transaction de création ; si la transaction échoue ensuite, le compte reste dans Keycloak (sans droits). Nettoyage automatique à prévoir au LOT 11.

### Démarrer avec les données de démonstration

```bash
docker compose -f infra/docker-compose.dev.yml down -v && docker compose -f infra/docker-compose.dev.yml up -d
cd backend && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # crée Wend-Panga et Pharmacie du Progrès
cd frontend && npm start                                          # awa / demo-ambawbio
```

Le volume PostgreSQL doit être recréé une fois (`down -v`) pour créer le rôle `ambawbio_app`.
