require("dotenv").config();

const express = require("express");
const cors = require("cors");
const morgan = require("morgan");

const { initFirebaseAdmin } = require("./firebaseAdmin");
const eventsRouter = require("./routes/events");

initFirebaseAdmin();

const app = express();
app.use(cors());
app.use(express.json({ limit: "256kb" }));
app.use(morgan("tiny"));

app.get("/health", (req, res) => res.json({ ok: true, ts: Date.now() }));

app.use("/events", eventsRouter);

// Basic error handler
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  // eslint-disable-next-line no-console
  console.error("[API] Unhandled error:", err);
  res.status(500).json({ ok: false, error: "Internal server error" });
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
  // eslint-disable-next-line no-console
  console.log(`Backend listening on :${port}`);
});
