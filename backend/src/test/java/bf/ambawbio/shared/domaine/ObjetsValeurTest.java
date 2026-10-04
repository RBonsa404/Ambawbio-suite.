package bf.ambawbio.shared.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

class ObjetsValeurTest {

    @Test
    void montantArrondiAuFrancDemiSuperieur() {
        assertThat(Montant.fcfa(1001).multiplier(new BigDecimal("0.18"))).isEqualTo(Montant.fcfa(180));
        assertThat(Montant.fcfa(25).multiplier(new BigDecimal("0.5"))).isEqualTo(Montant.fcfa(13));
        assertThat(Montant.fcfa(12_500).ajouter(Montant.fcfa(500)).soustraire(Montant.fcfa(1000))).isEqualTo(Montant.fcfa(12_000));
    }

    @Test
    void montantRefuseLeDepassement() {
        assertThatThrownBy(() -> Montant.fcfa(Long.MAX_VALUE).ajouter(Montant.fcfa(1))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void ifuNormaliseEtVerifie() {
        assertThat(Ifu.depuisSaisie(" 00012345 a ").valeur()).isEqualTo("00012345A");
        assertThatThrownBy(() -> Ifu.depuisSaisie("1234")).isInstanceOf(RegleMetierException.class)
                .extracting("code").isEqualTo("IFU_INVALIDE");
    }

    @Test
    void uuid7VersionneTriableEtUnique() throws InterruptedException {
        var premier = Uuid7.nouveau();
        Thread.sleep(2);
        var second = Uuid7.nouveau();
        assertThat(Uuid7.estUuid7(premier)).isTrue();
        assertThat(premier.toString().compareTo(second.toString())).isNegative();
        var vus = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(vus.add(Uuid7.nouveau())).isTrue();
        }
    }
}
