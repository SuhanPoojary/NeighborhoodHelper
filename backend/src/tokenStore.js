const { initFirebaseAdmin } = require("./firebaseAdmin");

async function getUserTokens(uid) {
  if (!uid) return [];
  const admin = initFirebaseAdmin();

  const snap = await admin
    .firestore()
    .collection("fcmTokens")
    .doc(uid)
    .collection("tokens")
    .get();

  return snap.docs
    .map((d) => {
      const data = d.data() || {};
      return data.token || d.id;
    })
    .filter((t) => typeof t === "string" && t.length > 0);
}

async function deleteUserToken(uid, token) {
  if (!uid || !token) return;
  const admin = initFirebaseAdmin();

  await admin.firestore().collection("fcmTokens").doc(uid).collection("tokens").doc(token).delete();
}

module.exports = {
  getUserTokens,
  deleteUserToken,
};

