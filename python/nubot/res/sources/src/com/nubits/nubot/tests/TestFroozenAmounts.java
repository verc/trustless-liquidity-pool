package com.nubits.nubot.tests;
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

import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.Amount;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.utils.FrozenBalancesManager;
import com.nubits.nubot.utils.Utils;
import java.util.logging.Logger;

public class TestFroozenAmounts {

    private static final Logger LOG = Logger.getLogger(TestFroozenAmounts.class.getName());

    public static void main(String[] args) {
        //Load settings that contains the path
        Utils.loadProperties("settings.properties");

        CurrencyPair pair = Constant.NBT_BTC;
        Currency currency = pair.getPaymentCurrency();
        String exchangeName = Constant.BTER;

        FrozenBalancesManager fbm = new FrozenBalancesManager(exchangeName, pair, Global.settings.getProperty("frozen_folder"));

        fbm.updateFrozenBalance(new Amount(0.000000091, currency));
        fbm.updateFrozenBalance(new Amount(5032, currency));
        fbm.updateFrozenBalance(new Amount(202, currency));
        fbm.updateFrozenBalance(new Amount(30.3, currency));
        fbm.updateFrozenBalance(new Amount(34.3, currency));
        fbm.updateFrozenBalance(new Amount(330.1233, currency));
        fbm.updateFrozenBalance(new Amount(1130.13, currency));
        fbm.updateFrozenBalance(new Amount(303.32342, currency));
        fbm.updateFrozenBalance(new Amount(30.3, currency));
        fbm.updateFrozenBalance(new Amount(1, currency));

        LOG.info("Loaded Froozen balance : " + fbm.getFrozenAmount().getAmount().getQuantity());

        fbm.updateFrozenBalance(new Amount(231.2, currency));

        LOG.info("then Froozen balance : " + fbm.getFrozenAmount().getAmount().getQuantity());


    }
}
