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
package com.nubits.nubot.trading.keys;

import com.nubits.nubot.utils.Utils;
import java.util.logging.Logger;

/**
 *
 * @author desrever < desrever@nubits.com >
 */
public class ApiKeys {

    private static final Logger LOG = Logger.getLogger(ApiKeys.class.getName());
    public static final String VALID_KEYS = "These keys are valid! Save them to complete the setup";
//Class Variables
    private String secretKey, apiKey;

//Constructor (private) use the static method loadKeysFromFile instead
    public ApiKeys(String secretKey, String apiKey) {
        this.secretKey = secretKey;
        this.apiKey = apiKey;
    }

//Methods
    /**
     * @return the privateKey
     */
    public String getPrivateKey() {
        return secretKey;
    }

    /**
     * @param privateKey the privateKey to set
     */
    public void setPrivateKey(String privateKey) {
        this.secretKey = privateKey;
    }

    /**
     * @return the apiKey
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * @param apiKey the apiKey to set
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public static ApiKeys loadKeysFromFile(String passphrase, String api_path, String secret_path) {
        ApiKeys toReturn = null;

        String secret = Utils.decode(secret_path, passphrase);
        String api = Utils.decode(api_path, passphrase);


        if (!(secret.equals("-1") || api.equals("-1"))) {
            toReturn = new ApiKeys(secret, api); //Correct passphrase
        } else if (!(secret.equals("-1") || api.equals("-1"))) {
            toReturn = new ApiKeys(secret, api); //Correct passphrase
        }
        return toReturn; //Wrong passphrase default
    }
    /*
     public static String validate(String secret, String api, Exchange exchange) {
     TradeInterface trade = null;
     String toReturn = "";
     switch (exchange.getName()) {
     case Constant.BTCE: {
     trade = new BtceWrapper(new ApiKeys(secret, api), exchange);
     break;
     }
     default: {
     LOG.severe("Exchange " + exchange.getName() + " is not supported");
     return "exchange " + exchange.getName() + " not supported";
     }
     }

     ApiResponse permissionResponse = exchange.getTrade().getPermissions();
     if (permissionResponse.isPositive()) {
     ApiPermissions permissions = (ApiPermissions) permissionResponse.getResponseObject();

     if (permissions.isGet_info() && permissions.isTrade()) {
     toReturn = VALID_KEYS;
     } else {
     toReturn = "The keys of " + exchange.getName() + " works but some permission is missing. Activate at least : get_info and trade";
     }
     } else {
     toReturn = "Authentication failed. Read the tutorial if the problem persist.";
     }

     return toReturn;
     }
     */
}
