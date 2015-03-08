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
import com.nubits.nubot.global.Passwords;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.LastPrice;
import com.nubits.nubot.utils.Utils;
import java.io.IOException;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class OpenexchangeratesPriceFeed extends AbstractPriceFeed {

    private static final Logger LOG = Logger.getLogger(OpenexchangeratesPriceFeed.class.getName());

    public OpenexchangeratesPriceFeed() {
        name = "openexchangerates";
        refreshMinTime = 8 * 60 * 60 * 1000; //8 hours
    }

    @Override
    public LastPrice getLastPrice(CurrencyPair pair) {
        long now = System.currentTimeMillis();
        long diff = now - lastRequest;
        if (diff >= refreshMinTime) {
            String url = getUrl(pair);
            String htmlString;
            try {
                htmlString = Utils.getHTML(url, true);
            } catch (IOException ex) {
                LOG.severe(ex.toString());
                return new LastPrice(true, name, pair.getOrderCurrency(), null);
            }
            JSONParser parser = new JSONParser();
            boolean found = false;
            try {
                JSONObject httpAnswerJson = (JSONObject) (parser.parse(htmlString));

                String lookingfor = pair.getOrderCurrency().getCode().toUpperCase();
                JSONObject rates = (JSONObject) httpAnswerJson.get("rates");
                lastRequest = System.currentTimeMillis();
                if (rates.containsKey(lookingfor)) {
                    double last = (Double) rates.get(lookingfor);
                    last = Utils.round(1 / last, 8);
                    lastPrice = new LastPrice(false, name, pair.getOrderCurrency(), new Amount(last, pair.getPaymentCurrency()));
                    return lastPrice;
                } else {
                    LOG.warning("Cannot find currency :" + lookingfor + " on feed :" + name);
                    return new LastPrice(true, name, pair.getOrderCurrency(), null);
                }


            } catch (ParseException ex) {
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
        String key = Passwords.OPEN_EXCHANGE_RATES_APP_ID;
        return "https://openexchangerates.org/api/latest.json?app_id=" + key;
    }
}
