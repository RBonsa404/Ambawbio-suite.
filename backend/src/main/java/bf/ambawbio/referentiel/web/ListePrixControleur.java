package bf.ambawbio.referentiel.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
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

import bf.ambawbio.referentiel.application.ServiceListesPrix;
import bf.ambawbio.referentiel.domaine.ListePrix;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@org.springframework.transaction.annotation.Transactional(readOnly = true)
@RequestMapping("/api/v1/referentiel")
@Tag(name = "Référentiel — prix", description = "Listes de prix par client, quantité ou période ; prix applicable (F-VEN-01)")
class ListePrixControleur {

    record LigneVue(@NotNull UUID produitId, String conditionnementCode, BigDecimal quantiteMin, @NotNull Long prix, LocalDate dateDebut,
            LocalDate dateFin) {
    }

    record ListeVue(UUID id, String nom, boolean actif, List<LigneVue> lignes) {
        static ListeVue de(ListePrix l) {
            return new ListeVue(l.getId(), l.nom(), l.actif(), l.lignes().stream().map(x -> new LigneVue(x.produitId(), x.conditionnementCode(),
                    x.quantiteMin(), x.prix(), x.dateDebut(), x.dateFin())).toList());
        }
    }

    record SaisieListe(@NotBlank(message = "Le nom est obligatoire.") String nom, Boolean actif, List<@Valid LigneVue> lignes) {
        List<ListePrix.DonneesLigne> donnees() {
            return lignes == null ? List.of() : lignes.stream().map(l -> new ListePrix.DonneesLigne(l.produitId(), l.conditionnementCode(),
                    l.quantiteMin(), l.prix(), l.dateDebut(), l.dateFin())).toList();
        }
    }

    record NouvelleListe(@NotNull UUID id, @Valid @NotNull SaisieListe liste) {
    }

    private final ServiceListesPrix service;

    ListePrixControleur(ServiceListesPrix service) {
        this.service = service;
    }

    @GetMapping("/listes-prix")
    @PreAuthorize("hasAuthority('socle:consulter')")
    List<ListeVue> listes() {
        return service.listes().stream().map(ListeVue::de).toList();
    }

    @GetMapping("/listes-prix/{id}")
    @PreAuthorize("hasAuthority('socle:consulter')")
    ListeVue liste(@PathVariable UUID id) {
        return ListeVue.de(service.liste(id));
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/listes-prix")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('referentiel:gerer')")
    ListeVue creer(@Valid @RequestBody NouvelleListe n) {
        return ListeVue.de(service.enregistrer(n.id(), n.liste().nom(), !Boolean.FALSE.equals(n.liste().actif()), n.liste().donnees(), true));
    }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/listes-prix/{id}")
    @PreAuthorize("hasAuthority('referentiel:gerer')")
    ListeVue modifier(@PathVariable UUID id, @Valid @RequestBody SaisieListe s) {
        return ListeVue.de(service.enregistrer(id, s.nom(), !Boolean.FALSE.equals(s.actif()), s.donnees(), false));
    }

    @GetMapping("/prix")
    @PreAuthorize("hasAuthority('socle:consulter')")
    @Operation(summary = "Prix applicable", description = "Liste de prix du client, sinon prix du conditionnement, sinon prix de base.")
    ServiceListesPrix.PrixApplicable prix(@RequestParam UUID produit, @RequestParam(required = false) String conditionnement,
            @RequestParam(required = false) UUID tiers, @RequestParam(defaultValue = "1") BigDecimal quantite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.prix(produit, conditionnement, tiers, quantite, date == null ? LocalDate.now() : date);
    }
}
