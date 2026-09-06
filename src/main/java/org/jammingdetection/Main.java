package org.jammingdetection;

import org.jammingdetection.config.Config;
import org.jammingdetection.config.Database;
import org.jammingdetection.detection.DetectionMain;
import org.jammingdetection.detection.service.AnomalyService;
import org.jammingdetection.ingestion.IngestionMain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entry point for the full jamming-detection pipeline: processes a dataset
 * directory, and runs ingestion and detection on every raw ADS-B file found.
 *
 * <p>The dataset root (configured via {@code ingestion.data.path}) is
 * expected to follow this directory structure:
 * <pre>
 * dataset_name/
 * └── year/
 *     └── month/
 *         └── day/
 *             └── hh_sensorSerial.txt.gz
 * </pre>
 * Each {@code .txt.gz} file holds exactly one hour of raw ADS-B messages
 * from a single ground sensor, identified by its two-digit hour ({@code hh})
 * and sensor serial number in the filename. The {@code year}/{@code month}/
 * {@code day} path segments are parsed to determine the file's date, and
 * files are processed in sorted path order so that, within a sensor, hours
 * are ingested chronologically.
 *
 * <p>For each file, {@link IngestionMain#run} decodes and persists its
 * messages, and {@link DetectionMain#run} then runs anomaly detection on
 * the resulting data. A file that fails to process does not stop the run,
 * the error is logged and processing continues with the next file. Once
 * all files have been processed, {@link AnomalyService#filterFalsePositionGaps}
 * removes position gap anomalies attributable to single sensor coverage
 * gap, and the database connection pool is closed.
 */
public class Main {
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

            try{
                long fileId = IngestionMain.run(filePath, fileDate);
                DetectionMain.run(fileId);
            } catch (Exception e) {
                System.err.println("Failed processing file: " + filePath);
                e.printStackTrace();
            }
        }

        AnomalyService.filterFalsePositionGaps();
        Database.close();
    }
}
