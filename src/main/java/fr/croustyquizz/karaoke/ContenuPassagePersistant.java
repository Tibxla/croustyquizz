package fr.croustyquizz.karaoke;

import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Contenu figé conservé même après la fin du Passage. */
@Entity
@Table(name = "karaoke_contenu_passage")
public class ContenuPassagePersistant {
    @Id private UUID passageId;
    private String chansonId;
    private long idLrclib;
    private String titre;
    private String artiste;
    private String album;
    private long dureeMs;
    @JdbcTypeCode(SqlTypes.JSON) private String lignesParoles;
    protected ContenuPassagePersistant() { }
    public ContenuPassagePersistant(UUID passageId, ContenuKaraoke contenu, JsonMapper json) {
        this.passageId = passageId;
        var chanson = contenu.chanson();
        chansonId = chanson.chansonId(); idLrclib = chanson.idLrclib(); titre = chanson.titre(); artiste = chanson.artiste();
        album = chanson.album(); dureeMs = chanson.dureeMs(); lignesParoles = json.writeValueAsString(contenu.lignes());
    }
    public UUID getPassageId() { return passageId; }
    public ContenuKaraoke restaurer(JsonMapper json) {
        return new ContenuKaraoke(new Chanson(chansonId, idLrclib, titre, artiste, album, dureeMs, EtatParoles.SYNCHRONISEES),
                json.readValue(lignesParoles, new TypeReference<List<LigneParoles>>() { }));
    }
}
