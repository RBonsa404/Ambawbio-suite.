package bf.ambawbio.socle.api;

import java.util.List;

/**
 * Catalogue des permissions métier « module:action » (guide §6.5, F-SOC-03).
 * Les permissions des lots suivants sont déclarées dès maintenant pour composer les rôles par défaut.
 */
public final class Permissions {

    public static final String SOCLE_CONSULTER = "socle:consulter";
    public static final String SOCLE_PARAMETRER = "socle:parametrer";
    public static final String SOCLE_UTILISATEURS = "socle:utilisateurs";
    public static final String AUDIT_CONSULTER = "audit:consulter";
    public static final String DONNEES_PERSONNELLES_TRAITER = "donnees-personnelles:traiter";
    public static final String TERMINAUX_GERER = "terminaux:gerer";
    public static final String REFERENTIEL_GERER = "referentiel:gerer";
    public static final String POS_VENDRE = "pos:vendre";
    public static final String POS_CLOTURER = "pos:cloturer";
    public static final String POS_VALIDER_ECART = "pos:valider-ecart";
    public static final String VENTES_GERER = "ventes:gerer";
    public static final String FACTURATION_CREER = "facturation:creer";
    public static final String FACTURATION_VALIDER = "facturation:valider";
    public static final String PAIEMENT_ENCAISSER = "paiement:encaisser";
    public static final String PAIEMENT_RAPPROCHER = "paiement:rapprocher";
    public static final String COMPTABILITE_SAISIR = "comptabilite:saisir";
    public static final String COMPTABILITE_CLOTURER = "comptabilite:cloturer";
    public static final String STOCK_GERER = "stock:gerer";
    public static final String STOCK_INVENTORIER = "stock:inventorier";
    public static final String ACHATS_GERER = "achats:gerer";

    public static final List<String> TOUTES = List.of(
            SOCLE_CONSULTER, SOCLE_PARAMETRER, SOCLE_UTILISATEURS, AUDIT_CONSULTER, DONNEES_PERSONNELLES_TRAITER,
            TERMINAUX_GERER, REFERENTIEL_GERER, POS_VENDRE, POS_CLOTURER, POS_VALIDER_ECART, VENTES_GERER,
            FACTURATION_CREER, FACTURATION_VALIDER, PAIEMENT_ENCAISSER, PAIEMENT_RAPPROCHER, COMPTABILITE_SAISIR,
            COMPTABILITE_CLOTURER, STOCK_GERER, STOCK_INVENTORIER, ACHATS_GERER);

    private Permissions() {
    }
}
