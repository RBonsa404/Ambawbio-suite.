package bf.ambawbio.referentiel.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.referentiel.domaine.ListePrix;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.socle.api.JournalAudit;

/** Listes de prix et calcul du prix applicable (F-VEN-01). */
@Service
public class ServiceListesPrix {

    public record PrixApplicable(long prixUnitaire, String source, UUID listeId) {
    }

    private final ListePrixDepot listes;
    private final ProduitDepot produits;
    private final TiersDepot tiers;
    private final JournalAudit audit;

    ServiceListesPrix(ListePrixDepot listes, ProduitDepot produits, TiersDepot tiers, JournalAudit audit) {
        this.listes = listes;
        this.produits = produits;
        this.tiers = tiers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ListePrix> listes() {
        return listes.findAllByOrderByNomAsc();
    }

    @Transactional(readOnly = true)
    public ListePrix liste(UUID id) {
        return listes.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Liste de prix introuvable."));
    }

    @Transactional
    public ListePrix enregistrer(UUID id, String nom, boolean actif, List<ListePrix.DonneesLigne> lignes, boolean creation) {
        var liste = listes.findById(id).orElse(null);
        if (liste == null) {
            if (!creation) {
                throw new RessourceIntrouvableException("Liste de prix introuvable.");
            }
            liste = new ListePrix(id, nom);
        }
        liste.modifier(nom, actif);
        var inconnus = lignes.stream().map(ListePrix.DonneesLigne::produitId).distinct().filter(p -> !produits.existsById(p)).toList();
        if (!inconnus.isEmpty()) {
            throw new RegleMetierException("PRODUIT_INCONNU", "Produits introuvables dans la liste de prix.");
        }
        liste.definirLignes(lignes);
        listes.save(liste);
        audit.enregistrer(creation ? "LISTE_PRIX_CREEE" : "PRIX_MODIFIE", "liste_prix", id, null, Map.of("nom", nom, "lignes", lignes.size()));
        return liste;
    }

    /**
     * Prix unitaire applicable : ligne de la liste du client (bon conditionnement, quantité minimale atteinte, période en cours ;
     * la plus forte quantité minimale l'emporte), sinon prix du conditionnement, sinon prix de base × quantité du conditionnement.
     */
    @Transactional(readOnly = true)
    public PrixApplicable prix(UUID produitId, String conditionnementCode, UUID tiersId, BigDecimal quantite, LocalDate date) {
        var produit = produits.findById(produitId).orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable."));
        var conditionnement = conditionnementCode == null ? null : produit.conditionnements().stream()
                .filter(c -> c.code().equals(conditionnementCode)).findFirst()
                .orElseThrow(() -> new RegleMetierException("CONDITIONNEMENT_INCONNU", "Conditionnement inconnu pour ce produit."));
        if (tiersId != null) {
            var client = tiers.findById(tiersId).orElseThrow(() -> new RessourceIntrouvableException("Tiers introuvable."));
            if (client.listePrixId() != null) {
                var liste = listes.findById(client.listePrixId()).filter(ListePrix::actif);
                var ligne = liste.stream().flatMap(l -> l.lignes().stream())
                        .filter(l -> l.sApplique(produitId, conditionnementCode, quantite, date))
                        .max(Comparator.comparing(ListePrix.Ligne::quantiteMin));
                if (ligne.isPresent()) {
                    return new PrixApplicable(ligne.get().prix(), "LISTE_PRIX", client.listePrixId());
                }
            }
        }
        if (conditionnement == null) {
            return new PrixApplicable(produit.prixVente(), "PRIX_DE_BASE", null);
        }
        if (conditionnement.prixVente() != null) {
            return new PrixApplicable(conditionnement.prixVente(), "PRIX_CONDITIONNEMENT", null);
        }
        var calcule = BigDecimal.valueOf(produit.prixVente()).multiply(conditionnement.quantite()).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new PrixApplicable(calcule, "PRIX_DE_BASE", null);
    }
}
