# Qdrant Offline Android SDK

> **Author**: Abhishek Thakur  
> **Repository**: https://github.com/athakurbng-lab/qdrant_integration

A **fully offline** vector search database for Android using Qdrant's core engine with Rust JNI bindings. **No internet connection, no cloud service, no external dependencies required!**

## 🌟 Features

- ✅ **100% Offline** - Complete vector database running entirely on-device
- ✅ **No Internet Required** - Zero network calls, works in airplane mode
- ✅ **Fast HNSW Search** - Sub-millisecond vector similarity search (0.44ms/query)
- ✅ **High Accuracy** - 100% recall on test dataset (perfect match with brute-force)
- ✅ **384-Dimensional Vectors** - Support for standard embedding sizes
- ✅ **Cosine Similarity** - Optimized for semantic search
- ✅ **File-Based Testing** - Import JSON vector data easily
- ✅ **Brute-Force Validation** - Built-in recall accuracy testing

## 📖 What is HNSW?

**HNSW (Hierarchical Navigable Small World)** is a graph-based algorithm for approximate nearest neighbor (ANN) search.

### How HNSW Works

Think of HNSW as a multi-level highway system:

1. **Hierarchical Layers**: 
   - Top layer: Like highways connecting major cities (sparse, long jumps)
   - Middle layers: Like state roads (moderate connectivity)
   - Bottom layer: Like local streets (dense, every point connected)

2. **Search Process**:
   ```
   Start at entry point (top layer)
   ↓
   Greedy search to nearest neighbor in current layer
   ↓
   Move down to next layer
   ↓
   Repeat until bottom layer
   ↓
   Return K nearest neighbors
   ```

3. **Why It's Fast**:
   - **O(log N)** search complexity instead of O(N) brute-force
   - Top layers skip large portions of the search space
   - Bottom layer ensures accuracy with local connections

### HNSW Parameters

- **`m`** (16): Number of bi-directional links per node
  - Higher = Better recall, more memory
  - Lower = Faster build, less memory
  
- **`ef_construct`** (100): Size of dynamic candidate list during construction
  - Higher = Better graph quality, slower build
  - Lower = Faster build, potentially lower recall

- **`ef`** (search time): Size of dynamic candidate list during search
  - Higher = Better recall, slower search
  - Lower = Faster search, potentially lower recall

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│         Android Application             │
│    (MainActivity.java)                  │
└──────────────┬──────────────────────────┘
               │ JNI calls
┌──────────────▼──────────────────────────┐
│    Java Wrapper (OfflineQdrant.java)    │
└──────────────┬──────────────────────────┘
               │ Native methods
┌──────────────▼──────────────────────────┐
│  Rust JNI Layer (lib.rs)                │
│  • init()                               │
│  • createCollection()                   │
│  • update() [upsert/delete]             │
│  • search()                             │
└──────────────┬──────────────────────────┘
               │ Direct calls
┌──────────────▼──────────────────────────┐
│  Qdrant Core Engine                     │
│  • HNSW Index                           │
│  • Vector Storage                       │
│  • Segment Management                   │
└─────────────────────────────────────────┘
```

## 📋 Prerequisites

### Required Tools

1. **Android Studio** (2023.1 or later)
   ```bash
   # Download from:
   https://developer.android.com/studio
   ```

2. **Rust Toolchain** (1.70+)
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   source $HOME/.cargo/env
   ```

3. **Android NDK** (r25c or later)
   ```bash
   # In Android Studio:
   # Tools → SDK Manager → SDK Tools → NDK (Side by side)
   ```

4. **cargo-ndk** (for cross-compilation)
   ```bash
   cargo install cargo-ndk
   ```

5. **Android Target**
   ```bash
   rustup target add aarch64-linux-android
   ```

6. **Java/JDK** (17 or 21)
   ```bash
   # macOS with Homebrew:
   brew install openjdk@21
   
   # Set JAVA_HOME:
   export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
   ```

7. **Python 3** (for test data generation)
   ```bash
   python3 --version  # Should be 3.7+
   ```

## 🚀 Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/athakurbng-lab/qdrant_integration.git
cd qdrant_integration/QdrantAndroidSDK
```

### 2. Build Native Library

```bash
cd qdrant_offline_android

# Build for ARM64 (most modern Android devices)
cargo ndk -t arm64-v8a build --release

# Copy to jniLibs
cp target/aarch64-linux-android/release/libqdrant_offline_android.so \
   ../qdrant-android-sdk/src/main/jniLibs/arm64-v8a/
```

### 3. Build Android App

```bash
cd ..

# Set JAVA_HOME if not already set
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"

# Build debug APK
./gradlew :app:assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### 4. Generate Test Data

```bash
# Generate 5K vectors for upsert, 100 for search
python3 generate_small_data.py

# Output files:
# - upsert_5k.json (5,000 vectors, 384 dimensions)
# - search_100.json (100 search queries)
```

### 5. Install and Test

```bash
# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Push test data to device
adb push upsert_5k.json /storage/emulated/0/Download/
adb push search_100.json /storage/emulated/0/Download/

# Open the app and:
# 1. Tap "Select Upsert File" → choose upsert_5k.json
# 2. Tap "Select Search File" → choose search_100.json
# 3. Tap "Run Test"
# 4. Tap "Check Recall" to validate accuracy
```

## 📱 Using the SDK in Your App

### 1. Add Dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":qdrant-android-sdk"))
}
```

### 2. Initialize Qdrant

```java
import com.qdrant.client.OfflineQdrant;

// Initialize (once at app startup)
File filesDir = getExternalFilesDir(null);
String storagePath = new File(filesDir, "qdrant_storage").getAbsolutePath();
String result = OfflineQdrant.init(storagePath);

if (!result.equals("OK")) {
    Log.e("Qdrant", "Init failed: " + result);
    return;
}
```

### 3. Create Collection

```java
String collectionConfig = """
{
  "vectors": {
    "size": 384,
    "distance": "Cosine"
  }
}
""";

boolean success = OfflineQdrant.createCollection("my_collection", collectionConfig);
```

### 4. Operations Guide

#### Batch Upsert (Recommended for Performance)

Instead of one by one, insert points in batches (e.g., 100-500 at a time).

```java
String batchUpsertJson = """
{
  "points": [
    {
      "id": 1, 
      "vector": [...], 
      "payload": {"text": "Doc 1", "category": "news"}
    },
    {
      "id": 2, 
      "vector": [...], 
      "payload": {"text": "Doc 2", "category": "tech"}
    },
    // ... add up to 500 points
    {
      "id": 100, 
      "vector": [...], 
      "payload": {"text": "Doc 100", "category": "sports"}
    }
  ]
}
""";

boolean success = OfflineQdrant.update("my_collection", batchUpsertJson);
```

#### Soft Delete (Using Null/Field Check)

You can "soft delete" by setting a specific field (e.g., `deleted_at`) and filtering for it.

**Step 1: Mark as Deleted**
```java
// Update point to mark as deleted
String softDeleteJson = """
{
  "points": [
    {
      "id": 1,
      "vector": [...], // Need to provide vector to update
      "payload": {
        "text": "Original content",
        "deleted_at": 1734364824000 // Set timestamp
      }
    }
  ]
}
""";
OfflineQdrant.update("my_collection", softDeleteJson);
```

**Step 2: Filter Soft-Deleted Items**
```java
// Query: Find items where deleted_at IS NULL (not deleted)
String searchJson = """
{
  "vector": [...],
  "limit": 10,
  "filter": {
    "must": [
      {
        "key": "deleted_at",
        "is_null": true    // Only return items where this field is missing/null
      }
    ]
  }
}
""";
String results = OfflineQdrant.search("my_collection", searchJson);
```

#### Hard Delete (Permanent Removal)

Permanently remove points to free up space.

```java
String hardDeleteJson = """
{
  "delete": {
    "points": [1, 2, 5, 10] // IDs to remove
  }
}
""";
OfflineQdrant.update("my_collection", hardDeleteJson);
```

#### Complex Queries (Filter combinations)

Low-level building block of complex logic.

```java
// Find: category="tech" AND (rating >= 4.0) AND NOT cancelled
String searchJson = """
{
  "vector": [...],
  "limit": 10,
  "filter": {
    "must": [
      { "key": "category", "match": { "value": "tech" } },
      { "key": "rating", "range": { "gte": 4.0 } }
    ],
    "must_not": [
      { "key": "cancelled", "match": { "value": true } }
    ]
  }
}
""";
```

#### Searching in Batches

Perform multiple searches in a single call.

`OfflineQdrant` currently supports single search requests. For batch search, simply loop through your queries and call `search()` sequentially. The native HNSW index is fast enough (sub-millisecond) that sequential calls are highly efficient.

```java
List<String> queries = Arrays.asList("query1", "query2");
for (String query : queries) {
    float[] vector = getEmbedding(query);
    String result = OfflineQdrant.search("my_collection", createSearchJson(vector));
    // Process result...
}
```


### 5. Search

```java
// Prepare search request
String searchJson = """
{
  "vector": [0.1, 0.2, 0.3, ...],  // 384 dimensions
  "limit": 10
}
""";

// Execute search
String resultsJson = OfflineQdrant.search("my_collection", searchJson);

// Parse results
JSONArray results = new JSONArray(resultsJson);
for (int i = 0; i < results.length(); i++) {
    JSONObject hit = results.getJSONObject(i);
    int id = hit.getInt("id");
    double score = hit.getDouble("score");
    // Use results...
}
```

### 6. Advanced Operations (Deletion & Filtering)

For comprehensive documentation on deletion strategies and filtering, see **[ADVANCED_OPERATIONS.md](ADVANCED_OPERATIONS.md)**:

**Key Features:**
- ✅ **Hard Delete**: Permanently remove points from database
- ✅ **Soft Delete**: Mark as deleted with payload flag (recoverable)
- ✅ **Filtering**: Search with must/should/must_not conditions
- ✅ **Range Queries**: Numeric filters (gte, lte, gt, lt)
- ✅ **Complex Queries**: Combine multiple filter conditions

**Quick Examples:**

```java
// Hard delete points
String deleteJson = """
{"delete": {"points": [1, 2, 3]}}
""";
OfflineQdrant.update("my_collection", deleteJson);

// Soft delete (mark as deleted)
String softDeleteJson = """
{
  "points": [{
    "id": 1,
    "vector": [...],
    "payload": {"deleted": true, "deleted_at": 1734364824000}
  }]
}
""";
OfflineQdrant.update("my_collection", softDeleteJson);

// Search excluding soft-deleted items
String searchJson = """
{
  "vector": [...],
  "limit": 10,
  "filter": {
    "must_not": [
      {"key": "deleted", "match": {"value": true}}
    ]
  }
}
""";
String results = OfflineQdrant.search("my_collection", searchJson);
```

## 💾 Storing and Retrieving Extra Information (Payload)

Each point in Qdrant can store **additional metadata** alongside the vector using the `payload` field.

### What Can You Store in Payload?

✅ **Strings**: Text, sentences, URLs, names  
✅ **Numbers**: Integers, floats, doubles  
✅ **Booleans**: true/false flags  
✅ **Arrays**: Lists of values  
✅ **Nested Objects**: Complex JSON structures  

**Size Recommendation**: Keep payloads under 1-2 KB for optimal performance

### Storing Data with Payload

```java
// Example: Store a document with metadata
String upsertJson = """
{
  "points": [
    {
      "id": 1,
      "vector": [0.123, -0.456, 0.789, ...],  // Your 384-dim embedding
      "payload": {
        "text": "How to make espresso coffee",
        "title": "Coffee Brewing Guide",
        "category": "food",
        "author": "Abhishek Thakur",
        "date": "2025-12-16",
        "url": "https://example.com/article/123",
        "views": 1500,
        "published": true,
        "tags": ["coffee", "brewing", "espresso"]
      }
    },
    {
      "id": 2,
      "vector": [0.321, 0.654, -0.987, ...],
      "payload": {
        "text": "Best coffee beans for cold brew",
        "title": "Cold Brew Selection",
        "category": "food",
        "author": "Abhishek Thakur",
        "date": "2025-12-15",
        "rating": 4.5,
        "premium": true
      }
    }
  ]
}
""";

boolean success = OfflineQdrant.update("my_collection", upsertJson);
```

### Retrieving Payload from Search Results

```java
// Search for similar vectors
String searchJson = """
{
  "vector": [0.111, -0.222, 0.333, ...],
  "limit": 10
}
""";

String resultsJson = OfflineQdrant.search("my_collection", searchJson);
JSONArray results = new JSONArray(resultsJson);

// Extract all information from results
for (int i = 0; i < results.length(); i++) {
    JSONObject hit = results.getJSONObject(i);
    
    // Basic fields
    int id = hit.getInt("id");
    double score = hit.getDouble("score");  // Similarity score (higher = more similar)
    
    // Access payload - ALL your extra info!
    JSONObject payload = hit.getJSONObject("payload");
    
    // Extract your custom fields
    String text = payload.getString("text");
    String title = payload.getString("title");
    String category = payload.getString("category");
    String author = payload.getString("author");
    String date = payload.getString("date");
    
    // Optional fields (check if exists)
    if (payload.has("url")) {
        String url = payload.getString("url");
    }
    
    if (payload.has("rating")) {
        double rating = payload.getDouble("rating");
    }
    
    if (payload.has("tags")) {
        JSONArray tags = payload.getJSONArray("tags");
        for (int j = 0; j < tags.length(); j++) {
            String tag = tags.getString(j);
        }
    }
    
    // Use your data
    Log.d("Results", String.format("#%d (score: %.3f) - %s by %s", 
          i+1, score, title, author));
}
```

### Programmatic Example (Building JSON in Code)

```java
// Build upsert request programmatically
int myId = 456;
float[] myEmbedding = getEmbeddingFromModel("your text");  // From your ML model
String originalText = "This is my sentence";

JSONObject point = new JSONObject();
point.put("id", myId);

// Add vector
JSONArray vectorArray = new JSONArray();
for (float val : myEmbedding) {
    vectorArray.put(val);
}
point.put("vector", vectorArray);

// Add ALL your extra info to payload
JSONObject payload = new JSONObject();
payload.put("text", originalText);
payload.put("category", "technology");
payload.put("author", "Abhishek Thakur");
payload.put("timestamp", System.currentTimeMillis());
payload.put("source", "mobile_app");
payload.put("language", "en");
// Add as many custom fields as you need!
point.put("payload", payload);

// Wrap in points array
JSONObject upsertRequest = new JSONObject();
JSONArray points = new JSONArray();
points.put(point);
upsertRequest.put("points", points);

// Store in Qdrant
boolean success = OfflineQdrant.update("my_collection", upsertRequest.toString());
```

### Use Cases for Payload

1. **RAG (Retrieval-Augmented Generation)**
   - Store: `{"text": "...", "chunk_id": 5, "document_title": "..."}`
   - Retrieve original text chunks for LLM context

2. **Semantic Search**
   - Store: `{"title": "...", "summary": "...", "url": "..."}`
   - Display rich results with metadata

3. **Recommendation System**
   - Store: `{"product_name": "...", "price": 99.99, "image_url": "..."}`
   - Show recommended items with details

4. **Document Classification**
   - Store: `{"text": "...", "label": "spam", "confidence": 0.95}`
   - Track classifications with vectors


## 🧪 Test Data Format

### Upsert File (`upsert_5k.json`)

```json
{
  "id": 1,
  "vector": [0.123, -0.456, 0.789, ...]
}
{
  "id": 2,
  "vector": [0.321, 0.654, -0.987, ...]
}
...
```

### Search File (`search_100.json`)

```json
{
  "vector": [0.111, -0.222, 0.333, ...],
  "limit": 10,
  "truth_ids": [42, 17, 99, ...]
}
{
  "vector": [0.444, 0.555, -0.666, ...],
  "limit": 10,
  "truth_ids": [3, 51, 88, ...]
}
...
```

## ⚙️ Configuration

### Storage Config (in `lib.rs`)

```rust
{
  "storage_path": "/path/to/storage",
  "optimizers": {
    "deleted_threshold": 0.2,
    "vacuum_min_vector_number": 1000,
    "default_segment_number": 2,
    "max_segment_size_kb": 200000,
    "indexing_threshold_kb": 20000,
    "flush_interval_sec": 5,
    "max_optimization_threads": 1
  },
  "hnsw_index": {
    "m": 16,                    // Connections per node
    "ef_construct": 100,        // Construction-time candidates
    "max_indexing_threads": 0,  // Auto-select
    "on_disk": false            // Keep in RAM
  }
}
```

### Performance Tuning

| Parameter | Low Memory | Balanced | High Accuracy |
|-----------|-----------|----------|---------------|
| `m` | 8 | 16 | 32 |
| `ef_construct` | 50 | 100 | 200 |
| `max_segment_size_kb` | 50000 | 200000 | 500000 |

## 📊 Performance Benchmarks

Test Device: Android Emulator (ARM64, Android 16)

| Operation | Dataset | Time | Throughput |
|-----------|---------|------|------------|
| Upsert | 5,000 vectors (384-dim) | 230ms | 21,739 vectors/sec |
| Search | 100 queries (top-10) | 44ms | 0.44ms/query |
| Recall | Brute-force validation | 547ms | 5.47ms/query |

**Recall Accuracy**: 1.0 (100% - perfect match with brute-force)

## 🔧 Troubleshooting

### Build Errors

**Problem**: `cargo ndk` not found
```bash
# Solution:
cargo install cargo-ndk
```

**Problem**: Android target not found
```bash
# Solution:
rustup target add aarch64-linux-android
```

**Problem**: JNI errors during app startup
```bash
# Check native library is copied:
ls qdrant-android-sdk/src/main/jniLibs/arm64-v8a/
# Should see: libqdrant_offline_android.so
```

### Runtime Errors

**Problem**: "STATE_ERROR: TOC not initialized"
- Ensure `init()` is called before any other operations
- Check storage path is writable
- Review logcat for detailed init errors

**Problem**: "INIT_ERROR: config parse ..."
- Config JSON is malformed
- Check `lib.rs` for correct config structure

**Problem**: App crashes on init
- Ensure you're not calling `init()` from UI thread
- Use background thread: `new Thread(() -> { ... }).start()`

## 📂 Project Structure

```
qdrant_integration/QdrantAndroidSDK/
├── qdrant_offline_android/           # Rust JNI layer (offline core)
│   ├── src/
│   │   └── lib.rs                    # Native Qdrant wrapper
│   ├── Cargo.toml                    # Rust dependencies
│   └── target/                       # Build output (generated)
├── qdrant-android-sdk/               # Android library module
│   └── src/main/
│       ├── java/com/qdrant/client/
│       │   └── OfflineQdrant.java    # JNI native method declarations
│       └── jniLibs/arm64-v8a/        # Compiled native library (.so)
├── app/                              # Demo/test application
│   └── src/main/java/com/example/qdrant/
│       └── MainActivity.java         # UI, file picker, recall testing
├── generate_small_data.py            # Generate 5K test vectors
├── generate_data.py                  # Generate 50K test vectors
├── upsert_5k.json                    # Sample vectors (42 MB)
├── search_100.json                   # Sample queries (841 KB)
├── README.md                         # This file
└── GITHUB_UPLOAD_GUIDE.md            # GitHub setup instructions
```

**Note**: This repository contains **ONLY offline functionality**. All vector operations run locally on-device with no internet connection required.

## 🤝 Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Test thoroughly on physical device
4. Submit pull request with detailed description

## 📄 License

MIT License - Copyright (c) 2025 Abhishek Thakur

See LICENSE file for full details.

## 🙏 Acknowledgments

- Built on [Qdrant](https://qdrant.tech/) vector search engine
- HNSW implementation based on research by Malkov & Yashunin
- Android JNI integration with Rust

## 💡 Use Cases

- **Offline RAG** (Retrieval-Augmented Generation)
- **Semantic search** in document collections
- **Image similarity** search
- **Recommendation systems**
- **Content deduplication**
- **Clustering and classification**

## 🔗 Resources

- [Qdrant Documentation](https://qdrant.tech/documentation/)
- [HNSW Paper](https://arxiv.org/abs/1603.09320)
- [Rust JNI Book](https://docs.rs/jni/)
- [Android NDK Guide](https://developer.android.com/ndk/guides)

---

**Made with ❤️ for offline vector search on Android**
