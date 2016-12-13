package uk.ac.ebi.eva.pipeline.jobs.flows;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import uk.ac.ebi.eva.pipeline.jobs.CommonJobStepInitialization;
import uk.ac.ebi.eva.pipeline.jobs.deciders.EmptyFileDecider;
import uk.ac.ebi.eva.pipeline.jobs.deciders.SkipStepDecider;
import uk.ac.ebi.eva.pipeline.jobs.steps.AnnotationLoaderStep;
import uk.ac.ebi.eva.pipeline.jobs.steps.VepAnnotationGeneratorStep;
import uk.ac.ebi.eva.pipeline.jobs.steps.VepInputGeneratorStep;
import uk.ac.ebi.eva.pipeline.parameters.JobOptions;
import uk.ac.ebi.eva.pipeline.parameters.JobParametersNames;

@Configuration
@EnableBatchProcessing
@Import({VepAnnotationGeneratorStep.class, VepInputGeneratorStep.class, AnnotationLoaderStep.class})
public class AnnotationFlow extends CommonJobStepInitialization {

    public static final String NAME_VEP_ANNOTATION_FLOW = "VEP annotation flow";

    public static final String GENERATE_VEP_ANNOTATION = "Generate VEP annotation";


    @Qualifier("vepInputGeneratorStep")
    @Autowired
    public Step variantsAnnotGenerateInputBatchStep;

    @Qualifier("annotationLoad")
    @Autowired
    private Step annotationLoadBatchStep;

    @Autowired
    private VepAnnotationGeneratorStep vepAnnotationGeneratorStep;

    @Bean(NAME_VEP_ANNOTATION_FLOW)
    Flow vepAnnotationFlow() {
        EmptyFileDecider emptyFileDecider = new EmptyFileDecider(getPipelineOptions().getString(JobOptions.VEP_INPUT));

        return new FlowBuilder<Flow>(NAME_VEP_ANNOTATION_FLOW)
                .start(variantsAnnotGenerateInputBatchStep)
                .next(emptyFileDecider).on(EmptyFileDecider.CONTINUE_FLOW)
                .to(annotationCreate())
                .next(annotationLoadBatchStep)
                .from(emptyFileDecider).on(EmptyFileDecider.STOP_FLOW)
                .end(BatchStatus.COMPLETED.toString())
                .build();
    }

    private Step annotationCreate() {
        return generateStep(GENERATE_VEP_ANNOTATION, vepAnnotationGeneratorStep);
    }

}
