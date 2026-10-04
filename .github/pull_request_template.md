## Objet

<!-- Lot concerné (LOT-xx), cas d'utilisation (UC), scénarios (SD), règles (RG, INV). -->

## Ce qui change

## Vérifications exécutées

- [ ] `cd backend && ./mvnw verify`
- [ ] `cd frontend && npm run lint && npm test && npm run build`
- [ ] Tests de bout en bout du lot (`npm run e2e`) si un parcours est concerné

## Liste de contrôle (guide §16.2)

- [ ] Le module ne dépend que d'API publiques d'autres modules (Modulith vert).
- [ ] Toute nouvelle table : `tenant_id`, RLS, index, migration Flyway nouvelle (pas de modification d'une ancienne).
- [ ] Montants en `Montant`/`bigint`, quantités en `BigDecimal`/`numeric(18,3)`.
- [ ] Créations idempotentes (identifiant fourni par le client).
- [ ] Aucune spécification officielle inventée ; simulateurs utilisés ; questions notées dans `docs/QUESTIONS.md`.
- [ ] Nouvelle dépendance justifiée ci-dessous.
- [ ] Aucun secret.
- [ ] Écrans conformes aux maquettes et aux jetons de design (aucune couleur ni police en dur, R-16).

## Dépendances ajoutées (justification)
