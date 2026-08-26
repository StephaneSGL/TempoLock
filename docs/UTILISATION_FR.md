# Utilisation, récupération et retour arrière

Ce guide commence après l'installation d'une APK release signée et le provisionnement de TempoLock comme Device Owner. Pour préparer l'appareil, suivez d'abord [INSTALLATION_FR.md](INSTALLATION_FR.md).

> Une session valide ne possède aucun bouton d'annulation, code secret ou raccourcissement. Pour un premier essai, utilisez l'émulateur jetable et l'application `fr.tempolock.testtarget` décrits dans [TESTS.md](TESTS.md#4-test-de-bout-en-bout-sur-émulateur).

## Avant d'armer un verrou

Vérifiez les points suivants :

- l'appareil utilise Android 13 / API 33 ou plus récent ;
- TempoLock indique qu'il est Device Owner ;
- l'accès spécial **Alarmes et rappels** est autorisé ;
- l'heure et le fuseau automatiques sont activés ;
- une connexion permet à Android de fournir son heure réseau ;
- la durée choisie ne vous empêchera pas d'accéder à une donnée ou une fonction indispensable.

TempoLock refuse l'armement si le statut Device Owner, l'alarme exacte ou l'heure réseau ne peuvent pas être confirmés. Ce refus est préférable à une session partiellement appliquée.

## Créer une session

1. Ouvrez TempoLock.
2. Choisissez une application dans la liste. Seules les applications utilisateur lançables sont proposées ; TempoLock et les paquets système sont exclus.
3. Choisissez une durée entre 1 minute et 30 jours.
4. Ouvrez l'écran de confirmation et relisez le paquet, le libellé et l'échéance.
5. Saisissez exactement `VERROUILLER`.
6. Confirmez une seule fois et attendez l'écran de verrou actif.

Une fois la confirmation réussie, la durée et la cible ne sont plus modifiables. Si Android refuse la suspension ou une restriction requise, TempoLock ne doit pas annoncer une session active.

## Pendant la session

L'écran affiche la cible, l'échéance et un compte à rebours. Il ne propose ni action « Annuler » ni action « Déverrouiller ».

Pendant cette période :

- le paquet cible est suspendu et protégé contre la désinstallation ;
- son contrôle utilisateur est désactivé ;
- l'installation, la désinstallation, le contrôle des applications, le débogage, le mode sans échec, la modification de l'heure, la réinitialisation depuis les réglages et l'ajout ou le changement d'utilisateur sont restreints ;
- TempoLock reste lui-même protégé contre la désinstallation.

Une extinction ou un redémarrage normal n'annule pas le verrou. Pendant le même démarrage, le compteur s'appuie sur le temps monotone et ne réagit pas à une modification de l'heure affichée. Après un redémarrage, TempoLock attend une heure réseau Android avant de décider que l'échéance est passée.

Si cette heure réseau est indisponible, l'application reste **fail-closed** : la cible demeure suspendue et une nouvelle tentative est planifiée. Le verrou peut alors dépasser la durée affichée. Inversement, cette heure réseau n'est pas une source de sécurité authentifiée ; une source usurpée qui avancerait l'heure après redémarrage pourrait théoriquement avancer la libération. Voir [SECURITE.md](SECURITE.md#temps-et-redémarrage).

## Après l'échéance

Lorsque l'échéance est confirmée, TempoLock doit :

1. désuspendre la cible ;
2. réautoriser sa désinstallation et son contrôle utilisateur ;
3. retirer les restrictions temporaires de la session ;
4. annuler les alarmes ;
5. écrire un tombstone signé, puis revenir à l'écran sans session active.

TempoLock reste néanmoins Device Owner et protégé contre sa propre désinstallation. C'est le comportement actuel attendu, pas un état résiduel accidentel.

## Échéance passée mais cible encore bloquée

Procédez sans effacer les données au hasard :

1. donnez à Android une connexion réseau fonctionnelle afin qu'il puisse fournir son heure réseau ;
2. ouvrez TempoLock et utilisez **Actualiser** si cette action est affichée ;
3. laissez le watchdog et les événements système déclencher une nouvelle réconciliation ;
4. si nécessaire, effectuez un redémarrage normal et attendez le retour de l'heure réseau ;
5. lorsque le débogage est de nouveau autorisé, contrôlez l'état avec les commandes suivantes.

```powershell
adb shell am start -W -n fr.tempolock.app/.MainActivity
adb shell dpm list-owners
adb shell dumpsys device_policy | Select-String "fr.tempolock.app"
```

La levée de `DISALLOW_DEBUGGING_FEATURES` ne rallume pas nécessairement ADB. Il peut être nécessaire de réactiver manuellement les options développeur et le débogage USB, puis d'autoriser de nouveau l'ordinateur.

Il n'existe pas de code de secours pour raccourcir une session valide. Si l'échéance est passée mais que l'état reste irrécupérable, une mise à jour corrective signée est la seule voie non destructive prévue par l'architecture actuelle.

## Installer une mise à jour corrective

Attendez de préférence qu'aucune session ne soit active. Une mise à jour compatible doit posséder simultanément :

- le paquet `fr.tempolock.app` ;
- un `versionCode` strictement supérieur à la version installée ;
- exactement le même certificat de signature ;
- une gestion compatible de l'état de session persistant.

Vérifiez les certificats avec `apksigner`, puis installez par-dessus l'application existante :

```powershell
& "$TempoLockBuildTools\apksigner.bat" verify --print-certs ".\dist\TempoLock-version-installee.apk"
& "$TempoLockBuildTools\apksigner.bat" verify --print-certs ".\dist\TempoLock-mise-a-jour.apk"
adb install -r ".\dist\TempoLock-mise-a-jour.apk"
```

Les empreintes de certificat doivent être identiques. Une nouvelle clé provoque normalement `INSTALL_FAILED_UPDATE_INCOMPATIBLE` et ne remplace pas le Device Owner existant.

## Déprovisionnement, désinstallation et retour arrière

La version actuelle ne contient **aucun écran de déprovisionnement**. Même hors session, TempoLock reste Device Owner et bloque sa propre désinstallation. Il ne faut donc pas promettre une suppression par le menu Applications ou par une simple commande `adb uninstall`.

Un APK avec un `versionCode` inférieur constitue un déclassement et Android le refuse normalement. Pour revenir fonctionnellement à un code antérieur sans perdre le statut Device Owner, il faut produire une **nouvelle version corrective** à partir de ce code connu, lui attribuer un `versionCode` supérieur, la signer avec la clé release d'origine et préserver la compatibilité avec l'état persistant. Ce n'est pas un retour arrière binaire classique.

Si aucune APK compatible ne peut être installée, notamment parce que la clé privée d'origine est perdue, la récupération ultime peut imposer :

- **Wipe Data** pour un AVD jetable ;
- une réinitialisation d'usine par la procédure du constructeur ;
- ou un reflash de l'appareil.

Ces opérations sont destructrices. Sauvegardez et vérifiez tout ce qui reste accessible avant de les engager. Une réinitialisation ou un reflash peut aussi demander les identifiants du compte précédemment associé à l'appareil à cause des protections antivol Android.

La commande `dpm remove-active-admin` ne constitue pas une procédure garantie pour cette APK release : l'application n'est pas déclarée `testOnly`, et la possibilité de retirer un Device Owner dépend du mode et de la version Android.

## Résumé des états

| Situation | Comportement attendu | Action utile |
|---|---|---|
| Pas de session | Sélection d'une cible possible ; TempoLock reste Device Owner et protégé | Créer une session ou installer une mise à jour signée |
| Session active | Cible suspendue, restrictions appliquées, pas d'annulation | Attendre l'échéance |
| Redémarrage avant échéance | Réconciliation et maintien du verrou | Rétablir la connexion nécessaire à l'heure réseau |
| Heure réseau indisponible après redémarrage | Maintien fail-closed, nouvelle tentative | Attendre la synchronisation Android |
| Échéance confirmée | Cible libérée, restrictions temporaires retirées | Vérifier le lancement de la cible |
| État irrécupérable | Aucune libération optimiste | Mise à jour signée ; en dernier recours reset/reflash destructif |

Voir aussi : [architecture](ARCHITECTURE.md), [sécurité et limites](SECURITE.md), [tests](TESTS.md) et [accueil du projet](../README.md).
