package org.pitest.mutationtest.engine.prebake;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import org.pitest.boot.HotSwapAgent;
import org.pitest.mutationtest.engine.Mutant;

/**
 * Run in a forked JVM with {@link HotSwapAgent} as its agent (see PreBakedMutaterTest).
 * Defines the original record, then hot-swaps each pre-baked mutant into it in turn, the way
 * a minion does, printing one line per swap. Exits non-zero if any swap fails or the class
 * does not behave like the mutant afterwards.
 */
public final class RecordHotSwapHarness {

  private RecordHotSwapHarness() {
  }

  public static void main(String[] args) throws Exception {
    Class<?> record = new FixtureLoader().define(RecordFixtures.original());
    List<Mutant> mutants = RecordFixtures.prebakedMutants(args);
    List<String> order = new ArrayList<>();
    for (String arg : args) {
      order.add(arg);
    }
    // swap every mutant, then the first one again, so each id is redefined more than once
    order.add(args[0]);
    mutants.add(mutants.get(0));

    boolean allOk = true;
    for (int i = 0; i < mutants.size(); i++) {
      String expected = order.get(i);
      String result;
      try {
        HotSwapAgent.hotSwap(record, mutants.get(i).getBytes());
        String described = (String) record.getMethod("describe").invoke(null);
        String components = componentNames(record);
        if (!expected.equals(described) || !"a,b".equals(components)) {
          result = "WRONG describe=" + described + " components=" + components;
        } else {
          result = "OK";
        }
      } catch (Throwable t) {
        result = "FAIL " + t;
      }
      allOk &= result.equals("OK");
      System.out.println("swap " + expected + ": " + result);
    }
    System.exit(allOk ? 0 : 1);
  }

  // Class.getRecordComponents is not in the Java 11 API this module compiles against
  private static String componentNames(Class<?> record) throws Exception {
    Object components = Class.class.getMethod("getRecordComponents").invoke(record);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Array.getLength(components); i++) {
      Object c = Array.get(components, i);
      if (sb.length() > 0) {
        sb.append(',');
      }
      sb.append(c.getClass().getMethod("getName").invoke(c));
    }
    return sb.toString();
  }

  private static final class FixtureLoader extends ClassLoader {
    FixtureLoader() {
      super(RecordHotSwapHarness.class.getClassLoader());
    }

    Class<?> define(byte[] bytes) {
      return defineClass(null, bytes, 0, bytes.length);
    }
  }
}
