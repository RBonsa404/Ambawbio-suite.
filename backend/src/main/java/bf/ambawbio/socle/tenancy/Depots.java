package bf.ambawbio.socle.tenancy;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EntrepriseDepot extends JpaRepository<Entreprise, UUID> {
}

interface SocieteDepot extends JpaRepository<Societe, UUID> {
    List<Societe> findAllByOrderByNomAsc();
}

interface EtablissementDepot extends JpaRepository<Etablissement, UUID> {
    List<Etablissement> findAllByOrderByCodeAsc();
}

interface DepotDepot extends JpaRepository<Depot, UUID> {
    List<Depot> findAllByOrderByCodeAsc();
}
