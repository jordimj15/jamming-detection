package org.jammingdetection.ingestion.model;

import modes.computed.ComputedAirbornePosition;
import modes.computed.ComputedPosition;

import java.time.Instant;

/**
 * Adapts a decoded {@link ComputedPosition} message into the shape expected
 * by the {@code POSITION} database table.
 *
 * <p>This class is a transition step between decoding
 * and database transaction: the constructor takes a
 * decoded position plus the {@code flightId}/{@code fileId} it belongs to,
 * and extracts only the fields the database needs.
 *
 * <p>{@link ComputedPosition} covers both airborne and surface positions,
 * but altitude and {@code NIC-sub-B} are only transmitted in the airborne message.
 * When the decoded position is a {@link ComputedAirbornePosition},
 * those fields are populated; otherwise they remain {@code null}.
 */
public class PositionMessage {
    private long flightId;
    private long fileId;
    private Instant ts;
    private short typeCode;
    private Short nicSubB;
    private double latitude;
    private double longitude;
    private Integer altitude;

    /**
     * Builds a database ready message from a decoded position message.
     *
     * @param decodedPosition the decoded position, a {@link ComputedPosition}, airborne or surface
     * @param flightId the ID of the {@code Flight} this message belongs to
     * @param fileId the ID of the ADS-B file this message was read from
     */
    public PositionMessage(ComputedPosition decodedPosition, long flightId, long fileId) {
        this.flightId = flightId;
        this.fileId = fileId;
        this.ts = Instant.ofEpochMilli(decodedPosition.getTimeStamp() / 1000);
        this.typeCode = (short) decodedPosition.getTypeCode();
        this.latitude = decodedPosition.getComputedLatitude();
        this.longitude = decodedPosition.getComputedLongitude();

        // Altitude and NIC-sub-B only apply to airborne positions; surface
        // positions leave these fields null.
        if (decodedPosition instanceof ComputedAirbornePosition cap) {
            this.altitude = cap.getAltitude();
            this.nicSubB = (short) cap.getNicSubB();
        }
    }

    public void setFlightId(long flightId) {
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