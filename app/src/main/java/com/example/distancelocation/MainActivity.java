package com.example.distancelocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineDasharray;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineTranslate;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener {
    private PermissionsManager permissionsManager;
    private MapboxMap mapboxMap;
    private MapView mapBoxMapView;
    private LocationComponent locationComponent;
    // locatin engine declaration
    private Location mLastKnownLocation;
    private LocationEngineCallback<LocationEngineResult> callback;
    private LocationEngine locationEngine;
    long DEFAULT_INTERVAL_IN_MILLISECONDS = 3000L;
    long DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5;
    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    private CarmenFeature home;
    private CarmenFeature work;
    private String geojsonSourceLayerId = "geojsonSourceLayerId";
    private String symbolIconId = "symbolIconId";
    private Point ORIGIN;
    private Point DESTINATION;
    List<DirectionsRoute> listOfRoutes;
    List<Feature> directionsRouteFeatureList;
    private FeatureCollection dashedLineDirectionsFeatureCollection;
    String validPostalCode = "";
    ArrayList<Double> minDistance = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);
        mapBoxMapView = findViewById(R.id.mapView);
        mapBoxMapView.onCreate(savedInstanceState);
        mapBoxMapView.getMapAsync(this);
    }


    @SuppressLint("MissingPermission")
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            statusCheck();
            LocationComponentOptions customLocationComponentOptions = LocationComponentOptions.builder(this)
                    .elevation(5)
                    .accuracyAlpha(.12f)
                    .accuracyColor(Color.GREEN)
                    .foregroundDrawable(R.drawable.map_marker_dark)
                    .build();
            // Get an instance of the component
            locationComponent = mapboxMap.getLocationComponent();
            LocationComponentActivationOptions locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this, loadedMapStyle)
                            .locationComponentOptions(customLocationComponentOptions)
                            .build();
            // Activate with options
            locationComponent.activateLocationComponent(locationComponentActivationOptions);
            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);
            //TODO below code commented out due to null last location -- weird behavior from mapbox
//            Location lastKnownLocation = locationComponent.getLastKnownLocation();
//            AppUtils.customToast("Last Location: " + lastKnownLocation, this);
            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);
            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Activity.RESULT_OK) {
            if (requestCode == 101) {
            }
        }
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapBoxMapView.onLowMemory();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style);
                }
            });
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            this.finish();
        }
    }

    private void locationUpdate() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this);
        LocationEngineRequest request = createLocationRequest();
        callback = new LocationEngineCallback<LocationEngineResult>() {
            @Override
            public void onSuccess(LocationEngineResult result) {
                Location lastLocation = result.getLastLocation();
                //optimization on app level
                if (mLastKnownLocation != null && mLastKnownLocation.getLongitude() == lastLocation.getLongitude() &&
                        mLastKnownLocation.getLatitude() == lastLocation.getLatitude())
                    if (mLastKnownLocation != null) {
                        GlobalClass.lat = mLastKnownLocation.getLatitude();
                        GlobalClass.lng = mLastKnownLocation.getLongitude();
                    }
            }

            @Override
            public void onFailure(@NonNull Exception exception) {

            }
        };
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationEngine.requestLocationUpdates(request, callback, getMainLooper());
    }

    @NotNull
    private LocationEngineRequest createLocationRequest() {
        return new LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_NO_POWER)
                .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME)
                .build();
    }


    @Override
    public void onStart() {
        super.onStart();
        mapBoxMapView.onStart();
        locationUpdate();
    }

    public void onPause() {
        super.onPause();
        mapBoxMapView.onPause();

    }

    public void onDestroy() {
        super.onDestroy();
        mapBoxMapView.onDestroy();

    }

    public void onStop() {
        super.onStop();
        mapBoxMapView.onStop();

    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
        MainActivity.this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                if (style.isFullyLoaded()) {
                    enableLocationComponent(style);
                    initSearchFab();
                    addUserLocations();
                    style.addImage(symbolIconId, BitmapFactory.decodeResource(
                            getApplicationContext().getResources(), R.drawable.map_marker_dark));
                    setUpSource(style);
                    setupLayer(style);
//                    initDottedLineSourceAndLayer(style);
                }
            }
        });
    }

    private void initSearchFab() {
        findViewById(R.id.destination_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = (Intent) new PlaceAutocomplete.IntentBuilder()
                        .accessToken(Mapbox.getAccessToken() != null ? Mapbox.getAccessToken() : getString(R.string.mapbox_access_token))
                        .placeOptions(PlaceOptions.builder()
                                .backgroundColor(Color.parseColor("#EEEEEE"))
                                .limit(10)
                                .proximity(ORIGIN)
                                .country("pk")
                                .build(PlaceOptions.PARCELABLE_WRITE_RETURN_VALUE))
                        .build(MainActivity.this);
                startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE);
            }
        });

    }


    private void addUserLocations() {
        home = CarmenFeature.builder().text("Mapbox SF Office")
                .geometry(Point.fromLngLat(-122.3964485, 37.7912561))
                .placeName("50 Beale St, San Francisco, CA")
                .id("mapbox-sf")
                .properties(new JsonObject())
                .build();
        work = CarmenFeature.builder().text("Mapbox DC Office")
                .placeName("740 15th Street NW, Washington DC")
                .geometry(Point.fromLngLat(-77.0338348, 38.899750))
                .id("mapbox-dc")
                .properties(new JsonObject())
                .build();
    }

    private void setUpSource(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addSource(new GeoJsonSource(geojsonSourceLayerId));
    }

    private void setupLayer(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addLayer(new SymbolLayer("SYMBOL_LAYER_ID", geojsonSourceLayerId).withProperties(
                iconImage(symbolIconId),
                iconOffset(new Float[]{0f, -8f})
        ));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_AUTOCOMPLETE) {
            CarmenFeature selectedCarmenFeature = PlaceAutocomplete.getPlace(data);
            if (mapboxMap != null) {
                Style style = mapboxMap.getStyle();
                if (style != null) {
                    GeoJsonSource source = style.getSourceAs(geojsonSourceLayerId);
                    if (source != null) {
                        source.setGeoJson(FeatureCollection.fromFeatures(
                                new Feature[]{Feature.fromJson(selectedCarmenFeature.toJson())}));
                        mapboxMap.animateCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(
                                new CameraPosition.Builder()
                                        .target(new com.mapbox.mapboxsdk.geometry.LatLng(((Point) selectedCarmenFeature.geometry()).latitude(),
                                                ((Point) selectedCarmenFeature.geometry()).longitude()))
                                        .zoom(12)
                                        .build()), 4000);
                        DESTINATION = Point.fromLngLat(((Point) selectedCarmenFeature.geometry()).longitude(), ((Point) selectedCarmenFeature.geometry()).latitude());
                        double latitude = ((Point) selectedCarmenFeature.geometry()).latitude();
                        double longitude = ((Point) selectedCarmenFeature.geometry()).longitude();
                        JSONArray array = new JSONArray();
                        try {
                            addToJSONArray(array, latitude, longitude);
                        } catch (Exception e) {
                            e.getMessage();
                        }
                        if (DESTINATION == null) {
                            return;
                        }
                        if (ORIGIN != null && DESTINATION != null) {
                            if (listOfRoutes != null) {

                            }
                        }
                    }
                }
            }
        }
    }

    public void addToJSONArray(JSONArray array, double lat, double lng) throws Exception {
        minDistance = new ArrayList<>();
        JSONObject data = new JSONObject();
        data.put("latitude", lat);
        data.put("longitude", lng);
        double distance = distance(lat, lng);
        minDistance.add(distance);
        data.put("distance", distance);
        String address = getAddress(lat, lng);
        data.put("location_name", address);
        array.put(data);
        saveJourney(this, array);
    }

    public void saveJourney(Context context, JSONArray saveData) {
        SharedPreferences storeAllRoutes = context.getSharedPreferences("STORED_ROUTES", context.MODE_PRIVATE);
        SharedPreferences numberOfRoutes = context.getSharedPreferences("NUM_ROUTES", context.MODE_PRIVATE);
        SharedPreferences.Editor editor = storeAllRoutes.edit();
        SharedPreferences.Editor numEdit = numberOfRoutes.edit();
        int i = numberOfRoutes.getInt("numOfRoutes", 0);
        editor.putString("saveData" + i, saveData.toString());
        i++;
        numEdit.putInt("numOfRoutes", i);
        editor.commit();
        numEdit.commit();
        Toast.makeText(getApplicationContext(), "Journey Saved",
                Toast.LENGTH_SHORT).show();

    }

    public JSONArray getData(Context context, int i) throws Exception {
        SharedPreferences storeAllRoutes = context.getSharedPreferences("STORED_ROUTES", context.MODE_PRIVATE);
        SharedPreferences numberOfRoutes = context.getSharedPreferences("NUM_ROUTES", context.MODE_PRIVATE);


        if (numberOfRoutes.contains("numOfRoutes") && i > 0) {


            return new JSONArray(storeAllRoutes.getString("saveData" + i, ""));


        } else {

            return new JSONArray();
        }

    }


    public void parseJSONArray(JSONArray array) {
        try {
            for (int i = 0; i < array.length(); ++i) {
                JSONObject item1 = array.getJSONObject(i);
                Collections.sort(minDistance);

                Iterator<String> iter = item1.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    try {
                        Object value = item1.get("location_name");
                        Toast.makeText(getApplicationContext(), ""+value,
                                Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        // Something went wrong!
                    }
                }
                if (minDistance.contains(item1.getDouble("distance"))) {
//                    getAddress(item1.getDouble("latitude"), item1.getDouble("longitude"));
                }
            }
        } catch (Exception e) {
            e.getMessage();
        }
    }


    public void retreiveDatabase(View view) {
        SharedPreferences numberOfRoutes = this.getSharedPreferences("NUM_ROUTES", this.MODE_PRIVATE);

        try {
            if (numberOfRoutes.contains("numOfRoutes") && numberOfRoutes.getInt("numOfRoutes", 0) > 0) {
                for (int i = 1; i < numberOfRoutes.getInt("numOfRoutes", 0) + 1; i++) {
                    JSONArray allRoutes = new JSONArray();
                    allRoutes = getData(this, i);
                    parseJSONArray(allRoutes);
                }
            } else {
                Toast.makeText(getApplicationContext(), "No data exist",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
        }
    }

    public String getAddress(double lat, double lng) {
        String addressLine = "";
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);

            if (addresses.size() > 0) {
                Address obj = addresses.get(0);
                addressLine = obj.getAddressLine(0);

//                if (obj.getPostalCode().equals("") || obj.getAddressLine(0)!=null) {
//                    Toast.makeText(this, "empty ha postal code", Toast.LENGTH_SHORT).show();
//
//                } else {
//                    validPostalCode = obj.getAddressLine(0) + obj.getPostalCode();
//                    Toast.makeText(this, validPostalCode, Toast.LENGTH_SHORT).show();
//                }

            }
            return addressLine;
        } catch (IOException e) {
            return null;
        }
    }

    private double distance(double lat2, double lon2) {
        ArrayList<Double> distance = new ArrayList<>();
        double theta = GlobalClass.lng - lon2;
        double dist = Math.sin(deg2rad(GlobalClass.lat))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(GlobalClass.lat))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        Math.round(dist);
        distance.add(dist);
        return (Math.round(dist));
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    public void deleteTheData(View view) {
        SharedPreferences preferences = getSharedPreferences("NUM_ROUTES", 0);
        preferences.edit().remove("numOfRoutes").commit();
        minDistance.clear();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapBoxMapView.onSaveInstanceState(outState);
    }

    public void statusCheck() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.your_gps))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));

                    }
                })
                .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }
}
