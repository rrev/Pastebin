package com.revonline.pastebin.trending_pastes;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import com.revonline.pastebin.*;
import com.revonline.pastebin.adapters.PastesListAdapter;
import com.revonline.pastebin.explorepaste.ExplorePaste;
import com.revonline.pastebin.xml.XMLHandler;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.joda.time.Hours;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Marco
 * Date: 01/12/13
 * Time: 12.01
 * To change this template use File | Settings | File Templates.
 */
public class PopPastes extends Activity
{
    public static final String KEY_POP_PASTES = "poppastes";
    private PastesListAdapter adapter;
    private ArrayList<PasteInfo> pasteInfos;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.trendingpastes);

        adapter = new PastesListAdapter(this);

        if (savedInstanceState != null)
        {
            pasteInfos = savedInstanceState.getParcelableArrayList(KEY_POP_PASTES);
        }
        else
        {
            //
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String cached_xml = sharedPreferences.getString("cachexml", null);

            long lastDownload = sharedPreferences.getLong("lastdownload", 0);

            Log.d(MyActivity.DEBUG_TAG, "lastDownload=>"+lastDownload);

            if (cached_xml != null)
            {
                int hours = Hours.hoursBetween(
                        new DateTime(lastDownload),
                        new DateTime()
                ).getHours();

                Log.d(MyActivity.DEBUG_TAG, "diff: " + hours);

                // non è passata un'ora, quindi uso la cache
                if (hours == 0)
                {
                    try
                    {
                        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                        SAXParser parser = saxParserFactory.newSAXParser();
                        XMLReader reader = parser.getXMLReader();
                        XMLHandler handler = new XMLHandler();
                        reader.setContentHandler(handler);
                        reader.parse(new InputSource(new StringReader("<root>" + cached_xml + "</root>")));

                        pasteInfos = handler.data;
                    } catch (ParserConfigurationException e) {
                        e.printStackTrace();
                    } catch (SAXException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (!MyActivity.apiLower11)
        {
            ActionBar actionBar = getActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setTitle(R.string.pastepopolari);

        ListView listView = (ListView) findViewById(R.id.treadingpastes);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(parent.getContext(), ExplorePaste.class);
                intent.putExtra(ExplorePaste.FLAG_EXTRA_PASTE_URL, ((PasteInfo)parent.getItemAtPosition(position)).getPasteKey());
                startActivity(intent);
            }
        });

        listView.setEmptyView(findViewById(R.id.empty));

        if (pasteInfos == null || pasteInfos.size() == 0)
        {
            new DownloadTrendingPastes().execute();
        }
        else
        {
            adapter.setPasteInfoList(pasteInfos);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putParcelableArrayList(KEY_POP_PASTES, pasteInfos);
        Log.d(MyActivity.DEBUG_TAG, "onSaveInstanceState->pasteInfos => " + pasteInfos);
        Log.d(MyActivity.DEBUG_TAG, "outState " + outState.getParcelableArrayList("poppastes"));

        super.onSaveInstanceState(outState);
    }

    DialogInterface.OnClickListener retry = new DialogInterface.OnClickListener() {
        @Override
            public void onClick(DialogInterface dialog, int which) {
            new DownloadTrendingPastes().execute();
        }
    };

    DialogInterface.OnClickListener close = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
    };

    //<Params, Progress, Result>
    class DownloadTrendingPastes extends AsyncTask<Void, Void, String>
    {
        ProgressDialog alertDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();    //To change body of overridden methods use File | Settings | File Templates.

            alertDialog = new ProgressDialog(PopPastes.this);
            alertDialog.setMessage(PopPastes.this.getString(R.string.waitdownloadlist));
            alertDialog.setCancelable(false);
            alertDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent keyEvent)
                {
                    if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP)
                    {
                        finish();
                        return true;
                    }

                    return false;
                }
            });
            alertDialog.show();
        }

        @Override
        protected String doInBackground(Void... params) {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost("http://pastebin.com/api/api_post.php");
            HttpResponse response;
            String bodyresponse = null;
            List<BasicNameValuePair> pairs = new ArrayList<BasicNameValuePair>();

            pairs.add(new BasicNameValuePair("api_option", "trends"));
            pairs.add(new BasicNameValuePair("api_dev_key", SpecialKeys.DEV_KEY));

            try
            {
                post.setEntity(new UrlEncodedFormEntity(pairs));

                response = client.execute(post);
                StatusLine statusLine = response.getStatusLine();

                if (statusLine.getStatusCode() == HttpStatus.SC_OK)
                {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    response.getEntity().writeTo(outputStream);
                    outputStream.close();
                    bodyresponse = outputStream.toString();
                    Log.d(MyActivity.DEBUG_TAG, "bodyresponse == " + bodyresponse);

                    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                    SAXParser parser = saxParserFactory.newSAXParser();
                    XMLReader reader = parser.getXMLReader();
                    XMLHandler handler = new XMLHandler();
                    reader.setContentHandler(handler);
                    reader.parse(new InputSource(new StringReader("<root>" + bodyresponse + "</root>")));

                    pasteInfos = handler.data;
                }
                else
                {
                    response.getEntity().getContent().close();
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (ClientProtocolException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (ParserConfigurationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            return bodyresponse;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        protected void onPostExecute(String xml) {
            super.onPostExecute(xml);    //To change body of overridden methods use File | Settings | File Templates.
            if (PopPastes.this.isFinishing()) return;

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(PopPastes.this).edit();
            editor.putString("cachexml", xml);
            editor.putLong("lastdownload", new DateTime().getMillis());
            editor.commit();

            Log.d(MyActivity.DEBUG_TAG, "pasteInfos = " + pasteInfos);

            alertDialog.dismiss();
            if (pasteInfos != null && pasteInfos.size() > 0)
            {
                adapter.setPasteInfoList(pasteInfos);
            }
            else
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(PopPastes.this);

                if (ErrorMessages.errors.containsKey(xml))
                {
                    builder.setMessage(getString(R.string.msgerrore, "(" + getString(ErrorMessages.errors.get(xml)) + ")"));
                }
                else
                {
                    builder.setMessage(R.string.nointernet);
                }

                // i do in this way cuz i think it can bug
                builder.setPositiveButton(R.string.retry, retry);
                builder.setNegativeButton(R.string.close, close);
                builder.show();
            }
        }
    }
}
