package org.jammingdetection.detection;

import org.jammingdetection.detection.service.MessageFetchService;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;
import org.jammingdetection.generated.ingestion.tables.records.PositionRecord;

import java.util.List;
import java.util.Map;

public class DetectionMain {
    public static void main(String[] args) {
        long fileId = 12;
        MessageFetchService fetchService = new MessageFetchService();

        Map<Long, List<PositionRecord>> positions = fetchService.fetchPositions(fileId);
        Map<Long, List<OperationalStatusRecord>> opStatuses = fetchService.fetchOperationalStatus(fileId);
        Map<Long, List<AirborneVelocityRecord>> velocities = fetchService.fetchAirborneVelocity(fileId);

        for (long flightId: positions.keySet()) {

        }
    }
}
