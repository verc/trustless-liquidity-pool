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
package com.nubits.nubot.tasks;

import com.nubits.nubot.global.Global;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class CheckConnectionTask extends TimerTask {

    private static final Logger LOG = Logger.getLogger(CheckConnectionTask.class.getName());

//Methods
    @Override
    public void run() {
        String url = Global.exchange.getLiveData().getUrlConnectionCheck();
        Global.exchange.getLiveData().setConnected(isConnectedTo(url));
        LOG.fine("Checking connection to " + url + " -  Connected : " + Global.exchange.getLiveData().isConnected());

    }

    private boolean isConnectedTo(String url) {
        boolean connected = false;
        HttpURLConnection connection = null;
        URL query = null;
        try {
            query = new URL(url);
        } catch (MalformedURLException ex) {
            LOG.severe(ex.toString());
        }
        try {
            connection = (HttpURLConnection) query.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.getOutputStream();
            connected = true;
        } catch (NoRouteToHostException | UnknownHostException ex) {
            connected = false;
            LOG.severe(ex.toString());
        } catch (IOException ex) {
            connected = false;
            LOG.severe(ex.toString());
        }
        return connected;
    }
}
