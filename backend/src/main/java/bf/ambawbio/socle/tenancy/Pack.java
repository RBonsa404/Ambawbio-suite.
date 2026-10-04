package bf.ambawbio.socle.tenancy;

import java.util.List;

/**
 * Offres d'abonnement. Modules disponibles par pack : valeurs par défaut à confirmer (docs/QUESTIONS.md, Q-16).
 * RG-16 / INV-15 : plusieurs sociétés uniquement avec le pack Enterprise.
 */
public enum Pack {
    ESSENTIEL(List.of("pos", "ventes", "facturation", "paiement", "stock")),
    BUSINESS(List.of("pos", "ventes", "facturation", "paiement", "stock", "achats", "comptabilite")),
    ENTERPRISE(List.of("pos", "ventes", "facturation", "paiement", "stock", "achats", "comptabilite")),
    INSTITUTION(List.of("ventes", "facturation", "paiement", "stock", "achats", "comptabilite"));

    private final List<String> modules;

    Pack(List<String> modules) {
        this.modules = modules;
    }

    public List<String> modulesDisponibles() {
        return modules;
    }

    public boolean autoriseMultiSocietes() {
        return this == ENTERPRISE;
    }
}
