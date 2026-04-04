package com.qorbit.engine.learning;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class LearningReportRunner implements ApplicationRunner {

    private final LearningService learningService;
    private final ConfigurableApplicationContext context;

    public LearningReportRunner(LearningService learningService, ConfigurableApplicationContext context) {
        this.learningService = learningService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("report")) {
            return;
        }
        if (args.containsOption("json")) {
            System.out.println(learningService.buildJsonReport());
        } else {
            System.out.println(learningService.buildTextReport());
        }
        int exitCode = 0;
        System.exit(exitCode);
    }
}
