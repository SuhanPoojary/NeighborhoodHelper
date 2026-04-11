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
async function sendToUser(userId, title, body, data = {}) {
  const snapshot = await db
    .collection("fcmTokens")
    .doc(userId)
    .collection("tokens")
    .get();

  const tokenDocs = snapshot.docs
    .map(doc => ({ id: doc.id, token: doc.data().token }))
    .filter(x => typeof x.token === "string" && x.token.length > 0);

  const tokens = tokenDocs.map(x => x.token);

  console.log(`[FCM] userId=${userId} tokens=${tokens.length}`);

  if (tokens.length === 0) return { successCount: 0, failureCount: 0 };

  const multicastMessage = {
    tokens,
    notification: { title, body },
    // Data payload is what we use for safe deep-links in Android
    data: {
      title,
      body,
      ...Object.fromEntries(
        Object.entries(data).map(([k, v]) => [String(k), v == null ? "" : String(v)])
      ),
    },
    android: {
      priority: "high",
      notification: {
        channelId: "complaints",
      },
    },
  };

  const result = await admin.messaging().sendEachForMulticast(multicastMessage);

  console.log(
    `[FCM] userId=${userId} success=${result.successCount} failure=${result.failureCount}`
  );

  // Cleanup invalid/unregistered tokens
  const invalidDocIds = [];
  result.responses.forEach((r, idx) => {
    if (r.success) return;
    const code = r.error?.code || "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      invalidDocIds.push(tokenDocs[idx]?.id);
    }
    console.log(`[FCM] send error tokenIndex=${idx} code=${code} msg=${r.error?.message}`);
  });

  if (invalidDocIds.length) {
    console.log(`[FCM] deleting invalid tokens: ${invalidDocIds.length}`);
    await Promise.all(
      invalidDocIds.map(docId =>
        db
          .collection("fcmTokens")
          .doc(userId)
          .collection("tokens")
          .doc(docId)
          .delete()
          .catch(() => {})
      )
    );
  }

  return { successCount: result.successCount, failureCount: result.failureCount };
}

function complaintUpdateCopy({ status, providerName }) {
  const s = String(status || "").trim().toLowerCase();

  // In our app, provider assignment typically maps to "In Progress".
  const isProviderAssigned =
    s === "assigned" ||
    s === "provider_assigned" ||
    s === "in_progress" ||
    s === "in progress" ||
    s === "inprogress";

  if (isProviderAssigned) {
    return {
      title: "Provider Assigned",
      body: providerName
        ? `${providerName} has been assigned for your complaint.`
        : "Provider has been assigned for your complaint.",
      type: "provider_assigned",
    };
  }

  const isResolved = s === "resolved" || s === "completed";
  if (isResolved) {
    return {
      title: "Complaint Resolved",
      body: "Your complaint has been resolved.",
      type: "complaint_resolved",
    };
  }

  if (s) {
    return {
      title: "Complaint Update",
      body: `Status updated to ${status}.`,
      type: "complaint_updated",
    };
  }

  return {
    title: "Complaint Update",
    body: "Your complaint has been updated.",
    type: "complaint_updated",
  };
}

// 📩 APIs

app.post("/events/complaint-created", async (req, res) => {
  try {
    const { adminId, complaintId } = req.body;
    await sendToUser(adminId, "New Complaint", "A new complaint has been registered.", {
      target: complaintId ? "complaint_detail" : "notifications",
      complaintId: complaintId || "",
      type: "complaint_created",
    });
    res.json({ ok: true });
  } catch (e) {
    console.error("/events/complaint-created error", e);
    res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
});

app.post("/events/complaint-updated", async (req, res) => {
  try {
    const { residentId, complaintId, status, providerName } = req.body;
    const { title, body, type } = complaintUpdateCopy({ status, providerName });

    await sendToUser(residentId, title, body, {
      // Prefer details if we have an id; otherwise go to notifications.
      target: complaintId ? "complaint_detail" : "notifications",
      complaintId: complaintId || "",
      type: type || "complaint_updated",
      status: status || "",
    });

    res.json({ ok: true });
  } catch (e) {
    console.error("/events/complaint-updated error", e);
    res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
});

app.post("/events/complaint-reopened", async (req, res) => {
  try {
    const { adminId, complaintId } = req.body;
    await sendToUser(adminId, "Complaint Reopened", "A resident reopened a complaint.", {
      target: complaintId ? "complaint_detail" : "notifications",
      complaintId: complaintId || "",
      type: "complaint_reopened",
    });
    res.json({ ok: true });
  } catch (e) {
    console.error("/events/complaint-reopened error", e);
    res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
});

// NEW: Join request -> notify admin and open Admin Requests tab on click
app.post("/events/join-request", async (req, res) => {
  try {
    const { adminId, residentName, communityId, joinRequestId } = req.body;

    if (!adminId) {
      return res.status(400).json({ ok: false, error: "Missing adminId" });
    }

    const title = "New user wants to join";
    const body = `${residentName || "A resident"} wants to join your community.`;

    await sendToUser(adminId, title, body, {
      target: "admin_requests",
      type: "join_request",
      communityId: communityId || "",
      joinRequestId: joinRequestId || "",
    });

    res.json({ ok: true });
  } catch (e) {
    console.error("/events/join-request error", e);
    res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
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