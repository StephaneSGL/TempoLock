# Installation de TempoLock

Cette procédure installe la variante **release** `fr.tempolock.app` et la configure comme Device Owner. Faites d'abord l'essai sur un émulateur vierge. Une erreur de provisionnement sur un téléphone principal peut imposer une réinitialisation d'usine pour revenir à un état normal.

L'APK publique officielle doit être téléchargée depuis la
[dernière release GitHub](https://github.com/StephaneSGL/TempoLock/releases/latest).
Vérifiez son SHA-256 et le certificat indiqués dans la release. N'installez pas
`dist/TempoLock-1.0.0-aligned.apk` : ce fichier intermédiaire n'est pas signé.

Guides associés : [utilisation et récupération](UTILISATION_FR.md), [architecture](ARCHITECTURE.md), [sécurité et limites](SECURITE.md), [tests](TESTS.md) et [accueil du projet](../README.md).

## 1. Sauvegarder avant toute réinitialisation

Le moyen fiable d'obtenir un appareil éligible au mode Device Owner est généralement de repartir d'une configuration d'usine. Une réinitialisation efface les données locales.

Avant de la lancer :

1. sauvegardez photos, vidéos, documents, messages et fichiers d'applications ;
2. vérifiez que la synchronisation des contacts et calendriers est terminée ;
3. exportez les codes de récupération et migrez les applications d'authentification à deux facteurs ;
4. vérifiez que les sauvegardes sont lisibles depuis un autre appareil ou ordinateur ;
5. notez les comptes nécessaires après restauration et vérifiez leurs mots de passe ;
6. retirez temporairement les cartes SD si leur contenu ne doit pas être touché.

Ne considérez pas l'ancienne commande `adb backup` comme une sauvegarde complète : elle est obsolète et de nombreuses applications l'ignorent. Pour un premier essai, utilisez un AVD Android Studio et sa commande **Wipe Data** plutôt qu'un téléphone contenant des données personnelles.

## 2. Préparer l'environnement

Prérequis :

- JDK 17 ;
- Android Studio ou le SDK Android en ligne de commande ;
- Platform Tools avec `adb` accessible depuis PowerShell ;
- un Android 13 / API 33 minimum ;
- une connexion permettant à Android de fournir son heure réseau au moment de l'armement et après un redémarrage ;
- une clé release privée conservée hors du dépôt, indispensable à toute mise à jour future.

Vérifications :

```powershell
java -version
adb version
adb devices
.\gradlew.bat --version
```

Un appareil doit apparaître avec l'état `device`, pas `unauthorized` ou `offline`.

## 3. Préparer un appareil éligible

### Émulateur recommandé

1. Créez un AVD Android 13 / API 33 ou ultérieur dans Android Studio. Préférez une image **AOSP** ou **Google APIs sans Play**.
2. Dans Device Manager, utilisez **Wipe Data** pour repartir d'un état vierge.
3. Démarrez l'AVD sans ajouter de compte Google, de profil professionnel ou d'utilisateur secondaire.
4. Vérifiez la connexion avec `adb devices`.

L'E2E final a utilisé un AVD jetable Android 16 / API 36 fondé sur une image Google Play. Cette image avait déjà marqué l'assistant de configuration comme terminé malgré la préparation du test. Uniquement sur cet émulateur destiné à être effacé, les indicateurs Android `device_provisioned` et `user_setup_complete` ont été remis à `0` avant `dpm set-device-owner`. Ce contournement de laboratoire ne doit pas être utilisé comme procédure normale, encore moins sur un téléphone réel. Une image AOSP ou Google APIs sans Play évite généralement cette situation et constitue la recommandation de reproduction.

### Téléphone physique

Après la sauvegarde vérifiée, réinitialisez le téléphone et interrompez la configuration initiale avant d'ajouter un compte. Activez ensuite les options développeur et le débogage USB si le constructeur le permet à cette étape. La méthode exacte varie selon le fabricant ; si `dpm set-device-owner` refuse le provisionnement, ne supprimez pas de données au hasard : revenez à un émulateur ou refaites une configuration d'usine propre.

Android demande notamment qu'il n'existe aucun compte, aucun autre utilisateur et aucun profil professionnel. Pour un déploiement autre que développement/test, Android recommande un véritable enrôlement d'appareil entièrement géré, par exemple par QR code : [documentation Android Enterprise](https://developer.android.com/work/dpc/dedicated-devices).

## 4. Construire et signer l'APK release

Depuis la racine de TempoLock :

```powershell
.\gradlew.bat clean :app:assembleRelease
```

Avec la configuration actuelle, la sortie attendue est une APK non signée :

```text
app\build\outputs\apk\release\app-release-unsigned.apk
```

Une APK release non signée ne doit pas être installée. Deux méthodes sont possibles.

### Méthode Android Studio

Utilisez **Build > Generate Signed App Bundle or APK > APK**, choisissez `release`, une clé existante et un emplacement de sortie explicite. Ne cochez pas une option qui stockerait le mot de passe dans le dépôt.

### Méthode en ligne de commande

Créez la clé une seule fois, dans un emplacement privé extérieur au dépôt. `keytool` demande les mots de passe de façon interactive :

```powershell
keytool -genkeypair -v -keystore "D:\Cles-Android\tempolock-release.jks" -alias tempolock -keyalg RSA -keysize 4096 -validity 10000
```

Repérez le dossier `build-tools` de votre SDK, puis alignez et signez l'APK. Adaptez uniquement les deux chemins ci-dessous :

```powershell
$TempoLockBuildTools = "C:\Android\Sdk\build-tools\36.0.0"
$TempoLockKeystore = "D:\Cles-Android\tempolock-release.jks"
New-Item -ItemType Directory -Force ".\dist" | Out-Null
& "$TempoLockBuildTools\zipalign.exe" -p -f 4 ".\app\build\outputs\apk\release\app-release-unsigned.apk" ".\dist\TempoLock-1.0.0-aligned.apk"
& "$TempoLockBuildTools\apksigner.bat" sign --ks $TempoLockKeystore --ks-key-alias tempolock --out ".\dist\TempoLock-1.0.0-release.apk" ".\dist\TempoLock-1.0.0-aligned.apk"
& "$TempoLockBuildTools\apksigner.bat" verify --verbose --print-certs ".\dist\TempoLock-1.0.0-release.apk"
```

Le dernier appel doit annoncer une vérification réussie et afficher le certificat. Conservez hors ligne :

- la clé `.jks` ;
- son alias ;
- ses mots de passe ;
- l'empreinte du certificat donnée par `apksigner` ;
- au moins deux sauvegardes chiffrées et testées de ces éléments.

Cette clé privée est indispensable à toute mise à jour compatible de l'application Device Owner. Si elle est perdue, une autre clé ne peut pas la remplacer sur l'installation existante.

## 5. Installer l'APK release

Sur l'appareil vierge :

```powershell
adb install ".\dist\TempoLock-1.0.0-release.apk"
adb shell pm list packages | Select-String "fr.tempolock.app"
```

La seconde commande doit afficher exactement `package:fr.tempolock.app`. Si elle affiche uniquement `fr.tempolock.app.debug`, vous avez installé la mauvaise variante : la commande Device Owner release ci-dessous ne la trouvera pas.

## 6. Déclarer TempoLock Device Owner

Cette opération donne à TempoLock le contrôle de politiques sensibles de l'appareil. La commande exacte pour la variante release est :

```powershell
adb shell dpm set-device-owner fr.tempolock.app/.receiver.TempoLockDeviceAdminReceiver
```

Vérifiez ensuite :

```powershell
adb shell dpm list-owners
adb shell dumpsys device_policy | Select-String "fr.tempolock.app"
```

Le résultat doit identifier `fr.tempolock.app/.receiver.TempoLockDeviceAdminReceiver` comme propriétaire de l'appareil.

Si `dpm` indique que l'appareil est déjà provisionné, qu'un compte existe ou qu'un autre propriétaire est actif, n'essayez pas de contourner l'erreur avec des suppressions ADB improvisées. Effacez l'AVD et privilégiez une image sans Play, ou sauvegardez puis réinitialisez correctement le téléphone. La remise à zéro des deux indicateurs décrite plus haut était limitée à l'AVD jetable de validation ; ce n'est pas une méthode de provisionnement prise en charge. La procédure ADB officielle est documentée dans le [guide Android des appareils dédiés](https://developer.android.com/work/dpc/dedicated-devices/cookbook#development).

## 7. Premier lancement

Lancez TempoLock :

```powershell
adb shell am start -W -n fr.tempolock.app/.MainActivity
```

Utilisez le bouton affiché par TempoLock pour autoriser l'accès spécial **Alarmes et rappels**, puis revenez dans l'application. TempoLock vérifie cette autorisation avant l'armement et refuse de créer une session tant qu'elle manque.

Activez l'heure et le fuseau automatiques, puis laissez l'appareil connecté le temps qu'Android obtienne une heure réseau. Au démarrage d'un verrou, TempoLock impose les réglages automatiques et exige une valeur de `SystemClock.currentNetworkTimeClock()`. Si Android ne la fournit pas, l'armement est refusé sans lancer de session ; le message demande de connecter brièvement l'appareil puis de réessayer.

Après un armement réussi, le calcul utilise `SystemClock.elapsedRealtime()` tant que le compteur de démarrage n'a pas changé. Après un redémarrage, TempoLock exige de nouveau l'heure réseau pour comparer l'échéance persistée. Il ne se rabat pas sur l'heure murale modifiable : si l'heure réseau est indisponible, le verrou reste fermé et une nouvelle tentative est programmée.

Cette protection a une limite : `SystemClock.currentNetworkTimeClock()` fournit l'heure réseau gérée par Android, mais n'est pas conçue comme une source temporelle authentifiée pour un mécanisme de sécurité. Après un redémarrage, une source réseau malveillante ou usurpée qui annoncerait une heure artificiellement avancée pourrait théoriquement faire considérer l'échéance comme atteinte trop tôt.

### État persistant et mode fail-closed

La session est écrite dans deux journaux `AtomicFile`. Chaque enregistrement contient une génération croissante et une signature HMAC produite avec une clé de l'Android Keystore. TempoLock retient la génération valide la plus récente ; si les deux journaux valides se contredisent à la même génération, ou si aucun journal disponible n'est valide, il refuse de supposer que le verrou est terminé.

La libération n'efface pas simplement les fichiers : elle écrit un tombstone signé avec une génération supérieure. Un ancien journal de session qui survivrait à une panne partielle ne doit donc pas ressusciter le verrou. Si l'intégrité ne peut pas être établie, TempoLock reste fermé et planifie une nouvelle réconciliation.

Pour le premier essai, suivez le scénario avec le paquet factice `fr.tempolock.testtarget` dans [TESTS.md](TESTS.md#4-test-de-bout-en-bout-sur-émulateur). N'utilisez pas immédiatement Snapchat, une application bancaire, un gestionnaire de mots de passe ou une application contenant des données importantes.

## Récupération après l'échéance

### Fonctionnement normal

À l'échéance confirmée par le temps monotone du démarrage courant, ou par l'heure réseau après un redémarrage, TempoLock doit :

1. retirer la suspension du paquet ciblé ;
2. réautoriser sa désinstallation ;
3. retirer les restrictions temporaires d'installation, de débogage, d'heure, d'utilisateurs et de démarrage ;
4. remplacer la session active par un tombstone signé ;
5. revenir à l'écran de sélection.

L'application cible doit alors se lancer normalement. TempoLock reste Device Owner et reste protégé contre sa propre désinstallation, y compris hors session.

Le déroulé utilisateur et les états attendus sont regroupés dans [UTILISATION_FR.md](UTILISATION_FR.md).

### Échéance passée mais cible encore bloquée

Procédez dans cet ordre :

1. vérifiez que l'appareil dispose d'une connexion permettant à Android d'actualiser son heure réseau ;
2. ouvrez TempoLock et utilisez **Actualiser** si ce bouton est proposé ;
3. attendez la nouvelle tentative : l'alarme exacte est doublée d'un watchdog inexact, qui peut être livré en retard mais jamais avant son déclencheur ;
4. redémarrez normalement l'appareil si nécessaire ; au redémarrage, l'absence d'heure réseau maintient volontairement le verrou fermé jusqu'à une réconciliation fiable ;
5. après retrait effectif de la restriction de débogage, vérifiez l'état :

La suppression de `DISALLOW_DEBUGGING_FEATURES` retire l'interdiction, mais Android ne réactive pas nécessairement ADB dans son état antérieur. Si `adb devices` ne retrouve pas l'appareil après la libération, réactivez manuellement les options développeur et le débogage USB, puis acceptez de nouveau l'autorisation de l'ordinateur si Android la demande.

```powershell
adb shell am start -W -n fr.tempolock.app/.MainActivity
adb shell dumpsys device_policy | Select-String "fr.tempolock.app|fr.tempolock.testtarget"
```

Il n'existe volontairement aucun code de secours permettant de raccourcir une session valide. Si l'état demeure irrécupérable après l'échéance et que les restrictions empêchent toute mise à jour signée, sauvegardez ce qui reste accessible. Les recours ultimes sont ensuite la réinitialisation d'usine par la procédure du constructeur, le reflash de l'appareil ou l'effacement de l'AVD. Ces opérations sont destructrices.

### Retirer complètement TempoLock

La version actuelle ne fournit pas de fonction de déprovisionnement. Même sans session active, l'application reste Device Owner et confirme à Android le blocage de sa propre désinstallation. Pour la retirer complètement de manière garantie, sauvegardez les données nécessaires puis réinitialisez ou reflashez l'appareil. Sur un émulateur, utilisez **Wipe Data**.

Ne comptez pas sur `dpm remove-active-admin` pour une APK release : sa disponibilité pour retirer un Device Owner dépend du mode de test et de la version Android, et ce projet ne déclare pas l'application `testOnly`.

## Installer une future mise à jour signée

Attendez qu'aucun verrou ne soit actif : pendant une session, TempoLock interdit l'installation d'applications et désactive le contrôle utilisateur des paquets concernés.

La clé release privée utilisée lors de la première installation est indispensable. Conservez-la hors du dépôt avec des sauvegardes chiffrées vérifiées ; le certificat public seul ne permet pas de signer une mise à jour.

La future version doit conserver :

- l'identifiant `fr.tempolock.app` ;
- la même clé et le même certificat de signature ;
- la compatibilité avec l'état de session existant ;
- un `versionCode` strictement supérieur.

Comparez les certificats avant toute installation :

```powershell
& "$TempoLockBuildTools\apksigner.bat" verify --print-certs ".\dist\TempoLock-1.0.0-release.apk"
& "$TempoLockBuildTools\apksigner.bat" verify --print-certs "D:\Mises-a-jour\TempoLock-nouvelle-version.apk"
```

Les empreintes doivent être identiques. Installez ensuite **par-dessus** la version existante :

```powershell
adb install -r "D:\Mises-a-jour\TempoLock-nouvelle-version.apk"
```

N'essayez pas de désinstaller l'ancienne version. Une signature différente entraîne normalement `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Si la clé privée d'origine est perdue et qu'aucune mise à jour signée compatible ne peut apporter une fonction de déprovisionnement, les récupérations ultimes sont la réinitialisation d'usine ou le reflash de l'appareil.

Un APK plus ancien avec un `versionCode` inférieur n'est pas un retour arrière pris en charge. Pour revenir fonctionnellement à un code antérieur, il faut en produire une nouvelle version corrective avec un `versionCode` supérieur, la même signature et une compatibilité explicite avec l'état persistant. Voir [déprovisionnement, désinstallation et retour arrière](UTILISATION_FR.md#déprovisionnement-désinstallation-et-retour-arrière).

## Distribution locale uniquement

Cette procédure est un sideload local. Elle ne constitue ni une publication Google Play ni un enrôlement Android Enterprise de production.

Avant toute publication, il faudrait au minimum vérifier les règles Google Play en vigueur, les déclarations de données et permissions, l'éligibilité des fonctionnalités Device Policy, le parcours de désinstallation/déprovisionnement, l'enrôlement sans ADB et la gestion de la clé. `SCHEDULE_EXACT_ALARM` demande un accès accordé par l'utilisateur ; les règles diffèrent de celles de `USE_EXACT_ALARM`. Voir les [règles Google Play relatives aux alarmes exactes](https://support.google.com/googleplay/android-developer/answer/16558241) et le [guide de création d'un DPC](https://developer.android.com/work/dpc/build-dpc).

Voir aussi : [utilisation](UTILISATION_FR.md), [architecture](ARCHITECTURE.md), [sécurité](SECURITE.md), [tests](TESTS.md) et [accueil du projet](../README.md).
