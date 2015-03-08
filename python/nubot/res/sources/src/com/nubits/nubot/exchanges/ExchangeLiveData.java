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
package com.nubits.nubot.exchanges;

import com.nubits.nubot.models.Balance;
import com.nubits.nubot.models.Order;
import java.util.ArrayList;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class ExchangeLiveData {

    private ExchangeStatus status;
    private boolean validKeys;
    private String urlConnectionCheck; //URL used to check connection
    //1 NBT price
    private double bid;
    private double ask;
    private double last;
    private boolean connected;
    //assets
    private Balance balance;
    //fee
    private double fee;
    //Orders
    private ArrayList<Order> ordersList = new ArrayList<>();
    //Liquidity info
    private double NBTonsell;
    private double NBTonbuy;

    //Constructor
    public ExchangeLiveData() {
    }

    public ExchangeLiveData(ExchangeStatus status, double bid, double ask, double last, double fee, Balance balance, ArrayList<Order> orderList) {
        this.status = status;
        this.bid = bid;
        this.ask = ask;
        this.last = last;
        this.balance = balance;
        this.fee = fee;


    }

    //Methods
    public ExchangeStatus getStatus() {
        return status;
    }

    public void setStatus(ExchangeStatus status) {
        this.status = status;
    }

    public double getBid() {
        return bid;
    }

    public void setBid(double bid) {
        this.bid = bid;
    }

    public double getAsk() {
        return ask;
    }

    public void setAsk(double ask) {
        this.ask = ask;
    }

    public Balance getBalance() {
        return balance;
    }

    public void setBalance(Balance balance) {
        this.balance = balance;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getUrlConnectionCheck() {
        return urlConnectionCheck;
    }

    public void setUrlConnectionCheck(String urlConnectionCheck) {
        this.urlConnectionCheck = urlConnectionCheck;
    }

    public double getLast() {
        return last;
    }

    public void setLast(double last) {
        this.last = last;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public boolean isValidKeys() {
        return validKeys;
    }

    public void setValidKeys(boolean validKeys) {
        this.validKeys = validKeys;
    }

    public ArrayList<Order> getOrdersList() {
        return ordersList;
    }

    public void setOrdersList(ArrayList<Order> ordersList) {
        this.ordersList = ordersList;
    }

    public double getNBTonsell() {
        return NBTonsell;
    }

    public void setNBTonsell(double NBTonsell) {
        this.NBTonsell = NBTonsell;
    }

    public double getNBTonbuy() {
        return NBTonbuy;
    }

    public void setNBTonbuy(double NBTonbuy) {
        this.NBTonbuy = NBTonbuy;
    }
}
