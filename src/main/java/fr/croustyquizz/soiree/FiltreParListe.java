package fr.croustyquizz.soiree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Filtre de pseudos par liste de mots ({@code soiree/pseudos-interdits.txt}).
 * Avant de comparer, le pseudo est ramené à une forme simple : sans accent, en
 * minuscules, chiffres et symboles lus comme les lettres qu'ils imitent, lettres
 * répétées réduites à une seule. Un mot de la liste est refusé s'il apparaît
 * comme mot entier, ou s'il forme le pseudo entier une fois les séparateurs
 * retirés (« f.d.p ») ; un mot marqué d'une étoile est aussi cherché à
 * l'intérieur du pseudo. Les mots courts restent ainsi entiers, pour accepter
 * « Constance » ou « Monique ».
 */
@Component
class FiltreParListe implements FiltreDePseudos {

	static final String LISTE = "/soiree/pseudos-interdits.txt";

	private static final Pattern MARQUES = Pattern.compile("\\p{M}+");
	private static final Pattern SEPARATEURS = Pattern.compile("[^a-z]+");
	private static final Pattern REPETITIONS = Pattern.compile("([a-z])\\1+");

	/** Chiffres et symboles utilisés à la place des lettres. */
	private static final String IMITATIONS = "0134578@$!|€";
	private static final String LETTRES = "oieastbasiie";

	private final Set<String> motsEntiers = new HashSet<>();
	private final Set<String> fragments = new HashSet<>();

	FiltreParListe() {
		this(lireListe());
	}

	FiltreParListe(List<String> lignes) {
		for (String ligne : lignes) {
			String mot = ligne.strip();
			if (mot.isEmpty() || mot.startsWith("#")) {
				continue;
			}
			boolean fragment = mot.endsWith("*");
			String forme = forme(fragment ? mot.substring(0, mot.length() - 1) : mot);
			motsEntiers.add(forme);
			if (fragment) {
				fragments.add(forme);
			}
		}
	}

	@Override
	public boolean estAcceptable(String pseudo) {
		if (pseudo == null) {
			return false;
		}
		String forme = forme(pseudo);
		// Recollé puis réduit à nouveau : « c.o.n.n.a.r.d » donne des lettres doublées une fois les points retirés.
		String colle = REPETITIONS.matcher(forme.replace(" ", "")).replaceAll("$1");
		if (motsEntiers.contains(colle)) {
			return false;
		}
		if (Arrays.stream(forme.split(" ")).anyMatch(motsEntiers::contains)) {
			return false;
		}
		return fragments.stream().noneMatch(colle::contains);
	}

	/** Mots en lettres a à z séparés par une espace, sans accent ni lettre répétée. */
	static String forme(String texte) {
		String sansAccent = MARQUES.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("");
		StringBuilder lettres = new StringBuilder(sansAccent.length());
		for (char caractere : sansAccent.toLowerCase(Locale.ROOT).toCharArray()) {
			int imitation = IMITATIONS.indexOf(caractere);
			lettres.append(imitation >= 0 ? LETTRES.charAt(imitation) : caractere);
		}
		String mots = SEPARATEURS.matcher(lettres).replaceAll(" ").strip();
		return REPETITIONS.matcher(mots).replaceAll("$1");
	}

	private static List<String> lireListe() {
		try (InputStream flux = FiltreParListe.class.getResourceAsStream(LISTE)) {
			if (flux == null) {
				throw new IllegalStateException("Liste de pseudos interdits introuvable : " + LISTE);
			}
			return new BufferedReader(new InputStreamReader(flux, StandardCharsets.UTF_8)).lines().toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
