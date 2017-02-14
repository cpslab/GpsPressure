package jp.ac.dendai.im.cps.gpspressure;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

import jp.ac.dendai.im.cps.gpspressure.databinding.ActivityMainBinding;
import jp.ac.dendai.im.cps.gpspressure.model.SensorStoreClient;
import jp.ac.dendai.im.cps.gpspressure.model.ThetaClient;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int MATRIX_SIZE = 16;

    private SensorManager sensorManager;

    private LocationManager locationManager;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        boolean gpsFlg = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        Log.d("GPS Enabled", gpsFlg ? "OK" : "NG");

        sensorStoreClient = new SensorStoreClient();

        if (isTheta()) {
            Log.d(TAG, "onCreate: try connect theta");
            thetaClient = new ThetaClient(new ThetaClient.OnCaptureStatusListener() {
                @Override
                public void onCaptureStart() {
                    Observable.create(subscriber -> {
                        binding.providerText.setText("撮影 ＆ センサー 取得中");
                    }).subscribeOn(AndroidSchedulers.mainThread()).subscribe();
                    startSensorLoop();
                }

                @Override
                public void onCaptureStop() {
                    Observable.create(subscriber -> {
                        binding.providerText.setText("準備痛");
                    }).subscribeOn(AndroidSchedulers.mainThread()).subscribe();
                    subscription.unsubscribe();
                    subscription = null;
                }
            });
        }

        binding.captureStart.setOnClickListener(this::onCaptureStartClick);

        if (!gpsFlg) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(LOCATION_PERMS, LOCATION_REQUEST);
            }
        }
        requestGps();
    }

    private boolean isTheta() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
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

        sensorStoreClient.createCsv();
        thetaClient.startThetaCapture(sensorStoreClient);
    }

    public void onClick(View v) {
        textFlag = !textFlag;
    }

    private void startSensorLoop() {
        subscription = Observable
            .interval(1, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(aLong -> {
                Log.d(TAG, "startSensorLoop: long: " + aLong);
                binding.timeText.setText(Utils.parseDate(System.currentTimeMillis()));
                sensorStoreClient.saveCsv(location, pressure, attitude);
            });
    }

    private void registSensorManager() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void requestGps() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, this);
    }

    @Override
    protected void onResume() {
        registSensorManager();

        super.onResume();
    }

    private void removeGps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        locationManager.removeUpdates(this);
    }

    @Override
    protected void onStop() {
        // センサーリスナー登録解除
        sensorManager.unregisterListener(this);
        subscription.unsubscribe();
        subscription = null;
        removeGps();
        super.onStop();
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case LOCATION_REQUEST: {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                if (isLocation) {
                    locationManager.removeUpdates(this);
                    isLocation = false;
                } else {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, this);
                    isLocation = true;
                }
            }
        }
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

        float[] rotationMatrix = new float[MATRIX_SIZE];
        float[] inclinationMatrix = new float[MATRIX_SIZE];
        float[] remapedMatrix = new float[MATRIX_SIZE];

        // 加速度センサーと地磁気センサーから回転行列を取得
        SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticValues);
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapedMatrix);
        SensorManager.getOrientation(remapedMatrix, attitude);

        attitude[0] = Utils.rad2deg(attitude[0]);
        attitude[1] = Utils.rad2deg(attitude[1]);
        attitude[2] = Utils.rad2deg(attitude[2]);

        float angle = attitude[0];
        //角度の範囲を0~360度へ調整
        if (angle >=0) {
            attitude[0] = angle;
        } else if (angle < 0) {
            attitude[0] = 360 + angle;
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;

        if (textFlag) {
            binding.latText.setText("lat: " + location.getLatitude());
            binding.lngText.setText("lng: " + location.getLongitude());
            binding.altText.setText("alt: " + location.getAltitude());
            binding.accText.setText("acc: " + location.getAccuracy());
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
//        String message = "";
//        switch (status) {
//            case LocationProvider.OUT_OF_SERVICE:
//                // if the provider is out of service, and this is not expected to change in the near future.
//                message = provider +"が圏外になっていて取得できません。";
//                break;
//            case LocationProvider.TEMPORARILY_UNAVAILABLE:
//                // if the provider is temporarily unavailable but is expected to be available shortly.
//                message = "一時的に" + provider + "が利用できません。もしかしたらすぐに利用できるようになるかもです。";
//                break;
//            case LocationProvider.AVAILABLE:
//                // if the provider is currently available.
//                if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
//                    message = provider + "が利用可能になりました。";
//                } else {
//                    message = provider + "が無効です";
//                }
//                break;
//        }
//        Log.d(TAG, "onStatusChanged: " + message);
//        binding.providerText.setText(message);
    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
