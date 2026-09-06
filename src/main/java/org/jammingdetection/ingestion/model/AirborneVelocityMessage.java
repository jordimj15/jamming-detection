package org.jammingdetection.ingestion.model;

import modes.raw.AirSpeedAirborneVelocity;
import modes.raw.AirborneVelocity;
import modes.raw.GroundSpeedAirborneVelocity;

import java.time.Instant;

/**
 * Adapts a decoded {@link AirborneVelocity} message into the expected object
 * by the {@code AIRBORNE_VELOCITY} database table.
 *
 * <p>This class exists as a transition step between decoding and
 * transaction: the constructor takes a decoded message plus the
 * {@code flightId}/{@code fileId} it belongs to, and extracts only the
 * fields the database needs. This keeps {@code MessageService} free of
 * specific logic and avoids repeating field mapping code.
 *
 * <p>{@link AirborneVelocity} has two subtypes depending on how the aircraft
 * reports its velocity: {@link GroundSpeedAirborneVelocity} (east/west and
 * north/south speed components) or {@link AirSpeedAirborneVelocity} (airspeed
 * and magnetic heading). Only the fields relevant to the given subtype are
 * populated; the others remain {@code null}, as the database accepts nullable fields.
 */
public class AirborneVelocityMessage {
    private long flightId;
    private long fileId;
    private Instant ts;
    private short nacSubV;
    private Integer groundSpeedEW;
    private Integer groundSpeedNS;
    private Double airSpeed;
    private Double magneticHeading;

    /**
     * Builds a database ready message from a decoded airborne velocity message.
     *
     * @param decodedVelocity the decoded message.
     * @param flightId the ID of the {@code Flight} this message belongs to
     * @param fileId the ID of the ADS-B file this message was read from
     */
    public AirborneVelocityMessage(AirborneVelocity decodedVelocity, long flightId, long fileId) {
        this.flightId = flightId;
        this.fileId = fileId;
        this.ts = Instant.ofEpochMilli(decodedVelocity.getTimeStamp() / 1000);
        this.nacSubV = (short) decodedVelocity.getNACv();

        // Depending on the sub-type, one of the fields will be null or the others.
        switch (decodedVelocity) {
            case GroundSpeedAirborneVelocity gsVel -> {
                this.groundSpeedEW = gsVel.getVelocityEastWest();
                this.groundSpeedNS = gsVel.getVelocitySouthWest();
            }
            case AirSpeedAirborneVelocity asVel -> {
                this.airSpeed = asVel.getAirspeed();
                this.magneticHeading = asVel.getHeading();
            }
            default -> {}
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

    public short getNacSubV() {
        return nacSubV;
    }

    public Integer getGroundSpeedEW() {
        return groundSpeedEW;
    }

    public Integer getGroundSpeedNS() {
        return groundSpeedNS;
    }

    public Double getAirSpeed() {
        return airSpeed;
    }

    public Double getMagneticHeading() {
        return magneticHeading;
    }
}