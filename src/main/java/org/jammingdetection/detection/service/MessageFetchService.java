package org.jammingdetection.detection.service;

import org.jammingdetection.config.Database;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;
import org.jammingdetection.generated.ingestion.tables.records.PositionRecord;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jammingdetection.generated.ingestion.Tables.*;

public class MessageFetchService {

    public Map<Long, List<PositionRecord>> fetchPositions(long fileId){
        return Database.ctx
                .selectFrom(POSITION)
                .where(POSITION.FILE_ID.eq(fileId))
                .orderBy(POSITION.FLIGHT_ID, POSITION.TS)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(PositionRecord::getFlightId));
    }

    public PositionRecord fetchPreviousPosition(PositionRecord firstPositonRecord){
        return Database.ctx
                .selectFrom(POSITION)
                .where(POSITION.FLIGHT_ID.eq(firstPositonRecord.getFlightId()))
                .and(POSITION.FILE_ID.notEqual(firstPositonRecord.getFileId()))
                .and(POSITION.TS.lessThan(firstPositonRecord.getTs()))
                .orderBy(POSITION.TS.desc())
                .limit(1)
                .fetchOne();
    }

    public Map<Long, List<OperationalStatusRecord>> fetchOperationalStatus(long fileId){
        return Database.ctx
                .selectFrom(OPERATIONAL_STATUS)
                .where(OPERATIONAL_STATUS.FILE_ID.eq(fileId))
                .orderBy(OPERATIONAL_STATUS.FLIGHT_ID, OPERATIONAL_STATUS.TS)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(OperationalStatusRecord::getFlightId));
    }

    public OperationalStatusRecord fetchPreviousOperationalStatus(OperationalStatusRecord firstOperationalStatus) {
        return Database.ctx
                .selectFrom(OPERATIONAL_STATUS)
                .where(OPERATIONAL_STATUS.FLIGHT_ID.eq(firstOperationalStatus.getFlightId()))
                .and(OPERATIONAL_STATUS.FILE_ID.notEqual(firstOperationalStatus.getFileId()))
                .and(OPERATIONAL_STATUS.TS.lessThan(firstOperationalStatus.getTs()))
                .orderBy(OPERATIONAL_STATUS.TS.desc())
                .limit(1)
                .fetchOne();
    }

    public Map<Long, List<AirborneVelocityRecord>> fetchAirborneVelocity(long fileId) {
        return Database.ctx
                .selectFrom(AIRBORNE_VELOCITY)
                .where(AIRBORNE_VELOCITY.FILE_ID.eq(fileId))
                .orderBy(AIRBORNE_VELOCITY.FLIGHT_ID, AIRBORNE_VELOCITY.FLIGHT_ID)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(AirborneVelocityRecord::getFlightId));
    }

    public AirborneVelocityRecord fetchPreviousAirborneVelocity(AirborneVelocityRecord firstAirborneVelocity) {
        return Database.ctx
                .selectFrom(AIRBORNE_VELOCITY)
                .where(AIRBORNE_VELOCITY.FLIGHT_ID.eq(firstAirborneVelocity.getFlightId()))
                .and(AIRBORNE_VELOCITY.FILE_ID.notEqual(firstAirborneVelocity.getFileId()))
                .and(AIRBORNE_VELOCITY.TS.lessThan(firstAirborneVelocity.getTs()))
                .orderBy(AIRBORNE_VELOCITY.TS.desc())
                .limit(1)
                .fetchOne();
    }

}
