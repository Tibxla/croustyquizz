import { creerInterface, creerTicket } from './commun.mjs';

export function monter(zones, contexte) {
	const ui = creerInterface(zones, contexte), { e } = ui;
	const invitation = e('h2', 'karaoke__invitation');
	const prochain = e('div', 'karaoke__prochain'); ui.principale.append(invitation, prochain);
	const titre = e('h2', '', 'Ensuite'), file = e('ol', 'rouleau'), autres = e('p');
	ui.laterale.append(titre, file, autres);
	ui.surRendu = () => {
		if (!ui.bloc) return;
		const passages = ui.bloc.prochainsPassages;
		invitation.textContent = passages.length ? 'Prépare-toi.' : 'Choisis ta Chanson sur ta Manette.';
		prochain.hidden = !passages.length;
		prochain.replaceChildren(...(passages.length ? [creerTicket(ui, passages[0])] : []));
		file.hidden = !passages.length; titre.hidden = !passages.length;
		file.replaceChildren(...passages.map(p => { const li = e('li'); li.append(creerTicket(ui, p)); return li; }));
		autres.hidden = !ui.bloc.nombreAutresPassages;
		autres.textContent = ui.bloc.nombreAutresPassages ? `et ${ui.bloc.nombreAutresPassages} autres` : '';
	};
	return { afficher: ui.afficher, demonter: ui.demonter };
}
