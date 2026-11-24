import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cleVibration, texteAnnonce, texteCouplet, texteOrdre } from '../../src/main/resources/static/js/modes/freestyle/couplets.mjs';

const zoe = { joueurId: 'z', pseudo: 'Zoé' }, ines = { joueurId: 'i', pseudo: 'Inès' }, mael = { joueurId: 'm', pseudo: 'Maël' };
const couplets = (suivantAnnonce = false) => ({ mesuresParCouplet: 2, nombreCouplets: 4, ordre: [zoe, ines, mael],
	courant: { numero: 1, interprete: zoe, premiereMesure: 1, derniereMesure: 2 },
	suivant: { numero: 2, interprete: ines, premiereMesure: 3, derniereMesure: 4 }, suivantAnnonce });
const bloc = (monCoupletAnnonce, annonce = true) => ({ monCoupletAnnonce, courant: { etapeId: 'e', couplets: couplets(annonce) } });
const annonce = { source: 'evenement', type: 'couplet-annonce' };

test('la salle dit l’ordre tiré, le Couplet courant et l’Interprète annoncé', () => {
	assert.equal(texteOrdre(couplets()), 'Ordre des Couplets : Zoé · Inès · Maël');
	assert.equal(texteCouplet(couplets()), 'Couplet 1 sur 4 · mesures 1 à 2');
	assert.equal(texteAnnonce(couplets()), '');
	assert.equal(texteAnnonce(couplets(true)), 'À Inès dans une mesure');
	assert.equal(texteOrdre(null), '');
	assert.equal(texteAnnonce({ ...couplets(true), suivant: null }), '');
});

test('seule la Manette annoncée vibre, à l’événement d’annonce et jamais sur une capture', () => {
	assert.equal(cleVibration(bloc(true), annonce), 'e:2');
	assert.equal(cleVibration(bloc(false), annonce), null);
	assert.equal(cleVibration(bloc(true), { source: 'capture' }), null);
	assert.equal(cleVibration(bloc(true), { source: 'evenement', type: 'couplet-commence' }), null);
	assert.equal(cleVibration({ monCoupletAnnonce: false, courant: null }, annonce), null);
});
