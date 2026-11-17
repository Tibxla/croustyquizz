package fr.croustyquizz.soiree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.tempsreel.FournisseurPartieCommune;
import tools.jackson.databind.json.JsonMapper;

/**
 * Partie commune des vues tirée de la Soirée enregistrée : la Soirée, ses
 * Joueurs non exclus, sa Manche active et, pour la Console, son programme.
 * Classement général et dernier Buzz restent vides jusqu'à leurs issues.
 * <p>
 * Une Soirée absente de la base est confiée à l'autre fournisseur du profil,
 * s'il y en a un : la démonstration du prototype en dev et recette (#80).
 */
@Component
@Primary
class FournisseurCommunSoiree implements FournisseurPartieCommune {

	private final DepotDeSoirees depotDeSoirees;
	private final DepotDeJoueurs depotDeJoueurs;
	private final DepotDeManches depotDeManches;
	private final JsonMapper json;
	private final ObjectProvider<FournisseurPartieCommune> fournisseurs;

	FournisseurCommunSoiree(DepotDeSoirees depotDeSoirees, DepotDeJoueurs depotDeJoueurs,
			DepotDeManches depotDeManches, JsonMapper json, ObjectProvider<FournisseurPartieCommune> fournisseurs) {
		this.depotDeSoirees = depotDeSoirees;
		this.depotDeJoueurs = depotDeJoueurs;
		this.depotDeManches = depotDeManches;
		this.json = json;
		this.fournisseurs = fournisseurs;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> fournir(UUID soireeId, Destination destination) {
		Soiree soiree = depotDeSoirees.findById(soireeId).orElse(null);
		if (soiree == null) {
			return autre().fournir(soireeId, destination);
		}
		List<ManchePersistante> programme = depotDeManches.deLaSoiree(soireeId);
		ManchePersistante active = programme.stream()
				.filter(manche -> manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE)
				.findFirst().orElse(null);
		Map<String, Object> vue = new LinkedHashMap<>();
		vue.put("soiree", ProjectionSoiree.de(soiree));
		vue.put("mancheActive", active == null ? null : decrire(active, programme.indexOf(active)));
		vue.put("modeEnCours", active == null ? null : active.getMode());
		vue.put("joueurs", presents(soireeId).stream()
				// Le suivi de présence arrive avec #7 et #47 : un Joueur présent est réputé connecté.
				.map(joueur -> new ProjectionAccesJoueur.JoueurPublic(joueur.getId(), joueur.getPseudo(), true))
				.toList());
		vue.put("classement", List.of());
		vue.put("dernierBuzz", null);
		if (destination.type() == Destination.Type.CONSOLE) {
			vue.put("programme", programme.stream().map(manche -> decrire(manche, programme.indexOf(manche))).toList());
		}
		if (destination.type() == Destination.Type.JOUEUR) {
			vue.put("joueurId", destination.joueurId());
			// L'éligibilité d'un retardataire à la Manche en cours relève de #10.
			vue.put("eligiblePourMancheActive", active != null);
			vue.put("derniereReponse", null);
			vue.put("resultatPersonnel", null);
		}
		return vue;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> joueurs(UUID soireeId) {
		if (!depotDeSoirees.existsById(soireeId)) {
			return autre().joueurs(soireeId);
		}
		return presents(soireeId).stream().map(Joueur::getId).toList();
	}

	private List<Joueur> presents(UUID soireeId) {
		return depotDeJoueurs.findBySoireeIdOrderByArriveLeAsc(soireeId).stream()
				.filter(joueur -> !joueur.isExclu())
				.toList();
	}

	/** {@code Manche} dans schemas.yaml ; une Manche prête attend son tour. */
	private Map<String, Object> decrire(ManchePersistante manche, int ordre) {
		return Map.of("id", manche.getMancheId(), "ordre", ordre, "mode", manche.getMode(),
				"etat", manche.getEtat() == EtatManche.PRETE ? "EN_ATTENTE" : manche.getEtat().name(),
				"reglages", json.readTree(manche.getReglages()));
	}

	private FournisseurPartieCommune autre() {
		return fournisseurs.orderedStream().filter(fournisseur -> !(fournisseur instanceof FournisseurCommunSoiree)).findFirst()
				.orElseThrow(() -> new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.").exception());
	}

}
