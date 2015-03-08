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
import com.nubits.nubot.models.*;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.trading.ServiceInterface;
import com.nubits.nubot.trading.Ticker;
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.utils.ErrorManager;
import java.io.*;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.*;
import java.util.*;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import org.apache.commons.codec.binary.Hex;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Created by sammoth on 30/11/14.
 */
public class ExcoinWrapper implements TradeInterface {

    private static final Logger LOG = Logger.getLogger(ExcoinWrapper.class.getName());
    //Class fields
    private ApiKeys keys;
    private Exchange exchange;
    private final String SIGN_HASH_FUNCTION = "HmacSHA256";
    private final String ENCODING = "UTF-8";
    private final int EXPIRE_TIMESTAMP = 900; //15 minutes
    private String apiBaseUrl;
    private String checkConnectionUrl;
    //API Paths
    private final String API_BASE_URL = "https://api.exco.in/v1";
    private final String API_ACCOUNT = "account";
    private final String API_EXCHANGE = "exchange";
    private final String API_SUMMARY = "summary";
    private final String API_TRADES = "trades";
    private final String API_ORDERS = "orders";
    private final String API_TRADE = "issue";
    private final String API_ORDER = "order";
    private final String API_CANCEL = "cancel";
    private final String API_TIMESTAMP = "timestamp";
    private final int API_MAX_TRADES = 750;
    //Errors
    private ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "error";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";

    public ExcoinWrapper() {
        setupErrors();
    }

    public ExcoinWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        setupErrors();
    }

    private void setupErrors() {
        errors.setExchangeName(exchange);
    }

    private ApiResponse getQuery(String url) {
        ApiResponse apiResponse = new ApiResponse();
        HashMap<String, String> query_args = new HashMap<>();
        boolean isGet = true;
        String queryResult = query(url, query_args, isGet);
        if (queryResult == null) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.noConnectionError);
            return apiResponse;
        }

        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) parser.parse(queryResult);
            if (httpAnswerJson.containsKey(TOKEN_ERR)) {
                String errorMessage = httpAnswerJson.get(TOKEN_ERR).toString();
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(errorMessage);
                LOG.severe("Exco.in API returned an error: " + errorMessage);
                apiResponse.setError(apiErr);
            } else {
                //LOG.info("httpAnswerJSON = \n" + httpAnswerJson.toJSONString());
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
            } catch (ClassCastException ccex) {
                LOG.severe("httpResponse: " + queryResult + " \n" + ccex.toString());
                apiResponse.setError(errors.genericError);
            }
        } catch (ParseException pe) {
            LOG.severe("httpResponse: " + queryResult + " \n" + pe.toString());
            apiResponse.setError(errors.parseError);
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getAvailableBalances(CurrencyPair pair) {
        return getBalanceImpl(pair, null);
    }

    @Override
    public ApiResponse getAvailableBalance(Currency currency) {
        return getBalanceImpl(null, currency);
    }

    private ApiResponse getBalanceImpl(CurrencyPair pair, Currency currency) {
        ApiResponse apiResponse = new ApiResponse();

        String url = API_BASE_URL + "/" + API_ACCOUNT + "/" + API_SUMMARY;

        ApiResponse response = getQuery(url);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONArray activeWallets = (JSONArray) httpAnswerJson.get("active_wallets");
            if (currency == null) { //get all balances
                Amount PEGAvail = new Amount(0, pair.getPaymentCurrency());
                Amount NBTAvail = new Amount(0, pair.getOrderCurrency());
                Amount PEGonOrder = new Amount(0, pair.getPaymentCurrency());
                Amount NBTonOrder = new Amount(0, pair.getOrderCurrency());
                for (Iterator<JSONObject> wallet = activeWallets.iterator(); wallet.hasNext();) {
                    JSONObject thisWallet = wallet.next();
                    String thisCurrency = thisWallet.get("currency").toString();
                    if (thisCurrency.equals(pair.getPaymentCurrency().getCode().toUpperCase())) {
                        PEGAvail.setQuantity(Double.parseDouble(thisWallet.get("available_balance").toString()));
                        PEGonOrder.setQuantity(Double.parseDouble(thisWallet.get("order_balance").toString()));
                    }
                    if (thisCurrency.equals(pair.getOrderCurrency().getCode().toUpperCase())) {
                        NBTAvail.setQuantity(Double.parseDouble(thisWallet.get("available_balance").toString()));
                        NBTonOrder.setQuantity(Double.parseDouble(thisWallet.get("order_balance").toString()));
                    }
                }
                Balance balance = new Balance(PEGAvail, NBTAvail, PEGonOrder, NBTonOrder);

                balance = Balance.getSwapedBalance(balance); //Swap here for BTC_NBT
                apiResponse.setResponseObject(balance);
            } else { //get specific balance
                Amount total = new Amount(0, currency);
                for (Iterator<JSONObject> wallet = activeWallets.iterator(); wallet.hasNext();) {
                    JSONObject thisWallet = wallet.next();
                    String thisCurrency = thisWallet.get("currency").toString();
                    if (thisCurrency.equals(currency.getCode().toUpperCase())) {
                        total.setQuantity(Double.parseDouble(thisWallet.get("available_balance").toString()));
                    }
                }
                apiResponse.setResponseObject(total);
            }
        } else {
            apiResponse = response;
        }
        return apiResponse;
    }

    @Override
    public ApiResponse getLastPrice(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();

        String curs = pair.getPaymentCurrency().getCode().toUpperCase() + "/" + pair.getOrderCurrency().getCode().toUpperCase();
        String url = API_BASE_URL + "/" + API_EXCHANGE + "/" + curs + "/" + API_SUMMARY;

        double last = -1;
        double ask = -1;
        double bid = -1;
        Ticker ticker = new Ticker();

        ApiResponse response = getQuery(url);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            last = Double.parseDouble(httpAnswerJson.get("last_price").toString());
            ask = Double.parseDouble(httpAnswerJson.get("lowest_ask").toString());
            bid = Double.parseDouble(httpAnswerJson.get("top_bid").toString());

            ticker.setLast(last);
            ticker.setAsk(ask);
            ticker.setBid(bid);
            apiResponse.setResponseObject(ticker);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse sell(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.SELL, pair, amount, rate);
    }

    @Override
    public ApiResponse buy(CurrencyPair pair, double amount, double rate) {
        amount = amount * rate;
        return enterOrder(Constant.BUY, pair, amount, rate);
    }

    public ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double rate) {
        ApiResponse apiResponse = new ApiResponse();

        DecimalFormat nf = new DecimalFormat("0");
        nf.setMinimumFractionDigits(8);
        String curs = pair.getPaymentCurrency().getCode().toUpperCase() + "/" + pair.getOrderCurrency().getCode().toUpperCase();
        String details = curs + "/" + (type.equals("BUY") ? "bid" : "ask") + "/" + nf.format(amount) + "/" + nf.format(rate);
        String url = API_BASE_URL + "/" + API_ACCOUNT + "/" + API_ORDERS + "/" + API_TRADE + "/" + details;

        ApiResponse response = getQuery(url);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            String order_id = httpAnswerJson.get("id").toString();
            apiResponse.setResponseObject(order_id);
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
        //https://api.exco.in/v1/account/orders(/{CURRENCY}/{COMMODITY}(/{TYPE}))
        String url = API_BASE_URL + "/" + API_ACCOUNT + "/" + API_ORDERS;
        String commodity;
        String currency;
        CurrencyPair returnedPair;
        String type;
        ArrayList<Order> orderList = new ArrayList<Order>();

        ApiResponse response = getQuery(url);

        /*
         [
         {
         "currency": "NBT",
         "commodity": "BTC",
         "orders": [
         {
         "count": "23",
         "type": "BID",
         "orders": [
         {
         "id": "BLK-DRK-BID-Qjclkj34GKmc4gDXKc",
         "timestamp": "2014-12-06T07:03:53.584Z",
         "price": "0.00000023",
         "commodity_amount": "22446985.14519785",
         "currency_amount": "5.16280655",
         "status": "OPEN"
         },
         ...
         ]
         },
         {
         "count": "23",
         "type": "ASK",
         "orders": [
         {
         "id": "BLK-DRK-ASK-Qjc03d5dlKmc4gDXKc",
         "timestamp": "2014-12-06T07:03:53.584Z",
         "price": "0.00000023",
         "commodity_amount": "22446985.14519785",
         "currency_amount": "5.16280655",
         "status": "OPEN"
         },
         ...
         ]
         }
         ]
         }
         */

        if (response.isPositive()) {
            JSONArray httpAnswerJson = (JSONArray) response.getResponseObject();
            for (Iterator<JSONObject> exchange = httpAnswerJson.iterator(); exchange.hasNext();) {
                JSONObject thisExchange = exchange.next();
                commodity = thisExchange.get("commodity").toString();
                currency = thisExchange.get("currency").toString();
                //only valid pair if a pair is specified
                if ((pair != null)
                        && (!currency.equals(pair.getPaymentCurrency().getCode().toUpperCase())
                        && !commodity.equals(pair.getOrderCurrency().getCode().toUpperCase()))) {
                    continue;
                }
                JSONArray _orders = (JSONArray) thisExchange.get("orders");
                for (Iterator<JSONObject> _order = _orders.iterator(); _order.hasNext();) {
                    JSONObject thisTyp = _order.next();
                    type = thisTyp.get("type").toString();
                    JSONArray orders = (JSONArray) thisTyp.get("orders");
                    for (Iterator<JSONObject> order = orders.iterator(); order.hasNext();) {
                        JSONObject orderJson = order.next();
                        returnedPair = CurrencyPair.getCurrencyPairFromString(commodity + "_" + currency, "_");
                        Order out = parseOrder(orderJson, returnedPair, type);
                        orderList.add(out);
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
        //https://api.exco.in/v1/account/order/{ORDER_ID}
        String url = API_BASE_URL + "/" + API_ACCOUNT + "/" + API_ORDER + "/" + orderID;

        String[] splitOrderId = orderID.split("-");
        String pairString = splitOrderId[1] + "_" + splitOrderId[0];
        CurrencyPair pair = CurrencyPair.getCurrencyPairFromString(pairString, "_");

        ApiResponse response = getQuery(url);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            Order out = parseOrder(httpAnswerJson, pair, null);
            apiResponse.setResponseObject(out);
        } else {
            apiResponse = response;
        }
        return apiResponse;
    }

    public Order parseOrder(JSONObject in, CurrencyPair pair, String type) {
        Order out = new Order();
        //set the id
        out.setId(in.get("id").toString());
        //set the inserted date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        Date date = null;
        try {
            date = sdf.parse(in.get("timestamp").toString());
        } catch (java.text.ParseException pe) {
            //sometimes timestamp in this format are returned
            //2014-12-19T16:02:07.961Z
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
            try {
                date = sdf.parse(in.get("timestamp").toString());
            } catch (java.text.ParseException pe1) {
                LOG.severe(pe1.toString());
            }
        }
        if (date != null) {
            long timeStamp = date.getTime();
            Date insertDate = new Date(timeStamp);
            out.setInsertedDate(insertDate);
        }
        //set the price
        Amount price = new Amount(Double.parseDouble(in.get("price").toString()), pair.getPaymentCurrency());
        out.setPrice(price);
        //set the amount
        Amount amount = new Amount(Double.parseDouble(in.get("commodity_amount").toString()), pair.getOrderCurrency());
        out.setAmount(amount);
        //set the type
        if (type == null) {
            type = in.get("type").toString();
        }
        out.setType(type.equals("BID") ? Constant.BUY : Constant.SELL);
        //set the pair
        out.setPair(pair);

        return out;
    }

    @Override
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        //https://api.exco.in/v1/account/order/{ORDER_ID}/cancel
        String url = API_BASE_URL + "/" + API_ACCOUNT + "/" + API_ORDER + "/" + orderID + "/" + API_CANCEL;

        ApiResponse response = getQuery(url);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            String returnedOrderId = httpAnswerJson.get("id").toString();
            if (returnedOrderId.equals(orderID)) {
                apiResponse.setResponseObject(true);
            } else {
                apiResponse.setResponseObject(false);
            }
        } else {
            apiResponse = response;
        }
        return apiResponse;
    }

    @Override
    public ApiResponse getTxFee() {
        double defaultFee = 0.15;
        if (Global.options != null) {
            defaultFee = Global.options.getTxFee();
        }
        return new ApiResponse(true, defaultFee, null);

    }

    @Override
    public ApiResponse getTxFee(CurrencyPair pair) {
        LOG.fine("Excoin uses global TX fee, currency pair not supported. \n"
                + "now calling getTxFee()");
        return getTxFee();
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair) {
        return getLastTradesImpl(pair, 0);
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime) {
        return getLastTradesImpl(pair, startTime);
    }

    public ApiResponse getLastTradesImpl(CurrencyPair pair, long startTime) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Trade> tradeList = new ArrayList<Trade>();


        String url;
        if (startTime == 0) { //https://api.exco.in/v1/account/trades(/{COUNT})
            LOG.info("A maximum of " + API_MAX_TRADES + " trades can be returned from the API");
            url = API_BASE_URL + "/" + API_ACCOUNT + "/" + API_TRADES + "/" + API_MAX_TRADES;
        } else { //https://api.exco.in/v1/account/timestamp/{TIMESTAMP}
            url = API_BASE_URL + "/" + API_ACCOUNT + "/" + API_TRADES + "/" + API_TIMESTAMP + "/" + startTime;
        }

        ApiResponse response = getQuery(url);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONArray trades = (JSONArray) httpAnswerJson.get("trades");
            for (Iterator<JSONObject> trade = trades.iterator(); trade.hasNext();) {
                tradeList.add(parseTrade(trade.next()));
            }
            apiResponse.setResponseObject(tradeList);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    public Trade parseTrade(JSONObject in) {
        Trade out = new Trade();

        //get and set the pair
        String commodity = in.get("commodity").toString();
        String currency = in.get("currency").toString();
        CurrencyPair pair = CurrencyPair.getCurrencyPairFromString(commodity + "_" + currency, "_");
        out.setPair(pair);
        //get and set the type
        String type = in.get("type").toString();
        out.setType(type.equals("BUY") ? Constant.BUY : Constant.SELL);
        //get and set the price
        Amount price = new Amount(Double.parseDouble(in.get("price").toString()), pair.getPaymentCurrency());
        out.setPrice(price);
        //get and set the date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss z");
        Date date = null;
        try {
            date = sdf.parse(in.get("timestamp").toString());
        } catch (java.text.ParseException pe) {
            //sometimes timestamp in this format are returned
            //2014-12-19T16:02:07.961Z
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
            try {
                date = sdf.parse(in.get("timestamp").toString());
            } catch (java.text.ParseException pe1) {
                LOG.severe(pe1.toString());
            }
        }
        if (date != null) {
            long timeStamp = date.getTime();
            Date insertDate = new Date(timeStamp);
            out.setDate(insertDate);
        }
        //set the exchangeName
        out.setExchangeName(exchange.getName());
        //set the amount
        Amount amount;
        if (type.equals("BUY")) {
            amount = new Amount(Double.parseDouble(in.get("received").toString()), pair.getOrderCurrency());
        } else {
            amount = new Amount(Double.parseDouble(in.get("sent").toString()), pair.getOrderCurrency());
        }
        out.setAmount(amount);
        //set the fee
        Amount fee = new Amount(Double.parseDouble(in.get("fee").toString()), pair.getPaymentCurrency());
        out.setFee(fee);

        //generate the unique id - MD5 hash of datetime and pair concatenation
        String hash_data = in.get("timestamp").toString()
                + in.get("commodity").toString()
                + in.get("currency").toString();
        String id = null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(hash_data.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
            }
            id = sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            LOG.severe(e.toString());
        }
        //set the id
        out.setId(id);
        //set the order_id
        out.setOrder_id(id);

        return out;
    }

    @Override
    public ApiResponse isOrderActive(String id) {
        ApiResponse apiResponse = new ApiResponse();
        ApiResponse activeOrdersResponse = getActiveOrders();

        apiResponse.setResponseObject(false);

        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();
            for (Iterator<Order> order = orderList.iterator(); order.hasNext();) {
                Order thisOrder = order.next();
                if (thisOrder.getId().equals(id)) {
                    apiResponse.setResponseObject(true);
                }
            }
        }

        return apiResponse;
    }

    @Override
    public ApiResponse clearOrders(CurrencyPair pair) {
        //Since there is no API entry point for that, this call will iterate over active orders
        ApiResponse toReturn = new ApiResponse();
        boolean ok = true;

        ApiResponse activeOrdersResponse = getActiveOrders();
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                if (tempOrder.getPair().equals(pair)) {
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
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        LOG.severe(ex.toString());
                    }
                }

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
        return API_BASE_URL;
    }

    @Override
    public String query(String url, HashMap<String, String> args, boolean isGet) {
        ExcoinService query = new ExcoinService(url, keys);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(true, isGet);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to Excoin");
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
    }

    @Override
    public String query(String base, String method, HashMap<String, String> args, boolean isGet) {
        return null;
    }

    @Override
    public String query(String url, TreeMap<String, String> args, boolean isGet) {
        return null;
    }

    @Override
    public String query(String base, String method, TreeMap<String, String> args, boolean isGet) {
        return null;
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
    }

    private class ExcoinService implements ServiceInterface {

        protected String url;
        protected ApiKeys keys;

        public ExcoinService(String url, ApiKeys keys) {
            this.url = url + "?expire=" + getExpireTimeStamp();
            this.keys = keys;
        }

        public String getExpireTimeStamp() {
            Long timeStamp = ((System.currentTimeMillis() / 1000L) + EXPIRE_TIMESTAMP);
            return timeStamp.toString();
        }

        @Override
        public String executeQuery(boolean needAuth, boolean isGet) {
            HttpsURLConnection connection = null;
            boolean httpError = false;
            String output;
            int response = 200;
            String answer = null;
            URL queryUrl = null;
            String post_data = "";

            try {
                queryUrl = new URL(url);
            } catch (MalformedURLException mal) {
                LOG.severe(mal.toString());
            }

            try {
                connection = (HttpsURLConnection) queryUrl.openConnection();
                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("User-Agent", Global.settings.getProperty("app_name"));
                connection.setRequestProperty("Accept", "*/*");
                if (needAuth) {
                    connection.setRequestProperty("Api-Key", keys.getApiKey());
                    connection.setRequestProperty("Api-Signature", signRequest(keys.getPrivateKey(), url));
                }
                connection.setRequestProperty("Connection", "close");
                connection.setRequestProperty("Host", "exco.in");

                connection.setDoOutput(true);
                connection.setDoInput(true);

                if (isGet) {
                    connection.setRequestMethod("GET");
                } else {
                    connection.setRequestMethod("POST");
                    DataOutputStream os = new DataOutputStream(connection.getOutputStream());
                    os.writeBytes(post_data);
                    os.flush();
                    os.close();
                }
            } catch (ProtocolException pe) {
                LOG.severe(pe.toString());
                return answer;
            } catch (IOException io) {
                LOG.severe((io.toString()));
                return answer;
            }

            BufferedReader br = null;
            try {
                if (connection.getResponseCode() >= 400) {
                    httpError = true;
                    response = connection.getResponseCode();
                    answer = "";
                    br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                } else {
                    answer = "";
                    br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                }
            } catch (IOException io) {
                LOG.severe(io.toString());
                return answer;
            }

            if (httpError) {
                LOG.severe("Query to : " + queryUrl
                        + "\nHTTP Response : " + Objects.toString(response));
            }

            try {
                while ((output = br.readLine()) != null) {
                    answer += output;
                }
            } catch (IOException io) {
                LOG.severe(io.toString());
                return null;
            }

            /*
             if (httpError) {
             JSONParser parser = new JSONParser();
             try {
             JSONObject obj = (JSONObject) (parser.parse(answer));
             answer = (String) obj.get(TOKEN_ERR);
             } catch (ParseException pe) {
             LOG.severe(pe.toString());
             return null;
             }
             }
             */

            connection.disconnect();
            connection = null;

            return answer;
        }

        @Override
        public String signRequest(String secret, String hash_data) {
            String sign = "";
            try {
                Mac mac;
                SecretKeySpec key;
                // Create a new secret key
                key = new SecretKeySpec(secret.getBytes(ENCODING), SIGN_HASH_FUNCTION);
                // Create a new mac
                mac = Mac.getInstance(SIGN_HASH_FUNCTION);
                // Init mac with key.
                mac.init(key);
                sign = Hex.encodeHexString(mac.doFinal(hash_data.getBytes(ENCODING)));
            } catch (UnsupportedEncodingException uee) {
                LOG.severe("Unsupported encoding exception: " + uee.toString());
            } catch (NoSuchAlgorithmException nsae) {
                LOG.severe("No such algorithm exception: " + nsae.toString());
            } catch (InvalidKeyException ike) {
                LOG.severe("Invalid key exception: " + ike.toString());
            }
            return sign;
        }
    }
}
