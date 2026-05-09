package org.jammingdetection.service;

import modes.computed.ComputedPosition;
import modes.raw.AirborneVelocity;
import modes.raw.OperationalStatus;
import modes.raw.Position;
import org.jammingdetection.config.Database;
import org.jammingdetection.model.AirborneVelocityMessage;
import org.jammingdetection.model.Flight;
import org.jammingdetection.model.OperationalStatusMessage;
import org.jammingdetection.model.PositionMessage;

import org.jooq.BatchBindStep;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.jammingdetection.generated.Tables.*;


public class MessageService {
    private static final int FLUSH_THRESHOLD = 10000;


    private final List<OperationalStatusMessage> operationalStatusMessageList = new ArrayList<>();
    private final List<PositionMessage> positionMessageList = new ArrayList<>();
    private final List<AirborneVelocityMessage> airborneVelocityMessageList = new ArrayList<>();

    public void addToOperationalStatusList (OperationalStatus decodedOperationalStatus, long flightId, long fileId){
        operationalStatusMessageList.add(new OperationalStatusMessage(decodedOperationalStatus, flightId, fileId));
        if (operationalStatusMessageList.size() >= FLUSH_THRESHOLD) flushOperationalStatus();
    }

    public void addToPositionList (ComputedPosition decodedComputedPosition, long flightId, long fileId) {
        positionMessageList.add(new PositionMessage(decodedComputedPosition, flightId, fileId));
        if (positionMessageList.size() >= FLUSH_THRESHOLD) flushPosition();
    }

    public void addToAirborneVelocity(AirborneVelocity decodedAirborneVelocity, long flightId, long fileId) {
        airborneVelocityMessageList.add(new AirborneVelocityMessage(decodedAirborneVelocity, flightId, fileId));
        if(airborneVelocityMessageList.size() >= FLUSH_THRESHOLD) flushAirborneVelocity();
    }

    public void flushAll(){
        flushPosition();
        flushOperationalStatus();
        flushAirborneVelocity();
    }

    private void flushPosition(){
        if (positionMessageList.isEmpty()) return;

        BatchBindStep batch = Database.ctx.batch(
                Database.ctx.insertInto(POSITION,
                        POSITION.FLIGHT_ID,
                        POSITION.FILE_ID,
                        POSITION.TS,
                        POSITION.TYPE_CODE,
                        POSITION.NIC_SUP_B,
                        POSITION.LATITUDE,
                        POSITION.LONGITUDE,
                        POSITION.ALTITUDE
                ).values((Long) null, null, null, null, null, null, null, null)
        );

        for (PositionMessage positionMessage: positionMessageList){
            batch.bind(
                    positionMessage.getFlightId(),
                    positionMessage.getFileId(),
                    positionMessage.getTs().atOffset(ZoneOffset.UTC),
                    positionMessage.getTypeCode(),
                    positionMessage.getNicSubB(),
                    positionMessage.getLatitude(),
                    positionMessage.getLongitude(),
                    positionMessage.getAltitude()
            );
        }

        batch.execute();
        positionMessageList.clear();
    }

    private void flushOperationalStatus(){
        if (operationalStatusMessageList.isEmpty()) return;

        BatchBindStep batch = Database.ctx.batch(
                Database.ctx.insertInto(OPERATIONAL_STATUS,
                        OPERATIONAL_STATUS.FLIGHT_ID,
                        OPERATIONAL_STATUS.FILE_ID,
                        OPERATIONAL_STATUS.TS,
                        OPERATIONAL_STATUS.NIC_SUP_A,
                        OPERATIONAL_STATUS.NAC_SUP_P,
                        OPERATIONAL_STATUS.NIC_SUP_C,
                        OPERATIONAL_STATUS.SIL
                ).values((Long) null, null, null, null, null, null, null)
        );

        for (OperationalStatusMessage operationalStatusMessage : operationalStatusMessageList) {
            batch.bind(
                    operationalStatusMessage.getFlightId(),
                    operationalStatusMessage.getFileId(),
                    operationalStatusMessage.getTs().atOffset(ZoneOffset.UTC),
                    operationalStatusMessage.getNicSubA(),
                    operationalStatusMessage.getNacSubP(),
                    operationalStatusMessage.getNicSupC(),
                    operationalStatusMessage.getSil()
            );
        }

        batch.execute();
        operationalStatusMessageList.clear();
    }

    private void flushAirborneVelocity() {
        if (airborneVelocityMessageList.isEmpty()) return;

        BatchBindStep batch = Database.ctx.batch(
                Database.ctx.insertInto(AIRBORNE_VELOCITY,
                        AIRBORNE_VELOCITY.FLIGHT_ID,
                        AIRBORNE_VELOCITY.FILE_ID,
                        AIRBORNE_VELOCITY.TS,
                        AIRBORNE_VELOCITY.NAC_SUB_V,
                        AIRBORNE_VELOCITY.GROUND_SPEED_EW,
                        AIRBORNE_VELOCITY.GROUND_SPEED_NS,
                        AIRBORNE_VELOCITY.AIRSPEED,
                        AIRBORNE_VELOCITY.MAGNETIC_HEADING
                ).values((Long) null, null, null, null, null, null, null, null)
        );

        for (AirborneVelocityMessage airborneVelocityMessage : airborneVelocityMessageList) {
            batch.bind(
                    airborneVelocityMessage.getFlightId(),
                    airborneVelocityMessage.getFileId(),
                    airborneVelocityMessage.getTs().atOffset(ZoneOffset.UTC),
                    airborneVelocityMessage.getNacSubV(),
                    airborneVelocityMessage.getGroundSpeedEW(),
                    airborneVelocityMessage.getGroundSpeedNS(),
                    airborneVelocityMessage.getAirSpeed(),
                    airborneVelocityMessage.getMagneticHeading()
            );
        }

        batch.execute();
        airborneVelocityMessageList.clear();
    }
}
