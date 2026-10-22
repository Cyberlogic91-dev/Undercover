# Undercover 🕵️

Jeu d'ambiance **Undercover** pour Android, 100 % hors ligne, en français : on se passe le téléphone, chacun découvre son mot secret… et il faut démasquer l'imposteur.

**Développé par Mickaël Monestier.**

## 📲 Installer

1. Télécharge le dernier fichier `Undercover-vX.Y.apk` dans les [Releases](../../releases/latest).
2. Ouvre-le sur ton téléphone et autorise l'installation depuis cette source.
3. Les versions suivantes se proposent toutes seules : l'appli vérifie les nouveautés au lancement (désactivable dans Réglages › Mises à jour automatiques).

Android 8.0 minimum.

## ✨ Fonctionnalités

- **3 333 paires de mots très proches** dans 17 catégories, jamais deux fois la même (mémoire des mots joués)
- Rôles **Civils, Undercover, Mr. White** et **Garde du corps** 🛡️ en option
- Configurations de rôles **automatiques** selon le nombre de joueurs
- **Photos des joueurs**, choix des cartes face cachée, bouton « i » avec la définition de chaque mot
- Révélation des rôles **avec suspense**, chrono de débat, égalités au vote
- **Narrateur vocal**, sons et vibrations, **thèmes saisonniers** (Halloween, Noël, Été)
- **Championnat de la soirée** avec podium animé, **13 badges** à débloquer, statistiques
- **Partage** du résultat en image (WhatsApp, SMS…)
- **Mode adulte 18+** 🔥 (catégories coquines) verrouillé par une confirmation d'âge
- Tutoriel illustré au premier lancement

## 🛠️ Compiler

Kotlin + Jetpack Compose, Gradle 8.14, JDK 17+.

```bash
./gradlew assembleRelease
```

## 🚀 Publier une nouvelle version

1. Augmenter `versionCode` et `versionName` dans `app/build.gradle.kts`.
2. Décrire les nouveautés dans `CHANGELOG-latest.md` (elles s'affichent dans l'appli).
3. Pousser sur `main` : dès qu'un nouveau numéro de version arrive, GitHub Actions compile l'APK, le signe avec la clé stockée dans les secrets du dépôt (`UC_KEYSTORE_B64`, `UC_STORE_PASSWORD`, `UC_KEY_ALIAS`, `UC_KEY_PASSWORD`) et publie la release. Les téléphones la reçoivent automatiquement.

> ⚠️ Toujours signer avec **la même clé** : sinon Android refuse d'installer la mise à jour par-dessus.

## 📄 Licences

Police Montserrat sous licence SIL Open Font (voir `FONT-LICENSE-OFL.txt`). Visuels, logo et sons sont des créations originales.
