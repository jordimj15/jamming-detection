package org.jammingdetection.detection.detector;

import org.jammingdetection.detection.service.AnomalyService;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class DowngradeDetector {
    private static final int MAX_NACP_DOWNGRADE = 4;
    private static final int MAX_SIL_DOWNGRADE = 2;
    private static final int MAX_NACV_DOWNGRADE = 2;
    private static final int NACP_WINDOW_SIZE = 5;
    private static final int SIL_WINDOW_SIZE = 5;
    private static final int NACV_WINDOW_SIZE = 25;

    AnomalyService anomalyService = new AnomalyService();

    public void detectSilNacpDowngrade(List<OperationalStatusRecord> opStatuses) {
        Deque<Short> nacpWindow = new ArrayDeque<>();
        Deque<Short> silWindow = new ArrayDeque<>();

        for(OperationalStatusRecord opStatus: opStatuses) {
            short currentNacp = opStatus.getNacSupP();
            short currentSil = opStatus.getSil();

            if(!nacpWindow.isEmpty()) {
                short nacpMaxValue = Collections.max(nacpWindow);
                if(Math.abs(nacpMaxValue - currentNacp) > MAX_NACP_DOWNGRADE)  {
                    //NACP ANOMALY
                }
            }
            if(!silWindow.isEmpty()) {
                short silMaxValue = Collections.max(silWindow);
                if(Math.abs(silMaxValue - currentSil) > MAX_SIL_DOWNGRADE) {
                    //SIL ANOMALY
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
                    //nacv anomaly
                }
            }
        }
    }

    private void addToWindow(Deque<Short> window, short value, int maxSize) {
        window.addLast(value);
        if (window.size() > maxSize) window.pollFirst();
    }
}
