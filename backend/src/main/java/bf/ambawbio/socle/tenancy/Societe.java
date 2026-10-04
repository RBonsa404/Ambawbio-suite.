package bf.ambawbio.socle.tenancy;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.Ifu;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Société (entité juridique) d'une entreprise. Plusieurs sociétés : pack Enterprise uniquement (RG-16). */
@Entity
@Table(schema = "socle", name = "societe")
public class Societe extends EntiteMetier {

    private String nom;
    private String ifu;
    private String rccm;
    @Column(name = "regime_fiscal")
    private String regimeFiscal;
    private String adresse;
    private boolean principale;

    protected Societe() {
    }

    public Societe(UUID id, String nom, String ifu, String rccm, String regimeFiscal, String adresse, boolean principale) {
        super(id);
        this.nom = nom;
        this.ifu = ifu == null || ifu.isBlank() ? null : Ifu.depuisSaisie(ifu).valeur();
        this.rccm = rccm;
        this.regimeFiscal = regimeFiscal;
        this.adresse = adresse;
        this.principale = principale;
    }

    public String nom() { return nom; }
    public String ifu() { return ifu; }
    public String rccm() { return rccm; }
    public String regimeFiscal() { return regimeFiscal; }
    public String adresse() { return adresse; }
    public boolean principale() { return principale; }
}
