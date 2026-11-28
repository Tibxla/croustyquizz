package fr.croustyquizz.photos;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Pouvoir;
import fr.croustyquizz.tempsreel.DonneesDeDelegation;

/** Photos en attente pour le pouvoir délégué PHOTOS (#67) : ni JPEG ni réglage d'ouverture des envois. */
@Component
class PhotosDeleguees implements DonneesDeDelegation {
	private final PhotosEnMemoire photos;

	PhotosDeleguees(PhotosEnMemoire photos) {
		this.photos = photos;
	}

	@Override
	public Pouvoir pouvoir() { return Pouvoir.PHOTOS; }

	@Override
	public String champ() { return "photos"; }

	@Override
	public Object fournir(UUID soireeId) { return new Projection(photos.enAttente(soireeId)); }

	record Projection(List<FileDePhotos.PhotoEnAttente> enAttente) { }
}
