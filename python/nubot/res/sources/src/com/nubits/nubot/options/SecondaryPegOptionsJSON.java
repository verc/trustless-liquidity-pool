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

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import com.nubits.nubot.models.CurrencyPair;
import java.util.ArrayList;
import java.util.logging.Logger;
import org.json.JSONException;

/**
 *
 * @author advanced
 */
public class SecondaryPegOptionsJSON {

    private static final Logger LOG = Logger.getLogger(SecondaryPegOptionsJSON.class.getName());
    //Compulsory settings ----------------------------
    private String mainFeed;
    private ArrayList<String> backupFeedNames;
    //Optional settings with a default value  ----------------------------
    private double wallchangeThreshold, spread, distanceThreshold;

    /**
     *
     * @param refreshTime
     * @param wallchangeThreshold
     * @param spread
     * @param distanceThreshold
     * @param mainFeed
     * @param backupFeedNames
     */
    public SecondaryPegOptionsJSON(double wallchangeThreshold, double spread, double distanceThreshold, String mainFeed, ArrayList<String> backupFeedNames) {
        this.wallchangeThreshold = wallchangeThreshold;
        this.spread = spread;
        this.distanceThreshold = distanceThreshold;
        this.mainFeed = mainFeed;
        this.backupFeedNames = backupFeedNames;

    }

    /**
     *
     * @param optionsJSON
     * @return
     */
    public static SecondaryPegOptionsJSON create(org.json.JSONObject optionsJSON, CurrencyPair pair) {
        OptionsJSON options = null;
        try {
            //First try to parse compulsory parameters

            String mainFeed = (String) optionsJSON.get("main-feed");


            ArrayList<String> backupFeedNames = new ArrayList<>();
            org.json.JSONObject dataJson = (org.json.JSONObject) optionsJSON.get("backup-feeds");

            //Iterate on backupFeeds

            String names[] = org.json.JSONObject.getNames(dataJson);
            if (names.length < 2) {
                LOG.severe("The bot requires at least two backup data feeds to run");
                System.exit(0);
            }
            for (int i = 0; i < names.length; i++) {
                try {
                    org.json.JSONObject tempJson = dataJson.getJSONObject(names[i]);
                    backupFeedNames.add((String) tempJson.get("name"));
                } catch (JSONException ex) {
                    LOG.severe(ex.toString());
                    System.exit(0);
                }
            }

            //Then parse optional settings. If not use the default value declared here

            //set the refresh time according to the global trading pair
            long refreshTime;
            if (pair.getPaymentCurrency().isFiat()) {
                refreshTime = 8 * 60 * 59 * 1000; //8 hours;
            } else {
                refreshTime = 61;
            }
            double wallchangeThreshold = 0.5;
            double spread = 0;
            double distanceThreshold = 10;

            if (optionsJSON.has("wallshift-threshold")) {
                wallchangeThreshold = new Double((optionsJSON.get("wallshift-threshold")).toString());
            }

            if (optionsJSON.has("spread")) {
                spread = new Double((optionsJSON.get("spread")).toString());
                if (spread != 0) {
                    LOG.warning("You are using the \"spread\" != 0 , which is not reccomented by Nu developers for purposes different from testing.");
                }

            }


            if (optionsJSON.has("price-distance-threshold")) {
                distanceThreshold = new Double((optionsJSON.get("price-distance-threshold")).toString());
            }


            /* ignore the refresh-time parameter to avoid single custodians checking faster than others (causing self-executing orders)
             if (optionsJSON.has("refresh-time")) {
             refreshTime = new Integer((optionsJSON.get("refresh-time")).toString());
             }
             */

            return new SecondaryPegOptionsJSON(wallchangeThreshold, spread, distanceThreshold, mainFeed, backupFeedNames);
        } catch (JSONException ex) {
            LOG.severe(ex.toString());
            System.exit(0);
        }
        return null; //never reached
    }

    /**
     *
     * @return
     */
    public double getWallchangeThreshold() {
        return wallchangeThreshold;
    }

    /**
     *
     * @param wallchangeThreshold
     */
    public void setWallchangeThreshold(double wallchangeThreshold) {
        this.wallchangeThreshold = wallchangeThreshold;
    }

    /**
     *
     * @return
     */
    public double getSpread() {
        return spread;
    }

    /**
     *
     * @param spread
     */
    public void setSpread(double spread) {
        this.spread = spread;
    }

    /**
     *
     * @return
     */
    public double getDistanceThreshold() {
        return distanceThreshold;
    }

    /**
     *
     * @param distanceThreshold
     */
    public void setDistanceThreshold(double distanceThreshold) {
        this.distanceThreshold = distanceThreshold;
    }

    /**
     *
     * @return
     */
    public String getMainFeed() {
        return mainFeed;
    }

    /**
     *
     * @param mainFeed
     */
    public void setMainFeed(String mainFeed) {
        this.mainFeed = mainFeed;
    }

    /**
     *
     * @return
     */
    public ArrayList<String> getBackupFeedNames() {
        return backupFeedNames;
    }

    /**
     *
     * @param backupFeedNames
     */
    public void setBackupFeedNames(ArrayList<String> backupFeedNames) {
        this.backupFeedNames = backupFeedNames;
    }

    @Override
    public String toString() {
        return "SecondaryPegOptionsJSON [" + "backupFeedNames " + backupFeedNames + " " + "distanceThreshold " + distanceThreshold + "mainFeed " + mainFeed + " " + "spread " + spread + "  " + "wallchangeThreshold " + wallchangeThreshold + "]";
    }

    String toHtmlString() {
        return "SecondaryPegOptionsJSON : <br>" + "backupFeedNames " + backupFeedNames + " <br>" + "distanceThreshold " + distanceThreshold + "<br>" + "mainFeed " + mainFeed + " <br>" + "spread " + spread + " <br>" + "wallchangeThreshold " + wallchangeThreshold;
    }
}
