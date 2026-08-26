# Sécurité et limites de TempoLock

TempoLock fournit un engagement local fort contre les manipulations Android ordinaires. Il ne rend pas un appareil inviolable et ne résiste pas à un propriétaire capable de modifier ou de remplacer le système.

## Objectif de sécurité

Le scénario visé est le suivant : sur un appareil Android non rooté dont TempoLock est Device Owner, l'utilisateur choisit une application et une durée, puis ne peut plus annuler ou raccourcir cette session par l'interface, les réglages Android usuels, une modification de l'heure, un redémarrage normal ou une désinstallation ordinaire.

TempoLock cherche notamment à éviter :

- l'arrêt manuel du verrou depuis l'application ;
- la désinstallation ou l'effacement de la cible pendant la session ;
- la désinstallation de TempoLock, y compris hors session ;
- le contournement par modification de l'heure murale, mode sans échec, ajout ou changement d'utilisateur, installation d'une autre APK ou débogage ADB pendant la session ;
- la libération sur un état de journal incomplet, corrompu ou contradictoire.

Ce modèle suppose qu'Android, `DevicePolicyManager`, le stockage protégé et Android Keystore fonctionnent correctement et que l'attaquant ne dispose ni du root ni d'une chaîne de démarrage compromise.

## Défenses réellement mises en œuvre

| Risque | Contrôle actuel |
|---|---|
| Lancement de la cible | Suspension du paquet par `DevicePolicyManager` et vérification de l'état |
| Suppression de la cible | Désinstallation bloquée pendant la session et contrôle utilisateur du paquet désactivé |
| Suppression de TempoLock | Blocage de désinstallation réaffirmé avec ou sans session ; TempoLock reste Device Owner |
| Modification de la durée | Durée fixée à l'armement ; aucun bouton d'annulation, code de secours ou raccourcissement |
| Modification de l'heure affichée | Temps monotone pendant le démarrage courant ; heure réseau Android obligatoire après redémarrage |
| Redémarrage | Session persistée, réconciliation directe au démarrage et stratégie fail-closed |
| Retard ou perte d'une alarme | Alarme exacte obligatoire plus watchdog inexact indépendant et réconciliation à plusieurs événements système |
| Écriture interrompue | Deux `AtomicFile`, génération croissante et acceptation du journal valide le plus récent |
| Ancien état qui réapparaît | Tombstone signé de génération supérieure lors de la libération |
| Altération ordinaire du journal | HMAC-SHA-256 avec clé Android Keystore ; incohérence traitée en fail-closed |

Les restrictions temporaires exactes sont décrites dans le [guide d'architecture](ARCHITECTURE.md#politiques-appliquées).

## Stratégie fail-closed

« Fail-closed » signifie qu'une incertitude ne déclenche pas une libération optimiste. TempoLock conserve ou réapplique le verrou et planifie une nouvelle tentative lorsque :

- l'heure réseau nécessaire n'est pas disponible après un redémarrage ;
- les journaux présents ne permettent pas d'établir un état valide ;
- Android ne confirme pas l'application ou le retrait d'une politique ;
- une transition durable de libération ne peut pas être enregistrée de manière sûre.

Cette stratégie réduit le risque de libération anticipée, mais peut **prolonger** un verrou après l'échéance si le système reste incapable de fournir les preuves attendues.

## Temps et redémarrage

Au démarrage d'une session, TempoLock refuse l'armement si `SystemClock.currentNetworkTimeClock()` n'est pas disponible. Tant que l'appareil n'a pas redémarré, `SystemClock.elapsedRealtime()` fournit la durée monotone. Après redémarrage, TempoLock doit comparer l'échéance absolue avec l'heure réseau Android ; il ne se rabat jamais sur l'heure murale modifiable par l'utilisateur.

Cette conception comporte deux limites opposées :

- une heure réseau indisponible après redémarrage prolonge le verrou jusqu'à une réconciliation fiable ;
- `currentNetworkTimeClock()` n'est pas une source conçue comme primitive de sécurité authentifiée. Une source réseau malveillante ou usurpée qui avancerait fortement l'heure après un redémarrage pourrait théoriquement provoquer une libération anticipée.

TempoLock n'a pas la permission Android `INTERNET` et n'implémente pas son propre client de synchronisation : il consomme l'heure réseau déjà fournie par le système Android. Cela ne permet pas à l'application d'authentifier la provenance de cette heure.

## Intégrité, confidentialité et vie privée

Le HMAC des journaux détecte les modifications ordinaires et les états contradictoires. Il ne chiffre pas les données et ne constitue pas une protection contre un système rooté capable d'extraire ou d'instrumenter Android Keystore.

La session reste locale et contient les informations nécessaires au verrou, notamment le paquet et le libellé de la cible, la durée, les échéances et le compteur de démarrage. L'application ne contient actuellement aucun mécanisme de compte, de serveur distant ou de télémétrie, et son manifeste ne demande pas la permission `INTERNET`.

La sauvegarde Android de l'application est désactivée et les règles d'extraction excluent ses données. Cette mesure évite une restauration ordinaire non maîtrisée ; elle ne protège pas contre une copie effectuée depuis un système compromis.

## Limites absolues et contournements hors périmètre

TempoLock ne peut pas garantir l'absence totale de contournement :

- une réinitialisation depuis le recovery, un déverrouillage du bootloader, le root, un reflash de la ROM, une faille système ou un remplacement physique de l'appareil peuvent neutraliser TempoLock ;
- `DISALLOW_FACTORY_RESET` bloque les chemins Android ordinaires, pas toutes les procédures du constructeur ou du recovery ;
- la protection vise un **nom de paquet** précis, pas le site web du service, un clone portant un autre paquet, une version web dans un navigateur, une application équivalente, un autre profil mal isolé ou un autre appareil ;
- TempoLock n'est ni un filtre DNS, ni un pare-feu, ni un VPN ;
- Android refuse de suspendre certains paquets système ou critiques. TempoLock doit alors refuser l'armement, mais ne peut pas modifier cette règle du système ;
- le comportement exact des politiques peut varier selon le fabricant et la version Android ; un essai sur AVD ne remplace pas une validation sur le modèle réel ;
- une alarme exacte ou le watchdog peuvent être retardés par Android. Le verrou peut durer plus longtemps, jamais être libéré sur la seule supposition que l'alarme aurait dû arriver ;
- retirer `DISALLOW_DEBUGGING_FEATURES` réautorise le débogage mais ne réactive pas nécessairement ADB automatiquement. Les options développeur et le débogage USB peuvent devoir être réactivés manuellement.

## Device Owner, signature et récupération

La version actuelle ne possède **aucun écran de déprovisionnement**. Après l'échéance et hors session, TempoLock reste Device Owner et protégé contre sa propre désinstallation.

La voie de maintenance normale est une mise à jour signée installée par-dessus l'application existante. Elle doit impérativement utiliser :

- le même paquet `fr.tempolock.app` ;
- un `versionCode` strictement supérieur ;
- exactement le même certificat de signature que l'APK déjà installée.

La clé privée release doit donc être conservée durablement hors du dépôt, avec des sauvegardes chiffrées vérifiées. Un certificat public ou une nouvelle clé ne suffit pas à produire une mise à jour compatible.

Si l'état n'est pas récupérable après l'échéance et qu'aucune mise à jour correctement signée ne peut corriger la situation, la récupération ultime peut exiger une réinitialisation d'usine ou un reflash. Ces opérations sont destructrices. Voir la [procédure d'utilisation et de récupération](UTILISATION_FR.md#déprovisionnement-désinstallation-et-retour-arrière).

## Portée de la validation actuelle

Les tests unitaires, instrumentés, visuels et E2E exécutés sont détaillés dans [TESTS.md](TESTS.md). L'E2E final a validé la persistance à travers un redémarrage dur et la relance visuelle de la cible après l'échéance sur un AVD API 36 jetable. Il n'a pas vérifié séparément l'inaccessibilité visuelle de la cible pendant la phase active.

Voir aussi : [architecture](ARCHITECTURE.md), [installation](INSTALLATION_FR.md), [utilisation](UTILISATION_FR.md) et [accueil du projet](../README.md).
