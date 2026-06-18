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
package org.pitest.boot;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

public class HotSwapAgent {

  private static Instrumentation instrumentation;

  public static void premain(final String agentArguments, // NO_UCD
      final Instrumentation inst) {
    System.out.println("Installing PIT agent");
    instrumentation = inst;
  }

  public static void addTransformer(final ClassFileTransformer transformer) {
    instrumentation.addTransformer(transformer);
  }

  public static void agentmain(final String agentArguments, // NO_UCD
      final Instrumentation inst) {
    instrumentation = inst;
  }

  public static boolean hotSwap(final Class<?> mutateMe, final byte[] bytes) { // NO_UCD

    final ClassDefinition[] definitions = { new ClassDefinition(mutateMe, bytes) };

    try {
      instrumentation.redefineClasses(definitions);
      return true;
    } catch (final ClassNotFoundException | UnmodifiableClassException | VerifyError | InternalError e) {
      // swallow
    } catch (final UnsupportedOperationException e) {
      // JVMTI rejects this redefinition (e.g. "class redefinition failed:
      // attempted to change the schema (add/remove fields)"). This happens when
      // the loaded class and the candidate bytes have different shapes - most
      // notably with the whole-class "prebake" engine when the class actually
      // loaded in the minion is not the one the mutant was generated from (e.g.
      // a same-named class shadowing it on the classpath). Swallow (as with the
      // other redefine failures above) so just this mutant is marked NON_VIABLE
      // rather than letting the exception propagate and kill the whole minion
      // (which would turn every mutation in the batch into a RUN_ERROR).
    }
    return false;
  }

}
