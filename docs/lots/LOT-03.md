# LOT 3 — Socle de l'application

**Références** : AMB-CONC-10 (design), ENF-03, 04, 09, 11, 16.
**Acceptation** : écrans conformes aux maquettes (comparaison visuelle) ; budgets de performance respectés ; contrastes AA vérifiés.
**Statut** : terminé, en attente de validation (comparaison visuelle finale avec les captures du porteur du projet).

## Réalisation

| Élément | Contenu |
|---|---|
| Jetons | `docs/design/theme.css` et `tokens.json` complétés : graisses, espacement des lettres, largeur des titres, épaisseurs de bordure, plein soleil (bordures épaissies, texte +2 px), durées toujours disponibles (écarts E-01, E-02, E-04 corrigés) |
| Composants partagés (`frontend/src/app/shared/ui`) | Bouton (principal, secondaire, tertiaire, danger, caisse 64 px), champ avec libellé / aide / erreur, champ montant FCFA (clavier numérique, groupement, entier), sélecteur de quantité et de conditionnement, tableau dense (montants alignés, chiffres tabulaires), pagination, état vide, notifications avec action, boîte de confirmation, indicateur réseau / synchronisation (bandeau et pastille, 5 états), champs personnalisés dynamiques, icônes Lucide, filtre `fcfa` |
| Coquille | Barre latérale charbon repliable à 64 px (repliée par défaut sous 1280 px, menu filtré par les permissions), barre du haut avec entreprise et établissement toujours visibles, profil et déconnexion, navigation basse 4 entrées sur téléphone, lien d'évitement, raccourcis `/`, `N`, ↑ ↓ |
| Écrans | Accueil (contexte, accès rapides), **W-05 Produits** et **W-06 Clients et fournisseurs** (liste + fiche côte à côte à partir de 1280 px, D-04 ; conditionnements, codes-barres, IFU guidé, Mobile Money, champs personnalisés), **W-07 Import** (assistant en 4 étapes, rapport ligne par ligne, téléchargement du modèle et du rapport), **W-24** (page introuvable, accès refusé, abonnement suspendu) |
| Connexion (A-03, W-24) | Thème Keycloak `ambawbio` aux couleurs de la charte (logo, frise, latérite, Archivo / IBM Plex) |
| Android | Plugins réseau, cycle de vie et stockage sécurisé (Keystore) ; **A-04** code PIN : choix au premier lancement, déverrouillage, verrouillage après 5 minutes en arrière-plan, empreinte PBKDF2 salée, blocage après 5 essais (D-01) |

## Critères d'acceptation

| Critère | Résultat | Preuve |
|---|---|---|
| Contrastes et accessibilité AA | ✅ | Playwright + axe-core (WCAG 2.0/2.1 A et AA) sans aucune violation sur accueil, produits (liste et fiche), clients et fournisseurs, import et page de connexion, **sur téléphone et sur bureau** |
| Budgets de performance | ✅ | Premier chargement : 105,6 Ko compressés (budget du guide : 250 Ko) ; écrans chargés à la demande (produits : 5,2 Ko) ; budgets bloquants dans `angular.json` |
| Conformité aux maquettes | ✅ structure / ⏳ comparaison fine | Structure, contenus et règles des filaires W-05, W-07 et de la planche Composants §04 reproduits (vérification sur captures Playwright) ; comparaison pixel à pixel à finaliser avec les captures demandées au porteur du projet (les maquettes ne s'affichent pas dans l'environnement de développement : elles chargent React depuis un CDN bloqué) |

Tests : 11 tests unitaires (formats, champ montant, indicateur, code PIN, UUID v7), 12 scénarios Playwright (connexion, MFA, création de produit, import, accessibilité, thème de connexion ; téléphone et bureau), 34 tests serveur.

## Écarts et reports

- **Textes des composants partagés** : les libellés internes des composants génériques (indicateur, pagination, confirmation) sont en français dans le code ; les textes des écrans sont dans `fr.json` (Transloco). Externalisation des premiers au LOT 13.
- **Tableau de bord W-04** : indicateurs au LOT 12 (dépendent des ventes, de la caisse et de la comptabilité).
- **Écrans de paramètres (W-18 à W-23)** : au fil des lots qui les alimentent (terminaux au LOT 4, abonnement au LOT 11) ; l'API existe depuis le LOT 1.
- **Déverrouillage hors-ligne complet** (jeton Keycloak conservé chiffré) : LOT 4, avec le moteur de synchronisation.
- **Workflow `e2e.yml` en CI** : à partir du LOT 4 (environnement complet en CI) ; les scénarios tournent aujourd'hui en local.
