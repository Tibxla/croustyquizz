package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifiantsChansonTest {
    @Test
    void conserveUnIdentifiantOpaqueStableEtReversible() {
        assertThat(IdentifiantsChanson.encoder(1)).isEqualTo("63726f75-7374-7900-516b-24e8a9d3705e");
        assertThat(IdentifiantsChanson.decoder("63726f75-7374-7900-516b-24e8a9d3705e"))
                .hasValue(1);
        assertThat(IdentifiantsChanson.decoder(IdentifiantsChanson.encoder(9007199254740991L)))
                .hasValue(9007199254740991L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 9007199254740992L, Long.MAX_VALUE, Long.MIN_VALUE})
    void refuseLesIdentitesHorsBornes(long id) {
        assertThatIllegalArgumentException().isThrownBy(() -> IdentifiantsChanson.encoder(id));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"inconnu", "63726F75-7374-7900-516B-24E8A9D3705E",
        "63726f75-7374-7900-516b-24e8a9d3705e ", "63726f75-7374-7900-516b-24e8a9d3705",
        "00000000-0000-0000-516b-24e8a9d3705e", "63726f75-7374-7900-516b-24e8a9d3705f",
        "63726f75-7374-7900-514b-24e8a9d3705f", "63726f75-7374-7900-ae94-db17562c8fa0"})
    void ignoreLesIdentifiantsEtrangersOuNonCanoniques(String id) {
        assertThat(IdentifiantsChanson.decoder(id)).isEmpty();
    }
}
