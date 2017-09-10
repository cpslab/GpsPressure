package jp.ac.dendai.im.cps.gpspressure.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import jp.ac.dendai.im.cps.gpspressure.R;
import jp.ac.dendai.im.cps.gpspressure.network.ApiClient;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ProjectListActivity extends AppCompatActivity {

    private String TAG = ProjectListActivity.class.getSimpleName();

    private ApiClient client = new ApiClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        fetchProjectList();
    }

    private void fetchProjectList() {
        client.fetchProjectList()
                .observeOn(Schedulers.io())
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    Log.d(TAG, result);
                }, throwable -> {
                    Log.e(TAG, "error", throwable);
                });
    }
}
