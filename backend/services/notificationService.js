const { admin, db } = require('../firebaseAdmin');

async function getTokensForUser(userId) {
  const uid = String(userId || '').trim();
  if (!uid) return [];

  const snap = await db.collection('fcmTokens').doc(uid).collection('tokens').get();

  // Token doc id == token, but we also support storing {token: "..."}
  const tokens = snap.docs
    .map((d) => {
      const data = d.data() || {};
      return data.token || d.id;
    })
    .filter((t) => typeof t === 'string' && t.length > 0);

  console.log(`[FCM] tokens found userId=${uid} count=${tokens.length}`);
  return tokens;
}

async function removeInvalidTokens(userId, invalidTokens) {
  const uid = String(userId || '').trim();
  if (!uid || !invalidTokens || invalidTokens.length === 0) return;

  const batch = db.batch();
  invalidTokens.forEach((token) => {
    const ref = db.collection('fcmTokens').doc(uid).collection('tokens').doc(token);
    batch.delete(ref);
  });
  await batch.commit();
  console.log(`[FCM] cleaned invalid tokens userId=${uid} count=${invalidTokens.length}`);
}

/**
 * Send a push notification to all tokens of a single user.
 *
 * @param {string} userId
 * @param {string} title
 * @param {string} body
 * @param {Record<string,string>} data
 */
async function sendToUser(userId, title, body, data = {}) {
  const uid = String(userId || '').trim();
  if (!uid) {
    throw new Error('sendToUser: userId is required');
  }

  const tokens = await getTokensForUser(uid);
  if (tokens.length === 0) {
    return { tokensFound: 0, successCount: 0, failureCount: 0, cleaned: 0 };
  }

  const stringData = Object.fromEntries(
    Object.entries(data || {}).map(([k, v]) => [String(k), v == null ? '' : String(v)])
  );

  // NOTE: Use BOTH notification + data.
  // - Background/terminated: system displays notification.
  // - Foreground: onMessageReceived is called; app can choose to show its own heads-up.
  const message = {
    tokens,
    notification: { title: String(title), body: String(body) },
    data: {
      ...stringData,
      // convenience fields for foreground handling
      title: String(title),
      body: String(body),
    },
    android: {
      priority: 'high',
      notification: {
        // MUST match Android channel
        channelId: 'complaint_updates_v2',
        sound: 'default',
      },
    },
  };

  console.log(`[FCM] sending multicast userId=${uid} tokens=${tokens.length}`);

  const response = await admin.messaging().sendEachForMulticast(message);

  console.log(
    `[FCM] result userId=${uid} successCount=${response.successCount} failureCount=${response.failureCount}`
  );

  const invalidTokens = [];
  response.responses.forEach((r, idx) => {
    if (r.success) return;

    const code = r.error && r.error.code;
    const token = tokens[idx];
    console.error(`[FCM] token failure userId=${uid} idx=${idx} code=${code} token=${token}`);

    if (
      code === 'messaging/invalid-registration-token' ||
      code === 'messaging/registration-token-not-registered'
    ) {
      invalidTokens.push(token);
    }
  });

  await removeInvalidTokens(uid, invalidTokens);

  return {
    tokensFound: tokens.length,
    successCount: response.successCount,
    failureCount: response.failureCount,
    cleaned: invalidTokens.length,
  };
}

module.exports = { sendToUser };
