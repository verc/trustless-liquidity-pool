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

/**
 *
 * @author desrever <desrever at nubits.com>
 */
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
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.TradeUtils;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.utils.ErrorManager;
import com.nubits.nubot.utils.Utils;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

public class CcedkWrapper implements TradeInterface {

    //Class fields
    private ApiKeys keys;
    private Exchange exchange;
    private final int SPACING_BETWEEN_CALLS = 1100;
    private final int TIME_OUT = 15000;
    private long lastSentTonce = 0L;
    private static int offset = -1000000000;
    private String checkConnectionUrl = "https://www.ccedk.com/";
    private boolean apiBusy = false;
    private final String SIGN_HASH_FUNCTION = "HmacSHA512";
    private final String ENCODING = "UTF-8";
    private final String API_BASE_URL = checkConnectionUrl + "api/v1/";
    private final String API_GET_INFO = "balance/list"; //post
    private final String API_TRADE = "order/new"; //post
    private final String API_GET_TRADES = "trade/list"; //post
    private final String API_ACTIVE_ORDERS = "order/list";
    private final String API_ORDER = "order/info";
    private final String API_CANCEL_ORDER = "order/cancel";
    //For the ticker entry point, use getTicketPath(CurrencyPair pair)
    // Errors
    private ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "errors";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";
    private static final Logger LOG = Logger.getLogger(CcedkWrapper.class.getName());
    private static final String INVALID_NONCE_ERROR = "Invalid Nonce value detected";
    private static final int ROUND_CUTOFF = 99;
    private static int INVALID_NONCE_COUNT = 1;

    public CcedkWrapper() {
        setupErrors();

    }

    public CcedkWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        setupErrors();

    }

    public String createNonce(String requester) {
        //This is a  workaround waiting for clarifications from CCEDK team
        int lastdigit;
        String validNonce;
        int numericalNonce;
        String startvalid = " till";
        int indexStart;
        int upperEdge;
        String nonceError;
        if (offset == -1000000000) {
            JSONParser parser = new JSONParser();
            try {
                String htmlString = Utils.getHTML("https://www.ccedk.com/api/v1/currency/list?nonce=1234567891", false);
                //{"errors":{"nonce":"incorrect range `nonce`=`1234567891`, must be from `1411036100` till `1411036141`"}
                JSONObject httpAnswerJson = (JSONObject) (parser.parse(htmlString));
                JSONObject errors = (JSONObject) httpAnswerJson.get("errors");
                nonceError = (String) errors.get("nonce");
                indexStart = nonceError.lastIndexOf(startvalid) + startvalid.length() + 2;
                upperEdge = Integer.parseInt(nonceError.substring(indexStart, indexStart + 10));
                offset = upperEdge - (int) (System.currentTimeMillis() / 1000L);
            } catch (ParseException ex) {
                LOG.severe(ex.toString());
            } catch (IOException io) {
                LOG.severe(io.toString());
            }
        }
        if (offset != -1000000000) {
            numericalNonce = (int) (System.currentTimeMillis() / 1000L) + offset;

            validNonce = Objects.toString(numericalNonce);
            //LOG.warning("validNonce = " + validNonce);
        } else {
            LOG.severe("Error calculating nonce");
            validNonce = "1234567891";
        }
        return validNonce;
    }

    private void setupErrors() {
        errors.setExchangeName(exchange);
    }

    private ApiResponse getQuery(String url, String method, HashMap<String, String> query_args, boolean isGet) {
        ApiResponse apiResponse = new ApiResponse();

        String queryResult = query(url, method, query_args, isGet);
        if (queryResult == null) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.noConnectionError);
            return apiResponse;
        }
        if (queryResult.contains(INVALID_NONCE_ERROR)) {
            ApiError error = errors.genericError;
            error.setDescription(queryResult);
            apiResponse.setError(error);
            if (INVALID_NONCE_COUNT < 5) {
                getQuery(url, method, query_args, isGet);
                INVALID_NONCE_COUNT++;
            }
            return apiResponse;
        }

        INVALID_NONCE_COUNT = 1;

        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            boolean hasErrors;
            try {
                hasErrors = (boolean) httpAnswerJson.get(TOKEN_ERR);
            } catch (ClassCastException e) {
                hasErrors = true;
            }

            if (hasErrors) {
                JSONObject errorMessage = (JSONObject) httpAnswerJson.get(TOKEN_ERR);
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(errorMessage.toJSONString());
                LOG.severe("Ccedk API returned an error: " + errorMessage);
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
        String url = API_BASE_URL;
        String method = API_GET_INFO;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("response");
            JSONArray entities = (JSONArray) dataJson.get("entities");

            //iterate on all currencies to find what I want
            if (currency == null) { //Get all balances
                int NBTid = TradeUtils.getCCDKECurrencyId(pair.getOrderCurrency().getCode().toUpperCase());
                int PEGid = TradeUtils.getCCDKECurrencyId(pair.getPaymentCurrency().getCode().toUpperCase());
                boolean foundNBT = false;
                boolean foundPEG = false;
                Amount NBTTotal = new Amount(-1, pair.getOrderCurrency());
                Amount PEGTotal = new Amount(-1, pair.getPaymentCurrency());
                for (int i = 0; i < entities.size(); i++) {
                    JSONObject temp = (JSONObject) entities.get(i);
                    int tempid = Integer.parseInt((String) temp.get("currency_id"));
                    if (tempid == NBTid) {
                        foundNBT = true;
                        double tempbalance = Double.parseDouble((String) temp.get("balance"));
                        NBTTotal = new Amount(tempbalance, pair.getOrderCurrency());
                    } else if (tempid == PEGid) {
                        double tempbalance = Double.parseDouble((String) temp.get("balance"));
                        PEGTotal = new Amount(tempbalance, pair.getPaymentCurrency());
                        foundPEG = true;
                    }
                }
                if (foundNBT && foundPEG) {
                    //Pack it into the ApiResponse
                    balance = new Balance(NBTTotal, PEGTotal);
                    apiResponse.setResponseObject(balance);
                } else {
                    ApiError err = errors.genericError;
                    err.setDescription("Cannot find a currency with id = " + NBTid + " or " + PEGid);
                    apiResponse.setError(err);
                }
            } else { //Specific currency requested
                int id = TradeUtils.getCCDKECurrencyId(currency.getCode().toUpperCase());
                boolean found = false;

                Amount total = new Amount(-1, currency);
                for (int i = 0; i < entities.size(); i++) {
                    JSONObject temp = (JSONObject) entities.get(i);
                    int tempid = Integer.parseInt((String) temp.get("currency_id"));
                    if (tempid == id) {
                        found = true;
                        double tempbalance = Double.parseDouble((String) temp.get("balance"));
                        total = new Amount(tempbalance, currency);
                    }
                }
                if (found) {
                    //Pack it into the ApiResponse
                    apiResponse.setResponseObject(total);
                } else {
                    ApiError err = errors.apiReturnError;
                    err.setDescription("Cannot find a currency with id = " + id);
                    apiResponse.setError(err);
                }
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getLastPrice(CurrencyPair pair) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ApiResponse sell(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.SELL.toLowerCase(), pair, amount, rate);
    }

    @Override
    public ApiResponse buy(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.BUY.toLowerCase(), pair, amount, rate);
    }

    private ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double price) {
        ApiResponse apiResponse = new ApiResponse();
        String order_id = "";
        String url = API_BASE_URL;
        String method = API_TRADE;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("pair_id", Integer.toString(TradeUtils.getCCDKECurrencyPairId(pair)));
        query_args.put("type", type);
        query_args.put("price", Double.toString(price));
        query_args.put("volume", Double.toString(amount));

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("response");
            JSONObject entity = (JSONObject) dataJson.get("entity");
            order_id = (String) entity.get("order_id");
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

    private ApiResponse getActiveOrdersImpl(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> orderList = new ArrayList<Order>();
        String url = API_BASE_URL;
        String method = API_ACTIVE_ORDERS;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();

        if (pair != null) {
            String pair_id = Integer.toString(TradeUtils.getCCDKECurrencyPairId(pair));
            query_args.put("pair_id", pair_id);
        }

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("response");
            JSONArray entities;
            try {
                entities = (JSONArray) dataJson.get("entities");
            } catch (ClassCastException e) { //Empty order list returns {"errors":false,"response":{"entities":false},
                apiResponse.setResponseObject(orderList);
                return apiResponse;
            }
            for (int i = 0; i < entities.size(); i++) {
                JSONObject orderObject = (JSONObject) entities.get(i);
                Order tempOrder = parseOrder(orderObject);


                if (!tempOrder.isCompleted()) //Do not add executed orders
                {
                    orderList.add(tempOrder);
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
        ArrayList<Order> orderList = new ArrayList<Order>();
        String url = API_BASE_URL;
        String method = API_ORDER;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("order_id", orderID);

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("response");
            JSONObject entity;
            try {
                boolean valid = (boolean) dataJson.get("entity");
                String message = "The order " + orderID + " does not exist";
                ApiError err = errors.apiReturnError;
                err.setDescription(message);
                apiResponse.setError(err);
                return apiResponse;
            } catch (ClassCastException e) {
                entity = (JSONObject) dataJson.get("entity");
            }

            Order order = parseOrder(entity);

            apiResponse.setResponseObject(order);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> orderList = new ArrayList<Order>();
        String url = API_BASE_URL;
        String method = API_CANCEL_ORDER;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("order_id", orderID);

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("response");
            JSONObject entity;
            try {
                boolean valid = (boolean) dataJson.get("entity");
                String message = "The order " + orderID + " does not exist";
                LOG.severe(message);
                apiResponse.setResponseObject(false);
                return apiResponse;
            } catch (ClassCastException e) {
                entity = (JSONObject) dataJson.get("entity");
            }
            apiResponse.setResponseObject(true);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getTxFee() {
        double defaultFee = 0.2;

        if (Global.options != null) {
            return new ApiResponse(true, Global.options.getTxFee(), null);
        } else {
            return new ApiResponse(true, defaultFee, null);
        }
    }

    @Override
    public ApiResponse getTxFee(CurrencyPair pair) {

        LOG.warning("CCEDK uses global TX fee, currency pair not supported."
                + "now calling getTxFee()");
        return getTxFee();
    }

    @Override
    public ApiResponse isOrderActive(String orderID) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> orderList = new ArrayList<Order>();
        String url = API_BASE_URL;
        String method = API_ORDER;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("order_id", orderID);

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("response");
            JSONObject entity;
            try {
                boolean valid = (boolean) dataJson.get("entity");
                String message = "The order " + orderID + " does not exist";
                LOG.info(message);

                apiResponse.setResponseObject(false);
                return apiResponse;
            } catch (ClassCastException e) {
                entity = (JSONObject) dataJson.get("entity");
            }
            Order order = parseOrder(entity);
            apiResponse.setResponseObject(true);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse clearOrders(CurrencyPair pair) {
        //Since there is no API entry point for that, this call will iterate over active
        ApiResponse toReturn = new ApiResponse();
        boolean ok = true;

        ApiResponse activeOrdersResponse = getActiveOrders();
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                if (pair.equals(tempOrder.getPair())) {
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
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    LOG.severe(ex.toString());
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
        return checkConnectionUrl;
    }

    @Override
    public String query(String url, HashMap<String, String> args, boolean isGet) {
        CcedkService query = new CcedkService(url, args);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(false, false);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to ccdek");
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
    }

    @Override
    public String query(String base, String method, HashMap<String, String> args, boolean isGet) {
        CcedkService query = new CcedkService(base, method, args, keys);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(true, false);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to ccdek");
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
    }

    @Override
    public String query(String url, TreeMap<String, String> args, boolean isGet) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String query(String base, String method, TreeMap<String, String> args, boolean isGet) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private Order parseOrder(JSONObject orderObject) {
        Order order = new Order();

        /*
         * "order_id":"1617","pair_id":"3",
         * "type":"sell","volume":"48.00000000",
         * "price":"9.50000000","fee":"0. 91200000",
         * "active":"1","created":"1406098537"}
         */

        order.setId((String) orderObject.get("order_id"));

        int currencyPairID = Integer.parseInt((String) orderObject.get("pair_id"));
        CurrencyPair cp = TradeUtils.getCCEDKPairFromID(currencyPairID);
        order.setPair(cp);


        order.setType(orderObject.get("type").toString());

        order.setAmount(new Amount(Double.parseDouble((String) orderObject.get("volume")), cp.getOrderCurrency()));
        order.setPrice(new Amount(Double.parseDouble((String) orderObject.get("price")), cp.getPaymentCurrency()));

        int active = Integer.parseInt((String) orderObject.get("active"));
        if (active == 0) {
            order.setCompleted(true);
        } else {
            order.setCompleted(false);
        }

        long created = Long.parseLong(((String) orderObject.get("created")) + "000");
        order.setInsertedDate(new Date(created));

        return order;
    }

    private Trade parseTrade(JSONObject orderObject) {
        //hotfix for Ben request
        //FileSystem.writeToFile(orderObject.toJSONString(), "raw-trades.json", true);

        Trade trade = new Trade();

        /*
         "id":<\d+> AS "trade_id",
         "pair_id":<\d+>,
         "type":<buy|sell>,
         "is_buyer":<0|1>,
         "is_seller":<0|1>,
         "price":<\d{1,8}\.d{0,8}>,
         "volume":<\d{1,8}\.d{0,8}>,
         "fee":<\d{1,8}\.d{0,8}>,
         "created":<\d{10}>
         */
        trade.setId((String) orderObject.get("trade_id"));

        trade.setExchangeName(Constant.CCEDK);
        int currencyPairID = Integer.parseInt((String) orderObject.get("pair_id"));
        CurrencyPair cp = TradeUtils.getCCEDKPairFromID(currencyPairID);
        trade.setPair(cp);

        trade.setType(orderObject.get("is_seller").equals("1") ? Constant.SELL : Constant.BUY);
        trade.setAmount(new Amount(Double.parseDouble((String) orderObject.get("volume")), cp.getOrderCurrency()));
        trade.setPrice(new Amount(Double.parseDouble((String) orderObject.get("price")), cp.getPaymentCurrency()));
        trade.setFee(new Amount(Double.parseDouble((String) orderObject.get("fee")), cp.getPaymentCurrency()));

        long date = Long.parseLong(((String) orderObject.get("created")) + "000");
        trade.setDate(new Date(date));

        return trade;
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
        ArrayList<Trade> tradesList = new ArrayList<Trade>();
        String url = API_BASE_URL;
        String method = API_GET_TRADES;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        String pair_id = Integer.toString(TradeUtils.getCCDKECurrencyPairId(pair));

        String startDateArg;
        if (startTime == 0) {
            long now = System.currentTimeMillis();
            long yesterday = now - Utils.getOneDayInMillis();
            startDateArg = Long.toString(yesterday); //24hours
        } else {
            startDateArg = Long.toString(startTime);
        }

        startDateArg = startDateArg.substring(0, 10);

        query_args.put("date_from", startDateArg);
        query_args.put("items_per_page", "10000");

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("response");
            JSONArray entities;
            try {
                entities = (JSONArray) dataJson.get("entities");
            } catch (ClassCastException e) { //Empty order list returns {"errors":false,"response":{"entities":false},
                apiResponse.setResponseObject(tradesList);
                return apiResponse;
            }
            for (int i = 0; i < entities.size(); i++) {
                JSONObject tradeObject = (JSONObject) entities.get(i);
                Trade tempTrade = parseTrade(tradeObject);
                tradesList.add(tempTrade);

            }
            apiResponse.setResponseObject(tradesList);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    private class CcedkService implements ServiceInterface {

        protected String base;
        protected String method;
        protected HashMap args;
        protected ApiKeys keys;
        protected String url;
        //Parameters used to repeat an API call in case of wrong nonce error
        protected final int MAX_NUMBER_ATTEMPTS = 3;
        protected int wrongNonceCounter;
        protected String adjustedNonce;

        public CcedkService(String base, String method, HashMap<String, String> args, ApiKeys keys) {
            this.base = base;
            this.method = method;
            this.args = args;
            this.keys = keys;
            this.wrongNonceCounter = 0;

        }

        private CcedkService(String url, HashMap<String, String> args) {
            //Used for ticker, does not require auth
            this.url = url;
            this.args = args;
            this.method = "";
            this.wrongNonceCounter = 0;
        }

        @Override
        public String executeQuery(boolean needAuth, boolean isGet) {

            String answer = "";
            String signature = "";
            String post_data = "";
            boolean httpError = false;
            HttpsURLConnection connection = null;

            try {
                // add nonce and build arg list
                if (needAuth) {
                    String nonce;
                    if (wrongNonceCounter == 0) {
                        nonce = createNonce("");
                    } else {
                        LOG.info("Re executing query for the " + wrongNonceCounter + " time. "
                                + "New nonce = " + adjustedNonce
                                + " while calling : " + method);
                        nonce = adjustedNonce;
                    }
                    args.put("nonce", nonce);
                    post_data = TradeUtils.buildQueryString(args, ENCODING);

                    // args signature with apache cryptografic tools
                    String toHash = post_data;

                    signature = signRequest(keys.getPrivateKey(), toHash);
                }
                // build URL

                URL queryUrl;
                if (needAuth) {
                    queryUrl = new URL(base + method);
                } else {
                    queryUrl = new URL(url);
                }


                connection = (HttpsURLConnection) queryUrl.openConnection();
                if (isGet) {
                    connection.setRequestMethod("GET");
                } else {
                    connection.setRequestMethod("POST");
                }
                // create and setup a HTTP connection

                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; CCEDK PHP client; " + Global.settings.getProperty("app_name"));

                if (needAuth) {
                    connection.setRequestProperty("Key", keys.getApiKey());
                    connection.setRequestProperty("Sign", signature);
                }

                connection.setDoOutput(true);
                connection.setDoInput(true);

                //Read the response

                DataOutputStream os = new DataOutputStream(connection.getOutputStream());
                os.writeBytes(post_data);
                os.close();

                BufferedReader br = null;
                if (connection.getResponseCode() >= 400) {
                    httpError = true;
                    br = new BufferedReader(new InputStreamReader((connection.getErrorStream())));
                } else {
                    br = new BufferedReader(new InputStreamReader((connection.getInputStream())));
                }

                String output;

                if (httpError) {
                    LOG.severe("Http error - Post Data: " + post_data);
                }
                LOG.fine("Query to :" + base + "(method=" + method + ")" + " , HTTP response : \n"); //do not log unless is error > 400
                while ((output = br.readLine()) != null) {
                    LOG.fine(output);
                    answer += output;
                }

                //LOG.severe("answer = " + answer);
                if (answer.contains("\"nonce\":\"incorrect range")) {
                    //executeQuery(needAuth, isGet);
                    return INVALID_NONCE_ERROR + " " + answer;
                }

                if (httpError) {
                    JSONParser parser = new JSONParser();
                    try {
                        JSONObject obj2 = (JSONObject) (parser.parse(answer));
                        answer = (String) obj2.get(TOKEN_ERR);

                    } catch (ParseException ex) {
                        LOG.severe(ex.toString());

                    }
                }
            } //Capture Exceptions
            catch (IllegalStateException ex) {
                LOG.severe(ex.toString());
                return null;
            } catch (NoRouteToHostException | UnknownHostException ex) {
                //Global.BtceExchange.setConnected(false);
                LOG.severe(ex.toString());

                answer = TOKEN_BAD_RETURN;
            } catch (IOException ex) {
                LOG.severe(ex.toString());
                return null;
            } finally {
                //close the connection, set all objects to null
                connection.disconnect();
                connection = null;
            }

            return answer;
        }

        @Override
        public String signRequest(String secret, String hash_data) {
            String signature = "";

            Mac mac;
            SecretKeySpec key = null;

            // Create a new secret key
            try {
                key = new SecretKeySpec(secret.getBytes(ENCODING), SIGN_HASH_FUNCTION);
            } catch (UnsupportedEncodingException uee) {
                LOG.severe("Unsupported encoding exception: " + uee.toString());
                return null;
            }

            // Create a new mac
            try {
                mac = Mac.getInstance(SIGN_HASH_FUNCTION);
            } catch (NoSuchAlgorithmException nsae) {
                LOG.severe("No such algorithm exception: " + nsae.toString());
                return null;
            }

            // Init mac with key.
            try {
                mac.init(key);
            } catch (InvalidKeyException ike) {
                LOG.severe("Invalid key exception: " + ike.toString());
                return null;
            }
            try {
                signature = Hex.encodeHexString(mac.doFinal(hash_data.getBytes(ENCODING)));

            } catch (UnsupportedEncodingException ex) {
                LOG.severe(ex.toString());
            }
            return signature;
        }
    }
}
