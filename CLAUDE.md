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
