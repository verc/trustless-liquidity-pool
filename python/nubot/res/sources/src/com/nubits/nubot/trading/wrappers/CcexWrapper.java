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
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.utils.ErrorManager;
import com.nubits.nubot.utils.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class CcexWrapper implements TradeInterface {

    private ApiKeys keys;
    private Exchange exchange;
    private String checkConnectionUrl = "https://c-cex.com/";
    private static final Logger LOG = Logger.getLogger(CcexWrapper.class.getName());
    //Entry point(s)
    private final String API_BASE = "https://c-cex.com/t/r.html?";
    private String baseUrl;
    //Errors
    private ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "error";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";

    public CcexWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        this.baseUrl = API_BASE + "key=" + keys.getPrivateKey();

        setupErrors();
    }

    public CcexWrapper() {
        setupErrors();
    }

    public void initBaseUrl() {
        this.baseUrl = API_BASE + "key=" + keys.getPrivateKey();
    }

    private void setupErrors() {
        errors.setExchangeName(exchange);
    }

    private ApiResponse getQuery(String url, HashMap<String, String> query_args, boolean isGet) {
        ApiResponse apiResponse = new ApiResponse();



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

    public ApiResponse getBalanceImpl(CurrencyPair pair, Currency currency) {
        ApiResponse apiResponse = new ApiResponse();
        Balance balance = new Balance();
        String url = baseUrl + "&a=getbalance";

        String queryResult = query(url, new HashMap<String, String>(), true);

        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.startsWith("Access denied")) {
            apiResponse.setError(errors.authenticationError);
            return apiResponse;
        }


        /*Sample result
         *
         * {"return":
         *  [
         *   {"usd":0},
         *   {"btc":0.04666311},
         *   ...
         *  ]
         * }
         *
         */

        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            JSONArray balanceArray = (JSONArray) httpAnswerJson.get("return");

            if (currency != null) {
                //looking for a specific currency
                String lookingFor = currency.getCode().toLowerCase();
                boolean found = false;
                for (int i = 0; i < balanceArray.size(); i++) {
                    JSONObject tempElement = (JSONObject) balanceArray.get(i);
                    if (tempElement.containsKey(lookingFor)) {
                        found = true;
                        double foundBalance = Utils.getDouble(tempElement.get(lookingFor));
                        apiResponse.setResponseObject(new Amount(foundBalance, currency));
                    }
                }
                if (!found) {
                    //Specific currency not found
                    String errorMessage = "Cannot find a balance for currency " + lookingFor;
                    ApiError apiErr = errors.genericError;
                    apiErr.setDescription(errorMessage);
                    apiResponse.setError(apiErr);
                    return apiResponse;
                }

            } else {
                //get all balances for the pair
                boolean foundNBTavail = false;
                boolean foundPEGavail = false;

                Amount NBTAvail = new Amount(0, pair.getOrderCurrency()),
                        PEGAvail = new Amount(0, pair.getPaymentCurrency());

                Amount PEGonOrder = new Amount(0, pair.getPaymentCurrency()),
                        NBTonOrder = new Amount(0, pair.getOrderCurrency());

                String NBTcode = pair.getOrderCurrency().getCode().toLowerCase();
                String PEGcode = pair.getPaymentCurrency().getCode().toLowerCase();


                for (int i = 0; i < balanceArray.size(); i++) {
                    JSONObject tempElement = (JSONObject) balanceArray.get(i);
                    if (tempElement.containsKey(NBTcode)) {

                        double tempAvailablebalance = Utils.getDouble(tempElement.get(NBTcode));
                        double tempLockedebalance = 0; //Not provided by the API

                        NBTAvail = new Amount(tempAvailablebalance, pair.getOrderCurrency());
                        NBTonOrder = new Amount(tempLockedebalance, pair.getOrderCurrency());

                        foundNBTavail = true;
                    } else if (tempElement.containsKey(PEGcode)) {
                        double tempAvailablebalance = Utils.getDouble(tempElement.get(PEGcode));
                        double tempLockedebalance = 0; //Not provided by the API

                        PEGAvail = new Amount(tempAvailablebalance, pair.getPaymentCurrency());
                        PEGonOrder = new Amount(tempLockedebalance, pair.getPaymentCurrency());

                        foundPEGavail = true;
                    }
                }

                balance = new Balance(PEGAvail, NBTAvail, PEGonOrder, NBTonOrder);

                apiResponse.setResponseObject(balance);
                if (!foundNBTavail || !foundPEGavail) {
                    LOG.warning("Cannot find a balance for currency with code "
                            + "" + NBTcode + " or " + PEGcode + " in your balance. "
                            + "NuBot assumes that balance is 0");
                }
            }
        } catch (ParseException ex) {
            LOG.severe("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }

        return apiResponse;
    }

    @Override
    public ApiResponse getLastPrice(CurrencyPair pair) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ApiResponse sell(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.SELL, pair, amount, rate);
    }

    @Override
    public ApiResponse buy(CurrencyPair pair, double amount, double rate) {
        return enterOrder(Constant.BUY, pair, amount, rate);
    }

    private ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double rate) {
        ApiResponse apiResponse = new ApiResponse();

        String order_id = "";

        /*https://c-cex.com/t/r.html?key=Your_API_Key&a=makeorder&pair=Curr1-Curr2&q=Amount&t=[s/b]&r=Rate
         "t" - order type. t=s - Sell, t=b - Buy
         */

        String typeCode;
        if (type.equals(Constant.BUY)) {
            typeCode = "b";
        } else {
            typeCode = "s";
        }

        String url = baseUrl + "&a=makeorder";
        url += "&pair=" + (pair.toString("-")).toLowerCase();
        url += "&q=" + amount;
        url += "&t=" + typeCode;
        url += "&r=" + rate;


        String queryResult = query(url, new HashMap<String, String>(), true);

        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.startsWith("Access denied")) {
            apiResponse.setError(errors.authenticationError);
            return apiResponse;
        }

        /*Sample result
         *
         *{"return":"not enough funds"}
         *  - OR -
         *{"return":"4677949"}
         */
        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            String ret = (String) httpAnswerJson.get("return");

            if (ret.contains("not enough funds") || ret.contains(" ")) {
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription(ret);
                LOG.severe("CCex API returned an error: " + ret);
                apiResponse.setError(apiErr);
                return apiResponse;
            } else {
                //correct
                order_id = ret;
                apiResponse.setResponseObject(order_id);
            }

        } catch (ParseException ex) {
            LOG.severe("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }
        return apiResponse;
    }

    @Override
    public ApiResponse getActiveOrders() {
        return getOrdersImpl(null);
    }

    @Override
    public ApiResponse getActiveOrders(CurrencyPair pair) {
        return getOrdersImpl(pair);
    }

    public ApiResponse getOrdersImpl(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> orderList = new ArrayList<Order>();
        String url = baseUrl + "&a=orderlist&self=1";
        if (pair != null) {
            url += "&pair=" + (pair.toString("-")).toLowerCase();
        }

        String queryResult = query(url, new HashMap<String, String>(), true);

        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.startsWith("Access denied")) {
            apiResponse.setError(errors.authenticationError);
            return apiResponse;
        }


        /*Sample result
         *
         *{"return":
         *  {"158913":
         *   {"type":"sell","c1":"usd","c2":"btc","amount":0.00064744,"price":520,"self":0},
         *  "158912":
         *      {...}
         *   }
         * }
         *
         */

        try {
            org.json.JSONObject httpAnswerJson = new org.json.JSONObject(queryResult);

            //correct
            org.json.JSONObject dataJson = (org.json.JSONObject) httpAnswerJson.get("return");

            if (dataJson.length() == 0) { //empty order list
                apiResponse.setResponseObject(orderList);
                return apiResponse;
            }
            //Iterate on orders
            String names[] = org.json.JSONObject.getNames(dataJson);
            for (int i = 0; i < names.length; i++) {
                org.json.JSONObject tempJson = dataJson.getJSONObject(names[i]);
                Order tempOrder = parseOrder(tempJson);
                tempOrder.setId(names[i]);

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


        } catch (JSONException ex) {
            LOG.severe("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }

        return apiResponse;
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
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription("Cannot find the order with id " + orderID);
                apiResp.setError(apiErr);
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

        String order_id = "";

        //https://c-cex.com/t/r.html?key=Your_API_Key&a=cancelorder&id=OrderID


        String url = baseUrl + "&a=cancelorder";
        url += "&id=" + orderID;

        String queryResult = query(url, new HashMap<String, String>(), true);

        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.startsWith("Access denied")) {
            apiResponse.setError(errors.authenticationError);
            return apiResponse;
        }

        /*Sample result
         *
         * {"return":"Order 4678299 canceled"}
         *  - OR -
         *{"error":"Order 1228 not found"}
         */


        JSONParser parser = new JSONParser();
        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));

            if (httpAnswerJson.containsKey("error")) {
                ApiError apiErr = errors.apiReturnError;
                apiErr.setDescription((String) httpAnswerJson.get("error"));
                apiResponse.setError(apiErr);
                return apiResponse;
            } else {
                //correct
                apiResponse.setResponseObject(true);
            }
        } catch (ParseException ex) {
            LOG.severe("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
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
        LOG.fine("CCex uses global TX fee, currency pair not supprted. \n"
                + "now calling getTxFee()");
        return getTxFee();
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair) {

        long now = System.currentTimeMillis();
        long yesterday = now - Utils.getOneDayInMillis();

        return getTradesImpl(pair, yesterday);
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime) {
        return getTradesImpl(pair, startTime);
    }

    private ApiResponse getTradesImpl(CurrencyPair pair, long startTime) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Trade> tradeList = new ArrayList<Trade>();

        /*
         * https://c-cex.com/t/r.html?key=Your_API_Key&a=tradehistory&d1=Date_Time_From&d2=Date_Time_To&pair=Curr1-Curr2
         * Example: https://c-cex.com/t/r.html?key=Your_API_Key&a=tradehistory&d1=2014-01-01&d2=2014-02-10&pair=grc-btc
         */

        //Parse the date

        Date date = new Date(startTime);
        Calendar myCal = new GregorianCalendar();
        myCal.setTime(date);
        String formattedStartDate = Integer.toString(myCal.get(Calendar.YEAR))
                + "-" + (myCal.get(Calendar.MONTH) + 1)
                + "-" + myCal.get(Calendar.DAY_OF_MONTH);

        myCal.setTime(new Date()); //now
        String formattedStopDate = Integer.toString(myCal.get(Calendar.YEAR))
                + "-" + (myCal.get(Calendar.MONTH) + 1)
                + "-" + myCal.get(Calendar.DAY_OF_MONTH);

        String url = baseUrl + "&a=tradehistory";
        url += "&d1=" + formattedStartDate;
        url += "&d2=" + formattedStopDate;
        url += "&pair=" + (pair.toString("-")).toLowerCase();

        String queryResult = query(url, new HashMap<String, String>(), true);

        if (queryResult.equals(TOKEN_BAD_RETURN)) {
            apiResponse.setError(errors.nullReturnError);
            return apiResponse;
        }
        if (queryResult.startsWith("Access denied")) {
            apiResponse.setError(errors.authenticationError);
            return apiResponse;
        }

        /*Sample result
         *{"return":
         * [
         *  {"id":"464351","dt":"2014-10-23 13:04:49","type":"Buy","amount":0.04271662,"rate":369,"backrate":0.00271003}
         * ]
         * }
         */
        JSONParser parser = new JSONParser();

        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));

            JSONArray array;
            //correct
            try {
                array = (JSONArray) httpAnswerJson.get("return");
            } catch (Exception e) {
                apiResponse.setResponseObject(tradeList);
                return apiResponse;
            }
            for (int i = 0; i < array.size(); i++) {
                tradeList.add(parseTrade((JSONObject) array.get(i), pair));
            }
            apiResponse.setResponseObject(tradeList);
        } catch (ParseException ex) {
            LOG.severe("httpresponse: " + queryResult + " \n" + ex.toString());
            apiResponse.setError(errors.parseError);
            return apiResponse;
        }

        return apiResponse;
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
            if (err.getDescription().contains("Cannot find the order")) {
                existResponse.setResponseObject(false);

            } else {
                existResponse.setError(err);
            }
        }
        return existResponse;
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

                ApiResponse deleteOrderResponse = cancelOrder(tempOrder.getId(), pair);
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
    public ApiError getErrorByCode(int code) {
        return null;
    }

    @Override
    public String getUrlConnectionCheck() {
        return checkConnectionUrl;
    }

    @Override
    public String query(String url, HashMap<String, String> args, boolean isGet) {
        CcexService query = new CcexService(url);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(true, isGet);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to CCex");
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

    private Order parseOrder(org.json.JSONObject orderObject) {

        Order order = new Order();
        try {
            /*
             "type" - Order type - by/sell
             "c1" - Currency 1
             "c2" - Currency 2
             "amount" - Currency 2 amount
             "price" - Currency rate
             "self" - 1 - self older
             */

            String c1 = orderObject.getString("c1");
            String c2 = orderObject.getString("c2");

            CurrencyPair cp = new CurrencyPair(Currency.createCurrency(c1), Currency.createCurrency(c2));

            order.setPair(cp);
            order.setType((orderObject.getString("type")).toUpperCase());
            order.setAmount(new Amount(orderObject.getDouble("amount"), cp.getOrderCurrency()));
            order.setPrice(new Amount(orderObject.getDouble("price"), cp.getPaymentCurrency()));

            order.setCompleted(false);

            order.setInsertedDate(new Date()); //Not provided

        } catch (JSONException ex) {
            LOG.severe(ex.toString());
        }
        return order;

    }

    private Trade parseTrade(JSONObject tradeObj, CurrencyPair pair) {
        //  {"id":"464351","dt":"2014-10-23 13:04:49","type":"Buy","amount":0.04271662,"rate":369,"backrate":0.00271003}
        Trade trade = new Trade();
        trade.setOrder_id((String) tradeObj.get("id"));

        trade.setExchangeName(Constant.CCEX);
        trade.setPair(pair);

        trade.setType(((String) tradeObj.get("type")).toUpperCase());
        trade.setAmount(new Amount(Utils.getDouble(tradeObj.get("amount")), pair.getPaymentCurrency()));
        trade.setPrice(new Amount(Utils.getDouble(tradeObj.get("rate")), pair.getOrderCurrency()));
        trade.setFee(new Amount(0, pair.getPaymentCurrency()));

        trade.setDate(parseDate((String) tradeObj.get("dt")));

        return trade;
    }

    private Date parseDate(String dateStr) {
        Date toRet = null;
        //Parse the date
        //Sample 2014-02-19 04:55:44

        String datePattern = "yyyy-MM-dd HH:mm:ss";
        DateFormat df = new SimpleDateFormat(datePattern, Locale.ENGLISH);
        try {
            toRet = df.parse(dateStr);
        } catch (java.text.ParseException ex) {
            LOG.severe(ex.toString());
            toRet = new Date();
        }
        return toRet;
    }

    private class CcexService implements ServiceInterface {

        protected ApiKeys keys;
        protected String url;

        private CcexService(String url) {
            //Used for ticker, does not require auth
            this.url = url;
        }

        @Override
        public String executeQuery(boolean needAuth, boolean isGet) {
            String answer = "";

            // add header
            Header[] headers = new Header[1];
            headers[ 0] = new BasicHeader("Content-type", "application/x-www-form-urlencoded");

            URL queryUrl;
            try {
                queryUrl = new URL(url);
            } catch (MalformedURLException ex) {
                LOG.severe(ex.toString());
            }

            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = null;
            HttpGet get = null;
            HttpResponse response = null;


            try {
                get = new HttpGet(url);
                get.setHeaders(headers);
                response = client.execute(get);

            } catch (Exception e) {
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
                Logger.getLogger(BterWrapper.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            } catch (IllegalStateException ex) {
                Logger.getLogger(BterWrapper.class.getName()).log(Level.SEVERE, null, ex);
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
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
