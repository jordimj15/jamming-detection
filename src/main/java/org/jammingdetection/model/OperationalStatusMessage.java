package org.jammingdetection.model;

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

    public OperationalStatusMessage (OperationalStatus decodedOperationalStatus){
        this.ts = Instant.ofEpochMilli(decodedOperationalStatus.getTimeStamp());
        this.nicSubA = (short) decodedOperationalStatus.getNicSupA();
        this.nacSubP = (short) decodedOperationalStatus.getNACp();
        this.nicSupC =  (decodedOperationalStatus instanceof SurfaceOperationalStatus sos) ? (short) sos.getNICsupC() : null;
    }

    public void setFlightId(long flightId){
        this.flightId = flightId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }
}
