package fr.croustyquizz.tempsreel;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.ModeDeJeu;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;

/** Assemble les blocs sous le verrou ; un mode sans fournisseur laisse un bloc nul. */
@Component
public final class AssembleurDeVues implements CaptureVueRepli {
	private static final Logger JOURNAL = LoggerFactory.getLogger(AssembleurDeVues.class);
	private final FournisseurPartieCommune commun;
	private final VerrousSoiree verrous;
	private final JsonMapper json;
	private final Function<UUID, MusiqueDAttente> lireMusiqueDAttente;
	private final Map<ModeDeJeu, FournisseurDeBloc> blocs = new EnumMap<>(ModeDeJeu.class);

	@Autowired
	AssembleurDeVues(FournisseurPartieCommune commun, List<FournisseurDeBloc> fournisseurs,
			VerrousSoiree verrous, JsonMapper json, DepotMusiqueDAttente musique) {
		this(commun, fournisseurs, verrous, json, musique::lire);
	}

	public AssembleurDeVues(FournisseurPartieCommune commun, List<FournisseurDeBloc> fournisseurs,
			VerrousSoiree verrous, JsonMapper json) {
		this(commun, fournisseurs, verrous, json, soireeId -> MusiqueDAttente.PAR_DEFAUT);
	}

	private AssembleurDeVues(FournisseurPartieCommune commun, List<FournisseurDeBloc> fournisseurs,
			VerrousSoiree verrous, JsonMapper json, Function<UUID, MusiqueDAttente> lireMusiqueDAttente) {
		this.commun = commun; this.verrous = verrous; this.json = json;
		this.lireMusiqueDAttente = lireMusiqueDAttente;
		for (var fournisseur : fournisseurs) {
			if (blocs.put(fournisseur.mode(), fournisseur) != null) {
				throw new IllegalArgumentException("Deux fournisseurs pour le même mode.");
			}
		}
	}
	public VueComposee assembler(UUID soireeId, Destination destination, String type) {
		return assembler(soireeId, Set.of(destination), type).get(destination);
	}
	Map<Destination, VueComposee> assembler(UUID soireeId, Set<Destination> destinations, String type) {
		return verrous.executer(soireeId, () -> {
			MusiqueDAttente musique = destinations.contains(Destination.SALLE) || destinations.contains(Destination.CONSOLE)
					? lireMusiqueDAttente.apply(soireeId) : null;
			Map<ModeDeJeu, Function<Destination, Object>> projections = new EnumMap<>(ModeDeJeu.class);
			Map<Destination, VueComposee> vues = new LinkedHashMap<>();
			var communes = commun.fournir(soireeId, destinations);
			for (var destination : destinations) {
				vues.put(destination, composer(soireeId, destination, type, communes.parties().get(destination),
						projections, musique, communes.soireeTermineeLe()));
			}
			return vues;
		});
	}
	private VueComposee composer(UUID soireeId, Destination destination, String type, Map<String, Object> partieCommune,
			Map<ModeDeJeu, Function<Destination, Object>> projections, MusiqueDAttente musique, Instant soireeTermineeLe) {
		Map<String, Object> vue = new LinkedHashMap<>(partieCommune);
		if (destination.equals(Destination.SALLE) || destination.equals(Destination.CONSOLE)) {
			vue.put("musiqueAttente", musique);
		}
		Object mode = vue.get("modeEnCours");
		ModeDeJeu actif = mode == null ? null : ModeDeJeu.valueOf(mode.toString());
		for (var valeur : ModeDeJeu.values()) {
			vue.put(cle(valeur), null);
		}
		if (actif != null) {
			var projection = projections.computeIfAbsent(actif, valeur -> preparer(soireeId, valeur));
			vue.put(cle(actif), projection.apply(destination));
		}
		// Détache aussi les structures imbriquées avant de quitter la transaction.
		Map<String, Object> copie = json.convertValue(vue, new TypeReference<Map<String, Object>>() { });
		return new VueComposee(type, copie, soireeTermineeLe);
	}
	private Function<Destination, Object> preparer(UUID soireeId, ModeDeJeu mode) {
		var fournisseur = blocs.get(mode);
		if (fournisseur == null) {
			JOURNAL.warn("Bloc absent pour le mode {} de la Soirée {}", mode, soireeId);
			return destination -> null;
		}
		return fournisseur.preparer(soireeId);
	}
	private static String cle(ModeDeJeu mode) {
		return switch (mode) { case QUIZ -> "quiz"; case BLIND_TEST -> "blindtest";
			case KARAOKE -> "karaoke"; case FREESTYLE -> "freestyle"; };
	}
	@Override
	public Evenement capturerSalle(UUID soireeId) { return assembler(soireeId, Destination.SALLE, "manche-demarree"); }
	@Override
	public Evenement capturerConsole(UUID soireeId) { return assembler(soireeId, Destination.CONSOLE, "manche-demarree"); }
	@Override
	public Evenement capturerJoueur(UUID soireeId, UUID joueurId) {
		return assembler(soireeId, Destination.joueur(joueurId), "manche-demarree");
	}
}
