package org.jammingdetection.ingestion.model;
import org.jammingdetection.generated.ingestion.tables.records.AdsbFileRecord;


public class AdsbFile {
    private long id;
    private short hour;
    private long sensorSerial;
    private int totalMsgCount;
    private long timeToDecode;

    public AdsbFile (AdsbFileRecord record){
        this.id = record.getId();
        this.hour = record.getHour();
        this.sensorSerial = record.getSensorSerial();
        this.totalMsgCount = record.getTotalMsgCount();
        this.timeToDecode = record.getTimeToDecode();
    }

    public long getId() {
        return id;
    }
}
