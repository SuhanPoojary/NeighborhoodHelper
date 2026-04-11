const { initFirebaseAdmin } = require("./firebaseAdmin");
const { getUserTokens, deleteUserToken } = require("./tokenStore");

function buildMessage({ tokens, title, body, data }) {
  // Use notification+data so:
  // - Background/terminated: system shows notification automatically
  // - Foreground: onMessageReceived is called; you can decide to show
  return {
    tokens,
    notification: {
      title,
      body,
    },
    data: data || {},
    android: {
      priority: "high",
      notification: {
        channelId: "complaint_updates_v2",
      },
    },
  };
}

async function sendPushToUser(uid, { title, body, data }) {
  const admin = initFirebaseAdmin();

  const tokens = await getUserTokens(uid);
  if (tokens.length === 0) {
    return {
      tokens: 0,
      success: 0,
      failure: 0,
      cleaned: 0,
      details: [],
    };
  }

  const message = buildMessage({ tokens, title, body, data });
  const res = await admin.messaging().sendEachForMulticast(message);

  const invalidTokens = [];
  res.responses.forEach((r, idx) => {
    if (r.success) return;

    const code = r.error && r.error.code;
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      invalidTokens.push(tokens[idx]);
    }
  });

  await Promise.all(invalidTokens.map((t) => deleteUserToken(uid, t)));

  return {
    tokens: tokens.length,
    success: res.successCount,
    failure: res.failureCount,
    cleaned: invalidTokens.length,
    details: res.responses.map((r) => ({
      success: r.success,
      error: r.success ? null : { code: r.error.code, message: r.error.message },
    })),
  };
}

module.exports = {
  sendPushToUser,
};

