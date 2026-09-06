package org.jammingdetection.detection.service;

import org.jammingdetection.config.Config;
import org.jammingdetection.config.Database;
import org.jammingdetection.generated.detection.tables.records.PositionGapAnomalyRecord;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.jammingdetection.generated.detection.Tables.*;
import static org.jammingdetection.generated.ingestion.Tables.*;

/**
 * Saves jamming related anomalies detected for a single ADS-B file:
 * position gaps, and NIC/SIL/NAC-p/NAC-v quality indicator downgrades
 * and upgrades.
 *
 * <p> The same anomaly (same flight and anomaly type) can be detected
 * multiple times across different files, as different sensors may detect
 * the same flight at the same time. To avoid duplicate rows for what is
 * really one event, each {@code save*Anomaly} method checks for an existing
 * anomaly of the same kind within a configurable time window (see
 * {@code detection.anomaly.dedup.*} config keys) before inserting:
 *   <li>If no matching anomaly exists, a new one is inserted.</li>
 *   <li>If one exists and the new detected has better coverage (e.g. a
 *       position gap with more missing messages), the existing anomaly is
 *       updated to show the current file rather than creating
 *       a duplicate.</li>
 *   <li>Otherwise, the new anomaly is discarded as a weaker duplicate
 *       of an already-recorded anomaly.</li>
 * </ul>
 *
 * <p>Position-gap deduplication and "which event is stronger" logic
 * is implemented directly in {@link #savePositionGapAnomaly}, rather than
 * through a separate {@code exists} helper as with the rest of anomalies,
 * since the comparison in the position gap logic duplicates is more complex
 * than a simple exists check.
 */
public class AnomalyService {

    /** Time window, in seconds, within which two position gaps are considered the same event. */
    private static final long ANOMALY_DEDUP_POSITION_SECONDS = Config.getLong("detection.anomaly.dedup.position.seconds");

    /** Time window, in seconds, within which two quality-indicator anomalies of the same direction are considered the same event. */
    private static final long ANOMALY_DEDUP_DOWNGRADE_SECONDS = Config.getLong("detection.anomaly.dedup.downgrade.seconds");

    private final long fileId;

    public AnomalyService(long fileId) {
        this.fileId = fileId;
    }

    /**
     * Saves a position gap anomaly: a gap between two consecutive positions
     * for a flight during which operational status messages were received than expected.
     * Meaning that the gap is potentially due to loss of GNSS rather than LOS with the flight.
     * Airborne velocity messages are saved because one of the goals of the software is to
     * characterise the behavior of this message during jammings.
     *
     * <p>Deduplicates against existing position gap anomalies for the same
     * flight whose previous position timestamp falls within
     * {@link #ANOMALY_DEDUP_POSITION_SECONDS} of {@code ts}. If a match is
     * found, the anomaly is only updated (to this file, with the new
     * message counts) when the new event has more received operational
     * status messages than the existing record.
     *
     * @param flightId the ID of the flight the gap belongs to
     * @param previousMsgId the ID of the position message just before the gap
     * @param lastMsgId the ID of the position message just after the gap
     * @param receivedOs the number of operational status messages received during the gap
     * @param receivedAv the number of airborne velocity messages received during the gap
     * @param ts the timestamp of the previous position, used for deduplication matching
     */
    public void savePositionGapAnomaly(long flightId, Long previousMsgId, long lastMsgId, int receivedOs, int receivedAv, OffsetDateTime ts) {
        PositionGapAnomalyRecord existing = Database.ctx
                .select(POSITION_GAP_ANOMALY.fields())
                .from(POSITION_GAP_ANOMALY)
                .join(POSITION)
                .on(POSITION.ID.eq(POSITION_GAP_ANOMALY.PREVIOUS_POSITION_ID))
                .where(POSITION_GAP_ANOMALY.FLIGHT_ID.eq(flightId))
                .and(POSITION_GAP_ANOMALY.FILE_ID.notEqual(this.fileId))
                .and(POSITION.TS.between(
                        ts.minusSeconds(ANOMALY_DEDUP_POSITION_SECONDS),
                        ts.plusSeconds(ANOMALY_DEDUP_POSITION_SECONDS)
                ))
                .limit(1)
                .fetchAnyInto(PositionGapAnomalyRecord.class);

        if (existing != null) {
            if (receivedOs <= existing.getReceivedOs()) return;

            Database.ctx
                    .update(POSITION_GAP_ANOMALY)
                    .set(POSITION_GAP_ANOMALY.PREVIOUS_POSITION_ID, previousMsgId)
                    .set(POSITION_GAP_ANOMALY.AFTER_POSITION_ID, lastMsgId)
                    .set(POSITION_GAP_ANOMALY.RECEIVED_OS, receivedOs)
                    .set(POSITION_GAP_ANOMALY.RECEIVED_AV, receivedAv)
                    .set(POSITION_GAP_ANOMALY.FILE_ID, this.fileId)
                    .where(POSITION_GAP_ANOMALY.ID.eq(existing.getId()))
                    .execute();

            return;
        }

        Database.ctx
                .insertInto(POSITION_GAP_ANOMALY)
                .set(POSITION_GAP_ANOMALY.FILE_ID, this.fileId)
                .set(POSITION_GAP_ANOMALY.FLIGHT_ID, flightId)
                .set(POSITION_GAP_ANOMALY.PREVIOUS_POSITION_ID, previousMsgId)
                .set(POSITION_GAP_ANOMALY.AFTER_POSITION_ID, lastMsgId)
                .set(POSITION_GAP_ANOMALY.RECEIVED_OS, receivedOs)
                .set(POSITION_GAP_ANOMALY.RECEIVED_AV, receivedAv)
                .execute();
    }

    /**
     * Checks whether a NIC anomaly of the same flight and direction
     * (upgrade/downgrade) already exists in another file, within
     * {@link #ANOMALY_DEDUP_DOWNGRADE_SECONDS} of the given timestamp.
     *
     * @param flightId the flight to check
     * @param ts the timestamp to match nearby anomalies against
     * @param isDowngrade whether the anomaly to check for is a downgrade (vs. an upgrade)
     * @return {@code true} if a matching anomaly already exists
     */
    private boolean nicAnomalyExists(long flightId, OffsetDateTime ts, boolean isDowngrade) {
        return Database.ctx.fetchExists(
                Database.ctx.selectOne()
                        .from(NIC_ANOMALY)
                        .join(OPERATIONAL_STATUS)
                        .on(OPERATIONAL_STATUS.ID.eq(NIC_ANOMALY.OPERATIONAL_STATUS_ID))
                        .where(NIC_ANOMALY.FLIGHT_ID.eq(flightId))
                        .and(NIC_ANOMALY.FILE_ID.notEqual(this.fileId))
                        .and(NIC_ANOMALY.IS_DOWNGRADE.eq(isDowngrade))
                        .and(OPERATIONAL_STATUS.TS.between(
                                ts.minusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS),
                                ts.plusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS)
                        ))
        );
    }

    /**
     * Saves a NIC change anomaly if a change in NIC value was detected and
     * no matching anomaly already exists nearby (see {@link #nicAnomalyExists}).
     *
     * @param flightId the ID of the flight the anomaly belongs to
     * @param positionId the ID of the position message associated with the new NIC value
     * @param operationalStatusId the ID of the operational status associated with the new NIC value
     * @param previousNic the flight's previous NIC value
     * @param currentNic the newly observed NIC value
     * @param ts the timestamp used for deduplication matching
     */
    public void saveNicAnomaly(long flightId, long  positionId, long  operationalStatusId, short previousNic, short currentNic, OffsetDateTime ts) {
        boolean isDowngrade = currentNic < previousNic;

        if(nicAnomalyExists(flightId, ts, isDowngrade)) return;

        Database.ctx
                .insertInto(NIC_ANOMALY)
                .set(NIC_ANOMALY.POSITION_ID, positionId)
                .set(NIC_ANOMALY.OPERATIONAL_STATUS_ID, operationalStatusId)
                .set(NIC_ANOMALY.PREVIOUS_NIC, previousNic)
                .set(NIC_ANOMALY.NIC, currentNic)
                .set(NIC_ANOMALY.IS_DOWNGRADE, isDowngrade)
                .set(NIC_ANOMALY.FLIGHT_ID, flightId)
                .set(NIC_ANOMALY.FILE_ID, this.fileId)
                .execute();
    }

    /**
     * Checks whether a SIL anomaly of the same flight and direction
     * (upgrade/downgrade) already exists in another file, within
     * {@link #ANOMALY_DEDUP_DOWNGRADE_SECONDS} of the given timestamp.
     *
     * @param flightId the flight to check
     * @param ts the timestamp to match nearby anomalies against
     * @param isDowngrade whether the anomaly to check for is a downgrade (vs. an upgrade)
     * @return {@code true} if a matching anomaly already exists
     */
    private boolean silAnomalyExists(long flightId, OffsetDateTime ts, boolean isDowngrade) {
        return Database.ctx.fetchExists(
                Database.ctx.selectOne()
                        .from(SIL_ANOMALY)
                        .join(OPERATIONAL_STATUS)
                        .on(OPERATIONAL_STATUS.ID.eq(SIL_ANOMALY.OPERATIONAL_STATUS_ID))
                        .where(SIL_ANOMALY.FLIGHT_ID.eq(flightId))
                        .and(SIL_ANOMALY.FILE_ID.notEqual(this.fileId))
                        .and(SIL_ANOMALY.IS_DOWNGRADE.eq(isDowngrade))
                        .and(OPERATIONAL_STATUS.TS.between(
                                ts.minusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS),
                                ts.plusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS)
                        ))
        );
    }

    /**
     * Saves a SIL change anomaly if a change in SIL value was detected and
     * no matching anomaly already exists nearby (see {@link #silAnomalyExists}).
     *
     * @param flightId the ID of the flight the anomaly belongs to
     * @param operationalStatusId the ID of the operational status message the SIL change was observed on
     * @param previousSil the flight's previous SIL value
     * @param currentSil the newly observed SIL value
     * @param ts the timestamp used for deduplication matching
     */
    public void saveSilAnomaly(long flightId, long  operationalStatusId, short previousSil, short currentSil, OffsetDateTime ts) {
        boolean isDowngrade = currentSil < previousSil;

        if(silAnomalyExists(flightId, ts, isDowngrade)) return;

        Database.ctx
                .insertInto(SIL_ANOMALY)
                .set(SIL_ANOMALY.OPERATIONAL_STATUS_ID, operationalStatusId)
                .set(SIL_ANOMALY.PREVIOUS_SIL, previousSil)
                .set(SIL_ANOMALY.SIL, currentSil)
                .set(SIL_ANOMALY.IS_DOWNGRADE, isDowngrade)
                .set(SIL_ANOMALY.FLIGHT_ID, flightId)
                .set(SIL_ANOMALY.FILE_ID, this.fileId)
                .execute();
    }

    /**
     * Checks whether a NAC-p anomaly of the same flight and direction
     * (upgrade/downgrade) already exists in another file, within
     * {@link #ANOMALY_DEDUP_DOWNGRADE_SECONDS} of the given timestamp.
     *
     * @param flightId    the flight to check
     * @param ts          the timestamp to match nearby anomalies against
     * @param isDowngrade whether the anomaly to check for is a downgrade (vs. an upgrade)
     * @return {@code true} if a matching anomaly already exists
     */
    private boolean nacpAnomalyExists(long flightId, OffsetDateTime ts, boolean isDowngrade) {
        return Database.ctx.fetchExists(
                Database.ctx.selectOne()
                        .from(NAC_SUP_P_ANOMALY)
                        .join(OPERATIONAL_STATUS)
                        .on(OPERATIONAL_STATUS.ID.eq(NAC_SUP_P_ANOMALY.OPERATIONAL_STATUS_ID))
                        .where(NAC_SUP_P_ANOMALY.FLIGHT_ID.eq(flightId))
                        .and(NAC_SUP_P_ANOMALY.FILE_ID.notEqual(this.fileId))
                        .and(NAC_SUP_P_ANOMALY.IS_DOWNGRADE.eq(isDowngrade))
                        .and(OPERATIONAL_STATUS.TS.between(
                                ts.minusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS),
                                ts.plusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS)
                        ))
        );
    }

    /**
     * Saves a NAC-p change anomaly if a change in NAC-p value was detected
     * and no matching anomaly already exists nearby (see {@link #nacpAnomalyExists}).
     *
     * @param flightId            the ID of the flight the anomaly belongs to
     * @param operationalStatusId the ID of the operational status message the NAC-p change was observed on
     * @param previousNacSupP     the flight's previous NAC-p value
     * @param currentNacSupP      the newly observed NAC-p value
     * @param ts                  the timestamp used for deduplication matching
     */

    public void saveNacSupPAnomaly(long flightId, long  operationalStatusId, short previousNacSupP, short currentNacSupP, OffsetDateTime ts) {
        boolean isDowngrade = currentNacSupP < previousNacSupP;

        if(nacpAnomalyExists(flightId, ts, isDowngrade)) return;

        Database.ctx
                .insertInto(NAC_SUP_P_ANOMALY)
                .set(NAC_SUP_P_ANOMALY.OPERATIONAL_STATUS_ID, operationalStatusId)
                .set(NAC_SUP_P_ANOMALY.PREVIOUS_NAC_SUP_P, previousNacSupP)
                .set(NAC_SUP_P_ANOMALY.NAC_SUP_P, currentNacSupP)
                .set(NAC_SUP_P_ANOMALY.IS_DOWNGRADE, isDowngrade)
                .set(NAC_SUP_P_ANOMALY.FLIGHT_ID, flightId)
                .set(NAC_SUP_P_ANOMALY.FILE_ID, this.fileId)
                .execute();
    }

    /**
     * Checks whether a NAC-v anomaly of the same flight and direction
     * (upgrade/downgrade) already exists in another file, within
     * {@link #ANOMALY_DEDUP_DOWNGRADE_SECONDS} of the given timestamp.
     *
     * @param flightId    the flight to check
     * @param ts          the timestamp to match nearby anomalies against
     * @param isDowngrade whether the anomaly to check for is a downgrade (vs. an upgrade)
     * @return {@code true} if a matching anomaly already exists
     */
    private boolean nacvAnomalyExists(long flightId, OffsetDateTime ts, boolean isDowngrade) {
        return Database.ctx.fetchExists(
                Database.ctx.selectOne()
                        .from(NAC_SUP_V_ANOMALY)
                        .join(AIRBORNE_VELOCITY)
                        .on(AIRBORNE_VELOCITY.ID.eq(NAC_SUP_V_ANOMALY.AIRBORNE_VELOCITY_ID))
                        .where(NAC_SUP_V_ANOMALY.FLIGHT_ID.eq(flightId))
                        .and(NAC_SUP_V_ANOMALY.FILE_ID.notEqual(this.fileId))
                        .and(NAC_SUP_V_ANOMALY.IS_DOWNGRADE.eq(isDowngrade))
                        .and(AIRBORNE_VELOCITY.TS.between(
                                ts.minusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS),
                                ts.plusSeconds(ANOMALY_DEDUP_DOWNGRADE_SECONDS)
                        ))
        );
    }

    /**
     * Saves a NAC-v change anomaly if a change in NAC-v value was detected
     * and no matching anomaly already exists nearby (see {@link #nacvAnomalyExists}).
     *
     * <p>NAC-v values above 4 are outside the meaningful range for this
     * comparison and are ignored entirely, so no anomaly is recorded when
     * either the previous or current value exceeds it.
     *
     * @param flightId          the ID of the flight the anomaly belongs to
     * @param airborneVelocityId the ID of the airborne velocity message the NAC-v change was observed on
     * @param previousNacSupV   the flight's previous NAC-v value
     * @param currentNacSupV    the newly observed NAC-v value
     * @param ts                the timestamp used for deduplication matching
     */
    public void saveNacSupVAnomaly(long flightId, long  airborneVelocityId,  short previousNacSupV, short currentNacSupV, OffsetDateTime ts) {
        if (previousNacSupV > 4 || currentNacSupV > 4) return;

        boolean isDowngrade = currentNacSupV < previousNacSupV;

        if(nacvAnomalyExists(flightId, ts, isDowngrade)) return;

        Database.ctx
                .insertInto(NAC_SUP_V_ANOMALY)
                .set(NAC_SUP_V_ANOMALY.AIRBORNE_VELOCITY_ID, airborneVelocityId)
                .set(NAC_SUP_V_ANOMALY.PREVIOUS_NAC_SUP_V, previousNacSupV)
                .set(NAC_SUP_V_ANOMALY.NAC_SUP_V, currentNacSupV)
                .set(NAC_SUP_V_ANOMALY.IS_DOWNGRADE, isDowngrade)
                .set(NAC_SUP_V_ANOMALY.FLIGHT_ID, flightId)
                .set(NAC_SUP_V_ANOMALY.FILE_ID, this.fileId)
                .execute();
    }

    /**
     * Removes position gap anomalies that are likely false positives caused
     * by a single sensor's processing gap on the position message.
     * Due to unknown factors, some sensors have stopped processing position
     * messages at some point. This is discarded as jamming, because the airborne
     * velocity message transmission is constant, which also depends on GNSS.
     * The quality indicators also show a constant healthy value across the gap,
     * which is a strong indication that the gap is due to the sensor malfunction.
     *
     * <p>For each recorded position gap, this checks whether another file
     * (i.e. another sensor) has enough positions for the same flight
     * within the same time span to account for more than half of the expected
     * messages (assuming roughly one expected position per second of gap). If so, the gap
     * is attributed to the original sensor's own coverage limitation rather than jamming,
     * and the anomaly is deleted.
     *
     * <p>Intended to be run as a global post-processing pass after ingestion
     * and per-file detection have completed, since it needs visibility
     * across all files to find a corroborating sensor for each gap.
     */
    public static void filterFalsePositionGaps() {
        List<PositionGapAnomalyRecord> gaps = Database.ctx
                .selectFrom(POSITION_GAP_ANOMALY)
                .fetch();

        for (PositionGapAnomalyRecord gap : gaps) {
            OffsetDateTime previousTs = Database.ctx
                    .select(POSITION.TS)
                    .from(POSITION)
                    .where(POSITION.ID.eq(gap.getPreviousPositionId()))
                    .fetchOneInto(OffsetDateTime.class);

            OffsetDateTime lastTs = Database.ctx
                    .select(POSITION.TS)
                    .from(POSITION)
                    .where(POSITION.ID.eq(gap.getAfterPositionId()))
                    .fetchOneInto(OffsetDateTime.class);

            if (previousTs == null || lastTs == null) continue;

            long gapSeconds = ChronoUnit.SECONDS.between(previousTs, lastTs);
            long expectedPositions = gapSeconds;

            Record2<Long, Integer> bestSensor = Database.ctx
                    .select(POSITION.FILE_ID, DSL.count().as("pos_count"))
                    .from(POSITION)
                    .where(POSITION.FLIGHT_ID.eq(gap.getFlightId()))
                    .and(POSITION.FILE_ID.notEqual(gap.getFileId()))
                    .and(POSITION.TS.greaterThan(previousTs))
                    .and(POSITION.TS.lessThan(lastTs))
                    .groupBy(POSITION.FILE_ID)
                    .orderBy(DSL.count().desc())
                    .limit(1)
                    .fetchOne();

            if (bestSensor == null) continue;

            int positionsFromBestSensor = bestSensor.value2();

            if (positionsFromBestSensor > expectedPositions / 2) {
                Database.ctx
                        .deleteFrom(POSITION_GAP_ANOMALY)
                        .where(POSITION_GAP_ANOMALY.ID.eq(gap.getId()))
                        .execute();
            }
        }
    }
}
