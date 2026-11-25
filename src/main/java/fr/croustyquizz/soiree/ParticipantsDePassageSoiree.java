package fr.croustyquizz.soiree;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.passage.ParticipantsDePassage;

/**
 * Présence et pseudo des Joueurs d'une Soirée enregistrée (#111), lus en base et non
 * dans la vue. Un Joueur exclu ou inconnu est absent des participants.
 * Une Soirée absente de la base est confiée à l'autre implémentation du profil
 * (démonstration en dev et recette, {@code @Fallback}).
 */
@Component
class ParticipantsDePassageSoiree implements ParticipantsDePassage {

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
		Optional<Joueur> joueur = joueurs.findById(joueurId).filter(j -> soireeId.equals(j.getSoireeId()));
		if (joueur.isPresent()) {
			return !joueur.get().isExclu();
		}
		ParticipantsDePassage autre = soirees.existsById(soireeId) ? null : autre();
		return autre != null && autre.estPresent(soireeId, joueurId);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, String> pseudos(UUID soireeId) {
		if (soirees.existsById(soireeId)) {
			return joueurs.findBySoireeIdOrderByArriveLeAsc(soireeId).stream()
					.filter(joueur -> !joueur.isExclu())
					.collect(Collectors.toUnmodifiableMap(Joueur::getId, Joueur::getPseudo));
		}
		ParticipantsDePassage autre = autre();
		return autre == null ? Map.of() : autre.pseudos(soireeId);
	}

	/** Le filtre {@code instanceof} suppose des proxys CGLIB, le réglage par défaut de Spring Boot. */
	private ParticipantsDePassage autre() {
		return participants.orderedStream().filter(p -> !(p instanceof ParticipantsDePassageSoiree)).findFirst().orElse(null);
	}

}
