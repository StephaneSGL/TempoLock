# Architecture technique de TempoLock

Ce document décrit l'implémentation actuelle. TempoLock est une application Android native en Kotlin, Jetpack Compose et Hilt. Elle ne repose ni sur un service distant, ni sur un VPN, ni sur un service d'accessibilité : le verrou est appliqué par `DevicePolicyManager`, ce qui impose que l'application soit **Device Owner**.

## Vue d'ensemble

Le projet contient deux modules Gradle :

| Module | Rôle |
|---|---|
| `:app` | Application TempoLock, paquet release `fr.tempolock.app` |
| `:test-target` | Application factice `fr.tempolock.testtarget` destinée aux essais sur émulateur |

Dans `:app`, les responsabilités sont séparées comme suit :

| Couche | Composants principaux | Responsabilité |
|---|---|---|
| Interface | [`MainActivity`](../app/src/main/java/fr/tempolock/app/MainActivity.kt), [`TempoLockScreen`](../app/src/main/java/fr/tempolock/app/ui/TempoLockScreen.kt), [`MainViewModel`](../app/src/main/java/fr/tempolock/app/MainViewModel.kt) | Sélection de la cible et de la durée, confirmation, affichage du compte à rebours et des erreurs |
| Domaine | [`SessionCoordinator`](../app/src/main/java/fr/tempolock/app/domain/SessionCoordinator.kt), [`Models`](../app/src/main/java/fr/tempolock/app/domain/Models.kt), [`Contracts`](../app/src/main/java/fr/tempolock/app/domain/Contracts.kt) | Transitions de session, invariants et stratégie fail-closed |
| Persistance | [`SecureSessionStore`](../app/src/main/java/fr/tempolock/app/data/SecureSessionStore.kt) | Double journal atomique signé et tombstone de fin de session |
| Politiques Android | [`DeviceOwnerPolicy`](../app/src/main/java/fr/tempolock/app/platform/DeviceOwnerPolicy.kt) | Suspension de la cible, restrictions système, vérification puis retrait des politiques temporaires |
| Temps | [`AndroidTrustedClock`](../app/src/main/java/fr/tempolock/app/platform/AndroidTrustedClock.kt) | Heure réseau Android, temps monotone et compteur de démarrage |
| Réveil | [`AndroidUnlockScheduler`](../app/src/main/java/fr/tempolock/app/platform/AndroidUnlockScheduler.kt), [`EnforcementReceiver`](../app/src/main/java/fr/tempolock/app/receiver/EnforcementReceiver.kt) | Alarme exacte, watchdog inexact et réconciliation après événements système |
| Injection | [`AppModule`](../app/src/main/java/fr/tempolock/app/di/AppModule.kt) | Liaison des interfaces du domaine à leurs implémentations Android |

## Cycle d'armement

L'interface accepte une durée comprise entre 1 minute et 30 jours et exige la saisie exacte de `VERROUILLER`. La coordination est ensuite sérialisée par un mutex.

1. TempoLock confirme qu'il est Device Owner, qu'aucune session n'est déjà active, que la cible et la durée sont valides et que les alarmes exactes sont autorisées.
2. `DeviceOwnerPolicy` impose l'heure et le fuseau automatiques, puis `AndroidTrustedClock` exige une valeur de `SystemClock.currentNetworkTimeClock()` et un compteur de démarrage valide.
3. Le coordinateur persiste d'abord une session en phase `ARMING`.
4. La politique protège TempoLock, suspend la cible, protège la cible contre la désinstallation et applique les restrictions temporaires.
5. Chaque effet critique est relu auprès d'Android. L'armement n'est annoncé comme réussi que si la suspension et les restrictions attendues sont confirmées.
6. La session passe en phase `ACTIVE`, puis une alarme exacte et un watchdog inexact indépendant sont programmés pour la même échéance.

Cette séquence évite de présenter un verrou comme actif si l'état durable ou les politiques Android n'ont pas pu être établis. En cas d'échec partiel, le coordinateur tente un retour arrière conservateur ; s'il ne peut pas prouver que la libération est sûre, il conserve l'état de verrouillage et planifie une nouvelle réconciliation.

## Politiques appliquées

Pendant une session active, `DeviceOwnerPolicy` :

- suspend le paquet ciblé avec `setPackagesSuspended()` ;
- bloque sa désinstallation ;
- désactive le contrôle utilisateur des paquets TempoLock et cible ;
- ajoute `DISALLOW_APPS_CONTROL`, `DISALLOW_UNINSTALL_APPS`, `DISALLOW_INSTALL_APPS`, `DISALLOW_DEBUGGING_FEATURES`, `DISALLOW_SAFE_BOOT`, `DISALLOW_FACTORY_RESET`, `DISALLOW_ADD_USER`, `DISALLOW_USER_SWITCH`, `DISALLOW_CONFIG_DATE_TIME` et `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY`.

TempoLock bloque également sa **propre** désinstallation et vérifie cette protection, même en l'absence de session. À la fin d'un verrou, seules les politiques temporaires concernant la cible et la session sont retirées ; TempoLock reste Device Owner et protégé.

La liste de sélection provient des activités lançables `MAIN/LAUNCHER`. Elle exclut TempoLock, les applications désactivées et les applications système ou mises à jour système. Android peut encore refuser la suspension de certains paquets critiques ; dans ce cas, l'armement doit échouer.

## État durable et intégrité

`SecureSessionStore` écrit dans deux fichiers `AtomicFile` distincts placés dans le stockage protégé de l'appareil. Chaque enregistrement contient un numéro de génération et une charge utile signée par HMAC-SHA-256 avec une clé Android Keystore.

À la lecture :

- la génération valide la plus récente gagne ;
- un journal valide permet de récupérer l'autre s'il est absent ou corrompu ;
- deux enregistrements valides mais contradictoires de même génération provoquent une erreur d'intégrité ;
- si des données existent mais qu'aucun journal n'est valide, le système reste fail-closed ;
- la fin d'une session écrit un **tombstone signé** de génération supérieure, afin qu'un ancien journal valide ne puisse pas ressusciter le verrou.

Les phases persistées sont `ARMING`, `ACTIVE` et `RELEASING`. La phase `RELEASING` est enregistrée avant le retrait des politiques. Ce journal protège l'intégrité et la cohérence de la session ; il ne chiffre pas son contenu.

## Modèle temporel

Une session enregistre deux échéances :

- une échéance monotone fondée sur `SystemClock.elapsedRealtime()` pour le démarrage courant ;
- une échéance absolue fondée sur `SystemClock.currentNetworkTimeClock()` pour survivre à un redémarrage.

Tant que le compteur de démarrage n'a pas changé, le temps monotone est utilisé et une modification de l'heure affichée n'avance pas le compteur. Après un redémarrage, le temps monotone précédent n'est plus comparable : TempoLock exige alors de nouveau l'heure réseau Android. Si elle est indisponible, la cible reste verrouillée et une nouvelle tentative est programmée.

`currentNetworkTimeClock()` n'est cependant pas une primitive de sécurité authentifiée. Une source réseau malveillante ou usurpée après un redémarrage pourrait théoriquement annoncer une heure avancée et provoquer une libération anticipée. Les conséquences sont détaillées dans le [guide de sécurité](SECURITE.md#temps-et-redémarrage).

## Réconciliation et échéance

`EnforcementReceiver` relance la réconciliation lors des démarrages verrouillé ou déverrouillé, d'une mise à jour de TempoLock, d'un changement d'heure ou de fuseau, d'un changement d'autorisation d'alarme exacte et des deux alarmes internes.

Lors d'une réconciliation :

1. sans session, TempoLock réaffirme sa propre protection et annule les alarmes résiduelles ;
2. avant l'échéance, il réapplique et vérifie les politiques, remet la session en `ACTIVE` et reprogramme les alarmes ;
3. à l'échéance, il écrit `RELEASING`, désuspend la cible, retire ses protections et les restrictions temporaires, vérifie leur retrait, annule les alarmes puis écrit le tombstone signé ;
4. en cas d'erreur ou d'incertitude, il conserve le verrou et programme une nouvelle tentative.

L'alarme exacte est obligatoire au moment de l'armement. Le watchdog inexact est une seconde voie de réveil, mais Android peut le livrer en retard. Aucun de ces mécanismes n'autorise une libération avant une échéance confirmée.

## Construction, variantes et évolution

La variante debug porte le suffixe `.debug` et ne correspond pas à la commande Device Owner de la release. La variante release utilise `fr.tempolock.app`, mais le dépôt ne contient pas de configuration de signature privée : l'APK produite doit être signée en dehors du dépôt.

Une mise à jour installée par-dessus le Device Owner existant doit conserver le paquet `fr.tempolock.app`, utiliser un `versionCode` strictement supérieur et être signée avec exactement le même certificat. Toute évolution du format persistant doit aussi préserver ou migrer correctement une session existante.

Voir aussi : [installation et build](INSTALLATION_FR.md), [utilisation et récupération](UTILISATION_FR.md), [sécurité et limites](SECURITE.md), [tests](TESTS.md) et [accueil du projet](../README.md).
