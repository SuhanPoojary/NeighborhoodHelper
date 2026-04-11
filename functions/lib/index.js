"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.onComplaintUpdated = exports.onComplaintCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const firebase_functions_1 = require("firebase-functions");
const firebase_admin_1 = __importDefault(require("firebase-admin"));
firebase_admin_1.default.initializeApp();
// NOTE:
// In-app notifications are stored in Firestore (notifications collection).
// We ALSO attempt to send real device push notifications via FCM when tokens exist.
async function getUserTokens(uid) {
    if (!uid)
        return [];
    const snap = await firebase_admin_1.default.firestore().collection("fcmTokens").doc(uid).collection("tokens").get();
    return snap.docs
        .map((d) => d.data()?.token || d.id)
        .filter((t) => typeof t === "string" && t.length > 0);
}
async function sendPushToUser(uid, payload) {
    try {
        const tokens = await getUserTokens(uid);
        if (tokens.length === 0)
            return;
        const res = await firebase_admin_1.default.messaging().sendEachForMulticast({
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
        const invalid = [];
        res.responses.forEach((r, idx) => {
            if (r.success)
                return;
            const code = r.error?.code;
            if (code === "messaging/registration-token-not-registered" ||
                code === "messaging/invalid-registration-token") {
                invalid.push(tokens[idx]);
            }
        });
        await Promise.all(invalid.map((t) => firebase_admin_1.default.firestore().collection("fcmTokens").doc(uid).collection("tokens").doc(t).delete()));
        firebase_functions_1.logger.info("FCM push sent", { uid, tokens: tokens.length, success: res.successCount });
    }
    catch (e) {
        // Don't break Firestore triggers if push fails.
        firebase_functions_1.logger.warn("FCM push failed", { uid, error: e.message });
    }
}
async function writeInAppNotification(n) {
    const ref = firebase_admin_1.default.firestore().collection("notifications").doc();
    const doc = { ...n, id: ref.id };
    await ref.set(doc);
}
async function writeInAppAndPush(n) {
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
exports.onComplaintCreated = (0, firestore_1.onDocumentCreated)("complaints/{complaintId}", async (event) => {
    const data = event.data?.data();
    if (!data)
        return;
    const complaintId = event.params.complaintId;
    const category = data.category || data.title || "Complaint";
    const communityId = data.communityId || "";
    // 1) Notify admin (IN-APP + PUSH) for that community (from communities/{id}.adminUid)
    if (communityId) {
        const commDoc = await firebase_admin_1.default.firestore().collection("communities").doc(communityId).get();
        const adminUid = commDoc.data()?.adminUid || "";
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
exports.onComplaintUpdated = (0, firestore_1.onDocumentUpdated)("complaints/{complaintId}", async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after)
        return;
    const complaintId = event.params.complaintId;
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
    const resolvedConfirmChanged = (before.resolvedConfirmedByResident ?? false) !== (after.resolvedConfirmedByResident ?? false);
    // If nothing interesting changed, ignore.
    if (!statusChanged &&
        !providerChanged &&
        !descriptionChanged &&
        !priorityChanged &&
        !imageChanged &&
        !resolvedConfirmChanged) {
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
        if (providerChanged) {
            if (afterProvider.length > 0) {
                type = "provider_assigned";
                title = "Provider assigned";
                body = "A service provider has been assigned to your complaint.";
            }
            else {
                type = "provider_assigned";
                title = "Provider unassigned";
                body = "The assigned provider was removed from your complaint.";
            }
        }
        else if (statusChanged) {
            title = "Status updated";
            body = `Your complaint status is now: ${afterStatus || "Updated"}.`;
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
        firebase_functions_1.logger.info("Resident in-app notification written", { complaintId, type, reporterUid });
    }
    // ── 2) Admin notification when RESIDENT edits complaint details ──
    if (communityId &&
        reporterUid &&
        (descriptionChanged || priorityChanged || imageChanged || statusChanged || resolvedConfirmChanged)) {
        const commDoc = await firebase_admin_1.default.firestore().collection("communities").doc(communityId).get();
        const adminUid = commDoc.data()?.adminUid || "";
        if (!adminUid || adminUid === reporterUid) {
            return;
        }
        const updatedBy = after.updatedBy || after.lastUpdatedBy || "";
        if (updatedBy && updatedBy === adminUid) {
            firebase_functions_1.logger.info("Skip admin notification: update performed by admin", { complaintId, adminUid });
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
        }
        else if (statusChanged) {
            title = "Resident updated complaint status";
            body = `Resident changed status: ${beforeStatus || ""} → ${afterStatus || ""}`.trim();
        }
        else if (descriptionChanged) {
            body = "Resident updated the complaint description.";
        }
        else if (priorityChanged) {
            body = "Resident updated the complaint priority.";
        }
        else if (imageChanged) {
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
        firebase_functions_1.logger.info("Admin in-app notification written", { complaintId, adminUid });
    }
});
