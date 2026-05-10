package org.jammingdetection.detection.service;

import org.jammingdetection.config.Database;

import static org.jammingdetection.generated.detection.Tables.*;

public class AnomalyService {

    public void savePositionGapAnomaly(long previousMsgId, long lastMsgId, int  receivedOs, int  receivedAv) {

        Database.ctx
                .insertInto(POSITION_GAP_ANOMALY)
                .set(POSITION_GAP_ANOMALY.PREVIOUS_POSITION_ID, previousMsgId)
                .set(POSITION_GAP_ANOMALY.AFTER_POSITION_ID, lastMsgId)
                .set(POSITION_GAP_ANOMALY.RECEIVED_OS, receivedOs)
                .set(POSITION_GAP_ANOMALY.RECEIVED_AV, receivedAv)
                .execute();
    }

    public void saveNicDowngrade(long  positionId, long  operationalStatusId, short previousNic, short currentNic) {

        Database.ctx
                .insertInto(NIC_DOWNGRADE)
                .set(NIC_DOWNGRADE.POSITION_ID, positionId)
                .set(NIC_DOWNGRADE.OPERATIONAL_STATUS_ID, operationalStatusId)
                .set(NIC_DOWNGRADE.PREVIOUS_NIC, previousNic)
                .set(NIC_DOWNGRADE.NIC, currentNic)
                .execute();
    }

    public void saveSilDowngrade(long  operationalStatusId, short previousSil, short currentSil) {

        Database.ctx
                .insertInto(SIL_DOWNGRADE)
                .set(SIL_DOWNGRADE.OPERATIONAL_STATUS_ID, operationalStatusId)
                .set(SIL_DOWNGRADE.PREVIOUS_SIL, previousSil)
                .set(SIL_DOWNGRADE.SIL, currentSil)
                .execute();
    }

    public void saveNacSupPDowngrade(long  operationalStatusId, short previousNacSupP, short currentNacSupP) {

        Database.ctx
                .insertInto(NAC_SUP_P_DOWNGRADE)
                .set(NAC_SUP_P_DOWNGRADE.OPERATIONAL_STATUS_ID, operationalStatusId)
                .set(NAC_SUP_P_DOWNGRADE.PREVIOUS_NAC_SUP_P, previousNacSupP)
                .set(NAC_SUP_P_DOWNGRADE.NAC_SUP_P, currentNacSupP)
                .execute();
    }

    public void saveNacSupVDowngrade(long  airborneVelocityId, short previousNacSupV, short currentNacSupV) {

        Database.ctx
                .insertInto(NAC_SUP_V_DOWNGRADE)
                .set(NAC_SUP_V_DOWNGRADE.AIRBORNE_VELOCITY_ID, airborneVelocityId)
                .set(NAC_SUP_V_DOWNGRADE.PREVIOUS_NAC_SUP_V, previousNacSupV)
                .set(NAC_SUP_V_DOWNGRADE.NAC_SUP_V, currentNacSupV)
                .execute();
    }
}
