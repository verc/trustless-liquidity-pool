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

import com.nubits.nubot.global.Global;
import com.nubits.nubot.global.Passwords;
import com.sun.mail.smtp.SMTPTransport;
import java.security.Security;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author doraemon
 */
public class MailNotifications {

    private static final Logger LOG = Logger.getLogger(MailNotifications.class.getName());

    public static void send(String address, String title, String message) {
        if (Global.options == null || Global.options.isSendMails()) {
            try {
                MailNotifications.Send(address, title, message);
            } catch (AddressException ex) {
                LOG.severe(ex.toString());
            } catch (MessagingException ex) {
                LOG.severe(ex.toString());
            }
        }
    }

    private MailNotifications() {
    }

    /**
     * Send email using GMail SMTP server.
     *
     * @param username GMail username
     * @param password GMail password
     * @param recipientEmail TO recipient
     * @param title title of the message
     * @param message message to be sent
     * @throws AddressException if the email address parse failed
     * @throws MessagingException if the connection is dead or not in the
     * connected state or if the message is not a MimeMessage
     */
    private static void Send(String recipientEmail, String title, String message) throws AddressException, MessagingException {
        title = "[NuBot] " + title;
        Date now = new Date();
        String footer = "\n --- \n Message generated at " + now;
        if (Global.options != null) {
            footer += "from bot with custodial address " + Global.options.getNubitsAddress() + " on " + Global.options.getExchangeName();
        }
        message = message + footer;
        MailNotifications.Send(recipientEmail, "", title, message);
    }

    /**
     * Send email using GMail SMTP server.
     *
     * @param username username
     * @param password password
     * @param recipientEmail TO recipient
     * @param ccEmail CC recipient. Can be empty if there is no CC recipient
     * @param title title of the message
     * @param message message to be sent
     * @throws AddressException if the email address parse failed
     * @throws MessagingException if the connection is dead or not in the
     * connected state or if the message is not a MimeMessage
     */
    private static void Send(String recipientEmail, String ccEmail, String title, String message) throws AddressException, MessagingException {
        Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";

        // Get a Properties object
        Properties props = System.getProperties();
        props.setProperty("mail.smtps.host", Passwords.SMTP_HOST);
        props.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
        props.setProperty("mail.smtp.socketFactory.fallback", "false");
        props.setProperty("mail.smtp.port", "465");
        props.setProperty("mail.smtp.socketFactory.port", "465");
        props.setProperty("mail.smtps.auth", "false");

        /*
         If set to false, the QUIT command is sent and the connection is immediately closed. If set
         to true (the default), causes the transport to wait for the response to the QUIT command.

         ref :   http://java.sun.com/products/javamail/javadocs/com/sun/mail/smtp/package-summary.html
         http://forum.java.sun.com/thread.jspa?threadID=5205249
         smtpsend.java - demo program from javamail
         */
        props.put("mail.smtps.quitwait", "false");

        Session session = Session.getInstance(props, null);

        // -- Create a new message --
        final MimeMessage msg = new MimeMessage(session);

        // -- Set the FROM and TO fields --
        msg.setFrom(new InternetAddress(Passwords.SMTP_USERNAME + ""));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail, false));

        if (ccEmail.length() > 0) {
            msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccEmail, false));
        }

        msg.setSubject(title);
        msg.setText(message, "utf-8");
        msg.setSentDate(new Date());

        SMTPTransport t = (SMTPTransport) session.getTransport("smtps");

        t.connect(Passwords.SMTP_HOST, Passwords.SMTP_USERNAME, Passwords.SMTP_PASSWORD);
        t.sendMessage(msg, msg.getAllRecipients());
        t.close();
    }
}
