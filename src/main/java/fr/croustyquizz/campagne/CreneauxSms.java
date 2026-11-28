package fr.croustyquizz.campagne;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Set;

/** Calendrier national et heures civiles de Paris. */
final class CreneauxSms {
	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final LocalTime OUVERTURE = LocalTime.of(8, 0);
	private static final LocalTime FERMETURE = LocalTime.of(20, 0);
	private static final Set<MonthDay> FERIES_FIXES = Set.of(
			MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(5, 8), MonthDay.of(7, 14),
			MonthDay.of(8, 15), MonthDay.of(11, 1), MonthDay.of(11, 11), MonthDay.of(12, 25));
	private static final Set<Long> JOURS_APRES_PAQUES = Set.of(1L, 39L, 50L);

	private CreneauxSms() { }

	static Instant prochain(Instant demande) {
		var paris = demande.atZone(PARIS);
		LocalDate jour = paris.toLocalDate();
		LocalTime heure = paris.toLocalTime();
		if (autorise(jour) && !heure.isBefore(OUVERTURE) && heure.isBefore(FERMETURE)) {
			return demande;
		}
		if (!heure.isBefore(FERMETURE)) {
			jour = jour.plusDays(1);
		}
		while (!autorise(jour)) {
			jour = jour.plusDays(1);
		}
		return jour.atTime(OUVERTURE).atZone(PARIS).toInstant();
	}

	private static boolean autorise(LocalDate jour) {
		return jour.getDayOfWeek() != DayOfWeek.SUNDAY
				&& !FERIES_FIXES.contains(MonthDay.from(jour))
				&& !JOURS_APRES_PAQUES.contains(jour.toEpochDay() - paques(jour.getYear()).toEpochDay());
	}

	/** Comput grégorien de Meeus, Jones et Butcher. */
	private static LocalDate paques(int annee) {
		int cycleLunaire = annee % 19;
		int siecle = annee / 100;
		int anneeDuSiecle = annee % 100;
		int correctionLunaire = (siecle - (siecle + 8) / 25 + 1) / 3;
		int epacte = (19 * cycleLunaire + siecle - siecle / 4 - correctionLunaire + 15) % 30;
		int decalage = (32 + 2 * (siecle % 4) + 2 * (anneeDuSiecle / 4) - epacte - anneeDuSiecle % 4) % 7;
		int correction = (cycleLunaire + 11 * epacte + 22 * decalage) / 451;
		int date = epacte + decalage - 7 * correction + 114;
		return LocalDate.of(annee, date / 31, date % 31 + 1);
	}
}
