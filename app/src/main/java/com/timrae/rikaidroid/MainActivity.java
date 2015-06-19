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

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends ActionBarActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_AEDICT_ANALYSIS = 101;
    private static final Pattern KANJI_REGEXP = Pattern.compile("[\u4e00-\u9faf]+");
    private static final Pattern KANA_REGEXP = Pattern.compile("[\u3041-\u309e\uff66-\uff9d\u30a1-\u30fe]+");
    private static final String RUBY = "<ruby><rb>%s</rb><rt>%s</rt></ruby>";
    private static final String AEDICT_SEARCH_INTENT = "sk.baka.aedict3.action.ACTION_SEARCH_JMDICT_NOUI";
    private static final String AEDICT_INTENT = "sk.baka.aedict3.action.ACTION_SEARCH_JMDICT";
    private static final String INTENT_URL = "<a href=\"lookup://%s\">%s</a>";
    private static final boolean USE_KUROMOJI_WEB = true;
    private WebView mWebView;
    private EditText mEditText;
    private WebViewClient mWebClient;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWebView = (WebView) findViewById(R.id.webview);
        mWebClient = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if(uri.getScheme().equals("lookup")) {
                    String query = uri.getHost();
                    Intent i = new Intent(AEDICT_INTENT);
                    i.putExtra("kanjis", query);
                    i.putExtra("showEntryDetailOnSingleResult", true);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    view.reload();
                    return true;
                }

                else{
                    view.loadUrl(url);
                }
                return true;
            }
        };
        mWebView.setWebViewClient(mWebClient);
        mEditText = (EditText) findViewById(R.id.search_src_text);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadContentFromClipboard();
        analyze(null);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_AEDICT_ANALYSIS && resultCode == Activity.RESULT_OK) {
            List<HashMap<String, String>> result = (List<HashMap<String, String>>) data.getSerializableExtra("result");
            StringBuilder builder = getHtmlPreamble();
            addAedictItems(builder, result);
            displayHtml(builder);
        }
    }


    private void loadContentFromClipboard() {
        if (mWebView == null) {
            Log.e(TAG, "Webview is null");
            return;
        }
        // Get clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (!(clipboard.hasPrimaryClip())) {
            Log.w(TAG, "Clipboard is empty");
            return;
        }
        // Get string from clipboard
        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        String pasteData = item.getText().toString();
        //pasteData = "生き返る。俺、新木場駅でキミの寿司が食べたい。";
        if (pasteData == null) {
            Log.w(TAG, "Clipboard doesn't contain text");
            return;
        }
        mEditText.setText(pasteData);
    }

    public void searchAedict(String query) {
        String pasteData = mEditText.getText().toString();
        final Intent intent = new Intent(AEDICT_SEARCH_INTENT);
        intent.putExtra("kanjis",pasteData);
        intent.putExtra("return_results", true);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, REQUEST_AEDICT_ANALYSIS);
    }

    public void analyze(View view) {
        //  Prepend html tags
        String searchQuery = mEditText.getText().toString();
        if (USE_KUROMOJI_WEB) {
            new DownloadWebpageTask().execute(searchQuery);
            return;
        }
        // Analyzer with Aedict if present
        if (isAedictPresent(this)) {
            searchAedict(searchQuery);
            return;
        }
    }

    private StringBuilder getHtmlPreamble (){
        //  Prepend html tags
        StringBuilder builder = new StringBuilder();
        builder.append("<html><body text=\\\"#000000\\\" link=\\\"#E37068\\\" alink=\\\"#E37068\\\" vlink=\\\"#E37068\\\">");
        return builder;
    }

    private void displayHtml(StringBuilder builder) {
        // Close html tags
        builder.append("</body></html>");
        // Display text in webpage
        mWebView.loadDataWithBaseURL(null, builder.toString(), "text/html", "UTF-8", null);
        if (mProgressBar != null) {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    private void addKuromojiWebItems(StringBuilder builder, JSONArray tokens) {
        for (int i = 0; i < tokens.length(); i++) {
            String reading;
            String surface;
            try {
                JSONObject token = tokens.getJSONObject(i);
                //base = token.getString("base");
                reading = token.getString("reading");
                surface = token.getString("surface");

                String formattedText;
                if (hasKanji(surface)) {
                    // Try to join the surface form with any particles following it
                    if (i < tokens.length()-1) {
                        JSONObject nextToken = tokens.getJSONObject(i);
                        String pos = nextToken.getString("pos");
                        if (pos.contains("接続助詞") || pos.contains("助動詞")) {
                            surface += nextToken.getString("surface");
                            i++;
                        }
                    }
                    formattedText = makeFurigana(surface, katToHira(reading));
                    if (isAedictPresent(this)) {
                        formattedText = String.format(INTENT_URL, token.getString("base"), formattedText);
                    }
                } else {
                    formattedText = surface;
                }

                builder.append(formattedText);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void addAedictItems(StringBuilder builder, List<HashMap<String, String>> items) {
        String query = mEditText.getText().toString();
        int start;
        int end;
        int lastEnd = 0;
        // Word match; discard all but the first entry
        if (items.get(0).get("position_in_sentence").equals("")) {
            HashMap<String, String> item0 = items.get(0);
            String reinflectedReading = reinflectReading(query, item0.get("kanji"), item0.get("reading"));
            String formattedText = makeFurigana(query, reinflectedReading);
            builder.append(formattedText);
            return;
        }
        // Sentence match
        for (HashMap<String, String> item : items) {
            // Get the position in the original sentence of the current item
            String[] position = item.get("position_in_sentence").split(",");
            if (position.length != 2) {
                Log.e(TAG, "incorrect position variable returned by Aedict");
            }
            try {
                start = Integer.parseInt(position[0]);
                end = start + Integer.parseInt(position[1]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Inalid position indices returned by Aedict");
                return;
            }
            // Get the word and the furigana
            String originalWord = query.substring(start, end);
            // Prepend any characters which were ignored by Aedict
            String preamble = query.substring(lastEnd, start);
            builder.append(preamble);
            // Prepend the word with furigana
            if (hasKanji(originalWord)) {
                //String furigana = item.get("furigana_anki").split(",")[0];
                String kanji = item.get("kanji").split(",")[0];
                String reading = item.get("reading").split(",")[0];
                if (isKanji(originalWord)) {
                    // Use Aedict's reading directly if the word was entirely made of kanji
                    String formattedText = String.format(RUBY, originalWord, reading);
                    builder.append(formattedText);
                } else {
                    try {
                        String reinflectedReading = reinflectReading(originalWord, kanji, reading);
                        String formattedText = makeFurigana(originalWord, reinflectedReading);
                        builder.append(formattedText);
                    } catch (Exception e) {
                        // If error then just skip the word
                        Log.e(TAG, "Exception making furigana");
                        builder.append(originalWord);
                    }
                }
            } else {
                // If no kanji in the word then just print it verbatim
                builder.append(originalWord);
            }
            // Remember the end index of current match in original sentence
            lastEnd = end;
        }
    }


    public static boolean isAedictPresent(Activity context) {
        final Intent intent = new Intent("sk.baka.aedict3.action.ACTION_SEARCH_JMDICT");
        return !context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
    }

    private static boolean isKanji(String testStr) {
        return KANJI_REGEXP.matcher(testStr).matches();
    }

    private static boolean hasKanji(String testStr) {
        return KANJI_REGEXP.matcher(testStr).find();
    }

    private class DownloadWebpageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... args) {

            // params comes from the execute() call: params[0] is the url.
            try {
                KuromojiWeb web = new KuromojiWeb();
                String query = args[0];
                return web.postTokenizeQuery(query);
            } catch (IOException e) {
                return "Unable to connect to KuroMoji website: \n" + e.getMessage();
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            StringBuilder builder = getHtmlPreamble();
            JSONObject json;
            JSONArray tokens;
            try {
                json = new JSONObject(result);
                tokens = json.getJSONArray("tokens");
                addKuromojiWebItems(builder, tokens);
            } catch (JSONException e) {
                builder.append(result);
            }
            displayHtml(builder);
        }
    }

    /**
     * Add the reading to the kanji as Ruby furigana, ensuring that there is only furigana above
     * the kanji, not above any hiragana included in the word.
     * @param kanji a word in kanji
     * @param reading the hiragana reading for the word
     * @return a String with the reading correctly added to the kanji as Ruby
     */
    private String makeFurigana(String kanji, String reading) {
        Matcher kanaMatcher = KANA_REGEXP.matcher(kanji);
        // All characeters are kanji; simple replacement will work
        if (!kanaMatcher.find()) {
            return String.format(RUBY, kanji, reading);
        }
        // Strip off any kana from the beginning of the word
        StringBuilder output = new StringBuilder();
        if (kanaMatcher.start() == 0) {
            String prefix = kanaMatcher.group();
            kanji = kanji.substring(prefix.length());
            reading = reading.substring(prefix.length());
            output.append(prefix);
            kanaMatcher = KANA_REGEXP.matcher(kanji);
        } else {
            kanaMatcher.reset();
        }
        // Keep track of number of kana added to output to see if the algorithm was successful
        int numKana = output.length();
        // Now step through each kanji
        int lastKanaEnd = 0;
        int lastReadingKanaEnd = 0;
        while (kanaMatcher.find()) {
            // Find the next kana in the kanji string
            int kanaStart = kanaMatcher.start();
            String currentKana = kanaMatcher.group();
            // Extract the kanji in-between the current kana and the previous kana
            String currentKanji = kanji.substring(lastKanaEnd, kanaStart);
            // Set the end index of current kana in kanji string for next loop iteration
            lastKanaEnd = kanaMatcher.end();
            // Find the current kana in the reading string
            // Not perfect. Here we take the first occurrence at least number of kanji after the last kana
            int readingKanaStart = reading.indexOf(currentKana, lastReadingKanaEnd + currentKanji.length());
            // Extract the reading in-between the kana found in the kanji this time and last time
            String currentReading = reading.substring(lastReadingKanaEnd, readingKanaStart);
            // Set the end index of current kana in reading string for next loop iteration
            lastReadingKanaEnd = readingKanaStart + currentKana.length();
            // Append current kanji and reading to the StringBuilder as furigana
            output.append(String.format(RUBY, currentKanji, currentReading));
            // Append the current kana to the StringBuilder (outside the furigana)
            output.append(currentKana);
            // Keep track of number of kana addded to see if the algorithm was successful
            numKana += currentReading.length() + currentKana.length();
        }
        // Add any kanji / reading at the end of the string to the builder
        if (lastKanaEnd < kanji.length()) {
            String currentKanji = kanji.substring(lastKanaEnd+1);
            String currentReading = reading.substring(lastReadingKanaEnd + 1);
            output.append(String.format(RUBY, currentKanji, currentReading));
            numKana += currentReading.length();
        }
        // Do sanity check, returning naiive substitution if it failed
        if (numKana < reading.length()) {
            return String.format(RUBY, kanji, reading);
        }
        return output.toString().trim();
    }

    /**
     * Reinflect the reading returned by Aedict according to the original word
     * @param original original word looked up by Aedict
     * @param kanji kanji of result returned by Aedict (after de-inflection)
     * @param reading reading of result returned by Aedict (after de-inflection)
     * @return what the reading would look like before the de-inflection
     */
    private String reinflectReading(String original, String kanji, String reading) {
        Matcher matcher = KANJI_REGEXP.matcher(kanji);
        int end = 0;
        while (matcher.find()) {
            // do nothing so that we get the last hit
            end = matcher.end();
        }
        String originalVerbEnding = original.substring(end);
        String newVerbEnding = kanji.substring(end);
        String readingBase = reading.substring(0, reading.length() - newVerbEnding.length());
        return readingBase + originalVerbEnding;
    }

    private String katToHira(String katakana) {
        StringBuilder builder = new StringBuilder();
        for (Character katakanachar: katakana.toCharArray()) {
            //char katakanachar = Character.valueOf(katakana.charAt(0));
            // convert char to unicode value
            String katakanahex = Integer.toHexString(katakanachar & 0xFFFF);
            // convert unicode to decimal
            int katakanadecimalNumber = Integer.parseInt(katakanahex, 16);
            // convert hiragana decimal to katakana decimal
            int hiraganadecimalNumber = Integer.valueOf(katakanadecimalNumber) - 96;
            // covert decimal to unicode value
            String hiraganahex = Integer.toString(hiraganadecimalNumber, 16);
            // convert unicode to char
            builder.append((char) Integer.parseInt(String.valueOf(hiraganahex), 16));
        }
        return builder.toString();
    }

}
