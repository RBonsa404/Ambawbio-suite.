package bf.ambawbio.referentiel.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.referentiel.application.ServiceTiers;
import bf.ambawbio.referentiel.domaine.Tiers;
import bf.ambawbio.shared.web.PageResultat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@org.springframework.transaction.annotation.Transactional(readOnly = true)
@RequestMapping("/api/v1/referentiel/tiers")
@Tag(name = "Référentiel — tiers", description = "Clients et fournisseurs : IFU, RCCM, régime fiscal, contacts, Mobile Money (F-SOC-06)")
class TiersControleur {

    record ContactVue(String nom, String fonction, String telephone, String courriel, boolean consentementProspection, Instant consentementLe) {
    }

    record CompteVue(Tiers.Operateur operateur, String numero, String titulaire, boolean parDefaut) {
    }

    record TiersVue(UUID id, String code, String nom, Tiers.Nature nature, boolean estClient, boolean estFournisseur, String ifu, String rccm,
            UUID regimeFiscalId, String telephone, String courriel, String adresse, String ville, UUID listePrixId, int delaiPaiementJours,
            Long plafondCredit, boolean actif, List<ContactVue> contacts, List<CompteVue> comptesMobileMoney, Map<String, Object> champsPerso,
            long version) {
        static TiersVue de(Tiers t) {
            return new TiersVue(t.getId(), t.code(), t.nom(), t.nature(), t.estClient(), t.estFournisseur(), t.ifu(), t.rccm(), t.regimeFiscalId(),
                    t.telephone(), t.courriel(), t.adresse(), t.ville(), t.listePrixId(), t.delaiPaiementJours(), t.plafondCredit(), t.actif(),
                    t.contacts().stream().map(c -> new ContactVue(c.nom(), c.fonction(), c.telephone(), c.courriel(), c.consentementProspection(),
                            c.consentementLe())).toList(),
                    t.comptesMobileMoney().stream().map(c -> new CompteVue(c.operateur(), c.numero(), c.titulaire(), c.parDefaut())).toList(),
                    t.champsPerso(), t.version());
        }
    }

    record SaisieContact(@NotBlank(message = "Le nom du contact est obligatoire.") String nom, String fonction, String telephone, String courriel,
            Boolean consentementProspection) {
    }

    record SaisieTiers(@NotBlank(message = "Le code est obligatoire.") String code,
            @NotBlank(message = "Le nom est obligatoire.") String nom,
            Tiers.Nature nature, Boolean estClient, Boolean estFournisseur, String ifu, String rccm, String regimeCode,
            String telephone, String courriel, String adresse, String ville, UUID listePrixId, Integer delaiPaiementJours, Long plafondCredit,
            Boolean actif, List<SaisieContact> contacts, List<CompteVue> comptesMobileMoney, Map<String, Object> champsPerso) {
        ServiceTiers.Commande commande() {
            return new ServiceTiers.Commande(code, nom, nature, !Boolean.FALSE.equals(estClient), Boolean.TRUE.equals(estFournisseur), ifu, rccm,
                    regimeCode, new Tiers.Coordonnees(telephone, courriel, adresse, ville),
                    new Tiers.ConditionsCommerciales(listePrixId, delaiPaiementJours == null ? 0 : delaiPaiementJours, plafondCredit),
                    !Boolean.FALSE.equals(actif),
                    contacts == null ? List.of() : contacts.stream().map(c -> new Tiers.DonneesContact(c.nom(), c.fonction(), c.telephone(), c.courriel(),
                            Boolean.TRUE.equals(c.consentementProspection()))).toList(),
                    comptesMobileMoney == null ? List.of() : comptesMobileMoney.stream().map(c -> new Tiers.DonneesCompte(c.operateur(), c.numero(),
                            c.titulaire(), c.parDefaut())).toList(),
                    champsPerso);
        }
    }

    record NouveauTiers(@NotNull(message = "L'identifiant est obligatoire.") UUID id, @Valid @NotNull SaisieTiers tiers) {
    }

    private final ServiceTiers service;

    TiersControleur(ServiceTiers service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('socle:consulter')")
    @Operation(summary = "Rechercher des tiers", description = "Par nom, code, téléphone ou IFU, sans accents ; filtres client, fournisseur, champ.<code>.")
    PageResultat<TiersVue> rechercher(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean client,
            @RequestParam(required = false) Boolean fournisseur, @RequestParam(required = false) Boolean actif,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille, @RequestParam Map<String, String> parametres) {
        return PageResultat.de(service.rechercher(q, client, fournisseur, actif, parametres, page, taille), TiersVue::de);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('socle:consulter')")
    TiersVue tiers(@PathVariable UUID id) {
        return TiersVue.de(service.tiers(id));
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('referentiel:gerer') or hasAuthority('ventes:gerer')")
    TiersVue creer(@Valid @RequestBody NouveauTiers n) {
        return TiersVue.de(service.creer(n.id(), n.tiers().commande()));
    }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('referentiel:gerer') or hasAuthority('ventes:gerer')")
    TiersVue modifier(@PathVariable UUID id, @Valid @RequestBody SaisieTiers t) {
        return TiersVue.de(service.modifier(id, t.commande()));
    }
}
