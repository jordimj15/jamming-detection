package org.jammingdetection.ingestion.model;

import modes.AdsbMessage;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a single flight: a continuous sequence of ADS-B messages
 * sharing the same ICAO address and callsign, as resolved by {@code FlightService}.
 *
 * <p>Tracks the time range covered by the flight's messages, along with
 * message types sets of the timestamps. These sets are used to detect
 * and skip duplicate messages.
 */

public class Flight {
    private long id;
    private String icaoAddress;
    private String callsign;
    private String aircraftCategory;
    private Instant firstMsgTs;
    private Instant lastMsgTs;

    private final Set<Long> positionTimestampList =new HashSet<>();
    private final Set<Long> operationalStatusTimestampList = new HashSet<>();
    private final Set<Long> airborneVelocityTimestampList = new HashSet<>();

    public boolean addPositionTimestampList(long timestamp){
        return positionTimestampList.add(timestamp);
    }
    public boolean addOperationalStatusTimestampList(long timestamp){
        return operationalStatusTimestampList.add(timestamp);
    }
    public boolean addAirborneVelocityTimestampList(long timestamp){
        return airborneVelocityTimestampList.add(timestamp);
    }

    public Flight () {

    }

    public Flight (AdsbMessage decodedMessage) {
        this.icaoAddress = decodedMessage.getIcaoAddress();
        this.callsign = decodedMessage.getCallsign();
        this.aircraftCategory = decodedMessage.getAircraftCategory();
        this.firstMsgTs = Instant.ofEpochMilli(decodedMessage.getTimeStamp() / 1000);
        this.lastMsgTs = Instant.ofEpochMilli(decodedMessage.getTimeStamp() / 1000);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getIcaoAddress() {
        return icaoAddress;
    }

    public void setIcaoAddress(String icaoAddress) {
        this.icaoAddress = icaoAddress;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getAircraftCategory() {
        return aircraftCategory;
    }

    public void setAircraftCategory(String aircraftCategory) {
        this.aircraftCategory = aircraftCategory;
    }

    public Instant getFirstMsgTs() {
        return firstMsgTs;
    }

    public void setFirstMsgTs(Instant firstMsgTs) {
        this.firstMsgTs = firstMsgTs;
    }

    public Instant getLastMsgTs() {
        return lastMsgTs;
    }

    public void setLastMsgTs(Instant lastMsgTs) {
        this.lastMsgTs = lastMsgTs;
    }
}
