package org.pitest.mutationtest.build;

import org.pitest.plugin.FeatureSetting;

public class TestFilterParams {
    private final FeatureSetting conf;

    public TestFilterParams(FeatureSetting conf) {
        this.conf = conf;
    }

    public FeatureSetting conf() {
        return conf;
    }

}
