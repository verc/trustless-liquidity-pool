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

import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;
import com.nubits.nubot.pricefeeds.AbstractPriceFeed;
import com.nubits.nubot.pricefeeds.BitstampEURPriceFeed;
import com.nubits.nubot.pricefeeds.PriceFeedManager;
import com.nubits.nubot.pricefeeds.PriceFeedManager.LastPriceResponse;
import com.nubits.nubot.utils.Utils;
import com.nubits.nubot.utils.logging.NuLogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class TestPriceFeed {

    private static final Logger LOG = Logger.getLogger(TestPriceFeed.class.getName());
    AbstractPriceFeed feed;
    CurrencyPair pair;

    public static void main(String a[]) {
        TestPriceFeed test = new TestPriceFeed();
        test.init();

        test.pair = Constant.BTC_USD;

        //test.executeSingle();
        test.execute();
        //test.executePPC();
    }

    private void init() {
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

        feed = new BitstampEURPriceFeed(); //REPLACE HERE

        LOG.info("Set up SSL certificates");
        System.setProperty("javax.net.ssl.trustStore", Global.settings.getProperty("keystore_path"));
        System.setProperty("javax.net.ssl.trustStorePassword", Global.settings.getProperty("keystore_pass"));

    }

    private void executeSingle() {
        LastPrice lastPrice = feed.getLastPrice(pair);
        if (!lastPrice.isError()) {
            LOG.info(lastPrice.toString());
        } else {
            //handle error
            LOG.severe("There was a problem while updating the price");
        }
    }

    private void execute() {

        String mainFeed = PriceFeedManager.BTCE;

        ArrayList<String> backupFeedList = new ArrayList<>();

        backupFeedList.add(PriceFeedManager.BITCOINAVERAGE);
        backupFeedList.add(PriceFeedManager.BLOCKCHAIN);
        backupFeedList.add(PriceFeedManager.COINBASE);
        backupFeedList.add(PriceFeedManager.BTER);
        backupFeedList.add(PriceFeedManager.CCEDK);
        backupFeedList.add(PriceFeedManager.BITSTAMP);
        backupFeedList.add(PriceFeedManager.BITFINEX);

        PriceFeedManager pfm = new PriceFeedManager(mainFeed, backupFeedList, pair);

        LastPriceResponse lpr = pfm.getLastPrices();

        ArrayList<LastPrice> priceList = pfm.getLastPrices().getPrices();

        LOG.info("CheckLastPrice received values from remote feeds. ");

        LOG.info("Positive response from " + priceList.size() + "/" + pfm.getFeedList().size() + " feeds");
        for (int i = 0; i < priceList.size(); i++) {
            LastPrice tempPrice = priceList.get(i);
            LOG.info(tempPrice.getSource() + ":1 " + tempPrice.getCurrencyMeasured().getCode() + " = "
                    + tempPrice.getPrice().getQuantity() + " " + tempPrice.getPrice().getCurrency().getCode());
        }
    }

    private void executePPC() {

        String mainFeed = PriceFeedManager.BTCE;

        ArrayList<String> backupFeedList = new ArrayList<>();


        backupFeedList.add(PriceFeedManager.COINMARKETCAP_NO);
        backupFeedList.add(PriceFeedManager.COINMARKETCAP_NE);


        PriceFeedManager pfm = new PriceFeedManager(mainFeed, backupFeedList, pair);

        ArrayList<LastPrice> priceList = pfm.getLastPrices().getPrices();

        LOG.info("CheckLastPrice received values from remote feeds. ");

        LOG.info("Positive response from " + priceList.size() + "/" + pfm.getFeedList().size() + " feeds");
        for (int i = 0; i < priceList.size(); i++) {
            LastPrice tempPrice = priceList.get(i);
            LOG.info(tempPrice.getSource() + ":1 " + tempPrice.getCurrencyMeasured().getCode() + " = "
                    + tempPrice.getPrice().getQuantity() + " " + tempPrice.getPrice().getCurrency().getCode());
        }
    }
}
