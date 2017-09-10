package jp.ac.dendai.im.cps.gpspressure.model;

import android.location.Location;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import jp.ac.dendai.im.cps.gpspressure.Utils;

public class SensorStoreClient {

    private final String TAG = this.getClass().getSimpleName();

    private static final String PATH_DIR = Environment.getExternalStorageDirectory().getPath() + "/thetagps/";

    private String filename;

    public void createCsv() {
        this.filename = Utils.parseDate(System.currentTimeMillis()) + ".csv";
        File file = new File(PATH_DIR);
        if (!file.isDirectory()) {
            file.getAbsoluteFile().mkdir();
        }

        File dataFile = new File(PATH_DIR + filename);

        Log.d(TAG, "createCsv: " + dataFile.getPath());

        OutputStream outputStream;
        String data = "currentTimeMillis, timestamp, latitude, altitude, accuracy, pressure, 方位角, 前後傾斜, 左右傾斜, orientation1, orientation2, orientation3, lowpass方位角\n";

        try {
            outputStream = new FileOutputStream(dataFile, true);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.append(data);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void finishCsv(String moveName) {
        File file = new File(PATH_DIR);
        if (!file.isDirectory()) {
            file.getAbsoluteFile().mkdir();
        }

        File dataFile = new File(PATH_DIR + filename);
        OutputStream outputStream;
        try {
            outputStream = new FileOutputStream(dataFile, true);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.append(moveName);
            writer.append("\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveCsv(Location location, String pressure, float[] attitude, float[] orientation) {
        if (location == null) {
            return;
        }

        File file = new File(PATH_DIR);
        if (!file.isDirectory()) {
            file.getAbsoluteFile().mkdir();
        }

        File dataFile = new File(PATH_DIR + filename);
        OutputStream outputStream;
        String data = createCsvFormat(location, pressure, attitude, orientation);
        try {
            outputStream = new FileOutputStream(dataFile, true);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.append(data);
            writer.append("\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * timestamp, latitude, altitude, accuracy, pressure, 方位角, 前後, 左右, orientation1, orientation2, orientation3
     * @return csv format
     */
    private String createCsvFormat(Location location, String pressure, float[] attitude) {
        String data =
            System.currentTimeMillis() + "," +
                Utils.parseDate(System.currentTimeMillis()) + "," +
                location.getLatitude() + "," +
                location.getLongitude() + "," +
                location.getAltitude() + "," +
                location.getAccuracy() + "," +
                pressure + "," +
                attitude[0] + "," +
                attitude[1] + "," +
                attitude[2];

        return data;
    }

    private String createCsvFormat(Location location, String pressure, float[] attitude, float[] orientation) {
        String data = createCsvFormat(location, pressure, attitude);
        data += "," + orientation[0] + "," + orientation[1] + "," + orientation[2];
        return data;
    }
}
