# ⏱️ Toil Tracker

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](#android-application)
[![Platform: Web](https://img.shields.io/badge/Platform-Web-blue.svg)](#standalone-web-app)

**Master your work-life balance with precision.** Toil Tracker is a high-performance work hour management system designed to track cumulative overtime, forecast year-end balances, and keep you ahead of your contractual obligations.

---

## 🚀 Key Features

- 📊 **Dynamic Progression:** Real-time visual tracking of your worked hours vs. contractual baseline.
- 🔮 **Balance Forecasting:** Predict your year-end hour balance based on current trends.
- 📱 **Native Performance:** Fully optimized Android application with a seamless WebView bridge.
- ☁️ **Self-Contained:** A standalone HTML system that works anywhere.
- 🌙 **Dark Mode First:** Premium dark aesthetic for reduced eye strain.

---

## 📸 Screenshots (Mobile View)

| Dashboard | History | Progression |
| :---: | :---: | :---: |
| ![Dashboard](screenshots/dashboard.png) | ![History](screenshots/history.png) | ![Progression](screenshots/progression.png) |

---

<a name="android-application"></a>
## 📱 Android Application

The Android version offers a robust, dedicated experience for mobile users.

### Tech Stack
- **Kotlin:** Native logic and WebView management.
- **WebView Bridge:** Bi-directional communication between JS and Kotlin.
- **Android API Level 36:** Optimized for the latest mobile features.

### Installation
1. Clone the repository.
2. Open the project in Android Studio.
3. Build and deploy the `app` module to your device.

---

<a name="standalone-web-app"></a>
## 🌐 Standalone Web App

Need a quick way to track hours on your desktop or via a server? Use the included Flask application or the standalone HTML file.

### Quick Start (Web)
```bash
# Install dependencies
pip install flask

# Launch the server
python app.py
```
Access the dashboard at `http://localhost:5000`.

---

## ⚙️ Configuration

Tailor the system to your specific contract:
1. Tap the **Setup** icon.
2. Define your **Weekly Contracted Hours**.
3. Set your **Cycle Start Date** and **Year-End** targets.
4. Input your **Standard Weekly Schedule**.

---

## 🛠️ Built With

- [Tailwind CSS](https://tailwindcss.com/) - Modern styling.
- [Chart.js](https://www.chartjs.org/) - Beautiful data visualization.
- [Flask](https://flask.palletsprojects.com/) - Lightweight Python backend.
- [Kotlin](https://kotlinlang.org/) - Powering the Android core.

---

## ⚖️ License

Distributed under the MIT License. See `LICENSE` for more information.

---

*Made with ❤️ for the hardworking.*
