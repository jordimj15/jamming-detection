package org.jammingdetection.ingestion.model;

import modes.AdsbMessage;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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
        if(!positionTimestampList.contains(timestamp)) {
            positionTimestampList.add(timestamp);
            return true;
        }
        return false;
    }
    public boolean addOperationalStatusTimestampList(long timestamp){
        if(!operationalStatusTimestampList.contains(timestamp)) {
            operationalStatusTimestampList.add(timestamp);
            return true;
        }
        return false;
    }
    public boolean addAirborneVelocityTimestampList(long timestamp){
        if(!airborneVelocityTimestampList.contains(timestamp)) {
            airborneVelocityTimestampList.add(timestamp);
            return true;
        }
        return false;
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
