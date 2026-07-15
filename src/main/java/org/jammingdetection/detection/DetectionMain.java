package org.jammingdetection.detection;

import org.apache.commons.lang3.time.StopWatch;
import org.jammingdetection.detection.detector.DowngradeDetector;
import org.jammingdetection.detection.detector.PositionGapDetector;
import org.jammingdetection.detection.service.MessageFetchService;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;
import org.jammingdetection.generated.ingestion.tables.records.PositionRecord;
import org.jammingdetection.ingestion.service.FileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DetectionMain {
    public static void run(long fileId) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        MessageFetchService fetchService = new MessageFetchService();
        DowngradeDetector downgradeDetector = new DowngradeDetector(fileId);
        PositionGapDetector positionGapDetector = new PositionGapDetector(fileId);

        Map<Long, List<PositionRecord>> positions = fetchService.fetchPositions(fileId);
        Map<Long, List<OperationalStatusRecord>> opStatuses = fetchService.fetchOperationalStatus(fileId);
        Map<Long, List<AirborneVelocityRecord>> velocities = fetchService.fetchAirborneVelocity(fileId);


        for (long flightId : positions.keySet()) {
            List<PositionRecord> currentPositions = new ArrayList<>(positions.get(flightId));
            List<OperationalStatusRecord> currentOpStatuses = new ArrayList<>(opStatuses.getOrDefault(flightId, new ArrayList<>()));
            List<AirborneVelocityRecord> currentVelocities = new ArrayList<>(velocities.getOrDefault(flightId, new ArrayList<>()));

            PositionRecord prevPosition = currentPositions.isEmpty() ? null :
                    fetchService.fetchPreviousPosition(currentPositions.getFirst());
            OperationalStatusRecord prevOpStatus = currentOpStatuses.isEmpty() ? null :
                    fetchService.fetchPreviousOperationalStatus(currentOpStatuses.getFirst());
            AirborneVelocityRecord prevVelocity = currentVelocities.isEmpty() ? null :
                    fetchService.fetchPreviousAirborneVelocity(currentVelocities.getFirst());

            List<OperationalStatusRecord> allPreviousOpStatuses = new ArrayList<>();
            List<AirborneVelocityRecord> allPreviousVelocities = new ArrayList<>();

            if (prevPosition != null) {
                allPreviousOpStatuses = fetchService.fetchAllPreviousOperationalStatus(prevPosition, currentPositions.getFirst());
                allPreviousVelocities = fetchService.fetchAllPreviousAirborneVelocity(prevPosition, currentPositions.getFirst());
                currentPositions.addFirst(prevPosition);
            }
            if (prevOpStatus != null)   currentOpStatuses.addFirst(prevOpStatus);
            if (prevVelocity != null)   currentVelocities.addFirst(prevVelocity);

            positionGapDetector.detectPositionGaps(currentPositions, currentOpStatuses, currentVelocities, allPreviousOpStatuses, allPreviousVelocities);
            downgradeDetector.detectSilNacpNicDowngrade(currentOpStatuses, currentPositions);
            downgradeDetector.detectNacvDowngrade(currentVelocities);
        }
        stopWatch.stop();

        new FileService().updateTimeToDetect(fileId, stopWatch.getTime());
        System.out.println("Time to process DETECTION: " + stopWatch.getTime() + " ms");
    }
}