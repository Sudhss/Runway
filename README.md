# Runway — AI-Powered Expense & Burn Rate Copilot ⚡📱

A modern, high-performance personal finance Android application built with **Jetpack Compose**, **Room SQLite**, and an **Intelligent Natural Language & SMS Parsing Engine** (Groq / Gemini Flash).

Designed specifically for ambitious tech professionals to manage monthly runway, track family debt allocations, and maintain a real-time **Safe Daily Burn Rate**.

---

## 📸 Key Features

- **Liquid Net Worth Hero Card:** Real-time visibility into liquid cash, monthly burn vs. budget cap, and your exact **Safe Daily Spend** limit.
- **Universal AI Input Bar:**
  - Type or dictate in plain natural language (English / Hinglish):
    - *"Zomato lunch ₹350"*
    - *"Zepto groceries 195"*
    - *"Paid 17000 to Meet for flat rent"*
    - *"IndiGo flight reimbursement credited 12221"*
  - Paste unformatted bank / UPI debit SMS notifications directly.
  - Sub-300ms parsing via lightweight free LLM APIs (Groq Llama 3.1 8B or Google Gemini 1.5 Flash).
  - Built-in resilient **offline fallback parser** if no API key is provided.
- **Category Runway Progress:**
  - Visual category tracking with progress bars:
    - 🍔 **Food & Dining** (Zomato, Swiggy, Zepto, groceries)
    - 🚕 **Travel & Auto** (Uber, Ola, flights, metro)
    - 🏠 **Housing & Rent** (Monthly flat rent, maintenance)
    - 💊 **Health & Supplements** (Apollo Pharmacy, gym, Vit D3)
    - 💻 **Tech & Utilities** (AWS, Wi-Fi, mobile recharges)
    - 💳 **Family & Debt** (Allocations for family debt repayment)
    - 🛍️ **Shopping & Misc**
- **Voice-to-Expense:** One-tap mic button using Android's speech recognition.
- **Offline-First Room SQLite Database:** All transactions, categories, and settings are cached locally. Zero data leaks.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework:** 100% Jetpack Compose with Material 3 Dark Palette (Deep space obsidian `#0B0D10` with Emerald Green `#00E676`).
- **Architecture:** Modern MVVM with Android Architecture Components.
- **Local Storage:** Room Database (SQLite) with Reactive Kotlin `Flow`.
- **Networking:** OkHttp & Gson for ultra-fast, lightweight structured JSON extraction.
- **Speech Recognition:** Android `RecognizerIntent` integration.
- **Build System:** Gradle 8.10 with Version Catalogs (`libs.versions.toml`) and KSP.

---

## 🚀 How to Run in Android Studio

1. Open **Android Studio**.
2. Select **File $\to$ Open** and choose this directory (`C:\Users\shukl\OneDrive\Documents\Projects\Expense`).
3. Click the **"Sync Project with Gradle Files"** elephant icon in the top right toolbar.
4. Plug in your Android phone with **USB Debugging** enabled, or choose an Android Emulator.
5. Hit the green **Run (▶)** button!
