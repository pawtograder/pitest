package org.pitest.mutationtest.engine.prebake;

import java.util.List;
import java.util.stream.Collectors;

import org.pitest.mutationtest.EngineArguments;
import org.pitest.mutationtest.MutationEngineFactory;
import org.pitest.mutationtest.engine.MutationEngine;
import org.pitest.mutationtest.engine.prebake.PreBakeConfiguration.PreBakeConfigurationEntry;

public final class PreBakeEngineFactory implements MutationEngineFactory {

    @Override
    public MutationEngine createEngine(EngineArguments arguments) {
        List<PreBakeConfigurationEntry> entries = arguments.mutators().stream()
        .map(PreBakeConfigurationEntry::forMutator)
        .collect(Collectors.toList());
        PreBakeConfiguration configuration = new PreBakeConfiguration(entries);
        return new PreBakedMutationEngine(configuration);
    }

    @Override
    public String name() {
        return "prebake";
    }

    @Override
    public String description() {
        return "Pre-bake mutation engine";
    }
}
