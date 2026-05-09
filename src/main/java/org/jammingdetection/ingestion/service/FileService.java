package org.jammingdetection.ingestion.service;

import org.jammingdetection.config.Database;
import org.jammingdetection.generated.ingestion.tables.records.AdsbFileRecord;
import org.jammingdetection.ingestion.model.AdsbFile;

import static org.jammingdetection.generated.ingestion.Tables.*;

public class FileService {
    public AdsbFile create (short hour, long sensorSerial) {
        AdsbFileRecord record = Database.ctx
                .insertInto(ADSB_FILE)
                .set(ADSB_FILE.HOUR, hour)
                .set(ADSB_FILE.SENSOR_SERIAL, sensorSerial)
                .set(ADSB_FILE.TOTAL_MSG_COUNT, 0)
                .set(ADSB_FILE.TIME_TO_DECODE, 0L)
                .returning()
                .fetchOne();

        AdsbFile file = new AdsbFile(record);
        return file;
    }

    public AdsbFile findExisting(short hour, long sensorSerial) {
        AdsbFileRecord record = Database.ctx
                .selectFrom(ADSB_FILE)
                .where(ADSB_FILE.HOUR.eq(hour))
                .and(ADSB_FILE.SENSOR_SERIAL.eq(sensorSerial))
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
    }


    public void finalizeFile(long fileId, int msgCount, long timeToDecode) {
        Database.ctx
                .update(ADSB_FILE)
                .set(ADSB_FILE.TOTAL_MSG_COUNT,    msgCount)
                .set(ADSB_FILE.TIME_TO_DECODE,  timeToDecode)
                .where(ADSB_FILE.ID.eq(fileId))
                .execute();
    }
}
