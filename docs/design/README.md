# Handoff : Ambawbio Suite — système de conception et lot P1

## Vue d'ensemble
Ambawbio Suite est un ERP pour le Burkina Faso (caisse Android hors-ligne, facturation certifiée FEC/DGI, Mobile Money, comptabilité SYSCOHADA, stock, achats). Ce dossier contient l'identité retenue (direction **C « La terre et le motif »**), les jetons, les ressources et les spécifications pour implémenter le MVP. Codes d'écran (A-xx caisse, M-xx magasin, W-xx web, D-xx documents) = références des lots de développement et des cas d'utilisation (AMB-CONC-05 / 07 / 09).

## À propos des fichiers de conception
Les fichiers de `references/` sont des **références de conception en HTML** (prototypes montrant l'apparence et le comportement attendus), **pas du code de production à copier**. Il faut **recréer ces écrans dans la pile cible** : Angular 22 + Tailwind CSS 4, Android via Capacitor (même code), boutique en Angular SSR. Aucune bibliothèque d'interface lourde.
Les fichiers `.dc.html` s'ouvrent directement dans un navigateur (ils chargent `support.js` du même dossier).

## Fidélité
| Lot | Fidélité | Fichier |
|---|---|---|
| Identité, jetons, composants | **Haute fidélité** | Phase 2, Phase 3 |
| Caisse A-06, A-07, A-09, A-10, A-11, A-12, A-17, plein soleil, tablette | **Haute fidélité** — reproduire au pixel près | Phase 5 - HF caisse Android |
| Documents D-01 à D-08 | **Haute fidélité** | Phase 6 - Documents |
| Site vitrine (accueil) | **Haute fidélité**, fluide, mobile d'abord | Phase 6 - Site vitrine |
| Autres écrans A-xx, M-01 à M-07, W-01 à W-24 | **Filaires** (structure, contenu, parcours) — appliquer jetons et composants des phases 2–3 | Phase 4 - Filaires * |

## Contenu
```
docs/design/
  tokens.json              Jetons W3C Design Tokens (primitives, sémantiques, plein soleil, typo, espacements, rayons, ombres, durées, ruptures)
  theme.css                Bloc @theme Tailwind CSS 4 + [data-theme="plein-soleil"] + reduced-motion
  logo/                    Horizontal, vertical, symbole — couleur / noir / blanc ; symbole thermique (texte vectorisé) ; source/ avec texte modifiable
  icones-app/              Favicon, PWA, Apple, icône Android adaptative (+ README d'export PNG)
  icones/                  Icônes métier sur mesure (grille Lucide) + liste des icônes Lucide utilisées
  polices/LICENCES.md      Archivo, IBM Plex Sans, IBM Plex Mono (OFL 1.1) + intégration
  gabarits/README.md       Facture A4, avoir, devis/BC/BL, tickets 58/80 mm, rapport Z, courriels, SMS — zones variables nommées
  composants/              Fiches : indicateur de synchro, attente Mobile Money, badge FEC, pavé de caisse
  references/              Maquettes HTML (.dc.html) des phases 2 à 6
  references/exploration/  Phase 1 : les trois directions de marque (historique)
  references/captures/     PNG de chaque écran haute fidélité, nommés par code (A-06.png, D-01.png…)
```

## Jetons (résumé — source de vérité : tokens.json / theme.css)
- Noms en français sans accents, kebab-case : `--color-laterite-600`, `--color-primaire`, `--color-etat-hors-ligne`…
- Primitives (50→950, OKLCH) : **laterite** (principale, ancre 600 #B4532A), **banco** (neutres chauds, 50 #F5F1EA « kaolin », 950 #1E1B18 « charbon »), **vert** (800 #1B4332), **or** (400 #C9A227), **rouge** (danger uniquement, plus froid que la latérite), **bleu** (info, synchro).
- Sémantiques : fond, surface, surface-elevee, texte, texte-secondaire, bordure, primaire, primaire-survol, primaire-texte, accent, succes, avertissement, danger, info, etat-hors-ligne(-fond), etat-synchronisation(-fond), etat-en-attente(-fond), etat-a-verifier(-fond), etat-certifie(-fond).
- Plein soleil : `<html data-theme="plein-soleil">` — blanc/noir purs, primaire `laterite-800`, bordures 2–3 px, textes +2 px.
- Typo : titre Archivo (700–800, largeur 112–120 %, capitales pour titres de section), interface IBM Plex Sans, codes IBM Plex Mono. Échelle : xs 12/16, sm 14/20, base 16/24, lg 18/26, xl 20/28, 2xl 24/30, 3xl 30/36, 4xl 36/40, montant 44/48, 5xl 48/52, 6xl 64/64. **Min. 16 px en caisse ; total ≥ 40 px.** `tabular-nums` sur tous les montants.
- Espacement base 4 px. Rayons 0 / 2 / 4 / 8 px (style anguleux : boutons 2 px, cartes 0). Ombres légères uniquement (1 : 0 1 2 / 12 %, 2 : 0 2 6 / 16 %). Pas de flou d'arrière-plan.
- Animations 150 / 200 / 250 ms, `cubic-bezier(.2,0,0,1)`, désactivées par `prefers-reduced-motion`.
- Ruptures : telephone 360, petite-tablette 600, tablette 900, bureau 1280, large 1600.

## Motif de marque
Frise de chevrons (deux `linear-gradient` 135°/225° à 25 %) : 6–8 px sous la barre de caisse, 14–20 px en tête du site et des documents, noire en pied de facture. **Une frise par écran maximum**, jamais sous du texte, supprimée en plein soleil.

## Interactions et comportements clés
- Une action principale par écran, visible sans défilement à 360 × 640, en bas de l'écran ; cibles ≥ 48 × 48 dp (64 px pour le magasin).
- Confirmation uniquement pour l'irréversible (valider une facture, clôturer une caisse, révoquer un terminal, effacer des données) ; sinon annulation par toast.
- Aucun écran de caisse ne dépend du réseau ; tout état métier est écrit en clair avec icône + libellé.
- Parcours prototypés : SD-02 vente, SD-04 Mobile Money, SD-06 clôture, SD-11 appairage (caisse) ; SD-07 réception, SD-12 inventaire (magasin) ; SD-05 facture et SD-13 inscription (web W-01 à W-12).
- Formats : 12 500 FCFA (U+202F), 14/03/2027, +226 70 00 00 00, 10 h 42.

## Données de démonstration
Quincaillerie Wend-Panga (IFU 00012345 A), Pharmacie du Progrès (démo), Awa Kaboré (caissière), Moussa Sawadogo (gérant), Mariam Ilboudo (comptable), Issouf (magasinier), Boukary, Aminata, Salimata. Toutes fictives.

## Points ouverts
Zones réservées, clairement marquées dans les maquettes — **à implémenter comme valeurs paramétrables, jamais en dur** :
- `[PRIX À DÉFINIR]` : prix des packs (site, W-01, W-02, W-22). Les prix sont paramétrables dans l'application (console éditeur W-23).
- `[MENTIONS DGI À CONFIRMER]` : mentions obligatoires de la facture certifiée (D-01, D-02), en attente des spécifications officielles de la FEC.
- `[IFU EN COURS]`, `[RCCM EN COURS]` : identifiants d'Ambawbio (société éditrice en création) — en-tête de lettre, factures d'abonnement, mentions légales du site.
- Icônes métier : premières versions, à affiner.
- Haute fidélité restante (caisse A-01–A-05, A-08, A-13–A-16, A-18 ; magasin ; web) : appliquer les composants de la phase 3 aux filaires.
