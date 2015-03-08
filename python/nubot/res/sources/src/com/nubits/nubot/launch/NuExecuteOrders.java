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
package com.nubits.nubot.launch;

import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.exchanges.ExchangeLiveData;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.ApiError;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.tasks.TaskManager;
import com.nubits.nubot.trading.keys.ApiKeys;
import com.nubits.nubot.trading.wrappers.PeatioWrapper;
import com.nubits.nubot.utils.FileSystem;
import com.nubits.nubot.utils.Utils;
import com.nubits.nubot.utils.logging.NuLogger;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class NuExecuteOrders {

    private static final Logger LOG = Logger.getLogger(NuExecuteOrders.class.getName());
    private String api;
    private String secret;
    private String pathToOrders;
    private String exchangename;
    private ArrayList<CsvLine> orderList;
    private ApiKeys keys;
    private static Thread mainThread;
    private static final String USAGE_STRING = "java -jar OrderBot <apikey> <secretkey> <exchange-name> <path/to/orders.csv>";

    public static void main(String[] args) {
        //Load settings
        Utils.loadProperties("settings.properties");

        mainThread = Thread.currentThread();

        String folderName = "NuExecuteOrders_" + System.currentTimeMillis() + "/";
        String logsFolder = Global.settings.getProperty("log_path") + folderName;
        //Create log dir
        FileSystem.mkdir(logsFolder);

        try {
            NuLogger.setup(true, logsFolder);
        } catch (IOException ex) {
            LOG.severe(ex.toString());
        }


        NuExecuteOrders app = new NuExecuteOrders();
        if (app.readParams(args)) {
            createShutDownHook();
            LOG.fine("Launching NuOrderExecutor ");
            app.orderList = app.readOrdersFromFile();
            app.prepareForExecution();
            app.executeOrders();
            LOG.fine("Done");
            System.exit(0);

        } else {
            System.exit(0);
        }
    }

    private void executeOrders() {

        Utils.printSeparator();
        for (int i = 0; i < orderList.size(); i++) {
            ApiResponse respose = orderList.get(i).execute();
            if (respose.isPositive()) {
                String resposeString = (String) respose.getResponseObject();
                LOG.warning("Orders submitted . Response = " + resposeString);
                Utils.printSeparator();
            } else {
                LOG.severe(respose.getError().toString());
            }
        }
    }

    private void prepareForExecution() {
        //Wrap the keys into a new ApiKeys object
        keys = new ApiKeys(secret, api);

        Global.exchange = new Exchange(exchangename);

        //Create e ExchangeLiveData object to accomodate liveData from the exchange
        ExchangeLiveData liveData = new ExchangeLiveData();
        Global.exchange.setLiveData(liveData);



        //Switch the ip of exchange
        String apibase = "";
        if (exchangename.equalsIgnoreCase(Constant.INTERNAL_EXCHANGE_PEATIO)) {
            apibase = Constant.INTERNAL_EXCHANGE_PEATIO_API_BASE;
        } else {
            LOG.severe("Exchange name not accepted : " + exchangename);
            System.exit(0);
        }

        //Create a new TradeInterface object using the PeatioWrapper implementation
        //Assign the TradeInterface to the PeatioExchange



        Global.exchange.setTrade(new PeatioWrapper(keys, Global.exchange, apibase));
        Global.exchange.getLiveData().setUrlConnectionCheck(Global.exchange.getTrade().getUrlConnectionCheck());

        //Create a TaskManager and
        Global.taskManager = new TaskManager();
        //Start checking for connection
        Global.taskManager.getCheckConnectionTask().start();


        //Wait a couple of seconds for the connectionThread to get live
        LOG.fine("Exchange setup complete. Now checking connection ...");
        try {
            Thread.sleep(4000);
        } catch (InterruptedException ex) {
            LOG.severe(ex.toString());
        }

    }

    private boolean readParams(String[] args) {
        boolean ok = false;

        if (args.length != 4) {
            LOG.severe("wrong argument number : call it with \n" + USAGE_STRING);
            System.exit(0);
        }


        api = args[0];
        secret = args[1];
        exchangename = args[2];
        pathToOrders = args[3];

        ok = true;
        return ok;
    }

    private ArrayList<CsvLine> readOrdersFromFile() {
        BufferedReader br = null;
        String line = "";
        String cvsSplitBy = ",";
        ArrayList<CsvLine> list = new ArrayList<CsvLine>();
        try {

            br = new BufferedReader(new FileReader(this.pathToOrders));
            while ((line = br.readLine()) != null) {
                String[] order = line.split(cvsSplitBy);
                CsvLine csvline = new CsvLine(order[0], Double.valueOf(order[1]), Double.valueOf(order[2]), Long.valueOf(order[3]));
                list.add(csvline);
                LOG.fine("Order " + list.size() + " loaded from file : " + csvline.toString());
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return list;

    }

    private static void createShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                LOG.fine("Exiting...");
                NuExecuteOrders.mainThread.interrupt();
                Global.taskManager.stopAll();

                Thread.currentThread().interrupt();
                return;
            }
        }));
    }

    protected class CsvLine {

        private String type;
        private double amount;
        private double price;
        private long delay;

        public CsvLine(String type, double amount, double price, long delay) {
            this.type = type;
            this.amount = amount;
            this.price = price;
            this.delay = delay;
        }

        public ApiResponse execute() {
            try {
                Thread.sleep(this.delay);
            } catch (InterruptedException ex) {
                LOG.severe(ex.toString());
            }


            LOG.warning("\nExecuting " + this.toString());


            switch (this.type) {
                case "sell":
                    return Global.exchange.getTrade().sell(Constant.BTC_CNY, amount, price);
                case "buy":
                    return Global.exchange.getTrade().buy(Constant.BTC_CNY, amount, price);
                default:
                    return new ApiResponse(false, null, new ApiError(2311, "Unrecognized order type (" + this.getType() + "). "
                            + "it can either be buy or sell"));
            }
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public long getDelay() {
            return delay;
        }

        public void setDelay(long delay) {
            this.delay = delay;
        }

        @Override
        public String toString() {
            return "amount " + amount + " " + "delay " + delay + " " + "price " + price + " " + "type " + type;
        }
    }
}
