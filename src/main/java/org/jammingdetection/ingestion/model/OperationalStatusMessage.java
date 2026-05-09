package org.jammingdetection.ingestion.model;

import modes.raw.OperationalStatus;
import modes.raw.SurfaceOperationalStatus;

import java.time.Instant;

public class OperationalStatusMessage {
    private long flightId;
    private long fileId;
    private Instant ts;
    private short nicSubA;
    private short nacSubP;
    private Short nicSupC;
    private short sil;

    public OperationalStatusMessage (OperationalStatus decodedOperationalStatus, long flightId, long fileId){
        this.flightId = flightId;
        this.fileId = fileId;
        this.ts = Instant.ofEpochMilli(decodedOperationalStatus.getTimeStamp() / 1000);
        this.nicSubA = (short) decodedOperationalStatus.getNicSupA();
        this.nacSubP = (short) decodedOperationalStatus.getNACp();
        this.nicSupC =  (decodedOperationalStatus instanceof SurfaceOperationalStatus sos) ? (short) sos.getNICsupC() : null;
        this.sil = (short) decodedOperationalStatus.getSIL();
    }

    public void setFlightId(long flightId){
        this.flightId = flightId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }

    public long getFlightId() {
        return flightId;
    }

    public long getFileId() {
        return fileId;
    }

    public Instant getTs() {
        return ts;
    }

    public short getNicSubA() {
        return nicSubA;
    }

    public short getNacSubP() {
        return nacSubP;
    }

    public Short getNicSupC() {
        return nicSupC;
    }

    public short getSil() {
        return sil;
    }

}
