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
package com.nubits.nubot.models;

/**
 *
 * @author desrever <desrever@nubits.com>
 */
public class ApiResponse {

//Class Variables
    private boolean positive;
    private Object responseObject;
    private ApiError responseError;

    /**
     *
     */
    public ApiResponse() {
    }

    /**
     *
     * @param positive
     * @param responseObject
     * @param error
     */
    public ApiResponse(boolean positive, Object responseObject, ApiError error) {
        this.positive = positive;
        this.responseObject = responseObject;
        this.responseError = error;
    }

    /**
     *
     * @return
     */
    public boolean isPositive() {
        return positive;
    }

    private void setPositive(boolean positive) {
        this.positive = positive;
    }

    /**
     *
     * @return
     */
    public Object getResponseObject() {
        return responseObject;
    }

    /**
     *
     * @param responseObject
     */
    public void setResponseObject(Object responseObject) {
        this.responseObject = responseObject;
        this.setPositive(true);
    }

    /**
     *
     * @return
     */
    public ApiError getError() {
        return responseError;
    }

    /**
     *
     * @param error
     */
    public void setError(ApiError error) {
        this.responseError = error;
        this.setPositive(false);
    }
}
