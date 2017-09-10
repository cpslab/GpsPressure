package jp.ac.dendai.im.cps.gpspressure.model;

public class KalmanLatLng {

    private float Q_metres_per_second;

    private float minAccuracy = 1f;

    private float variance = -1f;

    long TimeStamp_milliseconds;

    public double lat;

    public double lng;

    public int consecutiveRejectCount;

    public KalmanLatLng(float q_metres_per_second) {
        Q_metres_per_second = q_metres_per_second;
    }

    public float getAccuracy() {

        return (float) Math.sqrt(variance);
    }

    public void setState(double lat, double lng, float accuracy, long TimeStamp_milliseconds) {
        this.lat = lat;
        this.lng = lng;
        this.variance = accuracy * accuracy;
        this.TimeStamp_milliseconds = TimeStamp_milliseconds;
    }

    public void process(double lat_measurement, double lng_measurement,
                float accuracy, long TimeStamp_milliseconds, float Q_metres_per_second) {
        float innerAccuracy = accuracy;
        this.Q_metres_per_second = Q_metres_per_second;

        if (innerAccuracy < minAccuracy)
            innerAccuracy = minAccuracy;
        if (variance < 0) {
            // if variance < 0, object is unitialised, so initialise with
            // current values
            setState(lat_measurement, lng_measurement, innerAccuracy, TimeStamp_milliseconds);
        } else {
            // else apply Kalman filter methodology
            float TimeInc_milliseconds = TimeStamp_milliseconds - this.TimeStamp_milliseconds;
            if (TimeInc_milliseconds > 0) {
                // time has moved on, so the uncertainty in the current position
                // increases
                variance += TimeInc_milliseconds * Q_metres_per_second * Q_metres_per_second / 1000;
                this.TimeStamp_milliseconds = TimeStamp_milliseconds;
                // TO DO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE
                // OF CURRENT POSITION
            }

            // Kalman gain matrix K = Covarariance * Inverse(Covariance +
            // MeasurementVariance)
            // NB: because K is dimensionless, it doesn't matter that variance
            // has different units to lat and lng
            double K = variance / (variance + innerAccuracy * innerAccuracy);
            // apply K
            lat += K * (lat_measurement - lat);
            lng += K * (lng_measurement - lng);
            // new Covarariance matrix is (IdentityMatrix - K) * Covarariance
            variance *= (1 - K);
        }
    }
}