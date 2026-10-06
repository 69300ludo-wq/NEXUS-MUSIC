# NEXUS MUSIC 1.0.0

Lecteur audio Android futuriste, sans publicité et orienté haute fidélité.

## Version finale 1.0

Fonctions actives :
- interface NEXUS holographique / cockpit
- aucun prénom affiché
- lecture de fichiers audio locaux
- lecture en arrière-plan avec AndroidX Media3 / ExoPlayer
- radio Internet mondiale via flux HTTPS et Radio Browser
- recherche radio par station, pays et genre
- reprise de la dernière station
- affichage du format audio, fréquence d'échantillonnage et canaux lorsque disponibles
- détection de la route audio Android : Bluetooth, USB DAC, casque filaire, HDMI ou haut-parleur
- contrôles lecture / pause / position
- Audio Lab avec diagnostic de la chaîne audio
- aucune publicité

## Audio Lab — fonctions avancées non simulées

Ces fonctions restent affichées comme ROADMAP tant qu'elles ne sont pas réellement implémentées et validées :
- égaliseur paramétrique 20 bandes natif
- convolver FIR
- correction casque
- chemin USB DAC exclusif
- DSD natif
- bit-perfect garanti
- spatialisation avancée

NEXUS MUSIC ne prétend pas fournir une capacité que le téléphone, Android ou le DAC ne permettent pas réellement.

## Build

La pipeline **NEXUS MUSIC Final** construit une variante Android `release`, vérifie l'APK avec les outils Android et publie :
- `NEXUS-MUSIC-v1.0.0-FINAL.apk`
- son SHA-256

Base technique : Java 17, Android API 36, AndroidX Media3.
