package org.jammingdetection.service;

import org.jammingdetection.config.Database;
import org.jammingdetection.generated.tables.records.AdsbFileRecord;
import org.jammingdetection.model.AdsbFile;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.jammingdetection.generated.Tables.ADSB_FILE;

public class FileService {
    public AdsbFile create (short hour, long sensorSerial, Instant firstMsgTs) {
        OffsetDateTime ts = firstMsgTs.atOffset(ZoneOffset.UTC);
        AdsbFileRecord record = Database.ctx
                .insertInto(ADSB_FILE)
                .set(ADSB_FILE.HOUR, hour)
                .set(ADSB_FILE.SENSOR_SERIAL, sensorSerial)
                .set(ADSB_FILE.TOTAL_MSG_COUNT, 0)
                .set(ADSB_FILE.TIME_TO_DECODE, (short)0)
                .returning()
                .fetchOne();

        AdsbFile file = new AdsbFile(record);
        return file;
    }

    public void finalizeFile(long fileId, int msgCount, short timeToDecode) {
        Database.ctx
                .update(ADSB_FILE)
                .set(ADSB_FILE.TOTAL_MSG_COUNT,    msgCount)
                .set(ADSB_FILE.TIME_TO_DECODE,  timeToDecode)
                .where(ADSB_FILE.ID.eq(fileId))
                .execute();
    }
}
