# NEXUS MUSIC

Lecteur audio Android futuriste, sans publicité et orienté haute fidélité.

## Disponible dans la base actuelle

- Interface NEXUS sombre, holographique et animée
- Aucun prénom affiché
- Lecture de fichiers audio locaux via Android Storage Access Framework
- Lecture en arrière-plan avec AndroidX Media3 / ExoPlayer
- Radio Internet par URL HTTPS
- Recherche de radios via Radio Browser
- Reprise de la dernière station
- Affichage du format, de la fréquence d'échantillonnage, des canaux et de la sortie audio lorsque Android expose ces informations
- Détection de sorties comme Bluetooth, USB DAC, casque filaire, HDMI et haut-parleur
- GitHub Actions pour compiler automatiquement l'APK debug

## Audio Lab — roadmap

Ces fonctions sont prévues mais ne sont pas faussement annoncées comme actives tant qu'elles ne sont pas réellement implémentées et validées :

- égaliseur paramétrique 20 bandes
- DSP interne haute précision
- convolver FIR
- profils de correction casque
- gestion avancée USB DAC / Hi-Res
- DSD lorsque la chaîne matérielle le permet
- mode bit-perfect lorsqu'Android et le périphérique de sortie le permettent
- audio spatial et profils d'écoute

Le moteur pourra accepter des sources très haute résolution, mais la fréquence et la profondeur réellement délivrées dépendent toujours du téléphone, d'Android et du DAC.

## Build

Le workflow **Android APK** compile le projet à chaque push sur `main` et publie l'APK debug comme artifact GitHub Actions.

Base technique : Java 17, Android API 36, AndroidX Media3.
