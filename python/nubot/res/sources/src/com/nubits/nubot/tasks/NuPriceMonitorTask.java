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
package com.nubits.nubot.tasks;

import com.nubits.nubot.global.Global;
import com.nubits.nubot.launch.NuPriceMonitor;
import com.nubits.nubot.models.LastPrice;
import com.nubits.nubot.notifications.HipChatNotifications;
import com.nubits.nubot.notifications.MailNotifications;
import com.nubits.nubot.notifications.jhipchat.messages.Message.Color;
import com.nubits.nubot.pricefeeds.PriceFeedManager;
import com.nubits.nubot.utils.FileSystem;
import com.nubits.nubot.utils.Utils;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 *
 * USE THIS TASK ONLY WITH NuPriceMonitor bot
 */
public class NuPriceMonitorTask extends TimerTask {

    private static final Logger LOG = Logger.getLogger(NuPriceMonitorTask.class.getName());
    private PriceFeedManager pfm = null;
    private double distanceTreshold;
    private LastPrice lastPrice;
    private boolean isFirstTime = true;
    private LastPrice currentWallPEGPrice;
    private double wallchangeThreshold;
    private double sellPriceUSDsingleside, sellPriceUSDdoubleside, buyPriceUSD;
    private String outputPath, recipient;
    private boolean sendEmails;
    private boolean isFirstEmail = true;
    private String emailHistory = "";

    @Override
    public void run() {
        LOG.info("Executing task : CheckLastPriceTask ");
        if (pfm == null) {
            LOG.severe("CheckLastPrice task needs a PriceFeedManager to work. Please assign it before running it");

        } else {
            ArrayList<LastPrice> priceList = pfm.getLastPrices().getPrices();

            LOG.info("CheckLastPrice received values from remote feeds. ");

            LOG.info("Positive response from " + priceList.size() + "/" + pfm.getFeedList().size() + " feeds");
            for (int i = 0; i < priceList.size(); i++) {
                LastPrice tempPrice = priceList.get(i);
                LOG.info(tempPrice.getSource() + ":1 " + tempPrice.getCurrencyMeasured().getCode() + " = "
                        + tempPrice.getPrice().getQuantity() + " " + tempPrice.getPrice().getCurrency().getCode());
            }


            if (priceList.size() == pfm.getFeedList().size()) {
                //All feeds returned a positive value

                //Check if mainPrice is close enough to the others
                // I am assuming that mainPrice is the first element of the list
                if (sanityCheck(priceList, 0)) {
                    //mainPrice is reliable compared to the others
                    this.updateLastPrice(priceList.get(0));
                } else {
                    //mainPrice is not reliable compared to the others
                    //Check if other backup prices are close enough to each other
                    boolean foundSomeValiBackUp = false;
                    LastPrice goodPrice = null;
                    for (int l = 1; l < priceList.size(); l++) {
                        if (sanityCheck(priceList, l)) {
                            goodPrice = priceList.get(l);
                            foundSomeValiBackUp = true;
                            break;
                        }
                    }

                    if (foundSomeValiBackUp) {
                        //goodPrice is a valid price backup!
                        this.updateLastPrice(goodPrice);
                    } else {
                        //None of the source are in accord with others.

                        //Try to send a notification
                        if (Global.options != null) {
                            String title = "Problems while updating " + pfm.getPair().getOrderCurrency().getCode() + " price. Cannot find a reliable feed.";
                            String message = "Positive response from " + priceList.size() + "/" + pfm.getFeedList().size() + " feeds\n";
                            for (int i = 0; i < priceList.size(); i++) {
                                LastPrice tempPrice = priceList.get(i);
                                message += (tempPrice.getSource() + ":1 " + tempPrice.getCurrencyMeasured().getCode() + " = "
                                        + tempPrice.getPrice().getQuantity() + " " + tempPrice.getPrice().getCurrency().getCode()) + "\n";
                            }



                            MailNotifications.send(Global.options.getMailRecipient(), title, message);
                            HipChatNotifications.sendMessage(title + message, Color.RED);
                            LOG.severe(title + message);
                        }
                    }
                }
            } else {
            }

        }
    }

    private boolean sanityCheck(ArrayList<LastPrice> priceList, int mainPriceIndex) {
        //Measure if mainPrice is close to other two values

        boolean[] ok = new boolean[priceList.size() - 1];
        double mainPrice = priceList.get(mainPriceIndex).getPrice().getQuantity();

        //Test mainPrice vs backup sources
        int f = 0;
        for (int i = 0; i < priceList.size(); i++) {
            if (i != mainPriceIndex) {
                LastPrice tempPrice = priceList.get(i);
                double temp = tempPrice.getPrice().getQuantity();
                ok[f] = closeEnough(mainPrice, temp);
                f++;
            }
        }

        int countOk = 0;
        for (int j = 0; j < ok.length; j++) {
            if (ok[j]) {
                countOk++;
            }
        }

        boolean overallOk = false; //is considered ok if the mainPrice is closeEnough to more than a half of backupPrices
        //Need to distinguish pair vs odd
        if (ok.length % 2 == 0) {
            if (countOk >= (int) ok.length / 2) {
                overallOk = true;
            }
        } else {
            if (countOk > (int) ok.length / 2) {
                overallOk = true;
            }
        }

        return overallOk;

    }
    //if temp differs from mainPrice for more than a threshold%, return false

    private boolean closeEnough(double mainPrice, double temp) {
        double distance = Math.abs(mainPrice - temp);

        double percentageDistance = Utils.round(distance * 100 / mainPrice, 4);
        if (percentageDistance > distanceTreshold) {
            return false;
        } else {
            return true;
        }
    }

    public PriceFeedManager getPfm() {
        return pfm;
    }

    public void setPriceFeedManager(PriceFeedManager pfm) {
        this.pfm = pfm;
    }

    public double getDistanceTreshold() {
        return distanceTreshold;
    }

    public void setDistanceTreshold(double distanceTreshold) {
        this.distanceTreshold = distanceTreshold;
    }

    public void updateLastPrice(LastPrice lp) {
        this.lastPrice = lp;
        LOG.info("Price Updated." + lp.getSource() + ":1 " + lp.getCurrencyMeasured().getCode() + " = "
                + "" + lp.getPrice().getQuantity() + " " + lp.getPrice().getCurrency().getCode() + "\n");
        tryMoveWalls();
    }

    public LastPrice getLastPriceFromFeeds() {
        return this.lastPrice;
    }

    private void tryMoveWalls() {

        LOG.info("Executing tryMoveWalls");
        String notificationEmail = "";
        if (isFirstTime) {
            currentWallPEGPrice = lastPrice;
            //Compute price for walls
            computePrices();
            notificationEmail = "First time wall-setup with PEGprice = " + currentWallPEGPrice.getPrice().getQuantity();
            LOG.info(notificationEmail);
        } else {
            //check if price moved more than x% from when the wall was setup
            if (needToMoveWalls(lastPrice)) {
                LOG.severe("We should move the walls now!");
                //Compute price for walls

                currentWallPEGPrice = lastPrice;
                computePrices();

            } else {
                LOG.info("No need to move walls");
            }

        }

        isFirstTime = false;
    }

    private boolean needToMoveWalls(LastPrice last) {
        double currentWallPEGprice = currentWallPEGPrice.getPrice().getQuantity();
        double distance = Math.abs(last.getPrice().getQuantity() - currentWallPEGprice);
        double percentageDistance = Utils.round((distance * 100) / currentWallPEGprice, 4);
        LOG.info("d=" + percentageDistance + "% (old : " + currentWallPEGprice + " new " + last.getPrice().getQuantity() + ")");

        if (percentageDistance < wallchangeThreshold) {
            return false;
        } else {
            return true;
        }
    }

    private void computePrices() {
        //Sell-side custodian sell-wall

        double peg_price = lastPrice.getPrice().getQuantity();

        //convert sell price to PEG
        double sellPricePEG = Utils.round(sellPriceUSDsingleside / peg_price, 8);
        double sellPricePEGdual = Utils.round(sellPriceUSDdoubleside / peg_price, 8);
        double buyPricePEG = Utils.round(buyPriceUSD / peg_price, 8);

        LOG.info("Sell wall prices: \n"
                + "Sell Price (sell-side custodians) " + sellPricePEG + "\n"
                + "Sell Price (dual-side custodians) " + sellPricePEGdual + "\n"
                + "Buy Price  " + buyPricePEG);


        //------------ here for output csv
        String source = currentWallPEGPrice.getSource();
        double price = currentWallPEGPrice.getPrice().getQuantity();
        String currency = currentWallPEGPrice.getPrice().getCurrency().getCode();
        String crypto = pfm.getPair().getOrderCurrency().getCode();

        String row = new Date() + ","
                + source + ","
                + crypto + ","
                + price + ","
                + currency + ","
                + sellPricePEG + ","
                + sellPricePEGdual + ","
                + buyPricePEG + ",";

        String otherPricesAtThisTime = "";

        ArrayList<LastPrice> priceList = pfm.getLastPrices().getPrices();

        for (int i = 0; i < priceList.size(); i++) {
            LastPrice tempPrice = priceList.get(i);
            otherPricesAtThisTime += "{ feed : " + tempPrice.getSource() + " - price : " + tempPrice.getPrice().getQuantity() + "}  ";
        }
        row += otherPricesAtThisTime + "\n";

        if (sendEmails) {
            String title = "[" + pfm.getPair().toString() + "] price changed more than " + wallchangeThreshold + "%";
            if (isFirstEmail) {
                title = "[" + pfm.getPair().toString() + "] price tracking started";
            }

            String messageNow = NuPriceMonitor.HEADER + row;
            emailHistory += messageNow;


            String tldr = pfm.getPair().toString() + " price changed more than " + wallchangeThreshold + "% since last notification: "
                    + "now is " + price + " " + pfm.getPair().getPaymentCurrency().getCode().toUpperCase() + ".\n"
                    + "Here are the prices you should used in the new orders : \n"
                    + "If you are a sell-side custodian, sell at " + sellPricePEG + " " + pfm.getPair().getOrderCurrency().getCode().toUpperCase() + "\n"
                    + "If you area dual-side custodian, sell at " + sellPricePEGdual + " " + pfm.getPair().getOrderCurrency().getCode().toUpperCase() + " "
                    + "and buy at " + buyPricePEG + " " + pfm.getPair().getOrderCurrency().getCode().toUpperCase() + "\n"
                    + "\n#########\n"
                    + "Below you can see the history of price changes. You can copy paste to create a csv report."
                    + "For each row you should have shifted the sell/buy walls.\n\n";
            if (isFirstEmail) {
                tldr = pfm.getPair().getOrderCurrency().getCode().toUpperCase() + " price is now " + price + " " + pfm.getPair().getPaymentCurrency().getCode() + ""
                        + "(" + source + ").\n"
                        + "Here are the prices you should used in the first order : \n"
                        + "If you are a sell-side custodian, sell at " + sellPricePEG + " " + pfm.getPair().getOrderCurrency().getCode().toUpperCase() + "\n"
                        + "If you area dual-side custodian, sell at " + sellPricePEGdual + " " + pfm.getPair().getOrderCurrency().getCode().toUpperCase() + " "
                        + "and buy at " + buyPricePEG + " " + pfm.getPair().getOrderCurrency().getCode().toUpperCase() + ".\nDetails.csv below \n\n\n";
            }

            MailNotifications.send(recipient, title, tldr + emailHistory);
            isFirstEmail = false;


        }
        FileSystem.writeToFile(row, outputPath, true);

        //HTML logging
        /*
         String preHTML = "        <tbody>\n"
         + "            <tr>\n";

         String bodyHTML = "<td>" + new Date() + "</td>"
         + "<td>" + source + "</td>"
         + "<td>" + crypto + "</td>"
         + "<td>" + price + "</td>"
         + "<td>" + currency + "</td>"
         + "<td>" + sellPricePEG + "</td>"
         + "<td>" + sellPricePEGdual + "</td>"
         + "<td>" + buyPricePEG + "</td>"
         + "<td>\n"
         + "\n"
         + "                    <table>\n";

         for (int i = 0; i < priceList.size(); i++) {
         LastPrice tempPrice = priceList.get(i);
         bodyHTML += " <tr><td> { feed : " + tempPrice.getSource() + " - price : " + tempPrice.getPrice().getQuantity() + "}  </td> </tr>";
         }
         String footerHTML = "</table>\n"
         + "                </td>\n"
         + "            </tr>\n"
         + "        </tbody>\n"
         + "    </table>\n"
         + "</body>\n"
         + "</html>";

         FileSystem.writeToFile(preHTML + bodyHTML + footerHTML, outputPath + ".html", true);
         */
    }

    public double getWallchangeThreshold() {
        return wallchangeThreshold;
    }

    public void setWallchangeThreshold(double wallchangeThreshold) {
        this.wallchangeThreshold = wallchangeThreshold;
    }

    public double getSellPriceUSDsingleside() {
        return sellPriceUSDsingleside;
    }

    public void setSellPriceUSDsingleside(double sellPriceUSDsingleside) {
        this.sellPriceUSDsingleside = sellPriceUSDsingleside;
    }

    public double getSellPriceUSDdoubleside() {
        return sellPriceUSDdoubleside;
    }

    public void setSellPriceUSDdoubleside(double sellPriceUSDdoubleside) {
        this.sellPriceUSDdoubleside = sellPriceUSDdoubleside;
    }

    public double getBuyPriceUSD() {
        return buyPriceUSD;
    }

    public void setBuyPriceUSD(double buyPriceUSD) {
        this.buyPriceUSD = buyPriceUSD;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public void setSendEmails(boolean sendEmails) {
        this.sendEmails = sendEmails;
    }
}
