/*
 * External backend entrypoint (Render / any Node host).
 * This is NOT Firebase Cloud Functions.
 */

const express = require('express');
const cors = require('cors');

const { initFirebaseAdmin } = require('./src/firebaseAdmin');
const eventsRouter = require('./src/routes/events');

const app = express();

app.use(cors());
app.use(express.json({ limit: '1mb' }));

// Health
app.get('/health', (req, res) => {
  res.json({ ok: true, service: 'smartneighborhoodhelper-backend', ts: Date.now() });
});

// Initialize Firebase Admin (service account)
initFirebaseAdmin();

// Routes
app.use('/events', eventsRouter);

// Basic error handler
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error('[ERROR]', err);
  res.status(500).json({ error: 'internal_error', message: err?.message || String(err) });
});

const port = process.env.PORT || 8080;
app.listen(port, () => {
  console.log(`[BOOT] listening on :${port}`);
});

