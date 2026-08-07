package com.onlineinterview.notification.chat;

/** Team chat destinations the platform can post interview events to, each via an
 *  incoming-webhook URL. All are optional and independently configured. */
public enum ChatProvider {
    SLACK,
    MICROSOFT_TEAMS,
    DISCORD,
    GOOGLE_CHAT,
    WEBHOOK
}
