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

public class DowngradeDetector {
    private static final int MAX_NACP_DOWNGRADE = Integer.parseInt(Config.get("detection.nacp.downgrade"));
    private static final int NACP_WINDOW_SIZE = Integer.parseInt(Config.get("detection.nacp.window"));

    private static final int MAX_SIL_DOWNGRADE = Integer.parseInt(Config.get("detection.sil.downgrade"));
    private static final int SIL_WINDOW_SIZE = Integer.parseInt(Config.get("detection.sil.window"));

    private static final int MAX_NACV_DOWNGRADE = Integer.parseInt(Config.get("detection.nacv.downgrade"));
    private static final int NACV_WINDOW_SIZE = Integer.parseInt(Config.get("detection.nacv.window"));

    private static final int MAX_NIC_TIME = Integer.parseInt(Config.get("detection.nic.time"));
    private static final int NIC_WINDOW_SIZE = Integer.parseInt(Config.get("detection.nic.window"));
    private static final int MAX_NIC_DOWNGRADE = Integer.parseInt(Config.get("detection.nic.downgrade"));


    private final AnomalyService anomalyService;

    public DowngradeDetector (long fileId) {
        this.anomalyService = new AnomalyService(fileId);
    }

    public void detectSilNacpNicDowngrade(List<OperationalStatusRecord> opStatuses, List<PositionRecord> positions) {
        Deque<Short> nacpWindow = new ArrayDeque<>();
        Deque<Short> silWindow = new ArrayDeque<>();
        Deque<Short> nicWindow = new ArrayDeque<>();

        int currentPosIndex = 0;

        for(OperationalStatusRecord opStatus: opStatuses) {
            short currentNacp = opStatus.getNacSupP();
            short currentSil = opStatus.getSil();

            if(!nacpWindow.isEmpty()) {
                short nacpMaxValue = Collections.max(nacpWindow);
                if(Math.abs(nacpMaxValue - currentNacp) > MAX_NACP_DOWNGRADE)  {
                    anomalyService.saveNacSupPAnomaly(opStatus.getFlightId(), opStatus.getId(), nacpMaxValue, currentNacp, opStatus.getTs());
                    nacpWindow.clear();
                }
            }
            if(!silWindow.isEmpty()) {
                short silMaxValue = Collections.max(silWindow);
                if(Math.abs(silMaxValue - currentSil) > MAX_SIL_DOWNGRADE) {
                    anomalyService.saveSilAnomaly(opStatus.getFlightId(), opStatus.getId(), silMaxValue, currentSil, opStatus.getTs());
                    silWindow.clear();
                }
            }

            for(int i = currentPosIndex; i < positions.size(); i++) {
                PositionRecord currentPosition = positions.get(i);
                if(currentPosition.getTs().isAfter(opStatus.getTs())) {
                    if (ChronoUnit.MILLIS.between(opStatus.getTs(), currentPosition.getTs()) < MAX_NIC_TIME) {
                        Short currentNic = computeNIC(currentPosition, opStatus);
                        if(currentNic != null) {
                            if(!nicWindow.isEmpty()) {
                                short maxNic = Collections.max(nicWindow);
                                if (Math.abs(currentNic - maxNic) > MAX_NIC_DOWNGRADE) {
                                    anomalyService.saveNicAnomaly(currentPosition.getFlightId(), currentPosition.getId(), opStatus.getId(), maxNic, currentNic, opStatus.getTs());
                                    nicWindow.clear();
                                }
                            }
                            addToWindow(nicWindow, currentNic, NIC_WINDOW_SIZE);
                        }
                    }
                    currentPosIndex = i;
                    break;
                }
            }

            addToWindow(nacpWindow, currentNacp, NACP_WINDOW_SIZE);
            addToWindow(silWindow, currentSil, SIL_WINDOW_SIZE);
        }
    }

    public void detectNacvDowngrade(List<AirborneVelocityRecord> velocities) {
        Deque<Short> nacvWindow = new ArrayDeque<>();

        for(AirborneVelocityRecord velocity : velocities) {
            short currentNacV = velocity.getNacSubV();
            if(!nacvWindow.isEmpty()) {
                short nacvMaxValue = Collections.max(nacvWindow);
                if(Math.abs(nacvMaxValue - currentNacV) > MAX_NACV_DOWNGRADE) {
                    anomalyService.saveNacSupVAnomaly(velocity.getFlightId(), velocity.getId(), nacvMaxValue, currentNacV, velocity.getTs());
                    nacvWindow.clear();
                }
            }
            addToWindow(nacvWindow, currentNacV, NACV_WINDOW_SIZE);
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
