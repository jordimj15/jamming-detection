package org.jammingdetection;

import org.jammingdetection.config.Config;
import org.jammingdetection.config.Database;
import org.jammingdetection.detection.DetectionMain;
import org.jammingdetection.ingestion.IngestionMain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class main {
    public static void main(String[] args) throws IOException {
        Path datasetRoot = Paths.get(Config.get("ingestion.data.path"));

        List<Path> files = Files.walk(datasetRoot)
                .filter(p -> p.toString().endsWith(".txt.gz"))
                .sorted(Comparator.comparing(Path::toString))
                .collect(Collectors.toList());

        System.out.println("Found " + files.size() + " files to process");

        for (Path filePath : files) {
            System.out.println("Processing " + filePath);
            Path relative = datasetRoot.relativize(filePath);

            LocalDate fileDate = LocalDate.of(
                    Integer.parseInt(relative.getName(0).toString()),
                    Integer.parseInt(relative.getName(1).toString()),
                    Integer.parseInt(relative.getName(2).toString())
            );

            String fileName = filePath.getFileName().toString();

            try{
                long fileId = IngestionMain.run(filePath, fileDate);
                DetectionMain.run(fileId);
            } catch (Exception e) {
                System.err.println("Failed processing file: " + filePath);
                e.printStackTrace();
            }
        }
        Database.close();
    }
}
