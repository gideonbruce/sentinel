# 🚨 Sentinel – Smart Safety & Alert System

> **Sentinel** is an intelligent Android safety application that detects emergencies using motion and device sensors, automatically triggering alerts to predefined contacts.  
> Built with precision for speed, accuracy, and reliability.

---

## 📝 Table of Contents

- [Features](#-features)
- [Project Structure](#-project-structure)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [Usage](#-usage)
- [Contributing](#-contributing)
- [License](#-license)

---

## 📱 Features

| Feature | Description |
|----------|--------------|
| 🧠 **Smart Detection** | Detects sudden shakes, impacts, or emergency gestures using the device's accelerometer and gyroscope. |
| 📍 **Live Location** | Sends GPS coordinates to your emergency contacts instantly for a precise location. |
| 🔔 **Instant Alerts** | Auto-sends SMS or notification alerts to a predefined contact when an emergency is triggered. |
| 🕒 **Alert History** | View previously triggered alerts within the app, including the date, time, and location of the emergency. |
| ⚙️ **Customizable Settings** | Adjust the sensitivity of the shake detection, enable or disable detection methods, and manage your emergency contact. |
| 🌙 **Background Mode** | Runs quietly in the background, ready to detect an emergency even when the app is not in the foreground. |
| 🗣️ **AI-Powered Emergency Messages** | (Optional) Uses AI to generate more descriptive and context-aware emergency messages. |
| 🆘 **Emergency Dialog** | Displays a confirmation dialog before sending an alert, allowing the user to cancel a false alarm. |

---

## 📂 Project Structure

The project is organized into the following packages:

-   `ai`: Contains the `AIMessageGenerator.java` for generating AI-powered emergency messages.
-   `core`: Core functionalities like `EmergencyShakeService`, `LocationGeocoder`, `ShakeDetector`, and `VolumeButtonGestureDetector`.
-   `data`: Handles data persistence with `AlertDao`, `AlertDatabase`, `AlertEntity`, `AlertRepository`, and `EmergencyContactManager`.
-   `ml`: Includes the machine learning components for fall detection, such as `FallDetectionModel`, `FallDetectionService`, and `SensorDataCollector`.
-   `sentinel`: The main package containing all the activities (`MainActivity`, `LoginActivity`, etc.), services, and the application class.
-   `ui`: Contains UI-related classes like `EmergencyAlertDialog`.

---

## 🏗️ Tech Stack

| Layer | Technology |
|-------|-------------|
| **Frontend** | Android (Java / Kotlin) |
| **UI** | Material Design Components |
| **Database** | Room (SQLite ORM) |
| **Cloud Integration** | Firebase (for notifications & backups) |
| **Machine Learning** | TensorFlow Lite |
| **Build System** | Gradle |

---

## 🚀 Getting Started

### 1️⃣ Clone the Repository
```bash
git clone https://github.com/<your-username>/Sentinel.git
cd Sentinel
```

### 2️⃣ Open in Android Studio
Open the project in Android Studio and wait for Gradle to sync.

### 3️⃣ Run the App
Connect a device or start an emulator and run the app.

---

## ⚙️ Configuration

### Firebase
1.  **Create a Firebase Project**: Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project.
2.  **Add an Android App**: Add a new Android app to your Firebase project with the package name `com.example.sentinel`.
3.  **Download `google-services.json`**: Download the `google-services.json` file and place it in the `app` directory of the project.
4.  **Enable Authentication**: In the Firebase Console, go to **Authentication** and enable the **Google** sign-in method.
5.  **Enable Realtime Database**: In the Firebase Console, go to **Realtime Database** and create a new database.

### Google Maps API
To enable location features, you need a Google Maps API key.
1.  Go to the [Google Cloud Console](https://console.cloud.google.com/).
2.  Create a new project.
3.  Enable the **Geocoding API** and **Maps SDK for Android**.
4.  Create an API key and restrict it to your app's package name and SHA-1 certificate fingerprint.
5.  Add the API key to your `local.properties` file:
    ```
    MAPS_API_KEY=YOUR_API_KEY
    ```

### Gemini API
To use the AI-powered emergency messages, you need a Gemini API key.
1.  Go to the [Google AI Studio](https://aistudio.google.com/).
2.  Create a new API key.
3.  In the `MainActivity.java` file, replace `"YOUR_GEMINI_API_KEY_HERE"` with your actual Gemini API key in the `saveGeminiApiKey()` method.

---

## Usage

1.  **Sign In**: The first time you open the app, you will be prompted to sign in with your Google account.
2.  **Set an Emergency Contact**: After signing in, you will need to add an emergency contact. You can either pick a contact from your phone or enter the details manually.
3.  **Start the Service**: Press the "Start Service" button to activate the emergency detection service. The service will run in the background and monitor for shakes or volume button presses.
4.  **Trigger an Alert**:
    *   **Shake**: Shake your device vigorously to trigger an alert.
    *   **Volume Buttons**: Press the volume up and down buttons in a specific sequence.
5.  **Emergency Dialog**: When an alert is triggered, an emergency dialog will appear. You will have a few seconds to cancel the alert before it is sent to your emergency contact.
6.  **View Alert History**: You can view a history of all the alerts that have been sent from the app in the "Alert History" section.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue if you have any suggestions or find any bugs.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
