package org.jammingdetection.detection.service;

import org.jammingdetection.config.Config;
import org.jammingdetection.config.Database;
import org.jammingdetection.generated.detection.tables.records.PositionGapAnomalyRecord;

import java.time.OffsetDateTime;

import static org.jammingdetection.generated.detection.Tables.*;
import static org.jammingdetection.generated.ingestion.Tables.*;

public class AnomalyService {

    private static final long ANOMALY_DEDUP_POSITION_SECONDS = Config.getLong("detection.anomaly.dedup.position.seconds");
    private static final long ANOMALY_DEDUP_DOWNGRADE_SECONDS = Config.getLong("detection.anomaly.dedup.downgrade.seconds");

    private final long fileId;

    public AnomalyService(long fileId) {
        this.fileId = fileId;
    }

    public void savePositionGapAnomaly(long flightId, long previousMsgId, long lastMsgId, int receivedOs, int receivedAv, OffsetDateTime ts) {
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
                .fetchOneInto(PositionGapAnomalyRecord.class);

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

    public void saveNacSupVAnomaly(long flightId, long  airborneVelocityId,  short previousNacSupV, short currentNacSupV, OffsetDateTime ts) {
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
}
