const express = require("express");
const { sendToUser } = require("../services/notificationService");

const router = express.Router();

function requireFields(body, fields) {
  const missing = fields.filter((f) => !body || body[f] == null || String(body[f]).trim() === "");
  return missing;
}

router.post("/complaint-created", async (req, res) => {
  try {
    const missing = requireFields(req.body, ["adminId", "complaintId"]);
    if (missing.length) return res.status(400).json({ ok: false, error: `Missing: ${missing}` });

    const { adminId, complaintId, residentName, complaintTitle, communityId } = req.body;

    const title = "New complaint";
    const body = `${residentName || "Resident"} created: ${complaintTitle || "a complaint"}`;

    const result = await sendToUser(adminId, title, body, {
      type: "complaint_created",
      complaintId,
      communityId: communityId || "",
    });

    res.json({ ok: true, result });
  } catch (error) {
    console.error("[API] /complaint-created error:", error);
    res.status(500).json({ ok: false, error: "Failed to send notification" });
  }
});

router.post("/complaint-updated", async (req, res) => {
  try {
    const missing = requireFields(req.body, ["residentId", "complaintId"]);
    if (missing.length) return res.status(400).json({ ok: false, error: `Missing: ${missing}` });

    const { residentId, complaintId, status, updatedByName, communityId } = req.body;

    // Better UX copy for the resident
    const normalizedStatus = String(status || "").trim().toLowerCase();

    let title = "Complaint updated";
    let body = "Your complaint has been updated.";

    if (normalizedStatus === "in progress" || normalizedStatus === "in_progress" || normalizedStatus === "inprogress") {
      // In your app, this status often implies provider assignment.
      title = "Provider Assigned";
      body = "Provider has been assigned to your complaint.";
    } else if (normalizedStatus === "resolved") {
      title = "Complaint Resolved";
      body = "Your complaint has been resolved.";
    } else if (normalizedStatus) {
      title = "Complaint updated";
      body = `${updatedByName || "Admin"} updated status to ${status}`;
    }

    const result = await sendToUser(residentId, title, body, {
      type: normalizedStatus === "resolved"
        ? "complaint_resolved"
        : (normalizedStatus === "in progress" || normalizedStatus === "in_progress" || normalizedStatus === "inprogress")
          ? "provider_assigned"
          : "complaint_updated",
      complaintId,
      status: status || "",
      communityId: communityId || "",
    });

    res.json({ ok: true, result });
  } catch (error) {
    console.error("[API] /complaint-updated error:", error);
    res.status(500).json({ ok: false, error: "Failed to send notification" });
  }
});

router.post("/complaint-reopened", async (req, res) => {
  try {
    const missing = requireFields(req.body, ["adminId", "complaintId"]);
    if (missing.length) return res.status(400).json({ ok: false, error: `Missing: ${missing}` });

    const { adminId, complaintId, residentName, communityId } = req.body;

    const title = "Complaint reopened";
    const body = `${residentName || "Resident"} reopened a complaint`;

    const result = await sendToUser(adminId, title, body, {
      type: "complaint_reopened",
      complaintId,
      communityId: communityId || "",
    });

    res.json({ ok: true, result });
  } catch (error) {
    console.error("[API] /complaint-reopened error:", error);
    res.status(500).json({ ok: false, error: "Failed to send notification" });
  }
});

router.post("/join-request", async (req, res) => {
  try {
    const missing = requireFields(req.body, ["adminId", "residentId"]);
    if (missing.length) return res.status(400).json({ ok: false, error: `Missing: ${missing}` });

    const { adminId, residentId, residentName, communityName, communityId } = req.body;

    const title = "Join request";
    const body = `${residentName || "Resident"} requested to join ${communityName || "community"}`;

    const result = await sendToUser(adminId, title, body, {
      type: "join_request",
      residentId,
      communityId: communityId || "",
    });

    res.json({ ok: true, result });
  } catch (error) {
    console.error("[API] /join-request error:", error);
    res.status(500).json({ ok: false, error: "Failed to send notification" });
  }
});

module.exports = router;
