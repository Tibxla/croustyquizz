---
status: accepted
---

# Prestataire des Campagnes SMS

## Contexte

La démonstration doit montrer la chaîne d’envoi d’une Campagne SMS jusqu’au téléphone de Tibxla, à coût nul. Une Campagne contient un texte rédigé par l’Animateur et un lien STOP propre à chaque Contact. Le port `EnvoiDeSms` de la tranche 1 isole déjà l’envoi de la composition et de la planification de la Campagne.

## Options

| Option | Avantages | Contraintes |
|---|---|---|
| Twilio, compte d’essai (« Limited trial ») | Inscription gratuite, sans carte bancaire. API HTTPS utilisable depuis Render. | Durée et volume limités, destinataires vérifiés, restrictions sur le contenu et la destination. |
| Passerelle Android open source en mode local | Réutilise un téléphone et un forfait SMS existants. Permet de transmettre le texte de la Campagne. | Mobilise un téléphone et sa carte SIM. Le serveur doit pouvoir joindre la passerelle sur le réseau local ou par un accès dédié. |
| Prestataire français payant | Offre adaptée aux envois en France, selon les conditions du prestataire. | Nécessite un compte payant et un budget pour les envois. |

## Décision

Tibxla choisit de rester sur le compte d’essai gratuit de Twilio. L’équipe n’ouvrira pas de compte payant pour la démonstration. L’essai est accessible sans carte bancaire, dure 30 jours et comprend 100 SMS. La démonstration utilise le numéro expéditeur d’essai indiqué dans la Console Twilio et un seul destinataire vérifié, le téléphone de Tibxla. Twilio autorise jusqu’à cinq numéros vérifiés et applique des restrictions géographiques. [Conditions de l’essai Twilio](https://www.twilio.com/docs/usage/trials).

L’API d’essai attend un nom de modèle dans le champ `Body`. L’application transmet `sms_marketing_promotions` par défaut et conserve le texte composé avec son lien STOP dans l’aperçu. La recette constate le message type reçu sur le téléphone. [Guide SMS de l’essai](https://www.twilio.com/docs/usage/trials/try-out-sms#using-the-messaging-api).

## Conséquences

- L’adaptateur `EnvoiDeSmsTwilio` raccorde le port `EnvoiDeSms` à l’API REST de Twilio avec des délais bornés. Un SID de message confirme l’acceptation de la demande par l’API. La réception se vérifie sur le téléphone.
- Le SID du compte, le jeton et le numéro expéditeur sont fournis par variables d’environnement. Sur Render, l’activation est réservée à la recette. Sans activation explicite et configuration complète, les envois sont simulés.
- La page Campagnes annonce que le destinataire recevra un message type en mode d’essai. Le réglage `TWILIO_MODELE_ESSAI` permet de choisir un autre modèle. Une valeur explicitement vide permet de transmettre le texte composé avec son lien STOP si l’équipe passe un jour à un compte payant.
- Une passerelle Android pourrait plus tard remplacer Twilio par un autre adaptateur du port `EnvoiDeSms`.
