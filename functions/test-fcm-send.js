/*
  Local FCM send tester (no emulator).
  Usage:
    node test-fcm-send.js <fcmToken> [title] [body]

  Notes:
  - Running on your laptop requires explicit credentials.
    Set GOOGLE_APPLICATION_CREDENTIALS to a Firebase service-account JSON path.

    PowerShell example:
      $env:GOOGLE_APPLICATION_CREDENTIALS="C:\\path\\service-account.json"
      Remove-Item Env:FIREBASE_PROJECT_ID -ErrorAction SilentlyContinue  # optional: let it auto-detect
      node .\\test-fcm-send.js "<TOKEN>" "Test" "Hello"
*/

const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

function initAdmin() {
  if (admin.apps.length) return;

  // Preferred for local/Render: env var triad
  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  let privateKey = process.env.FIREBASE_PRIVATE_KEY;

  if (projectId && clientEmail && privateKey) {
    privateKey = privateKey.replace(/\\n/g, "\n");
    admin.initializeApp({
      credential: admin.credential.cert({
        projectId,
        clientEmail,
        privateKey,
      }),
      projectId,
    });
    return;
  }

  // JSON file via GOOGLE_APPLICATION_CREDENTIALS
  const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (credPath && fs.existsSync(credPath)) {
    const absolute = path.isAbsolute(credPath) ? credPath : path.join(process.cwd(), credPath);
    // eslint-disable-next-line import/no-dynamic-require, global-require
    const serviceAccount = require(absolute);

    // ✅ IMPORTANT:
    // Don't force a projectId unless you are sure it's correct. A wrong projectId can lead
    // to confusing DNS errors like: getaddrinfo ENOTFOUND metadata.google.internal
    // (seen when Admin SDK tries alternate credential flows).
    const resolvedProjectId = serviceAccount.project_id;

    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: resolvedProjectId,
    });

    return;
  }

  throw new Error(
    "No Firebase credentials found. Set FIREBASE_PROJECT_ID/FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY or GOOGLE_APPLICATION_CREDENTIALS"
  );
}

initAdmin();

async function main() {
  const token = process.argv[2];
  const title = process.argv[3] || "Test notification";
  const body = process.argv[4] || "Hello from SmartNeighborhoodHelper";

  if (!token) {
    console.error("Missing token. Usage: node test-fcm-send.js <fcmToken> [title] [body]");
    process.exit(1);
  }

  const message = {
    token,
    notification: {
      title,
      body,
    },
    data: {
      type: "manual_test",
      // For Android foreground handler convenience
      title,
      body,
    },
    android: {
      priority: "high",
      notification: {
        channelId: "complaint_updates_v2",
      },
    },
  };

  const res = await admin.messaging().send(message);
  console.log("Sent. messageId:", res);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
