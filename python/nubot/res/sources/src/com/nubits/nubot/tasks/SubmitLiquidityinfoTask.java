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
package com.nubits.nubot.tasks;

//import com.alibaba.fastjson.JSON;
//import com.alibaba.fastjson.JSONArray;
import com.nubits.nubot.RPC.NuRPCClient;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.Balance;
import com.nubits.nubot.models.Order;
import com.nubits.nubot.utils.FileSystem;
import com.nubits.nubot.utils.Utils;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class SubmitLiquidityinfoTask extends TimerTask {

    private static final Logger LOG = Logger.getLogger(SubmitLiquidityinfoTask.class.getName());
    private boolean verbose;
    private String outputFile_orders;
    private String jsonFile_orders;
    private String jsonFile_balances;
    private boolean wallsBeingShifted = false;
    private boolean firstOrdersPlaced = false;

    public SubmitLiquidityinfoTask(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public void run() {
        LOG.fine("Executing task : CheckOrdersTask ");
        checkOrders();
    }
    //Taken the input exchange, updates it and returns it.

    private void checkOrders() {
        if (!isWallsBeingShifted()) { //Do not report liquidity info during wall shifts (issue #23)
            if (isFirstOrdersPlaced()) {
                String response1 = reportTier1(); //active orders
                String response2 = reportTier2(); //balance
                LOG.info(response1 + "\n" + response2);
            } else {
                LOG.warning("Liquidity is not being sent : orders are not yet initialized");

            }
        } else {
            LOG.warning("Liquidity is not being sent, a wall shift is happening. Will send on next execution.");
        }
    }

    private String reportTier1() {
        String toReturn = "";

        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders(Global.options.getPair());
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();

            LOG.fine("Active orders : " + orderList.size());

            if (verbose) {
                LOG.info(Global.exchange.getName() + "OLD NBTonbuy  : " + Global.exchange.getLiveData().getNBTonbuy());
                LOG.info(Global.exchange.getName() + "OLD NBTonsell  : " + Global.exchange.getLiveData().getNBTonsell());
            }

            double nbt_onsell = 0;
            double nbt_onbuy = 0;
            int sells = 0;
            int buys = 0;
            String digest = "";
            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                digest = digest + tempOrder.getDigest();
                double toAdd = tempOrder.getAmount().getQuantity();
                if (verbose) {
                    LOG.fine(tempOrder.toString());
                }

                if (tempOrder.getType().equalsIgnoreCase(Constant.SELL)) {
                    //Start summing up amounts of NBT
                    nbt_onsell += toAdd;
                    sells++;
                } else if (tempOrder.getType().equalsIgnoreCase(Constant.BUY)) {
                    //Start summing up amounts of NBT
                    nbt_onbuy += toAdd;
                    buys++;
                }
            }
            //Update the order
            Global.exchange.getLiveData().setOrdersList(orderList);

            if (Global.conversion != 1
                    && Global.swappedPair) {  //For swapped pair, need to convert the amounts to NBT
                nbt_onbuy = nbt_onbuy * Global.conversion;
                nbt_onsell = nbt_onsell * Global.conversion;
            }


            Global.exchange.getLiveData().setNBTonbuy(nbt_onbuy);
            Global.exchange.getLiveData().setNBTonsell(nbt_onsell);

            //Write to file timestamp,activeOrders, sells,buys, digest
            Date timeStamp = new Date();
            String timeStampString = timeStamp.toString();
            Long timeStampLong = Utils.getTimestampLong();
            String toWrite = timeStampString + " , " + orderList.size() + " , " + sells + " , " + buys + " , " + digest;
            FileSystem.writeToFile(toWrite, outputFile_orders, true);

            //Also update a json version of the output file
            //build the latest data into a JSONObject
            JSONObject latestOrders = new JSONObject();
            latestOrders.put("time_stamp", timeStampLong);
            latestOrders.put("active_orders", orderList.size());
            JSONArray jsonDigest = new JSONArray();
            for (Iterator<Order> order = orderList.iterator(); order.hasNext();) {

                JSONObject thisOrder = new JSONObject();
                Order _order = order.next();

                //issue 160 - convert all amounts in NBT

                double amount = _order.getAmount().getQuantity();
                //special case: swapped pair
                if (Global.conversion != 1) {
                    if (Global.swappedPair)//For swapped pair, need to convert the amounts to NBT
                    {
                        amount = _order.getAmount().getQuantity() * Global.conversion;
                    }
                }

                thisOrder.put("order_id", _order.getId());
                thisOrder.put("time", _order.getInsertedDate().getTime());
                thisOrder.put("order_type", _order.getType());
                thisOrder.put("order_currency", _order.getPair().getOrderCurrency().getCode());
                thisOrder.put("amount", amount);
                thisOrder.put("payment_currency", _order.getPair().getPaymentCurrency().getCode());
                thisOrder.put("price", _order.getPrice().getQuantity());
                jsonDigest.add(thisOrder);
            }
            latestOrders.put("digest", jsonDigest);


            //now read the existing object if one exists
            JSONParser parser = new JSONParser();
            JSONObject orderHistory = new JSONObject();
            JSONArray orders = new JSONArray();
            try { //object already exists in file
                orderHistory = (JSONObject) parser.parse(FileSystem.readFromFile(this.jsonFile_orders));
                orders = (JSONArray) orderHistory.get("orders");
            } catch (ParseException pe) {
                LOG.severe("Unable to parse " + this.jsonFile_orders);
            }
            //add the latest orders to the orders array
            orders.add(latestOrders);
            //then save
            FileSystem.writeToFile(orderHistory.toJSONString(), jsonFile_orders, false);

            if (verbose) {
                LOG.info(Global.exchange.getName() + "Updated NBTonbuy  : " + nbt_onbuy);
                LOG.info(Global.exchange.getName() + "Updated NBTonsell  : " + nbt_onsell);
            }

            if (Global.options.isSendRPC()) {
                //Call RPC

                double buySide;
                double sellSide;

                if (!Global.swappedPair) {
                    buySide = Global.exchange.getLiveData().getNBTonbuy();
                    sellSide = Global.exchange.getLiveData().getNBTonsell();
                } else {
                    buySide = Global.exchange.getLiveData().getNBTonsell();
                    sellSide = Global.exchange.getLiveData().getNBTonbuy();
                }

                toReturn = sendLiquidityInfoImpl(buySide, sellSide, 1);
            }

        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
        }
        return toReturn;

    }

    private String reportTier2() {
        String toReturn = "";
        ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalances(Global.options.getPair());
        if (balancesResponse.isPositive()) {
            Balance balance = (Balance) balancesResponse.getResponseObject();

            Amount NBTbalance = balance.getNBTAvailable();
            Amount PEGbalance = balance.getPEGAvailableBalance();

            double buyside = PEGbalance.getQuantity();
            double sellside = NBTbalance.getQuantity();

            //Log balances
            JSONObject latestBalances = new JSONObject();
            latestBalances.put("time_stamp", Utils.getTimestampLong());

            JSONArray availableBalancesArray = new JSONArray();
            JSONObject NBTBalanceJSON = new JSONObject();
            NBTBalanceJSON.put("amount", sellside);
            NBTBalanceJSON.put("currency", NBTbalance.getCurrency().getCode().toUpperCase());

            JSONObject PEGBalanceJSON = new JSONObject();
            PEGBalanceJSON.put("amount", buyside);
            PEGBalanceJSON.put("currency", PEGbalance.getCurrency().getCode().toUpperCase());

            availableBalancesArray.add(PEGBalanceJSON);
            availableBalancesArray.add(NBTBalanceJSON);

            latestBalances.put("balance-not-on-order", availableBalancesArray);

            //now read the existing object if one exists
            JSONParser parser = new JSONParser();
            JSONObject balanceHistory = new JSONObject();
            JSONArray balances = new JSONArray();
            try { //object already exists in file
                balanceHistory = (JSONObject) parser.parse(FileSystem.readFromFile(this.jsonFile_balances));
                balances = (JSONArray) balanceHistory.get("balances");
            } catch (ParseException pe) {
                LOG.severe("Unable to parse " + this.jsonFile_balances);
            }
            //add the latest orders to the orders array
            balances.add(latestBalances);
            //then save
            FileSystem.writeToFile(balanceHistory.toJSONString(), jsonFile_balances, false);

            buyside = Utils.round(buyside * Global.conversion, 2);

            if (Global.options.isSendRPC()) {
                //Call RPC
                toReturn = sendLiquidityInfoImpl(buyside, sellside, 2);
            }


        } else {
            LOG.severe(balancesResponse.getError().toString());
        }
        return toReturn;
    }

    private String sendLiquidityInfoImpl(double buySide, double sellSide, int tier) {
        String toReturn = "";
        if (Global.rpcClient.isConnected()) {
            JSONObject responseObject;


            responseObject = Global.rpcClient.submitLiquidityInfo(Global.rpcClient.USDchar,
                    buySide, sellSide, tier);

            toReturn = "buy : " + buySide + " sell : " + sellSide + " tier: " + tier + " response: " + responseObject.toJSONString();
            if (null == responseObject) {
                LOG.severe("Something went wrong while sending liquidityinfo");
            } else {
                LOG.fine(responseObject.toJSONString());
                if ((boolean) responseObject.get("submitted")) {
                    LOG.fine("RPC Liquidityinfo sent : "
                            + "\nbuyside : " + buySide
                            + "\nsellside : " + sellSide);
                    if (verbose) {
                        JSONObject infoObject = Global.rpcClient.getLiquidityInfo(NuRPCClient.USDchar);
                        LOG.info("getliquidityinfo result : ");
                        LOG.info(infoObject.toJSONString());
                    }
                }
            }
        } else {
            LOG.severe("Client offline. ");
        }
        return toReturn;
    }

    public void setOutputFiles(String outputFileOrders, String outputFileBalances) {
        this.outputFile_orders = outputFileOrders;
        this.jsonFile_orders = this.outputFile_orders.replace(".csv", ".json");



        //create json file if it doesn't already exist
        File jsonF1 = new File(this.jsonFile_orders);
        if (!jsonF1.exists()) {
            JSONObject history = new JSONObject();
            JSONArray orders = new JSONArray();
            history.put("orders", orders);
            FileSystem.writeToFile(history.toJSONString(), this.jsonFile_orders, true);
        }

        this.jsonFile_balances = outputFileBalances;

        //create json file if it doesn't already exist
        File jsonF2 = new File(this.jsonFile_balances);
        if (!jsonF2.exists()) {
            JSONObject history = new JSONObject();
            JSONArray balances = new JSONArray();
            history.put("balances", balances);
            FileSystem.writeToFile(history.toJSONString(), this.jsonFile_balances, true);
        }
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isWallsBeingShifted() {
        return wallsBeingShifted;
    }

    public void setWallsBeingShifted(boolean wallsBeingShifted) {
        this.wallsBeingShifted = wallsBeingShifted;
    }

    public boolean isFirstOrdersPlaced() {
        return firstOrdersPlaced;
    }

    public void setFirstOrdersPlaced(boolean firstOrdersPlaced) {
        this.firstOrdersPlaced = firstOrdersPlaced;
    }
}
