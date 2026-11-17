// node tools/ecrans/servir-recette.mjs
// playwright-cli open
// playwright-cli run-code --filename=tools/ecrans/karaoke-recette-navigateur.mjs
// Vrais HTML/CSS/modules/WebJar, frontières HTTP et STOMP simulées, aucun accès externe.
async page => {
	page = await page.context().newPage();
	const base = 'http://127.0.0.1:18080';
	const soireeId = '00000001-0000-4000-8000-000000000000';
	const mancheId = '00000002-0000-4000-8000-000000000000';
	const ids = [3, 4, 5].map(n => `0000000${n}-0000-4000-8000-000000000000`);
	const chanson = { chansonId: 'chanson-demonstration', titre: 'Le Dernier Métro', artiste: 'Les Néons', album: 'Démonstration', dureeMs: 208000, etatParoles: 'SYNCHRONISEES' };
	const joueurs = ids.map((id, i) => ({ id, pseudo: ['Jo', 'Léa', 'Sam'][i], connecte: true }));
	let passages = [12, 13, 14, 15].map((numero, i) => ({ passageId: `000000${numero}-0000-4000-8000-000000000000`,
		numero, numeroModification: 0, position: i + 1, tour: i === 3 ? 2 : 1, prioritaire: i === 2,
		chanson: { ...chanson, chansonId: `demo-${numero}` }, interpretes: [{ joueurId: ids[i % 3], pseudo: joueurs[i % 3].pseudo }],
		attente: { etat: 'ESTIMEE', dureeMs: i * 208000, calculeeLe: '2026-11-17T20:00:00Z' } }));
	let limite = 3, terminee = false, confirmationRetardee, perdreConfirmation = true;
	const clients = [], erreurs = [], captures = [];
	const verifier = (condition, texte) => { if (!condition) throw new Error(texte); };
	function accuse(cle, action, passage) {
		const commun = { envoiId: cle, soireeId, mancheId, action, recueLe: '2026-11-17T20:00:00Z' };
		if (action === 'REGLER_LIMITE') return { ...commun, passage: null };
		if (action === 'DEFINIR_PRIORITE') return { ...commun, passage: { passageId: passage.passageId, numeroModification: passage.numeroModification } };
		return { ...commun, passageId: passage.passageId, numeroModification: passage.numeroModification,
			etatApres: ['RETIRER', 'DESINSCRIRE'].includes(action) ? 'RETIRE' : 'EN_ATTENTE' };
	}
	function projection(client) {
		const commun = { soiree: { id: soireeId, titre: 'Soirée de démonstration', format: 'SOLO', pilotage: 'MANUEL', etat: 'EN_COURS', codeAcces: 'DEMO01' },
			mancheActive: terminee ? null : { id: mancheId, ordre: 0, mode: 'KARAOKE', etat: 'EN_COURS' }, modeEnCours: terminee ? null : 'KARAOKE',
			joueurs, classement: [], dernierBuzz: null };
		const personnels = passages.filter(p => p.interpretes.some(i => i.joueurId === client.identifiant));
		const bloc = { mancheId, etat: 'EN_COURS', courant: null, limitePassagesEnAttente: limite, nombreEnAttente: passages.length, tourOuvert: 1,
			passagePrioritaireId: passages.find(p => p.prioritaire)?.passageId ?? null,
			configurationInterface: { longueurRechercheMax: 120, delaiConsultationNavigateurMs: 8000, delaiConfirmationNavigateurMs: 100 } };
		if (client.type === 'ANIMATEUR') return { ...commun, programme: [commun.mancheActive].filter(Boolean), karaoke: terminee ? null :
			{ ...bloc, passagesEnAttente: passages, peutGererFile: true, peutReglerLimite: true, peutRechercher: true } };
		const suite = { prochainsPassages: passages.slice(0, 3), nombreAutresPassages: Math.max(0, passages.length - 3) };
		if (client.type === 'ECRAN') return { ...commun, karaoke: terminee ? null : { ...bloc, ...suite } };
		return { ...commun, joueurId: client.identifiant, eligiblePourMancheActive: true, derniereReponse: null, resultatPersonnel: null,
			karaoke: terminee ? null : { ...bloc, ...suite, mesPassagesEnAttente: personnels, nombrePersonnelEnAttente: personnels.length,
				peutRechercher: true, peutInscrire: personnels.length < limite, motifInscriptionRefusee: personnels.length < limite ? null : 'LIMITE_ATTEINTE' } };
	}
	function publier(type = 'file-attente-modifiee') {
		for (const c of clients) if (c.socket && c.abonnement) {
			c.revision++;
			c.socket.send(`MESSAGE\nsubscription:${c.abonnement}\nmessage-id:${c.revision}\ndestination:/test\n\n${JSON.stringify({ revision: c.revision, type, soireeId, donnees: projection(c) })}\0`);
		}
	}
	async function installer(onglet, type, identifiant, ecran) {
		const c = { onglet, type, identifiant, revision: 0, captures: 0 }; clients.push(c);
		onglet.on('pageerror', e => erreurs.push(e.message));
		await onglet.route('**/*', async route => {
			if (!route.request().url().startsWith(base + '/')) { await route.abort(); throw new Error('Accès externe interdit dans la recette.'); }
			await route.continue();
		});
		await onglet.routeWebSocket('**/ws', socket => {
			c.socket = socket;
			socket.onMessage(message => {
				const trame = String(message);
				if (trame.startsWith('CONNECT')) socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
				if (trame.startsWith('SUBSCRIBE')) c.abonnement = /^id:(.*)$/m.exec(trame)[1];
			});
		});
		await onglet.route('**/api/**', async route => {
			const requete = route.request(), adresse = requete.url(), chemin = adresse.split('?')[0].slice(base.length);
			const parametre = nom => { const valeur = new RegExp(`[?&]${nom}=([^&]*)`).exec(adresse)?.[1]; return valeur ? decodeURIComponent(valeur.replace(/\+/g, ' ')) : null; };
			verifier(requete.headers().authorization === `Bearer demo:${type}:${soireeId}:${identifiant}`, 'Identité HTTP sur chaque requête');
			if (chemin.includes('/etat/')) {
				c.captures++;
				await route.fulfill({ json: { abonnementId: parametre('abonnementId'), revision: c.revision, heureServeur: '2026-11-17T20:00:00Z', vue: projection(c) } }); return;
			}
			if (requete.method() === 'GET') {
				if (parametre('recherche') === 'panne') {
					await route.fulfill({ status: 503, json: { type: 'about:blank', title: 'Source indisponible', status: 503, code: 'SOURCE_PAROLES_INDISPONIBLE', detail: 'La recherche est indisponible.' } }); return;
				}
				await route.fulfill({ json: chemin.endsWith('/chansons') ? { rechercheId: parametre('rechercheId'), recherche: parametre('recherche'), chansons: [chanson] } :
					{ chanson, inscriptionPossible: type === 'JOUEUR', motifIndisponibilite: type === 'JOUEUR' ? null : 'ANIMATEUR' } }); return;
			}
			const corps = requete.postDataJSON(), cle = corps.envoiId ?? requete.headers()['idempotency-key'];
			verifier(Boolean(cle), 'Clé d’envoi obligatoire');
			let cible = passages.find(p => p.passageId === (corps.passage?.passageId ?? chemin.split('/').at(-2)));
			let action = 'DEPLACER';
			if (chemin.endsWith('/limite')) { limite = corps.limitePassagesEnAttente; action = 'REGLER_LIMITE'; }
			else if (chemin.endsWith('/priorite')) {
				action = 'DEFINIR_PRIORITE'; passages = passages.map(p => ({ ...p, prioritaire: corps.prioritaire && p.passageId === corps.passage.passageId, numeroModification: p.numeroModification + 1 }));
				cible = passages.find(p => p.passageId === corps.passage.passageId);
			} else if (/\/(retrait|desinscription)$/.test(chemin)) { action = chemin.endsWith('/retrait') ? 'RETIRER' : 'DESINSCRIRE'; passages = passages.filter(p => p.passageId !== cible.passageId); cible = { ...cible, numeroModification: cible.numeroModification + 1 }; }
			else if (chemin.endsWith('/passages')) {
				const p = { ...passages[0], passageId: '00000016-0000-4000-8000-000000000000', numero: 16, numeroModification: 0,
					position: passages.length + 1, tour: 3, prioritaire: false, chanson, interpretes: [{ joueurId: identifiant, pseudo: 'Jo' }],
					attente: { etat: 'ESTIMEE', dureeMs: passages.length * 208000, calculeeLe: '2026-11-17T20:00:00Z' } };
				passages.push(p); cible = p; action = 'INSCRIRE'; publier();
				if (perdreConfirmation) {
					perdreConfirmation = false;
					await new Promise(resolve => { confirmationRetardee = async () => { await route.fulfill({ status: 201, json: accuse(cle, 'INSCRIRE', p) }); resolve(); }; }); return;
				}
			}
			publier(chemin.endsWith('/limite') ? 'limite-passages-modifiee' : 'file-attente-modifiee');
			await route.fulfill({ status: action === 'INSCRIRE' ? 201 : 200, json: accuse(cle, action, cible) });
		});
		await onglet.addInitScript(({ soireeId, type, identifiant }) => sessionStorage.setItem('croustyquizz.jeton', `demo:${type}:${soireeId}:${identifiant}`), { soireeId, type, identifiant });
		await onglet.goto(`${base}/${ecran}.html?soiree=${soireeId}`);
		await onglet.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
		await onglet.locator('.karaoke').first().waitFor(); return c;
	}
	const consoleClient = await installer(page, 'ANIMATEUR', ids[0], 'console');
	const manettes = [];
	for (const id of ids) manettes.push(await installer(await page.context().newPage(), 'JOUEUR', id, 'manette'));
	const salle = await installer(await page.context().newPage(), 'ECRAN', ids[0], 'ecran');
	const manette = manettes[0].onglet;
	verifier(!(await manette.locator('[data-personnel]').innerText()).includes('Tu participeras'), 'Retardataire admis au Karaoké');
	verifier(await manette.locator('.mon-ticket').count() === 2, 'Deux Tours et tickets personnels multiples');
	await page.locator('button.ticket').first().focus(); await page.keyboard.press('Enter');
	verifier(await page.locator('button[aria-pressed=true]').count() === 1, 'Sélection clavier unique');
	await page.getByRole('button', { name: 'Priorité', exact: true }).click();
	await page.keyboard.press('Escape');
	verifier(await page.evaluate(() => document.activeElement?.textContent === 'Priorité'), 'Échap rend le focus');
	await page.getByRole('button', { name: 'Priorité', exact: true }).click(); await page.getByRole('button', { name: 'Confirmer', exact: true }).click();
	await page.waitForFunction(() => document.querySelector('button[aria-pressed=true]')?.textContent.includes('Prioritaire'));
	verifier(await page.evaluate(() => document.activeElement?.isConnected && document.activeElement !== document.body), 'Retour de focus après confirmation');
	await page.getByRole('button', { name: 'Retirer', exact: true }).focus(); limite = 4; publier('limite-passages-modifiee');
	await page.waitForFunction(() => document.querySelector('#karaoke-limite').value === '4');
	verifier(await page.evaluate(() => document.activeElement?.textContent === 'Retirer'), 'Événement reçu pendant le focus d’une commande');
	await page.locator('#karaoke-limite').fill('5'); publier();
	verifier(await page.locator('#karaoke-limite').inputValue() === '5', 'Vue reçue pendant la saisie');
	await page.getByRole('button', { name: 'Appliquer', exact: true }).click();
	await page.locator('[data-laterale] input').fill('panne'); await page.getByRole('button', { name: 'Chercher', exact: true }).click();
	await page.getByText('La recherche est indisponible.', { exact: true }).waitFor();
	await page.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
	await page.locator('[data-laterale] input').fill('métro'); await page.getByRole('button', { name: 'Chercher', exact: true }).click();
	await page.getByRole('button', { name: chanson.titre, exact: true }).click();
	verifier((await page.locator('dialog').innerText()).includes('Consultation uniquement'), 'Fiche Animateur sans inscription');
	await page.keyboard.press('Escape');
	await manette.locator('input').fill('métro'); await manette.getByRole('button', { name: 'Chercher', exact: true }).click();
	await manette.getByRole('button', { name: chanson.titre, exact: true }).click();
	await manette.getByRole('button', { name: "M'inscrire", exact: true }).click();
	await manette.waitForFunction(() => document.querySelectorAll('.mon-ticket').length === 3);
	await manette.getByRole('button', { name: 'Fermer', exact: true }).click();
	await confirmationRetardee(); // Le ticket est confirmé par vue avant le 201 tardif.
	verifier(await manette.locator('.mon-ticket').count() === 3, '201 tardif sans ticket dupliqué');
	const avant = consoleClient.captures;
	await page.getByRole('button', { name: 'Resynchroniser', exact: true }).click();
	await page.waitForFunction(() => document.querySelector('[data-connexion]').textContent === 'Vue synchronisée');
	verifier(consoleClient.captures > avant, 'Recapture effective');
	for (const [client, nom, tailles] of [[manettes[0], 'manette', [[390, 844]]], [consoleClient, 'console', [[1440, 900], [1200, 900], [1024, 768], [860, 900]]], [salle, 'ecran', [[1920, 1080]]]]) {
		for (const [width, height] of tailles) {
			await client.onglet.setViewportSize({ width, height }); await client.onglet.evaluate(() => document.fonts.ready);
			verifier(await client.onglet.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${nom} sans débordement à ${width}`);
			if (nom === 'ecran') verifier(await client.onglet.evaluate(() => document.querySelector('[data-talon]').getBoundingClientRect().bottom <= innerHeight), 'Talon visible sans défilement');
			const fichier = `/tmp/croustyquizz-93-${nom}-${width}.png`; await client.onglet.screenshot({ path: fichier, fullPage: true }); captures.push(fichier);
		}
	}
	verifier(await salle.onglet.locator('[data-talon]').innerText().then(t => t.includes('DEMO01')), 'Talon intact');
	terminee = true; publier('manche-terminee');
	for (const c of clients) {
		await c.onglet.waitForFunction(() => document.querySelectorAll('link[data-mode=karaoke]').length === 0);
		verifier(await c.onglet.locator('[data-principale]').innerText() === '', 'Démontage des zones');
	}
	verifier(erreurs.length === 0, erreurs.join('\n'));
	for (const c of clients) if (c.onglet !== page) await c.onglet.close();
	return { resultat: 'Trois Joueurs, trois écrans, Tours, priorité, catalogue, 201 tardif, recapture, focus et démontage vérifiés.', captures };
}
