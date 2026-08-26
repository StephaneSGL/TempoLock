# Politique de sécurité de TempoLock

Ce fichier décrit comment signaler une vulnérabilité et quelles versions peuvent
être examinées. Le modèle de menace, les garanties réellement mises en œuvre et
les limites connues sont documentés séparément dans
[`docs/SECURITE.md`](docs/SECURITE.md).

## Versions prises en charge

La branche de développement actuelle déclare `versionName 1.0.0`,
`versionCode 1`, `minSdk 33` (Android 13) et `targetSdk 36`.

| Version ou environnement | Traitement des signalements |
| --- | --- |
| Branche `main` actuelle et APK locale construite à partir du même code | Les problèmes reproductibles sont examinés. |
| Android 13 / API 33 ou version ultérieure | Dans le périmètre déclaré, sous réserve d’une reproduction sur la version et le modèle concernés. |
| Ancienne APK, fork, APK modifiée, build non identifiable ou Android antérieur à API 33 | Pas de support garanti ; reproduire d’abord sur `main` si possible. |
| Appareil rooté, bootloader déverrouillé, ROM modifiée ou système compromis | Hors du modèle de sécurité annoncé ; un rapport reste utile s’il démontre aussi un impact sur un appareil conforme au périmètre. |

Le projet est actuellement distribué localement et ne publie ni calendrier de
maintenance, ni délai contractuel de correction, ni canal de release public. Une
mise à jour d’un TempoLock déjà Device Owner exige le même paquet, un
`versionCode` supérieur et le certificat de signature release d’origine.

## Signaler une vulnérabilité sans divulgation publique

Ce dépôt est privé et GitHub Issues y est activé, mais le projet ne publie
actuellement **ni adresse e-mail de sécurité, ni formulaire externe, ni portail
de signalement privé vérifié**. Cette politique n’en invente pas.

1. Si vous disposez déjà d’un canal privé fiable avec le propriétaire du dépôt,
   utilisez-le et indiquez uniquement la référence du dépôt dans un premier
   message.
2. Si vous avez accès à ce dépôt privé mais aucun autre canal, ouvrez une issue
   contenant seulement un résumé non sensible, la version touchée et une demande
   d’échange privé. N’y joignez pas d’exploit, de secret ou de donnée personnelle.
3. Si vous n’avez ni accès au dépôt ni canal privé existant, aucun mécanisme de
   réception confidentielle n’est actuellement documenté. Ne publiez pas les
   détails exploitables ; un canal devra être convenu avec le propriétaire avant
   leur transmission.

Un rapport utile précise, sans donnée sensible :

- le commit, `versionName` et `versionCode` concernés ;
- la version Android, le fabricant/modèle ou l’image AVD, et l’état Device Owner ;
- les préconditions, les étapes minimales de reproduction, l’impact observé et
  le résultat attendu ;
- si le problème peut libérer une cible avant l’échéance, contourner une politique,
  altérer l’état signé, empêcher une récupération ou exposer des données locales ;
- des journaux réduits et anonymisés, seulement lorsqu’ils sont indispensables.

Il n’existe pas de procédure d’urgence qui déverrouille manuellement une session.
Un signalement ne garantit pas la récupération immédiate d’un appareil. Consultez
la [procédure de récupération](docs/UTILISATION_FR.md#déprovisionnement-désinstallation-et-retour-arrière)
avant toute opération destructive.

## Informations qui ne doivent jamais être transmises

Ne joignez jamais à une issue, une PR, un journal ou un message non chiffré :

- une clé de signature ou un fichier `.jks`, `.keystore`, `.p12`, `.pfx`, `.pem`,
  `.key`, `.pk8`, `.p8` ou `keystore.properties` ;
- un mot de passe de clé, un token, un compte de service ou une sauvegarde privée ;
- des données de session complètes, la liste personnelle des applications, un
  identifiant d’appareil, un numéro de série ou une capture non anonymisée ;
- une clé Android Keystore extraite, un état HMAC exploitable ou un code d’attaque
  lorsque le canal n’est pas confidentiel.

Le certificat public ou son empreinte peut suffire à diagnostiquer une erreur de
signature ; la clé privée n’est jamais nécessaire au signalement.

## Périmètre prioritaire

Les signalements prioritaires concernent notamment :

- une libération avant l’échéance ou un contournement reproductible sur un appareil
  Android non rooté conforme au périmètre ;
- une application partielle ou non vérifiée des politiques Device Owner ;
- une corruption, restauration ou divergence des journaux permettant de contourner
  la stratégie fail-closed ;
- une exposition non annoncée des données locales ou de secrets ;
- une mise à jour acceptée avec une signature incorrecte ou une voie non documentée
  de retrait des protections.

Les limitations déjà déclarées — recovery, root, bootloader déverrouillé, reflash,
autre appareil, clone sous un autre paquet ou absence prolongée d’heure réseau —
ne sont pas à elles seules des vulnérabilités. Elles peuvent néanmoins être
signalées si le comportement observé contredit précisément
[`docs/SECURITE.md`](docs/SECURITE.md).

## Coordination

Évitez toute divulgation publique tant que l’impact et une correction éventuelle
n’ont pas été examinés. Le propriétaire doit confirmer le périmètre, demander au
besoin une reproduction minimale et convenir explicitement du moment et du contenu
d’une publication. Cette politique ne promet aucun délai de réponse ou de correctif.
