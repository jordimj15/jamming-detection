package org.jammingdetection.detection.detector;

import org.jammingdetection.config.Config;
import org.jammingdetection.detection.service.AnomalyService;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;
import org.jammingdetection.generated.ingestion.tables.records.PositionRecord;
import org.jooq.impl.QOM;

import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class PositionGapDetector {
    private static final int MAX_POS_TIME = Integer.parseInt(Config.get("detection.pos.time"));

    AnomalyService anomalyService;

    public PositionGapDetector(long fileId) {
        this.anomalyService = new AnomalyService(fileId);
    }

    public void detectPositionGaps(List<PositionRecord> positions, List<OperationalStatusRecord> opStatuses, List<AirborneVelocityRecord> velocities, List<OperationalStatusRecord> allPreviousOperationalStatus, List<AirborneVelocityRecord> allPreviousAirborneVelocity) {
        int currentOsIndex = 0;

        if(positions.size() > 2) {
            if (!positions.get(0).getFileId().equals(positions.get(1).getFileId())) {
                if (ChronoUnit.MILLIS.between(positions.get(0).getTs(), positions.get(1).getTs()) > MAX_POS_TIME) {
                    int osCount = allPreviousOperationalStatus.size() + (int) opStatuses.stream()
                            .filter(op -> op.getTs().isAfter(positions.get(0).getTs()) && op.getTs().isBefore(positions.get(1).getTs()))
                            .count();

                    if (osCount > (ChronoUnit.SECONDS.between(positions.get(0).getTs(), positions.get(1).getTs()) * 4) / 20) {
                        int avCount = allPreviousAirborneVelocity.size() + (int) velocities.stream()
                                .filter(v -> v.getTs().isBefore(positions.get(0).getTs()))
                                .count();
                        anomalyService.savePositionGapAnomaly(positions.get(0).getFlightId(), positions.get(0).getId(), positions.get(1).getId(), osCount, avCount, positions.get(0).getTs());
                    }
                }
            }
        }


        for (int i = 0; i < positions.size() - 1; i++) {
            PositionRecord currentPosition = positions.get(i);
            for (int j = currentOsIndex; j < opStatuses.size(); j++) {
                OperationalStatusRecord currentOperationalStatus = opStatuses.get(j);
                if (currentOperationalStatus.getTs().isAfter(currentPosition.getTs())) {
                    currentOsIndex = j;
                    break;
                }
            }
            PositionRecord nextPosition = positions.get(i + 1);
            if (ChronoUnit.MILLIS.between(currentPosition.getTs(), nextPosition.getTs()) > MAX_POS_TIME) {
                int osMessageCount = 0;
                for (int j = currentOsIndex; j < opStatuses.size(); j++) {
                    if (opStatuses.get(j).getTs().isBefore(nextPosition.getTs())) {
                        osMessageCount++;
                    } else
                        break;
                }

                if (osMessageCount > (ChronoUnit.SECONDS.between(currentPosition.getTs(), nextPosition.getTs()) * 4) / 20) {
                    int avMessageCount = (int) velocities.stream()
                            .filter(v -> v.getTs().isAfter(currentPosition.getTs()) && v.getTs().isBefore(nextPosition.getTs()))
                            .count();
                    anomalyService.savePositionGapAnomaly(currentPosition.getFlightId(), currentPosition.getId(), nextPosition.getId(), osMessageCount, avMessageCount, currentPosition.getTs());
                }
            }
        }
    }
}
