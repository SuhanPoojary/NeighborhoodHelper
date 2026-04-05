# SmartNeighborhoodHelper Cloud Functions

This folder adds Firebase Cloud Functions that send **FCM v1** push notifications when:
- a new complaint is created (push to community admin)
- a complaint is updated (status/provider changes → push to the reporter)

## Firestore schema used
- `userFcmTokens/{uid}/tokens/{token}` documents (created by the Android app)
- `complaints/{complaintId}`
- `communities/{communityId}.adminUid`

## Deploy
1. Install Firebase CLI and login.
2. From repo root, run in this folder:
   - `npm install`
   - `npm run build`
   - `firebase deploy --only functions`

> Note: Rules don’t affect Admin SDK inside functions.

