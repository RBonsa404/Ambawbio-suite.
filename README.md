# Ambawbio Suite

> « Celui qui connaît demain » — Suite de gestion d'entreprise intégrée (ERP) pour le Burkina Faso.

## Présentation

Ambawbio Suite est un ERP tout-en-un inspiré d'Odoo, conçu pour les entreprises burkinabè. Il couvre huit piliers sur une base de données unique :

| Pilier | Description |
|---|---|
| Ventes & Relation client | CRM, devis, facturation, caisse POS |
| Finance & Comptabilité | SYSCOHADA révisé, FEC (DGI), déclarations |
| Opérations & Logistique | Stock, achats, inventaire, réception |
| Ressources humaines | Paie, congés, notes de frais (R2) |
| Site Web & E-commerce | Boutique en ligne, pages publiques (R2) |
| Marketing & Communication | Campagnes, SMS, e-mail (R3) |
| Productivité & Projets | Tâches, Kanban, GED (R2) |
| Studio No-Code | Champs personnalisés, vues (R3) |

## Trois promesses

- **Conformité locale native** — SYSCOHADA, IFU, Facture Électronique Certifiée (FEC/DGI)
- **Hors-ligne** — Caisse, facturation, stock : au moins 7 jours sans réseau
- **Mobile Money natif** — Orange Money, Moov Money, Wave

## Pile technique

- **Back-end** : Java 25, Spring Boot 4.x (monolithe modulaire), PostgreSQL 18
- **Front-end** : Angular 22 + Tailwind CSS 4
- **Mobile** : Android via Capacitor
- **Identité** : Keycloak

## Structure du dépôt

```
ambawbio-suite/
├── CLAUDE.md              Instructions pour l'agent de développement
├── docs/
│   ├── conception/        Livrables AMB-CONC-01 à 09
│   └── design/            Identité visuelle, tokens, maquettes
├── logo/                  Symboles SVG (variantes)
├── backend/               (à venir)
├── frontend/              (à venir)
└── infra/                 (à venir)
```

## Licence

Projet privé — © 2026 Rachid Bonsa. Tous droits réservés.
