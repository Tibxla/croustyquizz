package fr.croustyquizz.soiree;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.manche.PilotageDeSoiree;

/** Lit le Pilotage enregistré à la création de la Soirée. */
@Component
class PilotageDeSoireeEnregistree implements PilotageDeSoiree {
	private final DepotDeSoirees soirees;
	private final ObjectProvider<PilotageDeSoiree> pilotages;

	PilotageDeSoireeEnregistree(DepotDeSoirees soirees, ObjectProvider<PilotageDeSoiree> pilotages) {
		this.soirees = soirees;
		this.pilotages = pilotages;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean estAutomatique(UUID soireeId) {
		return soirees.findById(soireeId)
				.map(soiree -> soiree.getPilotage() == Pilotage.AUTOMATIQUE)
				.orElseGet(() -> autre().estAutomatique(soireeId));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean estModifiable(UUID soireeId) {
		return !soirees.existsById(soireeId) && autre().estModifiable(soireeId);
	}

	private PilotageDeSoiree autre() {
		return pilotages.orderedStream().filter(pilotage -> !(pilotage instanceof PilotageDeSoireeEnregistree))
				.findFirst().orElseThrow(() -> new IllegalStateException("Aucun Pilotage de repli pour ce profil."));
	}
}
