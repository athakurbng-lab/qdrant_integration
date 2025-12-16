# GitHub Upload Guide

Follow these steps to upload your Qdrant Offline Android SDK to GitHub.

## 🗑️ Pre-Upload Cleanup

Remove large/temporary files that don't belong in the repository:

```bash
# Navigate to project root
cd /Users/abhishekthakur/Desktop/Projects/Qdrant_integration/QdrantAndroidSDK

# Remove large test data files (users will regenerate them)
rm -f upsert_50k.json
rm -f search_1k.json

# Remove heap dump
rm -f java_pid*.hprof

# Remove Rust build artifacts
rm -rf qdrant_offline_android/target

# Remove Android build outputs
rm -rf app/build
rm -rf qdrant-android-sdk/build
rm -rf build
rm -rf .gradle

# Remove local.properties (contains machine-specific paths)
rm -f local.properties

# The .gitignore will handle these automatically going forward
```

## 📤 Files TO Upload

### Essential Project Files

✅ **Source Code**
- `app/src/`
- `qdrant-android-sdk/src/`
- `qdrant_offline_android/src/`
- `qdrant_offline_android/Cargo.toml`

✅ **Build Configuration**
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`
- `gradle/`
- `app/build.gradle.kts`
- `qdrant-android-sdk/build.gradle.kts`

✅ **Test Data Generators**
-  `generate_small_data.py` ⭐ **Include this**
- `generate_data.py` ⭐ **Include this**

✅ **Small Test Data** (optional but helpful)
- `upsert_5k.json` (42 MB - borderline, include if under GitHub limit)
- `search_100.json` (841 KB - safe to include)

✅ **Documentation**
- `README.md`
- `LICENSE`
- `.gitignore`

### Android Resources
- `app/src/main/res/`
- `app/src/main/AndroidManifest.xml`

## ❌ Files NOT to Upload

❌ **Build Artifacts (auto-generated)**
- `target/`
- `build/`
- `.gradle/`
- `*.apk`
- `*.aab`

❌ **Large Test Files**
- `upsert_50k.json` (422 MB - too large)
- `search_1k.json` (8.4 MB - unnecessary)

❌ **IDE/Local Files**
- `local.properties`
- `.idea/`
- `*.iml`
- `.DS_Store`
- `java_pid*.hprof` (heap dumps)

❌ **User Data**
- Any `qdrant_storage/` directories

## 🔨 Git Commands

### 1. Initialize Repository

```bash
cd /Users/abhishekthakur/Desktop/Projects/Qdrant_integration/QdrantAndroidSDK

# Initialize git
git init

# Add all files (gitignore will automatically exclude unwanted files)
git add .

# Create first commit
git commit -m "Initial commit: Qdrant Offline Android SDK

- Full offline vector search with Rust JNI
- HNSW indexing for fast similarity search  
- 100% recall accuracy on test dataset
- Complete demo app with UI"
```

### 2. Create GitHub Repository

On GitHub:
1. Go to https://github.com/new
2. Repository name: `qdrant-offline-android`
3. Description: "Fully offline vector search database for Android using Qdrant's HNSW engine"
4. Keep it Public ✅
5. **Do NOT initialize** with README (you already have one)
6. Click "Create repository"

### 3. Push to GitHub

```bash
# Add remote (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/qdrant-offline-android.git

# Push to GitHub
git branch -M main
git push -u origin main
```

## 📦 Repository Size Check

Before pushing, verify your repository size:

```bash
# Check total size
du -sh .

# Should be under 100 MB for smooth GitHub experience
# If larger, remove big test data files
```

## 🏷️ Add GitHub Topics

After uploading, add these topics to your repository:

- `android`
- `vector-database`
- `qdrant`
- `offline`
- `hnsw`
- `rust`
- `jni`
- `semantic-search`
- `vector-search`
- `embeddings`

Go to: Repository → About → ⚙️ Settings → Add topics

## 📋 Recommended Repository Settings

### About Section
- Description: "Fully offline vector search database for Android using Qdrant's HNSW engine"
- Website: Leave empty or add your blog
- Include topics (listed above)

### Features
- ✅ Issues (for bug reports)
- ✅ Discussions (for questions)
- ❌ Projects (not needed initially)
- ❌ Wiki (README is sufficient)

## 🔄 Future Updates

When making changes:

```bash
# Check what changed
git status

# Add changes
git add .

# Commit
git commit -m "Description of changes"

# Push
git push
```

## ⚠️ Important Notes

1. **Large Files**: GitHub has a 100 MB limit per file. The 50K test data file (422 MB) is too large - users should generate it locally.

2. **Compiled Binaries**: Don't commit `.so` files to git. Users will build them locally. Add this to `.gitignore`:
   ```
   *.so
   ```

3. **Sensitive Data**: Never commit:
   - API keys
   - Passwords
   - Personal data
   - Local file paths (in `local.properties`)

4. **Build Time**: First-time users will need to:
   - Clone repo
   - Run `cargo ndk` to build native library
   - Run Python script to generate test data
   - Build APK with Gradle

## ✅ Final Checklist

Before pushing to GitHub, verify:

- [ ] Large files removed (>50 MB)
- [ ] Build artifacts removed
- [ ] `local.properties` removed
- [ ] README.md is complete
- [ ] LICENSE file included
- [ ] .gitignore configured
- [ ] Test data generators included
- [ ] Code compiles successfully

## 🎯 Expected Repository Structure

```
qdrant-offline-android/
├── .gitignore
├── LICENSE  
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
├── generate_small_data.py
├── generate_data.py
├── search_100.json (841 KB - optional)
├── app/
│   ├── build.gradle.kts
│   └── src/
├── qdrant-android-sdk/
│   ├── build.gradle.kts
│   └── src/
└── qdrant_offline_android/
    ├── Cargo.toml
    └── src/
        └── lib.rs
```

**Total Size**: ~50-100 MB (without test data, with small samples)

---

After following this guide, your repository will be ready for others to clone, build, and use!
