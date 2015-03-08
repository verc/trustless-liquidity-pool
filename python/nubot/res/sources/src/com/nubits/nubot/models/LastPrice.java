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

import java.util.Date;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class LastPrice {

//Class Variables
    private boolean error;
    private String source; //The name of the datasource where the price was taken
    private Currency currencyMeasured; // The currency which price is being taken
    private Amount price;  // the last price
    private Date timestamp; //the time stamp at when price was registered

    //Constructor
    /**
     *
     * @param error
     * @param source
     * @param currencyMeasured
     * @param price
     */
    public LastPrice(boolean error, String source, Currency currencyMeasured, Amount price) {
        this.error = error;
        this.source = source;
        this.currencyMeasured = currencyMeasured;
        this.price = price;
        this.timestamp = new Date();
    }

    /**
     *
     * @return
     */
    public long getAge() {
        Date now = new Date();
        return now.getTime() - timestamp.getTime();
    }

    //Methods
    /**
     *
     * @return
     */
    public String getSource() {
        return source;
    }

    /**
     *
     * @param source
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     *
     * @return
     */
    public Currency getCurrencyMeasured() {
        return currencyMeasured;
    }

    /**
     *
     * @param currencyMeasured
     */
    public void setCurrencyMeasured(Currency currencyMeasured) {
        this.currencyMeasured = currencyMeasured;
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
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     *
     * @return
     */
    public boolean isError() {
        return error;
    }

    /**
     *
     * @param error
     */
    public void setError(boolean error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "currencyMeasured " + currencyMeasured + " \n"
                + "error " + error + " \n" + "price " + price + " \n" + "source "
                + source + " \n" + "timestamp " + timestamp;
    }
}
