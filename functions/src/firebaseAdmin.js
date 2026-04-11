const admin = require('firebase-admin');

let initialized = false;

function initFirebaseAdmin() {
  if (initialized) return admin;

  // Option A: FIREBASE_SERVICE_ACCOUNT_JSON contains the full JSON (safe when set as env var)
  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON && !process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    try {
      const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        projectId: serviceAccount.project_id,
      });
      initialized = true;
      console.log('[FIREBASE] initialized via FIREBASE_SERVICE_ACCOUNT_JSON', { projectId: serviceAccount.project_id });
      return admin;
    } catch (e) {
      console.error('[FIREBASE] invalid FIREBASE_SERVICE_ACCOUNT_JSON', e);
      throw e;
    }
  }

  // Option B: GOOGLE_APPLICATION_CREDENTIALS points to the service account file.
  // IMPORTANT: On Render/Windows we must NOT rely on metadata server ADC.
  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    throw new Error(
      'Missing Firebase credentials. Set GOOGLE_APPLICATION_CREDENTIALS (path) or FIREBASE_SERVICE_ACCOUNT_JSON (raw json).'
    );
  }

  // firebase-admin will pick up GOOGLE_APPLICATION_CREDENTIALS automatically.
  admin.initializeApp();
  initialized = true;
  console.log('[FIREBASE] initialized via GOOGLE_APPLICATION_CREDENTIALS');
  return admin;
}

function getAdmin() {
  if (!initialized) {
    throw new Error('Firebase admin not initialized. Call initFirebaseAdmin() first.');
  }
  return admin;
}

module.exports = { initFirebaseAdmin, getAdmin };
