package org.jammingdetection.service;

import org.jammingdetection.config.Database;
import org.jammingdetection.generated.tables.records.FlightRecord;
import org.jammingdetection.model.AirborneVelocityMessage;
import org.jammingdetection.model.Flight;
import modes.AdsbMessage;
import org.jammingdetection.model.OperationalStatusMessage;
import org.jammingdetection.model.PositionMessage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jammingdetection.generated.Tables.FLIGHT;

public class FlightService {
    private final Map<String, Map<String, Flight>> flightCache = new HashMap<>();
    private final long maxFlightTime = 45 * 60 * 1000;

    public Flight getOrCreate(AdsbMessage decodedMessage) {
        String icaoAddress = decodedMessage.getIcaoAddress();
        String callsign = decodedMessage.getCallsign();
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
}
