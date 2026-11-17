export function preparerPlacement(passages, passageId, geste) {
	const cible = passages.find(p => p.passageId === passageId);
	if (!cible || cible.prioritaire) return null;
	const tour = passages.filter(p => p.tour === cible.tour && !p.prioritaire).sort((a, b) => a.position - b.position);
	const index = tour.findIndex(p => p.passageId === passageId);
	let ancre;
	if (geste === 'monter') {
		if (!index) return null;
		ancre = tour[index - 1];
	} else if (geste === 'descendre' || geste === 'fin-du-tour') {
		if (index === tour.length - 1) return null;
		ancre = geste === 'descendre' ? tour[index + 2] : null;
	} else return null;
	return { numeroModification: cible.numeroModification, tour: cible.tour,
		avant: ancre ? { passageId: ancre.passageId, numeroModification: ancre.numeroModification } : null };
}
