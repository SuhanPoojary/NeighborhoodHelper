# Firebase Setup Guide — Smart Neighborhood Helper

## Step-by-Step Instructions (with detailed descriptions)

### Step 1: Go to Firebase Console
1. Open your browser and go to **https://console.firebase.google.com**
2. Sign in with your **Google account** (same one you use for Gmail)
3. You'll see the Firebase Console dashboard

---

### Step 2: Create a New Project
1. Click the big **"Create a project"** button (or "Add project")
2. **Project name:** Type `SmartNeighborhoodHelper`
3. Click **Continue**
4. **Google Analytics:** You can disable this (toggle OFF) — we don't need it for this project
5. Click **Create project**
6. Wait 30 seconds for it to set up, then click **Continue**

---

### Step 3: Add an Android App
1. On the project dashboard, you'll see icons for iOS, Android, Web, etc.
2. Click the **Android icon** (the green robot)
3. Fill in the form:
   - **Android package name:** `com.example.smartneighborhoodhelper`
     (This MUST match the `applicationId` in `app/build.gradle.kts` exactly!)
   - **App nickname:** `Smart Neighborhood Helper` (optional, for your reference)
   - **Debug signing certificate:** Leave blank for now (we'll add it later if needed)
4. Click **Register app**

---

### Step 4: Download google-services.json
1. Firebase will show a **Download google-services.json** button
2. Click it — a file named `google-services.json` will download
3. **IMPORTANT:** Move this file to your project's `app/` folder:
   ```
   SmartNeighborhoodHelper/
   ├── app/
   │   ├── google-services.json   ← PUT IT HERE
   │   ├── build.gradle.kts
   │   └── src/
   ```
4. In Android Studio: Right-click the `app` folder → Show in Explorer → paste the file
5. Click **Next** in Firebase Console (skip the "Add Firebase SDK" step — we already did this in build.gradle)
6. Click **Next** again, then **Continue to console**

---

### Step 5: Enable Email/Password Authentication
1. In the left sidebar of Firebase Console, click **Build → Authentication**
2. Click **Get started**
3. You'll see a list of "Sign-in providers"
4. Click **Email/Password** (the first one in the list)
5. Toggle the **Enable** switch to ON
6. Leave "Email link (passwordless sign-in)" DISABLED
7. Click **Save**

> **What this does:** Allows users to create accounts and sign in using email + password.
> This is the simplest auth method and perfect for a college project.

---

### Step 6: Create Cloud Firestore Database
1. In the left sidebar, click **Build → Firestore Database**
2. Click **Create database**
3. Choose **Start in test mode** (this allows read/write without auth rules — fine for development)
4. Click **Next**
5. Select a **Cloud Firestore location** closest to you:
   - If you're in India: choose `asia-south1 (Mumbai)`
   - If you're in US: choose `us-central`
6. Click **Enable**

> **What is Firestore?** A NoSQL cloud database. Data is stored as "documents" inside
> "collections" (like rows inside tables, but more flexible). We'll have collections
> for: `users`, `communities`, `complaints`, `serviceProviders`.

> **⚠️ IMPORTANT:** Test mode rules expire after 30 days. Before submitting your project,
> we'll add proper security rules. For now, test mode lets you develop without restrictions.

---

### Step 7: Enable Firebase Storage

> **⚠️ Storage requires the Blaze (pay-as-you-go) plan.**
> You're on the free Spark plan by default. Storage won't work until you upgrade.
>
> **Don't worry — Blaze is still free for small usage:**
> - Free tier: 5 GB stored, 1 GB/day downloads, 20K/day uploads
> - A college project will NEVER exceed this
> - You need a credit/debit card on file, but you won't be charged
>
> **To upgrade:** Click "Upgrade" at the bottom-left of Firebase Console →
> Select "Blaze" → Add billing info → Done.
>
> **If you can't upgrade now:** Skip this step. Auth and Firestore work on the free
> plan. We'll build image upload later when Storage is available. The app will
> function fully without it — complaints just won't have photos temporarily.

1. After upgrading to Blaze, in the left sidebar, click **Build → Storage**
2. Click **Get started**
3. Choose **Start in test mode**
4. Click **Next**
5. Select the same location you chose for Firestore
6. Click **Done**

> **What is Firebase Storage?** A file storage service (like Google Drive for your app).
> We'll use it to store complaint photos that residents upload.

---

### Step 8: Verify Setup
After completing all steps, your Firebase project should show:
- ✅ Authentication → Email/Password enabled
- ✅ Firestore Database → Created (empty, test mode)
- ✅ Storage → Created (empty, test mode)
- ✅ `google-services.json` file in your `app/` folder

---

## How to Verify in Android Studio

After placing `google-services.json` in `app/`:
1. Click **File → Sync Project with Gradle Files** (or the elephant icon in toolbar)
2. Wait for sync to complete (bottom progress bar)
3. If you see no errors → Firebase is connected!

If you see errors like "File google-services.json is missing":
- Double-check the file is in the `app/` folder (NOT the root project folder)
- Make sure the package name in the JSON file matches `com.example.smartneighborhoodhelper`

---

## Firestore Data Structure (Preview)

This is how our data will look in Firestore once the app is running:

```
users/                          ← Collection
  ├── uid_abc123/               ← Document (one per user)
  │   ├── name: "Suhan"
  │   ├── email: "suhan@example.com"
  │   ├── role: "admin"
  │   ├── communityId: "comm_xyz"
  │   ├── pincode: "400001"
  │   └── createdAt: 1708531200000
  │
  └── uid_def456/
      ├── name: "Raj"
      ├── email: "raj@example.com"
      ├── role: "resident"
      └── ...

communities/                    ← Collection
  └── comm_xyz/                 ← Document (one per community)
      ├── name: "Sunrise Apartments"
      ├── pincode: "400001"
      ├── code: "482917"        ← 6-digit join code
      ├── adminUid: "uid_abc123"
      └── createdAt: 1708531200000

complaints/                     ← Collection
  └── comp_001/                 ← Document (one per complaint)
      ├── title: "Broken streetlight"
      ├── description: "The light near gate 3..."
      ├── category: "Electrical"
      ├── imageUrl: "https://firebasestorage.googleapis.com/..."
      ├── latitude: 19.0760
      ├── longitude: 72.8777
      ├── status: "Pending"
      ├── reportedBy: "uid_def456"
      ├── communityId: "comm_xyz"
      └── ...

serviceProviders/               ← Collection
  └── prov_001/
      ├── name: "Raj Electricals"
      ├── phone: "9876543210"
      ├── category: "Electrical"
      └── communityId: "comm_xyz"
```

