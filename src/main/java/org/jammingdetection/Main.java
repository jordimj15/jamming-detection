package org.jammingdetection;

import decoder.Decoder;
import modes.AdsbMessage;
import modes.computed.ComputedPosition;
import modes.raw.AirborneVelocity;
import modes.raw.OperationalStatus;
import org.jammingdetection.model.Flight;
import org.jammingdetection.service.FlightService;
import org.jammingdetection.service.MessageService;
import preprocessor.Preprocessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception{
        Path filePath = null;
        long fileId = 0;

        Decoder decoder = new Decoder();
        Preprocessor preprocessor = new Preprocessor();
        FlightService flightService = new FlightService();
        MessageService messageService = new MessageService();

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
                                messageService.addToPositionList(computedPosition, flight.getId(), fileId);
                            }
                        }
                        case OperationalStatus operationalStatus -> {
                            if(!flight.addOperationalStatusTimestampList(timestamp)){
                                messageService.addToOperationalStatusList(operationalStatus, flight.getId(), fileId);
                            }
                        }
                        case AirborneVelocity airborneVelocity -> {
                            if(!flight.addAirborneVelocityTimestampList(timestamp)){
                                messageService.addToAirborneVelocity(airborneVelocity, flight.getId(), fileId);
                            }
                        }
                        default -> {}
                    }

                }

            }
        }
        catch (Exception e){
            System.err.println("Failed to read file: " + filePath);
            e.printStackTrace();
        }
    }
}
