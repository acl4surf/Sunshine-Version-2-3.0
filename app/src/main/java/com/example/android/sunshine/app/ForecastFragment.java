package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //enable fragment to handle menu events
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        //ArrayAdapter will populate the ListView
        mForecastAdapter = new ArrayAdapter<>(getActivity(),
                R.layout.list_item_forecast, R.id.list_item_forecast_textView,
                new ArrayList<String>());
        ListView listView = (ListView) rootView.findViewById(R.id.listView_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });
        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        /**
         * date time conversion method
         */
        private String getReadableDateString(long time) {
            //OWM API returns  a unix timestamp in seconds
            // convert it to milliseconds to convert to valid date
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * temp high/low conversion method
         */
        private String formatHighLow(double high, double low, String unitType) {

            if(unitType.equals(getString(R.string.pref_units_imperial))) {
                high = (high*1.8)+32;
                low = (low*1.8)+32;
            } else if(!unitType.equals(getString(R.string.pref_units_metric))) {
                Log.e(LOG_TAG, "Unit type not found: "+unitType);
            }
            //round temp double to int
            long roundHigh = Math.round(high); //returns (long)int
            long roundLow = Math.round(low);
            String highLowStr = roundHigh + "/" + roundLow;
            return highLowStr;
        }

        /**
         * forecastJsonStr contains complete forecast in JSON String so extract data
         * Parse data: constructor converts JSON String into an Object hierarchy
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {
            //names of JSON objects  we need to extract
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);
            // OWM returns daily forecasts local time so 1st day is the current day
            Time dayTime = new Time(); // time initialized to 1/1/1970
            dayTime.setToNow(); // sets given Time object to current time
            //returns Julian day
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);
            // Now Time in UTC standard time
            // dayTime = new Time();
            String[] resultStr = new String[numDays];
            SharedPreferences sharedPrefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(getString(R.string.pref_units_key),
                    getString(R.string.pref_units_metric));
            for (int i = 0; i < weatherArray.length(); i++) {
                // for now using format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;
                // Get JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);
                // date/time is returned as a lon ie. 1400356800 for this Saturday
                long dateTime;
                // convert to UTC time
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);
                //description is in a child array called "weather", 1 element long
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);
                //Temperatures are in a child object called "temp"
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);
                highAndLow = formatHighLow(high, low, unitType);
                resultStr[i] = day + "-" + description + "-" + highAndLow;
            }

            return resultStr;
        }

        @Override
        protected String[] doInBackground(String... params) {
            //Verify zip
            if (params.length == 0) {
                return null;
            }
            //Add network call code
            //initialize outside of try catch to be closed in finally
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            //Will contain raw JSON response as a String
            String forecastJsonStr = null;
            //Query param values
            String format = "json";
            String units = "metric";
            int numDays = 7;
            try {
                //Construct URL OWM(weather site) query
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APPID_PARAM = "APPID";
                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_KEY)
                        .build();

                URL url = new URL(builtUri.toString());
                //Log.v(LOG_TAG, "Built URI "+builtUri.toString());
                //Create request to OWM and open connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();
                //read InputStream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }
                if (buffer.length() == 0) {
                    return null;
                }
                forecastJsonStr = buffer.toString();
                // Log.v(LOG_TAG, "ForecastJSON String: " + forecastJsonStr);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error", e);
                //e.printStackTrace();
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            // This will happen only if there was an error getting or parsing the forecast.
            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result != null) {
                mForecastAdapter.clear();
                mForecastAdapter.addAll(result);
                /**
                 * for older than Honeycomb SDK11
                 */
               /* for(String dayForecastStr : result) {
                    mForecastAdapter.add(dayForecastStr);
                }*/
            }
        }
    }
}