import db from './database.js';

// Firebase Admin SDK initialization (optional - configure with your own credentials)
let firebaseAdmin = null;

/**
 * Initialize Firebase Admin SDK for push notifications
 * To enable, create a firebase-service-account.json file in the apisvc directory
 * and uncomment the initialization code below
 */
async function initializeFirebase() {
  try {
    // Uncomment and configure when you have Firebase credentials:
    // const admin = await import('firebase-admin');
    // const serviceAccount = JSON.parse(
    //   await fs.readFile('./firebase-service-account.json', 'utf8')
    // );
    // firebaseAdmin = admin.default;
    // firebaseAdmin.initializeApp({
    //   credential: firebaseAdmin.credential.cert(serviceAccount)
    // });
    // console.log('Firebase Admin SDK initialized');

    console.log('Push notifications: Firebase not configured (running in mock mode)');
  } catch (error) {
    console.log('Push notifications: Running in mock mode -', error.message);
  }
}

// Initialize on module load
initializeFirebase();

/**
 * Send push notification to all devices registered for a user
 * @param {number} userId - The user ID to send notifications to
 * @param {object} notification - The notification payload
 * @param {string} notification.title - Notification title
 * @param {string} notification.body - Notification body
 * @param {object} notification.data - Optional additional data
 */
export async function sendPushNotification(userId, notification) {
  try {
    // Get all device tokens for the user
    const stmt = db.prepare(`
      SELECT token, platform FROM device_tokens WHERE user_id = ?
    `);
    const devices = stmt.all(userId);

    if (devices.length === 0) {
      console.log(`No devices registered for user ${userId}`);
      return { sent: 0, failed: 0 };
    }

    let sent = 0;
    let failed = 0;

    for (const device of devices) {
      try {
        if (firebaseAdmin) {
          // Send real push notification via Firebase
          await firebaseAdmin.messaging().send({
            token: device.token,
            notification: {
              title: notification.title,
              body: notification.body
            },
            data: notification.data || {},
            ...(device.platform === 'ios' && {
              apns: {
                payload: {
                  aps: {
                    sound: 'default',
                    badge: 1
                  }
                }
              }
            }),
            ...(device.platform === 'android' && {
              android: {
                priority: 'high',
                notification: {
                  sound: 'default',
                  channelId: 'tasks'
                }
              }
            })
          });
          sent++;
        } else {
          // Mock mode - just log the notification
          console.log(`[MOCK PUSH] To ${device.platform} device: ${notification.title} - ${notification.body}`);
          sent++;
        }
      } catch (error) {
        console.error(`Failed to send notification to device ${device.token}:`, error.message);
        failed++;

        // Remove invalid tokens
        if (error.code === 'messaging/registration-token-not-registered') {
          const deleteStmt = db.prepare('DELETE FROM device_tokens WHERE token = ?');
          deleteStmt.run(device.token);
        }
      }
    }

    return { sent, failed };
  } catch (error) {
    console.error('Error sending push notifications:', error);
    return { sent: 0, failed: 0, error: error.message };
  }
}

/**
 * Send push notification to specific device token
 * @param {string} token - The device token
 * @param {string} platform - The platform (ios/android)
 * @param {object} notification - The notification payload
 */
export async function sendToDevice(token, platform, notification) {
  try {
    if (firebaseAdmin) {
      await firebaseAdmin.messaging().send({
        token,
        notification: {
          title: notification.title,
          body: notification.body
        },
        data: notification.data || {}
      });
      return { success: true };
    } else {
      console.log(`[MOCK PUSH] To ${platform}: ${notification.title} - ${notification.body}`);
      return { success: true, mock: true };
    }
  } catch (error) {
    console.error('Error sending to device:', error);
    return { success: false, error: error.message };
  }
}
