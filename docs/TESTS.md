# Tests de TempoLock

Cette page sépare les tests automatisés du véritable essai Device Owner. Un test unitaire ou une capture Compose ne prouve pas qu'Android a effectivement suspendu un paquet.

Guides associés : [installation](INSTALLATION_FR.md), [utilisation et récupération](UTILISATION_FR.md), [architecture](ARCHITECTURE.md), [sécurité et limites](SECURITE.md) et [accueil du projet](../README.md).

## Statut actuel

Résultats finaux du 27 août 2026 :

| Couche | Résultat |
|---|---|
| Tests unitaires | **20 réussis**, sans échec ni erreur : 17 `SessionCoordinatorTest` et 3 `FormatCountdownTest` |
| Instrumentation | **7 réussis** sur AVD Android 16 / API 36 : 4 Compose UI et 3 `SecureSessionStore` |
| Captures | `:app:validateDebugScreenshotTest` réussi ; revue visuelle manuelle de 9 configurations responsive |
| E2E Device Owner | Réussi sur un AVD API 36 jetable réinitialisé, avec la réserve détaillée dans la section 4 |

Pour chaque campagne, notez au minimum :

- date et commit ou archive testée ;
- version Android, niveau API et modèle/AVD ;
- empreinte SHA-256 de l'APK release signée ;
- résultat de chaque commande Gradle ;
- résultat du provisionnement Device Owner ;
- heures réelles de début et de libération ;
- résultat avant/après redémarrage ;
- anomalies constructeur ou messages Android exacts.

## 1. Vérifications Gradle

Depuis la racine du projet, sous PowerShell :

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Résultats attendus :

- chaque commande termine par `BUILD SUCCESSFUL` ;
- les 20 tests unitaires actuels restent verts ; ils couvrent notamment les bornes de durée, l'exigence Device Owner, le refus sans alarme exacte ou sans heure réseau au démarrage, les transitions transactionnelles, les retours arrière, l'échéance, les nouvelles tentatives, l'usage de l'heure réseau après redémarrage, le formatage du compte à rebours et le fuseau explicite de l'échéance ;
- Lint ne remonte aucune erreur bloquante ;
- l'APK debug est générée ;
- la release est générée mais reste non installable tant qu'elle n'est pas signée selon [INSTALLATION_FR.md](INSTALLATION_FR.md#4-construire-et-signer-lapk-release).

Ne déduisez pas qu'un test a été exécuté uniquement parce que son fichier source existe.

## 2. Tests instrumentés

Démarrez un émulateur ou connectez un appareil de test, puis lancez :

```powershell
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

Les scénarios d'interface doivent vérifier au minimum :

- la phrase `VERROUILLER` est nécessaire avant la confirmation ;
- l'écran actif n'offre ni action « Déverrouiller » ni action « Annuler » ;
- la boîte de confirmation survit à une recréation d'état ;
- l'action principale reste accessible sur une fenêtre large et avec une police à 150 %.

Résultat obtenu sur un AVD Android 16 / API 36 : **7 tests réussis**, sans échec signalé.

- 4 tests Compose UI : confirmation explicite, absence de déverrouillage manuel dans l'écran actif, restauration de la boîte de confirmation et accessibilité de l'action principale avec grande fenêtre/police ;
- 3 tests `SecureSessionStore` dans l'environnement Android de l'AVD : aller-retour d'une session signée, récupération avec un journal corrompu et priorité du tombstone signé plus récent sur un ancien journal valide.

Ces tests utilisent la variante debug `fr.tempolock.app.debug`. Ils complètent, sans le remplacer, l'essai Device Owner de la variante release.

## 3. Captures et contrôle visuel

Pour créer ou renouveler intentionnellement les références :

```powershell
.\gradlew.bat :app:updateDebugScreenshotTest
```

La validation automatique finale est verte. Une revue visuelle manuelle a également été effectuée sur les 9 configurations de la matrice responsive : largeurs 400, 610 et 900 dp, chacune en hauteurs 400, 500 et 1 000 dp. Les références supplémentaires clair, grande police et verrou actif restent couvertes par la validation automatique ; la mention « 9 configurations revues » désigne précisément la matrice responsive.

Commande de validation exécutée après la revue des références :

```powershell
.\gradlew.bat :app:validateDebugScreenshotTest
```

Résultat obtenu : tâche verte, sans différence inattendue signalée. La revue des 9 configurations n'a pas relevé de contenu principal coupé ou illisible.

## 4. Test de bout en bout sur émulateur

Le paquet factice recommandé est `fr.tempolock.testtarget`. Il évite de toucher à Snapchat ou à des données personnelles. Le module Gradle attendu est `:test-target`.

### Résultat final observé

L'essai a été réalisé sur un AVD Android 16 / API 36 jetable, réinitialisé avant la campagne, avec la cible factice :

1. TempoLock a été déclaré Device Owner ;
2. une session d'une minute a été armée sur `fr.tempolock.testtarget` ;
3. `DISALLOW_DEBUGGING_FEATURES` a coupé ADB pendant la session ;
4. un redémarrage dur a été déclenché environ 25 secondes après l'armement ;
5. après l'échéance, la cible a été relancée par le contrôle gRPC de l'émulateur et son écran a été observé visuellement.

Ce résultat valide la persistance de la session à travers ce redémarrage et la libération observable après l'échéance dans cet environnement. **Il ne valide pas séparément l'inaccessibilité visuelle de la cible pendant la phase active** : aucune tentative visuelle dédiée de lancement de la cible n'a été effectuée avant l'échéance.

L'AVD employait une image Google Play qui avait déjà marqué l'assistant de configuration comme terminé. Pour ce test jetable uniquement, les indicateurs `device_provisioned` et `user_setup_complete` ont été remis à `0` avant la commande `dpm set-device-owner`. Ce réglage de laboratoire n'est pas recommandé pour une reproduction normale. Utilisez de préférence une image AOSP ou Google APIs sans Play.

### 4.1 Préparer les APK

Construisez la cible factice :

```powershell
.\gradlew.bat :test-target:assembleDebug
```

L'APK attendue est :

```text
test-target\build\outputs\apk\debug\test-target-debug.apk
```

Construisez, alignez et signez ensuite la variante release de TempoLock selon [INSTALLATION_FR.md](INSTALLATION_FR.md#4-construire-et-signer-lapk-release). L'essai ci-dessous suppose que l'APK signée se trouve dans :

```text
dist\TempoLock-1.0.0-release.apk
```

### 4.2 Repartir d'un AVD vierge

Utilisez exclusivement un AVD Android 13 / API 33 ou ultérieur.

1. Arrêtez l'AVD.
2. Dans Android Studio Device Manager, choisissez **Wipe Data** sur cet AVD de test.
3. Redémarrez-le sans ajouter de compte ni de profil et gardez une connexion réseau fonctionnelle pour l'heure réseau Android.
4. Vérifiez :

```powershell
adb devices
```

N'utilisez pas le téléphone principal pour cette première validation.

### 4.3 Installer et provisionner

```powershell
adb install ".\test-target\build\outputs\apk\debug\test-target-debug.apk"
adb install ".\dist\TempoLock-1.0.0-release.apk"
adb shell pm list packages | Select-String "fr.tempolock.testtarget|fr.tempolock.app"
adb shell dpm set-device-owner fr.tempolock.app/.receiver.TempoLockDeviceAdminReceiver
adb shell dpm list-owners
```

Résultats attendus :

- les deux paquets apparaissent ;
- `dpm set-device-owner` termine sans erreur ;
- `dpm list-owners` désigne le récepteur TempoLock comme Device Owner.

Si le provisionnement échoue, conservez le message exact et effacez de nouveau l'AVD. Ne poursuivez pas comme si le mode Device Owner était actif.

### 4.4 Autoriser l'alarme exacte

```powershell
adb shell am start -W -n fr.tempolock.app/.MainActivity
```

Dans TempoLock, ouvrez le réglage proposé pour **Alarmes et rappels**, autorisez TempoLock, puis revenez à l'application. L'armement doit être refusé tant que cet accès manque. Lorsqu'il est autorisé, TempoLock programme une alarme exacte et un watchdog inexact indépendant pour la même échéance.

### 4.5 Activer un verrou court

1. choisissez l'application factice correspondant à `fr.tempolock.testtarget` ;
2. choisissez **1 minute** ;
3. ouvrez la confirmation ;
4. saisissez `VERROUILLER` ;
5. confirmez et notez immédiatement l'heure de début et l'heure de fin affichée.

L'appareil doit être connecté au moment de la confirmation afin qu'Android fournisse `SystemClock.currentNetworkTimeClock()`. Si cette source est indisponible, TempoLock doit refuser l'armement sans suspendre la cible ni persister une nouvelle session. Ne changez pas l'heure système pour « accélérer » le test : TempoLock n'utilise pas cette horloge utilisateur comme autorité.

### 4.6 Contrôle visuel pendant la session à compléter séparément

La campagne finale n'a pas exécuté ce contrôle séparé. Pour compléter la preuve lors d'une campagne ultérieure, essayez de lancer visuellement la cible avant l'échéance. La commande suivante n'est exploitable que tant qu'ADB reste connecté :

```powershell
adb shell am start -W -n fr.tempolock.testtarget/.TargetActivity
```

Résultats attendus, mais non revendiqués comme observés dans l'E2E final :

- la cible ne s'ouvre pas depuis son icône ;
- ses notifications sont masquées et elle ne reste pas dans les applications récentes ;
- TempoLock affiche un compte à rebours, sans annulation ni modification de durée ;
- les commandes d'arrêt forcé, d'effacement des données et de désinstallation sont désactivées dans les réglages Android ;
- les réglages de date/heure, le mode sans échec, l'ajout/changement d'utilisateur et le débogage sont restreints comme prévu.

Le test suivant devrait échouer avec une raison liée à la politique de l'appareil, mais il peut devenir impossible dès qu'ADB est coupé :

```powershell
adb shell pm uninstall fr.tempolock.testtarget
```

La cible factice doit toujours être présente après l'échec :

```powershell
adb shell pm list packages | Select-String "fr.tempolock.testtarget"
```

### 4.7 Vérifier un redémarrage

Pour reproduire ce contrôle, utilisez une durée suffisamment longue, activez le verrou puis effectuez un redémarrage dur depuis les commandes de l'émulateur ou son interface. Ne dépendez pas de `adb reboot` : ADB peut déjà être coupé par la politique active.

Dans la campagne finale, le redémarrage dur a eu lieu environ 25 secondes après l'armement. Après le retour complet d'Android :

- le paquet factice doit rester inaccessible si l'échéance n'est pas atteinte ;
- le compte à rebours ne doit pas repartir de zéro ni être raccourci ;
- si l'heure réseau Android est disponible et confirme que l'échéance est passée pendant l'arrêt, TempoLock doit libérer la cible après la réconciliation de démarrage ;
- si l'heure réseau est indisponible, TempoLock doit rester **fail-closed** : cible suspendue, état de protection affiché et nouvelle tentative planifiée. Il ne doit pas utiliser l'heure murale modifiable pour décider d'une libération.

La coupure d'ADB par `DISALLOW_DEBUGGING_FEATURES` a bien été observée. Le contrôle gRPC de l'émulateur, indépendant d'ADB, a servi à poursuivre l'observation. La levée ultérieure de la restriction ne réactive pas nécessairement ADB automatiquement ; il peut falloir réactiver manuellement les options développeur et le débogage.

### 4.8 Vérifier la libération

Après l'échéance, utilisez l'écran de l'émulateur ou son contrôle gRPC. Si ADB a été réactivé manuellement, la commande équivalente est :

```powershell
adb shell am start -W -n fr.tempolock.testtarget/.TargetActivity
```

Résultat observé dans la campagne finale : après l'échéance, le contrôle gRPC a relancé la cible et son écran a été visible. Les autres contrôles ci-dessous restent les critères attendus d'une campagne complète :

- la cible s'ouvre normalement ;
- TempoLock revient à l'état sans verrou actif ;
- les restrictions temporaires sont retirées ;
- le paquet cible redevient désinstallable ;
- TempoLock reste Device Owner et demeure protégé contre sa désinstallation, même sans session active.

Si la cible reste bloquée, suivez la [procédure de récupération après l'échéance](INSTALLATION_FR.md#récupération-après-léchéance) et consignez le délai réel ainsi que les messages affichés.

Cette campagne n'authentifie pas la source fournie par `SystemClock.currentNetworkTimeClock()`. Une source réseau malveillante ou usurpée après redémarrage pourrait théoriquement avancer la décision de libération ; cette limite reste hors du périmètre de l'E2E réalisé.

## 5. Cas négatifs complémentaires

À exécuter séparément sur un AVD pouvant être effacé :

- tenter l'activation sans Device Owner : aucun verrou partiel ne doit subsister ;
- refuser l'accès aux alarmes exactes : l'activation doit être bloquée ;
- rendre l'heure réseau indisponible avant l'armement : aucune session ni politique cible ne doit être créée ;
- après redémarrage, rendre l'heure réseau indisponible si l'environnement permet de reproduire ce cas : la cible doit rester verrouillée et une nouvelle tentative doit être planifiée ;
- sélectionner une application puis la désinstaller avant confirmation : l'activation doit échouer proprement ;
- tenter de cibler un paquet système critique : Android doit refuser et TempoLock doit annuler l'activation ;
- faire échouer la programmation ou la politique : aucune session fantôme ne doit être présentée comme réussie ;
- vérifier que l'alarme exacte et le watchdog inexact sont annulés ensemble après une libération confirmée ;
- vérifier qu'un journal HMAC valide peut récupérer l'état si l'autre est absent ou corrompu, que la génération valide la plus récente gagne et qu'un tombstone signé empêche la résurrection d'une ancienne session ;
- vérifier les limites 1 minute et 30 jours, puis le rejet de toute valeur hors bornes au niveau du domaine ;
- tester une rotation, une grande police et plusieurs tailles de fenêtre ;
- tester un redémarrage avant et après échéance.

## 6. Nettoyage de l'émulateur

La version release n'est pas déclarée `testOnly`, reste protégée contre sa désinstallation hors session et ne propose pas encore de déprovisionnement. Pour rendre l'AVD propre après le test :

1. arrêtez l'émulateur ;
2. utilisez **Wipe Data** dans Android Studio Device Manager ;
3. redémarrez et vérifiez que `dpm list-owners` ne mentionne plus TempoLock.

Cette suppression efface toutes les données de l'AVD. Elle est acceptable uniquement parce que le protocole exige un émulateur dédié et sans données personnelles.

Voir aussi : [installation et build](INSTALLATION_FR.md), [utilisation et récupération](UTILISATION_FR.md), [architecture](ARCHITECTURE.md), [sécurité et limites](SECURITE.md) et [accueil du projet](../README.md).
