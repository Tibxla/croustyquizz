package fr.croustyquizz.photos;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Minuteur;
import fr.croustyquizz.manche.Refuse;

/** Les opérations sur cette file sont sérialisées par le verrou de sa Soirée. */
final class FileDePhotos {

	static final int ATTENTE_MAX_PAR_JOUEUR = 3;
	static final int ATTENTE_MAX_PAR_SOIREE = 30;
	static final Duration DUREE_AFFICHE = Duration.ofSeconds(8);

	private final Map<UUID, Photo> photosEnAttente = new LinkedHashMap<>();
	private final SequencedMap<UUID, Photo> acceptees = new LinkedHashMap<>();
	private final Map<Cle, Reception> recus = new HashMap<>();
	private final Set<Destination> aPublier = new LinkedHashSet<>();
	private boolean envoisOuverts = true;
	private Optional<Affiche> affiche = Optional.empty();

	boolean ouvertes() {
		return envoisOuverts;
	}

	void regler(boolean valeur) {
		envoisOuverts = valeur;
	}

	void verifierEnvoi(UUID joueurId) {
		if (!envoisOuverts) {
			throw new Refuse(409, "PHOTOS_COUPEES", "L’Animateur a coupé les envois de photos.").exception();
		}
		if (photosEnAttente.values().stream().filter(photo -> photo.joueurId().equals(joueurId)).count() >= ATTENTE_MAX_PAR_JOUEUR) {
			throw new Refuse(409, "LIMITE_ATTEINTE", "Tu as déjà trois photos en attente de validation.").exception();
		}
		if (photosEnAttente.size() >= ATTENTE_MAX_PAR_SOIREE) {
			throw new Refuse(409, "LIMITE_ATTEINTE", "La Soirée a déjà trente photos en attente de validation.").exception();
		}
	}

	UUID ajouter(UUID joueurId, String pseudo, byte[] jpeg) {
		UUID id = UUID.randomUUID();
		photosEnAttente.put(id, new Photo(joueurId, pseudo, jpeg));
		return id;
	}

	Photo moderer(UUID photoId, boolean acceptee) {
		Photo photo = photosEnAttente.remove(photoId);
		if (photo == null) {
			if (acceptees.containsKey(photoId) || affiche.filter(courante -> courante.estLaPhoto(photoId)).isPresent()) {
				throw new Refuse(409, "COMMANDE_PERIMEE", "Cette photo a déjà été acceptée.").exception();
			}
			throw PhotosEnMemoire.photoIntrouvable().exception();
		}
		if (acceptee) {
			acceptees.put(photoId, photo);
		}
		return photo;
	}

	boolean ouvrirAffiche(Instant maintenant, Function<UUID, Minuteur> programmer) {
		if (affiche.isPresent() || acceptees.isEmpty()) {
			return false;
		}
		var suivante = acceptees.pollFirstEntry();
		var projection = new PhotoAffichee(suivante.getKey(), maintenant.plus(DUREE_AFFICHE));
		// Identifie ce créneau, y compris si la même photo reprend à la même heure.
		UUID diffusion = UUID.randomUUID();
		affiche = Optional.of(new Affiche(suivante.getValue(), projection, diffusion, programmer.apply(diffusion)));
		return true;
	}

	void interrompre() {
		affiche.ifPresent(courante -> {
			acceptees.putFirst(courante.projection().photoId(), courante.photo());
			fermerAffiche(courante);
		});
	}

	int expirer(UUID diffusion) {
		var courante = affiche.filter(candidat -> candidat.diffusion().equals(diffusion)).orElse(null);
		if (courante == null) {
			return 0;
		}
		fermerAffiche(courante);
		return courante.photo().taille();
	}

	private void fermerAffiche(Affiche courante) {
		affiche = Optional.empty();
		courante.minuteur().annuler();
	}

	PhotoAffichee photoAffichee() {
		return affiche.map(Affiche::projection).orElse(null);
	}

	byte[] lire(UUID id, boolean lecturePrivee, Instant maintenant) {
		var courante = affiche.filter(candidat -> candidat.estLaPhoto(id)).orElse(null);
		if (courante != null && (lecturePrivee || maintenant.isBefore(courante.projection().echeance()))) {
			return courante.photo().jpeg();
		}
		if (lecturePrivee) {
			Photo photo = photosEnAttente.getOrDefault(id, acceptees.get(id));
			if (photo != null) {
				return photo.jpeg();
			}
		}
		throw PhotosEnMemoire.photoIntrouvable().exception();
	}

	List<PhotoEnAttente> enAttente() {
		return photosEnAttente.entrySet().stream().map(e -> new PhotoEnAttente(e.getKey(), e.getValue().pseudo())).toList();
	}

	long octetsConserves() {
		long attente = photosEnAttente.values().stream().mapToLong(Photo::taille).sum();
		long file = acceptees.values().stream().mapToLong(Photo::taille).sum();
		return attente + file + affiche.map(courante -> courante.photo().taille()).orElse(0);
	}

	Set<Destination> publications(Set<Destination> destinations) {
		aPublier.addAll(destinations);
		return Set.copyOf(aPublier);
	}

	void publicationsTerminees() {
		aPublier.clear();
	}

	Accepte rejouer(Cle cle, Object empreinte) {
		Reception reception = recus.get(cle);
		if (reception == null) {
			return null;
		}
		if (!reception.empreinte().equals(empreinte)) {
			throw new Refuse(409, "ENVOI_REUTILISE", "Cette clé a déjà servi pour un autre envoi.").exception();
		}
		Accepte precedent = reception.recu();
		return new Accepte(precedent.accuse(), Set.of(), precedent.typeEvenement(), precedent.statutHttp());
	}

	void conserver(Cle cle, Object empreinte, Accepte recu) {
		recus.put(cle, new Reception(empreinte, recu));
	}

	record Cle(Acteur acteur, UUID envoiId) { }
	record Photo(UUID joueurId, String pseudo, byte[] jpeg) {
		int taille() { return jpeg.length; }
	}
	private record Affiche(Photo photo, PhotoAffichee projection, UUID diffusion, Minuteur minuteur) {
		boolean estLaPhoto(UUID id) { return projection.photoId().equals(id); }
	}
	record PhotoAffichee(UUID photoId, Instant echeance) { }
	record PhotoEnAttente(UUID photoId, String pseudo) { }
	private record Reception(Object empreinte, Accepte recu) { }
}
