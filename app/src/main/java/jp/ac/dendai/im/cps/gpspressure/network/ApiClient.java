package jp.ac.dendai.im.cps.gpspressure.network;

import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.Observable;

public class ApiClient {

    private static final String TAG = ApiClient.class.getSimpleName();

    private static final String PATH_OSC = "osc";

    private static final String PATH_COMMANDS = "commands";

    private static final String PATH_EXECUTE = "execute";

    private static final String PATH_STATE = "state";

    private static final String JSON_SET_CLIENT_VERSION_TOP
        = "{\"name\": \"camera.setOptions\", \"parameters\": {\"sessionId\": \"";

    private static final String JSON_SET_CLIENT_VERSION_END
        = "\", \"options\": {\"clientVersion\": 2}}}";

    private static final String JSON_START_SESSION =
        "{\"name\": \"camera.startSession\",\"parameters\": {}}";

    private static final String JSON_START_CAPTURE =
        "{\"name\": \"camera.startCapture\"}";

    private static String JSON_STOP_CAPTURE =
        "{\"name\": \"camera.stopCapture\"}";

    private static final String JSON_TAKE_PICTURE =
        "{\"name\": \"camera.takePicture\"}";

    private MediaType jsonMedia = MediaType.parse("application/json; charset=utf-8");

    private OkHttpClient client = new OkHttpClient();

    public Observable<String> uploadSensor(String csv) {
        Request request = new Request.Builder()
                .url(buildSensorUploadUrl())
                .post(RequestBody.create(jsonMedia, csv))
                .build();
        return enqueue(request);
    }

    public Observable<String> fetchProjectList() {
        Request request = new Request.Builder()
                .url(buildProjectListUrl())
                .get()
                .build();
        return enqueue(request);
    }

    public Observable<String> getState() {
        Request request = new Request.Builder()
            .url(buildStateUrl())
            .post(RequestBody.create(jsonMedia, JSON_STOP_CAPTURE))
            .build();
        return enqueue(request);
    }

    public Observable<String> takePicture() {
        Request request = new Request.Builder()
            .url(buildCommandExecUrl())
            .post(RequestBody.create(jsonMedia, JSON_TAKE_PICTURE))
            .build();
        return enqueue(request);
    }

    public Observable<String> setClientVersion(String sessionId) {
        String json = JSON_SET_CLIENT_VERSION_TOP + sessionId + JSON_SET_CLIENT_VERSION_END;
        Request request = new Request.Builder()
            .url(buildCommandExecUrl())
            .post(RequestBody.create(jsonMedia, json))
            .build();
        return enqueue(request);
    }

    public Observable<String> stopCapture() {
        Request request = new Request.Builder()
            .url(buildCommandExecUrl())
            .post(RequestBody.create(jsonMedia, JSON_STOP_CAPTURE))
            .build();
        return enqueue(request);
    }

    public Observable<String> startCapture() {
        Request request = new Request.Builder()
            .url(buildCommandExecUrl())
            .post(RequestBody.create(jsonMedia, JSON_START_CAPTURE))
            .build();
        return enqueue(request);
    }

    public Observable<String> startSession() {
        Request request = new Request.Builder()
            .url(buildCommandExecUrl())
            .post(RequestBody.create(jsonMedia, JSON_START_SESSION))
            .build();
        return enqueue(request);
    }

    private HttpUrl buildSensorUploadUrl() {
        HttpUrl.Builder builder = getMM360RootUrl();
        return builder
                .addPathSegment("sensor")
                .addPathSegment("raw")
                .build();
    }

    private HttpUrl buildProjectListUrl() {
        HttpUrl.Builder builder = getMM360RootUrl();
        return builder
                .addPathSegment("projects")
                .build();
    }

    private HttpUrl buildStateUrl() {
        HttpUrl.Builder builder = getRootUrlBuilder();
        return builder.addEncodedPathSegment(PATH_OSC)
            .addEncodedPathSegment(PATH_STATE).build();
    }

    private HttpUrl buildCommandExecUrl() {
        HttpUrl.Builder builder = getRootUrlBuilder();
        return builder.addEncodedPathSegment(PATH_OSC)
            .addEncodedPathSegment(PATH_COMMANDS)
            .addEncodedPathSegment(PATH_EXECUTE).build();
    }

    private HttpUrl.Builder getMM360RootUrl() {
        return new HttpUrl.Builder()
                .scheme("http")
                .host("mm360-server.herokuapp.com")
                .addPathSegment("api");
    }

    private HttpUrl.Builder getRootUrlBuilder() {
        return new HttpUrl.Builder()
            .scheme("http")
            .host("192.168.1.1")
            .port(80);
    }

    private Observable<String> enqueue(Request request) {
        return Observable.create(subscriber -> {
            Log.d(TAG, "enqueue: post url: " + request.url().toString());
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    subscriber.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String result = response.body().string();
                    subscriber.onNext(result);
                    subscriber.onCompleted();
                }
            });
        });
    }
}
