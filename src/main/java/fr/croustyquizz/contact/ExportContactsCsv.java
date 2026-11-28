package fr.croustyquizz.contact;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ExportContactsCsv {
	private static final String ENTETES = ligne("Prénom", "Nom", "Téléphone", "Consentement donné le", "Mention acceptée");

	private ExportContactsCsv() { }

	static String composer(List<ServiceContacts.ContactActif> contacts) {
		var csv = new StringBuilder(ENTETES);
		for (var contact : contacts) {
			csv.append(ligne(contact.prenom(), contact.nom(), contact.numero(), contact.consentiLe().toString(),
					contact.mention()));
		}
		return csv.toString();
	}

	private static String ligne(String... cellules) {
		return Stream.of(cellules).map(ExportContactsCsv::echapper).collect(Collectors.joining(",", "", "\r\n"));
	}

	private static String echapper(String valeur) {
		String debut = valeur.stripLeading();
		boolean formule = !debut.isEmpty() && "=+-@＝＋－＠".indexOf(debut.charAt(0)) >= 0;
		String protegee = formule ? "'" + valeur : valeur;
		return "\"" + protegee.replace("\"", "\"\"") + "\"";
	}
}
