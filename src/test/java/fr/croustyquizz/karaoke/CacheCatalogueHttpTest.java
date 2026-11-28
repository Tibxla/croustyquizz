package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

class CacheCatalogueHttpTest {
    private static final String ROUTE = "/bibliotheque/catalogue.json";

    @EnableWebMvc
    static class Mvc { }

    @Test
    void leCatalogueSeRevalideParSonContenuMemeApresUnRetourDeVersion(@TempDir Path racine) throws Exception {
        Path essai = Path.of("src/test/resources/bibliotheque-essai");
        Files.createDirectory(racine.resolve("attente"));
        Files.createDirectory(racine.resolve("chansons"));
        for (String fichier : new String[] { "catalogue.json", "attente/essai-attente.wav",
                "chansons/les-lucioles-la-lanterne.wav", "chansons/les-lucioles-la-lanterne.lrc" }) {
            Files.copy(essai.resolve(fichier), racine.resolve(fichier));
        }
        Path catalogue = racine.resolve("catalogue.json");
        String versionA = Files.readString(catalogue);
        String versionB = versionA.replace("La lanterne", "La Lanterne");
        FileTime dateA = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(catalogue, dateA);

        new WebApplicationContextRunner().withUserConfiguration(Mvc.class, ConfigurationBibliotheque.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withPropertyValues("croustyquizz.bibliotheque.racine=" + racine.toUri())
                .run(contexte -> {
                    var http = MockMvcBuilders.webAppContextSetup(contexte.getSourceApplicationContext()).build();
                    var a = http.perform(get(ROUTE)).andExpect(status().isOk())
                            .andExpect(header().doesNotExist(HttpHeaders.LAST_MODIFIED))
                            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                            .andExpect(header().exists(HttpHeaders.ETAG)).andReturn().getResponse();
                    String etagA = a.getHeader(HttpHeaders.ETAG);

                    Files.writeString(catalogue, versionB);
                    Files.setLastModifiedTime(catalogue, dateA);
                    var b = http.perform(get(ROUTE).header(HttpHeaders.IF_NONE_MATCH, etagA))
                            .andExpect(status().isOk()).andExpect(content().string(versionB))
                            .andReturn().getResponse();
                    String etagB = b.getHeader(HttpHeaders.ETAG);
                    assertThat(etagB).isNotBlank().isNotEqualTo(etagA);

                    Files.writeString(catalogue, versionA);
                    Files.setLastModifiedTime(catalogue, dateA);
                    http.perform(get(ROUTE).header(HttpHeaders.IF_NONE_MATCH, etagB)
                                    .header(HttpHeaders.IF_MODIFIED_SINCE, "Fri, 01 Dec 2023 00:00:00 GMT"))
                            .andExpect(status().isOk()).andExpect(content().string(versionA))
                            .andExpect(header().string(HttpHeaders.ETAG, etagA));
                    http.perform(get(ROUTE).header(HttpHeaders.IF_NONE_MATCH, etagA))
                            .andExpect(status().isNotModified()).andExpect(content().string(""));
                });
    }
}
