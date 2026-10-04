package bf.ambawbio.socle.tenancy;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Établissement (boutique, agence, entrepôt) rattaché à une société. */
@Entity
@Table(schema = "socle", name = "etablissement")
public class Etablissement extends EntiteMetier {

    @Column(name = "societe_id")
    private UUID societeId;
    private String code;
    private String nom;
    private String adresse;
    private String ville;

    protected Etablissement() {
    }

    public Etablissement(UUID id, UUID societeId, String code, String nom, String adresse, String ville) {
        super(id);
        this.societeId = societeId;
        this.code = code;
        this.nom = nom;
        this.adresse = adresse;
        this.ville = ville;
    }

    public UUID societeId() { return societeId; }
    public String code() { return code; }
    public String nom() { return nom; }
    public String adresse() { return adresse; }
    public String ville() { return ville; }
}
