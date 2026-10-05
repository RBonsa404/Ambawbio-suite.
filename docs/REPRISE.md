# Reprise du projet — instructions pour Claude Code (en local)

Ce document permet à une nouvelle session Claude Code de reprendre Ambawbio Suite **là où il en est** et de mener la plateforme **jusqu'au produit fini**. Lisez-le en entier avant toute action, puis lisez `CLAUDE.md` et `docs/conception/AMB-CONC-09-guide-claude-code.md`.

## 1. Récupérer le projet

```bash
git clone https://github.com/RBonsa404/Ambawbio-suite..git ambawbio-suite
cd ambawbio-suite
docker compose -f infra/docker-compose.dev.yml up -d   # PostgreSQL 18, Keycloak, Mailpit
cd backend && ./mvnw verify                             # Java 25 requis
cd ../frontend && npm ci && npm run lint && npm test && npm run build
```

Le nom du dépôt se termine par un point (`Ambawbio-suite.`). Avec `gh`, utilisez l'API REST (`gh api repos/RBonsa404/Ambawbio-suite./...`).

## 2. Où en est le projet (5 octobre 2026)

| Lot | Contenu | État |
|---|---|---|
| 0 à 3 | Initialisation, socle technique (multi-entreprise, RLS, Keycloak, audit), référentiels et import, socle de l'application Angular | Terminé, fusionné |
| 4 | Moteur de synchronisation et terminaux (appairage, plages de numéros) | Terminé (PR #15) |
| 5 | Caisse hors-ligne (ventes, encaissements, sessions de caisse, PIN responsable) | Terminé (PR #16) |
| 6 | Facturation et conformité FEC (numérotation sans trou, avoirs, PDF, `PortCertificationFiscale` + `SimulateurFec`) | Terminé (PR #17) |
| Déploiement | Démo Railway en ligne (4 services : `postgres`, `keycloak`, `serveur`, `application`) | Fonctionne (PR #18, #20 ; D-39, D-40) |
| **7 à 14** | Voir § 4 | **À faire** |

Sources de vérité :

- les plans et bilans de chaque lot sont dans `docs/lots/LOT-xx.md` ;
- les décisions D-01 à D-40 sont dans `docs/DECISIONS.md` ;
- les questions ouvertes sont dans `docs/QUESTIONS.md` ;
- le déploiement de démonstration est décrit dans `docs/deploiement/railway.md`.

L'instance Railway se met à jour à chaque fusion dans `main`. Ses variables (mots de passe compris) sont posées dans Railway, jamais dans le dépôt.

## 3. Objectif de cette reprise : le produit fini

Le but n'est plus seulement le MVP. Il faut livrer **la plateforme entière, fonctionnelle à 100 %**. La seule exception concerne les dépendances externes qui exigent un contrat ou une spécification officielle :

- DGI (certification FEC) ;
- agrégateurs ou opérateurs Mobile Money ;
- banques ;
- passerelle SMS ou WhatsApp.

Pour chacune de ces dépendances :

- **Un port, un simulateur, un adaptateur prêt à brancher.** Le port (`PortCertificationFiscale`, `PortPaiement`, `PortBanque`, `PortMessagerie`) et le simulateur restent. L'adaptateur réel est écrit jusqu'à la frontière exacte de la spécification manquante : configuration, authentification, gestion d'erreurs, relances, journalisation et tests contractuels sur des réponses simulées. Les champs inconnus sont signalés dans `docs/QUESTIONS.md`. **Ne jamais inventer** un format officiel.
- **Le choix se fait par configuration**, avec une propriété par port (`ambawbio.integrations.<port>=simulateur|reel`). Passer en réel ne doit demander que des identifiants et la spécification, sans aucune refonte.
- **Une page « Intégrations » dans la console éditeur.** Elle affiche, pour chaque port, le mode actif, l'état de santé et la dernière erreur.

Hors de ces relations extérieures, rien ne doit rester simulé, factice ou « à venir ». Chaque écran, règle, export, état et rôle doit être réellement implémenté et testé.

**La démonstration est conservée.** Le profil `demo`, les deux entreprises fictives, les comptes `demo-ambawbio` et le déploiement Railway restent intacts. Toute nouvelle fonctionnalité enrichit les données de démo (profil `demo` uniquement), pour qu'elle soit montrable à un prospect. Les profils `prod` sans `demo` démarrent vides.

## 4. Travail restant, dans l'ordre

Pour le détail de chaque lot, voir la section 13 du guide AMB-CONC-09, avec les références F-, UC-, SD-, RG- et INV- des études qui se trouvent dans `docs/conception/`.

1. **LOT 7** : paiements Mobile Money et rapprochement. Le simulateur doit couvrir 7 comportements. Ajouter l'adaptateur agrégateur prêt à brancher, le lien de paiement et le rapprochement quotidien et manuel.
2. **LOT 8** : ventes entre entreprises. Devis, commande, acompte, livraison, facture, relances et balance âgée. L'e-mail est réel (SMTP configurable) ; WhatsApp passe par `PortMessagerie`.
3. **LOT 9** : stocks, achats et inventaire. Coût moyen pondéré, mouvements immuables, inventaire hors-ligne, commandes et factures fournisseurs.
4. **LOT 10** : comptabilité SYSCOHADA. Écritures automatiques pour tous les lots 5 à 9, lettrage, balance, grand livre, états financiers normal et minimal, multi-dossiers.
5. **LOT 11** : abonnement et console éditeur. Inscription en autonomie, packs, renouvellement, suspension, export.
6. **LOT 12** : tableaux de bord et exports Excel et PDF.
7. **Après le MVP**, à faire en totalité :
   - CRM complet (F-CRM-02 à 04) ;
   - boutique en ligne et portail client (Angular SSR) ;
   - MFA généralisée ;
   - API publique et webhooks ;
   - RH et paie, avec barèmes IUTS et CNSS paramétrables (valeurs officielles à confirmer dans `QUESTIONS.md`) ;
   - notes de frais ;
   - comptabilité analytique ;
   - projets ;
   - GED ;
   - abonnements clients ;
   - notifications instantanées EVO-01 (SSE) ;
   - puis les fonctions Release 3 et 4 de l'étude MoSCoW (`docs/conception/PDF/02_Etude_Fonctionnelle_MoSCoW.pdf`).
8. **APK Android** complet via Capacitor :
   - impression Bluetooth ;
   - lecture du QR code par la caméra ;
   - douchette ;
   - campagne hors-ligne.
9. **LOT 13** : durcissement.
   - Performance (k6) et sécurité (OWASP ASVS niveau 2, ZAP).
   - Accessibilité.
   - Supervision.
   - Sauvegarde et restauration testées.
   - Réversibilité.
10. **LOT 14** : préparation de la production.
    - Infrastructure au Burkina décrite en code.
    - Procédures d'exploitation.
    - Guides utilisateurs par profil.
    - Procès-verbal de recette.

Le produit est fini quand :

- les critères d'acceptation du cahier des charges (§ 16.2) sont tous vérifiés ;
- chaque fonctionnalité des études est livrée ou expressément rattachée à une dépendance externe listée dans `QUESTIONS.md`.

## 5. Méthode (inchangée, voir `CLAUDE.md`)

1. Lire le lot, puis écrire le plan dans `docs/lots/LOT-xx.md`.
2. Écrire d'abord les tests des règles métier, puis le code par tranches verticales.
3. Lancer `./mvnw verify`, puis lint, tests, build et E2E côté front.
4. Mettre à jour les docs (`DECISIONS.md` en continuant à D-41, `QUESTIONS.md`, guide Railway si le déploiement change).
5. Ouvrir une PR par lot, avec une checklist. L'utilisateur fusionne.

Les règles de `CLAUDE.md` restent non négociables :

- montants en `Montant` (type `long`) ;
- identifiants UUID v7 ;
- `tenant_id` et RLS sur chaque table métier ;
- idempotence ;
- pièces fiscales immuables ;
- numérotation sans trou ;
- tout en français ;
- aucun test désactivé.

Pour répondre aux questions de `QUESTIONS.md` qui ne dépendent pas d'un tiers, prenez l'option la plus recommandée (recherche web si utile) et notez-la en décision.

## 6. Pièges déjà rencontrés (à ne pas refaire)

- **Railway** :
  - l'adresse privée de la base est `ambawbio-suite.railway.internal`, pas `postgres.railway.internal` ;
  - le port des domaines publics est 8080 ;
  - l'image PostgreSQL resynchronise les mots de passe à chaque démarrage (D-40), et le serveur garantit son rôle `ambawbio_app` (D-39).
- **Vérifier avant d'affirmer.** Ne jamais donner à l'utilisateur un libellé d'interface (Railway, GitHub…) sans l'avoir vérifié. Demander une capture d'écran plutôt que deviner.
- **Versions** : TypeScript reste en `~6.0.x` tant qu'Angular 22 l'exige ; Dependabot ignore ses montées mineures et majeures.
- **Flyway** : pas de littéral entre `$$` dans un script qui utilise des placeholders. Utiliser un littéral simple entre apostrophes (voir `db/callback/beforeMigrate.sql`).
- **Tests concurrents** : dimensionner le pool Hikari. Éviter l'auto-invocation de méthodes `@Transactional`.
- **Secrets** : ne jamais les écrire dans le dépôt ni les demander dans le chat. Ils vont dans les variables d'environnement.
