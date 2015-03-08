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

import com.nubits.nubot.utils.FileSystem;
import java.io.*;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 *
 * @author desrever
 */
public class FileSystem {

    private static final Logger LOG = Logger.getLogger(FileSystem.class.getName());

    public static void deleteFile(String path) {
        try {
            File file = new File(path);
            if (file.delete()) {
                LOG.fine("file " + file.getName() + " deleted!");
            } else {
                LOG.severe("Delete operation is failed for : " + path);
            }
        } catch (Exception e) {
            LOG.severe(e.toString());
        }
    }

    public static void deleteFile(String path, boolean verbose) {
        try {
            File file = new File(path);
            if (file.delete()) {
                if (verbose) {
                    LOG.info("file " + file.getName() + " deleted!");
                }
            } else {
                if (verbose) {
                    LOG.info("Delete operation is failed for : " + path);
                }
            }
        } catch (Exception e) {
            if (verbose) {
                LOG.info(e.toString());
            }
        }
    }

    public static void mkdir(String path) {
        new File(path).mkdirs();
    }

    public static void writeToFile(String what, String where, boolean append) {
        if (!append) {
            PrintWriter writer;
            try {
                writer = new PrintWriter(where, "UTF-8");
                writer.println(what);
                writer.close();
            } catch (FileNotFoundException ex) {
                LOG.severe(ex.toString());
            } catch (UnsupportedEncodingException ex) {
                LOG.severe(ex.toString());
            }
        } else {
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(where, true)))) {
                out.println(what);
            } catch (IOException e) {
                LOG.severe(e.toString());
            }
        }
    }

    public static String readFromFile(String path) {

        File file = new File(path);

        StringBuilder fileContent = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {

            bufferedReader = new BufferedReader(new FileReader(file));

            String text;
            while ((text = bufferedReader.readLine()) != null) {
                fileContent.append(text);
            }

        } catch (FileNotFoundException ex) {
            LOG.severe(ex.toString());
        } catch (IOException ex) {
            LOG.severe(ex.toString());
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException ex) {
                LOG.severe("File not found " + path + "\n "
                        + ex.toString());
            }
        }

        return fileContent.toString();
    }

    public static ArrayList<String[]> parseCsvFromFile(String path) {
        BufferedReader br = null;
        String line = "";
        String cvsSplitBy = ",";
        ArrayList<String[]> toReturn = new ArrayList<>();
        try {

            br = new BufferedReader(new FileReader(path));
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] tempLine = line.split(cvsSplitBy);
                toReturn.add(tempLine);

            }

        } catch (FileNotFoundException e) {
            LOG.severe(e.toString());
        } catch (IOException e) {
            LOG.severe(e.toString());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    LOG.severe(e.toString());
                }
            }
        }

        return toReturn;
    }
}
