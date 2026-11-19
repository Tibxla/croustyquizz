package fr.croustyquizz.soiree;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.passage.ParticipantsDePassage;

/**
 * Présence et pseudo des Joueurs d'une Soirée enregistrée (#111), lus en base et non
 * dans la vue. Un Joueur exclu ou inconnu de la Soirée s'affiche « Joueur parti ».
 * Une Soirée absente de la base est confiée à l'autre implémentation du profil
 * (démonstration en dev et recette, {@code @Fallback}).
 */
@Component
class ParticipantsDePassageSoiree implements ParticipantsDePassage {

	static final String JOUEUR_PARTI = "Joueur parti";

	private final DepotDeSoirees soirees;
	private final DepotDeJoueurs joueurs;
	private final ObjectProvider<ParticipantsDePassage> participants;

	ParticipantsDePassageSoiree(DepotDeSoirees soirees, DepotDeJoueurs joueurs,
			ObjectProvider<ParticipantsDePassage> participants) {
		this.soirees = soirees;
		this.joueurs = joueurs;
		this.participants = participants;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean estPresent(UUID soireeId, UUID joueurId) {
		if (!soirees.existsById(soireeId)) {
			ParticipantsDePassage autre = autre();
			return autre != null && autre.estPresent(soireeId, joueurId);
		}
		return present(joueurs, soireeId, joueurId);
	}

	@Override
	@Transactional(readOnly = true)
	public String pseudo(UUID soireeId, UUID joueurId) {
		if (!soirees.existsById(soireeId)) {
			ParticipantsDePassage autre = autre();
			return autre == null ? JOUEUR_PARTI : autre.pseudo(soireeId, joueurId);
		}
		return joueurs.findById(joueurId)
				.filter(joueur -> soireeId.equals(joueur.getSoireeId()) && !joueur.isExclu())
				.map(Joueur::getPseudo)
				.orElse(JOUEUR_PARTI);
	}

	static boolean present(DepotDeJoueurs joueurs, UUID soireeId, UUID joueurId) {
		return joueurs.findById(joueurId).filter(joueur -> soireeId.equals(joueur.getSoireeId()) && !joueur.isExclu()).isPresent();
	}

	private ParticipantsDePassage autre() {
		return participants.orderedStream().filter(p -> !(p instanceof ParticipantsDePassageSoiree)).findFirst().orElse(null);
	}

}
