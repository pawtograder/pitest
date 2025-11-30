package org.pitest.mutationtest.engine.prebake;

import java.util.Collection;
import java.util.stream.Collectors;

import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationEngine;

public class PreBakedMutationEngine implements MutationEngine {
    private final PreBakeConfiguration configuration;

    public PreBakedMutationEngine(PreBakeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Mutater createMutator(ClassByteArraySource byteSource) {
        return new PreBakedMutater(byteSource, configuration);
    }

    @Override
    public Collection<String> getMutatorNames() {
        return configuration.getEntries().stream()
                .map(PreBakeConfiguration.PreBakeConfigurationEntry::getMutatedClassName)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "PreBakedMutationEngine [configuration=" + configuration + "]";
    }

    @Override
    public String getName() {
        return "prebake";
    }
}
