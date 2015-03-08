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

import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.models.ApiError;
import com.nubits.nubot.models.ApiResponse;
import com.nubits.nubot.models.Balance;
import com.nubits.nubot.models.Currency;
import com.nubits.nubot.models.CurrencyPair;
import com.nubits.nubot.models.Order;
import com.nubits.nubot.trading.keys.ApiKeys;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * This interface can be used to trade Please refer to readme to best practice
 * for the implementation
 *
 * @author desrever
 * @see Balance
 * @see ApiResponse
 * @see Order
 * @see ApiError
 * @see ServiceInterface
 *
 */
public interface TradeInterface {

    /**
     * Returns the balances associated with the user's account
     *
     * @param pair
     * @return A Balance object containing all the balance made available for
     * the two pairs. an ApiError in case of error.
     */
    public ApiResponse getAvailableBalances(CurrencyPair pair);

    /**
     * Returns the available balance (not on order) associated with the specific
     * currency
     *
     * @param currency The desired currency
     * @return return the Amount object containing the specific balance. an
     * ApiError in case of error.
     */
    public ApiResponse getAvailableBalance(Currency currency);

    /**
     * Return the last price of one pair.orderCurrency expressed in
     * pair.paymentCurrency
     *
     * @param pair pair.orderCurrency is the currency of interest and
     * pair.paymentCurrency is unit for its price
     * @return the last,bid,and ask price from the public ticker of the exchange
     * encapsulated into a Ticker object an ApiError in case of error.
     *
     */
    public ApiResponse getLastPrice(CurrencyPair pair);

    /**
     * Place a sell order
     *
     * @param pair pair.orderCurrency is the currency to order and
     * pair.paymentCurrency is unit for its redeem
     * @param amount the amount of pair.OrderCurrency to sell
     * @param rate the price rate for the order expressed in
     * pair.paymentCurrency
     * @return the String message of the response, containing the order id . an
     * ApiError in case of error.
     */
    public ApiResponse sell(CurrencyPair pair, double amount, double rate);

    /**
     * Place a buy order
     *
     * @param pair pair.orderCurrency is the currency to order and
     * pair.paymentCurrency is unit for its payment
     * @param amount the amount of pair.OrderCurrency to buy
     * @param rate the price rate for the order pair.paymentCurrency
     * @return the String message of the response, containing the order id an
     * ApiError in case of error.
     */
    public ApiResponse buy(CurrencyPair pair, double amount, double rate);

    /**
     * Get active orders
     *
     * @return list of active* orders (*on order of partially filled) in the
     * form of ArrayList<Order>
     * an ApiError in case of error.
     */
    public ApiResponse getActiveOrders();

    /**
     * Get active orders for a specific CurrencyPair
     *
     * @param pair pair.orderCurrency is the currency on order and
     * pair.paymentCurrency is unit for its payment/redeem
     * @return list of active* orders for the specified CurrencyPair (*on order
     * of partially filled) wrapped into ArrayList<Order>
     * an ApiError in case of error.
     */
    public ApiResponse getActiveOrders(CurrencyPair pair);

    /**
     * Get details for an active order
     *
     * @param orderID The id of the order (exchange specific)
     * @return the details of the order wrapped into an Order object an ApiError
     * in case of error.
     */
    public ApiResponse getOrderDetail(String orderID);

    /**
     * Cancel an order
     *
     * @param orderID The id of the order (exchange specific)
     * @param pair The currency pair of the order. Ignored by most exchanged,
     * needed by some
     * @return an ApiResponse object with the a boolean object (true if
     * succesful) an ApiError in case of error.
     */
    public ApiResponse cancelOrder(String orderID, CurrencyPair pair);

    /**
     * Get the transaction fee
     *
     * @return an ApiResponse object with the response. Double , i.e 0.2 an
     * ApiError in case of error.
     */
    public ApiResponse getTxFee();

    /**
     * Get the transaction fee for a specific currency
     *
     * @param pair pair.orderCurrency is the currency on order and
     * pair.paymentCurrency is unit for its payment/redeem
     * @return an ApiResponse object with the response Double decimal, i.e 0.2
     * an ApiError in case of error.
     */
    public ApiResponse getTxFee(CurrencyPair pair);

    /**
     * Get the last trades associated with the account. The range of time
     * depends on the exchange's default range
     *
     * @param pair pair.orderCurrency is the currency on order and
     * pair.paymentCurrency is unit for its payment/redeem
     * @return an ApiResponse object with an array of Trades
     */
    public ApiResponse getLastTrades(CurrencyPair pair);

    /**
     * Get the last trades associated with the account. The range of time
     * depends on the exchange's default range
     *
     * @param pair pair.orderCurrency is the currency on order and
     * pair.paymentCurrency is unit for its payment/redeem
     * @param startDate a unix-timestamp (seconds) indicating the start of the
     * period
     * @return an ApiResponse object with an array of Trades
     */
    public ApiResponse getLastTrades(CurrencyPair pair, long startTime);

    /**
     * Get the transaction fee for a specific currency
     *
     * @param id The order id
     * @return an ApiResponse object with the boolean response an ApiError in
     * case of error.
     */
    public ApiResponse isOrderActive(String id);

    /**
     * Delete all active orders. Async
     *
     * @param pair The currency pair of the order. Ignored by most exchanged,
     * needed by some
     * @return an ApiResponse object with the boolean response (positive,
     * submitted ok) an ApiError in case of error.
     */
    public ApiResponse clearOrders(CurrencyPair pair);

    /**
     * Get the ApiError associated with an error code
     *
     * @param code The error code
     * @return The ApiResponse with an ApiPermission object, or ApiError
     * requested
     */
    public ApiError getErrorByCode(int code);

    /**
     * Get the url for checking connection with exchange
     *
     * @return the url as a String
     */
    public String getUrlConnectionCheck();

    /**
     * Calls the HTTP query to a specific URL
     *
     * @param url A String with the URL of the entrypoint of the API
     * @param args a list of parameters as arguments of the query
     * @param isGet
     * @return A String with the raw HTTP response
     */
    public String query(String url, HashMap<String, String> args, boolean isGet);

    /**
     * Calls the HTTP query to a specific URL
     *
     * @param base A String with the base URL of the API server
     * @param method A String with the entry point of the API method
     * @param args a list of parameters as arguments of the query
     * @param isGet
     * @return A String with the raw HTTP response
     */
    public String query(String base, String method, HashMap<String, String> args, boolean isGet);

    /**
     * Calls the HTTP query to a specific URL with parameters sorted
     * alphabetically
     *
     * @param url A String with the URL of the entrypoint of the API
     * @param args a list of parameters as arguments of the query
     * @param isGet
     * @return A String with the raw HTTP response
     */
    public String query(String url, TreeMap<String, String> args, boolean isGet);

    /**
     * Calls the HTTP query to a specific URL with parameters sorted
     * alphabetically
     *
     * @param base A String with the base URL of the API server
     * @param method A String with the entry point of the API method
     * @param args a list of parameters as arguments of the query
     * @param isGet
     * @return A String with the raw HTTP response
     */
    public String query(String base, String method, TreeMap<String, String> args, boolean isGet);

    /**
     *
     * @param keys
     */
    public void setKeys(ApiKeys keys);

    /**
     *
     * @param exchange
     */
    public void setExchange(Exchange exchange);

    /**
     *
     * @param apiBaseUrl
     */
    public void setApiBaseUrl(String apiBaseUrl);
}
