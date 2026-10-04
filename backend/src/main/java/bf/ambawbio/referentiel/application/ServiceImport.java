package bf.ambawbio.referentiel.application;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.referentiel.domaine.ImportDonnees;
import bf.ambawbio.referentiel.domaine.ImportDonnees.Erreur;
import bf.ambawbio.referentiel.domaine.Parametres.Categorie;
import bf.ambawbio.referentiel.domaine.Parametres.RegimeFiscal;
import bf.ambawbio.referentiel.domaine.Produit;
import bf.ambawbio.referentiel.domaine.Tiers;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.shared.tenant.ContexteTenant;
import bf.ambawbio.socle.api.ChampsPersonnalises;
import bf.ambawbio.socle.api.JournalAudit;

/**
 * Import CSV/Excel des produits et des tiers (F-SOC-16, UC-SOC-06). Chaque ligne est entièrement contrôlée ; le rapport
 * indique la ligne du tableur, la colonne, la valeur et le motif. Par défaut, tout ou rien : un fichier comportant une
 * erreur n'importe rien. Les lignes existantes (même code) sont mises à jour, ce qui permet de rejouer un import.
 */
@Service
public class ServiceImport {

    public static final List<String> TYPES = List.of("produits", "tiers");

    static final List<String> COLONNES_PRODUITS = List.of("code", "nom", "type", "categorie", "unite", "taxe", "prix_vente", "prix_ttc",
            "prix_achat", "suivi_stock", "actif", "code_barre", "conditionnement", "conditionnement_quantite", "conditionnement_prix",
            "conditionnement_code_barre");
    static final List<String> COLONNES_TIERS = List.of("code", "nom", "nature", "client", "fournisseur", "ifu", "rccm", "regime",
            "telephone", "courriel", "adresse", "ville", "delai_paiement", "plafond_credit", "mobile_money_operateur", "mobile_money_numero");

    private static final Map<String, String> COLONNE_PAR_CODE = Map.ofEntries(
            Map.entry("IFU_INVALIDE", "ifu"), Map.entry("INV-03", "ifu"), Map.entry("TELEPHONE_INVALIDE", "telephone"),
            Map.entry("NOM_OBLIGATOIRE", "nom"), Map.entry("CODE_OBLIGATOIRE", "code"), Map.entry("PRIX_INVALIDE", "prix_vente"),
            Map.entry("QUANTITE_INVALIDE", "conditionnement_quantite"), Map.entry("ROLE_TIERS_OBLIGATOIRE", "client"),
            Map.entry("CONDITIONS_INVALIDES", "delai_paiement"), Map.entry("OPERATEUR_OBLIGATOIRE", "mobile_money_operateur"));

    private final ProduitDepot produits;
    private final TiersDepot tiers;
    private final TaxeDepot taxes;
    private final UniteDepot unites;
    private final CategorieDepot categories;
    private final RegimeDepot regimes;
    private final ImportDepot imports;
    private final ChampsPersonnalises champs;
    private final JournalAudit audit;
    private final PublicationReferentiel publication;

    ServiceImport(ProduitDepot produits, TiersDepot tiers, TaxeDepot taxes, UniteDepot unites, CategorieDepot categories, RegimeDepot regimes,
            ImportDepot imports, ChampsPersonnalises champs, JournalAudit audit,
            PublicationReferentiel publication) {
        this.produits = produits;
        this.tiers = tiers;
        this.taxes = taxes;
        this.unites = unites;
        this.categories = categories;
        this.regimes = regimes;
        this.imports = imports;
        this.champs = champs;
        this.audit = audit;
        this.publication = publication;
    }

    @Transactional(readOnly = true)
    public ImportDonnees importDonnees(UUID id) {
        return imports.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Import introuvable."));
    }

    /** Modèle de fichier : colonnes attendues, y compris les champs personnalisés définis dans le Studio. */
    @Transactional(readOnly = true)
    public List<String> colonnes(String type) {
        var base = new ArrayList<>(switch (verifierType(type)) {
            case "produits" -> COLONNES_PRODUITS;
            default -> COLONNES_TIERS;
        });
        champs.definitions(entite(type)).forEach(d -> base.add("champ." + d.code()));
        return base;
    }

    @Transactional
    public ImportDonnees importer(String type, String nomFichier, InputStream flux, ImportDonnees.Mode mode, boolean lignesValidesSeulement)
            throws IOException {
        verifierType(type);
        var lignes = LecteurTableau.lire(flux, nomFichier);
        if (lignes.isEmpty()) {
            throw new RegleMetierException("FICHIER_VIDE", "Le fichier ne contient aucune ligne de données.");
        }
        var erreurs = new ArrayList<Erreur>();
        int importees = "produits".equals(type)
                ? traiterProduits(lignes, erreurs, mode, lignesValidesSeulement)
                : traiterTiers(lignes, erreurs, mode, lignesValidesSeulement);
        var statut = erreurs.isEmpty() ? ImportDonnees.Statut.REUSSI : importees > 0 ? ImportDonnees.Statut.PARTIEL : ImportDonnees.Statut.REJETE;
        erreurs.sort((a, b) -> a.ligne() != b.ligne() ? Integer.compare(a.ligne(), b.ligne()) : a.colonne().compareTo(b.colonne()));
        var trace = imports.save(new ImportDonnees(Uuid7.nouveau(), type, nomFichier, mode, statut, lignes.size(), importees, erreurs));
        if (mode == ImportDonnees.Mode.IMPORT && importees > 0) {
            audit.enregistrer("IMPORT_REALISE", "import_donnees", trace.getId(), null,
                    Map.of("type", type, "fichier", nomFichier, "lignes", lignes.size(), "importees", importees, "erreurs", erreurs.size()));
        }
        return trace;
    }

    // ------------------------------------------------------------------ produits

    private record LigneProduit(int numero, ServiceProduits.Commande commande, String nomCategorie) {
    }

    private int traiterProduits(List<LecteurTableau.Ligne> lignes, List<Erreur> erreurs, ImportDonnees.Mode mode, boolean lignesValidesSeulement) {
        var tenant = ContexteTenant.tenantObligatoire();
        var taxesParCode = taxes.findAll().stream().collect(Collectors.toMap(t -> t.code().toUpperCase(Locale.ROOT), Function.identity()));
        var unitesParCode = unites.findAll().stream().collect(Collectors.toMap(u -> u.code().toUpperCase(Locale.ROOT), Function.identity()));
        var codesBarresExistants = new HashMap<String, String>();
        produits.tousLesCodesBarres(tenant).forEach(v -> {
            var i = v.lastIndexOf(':');
            codesBarresExistants.put(v.substring(0, i), v.substring(i + 1));
        });
        var codesVus = new HashMap<String, Integer>();
        var codesBarresVus = new HashMap<String, Integer>();
        var valides = new ArrayList<LigneProduit>();

        for (var ligne : lignes) {
            var erreursLigne = new ArrayList<Erreur>();
            var code = ligne.valeur("code");
            var nom = ligne.valeur("nom");
            exiger(ligne, "code", erreursLigne);
            exiger(ligne, "nom", erreursLigne);
            if (code != null && codesVus.putIfAbsent(code, ligne.numero()) != null) {
                erreursLigne.add(erreur(ligne, "code", "Code déjà présent à la ligne " + codesVus.get(code) + " du fichier."));
            }
            var type = enumeration(ligne, "type", Produit.Type.class, Produit.Type.BIEN, erreursLigne);
            var taxeCode = ligne.valeur("taxe") == null ? "TVA18" : ligne.valeur("taxe").toUpperCase(Locale.ROOT);
            if (!taxesParCode.containsKey(taxeCode)) {
                erreursLigne.add(erreur(ligne, "taxe", "Taxe inconnue. Valeurs possibles : " + String.join(", ", taxesParCode.keySet()) + "."));
            }
            var uniteCode = ligne.valeur("unite") == null ? "U" : ligne.valeur("unite").toUpperCase(Locale.ROOT);
            if (!unitesParCode.containsKey(uniteCode)) {
                erreursLigne.add(erreur(ligne, "unite", "Unité inconnue. Valeurs possibles : " + String.join(", ", unitesParCode.keySet()) + "."));
            }
            var prixVente = montant(ligne, "prix_vente", true, erreursLigne);
            var prixAchat = montant(ligne, "prix_achat", false, erreursLigne);
            var prixTtc = booleen(ligne, "prix_ttc", true, erreursLigne);
            var suiviStock = booleen(ligne, "suivi_stock", true, erreursLigne);
            var actif = booleen(ligne, "actif", true, erreursLigne);

            var conditionnements = new ArrayList<Produit.DonneesConditionnement>();
            var codesBarres = new ArrayList<Produit.DonneesCodeBarre>();
            var codeConditionnement = ligne.valeur("conditionnement");
            if (codeConditionnement != null) {
                var quantite = quantite(ligne, "conditionnement_quantite", erreursLigne);
                var prixConditionnement = montant(ligne, "conditionnement_prix", false, erreursLigne);
                if (quantite != null) {
                    conditionnements.add(new Produit.DonneesConditionnement(codeConditionnement.toUpperCase(Locale.ROOT), null, quantite,
                            prixConditionnement));
                }
            }
            ajouterCodeBarre(ligne, "code_barre", null, code, codesBarres, codesBarresVus, codesBarresExistants, erreursLigne);
            if (codeConditionnement != null) {
                ajouterCodeBarre(ligne, "conditionnement_code_barre", codeConditionnement.toUpperCase(Locale.ROOT), code, codesBarres,
                        codesBarresVus, codesBarresExistants, erreursLigne);
            }
            var valeursChamps = champsPersonnalises(ligne, "produit", erreursLigne);

            if (erreursLigne.isEmpty()) {
                var commande = new ServiceProduits.Commande(code, nom, type, null, uniteCode, taxeCode, prixVente == null ? 0 : prixVente, prixTtc,
                        prixAchat, suiviStock, actif, conditionnements, codesBarres, valeursChamps);
                // Contrôle complet par le domaine, sur un objet non enregistré.
                controler(ligne, erreursLigne, () -> {
                    var essai = new Produit(Uuid7.nouveau(), code);
                    essai.modifier(nom, type, null, unitesParCode.get(uniteCode).getId(), taxesParCode.get(taxeCode).getId(), commande.prixVente(),
                            prixTtc, prixAchat, suiviStock, actif, valeursChamps);
                    essai.definirConditionnements(conditionnements);
                    essai.definirCodesBarres(codesBarres);
                });
                if (erreursLigne.isEmpty()) {
                    valides.add(new LigneProduit(ligne.numero(), commande, ligne.valeur("categorie")));
                }
            }
            erreurs.addAll(erreursLigne);
        }
        if (mode == ImportDonnees.Mode.VERIFICATION || (!erreurs.isEmpty() && !lignesValidesSeulement)) {
            return 0;
        }
        var categoriesParNom = categories.findAll().stream().collect(Collectors.toMap(c -> c.nom().toLowerCase(Locale.ROOT), Function.identity()));
        var existants = produits.findByCodeIn(valides.stream().map(v -> v.commande().code()).toList()).stream()
                .collect(Collectors.toMap(Produit::code, Function.identity()));
        for (var v : valides) {
            UUID categorieId = null;
            if (v.nomCategorie() != null) {
                var categorie = categoriesParNom.computeIfAbsent(v.nomCategorie().toLowerCase(Locale.ROOT),
                        n -> {
                            var nouvelle = categories.save(new Categorie(Uuid7.nouveau(), v.nomCategorie(), null));
                            publication.categorie(nouvelle);
                            return nouvelle;
                        });
                categorieId = categorie.getId();
            }
            var c = v.commande();
            var produit = existants.getOrDefault(c.code(), null);
            if (produit == null) {
                produit = new Produit(Uuid7.nouveau(), c.code());
            }
            produit.modifier(c.nom(), c.type(), categorieId, unitesParCode.get(c.uniteCode()).getId(), taxesParCode.get(c.taxeCode()).getId(),
                    c.prixVente(), c.prixVenteTtc(), c.prixAchat(), c.suiviStock(), c.actif(), c.champsPerso());
            produit.definirConditionnements(c.conditionnements());
            produit.definirCodesBarres(c.codesBarres());
            publication.produit(produits.saveAndFlush(produit));
        }
        return valides.size();
    }

    private void ajouterCodeBarre(LecteurTableau.Ligne ligne, String colonne, String conditionnement, String codeProduit,
            List<Produit.DonneesCodeBarre> codes, Map<String, Integer> vus, Map<String, String> existants, List<Erreur> erreurs) {
        var valeur = ligne.valeur(colonne);
        if (valeur == null) {
            return;
        }
        if (vus.putIfAbsent(valeur, ligne.numero()) != null) {
            erreurs.add(erreur(ligne, colonne, "Code-barres déjà présent à la ligne " + vus.get(valeur) + " du fichier."));
        } else if (existants.containsKey(valeur) && !existants.get(valeur).equals(codeProduit)) {
            erreurs.add(erreur(ligne, colonne, "Code-barres déjà attribué au produit « " + existants.get(valeur) + " »."));
        } else {
            codes.add(new Produit.DonneesCodeBarre(valeur, conditionnement));
        }
    }

    // ------------------------------------------------------------------ tiers

    private record LigneTiers(int numero, ServiceTiers.Commande commande, RegimeFiscal regime) {
    }

    private int traiterTiers(List<LecteurTableau.Ligne> lignes, List<Erreur> erreurs, ImportDonnees.Mode mode, boolean lignesValidesSeulement) {
        var regimesParCode = regimes.findAll().stream().collect(Collectors.toMap(r -> r.code().toUpperCase(Locale.ROOT), Function.identity()));
        var codesVus = new HashMap<String, Integer>();
        var valides = new ArrayList<LigneTiers>();

        for (var ligne : lignes) {
            var erreursLigne = new ArrayList<Erreur>();
            var code = ligne.valeur("code");
            var nom = ligne.valeur("nom");
            exiger(ligne, "code", erreursLigne);
            exiger(ligne, "nom", erreursLigne);
            if (code != null && codesVus.putIfAbsent(code, ligne.numero()) != null) {
                erreursLigne.add(erreur(ligne, "code", "Code déjà présent à la ligne " + codesVus.get(code) + " du fichier."));
            }
            var nature = enumeration(ligne, "nature", Tiers.Nature.class, null, erreursLigne);
            var client = booleen(ligne, "client", true, erreursLigne);
            var fournisseur = booleen(ligne, "fournisseur", false, erreursLigne);
            RegimeFiscal regime = null;
            if (ligne.valeur("regime") != null) {
                regime = regimesParCode.get(ligne.valeur("regime").toUpperCase(Locale.ROOT));
                if (regime == null) {
                    erreursLigne.add(erreur(ligne, "regime", "Régime inconnu. Valeurs possibles : " + String.join(", ", regimesParCode.keySet()) + "."));
                }
            }
            var delai = entier(ligne, "delai_paiement", erreursLigne);
            var plafond = montant(ligne, "plafond_credit", false, erreursLigne);
            var comptes = new ArrayList<Tiers.DonneesCompte>();
            if (ligne.valeur("mobile_money_numero") != null || ligne.valeur("mobile_money_operateur") != null) {
                var operateur = operateur(ligne, erreursLigne);
                if (operateur != null) {
                    comptes.add(new Tiers.DonneesCompte(operateur, ligne.valeur("mobile_money_numero"), nom, true));
                }
            }
            var valeursChamps = champsPersonnalises(ligne, "tiers", erreursLigne);
            if (erreursLigne.isEmpty()) {
                var regimeRetenu = regime;
                var commande = new ServiceTiers.Commande(code, nom, nature, client, fournisseur, ligne.valeur("ifu"), ligne.valeur("rccm"),
                        regime == null ? null : regime.code(),
                        new Tiers.Coordonnees(ligne.valeur("telephone"), ligne.valeur("courriel"), ligne.valeur("adresse"), ligne.valeur("ville")),
                        new Tiers.ConditionsCommerciales(null, delai == null ? 0 : delai, plafond), true, List.of(), comptes, valeursChamps);
                controler(ligne, erreursLigne, () -> {
                    var essai = new Tiers(Uuid7.nouveau(), code);
                    appliquerTiers(essai, commande, regimeRetenu);
                });
                if (erreursLigne.isEmpty()) {
                    valides.add(new LigneTiers(ligne.numero(), commande, regime));
                }
            }
            erreurs.addAll(erreursLigne);
        }
        if (mode == ImportDonnees.Mode.VERIFICATION || (!erreurs.isEmpty() && !lignesValidesSeulement)) {
            return 0;
        }
        var existants = tiers.findByCodeIn(valides.stream().map(v -> v.commande().code()).toList()).stream()
                .collect(Collectors.toMap(Tiers::code, Function.identity()));
        for (var v : valides) {
            var t = existants.get(v.commande().code());
            if (t == null) {
                t = new Tiers(Uuid7.nouveau(), v.commande().code());
            }
            appliquerTiers(t, v.commande(), v.regime());
            publication.tiers(tiers.saveAndFlush(t));
        }
        return valides.size();
    }

    private static void appliquerTiers(Tiers t, ServiceTiers.Commande c, RegimeFiscal regime) {
        t.modifier(new Tiers.Identification(c.nom(), c.nature(), c.estClient(), c.estFournisseur(), c.ifu(), c.rccm(),
                        regime == null ? null : regime.getId(), regime != null && regime.assujettiTva()),
                c.coordonnees(), c.conditions(), c.actif(), c.champsPerso());
        t.definirComptesMobileMoney(c.comptesMobileMoney());
    }

    // ------------------------------------------------------------------ outils de contrôle

    private Map<String, Object> champsPersonnalises(LecteurTableau.Ligne ligne, String entite, List<Erreur> erreurs) {
        var valeurs = new LinkedHashMap<String, Object>();
        Set<String> fournis = new HashSet<>();
        ligne.valeurs().forEach((colonne, brute) -> {
            if (!colonne.startsWith("champ.")) {
                return;
            }
            var code = colonne.substring("champ.".length());
            try {
                var valeur = champs.validerValeur(entite, code, brute);
                if (valeur != null) {
                    valeurs.put(code, valeur);
                    fournis.add(code);
                }
            } catch (RegleMetierException e) {
                erreurs.add(erreur(ligne, colonne, e.getMessage()));
            }
        });
        champs.definitions(entite).stream().filter(d -> d.obligatoire() && !fournis.contains(d.code()))
                .forEach(d -> erreurs.add(erreur(ligne, "champ." + d.code(), "Le champ « " + d.libelle() + " » est obligatoire.")));
        return valeurs;
    }

    private static void controler(LecteurTableau.Ligne ligne, List<Erreur> erreurs, Runnable controle) {
        try {
            controle.run();
        } catch (RegleMetierException e) {
            erreurs.add(erreur(ligne, COLONNE_PAR_CODE.getOrDefault(e.code(), "ligne"), e.getMessage()));
        }
    }

    private static void exiger(LecteurTableau.Ligne ligne, String colonne, List<Erreur> erreurs) {
        if (ligne.valeur(colonne) == null) {
            erreurs.add(erreur(ligne, colonne, "Valeur obligatoire."));
        }
    }

    private static Long montant(LecteurTableau.Ligne ligne, String colonne, boolean obligatoire, List<Erreur> erreurs) {
        var brute = ligne.valeur(colonne);
        if (brute == null) {
            if (obligatoire) {
                erreurs.add(erreur(ligne, colonne, "Valeur obligatoire."));
            }
            return null;
        }
        var nettoyee = brute.replaceAll("[\\s  ]", "").replaceAll("(?i)f(cfa)?$", "");
        if (!nettoyee.matches("\\d{1,15}")) {
            erreurs.add(erreur(ligne, colonne, "Montant invalide : nombre entier de francs CFA attendu (ex. 12500)."));
            return null;
        }
        return Long.parseLong(nettoyee);
    }

    private static BigDecimal quantite(LecteurTableau.Ligne ligne, String colonne, List<Erreur> erreurs) {
        var brute = ligne.valeur(colonne);
        try {
            var valeur = new BigDecimal(brute == null ? "" : brute.replace(',', '.').replaceAll("\\s", ""));
            if (valeur.signum() <= 0 || valeur.scale() > 3) {
                throw new NumberFormatException();
            }
            return valeur;
        } catch (NumberFormatException e) {
            erreurs.add(erreur(ligne, colonne, "Quantité invalide : nombre positif, 3 décimales au plus (ex. 0,5)."));
            return null;
        }
    }

    private static Integer entier(LecteurTableau.Ligne ligne, String colonne, List<Erreur> erreurs) {
        var brute = ligne.valeur(colonne);
        if (brute == null) {
            return null;
        }
        if (!brute.matches("\\d{1,4}")) {
            erreurs.add(erreur(ligne, colonne, "Nombre de jours invalide."));
            return null;
        }
        return Integer.parseInt(brute);
    }

    private static boolean booleen(LecteurTableau.Ligne ligne, String colonne, boolean defaut, List<Erreur> erreurs) {
        var brute = ligne.valeur(colonne);
        if (brute == null) {
            return defaut;
        }
        return switch (brute.toLowerCase(Locale.ROOT)) {
            case "oui", "o", "1", "vrai", "true", "x" -> true;
            case "non", "n", "0", "faux", "false" -> false;
            default -> {
                erreurs.add(erreur(ligne, colonne, "Valeur attendue : oui ou non."));
                yield defaut;
            }
        };
    }

    private static <E extends Enum<E>> E enumeration(LecteurTableau.Ligne ligne, String colonne, Class<E> type, E defaut, List<Erreur> erreurs) {
        var brute = ligne.valeur(colonne);
        if (brute == null) {
            return defaut;
        }
        try {
            return Enum.valueOf(type, brute.toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            var possibles = java.util.Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
            erreurs.add(erreur(ligne, colonne, "Valeur inconnue. Valeurs possibles : " + possibles + "."));
            return defaut;
        }
    }

    private static Tiers.Operateur operateur(LecteurTableau.Ligne ligne, List<Erreur> erreurs) {
        var brute = ligne.valeur("mobile_money_operateur");
        if (brute == null) {
            erreurs.add(erreur(ligne, "mobile_money_operateur", "Opérateur obligatoire avec un numéro Mobile Money (Orange Money, Moov Money, Wave)."));
            return null;
        }
        var cle = brute.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (cle) {
            case "orange", "orangemoney" -> Tiers.Operateur.ORANGE_MONEY;
            case "moov", "moovmoney", "moovafrica" -> Tiers.Operateur.MOOV_MONEY;
            case "wave" -> Tiers.Operateur.WAVE;
            default -> {
                erreurs.add(erreur(ligne, "mobile_money_operateur", "Opérateur inconnu : Orange Money, Moov Money ou Wave."));
                yield null;
            }
        };
    }

    private static Erreur erreur(LecteurTableau.Ligne ligne, String colonne, String message) {
        return new Erreur(ligne.numero(), colonne, ligne.valeurs().get(colonne), message);
    }

    private static String verifierType(String type) {
        if (!TYPES.contains(type)) {
            throw new RegleMetierException("TYPE_IMPORT", "Type d'import inconnu. Types possibles : " + String.join(", ", TYPES) + ".");
        }
        return type;
    }

    private static String entite(String type) {
        return "produits".equals(type) ? "produit" : "tiers";
    }
}
