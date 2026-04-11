const express = require('express');
const { sendToUser } = require('../services/pushService');

const router = express.Router();

function requireEventSecret(req, res, next) {
  const required = process.env.EVENTS_SECRET;
  if (!required) return next();
  const got = req.header('x-events-secret');
  if (got !== required) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  return next();
}

router.use(requireEventSecret);

// Contract: each endpoint returns send stats.

router.post('/complaint-created', async (req, res, next) => {
  try {
    const { adminId, residentId, complaintId, communityId, category } = req.body || {};
    if (!adminId || !residentId || !complaintId) {
      return res.status(400).json({ error: 'missing_fields', required: ['adminId', 'residentId', 'complaintId'] });
    }

    const title = 'New complaint submitted';
    const body = `A resident submitted a${category ? ` ${category}` : ''} complaint.`;

    const result = await sendToUser(adminId, title, body, {
      type: 'complaint_created',
      complaintId,
      communityId: communityId || '',
      residentId,
    });

    res.json({ ok: true, event: 'complaint-created', result });
  } catch (e) {
    next(e);
  }
});

router.post('/complaint-updated', async (req, res, next) => {
  try {
    const { residentId, adminId, complaintId, communityId, status } = req.body || {};
    if (!residentId || !adminId || !complaintId) {
      return res.status(400).json({ error: 'missing_fields', required: ['residentId', 'adminId', 'complaintId'] });
    }

    const title = 'Complaint updated';
    const body = status ? `Your complaint status is now: ${status}` : 'Your complaint has been updated.';

    const result = await sendToUser(residentId, title, body, {
      type: 'complaint_updated',
      complaintId,
      communityId: communityId || '',
      adminId,
      status: status || '',
    });

    res.json({ ok: true, event: 'complaint-updated', result });
  } catch (e) {
    next(e);
  }
});

router.post('/complaint-reopened', async (req, res, next) => {
  try {
    const { adminId, residentId, complaintId, communityId } = req.body || {};
    if (!adminId || !residentId || !complaintId) {
      return res.status(400).json({ error: 'missing_fields', required: ['adminId', 'residentId', 'complaintId'] });
    }

    const title = 'Complaint reopened';
    const body = 'A resident reopened a complaint.';

    const result = await sendToUser(adminId, title, body, {
      type: 'complaint_reopened',
      complaintId,
      communityId: communityId || '',
      residentId,
    });

    res.json({ ok: true, event: 'complaint-reopened', result });
  } catch (e) {
    next(e);
  }
});

router.post('/join-request', async (req, res, next) => {
  try {
    const { adminId, residentId, communityId } = req.body || {};
    if (!adminId || !residentId || !communityId) {
      return res.status(400).json({ error: 'missing_fields', required: ['adminId', 'residentId', 'communityId'] });
    }

    const title = 'New join request';
    const body = 'A resident requested to join your community.';

    const result = await sendToUser(adminId, title, body, {
      type: 'join_request',
      communityId,
      residentId,
    });

    res.json({ ok: true, event: 'join-request', result });
  } catch (e) {
    next(e);
  }
});

module.exports = router;

