package org.jammingdetection.ingestion.model;

import modes.raw.OperationalStatus;
import modes.raw.SurfaceOperationalStatus;

import java.time.Instant;

/**
 * Adapts a decoded {@link OperationalStatus} message into the shape expected
 * by the {@code OPERATIONAL_STATUS} database table.
 *
 * <p>As with the other message model classes, this is a transition step
 * between decoding and transaction: the constructor takes a decoded
 * message plus the {@code flightId}/{@code fileId} it belongs to, and
 * extracts only the fields the database needs.
 *
 * <p>{@link OperationalStatus} covers both airborne and surface reports,
 * but {@code NIC-sup-C} is only reported by surface aircraft.
 */
public class OperationalStatusMessage {
    private long flightId;
    private long fileId;
    private Instant ts;
    private short nicSubA;
    private short nacSubP;
    private Short nicSupC;
    private short sil;

    /**
     * Builds a database ready message from a decoded operational status message.
     *
     * @param decodedOperationalStatus the decoded message, a {@link OperationalStatus}, airborne or surface
     * @param flightId the ID of the {@code Flight} this message belongs to
     * @param fileId the ID of the ADS-B file this message was read from
     */
    public OperationalStatusMessage(OperationalStatus decodedOperationalStatus, long flightId, long fileId) {
        this.flightId = flightId;
        this.fileId = fileId;
        this.ts = Instant.ofEpochMilli(decodedOperationalStatus.getTimeStamp() / 1000);
        this.nicSubA = (short) decodedOperationalStatus.getNicSupA();
        this.nacSubP = (short) decodedOperationalStatus.getNACp();

        // NIC-sup-C is only present on surface reports.
        this.nicSupC = (decodedOperationalStatus instanceof SurfaceOperationalStatus sos)
                ? (short) sos.getNICsupC()
                : null;
        this.sil = (short) decodedOperationalStatus.getSIL();
    }

    public void setFlightId(long flightId) {
        this.flightId = flightId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }

    public long getFlightId() {
        return flightId;
    }

    public long getFileId() {
        return fileId;
    }

    public Instant getTs() {
        return ts;
    }

    public short getNicSubA() {
        return nicSubA;
    }

    public short getNacSubP() {
        return nacSubP;
    }

    public Short getNicSupC() {
        return nicSupC;
    }

    public short getSil() {
        return sil;
    }
}