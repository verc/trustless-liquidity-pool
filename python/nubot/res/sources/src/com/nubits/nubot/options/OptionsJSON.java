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
package com.nubits.nubot.options;

import com.nubits.nubot.global.Constant;
import com.nubits.nubot.global.Global;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.utils.FileSystem;
import com.nubits.nubot.utils.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author desrever <desrever at nubits.com>
 *
 *
 */
public class OptionsJSON {

    private static final Logger LOG = Logger.getLogger(OptionsJSON.class.getName());
    //Compulsory settings ----------------------------
    private String apiKey;
    private String apiSecret;
    private String mailRecipient;
    private String exchangeName;
    private boolean dualSide;
    private CurrencyPair pair;
    private SecondaryPegOptionsJSON secondaryPegOptions;
    //Conditional settings with a default value
    private String rpcUser;
    private String rpcPass;
    private String nubitAddress;
    private int nudPort;
    //Optional settings with a default value  ----------------------------
    private String nudIp;
    private boolean sendMails;
    private boolean submitLiquidity;
    private boolean executeOrders;
    private boolean verbose;
    private boolean sendHipchat;
    private boolean aggregate;
    private boolean multipleCustodians;
    private int executeStrategyInterval; //disabled
    private int sendLiquidityInterval; //disabled
    private double txFee;
    private double priceIncrement;
    private int emergencyTimeout;
    private double keepProceeds;
    private double maxSellVolume;
    private double maxBuyVolume;
    private SecondaryPegOptionsJSON cpo;

    /**
     *
     * @param dualSide
     * @param apiKey
     * @param apiSecret
     * @param nubitAddress
     * @param rpcUser
     * @param rpcPass
     * @param nudIp
     * @param nudPort
     * @param priceIncrement
     * @param txFee
     * @param sendRPC
     * @param exchangeName
     * @param executeOrders
     * @param verbose
     * @param pair
     * @param executeStrategyInterval
     * @param sendLiquidityInterval
     * @param sendHipchat
     * @param sendMails
     * @param mailRecipient
     * @param emergencyTimeout
     * @param keepProceeds
     * @param secondaryPegOptions
     */
    public OptionsJSON(boolean dualSide, String apiKey, String apiSecret, String nubitAddress,
            String rpcUser, String rpcPass, String nudIp, int nudPort, double priceIncrement,
            double txFee, boolean sendRPC, String exchangeName, boolean executeOrders, boolean verbose, CurrencyPair pair,
            int executeStrategyInterval, int sendLiquidityInterval, boolean sendHipchat,
            boolean sendMails, String mailRecipient, int emergencyTimeout, double keepProceeds, boolean aggregate, boolean multipleCustodians, double maxSellVolume, double maxBuyVolume, SecondaryPegOptionsJSON secondaryPegOptions) {
        this.dualSide = dualSide;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.nubitAddress = nubitAddress;
        this.rpcUser = rpcUser;
        this.rpcPass = rpcPass;
        this.nudIp = nudIp;
        this.nudPort = nudPort;
        this.priceIncrement = priceIncrement;
        this.txFee = txFee;
        this.submitLiquidity = sendRPC;
        this.exchangeName = exchangeName;
        this.verbose = verbose;
        this.executeOrders = executeOrders;
        this.pair = pair;
        this.sendLiquidityInterval = sendLiquidityInterval;
        this.executeStrategyInterval = executeStrategyInterval;
        this.sendHipchat = sendHipchat;
        this.sendMails = sendMails;
        this.mailRecipient = mailRecipient;
        this.emergencyTimeout = emergencyTimeout;
        this.keepProceeds = keepProceeds;
        this.secondaryPegOptions = secondaryPegOptions;
        this.aggregate = aggregate;
        this.multipleCustodians = multipleCustodians;
        this.maxSellVolume = maxSellVolume;
        this.maxBuyVolume = maxBuyVolume;
    }

    /**
     *
     * @return
     */
    public boolean isDualSide() {
        return dualSide;
    }

    /**
     *
     * @param dualSide
     */
    public void setDualSide(boolean dualSide) {
        this.dualSide = dualSide;
    }

    /**
     *
     * @return
     */
    public boolean isSendRPC() {
        return submitLiquidity;
    }

    /**
     *
     * @param sendRPC
     */
    public void setSendRPC(boolean sendRPC) {
        this.submitLiquidity = sendRPC;
    }

    /**
     *
     * @return
     */
    public boolean isExecuteOrders() {
        return executeOrders;
    }

    /**
     *
     * @param executeOrders
     */
    public void setExecuteOrders(boolean executeOrders) {
        this.executeOrders = executeOrders;
    }

    /**
     *
     * @return
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     *
     * @param verbose
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     *
     * @return
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     *
     * @param apiKey
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     *
     * @return
     */
    public String getApiSecret() {
        return apiSecret;
    }

    /**
     *
     * @param apiSecret
     */
    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    /**
     *
     * @return
     */
    public String getNubitsAddress() {
        return nubitAddress;
    }

    /**
     *
     * @param nubitAddress
     */
    public void setNubitsAddress(String nubitAddress) {
        this.nubitAddress = nubitAddress;
    }

    /**
     *
     * @return
     */
    public String getRpcUser() {
        return rpcUser;
    }

    /**
     *
     * @param rpcUser
     */
    public void setRpcUser(String rpcUser) {
        this.rpcUser = rpcUser;
    }

    /**
     *
     * @return
     */
    public String getRpcPass() {
        return rpcPass;
    }

    /**
     *
     * @param rpcPass
     */
    public void setRpcPass(String rpcPass) {
        this.rpcPass = rpcPass;
    }

    /**
     *
     * @return
     */
    public String getNudIp() {
        return nudIp;
    }

    /**
     *
     * @param nudIp
     */
    public void setNudIp(String nudIp) {
        this.nudIp = nudIp;
    }

    /**
     *
     * @return
     */
    public int getNudPort() {
        return nudPort;
    }

    /**
     *
     * @param nudPort
     */
    public void setNudPort(int nudPort) {
        this.nudPort = nudPort;
    }

    /**
     *
     * @return
     */
    public double getPriceIncrement() {
        return priceIncrement;
    }

    /**
     *
     * @param priceIncrement
     */
    public void setPriceIncrement(double priceIncrement) {
        this.priceIncrement = priceIncrement;
    }

    /**
     *
     * @return
     */
    public double getTxFee() {
        return txFee;
    }

    /**
     *
     * @param txFee
     */
    public void setTxFee(double txFee) {
        this.txFee = txFee;
    }

    /**
     *
     * @return
     */
    public String getExchangeName() {
        return exchangeName;
    }

    /**
     *
     * @param exchangeName
     */
    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    /**
     *
     * @return
     */
    public CurrencyPair getPair() {
        return pair;
    }

    /**
     *
     * @param pair
     */
    public void setPair(CurrencyPair pair) {
        this.pair = pair;
    }

    /**
     *
     * @return
     */
    public int getExecuteStrategyInterval() {
        return executeStrategyInterval;
    }

    /**
     *
     * @param executeStrategyInterval
     */
    public void getExecuteStrategyInterval(int executeStrategyInterval) {
        this.executeStrategyInterval = executeStrategyInterval;
    }

    /**
     *
     * @return
     */
    public int getSendLiquidityInteval() {
        return sendLiquidityInterval;
    }

    /**
     *
     * @param sendLiquidityInterval
     */
    public void setSendLiquidityInteval(int sendLiquidityInterval) {
        this.sendLiquidityInterval = sendLiquidityInterval;
    }

    /**
     *
     * @return
     */
    public boolean isSendHipchat() {
        return sendHipchat;
    }

    /**
     *
     * @param sendHipchat
     */
    public void setSendHipchat(boolean sendHipchat) {
        this.sendHipchat = sendHipchat;
    }

    /**
     *
     * @return
     */
    public boolean isSendMails() {
        return sendMails;
    }

    /**
     *
     * @param sendMails
     */
    public void setSendMails(boolean sendMails) {
        this.sendMails = sendMails;
    }

    /**
     *
     * @return
     */
    public String getMailRecipient() {
        return mailRecipient;
    }

    /**
     *
     * @param mailRecipient
     */
    public void setMailRecipient(String mailRecipient) {
        this.mailRecipient = mailRecipient;
    }

    /**
     *
     * @return
     */
    public SecondaryPegOptionsJSON getSecondaryPegOptions() {
        return secondaryPegOptions;
    }

    /**
     *
     * @param secondaryPegOptions
     */
    public void setCryptoPegOptions(SecondaryPegOptionsJSON secondaryPegOptions) {
        this.secondaryPegOptions = secondaryPegOptions;
    }

    public boolean isMultipleCustodians() {
        return multipleCustodians;
    }

    public void setMultipleCustodians(boolean multipleCustodians) {
        this.multipleCustodians = multipleCustodians;
    }

    public double getMaxSellVolume() {
        return maxSellVolume;
    }

    public void setMaxSellVolume(double maxSellVolume) {
        this.maxSellVolume = maxSellVolume;
    }

    public double getMaxBuyVolume() {
        return maxBuyVolume;
    }

    public void setMaxBuyVolume(double maxBuyVolume) {
        this.maxBuyVolume = maxBuyVolume;
    }

    /**
     *
     * @param paths
     * @return
     */
    public static OptionsJSON parseOptions(String[] paths) {
        OptionsJSON options = null;
        ArrayList<String> filePaths = new ArrayList();
        filePaths.addAll(Arrays.asList(paths));

        try {
            JSONObject inputJSON = parseFiles(filePaths);
            JSONObject optionsJSON = (JSONObject) inputJSON.get("options");


            //First try to parse compulsory parameters
            String exchangeName = (String) optionsJSON.get("exchangename");

            String apiKey = "";


            if (!exchangeName.equalsIgnoreCase(Constant.CCEX)) { //for ccex this parameter can be omitted
                if (!optionsJSON.containsKey("apikey")) {
                    Utils.exitWithMessage("The apikey parameter is compulsory.");
                } else {
                    apiKey = (String) optionsJSON.get("apikey");
                }

            }

            String apiSecret = (String) optionsJSON.get("apisecret");

            String mailRecipient = (String) optionsJSON.get("mail-recipient");

            String pairStr = (String) optionsJSON.get("pair");
            CurrencyPair pair = CurrencyPair.getCurrencyPairFromString(pairStr, "_");

            boolean aggregate = true; //true only for USD
            if (!pair.getPaymentCurrency().getCode().equalsIgnoreCase("USD")) {
                aggregate = false; //default to false
            }



            boolean dualside = (boolean) optionsJSON.get("dualside");


            //Based on the pair, set a parameter do define whether setting SecondaryPegOptionsJSON i necessary or not
            boolean requireCryptoOptions = Utils.requiresSecondaryPegStrategy(pair);
            org.json.JSONObject pegOptionsJSON;
            SecondaryPegOptionsJSON cpo = null;
            if (requireCryptoOptions) {

                if (optionsJSON.containsKey("secondary-peg-options")) {

                    Map setMap = new HashMap();


                    //convert from simple JSON to org.json.JSONObject
                    JSONObject oldObject = (JSONObject) optionsJSON.get("secondary-peg-options");

                    Set tempSet = oldObject.entrySet();
                    for (Object o : tempSet) {
                        Map.Entry entry = (Map.Entry) o;
                        setMap.put(entry.getKey(), entry.getValue());
                    }

                    pegOptionsJSON = new org.json.JSONObject(setMap);
                    cpo = SecondaryPegOptionsJSON.create(pegOptionsJSON, pair);
                } else {
                    LOG.severe("secondary-peg-options are required in the options");
                    System.exit(0);
                }

                /*
                 org.json.JSONObject jsonString = new org.json.JSONObject(optionsString);
                 org.json.JSONObject optionsJSON2 = (org.json.JSONObject) jsonString.get("options");
                 pegOptionsJSON = (org.json.JSONObject) optionsJSON2.get("secondary-peg-options");
                 cpo = SecondaryPegOptionsJSON.create(pegOptionsJSON, pair);*/
            }

            //Then parse optional settings. If not use the default value declared here

            String nudIp = "127.0.0.1";
            boolean sendMails = true;
            boolean submitLiquidity = true;
            boolean executeOrders = true;
            boolean verbose = false;
            boolean sendHipchat = true;

            boolean multipleCustodians = false;
            int executeStrategyInterval = 41;
            int sendLiquidityInterval = Integer.parseInt(Global.settings.getProperty("submit_liquidity_seconds"));

            double txFee = 0.2;
            double priceIncrement = 0.0003;
            double keepProceeds = 0;

            double maxSellVolume = 0;
            double maxBuyVolume = 0;


            int emergencyTimeout = 60;

            if (optionsJSON.containsKey("nudip")) {
                nudIp = (String) optionsJSON.get("nudip");
            }

            if (optionsJSON.containsKey("priceincrement")) {
                priceIncrement = Utils.getDouble(optionsJSON.get("priceincrement"));
            }

            if (optionsJSON.containsKey("txfee")) {
                txFee = Utils.getDouble(optionsJSON.get("txfee"));
            }

            if (optionsJSON.containsKey("submit-liquidity")) {
                submitLiquidity = (boolean) optionsJSON.get("submit-liquidity");
            }

            if (optionsJSON.containsKey("max-sell-order-volume")) {
                maxSellVolume = Utils.getDouble(optionsJSON.get("max-sell-order-volume"));
            }

            if (optionsJSON.containsKey("max-buy-order-volume")) {
                maxBuyVolume = Utils.getDouble(optionsJSON.get("max-buy-order-volume"));
            }

            //Now require the parameters only if submitLiquidity is true, otherwise can use the default value

            String nubitAddress = "", rpcPass = "", rpcUser = "";
            int nudPort = 9091;

            if (submitLiquidity) {
                if (optionsJSON.containsKey("nubitaddress")) {
                    nubitAddress = (String) optionsJSON.get("nubitaddress");
                } else {
                    Utils.exitWithMessage("When submit-liquidity is set to true "
                            + "you need to declare a value for \"nubitaddress\" ");
                }

                if (optionsJSON.containsKey("rpcpass")) {
                    rpcPass = (String) optionsJSON.get("rpcpass");
                } else {
                    Utils.exitWithMessage("When submit-liquidity is set to true "
                            + "you need to declare a value for \"rpcpass\" ");
                }

                if (optionsJSON.containsKey("rpcuser")) {
                    rpcUser = (String) optionsJSON.get("rpcuser");
                } else {
                    Utils.exitWithMessage("When submit-liquidity is set to true "
                            + "you need to declare a value for \"rpcuser\" ");
                }

                if (optionsJSON.containsKey("nudport")) {
                    long nudPortlong = (long) optionsJSON.get("nudport");
                    nudPort = (int) nudPortlong;
                } else {
                    Utils.exitWithMessage("When submit-liquidity is set to true "
                            + "you need to declare a value for \"nudport\" ");
                }

            }


            if (optionsJSON.containsKey("executeorders")) {
                executeOrders = (boolean) optionsJSON.get("executeorders");
            }

            if (optionsJSON.containsKey("verbose")) {
                verbose = (boolean) optionsJSON.get("verbose");
            }

            if (optionsJSON.containsKey("hipchat")) {
                sendHipchat = (boolean) optionsJSON.get("hipchat");
            }

            if (optionsJSON.containsKey("mail-notifications")) {
                sendMails = (boolean) optionsJSON.get("mail-notifications");
            }


            /*Ignore this parameter to prevent one custodian to execute faster than others (walls collapsing)
             if (optionsJSON.containsKey("check-balance-interval")) {
             long checkBalanceIntervallong = (long) optionsJSON.get("check-balance-interval");
             checkBalanceInterval = (int) checkBalanceIntervallong;
             }

             if (optionsJSON.containsKey("check-orders-interval")) {
             long checkOrdersIntevallong = (long) optionsJSON.get("check-orders-interval");
             checkOrdersInteval = (int) checkOrdersIntevallong;
             }
             */

            if (optionsJSON.containsKey("emergency-timeout")) {
                long emergencyTimeoutLong = (long) optionsJSON.get("emergency-timeout");
                emergencyTimeout = (int) emergencyTimeoutLong;
            }

            if (optionsJSON.containsKey("keep-proceeds")) {
                keepProceeds = Utils.getDouble((optionsJSON.get("keep-proceeds")));
            }

            if (optionsJSON.containsKey("multiple-custodians")) {
                multipleCustodians = (boolean) optionsJSON.get("multiple-custodians");
            }
            //Create a new Instance
            options = new OptionsJSON(dualside, apiKey, apiSecret, nubitAddress, rpcUser,
                    rpcPass, nudIp, nudPort, priceIncrement, txFee, submitLiquidity, exchangeName,
                    executeOrders, verbose, pair, executeStrategyInterval,
                    sendLiquidityInterval, sendHipchat, sendMails, mailRecipient,
                    emergencyTimeout, keepProceeds, aggregate, multipleCustodians, maxSellVolume, maxBuyVolume, cpo);

        } catch (NumberFormatException e) {
            LOG.severe("Error while parsing the options file : " + e);
        }
        return options;
    }

    /*
     * Concatenate a list of of files into a JSONObject
     */
    public static JSONObject parseFiles(ArrayList<String> filePaths) {
        JSONObject optionsObject = new JSONObject();
        Map setMap = new HashMap();

        for (int i = 0; i < filePaths.size(); i++) {
            try {
                JSONParser parser = new JSONParser();

                JSONObject fileJSON = (JSONObject) (parser.parse(FileSystem.readFromFile(filePaths.get(i))));
                JSONObject tempOptions = (JSONObject) fileJSON.get("options");

                Set tempSet = tempOptions.entrySet();
                for (Object o : tempSet) {
                    Map.Entry entry = (Map.Entry) o;
                    setMap.put(entry.getKey(), entry.getValue());
                }

            } catch (ParseException ex) {
                LOG.severe("Parse exception \n" + ex.toString());
                System.exit(0);
            }
        }

        JSONObject content = new JSONObject(setMap);
        optionsObject.put("options", content);
        return optionsObject;
    }

    /**
     *
     * @return
     */
    public int getEmergencyTimeout() {
        return emergencyTimeout;
    }

    /**
     *
     * @param emergencyTimeoutMinutes
     */
    public void setEmergencyTimeoutMinutes(int emergencyTimeoutMinutes) {
        this.emergencyTimeout = emergencyTimeoutMinutes;
    }

    public double getKeepProceeds() {
        return keepProceeds;
    }

    public void setKeepProceeds(double keepProceeds) {
        this.keepProceeds = keepProceeds;
    }

    public String getNubitAddress() {
        return nubitAddress;
    }

    public void setNubitAddress(String nubitAddress) {
        this.nubitAddress = nubitAddress;
    }

    public boolean isAggregate() {
        return aggregate;
    }

    public void setAggregate(boolean aggregate) {
        this.aggregate = aggregate;
    }

    @Override
    public String toString() {
        String cryptoOptions = "";
        if (secondaryPegOptions != null) {
            cryptoOptions = secondaryPegOptions.toString();
        }
        return "OptionsJSON{" + "dualSide=" + dualSide + ", submitLiquidity=" + submitLiquidity + ", executeOrders=" + executeOrders + ", verbose=" + verbose + ", sendHipchat=" + sendHipchat + ", apiKey=" + apiKey + ", apiSecret=" + apiSecret + ", nubitAddress=" + nubitAddress + ", rpcUser=" + rpcUser + ", rpcPass=" + rpcPass + ", nudIp=" + nudIp + ", nudPort=" + nudPort + ", priceIncrement=" + priceIncrement + ", txFee=" + txFee + ", exchangeName=" + exchangeName + ", pair=" + pair + ", executeStrategyInterval=" + executeStrategyInterval + ", sendLiquidityInterval=" + sendLiquidityInterval + ", sendMails=" + sendMails + ", mailRecipient=" + mailRecipient + "emergencyTimeoutMinutes " + emergencyTimeout + "keepProceeds=" + keepProceeds + "aggregate=" + aggregate + " , waitBeforeShift=" + multipleCustodians + " , cryptoPegOptions=" + cryptoOptions + '}';
    }

    //Same as above, without printing api secret key and RCP password (for logging purposes)
    /**
     *
     * @return
     */
    public String toStringNoKeys() {
        String cryptoOptions = "";
        if (secondaryPegOptions != null) {
            cryptoOptions = secondaryPegOptions.toHtmlString();
        }
        return "Options : {<br>" + "dualSide=" + dualSide + "<br> submitLiquidity=" + submitLiquidity + "<br> executeOrders=" + executeOrders + "<br> verbose=" + verbose + "<br> sendHipchat=" + sendHipchat + "<br> apiKey=" + apiKey + "<br> nubitAddress=" + nubitAddress + "<br> rpcUser=" + rpcUser + "<br> nudIp=" + nudIp + "<br> nudPort=" + nudPort + "<br> priceIncrement=" + priceIncrement + "<br> txFee=" + txFee + "<br> exchangeName=" + exchangeName + "<br> pair=" + pair + "<br> executeStrategyInterval=" + executeStrategyInterval + "<br> sendLiquidityInterval=" + sendLiquidityInterval + "<br> sendMails=" + sendMails + "<br> mailRecipient=" + mailRecipient + "<br> emergencyTimeoutMinutes " + emergencyTimeout + "<br> keepProceeds=" + keepProceeds + "<br> aggregate=" + aggregate + " <br><br>" + cryptoOptions + '}';
    }
}
