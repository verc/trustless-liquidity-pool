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
package com.nubits.nubot.pricefeeds;

import com.nubits.nubot.global.Constant;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class PriceFeedManager {

    private static final Logger LOG = Logger.getLogger(PriceFeedManager.class.getName());
    private ArrayList<AbstractPriceFeed> feedList = new ArrayList<>();
    private CurrencyPair pair;
    public static HashMap<String, AbstractPriceFeed> FEED_NAMES_MAP;
    public final static String BLOCKCHAIN = "blockchain"; //BTC
    public final static String BITCOINAVERAGE = "bitcoinaverage"; //BTC
    public final static String COINBASE = "coinbase"; //BTC
    public final static String BITSTAMP = "bitstamp"; // BTC
    public final static String BITFINEX = "bitfinex"; // BTC
    public final static String BTER = "bter"; //BTC and PPC
    public final static String CCEDK = "ccedk"; //BTC and PPC
    public final static String BTCE = Constant.BTCE;
    public final static String COINMARKETCAP_NO = "coinmarketcap_no"; //PPC
    public final static String COINMARKETCAP_NE = "coinmarketcap_ne"; //PPC
    public final static String BITSTAMP_EURUSD = "bitstampeurusd"; // EUR
    public final static String GOOGLE_UNOFFICIAL = "google-unofficial"; // EUR CNY
    public final static String YAHOO = "yahoo"; //EUR CNY
    public final static String OPENEXCHANGERATES = "openexchangerates"; //EUR CNY
    public final static String EXCHANGERATELAB = "exchangeratelab"; // EUR CNY
    //When adding new feed here remember to also add to the hasmap below

    public PriceFeedManager(String mainFeed, ArrayList<String> backupFeedList, CurrencyPair pair) {
        initValidFeeds();
        this.pair = pair;

        feedList.add(createFeed(mainFeed)); //add the main feed at index 0

        for (int i = 0; i < backupFeedList.size(); i++) {
            feedList.add(createFeed(backupFeedList.get(i)));
        }
    }

    private void initValidFeeds() {
        FEED_NAMES_MAP = new HashMap<>();

        FEED_NAMES_MAP.put(BLOCKCHAIN, new BlockchainPriceFeed());
        FEED_NAMES_MAP.put(BITCOINAVERAGE, new BitcoinaveragePriceFeed());
        FEED_NAMES_MAP.put(COINBASE, new CoinbasePriceFeed());
        FEED_NAMES_MAP.put(BTER, new BterPriceFeed());
        FEED_NAMES_MAP.put(CCEDK, new CcedkPriceFeed());
        FEED_NAMES_MAP.put(BTCE, new BtcePriceFeed());
        FEED_NAMES_MAP.put(COINMARKETCAP_NO, new CoinmarketcapnorthpolePriceFeed());
        FEED_NAMES_MAP.put(COINMARKETCAP_NE, new CoinmarketcapnexuistPriceFeed());
        FEED_NAMES_MAP.put(BITSTAMP, new BitstampPriceFeed());
        FEED_NAMES_MAP.put(BITSTAMP_EURUSD, new BitstampEURPriceFeed());
        FEED_NAMES_MAP.put(GOOGLE_UNOFFICIAL, new GoogleUnofficialPriceFeed());
        FEED_NAMES_MAP.put(YAHOO, new YahooPriceFeed());
        FEED_NAMES_MAP.put(OPENEXCHANGERATES, new OpenexchangeratesPriceFeed());
        FEED_NAMES_MAP.put(EXCHANGERATELAB, new ExchangeratelabPriceFeed());
        FEED_NAMES_MAP.put(BITFINEX, new BitfinexPriceFeed());

    }

    public LastPriceResponse getLastPrices() {
        LastPriceResponse response = new LastPriceResponse();
        boolean isMainFeedValid = false;
        ArrayList<LastPrice> prices = new ArrayList<>();
        for (int i = 0; i < feedList.size(); i++) {
            AbstractPriceFeed tempFeed = feedList.get(i);

            LastPrice lastPrice = tempFeed.getLastPrice(pair);
            if (lastPrice != null) {
                if (!lastPrice.isError()) {
                    prices.add(lastPrice);
                    if (i == 0) {
                        isMainFeedValid = true;
                    }
                } else {
                    LOG.warning("Error while updating " + pair.getOrderCurrency().getCode() + ""
                            + " price from " + tempFeed.name);
                }
            } else {
                LOG.warning("Error (null) while updating " + pair.getOrderCurrency().getCode() + ""
                        + " price from " + tempFeed.name);
            }
        }
        response.setMainFeedValid(isMainFeedValid);
        response.setPrices(prices);
        return response;
    }

    public LastPrice getLastPrice() {
        boolean ok = false;

        for (int i = 0; i < feedList.size(); i++) {
            AbstractPriceFeed tempFeed = feedList.get(i);
            LastPrice lastPrice = tempFeed.getLastPrice(pair);
            if (!lastPrice.isError()) {
                LOG.fine("Got last price of 1" + pair.getOrderCurrency().getCode() + ""
                        + " from " + tempFeed.name + " : " + lastPrice.getPrice().getQuantity() + " " + lastPrice.getPrice().getCurrency().getCode());
                return lastPrice;
            } else {
                //handle error
                LOG.severe("Problem while updating the price on " + tempFeed.name);
            }
        }

        //None of them worked. Caution now
        return new LastPrice(true, "", pair.getOrderCurrency(), null);

    }

    private AbstractPriceFeed createFeed(String feedname) {
        if (FEED_NAMES_MAP.containsKey(feedname)) {
            return FEED_NAMES_MAP.get(feedname);
        } else {
            LOG.severe("Error wile adding price seed with name unrecognized : " + feedname);
            System.exit(0);
        }
        return null;//never reached

    }

    public ArrayList<AbstractPriceFeed> getFeedList() {
        return feedList;
    }

    public void setFeedList(ArrayList<AbstractPriceFeed> feedList) {
        this.feedList = feedList;
    }

    public CurrencyPair getPair() {
        return pair;
    }

    public void setPair(CurrencyPair pair) {
        this.pair = pair;
    }

//class to wrap results from getLastPrices
    public class LastPriceResponse {

        private boolean mainFeedValid;
        private ArrayList<LastPrice> prices;

        public LastPriceResponse() {
        }

        public boolean isMainFeedValid() {
            return mainFeedValid;
        }

        public void setMainFeedValid(boolean mainFeedValid) {
            this.mainFeedValid = mainFeedValid;
        }

        public ArrayList<LastPrice> getPrices() {
            return prices;
        }

        public void setPrices(ArrayList<LastPrice> prices) {
            this.prices = prices;
        }
    }
}
