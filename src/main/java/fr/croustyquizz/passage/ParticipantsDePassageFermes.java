package fr.croustyquizz.passage;

import fr.croustyquizz.manche.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Accès fermé en attendant les domaines de production. */
@Component
@Profile("!dev & !recette")
public final class ParticipantsDePassageFermes implements ParticipantsDePassage {
    @Override public boolean estPresent(UUID soireeId, UUID joueurId) { throw refus(); }
    @Override public String pseudo(UUID soireeId, UUID joueurId) { throw refus(); }
    private RuntimeException refus() {
        return new Refuse(404, "RESSOURCE_INTROUVABLE", "Ressource introuvable.").exception();
    }
}
