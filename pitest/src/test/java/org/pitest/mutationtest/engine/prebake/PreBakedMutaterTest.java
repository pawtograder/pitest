package org.pitest.mutationtest.engine.prebake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.pitest.boot.HotSwapAgent;
import org.pitest.mutationtest.engine.Mutant;

public class PreBakedMutaterTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void renamesMutantToTheOriginalClass() {
    ClassNode mutant = read(RecordFixtures.prebakedMutants("M1").get(0));
    assertThat(mutant.name).isEqualTo(RecordFixtures.ORIGINAL);
  }

  @Test
  public void keepsRecordComponentsButDropsTheirTypeAnnotations() {
    ClassNode mutant = read(RecordFixtures.prebakedMutants("M1").get(0));
    assertThat(mutant.recordComponents).extracting(c -> c.name).containsExactly("a", "b");
    assertThat(mutant.recordComponents).extracting(c -> c.descriptor)
        .containsOnly("Ljava/lang/String;");
    for (RecordComponentNode c : mutant.recordComponents) {
      assertThat(c.visibleTypeAnnotations).isNullOrEmpty();
      assertThat(c.invisibleTypeAnnotations).isNullOrEmpty();
    }
  }

  @Test
  public void keepsTypeAnnotationsOutsideRecordComponents() {
    ClassNode mutant = read(RecordFixtures.prebakedMutants("M1").get(0));
    assertThat(mutant.fields.get(1).name).isEqualTo("b");
    assertThat(mutant.fields.get(1).visibleTypeAnnotations).extracting(a -> a.desc)
        .containsExactly(RecordFixtures.ANNOTATION);
  }

  /**
   * Regression test for record mutants after the first one in a minion ending in RUN_ERROR
   * ("attempted to change the class NestHost, NestMembers, Record, or PermittedSubclasses
   * attribute") on JDKs without the JDK-8376185 fix, such as 21.0.12.
   */
  @Test
  public void recordMutantsCanBeHotSwappedRepeatedlyInOneJvm() throws Exception {
    assumeTrue("records need Java 16+", javaFeatureVersion() >= 16);

    Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
    ProcessBuilder pb = new ProcessBuilder(java.toString(),
        "-javaagent:" + agentJar(),
        "-cp", System.getProperty("java.class.path"),
        RecordHotSwapHarness.class.getName(), "M1", "M2", "M3");
    pb.redirectErrorStream(true);
    Process p = pb.start();
    String output = readAll(p.getInputStream());
    assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();

    assertThat(output).contains("swap M1: OK", "swap M2: OK", "swap M3: OK")
        .doesNotContain("FAIL", "WRONG");
    assertThat(p.exitValue()).as(output).isZero();
  }

  private String agentJar() throws Exception {
    File jar = folder.newFile("agent.jar");
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.putValue("Premain-Class", HotSwapAgent.class.getName());
    attributes.putValue("Can-Redefine-Classes", "true");
    attributes.putValue("Can-Retransform-Classes", "true");
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest)) {
      // agent classes are loaded from the forked JVM's classpath
    }
    return jar.getAbsolutePath();
  }

  private static ClassNode read(Mutant mutant) {
    ClassNode node = new ClassNode(Opcodes.ASM9);
    new ClassReader(mutant.getBytes()).accept(node, 0);
    return node;
  }

  private static int javaFeatureVersion() {
    return Runtime.version().feature();
  }

  private static String readAll(InputStream in) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    in.transferTo(bytes);
    return bytes.toString(StandardCharsets.UTF_8);
  }
}
