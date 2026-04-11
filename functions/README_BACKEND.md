# External Node.js Backend (Render) — FCM Event API

This backend is **separate** from Firebase Cloud Functions.
It runs as a normal Node/Express service and sends pushes with **Firebase Admin SDK**.

## Folder structure

- `server.js` – Express entry
- `src/firebaseAdmin.js` – Admin init (service account)
- `src/services/pushService.js` – `sendToUser()` using Firestore tokens + `sendEachForMulticast`
- `src/routes/events.js` – `/events/*` endpoints

## Environment variables

**Required (choose ONE):**

- `FIREBASE_SERVICE_ACCOUNT_JSON` – full service account JSON as a string (recommended on Render)
- OR `GOOGLE_APPLICATION_CREDENTIALS` – path to your service-account JSON file

> Important: this backend intentionally does **not** fall back to metadata server credentials.
> That fallback causes: `getaddrinfo ENOTFOUND metadata.google.internal` on Render/Windows.

Optional:

- `PORT` – default `8080`
- `ANDROID_CHANNEL_ID` – must match app channel (default `complaint_updates_v2`)
- `EVENTS_SECRET` – optional shared secret. If set, Android must send header `x-events-secret`.

## Run locally (PowerShell)

```powershell
cd functions
npm install
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\serviceAccount.json"
$env:ANDROID_CHANNEL_ID="complaint_updates_v2"
node server.js
```

Health check:

- `GET http://localhost:8080/health`

## API

All endpoints: `POST /events/*` JSON body.

- `/events/complaint-created`
- `/events/complaint-updated`
- `/events/complaint-reopened`
- `/events/join-request`

Each returns `{ ok: true, result: { successCount, failureCount, ... } }`.
