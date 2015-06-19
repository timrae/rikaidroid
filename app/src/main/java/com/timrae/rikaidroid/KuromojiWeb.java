/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.timrae.rikaidroid;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Tim on 6/05/2015.
 */
public class KuromojiWeb {
    private static final String HOST_URL = "http://atilika.org/kuromoji/rest/tokenizer/tokenize";
    private static final int TOKENIZE_MODE = 0;

    // Given a URL, establishes an HttpUrlConnection and retrieves
// the web page content as a InputStream, which it returns as
// a string.
    public String postTokenizeQuery(String query) throws IOException {
        InputStream is = null;
        String result;

        URL url = new URL(HOST_URL);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

        try {
            // Setup connection to server
            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);
            // Set parameters
            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            writeStream(out, query, TOKENIZE_MODE);
            // Throw exception if not HTTP OK
            int response = urlConnection.getResponseCode();
            if (response != HttpURLConnection.HTTP_OK) {
                throw new IOException(urlConnection.getResponseMessage());
            }
            // Read Response data
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            result = readStream(in);

        } finally {
            // Close connection and InputStream
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (is != null) {
                is.close();
            }
        }
        return result;
    }

    // Reads an InputStream and converts it to a String.
    private String readStream(InputStream stream) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        String line;
        // read from the urlconnection via the bufferedreader
        while ((line = reader.readLine()) != null) {
            content.append(line + "\n");
        }
        return content.toString();
    }

    // Adds the input arguments to query
    private void writeStream(OutputStream os, String query, int mode) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        StringBuilder result = new StringBuilder();
        result.append("text=");
        result.append(query);
        result.append("&mode=");
        result.append(Integer.toString(mode));
        String args = result.toString();
        writer.write(args);
        writer.flush();
        writer.close();
        os.close();
    }
}
