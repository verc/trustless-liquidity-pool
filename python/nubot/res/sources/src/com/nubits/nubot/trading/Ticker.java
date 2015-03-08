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
public class Ticker {

//Class Variables
    private double last;
    private double ask;
    private double bid;
    //Constructor

    /**
     *
     */
    public Ticker() {
    }

    /**
     *
     * @param last
     * @param ask
     * @param bid
     */
    public Ticker(double last, double ask, double bid) {
        this.last = last;
        this.ask = ask;
        this.bid = bid;
    }

    /**
     *
     * @return
     */
    public double getLast() {
        return last;
    }

    /**
     *
     * @param last
     */
    public void setLast(double last) {
        this.last = last;
    }

    /**
     *
     * @return
     */
    public double getAsk() {
        return ask;
    }

    /**
     *
     * @param ask
     */
    public void setAsk(double ask) {
        this.ask = ask;
    }

    /**
     *
     * @return
     */
    public double getBid() {
        return bid;
    }

    /**
     *
     * @param bid
     */
    public void setBid(double bid) {
        this.bid = bid;
    }
}
