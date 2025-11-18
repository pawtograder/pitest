package org.pitest.mutationtest.build;

import org.pitest.plugin.ProvidesFeature;
import org.pitest.plugin.ToolClasspathPlugin;

public interface TestFilterFactory extends ToolClasspathPlugin, ProvidesFeature {

    TestFilter makeFilter(TestFilterParams params);

}
