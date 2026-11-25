package fr.croustyquizz.freestyle;

import static java.time.temporal.ChronoUnit.MICROS;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.FournisseurPartieCommune;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServiceFreestyle extends ServiceDePassages<ReglagesFreestyle, EtatFreestyle> {
    private final PersistanceFreestyleJpa persistance;
    private final EtatDeSoiree etatSoiree;
    private final FournisseurPartieCommune commun;
    private final DictionnaireDeRimes dictionnaire;
    private final TransactionTemplate declaration;

    public ServiceFreestyle(FabriqueFreestyle fabrique, PersistanceFreestyleJpa persistance, SocleDeManche socle,
            PorteeDePassage portee, ParticipantsDePassage participants, PersistancePassageJpa passages,
            EtatDeSoiree etatSoiree, FournisseurPartieCommune commun, DictionnaireDeRimes dictionnaire) {
        super(fabrique, persistance, socle, portee, participants, passages);
        this.persistance = persistance;
        this.etatSoiree = etatSoiree;
        this.commun = commun;
        this.dictionnaire = dictionnaire;
        declaration = new TransactionTemplate(socle.transactions());
        declaration.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public UUID creer(UUID soireeId, Object reglages) {
        return socle.verrous().executer(soireeId, () -> super.creer(soireeId, reglages));
    }

    @Override public ManchePersistante preparer(UUID soireeId, Object reglages) {
        return lecture(soireeId, () -> {
            var valeurs = CorpsFreestyle.reglages(socle.json().valueToTree(reglages), socle.json());
            var selection = valeurs.selectionDisponible(persistance.instrumentales(soireeId));
            if (selection.size() != valeurs.instrumentaleIds().size()) {
                throw new Refuse(422, "INSTRUMENTALE_INDISPONIBLE",
                        "Une Instrumentale de la sélection n’est plus disponible.").exception();
            }
            if (selection.stream().anyMatch(fiche -> !fiche.couvre(valeurs.nombreMesures()))) {
                throw new Refuse(422, "INSTRUMENTALE_TROP_COURTE",
                        "Une Instrumentale de la sélection finit avant la Grille de rimes.").exception();
            }
            return super.preparer(soireeId, valeurs);
        });
    }

    @Override protected EtatManche etatApresAcceptation(EtatManche etatParDefaut, Object instantane) {
        return ((InstantaneFreestyle) instantane).etatManche();
    }

    @Override public ResultatDeCommande inscrire(UUID mancheId, EnvoiDeCommande envoi, CorpsPassage.Inscription corps) {
        verifierEntree(mancheId, envoi, Acteur.Type.JOUEUR);
        UUID soireeId = envoi.acteur().soireeId();
        return executer(mancheId, envoi, () -> PreparationDeCommande.prete((deroulement, recueLe) -> {
            var instantane = (InstantaneFreestyle) deroulement.instantane();
            long numero = passages.numeroPropose(soireeId);
            var resultat = deroulement.recevoir(new InscrireFreestyle(envoi.acteur(), recueLe, envoi.envoiId(),
                    UUID.randomUUID(), numero, corps.instrumentaleId(), persistance.instrumentales(soireeId),
                    dictionnaire.amorces(instantane.reglages().theme())));
            if (resultat instanceof Accepte) { passages.incrementerNumero(soireeId, numero); }
            return resultat;
        }));
    }

    public List<Instrumentale> instrumentales(UUID soireeId) {
        return lecture(soireeId, () -> {
            commun.fournir(soireeId, Destination.CONSOLE);
            return persistance.instrumentales(soireeId);
        });
    }

    /** La fenêtre qui déclare devient le lecteur désigné ; un rejeu rend le reçu sans changer de lecteur. */
    public ResultatDeCommande declarer(EnvoiDeCommande envoi, CorpsFreestyle.Declaration corps) {
        UUID soireeId = envoi.acteur().soireeId();
        List<Instrumentale> catalogue = corps.fiches().stream().sorted(Comparator.comparing(Instrumentale::instrumentaleId)).toList();
        return socle.verrous().executer(soireeId, () -> declaration.execute(statut -> {
            ResultatDeCommande recu = socle.envois().retrouver(envoi);
            if (recu != null) { return recu; }
            var recueLe = socle.horloge().maintenant().truncatedTo(MICROS);
            var resultat = remplacer(soireeId, catalogue, corps.lecteurId(), new AccuseCommande(envoi.envoiId(), recueLe, soireeId));
            socle.envois().enregistrer(envoi, resultat, recueLe);
            return resultat;
        }));
    }

    private ResultatDeCommande remplacer(UUID soireeId, List<Instrumentale> fiches, UUID lecteurId, AccuseCommande recu) {
        if (etatSoiree.estTerminee(soireeId)) {
            return new Refuse(409, "ETAT_INCOMPATIBLE", "Cette Soirée est terminée.");
        }
        if (fiches.stream().map(Instrumentale::instrumentaleId).distinct().count() != fiches.size()) {
            return new Refuse(422, "DONNEES_INVALIDES", "Les identifiants des Instrumentales doivent être distincts.");
        }
        List<Instrumentale> anciennes = persistance.instrumentales(soireeId);
        boolean fichesModifiees = !anciennes.equals(fiches);
        boolean lecteurModifie = !lecteurId.equals(persistance.lecteurDesigne(soireeId));
        Set<Destination> destinations = new HashSet<>();
        if (fichesModifiees || lecteurModifie) {
            persistance.declarer(soireeId, fiches, lecteurId);
            var active = ManchesDePassage.active(socle.depot().deLaSoiree(soireeId), mode());
            if (active.isPresent() && fichesModifiees) {
                var reglages = socle.json().readValue(active.get().getReglages(), ReglagesFreestyle.class);
                destinations.add(Destination.CONSOLE);
                if (!reglages.selectionDisponible(anciennes).equals(reglages.selectionDisponible(fiches))) {
                    destinations.add(Destination.TOUTES_LES_MANETTES);
                }
            }
            if (active.isPresent() && lecteurModifie) { destinations.add(Destination.SALLE); }
        }
        var resultat = new Accepte(recu, Set.copyOf(destinations), "instrumentales-modifiees");
        if (!destinations.isEmpty()) { socle.vues().publier(soireeId, resultat); }
        return resultat;
    }
}
