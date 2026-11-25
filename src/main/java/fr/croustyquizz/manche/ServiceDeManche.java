package fr.croustyquizz.manche;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Parcours unique : préparation, verrou de Soirée, transaction, domaine, persistance, publication après commit.
 * Chaque mode déclare un bean qui étend ce service et fournit sa fabrique et sa persistance JPA.
 */
public class ServiceDeManche<R, E> {
	private static final Set<Destination> TOUT = Set.of(Destination.SALLE, Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
	private final FabriqueDeDeroulement<R, E> fabrique;
	private final PersistanceDuMode<E> persistance;
	private final SocleDeManche socle;
	private final TransactionTemplate transaction;

	public ServiceDeManche(FabriqueDeDeroulement<R, E> fabrique, PersistanceDuMode<E> persistance, SocleDeManche socle) {
		this.fabrique = fabrique; this.persistance = persistance; this.socle = socle;
		transaction = new TransactionTemplate(socle.transactions());
		// Le commit doit terminer avant de rendre le verrou, même si l'appelant a une transaction.
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}
	/** Lecture pour le FournisseurDeBloc, dans la transaction existante ou une transaction de capture. */
	public Object instantane(UUID soireeId, UUID mancheId) {
		return socle.verrous().executer(soireeId, () -> {
			Supplier<Object> lecture = () -> deroulement(charger(mancheId, soireeId)).instantane();
			if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
				return lecture.get();
			}
			return transaction.execute(statut -> lecture.get());
		});
	}
	public ModeDeJeu mode() { return fabrique.mode(); }
	public UUID creer(UUID soireeId, Object reglages) {
		AjoutDeManche ajout = preparerAjout(soireeId, reglages);
		UUID mancheId = ajout.manche().getMancheId();
		return sousTransaction(soireeId, mancheId, () -> {
			ajout.enregistrer(socle.depot());
			return mancheId;
		});
	}
	/**
	 * Prépare un ajout hors du verrou de la Soirée ; un mode qui consulte un service externe à la création le
	 * redéfinit. L'appelant enregistre le résultat sous le verrou.
	 */
	public AjoutDeManche preparerAjout(UUID soireeId, Object reglages) {
		return new AjoutDeManche(preparer(soireeId, reglages), manche -> { });
	}
	/** Construit une Manche prête à enregistrer dans la transaction qui compose le programme. */
	public ManchePersistante preparer(UUID soireeId, Object reglages) {
		R valeurs = resoudreReglages(reglages);
		fabrique.reglagesCommuns(valeurs);
		return new ManchePersistante(UUID.randomUUID(), soireeId, mode(), socle.json().writeValueAsString(valeurs),
				socle.horloge().maintenant());
	}
	protected R resoudreReglages(Object reglages) {
		return socle.json().convertValue(reglages, fabrique.typeReglages());
	}
	public ResultatDeCommande demarrer(UUID mancheId, EnvoiDeCommande envoi) {
		return traiter(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) -> {
			d.demarrer();
			return new Accepte(new AccuseCommande(envoi.envoiId(), recueLe, envoi.acteur().soireeId()),
					TOUT, "manche-demarree");
		}), EtatManche.EN_COURS, true);
	}
	public ResultatDeCommande piloter(UUID mancheId, EnvoiDeCommande envoi, InterventionDeManche intervention) {
		return traiter(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) ->
				intervention.appliquer(d, new CommandePilotage(envoi.acteur(), envoi.envoiId(), recueLe))),
				intervention.etatCible(), false);
	}
	ResultatDeCommande refuserPilotage(UUID mancheId, EnvoiDeCommande envoi, Refuse refus) {
		return sousTransaction(envoi.acteur().soireeId(), mancheId, () -> {
			var precedent = socle.envois().retrouver(envoi);
			if (precedent != null) { return precedent; }
			conserver(envoi, refus);
			return refus;
		});
	}

	/** Clôture interne à la Soirée, quelle que soit l'étape de sa Manche active. */
	public final ResultatDeCommande terminerPourSoiree(UUID mancheId, EnvoiDeCommande envoi) {
		return traiter(mancheId, envoi, () -> PreparationDeCommande.prete((d, recueLe) ->
				d.terminer(new CommandePilotage(envoi.acteur(), envoi.envoiId(), recueLe))), EtatManche.TERMINEE, false);
	}
	/** La préparation externe est hors verrou ; sa fonction revalide ensuite le domaine sous verrou. */
	public ResultatDeCommande executer(UUID mancheId, EnvoiDeCommande envoi, Supplier<PreparationDeCommande> preparer) {
		return executer(mancheId, envoi, preparer, null);
	}
	protected ResultatDeCommande executer(UUID mancheId, EnvoiDeCommande envoi,
			Supplier<PreparationDeCommande> preparer, EtatManche etatApresAcceptation) {
		return traiter(mancheId, envoi, preparer, etatApresAcceptation, false);
	}
	private ResultatDeCommande traiter(UUID id, EnvoiDeCommande envoi, Supplier<PreparationDeCommande> preparer,
			EtatManche cible, boolean demarrage) {
		UUID soireeId = envoi.acteur().soireeId();
		// Une répétition évite aussi un nouvel appel externe, sans retenir le verrou pendant la préparation.
		ResultatDeCommande repetition = sousTransaction(soireeId, id, () -> {
			ManchePersistante manche = charger(id, soireeId);
			ResultatDeCommande recu = socle.envois().retrouver(envoi);
			if (recu != null) { return recu; }
			Refuse refus = verifierAvantPreparation(manche, envoi, demarrage);
			if (refus != null) { conserver(envoi, refus); }
			return refus;
		});
		if (repetition != null) { return repetition; }
		PreparationDeCommande preparation = preparer.get();
		return sousTransaction(soireeId, id, () -> {
			ManchePersistante manche = charger(id, soireeId);
			ResultatDeCommande dejaRecu = socle.envois().retrouver(envoi);
			if (dejaRecu != null) { return dejaRecu; }
			ResultatDeCommande resultat = preparation.refus();
			if (resultat == null) {
				resultat = verifierEtat(manche, demarrage);
				if (resultat == null) { resultat = verifierAvantPreparation(manche, envoi, demarrage); }
				if (resultat == null) {
					var deroulement = deroulement(manche);
					Instant recueLe = socle.horloge().maintenant().truncatedTo(MICROS);
					resultat = preparation.appliquer().apply(deroulement, recueLe);
					if (resultat instanceof Accepte accepte) {
						enregistrer(manche, deroulement, cible, accepte, deroulement.instantane(), recueLe);
					}
				}
			}
			conserver(envoi, resultat);
			return resultat;
		});
	}
	/**
	 * Mutation décidée par le service lui-même, sans envoi ni reçu (par exemple après une préparation externe) :
	 * verrou, transaction, domaine, persistance et publication, comme une commande. Une Manche qui n'est ni en cours
	 * ni en pause la refuse sans appeler le domaine.
	 */
	protected ResultatDeCommande appliquer(UUID soireeId, UUID mancheId,
			Function<DeroulementDeManche, ResultatDeCommande> action) {
		return sousTransaction(soireeId, mancheId, () -> {
			ManchePersistante manche = charger(mancheId, soireeId);
			if (manche.getEtat() != EtatManche.EN_COURS && manche.getEtat() != EtatManche.EN_PAUSE) {
				return new Refuse(409, "ETAT_INCOMPATIBLE", "La Manche n'est pas en cours.");
			}
			var d = deroulement(manche);
			ResultatDeCommande resultat = action.apply(d);
			if (resultat instanceof Accepte accepte) {
				enregistrer(manche, d, null, accepte, d.instantane(), socle.horloge().maintenant().truncatedTo(MICROS));
			}
			return resultat;
		});
	}
	/**
	 * Appelé sous le verrou, avant le commit, après chaque mutation enregistrée, minuteurs compris : un mode y lance
	 * la préparation externe que sa nouvelle phase attend. Il ne doit ni bloquer ni appeler de service externe.
	 */
	protected void apresEnregistrement(ManchePersistante manche, Object instantane) {
		// Aucun mode du socle n'attend de préparation externe.
	}
	/** Par défaut les autres modes ne décident rien avant leur préparation externe. */
	protected Refuse verifierAvantPreparation(ManchePersistante manche, EnvoiDeCommande envoi, boolean demarrage) {
		return null;
	}
	protected Refuse verifierEtat(ManchePersistante manche, boolean demarrage) {
		boolean prete = manche.getEtat() == EtatManche.PRETE;
		return manche.getEtat() == EtatManche.TERMINEE || prete != demarrage || (demarrage && uneAutreActive(manche))
				? new Refuse(409, "ETAT_INCOMPATIBLE", "Commande incompatible avec la Manche.") : null;
	}
	protected boolean doitConserverRecu(ResultatDeCommande resultat) { return true; }
	private void conserver(EnvoiDeCommande envoi, ResultatDeCommande resultat) {
		if (!doitConserverRecu(resultat) && resultat instanceof Refuse refus) { throw refus.exception(); }
		socle.envois().enregistrer(envoi, resultat, socle.horloge().maintenant());
	}

	private boolean uneAutreActive(ManchePersistante manche) {
		return socle.depot().deLaSoiree(manche.getSoireeId()).stream().anyMatch(m -> !m.getMancheId().equals(manche.getMancheId())
				&& (m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE));
	}
	private ManchePersistante charger(UUID id, UUID soireeId) {
		ManchePersistante manche = socle.depot().charger(id);
		if (manche == null || !manche.getSoireeId().equals(soireeId) || manche.getMode() != mode()) {
			throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche introuvable.").exception();
		}
		return manche;
	}
	private DeroulementDeManche deroulement(ManchePersistante manche) {
		return socle.registre().obtenir(manche.getMancheId(), () -> {
			R reglages = socle.json().readValue(manche.getReglages(), fabrique.typeReglages());
			HorlogeDeManche horloge = new HorlogeDeManche(socle.horloge(), (d, action) -> minuter(manche, d, action));
			boolean automatique = socle.pilotage().estAutomatique(manche.getSoireeId());
			// Le service constate estTerminee et persiste la fin naturelle.
			var contexte = new ContexteDeManche(manche.getSoireeId(), manche.getMancheId(), fabrique.reglagesCommuns(reglages),
					horloge, () -> { }, automatique);
			DeroulementDeManche d = manche.getEtat() == EtatManche.PRETE ? fabrique.creer(contexte, reglages)
					: fabrique.restaurer(contexte, reglages, persistance.charger(manche.getMancheId()), manche.getEtat());
			horloge.attacher(d);
			return d;
		});
	}
	private void minuter(ManchePersistante identite, Supplier<DeroulementDeManche> deroulement, HorlogeDeManche.Rappel rappel) {
		sousTransaction(identite.getSoireeId(), identite.getMancheId(), () -> {
			if (rappel.annule().getAsBoolean()) { return null; }
			DeroulementDeManche d = deroulement.get();
			if (!socle.registre().contient(identite.getMancheId(), d)) { return null; }
			ManchePersistante manche = charger(identite.getMancheId(), identite.getSoireeId());
			if (manche.getEtat() != EtatManche.EN_COURS) { return null; }
			Object avant = d.instantane();
			rappel.action().run();
			Object apres = d.instantane();
			if (!Objects.equals(avant, apres)) {
				enregistrer(manche, d, null, new Accepte(null, TOUT, fabrique.typeEvenementMinuteur(d)), apres,
						socle.horloge().maintenant());
			}
			return null;
		});
	}
	/** Un mode peut exposer son propre état dans son instantané. */
	protected EtatManche etatApresAcceptation(EtatManche etatParDefaut, Object instantane) {
		return etatParDefaut;
	}

	private void enregistrer(ManchePersistante manche, DeroulementDeManche d, EtatManche cible, Accepte accepte,
			Object instantane, Instant recueLe) {
		EtatManche etatParDefaut = cible == null ? manche.getEtat() : cible;
		EtatManche etat = d.estTerminee() ? EtatManche.TERMINEE : etatApresAcceptation(etatParDefaut, instantane);
		manche.changerEtat(etat, recueLe);
		boolean classementModifie = manche.getMode().compteAuClassementGeneral() && manche.actualiserPoints(d.points());
		persistance.enregistrer(manche.getMancheId(), instantane);
		socle.depot().enregistrer(manche);
		socle.vues().publier(manche.getSoireeId(), accepte, classementModifie);
		apresEnregistrement(manche, instantane);
	}
	private <T> T sousTransaction(UUID soireeId, UUID mancheId, Supplier<T> action) {
		return socle.verrous().executer(soireeId, () -> {
			try { return transaction.execute(statut -> action.get()); }
			catch (RuntimeException erreur) {
				socle.registre().retirer(mancheId);
				throw erreur;
			}
		});
	}
}
