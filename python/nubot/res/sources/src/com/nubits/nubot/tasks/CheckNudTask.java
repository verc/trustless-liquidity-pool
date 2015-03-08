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
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 *
 * @author desrever <desrever at nubits.com>
 */
public class CheckNudTask extends TimerTask {

    private static final Logger LOG = Logger.getLogger(CheckNudTask.class.getName());

    @Override
    public void run() {
        LOG.fine("Executing task : CheckNudTask ");
        Global.rpcClient.checkConnection();
        if (Global.rpcClient.isVerbose()) {
            String connectedString = "offline";
            if (Global.rpcClient.isConnected()) {
                connectedString = "online";
            }
            LOG.info("Nud is " + connectedString + " @ " + Global.rpcClient.getIp() + ":" + Global.rpcClient.getPort());
        }

    }
}
