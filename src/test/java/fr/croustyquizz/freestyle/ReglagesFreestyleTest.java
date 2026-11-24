package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.croustyquizz.manche.RefusDeCommande;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class ReglagesFreestyleTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID INSTRUMENTALE = UUID.fromString("74000000-0000-4000-8000-000000000004");

    private static ReglagesFreestyle lire(Map<String, Object> reglages) {
        return CorpsFreestyle.reglages(JSON.valueToTree(reglages), JSON);
    }

    private static Map<String, Object> reglages(Object mesuresParCouplet) {
        var valeurs = new java.util.LinkedHashMap<String, Object>(Map.of("theme", "VILLE", "schemaDeRimes", "ABAB",
                "difficulte", "TOUS", "nombreMesures", 8, "instrumentaleIds", List.of(INSTRUMENTALE)));
        if (mesuresParCouplet != null) { valeurs.put("mesuresParCouplet", mesuresParCouplet); }
        return valeurs;
    }

    @Test
    void lesCoupletsDurentQuatreMesuresParDefaut() {
        assertThat(lire(reglages(null)).mesuresParCouplet()).isEqualTo(4);
        assertThat(lire(reglages(2)).mesuresParCouplet()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"3", "16", "0"})
    void uneLongueurHorsDeDeuxQuatreOuHuitEstUneRequeteInvalide(int mesures) {
        assertThatThrownBy(() -> lire(reglages(mesures))).isInstanceOfSatisfying(RefusDeCommande.class,
                erreur -> assertThat(erreur.refus().code()).isEqualTo("REQUETE_INVALIDE"));
    }

    @Test
    void uneGrilleQuiNestPasUnMultipleDesCoupletsEstRefusee() {
        assertThat(ReglagesFreestyle.refusDesCouplets(12, 8)).hasValueSatisfying(refus -> {
            assertThat(refus.statutHttp()).isEqualTo(422);
            assertThat(refus.code()).isEqualTo("DONNEES_INVALIDES");
        });
        for (int mesures : new int[] {8, 16, 32}) {
            for (int couplet : new int[] {2, 4, 8}) {
                assertThat(ReglagesFreestyle.refusDesCouplets(mesures, couplet)).isEmpty();
            }
        }
    }
}
