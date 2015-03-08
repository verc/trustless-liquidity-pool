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

import com.nubits.nubot.global.Constant;
import com.nubits.nubot.trading.TradeInterface;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.trading.wrappers.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
 * @author desrever < desrever@nubits.com >
 */
public class Exchange {

//Class Variables
    //Persisted
    private static final Logger LOG = Logger.getLogger(Exchange.class.getName());
    private String name; //Name of the exchange
    //Not persisted
    private ExchangeLiveData exchangeLiveData; //contains the data shown in the UI
    private ApiKeys keys;
    private TradeInterface trade;
    //Constructor
    private static HashMap<String, TradeInterface> supportedExchanges = new HashMap<>();

    public static boolean isSupported(String name) {

        supportedExchanges.put(Constant.BTCE, new BtceWrapper());
        supportedExchanges.put(Constant.INTERNAL_EXCHANGE_PEATIO, new PeatioWrapper());
        supportedExchanges.put(Constant.BTER, new BterWrapper());
        supportedExchanges.put(Constant.CCEDK, new CcedkWrapper());
        supportedExchanges.put(Constant.POLONIEX, new PoloniexWrapper());
        supportedExchanges.put(Constant.CCEX, new CcexWrapper());
        supportedExchanges.put(Constant.ALLCOIN, new AllCoinWrapper());
        supportedExchanges.put(Constant.BITSPARK_PEATIO, new BitSparkWrapper());
        supportedExchanges.put(Constant.EXCOIN, new ExcoinWrapper());
        supportedExchanges.put(Constant.BITCOINCOID, new BitcoinCoIDWrapper());

        Iterator it = supportedExchanges.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            if (name.equalsIgnoreCase((String) pairs.getKey())) {
                return true;
            }
        }

        return false;
    }

    public Exchange(String name) {
        if (isSupported(name)) {
            this.name = name;
            this.exchangeLiveData = new ExchangeLiveData();
        } else {
            LOG.severe("Nubot doesn't support exchange named : " + name);
            listSupportedExchanges();
            System.exit(0);

        }

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExchangeLiveData getLiveData() {
        return exchangeLiveData;
    }

    public void setLiveData(ExchangeLiveData exchangeLiveData) {
        this.exchangeLiveData = exchangeLiveData;
    }

    public ApiKeys getKeys() {
        return keys;
    }

    public void setKeys(ApiKeys keys) {
        this.keys = keys;
    }

    public TradeInterface getTrade() {
        return trade;
    }

    public void setTrade(TradeInterface trade) {
        this.trade = trade;
    }

    public ExchangeLiveData getExchangeLiveData() {
        return exchangeLiveData;
    }

    public void setExchangeLiveData(ExchangeLiveData exchangeLiveData) {
        this.exchangeLiveData = exchangeLiveData;
    }

    private static void listSupportedExchanges() {
        String infoString = "Accepted values for exchange name :";


        Iterator it = supportedExchanges.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            infoString += pairs.getKey() + " ; ";
        }
        LOG.info(infoString);
    }

    public static TradeInterface getTradeInterface(String name) {
        TradeInterface ti = null;

        if (supportedExchanges.containsKey(name)) {
            return supportedExchanges.get(name);
        } else {
            LOG.severe("Cannot find the trading interface for " + name);
            System.exit(0);
        }

        return ti;

    }
}
