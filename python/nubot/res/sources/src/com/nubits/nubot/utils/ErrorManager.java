package com.nubits.nubot.utils;

import com.nubits.nubot.exchanges.Exchange;
import com.nubits.nubot.models.ApiError;
import com.nubits.nubot.models.ApiResponse;

/**
 * Created by sammoth on 25/11/14.
 */
public class ErrorManager {
    //ErrorManager Class wants to have an Exchange name as a property
    public Exchange exchangeName = null;

    //Set the errors
    public ApiError genericError = new ApiError(1, "Generic Error");
    public ApiError parseError = new ApiError(2, "Parsing Error");
    public ApiError noConnectionError = new ApiError(3, "No Connection");
    public ApiError nullReturnError = new ApiError(4, "Null Return");
    public ApiError apiReturnError = new ApiError(5, ""); //This shows an error returned by the Exchange API.
    // The description will be filled with the returned value
    public ApiError authenticationError = new ApiError(6, "Authentication Error");



    public void setExchangeName(Exchange exchange) {
        exchangeName = exchange;
    }

}
