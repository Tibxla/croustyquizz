package fr.croustyquizz.freestyle;

import static java.time.temporal.ChronoUnit.MICROS;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.FournisseurPartieCommune;
import java.util.Comparator;
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
    private final TransactionTemplate declaration;

    public ServiceFreestyle(FabriqueFreestyle fabrique, PersistanceFreestyleJpa persistance, SocleDeManche socle,
            PorteeDePassage portee, ParticipantsDePassage participants, PersistancePassageJpa passages,
            EtatDeSoiree etatSoiree, FournisseurPartieCommune commun) {
        super(fabrique, persistance, socle, portee, participants, passages);
        this.persistance = persistance;
        this.etatSoiree = etatSoiree;
        this.commun = commun;
        declaration = new TransactionTemplate(socle.transactions());
        declaration.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public UUID creer(UUID soireeId, Object reglages) {
        return socle.verrous().executer(soireeId, () -> super.creer(soireeId, reglages));
    }

    @Override public ManchePersistante preparer(UUID soireeId, Object reglages) {
        return lecture(soireeId, () -> {
            var valeurs = CorpsFreestyle.reglages(socle.json().valueToTree(reglages), socle.json());
            if (valeurs.selectionDisponible(persistance.instrumentales(soireeId)).size() != valeurs.instrumentaleIds().size()) {
                throw new Refuse(422, "INSTRUMENTALE_INDISPONIBLE",
                        "Une Instrumentale de la sélection n’est plus disponible.").exception();
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
                    DictionnaireFreestyleLocal.amorces(instantane.reglages().theme()), corps.invites(),
                    invitesPresents(soireeId, corps.invites())));
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

    public ResultatDeCommande declarer(EnvoiDeCommande envoi, List<Instrumentale> fiches) {
        UUID soireeId = envoi.acteur().soireeId();
        List<Instrumentale> catalogue = fiches.stream().sorted(Comparator.comparing(Instrumentale::instrumentaleId)).toList();
        return socle.verrous().executer(soireeId, () -> declaration.execute(statut -> {
            ResultatDeCommande recu = socle.envois().retrouver(envoi);
            if (recu != null) { return recu; }
            var recueLe = socle.horloge().maintenant().truncatedTo(MICROS);
            var resultat = remplacer(soireeId, catalogue, new AccuseCommande(envoi.envoiId(), recueLe, soireeId));
            socle.envois().enregistrer(envoi, resultat, recueLe);
            return resultat;
        }));
    }

    private ResultatDeCommande remplacer(UUID soireeId, List<Instrumentale> fiches, AccuseCommande recu) {
        if (etatSoiree.estTerminee(soireeId)) {
            return new Refuse(409, "ETAT_INCOMPATIBLE", "Cette Soirée est terminée.");
        }
        if (fiches.stream().map(Instrumentale::instrumentaleId).distinct().count() != fiches.size()) {
            return new Refuse(422, "DONNEES_INVALIDES", "Les identifiants des Instrumentales doivent être distincts.");
        }
        List<Instrumentale> anciennes = persistance.instrumentales(soireeId);
        Set<Destination> destinations = Set.of();
        if (!anciennes.equals(fiches)) {
            persistance.remplacerInstrumentales(soireeId, fiches);
            var active = socle.depot().deLaSoiree(soireeId).stream().filter(m -> m.getMode() == mode()
                    && (m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)).findFirst();
            if (active.isPresent()) {
                var reglages = socle.json().readValue(active.get().getReglages(), ReglagesFreestyle.class);
                destinations = reglages.selectionDisponible(anciennes).equals(reglages.selectionDisponible(fiches))
                        ? Set.of(Destination.CONSOLE) : Set.of(Destination.CONSOLE, Destination.TOUTES_LES_MANETTES);
            }
        }
        var resultat = new Accepte(recu, destinations, "instrumentales-modifiees");
        if (!destinations.isEmpty()) { socle.vues().publier(soireeId, resultat); }
        return resultat;
    }
}
