// Écran de salle sans débordement (#276), vérifié dans un vrai navigateur à 1280 × 720 et 1920 × 1080.
// node tools/ecrans/servir-recette.mjs
// playwright-cli open http://127.0.0.1:18080/index.html
// playwright-cli run-code --filename=tools/ecrans/salle-hauteur-navigateur.mjs
// Vraie coquille ecran.html, vraies feuilles et vrais modules du Karaoké et du Freestyle, montés avec un contexte
// factice : aucun serveur ni STOMP. Chaque étape (paroles longues avec crédit, appel, vote, Freestyle à plusieurs)
// doit tenir dans la fenêtre, sa zone principale, la colonne du mode et le talon entiers. Le script lève une erreur sinon.
async page => {
	const url = page.url();
	const base = url.startsWith('http://127.0.0.1:') ? new URL(url).origin : 'http://127.0.0.1:18080';
	const contexte = page.context();
	const coquille = (await (await contexte.request.get(`${base}/ecran.html`)).text())
		.replace('<script type="module" src="/js/page.mjs"></script>', '<script type="module" src="/__banc-salle.mjs"></script>')
		.replace(/<script defer src="\/webjars[^>]*><\/script>/, '');
	const banc = `
import { creerAudioSalle } from '/js/audio-salle.mjs';
const scenario = JSON.parse(new URLSearchParams(location.search).get('s'));
const $ = s => document.querySelector(s);
const ecrire = (s, t) => { $(s).textContent = t; };
ecrire('[data-titre]', 'Grande Soirée Karaoké du Jeudi'); ecrire('[data-resume]', 'En cours · Solo · Pilotage manuel');
ecrire('[data-manche]', scenario.manche); ecrire('[data-connexion]', 'En direct');
$('[data-attente]').hidden = true; $('[data-audio-salle]').hidden = false;
const joueurs = ['Léa', 'Bartholoméo Croustillant-Dupré', 'Max', 'Inès'];
ecrire('[data-nombre-joueurs]', 'Joueurs (4)');
$('[data-joueurs]').replaceChildren(...joueurs.map(j => Object.assign(document.createElement('li'), { textContent: j + ' · connecté' })));
ecrire('[data-buzz]', 'Dernier Buzz : Bartholoméo Croustillant-Dupré');
$('[data-talon]').hidden = $('[data-qr]').hidden = $('[data-adresse]').hidden = false;
$('[data-qr] img').src = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 4"><rect width="4" height="4" fill="#fff"/><rect width="1" height="1"/></svg>');
ecrire('[data-code]', 'DEMO01');
['croustyquizz-recette', '.onrender', '.com', '/rejoindre.html'].forEach((s, i) => {
	if (i) $('[data-adresse]').append(document.createElement('wbr'));
	$('[data-adresse]').append(Object.assign(document.createElement('span'), { textContent: s }));
});
if (scenario.credit) $('.salle__pied').insertBefore(Object.assign(document.createElement('span'), { textContent: scenario.credit }), $('.salle__pied').lastElementChild);
const lecteur = { debloquer: async () => {}, charger: async () => ({ dureeMs: 200000 }), lire: async () => {}, pause() {}, reprendre: async () => {},
	position: () => 0, positionner() {}, surFin: () => () => {}, surErreur: () => () => {}, detruire() {} };
const audioSalle = creerAudioSalle({ lecteur, lecteurId: 'lecteur', sonde: { charger: async () => ({ dureeMs: 2000 }), detruire() {} },
	coordonner: () => ({ activer() {}, signalerSonDeManche() {}, attendreSonDeManche: async () => true, detruire() {} }) });
const maintenant = Date.now();
const vue = JSON.parse(JSON.stringify(scenario.vue).replaceAll('"__MAINTENANT__"', JSON.stringify(new Date(maintenant).toISOString())));
const module = await import('/js/modes/' + scenario.mode + '/ecran.mjs');
module.monter({ principale: $('[data-principale]'), laterale: $('[data-laterale]') }, {
	soireeId: 's', acteur: { type: 'ECRAN', identifiant: 'e' }, maintenantServeur: () => maintenant,
	envoyer: () => new Promise(() => {}), resynchroniser() {}, surEtatConnexion: f => { f('synchronisee'); return () => {}; },
	audioSalle, signalerSonDeManche: async () => true, catalogueInstrumentales: { fichier: () => null, surChangement: () => () => {} },
}).afficher(vue, { source: 'capture', type: 'capture' });
document.body.dataset.pret = '1';`;
	await contexte.route(`${base}/__banc-salle.html*`, route => route.fulfill({ contentType: 'text/html', body: coquille }));
	await contexte.route(`${base}/__banc-salle.mjs`, route => route.fulfill({ contentType: 'text/javascript', body: banc }));

	const MAINTENANT = '__MAINTENANT__';
	const qui = (...noms) => noms.map((pseudo, i) => ({ joueurId: `j${i}`, pseudo }));
	const passage = (numero, noms, titre) => ({ passageId: `p${numero}`, numero, tour: 1, position: numero, numeroModification: 1,
		chanson: { chansonId: titre, titre, artiste: 'Artiste fictif', album: null, dureeMs: 180000, etatParoles: 'SYNCHRONISEES' },
		interpretes: qui(...noms), prioritaire: false, attente: { etat: 'ESTIMEE', dureeMs: 60000 } });
	const karaoke = bloc => ({ modeEnCours: 'KARAOKE', karaoke: { mancheId: 'm', etat: 'EN_COURS', courant: null, votes: [],
		configurationInterface: { longueurRechercheMax: 120, delaiConsultationNavigateurMs: 8000, delaiConfirmationNavigateurMs: 10000, dureeAffichageMoyenneMs: 10000 },
		mesPassagesEnAttente: [], passagesEnAttente: [], nombreAutresPassages: 2,
		prochainsPassages: [passage(2, ['Bartholoméo Croustillant-Dupré'], 'Un titre de démonstration nettement trop long'), passage(3, ['Max'], 'Colle forte'), passage(4, ['Inès'], 'Les files d’attente')],
		...bloc } });
	const courant = etat => ({ ...passage(1, ['Léa', 'Bartholoméo Croustillant-Dupré'], 'Le Dernier Métro'), etat, etapeId: 'e',
		calculeLe: MAINTENANT, delaiAppelRestantMs: 27000, positionChronologiqueMs: 5000, finPrevueLe: null });
	const paroles = enCours => ({ parolesId: 'v', dureeMs: 180000, lignes: ['Une ligne déjà chantée qui passe elle aussi sur deux lignes', enCours,
		'La ligne suivante, assez longue pour se replier sur deux lignes', 'Encore une', 'Et une autre'].map((texte, i) => ({ texte, positionMs: i * 4000 })) });
	const lecture = { lectureId: 'l', source: 'PAROLES_SEULES', decalageMs: 0 };
	const vote = (numero, champs = {}) => ({ ...passage(numero, ['Léa', 'Bartholoméo Croustillant-Dupré'], 'Le Dernier Métro'), etat: 'OUVERT',
		delaiRestantMs: 26000, calculeLe: MAINTENANT, clotureLe: null, nombreVotes: 2, suivantDemarre: false, moyenneDixiemes: null, ...champs });
	const instrumentale = { instrumentaleId: 'i', titre: 'Instrumentale de démonstration', tempoBpm: 90, departBeatMs: 0, dureeMs: 120000 };
	const trio = qui('Joueur de démonstration 1', 'Joueur de démonstration 2', 'Joueur de démonstration 3');
	const grille = { nombreMesures: 8, mots: ['lumière', null, 'décision', 'animation', 'rivière', null, 'poussière', 'minuit']
		.map((mot, i) => ({ mesure: i + 1, temps: 3, positionMs: 1250 + 2667 * i, famille: i % 2 ? 'B' : 'A', mot })) };
	const courantFreestyle = (etat, couplets = null) => ({ passageId: 'pf', numero: 1, numeroModification: 1, instrumentale, interpretes: trio,
		etapeId: 'ef', etat, delaiAppelRestantMs: 27000, demarreLe: MAINTENANT, finPrevueLe: null, positionChronologiqueMs: 8000, calculeLe: MAINTENANT, couplets });
	const freestyle = bloc => ({ modeEnCours: 'FREESTYLE', freestyle: { mancheId: 'f', etat: 'EN_COURS', instrumentales: [instrumentale], votes: [],
		passagesEnAttente: [{ passageId: 'q', numero: 2, numeroModification: 1, instrumentale, interpretes: qui('Inès'), position: 1 }],
		nombreEnAttente: 1, lecteurDesigneId: 'autre', grille: null, courant: null, ...bloc } });
	const couplets = { mesuresParCouplet: 2, nombreCouplets: 4, ordre: trio, courant: { numero: 2, interprete: trio[1], premiereMesure: 3, derniereMesure: 4 },
		suivant: { numero: 3, interprete: trio[2], premiereMesure: 5, derniereMesure: 6 }, suivantAnnonce: true };
	const scenarios = {
		'karaoke-ligne-longue': { mode: 'karaoke', manche: 'Karaoké · Manche 1 · En cours', attendu: '.paroles__ligne--en-cours',
			vue: karaoke({ courant: courant('EN_COURS'), paroles: paroles('Fi your time up, a mi the man a mind up'), lecture,
				votes: [vote(9, { etat: 'CLOS', moyenneDixiemes: 43, nombreVotes: 7, clotureLe: MAINTENANT })] }) },
		'karaoke-ligne-tres-longue-credit': { mode: 'karaoke', manche: 'Karaoké · Manche 1 · En cours', attendu: '.paroles__ligne--en-cours',
			credit: 'Chanson : « Le Dernier Métro », Les Néons · CC BY 4.0 · Bibliothèque de base, morceaux libres de démonstration',
			vue: karaoke({ courant: courant('EN_COURS'), lecture,
				paroles: paroles('Une ligne chantée vraiment très longue qui ne tient pas sur deux lignes, même à ce corps-là') }) },
		'karaoke-deux-votes-en-colonne': { mode: 'karaoke', manche: 'Karaoké · Manche 1 · En cours', attendu: '.paroles__ligne--en-cours',
			vue: karaoke({ courant: courant('EN_COURS'), paroles: paroles('Fi your time up, a mi the man a mind up'), lecture,
				votes: [vote(9), vote(8, { nombreVotes: 0 })] }) },
		'karaoke-appel': { mode: 'karaoke', manche: 'Karaoké · Manche 1 · En cours', attendu: '.passage-salle--appel', vue: karaoke({ courant: courant('APPELE') }) },
		'karaoke-vote': { mode: 'karaoke', manche: 'Karaoké · Manche 1 · En cours', attendu: '.passage-salle--vote',
			vue: karaoke({ courant: courant('TERMINE'), votes: [vote(1), vote(8, { nombreVotes: 0 })] }) },
		'freestyle-a-plusieurs': { mode: 'freestyle', manche: 'Freestyle · Manche 2 · En cours', attendu: '.freestyle__grille',
			vue: freestyle({ courant: courantFreestyle('EN_COURS', couplets), grille }) },
		'freestyle-vote': { mode: 'freestyle', manche: 'Freestyle · Manche 2 · En cours', attendu: '.passage-salle--vote',
			vue: freestyle({ courant: courantFreestyle('TERMINE'), votes: [{ ...courantFreestyle('TERMINE'), etat: 'OUVERT', delaiRestantMs: 26000,
				calculeLe: MAINTENANT, clotureLe: null, scrutin: 'MEILLEUR_INTERPRETE', nombreVotes: 1, voix: null }] }) },
	};
	const defauts = [], mesures = [];
	const essai = await contexte.newPage();
	try {
		for (const [nom, scenario] of Object.entries(scenarios)) {
			for (const [width, height] of [[1280, 720], [1920, 1080]]) {
				await essai.setViewportSize({ width, height });
				await essai.goto(`${base}/__banc-salle.html?s=${encodeURIComponent(JSON.stringify(scenario))}`);
				await essai.waitForSelector('body[data-pret]');
				await essai.waitForTimeout(400);
				const mesure = await essai.evaluate(attendu => {
					const vh = innerHeight;
					// Entier : dans la fenêtre et jamais coupé par un ancêtre qui masque son contenu.
					const entier = element => {
						const r = element.getBoundingClientRect();
						let haut = r.top, bas = r.bottom;
						for (let n = element.parentElement; n; n = n.parentElement) {
							if (getComputedStyle(n).overflowY === 'visible') continue;
							const p = n.getBoundingClientRect();
							haut = Math.max(haut, p.top); bas = Math.min(bas, p.bottom);
						}
						return r.height > 0 && r.top >= -0.5 && r.bottom <= vh + 0.5 && bas - haut >= r.height - 1;
					};
					const coupes = [attendu, '[data-talon]', '[data-qr]', '[data-code]', '[data-adresse]', '.salle__pied', '[data-choisir-dossier]']
						.filter(selecteur => { const element = document.querySelector(selecteur); return !element || !entier(element); });
					// La colonne du mode ne doit rien couper non plus : rouleau et bandeaux de vote entiers.
					const laterale = document.querySelector('[data-laterale]');
					if (laterale.scrollHeight > laterale.clientHeight + 1) coupes.push('[data-laterale]');
					return { hauteurPage: document.documentElement.scrollHeight, coupes };
				}, scenario.attendu);
				mesures.push(`${nom} ${width}×${height} : page ${mesure.hauteurPage} px`);
				if (mesure.hauteurPage !== height) defauts.push(`${nom} ${width}×${height} : la page mesure ${mesure.hauteurPage} px`);
				if (mesure.coupes.length) defauts.push(`${nom} ${width}×${height} : coupé ou absent ${mesure.coupes.join(', ')}`);
			}
		}
	} finally {
		await essai.close();
	}
	if (defauts.length) throw new Error(`Écran de salle :\n${defauts.join('\n')}`);
	return mesures.join('\n');
}
