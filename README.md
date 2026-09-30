# Tsundoku

Your personal library app. Keep track of the books you own, the ones you've read, and the pile you still mean to get to (*tsundoku*: buying books and letting them stack up unread).

## Features

- Add books by scanning the barcode; title, author and cover come from [Open Library](https://openlibrary.org)
- Take your own cover photo, and edit a book's cover, title and author
- Mark books as read with finish dates, ratings and reviews, including rereads
- Favorites, search, and sorting by author
- Export and import your library, plus import from Goodreads and StoryGraph

## Install (Android)

Download the latest APK from [Releases](https://github.com/phuuun/Tsundoku/releases), or add `https://github.com/phuuun/Tsundoku` to [Obtainium](https://github.com/ImranR98/Obtainium) to get updates automatically.

Requires Android 7.0 or newer.

## Build

Kotlin Multiplatform with Compose Multiplatform; shared code lives in `shared/`, the Android app in `androidApp/`, the iOS app in `iosApp/`.

```sh
./gradlew :androidApp:assembleDebug
```

Release builds are signed using `keystore.properties` in the project root (not committed):

```properties
storeFile=/path/to/tsundoku.jks
storePassword=...
keyAlias=tsundoku
keyPassword=...
```

Then `./gradlew :androidApp:assembleRelease`. Remember to bump `versionCode` in `androidApp/build.gradle.kts` for every release.
