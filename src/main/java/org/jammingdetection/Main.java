package org.jammingdetection;

import decoder.Decoder;
import modes.AdsbMessage;
import modes.computed.ComputedPosition;
import modes.raw.AirborneVelocity;
import modes.raw.OperationalStatus;
import org.jammingdetection.config.Database;
import org.jammingdetection.model.AdsbFile;
import org.jammingdetection.model.Flight;
import org.jammingdetection.service.FileService;
import org.jammingdetection.service.FlightService;
import org.jammingdetection.service.MessageService;
import preprocessor.Preprocessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.StopWatch;


public class Main {
    public static void main(String[] args) throws Exception{
        StopWatch stopWatch = new StopWatch();
        Decoder decoder = new Decoder();
        Preprocessor preprocessor = new Preprocessor();
        FlightService flightService = new FlightService();
        MessageService messageService = new MessageService();
        FileService fileService = new FileService();

        Path filePath = Path.of("I dont know boss");
        String fileName = filePath.getFileName().toString();
        String[] fileNameParts = fileName.split("_");

        short hour = Short.parseShort(fileNameParts[0]);
        long sensorSerial = Long.parseLong(fileNameParts[1]);

        AdsbFile adsbFile = fileService.findExisting(hour, sensorSerial);
        if(adsbFile != null) {
            fileService.deleteExistingData(adsbFile.getId());
        } else {
            adsbFile = fileService.create(hour, sensorSerial);
        }

        int msgCount = 0;
        stopWatch.start();
        try(Stream<String> lines = Files.lines(filePath)){
            for(String line : (Iterable<String>) lines::iterator){
                AdsbMessage decodedMessage = decoder.decode(line);
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
                            if(!flight.addOperationalStatusTimestampList(timestamp)){
                                messageService.addToOperationalStatusList(operationalStatus, flight.getId(), adsbFile.getId());
                            }
                        }
                        case AirborneVelocity airborneVelocity -> {
                            if(!flight.addAirborneVelocityTimestampList(timestamp)){
                                messageService.addToAirborneVelocity(airborneVelocity, flight.getId(), adsbFile.getId());
                            }
                        }
                        default -> {}
                    }
                }
            }
            messageService.flushAll();
            flightService.flushCache();
            stopWatch.stop();
            fileService.finalizeFile(adsbFile.getId(),  msgCount, stopWatch.getTime());
            Database.close();
        }
        catch (Exception e){
            System.err.println("Failed to read file: " + filePath);
            e.printStackTrace();
        }
    }
}
