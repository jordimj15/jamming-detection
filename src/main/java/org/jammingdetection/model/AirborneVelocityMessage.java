package org.jammingdetection.model;

import modes.raw.AirSpeedAirborneVelocity;
import modes.raw.AirborneVelocity;
import modes.raw.GroundSpeedAirborneVelocity;

import java.time.Instant;

public class AirborneVelocityMessage {
    private long flightId;
    private long fileId;
    private Instant ts;
    private short nacSubV;
    private Integer groundSpeedEW;
    private Integer groundSpeedNS;
    private Double airSpeed;
    private Double magneticHeading;

    public AirborneVelocityMessage (AirborneVelocity decodedVelocity){
        this.ts = Instant.ofEpochMilli(decodedVelocity.getTimeStamp());
        this.nacSubV = (short) decodedVelocity.getNACv();
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
    public void setFlightId(long flightId){
        this.flightId = flightId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }
}
