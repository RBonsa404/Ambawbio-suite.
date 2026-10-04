package bf.ambawbio.socle.identite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bf.ambawbio.shared.domaine.RegleMetierException;
import bf.ambawbio.shared.domaine.RessourceIntrouvableException;
import bf.ambawbio.shared.domaine.Uuid7;
import bf.ambawbio.socle.api.JournalAudit;

/** Utilisateurs, rôles et affectations de l'entreprise courante (UC-SOC-03, F-SOC-03). */
@Service
public class ServiceIdentite {

    private final UtilisateurDepot utilisateurs;
    private final RoleDepot roles;
    private final AffectationDepot affectations;
    private final PortAnnuaire annuaire;
    private final JournalAudit audit;

    ServiceIdentite(UtilisateurDepot utilisateurs, RoleDepot roles, AffectationDepot affectations, PortAnnuaire annuaire, JournalAudit audit) {
        this.utilisateurs = utilisateurs;
        this.roles = roles;
        this.affectations = affectations;
        this.annuaire = annuaire;
        this.audit = audit;
    }

    /** Profil d'accès de l'utilisateur connecté (sujet du jeton), dans l'entreprise du contexte. */
    @Transactional(readOnly = true)
    public Optional<ProfilAcces> profil(String keycloakId) {
        return utilisateurs.findByKeycloakId(keycloakId).map(this::profilDe);
    }

    private ProfilAcces profilDe(Utilisateur utilisateur) {
        var liste = affectations.findByUtilisateurId(utilisateur.getId());
        Map<UUID, Role> parId = new HashMap<>();
        roles.findAllById(liste.stream().map(AffectationRole::roleId).collect(Collectors.toSet())).forEach(r -> parId.put(r.getId(), r));
        Set<String> codes = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        Set<UUID> etablissements = new HashSet<>();
        boolean tous = false;
        for (var a : liste) {
            var role = parId.get(a.roleId());
            codes.add(role.code());
            permissions.addAll(role.permissions());
            if (a.etablissementId() == null) {
                tous = true;
            } else {
                etablissements.add(a.etablissementId());
            }
        }
        return new ProfilAcces(utilisateur.getId(), utilisateur.actif(), Set.copyOf(codes),
                utilisateur.actif() ? Set.copyOf(permissions) : Set.of(), tous, Set.copyOf(etablissements));
    }

    @Transactional(readOnly = true)
    public List<Utilisateur> utilisateurs() {
        return utilisateurs.findAllByOrderByNomAscPrenomAsc();
    }

    @Transactional(readOnly = true)
    public Utilisateur utilisateur(UUID id) {
        return utilisateurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    @Transactional(readOnly = true)
    public ProfilAcces profilDe(UUID utilisateurId) {
        return profilDe(utilisateur(utilisateurId));
    }

    /** Crée le compte dans Keycloak (courriel de définition du mot de passe) puis l'utilisateur. Idempotent. */
    @Transactional
    public Utilisateur creerUtilisateur(UUID id, UUID tenantId, String nomUtilisateur, String prenom, String nom, String courriel, String telephone) {
        var existant = utilisateurs.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        var keycloakId = annuaire.creerCompte(new PortAnnuaire.NouveauCompte(nomUtilisateur, prenom, nom, courriel, tenantId));
        var utilisateur = utilisateurs.save(new Utilisateur(id, keycloakId, nomUtilisateur, prenom, nom, courriel, telephone));
        audit.enregistrer("UTILISATEUR_CREE", "utilisateur", id, null, Map.of("nomUtilisateur", nomUtilisateur));
        return utilisateur;
    }

    /** Rattache un compte Keycloak déjà existant (données de démonstration), sans passer par l'annuaire. */
    @Transactional
    public Utilisateur rattacherCompteExistant(UUID id, String keycloakId, String nomUtilisateur, String prenom, String nom, String courriel) {
        return utilisateurs.findById(id).orElseGet(() -> utilisateurs.save(new Utilisateur(id, keycloakId, nomUtilisateur, prenom, nom, courriel, null)));
    }

    /** Affectation sans synchronisation Keycloak (données de démonstration : rôles déjà présents dans le royaume). */
    @Transactional
    public void affecterSansSynchronisation(UUID id, UUID utilisateurId, UUID roleId, UUID etablissementId) {
        if (!affectations.existsById(id)) {
            affectations.save(new AffectationRole(id, utilisateurId, roleId, etablissementId));
        }
    }

    @Transactional
    public Utilisateur modifierUtilisateur(UUID id, String prenom, String nom, String telephone, boolean actif) {
        var utilisateur = utilisateur(id);
        boolean changementActif = utilisateur.actif() != actif;
        utilisateur.modifier(prenom, nom, telephone);
        utilisateur.definirActif(actif);
        if (changementActif) {
            annuaire.definirActif(utilisateur.keycloakId(), actif);
            audit.enregistrer(actif ? "UTILISATEUR_REACTIVE" : "UTILISATEUR_DESACTIVE", "utilisateur", id, null, null);
        }
        return utilisateur;
    }

    /** Changement de droits : journalisé (guide §6.6) et répercuté sur les rôles Keycloak (MFA). */
    @Transactional
    public AffectationRole affecter(UUID id, UUID utilisateurId, UUID roleId, UUID etablissementId) {
        var existante = affectations.findById(id);
        if (existante.isPresent()) {
            return existante.get();
        }
        var utilisateur = utilisateur(utilisateurId);
        var role = roles.findById(roleId).orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable."));
        var affectation = affectations.save(new AffectationRole(id, utilisateurId, roleId, etablissementId));
        affectations.flush();
        audit.enregistrer("DROITS_MODIFIES", "utilisateur", utilisateurId, null,
                Map.of("ajout", role.code(), "etablissement", String.valueOf(etablissementId)));
        annuaire.synchroniserRoles(utilisateur.keycloakId(), profilDe(utilisateur).roles());
        return affectation;
    }

    @Transactional
    public void retirerAffectation(UUID affectationId) {
        var affectation = affectations.findById(affectationId)
                .orElseThrow(() -> new RessourceIntrouvableException("Affectation introuvable."));
        var utilisateur = utilisateur(affectation.utilisateurId());
        var role = roles.findById(affectation.roleId()).orElseThrow();
        affectations.delete(affectation);
        affectations.flush();
        audit.enregistrer("DROITS_MODIFIES", "utilisateur", utilisateur.getId(),
                Map.of("retrait", role.code(), "etablissement", String.valueOf(affectation.etablissementId())), null);
        annuaire.synchroniserRoles(utilisateur.keycloakId(), profilDe(utilisateur).roles());
    }

    @Transactional(readOnly = true)
    public List<AffectationRole> affectationsDe(UUID utilisateurId) {
        return affectations.findByUtilisateurId(utilisateurId);
    }

    @Transactional(readOnly = true)
    public List<Role> roles() {
        return roles.findAllByOrderByLibelleAsc();
    }

    @Transactional
    public Role creerRole(UUID id, String code, String libelle, List<String> permissions) {
        var existant = roles.findById(id);
        if (existant.isPresent()) {
            return existant.get();
        }
        if (roles.findByCode(code).isPresent()) {
            throw new RegleMetierException("ROLE_EXISTANT", "Un rôle avec ce code existe déjà.");
        }
        var role = roles.save(new Role(id, code, libelle, permissions, false));
        audit.enregistrer("ROLE_CREE", "role", id, null, Map.of("code", code, "permissions", role.permissions()));
        return role;
    }

    @Transactional
    public Role modifierRole(UUID id, String libelle, List<String> permissions) {
        var role = roles.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable."));
        var avant = role.permissions();
        role.modifier(libelle, permissions);
        audit.enregistrer("DROITS_MODIFIES", "role", id, Map.of("permissions", avant), Map.of("permissions", role.permissions()));
        return role;
    }

    /** Rôles système d'une nouvelle entreprise (guide §7.5). */
    @Transactional
    public Map<String, Role> creerRolesParDefaut() {
        Map<String, Role> resultat = new HashMap<>();
        for (var modele : RolesParDefaut.MODELES) {
            resultat.put(modele.code(), roles.save(new Role(Uuid7.nouveau(), modele.code(), modele.libelle(), modele.permissions(), true)));
        }
        return resultat;
    }
}
