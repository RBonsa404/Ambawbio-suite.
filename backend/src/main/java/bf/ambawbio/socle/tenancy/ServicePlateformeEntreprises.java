package bf.ambawbio.socle.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Accès aux entreprises pour l'administration de la plateforme (mode plateforme ou entreprise en création). */
@Component
public class ServicePlateformeEntreprises {

    private final EntrepriseDepot entreprises;

    ServicePlateformeEntreprises(EntrepriseDepot entreprises) {
        this.entreprises = entreprises;
    }

    public List<Entreprise> toutes() {
        return entreprises.findAll(Sort.by("nom"));
    }

    public Optional<Entreprise> trouver(UUID id) {
        return entreprises.findById(id);
    }

    public Entreprise enregistrer(Entreprise entreprise) {
        return entreprises.save(entreprise);
    }
}
