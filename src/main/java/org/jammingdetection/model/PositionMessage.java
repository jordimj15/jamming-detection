package org.jammingdetection.model;
import modes.computed.ComputedAirbornePosition;
import modes.computed.ComputedPosition;
import modes.computed.ComputedSurfacePosition;

import java.time.Instant;

public class PositionMessage {
    private long flightId;
    private long fileId;
    private Instant ts;
    private short typeCode;
    private Short nicSubB;
    private double latitude;
    private double longitude;
    private Integer altitude;

    public PositionMessage (ComputedPosition decodedPosition, long flightId, long fileId) {
        this.flightId = flightId;
        this.fileId = fileId;
        this.ts = Instant.ofEpochMilli(decodedPosition.getTimeStamp());
        this.typeCode = (short) decodedPosition.getTypeCode();
        this.latitude = decodedPosition.getComputedLatitude();
        this.longitude = decodedPosition.getComputedLongitude();
        if (decodedPosition instanceof ComputedAirbornePosition cap) {
            this.altitude = cap.getAltitude();
            this.nicSubB = (short) cap.getNicSubB();
        }
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

    public short getTypeCode() {
        return typeCode;
    }

    public Short getNicSubB() {
        return nicSubB;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Integer getAltitude() {
        return altitude;
    }
}
