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
package com.nubits.nubot.pricefeeds;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.exchanges.ExchangeLiveData;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;
import com.nubits.nubot.trading.Ticker;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.trading.wrappers.BterWrapper;
import java.util.logging.Logger;

public class BterPriceFeed extends AbstractPriceFeed {

    private static final Logger LOG = Logger.getLogger(BterPriceFeed.class.getName());

    public BterPriceFeed() {
        name = "bter";
        refreshMinTime = 50 * 1000; //one minutee
    }

    @Override
    public LastPrice getLastPrice(CurrencyPair pair) {
        long now = System.currentTimeMillis();
        long diff = now - lastRequest;
        if (diff >= refreshMinTime) {
            try {
                Exchange exch = new Exchange(Constant.BTER);
                ExchangeLiveData liveData = new ExchangeLiveData();
                exch.setLiveData(liveData);
                ApiKeys keys = new ApiKeys("a", "b");
                exch.setTrade(new BterWrapper(keys, exch));

                BterWrapper trader = (BterWrapper) exch.getTrade();
                ApiResponse lastPriceResponse = trader.getLastPriceFeed(pair);
                if (lastPriceResponse.isPositive()) {
                    Ticker ticker = (Ticker) lastPriceResponse.getResponseObject();
                    double last = ticker.getLast();
                    lastRequest = System.currentTimeMillis();
                    lastPrice = new LastPrice(false, name, pair.getOrderCurrency(), new Amount(last, pair.getPaymentCurrency()));
                    return lastPrice;
                } else {
                    LOG.severe(lastPriceResponse.getError().toString());
                    lastRequest = System.currentTimeMillis();
                    return new LastPrice(true, name, pair.getOrderCurrency(), null);
                }

            } catch (Exception ex) {
                LOG.severe(ex.toString());
                lastRequest = System.currentTimeMillis();
                return new LastPrice(true, name, pair.getOrderCurrency(), null);
            }
        } else {
            LOG.fine("Wait " + (refreshMinTime - (System.currentTimeMillis() - lastRequest)) + " ms "
                    + "before making a new request. Now returning the last saved price\n\n");
            return lastPrice;
        }
    }

    private String getUrl(CurrencyPair pair) {
        return "http://data.bter.com/api/1/ticker/" + pair.toString("_");
    }
}
