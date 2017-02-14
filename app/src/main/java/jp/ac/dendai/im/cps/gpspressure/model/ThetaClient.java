package jp.ac.dendai.im.cps.gpspressure.model;

import android.util.Log;

import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;

import jp.ac.dendai.im.cps.gpspressure.entity.ThetaEntity;
import jp.ac.dendai.im.cps.gpspressure.network.ApiClient;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;

public class ThetaClient {

    private static final String TAG = "ThetaClient";

    private Subscription subscription;

    private String sessionId;

    private Gson gson = new Gson();

    private ApiClient apiClient = new ApiClient();

    private OnCaptureStatusListener listener;

    public ThetaClient(OnCaptureStatusListener listener) {
        this.listener = listener;
        init();
    }

    private void stopThetaCapture(final SensorStoreClient sensorStoreClient) {
        listener.onCaptureStop();
        apiClient.stopCapture()
            .subscribeOn(Schedulers.io())
            .subscribe(s -> {
                ThetaEntity thetaEntity = gson.fromJson(s, ThetaEntity.class);
                if (thetaEntity.results == null) {
                    return;
                }

                sessionId = thetaEntity.results.sessionId;
                Log.d(TAG, "onCreate: stop capture: " + thetaEntity.results.fileUrls.get(0));
                sensorStoreClient.finishCsv(thetaEntity.results.fileUrls.get(0));
            });
    }

    public void startThetaCapture(SensorStoreClient sensorStoreClient) {
        apiClient.startCapture()
            .subscribeOn(Schedulers.io())
            .subscribe(s -> {
                Log.d(TAG, "startThetaCapture: " + s);
                startStopCaptureTimer(sensorStoreClient);
            });
    }

    private void startStopCaptureTimer(SensorStoreClient sensorStoreClient) {
        listener.onCaptureStart();
        Observable.timer(30, TimeUnit.SECONDS)
            .observeOn(Schedulers.io())
            .subscribe(aLong -> {
                stopThetaCapture(sensorStoreClient);
            });
    }

    private void init() {
        apiClient.startSession()
            .subscribeOn(Schedulers.io())
            .subscribe(s -> {
                ThetaEntity thetaEntity = gson.fromJson(s, ThetaEntity.class);
                if (thetaEntity.results == null) {
                    return;
                }

                sessionId = thetaEntity.results.sessionId;
                Log.d(TAG, "onCreate: start session: " + thetaEntity);

                setThetaApiClientVersion();
            });
    }

    private void setThetaApiClientVersion() {
        apiClient.setClientVersion(sessionId)
            .subscribeOn(Schedulers.io())
            .subscribe(s1 -> {
                Log.d(TAG, "onCreate: setClientVersion: " + s1);
            });
    }

    public interface OnCaptureStatusListener {
        void onCaptureStart();
        void onCaptureStop();
    }
}
