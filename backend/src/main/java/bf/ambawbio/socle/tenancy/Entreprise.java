package bf.ambawbio.socle.tenancy;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.Ifu;
import bf.ambawbio.shared.domaine.RegleMetierException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Entreprise cliente : c'est le tenant (son identifiant est aussi son {@code tenant_id}). */
@Entity
@Table(schema = "socle", name = "entreprise")
public class Entreprise extends EntiteMetier {

    public enum Statut { ACTIVE, SUSPENDUE }

    private String nom;
    private String ifu;
    private String rccm;
    private String adresse;
    private String ville;
    private String telephone;
    private String courriel;
    @Enumerated(EnumType.STRING)
    private Pack pack;
    @Enumerated(EnumType.STRING)
    private Statut statut;

    protected Entreprise() {
    }

    public Entreprise(UUID id, String nom, Pack pack) {
        super(id);
        this.nom = nom;
        this.pack = pack;
        this.statut = Statut.ACTIVE;
    }

    public void modifier(String nom, String ifu, String rccm, String adresse, String ville, String telephone, String courriel) {
        this.nom = nom;
        this.ifu = ifu == null || ifu.isBlank() ? null : Ifu.depuisSaisie(ifu).valeur();
        this.rccm = rccm;
        this.adresse = adresse;
        this.ville = ville;
        this.telephone = telephone;
        this.courriel = courriel;
    }

    /** RG-16 / INV-15 : hors pack Enterprise, une seule société. */
    public void verifierAjoutSociete(long societesExistantes) {
        if (societesExistantes >= 1 && !pack.autoriseMultiSocietes()) {
            throw new RegleMetierException("INV-15",
                    "Votre offre ne permet qu'une seule société. Passez au pack Enterprise pour gérer plusieurs sociétés.");
        }
    }

    public void suspendre() {
        statut = Statut.SUSPENDUE;
    }

    public void reactiver() {
        statut = Statut.ACTIVE;
    }

    public String nom() { return nom; }
    public String ifu() { return ifu; }
    public String rccm() { return rccm; }
    public String adresse() { return adresse; }
    public String ville() { return ville; }
    public String telephone() { return telephone; }
    public String courriel() { return courriel; }
    public Pack pack() { return pack; }
    public Statut statut() { return statut; }
}
