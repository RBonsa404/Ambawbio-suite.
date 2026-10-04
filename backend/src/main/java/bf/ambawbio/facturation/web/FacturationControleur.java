package bf.ambawbio.facturation.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bf.ambawbio.facturation.application.ServiceFacturation;
import bf.ambawbio.facturation.domaine.DocumentFiscal;

/** Factures et avoirs (guide §12 ; W-10). */
@RestController
@RequestMapping("/api/v1/facturation/documents")
class FacturationControleur {

    record LigneVue(UUID id, UUID produitId, String designation, String unite, BigDecimal quantite, long prixUnitaire, boolean prixTtc, long remise,
            String taxeCode, BigDecimal taux, long montantHt, long montantTaxe, long montantTtc, UUID ligneOrigineId) {
    }

    record DocumentVue(UUID id, String type, String statut, String numero, LocalDate dateEmission, LocalDate dateEcheance, UUID clientId,
            String clientNom, String clientIfu, UUID factureOrigineId, String motif, long totalHt, long totalTaxes, long totalTtc, long totalAvoirs,
            String origine, Instant valideLe, String fecStatut, String fecIdentifiant, Instant fecHorodatage, boolean fecSimulee, String fecMessage,
            List<LigneVue> lignes) {
        static DocumentVue de(DocumentFiscal d) {
            return new DocumentVue(d.getId(), d.type().name(), d.statut().name(), d.numero(), d.dateEmission(), d.dateEcheance(), d.clientId(),
                    d.client().nom(), d.client().ifu(), d.factureOrigineId(), d.motif(), d.totalHt(), d.totalTaxes(), d.totalTtc(), d.totalAvoirs(),
                    d.origine(), d.valideLe(), d.fecStatut().name(), d.fecIdentifiant(), d.fecHorodatage(), d.fecSimulee(), d.fecMessage(),
                    d.lignes().stream().map(l -> new LigneVue(l.getId(), l.produitId(), l.designation(), l.unite(), l.quantite(), l.prixUnitaire(),
                            l.prixTtc(), l.remise(), l.taxeCode(), l.taux(), l.montantHt(), l.montantTaxe(), l.montantTtc(), l.ligneOrigineId())).toList());
        }
    }

    record DemandeAvoir(UUID id, String motif, Map<UUID, BigDecimal> quantites) {
    }

    private final ServiceFacturation service;

    FacturationControleur(ServiceFacturation service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('facturation:creer', 'facturation:valider')")
    List<DocumentVue> liste() {
        return service.lireTout(DocumentVue::de);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('facturation:creer', 'facturation:valider')")
    DocumentVue document(@PathVariable UUID id) {
        return service.lire(id, DocumentVue::de);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('facturation:creer')")
    DocumentVue creer(@RequestBody ServiceFacturation.DemandeDocument demande) {
        return document(service.creerBrouillon(demande).getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('facturation:creer')")
    DocumentVue modifier(@PathVariable UUID id, @RequestBody ServiceFacturation.DemandeDocument demande) {
        service.modifierBrouillon(id, demande);
        return document(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('facturation:creer')")
    void supprimer(@PathVariable UUID id) {
        service.supprimerBrouillon(id);
    }

    @PostMapping("/{id}/validation")
    @PreAuthorize("hasAuthority('facturation:valider')")
    DocumentVue valider(@PathVariable UUID id) {
        service.valider(id);
        return document(id);
    }

    @PostMapping("/{id}/avoirs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('facturation:valider')")
    DocumentVue avoir(@PathVariable UUID id, @RequestBody DemandeAvoir demande) {
        var avoir = service.emettreAvoir(id, new ServiceFacturation.DemandeAvoir(demande.id(), demande.motif(), demande.quantites()));
        return document(avoir.getId());
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('facturation:creer', 'facturation:valider')")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var doc = service.document(id);
        var nom = (doc.numero() == null ? "brouillon-" + id : doc.numero()) + ".pdf";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nom).build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(service.pdf(id));
    }
}
