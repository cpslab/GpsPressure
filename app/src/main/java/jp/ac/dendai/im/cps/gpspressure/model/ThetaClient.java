package jp.ac.dendai.im.cps.gpspressure.model;

import android.util.Log;

import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;

import jp.ac.dendai.im.cps.gpspressure.entity.ThetaEntity;
import jp.ac.dendai.im.cps.gpspressure.network.ApiClient;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class ThetaClient {

    private static final String TAG = "ThetaClient";

    private CompositeSubscription compositeSubscription = new CompositeSubscription();

    private String sessionId;

    private Gson gson = new Gson();

    private ApiClient apiClient = new ApiClient();

    public ThetaClient() {
        init();
    }

    public void unsubscribe() {
        compositeSubscription.unsubscribe();
    }

    private void stopThetaCapture(final SensorStoreClient sensorStoreClient) {
        Subscription subscription = apiClient.stopCapture()
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
        compositeSubscription.add(subscription);
    }

    public void startThetaCapture(SensorStoreClient sensorStoreClient) {
        Subscription subscription = apiClient.startCapture()
            .subscribeOn(Schedulers.io())
            .subscribe(s -> {
                Log.d(TAG, "startThetaCapture: " + s);
                startStopCaptureTimer(sensorStoreClient);
            });
        compositeSubscription.add(subscription);
    }

    private void startStopCaptureTimer(SensorStoreClient sensorStoreClient) {
        Subscription subscription = Observable.timer(30, TimeUnit.SECONDS)
            .observeOn(Schedulers.io())
            .subscribe(aLong -> {
                stopThetaCapture(sensorStoreClient);
            });
        compositeSubscription.add(subscription);
    }

    private void init() {
        Subscription subscription = apiClient.startSession()
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
        compositeSubscription.add(subscription);
    }

    private void setThetaApiClientVersion() {
        Subscription subscription = apiClient.setClientVersion(sessionId)
            .subscribeOn(Schedulers.io())
            .subscribe(s1 -> {
                Log.d(TAG, "onCreate: setClientVersion: " + s1);
            });
        compositeSubscription.add(subscription);
    }
}
