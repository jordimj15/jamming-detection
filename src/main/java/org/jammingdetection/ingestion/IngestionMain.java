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
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.time.StopWatch;


public class IngestionMain {
    public static void main(String[] args) throws Exception{
        StopWatch stopWatch = new StopWatch();
        Decoder decoder = new Decoder();
        Preprocessor preprocessor = new Preprocessor();
        FlightService flightService = new FlightService();
        MessageService messageService = new MessageService();
        FileService fileService = new FileService();

        Path filePath = Path.of("data/12_1808670454.txt.gz");
        String fileName = filePath.getFileName().toString();
        String[] fileNameParts = fileName.split("_");

        short hour = Short.parseShort(fileNameParts[0]);
        long sensorSerial = Long.parseLong(fileNameParts[1].replace(".txt.gz", ""));

        AdsbFile adsbFile = fileService.findExisting(hour, sensorSerial);
        if(adsbFile != null) {
            fileService.deleteExistingData(adsbFile.getId());
        } else {
            adsbFile = fileService.create(hour, sensorSerial);
        }

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
            fileService.finalizeFile(adsbFile.getId(),  msgCount, stopWatch.getTime() / 1000);
            Database.close();
        }
        catch (Exception e){
            System.err.println("Failed to read file: " + filePath);
            e.printStackTrace();
        }
    }
}
