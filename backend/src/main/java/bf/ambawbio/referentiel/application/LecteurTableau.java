package bf.ambawbio.referentiel.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import bf.ambawbio.shared.domaine.RegleMetierException;

/**
 * Lecture d'un fichier CSV (séparateur « ; », « , » ou tabulation détecté, guillemets RFC 4180, UTF-8 avec ou sans BOM)
 * ou Excel (.xlsx, première feuille). En-têtes normalisés : minuscules, sans accents, espaces remplacés par « _ ».
 */
final class LecteurTableau {

    /** Ligne lue : numéro dans le tableur (en-tête = 1) et valeurs par colonne normalisée. */
    record Ligne(int numero, Map<String, String> valeurs) {
        String valeur(String colonne) {
            var v = valeurs.get(colonne);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    static final int LIGNES_MAX = 20_000;

    private LecteurTableau() {
    }

    static List<Ligne> lire(InputStream flux, String nomFichier) throws IOException {
        var nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        if (nom.endsWith(".xlsx")) {
            return lireExcel(flux);
        }
        if (nom.endsWith(".csv") || nom.endsWith(".txt")) {
            return lireCsv(new String(flux.readAllBytes(), StandardCharsets.UTF_8));
        }
        throw new RegleMetierException("FORMAT_FICHIER", "Format non pris en charge : utilisez un fichier .csv (UTF-8) ou .xlsx.");
    }

    static String normaliserEntete(String entete) {
        var sansAccent = Normalizer.normalize(entete.replace("﻿", "").trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        var resultat = sansAccent.toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        // « champ.marque » garde son point ; le code du champ reste en minuscules.
        return resultat;
    }

    private static List<Ligne> lireExcel(InputStream flux) throws IOException {
        try (var classeur = new XSSFWorkbook(flux)) {
            var feuille = classeur.getSheetAt(0);
            var format = new DataFormatter(Locale.FRANCE);
            var entetes = new ArrayList<String>();
            var lignes = new ArrayList<Ligne>();
            for (Row ligne : feuille) {
                var cellules = new ArrayList<String>();
                for (int i = 0; i < Math.max(ligne.getLastCellNum(), entetes.size()); i++) {
                    var cellule = ligne.getCell(i);
                    cellules.add(cellule == null ? "" : format.formatCellValue(cellule));
                }
                if (entetes.isEmpty()) {
                    cellules.forEach(c -> entetes.add(normaliserEntete(c)));
                } else if (cellules.stream().anyMatch(c -> !c.isBlank())) {
                    lignes.add(new Ligne(ligne.getRowNum() + 1, associer(entetes, cellules)));
                }
                verifierTaille(lignes);
            }
            return lignes;
        }
    }

    static List<Ligne> lireCsv(String contenu) {
        var texte = contenu.startsWith("﻿") ? contenu.substring(1) : contenu;
        var premiere = texte.lines().findFirst().orElse("");
        char separateur = premiere.contains(";") ? ';' : premiere.contains("\t") ? '\t' : ',';
        var enregistrements = decouper(texte, separateur);
        if (enregistrements.isEmpty()) {
            return List.of();
        }
        var entetes = enregistrements.getFirst().cellules().stream().map(LecteurTableau::normaliserEntete).toList();
        var lignes = new ArrayList<Ligne>();
        for (var e : enregistrements.subList(1, enregistrements.size())) {
            if (e.cellules().stream().anyMatch(c -> !c.isBlank())) {
                lignes.add(new Ligne(e.numero(), associer(entetes, e.cellules())));
                verifierTaille(lignes);
            }
        }
        return lignes;
    }

    private record Enregistrement(int numero, List<String> cellules) {
    }

    /** Découpage RFC 4180 : guillemets doublés, retours à la ligne dans les champs entre guillemets. */
    private static List<Enregistrement> decouper(String texte, char separateur) {
        var resultat = new ArrayList<Enregistrement>();
        var cellules = new ArrayList<String>();
        var cellule = new StringBuilder();
        boolean entreGuillemets = false;
        int ligne = 1;
        int debut = 1;
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (entreGuillemets) {
                if (c == '"' && i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
                    cellule.append('"');
                    i++;
                } else if (c == '"') {
                    entreGuillemets = false;
                } else {
                    if (c == '\n') {
                        ligne++;
                    }
                    cellule.append(c);
                }
            } else if (c == '"') {
                entreGuillemets = true;
            } else if (c == separateur) {
                cellules.add(cellule.toString());
                cellule.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') {
                    i++;
                }
                cellules.add(cellule.toString());
                cellule.setLength(0);
                resultat.add(new Enregistrement(debut, List.copyOf(cellules)));
                cellules.clear();
                ligne++;
                debut = ligne;
            } else {
                cellule.append(c);
            }
        }
        if (cellule.length() > 0 || !cellules.isEmpty()) {
            cellules.add(cellule.toString());
            resultat.add(new Enregistrement(debut, List.copyOf(cellules)));
        }
        return resultat;
    }

    private static Map<String, String> associer(List<String> entetes, List<String> cellules) {
        var valeurs = new LinkedHashMap<String, String>();
        for (int i = 0; i < entetes.size(); i++) {
            valeurs.put(entetes.get(i), i < cellules.size() ? cellules.get(i) : "");
        }
        return valeurs;
    }

    private static void verifierTaille(List<Ligne> lignes) {
        if (lignes.size() > LIGNES_MAX) {
            throw new RegleMetierException("FICHIER_TROP_GRAND", "Un import est limité à " + LIGNES_MAX + " lignes. Découpez le fichier.");
        }
    }
}
