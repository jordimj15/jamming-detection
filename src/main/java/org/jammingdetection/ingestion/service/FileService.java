package org.jammingdetection.ingestion.service;

import org.jammingdetection.config.Database;
import org.jammingdetection.generated.ingestion.tables.records.AdsbFileRecord;
import org.jammingdetection.ingestion.model.AdsbFile;

import java.time.LocalDate;

import static org.jammingdetection.generated.ingestion.Tables.*;
import static org.jammingdetection.generated.detection.Tables.*;

public class FileService {
    public AdsbFile create (short hour, long sensorSerial, LocalDate date) {
        AdsbFileRecord record = Database.ctx
                .insertInto(ADSB_FILE)
                .set(ADSB_FILE.HOUR, hour)
                .set(ADSB_FILE.SENSOR_SERIAL, sensorSerial)
                .set(ADSB_FILE.TOTAL_MSG_COUNT, 0)
                .set(ADSB_FILE.TIME_TO_DECODE, 0L)
                .set(ADSB_FILE.FILE_DATE, date)
                .returning()
                .fetchOne();

        AdsbFile file = new AdsbFile(record);
        return file;
    }

    public AdsbFile findExisting(short hour, long sensorSerial, LocalDate date) {
        AdsbFileRecord record = Database.ctx
                .selectFrom(ADSB_FILE)
                .where(ADSB_FILE.HOUR.eq(hour))
                .and(ADSB_FILE.SENSOR_SERIAL.eq(sensorSerial))
                .and(ADSB_FILE.FILE_DATE.eq(date))
                .fetchOne();

        if (record == null) return null;

        return new AdsbFile(record);
    }

    public void deleteExistingData(long fileId) {
        Database.ctx.deleteFrom(POSITION)
                .where(POSITION.FILE_ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(OPERATIONAL_STATUS)
                .where(OPERATIONAL_STATUS.FILE_ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(AIRBORNE_VELOCITY)
                .where(AIRBORNE_VELOCITY.FILE_ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(ADSB_FILE)
                .where(ADSB_FILE.ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(POSITION_GAP_ANOMALY)
                .where(POSITION_GAP_ANOMALY.FILE_ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(NIC_ANOMALY)
                .where(NIC_ANOMALY.FILE_ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(NAC_SUP_P_ANOMALY)
                .where(NAC_SUP_P_ANOMALY.FILE_ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(NAC_SUP_V_ANOMALY)
                .where(NAC_SUP_V_ANOMALY.FILE_ID.eq(fileId))
                .execute();

        Database.ctx.deleteFrom(SIL_ANOMALY)
                .where(SIL_ANOMALY.FILE_ID.eq(fileId))
                .execute();
    }


    public void finalizeFile(long fileId, int msgCount, long timeToDecode) {
        Database.ctx
                .update(ADSB_FILE)
                .set(ADSB_FILE.TOTAL_MSG_COUNT,    msgCount)
                .set(ADSB_FILE.TIME_TO_DECODE,  timeToDecode)
                .where(ADSB_FILE.ID.eq(fileId))
                .execute();
    }

    public void updateTimeToDetect(long fileId, long timeToDetect) {
        Database.ctx
                .update(ADSB_FILE)
                .set(ADSB_FILE.TIME_TO_DETECT, timeToDetect)
                .where(ADSB_FILE.ID.eq(fileId))
                .execute();
    }
}
