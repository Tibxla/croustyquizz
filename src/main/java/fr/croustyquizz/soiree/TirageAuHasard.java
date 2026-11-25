package fr.croustyquizz.soiree;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Tire les codes d'accès au hasard, sans vérifier qu'ils sont libres. */
@Component
class TirageAuHasard implements TirageDeCode {

	private final SecureRandom hasard = new SecureRandom();

	@Override
	public String tirer() {
		return CodeAcces.generer(hasard);
	}

}
