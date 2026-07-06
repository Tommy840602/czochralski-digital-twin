package com.twin.alarm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupBaselineInit {

    private static final Logger log = LoggerFactory.getLogger(StartupBaselineInit.class);

    @EventListener(ApplicationReadyEvent.class)
    public void initBaseline() {
        log.info("Startup baseline init skipped (managed manually via SQL)");
    }
}
