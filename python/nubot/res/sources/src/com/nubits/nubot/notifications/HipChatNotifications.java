/*
 * Copyright (C) 2014 desrever <desrever at nubits.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.nubits.nubot.notifications;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import com.nubits.nubot.global.Global;
import com.nubits.nubot.global.Passwords;
import com.nubits.nubot.notifications.jhipchat.HipChat;
import com.nubits.nubot.notifications.jhipchat.Room;
import com.nubits.nubot.notifications.jhipchat.UserId;
import com.nubits.nubot.notifications.jhipchat.messages.Message.Color;
import java.util.logging.Logger;

public class HipChatNotifications {

    private static final Logger LOG = Logger.getLogger(HipChatNotifications.class.getName());
    private static String BOT_NAME = "Custodian Bot";
    private static HipChat hipchat = new HipChat(Passwords.HIPCHAT_KEY);
    private static UserId hipchatUser = UserId.create("idbot", BOT_NAME);
    private static Room notificationRoom = Room.create(Passwords.HIPCHAT_NOTIFICATIONS_ROOM_ID, hipchat);
    private static Room criticalNotificationRoom = Room.create(Passwords.HIPCHAT_CRITICAL_ROOM_ID, hipchat);

    public static void sendMessage(String message, Color color) {
        String publicAddress = "";
        boolean send = true; // default send
        boolean notify = false; //default notify
        if (Global.options != null) {
            publicAddress = Global.options.getNubitsAddress();
            send = Global.options.isSendHipchat();
            notify = false;
        }

        String toSend = message + " (" + publicAddress + ")";

        if (color == Color.RED) {
            notify = true;
        }

        if (send) {
            try {
                if (notify) {
                    criticalNotificationRoom.sendMessage(toSend, hipchatUser, notify, color);
                } else {
                    notificationRoom.sendMessage(toSend, hipchatUser, notify, color);
                }

            } catch (Exception e) {
                LOG.severe("Not sending hipchat notification. Network problem");
            }
        }
    }
}
