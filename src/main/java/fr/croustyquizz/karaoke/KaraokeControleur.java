package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Opérations HTTP de la File d’attente et du Passage Karaoké. */
@RestController
@RequestMapping("/api/soirees/{soireeId}/manches/{mancheId}")
public class KaraokeControleur {
    private static final String CLE_IDEMPOTENCE = "Idempotency-Key";
    private final ServiceKaraoke service;
    public KaraokeControleur(ServiceKaraoke service) { this.service = service; }
    @GetMapping("/chansons")
    public ResponseEntity<?> rechercherChansons(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @RequestParam String recherche, @RequestParam UUID rechercheId, Acteur acteur) {
        return lecture(service.rechercherChansons(acteur, soireeId, mancheId, recherche, rechercheId));
    }
    @GetMapping("/chansons/{chansonId}")
    public ResponseEntity<?> consulterChanson(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable String chansonId, Acteur acteur) {
        return lecture(service.consulterChanson(acteur, soireeId, mancheId, chansonId));
    }
    @PostMapping("/passages")
    public ResponseEntity<?> inscrirePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsKaraoke.InscriptionPassage corps,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = racine(soireeId, mancheId) + "/passages";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(),
                new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.inscrirePassage(mancheId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/desinscription")
    public ResponseEntity<?> desinscrirePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.DesinscriptionPassage corps,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = passage(soireeId, mancheId, passageId) + "/desinscription";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(),
                new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.desinscrirePassage(mancheId, passageId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/vote")
    public ResponseEntity<?> voterPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId, @Valid @RequestBody CorpsKaraoke.VotePassage corps, Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = passage(soireeId, mancheId, passageId) + "/vote";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(),
                new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.voterPassage(mancheId, passageId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/retrait")
    public ResponseEntity<?> retirerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.RetraitPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = passage(soireeId, mancheId, passageId) + "/retrait";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.retirerPassage(mancheId, passageId, envoi, corps), route);
    }
    @PutMapping("/passages/{passageId}/placement")
    public ResponseEntity<?> deplacerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.PlacementPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, null); // Animateur ou Joueur délégué : le service tranche (#67).
        String route = passage(soireeId, mancheId, passageId) + "/placement";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsKaraoke.EmpreinteHttp(HttpMethod.PUT.name(), corps));
        return ReponsesDeCommande.http(service.deplacerPassage(mancheId, passageId, envoi, corps), route);
    }
    @PutMapping("/file-attente/priorite")
    public ResponseEntity<?> definirPrioritePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsKaraoke.PrioritePassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, null); // Animateur ou Joueur délégué : le service tranche (#67).
        String route = racine(soireeId, mancheId) + "/file-attente/priorite";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsKaraoke.EmpreinteHttp(HttpMethod.PUT.name(), corps));
        return ReponsesDeCommande.http(service.definirPrioritePassage(mancheId, envoi, corps), route);
    }
    @PutMapping("/file-attente/limite")
    public ResponseEntity<?> reglerLimitePassages(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsKaraoke.LimitePassages corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = racine(soireeId, mancheId) + "/file-attente/limite";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsKaraoke.EmpreinteHttp(HttpMethod.PUT.name(), corps));
        return ReponsesDeCommande.http(service.reglerLimitePassages(mancheId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/appel")
    public ResponseEntity<?> appelerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.AppelPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, null); // Animateur ou Joueur délégué : le service tranche (#67).
        String route = passage(soireeId, mancheId, passageId) + "/appel";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.appelerPassage(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/presence")
    public ResponseEntity<?> confirmerPresencePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.ConfirmationPresencePassage corps,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = passage(soireeId, mancheId, passageId) + "/presence";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(), new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.confirmerPresencePassage(mancheId, passageId, envoi, corps), route);
    }

    @PutMapping("/lecteur")
    public ResponseEntity<?> designerLecteurChanson(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsKaraoke.DesignationLecteur corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
        String route = racine(soireeId, mancheId) + "/lecteur";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.PUT.name(), corps));
        return ReponsesDeCommande.http(service.designerLecteurChanson(mancheId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/lecture")
    public ResponseEntity<?> declarerLectureChanson(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.DeclarationLectureChanson corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
        String route = passage(soireeId, mancheId, passageId) + "/lecture";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.declarerLectureChanson(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/fin-lecture")
    public ResponseEntity<?> signalerFinLectureChanson(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.SignalFinLectureChanson corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
        String route = passage(soireeId, mancheId, passageId) + "/fin-lecture";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.signalerFinLectureChanson(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/erreur-lecture")
    public ResponseEntity<?> signalerErreurLectureChanson(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.SignalErreurLectureChanson corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
        String route = passage(soireeId, mancheId, passageId) + "/erreur-lecture";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.signalerErreurLectureChanson(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/paroles-seules")
    public ResponseEntity<?> reprendreEnParolesSeules(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.RepriseParolesSeules corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = passage(soireeId, mancheId, passageId) + "/paroles-seules";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.reprendreEnParolesSeules(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/calage/debut")
    public ResponseEntity<?> marquerDebutParoles(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.MarqueDebutParoles corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = passage(soireeId, mancheId, passageId) + "/calage/debut";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.marquerDebutParoles(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/calage/correction")
    public ResponseEntity<?> corrigerCalage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.CorrectionCalage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = passage(soireeId, mancheId, passageId) + "/calage/correction";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.corrigerCalage(mancheId, passageId, envoi, corps), route);
    }

    @PostMapping("/passages/{passageId}/calage/position")
    public ResponseEntity<?> releverPositionLecture(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.RelevePositionLecture corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
        String route = passage(soireeId, mancheId, passageId) + "/calage/position";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId, new CorpsKaraoke.EmpreinteHttp(HttpMethod.POST.name(), corps));
        return ReponsesDeCommande.http(service.releverPositionLecture(mancheId, passageId, envoi, corps), route);
    }

    private static String racine(UUID soireeId, UUID mancheId) {
        return "/api/soirees/" + soireeId + "/manches/" + mancheId;
    }
    private static String passage(UUID soireeId, UUID mancheId, UUID passageId) {
        return racine(soireeId, mancheId) + "/passages/" + passageId;
    }
    private static ResponseEntity<?> lecture(Object corps) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(corps);
    }
}
