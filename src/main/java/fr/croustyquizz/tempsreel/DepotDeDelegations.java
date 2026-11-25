package fr.croustyquizz.tempsreel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import fr.croustyquizz.manche.Pouvoir;

/** Délégations conservées avec la Soirée (#67), une ligne par Joueur délégué. */
@Repository
class DepotDeDelegations {
	private static final String SOIREE = "soiree";
	private final JdbcClient jdbc;

	DepotDeDelegations(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Pouvoirs du Joueur dans cette Soirée, vide sans Délégation. */
	Set<Pouvoir> pouvoirs(UUID soireeId, UUID joueurId) {
		return jdbc.sql("""
				select file_attente, musique_attente, photos from tempsreel_delegation
				where soiree_id = :soiree and joueur_id = :joueur
				""").param(SOIREE, soireeId).param("joueur", joueurId)
				.query((ligne, numero) -> lire(ligne)).optional().orElse(EnumSet.noneOf(Pouvoir.class));
	}

	/** Délégations de la Soirée, par Joueur, de la moins récemment modifiée à la plus récente. */
	Map<UUID, Set<Pouvoir>> deLaSoiree(UUID soireeId) {
		Map<UUID, Set<Pouvoir>> delegations = new LinkedHashMap<>();
		jdbc.sql("""
				select joueur_id, file_attente, musique_attente, photos from tempsreel_delegation
				where soiree_id = :soiree order by modifiee_le, joueur_id
				""").param(SOIREE, soireeId)
				.query(ligne -> { delegations.put(ligne.getObject("joueur_id", UUID.class), lire(ligne)); });
		return delegations;
	}

	void enregistrer(UUID soireeId, UUID joueurId, Set<Pouvoir> pouvoirs, Instant maintenant) {
		jdbc.sql("""
				insert into tempsreel_delegation (joueur_id, soiree_id, file_attente, musique_attente, photos, modifiee_le)
				    values (:joueur, :soiree, :file, :musique, :photos, :maintenant)
				on conflict (joueur_id) do update
				    set file_attente = excluded.file_attente, musique_attente = excluded.musique_attente,
				        photos = excluded.photos, modifiee_le = excluded.modifiee_le
				""").param("joueur", joueurId).param(SOIREE, soireeId)
				.param("file", pouvoirs.contains(Pouvoir.FILE_ATTENTE))
				.param("musique", pouvoirs.contains(Pouvoir.MUSIQUE_ATTENTE))
				.param("photos", pouvoirs.contains(Pouvoir.PHOTOS))
				.param("maintenant", Timestamp.from(maintenant)).update();
	}

	boolean retirer(UUID soireeId, UUID joueurId) {
		return jdbc.sql("delete from tempsreel_delegation where soiree_id = :soiree and joueur_id = :joueur")
				.param(SOIREE, soireeId).param("joueur", joueurId).update() > 0;
	}

	void effacer(UUID soireeId) {
		jdbc.sql("delete from tempsreel_delegation where soiree_id = :soiree").param(SOIREE, soireeId).update();
	}

	private static Set<Pouvoir> lire(ResultSet ligne) throws SQLException {
		Set<Pouvoir> pouvoirs = EnumSet.noneOf(Pouvoir.class);
		if (ligne.getBoolean("file_attente")) { pouvoirs.add(Pouvoir.FILE_ATTENTE); }
		if (ligne.getBoolean("musique_attente")) { pouvoirs.add(Pouvoir.MUSIQUE_ATTENTE); }
		if (ligne.getBoolean("photos")) { pouvoirs.add(Pouvoir.PHOTOS); }
		return pouvoirs;
	}
}
