package bf.ambawbio.referentiel.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.Uuid7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Liste de prix : prix par client, par quantité ou par période (F-VEN-01). */
@Entity
@Table(schema = "referentiel", name = "liste_prix")
public class ListePrix extends EntiteMetier {

    public record DonneesLigne(UUID produitId, String conditionnementCode, BigDecimal quantiteMin, long prix, LocalDate dateDebut, LocalDate dateFin) {
    }

    private String nom;
    private boolean actif;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "liste_id", nullable = false, updatable = false)
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<Ligne> lignes = new ArrayList<>();

    protected ListePrix() {
    }

    public ListePrix(UUID id, String nom) {
        super(id);
        modifier(nom, true);
    }

    public final void modifier(String nom, boolean actif) {
        if (nom == null || nom.isBlank()) {
            throw new RegleMetierException("NOM_OBLIGATOIRE", "Le nom de la liste de prix est obligatoire.");
        }
        this.nom = nom.trim();
        this.actif = actif;
    }

    public void definirLignes(List<DonneesLigne> donnees) {
        lignes.clear();
        donnees.forEach(d -> lignes.add(new Ligne(Uuid7.nouveau(), d)));
    }

    public String nom() { return nom; }
    public boolean actif() { return actif; }
    public List<Ligne> lignes() { return List.copyOf(lignes); }

    @Entity
    @Table(schema = "referentiel", name = "ligne_liste_prix")
    public static class Ligne extends EntiteMetier {
        @Column(name = "produit_id")
        private UUID produitId;
        @Column(name = "conditionnement_code")
        private String conditionnementCode;
        @Column(name = "quantite_min")
        private BigDecimal quantiteMin;
        private long prix;
        @Column(name = "date_debut")
        private LocalDate dateDebut;
        @Column(name = "date_fin")
        private LocalDate dateFin;

        protected Ligne() {
        }

        Ligne(UUID id, DonneesLigne d) {
            super(id);
            if (d.prix() < 0) {
                throw new RegleMetierException("PRIX_INVALIDE", "Un prix ne peut pas être négatif.");
            }
            if (d.dateDebut() != null && d.dateFin() != null && d.dateFin().isBefore(d.dateDebut())) {
                throw new RegleMetierException("PERIODE_INVALIDE", "La date de fin précède la date de début.");
            }
            this.produitId = d.produitId();
            this.conditionnementCode = d.conditionnementCode();
            this.quantiteMin = d.quantiteMin() == null ? BigDecimal.ONE : d.quantiteMin();
            this.prix = d.prix();
            this.dateDebut = d.dateDebut();
            this.dateFin = d.dateFin();
        }

        /** La ligne s'applique-t-elle à ce produit, ce conditionnement, cette quantité et cette date ? */
        public boolean sApplique(UUID produit, String conditionnement, BigDecimal quantite, LocalDate date) {
            return produitId.equals(produit)
                    && java.util.Objects.equals(conditionnementCode, conditionnement)
                    && quantite.compareTo(quantiteMin) >= 0
                    && (dateDebut == null || !date.isBefore(dateDebut))
                    && (dateFin == null || !date.isAfter(dateFin));
        }

        public UUID produitId() { return produitId; }
        public String conditionnementCode() { return conditionnementCode; }
        public BigDecimal quantiteMin() { return quantiteMin; }
        public long prix() { return prix; }
        public LocalDate dateDebut() { return dateDebut; }
        public LocalDate dateFin() { return dateFin; }
    }
}
