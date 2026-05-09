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

public class FlightService {
    private final Map<String, Map<String, Flight>> flightCache = new HashMap<>();
    private final long maxFlightTime = Config.getLong("ingestion.max.flight.time");

    public Flight getOrCreate(AdsbMessage decodedMessage) {
        String icaoAddress = decodedMessage.getIcaoAddress();
        String callsign = decodedMessage.getCallsign();
        String aircraftCategory = decodedMessage.getAircraftCategory();
        long timeStamp = decodedMessage.getTimeStamp();

        flightCache.putIfAbsent(decodedMessage.getIcaoAddress(), new HashMap<>());

        Flight flight = flightCache.get(icaoAddress).get(callsign);

        if (flight == null) {
            flight = findInDatabase(icaoAddress, callsign, timeStamp);

            if (flight == null){
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
        flight.setLastMsgTs(Instant.ofEpochMilli(timeStamp));

        return flight;
    }

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

    private OffsetDateTime toOffsetDateTime(long timestamp) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneOffset.UTC
        );
    }

    private Flight toFlight(FlightRecord record) {
        Flight flight = new Flight();
        flight.setId(record.getId());
        flight.setIcaoAddress(record.getIcaoaddress());
        flight.setCallsign(record.getCallsign());
        flight.setFirstMsgTs(record.getFirstMsgTs().toInstant());
        flight.setLastMsgTs(record.getLastMsgTs().toInstant());
        return flight;
    }

    public void flushCache() {
        flightCache.values().forEach(callsignMap ->
                callsignMap.values().forEach(flight ->
                        Database.ctx
                                .update(FLIGHT)
                                .set(FLIGHT.LAST_MSG_TS,
                                        flight.getLastMsgTs().atOffset(ZoneOffset.UTC))
                                .where(FLIGHT.ID.eq(flight.getId()))
                                .execute()
                )
        );
        flightCache.clear();
    }
}
