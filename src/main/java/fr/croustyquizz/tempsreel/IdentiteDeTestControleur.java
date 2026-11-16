package fr.croustyquizz.tempsreel;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Page temporaire de #80, hors de static pour ne jamais contourner le profil par une URL directe. */
@RestController
@Profile({"dev", "recette"})
class IdentiteDeTestControleur {
	@GetMapping("/test/identite.html")
	ResponseEntity<Resource> identite() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML)
				.body(new ClassPathResource("demonstration/identite.html"));
	}
}
