package bf.ambawbio;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Vérifie les frontières entre modules (R-02) et produit leur documentation. */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(AmbawbioApplication.class);

    @Test
    void lesFrontieresDesModulesSontRespectees() {
        modules.verify();
    }

    @Test
    void documenteLesModules() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
