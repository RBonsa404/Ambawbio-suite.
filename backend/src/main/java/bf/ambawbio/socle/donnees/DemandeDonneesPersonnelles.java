package bf.ambawbio.socle.donnees;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Demande d'une personne sur ses données (F-SOC-18, UC-SOC-11 ; loi n° 001-2021/AN). */
@Entity
@Table(schema = "socle", name = "demande_donnees_personnelles")
public class DemandeDonneesPersonnelles extends EntiteMetier {

    public enum Type { ACCES, RECTIFICATION, EFFACEMENT, OPPOSITION }

    public enum Statut { RECUE, EN_COURS, TRAITEE, REFUSEE }

    @Enumerated(EnumType.STRING)
    private Type type;
    @Column(name = "personne_concernee")
    private String personneConcernee;
    private String contact;
    private String description;
    @Enumerated(EnumType.STRING)
    private Statut statut;
    private LocalDate echeance;
    private String reponse;
    @Column(name = "traitee_le")
    private Instant traiteeLe;

    protected DemandeDonneesPersonnelles() {
    }

    public DemandeDonneesPersonnelles(UUID id, Type type, String personneConcernee, String contact, String description, LocalDate echeance) {
        super(id);
        this.type = type;
        this.personneConcernee = personneConcernee;
        this.contact = contact;
        this.description = description;
        this.echeance = echeance;
        this.statut = Statut.RECUE;
    }

    public void prendreEnCharge() {
        verifierOuverte();
        statut = Statut.EN_COURS;
    }

    /** Clôture avec une réponse motivée. L'effacement effectif (RG-12, anonymisation) est réalisé par chaque module. */
    public void cloturer(boolean acceptee, String reponse) {
        verifierOuverte();
        if (reponse == null || reponse.isBlank()) {
            throw new RegleMetierException("REPONSE_OBLIGATOIRE", "Indiquez la réponse apportée à la personne.");
        }
        this.statut = acceptee ? Statut.TRAITEE : Statut.REFUSEE;
        this.reponse = reponse;
        this.traiteeLe = Instant.now();
    }

    private void verifierOuverte() {
        if (statut == Statut.TRAITEE || statut == Statut.REFUSEE) {
            throw new RegleMetierException("DEMANDE_CLOTUREE", "Cette demande est déjà clôturée.");
        }
    }

    public Type type() { return type; }
    public String personneConcernee() { return personneConcernee; }
    public String contact() { return contact; }
    public String description() { return description; }
    public Statut statut() { return statut; }
    public LocalDate echeance() { return echeance; }
    public String reponse() { return reponse; }
    public Instant traiteeLe() { return traiteeLe; }
}
