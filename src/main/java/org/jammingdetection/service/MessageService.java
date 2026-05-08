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
        OperationalStatusMessage operationalStatusMessage = new OperationalStatusMessage(decodedOperationalStatus);
        operationalStatusMessage.setFlightId(flightId);
        operationalStatusMessage.setFileId(fileId);
        operationalStatusMessageList.add(operationalStatusMessage);
    }

    public void addToPositionList (ComputedPosition decodedComputedPosition, long flightId, long fileId) {
        PositionMessage positionMessage = new PositionMessage(decodedComputedPosition);
        positionMessage.setFlightId(flightId);
        positionMessage.setFileId(fileId);
        positionMessageList.add(positionMessage);
    }

    public void addToAirborneVelocity(AirborneVelocity decodedAirborneVelocity, long flightId, long fileId) {
        AirborneVelocityMessage airborneVelocityMessage = new AirborneVelocityMessage(decodedAirborneVelocity);
        airborneVelocityMessage.setFlightId(flightId);
        airborneVelocityMessage.setFileId(fileId);
        airborneVelocityMessageList.add(airborneVelocityMessage);
    }

    public void flushAll(){

    }

    private void flushAllPositions(){
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
}
