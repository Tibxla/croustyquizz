package fr.croustyquizz.soiree;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ReponsesDeCommande;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Refus du domaine de la Soirée au format Probleme du contrat. Passe avant
 * {@code ErreursHttp}, dont le gestionnaire générique en ferait des 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ErreursSoiree {

	@ExceptionHandler(SoireeIntrouvable.class)
	ResponseEntity<?> introuvable(HttpServletRequest requete) {
		return refus(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.", requete);
	}

	@ExceptionHandler(CodeAccesInvalide.class)
	ResponseEntity<?> codeInvalide(HttpServletRequest requete) {
		return refus(404, "CODE_ACCES_INVALIDE", "Aucune Soirée à rejoindre ne porte ce code.", requete);
	}

	@ExceptionHandler(SoireeDejaLancee.class)
	ResponseEntity<?> dejaLancee(HttpServletRequest requete) {
		return refus(409, "ETAT_INCOMPATIBLE", "La Soirée est déjà lancée ou terminée.", requete);
	}

	@ExceptionHandler(AucunCodeLibre.class)
	ResponseEntity<?> aucunCodeLibre(HttpServletRequest requete) {
		return refus(503, "SERVICE_INDISPONIBLE", "Aucun code d'accès libre pour l'instant. Réessaie l'ouverture.", requete);
	}

	@ExceptionHandler(TitreInvalide.class)
	ResponseEntity<?> titreInvalide(TitreInvalide erreur, HttpServletRequest requete) {
		return refus(422, "DONNEES_INVALIDES", erreur.getMessage() + ".", requete);
	}

	@ExceptionHandler(PseudoInvalide.class)
	ResponseEntity<?> pseudoInvalide(PseudoInvalide erreur, HttpServletRequest requete) {
		return refus(422, "DONNEES_INVALIDES", erreur.getMessage() + ".", requete);
	}

	@ExceptionHandler(PseudoRefuse.class)
	ResponseEntity<?> pseudoRefuse(HttpServletRequest requete) {
		return refus(422, "PSEUDO_REFUSE", "Ce pseudo n'est pas accepté. Choisis-en un autre.", requete);
	}

	@ExceptionHandler(PseudoDejaPris.class)
	ResponseEntity<?> pseudoDejaPris(HttpServletRequest requete) {
		return refus(409, "PSEUDO_DEJA_PRIS", "Ce pseudo est déjà pris dans la Soirée.", requete);
	}

	private static ResponseEntity<?> refus(int statut, String code, String detail, HttpServletRequest requete) {
		return ReponsesDeCommande.http(new Refuse(statut, code, detail), requete.getRequestURI());
	}

}
