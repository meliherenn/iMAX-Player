# 🎬 iMAX Player

Premium IPTV Player for Android TV & Android Mobile with production-grade architecture, beautiful dark theme UI, and comprehensive feature set.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2024.02.00-4285F4.svg?logo=android)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## ✨ Features

### 📺 Dual Platform
- **Android Mobile** - Touch gestures, responsive layouts
- **Android TV** - Full remote support, D-pad navigation, focus halo effects, 10ft UI

### 🎬 Content Management
- **Playlist Support** - M3U/M3U8 URL, Xtream Codes, Local file import
- **Live TV** - Category browsing, channel favorites, EPG integration
- **Movies** - Category grid browsing, poster cards with ratings
- **Series** - Season/episode browsing, progress tracking
- **Search** - Global debounced search with content type filters

### 🎮 Player Engine
- **ExoPlayer (Media3)** as primary player — HLS, DASH, Progressive support
- **VLC fallback** engine (modular stub — add libVLC dependency to enable)
- **Engine switching** on-the-fly with position preservation
- **Audio/subtitle track** selection
- **Playback speed** control (0.5x - 2x)
- **TV remote control** — Play/Pause, Seek, Back button
- **Mobile gestures** — Double-tap to seek, tap to show controls

### 🎨 Premium UI
- **Dark theme** with neon red/blue gradient palette
- **Glassmorphism** effects on hero banner and cards
- **Shimmer loading** animations
- **Focus animations** — Scale, border glow for TV navigation
- **Dynamic backdrop** blur on Home/Detail screens
- **Continue Watching** progress bars

### 📊 Metadata
- **TMDB Integration** — Posters, backdrops, cast, ratings, plot
- **Local caching** with Room for offline metadata
- **Fuzzy title matching** with Jaro-Winkler similarity

### ⚙️ Settings
- Player engine selection, seek intervals, aspect ratio
- Default audio/subtitle language
- Auto-play next episode
- Open last playlist on start
- Buffer duration control

---

## 🏗️ Architecture

```
Clean Architecture + MVVM
├── core/                    # Domain models, utilities, database, network
│   ├── common/              # Constants, Resource, StringUtils, DeviceUtils
│   ├── database/            # Room entities, DAOs, mappers
│   ├── datastore/           # DataStore preferences
│   ├── designsystem/        # Theme, colors, typography
│   ├── model/               # Domain data classes
│   ├── network/             # Retrofit APIs, DTOs
│   └── player/              # PlayerEngine, ExoPlayer, VLC, PlayerManager
├── data/                    # Repositories, parsers, clients
│   ├── parser/              # M3U parser, Xtream client
│   └── repository/          # Playlist, Content repositories
├── di/                      # Hilt modules
├── metadata/                # TMDB metadata provider
└── ui/                      # Compose screens + ViewModels
    ├── components/          # Shared UI (cards, drawer, buttons)
    ├── navigation/          # NavHost + Routes
    ├── home/                # Home screen
    ├── onboarding/          # Playlist management
    ├── live/                # Live TV
    ├── movies/              # Movies
    ├── series/              # Series
    ├── search/              # Search
    ├── settings/            # Settings
    ├── details/             # Content details
    └── player/              # Video player
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Compose for TV |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Networking | Retrofit + OkHttp |
| Serialization | Kotlinx Serialization |
| Image Loading | Coil |
| Video | Media3 ExoPlayer |
| Async | Coroutines + Flow |
| Logging | Timber |

---

## 🚀 Setup & Run

### Prerequisites
- Android Studio Ladybug (2024.1+)
- JDK 17+
- Android SDK 34

### Steps

1. **Clone the repository**
```bash
git clone https://github.com/your-username/imax-player.git
cd imax-player
```

2. **Configure local.properties**
```properties
sdk.dir=/path/to/your/Android/Sdk

# Optional: TMDB API key for movie metadata
TMDB_API_KEY=your_tmdb_api_key_here
```

3. **Build & Run**
```bash
# For mobile
./gradlew installDebug

# Or open in Android Studio and run
```

4. **For Android TV**
- Use an Android TV emulator or ADB-connect to your TV
- The app auto-detects the platform and adapts its UI

### Getting a TMDB API Key (Optional)
1. Create a free account at [TMDB](https://www.themoviedb.org/signup)
2. Go to Settings → API → Create new API key
3. Add it to `local.properties` as shown above

---

## 📱 Usage

1. **Launch the app** → Onboarding screen appears
2. **Add a playlist** — Choose M3U URL, Xtream Codes, or local file
3. **Test connection** — Verify your provider is reachable
4. **Save & select** → App syncs content and navigates to Home
5. **Browse & play** — Explore Live TV, Movies, Series
6. **Use the player** — Full controls, track selection, speed, seek

### TV Remote Mapping
| Key | Action |
|-----|--------|
| OK / Enter | Play / Pause |
| ← → | Seek backward / forward |
| ↑ ↓ | Show controls |
| Back | Exit player |
| Media keys | Play, Pause, FF, RW |

---

## ⚖️ Legal Notice

**iMAX Player does NOT provide any content.** It is a media player that plays user-provided streams. Users are responsible for ensuring they have the right to access the content they add to the app. The developers of iMAX Player are not responsible for any misuse.

---

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
