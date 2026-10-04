package bf.ambawbio.referentiel.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bf.ambawbio.referentiel.domaine.ImportDonnees;
import bf.ambawbio.referentiel.domaine.ListePrix;
import bf.ambawbio.referentiel.domaine.Parametres.Categorie;
import bf.ambawbio.referentiel.domaine.Parametres.RegimeFiscal;
import bf.ambawbio.referentiel.domaine.Parametres.Taxe;
import bf.ambawbio.referentiel.domaine.Parametres.TypeConditionnement;
import bf.ambawbio.referentiel.domaine.Parametres.UniteMesure;
import bf.ambawbio.referentiel.domaine.Produit;
import bf.ambawbio.referentiel.domaine.Tiers;

interface RegimeDepot extends JpaRepository<RegimeFiscal, UUID> {
    List<RegimeFiscal> findAllByOrderByCodeAsc();

    Optional<RegimeFiscal> findByCode(String code);
}

interface TaxeDepot extends JpaRepository<Taxe, UUID> {
    List<Taxe> findAllByOrderByCodeAsc();

    Optional<Taxe> findByCode(String code);
}

interface UniteDepot extends JpaRepository<UniteMesure, UUID> {
    List<UniteMesure> findAllByOrderByCodeAsc();

    Optional<UniteMesure> findByCode(String code);
}

interface TypeConditionnementDepot extends JpaRepository<TypeConditionnement, UUID> {
    List<TypeConditionnement> findAllByOrderByCodeAsc();
}

interface CategorieDepot extends JpaRepository<Categorie, UUID> {
    List<Categorie> findAllByOrderByNomAsc();

    Optional<Categorie> findByNomIgnoreCase(String nom);
}

interface ListePrixDepot extends JpaRepository<ListePrix, UUID> {
    List<ListePrix> findAllByOrderByNomAsc();
}

interface ImportDepot extends JpaRepository<ImportDonnees, UUID> {
}

/**
 * Recherche rapide (index trigrammes sur le nom et le code, sans accents) et filtres sur les champs personnalisés
 * (inclusion JSON, index GIN). Requêtes natives : l'entreprise est filtrée explicitement en plus de la RLS.
 */
interface ProduitDepot extends JpaRepository<Produit, UUID> {

    Optional<Produit> findByCode(String code);

    List<Produit> findByCodeIn(List<String> codes);

    String FILTRE = """
            from referentiel.produit p
            where p.tenant_id = :tenant
              and (cast(:q as text) is null
                   or referentiel.sans_accent(p.nom || ' ' || p.code) like '%' || referentiel.sans_accent(cast(:q as text)) || '%'
                   or exists (select 1 from referentiel.code_barre cb where cb.produit_id = p.id and cb.valeur = cast(:q as text)))
              and (cast(:categorie as uuid) is null or p.categorie_id = cast(:categorie as uuid))
              and (cast(:actif as boolean) is null or p.actif = cast(:actif as boolean))
              and p.champs_perso @> cast(:champs as jsonb)
            """;

    @Query(value = "select p.* " + FILTRE
            + " order by similarity(referentiel.sans_accent(p.nom || ' ' || p.code), referentiel.sans_accent(coalesce(cast(:q as text), ''))) desc, p.nom",
            countQuery = "select count(*) " + FILTRE, nativeQuery = true)
    Page<Produit> rechercher(@Param("tenant") UUID tenant, @Param("q") String q, @Param("categorie") UUID categorie,
            @Param("actif") Boolean actif, @Param("champs") String champs, Pageable page);

    @Query(value = "select p.* from referentiel.produit p join referentiel.code_barre cb on cb.produit_id = p.id "
            + "where p.tenant_id = :tenant and cb.valeur = :valeur", nativeQuery = true)
    Optional<Produit> parCodeBarre(@Param("tenant") UUID tenant, @Param("valeur") String valeur);

    @Query(value = "select cb.valeur from referentiel.code_barre cb where cb.tenant_id = :tenant and cb.valeur in (:valeurs) "
            + "and cb.produit_id <> :produit", nativeQuery = true)
    List<String> codesBarresPrisAilleurs(@Param("tenant") UUID tenant, @Param("valeurs") List<String> valeurs, @Param("produit") UUID produit);

    @Query(value = "select cb.valeur || ':' || p.code from referentiel.code_barre cb join referentiel.produit p on p.id = cb.produit_id "
            + "where cb.tenant_id = :tenant", nativeQuery = true)
    List<String> tousLesCodesBarres(@Param("tenant") UUID tenant);
}

interface TiersDepot extends JpaRepository<Tiers, UUID> {

    Optional<Tiers> findByCode(String code);

    List<Tiers> findByCodeIn(List<String> codes);

    String FILTRE = """
            from referentiel.tiers t
            where t.tenant_id = :tenant
              and (cast(:q as text) is null
                   or referentiel.sans_accent(t.nom || ' ' || t.code || ' ' || coalesce(t.telephone, '') || ' ' || coalesce(t.ifu, ''))
                      like '%' || referentiel.sans_accent(cast(:q as text)) || '%')
              and (cast(:client as boolean) is null or t.est_client = cast(:client as boolean))
              and (cast(:fournisseur as boolean) is null or t.est_fournisseur = cast(:fournisseur as boolean))
              and (cast(:actif as boolean) is null or t.actif = cast(:actif as boolean))
              and t.champs_perso @> cast(:champs as jsonb)
            """;

    @Query(value = "select t.* " + FILTRE
            + " order by similarity(referentiel.sans_accent(t.nom || ' ' || t.code), referentiel.sans_accent(coalesce(cast(:q as text), ''))) desc, t.nom",
            countQuery = "select count(*) " + FILTRE, nativeQuery = true)
    Page<Tiers> rechercher(@Param("tenant") UUID tenant, @Param("q") String q, @Param("client") Boolean client,
            @Param("fournisseur") Boolean fournisseur, @Param("actif") Boolean actif, @Param("champs") String champs, Pageable page);
}
