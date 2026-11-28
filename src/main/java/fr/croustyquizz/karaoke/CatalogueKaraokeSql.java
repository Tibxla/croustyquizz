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

    /** Bornes des groupes ouverts, vérifiées sous le verrou de Soirée avant toute écriture. */
    static final int GROUPES_PAR_JOUEUR_MAX = 5;
    static final int GROUPES_PAR_SOIREE_MAX = 50;

    enum Demande { AJOUTEE, DEJA_COMPTEE, LIMITE_JOUEUR, LIMITE_SOIREE }

    Demande ajouterDemande(UUID soireeId, UUID joueurId, CorpsKaraoke.SuggestionChanson corps) {
        String titre = CorpsKaraoke.nettoyerSuggestion(corps.titre());
        String artiste = CorpsKaraoke.nettoyerSuggestion(corps.artiste());
        String titreNormalise = CorpsKaraoke.normaliserSuggestion(titre);
        String artisteNormalise = CorpsKaraoke.normaliserSuggestion(artiste);
        Optional<UUID> groupe = sql.sql("""
                SELECT id FROM karaoke_suggestion_chanson
                WHERE soiree_id = ? AND titre_normalise = ? AND artiste_normalise = ?
                """).params(soireeId, titreNormalise, artisteNormalise).query(UUID.class).optional();
        if (groupe.isPresent() && sql.sql("SELECT count(*) FROM karaoke_demande_chanson WHERE suggestion_id = ? AND joueur_id = ?")
                .params(groupe.get(), joueurId).query(Long.class).single() > 0) {
            return Demande.DEJA_COMPTEE;
        }
        long duJoueur = sql.sql("""
                SELECT count(*) FROM karaoke_demande_chanson d
                JOIN karaoke_suggestion_chanson s ON s.id = d.suggestion_id
                WHERE s.soiree_id = ? AND d.joueur_id = ?
                """).params(soireeId, joueurId).query(Long.class).single();
        if (duJoueur >= GROUPES_PAR_JOUEUR_MAX) { return Demande.LIMITE_JOUEUR; }
        UUID id;
        if (groupe.isPresent()) {
            id = groupe.get();
        } else {
            long groupes = sql.sql("SELECT count(*) FROM karaoke_suggestion_chanson WHERE soiree_id = ?")
                    .param(soireeId).query(Long.class).single();
            if (groupes >= GROUPES_PAR_SOIREE_MAX) { return Demande.LIMITE_SOIREE; }
            id = UUID.randomUUID();
            sql.sql("""
                    INSERT INTO karaoke_suggestion_chanson
                        (id, soiree_id, titre, artiste, titre_normalise, artiste_normalise)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """).params(id, soireeId, titre, artiste, titreNormalise, artisteNormalise).update();
        }
        sql.sql("INSERT INTO karaoke_demande_chanson (suggestion_id, joueur_id) VALUES (?, ?)").params(id, joueurId).update();
        return Demande.AJOUTEE;
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
