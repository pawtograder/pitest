package org.pitest.mutationtest.engine.prebake;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.engine.Location;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;

public class PreBakedMutater implements Mutater {
    public static final String MUTATE_ENTIRE_CLASS_METHOD_NAME = "__mutate_entire_class";

    private final PreBakeConfiguration configuration;
    private final ClassByteArraySource byteSource;

    public PreBakedMutater(final ClassByteArraySource byteSource,
            final PreBakeConfiguration configuration) {
        this.configuration = configuration;
        this.byteSource = byteSource;
    }

    @Override
    public List<MutationDetails> findMutations(final ClassName classToMutate) {
        Optional<byte[]> bytes = byteSource.getBytes(classToMutate.asInternalName());
        if (bytes.isEmpty()) {
            return Collections.emptyList();
        }
        ClassReader reader = new ClassReader(bytes.get());
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        reader.accept(classNode, ClassReader.SKIP_CODE);
        String sourceFile = classNode.sourceFile;
        if (sourceFile == null) {
            return Collections.emptyList();
        }
        return configuration.getEntries()
                .stream()
                .filter(entry -> entry.getSourceClassName().equals(classToMutate.asInternalName().replace('/', '.')))
                .map(entry -> toMutationDetails(entry, sourceFile))
                .collect(Collectors.toList());
    }

    private MutationDetails toMutationDetails(PreBakeConfiguration.PreBakeConfigurationEntry entry, String sourceFile) {
        MutationIdentifier id = new MutationIdentifier(
                Location.location(ClassName.fromString(entry.getSourceClassName()),
                        MUTATE_ENTIRE_CLASS_METHOD_NAME, "()V"),
                -1, entry.getSourceClassName() + " " + entry.getMutatedClassName());
        return new MutationDetails(id, sourceFile,
                "Replace " + entry.getMutatedClassName() + " with " + entry.getSourceClassName(), 0, 0);
    }

    @Override
    public Mutant getMutation(MutationIdentifier id) {
        byte[] originalBytes = byteSource.getBytes(id.getClassName().asInternalName())
                .orElseThrow(() -> new RuntimeException("Class not found"));
        String[] parts = id.getMutator().split(" ");
        String sourceClassName = parts[0];
        String mutatedClassName = parts[1];
        ClassReader reader = new ClassReader(originalBytes);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);
        reader.accept(classNode, ClassReader.SKIP_CODE);
        String sourceFile = classNode.sourceFile;
        if (sourceFile == null) {
            throw new RuntimeException("Source file not found");
        }
        byte[] mutatedBytes = byteSource.getBytes(mutatedClassName.replace('.', '/'))
                .orElseThrow(() -> new RuntimeException("Mutated class, " + mutatedClassName + ", not found"));
        ClassReader mutatedReader = new ClassReader(mutatedBytes);
        ClassWriter writer = new ClassWriter(mutatedReader, ClassWriter.COMPUTE_MAXS);

        // Create a remapper to replace mutant class name with original class name
        SimpleRemapper remapper = new SimpleRemapper(
                mutatedClassName.replace('.', '/'),
                sourceClassName.replace('.', '/'));

        // Apply the transformation
        ClassRemapper classRemapper = new ClassRemapper(writer, remapper);
        mutatedReader.accept(classRemapper, ClassReader.EXPAND_FRAMES);

        return new Mutant(
                new MutationDetails(id, sourceFile,
                        "Replace " + sourceClassName + " with " + mutatedClassName, 0, 0),
                writer.toByteArray());
    }

}
