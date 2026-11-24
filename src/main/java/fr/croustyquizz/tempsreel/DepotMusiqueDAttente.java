package fr.croustyquizz.tempsreel;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class DepotMusiqueDAttente {
	private final JdbcClient jdbc;

	DepotMusiqueDAttente(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	MusiqueDAttente lire(UUID soireeId) {
		return jdbc.sql("""
				select etat, morceaux_disponibles from tempsreel_musique_attente where soiree_id = :soiree
				""").param("soiree", soireeId)
				.query((ligne, numero) -> new MusiqueDAttente(MusiqueDAttente.Etat.valueOf(ligne.getString("etat")),
						ligne.getObject("morceaux_disponibles", Boolean.class)))
				.optional().orElse(MusiqueDAttente.PAR_DEFAUT);
	}

	void enregistrer(UUID soireeId, MusiqueDAttente musique) {
		jdbc.sql("""
				insert into tempsreel_musique_attente (soiree_id, etat, morceaux_disponibles)
				    values (:soiree, :etat, :morceaux)
				on conflict (soiree_id) do update
				    set etat = excluded.etat, morceaux_disponibles = excluded.morceaux_disponibles
				""").param("soiree", soireeId).param("etat", musique.etat().name())
				.param("morceaux", musique.morceauxDisponibles()).update();
	}
}
