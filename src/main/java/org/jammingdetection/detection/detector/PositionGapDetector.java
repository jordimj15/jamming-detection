package org.jammingdetection.detection.detector;

import org.jammingdetection.config.Config;
import org.jammingdetection.detection.service.AnomalyService;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;
import org.jammingdetection.generated.ingestion.tables.records.PositionRecord;

import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class PositionGapDetector {
    private static final int MAX_NIC_TIME = Integer.parseInt(Config.get("detection.nic.time"));
    private static final int NIC_WINDOW_SIZE = Integer.parseInt(Config.get("detection.nic.window"));
    private static final int  MAX_POS_TIME = Integer.parseInt(Config.get("detection.pos.time"));
    private static final int MAX_NIC_DOWNGRADE = Integer.parseInt(Config.get("detection.nic.downgrade"));

    AnomalyService anomalyService;

    public PositionGapDetector (long fileId) {
        this.anomalyService = new AnomalyService(fileId);
    }



    public void detectPositionGaps(List<PositionRecord> positions, List<OperationalStatusRecord> opStatuses, List<AirborneVelocityRecord> velocities) {
        int currentOsIndex = 0;
        Deque<Short> nicWindow = new ArrayDeque<>();

        for (int i = 0; i < positions.size(); i++) {
            PositionRecord currentPosition = positions.get(i);

            for (int j = currentOsIndex; j < opStatuses.size(); j++) {
                OperationalStatusRecord currentOperationalStatus = opStatuses.get(j);

                if (currentOperationalStatus.getTs().isAfter(currentPosition.getTs())) {
                    if (ChronoUnit.MILLIS.between(currentPosition.getTs(), currentOperationalStatus.getTs()) < MAX_NIC_TIME) {
                        Short currentNic = computeNIC(currentPosition, currentOperationalStatus);

                        if (currentNic != null) {
                            if (!nicWindow.isEmpty()) {
                                short maxNic = Collections.max(nicWindow);
                                if (Math.abs(currentNic - maxNic) > MAX_NIC_DOWNGRADE) {
                                    anomalyService.saveNicAnomaly(currentPosition.getFlightId(), currentPosition.getId(), currentOperationalStatus.getId(), maxNic, currentNic, currentOperationalStatus.getTs());
                                    nicWindow.clear();
                                }
                            }
                            addToWindow(nicWindow, currentNic, NIC_WINDOW_SIZE);
                        }
                    }
                    currentOsIndex = j;
                    break;
                }
            }
            if(i < positions.size() - 1) {
                PositionRecord nextPosition = positions.get(i + 1);

                if(ChronoUnit.MILLIS.between(currentPosition.getTs(), nextPosition.getTs()) > MAX_POS_TIME) {
                    int osMessageCount = 0;
                    for (int j = currentOsIndex; j < opStatuses.size(); j++) {
                        if (opStatuses.get(j).getTs().isBefore(nextPosition.getTs())){
                            osMessageCount ++;
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

    private void addToWindow(Deque<Short> window, short value, int maxSize) {
        window.addLast(value);
        if (window.size() > maxSize) window.pollFirst();
    }

    private Short computeNIC (PositionRecord position, OperationalStatusRecord operationalStatus){
        int tc = position.getTypeCode();

        Short a = operationalStatus.getNicSupA();
        Short b = position.getNicSupB();
        Short c = operationalStatus.getNicSupC();

        Boolean nicA = a == null ? null : a == 1;
        Boolean nicB = b == null ? null : b == 1;
        Boolean nicC = c == null ? null : c == 1;

        if(nicA != null && nicC != null && nicB == null) {
            switch(tc) {
                case 5:
                    if (!nicA && !nicC) return 11;
                    break;
                case 6:
                    if (!nicA && !nicC) return 10;
                    break;
                case 7:
                    if (nicA && !nicC) return 9;
                    if (!nicA && !nicC) return 8;
                    break;
                case 8:
                    if (nicA && nicC) return 7;
                    if (nicA) return 6;
                    if (nicC) return 6;
                    return 0;
            }
        }

        else if (nicA != null && nicB != null && nicC == null) {
            switch (tc) {
                case 9: case 20:
                    if(!nicA && !nicB) return 11;
                    break;
                case 10: case 21:
                    if(!nicA && !nicB) return 10;
                    break;
                case 11:
                    if(nicA && nicB) return 9;
                    if(!nicA && !nicB) return 8;
                    break;
                case 12:
                    if(!nicA && !nicB) return 7;
                    break;
                case 13:
                    if(!nicA && nicB) return 6;
                    if(!nicA) return 6;
                    if(nicB) return 6;
                    break;
                case 14:
                    if(!nicA && !nicB) return 5;
                    break;
                case 15:
                    if(!nicA && !nicB) return 4;
                    break;
                case 16:
                    if(nicA && nicB) return 3;
                    break;
                case 17:
                    if(!nicA && !nicB) return 1;
                    break;
                case 18: case 22:
                    if(!nicA && !nicB) return 0;
                    break;
            }
        } else return null;


        return null;
    }
}
