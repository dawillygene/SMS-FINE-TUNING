# DawillyGene SMS Clone 📱

A specialized Android tool designed to bridge the gap between mobile communication and AI dataset preparation. This app extracts SMS conversations and transforms them into clean, structured JSON files perfect for training Large Language Models (LLMs) or fine-tuning conversational AI.

## 🎯 Project Intention
The primary goal of **DawillyGene SMS** is to empower developers and researchers to create personal conversational datasets from their own device history. By grouping fragmented SMS messages into chronological, contact-specific threads, it provides a high-quality data source for training AI that understands personal context and communication styles.

## ✨ Key Features
- **Intelligent Threading**: Automatically groups messages by contact and `thread_id` to maintain conversation flow.
- **AI-Ready Export**: Generates structured JSON files with clear "me" vs "contact" labels and timestamps.
- **Custom Storage Path**: Full integration with Android's Storage Access Framework (SAF), allowing you to save backups to any folder, SD card, or cloud-synced directory.
- **Daily Auto-Backup**: Background scheduling via `WorkManager` ensures your AI dataset stays up-to-date without opening the app.
- **Modern UI/UX**: Built with **Material 3** design principles, featuring a premium look, responsive progress indicators, and a dedicated splash screen.
- **Privacy First**: Works entirely offline. Your messages never leave your device unless you move the exported JSON files yourself.

## 🛠 Technology Stack
- **Language**: Kotlin
- **Asynchronous Logic**: Kotlin Coroutines & Lifecycle Scope
- **Background Processing**: WorkManager API
- **JSON Serialization**: Google Gson
- **Storage**: Storage Access Framework (SAF) & DocumentFile API
- **UI Components**: Material 3, CoordinatorLayout, Splash Screen API

## 🚀 How to Use
1. **Grant Permissions**: Upon first launch, the app will request access to your SMS and Contacts.
2. **Select Storage**: Tap "Storage Path" to define where you want your JSON files to live.
3. **Manual Export**: Hit "Export All Now" to generate your initial dataset.
4. **Schedule**: Toggle "Daily Auto-backup" to automate the extraction process.

---

### Created by [@dawillygene](https://github.com/dawillygene) ✍️
*Transforming personal data into intelligent insights.*
