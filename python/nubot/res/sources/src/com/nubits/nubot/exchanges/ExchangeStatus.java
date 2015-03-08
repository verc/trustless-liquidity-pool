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
package com.nubits.nubot.exchanges;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class ExchangeStatus {

//Class Variables
    public static final String STOPPED = "Bot Stopped";
    public static final String RUNNING = "Bot Running";
    private String botStatusString;

//Constructor
    public ExchangeStatus(String botStatusString) throws WrongBotStatusException {
        if (botStatusString.equals(STOPPED) || botStatusString.equals(RUNNING)) {
            this.botStatusString = botStatusString;
        } else {
            throw new WrongBotStatusException("Invalid botStatusString");
        }

    }

//Methods
    public String getBotStatusString() {
        return botStatusString;
    }

    public void setBotStatusString(String botStatusString) {
        this.botStatusString = botStatusString;
    }

    public class WrongBotStatusException extends Exception {

        public WrongBotStatusException() {
            super();
        }

        public WrongBotStatusException(String message) {
            super(message);
        }

        public WrongBotStatusException(String message, Throwable cause) {
            super(message, cause);
        }

        public WrongBotStatusException(Throwable cause) {
            super(cause);
        }
    }
}
