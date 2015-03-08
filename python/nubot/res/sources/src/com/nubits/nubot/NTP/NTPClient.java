package com.nubits.nubot.NTP;

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
/**
 *
 * @author desrever <desrever at nubits.com>
 */
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;
import org.apache.commons.net.time.TimeUDPClient;

public final class NTPClient {

    private static final Logger LOG = Logger.getLogger(NTPClient.class.getName());
    private ArrayList<String> hostnames;
    private static final int TIMEOUT = 10 * 1000; //10 seconds timeout

    public NTPClient() {
    }

    private void initHosts() {
        hostnames = new ArrayList<>();
        hostnames.add("ntp.xs4all.nl");
        hostnames.add("nist1-pa.ustiming.org");
        hostnames.add("nist-time-server.eoni.com");
        hostnames.add("time.nist.gov");
        hostnames.add("utcnist.colorado.edu");
        hostnames.add("nist.time.nosc.us");
    }

    public Date getTime(String host) {
        try {
            return getTimeImpl(host);
        } catch (IOException ex) {
            LOG.severe("Cannot read the date from the time server " + host + "\n"
                    + ex.toString());
            return new Date();
        }
    }

    public Date getTime() {
        initHosts();
        boolean found = false;
        for (int i = 0; i < hostnames.size(); i++) {
            try {
                return getTimeImpl(hostnames.get(i));
            } catch (IOException ex) {
                LOG.warning("Problem with timeserver " + hostnames.get(i) + ""
                        + "\n" + ex.toString());
                if (i != hostnames.size() - 1) {
                    LOG.info("Trying next server");
                }
            }
        }
        if (!found) {
            LOG.severe("Cannot update time after querying " + hostnames.size() + " timeservers. ");
            System.exit(0);
        }
        return new Date(); //statement is never reached



    }

    private Date getTimeImpl(String host) throws IOException {
        Date toRet;
        TimeUDPClient client = new TimeUDPClient();
        // We want to timeout if a response takes longer than TIMEOUT seconds
        client.setDefaultTimeout(TIMEOUT);
        client.open();
        toRet = client.getDate(InetAddress.getByName(host));
        client.close();

        return toRet;
    }
}
