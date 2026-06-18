/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.execute;

import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationStatusTestPair;
import org.pitest.mutationtest.environment.ResetEnvironment;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.testapi.Description;
import org.pitest.testapi.TestResult;
import org.pitest.testapi.TestUnit;
import org.pitest.testapi.execute.Container;
import org.pitest.testapi.execute.ExitingResultCollector;
import org.pitest.testapi.execute.MultipleTestGroup;
import org.pitest.testapi.execute.Pitest;
import org.pitest.testapi.execute.containers.ConcreteResultCollector;
import org.pitest.testapi.execute.containers.UnContainer;
import org.pitest.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.pitest.util.Unchecked.translateCheckedException;

public class MutationTestWorker {

  private static final Logger                               LOG   = Log
      .getLogger();

  // micro optimise debug logging
  private static final boolean                              DEBUG = LOG
      .isLoggable(Level.FINE);

  private final Mutater                                     mutater;
  private final ClassLoader                                 loader;
  private final HotSwap                                     hotswap;
  private final boolean                                     fullMutationMatrix;

  private final ResetEnvironment                            reset;

  /**
   * When set, the class targeted by a mutation is redefined back to its original
   * (classloader) bytes before the next mutant is swapped in, so every redefine
   * is original-&gt;variant rather than variant-&gt;variant. This matters for the
   * whole-class "prebake" engine, where repeatedly redefining the same class
   * with successive variants in one JVM can crash the JVMTI agent (see the
   * single-class assumption documented on {@link HotSwap}).
   */
  static final String RESET_BEFORE_REDEFINE_PROPERTY = "pitest.prebake.resetBeforeRedefine";

  private final boolean                                     resetBeforeRedefine;
  // Original bytes of classes already redefined in this minion, so we can roll
  // them back to their pristine state before applying the next mutant.
  private final ClassByteArraySource                        originalBytes;
  private final Set<ClassName>                              redefinedClasses = new HashSet<>();

  public MutationTestWorker(HotSwap hotswap,
                            Mutater mutater,
                            ClassLoader loader,
                            ResetEnvironment reset,
                            boolean fullMutationMatrix) {
    this.loader = loader;
    this.reset = reset;
    this.mutater = mutater;
    this.hotswap = hotswap;
    this.fullMutationMatrix = fullMutationMatrix;
    this.resetBeforeRedefine = Boolean.getBoolean(RESET_BEFORE_REDEFINE_PROPERTY);
    this.originalBytes = new ClassloaderByteArraySource(loader);
  }

  protected void run(final Collection<MutationDetails> range, final Reporter r,
      final TimeOutDecoratedTestSource testSource) throws IOException {

    for (final MutationDetails mutation : range) {
      if (DEBUG) {
        LOG.fine("Running mutation " + mutation);
      }
      final long t0 = System.nanoTime();
      processMutation(r, testSource, mutation);
      if (DEBUG) {
        LOG.fine("processed mutation in " + NANOSECONDS.toMillis(System.nanoTime() - t0)
            + " ms.");
      }
    }

  }

  private void processMutation(Reporter r,
                               TimeOutDecoratedTestSource testSource,
                               MutationDetails mutationDetails) {

    final MutationIdentifier mutationId = mutationDetails.getId();
    final Mutant mutatedClass = this.mutater.getMutation(mutationId);

    reset.resetFor(mutatedClass);

    if (DEBUG) {
      LOG.fine("mutating method " + mutatedClass.getDetails().getMethod());
    }
    final List<TestUnit> relevantTests = testSource
        .translateTests(mutationDetails.getTestsInOrder());

    r.describe(mutationId);

    final MutationStatusTestPair mutationDetected = handleMutation(
        mutationDetails, mutatedClass, relevantTests);

    r.report(mutationId, mutationDetected);
    if (DEBUG) {
      LOG.fine("Mutation " + mutationId + " detected = " + mutationDetected);
    }
  }

  private MutationStatusTestPair handleMutation(
      final MutationDetails mutationId, final Mutant mutatedClass,
      final List<TestUnit> relevantTests) {
    final MutationStatusTestPair mutationDetected;
    if ((relevantTests == null) || relevantTests.isEmpty()) {
      LOG.log(Level.WARNING, "No test coverage for mutation " + mutationId + " in " + mutatedClass.getDetails().getMethod()
              + ". This should have been detected in the outer process so treating as an error");
      mutationDetected =  MutationStatusTestPair.notAnalysed(0, DetectionStatus.RUN_ERROR, Collections.emptyList());
    } else {
      mutationDetected = handleCoveredMutation(mutationId, mutatedClass,
          relevantTests);

    }
    return mutationDetected;
  }

  private MutationStatusTestPair handleCoveredMutation(
      final MutationDetails mutationId, final Mutant mutatedClass,
      final List<TestUnit> relevantTests) {
    final MutationStatusTestPair mutationDetected;
    if (DEBUG) {
      LOG.fine(relevantTests.size() + " relevant test for "
          + mutatedClass.getDetails().getMethod());
    }

    final Container c = createNewContainer();
    final long t0 = System.nanoTime();

    final ClassName targetClass = mutationId.getClassName();
    if (this.resetBeforeRedefine) {
      resetToOriginalIfNeeded(targetClass, mutatedClass.getCompanionClasses().keySet());
    }

    if (this.hotswap.insertClass(targetClass, this.loader,
        mutatedClass.getBytes(), mutatedClass.getCompanionClasses())) {
      if (this.resetBeforeRedefine) {
        this.redefinedClasses.add(targetClass);
        this.redefinedClasses.addAll(mutatedClass.getCompanionClasses().keySet());
      }
      if (DEBUG) {
        LOG.fine("replaced class with mutant in "
            + NANOSECONDS.toMillis(System.nanoTime() - t0) + " ms");
      }

      mutationDetected = doTestsDetectMutation(c, relevantTests);
    } else {
      LOG.warning("Mutation " + mutationId + " was not viable ");
      mutationDetected = MutationStatusTestPair.notAnalysed(0,
          DetectionStatus.NON_VIABLE, relevantTests.stream()
              .map(t -> t.getDescription().getQualifiedName())
              .collect(Collectors.toList()));
    }
    return mutationDetected;
  }

  /**
   * Redefines the target class (and any companion classes) back to their
   * original classloader bytes if they were previously redefined with a mutant
   * in this minion. Ensures the subsequent mutant redefine starts from the
   * pristine class state (original-&gt;variant) rather than from a prior variant.
   */
  private void resetToOriginalIfNeeded(final ClassName target, final Set<ClassName> companions) {
    final Set<ClassName> toReset = new HashSet<>();
    if (this.redefinedClasses.contains(target)) {
      toReset.add(target);
    }
    for (final ClassName companion : companions) {
      if (this.redefinedClasses.contains(companion)) {
        toReset.add(companion);
      }
    }
    if (toReset.isEmpty()) {
      return;
    }
    final Map<ClassName, byte[]> originals = new java.util.LinkedHashMap<>();
    for (final ClassName cn : toReset) {
      final Optional<byte[]> bytes = this.originalBytes.getBytes(cn.asJavaName());
      if (bytes.isPresent()) {
        originals.put(cn, bytes.get());
      } else {
        LOG.warning("Could not load original bytes for " + cn
            + " to reset before redefine; skipping reset for this class");
      }
    }
    for (final Map.Entry<ClassName, byte[]> each : originals.entrySet()) {
      // restore pristine state; failure here is non-fatal, the redefine below
      // will simply proceed from the prior state as it did before this feature.
      this.hotswap.insertClass(each.getKey(), this.loader, each.getValue());
      this.redefinedClasses.remove(each.getKey());
    }
    if (DEBUG) {
      LOG.fine("reset " + originals.keySet() + " to original bytes before redefine");
    }
  }

  private static Container createNewContainer() {
    return new UnContainer() {
      @Override
      public List<TestResult> execute(final TestUnit group) {
        final Collection<TestResult> results = new ConcurrentLinkedDeque<>();
        final ExitingResultCollector rc = new ExitingResultCollector(
            new ConcreteResultCollector(results));
        group.execute(rc);
        return new ArrayList<>(results);
      }
    };
  }



  @Override
  public String toString() {
    return "MutationTestWorker [mutater=" + this.mutater + ", loader="
        + this.loader + ", hotswap=" + this.hotswap + "]";
  }

  private MutationStatusTestPair doTestsDetectMutation(final Container c,
      final List<TestUnit> tests) {
    try {
      final CheckTestHasFailedResultListener listener = new CheckTestHasFailedResultListener(fullMutationMatrix);

      final Pitest pit = new Pitest(listener);

      if (this.fullMutationMatrix) {
        pit.run(c, tests);
      } else {
        pit.run(c, createEarlyExitTestGroup(tests));
      }

      return createStatusTestPair(listener, tests);
    } catch (final Exception ex) {
      throw translateCheckedException(ex);
    }

  }

  private MutationStatusTestPair createStatusTestPair(
      final CheckTestHasFailedResultListener listener, List<TestUnit> relevantTests) {
    List<String> failingTests = listener.getFailingTests().stream()
        .map(Description::getQualifiedName).collect(Collectors.toList());
    List<String> succeedingTests = listener.getSucceedingTests().stream()
        .map(Description::getQualifiedName).collect(Collectors.toList());
    List<String> coveredTests = relevantTests.stream()
        .map(t -> t.getDescription().getQualifiedName()).collect(Collectors.toList());

    return new MutationStatusTestPair(listener.getNumberOfTestsRun(),
        listener.status(), failingTests, succeedingTests, coveredTests);
  }

  private List<TestUnit> createEarlyExitTestGroup(final List<TestUnit> tests) {
    return Collections.singletonList(new MultipleTestGroup(tests));
  }

}
