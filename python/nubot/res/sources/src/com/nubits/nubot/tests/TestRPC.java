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
package com.nubits.nubot.tests;

import com.nubits.nubot.RPC.NuRPCClient;
import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.global.Passwords;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.tasks.TaskManager;
import com.nubits.nubot.utils.FileSystem;
import com.nubits.nubot.utils.Utils;
import com.nubits.nubot.utils.logging.NuLogger;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class TestRPC {

    private static final Logger LOG = Logger.getLogger(TestRPC.class.getName());
    private static String ipTest = "127.0.0.1";
    private static int portTest = 9091;
    private static boolean verbose = false;
    private static boolean useIdentifier = false;

    public static void main(String[] args) {

        //Default values
        String custodian = Passwords.CUSTODIAN_PUBLIC_ADDRESS;
        String user = Passwords.NUD_RPC_USER;
        String pass = Passwords.NUD_RPC_PASS;
        double sell = 0;
        double buy = 0;
        //java -jar testRPC user pass custodian sell buy
        if (args.length == 5) {
            LOG.info("Reading input parameters");
            user = args[0];
            pass = args[1];
            custodian = args[2];
            sell = Double.parseDouble(args[3]);
            buy = Double.parseDouble(args[4]);
        }

        Utils.loadProperties("settings.properties");

        TestRPC test = new TestRPC();

        test.setup(Constant.INTERNAL_EXCHANGE_PEATIO, custodian, Constant.NBT_BTC, user, pass);
        test.testCheckNudTask();
        try {
            Thread.sleep(2000);

        } catch (InterruptedException ex) {
            Logger.getLogger(TestRPC.class.getName()).log(Level.SEVERE, null, ex);
        }
        //test.testGetInfo();
        //test.testIsConnected();
        test.testSendLiquidityInfo(buy, sell, 1);
        //test.testGetLiquidityInfo();
        //test.testGetLiquidityInfo(Constant.SELL, Passwords.CUSTODIA_PUBLIC_ADDRESS);
        //test.testGetLiquidityInfo(Constant.BUY, Passwords.CUSTODIA_PUBLIC_ADDRESS);

        System.exit(0);

    }

    private void testSendLiquidityInfo(double amountBuy, double amountSell, int tier) {
        if (Global.rpcClient.isConnected()) {
            JSONObject responseObject = Global.rpcClient.submitLiquidityInfo(Global.rpcClient.USDchar, amountBuy, amountSell, tier);
            if (null == responseObject) {
                LOG.severe("Something went wrong while sending liquidityinfo");
            } else {
                LOG.info(responseObject.toJSONString());
                if ((boolean) responseObject.get("submitted")) {
                    LOG.info("Now calling getliquidityinfo");
                    JSONObject infoObject = Global.rpcClient.getLiquidityInfo(NuRPCClient.USDchar);
                    LOG.info(infoObject.toJSONString());
                }
            }
        } else {
            LOG.severe("Nu Client offline. ");
        }

    }

    private void testGetInfo() {
        if (Global.rpcClient.isConnected()) {
            JSONObject responseObject = Global.rpcClient.getInfo();
            LOG.info(responseObject.toJSONString());
        } else {
            LOG.severe("Nu Client offline. ");
        }
    }

    private void testIsConnected() {
        String connectedString = "offline";
        if (Global.rpcClient.isConnected()) {
            connectedString = "online";
        }
        LOG.info("Nud is " + connectedString + " @ " + Global.rpcClient.getIp() + ":" + Global.rpcClient.getPort());
    }

    private void setup(String exchangeName, String custodianAddress, CurrencyPair pair, String user, String pass) {
        String folderName = "tests_" + System.currentTimeMillis() + "/";
        String logsFolder = Global.settings.getProperty("log_path") + folderName;
        //Create log dir
        FileSystem.mkdir(logsFolder);
        try {
            NuLogger.setup(verbose, logsFolder);
        } catch (IOException ex) {
            LOG.severe(ex.toString());
        }

        System.setProperty("javax.net.ssl.trustStore", Global.settings.getProperty("keystore_path"));
        System.setProperty("javax.net.ssl.trustStorePassword", Global.settings.getProperty("keystore_pass"));

        Global.publicAddress = custodianAddress;

        //Create the client
        Global.rpcClient = new NuRPCClient(ipTest, portTest, user, pass, verbose, useIdentifier, custodianAddress, pair, exchangeName);
    }

    private void testCheckNudTask() {
        //Create a TaskManager and
        Global.taskManager = new TaskManager();
        //Start checking for connection
        Global.taskManager.getCheckNudTask().start();


        //Wait a couple of seconds for the connectionThread to get live

    }

    private void testGetLiquidityInfo() {
        if (Global.rpcClient.isConnected()) {
            JSONObject responseObject = Global.rpcClient.getLiquidityInfo(NuRPCClient.USDchar);
            if (null == responseObject) {
                LOG.severe("Something went wrong while sending liquidityinfo");
            } else {
                LOG.info(responseObject.toJSONString());
            }
        } else {
            LOG.severe("Nu Client offline. ");
        }
    }

    private void testGetLiquidityInfo(String type, String address) {
        if (Global.rpcClient.isConnected()) {
            double response = Global.rpcClient.getLiquidityInfo(NuRPCClient.USDchar, type, address);
            if (response == -1) {
                LOG.severe("Something went wrong while sending liquidityinfo");
            } else {
                LOG.info("Total " + type + " liquidity : " + response + " " + Constant.NBT.getCode());
            }
        } else {
            LOG.severe("Nu Client offline. ");
        }

    }
}
