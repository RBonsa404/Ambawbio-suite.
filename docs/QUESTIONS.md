# Questions ouvertes au porteur du projet

Reprises de la section 17 du guide (AMB-CONC-09), complétées au fil des lots. Le 2026-10-04, le porteur du projet a délégué les réponses : « réponds toi-même avec les solutions les plus recommandées, en t'appuyant sur des recherches ». Chaque réponse ci-dessous est donc une **décision par défaut**, paramétrable dans l'application, que le porteur peut remplacer à tout moment.

Règle maintenue : aucune spécification officielle n'est inventée. Quand un document officiel manque, on retient l'option la plus prudente et on la rend paramétrable.

## En attente d'un fait extérieur

| ID | Question | Pourquoi elle reste ouverte | Conduite retenue |
|---|---|---|---|
| Q-09 | IFU et RCCM de la société éditrice | La société n'est pas encore créée : ces numéros n'existent pas | Paramètres de la plateforme ; zones « [IFU EN COURS] » et « [RCCM EN COURS] » jusqu'à l'immatriculation |

## Réglées

| ID | Question | Réponse retenue (2026-10-04) | Source / justification |
|---|---|---|---|
| Q-01 | Spécifications de la FEC | La FEC est en vigueur depuis le 06/01/2026 et obligatoire depuis le 01/07/2026 pour les grandes et moyennes entreprises du régime normal (petites entreprises en 2027, micro-entreprises en 2028). Chaque facture porte un **identifiant unique, un QR code vérifiable et un horodatage**, émis par un dispositif certifié (SECeF) ou un **système de facturation commerciale certifié** par la DGI. Décision : Ambawbio vise l'agrément « système de facturation commerciale certifié » ; l'adaptateur DGI reste derrière `PortCertificationFiscale`, le simulateur sert jusqu'à l'obtention du cahier des charges technique (à demander à la DGI lors de la demande d'agrément). Mentions par défaut (paramétrables) : identifiant FEC, QR code, date et heure de certification, IFU et régime du vendeur, IFU du client assujetti, numéro de facture, détail des taxes | [Comarch](https://www.comarch.com/trade-and-services/data-management/legal-regulation-changes/burkina-faso-officially-launches-the-certified-electronic-invoice-system/), [Burkina24](https://burkina24.com/?p=429083), [Lookuptax](https://lookuptax.com/tax-changes/burkina-faso/facture-electronique-certifiee-2026) |
| Q-02 | Agrégateur Mobile Money | **LigdiCash** en premier adaptateur (acteur burkinabè, Orange Money et Moov Money, API facture → redirection → rappel), **pawaPay** en second (Orange, Moov). Wave : adaptateur dédié quand l'offre marchande sera disponible au Burkina. Le simulateur reste l'adaptateur par défaut en développement | [Kolonell — LigdiCash](https://kolonell.com/fr/blog/ligdicash-burkina-faso-paiement-orange-moov-ouagadougou-2026), [pawaPay](https://pawapay.io/blog/pawapay-live-in-burkina-faso) |
| Q-03 | Régimes fiscaux et taxes | Régimes par défaut : **RNI** (réel normal), **RSI** (réel simplifié), **CME** (contribution des micro-entreprises), tous paramétrables. Taxes par défaut : **TVA 18 %** (taux normal), **TVA 10 %** (taux réduit, cas limités), **Exonéré 0 %**. Valeurs marquées « à valider » par l'expert-comptable de chaque client | [vatcalc](https://www.vatcalc.com/?p=41782), [DGI — CGI 2023](https://dgi.bf/wp-content/uploads/2023/10/CODE-GENERAL-DES-IMPOTS-2023-A-JOUR-AVEC-LA-LOI-DE-FINANCE-2023.pdf) |
| Q-04 | Comptes par défaut SYSCOHADA | Plan comptable SYSCOHADA révisé (2017) et comptes de la section 11.4 du guide, chargés à la création de l'entreprise au LOT 10, modifiables | Acte uniforme OHADA relatif au droit comptable |
| Q-05 | Hébergeur au Burkina Faso | Les datacenters publics inaugurés en janvier 2026 sont réservés à l'État ; un datacenter national ouvert au privé est annoncé pour 2028. Décision : **colocation ou serveurs dédiés chez un opérateur installé au Burkina** (sélection sur cahier des charges : Tier III ou équivalent, double alimentation, sauvegarde hors site dans le pays), installation par `docker-compose.prod.yml` ; bascule possible vers le datacenter national en 2028 | [APA News](https://fr.apanews.net/news/le-burkina-inaugure-deux-datacenters-pour-securiser-ses-donnees-publiques/), [DCD](https://datacenterdynamics.com/en/news/govt-of-burkina-faso-launches-two-mini-data-centers-to-support-data-sovereignty/) |
| Q-06 | Durée de conservation | **10 ans** pour les pièces et livres comptables (droit comptable OHADA) ; aucune purge automatique ; l'effacement d'une personne anonymise sans supprimer les pièces (RG-12) | Acte uniforme OHADA relatif au droit comptable et à l'information financière |
| Q-07 | Barèmes IUTS, CNSS, taxe patronale | Reporté à la Release 2 (paie) ; barèmes versionnés (`socle.bareme`), saisis avec l'expert-comptable au démarrage du lot paie | Guide §17 |
| Q-08 | Prix des packs | Prix indicatifs de lancement, paramétrables (console éditeur) : **Essentiel 15 000 FCFA/mois**, **Business 45 000 FCFA/mois**, **Enterprise 150 000 FCFA/mois**, **Institution sur devis** ; −2 mois si paiement annuel. Positionnement sous les ERP importés, au niveau des caisses locales | Décision commerciale par défaut, à confirmer par le porteur |
| Q-10 à Q-13 | Points de conception | Voir décisions D-01 à D-04 | — |
| Q-14 | Stockage d'objets | **SeaweedFS** (licence Apache 2.0, compatible S3, images publiées) à la place de MinIO ; le code n'utilise que l'API S3 | MinIO ne publie plus d'image communautaire |
| Q-15 | Format de l'IFU | **8 chiffres suivis d'une lettre** (ex. 00012345A), saisie normalisée (espaces, minuscules) ; pas de clé de contrôle tant qu'aucune règle publique n'existe | [Lookuptax — IFU](https://lookuptax.com/validate/burkina-faso/ifu) |
| Q-16 | Contenu des packs | Essentiel : caisse, ventes, facturation, paiement, stock ; Business et Enterprise : + achats, comptabilité (Enterprise : plusieurs sociétés) ; Institution : sans caisse. Limites par défaut : Essentiel 3 utilisateurs, 1 établissement, 2 terminaux ; Business 15 / 5 / 10 ; Enterprise et Institution illimités (appliquées au LOT 11) | Décision par défaut |
| Q-17 | Délai de réponse aux demandes (loi n° 001-2021/AN) | **30 jours** (pratique alignée sur les autorités de protection francophones) ; déclaration des traitements à la CIL avant la mise en production (guide §16.3) | [Assemblée nationale — loi 001-2021](https://assembleenationale.bf/storage/Loi/kDsWHXOTUCQFVESChh8BlBdiZnCrdTsOpXe1rzmH.pdf), [DataGuidance](https://www.DataGuidance.com/jurisdiction/burkina-faso) |

### Détail des points de conception (Q-10 à Q-13)

| ID | Question | Réponse (2026-10-04) | Décision |
|---|---|---|---|
| Q-10 | Entrée directe par le code PIN (A-04) quand le terminal est déjà appairé ? | Oui | D-01 |
| Q-11 | Comptage à l'aveugle par défaut (clôture A-15, inventaire M-04) ? | Oui, désactivable par point de vente | D-02 |
| Q-12 | Un ou deux téléphones pour le magasinier et le commercial ? | Une seule application, menus selon le rôle, changement d'utilisateur par PIN | D-03 |
| Q-13 | Liste + fiche côte à côte pour toutes les listes web ? | Oui à partir de 1280 px | D-04 |

Justifications dans `docs/DECISIONS.md`.
