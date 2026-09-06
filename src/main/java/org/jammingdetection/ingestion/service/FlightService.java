package org.jammingdetection.ingestion.service;

import org.jammingdetection.config.Config;
import org.jammingdetection.config.Database;
import org.jammingdetection.generated.ingestion.tables.records.FlightRecord;
import org.jammingdetection.ingestion.model.Flight;

import modes.AdsbMessage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.jammingdetection.generated.ingestion.Tables.FLIGHT;

/**
 * Resolves incoming ADS-B messages to {@link Flight} entities, creating new
 * flights when needed and extending the time range of existing ones.
 *
 * <p>A "flight" is identified by the combination of ICAO address and
 * callsign, and is considered ongoing as long as consecutive messages for
 * that pair arrive within {@code ingestion.max.flight.time} of each other.
 * Messages further apart than that window are treated as a new flight,
 * even if the ICAO address and callsign repeat (e.g. across separate days).
 *
 * <p>An in-memory cache avoids repeated database lookups while a flight is
 * actively receiving messages; call {@link #flushCache()} to persist all
 * cached flights at the end of processing.
 */
public class FlightService {

    /** In-memory cache of active flights, keyed by ICAO address then callsign. */
    private final Map<String, Map<String, Flight>> flightCache = new HashMap<>();

    /** Maximum gap, in seconds, between messages for them to belong to the same flight. */
    private final long maxFlightTime = Config.getLong("ingestion.max.flight.time");

    /**
     * Resolves the {@link Flight} a decoded ADS-B message belongs to,
     * creating a new flight record if none exists yet.
     *
     * <p>Resolution order is: in-memory cache, then database lookup within
     * the flight time window, then creation of a new flight. Once resolved,
     * the flight's first/last message timestamps are extended to cover the
     * incoming message if needed.
     *
     * @param decodedMessage the decoded ADS-B message to attribute to a flight
     * @return the {@link Flight} this message belongs to
     */
    public Flight getOrCreate(AdsbMessage decodedMessage) {
        String icaoAddress = decodedMessage.getIcaoAddress();
        String callsign = decodedMessage.getCallsign();
        String aircraftCategory = decodedMessage.getAircraftCategory();

        // Message timestamps arrive in epoch micro, converted to epoch mili
        long timeStamp = decodedMessage.getTimeStamp() / 1000;
        Instant msgInstant = Instant.ofEpochMilli(timeStamp);

        flightCache.putIfAbsent(icaoAddress, new HashMap<>());
        Flight flight = flightCache.get(icaoAddress).get(callsign);

        if (flight == null) {
            // Not in cache: check the database in case this flight was
            // created in a previous run.
            flight = findInDatabase(icaoAddress, callsign, timeStamp);

            if (flight == null) {
                flight = new Flight(decodedMessage);
                OffsetDateTime ts = toOffsetDateTime(timeStamp);
                FlightRecord record = Database.ctx
                        .insertInto(FLIGHT)
                        .set(FLIGHT.ICAOADDRESS, icaoAddress)
                        .set(FLIGHT.CALLSIGN, callsign)
                        .set(FLIGHT.AIRCRAFT_CATEGORY, aircraftCategory)
                        .set(FLIGHT.FIRST_MSG_TS, ts)
                        .set(FLIGHT.LAST_MSG_TS, ts)
                        .returning()
                        .fetchOne();
                flight.setId(record.getId());
            }
            flightCache.get(icaoAddress).put(callsign, flight);
        }

        // Messages may arrive out of order, so extend whichever bound
        // (first or last) the new message falls outside.
        if (msgInstant.isAfter(flight.getLastMsgTs())) {
            flight.setLastMsgTs(msgInstant);
        } else if (msgInstant.isBefore(flight.getFirstMsgTs())) {
            flight.setFirstMsgTs(msgInstant);
        }
        return flight;
    }

    /**
     * Searches the database for an existing flight matching the given ICAO
     * address and callsign, whose message time range overlaps a window of
     * {@code maxFlightTime} seconds around the given timestamp.
     *
     * @param icaoAddress the aircraft's ICAO24 address
     * @param callsign the aircraft's callsign
     * @param timeStamp the epoch-second timestamp of the incoming message
     * @return the most recent matching {@link Flight}, or {@code null} if none exists
     */
    private Flight findInDatabase(String icaoAddress, String callsign, long timeStamp) {
        long upperBound = timeStamp + maxFlightTime;
        long lowerBound = timeStamp - maxFlightTime;

        OffsetDateTime lowerTs = toOffsetDateTime(lowerBound);
        OffsetDateTime upperTs = toOffsetDateTime(upperBound);

        FlightRecord record = Database.ctx
                .selectFrom(FLIGHT)
                .where(FLIGHT.ICAOADDRESS.eq(icaoAddress))
                .and(FLIGHT.CALLSIGN.eq(callsign))
                .and(FLIGHT.FIRST_MSG_TS.lessOrEqual(upperTs))
                .and(FLIGHT.LAST_MSG_TS.greaterOrEqual(lowerTs))
                .orderBy(FLIGHT.LAST_MSG_TS.desc())
                .limit(1)
                .fetchOne();

        if (record == null) return null;
        return toFlight(record);
    }

    /**
     * Converts an epoch-mili timestamp to a UTC {@link OffsetDateTime}.
     *
     * @param timestamp epoch-mili timestamp
     * @return the equivalent UTC {@link OffsetDateTime}
     */
    private OffsetDateTime toOffsetDateTime(long timestamp) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneOffset.UTC
        );
    }

    /**
     * Maps a {@link FlightRecord} database row to a {@link Flight} domain object.
     *
     * @param record the database record to convert
     * @return the corresponding {@link Flight}
     */
    private Flight toFlight(FlightRecord record) {
        Flight flight = new Flight();
        flight.setId(record.getId());
        flight.setIcaoAddress(record.getIcaoaddress());
        flight.setCallsign(record.getCallsign());
        flight.setFirstMsgTs(record.getFirstMsgTs().toInstant());
        flight.setLastMsgTs(record.getLastMsgTs().toInstant());
        return flight;
    }

    /**
     * Persists all cached flights' updated first/last message timestamps
     * to the database and clears the cache.
     *
     * <p>Intended to be called at the end of an ingestion run, after
     * all messages for the batch have been processed.
     */
    public void flushCache() {
        flightCache.values().forEach(callsignMap ->
                callsignMap.values().forEach(flight ->
                        Database.ctx
                                .update(FLIGHT)
                                .set(FLIGHT.FIRST_MSG_TS, flight.getFirstMsgTs().atOffset(ZoneOffset.UTC))
                                .set(FLIGHT.LAST_MSG_TS, flight.getLastMsgTs().atOffset(ZoneOffset.UTC))
                                .where(FLIGHT.ID.eq(flight.getId()))
                                .execute()
                )
        );
        flightCache.clear();
    }
}