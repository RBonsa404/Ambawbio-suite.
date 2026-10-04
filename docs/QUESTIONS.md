# Questions ouvertes au porteur du projet

Reprises de la section 17 du guide (AMB-CONC-09), complétées au fil des lots. Une question tranchée passe en « Réglée » avec la date et la réponse, et la décision correspondante est consignée dans `docs/DECISIONS.md`.

Règle : ne jamais inventer une spécification officielle (DGI, opérateurs). En attendant la réponse, on applique la conduite indiquée et on continue.

## En attente

| ID | Question | Impact | Conduite à tenir en attendant | Ouverte le |
|---|---|---|---|---|
| Q-01 | Spécifications techniques officielles de la FEC (format, API, procédure d'agrément des logiciels, traitement hors-ligne), **dont la liste des mentions légales obligatoires sur la facture certifiée** | Adaptateur DGI, format de numérotation, mentions des factures D-01 et avoirs D-02 | Simulateur FEC, port isolé, tests de contrat préparés. Sur les gabarits, zone réservée **« [MENTIONS DGI À CONFIRMER] »** (`{mentions_dgi[]}`), liste paramétrable, jamais en dur | 2026-10 |
| Q-02 | Agrégateur Mobile Money retenu (contrat, documentation, environnement de test) | Adaptateur réel | Simulateur de paiement | 2026-10 |
| Q-03 | Liste des régimes fiscaux, taux de TVA, exonérations, retenues | Paramétrage par défaut | Valeurs paramétrables, marquées « à valider » | 2026-10 |
| Q-04 | Comptes par défaut et tables de correspondance des états financiers SYSCOHADA | Comptabilisation, états | Comptes de la section 11.4 du guide, paramétrables | 2026-10 |
| Q-05 | Hébergeur au Burkina Faso retenu (capacités, sauvegardes, site de secours) | Déploiement | Développement sur Docker Compose | 2026-10 |
| Q-06 | Durée légale de conservation exacte | Archivage, purge | Aucune purge de pièces comptables | 2026-10 |
| Q-07 | Barèmes IUTS, CNSS, taxe patronale (Release 2) | Paie | Ne pas commencer la paie sans validation | 2026-10 |
| Q-08 | Prix des packs en FCFA (Essentiel, Business, Enterprise, Institution) | Abonnement, site vitrine (Tarifs), écrans W-01, W-02, W-22 | Prix paramétrables depuis la console de l'éditeur (W-23). Dans l'interface et le site, zone réservée **« [PRIX À DÉFINIR] »**, jamais de montant en dur | 2026-10 |
| Q-09 | IFU et RCCM de la société éditrice d'Ambawbio Suite (société non encore créée) | En-tête de lettre, factures d'abonnement, mentions légales du site, pied des courriels | Paramètres de la plateforme. Zones réservées **« [IFU EN COURS] »** et **« [RCCM EN COURS] »**, jamais en dur | 2026-10 |

## Réglées

| ID | Question | Réponse (2026-10-04) | Décision |
|---|---|---|---|
| Q-10 | Entrée directe par le code PIN (A-04) quand le terminal est déjà appairé ? | Oui | D-01 |
| Q-11 | Comptage à l'aveugle par défaut (clôture A-15, inventaire M-04) ? | Oui, désactivable par point de vente | D-02 |
| Q-12 | Un ou deux téléphones pour le magasinier et le commercial ? | Une seule application, menus selon le rôle, changement d'utilisateur par PIN | D-03 |
| Q-13 | Liste + fiche côte à côte pour toutes les listes web ? | Oui à partir de 1280 px | D-04 |

Le porteur du projet a délégué ces choix (« choisis l'approche la plus recommandée »). Justifications dans `docs/DECISIONS.md`.
