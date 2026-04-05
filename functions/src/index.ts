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

// NOTE:
// We intentionally removed FCM/push notification sending to avoid needing billing (Blaze)
// and to keep the project focused on the in-app Notifications tab (Firestore-backed).

async function writeInAppNotification(n: Omit<InAppNotification, "id">) {
  const ref = admin.firestore().collection("notifications").doc();
  const doc: InAppNotification = { ...n, id: ref.id };
  await ref.set(doc);
}

export const onComplaintCreated = onDocumentCreated("complaints/{complaintId}", async (event) => {
  const data = event.data?.data() as Complaint | undefined;
  if (!data) return;

  const complaintId = event.params.complaintId as string;
  const category = data.category || data.title || "Complaint";

  const communityId = data.communityId || "";

  // 1) Notify admin (IN-APP only) for that community (from communities/{id}.adminUid)
  if (communityId) {
    const commDoc = await admin.firestore().collection("communities").doc(communityId).get();
    const adminUid = (commDoc.data()?.adminUid as string) || "";
    if (adminUid) {
      const title = "New complaint submitted";
      const body = `New ${category} complaint reported.`;

      await writeInAppNotification({
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

  // 2) Notify reporter (IN-APP) that complaint is submitted
  const reporterUid = data.reportedBy || "";
  if (reporterUid) {
    await writeInAppNotification({
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

  // Detect interesting changes
  const statusChanged = (before.status || "") !== (after.status || "");
  const providerChanged = (before.assignedProvider || "") !== (after.assignedProvider || "");
  if (!statusChanged && !providerChanged) return;

  const reporterUid = after.reportedBy || "";
  if (!reporterUid) return;

  let type = "status_changed";
  let title = "Complaint updated";
  let body = "Your complaint has been updated.";

  if (providerChanged && (after.assignedProvider || "").length > 0) {
    type = "provider_assigned";
    title = "Provider assigned";
    body = "A service provider has been assigned to your complaint.";
  } else if (statusChanged) {
    title = "Status updated";
    body = `Your complaint status is now: ${after.status || "Updated"}.`;
  }

  // In-app notification for resident
  await writeInAppNotification({
    userId: reporterUid,
    communityId: after.communityId || "",
    complaintId,
    type,
    title,
    message: body,
    isRead: false,
    createdAt: Date.now(),
  });

  logger.info("In-app notification written", { complaintId, type, reporterUid });
});
