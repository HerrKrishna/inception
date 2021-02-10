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
package de.tudarmstadt.ukp.inception.sharing;

import de.tudarmstadt.ukp.clarin.webanno.model.Project;

public interface InviteService
{
    /**
     * Generate random expiring invite id for the project and save to database
     * 
     * @param aProject
     *            the given project
     */
    String generateInviteID(Project aProject);

    /**
     * Delete invite id for the project if it exists
     * 
     * @param aProject
     *            the given project
     */
    void removeInviteID(Project aProject);

    /**
     * Get invite id for given project if it exists and has expired yet
     * 
     * @param aProject
     *            the given project
     */
    String getValidInviteID(Project aProject);

    /**
     * Check if given invite ID is valid for the given project
     * 
     * @param aProject
     *            the relevant project
     * @param aInviteId
     *            invite Id to check
     */
    boolean isValidInviteLink(Project aProject, String aInviteId);
}
