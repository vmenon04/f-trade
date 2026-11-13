package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.controller.TradeController;
import com.fintechfive.ftrade.service.ExecutionService;
import com.fintechfive.ftrade.service.RecipientService;
import com.fintechfive.ftrade.service.TradeEventProducer;
import com.fintechfive.ftrade.service.TradeRecordingService;
import com.fintechfive.ftrade.service.TradeTrackerService;
import com.fintechfive.ftrade.service.ValidationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks that the trade.stage.*.enabled properties decide which stages a process runs, as the docker-compose
 * services set them. A typo in a property name would otherwise leave a container healthy but doing nothing.
 */
class StageToggleTest {

    private static final List<Class<?>> STAGES = List.of(TradeController.class, ValidationConsumer.class,
            RecordingConsumer.class, ExecutionConsumer.class);

    // only the stage classes, with their dependencies mocked: no Kafka, database or web server is started
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(STAGES.toArray(Class<?>[]::new))
            .withBean(RecipientService.class, () -> mock(RecipientService.class))
            .withBean(ValidationService.class, () -> mock(ValidationService.class))
            .withBean(TradeRecordingService.class, () -> mock(TradeRecordingService.class))
            .withBean(ExecutionService.class, () -> mock(ExecutionService.class))
            .withBean(TradeEventProducer.class, () -> mock(TradeEventProducer.class));

    // what each docker-compose service sets: its own stage true, the others false
    private static String[] onlyStage(String stage) {
        return List.of("api", "validation", "recording", "execution").stream()
                .map(name -> "trade.stage." + name + ".enabled=" + name.equals(stage))
                .toArray(String[]::new);
    }

    private void assertRunsOnly(String stage, Class<?> expected) {
        runner.withPropertyValues(onlyStage(stage)).run(context -> {
            for (Class<?> stageClass : STAGES) {
                if (stageClass == expected) {
                    assertThat(context).hasSingleBean(stageClass);
                } else {
                    assertThat(context).doesNotHaveBean(stageClass);
                }
            }
        });
    }

    @Test
    void everyStageRunsWhenNothingIsSet() {
        runner.run(context -> STAGES.forEach(stageClass -> assertThat(context).hasSingleBean(stageClass)));
    }

    @Test
    void aStageSetToFalseIsLeftOutAndTheRestStillRun() {
        runner.withPropertyValues("trade.stage.execution.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ExecutionConsumer.class);
            assertThat(context).hasSingleBean(TradeController.class);
            assertThat(context).hasSingleBean(ValidationConsumer.class);
            assertThat(context).hasSingleBean(RecordingConsumer.class);
        });
    }

    @Test
    void theApiContainerOnlyTakesOrders() {
        assertRunsOnly("api", TradeController.class);
    }

    @Test
    void theValidationContainerOnlyValidates() {
        assertRunsOnly("validation", ValidationConsumer.class);
    }

    @Test
    void theRecordingContainerOnlyRecords() {
        assertRunsOnly("recording", RecordingConsumer.class);
    }

    @Test
    void theExecutionContainerOnlyExecutes() {
        assertRunsOnly("execution", ExecutionConsumer.class);
    }

    // it serves the live trades the api container's endpoint shows, so it runs there and nowhere else
    @Test
    void theTradeTrackerRunsInTheApiContainer() {
        ApplicationContextRunner tracker = new ApplicationContextRunner()
                .withUserConfiguration(TradeTrackerConsumer.class)
                .withBean(TradeTrackerService.class, () -> mock(TradeTrackerService.class));

        tracker.withPropertyValues(onlyStage("api"))
                .run(context -> assertThat(context).hasSingleBean(TradeTrackerConsumer.class));
        for (String stage : List.of("validation", "recording", "execution")) {
            tracker.withPropertyValues(onlyStage(stage))
                    .run(context -> assertThat(context).doesNotHaveBean(TradeTrackerConsumer.class));
        }
    }
}
