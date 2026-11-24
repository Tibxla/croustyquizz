package fr.croustyquizz.passage;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Les mêmes opérations HTTP pilotent les Passages, quel que soit leur Mode. */
@RestController
@RequestMapping("/api/soirees/{soireeId}/manches/{mancheId}")
public class PassagesControleur {
    private static final String CLE_IDEMPOTENCE = "Idempotency-Key";
    private final BiFunction<UUID, UUID, ServiceDeManche<?, ?>> services;

    @Autowired public PassagesControleur(RegistreServicesDeManche registre, DepotDeManches depot) {
        this((soireeId, mancheId) -> {
            var manche = depot.charger(mancheId);
            if (manche == null || !soireeId.equals(manche.getSoireeId()) || !PorteeDePassage.accepte(manche.getMode())) {
                throw introuvable().exception();
            }
            return registre.pourMode(manche.getMode());
        });
    }

    public PassagesControleur(BiFunction<UUID, UUID, ServiceDeManche<?, ?>> services) {
        this.services = services;
    }

    private ServiceDePassages<?, ?> service(UUID soireeId, UUID mancheId) {
        if (services.apply(soireeId, mancheId) instanceof ServiceDePassages<?, ?> passages) { return passages; }
        throw introuvable().exception();
    }

    private static Refuse introuvable() {
        return new Refuse(404, "RESSOURCE_INTROUVABLE", "Manche introuvable.");
    }

    @PostMapping("/passages")
    public ResponseEntity<?> inscrirePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsPassage.Inscription corps,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = racine(soireeId, mancheId) + "/passages";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(),
                new CorpsPassage.EmpreinteHttp(HttpMethod.POST.name(), corps));
        var service = service(soireeId, mancheId);
        if (corps.concerneLeKaraoke() != (service.mode() == ModeDeJeu.KARAOKE)) {
            CorpsPassage.invalide();
        }
        // Même empreinte pour les reçus Karaoké antérieurs au partage de la route.
        if (corps.concerneLeKaraoke()) {
            envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(), new CorpsPassage.EmpreinteHttp(HttpMethod.POST.name(),
                    java.util.Map.of("envoiId", corps.envoiId(), "chansonId", corps.chansonId())));
        }
        return ReponsesDeCommande.http(service.inscrire(mancheId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/desinscription")
    public ResponseEntity<?> desinscrirePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsPassage.DesinscriptionPassage corps,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = passage(soireeId, mancheId, passageId) + "/desinscription";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(),
                new CorpsPassage.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).desinscrirePassage(mancheId, passageId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/vote")
    public ResponseEntity<?> voterPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId, @Valid @RequestBody CorpsPassage.VotePassage corps, Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = passage(soireeId, mancheId, passageId) + "/vote";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(),
                new CorpsPassage.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).voterPassage(mancheId, passageId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/retrait")
    public ResponseEntity<?> retirerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsPassage.RetraitPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = passage(soireeId, mancheId, passageId) + "/retrait";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsPassage.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).retirerPassage(mancheId, passageId, envoi, corps), route);
    }
    @PutMapping("/passages/{passageId}/placement")
    public ResponseEntity<?> deplacerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsPassage.PlacementPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = passage(soireeId, mancheId, passageId) + "/placement";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsPassage.EmpreinteHttp(HttpMethod.PUT.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).deplacerPassage(mancheId, passageId, envoi, corps), route);
    }
    @PutMapping("/file-attente/priorite")
    public ResponseEntity<?> definirPrioritePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsPassage.PrioritePassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = racine(soireeId, mancheId) + "/file-attente/priorite";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsPassage.EmpreinteHttp(HttpMethod.PUT.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).definirPrioritePassage(mancheId, envoi, corps), route);
    }
    @PutMapping("/file-attente/limite")
    public ResponseEntity<?> reglerLimitePassages(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsPassage.LimitePassages corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = racine(soireeId, mancheId) + "/file-attente/limite";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsPassage.EmpreinteHttp(HttpMethod.PUT.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).reglerLimitePassages(mancheId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/appel")
    public ResponseEntity<?> appelerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsPassage.AppelPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = passage(soireeId, mancheId, passageId) + "/appel";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsPassage.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).appelerPassage(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/presence")
    public ResponseEntity<?> confirmerPresencePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsPassage.ConfirmationPresencePassage corps,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = passage(soireeId, mancheId, passageId) + "/presence";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(), new CorpsPassage.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service(soireeId, mancheId).confirmerPresencePassage(mancheId, passageId, envoi, corps), route);
    }

    private static String racine(UUID soireeId, UUID mancheId) {
        return "/api/soirees/" + soireeId + "/manches/" + mancheId;
    }
    private static String passage(UUID soireeId, UUID mancheId, UUID passageId) {
        return racine(soireeId, mancheId) + "/passages/" + passageId;
    }
}
