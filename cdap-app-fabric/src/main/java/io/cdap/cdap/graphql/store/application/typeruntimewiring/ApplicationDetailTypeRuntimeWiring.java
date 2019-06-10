/*
 *
 * Copyright © 2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.graphql.store.application.typeruntimewiring;

import com.google.inject.Inject;
import graphql.schema.idl.TypeRuntimeWiring;
import io.cdap.cdap.graphql.store.application.schema.ApplicationFields;
import io.cdap.cdap.graphql.store.application.schema.ApplicationTypes;
import io.cdap.cdap.graphql.store.metadata.datafetchers.MetadataDataFetcher;
import io.cdap.cdap.graphql.store.programrecord.datafetchers.ProgramRecordDataFetcher;
import io.cdap.cdap.graphql.typeruntimewiring.CDAPTypeRuntimeWiring;

/**
 * ApplicationDetail type runtime wiring. Registers the data fetchers for the ApplicationDetail type.
 */
public class ApplicationDetailTypeRuntimeWiring implements CDAPTypeRuntimeWiring {

  private final ProgramRecordDataFetcher programRecordDataFetcher;
  private final MetadataDataFetcher metadataDataFetcher;

  @Inject
  public ApplicationDetailTypeRuntimeWiring(MetadataDataFetcher metadataDataFetcher) {
    this.programRecordDataFetcher = ProgramRecordDataFetcher.getInstance();
    this.metadataDataFetcher = metadataDataFetcher;
  }

  @Override
  public TypeRuntimeWiring getTypeRuntimeWiring() {
    return TypeRuntimeWiring.newTypeWiring(ApplicationTypes.APPLICATION_DETAIL)
      .dataFetcher(ApplicationFields.PROGRAMS, programRecordDataFetcher.getProgramRecordsDataFetcher())
      .dataFetcher(ApplicationFields.METADATA, metadataDataFetcher.getMetadataDataFetcher())
      .build();
  }

}

