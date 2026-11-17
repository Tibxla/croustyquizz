package fr.croustyquizz.tempsreel;

import java.security.Principal;
import fr.croustyquizz.manche.Acteur;

/** Principal technique qui rattache les abonnements à l'Acteur du CONNECT. */
public record IdentiteStomp(Acteur acteur) implements Principal {
	@Override
	public String getName() { return acteur.getName(); }
}
