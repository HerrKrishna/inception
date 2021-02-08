/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.workload.dynamic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.support.JSONUtil;
import de.tudarmstadt.ukp.inception.workload.dynamic.config.DynamicWorkloadManagerAutoConfiguration;
import de.tudarmstadt.ukp.inception.workload.dynamic.trait.DynamicWorkloadTraits;
import de.tudarmstadt.ukp.inception.workload.model.WorkloadManagementService;
import de.tudarmstadt.ukp.inception.workload.model.WorkloadManager;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link DynamicWorkloadManagerAutoConfiguration#dynamicWorkloadExtension}
 * </p>
 */
public class DynamicWorkloadExtension_Impl
    implements DynamicWorkloadExtension
{
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public String getId()
    {
        return DYNAMIC_WORKLOAD_MANAGER_EXTENSION_ID;
    }

    @Override
    public String getLabel()
    {
        return "Dynamic assignment";
    }

    @Override
    public DynamicWorkloadTraits readTraits(WorkloadManager aWorkloadManager)
    {
        DynamicWorkloadTraits traits = null;

        try {
            traits = JSONUtil.fromJsonString(DynamicWorkloadTraits.class,
                    aWorkloadManager.getTraits());
        }
        catch (Exception e) {
            this.log.error("Unable to read traits", e);
        }

        if (traits == null) {
            traits = new DynamicWorkloadTraits();
        }

        return traits;
    }

    @Override
    public void writeTraits(WorkloadManagementService aWorkloadManagementService,
            DynamicWorkloadTraits aTrait, Project aProject)
    {
        try {
            WorkloadManager manager = aWorkloadManagementService
                    .loadOrCreateWorkloadManagerConfiguration(aProject);
            manager.setTraits(JSONUtil.toJsonString(aTrait));
            aWorkloadManagementService.saveConfiguration(manager);
        }
        catch (Exception e) {
            this.log.error("Unable to write traits", e);
        }
    }
}
