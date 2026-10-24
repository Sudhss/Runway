# Runway - Android Personal Finance and Expense Copilot

A high-performance personal finance Android application built with Jetpack Compose, Room SQLite, and an intelligent natural language and SMS parsing engine.

Engineered for tech professionals to track monthly spend velocity, category budget envelopes, and safe daily runway in real time.

---

## Core Features

- **Liquid Net Worth Hero Card:** Real-time visibility into liquid capital, monthly spend vs. budget cap, and your exact Safe Daily Spend limit:
  $$\text{Safe Daily Spend} = \frac{\max(0, \text{Monthly Cap} - \text{Total Spent})}{\text{Days Left in Month}}$$
- **Universal Input Engine:**
  - Type or speak in plain English or Hinglish:
    - `Zomato lunch 350`
    - `Uber auto 120`
    - `Paid 17000 for flat rent`
    - `Stipend credited 45000`
  - Paste raw, unformatted bank or UPI debit SMS notifications directly.
  - Sub-300ms structured JSON parsing via lightweight LLM APIs (Groq Llama 3.1 8B or Google Gemini 1.5 Flash).
  - Built-in resilient offline regex parser when offline or without an API key.
- **Natural Language Budget and Limit Adjustments:**
  - Update category limits via voice or text:
    - `change food limit to 5000`
    - `set travel budget to 3000`
  - Adjust global financial metrics:
    - `set monthly cap to 40000`
    - `set net worth to 50000`
  - Add or delete categories on the fly:
    - `add category gym with limit 2500`
    - `delete category misc`
- **Dynamic Category Envelopes:**
  - New expense categories (e.g., Gym, Books, Gaming) are dynamically detected and auto-created with dedicated progress tracking and Material icons.
  - Interactive tap-to-edit interface on every category card with quick limit presets.
- **Voice-to-Expense:** Integrated Android speech recognition dialog (`RecognizerIntent`) for one-tap voice input.
- **Offline-First Room SQLite Architecture:** Zero cloud dependencies for core accounting. All transactions, profiles, and budget envelopes persist locally on-device.

---

## Architecture and Tech Stack

- **UI Framework:** Jetpack Compose with Material 3 Dark Palette (Obsidian `#0B0D10` with Emerald `#00E676` accents).
- **Architecture Pattern:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow.
- **Local Persistence:** Room Database (SQLite) with Kotlin Coroutine `Flow` streams.
- **Annotation Processing:** Kotlin Symbol Processing (KSP).
- **Networking and JSON:** OkHttp 4 and Gson.
- **Build System:** Gradle 8.10 with Version Catalogs (`libs.versions.toml`).
- **Target Android SDK:** Android 15 (API 35), Min SDK 24.

---

## Project Structure

```
Expense/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/sudhanshu/expense/
│   │       ├── ExpenseApplication.kt
│   │       ├── MainActivity.kt
│   │       ├── data/
│   │       │   ├── local/          # Room DB, DAOs (Expense, Category, Profile)
│   │       │   ├── model/          # Entities (ExpenseEntity, CategoryBudgetEntity, UserProfileEntity)
│   │       │   ├── remote/         # Dual Groq/Gemini client and offline heuristic parser
│   │       │   └── repository/     # Unified expense repository and command processor
│   │       └── ui/
│   │           ├── screens/        # HomeScreen, Components, SettingsDialog, CategoryDialogs
│   │           ├── theme/          # Color, Type, Theme (Obsidian Dark)
│   │           └── viewmodel/      # ExpenseViewModel and reactive StateFlows
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Build and Installation

### Compile via CLI
Ensure `JAVA_HOME` is set to JDK 21+ and run:
```bash
./gradlew assembleDebug
```
Output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run in Android Studio
1. Open Android Studio.
2. Select **File -> Open** and choose this project directory.
3. Select an emulator or connected device with USB debugging enabled.
4. Click **Run** (`Shift + F10`).
