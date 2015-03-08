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
import com.nubits.nubot.utils.FileSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class TestAggregateOptions {

    private static final Logger LOG = Logger.getLogger(TestAggregateOptions.class.getName());
    private ArrayList<String> fileNames = new ArrayList();
    private ArrayList<String> fileContent = new ArrayList();

    //Try to combine multiple json files into one single String
    public static void main(String[] args) {
        if (args.length > 0) {
            TestAggregateOptions test = new TestAggregateOptions();
            test.init(args);
            test.aggregate();

        }
    }

    private void init(String[] args) {
        fileNames.addAll(Arrays.asList(args));
    }

    private void aggregate() {
        Map setMap = new HashMap();

        for (int i = 0; i < fileNames.size(); i++) {
            try {
                JSONParser parser = new JSONParser();

                JSONObject fileJSON = (JSONObject) (parser.parse(FileSystem.readFromFile(fileNames.get(i))));
                JSONObject tempOptions = (JSONObject) fileJSON.get("options");

                Set tempSet = tempOptions.entrySet();
                for (Object o : tempSet) {
                    Entry entry = (Entry) o;
                    setMap.put(entry.getKey(), entry.getValue());
                }

            } catch (ParseException ex) {
                LOG.severe("Parse exception \n" + ex.toString());
                System.exit(0);
            }
        }

        JSONObject optionsObject = new JSONObject();
        optionsObject.put("options", setMap);
    }
}
