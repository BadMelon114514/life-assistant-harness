/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.config;

import com.badmelon.farming.service.TeammateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupInitializer
implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupInitializer.class);
    private final TeammateService teammateService;

    public StartupInitializer(TeammateService teammateService) {
        this.teammateService = teammateService;
    }

    public void run(String ... args) {
        try {
            this.teammateService.initDefaultWorkers();
            log.info("\u751f\u6d3b\u52a9\u7406 Harness \u542f\u52a8\u5b8c\u6210\uff0c\u5df2\u521d\u59cb\u5316\u9ed8\u8ba4 Teammate \u8fdb\u7a0b");
        }
        catch (Exception e) {
            log.warn("\u521d\u59cb\u5316 Teammate \u5931\u8d25\uff08\u6570\u636e\u5e93\u53ef\u80fd\u672a\u521d\u59cb\u5316\uff0c\u8bf7\u6267\u884c schema.sql\uff09: {}", (Object)e.getMessage());
        }
    }
}

