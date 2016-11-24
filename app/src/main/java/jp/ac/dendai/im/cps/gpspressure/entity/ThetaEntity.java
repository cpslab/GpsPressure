package jp.ac.dendai.im.cps.gpspressure.entity;

import java.util.List;

public class ThetaEntity {
    public String name;
    public String state;
    public ResultEntity results;

    public class ResultEntity {
        public String sessionId;
        public int timeout;
        public List<String> fileUrls;

        @Override
        public String toString() {
            return "ResultEntity{" +
                "sessionId='" + sessionId + '\'' +
                ", timeout=" + timeout +
                ", fileUrls=" + fileUrls +
                '}';
        }
    }

    @Override
    public String toString() {
        return "ThetaEntity{" +
            "name='" + name + '\'' +
            ", state='" + state + '\'' +
            ", results=" + results +
            '}';
    }
}
