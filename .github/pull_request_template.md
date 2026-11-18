<!-- Titre : même format qu'un commit, par exemple « feat(karaoke): ajouter la file d'attente des chanteurs ». -->

Closes #

## Ce qui change

<!-- Le problème, ce que la PR fait, et ce qu'elle laisse de côté. -->

## Vérifications

- [ ] `./mvnw verify` passe en local
- [ ] `npm test --prefix tools/api` passe (si `docs/api/` a changé)
- [ ] Contrat `docs/api/` mis à jour avant l'implémentation (si un échange HTTP ou STOMP change)
- [ ] Package d'un autre propriétaire touché : non / oui, lequel :

<!-- Ce qui a été testé à la main, captures pour une page. -->
