package org.pitest.mutationtest.build;

import org.pitest.classpath.CodeSource;
import org.pitest.coverage.CoverageDatabase;

import java.util.Properties;
import java.util.stream.Collectors;

public class FilteringPrioritiser implements TestPrioritiserFactory {

    private final TestPrioritiserFactory delegate;
    private final TestFilter filter;

    public FilteringPrioritiser(TestPrioritiserFactory delegate, TestFilter filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public TestPrioritiser makeTestPrioritiser(Properties props, CodeSource code, CoverageDatabase coverage) {
        TestPrioritiser p = delegate.makeTestPrioritiser(props, code, coverage);

        return mutation -> p.assignTests(mutation).stream()
                .filter(test -> filter.include(test, mutation))
                .collect(Collectors.toList());
    }

    @Override
    public String description() {
        return "Internal prioritiser factory";
    }
}
