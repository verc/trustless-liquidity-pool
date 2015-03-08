package com.nubits.nubot.trading.wrappers;

import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.*;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.trading.ServiceInterface;
import com.nubits.nubot.trading.Ticker;
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.TradeUtils;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.utils.ErrorManager;
import java.io.*;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
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
 * Created by sammoth on 19/01/15.
 */
public class BitcoinCoIDWrapper implements TradeInterface {

    private static final Logger LOG = Logger.getLogger(BitcoinCoIDWrapper.class.getName());
    //Class fields
    private ApiKeys keys;
    private Exchange exchange;
    private final String SIGN_HASH_FUNCTION = "HmacSHA512";
    private final String ENCODING = "UTF-8";
    //API Paths
    private final String API_BASE_URL = "https://vip.bitcoin.co.id/tapi";
    private final String API_TICKER_URL = "https://vip.bitcoin.co.id/api/";
    private final String API_TICKER = "ticker";
    private final String API_GET_INFO = "getInfo";
    private final String API_TRADE = "trade";
    private final String API_OPEN_ORDERS = "openOrders";
    private final String API_CANCEL_ORDER = "cancelOrder";
    private final String API_TRADE_HISTORY = "tradeHistory";
    //Errors
    private ErrorManager errors = new ErrorManager();
    private final String TOKEN_ERR = "error";
    private final String TOKEN_BAD_RETURN = "No Connection With Exchange";
    private final String TOKEN_CODE = "success";

    public BitcoinCoIDWrapper() {
        setupErrors();
    }

    public BitcoinCoIDWrapper(ApiKeys keys, Exchange exchange) {
        this.keys = keys;
        this.exchange = exchange;
        setupErrors();
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
        JSONParser parser = new JSONParser();

        try {
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(queryResult));
            int code = 0;
            try {
                code = Integer.parseInt(httpAnswerJson.get(TOKEN_CODE).toString());
            } catch (ClassCastException cce) {
                apiResponse.setError(errors.genericError);
            }

            if (code == 0) {
                String errorMessage = (String) httpAnswerJson.get(TOKEN_ERR);
                ApiError apiError = errors.apiReturnError;
                apiError.setDescription(errorMessage);
                //LOG.severe("AllCoin API returned an error : " + errorMessage);
                apiResponse.setError(apiError);
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
        } catch (ParseException pe) {
            LOG.severe("httpResponse: " + queryResult + " \n" + pe.toString());
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
        boolean isGet = false;
        String url = API_BASE_URL;
        String method = API_GET_INFO;
        HashMap<String, String> query_args = new HashMap<>();

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject data = (JSONObject) httpAnswerJson.get("return");
            JSONObject balances = (JSONObject) data.get("balance");
            if (currency == null) { //get the balances for the pair
                double pegAvail = Double.parseDouble(balances.get(pair.getPaymentCurrency().getCode().toLowerCase()).toString());
                Amount PEGAvail = new Amount(pegAvail, pair.getPaymentCurrency());
                double nbtAvail = Double.parseDouble(balances.get(pair.getOrderCurrency().getCode().toLowerCase()).toString());
                Amount NBTAvail = new Amount(nbtAvail, pair.getOrderCurrency());
                double pegOnOrder = 0;
                double nbtOnOrder = 0;
                ArrayList<Order> orders = (ArrayList) getActiveOrders(pair).getResponseObject();
                for (Iterator<Order> order = orders.iterator(); order.hasNext();) {
                    Order thisOrder = order.next();
                    if (thisOrder.getType().equals(Constant.SELL)) {
                        nbtOnOrder += thisOrder.getAmount().getQuantity();
                    } else {
                        pegOnOrder += thisOrder.getAmount().getQuantity();
                    }
                }
                Amount PEGonOrder = new Amount(pegOnOrder, pair.getPaymentCurrency());
                Amount NBTonOrder = new Amount(nbtOnOrder, pair.getOrderCurrency());
                Balance balance = new Balance(PEGAvail, NBTAvail, PEGonOrder, NBTonOrder);
                apiResponse.setResponseObject(balance);
            } else {
                double balance = Double.parseDouble(balances.get(currency.getCode().toLowerCase()).toString());
                Amount total = new Amount(balance, currency);
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
        String url = API_TICKER_URL + pair.toString("_") + "/" + API_TICKER;
        HashMap<String, String> query_args = new HashMap<>();
        boolean isGet = true;

        double last = -1;
        double ask = -1;
        double bid = -1;
        Ticker ticker = new Ticker();

        String queryResult = query(url, query_args, isGet);
        JSONParser parser = new JSONParser();
        JSONObject httpAnswerJson = null;
        try {
            httpAnswerJson = (JSONObject) parser.parse(queryResult);
        } catch (ParseException pe) {
            LOG.severe(pe.toString());
            ApiError error = errors.apiReturnError;
            error.setDescription("Error parsing ticker response");
            apiResponse.setError(error);
            return apiResponse;
        }
        JSONObject tick = (JSONObject) httpAnswerJson.get("ticker");
        last = Double.parseDouble(tick.get("last").toString());
        ask = Double.parseDouble(tick.get("sell").toString());
        bid = Double.parseDouble(tick.get("buy").toString());
        ticker.setLast(last);
        ticker.setAsk(ask);
        ticker.setBid(bid);
        apiResponse.setResponseObject(ticker);
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

    private ApiResponse enterOrder(String type, CurrencyPair pair, double amount, double price) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        String method = API_TRADE;
        HashMap<String, String> args = new HashMap<>();
        boolean isGet = false;
        String order_id = null;

        args.put("pair", pair.toString("_"));
        args.put("type", type.toLowerCase());
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumFractionDigits(8);
        args.put("price", nf.format(price));
        if (type.equals(Constant.SELL)) {
            args.put(pair.getPaymentCurrency().getCode().toLowerCase(), nf.format(amount * price));
            args.put(pair.getOrderCurrency().getCode().toLowerCase(), nf.format(amount));
        } else {
            args.put(pair.getPaymentCurrency().getCode().toLowerCase(), nf.format(amount * price));
        }

        ApiResponse response = getQuery(url, method, args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject data = (JSONObject) httpAnswerJson.get("return");
            order_id = data.get("order_id").toString();
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
        String url = API_BASE_URL;
        String method = API_OPEN_ORDERS;
        ArrayList<Order> orderList = new ArrayList<>();
        HashMap<String, String> query_args = new HashMap<>();
        boolean isGet = false;


        //only handles openOrders with given pair

        if (pair != null) {
            query_args.put("pair", pair.toString("_"));
        } else {
            pair = Global.options.getPair();
            query_args.put("pair", pair.toString("_"));
        }

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject data = (JSONObject) httpAnswerJson.get("return");
            JSONArray orders = (JSONArray) data.get("orders");
            if (orders != null) {
                for (Iterator<JSONObject> order = orders.iterator(); order.hasNext();) {
                    JSONObject thisOrder = order.next();
                    orderList.add(parseOrder(thisOrder, pair));
                }
            }
            apiResponse.setResponseObject(orderList);
        } else {
            apiResponse = response;
        }
        return apiResponse;
    }

    private Order parseOrder(JSONObject in, CurrencyPair cp) {
        Order out = new Order();

        out.setId(in.get("order_id").toString());
        Date insertedDate = new Date(Long.parseLong(in.get("submit_time").toString()) * 1000L);
        out.setInsertedDate(insertedDate);
        out.setType(in.get("type").toString().equals("buy") ? Constant.BUY : Constant.SELL);
        String cur;
        Amount amount;
        Amount price = new Amount(Double.parseDouble(in.get("price").toString()), Global.options.getPair().getPaymentCurrency());
        if (out.getType().equals(Constant.BUY)) {
            cur = Global.options.getPair().getPaymentCurrency().getCode().toLowerCase();
            amount = new Amount(Double.parseDouble(in.get("order_" + cur).toString()) / price.getQuantity(), Global.options.getPair().getOrderCurrency());
        } else {
            cur = Global.options.getPair().getOrderCurrency().getCode().toLowerCase();
            amount = new Amount(Double.parseDouble(in.get("order_" + cur).toString()), Global.options.getPair().getOrderCurrency());
        }
        out.setPair(cp);

        out.setAmount(amount);
        out.setPrice(price);
        out.setCompleted(amount.getQuantity() == Double.parseDouble(in.get("remain_" + cur).toString()));

        return out;
    }

    @Override
    public ApiResponse getOrderDetail(String orderID) {
        ApiResponse apiResponse = new ApiResponse();
        ArrayList<Order> activeOrders = (ArrayList<Order>) getActiveOrders().getResponseObject();
        boolean found = false;
        for (Iterator<Order> order = activeOrders.iterator(); order.hasNext();) {
            Order thisOrder = order.next();
            if (thisOrder.getId().equals(orderID)) {
                found = true;
                apiResponse.setResponseObject(thisOrder);
                break;
            }
        }

        if (!found) {
            ApiError err = errors.apiReturnError;
            err.setDescription("Order " + orderID + " does not exists");
            apiResponse.setError(err);
        }
        return apiResponse;
    }

    @Override
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        String method = API_CANCEL_ORDER;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();

        query_args.put("pair", pair.toString("_"));
        query_args.put("order_id", orderID);
        Order currentOrder = (Order) getOrderDetail(orderID).getResponseObject();
        query_args.put("type", currentOrder.getType().toLowerCase());

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject data = (JSONObject) httpAnswerJson.get("return");
            if (data.get("order_id").toString().equals(orderID)) {
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
        double defaultFee = 0.0;
        return new ApiResponse(true, defaultFee, null);
    }

    @Override
    public ApiResponse getTxFee(CurrencyPair pair) {
        double defaultFee = 0.0;
        return new ApiResponse(true, defaultFee, null);
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair) {
        return getLastTradesImpl(pair, 0);
    }

    @Override
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime) {
        return getLastTradesImpl(pair, startTime);
    }

    private ApiResponse getLastTradesImpl(CurrencyPair pair, long startTime) {
        ApiResponse apiResponse = new ApiResponse();
        String url = API_BASE_URL;
        String method = API_TRADE_HISTORY;
        boolean isGet = false;
        HashMap<String, String> query_args = new HashMap<>();
        ArrayList<Trade> tradeList = new ArrayList<>();

        query_args.put("pair", pair.toString("_"));
        if (startTime > 0) {
            query_args.put("since", Objects.toString(startTime));
        }

        ApiResponse response = getQuery(url, method, query_args, isGet);
        if (response.isPositive()) {
            JSONObject httpAnswerJson = (JSONObject) response.getResponseObject();
            JSONObject data = (JSONObject) httpAnswerJson.get("return");
            JSONArray trades = (JSONArray) data.get("trades");
            for (Iterator<JSONObject> trade = trades.iterator(); trade.hasNext();) {
                JSONObject thisTrade = trade.next();
                tradeList.add(parseTrade(thisTrade, pair));
            }
            apiResponse.setResponseObject(tradeList);
        } else {
            apiResponse = response;
        }


        return apiResponse;
    }

    private Trade parseTrade(JSONObject in, CurrencyPair pair) {
        Trade out = new Trade();

        out.setId(in.get("trade_id").toString());
        out.setType(in.get("type").toString().equals("buy") ? Constant.BUY : Constant.SELL);
        Date tradeDate = new Date(Long.parseLong(in.get("trade_time").toString()) * 1000L);
        out.setDate(tradeDate);
        out.setExchangeName(Global.exchange.getName());
        Amount fee = new Amount(Double.parseDouble(in.get("fee").toString()), pair.getPaymentCurrency());
        out.setFee(fee);
        Amount amount = new Amount(Double.parseDouble(in.get(pair.getOrderCurrency().getCode().toLowerCase()).toString()), pair.getOrderCurrency());
        out.setAmount(amount);
        Amount price = new Amount(Double.parseDouble(in.get("price").toString()), pair.getPaymentCurrency());
        out.setPrice(price);
        out.setPair(pair);

        return out;
    }

    @Override
    public ApiResponse isOrderActive(String id) {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse = getOrderDetail(id);
        if (apiResponse.isPositive()) {
            Order order = (Order) apiResponse.getResponseObject();
            if (order.isCompleted()) {
                apiResponse.setResponseObject(true);
            } else {
                apiResponse.setResponseObject(false);
            }
        } else {
            apiResponse.setResponseObject(false);
        }
        return apiResponse;
    }

    @Override
    public ApiResponse clearOrders(CurrencyPair pair) {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setResponseObject(true);
        ApiResponse getOrders = getActiveOrders();

        if (getOrders.isPositive()) {
            ArrayList<Order> orders = (ArrayList) getOrders.getResponseObject();
            for (Iterator<Order> order = orders.iterator(); order.hasNext();) {
                if (!(boolean) cancelOrder(order.next().getId(), pair).getResponseObject()) {
                    apiResponse.setResponseObject(false);
                }
            }
        } else {
            apiResponse = getOrders;
        }

        return apiResponse;
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
        BitcoinCoIdService query = new BitcoinCoIdService(url, args);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(false, isGet);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to BitcoinCoId");
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
    }

    @Override
    public String query(String base, String method, HashMap<String, String> args, boolean isGet) {
        BitcoinCoIdService query = new BitcoinCoIdService(base, method, args, keys);
        String queryResult;
        if (exchange.getLiveData().isConnected()) {
            queryResult = query.executeQuery(true, isGet);
        } else {
            LOG.severe("The bot will not execute the query, there is no connection to BitcoinCoId");
            queryResult = TOKEN_BAD_RETURN;
        }
        return queryResult;
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
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private class BitcoinCoIdService implements ServiceInterface {

        protected String url;
        protected HashMap args;
        protected ApiKeys keys;
        protected String method;

        public BitcoinCoIdService(String url, String method, HashMap<String, String> args, ApiKeys keys) {
            this.url = url;
            this.args = args;
            this.keys = keys;
            this.method = method;
        }

        private BitcoinCoIdService(String url, HashMap<String, String> args) {
            //Used for ticker, does not require auth
            this.url = url;
            this.args = args;
        }

        @Override
        public String executeQuery(boolean needAuth, boolean isGet) {
            HttpsURLConnection connection = null;
            URL queryUrl = null;
            String post_data = "";
            boolean httpError = false;
            String output;
            int response = 200;
            String answer = null;

            try {
                queryUrl = new URL(url);
            } catch (MalformedURLException mal) {
                LOG.severe(mal.toString());
                return null;
            }

            if (needAuth) {
                args.put("nonce", Objects.toString(System.currentTimeMillis()));
                args.put("method", method);
                post_data = TradeUtils.buildQueryString(args, ENCODING);
            }

            try {
                connection = (HttpsURLConnection) queryUrl.openConnection();
                connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("User-Agent", Global.settings.getProperty("app_name"));

                if (needAuth) {
                    connection.setRequestProperty("Key", keys.getApiKey());
                    connection.setRequestProperty("Sign", signRequest(keys.getPrivateKey(), post_data));
                }

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
                return null;
            } catch (IOException io) {
                LOG.severe((io.toString()));
                return null;
            }


            BufferedReader br = null;
            try {
                if (connection.getResponseCode() >= 400) {
                    httpError = true;
                    response = connection.getResponseCode();
                    br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                } else {
                    answer = "";
                    br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                }
            } catch (IOException io) {
                LOG.severe(io.toString());
                return null;
            }

            if (httpError) {
                LOG.severe("Query to : " + url + " (method = " + method + " )"
                        + "\nData : \" + post_data"
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
