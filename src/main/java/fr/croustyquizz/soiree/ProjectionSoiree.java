package fr.croustyquizz.soiree;

import java.util.UUID;

/** Soirée telle que le contrat la montre ({@code Soiree} dans schemas.yaml). */
record ProjectionSoiree(UUID id, String titre, Format format, Pilotage pilotage, EtatSoiree etat, String codeAcces) {

	static ProjectionSoiree de(Soiree soiree) {
		return new ProjectionSoiree(soiree.getId(), soiree.getTitre(), soiree.getFormat(), soiree.getPilotage(),
				soiree.getEtat(), soiree.getCodeAcces());
	}

}
