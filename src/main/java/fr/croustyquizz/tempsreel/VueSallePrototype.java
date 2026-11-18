package fr.croustyquizz.tempsreel;

import java.util.List;

/** Vue complète immuable des derniers Buzz du prototype, dans l'ordre de réception. */
public record VueSallePrototype(List<BuzzRecu> buzz) implements Evenement {
	public VueSallePrototype {
		buzz = List.copyOf(buzz);
	}

	@Override
	public String type() {
		return "buzz-recu";
	}
}
