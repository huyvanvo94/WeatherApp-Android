package com.huyvo.cmpe277.sjsu.weatherapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.huyvo.cmpe277.sjsu.weatherapp.FetchWeatherIntentService;
import com.huyvo.cmpe277.sjsu.weatherapp.MainActivity;
import com.huyvo.cmpe277.sjsu.weatherapp.R;
import com.huyvo.cmpe277.sjsu.weatherapp.WeatherApp;
import com.huyvo.cmpe277.sjsu.weatherapp.WeatherForecastContainer;
import com.huyvo.cmpe277.sjsu.weatherapp.model.CityModel;
import com.huyvo.cmpe277.sjsu.weatherapp.model.WeatherModel;
import com.huyvo.cmpe277.sjsu.weatherapp.util.DateHelper;
import com.huyvo.cmpe277.sjsu.weatherapp.util.JsonHelper;
import com.huyvo.cmpe277.sjsu.weatherapp.util.JsonParser;
import com.huyvo.cmpe277.sjsu.weatherapp.util.Logger;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.huyvo.cmpe277.sjsu.weatherapp.activities.CityListViewActivity.Messages.ADD_CITY;
import static com.huyvo.cmpe277.sjsu.weatherapp.activities.CityListViewActivity.Messages.REMOVE_CITY;
import static com.huyvo.cmpe277.sjsu.weatherapp.activities.CityListViewActivity.Messages.UPDATE_CITY_TIME;
import static com.huyvo.cmpe277.sjsu.weatherapp.activities.CityListViewActivity.Messages.UPDATE_WEATHER;

public class CityListViewActivity extends BaseActivityWithFragment implements View.OnClickListener, AdapterView.OnItemClickListener{
    public final static String TAG = CityListViewActivity.class.getSimpleName();
    private CityViewAdapter mAdapter;
    private final List<CityModel> mCityModels = new ArrayList<>();
    int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city_list_view);
        Logger.d(TAG, "onCreate");

        initUI();
        load(WeatherApp.getLatLngList());

        ScheduledExecutorService executorService= Executors.newScheduledThreadPool(2);
        executorService.scheduleAtFixedRate(new FetchLocalTime(), 0, 1, TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(new FetchWeather(), 0, 3, TimeUnit.HOURS);
    }

    private void initUI(){
        FloatingActionButton mFabAddCity = (FloatingActionButton) findViewById(R.id.fab_add_city);

        mAdapter = new CityViewAdapter(getApplicationContext(), R.layout.item_city_view, mCityModels);
        ListView listView = (ListView) findViewById(R.id.list_city_view);
        listView.setAdapter(mAdapter);
        mFabAddCity.setOnClickListener(this);
        listView.setOnItemClickListener(this);
    }

    private void load(List<String> locations){
        Logger.d(TAG, String.valueOf(locations.size()));
        if(!locations.isEmpty()) {
            new Thread(new CitiesLoadRunnable(locations)).start();
        }
    }

    private void onCitySearch(){
        try{
            AutocompleteFilter typeFilter = new AutocompleteFilter.Builder()
                    .setTypeFilter(AutocompleteFilter.TYPE_FILTER_CITIES)
                    .build();
            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                    .setFilter(typeFilter)
                    .build(this);

            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        }catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException ignored){

        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Logger.d(TAG, "onActivityResult");
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);

                Logger.d(TAG, place.toString());
                final double lat = place.getLatLng().latitude;
                final double lng = place.getLatLng().longitude;

                String location = "lat="+lat+"&lon="+lng;
                if(!WeatherApp.getLatLngList().contains(location)){
                    WeatherApp.getLatLngList().add(location);

                    Intent intent = new Intent(this, FetchWeatherIntentService.class);
                    intent.putExtra(FetchWeatherIntentService.FETCH_WEATHER, location);
                    startService(intent);

                    // update current view inside of waiting
                    // for forecast fetching which will take relatively long
                    new Thread(new CityLoadRunnable(location)).start();
                }
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);

                Logger.d(TAG, status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {}
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.fab_add_city:
                onCitySearch();
                break;

            default:
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long l) {
        Logger.d(TAG, "onItemClick " + position);
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("position", position);
        startActivity(i);
    }

    private void append(CityModel model){
        mAdapter.add(model);
     //   Logger.d(TAG, mCityModels.size()+"");
    }

    private void update(CityModel model){

    }

    private void remove(String location){
        WeatherForecastContainer weatherForecastContainer = WeatherForecastContainer.getInstance();
        weatherForecastContainer.remove(location);
    }


    class CityRemoveRunnable implements Runnable{

        private String mLocation;
        public CityRemoveRunnable(String location){
            mLocation = location;
        }
        @Override
        public void run() {

            WeatherForecastContainer weatherForecastContainer = WeatherForecastContainer.getInstance();
            weatherForecastContainer.remove(mLocation);


        }
    }

    class CitiesLoadRunnable implements Runnable{

        private List<String> mLocations;

        public CitiesLoadRunnable(List<String> locations){
            mLocations = locations;
        }
        @Override
        public void run() {
            for(String location: mLocations){
                WeatherForecastContainer forecastContainer = WeatherForecastContainer.getInstance();
                WeatherModel weatherModel = forecastContainer.getWeatherModels(location).get(0);

                CityModel cityModel = new CityModel();
                cityModel.timeZoneId = weatherModel.timeZoneId;
                cityModel.cityName = weatherModel.city;
                cityModel.latlng = weatherModel.lat+","+weatherModel.lon;
                cityModel.currentTemp = String.valueOf(weatherModel.temp_day);
                cityModel.location = location;

                Message msg = mHandler.obtainMessage();
                msg.obj = cityModel;
                msg.what = ADD_CITY;
                mHandler.sendMessage(msg);
            }
        }
    }

    class FetchWeather implements Runnable{


        @Override
        public void run(){
            synchronized (mCityModels){
                for (int i = 0; i < mCityModels.size(); i++){
                    fetchWeather(mCityModels.get(i));
                }
            }

        }

        private void fetchWeather(final CityModel cityModel){

            String url = "http://api.openweathermap.org/data/2.5/weather?"+cityModel.location+"&units=imperial&appid=b54f500d4a53fdfc96813a4ba9210417";
            ((WeatherApp) WeatherApp.getInstance()).addToRequestQueue(new JsonObjectRequest(url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                WeatherModel weatherModel = JsonParser.parseWeather(response);

                                cityModel.currentTemp = String.valueOf(weatherModel.temp);

                                Message msg = mHandler.obtainMessage();
                                msg.what = UPDATE_WEATHER;
                                mHandler.sendMessage(msg);

                            }catch(Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    VolleyLog.e("Error: ", error.getMessage());
                }
            }));
        }
    }


    class FetchLocalTime implements Runnable{


        @Override
        public void run() {
            fetch();
        }

        private void fetch(){
            Message msg = mHandler.obtainMessage();
            msg.what = UPDATE_CITY_TIME;
            mHandler.sendMessage(msg);
        }
    }

    /**
     * load current local time of that city, city name, and current temperature.
     */
    private class CityLoadRunnable implements Runnable{

        private String mLocation;
        public CityLoadRunnable(String location){
            mLocation = location;
        }
        @Override
        public void run() {
            Logger.d(TAG, "run");
            load(mLocation);
        }

        private void load(String location){
            fetchWeather(location);
        }

        private void fetchLocalTime(String location, final CityModel model){

            String url = "https://maps.googleapis.com/maps/api/timezone/json?location="+location+"&timestamp="+DateHelper.getTimeStamp()+"&key="+getString(R.string.google_timezone_key);
            ((WeatherApp) WeatherApp.getInstance()).addToRequestQueue(new JsonObjectRequest(url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                Logger.d(TAG, response.toString());

                                model.timeZoneId = JsonHelper.getString(response, "timeZoneId");
                                post(model);


                            }catch(Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    VolleyLog.e("Error: ", error.getMessage());
                }
            }));
        }

        private void fetchWeather(final String location){
            String url = "http://api.openweathermap.org/data/2.5/weather?"+location+"&units=imperial&appid=b54f500d4a53fdfc96813a4ba9210417";
            ((WeatherApp) WeatherApp.getInstance()).addToRequestQueue(new JsonObjectRequest(url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                WeatherModel weatherModel = JsonParser.parseWeather(response);


                                CityModel model = new CityModel();
                                model.cityName = weatherModel.city;
                                model.latlng = weatherModel.lat+","+weatherModel.lon;
                                model.currentTemp = String.valueOf(weatherModel.temp);
                                model.location = location;

                                fetchLocalTime(weatherModel.lat+","+weatherModel.lon, model);


                            }catch(Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    VolleyLog.e("Error: ", error.getMessage());
                }
            }));
        }

        private void post(CityModel cityModel){
            Message msg = mHandler.obtainMessage();
            msg.what = ADD_CITY;
            msg.obj = cityModel;

            mHandler.sendMessage(msg);
        }
    }

    public final class Messages{
        public final static int ADD_CITY    = 1;
        public final static int REMOVE_CITY = 2;
        public final static int UPDATE_CITY_TIME = 3;
        public final static int UPDATE_WEATHER = 4;
    }
    Handler mHandler = new Handler(Looper.getMainLooper()){
        public void handleMessage(android.os.Message msg) {

            switch (msg.what){
                case ADD_CITY:
                    CityModel model = (CityModel) msg.obj;
                    mCityModels.add(model);
                    mAdapter.notifyDataSetChanged();
                    break;

                case REMOVE_CITY:
                    int index = (int) msg.obj;
                    mCityModels.remove(index);
                    mAdapter.notifyDataSetChanged();

                    break;

                case UPDATE_CITY_TIME:
                    mAdapter.notifyDataSetChanged();
                    break;
                case UPDATE_WEATHER:
                    Logger.d(TAG, "Update Weather");
                    mAdapter.notifyDataSetChanged();
                    break;

                default:
                    break;
            }

        }
    };


    class CityInfoPackage{
        String location;
        String localTime;
        int index;
    }
}
