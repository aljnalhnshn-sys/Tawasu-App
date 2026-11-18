const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore("ai-studio-f0a6dc55-c580-4b93-91e9-381782a539f0");
const messaging = getMessaging();

/**
 * Trusted server-side FCM notification trigger.
 * Keeps all Firebase Admin credentials strictly off the Android client.
 */
exports.notifyOnNewMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    const chatId = event.params.chatId;
    const senderId = message.senderId;

    const chatSnap = await db.collection("chats").doc(chatId).get();
    if (!chatSnap.exists) return;
    const chat = chatSnap.data();

    const senderSnap = await db.collection("users").doc(senderId).get();
    const senderName = senderSnap.exists
      ? senderSnap.data().displayName
      : "رسالة جديدة";

    const preview =
      message.type === "IMAGE"
        ? "أرسل صورة"
        : message.type === "VIDEO"
        ? "أرسل مقطع فيديو"
        : message.type === "AUDIO"
        ? "أرسل رسالة صوتية"
        : String(message.text || "").slice(0, 120);

    const recipients = (chat.participantIds || []).filter((uid) => uid !== senderId);

    for (const recipientUid of recipients) {
      // Respect user blocking before sending push notifications
      const blockedSnap = await db
        .collection("users")
        .doc(recipientUid)
        .collection("blocked")
        .doc(senderId)
        .get();
      if (blockedSnap.exists) continue;

      const privateSnap = await db
        .collection("users")
        .doc(recipientUid)
        .collection("private")
        .doc("info")
        .get();

      const tokens = privateSnap.exists ? privateSnap.data().fcmTokens || [] : [];
      if (tokens.length === 0) continue;

      const title = chat.type === "GROUP" ? `${chat.title} · ${senderName}` : senderName;

      await messaging.sendEachForMulticast({
        tokens,
        notification: {
          title,
          body: preview,
        },
        data: {
          chatId,
          senderId,
          senderName,
          preview,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "tawasul_messages",
          },
        },
      });
    }
  }
);
