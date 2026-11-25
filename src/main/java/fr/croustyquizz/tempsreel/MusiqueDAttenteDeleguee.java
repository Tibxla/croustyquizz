package fr.croustyquizz.tempsreel;

import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Pouvoir;

/** État de la Musique d'attente pour le pouvoir MUSIQUE_ATTENTE, tel que la Console le voit. */
@Component
class MusiqueDAttenteDeleguee implements DonneesDeDelegation {
	private final DepotMusiqueDAttente depot;

	MusiqueDAttenteDeleguee(DepotMusiqueDAttente depot) {
		this.depot = depot;
	}

	@Override
	public Pouvoir pouvoir() { return Pouvoir.MUSIQUE_ATTENTE; }

	@Override
	public String champ() { return "musiqueAttente"; }

	@Override
	public Object fournir(UUID soireeId) { return depot.lire(soireeId); }
}
