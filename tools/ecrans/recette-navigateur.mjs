// Exécuter à la racine après node tools/ecrans/servir-recette.mjs :
// playwright-cli open
// playwright-cli run-code --filename=tools/ecrans/recette-navigateur.mjs
// Ce script simule seulement HTTP/STOMP ; les vrais HTML, CSS, modules et WebJar sont servis localement.
async page => {
	const base = 'http://127.0.0.1:18080';
	const soireeId = '00000001-0000-4000-8000-000000000000';
	const joueurId = '65c5618c-10e0-357e-8f8e-eb7295ba6c74';
	const manche = { id: '00000002-0000-4000-8000-000000000000', ordre: 0, mode: 'QUIZ', etat: 'EN_COURS',
		reglages: { nombreEtapes: 3, tempsParEtapeSecondes: 20 } };
	const initiale = {
		soiree: { id: soireeId, titre: 'Soirée de démonstration', format: 'SOLO', pilotage: 'MANUEL', etat: 'EN_COURS', codeAcces: 'DEMO01' },
		mancheActive: manche, modeEnCours: 'QUIZ', quiz: { phase: 'ATTENTE' },
		joueurs: [{ id: joueurId, pseudo: 'Joueur de démonstration', connecte: true }], classement: [], dernierBuzz: null,
	};
	let role;
	let vue;
	let revision;
	let connexion;
	let abonnement;
	const erreurs = [];
	page.on('pageerror', erreur => erreurs.push(erreur.message));
	const verifier = (condition, detail) => { if (!condition) throw new Error(detail); };
	await page.routeWebSocket('**/ws', socket => {
		connexion = socket;
		socket.onMessage(message => {
			const trame = String(message);
			if (trame.startsWith('CONNECT')) {
				verifier(trame.includes(`Authorization:Bearer demo:${role}:${soireeId}:${role === 'JOUEUR' ? joueurId : joueurId.replace('357e', '457e')}`), 'CONNECT authentifié');
				socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
			} else if (trame.startsWith('SUBSCRIBE')) {
				abonnement = /^id:(.*)$/m.exec(trame)[1];
			}
		});
	});
	await page.route('**/api/soirees/*/etat/*', async route => {
		const requete = route.request();
		verifier(requete.headers().authorization?.startsWith(`Bearer demo:`), 'Capture authentifiée');
		const id = /[?&]abonnementId=([^&]+)/.exec(requete.url())?.[1] ?? null;
		if (id) verifier(id === abonnement, 'SUBSCRIBE installé avant la capture');
		await route.fulfill({ json: { abonnementId: id, revision, heureServeur: '2026-11-16T20:00:00Z', vue } });
	});
	function publier(nouvelle, type) {
		vue = nouvelle; revision++;
		connexion.send(`MESSAGE\nsubscription:${abonnement}\nmessage-id:${revision}\ndestination:/test\n\n${JSON.stringify({ revision, type, soireeId, donnees: vue })}\0`);
	}
	const captures = [];
	for (const [ecran, type, tailles] of [
		['manette', 'JOUEUR', [[390, 844]]], ['console', 'ANIMATEUR', [[1440, 900], [1024, 768]]],
		['ecran', 'ECRAN', [[1920, 1080]]],
	]) {
		role = type; revision = 0;
		vue = { ...initiale, ...(ecran === 'console' ? { programme: [manche] } : {}),
			...(ecran === 'manette' ? { joueurId, eligiblePourMancheActive: true, derniereReponse: null, resultatPersonnel: null } : {}) };
		await page.goto(`${base}/test/identite.html?soiree=${soireeId}`);
		await page.locator('select').selectOption(type);
		// L'Animateur et l'Écran ont normalement un UUID aléatoire ; fixer le hasard reste une frontière de test.
		await page.evaluate(id => {
			const octets = id.replaceAll('-', '').match(/../g).map(h => parseInt(h, 16));
			crypto.getRandomValues = tableau => { tableau.set(octets); return tableau; };
		}, joueurId.replace('357e', '457e'));
		// Pour ces deux rôles, l'UUID est choisi par le formulaire ; utiliser celui réellement déposé.
		await page.locator('button[type=submit]').click();
		await page.waitForURL(`**/${ecran}.html?**`);
		await page.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
		verifier(await page.locator('[data-principale]').innerText().then(t => t.includes('Mode factice')), 'Module factice monté');
		for (const [width, height] of tailles) {
			await page.setViewportSize({ width, height });
			await page.evaluate(() => document.fonts.ready);
			verifier(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${ecran} sans débordement horizontal`);
			const chemin = `/tmp/croustyquizz-80-${ecran}-${width}.png`;
			await page.screenshot({ path: chemin, fullPage: true }); captures.push(chemin);
		}
		const question = { etapeId: manche.id, questionId: manche.id, numero: 1, total: 3,
			enonce: 'Question factice', propositions: [0, 1, 2, 3].map(i => ({ id: `${i}0000000-0000-4000-8000-000000000000`, position: i, texte: `Proposition ${i}` })) };
		publier({ ...vue, mancheActive: { ...manche, etat: 'EN_PAUSE' },
			quiz: { phase: 'EN_PAUSE', question, tempsRestantMs: 10000 } }, 'manche-en-pause');
		await page.waitForFunction(() => document.querySelector('[data-principale]').textContent.includes('EN_PAUSE'));
		connexion.close();
		await page.waitForFunction(() => document.querySelector('[data-connexion]').textContent.includes('Connexion perdue'));
		await page.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
		publier({ ...vue, modeEnCours: null, mancheActive: null, quiz: null }, 'manche-terminee');
		await page.waitForFunction(() => document.querySelector('[data-principale]').childElementCount === 0);
		verifier(await page.locator('[data-titre]').innerText() === 'Soirée de démonstration', 'Partie commune conservée');
	}
	const focusDialogue = await page.evaluate(async () => {
		const { ouvrirDialogue } = await import('/js/dialogue.mjs');
		const bouton = document.createElement('button');
		bouton.textContent = 'Ouvrir le dialogue'; document.body.append(bouton);
		const dialogue = document.createElement('dialog'); dialogue.className = 'dialogue';
		dialogue.setAttribute('aria-label', 'Confirmation de test');
		const fermer = document.createElement('button'); fermer.textContent = 'Fermer'; dialogue.append(fermer);
		document.body.append(dialogue); bouton.focus(); ouvrirDialogue(dialogue);
		const initial = document.activeElement === fermer && dialogue.matches(':modal');
		dialogue.close();
		await new Promise(resoudre => dialogue.addEventListener('close', resoudre, { once: true }));
		const retour = document.activeElement === bouton;
		bouton.remove(); dialogue.remove(); return initial && retour;
	});
	verifier(focusDialogue, 'Focus modal et retour au déclencheur');
	await page.setViewportSize({ width: 390, height: 844 });
	await page.goto(`${base}/test/identite.html?soiree=invalide`);
	await page.locator('button[type=submit]').click();
	await page.locator('[role=alert]').waitFor({ state: 'visible' });
	verifier(await page.locator('#soiree').getAttribute('aria-invalid') === 'true', 'Erreur de champ accessible');
	await page.screenshot({ path: '/tmp/croustyquizz-80-identite-390.png', fullPage: true });
	captures.push('/tmp/croustyquizz-80-identite-390.png');
	verifier(erreurs.length === 0, erreurs.join('\n'));
	return { resultat: 'Trois pages, identité, capture, événement, coupure, recapture et démontage vérifiés.', captures };
}
