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
import com.nubits.nubot.trading.ServiceInterface;
import com.nubits.nubot.trading.Ticker;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class BtceWrapper implements TradeInterface {

    private static final Logger LOG = Logger.getLogger(BtceWrapper.class.getName());
    //Class fields
    private ApiKeys keys;
    private Exchange exchange;
    private String checkConnectionUrl = "http://btc-e.com";
    private final String SIGN_HASH_FUNCTION = "HmacSHA512";
    private final String ENCODING = "UTF-8";
    private final String API_BASE_URL = "https://btc-e.com/tapi/";
    private final String API_GET_INFO = "getInfo";
    private final String API_TRADE = "Trade";
    private final String API_ACTIVE_ORDERS = "ActiveOrders";
    private final String API_CANCEL_ORDER = "CancelOrder";
    private final String API_GET_FEE = "https://btc-e.com/exchange/";
    private final String API_V2_URL = "https://btc-e.com/api/2/";
    private final String API_TICKER_USD = "btc_usd/ticker";
    // Errors
    private ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "error";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";


    public BtceWrapper() {
        setupErrors();

    }

    public BtceWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        setupErrors();

    }

    private void setupErrors() {
        errors.setExchangeName(exchange);

    }

    protected static String createNonce() {
        long toRet = Math.round(System.currentTimeMillis() / 1000);
        return Long.toString(toRet);
    }

    private ApiResponse getQuery(String url, String method, HashMap<String, String> query_args, boolean isGet) {
        ApiResponse apiResponse = new ApiResponse();
        String queryResult = query(API_BASE_URL, method, query_args, false);
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
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            long success = (long) httpAnswerJson.get("success");
            if (success == 0) {
                //error
                String errorMessage = (String) httpAnswerJson.get("error");
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(errorMessage);
                //LOG.severe("Btce returned an error: " + errorMessage);
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
        ApiResponse apiResponse = new ApiResponse();
        Balance balance = new Balance();
        String url = API_BASE_URL;
        String method = API_GET_INFO;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        /*Params
         *
         */

        ApiResponse response = getQuery(url, method, query_args, isGet);

        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("return");
            JSONObject funds = (JSONObject) dataJson.get("funds");

            String pegCode = pair.getPaymentCurrency().getCode().toLowerCase();
            String nbtCode = pair.getOrderCurrency().getCode().toLowerCase();

            Amount PEGTotal = new Amount(Double.parseDouble(funds.get(pegCode).toString()), Constant.USD);
            Amount NBTTotal = new Amount(Double.parseDouble(funds.get(nbtCode).toString()), Constant.NBT);

            balance = new Balance(NBTTotal, PEGTotal);

            //Pack it into the ApiResponse
            apiResponse.setResponseObject(balance);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getAvailableBalance(Currency currency) {
        ApiResponse apiResponse = new ApiResponse();
        Balance balance = new Balance();
        String url = API_BASE_URL;
        String method = API_GET_INFO;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();

        ApiResponse response = getQuery(url, method, query_args, isGet);

        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("return");
            JSONObject funds = (JSONObject) dataJson.get("funds");

            Amount amount = new Amount(Double.parseDouble(funds.get(currency.getCode().toLowerCase()).toString()), currency);

            apiResponse.setResponseObject(amount);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getLastPrice(CurrencyPair pair) {
        Ticker ticker = new Ticker();
        ApiResponse apiResponse = new ApiResponse();
        String url = API_V2_URL;
        String method = API_TICKER_USD;
        boolean isGet = false;

        double last = -1;
        double ask = -1;
        double bid = -1;
        HashMap<String, String> query_args = new HashMap<>();

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject tickerObject = (JSONObject) httpAnswerJson.get("ticker");
            last = Utils.getDouble(tickerObject.get("last"));
            bid = Utils.getDouble(tickerObject.get("sell"));
            ask = Utils.getDouble(tickerObject.get("buy"));
            ticker.setAsk(ask);
            ticker.setBid(bid);
            ticker.setLast(last);
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
        return enterOrder(Constant.BUY, pair, amount, rate);
    }

    @Override
    public ApiResponse getActiveOrders() {
        return getActiveOrdersImpl(null);
    }

    @Override
    public ApiResponse getActiveOrders(CurrencyPair pair) {
        return getActiveOrdersImpl(pair);
    }

    @Override
    public ApiResponse getOrderDetail(String orderID) {
        ApiResponse apiResp = new ApiResponse();
        Order order = null;

        ApiResponse listApiResp = getActiveOrders();
        if (listApiResp.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) listApiResp.getResponseObject();
            boolean found = false;
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                if (orderID.equals(tempOrder.getId())) {
                    found = true;
                    apiResp.setResponseObject(tempOrder);
                    return apiResp;
                }
            }
            if (!found) {
                ApiError error = errors.genericError;
                error.setDescription("Cannot find the order with id " + orderID);
                apiResp.setError(error);
                return apiResp;

            }
        } else {
            return listApiResp;
        }
        return apiResp;
    }

    @Override
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        String method = API_CANCEL_ORDER;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        /*Params
         *  order_id
         */

        query_args.put("order_id", orderID);

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            apiResponse.setResponseObject(true);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getTxFee() {
        if (Global.options != null) {
            return new ApiResponse(true, Global.options.getTxFee(), null);
        } else {
            ApiResponse apiResponse = new ApiResponse();

            String strDelimiterStart = "the fee for transactions is ";
            String strDelimterStop = "%.</p>";
            String content = "ERROR";
            try {
                content = Utils.getHTML(API_GET_FEE, true);
            } catch (IOException ex) {
                LOG.severe(ex.toString());
            }
            if (!content.equals("ERROR")) {
                int startIndex = content.lastIndexOf(strDelimiterStart) + strDelimiterStart.length();
                int stopIndex = content.lastIndexOf(strDelimterStop);
                String feeString = content.substring(startIndex, stopIndex);
                double fee = Double.parseDouble(feeString);
                apiResponse.setResponseObject(fee);
            } else {
                apiResponse.setError(errors.genericError);
            }

            return apiResponse;
        }
    }

    @Override
    public ApiResponse getTxFee(CurrencyPair pair) {
        LOG.fine("Btc-e uses global TX fee, currency pair not supprted. \n"
                + "now calling getTxFee()");
        return getTxFee();
    }

    /*
     public ApiResponse getPermissions() {
     ApiResponse apiResponse = new ApiResponse();
     String path = API_GET_INFO;
     HashMap<String, String> query_args = new HashMap<>();

     ApiPermissions permissions = new ApiPermissions(false, false, false, false, false, false);
     String queryResult = query(API_BASE_URL, API_GET_INFO, query_args, false);
     if (queryResult.startsWith(TOKEN_ERR)) {
     apiResponse.setError(new ApiError(ERROR_GENERIC, "Generic error with btce service call"));
     return apiResponse;
     }

     if (queryResult.equals(ERROR_NO_CONNECTION)) {
     apiResponse.setError(getErrorByCode(ERROR_NO_CONNECTION));
     return apiResponse;
     }
     /*Sample result
     *{
     *"success":1,
     *"return":{
     *	"funds":{
     *		"usd":325,
     *		"btc":23.998,
     *		"sc":121.998,
     *		"ltc":0,
     *		"ruc":0,
     *		"nmc":0
     *	},
     *	"rights":{
     *		"info":1,
     *		"trade":1
     *	},
     *	"transaction_count":80,
     *	"open_orders":1,
     *	"server_time":1342123547
     *      }
     *}
     */
    /*
     JSONParser parser = new JSONParser();
     try {
     JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
     long success = (long) httpAnswerJson.get("success");
     if (success == 0) {
     //error
     String error = (String) httpAnswerJson.get("error");
     apiResponse.setError(new ApiError(ERRROR_API, error));
     LOG.severe("Btce returned an error: " + error);
     return apiResponse;
     } else {
     //correct
     JSONObject dataJson = (JSONObject) httpAnswerJson.get("return");
     JSONObject rightsJson = (JSONObject) dataJson.get("rights");

     long info = (long) rightsJson.get("info");
     long trade = (long) rightsJson.get("trade");
     long withdraw = (long) rightsJson.get("withdraw");

     if (info == 1) {
     permissions.setGet_info(true);
     }
     if (trade == 1) {
     permissions.setTrade(true);
     }
     if (withdraw == 1) {
     permissions.setWithdraw(true);
     }

     permissions.setValid_keys(true);
     }
     } catch (ParseException ex) {
     LOG.severe(ex.toString());
     apiResponse.setError(new ApiError(ERROR_PARSING, "Error while parsing api permissions for btce"));
     }
     apiResponse.setResponseObject(permissions);
     return apiResponse;

     }
     */
    private ApiResponse getActiveOrdersImpl(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> orderList = new ArrayList<Order>();
        String url = API_BASE_URL;
        String method = API_ACTIVE_ORDERS;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();


        /*Params
         * pair, default all pairs
         */
        if (pair != null) {
            query_args.put("pair", pair.toString("_"));
        }

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            try {
                JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
                org.json.JSONObject dataJson = (org.json.JSONObject) httpAnswerJson.get("return");

                //Iterate on orders
                String names[] = org.json.JSONObject.getNames(dataJson);
                for (int i = 0; i < names.length; i++) {
                    org.json.JSONObject tempJson = dataJson.getJSONObject(names[i]);
                    Order temp = new Order();

                    temp.setId(names[i]);

                    //Create a CurrencyPair object
                    CurrencyPair cp = CurrencyPair.getCurrencyPairFromString((String) tempJson.get("pair"), "_");

                    //Parse the status to encapsulate into Order object
                    boolean executed = false;
                    int status = tempJson.getInt("status");


                    switch (status) {
                        case 0: {
                            executed = false;
                            break;
                        }
                        case 1: {
                            executed = true;
                            break;
                        }
                        default: {
                            apiResponse.setError(new ApiError(231445, "Order status unknown : " + status));
                            break;
                        }
                    }

                    temp.setPair(cp);
                    temp.setType((String) tempJson.get("type"));
                    temp.setAmount(new Amount(tempJson.getDouble("amount"), cp.getOrderCurrency()));
                    temp.setPrice(new Amount(tempJson.getDouble("rate"), cp.getPaymentCurrency()));

                    temp.setInsertedDate(new Date(tempJson.getLong("timestamp_created")));

                    temp.setCompleted(executed);

                    if (!executed) //Do not return orders that are already executed
                    {
                        orderList.add(temp);
                    }
                }
                apiResponse.setResponseObject(orderList);
            } catch (JSONException ex) {
                LOG.severe(ex.toString());
                apiResponse.setError(errors.parseError);
                return apiResponse;
            }
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    private ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double rate) {
        ApiResponse apiResponse = new ApiResponse();
        String order_id = "";
        String url = API_BASE_URL;
        String method = API_TRADE;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        query_args.put("pair", pair.toString("_"));
        query_args.put("type", type);
        query_args.put("rate", Double.toString(rate));
        query_args.put("amount", Double.toString(amount));

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject dataJson = (JSONObject) httpAnswerJson.get("return");
            order_id = "" + (long) dataJson.get("order_id");
            apiResponse.setResponseObject(order_id);
        } else {
            apiResponse = response;
        }

        return apiResponse;
    }

    @Override
    public ApiError getErrorByCode(int code) {
        return null;
    }

    @Override
    public ApiResponse isOrderActive(String id) {
        ApiResponse existResponse = new ApiResponse();

        ApiResponse orderDetailResponse = getOrderDetail(id);
        if (orderDetailResponse.isPositive()) {
            Order order = (Order) orderDetailResponse.getResponseObject();
            existResponse.setResponseObject(true);
        } else {
            ApiError err = orderDetailResponse.getError();
            if (err.getCode() == 4564) {
                //Cannot find order
                existResponse.setResponseObject(false);
            } else {
                existResponse.setError(err);
                LOG.severe(existResponse.getError().toString());
            }
        }

        return existResponse;
    }

    @Override
    public String query(String url, HashMap<String, String> args, boolean isGet) {
        BtceService query = new BtceService(url, args);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(false, false);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to btce");
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
    }

    @Override
    public String query(String base, String method, HashMap<String, String> args, boolean isGet) {
        BtceService query = new BtceService(base, method, args, keys);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(true, false);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to btce");
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
            toReturn.setResponseObject(ok);
        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
            toReturn.setError(activeOrdersResponse.getError());
            return toReturn;
        }

        return toReturn;
    }

    @Override
    public String getUrlConnectionCheck() {
        return checkConnectionUrl;
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }


    /* Service implementation */
    private class BtceService implements ServiceInterface {

        protected String base;
        protected String method;
        protected HashMap args;
        protected ApiKeys keys;
        protected String url;

        public BtceService(String base, String method, HashMap<String, String> args, ApiKeys keys) {
            this.base = base;
            this.method = method;
            this.args = args;
            this.keys = keys;
        }

        private BtceService(String url, HashMap<String, String> args) {
            //Used for ticker, does not require auth
            this.url = url;
            this.args = args;
            this.method = "";
        }

        @Override
        public String executeQuery(boolean needAuth, boolean isGet) {

            String answer = null;
            String signature = "";
            String post_data = "";
            boolean httpError = false;
            HttpsURLConnection connection = null;

            try {
                // add nonce and build arg list
                if (needAuth) {
                    args.put("nonce", createNonce());
                    args.put("method", method);

                    post_data = TradeUtils.buildQueryString(args, ENCODING);

                    // args signature with apache cryptografic tools
                    String toHash = post_data;

                    signature = signRequest(keys.getPrivateKey(), toHash);
                }
                // build URL

                URL queryUrl;
                if (needAuth) {
                    queryUrl = new URL(base);
                } else {
                    queryUrl = new URL(url);
                }


                connection = (HttpsURLConnection) queryUrl.openConnection();
                connection.setRequestMethod("POST");

                // create and setup a HTTP connection

                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("User-Agent", Global.settings.getProperty("app_name"));

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
                boolean toLog = false;
                if (connection.getResponseCode() >= 400) {
                    httpError = true;
                    br = new BufferedReader(new InputStreamReader((connection.getErrorStream())));
                    toLog = true;
                } else {
                    br = new BufferedReader(new InputStreamReader((connection.getInputStream())));
                }

                String output;

                if (httpError) {
                    LOG.severe("Post Data: " + post_data);
                }
                LOG.fine("Query to :" + base + "(method=" + method + ")" + " , HTTP response : \n"); //do not log unless is error > 400
                while ((output = br.readLine()) != null) {
                    LOG.fine(output);
                    answer += output;
                }
                /*
                if (httpError) {
                    JSONParser parser = new JSONParser();
                    try {
                        JSONObject obj2 = (JSONObject) (parser.parse(answer));
                        answer = (String) obj2.get(TOKEN_ERR);

                    } catch (ParseException ex) {
                        LOG.severe(ex.toString());

                    }
                }
                */
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
