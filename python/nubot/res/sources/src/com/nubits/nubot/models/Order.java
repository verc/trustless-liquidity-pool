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
package com.nubits.nubot.models;

import com.nubits.nubot.utils.Utils;
import java.util.Date;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class Order {

    //Class Variables
    private String id; // A String containing a unique identifier for this order
    private Date insertedDate; //the time at which this trade was inserted place.
    private Date executedDate; //the time at which this trade was filled.
    private String type; // string value containing either Constant.BUY or Constant.SELL
    private CurrencyPair pair; //Object containing currency pair
    private Amount amount;    //Object containing the number of units for this trade (without fees). Expressed in pair.OrderCurrency
    private Amount price; //Object containing the price for each units traded. Expressed in pair.PaymentCurrency
    private Amount amountPlusFee; //Object representing the total costs/proceeds of this trade. Expressed in pair.OrderCurrency
    private boolean completed; // true if the order is filled completely

    //Constructor
    /**
     *
     * @param id
     * @param insertedDate
     * @param type
     * @param pair
     * @param amount
     * @param price
     */
    public Order(String id, Date insertedDate, String type, CurrencyPair pair, Amount amount, Amount price) {
        this.id = id;
        this.insertedDate = insertedDate;
        this.type = type;
        this.pair = pair;
        this.amount = amount;
        this.price = price;

        this.completed = false; //default false
    }

    /**
     *
     */
    public Order() {
    }

    //Methods
    /**
     *
     * @return
     */
    public String getId() {
        return id;
    }

    /**
     *
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     *
     * @return
     */
    public Date getInsertedDate() {
        return insertedDate;
    }

    /**
     *
     * @param insertedDate
     */
    public void setInsertedDate(Date insertedDate) {
        this.insertedDate = insertedDate;
    }

    /**
     *
     * @return
     */
    public Date getExecutedDate() {
        return executedDate;
    }

    /**
     *
     * @param executedDate
     */
    public void setExecutedDate(Date executedDate) {
        this.executedDate = executedDate;
    }

    /**
     *
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     *
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     *
     * @return
     */
    public CurrencyPair getPair() {
        return pair;
    }

    /**
     *
     * @param pair
     */
    public void setPair(CurrencyPair pair) {
        this.pair = pair;
    }

    /**
     *
     * @return
     */
    public Amount getAmount() {
        return amount;
    }

    /**
     *
     * @param amount
     */
    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    /**
     *
     * @return
     */
    public Amount getPrice() {
        return price;
    }

    /**
     *
     * @param price
     */
    public void setPrice(Amount price) {
        this.price = price;
    }

    /**
     *
     * @return
     */
    public Amount getAmountPlusFee() {
        return amountPlusFee;
    }

    /**
     *
     * @return
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     *
     * @param completed
     */
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public String toString() {
        return "Order{" + "id=" + id + ", insertedDate=" + insertedDate + ", executedDate="
                + executedDate + ", type=" + type + ", pair=" + pair + ", amount=" + amount
                + ", price=" + price + ", amountPlusFee=" + amountPlusFee
                + ", completed=" + completed + '}';
    }

    public String getDigest() {
        //i.e 12312 : { buy : 3242 NBT @ 0.003342 BTC }
        return id + " : { " + type + " " + Utils.round(amount.getQuantity(), 6) + "@" + Utils.round(price.getQuantity(), 8) + "} ";
    }
}
