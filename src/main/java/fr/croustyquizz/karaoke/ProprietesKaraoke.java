package fr.croustyquizz.karaoke;

import fr.croustyquizz.passage.EtatFileAttente;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Défauts de File et délais transmis aux écrans. */
@ConfigurationProperties("croustyquizz")
public record ProprietesKaraoke(Passage passage, Karaoke karaoke) {
    public record Passage(long limitePassagesEnAttente, long delaiAppelSecondes, long dureeVoteSecondes,
            long dureeAffichageMoyenneSecondes) {
        private static final long DUREE_MAX_SECONDES = 9007199254740L;

        public Passage {
            if (dureeVoteSecondes <= 0 || dureeVoteSecondes > DUREE_MAX_SECONDES) {
                throw new IllegalArgumentException("Durée du vote positive et représentable attendue.");
            }
            if (dureeAffichageMoyenneSecondes <= 0 || dureeAffichageMoyenneSecondes > DUREE_MAX_SECONDES) {
                throw new IllegalArgumentException("Durée d’affichage de la moyenne positive et représentable attendue.");
            }
            EtatFileAttente.verifierLimite(limitePassagesEnAttente);
            if (delaiAppelSecondes <= 0) { throw new IllegalArgumentException("Délai d'appel positif attendu."); }
        }
    }
    public record Karaoke(long delaiConsultationNavigateurMs, long delaiConfirmationNavigateurMs) {
        public Karaoke {
            if (delaiConsultationNavigateurMs <= 0 || delaiConfirmationNavigateurMs <= 0
                    || delaiConsultationNavigateurMs > 9007199254740991L || delaiConfirmationNavigateurMs > 9007199254740991L) {
                throw new IllegalArgumentException("Délais du navigateur positifs représentables attendus.");
            }
        }
    }
}
