package bf.ambawbio.referentiel.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import bf.ambawbio.referentiel.application.ServiceImport;
import bf.ambawbio.referentiel.domaine.ImportDonnees;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/referentiel/imports")
@PreAuthorize("hasAuthority('referentiel:gerer')")
@Tag(name = "Référentiel — import", description = "Assistant d'import CSV/Excel avec rapport d'erreurs ligne par ligne (F-SOC-16, UC-SOC-06)")
class ImportControleur {

    record ImportVue(UUID id, String type, String nomFichier, ImportDonnees.Mode mode, ImportDonnees.Statut statut, int lignesTotal,
            int lignesImportees, List<ImportDonnees.Erreur> erreurs) {
        static ImportVue de(ImportDonnees i) {
            return new ImportVue(i.getId(), i.type(), i.nomFichier(), i.mode(), i.statut(), i.lignesTotal(), i.lignesImportees(), i.erreurs());
        }
    }

    private final ServiceImport service;

    ImportControleur(ServiceImport service) {
        this.service = service;
    }

    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer un fichier",
            description = "type = produits ou tiers. mode = VERIFICATION (rien n'est enregistré) ou IMPORT. Par défaut, un fichier avec des erreurs "
                    + "n'importe rien ; lignesValidesSeulement=true importe les lignes correctes.")
    ImportVue importer(@PathVariable String type, @RequestPart("fichier") MultipartFile fichier,
            @RequestParam(defaultValue = "IMPORT") ImportDonnees.Mode mode,
            @RequestParam(defaultValue = "false") boolean lignesValidesSeulement) throws IOException {
        try (var flux = fichier.getInputStream()) {
            return ImportVue.de(service.importer(type, fichier.getOriginalFilename(), flux, mode, lignesValidesSeulement));
        }
    }

    @GetMapping("/{id:[0-9a-f-]{36}}")
    ImportVue importDonnees(@PathVariable UUID id) {
        return ImportVue.de(service.importDonnees(id));
    }

    @GetMapping("/{id:[0-9a-f-]{36}}/rapport.csv")
    @Operation(summary = "Rapport d'erreurs (CSV)", description = "Ouvrable dans Excel : ligne ; colonne ; valeur ; motif.")
    ResponseEntity<byte[]> rapport(@PathVariable UUID id) {
        var i = service.importDonnees(id);
        var lignes = i.erreurs().stream()
                .map(e -> String.join(";", String.valueOf(e.ligne()), csv(e.colonne()), csv(e.valeur()), csv(e.message())))
                .collect(Collectors.joining("\r\n"));
        return fichierCsv("rapport-import-" + i.type() + ".csv", "ligne;colonne;valeur;motif\r\n" + lignes + "\r\n");
    }

    @GetMapping("/modeles/{type}.csv")
    @Operation(summary = "Modèle de fichier", description = "Colonnes attendues, champs personnalisés compris.")
    ResponseEntity<byte[]> modele(@PathVariable String type) {
        return fichierCsv("modele-" + type + ".csv", String.join(";", service.colonnes(type)) + "\r\n");
    }

    private static ResponseEntity<byte[]> fichierCsv(String nom, String contenu) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("﻿" + contenu).getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur.contains(";") || valeur.contains("\"") || valeur.contains("\n") ? "\"" + valeur.replace("\"", "\"\"") + "\"" : valeur;
    }
}
