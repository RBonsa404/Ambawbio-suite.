package bf.ambawbio.socle.tenancy;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Dépôt de stock rattaché à un établissement. */
@Entity
@Table(schema = "socle", name = "depot")
public class Depot extends EntiteMetier {

    @Column(name = "etablissement_id")
    private UUID etablissementId;
    private String code;
    private String nom;

    protected Depot() {
    }

    public Depot(UUID id, UUID etablissementId, String code, String nom) {
        super(id);
        this.etablissementId = etablissementId;
        this.code = code;
        this.nom = nom;
    }

    public UUID etablissementId() { return etablissementId; }
    public String code() { return code; }
    public String nom() { return nom; }
}
