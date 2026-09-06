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

/**
 * Entry point for running anomaly detection on a single ingested ADS-B file.
 *
 * <p>For each flight present in the file, this class:
 * <ol>
 *   <li>Fetches the flight's Position, Operational Status, and Airborne
 *       Velocity messages for the current file.</li>
 *   <li>Fetches the flight's single most recent message of each type from
 *       the previous file. This is important to keep consistency between
 *       files in the downgrade/upgrade detection methods. And to keep
 *       consistency in the detection of position gaps.
 *       All the previous Operational Status messages and Airborne Velocity
 *       messages are retrieved to keep consistency in the position gap
 *       detection method.
 *   <li>Runs {@link PositionGapDetector#detectPositionGaps} and
 *       {@link DowngradeDetector#detectSilNacpNicDowngrade}/
 *       {@link DowngradeDetector#detectNacvDowngrade} for the flight.</li>
 * </ol>
 *
 * <p>Once all flights have been processed, the total detection time is
 * recorded on the file's {@code AdsbFile} record.
 */
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

            // Look up each message type's most recent record for this
            // flight in the previous file, to bridge detection across the
            // file boundary.
            PositionRecord prevPosition = currentPositions.isEmpty() ? null :
                    fetchService.fetchPreviousPosition(currentPositions.getFirst());
            OperationalStatusRecord prevOpStatus = currentOpStatuses.isEmpty() ? null :
                    fetchService.fetchPreviousOperationalStatus(currentOpStatuses.getFirst());
            AirborneVelocityRecord prevVelocity = currentVelocities.isEmpty() ? null :
                    fetchService.fetchPreviousAirborneVelocity(currentVelocities.getFirst());

            List<OperationalStatusRecord> allPreviousOpStatuses = new ArrayList<>();
            List<AirborneVelocityRecord> allPreviousVelocities = new ArrayList<>();

            if (prevPosition != null) {
                // All previous-file messages within the boundary gap are
                // needed by PositionGapDetector to keep OS/AV counts
                // consistent across the file boundary.
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