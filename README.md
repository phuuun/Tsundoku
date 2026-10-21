# Tsundoku

Your personal library app. Keep track of the books you own, the ones you've read, and the pile you still mean to get to (*tsundoku*: buying books and letting them stack up unread).

## Features

- Add books by scanning the barcode; title, author and cover come from [Open Library](https://openlibrary.org)
- Your books stand on a bookshelf, spine out with the odd one face out; hold a book to pull it off the shelf and see its cover
- Pocket, regular and tall books stand at their own height (looked up on Open Library, or set it yourself)
- Photograph a book's cover or spine, crop it, and edit its title and author
- Mark books as read with finish dates, ratings and reviews, including rereads
- Favorites (always first on the shelf), search, and sorting by author's surname
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
