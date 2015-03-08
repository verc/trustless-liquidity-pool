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
package com.nubits.nubot.launch;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.options.OptionsJSON;
import com.nubits.nubot.pricefeeds.PriceFeedManager;
import com.nubits.nubot.tasks.NuPriceMonitorTask;
import com.nubits.nubot.tasks.TaskManager;
import com.nubits.nubot.utils.FileSystem;
import com.nubits.nubot.utils.Utils;
import com.nubits.nubot.utils.logging.NuLogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.simple.parser.JSONParser;

public class NuPriceMonitor {

    private static final Logger LOG = Logger.getLogger(NuPriceMonitor.class.getName());
    private static final String USAGE_STRING = "java  -jar NuPriceMonitor <path/to/options.json>";
    public static final String HEADER = "timestamp,source,crypto,price,currency,sellprice,sellpricedoubleside,buyprice,otherfeeds\n";
    private static Thread mainThread;
    //Options from json
    int refreshTime;
    double tx_fee, increment, wallchangeTreshold, priceOffset, distanceTreshold;
    boolean emails;
    String recipient;
    String outputPath, mainFeed;
    CurrencyPair pair;
    ArrayList<String> backupFeedNames;
    private String optionsPath;

    public static void main(String[] args) {
        mainThread = Thread.currentThread();
        //Load settings
        Utils.loadProperties("settings.properties");

        String folderName = "NuPriceMonitor_"+System.currentTimeMillis()+"/";
        String logsFolder = Global.settings.getProperty("log_path")+folderName;
        //Create log dir
        FileSystem.mkdir(logsFolder);
        try {
            NuLogger.setup(false,logsFolder);
        } catch (IOException ex) {
            LOG.severe(ex.toString());
        }

        NuPriceMonitor app = new NuPriceMonitor();
        if (app.readParams(args)) {
            createShutDownHook();
            app.init();
            LOG.fine("Launching NuCheckPrice ");
            app.exec();
        } else {
            System.exit(0);
        }
    }

    private void exec() {
        //Creating options
        if (readOptions()) {
            init();

            PriceFeedManager pfm = new PriceFeedManager(mainFeed, backupFeedNames, pair);

            //Then set the pfm
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setPriceFeedManager(pfm);

            //Set the priceDistance threshold
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setDistanceTreshold(distanceTreshold);

            //Set the priceDistance threshold
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setWallchangeThreshold(wallchangeTreshold);

            //Set the priceDistance threshold
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setSendEmails(emails);

            //Set the priceDistance threshold
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setRecipient(recipient);

            //Compute the buy/sell prices in USD
            double sellPriceUSDdoubleside = 1 + (0.01 * tx_fee);
            double sellPriceUSDsellside = sellPriceUSDdoubleside + increment;
            double buyPriceUSD = 1 - (0.01 * tx_fee);

            //Add(remove) the offset % from prices
            sellPriceUSDdoubleside = sellPriceUSDdoubleside + ((sellPriceUSDdoubleside / 100) * priceOffset);
            sellPriceUSDsellside = sellPriceUSDsellside + ((sellPriceUSDsellside / 100) * priceOffset);
            buyPriceUSD = buyPriceUSD - ((buyPriceUSD / 100) * priceOffset);

            LOG.fine("Computing USD pegs with offset " + priceOffset + "% (sell-side custodian) : sell @ " + sellPriceUSDsellside);
            LOG.fine("Computing USD pegs with offset " + priceOffset + "% (dual-side custodian) : sell @ " + sellPriceUSDdoubleside + " buy @ " + buyPriceUSD);


            //Set the prices in USD
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setSellPriceUSDdoubleside(sellPriceUSDdoubleside);
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setSellPriceUSDsingleside(sellPriceUSDsellside);
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setBuyPriceUSD(buyPriceUSD);


            //Set output for csv
            ((NuPriceMonitorTask) (Global.taskManager.getPriceMonitorTask().getTask())).setOutputPath(outputPath);


            //Write the header for Bens csv
            FileSystem.writeToFile(HEADER, outputPath, false);

            //set the interval from options
            Global.taskManager.getPriceMonitorTask().setInterval(refreshTime);

            //then start the thread
            Global.taskManager.getPriceMonitorTask().start(2);

        } else {
            LOG.severe("Problem while reading options from " + optionsPath);
            System.exit(0);
        }



    }

    private boolean readOptions() {
        boolean ok = false;
        OptionsJSON options = null;
        JSONParser parser = new JSONParser();
        String optionsString = FileSystem.readFromFile(optionsPath);
        try {
            org.json.JSONObject jsonString = new org.json.JSONObject(optionsString);
            org.json.JSONObject optionsJSON = (org.json.JSONObject) jsonString.get("options");


            String cp = (String) optionsJSON.get("pair");
            pair = CurrencyPair.getCurrencyPairFromString(cp, "_");
            outputPath = (String) optionsJSON.get("output-path");
            mainFeed = (String) optionsJSON.get("main-feed");


            backupFeedNames = new ArrayList<>();
            org.json.JSONObject dataJson = (org.json.JSONObject) optionsJSON.get("backup-feeds");

            //Iterate on backupFeeds

            String names[] = org.json.JSONObject.getNames(dataJson);
            if (names.length < 2) {
                LOG.severe("The bot requires at least two backup data feeds to run");
                System.exit(0);
            }
            for (int i = 0; i < names.length; i++) {
                try {
                    org.json.JSONObject tempJson = dataJson.getJSONObject(names[i]);
                    backupFeedNames.add((String) tempJson.get("name"));
                } catch (JSONException ex) {
                    LOG.severe(ex.toString());
                    System.exit(0);
                }
            }


            tx_fee = new Double((optionsJSON.get("tx-fee")).toString());
            increment = new Double((optionsJSON.get("increment")).toString());
            wallchangeTreshold = new Double((optionsJSON.get("wallchange-treshold")).toString());
            priceOffset = new Double((optionsJSON.get("price-offset")).toString());

            distanceTreshold = new Double((optionsJSON.get("price-distance-threshold")).toString());
            refreshTime = new Integer((optionsJSON.get("refresh-time")).toString());

            emails = (boolean) optionsJSON.get("emails");
            recipient = (String) optionsJSON.get("recipient");


            ok = true;
        } catch (JSONException | NumberFormatException ex) {
            LOG.severe(ex.toString());
        }
        return ok;
    }

    private boolean readParams(String[] args) {
        boolean ok = false;

        if (args.length != 1) {
            LOG.severe("wrong argument number : call it with \n" + USAGE_STRING);
            System.exit(0);
        }


        optionsPath = args[0];


        ok = true;
        return ok;
    }

    private static void createShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                LOG.fine("Exiting...");
                mainThread.interrupt();
                Global.taskManager.stopAll();

                Thread.currentThread().interrupt();
                return;
            }
        }));
    }

    private void init() {
        System.setProperty("javax.net.ssl.trustStore", Global.settings.getProperty("keystore_path"));
        System.setProperty("javax.net.ssl.trustStorePassword", Global.settings.getProperty("keystore_pass"));

        Global.taskManager = new TaskManager();

    }
}
