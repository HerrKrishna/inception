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
package de.tudarmstadt.ukp.inception.ui.kb.stmt.coloring;

import java.util.List;

import de.tudarmstadt.ukp.inception.kb.model.KnowledgeBase;
import de.tudarmstadt.ukp.inception.ui.kb.config.KnowledgeBaseServiceUIAutoConfiguration;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link KnowledgeBaseServiceUIAutoConfiguration#defaultColoringStrategy}.
 * </p>
 */
public class DefaultColoringStrategyImpl
    implements StatementColoringStrategy
{
    private String coloringStrategyId;

    @Override
    public String getId()
    {
        return coloringStrategyId;
    }

    @Override
    public void setBeanName(String aBeanName)
    {
        coloringStrategyId = aBeanName;
    }

    @Override
    public String getBackgroundColor()
    {
        return "ffffff";
    }

    @Override
    public String getFrameColor()
    {
        return "f5f5f5";
    }

    @Override
    public boolean acceptsProperty(String aPropertyIdentifier, KnowledgeBase aKB,
            List<String> aLabelProperties)
    {
        return true;
    }
}
