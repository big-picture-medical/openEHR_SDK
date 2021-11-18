/*
 *
 *  *  Copyright (c) 2020  Stefan Spiska (Vitasystems GmbH) and Hannover Medical School
 *  *  This file is part of Project EHRbase
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *
 */

package org.ehrbase.serialisation.flatencoding.std.umarshal.postprocessor;

import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.TermMapping;
import org.ehrbase.serialisation.flatencoding.std.umarshal.rmunmarshaller.TermMappingRMUnmarshaller;
import org.ehrbase.serialisation.walker.Context;
import org.ehrbase.serialisation.walker.FlatHelper;
import org.ehrbase.webtemplate.path.flat.FlatPathDto;

import java.util.Map;
import java.util.Set;

public class DvTextPostprocessor extends AbstractUnmarshalPostprocessor<DvText> {

  private static final TermMappingRMUnmarshaller TERM_MAPPING_RM_UNMARSHALLER =
      new TermMappingRMUnmarshaller();
  /** {@inheritDoc} */
  @Override
  public void process(
      String term,
      DvText rmObject,
      Map<FlatPathDto, String> values,
      Set<String> consumedPaths,
      Context<Map<FlatPathDto, String>> context) {

    FlatHelper.extractMultiValuedFullPath(term, "_mapping", values).entrySet().stream()
        .map(
            e ->
                toTermMapping(
                    e.getValue(), term + "/_mapping:" + e.getKey(), consumedPaths, context))
        .forEach(rmObject::addMapping);

    FlatHelper.consumeAllMatching(term + "/_mapping", values, consumedPaths);
  }

  /** {@inheritDoc} */
  @Override
  public Class<DvText> getAssociatedClass() {
    return DvText.class;
  }

  private TermMapping toTermMapping(
      Map<FlatPathDto, String> values,
      String term,
      Set<String> consumedPaths,
      Context<Map<FlatPathDto, String>> context) {
    TermMapping termMapping = new TermMapping();

    TERM_MAPPING_RM_UNMARSHALLER.handle(term, termMapping, values, context, consumedPaths);

    return termMapping;
  }
}
