package org.pitest.mutationtest.engine.prebake;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
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
        
        // Read mutated class to discover inner classes
        ClassNode mutatedNode = new ClassNode(Opcodes.ASM9);
        new ClassReader(mutatedBytes).accept(mutatedNode, ClassReader.SKIP_CODE);
        
        ClassReader mutatedReader = new ClassReader(mutatedBytes);
        ClassWriter writer = new ClassWriter(mutatedReader, ClassWriter.COMPUTE_MAXS);

        // Create a remapper to replace mutant class name with original class name
        PrefixRemapper remapper = new PrefixRemapper(
                mutatedClassName.replace('.', '/'),
                sourceClassName.replace('.', '/'));

        // Apply the transformation to outer class
        ClassRemapper classRemapper = new ClassRemapper(writer, remapper);
        mutatedReader.accept(classRemapper, ClassReader.EXPAND_FRAMES);
        byte[] transformedOuterBytes = writer.toByteArray();

        // Discover and transform inner classes
        Map<ClassName, byte[]> companions = new LinkedHashMap<>();
        String mutatedInternalName = mutatedClassName.replace('.', '/');
        String sourceInternalName = sourceClassName.replace('.', '/');
        
        for (InnerClassNode inner : mutatedNode.innerClasses) {
            // Only process inner classes that belong to the mutated class
            if (inner.name != null && inner.name.startsWith(mutatedInternalName + "$")) {
                Optional<byte[]> innerBytes = byteSource.getBytes(inner.name);
                if (innerBytes.isPresent()) {
                    // Transform inner class with same remapper
                    ClassReader innerReader = new ClassReader(innerBytes.get());
                    ClassWriter innerWriter = new ClassWriter(innerReader, ClassWriter.COMPUTE_MAXS);
                    ClassRemapper innerRemapper = new ClassRemapper(innerWriter, remapper);
                    innerReader.accept(innerRemapper, ClassReader.EXPAND_FRAMES);
                    
                    // Calculate target inner class name
                    String suffix = inner.name.substring(mutatedInternalName.length());
                    String targetInnerName = sourceInternalName + suffix;
                    companions.put(ClassName.fromString(targetInnerName.replace('/', '.')), innerWriter.toByteArray());
                }
            }
        }

        return new Mutant(
                new MutationDetails(id, sourceFile,
                        "Replace " + sourceClassName + " with " + mutatedClassName, 0, 0),
                transformedOuterBytes,
                companions);
    }

    private static class PrefixRemapper extends Remapper {
        private final String fromPrefix;
        private final String toPrefix;
        private final String fromOuterPrefix;
        private final String toOuterPrefix;

        PrefixRemapper(String fromClass, String toClass) {
            this.fromPrefix = fromClass;
            this.toPrefix = toClass;
            
            // Extract outer class prefixes for handling mutations in inner classes
            // If fromClass is an inner class (contains $), extract the outer part
            int firstDollarIndex = fromClass.indexOf('$');
            if (firstDollarIndex > 0) {
                // Mutation is in an inner class - extract outer class names
                String fromOuter = fromClass.substring(0, firstDollarIndex);
                int toFirstDollarIndex = toClass.indexOf('$');
                String toOuter = toFirstDollarIndex > 0 ? toClass.substring(0, toFirstDollarIndex) : toClass;
                
                // Check if outer class names differ (i.e., outer was also mutated)
                if (!fromOuter.equals(toOuter)) {
                    this.fromOuterPrefix = fromOuter;
                    this.toOuterPrefix = toOuter;
                } else {
                    this.fromOuterPrefix = null;
                    this.toOuterPrefix = null;
                }
            } else {
                // Mutation is in outer class
                this.fromOuterPrefix = null;
                this.toOuterPrefix = null;
            }
        }

        @Override
        public String map(String internalName) {
            // First check exact match for the main class
            if (internalName.equals(fromPrefix)) {
                return toPrefix;
            }
            // Handle nested inner classes of the mutated class: MutatedClass$Inner, MutatedClass$1, etc.
            if (internalName.startsWith(fromPrefix + "$")) {
                return toPrefix + internalName.substring(fromPrefix.length());
            }
            // Handle outer class references when mutation is in inner class
            // This handles cases where the mutated inner class bytecode references the mutated outer class
            if (fromOuterPrefix != null) {
                if (internalName.equals(fromOuterPrefix)) {
                    return toOuterPrefix;
                }
                if (internalName.startsWith(fromOuterPrefix + "$")) {
                    return toOuterPrefix + internalName.substring(fromOuterPrefix.length());
                }
            }
            return internalName;
        }
    }

}
