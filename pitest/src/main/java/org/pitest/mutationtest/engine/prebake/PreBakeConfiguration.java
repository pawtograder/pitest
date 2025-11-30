package org.pitest.mutationtest.engine.prebake;

import java.util.List;

public class PreBakeConfiguration {
    private final List<PreBakeConfigurationEntry> entries;

    public PreBakeConfiguration(List<PreBakeConfigurationEntry> entries) {
        this.entries = entries;
    }

    public List<PreBakeConfigurationEntry> getEntries() {
        return entries;
    }

    @Override
    public String toString() {
        return "PreBakeConfiguration{"
                + "entries=" + entries
                + '}';
    }

    public static class PreBakeConfigurationEntry {
        private final String sourceClassName;
        private final String mutatedClassName;

        public static PreBakeConfigurationEntry forMutator(String mutator) {
            String[] parts = mutator.split("-");
            return new PreBakeConfigurationEntry(parts[0], parts[1]);
        }

        public PreBakeConfigurationEntry(String sourceClassName, String mutatedClassName) {
            this.sourceClassName = sourceClassName;
            this.mutatedClassName = mutatedClassName;
        }

        public String getSourceClassName() {
            return sourceClassName;
        }

        public String getMutatedClassName() {
            return mutatedClassName;
        }

        @Override
        public String toString() {
            return "PreBakeConfigurationEntry{"
                    + "sourceClassName='" + sourceClassName + '\''
                    + ", mutatedClassName='" + mutatedClassName + '\''
                    + '}';
        }
    }
}
