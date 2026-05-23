# SMS Clone 📱

A specialized Android tool designed to bridge the gap between mobile communication and AI dataset preparation. This app extracts SMS conversations and transforms them into clean, structured JSON files perfect for training Large Language Models (LLMs) or fine-tuning conversational AI.

## 🎯 Project Intention
The primary goal of **SMS Clone** is to empower developers and researchers to create personal conversational datasets from their own device history. By grouping fragmented SMS messages into chronological, contact-specific threads, it provides a high-quality data source for training AI that understands personal context and communication styles.

## ✨ Key Features
- **Forensic Acquisition Engine**: One-tap extraction of Call Logs, Contacts, Calendar Events, Browser History, and App Usage Statistics.
- **Media EXIF Analysis**: Automatically extracts GPS coordinates, device models, and timestamps from photos for geospatial mapping.
- **Chain of Custody (Integrity)**: Generates SHA-256 hashes for every extracted file, ensuring data integrity and forensically sound evidence handling.
- **Comprehensive Reporting**: Generates high-quality HTML forensic reports including device summaries and integrity manifests.
- **Evidence Packaging**: Automatically packages all artifacts into a single compressed forensic container (.zip).
- **Real-Time Safety Net**: Instantly detects and logs all SMS changes (Insertions, Updates, and Deletions). This creates a "recycle bin" that saves messages before they are permanently lost.
- **Intelligent Threading**: Automatically groups messages by contact and `thread_id` to maintain natural conversation flow.
- **Custom Storage Path**: Full integration with Android's Storage Access Framework (SAF), allowing you to save evidence to any folder or SD card.
- **Daily Auto-Backup**: Background scheduling via `WorkManager` ensures your forensic dataset stays up-to-date.
- **Premium Forensic UI**: A professional dark teal interface designed for rapid field acquisition.
- **SQLite Recovery (Root)**: Integrated hooks for parsing unallocated database pages to recover deleted records on rooted devices.

## 🛠 Technology Stack
- **Language**: Kotlin
- **Asynchronous Logic**: Kotlin Coroutines & Supervisor Jobs
- **Background Processing**: Foreground Services & WorkManager API
- **Local Database**: Room (Shadow Mirroring for change detection)
- **Forensic Utilities**: ExifInterface, UsageStatsManager, CallLog Provider, ContactsContract
- **Integrity**: SHA-256 Hashing (MessageDigest)
- **JSON Serialization**: Google Gson
- **Storage**: Storage Access Framework (SAF) & DocumentFile API
- **UI Components**: Material 3, CoordinatorLayout, Splash Screen API

## 🚀 How to Use
1. **Grant Permissions**: Upon first launch, the app will request access to SMS, Contacts, and Notifications.
2. **Select Storage**: Tap **"STORAGE PATH"** to define where you want your JSON files to live.
3. **Manual Export**: Hit **"EXPORT DATA"** to generate your initial dataset.
4. **Safety Net**: The app will start monitoring in the background. Check your storage folder for `deleted_safety_net.jsonl` to see the change history.
5. **Schedule**: Toggle **"Auto-Sync"** to automate the daily extraction process.

---

### Created by [@dawillygene](https://github.com/dawillygene) ✍️
*Transforming personal data into intelligent insights.*
