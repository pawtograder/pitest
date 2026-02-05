package org.pitest.mutationtest.execute;

import java.util.LinkedHashMap;
import java.util.Map;
import org.pitest.boot.HotSwapAgent;
import org.pitest.classinfo.ClassName;
import org.pitest.util.Unchecked;

/**
 * Since pitest 1.9.4 there is an implicit assumption that pitest will never mutate
 * more than once class within the same jvm. If this assumption is ever broken, mutants
 * from the previous class would remain active and invalidate results.
 */
class HotSwap {

  public Boolean insertClass(final ClassName clazzName, ClassLoader loader, final byte[] mutantBytes) {
    return insertClass(clazzName, loader, mutantBytes, null);
  }

  public Boolean insertClass(final ClassName clazzName, ClassLoader loader, final byte[] mutantBytes,
                              final Map<ClassName, byte[]> companionClasses) {
    try {
      // Build map of all classes to swap (main + companions)
      Map<String, byte[]> allMutants = new LinkedHashMap<>();
      allMutants.put(clazzName.asInternalName(), mutantBytes);
      if (companionClasses != null) {
        for (Map.Entry<ClassName, byte[]> entry : companionClasses.entrySet()) {
          allMutants.put(entry.getKey().asInternalName(), entry.getValue());
        }
      }

      // Some frameworks (eg quarkus) run tests in non delegating
      // classloaders. Need to make sure these are transformed too
      CatchNewClassLoadersTransformer.setMutants(allMutants);

      // Trigger loading and swap for the main class
      Class<?> clazz = Class.forName(clazzName.asJavaName(), false, loader);
      boolean success = HotSwapAgent.hotSwap(clazz, mutantBytes);

      // Swap companion classes (inner classes)
      if (companionClasses != null) {
        for (Map.Entry<ClassName, byte[]> entry : companionClasses.entrySet()) {
          try {
            Class<?> companionClass = Class.forName(entry.getKey().asJavaName(), false, loader);
            success = HotSwapAgent.hotSwap(companionClass, entry.getValue()) && success;
          } catch (final ClassNotFoundException e) {
            // Companion class might not be loaded yet, that's ok
            // The transformer will catch it when it's loaded
          }
        }
      }

      return success;

    } catch (final ClassNotFoundException e) {
      throw Unchecked.translateCheckedException(e);
    }

  }

}
