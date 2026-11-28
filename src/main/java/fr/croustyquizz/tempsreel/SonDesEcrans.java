package fr.croustyquizz.tempsreel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Destination;

/** Signaux en mémoire par connexion, consultés et modifiés sous le verrou de la Soirée. */
@Component
final class SonDesEcrans implements FournisseurDeChampCommun {
	enum Etat { ABSENT, COUPE, ACTIVE }

	private final Map<UUID, Map<String, Boolean>> signaux = new ConcurrentHashMap<>();

	Etat etat(UUID soireeId) {
		var ecrans = signaux.get(soireeId);
		if (ecrans == null) { return Etat.ABSENT; }
		return ecrans.containsValue(true) ? Etat.ACTIVE : Etat.COUPE;
	}

	Boolean remplacer(UUID soireeId, String connexion, Boolean actif) {
		if (actif != null) {
			return signaux.computeIfAbsent(soireeId, id -> new HashMap<>()).put(connexion, actif);
		}
		var ecrans = signaux.get(soireeId);
		if (ecrans == null) { return null; }
		Boolean precedent = ecrans.remove(connexion);
		if (ecrans.isEmpty()) { signaux.remove(soireeId); }
		return precedent;
	}

	@Override
	public String champ() { return "sonEcran"; }

	@Override
	public boolean concerne(Destination destination) { return Destination.CONSOLE.equals(destination); }

	@Override
	public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
		return etat(soireeId);
	}
}
