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

/**
 * Detects downgrade (and upgrade) anomalies in ADS-B quality indicators:
 * NACp, SIL, NACv, and NIC.
 *
 * <p>For indicators transmitted explicitly on a message (NACp and SIL on
 * Operational Status, NACv on Airborne Velocity), the detection algorithm compares each
 * new value against a sliding window of recent values for that flight,
 * rather than only the previous one. This is done to prevent a downgrade
 * that occurs gradually across several samples, too small to trigger a
 * threshold on any single consecutive sample, from going undetected. When a
 * change exceeding the configured threshold is found relative to the
 * window's maximum, an anomaly is saved and the window is reset so
 * subsequent comparisons start fresh from the new value.
 *
 * <p>NIC is not transmitted directly, it must be computed by pairing an
 * Operational Status message (which carries NICsupA, and maybe NICsupC) with
 * a nearby Position message (which carries the type code and maybe the NICsupB),
 * following the DO-260B NIC decoding table. For each Operational Status
 * message, the next Position message within {@link #MAX_NIC_TIME} is used
 * to compute the NIC. Positions further away are considered too stale to
 * pair reliably, and are therefore skipped. The same sliding-window comparison used
 * for NACp/SIL is then applied to the computed NIC sequence.
 */
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

    /**
     * Scans a flight's Operational Status messages for SIL and NAC-p
     * downgrades, and pairs each Operational Status with a nearby Position message
     * to compute and scan for NIC downgrades.
     *
     * <p>NACp and SIL are compared directly against their respective
     * sliding windows for every Operational Status message. For NIC, each
     * Operational Status message is paired with the next Position message
     * (by timestamp) as long as it falls within {@link #MAX_NIC_TIME}. If a
     * valid NIC value can be computed for the pair (see
     * {@link #computeNIC}), it is compared against the NIC window in the
     * same way.
     *
     * @param opStatuses the flight's Operational Status messages, ordered by timestamp
     * @param positions the flight's Position messages, ordered by timestamp
     */
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

            // Advance to and pair with the next Position message after this
            // Operational Status message, if it's recent enough to be a
            // reliable pairing for NIC computation.
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

    /**
     * Scans a flight's Airborne Velocity messages for NACv downgrades,
     * using the same sliding-window comparison as {@link #detectSilNacpNicDowngrade}.
     *
     * @param velocities the flight's Airborne Velocity messages, sorted by timestamp
     */
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

    /**
     * Appends a value to a sliding window, removing the oldest entry once
     * the window exceeds its configured size.
     *
     * @param window the window to update
     * @param value the new value to add
     * @param maxSize the maximum number of values the window may hold
     */
    private void addToWindow(Deque<Short> window, short value, int maxSize) {
        window.addLast(value);
        if (window.size() > maxSize) window.pollFirst();
    }

    /**
     * Computes the NIC (Navigation Integrity Category) value for a paired
     * Position/Operational Status message, as the DO-260B indicates.
     *
     * <p>NIC is only computable when both messages correspond to the same flight state:
     * Surface-Surface (using the Type Code, NICsupA and NICsupC)
     * or Airborne-Airborne (using the Type Code, NIC-sup-A and NIC-sup-B).
     *
     * @param position the paired Position message, providing the type code and, if airborne, NIC-sup-B
     * @param operationalStatus the paired Operational Status message, providing NIC-sup-A and, if surface, NIC-sup-C
     * @return the decoded NIC value, or {@code null} if the pair does not
     * resolve to a defined NIC (unsupported type code, or an invalid combination of available NIC supplement fields)
     */
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
