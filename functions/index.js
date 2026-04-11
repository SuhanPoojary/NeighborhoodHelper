const express = require("express");
const admin = require("firebase-admin");

const app = express();
app.use(express.json());

// 🔥 Firebase init
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

// 🔔 SEND FUNCTION
async function sendToUser(userId, title, body) {
  const snapshot = await db
    .collection("fcmTokens")
    .doc(userId)
    .collection("tokens")
    .get();

  const tokens = snapshot.docs.map(doc => doc.data().token);

  console.log("Tokens:", tokens.length);

  if (tokens.length === 0) return;

  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
  });

  console.log("Result:", res.successCount);
}

// 📩 APIs

app.post("/events/complaint-created", async (req, res) => {
  const { adminId } = req.body;
  await sendToUser(adminId, "New Complaint", "Complaint created");
  res.json({ ok: true });
});

app.post("/events/complaint-updated", async (req, res) => {
  const { residentId } = req.body;
  await sendToUser(residentId, "Complaint Update", "Status updated");
  res.json({ ok: true });
});

app.post("/events/complaint-reopened", async (req, res) => {
  const { adminId } = req.body;
  await sendToUser(adminId, "Complaint Reopened", "Resident reopened complaint");
  res.json({ ok: true });
});

// health check
app.get("/health", (req, res) => {
  res.send("OK");
});

// 🔥 IMPORTANT for Render
const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});