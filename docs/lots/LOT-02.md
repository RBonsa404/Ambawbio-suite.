# LOT 2 — Référentiels et import

**Références** : F-SOC-06, 07, 16 ; F-STU-01 ; UC-SOC-06, 09, 10.
**Acceptation** : import de 1 000 produits et 500 clients avec rapport d'erreurs exact ; recherche plein texte rapide ; champs personnalisés visibles et filtrables.
**Statut** : terminé, en attente de validation.

## Réalisation

| Élément | Contenu |
|---|---|
| Module `referentiel` (schéma `referentiel`) | Tiers (client/fournisseur, nature, IFU, RCCM, régime fiscal, contacts avec consentement daté, comptes Mobile Money), Produit (bien/service, unité, taxe, prix entiers, prix TTC ou HT, suivi de stock), Conditionnement (quantité en unités de base, prix propre), CodeBarre (unique par entreprise), Catégorie, UniteMesure, TypeConditionnement, Taxe, RegimeFiscal, ListePrix (prix par quantité et par période) |
| Recherche | Index trigrammes `pg_trgm` sur une forme sans accents (`unaccent`) : nom, code, téléphone, IFU ; code-barres exact ; tri par similarité |
| Studio (`socle.studio`) | Définitions des champs personnalisés (texte, nombre, date, oui/non, liste), obligatoires, filtrables ; valeurs typées dans `champs_perso` (jsonb, index GIN) ; filtres `champ.<code>=valeur` |
| Import | CSV (séparateur détecté, guillemets, UTF-8/BOM) et Excel (.xlsx) ; contrôle complet de chaque ligne ; rapport ligne/colonne/valeur/motif (JSON et CSV) ; mode vérification ; tout ou rien par défaut ou lignes valides seulement ; rejeu sans doublon (mise à jour par code) ; modèle de fichier téléchargeable |
| Données de démarrage | À la création de l'entreprise, par l'événement `EntrepriseCreee` (registre Spring Modulith) : régimes RNI/RSI/CME, taxes TVA 18 %, TVA 10 %, exonéré, unités, types de conditionnement |
| Prix applicable | Liste du client (quantité minimale, période), sinon prix du conditionnement, sinon prix de base |

## Critères d'acceptation

| Critère | Résultat | Preuve |
|---|---|---|
| Import de 1 000 produits avec rapport exact | ✅ | `ReferentielTest.import1000ProduitsAvecRapportDErreursExact` : 1 006 lignes dont 6 erronées (nom vide, montant invalide, taxe inconnue, code en double, code-barres en double, valeur de liste hors options) → rapport exact (lignes 12, 25, 40, 60, 80, 90 et colonnes), rien d'importé ; puis 1 000 lignes valides importées ; rejeu sans doublon |
| Import de 500 clients avec rapport exact | ✅ | `import500ClientsAvecRapportDErreursExact` : 503 lignes, 3 erreurs (IFU invalide, INV-03, téléphone invalide), 500 importés, comptes Mobile Money créés |
| Recherche plein texte rapide | ✅ | Recherche sans accents par nom, code, téléphone, IFU et code-barres ; < 300 ms sur 1 000 produits (test) |
| Champs personnalisés visibles et filtrables | ✅ | `champsPersonnalisesValidesEtFiltrables` + import : valeurs typées renvoyées, filtre exact, champ non filtrable refusé, valeur hors liste refusée, champ obligatoire exigé |

Autres : Excel et rapport CSV, INV-03, prix par liste et par quantité, isolation entre entreprises, données de démarrage. Total serveur : 34 tests au vert (+ 1 test de contrat Keycloak à la demande).

## API (`/api/v1`)

| Chemin | Permission |
|---|---|
| `GET /referentiel/produits?q=&categorie=&actif=&champ.<code>=`, `GET …/{id}`, `GET …/code-barre/{valeur}`, `POST`, `PUT …/{id}` | lecture `socle:consulter`, écriture `referentiel:gerer` |
| `GET /referentiel/tiers?q=&client=&fournisseur=&champ.<code>=`, `GET …/{id}`, `POST`, `PUT` | écriture `referentiel:gerer` ou `ventes:gerer` |
| `GET/POST /referentiel/regimes-fiscaux`, `/taxes` (+ `PUT …/{id}`), `/unites`, `/types-conditionnement`, `/categories` (+ `PUT`) | paramétrage fiscal : `socle:parametrer` |
| `GET/POST/PUT /referentiel/listes-prix`, `GET /referentiel/prix?produit=&tiers=&conditionnement=&quantite=&date=` | `referentiel:gerer` |
| `POST /referentiel/imports/{produits|tiers}` (multipart `fichier`, `mode`, `lignesValidesSeulement`), `GET …/{id}`, `GET …/{id}/rapport.csv`, `GET …/modeles/{type}.csv` | `referentiel:gerer` |
| `GET/POST/PUT /studio/champs?entite=produit|tiers` | lecture `socle:consulter`, écriture `socle:parametrer` |

## Écarts et reports

- **Rendu dynamique des champs personnalisés dans les formulaires** et écrans W-05, W-06, W-07 : LOT 3 (application), à partir des définitions renvoyées par `GET /studio/champs`.
- **Imports des stocks initiaux et des soldes comptables** (F-SOC-16) : LOT 9 et LOT 10, dans les modules concernés.
- **Chemin de l'API d'import** : `/referentiel/imports/{type}` au lieu de `/socle/imports/{type}` (guide §12) : l'import écrit dans le schéma du référentiel (un module = un schéma).
- **Extensions PostgreSQL** `pg_trgm` et `unaccent` : à créer par un compte autorisé lors de l'installation (fait par les migrations en développement).
