package fr.croustyquizz.tempsreel;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seuils de la limite de requêtes ({@code croustyquizz.limites}, valeurs par défaut dans application.yaml). Chaque
 * seuil est une rafale, puis un jeton regagné à chaque intervalle. Ils visent une salle de 60 Joueurs : un humain
 * ne les atteint pas, un script qui martèle une route ou essaie des codes d'accès, si.
 *
 * @param actif          {@code false} coupe la limite sans redéployer, par exemple si elle gênait une Soirée
 * @param plafondCles    nombre de seaux gardés en mémoire pour chaque route ; au-delà, une nouvelle clé passe sans
 *                       être comptée, sauf un code inconnu, refusé
 * @param buzz           par jeton : la Manette n'envoie qu'un Buzz à la fois, un doigt ne tient pas 5 par seconde
 * @param reponses       par jeton et par route : le brouillon part au plus deux fois par seconde pendant la saisie
 * @param codesInconnus  par adresse IP : seuls les codes inconnus comptent, une fois la réponse connue, et le bon
 *                       code n'est jamais refusé ici. Une salle derrière un seul Wi-Fi peut se tromper de code
 *                       une vingtaine de fois d'affilée sans voir de 429, et un voisin qui épuise ces essais ne
 *                       l'empêche pas d'arriver
 * @param arrivees       par adresse IP : deux salles de 60 Joueurs derrière la même adresse arrivent d'un coup, un
 *                       script qui crée des Joueurs en boucle est freiné. Comme le bon code passe toujours le seau
 *                       des codes inconnus, c'est ce seuil qui borne un script qui cherche un code : à 1 essai
 *                       toutes les 3 s parmi les 32<sup>6</sup> codes (10<sup>9</sup>), une adresse mettrait en
 *                       moyenne une dizaine d'années à tomber sur l'une de dix Soirées ouvertes. La rafale de 120
 *                       couvre l'arrivée d'une salle entière ; les retardataires passent ensuite à 20 par minute
 * @param accesEcran     par adresse IP, toutes Soirées confondues : l'Animateur ouvre un ou deux Écrans de salle
 * @param inscriptionContact par adresse IP, toutes Soirées confondues
 * @param qrCode         par clé d'Écran : l'Écran de salle ne le charge qu'au changement de code
 */
@ConfigurationProperties("croustyquizz.limites")
public record ReglagesDesLimites(boolean actif, int plafondCles, Seuil buzz, Seuil reponses, Seuil codesInconnus,
		Seuil arrivees, Seuil accesEcran, Seuil qrCode, Seuil inscriptionContact) {

	public ReglagesDesLimites {
		if (plafondCles <= 0) {
			throw new IllegalArgumentException("croustyquizz.limites.plafond-cles doit valoir au moins 1.");
		}
		Objects.requireNonNull(buzz, "croustyquizz.limites.buzz manquant");
		Objects.requireNonNull(reponses, "croustyquizz.limites.reponses manquant");
		Objects.requireNonNull(codesInconnus, "croustyquizz.limites.codes-inconnus manquant");
		Objects.requireNonNull(arrivees, "croustyquizz.limites.arrivees manquant");
		Objects.requireNonNull(accesEcran, "croustyquizz.limites.acces-ecran manquant");
		Objects.requireNonNull(qrCode, "croustyquizz.limites.qr-code manquant");
		Objects.requireNonNull(inscriptionContact, "croustyquizz.limites.inscription-contact manquant");
	}

	/** Rafale acceptée d'un coup, puis un jeton regagné à chaque intervalle. */
	public record Seuil(int rafale, Duration intervalle) {
		public Seuil {
			if (rafale <= 0 || intervalle == null || intervalle.isNegative() || intervalle.isZero()) {
				throw new IllegalArgumentException("Un seuil de limite demande une rafale d'au moins 1 et un intervalle positif.");
			}
		}
	}

}
