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

/**
 *
 * @author desrever < desrever@nubits.com >
 */
public class ApiPermissions {

//Class Variables
    private boolean valid_keys;
    private boolean deposit;
    private boolean get_info;
    private boolean merchant;
    private boolean trade;
    private boolean withdraw;

//Constructor
    public ApiPermissions() {
    }

    public ApiPermissions(boolean valid_keys, boolean deposit, boolean get_info, boolean merchant, boolean trade, boolean withdraw) {
        this.valid_keys = valid_keys;
        this.deposit = deposit;
        this.get_info = get_info;
        this.merchant = merchant;
        this.trade = trade;
        this.withdraw = withdraw;
    }

//Methods
    /**
     * @return the valid_keys
     */
    public boolean isValid_keys() {
        return valid_keys;
    }

    /**
     * @param valid_keys the valid_keys to set
     */
    public void setValid_keys(boolean valid_keys) {
        this.valid_keys = valid_keys;
    }

    /**
     * @return the deposit
     */
    public boolean isDeposit() {
        return deposit;
    }

    /**
     * @param deposit the deposit to set
     */
    public void setDeposit(boolean deposit) {
        this.deposit = deposit;
    }

    /**
     * @return the get_info
     */
    public boolean isGet_info() {
        return get_info;
    }

    /**
     * @param get_info the get_info to set
     */
    public void setGet_info(boolean get_info) {
        this.get_info = get_info;
    }

    /**
     * @return the merchant
     */
    public boolean isMerchant() {
        return merchant;
    }

    /**
     * @param merchant the merchant to set
     */
    public void setMerchant(boolean merchant) {
        this.merchant = merchant;
    }

    /**
     * @return the trade
     */
    public boolean isTrade() {
        return trade;
    }

    /**
     * @param trade the trade to set
     */
    public void setTrade(boolean trade) {
        this.trade = trade;
    }

    /**
     * @return the withdraw
     */
    public boolean isWithdraw() {
        return withdraw;
    }

    /**
     * @param withdraw the withdraw to set
     */
    public void setWithdraw(boolean withdraw) {
        this.withdraw = withdraw;
    }

    @Override
    public String toString() {
        return "ApiPermissions{" + "valid_keys=" + valid_keys + ", deposit=" + deposit + ", get_info=" + get_info + ", merchant=" + merchant + ", trade=" + trade + ", withdraw=" + withdraw + '}';
    }
}
