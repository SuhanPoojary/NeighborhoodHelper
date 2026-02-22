## Plan: Smart Neighborhood Helper — Revised for Syllabus Requirements

### What Changed From the Original Plan

| Area | Original Plan | Revised Plan (this document) |
|---|---|---|
| UI | Jetpack Compose (declarative) | **XML Layouts** (LinearLayout, ConstraintLayout, RelativeLayout) |
| Navigation | Navigation-Compose | **Activities + Intents + Fragments** |
| Architecture host | Single `ComponentActivity` | **Multiple Activities**, `MainActivity` hosts Fragments |
| Local storage | None (Firebase only) | **SharedPreferences + Room Database** (SQLite) |
| Sync strategy | Firebase-only | **Room = offline cache, Firebase = cloud sync** |
| Services | None | **Started Service** for background status polling |
| Notifications | None | **NotificationManager** local notifications |
| Testing | None planned | **JUnit unit tests + Espresso UI tests** |
| Version control | Not mentioned | **Git/GitHub** from day one |
| Publishing | Not mentioned | **Signed APK + Play Store docs** |

> **Why these changes matter:** Your syllabus explicitly requires Activities, Fragments, Intents, XML layouts, SQLite/SharedPreferences, Services, and Notifications. The original Compose-based plan would not satisfy those grading criteria. This revised plan checks every syllabus box while still using Firebase as the cloud backend.

---

### Tech Stack (Revised)

| Layer | Technology | Why |
|---|---|---|
| Language | Kotlin | Modern Android language, required |
| UI | XML Layouts | Syllabus requirement |
| Navigation | Activities + Intents + Fragments | Syllabus requirement — demonstrates all three |
| Architecture | MVVM (ViewModel + LiveData + Repository) | Clean separation, industry standard |
| Local DB | Room (wraps SQLite) | Syllabus says "SQLite" — Room is the official wrapper |
| Preferences | SharedPreferences | Syllabus requirement — stores user session |
| Cloud Backend | Firebase (Auth + Firestore + Storage) | Real-time sync, image uploads |
| Image Loading | Glide | Mature, XML-friendly (Coil is Compose-oriented) |
| Background Work | Started Service + BroadcastReceiver | Syllabus requirement |
| Notifications | NotificationManager + NotificationChannel | Syllabus requirement |
| Testing | JUnit 4 + Espresso | Syllabus requirement |
| Version Control | Git + GitHub | Syllabus requirement |
| Async | Kotlin Coroutines + LiveData | No RxJava complexity |

---

### Phase 0 — Project Configuration

> **Goal:** Add Kotlin plugin, Firebase, Room, and all dependencies to the existing project. No code changes yet—just build files.

#### Step 0.1 — Firebase Console (Manual, Do First)

1. Go to [console.firebase.google.com](https://console.firebase.google.com).
2. Create a new project named **SmartNeighborhoodHelper**.
3. Add an **Android app** with package name `com.example.smartneighborhoodhelper`.
4. Download `google-services.json` → place it in `app/` folder.
5. In Firebase Console, enable:
   - **Authentication → Email/Password** sign-in method.
   - **Cloud Firestore** → Start in **test mode** (we'll add rules later).
   - **Storage** → Start in **test mode**.

#### Step 0.2 — `gradle/libs.versions.toml`

Add version entries and library/plugin aliases for:

- `org.jetbrains.kotlin.android` plugin
- `com.google.gms.google-services` plugin
- `com.google.devtools.ksp` plugin (for Room annotation processing)
- Firebase BOM, `firebase-auth-ktx`, `firebase-firestore-ktx`, `firebase-storage-ktx`
- Room (`room-runtime`, `room-ktx`, `room-compiler`)
- Lifecycle (`lifecycle-viewmodel-ktx`, `lifecycle-livedata-ktx`)
- Fragment KTX
- Glide
- Coroutines (`kotlinx-coroutines-android`)
- Google Play Services Location (`play-services-location`)
- Espresso, JUnit (already present)

#### Step 0.3 — Root `build.gradle.kts`

Add `kotlin-android` and `google-services` plugins (both `apply false`).

#### Step 0.4 — `app/build.gradle.kts`

- Apply `kotlin-android`, `google-services`, and `ksp` plugins.
- Add `kotlinOptions { jvmTarget = "11" }`.
- Add `buildFeatures { viewBinding = true }` (type-safe XML access, replaces `findViewById`).
- Add all library dependencies from the version catalog.

#### Step 0.5 — `AndroidManifest.xml`

Add permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Register all Activities and the background Service (details in later phases).

#### Step 0.6 — Git Init

```bash
git init
# create .gitignore (exclude build/, .gradle/, local.properties, google-services.json)
git add .
git commit -m "Initial project setup with dependencies"
```

---

### Phase 1 — Package Structure

> **Goal:** Create the folder skeleton so every file has a clear home.

```
com.example.smartneighborhoodhelper/
│
├── data/
│   ├── model/              ← Data classes (User, Community, Complaint, ServiceProvider)
│   ├── local/
│   │   ├── dao/            ← Room DAOs (ComplaintDao, UserDao)
│   │   ├── database/       ← AppDatabase.kt (Room database class)
│   │   └── prefs/          ← SessionManager.kt (SharedPreferences wrapper)
│   └── remote/
│       └── repository/     ← AuthRepository, CommunityRepository, ComplaintRepository
│
├── ui/
│   ├── auth/               ← LoginActivity, SignupActivity + XML layouts
│   ├── main/               ← MainActivity (hosts Fragments via FrameLayout)
│   ├── fragments/
│   │   ├── admin/          ← AdminDashboardFragment, ManageProvidersFragment
│   │   └── resident/       ← ResidentDashboardFragment, MyComplaintsFragment
│   ├── complaint/          ← ReportComplaintActivity, ComplaintDetailActivity + XML
│   ├── community/          ← CreateCommunityActivity, JoinCommunityActivity + XML
│   └── adapter/            ← RecyclerView adapters (ComplaintAdapter, ProviderAdapter)
│
├── viewmodel/              ← AuthViewModel, ComplaintViewModel, CommunityViewModel
│
├── service/                ← ComplaintStatusService.kt (Started Service)
│
├── receiver/               ← NotificationReceiver.kt (BroadcastReceiver)
│
└── util/                   ← Constants.kt, LocationHelper.kt, NotificationHelper.kt
```

**Why this structure?**
- `data/` = everything about data (models, local DB, remote API) — the "M" in MVVM.
- `ui/` = everything the user sees (Activities, Fragments, Adapters, XML layouts) — the "V".
- `viewmodel/` = glue between data and UI — the "VM".
- `service/` and `receiver/` = Android components required by syllabus.
- `util/` = helper classes shared across the app.

---

### Phase 2 — Data Models & Local Storage

> **Goal:** Define all data classes, set up Room database, and create SharedPreferences wrapper.

#### Step 2.1 — Data Classes (`data/model/`)

```
User(uid, name, email, role, communityId, pincode, createdAt)
Community(id, name, pincode, code, adminUid, createdAt)
Complaint(id, title, description, category, imageUrl, latitude, longitude,
          status, reportedBy, communityId, assignedProvider,
          createdAt, updatedAt, resolvedConfirmedByResident)
ServiceProvider(id, name, phone, category, communityId)
```

- `role` is `"admin"` or `"resident"` (stored as String, not enum, for Firestore compatibility).
- `status` is `"Pending"` | `"In Progress"` | `"Resolved"`.
- Room `@Entity` annotations on `Complaint` and `User` (the two things we cache locally).

#### Step 2.2 — Room Database (`data/local/`)

- `ComplaintDao` — `@Insert`, `@Update`, `@Delete`, `@Query("SELECT * FROM complaints WHERE communityId = :id")`.
- `UserDao` — cache current user profile locally.
- `AppDatabase` — `@Database(entities = [Complaint::class, User::class], version = 1)`.
- **Why Room?** It wraps SQLite (satisfies syllabus) but gives you compile-time query verification and LiveData/Flow return types.

#### Step 2.3 — SharedPreferences (`data/local/prefs/`)

`SessionManager.kt` — wrapper class with:
- `saveUserSession(uid, role, communityId)` — called after login.
- `getUserRole(): String?` — used to decide which dashboard to show.
- `isLoggedIn(): Boolean` — checks if session exists.
- `clearSession()` — called on logout.
- Uses `context.getSharedPreferences("user_session", MODE_PRIVATE)`.

**Why SharedPreferences?** Perfect for small key-value data like login tokens. Syllabus requires it. Do NOT store large data here — that's what Room is for.

#### Step 2.4 — Firebase Repositories (`data/remote/repository/`)

- `AuthRepository` — wraps `FirebaseAuth` (signup, login, logout, getCurrentUser).
- `CommunityRepository` — wraps Firestore `communities` collection (create, join, findByPincode).
- `ComplaintRepository` — wraps Firestore `complaints` collection + Firebase Storage for images.
  - **Sync strategy:** Write to Room first (offline), then push to Firestore. On read, fetch from Firestore and cache in Room. If offline, show Room data.

---

### Phase 3 — Feature-by-Feature Build Order

> We build **one complete feature at a time** (UI + ViewModel + Repository + Database). Each feature results in a working, testable piece of the app.

---

#### Feature 1 — Authentication (Login + Signup)

**Syllabus concepts demonstrated:** Activities, Intents, SharedPreferences, XML Layouts.

**Files to create:**

| File | Type | Purpose |
|---|---|---|
| `activity_login.xml` | XML Layout | Email, password fields, login button, "Sign Up" link |
| `activity_signup.xml` | XML Layout | Name, email, password, pincode, role radio group, signup button |
| `LoginActivity.kt` | Activity | Handles login form, calls ViewModel, navigates via Intent |
| `SignupActivity.kt` | Activity | Handles signup form, role selection, calls ViewModel |
| `AuthViewModel.kt` | ViewModel | Exposes `login()`, `signup()`, `LiveData<AuthState>` |
| `AuthRepository.kt` | Repository | Calls `FirebaseAuth`, saves session to SharedPreferences |
| `SessionManager.kt` | SharedPrefs | Persists user role, uid, communityId |

**Flow:**
1. App launches → `LoginActivity`.
2. User taps "Sign Up" → `Intent` to `SignupActivity` (demonstrates Intents).
3. Signup: user picks "Admin" or "Resident" via `RadioGroup` → Firebase creates account → Firestore saves user doc → `SessionManager` saves session.
4. Login: Firebase authenticates → `SessionManager` saves session → `Intent` to `MainActivity`.
5. On next app open: `SessionManager.isLoggedIn()` → skip login, go straight to `MainActivity`.

**Git commit:** `feat: add authentication with login/signup activities`

---

#### Feature 2 — Community Creation & Joining

**Syllabus concepts demonstrated:** Activities, Intents with extras, XML Layouts.

**Files to create:**

| File | Type | Purpose |
|---|---|---|
| `activity_create_community.xml` | XML Layout | Community name, pincode fields, "Create" button |
| `activity_join_community.xml` | XML Layout | 6-digit code field, pincode auto-discover list, "Join" button |
| `CreateCommunityActivity.kt` | Activity | Admin creates community, generates 6-digit code |
| `JoinCommunityActivity.kt` | Activity | Resident enters code or picks from pincode matches |
| `CommunityViewModel.kt` | ViewModel | `createCommunity()`, `joinByCode()`, `findByPincode()` |
| `CommunityRepository.kt` | Repository | Firestore CRUD on `communities` collection |

**Flow:**
1. After signup/login, if user has no `communityId`:
   - Admin → `CreateCommunityActivity` (via Intent).
   - Resident → `JoinCommunityActivity` (via Intent).
2. Admin creates community → Firestore generates doc → 6-digit code displayed → `SessionManager` updates `communityId`.
3. Resident enters code → Firestore query → joins community → `SessionManager` updates.
4. After community is set → `Intent` to `MainActivity`.

**Git commit:** `feat: add community creation and joining`

---

#### Feature 3 — Main Dashboard with Fragments

**Syllabus concepts demonstrated:** Fragments, Fragment transactions, BottomNavigationView, dynamic fragment management.

**Files to create:**

| File | Type | Purpose |
|---|---|---|
| `activity_main.xml` | XML Layout | `BottomNavigationView` + `FrameLayout` container for fragments |
| `MainActivity.kt` | Activity | Hosts fragments, swaps them based on bottom nav selection |
| `fragment_admin_dashboard.xml` | XML Layout | RecyclerView of all complaints + filter tabs |
| `fragment_resident_dashboard.xml` | XML Layout | RecyclerView of user's complaints + FAB |
| `fragment_profile.xml` | XML Layout | User info, community code display, logout button |
| `AdminDashboardFragment.kt` | Fragment | Shows all community complaints, filter by status |
| `ResidentDashboardFragment.kt` | Fragment | Shows "my complaints", FAB to report new |
| `ProfileFragment.kt` | Fragment | Shows user profile, community info, logout |
| `ComplaintAdapter.kt` | Adapter | RecyclerView adapter for complaint cards |

**Flow:**
1. `MainActivity.onCreate()` → checks `SessionManager.getUserRole()`.
2. If `"admin"` → loads `AdminDashboardFragment` into `FrameLayout`.
3. If `"resident"` → loads `ResidentDashboardFragment` into `FrameLayout`.
4. `BottomNavigationView` items: **Home** (dashboard) | **Profile**.
5. Tapping bottom nav → `supportFragmentManager.beginTransaction().replace(...)`.
6. This demonstrates **dynamic fragment management** as required by your syllabus.

**Git commit:** `feat: add main dashboard with fragments and bottom navigation`

---

#### Feature 4 — Report Complaint (with Photo + GPS)

**Syllabus concepts demonstrated:** Activities, Intents (camera/gallery), Runtime permissions, XML Layouts.

**Files to create:**

| File | Type | Purpose |
|---|---|---|
| `activity_report_complaint.xml` | XML Layout | Title, description, category spinner, image preview, "Attach Photo" button, "Submit" button |
| `ReportComplaintActivity.kt` | Activity | Form handling, camera/gallery intent, location fetch |
| `ComplaintViewModel.kt` | ViewModel | `reportComplaint()`, image upload, location |
| `ComplaintRepository.kt` | Repository | Saves to Room (offline) + Firestore (cloud) + Storage (image) |
| `LocationHelper.kt` | Util | Wraps `FusedLocationProviderClient` |

**Flow:**
1. Resident taps FAB on dashboard → `Intent` to `ReportComplaintActivity`.
2. User fills title, description, picks category from `Spinner`.
3. "Attach Photo" → `Intent` to camera or gallery (ACTION_IMAGE_CAPTURE / ACTION_PICK) — demonstrates implicit Intents.
4. Photo preview shown via `Glide` in an `ImageView`.
5. On submit: get GPS coordinates → upload image to Firebase Storage → save complaint to Room + Firestore → navigate back.
6. **Offline support:** If no internet, complaint saves to Room with `status = "pending_sync"`. Syncs when connectivity returns.

**Git commit:** `feat: add complaint reporting with photo and GPS`

---

#### Feature 5 — Complaint Detail & Status Management

**Syllabus concepts demonstrated:** Intents with Parcelable extras, XML Layouts, LiveData observation.

**Files to create:**

| File | Type | Purpose |
|---|---|---|
| `activity_complaint_detail.xml` | XML Layout | Full complaint info, image, map snippet, status chip, action buttons |
| `ComplaintDetailActivity.kt` | Activity | Displays complaint, role-based action buttons |
| `ServiceProvider` model | Data class | Already defined in Phase 2 |
| `ManageProvidersFragment.kt` | Fragment | Admin: add/edit/delete service providers |
| `fragment_manage_providers.xml` | XML Layout | RecyclerView + FAB for provider list |
| `ProviderAdapter.kt` | Adapter | RecyclerView adapter for providers |

**Flow:**
1. Tap complaint in dashboard RecyclerView → `Intent` with complaint ID as extra → `ComplaintDetailActivity`.
2. Activity fetches complaint from ViewModel (LiveData observes Firestore snapshot).
3. **Admin sees:** "Assign Provider" dropdown + "Mark In Progress" / "Mark Resolved" buttons.
4. **Resident sees:** Current status + "Confirm Resolution" button (only when status is "Resolved").
5. Status updates propagate in real-time via Firestore snapshot listeners → LiveData → UI auto-updates.

**Git commit:** `feat: add complaint detail and status management`

---

#### Feature 6 — Background Service & Notifications

**Syllabus concepts demonstrated:** Started Service, NotificationManager, NotificationChannel, BroadcastReceiver.

**Files to create:**

| File | Type | Purpose |
|---|---|---|
| `ComplaintStatusService.kt` | Started Service | Periodically polls Firestore for status changes |
| `NotificationHelper.kt` | Util | Creates notification channel, builds notifications |
| `NotificationReceiver.kt` | BroadcastReceiver | Receives broadcast from service, shows notification |

**Flow:**
1. When user logs in → `startService(Intent(this, ComplaintStatusService::class.java))`.
2. Service runs a coroutine that polls Firestore every 60 seconds for complaints where `status` changed.
3. If a complaint's status changed since last check → build a notification:
   - Title: "Complaint Updated"
   - Body: "Your complaint '{title}' is now {status}"
4. Tapping notification → `Intent` to `ComplaintDetailActivity` with complaint ID.
5. **NotificationChannel** created in `NotificationHelper` (required for Android 8+).
6. Service is a **Started Service** (`START_STICKY`) — demonstrates the syllabus requirement.

**Git commit:** `feat: add background service and notifications`

---

#### Feature 7 — Offline Sync (Room ↔ Firebase)

**Syllabus concepts demonstrated:** SQLite (via Room), data persistence, network-aware behavior.

**Implementation:**
1. Every complaint write goes to **Room first** (instant, works offline).
2. A sync method checks `NetworkCapabilities` → if online, pushes pending Room records to Firestore.
3. On app start, fetch latest from Firestore → upsert into Room.
4. UI always reads from Room (via LiveData) → single source of truth.
5. This demonstrates **offline-first architecture** and satisfies the SQLite requirement.

**Git commit:** `feat: add offline sync between Room and Firebase`

---

### Phase 4 — Testing

> **Goal:** Write unit tests and UI tests to satisfy syllabus requirements.

#### Step 4.1 — Unit Tests (`src/test/`)

| Test Class | What It Tests |
|---|---|
| `SessionManagerTest` | SharedPreferences read/write/clear |
| `ComplaintViewModelTest` | ViewModel state changes, LiveData emissions |
| `CommunityCodeGeneratorTest` | 6-digit code generation uniqueness |
| `ComplaintStatusTest` | Status transition logic (Pending → In Progress → Resolved) |

#### Step 4.2 — UI Tests (`src/androidTest/`)

| Test Class | What It Tests |
|---|---|
| `LoginActivityTest` | Espresso: type email/password, click login, verify navigation |
| `ReportComplaintActivityTest` | Espresso: fill form, verify submit button enabled |
| `DashboardFragmentTest` | Espresso: verify RecyclerView loads items, verify filter tabs |

**Git commit:** `test: add unit tests and Espresso UI tests`

---

### Phase 5 — Polish & Submission

#### Step 5.1 — UI Polish
- Consistent Material Design theme (`themes.xml`).
- Proper error handling (empty fields, network errors, snackbars).
- Loading spinners (`ProgressBar`) during async operations.
- Empty state illustrations when no complaints exist.

#### Step 5.2 — Performance Profiling
- Run **Android Profiler** in Android Studio.
- Screenshot CPU, Memory, and Network tabs.
- Document any optimizations made (e.g., RecyclerView view holder pattern, image compression).

#### Step 5.3 — Version Control
- Push to GitHub with meaningful commit messages (one per feature as noted above).
- Write a `README.md` with project description, screenshots, setup instructions.

#### Step 5.4 — Publishing Documentation
- Generate a **signed APK** (Build → Generate Signed Bundle/APK).
- Document the steps for Play Store publishing (even if not actually published).
- Include in project report.

**Git commit:** `docs: add README, profiling report, and publishing docs`

---

### Activities & Fragments Summary

This table shows which syllabus concept each screen demonstrates:

| Screen | Component Type | Syllabus Concept |
|---|---|---|
| `LoginActivity` | Activity | Activities, XML Layouts |
| `SignupActivity` | Activity | Activities, Intents (from Login) |
| `CreateCommunityActivity` | Activity | Activities, Intents with extras |
| `JoinCommunityActivity` | Activity | Activities, Intents with extras |
| `MainActivity` | Activity + Fragments | Fragment management, BottomNavigationView |
| `AdminDashboardFragment` | Fragment | Dynamic Fragments, RecyclerView |
| `ResidentDashboardFragment` | Fragment | Dynamic Fragments, RecyclerView, FAB |
| `ProfileFragment` | Fragment | Dynamic Fragments, SharedPreferences |
| `ReportComplaintActivity` | Activity | Activities, Implicit Intents (camera), Permissions |
| `ComplaintDetailActivity` | Activity | Activities, Parcelable Intents |
| `ManageProvidersFragment` | Fragment | Dynamic Fragments, CRUD |
| `ComplaintStatusService` | Service | Started Service |
| `NotificationReceiver` | BroadcastReceiver | Notifications |

**Total: 7 Activities + 5 Fragments + 1 Service + 1 Receiver** — more than enough to demonstrate all syllabus requirements.

---

### Recommended Build Order (Week-by-Week)

| Week | What to Build | Key Concepts Learned |
|---|---|---|
| 1 | Phase 0 (setup) + Phase 1 (folders) + Phase 2 (models, Room, SharedPrefs) | Gradle, Room, SharedPreferences |
| 2 | Feature 1 (Auth: Login + Signup) | Activities, Intents, Firebase Auth, MVVM |
| 3 | Feature 2 (Communities) + Feature 3 (Dashboard with Fragments) | Fragments, RecyclerView, BottomNav |
| 4 | Feature 4 (Report Complaint with photo + GPS) | Camera Intent, Permissions, Firebase Storage |
| 5 | Feature 5 (Complaint Detail + Provider Management) | Parcelable, LiveData, Firestore listeners |
| 6 | Feature 6 (Service + Notifications) + Feature 7 (Offline Sync) | Services, Notifications, Room sync |
| 7 | Phase 4 (Testing) + Phase 5 (Polish + Submission) | JUnit, Espresso, Profiler, Git |

---

### Answers to Your Specific Questions

**Q1: Can you revise the plan to use XML + Activities + Fragments instead of Compose?**
Yes — this entire revised plan uses XML layouts, multiple Activities, and Fragments. No Compose anywhere. Your existing project template is already set up correctly for this approach.

**Q2: Should we use Room Database for offline storage + Firebase for sync?**
Yes — Room is the local cache (satisfies SQLite syllabus requirement), Firebase is the cloud backend. Write to Room first, sync to Firebase. UI reads from Room via LiveData. This gives you offline support AND real-time cloud sync.

**Q3: How do we structure the app to demonstrate both Activities AND Fragments?**
Separate Activities for distinct workflows (Login, Signup, Report Complaint, Complaint Detail). Fragments inside `MainActivity` for dashboard tabs (Admin/Resident dashboard, Profile). This is the standard Android pattern and clearly demonstrates both concepts.

**Q4: Can we start simpler and add complexity gradually?**
Yes — the feature-by-feature build order above goes from simple (login form) to complex (background services). Each feature builds on the previous one. You'll always have a runnable app at every stage.
