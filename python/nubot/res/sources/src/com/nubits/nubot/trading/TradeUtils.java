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
package com.nubits.nubot.trading;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.Order;
import com.nubits.nubot.utils.Utils;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class TradeUtils {

    private static final Logger LOG = Logger.getLogger(TradeUtils.class.getName());

    public static boolean tryCancelAllOrders(CurrencyPair pair) {
        boolean toRet = false;
        //get all orders
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders(Global.options.getPair());
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();

            if (orderList.size() == 0) {
                toRet = true;
            } else {
                LOG.info("There are still : " + orderList.size() + " active orders");
                //Retry to cancel them to fix issue #14
                ApiResponse deleteOrdersResponse = Global.exchange.getTrade().clearOrders(pair);
                if (deleteOrdersResponse.isPositive()) {
                    boolean deleted = (boolean) deleteOrdersResponse.getResponseObject();

                    if (deleted) {
                        LOG.info("Order clear request succesfully");
                    } else {
                        toRet = false;
                        LOG.info("Could not submit request to clear orders");
                    }
                } else {
                    toRet = false;
                    LOG.severe(deleteOrdersResponse.getError().toString());
                }
            }
        } else {
            toRet = false;

            LOG.severe(activeOrdersResponse.getError().toString());
        }

        return toRet;
    }

    public static boolean takeDownOrders(String type, CurrencyPair pair) {
        boolean completed = true;
        //Get active orders
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders(Global.options.getPair());
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();

            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                if (tempOrder.getType().equalsIgnoreCase(type)) {
                    boolean tempDeleted = takeDownAndWait(tempOrder.getId(), 120 * 1000, pair);
                    if (!tempDeleted) {
                        completed = false;
                    }
                }
            }
        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
            return false;
        }
        return completed;
    }

    public static boolean takeDownAndWait(String orderID, long timeoutMS, CurrencyPair pair) {

        ApiResponse deleteOrderResponse = Global.exchange.getTrade().cancelOrder(orderID, pair);
        if (deleteOrderResponse.isPositive()) {
            boolean delRequested = (boolean) deleteOrderResponse.getResponseObject();

            if (delRequested) {
                LOG.warning("Order " + orderID + " delete request submitted");
            } else {
                LOG.severe("Could not submit request to delete order" + orderID);
            }

        } else {
            LOG.severe(deleteOrderResponse.getError().toString());
        }


        //Wait until the order is deleted or timeout
        boolean timedout = false;
        boolean deleted = false;
        long wait = 6 * 1000;
        long count = 0L;
        do {
            try {
                Thread.sleep(wait);
                count += wait;
                timedout = count > timeoutMS;

                ApiResponse orderDetailResponse = Global.exchange.getTrade().isOrderActive(orderID);
                if (orderDetailResponse.isPositive()) {
                    deleted = !((boolean) orderDetailResponse.getResponseObject());
                    LOG.fine("Does order " + orderID + "  still exist?" + !deleted);
                } else {
                    LOG.severe(orderDetailResponse.getError().toString());
                    return false;
                }
            } catch (InterruptedException ex) {
                LOG.severe(ex.toString());
                return false;
            }
        } while (!deleted && !timedout);

        if (timedout) {
            return false;
        }
        return true;
    }

    public static double getSellPrice(double txFee) {
        if (Global.isDualSide) {
            return 1 + (0.01 * txFee);
        } else {
            return 1 + (0.01 * txFee) + Global.options.getPriceIncrement();
        }

    }

    public static double getBuyPrice(double txFeeUSDNTB) {
        return 1 - (0.01 * txFeeUSDNTB);
    }

    public static ArrayList<Order> filterOrders(ArrayList<Order> originalList, String type) {
        ArrayList<Order> toRet = new ArrayList<>();
        for (int i = 0; i < originalList.size(); i++) {
            Order temp = originalList.get(i);
            if (temp.getType().equalsIgnoreCase(type)) {
                toRet.add(temp);
            }
        }

        return toRet;
    }
    //Build the query string given a set of query parameters

    /**
     *
     * @param args
     * @param encoding
     * @return
     */
    public static String buildQueryString(HashMap<String, String> args, String encoding) {
        String result = new String();
        for (String hashkey : args.keySet()) {
            if (result.length() > 0) {
                result += '&';
            }
            try {
                result += URLEncoder.encode(hashkey, encoding) + "="
                        + URLEncoder.encode(args.get(hashkey), encoding);
            } catch (Exception ex) {
                LOG.severe(ex.toString());
            }
        }
        return result;
    }

    public static String buildQueryString(TreeMap<String, String> args, String encoding) {
        String result = new String();
        for (String hashkey : args.keySet()) {
            if (result.length() > 0) {
                result += '&';
            }
            try {
                result += URLEncoder.encode(hashkey, encoding) + "="
                        + URLEncoder.encode(args.get(hashkey), encoding);
            } catch (Exception ex) {
                LOG.severe(ex.toString());
            }
        }
        return result;
    }
    //The two methods below have been amalgamated into the CCDEK wrapper
    //TODO - get rid of these ccedk =methods once testing has taken place
    public static int offset = 0;

    public static String getCCDKEvalidNonce() {
        //It tries to send a wrong nonce, get the allowed window, and use it for the actual call
        String wrongNonce = "1234567891";
        String lastdigits;
        //LOG.info("Offset = " + Objects.toString(offset));
        String validNonce;
        if (offset == 0) {
            try {
                String htmlString = Utils.getHTML("https://www.ccedk.com/api/v1/currency/list?nonce=" + wrongNonce, false);
                //LOG.info(htmlString);
                //LOG.info(Objects.toString(System.currentTimeMillis() / 1000L));
                validNonce = getCCDKEvalidNonce(htmlString);
                offset = Integer.parseInt(validNonce) - (int) (System.currentTimeMillis() / 1000L);
                //LOG.info("Offset = " + Objects.toString(offset));
            } catch (IOException io) {
                //LOG.info(io.toString());
                validNonce = "";
            }
        } else {
            validNonce = Objects.toString(((int) (System.currentTimeMillis() / 1000L) + offset) - 1);
        }
        if (!validNonce.equals("")) {
            lastdigits = validNonce.substring(validNonce.length() - 2);
            if (lastdigits.equals("98") || lastdigits.equals("99")) {
                offset = 0;
                validNonce = getCCDKEvalidNonce();
            }
        } else {
            offset = 0;
            validNonce = getCCDKEvalidNonce();
        }
        //LOG.info("Last digits = " + lastdigits + "\nvalidNonce = " + validNonce);
        return validNonce;
    }

    //used by ccedkqueryservice
    public static String getCCDKEvalidNonce(String htmlString) {

        JSONParser parser = new JSONParser();
        try {
            //{"errors":{"nonce":"incorrect range `nonce`=`1234567891`, must be from `1411036100` till `1411036141`"}
            JSONObject httpAnswerJson = (JSONObject) (parser.parse(htmlString));
            JSONObject errors = (JSONObject) httpAnswerJson.get("errors");
            String nonceError = (String) errors.get("nonce");

            //String startStr = " must be from";
            //int indexStart = nonceError.lastIndexOf(startStr) + startStr.length() + 2;
            //String from = nonceError.substring(indexStart, indexStart + 10);


            String startStr2 = " till";
            int indexStart2 = nonceError.lastIndexOf(startStr2) + startStr2.length() + 2;
            String to = nonceError.substring(indexStart2, indexStart2 + 10);

            //if (to.equals(from)) {
            //    LOG.info("Detected ! " + to + " = " + from);
            //    return "retry";
            //}

            return to;
        } catch (ParseException ex) {
            LOG.severe(htmlString + " " + ex.toString());
            return "1234567891";
        }
    }

    public static int getCCDKECurrencyId(String currencyCode) {
        /*
         * LAST UPDATED : 17 september
         * FROM : https://www.ccedk.com/api/v1/currency/list?nonce=1410950600
         * 1,LTC
         * 2,BTC
         * 3,USD
         * 4, EUR
         * 8,PPC
         * 15, NBT

         */
        final String BTC = Constant.BTC.getCode();
        String USD = Constant.USD.getCode();
        String PPC = Constant.PPC.getCode();

        int toRet = -1;

        switch (currencyCode) {
            case "BTC":
                toRet = 2;
                break;
            case "USD":
                toRet = 3;
                break;
            case "PPC":
                toRet = 8;
                break;
            case "LTC":
                toRet = 1;
                break;
            case "NBT":
                toRet = 15;
                break;
            case "EUR":
                toRet = 4;
                break;
            default:
                LOG.severe("Currency " + currencyCode + "not available");
                break;
        }
        return toRet;
    }

    public static int getCCDKECurrencyPairId(CurrencyPair pair) {
        /*
         * LAST UPDATED : 20 september
         FROM : https://www.ccedk.com/api/v1/pair/list?nonce=1410950600
         * 2 ,BTC USD
         * 14 , PPC USD
         * 13 , PPC BTC
         * 12 , PPC LTC
         * 47, NBT BTC
         * 48, NBT PPC
         * 46, NBT USD
         * 49, NBT EUR
         */

        int toRet = -1;

        if (pair.equals(Constant.BTC_USD)) {
            return 2;
        } else if (pair.equals(Constant.PPC_USD)) {
            return 14;
        } else if (pair.equals(Constant.PPC_BTC)) {
            return 13;
        } else if (pair.equals(Constant.PPC_LTC)) {
            return 12;
        } else if (pair.equals(Constant.NBT_BTC)) {
            return 47;
        } else if (pair.equals(Constant.NBT_PPC)) {
            return 48;
        } else if (pair.equals(Constant.NBT_USD)) {
            return 46;
        } else if (pair.equals(Constant.NBT_EUR)) {
            return 49;
        } else {
            LOG.severe("Pair " + pair.toString() + " not available");
        }

        return toRet;
    }

    public static CurrencyPair getCCEDKPairFromID(int id) {
        /*
         * LAST UPDATED : 20 september
         FROM : https://www.ccedk.com/api/v1/pair/list?nonce=1410950600
         * 2 ,BTC USD
         * 14 , PPC USD
         * 13 , PPC BTC
         * 12 , PPC LTC
         * 47, NBT BTC
         * 48, NBT PPC
         * 46, NBT USD
         * 49, NBT EUR
         */
        CurrencyPair toRet = new CurrencyPair(Constant.BTC, Constant.BTC);

        switch (id) {
            case 2:
                toRet = Constant.BTC_USD;
                break;
            case 12:
                toRet = Constant.PPC_LTC;
                break;
            case 13:
                toRet = Constant.PPC_BTC;
                break;
            case 14:
                toRet = Constant.PPC_USD;
                break;
            case 46:
                toRet = Constant.NBT_USD;
                break;
            case 47:
                toRet = Constant.NBT_BTC;
                break;
            case 48:
                toRet = Constant.NBT_PPC;
                break;
            case 49:
                toRet = Constant.NBT_EUR;
                break;
            default:
                LOG.severe("Pair with id = " + id + " not available");

        }
        return toRet;

    }

    public static String getCCEDKTickerUrl(CurrencyPair pair) {
        String nonce = TradeUtils.getCCDKEvalidNonce();
        return "https://www.ccedk.com/api/v1/stats/marketdepth?nonce=" + nonce + "&pair_id=" + getCCDKECurrencyPairId(pair);
    }
}
