package bf.ambawbio.socle.identite;

import java.util.UUID;

import bf.ambawbio.shared.domaine.EntiteMetier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Utilisateur d'une entreprise ; l'authentification est déléguée à Keycloak ({@code keycloak_id} = sujet du jeton). */
@Entity
@Table(schema = "socle", name = "utilisateur")
public class Utilisateur extends EntiteMetier {

    @Column(name = "keycloak_id")
    private String keycloakId;
    @Column(name = "nom_utilisateur")
    private String nomUtilisateur;
    private String prenom;
    private String nom;
    private String courriel;
    private String telephone;
    private boolean actif;

    protected Utilisateur() {
    }

    public Utilisateur(UUID id, String keycloakId, String nomUtilisateur, String prenom, String nom, String courriel, String telephone) {
        super(id);
        this.keycloakId = keycloakId;
        this.nomUtilisateur = nomUtilisateur;
        this.prenom = prenom;
        this.nom = nom;
        this.courriel = courriel;
        this.telephone = telephone;
        this.actif = true;
    }

    public void modifier(String prenom, String nom, String telephone) {
        this.prenom = prenom;
        this.nom = nom;
        this.telephone = telephone;
    }

    public void definirActif(boolean actif) {
        this.actif = actif;
    }

    public String nomComplet() {
        return ((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom)).trim();
    }

    public String keycloakId() { return keycloakId; }
    public String nomUtilisateur() { return nomUtilisateur; }
    public String prenom() { return prenom; }
    public String nom() { return nom; }
    public String courriel() { return courriel; }
    public String telephone() { return telephone; }
    public boolean actif() { return actif; }
}
