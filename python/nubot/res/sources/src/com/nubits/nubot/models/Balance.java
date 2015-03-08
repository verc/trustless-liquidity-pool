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

import com.nubits.nubot.global.Constant;

/**
 *
 * @author desrever < desrever@NBTTotal.com >
 */
public class Balance {

//Class Variables
    private Amount PEGTotal;
    private Amount NBTTotal;
    private Amount PEGonOrder;
    private Amount NBTonOrder;
    private Amount PEGAvailable;
    private Amount NBTAvailable;

//Constructor
    /**
     *
     */
    public Balance() {
    }

    /**
     *
     * @param NBTTotal
     * @param PEGTotal
     */
    public Balance(Amount NBTTotal, Amount PEGTotal) {
        this.NBTTotal = NBTTotal;
        this.PEGTotal = PEGTotal;
        this.PEGonOrder = new Amount(0, Currency.createCurrency(PEGTotal.getCurrency().getCode()));
        this.NBTonOrder = new Amount(0, Constant.NBT);
        this.PEGAvailable = PEGTotal;
        this.NBTAvailable = NBTTotal;
    }

    /**
     *
     * @param PEGAvail
     * @param NBTAvail
     * @param PEGonOrder
     * @param NBTonOrder
     */
    public Balance(Amount PEGAvail, Amount NBTAvail, Amount PEGonOrder, Amount NBTonOrder) {
        this.PEGAvailable = PEGAvail;
        this.NBTAvailable = NBTAvail;
        this.PEGonOrder = PEGonOrder;
        this.NBTonOrder = NBTonOrder;
        this.PEGTotal = new Amount(PEGAvailable.getQuantity() + PEGonOrder.getQuantity(), Currency.createCurrency(PEGonOrder.getCurrency().getCode()));
        this.NBTTotal = new Amount(NBTAvailable.getQuantity() + NBTonOrder.getQuantity(), Constant.NBT);
    }

//Methods
    /**
     * @return the PEGTotal
     */
    public Amount getPEGBalance() {
        return PEGTotal;
    }

    /**
     * @param PEGTotal the PEGTotal to set
     */
    private void setPegBalance(Amount PEGTotal) {
        this.PEGTotal = PEGTotal;
    }

    /**
     * @return the NBTTotal
     */
    public Amount getNubitsBalance() {
        return NBTTotal;
    }

    /**
     * @param NBTTotal
     */
    private void setNubitsBalance(Amount NBTTotal) {
        this.NBTTotal = NBTTotal;
    }

    /**
     *
     * @return
     */
    public Amount getPEGBalanceonOrder() {
        return PEGonOrder;
    }

    /**
     *
     * @param PEGonOrder
     */
    private void setPEGBalanceonOrder(Amount PEGonOrder) {
        this.PEGonOrder = PEGonOrder;
    }

    /**
     *
     * @return
     */
    public Amount getNBTonOrder() {
        return NBTonOrder;
    }

    /**
     *
     * @param NBTonOrder
     */
    private void setNBTonOrder(Amount NBTonOrder) {
        this.NBTonOrder = NBTonOrder;
    }

    /**
     *
     * @return
     */
    public Amount getPEGAvailableBalance() {
        return PEGAvailable;
    }

    /**
     *
     * @param PEGAvailable
     */
    private void setPEGAvailableBalance(Amount PEGAvailable) {
        this.PEGAvailable = PEGAvailable;
    }

    /**
     *
     * @return
     */
    public Amount getNBTAvailable() {
        return NBTAvailable;
    }

    /**
     *
     * @param NBTAvailable
     */
    private void setNBTAvailableBalance(Amount NBTAvailable) {
        this.NBTAvailable = NBTAvailable;
    }

    @Override
    public String toString() {
        return "Balance{" + "PEGTotal=" + PEGTotal + ", PEGonOrder=" + PEGonOrder + ", PEGAvailable=" + PEGAvailable + ", NBTTotal=" + NBTTotal + ", NBTonOrder=" + NBTonOrder + ", NBTAvailable=" + NBTAvailable + '}';
    }

    public static Balance getSwapedBalance(Balance original) {
        return new Balance(original.NBTAvailable, original.getPEGAvailableBalance(), original.getNBTonOrder(), original.getPEGBalanceonOrder());
    }
}
