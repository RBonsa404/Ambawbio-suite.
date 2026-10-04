package bf.ambawbio.referentiel.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Trace d'un import (UC-SOC-06) avec son rapport d'erreurs ligne par ligne. */
@Entity
@Table(schema = "referentiel", name = "import_donnees")
public class ImportDonnees extends EntiteMetier {

    public enum Mode { VERIFICATION, IMPORT }

    public enum Statut { REUSSI, REJETE, PARTIEL }

    /** Erreur sur une ligne du fichier (numéro de ligne du tableur, en-tête = ligne 1). */
    public record Erreur(int ligne, String colonne, String valeur, String message) {
    }

    private String type;
    @Column(name = "nom_fichier")
    private String nomFichier;
    @Enumerated(EnumType.STRING)
    private Mode mode;
    @Enumerated(EnumType.STRING)
    private Statut statut;
    @Column(name = "lignes_total")
    private int lignesTotal;
    @Column(name = "lignes_importees")
    private int lignesImportees;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Erreur> erreurs = new ArrayList<>();

    protected ImportDonnees() {
    }

    public ImportDonnees(UUID id, String type, String nomFichier, Mode mode, Statut statut, int lignesTotal, int lignesImportees, List<Erreur> erreurs) {
        super(id);
        this.type = type;
        this.nomFichier = nomFichier;
        this.mode = mode;
        this.statut = statut;
        this.lignesTotal = lignesTotal;
        this.lignesImportees = lignesImportees;
        this.erreurs = new ArrayList<>(erreurs);
    }

    public String type() { return type; }
    public String nomFichier() { return nomFichier; }
    public Mode mode() { return mode; }
    public Statut statut() { return statut; }
    public int lignesTotal() { return lignesTotal; }
    public int lignesImportees() { return lignesImportees; }
    public List<Erreur> erreurs() { return List.copyOf(erreurs); }
}
