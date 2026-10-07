# Symphony

Lecteur de musique Android pour les fichiers audio stockés sur le téléphone.
Jetpack Compose, Material 3, Media3. Android 8.0 et plus.

## Obtenir l'APK avec GitHub

1. Créez un dépôt GitHub et envoyez-y le contenu de ce dossier, sur la branche `main`.
2. Ouvrez l'onglet **Actions** du dépôt : la tâche « Build APK » démarre à chaque envoi.
3. Quand elle est terminée, ouvrez-la et téléchargez l'artefact `symphony-debug-apk`.
4. Décompressez-le, copiez le fichier `.apk` sur le téléphone et installez-le.

## Compiler sur un ordinateur

Ouvrez le dossier dans Android Studio, ou lancez `./gradlew assembleDebug`.
L'APK se trouve dans `app/build/outputs/apk/debug/`.

## Ce que contient cette version

- Accueil, Albums, Artistes, Bibliothèque, Recherche, pages Album, Artiste et Playlist.
- Lecteur plein écran : pochette bord à bord, fond teinté par la pochette, progression,
  volume, aléatoire, répéter, favoris, file d'attente, paroles.
- Barre flottante : mini-player, quatre onglets, bouton de recherche.
- Lecture en arrière-plan, notification, écran verrouillé, commandes du casque.
- Paroles : fichier `.lrc` à côté du morceau, ou paroles intégrées aux balises du fichier.
- Favoris et playlists enregistrés sur le téléphone.
- Réglages : thème, Liquid Glass, pochette plein écran, paroles synchronisées, silences.
- Interface en français, et en anglais quand le téléphone est en anglais.

## Limites connues

- Le verre de la barre flottante est translucide, sans flou du contenu derrière.
- La file d'attente et les playlists ne se réordonnent pas encore par glisser.
- Pas d'égaliseur, pas de mise en page tablette.
- Police du système (la police Figtree des maquettes n'est pas embarquée).
