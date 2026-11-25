package fr.croustyquizz.tempsreel;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Pouvoir;
import fr.croustyquizz.manche.Refuse;

/** Attribution et retrait d'une Délégation (#67), à brancher sur l'écran de la Console. */
@RestController
@RequestMapping("/api/soirees/{soireeId}/delegations/{joueurId}")
class DelegationsControleur {
	private final ServiceDesDelegations service;

	DelegationsControleur(ServiceDesDelegations service) {
		this.service = service;
	}

	@PutMapping
	ResponseEntity<ServiceDesDelegations.Delegation> attribuer(@PathVariable UUID soireeId, @PathVariable UUID joueurId,
			@RequestBody Attribution attribution, Acteur acteur) {
		var delegation = service.attribuer(acteur, soireeId, joueurId, attribution.lire());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(delegation);
	}

	@DeleteMapping
	ResponseEntity<Void> retirer(@PathVariable UUID soireeId, @PathVariable UUID joueurId, Acteur acteur) {
		service.retirer(acteur, soireeId, joueurId);
		return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
	}

	/** Ensemble non vide et sans doublon de pouvoirs pris dans la liste fermée. */
	record Attribution(List<String> pouvoirs) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans l’attribution d’une Délégation.");
		}

		Set<Pouvoir> lire() {
			if (pouvoirs == null || pouvoirs.isEmpty()) {
				throw invalide("Choisis au moins un pouvoir ; pour tout retirer, retire la Délégation.");
			}
			Set<Pouvoir> lus = EnumSet.noneOf(Pouvoir.class);
			for (String nom : pouvoirs) {
				Pouvoir pouvoir;
				try {
					pouvoir = Pouvoir.valueOf(String.valueOf(nom));
				} catch (IllegalArgumentException inconnu) {
					throw invalide("Ce pouvoir ne peut pas être délégué : " + nom + ".");
				}
				if (!lus.add(pouvoir)) {
					throw invalide("Chaque pouvoir ne figure qu’une fois.");
				}
			}
			return lus;
		}

		private static RuntimeException invalide(String detail) {
			return new Refuse(400, "REQUETE_INVALIDE", detail).exception();
		}
	}
}
