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
    const { residentId, adminId, complaintId, communityId, status, providerAssigned } = req.body || {};
    if (!residentId || !adminId || !complaintId) {
      return res.status(400).json({ error: 'missing_fields', required: ['residentId', 'adminId', 'complaintId'] });
    }

    const normalizedStatus = String(status || '').trim().toLowerCase();

    let title = 'Complaint Update';
    let body = 'Your complaint has been updated.';

    const isAssigned =
      providerAssigned === true ||
      normalizedStatus === 'assigned' ||
      normalizedStatus === 'in_progress' ||
      normalizedStatus === 'in progress';

    const isResolved = normalizedStatus === 'resolved';

    if (isResolved) {
      title = 'Complaint Resolved';
      body = 'Your complaint has been resolved';
    }
    else if (isAssigned) {
      title = 'Provider Assigned';
      body = 'Provider has been assigned for your complaint';
    }
    else if (normalizedStatus) {
      title = 'Status Updated';
      body = `Your complaint status is now: ${status}`;
    }

    const result = await sendToUser(residentId, title, body, {
      type: 'complaint_updated',
      // NOTE: we intentionally do NOT deep-link for now.
      // Click will open app dashboard on Notifications tab.
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
