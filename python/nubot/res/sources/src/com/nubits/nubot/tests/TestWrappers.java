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

import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.exchanges.ExchangeLiveData;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.global.Passwords;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.Balance;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.Order;
import com.nubits.nubot.models.Trade;
import com.nubits.nubot.options.OptionsJSON;
import com.nubits.nubot.tasks.TaskManager;
import com.nubits.nubot.trading.Ticker;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.trading.wrappers.*;
import com.nubits.nubot.utils.FileSystem;
import com.nubits.nubot.utils.Utils;
import com.nubits.nubot.utils.logging.NuLogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class TestWrappers {

    private static final Logger LOG = Logger.getLogger(TestWrappers.class.getName());
    /**
     * Configure tests
     */
    private static final String TEST_OPTIONS_PATH = "res/options/private/old/options-full.json";
    //private static final String TEST_OPTIONS_PATH = "options.json";
    public static final String testExchange = Constant.BTER;
    public static final CurrencyPair testPair = Constant.NBT_BTC;
    public static final Currency testCurrency = Constant.NBT;

    public static void main(String[] args) {
        //Load settings
        Utils.loadProperties("settings.properties");
        init();
        String[] inputs = new String[1];
        inputs[0] = TEST_OPTIONS_PATH;
        Global.options = OptionsJSON.parseOptions(inputs);

        configExchange(testExchange); //Replace to test a different API implementation

        runTests();
        System.exit(0);
    }

    public static void runTests() {
        //Methods strictly necessary for NuBot to run-------------
        //-------------
        //testGetAvailableBalance(testCurrency);
        testGetAvailableBalances(testPair);
        //testGetActiveOrders(testPair);
        //testGetActiveOrders(); //Try with 0 active orders also . for buy orders, check in which currency is the amount returned.
        //testClearAllOrders(Constant.NBT_BTC);
        //testGetAvailableBalances(testPair);
        //testSell(0.3, 0.00830509, Constant.NBT_BTC);  //ok
        //testBuy(0.003, 0.0000120, Constant.NBT_BTC);  //ok
        //testGetActiveOrders();
        //testCancelOrder("1139", Constant.NBT_BTC);
        //testClearAllOrders(Constant.NBT_BTC);
        //testSell(1, 0.1830509, testPair);  //ok
        //testBuy(0.0000120, 0.0000120, testPair);  //ok
        //testGetActiveOrders();
        //testCancelOrder("2063803", testPair);
        //testClearAllOrders(testPair);
        //testGetOrderDetail("1139");
        //testIsOrderActive("1139");
        //testGetTxFee();
        //testGetTxFeeWithArgs(testPair);
        //Methods NOT strictly necessary for NuBot to run---------------
        //---------------
        //testGetLastPrice(testPair);
        //testGetLastTrades(testPair, 1388534400);
        //testGetLastTrades(testPair);

        testStressClearAllorders();


        //for (int i = 0; i < 5000; i++) {
        //   ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders(Global.options.getPair());
        //    if (activeOrdersResponse.isPositive()) {
        //        LOG.info("Active orders : " + activeOrdersResponse.getResponseObject());
        //    } else {
        //        LOG.severe(activeOrdersResponse.getError().toString());
        //    }
        //}
        //stimulating ccedk wrong nonce

        /*
         for (int i = 0; i < 60; i++) {
         try {
         String htmlString = Utils.getHTML("https://www.ccedk.com/api/v1/currency/list?nonce=1234567891", false);
         LOG.warning(htmlString);
         } catch (IOException io) {
         LOG.severe(io.toString());
         }
         }
         */

        /* stress test start
         for (int i = 0; i < 5000; i++) {
         testGetActiveOrders();
         try {
         Thread.sleep(100);
         } catch (InterruptedException ex) {
         LOG.severe(ex.toString());
         }

         testGetAvailableBalances(Constant.NBT_BTC);

         try {
         Thread.sleep(100);
         } catch (InterruptedException ex) {
         LOG.severe(ex.toString());
         }
         testGetOrderDetail("3454");

         try {
         Thread.sleep(300);
         } catch (InterruptedException ex) {
         LOG.severe(ex.toString());
         }
         }
         * Stress test stop*/
    }

    private static void testStressClearAllorders() {
        //clear old orders if any
        testClearAllOrders(testPair);


        // place a few orders
        for (int i = 0; i <= 5; i++) {
            testSell(0.1, 0.004, testPair);
            try {
                Thread.sleep(400);
            } catch (InterruptedException ex) {
                LOG.severe(ex.toString());
            }
        }

        for (int i = 0; i <= 5; i++) {
            testBuy(0.1, 0.001, testPair);
            try {
                Thread.sleep(400);
            } catch (InterruptedException ex) {
                LOG.severe(ex.toString());
            }
        }


        //Wait 4 secs
        try {
            Thread.sleep(4000);
        } catch (InterruptedException ex) {
            LOG.severe(ex.toString());
        }

        //try to clear orders
        testClearAllOrders(testPair);
    }

    private static void testGetAvailableBalances(CurrencyPair pair) {
        //Get all the balances  associated with the account
        ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalances(pair);
        if (balancesResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getBalance() ");
            Balance balance = (Balance) balancesResponse.getResponseObject();

            LOG.info(balance.toString());

        } else {
            LOG.severe(balancesResponse.getError().toString());
        }
    }

    private static void testGetAvailableBalance(Currency cur) {
        //Get the USD balance associated with the account
        ApiResponse balanceResponse = Global.exchange.getTrade().getAvailableBalance(cur);
        if (balanceResponse.isPositive()) {
            LOG.info("Positive response from TradeInterface.getBalance(CurrencyPair pair) ");
            Amount balance = (Amount) balanceResponse.getResponseObject();

            LOG.info(balance.toString());
        } else {
            LOG.severe(balanceResponse.getError().toString());
        }
    }

    private static void testGetLastPrice(CurrencyPair pair) {
        //Get lastPrice for a given CurrencyPair
        ApiResponse lastPriceResponse = Global.exchange.getTrade().getLastPrice(pair);
        if (lastPriceResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getLastPrice(CurrencyPair pair) ");
            Ticker ticker = (Ticker) lastPriceResponse.getResponseObject();
            LOG.info("Last price : 1 " + testPair.getOrderCurrency().getCode() + " = "
                    + ticker.getLast() + " " + testPair.getPaymentCurrency().getCode());
            LOG.info("ask  : 1 " + testPair.getOrderCurrency().getCode() + " = "
                    + ticker.getAsk() + " " + testPair.getPaymentCurrency().getCode());
            LOG.info("bid  : 1 " + testPair.getOrderCurrency().getCode() + " = "
                    + ticker.getBid() + " " + testPair.getPaymentCurrency().getCode());

        } else {
            LOG.severe(lastPriceResponse.getError().toString());
        }

    }

    private static void testSell(double amountSell, double priceSell, CurrencyPair pair) {
        //Place a sell order


        ApiResponse sellResponse = Global.exchange.getTrade().sell(pair, amountSell, priceSell);
        if (sellResponse.isPositive()) {

            LOG.info("\nPositive response  from TradeInterface.sell(...) ");
            LOG.warning("Strategy : Submit order : "
                    + "sell" + amountSell + " " + pair.getOrderCurrency().getCode()
                    + " @ " + priceSell + " " + pair.getPaymentCurrency().getCode());

            String sellResponseString = (String) sellResponse.getResponseObject();
            LOG.info("Response = " + sellResponseString);
        } else {
            LOG.severe(sellResponse.getError().toString());
        }
    }

    private static void testBuy(double amountBuy, double priceBuy, CurrencyPair pair) {
        //Place a buy order

        ApiResponse buyResponse = Global.exchange.getTrade().buy(pair, amountBuy, priceBuy);
        if (buyResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.buy(...) ");
            LOG.info(": Submit order : "
                    + "buy" + amountBuy + " " + pair.getOrderCurrency().getCode()
                    + " @ " + priceBuy + " " + pair.getPaymentCurrency().getCode());
            String buyResponseString = (String) buyResponse.getResponseObject();
            LOG.info("Response = " + buyResponseString);

        } else {
            LOG.severe(buyResponse.getError().toString());
        }
    }

    private static void testGetActiveOrders() {
        //Get active orders
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders();
        if (activeOrdersResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getActiveOrders() ");
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();

            LOG.info("Active orders : " + orderList.size());
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                LOG.info(tempOrder.toString());
            }

        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
        }
    }

    private static void testGetActiveOrders(CurrencyPair pair) {
        //Get active orders associated with a specific CurrencyPair
        ApiResponse activeOrdersUSDNTBResponse = Global.exchange.getTrade().getActiveOrders(pair);
        if (activeOrdersUSDNTBResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getActiveOrders(CurrencyPair pair) ");
            ArrayList<Order> orderListUSDNBT = (ArrayList<Order>) activeOrdersUSDNTBResponse.getResponseObject();

            LOG.info("Active orders : " + orderListUSDNBT.size());
            for (int i = 0; i < orderListUSDNBT.size(); i++) {
                Order tempOrder = orderListUSDNBT.get(i);
                LOG.info(tempOrder.toString());
            }
        } else {
            LOG.severe(activeOrdersUSDNTBResponse.getError().toString());
        }
    }

    private static void testGetOrderDetail(String order_id_detail) {
        //Get the order details for a specific order_id
        ApiResponse orderDetailResponse = Global.exchange.getTrade().getOrderDetail(order_id_detail);
        if (orderDetailResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getOrderDetail(id) ");
            Order order = (Order) orderDetailResponse.getResponseObject();
            LOG.info(order.toString());
        } else {
            LOG.info(orderDetailResponse.getError().toString());
        }
    }

    private static void testCancelOrder(String order_id_delete, CurrencyPair pair) {
        //Cancel an order
        ApiResponse deleteOrderResponse = Global.exchange.getTrade().cancelOrder(order_id_delete, pair);
        if (deleteOrderResponse.isPositive()) {
            boolean deleted = (boolean) deleteOrderResponse.getResponseObject();

            if (deleted) {
                LOG.info("Order deleted succesfully");
            } else {
                LOG.info("Could not delete order");
            }

        } else {
            LOG.severe(deleteOrderResponse.getError().toString());
        }
    }

    private static void testGetTxFee() {
        //Get current trascation fee
        ApiResponse txFeeResponse = Global.exchange.getTrade().getTxFee();
        if (txFeeResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getTxFee()");
            double txFee = (Double) txFeeResponse.getResponseObject();
            LOG.info("Trasaction fee = " + txFee + "%");
        } else {
            LOG.severe(txFeeResponse.getError().toString());
        }
    }

    private static void testGetTxFeeWithArgs(CurrencyPair pair) {
        //Get the current transaction fee associated with a specific CurrencyPair
        ApiResponse txFeeNTBUSDResponse = Global.exchange.getTrade().getTxFee(pair);
        if (txFeeNTBUSDResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getTxFee(CurrencyPair pair)");
            double txFeeUSDNTB = (Double) txFeeNTBUSDResponse.getResponseObject();
            LOG.info("Trasaction fee = " + txFeeUSDNTB + "%");
        } else {
            LOG.severe(txFeeNTBUSDResponse.getError().toString());
        }
    }

    private static void testIsOrderActive(String orderId) {
        //Check if orderId is active
        ApiResponse orderDetailResponse = Global.exchange.getTrade().isOrderActive(orderId);
        if (orderDetailResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.isOrderActive(id) ");
            boolean exist = (boolean) orderDetailResponse.getResponseObject();
            LOG.info("Order " + orderId + "  active? " + exist);
        } else {
            LOG.severe(orderDetailResponse.getError().toString());
        }
    }

    private static void testClearAllOrders(CurrencyPair pair) {
        ApiResponse deleteOrdersResponse = Global.exchange.getTrade().clearOrders(pair);
        if (deleteOrdersResponse.isPositive()) {
            boolean deleted = (boolean) deleteOrdersResponse.getResponseObject();

            if (deleted) {
                LOG.info("Order clear request succesfully");
            } else {
                LOG.info("Could not submit request to clear orders");
            }

        } else {
            LOG.severe(deleteOrdersResponse.getError().toString());
        }
    }

    private static void testGetLastTrades(CurrencyPair pair) {
        //Get active orders
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getLastTrades(pair);
        if (activeOrdersResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getLastTrades(pair) ");
            ArrayList<Trade> tradeList = (ArrayList<Trade>) activeOrdersResponse.getResponseObject();
            LOG.info("Last 24h trades : " + tradeList.size());
            for (int i = 0; i < tradeList.size(); i++) {
                Trade tempTrade = tradeList.get(i);
                LOG.info(tempTrade.toString());
            }
        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
        }
    }

    private static void testGetLastTrades(CurrencyPair pair, long startTime) {
        //Get active orders
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getLastTrades(pair, startTime);
        if (activeOrdersResponse.isPositive()) {
            LOG.info("\nPositive response  from TradeInterface.getLastTrades(pair,startTime) ");
            ArrayList<Trade> tradeList = (ArrayList<Trade>) activeOrdersResponse.getResponseObject();
            LOG.info("Last trades from " + startTime + " : " + tradeList.size());
            for (int i = 0; i < tradeList.size(); i++) {
                Trade tempTrade = tradeList.get(i);
                LOG.info(tempTrade.toString());
            }
        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
        }
    }

    private static void init() {
        String folderName = "testwrappers_" + System.currentTimeMillis() + "/";
        String logsFolder = Global.settings.getProperty("log_path") + folderName;
        //Create log dir
        FileSystem.mkdir(logsFolder);
        try {
            NuLogger.setup(false, logsFolder);
        } catch (IOException ex) {
            LOG.severe(ex.toString());
        }

        System.setProperty("javax.net.ssl.trustStore", Global.settings.getProperty("keystore_path"));
        System.setProperty("javax.net.ssl.trustStorePassword", Global.settings.getProperty("keystore_pass"));
    }

    public static void configExchange(String exchangeName) {
        ApiKeys keys;

        Global.exchange = new Exchange(exchangeName);

        //Create e ExchangeLiveData object to accomodate liveData from the Global.exchange
        ExchangeLiveData liveData = new ExchangeLiveData();
        Global.exchange.setLiveData(liveData);

        Global.options.setExchangeName(exchangeName);

        if (exchangeName.equals(Constant.BTCE)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.BTCE_SECRET, Passwords.BTCE_KEY);
            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange

            Global.exchange.setTrade(new BtceWrapper(keys, Global.exchange));

        } else if (exchangeName.equals(Constant.INTERNAL_EXCHANGE_PEATIO)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.INTERNAL_PEATIO_SECRET, Passwords.INTERNAL_PEATIO_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new PeatioWrapper(keys, Global.exchange, Constant.INTERNAL_EXCHANGE_PEATIO_API_BASE));
        } else if (exchangeName.equals(Constant.CCEDK)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.CCEDK_SECRET, Passwords.CCEDK_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new CcedkWrapper(keys, Global.exchange));
        } else if (exchangeName.equals(Constant.BTER)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.BTER_SECRET, Passwords.BTER_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new BterWrapper(keys, Global.exchange));
        } else if (exchangeName.equals(Constant.POLONIEX)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.POLONIEX_SECRET, Passwords.POLONIEX_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new PoloniexWrapper(keys, Global.exchange));
        } else if (exchangeName.equals(Constant.CCEX)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.CCEX_SECRET, "");

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new CcexWrapper(keys, Global.exchange));
        } else if (exchangeName.equals(Constant.ALLCOIN)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.ALLCOIN_SECRET, Passwords.ALLCOIN_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new AllCoinWrapper(keys, Global.exchange));
        } else if (exchangeName.equals(Constant.BITSPARK_PEATIO)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.BITSPARK_SECRET, Passwords.BITSPARK_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new BitSparkWrapper(keys, Global.exchange));
        } else if (exchangeName.equals(Constant.EXCOIN)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.EXCOIN_SECRET, Passwords.EXCOIN_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new ExcoinWrapper(keys, Global.exchange));
        } else if (exchangeName.equals(Constant.BITCOINCOID)) {
            //Wrap the keys into a new ApiKeys object
            keys = new ApiKeys(Passwords.BITCOINCOID_SECRET, Passwords.BITCOINCOID_KEY);

            //Create a new TradeInterface object using the custom implementation
            //Assign the TradeInterface to the exchange
            Global.exchange.setTrade(new BitcoinCoIDWrapper(keys, Global.exchange));
        } else {
            LOG.severe("Exchange " + exchangeName + " not supported");
            System.exit(0);
        }

        Global.exchange.getLiveData().setUrlConnectionCheck(Global.exchange.getTrade().getUrlConnectionCheck());

        //Create a TaskManager and
        Global.taskManager = new TaskManager();
        //Start checking for connection
        Global.taskManager.getCheckConnectionTask().start();


        //Wait a couple of seconds for the connectionThread to get live
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            LOG.severe(ex.toString());
        }

        /* Setup (end) ------------------------------------------------------------------------------------------------------ */
    }
}
