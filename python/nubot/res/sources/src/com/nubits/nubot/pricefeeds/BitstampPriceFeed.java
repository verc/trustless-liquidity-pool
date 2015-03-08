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
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;
import com.nubits.nubot.utils.Utils;
import java.io.IOException;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class BitstampPriceFeed extends AbstractPriceFeed {

    private static final Logger LOG = Logger.getLogger(BitstampPriceFeed.class.getName());

    public BitstampPriceFeed() {
        name = "bitstamp";
        refreshMinTime = 58 * 1000; //8 hours
    }

    @Override
    public LastPrice getLastPrice(CurrencyPair pair) {
        long now = System.currentTimeMillis();
        long diff = now - lastRequest;
        if (diff >= refreshMinTime) {
            String url = "https://www.bitstamp.net/api/ticker/";
            String htmlString;
            try {
                htmlString = Utils.getHTML(url, true);
            } catch (IOException ex) {
                LOG.severe(ex.toString());
                return new LastPrice(true, name, pair.getOrderCurrency(), null);
            }
            JSONParser parser = new JSONParser();
            try {
                JSONObject httpAnswerJson = (JSONObject) (parser.parse(htmlString));
                double last = Double.valueOf((String) httpAnswerJson.get("last"));

                //Make the average between buy and sell
                last = Utils.round(last, 8);


                lastRequest = System.currentTimeMillis();
                lastPrice = new LastPrice(false, name, pair.getOrderCurrency(), new Amount(last, pair.getPaymentCurrency()));
                return lastPrice;
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
}
