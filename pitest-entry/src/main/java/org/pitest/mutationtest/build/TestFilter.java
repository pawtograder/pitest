package org.pitest.mutationtest.build;

import org.pitest.coverage.TestInfo;
import org.pitest.mutationtest.engine.MutationDetails;

import java.util.List;

public interface TestFilter {
   boolean include(TestInfo test, MutationDetails mutant);

   static TestFilter combine(List<TestFilter> filters) {
       return (test, mutant) -> filters.stream()
               .allMatch(f -> f.include(test, mutant));
   }
}
