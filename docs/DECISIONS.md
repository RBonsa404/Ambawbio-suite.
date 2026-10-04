# Décisions

Format ADR court : contexte, décision, conséquences. Une décision n'est remise en cause que par une nouvelle entrée qui la remplace.

## D-01 — Déverrouillage direct par PIN sur un terminal appairé (Q-10)
- **Contexte** : la caissière doit reprendre la vente en quelques secondes, souvent hors-ligne. Keycloak n'est pas joignable sans réseau.
- **Décision** : démarrage A-01 → A-04 (PIN) si le terminal est appairé et que le caissier y a déjà ouvert une session. La connexion complète (A-03) n'est demandée qu'au premier usage sur l'appareil, après révocation, ou après expiration du jeton de rafraîchissement hors-ligne. Le PIN est vérifié localement (empreinte salée dans le stockage sécurisé), avec blocage après 5 essais.
- **Conséquences** : pratique standard des caisses ; le LOT 4 prévoit le stockage sécurisé du jeton et de l'empreinte du PIN.

## D-02 — Comptage à l'aveugle par défaut (Q-11)
- **Contexte** : afficher le montant ou la quantité attendus avant le comptage incite à « ajuster » le résultat et masque les écarts.
- **Décision** : à la clôture de caisse (A-15) et en inventaire (M-04), l'attendu est masqué jusqu'à la saisie du comptage. Option « comptage à l'aveugle » activée par défaut, désactivable par point de vente (W-13) ou par dépôt. Les écarts au-delà du seuil exigent la validation d'un responsable (A-16, bureau pour l'inventaire).
- **Conséquences** : bonne pratique de contrôle interne ; un écart est toujours tracé dans le journal d'audit et le rapport Z.

## D-03 — Une application, des rôles (Q-12)
- **Contexte** : magasinier et commercial peuvent partager un appareil dans les petites structures, ou avoir chacun le leur.
- **Décision** : une seule application Android ; les menus et écrans dépendent des rôles de l'utilisateur connecté. Plusieurs utilisateurs peuvent être enregistrés sur un même appareil et passer de l'un à l'autre par PIN (comme la caisse).
- **Conséquences** : un seul binaire à maintenir ; le choix « un ou deux appareils » devient un choix d'équipement du client, pas de conception.

## D-04 — Listes web en maître-détail (Q-13)
- **Décision** : à partir de la rupture `bureau` (1280 px), liste et fiche côte à côte pour produits, clients, fournisseurs, factures, devis, commandes ; ↑ ↓ change d'élément sans quitter le clavier. En dessous de 1280 px : liste seule, la fiche s'ouvre en pleine page. L'URL reflète l'élément sélectionné (lien partageable, retour arrière).
- **Conséquences** : un composant partagé « liste-fiche » au LOT 3.

## D-05 — Séparateur des milliers : espace insécable U+00A0 (écart E-03)
- **Contexte** : `Intl.NumberFormat('fr-FR')` produit l'espace fine insécable U+202F, absente d'Archivo et d'IBM Plex, et non gérée par la plupart des imprimantes thermiques ESC/POS.
- **Décision** : `formaterFcfa` et tous les formats numériques remplacent U+202F par U+00A0 (présente dans toutes nos polices et dans les jeux de caractères des imprimantes). Un test unitaire le vérifie.
- **Conséquences** : rendu homogène écran, PDF et ticket ; espace un peu plus large qu'une espace fine, acceptable typographiquement.

## D-06 — Compléments de jetons au LOT 3 (écarts E-01, E-02, E-04)
- **Décision** : ajouter à `tokens.json` et `theme.css` les graisses (`--font-weight-*`), l'espacement des lettres (`--tracking-*`), la largeur des titres (classe utilitaire `font-stretch` à 118 %), les épaisseurs de bordure (`--border-width-*`), et pour `[data-theme="plein-soleil"]` les bordures épaissies et la taille de texte +2 px. Les durées sont déclarées avec `@theme static` pour être toujours émises. Les valeurs sont celles des README et des maquettes Claude Design, sans en inventer.
- **Conséquences** : R-16 respectée sans valeur en dur dans les composants.

## D-07 — Captures de référence fournies par le porteur du projet (écart E-07)
- **Décision** : les captures manquantes (documents D-01 à D-08, site vitrine, écrans complétés plus tard) seront faites à la main par le porteur du projet, à la demande de Claude Code au début du lot concerné, et rangées dans `docs/design/references/captures/` sous le code de l'écran.

## D-08 — Versions retenues au LOT 0
- **Décision** : dernières versions stables au 2026-10-04 (guide §4) : Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, springdoc 3.1.1, PostgreSQL 18, Keycloak 26.8, Angular 22.2, Tailwind CSS 4.3, Transloco 8.4, keycloak-angular 22, Capacitor 8.5, Vitest 5, Playwright 1.63, Node.js 24 LTS (exigé par Angular 22). Les jalons (4.2.0-M2…) sont exclus.
- **Conséquences** : Dependabot propose les mises à jour chaque semaine.

## D-09 — Dépendances ajoutées lot par lot
- **Contexte** : la section 4 liste toute la pile ; le guide interdit d'ajouter une dépendance sans justification.
- **Décision** : le LOT 0 n'ajoute que ce qu'il utilise (web, sécurité OAuth2, JPA, Flyway, Modulith, Actuator, springdoc ; Angular, Tailwind, Transloco, keycloak-angular, Capacitor). Les autres entrent dans le lot qui en a besoin, justifiées dans la pull request.

## D-10 — Jetons et ressources de design : source unique `docs/design`
- **Décision** : `frontend/src/styles.css` importe directement `docs/design/theme.css` (dont la ligne `@import "tailwindcss"` a été déplacée dans ce point d'entrée). Les polices, logos et icônes sont copiés depuis `docs/design` par `frontend/scripts/synchroniser-design.mjs` avant `start`, `build` et `test` (Angular refuse les ressources hors du projet) ; les copies sont ignorées par git.
- **Conséquences** : aucune duplication des jetons (R-16) ; toute évolution du design passe par `docs/design`.

## D-11 — Un seul client Keycloak public pour le web et Android
- **Décision** : client `ambawbio-web`, code d'autorisation + PKCE S256, origines `http://localhost:4200` et `https://localhost` (WebView Capacitor). Le serveur valide le jeton (émetteur + clés JWKS, sans découverte au démarrage) et convertit les rôles du royaume en autorités `ROLE_*`.
- **Conséquences** : l'authentification hors-ligne de la caisse (PIN, D-01) s'appuiera sur ce client au LOT 4.

## D-12 — Organisation du module `socle` par sous-domaine
- **Contexte** : le guide (§6.1) décrit `api/` + `internal/{web,application,domaine,infrastructure}` par module ; le socle regroupe huit sous-domaines.
- **Décision** : `bf.ambawbio.socle.api` (interface publique nommée pour Spring Modulith : `Permissions`, `JournalAudit`, `AccesEtablissement`) ; sous-paquets internes par sous-domaine (`tenancy`, `identite`, `parametrage`, `audit`, `plateforme`, `donnees`), chacun avec ses entités, dépôts, service et contrôleur. Module `shared` déclaré ouvert.
- **Conséquences** : frontières vérifiées par `ModularityTest` ; les autres modules n'utilisent que `socle.api`.

## D-13 — Mise en œuvre du multi-tenant (guide §6.4)
- **Décision** : (1) entreprise lue dans la revendication `tenant_id` du jeton, ajoutée par Keycloak depuis un attribut utilisateur ; (2) Hibernate `@TenantId` + `CurrentTenantIdentifierResolver` ; (3) RLS PostgreSQL sur toutes les tables métier, `app.tenant_id` positionné **à chaque emprunt de connexion** (enveloppe de la source de données, valeur réécrite à chaque emprunt) ; rôle applicatif `ambawbio_app` sans `BYPASSRLS` ni propriété des tables, migrations exécutées par le propriétaire. Mode plateforme (éditeur) : politique RLS supplémentaire limitée à `socle.entreprise`, activée par `app.plateforme`.
- **Conséquences** : en production, deux comptes PostgreSQL (`AMBAWBIO_BD_PROPRIETAIRE` pour Flyway, `AMBAWBIO_BD_UTILISATEUR` pour l'application). Les tests d'intégration reproduisent cette configuration.

## D-14 — Permissions fines en base, rôles Keycloak pour la MFA
- **Décision** : Keycloak porte l'identité, `tenant_id` et des rôles « grossiers » ; les permissions `module:action` et les établissements autorisés viennent des affectations en base (rôles par entreprise, modifiables), chargées à chaque requête. Les codes de rôle sont répercutés sur les rôles du royaume, ce qui déclenche la MFA conditionnelle (rôle composite `mfa-obligatoire` dans `comptable`, `administrateur`, `dirigeant`).
- **Conséquences** : un cache (Caffeine) pourra être ajouté si le coût par requête devient sensible (mesure au LOT 13).

## D-15 — Empreinte du journal d'audit calculée par PostgreSQL
- **Contexte** : `jsonb` réécrit le JSON (ordre des clés, espaces) : une empreinte calculée en Java sur le texte envoyé ne serait pas recalculable.
- **Décision** : fonction `audit.calculer_empreinte(...)` (SHA-256 sur la forme canonique `jsonb::text`, horodatage en microsecondes) utilisée à l'insertion et à la vérification ; verrou consultatif par entreprise ; déclencheur + absence de droits UPDATE/DELETE ; vérification nocturne (ShedLock) avec compteur `ambawbio.audit.ruptures`.

## D-16 — Pas de dépendance pour l'UUID v7 ni pour le client Keycloak
- **Décision** : générateur UUID v7 interne (`Uuid7`, RFC 9562, 30 lignes testées) au lieu d'`uuid-creator` ; API d'administration Keycloak appelée avec `RestClient` (pas de `keycloak-admin-client`, qui embarque RESTEasy). Seule dépendance ajoutée au LOT 1 : ShedLock (prévu par le guide §4).

## D-17 — Prix de vente saisi TTC ou HT
- **Contexte** : en caisse, le prix affiché et payé inclut les taxes (5 500 F le sac) ; entre entreprises, les devis se font hors taxes. Stocker uniquement un HT entier ne permet pas de retrouver un TTC « rond ».
- **Décision** : le produit porte `prix_vente` (francs entiers) et `prix_vente_ttc` (vrai par défaut). Le calcul HT/TVA par ligne (guide §11.3) est fait par la facturation (LOT 6) selon ce drapeau, arrondi au franc demi supérieur.

## D-18 — Import : tout ou rien par défaut, rejouable
- **Décision** : chaque ligne est entièrement contrôlée (y compris par le domaine) avant tout enregistrement ; par défaut, un fichier comportant une erreur n'importe rien, ce qui évite les imports à moitié faits ; l'option « lignes valides seulement » existe pour les gros fichiers. Une ligne dont le code existe met à jour la fiche (rejeu sans doublon). Un seul enregistrement dans le journal d'audit par import (`IMPORT_REALISE`) plutôt qu'une entrée par ligne.

## D-19 — Champs personnalisés typés et filtrés par inclusion JSON
- **Décision** : valeurs normalisées (nombre sans zéros inutiles, date ISO, oui/non en booléen, liste fermée) pour que le filtre `champs_perso @> {...}` (index GIN `jsonb_path_ops`) retrouve exactement les valeurs. Seuls les champs déclarés « filtrables » sont acceptés en filtre. Les méthodes de validation ne sont pas transactionnelles, pour qu'une erreur de saisie pendant un import ne fasse pas échouer la transaction de l'import.

## D-20 — Données de démarrage par événement
- **Décision** : le socle publie `EntrepriseCreee` (registre d'événements Spring Modulith, R-03) ; chaque module charge ses valeurs par défaut dans sa propre transaction, après validation de la création (référentiel au LOT 2, plan comptable au LOT 10). Le contexte d'entreprise est positionné avant l'ouverture de la transaction pour que la connexion porte `app.tenant_id`.

## D-21 — Icônes : paquet `lucide` plutôt que `lucide-angular`
- **Contexte** : `lucide-angular` ne déclare pas encore Angular 22.
- **Décision** : composant `amb-icone` qui dessine les nœuds du paquet `lucide` (licence ISC), importés un par un (seules les icônes utilisées sont embarquées).

## D-22 — Points de rupture de la charte dans Tailwind
- **Décision** : `theme.css` remplace les points de rupture par ceux de la charte ; les classes s'écrivent `petite-tablette:` (600 px), `tablette:` (900 px), `bureau:` (1280 px), `large:` (1600 px) — jamais `sm:`/`md:`/`lg:`, qui n'existent pas dans ce thème.

## D-23 — Thème de connexion Keycloak par surcharge CSS
- **Décision** : thème `ambawbio` héritant de `keycloak.v2` (PatternFly 5), limité à une feuille de style, au logo et aux polices de la charte : pas de gabarit FreeMarker recopié, donc pas de maintenance à chaque version de Keycloak.

## D-24 — Code PIN du terminal
- **Décision** : empreinte PBKDF2-SHA-256 (150 000 itérations, sel aléatoire de 16 octets) conservée dans le stockage sécurisé Android (`@aparajita/capacitor-secure-storage`, Keystore) ; 5 essais au plus, puis reconnexion complète obligatoire ; verrouillage au démarrage et après 5 minutes en arrière-plan.

## D-25 — Charge des opérations signée sous forme de texte JSON
- **Contexte** : la signature ECDSA (guide §8.2) porte sur `idOperation|type|horodatageLocal|sha256(charge)`. Si la charge circulait comme objet JSON, le serveur devrait recalculer exactement la même sérialisation que le terminal (ordre des clés, nombres, échappements), source d'échecs de vérification.
- **Décision** : le terminal envoie la charge comme **chaîne JSON** (le texte exact qui a été signé) ; le serveur vérifie l'empreinte de cette chaîne puis la lit. Signature au format r‖s (P1363), clé privée WebCrypto non exportable.

## D-26 — Stockage local : IndexedDB (Dexie) sur navigateur et Android jusqu'au LOT 5
- **Contexte** : le guide prévoit SQLite chiffrée sur Android et Dexie sur navigateur, derrière une interface unique `LocalStore`.
- **Décision** : au LOT 4, une seule implémentation (`DexieStore`), utilisée aussi dans la WebView Android ; le stockage ne contient encore que des référentiels non sensibles et des opérations signées. L'implémentation SQLite chiffrée (SQLCipher, clé dans le Keystore) arrive au LOT 5 avec les ventes, sans changement des appelants.

## D-27 — Alerte « sans synchronisation » à 24 heures
- **Contexte** : le guide §8.7 fixe l'alerte à 24 h ; la fiche composant de l'indicateur (paquet de design) parle de 48 h.
- **Décision** : 24 h (le guide fait foi) ; valeur centralisée dans `agent-synchro.ts`.

## D-28 — Moteur de synchronisation chargé dès le démarrage
- **Décision** : l'agent de synchronisation (et Dexie) fait partie du premier chargement pour synchroniser en arrière-plan quel que soit l'écran ouvert ; budget du bundle initial porté à 500 Ko non compressés (alerte) / 600 Ko (erreur), soit environ 125 Ko compressés, sous les 250 Ko du guide (ENF-03). La bibliothèque `qrcode` n'est chargée qu'avec l'écran Terminaux.
