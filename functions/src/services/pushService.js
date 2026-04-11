const { getAdmin } = require('../firebaseAdmin');

const INVALID_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

async function getUserTokens(userId) {
  if (!userId) return [];
  const admin = getAdmin();
  const snap = await admin.firestore().collection('fcmTokens').doc(userId).collection('tokens').get();
  return snap.docs
    .map((d) => (d.data()?.token ? String(d.data().token) : d.id))
    .filter((t) => typeof t === 'string' && t.length > 0);
}

async function deleteToken(userId, token) {
  const admin = getAdmin();
  await admin.firestore().collection('fcmTokens').doc(userId).collection('tokens').doc(token).delete();
}

/**
 * sendToUser(userId, title, body, data?)
 * - Fetches tokens from Firestore: /fcmTokens/{userId}/tokens/{token}
 * - Uses sendEachForMulticast
 * - Cleans up invalid tokens
 */
async function sendToUser(userId, title, body, data = {}) {
  const admin = getAdmin();

  const tokens = await getUserTokens(userId);
  console.log('[PUSH] tokens found', { userId, tokens: tokens.length });

  if (tokens.length === 0) {
    return { tokens: 0, successCount: 0, failureCount: 0, skipped: true };
  }

  // Put title/body also into data for data-only handlers
  const dataPayload = {
    ...Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
    title: String(title),
    body: String(body),
  };

  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: String(title),
      body: String(body),
    },
    data: dataPayload,
    android: {
      priority: 'high',
      notification: {
        channelId: process.env.ANDROID_CHANNEL_ID || 'complaint_updates_v2',
      },
    },
  });

  console.log('[PUSH] multicast result', {
    userId,
    successCount: res.successCount,
    failureCount: res.failureCount,
  });

  const invalidTokens = [];
  res.responses.forEach((r, idx) => {
    if (r.success) return;
    const code = r.error?.code;
    console.warn('[PUSH] send failed', { userId, idx, token: tokens[idx]?.slice(0, 12), code, message: r.error?.message });
    if (code && INVALID_TOKEN_CODES.has(code)) invalidTokens.push(tokens[idx]);
  });

  if (invalidTokens.length) {
    console.log('[PUSH] cleaning invalid tokens', { userId, invalidTokens: invalidTokens.length });
    await Promise.allSettled(invalidTokens.map((t) => deleteToken(userId, t)));
  }

  return {
    tokens: tokens.length,
    successCount: res.successCount,
    failureCount: res.failureCount,
    invalidCleaned: invalidTokens.length,
  };
}

module.exports = { sendToUser, getUserTokens };

