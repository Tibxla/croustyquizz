package fr.croustyquizz.tempsreel;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Refuse;

/** Aucun domaine de Soirée de production n'est disponible avant #6. */
@Component
@Profile("!dev & !recette")
public final class FournisseurCommunIndisponible implements FournisseurPartieCommune {
	@Override
	public Map<String, Object> fournir(UUID soireeId, Destination destination) { throw refus(); }
	@Override
	public List<UUID> joueurs(UUID soireeId) { throw refus(); }
	private RuntimeException refus() {
		return new Refuse(401, "AUTHENTIFICATION_REQUISE", "Une authentification est requise.").exception();
	}
}
