package org.jammingdetection.detection.detector;

import org.jammingdetection.config.Config;
import org.jammingdetection.detection.service.AnomalyService;
import org.jammingdetection.generated.ingestion.tables.records.AirborneVelocityRecord;
import org.jammingdetection.generated.ingestion.tables.records.OperationalStatusRecord;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class DowngradeDetector {
    private static final int MAX_NACP_DOWNGRADE = Integer.parseInt(Config.get("detection.nacp.downgrade"));
    private static final int MAX_SIL_DOWNGRADE = Integer.parseInt(Config.get("detection.sil.downgrade"));
    private static final int MAX_NACV_DOWNGRADE = Integer.parseInt(Config.get("detection.nacv.downgrade"));
    private static final int NACP_WINDOW_SIZE = Integer.parseInt(Config.get("detection.nacp.window"));
    private static final int SIL_WINDOW_SIZE = Integer.parseInt(Config.get("detection.sil.window"));
    private static final int NACV_WINDOW_SIZE = Integer.parseInt(Config.get("detection.nacv.window"));

    private AnomalyService anomalyService;

    public DowngradeDetector (long fileId) {
        this.anomalyService = new AnomalyService(fileId);
    }

    public void detectSilNacpDowngrade(List<OperationalStatusRecord> opStatuses) {
        Deque<Short> nacpWindow = new ArrayDeque<>();
        Deque<Short> silWindow = new ArrayDeque<>();

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
}
