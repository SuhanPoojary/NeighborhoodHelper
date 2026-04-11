import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import admin from "firebase-admin";

admin.initializeApp();

type Complaint = {
  id?: string;
  title?: string;
  description?: string;
  category?: string;
  status?: string;
  reportedBy?: string;
  communityId?: string;
  assignedProvider?: string;
  updatedAt?: number;
  // add common fields we might compare
  priority?: string;
  imageUrl?: string;
  resolvedConfirmedByResident?: boolean;
};

type InAppNotification = {
  id?: string;
  userId: string;
  communityId: string;
  complaintId: string;
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: number;
};

type JoinRequest = {
  id?: string;
  communityId?: string;
  communityName?: string;
  residentUid?: string;
  residentName?: string;
  residentEmail?: string;
  residentPhone?: string;
  status?: string;
  createdAt?: number;
  updatedAt?: number;
};

// NOTE:
// In-app notifications are stored in Firestore (notifications collection).
// We ALSO attempt to send real device push notifications via FCM when tokens exist.

async function getUserTokens(uid: string): Promise<string[]> {
  if (!uid) return [];
  const snap = await admin.firestore().collection("fcmTokens").doc(uid).collection("tokens").get();
  return snap.docs
    .map((d) => (d.data()?.token as string) || d.id)
    .filter((t) => typeof t === "string" && t.length > 0);
}

async function sendPushToUser(
  uid: string,
  payload: { title: string; body: string; data?: Record<string, string> }
) {
  try {
    const tokens = await getUserTokens(uid);
    if (tokens.length === 0) return;

    const res = await admin.messaging().sendEachForMulticast({
      tokens,
      notification: {
        title: payload.title,
        body: payload.body,
      },
      data: payload.data || {},
      android: {
        priority: "high",
        notification: {
          // Must match Android channel id created in the app (Constants.NOTIFICATION_CHANNEL_ID)
          channelId: "complaint_updates_v2",
        },
      },
    });

    // Clean up invalid tokens
    const invalid: string[] = [];
    res.responses.forEach((r, idx) => {
      if (r.success) return;
      const code = (r.error as any)?.code as string | undefined;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        invalid.push(tokens[idx]);
      }
    });

    await Promise.all(
      invalid.map((t) =>
        admin.firestore().collection("fcmTokens").doc(uid).collection("tokens").doc(t).delete()
      )
    );

    logger.info("FCM push sent", { uid, tokens: tokens.length, success: res.successCount });
  } catch (e) {
    // Don't break Firestore triggers if push fails.
    logger.warn("FCM push failed", { uid, error: (e as Error).message });
  }
}

async function writeInAppNotification(n: Omit<InAppNotification, "id">) {
  const ref = admin.firestore().collection("notifications").doc();
  const doc: InAppNotification = { ...n, id: ref.id };
  await ref.set(doc);
}

async function writeInAppAndPush(n: Omit<InAppNotification, "id">) {
  await writeInAppNotification(n);
  await sendPushToUser(n.userId, {
    title: n.title,
    body: n.message,
    data: {
      complaintId: n.complaintId || "",
      type: n.type || "",
      communityId: n.communityId || "",
    },
  });
}

export const onComplaintCreated = onDocumentCreated("complaints/{complaintId}", async (event) => {
  const data = event.data?.data() as Complaint | undefined;
  if (!data) return;

  const complaintId = event.params.complaintId as string;
  const category = data.category || data.title || "Complaint";

  const communityId = data.communityId || "";

  // 1) Notify admin (IN-APP + PUSH) for that community (from communities/{id}.adminUid)
  if (communityId) {
    const commDoc = await admin.firestore().collection("communities").doc(communityId).get();
    const adminUid = (commDoc.data()?.adminUid as string) || "";
    if (adminUid) {
      const title = "New complaint submitted";
      const body = `New ${category} complaint reported.`;

      await writeInAppAndPush({
        userId: adminUid,
        communityId,
        complaintId,
        type: "new_complaint",
        title,
        message: body,
        isRead: false,
        createdAt: Date.now(),
      });
    }
  }

  // 2) Notify reporter (IN-APP + PUSH) that complaint is submitted
  const reporterUid = data.reportedBy || "";
  if (reporterUid) {
    await writeInAppAndPush({
      userId: reporterUid,
      communityId: communityId,
      complaintId,
      type: "new_complaint",
      title: "Complaint submitted",
      message: "Your complaint has been submitted successfully.",
      isRead: false,
      createdAt: Date.now(),
    });
  }
});

export const onComplaintUpdated = onDocumentUpdated("complaints/{complaintId}", async (event) => {
  const before = event.data?.before.data() as Complaint | undefined;
  const after = event.data?.after.data() as Complaint | undefined;
  if (!before || !after) return;

  const complaintId = event.params.complaintId as string;

  const beforeStatus = before.status || "";
  const afterStatus = after.status || "";
  const beforeProvider = before.assignedProvider || "";
  const afterProvider = after.assignedProvider || "";

  // Detect interesting changes
  const statusChanged = beforeStatus !== afterStatus;
  const providerChanged = beforeProvider !== afterProvider;

  // Optional: detect resident edits (description/priority/image)
  const descriptionChanged = (before.description || "") !== (after.description || "");
  const priorityChanged = (before.priority || "") !== (after.priority || "");
  const imageChanged = (before.imageUrl || "") !== (after.imageUrl || "");
  const resolvedConfirmChanged =
    (before.resolvedConfirmedByResident ?? false) !== (after.resolvedConfirmedByResident ?? false);

  // If nothing interesting changed, ignore.
  if (
    !statusChanged &&
    !providerChanged &&
    !descriptionChanged &&
    !priorityChanged &&
    !imageChanged &&
    !resolvedConfirmChanged
  ) {
    return;
  }

  const communityId = after.communityId || "";
  const reporterUid = after.reportedBy || "";

  // ── 1) Resident notification when ADMIN changes status/provider ──
  // We only notify the resident for status/provider changes.
  if (reporterUid && (statusChanged || providerChanged)) {
    let type = "status_changed";
    let title = "Complaint updated";
    let body = "Your complaint has been updated.";
    let target = "notifications";

    if (providerChanged) {
      type = "provider_assigned";
      title = "Provider Assigned";
      body = afterProvider.length > 0
        ? "Provider has been assigned for your complaint."
        : "The assigned provider was removed from your complaint.";
      target = "complaint_detail";
    } else if (statusChanged) {
      type = "status_changed";

      const normalized = (afterStatus || "").toLowerCase();
      if (normalized === "resolved") {
        title = "Complaint Resolved";
        body = "Your complaint has been Resolved.";
        target = "complaint_detail";
      } else if (normalized === "in progress" || normalized === "in_progress" || normalized === "inprogress") {
        // In your UX, this happens when admin assigns provider.
        title = "Provider Assigned";
        body = "Provider has been Assigned for your Complaint.";
        target = "complaint_detail";
      } else {
        title = "Status updated";
        body = `Your complaint status is now: ${afterStatus || "Updated"}.`;
        target = "complaint_detail";
      }
    }

    await writeInAppAndPush({
      userId: reporterUid,
      communityId,
      complaintId,
      type,
      title,
      message: body,
      isRead: false,
      createdAt: Date.now(),
    });

    // Also push extra routing hints for Android click handling.
    await sendPushToUser(reporterUid, {
      title,
      body,
      data: {
        complaintId: complaintId || "",
        type: type || "",
        communityId: communityId || "",
        target,
      },
    });

    logger.info("Resident in-app notification written", { complaintId, type, reporterUid });
  }

  // ── 2) Admin notification when RESIDENT edits complaint details ──
  if (
    communityId &&
    reporterUid &&
    (descriptionChanged || priorityChanged || imageChanged || statusChanged || resolvedConfirmChanged)
  ) {
    const commDoc = await admin.firestore().collection("communities").doc(communityId).get();
    const adminUid = (commDoc.data()?.adminUid as string) || "";

    if (!adminUid || adminUid === reporterUid) {
      return;
    }

    const updatedBy = (after as any).updatedBy || (after as any).lastUpdatedBy || "";
    if (updatedBy && updatedBy === adminUid) {
      logger.info("Skip admin notification: update performed by admin", { complaintId, adminUid });
      return;
    }

    let title = "Complaint updated by resident";
    let body = "A resident updated a complaint.";

    if (resolvedConfirmChanged) {
      const confirmed = (after.resolvedConfirmedByResident ?? false) === true;
      title = confirmed ? "Resident confirmed resolution" : "Resident reopened complaint";
      body = confirmed
        ? "Resident marked the complaint as fixed (confirmed)."
        : "Resident said it's not fixed, complaint reopened.";
    } else if (statusChanged) {
      title = "Resident updated complaint status";
      body = `Resident changed status: ${beforeStatus || ""} → ${afterStatus || ""}`.trim();
    } else if (descriptionChanged) {
      body = "Resident updated the complaint description.";
    } else if (priorityChanged) {
      body = "Resident updated the complaint priority.";
    } else if (imageChanged) {
      body = "Resident updated/added an image for the complaint.";
    }

    await writeInAppAndPush({
      userId: adminUid,
      communityId,
      complaintId,
      type: "complaint_changed",
      title,
      message: body,
      isRead: false,
      createdAt: Date.now(),
    });

    logger.info("Admin in-app notification written", { complaintId, adminUid });
  }
});

export const onJoinRequestCreated = onDocumentCreated("joinRequests/{requestId}", async (event) => {
  const data = event.data?.data() as JoinRequest | undefined;
  if (!data) return;

  const requestId = event.params.requestId as string;
  const communityId = data.communityId || "";
  if (!communityId) return;

  // Notify community admin
  const commDoc = await admin.firestore().collection("communities").doc(communityId).get();
  const adminUid = (commDoc.data()?.adminUid as string) || "";
  if (!adminUid) return;

  const residentName = data.residentName || "A resident";

  await writeInAppAndPush({
    userId: adminUid,
    communityId,
    complaintId: requestId, // keep field non-empty; using requestId for routing/debug
    type: "join_request",
    title: "New join request",
    message: `${residentName} wants to join your community.`,
    isRead: false,
    createdAt: Date.now(),
  });

  await sendPushToUser(adminUid, {
    title: "New user wants to join",
    body: `${residentName} wants to join your community.`,
    data: {
      type: "join_request",
      communityId,
      joinRequestId: requestId,
      target: "admin_requests",
    } as any,
  });

  logger.info("Admin join request notification sent", { requestId, adminUid, communityId });
});
