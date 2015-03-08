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
import java.text.DecimalFormat;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class Amount {

//Class Variables
    private double quantity;
    private Currency currency;

    //Constructor
    /**
     *
     * @param quantity
     * @param currency
     */
    public Amount(double quantity, Currency currency) {
        this.quantity = Utils.round(quantity, 8);
        this.currency = currency;
    }

    //Methods
    /**
     *
     * @return
     */
    public double getQuantity() {
        return quantity;
    }

    /**
     *
     * @param quantity
     */
    public void setQuantity(double quantity) {
        this.quantity = Utils.round(quantity, 8);
    }

    /**
     *
     * @return
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     *
     * @param currency
     */
    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public String toString() {
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(8);
        return "" + df.format(quantity) + " " + currency.getCode() + "\n";
    }

    /**
     *
     * @param multiplyFactor
     * @return
     */
    public double getConversion(double multiplyFactor) {
        return Utils.round(this.getQuantity() * multiplyFactor, 8);
    }
}
