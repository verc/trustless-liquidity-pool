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
package com.nubits.nubot.tests;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import com.nubits.nubot.NTP.NTPClient;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;
import com.nubits.nubot.notifications.HipChatNotifications;
import com.nubits.nubot.pricefeeds.AbstractPriceFeed;
import com.nubits.nubot.pricefeeds.PriceFeedManager;
import com.nubits.nubot.utils.Utils;
import com.nubits.nubot.utils.logging.NuLogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestSync extends TimerTask {

    private static final Logger LOG = Logger.getLogger(TestSync.class.getName());
    private static final int TASK_INTERVAL = 61;
    private static final int TASK_MAX_EXECUTION_INTERVAL = 50;
    private static String id;
    private static int startTime;
    private static CurrencyPair pair = Constant.BTC_USD;
    private static AbstractPriceFeed feed;

    public static void main(String[] args) throws InterruptedException {
        //Run multiple instance of this test to see if they read the same price.
        //It sends a notification on hipchat after syncing with a remote time server
        //Change parameters above


        startTime = (int) (System.currentTimeMillis() / 1000);
        System.out.println("Start-time = " + startTime);
        id = UUID.randomUUID().toString();

        init();

        message("Started");
        //Random sleep + 10 seconds
        int rand = 10 + (int) Math.round(Math.random() * 10);
        Thread.sleep(rand * 1000);

        //Read remote date
        message("Reading remote time");
        Date remoteDate = new NTPClient().getTime();
        Calendar remoteCalendar = new GregorianCalendar();
        remoteCalendar.setTime(remoteDate);

        //Compute the delay
        message("Computing delay");

        int remoteTimeInSeconds = remoteCalendar.get(Calendar.SECOND);
        message("Remote time in sec = " + remoteTimeInSeconds);
        int delay = (60 - remoteTimeInSeconds);
        message("Delay = " + delay + "  s");

        //Construct and use a TimerTask and Timer.
        TimerTask testSync = new TestSync();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(testSync, delay * 1000, TASK_INTERVAL * 1000);
        message("Timer scheduled");
    }

    private static void message(String msg) {
        System.out.println(getIdString() + msg);
    }

    private static String getIdString() {
        int now = (int) (System.currentTimeMillis() / 1000);
        int secondsFromStart = now - startTime;
        return id.substring(id.lastIndexOf("-") + 10) + " , t=" + secondsFromStart + "     - ";
    }

    private static void init() {
        Utils.loadProperties("settings.properties");
        //feed = new BitcoinaveragePriceFeed();
        String folderName = "tests_" + System.currentTimeMillis() + "/";
        String logsFolder = Global.settings.getProperty("log_path") + folderName;
        try {
            NuLogger.setup(false, logsFolder);
        } catch (IOException ex) {
            LOG.severe(ex.toString());
        }
        LOG.setLevel(Level.INFO);

        LOG.info("Set up SSL certificates");
        System.setProperty("javax.net.ssl.trustStore", Global.settings.getProperty("keystore_path"));
        System.setProperty("javax.net.ssl.trustStorePassword", Global.settings.getProperty("keystore_pass"));

    }

    @Override
    public void run() {
        //Send hipchat notification
        message("Run");

        HipChatNotifications.sendMessage(getIdString() + " test price reading : 1BTC =" + readPrice()
                + "$ ", com.nubits.nubot.notifications.jhipchat.messages.Message.Color.RED);

        //Add a random sleep after the notification to see if the keep sync

        int rand = (int) Math.round(Math.random() * TASK_MAX_EXECUTION_INTERVAL);
        try {
            Thread.sleep(rand * 1000);
        } catch (InterruptedException ex) {
            LOG.severe(ex.getMessage());
        }

    }

    private double readPrice() {

        String mainFeed = PriceFeedManager.BTCE;

        ArrayList<String> backupFeedList = new ArrayList<>();

        backupFeedList.add(PriceFeedManager.BITCOINAVERAGE);
        backupFeedList.add(PriceFeedManager.BLOCKCHAIN);
        backupFeedList.add(PriceFeedManager.COINBASE);

        PriceFeedManager pfm = new PriceFeedManager(mainFeed, backupFeedList, pair);

        ArrayList<LastPrice> priceList = pfm.getLastPrices().getPrices();

        return priceList.get(0).getPrice().getQuantity();

    }
}
