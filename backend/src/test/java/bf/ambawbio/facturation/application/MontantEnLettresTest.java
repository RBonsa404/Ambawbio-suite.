package bf.ambawbio.facturation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Montant en lettres du gabarit D-01 (règles classiques, avant 1990). */
class MontantEnLettresTest {

    @Test
    void reglesDuFrancais() {
        assertThat(MontantEnLettres.francsCfa(1_242_800)).isEqualTo("Un million deux cent quarante-deux mille huit cents francs CFA");
        assertThat(MontantEnLettres.enLettres(71)).isEqualTo("soixante et onze");
        assertThat(MontantEnLettres.enLettres(80)).isEqualTo("quatre-vingts");
        assertThat(MontantEnLettres.enLettres(81)).isEqualTo("quatre-vingt-un");
        assertThat(MontantEnLettres.enLettres(91)).isEqualTo("quatre-vingt-onze");
        assertThat(MontantEnLettres.enLettres(200)).isEqualTo("deux cents");
        assertThat(MontantEnLettres.enLettres(200_000)).isEqualTo("deux cent mille");
        assertThat(MontantEnLettres.enLettres(80_000)).isEqualTo("quatre-vingt mille");
        assertThat(MontantEnLettres.enLettres(1000)).isEqualTo("mille");
        assertThat(MontantEnLettres.enLettres(21)).isEqualTo("vingt et un");
        assertThat(MontantEnLettres.enLettres(2_000_000_000L)).isEqualTo("deux milliards");
        assertThat(MontantEnLettres.francsCfa(55_000)).isEqualTo("Cinquante-cinq mille francs CFA");
    }
}
