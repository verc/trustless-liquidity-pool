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
package com.nubits.nubot.utils.logging;

import com.nubits.nubot.utils.Utils;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 *
 * @author desrever < desrever@nubits.com >
 * adapted from vogella tutorial
 */
public class LogFormatterCSV extends Formatter {
    // This method is called for every log records

    public String format(LogRecord rec) {
        if (rec.getLevel().intValue() >= Level.INFO.intValue()) {
            StringBuffer buf = new StringBuffer(1000);
            buf.append((formatMessage(rec)).replaceAll(",", " ") + "," + rec.getLevel() + "," + rec.getSourceClassName() + "," + rec.getSourceMethodName() + "," + Utils.calcDate(rec.getMillis()) + "\n");
            return buf.toString();
        } else {
            return "";
        }
    }

    // This method is called just after the handler using this
    // formatter is created
    public String getHead(Handler h) {
        return "message,level,source,method,time\n";
    }
}
