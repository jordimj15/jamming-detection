package org.jammingdetection.ingestion;

import decoder.Decoder;
import modes.AdsbMessage;
import modes.computed.ComputedPosition;
import modes.raw.AirborneVelocity;
import modes.raw.OperationalStatus;

import org.jammingdetection.config.Database;
import org.jammingdetection.ingestion.model.AdsbFile;
import org.jammingdetection.ingestion.model.Flight;
import org.jammingdetection.ingestion.service.FileService;
import org.jammingdetection.ingestion.service.FlightService;
import org.jammingdetection.ingestion.service.MessageService;

import preprocessor.Preprocessor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.time.StopWatch;

/**
 * Entry point for ingesting an ADS-B message file into the database.
 *
 * <p>The ingestion pipeline for one file is as following:
 * <ol>
 *   <li>Check whether a file for this sensor + date has already been
 *       processed. If so, its data (positions, operational status,
 *       airborne velocity, and anomalies) will be deleted so that the file can
 *       be reprocessed cleanly.</li>
 *   <li>Create a new {@code AdsbFile} record for this ingestion run.</li>
 *   <li>Decode the file line by line. Each decoded message is decoded.
 *       linked to a {@code Flight} via {@link FlightService},
 *       and added to the right {@link MessageService} buffer based on
 *       its type (position, operational status, or airborne velocity).</li>
 *   <li>Once the file is fully read, flush all remaining buffered messages and
 *       update all flights updates to the database, and record final statistics
 *       (message count, decode time) on the {@code AdsbFile} record.</li>
 * </ol>
 */


public class IngestionMain {

    /**
     * Ingests a single ADS-B file, decoding all messages it contains.
     *
     * <p>The file name is expected to follow the {@code <hour>_<sensorSerial>.txt.gz}
     * naming convention, from which the sensor and hour are parsed.
     *
     * @param filePath the path to the gzip-compressed raw ADS-B message file
     * @param fileDate the calendar date this file corresponds to
     * @return the ID of the {@code AdsbFile} record created for this ingestion run
     * @throws Exception if the file name cannot be parsed or an unrecoverable I/O error occurs
     */
    public static long run(Path filePath, LocalDate fileDate) throws Exception {
        StopWatch stopWatch = new StopWatch();
        Decoder decoder = new Decoder();
        Preprocessor preprocessor = new Preprocessor();
        FlightService flightService = new FlightService();
        MessageService messageService = new MessageService();
        FileService fileService = new FileService();

        String fileName = filePath.getFileName().toString();
        String[] fileNameParts = fileName.split("_");

        short hour = Short.parseShort(fileNameParts[0]);
        long sensorSerial = Long.parseLong(fileNameParts[1].replace(".txt.gz", ""));

        // If this sensor + date combination was already ingested, remove its
        // derived data so it can be cleanly reprocessed.
        AdsbFile adsbFile = fileService.findExisting(hour, sensorSerial, fileDate);
        if(adsbFile != null) {
            fileService.deleteExistingData(adsbFile.getId());
        }
        adsbFile = fileService.create(hour, sensorSerial, fileDate);


        int msgCount = 0;
        GZIPInputStream gzip   = new GZIPInputStream(new FileInputStream(filePath.toFile()));
        BufferedReader  reader = new BufferedReader(new InputStreamReader(gzip));

        stopWatch.start();
        try(Stream<String> lines = reader.lines()){
            for (String line : (Iterable<String>) lines::iterator) {
                AdsbMessage decodedMessage = decoder.decode(line);
                if (decodedMessage != null) {
                    msgCount++;
                    List<AdsbMessage> processedMessages = preprocessor.preprocess(decodedMessage);
                    for(AdsbMessage message : processedMessages){
                        Flight flight = flightService.getOrCreate(message);
                        long timestamp = message.getTimeStamp() / 1000;

                        // Route each message to its type specific buffer. The
                        // flight's per type timestamp list also deduplicates
                        // messages that arrive faster than the expected
                        // reporting rate, so only new timestamps are persisted.
                        switch (message){
                            case ComputedPosition computedPosition -> {
                                if(flight.addPositionTimestampList(timestamp)){
                                    messageService.addToPositionList(computedPosition, flight.getId(), adsbFile.getId());
                                }
                            }
                            case OperationalStatus operationalStatus -> {
                                if(flight.addOperationalStatusTimestampList(timestamp)){
                                    messageService.addToOperationalStatusList(operationalStatus, flight.getId(), adsbFile.getId());
                                }
                            }
                            case AirborneVelocity airborneVelocity -> {
                                if(flight.addAirborneVelocityTimestampList(timestamp)){
                                    messageService.addToAirborneVelocity(airborneVelocity, flight.getId(), adsbFile.getId());
                                }
                            }
                            default -> {}
                        }
                    }
                }
            }
            messageService.flushAll();
            flightService.flushCache();
            stopWatch.stop();
            System.out.println("Time to process INGESTION: " + stopWatch.getTime() + " ms");
            fileService.finalizeFile(adsbFile.getId(),  msgCount, stopWatch.getTime());

        }
        catch (Exception e){
            System.err.println("Failed to read file: " + filePath);
            e.printStackTrace();
        }
        return adsbFile.getId();
    }
}
