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
package com.nubits.nubot.tests;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
import com.nubits.nubot.NTP.NTPClient;
import com.nubits.nubot.utils.Utils;
import java.util.logging.Logger;

public class TestNTPClient {

    private static final Logger LOG = Logger.getLogger(TestNTPClient.class.getName());

    public static void main(String[] args) {
        NTPClient client = new NTPClient();

        LOG.info("Seconds untile next window : " + Utils.getSecondsToNextwindow(3));

        //Try multiple servers
        LOG.info("Date (multiple servers) : " + client.getTime());

        //Try single server
        LOG.info("Date (single server) : " + client.getTime("time.nist.gov"));

        System.exit(0);

    }
}
