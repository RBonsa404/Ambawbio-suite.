package bf.ambawbio.socle.identite;

import static bf.ambawbio.socle.api.Permissions.ACHATS_GERER;
import static bf.ambawbio.socle.api.Permissions.AUDIT_CONSULTER;
import static bf.ambawbio.socle.api.Permissions.COMPTABILITE_CLOTURER;
import static bf.ambawbio.socle.api.Permissions.COMPTABILITE_SAISIR;
import static bf.ambawbio.socle.api.Permissions.FACTURATION_CREER;
import static bf.ambawbio.socle.api.Permissions.FACTURATION_VALIDER;
import static bf.ambawbio.socle.api.Permissions.PAIEMENT_ENCAISSER;
import static bf.ambawbio.socle.api.Permissions.PAIEMENT_RAPPROCHER;
import static bf.ambawbio.socle.api.Permissions.POS_CLOTURER;
import static bf.ambawbio.socle.api.Permissions.POS_VALIDER_ECART;
import static bf.ambawbio.socle.api.Permissions.POS_VENDRE;
import static bf.ambawbio.socle.api.Permissions.REFERENTIEL_GERER;
import static bf.ambawbio.socle.api.Permissions.SOCLE_CONSULTER;
import static bf.ambawbio.socle.api.Permissions.STOCK_GERER;
import static bf.ambawbio.socle.api.Permissions.STOCK_INVENTORIER;
import static bf.ambawbio.socle.api.Permissions.VENTES_GERER;

import java.util.List;

import bf.ambawbio.socle.api.Permissions;

/** Rôles créés avec chaque entreprise (guide §7.5). Les codes correspondent aux rôles du royaume Keycloak (MFA). */
public final class RolesParDefaut {

    public record Modele(String code, String libelle, List<String> permissions) {
    }

    public static final List<Modele> MODELES = List.of(
            new Modele("administrateur", "Administrateur", Permissions.TOUTES),
            new Modele("dirigeant", "Dirigeant", List.of(SOCLE_CONSULTER, AUDIT_CONSULTER, FACTURATION_VALIDER, POS_VALIDER_ECART)),
            new Modele("gerant", "Gérant", List.of(SOCLE_CONSULTER, Permissions.TERMINAUX_GERER, POS_VENDRE, POS_CLOTURER, POS_VALIDER_ECART, VENTES_GERER,
                    FACTURATION_CREER, FACTURATION_VALIDER, PAIEMENT_ENCAISSER, STOCK_GERER, REFERENTIEL_GERER, AUDIT_CONSULTER)),
            new Modele("comptable", "Comptable", List.of(SOCLE_CONSULTER, COMPTABILITE_SAISIR, COMPTABILITE_CLOTURER,
                    PAIEMENT_RAPPROCHER, FACTURATION_CREER, FACTURATION_VALIDER, AUDIT_CONSULTER)),
            new Modele("caissier", "Caissier", List.of(SOCLE_CONSULTER, POS_VENDRE, POS_CLOTURER, PAIEMENT_ENCAISSER)),
            new Modele("magasinier", "Magasinier", List.of(SOCLE_CONSULTER, STOCK_GERER, STOCK_INVENTORIER, ACHATS_GERER)),
            new Modele("commercial", "Commercial", List.of(SOCLE_CONSULTER, VENTES_GERER, FACTURATION_CREER)));

    private RolesParDefaut() {
    }
}
