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

import java.util.ArrayList;
import java.util.Objects;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class CurrencyPair {

//Class Variables
    private Currency orderCurrency;
    private Currency paymentCurrency;

    //Constructor
    /**
     *
     * @param orderCurrency
     * @param paymentCurrency
     */
    public CurrencyPair(Currency orderCurrency, Currency paymentCurrency) {
        this.orderCurrency = orderCurrency;
        this.paymentCurrency = paymentCurrency;
    }

    /**
     *
     * @param pairString
     * @param sep
     * @return
     */
    public static CurrencyPair getCurrencyPairFromString(String pairString, String sep) {
        String orderCurrencyCode;
        String paymentCurrencyCode;
        if (!sep.equals("")) {
            orderCurrencyCode = pairString.substring(0, pairString.indexOf(sep));
            paymentCurrencyCode = pairString.substring(pairString.indexOf(sep) + 1);
        } else {
            orderCurrencyCode = pairString.substring(0, 3);
            paymentCurrencyCode = pairString.substring(3);
        }


        Currency orderC = Currency.createCurrency(orderCurrencyCode);
        Currency paymentC = Currency.createCurrency(paymentCurrencyCode);
        return new CurrencyPair(orderC, paymentC);

    }

    //Methods
    /**
     *
     * @param sep
     * @return
     */
    public String toString(String sep) {
        return orderCurrency.getCode().toLowerCase() + sep + paymentCurrency.getCode().toLowerCase();
    }

    @Override
    public String toString() {
        return orderCurrency.getCode().toLowerCase() + "" + paymentCurrency.getCode().toLowerCase();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CurrencyPair other = (CurrencyPair) obj;
        if (!Objects.equals(this.toString(), other.toString())) {
            return false;
        }
        return true;
    }

    /**
     *
     * @return
     */
    public Currency getOrderCurrency() {
        return orderCurrency;
    }

    /**
     *
     * @param currency1
     */
    public void setOrderCurrency(Currency currency1) {
        this.orderCurrency = currency1;
    }

    /**
     *
     * @return
     */
    public Currency getPaymentCurrency() {
        return paymentCurrency;
    }

    /**
     *
     * @param currency2
     */
    public void setPaymentCurrency(Currency currency2) {
        this.paymentCurrency = currency2;
    }

    /**
     *
     * @param other
     * @return
     */
    public boolean equals(CurrencyPair other) {
        String code1This = this.getOrderCurrency().getCode().toLowerCase();
        String code1Other = other.getOrderCurrency().getCode().toLowerCase();

        String code2This = this.getPaymentCurrency().getCode().toLowerCase();
        String code2Other = other.getPaymentCurrency().getCode().toLowerCase();

        if (code1This.equals(code1Other) && code2This.equals(code2Other)) {
            return true;
        } else {
            return false;
        }

    }

    public static CurrencyPair swap(CurrencyPair regular) {
        return new CurrencyPair(regular.paymentCurrency, regular.orderCurrency);
    }

    public static boolean isFiat(String currencyCode) {
        boolean fiat = false;

        ArrayList<String> knownFiatList = new ArrayList();
        knownFiatList.add("usd");
        knownFiatList.add("eur");
        knownFiatList.add("cny");
        knownFiatList.add("rur");
        knownFiatList.add("gbp");

        for (int i = 0; i < knownFiatList.size(); i++) {
            if (currencyCode.equalsIgnoreCase(knownFiatList.get(i))) {
                return true;
            }
        }

        return fiat;
    }
}
