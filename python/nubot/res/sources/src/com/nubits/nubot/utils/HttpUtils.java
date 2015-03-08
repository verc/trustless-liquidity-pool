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
package com.nubits.nubot.utils;

import java.net.SocketTimeoutException;
import java.util.Map;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class HttpUtils {

    public static String USER_AGENT = "Mozilla/5.0 (Windows NT 6.2; WOW64; rv:26.0) Gecko/20100101 Firefox/26.0";

    public static Connection getConnectionForPost(String url, Map<String, String> datas) {
        url = appendHttpString(url);
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT).timeout(5000)
                .method(Connection.Method.POST);
        if (datas != null && !datas.isEmpty()) {
            connection.data(datas);
        }
        return connection;
    }

    private static String appendHttpString(String url) {
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        return url;
    }

    public static Connection getConnectionForGetNoCookies(String url, Map<String, String>... datas) {
        url = appendHttpString(url);
        Connection connection = Jsoup.connect(url).userAgent(USER_AGENT).ignoreContentType(true).timeout(5000);
        if (datas != null && datas.length > 0 && !datas[0].isEmpty()) {
            connection.data(datas[0]);
        }

        return connection;
    }

    public static Connection getConnectionForGet(String url, Map<String, String>... datas) {
        return getConnectionForGetNoCookies(url, datas);
    }

    public static String getContentForGet(String url, int timeout) {
        try {
            Document objectDoc;
            try {
                Connection connection = getConnectionForGetNoCookies(url).timeout(timeout);
                objectDoc = connection.get();
            } catch (SocketTimeoutException e) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e1) {
                    // ignore
                }
                Connection connection = getConnectionForGetNoCookies(url).timeout(timeout);
                objectDoc = connection.get();
            }
            return objectDoc.body().text();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
