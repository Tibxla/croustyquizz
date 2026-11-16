package fr.croustyquizz.manche;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import fr.croustyquizz.tempsreel.AssembleurDeVues;
import fr.croustyquizz.tempsreel.DiffuseurFactice;
import fr.croustyquizz.tempsreel.FournisseurCommunDemonstration;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;
import fr.croustyquizz.tempsreel.PublicationApresCommit;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.VerrousSoiree;
import tools.jackson.databind.json.JsonMapper;

/** Parcours complet du socle et bloc Quiz factice conforme au schéma existant. */
public final class BancDuSocle {
	public final ModeFacticeDuSocle mode = new ModeFacticeDuSocle();
	public final StockageMancheFactice stockage = new StockageMancheFactice(mode);
	public final HorlogeFactice horloge = new HorlogeFactice();
	public final DiffuseurFactice diffuseur = new DiffuseurFactice();
	public final VerrousSoiree verrous = new VerrousSoiree();
	public final RegistreDesDeroulements registre = new RegistreDesDeroulements();
	public final JsonMapper json = JsonMapper.builder().build();
	public final FournisseurCommunDemonstration commun = new FournisseurCommunDemonstration(stockage, json);
	public final AssembleurDeVues assembleur;
	public final ServiceDeManche<ModeFacticeDuSocle.Reglages, DeroulementFactice.Etat> service;
	public BancDuSocle() {
		FournisseurDeBloc bloc = new BlocFacticeDuSocle(this::serviceCourant, stockage);
		assembleur = new AssembleurDeVues(commun, List.of(bloc), verrous, json);
		var vues = new PublicationDeVues(assembleur, commun, new PublicationApresCommit(), diffuseur);
		service = new ServiceDeManche<>(mode, mode, new SocleDeManche(verrous, stockage, stockage, registre,
				new RegistreDesEnvois(stockage, json), vues, horloge, json));
	}
	private ServiceDeManche<?, ?> serviceCourant() { return service; }
	public UUID creer(UUID soireeId) {
		return service.creer(soireeId, new ModeFacticeDuSocle.Reglages(3, 20));
	}
	public EnvoiDeCommande envoi(UUID soireeId, String route, Object corps) {
		return new EnvoiDeCommande(new Acteur(Acteur.Type.ANIMATEUR, UUID.fromString("00000000-0000-4000-8000-000000000002"), soireeId),
				route, UUID.randomUUID(), corps);
	}
}
