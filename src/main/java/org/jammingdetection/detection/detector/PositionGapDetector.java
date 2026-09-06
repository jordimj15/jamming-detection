package org.jammingdetection.detection.detector;

import org.jammingdetection.config.Config;
import org.jammingdetection.detection.service.AnomalyService;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;
import org.jammingdetection.generated.ingestion.tables.records.PositionRecord;
import org.jooq.impl.QOM;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Detects position gap anomalies: intervals between two consecutive
 * position messages for a flight that exceed the maximum allowed
 * interval, while the aircraft continues to transmit at a normal rate the
 * Operational Status messages, suggesting the problem is not LOS but rather GNSS.
 *
 * <p>A gap in position reporting alone is not sufficient evidence of GNSS
 * jamming, since it can equally be caused by ordinary loss of ADS-B
 * coverage. To distinguish the two, the number of Operational Status messages received
 * during the gap is checked against the rate expected for this type:
 * if enough Operational Status messages arrived, the aircraft was clearly
 * still broadcasting, so the missing positions are attributed to a
 * GNSS specific event (likely jamming) rather than a general reception gap, and the
 * anomaly is flagged.
 *
 * <p>The number of Airborne Velocity messages received during the same
 * gap is also counted, but to characterize this message type behavior during
 * the event of GNSS jamming.
 */
public class PositionGapDetector {
    private static final int MAX_POS_TIME = Integer.parseInt(Config.get("detection.pos.time"));

    AnomalyService anomalyService;

    public PositionGapDetector(long fileId) {
        this.anomalyService = new AnomalyService(fileId);
    }

    /**
     * Scans a flight's position messages for gaps exceeding
     * {@link #MAX_POS_TIME} and flags them as anomalies when the aircraft's
     * Operational Status message rate during the gap confirms it was
     * actively transmitting.
     *
     * <p>Before scanning within the file, a sanity check is performed to ensure consistency across files.
     * If the flight's first position on the list is coming from another file, then it is checked if there can
     * be a possible position gap between files.
     * This check relies on {@code allPreviousOperationalStatus} and
     * {@code allPreviousAirborneVelocity} messages from the previous file
     * that fall within that boundary gap.
     *
     * @param positions the flight's position messages, ordered by timestamp.
     * The first entry may belong to the previous file
     * (see {@code MessageFetchService.fetchPreviousPosition})
     * @param opStatuses the flight's Operational Status messages for the current file, ordered by timestamp
     * @param velocities the flight's Airborne Velocity messages for the current file, ordered by timestamp
     * @param allPreviousOperationalStatus Operational Status messages from the previous file
     * falling within the boundary gap between the first message from a previous file and the first messages
     * from the current file.
     * (see {@code MessageFetchService.fetchAllPreviousOperationalStatus})
     * @param allPreviousAirborneVelocity  Airborne Velocity messages from the previous file
     * falling within the boundary described above.
     * (see {@code MessageFetchService.fetchAllPreviousAirborneVelocity})
     */
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
                // Advance currentOsIndex to the first OS message after the current
                // position, so each iteration starts scanning from where the
                // previous one left off instead of rescanning from the start.
                for (int j = currentOsIndex; j < opStatuses.size(); j++) {
                    if (opStatuses.get(j).getTs().isBefore(nextPosition.getTs())) {
                        osMessageCount++;
                    } else
                        break;
                }

                // Require at least 4 Operational Status messages per 20 seconds
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
