package org.pitest.mutationtest.build;

import org.junit.Test;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.CodeSource;
import org.pitest.coverage.CoverageDatabase;
import org.pitest.coverage.TestInfo;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class FilteringPrioritiserTest {

    @Test
    public void appliesFilterToPrioritisedTestWhenFalse() {
        TestFilter alwaysFalse = (t, m) -> false;
        List<TestInfo> tests = asList(aTest(), aTest());
        FilteringPrioritiser underTest = new FilteringPrioritiser(alwaysSupply(tests), alwaysFalse);

        TestPrioritiser prioritiser = underTest.makeTestPrioritiser(new Properties(), unused(), unused());

        assertThat(prioritiser.assignTests(unused())).isEmpty();
    }

    @Test
    public void appliesFilterToPrioritisedTestWhenTrue() {
        TestFilter alwaysTrue = (t, m) -> true;
        List<TestInfo> tests = asList(aTest(), aTest());
        FilteringPrioritiser underTest = new FilteringPrioritiser(alwaysSupply(tests), alwaysTrue);

        TestPrioritiser prioritiser = underTest.makeTestPrioritiser(new Properties(), unused(), unused());

        assertThat(prioritiser.assignTests(unused())).containsExactlyElementsOf(tests);
    }


    private <T> T unused() {
        return null;
    }

    private TestInfo aTest() {
        return new TestInfo("foo", "bar", 1, Optional.<ClassName> empty(), 0);
    }

    private TestPrioritiserFactory alwaysSupply(List<TestInfo> tests) {
        return  new TestPrioritiserFactory() {
            @Override
            public String description() {
                return "";
            }

            @Override
            public TestPrioritiser makeTestPrioritiser(Properties props, CodeSource code, CoverageDatabase coverage) {
                return m -> tests;
            }
        };
    }
}