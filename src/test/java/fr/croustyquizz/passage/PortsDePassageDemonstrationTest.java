package fr.croustyquizz.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.BancDuSocle;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.RefusDeCommande;
import fr.croustyquizz.tempsreel.FournisseurCommunDemonstration;

class PortsDePassageDemonstrationTest {
	private final BancDuSocle banc = new BancDuSocle();
	private final ParticipantsDePassage participants = new ParticipantsDePassageDemonstration(banc.commun);
	private final PorteeDePassage portee = new PorteeDePassageDemonstration(banc.stockage, banc.commun);
	private final UUID soiree = UUID.randomUUID();
	private final UUID manche = UUID.randomUUID();

	PortsDePassageDemonstrationTest() {
		banc.stockage.enregistrer(new ManchePersistante(manche, soiree, ModeDeJeu.KARAOKE, "{}", banc.horloge.maintenant()));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 2, 3, 4})
	void chaqueJoueurDeDemonstrationEstReconnuDansSaManche(int rang) {
		UUID joueur = FournisseurCommunDemonstration.joueurId(soiree, rang);
		assertThat(participants.estPresent(soiree, joueur)).isTrue();
		assertThat(participants.estPresent(UUID.randomUUID(), joueur)).isFalse();
		assertThat(participants.pseudo(soiree, joueur)).isEqualTo("Joueur de démonstration " + rang);
		assertThatCode(() -> portee.verifier(new Acteur(Acteur.Type.JOUEUR, joueur, soiree), soiree, manche))
				.doesNotThrowAnyException();
	}

	@Test
	void unJoueurInconnuEstRefuse() {
		UUID inconnu = UUID.randomUUID();
		assertThat(participants.estPresent(soiree, inconnu)).isFalse();
		assertThatThrownBy(() -> portee.verifier(new Acteur(Acteur.Type.JOUEUR, inconnu, soiree), soiree, manche))
				.isInstanceOfSatisfying(RefusDeCommande.class, exception -> {
					assertThat(exception.refus().statutHttp()).isEqualTo(404);
					assertThat(exception.refus().code()).isEqualTo("RESSOURCE_INTROUVABLE");
				});
	}
}
