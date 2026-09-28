package org.pitest.mutationtest.engine.prebake;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypeReference;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.MutationDetails;

/**
 * Generates the class files for a record {@code R(String a, @N String b)} with a TYPE_USE
 * annotation on component {@code b} (as a JSpecify {@code @Nullable} would add), plus
 * pre-baked mutants of it. The fixtures are generated with ASM because the module compiles
 * with --release 11.
 *
 * <p>Each mutant is written in a different order from the original, so its constant pool
 * layout differs and a redefinition has to merge constant pools, which is what a real
 * hand-written mutant class does.
 */
final class RecordFixtures {

  static final String ORIGINAL = "prebakefixture/R";
  static final String ANNOTATION = "Lprebakefixture/N;";
  static final String ORIGINAL_DESCRIPTION = "original";

  private RecordFixtures() {
  }

  static byte[] original() {
    return record(ORIGINAL, ORIGINAL_DESCRIPTION, false);
  }

  static byte[] mutant(String name) {
    return record(ORIGINAL + "_" + name, name, true);
  }

  static String mutantName(String name) {
    return ORIGINAL + "_" + name;
  }

  static ClassByteArraySource source(String... mutants) {
    Map<String, byte[]> classes = new HashMap<>();
    classes.put(ORIGINAL, original());
    for (String m : mutants) {
      classes.put(mutantName(m), mutant(m));
    }
    return clazz -> Optional.ofNullable(classes.get(clazz.replace('.', '/')));
  }

  /**
   * Builds the mutants the way PIT does: findMutations on the original, then getMutation
   * for each id, in the order the mutants are given.
   */
  static List<Mutant> prebakedMutants(String... mutants) {
    List<PreBakeConfiguration.PreBakeConfigurationEntry> entries = Arrays.stream(mutants)
        .map(m -> new PreBakeConfiguration.PreBakeConfigurationEntry(
            ORIGINAL.replace('/', '.'), mutantName(m).replace('/', '.')))
        .collect(Collectors.toList());
    PreBakedMutater testee = new PreBakedMutater(source(mutants), new PreBakeConfiguration(entries));
    return testee.findMutations(ClassName.fromString(ORIGINAL)).stream()
        .map(MutationDetails::getId)
        .map(testee::getMutation)
        .collect(Collectors.toList());
  }

  private static byte[] record(String internalName, String description, boolean shuffled) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.V16, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        internalName, null, "java/lang/Record", null);
    cw.visitSource("R.java", null);
    if (shuffled) {
      describe(cw, description);
      accessors(cw, internalName);
      constructor(cw, internalName);
      fields(cw);
      components(cw);
    } else {
      components(cw);
      fields(cw);
      constructor(cw, internalName);
      accessors(cw, internalName);
      describe(cw, description);
    }
    cw.visitEnd();
    return cw.toByteArray();
  }

  private static void components(ClassWriter cw) {
    cw.visitRecordComponent("a", "Ljava/lang/String;", null).visitEnd();
    RecordComponentVisitor b = cw.visitRecordComponent("b", "Ljava/lang/String;", null);
    b.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.FIELD).getValue(), null,
        ANNOTATION, true).visitEnd();
    b.visitEnd();
  }

  private static void fields(ClassWriter cw) {
    cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "a", "Ljava/lang/String;", null, null)
        .visitEnd();
    FieldVisitor b = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "b",
        "Ljava/lang/String;", null, null);
    b.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.FIELD).getValue(), null,
        ANNOTATION, true).visitEnd();
    b.visitEnd();
  }

  private static void constructor(ClassWriter cw, String owner) {
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "a", "Ljava/lang/String;");
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitVarInsn(Opcodes.ALOAD, 2);
    mv.visitFieldInsn(Opcodes.PUTFIELD, owner, "b", "Ljava/lang/String;");
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void accessors(ClassWriter cw, String owner) {
    for (String name : new String[] {"a", "b"}) {
      MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()Ljava/lang/String;", null,
          null);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitFieldInsn(Opcodes.GETFIELD, owner, name, "Ljava/lang/String;");
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }
  }

  private static void describe(ClassWriter cw, String description) {
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "describe",
        "()Ljava/lang/String;", null, null);
    mv.visitCode();
    mv.visitLdcInsn(description);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }
}
