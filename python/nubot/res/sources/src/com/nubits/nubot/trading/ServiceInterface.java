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
package com.nubits.nubot.trading;

/**
 *
 * @author advanced
 */
public interface ServiceInterface {

    /**
     * This interface is used internally by the TradeInterface implementation It
     * is used to encapsulate methods to communicate with the exchange via https
     * API requests.
     *
     * @author desrever
     *
     *
     */
    /**
     * Execute a query
     *
     * @param needAuth True if the specific call requires Authentication
     * @param isGet
     * @return a String with the raw answer from the server
     */
    public String executeQuery(boolean needAuth, boolean isGet);

    /**
     * Sign the request
     *
     * @param secret The secret key
     * @param hash_data The hash data
     * @return A String with the signature
     */
    public String signRequest(String secret, String hash_data);
}
