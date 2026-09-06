package org.jammingdetection.ingestion.service;

import org.jammingdetection.config.Database;
import org.jammingdetection.generated.ingestion.tables.records.AdsbFileRecord;
import org.jammingdetection.ingestion.model.AdsbFile;

import java.time.LocalDate;

import static org.jammingdetection.generated.ingestion.Tables.*;
import static org.jammingdetection.generated.detection.Tables.*;

/**
 * Provides CRUD operations for ADS-B ingestion files.
 *
 * <p>An {@code AdsbFile} represents one hour of raw ADS-B messages received
 * from a single sensor. This service manages the lifecycle of the file:
 * creation on ingestion start, lookup for deduplication, cleanup of existing
 * data on reprocessing, and finalization once decoding completes.
 */
public class FileService {

    /**
     * Creates a new ADS-B file record for the given sensor, hour, and date.
     *
     * <p>Message count and decode time are initialized to zero and stated
     * later with {@link #finalizeFile(long, int, long)}.
     *
     * @param hour the UTC hour (0–23) this file covers
     * @param sensorSerial the serial number of the receiving sensor
     * @param date the calendar date of the file
     * @return the newly created {@link AdsbFile}
     */
    public AdsbFile create(short hour, long sensorSerial, LocalDate date) {
        AdsbFileRecord record = Database.ctx
                .insertInto(ADSB_FILE)
                .set(ADSB_FILE.HOUR, hour)
                .set(ADSB_FILE.SENSOR_SERIAL, sensorSerial)
                .set(ADSB_FILE.TOTAL_MSG_COUNT, 0)
                .set(ADSB_FILE.TIME_TO_DECODE, 0L)
                .set(ADSB_FILE.FILE_DATE, date)
                .returning()
                .fetchOne();
        return new AdsbFile(record);
    }

    /**
     * Looks up an existing ADS-B file matching the given sensor, hour, and date.
     *
     * <p>Used to avoid duplicating data from a file that has already been processed
     * for a given sensor/hour/date combination.
     *
     * @param hour the UTC hour (0–23) this file covers
     * @param sensorSerial the serial number of the receiving sensor
     * @param date the calendar date of the file
     * @return the matching {@link AdsbFile}, or {@code null} if none exists
     */
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

    /**
     * Deletes an ADS-B file and all data derived from it.
     *
     * <p>This includes position, operational status and velocity
     * records from the ingestion schema, as well as all anomaly detections
     * (Position Gap, NIC, NACp, NACv, SIL) associated with the file. Intended for use
     * when a file needs to be reprocessed.
     *
     * @param fileId the ID of the ADS-B file to delete
     */
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

    /**
     * Marks a file as fully processed by recording its final message
     * count and total time to decode.
     *
     * @param fileId the ID of the ADS-B file to update
     * @param msgCount the total number of ADS-B messages from the file
     * @param timeToDecode the total decoding time, in milliseconds
     */
    public void finalizeFile(long fileId, int msgCount, long timeToDecode) {
        Database.ctx
                .update(ADSB_FILE)
                .set(ADSB_FILE.TOTAL_MSG_COUNT, msgCount)
                .set(ADSB_FILE.TIME_TO_DECODE, timeToDecode)
                .where(ADSB_FILE.ID.eq(fileId))
                .execute();
    }

    /**
     * Records how long anomaly detection took for a given file.
     *
     * @param fileId the ID of the ADS-B file to update
     * @param timeToDetect the total anomaly detection time, in milliseconds
     */
    public void updateTimeToDetect(long fileId, long timeToDetect) {
        Database.ctx
                .update(ADSB_FILE)
                .set(ADSB_FILE.TIME_TO_DETECT, timeToDetect)
                .where(ADSB_FILE.ID.eq(fileId))
                .execute();
    }
}