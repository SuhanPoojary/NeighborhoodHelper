require("dotenv").config();

const express = require("express");
const cors = require("cors");
const morgan = require("morgan");

const { initFirebaseAdmin } = require("./firebaseAdmin");
const { verifyFirebaseIdToken } = require("./auth");
const { sendPushToUser } = require("./pushService");

initFirebaseAdmin();

const app = express();
app.use(cors());
app.use(express.json({ limit: "256kb" }));

// Include request body size, status, time.
app.use(morgan("tiny"));

app.get("/health", (req, res) => {
  res.json({ ok: true, ts: Date.now() });
});

// Generic endpoint for your Android app to trigger push.
// Later you can split into individual endpoints per event.
app.post("/events/notify", verifyFirebaseIdToken, async (req, res) => {
  const { toUserId, title, body, data } = req.body || {};

  if (!toUserId || !title || !body) {
    return res.status(400).json({ error: "toUserId, title, body are required" });
  }

  try {
    const result = await sendPushToUser(toUserId, {
      title: String(title),
      body: String(body),
      data: Object.fromEntries(
        Object.entries(data || {}).map(([k, v]) => [String(k), v == null ? "" : String(v)])
      ),
    });

    return res.json({ ok: true, result });
  } catch (e) {
    return res.status(500).json({ ok: false, error: e.message });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
  // eslint-disable-next-line no-console
  console.log(`Backend listening on :${port}`);
});

