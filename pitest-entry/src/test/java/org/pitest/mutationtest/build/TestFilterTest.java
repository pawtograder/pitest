package org.pitest.mutationtest.build;

import org.junit.Test;
import org.pitest.classinfo.ClassName;
import org.pitest.coverage.TestInfo;
import org.pitest.mutationtest.engine.MutationDetails;

import java.util.Collections;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.pitest.mutationtest.engine.MutationDetailsMother.aMutationDetail;

public class TestFilterTest {

    @Test
    public void doesNotFilterWhenNoChildren() {
        TestFilter underTest = TestFilter.combine(Collections.emptyList());
        assertThat(underTest.include(aTest(), aMutant())).isTrue();
    }

    @Test
    public void filtersWhenOneChildMatches() {
        TestFilter underTest = TestFilter.combine(asList(alwaysTrue(), alwaysTrue(), alwaysFalse()));
        assertThat(underTest.include(aTest(), aMutant())).isFalse();
    }

    @Test
    public void doesNotFilterWhenNoMatch() {
        TestFilter underTest = TestFilter.combine(asList(alwaysTrue(), alwaysTrue(), alwaysTrue()));
        assertThat(underTest.include(aTest(), aMutant())).isTrue();
    }

    private TestFilter alwaysTrue() {
        return (t, m) -> true;
    }

    private TestFilter alwaysFalse() {
        return (t, m) -> false;
    }

    private MutationDetails aMutant() {
        return aMutationDetail().build();
    }

    private TestInfo aTest() {
        return new TestInfo("foo", "foo", 0, Optional.ofNullable(ClassName.fromString("com.foo")), 0);
    }
}