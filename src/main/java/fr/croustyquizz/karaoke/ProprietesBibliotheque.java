package fr.croustyquizz.karaoke;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Racine de la Bibliothèque de base ; les tests la font pointer sur un catalogue d'essai. */
@ConfigurationProperties("croustyquizz.bibliotheque")
public record ProprietesBibliotheque(@DefaultValue("classpath:static/bibliotheque/") String racine) {
    public ProprietesBibliotheque {
        if (racine == null || racine.isBlank()) { throw new IllegalArgumentException("Racine de bibliothèque requise"); }
        racine = racine.endsWith("/") ? racine : racine + "/";
    }
}
