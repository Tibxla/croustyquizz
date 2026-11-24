package fr.croustyquizz.animateur;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.RefusDeCommande;
import fr.croustyquizz.soiree.Secrets;

@Service
class InvitationsAnimateur {

	private static final Duration DUREE = Duration.ofDays(7);
	private final SecureRandom hasard = new SecureRandom();
	private final JdbcClient jdbc;
	private final ComptesAnimateur comptes;
	private final Horloge horloge;

	InvitationsAnimateur(JdbcClient jdbc, ComptesAnimateur comptes, Horloge horloge) {
		this.jdbc = jdbc;
		this.comptes = comptes;
		this.horloge = horloge;
	}

	InvitationCreee creer(UUID lieuId, String email) {
		comptes.verifierEmailDisponible(email);
		String cle = Secrets.tirer(hasard);
		Instant expireLe = horloge.maintenant().plus(DUREE).truncatedTo(ChronoUnit.MICROS);
		jdbc.sql("""
				insert into invitation_animateur (empreinte_cle, lieu_id, email, expire_le)
				values (:empreinte, :lieuId, :email, :expireLe)
				""")
				.param("empreinte", Secrets.empreinte(cle)).param("lieuId", lieuId).param("email", email)
				.param("expireLe", expireLe.atOffset(ZoneOffset.UTC)).update();
		return new InvitationCreee(cle, expireLe);
	}

	Invitation consulter(String cle) {
		return jdbc.sql("""
				select lieu.nom, lieu.ville, invitation_animateur.email
				from invitation_animateur join lieu on lieu.id = invitation_animateur.lieu_id
				where empreinte_cle = :empreinte and expire_le > :maintenant
				""")
				.param("empreinte", empreinte(cle)).param("maintenant", horloge.maintenant().atOffset(ZoneOffset.UTC))
				.query((ligne, numero) -> new Invitation(ligne.getString("nom"), ligne.getString("ville"),
						ligne.getString("email")))
				.optional().orElseThrow(InvitationsAnimateur::invitationInvalide);
	}

	@Transactional
	ComptesAnimateur.IdentiteAnimateur accepter(SaisiesAnimateur.AcceptationInvitation saisie) {
		// Seule la transaction qui supprime cette ligne peut créer le compte.
		var invitation = jdbc.sql("""
				delete from invitation_animateur where empreinte_cle = :empreinte and expire_le > :maintenant
				returning lieu_id, email
				""")
				.param("empreinte", empreinte(saisie.cle())).param("maintenant", horloge.maintenant().atOffset(ZoneOffset.UTC))
				.query((ligne, numero) -> new Destinataire(ligne.getObject("lieu_id", UUID.class), ligne.getString("email")))
				.optional().orElseThrow(InvitationsAnimateur::invitationInvalide);
		return comptes.creerCompte(invitation.lieuId(), saisie.nom(), invitation.email(), saisie.motDePasse());
	}

	private static byte[] empreinte(String cle) {
		if (!Secrets.aLaForme(cle)) {
			throw invitationInvalide();
		}
		return Secrets.empreinte(cle);
	}

	private static RefusDeCommande invitationInvalide() {
		return new Refuse(404, "INVITATION_INVALIDE", "Ce lien d’invitation est invalide, expiré ou déjà utilisé.").exception();
	}

	record InvitationCreee(String cle, Instant expireLe) {
		@Override
		public String toString() {
			return "InvitationCreee[expireLe=" + expireLe + "]";
		}
	}

	record Invitation(String nomLieu, String ville, String email) { }

	private record Destinataire(UUID lieuId, String email) { }
}
