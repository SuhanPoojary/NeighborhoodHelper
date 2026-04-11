const { initFirebaseAdmin } = require("./firebaseAdmin");

async function verifyFirebaseIdToken(req, res, next) {
  try {
    const header = req.headers.authorization || "";
    const match = header.match(/^Bearer (.+)$/);
    if (!match) {
      return res.status(401).json({ error: "Missing Authorization: Bearer <idToken>" });
    }

    const idToken = match[1];
    const admin = initFirebaseAdmin();
    const decoded = await admin.auth().verifyIdToken(idToken);

    req.user = {
      uid: decoded.uid,
      claims: decoded,
    };

    return next();
  } catch (e) {
    return res.status(401).json({ error: "Invalid token", details: e.message });
  }
}

module.exports = { verifyFirebaseIdToken };

