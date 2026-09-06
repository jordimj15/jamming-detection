package org.jammingdetection.detection.service;

import org.jammingdetection.config.Database;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;
import org.jammingdetection.generated.ingestion.tables.records.PositionRecord;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jammingdetection.generated.ingestion.Tables.*;

/**
 * Fetches the ADS-B message data needed to run anomaly detection on a file.
 *
 * <p>Detection needs more than just the messages of the file being
 * processed to keep consistency. Quality indicators upgrade/downgrade anomalies (NIC, NACp, NACv, SIL)
 * require comparing a file's first message of each type against the last message
 * of the immediately previous file. The position-gap logic requires knowing how many
 * Operational Status / Airborne Velocity messages were actually received during the
 * gap between two consecutive positions. This service provides both kinds of queries:
 *
 * <ul>
 *   <li>{@code fetchX(fileId)} — all messages of each type in the given file,
 *       grouped by flight and ordered by timestamp. The baseline fetch for
 *       detection analysis of the current file.</li>
 *   <li>{@code fetchPreviousX(record)} — the single most recent message of
 *       type X for the same flight in a different (immediately earlier) file, used for
 *       upgrade/downgrade comparisons at file boundaries.</li>
 *   <li>{@code fetchAllPreviousX(prevPositionRecord, firstPositionRecord)} —
 *       all Operational Status / Airborne Velocity messages for a flight
 *       that fall strictly between two given position timestamps, used to
 *       evaluate the position-gap jamming logic.</li>
 * </ul>
 */
public class MessageFetchService {

    /**
     * Fetches all position messages in the given file, grouped by flight
     * and ordered by timestamp within each flight.
     *
     * @param fileId the ID of the ADS-B file to fetch positions for
     * @return a map of flight ID to that flight's position messages, timestamp sorted
     */
    public Map<Long, List<PositionRecord>> fetchPositions(long fileId){
        return Database.ctx
                .selectFrom(POSITION)
                .where(POSITION.FILE_ID.eq(fileId))
                .orderBy(POSITION.FLIGHT_ID, POSITION.TS)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(PositionRecord::getFlightId));
    }

    /**
     * Fetches the most recent position message for the same flight as
     * {@code firstPositionRecord}, from an earlier file.
     *
     * <p>Used to compare a file's first position against the flight's last
     * known position from the previous file, so that NIC upgrade/downgrade
     * anomalies spanning a file boundary are not missed.
     *
     * @param firstPositonRecord the first position record of the file being analyzed
     * @return the flight's most recent prior position record from a
     * different file, or {@code null} if none exists
     */
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

    /**
     * Fetches all operational status messages in the given file, grouped by
     * flight and ordered by timestamp within each flight.
     *
     * @param fileId the ID of the ADS-B file to fetch operational status messages for
     * @return a map of flight ID to that flight's operational status messages, in timestamp order
     */
    public Map<Long, List<OperationalStatusRecord>> fetchOperationalStatus(long fileId){
        return Database.ctx
                .selectFrom(OPERATIONAL_STATUS)
                .where(OPERATIONAL_STATUS.FILE_ID.eq(fileId))
                .orderBy(OPERATIONAL_STATUS.FLIGHT_ID, OPERATIONAL_STATUS.TS)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(OperationalStatusRecord::getFlightId));
    }

    /**
     * Fetches the most recent operational status message for the same
     * flight as {@code firstOperationalStatus}, from an earlier file.
     *
     * <p>Used to compare a file's first operational status message against
     * the flight's last known one from the previous file, so that NIC/NACp/SIL
     * upgrade/downgrade anomalies spanning a file boundary are not missed.
     *
     * @param firstOperationalStatus the first operational status record of the file being analyzed
     * @return the flight's most recent prior operational status record from
     * a different file, or {@code null} if none exists
     */
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

    /**
     * Fetches all operational status messages for the flight in
     * {@code prevPositionRecord} that fall  between the two given position timestamps.
     *
     * <p>Used by the position-gap jamming algorithm to determine how many
     * operational status messages were actually received during the time
     * between two consecutive positions, against how many were expected
     * given the reporting rate. This gives the algorithm consistency between files.
     *
     * @param prevPositionRecord  the position marking the first possible position message
     * @param firstPositionRecord the position marking the last position message
     * @return the operational status messages received within the gap, most recent first
     */
    public List<OperationalStatusRecord> fetchAllPreviousOperationalStatus(PositionRecord prevPositionRecord, PositionRecord firstPositionRecord) {
        return Database.ctx
                .selectFrom(OPERATIONAL_STATUS)
                .where(OPERATIONAL_STATUS.FLIGHT_ID.eq(prevPositionRecord.getFlightId()))
                .and(OPERATIONAL_STATUS.FILE_ID.eq(prevPositionRecord.getFileId()))
                .and(OPERATIONAL_STATUS.TS.greaterThan(prevPositionRecord.getTs()))
                .and(OPERATIONAL_STATUS.TS.lessThan(firstPositionRecord.getTs()))
                .orderBy(OPERATIONAL_STATUS.TS.desc())
                .fetchInto(OperationalStatusRecord.class);
    }

    /**
     * Fetches all airborne velocity messages in the given file, grouped by
     * flight and ordered by timestamp within each flight.
     *
     * @param fileId the ID of the ADS-B file to fetch airborne velocity messages for
     * @return a map of flight ID to that flight's airborne velocity messages, timestamp sorted
     */
    public Map<Long, List<AirborneVelocityRecord>> fetchAirborneVelocity(long fileId) {
        return Database.ctx
                .selectFrom(AIRBORNE_VELOCITY)
                .where(AIRBORNE_VELOCITY.FILE_ID.eq(fileId))
                .orderBy(AIRBORNE_VELOCITY.FLIGHT_ID, AIRBORNE_VELOCITY.FLIGHT_ID)
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(AirborneVelocityRecord::getFlightId));
    }

    /**
     * Fetches the most recent airborne velocity message for the same flight
     * as {@code firstAirborneVelocity}, from an earlier file.
     *
     * <p>Used to compare a file's first airborne velocity message against the
     * flight's last known one from the previous file, so that NAC-v
     * upgrade/downgrade anomalies spanning a file boundary are not missed.
     *
     * @param firstAirborneVelocity the first airborne velocity record of the file being analyzed
     * @return the flight's most recent prior airborne velocity record from a
     * different file, or {@code null} if none exists
     */
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

    /**
     * Fetches all airborne velocity messages for the flight in
     * {@code prevPositionRecord} that fall strictly between the two given
     * position timestamps.
     *
     * <p>Used by the position-gap jamming algorithm to determine how many
     * airborne velocity messages were actually received during the time
     * between two consecutive positions, against how many were expected
     * given the reporting rate.
     *
     * @param prevPositionRecord  the position marking the first possible position message
     * @param firstPositionRecord the position marking the last position message
     * @return the airborne velocity messages received within these position messages, most recent first
     */
    public List<AirborneVelocityRecord> fetchAllPreviousAirborneVelocity(PositionRecord prevPositionRecord, PositionRecord firstPositionRecord) {
        return Database.ctx
                .selectFrom(AIRBORNE_VELOCITY)
                .where(AIRBORNE_VELOCITY.FLIGHT_ID.eq(prevPositionRecord.getFlightId()))
                .and(AIRBORNE_VELOCITY.FILE_ID.eq(prevPositionRecord.getFileId()))
                .and(AIRBORNE_VELOCITY.TS.greaterThan(prevPositionRecord.getTs()))
                .and(AIRBORNE_VELOCITY.TS.lessThan(firstPositionRecord.getTs()))
                .orderBy(AIRBORNE_VELOCITY.TS.desc())
                .fetchInto(AirborneVelocityRecord.class);
    }

}
