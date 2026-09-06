package org.jammingdetection.ingestion.service;

import modes.computed.ComputedPosition;
import modes.raw.AirborneVelocity;
import modes.raw.OperationalStatus;

import org.jammingdetection.config.Config;
import org.jammingdetection.config.Database;
import org.jammingdetection.ingestion.model.AirborneVelocityMessage;
import org.jammingdetection.ingestion.model.OperationalStatusMessage;
import org.jammingdetection.ingestion.model.PositionMessage;

import org.jooq.BatchBindStep;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.jammingdetection.generated.ingestion.Tables.*;

/**
 * Buffers decoded ADS-B messages in memory and flushes them to the database
 * in batches, for position, operational status, and airborne velocity
 * message types.
 *
 * <p>Batching avoids one database transaction per decoded message, which
 * would be much slower given the amount of ADS-B messages per file. Each
 * message type is accumulated in its own list and is flushed automatically
 * once it reaches {@code ingestion.flush.threshold} entries, or manually
 * with {@link #flushAll()} (e.g. at the end of the processing of the file, so
 * that no message is lost).
 */
public class MessageService {

    /** Number of messages in memory of a given type that triggers an automatic flush. */
    private static final int FLUSH_THRESHOLD = Config.getInt("ingestion.flush.threshold");

    private final List<OperationalStatusMessage> operationalStatusMessageList = new ArrayList<>();
    private final List<PositionMessage> positionMessageList = new ArrayList<>();
    private final List<AirborneVelocityMessage> airborneVelocityMessageList = new ArrayList<>();

    /**
     * Buffers a decoded operational status message for later batch insertion,
     * flushing the buffer immediately if it has reached {@link #FLUSH_THRESHOLD}.
     *
     * @param decodedOperationalStatus the decoded operational status message
     * @param flightId the ID of the {@code Flight} this message belongs to
     * @param fileId the ID of the ADS-B file this message was read from
     */
    public void addToOperationalStatusList(OperationalStatus decodedOperationalStatus, long flightId, long fileId) {
        operationalStatusMessageList.add(new OperationalStatusMessage(decodedOperationalStatus, flightId, fileId));
        if (operationalStatusMessageList.size() >= FLUSH_THRESHOLD)
            flushOperationalStatus();
    }

    /**
     * Buffers a decoded position message for later batch insertion, flushing
     * the buffer immediately if it has reached {@link #FLUSH_THRESHOLD}.
     *
     * @param decodedComputedPosition the decoded position message
     * @param flightId the ID of the {@code Flight} this message belongs to
     * @param fileId the ID of the ADS-B file this message was read from
     */
    public void addToPositionList(ComputedPosition decodedComputedPosition, long flightId, long fileId) {
        positionMessageList.add(new PositionMessage(decodedComputedPosition, flightId, fileId));
        if (positionMessageList.size() >= FLUSH_THRESHOLD)
            flushPosition();
    }

    /**
     * Buffers a decoded airborne velocity message for later batch insertion,
     * flushing the buffer immediately if it has reached {@link #FLUSH_THRESHOLD}.
     *
     * @param decodedAirborneVelocity the decoded airborne velocity message
     * @param flightId the ID of the {@code Flight} this message belongs to
     * @param fileId the ID of the ADS-B file this message was read from
     */
    public void addToAirborneVelocity(AirborneVelocity decodedAirborneVelocity, long flightId, long fileId) {
        airborneVelocityMessageList.add(new AirborneVelocityMessage(decodedAirborneVelocity, flightId, fileId));
        if (airborneVelocityMessageList.size() >= FLUSH_THRESHOLD)
            flushAirborneVelocity();
    }

    /**
     * Flushes all three buffers (position, operational status, airborne
     * velocity) to the database, regardless of whether they have reached
     * {@link #FLUSH_THRESHOLD}.
     *
     * <p>Intended to be called once ingestion of a file completes, so that
     * any partially filled buffers are not left unwritten.
     */
    public void flushAll() {
        flushPosition();
        flushOperationalStatus();
        flushAirborneVelocity();
    }

    /**
     * Writes all buffered position messages to the database in a single
     * batch insert, then clears the buffer.
     */
    private void flushPosition() {
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

        for (PositionMessage positionMessage : positionMessageList) {
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

    /**
     * Writes all buffered operational status messages to the database in a
     * single batch insert, then clears the buffer.
     */
    private void flushOperationalStatus() {
        if (operationalStatusMessageList.isEmpty()) return;

        BatchBindStep batch = Database.ctx.batch(
                Database.ctx.insertInto(OPERATIONAL_STATUS,
                        OPERATIONAL_STATUS.FLIGHT_ID,
                        OPERATIONAL_STATUS.FILE_ID,
                        OPERATIONAL_STATUS.TS,
                        OPERATIONAL_STATUS.NIC_SUP_A,
                        OPERATIONAL_STATUS.NAC_SUP_P,
                        OPERATIONAL_STATUS.NIC_SUP_C,
                        OPERATIONAL_STATUS.SIL
                ).values((Long) null, null, null, null, null, null, null)
        );

        for (OperationalStatusMessage operationalStatusMessage : operationalStatusMessageList) {
            batch.bind(
                    operationalStatusMessage.getFlightId(),
                    operationalStatusMessage.getFileId(),
                    operationalStatusMessage.getTs().atOffset(ZoneOffset.UTC),
                    operationalStatusMessage.getNicSubA(),
                    operationalStatusMessage.getNacSubP(),
                    operationalStatusMessage.getNicSupC(),
                    operationalStatusMessage.getSil()
            );
        }

        batch.execute();
        operationalStatusMessageList.clear();
    }

    /**
     * Writes all buffered airborne velocity messages to the database in a
     * single batch insert, then clears the buffer.
     */
    private void flushAirborneVelocity() {
        if (airborneVelocityMessageList.isEmpty()) return;

        BatchBindStep batch = Database.ctx.batch(
                Database.ctx.insertInto(AIRBORNE_VELOCITY,
                        AIRBORNE_VELOCITY.FLIGHT_ID,
                        AIRBORNE_VELOCITY.FILE_ID,
                        AIRBORNE_VELOCITY.TS,
                        AIRBORNE_VELOCITY.NAC_SUB_V,
                        AIRBORNE_VELOCITY.GROUND_SPEED_EW,
                        AIRBORNE_VELOCITY.GROUND_SPEED_NS,
                        AIRBORNE_VELOCITY.AIRSPEED,
                        AIRBORNE_VELOCITY.MAGNETIC_HEADING
                ).values((Long) null, null, null, null, null, null, null, null)
        );

        for (AirborneVelocityMessage airborneVelocityMessage : airborneVelocityMessageList) {
            batch.bind(
                    airborneVelocityMessage.getFlightId(),
                    airborneVelocityMessage.getFileId(),
                    airborneVelocityMessage.getTs().atOffset(ZoneOffset.UTC),
                    airborneVelocityMessage.getNacSubV(),
                    airborneVelocityMessage.getGroundSpeedEW(),
                    airborneVelocityMessage.getGroundSpeedNS(),
                    airborneVelocityMessage.getAirSpeed(),
                    airborneVelocityMessage.getMagneticHeading()
            );
        }

        batch.execute();
        airborneVelocityMessageList.clear();
    }
}