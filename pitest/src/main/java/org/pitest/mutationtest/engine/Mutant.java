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
package org.pitest.mutationtest.engine;

import java.util.Collections;
import java.util.Map;
import org.pitest.classinfo.ClassName;

/**
 * A fully generated mutant
 */
public final class Mutant {

  private final MutationDetails details;
  private final byte[]          bytes;
  private final Map<ClassName, byte[]> companionClasses;

  public Mutant(final MutationDetails details, final byte[] bytes) {
    this(details, bytes, Collections.emptyMap());
  }

  public Mutant(final MutationDetails details, final byte[] bytes, final Map<ClassName, byte[]> companionClasses) {
    this.details = details;
    this.bytes = bytes;
    this.companionClasses = companionClasses != null ? companionClasses : Collections.emptyMap();
  }

  /**
   * Returns a data relating to the mutant
   *
   * @return A MutationDetails object
   */
  public MutationDetails getDetails() {
    return this.details;
  }

  /**
   * Returns a byte array containing the mutant class
   *
   * @return A byte array
   */
  public byte[] getBytes() {
    return this.bytes;
  }

  /**
   * Returns companion classes (e.g., inner classes) that should be swapped together with the main class
   *
   * @return A map of class names to their bytecode
   */
  public Map<ClassName, byte[]> getCompanionClasses() {
    return this.companionClasses;
  }

}
