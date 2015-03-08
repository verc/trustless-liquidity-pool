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
package com.nubits.nubot.tasks.strategy;

import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.Balance;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.models.Order;
import com.nubits.nubot.notifications.HipChatNotifications;
import com.nubits.nubot.notifications.MailNotifications;
import com.nubits.nubot.notifications.jhipchat.messages.Message;
import com.nubits.nubot.notifications.jhipchat.messages.Message.Color;
import com.nubits.nubot.trading.TradeUtils;
import com.nubits.nubot.utils.Utils;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class StrategyPrimaryPegTask extends TimerTask {

    private static final Logger LOG = Logger.getLogger(StrategyPrimaryPegTask.class.getName());
    private boolean mightNeedInit = true;
    private int activeSellOrders, activeBuyOrders, totalActiveOrders;
    private boolean ordersAndBalancesOk;
    private boolean isFirstTime = true;
    private boolean proceedsInBalance = false;
    private final int RESET_AFTER_CYCLES = 50;
    private final int MAX_RANDOM_WAIT_SECONDS = 5;
    private final int SHORT_WAIT_SECONDS = 5;
    private int cycles = 0;

    @Override
    public void run() {
        LOG.fine("Executing task : StrategyTask. DualSide :  " + Global.options.isDualSide());

        cycles++;
        if (cycles != RESET_AFTER_CYCLES) {
            if (!isFirstTime) {
                recount(); //Count number of active sells and buys
                if (mightNeedInit) {

                    // if there are 2 active orders, do nothing
                    // if there are 0 orders, place initial walls
                    // if there are a number of orders different than 2, cancel all and place initial walls

                    if (!(ordersAndBalancesOk)) {
                        //They are either 0 or need to be cancelled
                        if (totalActiveOrders != 0) {
                            ApiResponse deleteOrdersResponse = Global.exchange.getTrade().clearOrders(Global.options.getPair());
                            if (deleteOrdersResponse.isPositive()) {
                                boolean deleted = (boolean) deleteOrdersResponse.getResponseObject();
                                if (deleted) {
                                    LOG.warning("Clear all orders request succesfully");
                                    //Wait until there are no active orders
                                    boolean timedOut = false;
                                    long timeout = Global.options.getEmergencyTimeout() * 1000;
                                    long wait = 6 * 1000;
                                    long count = 0L;
                                    do {
                                        try {
                                            Thread.sleep(wait);
                                            count += wait;
                                            timedOut = count > timeout;

                                        } catch (InterruptedException ex) {
                                            LOG.severe(ex.toString());
                                        }
                                    } while (!TradeUtils.tryCancelAllOrders(Global.options.getPair()) && !timedOut);

                                    if (timedOut) {
                                        String message = "There was a problem cancelling all existing orders";
                                        LOG.severe(message);
                                        HipChatNotifications.sendMessage(message, Color.YELLOW);
                                        MailNotifications.send(Global.options.getMailRecipient(), "NuBot : Problem cancelling existing orders", message);
                                        //Continue anyway, maybe there is some balance to put up on order.
                                    }
                                    //Update the balance
                                    placeInitialWalls();
                                } else {
                                    String message = "Could not submit request to clear orders";
                                    LOG.severe(message);
                                    System.exit(0);
                                }

                            } else {
                                LOG.severe(deleteOrdersResponse.getError().toString());
                                String message = "Could not submit request to clear orders";
                                LOG.severe(message);
                                System.exit(0);
                            }
                        } else {
                            placeInitialWalls();
                        }
                    } else {
                        LOG.warning("No need to init new orders since current orders seems correct");
                    }
                    mightNeedInit = false;
                    recount();
                }

                //Make sure there are 2 orders per side
                if (!ordersAndBalancesOk) {
                    LOG.severe("Detected a number of active orders not in line with strategy. Will try to aggregate soon");
                    mightNeedInit = true; //if not, set firstime = true so nextTime will try to cancel and reset.
                } else {
                    ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalances(Global.options.getPair());
                    if (balancesResponse.isPositive()) {
                        Balance balance = (Balance) balancesResponse.getResponseObject();
                        Amount balanceNBT = balance.getNBTAvailable();

                        Amount balanceFIAT = Global.frozenBalances.removeFrozenAmount(balance.getPEGAvailableBalance(), Global.frozenBalances.getFrozenAmount());
                        LOG.fine("Updated Balance : " + balanceNBT.getQuantity() + " NBT\n "
                                + balanceFIAT.getQuantity() + " USD");

                        //Execute sellSide strategy
                        sellSide(balanceNBT);

                        //Execute buy Side strategy
                        if (Global.isDualSide && proceedsInBalance) {
                            buySide();
                        }

                    } else {
                        //Cannot get balance
                        LOG.severe(balancesResponse.getError().toString());
                    }
                }
            } else {
                LOG.info("Initializing strategy");
                recount();
                isFirstTime = false;

                boolean reinitiateSuccess = reInitiateOrders(true);
                if (!reinitiateSuccess) {
                    LOG.severe("There was a problem while trying to reinitiating orders on first execution. Trying again on next execution");
                    isFirstTime = true;
                } else {
                    LOG.info("Initial walls placed");
                }
            }
        } else {
            //Execute this block every RESET_AFTER_CYCLES cycles to ensure faireness with competing custodians

            //Reset cycle number
            cycles = 0;

            //add a random number of cycles to avoid unlikely situation of synced custodians
            cycles += Utils.randInt(0, 5);

            //Cancel sell side orders
            boolean cancelSells = TradeUtils.takeDownOrders(Constant.SELL, Global.options.getPair());

            if (cancelSells) {
                //Update balances
                ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalances(Global.options.getPair());
                if (balancesResponse.isPositive()) {
                    Balance balance = (Balance) balancesResponse.getResponseObject();
                    Amount balanceNBT = balance.getNBTAvailable();

                    Amount balanceFIAT = Global.frozenBalances.removeFrozenAmount(balance.getPEGAvailableBalance(), Global.frozenBalances.getFrozenAmount());
                    LOG.fine("Updated Balance : " + balanceNBT.getQuantity() + " NBT\n "
                            + balanceFIAT.getQuantity() + " USD");

                    //Execute sellSide strategy
                    //Introuce an aleatory sleep time to desync bots at the time of placing orders.
                    //This will favour competition in markets with multiple custodians
                    try {
                        Thread.sleep(Utils.randInt(0, MAX_RANDOM_WAIT_SECONDS) * 1000);
                    } catch (InterruptedException ex) {
                        LOG.severe(ex.toString());
                    }

                    sellSide(balanceNBT);
                }
                {
                    //Cannot get balance
                    LOG.severe(balancesResponse.getError().toString());
                }
            }

            //Execute buy Side strategy
            if (Global.isDualSide) {
                //Introuce an aleatory sleep time to desync bots at the time of placing orders.
                //This will favour competition in markets with multiple custodians
                try {
                    Thread.sleep(Utils.randInt(0, MAX_RANDOM_WAIT_SECONDS) * 1000);
                } catch (InterruptedException ex) {
                    LOG.severe(ex.toString());
                }

                buySide();
            }

        }

    }

    private void placeInitialWalls() {


        ApiResponse txFeeNTBFIATResponse = Global.exchange.getTrade().getTxFee(Global.options.getPair());
        if (txFeeNTBFIATResponse.isPositive()) {
            double txFeeFIATNTB = (Double) txFeeNTBFIATResponse.getResponseObject();
            boolean buysOrdersOk = true;
            boolean sellsOrdersOk = initOrders(Constant.SELL, TradeUtils.getSellPrice(txFeeFIATNTB));
            if (Global.options.isDualSide()) {
                buysOrdersOk = initOrders(Constant.BUY, TradeUtils.getBuyPrice(txFeeFIATNTB));
            }

            if (buysOrdersOk && sellsOrdersOk) {
                mightNeedInit = false;
            } else {
                mightNeedInit = true;
            }
        } else {
            LOG.severe("An error occurred while attempting to update tx fee.");
            mightNeedInit = true;
        }


    }

    private void sellSide(Amount balanceNBT) {
        //----------------------NTB (Sells)----------------------------
        //Check if NBT balance > 1
        if (balanceNBT.getQuantity() > 1) {
            String idToDelete = getSmallerWallID(Constant.SELL);
            if (!idToDelete.equals("-1")) {
                LOG.warning("Sellside : Taking down smaller order to aggregate it with new balance");

                if (TradeUtils.takeDownAndWait(idToDelete, Global.options.getEmergencyTimeout() * 1000, Global.options.getPair())) {

                    //Update balanceNBT to aggregate new amount made available
                    ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalances(Global.options.getPair());
                    if (balancesResponse.isPositive()) {
                        Balance balance = (Balance) balancesResponse.getResponseObject();
                        balanceNBT = balance.getNBTAvailable();

                        Amount balanceFIAT = Global.frozenBalances.removeFrozenAmount(balance.getPEGAvailableBalance(), Global.frozenBalances.getFrozenAmount());

                        LOG.fine("Updated Balance : " + balanceNBT.getQuantity() + " " + balanceNBT.getCurrency().getCode() + "\n "
                                + balanceFIAT.getQuantity() + " " + balanceFIAT.getCurrency().getCode());

                        //Update TX fee :
                        //Get the current transaction fee associated with a specific CurrencyPair
                        ApiResponse txFeeNTBUSDResponse = Global.exchange.getTrade().getTxFee(Global.options.getPair());
                        if (txFeeNTBUSDResponse.isPositive()) {
                            double txFeeUSDNTB = (Double) txFeeNTBUSDResponse.getResponseObject();
                            LOG.fine("Updated Trasaction fee = " + txFeeUSDNTB + "%");

                            //Prepare the sell order
                            double sellPrice = TradeUtils.getSellPrice(txFeeUSDNTB);

                            if (Global.options.getMaxSellVolume() > 0) //There is a cap on the order size
                            {
                                if (balanceNBT.getQuantity() > Global.options.getMaxSellVolume()) {
                                    //put the cap
                                    balanceNBT.setQuantity(Global.options.getMaxSellVolume());
                                }
                            }

                            double amountToSell = balanceNBT.getQuantity();
                            if (Global.executeOrders) {
                                //execute the order
                                String orderString = "sell " + Utils.round(amountToSell, 2) + " " + Global.options.getPair().getOrderCurrency().getCode()
                                        + " @ " + sellPrice + " " + Global.options.getPair().getPaymentCurrency().getCode();
                                LOG.warning("Strategy : Submit order : " + orderString);

                                ApiResponse sellResponse = Global.exchange.getTrade().sell(Global.options.getPair(), amountToSell, sellPrice);
                                if (sellResponse.isPositive()) {
                                    HipChatNotifications.sendMessage("New sell wall is up on " + Global.options.getExchangeName() + " : " + orderString, Color.YELLOW);
                                    String sellResponseString = (String) sellResponse.getResponseObject();
                                    LOG.warning("Strategy : Sell Response = " + sellResponseString);
                                } else {
                                    LOG.severe(sellResponse.getError().toString());
                                }
                            } else {
                                //Testing only : print the order without executing it
                                LOG.warning("Strategy : (Should) Submit order : "
                                        + "sell" + amountToSell + " " + Global.options.getPair().getOrderCurrency().getCode()
                                        + " @ " + sellPrice + " " + Global.options.getPair().getPaymentCurrency().getCode());
                            }
                        } else {
                            //Cannot update txfee
                            LOG.severe(txFeeNTBUSDResponse.getError().toString());
                        }


                    } else {
                        //Cannot get balance
                        LOG.severe(balancesResponse.getError().toString());
                    }
                } else {
                    String errMessagedeletingOrder = "could not delete order " + idToDelete;
                    LOG.severe(errMessagedeletingOrder);
                    HipChatNotifications.sendMessage(errMessagedeletingOrder, Color.YELLOW);
                    MailNotifications.send(Global.options.getMailRecipient(), "NuBot : problem shifting walls", errMessagedeletingOrder);
                }
            } else {
                LOG.severe("Can't get smaller wall id.");
            }

        } else {
            //NBT balance = 0
            LOG.fine("NBT balance < 1, no orders to execute");
        }
    }

    private void buySide() {
        //----------------------USD (Buys)----------------------------
        boolean cancel = TradeUtils.takeDownOrders(Constant.BUY, Global.options.getPair());
        if (cancel) {
            Global.frozenBalances.freezeNewFunds();
            ApiResponse txFeeNTBFIATResponse = Global.exchange.getTrade().getTxFee(Global.options.getPair());
            if (txFeeNTBFIATResponse.isPositive()) {
                double txFeeFIATNTB = (Double) txFeeNTBFIATResponse.getResponseObject();
                {
                    initOrders(Constant.BUY, TradeUtils.getBuyPrice(txFeeFIATNTB));
                }
            } else {
                LOG.severe("An error occurred while attempting to update tx fee.");
            }

        } else {
            LOG.severe("An error occurred while attempting to cancel buy orders.");
        }

    }

    private String getSmallerWallID(String type) {
        Order smallerOrder = new Order();
        smallerOrder.setId("-1");
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders(Global.options.getPair());
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();
            ArrayList<Order> orderListCategorized = TradeUtils.filterOrders(orderList, type);

            for (int i = 0; i < orderListCategorized.size(); i++) {
                Order tempOrder = orderListCategorized.get(i);
                if (tempOrder.getType().equalsIgnoreCase(type)) {
                    if (i == 0) {
                        smallerOrder = tempOrder;
                    } else {
                        if (smallerOrder.getAmount().getQuantity() > tempOrder.getAmount().getQuantity()) {
                            smallerOrder = tempOrder;
                        }
                    }
                }
            }
        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
            return "-1";
        }

        return smallerOrder.getId();
    }

    private int countActiveOrders(String type) {
        //Get active orders
        int toRet = 0;
        ApiResponse activeOrdersResponse = Global.exchange.getTrade().getActiveOrders(Global.options.getPair());
        if (activeOrdersResponse.isPositive()) {
            ArrayList<Order> orderList = (ArrayList<Order>) activeOrdersResponse.getResponseObject();

            for (int i = 0; i < orderList.size(); i++) {
                Order tempOrder = orderList.get(i);
                if (tempOrder.getType().equalsIgnoreCase(type)) {
                    toRet++;
                }
            }

        } else {
            LOG.severe(activeOrdersResponse.getError().toString());
            return -1;
        }
        return toRet;
    }

    private void recount() {

        ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalances(Global.options.getPair());
        if (balancesResponse.isPositive()) {
            Balance balance = (Balance) balancesResponse.getResponseObject();
            double balanceNBT = balance.getNBTAvailable().getQuantity();
            double balanceFIAT = (Global.frozenBalances.removeFrozenAmount(balance.getPEGAvailableBalance(), Global.frozenBalances.getFrozenAmount())).getQuantity();
            activeSellOrders = countActiveOrders(Constant.SELL);
            activeBuyOrders = countActiveOrders(Constant.BUY);
            totalActiveOrders = activeSellOrders + activeBuyOrders;

            ordersAndBalancesOk = false;

            if (Global.options.isDualSide()) {
                ordersAndBalancesOk = (activeSellOrders == 2 && activeBuyOrders == 2)
                        || (activeSellOrders == 2 && activeBuyOrders == 0 && balanceFIAT < 1)
                        || (activeSellOrders == 0 && activeBuyOrders == 2 && balanceNBT < 1);

                if (balanceFIAT > 1 && !isFirstTime) {
                    LOG.warning("The " + balance.getPEGAvailableBalance().getCurrency().getCode() + " balance is not zero (" + balanceFIAT + " ). If the balance represent proceedings "
                            + "from a sale the bot will notice.  On the other hand, If you keep seying this message repeatedly over and over, you should restart the bot. ");
                    proceedsInBalance = true;
                } else {
                    proceedsInBalance = false;
                }
            } else {
                ordersAndBalancesOk = activeSellOrders == 2 && activeBuyOrders == 0 && balanceNBT < 0.01;
            }
        } else {
            LOG.severe(balancesResponse.getError().toString());
        }
    }

    private boolean reInitiateOrders(boolean firstTime) {
        if (totalActiveOrders != 0) {
            ApiResponse deleteOrdersResponse = Global.exchange.getTrade().clearOrders(Global.options.getPair());
            if (deleteOrdersResponse.isPositive()) {
                boolean deleted = (boolean) deleteOrdersResponse.getResponseObject();
                if (deleted) {
                    LOG.warning("Clear all orders request succesfully");
                    if (firstTime) //update the initial balance of the secondary peg
                    {
                        Global.frozenBalances.setBalanceAlreadyThere(Global.options.getPair().getPaymentCurrency());
                    }
                    //Wait until there are no active orders
                    boolean timedOut = false;
                    long timeout = Global.options.getEmergencyTimeout() * 1000;
                    long wait = 5 * 1000;
                    long count = 0L;

                    boolean areAllOrdersCanceled = false;
                    do {
                        try {

                            Thread.sleep(wait);
                            areAllOrdersCanceled = TradeUtils.tryCancelAllOrders(Global.options.getPair());
                            LOG.info("Are all orders canceled? " + areAllOrdersCanceled);
                            count += wait;
                            timedOut = count > timeout;

                        } catch (InterruptedException ex) {
                            LOG.severe(ex.toString());
                        }
                    } while (!areAllOrdersCanceled && !timedOut);

                    if (timedOut) {
                        String message = "There was a problem cancelling all existing orders";
                        LOG.severe(message);
                        HipChatNotifications.sendMessage(message, Color.YELLOW);
                        MailNotifications.send(Global.options.getMailRecipient(), "NuBot : Problem cancelling existing orders", message);
                        //Continue anyway, maybe there is some balance to put up on order.
                    }
                    //Update the balance
                    placeInitialWalls();
                } else {
                    String message = "Could not submit request to clear orders";
                    LOG.severe(message);
                    return false;
                }

            } else {
                LOG.severe(deleteOrdersResponse.getError().toString());
                String message = "Could not submit request to clear orders";
                LOG.severe(message);
                return false;
            }
        } else {
            if (firstTime) //update the initial balance of the secondary peg
            {
                Global.frozenBalances.setBalanceAlreadyThere(Global.options.getPair().getPaymentCurrency());
            }
            placeInitialWalls();
        }
        try {
            Thread.sleep(SHORT_WAIT_SECONDS); //Give the time to new orders to be placed before counting again
        } catch (InterruptedException ex) {
            LOG.severe(ex.toString());
        }
        return true;
    }

    private boolean initOrders(String type, double price) {
        boolean success = true;
        Amount balance = null;
        //Update the available balance
        Currency currency;

        if (type.equals(Constant.SELL)) {
            currency = Global.options.getPair().getOrderCurrency();
        } else {
            currency = Global.options.getPair().getPaymentCurrency();
        }

        ApiResponse balancesResponse = Global.exchange.getTrade().getAvailableBalance(currency);

        if (balancesResponse.isPositive()) {

            double oneNBT = 1;
            if (type.equals(Constant.SELL)) {
                balance = (Amount) balancesResponse.getResponseObject();
            } else {
                //Here its time to compute the balance to put apart, if any
                balance = (Amount) balancesResponse.getResponseObject();
                balance = Global.frozenBalances.removeFrozenAmount(balance, Global.frozenBalances.getFrozenAmount());
                oneNBT = Utils.round(1 / Global.conversion, 8);
            }

            if (balance.getQuantity() > oneNBT) {
                // Divide the  balance 50% 50% in balance1 and balance2

                //Update TX fee :
                //Get the current transaction fee associated with a specific CurrencyPair
                ApiResponse txFeeNTBPEGResponse = Global.exchange.getTrade().getTxFee(Global.options.getPair());
                if (txFeeNTBPEGResponse.isPositive()) {
                    double txFeePEGNTB = (Double) txFeeNTBPEGResponse.getResponseObject();
                    LOG.fine("Updated Trasaction fee = " + txFeePEGNTB + "%");

                    double amount1 = Utils.round(balance.getQuantity() / 2, 8);


                    //check the calculated amount against the set maximum sell amount set in the options.json file
                    if (Global.options.getMaxSellVolume() > 0 && type.equals(Constant.SELL)) {
                        amount1 = amount1 > (Global.options.getMaxSellVolume() / 2) ? (Global.options.getMaxSellVolume() / 2) : amount1;
                    }

                    if (type.equals(Constant.BUY) && !Global.swappedPair) {
                        amount1 = Utils.round(amount1 / price, 8);
                        //check the calculated amount against the max buy amount option, if any.
                        if (Global.options.getMaxBuyVolume() > 0) {
                            amount1 = amount1 > (Global.options.getMaxBuyVolume() / 2) ? (Global.options.getMaxBuyVolume() / 2) : amount1;
                        }

                    }


                    double amount2 = balance.getQuantity() - amount1;

                    if (Global.options.getMaxSellVolume() > 0 && type.equals(Constant.SELL)) {
                        amount2 = amount2 > (Global.options.getMaxSellVolume() / 2) ? (Global.options.getMaxSellVolume() / 2) : amount2;
                    }

                    if ((type.equals(Constant.BUY) && !Global.swappedPair)
                            || (type.equals(Constant.SELL) && Global.swappedPair)) {
                        //hotfix
                        amount2 = Utils.round(amount2 - (oneNBT * 0.9), 8); //multiply by .9 to keep it below one NBT

                        amount2 = Utils.round(amount2 / price, 8);

                        //check the calculated amount against the max buy amount option, if any.
                        if (Global.options.getMaxBuyVolume() > 0) {
                            amount2 = amount2 > (Global.options.getMaxBuyVolume() / 2) ? (Global.options.getMaxBuyVolume() / 2) : amount2;
                        }

                    }

                    if (type.equals(Constant.BUY)) {
                        amount2 = Utils.round(amount2 / price, 8);

                    }

                    //Prepare the orders

                    String orderString1 = type + " " + Utils.round(amount1, 2) + " " + Global.options.getPair().getOrderCurrency().getCode()
                            + " @ " + price + " " + Global.options.getPair().getPaymentCurrency().getCode();
                    String orderString2 = type + " " + Utils.round(amount2, 2) + " " + Global.options.getPair().getOrderCurrency().getCode()
                            + " @ " + price + " " + Global.options.getPair().getPaymentCurrency().getCode();

                    if (Global.options.isExecuteOrders()) {
                        LOG.warning("Strategy - Submit order : " + orderString1);

                        ApiResponse order1Response;
                        if (type.equals(Constant.SELL)) {
                            order1Response = Global.exchange.getTrade().sell(Global.options.getPair(), amount1, price);
                        } else {
                            order1Response = Global.exchange.getTrade().buy(Global.options.getPair(), amount1, price);
                        }

                        if (order1Response.isPositive()) {
                            HipChatNotifications.sendMessage("New " + type + " wall is up on " + Global.options.getExchangeName() + " : " + orderString1, Message.Color.YELLOW);
                            String response1String = (String) order1Response.getResponseObject();
                            LOG.warning("Strategy - " + type + " Response1 = " + response1String);
                        } else {
                            LOG.severe(order1Response.getError().toString());
                            success = false;
                        }

                        LOG.warning("Strategy - Submit order : " + orderString2);

                        ApiResponse order2Response;
                        if (type.equals(Constant.SELL)) {
                            order2Response = Global.exchange.getTrade().sell(Global.options.getPair(), amount2, price);
                        } else {
                            order2Response = Global.exchange.getTrade().buy(Global.options.getPair(), amount2, price);
                        }


                        if (order2Response.isPositive()) {
                            HipChatNotifications.sendMessage("New " + type + " wall is up on " + Global.options.getExchangeName() + " : " + orderString2, Message.Color.YELLOW);
                            String response2String = (String) order2Response.getResponseObject();
                            LOG.warning("Strategy : " + type + " Response2 = " + response2String);
                        } else {
                            LOG.severe(order2Response.getError().toString());
                            success = false;
                        }

                    } else {
                        //Just print the order without executing it
                        LOG.warning("Should execute : " + orderString1 + "\n and " + orderString2);
                    }
                }
            } else {
                LOG.fine(type + " available balance < 1 NBT, no need to execute orders");
            }
        } else {
            LOG.severe(balancesResponse.getError().toString());
            success = false;
        }

        return success;
    }
}
