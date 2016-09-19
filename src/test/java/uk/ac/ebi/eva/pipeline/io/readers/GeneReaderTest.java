package uk.ac.ebi.eva.pipeline.io.readers;

import org.junit.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.test.MetaDataInstanceFactory;
import uk.ac.ebi.eva.pipeline.model.FeatureCoordinates;
import uk.ac.ebi.eva.test.data.GtfStaticTestData;
import uk.ac.ebi.eva.test.utils.JobTestUtils;
import java.io.File;
import java.io.FileInputStream;
import java.util.zip.GZIPInputStream;
import static junit.framework.TestCase.assertEquals;

public class GeneReaderTest {

    @Test
    public void shouldReadAllLinesInGtf() throws Exception {
        ExecutionContext executionContext = MetaDataInstanceFactory.createStepExecution().getExecutionContext();

        //simulate VEP output file
        File file = JobTestUtils.makeGzipFile(GtfStaticTestData.GTF_CONTENT);

        GeneReader geneReader = new GeneReader(file);
        geneReader.setSaveState(false);
        geneReader.open(executionContext);

        FeatureCoordinates gene;
        int chromosomeCount = 0;
        int count = 0;
        while ((gene = geneReader.read()) != null) {
            count++;
            if (gene.getChromosome() != null && !gene.getChromosome().isEmpty()) {
                chromosomeCount++;
            }
        }
        // all should have at least consequence type annotations
        assertEquals(count, chromosomeCount);

        // variantAnnotationReader should get all the lines from the file
        long actualCount = JobTestUtils.getLines(new GZIPInputStream(new FileInputStream(file)));
        assertEquals(actualCount, count);
    }

}