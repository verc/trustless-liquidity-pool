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

import java.text.DecimalFormat;
import java.util.Date;

/**
 *
 * @author admin
 */
public class Trade {

    private String id; // A String containing a unique identifier for this order
    private String order_id; // A String containing a unique identifier for this order
    private CurrencyPair pair; //Object containing currency pair
    private String type; // string value containing either Constant.BUY or Constant.SELL
    private Amount price; //Object containing the price for each units traded.
    private Amount amount;    //Object containing the number of units for this trade (without fees).
    private Amount fee; //containing the fee paid
    private Date date; //the time at which this trade was inserted place.
    private String exchangeName; //The name of the exchange

    public Trade(String id, String order_id, CurrencyPair pair, String type, Amount price, Amount amount, Amount fee, Date date, String exchangeName) {
        this.id = id;
        this.order_id = order_id;
        this.pair = pair;
        this.type = type;
        this.price = price;
        this.amount = amount;
        this.date = date;
        this.fee = fee;
        this.exchangeName = exchangeName;
    }

    public Trade() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public CurrencyPair getPair() {
        return pair;
    }

    public void setPair(CurrencyPair pair) {
        this.pair = pair;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Amount getPrice() {
        return price;
    }

    public void setPrice(Amount price) {
        this.price = price;
    }

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Amount getFee() {
        return fee;
    }

    public void setFee(Amount fee) {
        this.fee = fee;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    @Override
    public String toString() {
        return "Trade{" + "id=" + id + ", order_id=" + order_id + ", pair=" + pair + ", type=" + type + ", price=" + price + ", amount=" + amount + ", fee=" + fee + ", date=" + date + ", exchangeName=" + exchangeName + '}';
    }


    /* uncomment for csv output
     public String toCsvString() {
     return id + "," + order_id + "," + pair.toString("_") + "," + type + "," + price.getQuantity() + "," + amount.getQuantity() + "," + date;
     }
     */
    public String toJSONString() {
        DecimalFormat df = new DecimalFormat("0");
        df.setMaximumFractionDigits(8);

        String feeString = "N.A.";
        if (fee != null) {
            feeString = df.format(this.fee.getQuantity());
        }


        return "\"Trade_" + this.id + "\":{\n"
                + "\"id\":\"" + this.id + "\",\n"
                + "\"order_id\":\"" + this.order_id + "\",\n"
                + "\"exchange\":\"" + this.exchangeName + "\",\n"
                + "\"pair\":\"" + this.pair.toString() + "\",\n"
                + "\"type\":\"" + this.type.toUpperCase() + "\",\n"
                + "\"price\":" + this.price.getQuantity() + ",\n"
                + "\"amount\":" + this.amount.getQuantity() + ",\n"
                + "\"fee\":" + feeString + ",\n"
                + "\"timestamp\":" + this.date.getTime() + "\n"
                + "}";
    }
}
