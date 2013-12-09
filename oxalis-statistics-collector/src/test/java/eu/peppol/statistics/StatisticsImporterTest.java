package eu.peppol.statistics;

import eu.peppol.statistics.repository.DownloadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author steinar
 *         Date: 22.03.13
 *         Time: 17:56
 */
@Test(groups = {"integration"})
public class StatisticsImporterTest {

    public static final Logger log = LoggerFactory.getLogger(StatisticsImporterTest.class);

    public static final int COUNT_OF_SAMPLE_ENTRIES = 10;
    private File downloadRepoDir;
    private DownloadRepository downloadRepository;
    private AggregatedStatisticsRepository aggregatedStatisticsRepository;
    private Collection<AggregatedStatistics> aggregatedStatistics;

    @BeforeTest
    public void setUp() {

        // Establishes our file based download repository
        downloadRepoDir = new File(System.getProperty("java.io.tmpdir"), "oxalis-test");
        downloadRepository = new DownloadRepository(downloadRepoDir);

        AggregatedStatisticsSampleGenerator aggregatedStatisticsSampleGenerator = new AggregatedStatisticsSampleGenerator();

        // Prepares some data
        aggregatedStatistics = aggregatedStatisticsSampleGenerator.generateEntries(COUNT_OF_SAMPLE_ENTRIES);
        assertNotNull(aggregatedStatistics);

        assertEquals(aggregatedStatistics.size(), COUNT_OF_SAMPLE_ENTRIES);

        // Creates an instance of our DBMS statistics repository

        // TODO: implement a factory for AggregatedStatisticsRepository
        aggregatedStatisticsRepository = null;

    }

    @Test
    public void testInsertEntriesInDatabase() {
            int i=0;
        try {
            for (AggregatedStatistics statisticsEntry : aggregatedStatistics) {
                i++;
                aggregatedStatisticsRepository.persist(statisticsEntry);
            }
        } catch (Exception e) {
            log.error("Something went wrong during insertion of aggregated statistics for " + i + "th. item");
        }
    }

    @Test
    public void testLoadSaveAndArchive() throws Exception {

        StatisticsImporter statisticsImporter = new StatisticsImporter(downloadRepository);
        statisticsImporter.loadSaveAndArchive();
    }
}