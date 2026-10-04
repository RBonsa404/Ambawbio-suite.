package bf.ambawbio.facturation.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import bf.ambawbio.facturation.domaine.DocumentFiscal;

/**
 * PDF A4 des factures et avoirs (gabarits D-01 et D-02, docs/design/gabarits) : HTML rendu en PDF (D-35), polices
 * de la charte embarquées, QR code de certification (ZXing). Bloc de certification selon l'état : certifiée,
 * en attente (zone « QR à venir »), simulée (filigrane « SANS VALEUR FISCALE »), rejetée ; brouillon en filigrane.
 */
@Component
class GenerateurPdf {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Africa/Ouagadougou"));
    private static final String[][] POLICES = {
        {"documents-titre.ttf", "Titre", "400"}, {"documents-sans-regulier.ttf", "Sans", "400"}, {"documents-sans-gras.ttf", "Sans", "700"},
        {"documents-mono.ttf", "Mono", "400"}};

    byte[] generer(DocumentFiscal d, String numeroOrigine) {
        var sortie = new ByteArrayOutputStream();
        try {
            var builder = new PdfRendererBuilder().useFastMode().withHtmlContent(html(d, numeroOrigine), null).toStream(sortie);
            for (var p : POLICES) {
                builder.useFont(() -> police(p[0]), p[1], Integer.parseInt(p[2]), PdfRendererBuilder.FontStyle.NORMAL, true);
            }
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sortie.toByteArray();
    }

    private static InputStream police(String fichier) {
        return GenerateurPdf.class.getResourceAsStream("/documents/polices/" + fichier);
    }

    String html(DocumentFiscal d, String numeroOrigine) {
        var e = d.emetteur();
        var c = d.client();
        boolean avoir = d.type() == DocumentFiscal.Type.AVOIR;
        int signe = avoir ? -1 : 1;
        var h = new StringBuilder(8000);
        h.append("""
                <html><head><meta charset="utf-8"/><style>
                @page { size: A4; margin: 13mm 13mm 18mm 13mm;
                  @bottom-left { content: element(pied); } }
                body { font-family: Sans; font-size: 9pt; color: #1E1B18; }
                .titre { font-family: Titre; font-size: 22pt; text-transform: uppercase; letter-spacing: 0.5pt; }
                .mono { font-family: Mono; }
                .gris { color: #5D5650; }
                table { border-collapse: collapse; width: 100%; }
                .lignes th { text-align: left; font-size: 7.5pt; text-transform: uppercase; border-bottom: 2px solid #1E1B18; padding: 4pt 3pt; }
                .lignes td { border-bottom: 1px solid #D8D3CE; padding: 4pt 3pt; vertical-align: top; }
                .n { text-align: right; white-space: nowrap; }
                .cadre { border: 1px solid #D8D3CE; padding: 6pt 8pt; }
                .ttc td { border-top: 3px solid #1E1B18; font-family: Titre; font-size: 14pt; padding-top: 5pt; }
                .fec { margin-top: 10pt; padding: 8pt; }
                .certifiee { border: 2px solid #1B4332; }
                .attente { border: 2px solid #503D01; background: #FFF4DC; }
                .simulee { border: 2px solid #96252D; }
                .rejetee { border: 2px solid #96252D; background: #FDECEC; }
                .qr-attente { width: 30mm; height: 30mm; border: 1px dashed #503D01; text-align: center; font-size: 7pt; color: #503D01; }
                .filigrane { position: fixed; top: 110mm; left: 0; width: 184mm; text-align: center; font-family: Titre; font-size: 54pt;
                  color: #F1E2E3; z-index: -1; transform: rotate(-30deg); }
                #pied { position: running(pied); font-size: 7pt; color: #5D5650; border-top: 3px solid #1E1B18; padding-top: 3pt; width: 184mm; }
                </style></head><body>
                """);
        h.append("<div id=\"pied\">").append(t(e.get("raisonSociale"))).append(" · IFU ").append(t(e.get("ifu")))
                .append(" · Édité avec Ambawbio Suite</div>");
        if (d.statut() == DocumentFiscal.Statut.BROUILLON) {
            h.append("<div class=\"filigrane\">BROUILLON</div>");
        } else if (d.fecSimulee()) {
            h.append("<div class=\"filigrane\">SANS VALEUR FISCALE</div>");
        }
        // En-tête : émetteur à gauche, type et numéro à droite
        h.append("<table><tr><td style=\"width:55%;vertical-align:top\"><div style=\"font-size:12pt;font-weight:700\">")
                .append(t(e.get("raisonSociale"))).append("</div><div>").append(t(e.get("adresse")))
                .append(e.get("ville") == null ? "" : ", " + t(e.get("ville")))
                .append("</div><div>").append(t(e.get("telephone"))).append(e.get("courriel") == null ? "" : " · " + t(e.get("courriel"))).append("</div>")
                .append("<div>IFU <span class=\"mono\">").append(t(e.get("ifu"))).append("</span>")
                .append(e.get("rccm") == null ? "" : " · RCCM <span class=\"mono\">" + t(e.get("rccm")) + "</span>")
                .append(e.get("regimeFiscal") == null ? "" : " · " + t(e.get("regimeFiscal"))).append("</div></td>")
                .append("<td style=\"vertical-align:top;text-align:right\"><div class=\"titre\">").append(avoir ? "Avoir" : "Facture").append("</div>")
                .append("<div class=\"mono\" style=\"font-size:11pt\">").append(t(d.numero() == null ? "BROUILLON" : d.numero())).append("</div>")
                .append("<div>Date : ").append(d.dateEmission() == null ? "—" : JOUR.format(d.dateEmission())).append("</div>");
        if (!avoir && d.dateEcheance() != null) {
            h.append("<div>Échéance : ").append(JOUR.format(d.dateEcheance())).append("</div>");
        }
        h.append("</td></tr></table>");
        // Client
        h.append("<div class=\"cadre\" style=\"margin-top:10pt;width:55%;margin-left:45%\"><div class=\"gris\" style=\"font-size:7pt\">CLIENT</div>")
                .append("<div style=\"font-weight:700\">").append(t(c.nom())).append("</div><div>").append(t(c.adresse())).append("</div>");
        if (c.ifu() != null) {
            h.append("<div>IFU <span class=\"mono\">").append(t(c.ifu())).append("</span>")
                    .append(c.rccm() == null ? "" : " · RCCM <span class=\"mono\">" + t(c.rccm()) + "</span>").append("</div>");
        }
        if (c.regime() != null) {
            h.append("<div>").append(t(c.regime())).append("</div>");
        }
        h.append("</div>");
        if (avoir) {
            h.append("<div class=\"cadre\" style=\"margin-top:8pt;background:#F5F1EA\">Avoir sur la facture <span class=\"mono\">").append(t(numeroOrigine))
                    .append("</span>").append(d.motif() == null ? "" : " — motif : " + t(d.motif())).append("</div>");
        }
        // Lignes
        h.append("<table class=\"lignes\" style=\"margin-top:12pt\"><tr><th>Désignation</th><th class=\"n\">Quantité</th><th>Unité</th>")
                .append("<th class=\"n\">P.U. HT</th><th class=\"n\">TVA</th><th class=\"n\">Montant HT</th></tr>");
        Map<String, long[]> parTaux = new LinkedHashMap<>();
        for (var l : d.lignes()) {
            long puHt = l.prixTtc() ? BigDecimal.valueOf(l.prixUnitaire()).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(100).add(l.taux()), 0, RoundingMode.HALF_UP).longValue() : l.prixUnitaire();
            h.append("<tr><td>").append(t(l.designation())).append(l.remise() > 0 ? "<br/><span class=\"gris\">remise " + nombre(l.remise()) + " F</span>" : "")
                    .append("</td><td class=\"n\">").append(quantite(l.quantite())).append("</td><td>").append(t(l.unite())).append("</td><td class=\"n\">")
                    .append(nombre(puHt)).append("</td><td class=\"n\">").append(taux(l.taux())).append("</td><td class=\"n\">")
                    .append(nombre(signe * l.montantHt())).append("</td></tr>");
            var cle = taux(l.taux());
            var cumul = parTaux.computeIfAbsent(cle, k -> new long[2]);
            cumul[0] += l.montantHt();
            cumul[1] += l.montantTaxe();
        }
        h.append("</table>");
        // Taxes par taux et totaux
        h.append("<table style=\"margin-top:10pt\"><tr><td style=\"width:50%;vertical-align:top\"><table class=\"lignes\"><tr><th>Taxe</th>")
                .append("<th class=\"n\">Base HT</th><th class=\"n\">Montant</th></tr>");
        parTaux.forEach((k, v) -> h.append("<tr><td>TVA ").append(k).append("</td><td class=\"n\">").append(nombre(signe * v[0]))
                .append("</td><td class=\"n\">").append(nombre(signe * v[1])).append("</td></tr>"));
        h.append("</table></td><td style=\"vertical-align:top;padding-left:20pt\"><table>")
                .append("<tr><td>Total HT</td><td class=\"n\">").append(nombre(signe * d.totalHt())).append(" F</td></tr>")
                .append("<tr><td>TVA</td><td class=\"n\">").append(nombre(signe * d.totalTaxes())).append(" F</td></tr>")
                .append("<tr class=\"ttc\"><td>Total TTC</td><td class=\"n\">").append(nombre(signe * d.totalTtc())).append(" FCFA</td></tr></table>")
                .append("</td></tr></table>");
        h.append("<p class=\"gris\">Arrêté").append(avoir ? " le présent avoir" : " la présente facture").append(" à la somme de : <strong>")
                .append(t(MontantEnLettres.francsCfa(d.totalTtc()))).append("</strong>.</p>");
        h.append(blocCertification(d));
        h.append("</body></html>");
        return h.toString();
    }

    private String blocCertification(DocumentFiscal d) {
        if (d.statut() == DocumentFiscal.Statut.BROUILLON) {
            return "<div class=\"fec cadre\">Brouillon : pièce non validée, sans numéro définitif.</div>";
        }
        var b = new StringBuilder();
        switch (d.fecStatut()) {
            case CERTIFIEE -> {
                b.append("<table class=\"fec ").append(d.fecSimulee() ? "simulee" : "certifiee").append("\"><tr><td style=\"width:34mm\">")
                        .append("<img style=\"width:30mm;height:30mm\" src=\"data:image/png;base64,").append(qr(d.fecCodeQr())).append("\"/></td><td>")
                        .append("<strong>").append(d.fecSimulee() ? "CERTIFICATION SIMULÉE – SANS VALEUR FISCALE" : "Facture électronique certifiée")
                        .append("</strong><br/>Identifiant : <span class=\"mono\">").append(t(d.fecIdentifiant())).append("</span><br/>Certifiée le ")
                        .append(HORODATAGE.format(d.fecHorodatage())).append("</td></tr></table>");
            }
            case REJETEE -> b.append("<div class=\"fec rejetee\"><strong>Certification refusée.</strong> ").append(t(d.fecMessage()))
                    .append(" Cette pièce doit être annulée par un avoir.</div>");
            default -> b.append("<table class=\"fec attente\"><tr><td style=\"width:34mm\"><div class=\"qr-attente\"><br/><br/>QR à venir</div></td>")
                    .append("<td><strong>En attente de certification.</strong> La pièce est transmise dès que le service de certification répond ; ")
                    .append("son identifiant et son QR code figureront sur la version certifiée.</td></tr></table>");
        }
        return b.toString();
    }

    static String qr(String contenu) {
        try {
            var matrice = new QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, 300, 300,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4, EncodeHintType.CHARACTER_SET, "UTF-8"));
            var image = new java.awt.image.BufferedImage(matrice.getWidth(), matrice.getHeight(), java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrice.getWidth(); x++) {
                for (int y = 0; y < matrice.getHeight(); y++) {
                    image.setRGB(x, y, matrice.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            var png = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", png);
            return Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (com.google.zxing.WriterException | IOException e) {
            throw new IllegalStateException("QR code impossible", e);
        }
    }

    private static String nombre(long valeur) {
        return NumberFormat.getIntegerInstance(Locale.FRANCE).format(valeur).replace(' ', ' ');
    }

    private static String quantite(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String taux(BigDecimal taux) {
        return (taux == null ? "0" : taux.stripTrailingZeros().toPlainString().replace('.', ',')) + " %";
    }

    private static String t(Object valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
