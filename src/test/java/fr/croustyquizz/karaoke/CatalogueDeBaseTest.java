package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

/** Le catalogue livré et ses refus, sans Spring sauf pour le refus de démarrer. */
class CatalogueDeBaseTest {
    static final String ESSAI = "classpath:bibliotheque-essai/";
    private static final String CHANSON_ID = IdentifiantsChanson.encoderBibliotheque(1);
    private final JsonMapper json = JsonMapper.builder().build();
    @TempDir Path dossier;

    static CatalogueDeBase essai() {
        return CatalogueDeBase.charger(new DefaultResourceLoader(), ESSAI, JsonMapper.builder().build());
    }

    @Test
    void leCatalogueLivreAvecLApplicationEstValide() {
        var catalogue = CatalogueDeBase.charger(new DefaultResourceLoader(), "classpath:static/bibliotheque/", json);
        assertThat(catalogue.entrees()).allSatisfy(morceau ->
                assertThat(CatalogueDeBase.LICENCES_ACCEPTEES).containsKey(morceau.licence()));
        // Sans dossier, l'Écran de salle a toujours une Musique d'attente et des Chansons à proposer.
        assertThat(catalogue.entrees()).extracting(EntreeDeBibliotheque::type)
                .contains(TypeDEntree.ATTENTE, TypeDEntree.CHANSON);
        assertThat(catalogue.chansons()).allSatisfy(contenu -> assertThat(contenu.lignes()).isNotEmpty());
    }

    @Test
    void leCatalogueDEssaiDonneSesMorceauxSesChansonsEtLeurCredit() {
        var catalogue = essai();
        assertThat(catalogue.entrees()).extracting(EntreeDeBibliotheque::type)
                .containsExactly(TypeDEntree.ATTENTE, TypeDEntree.CHANSON);
        var contenu = catalogue.contenu(1).orElseThrow();
        assertThat(contenu.chanson()).isEqualTo(new Chanson(CHANSON_ID, 1, "La lanterne", "Les Lucioles", null, 4000L,
                EtatParoles.SYNCHRONISEES));
        assertThat(contenu.lignes()).containsExactly(new LigneParoles(500, "Une lanterne au bout du quai"),
                new LigneParoles(1500, "Éclaire la nuit qui s’en va"), new LigneParoles(2500, "Chante avec nous"));
        assertThat(catalogue.bibliotheque(CHANSON_ID)).contains(new ChansonDeBibliotheque(
                "/bibliotheque/chansons/les-lucioles-la-lanterne.wav", 32044, "CC BY-SA 4.0",
                URI.create("https://creativecommons.org/licenses/by-sa/4.0/deed.fr"), URI.create("https://example.org/la-lanterne")));
        assertThat(catalogue.bibliotheque(IdentifiantsChanson.encoder(1))).isEmpty();
        assertThat(catalogue.contenu(2)).isEmpty();
    }

    static Stream<Arguments> cataloguesInvalides() {
        return Stream.of(
                Arguments.of("licence NC refusée", (UnaryOperator<String>) t -> t
                        .replace("\"CC-BY-4.0\": {", "\"CC-BY-NC-4.0\": {").replace("\"licence\": \"CC-BY-4.0\"", "\"licence\": \"CC-BY-NC-4.0\"")
                        .replace("licenses/by/4.0", "licenses/by-nc/4.0"), "licence"),
                Arguments.of("licence ND refusée", (UnaryOperator<String>) t -> t
                        .replace("\"CC-BY-SA-4.0\": {", "\"CC-BY-ND-4.0\": {").replace("\"licence\": \"CC-BY-SA-4.0\"", "\"licence\": \"CC-BY-ND-4.0\"")
                        .replace("licenses/by-sa/4.0", "licenses/by-nd/4.0"), "licence"),
                Arguments.of("lien de licence qui n'est pas l'officiel", (UnaryOperator<String>) t -> t
                        .replace("https://creativecommons.org/licenses/by/4.0/deed.fr", "https://example.org/cc-by"), "licence"),
                Arguments.of("licence non déclarée", (UnaryOperator<String>) t -> t
                        .replace("\"licence\": \"CC-BY-4.0\"", "\"licence\": \"CC0-1.0\""), "licence"),
                Arguments.of("fichier absent", (UnaryOperator<String>) t -> t
                        .replace("attente/essai-attente.wav", "attente/absent.wav"), "absent"),
                Arguments.of("taille fausse", (UnaryOperator<String>) t -> t.replace("\"tailleOctets\": 24044", "\"tailleOctets\": 24043"),
                        "taille"),
                Arguments.of("LRC hors de la durée", (UnaryOperator<String>) t -> t.replace("\"dureeMs\": 4000", "\"dureeMs\": 2000"),
                        "ligne"),
                Arguments.of("LRC absent", (UnaryOperator<String>) t -> t
                        .replace("chansons/les-lucioles-la-lanterne.lrc", "chansons/absent.lrc"), "absent"),
                Arguments.of("chemin qui sort du dossier", (UnaryOperator<String>) t -> t
                        .replace("attente/essai-attente.wav", "attente/../chansons/les-lucioles-la-lanterne.wav"), "fichier"),
                Arguments.of("fichier rangé sous un autre type", (UnaryOperator<String>) t -> t
                        .replace("\"type\": \"attente\"", "\"type\": \"chanson\", \"numero\": 2, \"lrc\": \"chansons/les-lucioles-la-lanterne.lrc\""),
                        "fichier"),
                Arguments.of("numéro de Chanson manquant", (UnaryOperator<String>) t -> t.replace("\"numero\": 1,", ""), "numero"),
                Arguments.of("champ inconnu", (UnaryOperator<String>) t -> t.replace("\"type\": \"attente\"",
                        "\"type\": \"attente\", \"inconnu\": true"), "inconnu"),
                Arguments.of("Instrumentale sans tempo", (UnaryOperator<String>) t -> t.replace("\"type\": \"attente\"",
                        "\"type\": \"instrumentale\"").replace("attente/essai-attente.wav", "instrumentales/essai-attente.wav"), "tempo"),
                Arguments.of("type inconnu", (UnaryOperator<String>) t -> t.replace("\"type\": \"attente\"", "\"type\": \"jingle\""), "type"),
                Arguments.of("JSON illisible", (UnaryOperator<String>) t -> t + "}", "catalogue"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cataloguesInvalides")
    void unCatalogueInvalideEchoueAuChargement(String cas, UnaryOperator<String> alteration, String motif) throws IOException {
        copierEssai(alteration);
        assertThatExceptionOfType(CatalogueDeBaseInvalide.class)
                .isThrownBy(() -> CatalogueDeBase.charger(new DefaultResourceLoader(), dossier.toUri().toString(), json))
                .withMessageContaining(motif);
    }

    @Test
    void uneInstrumentaleSeDeclareAvecSonTempoEtSonDepart() throws IOException {
        copierEssai(t -> t.replace("\"type\": \"attente\"", "\"type\": \"instrumentale\", \"tempoBpm\": 92, \"departBeatMs\": 250")
                .replace("attente/essai-attente.wav", "instrumentales/essai-attente.wav"));
        Files.createDirectories(dossier.resolve("instrumentales"));
        Files.move(dossier.resolve("attente/essai-attente.wav"), dossier.resolve("instrumentales/essai-attente.wav"));
        var catalogue = CatalogueDeBase.charger(new DefaultResourceLoader(), dossier.toUri().toString(), json);
        assertThat(catalogue.entrees().getFirst()).satisfies(m -> {
            assertThat(m.type()).isEqualTo(TypeDEntree.INSTRUMENTALE);
            assertThat(m.tempoBpm()).isEqualTo(92);
            assertThat(m.departBeatMs()).isEqualTo(250L);
        });
    }

    @Test
    void lApplicationRefuseDeDemarrerSurUnCatalogueInvalide() throws IOException {
        copierEssai(t -> t.replace("attente/essai-attente.wav", "attente/absent.wav"));
        new ApplicationContextRunner().withUserConfiguration(ConfigurationBibliotheque.class)
                .withBean(JsonMapper.class, () -> json)
                .withPropertyValues("croustyquizz.bibliotheque.racine=" + dossier.toUri())
                .run(contexte -> assertThat(contexte).hasFailed().getFailure()
                        .hasStackTraceContaining(CatalogueDeBaseInvalide.class.getName()).hasStackTraceContaining("fichier absent"));
        new ApplicationContextRunner().withUserConfiguration(ConfigurationBibliotheque.class)
                .withBean(JsonMapper.class, () -> json)
                .run(contexte -> assertThat(contexte).hasNotFailed().hasSingleBean(CatalogueDeBase.class));
    }

    private void copierEssai(UnaryOperator<String> alteration) throws IOException {
        Path essai = Path.of("src/test/resources/bibliotheque-essai");
        try (var fichiers = Files.walk(essai)) {
            for (Path source : (Iterable<Path>) fichiers::iterator) {
                Path cible = dossier.resolve(essai.relativize(source).toString());
                if (Files.isDirectory(source)) { Files.createDirectories(cible); }
                else { Files.copy(source, cible); }
            }
        }
        Path catalogue = dossier.resolve("catalogue.json");
        String texte = Files.readString(catalogue, StandardCharsets.UTF_8);
        String altere = alteration.apply(texte);
        if (altere.equals(texte)) { throw new UncheckedIOException(new IOException("Altération sans effet")); }
        Files.writeString(catalogue, altere, StandardCharsets.UTF_8);
    }
}
