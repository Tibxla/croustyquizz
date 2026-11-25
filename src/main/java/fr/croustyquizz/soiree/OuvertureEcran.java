package fr.croustyquizz.soiree;

/**
 * Accès d'Écran de salle qui vient d'être créé. La clé n'existe en clair qu'ici :
 * elle est remise à la Console, puis seule son empreinte reste en base.
 */
public record OuvertureEcran(AccesEcran acces, String cle) {

	/** La clé ne doit jamais finir dans un journal. */
	@Override
	public String toString() {
		return "OuvertureEcran[acces=" + acces.getId() + ", cle=***]";
	}

}
