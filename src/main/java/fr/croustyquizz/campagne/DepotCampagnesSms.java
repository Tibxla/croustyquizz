package fr.croustyquizz.campagne;

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
class DepotCampagnesSms {
	private static final String LIEU = "lieu";
	private static final String CAMPAGNE = "campagne";
	private static final String CONTACT = "contact";
	private static final String ENVOI_PREVU = "envoi_prevu_le";

	private static final String COMPTEURS = """
			select c.*, count(*) filter (where e.statut = 'ENVOYE') as envoyes,
				count(*) filter (where e.statut = 'ECHOUE') as echoues
			from campagne_sms c left join campagne_sms_envoi e on e.campagne_id = c.id
			where c.lieu_id = :lieu
			""";

	record Plan(UUID id, UUID lieuId, String texte, Instant envoiPrevuLe, CampagneSms.Etat etat) { }

	record Echeance(UUID id, Instant envoiPrevuLe) { }

	private final JdbcClient jdbc;

	DepotCampagnesSms(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void verrouillerLieu(UUID lieuId) {
		jdbc.sql("select id from lieu where id = :lieu for no key update").param(LIEU, lieuId).query(UUID.class).single();
	}

	Optional<CampagneSms> parCle(UUID lieuId, UUID cle) {
		return jdbc.sql(COMPTEURS + "and c.cle_idempotence = :cle group by c.id")
				.param(LIEU, lieuId).param("cle", cle).query((ligne, numero) -> lireCampagne(ligne)).optional();
	}

	void creer(UUID lieuId, UUID cle, CampagneSms campagne, List<UUID> contacts) {
		jdbc.sql("""
				insert into campagne_sms (id, lieu_id, cle_idempotence, texte, demandee_le, envoi_prevu_le, etat)
				values (:campagne, :lieu, :cle, :texte, :demande, :prevision, 'PROGRAMMEE')
				""").param(CAMPAGNE, campagne.id()).param(LIEU, lieuId).param("cle", cle).param("texte", campagne.texte())
				.param("demande", avecFuseau(campagne.demandeeLe())).param("prevision", avecFuseau(campagne.envoiPrevuLe()))
				.update();
		jdbc.sql("""
				insert into campagne_sms_envoi (campagne_id, contact_id)
				select :campagne, unnest(cast(string_to_array(:contacts, ',') as uuid[]))
				""").param(CAMPAGNE, campagne.id()).param("contacts", String.join(",", contacts.stream().map(UUID::toString).toList()))
				.update();
	}

	List<CampagneSms> lister(UUID lieuId) {
		return jdbc.sql(COMPTEURS + "group by c.id order by c.demandee_le desc, c.id desc")
				.param(LIEU, lieuId).query((ligne, numero) -> lireCampagne(ligne)).list();
	}

	Optional<CampagneSms> consulter(UUID lieuId, UUID id) {
		return jdbc.sql(COMPTEURS + "and c.id = :campagne group by c.id")
				.param(LIEU, lieuId).param(CAMPAGNE, id).query((ligne, numero) -> lireCampagne(ligne)).optional();
	}

	List<CampagneSms.Envoi> envois(UUID id) {
		return jdbc.sql("""
				select contact_id, statut, envoye_le, motif, identifiant_prestataire
				from campagne_sms_envoi where campagne_id = :campagne and statut is not null
				order by envoye_le, contact_id
				""").param(CAMPAGNE, id).query((ligne, numero) -> new CampagneSms.Envoi(
				ligne.getObject("contact_id", UUID.class), ligne.getString("statut"), instant(ligne, "envoye_le"),
				ligne.getString("motif"), ligne.getString("identifiant_prestataire"))).list();
	}

	Optional<Echeance> prochaine() {
		return jdbc.sql("""
				select id, envoi_prevu_le from campagne_sms where etat <> 'TERMINEE'
				order by envoi_prevu_le, id limit 1
				""").query((ligne, numero) -> new Echeance(ligne.getObject("id", UUID.class),
				instant(ligne, ENVOI_PREVU))).optional();
	}

	Plan verrouiller(UUID id) {
		return jdbc.sql("select * from campagne_sms where id = :campagne for no key update")
				.param(CAMPAGNE, id).query((ligne, numero) -> new Plan(ligne.getObject("id", UUID.class),
						ligne.getObject("lieu_id", UUID.class), ligne.getString("texte"), instant(ligne, ENVOI_PREVU),
						CampagneSms.Etat.valueOf(ligne.getString("etat")))).single();
	}

	void reporter(UUID id, Instant prevision) {
		jdbc.sql("update campagne_sms set etat = 'PROGRAMMEE', envoi_prevu_le = :prevision where id = :campagne")
				.param(CAMPAGNE, id).param("prevision", avecFuseau(prevision)).update();
	}

	void changerEtat(UUID id, CampagneSms.Etat etat) {
		jdbc.sql("update campagne_sms set etat = :etat where id = :campagne")
				.param(CAMPAGNE, id).param("etat", etat.name()).update();
	}

	Optional<UUID> prochainContact(UUID id) {
		return jdbc.sql("""
				select contact_id from campagne_sms_envoi where campagne_id = :campagne and statut is null
				order by contact_id limit 1
				""").param(CAMPAGNE, id).query(UUID.class).optional();
	}

	void ecarter(UUID id, UUID contactId) {
		jdbc.sql("delete from campagne_sms_envoi where campagne_id = :campagne and contact_id = :contact")
				.param(CAMPAGNE, id).param(CONTACT, contactId).update();
	}

	void enregistrer(UUID id, UUID contactId, EnvoiDeSms.Resultat resultat, Instant date) {
		CampagneSms.Envoi envoi = switch (resultat) {
			case EnvoiDeSms.Envoye envoye -> new CampagneSms.Envoi(contactId, "ENVOYE", date, null, envoye.identifiantPrestataire());
			case EnvoiDeSms.Echec echec -> new CampagneSms.Envoi(contactId, "ECHOUE", date, echec.motif(), null);
		};
		jdbc.sql("""
				update campagne_sms_envoi set statut = :statut, envoye_le = :date,
					motif = :motif, identifiant_prestataire = :identifiant
				where campagne_id = :campagne and contact_id = :contact
				""").param(CAMPAGNE, id).param(CONTACT, envoi.contactId()).param("statut", envoi.statut())
				.param("date", avecFuseau(envoi.envoyeLe())).param("motif", envoi.motif())
				.param("identifiant", envoi.identifiantPrestataire()).update();
	}

	private static CampagneSms lireCampagne(ResultSet ligne) throws SQLException {
		return new CampagneSms(ligne.getObject("id", UUID.class), ligne.getString("texte"),
				instant(ligne, "demandee_le"), instant(ligne, ENVOI_PREVU), CampagneSms.Etat.valueOf(ligne.getString("etat")),
				ligne.getLong("envoyes"), ligne.getLong("echoues"));
	}

	private static OffsetDateTime avecFuseau(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet ligne, String colonne) throws SQLException {
		return ligne.getObject(colonne, OffsetDateTime.class).toInstant();
	}
}
