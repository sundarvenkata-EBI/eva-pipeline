/*
 * Copyright 2016 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.ebi.eva.pipeline.io.writers;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencb.biodata.models.variant.annotation.VariantAnnotation;
import org.opencb.opencga.storage.mongodb.variant.DBObjectToVariantAnnotationConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import uk.ac.ebi.eva.pipeline.configuration.AnnotationConfiguration;
import uk.ac.ebi.eva.pipeline.configuration.JobOptions;
import uk.ac.ebi.eva.pipeline.configuration.JobParametersNames;
import uk.ac.ebi.eva.pipeline.io.mappers.AnnotationLineMapper;
import uk.ac.ebi.eva.pipeline.jobs.AnnotationJob;
import uk.ac.ebi.eva.test.utils.JobTestUtils;
import uk.ac.ebi.eva.utils.MongoDBHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static uk.ac.ebi.eva.test.data.VepOutputContent.vepOutputContent;

/**
 * {@link VepAnnotationMongoWriter}
 * input: a List of VariantAnnotation to each call of `.write()`
 * output: all the VariantAnnotations get written in mongo, with at least the
 * "consequence types" annotations set
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {AnnotationJob.class, AnnotationConfiguration.class})
public class VepAnnotationMongoWriterTest {

    @Autowired
    private JobOptions jobOptions;

    private DBObjectToVariantAnnotationConverter converter;
    private MongoOperations mongoOperations;
    private MongoClient mongoClient;
    private VepAnnotationMongoWriter annotationWriter;
    private AnnotationLineMapper AnnotationLineMapper;

    @Test
    public void shouldWriteAllFieldsIntoMongoDb() throws Exception {
        String dbName = jobOptions.getDbName();
        String dbCollectionVariantsName = jobOptions.getDbCollectionsVariantsName();

        List<VariantAnnotation> annotations = new ArrayList<>();
        for (String annotLine : vepOutputContent.split("\n")) {
            annotations.add(AnnotationLineMapper.mapLine(annotLine, 0));
        }
        DBCollection variants = mongoClient.getDB(dbName).getCollection(dbCollectionVariantsName);

        // first do a mock of a "variants" collection, with just the _id
        writeIdsIntoMongo(annotations, variants);

        // now, load the annotation
        MongoOperations mongoOperations = MongoDBHelper.getMongoOperationsFromPipelineOptions(jobOptions.getPipelineOptions());
        annotationWriter = new VepAnnotationMongoWriter(mongoOperations, dbCollectionVariantsName);
        annotationWriter.write(annotations);

        // and finally check that documents in DB have annotation (only consequence type)
        DBCursor cursor = variants.find();

        int cnt = 0;
        int consequenceTypeCount = 0;
        while (cursor.hasNext()) {
            cnt++;
            VariantAnnotation annot = converter.convertToDataModelType((DBObject) cursor.next().get("annot"));
            assertNotNull(annot.getConsequenceTypes());
            consequenceTypeCount += annot.getConsequenceTypes().size();
        }
        assertTrue(cnt > 0);
        assertEquals(annotations.size(), consequenceTypeCount);
    }

    /**
     * Test that every VariantAnnotation gets written, even if the same variant receives different annotation from
     * different batches.
     *
     * @throws Exception if the annotationWriter.write fails, or the DBs cleaning fails
     */
    @Test
    public void shouldWriteAllFieldsIntoMongoDbMultipleSetsAnnotations() throws Exception {
        String dbName = jobOptions.getDbName();
        String dbCollectionVariantsName = jobOptions.getPipelineOptions().getString(JobParametersNames.DB_COLLECTIONS_VARIANTS_NAME);

        List<VariantAnnotation> annotations = new ArrayList<>();
        for (String annotLine : vepOutputContent.split("\n")) {
            annotations.add(AnnotationLineMapper.mapLine(annotLine, 0));
        }
        DBCollection variants = mongoClient.getDB(dbName).getCollection(dbCollectionVariantsName);

        // first do a mock of a "variants" collection, with just the _id
        writeIdsIntoMongo(annotations, variants);

        //prepare annotation sets
        List<VariantAnnotation> annotationSet1 = new ArrayList<>();
        List<VariantAnnotation> annotationSet2 = new ArrayList<>();
        List<VariantAnnotation> annotationSet3 = new ArrayList<>();

        String[] vepOutputLines = vepOutputContent.split("\n");

        for (String annotLine : Arrays.copyOfRange(vepOutputLines, 0, 2)) {
            annotationSet1.add(AnnotationLineMapper.mapLine(annotLine, 0));
        }

        for (String annotLine : Arrays.copyOfRange(vepOutputLines, 2, 4)) {
            annotationSet2.add(AnnotationLineMapper.mapLine(annotLine, 0));
        }

        for (String annotLine : Arrays.copyOfRange(vepOutputLines, 4, 7)) {
            annotationSet3.add(AnnotationLineMapper.mapLine(annotLine, 0));
        }

        // now, load the annotation
        String collections = jobOptions.getPipelineOptions().getString(JobParametersNames.DB_COLLECTIONS_VARIANTS_NAME);
        annotationWriter = new VepAnnotationMongoWriter(mongoOperations, collections);

        annotationWriter.write(annotationSet1);
        annotationWriter.write(annotationSet2);
        annotationWriter.write(annotationSet3);

        // and finally check that documents in DB have the correct number of annotation
        DBCursor cursor = variants.find();

        while (cursor.hasNext()) {
            DBObject dbObject = cursor.next();
            String id = dbObject.get("_id").toString();

            VariantAnnotation annot = converter.convertToDataModelType((DBObject) dbObject.get("annot"));

            if (id.equals("20_63360_C_T") || id.equals("20_63399_G_A") || id.equals("20_63426_G_T")) {
                assertEquals(2, annot.getConsequenceTypes().size());
                assertEquals(4, annot.getXrefs().size());
            }

        }
    }

    @Before
    public void setUp() throws Exception {
        converter = new DBObjectToVariantAnnotationConverter();
        mongoOperations = MongoDBHelper.getMongoOperationsFromPipelineOptions(jobOptions.getPipelineOptions());
        mongoClient = new MongoClient();
        AnnotationLineMapper = new AnnotationLineMapper();

        String dbName = jobOptions.getDbName();
        JobTestUtils.cleanDBs(dbName);
    }

    @After
    public void tearDown() throws Exception {
        String dbName = jobOptions.getDbName();
        JobTestUtils.cleanDBs(dbName);
    }

    private void writeIdsIntoMongo(List<VariantAnnotation> annotations, DBCollection variants) {
        Set<String> uniqueIdsLoaded = new HashSet<>();
        for (VariantAnnotation annotation : annotations) {
            String id = MongoDBHelper.buildStorageId(
                    annotation.getChromosome(),
                    annotation.getStart(),
                    annotation.getReferenceAllele(),
                    annotation.getAlternativeAllele());

            if (!uniqueIdsLoaded.contains(id)) {
                variants.insert(new BasicDBObject("_id", id));
                uniqueIdsLoaded.add(id);
            }

        }
    }

}