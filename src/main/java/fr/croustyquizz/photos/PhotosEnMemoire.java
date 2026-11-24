package fr.croustyquizz.photos;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Refuse;

/** Budget commun à toutes les Soirées et projections sans octets. */
@Component
public final class PhotosEnMemoire {

	static final long OCTETS_MAX_SERVEUR = 64_000_000;
	private final ConcurrentMap<UUID, FileDePhotos> files = new ConcurrentHashMap<>();
	private long octetsConserves;

	FileDePhotos file(UUID soireeId) {
		return files.computeIfAbsent(soireeId, id -> new FileDePhotos());
	}

	FileDePhotos trouver(UUID soireeId) {
		return files.get(soireeId);
	}

	synchronized void reserver(int taille) {
		if (octetsConserves + taille > OCTETS_MAX_SERVEUR) {
			throw new Refuse(503, "SERVICE_INDISPONIBLE", "La mémoire des photos est pleine. Réessaie plus tard.").exception();
		}
		octetsConserves += taille;
	}

	synchronized void liberer(long taille) {
		octetsConserves -= taille;
	}

	void purger(UUID soireeId) {
		FileDePhotos file = files.remove(soireeId);
		if (file != null) {
			file.interrompre();
			liberer(file.octetsConserves());
		}
	}

	/** Appelée lors de l’assemblage, sous le verrou de Soirée. */
	public Object projection(UUID soireeId, Destination destination, boolean soireeOuverte, boolean mancheActive) {
		FileDePhotos file = soireeOuverte ? trouver(soireeId) : null;
		boolean ouvertes = soireeOuverte && (file == null || file.ouvertes());
		return switch (destination.type()) {
			case SALLE -> file == null || mancheActive ? null : file.photoAffichee();
			case CONSOLE -> new BlocConsole(ouvertes, file == null ? List.of() : file.enAttente());
			case JOUEUR, TOUTES_LES_MANETTES -> new Reglages(ouvertes);
		};
	}

	static Refuse photoIntrouvable() {
		return new Refuse(404, "RESSOURCE_INTROUVABLE", "Photo introuvable.");
	}

	record Reglages(boolean ouvertes) { }
	record BlocConsole(boolean ouvertes, List<FileDePhotos.PhotoEnAttente> enAttente) { }
}
