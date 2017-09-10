package jp.ac.dendai.im.cps.gpspressure;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.concurrent.TimeUnit;

import jp.ac.dendai.im.cps.gpspressure.databinding.ActivityMainBinding;
import jp.ac.dendai.im.cps.gpspressure.model.KalmanLatLng;
import jp.ac.dendai.im.cps.gpspressure.model.SensorStoreClient;
import jp.ac.dendai.im.cps.gpspressure.model.ThetaClient;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends AppCompatActivity
        implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, com.google.android.gms.location.LocationListener,
        GoogleApiClient.OnConnectionFailedListener, ResultCallback<LocationSettingsResult> {

    /**
     * Constant used in the location settings dialog.
     */
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int MATRIX_SIZE = 16;

    private SensorManager sensorManager;

    private boolean textFlag = true;

    private boolean isLocation = false;

    private static final String[] LOCATION_PERMS = {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private static final int INITIAL_REQUEST = 1337;

    private static final int LOCATION_REQUEST = INITIAL_REQUEST + 3;

    private ActivityMainBinding binding;

    private Location location;

    private String pressure;

    private float[] accelerometerValues = new float[3];

    private float[] magneticValues = new float[3];

    private float[] attitude = new float[3];

    private float[] orientation = new float[3];

    private Subscription subscription;

    private SensorStoreClient sensorStoreClient;

    private ThetaClient thetaClient;

    private float currentSpeed;

    private long runStartTimeInMillis = SystemClock.elapsedRealtimeNanos() / 1000000;

    private KalmanLatLng kalmanFilter = new KalmanLatLng(3F);

    private GpsStatus.Listener mGpsStatusListener;

    private GoogleApiClient googleApiClient;

    private LocationRequest locationRequest;

    private LocationSettingsRequest locationSettingsRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorStoreClient = new SensorStoreClient();

//        setupTheta();
        binding.captureStart.setOnClickListener(this::onCaptureStartClick);
        setupLocationPlayService();
    }

    private synchronized void setupLocationPlayService() {
        buildGooglePlayService();
        locationRequest = createLocationRequest();
        buildLocationSettingRequest(locationRequest);
    }

    private synchronized void buildGooglePlayService() {
        // Create an instance of GoogleAPIClient.
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    private LocationRequest createLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(100);
        locationRequest.setFastestInterval(100);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }

    private void buildLocationSettingRequest(LocationRequest locationRequest) {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback(this);
    }

    private void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            } else {
            }
            return;
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, this
        ).setResultCallback(status -> {
            if (status.isSuccess()) {
                Log.d(TAG, "App Indexing API: Recorded recipe view end successfully."+ status.toString());
            } else {
                Log.d(TAG, "App Indexing API: There was an error recording the recipe view."
                        + status.toString());
            }
        });

    }

    private void setupTheta() {
        if (isTheta()) {
            Log.d(TAG, "onCreate: try connect theta");
            thetaClient = new ThetaClient(new ThetaClient.OnCaptureStatusListener() {
                @Override
                public void onCaptureStart() {
                    Observable
                            .create(subscriber -> binding.providerText.setText("撮影 ＆ センサー 取得中"))
                            .subscribeOn(AndroidSchedulers.mainThread())
                            .subscribe();
                    startSensorLoop();
                }

                @Override
                public void onCaptureStop() {
                    Observable
                            .create(subscriber -> binding.providerText.setText("準備中"))
                            .subscribeOn(AndroidSchedulers.mainThread())
                            .subscribe();
                    if (subscription != null) {
                        subscription.unsubscribe();
                        subscription = null;
                    }
                }
            });
        }
//        binding.captureStart.setOnClickListener(this::onCaptureStartClick);
    }

    private boolean isTheta() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo w_info = wifiManager.getConnectionInfo();
        String ssid = w_info.getSSID();
        Log.d(TAG, "isTheta: " + ssid);
        return ssid.contains("THETA");
    }

    private void onCaptureStartClick(View v) {
        if (location == null) {
            Toast.makeText(this, "GPSが取得できていません", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.captureStart.setText("stop");
        binding.captureStart.setOnClickListener(this::stopCapture);
        sensorStoreClient.createCsv();
        startSensorLoop();
//        thetaClient.startThetaCapture(sensorStoreClient);
    }

    private void stopCapture(View v) {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }

        binding.captureStart.setText("start");
        binding.captureStart.setOnClickListener(this::onCaptureStartClick);
        binding.storeText.setText("記録していません");
        sensorStoreClient.finishCsv("");
    }

    public void onClick(View v) {
        textFlag = !textFlag;
    }

    private void startSensorLoop() {
        binding.storeText.setText("記録中");
        subscription = Observable
            .interval(100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(aLong -> {
                Log.d(TAG, "startSensorLoop: long: " + aLong);
                binding.timeText.setText(Utils.parseDate(System.currentTimeMillis()));
                sensorStoreClient.saveCsv(location, pressure, attitude, orientation);
            });
    }

    private void registSensorManager() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
        // センサーリスナー登録解除
        sensorManager.unregisterListener(this);
        stopCapture(null);
        super.onStop();
    }

    @Override
    protected void onResume() {
        registSensorManager();
        if (googleApiClient.isConnected()) {
            startLocationUpdates();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accelerometerValues = event.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magneticValues = event.values.clone();
                break;
            case Sensor.TYPE_PRESSURE:
                pressure = String.valueOf(event.values[0]);
                break;
            case Sensor.TYPE_ORIENTATION:
                orientation = event.values.clone();
                break;
        }

        String attitudeText = calcAttitude();
        if (textFlag) {
            // 例えば小数点以下3桁表示
            String value = String.format("%.3f hPa (millibar)", event.values[0]);
            binding.pressText.setText("press: " + value);
            binding.thetaText.setText(attitudeText);
        }
    }

    private String calcAttitude() {
        if(accelerometerValues == null || magneticValues == null) {
            return "";
        }

        attitude = setAttitude(accelerometerValues, magneticValues);

        attitude[0] = Utils.rad2deg(attitude[0]);
        attitude[1] = Utils.rad2deg(attitude[1]);
        attitude[2] = Utils.rad2deg(attitude[2]);

        //角度の範囲を0~360度へ調整
        if (attitude[0] < 0) {
            attitude[0] += 360;
        }

        return "---------- attitude --------\n" +
                String.format("方位角\n\t%f\n", attitude[0]) +
                String.format("前後の傾斜\n\t%f\n", attitude[1]) +
                String.format("左右の傾斜\n\t%f\n", attitude[2])
                + "\n---------- orientation ----------\n" +
                String.format("方位角\n\t%f\n", orientation[0]) +
                String.format("前後の傾斜\n\t%f\n", orientation[1]) +
                String.format("左右の傾斜\n\t%f\n", orientation[2]);
    }

    private float[] setAttitude(float[] aValues, float[] mValues) {
        float[] rotationMatrix = new float[MATRIX_SIZE];
        float[] inclinationMatrix = new float[MATRIX_SIZE];
        float[] remapedMatrix = new float[MATRIX_SIZE];
        float[] result = new float[3];

        // 加速度センサーと地磁気センサーから回転行列を取得
        SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, aValues, mValues);
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapedMatrix);
        SensorManager.getOrientation(remapedMatrix, result);
        // 加速度センサーと地磁気センサーから回転行列を取得

        return result;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (filterAndAddLocation(location) == null) {
            return;
        }

        this.location = location;
        if (textFlag) {
            binding.latText.setText("lat: " + location.getLatitude());
            binding.lngText.setText("lng: " + location.getLongitude());
            binding.altText.setText("alt: " + location.getAltitude());
            binding.accText.setText("acc: " + location.getAccuracy());
        }
    }

    private Location filterAndAddLocation(Location location) {
        long age = getLocationAge(location);

        if (age > 10 * 1000) { //more than 10 seconds
            android.util.Log.d(TAG, "Location is old");
            return null;
        }

        if (location.getAccuracy() <= 0) {
            android.util.Log.d(TAG, "Latitidue and longitude values are invalid.");
            return null;
        }

        float horizontalAccuracy = location.getAccuracy();
        if (horizontalAccuracy > 100) {
            android.util.Log.d(TAG, "Accuracy is too low.");
            return null;
        }

        Location filterdLocation = filterKalman(location);
        if (filterdLocation == null) {
            return null;
        }

        Log.d(TAG, "Location quality is good enough.");
        currentSpeed = location.getSpeed();

        return location;
    }

    private Location filterKalman(Location location) {
        float Qvalue;

        long elapsedTimeInMillis = (location.getElapsedRealtimeNanos() / 1000000) - runStartTimeInMillis;

        if (currentSpeed == 0.0f) {
            Qvalue = 3.0f;
        } else {
            Qvalue = currentSpeed;
        }

        kalmanFilter.process(location.getLatitude(), location.getLongitude(), location.getAccuracy(), elapsedTimeInMillis, Qvalue);

        Location predictedLocation = new Location("");
        predictedLocation.setLatitude(kalmanFilter.lat);
        predictedLocation.setLongitude(kalmanFilter.lng);
        float predictedDeltaInMeters = predictedLocation.distanceTo(location);

        if (predictedDeltaInMeters > 60) {
            android.util.Log.d(TAG, "Kalman Filter detects mal GPS, we should probably remove this from track");
            kalmanFilter.consecutiveRejectCount = 1 + kalmanFilter.consecutiveRejectCount;

            if (kalmanFilter.consecutiveRejectCount > 3) {
                kalmanFilter = new KalmanLatLng(3f);
            }

            return null;
        } else {
            kalmanFilter.consecutiveRejectCount = 0;
        }

        return predictedLocation;
    }

    private long getLocationAge(Location newLocation) {
        long currentTimeInMilli = (android.os.SystemClock.elapsedRealtimeNanos() / 1000000);
        return currentTimeInMilli - newLocation.getElapsedRealtimeNanos() / 1000000;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onResult(@NonNull LocationSettingsResult result) {
        final Status status = result.getStatus();
//        final LocationSettingsStates = result.getLocationSettingsStates();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                Log.i(TAG, "All location settings are satisfied.");
                startLocationUpdates();
                break;
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to" +
                        "upgrade location settings ");

                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result
                    // in onActivityResult().
                    status.startResolutionForResult(this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException e) {
                    Log.i(TAG, "PendingIntent unable to execute request.");
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog " +
                        "not created.");
                break;
        }
    }
}
