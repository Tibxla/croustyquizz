---
status: accepted
---

# Éviction des vues des Soirées terminées

## Contexte

L’ADR 0004 conserve la dernière Vue synchronisée et sa Révision pour chaque destination. Un Écran de salle rechargé dans la nuit doit retrouver le Podium final. Ces données doivent aussi pouvoir être libérées sans redémarrer le serveur.

## Décision

`DiffuseurStomp` conserve ensemble les vues et les Révisions de la salle, de la Console et de toutes les Manettes pendant 24 heures à compter de la fin enregistrée de la Soirée. Le délai se règle dans `application.yaml` avec `croustyquizz.tempsreel.delai-eviction-vues`. Les Soirées en cours gardent leurs vues.

Une tâche Spring vérifie les échéances chaque minute avec la `Clock` du socle. Elle libère la mémoire même sans trafic. La capture et la publication vérifient aussi l’échéance sous le verrou de Soirée pour l’appliquer dès qu’elle est atteinte.

## Conséquences

Après l’échéance, `CaptureVueRepli` reconstruit la vue depuis la base et la capture rend la Révision `0`. La coquille prend la Révision de la capture comme point de départ du nouvel abonnement. Les réglages tardifs de Musique d’attente restent enregistrés et sont visibles à la prochaine capture. La diffusion de la Soirée s’arrête à l’échéance pour conserver l’ordre des Révisions des abonnements encore ouverts.
