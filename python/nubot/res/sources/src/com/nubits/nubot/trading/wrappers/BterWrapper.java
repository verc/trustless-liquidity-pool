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
package com.nubits.nubot.trading.wrappers;

import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.ApiError;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.Balance;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.Order;
import com.nubits.nubot.models.Trade;
import com.nubits.nubot.trading.ServiceInterface;
import com.nubits.nubot.trading.Ticker;
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.utils.ErrorManager;
import com.nubits.nubot.utils.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class BterWrapper implements TradeInterface {

    private static final Logger LOG = Logger.getLogger(BterWrapper.class.getName());
    //Class fields
    private ApiKeys keys;
    private Exchange exchange;
    private String checkConnectionUrl = "https://bter.com/";
    private final String SIGN_HASH_FUNCTION = "HmacSHA512";
    private final String ENCODING = "UTF-8";
    private final String API_BASE_URL = "https://bter.com/api/1/";
    private final String API_GET_INFO = "private/getfunds";
    private final String API_TRADE = "private/placeorder";
    private final String API_GET_TRADES = "private/mytrades";
    private final String API_ACTIVE_ORDERS = "private/orderlist";
    private final String API_ORDER = "private/getorder";
    private final String API_CANCEL_ORDER = "private/cancelorder";
    private final String API_GET_FEE = "http://data.bter.com/api/1/marketinfo";
    // Errors
    private ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "error";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";
    private final String TOKEN_ERROR_HTML_405 = "<title>405 Method Not Allowed</title>";

    public BterWrapper() {
        setupErrors();
    }

    public BterWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        setupErrors();
    }

    private void setupErrors() {
        errors.setExchangeName(exchange);
    }

    private ApiResponse getQuery(String url, HashMap<String, String> query_args, boolean isGet) {
        ApiResponse apiResponse = new ApiResponse();
        String queryResult = query(url, query_args, isGet);

        if (queryResult == null) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.noConnectionError);
            return apiResponse;
        }

        if (queryResult.contains(TOKEN_ERROR_HTML_405)) {
            ApiError error = errors.apiReturnError;
            error.setDescription("BTER returned http error 405 - method not allowed");
            apiResponse.setError(error);
            return apiResponse;
        }

        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            boolean valid;
            try {
                valid = Boolean.parseBoolean((String) httpAnswerJson.get("result"));
            } catch (ClassCastException e) {
                valid = true; //hack due to bter returning "false" and false at times, depending on the err
                try {
                    valid = (boolean) httpAnswerJson.get("result");
                } catch (ClassCastException ex) {
                    valid = true;
                }
            }

            if (!valid) {
                String errorMessage = "";
                if (httpAnswerJson.containsKey("message")) {
                    errorMessage = (String) httpAnswerJson.get("message");
                } else if (httpAnswerJson.containsKey("msg")) {
                    errorMessage = (String) httpAnswerJson.get("msg");
                }
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(errorMessage);
                apiResponse.setError(apiErr);
            } else {
                apiResponse.setResponseObject(httpAnswerJson);
            }
        } catch (ClassCastException cce) {
            //if casting to a JSON object failed, try a JSON Array
            try {
                JSONArray httpAnswerJson = (JSONArray) (parser.parse(queryResult));
                apiResponse.setResponseObject(httpAnswerJson);
            } catch (ParseException pe) {
                LOG.severe("httpResponse: " + queryResult + " \n" + pe.toString());
                apiResponse.setError(errors.parseError);
            }
        } catch (ParseException ex) {
            LOG.severe("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }
        return apiResponse;
    }

    @Override
    public ApiResponse getAvailableBalances(CurrencyPair pair) {
        return getBalanceImpl(null, pair);
    }

    @Override
    public ApiResponse getAvailableBalance(Currency currency) {
        return getBalanceImpl(currency, null);
    }

    private ApiResponse getBalanceImpl(Currency currency, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        Balance balance = new Balance();
        String url = API_BASE_URL + API_GET_INFO;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();

        ApiResponse response = getQuery(url, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            boolean somethingLocked = false;
            JSONObject lockedFundsJSON = null;
            JSONObject availableFundsJSON = (JSONObject) httpAnswerJson.get("available_funds");

            if (httpAnswerJson.containsKey("locked_funds")) {
                lockedFundsJSON = (JSONObject) httpAnswerJson.get("locked_funds");
                somethingLocked = true;
            }

            if (currency == null) { //Get all balances
                boolean foundNBTavail = false;
                boolean foundPEGavail = false;
                Amount NBTAvail = new Amount(0, pair.getOrderCurrency()),
                        PEGAvail = new Amount(0, pair.getPaymentCurrency());
                Amount PEGonOrder = new Amount(0, pair.getPaymentCurrency());
                Amount NBTonOrder = new Amount(0, pair.getOrderCurrency());
                String NBTcode = pair.getOrderCurrency().getCode().toUpperCase();
                String PEGcode = pair.getPaymentCurrency().getCode().toUpperCase();
                if (availableFundsJSON.containsKey(NBTcode)) {
                    double tempbalance = Double.parseDouble((String) availableFundsJSON.get(NBTcode));
                    NBTAvail = new Amount(tempbalance, pair.getOrderCurrency());
                    foundNBTavail = true;
                }
                if (availableFundsJSON.containsKey(PEGcode)) {
                    double tempbalance = Double.parseDouble((String) availableFundsJSON.get(PEGcode));
                    PEGAvail = new Amount(tempbalance, pair.getPaymentCurrency());
                    foundPEGavail = true;
                }
                if (somethingLocked) {
                    if (lockedFundsJSON.containsKey(NBTcode)) {
                        double tempbalance = Double.parseDouble((String) lockedFundsJSON.get(NBTcode));
                        NBTonOrder = new Amount(tempbalance, pair.getOrderCurrency());
                    }

                    if (lockedFundsJSON.containsKey(PEGcode)) {
                        double tempbalance = Double.parseDouble((String) lockedFundsJSON.get(PEGcode));
                        PEGonOrder = new Amount(tempbalance, pair.getOrderCurrency());
                    }
                }
                balance = new Balance(PEGAvail, NBTAvail, PEGonOrder, NBTonOrder);
                apiResponse.setResponseObject(balance);
                if (!foundNBTavail || !foundPEGavail) {
                    LOG.info("Cannot find a balance for currency with code "
                            + "" + NBTcode + " or " + PEGcode + " in your balance. "
                            + "NuBot assumes that balance is 0");
                }
            } else { //Get specific balance
                boolean found = false;
                Amount avail = new Amount(0, currency);
                String code = currency.getCode().toUpperCase();
                if (availableFundsJSON.containsKey(code)) {
                    double tempbalance = Double.parseDouble((String) availableFundsJSON.get(code));
                    avail = new Amount(tempbalance, currency);
                    found = true;
                }
                apiResponse.setResponseObject(avail);
                if (!found) {
                    LOG.warning("Cannot find a balance for currency with code "
                            + code + " in your balance. NuBot assumes that balance is 0");
                }
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getLastPrice(CurrencyPair pair) {
        return getLastPriceImpl(pair, false);
    }

    public ApiResponse getLastPriceFeed(CurrencyPair pair) { //used for BterPriceFeed only
        return getLastPriceImpl(pair, true);
    }

    private ApiResponse getLastPriceImpl(CurrencyPair pair, boolean bypass) {
        Ticker ticker = new Ticker();
        ApiResponse apiResponse = new ApiResponse();

        double last = -1;
        double ask = -1;
        double bid = -1;

        String ticker_url = API_BASE_URL + getTickerPath(pair);

        HashMap<String, String> query_args = new HashMap<>();

        /* Sample response
         * {"result":"true","last":2599,"high":2620,"low":2406,"avg":2526.11,"sell":2598,"buy":2578,"vol_btc":544.5027,"vol_cny":1375475.92}
         */

        String queryResult = "";
        if (bypass) { //used by BterPriceFeed only
            BterService query = new BterService(ticker_url, keys, query_args);
            queryResult = query.executeQuery(true, true);
        } else {
            queryResult = query(ticker_url, query_args, true);
        }

        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }

        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            boolean valid = true;
            try {
                valid = Boolean.parseBoolean((String) httpAnswerJson.get("result"));
            } catch (ClassCastException e) {
                valid = true;
            }

            if (!valid) {
                //error
                String errorMessage = (String) httpAnswerJson.get("message");
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(errorMessage);
                apiResponse.setError(apiErr);
                return apiResponse;
            } else {
                //correct

                last = Utils.getDouble(httpAnswerJson.get("last"));
                bid = Utils.getDouble(httpAnswerJson.get("sell"));
                ask = Utils.getDouble(httpAnswerJson.get("buy"));
            }
        } catch (ParseException ex) {
            LOG.severe("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }

        ticker.setAsk(ask);
        ticker.setBid(bid);
        ticker.setLast(last);
        apiResponse.setResponseObject(ticker);
        return apiResponse;

    }

    @Override
    public ApiResponse sell(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.SELL.toUpperCase(), pair, amount, rate);
    }

    @Override
    public ApiResponse buy(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.BUY.toUpperCase(), pair, amount, rate);
    }

    private ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double rate) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL + API_TRADE;
        boolean isGet = false;
        String order_id = "";
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("pair", pair.toString("_").toLowerCase());
        query_args.put("type", type);
        query_args.put("rate", Double.toString(rate));
        query_args.put("amount", Double.toString(amount));

        ApiResponse response = getQuery(url, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            order_id = "" + (long) httpAnswerJson.get("order_id");
            String msg = (String) httpAnswerJson.get("msg");
            if (!msg.equals("Success")) {
                //LOG.severe("BTER : Something went wrong while placing the order :" + msg);
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(msg);
                apiResponse.setError(apiErr);
                return apiResponse;
            } else {
                apiResponse.setResponseObject(order_id);
                return apiResponse;
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getActiveOrders() {
        return getActiveOrdersImpl(null);
    }

    @Override
    public ApiResponse getActiveOrders(CurrencyPair pair) {
        return getActiveOrdersImpl(pair);
    }

    public ApiResponse getActiveOrdersImpl(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL + API_ACTIVE_ORDERS;
        boolean isGet = false;
        ArrayList<Order> orderList = new ArrayList<Order>();

        HashMap<String, String> query_args = new HashMap<>();

        ApiResponse response = getQuery(url, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONArray orders;
            try {
                orders = (JSONArray) httpAnswerJson.get("orders");
            } catch (ClassCastException e) { //Empty order list?
                apiResponse.setResponseObject(orderList);
                return apiResponse;
            }

            for (int i = 0; i < orders.size(); i++) {
                JSONObject orderObject = (JSONObject) orders.get(i);
                Order tempOrder = parseOrder(orderObject);


                if (!tempOrder.isCompleted()) //Do not add executed orders
                {
                    //check if a specific currencypair is set
                    if (pair != null) {
                        if (tempOrder.getPair().equals(pair)) {
                            orderList.add(tempOrder);
                        }
                    } else {
                        orderList.add(tempOrder);
                    }
                }
            }
            apiResponse.setResponseObject(orderList);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getOrderDetail(String orderID) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL + API_ORDER;
        boolean isGet = false;

        String order_id = "";
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("order_id", orderID);

        ApiResponse response = getQuery(url, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            String msg = (String) httpAnswerJson.get("msg");
            if (!msg.equals("Success")) {
                //LOG.severe("BTER : Something went wrong while gettin the order :" + msg);
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(msg);
                apiResponse.setError(apiErr);
                return apiResponse;
            } else {
                JSONObject orderObject = (JSONObject) httpAnswerJson.get("order");
                Order order = parseOrder(orderObject);
                apiResponse.setResponseObject(order);
                return apiResponse;
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL + API_CANCEL_ORDER;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("order_id", orderID);

        ApiResponse response = getQuery(url, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            String msg = (String) httpAnswerJson.get("msg");
            if (!msg.equals("Success")) {
                LOG.severe("BTER : Something went wrong while deleting the order :" + msg);
                apiResponse.setResponseObject(false);
            } else {
                apiResponse.setResponseObject(true);
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getTxFee() {
        ApiError error = errors.genericError;
        error.setDescription("For Bter the fee changes with the currency. Please be more specific");
        return new ApiResponse(false, null, error);
    }

    @Override
    public ApiResponse getTxFee(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        double fee = 0;

        String url = API_GET_FEE;
        boolean isGet = true;

        HashMap<String, String> query_args = new HashMap<>();

        ApiResponse response = getQuery(url, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONArray array = (JSONArray) httpAnswerJson.get("pairs");

            String searchingFor = pair.toString("_").toLowerCase();

            for (int i = 0; i < array.size(); i++) {
                JSONObject tempObj = (JSONObject) array.get(i);
                if (tempObj.containsKey(searchingFor)) {
                    {
                        JSONObject tempObjCorrect = (JSONObject) tempObj.get(searchingFor);
                        fee = Double.parseDouble(tempObjCorrect.get("fee").toString());
                        apiResponse.setResponseObject(fee);
                        return apiResponse;
                    }
                }
            }
            //Not found
            ApiError err = errors.genericError;
            err.setDescription("Did not find fee for pair " + searchingFor);
            apiResponse.setError(err);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    public static String getTickerPath(CurrencyPair pair) {
        return "ticker/" + pair.toString("_").toLowerCase();
    }

    @Override
    public ApiResponse isOrderActive(String id) {
        ApiResponse apiResp = new ApiResponse();

        ApiResponse orderDetailResponse = Global.exchange.getTrade().getOrderDetail(id);
        if (orderDetailResponse.isPositive()) {
            Order order = (Order) orderDetailResponse.getResponseObject();
            if (order.isCompleted()) {
                apiResp.setResponseObject(false);
            } else {
                apiResp.setResponseObject(true);
            }
        } else { //in case of api error...
            //Distinguish between the "Is not valid" and another case of API err
            String errMessage = orderDetailResponse.getError().toString();
            String searching = "invalid order id";
            if (errMessage.contains(searching)) { //order does not exist therfore not active
                apiResp.setResponseObject(false);
            } else { //is an error
                apiResp = orderDetailResponse;
            }
        }
        return apiResp;
    }

    @Override
    public ApiResponse clearOrders(CurrencyPair pair) {
        //Since there is no API entry point for that, this call will iterate over actie
        ApiResponse toReturn = new ApiResponse();
        boolean ok = true;

        ApiResponse activeOrdersResponse = getActiveOrders();
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);

                //Wait to avoid placing requests too fast
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException ex) {
                    LOG.severe(ex.toString());
                }

                ApiResponse deleteOrderResponse = cancelOrder(tempOrder.getId(), null);
                if (deleteOrderResponse.isPositive()) {
                    boolean deleted = (boolean) deleteOrderResponse.getResponseObject();

                    if (deleted) {
                        LOG.warning("Order " + tempOrder.getId() + " deleted succesfully");
                    } else {
                        LOG.warning("Could not delete order " + tempOrder.getId() + "");
                        ok = false;
                    }

                } else {
                    LOG.severe(deleteOrderResponse.getError().toString());
                    toReturn.setError(deleteOrderResponse.getError());
                    return toReturn;
                }
            }
            //Wait to avoid placing requests too fast
            try {
                Thread.sleep(800);
            } catch (InterruptedException ex) {
                LOG.severe(ex.toString());
            }
            toReturn.setResponseObject(ok);
        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
            toReturn.setError(activeOrdersResponse.getError());
            return toReturn;
        }

        return toReturn;
    }

    @Override
    public ApiError getErrorByCode(int code) {
        return null;
    }

    @Override
    public String getUrlConnectionCheck() {
        return checkConnectionUrl;
    }

    @Override
    public String query(String url, HashMap<String, String> args, boolean isGet) {
        BterService query = new BterService(url, keys, args);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(true, isGet);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to bter");
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
    }

    @Override
    public String query(String base, String method, HashMap<String, String> args, boolean isGet) {
        throw new UnsupportedOperationException("Not supported yet.");

    }

    @Override
    public String query(String url, TreeMap<String, String> args, boolean isGet) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String query(String base, String method, TreeMap<String, String> args, boolean isGet) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setKeys(ApiKeys keys) {
        this.keys = keys;
    }

    @Override
    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void setApiBaseUrl(String apiBaseUrl) {
        throw new UnsupportedOperationException("Not supported yet.");

    }

    private Order parseOrder(JSONObject orderObject) {
        Order order = new Order();

        /*
         *id":"15088",
         "sell_type":"BTC",
         "buy_type":"LTC",
         "sell_amount":"0.39901357",
         "buy_amount":"12.0",
         "pair":"ltc_btc",
         "type":"buy",
         "rate":0.033251,
         "amount":"0.39901357",
         "initial_rate":0.033251,
         "initial_amount":"1"
         "status":"open"
         */

        order.setId((String) orderObject.get("id"));


        CurrencyPair cp = CurrencyPair.getCurrencyPairFromString((String) orderObject.get("pair"), "_");
        order.setPair(cp);

        order.setType(((String) orderObject.get("type")).toUpperCase());

        String amountString = "amount";
        if (order.getType().equals(Constant.BUY)) {
            amountString = "buy_amount";
        }
        order.setAmount(new Amount(Utils.getDouble(orderObject.get(amountString)), cp.getOrderCurrency()));
        order.setPrice(new Amount(Utils.getDouble(orderObject.get("rate")), cp.getPaymentCurrency()));

        String status = (String) orderObject.get("status");

        if (!status.equals("open")) {
            order.setCompleted(true);
        } else {
            order.setCompleted(false);
        }

        order.setInsertedDate(new Date()); //Not provided

        return order;
    }

    private Trade parseTrade(JSONObject orderObject) {
        Trade trade = new Trade();

        /*
         "id":"7942422"
         "orderid":"38100777"
         "pair":"ltc_btc"
         "type":"sell"
         "rate":"0.01719"
         "amount":"0.0588"
         "time":"06-12 02:49:11"
         "time_unix":"1402512551"
         */

        trade.setExchangeName(Constant.BTER);

        trade.setId((String) orderObject.get("id"));
        trade.setOrder_id((String) orderObject.get("orderid"));


        CurrencyPair cp = CurrencyPair.getCurrencyPairFromString((String) orderObject.get("pair"), "_");
        trade.setPair(cp);

        trade.setType(((String) orderObject.get("type")).toUpperCase());
        trade.setAmount(new Amount(Utils.getDouble(orderObject.get("amount")), cp.getOrderCurrency()));
        trade.setPrice(new Amount(Utils.getDouble(orderObject.get("rate")), cp.getPaymentCurrency()));
        trade.setFee(new Amount(0, cp.getPaymentCurrency())); //Not available from API

        long date = Long.parseLong(((String) orderObject.get("time_unix")) + "000");
        trade.setDate(new Date(date));

        return trade;
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL + API_GET_TRADES;
        boolean isGet = false;
        ArrayList<Trade> tradeList = new ArrayList<Trade>();

        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("pair", pair.toString("_").toLowerCase());

        ApiResponse response = getQuery(url, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONArray orders;
            try {
                orders = (JSONArray) httpAnswerJson.get("trades");
            } catch (ClassCastException e) { //Empty order list?
                apiResponse.setResponseObject(tradeList);
                return apiResponse;
            }

            for (int i = 0; i < orders.size(); i++) {
                JSONObject tradeObject = (JSONObject) orders.get(i);
                Trade tempTrade = parseTrade(tradeObject);
                tradeList.add(tempTrade);
            }
            apiResponse.setResponseObject(tradeList);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime) {
        LOG.warning("BTER only expose access to trades from 24 hours");
        return getLastTrades(pair);
    }

    private class BterService implements ServiceInterface {

        protected String base;
        protected String method;
        protected HashMap args;
        protected ApiKeys keys;
        protected String url;

        private BterService(String url, ApiKeys keys, HashMap<String, String> args) {
            //Used for ticker, does not require auth
            this.url = url;
            this.args = args;
            this.method = "";
            this.keys = keys;
        }

        @Override
        public String executeQuery(boolean needAuth, boolean isGet) {
            String answer = null;
            String signature = "";
            String post_data = "";

            List< NameValuePair> urlParameters = new ArrayList< NameValuePair>();

            for (Iterator< Map.Entry< String, String>> argumentIterator = args.entrySet().iterator(); argumentIterator.hasNext();) {

                Map.Entry< String, String> argument = argumentIterator.next();

                urlParameters.add(new BasicNameValuePair(argument.getKey().toString(), argument.getValue().toString()));

                if (post_data.length() > 0) {
                    post_data += "&";
                }

                post_data += argument.getKey() + "=" + argument.getValue();

            }

            signature = signRequest(keys.getPrivateKey(), post_data);

            // add header
            Header[] headers = new Header[3];
            headers[ 0] = new BasicHeader("Key", keys.getApiKey());
            headers[ 1] = new BasicHeader("Sign", signature);
            headers[ 2] = new BasicHeader("Content-type", "application/x-www-form-urlencoded");

            URL queryUrl;
            try {
                queryUrl = new URL(url);
            } catch (MalformedURLException ex) {
                LOG.severe(ex.toString());
                return null;
            }
            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = null;
            HttpGet get = null;
            HttpResponse response = null;


            try {
                if (!isGet) {
                    post = new HttpPost(url);
                    post.setEntity(new UrlEncodedFormEntity(urlParameters));
                    post.setHeaders(headers);
                    response = client.execute(post);
                } else {
                    get = new HttpGet(url);
                    get.setHeaders(headers);
                    response = client.execute(get);
                }
            } catch (NoRouteToHostException e) {
                if (!isGet) {
                    post.abort();
                } else {
                    get.abort();
                }
                LOG.severe(e.toString());
                return null;
            } catch (SocketException e) {
                if (!isGet) {
                    post.abort();
                } else {
                    get.abort();
                }
                LOG.severe(e.toString());
                return null;
            } catch (Exception e) {
                if (!isGet) {
                    post.abort();
                } else {
                    get.abort();
                }
                LOG.severe(e.toString());
                return null;
            }
            BufferedReader rd;


            try {
                rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));


                StringBuffer buffer = new StringBuffer();
                String line = "";
                while ((line = rd.readLine()) != null) {
                    buffer.append(line);
                }

                answer = buffer.toString();
            } catch (IOException ex) {

                LOG.severe(ex.toString());
                return null;
            } catch (IllegalStateException ex) {

                LOG.severe(ex.toString());
                return null;
            }
            if (Global.options
                    != null && Global.options.isVerbose()) {

                LOG.fine("\nSending request to URL : " + url + " ; get = " + isGet);
                if (post != null) {
                    System.out.println("Post parameters : " + post.getEntity());
                }
                LOG.fine("Response Code : " + response.getStatusLine().getStatusCode());
                LOG.fine("Response :" + response);

            }
            return answer;
        }

        @Override
        public String signRequest(String secret, String hash_data) {
            String sign = "";
            try {
                Mac mac = null;
                SecretKeySpec key = null;
                // Create a new secret key
                try {
                    key = new SecretKeySpec(secret.getBytes(ENCODING), SIGN_HASH_FUNCTION);
                } catch (UnsupportedEncodingException uee) {
                    LOG.severe("Unsupported encoding exception: " + uee.toString());
                }

                // Create a new mac
                try {
                    mac = Mac.getInstance(SIGN_HASH_FUNCTION);
                } catch (NoSuchAlgorithmException nsae) {
                    LOG.severe("No such algorithm exception: " + nsae.toString());
                }

                // Init mac with key.
                try {
                    mac.init(key);
                } catch (InvalidKeyException ike) {
                    LOG.severe("Invalid key exception: " + ike.toString());
                }

                sign = Hex.encodeHexString(mac.doFinal(hash_data.getBytes(ENCODING)));

            } catch (UnsupportedEncodingException ex) {
                LOG.severe(ex.toString());
            }
            return sign;

        }
    }
}
