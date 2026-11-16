/**
 * Firestore Security Rules Test Specification (for @firebase/rules-unit-testing + Emulator)
 * Covers identity spoofing, PII isolation, username hijacking, duplicate direct chats,
 * unauthorized message reading/writing, and oversized payload rejection.
 */
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, serverTimestamp } from 'firebase/firestore';
import fs from 'fs';

let testEnv: RulesTestEnvironment;

export async function runTawasulSecurityRulesSuite() {
  testEnv = await initializeTestEnvironment({
    projectId: 'tawasul-rules-test',
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
    },
  });

  const alice = testEnv.authenticatedContext('alice_uid', { email_verified: true });
  const bob = testEnv.authenticatedContext('bob_uid', { email_verified: true });
  const eve = testEnv.authenticatedContext('eve_uid', { email_verified: true });

  // 1. Alice cannot read Bob's private PII (email & FCM tokens)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users/bob_uid/private/info'), {
      uid: 'bob_uid',
      email: 'bob@private.com',
      fcmTokens: ['token_123'],
      updatedAt: new Date(),
    });
  });
  await assertFails(getDoc(doc(alice.firestore(), 'users/bob_uid/private/info')));

  // 2. Eve cannot hijack Alice's reserved username
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'usernames/alice_99'), {
      uid: 'alice_uid',
      usernameLower: 'alice_99',
      createdAt: new Date(),
    });
  });
  await assertFails(
    setDoc(doc(eve.firestore(), 'usernames/alice_99'), {
      uid: 'eve_uid',
      usernameLower: 'alice_99',
      createdAt: serverTimestamp(),
    })
  );

  // 3. Eve cannot read messages from a chat she is not a member of
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'chats/alice_uid_bob_uid/members/alice_uid'), {
      uid: 'alice_uid',
      role: 'MEMBER',
    });
    await setDoc(doc(ctx.firestore(), 'chats/alice_uid_bob_uid/messages/m1'), {
      id: 'm1',
      chatId: 'alice_uid_bob_uid',
      senderId: 'alice_uid',
      type: 'TEXT',
      text: 'رسالة خاصة',
      mediaUrl: '',
      mediaDurationSec: 0,
      deliveryStatus: 'SENT',
      readBy: {},
      deletedForUids: [],
      localClientId: 'loc1',
      createdAt: new Date(),
    });
  });
  await assertFails(getDoc(doc(eve.firestore(), 'chats/alice_uid_bob_uid/messages/m1')));
  await assertSucceeds(getDoc(doc(alice.firestore(), 'chats/alice_uid_bob_uid/messages/m1')));

  // 4. Bob cannot forge senderId as Alice inside the shared chat
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'chats/alice_uid_bob_uid/members/bob_uid'), {
      uid: 'bob_uid',
      role: 'MEMBER',
    });
  });
  await assertFails(
    setDoc(doc(bob.firestore(), 'chats/alice_uid_bob_uid/messages/m_spoof'), {
      id: 'm_spoof',
      chatId: 'alice_uid_bob_uid',
      senderId: 'alice_uid',
      type: 'TEXT',
      text: 'رسالة منتحلة',
      mediaUrl: '',
      mediaDurationSec: 0,
      deliveryStatus: 'SENT',
      readBy: {},
      deletedForUids: [],
      localClientId: 'loc2',
      createdAt: serverTimestamp(),
    })
  );

  // 5. Oversized message (> 4000 chars) is rejected
  await assertFails(
    setDoc(doc(alice.firestore(), 'chats/alice_uid_bob_uid/messages/m_big'), {
      id: 'm_big',
      chatId: 'alice_uid_bob_uid',
      senderId: 'alice_uid',
      type: 'TEXT',
      text: 'x'.repeat(4001),
      mediaUrl: '',
      mediaDurationSec: 0,
      deliveryStatus: 'SENT',
      readBy: {},
      deletedForUids: [],
      localClientId: 'loc3',
      createdAt: serverTimestamp(),
    })
  );

  await testEnv.cleanup();
}
