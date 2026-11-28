package fr.croustyquizz.contact;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class DepotContacts {
	record Lieu(UUID id, String nom) { }

	record Fiche(UUID id, String prenom, String nom, String numero, Instant consentiLe, String mention, Instant expireLe) {
		@Override
		public String toString() {
			return "Fiche[" + id + "]";
		}
	}

	private static final String CLE_LIEU = "lieu";
	private static final String CLE_MAINTENANT = "maintenant";
	private static final String COORDONNEES_ACTIVES = """
			select id, prenom, nom, numero, consenti_le, mention, expire_le
			from contact_du_lieu
			where lieu_id = :lieu and expire_le > :maintenant
			""";
	private final JdbcClient jdbc;

	DepotContacts(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Lieu> lieuDeLaSoiree(UUID soiree) {
		return jdbc.sql("""
				select l.id, l.nom from lieu l join soiree s on s.lieu_id = l.id where s.id = :soiree
				""").param("soiree", soiree)
				.query((rs, ligne) -> new Lieu(rs.getObject("id", UUID.class), rs.getString("nom"))).optional();
	}

	void verrouillerLieu(UUID lieu) {
		jdbc.sql("select id from lieu where id = :lieu for update").param(CLE_LIEU, lieu).query(UUID.class).single();
	}

	SecretsDesContacts secrets() {
		return new SecretsDesContacts(jdbc.sql("select cle from contact_secret").query(byte[].class).single());
	}

	boolean opposition(UUID lieu, byte[] empreinte) {
		return jdbc.sql("""
				select exists (select 1 from contact_opposition where lieu_id = :lieu and empreinte = :empreinte)
				""").param(CLE_LIEU, lieu).param("empreinte", empreinte).query(Boolean.class).single();
	}

	UUID enregistrer(UUID lieu, Fiche fiche) {
		// Une fiche expirée n’est jamais réutilisée par une Campagne déjà programmée.
		jdbc.sql("delete from contact_du_lieu where lieu_id = :lieu and numero = :numero and expire_le <= :maintenant")
				.param(CLE_LIEU, lieu).param("numero", fiche.numero()).param(CLE_MAINTENANT, date(fiche.consentiLe())).update();
		return jdbc.sql("""
				insert into contact_du_lieu (id, lieu_id, numero, prenom, nom, consenti_le, mention, expire_le)
				values (:id, :lieu, :numero, :prenom, :nom, :consenti, :mention, :expire)
				on conflict (lieu_id, numero) do update set prenom = excluded.prenom, nom = excluded.nom,
					consenti_le = excluded.consenti_le, mention = excluded.mention, expire_le = excluded.expire_le
				returning id
				""").param("id", fiche.id()).param(CLE_LIEU, lieu).param("numero", fiche.numero())
				.param("prenom", fiche.prenom()).param("nom", fiche.nom()).param("consenti", date(fiche.consentiLe()))
				.param("mention", fiche.mention()).param("expire", date(fiche.expireLe())).query(UUID.class).single();
	}

	List<Fiche> actifs(UUID lieu, Instant maintenant) {
		return jdbc.sql(COORDONNEES_ACTIVES + "order by nom, prenom, id").param(CLE_LIEU, lieu)
				.param(CLE_MAINTENANT, date(maintenant)).query((rs, ligne) -> lire(rs)).list();
	}

	/** Le verrou de la fiche reste pris jusqu’à la fin de la tentative d’envoi. */
	Optional<Fiche> actifPourEnvoi(UUID lieu, UUID id, Instant maintenant) {
		return jdbc.sql(COORDONNEES_ACTIVES + "and id = :id for update").param(CLE_LIEU, lieu).param("id", id)
				.param(CLE_MAINTENANT, date(maintenant)).query((rs, ligne) -> lire(rs)).optional();
	}

	Optional<UUID> lieuDuContact(UUID id) {
		return jdbc.sql("select lieu_id from contact_du_lieu where id = :id").param("id", id).query(UUID.class).optional();
	}

	Optional<String> retirer(UUID id, Instant maintenant) {
		return jdbc.sql("delete from contact_du_lieu where id = :id and expire_le > :maintenant returning numero")
				.param("id", id).param(CLE_MAINTENANT, date(maintenant)).query(String.class).optional();
	}

	void enregistrerOpposition(UUID id, UUID lieu, byte[] empreinte) {
		jdbc.sql("insert into contact_opposition (contact_id, lieu_id, empreinte) values (:id, :lieu, :empreinte)")
				.param("id", id).param(CLE_LIEU, lieu).param("empreinte", empreinte).update();
	}

	boolean dejaRetire(UUID id) {
		return jdbc.sql("select exists (select 1 from contact_opposition where contact_id = :id)")
				.param("id", id).query(Boolean.class).single();
	}

	void purger(Instant maintenant) {
		jdbc.sql("delete from contact_du_lieu where expire_le <= :maintenant")
				.param(CLE_MAINTENANT, date(maintenant)).update();
	}

	private static Fiche lire(ResultSet rs) throws SQLException {
		return new Fiche(rs.getObject("id", UUID.class), rs.getString("prenom"), rs.getString("nom"),
				rs.getString("numero"), rs.getObject("consenti_le", OffsetDateTime.class).toInstant(),
				rs.getString("mention"), rs.getObject("expire_le", OffsetDateTime.class).toInstant());
	}

	private static OffsetDateTime date(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
