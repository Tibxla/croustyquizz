// Avec l'application de développement sur le port 8080 et playwright-cli open :
// playwright-cli run-code --filename=tools/ecrans/compte-recette-navigateur.mjs
async page => {
	const contexte = await page.context().browser().newContext({ viewport: { width: 390, height: 844 } });
	try {
		const navigateur = await contexte.newPage();
		const base = 'http://localhost:8080';
		const preparation = '/preparer-soiree.html';
		const motDePasse = 'un mot de passe de recette';
		const email = `recette-${Date.now()}@exemple.test`;
		const chercher = selecteur => navigateur.locator(selecteur);
		const bouton = nom => navigateur.getByRole('button', { name: nom, exact: true });
		const verifierErreur = async id => navigateur.waitForFunction(champId => {
			const champ = document.getElementById(champId);
			const erreur = document.getElementById(`${champId}-erreur`);
			return document.activeElement === champ && champ.getAttribute('aria-invalid') === 'true'
				&& champ.getAttribute('aria-describedby').split(' ').includes(erreur.id)
				&& erreur.classList.contains('champ--erreur') && !erreur.hidden && erreur.textContent.length > 0;
		}, id);

		await navigateur.goto(base + preparation);
		await navigateur.waitForURL(adresse => adresse.pathname === '/connexion.html'
			&& adresse.searchParams.get('retour') === preparation);
		await navigateur.getByRole('link', { name: 'Créer un compte' }).click();
		await bouton('Créer le compte').click();
		await verifierErreur('lieu');
		for (const [id, valeur] of Object.entries({
			lieu: 'Le Balcon de recette', ville: 'Nanterre', nom: 'Camille', email, 'mot-de-passe': motDePasse,
		})) {
			await chercher(`#${id}`).fill(valeur);
		}
		await bouton('Créer le compte').click();
		await navigateur.waitForURL(adresse => adresse.pathname === '/connexion.html'
			&& adresse.searchParams.get('inscription') === '1');
		await chercher('#email').fill(email);
		await chercher('#mot-de-passe').fill('mauvais mot de passe');
		await bouton('Se connecter').click();
		await verifierErreur('mot-de-passe');
		await chercher('#mot-de-passe').fill(motDePasse);
		await bouton('Se connecter').click();
		await navigateur.waitForURL(base + preparation);

		await chercher('#titre').fill('Soirée de recette du compte');
		await bouton('Créer la Soirée').click();
		await navigateur.getByRole('link', { name: 'Ouvrir la Console d’animation' }).click();
		await navigateur.waitForFunction(() => document.querySelector('[data-connexion]')?.textContent === 'Vue synchronisée');
		const consoleDemandee = new URL(navigateur.url());
		await bouton('Se déconnecter').click();
		await navigateur.waitForURL(adresse => adresse.pathname === '/connexion.html'
			&& adresse.searchParams.get('retour') === consoleDemandee.pathname + consoleDemandee.search);
		return 'Inscription, focus des erreurs, connexion, Console synchronisée et déconnexion vérifiés.';
	} finally {
		await contexte.close();
	}
}
