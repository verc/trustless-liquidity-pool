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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public class TestQuick {

    private static final Logger LOG = Logger.getLogger(TestQuick.class.getName());

    public static void main(String[] a) {
        String date = "2014-12-22T17:55:25.107Z";
        String pattern = "yyyy-MM-dd'T'HH:mm:ss";

        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        try {
            Date d = sdf.parse(date);
        } catch (java.text.ParseException pe1) {
            LOG.severe(pe1.toString());
        }

        System.out.println("done");



    }
}
