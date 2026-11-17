package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Les huit opérations publiques de la File d'attente du Karaoké. */
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
                new CorpsKaraoke.EmpreinteHttp("POST", corps));
        return ReponsesDeCommande.http(service.inscrirePassage(mancheId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/desinscription")
    public ResponseEntity<?> desinscrirePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.DesinscriptionPassage corps,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
        String route = racine(soireeId, mancheId) + "/passages/" + passageId + "/desinscription";
        var envoi = new EnvoiDeCommande(acteur, route, corps.envoiId(),
                new CorpsKaraoke.EmpreinteHttp("POST", corps));
        return ReponsesDeCommande.http(service.desinscrirePassage(mancheId, passageId, envoi, corps), route);
    }
    @PostMapping("/passages/{passageId}/retrait")
    public ResponseEntity<?> retirerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.RetraitPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = racine(soireeId, mancheId) + "/passages/" + passageId + "/retrait";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsKaraoke.EmpreinteHttp("POST", corps));
        return ReponsesDeCommande.http(service.retirerPassage(mancheId, passageId, envoi, corps), route);
    }
    @PutMapping("/passages/{passageId}/placement")
    public ResponseEntity<?> deplacerPassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @PathVariable UUID passageId,
            @Valid @RequestBody CorpsKaraoke.PlacementPassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = racine(soireeId, mancheId) + "/passages/" + passageId + "/placement";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsKaraoke.EmpreinteHttp("PUT", corps));
        return ReponsesDeCommande.http(service.deplacerPassage(mancheId, passageId, envoi, corps), route);
    }
    @PutMapping("/file-attente/priorite")
    public ResponseEntity<?> definirPrioritePassage(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
            @Valid @RequestBody CorpsKaraoke.PrioritePassage corps,
            @RequestHeader(CLE_IDEMPOTENCE) UUID envoiId,
            Acteur acteur) {
        AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
        String route = racine(soireeId, mancheId) + "/file-attente/priorite";
        var envoi = new EnvoiDeCommande(acteur, route, envoiId,
                new CorpsKaraoke.EmpreinteHttp("PUT", corps));
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
                new CorpsKaraoke.EmpreinteHttp("PUT", corps));
        return ReponsesDeCommande.http(service.reglerLimitePassages(mancheId, envoi, corps), route);
    }
    private static String racine(UUID soireeId, UUID mancheId) {
        return "/api/soirees/" + soireeId + "/manches/" + mancheId;
    }
    private static ResponseEntity<?> lecture(Object corps) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(corps);
    }
}
