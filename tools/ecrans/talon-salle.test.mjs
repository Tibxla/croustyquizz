import test from 'node:test';
import assert from 'node:assert/strict';
import { monterTalonSalle } from '../../src/main/resources/static/js/talon-salle.mjs';
import { creerTransport } from '../../src/main/resources/static/js/transport.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const soireeId = '00000001-0000-4000-8000-000000000000';
const ouverte = { etat: 'OUVERTE', codeAcces: 'K7PQ2M' };
const adressePublique = 'https://quiz.exemple.test:9443/animations/rejoindre.html';
const imageSvg = () => new Response('<svg xmlns="http://www.w3.org/2000/svg"/>', {
	headers: { 'Content-Type': 'image/svg+xml', 'Adresse-Manette': adressePublique },
});

function installerTalon(requete, jeton = 'cle-opaque') {
	const document = creerDocument();
	const talon = document.createElement('div');
	const qr = document.createElement('div');
	const image = document.createElement('img');
	const code = document.createElement('span');
	const adresse = document.createElement('span');
	for (const [element, nom] of [[qr, 'qr'], [code, 'code'], [adresse, 'adresse']]) {
		element.setAttribute(`data-${nom}`, '');
	}
	talon.hidden = qr.hidden = adresse.hidden = true;
	image.decode = async () => {};
	qr.append(image);
	talon.append(qr, code, adresse);
	const requetes = [];
	const transport = creerTransport({ jeton }, {
		adresse: `https://croustyquizz-recette.onrender.com:8443/ecran.html?soiree=${soireeId}`,
		requete: (url, options) => {
			requetes.push({ url, options });
			return requete();
		},
	});
	const images = [];
	const adressesRevoquees = [];
	const affichage = monterTalonSalle(talon, { soireeId, chargerQrCode: transport.chargerQrCode }, {
		url: {
			createObjectURL(blob) { images.push(blob); return `blob:qr-${images.length}`; },
			revokeObjectURL(adresse) { adressesRevoquees.push(adresse); },
		},
	});
	return { affichage, talon, qr, image, code, adresse, requetes, images, adressesRevoquees };
}

test('le talon charge le QR code avec la clé d’Écran et l’affiche sans le recharger à chaque vue', async () => {
	const { affichage, talon, qr, image, code, requetes, images } = installerTalon(imageSvg);
	await affichage.afficher(ouverte);
	await affichage.afficher({ ...ouverte, etat: 'EN_COURS' });
	assert.equal(requetes.length, 1);
	assert.equal(requetes[0].url.pathname, `/api/soirees/${soireeId}/qrcode`);
	assert.equal(requetes[0].url.search, '');
	assert.equal(requetes[0].options.method, 'GET');
	assert.equal(requetes[0].options.headers.Authorization, 'Bearer cle-opaque');
	assert.equal(requetes[0].options.cache, 'no-store');
	assert.equal(images[0].type, 'image/svg+xml');
	assert.equal(image.getAttribute('src'), 'blob:qr-1');
	assert.equal(talon.hidden, false);
	assert.equal(qr.hidden, false);
	assert.equal(code.textContent, 'K7PQ2M');
});

test('le talon affiche l’adresse publique du serveur avec des coupures dans l’hôte et le chemin', async () => {
	const { affichage, adresse } = installerTalon(imageSvg);
	assert.equal(adresse.hidden, true);
	await affichage.afficher(ouverte);
	assert.equal(adresse.hidden, false);
	assert.equal(adresse.textContent, adressePublique);
	assert.deepEqual(adresse.children.map(element => [element.tagName, element.textContent]), [
		['SPAN', 'https://quiz'], ['WBR', ''], ['SPAN', '.exemple'], ['WBR', ''], ['SPAN', '.test:9443'],
		['WBR', ''], ['SPAN', '/animations'], ['WBR', ''], ['SPAN', '/rejoindre.html'],
	]);
});

test('un refus ou une réponse inutilisable laisse le code visible et attend un nouveau code pour réessayer', async () => {
	const echecs = [
		() => new Response(null, { status: 403 }),
		() => new Response('<html>Proxy indisponible</html>', { headers: { 'Content-Type': 'text/html' } }),
		() => new Response('<svg/>', { headers: { 'Content-Type': 'image/svg+xml' } }),
	];
	for (const echouer of echecs) {
		const reponses = [echouer, imageSvg];
		const { affichage, talon, qr, adresse, code, requetes, images } = installerTalon(() => reponses.shift()());
		await affichage.afficher(ouverte);
		assert.equal(requetes.length, 1);
		assert.equal(images.length, 0);
		assert.equal(talon.hidden, false);
		assert.equal(code.textContent, 'K7PQ2M');
		assert.equal(qr.hidden, true);
		assert.equal(adresse.hidden, true);
		await affichage.afficher({ ...ouverte, etat: 'EN_COURS' });
		assert.equal(requetes.length, 1);
		await affichage.afficher({ ...ouverte, codeAcces: 'ABC234' });
		assert.equal(requetes.length, 2);
		assert.equal(qr.hidden, false);
	}
});

test('une panne réseau autorise un nouvel essai à la vue suivante', async () => {
	const reponses = [async () => { throw new TypeError('Hors ligne'); }, imageSvg];
	const { affichage, qr, code, requetes } = installerTalon(() => reponses.shift()());
	await affichage.afficher(ouverte);
	assert.equal(qr.hidden, true);
	assert.equal(code.textContent, 'K7PQ2M');
	await affichage.afficher(ouverte);
	assert.equal(requetes.length, 2);
	assert.equal(qr.hidden, false);
});

test('un Écran de démonstration affiche le code d’accès sans requête de QR code', async () => {
	const jeton = `demo:ECRAN:${soireeId}:00000002-0000-4000-8000-000000000000`;
	const { affichage, talon, qr, code, requetes } = installerTalon(imageSvg, jeton);
	await affichage.afficher(ouverte);
	await affichage.afficher({ ...ouverte, etat: 'EN_COURS' });
	assert.equal(requetes.length, 0);
	assert.equal(talon.hidden, false);
	assert.equal(code.textContent, 'K7PQ2M');
	assert.equal(qr.hidden, true);
});

test('une image illisible libère son URL et attend un nouveau code', async () => {
	const { affichage, qr, image, requetes, adressesRevoquees } = installerTalon(imageSvg);
	image.decode = async () => { throw new Error('Image illisible'); };
	await affichage.afficher(ouverte);
	assert.equal(qr.hidden, true);
	assert.equal(image.getAttribute('src'), null);
	assert.deepEqual(adressesRevoquees, ['blob:qr-1']);
	image.decode = async () => {};
	await affichage.afficher(ouverte);
	assert.equal(requetes.length, 1);
	await affichage.afficher({ ...ouverte, codeAcces: 'ABC234' });
	assert.equal(qr.hidden, false);
	assert.equal(image.getAttribute('src'), 'blob:qr-2');
	assert.deepEqual(adressesRevoquees, ['blob:qr-1']);
});

test('le remplacement du code et le démontage révoquent les URL des QR codes retirés', async () => {
	const { affichage, qr, image, code, adressesRevoquees } = installerTalon(imageSvg);
	await affichage.afficher(ouverte);
	await affichage.afficher({ ...ouverte, codeAcces: 'ABC234' });
	assert.deepEqual(adressesRevoquees, ['blob:qr-1']);
	assert.equal(code.textContent, 'ABC234');
	assert.equal(image.getAttribute('src'), 'blob:qr-2');
	affichage.demonter();
	assert.deepEqual(adressesRevoquees, ['blob:qr-1', 'blob:qr-2']);
	assert.equal(qr.hidden, true);
	assert.equal(image.getAttribute('src'), null);
});

test('la fin de Soirée annule le chargement et une réponse tardive ne réaffiche pas le QR code', async () => {
	let livrer;
	const { affichage, talon, qr, adresse, requetes, images } = installerTalon(() => new Promise(resolve => { livrer = resolve; }));
	await affichage.afficher({ ...ouverte, etat: 'PREPARATION' });
	assert.equal(talon.hidden, true);
	await affichage.afficher({ ...ouverte, codeAcces: null });
	assert.equal(talon.hidden, true);
	assert.equal(requetes.length, 0);
	const chargement = affichage.afficher(ouverte);
	await affichage.afficher(ouverte);
	assert.equal(requetes.length, 1);
	await affichage.afficher({ ...ouverte, etat: 'TERMINEE' });
	assert.equal(requetes[0].options.signal.aborted, true);
	livrer(imageSvg());
	await chargement;
	assert.equal(talon.hidden, true);
	assert.equal(qr.hidden, true);
	assert.equal(adresse.hidden, true);
	assert.equal(images.length, 0);
});
