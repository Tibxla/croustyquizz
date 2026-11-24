package fr.croustyquizz.karaoke;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Suggestions et favoris de la Soirée, écrits dans la transaction du service du Karaoké. */
@Repository
public class CatalogueKaraokeSql {
    private final JdbcClient sql;

    public CatalogueKaraokeSql(DataSource source) { sql = JdbcClient.create(source); }

    boolean ajouterDemande(UUID soireeId, UUID joueurId, CorpsKaraoke.SuggestionChanson corps) {
        String titre = CorpsKaraoke.nettoyerSuggestion(corps.titre());
        String artiste = CorpsKaraoke.nettoyerSuggestion(corps.artiste());
        String titreNormalise = CorpsKaraoke.normaliserSuggestion(titre);
        String artisteNormalise = CorpsKaraoke.normaliserSuggestion(artiste);
        sql.sql("""
                INSERT INTO karaoke_suggestion_chanson
                    (id, soiree_id, titre, artiste, titre_normalise, artiste_normalise)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (soiree_id, titre_normalise, artiste_normalise) DO NOTHING
                """).params(UUID.randomUUID(), soireeId, titre, artiste, titreNormalise, artisteNormalise).update();
        return sql.sql("""
                INSERT INTO karaoke_demande_chanson (suggestion_id, joueur_id)
                SELECT id, ? FROM karaoke_suggestion_chanson
                WHERE soiree_id = ? AND titre_normalise = ? AND artiste_normalise = ?
                ON CONFLICT DO NOTHING
                """).params(joueurId, soireeId, titreNormalise, artisteNormalise).update() > 0;
    }

    List<VuesKaraoke.SuggestionChansonConsole> suggestions(UUID soireeId) {
        return sql.sql("""
                SELECT s.id, s.titre, s.artiste, count(*) AS nombre_joueurs
                FROM karaoke_suggestion_chanson s
                JOIN karaoke_demande_chanson d ON d.suggestion_id = s.id
                WHERE s.soiree_id = ?
                GROUP BY s.id
                ORDER BY nombre_joueurs DESC, s.titre_normalise, s.artiste_normalise, s.id
                """).param(soireeId)
                .query((ligne, index) -> new VuesKaraoke.SuggestionChansonConsole(ligne.getObject("id", UUID.class),
                        ligne.getString("titre"), ligne.getString("artiste"), ligne.getLong("nombre_joueurs")))
                .list();
    }

    Optional<Long> compterDemandes(UUID soireeId, UUID suggestionId) {
        return sql.sql("""
                SELECT count(d.joueur_id)
                FROM karaoke_suggestion_chanson s
                LEFT JOIN karaoke_demande_chanson d ON d.suggestion_id = s.id
                WHERE s.soiree_id = ? AND s.id = ?
                GROUP BY s.id
                """).params(soireeId, suggestionId).query(Long.class).optional();
    }

    void retirerSuggestion(UUID soireeId, UUID suggestionId) {
        sql.sql("DELETE FROM karaoke_suggestion_chanson WHERE soiree_id = ? AND id = ?")
                .params(soireeId, suggestionId).update();
    }

    List<String> favoris(UUID soireeId) {
        return sql.sql("SELECT chanson_id FROM karaoke_favori_chanson WHERE soiree_id = ? ORDER BY chanson_id")
                .param(soireeId).query(String.class).list();
    }

    boolean definirFavori(UUID soireeId, String chansonId, boolean favorite) {
        String requete = favorite
                ? "INSERT INTO karaoke_favori_chanson (soiree_id, chanson_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
                : "DELETE FROM karaoke_favori_chanson WHERE soiree_id = ? AND chanson_id = ?";
        return sql.sql(requete).params(soireeId, chansonId).update() > 0;
    }
}
